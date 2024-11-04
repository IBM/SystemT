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
package com.ibm.avatar.algebra.util.test;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assert;

import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.TextSetter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.util.dict.DictFile;
import com.ibm.avatar.algebra.util.dict.DictMemoization;
import com.ibm.avatar.algebra.util.dict.DictParams;
import com.ibm.avatar.algebra.util.document.ToHTMLOutput;
import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.algebra.util.file.SearchPath;
import com.ibm.avatar.algebra.util.lang.LangCode;
import com.ibm.avatar.algebra.util.tokenize.StandardTokenizer;
import com.ibm.avatar.algebra.util.tokenize.TokenizerConfig;
import com.ibm.avatar.api.AQLProfiler;
import com.ibm.avatar.api.CompilationSummary;
import com.ibm.avatar.api.CompileAQL;
import com.ibm.avatar.api.CompileAQLParams;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.DocReader;
import com.ibm.avatar.api.ExternalTypeInfo;
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.api.exceptions.CompilerException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.compiler.Compiler;
import com.ibm.avatar.aql.compiler.CompilerWarning;
import com.ibm.avatar.aql.compiler.CompilerWarning.WarningType;
import com.ibm.avatar.aql.planner.Planner;
import com.ibm.avatar.aql.tam.ModuleMetadataImpl;
import com.ibm.avatar.aql.tam.ModuleUtils;
import com.ibm.avatar.aql.tam.TAM;
import com.ibm.avatar.aql.tam.TAMSerializer;
import com.ibm.avatar.logging.Log;

/**
 * Version of TestHarness specialized for the Runtime project.
 * 
 */
public class RuntimeTestHarness extends TestHarness {

  /*
   * CONSTANTS
   */

  /** Encoding used whenever a method of this class reads or writes a file. */
  @SuppressWarnings("unused")
  private static final String ENCODING = "UTF-8";

  /** Set this variable to true when regex strength reduction should be used. */
  private static final boolean USE_REGEX_STRENGTH_RED = true;

  /**
   * Default value of {@link #progressIntervalDoc}
   */
  private static final int DEFAULT_PROG_INTERVAL_DOC = 1000;

  /** Name of file within compiled module, which contains the compiled plan */
  private static final String PLAN_FILE_NAME = "plan.aog";

  /** Name of file within the compiled module, which contains the metadata */
  private static final String METADATA_FILE_NAME = "metadata.xml";

  /*
   * FIELDS
   */

  /**
   * If this is set to true, then methods that compile AQL will dump the plan to stderr. Initialized
   * to false in startHook().
   */
  private boolean dumpPlan;

  /**
   * If this variable is set to true, then methods that run annotators won't produce any HTML
   * output. Initialized to false in startHook().
   */
  private boolean disableOutput;

  /**
   * If this variable is set to true, then methods that generate HTML-format annotator output will
   * include tables of tuples. Initialized to false in startHook().
   */
  private boolean printTups;

  /**
   * If this variable is set to true, then methods that generate HTML-format annotator output will
   * write Charset info to the meta content header
   */
  private boolean writeCharset;

  /**
   * Path to find Dictionaries and UDFs
   */
  private String dataPath;

  /**
   * Interval (in documents) between subsequent progress reports to stderr when running an
   * annotator. Initialized in startHook().
   */
  private int progressIntervalDoc;

  /**
   * Tokenizer configuration to be used while running the test.Default tokenizer configuration; use
   * the {@link #setTokenizerConfig(TokenizerConfig)} method to set a custom tokenizer.
   */
  private TokenizerConfig tokenizerCfg = new TokenizerConfig.Standard();

  /**
   * Language of the document collection on which the test is performing extraction. Default
   * language is {@link LangCode#en}; use the {@link #setLanguage(LangCode)} method to change
   * document collection language.
   */
  private LangCode language = LangCode.en;

  private CompilationSummary summary;

  /*
   * METHODS
   */

  /**
   * Upcall from the superclass's startTest() method. Initializes the class's local variables.
   */
  @Override
  protected void startHook() {
    // Put in place some default values.
    dumpPlan = false;
    disableOutput = false;
    printTups = false;
    writeCharset = false;
    progressIntervalDoc = DEFAULT_PROG_INTERVAL_DOC;
  }

  @Override
  protected File getBaseDir() {
    // Use the value from TestConstants.
    return new File(TestConstants.TEST_WORKING_DIR);
  }

  /**
   * @param dumpPlan if true, then methods that compile AQL will dump the plan to stderr.
   */
  public void setDumpPlan(boolean dumpPlan) {
    this.dumpPlan = dumpPlan;
  }

  /**
   * @param progressInterval Interval (in documents) between subsequent progress reports to stderr
   *        when running an annotator.
   */
  public void setProgressInterval(int progressIntervalDoc) {
    this.progressIntervalDoc = progressIntervalDoc;
  }

  /**
   * @param disableOutput if true, then methods that run annotators won't produce any HTML output.
   */
  public void setDisableOutput(boolean disableOutput) {
    this.disableOutput = disableOutput;
  }

  /**
   * @param printTups if true, then methods that generate HTML-format annotator output will include
   *        tables of tuples.
   */
  public void setPrintTups(boolean printTups) {
    this.printTups = printTups;
  }

  /**
   * @return <code>true</code> if methods that generate HTML-format annotator output will include
   *         tables of tuples.
   */
  public boolean getPrintTups() {
    return printTups;
  }

  /**
   * @param writeCharset if true, then write the charset info into the META content-type header
   */
  public void setWriteCharsetInfo(boolean writeCharset) {
    this.writeCharset = writeCharset;
  }

  /**
   * @return <code>true</code> if methods that generate HTML-format extractor output will write the
   *         charset info into the META content-type header
   */
  public boolean getWriteCharsetInfo() {
    return writeCharset;
  }

  /**
   * @param dataPath sets the path for all dictionaries and UDFs
   */
  public void setDataPath(String dataPath) {
    this.dataPath = dataPath;
  }

  /**
   * Merges all dict/include/udf paths into one datapath
   * 
   * @param dictPath path for dictionary files
   * @param includePath path to look for included AQL
   * @param udfPath path for UDFs
   */
  public void setDataPath(String dictPath, String includePath, String udfPath) {

    // complicated logic to make sure we don't add to null
    this.dataPath = dictPath;

    if (dictPath == null) {
      this.dataPath = includePath;
      if (includePath == null) {
        this.dataPath = udfPath;
      } else if (udfPath != null) {
        this.dataPath += SearchPath.PATH_SEP_CHAR + udfPath;
      }
    } else {

      if (includePath != null) {
        this.dataPath += SearchPath.PATH_SEP_CHAR + includePath;
      }

      if (udfPath != null) {
        this.dataPath += SearchPath.PATH_SEP_CHAR + udfPath;
      }
    }
  }

  public String getDataPath() {
    return this.dataPath;
  }

  /**
   * Set the tokenizer to be used while running the test.
   * 
   * @param tokenizerCfg the tokenizer to be used while running the test
   */
  public void setTokenizerConfig(TokenizerConfig tokenizerCfg) {
    this.tokenizerCfg = tokenizerCfg;
  }

  /**
   * @return tokenizer configuration to be used for test case execution
   */
  public TokenizerConfig getTokenizerConfig() {
    return this.tokenizerCfg;
  }

  /**
   * Set the language of document collection on which extraction is performed.
   * 
   * @param language language of document collection
   */
  public void setLanguage(LangCode language) {
    this.language = language;
  }

  /**
   * @return AQL source directory for the currently active test case
   */
  public File getCurTestDir() throws Exception {
    if (null == getCurOutputDir()) {
      throw new Exception("getCurTestDir () called without calling startTest() first");
    }

    String className = getClass().getSimpleName();
    String testName = getCurPrefix();

    // Compute the location of the current test case's AQL code
    File moduleDir =
        new File(String.format("%s/%s/%s", TestConstants.AQL_DIR, className, testName));

    return moduleDir;
  }

