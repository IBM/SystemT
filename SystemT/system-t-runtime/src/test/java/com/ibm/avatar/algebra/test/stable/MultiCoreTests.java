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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.algebra.util.tokenize.Tokenizer;
import com.ibm.avatar.algebra.util.tokenize.TokenizerConfig;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.CompileAQL;
import com.ibm.avatar.api.CompileAQLParams;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.DocReader;
import com.ibm.avatar.api.ExternalTypeInfo;
import com.ibm.avatar.api.ExternalTypeInfoFactory;
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.logging.Log;

/**
 * Regression tests that run annotators in multiple threads.
 */
public class MultiCoreTests extends RuntimeTestHarness {

  private static final String AQL_FILES_DIR = TestConstants.AQL_DIR + "/MultiCoreTests";

  /** Various numbers of threads to use for test runs. */
  public static final int[] NTHREADS = {1, 2, 4, 8, 16};
  // public static final int[] NTHREADS = { 2 };

  /** How often (num docs) to report status */
  public static final int STATUS_INTERVAL = 100000;

  /** Should we subtract out the time it takes to scan the documents? */
  public static final boolean SUBTRACT_SCAN_TIME = true;

  /** Should we validate the results of the annotators? */
  public static boolean VALIDATE_RESULTS = true;

  public static void main(String[] args) {
    try {

      MultiCoreTests t = new MultiCoreTests();

      t.setUp();

      long startMS = System.currentTimeMillis();

      // t.personPhoneTest();
      // t.neRegexesTest();
      // t.namedEntityTest();
      // t.minusTest();
      // t.personOrgTest();
      // t.personOrgBench();
      // t.personOrgBench();
      // t.ediscoveryBench();
      // t.minusTest();

      long endMS = System.currentTimeMillis();

      t.tearDown();

      double elapsedSec = ((double) (endMS - startMS)) / 1000.0;

      System.err.printf("Test took %1.3f sec.\n", elapsedSec);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /** Dictionary path; used from inside genericTest() */
  private String dictsPathStr;

  /** Include path; used from inside genericTest() */
  private String includePathStr;

  @Before
  public void setUp() throws Exception {

    // Renice the current thread to avoid locking up the entire system.
    Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

    // By default, validate results. Individual tests can override this
    VALIDATE_RESULTS = true;

    // Put default parameter values into place.
    dictsPathStr = TestConstants.TESTDATA_DIR;
    includePathStr = null;
  }

  @After
  public void tearDown() throws Exception {
    // Restore the original setting so as not to screw up other tests.
    // Planner.restoreDefaultSentence();
  }

  /**
   * Test of parallel speedup with a simple person-phone annotator.
   */
  @Test
  public void personPhoneTest() throws Exception {
    startTest();

    final String AQLFILE_NAME = AQL_FILES_DIR + "/personphone.aql";

    final String DOCFILE_NAME = TestConstants.ENRON_10K_DUMP;

    genericTest(null, AQLFILE_NAME, DOCFILE_NAME);

    endTest();
  }

  /**
   * Test of parallel speedup with a bunch of regular expressions.
   */
  @Test
  public void neRegexesTest() throws Exception {
    startTest();

    final String AQLFILE_NAME = AQL_FILES_DIR + "/neRegexesTest.aql";

    // final String DOCFILE_NAME = TestConstants.ENRON_10K_DUMP;
    // final String DOCFILE_NAME = TestConstants.ENRON_1K_DUMP;
    final String DOCFILE_NAME = TestConstants.ENRON_100_DUMP;

    genericTest(null, AQLFILE_NAME, DOCFILE_NAME);

    endTest();
  }

  /**
   * Test of parallel speedup with minus.aql
   */
  @Test
  public void minusTest() throws Exception {
    startTest();

    final String AQLFILE_NAME = AQL_FILES_DIR + "/minus.aql";

    final String DOCFILE_NAME = TestConstants.ENRON_1K_DUMP;

    genericTest(null, AQLFILE_NAME, DOCFILE_NAME);

    endTest();
  }

  /**
   * Test of parallel speedup with the full named entity annotator.
   */
  @Test
  public void namedEntityTest() throws Exception {
    startTest();

    final String AQLFILE_NAME = AQL_FILES_DIR + "/namedentity.aql";

    // Use a smaller dump, since the named entity annotator takes so long.
    final String DOCFILE_NAME = TestConstants.ENRON_1K_DUMP;

    genericTest(null, AQLFILE_NAME, DOCFILE_NAME);

    endTest();
  }

  /**
   * Measures *speed* of the full named entity annotator from KISO. Not enabled as a test, since it
   * takes a while. NOTE FROM LAURA 10/1/2012: Disabled since revision 25 of old CS svn. END NOTE
   * FROM LAURA 10/1/2012
   */
  // @Test
  public void ediscoveryBench() throws Exception {

    final String AQLFILE_NAME = AQLEnronTests.EDISCOVERY_FULL_AQLFILE.getPath();
    final String DICT_PATH = AQLEnronTests.EDISCOVERY_DICT_PATH;
    final String INCLUDE_PATH = AQLEnronTests.EDISCOVERY_INCLUDE_PATH;

    // Use the larger dump so that we get better timings.
    final String DOCFILE_NAME = TestConstants.ENRON_37939_DUMP;
    // final String DOCFILE_NAME = TestConstants.ENRON_10K_DUMP;

    dictsPathStr = DICT_PATH;
    includePathStr = INCLUDE_PATH;

    // For laptop
    // int[] nthreads = { 1, 2, 3 };

    // For server
    int[] nthreads = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18};

    System.err.printf("Benchmarking %s\n", AQLFILE_NAME);

    genericTest(null, AQLFILE_NAME, DOCFILE_NAME, nthreads, false, false, null);
  }

  /**
   * Test for defect : Thread safety violation in HashJoin for a view involving a 3-way Equals join
   * between one view and two tables. Commented out because it fails.
   */
  @Test
  public void mdaWasTest() throws Exception {
    startTest();

    String MDA_DIR_WAS = AQL_FILES_DIR + "/mdaWas";

    this.includePathStr = MDA_DIR_WAS + ";" + MDA_DIR_WAS + "/includes;" + MDA_DIR_WAS + "/dicts";

    final String AQLFILE_NAME = MDA_DIR_WAS + "/extractors/extractor_was.aql";

    final String DOCFILE_NAME = TestConstants.DUMPS_DIR + "/logs/was_log.del";

    VALIDATE_RESULTS = false;

    genericTest(null, AQLFILE_NAME, DOCFILE_NAME);

    endTest();
  }

  /**
   * Test that CompileAQL works properly when a tokenizer instance is shared across multiple
   * CompileAQLParams instances.
   * 
   * @throws Exception
   */
  @Test
  public void threadSafetyCompileTest() throws Exception {
    startTest();

    genericCompileTest("module1", TestConstants.STANDARD_TOKENIZER, NTHREADS);

    endTest();
  }

  /**
   * Task class to run cnc contract.zip file in SystemT since there's no aqlfile
   */
  static final class CncMultiThreadBugTestTask implements Runnable {
    OperatorGraph og;
    Tuple doc;

    public CncMultiThreadBugTestTask(OperatorGraph og, Tuple doc) {
      this.og = og;
      this.doc = doc;
    }

    @Override
    public void run() {
      try {
        og.execute(doc, null, null);
      } catch (TextAnalyticsException e) {
        e.printStackTrace();
      }
    }

  }

  /**
   * There are lots of dependencies to run CnC model, so will not add them in the repository Before
   * run this test need to setup - Add these libs in classpath - commons-pool2 - hadoop-common -
   * action-api - Add esg resource in TestConstants.RESOURCES_DIR - Add all tam files in
   * PRECOMPILED_MODULES_DIR - Add dicts/tables in AQL_FILES_DIR + "/cncMultiThreadBugTest"
   */
  @Ignore("Need to configure before run this test")
  @Test
  public void cncMultiThreadBugTest() throws Exception {

    final String DOC_FILES_DIR = TestConstants.TEST_DOCS_DIR + "/MultiCoreTests";

    final String PRECOMPILED_MODULES_DIR =
        TestConstants.PRECOMPILED_MODULES_DIR + "/MultiCoreTests";

    startTest();

    String resourceDir = AQL_FILES_DIR + "/cncMultiThreadBugTest";
    ExternalTypeInfo externalTypeInfo = ExternalTypeInfoFactory.createInstance();

    externalTypeInfo.addDictionary("extract_column_headers.Dict_ColHeader_FullMatchClue",
        Paths.get(resourceDir, "Dict_ColHeader_FullMatchClue.dict").toUri().toString());
    externalTypeInfo.addDictionary("customDictionaries.PartyRoleCustom_Dict",
        Paths.get(resourceDir, "partyRoleCustomDict.dict").toUri().toString());
    externalTypeInfo.addDictionary("extract_row_headers.Dict_RowHeader_ContainMatchClue",
        Paths.get(resourceDir, "Dict_RowHeader_ContainMatchClue.dict").toUri().toString());
    externalTypeInfo.addDictionary("extract_column_headers.Dict_ColHeader_ContainMatchClue",
        Paths.get(resourceDir, "Dict_ColHeader_ContainMatchClue.dict").toUri().toString());
    externalTypeInfo.addDictionary("customDictionaries.PartyNameCustom_Dict",
        Paths.get(resourceDir, "partyNameCustomDict.dict").toUri().toString());
    externalTypeInfo.addDictionary("LocationCandidates.LocationCustomDict",
        Paths.get(resourceDir, "locationCustomDict.dict").toUri().toString());
    externalTypeInfo.addDictionary("extract_row_headers.Dict_RowHeader_FullMatchClue",
        Paths.get(resourceDir, "Dict_RowHeader_FullMatchClue.dict").toUri().toString());

    externalTypeInfo.addTable("customization.Map_ColHeader_NormalizeFullMatch",
        Paths.get(resourceDir, "Map_ColHeader_NormalizeFullMatch.csv").toUri().toString());
    externalTypeInfo.addTable("customTables.ConfigurationNatureWithoutPartyCustom_Table", Paths
        .get(resourceDir, "configurationNatureWithoutPartyCustom_Table.csv").toUri().toString());
    externalTypeInfo.addTable("typeScoring.CombinedModel_Config_Table",
        Paths.get(resourceDir, "config_type_combined_model.csv").toUri().toString());
    externalTypeInfo.addTable("customization.Map_RowHeader_NormalizeFullMatch",
        Paths.get(resourceDir, "Map_RowHeader_NormalizeFullMatch.csv").toUri().toString());
    externalTypeInfo.addTable("customTables.PartyNameAndRoleCustom_Table",
        Paths.get(resourceDir, "partyNameAndRoleCustomTable.csv").toUri().toString());
    externalTypeInfo.addTable("categoryScoring.CombinedModel_Config_Table",
        Paths.get(resourceDir, "config_category_combined_model.csv").toUri().toString());
    externalTypeInfo.addTable("annotatorConfig.AnnotatorConfig",
        Paths.get(resourceDir, "config_annotator.csv").toUri().toString());

    String[] moduleNames = new String[] {"combinedScoringOutputWithConfidence",
        "documentStructureScoring", "contractMetadataScoring", "tables_output"};

    OperatorGraph.validateOG(moduleNames, PRECOMPILED_MODULES_DIR, new TokenizerConfig.Standard());
    OperatorGraph og = OperatorGraph.createOG(moduleNames, PRECOMPILED_MODULES_DIR,
        externalTypeInfo, new TokenizerConfig.Standard());

    System.setProperty("TEXTANALYTICS_PARSER_HOME", TestConstants.RESOURCES_DIR);

    int nThread = 16;
    List<Future<?>> futures = new ArrayList<Future<?>>();
    ExecutorService executors = Executors.newFixedThreadPool(16);
    for (int i = 0; i < nThread; i++) {
      DocReader reader = new DocReader(Paths.get(DOC_FILES_DIR, "file0282.html").toFile());
      TupleList docs = new TupleList(reader.getDocSchema());
      while (reader.hasNext()) {
        Tuple doc = reader.next();
        docs.add(doc);
      }
      futures.add(executors.submit(new CncMultiThreadBugTestTask(og, docs.getElemAtIndex(0))));
    }

    for (Future<?> future : futures) {
      future.get();
    }

    endTest();

  }

  /*
   * ADD NEW TESTS HERE
   */

  /*
   * PRIVATE METHODS GO HERE
   */

  private void genericTest(OperatorGraph og, String aqlfileName, String docfileName)
      throws Exception {
    genericTest(og, aqlfileName, docfileName, NTHREADS, VALIDATE_RESULTS, SUBTRACT_SCAN_TIME, null);
  }

  private void genericTest(OperatorGraph og, String aqlfileName, String docfileName,
      TokenizerConfig tokenizerCfg) throws Exception {
    genericTest(og, aqlfileName, docfileName, NTHREADS, VALIDATE_RESULTS, SUBTRACT_SCAN_TIME,
        tokenizerCfg);
  }

  /**
   * Implementation of the tests in this class; compiles the indicated AQL file if OperatorGraph
   * instance is null and runs it over the documents with varying numbers of threads.
   * 
   * @param og OperatorGraph instance representing the AQL
   * @param aqlfileName name of main AQL file
   * @param docfileName name of dump file containing documents
   * @param nthreads array containing a list of the number of threads to use on each run
   * @param validateResults true to make sure that results produced are correct.
   * @param subtractScanTime subtract the time required for document scan from running times.
   * @param tokenizerCfg
   */
  private void genericTest(OperatorGraph og, String aqlfileName, String docfileName, int[] nthreads,
      boolean validateResults, boolean subtractScanTime, TokenizerConfig tokenizerCfg)
      throws Exception {

    // Read the documents into memory.
    // DocScan scan = DocScan.makeFileScan(new File(docfileName));
    DocReader reader = new DocReader(new File(docfileName));
    // MemoizationTable mt = new MemoizationTable(scan);
    // FieldGetter<Span> getText = scan.textGetter();
    FieldGetter<Text> getText = reader.getDocSchema().textAcc(Constants.DOCTEXT_COL);
    // TupleList docs = new TupleList(scan.getOutputSchema());
    TupleList docs = new TupleList(reader.getDocSchema());

    // Compute total document size and number of docs while we're at it.
    int totalDocSize = 0;
    // while (mt.haveMoreInput()) {
    while (reader.hasNext()) {
      // Tuple doc = scan.getNextDocTup(mt);
      Tuple doc = reader.next();
      docs.add(doc);
      totalDocSize += getText.getVal(doc).getText().length();
    }
    // mt.closeOutputs();

    // Now run the scan again and take timing measurements.
    // scan = new DBDumpFileScan(docfileName);
    // mt.reinit(scan);

    long startMs = System.currentTimeMillis();
    // while (mt.haveMoreInput()) {
    while (reader.hasNext()) {
      // scan.getNext(mt);
      reader.next();
    }
    // mt.closeOutputs();
    long endMs = System.currentTimeMillis();

    double scanSec = ((double) (endMs - startMs)) / 1000.0;

    System.err.printf("Document set contains %d documents and takes %1.2f sec to scan.\n",
        docs.size(), scanSec);

    // Generate the "gold standard" result set.
    // Each entry in the ArrayList is a map from output name to result
    // tuples.
    ArrayList<Map<String, TupleList>> goldStandard = null;
    if (validateResults) {
      goldStandard = computeGoldStandard(aqlfileName, docfileName, docs, tokenizerCfg);
    }

    // Now we can run the test.
    for (int i = 0; i < nthreads.length; i++) {

      int nthread = nthreads[i];

      Log.info("Running with %d threads", nthread);

      double elapsedSec = runTest(og, aqlfileName, docs, goldStandard, nthread);

      if (subtractScanTime) {
        // Subtract out scanning overhead so that we get a better
        // picture of scaling behavior.
        elapsedSec -= scanSec;
      }

      double msPerDoc = elapsedSec * 1e3 / docs.size();
      double kbPerSec = totalDocSize / 1024 / elapsedSec;

      System.err.printf("%3d threads --> %5.2f sec (%4.2f ms/doc; %4.0f kb/sec)\n", nthread,
          elapsedSec, msPerDoc, kbPerSec);
    }

    // Close the document reader
    reader.remove();
  }

  private ArrayList<Map<String, TupleList>> computeGoldStandard(String aqlfileName,
      String docfileName, TupleList docs, TokenizerConfig tokenizerCfg)
      throws Exception, ParseException {

    System.err.printf("Generating gold standard results...\n");

    ArrayList<Map<String, TupleList>> goldStandard = new ArrayList<Map<String, TupleList>>();

    compileAQL(new File(aqlfileName), includePathStr + ";" + dictsPathStr);

    String moduleURI = getCurOutputDir().toURI().toString();
    OperatorGraph og = OperatorGraph.createOG(new String[] {Constants.GENERIC_MODULE_NAME},
        moduleURI, null, tokenizerCfg);

    // Turn off logging.
    Log.disableAllMsgTypes();

    TLIter itr = docs.iterator();
    while (itr.hasNext()) {
      Tuple docTup = itr.next();

      // Collect the results for the current document and add them to the
      // "gold standard"
      Map<String, TupleList> curResults = og.execute(docTup, null, null);
      goldStandard.add(curResults);
    }
    itr.done();

    System.err.printf("Done generating gold standard results.\n");

    // Re-enable logging.
    Log.restoreDefaultMsgTypes();

    return goldStandard;
  }

  /**
   * Workhorse method
   * 
   * @param og OperatorGraph instance representing the AQL
   * @param aqlfileName name of the AQL file to compile and run
   * @param docTups list of the tuples to feed through the AQL
   * @param goldStandard "gold standard" results to compare against, indexed by document number,
   *        then output name
   * @param nthread number of threads to use when annotating
   * @return elapsed seconds
   * @throws Exception
   */
  private double runTest(OperatorGraph og, String aqlfileName, TupleList docTups,
      ArrayList<Map<String, TupleList>> goldStandard, int nthread) throws Exception {
    if (og == null && aqlfileName != null) {
      // Parse and compile the file.
      compileAQL(new File(aqlfileName), includePathStr + ";" + dictsPathStr);
      og = instantiateOperatorGraph(Constants.GENERIC_MODULE_NAME);
    }
    // runner.setDisableUnusedViews(false);
    Log.disableAllMsgTypes();

    mutableInt docCount = new mutableInt();
    mutableInt errorCount = new mutableInt();

    // Now we can spawn off the worker threads.
    long startMs = System.currentTimeMillis();

    TLIter docItr = docTups.iterator();

    Thread[] threads = new Thread[nthread];
    for (int i = 0; i < nthread; i++) {

      threads[i] = new Thread(new worker(i, docItr, og, docCount, errorCount, goldStandard));
      threads[i].start();
    }

    // Wait for the worker threads to complete.
    for (int i = 0; i < nthread; i++) {
      threads[i].join();
    }

    long endMs = System.currentTimeMillis();
    long elapsedMs = endMs - startMs;

    System.err.printf("Processed %d documents total.\n", docCount.count);

    // Make sure none of the background threads failed.
    assertEquals(0, errorCount.count);

    // Re-enable logging.
    Log.restoreDefaultMsgTypes();

    return (double) elapsedMs / 1000.0;
  }

  // Package up the documents counter as an object so that multiple
  // threads can update it.
  class mutableInt {
    int count = 0;
  }

  /**
   * Thread entry point for the threads in {@link #runTest(String, String, ArrayList, int)}
   */
  class worker implements Runnable {

    int threadIx;
    // DBDumpFileScan scan;

    /** MemoizationTable for use with document scan. */
    OperatorGraph runner;
    mutableInt docCount;
    mutableInt errorCount;
    ArrayList<Map<String, TupleList>> goldStandard;

    /** Shared iterator over the documents. */
    TLIter docItr;

    private worker(int threadIx, TLIter docItr, OperatorGraph og, mutableInt docCount,
        mutableInt errorCount, ArrayList<Map<String, TupleList>> goldStandard2) {
      this.threadIx = threadIx;
      this.docItr = docItr;
      this.runner = og;
      this.docCount = docCount;
      this.errorCount = errorCount;
      this.goldStandard = goldStandard2;

    }

    // Main entry point for a worker thread.
    @Override
    public void run() {
      try {

        ArrayList<String> outputNames = runner.getOutputTypeNames();
        while (true) {
          Tuple curDoc;
          int docNum;

          // Need to serialize access to the scan and the document
          // counter.
          synchronized (docItr) {
            // Check whether we've processed all the documents.
            if (false == docItr.hasNext()) {
              return;
            }
            curDoc = docItr.next();
            docNum = (++docCount.count);

            if (0 == docNum % STATUS_INTERVAL) {
              System.err.printf("Processed %d documents.\n", docNum);
            }
          }
          // End critical section.

          // Feed the document through the plan.
          Map<String, TupleList> annotations = runner.execute(curDoc, null, null);

          if (VALIDATE_RESULTS) {
            // Collect the results for the current document and
            // compare them to the "gold standard"
            for (String outputName : outputNames) {
              TupleList results = annotations.get(outputName);

              if (null != goldStandard) {
                // Document numbers are 1-based; the gold
                // standard's document numbers are 0-based.
                TupleList expectedResults = goldStandard.get(docNum - 1).get(outputName);

                assertEquals(expectedResults, results);
              }
            }
          }
        }

      } catch (Exception e) {
        synchronized (errorCount) {
          errorCount.count++;
          // System.err.printf("Incremented error count to %d.\n",
          // errorCount.count);
        }
        throw new RuntimeException(e);
      } catch (AssertionError e) {
        synchronized (errorCount) {
          errorCount.count++;
          // System.err.printf("Incremented error count to %d.\n",
          // errorCount.count);
        }
        throw new RuntimeException(e);
      }
    }

  }

  /**
   * Implementation of the compile tests in this class; creates an instance of a tokenizer and
   * shares it across multiple CompileAQLParams instances. Then compiles in separate threads to
   * ensure the Compiler doesn't run into thread safety issues from sharing a single instance of the
   * tokenizer (since the some tokenizer types are not thread safe, we have the Compiler synchronize
   * on the tokenizer object when compiling dictionaries.). It does so repeatedly, for an increasing
   * number of threads. This method assumes the module AQL source code is in:
   * 
   * <pre>
   * testdata / aql / tesClassname / testMethodName
   * </pre>
   * 
   * and compiles this module once and uses it as gold standard to compare modules compiled with the
   * shared tokenizer.
   * 
   * @param moduleName name of module to compile (assumed to be in this test's AQL directory)
   * @param tokenizerCfg tokenizer configuration to use when instantiating the tokenizer
   * @param nthreads array containing a list of the number of threads to use on each run
   */
  private void genericCompileTest(String moduleName, TokenizerConfig tokenizerCfg, int[] nthreads)
      throws Exception {

    // Compile the module once in the test's current output dir
    setTokenizerConfig(tokenizerCfg);
    compileModule(moduleName);
    File goldStandardTAMFile = new File(getCurOutputDir(), moduleName + ".tam");

    // Now we can run the test.
    for (int i = 0; i < nthreads.length; i++) {

      int nthread = nthreads[i];

      Log.info("Running with %d threads", nthread);

      double elapsedSec =
          genericCompileTest(moduleName, tokenizerCfg, goldStandardTAMFile, nthread);

      System.err.printf("%3d threads --> %5.2f sec\n", nthread, elapsedSec);
    }
  }

  /**
   * Workhorse method for compiling in parallel with a single tokenizer instance shared across the
   * given number of threads. Called from
   * {@link MultiCoreTests#genericCompileTest(String, TokenizerConfig, int[])}
   * 
   * @param moduleName name of source module to compile
   * @param tokenizerCfg tokenizer configuration to use
   * @param goldStandardTAMFile tam file to compare the output of each thread against
   * @param number of threads that share the tokenizer instance and compile in parallel
   * @throws Exception
   */
  private double genericCompileTest(String moduleName, TokenizerConfig tokenizerCfg,
      File goldStandardTAMFile, int nthread) throws Exception {
    Log.disableAllMsgTypes();
    mutableInt errorCount = new mutableInt();

    // Module to compile
    String moduleURI = new File(getCurTestDir(), moduleName).toURI().toString();

    // Where to put the modules compiled by the worker threads
    File outputDir = getCurOutputDir();

    // Make a tokenizer to share among threads
    Tokenizer tokenizer = tokenizerCfg.makeTokenizer();

    // Now we can spawn off the worker threads.
    long startMs = System.currentTimeMillis();

    Thread[] threads = new Thread[nthread];
    for (int i = 0; i < nthread; i++) {

      File threadOutputDir = new File(outputDir, "thread-" + i);

      threads[i] =
          new Thread(new compilerWorker(i, moduleURI, tokenizer, threadOutputDir, errorCount));
      threads[i].start();
    }

    // Wait for the worker threads to complete.
    for (int i = 0; i < nthread; i++) {
      threads[i].join();
    }

    long endMs = System.currentTimeMillis();
    long elapsedMs = endMs - startMs;

    System.err.printf("Compiled %d times total.\n", nthread);

    // Make sure none of the background threads failed.
    assertEquals(0, errorCount.count);

    // Compare tam files generated by the threads with the gold
    for (int i = 0; i < nthread; i++) {
      // Actual Tam file
      File threadOutputDir = new File(outputDir, "thread-" + i);
      File actualTAMFile = new File(threadOutputDir, moduleName + ".tam");

      // Check it exists
      if (!actualTAMFile.exists())
        throw new Exception(String.format("Tam file %s does not exist.", actualTAMFile.getPath()));

      // Compare against gold
      this.compareTAMFiles(goldStandardTAMFile, actualTAMFile);

    }

    // Re-enable logging.
    Log.restoreDefaultMsgTypes();

    return (double) elapsedMs / 1000.0;
  }

  /**
   * Thread entry point for the threads in
   * {@link MultiCoreTests#genericCompileTest(String, TokenizerConfig, int[])}
   */
  class compilerWorker implements Runnable {

    int threadIx;
    String moduleURI;
    Tokenizer tokenizer;
    mutableInt errorCount;
    File goldStandardTamFile;
    File outputDir;

    /**
     * @param threadIx name of the thread
     * @param moduleURI module to compile
     * @param tokenizer tokenizer to use for compilation (shared with other threads)
     * @param outputDir output directory where to put the compiled module so we can compare it later
     * @param errorCount number of errors encountered
     */
    private compilerWorker(int threadIx, String moduleURI, Tokenizer tokenizer, File outputDir,
        mutableInt errorCount) {
      this.threadIx = threadIx;
      this.moduleURI = moduleURI;
      this.errorCount = errorCount;
      this.tokenizer = tokenizer;
      this.outputDir = outputDir;
    }

    // Main entry point for a worker thread.
    @Override
    public void run() {
      try {

        // Prepare the compilation params
        FileUtils.ensure_dir(outputDir);
        String outputURI = outputDir.toURI().toString();

        CompileAQLParams params = new CompileAQLParams(new String[] {moduleURI}, outputURI, null);

        // Share the tokenizer
        params.setTokenizer(tokenizer);

        // Compile
        CompileAQL.compile(params);
      } catch (Exception e) {
        synchronized (errorCount) {
          errorCount.count++;
          // System.err.printf("Incremented error count to %d.\n",
          // errorCount.count);
        }
        throw new RuntimeException(e);
      } catch (AssertionError e) {
        synchronized (errorCount) {
          errorCount.count++;
          // System.err.printf("Incremented error count to %d.\n",
          // errorCount.count);
        }
        throw new RuntimeException(e);
      }
    }

  }

}
