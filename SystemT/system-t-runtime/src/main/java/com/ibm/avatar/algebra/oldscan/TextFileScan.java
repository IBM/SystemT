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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldSetter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.scan.DocScanInternal;
import com.ibm.avatar.api.Constants;

/**
 * Operator that reads the contents of a single text file. Supported file formats are: .txt, .html,
 * .htm, .xhtml and .xml This operator assumes the input file to be of UTF-8 encoding. This operator
 * returns either tuples with two fields: label and text or tuples with one field: text.
 * 
 */
public class TextFileScan extends DocScanInternal {

  /** The input file is assumed to be of UTF-8 encoding */
  public static final String FILE_ENCODING = "UTF-8";

  /** Input source for the text file to scan */
  protected InputStreamReader in;

  /** Input text file passed to the operator */
  protected File textFile;

  /** Denotes whether the operator has completed reading the input file */
  protected boolean scanComplete;

  /**
   * Accessor for putting the document label in place.
   */
  protected FieldSetter<String> docLabelAcc;

  /**
   * Constructs a TextFileScan operator for the specified input text file.
   * 
   * @param textFile the text file to be scanned
   * @throws FileNotFoundException if the input text file does not exist
   */
  public TextFileScan(File textFile) throws FileNotFoundException {
    this(textFile, DocScanInternal.createLabeledSchema());
  }

  /**
   * Constructs a TextFileScan operator for the specified input text file and expected operator
   * output schema.
   * 
   * @param textFile the text file to be scanned
   * @param docSchema expected output schema of the operator
   * @throws FileNotFoundException if the input text file does not exist
   */
  public TextFileScan(File textFile, AbstractTupleSchema docSchema) throws FileNotFoundException {
    super();

    // Validate first
    if (!textFile.exists()) {
      throw new FileNotFoundException("Text file " + textFile.getPath() + " not found");
    }

    this.textFile = textFile;
    this.docSchema = docSchema;
  }

  /**
   * Defines the output schema of this operator. TextFileScan operator returns tuple with two
   * fields: label and text or tuple with one field: text
   */
  @Override
  protected AbstractTupleSchema createOutputSchema() {

    // Create accessor for the text field
    docTextAcc = docSchema.textSetter(getTextColName());
    docTextGetters.add(docSchema.textAcc(getTextColName()));

    // Create accessor for label, only if it is part of specified document schema
    if (docSchema.containsField(Constants.LABEL_COL_NAME)) {
      docLabelAcc = docSchema.textSetter(Constants.LABEL_COL_NAME);
      docTextGetters.add(docSchema.textAcc(Constants.LABEL_COL_NAME));
    }

    return docSchema;
  }

  /**
   * Internal implementation of checkEndOfInput that verifies that the end of input is reached.
   */
  @Override
  protected void reallyCheckEndOfInput(MemoizationTable mt) throws Exception {
    if (scanComplete) {
      mt.endOfInput();
    }
  }

  /**
   * Call back method that gets invoked when the scan starts so that the operator can initialize its
   * state
   */
  @Override
  protected void startScan(MemoizationTable mt) throws Exception {
    scanComplete = false;
    in = new InputStreamReader(new FileInputStream(textFile), FILE_ENCODING);
  }

  /**
   * Grabs the next document tuple in the scan
   */
  @Override
  protected Tuple getNextDoc(MemoizationTable mt) throws Exception {

    String docText = getDocText(in);
    in.close();

    // Create a tuple out of the text we've read into memory.
    Tuple ret = createOutputTup();
    docTextAcc.setVal(ret, docText);

    // label may or may not be the part of the specified document schema
    if (docSchema.containsField(Constants.LABEL_COL_NAME))
      docLabelAcc.setVal(ret, textFile.getName());

    // We are processing only a single text file, so mark EOI
    mt.setEndOfInput();
    scanComplete = true;

    return ret;
  }
}