  /**
   * Returns the location where precompiled module files are stored for the current test case
   * 
   * @return $workspace/Runtime/testdata/tam/<TestClassName>/<testName>
   * @throws Exception
   */
  public File getPreCompiledModuleDir() throws Exception {
    if (null == getCurOutputDir()) {
      throw new Exception("getPreCompiledModuleDir () called without calling startTest() first");
    }

    String className = getClass().getSimpleName();
    String testName = getCurPrefix();

    // Compute the location of the current test case's AQL code
    File tamDir = new File(
        String.format("%s/%s/%s", TestConstants.PRECOMPILED_MODULES_DIR, className, testName));

    return tamDir;
  }

  //
  // protected static String readFileToString(File inputFile) throws Exception
  // {
  // FileInputStream stream = new FileInputStream(inputFile);
  // try {
  // FileChannel fc = stream.getChannel();
  // MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0,
  // fc.size());
  // /* Instead of using default, pass in a decoder. */
  // return Charset.defaultCharset().decode(bb).toString();
  // }
  // finally {
  // stream.close();
  // }
  // }

  /**
   * Helper method to read the default AOG file as a string
   * 
   * @param docsFile
   * @param dictLocs location of dictionary files used by the AOG, encoded as pairs of dictionary
   *        name and file name (or null for no dicts)
   * @throws Exception
   */
  protected void runAOGFile(File docsFile, String[][] dictLocs) throws Exception {
    runAOGString(docsFile, (String) null, dictLocs);
  }

  /**
   * Helper method to read the AOG file as a string and pass it to runAOGString
   * 
   * @param docsFile file containing documents to run through the operator graph
   * @param aogFile file containing the operator graph to run
   * @param dictLocs location of dictionary files used by the AOG, encoded as pairs of dictionary
   *        name and file name (or null for no dicts)
   * @throws Exception
   */
  protected void runAOGFile(File docsFile, File aogFile, String[][] dictLocs) throws Exception {
    if (false == aogFile.exists()) {
      throw new Exception(String.format("AOG file %s not found.", aogFile));
    }

    String aog = FileUtils.fileToStr(aogFile, "UTF-8");

    runAOGString(docsFile, aog, dictLocs);
  }

  //
  // protected void printAOGFile (File aogFile) throws Exception
  // {
  //
  // String aog = FileUtils.fileToStr (aogFile, "UTF-8");
  //
  // printAOGStr (aog);
  // }
  //
  // /**
  // * Debug AOG Strings
  // */
  // protected void printAOGStr (String aog) throws Exception
  // {
  // // final String DEFAULT_DICTS_DIR = "." + "/testdata";
  //
  // AOGRunner runner;
  // try {
  // runner = AOGRunner.compileStr (aog, TestConstants.EMPTY_CD_MAP);
  // }
  // catch (Exception e) {
  // // Intercept compile errors and dump the plan.
  // File tmp = File.createTempFile ("plan", ".aog");
  //
  // System.err.printf ("Caught exception while compiling AOG string.\n" + "Dumping AOG to %s\n",
  // tmp);
  // FileWriter out = new FileWriter (tmp);
  // out.append (aog);
  // out.close ();
  //
  // throw e;
  // }
  //
  // runner.dumpPlan (System.out);
  //
  // }

  /**
   * Main method for running an AOG on a document
   * 
   * @param docsFile archive file containing documents to run through this test case
   * @param aog compiled operator graph
   * @param dictLocs location of dictionary files used by the AOG, encoded as pairs of dictionary
   *        name and file name (or null for no dicts)
   * @throws Exception
   */
  protected void runAOGString(File docsFile, String aog, String[][] dictLocs) throws Exception {

    if (null == getCurOutputDir()) {
      throw new RuntimeException("runAOGString() called without calling startTest() first");
    }

    String className = getClass().getSimpleName();

    // Read the AOG if not passed in
    if (aog == null) {
      File aogDir = new File(TestConstants.AOG_DIR, className);
      File aogFile = new File(aogDir, String.format("%s.aog", getCurPrefix()));

      if (false == aogFile.exists()) {
        throw new Exception(String.format("AOG file %s not found.", aogFile));
      }

      aog = FileUtils.fileToStr(aogFile, "UTF-8");
    }

    if (dumpPlan) {
      Log.debug("AOG plan is:\n%s\n", aog);
    }

    // Compute the location of the documents file.
    if (null == docsFile) {
      // Caller wants us to look for a specific
      docsFile = new File(TestConstants.TEST_DOCS_DIR,
          String.format("%s/%s.del", className, getCurPrefix()));
    }

    if (false == docsFile.exists()) {
      throw new Exception(String.format("Documents file/dir %s not found", docsFile));
    }

    // Open up the documents so that we can get at their schema information;
    // we'll keep them open so that we can scan them later on.
    DocReader docs = new DocReader(docsFile);
    TupleSchema docSchema = docs.getDocSchema();
    FieldGetter<Text> docTextGetter = docSchema.textAcc(Constants.DOCTEXT_COL);

    // Compile the AQL file.
    // CompileAQLParams params = new CompileAQLParams (aqlFile, null,
    // dataPath);
    // params.setPerformRSR (USE_REGEX_STRENGTH_RED);
    // TAM[] tams = CompileAQL.compileToTAM(params);
    // String aog = tams[0].getAog();

    // Generate a TAM file.
    final String moduleName = "genericModule";
    TAM tam = new TAM(moduleName);
    tam.setAog(aog);

    // create dummy empty meta data object; this is to make loader happy
    ModuleMetadataImpl dummyMD = ModuleMetadataImpl.createEmptyMDInstance();

    // we compile dictionaries from old testcases using the tokenizer specified in the test case
    dummyMD.setTokenizerType(tokenizerCfg.getName());
    tam.setMetadata(dummyMD);

    if (null != dictLocs) {
      // Caller has provided a list of dictionaries; add them to the TAM
      // file.
      for (String[] pair : dictLocs) {
        String dictName = pair[0];
        String dictPath = pair[1];

        DictParams params = new DictParams();
        params.setDictName(dictName);
        params.setFileName(dictPath);
        params.setLangStr("en");

        DictFile df = new DictFile(FileUtils.createValidatedFile(dictPath), params);

        tam.addDict(dictName, df.compile(new StandardTokenizer(), new DictMemoization()));
      }

    }

    TAMSerializer.serialize(tam, getCurOutputDir().toURI());

    // Instantiate the resulting operator graph.
    String modulePath = getCurOutputDir().toURI().toString();
    System.err.printf("Using module path '%s'\n", modulePath);

    OperatorGraph og =
        OperatorGraph.createOG(new String[] {moduleName}, modulePath, null, tokenizerCfg);

    // If we want to dump the output, get the list of views and their schemas from the operator
    // graph, otherwise use an
    // empty map
    Map<String, TupleSchema> outputViews =
        ((false == disableOutput) ? og.getOutputTypeNamesAndSchema()
            : new HashMap<String, TupleSchema>());

    // Initialize the utility object to write the output HTML files
    ToHTMLOutput toHtmlOut = new ToHTMLOutput(outputViews, getCurOutputDir(), this.getPrintTups(),
        this.getWriteCharsetInfo(), false, docSchema);

    int ndoc = 0;
    int nchar = 0;
    long startMs = System.currentTimeMillis();

    while (docs.hasNext()) {
      Tuple docTup = docs.next();

      // Execute
      Map<String, TupleList> annots = og.execute(docTup, null, null);

      // Append the results to the output HTML files
      if (false == disableOutput)
        toHtmlOut.write(docTup, annots);

      ndoc++;
      nchar += docTextGetter.getVal(docTup).getLength();

      // Generate some feedback for the user.
      if (progressIntervalDoc != 0) {
        if (0 == ndoc % progressIntervalDoc) {
          printProgress(ndoc, nchar, startMs);
        }
      } else {
        throw new RuntimeException("progressIntervalDoc was never initialized, call startTest()");
      }
    }

    // Close the output files.
    toHtmlOut.close();

    // Close the document reader
    docs.remove();

    // Print the final statistics
    printProgress(ndoc, nchar, startMs);
  }

