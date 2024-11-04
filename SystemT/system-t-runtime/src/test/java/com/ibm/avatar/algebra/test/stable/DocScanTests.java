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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.util.lang.LangCode;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.DocReader;
import com.ibm.avatar.api.DocReaderInternal;
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.api.exceptions.ExceptionWithView;
import com.ibm.avatar.api.exceptions.FatalRuntimeError;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.tam.ModuleUtils;
import com.ibm.avatar.logging.Log;

/** Various tests of the different implementations of the DocScan operator. */
public class DocScanTests extends RuntimeTestHarness {
  // uncomment this out when we upgrade to JUnit 4.7+
  // @Rule
  // public ExpectedException thrown = ExpectedException.none ();

  // directory containing AQL used for this docscan
  public static final String AQL_FILES_DIR = TestConstants.AQL_DIR + "/DocScanTests";

  /** Bigger dump file that we use if a copy exists. */
  public static final String bigDumpfileName = "testdata/blogspot50k.del";

  public static void main(String[] args) throws Exception {
    DocScanTests test = new DocScanTests();

    long startMs = System.currentTimeMillis();
    {
      test.setUp();

      test.eViewSpanTest();

      test.tearDown();
    }

    long elapsedMs = System.currentTimeMillis() - startMs;

    System.err.printf("Test took %1.1f sec\n", ((elapsedMs)) / 1000.0);
  }

  @Before
  public void setUp() throws Exception {

    // Renice the current thread to avoid locking up the entire system.
    Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

  }

  @After
  public void tearDown() throws Exception {}

  /**
   * See how fast a document scan can go. To avoid perturbing the running times, this test does not
   * do much result verification.
   */
  @Test
  public void benchmarkDBDumpScan() throws Exception {

    // Try to use the big dump if possible.
    String fileToUse;

    File bigdump = new File(bigDumpfileName);
    if (bigdump.exists()) {
      fileToUse = bigDumpfileName;
    } else {
      fileToUse = TestConstants.ENRON_10K_DUMP;
    }

    // Create the operator.
    DocReader reader = new DocReader(new File(fileToUse));

    int nread = 0;
    while (reader.hasNext()) {
      nread++;
      Tuple out = reader.next();

      Assert.assertEquals(1, out.size());

      if (0 == nread % 10000) {
        System.err.print("Read " + nread + " documents\n");
      }
    }

    // Close the document reader
    reader.remove();
    System.err.print("Read " + nread + " documents\n");

    // Make sure we read the correct number of documents.
    if (bigDumpfileName.equals(fileToUse)) {
      Assert.assertEquals(50000, nread);
    } else {
      Assert.assertEquals(10001, nread);
    }
  }

  /**
   * Test to ensure that the reader behaves properly on empty input.
   */
  @Test
  public void emptyInputTest() throws Exception {
    final String INFILE_NAME = TestConstants.TEST_DOCS_DIR + "/empty.del";

    DocReader reader = new DocReader(new File(INFILE_NAME));

    int ndoc = 0;
    while (reader.hasNext()) {
      reader.next();
      ndoc++;
    }

    Assert.assertEquals(0, ndoc);

    // Close the document reader
    reader.remove();
  }

  /**
   * Test of the TarFileScan operator.
   * 
   * @throws Exception
   */
  @Test
  public void tarScanTest() throws Exception {
    startTest();

    final String OUTFILE_NAME = "tarScan.out";

    DocReader reader = new DocReader(new File(TestConstants.SPOCK_TARFILE));

    // We'll generate a dump of the document tuples for comparison against
    // the "gold standard" later on.
    OutputStreamWriter out = new java.io.OutputStreamWriter(
        new FileOutputStream(new File(getCurOutputDir(), OUTFILE_NAME)), "UTF-8");

    while (reader.hasNext()) {
      Tuple doc = reader.next();

      out.append(doc.toString() + "\n");

      // System.err.printf("Got doc: %s\n", doc);
    }

    out.close();

    // Close the document reader
    reader.remove();

    // Disable this check because we get subtly different results on
    // different JVMs.
    // compareAgainstExpected(OUTFILE_NAME);
    endTest();
  }

  /**
   * Test of the ZipScan operator.
   * 
   * @throws Exception
   */
  @Test
  public void zipScanTest() throws Exception {
    startTest();

    final String OUTFILE_NAME = "zipScan.out";

    DocReader reader = new DocReader(new File(TestConstants.ENRON_SAMPLE_ZIP));

    // We'll generate a dump of the document tuples for comparison against
    // the "gold standard" later on.
    OutputStreamWriter out = new java.io.OutputStreamWriter(
        new FileOutputStream(new File(getCurOutputDir(), OUTFILE_NAME)), "UTF-8");

    while (reader.hasNext()) {
      Tuple doc = reader.next();

      out.append(doc.toString() + "\n");

      // System.err.printf("Got doc: %s\n", doc);
    }

    out.close();

    // Close the document reader
    reader.remove();

    // Disable this check because we get subtly different results on
    // different JVMs.
    // compareAgainstExpected(OUTFILE_NAME);

    endTest();
  }

  /**
   * Test of the ZipScan operator, invoked via OperatorGraphRunner.setFileInput().
   * 
   * @throws Exception
   */
  @Test
  public void zipScanTest2() throws Exception {

    startTest();

    // Compile a "pass-through" AQL statement; we just want to see if the
    // setup goes through.

    // String aql = "select D.text as text into Out from Document D;\n";
    String aog =
        "$Document = DocScan(\n( \"text\" => \"Text\", \"label\" => \"Text\")\n); Output: $Document;\n";

    setDisableOutput(true);
    runAOGString(new File(TestConstants.ENRON_SAMPLE_ZIP), aog, null);
    // AQLRunner runner = AQLRunner.compileStr ("select D.text as text into Out from DocScan D;\n");
    // runner.setFileInput (new File (TestConstants.ENRON_SAMPLE_ZIP));
    // runner.setNoOutput ();
    // runner.run ();
  }

  /**
   * Test of the DirDocScan operator, invoked via OperatorGraphRunner.setFileInput().
   * 
   * @throws Exception
   */
  @Test
  public void dirDocScanTest() throws Exception {
    startTest();

    final String DOCS_DIR = TestConstants.TESTDATA_DIR + "/docs/DocScanTests/dirDocScan";

    String aog =
        "$Document = DocScan(\n( \"text\" => \"Text\", \"label\" => \"Text\")\n); Output: $Document;\n";

    runAOGString(new File(DOCS_DIR), aog, null);

  }

  /**
   * Test of the DirDocScan operator when the specified dir does not have any file but has only
   * sub-directories. Test is invoked via OperatorGraphRunner.setFileInput().
   * 
   * @throws Exception
   */
  @Test
  public void dirDocScanMissingDirsTest() throws Exception {
    startTest();

    final String DOCS_DIR = TestConstants.TESTDATA_DIR + "/docs/DocScanTests/dirDocScanMissingDirs";

    String aog =
        "$Document = DocScan(\n( \"text\" => \"Text\", \"label\" => \"Text\")\n); Output: $Document;\n";

    runAOGString(new File(DOCS_DIR), aog, null);

    // AQLRunner runner = AQLRunner.compileStr ("select D.text as text into Out from DocScan D;\n");
    // runner.setFileInput (new File (DOCS_DIR));
    // runner.setHTMLOutput (getCurOutputDir ());
    // runner.run ();
  }

  /**
   * Test of the DirDocScan operator when the sub directory is empty. Test is invoked via
   * OperatorGraphRunner.setFileInput().
   * 
   * @throws Exception
   */
  @Test
  public void dirDocScanEmptySubDirTest() throws Exception {
    startTest();

    final String DOCS_DIR = TestConstants.TESTDATA_DIR + "/docs/DocScanTests/dirDocScanEmptySubDir";

    String aog =
        "$Document = DocScan(\n( \"text\" => \"Text\", \"label\" => \"Text\")\n); Output: $Document;\n";

    runAOGString(new File(DOCS_DIR), aog, null);

    // AQLRunner runner = AQLRunner.compileStr ("select D.text as text into Out from DocScan D;\n");
    // runner.setFileInput (new File (DOCS_DIR));
    // runner.setHTMLOutput (getCurOutputDir ());
    // runner.run ();
  }

  /**
   * Verifies that DocScan can process single files
   * 
   * @throws Exception
   */
  @Test
  public void textScanTest() throws Exception {
    final String TEXT_FILE =
        TestConstants.TESTDATA_DIR + "/docs/DocScanTests/textFileScan/doc_1.txt";
    DocReader reader = new DocReader(new File(TEXT_FILE));
    Tuple doc = reader.next();

    Assert.assertEquals("Number of attributes should be 2.", 2, doc.size());
    System.err.printf("CSV is: %s\n", doc.toCSVString());
    Assert.assertEquals("\"'doc_1.txt'\",\"'Document 1.'\"", doc.toCSVString());

    // Close the document reader
    reader.remove();
  }

