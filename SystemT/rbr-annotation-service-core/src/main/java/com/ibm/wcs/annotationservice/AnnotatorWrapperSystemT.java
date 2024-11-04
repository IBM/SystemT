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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.types.ArraySchema;
import com.fasterxml.jackson.module.jsonSchema.types.ObjectSchema;
import com.ibm.avatar.algebra.datamodel.FieldSetter;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.ScalarList;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.TextSetter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.algebra.util.lang.LangCode;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.algebra.util.tokenize.TokenizerConfig;
import com.ibm.avatar.api.CompilationSummary;
import com.ibm.avatar.api.CompileAQL;
import com.ibm.avatar.api.CompileAQLParams;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.ExternalTypeInfo;
import com.ibm.avatar.api.ExternalTypeInfoFactory;
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.api.OperatorGraphImpl;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.api.tam.ViewMetadata;
import com.ibm.wcs.annotationservice.exceptions.AnnotationServiceException;
import com.ibm.wcs.annotationservice.exceptions.JSONException;
import com.ibm.wcs.annotationservice.models.json.AnnotatorBundleConfig;
import com.ibm.wcs.annotationservice.models.json.SystemTAnnotatorBundleConfig;
import com.ibm.wcs.annotationservice.util.ExceptionUtils;
import com.ibm.wcs.annotationservice.util.JsonUtils;
import com.ibm.wcs.annotationservice.util.file.FileOperations;

/**
 */
public class AnnotatorWrapperSystemT extends AnnotatorWrapperImpl {

  /** Running in debug mode ? */
  private static final boolean DEBUG = false;

  /**
   * OperatorGraph for the systemT annotations, for common use in all documents.
   */
  private OperatorGraph operatorGraph;

  /** TupleSchema of the docs as defined in the modules. */
  private TupleSchema docSchema;

  /**
   * Cached accessors for putting in place the fields of the document tuple, in the same order in
   * which they appear in the document schema of the Operator Graph.
   */
  private FieldSetter<? extends Object>[] docSetters = null;

  /**
   * Map from the "external" to "internal" names of external views in the operator graph.
   */
  private Map<String, String> externalToInternalName = null;

  /**
   * Cached accessors for putting in place the fields of external view tuples. Key is the external
   * view's *internal* name. Reinitialized whenever the operator graph is refreshed.
   */
  private Map<String, FieldSetter<? extends Object>[]> externalViewSetters = null;

  /**
   * Output views for the extractor, requested in the Annotator Bundle Config
   */
  private String[] outputViews = null;

  /**
   * Map of document field value (a Text) to field name in the input record passed to
   * {@link #invoke(JsonNode, String)}; used to serialize the docref attribute of a span; this map
   * is cleared at the beginning of {@link #invoke(JsonNode, String)}
   */
  // private HashMap<Text, String> docTextToFieldName = new HashMap<Text,
  // String>();

  /**
   * Map of document field name in the input record passed to {@link #invoke(JsonNode, String)} to
   * field value (a Text); used to convert JSON to external view spans ; this map is cleared at the
   * beginning of {@link #invoke(JsonNode, String)}
   */
  // private HashMap<String, Text> docFieldNameToText = new HashMap<String,
  // Text>();

  /**
   * Serilization type of span simple for default and locationAndText when the span requires
   * location field and text
   * 
   * @see {@link #spanToJson(Span, String, String, HashMap)}
   */
  private String serializeSpan;

  /**
   * Resource files necessary to reproduce operatorGraph. When the operatorGraph is created, the
   * resource files are stored in this field.
   */
  private AnnotatorResources resources = null;

