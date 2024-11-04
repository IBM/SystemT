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
import static org.junit.Assert.fail;

import java.io.File;
import java.util.Map;

import org.junit.Test;

import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.scan.DocScanInternal;
import com.ibm.avatar.algebra.util.lang.LangCode;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.DocReader;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.logging.Log;

/**
 * Tests the DocReader API
 * 
 */
public class DocReaderTests extends RuntimeTestHarness {

  /**
   * Main method for invoking one test at a time.
   * 
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    DocReaderTests t = new DocReaderTests();
    // t.setUp ();

    long startMS = System.currentTimeMillis();

    t.testCardinalityAndInternalCursorAffectEachOther();

    long endMS = System.currentTimeMillis();

    // t.tearDown ();

    double elapsedSec = ((double) (endMS - startMS)) / 1000.0;
    System.err.printf("Test took %1.3f sec.\n", elapsedSec);
  }

  /**
   * Helper method that tests the size of the input collection
   * 
   * @param inputDoc absolute path of the input document
   * @param expectedSize size of the document as expected by the test
   * @param message Error message to be thrown if the actual size does not match with expected size
   * @throws Exception
   */
  private void testSize(String inputDoc, int expectedSize, String message) throws Exception {
    DocReader docReader = new DocReader(new File(inputDoc));
    assertEquals(message, expectedSize, docReader.size());

    // Close the document reader
    docReader.remove();
  }

  /**
   * Check cardinality of a directory
   */
  @Test
  public void testCardinalityDir() throws Exception {

    final String DOCS_DIR = TestConstants.TESTDATA_DIR + "/docs/docReaderTests/dirDocScan";
    testSize(DOCS_DIR, 5, "Cardinality of dirDocScan folder should be 5.");
  }

  /**
   * Check cardinality of a single text file
   */
  @Test
  public void testCardinalitySingleTextFile() throws Exception {
    final String TEXT_FILE =
        TestConstants.TESTDATA_DIR + "/docs/docReaderTests/dirDocScan/doc_1.txt";
    testSize(TEXT_FILE, 1, "Cardinality of text file should be 1.");
  }

  /**
   * Check cardinality of an empty directory
   */
  // Ramiya - 12/08/2016 - comment out this test for now since Github does not allow completely
  // empty directories
  // @Test
  public void testCardinalityEmptyDir() throws Exception {
    final String EMPTY_DIR = TestConstants.TESTDATA_DIR + "/docs/docReaderTests/subdir_empty";
    testSize(EMPTY_DIR, 0, "Cardinality of empty directory should be 0.");
  }

  /**
   * Check cardinality of a non-empty del file
   */
  @Test
  public void testCardinalityNonEmptyDelFile() throws Exception {
    final String NON_EMPTY_DEL_FILE = TestConstants.TESTDATA_DIR + "/docs/common/spock.del";
    testSize(NON_EMPTY_DEL_FILE, 1985, "Cardinality of spock should be 1985.");
  }

  /**
   * Check cardinality of an empty del file
   */
  @Test
  public void testCardinalityEmptyDelFile() throws Exception {
    final String EMPTY_DEL_FILE = TestConstants.TESTDATA_DIR + "/docs/empty.del";
    testSize(EMPTY_DEL_FILE, 0, "Cardinality of an empty del file should be 0.");
  }

  /**
   * Check cardinality of a zip file
   */
  @Test
  public void testCardinalityZipFile() throws Exception {
    final String ZIP_FILE = TestConstants.TESTDATA_DIR + "/docs/common/ensmall.zip";
    testSize(ZIP_FILE, 1000, "Cardinality of ensmall.zip should be 1000.");
  }

  /**
   * Check cardinality of a tar file
   */
  @Test
  public void testCardinalityTarFile() throws Exception {
    final String TAR_FILE = TestConstants.TESTDATA_DIR + "/docs/docReaderTests/imdb.tar";
    testSize(TAR_FILE, 4, "Cardinality of imdb.tar should be 4.");
  }

  /**
   * Check cardinality of a tar.gz file
   */
  @Test
  public void testCardinalityTarGZFile() throws Exception {
    final String TAR_GZ_FILE = TestConstants.TESTDATA_DIR + "/docs/common/imdb.tar.gz";
    testSize(TAR_GZ_FILE, 4, "Cardinality of imdb.tar.gz file should be 4.");
  }

  /**
   * Check cardinality of a tgz file
   */
  @Test
  public void testCardinalityTGZFile() throws Exception {

    final String TGZ_FILE = TestConstants.TESTDATA_DIR + "/docs/common/spockdocs.tgz";
    testSize(TGZ_FILE, 1931, "Cardinality of spockdocs.tgz should be 1931.");
  }

