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
import java.util.ArrayList;

import org.junit.Test;

import com.ibm.avatar.algebra.util.test.MemoryProfiler;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.algebra.util.tokenize.TokenizerConfig;
import com.ibm.avatar.api.CompilationSummaryImpl;
import com.ibm.avatar.api.CompileAQL;
import com.ibm.avatar.api.CompileAQLParams;
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.aql.planner.Planner;
import com.ibm.avatar.logging.Log;

/**
 * Test suite to verify null argument handling and 3-valued logic
 * 
 */
public class NullHandlingTests extends RuntimeTestHarness {
  public static final File JSON_DOCS_FILE =
      new File(TestConstants.TEST_DOCS_DIR, "/ThreeValueLogicTests/lineup.json");

  public static final File CSV_DOCS_FILE =
      new File(TestConstants.TEST_DOCS_DIR, "/ThreeValueLogicTests/lineup.csv");

  // Main method for running one test at a time.
  public static void main(String[] args) {
    try {

      NullHandlingTests t = new NullHandlingTests();

      // t.setUp ();

      long startMS = System.currentTimeMillis();

      t.basicNullArgCompileTest();

      long endMS = System.currentTimeMillis();

      // t.tearDown ();

      double elapsedSec = (endMS - startMS) / 1000.0;

      System.err.printf("Test took %1.3f sec.\n", elapsedSec);

      MemoryProfiler.dumpHeapSize("After test");

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /*****************************************************************************************
   * Comprehensive null handling tests
   *****************************************************************************************/

  /**
   * Verifies that nulls are properly resolved as arguments by all logical predicate functions <br>
   * (And, Equals, Not, Or)
   * 
   * @throws Exception
   */
  @Test
  public void logicalPredicateTests() throws Exception {
    startTest();
    super.setPrintTups(true);

    final File DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR,
        String.format("%s/boolInput.del", getClass().getSimpleName()));
    OperatorGraph og = compileAndLoadModule("nullargs", null);
    annotateAndPrint(DOCS_FILE, og);
    compareAgainstExpected(false);

    endTest();
  }

  /**
   * Verifies that nulls are properly resolved as arguments by all span-input predicate functions
   * <br>
   * (Contains*, Follows*, GreaterThan, Matches*, Overlaps)
   * 
   * @throws Exception
   */
  @Test
  public void spanPredicateTests() throws Exception {
    startTest();
    super.setPrintTups(true);

    final File DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR,
        String.format("%s/spanInput.del", getClass().getSimpleName()));
    OperatorGraph og = compileAndLoadModule("nullargs", null);
    annotateAndPrint(DOCS_FILE, og);
    compareAgainstExpected(false);

    endTest();
  }

  /**
   * Verifies that nulls are properly resolved as arguments by all span-input scalar functions <br>
   * (CombineSpans, GetLanguage, LeftContext*, RightContext*, Remap, SpanBetween, SpanIntersection,
   * SubSpanTok, ToLowerCase, GetBegin, GetEnd, GetLength, GetLengthTok)
   * 
   * @throws Exception
   */
  @Test
  public void spanScalarTests() throws Exception {
    startTest();
    super.setPrintTups(true);

    final File DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR,
        String.format("%s/spanInput.del", getClass().getSimpleName()));
    OperatorGraph og = compileAndLoadModule("nullargs", null);
    annotateAndPrint(DOCS_FILE, og);
    compareAgainstExpected(false);

