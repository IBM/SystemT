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
package com.ibm.systemt.regex.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import junitx.framework.FileAssert;

/**
 * Superclass containing various helper methods for JUnit tests; supersedes the old "TestUtils"
 * class.
 */
public class TestHarness {

  /*
   * CONSTANTS
   */

  /**
   * Directory where outputs of test cases go, relative to the current base dir.
   */
  public static final String OUTPUT_DIR_REL = "regression/actual";

  /**
   * Directory where gold-standard output files for tests can be found, relative to the current base
   * dir.
   */
  public static final String EXPECTED_DIR_REL = "regression/expected";

  /**
   * Number of kilochars that we truncate files to if requested.
   */
  public static final int TRUNCATE_LEN_KCHAR = 100;

  /** File encoding to use during comparison. */
  public static final String FILE_ENCODING = "UTF-8";

  /*
   * FIELDS
   */

  /**
   * String that identifies the current test harness instance
   */
  private String curHarnessName = null;

  /**
   * Directory where the outputs of this test harness will go.
   */
  private File harnessOutputDir = null;

  /**
   * Directory containing gold-standard results (if any) for this test harness.
   */
  private File harnessExpectedDir = null;

  /**
   * Prefix string that identifies the current test case
   */
  private String curPrefix = null;

  /**
   * Output directory for the current test case
   */
  private File curOutputDir = null;

  /**
   * "Expeced" (gold standard) directory for the current test case.
   */
  private File curExpectedDir = null;

  /*
   * CONSTRUCTOR
   */
  protected TestHarness() {
    // Figure out the name of the current class.
    curHarnessName = this.getClass().getSimpleName();

    // Set up the output directory for the tests in this class.

    File rootOutputDir = new File(getBaseDir(), OUTPUT_DIR_REL);
    harnessOutputDir = new File(rootOutputDir, curHarnessName);
    if (false == harnessOutputDir.exists()) {
      harnessOutputDir.mkdirs();
    }

    // Set up the directory for "gold standard" results
    // This directory may or may not exist.
    File rootExpectedDir = new File(getBaseDir(), EXPECTED_DIR_REL);
    harnessExpectedDir = new File(rootExpectedDir, curHarnessName);
  }

  /*
   * UTILITY METHODS
   */

  /**
   * Call this method when starting a test, to initialize internal state. The name of the calling
   * method is used as a prefix.
   */
  protected void startTest() {

    // Determine the name of the calling method.
    StackTraceElement[] stack = (new Throwable()).getStackTrace();
    String callingMethodName = stack[1].getMethodName();

    curPrefix = callingMethodName;
    curOutputDir = new File(harnessOutputDir, curPrefix);
    curExpectedDir = new File(harnessExpectedDir, curPrefix);

    // Clean out the output directory, creating it if necessary.
    cleanOutputDir();
  }

  /**
   * Call this method at the conclusion of a test to release any resources.
   */
  protected void endTest() {
    curPrefix = null;
    curOutputDir = null;
  }

  /**
   * @return prefix string that identifies the current test case within the current test harness
   */
  public String getCurPrefix() {
    return curPrefix;
  }

  /**
   * @return output directory for the currently active test case
   */
  public File getCurOutputDir() {
    return curOutputDir;
  }

  /**
   * @return "gold standard" output directory for the currently active test case
   */
  public File getCurExpectedDir() {
    return curExpectedDir;
  }

  /**
   * Compare output files for the current test against the corresponding "gold standard" files
   * stored on disk.
   * 
   * @param truncate true to truncate the files being compared to {@link #TRUNCATE_LEN_KCHAR} kb
   */
  public void compareAgainstExpected(boolean truncate) throws Exception {

    File[] expectedFiles = curExpectedDir.listFiles();
    if (null == expectedFiles) {
      throw new Exception("Expected dir " + curExpectedDir + " does not exist");
    }

    for (File file : expectedFiles) {
      // Skip directories (including the CVS directory)
      if (file.isFile()) {
        compareAgainstExpected(file.getName(), truncate);
      }
    }
  }

  /**
   * Compare an output file from a regression test against the expected output stored in CVS.
   * 
   * @param filename name of the output file, not containing the directory it's in; file is assumed
   *        to be in {@link #curOutputDir}.
   * @param truncate true to truncate the files being compared
   * @throws IOException
   * @throws Exception
   */
  public void compareAgainstExpected(String filename, boolean truncate)
      throws IOException, Exception {

    File outputFile = new File(curOutputDir, filename);
    File expectedFile = new File(curExpectedDir, filename);

    if (truncate) {
      truncateFile(outputFile);
    }

    System.err.printf("Comparing output file %s against expected output...\n", filename);

    // Use JUnit extensions to do file comparison.
    FileAssert.assertEquals(expectedFile, outputFile);
  }

  /**
   * Subclasses may override this method as needed to relocate the regression test output directory
   * tree.
   * 
   * @return base directory for regression test outputs
   */
  protected File getBaseDir() {
    return new File(".");
  }

  /*
   * INTERNAL METHODS
   */

  /**
   * Clean out the output directory for the current test, creating it if necessary.
   * 
   * @param prefix prefix that identifies the current test case
   */
  private void cleanOutputDir() {

    if (curOutputDir.exists()) {
      // System.err.printf("Deleting output dir '%s'\n", curOutputDir);
      deleteDirectory(curOutputDir);
    }

    curOutputDir.mkdirs();
  }

  /** Recursively delete a directory and its contents. */
  private boolean deleteDirectory(File directory) {
    if (directory.isDirectory()) {
      String[] children = directory.list();
      for (int i = 0; i < children.length; i++) {
        boolean success = deleteDirectory(new File(directory, children[i]));
        if (!success) {
          return false;
        }
      }
    }

    // The directory is now empty so delete it
    return directory.delete();
  }

  /** Truncate a file to {@link #TRUNCATE_LEN_KCHAR} characters. */
  private void truncateFile(File origFile) throws IOException {

    // Read the appropriate number of characters into a buffer.
    char[] buf = new char[TRUNCATE_LEN_KCHAR * 1024];

    InputStreamReader in = new InputStreamReader(new FileInputStream(origFile), FILE_ENCODING);
    int nread = in.read(buf);
    in.close();

    if (nread < buf.length) {
      // Didn't even fill up the buffer; no truncation needed.
      return;
    }

    // Write out the truncated version of the file.
    OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(origFile), FILE_ENCODING);
    out.write(buf);
    out.close();
  }
}