  /**
   * Verify if invoking DocReader.size() affects its internal cursor and vice versa
   */
  @Test
  public void testCardinalityAndInternalCursorAffectEachOther() throws Exception {
    final String DOCS_DIR = TestConstants.TESTDATA_DIR + "/docs/docReaderTests/dirDocScan";
    DocReader docReader = new DocReader(new File(DOCS_DIR));
    Tuple tuple1 = docReader.next();
    System.err.printf("tuple1 is: %s\n", tuple1.toString());
    assertTrue(tuple1.toString().contains("'Document 1.'"));
    assertEquals("Cardinality of dirDocScan folder should be 5.", 5, docReader.size());

    Tuple tuple2 = docReader.next();
    assertTrue(tuple2.toString().contains("'This is document 2.'"));
    assertEquals("Cardinality of dirDocScan folder should be 5.", 5, docReader.size());

    // Close the document reader
    docReader.remove();
  }

  /**
   * Test case for defect . This test case verifies that a del file with comma in label column can
   * be parsed successfully by DBDumpFileScan and format of the file is correctly determined.
   */
  @Test
  public void delFileWithCommaInLabelBugTest() {
    final String DEL_FILE =
        TestConstants.TESTDATA_DIR + "/docs/docReaderTests/del_files/url_with_comma.del";
    testCanReadDelFile(DEL_FILE, 309, "Cardinality of url_with_comma.del should be 309",
        "Failed to scan DEL file that contains comma in label column.");
  }

  @Test
  public void legacyDELFileFormatTest() {
    // TEST 1: Test if DocReader can read a del file with two columns and not enclosed within quotes
    final String DEL_FILE_1 =
        TestConstants.TESTDATA_DIR + "/docs/docReaderTests/del_files/two_column_without_quotes.del";
    testCanReadDelFile(DEL_FILE_1, 2, "Cardinality of two_column_without_quotes.del should be 2",
        "Failed to scan DEL file that contains two columns without quotes.");

    // TEST 2: Test if DocReader can read a del file with two columns and enclosed within quotes
    final String DEL_FILE_2 =
        TestConstants.TESTDATA_DIR + "/docs/docReaderTests/del_files/two_column_with_quotes.del";
    testCanReadDelFile(DEL_FILE_2, 2, "Cardinality of two_column_with_quotes.del should be 2",
        "Failed to scan DEL file that contains two columns without quotes.");

    // TEST 3: Test if DocReader can read a del file with three columns and not enclosed within
    // quotes
    final String DEL_FILE_3 = TestConstants.TESTDATA_DIR
        + "/docs/docReaderTests/del_files/three_column_without_quotes.del";
    testCanReadDelFile(DEL_FILE_3, 2, "Cardinality of three_column_without_quotes.del should be 2",
        "Failed to scan DEL file that contains two columns without quotes.");

    // TEST 4: Test spock100.del whose first record spans across multiple lines
    final String DEL_FILE_4 = TestConstants.TESTDATA_DIR + "/docs/common/spock100.del";
    testCanReadDelFile(DEL_FILE_4, 100, "Cardinality of spock100.del should be 100",
        "Failed to scan DEL file whose first record spans across multiple lines.");
  }

  /**
   * Scenario: Read the zip file by specifying a supported customized document schema, [text
   * Text].<br>
   * Expected result: 1000 document tuples with single field, name text, containing the content from
   * the each entry in the zip file
   * 
   * @throws Exceptiona
   */
  @Test
  public void readZipFileWithSchemaTest() throws Exception {
    startTest();
    final String ZIP_FILE = TestConstants.TESTDATA_DIR + "/docs/common/ensmall.zip";

    // set up the expected doc schema
    TupleSchema expectedDocSchema = DocScanInternal.createOneColumnSchema();

    genericReadTest(ZIP_FILE, expectedDocSchema, 1000);

    endTest();
  }

  /**
   * Scenario: Read the specified Tar GZ file by specifying a supported customized document schema,
   * [text Text].<br>
   * Expected result: 4 document tuples with single field, named text, containing the content from
   * the each entry in the specified tar.gz file.
   * 
   * @throws Exceptiona
   */
  @Test
  public void readTarGZFileWithSchemaTest() throws Exception {
    startTest();
    final String TAR_GZ_FILE = TestConstants.TESTDATA_DIR + "/docs/common/imdb.tar.gz";

    // set up the expected doc schema
    TupleSchema expectedDocSchema = DocScanInternal.createOneColumnSchema();

    genericReadTest(TAR_GZ_FILE, expectedDocSchema, 4);

    endTest();
  }

