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
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.function.scalar.AutoID;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.api.CompileAQL;
import com.ibm.avatar.api.CompileAQLParams;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.aql.compiler.Compiler;
import com.ibm.avatar.aql.planner.Planner;
import com.ibm.avatar.aql.planner.Planner.ImplType;
import com.ibm.avatar.aql.tam.TAM;
import com.ibm.avatar.aql.tam.TAMSerializer;
import com.ibm.avatar.logging.Log;
import com.ibm.avatar.provenance.AQLProvenanceRewriter;
import com.ibm.avatar.provenance.AQLRefine;

/**
 * Tests for AQLRefine. Some code borrowed from ProvenanceRewriteTests.
 */

public class AQLRefineTests extends RuntimeTestHarness {

  /**
   * Directory where AQL files referenced in this class's regression tests are located.
   */
  public static final String AQL_FILES_DIR = TestConstants.AQL_DIR + "/refineTests";

  /**
   * Directory where the dictionary files are located.
   */
  public static final String DICTS_DIR = AQL_FILES_DIR + "/dictionaries";

  public static void main(String[] args) {
    try {

      AQLRefineTests t = new AQLRefineTests();

      t.setUp();

      long startMS = System.currentTimeMillis();

      // t.selectTest();
      // t.subqueryTest();
      // t.unionTest();
      // t.consolidateTest();
      // t.minusTest();

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
  private String docLocation;
  private int tupleID;
  private final boolean runProvQuery = false;

  @Before
  public void setUp() throws Exception {

    // Renice the current thread to avoid locking up the entire system.
    Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

    docLocation = TestConstants.ENRON_SMALL_ZIP;

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
   * Test case for the rewrite of the SELECT statement
   * 
   * @Test public void selectTest() throws Exception { tupleID = 818; genericTestCase("select");
   *       //util.compareAgainstExpected(true); }
   * @Test public void unionTest() throws Exception { tupleID = 230; genericTestCase("union");
   *       //util.compareAgainstExpected(true); }
   * @Test public void subqueryTest() throws Exception { tupleID = 797; genericTestCase("subquery");
   *       //util.compareAgainstExpected(true); }
   * @Test public void consolidateTest() throws Exception { tupleID = 828;
   *       genericTestCase("consolidate"); //util.compareAgainstExpected(true); }
   * @Test public void minusTest() throws Exception { tupleID = 829; genericTestCase("minus");
   *       //util.compareAgainstExpected(true); }
   * @Test public void refineFromWebTest() throws Exception{ }
   */
  /**
   * Version of {@link #genericTestCase(String, ImplType)} that uses the default planner
   * implementation.
   */
  @SuppressWarnings("unused")
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
    File OUTPUT_DIR = getCurOutputDir();
    File INPUT_AQLFILE = new File(String.format("%s/%s.aql", AQL_FILES_DIR, prefix));
    File CHANGE_FILE = new File(String.format("%s/%s/%s_Changes.txt", OUTPUT_DIR, prefix, prefix));
    File REWRITTEN_AQLFILE =
        new File(String.format("%s/%s/%s_Rewrite.aql", OUTPUT_DIR, prefix, prefix));

    // Rewrite the input AQL
    AutoID.resetIDCounter();
    ArrayList<Integer> idList = new ArrayList<Integer>();
    idList.add(tupleID);
    idList.add(830);
    AQLRefine refiner = new AQLRefine(idList, INPUT_AQLFILE, CHANGE_FILE, docLocation);

    Log.info("Setting dict path to '%s'", getDataPath());

    refiner.setDictPath(getDataPath());

    AQLProvenanceRewriter rewriter = new AQLProvenanceRewriter();
    // FIXME: Now that outputFile is removed from CompileAQLParams, AQLProvenanceRewriter should use
    // alternate way of
    // passing rewrittenAQLFile parameter to rewriteAQL() method. Temporarily passing some arbitrary
    // value to allow
    // compiler to succeed.
    CompileAQLParams params =
        new CompileAQLParams(INPUT_AQLFILE, REWRITTEN_AQLFILE.toURI().toString(), getDataPath());
    rewriter.rewriteAQL(params, refiner.getBaseViews());

    refiner.addBaseViews(refiner.getBaseViews());

    refiner.autoRefine(REWRITTEN_AQLFILE.getAbsolutePath(), rewriter, AQL_FILES_DIR, getDataPath(),
        null, null, docLocation, null, false, System.err);

    refiner = null;

    if (runProvQuery) {
      // run the rewritten file to see the output

      // Parse the rewritten AQL.
      Log.info("Compiling rewritten AQL file '%s'", REWRITTEN_AQLFILE.getPath());

      // Prepare compile parameter
      CompileAQLParams compileParam = new CompileAQLParams();
      String dataPathStr = getDataPath();
      compileParam.setDataPath(dataPathStr);
      compileParam.setInputFile(REWRITTEN_AQLFILE);

      // Compile
      Compiler compiler = new Compiler();
      compiler.setPlanner(new Planner(implType));
      CompileAQL.compile(compileParam);

      // Load tam
      TAM tam = TAMSerializer.load(Constants.GENERIC_MODULE_NAME, compileParam.getOutputURI());
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

      // Try running the plan.
      try {

        // TODO : restore the call to run AOG String , this time using new test harness -- eyhung
        // util.runAOGStr(scan, aog, allCompiledDicts);
        // runAOGString(aog, null);
      } catch (Exception e) {
        System.err.printf("Caught exception while parsing AOG; " + "original AOG plan was:\n%s\n",
            aog);
        throw new RuntimeException(e);
      }
    }
  }

  // This method provides a single place to turn truncatation on or off.
  // private void doComparisons() throws Exception {
  // // util.truncateExpectedFiles();
  // util.compareAgainstExpected(true);
  // }

  /*
   * Dummy test required to avoid failure of ant junit task testSystemTRuntime due to
   * "no runnable methods". Remove the test when the class contains at least one "real" test.
   */
  @Test
  public void dummyTest() throws Exception {}

}
