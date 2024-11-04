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

import org.junit.Test;

import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.api.CompilationSummary;

/**
 * Test harness to verify that various forms of erroneous import statements are properly flagged by
 * the compiler.
 * 
 */
public class ImportCompileErrorTests extends RuntimeTestHarness {
  /**
   * Test to verify that the AQL compiler ignores redundant 'import module' statements. <br/>
   * Example: <br/>
   * import module X; <br/>
   * import module X;
   * 
   * @throws Exception
   */
  @Test
  public void dupImportModuleTest() throws Exception {
    startTest();

    String[] modules = {"module1", "module2"};

    genericTestZeroCompilerErrorAndWarns(modules);

    endTest();
  }

  /**
   * Test to verify that an AQL element imported twice, using 'import element_type X from module Y',
   * is NOT flagged as a compiler error. The compiler is expected to ignore redundant import
   * statement.
   * 
   * @throws Exception
   */
  @Test
  public void dupImportElemTest1() throws Exception {
    startTest();
    String[] modules = {"module1", "module2"};
    genericTestZeroCompilerErrorAndWarns(modules);
    endTest();
  }

  /**
   * Test to verify that an AQL element imported twice -- first through import module statement and
   * later through import element_type statement -- is ignored by the compiler and NOT flagged as an
   * error or warning
   * 
   * @throws Exception
   */
  @Test
  public void dupImportElemTest2() throws Exception {
    startTest();
    String[] modules = {"module1", "module2"};
    genericTestZeroCompilerErrorAndWarns(modules);
    endTest();
  }

  /**
   * Test to verify that an AQL element imported twice -- first through import element_type
   * statement and then through import module statement -- is ignored by the compiler and NOT
   * flagged as an error or warning
   * 
   * @throws Exception
   */
  @Test
  public void dupImportElemTest3() throws Exception {
    startTest();
    String[] modules = {"module1", "module2"};
    genericTestZeroCompilerErrorAndWarns(modules);
    endTest();
  }

  /**
   * Test to verify that conflicting import aliases are flagged as compiler errors. <br/>
   * Example: <br/>
   * import view View1 from module module1 as MyAlias; <br/>
   * import view View2 from module module1 as MyAlias; <br/>
   * Where as, two different AQL element types can be imported into the same alias name, as they
   * belong to different name space. <br/>
   * For instance, a view and a dictionary can be imported into the same alias name.
   * 
   * @throws Exception
   */
  @Test
  public void dupImportElemTest4() throws Exception {
    startTest();

    String[] modules = {"module1", "module2"};
    int lineNos[] = {11, 18, 25, 32, 39};
    int colNos[] = {1, 1, 1, 1, 1};

    compileMultipleModulesAndCheckErrors(modules, lineNos, colNos);

    endTest();
  }

  /**
   * Verifies that proper error messages are thrown when an imported alias conflicts with the name
   * of an element definition in the current module. In this test, the file names of AQLs in module2
   * (consuming module) are chosen such that the AQL file with definitions is compiled first,
   * followed by the AQL file with import statements. Since AQL compiler uses sorted order of file
   * names, definitions are placed in 1.aql and import statements are placed in 2.aql.
   * 
   * @throws Exception
   */
  @Test
  public void importConflictsWithDef_DefinitionCompiledFirst() throws Exception {
    startTest();
    int[] lineNos = {3, 5, 7, 9, 11};
    int[] colNos = {1, 1, 1, 1, 1};
    String[] modulesToCompile = new String[] {"module1", "module2"};
    compileMultipleModulesAndCheckErrors(modulesToCompile, lineNos, colNos);
    endTest();
  }

  /**
   * Verifies that proper error messages are thrown when an imported alias conflicts with the name
   * of an element definition in the current module. In this test, the file names of AQLs in module2
   * (consuming module) are chosen such that the AQL file with import statements is compiled first,
   * followed by the AQL file with element definitions. Since AQL compiler uses sorted order of file
   * names, import statements are placed in 1.aql and element definitions are placed in 2.aql.
   * 
   * @throws Exception
   */
  @Test
  public void importConflictsWithDef_ImportCompiledFirst() throws Exception {
    startTest();
    int[] lineNos = {4, 10, 15, 23, 29};
    int[] colNos = {13, 1, 14, 22, 1};
    String[] modulesToCompile = new String[] {"module1", "module2"};
    compileMultipleModulesAndCheckErrors(modulesToCompile, lineNos, colNos);
    endTest();
  }

  /* ADD NEW TESTS ABOVE */

  /* ADD UTILITIES BELOW */

  /**
   * Generic test utility method that compiles the specified set of modules and asserts that there
   * are no compilation errors or warnings.
   * 
   * @param modules List of modules to compile
   * @throws Exception
   */
  private void genericTestZeroCompilerErrorAndWarns(String[] modules) throws Exception {
    CompilationSummary summary = compileModules(modules, null);

    // Expect NO compile errors

    // Expect Zero warnings
    int warningCount = summary.getCompilerWarning().size();
    assertTrue(
        String.format("Expected zero compilation warnings, but found %s warnings", warningCount),
        warningCount == 0);

  }
}
