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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldSetter;
import com.ibm.avatar.algebra.datamodel.ObjectID;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.scan.DocScanInternal;
import com.ibm.avatar.algebra.util.document.DumpFileReader;
import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.api.Constants;

/**
 * Operator that reads documents from files in DB2/Derby export format. To create such a file in
 * db2, issue a command in the form:
 * 
 * <pre>
 *      db2 =&gt; export to [filename].del of del select [docid], [doctext] \
 *      db2 =&gt; from [table] fetch first [nrows] rows only
 * </pre>
 * 
 * or with a label column:
 * 
 * <pre>
 *      db2 =&gt; export to [filename].del of del select [docid], [label], [doctext] \
 *      db2 =&gt; from [table] fetch first [nrows] rows only
 * </pre>
 * 
 * The first column of the input file must be the doc id, and the second column must be the document
 * text.
 * 
 */
public class DBDumpFileScan extends DocScanInternal {

  /** Name of the type given to the document tuples this operator returns. */
  private static final String DOC_TYPE_NAME = "AOMDocument";

  /** Encoding to use when reading dump files. */
  private static final String ENCODING_NAME = "UTF-8";

  private static final String COMMA = ",";
  private static final String SINGLE_QUOTE = "\"";

  // private final File dumpfile;

  /** Class that does most of the work for us. */
  private final DumpFileReader in;

  /**
   * Flag that is set to TRUE if we expect each line to have an additional "label" column between
   * the integer ID and the doctext.
   */
  private boolean haveLabelCol = false;

  public boolean getHaveLabelCol() {
    return this.haveLabelCol;
  }

  /**
   * Accessor for putting the document label (if applicable) in place.
   */
  protected FieldSetter<String> docLabelAcc;

  /**
   * Convenience constructor for reading from just one file or directory.
   * 
   * @throws FileNotFoundException
   */
  public DBDumpFileScan(String dumpfileName) throws Exception {
    this(FileUtils.createValidatedFile(dumpfileName));
  }

  public DBDumpFileScan(File dumpfile) throws Exception {
    this(dumpfile, null);
  }

  /**
   * Create a {@link DBDumpFileScan} operator for the specified delimited file and expected operator
   * schema.
   * 
   * @param dumpfile the delimited file to be scanned
   * @param docSchema expected output schema of the operator
   * @throws Exception if the specified delimited file does not exist
   */
  public DBDumpFileScan(File dumpfile, AbstractTupleSchema docSchema) throws Exception {
    if (dumpfile == null) {
      throw new FileNotFoundException("No dump file passed to constructor.");
    }

    if (!dumpfile.exists()) {
      throw new FileNotFoundException("Dump file " + dumpfile.getPath() + " not found");
    }

    // this.dumpfile = dumpfile;

    // Determine the file format: 2 columns or 3?
    determineFileFormat(dumpfile);

    // Set up the object that does most of the reading for us.
    int[] schema;
    if (haveLabelCol) {
      schema = new int[] {DumpFileReader.LONG_TYPE, DumpFileReader.STRING_TYPE,
          DumpFileReader.STRING_TYPE};
      if (docSchema == null) {
        docSchema = DocScanInternal.createLabeledSchema();
      }
    } else {
      schema = new int[] {DumpFileReader.LONG_TYPE, DumpFileReader.STRING_TYPE};
      if (docSchema == null) {
        docSchema = DocScanInternal.createOneColumnSchema();
      }
    }

    this.docSchema = docSchema;

    in = new DumpFileReader(dumpfile, ENCODING_NAME, schema, stripCR);
  }

