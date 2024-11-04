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

import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;

/**
 * Various regression tests involving AQL that extracts ICD9 codes from medical transcripts.
 * 
 */
public class ICD9Tests extends RuntimeTestHarness {

  public static final File AQL_FILES_DIR = new File(TestConstants.AQL_DIR, "icd9Tests");

  /**
   * A small collection of fake medical transcripts, shared by most of the tests in this class.
   */
  public static final File DOCS_FILE = new File(TestConstants.DUMPS_DIR,
      // "mtsamples_100k.zip");
      "mtsamples_nokw.zip");

  // "mtsamples.zip");

  /**
   * Main method for invoking one test at a time.
   * 
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    ICD9Tests t = new ICD9Tests();
    t.setUp();

    long startMS = System.currentTimeMillis();

    t.dictTest();
    // t.lookupTest();
    // t.strippedICD9Test();
    // t.negativeTest();
    // t.negativeTest4();

    long endMS = System.currentTimeMillis();

    t.tearDown();

    double elapsedSec = ((double) (endMS - startMS)) / 1000.0;
    System.err.printf("Test took %1.3f sec.\n", elapsedSec);
  }

  @Before
  public void setUp() {

  }

  // Not sure if db release is really required for this test case
  @After
  public void tearDown() {

  }

  /**
   * Test case that creates a dictionary from the ICD9 codes lookup table, then applies the
   * dictionary to the test documents.
   */
  @Test
  public void dictTest() throws Exception {
    startTest();
    // setDisableOutput(true);
    runNonModularAQLTest(DOCS_FILE);

    // compareAgainstExpected(true);
    // Comparison disabled for now.
    endTest();
  }

  /**
   * Test case that creates a dictionary from the ICD9 codes lookup table, then applies the
   * dictionary to the test documents, then maps the dictionary matches back to their codes.
   */
  @Test
  public void lookupTest() throws Exception {
    startTest();
    // setDumpPlan(true);
    // setDisableOutput(true);
    setPrintTups(true);
    runNonModularAQLTest(DOCS_FILE);

    // compareAgainstExpected(true);
    endTest();
  }

  /**
   * Test case that creates a dictionary from the ICD9 codes lookup table, applies negation, then
   * maps the dictionary matches back to their codes.
   */
  @Test
  public void negativeTest() throws Exception {
    startTest();
    setPrintTups(true);
    runNonModularAQLTest(DOCS_FILE);

    endTest();
  }

  /**
   * Test case that creates a dictionary from the ICD9 codes lookup table, applies negation with
   * span to the test documents, then maps the dictionary matches back to their codes.
   */
  @Test
  public void negativeTest4() throws Exception {
    startTest();
    setPrintTups(true);
    runNonModularAQLTest(DOCS_FILE);

    endTest();
  }

  /**
   * Test case that creates a dictionary from the stripped ICD9 codes lookup table, then applies the
   * dictionary to the test documents, then maps the dictionary matches back to their codes.
   */
  @Test
  public void strippedICD9Test() throws Exception {
    startTest();
    setPrintTups(true);
    runNonModularAQLTest(DOCS_FILE);

    endTest();
  }

  /*
   * UTILITY METHODS
   */

}
