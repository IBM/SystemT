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
package com.ibm.avatar.algebra.scan;

import java.io.CharConversionException;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.base.MultiInputOperator;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.FieldSetter;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.oldscan.CsvFileScan;
import com.ibm.avatar.algebra.oldscan.DBDumpFileScan;
import com.ibm.avatar.algebra.oldscan.DirDocScan;
import com.ibm.avatar.algebra.oldscan.JsonFileScan;
import com.ibm.avatar.algebra.oldscan.TextFileScan;
import com.ibm.avatar.algebra.oldscan.ZipFileScan;
import com.ibm.avatar.algebra.util.lang.LangCode;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.api.exceptions.UnrecognizedFileFormatException;
import com.ibm.avatar.aql.tam.ModuleUtils;

/**
 * Superclass for operators that read documents from an external source. Sets up the appropriate
 * type information.
 * 
 */
public abstract class DocScanInternal extends MultiInputOperator {

  /**
   * @return common document schema used when the document has text and label columns
   */
  public static final TupleSchema createLabeledSchema() {
    String[] colnames = new String[] {Constants.DOCTEXT_COL, Constants.LABEL_COL_NAME};
    FieldType[] coltypes = new FieldType[] {FieldType.TEXT_TYPE, FieldType.TEXT_TYPE};

    TupleSchema ret = ModuleUtils.createSortedDocSchema(new TupleSchema(colnames, coltypes));
    return ret;
  }

  /**
   * @return common document schema used when the document has just a column 'text' of type 'Text'
   */
  public static final TupleSchema createOneColumnSchema() {
    String[] colnames = new String[] {Constants.DOCTEXT_COL};
    FieldType[] coltypes = new FieldType[] {FieldType.TEXT_TYPE};

    TupleSchema ret = ModuleUtils.createSortedDocSchema(new TupleSchema(colnames, coltypes));
    return ret;
  }

  /**
   * Accessor for putting the document text in place.
   */
  protected FieldSetter<String> docTextAcc;

  /**
   * List of accessors for reading the document text from the output tuples of the operator. <br />
   * For the default schema, there are one or two accessors, the text column and possibly the label
   * column. <br />
   * For JSON and CSV with custom schema, the list contains all columns of type Text
   */
  protected ArrayList<FieldGetter<Text>> docTextGetters = new ArrayList<FieldGetter<Text>>();

  /**
   * Schema of our document tuples. Can be changed by subclasses by using
   * {@link #DocScan(AbstractTupleSchema)}.
   */
  protected AbstractTupleSchema docSchema = createOneColumnSchema();

  /**
   * Language code to use for documents that this scan returns; if non-null, overrides any default
   * that the subclass puts into place.
   */
  private LangCode docLangOverride = null;

  protected DocScanInternal() {
    super();

    // Set up the view name for profiling purposes. Set up the name as "Document" not "DocScan" so
    // whenever there is a
    // problem with the format of the input file that causes an exception, the user gets an
    // ExceptionWithView with the
    // view Document not DocScan. setViewName (AOGParseTree.DOC_SCAN_BUILTIN);
    setViewName(Constants.DEFAULT_DOC_TYPE_NAME);
  }

  /** Constructor that also sets up a custom document schema. */
  protected DocScanInternal(AbstractTupleSchema docSchema) {
    this();

    this.docSchema = docSchema;
  }

  @Override
  public void checkEndOfInput(MemoizationTable mt) throws Exception {

    if (!scanStarted) {
      startScan(mt);
      setScanStarted();
    }
    if (mt.haveMoreInput()) {
      reallyCheckEndOfInput(mt);
    }
  }

  /**
   * Internal implementation of checkEndOfInput(); this class will guarantee that the scan is
   * started before calling this method.
   * 
   * @throws Exception
   */
  protected abstract void reallyCheckEndOfInput(MemoizationTable mt) throws Exception;

