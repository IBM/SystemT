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
import java.util.zip.GZIPInputStream;

import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarInputStream;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldSetter;
import com.ibm.avatar.algebra.datamodel.ObjectID;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.scan.DocScan;
import com.ibm.avatar.algebra.scan.DocScanInternal;
import com.ibm.avatar.api.Constants;

/**
 * Operator that reads documents from a tar or tar.gz file. Document schema will have two columns;
 * the first, called "label", holds the file name of the document within the archive; the second,
 * called "text", holds the document text. This class is NOT public, because it should never be
 * accessed directly, only through {@link DocScan#makeFileScan(File)}.
 * 
 */
public class TarFileScan extends DocScanInternal {

  /** Name of the character encoding we use for documents we exract. */
  private static final String ENCODING_NAME = "UTF-8";

  /** Name that we give the label column in our output tuples. */
  // public static final String LABEL_COL_NAME = "label";

  /** The .tar or .tar.gz file we read. */
  private final File tarfile;

  /** Where we read the file. */
  TarInputStream in;

  /** Stream reader attached to {@link #in}; converts bytes to characters. */
  InputStreamReader isr;

  /**
   * The current entry in the archive. We need to cache it here because of the way the
   * TarInputStream API is structured.
   */
  private TarEntry curEntry;

  /** Counter for generating dummy document IDs */
  private int nextDummyDocID = 1;

  /**
   * Accessor for putting the document label in place.
   */
  protected FieldSetter<String> docLabelAcc;

  // public TarFileScan(String tarfileName) throws Exception {
  // this(new File(tarfileName));
  // }

  public TarFileScan(File tarfile) throws Exception {
    this(tarfile, DocScanInternal.createLabeledSchema());
  }

  /**
   * Create a {@link TarFileScan} operator for the specified Tar file and expected operator schema.
   * 
   * @param tarfile the Tar file to be scanned
   * @param docSchema expected output schema of the operator
   * @throws FileNotFoundException if the specified Tar file does not exist
   */
  public TarFileScan(File tarfile, AbstractTupleSchema docSchema) throws FileNotFoundException {
    if (!tarfile.exists()) {
      throw new FileNotFoundException("Archive file " + tarfile.getPath() + " not found");
    }

    this.tarfile = tarfile;
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

    // Move to the next entry.
    advanceToNextEntry(mt);

    return doc;
  }

  /**
   * Advance to the next document in the tarball, checking for end of input while we're at it.
   */
  private void advanceToNextEntry(MemoizationTable mt) throws IOException {
    do {
      curEntry = in.getNextEntry();
      if (null == curEntry) {
        mt.setEndOfInput();
      }
    } while (null != curEntry && curEntry.isDirectory());
  }

  @Override
  protected void startScan(MemoizationTable mt) throws Exception {
    // Determine from the file name whether we're dealing with a gzipped
    // archive.
    if (tarfile.getName().matches(".*gz")) {
      // System.err.printf("%s is a gzipped archive.\n", tarfile);
      in = new TarInputStream(new GZIPInputStream(new FileInputStream(tarfile)));
    } else {
      // System.err.printf("%s is NOT a gzipped archive.\n", tarfile);
      in = new TarInputStream(new FileInputStream(tarfile));
    }

    // Create an InputStreamReader to the char->byte conversion.
    isr = new InputStreamReader(in, ENCODING_NAME);

    // Advance to the first entry.
    advanceToNextEntry(mt);
    if (null == curEntry) {
      System.err.printf("Warning: Tarfile %s is empty\n", tarfile);
    }
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
