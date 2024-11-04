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
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Test;

import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.api.CompilationSummary;
import com.ibm.avatar.api.CompileAQLParams;
import com.ibm.avatar.api.exceptions.CompilerException;
import com.ibm.avatar.aql.AQLParser;
import com.ibm.avatar.aql.ExtendedParseException;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.StatementList;
import com.ibm.avatar.aql.compiler.CompilerWarning.WarningType;

/**
 * Test cases on AQL compile error.
 * 
 */
public class AQLCompileErrorTests extends RuntimeTestHarness {

  private static final String AQL_FILES_DIR = TestConstants.AQL_DIR + "/aqlCompileErrorTests";

  /**
   * Main method for invoking one test at a time.
   * 
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    AQLCompileErrorTests t = new AQLCompileErrorTests();
    // t.setUp ();

    long startMS = System.currentTimeMillis();

    t.multipleErrorWithCorrectErrorLocation();

    long endMS = System.currentTimeMillis();

    // t.tearDown ();

    double elapsedSec = ((double) (endMS - startMS)) / 1000.0;
    System.err.printf("Test took %1.3f sec.\n", elapsedSec);
  }

  // Parser error tests starts

  /**
   * Test to assert compile error line number when there is a missing token (e.g ; ) and next token
   * is on following line column 1
   */
  @Test
  public void testCompileAQLMissingSemicolon() {
    genericAQLParseTest(new int[] {12, 16}, new int[] {1, 21}, "missingSemicolon");

  }

  /**
   * Test to assert compile error line number when there is a unknown token/miss spelled token
   */
  @Test
  public void testCompileAQLMisspelledToken() {
    genericAQLParseTest(new int[] {12}, new int[] {-1}, "misspelledToken");
  }

  /**
   * Test to verify that we throw ParseException for syntactically invalid regular expressions in
   * extract regex statements, when the regex is parsed as RegexLiteral.
   */
  @Test
  public void regexLiteralParseException() {
    genericAQLParseTest(new int[] {8}, new int[] {26}, "regexLiteralParseException");
  }

  /**
   * Test to verify that we throw ParseException for syntactically invalid regular expressions in
   * extract regex statements, when the regex is parsed as PureRegexLiteral.
   */
  @Test
  public void pureRegexLiteralParseException() {
    genericAQLParseTest(new int[] {9}, new int[] {20}, "pureRegexLiteralParseException");
  }

  /**
   * Test to verify that we throw ParseException for syntactically invalid regular expressions in
   * extract regex statements, when the regex is parsed as StringLiteral.
   */
  @Test
  public void stringRegexLiteralParseException() {
    genericAQLParseTest(new int[] {7}, new int[] {27}, "stringRegexLiteralParseException");
  }

  /**
   * Testcase for defect# 15933: Compile error - SelectListItemNode constructor should have detected
   * this error Test to assert multiple parse error when AQL contains unknown top level statement.
   */
  @Test
  public void unknownTopLevelStatementTest() {
    genericAQLParseTest(new int[] {6, 27, 43}, new int[] {-1, -1, -1},
        "unknownTopLevelStatementTest");
  }

  /**
   * Testcase for defect# 16863: SystemT Tools: AQL Editor: Error message contains "Java String"
   * instead of AQL file name This testcase asserts the correct filename in error message when
   * parser is invoked by passing aql as string and containing aql filename.
   */
  @Test
  public void incorrectFilenameInErrorMsg() {

    String aqlString = "create view The as \n" + "select R.match as the \n"
        + "from Regex(/the/, DocScan.text) R;\n" + "\n" + "selec T.wrongColumnName as the\n"
        + "into myoutput\n" + "from The T;\n";

    AQLParser parser = new AQLParser(aqlString, "dummyFileName");

    StatementList statementList = parser.parse();

    LinkedList<ParseException> parseErrors = statementList.getParseErrors();
    if (parseErrors.size() == 0)
      Assert.fail();

    String errorMsg = parseErrors.get(0).getMessage();

    assertTrue(errorMsg.contains("dummyFileName"));
  }

  // Uncomment this testcase once defect is fixed
  /**
   * Testcase for defect# 14453: AQL compilation error is displayed on another line for a
   * compilation error on a token after create Test case to assert correct parse error line number
   * in aql for an unknown token after create in any of the create statement
   */
  @Test
  public void unknownTokenAfterCreate() {
    genericAQLParseTest(new int[] {10}, new int[] {-1}, "unknownTokenAfterCreate");
  }