  /**
   * Helper method to run the default AQL file
   * 
   * @param docsFile
   * @throws Exception
   */
  protected void runNonModularAQLTest(File docsFile) throws Exception {
    runNonModularAQLTest(docsFile, (File) null);
  }

  protected void runNonModularAQLTest(File docsFile, String filename) throws Exception {
    runNonModularAQLTest(docsFile, FileUtils.createValidatedFile(filename));
  }

  /**
   * Main utility method to run a single test case involving a top-level AQL file with the same name
   * as the current test. Assumes that the top-level AQL file is located in
   * 
   * <pre>
   * testdata/aql/[test class name]/[test case name].aql
   * </pre>
   * 
   * Assumes that any dictionary or include files referenced in the top-level AQL file are located
   * relative to the containing directory.
   * 
   * @param docsFile file (or directory) containing documents, or NULL to look for a documents file
   *        customized to the needs of this particular test. Such custom files should be DB2 dump
   *        files located at
   * 
   *        <pre>
   * testdata/docs/[test class name]/[test case name].del
   *        </pre>
   */
  protected void runNonModularAQLTest(File docsFile, File aqlFile) throws Exception {

    if (null == getCurOutputDir()) {
      throw new Exception("runAQLTest() called without calling startTest() first");
    }

    String className = getClass().getSimpleName();

    // Compute the location of the current test case's top-level AQL file.
    File aqlDir = new File(TestConstants.AQL_DIR, className);
    if (aqlFile == null) {
      aqlFile = new File(aqlDir, String.format("%s.aql", getCurPrefix()));
    }

    if (false == aqlFile.exists()) {
      throw new Exception(String.format("AQL file %s not found.", aqlFile));
    }

    // Compute the location of the documents file.
    if (null == docsFile) {
      // Caller wants us to look for a specific doc file
      docsFile = new File(TestConstants.TEST_DOCS_DIR,
          String.format("%s/%s.del", className, getCurPrefix()));
    }

    if (false == docsFile.exists()) {
      throw new Exception(String.format("Documents file/dir %s not found", docsFile));
    }

    if (dataPath == null) {
      dataPath = aqlDir.getPath();
    }

    // if (getCurOutputDir() == null) {
    // setOutputDir(TestConstants.TEST_WORKING_DIR + "/" + className);
    // }

    // Compile the AQL file and serialize the TAM to testcase directory.
    CompileAQLParams params = new CompileAQLParams(aqlFile, null, dataPath);
    String moduleURI = getCurOutputDir().toURI().toString();
    params.setPerformRSR(USE_REGEX_STRENGTH_RED);
    params.setOutputURI(moduleURI);
    params.setTokenizerConfig(tokenizerCfg);

    Log.info("Compiler Parameters are:\n%s", params);

    try {
      CompileAQL.compile(params);
    } catch (CompilerException e) {
      // Print some useful information for dealing with broken test cases to STDERR
      dumpCompileErrors(e);
    }

    if (dumpPlan) {
      TAM tam = TAMSerializer.load(Constants.GENERIC_MODULE_NAME, moduleURI);
      Log.debug("AOG plan is:\n%s\n", tam.getAog());
    }

    // Instantiate the resulting operator graph.
    OperatorGraph og = OperatorGraph.createOG(new String[] {Constants.GENERIC_MODULE_NAME},
        moduleURI, null, tokenizerCfg);

    annotateAndPrint(docsFile, og);

  }

  /**
   * Utility method to run a single test case involving a top-level AQL file with the same name as
   * the current test. Assumes that the top-level AQL file is located in
   *
   * <pre>
   * testdata/aql/[test class name]/[test case name].aql
   * </pre>
   *
   * Assumes that any dictionary or include files referenced in the top-level AQL file are located
   * relative to the containing directory. Differs from
   * {@link RuntimeTestHarness#runNonModularAQLTest(File, File)} in that the output from each output
   * view is written to a CSV file
   *
   * @param docsFile file (or directory) containing documents, or NULL to look for a documents file
   *        customized to the needs of this particular test. Such custom files should be DB2 dump
   *        files located at
   *
   *        <pre>
   * testdata/docs/[test class name]/[test case name].del
   *        </pre>
   */
  protected void runNonModularAQL(File docsFile, File aqlFile) throws Exception {

    if (null == getCurOutputDir()) {
      throw new Exception("runAQLTest() called without calling startTest() first");
    }

    String className = getClass().getSimpleName();

    // Compute the location of the current test case's top-level AQL file.
    File aqlDir = new File(TestConstants.AQL_DIR, className);
    if (aqlFile == null) {
      aqlFile = new File(aqlDir, String.format("%s.aql", getCurPrefix()));
    }

    if (false == aqlFile.exists()) {
      throw new Exception(String.format("AQL file %s not found.", aqlFile));
    }

    // Compute the location of the documents file.
    if (null == docsFile) {
      // Caller wants us to look for a specific doc file
      docsFile = new File(TestConstants.TEST_DOCS_DIR,
          String.format("%s/%s.del", className, getCurPrefix()));
    }

    if (false == docsFile.exists()) {
      throw new Exception(String.format("Documents file/dir %s not found", docsFile));
    }

    if (dataPath == null) {
      dataPath = aqlDir.getPath();
    }

    // Compile the AQL file and serialize the TAM to testcase directory.
    CompileAQLParams params = new CompileAQLParams(aqlFile, null, dataPath);
    String moduleURI = getCurOutputDir().toURI().toString();
    params.setPerformRSR(USE_REGEX_STRENGTH_RED);
    params.setOutputURI(moduleURI);
    params.setTokenizerConfig(tokenizerCfg);

    Log.info("Compiler Parameters are:\n%s", params);

    try {
      CompileAQL.compile(params);
    } catch (CompilerException e) {
      // Print some useful information for dealing with broken test cases to STDERR
      dumpCompileErrors(e);
    }

    if (dumpPlan) {
      TAM tam = TAMSerializer.load(Constants.GENERIC_MODULE_NAME, moduleURI);
      Log.debug("AOG plan is:\n%s\n", tam.getAog());
    }

    // Instantiate the resulting operator graph.
    OperatorGraph og = OperatorGraph.createOG(new String[] {Constants.GENERIC_MODULE_NAME},
        moduleURI, null, tokenizerCfg);

    int ndoc = 0;
    int nchar = 0;
    long startMs = System.currentTimeMillis();

    DocReader docs = new DocReader(docsFile);
    if (language != LangCode.DEFAULT_LANG_CODE)
      docs.overrideLanguage(language);

    FieldGetter<Text> docTextGetter = docs.getDocSchema().textAcc(Constants.DOCTEXT_COL);

    while (docs.hasNext()) {
      Tuple docTup = docs.next();
      Map<String, TupleList> annotations = og.execute(docTup, null, null);
      for (String view : annotations.keySet()) {
        PrintWriter writer = new PrintWriter(String.format("%s/%s.csv", getCurOutputDir(), view));
        writer.write(og.getSchema(view).toString() + "\n");
        TLIter tupleItr = annotations.get(view).iterator();
        while (tupleItr.hasNext()) {
          writer.write(tupleItr.next().toCSVString() + "\n");
        }
        writer.close();
      }

      ndoc++;
      nchar += docTextGetter.getVal(docTup).getLength();

      if (0 == ndoc % progressIntervalDoc) {
        printProgress(ndoc, nchar, startMs);
      }

    }
    docs.remove();
  }


  /**
   * Print information about the compile errors in a CompilerException to STDERR, then rethrow the
   * original exception.
   * 
   * @param e compiler error output containing multiple errors rolled into one exception
   * @throws CompilerException the original exception gets rethrown
   */
  protected void dumpCompileErrors(CompilerException e) throws CompilerException {
    System.err.printf("Caught CompilerException.  Dumping stack traces...\n");
    for (Exception error : e.getSortedCompileErrors()) {
      error.printStackTrace();
    }
    throw e;
  }

