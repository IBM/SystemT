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
package com.ibm.avatar.algebra.base;

import java.io.PrintWriter;
import java.util.HashSet;

import com.ibm.avatar.algebra.base.Tee.TeeOutput;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.ConstantTupleList;
import com.ibm.avatar.algebra.datamodel.DerivedTupleSchema;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.util.data.StringPairList;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.ExceptionWithView;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.logging.Log;

/**
 * Base class for operators that do local analysis. These local analysis operators read inputs (and
 * produce results) on one document's worth of tuples at a time.
 * 
 */
public abstract class Operator {

  /**
   * Set this constant to TRUE to generate a trace of operator invocations.
   */
  public static final boolean TRACE_OPERATORS = false;

  /**
   * Set this constant to TRUE to generate a trace of all operations having to do with caching
   * outputs of operators that always return the same value.
   */
  public static final boolean TRACE_CACHING = false;

  /*
   * FIELDS
   */

  /** The operator(s) that feed into this operator. */
  protected Operator[] inputs;

  /**
   * The schema of the tuples that the operator produces.
   */
  protected AbstractTupleSchema outputSchema;

  /**
   * Index of the persistent results buffer for this operator.
   */
  protected int resultsBufIx;

  /**
   * Value used for the "view name" attribute of operators that aren't associated with a specific
   * AQL view
   */
  public static final String NOT_A_VIEW_NAME = "Unlabeled Operators";

  /**
   * Object that the profiler uses to mark this operator's "location" within the operator graph.
   */
  protected ProfileRecord profRecord = new ProfileRecord(NOT_A_VIEW_NAME, this);

  /**
   * String that represents tokenization overhead in profiling results.
   */
  public static final String TOKENIZATION_OVERHEAD_NAME = "Tokenization";

  /**
   * Object used to mark tokenization overhead that this operator incurs.
   */
  protected ProfileRecord tokRecord = new ProfileRecord(TOKENIZATION_OVERHEAD_NAME, this);

  private boolean attached = false;

  /**
   * Cached value of the last call to {@link #outputIsAlwaysTheSame()}. Set to null when it is not
   * initialized.
   */
  private volatile Boolean constOutput = null;

  /**
   * If {@link #cacheOutput} is true, the cached set of output results; initialized in a thread-safe
   * way on the first use of this operator.
   */
  private volatile ConstantTupleList cachedResults = null;

  //
  // CONSTRUCTORS
  //
  /** Constructor for an operator with <bf>no</bf> children (e.g. a scan) */
  protected Operator() {
    this(new Operator[0], true);
  }

  /** Constructor for an operator with a single child. */
  protected Operator(Operator child) {
    this(new Operator[] {child}, true);
  }

  /**
   * Constructor for an operator with a single child, providing control over whether to attach to
   * the child.
   */
  protected Operator(Operator child, boolean attach) {
    this(new Operator[] {child}, attach);

  }

  /** Constructor for an operator with two children. */
  protected Operator(Operator leftChild, Operator rightChild) {
    this(new Operator[] {leftChild, rightChild}, true);
  }

  protected Operator(Operator children[]) {
    this(children, true);
  }

  /** Main constructor; called by all others. */
  protected Operator(Operator children[], boolean attach) {
    // Copy the input array, just in case.
    inputs = new Operator[children.length];
    for (int i = 0; i < children.length; i++) {
      inputs[i] = children[i];
      if (attach) {
        inputs[i].attach();
      }
    }
  }

  //
  // EXTERNAL METHODS
  //

  /**
   * @param name the name of the AQL view that generated this operator
   */
  public void setViewName(String name) {
    profRecord.setViewName(name);
  }

  public String getViewName() {
    return profRecord.getViewName();
  }

  /**
   * @return number of other operators that feed into this one.
   */
  public int getNumInputs() {
    return inputs.length;
  }

