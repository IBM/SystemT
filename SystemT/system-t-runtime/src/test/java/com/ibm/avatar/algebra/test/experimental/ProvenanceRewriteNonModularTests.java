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
package com.ibm.avatar.algebra.test.experimental;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.algebra.util.file.SearchPath;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.algebra.util.test.TestUtils;
import com.ibm.avatar.algebra.util.tokenize.TokenizerConfig;
import com.ibm.avatar.api.CompileAQL;
import com.ibm.avatar.api.CompileAQLParams;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.api.exceptions.CompilerException;
import com.ibm.avatar.aql.planner.Planner;
import com.ibm.avatar.aql.planner.Planner.ImplType;
import com.ibm.avatar.aql.tam.TAM;
import com.ibm.avatar.aql.tam.TAMSerializer;
import com.ibm.avatar.logging.Log;
import com.ibm.avatar.provenance.AQLProvenanceRewriter;

/**
 * Tests for the provenance rewrite when running in backward compatibility mode. Need to be merged
 * into {@link ProvenanceRewriteModularTests} once we drop support for non-modular AQL code.
 * 
 */
public class ProvenanceRewriteNonModularTests extends RuntimeTestHarness {

  /**
   * Directory where AQL files referenced in this class's regression tests are located.
   */
  public static final String AQL_FILES_DIR = TestConstants.AQL_DIR + "/provenanceTests";

  /**
   * Directory where the dictionary files are located.
   */
  public static final String DICTS_DIR = AQL_FILES_DIR + "/dictionaries";

  /**
   * Directory where the dictionary files are located.
   */
  public static final String UDFJARS_DIR = AQL_FILES_DIR + "/udfjars";

  /**
   * A collection of 1000 Enron emails shared by tests in this class.
   */
  public static final File DOCS_FILE = new File(TestConstants.DUMPS_DIR, "ensmall.zip");