  /**
   * Utility method to compile a single test case involving a top-level AQL file with the same name
   * as the current test and verify that compilation fails with the expected errors. For each
   * ParseException, verifies if it is thrown with the input line number. If the column number is
   * larger than -1, it also verifies that the ParseException is thrown with the appropriate column
   * information. If you're not interested in verifying the column, call this method with
   * expectedColNumber=-1. Assumes that the top-level AQL file is located in:
   * 
   * <pre>
   * testdata/aql/[test class name]/[test case name].aql
   * </pre>
   * 
   * Assumes that any dictionary or include files referenced in the top-level AQL file are located
   * relative to the containing directory.
   * 
   * @param lineNo list of line numbers where a ParseException is expected. If expecting 0
   *        exceptions, pass a null
   * @param colNo list of column numbers where a ParseException is expected. If expecting 0
   *        exceptions, pass a null. If the caller is expecting a ParseException with a line number,
   *        but no column number, specify -1 as the column number.
   * @throws Exception
   */
  protected void compileAQLTest(int[] lineNo, int[] colNo) throws Exception {

    String className = getClass().getSimpleName();

    // Compute the location of the current test case's top-level AQL file.
    File aqlDir = new File(TestConstants.AQL_DIR, className);
    File aqlFile = new File(aqlDir, String.format("%s.aql", getCurPrefix()));

    if (false == aqlFile.exists()) {
      throw new Exception(String.format("AQL file %s not found.", aqlFile));
    }

    if (dataPath == null) {
      dataPath = aqlDir.getPath();
    }

    // Prepare the appropriate parameters to compile the AQL file.
    CompileAQLParams params =
        new CompileAQLParams(aqlFile, getCurOutputDir().toURI().toString(), dataPath);
    params.setTokenizerConfig(getTokenizerConfig());

    // Use the generic method to compile and check that we receive the expected exceptions
    compileAndCheckExceptions(params, lineNo, colNo);
  }

  /**
   * Utility method to compile an extractor given a CompileAQLParams object and verify that
   * compilation fails with the expected errors. For each ParseException, verifies if it is thrown
   * with the input line number. If the column number is larger than -1, it also verifies that the
   * ParseException is thrown with the appropriate column information. If you're not interested in
   * verifying the column, call this method with expectedColNumber=-1.
   * 
   * @param params AQL compilation parameters
   * @param lineNo list of line numbers where a ParseException is expected. If expecting 0
   *        exceptions, pass a null
   * @param colNo list of column numbers where a ParseException is expected. If expecting 0
   *        exceptions, pass a null. If the caller is expecting a ParseException with a line number,
   *        but no column number, specify -1 as the column number.
   */
  protected void compileAndCheckExceptions(CompileAQLParams params, int[] lineNo, int[] colNo)
      throws Exception {

    try {
      CompileAQL.compile(params);
    } catch (CompilerException e) {
      System.err.printf("\nFound CompilerException: %s", e.getMessage());
      checkException(e, lineNo, colNo);
    } catch (Exception e1) {
      throw new Exception(String.format("Expected a CompilerException, instead got:%s\n%s",
          e1.getClass(), e1.getMessage()));
    }

  }

  /**
   * Utility method to check that the given CompilerException contains exceptions at all expected
   * locations. For each individual exception within the given CompilerException, verifies if it is
   * thrown with the expected line number. If the column number is larger than -1, it also verifies
   * that the ParseException is thrown with the appropriate column information. If you're not
   * interested in verifying the column, call this method with expectedColNumber=-1.
   * 
   * @param e compiler exception
   * @param lineNo list of line numbers where an Exception is expected. If expecting 0 exceptions,
   *        pass a null
   * @param colNo list of column numbers where an Exception is expected. If expecting 0 exceptions,
   *        pass a null. If the caller is expecting a ParseException with a line number, but no
   *        column number, specify -1 as the column number.
   */
  protected void checkException(CompilerException e, int[] lineNo, int[] colNo) throws Exception {

    List<Exception> exceptions = e.getSortedCompileErrors();
    String message = e.getMessage();

    // Try-catch block prints out additional info if the test fails
    try {

      // Fail if the caller is expecting no exceptions
      if (null == lineNo && null == colNo) {
        if (exceptions.size() != 0) {
          throw new TextAnalyticsException(
              "Expected zero exceptions. Got %d exceptions instead:\n%s\n", exceptions.size(),
              message);
        }
      }

      if (null == lineNo || null == colNo) {
        throw new Exception(
            "Exactly one of expected exception line and column number lists is null.");
      }

      // Fail if the caller is expecting a different number of exceptions
      if (exceptions.size() != lineNo.length) {
        throw new TextAnalyticsException("Expected %d exceptions. Got %d exceptions instead:\n%s\n",
            lineNo.length, exceptions.size(), message);
      }

      // Iterate through all of the exceptions
      int eIx = 0;
      for (Exception pe : exceptions) {
        if (pe instanceof ParseException) {

          String msg = pe.getMessage();
          System.err.printf("\nFound ParseException: %s", msg);

          // Get the line number and column number of the exception
          Pattern p = Pattern.compile("line (\\d+), column (\\d+)");
          Matcher m = p.matcher(msg);
          if (m.find()) {
            int actualLineNumber = Integer.parseInt(m.group(1));

            if (lineNo[eIx] != actualLineNumber) {
              throw new TextAnalyticsException(
                  "Expected ParseException on line %d. Got instead: %s", lineNo[eIx], msg);
            }

            if (colNo[eIx] >= 0) {
              int actualColNumber = Integer.parseInt(m.group(2));
              if (colNo[eIx] != actualColNumber) {
                throw new TextAnalyticsException(
                    "Expected ParseException on column %d. Got instead: %s", colNo[eIx], msg);
              }
            }
          } else {
            // Got a ParseException without a line number.
            throw new TextAnalyticsException(
                "Expected a ParseException on line %d. Got a ParseException without a line number instead: %s",
                lineNo[eIx], msg);
          }
        } else
          // Throw any exception that is not a ParseException. The caller
          // should catch this exception and validate it as necessary
          throw new Exception(pe);
        eIx++;
      }
      System.err.printf("\n");
    } catch (Exception testCaseException) {
      // Common code to print out additional information to STDERR in the event the test case fails
      System.err.printf("\n\nTest case failed.  Dumping stack traces for compile errors.\n");
      for (Exception err : exceptions) {
        err.printStackTrace();
      }

      throw testCaseException;
    }
  }

  /**
   * Utility method to compile the given top level AQL and serialize the compiled TAM, to invoking
   * testcase directory.
   * 
   * @param mainAQLFile handle to top level AQL file.
   * @param dataPath path to dictionaries, aqls and udfs being referred in the top level AQL file.
   *        The path can contain multiple directories, separated by the character ';'.
   * @return CompilationSummary object
   */
  protected CompilationSummary compileAQL(File mainAQLFile, String dataPath) throws Exception {
    CompileAQLParams compileParam = new CompileAQLParams();
    return compileAQL(mainAQLFile, dataPath, compileParam);
  }

  /**
   * Utility method to compile the given top level AQL and serialize the compiled TAM, to invoking
   * testcase directory.
   * 
   * @param mainAQLFile handle to top level AQL file.
   * @param dataPath path to dictionaries, aqls and udfs being referred in the top level AQL file.
   *        The path can contain multiple directories, separated by the character ';'.
   * @param compileParam parameters for the compilation. Its inputFile, dataPath and OutputURI will
   *        be overwritten.
   * @return CompilationSummary object
   */
  protected CompilationSummary compileAQL(File mainAQLFile, String dataPath,
      CompileAQLParams compileParam) throws Exception {
    String moduleURI = getCurOutputDir().toURI().toString();

    compileParam.setInputFile(mainAQLFile);
    compileParam.setDataPath(dataPath);
    compileParam.setOutputURI(moduleURI);
    compileParam.setTokenizerConfig(getTokenizerConfig());

    CompilationSummary summary;
    try {
      summary = CompileAQL.compile(compileParam);
    } catch (CompilerException e) {
      dumpCompileErrors(e);
      summary = e.getCompileSummary();
    }

    return summary;
  }

