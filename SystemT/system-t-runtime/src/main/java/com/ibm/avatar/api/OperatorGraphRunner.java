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

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.base.ProfileRecord;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.FieldSetter;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.TextGetter;
import com.ibm.avatar.algebra.datamodel.TextSetter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.output.ToBuffer;
import com.ibm.avatar.algebra.scan.DocScan;
import com.ibm.avatar.algebra.scan.ExternalViewScan;
import com.ibm.avatar.algebra.util.document.DocUtils;
import com.ibm.avatar.algebra.util.lang.LangCode;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.algebra.util.tokenize.Tokenizer;
import com.ibm.avatar.aog.AOGPlan;
import com.ibm.avatar.aog.BufferOutputFactory;
import com.ibm.avatar.aog.OutputFactory;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.ChunkerException;
import com.ibm.avatar.api.exceptions.InvalidOutputNameException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;

/**
 * Class that encapsulates and runs an operator graph described in an AOG file or a Java string of
 * AOG specs. The code in this class also serves as example code for developers who need to access
 * the low-level AOG APIs directly.
 * 
 */
public class OperatorGraphRunner {

  /*
   * --------------------------------------------------------------------- BEGIN CONSTANTS
   */

  public static final int FEEDBACK_INTERVAL_DOCS = 1000;

  /** Should we generate detailed output messages? */
  public static boolean VERBOSE = false;

  /** Should we dump the operator graph to stderr? */
  public static boolean ALWAYS_DUMP_GRAPH = false;

  /** Should we (unconditionally) dump the AOG plan to stderr? */
  public static boolean ALWAYS_DUMP_PLAN = false;

  /** Should we call System.gc() before every document when profiling? */
  public static boolean GC_EVERY_DOC_DURING_PROF = false;

  /**
   * Minimum chunk size to use when dividing up input documents into chunks for processing.
   */
  private static final int MIN_CHUNK_SZ = 10000;

  /** How often we generate progress messages if {@link #writeStatus} is true. */
  private int feedbackInterval = FEEDBACK_INTERVAL_DOCS;

  public void setFeedbackInterval(int progressInterval) {
    this.feedbackInterval = progressInterval;
  }

  public int getFeedbackInterval() {
    return this.feedbackInterval;
  }

  /** The scan that will feed documents into the operator graph during run() */
  private DocScan docscan = null;

  /**
   * Factory object for creating output operators.
   */
  private OutputFactory outFact = new DisableOutputFactory();

  private AOGPlan plan = null;

  /**
   * Total bytes allocated during the most recent call to {@link #run()}, for tracing memory usage.
   */
  private final long totalBytesAlloc = 0;

  public long getTotalBytesAlloc() {
    return totalBytesAlloc;
  }

  /**
   * Total number of output annotations produced in the most recent call to {@link #run()} or
   * sequence of push calls.
   */
  private final int totalAnnotProd = 0;

  public int getTotalAnnotProd() {
    return totalAnnotProd;
  }

  /** Callbacks for dividing documents into smaller chunks for processing. */
  private Chunker[] chunkers = null;

  /**
   * When running in push mode, the output schema of our DocScan stub operator.
   */
  private TupleSchema docSchema = null;

  /**
   * When running in push mode, accessor to set the text field of a document tuple. <br/>
   * For document tuples without a field named 'text', this is null.
   */
  private TextSetter doctextSetter = null;

  /**
   * When running in push mode, accessor to set the text field of a document tuple *directly* with a
   * span.
   */
  private FieldSetter<Object> doctextSetterHack = null;

  /**
   * When running in push mode, accessor to retrieve the 'text' field of a document tuple. <br/>
   * For document tuples without a field named 'text', this is null.
   */
  private TextGetter doctextGetter = null;

  /**
   * When running in push mode, list of accessors for all fields of type Text in a document tuple.
   */
  private ArrayList<TextGetter> doctextGetters = null;

  /**
   * @param threadIx index of one of the threads that this OperatorGraphRunner can support
   * @return actual tokenizer object that this thread will use
   */
  public Tokenizer getTokenizer(int threadIx) {
    return plan.getMemoizationTable(threadIx).getTokenizer();
  }

  /*
   * END FIELDS ---------------------------------------------------------------------
   */