  /**
   * Verifies that DocScan can process text files with either carriage return policy
   * 
   * @throws Exception
   */
  @Test
  public void stripCrTextFileTest() throws Exception {
    final String TEST_DIR = TestConstants.TESTDATA_DIR + "/docs/DocScanTests/textFileScan/";
    final String DOS_FILE = "doc_dos.txt";
    final String UNIX_FILE = "doc_unix.txt";

    genericStripCrTest(TEST_DIR, DOS_FILE, UNIX_FILE, null);
  }

  /**
   * Verifies that DocScan can process text files with either carriage return policy
   * 
   * @throws Exception
   */
  @Test
  public void stripCrDelFileTest() throws Exception {
    final String TEST_DIR = TestConstants.TESTDATA_DIR + "/docs/DocScanTests/textFileScan/";
    final String DOS_FILE = "doc_dos.del";
    final String UNIX_FILE = "doc_unix.del";

    genericStripCrTest(TEST_DIR, DOS_FILE, UNIX_FILE, null);
  }

  /**
   * Verifies that DocScan can process text files in a directory with either carriage return policy
   * 
   * @throws Exception
   */
  @Test
  public void stripCrDirTest() throws Exception {
    final String TEST_DIR = TestConstants.TESTDATA_DIR + "/docs/DocScanTests/textFileScan/";
    final String DOS_FILE = "dos_files/";
    final String UNIX_FILE = "unix_files/";

    genericStripCrTest(TEST_DIR, DOS_FILE, UNIX_FILE, null);
  }

  /**
   * Verifies that DocScan can process text files in a zip with either carriage return policy
   * 
   * @throws Exception
   */
  @Test
  public void stripCrZipTest() throws Exception {
    final String TEST_DIR = TestConstants.TESTDATA_DIR + "/docs/DocScanTests/zipFileScan/";
    final String DOS_FILE = "dos_files.zip";
    final String UNIX_FILE = "unix_files.zip";

    genericStripCrTest(TEST_DIR, DOS_FILE, UNIX_FILE, null);
  }

  /**
   * Verifies that DocScan can process text files in a zip with either carriage return policy
   * 
   * @throws Exception
   */
  @Test
  public void stripCrTarTest() throws Exception {
    final String TEST_DIR = TestConstants.TESTDATA_DIR + "/docs/DocScanTests/tarFileScan/";
    final String DOS_FILE = "dos_files.tar";
    final String UNIX_FILE = "unix_files.tar";

    genericStripCrTest(TEST_DIR, DOS_FILE, UNIX_FILE, null);
  }

  /**
   * Verifies that DocScan can process CSV files with either carriage return policy
   * 
   * @throws Exception
   */
  @Test
  public void stripCrCsvFileTest() throws Exception {
    final String TEST_DIR = TestConstants.TESTDATA_DIR + "/docs/DocScanTests/csvTests/";
    final String DOS_FILE = "doc_dos.csv";
    final String UNIX_FILE = "doc_unix.csv";

    // set up the expected doc schema
    String[] textNames = new String[] {"text"};

    TupleSchema docSchema =
        makeDocSchema(Constants.DEFAULT_DOC_TYPE_NAME, new String[0], new FieldType[0], textNames);

    genericStripCrTest(TEST_DIR, DOS_FILE, UNIX_FILE, docSchema);

  }

  /**
   * Verifies that DocScan can process JSON files with either carriage return policy
   * 
   * @throws Exception
   */
  @Test
  public void stripCrJsonFileTest() throws Exception {

    final String TEST_DIR = TestConstants.TESTDATA_DIR + "/docs/DocScanTests/jsonTests/";

    final String DOS_FILE = "doc_dos.json";
    final String UNIX_FILE = "doc_unix.json";

    // set up the expected doc schema
    String[] textNames = new String[] {"text"};

    TupleSchema docSchema =
        makeDocSchema(Constants.DEFAULT_DOC_TYPE_NAME, new String[0], new FieldType[0], textNames);

    genericStripCrTest(TEST_DIR, DOS_FILE, UNIX_FILE, docSchema);
  }

  /**
   * Verifies that DocScan can process files with either carriage return policy
   * 
   * @throws Exception
   */
  public void genericStripCrTest(String testDir, String dosFilename, String unixFilename,
      TupleSchema docSchema) throws Exception {
    // Process the same file, with both UNIX and DOS-style carriage returns.
    DocReaderInternal reader =
        new DocReaderInternal(new File(testDir + dosFilename), docSchema, null);
    reader.stripCR(true);

    Tuple doc;
    while (reader.hasNext()) {
      doc = reader.next();
      Assert.assertFalse(doc.toCSVString().contains("Document 1.\\r"));
    }

    reader = new DocReaderInternal(new File(testDir + unixFilename), docSchema, null);
    reader.stripCR(false);

    while (reader.hasNext()) {
      doc = reader.next();
      Assert.assertFalse(doc.toCSVString().contains("Document 1.\\r"));
    }

    // Close the document reader
    reader.remove();
  }

  /**
   * Verifies if DocScan.makeFileScan() throws proper exception when a non existent file is read
   */
  @Test
  public void textScanNegativeTest() {
    final String TEXT_FILE =
        TestConstants.TESTDATA_DIR + "/docs/DocScanTests/textFileScan/doc_not_exist.txt";
    try {
      new DocReader(new File(TEXT_FILE));
      Assert.fail("Expected FileNotFoundException");
    } catch (Exception e) {
      Assert.assertTrue("Expected FileNotFoundException",
          e.getCause() instanceof FileNotFoundException);
    }
  }

  /**
   * Basic JSON reader test, read a file with multiple document tuples and external views and print
   * 
   * @throws Exception
   */
  @Test
  public void jsonScanTest() throws Exception {
    {
      String jsonFileString =
          TestConstants.TEST_DOCS_DIR + "/DocScanTests/jsonTests/jsonScanTest.json";

      // set up the expected doc schema
      // String[] nontextNames = new String[] { "id", "fame", "murphylaw" };
      // FieldType[] nontextTypes = new FieldType[] { FieldType.INT_TYPE, FieldType.FLOAT_TYPE,
      // FieldType.BOOL_TYPE };
      // String[] textNames = new String[] { "text" };
      //
      // TupleSchema docSchema = makeDocSchema (Constants.DEFAULT_DOC_TYPE_NAME, nontextNames,
      // nontextTypes,
      // textNames);

      TupleSchema docSchema =
          new TupleSchema(new String[] {"id", "fame", "murphylaw", "text"}, new FieldType[] {
              FieldType.INT_TYPE, FieldType.FLOAT_TYPE, FieldType.BOOL_TYPE, FieldType.TEXT_TYPE});
      docSchema.setName(Constants.DEFAULT_DOC_TYPE_NAME);

      // set up the expected external view schemas
      // Map<String, TupleSchema> extViewSchemas = new HashMap<String, TupleSchema> ();
      Map<Pair<String, String>, TupleSchema> extViewSchemas = null;

      // print all the doc and external view tuples
      Iterator<Pair<Tuple, Map<String, TupleList>>> itr =
          DocReader.makeDocandExternalPairsItr(jsonFileString, docSchema, extViewSchemas);

      printTuples(itr);

      // Close the document reader
      itr.remove();
    }
  }

  /**
   * Read a JSON file with multiple document and ext view tuples, annotate, and print
   * 
   * @throws Exception
   */
  @Test
  public void annotateFromJsonTest() throws Exception {

    startTest();

    setPrintTups(true);

    String jsonFileString =
        TestConstants.TEST_DOCS_DIR + "/DocScanTests/jsonTests/jsonExtViewScanTest.json";

    runNonModularAQLTest(new File(jsonFileString));

    // these outputs are not very large, so no need for truncation
    compareAgainstExpected(false);
  }

  /**
   * Handle GetString(), GetText(), and Chomp() with null input correctly -- using JSON to pass null
   */
  @Test
  public void nullScalarsTest() throws Exception {
    String jsonFileString =
        TestConstants.TEST_DOCS_DIR + "/DocScanTests/jsonTests/nullGetStringTest.json";

    startTest();
    setPrintTups(true);

    runNonModularAQLTest(new File(jsonFileString));

    // these outputs are not very large, so no need for truncation
    compareAgainstExpected(false);
  }

  /**
   * Handle an incorrectly formatted external view entry in JSON file correctly.
   * 
   * @throws Exception
   */
  @Test
  public void badFormatTest() throws Exception {

    startTest();

    setPrintTups(true);

    String jsonFileString =
        TestConstants.TEST_DOCS_DIR + "/DocScanTests/jsonTests/badFormatTest.json";

    try {
      runNonModularAQLTest(new File(jsonFileString));
      Assert.fail("Should have thrown a ExceptionWithView.");
    } catch (ExceptionWithView e) {
      e.printStackTrace();
      // Top level exception is ExceptionWithView in view Document. The cause of this exception
      // should contain the
      // expected string.
      // String msg = e.getMessage ();
      Assert.assertTrue(null != e.getCause());
      String msg = e.getCause().getMessage();
      Assert.assertTrue(null != msg);
      Assert.assertTrue(msg.contains(
          "line 1:  JSON record at this line does not contain an attribute for the required field named 'fromAddress'. Provide a non-null value of type 'Text' for this required field."));
    }

  }