  /**
   * Method to instantiate an OperatorGraph from the compiled AQL in the current test case's output
   * dir
   * 
   * @return The instantiated OperatorGraph object
   * @throws Exception
   */
  public OperatorGraph instantiateOperatorGraph(String moduleName) throws Exception {
    String moduleURI = getCurOutputDir().toURI().toString();
    OperatorGraph og = OperatorGraph.createOG(new String[] {moduleName}, moduleURI, null, null);
    return og;
  }

  /**
   * Compiles the given module. Locates the source from
   * $project/testdata/<TestClassName>/<currentTestName>/<moduleName>. Outputs the TAM to
   * $project/regression/actual/<currentTestName>, where currentTestName is identified by curPrefix.
   * Use this method to compile any dependent modules required by your test.
   * 
   * @param moduleName Name of the module to compile
   * @return the summary object
   * @throws Exception
   */
  protected CompilationSummary compileModule(String moduleName) throws Exception {
    return compileModules(new String[] {moduleName}, null);
  }

  /**
   * Compiles the given list of modules to the URI specified in compiledModuleURI. Locates the
   * source from $project/testdata/<TestClassName>/<currentTestName>/<moduleName>. Outputs the TAM
   * to $project/regression/actual/<currentTestName>, where currentTestName is identified by
   * curPrefix. Use this method to compile any dependent modules required by your test.
   * 
   * @param moduleNames Names of the modules to compile
   * @param compiledModuleDestination Name of archive file to output compiled module to (if null,
   *        creates a TAM for each module)
   * @return the summary object
   * @throws Exception
   */
  protected CompilationSummary compileModules(String[] moduleNames,
      String compiledModuleDestination) throws Exception {
    // Compute the location of the current test case's top-level AQL file.
    File aqlModuleDir = getCurTestDir();

    String compiledModuleURI;

    // URI where compiled modules should be dumped -- set to regression/actual
    if (compiledModuleDestination == null) {
      compiledModuleURI = getCurOutputDir().toURI().toString();
    } else {
      compiledModuleURI = getCurOutputDir().toURI().toString() + compiledModuleDestination;
    }

    // set up the directories containing the input modules we want to compile
    ArrayList<String> moduleDirectories = new ArrayList<String>();

    for (String moduleName : moduleNames) {
      String moduleDirectoryName = new File(String.format("%s/%s", aqlModuleDir, moduleName))
          .getCanonicalFile().toURI().toString();

      moduleDirectories.add(moduleDirectoryName);
    }

    // Compile
    CompileAQLParams params = new CompileAQLParams(moduleDirectories.toArray(new String[1]),
        compiledModuleURI, compiledModuleURI, tokenizerCfg);

    CompilationSummary summary;
    try {
      summary = CompileAQL.compile(params);
    } catch (CompilerException e) {
      // Generate some more useful STDERR output than "an error occurred somewhere and I won't tell
      // you where"
      List<Exception> errors = e.getSortedCompileErrors();
      System.err.printf("AQL compilation encountered %d errors.  Printing stack traces.\n",
          errors.size());

      for (Exception exception : errors) {
        exception.printStackTrace();
      }

      // Trigger downstream testing code.
      throw e;
    }
    return summary;
  }

  /**
   * Utility method to compile a single module and check for error line numbers and column numbers
   * 
   * @return the summary object
   * @throws Exception
   */
  protected void compileModuleAndCheckErrors(String moduleName, int[] lineNos, int[] colNos)
      throws Exception {
    try {
      summary = compileModule(moduleName);
    } catch (CompilerException e) {
      checkException(e, lineNos, colNos);
      summary = e.getCompileSummary();

      return;
    } catch (Exception e1) {
      throw new Exception(String.format("Expected a CompilerException, instead got:%s\n%s",
          e1.getClass(), e1.getMessage()));
    }
    // If here, compiler is not returning expected errors
    throw new Exception("No exceptions; expected a CompilerException");
  }

  /**
   * Utility method to compile multiple modules and check for error line numbers and column
   * numbers.Assumes that the source code for all the modules to compile are located in
   * 
   * <pre>
   * testdata/aql/[test class name]/[test case name]/[module name]
   * </pre>
   * 
   * @param moduleToCompile name of the modules to be compiled
   * @param lineNo list of line numbers where an Exception is expected. If expecting 0 exceptions,
   *        pass a null
   * @param colNo list of column numbers where an Exception is expected. If expecting 0 exceptions,
   *        pass a null. If the caller is expecting a ParseException with a line number, but no
   *        column number, specify -1 as the column number.
   * @return the summary object
   * @throws Exception
   */
  protected CompilationSummary compileMultipleModulesAndCheckErrors(String[] moduleToCompile,
      int[] lineNos, int[] colNos) throws Exception {
    try {
      File testAQLDir = getCurTestDir();

      if (false == testAQLDir.exists()) {
        throw new Exception(String.format("Directory containing modules %s not found.",
            testAQLDir.getAbsolutePath()));
      }

      // Some basic sanity check
      if (null == moduleToCompile)
        throw new Exception("Provide at least one module to compiler");

      // Construct module URIs from module names
      String[] moduleToCompileURIs = new String[moduleToCompile.length];
      for (int i = 0; i < moduleToCompileURIs.length; i++) {
        String uri = new File(testAQLDir, moduleToCompile[i]).toURI().toString();
        moduleToCompileURIs[i] = uri;
      }

      // Prepare compilation parameter
      CompileAQLParams param = new CompileAQLParams();
      param.setInputModules(moduleToCompileURIs);
      param.setOutputURI(getCurOutputDir().toURI().toString());
      param.setTokenizerConfig(getTokenizerConfig());

      // Compile
      summary = CompileAQL.compile(param);
      Assert.fail("Expected compiler errors, but no errors were thrown");
    } catch (CompilerException ce) {
      checkException(ce, lineNos, colNos);
      summary = ce.getCompileSummary();
    }
    return summary;
  }

  /**
   * Utility method to compile single module and later load the operator graph from compiled module.
   * This method takes an optional {@link ExternalTypeInfo} instance; using this consumer an pass in
   * external artifacts(dictionaries/tables) to be loaded into operator graph. <br>
   * The method assumes the name of the test module to be same as the test name, and look for the
   * source of the module under /testdata/aql/testharness directory.
   * 
   * @param eti optional external artifacts object to be loaded into operator graph.
   * @return loaded operator graph
   * @throws Exception
   */
  protected OperatorGraph compileAndLoadModule(String moduleName, ExternalTypeInfo eti)
      throws Exception {
    // URI where compiled modules should be dumped
    String compiledModuleURI = new File(String.format("%s", getCurOutputDir())).toURI().toString();

    summary = compileModule(moduleName);
    if (summary.getCompilerWarning().size() > 0) {
      Log.info("Compiled %d AQL statements...%d warnings.", summary.getNumberOfViewsCompiled(),
          summary.getCompilerWarning().size());
    } else {
      Log.info("Compiled %d AQL statements...", summary.getNumberOfViewsCompiled());
    }

    return OperatorGraph.createOG(new String[] {moduleName}, compiledModuleURI, eti, tokenizerCfg);
  }

  /**
   * Utility method to compile a list of modules and later load the operator graph from compiled
   * modules. This method takes an optional {@link ExternalTypeInfo} instance; using this consumer
   * an pass in external artifacts(dictionaries/tables) to be loaded into operator graph. <br>
   * The method assumes the name of the test module to be same as the test name, and look for the
   * source of the module under /testdata/aql/testharness directory.
   * 
   * @param moduleNames a list of modules to compile and load
   * @param eti optional external artifacts object to be loaded into operator graph.
   * @return loaded operator graph
   * @throws Exception
   */
  protected OperatorGraph compileAndLoadModules(String[] moduleNames, ExternalTypeInfo eti)
      throws Exception {
    // URI where compiled modules should be dumped
    String compiledModuleURI = new File(String.format("%s", getCurOutputDir())).toURI().toString();

    summary = compileModules(moduleNames, null);
    Log.info("Compiled %d AQL statements...", summary.getNumberOfViewsCompiled());

    return OperatorGraph.createOG(moduleNames, compiledModuleURI, eti, tokenizerCfg);
  }

