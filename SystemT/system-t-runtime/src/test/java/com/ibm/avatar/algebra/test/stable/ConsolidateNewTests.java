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

import java.io.File;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.util.file.SearchPath;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;

/**
 * Various regression tests to test optimized consolidator.
 * 
 */
public class ConsolidateNewTests extends RuntimeTestHarness {

  /**
   * Directory where input documents for this test reside.
   */
  private static final String DOCS_DIR = TestConstants.TEST_DOCS_DIR + "/ConsolidateNewTests";

  /**
   * Directory where AQL files for this test reside.
   */
  private static final String AQL_DIR = TestConstants.AQL_DIR + "/ConsolidateNewTests";

  /**
   * A collection of 1000 Enron emails shared by tests in this class.
   */
  public static final File DOCS_FILE = new File(TestConstants.DUMPS_DIR, "ensmall.zip");

  /**
   * A simple made-up document for testing simple cases.
   */
  public static final File SIMPLE_CANNED_DOC_FILE =
      new File(DOCS_DIR, "/notContainedWithinTest.del");

  /**
   * 1 simple Enron email for a real document.
   */
  public static final File SIMPLE_DOC_FILE = new File(TestConstants.DUMPS_DIR, "enron1.del");

  /**
   * Main method for invoking one test at a time.
   * 
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    ConsolidateNewTests t = new ConsolidateNewTests();
    t.setUp();

    long startMS = System.currentTimeMillis();

    t.notContainedWithinEnron1Test();

    long endMS = System.currentTimeMillis();

    t.tearDown();

    double elapsedSec = ((double) (endMS - startMS)) / 1000.0;
    System.err.printf("Test took %1.3f sec.\n", elapsedSec);
  }

  @Before
  public void setUp() {

  }

  @After
  public void tearDown() {

  }

  /**
   * Test case for validating NOT CONTAINED WITHIN policy on a small canned testcase with
   * overlapping spans.
   * 
   * @throws Exception
   */
  @Test
  public void notContainedWithinTest() throws Exception {
    startTest();

    // setDumpPlan(true);
    setDisableOutput(false);
    setPrintTups(true);

    runNonModularAQLTest(SIMPLE_CANNED_DOC_FILE);
    truncateExpectedFiles();
    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Test case for validating NOT CONTAINED WITHIN policy on a small real-world testcase with
   * overlapping spans.
   * 
   * @throws Exception
   */
  @Test
  public void notContainedWithinEnron1Test() throws Exception {
    startTest();

    // setDumpPlan(true);
    setDisableOutput(false);
    setPrintTups(true);

    runNonModularAQLTest(SIMPLE_DOC_FILE);
    truncateOutputFiles(true);
    compareAgainstExpected(false);
    endTest();
  }

  /**
   * Test case for validating NOT CONTAINED WITHIN policy on a reasonable real-world testcase with
   * overlapping spans.
   * 
   * @throws Exception
   */
  @Test
  public void notContainedWithinEnron1kTest() throws Exception {
    startTest();

    // setDumpPlan(true);
    setDisableOutput(false);
    setPrintTups(true);

    runNonModularAQLTest(DOCS_FILE);
    truncateOutputFiles(true);
    compareAgainstExpected(false);
    endTest();
  }

  /*
   * ADD NEW TEST CASES HERE
   */

  /*
   * UTILITY METHODS
   */

}