  /**
   * Replacement for the old TupleSchema.makeDocSchema() method, so that old code in this test
   * harness can work with minimal changes.
   * 
   * @param typename name of the new schema to create
   * @param nonTextNames names of columns in the new schema that are not of type Text
   * @param nonTextTypes types of columns
   * @param textNames names of text columns in the schema to be returned
   * @return a new schema with the same columns in the same order that the old method returned
   */
  public static TupleSchema makeDocSchema(String typename, String[] nonTextNames,
      FieldType[] nonTextTypes, String[] textNames) {

    // guard against NPE
    if (nonTextNames == null) {
      nonTextNames = new String[0];
    }

    if (nonTextTypes == null) {
      nonTextTypes = new FieldType[0];
    }

    if (textNames == null) {
      textNames = new String[0];
    }

    String[] colNames = new String[nonTextNames.length + textNames.length];
    FieldType[] colTypes = new FieldType[colNames.length];

    for (int i = 0; i < nonTextNames.length; i++) {
      colNames[i] = nonTextNames[i];
      colTypes[i] = nonTextTypes[i];
    }

    for (int i = 0; i < textNames.length; i++) {
      colNames[i + nonTextNames.length] = textNames[i];
      colTypes[i + nonTextNames.length] = FieldType.TEXT_TYPE;
    }

    TupleSchema ret = ModuleUtils.createSortedDocSchema(new TupleSchema(colNames, colTypes));
    ret.setName(typename);
    return ret;
  }

  /**
   * Testing whether DocReader.size() runs correctly with a JSON file (normal and empty)
   * 
   * @throws Exception
   */
  @Test
  public void countJsonDocsTest() throws Exception {
    // count the number of documents in a normal JSON file
    String jsonFileString =
        TestConstants.TEST_DOCS_DIR + "/DocScanTests/jsonTests/jsonScanTest.json";

    // set up the expected doc schema
    String[] nontextNames = new String[] {"id", "fame", "murphylaw"};
    FieldType[] nontextTypes =
        new FieldType[] {FieldType.INT_TYPE, FieldType.FLOAT_TYPE, FieldType.BOOL_TYPE};
    String[] textNames = new String[] {"text"};

    TupleSchema docSchema =
        makeDocSchema(Constants.DEFAULT_DOC_TYPE_NAME, nontextNames, nontextTypes, textNames);

    DocReader reader = new DocReader(new File(jsonFileString), docSchema, null);

    int numDocs = reader.size();

    Log.info("There are " + numDocs + " documents in file " + jsonFileString);
    Assert.assertTrue(numDocs == 14);
    // Close the document reader
    reader.remove();

    // count the number of docs in an empty file
    String emptyJson = TestConstants.TEST_DOCS_DIR + "/DocScanTests/jsonTests/empty.json";

    reader = new DocReader(new File(emptyJson), docSchema, null);

    numDocs = reader.size();

    Log.info("There are " + numDocs + " documents in file " + emptyJson);
    Assert.assertTrue(numDocs == 0);
    // Close the document reader
    reader.remove();

    // count the number of docs in a file with external views
    String extViewJson = TestConstants.TEST_DOCS_DIR + "/DocScanTests/jsonTests/sameFieldTest.json";

    // set up the expected external view schemas
    Map<Pair<String, String>, TupleSchema> extViewSchemas =
        new HashMap<Pair<String, String>, TupleSchema>();

    nontextNames = new String[] {"msgid"};
    nontextTypes = new FieldType[] {FieldType.INT_TYPE};
    textNames = new String[] {"fromAddress", "toAddress"};

    Pair<String, String> extViewName =
        new Pair<String, String>("EmailMetadata", "EmailMetadataSrc");

    TupleSchema extViewSchema1 =
        makeDocSchema(extViewName.first, nontextNames, nontextTypes, textNames);
    extViewSchemas.put(extViewName, extViewSchema1);

    nontextNames = new String[] {"msgid"};
    nontextTypes = new FieldType[] {FieldType.INT_TYPE};
    textNames = new String[] {"fromAddress"};

    Pair<String, String> extViewName2 = new Pair<String, String>("Spam", "SpamSrc");

    TupleSchema extViewSchema2 =
        makeDocSchema(extViewName2.first, nontextNames, nontextTypes, textNames);
    extViewSchemas.put(extViewName2, extViewSchema2);

    reader = new DocReader(new File(extViewJson), docSchema, extViewSchemas);

    numDocs = reader.size();

    Log.info("There are " + numDocs + " documents in file " + extViewJson);
    Assert.assertTrue(numDocs == 1);
    // Close the document reader
    reader.remove();

    // count the number of docs in another file with external views
    String extViewJson2 =
        TestConstants.TEST_DOCS_DIR + "/DocScanTests/jsonTests/jsonExtViewScanTest.json";

    // set up the expected external view schemas
    Map<Pair<String, String>, TupleSchema> extViewNameVsSchema =
        new HashMap<Pair<String, String>, TupleSchema>();

    // set up the expected doc schema
    nontextNames = new String[] {"id"};
    nontextTypes = new FieldType[] {FieldType.INT_TYPE};
    textNames = new String[] {"label", "text", "URL", "timeStamp"};

    docSchema =
        makeDocSchema(Constants.DEFAULT_DOC_TYPE_NAME, nontextNames, nontextTypes, textNames);

    // set up the external view schemas
    nontextNames = new String[] {"msgid"};
    nontextTypes = new FieldType[] {FieldType.INT_TYPE};
    textNames = new String[] {"fromAddress", "toAddress"};

    Pair<String, String> extViewName1 =
        new Pair<String, String>("EmailMetadata", "EmailMetadataSrc");

    extViewSchema1 = makeDocSchema(extViewName1.first, nontextNames, nontextTypes, textNames);
    extViewNameVsSchema.put(extViewName1, extViewSchema1);

    reader = new DocReader(new File(extViewJson2), docSchema, extViewNameVsSchema);

    numDocs = reader.size();

    Log.info("There are " + numDocs + " documents in file " + extViewJson2);
    Assert.assertTrue(numDocs == 4);
    // Close the document reader
    reader.remove();

  }

  /**
   * Testing the new custom accessors in the DocReader API
   * 
   * @throws Exception
   */
  @Test
  public void accessJsonFieldsTest() throws Exception {
    {

      // count the number of documents in a normal JSON file
      String jsonFileString =
          TestConstants.TEST_DOCS_DIR + "/DocScanTests/jsonTests/jsonScanTest.json";

      // set up the expected doc schema
      String[] nontextNames = new String[] {"id", "fame", "murphylaw"};
      FieldType[] nontextTypes =
          new FieldType[] {FieldType.INT_TYPE, FieldType.FLOAT_TYPE, FieldType.BOOL_TYPE};
      String[] textNames = new String[] {"text"};

      TupleSchema docSchema =
          makeDocSchema(Constants.DEFAULT_DOC_TYPE_NAME, nontextNames, nontextTypes, textNames);

      // set up the expected external view schemas
      Map<Pair<String, String>, TupleSchema> extViewSchemas = null;

      DocReader reader = new DocReader(new File(jsonFileString), docSchema, extViewSchemas);

      FieldGetter<Integer> idgetter = reader.getDocSchema().intAcc("id");

      // iterate through the doc and external view tuples and print just the id field
      Iterator<Pair<Tuple, Map<String, TupleList>>> itr =
          DocReader.makeDocandExternalPairsItr(jsonFileString, docSchema, extViewSchemas);

      while (itr.hasNext()) {
        Pair<Tuple, Map<String, TupleList>> daxPair = itr.next();
        Tuple tup = daxPair.first;

        Log.info("id = " + idgetter.getVal(tup));
      }

      // Close the document reader
      reader.remove();
      itr.remove();
    }

  }

  /**
   * Tests handling of invalid types for document schemas
   * 
   * @throws Exception
   */
  @Test
  public void badDocTypeTest() throws Exception {
    // uncomment when using JUnit 4.7
    // thrown.expect (RuntimeException.class);
    // thrown.expectMessage ("Invalid type 'Span' in field 'text' of document schema");

    String jsonFileString =
        TestConstants.TEST_DOCS_DIR + "/DocScanTests/jsonTests/badDocTypeTest.json";

    // set up the (flawed) expected doc schema
    String[] nontextNames = new String[] {"id", "text"};
    FieldType[] nontextTypes = new FieldType[] {FieldType.INT_TYPE, FieldType.SPAN_TYPE};
    String[] textNames = new String[] {"label"};

    TupleSchema docSchema =
        makeDocSchema(Constants.DEFAULT_DOC_TYPE_NAME, nontextNames, nontextTypes, textNames);

    // set up the expected external view schemas
    Map<Pair<String, String>, TupleSchema> extViewSchemas =
        new HashMap<Pair<String, String>, TupleSchema>();

    nontextNames = new String[] {"msgid"};
    nontextTypes = new FieldType[] {FieldType.INT_TYPE};
    textNames = new String[] {"fromAddress", "toAddress"};

    Pair<String, String> extViewName =
        new Pair<String, String>("EmailMetadata", "EmailMetadataSrc");

    TupleSchema extViewSchema1 =
        makeDocSchema(extViewName.first, nontextNames, nontextTypes, textNames);
    extViewSchemas.put(extViewName, extViewSchema1);

    try {
      // try to print all the doc and external view tuples
      Iterator<Pair<Tuple, Map<String, TupleList>>> itr =
          DocReader.makeDocandExternalPairsItr(jsonFileString, docSchema, extViewSchemas);

      printTuples(itr);

      Assert.fail("Should have thrown a RuntimeException.");
    } catch (TextAnalyticsException e) {
      String msg = e.getCause().getMessage();
      Log.info(msg);
      Assert.assertTrue(msg.contains("Invalid type 'Span' in field 'text' of document schema"));
    }

  }

