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
package com.ibm.wcs.annotationservice.test.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

import org.junit.Assert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ibm.avatar.algebra.util.test.TestUtils;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.logging.Log;
import com.ibm.wcs.annotationservice.AnnotationServiceConstants;

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

  /**
   * Special flag that is set to TRUE if the current test case is running inside a dedicated
   * subprocess.
   */
  protected boolean fenced = false;

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
  protected File harnessOutputDir = null;

  /**
   * Directory containing gold-standard results (if any) for this test harness.
   */
  protected File harnessExpectedDir = null;

  /**
   * Prefix string that identifies the current test case
   */
  protected String curPrefix = null;

  /**
   * Output directory for the current test case
   */
  protected File curOutputDir = null;

  /**
   * "Expected" (gold standard) directory for the current test case.
   */
  protected File curExpectedDir = null;

  /**
   * Used by Jackson for JSON binding.
   */
  private static ObjectMapper mapper = new ObjectMapper();

  /*
   * CONSTRUCTOR
   */
  protected TestHarness() {
    // Figure out the name of the current class.
    curHarnessName = this.getClass().getSimpleName();

    // initialize
    init();
  }

  /**
   * Perform initializations here rather than in constructor so that subclasses can override, if
   * need be
   */
  protected void init() {
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

    startTest(callingMethodName);
  }

  /**
   * Call this method when starting a test, to initialize internal state.
   * 
   * @param prefix name of the test, used as a prefix for any input/output directories of the test.
   */
  protected void startTest(String prefix) {

    curPrefix = prefix;
    curOutputDir = new File(harnessOutputDir, curPrefix);
    curExpectedDir = new File(harnessExpectedDir, curPrefix);

    // Clean out the output directory, creating it if necessary.
    cleanOutputDir();

    // Trigger any upcalls that the child class has put into place.
    startHook();
  }

  /**
   * Call this method when starting a test to run the test in a subprocess. Starts up a subprocess
   * that will execute the same test case. The return value of this function tells whether the test
   * case is running in the child or whether the test case has completed and has returned control to
   * the parent. Common usage of this method is to put the following: <code>
   * if (forkTest()) return;
   * </code> instead of <code>
   * startTest()
   * </code> at the beginning of the test case. NOTE: This method currently does not attempt to call
   * setUp() or tearDown() methods, since those methods are global to the test harness. Any
   * harness-wide initialization will need to be either inside the constructor or repeated in the
   * test case.
   * 
   * @param jvmArgs additional arguments to pass to the subprocess's Java interpreter
   * @return true from the parent process, false from the child
   */
  protected boolean forkTest(String... jvmArgs) throws Exception {

    // Determine the name of the calling method.
    StackTraceElement[] stack = (new Throwable()).getStackTrace();
    String callingMethodName = stack[1].getMethodName();

    Log.info("Starting test case %s.%s() in subprocess", getClass().getName(), callingMethodName);

    // Check whether this function is running in a subprocess already.
    if (fenced) {
      // Running in a subprocess; set up the test case, then return and
      // continue.
      startTest(callingMethodName);
      return false;
    }

    // If we get here, we're in the parent process. Start the child.
    String JAVA = TestHarness.getJavaExecPath();
    String CLASSPATH = System.getProperty("java.class.path");

    ArrayList<String> cmdList = new ArrayList<String>();
    cmdList.add(JAVA);

    for (String arg : jvmArgs) {
      cmdList.add(arg);
    }

    cmdList.add("-cp");
    cmdList.add(CLASSPATH);
    cmdList.add(SubprocessCallback.class.getName());
    cmdList.add(this.getClass().getName());
    cmdList.add(callingMethodName);

    Log.info("Command line is %s\n", cmdList);

    ProcessBuilder pb = new ProcessBuilder(cmdList);
    pb.redirectErrorStream(true);
    Process p = pb.start();

    // Java does not send the process's output anywhere useful. Capture it
    // and print to STDERR.
    BufferedReader childStdOut = new BufferedReader(new InputStreamReader(p.getInputStream()));

    String line;
    while (null != (line = childStdOut.readLine())) {
      // Don't go through the log, because the subprocess has its own
      // logger and we'll get two timestamps per line.
      System.err.printf("SUBPROCESS: %s\n", line);
    }

    // Process has stopped producing output; wait for its return value.
    int returnValue = p.waitFor();

    Log.info("Child process returned %d\n", returnValue);

    if (returnValue != 0) {
      throw new TextAnalyticsException("Child process for test case caught an exception.");
    }

    return true;

  }

  public static final class SubprocessCallback {
    /**
     * This main method is for the exclusive use of {@link #startTestInSubproc()}. Arguments: Name
     * of test harness class and name of method within the class to invoke.
     */
    public static void main(String[] args) {

      if (args.length != 2) {
        System.err.printf("Called %s.main() with no arguments.\n" + "This usually means that you "
            + "pressed the wrong button in Eclipse.\n", SubprocessCallback.class.getName());
        System.exit(-1);
      }

      String className = args[0];
      String methodName = args[1];

      // Create a copy of the specified class and invoke the test harness
      // method by reflection.
      System.err.printf("Child process calling %s.%s() by reflection.\n", className, methodName);
      try {
        TestHarness t = (TestHarness) (Class.forName(className).newInstance());

        // Tell this copy that it's the child process.
        t.fenced = true;

        Method m = t.getClass().getMethod(methodName);
        m.invoke(t);
      } catch (Throwable e) {
        System.err.printf("Child process caught exception.\n");
        e.printStackTrace();
        System.exit(1);
      }

    }
  }

  /**
   * This method is called from {@link #startTest()} after all other per-test initialization is
   * complete. Subclasses can override this method to provide some initialization upcalls.
   */
  protected void startHook() {
    // Default implementation does nothing.
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
    if (null == curOutputDir) {
      throw new RuntimeException("getCurOutputDir() called before startTest()");
    }
    return curOutputDir;
  }

  /**
   * @return "gold standard" output directory for the currently active test case
   */
  public File getCurExpectedDir() {
    return curExpectedDir;
  }

  public void setCurOutputDir(File outputDir) {
    this.curOutputDir = outputDir;
  }

  public void setOutputDir(String outputDir) {
    this.curOutputDir = new File(outputDir);
    cleanOutputDir();
  }

  public void setExpectedDir(File expectedDir) {
    this.curExpectedDir = expectedDir;
  }

  public void setExpectedDir(String expectedDir) {
    this.curExpectedDir = new File(expectedDir);
  }

  /**
   * Version of {@link #compareAgainstExpected(boolean)} that does not recurse into subdirectories.
   */
  public void compareTopDirAgainstExpected(boolean truncate) throws Exception {
    if (null == curExpectedDir) {
      throw new Exception("compareAgainstExpected() called without calling startTest() first");
    }
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
   * Compare output files for the current test against the corresponding "gold standard" files
   * stored on disk.
   * 
   * @param truncate true to truncate the files being compared to {@link #TRUNCATE_LEN_KCHAR} kb
   */
  public void compareAgainstExpected(boolean truncate) throws Exception {
    if (null == curExpectedDir) {
      throw new Exception("compareAgainstExpected() called without calling startTest() first");
    }

    // A queue of paths (relative to expected directory) to check
    LinkedList<String> q = new LinkedList<String>();

    // Start out with the contents of the root directory.
    File[] expectedFiles = curExpectedDir.listFiles();

    if (null == expectedFiles) {
      throw new Exception("Expected dir " + curExpectedDir + " does not exist");
    }

    for (File file : expectedFiles) {
      String name = file.getName();
      if (false == name.startsWith(".")) {
        q.add(file.getName());
      }
    }

    // Now process the queue until we run out of things to compare.
    File expectedDir = curExpectedDir;
    while (q.size() > 0) {
      String relPath = q.removeFirst();

      File expectedFile = new File(expectedDir, relPath);
      if (expectedFile.isDirectory()) {
        // Directory --> add children to the queue.
        File[] children = expectedFile.listFiles();
        for (File child : children) {
          String name = child.getName();

          // Skip directories and files whose names start with "."
          if (false == name.startsWith(".")) {
            String childPath = relPath + File.separator + name;
            q.add(childPath);
          }
        }
      } else {
        // File --> compare
        compareAgainstExpected(relPath, truncate);
      }
    }

  }

  /**
   * Helper method to emulate the old behavior of only truncating expected files by characters only.
   * 
   * @throws Exception
   */
  public void truncateExpectedFiles() throws Exception {
    truncateExpectedFiles(false);
  }

  /**
   * Truncate all the "gold standard" files stored on disk to {@link #TRUNCATE_LEN_KCHAR} kb or to
   * {@link #TestUtils .EXPECTED_RESULTS_FILE_NUM_LINES} lines if HTML.
   * <p>
   * <b>NOTE:</b> This method only truncates the files in the top-level directory and does NOT
   * recurse to subdirs.
   */
  public void truncateExpectedFiles(boolean truncByLines) throws Exception {

    if (null == curExpectedDir) {
      throw new Exception("truncateExpectedFiles() called without calling startTest() first");
    }
    File[] expectedFiles = curExpectedDir.listFiles();
    if (null == expectedFiles) {
      throw new Exception("Expected dir " + curExpectedDir + " does not exist");
    }

    for (File file : expectedFiles) {
      // Skip directories (including the CVS directory)
      if (file.isFile()) {
        // Skip TAM files
        if (file.getName().endsWith(".htm")) {
          if (truncByLines) {
            TestUtils.truncateHTML(file, TestUtils.EXPECTED_RESULTS_FILE_NUM_LINES);
          } else {
            truncateExpectedFile(file.getName());
          }
        }
      }
    }
  }

  /**
   * Truncate all the output files stored on disk to {@link #TRUNCATE_LEN_KCHAR} kb.
   * 
   * @param trunByLines ignored but kept around to avoid breaking code in other projects.
   */
  public void truncateOutputFiles(boolean truncByLines) throws Exception {
    File[] outputFiles = curOutputDir.listFiles();
    if (null == outputFiles) {
      throw new Exception("Output dir " + curOutputDir + " does not exist");
    }

    for (File file : outputFiles) {
      // Skip directories (including the CVS directory)
      if (file.isFile()) {
        // Skip TAM files
        if (file.getName().endsWith(".tam")) {
          continue;
        }
        if (file.getName().endsWith(".htm") && truncByLines) {
          TestUtils.truncateHTML(file, TestUtils.EXPECTED_RESULTS_FILE_NUM_LINES);
        } else {
          truncateFile(file);
        }
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

    // handle instrumentation file comparison in a special way
    if (filename.endsWith(TestConstants.INFO_JSON_FILE_NAME)) {
      compareInfoFiles(expectedFile.getCanonicalFile(), outputFile.getCanonicalFile());
    } else { // not a instrumentation info file

      if (truncate) {
        System.err.printf("Truncating output file %s\n", outputFile);
        truncateFile(outputFile);
      }

      System.err.printf("Comparing output file %s against expected output...\n", filename);
      System.err.printf("--> Files being compared are:\n" + //
          "        %s\n" + //
          "        %s\n", expectedFile.getCanonicalFile(), outputFile.getCanonicalFile());

      // Use JUnit extensions to do file comparison.
      if (filename.endsWith(TestConstants.JSON_FILE_EXTENSION)) {
        JsonNode outputJson = mapper.readTree(outputFile);
        JsonNode expectedJson = mapper.readTree(expectedFile);
        Assert.assertEquals(expectedJson, outputJson);
      } else {

        BufferedReader outputBR = new BufferedReader(new FileReader(outputFile));
        BufferedReader expectedBR = new BufferedReader(new FileReader(expectedFile));
        try {
          // Compare output file and expected file line by line
          while (true) {
            String outputLine = outputBR.readLine();
            String expectedLine = expectedBR.readLine();

            // Exit comparison if both files reach the end of the streams
            if (outputLine == null && expectedLine == null) {
              break;
              // Fail the test case if one file ends sooner than another
            } else if (outputLine == null || expectedLine == null) {
              Assert.fail(
                  "One file has more lines than another. Two files need to have same number of lines.");
            }

            JsonNode outputJson = mapper.readTree(outputLine);
            JsonNode expectedJson = mapper.readTree(expectedLine);
            Assert.assertEquals(expectedJson, outputJson);
          }
        } finally {
          outputBR.close();
          expectedBR.close();
        }
      }
    }
    // end: handler for non-instrumentation info files
  }

  /**
   * Special comparison for the instrumentation file, JSON to JSON, so that we can skip comparing
   * certain values such as the runtime or exception stack traces, which may be different when
   * running on different machines, or when running in Eclipse vs. maven.
   * 
   * @param expectedFile json file containing the expected instrumentation info
   * @param outputFile json file containing the actual instrumentation info
   * @throws IOException
   * @throws Exception
   */
  private void compareInfoFiles(File expectedFile, File outputFile) throws IOException, Exception {
    System.err.printf("Comparing output file %s against expected output...\n",
        outputFile.getName());
    System.err.printf("--> Files being compared are:\n" + //
        "        %s\n" + //
        "        %s\n", expectedFile.getCanonicalFile(), outputFile.getCanonicalFile());

    // Read the document and config file as JSON Records
    ObjectNode expected = mapper.readValue(expectedFile, ObjectNode.class);
    ObjectNode output = mapper.readValue(outputFile, ObjectNode.class);
    Iterator<String> expectedKeys = expected.fieldNames();

    // Iterate through the expected fields
    while (expectedKeys.hasNext()) {
      String expectedFieldName = expectedKeys.next();

      // FIXME: use String value for now
      JsonNode expectedVal = expected.get(expectedFieldName);

      JsonNode outputVal = output.get(expectedFieldName);
      // If the output file does not have the expected field name, throw an exception
      if (null == outputVal)
        throw new Exception(
            String.format("Expected property '%s' with value '%s' not present in output file: %s",
                expectedFieldName, expectedVal.asText(), outputFile.getCanonicalFile()));

      // Skip comparing values for certain fields
      if (expectedFieldName.equals(AnnotationServiceConstants.RUNNING_TIME_FIELD_NAME)
          || expectedFieldName.equals(AnnotationServiceConstants.EXCEPTION_STACKTRACE_FIELD_NAME))
        continue;

      // If the output file has a different value for this field, throw an exception
      // FIXME Don't use String value
      if (!expectedVal.equals(outputVal))
        throw new Exception(String.format(
            "Expected property '%s' with value '%s' present with different value '%s' in output file: %s",
            expectedFieldName, expectedVal.asText(), outputVal.asText(),
            outputFile.getCanonicalFile()));

      // If we get here the expected and output values are equal
    }

    // Throw an exception if the expected and output don't have the same set of keys
    if (expected.size() != output.size())
      throw new Exception(
          String.format("Different field names in expected file '%s' and output file: %s",
              expectedFile.getCanonicalFile(), outputFile.getCanonicalFile()));
  }

  /**
   * Compare an output file from a regression test against the expected output, skipping some lines
   * from the beginning and/or end. Mainly intended to compare module metadata.xml files, whose
   * header changes every time.
   * 
   * @param filename name of the output file, not containing the directory it's in; file is assumed
   *        to be in {@link #curOutputDir}.
   * @param linesToSkip how many lines to ignore (from both input files) before beginning the
   *        comparison; for use when the files are expected to differ slightly in the header
   * @param linesToCompare how many lines of the file to compare with each other, or -1 to look at
   *        both files to the end
   * @throws IOException
   * @throws Exception
   */
  public void compareAgainstExpected(String filename, int linesToSkip, int linesToCompare)
      throws IOException, Exception {
    File outputFile = new File(curOutputDir, filename);
    File expectedFile = new File(curExpectedDir, filename);

    System.err.printf("Comparing output file %s against expected output...\n", filename);
    TestUtils.compareFiles(expectedFile, outputFile, linesToSkip, linesToCompare);
  }

  /**
   * Truncate all the "gold standard" files stored on disk to {@link #TRUNCATE_LEN_KCHAR} kb
   * 
   * @param filename name of the expected file, not containing the directory it's in; file is
   *        assumed to be in {@link #expectedDir}
   * @throws IOException
   */
  public void truncateExpectedFile(String filename) throws IOException {
    File expectedFile = new File(curExpectedDir, filename);
    truncateFile(expectedFile);
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
    // char[] buf = new char[TRUNCATE_LEN_KCHAR * 1024];

    // InputStreamReader in = new InputStreamReader (new FileInputStream (origFile), FILE_ENCODING);
    // int nread = in.read (buf);
    // in.close ();
    //
    // if (nread < buf.length) {
    // // Didn't even fill up the buffer; no truncation needed.
    // return;
    // }

    int maxNumChars = TRUNCATE_LEN_KCHAR * 1024;

    // Read line by line and count each end of line (whether it is LF+CR as in Windows, or CR as in
    // Linux) as a
    // single
    // character. With the previous method above, on Windows new lines are counted as 2 characters
    // (LF+CR) but when
    // checked into RTC (LF+CR) is transformed into (CR), so the truncated expected file in RTC has
    // less characters
    // than
    // the truncated actual file and that messes up the tests.
    BufferedReader inBuf =
        new BufferedReader(new InputStreamReader(new FileInputStream(origFile), FILE_ENCODING));

    // Start from -1 because each time we read a line we add +1 for the previous line, and we do so
    // for the first
    // line
    int numCharsRead = -1;
    StringBuffer buf = new StringBuffer();

    while (numCharsRead < maxNumChars) {
      String line = inBuf.readLine();
      if (null == line)
        break;

      // Add the line, and a new line character.
      buf.append(line);
      buf.append("\n");

      // Add a single character for the end of line
      numCharsRead += line.length() + 1;
    }

    inBuf.close();

    if (numCharsRead <= maxNumChars) {
      // Didn't even have the maximum number of characters; no truncation needed.
      return;
    }

    // Write out the truncated version of the file.
    // OutputStreamWriter out = new OutputStreamWriter (new FileOutputStream (origFile),
    // FILE_ENCODING);
    // out.write (buf.toString ());

    // Use low-level IO to bypass CR -> CR+LF conversion
    FileOutputStream out = new FileOutputStream(origFile);
    out.write(buf.toString().getBytes(FILE_ENCODING));
    out.close();
  }

  /**
   * @return the name of the java executable being used by the current process; useful for spawning
   *         new subprocesses
   */
  public static String getJavaExecPath() throws Exception {
    // Possible names for the java executable
    final String[] javaExecNames = {"java", "java.exe", "javaw", "javaw.exe"};
    String javaHome = System.getProperty("java.home");
    File javaBinDir = new File(javaHome, "bin");
    // Try the different options for the Java executable.
    for (String name : javaExecNames) {
      File javaExec = new File(javaBinDir, name);
      if (javaExec.exists()) {
        try {
          return javaExec.getCanonicalPath();
        } catch (IOException e) {
          // This should never happen; we just checked whether the
          // file exists!
          throw new RuntimeException(e);
        }
      }
    }
    // If we get here, we didn't find the executable.
    throw new Exception("Can't find Java executable at " + javaHome);
  }
}
