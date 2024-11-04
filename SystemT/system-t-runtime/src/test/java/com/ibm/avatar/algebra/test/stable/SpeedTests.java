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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;

import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.ObjectID;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.algebra.util.lang.LangCode;
import com.ibm.avatar.algebra.util.test.MemoryProfiler;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.algebra.util.tokenize.OffsetsList;
import com.ibm.avatar.algebra.util.tokenize.StandardTokenizer;
import com.ibm.avatar.algebra.util.tokenize.TokenizerConfig;
import com.ibm.avatar.api.AQLProfiler;
import com.ibm.avatar.api.AQLProfiler.RuntimeRecord;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.DocReader;
import com.ibm.avatar.api.EmailChunker;
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.api.OperatorGraphImpl;
import com.ibm.avatar.api.OperatorGraphRunner;
import com.ibm.avatar.aql.planner.Planner;
import com.ibm.avatar.aql.planner.RandomMergePlanner;
import com.ibm.avatar.aql.tam.ModuleMetadataImpl;
import com.ibm.avatar.aql.tam.TAM;
import com.ibm.avatar.aql.tam.TAMSerializer;
import com.ibm.avatar.logging.Log;

/**
 * Regression tests for ensuring that annotation performance has not decreased dramatically. These
 * tests mostly check that there are no documents in the Enron dataset that take more than a certain
 * amount of time with a given annotator.
 * 
 */
public class SpeedTests extends RuntimeTestHarness {

  public static final String AQL_FILES_DIR = TestConstants.AQL_DIR + "/SpeedTests";

  public static final String DICTS_DIR = TestConstants.TESTDATA_DIR + "/dictionaries/speedTests";

  private static int FEEDBACK_INTERVAL = 1000;

