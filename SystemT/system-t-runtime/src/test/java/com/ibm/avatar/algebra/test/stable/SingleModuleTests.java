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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;

/**
 * Various regression tests for compiling and running a single module using the new OperatorGraph
 * API.
 * 
 */
public class SingleModuleTests extends RuntimeTestHarness {

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
    SingleModuleTests t = new SingleModuleTests();
    t.setUp();

    long startMS = System.currentTimeMillis();

    t.selectStarUnionTest();

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
   * Test for defect : SingleModule compilation for union all with select * is broken. AOG
   * production for SDM is broken. Verify that the compiler throws errors at the specified line
   * numbers for the erroneous AQL.
   * 
   * @throws Exception
   */
  @Test
  public void selectStarUnionTest() throws Exception {
    startTest();

    // TODO: Presently, compiler is capable of detecting errors in only one of the inputs to
    // UnionAll statement,
    // wherever the error appears first. This is in sync with v1.4 behavior. However, in a future
    // release, we must make
    // compiler detect errors in all components of UnionAll statement
    int lineNo[] = {360, 377, 383, 407, 416, 427, 437, 447, 460, 463, 473, 593, 620, 628, 675, 711,
        779, 782, 786, 791, 797, 803, 808, 812, 824};
    int colNo[] =
        {2, 17, 30, 6, 6, 6, 6, 6, 6, 80, 15, 10, 64, 71, 14, 63, 6, 17, 62, 61, 6, 6, 6, 39, 6};

    compileModuleAndCheckErrors("module1", lineNo, colNo);

    endTest();
  }

  /**
   * Test to verify that the corrected AQL for defect is working fine w.r.t proper SDM node
   * creation.
   * 
   * @throws Exception
   */
  @Test
  public void selectStarUnionSDMTest() throws Exception {
    startTest();

    compileAndRunModule("module1", DOCS_FILE, null);

    // truncateExpectedFiles ();
    compareAgainstExpected(true);

    endTest();
  }

  /**
   * Compiles a module with lots of dictionaries and checks if compilation succeeds
   * 
   * @throws Exception
   */
  @Test
  public void compilationTest1() throws Exception {
    startTest();
    try {
      compileModule("compilationTest1");
    } catch (Exception e) {
      Assert.fail(
          String.format("Module is expected to compile without errors, but received Exception %s",
              e.getMessage()));
    }
    endTest();
  }

  /**
   * Compiles an AQL with duplicate module declarations (two of the same declaration) -- should
   * throw exceptions
   * 
   * @throws Exception
   */
  @Test
  public void duplicateModuleDeclarationTest() throws Exception {
    startTest();
    int lineNo[] = {7};
    int colNo[] = {1};

    compileModuleAndCheckErrors("module1", lineNo, colNo);
    endTest();
  }

  /**
   * Compiles an AQL with multiple module declarations (too many modules) -- should throw exceptions
   * 
   * @throws Exception
   */
  @Test
  public void multipleModuleDeclarationTest() throws Exception {
    startTest();
    int lineNo[] = {7};
    int colNo[] = {1};

    compileModuleAndCheckErrors("module1", lineNo, colNo);
    endTest();
  }

  @Test
  public void compileOrderTest() throws Exception {
    startTest();
    try {
      compileModule("module1");
    } catch (Exception e) {
      Assert.fail(String.format("No exceptions expected but received %s", e.getMessage()));
    }
    endTest();
  }
  /*
   * ADD NEW TEST CASES HERE
   */

  /*
   * UTILITY METHODS
   */

}
