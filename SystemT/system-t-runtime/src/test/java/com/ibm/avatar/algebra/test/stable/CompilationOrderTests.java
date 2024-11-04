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

import org.junit.Test;

import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;

/**
 * Tests to verify compilation order. <br/>
 * TODO: Certain tests from TAMTests can be moved to this harness
 * 
 */
public class CompilationOrderTests extends RuntimeTestHarness {
  /**
   * Verifies that compiler is error recoverable when certain modules passed to it do not compile
   * fine. PersonName_BasicFeatures: No errors. Should compile fine and generate a tam <br/>
   * PersonName_CandidateGeneration: Depends on PersonName_BasicFeatures, but has 4 compilation
   * errors. TAM should not be generated <br/>
   * PersonName_FilterConsolidate: Depends on PersonName_CandidateGeneration, but has 1 compilation
   * error. TAM should not be generated <br/>
   * PersonName_Finals: Depends on PersonName_FilterConsolidate, but dependent TAM has errors.
   * Expect 2 errors. TAM should not be generated <br/>
   * 
   * @throws Exception
   */
  @Test
  public void compilerErrorRecoveryTest1() throws Exception {
    startTest();

    String[] modules = {"PersonName_CandidateGeneration", "PersonName_Finals",
        "PersonName_FilterConsolidate", "PersonName_BasicFeatures"};

    // Errors are ordered by file name, line number and then column number.
    // See CompilerException.getSortedCompileErrors() for details
    int lineNos[] = {11, 11, 21, 24, 5, 4, 6};
    int colNos[] = {6, 20, 40, 39, 71, 15, 42};

    compileMultipleModulesAndCheckErrors(modules, lineNos, colNos);

    compareAgainstExpected(false);

    endTest();
  }
}