  public static void main(String[] args) {
    try {

      // TEMPORARY: Watchdog thread
      // (new Thread() {
      // @Override
      // public void run ()
      // {
      // try {
      // Thread.sleep (30000);
      // }
      // catch (InterruptedException e) {
      // // Flow through
      // }
      // System.err.printf("Watchdog timer triggered. Shutting down process.\n");
      // System.exit (0);
      // }
      // }).start ();
      // END TEMPORARY CODE

      ProvenanceRewriteNonModularTests t = new ProvenanceRewriteNonModularTests();

      t.setUp();

      long startMS = System.currentTimeMillis();

      t.minusTest();

      long endMS = System.currentTimeMillis();

      t.tearDown();

      double elapsedSec = ((double) (endMS - startMS)) / 1000.0;

      System.err.printf("Test took %1.3f sec.\n", elapsedSec);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Before
  public void setUp() throws Exception {

    // Renice the current thread to avoid locking up the entire system.
    Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

    setDataPath(DICTS_DIR);

    // For now, don't put any character set information into the header of
    // our output HTML.
    setWriteCharsetInfo(false);
    setDisableOutput(false);
    this.setPrintTups(true);

    // Make sure that we don't dump query plans unless a particular test
    // requests it.
    this.dumpAOG = false;

    // Make sure our log messages get printed!
    Log.enableAllMsgTypes();
  }

  @After
  public void tearDown() {

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
   * Test case for the rewrite of the EXTRACT REGEX statement
   */
  @Test
  public void extractRegexTest() throws Exception {

    genericTestCase("extractRegex");
    compareAgainstExpected(true);
  }

  /**
   * Test case for the rewrite of the EXTRACT DICT statement
   */
  @Test
  public void extractDictTest() throws Exception {

    genericTestCase("extractDict");
    compareAgainstExpected(true);
  }

  /**
   * Test case for the rewrite of the EXTRACT PATTERN statement.
   */
  @Test
  public void extractPatternTest() throws Exception {

    genericTestCase("extractPattern");
    compareAgainstExpected(true);
  }

  /**
   * Test case for the rewrite of the SELECT statement
   */
  @Test
  public void selectTest() throws Exception {

    genericTestCase("select");
    compareAgainstExpected(true);
  }

  /**
   * Test case for the rewrite of the EXTRACT PART-OF-SPEECH statement as base views
   */
  @Test
  public void baseViewsTest() throws Exception {

    genericTestCase("baseViews");
    compareAgainstExpected(true);
  }

  /**
   * Test case for the rewrite of the SELECT statements with nested subqueries
   */
  @Test
  public void subqueryTest() throws Exception {

    genericTestCase("subquery");
    compareAgainstExpected(true);
  }

  /**
   * Test case for the rewrite of the UNION statements
   */
  @Test
  public void unionTest() throws Exception {

    genericTestCase("union");
    compareAgainstExpected(true);
  }

  /**
   * Test case for defect : Provenance rewrite compilation exception when the input AQL contains
   * external views
   */
  @Test
  public void externalViewTest() throws Exception {

    genericTestCase("externalView");
    compareAgainstExpected(true);
  }

  /**
   * Test case for the rewrite of the MINUS statement
   */
  @Test
  public void minusTest() throws Exception {

    genericTestCase("minus");
    compareAgainstExpected(true);
  }

  /**
   * Test case for the rewrite of the MINUS statement
   */
  @Test
  public void consolidateTest() throws Exception {
    genericTestCase("consolidate");
    compareAgainstExpected(true);
  }

  /**
   * Test case for bug #155177] Problem in AQL provenance rewrite when detag statement operates on
   * non-Document view.
   */
  @Test
  public void detagTest() throws Exception {

    genericTestCase("detag");
    compareAgainstExpected(true);
  }

  /**
   * Test case for bug #14463: NullPointerException in AQLProvenanceRewriter.topologicalSort()
   */
  @Test
  public void detagEmptySpecTest() throws Exception {

    genericTestCase("detagEmptySpec");
    // try {
    compareAgainstExpected(true);
    // }
    // catch (Exception e) {};
    // copyOutput ("detagEmptySpec");
  }

  /**
   * Test case for bug #18186: Provenance rewrite exception on UNION ALL/MINUS statements when one
   * of the operands is EXTRACT BLOCKS
   */
  @Test
  public void unionWithBlockTest() throws Exception {

    genericTestCase("unionWithBlock");
    compareAgainstExpected(true);
  }

  /**
   * Test case for bug #18186: Provenance rewrite exception on UNION ALL/MINUS statements when one
   * of the operands is EXTRACT BLOCKS
   */
  @Test
  public void subqueryWithBlockTest() throws Exception {

    genericTestCase("subqueryWithBlock");
    compareAgainstExpected(true);
  }

  /**
   * Test for defect : SystemT Tools: Results missing from the Provenance Viewer. Turned out that
   * the rewrtite for extract regex did not copy the flags
   */
  @Test
  public void extractWithFlagsTest() throws Exception {

    genericTestCase("extractWithFlags");
    compareAgainstExpected(true);
  }

  /**
   * Test for defect : From list example from AQL Reference gives provenance rewrite error when I
   * output view it. Turned out this was yet another problem due to the fact that parse tree nodes
   * don't know how to dump themselves correctly.
   */
  @Test
  public void doubleQuoteAliasTest() throws Exception {

    genericTestCase("doubleQuoteAlias");
    compareAgainstExpected(true);
  }

  /**
   * Test to ensure that NickLiterals that are double quote string literals with escaped quotes
   * inside the literal produce valid AOG. Currently commented out because it fails, probably due to
   * defect : CS defect : Problem with dictionary evaluation on entries ending with escape character
   * 
   * @throws Exception
   */
  // @Test
  public void doubleQuoteAndEscapedAliasTest() throws Exception {

    genericTestCase("doubleQuoteAndEscapedAlias");
    compareAgainstExpected(true);
  }

  /**
   * Test cases to make sure statements with wildcards in the select list are rewritten properly.
   */
  @Test
  public void extractWithStarTest() throws Exception {

    genericTestCase("extractWithStar");
    compareAgainstExpected(true);
  }

  /**
   * Test case for create table statements.
   */
  @Test
  public void lookupTableTest() throws Exception {

    genericTestCase("lookupTable");
    compareAgainstExpected(true);
  }

  /**
   * Test case for a create view and a create function with the same name. Currently fails because
   * modular AQL seems to consider the names duplicates, breaking backward compatibility: defect :
   * Backward compatibility is broken for table/dictionary/function names that have the same name as
   * a view.
   */
  @Test
  public void functionViewNameConflictTest() throws Exception {
    genericTestCase("functionViewNameConflict");
    compareAgainstExpected(true);
  }

  /**
   * Test case for the rewrite of the MINUS statement
   */
  @Test
  public void personBaseExtractionTest() throws Exception {
    genericTestCase("PersonPhone-complex");
    compareAgainstExpected(true);
  }

  /**
   * Test case for the rewrite of the Sigmod 2011 demo query
   */
  @Test
  public void personSimpleTest() throws Exception {

    genericTestCase("personSimple");
    compareAgainstExpected(true);
  }

  /**
   * Test case for bug #120960. Verify that the rewrite works when the top-level AQL is a String.
   */
  @Test
  public void dictFileBugTest() throws Exception {
    startTest("dictNameEscBug");

    // File rewrittenModuleDir = new File (String.format ("%s/%s", getCurOutputDir (),
    // "dictNameEscBug"));
    File rewrittenModuleDir = getCurOutputDir();

    // AQL for a simple extraction statement
    final String aql = ""//
        + "create view Name as \n"//
        + "extract dictionary 'names.dict' on D.text as name "//
        + "from Document D;\n" //
        + "output view Name;\n";

    String includePath = "";
    String dictPath = TestConstants.TESTDATA_DIR + "/dictionaries";
    String udfJarPath = TestConstants.TESTDATA_DIR + "/udfjars";
    setDataPath(includePath, dictPath, udfJarPath);

    Log.info("Dictionary path is: %s", dictPath);

    // Prepare compile parameters
    String dataPathStr = getDataPath();
    Log.info("Data path is: %s", dataPathStr);

    File inputFile = null;
    CompileAQLParams params =
        new CompileAQLParams(inputFile, rewrittenModuleDir.toURI().toString(), dataPathStr);
    params.setInputStr(aql);
    params.setTokenizerConfig(getTokenizerConfig());

    // Rewrite the AQL to a string
    AQLProvenanceRewriter rewriter = new AQLProvenanceRewriter();
    rewriter.rewriteAQL(params, null);

    // Compare against the expected result.
    compareAgainstExpected(true);

  }

  /**
   * Test case for provenance support for Gumshoe LA annotators. Currently fails because of some
   * problem with schema inference in provenance rewrite.
   */
  @Test
  public void w3LATest() throws Exception {
    startTest();

    File rewrittenModuleDir = new File(String.format("%s/genericModule", getCurOutputDir()));
    String AQL_DIR = TestConstants.AQL_DIR + "/w3-ported/w3-LA";
    // String DOC_FILE = TestConstants.TEST_DOCS_DIR +
    // "/provenanceTests/sample_la_docid_jaql_java-2010-08-13.zip";
    // String DOC_FILE = TestConstants.TEST_DOCS_DIR +
    // "/provenanceTests/sample_la_docid_jaql_java-2010-08-13.zip";

    File rewrittenAQLParent = getCurOutputDir();

    File INPUT_AQLFILE = new File(String.format("%s/%s", AQL_DIR, "localAnalysis.aql"));

    rewrittenAQLParent.mkdirs();

    // Prepare compile parameters
    String includePath = AQL_DIR;
    String dictPath = AQL_DIR + "/localAnalysis/dicts;" + AQL_DIR + "/GenericNE";
    String udfJarPath = AQL_DIR + "/localAnalysis/udfjars";

    String dataPathStr = String.format("%s%c%s%c%s", includePath, SearchPath.PATH_SEP_CHAR,
        dictPath, SearchPath.PATH_SEP_CHAR, udfJarPath);

    Log.info("Data path is: %s", dataPathStr);
    // FIXME: Now that outputFile is removed from CompileAQLParams, AQLProvenanceRewriter should use
    // alternate way of
    // passing rewrittenAQLFile parameter to rewriteAQL() method. Temporarily passing some arbitrary
    // value to allow
    // compiler to succeed.
    CompileAQLParams params =
        new CompileAQLParams(INPUT_AQLFILE, rewrittenAQLParent.toURI().toString(), dataPathStr);
    params.setTokenizerConfig(getTokenizerConfig());

    // Rewrite the input AQL
    AQLProvenanceRewriter rewriter = new AQLProvenanceRewriter();
    rewriter.rewriteAQL(params, null);

    // Parse the rewritten AQL.
    Log.info("Compiling rewritten AQL module '%s'", rewrittenModuleDir);

    // Prepare compile parameter
    params.setPerformSDM(false);
    params.setPerformSRM(false);
    params.setInputFile(null);
    params.setInputModules(new String[] {rewrittenModuleDir.toURI().toString()});
    String moduleURI = getCurOutputDir().toURI().toString();
    params.setOutputURI(moduleURI);

    // Compile
    CompileAQL.compile(params);

    // Load tam
    TAM tam = TAMSerializer.load(Constants.GENERIC_MODULE_NAME, params.getOutputURI());
    String aog = tam.getAog();

    // Dump the plan to a file in the output directory.
    File planFile = new File(getCurOutputDir(), "plan.aog");
    Log.info("Dumping plan to %s", planFile);
    FileWriter aogOut = new FileWriter(planFile);
    aogOut.append(aog);
    aogOut.close();

    if (dumpAOG) {
      System.err.print("-----\nAOG plan is:\n" + aog);
    }

    // DocReader docs = new DocReader (new File (DOC_FILE));
    setDataPath(includePath, dictPath, udfJarPath);
    System.setProperty("gumshoe.root", "../w3hadoop");

    // Try running the plan. COmmented out because it's missinng one of the gumshoe resources
    // Log.info ("Instantiating operator graph with module '%s' and module path URI '%s'...",
    // Constants.GENERIC_MODULE_NAME, moduleURI);
    //
    // OperatorGraph og = OperatorGraph.createOG (new String[] { Constants.GENERIC_MODULE_NAME },
    // moduleURI, null,
    // new TokenizerConfig.WhiteSpaceTokenizerConfig ());
    //
    // Log.info ("Executing operator graph on collection '%s'...", DOCS_FILE.getPath ());
    // annotateAndPrint (DOCS_FILE, og);

    // Temporarly skipping plan.aog comparision due to difference in order of views being output-ed
    // across OS/JVM.
    // Un-comment following line once task# 28911 is fixed and remove the following line
    // util.compareAgainstExpected (true);
    compareAgainstExpectedSkipAOG(true);

  }

  /*
   * ADD MORE TESTS HERE
   */

  /*
   * PRIVATE METHODS GO HERE
   */

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
  private boolean dumpAOG = true;

  /**
   * A generic AQL test case. Takes a prefix string as argument; runs the file prefix.aql and sends
   * output to testdata/regression/output/prefix. Also dumps the generated AOG plan to a file in the
   * output directory.
   * 
   * @param implType what implementation of the planner to use to compile the AQL
   */
  private void genericTestCase(String prefix, ImplType implType) throws Exception {
    File INPUT_AQLFILE = new File(String.format("%s/%s.aql", AQL_FILES_DIR, prefix));
    startTest(prefix);
    File outputDir = getCurOutputDir();
    Log.info("OutputDir is: %s", outputDir);

    // Prepare compile parameters
    String dataPathStr =
        String.format("%s%c%s", DICTS_DIR, SearchPath.PATH_SEP_CHAR, TestConstants.TESTDATA_DIR);

    Log.info("Data path is: %s", dataPathStr);
    CompileAQLParams params =
        new CompileAQLParams(INPUT_AQLFILE, outputDir.toURI().toString(), dataPathStr);
    params.setTokenizerConfig(new TokenizerConfig.Standard());
    params.setPerformSDM(false);
    params.setPerformSRM(false);

    // Rewrite the input AQL
    AQLProvenanceRewriter rewriter = new AQLProvenanceRewriter();
    rewriter.rewriteAQL(params, null);

    File rewrittenModuleDir = new File(String.format("%s/genericModule", outputDir));
    // Parse the rewritten AQL.
    Log.info("Compiling rewritten AQL file '%s'", rewrittenModuleDir.getPath());

    // Prepare compile parameter
    params.setInputFile(null);
    params.setInputModules(new String[] {rewrittenModuleDir.toURI().toString()});
    String moduleURI = new File(String.format("%s/genericModule", outputDir)).toURI().toString();
    params.setOutputURI(moduleURI);
    File genericTamFile =
        new File(String.format("%s/genericModule", outputDir), "genericModule.tam");

    // Compile
    try {
      CompileAQL.compile(params);
    } catch (CompilerException e) {
      // Print out full stack traces to help with debugging.
      System.err.printf("%d compile errors encountered; dumping stack traces.\n",
          e.getSortedCompileErrors().size());

      for (Exception error : e.getSortedCompileErrors()) {
        error.printStackTrace();
      }

      // Rethrow the original exception to cause the test case to fail.
      throw e;
    }

    Log.info("Loading generic module of rewritten AQL", dataPathStr);
    TAM tam = TAMSerializer.load(Constants.GENERIC_MODULE_NAME, moduleURI);
    if (dumpAOG) {
      System.err.print("-----\nAOG plan is:\n" + tam.getAog());
    }

    Log.info("Executing generic module");

    // Try running the plan.
    try {

      Log.info("Instantiating operator graph with module '%s' and module path URI '%s'...",
          Constants.GENERIC_MODULE_NAME, moduleURI);

      OperatorGraph og = OperatorGraph.createOG(new String[] {Constants.GENERIC_MODULE_NAME},
          moduleURI, null, new TokenizerConfig.Standard());

      Log.info("Executing operator graph on collection '%s'...", DOCS_FILE.getPath());
      annotateAndPrint(DOCS_FILE, og);
    } catch (Exception e) {
      System.err.printf("Caught exception while parsing AOG; " + "original AOG plan was:\n%s\n",
          tam.getAog());
      throw new RuntimeException(e);
    }

    // Extract the contents of the TAM so we can look at it without jumping through hoops
    TestUtils.unJar(genericTamFile, rewrittenModuleDir);

  }

  /**
   * Temporary utility to copy the unjarred version of the actual and expected modules into the
   * workspace so we can look at the differences. Use it on a need basis.
   * 
   * @param prefix
   * @throws IOException
   */
  @SuppressWarnings("unused")
  private void copyOutput(String prefix) throws IOException {
    File dest = new File(String.format("%s/%s", getCurOutputDir(), prefix));
    File actual = new File(dest, "actual");
    File expected = new File(dest, "expected");
    actual.mkdirs();
    expected.mkdirs();
    FileUtils.copyDir(new File("C:/Users/IBM_ADMIN/actual/genericModule"), actual);
    FileUtils.copyDir(new File("C:/Users/IBM_ADMIN/expected/genericModule"), expected);
  }

  // This method provides a single place to turn truncation on or off.
  // private void doComparisons() throws Exception {
  // // util.truncateExpectedFiles();
  // compareAgainstExpected(true);
  // }

  /**
   * Compare every file in the "expected" with the corresponding file in the "output" dir. This
   * method skips comparing the *.aog files.
   * 
   * @param truncate true to truncate the files being compared to 1000 lines
   */
  public void compareAgainstExpectedSkipAOG(boolean truncate) throws Exception {

    File[] expectedFiles = getCurExpectedDir().listFiles();
    if (null == expectedFiles) {
      throw new Exception("Expected dir " + getCurExpectedDir() + " does not exist");
    }

    for (File file : expectedFiles) {
      // Skip directories (including the CVS directory)
      if (file.isFile()) {
        // SPECIAL CASE: Ignore NFS temporary files
        if (true == file.getName().endsWith(".aog")) {
          // skip aog comparision
        }
        // Special case metadata.xml - skip comparison for the first two lines
        else if ("metadata.xml".equals(file.getName())) {
          File expected = file;
          File actual = new File(getCurOutputDir(), file.getName());
          TestUtils.compareFiles(expected, actual, 2, -1);
        } else {
          compareAgainstExpected(file.getName(), truncate);
        }
      }
    }

  }

}
