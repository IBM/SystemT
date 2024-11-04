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
package com.ibm.avatar.api;

import java.io.File;
import java.util.Iterator;
import java.util.Map;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.TextGetter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.oldscan.CsvFileScan;
import com.ibm.avatar.algebra.oldscan.DBDumpFileScan;
import com.ibm.avatar.algebra.oldscan.DirDocScan;
import com.ibm.avatar.algebra.oldscan.JsonFileScan;
import com.ibm.avatar.algebra.oldscan.TarFileScan;
import com.ibm.avatar.algebra.oldscan.TextFileScan;
import com.ibm.avatar.algebra.oldscan.ZipFileScan;
import com.ibm.avatar.algebra.scan.DocScanInternal;
import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.algebra.util.lang.LangCode;
import com.ibm.avatar.api.exceptions.ExceptionWithView;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException.ExceptionType;
import com.ibm.avatar.logging.Log;

/**
 * This class provides APIs to read data from supported document collections. Once the instance of
 * DocReader is created for a document collection use {@link #hasNext()} and {@link #next()} to
 * iterate through the document collection and fetch each document as a
 * {@link com.ibm.avatar.algebra.datamodel.Tuple}. Supported document collection formats:
 * <ul>
 * <li>A UTF-8 encoded single text file with one of the following file extensions: .txt, .htm,
 * .html, .xhtml, or .xml.
 * <li>A directory that contains text files in UTF-8 encoding.
 * <li>An archive file in one of the common archive formats (.zip, .tar, .tar.gz, .tgz) that
 * contains text files in UTF-8 encoding.
 * <li>A comma-delimited .del file in UTF-8 encoding in the following format:
 * <ul>
 * <li>Each row represents a single document. Rows are separated by new lines.
 * <li>Each row has three comma-separated columns that represent the identifier (of type integer),
 * label (of type string), and text (of type string).
 * <li>The label and text string values must be surrounded by the character string delimiter, a
 * double quotation mark ("). Any double quotation marks inside these values must be escaped ("").
 * <li>The document label can be any character string, for example, a file name.
 * <li>Here is an example .del file in this format that contains two documents with labels doc_1.txt
 * and doc_2.txt.
 * <ul>
 * <li>1,"doc_1.txt","This is an example document."
 * <li>2,"doc_2.txt","This is an example document with ""double quotation marks"" that need to be
 * escaped.".
 * </ul>
 * </ul>
 * <li>A CSV file with the first line being a header row defining field names separated by a
 * delimiter (default: ','), and subsequent lines being a list of document records. Each line
 * consists of exactly one record, with each field indexed by the name in the corresponding field in
 * the header row. Here is an example .csv file in this format:
 * <ul>
 * <li>first_name,last_name,avg,hr,rbi,mvp // header row
 * <li>Miguel,Cabrera,.366,44,139,true // first document tuple
 * <li>Mike,Trout,.326,30,83,false // second document tuple
 * <li>Nick,Punto,.219,1,10,false // third document tuple
 * </ul>
 * <li>A JSON file consisting of a list of document records, where each record represents a document
 * and, optionally, external view content associated with that document. Each line consists of
 * exactly one record, so a document and its associated external view content must be on the same
 * line.
 * <li>For more details, refer to Information Center, section:
 * <code> Reference &gt; Text Analytics &gt; Input collection formats </code>.</li>
 * </ul>
 * 
 */
public class DocReader {

  /*
   * PUBLIC API
   */

  /**
   * Creates an iterator to iterate over the text value of each document in the input document
   * collection.
   * 
   * @param docFile a file or directory containing documents in one of the supported formats
   * @return an iterator that will return the text of the documents in the collection.
   * @throws TextAnalyticsException if a problem is encountered when loading or iterating through
   *         the document collection.
   */
  public static Iterator<String> makeDocTextItr(File docFile) throws TextAnalyticsException {

    final DocReader reader = new DocReader(docFile);
    final TextGetter getText = reader.getDocSchema().textAcc(Constants.DOCTEXT_COL);

    // Create a callback to return.
    return new Iterator<String>() {
      @Override
      public boolean hasNext() {
        return reader.hasNext();
      }

      @Override
      public String next() {
        Tuple docTup = reader.next();
        return getText.getVal(docTup).getText();
      }

      @Override
      public void remove() {
        reader.remove();
      }
    };
  }

