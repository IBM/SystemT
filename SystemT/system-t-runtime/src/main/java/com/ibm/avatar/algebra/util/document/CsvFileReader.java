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
package com.ibm.avatar.algebra.util.document;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.util.file.FileOperations;
import com.ibm.avatar.api.exceptions.InvalidTableEntryException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;

/**
 * Utility class to read table entries from a file in MS-Excel style CSV file format with header.
 * This class assumes the following regarding the format of csv file: <br>
 * 1)The first line of the csv will be interpreted as header, and the header should contain all the
 * fields from the given schema. <br>
 * 2)All entries in the csv file should adhere to the given schema (contain value for all the fields
 * in table schema, and of the correct data type). That is, if the schema is { id Integer, firstName
 * Text, lastName Text } then the csv entries should look something like this: <br>
 * 123,Dharmesh,Jain <br>
 * 321,Laura,Chiticariu
 * 
 */

public class CsvFileReader {
  /** contents of the CSV file */
  private CSVReader in;

  /** the name and line number of the CSV file we are reading, for error messages */
  private final String fileName;
  private int lineNum;

  /** the current record being parsed -- only keep one record in memory at a time */
  private String[] currentRecord;

  /** column names */
  private final ArrayList<String> header;

  /** expected schema */
  private final AbstractTupleSchema csvSchema;

  /** BOM character sequence for UTF-8 encoding */
  private static char BOM_UTF8 = '\ufeff';

  /**
   * Constructor -- sets up the CSV file for parsing
   * 
   * <pre>
   * CSV file must be in the following format:
   * 
   * first_name,last_name,avg,hr,rbi,mvp      // header row
   * Miguel,Cabrera,.366,44,139,true          // first document tuple
   * Mike,Trout,.326,30,83,false              // second document tuple
   * Nick,Punto,.219,1,10,false               // third document tuple
   * <all other document tuples ...>
   * </pre>
   * 
   * Whitespace is allowed. Having a comma at the end of a line is not expected.
   * 
   * @param csvFile handle to file in CSV (with header) format.
   * @param csvSchema expected doc schema
   * @param separator character used to separate fields
   * @throws Exception
   */
  public CsvFileReader(File csvFile, AbstractTupleSchema csvSchema, char separator) throws Exception

  {
    
    CSVParser csvParser =
        new CSVParserBuilder().withSeparator(separator).withIgnoreQuotations(false).build();

    in =
        new CSVReaderBuilder(new InputStreamReader(new FileInputStream(csvFile), "UTF-8"))
            .withCSVParser(csvParser).build();

    fileName = csvFile.getName();
    lineNum = 1;

    this.csvSchema = csvSchema;

    String[] headerArray = in.readNext();

    // exit gracefully if the file is empty
    if (headerArray == null) {
      in.close();
      in = null;
      header = null;
    } else {

      // SPECIAL CASE: trim BOM character from the beginning of the first header
      stripBOMFromHeader(headerArray);

      // trim leading and trailing whitespace from each column
      for (int i = 0; i < headerArray.length; i++) {
        headerArray[i] = headerArray[i].trim();
      }

      // store the trimmed header columns as an ArrayList for later reference and validate
      header = new ArrayList<String>(Arrays.asList(headerArray));
      validateHeaderFlexibleOrder();

      readNextLine();
    }

  }

  /**
   * Validate and sort the next document record for parsing. The next doc record is set in the
   * initializer and also in calls to readNextLine(). We need to load the next line immediately
   * after getting a doc Tuple so we can identify whether the reader is done.
   * 
   * @return a Tuple containing the contents of the next document record.
   */
  public String[] getNextTup() throws TextAnalyticsException {

    try {

      // shouldn't be necessary but just in case
      if (currentRecord == null) {
        in = null;
      }

      // skip current line if current line does not contain any CSV content
      else if (currentRecord.length == 0) {
        // load next tuple
        readNextLine();

        // attempt to parse & return the tuple we read above
        return getNextTup();
      }

      if (endOfInput()) {
        return null;
      }

      // We have a CSV record, check to see whether it conforms with the header and schema
      // and sort it in order of the schema fields
      String[] sortedRecord = validateAndSequenceColumns();

      // we need to load the next line now, so that we can check for end of input and let the reader
      // know it's done reading tuples
      readNextLine();

      // parsing is handled in the scanner
      return sortedRecord;
    } catch (IOException e) {
      throw new TextAnalyticsException(makeErrorHeader()
          + "Could not parse this line as a CSV record : \n" + Arrays.toString(currentRecord));
    } finally {

    }
  }

