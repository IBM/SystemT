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
package com.ibm.avatar.algebra.oldscan;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.FieldSetter;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.TextSetter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.scan.DocScanInternal;
import com.ibm.avatar.algebra.util.document.CsvFileReader;
import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.exceptions.DocReaderException;
import com.ibm.avatar.logging.Log;

/**
 * Operator that reads documents from files in CSV with header format. The first row is a header
 * row, defining the columns and their names. This header row is assumed not to have any duplicates.
 * No external view information is handled.
 * 
 */
public class CsvFileScan extends DocScanInternal {

  private final boolean debug = false;

  /** The file to scan */
  private final File csvFile;

  /** Record counter for clearer error messages */
  private int recordCount = 1;

  /** Map of all setters associated with the doc tuple schema */
  protected Map<String, FieldSetter<?>> docSetters;

  /** Map of all getters associated with the doc tuple schema */
  protected Map<String, FieldGetter<?>> docGetters;

  /** Reader that does most of the work in parsing the CSV file */
  private final CsvFileReader csvReader;

  /**
   * Convenience constructor for reading from just one file or directory.
   * 
   * @throws FileNotFoundException
   */
  public CsvFileScan(String csvFileName, TupleSchema docSchema) throws Exception {
    this(FileUtils.createValidatedFile(csvFileName), docSchema);
  }

  /**
   * Constructor for scanning a file in CSV format with default field separator
   * 
   * @param csvFile name of file in CSV format
   * @param docSchema expected doc schema
   * @throws Exception
   */
  public CsvFileScan(File csvFile, TupleSchema docSchema) throws Exception {
    this(csvFile, docSchema, Constants.DEFAULT_CSV_FIELD_SEPARATOR);
  }

  /**
   * Constructor for scanning a file in CSV format
   * 
   * @param csvFile name of file in CSV format
   * @param docSchema expected doc schema
   * @param separator character to use as field separator
   * @throws Exception
   */
  public CsvFileScan(File csvFile, TupleSchema docSchema, char separator) throws Exception {
    this.csvFile = csvFile;
    if (docSchema == null) {
      this.docSchema = DocScanInternal.createLabeledSchema();
    } else {
      this.docSchema = docSchema;
    }

    if (!csvFile.exists()) {
      throw new FileNotFoundException("CSV file " + this.csvFile.getPath() + " not found");
    }

    csvReader = new CsvFileReader(csvFile, this.docSchema, separator);

  }

  @Override
  /**
   *
   * Gets the next document tuple to process. Also, sets the {@link externalViews} variable to the
   * external views of this tuple
   *
   * @return next document tuple
   */
  protected Tuple getNextDoc(MemoizationTable mt) throws Exception {

    // Get the next document record
    String[] sortedRecord = csvReader.getNextTup();

    recordCount++;

    Tuple docTup = parseSortedRecord(sortedRecord);
    return docTup;

  }

  @Override
  protected void startScan(MemoizationTable mt) throws Exception {
    // Scan was started in the constructor.
  }

  @Override
  protected void reallyCheckEndOfInput(MemoizationTable mt) throws Exception {
    if (csvReader.endOfInput()) {
      mt.setEndOfInput();
    }

  }

  @SuppressWarnings("unchecked")
  @Override
  protected AbstractTupleSchema createOutputSchema() {
    docSchema.setName(Constants.DEFAULT_DOC_TYPE_NAME);

    docSetters = new HashMap<String, FieldSetter<?>>();
    docGetters = new HashMap<String, FieldGetter<?>>();

    cacheAccessors(docSetters, docGetters, docSchema);

    // Initialize text field getters and setters. Subclasses that override this
    // method should be sure to provide their own way of setting the
    // attributes of document tuples!

    // iterate through all getters and test for type Text.
    // If a TextGetter is found, add to the list of text getters
    String[] schemaFieldNames = docSchema.getFieldNames();
    for (String fieldName : schemaFieldNames) {
      FieldType fieldType = docSchema.getFieldTypeByName(fieldName);
      if (fieldType.getIsText()) {
        docTextGetters.add((FieldGetter<Text>) docGetters.get(fieldName));
      }
    }

    // initialize the default schema's setter
    if (docSchema.containsField(Constants.DOCTEXT_COL)) {
      docTextAcc = docSchema.textSetter(Constants.DOCTEXT_COL);
    } else {
      docTextAcc = null;
    }

    return docSchema;
  }