  /**
   * Scenario: Read the specified Tar file by specifying a supported customized document schema,
   * [text Text].<br>
   * Expected result: 4 document tuples with single field, named text, containing the content from
   * the each entry in the specified tar file.
   * 
   * @throws Exceptiona
   */
  @Test
  public void readTarFileWithSchemaTest() throws Exception {
    startTest();
    final String TAR_FILE = TestConstants.TESTDATA_DIR + "/docs/docReaderTests/imdb.tar";

    // set up the expected doc schema
    TupleSchema expectedDocSchema = DocScanInternal.createOneColumnSchema();

    genericReadTest(TAR_FILE, expectedDocSchema, 4);

    endTest();
  }

  /**
   * Scenario: Read the delimited file by specifying a supported customized document schema, [text
   * Text].<br>
   * Expected result: 1985 document tuples with single field, named text
   * 
   * @throws Exceptiona
   */
  @Test
  public void readDelFileWithSchemaTest() throws Exception {
    startTest();
    final String NON_EMPTY_DEL_FILE = TestConstants.TESTDATA_DIR + "/docs/common/spock.del";

    // set up the expected doc schema
    TupleSchema expectedDocSchema = DocScanInternal.createOneColumnSchema();

    genericReadTest(NON_EMPTY_DEL_FILE, expectedDocSchema, 1985);

    endTest();
  }

  /**
   * Scenario: Read the directory by specifying a supported customized document schema, [text
   * Text].<br>
   * Expected result: Five document tuples with single field, named text,containing the content of
   * the Text files in the directory.
   * 
   * @throws Exception
   */
  @Test
  public void readDirectoryWithSchemaTest() throws Exception {
    startTest();
    final String DIR = TestConstants.TESTDATA_DIR + "/docs/docReaderTests/dirDocScan/";

    // set up the expected doc schema
    TupleSchema expectedDocSchema = DocScanInternal.createOneColumnSchema();

    genericReadTest(DIR, expectedDocSchema, 5);

    endTest();
  }

  /**
   * Scenario: Read the Text file by specifying a supported customized document schema, [text
   * Text].<br>
   * Expected result: Single document tuple with only field text, containing the content of the Text
   * file.
   * 
   * @throws Exception
   */
  @Test
  public void readTextFileWithSchemaTest() throws Exception {
    startTest();
    final String TEXT_FILE =
        TestConstants.TESTDATA_DIR + "/docs/docReaderTests/dirDocScan/doc_1.txt";
    // set up the expected doc schema
    TupleSchema expectedDocSchema = DocScanInternal.createOneColumnSchema();

    genericReadTest(TEXT_FILE, expectedDocSchema, 1);

    endTest();
  }

  /**
   * Scenario: Read the Text file by specifying a supported customized document schema, [label Text,
   * text Text].<br>
   * Expected result: Single document tuple with fields label and text, containing the name and
   * content of the Text file.
   * 
   * @throws Exception
   */
  @Test
  public void readTextFileWithSchemaTest2() throws Exception {
    startTest();
    final String TEXT_FILE =
        TestConstants.TESTDATA_DIR + "/docs/docReaderTests/dirDocScan/doc_1.txt";
    // set up the expected doc schema
    TupleSchema expectedDocSchema = DocScanInternal.createLabeledSchema();

    genericReadTest(TEXT_FILE, expectedDocSchema, 1);

    endTest();
  }

  /**
   * Scenario (negative test case): Read the Text file using the by specifying unsupported document
   * schema, [customFieldName Text] <br>
   * Expected result: TextAnalyticsException containing detailed error message with corrective
   * actions.
   * 
   * @throws Exception
   */
  @Test
  public void readTextFileWithInvalidSchemaTest() throws Exception {
    startTest();
    final String TEXT_FILE =
        TestConstants.TESTDATA_DIR + "/docs/docReaderTests/dirDocScan/doc_1.txt";

    // set up the expected doc schema
    TupleSchema docSchema =
        new TupleSchema(new String[] {"customFieldName"}, new FieldType[] {FieldType.TEXT_TYPE});
    docSchema.setName(Constants.DEFAULT_DOC_TYPE_NAME);

    // TupleSchema.makeDocSchema (Constants.DEFAULT_DOC_TYPE_NAME, "customFieldName");

    try {
      new DocReader(new File(TEXT_FILE), docSchema, null);
    } catch (TextAnalyticsException e) {
      // TextAnalyticsException expected
      assertTrue(e instanceof TextAnalyticsException);
      return;
    }

    fail("Exception was expected. Control should have not reached here.");
    endTest();
  }

