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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.util.dict.CompiledDictionary;
import com.ibm.avatar.algebra.util.html.HTMLParserDetagger;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.api.CompileAQLParams;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.aql.compiler.Compiler;
import com.ibm.avatar.aql.tam.TAM;
import com.ibm.avatar.aql.tam.TAMSerializer;
import com.ibm.avatar.logging.Log;

/** Tests of the HTML/XML detagging support in SystemT. */
public class DetaggerTests extends RuntimeTestHarness {

  public static void main(String[] args) {
    try {

      DetaggerTests t = new DetaggerTests();

      t.setUp();

      long startMS = System.currentTimeMillis();

      t.detaggerTest();

      long endMS = System.currentTimeMillis();

      double elapsedSec = (endMS - startMS) / 1000.0;

      System.err.printf("Test took %1.3f sec.\n", elapsedSec);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private File defaultDocsFile;

  @Before
  public void setUp() throws Exception {
    setDataPath(TestConstants.TESTDATA_DIR);

    defaultDocsFile = new File(TestConstants.IMDB_TARFILE);

    // Renice the current thread to avoid locking up the entire system.
    Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

  }

  @After
  public void tearDown() {

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

  /** Test of the Detag operator, called via AOG. */
  @Test
  public void testHTMLDetaggerAOG() throws Exception {

    // Use an AOG file to instantiate a phone number annotator. This
    // annotator should produce the same results as the other annotators
    // in this file.
    // final String AOGFILE = "testdata/aog/imdb.aog";

    // Now use AOG and AOM to verify that we've created the proper phone
    // number annotations.
    // final String AOG = "($DetaggedDoc, $title, $name) =\n"
    final String AOG = "($DetaggedDoc) =\n" + "Detag(" +
    // "(\"title\" => \"null\"=>\"null\"=>\"title\",\n"
    // + " \"a\" =>\"href\"=>\"/name/*\"=>\"name\"\n"
    // + " \"a\" =>\"href\"=>\"mailto:.*\"=>\"name\"\n"
    // + " ), " +
        "\"text\", \"Detagged\", \"true\", $DocScan);\n" + "Output: $DetaggedDoc;";
    Log.info("AOG is:\n%s", AOG);
    // , $title, $name;";

    // String tarFileName = TestConstants.DUMPS_DIR + "/spocktgz/20071127BA01.tgz";

    // runAOGString(new File(tarFileName), AOG);
  }

  /**
   * Run the test aql files for HTMLdetagger
   * 
   * @throws Exception
   */
  @Test
  public void detaggerTest() throws Exception {

    startTest();

    // String filename = TestConstants.AQL_DIR +
    // "/lotus/namedentity-detag-spock.aql";
    String filename = TestConstants.AQL_DIR + "/lotus/namedentity-detag-imdb.aql";

    // scan = new TarFileScan(TestConstants.SPOCK_100_TARFILE);
    // DocScan scan = DocScan
    // .makeFileScan(new File(TestConstants.IMDB_TARFILE));

    setDumpPlan(true);
    setWriteCharsetInfo(true);
    runNonModularAQLTest(defaultDocsFile, filename);

    // We comment some files out due to charset issues across JVMs.
    // compareAgainstExpected("detaggedDoc.htm");
    // compareAgainstExpected("movies.htm");
    // compareAgainstExpected("names.htm");
    // compareAgainstExpected("titles.htm");
    // compareAgainstExpected("Person.htm");
    // compareAgainstExpected("Organization.htm");
    // truncateExpectedFiles();

    truncateOutputFiles(false);
    compareAgainstExpected(true);
  }

  /**
   * Test the HTMLParserDetagger API to produce correct UTF-8 encoded output for a Chinese document
   * IMP: This test should ideally be run with explicit encoding of US-ASCII which will confirm that
   * the defect fix is working as expected. RTC 134468
   * 
   * @throws Exception
   */
  @Test
  public void detagApiChineseTest() throws Exception {
    startTest();

    final String htmlDocsDir = TestConstants.TEST_DOCS_DIR + "/detaggerTests/detagApiChineseDoc/";

    for (File htmlFile : new File(htmlDocsDir).listFiles()) {
      ByteArrayOutputStream detaggedDoc =
          HTMLParserDetagger.detagFile(new FileInputStream(htmlFile), true);
      FileOutputStream out = new FileOutputStream(new File(getCurOutputDir(), htmlFile.getName()));
      out.write(detaggedDoc.toByteArray());
      out.flush();
      out.close();
    }

    compareAgainstExpected(false);

    endTest();
  }

  /**
   * Much simpler test of the AQL detag statement.
   */
  @Test
  public void simpleDetagTest() throws Exception {

    startTest();

    // String filename = TestConstants.AQL_DIR +
    // "/lotus/namedentity-detag-spock.aql";
    String filename = TestConstants.AQL_DIR + "/DetaggerTests/simple.aql";

    // scan = new TarFileScan(TestConstants.SPOCK_100_TARFILE);
    // DocScan scan = DocScan
    // .makeFileScan(new File(TestConstants.IMDB_TARFILE));

    // Compile manually so that we can have hooks into the various stages.
    CompileAQLParams compileParam =
        new CompileAQLParams(new File(filename), getCurOutputDir().toURI().toString(), null);
    compileParam.setTokenizerConfig(getTokenizerConfig());

    Compiler compiler = null;
    try {
      compiler = new Compiler();
      compiler.compile(compileParam);

      // Load tam
      TAM tam = TAMSerializer.load(Constants.GENERIC_MODULE_NAME, compileParam.getOutputURI());

      String aog = tam.getAog();

      Log.info("AQL parse tree is:");
      compiler.getCatalog().dump(System.err);
      Map<String, CompiledDictionary> allCompiledDicts = tam.getAllDicts();
      Log.info("Dicts are:\n" + allCompiledDicts.toString());

      Log.info("AOG plan is:\n" + aog);

      setWriteCharsetInfo(true);

      // Parse and the AOG spec.
      runAOGString(defaultDocsFile, aog, null);

      truncateOutputFiles(true);
      compareAgainstExpected(true);
    } finally {
      if (null != compiler)
        compiler.deleteTempDirectory();
    }
  }

  /**
   * Much simpler test of the AQL detag statement, mainly geared towards handling of line breaks and
   * &lt;BR&gt; tags.
   */
  @Test
  public void detagBrTest() throws Exception {

    startTest();

    String filename = TestConstants.AQL_DIR + "/DetaggerTests/detagBr.aql";

    // scan = new TarFileScan(TestConstants.SPOCK_100_TARFILE);
    // DocScan scan = new ZipFileScan(TestConstants.DUMPS_DIR +
    // "/streetInsider.zip");
    // DocScan scan = DocScan.makeFileScan(new File(TestConstants.DUMPS_DIR,
    // "streetinsider"));

    setDumpPlan(true);
    setWriteCharsetInfo(true);
    runNonModularAQLTest(new File(TestConstants.DUMPS_DIR, "streetinsider"), filename);

    truncateOutputFiles(true);
    compareAgainstExpected(true);
  }

  /**
   * Test of how the detagger handles HTML entity codes.
   */
  @Test
  public void entityTest() throws Exception {

    startTest();

    final String aqlFile = TestConstants.AQL_DIR + "/DetaggerTests/entityTest.aql";

    final String docsFile = TestConstants.TEST_DOCS_DIR + "/detaggerTests/entityTest.del";

    // DocScan scan = DocScan.makeFileScan(new File(docsFile));

    setPrintTups(true);
    setWriteCharsetInfo(true);
    runNonModularAQLTest(new File(docsFile), aqlFile);

    compareAgainstExpected(true);
  }

  /**
   * Test of how the detagger handles improperly terminated HTML elements
   */
  @Test
  public void unterminatedTest() throws Exception {

    startTest();

    final String aqlFile = TestConstants.AQL_DIR + "/DetaggerTests/unterminatedTest.aql";

    final String docsFile = TestConstants.TEST_DOCS_DIR + "/detaggerTests/unterminatedTest.del";

    // DocScan scan = DocScan.makeFileScan(new File(docsFile));

    setPrintTups(true);
    setWriteCharsetInfo(true);
    runNonModularAQLTest(new File(docsFile), aqlFile);

    compareAgainstExpected(true);
  }

  /**
   * Test of how the detagger handles
   */
  @Test
  public void unterminatedTest1() throws Exception {

    startTest();

    final String aqlFile = TestConstants.AQL_DIR + "/DetaggerTests/unterminatedTest1.aql";

    final String docsFile = TestConstants.TEST_DOCS_DIR + "/detaggerTests/unterminatedTest1.del";

    // DocScan scan = DocScan.makeFileScan(new File(docsFile));

    setPrintTups(true);
    setWriteCharsetInfo(true);

    runNonModularAQLTest(new File(docsFile), aqlFile);

    truncateOutputFiles(true);

    compareAgainstExpected(true);
  }

  /**
   * Test of how the detagger handles improperly terminated script tags
   */
  @Test
  public void unterminatedScriptTagTest() throws Exception {

    startTest();

    final String aqlFile = TestConstants.AQL_DIR + "/DetaggerTests/unterminatedScriptTagTest.aql";

    final String docsFile =
        TestConstants.TEST_DOCS_DIR + "/detaggerTests/unterminatedScriptTagTest.del";

    // DocScan scan = DocScan.makeFileScan(new File(docsFile));

    setPrintTups(true);
    setWriteCharsetInfo(true);

    runNonModularAQLTest(new File(docsFile), aqlFile);

    truncateExpectedFiles();

    compareAgainstExpected(true);
  }

  /**
   * Test of how the detagger handles improperly terminated script tags
   */
  @Test
  public void unterminatedScriptTagTest1() throws Exception {

    startTest();

    final String aqlFile = TestConstants.AQL_DIR + "/DetaggerTests/unterminatedScriptTagTest1.aql";

    final String docsFile =
        TestConstants.TEST_DOCS_DIR + "/detaggerTests/unterminatedScriptTagTest1.del";

    // DocScan scan = DocScan.makeFileScan(new File(docsFile));

    setPrintTups(true);
    setWriteCharsetInfo(true);
    runNonModularAQLTest(new File(docsFile), aqlFile);

    truncateExpectedFiles();

    compareAgainstExpected(true);
  }

  /**
   * Test of how the remap function handles empty spans with empty detagged documents. Reproduces
   * bug #152805: Remap() exception with empty detagged documents Commented out since test fails.
   */
  @Test
  public void emptyTagRemapTest() throws Exception {

    startTest();

    final String aqlFile = TestConstants.AQL_DIR + "/DetaggerTests/emptyTagRemapTest.aql";

    final String docsFile = TestConstants.TEST_DOCS_DIR + "/detaggerTests/emptyTagRemapTest.del";

    // DocScan scan = DocScan.makeFileScan(new File(docsFile));

    setPrintTups(true);
    setWriteCharsetInfo(true);

    runNonModularAQLTest(new File(docsFile), aqlFile);

    truncateExpectedFiles();

    compareAgainstExpected(true);
  }

  /**
   * Test of how the detagger handles XML headers. When the IBM version of the HTMLParser is used,
   * this test case reproduces bug [#161892] Detagger passes through XML headers when used with IBM
   * version of HTMLParser.
   */
  @Test
  public void spuriousXmlHeaderTest() throws Exception {

    startTest();

    final String aqlFile = TestConstants.AQL_DIR + "/DetaggerTests/simple.aql";

    final String docsFile =
        TestConstants.TEST_DOCS_DIR + "/detaggerTests/spuriousXmlHeaderTest.del";

    // DocScan scan = DocScan.makeFileScan(new File(docsFile));

    setPrintTups(true);
    setWriteCharsetInfo(true);

    runNonModularAQLTest(new File(docsFile), aqlFile);

    truncateExpectedFiles();

    compareAgainstExpected(true);
  }

  /**
   * Test of how the detagger handles encoded entities
   */
  @Test
  public void encodedCharsTest() throws Exception {

    startTest();

    final String aqlFile = TestConstants.AQL_DIR + "/DetaggerTests/encodedCharsTest.aql";

    final String docsFile = TestConstants.TEST_DOCS_DIR + "/detaggerTests/encodedCharsTest.del";

    // DocScan scan = DocScan.makeFileScan(new File(docsFile));

    setPrintTups(true);
    setWriteCharsetInfo(true);

    runNonModularAQLTest(new File(docsFile), aqlFile);

    truncateExpectedFiles();

    compareAgainstExpected(true);
  }

  /**
   * Test on how remap function handles empty html document or html document which is detagged to
   * empty string
   * 
   * @throws Exception
   */
  @Test
  public void remapEmptyDocumentTest() throws Exception {

    startTest();

    final String aqlFile = TestConstants.AQL_DIR + "/DetaggerTests/remapEmptyDocumentTest.aql";

    final String docsFile =
        TestConstants.TEST_DOCS_DIR + "/detaggerTests/remapEmptyDocumentTest.del";

    // DocScan scan = DocScan.makeFileScan(new File(docsFile));

    // try {
    // util.runAQLFile(scan, aqlFile);
    // }catch( Exception e ){
    // Assert.fail("Following exception during remap : " + e.getMessage());
    // }
    //
    // Assert.assertTrue("If here then no exception", true);

    setPrintTups(true);

    setWriteCharsetInfo(true);

    runNonModularAQLTest(new File(docsFile), aqlFile);

    truncateExpectedFiles();

    compareAgainstExpected(true);
  }

  /**
   * Scenario: Detag a local view (view declaration and detag statement in the same module) by
   * referring the view (local) through the unqualified name. This test case covers the scenario
   * mentioned in defect#36710.
   * 
   * @throws Exception
   */
  @Test
  public void detagLocalViewTest1() throws Exception {
    startTest();
    setPrintTups(true);

    compileAndRunModule("detagLocalViewTest1", defaultDocsFile, null);

    compareAgainstExpected(true);

    endTest();
  }

  /**
   * Scenario: Detag a local view (view declaration and detag statement in the same module) by
   * referring the view (local) through the qualified name. This test case covers the scenario
   * mentioned in defect#36710.
   * 
   * @throws Exception
   */
  @Test
  public void detagLocalViewTest2() throws Exception {
    startTest();
    setPrintTups(true);

    compileAndRunModule("detagLocalViewTest2", defaultDocsFile, null);

    compareAgainstExpected(true);

    endTest();
  }

  /**
   * Scenario: Detag an imported view by referring the imported view through the qualified name.
   * This test case covers the scenario mentioned in defect#36710.
   * 
   * @throws Exception
   */
  @Test
  public void detagImportedViewTest1() throws Exception {
    startTest();
    setPrintTups(true);

    compileAndRunModules(new String[] {"viewToDetag", "detagImportedViewTest1"}, defaultDocsFile, null);

    compareAgainstExpected(true);

    endTest();
  }

  /**
   * Scenario: Detag an imported view by referring the imported view through the declared alias..
   * This test case covers the scenario mentioned in defect#36710.
   * 
   * @throws Exception
   */
  @Test
  public void detagImportedViewTest2() throws Exception {
    startTest();
    setPrintTups(true);

    compileAndRunModules(new String[] {"viewToDetag", "detagImportedViewTest2"}, defaultDocsFile, null);

    compareAgainstExpected(true);

    endTest();
  }

  /**
   * Test for defect : Detagger extracts null value for attributes whose name is equal to the tag
   * name
   */
  @Test
  public void missingAttrTest() throws Exception {

    startTest();

    final String aqlFile = TestConstants.AQL_DIR + "/DetaggerTests/missingAttrTest.aql";

    final String docsFile = TestConstants.TEST_DOCS_DIR + "/detaggerTests/missingAttrTest.del";

    setPrintTups(true);
    setWriteCharsetInfo(true);

    runNonModularAQLTest(new File(docsFile), aqlFile);

    truncateExpectedFiles();

    compareAgainstExpected(true);
  }

  /**
   * Detags an HTML/XML file, without generating annotations, and returns the result. Used to
   * manually verify the expected output of the
   * {@link com.ibm.avatar.api.DocReader#detagFile(FileInputStream)} API.
   */
  // @Test
  public void basicDetagTest() throws Exception {

    startTest();

    final String aqlFile = TestConstants.AQL_DIR + "/DetaggerTests/basicDetagTest.aql";

    final String docsFile =
        TestConstants.TEST_DOCS_DIR + "/detaggerTests/detagApiDocs/mixedNewLines.txt";

    setPrintTups(true);
    setWriteCharsetInfo(true);
    runNonModularAQLTest(new File(docsFile), aqlFile);

  }

  /**
   * Test for Task 75763: Runs the {@link com.ibm.avatar.api.DocReader#detagFile(FileInputStream)}
   * API on all files within a directory and compares their results with the gold standard to ensure
   * that the detagging is working properly.
   */
  @Test
  public void detagApiTest() throws Exception {
    startTest();

    final String htmlDocsDir = TestConstants.TEST_DOCS_DIR + "/detaggerTests/detagApiDocs/";

    for (File htmlFile : new File(htmlDocsDir).listFiles()) {
      ByteArrayOutputStream detaggedDoc =
          HTMLParserDetagger.detagFile(new FileInputStream(htmlFile), true);
      FileOutputStream out = new FileOutputStream(new File(getCurOutputDir(), htmlFile.getName()));
      out.write(detaggedDoc.toString().getBytes());
      out.flush();
      out.close();
    }

    compareAgainstExpected(false);

    endTest();

  }

  /**
   * Test scenario: When an input document contains an empty tag (for ex. <title> </title> tag is
   * used in this particular scenario), if the empty tag is annotated via detag statement using the
   * annotate clause, the detagged output or any other views written on top of the detagged output
   * should have identical span offsets in the output regardless of whether the annotated empty tag
   * view is used towards an output view.</br>
   * </br>
   * The bug was due to an extra white space character that was getting added to account for ANY
   * such empty tags ONLY WHEN such tags were annotated via the detag statement and being used
   * towards an output view. Thus this led to span offsets being off by 1 for every empty tag that
   * was used towards an output view vs. not used towards an output view. </br>
   * The fix removed this addition of whitespace (as we now map such empty tags to an empty span
   * instead of a whitespace) thus ensuring that the offsets are consistent when a) the annotated
   * empty tag gets used towards any output view b) the annotated empty tag is not used towards an
   * output view.
   * 
   * @throws Exception
   */
  @Test
  public void emptyTitleTagTest() throws Exception {
    startTest();
    setPrintTups(true);

    final String docsFile = TestConstants.TEST_DOCS_DIR + "/detaggerTests/emptyTitleTagTest.html";

    compileAndRunModules(new String[] {"emptyTitleTagTest"}, new File(docsFile), null);

    compareAgainstExpected(true);

    endTest();
  }

  /**
   * Assert that our detagging capability recognizes valid representations of the non-breaking space
   * character found in input HTML documents: <br>
   * <ul>
   * <li>Either as a HTML entity such as <code>&amp;nbsp;</code> or <code>&amp;#160;</code> or
   * <code>&amp;#0160;</code> or,</li>
   * <li>as the character whose Unicode codepoint is <code>U+00A0</code>
   * </ul>
   * <br>
   * 
   * @throws Exception
   */
  @Test
  public void detagNoBreakSpaceCharTest() throws Exception {
    startTest();
    final String aqlFile = TestConstants.AQL_DIR + "/DetaggerTests/detagNoBreakSpaceCharTest.aql";
    final String docsFile =
        TestConstants.TEST_DOCS_DIR + "/detaggerTests/detagNoBreakSpaceCharTest.html";
    setPrintTups(true);
    setWriteCharsetInfo(true);
    runNonModularAQLTest(new File(docsFile), aqlFile);
    compareAgainstExpected(true);
  }

  /**
   * Assert that upon the insertion of an artificial whitespace, all corresponding offsets of tags
   * processed until then are accordingly updated to exclude the whitespace from being included as
   * part of a given tag's value; thereby asserting resolution of SystemT-23.
   * 
   * @throws Exception
   */
  @Test
  public void updateOffsetsForArtificialWhitespaceTest() throws Exception {
    startTest();
    final String aqlFile =
        TestConstants.AQL_DIR + "/DetaggerTests/updateOffsetsForArtificialWhitespaceTest.aql";
    final String docsFile = TestConstants.TEST_DOCS_DIR
        + "/detaggerTests/updateOffsetsForArtificialWhitespaceTest.html";
    setPrintTups(true);
    setWriteCharsetInfo(true);
    runNonModularAQLTest(new File(docsFile), aqlFile);
    compareAgainstExpected(true);
  }
}