  /**
   * Tests handling of invalid types for external view schemas
   * 
   * @throws Exception
   */
  @Test
  public void badExtViewTypeTest() throws Exception {
    // uncomment when using JUnit 4.7
    // thrown.expect (RuntimeException.class);
    // thrown.expectMessage ("Invalid type 'Boolean' in field 'democrat' of external view schema");

    String jsonFileString =
        TestConstants.TEST_DOCS_DIR + "/DocScanTests/jsonTests/badExtViewTypeTest.json";

    // set up the expected doc schema
    String[] nontextNames = new String[] {"id"};
    FieldType[] nontextTypes = new FieldType[] {FieldType.INT_TYPE};
    String[] textNames = new String[] {"label", "text"};

    TupleSchema docSchema =
        makeDocSchema(Constants.DEFAULT_DOC_TYPE_NAME, nontextNames, nontextTypes, textNames);

    // set up the expected external view schemas
    Map<Pair<String, String>, TupleSchema> extViewSchemas =
        new HashMap<Pair<String, String>, TupleSchema>();

    nontextNames = new String[] {"msgid"};
    nontextTypes = new FieldType[] {FieldType.INT_TYPE};
    textNames = new String[] {"fromAddress", "toAddress"};

    Pair<String, String> extViewName =
        new Pair<String, String>("EmailMetadata", "EmailMetadataSrc");

    TupleSchema extViewSchema1 =
        makeDocSchema(extViewName.first, nontextNames, nontextTypes, textNames);
    extViewSchemas.put(extViewName, extViewSchema1);

    nontextNames = new String[] {"democrat"};
    nontextTypes = new FieldType[] {FieldType.BOOL_TYPE};
    textNames = new String[0];

    extViewName = new Pair<String, String>("Party", "PartySrc");

    TupleSchema extViewSchema2 =
        makeDocSchema(extViewName.first, nontextNames, nontextTypes, textNames);
    extViewSchemas.put(extViewName, extViewSchema2);

    try {
      // print all the doc and external view tuples
      Iterator<Pair<Tuple, Map<String, TupleList>>> itr =
          DocReader.makeDocandExternalPairsItr(jsonFileString, docSchema, extViewSchemas);

      printTuples(itr);

      Assert.fail("Should have thrown a RuntimeException.");
    } catch (TextAnalyticsException e) {
      Assert.assertTrue(null != e.getCause());
      String msg = e.getCause().getMessage();
      Log.info(msg);
      Assert.assertTrue(
          msg.contains("Invalid type 'Boolean' in field 'democrat' of external view schema"));
    }

  }

  /**
   * Tests compiling an AQL to run on document tuples in JSON.
   * 
   * @throws Exception
   */
  @Test
  public void eViewTest() throws Exception {
    final String JSON_FILE = TestConstants.TEST_DOCS_DIR + "/DocScanTests/jsonTests/EView.json";

    startTest();
    setPrintTups(true);

    runNonModularAQLTest(new File(JSON_FILE));

    // these outputs are not very large, so no need for truncation
    compareAgainstExpected(false);

  }

  /**
   * Test for DocReader support of Span fields in JSON input format, with both explicit docRef
   * attribute and without it.
   * 
   * @throws Exception
   */
  @Test
  public void eViewSpanTest() throws Exception {
    String JSON_FILE = TestConstants.TEST_DOCS_DIR + "/DocScanTests/jsonTests/eViewWithSpan.json";
    final String MODULE_NAME = "eViewSpanTest";

    startTest();
    setPrintTups(true);

    compileAndRunModule(MODULE_NAME, new File(JSON_FILE), null);

    // These outputs are not very large, so no need for truncation
    compareAgainstExpected(false);

    // TESTS FOR ERROR HANDLING IN THE JSON FORMAT
    // Use the same module as before, with different JSON files
    String modulePathURI = getCurOutputDir().toURI().toString();

    // ERROR HANDLING 1: when the JSON span is not a JSON record
    JSON_FILE =
        TestConstants.TEST_DOCS_DIR + "/DocScanTests/jsonTests/eViewWithSpanWrongJsonTypeTest.json";
    String expectedMsg =
        "line 1:  Unable to create span from JSON record 0 into field 'spanField' of schema 'eViewSpanTest.TestSpan' because the value of the field 'begin' of the JSON record is null. Provide a non-null value for the field 'begin' of this JSON record.";
    try {
      runModule(new File(JSON_FILE), MODULE_NAME, modulePathURI);
      Assert.fail(
          String.format("Should have thrown an ExceptionWithView with message: %s", expectedMsg));
    } catch (ExceptionWithView e) {
      checkException(expectedMsg, e);
    }

    // ERROR HANDLING 1: when the JSON span is not a JSON record
    JSON_FILE =
        TestConstants.TEST_DOCS_DIR + "/DocScanTests/jsonTests/eViewWithSpanWrongJsonTypeTest.json";
    expectedMsg =
        "line 1:  Unable to create span from JSON record 0 into field 'spanField' of schema 'eViewSpanTest.TestSpan' because the value of the field 'begin' of the JSON record is null. Provide a non-null value for the field 'begin' of this JSON record.";
    try {
      runModule(new File(JSON_FILE), MODULE_NAME, modulePathURI);
      Assert.fail(
          String.format("Should have thrown an ExceptionWithView with message: %s", expectedMsg));
    } catch (ExceptionWithView e) {
      checkException(expectedMsg, e);
    }

    // ERROR HANDLING 2: when the JSON span doesn't have a begin field
    JSON_FILE =
        TestConstants.TEST_DOCS_DIR + "/DocScanTests/jsonTests/eViewWithSpanNoBeginTest.json";
    expectedMsg =
        "line 1:  Unable to create span from JSON record {\"end\":4} into field 'spanField' of schema 'eViewSpanTest.TestSpan' because the value of the field 'begin' of the JSON record is null.";
    try {
      runModule(new File(JSON_FILE), MODULE_NAME, modulePathURI);
      Assert.fail(
          String.format("Should have thrown an ExceptionWithView with message: %s", expectedMsg));
    } catch (ExceptionWithView e) {
      checkException(expectedMsg, e);
    }

    // ERROR HANDLING 3: when the JSON span begin field is of the wrong type
    JSON_FILE = TestConstants.TEST_DOCS_DIR
        + "/DocScanTests/jsonTests/eViewWithSpanWrongBeginTypeTest.json";
    expectedMsg =
        "line 1:  Unable to create span from JSON record {\"begin\":\"string value\",\"end\":4} into field 'spanField' of schema 'eViewSpanTest.TestSpan' because the value of the field 'begin' has unexpected type TextNode. Provide a value of type Integer for the field 'begin' of this JSON record.";
    try {
      runModule(new File(JSON_FILE), MODULE_NAME, modulePathURI);
      Assert.fail(
          String.format("Should have thrown an ExceptionWithView with message: %s", expectedMsg));
    } catch (ExceptionWithView e) {
      checkException(expectedMsg, e);
    }

    // ERROR HANDLING 4: when the JSON span doesn't have a end field
    JSON_FILE = TestConstants.TEST_DOCS_DIR + "/DocScanTests/jsonTests/eViewWithSpanNoEndTest.json";
    expectedMsg =
        "line 1:  Unable to create span from JSON record {\"begin\":4} into field 'spanField' of schema 'eViewSpanTest.TestSpan' because the value of the field 'end' of the JSON record is null.";
    try {
      runModule(new File(JSON_FILE), MODULE_NAME, modulePathURI);
      Assert.fail(
          String.format("Should have thrown an ExceptionWithView with message: %s", expectedMsg));
    } catch (ExceptionWithView e) {
      checkException(expectedMsg, e);
    }

    // ERROR HANDLING 5: when the JSON span end field is of the wrong type
    JSON_FILE =
        TestConstants.TEST_DOCS_DIR + "/DocScanTests/jsonTests/eViewWithSpanWrongEndTypeTest.json";
    expectedMsg =
        "line 1:  Unable to create span from JSON record {\"end\":0.5,\"begin\":4} into field 'spanField' of schema 'eViewSpanTest.TestSpan'.";
    try {
      runModule(new File(JSON_FILE), MODULE_NAME, modulePathURI);
      Assert.fail(
          String.format("Should have thrown an ExceptionWithView with message: %s", expectedMsg));
    } catch (ExceptionWithView e) {
      checkException(expectedMsg, e);
    }

    // ERROR HANDLING 6: when the JSON span docRef value is not part of the Document schema
    JSON_FILE = TestConstants.TEST_DOCS_DIR
        + "/DocScanTests/jsonTests/eViewWithSpanDocFieldMissingTest.json";
    expectedMsg =
        "line 1:  Unable to create span from JSON record {\"begin\":5,\"end\":9,\"docref\":\"text123\"} into field 'spanField' of schema 'eViewSpanTest.TestSpan' because schema of view 'Document' does not contain field 'text123' of type Text.";
    try {
      runModule(new File(JSON_FILE), MODULE_NAME, modulePathURI);
      Assert.fail(
          String.format("Should have thrown an ExceptionWithView with message: %s", expectedMsg));
    } catch (ExceptionWithView e) {
      checkException(expectedMsg, e);
    }

    // ERROR HANDLING 7: when the JSON span docRef value is not of type Text
    JSON_FILE = TestConstants.TEST_DOCS_DIR
        + "/DocScanTests/jsonTests/eViewWithSpanDocFieldWrongTypeTest.json";
    expectedMsg =
        "line 1:  Unable to create span from JSON record {\"begin\":5,\"end\":9,\"docref\":\"id\"} into field 'spanField' of schema 'eViewSpanTest.TestSpan' because field 'id' of view 'Document' is of not of expected type Text (type is Integer)";
    try {
      runModule(new File(JSON_FILE), MODULE_NAME, modulePathURI);
      Assert.fail(
          String.format("Should have thrown an ExceptionWithView with message: %s", expectedMsg));
    } catch (ExceptionWithView e) {
      checkException(expectedMsg, e);
    }

    // ERROR HANDLING 8: when the JSON span record is invalid for some reason (Span.makeBaseSpan
    // throws an exception)
    JSON_FILE =
        TestConstants.TEST_DOCS_DIR + "/DocScanTests/jsonTests/eViewWithSpanInvalidSpanTest.json";
    expectedMsg =
        "line 1:  Unable to create span from JSON record {\"begin\":-2,\"end\":4} into field 'spanField' of schema 'eViewSpanTest.TestSpan'.";
    try {
      runModule(new File(JSON_FILE), MODULE_NAME, modulePathURI);
      Assert.fail(
          String.format("Should have thrown an ExceptionWithView with message: %s", expectedMsg));
    } catch (ExceptionWithView e) {
      checkException(expectedMsg, e);
    }

  }