  /**
   * Main entry point. Handles the creation/reuse of result buffers.
   * 
   * @param mt table of thread-local information to keep across calls
   * @return a list of result tuples, or null if there are zero results.
   */
  public TupleList getNext(MemoizationTable mt) throws Exception {

    // Record our current location in the operator graph.
    mt.profileEnter(profRecord);

    // Check for cached results; if we find any, return them.
    if (null != cachedResults) {
      mt.profileLeave(profRecord);
      if (TRACE_CACHING) {
        Log.debug("%s returning cached result %s", this, cachedResults);
      }
      return cachedResults;
    }

    // Get a handle on this operator's results buffer, which is persistent across calls.
    TupleList results = mt.getResultsBuf(resultsBufIx);
    results.clear();

    // Get a handle on a TupleList to hold our results.
    // Tell the subclass to do its work.
    try {
      getNextInternal(mt);
    } catch (ExceptionWithView e1) {
      // We have already caught an execution exception somewhere below this operator and attached it
      // a view name. So
      // just rethrow that exception.
      throw e1;
    } catch (Throwable e) {
      // Exception encountered during the execution of this operator. Attach view information and
      // rethrow the error.
      throw new ExceptionWithView(e, this.getViewName());
    }

    // If we have more than a small number of results, create a new results
    // list so that the garbage collector can clear out the large one we
    // just created. Note that this reset operation doesn't affect the local
    // variable <results>, which we return below.
    final int MAX_PERSISTENT_RESULTS = 2;
    if (results.size() > MAX_PERSISTENT_RESULTS) {
      mt.resetResultsBuf(resultsBufIx, getOutputSchema());
    }

    // If this variable has no cached results but should have some, cache
    // the current set of results for later use.
    if (producesConstOutput() && null == cachedResults) {
      // Synchronized block just in case setting a pointer is not atomic.
      // Note that multiple threads may set cachedResults to different
      // values, but that's ok.
      synchronized (this) {
        // Make a copy, since the buffer will be reset.
        cachedResults = new ConstantTupleList(results);

        if (TRACE_CACHING) {
          Log.debug("%s generated cached result %s", this, cachedResults);
        }

        // Return immediately to avoid returning TupleList
        // when cachedResults is ConstantTupleList but results is TupleList
        mt.profileLeave(profRecord);
        return cachedResults;
      }
    }

    mt.profileLeave(profRecord);

    // System.err.printf("%s returning %s\n", this, results);

    // Remove transform since it's not used.
    return results;
  }

  /**
   * @return the "public" output schema -- the one that upstream operators will interact with. This
   *         schema may include projections that this operator is not aware of.
   */
  public final AbstractTupleSchema getOutputSchema() {
    if (null == outputSchema) {
      outputSchema = createOutputSchema();
    }
    return outputSchema;
  }

  /**
   * Add a "virtual" projection to the output of this tuple; that is, a projection operation
   * accomplished by creating a new DerivedTupleSchema over the "actual" schema of this operator's
   * output tuples.
   * 
   * @param nameMapping mapping from column names in the current output schema of this operator to
   *        column names in the new derived schema
   */
  public final void addProjection(StringPairList nameMapping) {
    final boolean debug = false;

    if (null == outputSchema) {
      outputSchema = createOutputSchema();
    }
    AbstractTupleSchema orig = outputSchema;
    outputSchema = new DerivedTupleSchema(orig, nameMapping);

    if (debug) {
      Log.debug("%s: Added projection from %s to %s", this, orig, outputSchema);
    }
  }

