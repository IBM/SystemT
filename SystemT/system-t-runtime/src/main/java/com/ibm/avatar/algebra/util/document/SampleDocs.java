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

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.Tuple;

/**
 * Simple program that takes a sample from a large collection of documents and writes out the sample
 * in zip format, encoded as UTF-8.
 */
public class SampleDocs {

  public static final String USAGE = "java %s\n" + "    [input] [output.zip] [ndoc]\n";

  /** Maximum number of documents to read when sampling. */
  private static final int MAX_DOCS = 10000000;

  /**
   * @param args
   */
  public static void main(String[] args) throws Exception {

    if (3 != args.length) {
      System.err.printf(USAGE, SampleDocs.class.getName());
      return;
    }

    // Read arguments.
    File inputFile = new File(args[0]);
    File outputFile = new File(args[1]);
    int sampleSize = Integer.valueOf(args[2]);

    long seed = 42;

    // DocUtils does most of the work.
    ArrayList<Tuple> tups = DocUtils.getDocSample(inputFile, sampleSize, seed, MAX_DOCS);

    // Create accessors for reading the document tuples in the sample.
    FieldGetter<Text> getLabel = DocUtils.docLabelAcc(inputFile);
    FieldGetter<Text> getText = DocUtils.docTextAcc(inputFile);

    ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(outputFile));

    for (Tuple doc : tups) {

      // Break out the document into its component parts.
      // long docid = doc.getOid().getIDInType();
      String docText = getText.getVal(doc).getText();
      String label = getLabel.getVal(doc).getText();

      // Create a Zip file catalog entry for the new file.
      zip.putNextEntry(new ZipEntry(label));

      // Write the document into the zip file, in UTF-8 format.
      zip.write(docText.getBytes("UTF-8"));

    }
    zip.close();

    System.err.printf("Wrote sampled documents to %s\n", outputFile);
  }

}