  /**
   * Verifies that DocReader can parse a simple json file and detect the number of tuples correctly.
   * <br/>
   * Test case for defect : Text analytics tool cannot parse blank line for JSON input docs
   * 
   * @throws Exception
   */
  @Test
  public void simpleJsonCardinalityTest() throws Exception {
    startTest();
    genericJsonCardinalityTest(TestConstants.TEST_DOCS_DIR + "/docReaderTests/json/simple.json");
    endTest();
  }

  /**
   * Verifies that DocReader can parse a json file whose first line is empty and detect the number
   * of tuples correctly. <br/>
   * Test case for defect : Text analytics tool cannot parse blank line for JSON input docs
   * 
   * @throws Exception
   */
  @Test
  public void firstLineEmptyJsonCardinalityTest() throws Exception {
    startTest();
    genericJsonCardinalityTest(
        TestConstants.TEST_DOCS_DIR + "/docReaderTests/json/firstLineEmpty.json");
    endTest();
  }

  /**
   * Verifies that DocReader can parse a json file with empty lines at various record numbers and
   * detect the number of tuples correctly. <br/>
   * Test case for defect : Text analytics tool cannot parse blank line for JSON input docs
   * 
   * @throws Exception
   */
  @Test
  public void intermediateLineEmptyJsonTest() throws Exception {
    startTest();
    genericJsonCardinalityTest(
        TestConstants.TEST_DOCS_DIR + "/docReaderTests/json/intermediateLineEmpty.json");
    endTest();
  }

  /**
   * Verifies that DocReader can parse a json file whose last record is empty and detect the number
   * of tuples correctly. <br/>
   * Test case for defect : Text analytics tool cannot parse blank line for JSON input docs
   * 
   * @throws Exception
   */
  @Test
  public void lastLineEmptyJsonTest() throws Exception {
    startTest();
    genericJsonCardinalityTest(
        TestConstants.TEST_DOCS_DIR + "/docReaderTests/json/lastLineEmpty.json");
    endTest();
  }

  /**
   * Verifies that DocReader can parse a json file that has consecutive lines empty and detect the
   * number of tuples correctly. <br/>
   * Test case for defect : Text analytics tool cannot parse blank line for JSON input docs
   * 
   * @throws Exception
   */
  @Test
  public void consecutiveLinesEmptyTest() throws Exception {
    startTest();
    genericJsonCardinalityTest(
        TestConstants.TEST_DOCS_DIR + "/docReaderTests/json/consecutiveLinesEmpty.json");
    endTest();
  }

  /**
   * Helper method to test cardinality of JSON files. Used by {@link #simpleJsonCardinalityTest()},
   * {@link #firstLineEmptyJsonCardinalityTest()}, {@link #intermediateLineEmptyJsonTest()},
   * {@link #lastLineEmptyJsonTest()}. <br/>
   * This helper method uses a hardcoded schema that defines the schema of the files used by the
   * tets listed above. <br/>
   * Verifies that DocReader can recognize that there are only 14 records in the json regardless of
   * newlines at beginning or middle or end of the file.
   * 
   * @param absoluteFilePath Absolute path of the JSON file
   * @throws Exception
   */
  private void genericJsonCardinalityTest(String absoluteFilePath) throws Exception {
    // set up the expected doc schema
    // String[] nontextNames = new String[] { "id", "fame", "murphylaw" };
    // FieldType[] nontextTypes = new FieldType[] { FieldType.INT_TYPE, FieldType.FLOAT_TYPE,
    // FieldType.BOOL_TYPE };
    // String[] textNames = new String[] { "text" };

    TupleSchema docSchema =
        new TupleSchema(new String[] {"id", "fame", "murphylaw", "text"}, new FieldType[] {
            FieldType.INT_TYPE, FieldType.FLOAT_TYPE, FieldType.BOOL_TYPE, FieldType.TEXT_TYPE});
    // TupleSchema.makeDocSchema (Constants.DEFAULT_DOC_TYPE_NAME, nontextNames, nontextTypes,
    // textNames);
    docSchema.setName(Constants.DEFAULT_DOC_TYPE_NAME);

    // Instantiate a docreader with specified schema
    DocReader docReader = new DocReader(new File(absoluteFilePath), docSchema, null);

    // Verify that DocReader can recognize that there are only 14 records in the json regardless of
    // newlines at
    // beginning or middle or end of the file.
    assertEquals("DocReader computed incorrect record count.", 14, docReader.size());

    // Close the document reader
    docReader.remove();
  }

