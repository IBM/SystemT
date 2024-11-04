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
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.module.jsonSchema.types.ObjectSchema;
import com.ibm.wcs.annotationservice.exceptions.AnnotationServiceException;
import com.ibm.wcs.annotationservice.models.json.AnnotatorBundleConfig;
import com.ibm.wcs.annotationservice.util.ExceptionUtils;

/**
 * Class that encapsulates instantiation and invocation of multiple annotator bundles. An annotator
 * bundle is cached the first time it is instantiated, and the resulting object reused for
 * annotation of multiple documents.
 * 
 */
public class AnnotationService implements Serializable {

  /**
   * 
   */
  private static final long serialVersionUID = 1L;
  /**
   * Map of annotator bundles, indexed by their configuration.
   */
  volatile private HashMap<AnnotatorBundleConfig, AnnotatorBundle> cfgToAnnotatorBundle =
      new HashMap<>();

  /**
   * Main entry point.
   *
   * @param cfg the configuration of the annotator bundle to invoke
   * @param document the input document as a JSON record; the input document is ontology mapped;
   *        this method assumes the input record contains fields containing actual content, but also
   *        fields containing metadata (for example, to indicate the language or the content type of
   *        the document); this method will not validate that the record conforms to the ontology;
   *        FIXME: in v2 we will support additional input 'annotations'
   * @param params execution parameters for annotating the given input document
   * @return a non-null JSON record consisting of two fields named:
   *         {@link AnnotationServiceConstants#ANNOTATIONS_FIELD_NAME} and
   *         {@link AnnotationServiceConstants#INSTRUMENTATION_INFO_FIELD_NAME}, containing the
   *         output annotations, and instrumentation info, respectively; the value of the first
   *         field is a non-null JSON record with as many fields as output type names in the given
   *         annotator bundle config, where each element is a JSON array (potentially empty)
   *         containing the annotator results of that type; the value of the second field is a
   *         non-null JSON record containing information about: input document size (in characters),
   *         running time (in milliseconds), number of annotations output, whether the annotation
   *         was interrupted due to running time exceeding the given timeout, and exception
   *         information (if any)
   * @return
   */
  public JsonNode invoke(AnnotatorBundleConfig cfg, JsonNode document, ExecuteParams params) {
    ObjectNode ret;
    try {
      // Lookup Map for AnnotatorBundle; if absent, initialize.
      if (!cfgToAnnotatorBundle.containsKey(cfg)) {
        init(cfg);
      }
      AnnotatorBundle annotatorBundle = cfgToAnnotatorBundle.get(cfg);
      // Invoke on the current document and get back the result
      ret = annotatorBundle.invoke(document, params);
    } catch (Throwable e) {
      // Handle any exceptions thrown before {@link
      // AnnotatorBundle.invoke()}
      ObjectNode errors = ExceptionUtils.serializeException(e);
      // Create response record with default values: 0 running time, not
      // interrupted, the cfg (potentially null)
      long runtimeMS = 0;
      InterruptResponse response = new InterruptResponse(false);
      ret = AnnotatorBundle.makeOutputRecord(document, null, errors, runtimeMS, response, cfg);
    }
    return ret;
  }