  /**
   * Default implementation -- creates a single-column anonymous schema. Override if your documents
   * have more attributes.
   */
  @Override
  protected AbstractTupleSchema createOutputSchema() {

    // The text type needs a pointer back to its schema, so we first
    // create the FieldType object, then create a schema out of it, then
    // modify the object.
    AbstractTupleSchema ret = docSchema;

    // Set up the default text accessor. Subclasses that override this
    // method should be sure to provide their own way of setting the
    // attributes of document tuples!
    docTextAcc = ret.textSetter(getTextColName());

    // Set up the accessor for reading back the Text values
    docTextGetters.add(ret.textAcc(getTextColName()));

    return ret;
  }

  /**
   * Override as needed.
   * 
   * @return name of the column containing the canonical document text
   */
  public String getTextColName() {
    return Constants.DOCTEXT_COL;
  }

  @Override
  public void reallyEvaluate(MemoizationTable mt, TupleList[] childResults) throws Exception {

    Tuple curDoc;
    try {
      curDoc = getNextDoc(mt);
    } catch (CharConversionException e) {
      throw new IOException("Input document text contains a byte sequence that does not "
          + "conform to the UTF-8 standard.  Please convert the document to "
          + "UTF-8 encoding.  The text editors vim and emacs can perform this " + "conversion.");
    }

    if (null != docLangOverride) {
      // SPECIAL CASE: A test case has requested that this scan return a
      // particular language code instead of the default.

      ArrayList<FieldGetter<Text>> allTextGetters = textGetters();

      if (null != allTextGetters) {
        // iterate through all text getters and override the language for each column
        for (FieldGetter<Text> getter : allTextGetters) {
          Text textObj = getter.getVal(curDoc);
          // Some fields may be null. For example, a document might not have a label field.
          if (null != textObj) {
            textObj.overrideLanguage(docLangOverride);
          }
        }
      }

      // END SPECIAL CASE
    }

    reallyCheckEndOfInput(mt);

    if (null == curDoc) {
      // SPECIAL CASE: Didn't get a document back. This should only happen
      // if the input came to an end in a way that couldn't be detected on
      // the last call to getNextDoc().
      if (mt.endOfInput()) {
        return;
      } else {
        throw new Exception("DocScan received null instead of a document");
      }
      // END SPECIAL CASE
    }

    // System.err.printf("--> Got doc '%s'\n", curDoc.getOid());

    addResultTup(curDoc, mt);
  }

  private boolean scanStarted = false;

  protected boolean getScanStarted() {
    return scanStarted;
  }

  public void setScanStarted() {
    if (scanStarted) {
      throw new RuntimeException("Started scan twice");
    }

    scanStarted = true;
  }

  protected boolean stripCR = false;

  /**
   * Returns the carriage return policy of this scan
   * 
   * @return true if the policy is to remove carriage returns before line feeds, false if not
   */
  protected boolean getStripCR() {
    return stripCR;
  }

  /**
   * Sets a flag that indicates that this document scan should attempt to strip carriage returns
   * (CR) if they occur directly before a line feed (LF).
   */
  public void setStripCR(boolean stripCR) {
    this.stripCR = stripCR;
  }

  private static final TupleList[] EMPTY_LIST = new TupleList[0];

  @Override
  protected TupleList[] prepareInputs(MemoizationTable mt) throws Exception {
    // We want to connect to the database and to execute the query at query
    // runtime, not at query plan creation time.
    if (!scanStarted) {
      startScan(mt);
      setScanStarted();
    }

    return EMPTY_LIST;
  }

  protected abstract void startScan(MemoizationTable mt) throws Exception;

  /**
   * Function that grabs the next document in the scan.
   * 
   * @param mt
   */
  protected abstract Tuple getNextDoc(MemoizationTable mt) throws Exception;

  public final ArrayList<FieldGetter<Text>> textGetters() {
    // Make sure that output schema has been created.
    getOutputSchema();

    return docTextGetters;
  }

  /**
   * Convenience method for getting at document tuples. Equivalent to
   * {@code getNext().getElemAtIndex(0)}.
   * 
   * @return the next document tuple that this scan would normally return
   */
  public Tuple getNextDocTup(MemoizationTable mt) throws Exception {
    return getNext(mt).getElemAtIndex(0);
  }

