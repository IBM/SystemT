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
import java.io.FileWriter;
import java.io.PrintStream;
import java.net.URI;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.util.dict.CompiledDictionary;
import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.api.CompileAQL;
import com.ibm.avatar.api.CompileAQLParams;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.aql.compiler.Compiler;
import com.ibm.avatar.aql.compiler.ParseToCatalog;
import com.ibm.avatar.aql.planner.Planner;
import com.ibm.avatar.aql.tam.ModuleUtils;
import com.ibm.avatar.aql.tam.TAM;
import com.ibm.avatar.aql.tam.TAMSerializer;
import com.ibm.avatar.logging.Log;

/**
 * Tests that extract information from the blog data set using AQL
 */
public class AQLBlogTests extends RuntimeTestHarness {

  /** Directory with AQL files for the test cases in this class. */
  public static final String AQL_DIR = TestConstants.AQL_DIR + "/AQLBlogTests";

  /**
   * The expected output files are truncated to save space. This constant holds the number of lines
   * the files are truncated to.
   */
  public static int EXPECTED_RESULTS_FILE_NUM_LINES = 1000;

  /**
   * Should we use the small input file, even if there is a large one available?
   */
  public static boolean FORCE_SMALL_FILE = false;

  /**
   * Working directory for reading auxiliary dictionary files.
   */
  public static final String DICTS_DIR = TestConstants.TESTDATA_DIR + "/dictionaries/blog";

