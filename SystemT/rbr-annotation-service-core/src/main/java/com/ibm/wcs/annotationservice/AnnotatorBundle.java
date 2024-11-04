/*******************************************************************************
 * Copyright IBM
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *******************************************************************************/
package com.ibm.wcs.annotationservice;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.module.jsonSchema.types.ObjectSchema;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.logging.Log;
import com.ibm.wcs.annotationservice.exceptions.AnnotationServiceException;
import com.ibm.wcs.annotationservice.exceptions.VerboseNullPointerException;
import com.ibm.wcs.annotationservice.models.json.AnnotatorBundleConfig;
import com.ibm.wcs.annotationservice.util.ExceptionUtils;
import com.ibm.wcs.annotationservice.util.JsonUtils;

/**
 * Class encapsulating the instantiation of an annotator bundle, and the execution on a single
 * document.
 * 
 */
public class AnnotatorBundle {

  /** Running in debug mode ? */
  private static final boolean DEBUG = false;

  /** Configuration for this annotator bundle */
  private AnnotatorBundleConfig annotatorBundleCfg = null;

  /** The object that performs the actual annotation */
  private AnnotatorWrapper annotator = null;

  /**
   * Executor service for monitoring the running time of {@link #invoke(JsonNode, ExecuteParams)}
   */
  private ExecutorService executor = null;

  /** Name of the invoke method, to use in debug messages */
  private static final String INVOKE_FN_NAME =
      String.format("%s.invoke()", AnnotatorBundle.class.getSimpleName());

  /** Used by Jackson to bind JSON */
  protected static ObjectMapper mapper = new ObjectMapper();

  /**
   * Main constructor; sets the annotator configuration, but does not perform any initialization;
   * actual initialization is done in {@link #init()}
   * 
   * @param cfg the annotator configuration
   */
  public AnnotatorBundle(AnnotatorBundleConfig cfg) {
    annotatorBundleCfg = cfg;
  }

  /**
   * Instantiate the annotator, based on the bundle configuration. This method caches the actual
   * annotator object and reuses it across multiple invocations of
   * {@link #invoke(JsonNode, ExecuteParams)}
   * 
   * @throws AnnotationServiceException if any exception was encountered during initializing the
   *         annotator bundle
   */
  public void init() throws AnnotationServiceException {
    annotator = AnnotatorWrapperFactory.createInstance(annotatorBundleCfg);
  }

  /**
   * Execute the annotator bundle on a single document, with specified execution parameters.
   * 
   * @param document the input document as a JSON record; the input document is ontology mapped;
   *        this method assumes the input record contains fields containing actual content, but also
   *        fields containing metadata (for example, to indicate the language or the content type of
   *        the document); this method does not validate that the record conforms to the ontology;
   *        instead, this method queries the input schema of the annotator and passes through to the
   *        annotator the values of the expected fields from the input document record; for example,
   *        in the case of a SystemT annotator, this method queries the schema of the view Document;
   *        if a field expected by the annotator does not exist in the input record, or the field
   *        exists but has an incompatible type, this method throws an exception; in the case of
   *        UIMA annotators, the current implementation assumes that the input document contains a
   *        field named {@link AnnotationServiceConstants#DOCUMENT_TEXT_FIELD_NAME} of type String
   *        and will throw an exception otherwise
   * @param params execution parameters for annotating the given input document
   * @return a JSON record consisting of two fields named:
   *         {@link AnnotationServiceConstants#ANNOTATIONS_FIELD_NAME} and
   *         {@link AnnotationServiceConstants#INSTRUMENTATION_INFO_FIELD_NAME}, containing the
   *         output annotations, and instrumentation info, respectively; the value of the first
   *         field is a non-null JSON record with as many fields as output type names in the given
   *         annotator bundle config, where each element is a non-null JSON array (potentially
   *         empty) containing the annotator results of that type; the value of the second field is
   *         a non-null JSON record containing information about: input document size (in
   *         characters), running time (in milliseconds), number of annotations output, exception
   *         information (if any), whether the annotation was interrupted due to running time
   *         exceeding the given timeout, and whether the annotator completed successfully.
   */
  public ObjectNode invoke(JsonNode document, ExecuteParams params) {

    if (DEBUG)
      Log.debug("Execution Params:\n%s", params);

    boolean timeoutEnabled = false;
    long timeoutMS = params.getTimeoutMS();

    // Timeout is enabled if the user requested so, and the runtime supports
    // the interrupt functionality
    if (timeoutMS != AnnotationServiceConstants.NO_TIMEOUT && annotator.supportsInterrupt()) {
      timeoutEnabled = true;
    }

    // Data structures to hold our output
    ObjectNode annotations = null, errors = null, ret = null;
    InterruptResponse interrupted = new InterruptResponse(false);
    long elapsedTimeMS;

    // Record the running time
    long beginTimeMS = System.currentTimeMillis();

    // Call the actual process methods
    try {

      if (timeoutEnabled) {
        annotations = processAsync(document, params, interrupted);
      } else {
        annotations = process(document, params);
      }
    } catch (AnnotationServiceException e) {
      errors = ExceptionUtils.serializeException(e);
    } finally {
      elapsedTimeMS = System.currentTimeMillis() - beginTimeMS;
      ret = makeOutputRecord(document, annotations, errors, elapsedTimeMS, interrupted,
          annotatorBundleCfg);
    }

    return ret;

  }