  private void testCanReadDelFile(String DEL_FILE, int expectedSize, String assertionErrorMessage,
      String failureMessage) {
    DocReader docReader = null;
    try {
      docReader = new DocReader(new File(DEL_FILE));
      // DocScan is successful if control reaches here. Verify cardinality to ensure that file
      // format was recognized
      // correctly.
      assertEquals(assertionErrorMessage, expectedSize, docReader.size());
    } catch (Exception e) {
      fail(failureMessage);
    } finally {
      // Close the document reader
      if (null != docReader)
        docReader.remove();

    }
  }

  private void genericReadTest(String docCollectionPath, TupleSchema expectedDocSchema,
      int cardinality) throws Exception {
    DocReader reader = new DocReader(new File(docCollectionPath), expectedDocSchema, null);
    TupleSchema actualSchema = reader.getDocSchema();
    FieldGetter<Text> textAcc = actualSchema.textAcc(Constants.DOCTEXT_COL);

    // Assert actual and expected schema
    assertEquals(expectedDocSchema, actualSchema);

    while (reader.hasNext()) {
      Tuple docTup = reader.next();

      System.out.println(textAcc.getVal(docTup));
      assertTrue(null != textAcc.getVal(docTup));
    }

    // Assert number of tuples returned
    assertEquals(cardinality, reader.size());

    // Close the document reader
    reader.remove();
  }

  /**
   * Test that outputs the first document in English and subsequent documents in another language to
   * check that the override is working on any potentially cached documents.
   */
  public void genericLangOverrideTest(LangCode language, String langName, String inputFileName,
      TupleSchema docSchema, Map<Pair<String, String>, TupleSchema> extNameVsSchema)
      throws Exception {

    /**
     * Directory containing input documents that are specific to the tests in this class
     */
    final File DOCS_DIR = new File(TestConstants.TEST_DOCS_DIR, "docReaderTests");

    // A file containing some documents in the specified language
    final File docsFile = new File(DOCS_DIR, inputFileName);
    Log.info("Reading documents from %s", docsFile);

    // Open up the document collection
    DocReader docs = new DocReader(docsFile, docSchema, extNameVsSchema);

    // assume that the document collection contains a field named 'text'
    FieldGetter<Text> getText = docs.getDocSchema().textAcc(Constants.DOCTEXT_COL);

    boolean secondDocReached = false;

    // Now scan through the documents.
    // Override the language starting with the second document to make sure we aren't
    // incorrectly caching a document with the wrong language code.
    while (docs.hasNext()) {
      Tuple docTup = docs.next();
      Text sampleSpan = getText.getVal(docTup);

      if (secondDocReached) {
        assertTrue(sampleSpan.getLanguage() == language);
      } else {
        assertTrue(sampleSpan.getLanguage() == LangCode.DEFAULT_LANG_CODE);
      }

      docs.overrideLanguage(language);
      secondDocReached = true;
    }

    // Clean the document reader
    docs.remove();
  }

  /**
   * Test of the override functionality using Chinese on a Chinese ZIP doc collection
   */
  @Test
  public void langOverrideZipTest() throws Exception {

    startTest();

    genericLangOverrideTest(LangCode.zh, "chinese", "chinese.zip", null, null);
  }

  /**
   * Test of the override functionality using Chinese on a Chinese CSV doc collection
   */
  @Test
  public void langOverrideCsvTest() throws Exception {

    startTest();

    // set up the expected doc schema
    TupleSchema docSchema = DocScanInternal.createLabeledSchema();

    genericLangOverrideTest(LangCode.zh, "chineseCsv", "chinese.csv", docSchema, null);
  }

  /**
   * Test of the override functionality using Chinese on a Chinese JSON doc collection
   */
  @Test
  public void langOverrideJsonTest() throws Exception {

    startTest();

    // set up the expected doc schema
    TupleSchema docSchema = DocScanInternal.createLabeledSchema();

    genericLangOverrideTest(LangCode.zh, "chineseCsv", "chinese.json", docSchema, null);
  }

  /**
   * Test of the cached override functionality using Japanese on a Japanese DEL collection
   */
  @Test
  public void langOverrideDelTest() throws Exception {

    startTest();

    genericLangOverrideTest(LangCode.ja, "japanese", "japanese.del", null, null);
  }

}
