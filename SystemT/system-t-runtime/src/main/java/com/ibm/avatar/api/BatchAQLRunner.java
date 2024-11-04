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
package com.ibm.avatar.api;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.scan.DocScanInternal;
import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.algebra.util.tokenize.TokenizerConfig;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;

/**
 * Program for running large numbers of documents through an annotator expressed in AQL, using SMP
 * parallelism to speed things up if possible. Currently, this program takes in a single input file
 * in DB2 dump format and outputs a collection of annotation dumps in human-readable CSV format. See
 * {@link #USAGE} for arguments information.
 * 
 */
public class BatchAQLRunner {

  /** Encoding for input AQL files. */
  public static final String DEFAULT_ENCODING = "UTF-8";

  /** Default tokenizer configuration to use */
  public static final TokenizerConfig DEFAULT_TOKENIZER_CFG = new TokenizerConfig.Standard();

  public static final String USAGE =
      "Usage: java BatchAQLRunner [docsfile] " + "[dictsdir] [outputdir] [aqlfile.aql] [nthreads]";

  /**
   * How many documents go into each HTML output file. Note that this parameter is NOT final; it may
   * be set to different values by testing scripts.
   */
  public static int DOCS_PER_OUTPUT_FILE = 10000;

  /** How often (in documents processed) we generate a status message. */
  public static final int STATUS_INTERVAL_DOCS = 1000;

  public static final void main(String[] args) throws Exception {

    // Process arguments.
    if (5 != args.length) {
      System.err.printf("%d args: %s\n", args.length, Arrays.asList(args));
      System.err.printf(USAGE);
      return;
    }

    String docsfileName = args[0];
    String dictsDirName = args[1];
    String outputDirName = args[2];
    String aqlFileName = args[3];
    int nthreads = Integer.valueOf(args[4]);

    if (nthreads <= 0) {
      throw new Exception("Invalid number of threads " + nthreads);
    }

    BatchAQLRunner b =
        new BatchAQLRunner(docsfileName, dictsDirName, outputDirName, aqlFileName, nthreads);
    b.run();

    System.err.printf("Done.\n");

    // Planner.restoreDefaultSentence();
  }

  /** Struct for holding stats on the results of a run. */
  public static class RunRecord {
    public int nthreads;

    public int ndoc;

    public long ndocBytes;

    public double sec;

    public RunRecord(int nthreads, int ndoc, long ndocBytes, double sec) {
      this.nthreads = nthreads;
      this.ndoc = ndoc;
      this.ndocBytes = ndocBytes;
      this.sec = sec;
    }
  }

  /*
   * FIELDS
   */
  String outputDirName;
  int nthreads;

  /** Scan over our input documents file. */
  DocScanInternal docs;

  /** Accessor for getting at the "text" field of a document. */
  FieldGetter<Text> getText;

  /** Buffer for holding documents that we've read from the docs file. */
  LinkedList<Tuple> docBuf = new LinkedList<Tuple>();

  /** Flag that is set to true when a thread is filling the document buffer. */
  volatile boolean fillingDocBuf = false;

  /** Flag that is set to true when we reach the end of our input. */
  volatile boolean endOfDocs = false;

  /**
   * Minimum number of documents we want in the document buffer at a given time.
   */
  private static final int DOC_BUF_LOW_WATER_MARK = 10000;

  /**
   * Maximum number of documents we aim to have in the document buffer when filling it.
   */
  private static final int DOC_BUF_HIGH_WATER_MARK = 20000;

  /**
   * Number of documents we've scanned from {@link #docs}; used for generating status messages.
   * Synchronize access on the parent class.
   */
  int ndoc = 0;

  /**
   * Total bytes of documents we've scanned; used for generating status messages. Synchronize access
   * on the parent class.
   */
  long ndocBytes = 0;

  /**
   * Shared object for holding data across calls to docs.getNext(). Synchronize on the parent object
   * to control access.
   */
  MemoizationTable mt;

  /**
   * OperatorGraph object initiated in the constructor with given AQL. It is called to actually run
   * the operator graph.
   */
  private final OperatorGraph og;

  /**
   * Shared counter used for generating file sequence numbers across threads. Synchronize on the
   * parent object to control access to this counter.
   */
  int outFileCounter = 0;

  /**
   * Shared set of output modules for generating output; each writer corresponds to an output type
   * of the AQL file.
   */
  // OutputStreamWriter[] writers;
  // File[] outFiles;
  /**
   * Flag that we set to true if there is a label column in our documents.
   */
  // boolean haveLabelCol;
  /**
   * Flag that is set to true if we're generating output; false if we're just running the AQL and
   * throwing away the results.
   */
  boolean generateOutput;

  /*
   * METHODS
   */

