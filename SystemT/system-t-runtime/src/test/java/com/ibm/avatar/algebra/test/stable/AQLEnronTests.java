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
import java.io.FileReader;
import java.io.FileWriter;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.datamodel.TextSetter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.scan.DocScanInternal;
import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.algebra.util.file.SearchPath;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.api.CompileAQL;
import com.ibm.avatar.api.CompileAQLParams;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.DocReader;
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.aql.compiler.Compiler;
import com.ibm.avatar.aql.planner.Planner;
import com.ibm.avatar.aql.planner.Planner.ImplType;
import com.ibm.avatar.aql.tam.TAM;
import com.ibm.avatar.aql.tam.TAMSerializer;
import com.ibm.avatar.logging.Log;

/**
 * Tests that extract information from the enron data set using AQL.
 */
public class AQLEnronTests extends RuntimeTestHarness {

  /**
   * Directory where AQL files referenced in this class's regression tests are located.
   */
  public static final String AQL_FILES_DIR = TestConstants.AQL_DIR + "/enronTests";

  public static void main(String[] args) {
    try {

      AQLEnronTests t = new AQLEnronTests();

      t.setUp();

      long startMS = System.currentTimeMillis();

      t.udfFloatTest();

      long endMS = System.currentTimeMillis();

      t.tearDown();

      double elapsedSec = (endMS - startMS) / 1000.0;

      System.err.printf("Test took %1.3f sec.\n", elapsedSec);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Scan over the Enron database; set up by setUp() and cleaned out by tearDown()
   */
  // private DocScan scan = null;
  private File defaultDocsFile = null;

  @Before
  public void setUp() throws Exception {

    // Renice the current thread to avoid locking up the entire system.
    Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

    // scan = new DerbyDocScan(EnronTests.dbname, EnronTests.quickQuery);

    // System.err.printf("Loading file '%s'\n",
    // TestConstants.ENRON_10K_DUMP);
    // scan = new DBDumpFileScan (TestConstants.ENRON_1K_DUMP);
    // scan = new DBDumpFileScan(TestConstants.ENRON_10K_DUMP);
    // scan = new DBDumpFileScan(TestConstants.TEST_DOCS_DIR + "/tmp.del");

    // Set default input file
    defaultDocsFile = new File(TestConstants.ENRON_1K_DUMP);

    // Set default dictionary path
    setDataPath(TestConstants.TESTDATA_DIR);;

    // For now, don't put any character set information into the header of
    // our output HTML.
    setWriteCharsetInfo(false);

    // Make sure our log messages get printed!
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
    }
    System.gc();

    Log.info("Done.");
  }

  /**
   * Parse and compile the "person at phone" annotator.
   */
  @Test
  public void personPhoneParseTest() throws Exception {
    startTest();

    String filename = AQL_FILES_DIR + "/personphone.aql";

    // Parse the AQL file
    compileAQL(new File(filename), TestConstants.TESTDATA_DIR);

    String moduleURI = getCurOutputDir().toURI().toString();
    TAM tam = TAMSerializer.load("genericModule", moduleURI);
    String aog = tam.getAog();

    System.err.print("AQL parse tree is:\n" + aog);
    // compiler.getCatalog ().dump (System.err);

    // System.err.print("-----\nAOG plan is:\n" + aog);

    OperatorGraph og = instantiateOperatorGraph(Constants.GENERIC_MODULE_NAME);

    // Try running the operator graph.
    System.err.print("Running plan...\n");

    int ndoc = 0;
    int nannot = 0;

    DocReader reader = new DocReader(defaultDocsFile);

    while (reader.hasNext()) {

      Tuple docTup = reader.next();

      Map<String, TupleList> results = og.execute(docTup, null, null);

      for (String outputName : results.keySet()) {
        nannot += results.get(outputName).size();
      }

      ndoc++;

      if (0 == ndoc % 1000) {
        System.err.printf("Produced %d annotations on %d documents.\n", nannot, ndoc);
      }
    }

    // Close the document reader
    reader.remove();

    System.err.printf("Produced a total of %d annotations over %d documents.\n", nannot, ndoc);
  }

  /**
   * A more thorough test using the "person-phone" annotator as inspiration.
   */
  @Test
  public void personPhoneComplexTest() throws Exception {
    Compiler compiler = new Compiler();
    try {
      startTest();

      String filename = AQL_FILES_DIR + "/pptests.aql";

      // Parse the AQL file
      CompileAQLParams compileParam = new CompileAQLParams(new File(filename),
          getCurOutputDir().toURI().toString(), TestConstants.TESTDATA_DIR);
      compileParam.setTokenizerConfig(getTokenizerConfig());

      // Generate query plan in AOG format; force the naive planner to get
      // consistent results.
      compiler.setPlanner(new Planner(Planner.ImplType.NAIVE));
      compiler.compile(compileParam);

      // Load tam
      TAM tam = TAMSerializer.load(Constants.GENERIC_MODULE_NAME, compileParam.getOutputURI());

      String aog = tam.getAog();

      // Dump the AOG string to a file and make sure we generated the expected
      // AOG string.
      File aogfile = new File(getCurOutputDir(), "ppnest.aog");
      FileWriter out = new FileWriter(aogfile);
      out.append(aog);
      out.close();
      // compareAgainstExpected(aogfile.getName());

      // System.err.print("-----\nAOG plan is:\n" + aog);

      // // Parse and run the AOG spec.
      setDisableOutput(false);

      String[][] dictInfo =
          {{"dictionaries/first.dict", TestConstants.TESTDATA_DIR + "/dictionaries/first.dict"}};

      runAOGString(defaultDocsFile, aog, dictInfo);

      compareAgainstExpected(true);

      endTest();
    } finally {
      if (null != compiler) {
        compiler.deleteTempDirectory();
      }
    }
  }

  /**
   * Run the "person-phone" annotator, using the NaiveMergePlanner to compile the AQL annotation
   * spec.
   */
  @Test
  public void personPhoneMergeTest() throws Exception {
    Compiler compiler = null;
    try {
      startTest();

      String filename = AQL_FILES_DIR + "/pptests.aql";

      // Parse the AQL file
      CompileAQLParams params = new CompileAQLParams(new File(filename),
          getCurOutputDir().toURI().toString(), TestConstants.TESTDATA_DIR);
      params.setPerformSDM(false);
      params.setTokenizerConfig(getTokenizerConfig());

      // Generate query plan in AOG format; force the naive planner to get
      // consistent results.
      compiler = new Compiler();
      Planner p = new Planner(Planner.ImplType.NAIVE_MERGE);
      // No need to set the planner SDM, as long as we specify the right value
      // in CompileAQLParams
      // p.setPerformSDM(false);
      compiler.setPlanner(p);
      compiler.compile(params);

      // Load tam
      TAM tam = TAMSerializer.load(Constants.GENERIC_MODULE_NAME, params.getOutputURI());

      String aog = tam.getAog();

      // Dump the AOG string to a file and make sure we generated the expected
      // AOG string.
      File aogfile = new File(getCurOutputDir(), "ppmerge.aog");
      FileWriter out = new FileWriter(aogfile);
      out.append(aog);
      out.close();
      // compareAgainstExpected(aogfile.getName());

      // System.err.print("-----\nAOG plan is:\n" + aog);

      String[][] dictInfo =
          {{"dictionaries/first.dict", TestConstants.TESTDATA_DIR + "/dictionaries/first.dict"}};

      runAOGFile(defaultDocsFile, aogfile, dictInfo);

      // util.setSkipFileComparison(true);
      compareAgainstExpected(true);

      endTest();
    } finally {
      if (null != compiler) {
        compiler.deleteTempDirectory();
      }
    }
  }

  /**
   * The URL annotator, expressed in AQL. This test mostly verifies that the AQL compiler correctly
   * generates no Output expression if there is no output generated by the AQL.
   * 
   * @throws Exception
   */
  @Test
  public void urlTest() throws Exception {

    startTest();
    genericNonModularTestCase("urlTest");

    compareAgainstExpected(true);
  }

  /**
   * An email address annotator.
   * 
   * @throws Exception
   */
  @Test
  public void emailTest() throws Exception {
    startTest();

    // String filename = "testdata/aql/urltest.aql";
    String filename = TestConstants.AQL_DIR + "/enronTests/email.aql";

    // Parse and run the AQL file
    runNonModularAQLTest(defaultDocsFile, new File(filename));

    compareAgainstExpected(true);

    endTest();
  }

  /**
   * The built-in Time annotator, expressed in AQL.
   * 
   * @throws Exception
   */
  @Test
  public void timeTest() throws Exception {
    startTest();

    genericNonModularTestCase("timeTest");

    truncateOutputFiles(true);
    compareAgainstExpected(true);
  }

  /**
   * A test of set difference support in AQL.
   * 
   * @throws Exception
   */
  @Test
  public void setDifferenceTest() throws Exception {
    startTest();

    String filename = AQL_FILES_DIR + "/minus.aql";

    runNonModularAQLTest(defaultDocsFile, filename);

    // util.setSkipFileComparison(true);
    compareAgainstExpected(true);
  }

  /**
   * The built-in Direction annotator, expressed in AQL.
   * 
   * @throws Exception
   */
  @Test
  public void directionTest() throws Exception {
    startTest();
    genericNonModularTestCase("directionTest");

    compareAgainstExpected(true);
  }

  /**
   * The built-in Conference Call annotator, expressed in AQL. Disabled as a JUnit test case because
   * it takes too long. NOTE FROM LAURA 10/1/2012: Disabled since revision 25 of old CS svn. END
   * NOTE FROM LAURA 10/1/2012
   * 
   * @throws Exception
   */
  // @Test
  public void conferenceCall() throws Exception {

    startTest();

    // The 1000-document input set doesn't produce any answers, so use the
    // big one.

    genericNonModularTestCase("conferenceCall", new File(TestConstants.ENRON_10K_DUMP));

    compareAgainstExpected(true);
  }

  /**
   * The built-in forwardBlock annotator, expressed in AQL.
   * 
   * @throws Exception
   */
  @Test
  public void forwardBlockTest() throws Exception {
    startTest();

    genericNonModularTestCase("forwardBlock");

    // compareAgainstExpected("URL.htm");
  }

  /**
   * The built-in PersonName annotator, expressed in AQL.
   * 
   * @throws Exception
   */
  @Test
  public void personStrictTest() throws Exception {

    startTest();

    String filename = AQL_FILES_DIR + "/personstrict.aql";
    // String filename = "testdata/aql/personstrict.aql";
    // String filename = "testdata/aql/person-sekar.aql";

    setDataPath(TestConstants.TESTDATA_DIR);
    runNonModularAQLTest(defaultDocsFile, filename);

    // util.setSkipFileComparison(true);
    truncateOutputFiles(true);
    compareAgainstExpected(true);
  }

  /**
   * The built-in Organization annotator, expressed in AQL.
   * 
   * @throws Exception
   */
  @Test
  public void organizationTest() throws Exception {
    startTest();

    // String filename = "testdat/aql-Yunyao/organization.aql";
    // String filename = "testdata/aql/organization.aql";
    // String filename = "testdata/aql/organization-sekar.aql";
    // String filename = "testdata/aql-Yunyao/namedentity-yunyao.aql";
    // String filename = "testdata/aql/lotus/namedentity-sekar.aql";
    // String filename = "testdata/aql/lotus/namedentity-merged.aql";
    String filename = AQL_FILES_DIR + "/namedentity.aql";

    setDataPath(TestConstants.TESTDATA_DIR);
    runNonModularAQLTest(defaultDocsFile, filename);

    // util.setSkipFileComparison(false);
    // compareAgainstExpected("Organization.Fred.htm");
  }

  /**
   * Tests of the scalar functions.
   * 
   * @throws Exception
   */
  @Test
  public void scalarFuncTest() throws Exception {
    startTest();

    String filename = AQL_FILES_DIR + "/scalartests.aql";

    setDataPath(TestConstants.TESTDATA_DIR);

    runNonModularAQLTest(defaultDocsFile, filename);

    compareAgainstExpected("SpanBetween.htm", true);
    compareAgainstExpected("CapsPerson.htm", true);
  }

  /**
   * Test agnostic ordering of scalar functions. This runs the same AQL as scalarFuncTest but with
   * the ignore order flag set and the spans in reverse (invalid) order.
   * 
   * @throws Exception
   */
  @Test
  public void agnosticScalarFunctionTest() throws Exception {
    startTest();

    String filename = AQL_FILES_DIR + "/agnosticscalartests.aql";

    setDataPath(TestConstants.TESTDATA_DIR);

    runNonModularAQLTest(defaultDocsFile, filename);

    compareAgainstExpected("SpanBetween.htm", true);
    compareAgainstExpected("SpanBetweenFilt.htm", true);
  }

  /**
   * Full person annotator, in AQL. NOTE FROM LAURA 10/1/2012: Disabled since revision 9 of old CS
   * svn. END NOTE FROM LAURA 10/1/2012
   * 
   * @throws Exception
   */
  // @Test
  public void fullPersonTest() throws Exception {
    startTest();
    String filename = TestConstants.AQL_DIR + "/personstrict.aql";

    runNonModularAQLTest(defaultDocsFile, filename);

    compareAgainstExpected(true);
  }

  /**
   * Tests of block operators. NOTE FROM LAURA 10/1/2012: Disabled since revision 9 of old CS svn.
   * END NOTE FROM LAURA 10/1/2012
   * 
   * @throws Exception
   */
  // @Test
  public void blockTests() throws Exception {
    startTest();
    String filename = TestConstants.AQL_DIR + "/blocktests.aql";

    setDataPath(TestConstants.TESTDATA_DIR);
    runNonModularAQLTest(defaultDocsFile, filename);

    compareAgainstExpected(true);
  }

  /**
   * Test the watchdog timer API. This test was originally commented out, lacking input file. NOTE
   * FROM LAURA 10/1/2012: Disabled since revision 9 of old CS svn. END NOTE FROM LAURA 10/1/2012
   * 
   * @throws Exception
   */
  // @Test
  public void watchdogTest() throws Exception {
    startTest();

    // Generate AQL expression that takes a very long time to execute.
    // We do this by replicating an expensive regular expression multiple
    // times.
    String PROPER_NOUN_REGEX =
        "/(\\b[A-Z]\\w{3,}(?:'s)?\\b(?:-[A-z]\\w*(?:'s)?|\\s{1,10}(?:[a-z]{1,3}\\s{1,10}){0,2}[A-Z]\\w*(?:'s)?){0,6})(?<!\\s|This|That|They|Then|Their|Theirs)\\b/";
    StringBuilder sb = new StringBuilder();
    int NREGEX = 10;
    for (int i = 0; i < NREGEX; i++) {
      sb.append(String.format(
          "select R.match as match into output%d" + " from Regex(%s, DocScan.text) R;\n", i,
          PROPER_NOUN_REGEX));
    }
    String aql = sb.toString();

    // A single large input file for running the expression
    String INFILE_NAME = TestConstants.DUMPS_DIR + "/jack.txt";

    // Read the input file into a string.
    FileReader in = new FileReader(INFILE_NAME);
    char[] buf = new char[5000000];
    in.read(buf);
    in.close();
    final String doctext = new String(buf);

    // Compile the AQL and create OperatorGraph
    String modulePath = getCurOutputDir().toURI().toString();
    CompileAQLParams params = new CompileAQLParams();
    params.setInputStr(aql);
    params.setOutputURI(modulePath);
    CompileAQL.compile(params);
    OperatorGraph og = instantiateOperatorGraph(Constants.GENERIC_MODULE_NAME);

    TupleSchema docSchema = DocScanInternal.createOneColumnSchema();
    TextSetter setDocText = docSchema.textSetter(Constants.DOCTEXT_COL);

    // Create a background thread that will send us a signal.
    final Thread fgThread = Thread.currentThread();
    Runnable bgTask = new Runnable() {

      @Override
      public void run() {
        System.err.print("Background thread sleeping for 2 sec.\n");
        try {
          Thread.sleep(2000);
        } catch (InterruptedException e) {
          System.err.print("Background thread interrupted!\n");
          e.printStackTrace();
        }
        System.err.print("Interrupting foreground thread...\n");
        fgThread.interrupt();
      }

    };

    Thread bgThread = new Thread(bgTask);
    bgThread.start();

    System.err.print("Foreground thread pushing document.\n");

    Map<String, TupleList> annotations = new HashMap<String, TupleList>();
    // Now push the document through; we'll get interrupted midway through.
    try {
      Tuple docTup = docSchema.createTup();
      setDocText.setVal(docTup, doctext);
      annotations = og.execute(docTup, null, null);
    } catch (Exception e) {
      System.err.print("Foreground thread caught exception:\n");
      e.printStackTrace();
    }

    // Clean up the background thread.
    bgThread.join();

    // Count the number of matches on each output.
    for (int i = 0; i < NREGEX; i++) {
      String outputName = String.format("output%d", i);
      TupleList tups = annotations.get(outputName);

      int ntups = (null == tups) ? 0 : tups.size();
      System.err.printf("%d annotations for output '%s'\n", ntups, outputName);

      // The foreground thread should have stopped somewhere the first and
      // last regex.
      if (0 == i) {
        assertEquals(5135, ntups);
      } else if (NREGEX - 1 == i) {
        assertEquals(0, ntups);
      }
    }

    // Now reset the operator graph and make sure it still works.
    System.err.print("Resetting graph and running document again.\n");
    Tuple docTup = docSchema.createTup();
    setDocText.setVal(docTup, doctext);
    annotations = og.execute(docTup, null, null);

    // Count the number of matches on each output.
    for (int i = 0; i < NREGEX; i++) {
      String outputName = String.format("output%d", i);
      TupleList tups = annotations.get(outputName);

      int ntups = (null == tups) ? 0 : tups.size();
      System.err.printf("%d annotations for output '%s'\n", ntups, outputName);

      // This time around, every output should have been produced.
      assertEquals(5135, ntups);
    }
  }

  /**
   * Test the watchdog timer API with the named entity annotators. This test was originally
   * commented out, lacking input file. NOTE FROM LAURA 10/1/2012: Disabled since revision 9 of old
   * CS svn. END NOTE FROM LAURA 10/1/2012
   * 
   * @throws Exception
   */
  // @Test
  public void neWatchdogTest() throws Exception {
    startTest();

    String AQLFILE_NAME = TestConstants.AQL_DIR + "/lotus/namedentity.aql";

    // A single large input file for running the expression
    String INFILE_NAME = TestConstants.DUMPS_DIR + "/bigemail.txt";

    // Read the input file into a string.
    FileReader in = new FileReader(INFILE_NAME);
    char[] buf = new char[5000000];
    in.read(buf);
    in.close();
    final String doctext = new String(buf);

    // Compile the AQL
    compileAQL(new File(AQLFILE_NAME), TestConstants.TESTDATA_DIR);
    OperatorGraph og = instantiateOperatorGraph(Constants.GENERIC_MODULE_NAME);

    TupleSchema docSchema = DocScanInternal.createOneColumnSchema();
    TextSetter setDocText = docSchema.textSetter(Constants.DOCTEXT_COL);
    ArrayList<String> outputNames = og.getOutputTypeNames();

    // Create a background thread that will send us a signal.
    final Thread fgThread = Thread.currentThread();
    Runnable bgTask = new Runnable() {

      @Override
      public void run() {
        System.err.print("Background thread sleeping for 2 sec.\n");
        try {
          Thread.sleep(2000);
        } catch (InterruptedException e) {
          System.err.print("Background thread interrupted!\n");
          e.printStackTrace();
        }
        System.err.print("Interrupting foreground thread...\n");
        fgThread.interrupt();
      }

    };

    Thread bgThread = new Thread(bgTask);
    bgThread.start();

    System.err.print("Foreground thread pushing document.\n");

    // Now push the document through; we'll get interrupted midway through.
    Map<String, TupleList> annotations = new HashMap<String, TupleList>();
    try {
      Tuple docTup = docSchema.createTup();
      setDocText.setVal(docTup, doctext);
      annotations = og.execute(docTup, null, null);
    } catch (Exception e) {
      System.err.print("Foreground thread caught exception:\n");
      e.printStackTrace();
    }

    // Clean up the background thread.
    bgThread.join();

    // Print out how many of each output we got.
    for (String outputName : outputNames) {
      TupleList tups = annotations.get(outputName);

      int ntups = (null == tups) ? 0 : tups.size();
      System.err.printf("%d annotations for output '%s'\n", ntups, outputName);
    }

    // Now annotate the same document again.
    System.err.print("Running document again.\n");
    Tuple docTup = docSchema.createTup();
    setDocText.setVal(docTup, doctext);
    annotations = og.execute(docTup, null, null);

    // We should have now produced all outputs.
    for (String outputName : outputNames) {
      TupleList tups = annotations.get(outputName);

      int ntups = (null == tups) ? 0 : tups.size();
      System.err.printf("%d annotations for output '%s'\n", ntups, outputName);
    }
  }

  /**
   * Run the "scratchpad" file (temporary file for trying out new expressions).
   * 
   * @throws Exception
   */
  @Test
  public void scratchpadTest() throws Exception {
    startTest();

    genericNonModularTestCase("scratchpad");
  }

  /**
   * Test Sekar's version of the organization annotator.
   * 
   * @throws Exception
   */
  @Test
  public void orgSekarTest() throws Exception {
    startTest();
    String filename = TestConstants.AQL_DIR + "/organization-sekar.aql";

    // Try running the plan.
    setDataPath(TestConstants.TESTDATA_DIR);
    runNonModularAQLTest(defaultDocsFile, filename);
  }

  /**
   * Test the name entity annotators for lotus.
   * 
   * @throws Exception
   */
  @Test
  public void namedEntityTest() throws Exception {
    startTest();

    String filename = AQL_FILES_DIR + "/namedentity.aql";
    // String filename = TestConstants.AQL_DIR +
    // "/lotus/namedentity-spock-urlemailnooutput.aql";

    // Try running the plan.
    setDisableOutput(false);

    setDataPath(TestConstants.TESTDATA_DIR);
    runNonModularAQLTest(defaultDocsFile, filename);
  }

  /**
   * Run the "person-org only" named entity annotator.
   * 
   * @throws Exception
   */
  @Test
  public void personOrgTest() throws Exception {
    startTest();

    setDataPath(TestConstants.TESTDATA_DIR);

    genericNonModularTestCase("personOrg");
  }

  /**
   * Run the version of the "person-org only" named entity annotator with rewritten regexes.
   * 
   * @throws Exception
   */
  @Test
  public void fastPersonOrgTest() throws Exception {
    startTest();

    // Use the dictionary directory out of resources.
    setDataPath(TestConstants.TESTDATA_DIR);

    // scan = new DBDumpFileScan(TestConstants.ENRON_37939_DUMP);

    genericNonModularTestCase("fastPersonOrg");
  }

  /**
   * "Benchmark" version of namedEntityTest() -- skips generating pretty-printed HTML output.
   * 
   * @throws Exception
   */
  @Test
  public void namedEntityBench() throws Exception {
    startTest();
    // String filename = TestConstants.AQL_DIR +
    // "/lotus/namedentity-header.aql";
    String filename = AQL_FILES_DIR + "/namedentity.aql";

    // Try running the plan.
    setDisableOutput(true);
    setDataPath(TestConstants.TESTDATA_DIR);

    runNonModularAQLTest(defaultDocsFile, filename);
  }

  /**
   * Test of the LeftContext() and RightContext() scalar functions
   * 
   * @throws Exception
   */
  @Test
  public void contextTest() throws Exception {
    startTest();

    setPrintTups(true);

    genericNonModularTestCase("contextTest");

    truncateOutputFiles(true);
    compareAgainstExpected(true);
  }

  /**
   * Test of regex-style consolidation.
   * 
   * @throws Exception
   */
  @Test
  public void regexConsTest() throws Exception {
    startTest();

    // scan = new DBDumpFileScan(TestConstants.TEST_DOCS_DIR + "/tmp.del");

    genericNonModularTestCase("regexConsTest");

    setPrintTups(true);

    truncateOutputFiles(true);
    truncateExpectedFiles();
    compareAgainstExpected(true);
  }

  /**
   * Test of the ORDER BY clause in AQL.
   * 
   * @throws Exception
   */
  @Test
  public void orderByTest() throws Exception {
    startTest();

    // scan = new DBDumpFileScan(TestConstants.TEST_DOCS_DIR + "/tmp.del");

    setPrintTups(true);

    genericNonModularTestCase("orderByTest");

    truncateOutputFiles(true);
    compareAgainstExpected(true);

  }

  /**
   * Test of the GROUP BY clause in AQL.
   * 
   * @throws Exception
   */
  @Test
  public void groupByTest() throws Exception {
    startTest();

    setPrintTups(true);

    genericNonModularTestCase("groupByTest");

    truncateOutputFiles(true);
    compareAgainstExpected(true);

    // Don't screw up other tests' outputs.
    setPrintTups(false);
  }

  /**
   * Test of scalar list support for MINUS clause in AQL.
   * 
   * @throws Exception
   */
  @Test
  public void scalarListTest() throws Exception {
    startTest();

    setPrintTups(true);

    genericNonModularTestCase("scalarListTest");

    truncateOutputFiles(true);
    compareAgainstExpected(true);

    // Don't screw up other tests' outputs.
    setPrintTups(false);
  }

  /**
   * Test of cast expressions support in AQL.
   * 
   * @throws Exception
   */
  @Test
  public void castTest() throws Exception {

    startTest();

    setPrintTups(true);

    genericNonModularTestCase("castTest");

    truncateOutputFiles(true);
    compareAgainstExpected(true);

    // Don't screw up other tests' outputs.
    setPrintTups(false);
  }

  /**
   * Test of the GreaterThan() predicate
   * 
   * @throws Exception
   */
  @Test
  public void greaterThanTest() throws Exception {

    startTest();

    // scan = new DBDumpFileScan(TestConstants.TEST_DOCS_DIR + "/tmp.del");

    genericNonModularTestCase("greaterThanTest");

    truncateOutputFiles(true);
    compareAgainstExpected(true);
  }

  /**
   * Test of the GreaterThan() predicate for other types but Integer
   * 
   * @throws Exception
   */
  @Test
  public void greaterThanOtherTypesTest() throws Exception {

    startTest();
    setPrintTups(true);

    // scan = new DBDumpFileScan(TestConstants.TEST_DOCS_DIR + "/tmp.del");

    genericNonModularTestCase("greaterThanOtherTypesTest");

    truncateOutputFiles(true);
    compareAgainstExpected(true);
  }

  /**
   * Test of the GreaterThan() bug with Integer fields
   * 
   * @throws Exception
   */
  @Test
  public void greaterThanIntegerTest() throws Exception {
    startTest();

    setPrintTups(true);

    genericNonModularTestCase("greaterThanIntegerTest");

    truncateOutputFiles(true);
    compareAgainstExpected(true);

    // Don't screw up other tests' outputs.
    setPrintTups(false);
  }

  /**
   * Test for sequence patterns
   * 
   * @throws Exception
   */
  @Test
  public void sequencePatternTest() throws Exception {

    startTest();

    setPrintTups(true);

    genericNonModularTestCase("sequencePatternTest");

    truncateOutputFiles(true);
    compareAgainstExpected(true);

    // Don't screw up other tests' outputs.
    setPrintTups(false);
  }

  /**
   * Test for the case expression
   * 
   * @throws Exception
   */
  @Test
  public void caseTest() throws Exception {
    startTest();

    setPrintTups(true);

    genericNonModularTestCase("caseTest");

    truncateOutputFiles(true);
    compareAgainstExpected(true);

    // Don't screw up other tests' outputs.
    setPrintTups(false);
  }

  /**
   * Test of the RegexTok() table function. Ported from the AOG version,
   * {@link AOGParserTests#regexTokTest()}.
   */
  @Test
  public void regexTokTest() throws Exception {
    startTest();

    // This test uses a custom input file, based off of the first 200 docs
    // of ENRON_1K_DUMP, but with some additional test documents.
    // scan = new DBDumpFileScan (TestConstants.TEST_DOCS_DIR +
    // "/aqlEnronTests/regexTokTest.del");

    // dumpAOG = true;

    genericNonModularTestCase("regexTokTest",
        new File(TestConstants.TEST_DOCS_DIR + "/aqlEnronTests/regexTokTest.del"));

    System.err.printf("Comparing output files.\n");
    compareAgainstExpected(true);
  }

  /**
   * Test for a defect in java.util.regex reported as defect : SDA Accelerator Local Analysis App
   * throws Jaql Exception
   */
  @Test
  public void regexDotStarTest() throws Exception {
    startTest();

    genericNonModularTestCase("regexDotStarTest",
        new File(TestConstants.TEST_DOCS_DIR + "/aqlEnronTests/regexDotStarTest.del"));

    System.err.printf("Comparing output files.\n");
    compareAgainstExpected(true);
  }

  /**
   * Several tests of RSE join, packed into a single AQL file.
   */
  @Test
  public void rseTests() throws Exception {
    startTest();

    // Use the big input file so that we can see any speed regression.
    // scan = new DBDumpFileScan(TestConstants.ENRON_10K_DUMP);

    // setPrintTups(true);

    genericNonModularTestCase("rseTests", Planner.ImplType.RSE);

    System.err.printf("Comparing output files.\n");
    // util.setSkipFileComparison(true);

    compareAgainstExpected(true);
  }

  /** A test of AQL's "include" statement. */
  @Test
  public void includeTest() throws Exception {
    startTest();

    setDataPath(TestConstants.TESTDATA_DIR + SearchPath.PATH_SEP_CHAR + AQL_FILES_DIR);

    genericNonModularTestCase("includeTest");

    truncateOutputFiles(true);
    compareAgainstExpected(true);
  }

  /** A test of AQL's support for double-quoted table and column names. */
  @Test
  public void quotesTest() throws Exception {
    startTest();

    // Use the big input file so that we can see any speed regression.
    // scan = new DBDumpFileScan(TestConstants.ENRON_10K_DUMP);

    genericNonModularTestCase("quotesTest");

    System.err.printf("Comparing output files.\n");
    //
    // compareAgainstExpected("PersonPhone.htm");
    // compareAgainstExpected("PersonPhoneDirect.htm");
    // compareAgainstExpected("PersonPhoneReversed.htm");
    // compareAgainstExpected("PhonePerson.htm");
    //
    // compareAgainstExpected("PersonPhoneTok.htm");
    // compareAgainstExpected("PersonPhoneTokDirect.htm");
    // compareAgainstExpected("PhonePersonTok.htm");
  }

  /** A sentence boundary detector, written in AQL. */
  @Test
  public void sentenceBoundaryTest() throws Exception {
    startTest();

    // Use the big input file so that we can see any speed regression.
    // scan = new DBDumpFileScan(TestConstants.ENRON_10K_DUMP);

    genericNonModularTestCase("sentbound");

    System.err.printf("Comparing output files.\n");
    //
    // compareAgainstExpected("PersonPhone.htm");
    // compareAgainstExpected("PersonPhoneDirect.htm");
    // compareAgainstExpected("PersonPhoneReversed.htm");
    // compareAgainstExpected("PhonePerson.htm");
    //
    // compareAgainstExpected("PersonPhoneTok.htm");
    // compareAgainstExpected("PersonPhoneTokDirect.htm");
    // compareAgainstExpected("PhonePersonTok.htm");
  }

  /*
   * Constants for running the production eDiscovery annotator.
   */
  static final File ANNOTATORS_ROOT = new File(TestConstants.EXTRACTOR_LIB_DIR);

  // Top-level AQL file for the "eDA" annotator stack.
  public static final File EDISCOVERY_FULL_AQLFILE =
      new File(ANNOTATORS_ROOT, "aql/eDA/" + "ne-ediscovery-personorgphoneaddress.aql");

  // AQL file that generates all output types
  // public static final File EDISCOVERY_FULL_AQLFILE = new File(
  // ANNOTATOR_TESTER, "ediscovery-release/resources/configs/aql/"
  // + "ne-ediscovery-personorgphoneaddress.aql");

  public static final String[] EDISCOVERY_DICT_PATH_ELEMS =
      {ANNOTATORS_ROOT + "/aql/core/GenericNE/dictionaries",
          ANNOTATORS_ROOT + "/aql/core/Financial/dictionaries",};

  public static final String EDISCOVERY_DICT_PATH =
      StringUtils.join(EDISCOVERY_DICT_PATH_ELEMS, SearchPath.PATH_SEP_CHAR);

  // Include path for finding auxiliary files.
  public static final String EDISCOVERY_INCLUDE_PATH = (new File(ANNOTATORS_ROOT, "aql")).getPath();

  /**
   * Run the latest eDiscovery AQL file.
   */
  @Test
  public void ediscovery() throws Exception {
    startTest();

    if (false == ANNOTATORS_ROOT.exists()) {
      // SPECIAL CASE: User hasn't checked out a copy of AnnotatorTester.
      System.err.printf("No Annotators project.  Exiting.\n");
      return;
      // END SPECIAL CASE
    }

    final String DOCFILE_NAME = TestConstants.ENRON_SMALL_ZIP;
    // final String DOCFILE_NAME = TestConstants.ENRON_10K_DUMP;
    // final String DOCFILE_NAME = TestConstants.ENRON_37939_DUMP;
    // final String DOCFILE_NAME = TestConstants.ENRON_12_16_DUMP;
    // final String DOCFILE_NAME = TestConstants.SPOCK_DUMP;
    // final String DOCFILE_NAME = TestConstants.DUMPS_DIR +
    // "/spock_old.del";

    // scan = new DBDumpFileScan (DOCFILE_NAME);

    setDataPath(EDISCOVERY_DICT_PATH + SearchPath.PATH_SEP_CHAR + EDISCOVERY_INCLUDE_PATH);
    // setIncludePath(EDISCOVERY_INCLUDE_PATH);
    runNonModularAQLTest(new File(DOCFILE_NAME), EDISCOVERY_FULL_AQLFILE.getPath());
  }

  /** A test of AQL's attribute specification and evaluation capablity. */
  @Test
  public void attributeTest() throws Exception {
    startTest();
    String filename = AQL_FILES_DIR + "/attributeTest.aql";

    setPrintTups(true);
    runNonModularAQLTest(defaultDocsFile, filename);

    // TODO: Validate outputs.
  }

  /** Test of subquery support in AQL. */
  @Test
  public void subqueryTest() throws Exception {
    startTest();

    // Use the big input file so that we can see any speed regression.
    // scan = new DBDumpFileScan(TestConstants.ENRON_10K_DUMP);

    genericNonModularTestCase("subqueryTest");

    truncateOutputFiles(true);
    compareAgainstExpected(true);
  }

  /** Various tests of the "extract" statement in AQL. */
  @Test
  public void extractTests() throws Exception {
    // Use the big input file so that we can see any speed regression.
    // scan = new DBDumpFileScan(TestConstants.ENRON_10K_DUMP);

    // dumpAOG = true;

    // setPrintTups(true);
    startTest();
    setPrintTups(true);

    genericNonModularTestCase("extractTests");

    truncateOutputFiles(true);
    compareAgainstExpected(true);
  }

  /** Test of lookup table support in AQL. */
  @Test
  public void lookupTableTest() throws Exception {

    // dumpAOG = true;

    // Generate tables so that we can see all the columns of our output
    // tuples.
    startTest();
    setPrintTups(true);

    genericNonModularTestCase("lookupTableTest");

    truncateOutputFiles(true);
    compareAgainstExpected(true);
  }

  /** Test of the Chomp() scalar function */
  @Test
  public void chompTest() throws Exception {
    startTest();

    genericNonModularTestCase("chompTest");

    truncateOutputFiles(true);
    compareAgainstExpected(true);
  }

  /**
   * Test of the 'ExactMatch' consolidation type.
   */

  @Test
  public void exactMatchTest() throws Exception {
    startTest();

    setPrintTups(false);

    genericNonModularTestCase("exactMatchTest", new File(TestConstants.ENRON_SAMPLE_ZIP));
    truncateExpectedFiles();
    compareAgainstExpected(true);
  }

  /**
   * Test case for queries that try to fetch certain documents.
   */
  @Test
  public void fetchDocTest() throws Exception {
    startTest();
    genericNonModularTestCase("fetchDocTest");

    truncateOutputFiles(true);
    compareAgainstExpected(true);
  }

  /**
   * Test case for determining why a chunk of one of the url regexes produces fewer matches than the
   * entire regex. Turns out to have been a consolidation problem.
   */
  @Test
  public void urlRegexTest() throws Exception {
    startTest();

    // scan = DocScan.makeFileScan(new
    // File(TestConstants.ENRON_SAMPLE_ZIP));

    genericNonModularTestCase("urlRegexTest");

    compareAgainstExpected(true);
  }

  /**
   * Test case for queries that call the HashCode function.
   */
  @Test
  public void hashCodeTest() throws Exception {
    startTest();
    setPrintTups(true);

    genericNonModularTestCase("hashCodeTest");

    truncateOutputFiles(true);
    compareAgainstExpected(true);
  }

  /**
   * Test case for queries that call the Xor function.
   */
  @Test
  public void xorTest() throws Exception {
    startTest();
    setPrintTups(true);

    genericNonModularTestCase("xorTest");

    truncateOutputFiles(true);
    compareAgainstExpected(true);
  }

  /**
   * Test case for queries that use flags in the MatchesRegex function.
   */
  @Test
  public void flagsTest1() throws Exception {
    startTest();
    setPrintTups(true);

    genericNonModularTestCase("flagsTest1");

    truncateOutputFiles(true);
    compareAgainstExpected(true);
  }

  /**
   * Test case for queries that use flags in the ContainsRegex function.
   */
  @Test
  public void flagsTest2() throws Exception {
    startTest();
    setPrintTups(true);

    genericNonModularTestCase("flagsTest2");

    compareAgainstExpected(true);

  }

  /**
   * Test case for queries that use flags in the MatchesDict function.
   */
  @Test
  public void flagsTest3() throws Exception {
    startTest();
    setPrintTups(true);

    genericNonModularTestCase("flagsTest3");

    truncateOutputFiles(true);
    compareAgainstExpected(true);

  }

  /**
   * Test case for queries that use flags in the ContainsDict function.
   */
  @Test
  public void flagsTest4() throws Exception {
    startTest();
    setPrintTups(true);

    genericNonModularTestCase("flagsTest4");

    truncateOutputFiles(true);
    compareAgainstExpected(true);

  }

  /**
   * Test case for create function statement
   */
  @Test
  public void createFunctionTest() throws Exception {
    startTest();
    setPrintTups(true);

    genericNonModularTestCase("createFunctionTest");

    truncateOutputFiles(true);
    compareAgainstExpected(true);

  }

  /**
   * Test case for user-defined predicates
   */
  @Test
  public void udfPredTest() throws Exception {
    startTest();
    setPrintTups(true);

    genericNonModularTestCase("udfPredTest");

    truncateOutputFiles(true);
    compareAgainstExpected(true);
  }

  /**
   * Test case for user-defined functions with lofat arguments and return type
   */
  @Test
  public void udfFloatTest() throws Exception {
    startTest();
    setPrintTups(true);

    genericNonModularTestCase("udfFloatTest");

    truncateOutputFiles(true);
    compareAgainstExpected(true);
  }

  /**
   * Test case for a user-defined predicate that loads a non-class resource
   */
  @Test
  public void udfResourceTest() throws Exception {
    startTest();
    setPrintTups(true);

    genericNonModularTestCase("udfResourceTest");

    truncateOutputFiles(true);
    compareAgainstExpected(true);
  }

  /**
   * Test case for user-defined predicates, to test for classes having size more than 4096 kb.
   */
  @Test
  public void udfSizeTest() throws Exception {
    startTest();
    setPrintTups(true);

    genericNonModularTestCase("udfSizeTest");

    truncateOutputFiles(true);
    compareAgainstExpected(true);
  }

  /**
   * Test case for queries that call the GetLength function.
   */
  @Test
  public void getLengthTest() throws Exception {
    startTest();
    setPrintTups(true);

    genericNonModularTestCase("getLengthTest");

    truncateOutputFiles(true);
    compareAgainstExpected(true);
  }

  /**
   * Test case for queries that call the GetLengthTok function.
   */
  @Test
  public void getLengthTokTest() throws Exception {
    startTest();
    setPrintTups(true);

    genericNonModularTestCase("getLengthTokTest");

    truncateOutputFiles(true);
    compareAgainstExpected(true);
  }

  /**
   * Test case for queries that call the SubSpanTok function, which used to be called TokenRange()
   */
  @Test
  public void tokenRangeTest() throws Exception {
    startTest();
    setPrintTups(true);

    genericNonModularTestCase("tokenRangeTest");

    truncateOutputFiles(true);
    compareAgainstExpected(true);
  }

  /**
   * Test case for validating that wildcard expansion and alias inference work for ExtractNode
   * (extract statements other than EXTRACT PATTERN)
   */
  @Test
  public void extractExpandInferAliasTest() throws Exception {
    startTest();
    setPrintTups(true);

    genericNonModularTestCase("extractExpandInferAliasTest");

    truncateOutputFiles(true);
    compareAgainstExpected(true);
  }

  /**
   * Test case for queries that call the ContainsText predicate.
   */
  @Test
  public void containsTextTest() throws Exception {
    startTest();
    setPrintTups(true);

    genericNonModularTestCase("containsTextTest");

    truncateOutputFiles(true);
    compareAgainstExpected(true);
  }

  /**
   * Test case for running iopes annotators
   */
  @Test
  public void iopesTest() throws Exception {

    startTest();

    // scan = DocScan.makeFileScan(new
    // File(TestConstants.ENRON_SAMPLE_ZIP));
    String filename = AQL_FILES_DIR + "/iopes/RelationshipAnnotator.aql";
    String dictsdir = AQL_FILES_DIR + "/iopes";

    setDataPath(dictsdir);
    // compareAgainstExpected(false);

    runNonModularAQLTest(defaultDocsFile, filename);

    truncateOutputFiles(true);
    compareAgainstExpected(true);
  }

  /**
   * Test case for some regexes that were not having RSR applied to them.
   */
  @Test
  public void noRSRTest() throws Exception {
    startTest();
    genericNonModularTestCase("noRSRTest");
  }

  /**
   * Test of a standalone version of the U.S. address annotator from the named-entity stack.
   */
  @Test
  public void USAddressTest() throws Exception {
    startTest();
    final String AQL_FILE_NAME = String.format("%s/USAddress.aql", AQL_FILES_DIR);
    final String DICT_DIR_NAME = TestConstants.TESTDATA_DIR + "/dictionaries/enronTests/USAddress";

    setDataPath(DICT_DIR_NAME);
    runNonModularAQLTest(defaultDocsFile, AQL_FILE_NAME);

    compareAgainstExpected(true);
  }

  /**
   * Test case for translations of COBRA brand rules
   */
  @Test
  public void brandRuleTest() throws Exception {
    startTest();
    setPrintTups(true);

    genericNonModularTestCase("brandRuleTest");

    truncateOutputFiles(true);
    compareAgainstExpected(true);
  }

  /**
   * Test case for top-k queries
   */
  @Test
  public void topKTest() throws Exception {
    startTest();
    setPrintTups(true);

    genericNonModularTestCase("topKTest");

    truncateOutputFiles(true);
    compareAgainstExpected(true);
  }

  /**
   * Test of element inclusion in scalar list
   * 
   * @throws Exception
   */
  @Test
  public void listContainsTest() throws Exception {
    startTest();

    // scan = new DBDumpFileScan(TestConstants.TEST_DOCS_DIR
    // + "/aqlEnronTests/blurb.del");
    setPrintTups(true);

    genericNonModularTestCase("listContainsTest");

    truncateOutputFiles(true);
    compareAgainstExpected(true);

    // Don't screw up other tests' outputs.
    setPrintTups(false);
  }

  /**
   * Test case for user-defined predicates taking lists as input
   */
  @Test
  public void udfListsTest() throws Exception {
    startTest();
    setPrintTups(true);

    genericNonModularTestCase("udfListsTest");

    truncateOutputFiles(true);
    compareAgainstExpected(true);
  }

  /**
   * Test case for the new version of the ForwardBlock annotator
   */
  @Test
  public void newForwardBlockTest() throws Exception {
    startTest();
    setPrintTups(true);

    // Use a different Enron sample that has more interesting forward block
    // content.

    genericNonModularTestCase("newForwardBlockTest", new File(TestConstants.ENRON_SMALL_ZIP));

    truncateOutputFiles(true);
    compareAgainstExpected(true);
  }

  /**
   * Bug #159344 Issue while running AQL code: ArrayIndexOutOfBoundsException Size of the
   * profileStack array has been declared as 512 in class MemoizationTable.java This particular AQL
   * requires > 512 and hence the ArrayIndexOutOfBoundsException.
   */
  @Test
  public void consolidateMoreViewsBugTest() throws Exception {
    startTest();
    genericNonModularTestCase("PriorityAQL");
  }

  /**
   * Test case for user-defined predicates taking lists as input
   */
  @Test
  public void extractRegexSQLStyleTest() throws Exception {
    startTest();
    setPrintTups(true);

    genericNonModularTestCase("extractRegexSQLStyleTest");

    truncateOutputFiles(true);
    compareAgainstExpected(true);
  }

  /**
   * Version of {@link #genericNonModularTestCase(String, ImplType)} that uses the default doc file
   * and planner implementation.
   */
  private void genericNonModularTestCase(String prefix) throws Exception {
    genericNonModularTestCase(prefix, defaultDocsFile, Planner.DEFAULT_IMPL_TYPE);
  }

  /**
   * Version of {@link #genericNonModularTestCase(String, ImplType)} that uses the default doc file.
   */
  private void genericNonModularTestCase(String prefix, ImplType implType) throws Exception {
    genericNonModularTestCase(prefix, defaultDocsFile, implType);
  }

  /**
   * Version of {@link #genericNonModularTestCase(String, ImplType)} that uses the default planner
   * implementation.
   */
  private void genericNonModularTestCase(String prefix, File docInput) throws Exception {
    genericNonModularTestCase(prefix, docInput, Planner.DEFAULT_IMPL_TYPE);
  }

  /**
   * A generic AQL test case. Takes a prefix string as argument; runs the file prefix.aql and sends
   * output to testdata/regression/output/prefix. Also dumps the generated AOG plan to a file in the
   * output directory.
   * 
   * @param implType what implementation of the planner to use to compile the AQL
   * @param docInput file containing documents
   */
  private void genericNonModularTestCase(String prefix, File docInput, ImplType implType)
      throws Exception {

    // final String ENCODING = "UTF-8";

    Log.info("Starting test '%s'", prefix);

    File aqlFile = new File(AQL_FILES_DIR, String.format("%s.aql", prefix));

    runNonModularAQLTest(docInput, aqlFile);

  }

}