  // Uncomment this testcase once defect is fixed
  /**
   * Testcase for defect# 16669: Multiline comment when used to comment a single line gives
   * compilation error. Testcase to assert that there are no parse error while using multi line
   * comment style (C style of comment) to comment one line.
   */
  @Test
  public void parseErrorForMultiLineComment() {
    genericAQLParseTest(null, null, "parseErrorMultiLineComment");
  }

  // Parser error tests ends

  // Compiler error tests starts

  /**
   * Test case to test compiler throwing multiple compile errors with correct error locations like
   * aql filename and error line number
   * 
   * @throws Exception
   */
  @Test
  public void multipleErrorWithCorrectErrorLocation() throws Exception {
    startTest();

    try {
      genericAQLCompileTest("multipleErrorWithCorrectErrorloc");
    } catch (CompilerException e) {
      System.err.printf("Received errors:\n%s\n", e);

      // Make sure that we got
      List<Exception> compileErrors = e.getSortedCompileErrors();
      ParseException error = (ParseException) compileErrors.get(0);
      String erroneousFileName = ((ExtendedParseException) error).getFileName();
      assertTrue(erroneousFileName.contains("multipleErrorWithCorrectErrorloc.aql"));
      assertEquals(41, error.getLine());

      error = (ParseException) compileErrors.get(1);
      assertEquals(49, error.getLine());

      error = (ParseException) compileErrors.get(2);
      assertEquals(57, error.getLine());

      error = (ParseException) compileErrors.get(3);
      assertEquals(63, error.getLine());

      error = (ParseException) compileErrors.get(4);
      assertEquals(67, error.getLine());

      error = (ParseException) compileErrors.get(5);
      assertEquals(75, error.getLine());

      error = (ParseException) compileErrors.get(6);
      assertEquals(101, error.getLine());

      error = (ParseException) compileErrors.get(7);
      assertEquals(115, error.getLine());

      error = (ParseException) compileErrors.get(8);
      assertEquals(121, error.getLine());

      error = (ParseException) compileErrors.get(9);
      assertEquals(127, error.getLine());

      error = (ParseException) compileErrors.get(10);
      assertEquals(137, error.getLine());

      error = (ParseException) compileErrors.get(11);
      assertEquals(162, error.getLine());

      return;
    }
    Assert.fail("This test should have caught an exception.");
  }

  /**
   * Test to assert compile error line number when there is a unknown function call
   */
  @Test
  public void testCompileAQLHavingUnknownFunction() throws Exception {
    startTest();

    try {
      genericAQLCompileTest("unknownFunction");
    } catch (CompilerException e) {
      System.err.printf("Got CompilerException as expected: %s\n", e);

      // Make sure that we got exactly the exceptions we expected.
      List<Exception> compileErrors = e.getAllCompileErrors();

      // for (Exception exception : compileErrors) {
      // exception.printStackTrace ();
      // }

      ParseException error1 = (ParseException) compileErrors.get(0);
      String erroneousFileName = ((ExtendedParseException) error1).getFileName();
      assertTrue(erroneousFileName.contains("unknownFunction.aql"));
      assertEquals(14, error1.getLine());

      ParseException error2 = (ParseException) compileErrors.get(1);
      assertEquals(31, error2.getLine());

      ParseException error3 = (ParseException) compileErrors.get(2);
      assertEquals(35, error3.getLine());

      return;
    }
    Assert.fail("This test should have caught an exception.");
  }

  /**
   * Test to assert compile error due to ambiguous reference to dictionary file name in create view
   * statement
   */
  @Test
  public void testCompileAQLHavingAmbiguousFilename1() throws Exception {
    startTest();

    try {
      genericAQLCompileTest("ambiguousDictFilename1");
    } catch (CompilerException e) {
      // Make sure that we got
      List<Exception> compileErrors = e.getAllCompileErrors();

      ParseException error1 = (ParseException) compileErrors.get(0);
      String erroneousFileName = ((ExtendedParseException) error1).getFileName();
      assertTrue(erroneousFileName.contains("ambiguousDictFilename1.aql"));
      assertEquals(10, error1.getLine());

      return;
    }
    Assert.fail("This test should have caught an exception.");

  }