  /**
   * Tests annotating on custom doc schema fields
   * 
   * @throws Exception
   */
  @Test
  public void salutationTest() throws Exception {
    final String JSON_FILE = TestConstants.TEST_DOCS_DIR + "/DocScanTests/jsonTests/EView.json";

    startTest();
    setPrintTups(true);

    compileAndRunModule("salutationTest", new File(JSON_FILE), null);

    // these outputs are not very large, so no need for truncation
    compareAgainstExpected(false);

  }

  /**
   * Test reading of scalar list type in external view specified by a JSON file
   * 
   * @throws Exception
   */

  // TODO: Currently will not work because parser does not accept scalar list types for external
  // views (defect )
  // @Test
  public void scalarTest() throws Exception {
    final String JSON_FILE =
        TestConstants.TEST_DOCS_DIR + "/DocScanTests/jsonTests/scalarTest.json";

    startTest();

    runNonModularAQLTest(new File(JSON_FILE));
  }

  /**
   * Test reading a JSON file that does not contain external view data
   * 
   * @throws Exception
   */
  @Test
  public void noExtViewsTest() throws Exception {
    {
      String jsonFileString = TestConstants.TEST_DOCS_DIR + "/DocScanTests/jsonTests/EView.json";

      // set up the expected doc schema
      String[] nontextNames = new String[] {"id"};
      FieldType[] nontextTypes = new FieldType[] {FieldType.INT_TYPE};
      String[] textNames = new String[] {"label", "text", "url", "timeStamp"};

      TupleSchema docSchema =
          makeDocSchema(Constants.DEFAULT_DOC_TYPE_NAME, nontextNames, nontextTypes, textNames);

      // set up the expected external view schemas
      Map<Pair<String, String>, TupleSchema> extViewSchemas = null;

      // print all the doc and external view tuples
      Iterator<Pair<Tuple, Map<String, TupleList>>> itr =
          DocReader.makeDocandExternalPairsItr(jsonFileString, docSchema, extViewSchemas);

      printTuples(itr);

      // Close the document iterator
      itr.remove();
    }
  }

  /**
   * Test a JSON with a schema that does not contain a text field
   * 
   * @throws Exception
   */
  @Test
  public void noTextFieldTest() throws Exception {
    String jsonFileString =
        TestConstants.TEST_DOCS_DIR + "/DocScanTests/jsonTests/noTextFieldTest.json";

    // set up the expected doc schema
    String[] nontextNames = new String[] {"id"};
    FieldType[] nontextTypes = new FieldType[] {FieldType.INT_TYPE};
    String[] textNames = new String[] {"label"};

    TupleSchema docSchema =
        makeDocSchema(Constants.DEFAULT_DOC_TYPE_NAME, nontextNames, nontextTypes, textNames);

    // set up the expected external view schemas
    Map<Pair<String, String>, TupleSchema> extViewSchemas =
        new HashMap<Pair<String, String>, TupleSchema>();

    nontextNames = new String[] {"msgid"};
    nontextTypes = new FieldType[] {FieldType.INT_TYPE};
    textNames = new String[] {"fromAddress", "toAddress"};

    Pair<String, String> extViewName =
        new Pair<String, String>("EmailMetadata", "EmailMetadataSrc");

    TupleSchema extViewSchema1 =
        makeDocSchema(extViewName.first, nontextNames, nontextTypes, textNames);
    extViewSchemas.put(extViewName, extViewSchema1);

    // print all the doc and external view tuples

    Iterator<Pair<Tuple, Map<String, TupleList>>> itr =
        DocReader.makeDocandExternalPairsItr(jsonFileString, docSchema, extViewSchemas);

    printTuples(itr);

    // Close the document reader
    itr.remove();
  }

  /**
   * Use a JSON with data that doesn't fit the expected schema
   */
  @Test
  public void badDataTest() throws Exception {
    // uncomment when using JUnit 4.7
    // thrown.expect (RuntimeException.class);
    // thrown.expectMessage ("line 2: Could not cast ");

    String jsonFileString =
        TestConstants.TEST_DOCS_DIR + "/DocScanTests/jsonTests/badDataTest.json";

    // set up the expected doc schema
    String[] nontextNames = new String[] {"id"};
    FieldType[] nontextTypes = new FieldType[] {FieldType.INT_TYPE};
    String[] textNames = new String[] {"label"};

    TupleSchema docSchema =
        makeDocSchema(Constants.DEFAULT_DOC_TYPE_NAME, nontextNames, nontextTypes, textNames);

    // set up the expected external view schemas
    Map<Pair<String, String>, TupleSchema> extViewSchemas =
        new HashMap<Pair<String, String>, TupleSchema>();

    nontextNames = new String[] {"msgid"};
    nontextTypes = new FieldType[] {FieldType.INT_TYPE};
    textNames = new String[] {"fromAddress", "toAddress"};

    Pair<String, String> extViewName =
        new Pair<String, String>("EmailMetadata", "EmailMetadataSrc");

    TupleSchema extViewSchema1 =
        makeDocSchema(extViewName.first, nontextNames, nontextTypes, textNames);
    extViewSchemas.put(extViewName, extViewSchema1);

    // try to print all the doc and external view tuples
    try {
      Iterator<Pair<Tuple, Map<String, TupleList>>> itr =
          DocReader.makeDocandExternalPairsItr(jsonFileString, docSchema, extViewSchemas);

      printTuples(itr);

      Assert.fail("Should have thrown an ExceptionWithView.");
    } catch (ExceptionWithView e) {
      e.printStackTrace();
      // Top level exception is ExceptionWithView in view Document. The cause of this exception
      // should contain the
      // expected string.
      // String msg = e.getMessage ();
      Assert.assertTrue(null != e.getCause());
      String msg = e.getCause().getMessage();
      Assert.assertTrue(null != msg);
      Assert.assertTrue(msg.contains(
          "line 2:  Value '\"this is not an integer\"' of field 'id' of schema 'Document' is not expected type 'Integer'."));
    }

  }

