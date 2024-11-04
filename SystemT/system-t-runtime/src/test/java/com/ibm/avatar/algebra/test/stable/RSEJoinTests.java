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
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.tam.TAM;
import com.ibm.avatar.aql.tam.TAMSerializer;

/**
 * Various tests of the restricted span evaluation (RSE) join operator.
 * 
 */
public class RSEJoinTests extends RuntimeTestHarness {

  public static void main(String[] args) {
    try {

      RSEJoinTests t = new RSEJoinTests();

      t.setUp();

      long startMS = System.currentTimeMillis();

      t.useRSETest();

      long endMS = System.currentTimeMillis();

      double elapsedSec = ((double) (endMS - startMS)) / 1000.0;

      System.err.printf("Test took %1.3f sec.\n", elapsedSec);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Scan over the Enron database; set up by setUp() and cleaned out by tearDown()
   */
  // private DocScan scan = null;
  private File defaultDocsFile = null;

  @Before
  public void setUp() throws Exception {

    // Renice the current thread to avoid locking up the entire system.
    Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

    defaultDocsFile = new File(TestConstants.ENRON_1K_DUMP);
  }

  @After
  public void tearDown() {

  }

  /**
   * Test of the Follows join predicate with RSE join. Looks for pairs of potential first and last
   * names within 10-50 chars of each other.
   */
  @Test
  public void followsTest() throws Exception {
    startTest();
    String aogFileName = "testdata/aog/RSEJoinTests/followsTest.aog";

    String[][] dictInfo = {{"dictionaries/first.dict", "testdata/dictionaries/first.dict"},
        {"dictionaries/last.dict", "testdata/dictionaries/last.dict"}};

    setWriteCharsetInfo(true);
    runAOGFile(defaultDocsFile, new File(aogFileName), dictInfo);

    System.err.printf("Comparing output files.\n");

    // truncateExpectedFiles (true);
    truncateOutputFiles(true);
    compareAgainstExpected(false);
  }

  /**
   * Test case for defect : ScalarFuncNode.hasRseFuncImpl compares a class to a string -- RSEJoin
   * never used Compiles a simple module that should use RSE join and verifies that the optimizer
   * chose RSE join.
   */
  @Test
  public void useRSETest() throws Exception {
    startTest();

    final String MODULE_NAME = "rse";
    final String MODULE_PATH = getCurOutputDir().toURI().toString();

    compileModule(MODULE_NAME);

    // Dump the plan of the module (compileAndLoadModule() puts the tam file into the current test
    // case's output
    // directory)
    TAM tam = TAMSerializer.load(MODULE_NAME, MODULE_PATH);
    String aog = tam.getAog();

    System.err.printf("Compiled plan for module is:\n%s\n", aog);

    if (false == aog.contains("RSEJoin(")) {
      throw new TextAnalyticsException("Plan does not use RSEJoin as expected");
    }

    // Make sure the module will load
    OperatorGraph.createOG(new String[] {MODULE_NAME}, MODULE_PATH, null, getTokenizerConfig());

    endTest();
  }

}