  /**
   * AnnotatorWrapperSystemT -- Main constructor
   *
   * @param systemTConfig AnnotatorBundleConfig obj with all the config specified for the annotation
   *        job.
   * @throws AnnotationServiceException Errors in the construction of the obj and with the setup and
   *         creation of the operator graph for the systemT analysis.
   */
  @SuppressWarnings("unchecked")
  protected AnnotatorWrapperSystemT(SystemTAnnotatorBundleConfig systemTConfig)
      throws AnnotationServiceException {
    super(systemTConfig);

    // Get the moduleList from the config.
    String[] moduleList =
        systemTConfig.getModuleNames().toArray(new String[systemTConfig.getModuleNames().size()]);

    // create the modules path, and validate
    String modulesPath = getModulesPath();

    // Get the sourceModules
    List<String> sourceModules = systemTConfig.getSourceModules(true);

    // Set the tokenizer to use
    TokenizerConfig annotateTokenizer = setTokenizer();

    // Rely on OperatorGraph.createOG() to perform any validation.
    // validateDictionaries (moduleList, modulesPath);

    // set span serialization type
    serializeSpan = systemTConfig.getSerializeSpan();

    // Get the external types info for the operator graph.
    ExternalTypeInfo extInfo;

    File compiledTamsDir = null;
    try {
      // Compile AQL files if sourceModules element is specified
      if (sourceModules != null && sourceModules.size() > 0) {
        // initialize the temporary output path of the compiled tam files
        compiledTamsDir = FileUtils.makeTempDir("annotation-service-core-");

        CompilationSummary summary =
            compileAQL(sourceModules, modulesPath, compiledTamsDir, annotateTokenizer);

        // update moduleList and modulesPath with the compilation result
        modulesPath +=
            Constants.MODULEPATH_SEP_CHAR + summary.getCompilationRequest().getOutputURI();
      }

      // HANDLE EXTERNAL DICTIONARIES AND TABLES
      extInfo = getExternalTypesForModules(moduleList, modulesPath);

      // Instantiate the operator graph
      operatorGraph = OperatorGraph.createOG(moduleList, modulesPath, extInfo, annotateTokenizer);

      // HANDLE THE INPUT DOCUMENT TYPE

      // Cache the document schema
      docSchema = operatorGraph.getDocumentSchema();

      // Validate that the extractor does not expect both content and text
      if (docSchema.containsField(AnnotationServiceConstants.DOCUMENT_CONTENT_FIELD_NAME)
          && docSchema.containsField(AnnotationServiceConstants.DOCUMENT_TEXT_FIELD_NAME)) {
        throw new AnnotationServiceException(
            "The schema of view Document contains both fields %s and %s (schema is: %s);"
                + "such extractors are not supported because these two fields are considered analoguous in the document ontological concept, for historical reasons; "
                + "modify the extractor by renaming one of these two fields in the schema of view Document",
            AnnotationServiceConstants.DOCUMENT_CONTENT_FIELD_NAME,
            AnnotationServiceConstants.DOCUMENT_TEXT_FIELD_NAME, docSchema);
      }

      // Create the setters for the document tuple
      docSetters = cacheSetters(docSchema);

      // HANDLE INPUT TYPES (aka EXTERNAL VIEWS)

      // Cache setters for external view tuples
      externalViewSetters = new HashMap<String, FieldSetter<? extends Object>[]>();
      for (String viewName : operatorGraph.getExternalViewNames()) {
        externalViewSetters.put(viewName,
            cacheSetters(operatorGraph.getExternalViewSchema(viewName)));
      }

      // Map from "external" name of each external view to the AQL name
      externalToInternalName = new HashMap<String, String>();
      for (String viewName : operatorGraph.getExternalViewNames()) {
        externalToInternalName.put(operatorGraph.getExternalViewExternalName(viewName), viewName);
      }

      // Make sure that the extractor exposes all required input types
      // (external views)

      ArrayList<String> missingInputTypes = new ArrayList<String>();
      if (null != systemTConfig.getInputTypes())
        missingInputTypes.addAll(systemTConfig.getInputTypes());
      missingInputTypes.removeAll(externalToInternalName.keySet());

      if (0 != missingInputTypes.size()) {
        throw new AnnotationServiceException(
            "The SystemT annotator does not have the following required input types: %s; annotator external view external names are: %s; required input types (from annotator bundle config) are: %s",
            missingInputTypes, externalToInternalName.keySet(), systemTConfig.getInputTypes());
      }

      // HANDLE OUTPUT TYPES

      // Make sure that the extractor exposes all required output views
      ArrayList<String> missingOutputTypes = new ArrayList<String>();
      missingOutputTypes.addAll(systemTConfig.getOutputTypes());
      missingOutputTypes.removeAll(operatorGraph.getOutputTypeNames());

      if (0 != missingOutputTypes.size()) {
        throw new AnnotationServiceException(
            "The SystemT annotator does not output the following required output types: %s; annotator output views are: %s; required output types (from annotator bundle config) are: %s",
            missingOutputTypes, operatorGraph.getOutputTypeNames(), systemTConfig.getOutputTypes());
      }

      // Cache the array of output views for reuse across calls to {@link
      // #invoke()}
      List<String> typesList = systemTConfig.getOutputTypes();
      outputViews = typesList.toArray(new String[typesList.size()]);

    } catch (AnnotationServiceException e) {
      if (DEBUG)
        e.printStackTrace();
      throw e;
    } catch (Exception e) {
      if (DEBUG)
        e.printStackTrace();
      throw new AnnotationServiceException(e, "Exception when instantiating SystemT annotator: %s.",
          e.getMessage());
    } finally {
      if (compiledTamsDir != null) {
        // Delete the temporary directory if exists.
        FileUtils.deleteDirectory(compiledTamsDir);
      }
    }
  }

  /**
   * invoke - perform the annotations for a given document.
   *
   * @param doc JsonNode with the document data, has defined by the wcs document schema.
   * @param langCode String with the lang code for the document.
   * @throws AnnotationServiceException
   * @return ret ObjectNode
   */
  @Override
  public ObjectNode invoke(JsonNode doc, String langCode) throws AnnotationServiceException {
    ObjectNode ret;

    try {
      Tuple docTuple;
      Map<String, TupleList> externalViewTups;
      HashMap<Text, String> docTextToFieldName = new HashMap<Text, String>();
      HashMap<String, Text> docFieldNameToText = new HashMap<String, Text>();

      // Clean up the map of doc text to field name
      // docTextToFieldName.clear();
      // docFieldNameToText.clear();

      // Convert the language code
      LangCode languageCode = LangCode.strToLangCode(langCode);

      // Create the input document tuple
      docTuple = jsonToTup(doc, Constants.DEFAULT_DOC_TYPE_NAME, docSchema, docSetters,
          docTextToFieldName, docFieldNameToText, languageCode, true);

      // Read the external view tuples
      externalViewTups =
          getExternalViewTups(doc, languageCode, docTextToFieldName, docFieldNameToText);

      // Execute the extractor
      Map<String, TupleList> result =
          operatorGraph.execute(docTuple, outputViews, externalViewTups);

      // Convert SystemT results to JSON
      ret = formatExtractionResults(result, docTextToFieldName);
    } catch (Throwable e) {
      if (DEBUG)
        e.printStackTrace();
      throw new AnnotationServiceException(e.getCause(),
          "Exception executing SystemT annotator: %s.", ExceptionUtils.removeDocumentContent(e));
    }

    return ret;
  }

  /**
   * invoke - perform the annotations for a given document.
   *
   * @param doc JsonNode with the document data, has defined by the wcs document schema.
   * @param langCode String with the lang code for the document.
   * @throws AnnotationServiceException
   * @return ret Map<String, TupleList>
   */
  public Map<String, TupleList> invokeRaw(JsonNode doc, String langCode)
      throws AnnotationServiceException {
    Map<String, TupleList> ret;

    try {
      Tuple docTuple;
      Map<String, TupleList> externalViewTups;
      HashMap<Text, String> docTextToFieldName = new HashMap<Text, String>();
      HashMap<String, Text> docFieldNameToText = new HashMap<String, Text>();

      // Clean up the map of doc text to field name
      // docTextToFieldName.clear();
      // docFieldNameToText.clear();

      // Convert the language code
      LangCode languageCode = LangCode.strToLangCode(langCode);

      // Create the input document tuple
      docTuple = jsonToTup(doc, Constants.DEFAULT_DOC_TYPE_NAME, docSchema, docSetters,
          docTextToFieldName, docFieldNameToText, languageCode, true);

      // Read the external view tuples
      externalViewTups =
          getExternalViewTups(doc, languageCode, docTextToFieldName, docFieldNameToText);

      // Execute the extractor
      ret = operatorGraph.execute(docTuple, outputViews, externalViewTups);
    } catch (Throwable e) {
      if (DEBUG)
        e.printStackTrace();
      throw new AnnotationServiceException(e.getCause(),
          "Exception executing SystemT annotator: %s.", ExceptionUtils.removeDocumentContent(e));
    }

    return ret;
  }

