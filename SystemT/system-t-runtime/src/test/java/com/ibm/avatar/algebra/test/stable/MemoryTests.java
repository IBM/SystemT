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

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.algebra.util.file.SearchPath;
import com.ibm.avatar.algebra.util.test.MemoryProfiler;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.api.CompileAQL;
import com.ibm.avatar.api.CompileAQLParams;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.DocReader;
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.api.OperatorGraphRunner;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.compiler.ParseToCatalog;
import com.ibm.avatar.aql.planner.Planner;
import com.ibm.avatar.aql.tam.ModuleUtils;
import com.ibm.avatar.aql.tam.TAM;
import com.ibm.avatar.aql.tam.TAMSerializer;
import com.ibm.avatar.logging.Log;

/**
 * Tests of memory usage.
 */
public class MemoryTests extends RuntimeTestHarness {

  /**
   * Directory for holding AQL files that are specific to the test cases in this class.
   */
  public static final String AQL_FILES_DIR = TestConstants.AQL_DIR + "/memoryTests";

  /**
   * Directory for holding AOG files that are specific to the test cases in this class.
   */
  public static final String AOG_FILES_DIR = TestConstants.AOG_DIR + "/memoryTests";

  /** Location of the SDA 1.4 AQL code that is used by tests in this harness */
  public static final String SDA_AQL_DIR_NAME =
      TestConstants.AQL_DIR + "/BackwardCompatibilityModuleTests/sda_29745";

  /** Data path to invoke the SDA extractors using various different main AQL files */
  public static final String SDA_DATA_PATH =
      String.format("%s%c%s/udfjars", SDA_AQL_DIR_NAME, SearchPath.PATH_SEP_CHAR, SDA_AQL_DIR_NAME);

  /**
   * Encoding to use for the packed AQL file that we create out of lotus/namedentity.aql
   */
  public static final String PACKED_AQL_ENCODING = "UTF-8";