  /**
   * Utility method to compile, load and run single module against the given document collection.
   * This method takes an optional {@link ExternalTypeInfo} instance; using this consumer an pass in
   * external artifacts(dictionaries/tables) to be loaded into operator graph.<br>
   * The method assumes the name of the test module to compile, to be same as the test name, and
   * look for the source of the module under /testdata/aql/testharness directory.
   * 
   * @param docsFile file (or directory) containing documents, or NULL to look for a documents file
   *        customized to the needs of this particular test. Such custom files should be DB2 dump
   *        files located at
   * 
   *        <pre>
   *  testdata/docs/[test class name]/[test case name].del
   *        </pre>
   */
  protected void compileAndRunModule(String moduleName, File docsFile, ExternalTypeInfo eti)
      throws Exception {
    String className = getClass().getSimpleName();

    // Compute the location of the documents file.
    if (null == docsFile) {
      // Caller wants us to look for a specific
      docsFile = new File(TestConstants.TEST_DOCS_DIR,
          String.format("%s/%s.del", className, getCurPrefix()));
    }

    if (false == docsFile.exists()) {
      throw new Exception(String.format("Documents file/dir %s not found", docsFile));
    }

    // compile and load the module
    try {
      OperatorGraph og = compileAndLoadModule(moduleName, eti);
      annotateAndPrint(docsFile, og);
    } catch (CompilerException e) {
      // If a test case encounters compile errors, echo full stack trace information to STDERR
      System.err.printf(
          "Encountered %d error(s) during compilation.  Printing stack traces of errors:\n",
          e.getAllCompileErrors().size());
      for (Exception error : e.getAllCompileErrors()) {
        error.printStackTrace();
      }

      // Rethrow the exception unchanged so as not to mess up upstream code that checks errors
      throw e;
    }

  }

  /**
   * Utility method to compile, load and run a set of modules against the given document collection.
   * This method takes an optional {@link ExternalTypeInfo} instance; using this consumer an pass in
   * external artifacts(dictionaries/tables) to be loaded into operator graph.<br>
   * The method assumes the name of the test module to compile, to be same as the test name, and
   * look for the source of the module under /testdata/aql/testharness directory.
   * 
   * @param moduleNames an array of modules to compile and run
   * @param docsFile file (or directory) containing documents, or NULL to look for a documents file
   *        customized to the needs of this particular test. Such custom files should be DB2 dump
   *        files located at <code>
   *  testdata/docs/[test class name]/[test case name].del
   * </code>
   */
  protected void compileAndRunModules(String[] moduleNames, File docsFile, ExternalTypeInfo eti)
      throws Exception {
    String className = getClass().getSimpleName();

    // Compute the location of the documents file.
    if (null == docsFile) {
      // No documents file provided; assume that the test case has a dedicated input file in DEL
      // format
      docsFile = new File(TestConstants.TEST_DOCS_DIR,
          String.format("%s/%s.del", className, getCurPrefix()));
    }

    if (false == docsFile.exists()) {
      throw new Exception(String.format("Documents file/dir %s not found", docsFile));
    }

    // compile and load the module
    OperatorGraph og = compileAndLoadModules(moduleNames, eti);

    annotateAndPrint(docsFile, og);
  }

  private void printProgress(int ndoc, int nchar, long startMs) {
    long elapsedMs = System.currentTimeMillis() - startMs;
    double elapsedSec = (elapsedMs) / 1000.0;
    double docsPerSec = ndoc / elapsedSec;
    double kcharPerSec = nchar / elapsedSec / 1024.0;

    Log.info("Processed %d docs in %1.1f sec " + "(%1.1f docs/sec; %1.1f kchar/sec)", ndoc,
        elapsedSec, docsPerSec, kcharPerSec);
  }

  /**
   * Runs the given module with inputDoc
   * 
   * @param docsFile input document to be annotated
   * @param moduleName Name of the module to run
   * @param modulePathURI URI where module can be located
   * @throws Exception
   */
  protected void runModule(File docsFile, String moduleName, String modulePathURI)
      throws Exception {
    OperatorGraph og =
        OperatorGraph.createOG(new String[] {moduleName}, modulePathURI, null, tokenizerCfg);
    annotateAndPrint(docsFile, og);
  }

  /**
   * A generic test method to compile input AQL with a custom planner specified as input It then
   * runs the non modular AQL and compares the outputs
   * 
   * @param docsFile input document to be annotated
   * @param aqlFile File containing the AQL
   * @param planner Custom planner implementation: can be NAIVE, NAIVE_MERGE or RANDOM_MERGE
   * @throws Exception
   */
  protected void genericTest(File docsFile, File aqlFile, Planner planner) throws Exception {
    Compiler compiler = null;
    try {
      setPrintTups(true);

      // Compile with the low-level compiler API, so that we can set the planner.
      CompileAQLParams params = new CompileAQLParams();
      params.setInputFile(aqlFile);
      params.setOutputURI(curOutputDir.toURI().toString());
      params.setTokenizerConfig(getTokenizerConfig());

      compiler = new com.ibm.avatar.aql.compiler.Compiler();
      // Set the planner provided as input parameter to this test
      compiler.setPlanner(planner);
      compiler.compileToTAM(params);

      runNonModularAQLTest(docsFile, aqlFile);
      compareAgainstExpected(true);

    } finally {
      if (compiler != null) {
        compiler.deleteTempDirectory();
      }
    }
  }

  /**
   * Runs the given set of modules with the specified input document
   * 
   * @param docsFile input document to be annotated
   * @param moduleNames Name of the modules to run
   * @param modulePathURI URI where module can be located
   * @param eti optional external artifacts object to be loaded into operator graph
   * @throws Exception
   */
  protected void runModules(File docsFile, String[] moduleNames, String modulePathURI,
      ExternalTypeInfo eti) throws Exception {
    OperatorGraph og = OperatorGraph.createOG(moduleNames, modulePathURI, eti, tokenizerCfg);
    annotateAndPrint(docsFile, og);
  }

  /**
   * Common method used to execute a loaded operator graph on a text string. Used by a few test
   * cases to assist with profiling.
   * 
   * @param og the operator graph to execute
   * @param inputStr string to be annotated
   * @param outputTypes list of output views for which the extractor should generate outputs; null
   *        means “return all types”.
   * @param extViewTups Content of the external views, given as a map with the key being the
   *        external view name (qualified by the module name where that external view is created, as
   *        for external tables and dictionaries) and the value being a list of tuples that the
   *        external view must be populated with
   * @return map from output type name to a list of output tuples for the document
   * @throws Exception
   */
  public Map<String, TupleList> annotateString(OperatorGraph og, String inputStr,
      String[] outputTypes, Map<String, TupleList> extViewTups) throws Exception {
    // The basic doc schema is just one field named text with type Text
    Tuple doctup = TestConstants.BASIC_DOC_SCHEMA.createTup();
    TextSetter docTextSetter = (TestConstants.BASIC_DOC_SCHEMA).textSetter(Constants.DOCTEXT_COL);
    docTextSetter.setVal(doctup, inputStr, LangCode.en);

    return og.execute(doctup, outputTypes, extViewTups);
  }

