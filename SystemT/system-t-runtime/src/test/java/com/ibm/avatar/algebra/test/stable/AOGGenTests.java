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
import com.ibm.avatar.api.CompileAQL;
import com.ibm.avatar.api.CompileAQLParams;

/**
 * This test suite verifies the correctness of AOG generated from AQL compilation
 * 
 */
public class AOGGenTests extends RuntimeTestHarness {

  /**
   * Main method for invoking one test at a time.
   * 
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    AOGGenTests t = new AOGGenTests();
    t.setUp();

    long startMS = System.currentTimeMillis();

    t.consistentTAMGenerationTest();

    long endMS = System.currentTimeMillis();

    t.tearDown();

    double elapsedSec = (endMS - startMS) / 1000.0;
    System.err.printf("Test took %1.3f sec.\n", elapsedSec);
  }

  /**
   * Directory where AQL files referenced in this class's regression tests are located.
   */
  public static final File AQL_FILES_DIR = new File(TestConstants.AQL_DIR, "aqlGenTests");

  /**
   * Main AQL file for the annotators used in the evaluation
   */
  public static final File AQL_FILE = new File(AQL_FILES_DIR, "bandtest.aql");

  /**
   * Dictionary path for running the annotators.
   */
  public static final String DICT_PATH_STR =
      TestConstants.TESTDATA_DIR + "/dictionaries/aqlGenTests";

  /**
   * Include path for running the annotators.
   */
  public static final String INCLUDE_PATH_STR = AQL_FILES_DIR.getPath();

  /**
   * File encoding used by all tests in this class.
   */
  public static final String FILE_ENCODING = "UTF-8";

  @Before
  public void setUp() throws Exception {

  }

  @After
  public void tearDown() {

  }

  /**
   * Verifies that AQL compiler generates consistent AOGs every time an AQL is compiled. Test case
   * for defect : The AQL compiler may generate different AOGs when invoked on the same input AQL.
   * <br/>
   * 
   * @throws Exception
   */
  @Test
  public void consistentTAMGenerationTest() throws Exception {
    startTest();

    setDataPath(DICT_PATH_STR, INCLUDE_PATH_STR, null);

    setOutputDir(getCurOutputDir().getPath());
    setExpectedDir(getCurExpectedDir().getPath());

    // Compile and dump the AOG
    File tamDir = getCurOutputDir();
    CompileAQLParams params =
        new CompileAQLParams(AQL_FILE, tamDir.toURI().toString(), getDataPath());
    params.setTokenizerConfig(getTokenizerConfig());

    // repeat the test 10 times
    for (int i = 1; i <= 10; ++i) {
      CompileAQL.compile(params);

      // Make sure the AOG plan is the same as the expected value - don't truncate! (the first 1000
      // lines are likely to
      // be dictionary entries)

      // Do not truncate TAM files
      compareAgainstExpected(false);

      // reset inputModules and set input file back to AQL_FILE
      params.setInputModules(null);
      params.setInputFile(AQL_FILE);
    }
    endTest();
  }
}