  /*
   * --------------------------------------------------------------------- BEGIN CONSTRUCTORS
   */
  /**
   * Construct an object that will instantiate the AOG spec in a file.
   */
  protected OperatorGraphRunner(AOGPlan plan) throws ParseException {
    this.plan = plan;
    // this.outputNames = plan.getAllOutputNames ();
    this.docscan = plan.getDocScan();
    this.docSchema = this.docscan.getDocSchema();

    // set the text accessor for a default schema (which contains a field named 'text')
    if (docSchema.containsField(Constants.DOCTEXT_COL)) {
      doctextGetter = docSchema.textAcc(Constants.DOCTEXT_COL);
      doctextSetter = docSchema.textSetter(Constants.DOCTEXT_COL);
    } else {
      doctextSetter = null;
    }

    doctextGetters = DocUtils.getAllTextGetters(docSchema);
  }

  /*
   * END CONSTRUCTORS ---------------------------------------------------------------------
   */

  /*
   * --------------------------------------------------------------------- BEGIN COMMON API METHODS
   */

  /**
   * @return the expected input document schema
   */
  public TupleSchema getDocSchema() {
    return docscan.getDocSchema();
  }

  /**
   * @return the number of output streams the user-specified operator graph will produce.
   */
  public int getNumOutputs() {
    return getOutputNames().size();
  }

  /**
   * @return the names of the outputs
   */
  public ArrayList<String> getOutputNames() {
    // return outputNames;
    return plan.getValidOutputNames();
  }

  /**
   * Completely disable outputs; no output operators will be instantiated.
   */
  public void setNoOutput() {
    // Class for disabling outputs
    outFact = new DisableOutputFactory();
  }

  /*
   * END COMMON API METHODS ---------------------------------------------------------------------
   */

  /*
   * --------------------------------------------------------------------- BEGIN PUSH MODE API
   */

  /**
   * Set up output for push mode; tuples will go into a buffer, from which they can be fetched with
   * {@link #getResults(String, int)}. This method must be called BEFORE setPushInput().
   */
  public void setBufferOutput() {

    // Set up output for push mode.
    outFact = new BufferOutputFactory();
  }

  /**
   * Toggle whether an output of the operator graph is enabled for subsequent processing. If the
   * output is not enabled, processing that would have been done to compute it is skipped.
   * <B>NOTE:</B> This method currently only works in push mode.
   * 
   * @param outputName nickname of output
   * @param val TRUE to enable the output, FALSE to disable it
   */
  public void setOutputEnabled(String outputName, boolean val, int threadIx) {

    plan.setOutputEnabled(outputName, val, threadIx);
  }

  /**
   * Single-threaded version of {@link #setOutputEnabled(String, boolean, int)}.
   */
  public void setOutputEnabled(String outputName, boolean val) {
    setOutputEnabled(outputName, val, 0);
  }

  public boolean getOutputEnabled(String outputName, int threadIx) {

    return plan.getOutputEnabled(outputName, threadIx);
  }

  /**
   * Single-threaded version of {@link #getOutputEnabled(String, int)}.
   */
  public boolean getOutputEnabled(String outputName) {
    return getOutputEnabled(outputName, 0);
  }

  /**
   * Temporarily enable/disable all outputs. Currently, this method can only be used in push mode.
   */
  public void setAllOutputsEnabled(boolean val, int threadIx) {

    if (val) {
      plan.enableAllOutputs(threadIx);
    } else {
      plan.disableAllOutputs(threadIx);
    }
  }

  /** Single-threaded version of {@link #setAllOutputsEnabled(boolean, int)}. */
  public void setAllOutputsEnabled(boolean val) {
    setAllOutputsEnabled(val, 0);
  }

  /**
   * @param outputName name of an output of the current operator graph
   * @return the schema for that output's tuples.
   * @throws InvalidOutputNameException if there is no output with the indicated name
   */
  public TupleSchema getOutputSchema(String outputName) throws InvalidOutputNameException {
    Operator op = plan.getOutputOp(outputName);

    if (null == op) {
      throw new RuntimeException(
          String.format("Don't have an output operator for output '%s'.", outputName));
    }

    // Push-mode outputs are guaranteed to have base schemas
    return (TupleSchema) op.getOutputSchema();
  }

  /**
   * Convenience method to set the chunker on the docschema column 'text' of type Text
   * 
   * @param c object to use to divide large documents into smaller ones; may be duplicated
   *        internally via {@link Chunker#makeClone()}
   */
  public void setChunker(Chunker c) throws Exception {
    setChunker(c, Constants.DOCTEXT_COL);
  }

