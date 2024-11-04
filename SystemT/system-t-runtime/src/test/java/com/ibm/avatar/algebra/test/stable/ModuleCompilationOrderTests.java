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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Test;

import com.ibm.avatar.algebra.util.test.MemoryProfiler;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.api.CompilationSummaryImpl;
import com.ibm.avatar.api.CompileAQL;
import com.ibm.avatar.api.CompileAQLParams;
import com.ibm.avatar.api.exceptions.CircularDependencyException;
import com.ibm.avatar.api.exceptions.CompilerException;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.tam.ModuleUtils;

/**
 * Verifies that module compilation order is computed correctly
 * 
 */
public class ModuleCompilationOrderTests extends RuntimeTestHarness {

  // Main method for running one test at a time.
  public static void main(String[] args) {
    try {

      ModuleCompilationOrderTests t = new ModuleCompilationOrderTests();

      // t.setUp ();

      long startMS = System.currentTimeMillis();

      t.testImportModuleWithCyclicDependency();

      long endMS = System.currentTimeMillis();

      // t.tearDown ();

      double elapsedSec = (double) (endMS - startMS) / 1000.0;

      System.err.printf("Test took %1.3f sec.\n", elapsedSec);

      MemoryProfiler.dumpHeapSize("After test");

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /** Directory containing compiled version of the modules */
  public static final String TAM_DIR =
      TestConstants.TESTDATA_DIR + "/tam/ModuleCompilationOrderTests/";

  /**
   * Verifies that the compilation order is properly calculated for a simple scenario as described
   * below: <br/>
   * person.tam: independent module <br/>
   * phone.tam: independent module <br/>
   * personPhone.tam: Depends on person.tam and phone.tam <br/>
   * Calls genericTestCase() with various combinations of these three modules and verifies if
   * compilation order is correctly deduced.
   * 
   * @throws Exception
   */
  @Test
  public void simpleCompilationOrderTest() throws Exception {

    startTest();

    File testAQLDir = getCurTestDir();

    String uriPerson = new File(testAQLDir, "person").toURI().toString();
    String uriPhone = new File(testAQLDir, "phone").toURI().toString();
    String uriPersonPhone = new File(testAQLDir, "personPhone").toURI().toString();

    // Test case 1: Pass them in proper order
    String[] inputModules = new String[] {uriPerson, uriPhone, uriPersonPhone};
    String[] expectedOrder = new String[] {uriPerson, uriPhone, uriPersonPhone};
    genericTestCase(inputModules, expectedOrder, null, null, null, null);

    // Test case 2: A different variation of proper order
    inputModules = new String[] {uriPhone, uriPerson, uriPersonPhone};
    expectedOrder = new String[] {uriPerson, uriPhone, uriPersonPhone};
    genericTestCase(inputModules, expectedOrder, null, null, null, null);

    // Test case 3: Random order 1
    inputModules = new String[] {uriPersonPhone, uriPhone, uriPerson};
    expectedOrder = new String[] {uriPerson, uriPhone, uriPersonPhone};
    genericTestCase(inputModules, expectedOrder, null, null, null, null);

    // Test case 4: Random order 2
    inputModules = new String[] {uriPersonPhone, uriPerson, uriPhone};
    expectedOrder = new String[] {uriPerson, uriPhone, uriPersonPhone};
    genericTestCase(inputModules, expectedOrder, null, null, null, null);

    endTest();
  }

  /**
   * Verifies whether CircularDependencyException is thrown when two modules depend on each other.
   * 
   * @throws Exception
   */
  @Test
  public void circularDependencyTest() throws Exception {

    startTest();

    File testAQLDir = getCurTestDir();

    String uriModuleA = new File(testAQLDir, "moduleA").toURI().toString();
    String uriModuleB = new File(testAQLDir, "moduleB").toURI().toString();
    String[] inputModules = new String[] {uriModuleA, uriModuleB};

    // creating a summary with null parameters since we aren't actually compiling anything
    CompilerException ce = new CompilerException(new CompilationSummaryImpl(null));
    ModuleUtils.prepareCompileOrder(inputModules, null, ce);
    List<Exception> allCompileErrors = ce.getAllCompileErrors();

    if (allCompileErrors.size() == 0)
      Assert.fail("Expected CircularDependencyException, but received none");

    Exception e = allCompileErrors.get(0);
    assertTrue(String.format(
        "Expected CircularDependencyException, but received '%s' instead, with message %s",
        e.getClass().getName(), e.getMessage()), e instanceof CircularDependencyException);

    Integer[] linesOfcyclicImports = {3, 3};

    assertTrue(verifyCircularDependencyLocations(e, linesOfcyclicImports));

    endTest();
  }

  /**
   * Simulates circular dependencies between 5 modules and verifies that CircularDependencyException
   * is thrown. Fix for defect : Circular Dependency error from Runtime is not propagated in Tooling
   * 
   * @throws Exception
   */
  @Test
  public void circularDependency5ModulesTest() throws Exception {
    startTest();

    try {
      compileModules(new String[] {"module2", "module3", "module4", "module5", "module1"}, null);
      Assert.fail("Expected CircularDependencyException, but none received");
    } catch (CompilerException ce) {

      List<Exception> allCompileErrors = ce.getAllCompileErrors();

      Exception e1 = allCompileErrors.get(0);
      Integer[] linesOfcyclicImports1 = {4, 5, 4};

      Exception e2 = allCompileErrors.get(1);
      Integer[] linesOfcyclicImports2 = {5, 5, 5};

      assertTrue(
          String.format("Expected CircularDependencyException, but received %s", e1.getMessage()),
          e1 instanceof CircularDependencyException);
      assertTrue(verifyCircularDependencyLocations(e1, linesOfcyclicImports1));

      assertTrue(
          String.format("Expected CircularDependencyException, but received %s", e2.getMessage()),
          e2 instanceof CircularDependencyException);
      assertTrue(verifyCircularDependencyLocations(e2, linesOfcyclicImports2));
    } catch (Exception e) {
      Assert.fail(
          String.format("Expected CircularDependencyException, but received %s", e.getMessage()));
    }
    endTest();
  }

  /**
   * This test captures the scenario mentioned in defect# 28588: compile two un-related modules,
   * where one module imports itself( self-import).
   * 
   * @throws Exception
   */
  @Test
  public void selfImportTest() throws Exception {
    startTest();

    try {
      compileModules(new String[] {"module1", "module2"}, null);
      Assert.fail("Expected CircularDependencyException, but none received");
    } catch (CompilerException ce) {

      Exception e = ce.getAllCompileErrors().get(0);
      Integer[] linesOfcyclicImports = {3};

      assertTrue(
          String.format("Expected CircularDependencyException, but received %s", e.getMessage()),
          e instanceof CircularDependencyException);
      assertTrue(verifyCircularDependencyLocations(e, linesOfcyclicImports));
    } catch (Exception e) {
      Assert.fail(
          String.format("Expected CircularDependencyException, but received %s", e.getMessage()));
    }
    endTest();
  }

  /**
   * Another scenario to verify self import: moduleA, imports moduleB, moduleC and then a self
   * import moduleA(itself).
   * 
   * @throws Exception
   */
  @Test
  public void selfImport2Test() throws Exception {
    startTest();

    try {
      compileModules(new String[] {"moduleA", "moduleC", "moduleB"}, null);
      Assert.fail("Expected CircularDependencyException, but none received");
    } catch (CompilerException ce) {

      Exception e = ce.getAllCompileErrors().get(0);
      Integer[] linesOfcyclicImports = {7};

      assertTrue(
          String.format("Expected CircularDependencyException, but received %s", e.getMessage()),
          e instanceof CircularDependencyException);

      assertTrue(verifyCircularDependencyLocations(e, linesOfcyclicImports));
    } catch (Exception e) {
      Assert.fail(
          String.format("Expected CircularDependencyException, but received %s", e.getMessage()));
    }
    endTest();
  }

  /**
   * Test case related to defect# 34858.<br>
   * Scenario: There are three modules module1, module2 and module3; module1 imports module2 and
   * module4(unknown: neither found in source module list nor in the module path), module2 imports
   * module3 and module5(unknown) and module3 does not import any other module.<br>
   * Expected result: Compiler should report error for imports of unknown module, and should also
   * return correct compilation order of input source module.
   * 
   * @throws Exception
   */
  @Test
  public void importNonExistentDependentModuleTest() throws Exception {
    startTest();

    File testAQLDir = getCurTestDir();

    String uriModule1 = new File(testAQLDir, "module1").toURI().toString();
    String uriModule2 = new File(testAQLDir, "module2").toURI().toString();
    String uriModule3 = new File(testAQLDir, "module3").toURI().toString();

    String[] inputModules = {uriModule1, uriModule2, uriModule3};
    String[] expectedOrder = {uriModule3, uriModule2, uriModule1};

    // Expect exception at these locations
    int[] lineNos = new int[] {3, 8, 8, 12, 6, 6};
    int[] colNos = new int[] {15, 41, 41, 21, 43, 43};

    genericTestCase(inputModules, expectedOrder, lineNos, colNos, null, null);

    endTest();
  }

  /**
   * Test case related to defect# 34858.<br>
   * Scenario: There are 11 modules, module1, module2, ....module11. Here is the relationship
   * between modules:<br>
   * module1 ---> [module2], this reads as, module1 imports(directly) module2<br>
   * module2 ---> [ module3, module9, module11] <br>
   * module3 ---> [module4]<br>
   * module4 ---> [module5]<br>
   * module5 ---> [module6]<br>
   * module6 ---> [module4]<br>
   * module7 ---> [], that is module7 does not imports any other module, independent module<br>
   * module8 ---> []<br>
   * module9 ---> [module10]<br>
   * module10 ---> [module9]<br>
   * module11 ---> [module1]<br>
   * <br>
   * Expected result: We expect three cycles:<br>
   * (1) module1 -> module2 -> module11 -> module1 <br>
   * (2) module9 -> module10 ->module9 <br>
   * (3) module4 -> module5 -> module6 -> module4.<br>
   * 
   * @throws Exception
   */
  @Test
  public void multipleCircularDependencyTest() throws Exception {
    startTest();

    File testAQLDir = getCurTestDir();

    String uriModule1 = new File(testAQLDir, "module1").toURI().toString();
    String uriModule2 = new File(testAQLDir, "module2").toURI().toString();
    String uriModule3 = new File(testAQLDir, "module3").toURI().toString();
    String uriModule4 = new File(testAQLDir, "module4").toURI().toString();
    String uriModule5 = new File(testAQLDir, "module5").toURI().toString();
    String uriModule6 = new File(testAQLDir, "module6").toURI().toString();
    String uriModule7 = new File(testAQLDir, "module7").toURI().toString();
    String uriModule8 = new File(testAQLDir, "module8").toURI().toString();
    String uriModule9 = new File(testAQLDir, "module9").toURI().toString();
    String uriModule10 = new File(testAQLDir, "module10").toURI().toString();
    String uriModule11 = new File(testAQLDir, "module11").toURI().toString();

    String[] inputModules = {uriModule3, uriModule4, uriModule5, uriModule6, uriModule7, uriModule8,
        uriModule9, uriModule10, uriModule11, uriModule1, uriModule2,};

    String[] expectedOrder = {uriModule10, uriModule11, uriModule6, uriModule7, uriModule8,
        uriModule9, uriModule5, uriModule4, uriModule3, uriModule2, uriModule1};

    List<List<String>> listOfCycle = new ArrayList<List<String>>();
    List<Integer[]> expectedListofLinesOfImport = new ArrayList<Integer[]>();

    List<String> cycle1 = new ArrayList<String>();
    listOfCycle.add(cycle1);
    cycle1.add("module1");
    cycle1.add("module2");
    cycle1.add("module11");

    Integer[] linesOfcyclicImports1 = {3, 7, 3};
    expectedListofLinesOfImport.add(linesOfcyclicImports1);

    List<String> cycle2 = new ArrayList<String>();
    listOfCycle.add(cycle2);
    cycle2.add("module4");
    cycle2.add("module5");
    cycle2.add("module6");

    Integer[] linesOfcyclicImports2 = {3, 3, 3};
    expectedListofLinesOfImport.add(linesOfcyclicImports2);

    List<String> cycle3 = new ArrayList<String>();
    listOfCycle.add(cycle3);
    cycle3.add("module9");
    cycle3.add("module10");

    Integer[] linesOfcyclicImports3 = {3, 3};
    expectedListofLinesOfImport.add(linesOfcyclicImports3);

    genericTestCase(inputModules, expectedOrder, null, null, listOfCycle,
        expectedListofLinesOfImport);

    endTest();

  }

  /*
   * Add more test cases here
   */
  /**
   * Test case1 :defect# 34858.<br>
   * Description: module1 imports module2 that has Compilation and Parser Errors Expected Result: 1.
   * Compiler Exception for module2 which does not exist in module path 2. Parser Exception for the
   * parser errors in module2
   * 
   * @throws Exception
   */
  @Test
  public void testImportModuleWithCompilerError() throws Exception {
    startTest();

    File testAQLDir = getCurTestDir();

    String uriModule1 = new File(testAQLDir, "module1").toURI().toString();
    String uriModule2 = new File(testAQLDir, "module2").toURI().toString();

    String[] inputModules = {uriModule1, uriModule2};
    String[] expectedOrder = {uriModule2, uriModule1};

    // Expect exception at these locations
    int[] lineNos = new int[] {3, 5, 4, 8};
    int[] colNos = new int[] {15, 70, 38, 8};

    genericTestCase(inputModules, expectedOrder, lineNos, colNos, null, null);

    endTest();

  }

  /**
   * Test case2: defect# 34858.<br>
   * Description:<br>
   * 1. cyclicModule1 imports cyclicModule2 <br>
   * 2. cyclicModule2 imports cyclicModule3 <br>
   * 3. cyclicModule3 imports cyclicModule1 <br>
   * 4. importModule imports cyclicModule3<br>
   * Expected Result: <br>
   * 1. Compiler Exception for cyclicModule3,cyclicModule2 and cyclicModule1 which does not exist in
   * module path <br>
   * 2. Circular Dependency exception involving modules: cyclicModule1,cyclicModule2 and
   * cyclicModule3 <br>
   * 3. Order of Compilation possible : cyclicModule3,cyclicModule2,importModule and cyclicModule1
   * (or) <br>
   * cyclicModule3, cyclicModule2, cyclicModule1 and importModul1 (or) <br>
   * cyclicModule3, importModul1 , cyclicModule2 and cyclicModule1 (or)
   * 
   * @throws Exception
   */
  @Test
  public void testImportModuleWithCyclicDependency() throws Exception {

    startTest();

    File testAQLDir = getCurTestDir();

    String uriModule1 = new File(testAQLDir, "cyclicModule1").toURI().toString();
    String uriModule2 = new File(testAQLDir, "cyclicModule2").toURI().toString();
    String uriModule3 = new File(testAQLDir, "cyclicModule3").toURI().toString();
    String uriModule4 = new File(testAQLDir, "importModule").toURI().toString();

    String[] inputModules = {uriModule1, uriModule2, uriModule3, uriModule4};
    String[] expectedOrder = {uriModule3, uriModule4, uriModule2, uriModule1};

    List<List<String>> listOfCycle = new ArrayList<List<String>>();
    List<Integer[]> expectedListofLinesOfImport = new ArrayList<Integer[]>();

    List<String> cycle1 = new ArrayList<String>();
    listOfCycle.add(cycle1);
    cycle1.add("cyclicModule1");
    cycle1.add("cyclicModule2");
    cycle1.add("cyclicModule3");

    Integer[] linesOfcyclicImports = {4, 4, 4};
    expectedListofLinesOfImport.add(linesOfcyclicImports);

    // Expect exception at these locations
    int[] lineNos = new int[] {4, 6, 4, 6, 4, 6, 4, 6};
    int[] colNos = new int[] {31, 63, 31, 63, 31, 63, 15, 82};

    genericTestCase(inputModules, expectedOrder, lineNos, colNos, listOfCycle,
        expectedListofLinesOfImport);

    endTest();

  }

  /**
   * Test case3: defect# 34858.<br>
   * Description: This test case captures Problems reported in comment#9 of defect <br>
   * 1. module1 imports module2 <br>
   * 2. module2 imports module3 <br>
   * 3. module3 imports module4 <br>
   * 4. module4 imports module6,module7<br>
   * 5. module7 imports module10<br>
   * 6. module10 imports module11,module6<br>
   * 7. module11 imports module9<br>
   * 8. module9 imports module6,module8<br>
   * 9. module6 imports module4,module5<br>
   * 10. module9 imports module6,module8<br>
   * 11. module8 imports module5<br>
   * 12. module5 imports module3<br>
   * Possible Cycles for Non deterministic compiler: 1. cycle with modules: module4 and module6 2.
   * cycle with modules: module3,module4,module5 and module6 3. cycle with modules:
   * module3,module4,module7,module10,module6,module5 4. cycle with modules:
   * module3,module4,module7,module10,module11,module9,module6,module5 5. cycle with modules:
   * module3,module4,module7,module10,module11,module9,module8,module5 6. cycle with modules:
   * module4,module7,module10,module6 Expected Result for Deterministic Compiler: <br>
   * 1. Compiler Exception for
   * module2,module3,module4,module5,module6,module7,module8,module9,module10 and module11 <br>
   * 2. Circular Dependency exception involving modules: module4 and module6.<br>
   * 3. Circular Dependency exception involving modules: module3,module4,module6 and module5<br>
   * 3. Order of Compilation expected : module12, module5, module6, module8, module9, module11,
   * module10,module7,<br>
   * module4, module3, module2, module1<br>
   * 
   * @throws Exception
   */
  @Test
  public void circularDependencyTest_2cycles() throws Exception {

    startTest();

    File testAQLDir = getCurTestDir();

    String uriModule1 = new File(testAQLDir, "module1").toURI().toString();
    String uriModule2 = new File(testAQLDir, "module2").toURI().toString();
    String uriModule3 = new File(testAQLDir, "module3").toURI().toString();
    String uriModule4 = new File(testAQLDir, "module4").toURI().toString();
    String uriModule5 = new File(testAQLDir, "module5").toURI().toString();
    String uriModule6 = new File(testAQLDir, "module6").toURI().toString();
    String uriModule7 = new File(testAQLDir, "module7").toURI().toString();
    String uriModule8 = new File(testAQLDir, "module8").toURI().toString();
    String uriModule9 = new File(testAQLDir, "module9").toURI().toString();
    String uriModule10 = new File(testAQLDir, "module10").toURI().toString();
    String uriModule11 = new File(testAQLDir, "module11").toURI().toString();
    String uriModule12 = new File(testAQLDir, "module12").toURI().toString();

    String[] inputModules = {uriModule1, uriModule2, uriModule3, uriModule4, uriModule5, uriModule6,
        uriModule7, uriModule8, uriModule9, uriModule10, uriModule11, uriModule12};

    String[] expectedOrder = {uriModule12, uriModule5, uriModule6, uriModule8, uriModule9,
        uriModule11, uriModule10, uriModule7, uriModule4, uriModule3, uriModule2, uriModule1};

    List<List<String>> listOfCycle = new ArrayList<List<String>>();
    List<Integer[]> expectedListofLinesOfImport = new ArrayList<Integer[]>();

    List<String> cycle1 = new ArrayList<String>();
    listOfCycle.add(cycle1);
    cycle1.add("module4");
    cycle1.add("module6");
    Integer[] linesOfCyclicImports1 = {4, 4};
    expectedListofLinesOfImport.add(linesOfCyclicImports1);

    List<String> cycle2 = new ArrayList<String>();
    listOfCycle.add(cycle2);
    cycle2.add("module3");
    cycle2.add("module4");
    cycle2.add("module6");
    cycle2.add("module5");
    Integer[] linesOfCyclicImports2 = {4, 4, 5, 4};
    expectedListofLinesOfImport.add(linesOfCyclicImports2);

    // Expect exception at these locations
    int[] lineNos = new int[] {4, 4, 5, 4, 4, 4, 4, 5, 4, 4, 5, 4, 4, 4, 5};
    int[] colNos = new int[] {31, 39, 43, 15, 31, 33, 43, 39, 39, 33, 15, 15, 15, 15, 15};

    genericTestCaseWithPrecompiledModules(inputModules, null, expectedOrder, lineNos, colNos,
        listOfCycle, expectedListofLinesOfImport);

    endTest();

  }

  /**
   * Test case4: defect# 34858.<br>
   * Description: This test case captures Problems reported in comment#9 of defect and also uses
   * precompiled tam<br>
   * 1. module1 imports module2 <br>
   * 2. module2 imports module3 <br>
   * 3. module3 imports module4 <br>
   * 4. module4 imports module6,module7<br>
   * 5. module7 imports module10<br>
   * 6. module10 imports module11,module6<br>
   * 7. module11 imports module9<br>
   * 8. module9 imports module6,module8<br>
   * 9. module6 imports module4,module5<br>
   * 10. module8 imports module5<br>
   * 11. module5 imports module3,module12<br>
   * 12. module12 does not depend on any other modules<br>
   * Possible Cycles for Non deterministic compiler:<br>
   * 1. cycle with modules: module4 and module6<br>
   * 2. cycle with modules: module3,module4,module5 and module6<br>
   * 3. cycle with modules: module3,module4,module7,module10,module6,module5<br>
   * 4. cycle with modules: module3,module4,module7,module10,module11,module9,module6,module5<br>
   * 5. cycle with modules: module3,module4,module7,module10,module11,module9,module8,module5<br>
   * 6. cycle with modules: module4,module7,module10,module6 <br>
   * <br>
   * Expected Result for Deterministic Compiler:<br>
   * 1. Compiler Exception for
   * module2,module3,module4,module5,module6,module7,module8,module9,module10 and module11 <br>
   * 2. Circular Dependency exception involving modules: module4 and module6.<br>
   * 3. Circular Dependency exception involving modules: module3,module4,module6 and module5<br>
   * 3. Order of Compilation expected : module12, module5, module6, module8, module9, module11,
   * module10,module7,<br>
   * module4, module3, module2, module1<br>
   * 
   * @throws Exception
   */
  @Test
  public void circularDependencyTestWithPreCompiledTams() throws Exception {

    startTest();

    File testAQLDir = getCurTestDir();

    String uriModule1 = new File(testAQLDir, "module1").toURI().toString();
    String uriModule2 = new File(testAQLDir, "module2").toURI().toString();
    String uriModule3 = new File(testAQLDir, "module3").toURI().toString();
    String uriModule4 = new File(testAQLDir, "module4").toURI().toString();
    String uriModule5 = new File(testAQLDir, "module5").toURI().toString();
    String uriModule6 = new File(testAQLDir, "module6").toURI().toString();
    String uriModule7 = new File(testAQLDir, "module7").toURI().toString();
    String uriModule8 = new File(testAQLDir, "module8").toURI().toString();
    String uriModule9 = new File(testAQLDir, "module9").toURI().toString();
    String uriModule10 = new File(testAQLDir, "module10").toURI().toString();
    String uriModule11 = new File(testAQLDir, "module11").toURI().toString();

    String uriModule12 = new File(TAM_DIR).toURI().toString();// module path having the compiled
                                                              // tams

    String[] inputModules = {uriModule1, uriModule2, uriModule3, uriModule4, uriModule5, uriModule6,
        uriModule7, uriModule8, uriModule9, uriModule10, uriModule11};

    String[] expectedOrder = {uriModule5, uriModule6, uriModule8, uriModule9, uriModule11,
        uriModule10, uriModule7, uriModule4, uriModule3, uriModule2, uriModule1};

    List<List<String>> listOfCycle = new ArrayList<List<String>>();
    List<Integer[]> expectedListofLinesOfImport = new ArrayList<Integer[]>();

    List<String> cycle1 = new ArrayList<String>();
    listOfCycle.add(cycle1);
    cycle1.add("module4");
    cycle1.add("module6");
    Integer[] linesOfCyclicImports1 = {4, 4};
    expectedListofLinesOfImport.add(linesOfCyclicImports1);

    List<String> cycle2 = new ArrayList<String>();
    listOfCycle.add(cycle2);
    cycle2.add("module3");
    cycle2.add("module4");
    cycle2.add("module6");
    cycle2.add("module5");
    Integer[] linesOfCyclicImports2 = {4, 4, 5, 4};
    expectedListofLinesOfImport.add(linesOfCyclicImports2);

    // Expect exception at these locations
    int[] lineNos = new int[] {4, 4, 5, 4, 4, 4, 4, 5, 4, 4, 5, 4, 4, 4, 5};
    int[] colNos = new int[] {15, 31, 43, 33, 15, 33, 43, 39, 39, 33, 31, 43, 31, 43, 39};

    genericTestCaseWithPrecompiledModules(inputModules, uriModule12, expectedOrder, lineNos, colNos,
        listOfCycle, expectedListofLinesOfImport);

    endTest();

  }

  /**
   * Test case: defect <br>
   * Description: This test case is to check the order of Parser exception returned when the module
   * names are similar <br>
   * 1. module1 imports module10 <br>
   * 2. module10 imports module1 <br>
   * Expected Results:The compiler must return the following exceptions in the same order on Windows
   * or RHEL. The gold standard is RHEL. 1. Circular Dependency exception involving modules: module1
   * and module10 <br>
   * 2. A Parser Exception arising out of
   * '/ModuleCompilationOrderTests/ParseExceptionOrdertest/module1/test.aql' line 3,<br>
   * column 15: Module file 'module10.tam' is not found in the specified module path: null <br>
   * 3. A Parser Exception arising out of
   * '/ModuleCompilationOrderTests/ParseExceptionOrdertest/module10/test.aql' line 4, column 15:
   * Module file<br>
   * 'module1.tam' is not found in the specified module path: null<br>
   * 
   * @throws Exception
   */
  @Test
  public void parseExceptionOrderTest() throws Exception {

    startTest();

    File testAQLDir = getCurTestDir();

    String uriModule1 = new File(testAQLDir, "module1").toURI().toString();
    String uriModule10 = new File(testAQLDir, "module10").toURI().toString();

    String[] inputModules = {uriModule1, uriModule10,};

    String[] expectedOrder = {uriModule10, uriModule1};

    List<List<String>> listOfCycle = new ArrayList<List<String>>();
    List<Integer[]> expectedListofLinesOfImport = new ArrayList<Integer[]>();

    List<String> cycle1 = new ArrayList<String>();
    listOfCycle.add(cycle1);
    cycle1.add("module1");
    cycle1.add("module10");
    Integer[] linesOfCyclicImports1 = {3, 4};
    expectedListofLinesOfImport.add(linesOfCyclicImports1);

    // Expect exception at these locations
    int[] lineNos = new int[] {3, 4};
    int[] colNos = new int[] {15, 15};

    genericTestCaseWithPrecompiledModules(inputModules, null, expectedOrder, lineNos, colNos,
        listOfCycle, expectedListofLinesOfImport);

    endTest();

  }

  /**
   * This utility method verifies whether CircularDependencyException has the imports in the
   * expected lines
   * 
   * @param e CompilerException
   * @param linesOfcyclicImports -An Integer array that contains all line numbers which has the
   *        import statement leading to cyclic dependency exception
   * @return true if the line number of the all import statements are found in the error message
   *         false otherwise
   */
  private boolean verifyCircularDependencyLocations(Exception e, Integer[] linesOfcyclicImports) {

    String msg = e.getMessage();
    System.err.printf("\nFound CircularDependencyException: %s", msg);

    // Get the line number and column number of the exception
    Pattern p = Pattern.compile("line number (\\d+)");
    Matcher m = p.matcher(msg);
    int eIx = 0;
    int fails = 0;
    int matches = 0;

    while (m.find())
      matches++;

    assertTrue(String.format(
        "Expected total number of imports in the Circular Dependency is %d. Got a Circular Dependecy with a total imports of %d instead: %s",
        linesOfcyclicImports.length, matches, e.getMessage()),
        matches == linesOfcyclicImports.length);

    m.reset();

    while (m.find()) {
      int actualLineNumber = Integer.parseInt(m.group(1));
      if (linesOfcyclicImports[eIx] == actualLineNumber) {
        eIx++;
        continue;
      } else // Got a CircularDependencyException but a different line number.
      {
        Assert.fail(String.format(
            "Expected a CircularDependencyException on line %d. Got a CircularDependencyException with line number %d instead: %s",
            linesOfcyclicImports[eIx], actualLineNumber, e.getMessage()));
        eIx++;
        fails++;
      }
    }

    if (fails > 0)
      return false;
    else
      return true;

  }

  /**
   * Generic Test case that verifies that the compilation order computed by the compiler is as
   * expected.
   * 
   * @param inputModules An array of URIs to module source directories that need to be compiled
   * @param modulePath a semicolon-separated list of URIs that refer to absolute paths of locations
   *        where dependent modules can be located
   * @param expectedOrder An array of URIs to module source directories expected to be returned by
   *        the {@link ModuleUtils#prepareCompileOrder(String[])} method.
   * @param lineNo list of line numbers where an Exception is expected. If expecting 0 exceptions,
   *        pass a null
   * @param colNo list of column numbers where an Exception is expected. If expecting 0 exceptions,
   *        pass a null. If the caller is expecting a ParseException with a line number, but no
   *        column number, specify -1 as the column number.
   * @param listOfCycle list of all the cycle expected
   * @param expectedListofLinesOfImport list of Integer array that contains all line numbers which
   *        has the import statement leading to cyclic dependency exception
   * @throws Exception
   */
  private void genericTestCaseWithPrecompiledModules(String[] inputModules, String modulePath,
      String[] expectedOrder, int[] lineNos, int[] colNos, List<List<String>> expectedListOfCycle,
      List<Integer[]> expectedListofLinesOfImport) throws Exception {
    CompileAQLParams compileParam = new CompileAQLParams(inputModules,
        getCurOutputDir().toURI().toString(), modulePath, getTokenizerConfig());

    CompilationSummaryImpl summary = new CompilationSummaryImpl(compileParam);

    CompilerException ce = new CompilerException(summary);
    String[] actualOrder = ModuleUtils.prepareCompileOrder(inputModules, modulePath, ce);

    if (actualOrder == null) {
      Assert.fail("Deduced compile order is null");
    }

    // Verify that every element in actual order matches with the one in expected order
    for (int i = 0; i < actualOrder.length; i++) {
      if (false == expectedOrder[i].equals(actualOrder[i])) {
        Assert
            .fail(String
                .format("Incorrect compilation order. Expected order is : %s, Actual order is %s",
                    Arrays.asList(expectedOrder).toString(), Arrays.asList(actualOrder))
                .toString());
      }
    }

    String[] moduleNames = new String[inputModules.length];

    int id = 0;

    for (String module : inputModules) {
      if (module.endsWith("/"))
        moduleNames[id++] = module.substring(
            module.indexOf(getCurPrefix()) + getCurPrefix().length() + 1, module.lastIndexOf('/'));
      else
        moduleNames[id++] = module.substring(
            module.indexOf(getCurPrefix()) + getCurPrefix().length() + 1, module.length());
    }

    try {
      CompileAQL.compile(compileParam);
    }
    // Verify expected error
    catch (CompilerException e) {
      verifyCompilerException(e, lineNos, colNos, expectedListOfCycle, expectedListofLinesOfImport);
    }
  }

  /**
   * Generic Test case that verifies that the compilation order computed by the compiler is as
   * expected.
   * 
   * @param inputModules An array of URIs to module source directories that need to be compiled
   * @param expectedOrder An array of URIs to module source directories expected to be returned by
   *        the {@link ModuleUtils#prepareCompileOrder(String[])} method.
   * @param lineNo list of line numbers where an Exception is expected. If expecting 0 exceptions,
   *        pass a null
   * @param colNo list of column numbers where an Exception is expected. If expecting 0 exceptions,
   *        pass a null. If the caller is expecting a ParseException with a line number, but no
   *        column number, specify -1 as the column number.
   * @param listOfCycle list of all the cycle expected
   * @param expectedListofLinesOfImport list of Integer array that contains all line numbers which
   *        has the import statement leading to cyclic dependency exception
   * @throws Exception
   */
  private void genericTestCase(String[] inputModules, String[] expectedOrder, int[] lineNos,
      int[] colNos, List<List<String>> expectedListOfCycle,
      List<Integer[]> expectedListofLinesOfImport) throws Exception {
    genericTestCaseWithPrecompiledModules(inputModules, null, expectedOrder, lineNos, colNos,
        expectedListOfCycle, expectedListofLinesOfImport);
  }

  /**
   * This method verifies both the {@link ParseException} and {@link CircularDependencyException}
   * contained in the specified {@link CompilerException}. To verify {@link ParseException} this
   * methods falls back to {@link RuntimeTestHarness#checkException} method.
   * 
   * @param e compiler exception
   * @param lineNo list of line numbers where an Exception is expected. If expecting 0 exceptions,
   *        pass a null
   * @param colNo list of column numbers where an Exception is expected. If expecting 0 exceptions,
   *        pass a null. If the caller is expecting a ParseException with a line number, but no
   *        column number, specify -1 as the column number.
   * @param listOfCycle list of all the cycle expected
   * @param expectedListofLinesOfImport list of Integer array that contains all line numbers which
   *        has the import statement leading to cyclic dependency exception
   * @throws Exception
   */
  private void verifyCompilerException(CompilerException e, int[] lineNo, int[] colNo,
      List<List<String>> expectedListOfCycle, List<Integer[]> expectedListofLinesOfImport)
      throws Exception {
    List<Exception> allCompileErrors = e.getAllCompileErrors();

    List<ParseException> allPEs = new ArrayList<ParseException>();
    List<CircularDependencyException> allCDs = new ArrayList<CircularDependencyException>();

    // this summary is never checked so this is just an empty summary to
    // prevent an exception
    CompilationSummaryImpl summary = new CompilationSummaryImpl(null);

    for (Exception exception : allCompileErrors) {
      if (exception instanceof ParseException)
        allPEs.add((ParseException) exception);
      else if (exception instanceof CircularDependencyException)
        allCDs.add((CircularDependencyException) exception);
    }

    // Verify all the Parse exceptions
    if (null != lineNo && null != colNo) {
      CompilerException ce = new CompilerException(allPEs, summary);
      checkException(ce, lineNo, colNo);
    }

    // Verify all the Circular dependency exceptions
    if (null != expectedListOfCycle) {
      // First assert number of cycles
      Assert.assertEquals(expectedListOfCycle.size(), allCDs.size());

      // Compare cycle participants
      int itr = 0;
      for (CircularDependencyException circularDependencyException : allCDs) {
        Set<String> actualCycle = circularDependencyException.getErrorLocations().keySet();
        List<String> expectedCycle = expectedListOfCycle.get(itr);

        Assert.assertTrue(
            actualCycle.containsAll(expectedCycle) && expectedCycle.containsAll(actualCycle));

        Assert.assertTrue(expectedListofLinesOfImport != null
            && expectedListofLinesOfImport.size() == allCDs.size());

        // Compare the line number of the import statements leading to the circular dependency
        // exception
        Assert.assertTrue(verifyCircularDependencyLocations(circularDependencyException,
            expectedListofLinesOfImport.get(itr)));

        itr++;

      }
    }
  }

}
