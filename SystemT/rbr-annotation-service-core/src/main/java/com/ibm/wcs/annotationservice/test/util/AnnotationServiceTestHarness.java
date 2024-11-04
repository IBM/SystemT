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
package com.ibm.wcs.annotationservice.test.util;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.module.jsonSchema.types.ObjectSchema;
import com.ibm.avatar.algebra.util.tokenize.TokenizerConfig;
import com.ibm.avatar.api.CompileAQL;
import com.ibm.avatar.api.CompileAQLParams;
import com.ibm.wcs.annotationservice.AnnotationService;
import com.ibm.wcs.annotationservice.AnnotationServiceConstants;
import com.ibm.wcs.annotationservice.AnnotatorBundle;

import com.ibm.wcs.annotationservice.ExecuteParams;
import com.ibm.wcs.annotationservice.models.json.AnnotatorBundleConfig;

/**
 * Specialization of TestHarness for the Annotation Service.
 *
 */
public class AnnotationServiceTestHarness extends TestHarness {

  /*
   * GENERAL CONSTANTS
   */

  /** Encoding used whenever a method of this class reads or writes a file. */
  protected static final String ENCODING = "UTF-8";

  /**
   * Default value of {@link #progressIntervalDoc}
   */
  private static final int DEFAULT_PROG_INTERVAL_DOC = 1000;

  /*
   * FIELDS
   */

  /**
   * If this variable is set to true, then methods that run annotators won't produce any HTML
   * output. Initialized to false in startHook().
   */
  @SuppressWarnings("unused")
  private boolean disableOutput;

  /**
   * Interval (in documents) between subsequent progress reports to stderr when running an
   * annotator. Initialized in startHook().
   */
  @SuppressWarnings("unused")
  private int progressIntervalDoc;

  /**
   * Tokenizer configuration to use when compiling AQL code for the tests in this class. Initialized
   * in startHook().
   */
  private TokenizerConfig tokenizerCfg = new TokenizerConfig.Standard();

  /**
   * Default execution parameters to use by tests in this class. Initialized in startHook().
   */
  private ExecuteParams execParams = null;

  /**
   * Used by Jackson for JSON binding.
   */
  private static ObjectMapper mapper = new ObjectMapper();

  /*
   * METHODS
   */

  /**
   * Upcall from the superclass's startTest() method. Initializes the class's local variables.
   *
   * @throws Exception
   */
  @Override
  protected void startHook() {
    // Put in place some default values.
    disableOutput = false;
    progressIntervalDoc = DEFAULT_PROG_INTERVAL_DOC;
    tokenizerCfg = new TokenizerConfig.Standard();
  }

  @Override
  protected File getBaseDir() {
    // Use the value from TestConstants.
    return new File(TestConstants.TEST_WORKING_DIR);
  }

  /**
   * @param disableOutput if true, then methods that run extractors won't produce any HTML output.
   */
  protected void setDisableOutput(boolean disableOutput) {
    this.disableOutput = disableOutput;
  }

  /**
   * @param tokenizerCfg tokenizer configuration to use
   */
  protected void setTokenizerConfig(TokenizerConfig tokenizerCfg) {
    this.tokenizerCfg = tokenizerCfg;
  }

  /**
   * @param execParams execution parameters to use
   */
  protected void setExecuteParams(ExecuteParams execParams) {
    this.execParams = execParams;
  }

  /**
   * @return the tokenizerCfg
   */
  public TokenizerConfig getTokenizerCfg() {
    return tokenizerCfg;
  }

  /**
   * @return the execParams
   */
  public ExecuteParams getExecParams() {
    return execParams;
  }

