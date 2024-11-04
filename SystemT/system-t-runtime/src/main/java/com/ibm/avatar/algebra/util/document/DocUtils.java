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
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;

import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.TextGetter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.util.string.StringEscaper;
import com.ibm.avatar.algebra.util.test.ReservoirSampler;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.DocReader;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.logging.Log;

/**
 * Utility functions for dealing with sets of documents.
 */
public class DocUtils {

  /** How often (in documents) to report sampling progress. */
  public static int PROGRESS_INTERVAL_DOCS = 10000;

  /**
   * Convert the indicated documents file into Excel-friendly CSV format, with one document per line
   * and all special chars within the document text escaped. Currently assumes the presence of a
   * "text" and "label" column in the data.
   * 
   * @param docsFile input file, in any format that SystemT understands
   * @param csvFile output CSV file
   */
  public static void toCSV(File docsFile, File csvFile) throws Exception {

    // Open up a scan on the document set.
    // DocScan scan = DocScan.makeFileScan(docsFile);
    DocReader reader = new DocReader(docsFile);
    // MemoizationTable mt = new MemoizationTable(scan);

    // Create accessors for getting at the relevant fields.
    FieldGetter<Span> getDocText = reader.getDocSchema().spanAcc(Constants.DOCTEXT_COL);
    FieldGetter<Span> getLabel = reader.getDocSchema().spanAcc(Constants.LABEL_COL_NAME);

    // Open up the output file.
    OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(csvFile), "UTF-8");

    long startMs = System.currentTimeMillis();
    long bytesRead = 0;

    int nread = 0;
    // while (mt.haveMoreInput()) {
    while (reader.hasNext()) {
      // Tuple doctup = scan.getNextDocTup(mt);
      Tuple doctup = reader.next();

      String text = getDocText.getVal(doctup).getText();
      String label = getLabel.getVal(doctup).getText();
      long docid = doctup.getOid().getIDInType();

      nread++;
      bytesRead += text.length();

      // Add a line to the output; format is: docid, label, text
      out.append(Long.toString(docid));
      out.append(",\"");
      out.append(StringEscaper.escapeStr(label));
      out.append("\",\"");
      out.append(StringEscaper.escapeStr(text));
      out.append("\"\n");

      if (0 == nread % PROGRESS_INTERVAL_DOCS) {
        long elapsedMs = System.currentTimeMillis() - startMs;
        long MB = bytesRead / 1024 / 1024;
        long sec = elapsedMs / 1000;

        System.err.printf("Read %d documents (%d MB) " + "in %d sec (%1.2f MB/sec)...\n", nread, MB,
            sec, (double) MB / (double) sec);
      }
    }

