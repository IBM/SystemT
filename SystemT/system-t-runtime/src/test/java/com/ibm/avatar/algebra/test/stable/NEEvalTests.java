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

import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.util.file.SearchPath;
import com.ibm.avatar.algebra.util.lang.LangCode;
import com.ibm.avatar.algebra.util.test.MemoryProfiler;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.api.AQLProfiler;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.DocReader;
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.logging.Log;

/**
 * Tests with the data and rules used in the named-entity evaluation paper.
 */
public class NEEvalTests extends RuntimeTestHarness {

  /**
   * Directory where AQL files referenced in this class's regression tests are located.
   */
  public static final File AQL_FILES_DIR = new File(TestConstants.AQL_DIR, "neEvalTests");

  /**
   * Main AQL file for the annotators used in the evaluation
   */
  public static final File AQL_FILE =
      new File(AQL_FILES_DIR, "ne-library-annotators-for-ACE2005.aql");

  /**
   * Dictionary path for running the annotators.
   */
  public static final String DICT_PATH_STR = AQL_FILES_DIR.getPath() + "/GenericNE/dictionaries";

  /**
   * Include path for running the annotators.
   */
  public static final String INCLUDE_PATH_STR = AQL_FILES_DIR.getPath();

  /**
   * UDF jar file path for running the annotators.
   */
  public static final String UDF_PATH_STR = AQL_FILES_DIR.getPath() + "/GenericNE/udfjars";

  /**
   * Zip file containing the training documents from ACE 2005, used for performance tests for the
   * evaluation paper.
   */
  public static final File ACE_DOCS_FILE =
      new File(TestConstants.DUMPS_DIR, "ace2005trainingDoc.zip");

  /**
   * File encoding used by all tests in this class.
   */
  public static final String FILE_ENCODING = "UTF-8";