  /**
   * Creates an iterator that returns a {@link com.ibm.avatar.algebra.datamodel.Pair} object
   * containing document name and document text for each document in the document collection
   * 
   * @param docFile a file or directory containing documents in one of the supported formats
   * @return an iterator that will return (label, text) pairs for the documents in the collection.
   * @throws TextAnalyticsException if a problem is encountered when loading or iterating through
   *         the document collection.
   */
  public static Iterator<Pair<String, String>> makePairsItr(File docFile)
      throws TextAnalyticsException {

    final DocReader reader = new DocReader(docFile);

    final FieldGetter<Text> getText = reader.getDocSchema().textAcc(Constants.DOCTEXT_COL);
    final FieldGetter<Text> getLabel = reader.getDocSchema().textAcc(Constants.LABEL_COL_NAME);

    // Create a callback to return.
    return new Iterator<Pair<String, String>>() {
      @Override
      public boolean hasNext() {
        return reader.hasNext();
      }

      @Override
      public Pair<String, String> next() {
        Tuple docTup = reader.next();

        docCount++;

        String docLabel;
        if (null == getLabel) {
          // No label field; use a sequence number.
          docLabel = String.format("Document %d", docCount);
        } else {
          docLabel = getLabel.getVal(docTup).getText();
        }
        String docText = getText.getVal(docTup).getText();
        return new Pair<String, String>(docLabel, docText);
      }

      @Override
      public void remove() {
        reader.remove();
      }

      int docCount = 0;
    };
  }

  /**
   * Creates an iterator that returns a {@link com.ibm.avatar.algebra.datamodel.Pair} object
   * containing document text and external views content for each document in the document
   * collection
   * 
   * @param docFileURI a file containing documents in one of the supported formats (currently just
   *        JSON)
   * @param docSchema the expected schema for document tuples
   * @param extNameVsSchema map of external view name pair vs external view schema. External view
   *        name pair consist of: (1) view's aql name as defined in
   *        <code>create external view</code> statement <br>
   *        (2) view's external name as defined in external_name clause
   * @return an iterator that will return (docTuple, externalViewContent) pairs for the documents in
   *         the collection.
   * @throws TextAnalyticsException if a problem is encountered when loading or iterating through
   *         the document collection.
   */
  public static Iterator<Pair<Tuple, Map<String, TupleList>>> makeDocandExternalPairsItr(
      String docFileURI, TupleSchema docSchema,
      Map<Pair<String, String>, TupleSchema> extNameVsSchema) throws TextAnalyticsException {
    final DocReader reader =
        new DocReader(FileUtils.createValidatedFile(docFileURI), docSchema, extNameVsSchema);

    // Create a callback to return.
    return new Iterator<Pair<Tuple, Map<String, TupleList>>>() {
      @Override
      public boolean hasNext() {
        return reader.hasNext();
      }

      @Override
      public Pair<Tuple, Map<String, TupleList>> next() {
        Tuple docTup = reader.next();
        Map<String, TupleList> extViewTups = reader.getExtViewTups();

        return new Pair<Tuple, Map<String, TupleList>>(docTup, extViewTups);
      }

      @Override
      public void remove() {
        reader.remove();
      }

    };

  }