    endTest();
  }

  /**
   * Verifies that nulls are properly resolved as arguments by all aggregate functions <br>
   * (Avg, Count, List, Max, Min, Sum) <br>
   * <br>
   * All aggregate functions ignore all null inputs. With the exception of Count, if all inputs are
   * null, <br>
   * then the function returns null. Count never returns null, just the number of non-null inputs.
   * <br>
   * <br>
   * Also validates that group-by on a column with multiple null values results in a single group,
   * not multiple null groups.
   * 
   * @throws Exception
   */
  @Test
  public void aggregateFuncTests() throws Exception {
    startTest();
    super.setPrintTups(true);

    final File DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR,
        String.format("%s/aggInput.del", getClass().getSimpleName()));
    OperatorGraph og = compileAndLoadModule("nullargs", null);
    annotateAndPrint(DOCS_FILE, og);
    compareAgainstExpected(false);

    endTest();
  }

  /**
   * Verifies that nulls are properly resolved by logical operators <br>
   * (having clause, case-when clause, where clause) <br>
   * <br>
   * In all of these situations, null (UNKNOWN) should be treated as FALSE
   * 
   * @throws Exception
   */
  @Test
  public void logicalOperatorTests() throws Exception {
    startTest();
    super.setPrintTups(true);

    final File DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR,
        String.format("%s/logicalOperators.del", getClass().getSimpleName()));

    OperatorGraph og = compileAndLoadModule("nullargs", null);
    annotateAndPrint(DOCS_FILE, og);
    compareAgainstExpected(false);

    endTest();
  }

  /**
   * Verifies the new IsNull() function is working properly
   * 
   * @throws Exception
   */
  @Test
  public void isNullTest() throws Exception {
    startTest();
    super.setPrintTups(true);

    final File DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR,
        String.format("%s/aggInput.del", getClass().getSimpleName()));

    OperatorGraph og = compileAndLoadModule("nullargs", null);
    annotateAndPrint(DOCS_FILE, og);
    compareAgainstExpected(false);

    endTest();
  }

  /**
   * Verifies the expected output of null values in fields
   * 
   * @throws Exception
   */
  @Test
  public void nullOutputTests() throws Exception {
    startTest();
    super.setPrintTups(true);

    final File DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR,
        String.format("%s/iewtNull.del", getClass().getSimpleName()));
    OperatorGraph og = compileAndLoadModule("nullValues", null);
    annotateAndPrint(DOCS_FILE, og);
    compareAgainstExpected(false);

    endTest();
  }

  /**
   * Verifies that the order-by operator properly sorts nulls lower than other tuples.
   * 
   * @throws Exception
   */
  @Test
  public void orderByTest() throws Exception {
    startTest();
    super.setPrintTups(true);

    final File DOCS_FILE = new File(TestConstants.DUMPS_DIR + "/enron1k.del");

    OperatorGraph og = compileAndLoadModule("nullValues", null);
    annotateAndPrint(DOCS_FILE, og);
    compareAgainstExpected(false);

    endTest();
  }

  /**
   * Verifies that all consolidate policies work correctly with null tuples
   * 
   * @throws Exception
   */
  @Test
  public void consolidateTests() throws Exception {
    startTest();
    super.setPrintTups(true);

    final File DOCS_FILE = new File(TestConstants.DUMPS_DIR + "/enron1k.del");

    OperatorGraph og = compileAndLoadModule("nullValues", null);
    annotateAndPrint(DOCS_FILE, og);
    compareAgainstExpected(false);

    endTest();
  }

  /**
   * Verifies that all extraction specifications can handle nulls as inputs
   * 
   * @throws Exception
   */
  @Test
  public void extractStmtTests() throws Exception {
    startTest();
    super.setPrintTups(true);

    final File DOCS_FILE = new File(TestConstants.DUMPS_DIR + "/enron1k.del");

    String[] moduleNames = new String[] {"nullValues"};

    // Compute the location of the current test case's top-level AQL file.
    File aqlModuleDir = getCurTestDir();

    String compiledModuleURI;

    // URI where compiled modules should be dumped -- set to regression/actual
    compiledModuleURI = new File(String.format("%s", getCurOutputDir())).toURI().toString();

    // set up the directories containing the input modules we want to compile
    ArrayList<String> moduleDirectories = new ArrayList<String>();

    for (String moduleName : moduleNames) {
      String moduleDirectoryName = new File(String.format("%s/%s", aqlModuleDir, moduleName))
          .getCanonicalFile().toURI().toString();

      moduleDirectories.add(moduleDirectoryName);
    }

    // Compile
    CompileAQLParams params = new CompileAQLParams(moduleDirectories.toArray(new String[1]),
        compiledModuleURI, compiledModuleURI, TestConstants.STANDARD_TOKENIZER);

    CompileAQL.compile(params);

    OperatorGraph og = OperatorGraph.createOG(moduleNames, compiledModuleURI, null,
        TestConstants.STANDARD_TOKENIZER);
    annotateAndPrint(DOCS_FILE, og);
    compareAgainstExpected(false);

    endTest();
  }

  /**
   * Verifies that modules can compile with a null argument to a scalar function
   * 
   * @throws Exception
   */
  @Test
  public void basicNullArgCompileTest() throws Exception {
    startTest();
    super.setPrintTups(true);
    compileModule("nullargs");
    endTest();
  }

  /**
   * Verifies that nulls can be unionized with correct type inference, and handled as an argument to
   * a scalar function
   * 
   * @throws Exception
   */
  @Test
  public void nullUnionTest() throws Exception {
    startTest();
    super.setPrintTups(true);

    final File DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR,
        String.format("%s/iewtNull.del", getClass().getSimpleName()));
    OperatorGraph og = compileAndLoadModule("nullValues", null);
    annotateAndPrint(DOCS_FILE, og);
    compareAgainstExpected(false);

    endTest();
  }

  /**
   * Verifies that nulls are minus'ed with correct type inference
   * 
   * @throws Exception
   */
  @Test
  public void nullMinusTest() throws Exception {
    startTest();
    super.setPrintTups(true);

    final File DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR,
        String.format("%s/iewtNull.del", getClass().getSimpleName()));
    OperatorGraph og = compileAndLoadModule("nullValues", null);
    annotateAndPrint(DOCS_FILE, og);
    compareAgainstExpected(false);

    endTest();
  }

  /**
   * Verifies that nulls can be handled as an argument to many functions in production code
   * 
   * @throws Exception
   */
  @Test
  public void iewtNullTest() throws Exception {
    startTest();
    super.setPrintTups(true);

    final File DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR,
        String.format("%s/iewtNull.del", getClass().getSimpleName()));
    OperatorGraph og = compileAndLoadModule("nullValues", null);
    annotateAndPrint(DOCS_FILE, og);
    compareAgainstExpected(false);

    endTest();
  }

  /**
   * Verifies that nulls are handled correctly for all join predicates
   * 
   * @throws Exception
   */
  @Test
  public void nullJoinTests() throws Exception {
    startTest();
    super.setPrintTups(true);

    String moduleName = "nullargs";

    final File DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR,
        String.format("%s/joinInput.del", getClass().getSimpleName()));
    OperatorGraph og = compileAndLoadModule(moduleName, null);

    // verify that the optimizer is still creating AOG's with all joins in this AQL file
    assertModuleContainsJoin("NLJoin", moduleName);
    assertModuleContainsJoin("AdjacentJoin", moduleName);
    assertModuleContainsJoin("HashJoin", moduleName);
    assertModuleContainsJoin("RSEJoin", moduleName);
    assertModuleContainsJoin("SortMergeJoin", moduleName);

    annotateAndPrint(DOCS_FILE, og);

    compareAgainstExpected(false);

    endTest();
  }

  /**
   * Verifies that nulls are handled identically by different join predicates. Runs multiple AQL
   * optimized for a different plan, with different planners and then compares the results. For
   * example, AdjacentJoin is a module that contains AQL optimized to use AdjacentJoin without
   * forcing a planner We run this module for each planner and compare the results.
   * 
   * @throws Exception
   */
  @Test
  public void nullJoinTests2() throws Exception {
    com.ibm.avatar.aql.compiler.Compiler compiler = null;
    startTest();
    setPrintTups(true);

    // joins that should result in the same tuples
    // hash join throws out nulls for optimization so it's not included
    String[] joins = {"AdjacentJoin", "SortMergeJoin", "RSEJoin", "NLJoin"};

    // for each planner implementation, compile each of the modules and then compare with gold
    // standard
    for (Planner.ImplType plannerType : Planner.ImplType.values()) {
      // clean the output directory to avoid overwriting the same file
      setOutputDir(getCurOutputDir().toString());

      for (String joinTest : joins) {

        String moduleName = joinTest;
        Log.info(
            "----------------------------\nCompiling module %s with planner %s...\n----------------------------",
            moduleName, plannerType.toString());

        // Compute the location of the current test case's top-level AQL file.
        File aqlModuleDir = getCurTestDir();
        // URI where compiled modules should be dumped -- set to regression/actual
        String compiledModuleURI =
            new File(String.format("%s", getCurOutputDir())).toURI().toString();

        compiler = new com.ibm.avatar.aql.compiler.Compiler(compiledModuleURI);

        // set up the directories containing the input modules we want to compile
        ArrayList<String> moduleDirectories = new ArrayList<String>();

        String moduleDirectoryName = new File(String.format("%s/%s", aqlModuleDir, moduleName))
            .getCanonicalFile().toURI().toString();

        moduleDirectories.add(moduleDirectoryName);

        // Compile
        CompileAQLParams params = new CompileAQLParams(moduleDirectories.toArray(new String[1]),
            compiledModuleURI, compiledModuleURI, new TokenizerConfig.Standard());

        compiler.setPlanner(new Planner(plannerType));

        // Create the summary object for the given compilation request
        CompilationSummaryImpl summary = new CompilationSummaryImpl(params);

        compiler.setSummary(summary);
        compiler.compile(params);
        compiler.updateSummary();

        for (String join : joins) {
          if (isStringExistInAOG(join, moduleName)) {
            Log.info("AOG uses " + join);
          }
        }

        File DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR,
            String.format("%s/joinInput.del", getClass().getSimpleName()));

        runModule(DOCS_FILE, moduleName, params.getOutputURI());

      }
      // Check that the output of using this planner doesn't deviate from expected
      compareAgainstExpected(false);

    }
  }

  /**
   * Tests of UDFs with null inputs and outputs <br>
   * Also tests the "return null on null input" / "called on null input" clauses
   * 
   * @throws Exception
   */
  @Test
  public void nullUDFTests() throws Exception {
    startTest();
    super.setPrintTups(true);

    String[] moduleNames = new String[] {"nullargs", "nullUDFs"};

    OperatorGraph og = compileAndLoadModules(moduleNames, null);

    final File DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR,
        String.format("%s/boolInput.del", getClass().getSimpleName()));
    annotateAndPrint(DOCS_FILE, og);
    compareAgainstExpected(false);

    endTest();
  }

}