  /**
   * Main constructor. Sets up the objects that will perform annotation work and prepares the class
   * for the invocation of the run() method.
   * 
   * @param docsfileName DB2 dump file containing documents
   * @param dictsDirName directory containing dictionary files
   * @param outputDirName directory where HTML outputs will go, or null to generate no output
   * @param aqlFileName AQL file to run over the documents
   * @param nthreads number of processing threads to use
   */
  public BatchAQLRunner(String docsfileName, String dictsDirName, String outputDirName,
      String aqlFileName, int nthreads) throws Exception {

    this.outputDirName = outputDirName;
    this.nthreads = nthreads;

    this.generateOutput = (null != outputDirName);

    // Open up a scan on the documents dump file.
    docs = DocScanInternal.makeFileScan(FileUtils.createValidatedFile(docsfileName));
    mt = new MemoizationTable(docs);

    if (docs.getOutputSchema().containsField(Constants.DOCTEXT_COL)) {
      getText = docs.getOutputSchema().textAcc(Constants.DOCTEXT_COL);
    } else {
      throw new TextAnalyticsException(
          "BatchAQLRunner requires input documents to contain a field named '%s'.",
          Constants.DOCTEXT_COL);
    }

    // Figure out whether the document scan produces labels.
    // haveLabelCol = false;
    // for (String name : docs.interiorSchema().getFieldNames()) {
    // if ("label".equals(name)) {
    // haveLabelCol = true;
    // }
    // }
    File moduleDir = null;
    try {
      // First, compile the AQL into an operator graph and write a file on disk.
      moduleDir = File.createTempFile(System.currentTimeMillis() + "", "");
      moduleDir.delete();
      moduleDir.mkdirs();
      String modulePath = moduleDir.toURI().toString();

      CompileAQLParams params = new CompileAQLParams(FileUtils.createValidatedFile(aqlFileName),
          modulePath, dictsDirName);
      params.setTokenizerConfig(DEFAULT_TOKENIZER_CFG);
      CompileAQL.compile(params);

      // Instantiate OperatorGraph by reading AOG from disk
      og = OperatorGraph.createOG(new String[] {Constants.GENERIC_MODULE_NAME}, modulePath, null,
          null);
      System.err.println("Output types: " + og.getOutputTypeNames());

    } finally {
      if (null != moduleDir)
        FileUtils.deleteDirectory(moduleDir);
    }
  }

  /**
   * Main entry point. Parses and compiles the indicated AQL, then spawns off background threads to
   * run it. When the background threads complete, cleans up after them.
   */
  public RunRecord run() throws Exception {

    // Remember when we started.
    long startMs = System.currentTimeMillis();

    // Spawn the worker threads.
    workerThread[] workers = new workerThread[nthreads];
    for (int i = 0; i < nthreads; i++) {
      workers[i] = new workerThread(this);
      workers[i].start();
    }

    // Wait for the workers to complete.
    for (int i = 0; i < nthreads; i++) {
      workers[i].join();

      // System.err.printf("Thread %d processed %d documents.\n", i,
      // workers[i].totalDocCount);

    }

    // Measure elapsed time.
    long endMs = System.currentTimeMillis();
    double elapsedSec = (endMs - startMs) / 1000.0;
    double docPerSec = ndoc / elapsedSec;

    System.err.printf("Processed %d documents in %1.5f sec (%1.5f doc/sec)\n", ndoc, elapsedSec,
        docPerSec);

    // Construct a report for the caller.
    return new RunRecord(nthreads, ndoc, ndocBytes, elapsedSec);

  }

  /**
   * Fetch the next document. Replenishes the buffer of documents, if necessary. Reentrant. This
   * method is a wrapper for {@link #reallyGetNextDocTup()} that tracks document stats.
   */
  private Tuple getNextDocTup() throws Exception {

    Tuple ret = reallyGetNextDocTup();
    if (null == ret) {
      // SPECIAL CASE: We ran out of documents.
      return null;
      // END SPECIAL CASE
    }
    String docText = getText.getVal(ret).getText();

    synchronized (this) {
      ndoc++;
      ndocBytes += docText.length();

      if (0 == ndoc % STATUS_INTERVAL_DOCS) {
        // Technically we're reporting one document too early, but
        // that's ok.
        System.err.printf("Processed %d documents.\n", ndoc);
      }
    }

    return ret;
  }

