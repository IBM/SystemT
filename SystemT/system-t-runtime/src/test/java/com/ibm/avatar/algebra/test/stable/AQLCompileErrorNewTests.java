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
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.api.CompilationSummary;
import com.ibm.avatar.api.CompileAQL;
import com.ibm.avatar.api.CompileAQLParams;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.exceptions.CompilerException;
import com.ibm.avatar.api.exceptions.ModuleLoadException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.AQLParseTreeNode;
import com.ibm.avatar.aql.AQLParser;
import com.ibm.avatar.aql.OutputViewNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.compiler.CompilerWarning.WarningType;

/**
 * Various regression tests to ensure the parser and compiler throw errors whenever they should.
 * Extends the RuntimeTestHarness. We should merge into this class all the tests from
 * {@link AQLCompileErrorTests}.
 * 
 */
public class AQLCompileErrorNewTests extends RuntimeTestHarness {

  /**
   * A small collection of fake medical transcripts, shared by most of the tests in this class.
   */
  public static final File DOCS_FILE = new File(TestConstants.DUMPS_DIR, "ensmall.zip");

  /**
   * Main method for invoking one test at a time.
   * 
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    AQLCompileErrorNewTests t = new AQLCompileErrorNewTests();
    t.setUp();

    long startMS = System.currentTimeMillis();

    t.cycleBugTest3();

    long endMS = System.currentTimeMillis();

    t.tearDown();

    double elapsedSec = (endMS - startMS) / 1000.0;
    System.err.printf("Test took %1.3f sec.\n", elapsedSec);
  }

  @Before
  public void setUp() {

  }

  @After
  public void tearDown() {

  }

  /**
   * Test case for validating that the parser enforces for create function nodes the specification
   * of "like" when the return type is ScalarList.
   * 
   * @throws Exception
   */
  @Test
  public void scalarListReturnLikeValidationTest() throws Exception {
    startTest();

    // UDF declarations in the AQL file assume the data path is set to "./testdata"
    setDataPath("./testdata");

    // Expect exceptions at these locations
    int[] lineNo = new int[] {9, 17, 18, 26, 27};
    int[] colNo = new int[] {8, 1, 8, 1, 8};
    compileAQLTest(lineNo, colNo);

    endTest();
  }

  /**
   * Test case for validating that a minus statement contains exactly 2 operands
   * 
   * @throws Exception
   */
  @Test
  public void tooManyMinusOperandsTest() throws Exception {
    startTest();

    // Expect exceptions at these locations
    int[] lineNo = new int[] {23, 32, 36, 37};
    int[] colNo = new int[] {1, 1, 13, 13};
    compileAQLTest(lineNo, colNo);

    endTest();
  }

  /**
   * Test case for throwing an error when encountering the illegal 'select * from Document'
   * construct
   * 
   * @throws Exception
   */
  @Test
  public void selectAllFromDocTest() throws Exception {
    startTest();

    // Expect exceptions at these locations
    int[] lineNo = new int[] {7, 22};
    int[] colNo = new int[] {15, 6};
    compileAQLTest(lineNo, colNo);

    endTest();
  }

  /**
   * Test case for validating that wildcard expansion and alias inference work for ExtractNode
   * (extract statements other than EXTRACT PATTERN).
   * 
   * @throws Exception
   */
  @Test
  public void extractExpandInferAliasValidationTest() throws Exception {
    startTest();

    // Expect exceptions at these locations
    int[] lineNo = new int[] {22, 35, 38, 73, 75, 92, 103, 106, 118, 119, 131, 132, 142, 145};
    int[] colNo = new int[] {13, 9, 4, 9, 4, 1, 18, 4, 26, 4, 20, 4, 9, 4};
    compileAQLTest(lineNo, colNo);

    endTest();
  }

  /**
   * Test case for validating that wildcard expansion and alias inference work for ExtractNode
   * (extract statements other than EXTRACT PATTERN).
   * 
   * @throws Exception
   */
  @Test
  public void validateSelectWrtGroupByTest() throws Exception {
    startTest();

    // Expect exceptions at these locations
    int[] lineNo = new int[] {23, 32, 40, 48};
    int[] colNo = new int[] {28, 1, 33, 26};
    compileAQLTest(lineNo, colNo);

    endTest();
  }

  /**
   * Test case to validate that we do not allow aggregate function calls in EXTRACT statements.
   * 
   * @throws Exception
   */
  @Test
  public void validateExtractNoAggsTest() throws Exception {
    startTest();

    // Expect exceptions at these locations
    int[] lineNo = new int[] {11, 20, 20};
    int[] colNo = new int[] {9, 1, 9};
    compileAQLTest(lineNo, colNo);

    endTest();
  }

  /**
   * Test case to validate that we do not allow duplicate aliases in the FROM clause.
   * 
   * @throws Exception
   */
  @Test
  public void validateFromListAliasesTest() throws Exception {
    startTest();

    // Expect exceptions at these locations
    int[] lineNo = new int[] {15, 22, 23, 30, 31, 38, 39, 47, 53, 61, 69, 77, 77};
    int[] colNo = new int[] {19, 8, 16, 8, 16, 8, 16, 78, 19, 16, 16, 16, 96};
    compileAQLTest(lineNo, colNo);

    endTest();
  }

  /**
   * Test case to validate that we throw exceptions during compiletime for all Matches/ContainsRegex
   * constructs
   * 
   * @throws Exception
   */
  @Test
  public void validateMatchesAndContainsTest() throws Exception {
    startTest();

    // Expect exceptions at these locations
    int[] lineNo = new int[] {11, 19, 26, 34};
    int[] colNo = new int[] {3, 3, 3, 3};
    compileAQLTest(lineNo, colNo);

    endTest();
  }

  /**
   * Test case to validate that we do not throw exceptions during compiletime for functions which
   * can take 0 arguments
   * 
   * @throws Exception
   */
  @Test
  public void validateZeroArgFunctionsTest() throws Exception {
    startTest();

    // Expect exceptions at these locations
    int[] lineNo = new int[] {};
    int[] colNo = new int[] {};
    compileAQLTest(lineNo, colNo);

    endTest();
  }

  @Test
  public void multipleLexicalErrTest() throws Exception {
    startTest();

    // Expect exceptions at these locations
    int[] lineNo = new int[] {10, 18};
    int[] colNo = new int[] {-1, -1};

    compileAQLTest(lineNo, colNo);

    endTest();

  }

  /**
   * Test to verify that the compiler is validating missing UDF jars and reporting back error with
   * proper error location.
   * <p>
   * TODO: Temporarily disabled by Huaiyu Zhu, 2013-09. The exception at line 17 is missing but it
   * not likely related to the Span/Text/String change. Need investigation.
   * 
   * @throws Exception
   */
  // @Test
  public void missingUDFValidationTest() throws Exception {
    startTest();

    // Expect exceptions at these locations
    int[] lineNo = new int[] {8, 23};
    int[] colNo = new int[] {1, 8};

    compileModuleAndCheckErrors("missingUDFValidationTest", lineNo, colNo);
    endTest();
  }

  /**
   * Test to verify compiler throws error when referencing an undefined module.
   * 
   * @throws Exception
   */
  @Test
  public void undefinedModuleTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNo = new int[] {11};
    int[] colNo = new int[] {25};

    compileModuleAndCheckErrors("undefinedModuleTest", lineNo, colNo);