  /**
   * Common method used to execute a loaded operator graph on a document file, and then print out
   * the annotations to HTML
   * 
   * @param docsFile the input document file
   * @param og the operator graph to run the document file on
   * @throws Exception
   */
  protected void annotateAndPrint(File docsFile, OperatorGraph og) throws Exception {

    // If we want to dump the output, get the list of views and their schemas from the operator
    // graph, otherwise use an
    // empty map
    Map<String, TupleSchema> outputViews =
        ((false == disableOutput) ? og.getOutputTypeNamesAndSchema()
            : new HashMap<String, TupleSchema>());

    int ndoc = 0;
    int nchar = 0;
    long startMs = System.currentTimeMillis();

    // handle JSON and CSV files separately
    if (docsFile.toString().endsWith(Constants.JSON_EXTENSION)) {
      annotateAndPrintJson(docsFile, og, outputViews);
    } else if (docsFile.toString().endsWith(Constants.CSV_EXTENSION)) {
      annotateAndPrintCSV(docsFile, og, outputViews);
    }
    // non-JSON files instantiate a DocReader
    else {
      DocReader docs = new DocReader(docsFile);
      TupleSchema docSchema = docs.getDocSchema();
      if (language != LangCode.DEFAULT_LANG_CODE) {
        docs.overrideLanguage(language);
      }

      // Initialize the utility object to write the output HTML files
      ToHTMLOutput toHtmlOut = new ToHTMLOutput(outputViews, getCurOutputDir(), this.getPrintTups(),
          this.getWriteCharsetInfo(), false, docSchema);

      FieldGetter<Text> docTextGetter = docs.getDocSchema().textAcc(Constants.DOCTEXT_COL);

      while (docs.hasNext()) {
        Tuple docTup = docs.next();

        // System.err.printf("Doc tuple is: %s\n", docTup);

        // Last argument --> produce all output types of this annotator.
        // Map<String, TupleList> annotations = syst.annotateDoc (docTup, null);
        Map<String, TupleList> annotations = og.execute(docTup, null, null);

        // Append the results to the output HTML files
        if (false == disableOutput)
          toHtmlOut.write(docTup, annotations);

        ndoc++;
        nchar += docTextGetter.getVal(docTup).getLength();

        // Generate some feedback for the user.
        if (0 == ndoc % progressIntervalDoc) {
          printProgress(ndoc, nchar, startMs);
        }

      }

      docs.remove();

      printProgress(ndoc, nchar, startMs);

      // Close the output files.
      toHtmlOut.close();
    }

  }

  /**
   * Subroutine of {@link #annotateAndPrint(File, OperatorGraph)} that handles CSV input files.
   */
  protected void annotateAndPrintCSV(File docsFile, OperatorGraph og,
      Map<String, TupleSchema> outputViews) throws IOException, TextAnalyticsException, Exception {
    // Use the operator graph to determine the document schema.
    TupleSchema docSchema = og.getDocumentSchema();

    // Initialize the utility object to write the output HTML files
    ToHTMLOutput toHtmlOut = new ToHTMLOutput(outputViews, getCurOutputDir(), this.getPrintTups(),
        this.getWriteCharsetInfo(), false, docSchema);

    DocReader docs = new DocReader(docsFile, docSchema, null);

    if (language != LangCode.DEFAULT_LANG_CODE) {
      docs.overrideLanguage(language);
    }

    while (docs.hasNext()) {
      Tuple docTup = docs.next();

      // Last argument --> produce all output types of this annotator.
      // Map<String, TupleList> annotations = syst.annotateDoc (docTup, null);
      Map<String, TupleList> annotations = og.execute(docTup, null, null);

      // Append the results to the output HTML files
      if (false == disableOutput)
        toHtmlOut.write(docTup, annotations);

    }
    docs.remove();

    // Close the output files.
    toHtmlOut.close();
  }

  /**
   * Subroutine of {@link #annotateAndPrint(File, OperatorGraph)} that handles JSON input files.
   */
  private void annotateAndPrintJson(File docsFile, OperatorGraph og,
      Map<String, TupleSchema> outputViews) throws IOException, TextAnalyticsException, Exception {
    // Use the operator graph to determine the document schema.
    TupleSchema docSchema = og.getDocumentSchema();

    // Initialize the utility object to write the output HTML files
    ToHTMLOutput toHtmlOut = new ToHTMLOutput(outputViews, getCurOutputDir(), this.getPrintTups(),
        this.getWriteCharsetInfo(), false, docSchema);

    // populate the external views map
    Map<Pair<String, String>, TupleSchema> extViewsMap =
        new HashMap<Pair<String, String>, TupleSchema>();
    for (String extViewName : og.getExternalViewNames()) {
      Pair<String, String> extViewNamePair =
          new Pair<String, String>(extViewName, og.getExternalViewExternalName(extViewName));
      extViewsMap.put(extViewNamePair, og.getExternalViewSchema(extViewName));
    }

    // iterate over doc tuples with associated external views
    Iterator<Pair<Tuple, Map<String, TupleList>>> itr = DocReader
        .makeDocandExternalPairsItr(docsFile.toString(), og.getDocumentSchema(), extViewsMap);

    while (itr.hasNext()) {
      Pair<Tuple, Map<String, TupleList>> docExtViewTup = itr.next();

      Tuple tup = docExtViewTup.first;
      Map<String, TupleList> extViewData = docExtViewTup.second;

      Map<String, TupleList> annotations = og.execute(tup, null, extViewData);

      // Append the results to the output HTML files
      if (false == disableOutput)
        toHtmlOut.write(tup, annotations);
    }

    // Close the document iterator
    itr.remove();

    // Close the output files.
    toHtmlOut.close();
  }

  /**
   * Verify that we get the same number and type of compiler warnings
   * 
   * @param summary the summary object from which we obtain warnings
   * @param expectedWarningTypes The list of warning types expected
   */
  public void verifyWarnings(CompilationSummary summary, WarningType[] expectedWarningTypes) {
    if (null != expectedWarningTypes) {

      List<CompilerWarning> warnings = summary.getCompilerWarning();
      Assert.assertEquals("Number of CompilerWarnings does not match", expectedWarningTypes.length,
          warnings.size());

      for (int i = 0; i < expectedWarningTypes.length; i++) {
        WarningType expected = expectedWarningTypes[i];

        CompilerWarning warning = warnings.get(i);

        String fileName = warning.getFileName();
        System.out.printf("%s: (%s, %s)\n%s\n ", fileName, warning.getBeginLine(),
            warning.getBeginColumn(), warning);
        WarningType actual = warning.getType();

        Assert.assertEquals("CompilerWarning type does not match", expected, actual);
        Assert.assertTrue("Filename should not be null", null != fileName);
      }
    }
  }

  /**
   * Adds the specified file/directory to the class path of a private URL class loader instance and
   * sets this class loader as the current thread's context class loader. This is to enable test
   * cases to alter the class path of their class loader only for the life and scope of the test
   * case.
   * 
   * @param location File descriptor of the file / directory to be added to class path
   * @return original context class loader of the current thread
   * @throws Exception if there are any exceptions in adding the specified location to the class
   *         path
   */
  protected ClassLoader addToClassPath(File location) throws Exception {
    ClassLoader origClassLoader = Thread.currentThread().getContextClassLoader();

    URL jarUrl = location.toURI().toURL();
    URLClassLoader newClassLoader = new URLClassLoader(new URL[] {jarUrl}, origClassLoader);
    Thread.currentThread().setContextClassLoader(newClassLoader);

    return origClassLoader;
  }

  /**
   * Resets the context class loader of current thread to the specified class loader
   * 
   * @param origClassLoader class loader to set
   */
  protected void resetClassLoader(ClassLoader origClassLoader) {
    // BEGIN CHANGE FOR defect
    // null is a perfectly good value for the thread context classloader. Removed null pointer check
    // here.
    // if (null != origClassLoader) {
    Thread.currentThread().setContextClassLoader(origClassLoader);
    // }
    // END change for defect
  }

  /**
   * Get the CompilationSummary object from the last compilation run by this instance of
   * RuntimeTestHarness.
   * 
   * @return the CompilationSummary object from the last compilation run
   */
  protected CompilationSummary getSummary() {
    return summary;
  }