  public static void main(String[] args) {
    try {

      SpeedTests t = new SpeedTests();

      t.setUp();
      ThreadMXBean bean = ManagementFactory.getThreadMXBean();
      long id = java.lang.Thread.currentThread().getId();
      long startNS = bean.getThreadCpuTime(id);

      t.profileACE();

      long endNS = bean.getThreadCpuTime(id);

      t.tearDown();

      double elapsedSec = (endNS - startNS) / 1e9;

      System.err.printf("Test took %1.3f sec.\n", elapsedSec);

      MemoryProfiler.dumpHeapSize("After test");

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  // private Planner planner;

  @Before
  public void setUp() throws Exception {

    // Renice the current thread to avoid locking up the entire system.
    Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

    setDataPath(TestConstants.TESTDATA_DIR);

    // planner = new Planner ();
  }

  @After
  public void tearDown() {
    // Restore original value to avoid screwing with other tests.
    // Planner.restoreDefaultSentence();
  }

  /** Data structure for holding the results of a genericTimeTest run. */
  public static class TimeTestResults {
    /*
     * Statistics on all outputs
     */
    public long totNs = 0;
    public long maxNs = 0;
    public int ndoc = 0;
    public ObjectID worstDocId = null;

    /*
     * Data structures for "separate outputs" mode
     */
    public ArrayList<String> allOutputNames;
    public long[] totalElapsedTimes;
    public long[] maxTimes;
    public ObjectID[] worstDocIDs;

    private final boolean separateOutputsMode;

    /** Constructor for "all outputs together" mode */
    public TimeTestResults() {
      this.separateOutputsMode = false;
    }

    /** Constructor for "separate outputs" mode */
    public TimeTestResults(ArrayList<String> allOutputNames) {
      this.allOutputNames = allOutputNames;
      totalElapsedTimes = new long[allOutputNames.size()];
      maxTimes = new long[allOutputNames.size()];
      worstDocIDs = new ObjectID[allOutputNames.size()];
      Arrays.fill(totalElapsedTimes, 0L);
      Arrays.fill(maxTimes, 0L);

      this.separateOutputsMode = true;
    }

    public void printDoneMsg() {
      if (separateOutputsMode) {
        System.err.printf("Finished processing %d docs. Timings are as follows:\n", ndoc);
        for (int i = 0; i < allOutputNames.size(); i++) {
          System.err.printf("%30s --> %4.2f sec (max %4.2f sec for doc '%s')\n",
              allOutputNames.get(i), totalElapsedTimes[i] / 1e9, maxTimes[i] / 1e9, worstDocIDs[i]);
        }
      } else {
        System.err.printf(
            "Done; processed %d documents in %1.2f sec;" + " max is %1.2f sec for doc '%s'.\n",
            ndoc, totNs / 1e9, maxNs / 1e9, worstDocId);
      }
    }

    public void printInFlightMsg() {
      if (separateOutputsMode) {
        System.err.printf("Processed %d documents in %1.2f sec\n", ndoc, totNs / 1e9);
      } else {
        System.err.printf(
            "Processed %d documents in %1.2f sec;" + " max is %1.2f sec for doc '%s'\n", ndoc,
            totNs / 1e9, maxNs / 1e9, worstDocId);
      }
    }
  }

  /**
   * General running time regression test. Verifies that no document takes longer than the indicated
   * threshold.
   * 
   * @param aqlfileName name of the AQL file to run
   * @param docfileName file containing input documents to push through the AQL
   * @param msecMax max msec allowable to process any document in the set
   * @param separateOutputs true to try enabling every output of the graph separately
   * @return an object containing various timing statistics
   */
  private TimeTestResults genericTimeTest(String aqlfileName, String docfileName, int msecMax,
      boolean separateOutputs) throws Exception {

    // Directory containing auxiliary dictionary files.
    // final String DICTSDIR = TestConstants.TESTDATA_DIR;

    // ////////////////////////////////////////////////////////////////////
    // SETUP

    // The file containing documents is in DB2 comma-delimited dump format.
    // Manually instantiate a scan operator to get at the text fields of the
    // document.
    // DocScan docscan = new DBDumpFileScan (docfileName);
    DocReader reader = new DocReader(new File(docfileName));

    // Parse and compile the AQL file, producing an operator graph spec in
    // AOG.
    String dataPathStr = getDataPath();
    compileAQL(new File(aqlfileName), dataPathStr);

    OperatorGraph og = instantiateOperatorGraph(Constants.GENERIC_MODULE_NAME);

    // ////////////////////////////////////////////////////////////////////
    // COUNTER SETUP
    TimeTestResults results;
    if (separateOutputs) {
      results = new TimeTestResults(og.getOutputTypeNames());
    } else {
      results = new TimeTestResults();
    }

    // ////////////////////////////////////////////////////////////////////
    // EXECUTION

    // MemoizationTable mt = new MemoizationTable (docscan);
    ThreadMXBean bean = ManagementFactory.getThreadMXBean();
    long id = java.lang.Thread.currentThread().getId();
    TupleSchema operatorGraphDocSchema = og.getDocumentSchema();

    // while (mt.haveMoreInput ()) {
    while (reader.hasNext()) {

      // Unpack the document text from the scan's output.
      // mt.resetCache ();
      // Tuple doc = docscan.getNextDocTup (mt);
      Tuple doc = reader.next();

      if (doc.size() != operatorGraphDocSchema.size()) {
        throw new RuntimeException("Input document does not meet required doc schema."
            + operatorGraphDocSchema.toString());
      }

      ObjectID docoid = doc.getOid();

      // Measure how long the document takes. Discretization error should
      // average out in the long term.
      if (separateOutputs) {
        for (int i = 0; i < results.allOutputNames.size(); i++) {
          String outputName = results.allOutputNames.get(i);

          // Only enable the selected output.
          // runner.setAllOutputsEnabled (false);
          // runner.setOutputEnabled (outputName, true, 0);
          String[] outputTypes = new String[] {outputName};

          long startNS = bean.getThreadCpuTime(id);
          // runner.pushDoc (doc);
          og.execute(doc, outputTypes, null);
          long endNS = bean.getThreadCpuTime(id);

          long elapsedNs = endNS - startNS;
          if (elapsedNs / 1000000 > msecMax) {
            throw new Exception(String.format(
                "Document '%s' took %1.2f sec" + " to process for output '%s'"
                    + "; max is %1.2f sec",
                docoid.getStringValue(), elapsedNs / 1e9, outputName, msecMax / 1000.0));
          }

          results.totNs += elapsedNs;
          results.totalElapsedTimes[i] += elapsedNs;
          if (elapsedNs > results.maxTimes[i]) {
            results.maxTimes[i] = elapsedNs;
            results.worstDocIDs[i] = docoid;
          }
        }
      } else {
        long startNS = bean.getThreadCpuTime(id);
        // runner.pushDoc (doc);
        og.execute(doc, null, null);
        long endNS = bean.getThreadCpuTime(id);

        long elapsedNs = endNS - startNS;
        if (elapsedNs / 1000000 > msecMax) {
          throw new Exception(
              String.format("Document '%s' took %1.2f sec" + " to process; max is %1.2f sec",
                  docoid.getStringValue(), elapsedNs / 1e9, msecMax / 1000.0));
        }

        results.totNs += elapsedNs;
        if (elapsedNs > results.maxNs) {
          results.maxNs = elapsedNs;
          results.worstDocId = docoid;
        }
      }

      results.ndoc++;

      if (0 == results.ndoc % FEEDBACK_INTERVAL) {
        results.printInFlightMsg();

      }
    }

    // Close the document reader
    reader.remove();

    // Tell the graph runner to clean up.
    // runner.endOfPushInput ();

    results.printDoneMsg();

    return results;
  }

  /**
   * Make sure that no document in the Enron dataset takes more than 2 seconds to run through the
   * named entity annotators. Use namedEntityProfileTest() below to debug any problems that this
   * test case turns up.
   * <P>
   * <b>NOTE:</b> The extractor used in this test has a known performance problem: There are two
   * expensive regexes in the views that identify German street addresses.
   */
  @Test
  public void namedEntityTest() throws Exception {
    startTest();

    final int MSEC_MAX = 5 * 1000;
    final String AQLFILE_NAME = TestConstants.AQL_DIR
        // + "/lotus/namedentity-new.aql";
        + "/lotus/namedentity-textcol.aql";
    // + "/scratch.aql";

    // final String DOCFILE_NAME = TestConstants.ENRON_1K_DUMP;
    final String DOCFILE_NAME = TestConstants.ENRON_10K_DUMP;
    // final String DOCFILE_NAME = TestConstants.ENRON_37939_DUMP;
    // final String DOCFILE_NAME = TestConstants.ENRON_12_16_DUMP;
    // final String DOCFILE_NAME = TestConstants.TEST_DOCS_DIR + "/tmp.del";

    genericTimeTest(AQLFILE_NAME, DOCFILE_NAME, MSEC_MAX, false);

    endTest();
  }

  /**
   * Profiling version of namedEntityTest()
   */
  @Test
  public void namedEntityProfileTest() throws Exception {
    startTest();
    if (false == ANNOTATORS_PROJECT.exists()) {
      // SPECIAL CASE: User hasn't checked out a copy of Annotators.
      System.err.printf("No Annotators project.  Exiting.\n");
      return;
      // END SPECIAL CASE
    }

    final String AQLFILE_NAME = TestConstants.AQL_DIR
        // + "/lotus/namedentity-new.aql";
        + "/lotus/namedentity-textcol.aql";
    final String DICT_PATH = TestConstants.TESTDATA_DIR;

    // final String DOCFILE_NAME = TestConstants.ENRON_1K_DUMP;
    final String DOCFILE_NAME = TestConstants.ENRON_10K_DUMP;
    // final String DOCFILE_NAME = TestConstants.ENRON_37939_DUMP;
    // final String DOCFILE_NAME = TestConstants.ENRON_SAMPLE_ZIP;
    // final String DOCFILE_NAME = TestConstants.ENRON_SMALL_ZIP;

    genericProfile(AQLFILE_NAME, DOCFILE_NAME, DICT_PATH, null, null);

    endTest();
  }

  /**
   * Profile a very simple annotator.
   */
  @Test
  public void profilePersonPhone() throws Exception {
    startTest();

    final String AQLFILE_NAME = AQL_FILES_DIR + "/personphone.aql";

    final String DOCFILE_NAME = TestConstants.ENRON_1K_DUMP;
    // final String DOCFILE_NAME = TestConstants.ENRON_10K_DUMP;
    // final String DOCFILE_NAME = TestConstants.ENRON_37939_DUMP;
    // final String DOCFILE_NAME = TestConstants.ENRON_12_16_DUMP;

    // Directory containing auxiliary dictionary files.
    final String DICTSDIR = TestConstants.TESTDATA_DIR;

    genericProfile(AQLFILE_NAME, DOCFILE_NAME, DICTSDIR);

    endTest();
  }

  /**
   * Profile an AQL file containing accelerated versions of several "hotspots" in personOrg.aql.
   */
  @Test
  public void profileHotspots() throws Exception {
    startTest();

    final String AQLFILE_NAME = AQL_FILES_DIR + "/person-org-hs-after.aql";

    final String DICTSDIR = TestConstants.TESTDATA_DIR;

    final String DOCFILE_NAME = TestConstants.ENRON_1K_DUMP;
    // final String DOCFILE_NAME = TestConstants.ENRON_SAMPLE_ZIP;

    genericProfile(AQLFILE_NAME, DOCFILE_NAME, DICTSDIR);

    endTest();
  }

  /**
   * Profile the rules in the named entity annotator.
   */
  @Test
  public void profileNamedEntity() throws Exception {
    startTest();

    final String AQLFILE_NAME = TestConstants.AQL_DIR
        // + "/lotus/namedentity-new.aql";
        + "/lotus/namedentity-textcol.aql";
    // + "/scratch.aql";

    final String DOCFILE_NAME = TestConstants.ENRON_1K_DUMP;
    // final String DOCFILE_NAME = TestConstants.ENRON_SAMPLE_ZIP;

    // Directory containing auxiliary dictionary files.
    final String DICTSDIR = TestConstants.TESTDATA_DIR;

    genericProfile(AQLFILE_NAME, DOCFILE_NAME, DICTSDIR);

    endTest();
  }

  /**
   * Profile a version of person-org-minimal.aql with just the "problem" statements.
   */
  @Test
  public void profilePersonStmts() throws Exception {
    startTest();
    // Planner.forceLWSentence();

    final String AQLFILE_NAME = AQL_FILES_DIR + "/person-org-hotspots.aql";

    final String DOCFILE_NAME = TestConstants.ENRON_1K_DUMP;
    // final String DOCFILE_NAME = TestConstants.ENRON_10K_DUMP;
    // final String DOCFILE_NAME = TestConstants.ENRON_37939_DUMP;
    // final String DOCFILE_NAME = TestConstants.ENRON_12_16_DUMP;

    // Directory containing auxiliary dictionary files.
    final String DICTSDIR = TestConstants.TESTDATA_DIR;

    genericProfile(AQLFILE_NAME, DOCFILE_NAME, DICTSDIR);

    endTest();
  }

  /**
   * Profile improved versions of the statements in profilePersonStmts().
   */
  @Test
  public void profileFasterStmts() throws Exception {
    startTest();

    final String AQLFILE_NAME = AQL_FILES_DIR + "/fixed-hotspots.aql";

    final String DOCFILE_NAME = TestConstants.ENRON_1K_DUMP;
    // final String DOCFILE_NAME = TestConstants.ENRON_10K_DUMP;
    // final String DOCFILE_NAME = TestConstants.ENRON_37939_DUMP;
    // final String DOCFILE_NAME = TestConstants.ENRON_12_16_DUMP;

    // Directory containing auxiliary dictionary files.
    final String DICTSDIR = TestConstants.TESTDATA_DIR;

    genericProfile(AQLFILE_NAME, DOCFILE_NAME, DICTSDIR);

    endTest();
  }

  /**
   * Profile the version of personOrg.aql with RSR enabled.
   */
  @Test
  public void profilePersonRSR() throws Exception {
    startTest();

    final String AQLFILE_NAME = AQL_FILES_DIR + "/personOrgFast.aql";

    final String DOCFILE_NAME = TestConstants.ENRON_1K_DUMP;
    // final String DOCFILE_NAME = TestConstants.ENRON_SAMPLE_ZIP;

    // Directory containing auxiliary dictionary files.
    final String DICTSDIR = TestConstants.TESTDATA_DIR;

    genericProfile(AQLFILE_NAME, DOCFILE_NAME, DICTSDIR);

    if (TupleList.COLLECT_STATS) {
      TupleList.dumpListStats();
    }

    endTest();
  }

  /**
   * Profile the "email structure" annotator that Thomas sent.
   */
  @Test
  public void profileEmailStruct() throws Exception {
    startTest();

    final String AQLFILE_NAME = AQL_FILES_DIR + "/emailstructure/emailstructure.aql";

    final String INCLUDE_PATH = AQL_FILES_DIR;
    final String DICT_PATH = DICTS_DIR + "/emailstructure";

    // final String DOCFILE_NAME = TestConstants.ENRON_SAMPLE_ZIP;
    final String DOCFILE_NAME = TestConstants.ENRON_SMALL_ZIP;

    genericProfile(AQLFILE_NAME, DOCFILE_NAME, DICT_PATH, INCLUDE_PATH, null);

    if (TupleList.COLLECT_STATS) {
      TupleList.dumpListStats();
    }

    endTest();
  }

  /**
   * Profile the updated version of the forwardBlock annotator.
   */
  @Test
  public void profileNewForwardBlock() throws Exception {
    startTest();

    final String AQLFILE_NAME = AQL_FILES_DIR + "/newForwardBlock.aql";

    final String DOCFILE_NAME = TestConstants.ENRON_SMALL_ZIP;

    genericProfile(AQLFILE_NAME, DOCFILE_NAME, null);

    if (TupleList.COLLECT_STATS) {
      TupleList.dumpListStats();
    }

    endTest();
  }

  /**
   * Run the "email structure" annotator that Thomas sent, generating output and checking it for
   * correctness.
   */
  @Test
  public void runEmailStruct() throws Exception {
    startTest();

    final String AQLFILE_NAME = AQL_FILES_DIR + "/emailstructure/emailstructure.aql";

    final String INCLUDE_PATH = AQL_FILES_DIR;
    final String DICT_PATH = DICTS_DIR + "/emailstructure";

    final String DOCFILE_NAME = TestConstants.ENRON_SMALL_ZIP;

    setDataPath(DICT_PATH, INCLUDE_PATH, null);
    runNonModularAQLTest(new File(DOCFILE_NAME), new File(AQLFILE_NAME));

    endTest();
  }

  /*
   * Constants for running the production eDiscovery annotator.
   */
  public static final File ANNOTATORS_PROJECT = new File(TestConstants.EXTRACTOR_LIB_DIR);
  public static final File EDISCOVERY_AQLFILE =
      new File(ANNOTATORS_PROJECT, "aql/eDA/ne-ediscovery-personorgphoneaddress.aql");

  // Directory containing auxiliary dictionary files.
  public static final String EDISCOVERY_DICT_PATH =
      String.format("%s/aql/core/Financial/dictionaries;" + "%s/aql/core/GenericNE/dictionaries",
          ANNOTATORS_PROJECT, ANNOTATORS_PROJECT);

  // Include path for finding auxiliary files.
  public static final String EDISCOVERY_INCLUDE_PATH =
      (new File(ANNOTATORS_PROJECT, "aql")).getPath();

  // Directory in Annotators project containing documents from CoNLL data set
  public static final String CONLL_DOC_DIR =
      ANNOTATORS_PROJECT.getPath() + "/resources/data/sources/CoNLL2003-original";

  // Directory in Annotators project containing documents from ACE2005 data
  // set
  public static final String ACE_DOC_DIR =
      ANNOTATORS_PROJECT.getPath() + "/resources/data/sources/ace2005original";

  // Directory in Annotators project containing documents from EnronMeetings
  // data set
  public static final String ENRON_MEETINGS_DOC_DIR =
      ANNOTATORS_PROJECT.getPath() + "/resources/data/sources/EnronGoldStandard/EnronMeetings";

  /**
   * Profile the latest eDiscovery AQL file.
   */
  @Test
  public void profileEDiscovery() throws Exception {
    startTest();
    if (false == ANNOTATORS_PROJECT.exists()) {
      // SPECIAL CASE: User hasn't checked out a copy of Annotators.
      System.err.printf("No Annotators project.  Exiting.\n");
      return;
      // END SPECIAL CASE
    }

    // final String DOCFILE_NAME = TestConstants.ENRON_1K_DUMP;
    final String DOCFILE_NAME = TestConstants.ENRON_37939_DUMP;
    // final String DOCFILE_NAME = TestConstants.ENRON_SAMPLE_ZIP;
    // final String DOCFILE_NAME = TestConstants.ENRON_SMALL_ZIP;

    genericProfile(EDISCOVERY_AQLFILE.getPath(), DOCFILE_NAME, EDISCOVERY_DICT_PATH,
        EDISCOVERY_INCLUDE_PATH, null);

    endTest();
  }

  /**
   * Profile the updated core annotators in the Annotators project
   */
  @Test
  // FIXME: This test fails because of limitation of backward compatibility API, to handle only
  // .dict dictionary files
  // in function calls 'ContainsDicts'/'ContainsDict'/'MatchesDict' .
  // Refer ModulUtils class, line# 487-494.
  public void profileUpdatedCore() throws Exception {
    startTest();
    final boolean DUMP_PLAN = false;

    if (false == ANNOTATORS_PROJECT.exists()) {
      // SPECIAL CASE: User hasn't checked out a copy of Annotators.
      System.err.printf("No Annotators project.  Exiting.\n");
      return;
      // END SPECIAL CASE
    }

    // final String DOCFILE_NAME = TestConstants.ENRON_1K_DUMP;
    // final String DOCFILE_NAME = TestConstants.ENRON_37939_DUMP;
    // final String DOCFILE_NAME = TestConstants.ENRON_SAMPLE_ZIP;
    final String DOCFILE_NAME = TestConstants.ENRON_SMALL_ZIP;
    // final String DOCFILE_NAME = docsDir + "/slowDocs";
    // final String DOCFILE_NAME = CONLL_DOC_DIR +
    // "/CoNLL2003testingDocA.zip";
    // final String DOCFILE_NAME = CONLL_DOC_DIR +
    // "/CoNLL2003testingDocB.zip";

    final File AQLFILE = new File(ANNOTATORS_PROJECT, "aql/core-updated/ne-library-annotators.aql");

    // Directory containing auxiliary dictionary files.
    final String DICT_PATH = String.format("%s/aql/core-updated/Financial/dictionaries;"
        + "%s/aql/core-updated/GenericNE/dictionaries", ANNOTATORS_PROJECT, ANNOTATORS_PROJECT);

    // Include path for finding auxiliary files.
    final String INCLUDE_PATH = (new File(ANNOTATORS_PROJECT, "aql/core-updated")).getPath();

    // UDF path for finding jars of UDF code
    final String UDF_PATH =
        (new File(ANNOTATORS_PROJECT, "aql/core-updated/GenericNE/udfjars")).getPath();

    // Run for long enough to amortize init overhead
    AQLProfiler prof = genericProfile(AQLFILE.getAbsolutePath(), DOCFILE_NAME, DICT_PATH,
        INCLUDE_PATH, UDF_PATH, 1);

    System.err.print("\n\n");
    prof.dumpTopOperators();

    // prof.dumpTopDocuments();

    if (TupleList.COLLECT_STATS) {
      TupleList.dumpListStats();
    }

    Log.info("%d char in %1.2f sec --> %1.2f kb/sec\n", prof.getTotalChar(), prof.getRuntimeSec(), //
        prof.getCharPerSec() / 1024.0);

    if (DUMP_PLAN) {
      final String PLAN_FILE_NAME = getCurOutputDir() + "/ediscovery.aog";
      Log.info("Dumping plan to %s", PLAN_FILE_NAME);
      prof.dumpPlanToFile(new File(PLAN_FILE_NAME));
    }

    endTest();
  }

  /**
   * Profile the core set of annotators in the Annotators project that were written for the NE
   * evaluation paper.
   */
  @Test
  public void profileNEEvalCore() throws Exception {
    startTest();

    final boolean DUMP_PLAN = false;

    if (false == ANNOTATORS_PROJECT.exists()) {
      // SPECIAL CASE: User hasn't checked out a copy of Annotators.
      System.err.printf("No Annotators project.  Exiting.\n");
      return;
      // END SPECIAL CASE
    }

    // final String DOCFILE_NAME = TestConstants.ENRON_1K_DUMP;
    // final String DOCFILE_NAME = TestConstants.ENRON_37939_DUMP;
    // final String DOCFILE_NAME = TestConstants.ENRON_HDRS_25K_ZIP;
    final String DOCFILE_NAME = TestConstants.ENRON_SMALL_ZIP;
    // final String DOCFILE_NAME = CONLL_DOC_DIR +
    // "/CoNLL2003testingDocA.zip";
    // final String DOCFILE_NAME = CONLL_DOC_DIR +
    // "/CoNLL2003testingDocB.zip";
    // final String DOCFILE_NAME = ACE_DOC_DIR
    // + "/ace2005testingDocOriginal.zip";
    // final String DOCFILE_NAME = ENRON_MEETINGS_DOC_DIR
    // + "/EnronMeetingsDocTestBunch4.zip";

    final File AQLFILE = new File(ANNOTATORS_PROJECT, "aql/neEvaluation/ne-library-annotators.aql");

    // Directory containing auxiliary dictionary files.
    final String DICT_PATH =
        String.format("%s/aql/neEvaluation/GenericNE/dictionaries", ANNOTATORS_PROJECT);

    // Include path for finding auxiliary files.
    final String INCLUDE_PATH = (new File(ANNOTATORS_PROJECT, "aql/neEvaluation")).getPath();

    // UDF path for finding jars of UDF code
    final String UDF_PATH =
        (new File(ANNOTATORS_PROJECT, "aql/neEvaluation/GenericNE/udfjars")).getPath();

    // Run for long enough to amortize init overhead
    AQLProfiler prof = genericProfile(AQLFILE.getAbsolutePath(), DOCFILE_NAME, DICT_PATH,
        INCLUDE_PATH, UDF_PATH, 1);

    // prof.profile ();
    System.err.print("\n\n");
    prof.dumpTopOperators();

    // prof.dumpTopDocuments();

    if (TupleList.COLLECT_STATS) {
      TupleList.dumpListStats();
    }

    Log.info("%d char in %1.2f sec --> %1.2f kb/sec\n", prof.getTotalChar(), prof.getRuntimeSec(), //
        prof.getCharPerSec() / 1024);

    if (DUMP_PLAN) {
      final String PLAN_FILE_NAME = getCurOutputDir() + "/ediscovery.aog";
      Log.info("Dumping plan to %s", PLAN_FILE_NAME);
      prof.dumpPlanToFile(new File(PLAN_FILE_NAME));
    }

    endTest();
  }

  /**
   * Profile the annotators in the Annotators project that were written for the NE evaluation paper,
   * customized for the CoNLL data set.
   */
  @Test
  public void profileCoNLL() throws Exception {
    startTest();

    final boolean DUMP_PLAN = false;

    if (false == ANNOTATORS_PROJECT.exists()) {
      // SPECIAL CASE: User hasn't checked out a copy of Annotators.
      System.err.printf("No ExtractorLibrary project.  Exiting.\n");
      return;
      // END SPECIAL CASE
    }

    final String DOCFILE_NAME = TestConstants.ENRON_SMALL_ZIP;
    // final String DOCFILE_NAME = TestConstants.ENRON_37939_DUMP;
    // final String DOCFILE_NAME = TestConstants.ENRON_SAMPLE_ZIP;
    // final String DOCFILE_NAME = TestConstants.ENRON_SMALL_ZIP;
    // final String DOCFILE_NAME = CONLL_DOC_DIR +
    // "/CoNLL2003testingDocA.zip";
    // final String DOCFILE_NAME = CONLL_DOC_DIR +
    // "/CoNLL2003testingDocB.zip";

    final File AQLFILE =
        new File(ANNOTATORS_PROJECT, "aql/neEvaluation/ne-library-annotators-for-CoNLL2003.aql");

    // Directory containing auxiliary dictionary files.
    final String DICT_PATH =
        String.format("%s/aql/neEvaluation/GenericNE/dictionaries", ANNOTATORS_PROJECT);

    // Include path for finding auxiliary files.
    final String INCLUDE_PATH = (new File(ANNOTATORS_PROJECT, "aql/neEvaluation")).getPath();

    // UDF path for finding jars of UDF code
    final String UDF_PATH =
        (new File(ANNOTATORS_PROJECT, "aql/neEvaluation/GenericNE/udfjars")).getPath();

    // Run for long enough to amortize init overhead
    AQLProfiler prof = genericProfile(AQLFILE.getAbsolutePath(), DOCFILE_NAME, DICT_PATH,
        INCLUDE_PATH, UDF_PATH, 1);

    System.err.print("\n\n");
    prof.dumpTopOperators();

    // prof.dumpTopDocuments();

    if (TupleList.COLLECT_STATS) {
      TupleList.dumpListStats();
    }

    Log.info("%d char in %1.2f sec --> %1.2f kb/sec\n", prof.getTotalChar(), prof.getRuntimeSec(), //
        prof.getCharPerSec() / 1024);

    if (DUMP_PLAN) {
      final String PLAN_FILE_NAME = getCurOutputDir() + "/ediscovery.aog";
      Log.info("Dumping plan to %s", PLAN_FILE_NAME);
      prof.dumpPlanToFile(new File(PLAN_FILE_NAME));
    }

    endTest();
  }

  /**
   * Profile the annotators in the Annotators project that were written for the NE evaluation paper,
   * customized for the ACE2005 data set.
   */
  @Test
  public void profileACE() throws Exception {
    startTest();
    final boolean DUMP_PLAN = false;

    if (false == ANNOTATORS_PROJECT.exists()) {
      // SPECIAL CASE: User hasn't checked out a copy of Annotators.
      System.err.printf("No ExtractorLibrary project.  Exiting.\n");
      return;
      // END SPECIAL CASE
    }

    final String DOCFILE_NAME = TestConstants.ENRON_SAMPLE_ZIP;
    // final String DOCFILE_NAME = TestConstants.ENRON_SAMPLE_ZIP;
    // final String DOCFILE_NAME = ACE_DOC_DIR
    // + "/ace2005testingDocOriginal.zip";

    final File AQLFILE =
        new File(ANNOTATORS_PROJECT, "aql/neEvaluation/ne-library-annotators-for-ACE2005.aql");

    // Directory containing auxiliary dictionary files.
    final String DICT_PATH =
        String.format("%s/aql/neEvaluation/GenericNE/dictionaries", ANNOTATORS_PROJECT);

    // Include path for finding auxiliary files.
    final String INCLUDE_PATH = (new File(ANNOTATORS_PROJECT, "aql/neEvaluation")).getPath();

    // UDF path for finding jars of UDF code
    final String UDF_PATH =
        (new File(ANNOTATORS_PROJECT, "aql/neEvaluation/GenericNE/udfjars")).getPath();

    // Run for long enough to amortize init overhead
    AQLProfiler prof = genericProfile(AQLFILE.getAbsolutePath(), DOCFILE_NAME, DICT_PATH,
        INCLUDE_PATH, UDF_PATH, 1);

    System.err.print("\n\n");
    prof.dumpTopOperators();

    // prof.dumpTopDocuments();

    if (TupleList.COLLECT_STATS) {
      TupleList.dumpListStats();
    }

    Log.info("%d char in %1.2f sec --> %1.2f kb/sec\n", prof.getTotalChar(), prof.getRuntimeSec(), //
        prof.getCharPerSec() / 1024);

    if (DUMP_PLAN) {
      final String PLAN_FILE_NAME = getCurOutputDir() + "/ediscovery.aog";
      Log.info("Dumping plan to %s", PLAN_FILE_NAME);
      prof.dumpPlanToFile(new File(PLAN_FILE_NAME));
    }

    endTest();
  }

  /**
   * Profile the annotators in the Annotators project that were written for the NE evaluation paper,
   * customized for the EnronMeetings data set.
   */
  @Test
  public void profileEnronMeetings() throws Exception {
    startTest();

    final boolean DUMP_PLAN = false;

    if (false == ANNOTATORS_PROJECT.exists()) {
      // SPECIAL CASE: User hasn't checked out a copy of Annotators.
      System.err.printf("No Annotators project.  Exiting.\n");
      return;
      // END SPECIAL CASE
    }

    final String DOCFILE_NAME = TestConstants.ENRON_SMALL_ZIP;
    // final String DOCFILE_NAME = TestConstants.ENRON_SAMPLE_ZIP;
    // final String DOCFILE_NAME = ENRON_MEETINGS_DOC_DIR
    // + "/EnronMeetingsDocTestBunch4.zip";

    final File AQLFILE = new File(ANNOTATORS_PROJECT,
        "aql/neEvaluation/ne-library-annotators-for-EnronMeetings.aql");

    // Directory containing auxiliary dictionary files.
    final String DICT_PATH =
        String.format("%s/aql/neEvaluation/GenericNE/dictionaries", ANNOTATORS_PROJECT);

    // Include path for finding auxiliary files.
    final String INCLUDE_PATH = (new File(ANNOTATORS_PROJECT, "aql/neEvaluation")).getPath();

    // UDF path for finding jars of UDF code
    final String UDF_PATH =
        (new File(ANNOTATORS_PROJECT, "aql/neEvaluation/GenericNE/udfjars")).getPath();

    AQLProfiler prof =
        genericProfile(AQLFILE.getAbsolutePath(), DOCFILE_NAME, DICT_PATH, INCLUDE_PATH, UDF_PATH);

    System.err.print("\n\n");
    prof.dumpTopOperators();

    prof.dumpTopDocuments();

    if (TupleList.COLLECT_STATS) {
      TupleList.dumpListStats();
    }

    Log.info("%d char in %1.2f sec --> %1.2f kb/sec\n", prof.getTotalChar(), prof.getRuntimeSec(), //
        prof.getCharPerSec() / 1024);

    if (DUMP_PLAN) {
      final String PLAN_FILE_NAME = getCurOutputDir() + "/ediscovery.aog";
      Log.info("Dumping plan to %s", PLAN_FILE_NAME);
      prof.dumpPlanToFile(new File(PLAN_FILE_NAME));
    }

    endTest();
  }

  /**
   * Profile the annotators in the Annotators project that were written for the NE evaluation paper,
   * customized for the EnronMeetings and ACE data sets
   */
  @Test
  public void profileEnronMeetingsACE() throws Exception {
    startTest();
    if (false == ANNOTATORS_PROJECT.exists()) {
      // SPECIAL CASE: User hasn't checked out a copy of Annotators.
      System.err.printf("No Annotators project.  Exiting.\n");
      return;
      // END SPECIAL CASE
    }

    // final String DOCFILE_NAME = TestConstants.ENRON_SMALL_ZIP;
    final String DOCFILE_NAME = TestConstants.ENRON_SAMPLE_ZIP;
    // final String DOCFILE_NAME = TestConstants.ENRON_HDRS_25K_ZIP;
    // final String DOCFILE_NAME = ENRON_MEETINGS_DOC_DIR
    // + "/EnronMeetingsDocTestBunch4.zip";
    // final String DOCFILE_NAME =
    // "../SystemTTests/data/EnronSample/Enron100k.zip";

    final File AQLFILE = new File(ANNOTATORS_PROJECT,
        "aql/neEvaluation/" + "ne-library-annotators-for-EnronMeetings-and-ACE2005.aql");

    // Directory containing auxiliary dictionary files.
    final String DICT_PATH =
        String.format("%s/aql/neEvaluation/GenericNE/dictionaries", ANNOTATORS_PROJECT);

    // Include path for finding auxiliary files.
    final String INCLUDE_PATH = (new File(ANNOTATORS_PROJECT, "aql/neEvaluation")).getPath();

    // UDF path for finding jars of UDF code
    final String UDF_PATH =
        (new File(ANNOTATORS_PROJECT, "aql/neEvaluation/GenericNE/udfjars")).getPath();

    AQLProfiler prof =
        genericProfile(AQLFILE.getPath(), DOCFILE_NAME, DICT_PATH, INCLUDE_PATH, UDF_PATH);

    prof.dumpTopOperators();

    endTest();
  }

  /**
   * Just compile the latest eDiscovery AQL file.
   */
  @Test
  public void compileEDiscovery() throws Exception {
    startTest();

    if (false == ANNOTATORS_PROJECT.exists()) {
      // SPECIAL CASE: User hasn't checked out a copy of AnnotatorTester.
      System.err.printf("No AnnotatorTester project.  Exiting.\n");
      return;
      // END SPECIAL CASE
    }

    // Compile to AOG...
    compileAQL(EDISCOVERY_AQLFILE, EDISCOVERY_DICT_PATH + ";" + EDISCOVERY_INCLUDE_PATH);

    instantiateOperatorGraph(Constants.GENERIC_MODULE_NAME);

    endTest();
  }

  /**
   * Profile the version of personOrg.aql with RSR enabled.
   */
  @Test
  public void profilePersonBefore() throws Exception {
    startTest();

    final String AQLFILE_NAME = AQL_FILES_DIR + "/personOrg.aql";;

    final String DOCFILE_NAME = TestConstants.ENRON_1K_DUMP;
    // final String DOCFILE_NAME = TestConstants.ENRON_10K_DUMP;
    // final String DOCFILE_NAME = TestConstants.ENRON_37939_DUMP;
    // final String DOCFILE_NAME = TestConstants.ENRON_12_16_DUMP;

    // Directory containing auxiliary dictionary files.
    final String DICTSDIR = TestConstants.TESTDATA_DIR;

    // Disable RSR
    // com.ibm.avatar.algebra.function.RegexConst.STRENGTH_REDUCE = false;
    Planner.DEFAULT_RSR = false;

    genericProfile(AQLFILE_NAME, DOCFILE_NAME, DICTSDIR);

    // Restore defaults
    Planner.DEFAULT_RSR = true;
    // com.ibm.avatar.algebra.function.RegexConst.STRENGTH_REDUCE =
    // Planner.DEFAULT_RSR;
    // Planner.restoreDefaultSentence();

    endTest();
  }

  /**
   * Test to verify that the backdoor for profiling an OperatorRunner directly works as expected.
   */
  // @Test Operator graph does not provide any more back door entry
  public void profileBackDoorTest() throws Exception {

    final String TOP_VIEW = "CapsOrg";
    final File AOG_FILE = new File(TestConstants.AOG_DIR, "memoryTests/eDA.aog");
    final File DOCS_FILE = new File(TestConstants.ENRON_SMALL_ZIP);

    // Run the profiler with all outputs enabled
    // In this case, we should find CapsOrg in the top 25 views
    String ret = genericProfileBackdoor(AOG_FILE, DOCS_FILE, null);
    assertTrue(
        String.format("The view named '%s' is not among the top 25 views, as expected.", TOP_VIEW),
        -1 != ret.indexOf(TOP_VIEW));

    // Run the profiler when enabling a restricted set of outputs
    // In this case, we should not find CapsOrg in the top 25 views, because CapsOrg is not involved
    // in computing the
    // output type com.ibm.eda.LegalConcept.LegalContent
    String[] OUTPUT_TYPES = new String[] {"com.ibm.eda.LegalConcept.LegalContent"};
    ret = genericProfileBackdoor(AOG_FILE, DOCS_FILE, OUTPUT_TYPES);
    assertTrue(String.format("The view named '%s' is among the top 25 views, which is not expected",
        TOP_VIEW), -1 == ret.indexOf(TOP_VIEW));

  }

  /**
   * Generic method that exercises the back door instantiating the profiler directly on an
   * OperatorGraphRunner
   * 
   * @param aogFile the operator graph to profile
   * @param docsFile the input document collection
   * @param outputTypes a set of specific output types to enable; <code>null</code> to enable all
   *        output types.
   * @return
   * @throws IOException
   * @throws Exception
   */
  private String genericProfileBackdoor(File aogFile, File docsFile, String[] outputTypes)
      throws IOException, Exception {
    String aogStr = FileUtils.fileToStr(aogFile, "UTF-8");

    // Get the schema of the document collection
    DocReader docs = new DocReader(docsFile);
    TupleSchema docSchema = docs.getDocSchema();

    // Generate a TAM file.
    final String moduleName = "genericModule";
    TAM tam = new TAM(moduleName);
    tam.setAog(aogStr);

    // create dummy empty meta data object; this is to make loader happy
    ModuleMetadataImpl dummyMD = ModuleMetadataImpl.createEmptyMDInstance();

    // we compile dictionaries from old testcases using the built-in whitespace tokenizer
    TokenizerConfig tokenizerCfg = new TokenizerConfig.Standard();
    dummyMD.setTokenizerType(tokenizerCfg.getName());
    tam.setMetadata(dummyMD);

    TAMSerializer.serialize(tam, getCurOutputDir().toURI());

    // Instantiate the resulting operator graph.
    String modulePath = getCurOutputDir().toURI().toString();
    System.err.printf("Using module path '%s'\n", modulePath);

    OperatorGraph systemt =
        OperatorGraph.createOG(new String[] {moduleName}, modulePath, null, null);

    // The profiler hasn't been integrated yet with the Java API,
    // so we use the backdoor to get it a copy of the internal AOGRunner object.
    OperatorGraphRunner runner =
        ((OperatorGraphImpl) systemt).getInternalImpl_INTERNAL_USE_ONLY(docSchema);

    // Set up the runner to execute a specific set of outputs
    if (null != outputTypes) {
      runner.setAllOutputsEnabled(false);
      for (String outputName : outputTypes) {
        runner.setOutputEnabled(outputName, true);
      }
    }

    // Start profiling directly using the AOG runner
    AQLProfiler profiler = null;

    // FIXME: need to instantiate a profiler before the method will work.
    profiler = AQLProfiler.createProfilerFromOG(systemt);
    profiler.startSampling();

    long elapsedNs = 0;
    int ndoc = 0;
    ThreadMXBean bean = ManagementFactory.getThreadMXBean();
    long id = java.lang.Thread.currentThread().getId();
    int progressInterval = 1000;
    long startNs = bean.getThreadCpuTime(id);

    // Run documents through the operator graph for at least 10 seconds
    int minRuntimeSec = 10;
    while (elapsedNs < minRuntimeSec * 1e9) {

      docs = new DocReader(docsFile);

      while (docs.hasNext()) {

        runner.pushDoc(docs.next());

        ndoc++;
        if (0 == ndoc % progressInterval) {
          long curNs = bean.getThreadCpuTime(id);
          double curSec = (curNs - startNs) / 1e9;
          Log.info("Processed %d docs in %1.2f sec", ndoc, curSec);
        }
      }

      long endNs = bean.getThreadCpuTime(id);
      elapsedNs = endNs - startNs;

      docs.remove();

    }

    // Stop the profiler and dump a summary of overheads.
    profiler.stopSampling();
    StringBuffer sb = new StringBuffer();
    profiler.dumpTopViews(sb);
    Log.info("\n%s", sb.toString());
    return sb.toString();
  }

  /**
   * Test of a bug that we thought was a problem with the profiling code; turned out to be a
   * deficiency of the optimizer. NOTE FROM LAURA 10/1/2012: Disabled since revision 9 of old CS
   * svn. END NOTE FROM LAURA 10/1/2012
   */
  // @Test
  // public void profilingBugTest() throws Exception {
  //
  // // Planner.forceLWSentence();
  //
  // final String AQLFILE_NAME = AQL_FILES_DIR + "/profilingBugTest.aql";
  //
  // final String DOCFILE_NAME = TestConstants.SPOCK_DUMP;
  //
  // // Directory containing auxiliary dictionary files.
  // final String DICTSDIR = TestUtils.DEFAULT_DICTS_DIR;
  //
  // // Allow for some natural variation due to measurement error.
  // // final double FUDGE_FACTOR = 2.0;
  //
  // AQLProfiler prof = new AQLProfiler(new File(AQLFILE_NAME), new File(
  // DOCFILE_NAME));
  // prof.setDictPath(DICTSDIR);
  // prof.profile();
  // PerfCounter beforeCounter = prof.getPrevRunCounter("before");
  // PerfCounter afterCounter = prof.getPrevRunCounter("after");
  //
  // double beforeNs = beforeCounter.getExecNs(0);
  // double afterNs = afterCounter.getExecNs(0);
  //
  // System.err.printf("Time went from %1.2f to %1.2f sec.\n",
  // beforeNs / 1e9, afterNs / 1e9);
  // The two times should be approximately equal.
  // assertTrue(beforeNs < FUDGE_FACTOR * afterNs);
  // assertTrue(afterNs < FUDGE_FACTOR * beforeNs);
  // Planner.restoreDefaultSentence();
  // }
  /**
   * Test of tokenization rate for FastTokenize (which was a bottleneck when this test was written)
   */
  @Test
  public void fastTokenizeSpeedTest() throws Exception {

    startTest();

    final String AQL_FILE_NAME = AQL_FILES_DIR + "/tokSpeedTest.aql";
    final String DOCS_FILE_NAME = TestConstants.ENRON_37939_DUMP;

    runNonModularAQLTest(new File(DOCS_FILE_NAME), AQL_FILE_NAME);

  }

  /**
   * Place to profile individual AQL files.
   */
  public void scratchpad() throws Exception {

    // Planner.forceLWSentence();

    final String BASE_DIR = TestConstants.TESTDATA_DIR + "/../../AnnotatorTester";

    final String AQLFILE_NAME = BASE_DIR +
    // "/ediscovery/configs/aql/namedentity-ediscovery-modified2.aql";
        "/ediscovery/configs/aql/namedentity-ediscovery-modified3.aql";
    // "/ediscovery/configs/aql/namedentity-ediscovery.aql";

    final String DOCFILE_NAME =
        // TestConstants.ENRON_10K_DUMP;
        TestConstants.SPOCK_DUMP;
    // TestConstants.ENRON_1K_DUMP;

    // Directory containing auxiliary dictionary files.
    final String DICTSDIR = BASE_DIR + "/ediscovery/configs/aql";

    genericProfile(AQLFILE_NAME, DOCFILE_NAME, DICTSDIR);

    // Planner.restoreDefaultSentence();
  }

  // /**
  // * Verify that the "before" and "after" versions of the hot spots in
  // * person-org-minimal.aql produce the exact same results. NOTE FROM LAURA 10/1/2012: Disabled
  // since revision 9 of
  // old CS svn. END NOTE FROM LAURA 10/1/2012
  // */
  // @Test
  // public void validatePerson() throws Exception {
  //
  // // Planner.forceLWSentence();
  //
  // final String BEFORE_AQL_FILE = TestConstants.TESTDATA_DIR
  // + "/../../resources/NEResources/configs/builtin/aql"
  // + "/person-org-hotspots.aql";
  // final String AFTER_AQL_FILE = TestConstants.TESTDATA_DIR
  // + "/../../resources/NEResources/configs/builtin/aql"
  // + "/person-org-fast.aql";
  //
  // final String DOCFILE_NAME = TestConstants.ENRON_1K_DUMP;
  // // final String DOCFILE_NAME = TestConstants.ENRON_10K_DUMP;
  // // final String DOCFILE_NAME = TestConstants.ENRON_37939_DUMP;
  // // final String DOCFILE_NAME = TestConstants.ENRON_12_16_DUMP;
  //
  // // Directory containing auxiliary dictionary files.
  // final String DICTSDIR = TestConstants.TESTDATA_DIR
  // + "/../../resources/NEResources/data/builtin";
  //
  // // Run the original and the improved versions of the rules.
  // genericTestCase(BEFORE_AQL_FILE, DICTSDIR, DOCFILE_NAME, "personBefore");
  // genericTestCase(AFTER_AQL_FILE, DICTSDIR, DOCFILE_NAME, "personAfter");
  //
  // // Compare results between the two runs.
  // util.setOutputDir(String.format("%s/%s", getCurOutputDir(), "personAfter"));
  // util.setExpectedDir(String.format("%s/%s", getCurOutputDir(), "personBefore"));
  //
  // // Note that the comparisons are done without truncation.
  // util.compareAgainstExpected("CapsOrg.htm", false);
  // util.compareAgainstExpected("CapsPerson.htm", false);
  // util.compareAgainstExpected("Coonetothree.htm", false);
  // util.compareAgainstExpected("Coonetotwo.htm", false);
  // util.compareAgainstExpected("DotCom.htm", false);
  // util.compareAgainstExpected("InitialWord.htm", false);
  // util.compareAgainstExpected("Person4ar1.htm", false);
  // util.compareAgainstExpected("StrictCapsPerson.htm", false);
  // util.compareAgainstExpected("StrictCapsPersonR.htm", false);
  // util.compareAgainstExpected("WeakInitialWord.htm", false);
  //
  // // Planner.restoreDefaultSentence();
  // }

  /**
   * Make sure that no document in the Enron dataset takes more than 1 second to go through the
   * forwardBlock annotator.
   */
  @Test
  public void forwardBlockTest() throws Exception {
    startTest();

    final int MSEC_MAX = 1 * 1000;
    final String AQLFILE_NAME = AQL_FILES_DIR + "/forwardBlock.aql";
    // final String DOCFILE_NAME = TestConstants.ENRON_10K_DUMP;

    genericTimeTest(AQLFILE_NAME, TestConstants.ENRON_1K_DUMP, MSEC_MAX, false);
  }

  /**
   * Make sure that no document in the Enron dataset takes more than 2 seconds to go through the
   * version of the named entity annotator that uses forward blocks.
   */
  @Test
  public void namedEntityFBTest() throws Exception {
    startTest();

    final int MSEC_MAX = 2 * 1000;
    final String AQLFILE_NAME = TestConstants.AQL_DIR + "/lotus/namedentity-header.aql";

    // Due to JRE startup overhead, the first run of the test may have some
    // false positives. Do a "throw-away" run with a high limit to prime the
    // system.
    Log.info("Doing practice run...");
    genericTimeTest(AQLFILE_NAME, TestConstants.ENRON_1K_DUMP, 10 * MSEC_MAX, false);

    // Now run the test for real
    Log.info("Running test for real.");
    genericTimeTest(AQLFILE_NAME, TestConstants.ENRON_1K_DUMP, MSEC_MAX, false);
  }

  /**
   * Run just the regular expressions in the named entity annotator.
   */
  @Test
  public void neRegexesTest() throws Exception {
    startTest();

    final int MSEC_MAX = 5 * 1000;
    final String AQLFILE_NAME = AQL_FILES_DIR + "/neRegexesTest.aql";

    final String DUMPFILE_NAME = TestConstants.ENRON_1K_DUMP;
    // final String DUMPFILE_NAME = TestConstants.ENRON_10K_DUMP;

    // Fourth argument tells genericTimeTest() to break out individual
    // timings for all the top-level outputs of the graph.
    genericTimeTest(AQLFILE_NAME, DUMPFILE_NAME, MSEC_MAX, true);
  }

  /**
   * Generate random plans, using merge join when possible, and measure performance. NOTE FROM LAURA
   * 10/1/2012: Disabled since revision 9 of old CS svn. END NOTE FROM LAURA 10/1/2012
   */
  // @Test
  public void randomMergeTest() throws Exception {

    startTest();
    // Set a long upper bound; we only want to fail when the
    // randomly-generated plan is really hideous.
    final int MSEC_MAX = 30 * 1000;
    final String AQLFILE_NAME = TestConstants.AQL_DIR
        // + "/lotus/namedentity-new.aql";
        + "/lotus/namedentity-textcol.aql";
    // + "/scratch.aql";
    // + "/personphone.aql";

    final String DOCFILE_NAME = TestConstants.ENRON_1K_DUMP;
    // final String DOCFILE_NAME = TestConstants.ENRON_10K_DUMP;
    // final String DOCFILE_NAME = TestConstants.ENRON_37939_DUMP;
    // final String DOCFILE_NAME = TestConstants.ENRON_12_16_DUMP;
    // final String DOCFILE_NAME = TestConstants.TEST_DOCS_DIR + "/tmp.del";

    final int NUMRUNS = 500;

    final String OUTFILE_NAME = getCurOutputDir() + "/randomMergeOut.csv";

    // To generate the random join orders, we use the RandomMergePlanner
    // instead of the default planner implementation.
    // planner = new Planner (Planner.ImplType.RANDOM_MERGE);

    // Cut output to a minimum.
    Log.disableAllMsgTypes();
    int origFeedbackInterval = FEEDBACK_INTERVAL;
    FEEDBACK_INTERVAL = 10000000;

    // We'll generate an output file for spreadsheet analysis.
    FileWriter out = new FileWriter(OUTFILE_NAME);

    // Generate the header.
    out.append("Run,Random Seed,Tot Time (sec),Max Time (sec)\n");

    Random seedGen = new Random();

    for (int i = 0; i < NUMRUNS; i++) {
      // For determinism, we generate and output a random seed each
      // iteration.
      long seed = seedGen.nextLong();

      RandomMergePlanner.setRandomSeed(seed);

      TimeTestResults results = genericTimeTest(AQLFILE_NAME, DOCFILE_NAME, MSEC_MAX, false);

      double totSec = results.totNs / 1e9;
      double maxSec = results.maxNs / 1e9;

      out.append(String.format("%d,%d,%1.2f,%1.2f\n", i, seed, totSec, maxSec));
    }

    out.close();

    // Restore settings for the next test.
    Log.restoreDefaultMsgTypes();
    FEEDBACK_INTERVAL = origFeedbackInterval;
  }

  /**
   * A test case that creates an artificial large document set and runs it.
   */
  @Test
  public void profileBigDoc() throws Exception {
    startTest();

    final String DOCS_FILE_NAME = TestConstants.ENRON_SMALL_ZIP;
    final String BIG_DOC_FILE = getCurOutputDir() + "/bigdoc.del";

    final int DOC_SIZE_TARGET_KB = 1024;

    System.err.printf("Creating large 'documents'...\n");
    createBigDocs(DOCS_FILE_NAME, BIG_DOC_FILE, DOC_SIZE_TARGET_KB);

    // Run the production eDiscovery annotator.

    if (false == ANNOTATORS_PROJECT.exists()) {
      // SPECIAL CASE: User hasn't checked out a copy of AnnotatorTester.
      System.err.printf("No AnnotatorTester project.  Exiting.\n");
      return;
      // END SPECIAL CASE
    }

    // first compile the AQL
    String dataPath = String.format("%s;%s", EDISCOVERY_DICT_PATH, EDISCOVERY_INCLUDE_PATH);
    compileAQL(EDISCOVERY_AQLFILE, dataPath);

    // profile
    String moduleURI = getCurOutputDir().toURI().toString();
    AQLProfiler prof = AQLProfiler.createCompiledModulesProfiler(
        new String[] {Constants.GENERIC_MODULE_NAME}, moduleURI, null, null);

    // Use chunking to keep performance under control.
    prof.setChunker(new EmailChunker());

    // Get an update after every megabyte of documents.
    prof.setProgressInterval(Math.max(1, 1024 / DOC_SIZE_TARGET_KB));

    prof.profile(new File(BIG_DOC_FILE), LangCode.en, null);

    endTest();
  }

  /**
   * Test case that just runs the major dictionaries from the EDiscovery annotators.
   */
  @Test
  public void runEDiscoveryDicts() throws Exception {
    startTest();

    // We've precompiled the AQL for the eDiscovery annotators and stripped
    // it down to just the dictionaries.
    // final String AOG_FILE_NAME = TestConstants.AOG_DIR + "/speedTests/eDADicts.aog";
    final String TAM_DIR = new File(TestConstants.AOG_DIR + "/speedTests/serverSideNEDicts")
        .getCanonicalFile().toURI().toString();

    // final String DOCFILE_NAME = TestConstants.ENRON_1K_DUMP;
    // final String DOCFILE_NAME = TestConstants.ENRON_37939_DUMP;
    // final String DOCFILE_NAME = TestConstants.ENRON_SAMPLE_ZIP;
    final String DOCFILE_NAME = TestConstants.ENRON_SMALL_ZIP;

    // DocScan scan = DocScan.makeFileScan (new File (DOCFILE_NAME));

    // No output -- we just want to measure speed.
    setDisableOutput(true);

    // TODO: Replace the following line to run TAM
    // util.runAOGFile (scan, AOG_FILE_NAME);
    /*
     * // prepare list of dict file being referred in the aog List<String> dictFilePaths = new
     * ArrayList<String> (); dictFilePaths.add ("dictionaries/strictLast.dict"); dictFilePaths.add
     * ("saql/dictionaries/names/name_israel.dict"); dictFilePaths.add
     * ("dictionaries/organization_full_noCase.dict"); dictFilePaths.add
     * ("dictionaries/organization_full_case.dict"); dictFilePaths.add
     * ("dictionaries/strictLast_german_bluePages.dict"); dictFilePaths.add
     * ("saql/dictionaries/FilterPersonDict.dict"); dictFilePaths.add
     * ("dictionaries/filterPerson_LCDict.dict"); //dictFilePaths.add
     * ("dictionaries/abbreviation.dict"); dictFilePaths.add ("dictionaries/city_german.dict");
     * dictFilePaths.add ("dictionaries/filterPerson_german.dict"); dictFilePaths.add
     * ("dictionaries/organization_media.dict"); dictFilePaths.add
     * ("dictionaries/filterOrg_german.dict"); //dictFilePaths.add
     * ("dictionaries/names/strictLast_israel.dict"); //dictFilePaths.add
     * ("dictionaries/names/strictLast_india.partial.dict"); dictFilePaths.add
     * ("dictionaries/strictFirst_german.dict"); dictFilePaths.add
     * ("dictionaries/names/name_france.dict"); dictFilePaths.add ("dictionaries/country.dict");
     * dictFilePaths.add ("dictionaries/uniqMostCommonSurname.dict"); dictFilePaths.add
     * ("dictionaries/timeZone.dict"); dictFilePaths.add
     * ("dictionaries/speedTests/runEDiscoveryDicts/InitialDict.dict"); dictFilePaths.add
     * ("dictionaries/organization_suffix.dict"); dictFilePaths.add ("dictionaries/continent.dict");
     * dictFilePaths.add ("dictionaries/wkday.dict"); dictFilePaths.add
     * ("saql/dictionarie/GreetingsDict.dict"); dictFilePaths.add
     * ("dictionaries/speedTests/runEDiscoveryDicts/FilterOrgDict.dict"); dictFilePaths.add
     * ("dictionaries/city.dict"); dictFilePaths.add ("dictionaries/filterPerson_RCDict.dict");
     * dictFilePaths.add ("dictionaries/speedTests/runEDiscoveryDicts/DotComSuffixDict.dict");
     * dictFilePaths.add ("dictionaries/names/name_spain.dict"); dictFilePaths.add
     * ("dictionaries/industryType_suffix.dict"); dictFilePaths.add
     * ("dictionaries/invalidPersonFragment.dict"); dictFilePaths.add
     * ("dictionaries/industryType_prefix.dict"); dictFilePaths.add
     * ("dictionaries/names/strictLast_france.dict"); dictFilePaths.add
     * ("dictionaries/strictLast_german.dict"); dictFilePaths.add
     * ("dictionaries/names/strictLast_spain.dict"); dictFilePaths.add
     * ("dictionaries/names/strictLast_italy.dict"); dictFilePaths.add
     * ("dictionaries/names/strictFirst_spain.dict"); dictFilePaths.add ("dictionaries/name.dict");
     * dictFilePaths.add ("dictionaries/strictFirst_german_bluePages.dict"); dictFilePaths.add
     * ("dictionaries/organization_newspaper.dict"); dictFilePaths.add
     * ("dictionaries/streetSuffix_forPerson.dict"); dictFilePaths.add
     * ("dictionaries/strictFirst.dict"); dictFilePaths.add
     * ("dictionaries/names/strictFirst_france.dict"); dictFilePaths.add
     * ("dictionaries/speedTests/runEDiscoveryDicts/OrgPrepDict.dict"); dictFilePaths.add
     * ("dictionaries/strictNickName.dict"); dictFilePaths.add
     * ("saql/dictionarie/StrongPhoneVariantDictionary.dict"); dictFilePaths.add
     * ("dictionaries/names/strictFirst_india.partial.dict"); dictFilePaths.add
     * ("dictionaries/speedTests/runEDiscoveryDicts/OrgPartnershipDict.dict"); dictFilePaths.add
     * ("dictionaries/speedTests/runEDiscoveryDicts/TheDict.dict"); dictFilePaths.add
     * ("saql/dictionarie/PersonSuffixDict.dict"); dictFilePaths.add
     * ("dictionaries/stateList.dict"); dictFilePaths.add ("OrgToAvoidDict.dict"); dictFilePaths.add
     * ("dictionaries/names/strictFirst_italy.dict"); dictFilePaths.add
     * ("dictionaries/names/strictFirst_israel.dict"); dictFilePaths.add
     * ("dictionaries/speedTests/runEDiscoveryDicts/OrgConjDict.dict"); dictFilePaths.add
     * ("dictionaries/nationality.dict"); dictFilePaths.add ("dictionaries/names/name_italy.dict");
     * // create TAM out of aog and dictionaries TAM tam = TestUtils.createTAM
     * ("runEDiscoveryDicts", AOG_FILE_NAME, dictFilePaths, new FastTokenizer ()); // Serialize tam
     * URI moduleURI = getCurOutputDir()toURI (); TAMSerializer.serialize (tam, moduleURI, "UTF-8");
     * util.runTAMFile (scan, "condEvalBugTest", moduleURI.toString ());
     */

    // runAOGFile (new File (DOCFILE_NAME), new File (AOG_FILE_NAME), null);
    runModule(new File(DOCFILE_NAME), Constants.GENERIC_MODULE_NAME, TAM_DIR);
  }

  /** Create a set of artificial large documents by combining smaller ones. */
  public static void createBigDocs(final String smallDocsFile, final String bigDocsFile,
      final int sizeTargetKb)
      throws Exception, UnsupportedEncodingException, FileNotFoundException, IOException {
    {
      // Create a big "document" by concatenating input docs.
      // DocScan docscan = DocScan.makeFileScan (new File (smallDocsFile));
      // MemoizationTable mt = new MemoizationTable (docscan);
      DocReader reader = new DocReader(new File(smallDocsFile));

      Writer out = new OutputStreamWriter(new FileOutputStream(bigDocsFile), "UTF-8");

      // FieldGetter<Span> getText = docscan.textGetter ();
      FieldGetter<Text> getText = reader.getDocSchema().textAcc(Constants.DOCTEXT_COL);

      int bigDocCount = 0;
      int totalChars = 0;
      // while (mt.haveMoreInput ()) {
      while (reader.hasNext()) {
        int docSize = 0;
        bigDocCount++;
        out.append(String.format("%d,\"", bigDocCount));
        // adding document lable, since by default loader expects two column document scheama
        out.append(String.format("docLabel-%d\",\"", bigDocCount));

        // Append up to DOC_PER_BIG_DOC docs together to make a big
        // doctext.
        // while (mt.haveMoreInput ()
        while (reader.hasNext() && (docSize / 1024) < sizeTargetKb) {
          // Tuple doc = docscan.getNextDocTup (mt);
          Tuple doc = reader.next();
          String doctext = getText.getVal(doc).getText();

          totalChars += doctext.length();

          // Escape double quotes.
          doctext = doctext.replace("\"", "\"\"");
          out.append(doctext);
          out.append("\n");

          docSize += doctext.length();
        }

        // Close the quotes around the doctext.
        out.append("\"\n");

      }

      out.close();

      // Close the document reader
      reader.remove();

      double avgDocLen = (double) totalChars / (double) bigDocCount;

      System.err.printf("Created %d big docs of avg. length %1.1f kb\n", bigDocCount,
          avgDocLen / 1024);

    }
  }

  private static final String docsDir = TestConstants.TESTDATA_DIR + "/docs/speedTests";

  /**
   * Tarfile containing a single big document made by concatenating a many enron emails together,
   * with headers included.
   */
  public static final String TEN_MEG_DOC_TARFILE = docsDir + "/ten.tgz";
  public static final String FIVE_MEG_DOC_TARFILE = docsDir + "/five.tgz";
  public static final String ONE_MEG_DOC_TARFILE = docsDir + "/one.tgz";

  /**
   * A test case that runs the artificial big document that the Lotus folks provided.
   */
  @Test
  public void profileBigDoc2() throws Exception {
    startTest();

    final File PLAN_FILE = new File(getCurOutputDir(), "eda.aog");

    final String DOCS_FILE_NAME = ONE_MEG_DOC_TARFILE;

    // Run the production eDiscovery annotator.
    if (false == ANNOTATORS_PROJECT.exists()) {
      // SPECIAL CASE: User hasn't checked out a copy of AnnotatorTester.
      System.err.printf("No AnnotatorTester project.  Exiting.\n");
      return;
      // END SPECIAL CASE
    }

    // first compile the AQL
    String dataPath = String.format("%s;%s", EDISCOVERY_DICT_PATH, EDISCOVERY_INCLUDE_PATH);
    compileAQL(EDISCOVERY_AQLFILE, dataPath);

    // profile
    String moduleURI = getCurOutputDir().toURI().toString();
    AQLProfiler prof = AQLProfiler.createCompiledModulesProfiler(
        new String[] {Constants.GENERIC_MODULE_NAME}, moduleURI, null, null);

    // Use chunking so that we don't run out of memory.
    prof.setChunker(new EmailChunker());

    prof.setProgressInterval(1);

    Log.info("Dumping plan to %s.", PLAN_FILE);
    prof.dumpPlanToFile(PLAN_FILE);

    Log.info("Calling profile() method.");
    prof.profile(new File(DOCS_FILE_NAME), LangCode.en, null);

    endTest();
  }

  /**
   * A generic AQL test case, adapted from the method in {@link AQLEnronTests} with the same name.
   */
  public void genericTestCase(String aqlFileName, String dictsDirName, String dumpFileName,
      String prefix) throws Exception {

    setDataPath(dictsDirName);
    setDisableOutput(true);
    runNonModularAQLTest(new File(dumpFileName), new File(aqlFileName));

  }

  /**
   * Benchmark loading and compiling the AOG file that goes with the Lotus release. Change the AOG
   * file that this test uses as needed.
   */
  @Test
  public void benchmarkLotusAOGLoad() throws Exception {

    // final String aogFileName = TestConstants.AOG_DIR + "/speedTests/clientSideNE.aog";
    final String tamDir = new File(TestConstants.AOG_DIR + "/speedTests/clientSideNE")
        .getCanonicalFile().toURI().toString();

    final int numIter = 1;
    ThreadMXBean bean = ManagementFactory.getThreadMXBean();
    long id = java.lang.Thread.currentThread().getId();
    // We don't get a good profile running the test just once, so repeat it
    // multiple times.
    for (int i = 0; i < numIter; i++) {
      System.err.printf("Iteration %d: Loading rules...\n", i + 1);

      long beforeMs = bean.getThreadCpuTime(id);

      // String aog = FileUtils.fileToStr (new File (aogFileName), "UTF-8");
      //
      // // Generate a TAM file.
      // final String moduleName = Constants.GENERIC_MODULE_NAME;
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
      //
      // // Instantiate the resulting operator graph.
      // String modulePath = getCurOutputDir ().toURI ().toString ();
      // System.err.printf ("Using module path '%s'\n", modulePath);

      OperatorGraph.createOG(new String[] {Constants.GENERIC_MODULE_NAME}, tamDir, null, null);

      // annotateString (OperatorGraph.createOG (new String[] { Constants.GENERIC_MODULE_NAME },
      // tamDir, null, null),
      // "Dummy doc text", null, null);

      long afterMs = bean.getThreadCpuTime(id);

      long elapsedMs = afterMs - beforeMs;

      System.err.printf(" --> Loading rules took %1.2f sec\n", elapsedMs / 1e9);
    }
  }

  public void testLotusAOGLoadInBackground() throws Exception {

    CountingThread countThread = new CountingThread(1000000000);
    countThread.setPriority(Thread.NORM_PRIORITY);

    final String aogFileName = TestConstants.AOG_DIR + "/speedTests/clientSideNE.aog";
    SystemTThread systemtThread = new SystemTThread(aogFileName);
    systemtThread.setPriority(Thread.MIN_PRIORITY);

    countThread.start();
    systemtThread.start();
  }

  class SystemTThread extends Thread {
    String aogFileName;

    SystemTThread(String aogFileName) {
      this.aogFileName = aogFileName;
    }

    @Override
    public void run() {
      try {

        System.err.println("SystemT thread: Starting !");
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        long id = java.lang.Thread.currentThread().getId();
        long beforeMs = bean.getThreadCpuTime(id);

        String aog = FileUtils.fileToStr(new File(aogFileName), "UTF-8");

        // Generate a TAM file.
        final String moduleName = Constants.GENERIC_MODULE_NAME;
        TAM tam = new TAM(moduleName);
        tam.setAog(aog);

        // create dummy empty meta data object; this is to make loader happy
        ModuleMetadataImpl dummyMD = ModuleMetadataImpl.createEmptyMDInstance();

        // we compile dictionaries from old testcases using the built-in whitespace tokenizer
        TokenizerConfig tokenizerCfg = new TokenizerConfig.Standard();
        dummyMD.setTokenizerType(tokenizerCfg.getName());
        tam.setMetadata(dummyMD);

        TAMSerializer.serialize(tam, getCurOutputDir().toURI());

        // Instantiate the resulting operator graph.
        String modulePath = getCurOutputDir().toURI().toString();
        System.err.printf("Using module path '%s'\n", modulePath);

        OperatorGraph og =
            OperatorGraph.createOG(new String[] {moduleName}, modulePath, null, null);

        // Annotate a dummy document to force a load.

        System.setProperty(OperatorGraphImpl.LOAD_SLEEP_TIME_PROPERTY, "10");

        annotateString(og, "Dummy doc text", null, null);

        long afterMs = bean.getThreadCpuTime(id);
        long elapsedMs = afterMs - beforeMs;

        System.err.printf("SystemT thread: I'm done loading in %1.2f sec\n", elapsedMs / 1e9);

      } catch (Exception e) {
        e.printStackTrace();
      }

      System.clearProperty(OperatorGraphImpl.LOAD_SLEEP_TIME_PROPERTY);
    }
  }

  class CountingThread extends Thread {
    long limit;

    CountingThread(long limit) {
      this.limit = limit;
    }

    @Override
    public void run() {
      ThreadMXBean bean = ManagementFactory.getThreadMXBean();
      long id = java.lang.Thread.currentThread().getId();
      System.err.println("Counting thread: Starting !");
      long beforeMs = bean.getThreadCpuTime(id);

      // Count to 1 billion for 6 times
      for (int k = 0; k < 1; k++) {
        long i = 0;
        while (i <= limit)
          i++;
      }

      long afterMs = bean.getThreadCpuTime(id);
      long elapsedNs = afterMs - beforeMs;

      System.err.printf("Count thread: I'm done counting in %1.2f sec\n", elapsedNs);
    }
  }

  /**
   * Benchmark the latest eDiscovery AQL file. Not enabled as a JUnit test because it takes a while.
   */
  public void benchmarkEDiscovery() throws Exception {

    // Planner.forceLWSentence();

    if (false == ANNOTATORS_PROJECT.exists()) {
      // SPECIAL CASE: User hasn't checked out a copy of AnnotatorTester.
      System.err.printf("No AnnotatorTester project.  Exiting.\n");
      return;
      // END SPECIAL CASE
    }

    final String DOCFILE_NAME = TestConstants.ENRON_SAMPLE_ZIP;
    // final String DOCFILE_NAME = TestConstants.ENRON_1K_DUMP;

    setDataPath(EDISCOVERY_DICT_PATH, EDISCOVERY_INCLUDE_PATH, null);
    setDisableOutput(true);

    // DocScan scan = DocScan.makeFileScan (new File (DOCFILE_NAME));
    runNonModularAQLTest(new File(DOCFILE_NAME), EDISCOVERY_AQLFILE);

    if (TupleList.COLLECT_STATS) {
      TupleList.dumpListStats();
    }

    // Planner.restoreDefaultSentence();
  }

  /**
   * Benchmark the updated version of the core annotators that is checked into the Annotators
   * project. Not enabled as a JUnit test because it takes a while.
   */
  public void benchmarkUpdatedCore() throws Exception {

    if (false == ANNOTATORS_PROJECT.exists()) {
      // SPECIAL CASE: User hasn't checked out a copy of AnnotatorTester.
      System.err.printf("No AnnotatorTester project.  Exiting.\n");
      return;
      // END SPECIAL CASE
    }

    final String DOCFILE_NAME = TestConstants.ENRON_SAMPLE_ZIP;
    // final String DOCFILE_NAME = TestConstants.ENRON_1K_DUMP;

    final File AQLFILE =
        new File(ANNOTATORS_PROJECT, "Annotators/core-updated/mashuphub-systemT-annotators.aql");

    // Directory containing auxiliary dictionary files.
    final String DICT_PATH = String.format(
        "%s/Annotators/core-updated/Financial/dictionaries;"
            + "%s/Annotators/core-updated/GenericNE/dictionaries",
        ANNOTATORS_PROJECT, ANNOTATORS_PROJECT);

    // Include path for finding auxiliary files.
    final String INCLUDE_PATH = (new File(ANNOTATORS_PROJECT, "Annotators/core-updated")).getPath();

    // UDF path for finding jars of UDF code
    final String UDF_PATH =
        (new File(ANNOTATORS_PROJECT, "Annotators/core-updated/GenericNE/udfjars")).getPath();

    setDataPath(DICT_PATH, INCLUDE_PATH, UDF_PATH);

    // setDumpPlan(true);

    setDisableOutput(true);

    // DocScan scan = DocScan.makeFileScan (new File (DOCFILE_NAME));
    runNonModularAQLTest(new File(DOCFILE_NAME), AQLFILE);

    if (TupleList.COLLECT_STATS) {
      TupleList.dumpListStats();
    }

  }

  /**
   * Benchmark the customized CoNLL annotators on CoNLL data. Not enabled as a JUnit test because it
   * takes a while.
   */
  public void benchmarkCoNLL() throws Exception {

    if (false == ANNOTATORS_PROJECT.exists()) {
      // SPECIAL CASE: User hasn't checked out a copy of AnnotatorTester.
      System.err.printf("No AnnotatorTester project.  Exiting.\n");
      return;
      // END SPECIAL CASE
    }

    final String DOCFILE_NAME = TestConstants.ENRON_SMALL_ZIP;
    // final String DOCFILE_NAME = TestConstants.ENRON_SAMPLE_ZIP;
    // final String DOCFILE_NAME = CONLL_DOC_DIR + "/CoNLL2003testingDocA";
    // final String DOCFILE_NAME = CONLL_DOC_DIR + "/CoNLL2003testingDocB";

    final File AQLFILE = new File(ANNOTATORS_PROJECT,
        "Annotators/neEvaluation/ne-library-annotators-for-CoNLL2003.aql");

    // Directory containing auxiliary dictionary files.
    final String DICT_PATH =
        String.format("%s/Annotators/neEvaluation/GenericNE/dictionaries", ANNOTATORS_PROJECT);

    // Include path for finding auxiliary files.
    final String INCLUDE_PATH = (new File(ANNOTATORS_PROJECT, "Annotators/neEvaluation")).getPath();

    // UDF path for finding jars of UDF code
    final String UDF_PATH =
        (new File(ANNOTATORS_PROJECT, "Annotators/neEvaluation/GenericNE/udfjars")).getPath();

    setDataPath(DICT_PATH, INCLUDE_PATH, UDF_PATH);

    // setDumpPlan(true);

    setDisableOutput(true);

    // DocScan scan = DocScan.makeFileScan (new File (DOCFILE_NAME));
    runNonModularAQLTest(new File(DOCFILE_NAME), AQLFILE);

    if (TupleList.COLLECT_STATS) {
      TupleList.dumpListStats();
    }

  }

  /**
   * Benchmark the customized ACE annotators on ACE data. Not enabled as a JUnit test because it
   * takes a while.
   */
  public void benchmarkACE() throws Exception {
    startTest();

    if (false == ANNOTATORS_PROJECT.exists()) {
      // SPECIAL CASE: User hasn't checked out a copy of AnnotatorTester.
      System.err.printf("No AnnotatorTester project.  Exiting.\n");
      return;
      // END SPECIAL CASE
    }

    final String DOCFILE_NAME = ACE_DOC_DIR + "/ace2005testingDocOriginal.zip";

    final File AQLFILE =
        new File(ANNOTATORS_PROJECT, "aql/neEvaluation/ne-library-annotators-for-ACE2005.aql");

    // Directory containing auxiliary dictionary files.
    final String DICT_PATH =
        String.format("%s/Annotators/neEvaluation/GenericNE/dictionaries", ANNOTATORS_PROJECT);

    // Include path for finding auxiliary files.
    final String INCLUDE_PATH = (new File(ANNOTATORS_PROJECT, "Annotators/neEvaluation")).getPath();

    // UDF path for finding jars of UDF code
    final String UDF_PATH =
        (new File(ANNOTATORS_PROJECT, "Annotators/neEvaluation/GenericNE/udfjars")).getPath();

    setDataPath(DICT_PATH, INCLUDE_PATH, UDF_PATH);

    // setDumpPlan(true);

    setDisableOutput(true);

    // DocScan scan = DocScan.makeFileScan (new File (DOCFILE_NAME));
    runNonModularAQLTest(new File(DOCFILE_NAME), AQLFILE);

    if (TupleList.COLLECT_STATS) {
      TupleList.dumpListStats();
    }

  }

  /**
   * Benchmark the customized EnronMeetings annotators on EnronMeetings data. Not enabled as a JUnit
   * test because it takes a while.
   */
  public void benchmarkEnronMeetings() throws Exception {

    if (false == ANNOTATORS_PROJECT.exists()) {
      // SPECIAL CASE: User hasn't checked out a copy of AnnotatorTester.
      System.err.printf("No AnnotatorTester project.  Exiting.\n");
      return;
      // END SPECIAL CASE
    }

    final String DOCFILE_NAME = ENRON_MEETINGS_DOC_DIR + "/EnronMeetingsDocTestBunch4.zip";

    final File AQLFILE = new File(ANNOTATORS_PROJECT,
        "Annotators/neEvaluation/ne-library-annotators-for-EnronMeetings.aql");

    // Directory containing auxiliary dictionary files.
    final String DICT_PATH =
        String.format("%s/Annotators/neEvaluation/GenericNE/dictionaries", ANNOTATORS_PROJECT);

    // Include path for finding auxiliary files.
    final String INCLUDE_PATH = (new File(ANNOTATORS_PROJECT, "Annotators/neEvaluation")).getPath();

    // UDF path for finding jars of UDF code
    final String UDF_PATH =
        (new File(ANNOTATORS_PROJECT, "Annotators/neEvaluation/GenericNE/udfjars")).getPath();

    setDataPath(DICT_PATH, INCLUDE_PATH, UDF_PATH);

    // setDumpPlan(true);

    setDisableOutput(true);

    // DocScan scan = DocScan.makeFileScan (new File (DOCFILE_NAME));
    runNonModularAQLTest(new File(DOCFILE_NAME), AQLFILE);

    if (TupleList.COLLECT_STATS) {
      TupleList.dumpListStats();
    }

  }

  /**
   * Benchmark the NeEval core annotators on CoNLL data. Not enabled as a JUnit test because it
   * takes a while.
   */
  public void benchmarkNeEvalCore() throws Exception {

    if (false == ANNOTATORS_PROJECT.exists()) {
      // SPECIAL CASE: User hasn't checked out a copy of AnnotatorTester.
      System.err.printf("No AnnotatorTester project.  Exiting.\n");
      return;
      // END SPECIAL CASE
    }

    final String DOCFILE_NAME = TestConstants.ENRON_SAMPLE_ZIP;
    // final String DOCFILE_NAME = CONLL_DOC_DIR + "/CoNLL2003testingDocA";
    // final String DOCFILE_NAME = CONLL_DOC_DIR + "/CoNLL2003testingDocB";

    final File AQLFILE =
        new File(ANNOTATORS_PROJECT, "Annotators/neEvaluation/ne-library-annotators.aql");

    // Directory containing auxiliary dictionary files.
    final String DICT_PATH =
        String.format("%s/Annotators/neEvaluation/GenericNE/dictionaries", ANNOTATORS_PROJECT);

    // Include path for finding auxiliary files.
    final String INCLUDE_PATH = (new File(ANNOTATORS_PROJECT, "Annotators/neEvaluation")).getPath();

    // UDF path for finding jars of UDF code
    final String UDF_PATH =
        (new File(ANNOTATORS_PROJECT, "Annotators/neEvaluation/GenericNE/udfjars")).getPath();

    setDataPath(DICT_PATH, INCLUDE_PATH, UDF_PATH);

    // setDumpPlan(true);

    setDisableOutput(true);

    // DocScan scan = DocScan.makeFileScan (new File (DOCFILE_NAME));
    runNonModularAQLTest(new File(DOCFILE_NAME), AQLFILE);

    if (TupleList.COLLECT_STATS) {
      TupleList.dumpListStats();
    }

  }

  /**
   * Benchmark the annotators that ship with eDA version 2; these annotators are different from the
   * ones that we maintain internally.
   */
  public void benchmarkEDA2() throws Exception {

    // The AQL files pulled from the eDA build are stored under
    // testdata/aql/memoryTests/eDA
    final String ROOT_DIR = MemoryTests.AQL_FILES_DIR + "/eDA";
    final String AQL_FILE = ROOT_DIR + "/ne-ediscovery-personorgphoneaddress.aql";

    final String DICTS_DIR_NAME = ROOT_DIR;

    final String DOCFILE_NAME = TestConstants.ENRON_SAMPLE_ZIP;
    // final String DOCFILE_NAME = TestConstants.ENRON_1K_DUMP;

    setDataPath(DICTS_DIR_NAME, ROOT_DIR, null);

    // DocScan scan = DocScan.makeFileScan (new File (DOCFILE_NAME));
    runNonModularAQLTest(new File(DOCFILE_NAME), new File(AQL_FILE));

  }

  /**
   * Test of the main() method in {@link AQLProfiler}.
   */
  @Test
  public void testProfilerMain() throws Exception {
    startTest();

    try {
      final String AQL_FILE_NAME = TestConstants.AQL_DIR + "/lotus/namedentity.aql";
      final String DATA_PATH = TestConstants.TESTDATA_DIR;

      // Compile the AQL first
      compileAQL(new File(AQL_FILE_NAME), DATA_PATH);

      // profile using main method
      String moduleURI = getCurOutputDir().toURI().toString();
      final String DOCS_FILE_NAME = TestConstants.ENRON_1K_DUMP;
      final String[] ARGS =
          {"-m", Constants.GENERIC_MODULE_NAME, "-p", moduleURI, "-d", DOCS_FILE_NAME};

      AQLProfiler.main(ARGS);
    } catch (Exception e) {
      Assert.fail(e.getMessage());
    }

    endTest();
  }

  /**
   * Test of the main() method in {@link AQLProfiler} with a non-null set of output types to enable.
   */
  @Test
  public void testProfilerMainEnableOutput() throws Exception {
    startTest();
    try {
      final String AQL_FILE_NAME = TestConstants.AQL_DIR + "/lotus/namedentity.aql";
      final String DATA_PATH = TestConstants.TESTDATA_DIR;
      final String DOCS_FILE_NAME = TestConstants.ENRON_1K_DUMP;
      final String OUTPUT_TYPES = "AllCities;Zipcodes;";

      // Compile the AQL first
      compileAQL(new File(AQL_FILE_NAME), DATA_PATH);

      // profile using main method
      String moduleURI = getCurOutputDir().toURI().toString();

      final String[] ARGS = {"-m", Constants.GENERIC_MODULE_NAME, "-p", moduleURI, "-d",
          DOCS_FILE_NAME, "-o", OUTPUT_TYPES};

      AQLProfiler.main(ARGS);
    } catch (Exception e) {
      Assert.fail(e.getMessage());
    }

    endTest();
  }

  /**
   * Benchmark of our built-in whitespace tokenizer.
   */
  @Test
  public void benchmarkTokenizer() throws Exception {

    // File of test documents.
    final File DOCS_FILE = new File(TestConstants.ENRON_SAMPLE_ZIP);

    // Minimum time for which the benchmark should run = 10 seconds
    final long MIN_MSEC = 10000;

    // Read documents into memory to take unzip time out of the equation.
    ArrayList<Tuple> docs = new ArrayList<Tuple>();

    DocReader docReader = new DocReader(DOCS_FILE);
    while (docReader.hasNext()) {
      docs.add(docReader.next());
    }
    Log.info("Read %d documents into memory.", docs.size());

    // Create an accessor for getting at the document text.
    FieldGetter<Text> getText = docReader.getDocSchema().textAcc(Constants.DOCTEXT_COL);

    StandardTokenizer tokenizer = new StandardTokenizer();
    ThreadMXBean bean = ManagementFactory.getThreadMXBean();
    long id = java.lang.Thread.currentThread().getId();
    long startNS = bean.getThreadCpuTime(id);
    long elapsedNs = 0;
    long totalChars = 0;
    long totalTokens = 0;
    while (elapsedNs < (MIN_MSEC * 1e6)) {

      int numDocs = docs.size();
      for (int i = 0; i < numDocs; i++) {
        Tuple doc = docs.get(i);
        Text docText = getText.getVal(doc);
        docText.resetCachedTokens();
        OffsetsList tokens = tokenizer.tokenize(docText);
        totalTokens += tokens.size();
        totalChars += docText.getText().length();
      }

      elapsedNs = bean.getThreadCpuTime(id) - startNS;

      Log.info("%d char and %d tokens in %d ms...", totalChars, totalTokens, elapsedNs / 1000000);
    }

    double elapsedSec = elapsedNs / 1e9;
    double kchar = totalChars / 1024.0;
    double kcharPerSec = kchar / elapsedSec;

    Log.info("%1.1f kchar (%d tokens) in %1.1f sec --> %1.1f kchar/sec", kchar, totalTokens,
        elapsedSec, kcharPerSec);

    // Close the document reader
    docReader.remove();
  }

  /**
   * Test to verify that the unused views (view not reachable thru any of the output view) in the
   * loaded operator graph are not evaluated. We verify this by profiling the extractor containing
   * unused views( views exported but not output), and later looking for the presence/absence of
   * such unused views in the list of views, fetched from the profiler last run.<br>
   * This test captures the scenario mentioned in defect#33676, comment#12 and comment#19.
   * 
   * @throws Exception
   */
  @Test
  public void disableUnusedViewsTest() throws Exception {
    // This view is not reachable thru any of the output view statement; this view should not be
    // execute, hence should
    // not be part of top 25 view returned by profiler.
    String unUsedView = "module1.notSoCheapView";
    startTest();

    OperatorGraph og = compileAndLoadModules(new String[] {"module1", "module2"}, null);

    // Profile the loaded OG for 60 seconds
    AQLProfiler profiler = AQLProfiler.createProfilerFromOG(og);
    profiler.setMinRuntimeSec(60);

    profiler.profile(new File(TestConstants.DUMPS_DIR + "/ensmall.zip"), LangCode.en, null);

    // Views executed in the last profiler run; Unused view should be present in this list.
    HashMap<String, RuntimeRecord> executedViews = profiler.getRuntimeByView();

    assertTrue(String.format("The view named '%s' is not among the top 25 views, as expected.",
        unUsedView), false == executedViews.containsKey(unUsedView));

    endTest();
  }

  /*
   * INTERNAL UTILITY METHODS
   */

  /**
   * Generic function to profile an AQL file.
   * 
   * @param aqlFileName name of the top-level aql file to profile
   * @param docFileName name of file (or dir) containing documents to feed through the annotator
   * @param dictPath search path for dictionary files
   * @param includePath search path for included AQL files
   * @param udfJarPath search path for jars with UDFs
   * @param runDuration minimum time for which profiler should run.
   * @return the profiler object, in case you want to probe for additional stored information.
   */
  private AQLProfiler genericProfile(String aqlFileName, String docFileName, String dictPath,
      String includePath, String udfJarPath, int runDuration) throws Exception {
    // compile the AQL first
    setDataPath(includePath, dictPath, udfJarPath);
    compileAQL(new File(aqlFileName), getDataPath());

    // profile
    String moduleURI = getCurOutputDir().toURI().toString();
    AQLProfiler prof = AQLProfiler.createCompiledModulesProfiler(
        new String[] {Constants.GENERIC_MODULE_NAME}, moduleURI, null, null);
    if (runDuration > 0)
      prof.setMinRuntimeSec(runDuration);

    prof.profile(new File(docFileName), LangCode.en, null);

    Log.info("%d char in %1.2f sec --> %1.2f kb/sec\n", prof.getTotalChar(), prof.getRuntimeSec(), //
        prof.getCharPerSec() / 1024);

    return prof;
  }

  /**
   * Convenient varient of the above method.
   */
  private AQLProfiler genericProfile(String aqlFileName, String docFileName, String dictPath,
      String includePath, String udfJarPath) throws Exception {
    return genericProfile(aqlFileName, docFileName, dictPath, includePath, udfJarPath, 0);
  }

  /**
   * Simplified version of the above methods for annotators without includes or UDFs.
   * 
   * @param aqlFileName name of the aql file
   * @param docFileName
   * @param dictPath
   * @throws Exception
   */
  private void genericProfile(String aqlFileName, String docFileName, String dictPath)
      throws Exception {
    genericProfile(aqlFileName, docFileName, dictPath, null, null);
  }

}