  /**
   * Main method for running individual tests.
   * 
   * @param args
   */
  public static void main(String[] args) {
    try {

      NEEvalTests t = new NEEvalTests();

      t.setUp();

      long startMS = System.currentTimeMillis();

      t.runAnnotTest();

      long endMS = System.currentTimeMillis();

      t.tearDown();

      double elapsedSec = ((double) (endMS - startMS)) / 1000.0;

      System.err.printf("Test took %1.3f sec.\n", elapsedSec);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Before
  public void setUp() throws Exception {

  }

  @After
  public void tearDown() {

  }

  /**
   * @return true if we're running on a Java 6 or later JVM.
   */
  public static boolean javaSixOrLater() {
    // String got a new method "isEmpty()" with Java 6.
    try {
      String.class.getMethod("isEmpty");
    } catch (SecurityException e) {
      throw new RuntimeException(e);
    } catch (NoSuchMethodException e) {
      System.err.printf("No isEmpty() method in String; " + "assuming Java version < 6");
      return false;
    }
    return true;
  }

  /**
   * Simple test that runs the annotators over the ACE2005 documents, using the usual testing
   * infrastructure.
   * <p>
   * TODO: Final comparison disabled by Huaiyu Zhu 2013-09. The HTML visualizer chooses a wrong span
   * column for the Location view that happens to usually contain zero-length spans converted from
   * ''. Previously this was treated as a text column due to the Span/Text union bug.
   */
  @Test
  public void runAnnotTest() throws Exception {
    startTest();

    if (false == javaSixOrLater()) {
      // Annotators reference UDFs that only work with Java >= 6
      return;
    }

    setDataPath(DICT_PATH_STR, INCLUDE_PATH_STR, UDF_PATH_STR);

    setWriteCharsetInfo(true);
    runNonModularAQLTest(ACE_DOCS_FILE, AQL_FILE);

    // Make sure that the annotators produced the same output as before.
    // util.truncateExpectedFiles();
    truncateOutputFiles(true);

    // TODO: This is comparison is temporarily disabled. See comment for this method.
    // compareAgainstExpected (true);

    endTest();
  }

  /**
   * Test that compiles the extractor and verifies the generated AOG is Reproduces defect : The AQL
   * compiler may generate different AOGs when invoked on the same input AQL.
   */
  @Test
  public void compilePlanTest() throws Exception {
    startTest();

    if (false == javaSixOrLater()) {
      // Annotators reference UDFs that only work with Java >= 6
      return;
    }

    setDataPath(DICT_PATH_STR, INCLUDE_PATH_STR, UDF_PATH_STR);

    // Compile and dump the AOG
    compileAQL(AQL_FILE, getDataPath());

    // Make sure the AOG plan is the same as the expected value - don't truncate! (the first 1000
    // lines are likely to be
    // dictionary entries)
    compareAgainstExpected(false);

    endTest();
  }

  /**
   * Test case that runs the annotators through the AQL profiler.
   */
  @Test
  public void profileTest() throws Exception {
    startTest();

    if (false == javaSixOrLater()) {
      // Annotators reference UDFs that only work with Java >= 6
      return;
    }

    // Compile AQL file
    String datapath = String.format("%s;%s;%s", DICT_PATH_STR, INCLUDE_PATH_STR, UDF_PATH_STR);
    compileAQL(AQL_FILE, datapath);

    // profile
    String moduleURI = getCurOutputDir().toURI().toString();
    AQLProfiler prof = AQLProfiler.createCompiledModulesProfiler(
        new String[] {Constants.GENERIC_MODULE_NAME}, moduleURI, null, null);
    prof.setMinRuntimeSec(30);

    prof.profile(ACE_DOCS_FILE, LangCode.en, null);

    Log.info("%1.2f kchar / sec", prof.getCharPerSec() / 1024.0);
    endTest();
  }

  /**
   * Test case that runs the annotators through the Java API and measures throughput.
   */
  @Test
  public void benchmarkTest() throws Exception {

    startTest();

    if (false == javaSixOrLater()) {
      // Annotators reference UDFs that only work with Java >= 6
      return;
    }

    // How many times to go through the documents.
    final int NUM_REPEAT = 10;

    // AQL compiler expects a data path that is used for finding
    // dictionaries, UDFs, and include files.
    String dataPathStr = String.format("%s%c%s%c%s", DICT_PATH_STR, SearchPath.PATH_SEP_CHAR,
        UDF_PATH_STR, SearchPath.PATH_SEP_CHAR, INCLUDE_PATH_STR);

    compileAQL(AQL_FILE, dataPathStr);

    MemoryProfiler.dumpMemory("Before operator graph instantiation");

    OperatorGraph og = instantiateOperatorGraph(Constants.GENERIC_MODULE_NAME);

    MemoryProfiler.dumpMemory("After operator graph instantiation");

    long startMS = System.currentTimeMillis();
    long nchar = 0;

    for (int i = 0; i < NUM_REPEAT; i++) {

      DocReader docReader = new DocReader(ACE_DOCS_FILE);
      FieldGetter<Text> textAcc = docReader.getDocSchema().textAcc(Constants.DOCTEXT_COL);

      while (docReader.hasNext()) {
        Tuple docTup = docReader.next();

        // systemt.annotateDoc(docText, LangCode.en, null);
        og.execute(docTup, null, null);

        nchar += textAcc.getVal(docTup).getText().length();
      }

      docReader.remove();

      MemoryProfiler.dumpMemory(String.format("After iteration %d", i));
    }

    long elapsedMS = System.currentTimeMillis() - startMS;
    double elapsedSec = (double) elapsedMS / 1000.0;

    MemoryProfiler.dumpMemory("After test");

    // Need a reference to the SystemT API object here to prevent it from
    // being garbage collected before the above dumpMemory() call.
    // systemt.getOutputTypeNames();

    double kchar = (double) nchar / 1024.0;
    double kcharPerSec = kchar / elapsedSec;

    Log.info("%1.1f kchar in %1.2f sec --> %1.2f kchar / sec", kchar, elapsedSec, kcharPerSec);

    endTest();

  }
}