  /**
   * Utility method to instantiate and run an AnnotationService on a single document. The resulting
   * JSON record is to three files at the following location
   *
   * <pre>
   * regression/actual/[test class name]/[test case name]/out.json
   * regression/actual/[test class name]/[test case name]/annotations.json
   * regression/actual/[test class name]/[test case name]/instrumentationInfo.json
   * </pre>
   *
   * If you want to compare the output to expected, make sure to set up the following files and use
   * {@link #compareAgainstExpected(boolean)}:
   *
   * <pre>
   * regression/actual/[test class name]/[test case name]/annotations.json
   * regression/actual/[test class name]/[test case name]/instrumentationInfo.json
   * </pre>
   *
   * NOTE: Do not include regression/actual/[test class name]/[test case name]/out.json for
   * comparison; the comparison for any file but instrumentationInfo.json is line by line, which
   * means your test will fail due to platform differences (e.g., the running time differs for
   * individual machines).
   *
   * @param docsFile .json file containing a single document, or NULL to look for a documents file
   *        customized to the needs of this particular test. Such custom files should be .json files
   *        located at:
   *
   *        <pre>
   * src/test/resources/docs/[test class name]/[test case name].json
   *        </pre>
   * 
   * @param annotatorCfgFile .json file containing an annotator bundle configuration specification,
   *        or NULL to look for a config file customized to the needs of this particular test. Such
   *        custom files should be .json files located at:
   *
   *        <pre>
   * src/test/resources/configs/[test class name]/[test case name].json
   *
   * &#64;param docsFile execution parameters for the input document
   *        </pre>
   */
  protected void genericAnnotationServiceTest(File docsFile, File annotatorCfgFile)
      throws Exception {

    String className = getClass().getSimpleName();

    // Compute the location of the documents file.
    if (null == docsFile) {
      // Caller wants us to look for a specific .json file
      docsFile = new File(TestConstants.TEST_DOCS_DIR,
          String.format("%s/%s.json", className, getCurPrefix()));
    }

    if (false == docsFile.exists()) {
      throw new Exception(String.format("Documents file %s not found", docsFile));
    }

    // Compute the location of the annotation config file.
    if (null == annotatorCfgFile) {
      // Caller wants us to look for a specific file
      annotatorCfgFile = new File(TestConstants.TEST_CONFIGS_DIR,
          String.format("%s/%s.json", className, getCurPrefix()));
    }

    if (false == annotatorCfgFile.exists()) {
      throw new Exception(
          String.format("Annotator bundle config file %s not found", annotatorCfgFile));
    }

    // Read the document and config file as JSON Records
    JsonNode jsonDoc = mapper.readTree(docsFile);
    AnnotatorBundleConfig jsonCfg = mapper.readValue(annotatorCfgFile, AnnotatorBundleConfig.class);

    // Run the Annotation Service
    AnnotationService service = new AnnotationService();
    JsonNode outputJson = service.invoke(jsonCfg, jsonDoc, execParams);
    JsonNode annotations = outputJson.get(AnnotationServiceConstants.ANNOTATIONS_FIELD_NAME);
    JsonNode instrumentationInfo =
        outputJson.get(AnnotationServiceConstants.INSTRUMENTATION_INFO_FIELD_NAME);

    // Write the result, as a single JSON file
    File outputFile = new File(getCurOutputDir(), String.format("out.json", className));
    mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, outputJson);

