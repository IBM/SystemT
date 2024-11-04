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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.DocReader;

/**
 * Simple program that takes the first x documents from a large collection of documents and writes
 * out the sample in zip format.
 */
public class PrefixDocs {

  public static final String USAGE = "java %s\n" + "    [input] [output.zip] [ndoc]\n";

  /**
   * How many documents to acquire /**
   * 
   * @param args
   */
  public static void main(String[] args) throws Exception {

    if (3 != args.length) {
      System.err.printf(USAGE, PrefixDocs.class.getName());
      return;
    }

    // Read arguments.
    File inputFile = new File(args[0]);
    File outputFile = new File(args[1]);
    int maxDocsToRead = Integer.valueOf(args[2]);

    // Open up a scan on the full document set.
    // DocScan scan = DocScan.makeFileScan(inputFile);
    DocReader reader = new DocReader(inputFile);
    // MemoizationTable mt = new MemoizationTable(scan);

    FieldGetter<Span> getDocText = reader.getDocSchema().spanAcc(Constants.DOCTEXT_COL);
    FieldGetter<Span> getDocLabel = reader.getDocSchema().spanAcc(Constants.LABEL_COL_NAME);

    long startMs = System.currentTimeMillis();
    long bytesRead = 0;

    // Open up the output zip file.
    ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(outputFile));

    int nread = 0;
    // while (mt.haveMoreInput()
    while (reader.hasNext() && nread < maxDocsToRead) {
      // Tuple doctup = scan.getNextDocTup(mt);
      Tuple doctup = reader.next();

      String docText = getDocText.getVal(doctup).getText();
      String docLabel = getDocLabel.getVal(doctup).getText();

      // Create a Zip file catalog entry for the new file.
      zip.putNextEntry(new ZipEntry(docLabel));

      // Write the document into the zip file, in UTF-8 format.
      zip.write(docText.getBytes("UTF-8"));

      nread++;
      bytesRead += getDocText.getVal(doctup).getText().length();

      if (0 == nread % 10000) {
        long elapsedMs = System.currentTimeMillis() - startMs;
        long MB = bytesRead / 1024 / 1024;
        long sec = elapsedMs / 1000;

        System.err.printf("Read %d documents (%d MB) " + "in %d sec (%1.2f MB/sec)...\n", nread, MB,
            sec, (double) MB / (double) sec);
      }
    }

    zip.close();

    System.err.printf("Wrote %d documents to %s\n", nread, outputFile);
  }

}