  /**
   * DocScan generator for fixed doc schema
   * 
   * @param docsFile
   * @return
   * @throws Exception
   */
  public static DocScanInternal makeFileScan(File docsFile) throws Exception {
    return makeFileScan(docsFile, null, null, Constants.DEFAULT_CSV_FIELD_SEPARATOR);
  }

  /**
   * Create the appropriate file scanner based on the file extension
   * 
   * @param docsFile file or directory containing documents
   * @param docSchema schema of the document tuple returned by the file scan operator; required to
   *        create a JSON file scan, optional for all other file scans viz directory, zip, del etc
   * @param extViews map of external view name pair vs external view schema(optional). External view
   *        name pair consist of: (1) view's aql name as defined in 'create external view ...'
   *        statement <br>
   *        (2) view's external name as defined in external_name clause
   * @param separator character to use as a field separator in a CSV file
   * @return an instance of a {@link DocScanInternal} subclass that knows how to read the indicated
   *         type of file
   */
  public static DocScanInternal makeFileScan(File docsFile, TupleSchema docSchema,
      Map<Pair<String, String>, TupleSchema> extViews, char separator) throws Exception {
    String name = docsFile.getName();

    boolean isJsonFile = name.matches(".*\\" + Constants.JSON_EXTENSION);
    boolean isCsvFile = name.matches(".*\\" + Constants.CSV_EXTENSION);
    boolean isTextFile = name.matches(".*(\\.txt|\\.html|\\.htm|\\.xhtml|\\.xml)");

    if (null == docSchema) {
      // Document schema is required to create a JSON document scan
      if (isJsonFile) {
        throw new TextAnalyticsException(
            "Error while creating document reader for specified file '%s'. Document schema is required to create a JSON file reader. Specify a valid document schema.",
            docsFile);
      } else if (isCsvFile) {
        // do nothing, the reader will check to see if the header contains the fields of the default
        // schema
      } else {
        // do nothing, no declared doc schema is normal for most input data collection types
      }
    } else { // docSchema is defined
      if ((!isJsonFile) && (!isCsvFile)) {
        // For non-JSON and non-CSV documents, the only supported document schema are the default
        // schema [text Text] and
        // [text Text, label Text]
        validateSchemaAsDefault(docSchema);
      }
    }

    if ((!docsFile.isDirectory()) && isJsonFile) {
      return new JsonFileScan(docsFile, docSchema, extViews);
    } else if ((!docsFile.isDirectory()) && isCsvFile) {
      return new CsvFileScan(docsFile, docSchema, separator);
    } else if (docsFile.isDirectory()) {
      if (null != docSchema)
        return new DirDocScan(docsFile, docSchema);
      else
        return new DirDocScan(docsFile);
    } else if (name.matches(".*(\\.tar\\.gz|\\.tgz|\\.tar)")) {
      // Don't reference TarFileScan directly, because we want to avoid
      // creating unnecessary dependencies on ant.jar.
      // There are two constructors, one with only the File, and one
      // with both the file and the schema.
      // Use reflection to find the right constructor.
      Constructor<?> cs[] =
          Class.forName("com.ibm.avatar.algebra.oldscan.TarFileScan").getConstructors(); // get all
                                                                                         // the
                                                                                         // constructors
      Constructor<?> c1 = null;
      Constructor<?> c2 = null;
      for (int i = 0; i < cs.length; i++) { // find the constructors with
                                            // 1 and 2 parms
        if (1 == (cs[i]).getParameterTypes().length)
          c1 = cs[i];
        else if (2 == (cs[i].getParameterTypes().length))
          c2 = cs[i];
      }
      if (null != docSchema) {
        return (DocScanInternal) c2.newInstance(docsFile, docSchema);

      } else {
        return (DocScanInternal) c1.newInstance(docsFile);
        // Old code:
        // return new TarFileScan(docsFile);
      }
    } else if (name.matches(".*\\.del")) {
      if (null != docSchema)
        return new DBDumpFileScan(docsFile, docSchema);
      else
        return new DBDumpFileScan(docsFile);
    } else if (name.matches(".*\\.zip")) {
      if (null != docSchema)
        return new ZipFileScan(docsFile, docSchema);
      else
        return new ZipFileScan(docsFile);
    } else if (isTextFile) {
      if (null != docSchema)
        return new TextFileScan(docsFile, docSchema);
      else
        return new TextFileScan(docsFile);
    } else {
      throw new UnrecognizedFileFormatException(docsFile);
    }
  }