    endTest();
  }

  /**
   * Test to verify that the compiler return error with proper location information while importing
   * unknown module.
   * 
   * @throws Exception
   */
  @Test
  public void unknownModuleImportTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNo = new int[] {7, 9};
    int[] colNo = new int[] {15, 34};

    compileModuleAndCheckErrors("unknownModuleImportTest", lineNo, colNo);

    endTest();
  }

  /**
   * Test to verify that compiler returns error if first statement is not a 'module ... ' statement.
   * Test case for defect# 26474: Compiler error message is insufficient and compiler error is
   * appearing at incorrect line number.
   * 
   * @throws Exception
   */
  @Test
  public void firstStmtNotModuleTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNo = new int[] {1, 9};
    int[] colNo = new int[] {1, 1};

    compileModuleAndCheckErrors("firstStmtNotModuleTest", lineNo, colNo);

    endTest();
  }

  /**
   * Test to verify that compiler returns error if there is a missing keyword in the middle of a
   * 'create ... ' statement. Test case for defect# 26474: Compiler error message is insufficient
   * and compiler error is appearing at incorrect line number.
   * 
   * @throws Exception
   */
  @Test
  public void missingKeywordTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNo = new int[] {12};
    int[] colNo = new int[] {18};

    compileModuleAndCheckErrors("missingKeywordTest", lineNo, colNo);

    endTest();
  }

  /**
   * Test to verify that compiler returns error for multiple occurrences of 'module ...' statement
   * for all the aql files under module. <br>
   * 
   * @throws Exception
   */
  @Test
  public void multipleModuleStmtTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNo = new int[] {11, 7};
    int[] colNo = new int[] {1, 1};

    compileModuleAndCheckErrors("multipleModuleStmtTest", lineNo, colNo);

    endTest();
  }

  /**
   * Test to verify, that the parser is error recoverable while parsing aql files in the module.
   * <br>
   * 
   * @throws Exception
   */
  @Test
  public void parserModuleErrRecoveryTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNo = new int[] {6, 14, 18, 5};
    int[] colNo = new int[] {33, 8, 13, 8};

    compileModuleAndCheckErrors("parserModuleErrRecoveryTest", lineNo, colNo);

    endTest();
  }

  /**
   * Test to verify that the compiler report's errors on the same location(line and column) in two
   * different files belonging to the same module. <br>
   * 
   * @throws Exception
   */
  @Test
  public void compileErrDeDupTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNo = new int[] {9, 9};
    int[] colNo = new int[] {6, 6};

    compileModuleAndCheckErrors("compileErrDeDupTest", lineNo, colNo);

    endTest();
  }

  /**
   * Verifies if proper error message is thrown when a view that is not defined is used in output
   * view statement
   * 
   * @throws Exception
   */
  @Test
  public void outputUndefinedViewTest() throws Exception {
    startTest();

    // Expect exception at this location
    int[] lineNo = new int[] {8};
    int[] colNo = new int[] {13};

    compileModuleAndCheckErrors("outputUndefinedViewTest", lineNo, colNo);

    endTest();
  }

  /**
   * Test to verify that compiler return errors while exporting undefined
   * objects(table/function/view/dictionary).
   * 
   * @throws Exception
   */
  @Test
  public void exportUndefinedObjectTest() throws Exception {
    startTest();

    // Expect exception at this location
    int[] lineNo = new int[] {6, 8, 10, 12};
    int[] colNo = new int[] {1, 1, 1, 1};

    compileModuleAndCheckErrors("exportUndefinedObjectTest", lineNo, colNo);

    endTest();
  }

  /**
   * Test to verify that the compiler throws error, while outputting a view twice in a module.
   * 
   * @throws Exception
   */
  @Test
  public void checkDuplicateOutputTest() throws Exception {
    startTest();

    // Expect exception at this location
    int[] lineNo = new int[] {7};
    int[] colNo = new int[] {1};

    compileModuleAndCheckErrors("checkDuplicateOutputTest", lineNo, colNo);

    endTest();
  }

  /**
   * This test verifies that the compiler return error while module trying to import itself; we
   * prohibit self imports.
   * 
   * @throws Exception
   */
  @Test
  public void selfImportTest() throws Exception {
    startTest();

    // Expect exception at this location
    int[] lineNo = new int[] {6, 7};
    int[] colNo = new int[] {15, 34};

    compileModuleAndCheckErrors("selfImportTest", lineNo, colNo);

    endTest();
  }

  /**
   * Test to verify that the compiler returns error for various bad table declarations.
   * 
   * @throws Exception
   */
  @Test
  public void invalidTableTest() throws Exception {
    startTest();

    // Expect exception at this location
    int[] lineNo = new int[] {13, 20};
    int[] colNo = new int[] {14, 14};

    compileModuleAndCheckErrors("invalidTableTest", lineNo, colNo);

    endTest();
  }

  /**
   * Test to verify that compiler returns error for various bad dictionary declarations.
   * 
   * @throws Exception
   */
  @Test
  public void invalidDictTest() throws Exception {
    startTest();

    // Expect exception at this location
    int[] lineNo = new int[] {8, 17, 26, 27, 30, 32, 34, 37};
    int[] colNo = new int[] {1, 11, 29, 1, 30, 37, 32, 19};

    compileModuleAndCheckErrors("invalidDictTest", lineNo, colNo);

    endTest();
  }

  /**
   * Test case to verify defect - Incorrect error line# and error message from parser when creating
   * external views.
   */
  @Test
  public void parseErrInExtViewTest() throws Exception {
    startTest();

    // Expect exception at this location
    int[] lineNo = new int[] {7, 11};
    int[] colNo = new int[] {8, 17};

    compileAQLTest(lineNo, colNo);
    endTest();
  }

  /**
   * Test case to capture scenario mentioned in defect - Compiler error marked at incorrect line
   * number for certain types of Create statement.
   * 
   * @throws Exception
   */
  @Test
  public void invalidCreateStmtTest() throws Exception {
    startTest();

    // Expect exception at this location
    int[] lineNo = new int[] {5, 8};
    int[] colNo = new int[] {1, 4};

    compileAQLTest(lineNo, colNo);
    endTest();
  }

  /**
   * Test case to capture scenario mentioned in defect - Compiler error marked at incorrect line
   * number for certain types of Create statement.
   * 
   * @throws Exception
   */
  @Test
  public void invalidCreateStmt2Test() throws Exception {
    startTest();

    // Expect exception at this location
    int[] lineNo = new int[] {7, 14};
    int[] colNo = new int[] {19, 16};

    compileModuleAndCheckErrors("invalidCreateStmt2Test", lineNo, colNo);
    endTest();
  }

  /**
   * Test to verify that the compiler returns ambiguity error, when multiple module files(tams) for
   * a module is found in the module path.
   * 
   * @throws Exception
   */
  @Test
  public void moduleAmbiguityTest() throws Exception {
    startTest();

    // Expect exception at this location
    int[] lineNo = new int[] {7, 9};
    int[] colNo = new int[] {15, 25};

    try {
      String path1 = new File(getCurTestDir(), "TAM1").toURI().toString();
      String path2 = new File(getCurTestDir(), "TAM2").toURI().toString();

      // Compile
      CompileAQLParams params = new CompileAQLParams();
      params
          .setInputModules(new String[] {new File(getCurTestDir(), "module1").toURI().toString()});
      params.setModulePath(String.format("%s;%s", path1, path2));
      params.setOutputURI(getCurOutputDir().toURI().toString());
      params.setTokenizerConfig(getTokenizerConfig());

      CompileAQL.compile(params);
    } catch (CompilerException ce) {
      checkException(ce, lineNo, colNo);
      return;
    }

    // Control should not reach here; compiler exception expected
    Assert.fail();
  }

  /**
   * Test to verify that the compiler returns ambiguity error, when multiple module files(tams) for
   * a module is found in the module path.Module path for this contains ambiguous module file(tam)
   * in the zip archive.
   * 
   * @throws Exception
   */
  @Test
  public void moduleAmbiguity2Test() throws Exception {
    startTest();

    // Expect exception at this location
    int[] lineNo = new int[] {7, 9};
    int[] colNo = new int[] {15, 25};

    try {
      String path1 = new File(getCurTestDir(), "TAM1").toURI().toString();
      String path2 = new File(getCurTestDir(), "firstModule.zip").toURI().toString();

      // Compile
      CompileAQLParams params = new CompileAQLParams();
      params
          .setInputModules(new String[] {new File(getCurTestDir(), "module1").toURI().toString()});
      params.setModulePath(String.format("%s;%s", path1, path2));
      params.setOutputURI(getCurOutputDir().toURI().toString());
      params.setTokenizerConfig(getTokenizerConfig());

      CompileAQL.compile(params);
    } catch (CompilerException ce) {
      checkException(ce, lineNo, colNo);
      return;
    }

    // Control should not reach here; compiler exception expected
    Assert.fail();
  }

  /**
   * Test to verify that the compiler returns ambiguity error, when multiple module files(tams) for
   * a module is found in the module path.Module path for this contains ambiguous module file(tam)
   * in the jar archive.
   * 
   * @throws Exception
   */
  @Test
  public void moduleAmbiguity3Test() throws Exception {
    startTest();

    // Expect exception at this location
    int[] lineNo = new int[] {7, 9};
    int[] colNo = new int[] {15, 25};

    try {
      String path1 = new File(getCurTestDir(), "TAM1").toURI().toString();
      String path2 = new File(getCurTestDir(), "firstModule.jar").toURI().toString();

      // Compile
      CompileAQLParams params = new CompileAQLParams();
      params
          .setInputModules(new String[] {new File(getCurTestDir(), "module1").toURI().toString()});
      params.setModulePath(String.format("%s;%s", path1, path2));
      params.setOutputURI(getCurOutputDir().toURI().toString());
      params.setTokenizerConfig(getTokenizerConfig());

      CompileAQL.compile(params);
    } catch (CompilerException ce) {
      checkException(ce, lineNo, colNo);
      return;
    }

    // Control should not reach here; compiler exception expected
    Assert.fail();
  }

  /**
   * Test to verify that compiler *does not* return ambiguity error for duplicate entries in module
   * path.
   * 
   * @throws Exception
   */
  @Test
  public void moduleAmbiguity4Test() throws Exception {
    startTest();

    // No exception expected
    int[] lineNo = new int[] {};
    int[] colNo = new int[] {};

    try {
      String path1 = new File(getCurTestDir(), "TAM1").toURI().toString();
      // Duplicate of path1 in the module path
      String path2 = new File(getCurTestDir(), "TAM1").toURI().toString();

      // Compile
      CompileAQLParams params = new CompileAQLParams();
      params
          .setInputModules(new String[] {new File(getCurTestDir(), "module1").toURI().toString()});
      params.setModulePath(String.format("%s;%s", path1, path2));
      params.setOutputURI(getCurOutputDir().toURI().toString());
      params.setTokenizerConfig(getTokenizerConfig());

      CompileAQL.compile(params);
    } catch (CompilerException ce) {
      // Compiler should not return error, passing in 0 expected exceptions
      checkException(ce, lineNo, colNo);
    }

    endTest();
  }

  /**
   * Test to verify that the compiler de-duplicates the URIs(pointing to directory), which differ
   * only by trailing '/' character. The compiler should not throw ambiguity error for such URIs.
   * 
   * @throws Exception
   */
  @Test
  public void moduleAmbiguity5Test() throws Exception {
    startTest();

    try {
      // Module path entry without a trailing '/' character
      String path1 = new File(getCurTestDir(), "TAM1").toURI().toString();
      path1 = path1.substring(0, path1.length() - 1);

      // Duplicate module path with a trailing '/' character
      String path2 = new File(getCurTestDir(), "TAM1").toURI().toString();

      // Compile
      CompileAQLParams params = new CompileAQLParams();
      params
          .setInputModules(new String[] {new File(getCurTestDir(), "module1").toURI().toString()});
      params.setModulePath(String.format("%s;%s", path1, path2));
      params.setOutputURI(getCurOutputDir().toURI().toString());
      params.setTokenizerConfig(getTokenizerConfig());

      CompileAQL.compile(params);
    } catch (CompilerException ce) {
      // Compiler should not return ambiguity error
      Assert.fail("Compiler should not return ambiguity error");
    }

    endTest();
  }

  /**
   * Test to verify that compiler restricts, the output alias name that conflicts with any of the
   * exported view name. This restriction is introduced to fix defect - refer comment#6 in RTC.
   * 
   * @throws Exception
   */
  @Test
  public void outputAliasConflictTest() throws Exception {
    startTest();

    // Expect exception at this location
    int[] lineNo = new int[] {18, 28};
    int[] colNo = new int[] {22, 22};

    compileModuleAndCheckErrors("module1", lineNo, colNo);
    endTest();
  }

  /*
   * Multi module compiler error tests starts here
   */

  /**
   * Test to verify that the compiler returns appropriate errors with error location, while
   * importing object(view/table/function/dictionary) which are not exported or not all declared in
   * the imported module. Replicate defect : Importing of dictionaries and tables which are not
   * exported is not functioning correctly.
   * 
   * @throws Exception
   */
  @Test
  public void invalidImportsTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNo = new int[] {11, 14, 17, 20, 24, 27, 30, 33, 37, 37};
    int[] colNo = new int[] {1, 1, 1, 1, 1, 1, 1, 1, 34, 34};

    compileMultipleModulesAndCheckErrors(new String[] {"noExports", "invalidImports"}, lineNo,
        colNo);

    endTest();
  }

  /**
   * This test verifies that the compiler return errors with proper error location, while referring
   * undefined entities. Test case for defect# 27139: Missing error marker and location for
   * undefined entities - view and table.
   * 
   * @throws Exception
   */
  @Test
  public void invalidImportTest2() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNo = new int[] {11, 21, 21, 29, 29};
    int[] colNo = new int[] {34, 20, 45, 6, 21};

    compileMultipleModulesAndCheckErrors(new String[] {"partialExport", "invalidReferences"},
        lineNo, colNo);

    endTest();
  }

  /**
   * This test verifies that the compiler throws error for dictionary coming from imported table;
   * this test import all the table using in one shot using the 'import module ...' statement. As of
   * v2.0, we do not allow dictionaries coming from imported tables.
   * 
   * @throws Exception
   */
  @Test
  public void dictFromImportedTabTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNo = new int[] {8, 12};
    int[] colNo = new int[] {1, 1};

    compileMultipleModulesAndCheckErrors(new String[] {"exportTable", "importAllTable"}, lineNo,
        colNo);

    endTest();
  }

  /**
   * This test verifies that the compiler throws error for dictionary coming from imported table;
   * this tests import's the table using the 'import table ...' statement.As of v2.0, we do not
   * allow dictionaries coming from imported tables.
   * 
   * @throws Exception
   */
  @Test
  public void dictFromImportedTab2Test() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNo = new int[] {10, 14};
    int[] colNo = new int[] {1, 1};

    compileMultipleModulesAndCheckErrors(new String[] {"exportTable", "importTableAlias"}, lineNo,
        colNo);

    endTest();
  }

  /**
   * Test to verify that compiler returns error while exported imported
   * object(table/view/dictionary/function).
   * 
   * @throws Exception
   */
  @Test
  public void exportImportedObjectTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNo = new int[] {9, 11, 13, 15};
    int[] colNo = new int[] {1, 1, 1, 1};

    compileMultipleModulesAndCheckErrors(new String[] {"exportAll", "reExportImported"}, lineNo,
        colNo);

    endTest();
  }

  /**
   * Test to verify that compiler returns error while exported imported
   * object(table/view/dictionary/function). For this test objects are imported one by one using the
   * 'import ... from module ...' statement.
   * 
   * @throws Exception
   */
  @Test
  public void exportImportedObject2Test() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNo = new int[] {15, 17, 19, 21, 24};
    int[] colNo = new int[] {1, 1, 1, 1, 1};

    compileMultipleModulesAndCheckErrors(new String[] {"exportAll", "reExportImported"}, lineNo,
        colNo);

    endTest();
  }

  /**
   * Test to verify that compiler returns error when import statements are declared after regular
   * AQL statements.
   * 
   * @throws Exception
   */
  @Test
  public void importOutOfOrderTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNo = new int[] {20, 27};
    int[] colNo = new int[] {73, 70};

    compileMultipleModulesAndCheckErrors(
        new String[] {"PersonName_BasicFeatures", "PersonName_CandidateGeneration"}, lineNo, colNo);

    endTest();
  }

  /**
   * Test to verify that module statement used in backward compatibility code throws exception as
   * expected.
   * 
   * @throws Exception
   */
  @Test
  public void moduleStmtInBackwardCompatibilityCodeTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNo = new int[] {6};
    int[] colNo = new int[] {1};

    compileAQLTest(lineNo, colNo);

    endTest();
  }

  /**
   * Attempt to run an v2.0 AQL as String (and fail)
   * 
   * @throws Exception
   */
  @Test
  public void runModularAQLAsStringTest() throws Exception {

    startTest();

    // Parse the AQL file
    CompileAQLParams compileParam = new CompileAQLParams();
    compileParam.setInputStr(
        "module test; create view WillNotCompile as select D.text from Document D; output view WillNotCompile;");
    compileParam.setOutputURI(getCurOutputDir().toURI().toString());
    compileParam.setInputModules(new String[] {"/test"});
    compileParam.setTokenizerConfig(getTokenizerConfig());

    // we should throw an error because both inputStr and inputModules are declared
    try {
      CompileAQL.compile(compileParam);
    } catch (CompilerException e) {
      List<Exception> compileErrors = e.getSortedCompileErrors();
      assertEquals(1, compileErrors.size());
      assertEquals("Only one of the following values must be provided: inputModules, inputStr",
          compileErrors.get(0).getMessage());
      return;
    }

    Assert.fail("A compile exception was expected");
  }

  /**
   * Test to verify if compiler returns error when alias name used in 'import function' statement
   * conflicts with an built-in function
   * 
   * @throws Exception
   */
  @Test
  public void importAliasConflictingWithBuiltInFunctionTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNo = new int[] {4};
    int[] colNo = new int[] {1};

    compileMultipleModulesAndCheckErrors(new String[] {"module1", "module2"}, lineNo, colNo);

    endTest();
  }

  /**
   * Test to verify if compiler returns error when alias name used in 'import view' statement
   * conflicts with another view defined in current module
   * 
   * @throws Exception
   */
  @Test
  public void importAliasConflictingWithViewTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNo = new int[] {7};
    int[] colNo = new int[] {13};

    compileMultipleModulesAndCheckErrors(new String[] {"module1", "module2"}, lineNo, colNo);

    endTest();
  }

  /**
   * Test to verify if compiler returns error when a UDF is defined with the same name as a built-in
   * function (both scalar and aggregate)
   * 
   * @throws Exception
   */
  @Test
  public void functionNameConflictingWithBuiltInFunctionTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNo = new int[] {4, 15};
    int[] colNo = new int[] {1, 1};

    compileMultipleModulesAndCheckErrors(new String[] {"module1"}, lineNo, colNo);

    endTest();
  }

  /**
   * Test to verify if compiler returns proper error and location when a UDF is defined with the
   * same name as a previously defined UDF.
   * 
   * @throws Exception
   */
  @Test
  public void duplicateFunctionNameTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNo = new int[] {12};
    int[] colNo = new int[] {1};

    compileMultipleModulesAndCheckErrors(new String[] {"module1"}, lineNo, colNo);

    endTest();
  }

  /**
   * Test to verify that duplicate view definitions are flagged as error
   * 
   * @throws Exception
   */
  @Test
  public void duplicateViewDefinitionTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNo = new int[] {6};
    int[] colNo = new int[] {13};

    compileModuleAndCheckErrors("module1", lineNo, colNo);

    endTest();
  }

  /**
   * Test to verify that duplicate dictionary definitions are flagged as error
   * 
   * @throws Exception
   */
  @Test
  public void duplicateDictDefinitionTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNo = new int[] {8};
    int[] colNo = new int[] {1};

    compileModuleAndCheckErrors("module1", lineNo, colNo);

    endTest();
  }

  /**
   * Test to verify that duplicate table definitions are flagged as error
   * 
   * @throws Exception
   */
  @Test
  public void duplicateTableDefinitionTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNo = new int[] {11};
    int[] colNo = new int[] {14};

    compileModuleAndCheckErrors("module1", lineNo, colNo);

    endTest();
  }

  /**
   * Test to verify that 'export view Document' throws a compiler error. defect .
   * 
   * @throws Exception
   */
  @Test
  public void exportDocumentTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNo = new int[] {4};
    int[] colNo = new int[] {13};

    compileMultipleModulesAndCheckErrors(new String[] {"module1"}, lineNo, colNo);

    endTest();
  }

  /**
   * Test to verify that compiler throws an error when the user attempts to create a view named
   * Document
   * 
   * @throws Exception
   */
  @Test
  public void disallowViewByNameDocumentTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNo = new int[] {7, 9};
    int[] colNo = new int[] {13, 34};

    compileMultipleModulesAndCheckErrors(new String[] {"module1"}, lineNo, colNo);

    endTest();
  }

  /**
   * Test to verify that compiler throws an error when the user attempts to create a table named
   * Document
   * 
   * @throws Exception
   */
  @Test
  public void disallowTableByNameDocumentTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNo = new int[] {5, 24};
    int[] colNo = new int[] {14, 12};

    compileMultipleModulesAndCheckErrors(new String[] {"module1"}, lineNo, colNo);

    endTest();
  }

  /**
   * Test to verify that outputViewNode.getViewname ().getOrigTok () is not null. defect
   * 
   * @throws Exception
   */
  @Test
  public void outputViewNameOrigTokenNotNullTest() throws Exception {
    startTest();

    String className = getClass().getSimpleName();

    // Compute the location of the current test case's top-level AQL file.
    File aqlDir = new File(TestConstants.AQL_DIR, className);
    File aqlFile = new File(aqlDir, String.format("%s.aql", getCurPrefix()));

    // Parse and get list of parse tree nodes
    AQLParser parser = new AQLParser(aqlFile);
    LinkedList<AQLParseTreeNode> nodes = parser.parse().getParseTreeNodes();

    // Iterate over the parse tree nodes and check if origTok of viewname is not null
    for (AQLParseTreeNode node : nodes) {
      if (node instanceof OutputViewNode) {
        OutputViewNode outputViewNode = (OutputViewNode) node;
        assertNotNull("outputViewNode.getViewname ().getOrigTok () is not expected to be null.",
            outputViewNode.getViewname().getOrigTok());
      }
    }

    endTest();
  }

  /**
   * Test to verify that an AQL element (view, dictionary, table or function) imported through alias
   * is accessible only through the alias and not through fully qualified element name. <br/>
   * Test case for defect , comment #3
   * 
   * @throws Exception
   */
  @Test
  public void importAliasAccessTest() throws Exception {
    startTest();

    // TODO: presently in some cases, the origToken of <module>.<view> points to <view> and not to
    // <module> token and
    // hence some column numbers are adjusted. Fix this in a future release. For instance, in the
    // colNo[] data below,
    // the first error should ideally be reported at column number 13, but since the token for
    // <module> is not saved in
    // OutputViewNode, we report the column number of <view>

    // Expect exception at these locations
    int[] lineNo = new int[] {13, 18, 27, 37, 44, 51};
    int[] colNo = new int[] {21, 20, 20, 14, 16, 42};

    compileMultipleModulesAndCheckErrors(new String[] {"module1", "module2"}, lineNo, colNo);

    endTest();

  }

  /**
   * Scenario when a view, table, dictionary, and function share the same name. Currently, the view
   * and table are not allowed to use the same name. Dictionaries and functions are allowed to use
   * the same name as a view/table. <br/>
   * Test case for defect , comment #4
   * 
   * @throws Exception
   */
  @Test
  public void sameElementNameTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNo =
        new int[] {29, 52, 58, 65, 79, 83, 86, 90, 104, 108, 111, 115, 130, 134, 137, 141};
    int[] colNo = new int[] {14, 22, 7, 13, 13, 22, 7, 14, 13, 22, 7, 14, 13, 22, 7, 14};

    compileMultipleModulesAndCheckErrors(new String[] {"module1"}, lineNo, colNo);

    endTest();

  }

  /**
   * Test for when we attempt to output a view that does not exist but has the same name as a table.
   * Test case for defect , comment #4
   * 
   * @throws Exception
   */
  @Test
  public void outputTableAsViewTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNo = new int[] {15};
    int[] colNo = new int[] {13};

    compileMultipleModulesAndCheckErrors(new String[] {"module1"}, lineNo, colNo);

    endTest();

  }

  /**
   * Test that used to generate ParseExceptions without location info <br/>
   * Test case for defect
   * 
   * @throws Exception
   */
  @Test
  public void exceptionWithoutFilenameTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNo = new int[] {12, 16, 22, 27, 31, 47, 52};
    int[] colNo = new int[] {20, 7, 20, 7, 20, 6, 18};

    compileMultipleModulesAndCheckErrors(new String[] {"module1"}, lineNo, colNo);

    endTest();

  }

  /**
   * Test to verify that parser/compiler throw an error when AQL elements contain a dot in their
   * names. Test case for defect : view names enclosed with double quotes and having "." - AQL
   * Editor functionalities not working
   * 
   * @throws Exception
   */
  @Test
  public void disallowDotInAQLElementNamesTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNo = new int[] {10, 14, 15, 20, 29, 34, 37, 42, 49, 55, 62, 67, 70, 75, 82, 87, 92};
    int[] colNo = new int[] {13, 13, 1, 19, 1, 19, 1, 14, 1, 17, 1, 22, 1, 23, 1, 28, 1};

    compileMultipleModulesAndCheckErrors(new String[] {"module1"}, lineNo, colNo);

    endTest();
  }

  /**
   * Test to verify that parser/compiler throw an error when Import aliases contain a dot. Test case
   * for defect : view names enclosed with double quotes and having "." - AQL Editor functionalities
   * not working
   * 
   * @throws Exception
   */
  @Test
  public void disallowDotInImportAliasesTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNo = new int[] {3, 4, 5, 6, 7, 8, 9, 10};
    int[] colNo = new int[] {43, 57, 57, 52, 54, 49, 51, 55};

    compileMultipleModulesAndCheckErrors(new String[] {"module1", "module2"}, lineNo, colNo);

    endTest();
  }

  /**
   * Test case for running some AQL. Only here to show how to use the methods from the parent
   * {@link RuntimeTestHarness} class, which is why the Test annotation is commented out.
   */
  // @Test
  public void exampleRunTest() throws Exception {
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
   * Test to verify that the compiler returns error while exporting Document view.
   * 
   * @throws Exception
   */
  @Test
  public void disallowExportOfDocumentTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNos = new int[] {4, 6};
    int[] colNos = new int[] {13, 14};

    compileMultipleModulesAndCheckErrors(new String[] {"module1"}, lineNos, colNos);

    endTest();
  }

  /**
   * Test to verify that the compiler returns errors for each incorrect dictionary reference in the
   * predicate functions ContainsDict, MatchesDict and ContainsDicts.<br>
   * This test captures the scenarios mentioned in defect and another related defect# 34951.
   * 
   * @throws Exception
   */
  @Test
  public void incorrectDictRefInPredicatesTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNos = new int[] {10, 24, 24, 34};
    int[] colNos = new int[] {20, 21, 55, 19,};

    compileMultipleModulesAndCheckErrors(new String[] {"module1"}, lineNos, colNos);

    endTest();
  }

  /**
   * Test to verify that the compiler reports error for all the incorrect dictionary reference in a
   * complex predicate, formed by logical AND of the following predicate functions viz ContiansDict,
   * MatchesDict and ContainsDicts.<br>
   * This test captures the scenario mentioned in defect# 34951.
   * 
   * @throws Exception
   */
  @Test
  public void incorrectDictRefInPredicatesTest2() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNos = new int[] {13, 14, 16};
    int[] colNos = new int[] {23, 23, 23};

    compileMultipleModulesAndCheckErrors(new String[] {"module1"}, lineNos, colNos);

    endTest();
  }

  /**
   * Test to verify that the compiler handles the un-recoverable lexer error returned for unknown
   * token in the AQL file gracefully and continue compiling the remaining AQL files in the
   * module.<br>
   * This test captures the scenario mentioned in defect and defect#33820.
   * 
   * @throws Exception
   */
  @Test
  public void unknownAQLTokenTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNos = new int[] {4, 12, 12,};
    int[] colNos = new int[] {15, 8, 11};

    compileMultipleModulesAndCheckErrors(new String[] {"module1"}, lineNos, colNos);

    endTest();
  }

  /**
   * Test to verify that the compiler returns error, if the defined output alias names collides with
   * any of the declared view fully qualified name.
   */
  @Test
  public void outputAliasConflictWithViewName() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNos = new int[] {8};
    int[] colNos = new int[] {32};

    compileMultipleModulesAndCheckErrors(new String[] {"module1"}, lineNos, colNos);

    endTest();
  }

  /**
   * Test to verify that parser error with reference to
   * NICKNAME/SQL_STRING_LITERAL/DBLQUOTE_STRING_LITERAL/REGEX_LITERAL token are translated to more
   * user friendly textual representations. This test case is associated to task#26477.
   * 
   * @throws Exception
   */
  @Test
  public void parseErrorMsgTest() throws Exception {
    startTest();

    try {
      compileModule("module1");
    } catch (CompilerException ce) {
      List<Exception> allCompileErrors = ce.getAllCompileErrors();
      // Compare actual message in the exception
      String errorMsg = ((ParseException) allCompileErrors.get(0)).getErrorDescription();
      System.err.println(errorMsg);
      Assert.assertEquals(
          "Encountered  \"Simple identifier\" \"view1 \". Was expecting one of: \"view\" , \"table\" , \"external\" , \"function\" , \"dictionary\" ",
          errorMsg);

      errorMsg = ((ParseException) allCompileErrors.get(1)).getErrorDescription();
      System.err.println(errorMsg);
      Assert.assertEquals(
          "Encountered  \"Regular expression\" \"/\\\\/\\\\*([^*]|\\\\*[^\\\\/])*\\\\*\\\\// \". Was expecting a: \"String constant\" ",
          errorMsg);

      errorMsg = ((ParseException) allCompileErrors.get(2)).getErrorDescription();
      System.err.println(errorMsg);
      Assert.assertEquals(
          "Encountered  \"String constant\" \"\\'testView2\\' \". Was expecting a: \"Simple identifier\" ",
          errorMsg);
      return;
    }

    Assert.fail("Control should not reach here; we expect a compiler exception");
    endTest();
  }

  /**
   * This testcase verifies that the compiler returns, errors with appropriate error location
   * information, for incorrect “select into“statement. This test covers the scenario mentioned in
   * defect#36114. *
   * 
   * @throws Exception
   */
  @Test
  public void verifyErrLocForSelectIntoStmtTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNos = new int[] {10, 14};
    int[] colNos = new int[] {8, 8};

    compileMultipleModulesAndCheckErrors(new String[] {"module1"}, lineNos, colNos);

    endTest();
  }

  /**
   * Test to verify that the compiler does not allow outputting a local view (view declared in
   * current module) more than once in the module scope.
   * 
   * @throws Exception
   */
  @Test
  public void multipleLocalViewOutputsTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNos = new int[] {10, 5};
    int[] colNos = new int[] {1, 1};

    compileMultipleModulesAndCheckErrors(new String[] {"module1"}, lineNos, colNos);

    endTest();
  }

  /**
   * Test to verify that the compiler does not allow outputting an imported view more than once in
   * the module scope.
   * 
   * @throws Exception
   */
  @Test
  public void multipleImportedViewOutputsTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNos = new int[] {9, 11};
    int[] colNos = new int[] {1, 1};

    compileMultipleModulesAndCheckErrors(new String[] {"module2", "module1"}, lineNos, colNos);

    endTest();
  }

  /**
   * Test to verify that the parser will validate the number of arguments in a table declaration.
   * Fixes defect : ArrayIndexOutOfBounds when parsing table rows with an incorrect number of values
   * 
   * @throws Exception
   */
  @Test
  public void incorrectTableArgsTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNos = new int[] {6, 10, 20};
    int[] colNos = new int[] {14, 14, 73};

    compileMultipleModulesAndCheckErrors(new String[] {"incorrectTableArgs"}, lineNos, colNos);

    endTest();
  }

  /**
   * Test to verify that the compiler reports the cycle among the views in a module. This test
   * capture the scenario mentioned in defect# 36799.
   * 
   * @throws Exception
   */
  @Test
  public void cycleAmongViewsTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNos = new int[] {5, 4};
    int[] colNos = new int[] {13, 13};

    compileMultipleModulesAndCheckErrors(new String[] {"module1", "module2"}, lineNos, colNos);

    endTest();
  }

  /**
   * defect : Verify that importing a function twice should not throw an NPE, and that aliases can
   * allow a function to be imported multiple times
   * 
   * @throws Exception
   */
  @Test
  public void functionNamespaceTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNos = new int[] {15, 22};
    int[] colNos = new int[] {1, 8};

    compileMultipleModulesAndCheckErrors(new String[] {"module1", "module2"}, lineNos, colNos);

    endTest();
  }

  /**
   * defect : Verify that importing a view twice should not throw an NPE, and that aliases can allow
   * a view to be imported multiple times
   * 
   * @throws Exception
   */
  @Test
  public void viewNamespaceTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNos = new int[] {15, 21};
    int[] colNos = new int[] {1, 46};

    compileMultipleModulesAndCheckErrors(new String[] {"module1", "module2"}, lineNos, colNos);

    endTest();
  }

  /**
   * Test to verify that the compiler’s logic, to determine the compilation order among the
   * specified modules to compile, does *not* go into an infinite loop, if one or more of the
   * specified modules does not exist. <br>
   * Expected result: CompilerException containing three exceptions one per nonexistent module.<br>
   * This test captures the scenario mentioned in defect#40178.
   * 
   * @throws Exception
   */
  @Test
  public void compileAllNonExistentModulesTest() throws Exception {
    startTest();

    try {
      compileModules(new String[] {"module1", "module2", "module3"}, null);
      Assert.fail(
          "We don't expect control to reach here; compiler should have thrown CompilerException");
    } catch (CompilerException ce) {
      List<Exception> allCompileErrors = ce.getAllCompileErrors();
      Assert.assertTrue(allCompileErrors.size() == 3);

    }

    endTest();
  }

  /**
   * Test to verify that the compiler’s logic, to determine the compilation order among the
   * specified modules to compile, does *not* go into an infinite loop, if one or more of the
   * specified modules does not exist. <br>
   * Expected result: CompilerException containing exceptions for the nonexistent 'module2' and its
   * dependent module 'module1'. Compiler should compile unrelated module 'module3' and generate
   * corresponding tam, module3.tam.<br>
   * This test captures the scenario mentioned in defect#40178.
   * 
   * @throws Exception
   */
  @Test
  public void compileSomeNonExistentModulesTest() throws Exception {
    startTest();

    try {
      compileModules(new String[] {"module1", "module2", "module3"}, null);
      Assert.fail(
          "We don't expect control to reach here; compiler should have thrown CompilerException");
    } catch (CompilerException ce) {
      List<Exception> allCompileErrors = ce.getAllCompileErrors();
      Assert.assertTrue(allCompileErrors.size() == 4);

      compareAgainstExpected("module3.tam", false);

    }
    endTest();
  }

  /**
   * Test to validate defect : Spurious Parse Exception: Wrong type or value; SLIN constructor
   * should have detected this error.
   * 
   * @throws Exception
   */
  @Test
  public void nickNameRequiredErrorMsgTest() throws Exception {
    startTest();

    // Expect exceptions at these locations
    int[] lineNo = new int[] {9, 17, 22};
    int[] colNo = new int[] {9, 8, 8};

    compileModuleAndCheckErrors("module1", lineNo, colNo);

    endTest();
  }

  /**
   * Test to verify that the compiler reports error with appropriate location information for detag
   * statements referring undefined input view.<br>
   * This test captures the scenario mentioned in defect#38408.
   * 
   * @throws Exception
   */
  @Test
  public void detagUndefinedInputViewTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNos = new int[] {4};
    int[] colNos = new int[] {7};

    compileModuleAndCheckErrors("module1", lineNos, colNos);
  }

  /**
   * Test to verify that the compiler reports error with appropriate error location, when dictionary
   * files referred in AQL 'create dictionary ..', 'extract dictionary(s)...' and predicate
   * functions ContainsDict/ContainsDicts/MatchesDict statement are in invalid format.<br>
   * This test captures the scenario mentioned in defect#33809.
   * 
   * @throws Exception
   */
  @Test
  public void invalidDictFileFormatTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNos = new int[] {6, 13, 18, 31};
    int[] colNos = new int[] {20, 1, 20, 20};

    compileModuleAndCheckErrors("module1", lineNos, colNos);

    endTest();
  }

  /**
   * Test to verify that compiler reports error with appropriate error location, when extracting
   * parts of speech using an imported or external mapping table.
   * 
   * @throws Exception
   */
  @Test
  public void importPosMappingTableTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNo = new int[] {12, 21};
    int[] colNo = new int[] {41, 41};

    compileMultipleModulesAndCheckErrors(new String[] {"module1", "module2"}, lineNo, colNo);

    endTest();
  }

  /**
   * Test case for task# 17245: Verify that CompilerWarnings are generated when there is no
   * CompilerException.
   * 
   * @throws Exception
   */
  @Test
  public void warningsWithoutExceptionTest() throws Exception {
    startTest();

    WarningType[] warningTypes = new WarningType[] {WarningType.RSR_FOR_THIS_REGEX_NOT_SUPPORTED,
        WarningType.MODULE_COMMENT_LOCATION_IS_DIRECTORY};

    CompilationSummary summary = compileModules(new String[] {"module1", "module2"}, null);
    verifyWarnings(summary, warningTypes);

    endTest();
  }

  /**
   * Test case for task# 17245: Verify that CompilerWarnings are generated when there is
   * CompilerException, and that the warnings can be accessed through compilationSummary object.
   */
  @Test
  public void warningsWithExceptionTest() throws Exception {
    startTest();

    WarningType[] warningTypes = new WarningType[] {WarningType.RSR_FOR_THIS_REGEX_NOT_SUPPORTED};

    try {
      compileModules(new String[] {"module1", "module2"}, null);
    } catch (CompilerException e) {
      CompilationSummary s = e.getCompileSummary();
      verifyWarnings(s, warningTypes);
      return;
    }
    Assert.fail("A compile exception was expected");
  }

  /**
   * Test case for defect : When running the jaql with incorrect dfs URI for module path, the
   * Detailed message is null
   */
  @Test
  public void nullDetailedMsgTest() throws Exception {
    startTest();

    // Use an invalid GPFS URI as the module path
    final String INVALID_MODULE_PATH = "gpfs:///this/is/not/a/valid/path";

    // We should get an error message containing one of these strings
    final String ERROR_SUBSTR = "Detailed message: Wrong FS: gpfs";
    final String ERROR_SUBSTR_2 =
        "Detailed message: Error instantiating GPFS FileOperations object";

    boolean caughtException = false;

    try {
      runModule(new File(TestConstants.ENRON_100_DUMP), "dummyModule", INVALID_MODULE_PATH);
    }
    // if test is running without Hadoop in classpath, we'll get a ClassNotFoundException wrapped as
    // a
    // ModuleLoadException
    catch (ModuleLoadException e) {
      // We expect to catch an error about the invalid module path (ERROR_SUBSTR)
      // or an error about instantiating the GPFS FileOps object (ERROR_SUBSTR2)
      // [the latter only when run on a system without Hadoop in the classpath]
      if (false == e.getMessage().contains(ERROR_SUBSTR)
          && false == e.getMessage().contains(ERROR_SUBSTR_2)) {
        throw new TextAnalyticsException(e, "Caught unexpected exception: %s", e);
      }
      caughtException = true;
    }

    if (false == caughtException) {
      throw new TextAnalyticsException("Did not catch exception as expected.");
    }

    endTest();
  }

  /**
   * Test case for defect : Validation of builtin function parameters from execute layer to compile
   * layer is not working
   */
  @Test
  public void badJoinPredTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNo = new int[] {13, 24, 35, 43, 51, 61, 69};
    int[] colNo = new int[] {7, 7, 7, 8, 8, 8, 8};

    compileMultipleModulesAndCheckErrors(new String[] {"badJoinPredTest"}, lineNo, colNo);

    endTest();
  }

  /**
   * Test case for defect . Verifies that the compiler does not throw an error with null message.
   * 
   * @throws Exception
   */
  @Test
  public void nullCompileErrMsgTest() throws Exception {
    startTest();

    String[] modulesToCompile = {"MSCaseSentiment", "MSTaxon", "Sentiment", "SentimentCustom",
        "SentimentCustomTables", "SentimentMacros"};

    String[] moduleURIs = new String[modulesToCompile.length];
    for (int i = 0; i < modulesToCompile.length; i++) {
      moduleURIs[i] = new File(getCurTestDir(), modulesToCompile[i]).toURI().toString();
    }

    File tamBaseDir = getPreCompiledModuleDir();
    String path1 = tamBaseDir.toURI().toString();
    String path2 = new File(tamBaseDir, "sentences/fieldBased").toURI().toString();
    String path3 = getCurOutputDir().toURI().toString();
    String modulePath =
        path1 + Constants.MODULEPATH_SEP_CHAR + path2 + Constants.MODULEPATH_SEP_CHAR + path3;

    CompileAQLParams params = new CompileAQLParams();
    params.setInputModules(moduleURIs);
    params.setModulePath(modulePath);
    params.setOutputURI(getCurOutputDir().toURI().toString());
    params.setTokenizerConfig(getTokenizerConfig());

    // The AQLs should compile without any errors
    CompileAQL.compile(params);
    endTest();
  }

  /**
   * Test case for defect : Validation of Group By statement results in compiler error with no token
   * info and null message
   * 
   * @throws Exception
   */
  @Test
  public void groupByErrorTest() throws Exception {
    startTest();

    // We should get an error message containing this string:
    // Error Emitted by schemaInferrer.computeSchema that checks groupByClause
    final String ERROR_SUBSTR = "Error dereferencing column name S.text ";
    // Error emitted by AQLStatementValidator.validateSelect (calling validateColRef). This is
    // masked by previous check.
    // final String ERROR_SUBSTR = "Name 'S' of column reference 'S.text' not found in from list";

    try {
      compileModules(new String[] {"module1"}, null);
    } catch (CompilerException e) {
      Assert.assertTrue("Exception message wrong.", e.getMessage().contains(ERROR_SUBSTR));
      return;
    }
    Assert.fail("A compile exception was expected");

    endTest();
  }

  /**
   * Test case for defect : Float constants don't work and compiler exception not surfaced. <br/>
   * Verifies that float constants can be used in select list of create view statement without
   * generating any compilation error.
   * 
   * @throws Exception
   */
  @Test
  public void floatConstantsTest() throws Exception {
    startTest();
    compileModule("float");
    endTest();
  }

  /**
   * Test case for defect : AQL compilation loops forever or gives "null" error when there is a
   * cycle in AQL. <br/>
   * Compiles the example AQL code from the defect without the auxiliary compiled modules that it
   * depends on. This compilation used to throw a NullPointerException.
   */
  @Test
  public void cycleBugTest1() throws Exception {
    startTest();

    int[] lineNos = new int[] {3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 299, 300, 301, 302, 303,
        304, 306, 307, 330, 334, 357, 373, 379, 3, 4, 301, 301};
    int[] colNos = new int[] {55, 58, 55, 56, 46, 46, 51, 47, 53, 55, 58, 43, 17, 17, 17, 17, 18,
        18, 20, 21, 20, 20, 20, 1, 77, 58, 43, 25, 60};

    compileModuleAndCheckErrors("travel", lineNos, colNos);

    endTest();
  }

  /**
   * Test case for defect : AQL compilation loops forever or gives "null" error when there is a
   * cycle in AQL. <br/>
   * Compiles the example AQL code from the defect *with* the auxiliary compiled modules that it
   * depends on. This compilation used to throw a fatal internal error.
   */
  @Test
  public void cycleBugTest2() throws Exception {
    startTest();

    // Copy compiled tam files into the directory where the test harness expects them to reside.
    final File TAMS_DIR = new File(getCurTestDir(), "tams");
    final File[] tams = TAMS_DIR.listFiles();
    for (File src : tams) {
      File dest = new File(getCurOutputDir(), src.getName());
      FileUtils.copyFile(src, dest);
    }

    // Now compile with the auxiliary tams and check for the proper set of errors
    int[] lineNos = new int[] {34};
    int[] colNos = new int[] {13};

    compileModuleAndCheckErrors("travel", lineNos, colNos);

    endTest();
  }

  /**
   * Test case for defect : AQL compilation loops forever or gives "null" error when there is a
   * cycle in AQL. <br/>
   * A simplified version of {@link #cycleBugTest2()}
   */
  @Test
  public void cycleBugTest3() throws Exception {
    startTest();

    // Copy compiled tam files into the direcotry where the test harness expects them to reside.
    final File TAMS_DIR = new File(getCurTestDir(), "tams");
    final File[] tams = TAMS_DIR.listFiles();
    for (File src : tams) {
      File dest = new File(getCurOutputDir(), src.getName());
      FileUtils.copyFile(src, dest);
    }

    // Now compile with the auxiliary tams and check for the proper set of errors
    int[] lineNos = new int[] {20};
    int[] colNos = new int[] {13};

    compileModuleAndCheckErrors("travel", lineNos, colNos);

    endTest();
  }

  /**
   * Test case for defect (a) : Group ID of regex sub-group must be non-negative
   */
  @Test
  public void regexGroupIDNonNegativityTest() throws Exception {
    startTest();

    int[] lineNos = new int[] {5};
    int[] colNos = new int[] {22};

    // Group ID must be non-negative
    compileModuleAndCheckErrors("checkForNegativeRegexGroupID", lineNos, colNos);

    endTest();
  }

  /**
   * Test case for defect (b) : Group ID of regex sub-group must not be greater than the total
   * number of sub-groups within the regex
   */
  @Test
  public void regexGroupIDValidityTest() throws Exception {
    startTest();

    int[] lineNos = new int[] {4};
    int[] colNos = new int[] {9};

    // Group ID can't be greater than the actual number of sub-groups in the regular expression
    compileModuleAndCheckErrors("checkForRegexGroupIDUpperBound", lineNos, colNos);

    endTest();
  }

  /**
   * Test case for task 38268: agnostic order support for CombineSpans and SpanBetween <br/>
   * Verifies all syntax errors are caught: non-camelcase, misspelled flag, flag in wrong argument
   */
  @Test
  public void agnosticOrderTest() throws Exception {
    startTest();

    int[] lineNos = new int[] {35, 41, 47};
    int[] colNos = new int[] {8, 8, 8};

    // Group ID can't be greater than the actual number of sub-groups in the regular expression
    compileModuleAndCheckErrors("agnostic", lineNos, colNos);

    endTest();
  }

  /**
   * Test case for task 74927: NPE with bad flag to extract regex <br/>
   * Enable this when fixing the defect.
   */
  @Test
  public void badFlagTest() throws Exception {
    startTest();

    int[] lineNos = new int[] {4};
    int[] colNos = new int[] {45};

    compileModuleAndCheckErrors("badFlagTest", lineNos, colNos);

    endTest();
  }

  /**
   * Test case for task 109307 : Be sure mismatched case types still fails
   */
  @Test
  public void caseBadTypesTest() throws Exception {
    startTest();

    int[] lineNos = new int[] {17, 28, 39, 49};
    int[] colNos = new int[] {19, 21, 21, 21};

    compileModuleAndCheckErrors("caseBadTypesTest", lineNos, colNos);

    endTest();
  }

  /**
   * Test case for task 101435: Catch consolidation priority type error at compile time <br/>
   */
  @Test
  public void badConsolidatePriorityTypeTest() throws Exception {
    startTest();

    int[] lineNos = new int[] {12, 18, 24, 32, 40, 46, 54, 61, 68};
    int[] colNos = new int[] {44, 9, 8, 42, 42, 42, 42, 42, 42};

    compileModuleAndCheckErrors("badConsolidatePriorityTypeTest", lineNos, colNos);

    endTest();
  }

  /**
   * Test case for RTC 112329: Catch consolidation target type error at compile time <br/>
   */
  @Test
  public void badConsolidateTargetTypeTest() throws Exception {
    startTest();

    int[] lineNos = new int[] {16, 24};
    int[] colNos = new int[] {20, 18};

    compileModuleAndCheckErrors("badConsolidateTargetTypeTest", lineNos, colNos);

    endTest();
  }

  /**
   * Test case for RTC 161019 <br/>
   */
  @Test
  public void docSchemaTextOnlyBugTest() throws Exception {
    startTest();
    compileModule("docSchemaTextOnlyBugTest");
    endTest();
  }

  /**
   * Test case for defect : Null tokenizer type in dictionary error
   * 
   * @throws Exception
   */
  @Test
  public void incorrectEscapedDictEntryTest() throws Exception {
    startTest();

    Pattern expectedErrorPattern = Pattern.compile(
        ".*An error occurred while parsing and de-escaping dictionary entry '.*' in line 3 of dictionary: 'test.dict'.*",
        Pattern.DOTALL);

    try {
      compileModules(new String[] {"module1"}, null);
    } catch (CompilerException e) {

      Assert.assertTrue(String.format(
          "Exception does not match expected pattern.\nException message is: %s\nPattern is: %s",
          e.getMessage(), expectedErrorPattern.pattern()),
          expectedErrorPattern.matcher(e.getMessage()).matches());
      return;
    }

    Assert.fail("A compile exception was expected");

    endTest();
  }

  /*
   * ADD NEW TEST CASES HERE
   */

  /*
   * UTILITY METHODS
   */

  /**
   * Utility method to compile multiple modules and check for error line numbers and column
   * numbers.Assumes that the source code for all the modules to compile are located in
   * 
   * <pre>
   * testdata/aql/[test class name]/[test case name]/[module name]
   * </pre>
   * 
   * @param moduleToCompile name of the modules to be compiled
   * @param lineNo list of line numbers where an Exception is expected. If expecting 0 exceptions,
   *        pass a null
   * @param colNo list of column numbers where an Exception is expected. If expecting 0 exceptions,
   *        pass a null. If the caller is expecting a ParseException with a line number, but no
   *        column number, specify -1 as the column number.
   * @return
   * @throws Exception
   */
  @Override
  protected CompilationSummary compileMultipleModulesAndCheckErrors(String[] moduleToCompile,
      int[] lineNos, int[] colNos) throws Exception {
    CompilationSummary summary;
    try {
      File testAQLDir = getCurTestDir();

      if (false == testAQLDir.exists()) {
        throw new Exception(String.format("Directory containing modules %s not found.",
            testAQLDir.getAbsolutePath()));
      }

      // Some basic sanity check
      if (null == moduleToCompile)
        throw new Exception("Provide at least one module to compiler");

      // Construct module URIs from module names
      String[] moduleToCompileURIs = new String[moduleToCompile.length];
      for (int i = 0; i < moduleToCompileURIs.length; i++) {
        String uri = new File(testAQLDir, moduleToCompile[i]).toURI().toString();
        moduleToCompileURIs[i] = uri;
      }

      // Prepare compilation parameter
      CompileAQLParams param = new CompileAQLParams();
      param.setInputModules(moduleToCompileURIs);
      param.setOutputURI(getCurOutputDir().toURI().toString());
      param.setTokenizerConfig(getTokenizerConfig());

      // Compile
      summary = CompileAQL.compile(param);
      Assert.fail("Expected compiler errors, but no errors were thrown");
    } catch (CompilerException ce) {
      checkException(ce, lineNos, colNos);
      summary = ce.getCompileSummary();
    }
    return summary;
  }

}
