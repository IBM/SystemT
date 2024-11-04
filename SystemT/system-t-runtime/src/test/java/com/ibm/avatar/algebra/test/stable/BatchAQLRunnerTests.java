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

import java.sql.DriverManager;
import java.sql.SQLException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.api.BatchAQLRunner;

/**
 * Tests of the BatchAQLRunnerClass.
 */
public class BatchAQLRunnerTests extends RuntimeTestHarness {

  public static void main(String[] args) throws Exception {

    BatchAQLRunnerTests t = new BatchAQLRunnerTests();

    t.setUp();

    long startMS = System.currentTimeMillis();

    t.batchTest();
    // t.batchTestMT();
    // t.spockTest();

    long endMS = System.currentTimeMillis();

    double elapsedSec = ((double) (endMS - startMS)) / 1000.0;

    System.err.printf("Test took %1.3f sec.\n", elapsedSec);

    t.tearDown();
  }

  @Before
  public void setUp()
      throws SQLException, IllegalAccessException, InstantiationException, ClassNotFoundException {

    // Renice the current thread to avoid locking up the entire system.
    Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

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

    // Restore original value to avoid screwing with other tests.
    // Planner.restoreDefaultSentence();
  }

  // Dummy function to ensure that this file gets compiled whenever
  // BatchAQLRunner changes.
  public void dummy() throws Exception {
    BatchAQLRunner b = new BatchAQLRunner("never called", null, null, "never called", 0);
    b.toString();
  }

  /**
   * Basic test of the batch interface. Runs single-threaded and verifies outputs.
   */
  @Test
  public void batchTest() throws Exception {
    startTest();

    final String DOCSFILE = TestConstants.ENRON_1K_DUMP;
    final String DICTSDIR = TestConstants.TESTDATA_DIR;
    final String AQLFILE = TestConstants.AQL_DIR + "/lotus/namedentity.aql";
    final String NTHREADS_STR = "1";

    int prevDocsPerFile = BatchAQLRunner.DOCS_PER_OUTPUT_FILE;
    BatchAQLRunner.DOCS_PER_OUTPUT_FILE = 500;
    // System.out.println (getCurOutputDir ());

    BatchAQLRunner.main(
        new String[] {DOCSFILE, DICTSDIR, getCurOutputDir().toString(), AQLFILE, NTHREADS_STR});

    // We're running single-threaded, so the outputs should always be the
    // same.
    // Compare output results against what we expected to get.
    compareAgainstExpected(true);

    // Old comparison code
    // final String[] OUTPUT_NAMES = { "AllCities", "AllStates", "InternetEmail", "NotesEmail",
    // "Organization", "Person",
    // "PersonalNotesEmail", "PersonSingleToken", "PhoneNumber", "Place", "URL", "Zipcodes" };
    // final int FILES_PER_NAME = 2;
    //
    // for (String name : OUTPUT_NAMES) {
    // for (int i = 0; i < FILES_PER_NAME; i++) {
    // String outfileName = String.format ("%s%03d.csv", name, i);
    // compareAgainstExpected (outfileName, false);
    // }
    // }

    // Put previous value back in place.
    BatchAQLRunner.DOCS_PER_OUTPUT_FILE = prevDocsPerFile;
  }

  /**
   * Multithreaded version of {@link #batchTest()}. Doesn't verify outputs, since they change
   * depending on order of execution.
   */
  @Test
  public void batchTestMT() throws Exception {
    startTest();

    final String DOCSFILE = TestConstants.ENRON_1K_DUMP;
    final String DICTSDIR = TestConstants.TESTDATA_DIR;
    final String AQLFILE = TestConstants.AQL_DIR + "/lotus/namedentity.aql";
    final String NTHREADS_STR = "16";

    BatchAQLRunner.main(
        new String[] {DOCSFILE, DICTSDIR, getCurOutputDir().toString(), AQLFILE, NTHREADS_STR});

    endTest();
  }

  /**
   * A dry run for the Spock demo; uses detagged web pages from the spock data set and multiple
   * threads.
   */
  @Test
  public void spockTest() throws Exception {
    startTest();

    final String DOCSFILE = TestConstants.SPOCK_100_DUMP;
    final String DICTSDIR = TestConstants.TESTDATA_DIR;
    // TestConstants.RESOURCES_DIR + "/Notes8Resources/resources";
    final String AQLFILE = TestConstants.AQL_DIR + "/lotus/namedentity-spock.aql";
    final String NTHREADS_STR = "16";

    BatchAQLRunner.main(
        new String[] {DOCSFILE, DICTSDIR, getCurOutputDir().toString(), AQLFILE, NTHREADS_STR});

    endTest();
  }
}
