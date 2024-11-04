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
import java.sql.DriverManager;
import java.sql.SQLException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.join.SortMergeJoin;
import com.ibm.avatar.algebra.util.file.SearchPath;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;

/**
 * Various AOG files that reproduce bugs. Kept as test cases to ensure the bugs do not reappear.
 */
public class AOGBugTests extends RuntimeTestHarness {

  // private static final String AOG_FILES_DIR = TestConstants.AOG_DIR + "/aogBugTests";

  private static final String DOC_FILES_DIR = TestConstants.TEST_DOCS_DIR + "/aogBugTests";

  public static final File INPUT_FILE = new File(TestConstants.DUMPS_DIR, "enron1k.del");

  public static void main(String[] args) {
    try {

      AOGBugTests t = new AOGBugTests();

      t.setUp();

      long startMS = System.currentTimeMillis();

      t.regexEscapedSlashBugTest();

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

  @Before
  public void setUp() throws Exception {

    // For now, don't put any character set information into the header of
    // our output HTML.
    setWriteCharsetInfo(false);

    setDataPath(TestConstants.TESTDATA_DIR);

    // Renice the current thread to avoid locking up the entire system.
    Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

    // scan = new DBDumpFileScan(TestConstants.ENRON_1K_DUMP);
    // scan = new DBDumpFileScan("testdata/docs/tmp.del");
  }

  @After
  public void tearDown() {
    // scan = null;

    // Deregister the Derby driver so that other tests can connect to other
    // databases.
    try {
      DriverManager.getConnection("jdbc:derby:;shutdown=true");
    } catch (SQLException e) {
      // The shutdown command always raises a SQLException
      // See http://db.apache.org/derby/docs/10.2/devguide/
    }
    System.gc();
  }

  /**
   * Test of a bug in conditional evaluation. Due to incomplete resetting of operator state, some
   * operators were seeing old data when conditional evaluation was enabled.
   */
  @Test
  public void condEvalBugTest() throws Exception {

    // String filename = AOG_FILES_DIR + "/condEvalBugTest.aog";

    // Use the input file for the AQL version (at least for now)
    String INFILE_NAME =
        String.format("%s/AQLBugTests/%s.del", TestConstants.TEST_DOCS_DIR, "condEvalBug");

    File infile = new File(INFILE_NAME);
    startTest();

    setDumpPlan(false);
    setDisableOutput(false);
    setPrintTups(false);

    // Explicitly enable conditional evaluation.
    boolean oldval = SortMergeJoin.CONDITIONAL_EVAL;
    SortMergeJoin.CONDITIONAL_EVAL = true;

    // prepare list of dict file being referred in the aog
    String[][] dictInfo = {{"dictionaries/first.dict", "testdata/dictionaries/first.dict"}};

    runAOGFile(infile, dictInfo);

    // Reinstate the default setting.
    SortMergeJoin.CONDITIONAL_EVAL = oldval;

    truncateOutputFiles(true);
    // truncateExpectedFiles ();
    compareAgainstExpected(true);
    endTest();

    // setProgressInterval(1);

    // Checks disabled because of some cross-platform encoding issues.
    // util.setSkipFileComparison(true);

  }

  /**
   * Test of a bug in RSE join execution with a reversed FollowsTok() join predicate.
   */
  @Test
  public void rseBugTest() throws Exception {

    File DOCSFILE = new File(String.format("%s/rseBug.del", DOC_FILES_DIR));
    startTest();

    setDumpPlan(false);
    setDisableOutput(false);
    setPrintTups(false);

    // prepare list of dict file being referred in the aog
    String[][] dictInfo = {{"dictionaries/first.dict", "testdata/dictionaries/first.dict"}};

    runAOGFile(DOCSFILE, dictInfo);

    truncateOutputFiles(true);
    // truncateExpectedFiles ();
    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Test of a bug in parsing fastphone.aog. NOTE FROM LAURA 1/10/2012: This test was commented out
   * since revision 9 of CS SVN. END NOTE FROM LAURA 1/10/2012
   */
  // @Test
  // public void fastphoneBugTest() throws Exception {
  // String filename = AOG_FILES_DIR + "/fastphone.aog";
  //
  // scan = new DBDumpFileScan(TestConstants.ENRON_1K_DUMP);
  //
  // util.setOutputDir(OUTPUT_ROOT_DIR + "/fastphone");
  // util.setExpectedDir(EXPECTED_ROOT_DIR + "/fastphone");
  // util.cleanOutputDir();
  // util.runAOGFile(scan, filename);
  //
  // // util.setSkipFileComparison(true);
  // util.compareAgainstExpected("PhoneNumber.htm");
  // }

  /**
   * Test of a bug in the AOG compiler that was causing tee outputs to be used twice.
   */
  @Test
  public void teeBugTest() throws Exception {
    File DOCSFILE = new File(String.format("%s/teeBug.del", DOC_FILES_DIR));
    startTest();

    setDumpPlan(false);
    setDisableOutput(false);
    setPrintTups(false);

    runAOGFile(DOCSFILE, null);

    truncateOutputFiles(true);
    // truncateExpectedFiles ();
    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Test of a another tuple buffer handling bug.
   */
  @Test
  public void teeBugTest2() throws Exception {
    File DOCSFILE = new File(String.format("%s/teeBug2.del", DOC_FILES_DIR));
    startTest();

    setDumpPlan(false);
    setDisableOutput(false);
    setPrintTups(false);

    runAOGFile(DOCSFILE, null);

    truncateOutputFiles(true);
    // truncateExpectedFiles ();
    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Test of a another tuple buffer handling bug.
   */
  @Test
  public void teeBugTest3() throws Exception {
    File DOCSFILE = new File(String.format("%s/teeBug3.del", DOC_FILES_DIR));
    startTest();

    setDumpPlan(false);
    setDisableOutput(false);
    setPrintTups(false);

    runAOGFile(DOCSFILE, null);

    truncateOutputFiles(true);
    // truncateExpectedFiles ();
    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Test of a another tuple buffer handling bug.
   */
  @Test
  public void teeBugTest4() throws Exception {
    File DOCSFILE = new File(String.format("%s/teeBug4.del", DOC_FILES_DIR));
    startTest();

    setDumpPlan(false);
    setDisableOutput(false);
    setPrintTups(false);

    String[][] dictInfo =
        {{"dictionaries/strictlast.dict", "testdata/dictionaries/strictlast.dict"},
            {"dictionaries/strictfirst.dict", "testdata/dictionaries/strictfirst.dict"}};

    runAOGFile(DOCSFILE, dictInfo);

    truncateOutputFiles(true);
    // truncateExpectedFiles ();
    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Test of a bug in output schema handling with the detag statement
   */
  @Test
  public void detagBugTest() throws Exception {
    File DOCSFILE = new File(String.format("%s/detagBug.del", DOC_FILES_DIR));
    startTest();

    setDumpPlan(false);
    setDisableOutput(false);
    setPrintTups(false);

    runAOGFile(DOCSFILE, null);

    truncateOutputFiles(true);
    // truncateExpectedFiles ();
    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Test of a bug in virtual projection.
   */
  @Test
  public void projectBugTest() throws Exception {
    File DOCSFILE = new File(String.format("%s/projectBug.del", DOC_FILES_DIR));
    startTest();

    setDumpPlan(false);
    setDisableOutput(false);
    setPrintTups(true);

    // prepare list of dict file being referred in the aog
    String[][] dictInfo = {{"dictionaries/first.dict", "testdata/dictionaries/first.dict"}};

    runAOGFile(DOCSFILE, dictInfo);

    truncateOutputFiles(true);
    // truncateExpectedFiles ();
    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Test of a bug in the AdjacentJoin operator.
   */
  @Test
  public void adjacentBugTest() throws Exception {
    File DOCSFILE = new File(String.format("%s/adjacentBug.del", DOC_FILES_DIR));
    startTest();

    setDumpPlan(false);
    setDisableOutput(false);
    setPrintTups(true);

    String[][] dictInfo = {{"dictionaries/last.dict", "testdata/dictionaries/last.dict"},
        {"dictionaries/first.dict", "testdata/dictionaries/first.dict"}};

    runAOGFile(DOCSFILE, dictInfo);

    truncateOutputFiles(true);
    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Test of a bug in the code for recycling TupleList
   */
  @Test
  public void tlRecycleBugTest() throws Exception {
    File DOCSFILE = new File(String.format("%s/tlRecycleBugTest.del", DOC_FILES_DIR));
    startTest();

    setDumpPlan(false);
    setDisableOutput(false);
    setPrintTups(false);

    String[][] dictInfo = {{"dictionaries/first.dict", "testdata/dictionaries/first.dict"}};
    runAOGFile(DOCSFILE, dictInfo);

    // truncateExpectedFiles ();
    endTest();
  }

  /**
   * Test of a bug in detagging that causes incorrect offsets when spans are remapped back to the
   * original HTML. Disabled because we don't have a way to regenerate the proper AOG at the moment.
   */
  @Test
  public void remapBugTextTest() throws Exception {

    final File HTML_DOC_FILE = new File(DOC_FILES_DIR, "samplehtml.zip");
    // final File HTML_DOC_FILE = new File(TestConstants.DUMPS_DIR,
    // "html/imdbtomcruise.del");

    // Named-entity annotators over text (original test used the AOG)
    // final String TEXT_AOG_FILE = AOG_FILES_DIR + "/hadoopNE.aog";

    // We now use the AQL instead
    File AQL_FILE = new File(TestConstants.AQL_DIR,
        "ExtractorLibrary/aql/eDA/ne-ediscovery-personorgphoneaddress.aql");

    String includePath = TestConstants.AQL_DIR + "/ExtractorLibrary/aql";
    String dictPath = TestConstants.AQL_DIR + "/ExtractorLibrary/aql/core/GenericNE/dictionaries";
    String dataPath = String.format("%s%c%s", includePath, SearchPath.PATH_SEP_CHAR, dictPath);

    // First treat the HTML file as text.
    {
      startTest();

      setDumpPlan(false);
      setDisableOutput(false);
      setPrintTups(false);

      // Use the AQL instead
      // runAOGString (HTML_DOC_FILE, TEXT_AOG_FILE, null);
      setDataPath(dataPath);
      runNonModularAQLTest(HTML_DOC_FILE, AQL_FILE);

      truncateOutputFiles(true);
      // truncateExpectedFiles ();
      compareAgainstExpected(true);
      endTest();
    }

  }

  /**
   * Test of a bug in detagging that causes incorrect offsets when spans are remapped back to the
   * original HTML. Disabled because we don't have a way to regenerate the proper AOG at the moment.
   */
  @Test
  public void remapBugHtmlTest() throws Exception {

    final File HTML_DOC_FILE = new File(DOC_FILES_DIR, "samplehtml.zip");
    // final File HTML_DOC_FILE = new File (TestConstants.DUMPS_DIR, "html/imdbtomcruise.del");

    // Named-entity annotators over HTML (old test used the AOG)
    // final String REMAP_AOG_FILE = AOG_FILES_DIR + "/hadoopNE_remap.aog";

    // Named-entity annotators over HTML (we now use the AQL source code directly)
    File AQL_FILE = new File(TestConstants.AQL_DIR,
        "ExtractorLibrary/aql/hadoop/ne-ediscovery-personorgphoneaddress-htmlwithremap.aql");

    String includePath = TestConstants.AQL_DIR + "/ExtractorLibrary/aql";
    String dictPath = TestConstants.AQL_DIR + "/ExtractorLibrary/aql/core/GenericNE/dictionaries";
    String dataPath = String.format("%s%c%s", includePath, SearchPath.PATH_SEP_CHAR, dictPath);

    // Then treat the HTML file as HTML, detagging and then remapping
    // annotations over the detagged text back to the original document.
    {
      startTest();

      setDumpPlan(false);
      setDisableOutput(false);
      setPrintTups(false);

      // Run the original AQL code instead of the AOG
      // runAOGString (HTML_DOC_FILE, REMAP_AOG_FILE, null);
      setDataPath(dataPath);
      runNonModularAQLTest(HTML_DOC_FILE, AQL_FILE);

      truncateOutputFiles(true);
      // truncateExpectedFiles ();
      compareAgainstExpected(true);
      endTest();
    }

  }

  /**
   * Test case for a bug in de-escaping \ in the AOG parser. #166958 Problem with deescaping in
   * RegexLiterals
   */
  @Test
  public void regexEscapedSlashBugTest() throws Exception {
    File DOCSFILE = new File(String.format("%s/regexEscapedSlashBug.del", DOC_FILES_DIR));
    startTest();

    setDumpPlan(false);
    setDisableOutput(false);
    setPrintTups(false);

    runAOGFile(DOCSFILE, null);

    // truncateOutputFiles (true);
    // truncateExpectedFiles ();
    compareAgainstExpected(true);
    endTest();
  }

  /*
   * NEW TEST CASES GO HERE
   */

}