  /**
   * Test to assert compile error due to ambiguous reference to dictionary file name in create
   * dictionary statement
   */
  @Test
  public void testCompileAQLHavingAmbiguousFilename2() throws Exception {
    startTest();

    try {
      genericAQLCompileTest("ambiguousDictFilename2");
    } catch (CompilerException e) {
      // Make sure that we got
      List<Exception> compileErrors = e.getAllCompileErrors();

      ParseException error1 = (ParseException) compileErrors.get(0);
      String erroneousFileName = ((ExtendedParseException) error1).getFileName();
      assertTrue(erroneousFileName.contains("ambiguousDictFilename2.aql"));
      assertEquals(10, error1.getLine());

      return;
    }
    Assert.fail("This test should have caught an exception.");
  }

  /**
   * Test to assert compile error due to ambiguous reference to dictionary file name in ContainsDict
   * function call
   */
  @Test
  public void testCompileAQLHavingAmbiguousFilename3() throws Exception {
    startTest();

    try {
      genericAQLCompileTest("ambiguousDictFilename3");
    } catch (CompilerException e) {
      // Make sure that we got
      List<Exception> compileErrors = e.getAllCompileErrors();

      ParseException error1 = (ParseException) compileErrors.get(0);
      String erroneousFileName = ((ExtendedParseException) error1).getFileName();
      assertTrue(erroneousFileName.contains("ambiguousDictFilename3.aql"));
      assertEquals(18, error1.getLine());

      return;
    }
    Assert.fail("This test should have caught an exception.");

  }

  /**
   * Test to assert compile error due to ambiguous reference to dictionary file name in MatchesDict
   * function call
   */
  @Test
  public void testCompileAQLHavingAmbiguousFilename4() throws Exception {
    startTest();

    try {
      genericAQLCompileTest("ambiguousDictFilename4");
    } catch (CompilerException e) {
      // Make sure that we got
      List<Exception> compileErrors = e.getAllCompileErrors();
      ParseException error1 = (ParseException) compileErrors.get(0);
      String erroneousFileName = ((ExtendedParseException) error1).getFileName();
      assertTrue(erroneousFileName.contains("ambiguousDictFilename4.aql"));
      assertEquals(12, error1.getLine());

      return;
    }
    Assert.fail("This test should have caught an exception.");

  }

  /**
   * Test to assert that inferring alias does not happen in case of view not found.
   */
  @Test
  public void testCompileAQLInferingViewNotDefined() throws Exception {
    startTest();

    try {
      genericAQLCompileTest("inferingAliasForViewWithCompilationError");
    } catch (CompilerException e) {
      // Make sure that we got the expected errors
      List<Exception> compileErrors = e.getSortedCompileErrors();

      System.err.printf("Errors are: %s\n", compileErrors);

      ParseException error1 = (ParseException) compileErrors.get(0);

      String erroneousFileName = error1.getFileName();

      assertTrue(erroneousFileName.contains("inferingAliasForViewWithCompilationError.aql"));
      assertEquals(8, error1.getLine());

      // ParseException error2 = (ParseException) compileErrors.get (1);
      // assertEquals (8, error2.getLine ());

      return;
    }
    Assert.fail("A compile exception was expected");
  }

  /**
   * Test case for defect# 16182: AQL compilation shows error for ambiguous aql even when the aql is
   * not included in the search path This testcase will assert that system will not add main aql
   * file's parent directory to data path unless given data path is null.
   * 
   * @throws Exception
   */
  @Test
  public void testCompileAQLWithNotNullDataPath() throws Exception {
    startTest();

    try {
      genericAQLCompileTest("aqlIncludingFileFromCurrentDirectory");
    } catch (CompilerException e) {
      List<Exception> compileErrors = e.getAllCompileErrors();
      ParseException error1 = (ParseException) compileErrors.get(0);
      assertEquals(12, error1.getLine());

      return;
    }
    Assert.fail("A compile exception was expected");
  }

  /**
   * Test case for defect# 17124: Get multiple compilation errors for the same line. Test case to
   * assert error location when Dictionary/RegexTok function refer to an view not defined
   */
  @Test
  public void testMissingErrorLocationForDictionaryFunc() throws Exception {
    startTest();

    try {
      genericAQLCompileTest("aqlWithCallToDictionaryFunction");
    } catch (CompilerException e) {
      List<Exception> compileErrors = e.getAllCompileErrors();
      ParseException error1 = (ParseException) compileErrors.get(0);
      assertEquals(13, error1.getLine());

      ParseException error2 = (ParseException) compileErrors.get(2);
      assertEquals(17, error2.getLine());

      return;
    }
    Assert.fail("A compile exception was expected");
  }

