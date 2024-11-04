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
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Test;

import com.ibm.avatar.algebra.util.lang.LangCode;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.algebra.util.tokenize.TokenizerConfig;
import com.ibm.avatar.api.AQLProfiler;
import com.ibm.avatar.api.AQLProfiler.ProfileSummary;
import com.ibm.avatar.api.CompilationSummary;
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.aql.compiler.CompilerWarning.WarningType;
import com.ibm.avatar.logging.Log;
import com.ibm.avatar.logging.LogImpl;
import com.ibm.avatar.logging.MsgType;

public class ProfilerAPITests extends RuntimeTestHarness {
  private static String testDocDir =
      String.format("%s/%s", TestConstants.AQL_DIR, "ProfilerAPITests");

  public static void main(String[] args) {
    try {

      ProfilerAPITests t = new ProfilerAPITests();

      // t.setUp ();

      long startMS = System.currentTimeMillis();

      t.profileCSVDocCollectionTest();

      long endMS = System.currentTimeMillis();

      // t.tearDown ();

      double elapsedSec = (endMS - startMS) / 1000.0;

      System.err.printf("Test took %1.3f sec.\n", elapsedSec);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Test to profile compiled modules with references to external artifacts.
   * 
   * @throws Exception
   */
  // @Test
  // Dharmesh will uncomment this once implemented
  public void profileModulesWithETITest() throws Exception {
    Assert.fail("yet to implement");
  }

  @Test
  public void profilerUnCompiledModulesAPITest() throws Exception {
    startTest();

    String tamPath = String.format("%s/%s", TestConstants.AQL_DIR, "TAMTests");
    String[] inputModuleURIs = new String[3];
    inputModuleURIs[0] = new File(String.format("%s/%s", tamPath, "person")).toURI().toString();
    inputModuleURIs[1] = new File(String.format("%s/%s", tamPath, "phone")).toURI().toString();
    inputModuleURIs[2] =
        new File(String.format("%s/%s", tamPath, "personPhone")).toURI().toString();
    String modulePath = null;

    // instantiate profiler
    AQLProfiler profiler = AQLProfiler.createSourceModulesProfiler(inputModuleURIs, modulePath,
        null, new TokenizerConfig.Standard());
    profiler.setMinRuntimeSec(30);

    File DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR, "/TAMTests/input.zip");

    profiler.profile(DOCS_FILE, LangCode.en, null);

    ProfileSummary collectSummary = profiler.collectSummary();

    Assert.assertEquals(19, collectSummary.numStmts);

    endTest();
  }

  /**
   * Test to verify the AQLProfiler main method, this interface is consumed by script.
   * 
   * @throws Exception
   */
  @Test
  public void profilerScriptInterfaceTest() throws Exception {
    startTest();

    String docsFile = TestConstants.TEST_DOCS_DIR + "/TAMTests/input";

    String tamPath = String.format("%s/%s", TestConstants.AQL_DIR, "TAMTests");
    String[] inputModuleURIs = new String[3];
    inputModuleURIs[0] = new File(String.format("%s/%s", tamPath, "person")).toURI().toString();
    inputModuleURIs[1] = new File(String.format("%s/%s", tamPath, "phone")).toURI().toString();
    inputModuleURIs[2] =
        new File(String.format("%s/%s", tamPath, "personPhone")).toURI().toString();

    StringBuilder moduleSrcPath = new StringBuilder();
    for (int i = 0; i < inputModuleURIs.length; i++) {
      moduleSrcPath.append(inputModuleURIs[i]);
      moduleSrcPath.append(';');
    }

    String[] args = {"-d", new File(docsFile).getAbsolutePath(), "-s",
        moduleSrcPath.substring(0, moduleSrcPath.length() - 1), "-T", "30"};
    AQLProfiler.main(args);

    // FIXME: TC without assert
    endTest();
  }

  /**
   * Test to verify, that json document collection containing external view tuples are profiled.
   * This test also verify, profiling of loaded operator graph.
   * 
   * @throws Exception
   */
  @Test
  public void profileDocWithExtViewTest() throws Exception {
    startTest();

    // Compile test module with external view declared and later load module
    OperatorGraph loadedOG = compileAndLoadModule("profileDocWithExtViewTest", null);

    // json doc collection to profile
    File jsonDoc = new File(testDocDir, "/profileDocWithExtViewTest/extViewData.json");

    AQLProfiler profiler = AQLProfiler.createProfilerFromOG(loadedOG);
    profiler.setMinRuntimeSec(5);

    // Lets profile
    profiler.profile(jsonDoc, LangCode.en, null);
    profiler.dumpTopDocuments();

    endTest();
  }

  /**
   * Test to exercise the profiler’s ability to profile modules with customized input document
   * schema, declared through 'require document ...' statement, on CSV input document
   * collections.<br>
   * This test case covers the scenario mentioned in defect .
   * 
   * @throws Exception
   */
  @Test
  public void profileCSVDocCollectionTest() throws Exception {
    startTest();

    setTokenizerConfig(TestConstants.STANDARD_TOKENIZER);

    // Compile and load module with customized input document schema, [text Text]
    OperatorGraph loadedOG = compileAndLoadModule("csvAnnotator", null);

    // Input document collections on which the module run is to be profiled
    File csvCollection = new File(
        String.format("%s/docs/ProfilerAPITests/testdata_upd.csv", TestConstants.TESTDATA_DIR));

    // Instantiate Profiler
    AQLProfiler profiler = AQLProfiler.createProfilerFromOG(loadedOG);

    // Profile loaded module on document collections(non-JSON) in various formats
    profiler.profile(csvCollection, null, null);
    profiler.dumpTopDocuments();

    endTest();
  }

  /**
   * Test to exercise the profiler’s ability to profile modules with customized input document
   * schema, declared through 'require document ...' statement, on CSV input document collections
   * with a custom separator.<br>
   * 
   * @throws Exception
   */
  @Test
  public void profileCSVCustomSeparatorTest() throws Exception {
    startTest();

    setTokenizerConfig(TestConstants.STANDARD_TOKENIZER);

    // Compile and load module with customized input document schema, [text Text]
    OperatorGraph loadedOG = compileAndLoadModule("csvAnnotator", null);

    // Input document collections on which the module run is to be profiled
    File csvCollection = new File(
        String.format("%s/docs/ProfilerAPITests/NHTSA_10_mod.csv", TestConstants.TESTDATA_DIR));

    // Instantiate Profiler
    AQLProfiler profiler = AQLProfiler.createProfilerFromOG(loadedOG);

    // Profile loaded module on document collections(non-JSON) in various formats
    profiler.profile(csvCollection, null, null, '\t');
    profiler.dumpTopDocuments();

    endTest();
  }

  /**
   * Test case for defect . It tests that
   * <ul>
   * <li>the compiler reports number of AQL statements compiled,
   * <li>the profiler reports number of view with at least one sample, and
   * <li>the compiler registers compiler warnings.
   * </ul>
   * 
   * @throws Exception
   */
  @Test
  public void profilerMessagesTest() throws Exception {
    startTest();

    setTokenizerConfig(TestConstants.STANDARD_TOKENIZER);

    /**
     * A local class to capture log messages
     */
    class MyLogImpl extends LogImpl {
      Set<String> messages = new HashSet<String>();
      private final LogImpl logger;

      public MyLogImpl(LogImpl logger) {
        this.logger = logger;
      }

      @Override
      public void log(MsgType type, String msg) {
        messages.add(msg);
        logger.log(type, msg);
      }
    }

    LogImpl oldLogger = Log.getLogger();
    try {
      // Temporarily replace the logger
      MyLogImpl tmpLogger = new MyLogImpl(oldLogger);
      Log.setLogger(tmpLogger);

      // Compile and load module
      OperatorGraph loadedOG = compileAndLoadModule("RevenueByDivision_BasicFeatures", null);

      // Check the expected warnings are issued
      CompilationSummary summary = getSummary();
      WarningType[] warnings = new WarningType[] {WarningType.RSR_FOR_THIS_REGEX_NOT_SUPPORTED};
      verifyWarnings(summary, warnings);

      // Instantiate Profiler
      AQLProfiler profiler = AQLProfiler.createProfilerFromOG(loadedOG);
      profiler.setMinRuntimeSec(12);

      // Input document collection
      File docs =
          new File(TestConstants.TESTDATA_DIR, "/docs/ProfilerAPITests/ibmQuarterlyReports.zip");

      // Profile loaded module on document collections
      profiler.profile(docs, null, null);

      // Get the logged messages
      Set<String> messages = tmpLogger.messages;

      // Check the intended log message for expected patterns
      String[] expectedMessagePattern = new String[] {"Compiled 6 AQL statements...1 warnings.",
          "Total \\d{1,2} post-rewrite views with at least one sample"};
      for (String message : expectedMessagePattern) {
        boolean matches = false;
        for (String msg : messages) {
          if (Pattern.matches(message, msg)) {
            matches = true;
            break;
          }
        }
        assertTrue("Expect log message: " + message, matches);
      }

    } finally {
      // set back the old logger
      Log.setLogger(oldLogger);
    }

    endTest();
  }

  /**
   * Generic method for running the profiler and returning the top operator. Used by several tests
   * in this class to verify the overhead is correctly charged to the Tokenizer or PartOfSpeech for
   * operators that trigger tokenization
   * 
   * @param moduleNames
   * @param docsFile
   * @return the last line in the profiler Top Operators output, just before the footer
   * @throws Exception
   */
  private String genericMostExpensiveOperatorTest(String[] moduleNames, File docsFile)
      throws Exception {
    // Compile and load module with customized input document schema, [text Text]
    OperatorGraph loadedOG = compileAndLoadModules(moduleNames, null);

    // Instantiate Profiler
    AQLProfiler profiler = AQLProfiler.createProfilerFromOG(loadedOG);

    // Profile loaded module on document collections(non-JSON) in various formats
    profiler.profile(docsFile, null, null, '\t');

    // Dump top operators to console
    StringBuffer topOperators = new StringBuffer();
    profiler.dumpTopOperators(topOperators);
    Log.info("%s", topOperators);

    // Get the most expensive operator, which appears at the bottom of the profiler output, just
    // before the footer
    // To footer has 4 lines, so get the line just before
    String[] profilerTopOps = topOperators.toString().split("[\n\r]+");
    String topOperatorLine = profilerTopOps[profilerTopOps.length - 5];

    return topOperatorLine;
  }
}