  /**
   * interrupt - to interrupt the service before executing finishes.
   *
   * @throws AnnotationServiceException
   */
  @Override
  public void interrupt() throws AnnotationServiceException {
    OperatorGraphImpl ogImpl = (OperatorGraphImpl) operatorGraph;
    try {
      ogImpl.interrupt(operatorGraph.getDocumentSchema());
    } catch (Throwable e) {
      throw new AnnotationServiceException(e,
          "Problem encountered when interrupting the Annotation Service: %s", e.getMessage());
    }
  }

  /**
   * supportsInterrupt - returns boolean to tell if the service can be interrupted.
   */
  @Override
  public boolean supportsInterrupt() {
    return true;
  }

  /*
   * PRIVATE METHODS GO HERE
   */

  /**
   * compileAQL - compile specified AQL files and output the compiled .tam file to a temporary
   * directory.
   *
   * @param sourceModules AQL module path to be compiled
   * @param modulesPath pre-compiled AQL module path
   * @param dir a directory to output compiled .tam files.
   * @param tokenizerCfg tokenizer used in compilation
   * @return CompilationSummary summary
   * @throws Exception
   */
  private CompilationSummary compileAQL(List<String> sourceModules, String modulesPath, File dir,
      TokenizerConfig tokenizerCfg) throws Exception {
    // Set up compilation parameters
    CompileAQLParams params = new CompileAQLParams();

    // The source AQL modules to compile
    params.setInputModules(sourceModules.toArray(new String[sourceModules.size()]));

    // The module path, only if the source AQL uses an AQL library (other compilied AQL modules)
    if (modulesPath == null || modulesPath.isEmpty()) {
      params.setModulePath(null);
    } else {
      params.setModulePath(modulesPath);
    }

    // Set up the output directory where source AQL modules get compiled into .tam files
    String compiledTamUri = dir.toURI().toString();
    params.setOutputURI(compiledTamUri);

    // Setting up the underlying tokenizer
    params.setTokenizerConfig(tokenizerCfg);

    // Compile AQL modules to .tam files
    CompilationSummary summary = CompileAQL.compile(params);

    return summary;
  }

  /**
   * getModulesPath - create the modules path from the job configs, and validate that it exists.
   *
   * @return String - module paths string.
   * @throws AnnotationServiceException
   */
  private String getModulesPath() throws AnnotationServiceException {
    // Get the path list for the config, this needs to be converted to a
    // path string.
    ArrayList<String> pathList =
        (ArrayList<String>) ((SystemTAnnotatorBundleConfig) annotateConfig).getModulePath(true);
    if (pathList == null) {
      pathList = new ArrayList<>();
    }

    // check that each path for the modules actually exists.
    for (String eachpath : pathList) {
      try {
        if (!FileOperations.exists(eachpath)) {
          throw new AnnotationServiceException("Module path %s doesn't exist in filesystem.",
              eachpath);
        }
      } catch (Exception e) {
        throw new AnnotationServiceException(e,
            "Exception encountered when checking whether the following module path exists in the filesystem: %s",
            eachpath);
      }
    }

    String[] pathArray = pathList.toArray(new String[pathList.size()]);
    return StringUtils.join(pathArray, Constants.MODULEPATH_SEP_CHAR);
  }

  /**
   * setTokenizer - returns a configured tokenizer based on the job config.
   *
   * @return TokenizerConfig - configured tokenizer for the annotations.
   * @throws AnnotationServiceException
   */
  protected TokenizerConfig setTokenizer() throws AnnotationServiceException {
    String tokenizer = ((SystemTAnnotatorBundleConfig) annotateConfig).getTokenizer();
    TokenizerConfig tokenizerConfig = null;

    if (tokenizer.toUpperCase().equals("STANDARD")) {
      tokenizerConfig = new TokenizerConfig.Standard();
    } else {

      try {
        Class<?> tokenizerConfigClass = Class.forName(tokenizer);
        tokenizerConfig = (TokenizerConfig) tokenizerConfigClass.newInstance();
      } catch (ClassNotFoundException cnfe) {
        throw new AnnotationServiceException(
            "Tokenizer class '%s' specified in manifest.json not found!", tokenizer);
      } catch (final Exception e) {
        throw new AnnotationServiceException(e);
      }
    }
    return tokenizerConfig;
  }

  /**
   * getExternalTypesForModules - loads an ExternalTypeInfo object with info on dictionaries and
   * tables.
   *
   * @param modulesList list of modules used in the annotation job.
   * @param modulesPath paths to the modules.
   * @return ExternalTypeInfo object.
   * @throws TextAnalyticsException
   */
  private ExternalTypeInfo getExternalTypesForModules(String[] modulesList, String modulesPath)
      throws TextAnalyticsException {

    ExternalTypeInfo externalTypeInfo = ExternalTypeInfoFactory.createInstance();

    // Get list of URI Strings for all external dictionaries in this module
    HashMap<String, String> externalDictionaries =
        ((SystemTAnnotatorBundleConfig) annotateConfig).getExternalDictionaries(true);
    if (externalDictionaries != null && externalDictionaries.size() > 0) {
      for (String dictName : externalDictionaries.keySet()) {
        externalTypeInfo.addDictionary(dictName, externalDictionaries.get(dictName));
      }
    }

    // Get list of URI Strings for all external tables in this module
    HashMap<String, String> externalTables =
        ((SystemTAnnotatorBundleConfig) annotateConfig).getExternalTables(true);
    if (externalTables != null && externalTables.size() > 0) {
      for (String tableName : externalTables.keySet()) {
        externalTypeInfo.addTable(tableName, externalTables.get(tableName));
      }
    }

    return externalTypeInfo;
  }

