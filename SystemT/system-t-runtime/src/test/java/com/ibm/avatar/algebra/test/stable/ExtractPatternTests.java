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
 * Various regression tests involving sequence patterns. Mainly intended to test the sequence
 * pattern extension for passing through additional columns.
 * 
 */
public class ExtractPatternTests extends RuntimeTestHarness {

  /**
   * A collection of 1000 Enron emails shared by tests in this class.
   */
  public static final File DOCS_FILE = new File(TestConstants.DUMPS_DIR, "ensmall.zip");

  /**
   * Main method for invoking one test at a time.
   * 
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    ExtractPatternTests t = new ExtractPatternTests();
    t.setUp();

    long startMS = System.currentTimeMillis();

    t.havingValidationTest();

    long endMS = System.currentTimeMillis();

    t.tearDown();

    double elapsedSec = (double) ((endMS - startMS)) / 1000.0;
    System.err.printf("Test took %1.3f sec.\n", elapsedSec);
  }

  @Before
  public void setUp() {

  }

  @After
  public void tearDown() {

  }

  /**
   * Rest of {@link AQLEnronTests#sequencePatternTest()} that requires detagging support so it
   * cannot be included in AQLEnronTests because the build runs that test with only systemT.jar in
   * the classpath (i.e., no detagging support).
   */
  @Test
  public void sequencePatternEnronTest() throws Exception {
    startTest();
    // setDumpPlan(true);
    // setDisableOutput(true);
    setPrintTups(true);
    runNonModularAQLTest(DOCS_FILE);

    // truncateExpectedFiles ();
    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Test case for passing through columns from a single PatternAtomColNode
   */
  @Test
  public void simpleColPassThroughTest() throws Exception {
    startTest();
    // setDumpPlan(true);
    // setDisableOutput(true);
    setPrintTups(true);
    runNonModularAQLTest(DOCS_FILE);

    // truncateExpectedFiles ();
    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Test case for passing through columns from a PatternAlternationNode
   */
  @Test
  public void alternationColPassThroughTest() throws Exception {
    startTest();
    // setDumpPlan(true);
    // setDisableOutput(true);
    setPrintTups(true);
    runNonModularAQLTest(DOCS_FILE);

    // truncateExpectedFiles ();
    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Test case for passing through columns from a PatternSequenceNode or a PatternOptionalNode
   */
  @Test
  public void sequenceColPassThroughTest() throws Exception {
    startTest();
    // setDumpPlan(true);
    // setDisableOutput(true);
    setPrintTups(true);
    runNonModularAQLTest(DOCS_FILE);

    // truncateExpectedFiles ();
    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Test case for passing through columns from more complex patterns consisting of a mix of
   * sequence, optional and alternation elements.
   */
  @Test
  public void mixedColPassThroughTest() throws Exception {
    startTest();
    // setDumpPlan(true);
    // setDisableOutput(true);
    setPrintTups(true);
    runNonModularAQLTest(DOCS_FILE);

    // truncateExpectedFiles ();
    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Test case for passing through columns from a PatternSequenceNode
   */
  @Test
  public void sequencePatternTest() throws Exception {
    startTest();
    // setDumpPlan(true);
    // setDisableOutput(true);
    setPrintTups(true);
    runNonModularAQLTest(null);

    // truncateExpectedFiles ();
    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Tests copied from {@link AQLEnronTests#sequencePatternTest()} and modified to pass through
   * various columns from the input.
   */
  @Test
  public void enronSequencePatternTest() throws Exception {
    startTest();
    // setDumpPlan(true);
    // setDisableOutput(true);
    setPrintTups(true);
    runNonModularAQLTest(DOCS_FILE);

    // truncateOutputFiles (false);
    // truncateExpectedFiles ();
    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Test case for allowing sequence patterns that can match empty spans GHE #53
   *
   */
  @Test
  public void sequencePatternEmptySpanTest() throws Exception {
    startTest();
    // setDumpPlan(true);
    // setDisableOutput(true);
    setPrintTups(true);
    runNonModularAQLTest(null);

    // truncateExpectedFiles ();
    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Test case for parsing extract pattern statements with pass through attributes. Verifies that
   * parsing returns the appropriate parse error in various cases.
   * 
   * @throws Exception
   */
  @Test
  public void parseTest() throws Exception {
    startTest();

    // Expect exceptions at these locations
    int[] lineNo = new int[] {24, 27, 31, 35};
    int[] colNo = new int[] {28, 13, 9, 13};
    compileAQLTest(lineNo, colNo);

    endTest();
  }

  /**
   * Test case for validating the select list of a PatternNode wrt the FROM clause.
   * 
   * @throws Exception
   */
  @Test
  public void selectListValidationTest() throws Exception {
    startTest();

    // Expect exceptions at these locations
    int[] lineNo = new int[] {20, 28, 45, 53, 61, 70, 78, 87, 96};
    int[] colNo = new int[] {11, 11, 9, 9, 9, 11, 13, 13, 13};
    compileAQLTest(lineNo, colNo);

    endTest();
  }

  /**
   * Test cases for extension to sequence patterns to pass through attributes. Verifies that extra
   * items in select list do not contain a col reference if the pattern is simple (i.e., it contains
   * atoms of type: regex, dict or token gap, and does not contain any column references.
   * 
   * @throws Exception
   */
  @Test
  public void selectListNoColRefValidationTest() throws Exception {
    startTest();

    // Expect exceptions at these locations
    int[] lineNo = new int[] {26, 35, 44, 53};
    int[] colNo = new int[] {1, 1, 1, 9};
    compileAQLTest(lineNo, colNo);

    endTest();
  }

  /**
   * Test cases for extension to sequence patterns to pass through attributes. Verifies that extra
   * items in select list that do not contain a col reference are rewritten properly if the pattern
   * is simple (i.e., it contains atoms of type: regex, dict or token gap, and does not contain any
   * column references.
   * 
   * @throws Exception
   */
  @Test
  public void selectListNoColRefPassThroughTest() throws Exception {
    startTest();
    // setDumpPlan(true);
    // setDisableOutput(true);
    setPrintTups(true);
    runNonModularAQLTest(DOCS_FILE);

    // truncateOutputFiles (false);
    // truncateExpectedFiles ();
    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Test case for validating the PATTERN clause of a PatternNode.
   * 
   * @throws Exception
   */
  @Test
  public void patternValidationTest() throws Exception {
    startTest();

    // Expect exceptions at these locations
    int[] lineNo = new int[] {28, 40, 52, 60};
    int[] colNo = new int[] {1, 1, 1, 1};
    compileAQLTest(lineNo, colNo);

    endTest();
  }

  /**
   * Test case for validating the HAVING clause of a PatternNode wrt the select list and the pattern
   * return clause.
   * 
   * @throws Exception
   */
  @Test
  public void havingValidationTest() throws Exception {
    startTest();

    // Expect exceptions at these locations
    int[] lineNo = new int[] {23};
    int[] colNo = new int[] {8};
    compileAQLTest(lineNo, colNo);

    endTest();
  }

  /**
   * Test case for validating the CONSOLIDATE clause of a PatternNode wrt the select list and the
   * pattern return clause.
   * 
   * @throws Exception
   */
  @Test
  public void consolidateValidationTest() throws Exception {
    startTest();

    // Expect exceptions at these locations
    int[] lineNo = new int[] {24, 34, 53, 53};
    int[] colNo = new int[] {16, 26, 26, 63};
    compileAQLTest(lineNo, colNo);

    endTest();
  }

  /**
   * Test case for validating that we cannot pass through columns from under repeatable elements of
   * the pattern expression.
   * 
   * @throws Exception
   */
  @Test
  public void passThroughFromRepeatValidationTest() throws Exception {
    startTest();

    // Expect exceptions at these locations
    int[] lineNo = new int[] {20};
    int[] colNo = new int[] {1};
    compileAQLTest(lineNo, colNo);

    endTest();
  }

  /**
   * Test case for validating that expansion of wildcards and alias inference work for PatternNodes.
   * 
   * @throws Exception
   */
  @Test
  public void expandInferAliasValidateTest() throws Exception {
    startTest();

    // Expect exceptions at these locations
    int[] lineNo = new int[] {27, 30, 40, 43, 50};
    int[] colNo = new int[] {9, 4, 9, 4, 27};
    compileAQLTest(lineNo, colNo);

    endTest();
  }

  /**
   * Test case for validating that errors from PatternNode subqueries are surfaced with the correct
   * location.
   * 
   * @throws Exception
   */
  @Test
  public void subqueryCompileExceptionTest() throws Exception {
    startTest();

    // Expect exceptions at these locations
    int[] lineNo = new int[] {25};
    int[] colNo = new int[] {9};
    compileAQLTest(lineNo, colNo);

    endTest();
  }

  /**
   * Test cases for defect : Sequence pattern rewrite generates duplicate views
   * 
   * @throws Exception
   */
  @Test
  public void duplicateViewsTest() throws Exception {
    startTest();
    // setDumpPlan(true);
    // setDisableOutput(true);
    setPrintTups(true);
    runNonModularAQLTest(DOCS_FILE);

    // truncateOutputFiles (false);
    // truncateExpectedFiles ();
    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Test cases for defect : Type inference incomplete for with inline_match clause of extract
   * pattern statement.
   * 
   * @throws Exception
   */
  @Test
  public void requireDocColsTest() throws Exception {
    startTest();
    // Expect exceptions at these locations
    int[] lineNo = new int[] {13, 23};
    int[] colNo = new int[] {9, 39};
    compileAQLTest(lineNo, colNo);
    endTest();
  }

  /*
   * ADD NEW TEST CASES HERE
   */

  /*
   * UTILITY METHODS
   */

}