  /**
   * Verifies whether the specified Throwable object <code>e</code> contains in its chain of
   * exceptions, a Throwable matching the name of <code>expectedClass</code>, with a message
   * matching <code>expectedMsg</code>. Throws an assertion failure, if an Exception with the given
   * class name and message is not found in the chain of exceptions.
   * 
   * @param e The Throwable whose chain of exceptions is to be checked.
   * @param expectedClass Name of the Throwable class that is expected in the chain of exceptions
   *        starting from <code>e</code>
   * @param expectedMsg The message to be looked for in the chain of exceptions
   */
  protected void assertException(Throwable e, String expectedClass, String expectedMsg) {
    Throwable cause = e;
    while (false == cause.getClass().getName().equals(expectedClass)) {
      cause = cause.getCause();
    }

    String errMsg =
        String.format("Expected a Throwable of type %s with message \"%s\", but none found.",
            expectedClass, expectedMsg);
    assertEquals(errMsg, expectedMsg, cause.getMessage());
  }

  /**
   * Abstraction around {@link #isStringExistInAOG(String, String)} for non-modular compiled
   * archives
   * 
   * @param stringVal see <code>stringVal</code> of {@link #isStringExistInAOG(String, String)}
   * @return boolean value as received from {@link #isStringExistInAOG(String, String)}
   * @throws Exception
   */
  protected boolean isStringExistInAOG(String stringVal) throws Exception {
    return isStringExistInAOG(stringVal, Constants.GENERIC_MODULE_NAME);
  }

  /**
   * Utility method to check whether a String value exists inside a given module's compiled plan For
   * instance, this method can be used to check whether the plan utilizes 'NLJoin'
   * 
   * @param stringVal String value whose containment within the compiled plan is being checked
   * @param moduleName Module whose compiled plan either contains or doesn't contain
   *        <code>stringVal</code>
   * @return boolean result of containment check
   * @throws Exception
   */
  protected boolean isStringExistInAOG(String stringVal, String moduleName) throws Exception {

    // .tam [module] archive file
    File moduleArchive =
        new File(getCurOutputDir().toString(), String.format("%s.tam", moduleName));

    JarInputStream moduleArchiveStream = new JarInputStream(new FileInputStream(moduleArchive));
    ByteArrayInputStream planContentStream = null;

    // Iterate through the module archive file's entries
    JarEntry moduleArchiveEntry = null;
    while ((moduleArchiveEntry = moduleArchiveStream.getNextJarEntry()) != null) {

      // If we encounter the compiled plan file, read it in and check for containment
      if (moduleArchiveEntry.getName().equals(PLAN_FILE_NAME)) {
        byte[] planContent = ModuleUtils.readCurrJarEntry(moduleArchiveStream);
        planContentStream = new ByteArrayInputStream(planContent);
        moduleArchiveStream.close();
        return TAMSerializer.readAOG(planContentStream).contains(stringVal);
      }
    }

    moduleArchiveStream.close();
    return false;
  }

  /**
   * Helper function to check for a particular join type for a given module
   * 
   * @param joinPred Join Predicate Type (ex. HashJoin)
   * @param moduleName Name of the compiled module
   * @throws Exception
   */
  protected void assertModuleContainsJoin(String joinPred, String moduleName) throws Exception {
    Assert.assertTrue(String.format("This module's plan doesn't use %s!", joinPred),
        isStringExistInAOG(joinPred, moduleName));
  }

  /**
   * Helper function to check if the throughput on profiling the AQL is greater than a minimum
   * value. Very useful for performance tests.
   * 
   * @param throughput Value provided by the profiler output
   * @param minVal Minimum expected throughput
   * @throws Exception
   */
  protected void assertThroughput(double throughput, double minVal) throws Exception {
    Assert.assertTrue(
        String.format("Throughput of %s is too low! Should be above %s.", throughput, minVal),
        throughput > minVal);
  }

  /**
   * Helper function to compile modular AQL which depends on a set of precompiled TAMs
   * 
   * @param inputModules is a list of one or more URIs of the source module(s) to compile, separated
   *        by ';'
   * @param outputModule path to directory or .jar/.zip file on the local file system where the
   *        compiled module representation (.tam file) of each input module is sent to.
   * @param precompiledModulePath is a search path (one or more entries, separated by ';' for
   *        finding dependent compiled modules.
   * @param tokenizerCfg is the tokenizer used for compiling dictionaries
   * @throws Exception
   */
  protected void compileModules(String[] inputModules, String outputModule,
      String precompiledModulePath, TokenizerConfig tokenizerCfg) throws Exception {
    CompileAQLParams params =
        new CompileAQLParams(inputModules, outputModule, precompiledModulePath, tokenizerCfg);
    try {
      CompileAQL.compile(params);
    } catch (TextAnalyticsException e) {
      e.printStackTrace();
      Assert.fail("Exception encountered when compiling modules.");
    }
  }

  /**
   * Helper function to profile an operator graph over a given document. Very useful for
   * performance/throughput tests.
   * 
   * @param og Operator Graph
   * @param doc Input document for profiling
   * @param langCode Language Code of the input document set
   * @param minRuntimeSec Minimum time for which the profiler needs to run
   * @param minThroughput Minimum expected throughput
   * @throws Exception
   */
  protected void profileOG(OperatorGraph og, File doc, LangCode langCode, int minRuntimeSec,
      double minThroughput) throws Exception {
    // Create an AQLProfiler for the input OperatorGraph instance
    AQLProfiler profiler = AQLProfiler.createProfilerFromOG(og);
    profiler.setMinRuntimeSec(minRuntimeSec);
    // Run the profiler, dumping info about top most expensive views
    profiler.profile(doc, langCode, null);
    // Evaluate the throughput info
    double throughput = (profiler.getCharPerSec() / 1024.0);
    Log.info("%d char in %1.2f sec --> %1.2f kchar/sec\n", profiler.getTotalChar(),
        profiler.getRuntimeSec(), throughput);
    assertThroughput(throughput, minThroughput);
  }

  /**
   * Describes the contents of a compiled module, with output sent to files. <br/>
   * Note: Adapted from the ExplainModule API in {@link com.ibm.avatar.api.ExplainModule} for JUnits
   * to pass 2 writers for having metadata and operator graph in separate files for convenient
   * comparison later
   * 
   * @param compiledModule the {@link File} object pointing to the compiled module file(.tam) to be
   *        explained
   * @param metadataFile the {@link File} object pointing to the file in the local filesystem where
   *        explanation of the module metadata should be written
   * @param aogFile the {@link File} object pointing to the file in the local filesystem where
   *        explanation of the module operator graph should be written
   * @throws TextAnalyticsException if a problem is encountered when loading the metadata or
   *         operator graph
   */
  protected void explainModule(File compiledModule, File metadataFile, File aogFile)
      throws TextAnalyticsException {
    try {
      String aog = null;
      String metadata = null;

      Writer metadataWriter = new FileWriter(metadataFile);
      Writer aogWriter = new FileWriter(aogFile);

      FileInputStream fis = new FileInputStream(compiledModule);
      JarInputStream jis = new JarInputStream(fis);
      JarEntry entry = null;
      try {
        while ((entry = jis.getNextJarEntry()) != null) {
          byte[] content = ModuleUtils.readCurrJarEntry(jis);
          if (entry.getName().equals(PLAN_FILE_NAME)) {
            aog = new String(content);
          } else if (entry.getName().equals(METADATA_FILE_NAME)) {
            metadata = new String(content);
          }
        }

        // Write metadata into a separate file so that it can be read into ModuleMetadata objects
        // and then the objects
        // are compared for equality for JUnit tests (for cross JVM support)
        metadataWriter.write(metadata);

        // Write the operator graph into a separate file for line by line comparison for JUnit tests
        aogWriter.write(aog);
      } finally {
        fis.close();
        jis.close();
        metadataWriter.close();
        aogWriter.close();
      }
    } catch (IOException ioe) {
      throw new TextAnalyticsException(ioe);
    }
  }
}