  /**
   * Checks whether the file contains a label column or not. The label and text string values must
   * be surrounded by the character string delimiter, a double quotation mark ("). Any double
   * quotation marks inside these values must be escaped ("").
   * 
   * @param dumpfile
   * @throws FileNotFoundException
   * @throws IOException
   */
  private void determineFileFormat(File dumpfile) throws FileNotFoundException, IOException {
    BufferedReader tmp = null;

    try {
      tmp = new BufferedReader(new FileReader(dumpfile));
      String firstLine = tmp.readLine();
      if (null == firstLine) {
        // SPECIAL CASE: Empty input
        haveLabelCol = false;
        return;
        // END SPECIAL CASE
      }
      String[] fields = firstLine.split(COMMA);
      if (2 == fields.length) {
        haveLabelCol = false;
        return;
      } else if (fields.length > 2) {
        // If label contained a comma, then label column is spread across multiple elements of
        // fields[] array.
        if (fields[1].startsWith(SINGLE_QUOTE)) {
          if (!fields[1].endsWith(SINGLE_QUOTE)) {
            /*
             * If fields[1] starts with a single quote, but does not end with a single quote,then
             * there were some commas in column1 which got split when we invoked
             * firstLine.split(COMMA).So, skip till end of column1 is reached.
             */
            int idx = 2;
            while (idx < fields.length - 1) {
              if (fields[idx].endsWith(SINGLE_QUOTE)) {
                break;// reached end of column1
              } else {
                idx++;
                continue;// skip to the next field
              }
            }
            if (idx < fields.length - 1) {
              // there are more columns beyond column1, so column1 should be a label
              haveLabelCol = true;
              return;
            } else {
              // reached end of fields[]. So, no label column
              haveLabelCol = false;
              return;
            }
          } // end: if fields[1] does not end with single quote
        } // end: if fields[1] starts with a single quote

        // If control reaches here, then the document has a label column since fields[].length > 2
        haveLabelCol = true;
        return;
      } else {// fields[].length < 2
        throw new IOException(String.format("First line of dump file %s has only %d fields",
            dumpfile.getPath(), fields.length));
      }
    } finally {
      if (tmp != null) {
        tmp.close();
      }
    }
  }

  @Override
  protected Tuple getNextDoc(MemoizationTable mt) throws Exception {

    boolean debug = false;

    // Create a document tuple.
    Tuple doc = createOutputTup();

    // Populate the fields of the tuple.
    Object[] nextFields = in.getNextTup();

    long docid = (Long) nextFields[0];
    doc.setOid(new ObjectID(DOC_TYPE_NAME, docid, true));

    String doctext = (String) nextFields[haveLabelCol ? 2 : 1];
    docTextAcc.setVal(doc, doctext);

    if (haveLabelCol) {
      String labelStr = (String) nextFields[1];
      // label may or may not be the part of the specified custom document schema
      if (docSchema.containsField(Constants.LABEL_COL_NAME))
        docLabelAcc.setVal(doc, labelStr);
    }

    if (debug) {
      System.err.printf("DBDumpFileScan: Read docid %d\n", docid);
    }

    // Check for end of input.
    reallyCheckEndOfInput(mt);

    return doc;
  }

  @Override
  protected void startScan(MemoizationTable mt) throws Exception {
    // Scan was started in the constructor.
  }

  @Override
  protected void reallyCheckEndOfInput(MemoizationTable mt) throws Exception {
    if (in.endOfInput()) {
      mt.setEndOfInput();
    }
  }

  @Override
  public void setStripCR(boolean stripCR) {
    in.setStripCR(stripCR);
    this.stripCR = stripCR;
  }

  /**
   * We need to override this method so that we can add the "label" column if necessary.
   */
  @Override
  protected AbstractTupleSchema createOutputSchema() {
    if (null != this.docSchema) {
      docTextAcc = docSchema.textSetter(getTextColName());
      docTextGetters.add(docSchema.textAcc(getTextColName()));

      // Create accessor for label, only if it is part of specified document schema
      if (docSchema.containsField(Constants.LABEL_COL_NAME)) {
        docLabelAcc = docSchema.textSetter(Constants.LABEL_COL_NAME);
        docTextGetters.add(docSchema.textAcc(Constants.LABEL_COL_NAME));
      }
    } else { // if output schema is not specified, derive it based on the given delimited file
      if (haveLabelCol) {
        // AbstractTupleSchema baseSchema = super.createOutputSchema();
        // AbstractTupleSchema ret = TupleSchema
        // .addTextCol(baseSchema, LABEL_COL_NAME);
        AbstractTupleSchema ret = super.createLabeledSchema();

        ret.setName(Constants.DEFAULT_DOC_TYPE_NAME);

        docLabelAcc = ret.textSetter(Constants.LABEL_COL_NAME);
        docTextGetters.add(ret.textAcc(Constants.LABEL_COL_NAME));
        docTextAcc = ret.textSetter(getTextColName());
        docTextGetters.add(ret.textAcc(getTextColName()));

        this.docSchema = ret;
      } else {
        // No label column; defer to superclass implementation.
        this.docSchema = super.createOutputSchema();
      }
    }
    return this.docSchema;
  }
}