    out.close();
  }

  /**
   * Convert the indicated documents file into Zip format, with UTF-8 text. Currently assumes the
   * presence of a "label" column in the data.
   * 
   * @param docsFile input file, in any format that SystemT understands
   * @param csvFile output CSV file
   */
  public static void toZip(File docsFile, File zipFile) throws Exception {

    // Open up a scan on the document set.
    // DocScan scan = DocScan.makeFileScan(docsFile);
    DocReader reader = new DocReader(docsFile);
    // MemoizationTable mt = new MemoizationTable(scan);

    // Create accessors for getting at the relevant fields.
    FieldGetter<Span> getDocText = reader.getDocSchema().spanAcc(Constants.DOCTEXT_COL);
    FieldGetter<Span> getLabel = reader.getDocSchema().spanAcc(Constants.LABEL_COL_NAME);

    // Open up the output file.
    ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(zipFile));

    long startMs = System.currentTimeMillis();
    long bytesRead = 0;

    int nread = 0;
    // while (mt.haveMoreInput()) {
    while (reader.hasNext()) {
      // Tuple doctup = scan.getNextDocTup(mt);
      Tuple doctup = reader.next();

      String docText = getDocText.getVal(doctup).getText();
      String label = getLabel.getVal(doctup).getText();

      // Create a Zip file catalog entry for the new file.
      try {
        zip.putNextEntry(new ZipEntry(label));

        // Write the document into the zip file, in UTF-8 format.
        zip.write(docText.getBytes("UTF-8"));

        nread++;
        bytesRead += docText.length();

        if (0 == nread % PROGRESS_INTERVAL_DOCS) {
          long elapsedMs = System.currentTimeMillis() - startMs;
          long MB = bytesRead / 1024 / 1024;
          long sec = elapsedMs / 1000;

          System.err.printf("Read %d documents (%d MB) " + "in %d sec (%1.2f MB/sec)...\n", nread,
              MB, sec, (double) MB / (double) sec);
        }
      } catch (ZipException e) {
        Log.info("Skipping doc with label '%s' due to exception:\n%s", label, e.getMessage());
      }

    }
    zip.close();

    System.err.printf("Done.\n");
  }

  /**
   * Convenience version of {@link #getDocSample(File, int, long, int)} for when you're not that
   * picky about random seeds.
   */
  public static ArrayList<Tuple> getDocSample(File docsFile, int docSampleSize) throws Exception {
    return getDocSample(docsFile, docSampleSize, 42, Integer.MAX_VALUE);
  }

  /**
   * @param docsFile a collection of documents, in one of the input formats that System T
   *        understands
   * @param docSampleSize how many documents you want
   * @param see seed for random number generator
   * @param maxDocsToRead maximum number of documents to read from the collection when sampling
   * @return a fixed-size random sample of the documents. Always returns the same sample. Each
   *         reuturned tuple should contain two columns: the document text ("text") and the original
   *         file name ("label")
   */
  public static ArrayList<Tuple> getDocSample(File docsFile, int docSampleSize, long seed,
      int maxDocsToRead) throws Exception {

    // Open up a scan on the full document set.
    // DocScan scan = DocScan.makeFileScan(docsFile);
    DocReader reader = new DocReader(docsFile);
    // MemoizationTable mt = new MemoizationTable(scan);

    FieldGetter<Text> getDocText = null;
    // Create accessors for getting at the relevant fields.
    if (reader.getDocSchema().containsField(Constants.DOCTEXT_COL)) {
      getDocText = reader.getDocSchema().textAcc(Constants.DOCTEXT_COL);
    } else {
      throw new TextAnalyticsException(
          "Document utility getDocSample() expects a schema with column '%s'.",
          Constants.DOCTEXT_COL);
    }

    // Create a reservoir sample to do the work of picking docs.
    // Always use the same seed, for consistency across experiments.
    ReservoirSampler<Tuple> reservoir = new ReservoirSampler<Tuple>(docSampleSize, seed);

    long startMs = System.currentTimeMillis();
    long bytesRead = 0;

    int nread = 0;
    // while (mt.haveMoreInput()
    while (reader.hasNext() && nread < maxDocsToRead) {
      // Tuple doctup = scan.getNextDocTup(mt);
      Tuple doctup = reader.next();

      reservoir.add(doctup);

      nread++;
      bytesRead += getDocText.getVal(doctup).getText().length();

      if (0 == nread % PROGRESS_INTERVAL_DOCS) {
        long elapsedMs = System.currentTimeMillis() - startMs;
        long MB = bytesRead / 1024 / 1024;
        double sec = ((double) elapsedMs) / 1000.0;

        System.err.printf("Read %d documents (%d MB) " + "in %1.2f sec (%1.2f MB/sec)...\n", nread,
            MB, sec, (double) MB / sec);
      }
    }

    return reservoir.getReservoir();
  }

  /**
   * @return a random sample of the documents, with a roughly flat distribution of document sizes.
   */
  @SuppressWarnings("unchecked")
  public static ArrayList<Tuple> getEvenDocSample(File docsFile, int docSampleSize)
      throws Exception {
    FieldGetter<Text> getText = docTextAcc(docsFile);

    HashMap<Integer, Integer> sizeToCount = new HashMap<Integer, Integer>();
    {
      // PASS 1: Produce a count of how many documents of each size we
      // have.
      // DocScan scan = DocScan.makeFileScan(docsFile);
      DocReader reader = new DocReader(docsFile);
      // MemoizationTable mt = new MemoizationTable(scan);
      // while (mt.haveMoreInput()) {
      while (reader.hasNext()) {
        // Tuple doctup = scan.getNextDocTup(mt);
        Tuple doctup = reader.next();
        int size = getText.getVal(doctup).getText().length();

        int cnt = 0;
        if (sizeToCount.containsKey(size)) {
          cnt = sizeToCount.get(size);
        }
        sizeToCount.put(size, cnt + 1);
      }
    }

    final int SIZE_BIN_SIZE = 100;
    int[] outBins;
    {
      // PASS 2: Figure out how many documents to get from each size bin.

      // Find the 5 largest doc sizes.
      // int[] maxSz = new int[5];
      // for (int size : sizeToCount.keySet()) {
      // boolean replace = false;
      // for (int i = 0; i < maxSz.length; i++) {
      // if (size > maxSz[i]) {
      // replace = true;
      // }
      // }
      //
      // if (replace) {
      // // Choose the smallest max size as victim.
      // int smallestIx = 0;
      // for (int i = 1; i < maxSz.length; i++) {
      // if (maxSz[i] < maxSz[smallestIx]) {
      // smallestIx = i;
      // }
      // }
      // maxSz[smallestIx] = size;
      // }
      // }
      //
      // // Use the *fifth* largest doc size as our upper bound, to get
      // rid
      // // of the really huge outliers.
      // int smallestIx = 0;
      // for (int i = 1; i < maxSz.length; i++) {
      // if (maxSz[i] < maxSz[smallestIx]) {
      // smallestIx = i;
      // }
      // }
      // int max = maxSz[smallestIx];

      final int MAX_DOC_SZ = 10000;

      // Create an array of size bins; the first covers 0 to
      // (SIZE_BIN_SIZE-1), and so on.
      int numBins = MAX_DOC_SZ / SIZE_BIN_SIZE + 1;
      int[] inBins = new int[numBins];
      outBins = new int[numBins];

      // Fill up the "in" bins.
      for (int size : sizeToCount.keySet()) {
        int binIx = size / SIZE_BIN_SIZE;
        if (binIx < inBins.length) {
          inBins[binIx] += sizeToCount.get(size);
        }
      }

      // Do a round-robin transfer of documents from the "in" bins to the
      // "out" bins until we have the requested sample size.
      int numMoved = 0;
      while (numMoved < docSampleSize) {
        for (int i = 0; i < numBins; i++) {
          if (inBins[i] > 0) {
            // Transfer one "document".
            outBins[i]++;
            inBins[i]++;
            numMoved++;
          }
        }
      }
    }

    {
      // PASS 3: Create a reservoir sample for each size bin, and feed the
      // documents through the appropriate reservoirs to generate the
      // master sample.
      ReservoirSampler<Tuple>[] reservoirs = new ReservoirSampler[outBins.length];

      long seed = 42;
      for (int i = 0; i < reservoirs.length; i++) {
        reservoirs[i] = new ReservoirSampler<Tuple>(outBins[i], seed);
      }

      // DocScan scan = DocScan.makeFileScan(docsFile);
      DocReader reader = new DocReader(docsFile);
      // MemoizationTable mt = new MemoizationTable(scan);
      // while (mt.haveMoreInput()) {
      while (reader.hasNext()) {
        // Tuple doctup = scan.getNextDocTup(mt);
        Tuple doctup = reader.next();
        int size = getText.getVal(doctup).getText().length();

        int reservoirIx = size / SIZE_BIN_SIZE;

        if (reservoirIx < reservoirs.length) {
          reservoirs[reservoirIx].add(doctup);
        }
      }

      ArrayList<Tuple> results = new ArrayList<Tuple>();
      for (int i = 0; i < reservoirs.length; i++) {
        results.addAll(reservoirs[i].getReservoir());
      }
      return results;
    }
  }

  /**
   * @return an accessor for pulling document texts out of the tuples returned by getDocSample()
   */
  public static FieldGetter<Text> docTextAcc(File docsFile) throws Exception {
    // Create a scan so that we can get at its doc schema
    DocReader reader = new DocReader(docsFile);
    return reader.getDocSchema().textAcc(Constants.DOCTEXT_COL);
  }

  /**
   * @return an accessor for pulling document labels out of the tuples returned by getDocSample()
   */
  public static FieldGetter<Text> docLabelAcc(File docsFile) throws Exception {
    // Create a scan so that we can get at its doc schema
    DocReader reader = new DocReader(docsFile);
    return reader.getDocSchema().textAcc(Constants.LABEL_COL_NAME);
  }

  /**
   * Returns a list of accessors to all fields of type {@link Text} in the input schema.
   * 
   * @return list of accessors to all field of type Text in the schema, or an empty list if no such
   *         fields exist
   */
  public static ArrayList<TextGetter> getAllTextGetters(TupleSchema schema) {
    // assemble a list of accessors for all fields of type Text in the schema
    ArrayList<TextGetter> textGetters = new ArrayList<TextGetter>();

    for (int i = 0; i < schema.size(); i++) {
      if (schema.getFieldTypeByIx(i).getIsText()) {
        textGetters.add(schema.textAcc(schema.getFieldNameByIx(i)));
      }
    }
    return textGetters;
  }
}