  @Test
  public void testMultipleValidationErrorFromGroupByClause() throws Exception {
    startTest();

    try {
      genericAQLCompileTest("multipleValidationErrorFromGroupByClause");
    } catch (CompilerException e) {
      List<Exception> compileErrors = e.getAllCompileErrors();
      ParseException error1 = (ParseException) compileErrors.get(0);
      assertEquals(28, error1.getLine());
      assertEquals(10, error1.getColumn());

      ParseException error2 = (ParseException) compileErrors.get(1);
      assertEquals(28, error2.getLine());
      assertEquals(29, error2.getColumn());

      return;
    }
    Assert.fail("A compile exception was expected");
  }

  @Test
  public void testIncorrectSelectListWRTGroupByClause() throws Exception {
    startTest();

    try {
      genericAQLCompileTest("incorrectSelectListWRTGroupByClause");
    } catch (CompilerException e) {
      List<Exception> compileErrors = e.getAllCompileErrors();
      ParseException error1 = (ParseException) compileErrors.get(0);
      assertEquals(21, error1.getLine());
      assertEquals(28, error1.getColumn());

      // Moved the validation of select list wrt Group By in the catalog validation stage so that we
      // do the validation
      // after wildcard expansion and alias inference. In the catalog validation code (i.e., {@link
      // Catalog#validateStmt()}, we do not collect multiple errors, but throw the first error
      // instead. Re-enable
      // the next 3 lines once the catalog validation is modified to collect multiple errors.
      // ParseException error2 = (ParseException) compileErrors.get (1);
      // assertEquals (21, error2.getLine ());
      // assertEquals (48, error2.getColumn ());

      return;
    }
    Assert.fail("A compile exception was expected");
  }

  /**
   * Test case for defect & 19100: AQL Compiler does not validate column names for extract
   * statements Test case to assert error location when there are duplicate column names in extract
   * stmt list
   */
  @Test
  public void testValidateDuplicateColumnName() throws Exception {
    startTest();

    try {
      genericAQLCompileTest("validateDuplicateColumnName");
    } catch (CompilerException e) {
      System.err.printf("Caught CompilerException as expected:\n%s\n", e);

      List<Exception> compileErrors = e.getSortedCompileErrors();

      // System.err.printf ("Stack traces follow:\n");
      // for (Exception exception : compileErrors) {
      // exception.printStackTrace ();
      // }
      // System.err.printf ("------ END stack traces ------\n");

      ParseException error1 = (ParseException) compileErrors.get(0);
      assertEquals(17, error1.getLine());

      ParseException error2 = (ParseException) compileErrors.get(1);
      assertEquals(26, error2.getLine());

      // Removed the error on line 29 ("Document1" instead of "Document") because that error now
      // prevents type inference
      // from moving forward, masking the more important (for this test case) error on line 26.
      // ParseException error3 = (ParseException) compileErrors.get (2);
      // assertEquals (29, error3.getLine ());

      ParseException error3 = (ParseException) compileErrors.get(2);
      assertEquals(34, error3.getLine());

      ParseException error4 = (ParseException) compileErrors.get(3);
      assertEquals(42, error4.getLine());

      ParseException error5 = (ParseException) compileErrors.get(4);
      assertEquals(51, error5.getLine());

      return;
    }

    Assert.fail("A compile exception was expected");
  }

  /**
   * Test case for defect# 18697: Internal error in SystemT compiler Test case to assert error
   * location when dictionary referred in extract dictionary stmt is not in data path
   */
  @Test
  public void testMissingDictionaryFileNotInSearchPath() throws Exception {
    startTest();

    try {
      genericAQLCompileTest("missingDictionaryFileNotInSearchPath");
    } catch (CompilerException e) {
      List<Exception> compileErrors = e.getAllCompileErrors();
      ParseException error1 = (ParseException) compileErrors.get(0);
      assertEquals(12, error1.getLine());

      return;
    }
    Assert.fail("A compile exception was expected");
  }