  /**
   * Cache accessors for setting columns of a particular schema.
   *
   * @param schema schema of tuples to target
   * @return an array of FieldSetter objects, in the order of the columns of the schema.
   */
  @SuppressWarnings("rawtypes")
  private FieldSetter[] cacheSetters(TupleSchema schema) {
    FieldSetter[] setters = new FieldSetter[schema.size()];
    for (int i = 0; i < schema.size(); i++) {
      if (schema.getFieldTypeByIx(i).getIsText()) {
        setters[i] = schema.textSetter(schema.getFieldNameByIx(i));
      } else {
        setters[i] = schema.genericSetter(schema.getFieldNameByIx(i), schema.getFieldTypeByIx(i));
      }
    }
    return setters;
  }

  /**
   * Convert a JSON record into a SystemT tuple. Only those fields that are present in the SystemT
   * schema will be output. If a field in schema is not present in the input record, an exception is
   * thrown, with the exception of the view Document (see below). Field values are expected to be of
   * types: String, Integer, Long, Float, Double, Boolean.<br>
   * <br>
   * In the special case when the input JSON record is the input document, do the following: if the
   * schema contains the field text, its value is copied from the field "content" in the input
   * record, if it exists, otherwise from the field "text" if it exists, otherwise throw an
   * exception; if the input contains both fields "content" and "text", throw an exception
   *
   * @param record JSON object containing the document or other tuple
   * @param viewName name of SystemT view; for external views, this is the external name (from the
   *        <code>external_name</code> clause of the <code>create external view</code> statement)
   * @param schema schema of target SystemT tuple
   * @param setters accessor objects, in the same order as the names of the fields in the schema
   * @param docTextToFieldName Map of document field value (a Text) to field name in the input
   *        record
   * @param docFieldNameToText Map of document field name in the input record to field value (a
   *        Text)
   * @param lang language code for the language of all strings to be passed in
   * @param isDocument true if the input JSON record represents the view Document (to deal with
   *        special case of passing content input field to text); false otherwise
   * @return SystemT tuple representation of the record
   * @throws AnnotationServiceException if the JSON object is in the wrong format or the expected
   *         fields are not present
   */
  private Tuple jsonToTup(JsonNode record, String viewName, TupleSchema schema,
      FieldSetter<? extends Object>[] setters, HashMap<Text, String> docTextToFieldName,
      HashMap<String, Text> docFieldNameToText, LangCode lang, boolean isDocument)
      throws AnnotationServiceException {

    Tuple ret = schema.createTup();

    // Read the fields out of the JSON in the order they appear in the
    // SystemT schema.
    for (int i = 0; i < schema.size(); i++) {

      String fname = schema.getFieldNameByIx(i);
      FieldType ftype = schema.getFieldTypeByIx(i);
      JsonNode val = null;

      // BEGIN SPECIAL CASE: View Document, with field called text, and
      // the schema does not contain a field called
      // content. Note that we allow the case when the schema contains
      // both text and content, and the processing
      // is as usual (Document text and content are populated from the
      // text and content in the input JSON record
      // respectively
      if (isDocument && fname.equals(AnnotationServiceConstants.DOCUMENT_TEXT_FIELD_NAME)
          && (!schema.containsField(AnnotationServiceConstants.DOCUMENT_CONTENT_FIELD_NAME))) {

        // Gte the content and text values
        JsonNode valContent =
            (JsonNode) record.get(AnnotationServiceConstants.DOCUMENT_CONTENT_FIELD_NAME);
        JsonNode valText =
            (JsonNode) record.get(AnnotationServiceConstants.DOCUMENT_TEXT_FIELD_NAME);

        if (null == valContent && null == valText)
          // Did not find neither content, nor text; throw an
          // exception
          throw new AnnotationServiceException(
              "Annotator document schema has field '%s', but record does not have fields '%s' or '%s'; provide an input record with exactly one of these two fields",
              AnnotationServiceConstants.DOCUMENT_TEXT_FIELD_NAME,
              AnnotationServiceConstants.DOCUMENT_TEXT_FIELD_NAME,
              AnnotationServiceConstants.DOCUMENT_CONTENT_FIELD_NAME);

        if (null != valContent && null != valText)
          // Found both content and text; throw an exception
          throw new AnnotationServiceException(
              "Annotator document schema has field '%s', but record has fields '%s' and '%s'; provide an input document record with exactly one of these two fields",
              AnnotationServiceConstants.DOCUMENT_TEXT_FIELD_NAME,
              AnnotationServiceConstants.DOCUMENT_TEXT_FIELD_NAME,
              AnnotationServiceConstants.DOCUMENT_CONTENT_FIELD_NAME);

        // Use whichever of the two values that is not null
        if (null != valContent) {
          val = valContent;
        } else {
          val = valText;
        }
      }
      // END SPECIAL CASE
      else {
        val = JsonUtils.getVal(record, fname);
      }

      if (ftype.getIsText()) {
        if (!(val.isTextual() || val.isNull())) {
          throw new AnnotationServiceException(
              "Received value of type %s for field %s in %s, but the type of that field is Text. "
                  + "Provide a value of type String or null instead",
              val.getClass(), fname, viewName);
        }
        TextSetter ts = (TextSetter) setters[i];
        Text valText;
        if (val.isNull()) {
          valText = ts.setVal(ret, null, lang);

        } else {
          String valStr = val.textValue();
          valText = ts.setVal(ret, valStr, lang);
        }

        // Update the map of document text to field name, so we can
        // output the docref attribute for spans
        if (true == isDocument) {
          docTextToFieldName.put(valText, fname);
          docFieldNameToText.put(fname, valText);
        }
      } else if (ftype.getIsSpan()) {
        if (!(val.isObject() || val.isNull())) {
          throw new AnnotationServiceException(
              "Received value of type %s for field %s in %s, but the type of that field is Span. "
                  + "Provide a value of type JSON record or null instead",
              val.getClass(), fname, viewName);
        }
        @SuppressWarnings("unchecked")
        FieldSetter<Span> setter = (FieldSetter<Span>) setters[i];

        if (val.isNull()) {
          setter.setVal(ret, null);
        } else {
          Span valSpan = jsonToSpan(val, fname, viewName, docTextToFieldName, docFieldNameToText);
          setter.setVal(ret, valSpan);
        }

      } else if (ftype.getIsIntegerType()) {
        if (!(val.isLong() || val.isInt() || val.isNull())) {
          throw new AnnotationServiceException(
              "Received value of type %s for field %s in %s, but the type of that field is Integer. "
                  + " Provide a value of type Integer, Long, or null instead",
              val.getClass(), fname, viewName);
        }

        @SuppressWarnings("unchecked")
        FieldSetter<Integer> setter = (FieldSetter<Integer>) setters[i];
        if (val.isNull()) {
          setter.setVal(ret, null);
        } else {
          int intVal = val.intValue();
          setter.setVal(ret, new Integer(intVal));
        }

      } else if (ftype.getIsFloatType()) {
        if (!(val.isFloat() || val.isDouble() || val.isNull())) {
          throw new AnnotationServiceException(
              "Received value of type %s for field %s in %s, but the type of that field is Float. "
                  + "Provide a value of type Float, Double, or null instead.",
              val.getClass(), fname, viewName);
        }

        @SuppressWarnings("unchecked")
        FieldSetter<Float> setter = (FieldSetter<Float>) setters[i];
        if (val.isNull()) {
          setter.setVal(ret, null);
        } else {
          double dblVal = val.doubleValue();
          setter.setVal(ret, Float.valueOf((float) dblVal));
        }

      } else if (ftype.getIsBooleanType()) {
        if (!(val.isBoolean() || val.isNull())) {
          throw new AnnotationServiceException(
              "Received value of type %s for field %s in %s, but the type of that field is Boolean. "
                  + "Provide a value of type Boolean or null instead.",
              val.getClass(), fname, viewName);
        }

        @SuppressWarnings("unchecked")
        FieldSetter<Boolean> setter = (FieldSetter<Boolean>) setters[i];
        if (val.isNull()) {
          setter.setVal(ret, null);
        } else {
          boolean boolVal = val.booleanValue();
          setter.setVal(ret, new Boolean(boolVal));
        }
      } else {
        throw new AnnotationServiceException(
            "Column '%s' of schema is of type %s, which cannot be converted from JSON object of type '%s'",
            fname, ftype, val.getClass().getName());
      }
    }

    return ret;
  }