  /**
   * Sets the chunker (divider of documents) on one specific column of type Text
   * 
   * @param c object to use to divide large documents into smaller ones; may be duplicated
   *        internally via {@link Chunker#makeClone()}
   * @param colName the column to run the chunker on
   */
  public void setChunker(Chunker c, String colName) throws Exception {

    if (false == (outFact instanceof BufferOutputFactory)) {
      throw new IllegalStateException("Can only use document chunking with buffered output");
    }

    // only chunk on the one column name passed in as a parameter
    AbstractTupleSchema docschema = docscan.getDocSchema();

    // error checking -- the field being chunked must exist and be of type Text
    if (docschema.containsField(colName) == false) {
      throw new ChunkerException(colName, "Field does not exist.");
    }

    if (docschema.getFieldTypeByName(colName).getIsText() == false) {
      throw new ChunkerException(colName, "Field must be of type Text.");
    }

    chunkers = new Chunker[plan.getNumThreads()];
    for (int i = 0; i < chunkers.length; i++) {
      chunkers[i] = c.makeClone();
    }

    doctextGetter = docschema.textAcc(colName);
    doctextSetterHack = docschema.genericSetter(colName, docschema.getFieldTypeByName(colName));

  }

  /**
   * Pushes a single document through the operator graph
   * 
   * @param language language of the document text
   * @param threadIx index of the thread that is doing the pushing (0 when running single-threaded)
   */
  public void pushDoc(String doctext, LangCode language, int threadIx) throws Exception {
    if (doctextSetter == null) {
      throw new TextAnalyticsException(
          "Document pusher only operates on documents with field named '%s'.",
          Constants.DOCTEXT_COL);
    }

    // Create a tuple to hold the text.
    Tuple doctup = docSchema.createTup();
    doctextSetter.setVal(doctup, doctext, language);
    pushDoc(doctup, threadIx);
  }

  /**
   * Convenience version of {@link #pushDoc(String, LangCode, int)} for running in single-threaded
   * mode.
   */
  public void pushDoc(String doctext, LangCode language) throws Exception {
    pushDoc(doctext, language, 0);
  }

  /**
   * Pushes a single document through the operator graph, using the default language.
   * 
   * @param threadIx index of the thread that is doing the pushing (0 when running single-threaded)
   */
  public void pushDoc(String doctext, int threadIx) throws Exception {
    if (doctextSetter == null) {
      throw new TextAnalyticsException(
          "Document pusher only operates on documents with field named '%s'.",
          Constants.DOCTEXT_COL);
    }

    // Create a tuple to hold the text.
    Tuple doctup = docSchema.createTup();

    doctextSetter.setVal(doctup, doctext);
    pushDoc(doctup, threadIx);
  }

  /** Single-threaded version of {@link #pushDoc(String, int)} */
  public void pushDoc(String doctext) throws Exception {
    pushDoc(doctext, 0);
  }

  /**
   * Pushes a single document tuple through the operator graph.
   * 
   * @param threadIx index of the thread that is doing the pushing (0 when running single-threaded)
   */
  public void pushDoc(Tuple doc, int threadIx) throws Exception {

    // Make sure that this document tuple has the proper schema
    validateTuple(doc, docSchema);

    if (null != chunkers) {
      pushDocChunked(doc, threadIx);
    } else {
      docscan.setDoc(doc, plan.getMemoizationTable(threadIx));
      if (plan.hasNextDoc()) {
        plan.nextDoc(threadIx);
      }
    }

    // Remove any token buffers attached to the document, so that they do
    // not take up space after this call.
    for (FieldGetter<Text> textGetter : doctextGetters) {
      Text docText = textGetter.getVal(doc);

      // docText can be null for some labeled documents
      if (docText != null) {
        docText.resetCachedTokens();
      }
    }

  }

  /** Single-threaded version of {@link #pushDoc(Tuple, int)} */
  public void pushDoc(Tuple doc) throws Exception {
    pushDoc(doc, 0);
  }

