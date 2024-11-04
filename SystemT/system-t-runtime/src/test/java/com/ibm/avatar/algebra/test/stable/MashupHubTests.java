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
package com.ibm.avatar.algebra.test.stable;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileWriter;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.util.document.ToHTMLOutput;
import com.ibm.avatar.algebra.util.file.SearchPath;
import com.ibm.avatar.algebra.util.lang.LangCode;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.algebra.util.tokenize.TokenizerConfig;
import com.ibm.avatar.api.AQLProfiler;
import com.ibm.avatar.api.CompileAQL;
import com.ibm.avatar.api.CompileAQLParams;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.DocReader;
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.aql.planner.Planner;
import com.ibm.avatar.aql.planner.Planner.ImplType;
import com.ibm.avatar.aql.tam.TAM;
import com.ibm.avatar.aql.tam.TAMSerializer;
import com.ibm.avatar.logging.Log;

/**
 * Tests of the annotators that we're shipping to MashupHub.
 * 
 */
public class MashupHubTests extends RuntimeTestHarness {

  /**
   * Directory where AQL files referenced in this class's regression tests are located.
   */
  public static final String AQL_FILES_DIR = TestConstants.AQL_DIR + "/mashupHubTests";
  //
  // /** Directory where regression test results for this class go. */
  // public static final String OUTPUT_DIR = TestUtils.DEFAULT_OUTPUT_DIR + "/mashupHubTests";
  //
  // /** Corresponding directory for holding expected test results. */
  // public static final String EXPECTED_DIR = TestUtils.DEFAULT_EXPECTED_DIR + "/mashupHubTests";

  /**
   * Directory where special document files for the tests in this class are located.
   */
  public static final String DOCS_DIR = TestConstants.TEST_DOCS_DIR + "/mashupHubTests";