  /**
   * Execute the annotator bundle on a single document, with specified execution parameters.
   *
   * @param document the input document as a JSON record; the input document is ontology mapped;
   *        this method assumes the input record contains fields containing actual content, but also
   *        fields containing metadata (for example, to indicate the language or the content type of
   *        the document); this method does not validate that the record conforms to the ontology;
   *        instead, this method queries the input schema of the annotator and passes through to the
   *        annotator the values of the expected fields from the input document record; for example,
   *        in the case of a SystemT annotator, this method queries the schema of the view Document;
   *        if a field expected by the annotator does not exist in the input record, or the field
   *        exists but has an incompatible type, this method throws an exception; in the case of
   *        UIMA annotators, the current implementation assumes that the input document contains a
   *        field named {@link AnnotationServiceConstants#DOCUMENT_TEXT_FIELD_NAME} of type String
   *        and will throw an exception otherwise
   * @param params execution parameters for annotating the given input document
   * @return a raw annotation output record consisting of two fields named:
   *         {@link AnnotationServiceConstants#ANNOTATIONS_FIELD_NAME} and
   *         {@link AnnotationServiceConstants#INSTRUMENTATION_INFO_FIELD_NAME}, containing the
   *         output annotations, and instrumentation info, respectively; the value of the first
   *         field is a map from fields as output type name in the given annotator bundle config to
   *         TupleList which containing the annotator results of that type; the value of the second
   *         field is a non-null JSON record containing information about: input document size (in
   *         characters), running time (in milliseconds), number of annotations output, exception
   *         information (if any), whether the annotation was interrupted due to running time
   *         exceeding the given timeout, and whether the annotator completed successfully.
   */
  public RawAnnotationOutputRecord invokeRaw(JsonNode document, ExecuteParams params) {

    if (DEBUG)
      Log.debug("Execution Params:\n%s", params);

    boolean timeoutEnabled = false;
    long timeoutMS = params.getTimeoutMS();

    // Timeout is enabled if the user requested so, and the runtime supports
    // the interrupt functionality
    if (timeoutMS != AnnotationServiceConstants.NO_TIMEOUT && annotator.supportsInterrupt()) {
      timeoutEnabled = true;
    }

    // Data structures to hold our output
    Map<String, TupleList> annotations = null;
    ObjectNode errors = null;
    RawAnnotationOutputRecord ret = null;
    InterruptResponse interrupted = new InterruptResponse(false);
    long elapsedTimeMS;

    // Record the running time
    long beginTimeMS = System.currentTimeMillis();

    // Call the actual process methods
    try {

      if (timeoutEnabled) {
        annotations = processAsyncRaw(document, params, interrupted);
      } else {
        annotations = processRaw(document, params);
      }
    } catch (AnnotationServiceException e) {
      errors = ExceptionUtils.serializeException(e);
    } finally {
      elapsedTimeMS = System.currentTimeMillis() - beginTimeMS;
      ret = makeRawOutputRecord(document, annotations, errors, elapsedTimeMS, interrupted,
          annotatorBundleCfg);
    }

    return ret;

  }