  /**
   * Pushes a single document through the operator graph, using the chunker to divide the document
   * into smaller pieces.
   */
  private void pushDocChunked(Tuple doc, int threadIx) throws Exception {

    if (doctextGetter == null) {
      throw new TextAnalyticsException(
          "Document chunker only operates on documents with field named '%s'.",
          Constants.DOCTEXT_COL);
    }
    Text docspan = doctextGetter.getVal(doc);
    String doctext = docspan.getText();

    // Initialize the buffers that will hold our results.
    ArrayList<String> outputNames = getOutputNames();
    TupleList[] resultBufs = new TupleList[outputNames.size()];
    for (int i = 0; i < resultBufs.length; i++) {
      String outputName = outputNames.get(i);
      resultBufs[i] = new TupleList(getOutputSchema(outputName));
    }

    int chunkBegin = 0;
    while (chunkBegin < doctext.length() - MIN_CHUNK_SZ) {
      int chunkEnd = chunkers[threadIx].getNextBoundary(doctext, chunkBegin + MIN_CHUNK_SZ);

      pushChunk(threadIx, docspan, resultBufs, chunkBegin, chunkEnd);

      // Reset for next chunk.
      chunkBegin = chunkEnd;
    }

    // Don't forget the final chunk!
    pushChunk(threadIx, docspan, resultBufs, chunkBegin, doctext.length());

    // Put our full results into place in the output buffers.
    for (int i = 0; i < resultBufs.length; i++) {
      String outputName = outputNames.get(i);
      Operator output = plan.getOutputOp(outputName);

      if (plan.getOutputEnabled(outputName, threadIx)) {
        ToBuffer tobuf = (ToBuffer) output;

        tobuf.overrideOutput(resultBufs[i], plan.getMemoizationTable(threadIx));

      }
    }
  }

  private void pushChunk(int threadIx, Text docText, TupleList[] resultBufs, int chunkBegin,
      int chunkEnd) throws Exception {

    // System.err.printf(
    // "Pushing chunk that ranges from %d to %d (%d chars)\n",
    // chunkBegin, chunkEnd, chunkEnd - chunkBegin);

    Span chunk = Span.makeBaseSpan(docText, chunkBegin, chunkEnd);

    // Create a fake document tuple and put the chunk into
    // the text field.
    Tuple fakeDocTup = docSchema.createTup();
    doctextSetterHack.setVal(fakeDocTup, chunk);

    docscan.setDoc(fakeDocTup, plan.getMemoizationTable(threadIx));
    if (plan.hasNextDoc()) {
      plan.nextDoc(threadIx);
    }

    // Pull results out, since they'll get wiped.
    ArrayList<String> outputNames = getOutputNames();
    for (int i = 0; i < resultBufs.length; i++) {
      String outputName = outputNames.get(i);
      if (plan.getOutputEnabled(outputName, threadIx)) {
        TupleList results = getResults(outputName, threadIx);

        resultBufs[i].addAllNoChecks(results);
      }
    }
  }

  /**
   * Populate the tuples in an ExternalViewScan operator.
   * 
   * @param threadIx index of the thread that is doing the pushing (0 when running single-threaded)
   * @param viewName name of the external view
   * @param tups Tuples to populate.
   */
  public void pushExternalViewTups(int threadIx, String viewName, TupleList tups)
      throws TextAnalyticsException {

    // Get a pointer to the external view scan operator
    ExternalViewScan scan = plan.getExternalViewScanOp(viewName);

    if (Objects.isNull(scan)) {
      String[] externalViewNames = plan.getExternalViewNames();
      throw new TextAnalyticsException(
          "The external view '%s' does not exist; the extractor has external views with the following external names: [%s]",
          viewName, String.join(", ", externalViewNames));
    }

    // Put the tuples in place
    scan.setTups(plan.getMemoizationTable(threadIx), tups);
  }

  /**
   * Clear all the tuples in each external view scan so that we don't keep old external view tuples
   * around
   * 
   * @param threadIx index of the thread that is doing the clearing (0 when running single-threaded)
   */
  public void clearExternalViewTups(int threadIx) {
    for (String extViewName : plan.getExternalViewNames()) {
      ExternalViewScan scan = plan.getExternalViewScanOp(extViewName);

      scan.clearTups(plan.getMemoizationTable(threadIx));
    }
  }

  /**
   * Returns the schema of an external view.
   * 
   * @param viewName
   * @return
   */
  public TupleSchema getExternalViewSchema(String viewName) {

    ExternalViewScan op = plan.getExternalViewScanOp(viewName);

    if (null == op) {
      throw new RuntimeException(
          String.format("Don't have an operator for external view '%s'", viewName));
    }

    // Return the schema of the associated external view object,
    // as declared in AQL via the create external view statement.
    // Note that we do not return the output schema of the operator,
    // via {@link #getOutputSchema()} because it
    // might have been overridden by {@link #addProjection()}.
    // return op.getOutputSchema();
    return op.getExternalViewSchema();
  }

  /**
   * Returns the external name of the input external view.
   * 
   * @param viewName the name (in AQL) of the external view.
   * @return
   */
  public String getExternalViewExternalName(String viewName) {

    ExternalViewScan op = plan.getExternalViewScanOp(viewName);

    if (null == op) {
      throw new RuntimeException(
          String.format("Don't have an operator for external view '%s'.", viewName));
    }

    return op.getViewNameExternal();
  }