  /**
   * @param jsonSpan JSON record representing the span to convert
   * @param fname name of the field in the view schema that the span belongs to
   * @param viewName name of SystemT view; for external views, this is the external name (from the
   *        <code>external_name</code> clause of the <code>create external view</code> statement)
   * @param docTextToFieldName Map of document field value (a Text) to field name in the input
   *        record
   * @param docFieldNameToText Map of document field name in the input record to field value (a
   *        Text)
   * @return
   * @throws AnnotationServiceException
   */
  private Span jsonToSpan(JsonNode jsonSpan, String fname, String viewName,
      HashMap<Text, String> docTextToFieldName, HashMap<String, Text> docFieldNameToText)
      throws AnnotationServiceException {
    int begin, end;

    // Grab the begin offset
    JsonNode val = JsonUtils.getVal(jsonSpan, AnnotationServiceConstants.SPAN_BEGIN_FIELD_NAME);
    if (false == (val.canConvertToLong()) && false == (val.canConvertToInt())) {
      throw new AnnotationServiceException(
          "Received value of type %s for %s field in %s of external view %s (with internal name %s); Provide a value of type Integer instead",
          val.getClass(), AnnotationServiceConstants.SPAN_BEGIN_FIELD_NAME, fname, viewName,
          externalToInternalName.get(viewName));
    }

    begin = val.intValue();

    // Grab the end offset
    val = JsonUtils.getVal(jsonSpan, AnnotationServiceConstants.SPAN_END_FIELD_NAME);
    if (false == (val.canConvertToLong()) && false == (val.canConvertToInt())) {
      throw new AnnotationServiceException(
          "Received value of type %s for %s field in %s of external view %s (with internal name %s); Provide a value of type Integer instead",
          val.getClass(), AnnotationServiceConstants.SPAN_END_FIELD_NAME, fname, viewName,
          externalToInternalName.get(viewName));
    }

    end = val.intValue();

    // Grab the doc ref (the field name in the input document record that
    // the span is over)
    val = jsonSpan.get(AnnotationServiceConstants.SPAN_DOCREF_FIELD_NAME);
    String docRef = null;

    if (null == val) {
      // Span doesn't have a docref field; look for content or text and
      // throw an exception if not found
      if (docFieldNameToText.containsKey(AnnotationServiceConstants.DOCUMENT_CONTENT_FIELD_NAME))
        docRef = AnnotationServiceConstants.DOCUMENT_CONTENT_FIELD_NAME;
      else if (docFieldNameToText.containsKey(AnnotationServiceConstants.DOCUMENT_TEXT_FIELD_NAME))
        docRef = AnnotationServiceConstants.DOCUMENT_TEXT_FIELD_NAME;
      else
        throw new AnnotationServiceException(
            "Span for field %s in external view %s (internal name is %s) does not have a %s field, but the input document record does not have any of the default content fields: %s or %s",
            fname, viewName, externalToInternalName.get(viewName),
            AnnotationServiceConstants.SPAN_DOCREF_FIELD_NAME,
            AnnotationServiceConstants.DOCUMENT_CONTENT_FIELD_NAME,
            AnnotationServiceConstants.DOCUMENT_TEXT_FIELD_NAME);
    } else {
      // Span has a docref
      docRef = JsonUtils.castToString(val, AnnotationServiceConstants.SPAN_DOCREF_FIELD_NAME,
          String.format("value %s of field %s in external view %s (internal name is %s)", jsonSpan,
              fname, viewName, externalToInternalName.get(viewName)));
    }

    // Obtain the text object corresponding to the docRef and create the
    // span
    Text docText = docFieldNameToText.get(docRef);
    if (null == docText)
      throw new AnnotationServiceException(
          "Span for field %s in external view %s (internal name is %s) cannot be created because the input document record does not contain a field %s",
          fname, viewName, externalToInternalName.get(viewName), docRef);

    return Span.makeBaseSpan(docText, begin, end);
  }

