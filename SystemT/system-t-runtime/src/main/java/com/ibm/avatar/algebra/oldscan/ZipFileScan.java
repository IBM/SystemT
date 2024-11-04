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
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldSetter;
import com.ibm.avatar.algebra.datamodel.ObjectID;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.scan.DocScanInternal;
import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.api.Constants;

/**
 * Operator that reads documents from a .zip archive file. Document schema will have two columns;
 * the first, called "label", holds the file name of the document within the archive; the second,
 * called "text", holds the document text.
 * 
 */
public class ZipFileScan extends DocScanInternal {

  /** Name of the character encoding we use for documents we exract. */
  private static final String ENCODING_NAME = "UTF-8";

  /** Name that we give the label column in our output tuples. */
  // public static final String LABEL_COL_NAME = "label";

  /** The .zip file we read. */
  private final File zipfile;

  /** Where we read the file. */
  private ZipInputStream in;

  /** Stream reader attached to {@link #in}; converts bytes to characters. */
  private InputStreamReader isr;

  /** Metadata about the file that we're currently reading out of the archive. */
  ZipEntry curEntry;

  /** Counter for generating dummy document IDs */
  private int nextDummyDocID = 1;

  /**
   * Accessor for putting the document label in place.
   */
  protected FieldSetter<String> docLabelAcc;

  public ZipFileScan(String zipFileName) throws Exception {
    this(FileUtils.createValidatedFile(zipFileName));
  }

  public ZipFileScan(File zipfile) throws Exception {
    this(zipfile, DocScanInternal.createLabeledSchema());
  }

  /**
   * Create a {@link ZipFileScan} operator for the specified zip file and expected output schema.
   * 
   * @param zipfile the zip file to be scanned
   * @param docSchema expected output schema of the operator
   * @throws FileNotFoundException if the specified zip file does not exist
   */
  public ZipFileScan(File zipfile, AbstractTupleSchema docSchema) throws FileNotFoundException {
    if (!zipfile.exists()) {
      throw new FileNotFoundException("Archive file " + zipfile.getPath() + " not found");
    }

    this.zipfile = zipfile;
    this.docSchema = docSchema;
  }

  @Override
  protected Tuple getNextDoc(MemoizationTable mt) throws Exception {

    if (null == curEntry) {
      throw new RuntimeException("Read past end of archive");
    }

    String doclabel = curEntry.getName();

    // System.err.printf("Reading document %s\n", doclabel);

    String docText = getDocText(isr);

    // Now we can create a tuple to hold the field values we just created.
    Tuple doc = createOutputTup();

    docTextAcc.setVal(doc, docText);

    // label may or may not be the part of the specified document schema
    if (docSchema.containsField(Constants.LABEL_COL_NAME))
      docLabelAcc.setVal(doc, doclabel);

    // Generate a dummy document ID
    doc.setOid(new ObjectID(Constants.DEFAULT_DOC_TYPE_NAME, nextDummyDocID++, true));

    // Move to the next entry in the archive, and check for end of input.
    advanceToNextEntry(mt);

    return doc;
  }

  @Override
  protected void startScan(MemoizationTable mt) throws Exception {

    in = new ZipInputStream(new FileInputStream(zipfile));

    isr = new InputStreamReader(in, ENCODING_NAME);

    // Advance to the first entry, assuming there is one.
    advanceToNextEntry(mt);
  }

  /**
   * Advance to the next document in the archive, checking for end of input while we're at it. Skips
   * directories.
   */
  private void advanceToNextEntry(MemoizationTable mt) throws IOException {

    do {
      curEntry = in.getNextEntry();
      if (null == curEntry) {
        mt.setEndOfInput();
      }
    } while (null != curEntry && curEntry.isDirectory());
  }

  /**
   * We need to override this method so that we can add the "label" column.
   */
  @Override
  protected AbstractTupleSchema createOutputSchema() {

    docTextAcc = docSchema.textSetter(getTextColName());
    docTextGetters.add(docSchema.textAcc(getTextColName()));

    // Create accessor for label, only if it is part of specified document schema
    if (docSchema.containsField(Constants.LABEL_COL_NAME)) {
      docLabelAcc = docSchema.textSetter(Constants.LABEL_COL_NAME);
      docTextGetters.add(docSchema.textAcc(Constants.LABEL_COL_NAME));
    }

    return docSchema;
  }

  @Override
  protected void reallyCheckEndOfInput(MemoizationTable mt) throws Exception {
    // The startScan() method should have already checked for EOI
  }
}