    // Write just the annotations and instrumentation info, so we can compare them
    outputFile = new File(getCurOutputDir(),
        String.format(TestConstants.ANNOTATIONS_JSON_FILE_NAME, className));
    if (null != annotations)
      mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, annotations);

    outputFile =
        new File(getCurOutputDir(), String.format(TestConstants.INFO_JSON_FILE_NAME, className));
    mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, instrumentationInfo);

    try {
      // Write the schema, as a single JSON file
      ObjectNode shortSchema = service.getShortOutputViewSchema(jsonCfg, new ArrayList<>());
      File shortSchemaFile =
          new File(getCurOutputDir(), String.format("concise_schema.json", className));
      mapper.writerWithDefaultPrettyPrinter().writeValue(shortSchemaFile, shortSchema);
    } catch (Exception e) {
      // pass on invalid models, concise_schema is compared for valid models
    }

  }

  /**
   * Utility method to instantiate and run an AnnotatorBundle on a single document. The resulting
   * JSON record is written to three files at the following location:
   *
   * <pre>
   * regression/actual/[test class name]/[test case name]/out.json
   * regression/actual/[test class name]/[test case name]/annotations.json
   * regression/actual/[test class name]/[test case name]/instrumentationInfo.json
   * </pre>
   *
   * If you want to compare the output to expected, make sure to set up the following files and use
   * {@link #compareAgainstExpected(boolean)}:
   *
   * <pre>
   * regression/actual/[test class name]/[test case name]/annotations.json
   * regression/actual/[test class name]/[test case name]/instrumentationInfo.json
   * </pre>
   *
   * NOTE: Do not include regression/actual/[test class name]/[test case name]/out.json for
   * comparison; the comparison for any file but instrumentationInfo.json is line by line, which
   * means your test will fail due to platform differences (e.g., the running time differs for
   * individual machines).
   *
   * @param docsFile .json file containing a single document, or NULL to look for a documents file
   *        customized to the needs of this particular test. Such custom files should be .json files
   *        located at:
   *
   *        <pre>
   * src/test/resources/docs/[test class name]/[test case name].json
   *        </pre>
   * 
   * @param annotatorCfgFile .json file containing an annotator bundle configuration specification,
   *        or NULL to look for a config file customized to the needs of this particular test. Such
   *        custom files should be .json files located at:
   *
   *        <pre>
   * src/test/resources/configs/[test class name]/[test case name].json
   *        </pre>
   * 
   * @param docsFile execution parameters for the input document
   */
  protected void genericAnnotatorBundleTest(File docsFile, File annotatorCfgFile) throws Exception {
    String className = getClass().getSimpleName();

    // Compute the location of the documents file.
    if (null == docsFile) {
      // Caller wants us to look for a specific .json file
      docsFile = new File(TestConstants.TEST_DOCS_DIR,
          String.format("%s/%s.json", className, getCurPrefix()));
    }

    if (false == docsFile.exists()) {
      throw new Exception(String.format("Documents file %s not found", docsFile));
    }

    // Compute the location of the annotation config file.
    if (null == annotatorCfgFile) {
      // Caller wants us to look for a specific file
      annotatorCfgFile = new File(TestConstants.TEST_CONFIGS_DIR,
          String.format("%s/%s.json", className, getCurPrefix()));
    }

    if (false == annotatorCfgFile.exists()) {
      throw new Exception(
          String.format("Annotator bundle config file %s not found", annotatorCfgFile));
    }

    // Read the document and config file as JSON Records
    JsonNode jsonDoc = mapper.readTree(docsFile);

    // Instantiate an Annotator Bundle
    AnnotatorBundleConfig cfg = mapper.readValue(annotatorCfgFile, AnnotatorBundleConfig.class);
    AnnotatorBundle annotBundle = new AnnotatorBundle(cfg);
    annotBundle.init();

    // Run the Annotator Bundle
    JsonNode outputJson = annotBundle.invoke(jsonDoc, execParams);
    JsonNode annotations =
        (ObjectNode) outputJson.get(AnnotationServiceConstants.ANNOTATIONS_FIELD_NAME);
    JsonNode instrumentationInfo =
        (ObjectNode) outputJson.get(AnnotationServiceConstants.INSTRUMENTATION_INFO_FIELD_NAME);

    // Write the schema, as a single JSON file
    ObjectNode shortSchema = annotBundle.getShortOutputViewSchema(new ArrayList<>());
    File shortSchemaFile =
        new File(getCurOutputDir(), String.format("concise_schema.json", className));
    mapper.writerWithDefaultPrettyPrinter().writeValue(shortSchemaFile, shortSchema);

    // Write the result, as a single JSON file
    File outputFile = new File(getCurOutputDir(), String.format("out.json", className));
    mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, outputJson);

    // Write just the annotations and instrumentation info, so we can compare them
    outputFile = new File(getCurOutputDir(),
        String.format(TestConstants.ANNOTATIONS_JSON_FILE_NAME, className));
    if (null != annotations)
      mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, annotations);

    outputFile =
        new File(getCurOutputDir(), String.format(TestConstants.INFO_JSON_FILE_NAME, className));
    mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, instrumentationInfo);


  }

  /**
   * Utility method to compile a set of AQL modules; the resulting .tam files are placed in the
   * current output directory of the test:
   * 
   * <pre>
   * regression/actual/[test class name]/[test case name]
   * </pre>
   * 
   * @param sourceModulesDir directory containing source AQL modules to compile; if
   *        <code>null</code>, default to:
   * 
   *        <pre>
   * src/test/resources/aql/[test class name]/[test case name]
   *        </pre>
   * 
   * @param moduleNames names of AQL modules in the source module directory to compile; if
   *        <code>null</code>, all source modules under that directory are compiled:
   * @param modulePath module path to search any pre-compiled modules needed to compile the source
   */
  protected void compileAQL(File sourceModulesDir, String[] moduleNames, String modulePath)
      throws Exception {

    String className = getClass().getSimpleName();

    // Compute the location of the source modules.
    if (null == sourceModulesDir) {
      // Caller wants us to look for a specific module dir
      sourceModulesDir =
          new File(TestConstants.AQL_DIR, String.format("%s/%s", className, getCurPrefix()));
    }

    if (false == sourceModulesDir.exists()) {
      throw new Exception(String.format("Source module directory %s not found", sourceModulesDir));
    }

    // Compute the modules to compile
    if (null == moduleNames) {
      // Caller wants us to look for the specific modules inside the sourceModuleDir; compile all
      // modules in the
      // directory
      moduleNames = sourceModulesDir.list(new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
          return new File(dir, name).isDirectory();
        }
      });
    }

    // Set up source modules URIs
    String[] sourceModulesURIs = new String[moduleNames.length];
    for (int i = 0; i < moduleNames.length; i++) {
      sourceModulesURIs[i] = new File(sourceModulesDir, moduleNames[i]).toURI().toString();
    }

    // Output dir for the compiled modules
    String outputURI = getCurOutputDir().toURI().toString();

    // Set up compilation parameters and compile
    CompileAQLParams params =
        new CompileAQLParams(sourceModulesURIs, outputURI, modulePath, tokenizerCfg);
    CompileAQL.compile(params);

  }
}