  /**
   * Subroutine of invoke() that converts tuples of external views from JSON.
   *
   * @throws AnnotationServiceException
   * @throws TextAnalyticsException
   * @throws IllegalArgumentException
   * @throws JSONException
   */
  private Map<String, TupleList> getExternalViewTups(JsonNode docRec, LangCode langCode,
      HashMap<Text, String> docTextToFieldName, HashMap<String, Text> docFieldNameToText)
      throws JSONException, IllegalArgumentException, TextAnalyticsException,
      AnnotationServiceException {
    HashMap<String, TupleList> extViewTups = new HashMap<String, TupleList>();

    for (String viewName : operatorGraph.getExternalViewNames()) {
      TupleSchema schema = operatorGraph.getExternalViewSchema(viewName);

      FieldSetter<? extends Object>[] setters = externalViewSetters.get(viewName);

      TupleList tups = new TupleList(schema);

      // The API takes "external" names (as defined in the external_name
      // clause) for external views.
      String externalViewName = operatorGraph.getExternalViewExternalName(viewName);

      // The call to getArrayVal() will return an error if the expected
      // view name isn't present, but we want a more user-friendly error
      // message for that case.
      if (false == docRec.has(externalViewName)) {
        throw new AnnotationServiceException(
            "Document record does not contain tuples for the external view %s, which has the external name '%s' (record is %s)",
            viewName, externalViewName, docRec);
      }

      ArrayNode jsonTups = JsonUtils.getArrayVal(docRec, externalViewName);
      for (int i = 0; i < jsonTups.size(); i++) {
        ObjectNode tupRecord =
            JsonUtils.castToRecord(jsonTups.get(i), externalViewName, "document");
        tups.add(jsonToTup(tupRecord, externalViewName, schema, setters, docTextToFieldName,
            docFieldNameToText, langCode, false));
      }

      extViewTups.put(viewName, tups);
    }

    return extViewTups;
  }

  /**
   * Convert result of SystemT to JSON.
   *
   * @param extractions SystemT result
   * @param docTextToFieldName Map of document field value (a Text) to field name in the input
   *        record
   *
   * @return ObjectNode all annotations on from the doc as JSON objects
   * @throws AnnotationServiceException
   * @throws TextAnalyticsException
   */
  private ObjectNode formatExtractionResults(Map<String, TupleList> extractions,
      HashMap<Text, String> docTextToFieldName)
      throws AnnotationServiceException, TextAnalyticsException {
    ObjectNode ret = JsonNodeFactory.instance.objectNode();

    for (String viewName : extractions.keySet()) {

      // All tuples of the output view
      TupleList tups = extractions.get(viewName);

      // create a json array for each view, and then load it up.
      ArrayNode records = JsonNodeFactory.instance.arrayNode();

      // The schema of the output view
      TupleSchema schema = operatorGraph.getSchema(viewName);

      // Iterate through the tuples of the output view
      TLIter itr = tups.iterator();
      while (itr.hasNext()) {
        Tuple tup = itr.next();

        // Add the prepared field JSON obj. to the output view array
        records.add(tupleToRecord(tup, schema, viewName, docTextToFieldName));

      }

      // put the loaded array in the output json for the output view
      ret.set(viewName, records);

    }

    // return the loaded json object;
    return ret;

  }

  /**
   * Convert a tuple to a JSON object.
   *
   * @param tup SystemT tuple
   * @param viewName the name of the AQL view that the tuple belongs to, for error reporting
   *        purposes; for external views, this is the internal view name
   * @param schema schema of the tuple
   * @param docTextToFieldName Map of document field value (a Text) to field name in the input
   *        record
   * @throws AnnotationServiceException
   */
  private ObjectNode tupleToRecord(Tuple tup, TupleSchema schema, String viewName,
      HashMap<Text, String> docTextToFieldName) throws AnnotationServiceException {
    ObjectNode ret = JsonNodeFactory.instance.objectNode();

    for (int i = 0; i < schema.size(); i++) {
      String colName = schema.getFieldNameByIx(i);
      FieldType ftype = schema.getFieldTypeByIx(i);
      Object val = schema.getCol(tup, i);

      JsonNode jval = fieldToVal(val, ftype, viewName, colName, docTextToFieldName);
      ret.set(colName, jval);
    }

    // Copy the annotator info if the user requested it
    if (annotateConfig.isSerializeAnnotatorInfo()) {
      JsonNode annotator = MAPPER.convertValue(annotateConfig.getAnnotator(), JsonNode.class);
      ret.set(AnnotationServiceConstants.ANNOTATOR_FIELD_NAME, annotator);
    }

    return ret;
  }