  /**
   * Create a new DocReader object for reading documents as tuples using a default schema (text, or
   * text/label).
   * 
   * @param docFile File containing the input document collection, in one of the supported formats.
   * @throws TextAnalyticsException if a problem is encountered when loading or iterating through
   *         the document collection.
   */
  public DocReader(File docFile) throws TextAnalyticsException {
    try {
      this.docFile = docFile;
      scan = DocScanInternal.makeFileScan(docFile);

      // now that the scan has been determined, set the input collection input format
      setInputFormat();

      mt = new MemoizationTable(scan);
      if (scan.getOutputSchema().containsField(Constants.DOCTEXT_COL)) {
        textAcc = scan.getOutputSchema().textAcc(Constants.DOCTEXT_COL);
      }

      // this constructor expects an output schema with a text field, so throw an error if the
      // accessor is still null
      if (null == textAcc) {
        throw new RuntimeException("Scan returned NULL for text accessor!");
      }

      docSchema = scan.getDocSchema();

      if (scan.getOutputSchema().containsField(Constants.LABEL_COL_NAME)) {
        labelAcc = scan.getOutputSchema().textAcc(Constants.LABEL_COL_NAME);
      } else {
        labelAcc = null;
      }

      // not every output schema has a label field
    } catch (Throwable t) {
      throw TextAnalyticsException.convertToTextAnalyticsException(t, ExceptionType.RUNTIME_ERROR);
    }
  }

  /**
   * Create a new DocReader object for reading documents as tuples using the specified custom
   * schema.
   * 
   * @param docFile File containing the input document collection, in one of the supported formats.
   * @param docSchema Expected schema of the input document collection; for non-JSON document,
   *        supported document schema are [text Text], [text Text, label Text] and [label Text, text
   *        Text]
   * @param extNameVsSchema map of external view name pair versus external view schema. External
   *        view name pair consist of: (1) view's AQL name as defined in
   *        <code>create external view</code> statement <br>
   *        (2) view's external name as defined in external_name clause
   * @throws TextAnalyticsException if a problem is encountered when loading or iterating through
   *         the document collection.
   */
  public DocReader(File docFile, TupleSchema docSchema,
      Map<Pair<String, String>, TupleSchema> extNameVsSchema) throws TextAnalyticsException {
    this(docFile, docSchema, extNameVsSchema, Constants.DEFAULT_CSV_FIELD_SEPARATOR);
  }

  /**
   * Create a new DocReader object for reading documents as tuples using the specified custom schema
   * and custom field separator. Use for reading CSV files with a custom field separator.
   * 
   * @param docFile File containing the input document collection, in one of the supported formats.
   * @param docSchema Expected schema of the input document collection; for non-JSON document,
   *        supported document schema are [text Text], [text Text, label Text] and [label Text, text
   *        Text]
   * @param extNameVsSchema map of external view name pair versus external view schema. External
   *        view name pair consist of: (1) view's AQL name as defined in
   *        <code>create external view</code> statement <br>
   *        (2) view's external name as defined in external_name clause
   * @param separator the character to use as a field separator if reading from a CSV file.
   *        Optional.
   * @throws TextAnalyticsException if a problem is encountered when loading or iterating through
   *         the document collection.
   */
  public DocReader(File docFile, TupleSchema docSchema,
      Map<Pair<String, String>, TupleSchema> extNameVsSchema, char separator)
      throws TextAnalyticsException {
    try {
      this.docFile = docFile;
      this.extNameVsSchema = extNameVsSchema;
      this.csvFieldSeparator = separator;

      scan = DocScanInternal.makeFileScan(docFile, docSchema, extNameVsSchema, separator);
      setInputFormat();

      mt = new MemoizationTable(scan);
      this.docSchema = scan.getDocSchema();

      // set up the getters for the fields in the custom schema
      if (scan instanceof JsonFileScan) {
        inputFormat = DataFormat.JSON;
        extViewSchemas = ((JsonFileScan) scan).getExtViewSchemas();
        docGetters = ((JsonFileScan) scan).getDocAcc();
        allExtViewGetters = ((JsonFileScan) scan).getAllExtViewAcc();
      } else { // non-JSON input data collection
        // external views not supported for non-JSON inputs
        if (extNameVsSchema != null) {
          Log.info(
              "Warning: external view schema passed into DocReader, which does not support external views for this input data collection type.  This parameter will be ignored.");
        }

        if (scan instanceof CsvFileScan) {
          docGetters = ((CsvFileScan) scan).getDocAcc();
        }
      }

      // set the accessor for the 'text' field if that field exists
      if (scan.getOutputSchema().containsField(Constants.DOCTEXT_COL)) {
        textAcc = scan.getOutputSchema().textAcc(Constants.DOCTEXT_COL);
      } else {
        textAcc = null;
      }

      // set the label accessor if the 'label' field exists
      if (scan.getOutputSchema().containsField(Constants.LABEL_COL_NAME)) {
        labelAcc = scan.getOutputSchema().textAcc(Constants.LABEL_COL_NAME);
      } else {
        labelAcc = null;
      }
    } catch (Throwable t) {
      throw TextAnalyticsException.convertToTextAnalyticsException(t, ExceptionType.RUNTIME_ERROR);
    }

  }