  public TupleSchema getDocSchema() {
    return (TupleSchema) getOutputSchema();
  }

  /**
   * Override whatever language information comes from the scan source and put the specified
   * language code into place for all documents returned by the scan. This method is intended to be
   * used for TESTING ONLY. This method should be called during initialization; it applies to all
   * threads that may use the scan.
   * 
   * @param docLang language code to use for all documents returned
   */
  public void overrideLang(LangCode docLang) {
    docLangOverride = docLang;
  }

  /**
   * Array of default supported document schemas -- used for all input collections that are not JSON
   * or CSV
   */
  private static final AbstractTupleSchema[] defaultSupportedSchemas =
      new AbstractTupleSchema[] {createOneColumnSchema(), createLabeledSchema()};

  /**
   * This method validates the specified document schema for non-JSON/non-CSV input collection. It
   * throws a validation error for unsupported schema. Here is the list of supported schemas for
   * non-JSON/non-CSV files: (1)[text Text], (2) [text Text, label Text] and [label Text, text
   * Text].
   * 
   * @param docSchema expected output schema of the operator
   * @throws TextAnalyticsException if the specified schema is not supported for non-JSON/non-CSV
   *         input collection
   */
  private static void validateSchemaAsDefault(AbstractTupleSchema docSchema)
      throws TextAnalyticsException {
    boolean isSupportedSchema = false;

    for (int i = 0; i < defaultSupportedSchemas.length; i++) {
      if (docSchema.equals(defaultSupportedSchemas[i])) {
        isSupportedSchema = true;
        break;
      }
    }

    if (!isSupportedSchema) {
      throw new TextAnalyticsException(
          "Specified document schema '%s' is not supported for this input collection. The supported document schemas for non-JSON/non-CSV files are: %s.",
          docSchema, Arrays.toString(defaultSupportedSchemas));
    }
  }

  /**
   * Reads bytes from the input stream and strips out carriage returns (CR) before newline
   * characters (LF) if that flag is set
   * 
   * @param in the reader object for the input stream
   * @return the document text
   * @throws IOException
   */
  protected String getDocText(InputStreamReader in) throws IOException {
    /** Buffer for reading characters from the input stream */
    final int BUFSZ = 10000;
    final char[] buf = new char[BUFSZ];

    /** builds the document text string that's returned */
    StringBuilder sb = new StringBuilder();

    int nread;

    if (stripCR) {
      boolean lastBufEndsinCR = false;

      // remove carriage returns before newline characters
      while ((nread = in.read(buf)) > 0) {
        // if the previous buffer ended with a CR, add the CR only if the first character in this
        // buffer read is not a
        // LF.
        if (lastBufEndsinCR == true) {
          if (buf[0] != '\n') {
            sb.append('\r');
            lastBufEndsinCR = false;
          }
        }

        for (int i = 0; i < nread; i++) {
          if (buf[i] == '\r') {
            if (i == nread - 1) {
              // CR is last character in buffer, handle on next read
              lastBufEndsinCR = true;
              continue;
            } else if (buf[i + 1] == '\n') {
              // CR followed by LF, do not append the CR
              continue;
            }
          }

          sb.append(buf[i]);
        }
      }

      // edge case: final character is a CR, so add it because it's not followed by a LF
      if (lastBufEndsinCR == true) {
        sb.append('\r');
      }
    } else {
      while (-1 != (nread = in.read(buf))) {
        sb.append(buf, 0, nread);
      }
    }

    // the document text
    return sb.toString();
  }
}