  public ObjectSchema getOutputSchema(List<String> inputSchema) throws Exception {
    return annotator.getOutputSchema(inputSchema);
  }

  public ObjectNode getShortOutputViewSchema(List<String> inputSchema) throws Exception {
    return annotator.getShortOutputViewSchema(inputSchema);
  }
  /*
   * PRIVATE METHODS GO HERE
   */

  /**
   * Subroutine of {@link #invoke(JsonNode, ExecuteParams)} that does all the work, using an
   * executor service to monitor the running time and interrupt annotation if the specified timeout
   * elapsed; whether the annotation process was interrupted or now is returned as part of the
   * interrupted argument.
   * 
   * @param document JSON record representing the input document
   * @param params execution parameters
   * @param interruptResponse return value of true, if the annotator execution was interrupted, and
   *        false otherwise
   * @return a JSON record consisting of two fields, containing the output annotations, and
   *         instrumentation info, respectively
   * @throws AnnotationServiceException
   */
  private ObjectNode processAsync(final JsonNode document, final ExecuteParams params,
      InterruptResponse interruptResponse) throws AnnotationServiceException {

    if (executor == null) {
      executor = Executors.newSingleThreadExecutor();
    }
    Future<ObjectNode> result = executor.submit(new Callable<ObjectNode>() {

      @Override
      public ObjectNode call() throws Exception {
        return process(document, params);
      }

    });

    ObjectNode ret;
    try {
      long timeoutMS = params.getTimeoutMS();
      if (DEBUG)
        Log.debug("Callling Future.get() with timeout enabled at: %d msec", timeoutMS);
      ret = result.get(timeoutMS, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      // Interrupt the annotator, and indicate that to the caller
      annotator.interrupt();
      interruptResponse.setInterrupted(true);
      throw new AnnotationServiceException(e);
    } catch (ExecutionException e) {
      throw new AnnotationServiceException(e);
    } catch (TimeoutException e) {
      // Interrupt the annotator, and indicate that to the caller
      annotator.interrupt();
      interruptResponse.setInterrupted(true);
      throw new AnnotationServiceException(e);
    }

    return ret;
  }

  /**
   * Subroutine of {@link #invokeRaw(JsonNode, ExecuteParams)} that does all the work, using an
   * executor service to monitor the running time and interrupt annotation if the specified timeout
   * elapsed; whether the annotation process was interrupted or now is returned as part of the
   * interrupted argument.
   *
   * @param document JSON record representing the input document
   * @param params execution parameters
   * @param interruptResponse return value of true, if the annotator execution was interrupted, and
   *        false otherwise
   * @return a map from field name to annotation results
   * @throws AnnotationServiceException
   */
  private Map<String, TupleList> processAsyncRaw(final JsonNode document,
      final ExecuteParams params, InterruptResponse interruptResponse)
      throws AnnotationServiceException {

    if (executor == null) {
      executor = Executors.newSingleThreadExecutor();
    }
    Future<Map<String, TupleList>> result = executor.submit(new Callable<Map<String, TupleList>>() {

      @Override
      public Map<String, TupleList> call() throws Exception {
        return processRaw(document, params);
      }

    });

    Map<String, TupleList> ret;
    try {
      long timeoutMS = params.getTimeoutMS();
      if (DEBUG)
        Log.debug("Callling Future.get() with timeout enabled at: %d msec", timeoutMS);
      ret = result.get(timeoutMS, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      // Interrupt the annotator, and indicate that to the caller
      annotator.interrupt();
      interruptResponse.setInterrupted(true);
      throw new AnnotationServiceException(e);
    } catch (ExecutionException e) {
      throw new AnnotationServiceException(e);
    } catch (TimeoutException e) {
      // Interrupt the annotator, and indicate that to the caller
      annotator.interrupt();
      interruptResponse.setInterrupted(true);
      throw new AnnotationServiceException(e);
    }

    return ret;
  }

  /**
   * Terminate the annotator bundle and release the resource
   * 
   * @throws AnnotationServiceException
   */
  public void terminate() throws AnnotationServiceException {
    annotator.close();
  }

  /**
   * Subroutine of {@link #processAsync(JsonNode, ExecuteParams, InterruptResponse)} that performs
   * the actual annotation
   * 
   * @param document JSON record representing the input document
   * @param params execution parameters
   * @return a JSON record consisting of two fields, containing the output annotations, and
   *         instrumentation info, respectively
   * @throws AnnotationServiceException
   */
  private ObjectNode process(JsonNode document, ExecuteParams params)
      throws AnnotationServiceException {

    // VALIDATE THE INPUT DOCUMENT
    // Validate input document content
    if (null == document)
      throw new VerboseNullPointerException(INVOKE_FN_NAME,
          AnnotationServiceConstants.DOCUMENT_PARAMETER_NAME);

    // Obtain and validate the language code
    String langCode = params.getLangCodeValue();
    if (null == langCode) {
      // Language code comes from the input document
      String langCodeFieldName = params.getLangCodeFieldName();
      ObjectNode langCodeObj = (ObjectNode) document.get(langCodeFieldName);
      langCode = JsonUtils.processJSONString(langCodeFieldName, langCodeObj,
          AnnotationServiceConstants.DOCUMENT_PARAMETER_NAME, true);
    }

    // Obtain and validate the content type
    String contentTypeStr = params.getContentTypeValue();
    if (null == contentTypeStr) {
      // Content type comes from the input document
      String contentTypeFieldName = params.getContentTypeFieldName();
      JsonNode contentTypeObj = document.get(contentTypeFieldName);
      contentTypeStr = JsonUtils.processJSONString(contentTypeFieldName, contentTypeObj,
          AnnotationServiceConstants.DOCUMENT_PARAMETER_NAME, true);
    }

    // Validate that the content type of the document is one of the two
    // supported values
    if (!(contentTypeStr.equals(AnnotatorBundleConfig.ContentType.HTML.getValue())
        || contentTypeStr.equals(AnnotatorBundleConfig.ContentType.PLAIN.getValue())))
      throw new AnnotationServiceException(
          "Content type value '%s' is not supported; supported values are: %s and %s",
          contentTypeStr, AnnotatorBundleConfig.ContentType.HTML.getValue(),
          AnnotatorBundleConfig.ContentType.PLAIN.getValue());

    AnnotatorBundleConfig.ContentType contentType =
        AnnotatorBundleConfig.ContentType.getEnum(contentTypeStr);

    // Validate that the content type is compatible with the annotator
    // bundle config
    if (!annotatorBundleCfg.getAcceptedContentTypes().contains(contentType.getValue()))
      throw new AnnotationServiceException(
          "Input document content type '%s' is not compatible with the content types that the annotator accepts: %s",
          contentTypeStr, annotatorBundleCfg.getAcceptedContentTypes());

    // Check that the annotator object was initialized successfully
    if (null == annotator) {
      throw new AnnotationServiceException("AnnotatorBundle.invoke() called before init().");
    }

    // Invoke the annotator and add the output annotations to the existing
    // annotations object
    ObjectNode outputAnnotations = annotator.invoke(document, langCode);

    return outputAnnotations;
  }

  /**
   * Subroutine of {@link #processAsyncRaw(JsonNode, ExecuteParams, InterruptResponse)} that
   * performs the actual annotation
   *
   * @param document JSON record representing the input document
   * @param params execution parameters
   * @return a map from field name to annotation results
   * @throws AnnotationServiceException
   */
  private Map<String, TupleList> processRaw(JsonNode document, ExecuteParams params)
      throws AnnotationServiceException {

    // VALIDATE THE INPUT DOCUMENT
    // Validate input document content
    if (null == document)
      throw new VerboseNullPointerException(INVOKE_FN_NAME,
          AnnotationServiceConstants.DOCUMENT_PARAMETER_NAME);

    // Obtain and validate the language code
    String langCode = params.getLangCodeValue();
    if (null == langCode) {
      // Language code comes from the input document
      String langCodeFieldName = params.getLangCodeFieldName();
      ObjectNode langCodeObj = (ObjectNode) document.get(langCodeFieldName);
      langCode = JsonUtils.processJSONString(langCodeFieldName, langCodeObj,
          AnnotationServiceConstants.DOCUMENT_PARAMETER_NAME, true);
    }

    // Obtain and validate the content type
    String contentTypeStr = params.getContentTypeValue();
    if (null == contentTypeStr) {
      // Content type comes from the input document
      String contentTypeFieldName = params.getContentTypeFieldName();
      JsonNode contentTypeObj = document.get(contentTypeFieldName);
      contentTypeStr = JsonUtils.processJSONString(contentTypeFieldName, contentTypeObj,
          AnnotationServiceConstants.DOCUMENT_PARAMETER_NAME, true);
    }

    // Validate that the content type of the document is one of the two
    // supported values
    if (!(contentTypeStr.equals(AnnotatorBundleConfig.ContentType.HTML.getValue())
        || contentTypeStr.equals(AnnotatorBundleConfig.ContentType.PLAIN.getValue())))
      throw new AnnotationServiceException(
          "Content type value '%s' is not supported; supported values are: %s and %s",
          contentTypeStr, AnnotatorBundleConfig.ContentType.HTML.getValue(),
          AnnotatorBundleConfig.ContentType.PLAIN.getValue());

    AnnotatorBundleConfig.ContentType contentType =
        AnnotatorBundleConfig.ContentType.getEnum(contentTypeStr);

    // Validate that the content type is compatible with the annotator
    // bundle config
    if (!annotatorBundleCfg.getAcceptedContentTypes().contains(contentType.getValue()))
      throw new AnnotationServiceException(
          "Input document content type '%s' is not compatible with the content types that the annotator accepts: %s",
          contentTypeStr, annotatorBundleCfg.getAcceptedContentTypes());

    // Check that the annotator object was initialized successfully
    if (null == annotator) {
      throw new AnnotationServiceException("AnnotatorBundle.invoke() called before init().");
    }

    // Invoke the annotator and add the output annotations to the existing
    // annotations object
    if (annotator instanceof AnnotatorWrapperSystemT) {
      return ((AnnotatorWrapperSystemT) annotator).invokeRaw(document, langCode);
    } else {
      throw new AnnotationServiceException("invokeRaw() is supported only in SystemT annotator.");
    }
  }

  /**
   * Put together the output of {@link #invoke(JsonNode, ExecuteParams)} and the instrumentation
   * info into a single record.
   * 
   * @param document input document, as a JSON record
   * @param annotations JSON record containing the output of the annotator
   * @param errors JSON Record containing the error message and stack trace, if any error was
   *        encountered during the annotation of the given input document; null otherwise
   * @param elapsedTimeMS run time (in milliseconds) of the annotator on the input document
   * @param interruptResponse token object indicating whether the annotator execution was
   *        interrupted on the input document due to timeout
   * @param annotatorBundleCfg annotator bundle configuration (potentially null) of the annotator
   *        bundle that created the output
   * @return a JSON record consisting of two fields: 'annotations' and 'instrumentationInfo',
   *         containing the output annotations, and instrumentation info, respectively
   */
  public static ObjectNode makeOutputRecord(JsonNode document, ObjectNode annotations,
      ObjectNode errors, long elapsedTimeMS, InterruptResponse interruptResponse,
      AnnotatorBundleConfig annotatorBundleCfg) {

    // Initialize the output record
    ObjectNode ret = JsonNodeFactory.instance.objectNode();

    // Obtain the output types from the config, only if the config is
    // non-null; otherwise, assume 0 output types
    List<String> outputTypes = new ArrayList<String>();
    if (null != annotatorBundleCfg)
      outputTypes = annotatorBundleCfg.getOutputTypes();

    // Create the final record of output annotations, making sure every
    // output type appears in the record, in the order
    // in which it was specified in the annotator bundle config
    // TODO In v2, annotations can also come from the input document
    ObjectNode finalAnnotations = makeAnnotationsRecord(annotations, outputTypes);
    ret.set(AnnotationServiceConstants.ANNOTATIONS_FIELD_NAME, finalAnnotations);

    // Create instrumentation info
    ObjectNode instrumentationInfo = JsonNodeFactory.instance.objectNode();

    // Record info about the actual annotator bundle that generated this
    // output
    if (null != annotatorBundleCfg) {
      JsonNode annotator = mapper.convertValue(annotatorBundleCfg.getAnnotator(), JsonNode.class);
      instrumentationInfo.set(AnnotationServiceConstants.ANNOTATOR_FIELD_NAME, annotator);
    }

    // Record running time
    instrumentationInfo.put(AnnotationServiceConstants.RUNNING_TIME_FIELD_NAME, elapsedTimeMS);

    // Record the errors, if any
    boolean foundError = false;
    if (null != errors) {
      foundError = true;
      instrumentationInfo.setAll(errors);
    }

    // Size of the input document (in number of characters)
    long size = getSize(document);
    instrumentationInfo.put(AnnotationServiceConstants.DOCUMENT_SIZE_FIELD_NAME, size);

    // Number of annotations
    int numAnnots = 0, numAnnotsPerType = 0;
    ArrayNode outputAnnotsPerType = JsonNodeFactory.instance.arrayNode();

    // Find out the real counts, from the final annotations type which we
    // prepared above
    for (String outputType : outputTypes) {

      // At this point, output annotations is not null
      ArrayNode outputAnnotations = (ArrayNode) finalAnnotations.get(outputType);

      numAnnotsPerType = outputAnnotations.size();
      numAnnots += numAnnotsPerType;

      // Make a record to hold the count for this output type
      ObjectNode outputTypeCountRcd = JsonNodeFactory.instance.objectNode();
      outputTypeCountRcd.put(AnnotationServiceConstants.ANNOTATION_TYPE_FIELD_NAME, outputType);
      outputTypeCountRcd.put(AnnotationServiceConstants.ANNOTATIONS_SIZE_FIELD_NAME,
          numAnnotsPerType);

      // Add the new record to our array
      outputAnnotsPerType.add(outputTypeCountRcd);
    }

    instrumentationInfo.put(AnnotationServiceConstants.ANNOTATIONS_SIZE_TOTAL_FIELD_NAME,
        numAnnots);
    instrumentationInfo.set(AnnotationServiceConstants.ANNOTATIONS_SIZE_PER_TYPE_FIELD_NAME,
        outputAnnotsPerType);

    // Record whether it was interrupted
    instrumentationInfo.put(AnnotationServiceConstants.INTERRUPTED_FIELD_NAME,
        interruptResponse.getInterrupted());

    // Record whether it was successful
    boolean success = (interruptResponse.getInterrupted() || foundError) ? false : true;
    instrumentationInfo.put(AnnotationServiceConstants.SUCCESS_FIELD_NAME, success);

    // Record the instrumentation info
    ret.set(AnnotationServiceConstants.INSTRUMENTATION_INFO_FIELD_NAME, instrumentationInfo);

    return ret;
  }

  /**
   * A wrapper class for annotation result of Low-level Java API result
   */
  public static class RawAnnotationOutputRecord {
    private Map<String, TupleList> annotations = null;
    private JsonNode instrumentationInfo = null;

    RawAnnotationOutputRecord() {}

    public void setAnnotations(Map<String, TupleList> annotations) {
      this.annotations = annotations;
    }

    public void setInstrumentationInfo(JsonNode instrumentationInfo) {
      this.instrumentationInfo = instrumentationInfo;
    }

    public Map<String, TupleList> getAnnotations() {
      return this.annotations;
    }

    public JsonNode getInstrumentationInfo() {
      return this.instrumentationInfo;
    }
  }

  /**
   * Put together the output of {@link #invokeRaw(JsonNode, ExecuteParams)} and the instrumentation
   * info into a single record.
   *
   * @param document input document, as a JSON record
   * @param annotations A raw record containing the output of the annotator
   * @param errors JSON Record containing the error message and stack trace, if any error was
   *        encountered during the annotation of the given input document; null otherwise
   * @param elapsedTimeMS run time (in milliseconds) of the annotator on the input document
   * @param interruptResponse token object indicating whether the annotator execution was
   *        interrupted on the input document due to timeout
   * @param annotatorBundleCfg annotator bundle configuration (potentially null) of the annotator
   *        bundle that created the output
   * @return a raw record consisting of two fields: 'annotations' and 'instrumentationInfo',
   *         containing the output annotations, and instrumentation info, respectively
   */
  public static RawAnnotationOutputRecord makeRawOutputRecord(JsonNode document,
      Map<String, TupleList> annotations, ObjectNode errors, long elapsedTimeMS,
      InterruptResponse interruptResponse, AnnotatorBundleConfig annotatorBundleCfg) {

    // Initialize the output record
    RawAnnotationOutputRecord ret = new RawAnnotationOutputRecord();

    // Obtain the output types from the config, only if the config is
    // non-null; otherwise, assume 0 output types
    List<String> outputTypes = new ArrayList<String>();
    if (null != annotatorBundleCfg)
      outputTypes = annotatorBundleCfg.getOutputTypes();

    // Create the final record of output annotations, making sure every
    // output type appears in the record, in the order
    // in which it was specified in the annotator bundle config
    // TODO In v2, annotations can also come from the input document
    Map<String, TupleList> finalAnnotations = makeRawAnnotationsRecord(annotations, outputTypes);
    ret.setAnnotations(finalAnnotations);

    // Create instrumentation info
    ObjectNode instrumentationInfo = JsonNodeFactory.instance.objectNode();

    // Record info about the actual annotator bundle that generated this
    // output
    if (null != annotatorBundleCfg) {
      JsonNode annotator = mapper.convertValue(annotatorBundleCfg.getAnnotator(), JsonNode.class);
      instrumentationInfo.set(AnnotationServiceConstants.ANNOTATOR_FIELD_NAME, annotator);
    }

    // Record running time
    instrumentationInfo.put(AnnotationServiceConstants.RUNNING_TIME_FIELD_NAME, elapsedTimeMS);

    // Record the errors, if any
    boolean foundError = false;
    if (null != errors) {
      foundError = true;
      instrumentationInfo.setAll(errors);
    }

    // Size of the input document (in number of characters)
    long size = getSize(document);
    instrumentationInfo.put(AnnotationServiceConstants.DOCUMENT_SIZE_FIELD_NAME, size);

    // Number of annotations
    int numAnnots = 0, numAnnotsPerType = 0;
    ArrayNode outputAnnotsPerType = JsonNodeFactory.instance.arrayNode();

    // Find out the real counts, from the final annotations type which we
    // prepared above
    for (String outputType : outputTypes) {

      // At this point, output annotations is not null
      TupleList outputAnnotations = finalAnnotations.get(outputType);

      numAnnotsPerType = outputAnnotations.size();
      numAnnots += numAnnotsPerType;

      // Make a record to hold the count for this output type
      ObjectNode outputTypeCountRcd = JsonNodeFactory.instance.objectNode();
      outputTypeCountRcd.put(AnnotationServiceConstants.ANNOTATION_TYPE_FIELD_NAME, outputType);
      outputTypeCountRcd.put(AnnotationServiceConstants.ANNOTATIONS_SIZE_FIELD_NAME,
          numAnnotsPerType);

      // Add the new record to our array
      outputAnnotsPerType.add(outputTypeCountRcd);
    }

    instrumentationInfo.put(AnnotationServiceConstants.ANNOTATIONS_SIZE_TOTAL_FIELD_NAME,
        numAnnots);
    instrumentationInfo.set(AnnotationServiceConstants.ANNOTATIONS_SIZE_PER_TYPE_FIELD_NAME,
        outputAnnotsPerType);

    // Record whether it was interrupted
    instrumentationInfo.put(AnnotationServiceConstants.INTERRUPTED_FIELD_NAME,
        interruptResponse.getInterrupted());

    // Record whether it was successful
    boolean success = (interruptResponse.getInterrupted() || foundError) ? false : true;
    instrumentationInfo.put(AnnotationServiceConstants.SUCCESS_FIELD_NAME, success);

    // Record the instrumentation info
    ret.setInstrumentationInfo(instrumentationInfo);

    return ret;
  }

  /**
   * Create the final record of all annotations, ensuring that for each element in the annotator
   * bundle config output types, there is a corresponding element in the output record (even if it's
   * value is an empty array), and putting the elements in the order in which they have been
   * specified in the annotator bundle config
   * 
   * @param annotations non-null record consisting of as many elements as types in the output types
   *        specification of the annotator bundle config, in the order in which they appear in the
   *        config; the key is the name of the output type, and the value is a JSON array of records
   *        (possibly empty if the annotator did not generate any results for the output type)
   * @param outputTypes output types that should be serialized
   */
  private static ObjectNode makeAnnotationsRecord(ObjectNode annotations,
      List<String> outputTypes) {
    ObjectNode finalAnnotations = JsonNodeFactory.instance.objectNode();
    for (String outputType : outputTypes) {
      ArrayNode outputTypeAnnots = JsonNodeFactory.instance.arrayNode();
      finalAnnotations.set(outputType, outputTypeAnnots);
    }

    if (null != annotations) {
      for (String outputType : outputTypes) {
        JsonNode outputTypeAnnots = annotations.get(outputType);
        if (null != outputTypeAnnots) {
          finalAnnotations.set(outputType, outputTypeAnnots);
        }
      }
    }

    return finalAnnotations;
  }

  /**
   * Create the final record of all annotations, ensuring that for each element in the annotator
   * bundle config output types, there is a corresponding element in the output record (even if it's
   * value is an empty array), and putting the elements in the order in which they have been
   * specified in the annotator bundle config
   *
   * @param annotations non-null record consisting of as many elements as types in the output types
   *        specification of the annotator bundle config, in the order in which they appear in the
   *        config; the key is the name of the output type, and the value is a JSON array of records
   *        (possibly empty if the annotator did not generate any results for the output type)
   * @param outputTypes output types that should be serialized
   */
  private static Map<String, TupleList> makeRawAnnotationsRecord(Map<String, TupleList> annotations,
      List<String> outputTypes) {
    Map<String, TupleList> finalAnnotations = new TreeMap<String, TupleList>();
    for (String outputType : outputTypes) {
      finalAnnotations.put(outputType, TupleList.createDummyTupleList());
    }

    if (null != annotations) {
      for (String outputType : outputTypes) {
        TupleList outputTypeAnnots = annotations.get(outputType);
        if (null != outputTypeAnnots) {
          finalAnnotations.put(outputType, outputTypeAnnots);
        }
      }
    }

    return finalAnnotations;
  }

  /**
   * Measure the size of the input record, in characters.
   * 
   * @param record the input record
   * @return the size (in characters) of the input record, calculated as the number of characters in
   *         all fields of type String of the record; or 0 if the input record is null
   */
  private static long getSize(JsonNode record) {
    if (null == record) {
      // No document content, just record 0
      return 0;
    }

    long size = 0;

    // Iterate through the fields of the input record
    Iterator<String> fieldNameIterator = record.fieldNames();

    while (fieldNameIterator.hasNext()) {
      String fieldName = fieldNameIterator.next();
      JsonNode fieldValue = record.get(fieldName);

      // Only consider the size of String content
      if (fieldValue.isTextual()) {
        size += fieldValue.asText().length();
      }
    }

    return size;
  }

  /**
   * Save the annotation bundle model to the specified location as a zip file.
   *
   * @param zipFilePath
   * @param compileDictionaries
   * @throws Exception
   */
  void save(String zipFilePath, boolean compileDictionaries) throws Exception {
    this.annotator.save(zipFilePath, compileDictionaries);
  }
}
