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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.util.file.SearchPath;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.exceptions.CompilerException;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.tam.TAM;
import com.ibm.avatar.aql.tam.TAMSerializer;
import com.ibm.avatar.logging.Log;
import com.ibm.avatar.logging.MsgType;

/**
 * Test cases for feature request [132771] Implement wildcards in 'include' statement and optional
 * includes. This file should also be used for test cases of wildcard expansion for other uses of
 * paths (dictionaries, for example), as they are implemented.
 * 
 */
public class WildcardTests extends RuntimeTestHarness {

  private static final String AQL_FILES_DIR = TestConstants.AQL_DIR + "/wildcardTests";

  private static final String DOCS_DIR = TestConstants.TEST_DOCS_DIR + "/wildcardTests";

  // private static final String docFileName = "includeWildcardTest";

  public static void main(String[] args) {
    try {

      WildcardTests t = new WildcardTests();

      t.setUp();

      long startMS = System.currentTimeMillis();

      t.noDirPartTest();

      long endMS = System.currentTimeMillis();

      t.tearDown();

      double elapsedSec = ((double) (endMS - startMS)) / 1000.0;

      System.err.printf("Test took %1.3f sec.\n", elapsedSec);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private String includePath;

  /**
   * Scan over the Enron database; set up by setUp() and cleaned out by tearDown()
   */
  // private DocScanInternal scan = null;
  // private File defaultDocsFile = null;

  @Before
  public void setUp() throws Exception {

    // For now, don't put any character set information into the header of
    // our output HTML.
    setWriteCharsetInfo(false);
  }

  @After
  public void tearDown() {
    // scan = null;
  }

  /**
   * Negative testcase Test scenario : When include contains '*' wildcard but containing directory
   * has no files to include and flag 'allow empty_fileset' is not specified.
   */
  @Test
  public void noFlagTest() throws Exception {
    try {
      genericTestCase("noFlagTest");
    } catch (CompilerException e) {
      Exception containedException = e.getAllCompileErrors().get(0);
      assertTrue((containedException instanceof ParseException));
      Log.info("Caught exception as expected:");
      e.printStackTrace();
    }
  }

  /**
   * Test scenario : When include contains '*' wildcard but containing directory has no files to
   * include and flag 'allow empty_fileset' is specified. Reproduces defect : Backward compatibility
   * API does not support the ALLOW EMPTY_FILESET optional clause of an include statement.
   */
  @Test
  public void emptyFilesetFlagTest() throws Exception {
    genericTestCase("emptyFilesetFlagTest");

    // Test should produce exactly 2 output files
    assertEquals(2, getCurOutputDir().listFiles().length);
  }

  /**
   * Test scenario : When include contains '*' wildcard and containing directory has at least 1 file
   * to include . Assuming file1.aql and file2.aql are present in the 'IncludeFiles' directory.
   */
  @Test
  public void includeTwoFilesTest() throws Exception {
    genericTestCase("includeTwoFilesTest");

    // Test should produce exactly 4 output files
    assertEquals(4, getCurOutputDir().listFiles().length);
  }

  /**
   * Negative testcase Test scenario : When include contains a qualified file and flag 'allow
   * empty_fileset' is specified
   */
  @Test
  public void qualifiedIncludeWithFlagTest() throws Exception {
    genericTestCase("qualifiedIncludeWithFlagTest");
  }

  /**
   * Test scenario : When include contains a qualified file and flag 'allow empty_fileset' is not
   * specified
   */
  @Test
  public void qualifiedIncludeNoFlagTest() throws Exception {

    genericTestCase("qualifiedIncludeNoFlagTest");

    // Test should produce exactly 3 output files
    assertEquals(3, getCurOutputDir().listFiles().length);
  }

  /**
   * Test of handling directory paths with multiple elements.
   */
  @Test
  public void longDirPathTest() throws Exception {

    genericTestCase("longDirPathTest");

    // Test should produce exactly 4 output files
    assertEquals(4, getCurOutputDir().listFiles().length);
  }

  /**
   * Test to ensure that wildcards work when the include path includes more than one directory.
   */
  @Test
  public void longIncludePathTest() throws Exception {

    includePath = AQL_FILES_DIR + "/IncludeFiles/subdir1/subdir1.1" //
        + SearchPath.PATH_SEP_CHAR //
        + AQL_FILES_DIR + "/IncludeFiles/subdir1/subdir1.2";

    genericTestCase("longIncludePathTest");

    // Test should produce exactly 4 output files
    assertEquals(4, getCurOutputDir().listFiles().length);
  }

  /**
   * Test to ensure that ambiguous wildcard references (e.g. wildcard matches at two places in the
   * path) result in an exception
   */
  @Test
  public void ambigWildcardTest() throws Exception {

    includePath = AQL_FILES_DIR + "/IncludeFiles/subdir1/subdir1.1" //
        + SearchPath.PATH_SEP_CHAR //
        + AQL_FILES_DIR + "/IncludeFiles/subdir1/subdir1.2";

    try {
      genericTestCase("ambigWildcardTest");
    } catch (CompilerException e) {
      Exception containedException = e.getAllCompileErrors().get(0);

      if (false == (containedException instanceof ParseException)) {
        Log.info("Unexpected exception:");
        e.printStackTrace();
      }
      assertTrue((containedException instanceof ParseException));
      Log.info("Caught exception as expected: %s", e);

    }
  }

  /**
   * Test to ensure that wildcard expansion works if there is no directory part in the include
   * statement's wildcard expression.
   */
  @Test
  public void noDirPartTest() throws Exception {

    // The test case's AQL file includes *.aql, so we set the include path
    // so that the search starts at the directory containing target files to include.
    includePath = AQL_FILES_DIR + "/IncludeFiles";

    genericTestCase("noDirPartTest");

    // Test should produce exactly 4 output files
    assertEquals(4, getCurOutputDir().listFiles().length);
  }

  /*
   * UTILITY METHODS
   */

  /**
   * A generic test case. Takes a prefix string as argument; assumes that an AQL file called
   * prefix.aql and a docs file prefix.del will replicate the problem, and sends output to
   * testdata/regression/output/prefix.
   */
  private void genericTestCase(String prefix) throws Exception {
    String docsFileName = String.format("%s/%s.del", DOCS_DIR, prefix);
    genericTestCase(prefix, docsFileName);

  }

  private void genericTestCase(String prefix, String docsFileName) throws Exception {

    super.startTest(prefix);

    String aqlFileName = String.format("%s/%s.aql", AQL_FILES_DIR, prefix);


    // Override our default document scan with one that just uses the
    // problem document.
    // scan = OldDocScan.makeFileScan(new File(docsFileName));

    // Parse and compile the AQL with the low-level internal API so as to
    // separate out the effects of different path strings.
    String dataPathStr =
        String.format("%s%c%s", AQL_FILES_DIR, SearchPath.PATH_SEP_CHAR, includePath);
    compileAQL(new File(aqlFileName), dataPathStr);

    // Load tam
    TAM tam = TAMSerializer.load(Constants.GENERIC_MODULE_NAME,
        this.getCurOutputDir().toURI().toString());
    String aog = tam.getAog();
    // ArrayList<CompiledDictionary> allCompiledDicts = tams[0].getAllDicts ();

    System.err.print("-----\nAOG plan is:\n" + aog + "\n---\n");

    // Try running the plan.
    setOutputDir(getCurOutputDir().getPath());
    setExpectedDir(getCurExpectedDir().getPath());
    setPrintTups(true);
    runAOGString(new File(docsFileName), aog, null);

    // If there are "expected" results available, check them against the
    // ones produced.
    File expectedDir = getCurExpectedDir();
    if (expectedDir.exists()) {
      Log.log(MsgType.Info, "Comparing against expected files in %s...", expectedDir);
      compareAgainstExpected(true);
    } else {
      Log.log(MsgType.Info, "Skipping file comparison because dir %s does not exist.", expectedDir);
    }

  }

}