  /**
   * Initialize any internal state (such as output buffers) that will be stored in the memoization
   * table. Should not be overridden if at all possible; modify initStateInternal() instead!
   */
  public void initState(MemoizationTable mt) throws TextAnalyticsException {
    // Make this function idempotent.
    if (mt.getOpInitialized(this)) {
      return;
    }

    // Initialize inputs first, so that they get lower-numbered buffers.
    for (Operator in : inputs) {
      in.initState(mt);
    }

    // Create a persistent results buffer for this operator.
    resultsBufIx = mt.createResultsBuf(getOutputSchema());

    // Now we can initialize the state of this operator.
    // System.err.printf("Calling initStateInternal() for %s\n", this);
    initStateInternal(mt);

    // Determine whether this operator always produces the same result.

    mt.setOpInitialized(this);
  }

  /**
   * Wrapper around {@link #outputIsAlwaysTheSame()} that caches the results of the previous call.
   * Also sets up the value of {@link #cacheOutput}
   * 
   * @return true if the output of this operator is guaranteed never to change
   */
  public final boolean producesConstOutput() {
    if (null == constOutput) {
      // No cached value. Grab a lock and set it up.
      synchronized (this) {
        // Someone may have modified the pointer while we were grabbing
        // the lock, so check again for null.
        if (null == constOutput) {
          constOutput = outputIsAlwaysTheSame();
        }
      }
    }

    return constOutput;
  }

  @Override
  public String toString() {
    return String.format("%s (%s)", this.getClass().getSimpleName(), getViewName());
  }

  /**
   * Recursively pretty-print an operator tree's contents to a stream. Subclasses may want to
   * override this method.
   * 
   * @throws ParseException
   */
  public void dump(PrintWriter stream, int indent) throws ParseException {

    printIndent(stream, indent);
    stream.printf("%s(\n", this.toString());

    if (null != inputs) {
      for (int i = 0; i < inputs.length; i++) {
        inputs[i].dump(stream, indent + 1);
      }
    }

    printIndent(stream, indent + 1);
    stream.print(")\n");

  }

  /** Helper function for dump() */
  protected void printIndent(PrintWriter stream, int indent) {
    for (int i = 0; i < indent; i++) {
      stream.print("  ");
    }
  }

  /**
   * Get a pointer to the indicated input. This method is made public for testing and debugging
   * purposes.
   */
  public Operator getInputOp(int ix) {
    return inputs[ix];
  }

  /**
   * Check if the subplan rooted at this operator has a reference to lemma match
   * 
   * @param visited set of operators visited so far to avoid recursing to an operator twice
   * @return true if the subplan rooted at this operator has a lemma match reference, or false
   *         otherwise
   */
  public boolean requiresLemma(HashSet<Operator> visited) {
    final boolean debug = false;

    // Don't visit the same operator twice
    if (visited.contains(this))
      return false;

    // Remember that we visited this operator
    visited.add(this);

    // Check this operator
    if (requiresLemmaInternal()) {
      if (debug)
        Log.debug("%s has a reference to lemma", this);
      return true;
    }

    // Check its inputs
    // First, normal inputs
    for (int ix = 0; ix < getNumInputs(); ix++) {
      Operator childOp = getInputOp(ix);
      if (childOp.requiresLemma(visited)) {
        if (debug)
          Log.debug("%s has a reference to lemma", childOp);
        return true;
      }
    }

    // Then, special case inputs
    if (this instanceof TeeOutput) {
      Tee tee = ((TeeOutput) this).getTee();
      if (tee.requiresLemma(visited)) {
        if (debug)
          Log.debug("%s has a reference to lemma", tee);
        return true;
      }
    }

    // No references found
    return false;
  }

  public boolean requiresPartOfSpeech(HashSet<Operator> visited) {
    final boolean debug = false;

    // Don't visit the same operator twice
    if (visited.contains(this))
      return false;

    // Remember that we visited this operator
    visited.add(this);

    // Check this operator
    if (requiresPartOfSpeechInternal()) {
      if (debug)
        Log.debug("%s has a reference to partOfSpeech", this);
      return true;
    }

    // Check its inputs
    // First, normal inputs
    for (int ix = 0; ix < getNumInputs(); ix++) {
      Operator childOp = getInputOp(ix);
      if (childOp.requiresPartOfSpeech(visited)) {
        if (debug)
          Log.debug("%s has a reference to partOfSpeech", childOp);
        return true;
      }
    }

    // Then, special case inputs
    if (this instanceof TeeOutput) {
      Tee tee = ((TeeOutput) this).getTee();
      if (tee.requiresPartOfSpeech(visited)) {
        if (debug)
          Log.debug("%s has a reference to partOfSpeech", tee);
        return true;
      }
    }

    return false;
  }