  /**
   * Entry point for raw annotation record.
   * 
   * @param cfg the configuration of the annotator bundle to invoke
   * @param document the input document as a JSON record; the input document is ontology mapped;
   *        this method assumes the input record contains fields containing actual content, but also
   *        fields containing metadata (for example, to indicate the language or the content type of
   *        the document); this method will not validate that the record conforms to the ontology;
   *        FIXME: in v2 we will support additional input 'annotations'
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
  public AnnotatorBundle.RawAnnotationOutputRecord invokeRaw(AnnotatorBundleConfig cfg,
      JsonNode document, ExecuteParams params) {
    AnnotatorBundle.RawAnnotationOutputRecord ret;
    try {
      // Lookup Map for AnnotatorBundle; if absent, initialize.
      if (!cfgToAnnotatorBundle.containsKey(cfg)) {
        init(cfg);
      }
      AnnotatorBundle annotatorBundle = cfgToAnnotatorBundle.get(cfg);
      // Invoke on the current document and get back the result
      ret = annotatorBundle.invokeRaw(document, params);
    } catch (Throwable e) {
      // Handle any exceptions thrown before {@link
      // AnnotatorBundle.invoke()}
      ObjectNode errors = ExceptionUtils.serializeException(e);
      // Create response record with default values: 0 running time, not
      // interrupted, the cfg (potentially null)
      long runtimeMS = 0;
      InterruptResponse response = new InterruptResponse(false);
      ret = AnnotatorBundle.makeRawOutputRecord(document, null, errors, runtimeMS, response, cfg);
    }
    return ret;
  }

  /**
   * Computes the output schema of this annotator
   *
   * @param inputSchema a list of field names already defined in the input schema; will skip
   *        processing for field names of output schema if it exists in the input schema
   * @return a JSON Schema of output
   * @throws Exception
   * @see AnnotatorWrapperSystemT#getOutputSchema(List)
   * @see AnnotatorWrapperUIMA#getOutputSchema(List)
   */
  public ObjectSchema getOutputSchema(AnnotatorBundleConfig cfg, List<String> inputSchema)
      throws Exception {
    // Lookup Map for AnnotatorBundle; if absent, initialize.
    if (!cfgToAnnotatorBundle.containsKey(cfg)) {
      init(cfg);
    }
    AnnotatorBundle annotatorBundle = cfgToAnnotatorBundle.get(cfg);
    // Return output schema for current config
    return annotatorBundle.getOutputSchema(inputSchema);
  }

  /**
   * Computes the concise output schema of this annotator
   *
   * @param inputSchema a list of field names already defined in the input schema; will skip
   *        processing for field names of output schema if it exists in the input schema
   * @return an ObjectNode representation of output
   * @throws Exception
   * @see AnnotatorWrapperSystemT#getOutputSchema(List)
   * @see AnnotatorWrapperUIMA#getOutputSchema(List)
   */
  public ObjectNode getShortOutputViewSchema(AnnotatorBundleConfig cfg, List<String> inputSchema)
      throws Exception {
    // Lookup Map for AnnotatorBundle; if absent, initialize.
    if (!cfgToAnnotatorBundle.containsKey(cfg)) {
      init(cfg);
    }
    AnnotatorBundle annotatorBundle = cfgToAnnotatorBundle.get(cfg);
    // Return output schema for current config
    return annotatorBundle.getShortOutputViewSchema(inputSchema);
  }

  /**
   * Initialize the Annotator bundle given its config.
   * 
   * @param cfg the configuration for the annotator bundle to instantiate
   * 
   * @throws AnnotationServiceException if there is any error when instantiating the operator graph
   */
  public void init(AnnotatorBundleConfig cfg) throws AnnotationServiceException {
    // Check whether an annotator bundle has been already instantiated
    // for this config
    AnnotatorBundle annotator;
    synchronized (this) {
      annotator = cfgToAnnotatorBundle.get(cfg);
      // If not instantiated before, do it now
      if (null == annotator) {
        annotator = new AnnotatorBundle(cfg);
        annotator.init();
        cfgToAnnotatorBundle.put(cfg, annotator);
      }
    }
  }

  /**
   * Save the annotation bundle model to the specified location as a zip file.
   * 
   * @param cfg The configuration of the annotator bundle to save.
   * @param zipFilePath
   * @throws Exception
   */
  public void save(AnnotatorBundleConfig cfg, String zipFilePath) throws Exception {
    save(cfg, zipFilePath, false);
  }

  /**
   * Save the annotation bundle model to the specified location as a zip file.
   * 
   * @param cfg The configuration of the annotator bundle to save.
   * @param zipFilePath
   * @param compileDictionaries The flag whether the external dictionaries in the saved model should
   *        be compiled.
   * @throws Exception
   */
  public void save(AnnotatorBundleConfig cfg, String zipFilePath, boolean compileDictionaries)
      throws Exception {
    if (!cfgToAnnotatorBundle.containsKey(cfg)) {
      init(cfg);
    }
    AnnotatorBundle annotatorBundle = cfgToAnnotatorBundle.get(cfg);
    annotatorBundle.save(zipFilePath, compileDictionaries);
  }

  /**
   * @return the number of annotator bundles cached by this instance of Annotation Service.
   */
  public int getNumAnnotatorBundles() {
    if (null == cfgToAnnotatorBundle)
      return 0;

    return cfgToAnnotatorBundle.size();
  }


}