  /**
   * Convert one field of a tuple to JSON
   *
   * @param field field in the SystemT tuple
   * @param type type of the field, according to the schema
   * @param viewName the name of the view that the field belongs to, for error reporting purposes
   * @param fieldName the name of the field in the output view that the span belongs to, for error
   *        reporting purposes
   * @param docTextToFieldName Map of document field value (a Text) to field name in the input
   *        record
   * @return JSON object corresponding to the input field
   * @throws AnnotationServiceException
   */
  @SuppressWarnings("deprecation")
  private JsonNode fieldToVal(Object field, FieldType type, String viewName, String fieldName,
      HashMap<Text, String> docTextToFieldName) throws AnnotationServiceException {

    if (null == field) {
      // SPECIAL CASE: Null input --> null output
      return JsonNodeFactory.instance.nullNode();
      // END SPECIAL CASE
    }

    if (type.getIsText()) {
      // Text ==> JsonString
      return JsonNodeFactory.instance.textNode(((Text) field).getText());
    } else if (type.getIsSpan()) {
      // Span ==> treat according to policy
      return spanToJson((Span) field, viewName, fieldName, docTextToFieldName);
    } else if (FieldType.INT_TYPE == type) {
      // Integer
      return JsonNodeFactory.instance.numberNode((Integer) field);
    } else if (FieldType.FLOAT_TYPE == type) {
      // Float
      return JsonNodeFactory.instance.numberNode((Float) field);
    } else if (FieldType.BOOL_TYPE == type) {
      // Boolean
      return JsonNodeFactory.instance.booleanNode((Boolean) field);
    } else if (FieldType.STRING_TYPE == type) {
      // Raw String
      return JsonNodeFactory.instance.textNode((String) field);
    } else if (type.getIsScalarListType()) {
      // Scalar list ==> JSONArray

      // First determine the type of the elements in the list.
      FieldType elemType = type.getScalarListType();

      // Then recursively convert the elements and create a JSON array.
      @SuppressWarnings("rawtypes")
      ScalarList list = (ScalarList) field;

      ArrayNode ret = JsonNodeFactory.instance.arrayNode();
      for (int i = 0; i < list.size(); i++) {
        ret.insert(i, fieldToVal(list.get(i), elemType, viewName, fieldName, docTextToFieldName));
      }
      return ret;
    } else {
      throw new AnnotationServiceException(
          String.format("Don't know how to convert field type '%s' to JSON in view %s column %s",
              type, viewName, fieldName));
    }
  }

  /**
   * Serialize a span to a JSON record.
   *
   * @param span the input span
   * @param viewName the name of the output view that the span belongs to, for error reporting
   *        purposes
   * @param fieldName the name of the field in the output view that the span belongs to, for error
   *        reporting purposes
   * @param docTextToFieldName Map of document field value (a Text) to field name in the input
   *        record
   * @return JSON representation of the span as follows: simple (default)
   *         <ul>
   *         <li><i>begin</i> (mandatory): an Integer representing the span begin offset
   *         <li><i>end</i> (mandatory): an Integer representing the span end offset
   *         <li><i>docref</i>: a String representing the name of the field in the input document
   *         record that the span is over; if no such field exists (i.e., the span is over a text
   *         object generated by the annotator), this method throws an exception; this field is
   *         optional and it is serialized only for spans that are not over the default document
   *         fields {@link AnnotationServiceConstants#DOCUMENT_CONTENT_FIELD_NAME} or
   *         {@link AnnotationServiceConstants#DOCUMENT_TEXT_FIELD_NAME}
   *         </ul>
   *
   *         locationAndText
   *         <ul>
   *         <li>location
   *         <li><i>begin</i> (mandatory): an Integer representing the span begin offset
   *         <li><i>end</i> (mandatory): an Integer representing the span end offset
   *         <li><i>docref</i>: a String representing the name of the field in the input document
   *         record that the span is over; if no such field exists (i.e., the span is over a text
   *         object generated by the annotator), this method throws an exception; this field is
   *         optional and it is serialized only for spans that are not over the default document
   *         fields {@link AnnotationServiceConstants#DOCUMENT_CONTENT_FIELD_NAME} or
   *         {@link AnnotationServiceConstants#DOCUMENT_TEXT_FIELD_NAME}</li>
   *         <li><i>text</i></li> (mandatory): a String representing covered text by the offset
   *         begin and end
   *         </ul>
   *
   * @throws AnnotationServiceException when the span is not over a field of the input document
   *         record
   */
  private ObjectNode spanToJson(Span span, String viewName, String fieldName,
      HashMap<Text, String> docTextToFieldName) throws AnnotationServiceException {
    // need to build a span json obj to include in the output
    ObjectNode jspan = JsonNodeFactory.instance.objectNode();

    // For the detaggedText, get the detagged span before remap
    Span originalSpan = span;

    // Fix for AQL Web Tooling artifact with detagging
    // Detag outputs derived text and it does not match docText without remap
    if (span.getIsDerived()) {
      span = Span.makeRemappedSpan(span);
    }

    // Serialize the begin and end
    jspan.put(AnnotationServiceConstants.SPAN_BEGIN_FIELD_NAME, span.getBegin());
    jspan.put(AnnotationServiceConstants.SPAN_END_FIELD_NAME, span.getEnd());

    // Get the text that the span is over
    Text docText = span.getDocTextObj();

    String docFieldName = docTextToFieldName.get(docText);

    if (null == docFieldName) {
      // Didn't find the text in the input document record, throw an
      // exception
      throw new AnnotationServiceException(
          "Span [%d-%d] in %s.%s is over a string that does not appear in the input document record; modify the annotator to create spans over text objects that are present in the input document record",
          span.getBegin(), span.getEnd(), viewName, fieldName);
    } else if ((!AnnotationServiceConstants.DOCUMENT_CONTENT_FIELD_NAME.equals(docFieldName))
        && (!AnnotationServiceConstants.DOCUMENT_TEXT_FIELD_NAME.equals(docFieldName))) {
      // Serialize the field name only if it's not content or text

      jspan.put(AnnotationServiceConstants.SPAN_DOCREF_FIELD_NAME, docFieldName);
    }

    // (Default) Type of span serialization is "simple"
    if (serializeSpan.equals(AnnotatorBundleConfig.SerializeSpan.SIMPLE.getValue())) {
      return jspan;
    }
    // Type of span serialization is "locationAndText"
    // Move default span format under the "location", and add "text" field for covered text
    else if (serializeSpan
        .equals(AnnotatorBundleConfig.SerializeSpan.LOCATION_AND_TEXT.getValue())) {
      ObjectNode jspanLocationAndText = JsonNodeFactory.instance.objectNode();
      jspanLocationAndText.set(AnnotationServiceConstants.SPAN_LOCATION_FIELD_NAME, jspan);
      String text = originalSpan.getText();
      jspanLocationAndText.put(AnnotationServiceConstants.SPAN_TEXT_FIELD_NAME, text);
      return jspanLocationAndText;
    } else {
      throw new AnnotationServiceException(
          String.format("Unsupported span serialization type '%s' ", serializeSpan));
    }
  }