  private Tuple reallyGetNextDocTup() throws Exception {

    final boolean debug = false;

    boolean fillBuf = false;

    int bufSz;

    // Try to get a document from the buffer.
    synchronized (docBuf) {
      bufSz = docBuf.size();
      if (0 == bufSz && endOfDocs) {
        // Case 1: Empty buffer, and no more documents to read into the
        // buffer.
        return null;
      } else if (bufSz > DOC_BUF_LOW_WATER_MARK) {
        // Case 2: Enough documents in the buffer.
        return docBuf.remove();
      } else if (fillingDocBuf || endOfDocs) {
        // Case 3: Buffer is low, but either:
        // a. Someone else is filling it, or
        // b. There aren't any more docs to read.
        if (bufSz > 0) {
          // Case 3a: There is still at least one document in the
          // buffer; return it.
          return docBuf.remove();
        } else {
          // Case 3b: Buffer is completely empty; we'll wait until
          // someone has filled it after releasing the lock.
          fillBuf = false;
        }
      } else {
        // Case 4: Buffer is low and no one is currently filling it.
        // More docs to read; take on the task of filling the
        // buffer.
        fillBuf = true;
        fillingDocBuf = true;

      }
    }

    if (fillBuf) {
      // Filling the buffer is our job.
      int numToAdd = DOC_BUF_HIGH_WATER_MARK - bufSz;

      if (debug) {
        System.err.printf("%s trying to add %d docs to buffer.\n", Thread.currentThread(),
            numToAdd);
      }

      Tuple[] tups = new Tuple[numToAdd];
      int numRead = 0;
      while (numRead < numToAdd && mt.haveMoreInput()) {
        tups[numRead++] = docs.getNextDocTup(mt);
      }

      if (false == mt.haveMoreInput()) {
        endOfDocs = true;
      }

      if (debug) {
        System.err.printf("%s added %d docs to buffer.\n", Thread.currentThread(), numRead);
      }

      synchronized (docBuf) {
        for (int i = 0; i < numRead; i++) {
          docBuf.add(tups[i]);
        }
        fillingDocBuf = false;
        return docBuf.remove();
      }

    } else {
      // Filling the buffer is someone else's job; poll until there's at
      // least one document in the buffer.
      while (true) {
        synchronized (docBuf) {
          if (docBuf.size() > 0) {
            return docBuf.remove();
          } else if (endOfDocs) {
            return null;
          }
        }

        Thread.sleep(1);
      }
    }

  }

  /**
   * @return a unique sequence number for naming output files.
   */
  private synchronized int getFileSeqNum() {
    return outFileCounter++;
  }

  /** Internal worker thread for processing documents and generating output. */
  private static class workerThread extends Thread {

    BatchAQLRunner b;

    /**
     * Hooks to the current set of output files (one per output type) for this thread.
     */
    OutputStreamWriter[] writers;

    File[] outFiles;

    /**
     * Number of documents whose results are reflected in the current set of output files.
     */
    int curNumDocs;

    public workerThread(BatchAQLRunner b) throws Exception {
      this.b = b;

      ArrayList<String> outputNames = b.og.getOutputTypeNames();
      writers = new OutputStreamWriter[outputNames.size()];
      outFiles = new File[outputNames.size()];

      if (b.generateOutput) {
        openWriters();
      }
    }

    /**
     * Open a new set of output writers.
     */
    private void openWriters() throws Exception {

      // Get an output file sequence number from the parent class.
      int seqnum = b.getFileSeqNum();

      ArrayList<String> outputNames = b.og.getOutputTypeNames();
      for (int i = 0; i < writers.length; i++) {
        // Get schema info for the indicated output
        String outputName = outputNames.get(i);

        // Generate a file name; format is <type><number>.htm
        outFiles[i] = new File(b.outputDirName, String.format("%s%03d.csv", outputName, seqnum));

        writers[i] = new OutputStreamWriter(
            new BufferedOutputStream(new FileOutputStream(outFiles[i])), "UTF-8");

      }

      curNumDocs = 0;
    }

    /**
     * Close up and flush the current set of output writers. Called by individual worker threads
     * while holding a lock on {@link #writers}, and also during shutdown.
     */
    private void closeWriters() throws Exception {
      for (int i = 0; i < writers.length; i++) {
        writers[i].close();

        if (0 == curNumDocs) {
          // SPECIAL CASE: No documents passed through this iteration.
          outFiles[i].delete();
          // END SPECIAL CASE.
        }

        writers[i] = null;
      }
    }

    @Override
    public void run() {
      try {

        while (true) {
          // Iterate until we run out of documents.
          Tuple docTup = b.getNextDocTup();
          if (null == docTup) {
            // End of input.
            // Close any open files.
            if (b.generateOutput) {
              closeWriters();
            }
            return;
          }

          // System.err.printf("%s processing document %s\n", Thread
          // .currentThread(), docTup.getOid());

          // Push the current document through, generating results.
          Map<String, TupleList> annotations = b.og.execute(docTup, null, null);
          ArrayList<String> outputNames = b.og.getOutputTypeNames();

          // Push results to the output writers.
          // Notice that we do this while holding a lock,
          if (b.generateOutput) {
            for (int i = 0; i < outputNames.size(); i++) {
              String outputName = outputNames.get(i);
              TupleList tups = annotations.get(outputName);
              TLIter itr = tups.iterator();
              while (itr.hasNext()) {
                Tuple tup = itr.next();

                // Write the integer docid.
                writers[i].append(String.format("%d,", docTup.getOid().getIDInType()));

                // Write the rest of the output tuple.
                writers[i].append(tup.toCSVString());

                // End the current line of CSV output
                writers[i].append('\n');
              }
            }

            if (curNumDocs >= BatchAQLRunner.DOCS_PER_OUTPUT_FILE) {
              // Finished with one batch of documents; close
              // out our current output files and open a new
              // set of outputs.
              closeWriters();
              openWriters();

            }

            curNumDocs++;
          }
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }

    }

  }

}