  /**
   * Returns <code>true</code> if there is at least one more document to be read from the
   * collection.
   * 
   * @return <code>true</code> if there is at least one more document to be read from the
   *         collection.
   */
  public boolean hasNext() {
    return mt.haveMoreInput();
  }

  /**
   * Returns a tuple containing the next document from the document collection.
   * 
   * @return a {@link com.ibm.avatar.algebra.datamodel.Tuple} with two fields <b>label</b> and
   *         <b>text</b>, containing the values of the label and text of the next document in the
   *         input collection.
   */
  public Tuple next() {
    try {
      Tuple tup = scan.getNextDocTup(mt);

      // We used to override language here -- but it was redundant because the previous method ends
      // up
      // calling DocScanInternal.reallyEvaluate() which handles it -- eyhung

      return tup;
    } catch (ExceptionWithView e1) {
      // We have already caught an execution exception somewhere below this operator and attached it
      // a view name. So
      // just rethrow that exception.
      throw e1;
    } catch (Throwable e) {
      // Exception encountered during the execution of this operator. Attach view information and
      // rethrow it.
      throw new ExceptionWithView(e, Constants.DEFAULT_DOC_TYPE_NAME);
    }
  }

  /**
   * Reset the DocReader object.
   */
  public void remove() {
    mt.closeOutputs();
    scan = null;
    mt = null;
  }

  /**
   * Returns an accessor for the <code>text</code> field of the document schema. If no field with
   * that name exists, returns null.
   * 
   * @return Accessor {@link com.ibm.avatar.algebra.datamodel.FieldGetter} for the <code>text</code>
   *         field of the document schema, or null if that field does not exist
   * @deprecated As of v2.1.1, use {@link #getDocSchema()} to get the document schema and then use
   *             {@link com.ibm.avatar.algebra.datamodel.AbstractTupleSchema#spanAcc(String)} with
   *             parameter "text" to get the text field accessor.
   */
  @Deprecated
  public FieldGetter<Text> getTextAcc() {
    return textAcc;
  }

  /**
   * Returns accessor for the <code>label</code> field of the document schema. If no field with that
   * name exists, returns null.
   * 
   * @return accessor {@link com.ibm.avatar.algebra.datamodel.FieldGetter} for <code>label</code>
   *         field of the document schema, or null if that field does not exist
   * @deprecated As of v2.1.1, use {@link #getDocSchema()} to get the document schema and then use
   *             {@link com.ibm.avatar.algebra.datamodel.AbstractTupleSchema#spanAcc(String)} with
   *             parameter "label" to get the label field accessor.
   */
  @Deprecated
  public FieldGetter<Text> getLabelAcc() {
    return labelAcc;
  }

  /**
   * Returns accessor for a specific field of the document schema.
   * 
   * @param fieldName the field of the document schema to access
   * @return accessor {@link com.ibm.avatar.algebra.datamodel.FieldGetter} for
   *         <code>fieldName</code> field of the document schema
   * @deprecated As of v2.1.1, use {@link #getDocSchema()} to get the document schema and then use
   *             the appropriate accessor getter
   */
  @Deprecated
  public FieldGetter<?> getDocFieldAcc(String fieldName) {
    if (docGetters != null) {
      return docGetters.get(fieldName);
    } else {
      throw new RuntimeException("Document field accessors not initialized.");
    }
  }