  /**
   * {@inheritDoc}
   * <p>
   * For SystemT:
   * <ul>
   * <li>Does not instantiate extractor</li>
   * </ul>
   * </p>
   */
  @Override
  public ObjectSchema getOutputSchema(List<String> inputFieldNames) throws Exception {
    Set<String> outputViewNames = new HashSet<>(Arrays.asList(this.operatorGraph.getOutputViews()));

    // Only get output schema for those specified in the configuration
    outputViewNames.retainAll(annotateConfig.getOutputTypes());
    // Remove fields of input schema from the output schema
    outputViewNames.removeAll(inputFieldNames);

    // A list used to collect fields that will be returned
    ObjectSchema outputSchema = JSFACTORY.objectSchema();
    for (String outputViewName : outputViewNames) {
      ViewMetadata viewMeta = this.operatorGraph.getViewMetadata(outputViewName);
      outputSchema.putProperty(outputViewName, getOutputViewSchema(viewMeta));
    }
    return outputSchema;
  }

  @Override
  public ObjectNode getShortOutputViewSchema(List<String> inputFieldNames) throws Exception {
    Set<String> outputViewNames = new HashSet<>(Arrays.asList(this.operatorGraph.getOutputViews()));

    // Only get output schema for those specified in the configuration
    outputViewNames.retainAll(annotateConfig.getOutputTypes());

    // Remove fields of input schema from the output schema
    outputViewNames.removeAll(inputFieldNames);

    ObjectMapper mapper = new ObjectMapper();
    ObjectNode outputNode = mapper.createObjectNode();
    for (String outputViewName : outputViewNames) {
      ViewMetadata viewMeta = this.operatorGraph.getViewMetadata(outputViewName);
      outputNode.set(outputViewName, getShortOutputViewSchema(viewMeta));
    }
    outputNode.put("serializeSpan", annotateConfig.getSerializeSpan());
    return outputNode;
  }

  @Override
  public void save(String zipFilePath, boolean compileDictionaries) throws Exception {
    SystemTAnnotatorBundleConfig systemTConfig = (SystemTAnnotatorBundleConfig) this.annotateConfig;

    new AnnotatorResources(systemTConfig, this.operatorGraph).save(zipFilePath,
        compileDictionaries);
  }

  /**
   * Converts SystemT ViewMetadata into Jackson ArraySchema to fit into the SystemT output schema
   * standard
   *
   * @param viewMeta
   * @return ArraySchema an array schema of an output view object schema
   * @throws AnnotationServiceException
   */
  static private ArraySchema getOutputViewSchema(ViewMetadata viewMeta)
      throws AnnotationServiceException {
    TupleSchema viewSchema = viewMeta.getViewSchema();

    String[] colNames = viewSchema.getFieldNames();
    FieldType[] colTypes = viewSchema.getFieldTypes();

    ObjectSchema itemFields = JSFACTORY.objectSchema();
    for (int i = 0; i < colNames.length; i++) {
      itemFields.putProperty(colNames[i], getDataType(colTypes[i]));
    }

    ArraySchema value = JSFACTORY.arraySchema();
    value.setItemsSchema(itemFields);
    return value;
  }

  /**
   * Converts SystemT ViewMetadata into Jackson ObjectNode, with a concise format than
   * {@link #getOutputSchema(List)} where only the output type key and its type is returned
   * 
   * @param viewMeta
   * @return
   */
  static private ObjectNode getShortOutputViewSchema(ViewMetadata viewMeta) {
    TupleSchema viewSchema = viewMeta.getViewSchema();

    String[] colNames = viewSchema.getFieldNames();
    FieldType[] colTypes = viewSchema.getFieldTypes();
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode itemFields = mapper.createObjectNode();
    for (int i = 0; i < colNames.length; i++) {
      if (colTypes[i].getIsScalarListType()) {
        itemFields.set(colNames[i],
            mapper.createArrayNode().add(colTypes[i].getScalarListType().toString()));
      } else
        itemFields.put(colNames[i], colTypes[i].toString());
    }
    return itemFields;
  }

  /**
   * Converts FieldType to JsonSchema
   *
   * @param type a field used by SystemT TupleSchema
   * @return JsonSchema
   * @throws AnnotationServiceException
   */
  static private JsonSchema getDataType(FieldType type) throws AnnotationServiceException {
    if (type.getIsNullType()) {
      return JSFACTORY.nullSchema();
    } else if (type.getIsText()) {
      return JSFACTORY.stringSchema();
    } else if (type.getIsIntegerType()) {
      // Corresponds to JSON Long
      return JSFACTORY.integerSchema();
    } else if (type.getIsFloatType()) {
      // Corresponds to JSON Double
      return JSFACTORY.numberSchema();
    } else if (type.getIsBooleanType()) {
      return JSFACTORY.booleanSchema();
    } else if (type.getIsSpan()) {
      // keys: begin, end, docref
      return createSpanSchema();
    } else if (type.getIsScalarListType()) {
      // Determine the type of the elements in the list.
      ArraySchema arraySchema = JSFACTORY.arraySchema();
      JsonSchema itemSchema = getDataType(type.getScalarListType());
      arraySchema.setItemsSchema(itemSchema);
      return arraySchema;
    } else {
      throw new AnnotationServiceException(
          String.format("Don't know how to convert SystemT FieldType '%s' to Jackson JsonSchema",
              type.toString()));
    }
  }
}