  /**
   * Checks to see if there is another document tuple and if so, loads it into memory. If not, mark
   * this reader as having reached end of input.
   * 
   * @return whether we have another document tuple to parse
   */
  private void readNextLine() throws IOException {
    if (in != null) {

      try {

        lineNum++;

        if ((currentRecord = in.readNext()) != null) {
          return;
        } else {
          in.close();
          in = null;
          return;
        }
      } catch (IOException e) {
        throw new IOException(makeErrorHeader() + e.getMessage());
      } catch (CsvValidationException e) {
        throw new IOException(makeErrorHeader() + e.getMessage());
      }
      
    } else {

      // we've already finished processing this document
      return;
    }
  }

  /**
   * @return true if we have reached the end of the input file
   */
  public boolean endOfInput() {
    if (in == null) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * Utility method to print out file name and line number for clear error messages
   * 
   * @return
   */
  public String makeErrorHeader() {
    return String.format("file '%s', line %d: ", fileName, lineNum);
  }

  /**
   * validate that the CSV file's columns is a superset of the required document columns
   * 
   * @throws IOException
   */
  private void validateHeaderFlexibleOrder() throws TextAnalyticsException {

    String[] fieldNames = csvSchema.getFieldNames();

    if (null == header) {
      throw new TextAnalyticsException("The CSV file '%s' is missing a required header row.",
          fileName);
    }

    String incompatibleSchemaErr = String.format(
        "The header of CSV file '%s' does not contain columns required by the document schema.\nSchema of CSV header: %s.\nRequired document schema: %s.",
        fileName, header.toString(), Arrays.toString(fieldNames));

    // if the header contains fewer columns than required, throw an exception
    // extra columns are okay
    if (header.size() < fieldNames.length) {
      throw new TextAnalyticsException(incompatibleSchemaErr);
    }

    // for each required column, check to see if it exists in the header row
    for (int i = 0; i < fieldNames.length; i++) {
      String field = fieldNames[i];

      if (false == header.contains(field)) {
        throw new TextAnalyticsException(incompatibleSchemaErr);
      }
    }
  }

  /**
   * Validates each entry in the in-memory record, and returns a list of column entries sequenced in
   * the order of the fields in the required document schema
   */
  private String[] validateAndSequenceColumns() throws TextAnalyticsException {
    String[] fieldNames = csvSchema.getFieldNames();

    // The current record must contain all columns of the required document schema, so if it's
    // smaller than the required
    // schema, there will be a problem. larger is okay, as it is allowed to contain extra columns
    if (currentRecord.length < csvSchema.size()) {
      String errMsg = String.format("does not adhere to required document schema %s", csvSchema);
      throw new TextAnalyticsException(makeErrorHeader() + errMsg);
    }

    String[] sortedRecord = new String[csvSchema.size()];

    // Iterate thru all the fields of the required doc schema, validate values of type Integer and
    // Float,
    // and copy the validated value to the correct expected field position
    for (int i = 0; i < fieldNames.length; i++) {
      FieldType fieldType = csvSchema.getFieldTypeByName(fieldNames[i]);

      if (header.contains(fieldNames[i])) {
        String entry = currentRecord[header.indexOf(fieldNames[i])];

        if (entry == null) {
          String errMsg = String.format("Value of field '%s' is null.", fieldNames[i]);
          throw new TextAnalyticsException(makeErrorHeader() + errMsg);
        }

        // Validate after removing leading/trailing white-spaces
        String trimmedEntry = entry.trim();

        if (fieldType.getIsIntegerType()) {
          try {
            Integer.parseInt(trimmedEntry);
          } catch (NumberFormatException e) {
            String errMsg = String.format(
                "value of Integer field '%s' is '%s'.  This value cannot be parsed as a 32-bit integer",
                fieldNames[i], entry);
            throw new TextAnalyticsException(makeErrorHeader() + errMsg);
          }
        } else if (fieldType.getIsFloatType()) {
          try {
            Float.parseFloat(trimmedEntry);
          } catch (NumberFormatException e) {
            String errMsg = String.format(
                "value of Float field '%s' is '%s'.  This value cannot be parsed as a single-precision floating point number.",
                fieldNames[i], entry);
            throw new TextAnalyticsException(makeErrorHeader() + errMsg);
          }
        } else if (fieldType.getIsBooleanType()) {
          // for now, all strings can be parsed as a boolean -
          // only "true" returns true, and all other strings returning false
          if (trimmedEntry.equalsIgnoreCase("true") || trimmedEntry.equalsIgnoreCase("false")) {
            Boolean.parseBoolean(trimmedEntry);
          } else {
            String errMsg = String.format(
                "value of Boolean field '%s' is '%s', valid values are 'true' or 'false'",
                fieldNames[i], entry);
            throw new TextAnalyticsException(makeErrorHeader() + errMsg);
          }
        } else if (fieldType.getIsText()) {
          // do nothing, obviously all Strings are valid
        } else {
          String errMsg =
              String.format("field '%s' is of unsupported type %s.", fieldNames[i], fieldType);
          throw new TextAnalyticsException(makeErrorHeader() + errMsg);
        }

        // copy the entry to a new, sorted record
        sortedRecord[i] = entry;
      } else {
        String errMsg = String.format(
            "required column %s not found in header row -- header was not properly validated.",
            fieldNames[i]);
        throw new TextAnalyticsException(makeErrorHeader() + errMsg);
      }

    }

    return sortedRecord;

  }

  /**
   * STATIC METHODS below -- for reading external tables from CSV file. Not used for document tuple
   * reading.
   */

  /**
   * This method reads the table entries from the specified CSV file. The method returns the table
   * entries in the form of ArrayList of ArrayList<String>, where each entry in the outer list is a
   * table entry. The method will also throw exception for CSV header or entries, incompatible with
   * schema of the declared external table.
   * 
   * @param tableFileURI uri to the csv file containing table entries
   * @param tableSchema schema of the external table
   * @return ArrayList of ArrayList<String> where each entry in the outer list is a table entry
   * @throws Exception
   */
  public static ArrayList<ArrayList<String>> readTable(String tableFileURI, TupleSchema tableSchema)
      throws Exception {
    InputStream tableStream = null;
    CSVReader csvReader = null;

    try {
      tableStream = FileOperations.getStream(tableFileURI);

      // Create an instance of CSV reader, to read from the table stream
      csvReader =
          new CSVReader(new InputStreamReader(new BufferedInputStream(tableStream), "UTF-8"));

      // First line contains the header
      String[] header = csvReader.readNext();

      // SPECIAL CASE: trim BOM character from the beginning of the first header
      stripBOMFromHeader(header);

      validateHeaderExactOrder(tableFileURI, header, tableSchema.getFieldNames());

      // if here, csv file in hand has correct headers; let's create array list for table entries.
      ArrayList<ArrayList<String>> tuples = new ArrayList<ArrayList<String>>();

      String[] tableEntry = null;
      int entryCounter = 1;

      try {
        while (null != (tableEntry = csvReader.readNext())) {
          validateTableEntry(tableEntry, tableSchema);
          tuples.add(new ArrayList<String>(Arrays.asList(tableEntry)));
          entryCounter++;
        }
      } catch (Exception e) {
        throw new InvalidTableEntryException("In the csv file '%s', on line %d, %s.", tableFileURI,
            entryCounter, e.getMessage());
      }
      return tuples;
    } finally {
      if (null != csvReader) {
        csvReader.close();
      }
      if (null != tableStream) {
        tableStream.close();
      }
    }

  }

  /**
   * Static method to validate the given table entry against the schema of the table; for invalid
   * entry this method throws exception.
   * 
   * @param tableEntry string array containing table entry; each element if the array contains value
   *        for a field
   * @param tableSchema schema of the table against which validation is performed.
   * @throws Exception throws exception for invalid entry.
   */
  public static void validateTableEntry(String[] tableEntry, TupleSchema tableSchema)
      throws Exception {
    String[] fieldNames = tableSchema.getFieldNames();
    if (tableEntry.length != tableSchema.size()) {
      String errMsg = String.format("does not adhere to table schema %s", tableSchema);
      throw new Exception(errMsg);
    }

    // Iterate thru all the fields of an entry, and validate values of type fields
    for (int i = 0; i < fieldNames.length; i++) {
      FieldType fieldType = tableSchema.getFieldTypeByName(fieldNames[i]);
      if (fieldType.equals(FieldType.INT_TYPE)) {
        try {
          Integer.parseInt(tableEntry[i]);
        } catch (NumberFormatException e) {
          String errMsg = String.format("field value '%s' in column %d is not a valid Integer",
              fieldNames[i], i + 1);
          throw new Exception(errMsg);
        }
      } else if (fieldType.equals(FieldType.FLOAT_TYPE)) {
        try {
          Float.parseFloat(tableEntry[i]);
        } catch (NumberFormatException e) {
          String errMsg = String.format("field value '%s' in column %d is not a valid Float",
              fieldNames[i], i + 1);
          throw new Exception(errMsg);
        }
      } else if (fieldType.equals(FieldType.BOOL_TYPE)) {
        if ((tableEntry[i].equals("true") || tableEntry[i].equals("false")) == false) {
          String errMsg = String.format("field value '%s' in column %d is not a valid Boolean",
              fieldNames[i], i + 1);
          throw new Exception(errMsg);
        }
      }
    }
  }

  /**
   * Method to validate header in the CSV file against the given table schema. Each column in the
   * header row is expected to match and be in the same exact order as the columns in the table
   * schema.
   * 
   * @param tableFileURI URI of the file containing external table data
   * @param header expected header columns
   * @param fieldNames columns of the table schema
   * @throws Exception
   */
  private static void validateHeaderExactOrder(String tableFileURI, String[] header,
      String[] fieldNames) throws Exception {

    if (null == header) {
      throw new TextAnalyticsException("The CSV file '%s' is missing the required header.",
          tableFileURI);
    }

    String incompatibleSchemaErr = String.format(
        "The header of CSV file '%s' differs from the schema of the external table.\nSchema of CSV header: %s.\nSchema of external table: %s.",
        tableFileURI, Arrays.toString(header), Arrays.toString(fieldNames));

    if (header.length != fieldNames.length) {
      throw new TextAnalyticsException("%s", incompatibleSchemaErr);
    }



    for (int i = 0; i < fieldNames.length; i++) {
      String fieldName = fieldNames[i];
      // the headers of tables read during compile time are contained within <> for some reason
      if (fieldName.charAt(0) == '<' && fieldName.charAt(fieldName.length() - 1) == '>') {
        fieldName = fieldName.substring(1, fieldName.length() - 1);
      }

      if (false == fieldName.equals(header[i])) {
        throw new TextAnalyticsException("%s", incompatibleSchemaErr);
      }
    }
  }

  /**
   * Method that removes a BOM (byte order marker) character from the beginning of a UTF-8 encoded
   * file. Such a character is allowed in the UTF-8 specification but has no effect, and it needs to
   * be removed or else it will appear as part of the header row.
   * 
   * @param header the header row of the CSV file
   */
  private static void stripBOMFromHeader(String[] header) {
    // SPECIAL CASE: trim BOM character from the beginning of the first header
    // We only support UTF-8 encoded CSV files, so other BOM character sequences do not need to be
    // checked
    if (header != null) {
      if (header[0].charAt(0) == BOM_UTF8) {
        header[0] = header[0].substring(1);
      }
    }
  }

}