  /**
   * Returns accessor for a specific field of external view <code>extViewName</code>
   * 
   * @param extViewName the external view schema to access
   * @param fieldName the field of the external view <code>extViewName</code> to access
   * @return Accessor {@link com.ibm.avatar.algebra.datamodel.FieldGetter} for
   *         <code>fieldName</code> field of external view <code>extViewName</code>
   * @deprecated As of v2.1.1, use {@link #getExternalViewSchema(String)} to get the external view
   *             schema for <code>extViewName</code> and then use the appropriate accessor
   */
  @Deprecated
  public FieldGetter<?> getExtViewFieldAcc(String extViewName, String fieldName) {
    if (allExtViewGetters != null) {
      return (allExtViewGetters.get(extViewName)).get(fieldName);
    } else {
      throw new RuntimeException("External view accessors not initialized.");
    }
  }

  /**
   * Returns schema for the document tuple returned by the {@link #next()} method.
   * 
   * @return schema for the document tuple that the {@link #next()} method returns.
   */
  public TupleSchema getDocSchema() {
    return docSchema;

  }

  /**
   * Returns schema for the requested external view
   * 
   * @param extViewName name of the external view whose schema is returned
   * @return schema for a specific external view.
   */
  public TupleSchema getExternalViewSchema(String extViewName) {
    if (extViewSchemas == null) {
      return null;
    } else {
      return extViewSchemas.get(extViewName);
    }
  }

  /**
   * Returns all fields for the document schema.
   * 
   * @return An array containing all the fields in the document schema
   */
  public String[] getDocSchemaFields() {
    if (scan != null) {
      return scan.getOutputSchema().getFieldNames();
    } else {
      throw new RuntimeException("Document scan not initialized.");
    }
  }

  /**
   * Returns all fields for the schema for the requested external view
   * 
   * @param extViewName the external view schema to parse
   * @return An array containing all the field in the specified external view schema
   */
  public String[] getExternalViewSchemaFields(String extViewName) {
    if (extViewSchemas == null) {
      return null;
    } else {
      return extViewSchemas.get(extViewName).getFieldNames();
    }
  }