  /**
   * Use a JSON with an external view field that has extraneous external view data. <br/>
   * This should not throw an exception, we only check to make sure that all declared external views
   * are present.
   */
  @Test
  public void extraDataTest() throws Exception {
    String jsonFileString =
        TestConstants.TEST_DOCS_DIR + "/DocScanTests/jsonTests/extraDataTest.json";

    // set up the expected doc schema
    String[] nontextNames = new String[] {"id"};
    FieldType[] nontextTypes = new FieldType[] {FieldType.INT_TYPE};
    String[] textNames = new String[] {"label"};

    TupleSchema docSchema =
        makeDocSchema(Constants.DEFAULT_DOC_TYPE_NAME, nontextNames, nontextTypes, textNames);

    // set up the expected external view schemas
    Map<Pair<String, String>, TupleSchema> extViewSchemas =
        new HashMap<Pair<String, String>, TupleSchema>();

    nontextNames = new String[] {"msgid"};
    nontextTypes = new FieldType[] {FieldType.INT_TYPE};
    textNames = new String[] {"fromAddress", "toAddress"};

    Pair<String, String> extViewName =
        new Pair<String, String>("EmailMetadata", "EmailMetadataSrc");

    TupleSchema extViewSchema1 =
        makeDocSchema(extViewName.first, nontextNames, nontextTypes, textNames);
    extViewSchemas.put(extViewName, extViewSchema1);

    // try to print all the doc and external view tuples
    try {
      Iterator<Pair<Tuple, Map<String, TupleList>>> itr =
          DocReader.makeDocandExternalPairsItr(jsonFileString, docSchema, extViewSchemas);

      printTuples(itr);

      // Close the document reader
      itr.remove();
    } catch (ExceptionWithView e) {
      e.printStackTrace();

      Assert.fail("This test with extra external fields should have ignored the extra fields.");
    }

  }

  /**
   * Use a JSON without an expected external view (which should throw an exception)
   */
  @Test
  public void missingExternalViewTest() throws Exception {
    String jsonFileString =
        TestConstants.TEST_DOCS_DIR + "/DocScanTests/jsonTests/missingExternalViewTest.json";

    // set up the expected doc schema
    String[] nontextNames = new String[] {"id"};
    FieldType[] nontextTypes = new FieldType[] {FieldType.INT_TYPE};
    String[] textNames = new String[] {"label"};

    TupleSchema docSchema =
        makeDocSchema(Constants.DEFAULT_DOC_TYPE_NAME, nontextNames, nontextTypes, textNames);

    // set up the expected external view schemas
    Map<Pair<String, String>, TupleSchema> extViewSchemas =
        new HashMap<Pair<String, String>, TupleSchema>();

    nontextNames = new String[] {"msgid"};
    nontextTypes = new FieldType[] {FieldType.INT_TYPE};
    textNames = new String[] {"fromAddress", "toAddress"};

    Pair<String, String> extViewName =
        new Pair<String, String>("EmailMetadata", "EmailMetadataSrc");

    TupleSchema extViewSchema1 =
        makeDocSchema(extViewName.first, nontextNames, nontextTypes, textNames);
    extViewSchemas.put(extViewName, extViewSchema1);

    // try to print all the doc and external view tuples
    try {
      Iterator<Pair<Tuple, Map<String, TupleList>>> itr =
          DocReader.makeDocandExternalPairsItr(jsonFileString, docSchema, extViewSchemas);

      printTuples(itr);

      Assert.fail("Should have thrown a ExceptionWithView.");
    } catch (ExceptionWithView e) {
      e.printStackTrace();
      // Top level exception is ExceptionWithView in view Document. The cause of this exception
      // should contain the
      // expected string.
      // String msg = e.getMessage ();
      Assert.assertTrue(null != e.getCause());
      String msg = e.getCause().getMessage();
      Assert.assertTrue(null != msg);
      Assert.assertTrue(msg.contains(
          "line 1:  External view EmailMetadataSrc was expected but not found in input JSON."));
    }

  }

  /**
   * Use a JSON with two external views that have the same fields
   */
  @Test
  public void sameFieldTest() throws Exception {

    String jsonFileString =
        TestConstants.TEST_DOCS_DIR + "/DocScanTests/jsonTests/sameFieldTest.json";

    // set up the expected doc schema
    String[] nontextNames = new String[] {"id"};
    FieldType[] nontextTypes = new FieldType[] {FieldType.INT_TYPE};
    String[] textNames = new String[] {"label"};

    TupleSchema docSchema =
        makeDocSchema(Constants.DEFAULT_DOC_TYPE_NAME, nontextNames, nontextTypes, textNames);

    // set up the expected external view schemas
    Map<Pair<String, String>, TupleSchema> extViewSchemas =
        new HashMap<Pair<String, String>, TupleSchema>();

    nontextNames = new String[] {"msgid"};
    nontextTypes = new FieldType[] {FieldType.INT_TYPE};
    textNames = new String[] {"fromAddress", "toAddress"};

    Pair<String, String> extViewName =
        new Pair<String, String>("EmailMetadata", "EmailMetadataSrc");

    TupleSchema extViewSchema1 =
        makeDocSchema(extViewName.first, nontextNames, nontextTypes, textNames);
    extViewSchemas.put(extViewName, extViewSchema1);

    nontextNames = new String[] {"msgid"};
    nontextTypes = new FieldType[] {FieldType.INT_TYPE};
    textNames = new String[] {"fromAddress"};

    extViewName = new Pair<String, String>("Spam", "SpamSrc");

    TupleSchema extViewSchema2 =
        makeDocSchema(extViewName.first, nontextNames, nontextTypes, textNames);
    extViewSchemas.put(extViewName, extViewSchema2);

    // print all the doc and external view tuples
    Iterator<Pair<Tuple, Map<String, TupleList>>> itr =
        DocReader.makeDocandExternalPairsItr(jsonFileString, docSchema, extViewSchemas);

    StringBuffer results = printTuples(itr);

    // check if accessors linked to fields with the same name are accessing different external views
    Assert.assertTrue(results.toString().contains("Barack Obama"));
    Assert.assertTrue(results.toString().contains("Make Money Fast"));

    Assert.assertTrue(results.toString().contains("2012"));
    Assert.assertTrue(results.toString().contains("13"));

    // Close the document reader
    itr.remove();
  }