  /**
   * Helper function to iterate through each field of the passed-in schema, create an accessor for
   * it, and then cache it for later retrieval
   * 
   * @param setters The corresponding setter cache (document or external view)
   * @param getters The corresponding getter cache
   * @param schema The schema to generate accessors for
   */
  private void cacheAccessors(Map<String, FieldSetter<?>> setters,
      Map<String, FieldGetter<?>> getters, AbstractTupleSchema schema) {

    // get the name of the schema and identify whether we are working on a doc or external view
    // schema
    String schemaName = schema.getName();

    String[] schemaFieldNames = schema.getFieldNames();

    // iterate through each field of the schema and cache an accessor for it
    for (String fieldName : schemaFieldNames) {
      FieldType fieldType = schema.getFieldTypeByName(fieldName);
      if (fieldType.getIsIntegerType()) {
        setters.put(fieldName, schema.intSetter(fieldName));
        getters.put(fieldName, schema.intAcc(fieldName));
      } else if (fieldType.getIsFloatType()) {
        setters.put(fieldName, schema.floatSetter(fieldName));
        getters.put(fieldName, schema.floatAcc(fieldName));
      } else if (fieldType.getIsText()) {
        setters.put(fieldName, schema.textSetter(fieldName));
        getters.put(fieldName, schema.textAcc(fieldName));
      }
      // Boolean is only valid for document schemas, not external view schemas
      else if (fieldType.getIsBooleanType()) {
        setters.put(fieldName, schema.genericSetter(fieldName, FieldType.BOOL_TYPE));
        getters.put(fieldName, schema.genericGetter(fieldName, FieldType.BOOL_TYPE));
      } else {
        throw new RuntimeException(
            makeErrorHeader() + String.format("Invalid type '%s' in field '%s' of doc schema %s",
                fieldType.toString(), fieldName, schemaName));
      }
    }
  }

  /**
   * Reads doc tuple information from the sorted CSV record.
   * 
   * @param record The record with fields corresponding to a doc tuple, in the order of the expected
   *        schema
   * @return a document tuple
   * @throws IOException
   */
  private Tuple parseSortedRecord(String[] record) throws DocReaderException {

    Tuple tup = docSchema.createTup();

    // populate the document tuple according to the input schema
    // if the document record does not contain a field corresponding to
    // the input schema, throw an exception
    for (int i = 0; i < docSchema.size(); i++) {
      String fieldName = docSchema.getFieldNameByIx(i);
      FieldType ft = docSchema.getFieldTypeByName(fieldName);

      if (null == record[i]) {
        throw new DocReaderException(makeErrorHeader(), String.format(
            "CSV record at this line does not contain an attribute for the required field named '%s'. Provide a non-null value of type '%s' for this required field.",
            fieldName, ft.getIsText() ? FieldType.TEXT_TYPE.getTypeName() : ft.getTypeName()));
      }
      populateTupleWithObject(tup, docSchema, fieldName, docSetters.get(fieldName), record[i]);
    }

    if (debug) {
      Log.debug("Doc tuple created with contents:");
      Log.debug(tup.toString());
    }

    return tup;
  }

  /**
   * Parses an object correctly according to the tuple schema, and stores it in the passed-in tuple.
   * <br/>
   * 
   * @param tup The tuple to be modified
   * @param schema The tuple schema to use
   * @param key The schema field corresponding to this object
   * @param entry The value to be parsed according to the schema
   * @throws DocReaderException
   */
  @SuppressWarnings("unchecked")
  private void populateTupleWithObject(Tuple tup, AbstractTupleSchema schema, String key,
      FieldSetter<?> accessor, String entry) throws DocReaderException {

    // Figure out the expected type
    FieldType expectedFieldType = schema.getFieldTypeByName(key);

    if (entry == null) {
      throw new DocReaderException(makeErrorHeader(), String.format(
          "%s CSV record at this line does not contain an attribute for the required field named '%s'. Provide a non-null value of type '%s' for this required field.",
          makeErrorHeader(), key, expectedFieldType.getIsText() ? FieldType.TEXT_TYPE.getTypeName()
              : expectedFieldType.getTypeName()));
    }

    if (accessor == null) {
      throw new DocReaderException(makeErrorHeader(), String.format(
          "Unable to put CSV record into field '%s' of schema '%s' because schema field accessor does not exist.  "
              + "Check to see if field is part of the expected schema.",
          key, schema.getName()));
    }

    // Parse the object appropriately with the expected field type
    try {
      // For the field type: Integer, Float and Boolean, use the trimmed version of the entry
      String trimmedEntry = entry.trim();

      if (expectedFieldType.getIsIntegerType()) {
        ((FieldSetter<Integer>) accessor).setVal(tup, Integer.parseInt(trimmedEntry));
      } else if (expectedFieldType.getIsBooleanType()) {
        ((FieldSetter<Object>) accessor).setVal(tup, Boolean.valueOf(trimmedEntry));
      } else if (expectedFieldType.getIsFloatType()) {
        ((FieldSetter<Float>) accessor).setVal(tup, Float.parseFloat(trimmedEntry));
      } else if (expectedFieldType == FieldType.SPAN_TYPE) {
        throw new DocReaderException(makeErrorHeader(),
            String.format("Reader currently does not support reading field '%s.%s' of type Span.",
                schema.getName(), key));
      } else {
        ((TextSetter) accessor).setVal(tup, entry);
      }
    } catch (ClassCastException cce) {
      throw new DocReaderException(makeErrorHeader(),
          String.format(
              "Could not cast value '%s' of field '%s' of schema '%s' to expected type '%s'.",
              entry, key, schema.getName(), expectedFieldType.toString()));
    }

  }

  /**
   * @return the getter map for the fields of the document schema
   */
  public Map<String, FieldGetter<?>> getDocAcc() {
    return docGetters;
  }

  /**
   * Utility method to print out file name and line number for clear error messages
   * 
   * @return
   */
  public String makeErrorHeader() {
    return String.format("file '%s', line %d:  ", csvFile.getName(), recordCount);
  }

}