  public static void main(String[] args) {
    try {
      AQLBlogTests t = new AQLBlogTests();

      t.setUp();

      long startMS = System.currentTimeMillis();

      t.bandReviewMergeTest();

      long endMS = System.currentTimeMillis();

      t.tearDown();

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
  private File docsFile;

  @Before
  public void setUp() throws Exception {

    // Renice the current thread to avoid locking up the entire system.
    Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

    // Use the blog entry dump that Huaiyu has supplied.
    String BIG_FILE_NAME = "testdata/docs/common/text2k.del";
    // String BIG_FILE_NAME = "testdata/text50k.del";
    // String BIG_FILE_NAME = "testdata/blogentry_100k.del";
    // String BIG_FILE_NAME = "testdata/blogentry_nojack.del";

    String SMALL_FILE_NAME = "testdata/testtext.del";

    // The full data set for this series of tests is huge, but a
    // much smaller version is checked into CVS for basic testing.
    // Attempt to use the big dump file, if possible.
    File bigdump = new File(BIG_FILE_NAME);
    boolean usingBigFile = (bigdump.exists());

    if (FORCE_SMALL_FILE) {
      usingBigFile = false;
    }

    String textfilename = usingBigFile ? BIG_FILE_NAME : SMALL_FILE_NAME;
    // scan = new DBDumpFileScan(textfilename);
    docsFile = new File(textfilename);

  }

  @After
  public void tearDown() {
    // scan = null;
    endTest();

    // Deregister the Derby driver so that other tests can connect to other
    // databases. This is probably not necessary (we don't currently use
    // derby), but we do it just in case.
    try {
      DriverManager.getConnection("jdbc:derby:;shutdown=true");
    } catch (SQLException e) {
      // The shutdown command always raises a SQLException
      // See http://db.apache.org/derby/docs/10.2/devguide/
    }
    System.gc();
  }

  /**
   * Parse and compile the band review annotator.
   */
  @Test
  public void bandReviewParseTest() throws Exception {
    startTest();

    setWriteCharsetInfo(true);

    String filename = AQL_DIR + "/bandtest.aql";

    // Parse the AQL file
    compileAQL(new File(filename), DICTS_DIR);

    // Load tam
    TAM tam =
        TAMSerializer.load(Constants.GENERIC_MODULE_NAME, getCurOutputDir().toURI().toString());

    String aog = tam.getAog();

    String[][] dictInfo = {{"abbreviations.dict", "testdata/dictionaries/blog/abbreviations.dict"},
        {"crowdwords.dict", "testdata/dictionaries/blog/crowdwords.dict"},
        {"performwords.dict", "testdata/dictionaries/blog/performwords.dict"},
        {"interact_words.dict", "testdata/dictionaries/blog/interact_words.dict"}};

    runAOGString(docsFile, aog, dictInfo);

    compareAgainstExpected("ConcertDescRun.htm", true);
  }

  /**
   * Parse the band review annotator, regenerate the AQL, parse again, and run.
   */
  // JAY: commenting out this test case because defect blocks AQL elements to have dots in them and
  // hence parsing
  // a 'dumped' AQL leads to errors, because implicit dictionaries have dots in their names!
  // @Test
  public void bandReviewParseTwiceTest() throws Exception {
    File genericModuleDir = null;
    try {
      startTest();

      String filename = AQL_DIR + "/bandtest.aql";

      String dumpFileName = "bandtest2.aql";

      URI outputURI = getCurOutputDir().toURI();

      // Parse the AQL file and populate the catalog
      CompileAQLParams params =
          new CompileAQLParams(new File(filename), outputURI.toString(), DICTS_DIR);

      // Generate a "dumb" (not optimized) query plan in AOG format.
      genericModuleDir = ModuleUtils.createGenericModule(params, new HashMap<String, String>(),
          new ArrayList<Exception>());

      CompileAQLParams modularParams = new CompileAQLParams();
      modularParams.setInputModules(new String[] {genericModuleDir.toURI().toString()});
      modularParams.setOutputURI(outputURI.toString());

      ParseToCatalog catalogGenerator = new ParseToCatalog();
      catalogGenerator.parse(modularParams);

      // Dump the parse tree back to a buffer.
      ByteArrayOutputStream buf = new ByteArrayOutputStream();
      catalogGenerator.getCatalog().dump(new PrintStream(buf, true, "UTF-8"));

      File dumpAQL = new File(getCurOutputDir(), dumpFileName);

      // Dump the buffer to a file for comparison.
      FileOutputStream aqlOut = new FileOutputStream(dumpAQL);
      buf.writeTo(aqlOut);
      aqlOut.close();

      Log.info("Generated AQL is:\n%s", buf.toString());

      // Reparse.
      File inputFile = null;
      CompileAQLParams reCompileParams =
          new CompileAQLParams(inputFile, outputURI.toString(), DICTS_DIR);
      reCompileParams.setInputStr(buf.toString());
      CompileAQL.compile(reCompileParams);

      // Load tam
      TAM tam = TAMSerializer.load(Constants.GENERIC_MODULE_NAME, reCompileParams.getOutputURI());

      String aog = tam.getAog();

      String[][] dictInfo = {{"crowdwords.dict", "testdata/dictionaries/blog/crowdwords.dict"},
          {"performwords.dict", "testdata/dictionaries/blog/performwords.dict"},
          {"interact_words.dict", "testdata/dictionaries/blog/interact_words.dict"}};

      setWriteCharsetInfo(true);
      runAOGString(docsFile, aog, dictInfo);

      compareAgainstExpected(dumpFileName, false);
      compareAgainstExpected("ConcertDescRun.htm", false);
    } finally {
      if (genericModuleDir != null) {
        FileUtils.deleteDirectory(genericModuleDir.getParentFile()); // parent file is the
                                                                     // moduleUtilsTmp directory
        // created by ModuleUtils.createGenericModule()
      }
    }

  }

  /**
   * Runs the band review annotator aql file through the naive merge join planner.
   * 
   * @throws Exception
   */
  @Test
  public void bandReviewMergeTest() throws Exception {
    Compiler compiler = null;
    try {
      startTest();
      String aqlFileName = AQL_DIR + "/bandtest.aql";

      // Parse the AQL file
      CompileAQLParams compileParam = new CompileAQLParams();
      compileParam.setDataPath(DICTS_DIR);
      compileParam.setInputFile(new File(aqlFileName));
      compileParam.setPerformSDM(false);
      compileParam.setOutputURI(getCurOutputDir().toURI().toString());
      compileParam.setTokenizerConfig(getTokenizerConfig());

      // Generate a "dumb" (not optimized) query plan in AOG format.
      compiler = new Compiler();
      Planner p = new Planner(Planner.ImplType.NAIVE_MERGE);
      // No need to set the planner SDM, as long as we specify the right value in CompileAQLParams
      // p.setPerformSDM(false);
      compiler.setPlanner(p);
      compiler.compile(compileParam);

      // Load tam
      TAM tam = TAMSerializer.load(Constants.GENERIC_MODULE_NAME, compileParam.getOutputURI());

      String aog = tam.getAog();
      Map<String, CompiledDictionary> allCompiledDicts = tam.getAllDicts();
      String[] dictNames = allCompiledDicts.keySet().toArray(new String[0]);

      // Write the resulting AOG plan out to a file.
      File aogfile = new File(getCurOutputDir(), "bandmerge.aog");
      FileWriter out = new FileWriter(aogfile);
      out.append(aog);
      out.close();

      // Run the resulting annotator.
      setDataPath(DICTS_DIR);

      String[][] dictInfo = new String[allCompiledDicts.size()][2];
      for (int i = 0; i < dictNames.length; i++) {
        String dict = dictNames[i];
        dictInfo[i][0] = dict;
        dictInfo[i][1] = DICTS_DIR + "/" + dict;
      }

      setWriteCharsetInfo(true);

      runAOGFile(docsFile, aogfile, dictInfo);

      compareAgainstExpected("ConcertDescRun.htm", true);
    } finally {
      if (null != compiler)
        compiler.deleteTempDirectory();
    }
  }
}