  /**
   * Returns a map containing the external view tuples from the document collection. Currently only
   * documents in JSON format support reading of external views.
   * 
   * @return a {@link java.util.Map} from String to
   *         {@link com.ibm.avatar.algebra.datamodel.TupleList} containing the values of the
   *         external view tuples in this document collection
   */
  public Map<String, TupleList> getExtViewTups() {
    try {
      if (scan instanceof JsonFileScan) {
        return ((JsonFileScan) scan).getExtViewTups();
      }
      // for some reason, the non-JSON scan was called with non-null external views
      else if (this.extNameVsSchema != null) {
        throw new Exception(
            "External view tuples can only be retrieved from input document collections in the JSON format.");
      }
      // we allow non-JSON scans to call this with a null external view
      else {
        return null;
      }
    } catch (Exception e) {
      // Need to throw a RuntimeException to get around the Iterator
      // interface.
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns the number of documents in the input collection. This method iterates over the input
   * collection to determine its cardinality.
   * 
   * @return the number of documents in the input collection
   * @throws TextAnalyticsException if a problem is encountered when loading the input collection or
   *         when iterating through the document collection
   */
  // This is a linear algorithm that iterates over the input collection to determine cardinality.
  public int size() throws TextAnalyticsException {
    DocReader reader = null;
    try {
      /*
       * Create a separate instance of DocReader so that cursor positions of current instance's
       * iterator are not messed up
       */
      if (docFile.toString().endsWith(Constants.JSON_EXTENSION)) {
        reader = new DocReader(docFile, scan.getDocSchema(), this.extNameVsSchema);
      } else if (docFile.toString().endsWith(Constants.CSV_EXTENSION)) {
        reader = new DocReader(docFile, scan.getDocSchema(), null, this.csvFieldSeparator);
      } else {
        reader = new DocReader(docFile);
      }
      int docCount = 0;
      while (reader.hasNext()) {
        docCount++;
        reader.next();
      }
      return docCount;
    } finally {
      if (reader != null) {
        reader.remove();
        reader = null;
      }
    }
  }

  /**
   * Override the language of text fields of documents.
   * 
   * @param language language code to use for text fields of documents
   */
  public void overrideLanguage(LangCode language) {
    // DocReader no longer handles overriding of language, instead
    // propagate the language override code to the scan itself.
    if (scan != null) {
      scan.overrideLang(language);
    } else {
      throw new RuntimeException(
          "Attempted to set language override, but document scan not initialized.");
    }
  }

  /**
   * Return the type of input document collection format (e.g., JSON, CSV, etc.)
   * 
   * @return the type of input document collection format
   */
  public String getInputFormat() {
    return inputFormat.toString();
  }

  /**
   * Internal method to determine the input format corresponding to the document scan
   */
  private void setInputFormat() {
    if (scan == null) {
      throw new RuntimeException(
          "Attempted to determine input document collection format, but document scan not initialized.");
    }

    if ((scan instanceof ZipFileScan) || (scan instanceof TarFileScan)) {
      inputFormat = DataFormat.ARCHIVE;
    } else if ((scan instanceof DirDocScan)) {
      inputFormat = DataFormat.DIRECTORY;
    } else if ((scan instanceof DBDumpFileScan)) {
      inputFormat = DataFormat.DB2_DUMP;
    } else if ((scan instanceof JsonFileScan)) {
      inputFormat = DataFormat.JSON;
    } else if ((scan instanceof CsvFileScan)) {
      inputFormat = DataFormat.CSV;
    } else if ((scan instanceof TextFileScan)) {
      inputFormat = DataFormat.TEXT;
    } else {
      throw new RuntimeException(
          "Unknown document scan type, input document collection format could not be determined.");
    }
  }

  /*
   * INTERNAL IMPLEMENTATION
   */
  protected DocScanInternal scan;

  private TupleSchema docSchema;

  private MemoizationTable mt;

  @Deprecated
  // Get text accessor dynamically with getDocSchema().spanAcc()
  private FieldGetter<Text> textAcc;

  @Deprecated
  // Get label accessor dynamically with getDocSchema().spanAcc()
  private FieldGetter<Text> labelAcc;

  /**
   * Map of external view's AQL name (name defined thru 'create external view') vs Schema of the
   * view. For modular AQL's, the AQL name will be qualified with module name.
   */
  private Map<String, TupleSchema> extViewSchemas;

  @Deprecated
  // Get individual field accessors dynamically with getDocSchema().[type]Acc()
  private Map<String, FieldGetter<?>> docGetters;

  @Deprecated
  // Get individual field accessors dynamically with getExternalViewSchema(String).[type]Acc()
  private Map<String, Map<String, FieldGetter<?>>> allExtViewGetters;

  /**
   * Map of external view name pair vs external view schema. External view name pair consist of:
   * <br>
   * (1) view's aql name as defined in 'create external view ...' statement <br>
   * (2) view's external name as defined in external_name clause
   */
  private Map<Pair<String, String>, TupleSchema> extNameVsSchema;

  /**
   * References the file read by the current instance of DocReader
   */
  protected File docFile;

  /**
   * Supported types of input data formats. Can be used by consuming applications to set document
   * read policies.
   */
  private enum DataFormat {
    TEXT("Text file"), DIRECTORY("Directory of text files"), ARCHIVE(
        "Compressed archive of text files"), DB2_DUMP(
            "DB2/Derby export format"), CSV("Comma-separated-values format"), JSON("JSON format");

    private final String name;

    private DataFormat(String s) {
      name = s;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  /** the input data format of the documents being read */
  private DataFormat inputFormat = DataFormat.TEXT;

  /** the field separator used for the documents being read in CSV format */
  private char csvFieldSeparator = Constants.DEFAULT_CSV_FIELD_SEPARATOR;
}