  /**
   * Returns the external name of the input external view.
   * 
   * @param viewName the name (in AQL) of the external view.
   * @return
   */
  public String[] getExternalViewNames() {

    return plan.getExternalViewNames();
  }

  /**
   * Clears out any buffered data from the last call to pushDoc().
   */
  public void clearBuffers(int threadIx) {
    plan.clearBuffers(threadIx);
  }

  /**
   * Mark all iterators of result buffers used by this thread as not in use. This method is only
   * called on exceptions in {@link OperatorGraphImpl#execute(Tuple, String[], Map)} to ensure that
   * the OperatorGraph state is completely reset and there are no tuple lists left in use when the
   * next document comes in this thread.
   * 
   * @param threadIx
   */
  public void markResultsBufDone(int threadIx) {
    plan.markResultsBufDone(threadIx);
  }

  /**
   * Single-threaded version of clearBuffers().
   */
  public void clearBuffers() {
    clearBuffers(0);
  }

  /**
   * Reset the operator graph's internal state; call this method when the thread executing
   * {@link #pushDoc(Tuple, int)} has been interrupted, leaving operators with potentially
   * inconsistent internal state.
   */
  public void resetGraph(int threadIx) {

    // Toggling the "enabled" state of an output will reset its internal
    // state.
    for (String outputName : getOutputNames()) {

      boolean wasEnabled = getOutputEnabled(outputName, threadIx);

      setOutputEnabled(outputName, false, threadIx);
      setOutputEnabled(outputName, true, threadIx);
      setOutputEnabled(outputName, wasEnabled, threadIx);
    }
  }

  /** Single-threaded version of {@link #resetGraph(int)} */
  public void resetGraph() {
    resetGraph(0);
  }

  /**
   * Method for retrieving results programatically when running in push mode.
   * 
   * @param outputName name of an output of the current graph
   * @param threadIx index of the thread in which the caller is running (every thread gets its own
   *        output buffers); 0 when running single-threaded.
   * @return the results for the most recent document
   * @throws InvalidOutputNameException if there is no output with the indicated name
   */
  public TupleList getResults(String outputName, int threadIx) throws InvalidOutputNameException {
    Operator output = plan.getOutputOp(outputName);

    if (false == plan.getOutputEnabled(outputName, threadIx)) {
      throw new IllegalStateException("Tried to retrieve results from a disabled output.");
    }

    if (null == output) {

    }

    if (output instanceof ToBuffer) {
      ToBuffer tobuf = (ToBuffer) output;

      return tobuf.getBuf(plan.getMemoizationTable(threadIx));
    } else {
      // Someone has forgotten to call setBufferOutput().
      throw new IllegalStateException(String.format(
          "Called getResults() without buffered output enabled (output operator is %s).",
          output.getClass()));
    }
  }

  /**
   * Single-threaded version of {@link #getResults(String, int)}.
   * 
   * @throws InvalidOutputNameException if there is no output with the indicated name
   */
  public TupleList getResults(String outputName) throws InvalidOutputNameException {
    return getResults(outputName, 0);
  }

  /** Send an interrupt signal to the appropriate thread. */
  public void interrupt(int threadIx) {
    plan.getMemoizationTable(threadIx).interrupted = true;
  }

  /** Single-threaded variant of this method. */
  public void interrupt() {
    this.interrupt(0);
  }

  /**
   * Dump the query plan to the indicated stream. Useful for debugging.
   */
  public void dumpPlan(PrintStream stream) throws ParseException {
    // parsetree.dump (stream);
    if (plan != null) {
      plan.dump(new PrintWriter(stream), 1);
    }
  }

  /**
   * For internal use only.
   * 
   * @param threadIx index of an internal thread
   * @return information about what operator the thread is currently running in
   */
  public ProfileRecord getCurCodeLoc(int threadIx) {
    return plan.getCurCodeLoc(threadIx);
  }

  /**
   * Checks to see whether the input document tuple is valid for the expected doc schema Currently,
   * all this does is check to see if the sizes match, since Tuple does not contain detailed column
   * information.
   * 
   * @param doc The input document tuple
   * @param docSchema The expected doc schema
   * @throws Exception
   */
  public static void validateTuple(Tuple doc, AbstractTupleSchema docSchema) throws Exception {
    if (doc.size() != docSchema.size()) {
      throw new RuntimeException(
          String.format("Input document does not meet required doc schema %s. The doc tuple is %s",
              docSchema.toString(), StringUtils.shorten(doc.toString())));
    }
  }

  /*
   * INTERNAL METHODS GO HERE
   */

}