  /*
   * METHODS TO BE IMPLEMENTED BY SUBCLASSES
   */

  /**
   * Operators that can produce constant output may need to override this method to return true. The
   * default implementation returns true the operator has one or more inputs and all inputs always
   * produce the same result.
   * 
   * @return true if this operator produces the exact same output every time it is invoked.
   */
  protected boolean outputIsAlwaysTheSame() {
    if (0 == inputs.length) {
      // No inputs -- assume this operator is a scan that produces a
      // different result every time.
      return false;
    }

    boolean allInputsConst = true;
    for (Operator input : inputs) {
      // Call producesConstOutput(), which memoizes, to prevent too much
      // recursion from happening.
      if (false == input.producesConstOutput()) {
        allInputsConst = false;
      }
    }

    return allInputsConst;
  }

  /**
   * Subclasses must override this function to initialize their schemas. This function will be
   * called from clearResults(). Assume that the outputSchema has been initialized to empty.
   */
  protected abstract AbstractTupleSchema createOutputSchema();

  /**
   * Main entry point for subclasses. Subclasses should override this method to perform the
   * appropriate processing for a document.
   * 
   * @param mt table of thread-local information to keep across calls
   */
  protected abstract void getNextInternal(MemoizationTable mt) throws Exception;

  /**
   * Operators that store such state in the memoization table should override this method to store
   * that state.
   */
  protected void initStateInternal(MemoizationTable mt) throws TextAnalyticsException {
    // Default implementation does nothing.
  }

  /**
   * @return whether this operators has a reference to lemma match; subclasses should override this
   *         as appropriate
   */
  protected boolean requiresLemmaInternal() {
    // Default implementation returns false
    return false;
  }

  protected boolean requiresPartOfSpeechInternal() {
    // Default implementation returns false
    return false;
  }

  /**
   * Scan operators should override this method to set the end of input flag if their internal scans
   * have reached the end. Sets the end of input flag to true if somewhere in a subtree of this
   * operator a scan has reached the end of its input
   * 
   * @throws Exception
   */
  protected void checkEndOfInput(MemoizationTable mt) throws Exception {
    // Default implementation just propagates the check.
    for (Operator in : inputs) {
      in.checkEndOfInput(mt);
    }
  }

  /*
   * METHODS TO BE USED BY SUBCLASSES
   */

  /**
   * Upcall to create a new tuple with the appropriate schema.
   * 
   * @throws Exception
   */
  protected Tuple createOutputTup() {
    return outputSchema.createTup();
  }

  protected void addResultTup(Tuple tup, MemoizationTable mt) {
    TupleList results = mt.getResultsBuf(resultsBufIx);
    results.add(tup);
  }

  /**
   * Add a set of tuples to our result set.
   * 
   * @param tups the tuples to add
   * @param mt table of persistent thread-local information
   */
  protected void addResultTups(TupleList tups, MemoizationTable mt) {
    TupleList results = mt.getResultsBuf(resultsBufIx);
    results.addAllNoChecks(tups);
  }

  /*
   * INTERNAL METHODS For use only by this class.
   */

  /**
   * Called by the operator that will be pulling from this operator. There should be exactly one
   * such operator.
   */
  private void attach() throws IllegalArgumentException {
    if (this.attached) {
      throw new IllegalArgumentException(
          "Tried to attach two outputs to PullOperator " + this.toString());
    }
    attached = true;
  }

}
