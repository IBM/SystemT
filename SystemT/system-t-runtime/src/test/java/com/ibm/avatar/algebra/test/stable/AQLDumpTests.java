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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.algebra.util.tokenize.TokenizerConfig;
import com.ibm.avatar.api.CompileAQLParams;
import com.ibm.avatar.aql.compiler.ParseToCatalog;

/**
 * Tests that print out parse tree node dumps. Useful for IEWT and other tools that consume SystemT
 * 
 */
public class AQLDumpTests extends RuntimeTestHarness {

  /** Directory with AQL files for the test cases in this class. */
  public static final String AQL_FILES_DIR = TestConstants.AQL_DIR + "/AQLDumpTests";

  public static void main(String[] args) {
    try {
      AQLDumpTests t = new AQLDumpTests();

      t.setUp();

      long startMS = System.currentTimeMillis();

      t.dumpSelect();

      long endMS = System.currentTimeMillis();

      t.tearDown();

      double elapsedSec = (endMS - startMS) / 1000.0;

      System.err.printf("Test took %1.3f sec.\n", elapsedSec);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Before
  public void setUp() throws Exception {
    // Renice the current thread to avoid locking up the entire system.
    Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
  }

  @After
  public void tearDown() {
    endTest();

    System.gc();
  }

  /** The standard module name we use for tests in this suite */
  static final String[] DEFAULT_MODULE_NAME = {"aqldump"};

  /**
   * Test case for defect Case 1. Verify that extract regex statements pretty-print correctly.
   */
  @Test
  public void dumpExtractRegex() throws Exception {
    dumpAQLToFile("dumpExtractRegex", DEFAULT_MODULE_NAME);
  }

  /**
   * Test case for defect Case 2. Verify that extract dictionary statements pretty-print correctly.
   */
  @Test
  public void dumpExtractDictionary() throws Exception {
    dumpAQLToFile("dumpExtractDictionary", DEFAULT_MODULE_NAME);
  }

  /**
   * Test case for defect : Lemma match doesn't work for anything but inline dictionaries.
   */
  @Test
  public void dumpCreateDictionary() throws Exception {
    dumpAQLToFile("dumpCreateDictionary", DEFAULT_MODULE_NAME);
  }

  /**
   * Test case for defect Case 3. Verify that extract pattern statements pretty-print correctly.
   */
  @Test
  public void dumpExtractPattern() throws Exception {
    dumpAQLToFile("dumpExtractPattern", DEFAULT_MODULE_NAME);
  }

  /**
   * Test case for defect Case 4. Verify that select statements pretty-print correctly.
   */
  @Test
  public void dumpSelect() throws Exception {
    dumpAQLToFile("dumpSelect", DEFAULT_MODULE_NAME);
  }

  /**
   * Test case for defect Case 5. Verify that select subquery statements pretty-print correctly.
   */
  @Test
  public void dumpSelectSubquery() throws Exception {
    dumpAQLToFile("dumpSelectSubquery", DEFAULT_MODULE_NAME);
  }

  /**
   * Test case for defect Case 6. Verify that union and minus statements pretty-print correctly.
   */
  @Test
  public void dumpUnionAndMinus() throws Exception {
    dumpAQLToFile("dumpUnionAndMinus", DEFAULT_MODULE_NAME);
  }

  /**
   * Test case for defect Case 7. Verify that mapping tables pretty-print correctly.
   */
  @Test
  public void dumpCreateTable() throws Exception {
    dumpAQLToFile("dumpCreateTable", DEFAULT_MODULE_NAME);
  }

  /**
   * Test case for defect . Verify that other extraction specs pretty-print correctly.
   */
  @Test
  public void dumpExtractOther() throws Exception {
    dumpAQLToFile("dumpExtractOther", DEFAULT_MODULE_NAME);
  }

  /**
   * Test case for defect . Verify that detag statement pretty-prints correctly.
   */
  @Test
  public void dumpDetag() throws Exception {
    dumpAQLToFile("dumpDetag", DEFAULT_MODULE_NAME);
  }

  /**
   * Test case for defect . Verify that null spans pretty-prints it correctly as null instead of
   * NullConst()
   */
  @Test
  public void dumpNullSpan() throws Exception {
    dumpAQLToFile("dumpNullSpan", DEFAULT_MODULE_NAME);
  }

  /**
   * Test case for defect Case 1. Verify that multiple union statements pretty-print correctly.
   */
  @Test
  public void dumpMultipleUnions() throws Exception {
    dumpAQLToFile("dumpMultipleUnions", DEFAULT_MODULE_NAME);
  }

  /**
   * Test case for defect Case 2. Verify that select * statements pretty-print correctly.
   */
  @Test
  public void dumpSelectStar() throws Exception {
    dumpAQLToFile("dumpSelectStar", DEFAULT_MODULE_NAME);
  }

  /**
   * Test case for defect Case 3. Verify that create dictionary from table statements are
   * pretty-print correctly.
   */
  @Test
  public void dumpCreateDictFromTable() throws Exception {
    dumpAQLToFile("dumpCreateDictFromTable", DEFAULT_MODULE_NAME);
  }

  /*
   * PRIVATE METHODS
   */

  /**
   * Rewrite the AQL contained within a file and output to a file. Useful for testing node.dump()
   * functions.
   */
  private void dumpAQLToFile(String testName, String[] moduleNames) throws Exception {
    startTest(testName);

    File rewrittenModuleDir = getCurOutputDir();

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
        compiledModuleURI, compiledModuleURI, new TokenizerConfig.Standard());

    ParseToCatalog catalogGenerator = new ParseToCatalog();
    catalogGenerator.parse(params);

    // Dump the parse tree back to a buffer.
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    catalogGenerator.getCatalog().dump(new PrintStream(buf, true, "UTF-8"));

    File dumpAQL = new File(rewrittenModuleDir, testName + ".aql");

    // Dump the buffer to a file for comparison.
    FileOutputStream aqlOut = new FileOutputStream(dumpAQL);
    buf.writeTo(aqlOut);
    aqlOut.close();

    // Log.info ("Generated AQL is:\n%s", buf.toString ());

    // Compare against the expected result.
    compareAgainstExpected(false);

    endTest();
  }
}