  /**
   * Test to verify that external view from json works fine with modular AQLs.
   * 
   * @throws Exception
   */
  @Test
  public void endToEndExtViewTest() throws Exception {
    startTest();
    compileModules(new String[] {"module1", "module2"}, null);

    super.setPrintTups(true);

    String jsonFileString =
        TestConstants.TEST_DOCS_DIR + "/DocScanTests/jsonTests/jsonExtViewScanTest.json";
    runModule(new File(jsonFileString), "module2", getCurOutputDir().toURI().toString());

    // truncateExpectedFiles ();
    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Test to verify that JSON doc reader returns appropriate error message for missing required
   * field 'bool' of type Boolean. This test is related to defect and defect#29797.
   * 
   * @throws Exception
   */
  @Test
  public void missingRequiredBoolFieldTest() throws Exception {
    startTest();

    String[] modules = new String[] {"moduleR1", "whitehouse"};
    // Compile test modules
    compileModules(modules, null);

    // Test 1: missing required field 'bool' of type Boolean
    try {
      String jsonFileString =
          TestConstants.TEST_DOCS_DIR + "/DocScanTests/jsonTests/missingRequiredBoolFieldTest.json";
      runModule(new File(jsonFileString), "moduleR1", getCurOutputDir().toURI().toString());
      Assert.fail("Should have thrown a ExceptionWithView.");
    } catch (ExceptionWithView e) {

      Assert.assertTrue(null != e.getCause());
      String msg = e.getCause().getMessage();
      Assert.assertTrue(null != msg);
      Assert.assertTrue(msg.contains(
          "JSON record at this line does not contain an attribute for the required field named 'bool'. Provide a non-null value of type 'Boolean' for this required field."));
      System.err.println(msg);
    }
  }

  /**
   * Test to verify that JSON doc reader returns appropriate error message for missing a required
   * field of type Text (specifically, unparameterized). This test is related to defect and
   * defect#29797.
   * 
   * @throws Exception
   */
  @Test
  public void missingRequiredTextFieldTest() throws Exception {
    startTest();

    String[] modules = new String[] {"moduleR1", "whitehouse"};
    // Compile test modules
    compileModules(modules, null);

    // Test 2: missing required field 'text' of type Text
    try {
      String jsonFileString =
          TestConstants.TEST_DOCS_DIR + "/DocScanTests/jsonTests/missingRequiredTextFieldTest.json";
      runModule(new File(jsonFileString), "moduleR1", getCurOutputDir().toURI().toString());
    } catch (ExceptionWithView e) {
      Assert.assertTrue(null != e.getCause());
      String msg = e.getCause().getMessage();
      Assert.assertTrue(null != msg);
      Assert.assertTrue(msg.contains(
          "line 2:  JSON record at this line does not contain an attribute for the required field named 'text'. "
              + "Provide a non-null value of type 'Text' for this required field."));
      System.err.println(msg);
    }
  }

  /**
   * Test to verify that JSON doc reader returns appropriate error message for missing a required
   * field of type Text in an external view. This test is related to defect and defect#29797.
   * 
   * @throws Exception
   */
  @Test
  public void missingRequiredExtViewFieldTest() throws Exception {
    startTest();

    String[] modules = new String[] {"moduleR1", "whitehouse"};
    // Compile test modules
    compileModules(modules, null);

    // Test 3: missing required field 'toAddress' of type Text in an external view
    try {
      String jsonFileString = TestConstants.TEST_DOCS_DIR
          + "/DocScanTests/jsonTests/missingRequiredExtViewFieldTest.json";
      runModule(new File(jsonFileString), "whitehouse", getCurOutputDir().toURI().toString());
    } catch (ExceptionWithView e) {
      Assert.assertTrue(null != e.getCause());
      String msg = e.getCause().getMessage();
      Assert.assertTrue(null != msg);
      Assert.assertTrue(msg.contains(
          "line 3:  JSON record at this line does not contain an attribute for the required field named 'toAddress'. "
              + "Provide a non-null value of type 'Text' for this required field."));
      System.err.println(msg);
    }
  }

  /**
   * Test to verify that JSON doc reader returns appropriate error message for missing a required
   * field of type Span in an external view. This test is related to defect and defect#29797. <br />
   * <br />
   * This test is commented out because we do not currently support inputs with Span in JSON <br />
   * Once Span inputs are supported, enable by uncommenting the test, copy the source modules from
   * docMissingRequireFieldTest in testdata/aql/DocScanTests/missingRequiredTextFieldTest to
   * missingRequiredSpanFieldTest, and change the ext view schema of fromAddress in whitehouse.aql
   * to Span -- eyhung
   * 
   * @throws Exception
   */
  // @Test
  public void missingRequiredSpanFieldTest() throws Exception {
    startTest();

    String[] modules = new String[] {"moduleR1", "whitehouse"};
    // Compile test modules
    compileModules(modules, null);

    // Test 4: missing required field 'fromAddress' of type Span in an external view

    try {
      String jsonFileString =
          TestConstants.TEST_DOCS_DIR + "/DocScanTests/jsonTests/missingRequiredSpanFieldTest.json";
      runModule(new File(jsonFileString), "whitehouse", getCurOutputDir().toURI().toString());
    } catch (ExceptionWithView e) {
      Assert.assertTrue(null != e.getCause());
      String msg = e.getCause().getMessage();
      Assert.assertTrue(null != msg);

      System.err.println(msg);
    }

  }

  /**
   * Test to verify that JSON doc reader correctly parses an external view field without any tuples.
   * 
   * @throws Exception
   */
  @Test
  public void emptyExtViewTest() throws Exception {
    startTest();

    String[] modules = new String[] {"moduleR1", "whitehouse"};
    // Compile test modules
    compileModules(modules, null);

    // External view with no tuple information should not throw an exception
    try {
      String jsonFileString =
          TestConstants.TEST_DOCS_DIR + "/DocScanTests/jsonTests/emptyExtViewTest.json";
      runModule(new File(jsonFileString), "whitehouse", getCurOutputDir().toURI().toString());
    } catch (ExceptionWithView e) {
      e.printStackTrace();

      Assert.fail("This test with no tuples in an external view should have passed.");
    }
  }

  /**
   * Test to verify that the CSV doc reader correctly parses a simple CSV file with header
   * 
   * @throws Exception
   */
  @Test
  public void csvTest() throws Exception {
    startTest();
    String csvFileString = TestConstants.TEST_DOCS_DIR + "/DocScanTests/csvTests/csvScanTest.csv";

    // set up the expected doc schema, ignoring one
    String[] nontextNames = new String[] {"avg", "hr", "mvp"};
    FieldType[] nontextTypes =
        new FieldType[] {FieldType.FLOAT_TYPE, FieldType.INT_TYPE, FieldType.BOOL_TYPE};
    String[] textNames = new String[] {"first_name", "last_name"};

    TupleSchema docSchema =
        makeDocSchema(Constants.DEFAULT_DOC_TYPE_NAME, nontextNames, nontextTypes, textNames);

    // set up the expected external view schemas
    Map<Pair<String, String>, TupleSchema> extViewSchemas = null;

    // print all the doc tuples
    Iterator<Pair<Tuple, Map<String, TupleList>>> itr =
        DocReader.makeDocandExternalPairsItr(csvFileString, docSchema, extViewSchemas);

    printTuples(itr);

    // Close the document reader
    itr.remove();

    endTest();
  }

  /**
   * Test to verify that the CSV doc reader correctly parses a real CSV file with header from
   * BigSheets
   * 
   * @throws Exception
   */
  @Test
  public void csvBigSheetsTest() throws Exception {
    startTest();
    String csvFileString =
        TestConstants.TEST_DOCS_DIR + "/DocScanTests/csvTests/bigSheetsSample.csv";

    // set up the expected doc schema, ignoring one
    String[] nontextNames = new String[] {"ICD9Codes", "BirthYear", "HomeZip"};
    FieldType[] nontextTypes =
        new FieldType[] {FieldType.FLOAT_TYPE, FieldType.INT_TYPE, FieldType.INT_TYPE};
    String[] textNames =
        new String[] {"OrderID", "DiagName", "DiagGroupName", "Gender", "HomeCity"};

    TupleSchema docSchema =
        makeDocSchema(Constants.DEFAULT_DOC_TYPE_NAME, nontextNames, nontextTypes, textNames);

    // read all 500 tuples -- throw exception if one was not correctly read
    DocReader reader = new DocReader(new File(csvFileString), docSchema, null);

    int ndoc = 0;
    while (reader.hasNext()) {
      // Tuple tup =
      reader.next();
      // Log.debug (tup.toString ());

      ndoc++;
    }

    Assert.assertEquals(500, ndoc);

    // Close the document reader
    reader.remove();

    endTest();
  }

  /**
   * Test to verify that the CSV doc reader correctly parses a CSV file without the text column,
   * even when lang override is set. Verifies defect
   * 
   * @throws Exception
   */
  @Test
  public void csvWithNoTextColumnTest() throws Exception {
    startTest();

    // setting language code to non-default language triggers the defect
    setLanguage(LangCode.zh);

    String[] modules = new String[] {"defect46880"};
    // Compile test modules
    compileModules(modules, null);

    String csvFileString = TestConstants.TEST_DOCS_DIR + "/DocScanTests/csvTests/46880.csv";
    runModule(new File(csvFileString), "defect46880", getCurOutputDir().toURI().toString());

  }

  /**
   * Read a CSV file with multiple document tuples, annotate, and print
   * 
   * @throws Exception
   */
  @Test
  public void annotateFromCsvTest() throws Exception {

    startTest();

    setPrintTups(true);

    String jsonFileString = TestConstants.TEST_DOCS_DIR + "/DocScanTests/csvTests/csvScanTest.csv";

    runNonModularAQLTest(new File(jsonFileString));

    // these outputs are not very large, so no need for truncation
    compareAgainstExpected(false);
  }

  /**
   * Test to verify that the CSV doc reader throws exception on unsupported types
   * 
   * @throws Exception
   */
  @Test
  public void csvUnsupportedTypeTest() throws Exception {
    startTest();
    String csvFileString = TestConstants.TEST_DOCS_DIR + "/DocScanTests/csvTests/csvScanTest.csv";

    // set up the expected doc schema, with an illegal type of Span
    String[] nontextNames = new String[] {"avg", "hr", "mvp"};
    FieldType[] nontextTypes =
        new FieldType[] {FieldType.SPAN_TYPE, FieldType.INT_TYPE, FieldType.BOOL_TYPE};
    String[] textNames = new String[] {"first_name", "last_name"};

    TupleSchema docSchema =
        makeDocSchema(Constants.DEFAULT_DOC_TYPE_NAME, nontextNames, nontextTypes, textNames);

    // set up the expected external view schemas
    Map<Pair<String, String>, TupleSchema> extViewSchemas = null;

    try {
      Iterator<Pair<Tuple, Map<String, TupleList>>> itr =
          DocReader.makeDocandExternalPairsItr(csvFileString, docSchema, extViewSchemas);

      printTuples(itr);
    } catch (FatalRuntimeError e) {
      Assert.assertTrue(null != e.getCause());
      String msg = e.getCause().getMessage();
      Assert.assertTrue(null != msg);
      Assert.assertTrue(msg.contains("Invalid type 'Span' in field 'avg' of doc schema"));
      System.err.println(msg);
    }
    endTest();
  }

  /**
   * Test to verify that the CSV doc reader throws exception when required column is not found
   * 
   * @throws Exception
   */
  @Test
  public void csvMissingColumnTest() throws Exception {
    startTest();
    String csvFileString =
        TestConstants.TEST_DOCS_DIR + "/DocScanTests/csvTests/csvMissingColumnTest.csv";

    // set up the expected doc schema
    String[] nontextNames = new String[] {"avg", "hr", "mvp"};
    FieldType[] nontextTypes =
        new FieldType[] {FieldType.SPAN_TYPE, FieldType.INT_TYPE, FieldType.BOOL_TYPE};
    String[] textNames = new String[] {"first_name", "last_name"};

    TupleSchema docSchema =
        makeDocSchema(Constants.DEFAULT_DOC_TYPE_NAME, nontextNames, nontextTypes, textNames);

    // set up the expected external view schemas
    Map<Pair<String, String>, TupleSchema> extViewSchemas = null;

    try {
      // try to read the tuple, but the document is missing a required column
      Iterator<Pair<Tuple, Map<String, TupleList>>> itr =
          DocReader.makeDocandExternalPairsItr(csvFileString, docSchema, extViewSchemas);

      printTuples(itr);
    } catch (TextAnalyticsException e) {
      String msg = e.getMessage();
      Assert.assertTrue(null != msg);
      Assert.assertTrue(msg
          .contains("The header of CSV file 'csvMissingColumnTest.csv' does not contain columns"));
      System.err.println(msg);
    }
    endTest();
  }

  /**
   * Test to verify that the CSV doc reader throws exception when data does not conform to expected
   * type. Tests both Float and Integer (the only types that are validated).
   * 
   * @throws Exception
   */
  @Test
  public void csvBadDataTest() throws Exception {
    startTest();
    String badIntCsv = TestConstants.TEST_DOCS_DIR + "/DocScanTests/csvTests/badInteger.csv";
    String badFloatCsv = TestConstants.TEST_DOCS_DIR + "/DocScanTests/csvTests/badFloat.csv";
    String badBoolCsv = TestConstants.TEST_DOCS_DIR + "/DocScanTests/csvTests/badBoolean.csv";

    // set up the expected doc schema
    String[] nontextNames = new String[] {"avg", "hr", "mvp"};
    FieldType[] nontextTypes =
        new FieldType[] {FieldType.FLOAT_TYPE, FieldType.INT_TYPE, FieldType.BOOL_TYPE};
    String[] textNames = new String[] {"first_name", "last_name"};

    TupleSchema docSchema =
        makeDocSchema(Constants.DEFAULT_DOC_TYPE_NAME, nontextNames, nontextTypes, textNames);

    // set up the expected external view schemas
    Map<Pair<String, String>, TupleSchema> extViewSchemas = null;

    try {
      // try to read the tuple, but the document has an invalid integer value
      Iterator<Pair<Tuple, Map<String, TupleList>>> itr =
          DocReader.makeDocandExternalPairsItr(badIntCsv, docSchema, extViewSchemas);

      printTuples(itr);
    } catch (ExceptionWithView e) {
      Assert.assertTrue(null != e.getCause());
      String msg = e.getCause().getMessage();
      Assert.assertTrue(null != msg);
      System.err.println(msg);
      Assert.assertTrue(msg.contains("value of Integer field 'hr' is 'whoa'"));
    }

    try {
      // try to read the tuple, but the document has an invalid float value
      Iterator<Pair<Tuple, Map<String, TupleList>>> itr =
          DocReader.makeDocandExternalPairsItr(badFloatCsv, docSchema, extViewSchemas);

      printTuples(itr);
    } catch (ExceptionWithView e) {
      Assert.assertTrue(null != e.getCause());
      String msg = e.getCause().getMessage();
      Assert.assertTrue(null != msg);
      System.err.println(msg);
      Assert.assertTrue(msg.contains("value of Float field 'avg' is 'blah'"));
    }

    try {
      // try to read the tuple, but the document has an invalid float value
      Iterator<Pair<Tuple, Map<String, TupleList>>> itr =
          DocReader.makeDocandExternalPairsItr(badBoolCsv, docSchema, extViewSchemas);

      printTuples(itr);
    } catch (ExceptionWithView e) {
      Assert.assertTrue(null != e.getCause());
      String msg = e.getCause().getMessage();
      Assert.assertTrue(null != msg);
      System.err.println(msg);
      Assert.assertTrue(
          msg.contains("value of Boolean field 'mvp' is 't', valid values are 'true' or 'false'"));
    }
    endTest();
  }

  /**
   * Test to ensure that the CSV reader behaves properly on empty input.
   */
  @Test
  public void emptyCsvInputTest() throws Exception {
    final String INFILE_NAME = TestConstants.TEST_DOCS_DIR + "/DocScanTests/csvTests/empty.csv";

    DocReader reader = new DocReader(new File(INFILE_NAME));

    int ndoc = 0;
    while (reader.hasNext()) {
      reader.next();
      ndoc++;
    }

    Assert.assertEquals(0, ndoc);

    // Close the document reader
    reader.remove();

  }

  /**
   * Test to ensure that the CSV reader behaves properly on a CSV that supports the default schema.
   */
  @Test
  public void csvDefaultSchemaTest() throws Exception {
    final String INFILE_NAME = TestConstants.TEST_DOCS_DIR + "/DocScanTests/csvTests/default.csv";

    DocReader reader = new DocReader(new File(INFILE_NAME));

    int ndoc = 0;
    while (reader.hasNext()) {
      Tuple tup = reader.next();
      Log.info(tup.toString());

      ndoc++;
    }

    Assert.assertEquals(4, ndoc);

    // Close the document reader
    reader.remove();
  }

  /**
   * Test to ensure that the CSV reader invoked with the default schema throws an exception if it
   * does not contain both "text" and "label"
   */
  @Test
  public void csvDefault1ColSchemaTest() throws Exception {
    final String INFILE_NAME =
        TestConstants.TEST_DOCS_DIR + "/DocScanTests/csvTests/default1col.csv";
    DocReader reader = null;
    try {

      // this statement should throw an exception
      reader = new DocReader(new File(INFILE_NAME));

      // following statements should not get processed
      int ndoc = 0;

      while (reader.hasNext()) {
        Tuple tup = reader.next();
        Log.info(tup.toString());

        ndoc++;
      }

      Assert.assertEquals(2, ndoc);
    } catch (TextAnalyticsException e) {
      String msg = e.getMessage();
      Assert.assertTrue(null != msg);
      Assert.assertTrue(msg.contains("does not contain columns required by the document schema."));
      System.err.println(msg);
    } finally {
      if (null != reader)
        reader.remove();
    }
  }

  /**
   * Test case to ensure that the leading/trailing white-spaces in the fields of a csv records are
   * handled propertl; trim the white-spaces for the field of type: Integer, Float and Boolean;
   * retain the white-spaces for the field of type Text and String. This test captures the scenario
   * mentioned in defect# 49370.
   * 
   * @throws Exception
   */
  @Test
  public void csvWithLeadingTrailingWhitespacesTest() throws Exception {
    final String INFILE_NAME = TestConstants.TEST_DOCS_DIR
        + "/DocScanTests/csvWithLeadingTrailingWhitespacesTest/leadingTrailingWhitespaces.csv";
    DocReader reader = null;
    try {
      startTest();
      OperatorGraph operatorGraph = compileAndLoadModule("module1", null);
      reader = new DocReader(new File(INFILE_NAME), operatorGraph.getDocumentSchema(), null);

      int ndoc = 0;
      while (reader.hasNext()) {
        Tuple tup = reader.next();
        Log.info(tup.toString());

        ndoc++;
      }

      Assert.assertEquals(3, ndoc);
    } finally {
      if (null != reader)
        reader.remove();
      endTest();
    }
  }

  /**
   * Test case to verify that custom separators in a CSV file are handled properly (Task 70209).
   * 
   * @throws Exception
   */
  @Test
  public void csvCustomSeparatorTest() throws Exception {
    final String INFILE_NAME =
        TestConstants.TEST_DOCS_DIR + "/DocScanTests/csvTests/NHTSA_10_mod.csv";
    DocReader reader = null;
    try {
      startTest();
      OperatorGraph operatorGraph = compileAndLoadModule("module1", null);

      // the following line will throw an exception if the tab separator is not handled correctly
      reader = new DocReader(new File(INFILE_NAME), operatorGraph.getDocumentSchema(), null, '\t');

      int ndoc = 0;
      while (reader.hasNext()) {
        Tuple tup = reader.next();
        Log.info(tup.toString());

        ndoc++;
      }

      Assert.assertEquals(10, ndoc);
    } finally {
      if (null != reader)
        reader.remove();
      endTest();
    }
  }

  /*
   * PRIVATE METHODS GO HERE
   */

  // Helper function to print all the doc and ext view tuples linked to the input iterator
  private StringBuffer printTuples(Iterator<Pair<Tuple, Map<String, TupleList>>> itr) {

    StringBuffer ret = new StringBuffer();

    while (itr.hasNext()) {
      Pair<Tuple, Map<String, TupleList>> daxPair = itr.next();
      Tuple tup = daxPair.first;
      Map<String, TupleList> extViews = daxPair.second;

      ret.append("Doc tuple: " + tup.toString() + "\n");

      if (extViews != null) {
        ret.append("Ext view tuples: " + extViews.toString() + "\n");
      }

    }

    Log.info(ret.toString());
    return ret;
  }

  /**
   * Utility method to check the content of a given exception
   * 
   * @param expectedMsg the expected message that should be in the inner exception
   * @param e the actual exception
   */
  private void checkException(String expectedMsg, ExceptionWithView e) {
    e.printStackTrace();
    // Top level exception is ExceptionWithView in view Document. The cause of this exception should
    // contain the
    // expected string.
    // String msg = e.getMessage ();
    Assert.assertTrue(null != e.getCause());
    String msg = e.getCause().getMessage();
    Assert.assertTrue(null != msg);
    Assert.assertTrue(msg.contains(expectedMsg));
  }
}