  public static void main(String[] args) {
    try {

      MashupHubTests t = new MashupHubTests();

      t.setUp();

      long startMS = System.currentTimeMillis();

      t.companyInfoBug();

      long endMS = System.currentTimeMillis();

      t.tearDown();

      double elapsedSec = ((double) (endMS - startMS)) / 1000.0;

      System.err.printf("Test took %1.3f sec.\n", elapsedSec);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Scan over the Enron database; set up by setUp() and cleaned out by tearDown()
   */
  private DocReader docs = null;
  private File defaultDocsFile = null;

  // private TestUtils util = null;

  @Before
  public void setUp() throws Exception {

    // Renice the current thread to avoid locking up the entire system.
    Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

    // scan = new DirDocScan(new File(TestConstants.DUMPS_DIR
    // + "/streetinsider"));
    defaultDocsFile = new File(TestConstants.DUMPS_DIR + "/streetinsider");

    // util = new TestUtils ();

    // Make sure that we don't dump query plans unless a particular test
    // requests it.
    this.dumpAOG = false;

    // We want verbose logging.
    Log.enableAllMsgTypes();
  }

  @After
  public void tearDown() {
    // scan = null;

    // Deregister the Derby driver so that other tests can connect to other
    // databases. This is probably not necessary (we don't currently use
    // derby), but we do it just in case.
    try {
      DriverManager.getConnection("jdbc:derby:;shutdown=true");
    } catch (SQLException e) {
      // The shutdown command always raises a SQLException
      // See http://db.apache.org/derby/docs/10.2/devguide/
    } finally {
      if (null != docs)
        docs.remove();
    }
    System.gc();
  }

  /**
   * Top-level AQL file for running the entire MashupHub annotators set.
   */
  public static final String TOP_LEVEL_AQL_FILE_NAME = "mashuphub-systemT-annotators.aql";

  /**
   * Run the all the annotators directly out of the AnnotatorTester project.
   */
  @Test
  public void runAll() throws Exception {
    final String TEST_NAME = "runAll";

    genericInPlaceTest(TOP_LEVEL_AQL_FILE_NAME, TEST_NAME);

  }

  /**
   * Run the all the annotators directly out of the AnnotatorTester project, with output disabled,
   * using the full 25k Enron sample data set. Disabled as a regression test because it takes a long
   * time to run.
   */
  @Test
  public void timeAll() throws Exception {
    final String TEST_NAME = "runAll";

    // scan = DocScan.makeFileScan(new
    // File(TestConstants.ENRON_37939_DUMP));
    // scan = DocScan.makeFileScan(new File(TestConstants.ENRON_SAMPLE_ZIP));

    // util.setGenerateHTML (false);

    genericInPlaceTest(TOP_LEVEL_AQL_FILE_NAME, TEST_NAME);

  }

  /**
   * Run all the annotators directly out of the AnnotatorTester project, with output disabled, using
   * the StreetInserder M&A data set. Not enabled as a regression test because IBM Java doesn't like
   * the input documents file.
   */
  @Test
  public void timeAllMandA() throws Exception {
    final File ANNOTATOR_TESTER = new File(TestConstants.TEST_WORKING_DIR, "../AnnotatorTester");

    if (false == ANNOTATOR_TESTER.exists()) {
      Log.info("Skipping test because AnnotatorTester project" + " was not found.");
      return;
    }

    // final File DOCS_FILE = new File (ANNOTATOR_TESTER, "resources/data/src/StreetInsider/"
    // + "original-html-format/SIMergerAcquisition-all.zip");

    final String TEST_NAME = "mAndA";

    // scan = DocScan.makeFileScan(DOCS_FILE);

    // util.setGenerateHTML (false);

    genericInPlaceTest(TOP_LEVEL_AQL_FILE_NAME, TEST_NAME);

  }

  /**
   * Run all the annotators directly out of the AnnotatorTester project, with the profiler enabled,
   * using the full Enron38k data set.
   */
  @Test
  public void profileAll() throws Exception {
    startTest();

    final File DUMP_FILE = new File(TestConstants.ENRON_SAMPLE_ZIP);

    AQLProfiler profiler = genericInPlaceProfile(TOP_LEVEL_AQL_FILE_NAME, DUMP_FILE);

    if (null == profiler) {
      // No AnnotatorTester project
      return;
    }

    profiler.dumpTopOperators();

    Log.info("%d char in %1.2f sec --> %1.2f kb/sec\n", profiler.getTotalChar(),
        profiler.getRuntimeSec(), //
        profiler.getCharPerSec() / (double) 1024);

    endTest();
  }

  /**
   * Run a test over AQL files and dictionaries located inside the AnnotatorTester project.
   * 
   * @param aqlFileName name of the top-level AQL file to test, relative to the MashupHub
   *        subdirectory in the AnnotatorTester project
   * @param testName internal name given to the current test case; used to generate the names of
   *        output directories
   */
  private void genericInPlaceTest(final String aqlFileName, final String testName)
      throws Exception {

    startTest(testName);

    System.out.println("output dir " + getCurOutputDir());
    // Location of the "Annotators" project, which contains the latest
    // version of the MashupHub annotators.
    final File ANNOTATORS_PROJECT = new File(TestConstants.TEST_WORKING_DIR, "../Annotators");

    // Locations of various files associated with the MashupHub annotators
    final File MH_INCLUDE_DIR = new File(ANNOTATORS_PROJECT, "Annotators");

    final String[] MH_DICT_PATH_ELEMS =
        {new File(MH_INCLUDE_DIR, "core/Financial/dictionaries").getPath(),
            new File(MH_INCLUDE_DIR, "core/GenericNE/dictionaries").getPath()};
    final String MH_DICT_PATH =
        StringUtils.join(MH_DICT_PATH_ELEMS, new String(new char[] {SearchPath.PATH_SEP_CHAR}));

    if (false == ANNOTATORS_PROJECT.exists()) {
      Log.info("Skipping test because project dir %s" + " was not found.",
          ANNOTATORS_PROJECT.getPath());
      return;
    }

    File aqlFile = new File(MH_INCLUDE_DIR, "mashupHub/" + aqlFileName);

    setDataPath(MH_DICT_PATH, MH_INCLUDE_DIR.getPath(), null);

    runNonModularAQLTest(defaultDocsFile, aqlFile);
  }

  /**
   * Profile an AQL file out of the AnnotatorTester project.
   * 
   * @param aqlFileName name of AQL file, relative to the AQL directory inside AnnotatorTester.
   * @param dumpFile path to a documents dump file; absolute or relative to the JVM's working
   *        directory
   */
  private AQLProfiler genericInPlaceProfile(final String aqlFileName, final File dumpFile)
      throws Exception {
    final File ANNOTATOR_TESTER = new File(TestConstants.EXTRACTOR_LIB_DIR);

    if (false == ANNOTATOR_TESTER.exists()) {
      Assert.fail(String.format("AnnotatorTester project not found at %s.\n", ANNOTATOR_TESTER));
    }

    // Locations of various files associated with the MashupHub annotators
    final File MH_AQL_DIR = new File(ANNOTATOR_TESTER, "/aql/mashupHub/");

    String MH_INCLUDE_DIR1 = new File(ANNOTATOR_TESTER, "/aql").getCanonicalPath();
    String MH_INCLUDE_DIR2 =
        new File(ANNOTATOR_TESTER, "/aql/core-updated-refactored/GenericNE/language/en")
            .getCanonicalPath();

    String MH_DICT_DIR1 =
        new File(ANNOTATOR_TESTER, "/aql/core/GenericNE/dictionaries").getCanonicalPath();
    String MH_DICT_DIR2 =
        new File(ANNOTATOR_TESTER, "/aql/core/Financial/dictionaries").getCanonicalPath();

    String MH_UDF_DIR1 =
        new File(ANNOTATOR_TESTER, "/aql/core-updated-refactored/GenericNE/udfjars")
            .getCanonicalPath();

    String dataPath = String.format("%s;%s;%s;%s;%s", MH_INCLUDE_DIR1, MH_INCLUDE_DIR2,
        MH_DICT_DIR1, MH_DICT_DIR2, MH_UDF_DIR1);

    File aqlFile = new File(MH_AQL_DIR, aqlFileName);
    compileAQL(aqlFile, dataPath);

    String moduleURI = getCurOutputDir().toURI().toString();
    AQLProfiler profiler = AQLProfiler.createCompiledModulesProfiler(
        new String[] {Constants.GENERIC_MODULE_NAME}, moduleURI, null, null);
    profiler.profile(dumpFile, LangCode.en, null);

    return profiler;
  }

  /**
   * Test case for a compilation bug in the "company info" annotator.
   */
  @Test
  public void companyInfoBug() throws Exception {
    startTest();

    docs = new DocReader(new File(TestConstants.ENRON_1K_DUMP));
    setWriteCharsetInfo(true);
    setPrintTups(false);
    genericTestCase("companyInfoBug");
  }

  /**
   * Test case for a compilation bug involving include processing.
   */
  @Test
  public void includeBug() throws Exception {
    startTest();

    docs = new DocReader(new File(TestConstants.ENRON_1K_DUMP));
    setWriteCharsetInfo(true);
    genericTestCase("includeBug");
  }

  /**
   * Test case for a compilation bug involving include processing.
   */
  @Test
  public void includeBug2() throws Exception {

    startTest();

    // This test should throw an exception on Windows/IBM Java 5
    docs = new DocReader(new File(TestConstants.ENRON_1K_DUMP));
    try {
      genericTestCase("includeBug2");
    } catch (com.ibm.avatar.api.exceptions.CompilerException e) {
      String message = e.getAllCompileErrors().get(0).getMessage();

      // Message will be in the form:
      // AQL file '<system-dependent path>' is not in UTF-8 encoding
      final String SUFFIX = "is not in UTF-8 encoding";
      assertEquals(SUFFIX,
          message.subSequence(message.length() - SUFFIX.length(), message.length()));

      Log.info("Caught error as expected: %s", message);
    }

  }

  /**
   * Test case for a confusing Array index out of range: -1 exception thrown when a view is output
   * in two different aql files. The error message should inform of the duplicate output view
   * statements.
   */
  @Test
  public void duplicateOutputBug() throws Exception {

    startTest();

    try {
      genericTestCase("duplicateOutputBug");
    } catch (com.ibm.avatar.api.exceptions.CompilerException e) {
      String message = e.getAllCompileErrors().get(0).getMessage();
      Log.info("Caught exception as expected: %s", message);

      // Make sure we get the error message we expected.
      final String EXPECTED_MSG_TAIL = "line 13, column 1: Enabled output 'Title' twice";

      assertEquals(EXPECTED_MSG_TAIL,
          message.substring(message.length() - EXPECTED_MSG_TAIL.length()));
    }
  }

  /**
   * Test case for a confusing ArrayIndexOutOfBoundsException: Array size is 1, index is -1 thrown
   * when one of the inputs to the FollowsTok predicate is a string, instead of a span. The error
   * message should inform of the type mismatch.
   */
  @Test
  public void string2SpanBug() throws Exception {

    startTest();

    docs = new DocReader(new File(TestConstants.ENRON_1K_DUMP));
    this.setPrintTups(true);
    this.setWriteCharsetInfo(true);
    genericTestCase("string2SpanBug");
  }

  /**
   * Test case for incorrect regex match on detagged text containing "&"
   */
  @Test
  public void regexAmpBug() throws Exception {
    startTest();

    // We use a special input document for this test.
    docs = new DocReader(new File(DOCS_DIR, "regexAmpBug.del"));
    this.setWriteCharsetInfo(true);
    genericTestCase("regexAmpBug");
  }

  /**
   * Test case for incorrect regex match on detagged text containing "'"
   */
  @Test
  public void regexSingleQuoteBug() throws Exception {

    startTest();

    // We use a special input document for this test.
    docs = new DocReader(new File(DOCS_DIR, "regexSingleQuoteBug.del"));
    this.setWriteCharsetInfo(true);
    genericTestCase("regexSingleQuoteBug");
  }

  /**
   * Test case for optimizer bug on extract block statement
   */
  @Test
  public void blockCostBug() throws Exception {

    startTest();

    docs = new DocReader(new File(TestConstants.ENRON_1K_DUMP));
    genericTestCase("blockCostBug");
  }

  /**
   * Test case for a bug report about finding addresses in Craigslist postings.
   */
  @Test
  public void craigslistBug() throws Exception {

    // scan = DocScan.makeFileScan(new File(DOCS_DIR, "craigslistBug.del"));
    final String TEST_NAME = "craigslistBug";

    genericInPlaceTest(TOP_LEVEL_AQL_FILE_NAME, TEST_NAME);

    // TODO: The comparison is not done because the "Annotators" project does not exist.
    // Skip comparison.
    // compareAgainstExpected (true);
  }

  /**
   * Version of {@link #genericTestCase(String, ImplType)} that uses the default planner
   * implementation.
   */
  private void genericTestCase(String prefix) throws Exception {
    genericTestCase(prefix, Planner.DEFAULT_IMPL_TYPE);
  }

  /**
   * Set this flag to TRUE to make {@link #genericTestCase(String, ImplType)} print out query plans
   * to STDERR.
   */
  private boolean dumpAOG = false;

  /**
   * A generic AQL test case. Takes a prefix string as argument; runs the file prefix.aql and sends
   * output to testdata/regression/output/prefix. Also dumps the generated AOG plan to a file in the
   * output directory.
   * 
   * @param implType what implementation of the planner to use to compile the AQL
   */
  private void genericTestCase(String prefix, ImplType implType) throws Exception {

    String AQLFILE_NAME = String.format("%s/%s.aql", AQL_FILES_DIR, prefix);

    // Parse the AQL.

    /**
     * Directory where dictionary files for the tests in this file are located.
     */
    String DICTS_DIR = TestConstants.TESTDATA_DIR + "/dictionaries/mashupHubTests";

    System.err.printf("Compiling AQL file '%s'\n", AQLFILE_NAME);
    System.err.printf("Dictionary path '%s'\n", DICTS_DIR);
    System.err.printf("Output dir  '%s'\n", getCurOutputDir());
    String dataPathStr =
        String.format("%s%c%s", DICTS_DIR, SearchPath.PATH_SEP_CHAR, AQL_FILES_DIR);
    String outputURI = getCurOutputDir().toURI().toString();
    CompileAQLParams compileParam =
        new CompileAQLParams(new File(AQLFILE_NAME), outputURI, dataPathStr);
    compileParam.setTokenizerConfig(getTokenizerConfig());

    CompileAQL.compile(compileParam);

    // Load tam
    TAM tam = TAMSerializer.load(Constants.GENERIC_MODULE_NAME, compileParam.getOutputURI());

    String aog = tam.getAog();

    // Dump the plan to a file in the output directory.
    FileWriter aogOut = new FileWriter(new File(getCurOutputDir(), "plan.aog"));
    aogOut.append(aog);
    aogOut.close();

    if (dumpAOG)
      System.err.print("-----\nAOG plan is:\n" + aog);

    // Try running the plan.
    try {
      // util.runAOGStr (scan, aog, allCompiledDicts);
      OperatorGraph og = OperatorGraph.createOG(new String[] {Constants.GENERIC_MODULE_NAME},
          outputURI, null, new TokenizerConfig.Standard());

      // Get the list of views and their schemas from the operator graph
      Map<String, TupleSchema> outputViews = og.getOutputTypeNamesAndSchema();

      // Initialize the utility object to write the output HTML files
      ToHTMLOutput toHtmlOut = new ToHTMLOutput(outputViews, getCurOutputDir(), this.getPrintTups(),
          this.getWriteCharsetInfo(), false, docs.getDocSchema());

      while (docs.hasNext()) {
        Tuple docTup = docs.next();

        // Execute
        Map<String, TupleList> annots = og.execute(docTup, null, null);

        // Append the results to the output HTML files
        toHtmlOut.write(docTup, annots);
      }

      // Close the output files.
      toHtmlOut.close();
    } catch (Exception e) {
      System.err.printf("Caught exception while parsing AOG; " + "original AOG plan was:\n%s\n",
          aog);
      throw new RuntimeException(e);
    }

    truncateOutputFiles(true);
    File expectedDir = getCurExpectedDir();
    if (expectedDir.exists()) {
      compareAgainstExpected(true);
    }
  }

}