  /**
   * Test case for defect# 18697: Internal error in SystemT compiler Test case to assert error
   * location pattern spec(Atom of the form viewname.columnname) in sequence pattern is missing
   * column name
   */
  @Test
  public void testColumnValidationExtractPatternStmt() throws Exception {
    startTest();

    try {
      genericAQLCompileTest("columnValidationExtractPatternStmt");
    } catch (CompilerException e) {
      List<Exception> compileErrors = e.getAllCompileErrors();
      ParseException error1 = (ParseException) compileErrors.get(0);
      assertEquals(16, error1.getLine());

      return;
    }
    Assert.fail("A compile exception was expected");
  }

  /**
   * Test case for task# 17245: Verify that CompilerWarnings are generated when there is no
   * CompilerException.
   */
  @Test
  public void testComplilerWarningsWithoutException() throws Exception {
    startTest();

    WarningType[] warningTypes = new WarningType[] {WarningType.RSR_FOR_THIS_REGEX_NOT_SUPPORTED};

    CompilationSummary summary = genericAQLCompileTest("compilerWarningWithoutException");
    verifyWarnings(summary, warningTypes);

    endTest();

  }

  /**
   * A generic bug-derived test case. assumes that an AQL file called prefix.aql exists. Verifies if
   * a ParseException is thrown with the input line number. If the column number is larger than 0,
   * it also verifies that the ParseException is thrown with the appropriate column information. If
   * you're not interested in verifying the column, call this method with expectedColNumber=-1.
   * 
   * @param expectedLineNumber : If caller is not expecting a parse error then pass null
   * @param expectedColNumber : If caller is not expecting a parse error then pass null
   * @param prefix
   */
  private void genericAQLParseTest(int[] expectedLineNumbers, int[] correspondingExpectedColNumbers,
      String prefix) {

    AQLParser parser = null;
    String aqlFileName = String.format("%s/%s.aql", AQL_FILES_DIR, prefix);
    String dataPath = String.format("%s/%s;%s/%s", AQL_FILES_DIR, "dict1", AQL_FILES_DIR, "dict2");

    try {
      parser = new AQLParser(new File(aqlFileName));
      parser.setBackwardCompatibilityMode(true);
    } catch (Exception e) {
      // TODO Need to remove this try catch clause
      Assert.fail();
    }

    parser.setIncludePath(dataPath);
    StatementList statementList = parser.parse();
    LinkedList<ParseException> parseErrors = statementList.getParseErrors();

    // caller is expecting no parse errors
    if (null == expectedLineNumbers && null == correspondingExpectedColNumbers) {
      assertTrue(parseErrors.size() == 0);
      return;
    }

    // bad declaration -- exactly one of expected linenums and colnums is null
    if (null == expectedLineNumbers || null == correspondingExpectedColNumbers) {
      Assert.fail("An expected parse error does not have both line and column number.");
      return;
    }

    if (parseErrors.size() != expectedLineNumbers.length)
      Assert.fail();

    int errorIndex = 0;
    for (ParseException pe : parseErrors) {
      if (pe instanceof ParseException) {
        Pattern p = Pattern.compile("line (\\d+), column (\\d+)");
        Matcher m = p.matcher(pe.getMessage());
        if (m.find()) {
          int actualLineNumber = Integer.parseInt(m.group(1));
          Assert.assertEquals(expectedLineNumbers[errorIndex], actualLineNumber);

          if (correspondingExpectedColNumbers[errorIndex] >= 0) {
            int actualColNumber = Integer.parseInt(m.group(2));
            Assert.assertEquals(correspondingExpectedColNumbers[errorIndex], actualColNumber);
          }
          return;
        } else
          Assert.fail();
      } else
        Assert.fail();
      errorIndex++;
    }
  }

  private CompilationSummary genericAQLCompileTest(String prefix) throws Exception {

    String aqlFileName = String.format("%s/%s.aql", AQL_FILES_DIR, prefix);
    String dataPath = String.format("%s/%s;%s/%s", AQL_FILES_DIR, "dict1", AQL_FILES_DIR, "dict2");

    File aqlFile = new File(aqlFileName);
    CompileAQLParams compileParam = new CompileAQLParams();
    compileParam.setPerformRSR(true);
    CompilationSummary summary = compileAQL(aqlFile, dataPath, compileParam);

    return summary;
  }

}