  public static void main(String[] args) {
    try {

      MemoryTests t = new MemoryTests();

      t.setUp();

      long startMS = System.currentTimeMillis();

      t.sekarQuick();

      long endMS = System.currentTimeMillis();

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

    setDataPath(TestConstants.TESTDATA_DIR);

  }

  @After
  public void tearDown() {

    // Deregister the Derby driver so that other tests can connect to other
    // databases.
    try {
      DriverManager.getConnection("jdbc:derby:;shutdown=true");
    } catch (SQLException e) {
      // The shutdown command always raises a SQLException
      // See http://db.apache.org/derby/docs/10.2/devguide/
    }
    System.gc();
  }

  /**
   * Benchmark of what happens when you run the JVM
   */

  /**
   * Memory benchmark of sekar's named entity annotators, running over the enron 10k docs data set
   */
  @Test
  public void sekar10k() throws Exception {
    startTest();
    String filename = TestConstants.AQL_DIR + "/memoryTests/namedentity-sekar.aql";
    String docsfile = TestConstants.ENRON_10K_DUMP;

    memBench(filename, docsfile, 0.1);
  }

  /**
   * Memory benchmark that tries to replicate what goes on inside the plugin. NOTE FROM LAURA
   * 2/10/2012: This test was disabled since revision 130 of old CS svn. END NOTE FROM LAURA
   * 2/10/2012.
   */
  // @Test
  // public void lotusBench() throws Exception {
  //
  // String AQLFILE = "testdata/aql/lotus/namedentity-packed.aql";
  // // String DOCSFILE = TestConstants.ENRON_10K_DUMP;
  // String DICTSDIR = "testdata";
  // // final double SAMPLE_RATE = 0.1;
  //
  // if (false == (new File(AQLFILE)).exists()) {
  // createPackedAQLFile(AQLFILE);
  // }
  //
  // MemoryProfiler.dumpMemory("Beginning");
  //
  // // Compile to AOG, using the default planner implementation.
  // AQLParser parser = new AQLParser(new File(AQLFILE), PACKED_AQL_ENCODING);
  // parser.getCatalog().setDictsPath(DICTSDIR);
  // parser.parse();
  //
  // MemoryProfiler.dumpMemory("After parsing");
  //
  // Planner p = new Planner();
  // String aog = p.compileToString(parser.getCatalog());
  //
  // MemoryProfiler.dumpMemory("After compile to AOG");
  //
  // // Create the object that will compile and run the graph.
  // AOGRunner runner = AOGRunner.compileStr(aog, DICTSDIR);
  // runner.setBufferOutput();
  //
  // runner.setPushInput(1, null);
  //
  // MemoryProfiler.dumpMemory("After instantiating OperatorGraphRunner");
  //
  // }
  /**
   * Test that more closely replicates the Lotus deployment. Embeds the dictionaries in the AQL and
   * runs all of the... NOTE FROM LAURA 2/10/2012: This test was disabled since revision 9 of old CS
   * svn. END NOTE FROM LAURA 2/10/2012.
   */

  // @Test
  // public void lotusMockup() throws Exception {
  //
  // // Planner.DEFAULT_RSR = false;
  // // MatchesRegex.STRENGTH_REDUCE = true;
  //
  // boolean useRSR = true;
  //
  // util.setOutputDir(TestUtils.DEFAULT_OUTPUT_DIR + "/lotusMockup");
  // util.cleanOutputDir();
  //
  // // String DOCSFILE = TestConstants.ENRON_10K_DUMP;
  // String DOCSFILE = TestConstants.ENRON_1K_DUMP;
  //
  // String AQL_PACKED = util.getOutputDir() + "/namedentity-packed.aql";
  // String AOG_PACKED = util.getOutputDir() + "/namedentity-packed.aog";
  //
  // AQLPackagerTests.packProductionNEFile(AQL_PACKED, PACKED_AQL_ENCODING);
  //
  // // The Lotus plugin now compiles an AOG file. Generate one.
  //
  // CompileAQL.run(AQL_PACKED, AOG_PACKED, PACKED_AQL_ENCODING,
  // PACKED_AQL_ENCODING, useRSR);
  //
  // MemoryProfiler.dumpMemory("Before loading AOG");
  //
  // // We currently don't use regex strength reduction.
  // // com.ibm.avatar.algebra.function.RegexConst.STRENGTH_REDUCE = useRSR;
  //
  // // Load AOG from a stream.
  // AOGRunner runner = AOGRunner.compileFile(new File(AOG_PACKED),
  // PACKED_AQL_ENCODING, "not a dictionary path");
  // // OperatorGraphRunner runner = new OperatorGraphRunner(
  // // new InputStreamReader(new FileInputStream(AOG_PACKED),
  // // PACKED_AQL_ENCODING));
  //
  // // Read the size of the heap, including temporary objects, after
  // // parsing.
  // MemoryProfiler.dumpHeapSize("After loading AOG");
  // MemoryProfiler.dumpMemory("After loading AOG");
  //
  // // runner.setWorkingDir(DICTS_DIR_NAME);
  // runner.setBufferOutput();
  // runner.setPushInput(1, null);
  //
  // // Clear out the cache of regex atoms.
  // CharSet.clearCachedSets();
  //
  // MemoryProfiler.dumpHeapSize("After instantiating operators");
  // MemoryProfiler.dumpMemory("After instantiating operators");
  //
  // // System.exit(1);
  //
  // runLotus(DOCSFILE, runner);
  // }
  /**
   * Memory test for the actual Lotus drop.
   */
  @Test
  public void lotusActualDrop() throws Exception {

    // String DOCSFILE = TestConstants.ENRON_10K_DUMP;
    String DOCSFILE = TestConstants.ENRON_1K_DUMP;

    File ANNOTATORS_PROJECT_ROOT = new File(TestConstants.TEST_WORKING_DIR, "../ExtractorLibrary");
    if (false == ANNOTATORS_PROJECT_ROOT.exists()) {
      // Don't run the test if we don't have the proper input files.
      Log.info("ExtractorLibrary project (%s) doesn't exist; exiting test.",
          ANNOTATORS_PROJECT_ROOT);
      return;
    }

    // Don't use the AOG anymore. Use the TAM instead
    // File AOG_FILE = new File (ANNOTATORS_PROJECT_ROOT + "/build/aog/clientSideNE.aog");
    //
    // if (false == AOG_FILE.exists ()) {
    // // Don't run the test if we don't have the proper input file.
    // Log.info ("AOG file (%s) doesn't exist; exiting test.", AOG_FILE);
    // return;
    // }

    File TAM_FILE = new File(ANNOTATORS_PROJECT_ROOT + "/build/aog/clientSideNE/genericModule.tam");

    if (false == TAM_FILE.exists()) {
      // Don't run the test if we don't have the proper input file.
      Log.info("TAM file (%s) doesn't exist; exiting test.", TAM_FILE);
      return;
    }

    // We currently don't use regex strength reduction.
    // boolean useRSR = true;
    // boolean oldRSR =
    // com.ibm.avatar.algebra.function.RegexConst.STRENGTH_REDUCE;
    // com.ibm.avatar.algebra.function.RegexConst.STRENGTH_REDUCE = useRSR;

    MemoryProfiler.dumpMemory("Before loading AOG");

    // Don't load from AOG. Load from TAM instead. Also, parsing from AOG and instantiating the
    // operator graph happen
    // immediately one after the other. So we just emasure the heap size after instantiating the
    // operator graph.
    // AOGRunner runner = AOGRunner.compileFile (AOG_FILE, PACKED_AQL_ENCODING,
    // TestConstants.EMPTY_CD_LIST);

    // Read the size of the heap, including temporary objects, after parsing.
    // MemoryProfiler.dumpHeapSize ("After loading AOG");
    // MemoryProfiler.dumpMemory ("After loading AOG");
    //
    // // runner.setWorkingDir(DICTS_DIR_NAME);
    // runner.setBufferOutput ();
    // runner.setNumThreads (1);
    //
    // runner.pushDoc ("");

    OperatorGraph og = OperatorGraph.createOG(new String[] {Constants.GENERIC_MODULE_NAME},
        TAM_FILE.getParentFile().toURI().toString(), null, getTokenizerConfig());

    MemoryProfiler.dumpHeapSize("After parsing AOG and instantiating operators");
    MemoryProfiler.dumpMemory("After parsing AOG and instantiating operators");

    // System.exit(1);
    // System.err.println("*********************************");
    // Thread.sleep(100000);

    runLotus(DOCSFILE, og, false);

    // Thread.sleep(100000);
    // runner.pushDoc("");

    // com.ibm.avatar.algebra.function.RegexConst.STRENGTH_REDUCE = oldRSR;
  }

  /**
   * Push documents through an OperatorGraphRunner in a way that simulates a Lotus Notes
   * installation.
   * 
   * @param cleanup true to clean up memory after running
   */
  private void runLotus(String docsFile, OperatorGraph og, boolean cleanup) throws Exception {

    // The file containing documents is in DB2 comma-delimited dump format.
    // Manually instantiate a scan operator to get at the text fields of the
    // document.
    // DocScan docscan = DocScan.makeFileScan(new File(docsFile));

    DocReader docs = new DocReader(new File(docsFile));

    // Create an accessor for getting at the document text in our document
    // tuples.
    // FieldGetter<Span> getText = docscan.textGetter();

    // MemoizationTable mt = new MemoizationTable(docscan);

    // ////////////////////////////////////////////////////////////////////
    // EXECUTION
    int ndoc = 0;
    // while (mt.haveMoreInput()) {
    while (docs.hasNext()) {

      // Unpack the document text from the scan's output.
      // mt.resetCache();
      // Tuple doc = docscan.getNextDocTup(mt);

      // String doctext = getText.getVal(doc).getText();
      Tuple doc = docs.next();

      // Feed the document text into the operator graph and retrieve all results for the document
      // text we just pushed
      // through.
      og.execute(doc, null, null);

      ndoc++;

      if (0 == ndoc % 1000) {
        // System.err.printf("Processed %d docs\n", ndoc);
        MemoryProfiler.dumpMemory(String.format("After %d docs", ndoc));
      }
    }

    // docscan = null;
    // mt = null;

    MemoryProfiler.dumpMemory("After pushing all documents");

    // Close the document reader
    docs.remove();

    // TEMPORARY: Generate a heap dump. Only works on IBM java!
    // com.ibm.jvm.Dump.HeapDump();

    // System.err.printf("Press return to continue.\n");
    // System.in.read();

    if (cleanup) {
      // Tell the graph runner to clean up.
      // runner.endOfPushInput (0);

      // MemoryProfiler.dumpMemory ("After cleanup");
    }
  }

  /**
   * Push documents through an OperatorGraphRunner in a way that simulates a Lotus Notes
   * installation.
   * 
   * @param cleanup true to clean up memory after running
   */
  public void runLotus(String docsFile, OperatorGraphRunner runner, boolean cleanup)
      throws Exception {

    // The file containing documents is in DB2 comma-delimited dump format.
    // Manually instantiate a scan operator to get at the text fields of the
    // document.
    // DocScan docscan = DocScan.makeFileScan(new File(docsFile));

    Iterator<String> itr = DocReader.makeDocTextItr(new File(docsFile));

    // Create an accessor for getting at the document text in our document
    // tuples.
    // FieldGetter<Span> getText = docscan.textGetter();

    // MemoizationTable mt = new MemoizationTable(docscan);

    // ////////////////////////////////////////////////////////////////////
    // EXECUTION
    int ndoc = 0;
    // while (mt.haveMoreInput()) {
    while (itr.hasNext()) {

      // Unpack the document text from the scan's output.
      // mt.resetCache();
      // Tuple doc = docscan.getNextDocTup(mt);

      // String doctext = getText.getVal(doc).getText();
      String doctext = itr.next();

      // Feed the document text into the operator graph.
      runner.pushDoc(doctext);

      // Retrieve all results for the document text we just pushed
      // through.
      for (String outputName : runner.getOutputNames()) {
        runner.getResults(outputName);
      }

      ndoc++;

      if (0 == ndoc % 1000) {
        // System.err.printf("Processed %d docs\n", ndoc);
        MemoryProfiler.dumpMemory(String.format("After %d docs", ndoc));
      }
    }

    // docscan = null;
    // mt = null;
    // Close the document reader
    itr.remove();

    MemoryProfiler.dumpMemory("After pushing all documents");

    // TEMPORARY: Generate a heap dump. Only works on IBM java!
    // com.ibm.jvm.Dump.HeapDump();

    // System.err.printf("Press return to continue.\n");
    // System.in.read();

    if (cleanup) {

      MemoryProfiler.dumpMemory("After cleanup");
    }
  }

  /**
   * Test of Lotus compile overhead, with inline dictionaries and AQL input. NOTE FROM LAURA
   * 2/10/2012: This test was commented out since revision 130 of old CS svn. END NOTE FROM LAURA
   * 2/10/2012.
   */
  // @Test
  // public void lotusCompilePackedAQL() throws Exception {
  //
  // System.err.print("*** Measuring memory usage"
  // + " when compiling AQL with inline dicts.\n");
  //
  // String AQL_PACKED = TestConstants.TESTDATA_DIR
  // + "/aql/lotus/namedentity-packed.aql";
  //
  // if (false == (new File(AQL_PACKED)).exists()) {
  // createPackedAQLFile(AQL_PACKED);
  // }
  //
  // // The following is a simplified version of PushModeTests.lotusMockup().
  // // I've removed the output code and the code that toggles outputs; also
  // // added some memory-tracing code.
  //
  // MemoryProfiler.dumpMemory("Baseline");
  //
  // AQLParser parser = new AQLParser(new File(AQL_PACKED),
  // PACKED_AQL_ENCODING);
  // parser.parse();
  //
  // MemoryProfiler.dumpHeapSize("After parsing AQL");
  // MemoryProfiler.dumpMemory("After parsing AQL");
  //
  // // Use whatever is the default planner to compile the query.
  // Planner p = new Planner();
  // String aog = p.compileToString(parser.getCatalog());
  // parser = null;
  // p = null;
  //
  // MemoryProfiler.dumpHeapSize("After compilation");
  // MemoryProfiler.dumpMemory("After compilation");
  //
  // AOGRunner runner = AOGRunner.compileStr(aog);
  //
  // MemoryProfiler.dumpHeapSize("After parsing AOG");
  // MemoryProfiler.dumpMemory("After parsing AOG");
  //
  // // runner.setWorkingDir(DICTS_DIR_NAME);
  // runner.setBufferOutput();
  // runner.setPushInput(1, null);
  //
  // MemoryProfiler.dumpHeapSize("After instantiating operators");
  // MemoryProfiler.dumpMemory("After instantiating operators");
  //
  // runLotus(TestConstants.ENRON_1_DUMP, runner);
  // }

  /**
   * Test of Lotus compile overhead, with dictionary files and AQL input
   */
  @Test
  public void lotusCompileAQL() throws Exception {
    File genericModuleDir = null;
    try {
      startTest();

      System.err
          .print("*** Measuring memory usage" + " when compiling AQL with dictionary files.\n");

      String AQL_FILE = TestConstants.TESTDATA_DIR + "/aql/lotus/namedentity.aql";
      String ENCODING = "UTF-8";

      String DICTS_DIR_NAME = TestConstants.TESTDATA_DIR;

      URI outputURI = getCurOutputDir().toURI();

      // MemoryProfiler.dumpMemory ("Baseline");

      // We cannot parse non modular AQL anymore without compiling it. So transform the non-modular
      // code into modular
      // code
      // and then parse.
      // CompileAQLParams params = new CompileAQLParams (new File (AQL_FILE), null, DICTS_DIR_NAME);
      // params.setInEncoding (ENCODING);
      // ParseToCatalog catalogGenerator = new ParseToCatalog ();
      // catalogGenerator.parse (params);

      // Make a generic module
      CompileAQLParams params =
          new CompileAQLParams(new File(AQL_FILE), outputURI.toString(), DICTS_DIR_NAME);
      params.setInEncoding(ENCODING);
      genericModuleDir = ModuleUtils.createGenericModule(params, new HashMap<String, String>(),
          new ArrayList<Exception>());

      CompileAQLParams modularParams = new CompileAQLParams();
      modularParams.setInEncoding(ENCODING);
      modularParams.setInputModules(new String[] {genericModuleDir.toURI().toString()});
      modularParams.setOutputURI(outputURI.toString());
      modularParams.setTokenizerConfig(getTokenizerConfig());

      MemoryProfiler.dumpMemory("Baseline");

      ParseToCatalog catalogGenerator = new ParseToCatalog();
      catalogGenerator.parse(modularParams);

      MemoryProfiler.dumpHeapSize("After parsing AQL");
      MemoryProfiler.dumpMemory("After parsing AQL");

      // Use whatever is the default planner to compile the query.
      Planner planner = new Planner();
      TAM tam = new TAM(Constants.GENERIC_MODULE_NAME);
      planner.compileToTAM(catalogGenerator.getCatalog(), tam);
      catalogGenerator = null;
      planner = null;

      MemoryProfiler.dumpHeapSize("After compilation to AOG, but without compiling dictionaries");
      MemoryProfiler.dumpMemory("After compilation to AOG, but without compiling dictionaries");

      // Now compile the entire TAM beginning to end, including creating the generic module, parsing
      // AQL, compiling
      // dictionaries
      MemoryProfiler.dumpMemory("Baseline");
      CompileAQL.compile(modularParams);

      MemoryProfiler.dumpHeapSize(
          "After end-to-end compilation of generic module, including compiling dictionaries");
      MemoryProfiler.dumpMemory(
          "After end-to-end compilation of generic module, including compiling dictionaries");

      // Can't separate the parsing of the AOG and the instantiation of the Operator graph. Measure
      // the memory after
      // instantiation only
      // TODO: In the following call replace the empty list of compiled dictionaries by dictionaries
      // from TAM
      // AOGRunner runner = AOGRunner.compileStr (aog, TestConstants.EMPTY_CD_LIST);
      //
      // MemoryProfiler.dumpHeapSize ("After parsing AOG");
      // MemoryProfiler.dumpMemory ("After parsing AOG");
      //
      // runner.setBufferOutput ();
      // runner.setNumThreads (1);
      //
      // MemoryProfiler.dumpHeapSize ("After instantiating operators");
      // MemoryProfiler.dumpMemory ("After instantiating operators");

      OperatorGraph og = OperatorGraph.createOG(new String[] {Constants.GENERIC_MODULE_NAME},
          getCurOutputDir().toURI().toString(), null, getTokenizerConfig());

      MemoryProfiler.dumpHeapSize("After parsing AOG and instantiating operators");
      MemoryProfiler.dumpMemory("After parsing AOG and instantiating operators");

      runLotus(TestConstants.ENRON_1_DUMP, og, false);

    } finally {
      if (genericModuleDir != null) {
        FileUtils.deleteDirectory(genericModuleDir.getParentFile()); // parent file is the
                                                                     // moduleUtilsTmp directory
        // created by ModuleUtils.createGenericModule()
      }
    }
  }

  /**
   * Test of memory consumption during compilation of the annotators from eDA 2.0. Also runs some
   * documents through the operator graph for good measure.
   */
  @Test
  public void edaCompileAQL() throws Exception {
    File genericModuleDir = null;
    try {
      startTest();

      final int SLEEP_TIME_MS = 0;
      final boolean USE_RSR = true;

      System.err
          .print("*** Measuring memory usage" + " when compiling AQL with dictionary files.\n");
      Thread.sleep(SLEEP_TIME_MS);

      String ROOT_DIR = AQL_FILES_DIR + "/eDA";
      File AQL_FILE = new File(ROOT_DIR, "/ne-ediscovery-personorgphoneaddress.aql");
      String ENCODING = "UTF-8";

      String DICTS_DIR_NAME = ROOT_DIR;

      URI outputURI = getCurOutputDir().toURI();

      // MemoryProfiler.dumpMemory ("Baseline");
      Thread.sleep(SLEEP_TIME_MS);

      // We cannot parse non modular AQL anymore without compiling it. So transform the non-modular
      // code into modular
      // code
      // and then parse.
      // CompileAQLParams params = new CompileAQLParams (AQL_FILE, null, DICTS_DIR_NAME);
      // params.setInEncoding (ENCODING);
      // ParseToCatalog catalogGenerator = new ParseToCatalog ();
      // catalogGenerator.parse (params);

      // Make a generic module
      CompileAQLParams params =
          new CompileAQLParams(AQL_FILE, outputURI.toString(), DICTS_DIR_NAME);
      params.setInEncoding(ENCODING);
      genericModuleDir = ModuleUtils.createGenericModule(params, new HashMap<String, String>(),
          new ArrayList<Exception>());

      CompileAQLParams modularParams = new CompileAQLParams();
      modularParams.setInEncoding(ENCODING);
      modularParams.setInputModules(new String[] {genericModuleDir.toURI().toString()});
      modularParams.setOutputURI(outputURI.toString());
      modularParams.setTokenizerConfig(getTokenizerConfig());

      MemoryProfiler.dumpMemory("Baseline");

      ParseToCatalog catalogGenerator = new ParseToCatalog();
      catalogGenerator.parse(modularParams);

      MemoryProfiler.dumpHeapSize("After parsing AQL");
      MemoryProfiler.dumpMemory("After parsing AQL");
      Thread.sleep(SLEEP_TIME_MS);

      // Compile the query, inlining all dictionary files.
      // Use whatever is the default planner to compile the query.
      // Use whatever is the default planner to compile the query.
      Planner planner = new Planner();
      planner.setPerformRSR(USE_RSR);
      TAM tam = new TAM(Constants.GENERIC_MODULE_NAME);
      planner.compileToTAM(catalogGenerator.getCatalog(), tam);
      catalogGenerator = null;
      planner = null;

      MemoryProfiler.dumpHeapSize("After compilation to AOG, but without compiling dictionaries");
      MemoryProfiler.dumpMemory("After compilation to AOG, but without compiling dictionaries");

      // Now compile the entire TAM beginning to end, including creating the generic module, parsing
      // AQL, compiling
      // dictionaries
      MemoryProfiler.dumpMemory("Baseline");
      CompileAQL.compile(modularParams);

      MemoryProfiler.dumpHeapSize(
          "After end-to-end compilation of generic module, including compiling dictionaries");
      MemoryProfiler.dumpMemory(
          "After end-to-end compilation of generic module, including compiling dictionaries");

      MemoryProfiler.dumpHeapSize("After compilation");
      MemoryProfiler.dumpMemory("After compilation");
      Thread.sleep(SLEEP_TIME_MS);

      // Can't separate the parsing of the AOG and the instantiation of the Operator graph. Measure
      // the memory after
      // instantiation only
      // TODO: In the following call replace the empty list of compiled dictionaries by dictionaries
      // from TAM
      // AOGRunner runner = AOGRunner.compileStr (aog, TestConstants.EMPTY_CD_LIST);
      // aog = null;
      //
      // MemoryProfiler.dumpHeapSize ("After parsing AOG");
      // MemoryProfiler.dumpMemory ("After parsing AOG");
      // Thread.sleep (SLEEP_TIME_MS);
      //
      // runner.setBufferOutput ();
      // runner.setNumThreads (1);

      OperatorGraph og = OperatorGraph.createOG(new String[] {Constants.GENERIC_MODULE_NAME},
          getCurOutputDir().toURI().toString(), null, getTokenizerConfig());

      MemoryProfiler.dumpHeapSize("After instantiating operators");
      MemoryProfiler.dumpMemory("After instantiating operators");

      runLotus(TestConstants.ENRON_1K_DUMP, og, false);
    } finally {
      if (genericModuleDir != null) {
        FileUtils.deleteDirectory(genericModuleDir.getParentFile()); // parent file is the
                                                                     // moduleUtilsTmp directory
        // created by ModuleUtils.createGenericModule()
      }
    }
  }

  /**
   * Test of memory consumption when loading the annotators from eDA 2.0 as a compiled operator
   * graph.
   */
  @Test
  public void edaLoadAOG() throws Exception {

    startTest();

    final int SLEEP_TIME_MS = 10000;
    String ENCODING = "UTF-8";

    System.err.print("*** Measuring memory usage loading AOG file.\n");
    Thread.sleep(SLEEP_TIME_MS);

    // Compile the entire extractor
    String ROOT_DIR = AQL_FILES_DIR + "/eDA";
    File AQL_FILE = new File(ROOT_DIR, "/ne-ediscovery-personorgphoneaddress.aql");

    String DICTS_DIR_NAME = ROOT_DIR;

    URI outputURI = getCurOutputDir().toURI();

    CompileAQLParams params = new CompileAQLParams(AQL_FILE, outputURI.toString(), DICTS_DIR_NAME);
    params.setInEncoding(ENCODING);
    // File AOG_FILE = new File(AOG_FILES_DIR, "eDA.aog");
    // File AOG_FILE = new File (AOG_FILES_DIR, "eDA_noRSR.aog");
    params.setPerformRSR(false);
    params.setPerformSRM(false);
    params.setTokenizerConfig(getTokenizerConfig());
    CompileAQL.compile(params);

    MemoryProfiler.dumpMemory("Baseline");
    Thread.sleep(SLEEP_TIME_MS);

    // Can't separate the parsing of the AOG and the instantiation of the Operator graph. Measure
    // the memory after
    // instantiation only
    // AOGRunner runner = AOGRunner.compileFile (AOG_FILE, ENCODING, tam.getAllDicts ());
    //
    // MemoryProfiler.dumpHeapSize ("After parsing AOG");
    // MemoryProfiler.dumpMemory ("After parsing AOG");
    // Thread.sleep (SLEEP_TIME_MS);
    //
    // runner.setBufferOutput ();
    // runner.setNumThreads (1);
    //
    // MemoryProfiler.dumpHeapSize ("After instantiating operators");
    // MemoryProfiler.dumpMemory ("After instantiating operators");

    OperatorGraph og = OperatorGraph.createOG(new String[] {Constants.GENERIC_MODULE_NAME},
        getCurOutputDir().toURI().toString(), null, getTokenizerConfig());

    MemoryProfiler.dumpHeapSize("After instantiating operators");
    MemoryProfiler.dumpMemory("After instantiating operators");

    runLotus(TestConstants.ENRON_1_DUMP, og, false);
  }

  /**
   * Test of memory consumption when loading the annotators from MashupHub 2.0 as a compiled
   * operator graph, using the new Java API.
   */
  @Test
  public void mashupHubMockup() throws Exception {

    // Pull the AOG from the Annotators project.
    File ANNOTATORS_PROJECT_ROOT = new File(TestConstants.TEST_WORKING_DIR, "../ExtractorLibrary");
    if (false == ANNOTATORS_PROJECT_ROOT.exists()) {
      // Don't run the test if we don't have the proper input files.
      Log.info("ExtractorLibrary project (%s) doesn't exist; exiting test.",
          ANNOTATORS_PROJECT_ROOT);
      return;
    }

    // File AOG_FILE = new File (ANNOTATORS_PROJECT_ROOT + "/build/aog/serverSideNEFinancial.aog");
    //
    // if (false == AOG_FILE.exists ()) {
    // // Don't run the test if we don't have the proper input file.
    // Log.info ("AOG file (%s) doesn't exist; exiting test.", AOG_FILE);
    // return;
    // }

    File TAM_DIR = new File(ANNOTATORS_PROJECT_ROOT + "/build/aog/serverSideNEFinancial");

    if (false == TAM_DIR.exists()) {
      // Don't run the test if we don't have the proper input file.
      Log.info("TAM directory (%s) doesn't exist; exiting test.", TAM_DIR);
      return;
    }

    final int SLEEP_TIME_MS = 1000;

    // String aog = FileUtils.fileToStr (AOG_FILE, "UTF-8");
    //
    // // Generate a TAM file containing this AOG
    // final String moduleName = "genericModule";
    // TAM tam = new TAM (moduleName);
    // tam.setAog (aog);
    //
    // // create dummy empty meta data object; this is to make loader happy
    // ModuleMetadataImpl dummyMD = ModuleMetadataImpl.createEmptyMDInstance ();
    //
    // // we compile dictionaries from old testcases using the built-in whitespace tokenizer
    // dummyMD.setTokenizerType (Constants.TKNZR_TYPE_BUILTIN);
    // tam.setMetadata (dummyMD);
    //
    // TAMSerializer.serialize (tam, getCurOutputDir ().toURI ());

    // create the operator graph containing this TAM file
    OperatorGraph og = OperatorGraph.createOG(new String[] {Constants.GENERIC_MODULE_NAME},
        TAM_DIR.toURI().toString(), null, null);

    MemoryProfiler.dumpMemory("Baseline");
    Thread.sleep(SLEEP_TIME_MS);

    // String AOG_FILE_NAME = AOG_FILE.getCanonicalPath ();
    og.getOutputTypeNames();

    MemoryProfiler.dumpHeapSize("After instantiating operators");
    MemoryProfiler.dumpMemory("After instantiating operators");

    // Push 1000 docs through the operator graph.
    int ndoc = 0;
    File docsFile = new File(TestConstants.ENRON_SMALL_ZIP);
    // Iterator<String> docItr = DocReader.makeDocTextItr (docsFile);

    DocReader docs = new DocReader(docsFile);
    Tuple doc = null;
    while (docs.hasNext()) {
      doc = docs.next();

      // Annotate the current document, generating every single output
      // type that the extractor produces.
      og.execute(doc, null, null);
      ndoc++;
    }

    docs.remove();
    docs = null;

    MemoryProfiler.dumpHeapSize(String.format("After %d docs", ndoc));
    MemoryProfiler.dumpMemory(String.format("After %d docs", ndoc));

    // Make sure that the pointer to the SystemT object is kept around, so
    // that we get an accurate count of memory usage in the above printf
    og.execute(doc, null, null);
  }

  /**
   * Test of Lotus compile overhead, with dictionary files and AOG input
   */
  @Test
  public void lotusCompileAOG() throws Exception {
    File aogTmp = null;
    try {
      startTest();

      System.err
          .print("*** Measuring memory usage" + " when compiling AOG with dictionary files.\n");

      String AQL_FILE = TestConstants.TESTDATA_DIR + "/aql/lotus/namedentity.aql";
      String ENCODING = "UTF-8";

      String DICTS_DIR_NAME = TestConstants.TESTDATA_DIR;

      // Start by generating an AOG file on disk.
      System.err.printf("Generating AOG file.\n");
      aogTmp = File.createTempFile("lotus", ".aog");
      {
        File tamDir = getCurOutputDir();
        CompileAQLParams params =
            new CompileAQLParams(new File(AQL_FILE), tamDir.toURI().toString(), DICTS_DIR_NAME);
        params.setInEncoding(ENCODING);
        params.setTokenizerConfig(getTokenizerConfig());

        CompileAQL.compile(params);

        // Load tam
        TAM tam = TAMSerializer.load(Constants.GENERIC_MODULE_NAME, params.getOutputURI());

        String aog = tam.getAog();
        OutputStreamWriter out =
            new OutputStreamWriter(new FileOutputStream(aogTmp), PACKED_AQL_ENCODING);
        out.append(aog);
        out.close();
      }
      System.gc();

      // The following is a simplified version of PushModeTests.lotusMockup().
      // I've removed the output code and the code that toggles outputs; also
      // added some memory-tracing code.

      MemoryProfiler.dumpMemory("Baseline");

      // Use the new AQLRunner API to parse/compile the AQL.
      File tamDir = getCurOutputDir();
      String modulePath = tamDir.toURI().toString();
      OperatorGraph og = OperatorGraph.createOG(new String[] {Constants.GENERIC_MODULE_NAME},
          modulePath, null, null);

      // Read the size of the heap, including temporary objects, after
      // parsing.
      MemoryProfiler.dumpHeapSize("After parsing AOG");
      MemoryProfiler.dumpMemory("After parsing AOG");

      MemoryProfiler.dumpHeapSize("After instantiating operators");
      MemoryProfiler.dumpMemory("After instantiating operators");

      runLotus(TestConstants.ENRON_1_DUMP, og, false);
    } finally {
      if (null != aogTmp)
        aogTmp.delete();
    }
  }

  /**
   * Test of Lotus compile overhead, with inline dictionaries and AOG input. NOTE FROM LAURA
   * 2/10/2012: This test was commented out since revision 130 of old CS svn. END NOTE FROM LAURA
   * 2/10/2012.
   */
  // @Test
  // public void lotusCompilePackedAOG() throws Exception {
  //
  // System.err.print("*** Measuring memory usage"
  // + " when compiling AOG with inline dicts.\n");
  //
  // String AQL_PACKED = TestConstants.TESTDATA_DIR
  // + "/aql/lotus/namedentity-packed.aql";
  //
  // if (false == (new File(AQL_PACKED)).exists()) {
  // createPackedAQLFile(AQL_PACKED);
  // }
  //
  // // Start by generating an AOG file on disk.
  // System.err.printf("Generating AOG file.\n");
  // File aogTmp = File.createTempFile("lotus", ".aog");
  // {
  // AQLParser parser = new AQLParser(new File(AQL_PACKED),
  // PACKED_AQL_ENCODING);
  // parser.parse();
  // Planner p = new Planner();
  // String aog = p.compileToString(parser.getCatalog());
  //
  // OutputStreamWriter out = new OutputStreamWriter(
  // new FileOutputStream(aogTmp), PACKED_AQL_ENCODING);
  // out.append(aog);
  // out.close();
  // out = null;
  // aog = null;
  // p = null;
  // }
  // System.gc();
  //
  // // The following is a simplified version of PushModeTests.lotusMockup().
  // // I've removed the output code and the code that toggles outputs; also
  // // added some memory-tracing code.
  //
  // MemoryProfiler.dumpMemory("Baseline");
  //
  // // Use the new AQLRunner API to parse/compile the AQL.
  // AOGRunner runner = AOGRunner.compileFile(aogTmp, PACKED_AQL_ENCODING,
  // "not a dictionary path");
  //
  // // Read the size of the heap, including temporary objects, after
  // // parsing.
  // MemoryProfiler.dumpHeapSize("After parsing AOG");
  // MemoryProfiler.dumpMemory("After parsing AOG");
  //
  // // runner.setWorkingDir(DICTS_DIR_NAME);
  // runner.setBufferOutput();
  // runner.setPushInput(1, null);
  //
  // MemoryProfiler.dumpHeapSize("After instantiating operators");
  // MemoryProfiler.dumpMemory("After instantiating operators");
  //
  // runLotus(TestConstants.ENRON_1_DUMP, runner);
  // }
  // private void createPackedAQLFile(String AQL_PACKED) throws Exception {
  // // Start by packing everything into a single file.
  // String AQLFILE_NAME = TestConstants.TESTDATA_DIR
  // + "/aql/lotus/namedentity.aql";
  // String DICTS_DIR_NAME = TestConstants.TESTDATA_DIR
  // + "/dictionaries/lotus";
  // String DICTS_PREFIX = "dictionaries/lotus";
  // String INPUT_ENCODING = "UTF-8";
  // String[] args = { INPUT_ENCODING, PACKED_AQL_ENCODING, DICTS_DIR_NAME,
  // DICTS_PREFIX, AQLFILE_NAME, AQL_PACKED };
  // AQLPackager.main(args);
  // }

  /**
   * Memory benchmark of sekar's named entity annotators, running over the enron 10k docs data set
   */
  @Test
  public void sekar1k() throws Exception {
    startTest();
    String filename = TestConstants.AQL_DIR + "/memoryTests/namedentity-sekar.aql";
    String docsfile = TestConstants.ENRON_1K_DUMP;

    memBench(filename, docsfile, 0.1);
  }

  /**
   * Memory benchmark of a version of the person/org annotator with big dictionaries. NOTE FROM
   * LAURA 2/10/2012: This test was disabled since revision 9 of old CS svn. END NOTE FROM LAURA
   * 2/10/2012.
   * 
   * @throws Exception
   */
  // @Test
  // public void largeDicts() throws Exception {
  // String filename = "../resources/NEResources/configs/builtin/aql/"
  // + "person-org-minimal-moredict.aql";
  // String dictsdir = "../resources/NEResources/data/builtin";
  // String docsfile = TestConstants.ENRON_1K_DUMP;
  //
  // memBench(filename, docsfile, dictsdir, 0.1);
  // }

  /**
   * Shorter version of sekar10K for testing start/stop conditions.
   */
  @Test
  public void sekarQuick() throws Exception {
    startTest();
    String filename = TestConstants.AQL_DIR + "/lotus/namedentity-sekar.aql";
    String docsfile = TestConstants.ENRON_1_DUMP;

    memBench(filename, docsfile, 0.1);
  }

  /**
   * Memory benchmark of yunyao's named entity annotators, running over the enron 10k docs data set.
   * NOTE FROM LAURA 2/10/2012: This test was commented out since revision 9 of old CS svn. END NOTE
   * FROM LAURA 2/10/2012.
   */
  // @Test
  public void yunyao10k() throws Exception {
    startTest();

    String filename = "testdata/aql/lotus/namedentity-yunyao.aql";
    // String docsfile = TestConstants.ENRON_10K_DUMP;
    String docsfile = TestConstants.ENRON_12_16_DUMP;

    memBench(filename, docsfile, 0.1);
  }

  /**
   * Memory benchmark of yunyao's named entity annotators, running over the enron 10k docs data set.
   * NOTE FROM LAURA 2/10/2012: This test was commented out since revision 9 of old CS svn. END NOTE
   * FROM LAURA 2/10/2012.
   */
  // @Test
  // public void yunyao1k() throws Exception {
  // String filename = "testdata/aql/lotus/namedentity-yunyao.aql";
  // // String docsfile = TestConstants.ENRON_10K_DUMP;
  // String docsfile = TestConstants.ENRON_1K_DUMP;
  //
  // memBench(filename, docsfile, 0.1);
  // }
  /**
   * Memory torture test; runs the cross-product of several AQL and document files. NOTE FROM LAURA
   * 2/10/2012: This test was commented out since revision 9 of old CS svn. END NOTE FROM LAURA
   * 2/10/2012.
   */
  // @Test
  public void manyTests() throws Exception {

    startTest();

    String[] AQLFILES = {"testdata/aql/lotus/namedentity-sekar-textlabel.aql",
        "testdata/aql/lotus/namedentity-yunyao.aql"};

    String[] DOCFILES = {TestConstants.ENRON_1_DUMP, TestConstants.ENRON_10K_DUMP,
        TestConstants.ENRON_4_8_DUMP, TestConstants.ENRON_12_16_DUMP,};

    // Sample rates for memory profiling; each entry corresponds to an entry
    // in DOCFILES
    double[] sampleRates = {1.0, 0.1, 0.1, 0.1};

    for (int i = 0; i < DOCFILES.length; i++) {
      String docfile = DOCFILES[i];
      double sampleRate = sampleRates[i];

      for (String aqlfile : AQLFILES) {
        memBench(aqlfile, docfile, sampleRate);
        System.err.print("----------------\n");
      }
    }

  }

  @Test
  public void longRegexMemoryTest() throws Exception {
    startTest();

    String AOG_FILE = TestConstants.TESTDATA_DIR + "/aog/longRegexTests/longRegex.aog";
    String DEL_FILE = TestConstants.TESTDATA_DIR + "/docs/longRegexTests/chemicals.del";

    // Create TAM from AOG and serialize it
    TAM tam = new TAM("longRegexMemoryTest");
    tam.setAog(FileUtils.fileToStr(new File(AOG_FILE), "UTF-8"));
    URI moduleURI = getCurOutputDir().toURI();
    TAMSerializer.serialize(tam, moduleURI);

    memBenchAOG("longRegexMemoryTest", moduleURI.toString(), DEL_FILE, 1.0);

    endTest();
  }

  /***
   * Helper function to benchmark OG creation and execution
   * 
   * @param moduleNames
   * @param modulePath
   * @throws TextAnalyticsException
   */
  private void memBenchTAM(String[] moduleNames, String modulePath, long expectedHeap)
      throws TextAnalyticsException {
    System.gc();
    MemoryProfiler.dumpHeapSize("Before creating OG");
    MemoryProfiler.dumpMemory("Before creating OG");

    OperatorGraph og = OperatorGraph.createOG(moduleNames, modulePath, null, null);

    MemoryProfiler.dumpMemory("After creating OG");

    DocReader docs = new DocReader(new File(TestConstants.TWITTER_MOVIE_1000));

    int count = 0;
    while (docs.hasNext()) {
      Tuple docTup = docs.next();

      // output the heap use for first 5 docs to verify that processing 1 doc greatly reduces heap
      // use because
      // the symbol table in Dictionaries.java can now be thrown away
      if (count < 5) {
        MemoryProfiler.dumpMemory("After running AOG on " + count + " documents");
      }

      og.execute(docTup, null, null);

      count++;
    }
    docs.remove();

    System.gc();

    MemoryProfiler.dumpMemory("After running AOG on 1000 documents");

    // commented out because other tests in a full test suite can impact this number
    // long mem = Runtime.getRuntime ().totalMemory () - Runtime.getRuntime ().freeMemory ();
    // Assert.assertTrue (String.format ("Too much memory (%d bytes) being used.", mem), mem <
    // expectedHeap);
    System.err.println(String.format("Expected memory use: %d bytes.", expectedHeap));

    // useless statement to keep the OG in memory
    og.toString();

    endTest();
  }

  /**
   * Scratchpad for profiling with an external memory profiler. Paste code in here, then remove
   * anything that calls System.gc().
   */
  public void scratchpad() throws Exception {

    System.err.print("*** Measuring memory usage" + " when compiling AQL with inline dicts.\n");

    String AQL_PACKED = TestConstants.TESTDATA_DIR + "/aql/lotus/namedentity-packed.aql";

    File moduleDir = File.createTempFile(System.currentTimeMillis() + "", "");
    moduleDir.delete();
    moduleDir.mkdirs();
    String modulePath = moduleDir.toURI().toString();

    // Prepare compile parameter
    CompileAQLParams compileParam = new CompileAQLParams(new File(AQL_PACKED), modulePath, null);
    compileParam.setInEncoding(PACKED_AQL_ENCODING);

    // Compile
    // Use whatever is the default planner to compile the query.

    CompileAQL.compile(compileParam);

    OperatorGraph og = OperatorGraph.createOG(new String[] {Constants.GENERIC_MODULE_NAME},
        modulePath, null, null);
    moduleDir.delete();
    og.toString();
  }

  private void memBench(String aqlfile, String docsfile, double sampleRate) throws Exception {
    memBench(aqlfile, docsfile, TestConstants.TESTDATA_DIR, sampleRate);
  }

  /**
   * Inner loop of most of the tests in this class.
   * 
   * @param aqlfile name of the file containing AQL to benchmark
   * @param docsfile DB2 dump file containing test documents
   * @param sampleRate portion of documents for which we trace memory allocations
   */
  private void memBench(String aqlfile, String docsfile, String dictsdir, double sampleRate)
      throws Exception {

    // Get a baseline memory usage so that we can compensate for other tests
    // leaving junk in memory.
    long baselineMemUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

    // Configure for maximum memory profiling, and disable outputs that
    // would interfere with measurements.
    MemoryProfiler mp = new MemoryProfiler(sampleRate);

    // util.setPrintMemory (true);
    // util.setMemProfiler (mp);
    setDisableOutput(true);
    setDataPath(dictsdir);

    // DocScan scan = new DBDumpFileScan(docsfile);
    runNonModularAQLTest(new File(docsfile), aqlfile);

    memBenchDelegate(docsfile, mp, baselineMemUsed, aqlfile);
  }

  private void memBenchAOG(String moduleName, String moduleURI, String docsfile, double sampleRate)
      throws Exception {
    memBenchAOG(moduleName, moduleURI, docsfile, TestConstants.TESTDATA_DIR, sampleRate);
  }

  private void memBenchAOG(String moduleName, String moduleURI, String docsfile, String dictsdir,
      double sampleRate) throws Exception {

    // Get a baseline memory usage so that we can compensate for other tests
    // leaving junk in memory.
    // long baselineMemUsed = Runtime.getRuntime ().totalMemory () - Runtime.getRuntime
    // ().freeMemory ();

    // Configure for maximum memory profiling, and disable outputs that
    // would interfere with measurements.
    // MemoryProfiler mp = new MemoryProfiler (sampleRate);

    // util.setPrintMemory (true);
    // util.setMemProfiler (mp);
    setDisableOutput(true);
    setDataPath(dictsdir);

    // DocScan scan = new DBDumpFileScan(docsfile);
    // runAOGFile(new File(aogFile));

    // run the module
    // util.runTAMFile (scan, moduleName, moduleURI);

    // memBenchDelegate(docsfile, mp, baselineMemUsed, aogFile);
  }

  private void memBenchDelegate(String docsfile, MemoryProfiler mp, long baselineMemUsed,
      String aqlAogFile) throws Exception {

    // Compute bytes of temporary objects per byte of document.

    int ndoc = 0;
    long totalDocBytes = 0;

    // scan = new DBDumpFileScan(docsfile);
    DocReader reader = new DocReader(new File(docsfile));
    // FieldGetter<Span> getText = scan.textGetter();
    FieldGetter<Text> getText = reader.getDocSchema().textAcc(Constants.DOCTEXT_COL);
    // MemoizationTable mt = new MemoizationTable(scan);
    while (reader.hasNext()) {
      // while (mt.haveMoreInput()) {

      // mt.resetCache();
      // Tuple tup = scan.getNext(mt).getElemAtIndex(0);
      Tuple tup = reader.next();
      Text docText = getText.getVal(tup);
      totalDocBytes += docText.getText().length();
      ndoc++;
    }

    double tempBytesPerDoc = mp.getBytesPerTrace();
    double textBytesPerDoc = (double) totalDocBytes / (double) ndoc;
    double byteRatio = tempBytesPerDoc / textBytesPerDoc;

    System.err.print("\n");
    System.err.printf(
        "Input file %s\n" + "     contains %d documents of average size %1.2f bytes.\n", docsfile,
        ndoc, textBytesPerDoc);
    System.err.printf("When running AQL file %s:\n", aqlAogFile);
    System.err.printf("  => Produced %1.1f bytes of temp objects per doc\n", tempBytesPerDoc);
    System.err.printf("  => %1.2f bytes of temp per byte of doc text.\n", byteRatio);
    System.err.printf("  => Averaged %1.1f bytes of live objects.\n", mp.getAvgLiveBytesBefore());
    System.err.printf("  => Ranged from %d to %d bytes of live objects.\n",
        mp.getMinLiveBytesBefore(), mp.getMaxLiveBytesBefore());

    // Sanity check: This test should use less than 50 MB
    long maxBytesUsed = mp.getMaxLiveBytesBefore() - baselineMemUsed;
    System.err.printf("%d bytes used (%d - %d)\n", maxBytesUsed, mp.getMaxLiveBytesBefore(),
        baselineMemUsed);
    assertTrue(maxBytesUsed < 200000000);

    reader.remove();
  }

}
