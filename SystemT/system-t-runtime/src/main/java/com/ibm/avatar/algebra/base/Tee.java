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

import java.util.Arrays;
import java.util.LinkedList;

import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.extract.Dictionaries;
import com.ibm.avatar.algebra.extract.RegexesTok;
import com.ibm.avatar.logging.Log;

/**
 * Operator that splits its input tuple stream into a number of output streams. These output streams
 * are kept in lock step. Once an output has produced the current input tuple, it will indicate that
 * it is "idle" ( {@link PullOperator#idle()} returns true) until <bf>all</bf> outputs have produced
 * the current input tuple. Note that this operator operates directly on top-level tuples, as
 * opposed to running on the sets encoded in the first field of the tuple (as in classes derived
 * from {@link Operator}). This operator also serves as the base class for the Dictionary operator.
 * 
 */
public class Tee extends Operator {

  private static final int NOT_AN_INDEX = -1;

  private static final boolean debugTupleBufs = false;

  /** Performance counter name used for the "tee time" counter. */
  public static final String PERF_COUNTER_NAME = "Tee Operators";

  /**
   * Index of the counter in the MemoizationTable that tracks how many outputs of this Tee have been
   * cleared for the current document.
   */
  private int counterIx = NOT_AN_INDEX;

  /**
   * This interior class (which implements an output of the Tee) contains most of the functionality.
   */
  public class TeeOutput extends Operator {

    private Tee t;
    private int ix;

    /** Index of the MemoizationTable buffer that we use for our output. */
    private int bufferIx = NOT_AN_INDEX;

    /**
     * If this output is the last output (the one that resets all buffers in the child subtree), a
     * list of all the TeeOutputs to reset.
     */
    private TeeOutput[] toReset = null;

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();

      // Distinguish between Dictionary and Tee outputs (Dictionary is a
      // subclass of Tee)
      if (t instanceof Dictionaries) {
        sb.append(Dictionaries.class.getSimpleName());
        sb.append(t.toString());
        sb.append("$TeeOutput#");
        sb.append(Integer.valueOf(ix));
      } else if (t instanceof RegexesTok) {
        sb.append(RegexesTok.class.getSimpleName());
        sb.append(t.toString());
        sb.append("$TeeOutput#");
        sb.append(Integer.valueOf(ix));
      } else {
        // Emulate the superclass's behavior, but add an output index.
        sb.append("TeeOutput#");
        sb.append(Integer.valueOf(ix));
        sb.append(" (");
        sb.append(getViewName());
        sb.append(")");
        // sb.append (super.toString ());
      }

      return sb.toString();
    }

    /**
     * @return return the Tee operator
     */
    public Tee getTee() {
      return t;
    }

    /**
     * Set the output tuples that this output will return the next time its getNext() method is
     * called. Public so that children of the parent class (Tee) can access this method.
     */
    public void setCurtups(TupleList curtups, MemoizationTable mt) {

      if (NOT_AN_INDEX == bufferIx) {
        // This should never happen.
        throw new RuntimeException("Buffer not initialized for " + this);
      }

      if (mt.bufferWasCleared(bufferIx)) {
        // Someone has already requested that this buffer be cleared;
        // don't bother loading data into it, since the data will never
        // be used.
        if (debugTupleBufs) {
          System.err.printf("  NOT Caching results for " + "Tee output %x (buffer %d)\n",
              hashCode(), bufferIx);
        }
      } else {
        if (debugTupleBufs) {
          System.err.printf("  Caching results for Tee " + "output %x (buffer %d)\n", hashCode(),
              bufferIx);
        }
        mt.cacheResults(bufferIx, curtups);
      }
    }

    /**
     * Default constructor.
     * 
     * @param t the parent Tee operator.
     */
    private TeeOutput(Tee t, int ix) {
      // Set the Tee operator's child as our child, so that the operator initialization logic will
      // skip the Tee
      // operator.
      super(t.child, false);
      this.t = t;
      this.ix = ix;
    }

    /**
     * We need to override the default behavior, since the input to this operator is actually the
     * input to the Tee below it.
     */
    // @Override
    // protected boolean outputIsAlwaysTheSame() {
    // return t.outputIsAlwaysTheSame();
    // }

    @Override
    protected AbstractTupleSchema createOutputSchema() {
      return t.createOutputSchema(ix);
    }

    @Override
    protected void initStateInternal(MemoizationTable mt) {
      // System.err.printf("In %s.initStateInternal\n", this);

      t.initState(mt);
      if (NOT_AN_INDEX == bufferIx) {
        // Allocate a tuple buffer to hold our outputs.
        bufferIx = mt.createResultsCache(this);

        // Last output needs to reset tuple buffers for the children
        // of this Tee. Figure out which operators' buffers will be
        // reset.
        toReset = Tee.getBufClearTargets(t.child);
      }
    }

    /**
     * Remove cached tuples for this output.
     */
    // @Override
    public void clearCachedState(MemoizationTable mt) {

      if (debugTupleBufs) {
        System.err.printf("In clearCache() for %s (buffer %d)\n", this, bufferIx);
      }

      boolean bufWasUnused = mt.bufferIsUnused(bufferIx);

      mt.clearCachedResults(bufferIx);

      // Track how many buffers have been cleared.
      mt.incrCounter(t.counterIx);

      if (bufWasUnused && null != toReset) {

        // The buffer was never filled. If every other buffer has been
        // marked as "not needed", then recursively free any buffers
        // below this one.
        boolean canFreeChildBufs = (mt.getCounterVal(t.counterIx) >= t.outputs.length);
        // for (int i = 0; canFreeChildBufs && i < t.outputs.length;
        // i++) {
        // if (false == mt.bufferWasCleared(t.outputs[i].bufferIx)) {
        // canFreeChildBufs = false;
        // }
        // }

        if (canFreeChildBufs) {
          if (debugTupleBufs) {
            System.err.printf("==> Last buffer; recursing...\n");
            System.err.printf("    (Tee outputs are: %s)\n", Arrays.toString(t.outputs));
          }
          for (TeeOutput todo : toReset) {
            todo.clearCachedState(mt);
          }
        }
      }
    }

    /**
     * We override this method so that only the last output of the tee recurses.
     */
    @Override
    protected void checkEndOfInput(MemoizationTable mt) throws Exception {
      if (this.ix == t.outputs.length - 1) {
        t.child.checkEndOfInput(mt);
      }
    }

    /**
     * We override this method so that we can return TupleLists directly out of the buffer without
     * copying them.
     */
    @Override
    public TupleList getNext(MemoizationTable mt) throws Exception {

      mt.profileEnter(profRecord);

      if (debugTupleBufs) {
        System.err.printf("In getNext() for %s (buffer %d)\n", this, bufferIx);
        System.err.printf(" Tee is 0x%x and has %d outputs\n", t.hashCode(), t.outputs.length);
      }

      // First try to get a cached result.
      TupleList ret = mt.getCachedResults(bufferIx);
      if (null == ret) {
        // No cached result found; generate one, creating results for
        // the other outputs of the Tee while we're at it.
        t.advanceAll(mt);
      }

      ret = mt.getCachedResults(bufferIx);

      if (null == ret) {
        throw new RuntimeException(String.format("Got no results for %s", this));
      }

      // Free up buffers.
      clearCachedState(mt);

      mt.profileLeave(profRecord);

      return ret;
    }

    @Override
    protected void getNextInternal(MemoizationTable mt) throws Exception {
      throw new RuntimeException("Should never be called!");
    }

  }

  protected TeeOutput outputs[];

  public int getNumOutputs() {
    return outputs.length;
  }

  public Operator getOutput(int ix) {
    return outputs[ix];
  }

  private boolean stateInitialized = false;

  /** The TeeOutput operators take care of most state initialization. */
  @Override
  public void initState(MemoizationTable mt) {
    boolean debug = false;

    if (false == stateInitialized) {
      stateInitialized = true;

      if (debug) {
        Log.debug("Initializing %s with %d outputs", this, getNumOutputs());
      }

      // Allocate a counter so that we can tell when all outputs have been
      // cleared.
      counterIx = mt.createCounter();

      initStateInternal(mt);

      // Make sure that every output of the Tee is initialized, even those
      // that are not connected to anything.
    }
  }

  /**
   * Set up an persistent state that will be kept in the memoization table. This method is mainly
   * here so that subclasses can override it.
   */
  @Override
  protected void initStateInternal(MemoizationTable mt) {
    // Default implementation does nothing; overridden in Dictionary
  }

  /** Create an additional output and return it. */
  public Operator getNextOutput() {
    if (stateInitialized) {
      throw new RuntimeException(
          "Attempted to add an output to a Tee " + "after initializing internal operator state");
    }

    TeeOutput[] newOutputs = new TeeOutput[outputs.length + 1];
    System.arraycopy(outputs, 0, newOutputs, 0, outputs.length);
    newOutputs[outputs.length] = new TeeOutput(this, outputs.length);
    outputs = newOutputs;
    return outputs[outputs.length - 1];
  }

  protected Operator child;

  /**
   * Create a new Tee operator.
   * 
   * @param child root of child operator subtree
   * @param noutputs number of new output operators to create.
   */
  public Tee(Operator child, int noutputs) {
    super(child);

    this.child = child;

    // Calling attach() on the child is now taken care of by the superclass
    // constructor.
    // child.attach();

    // assert noutputs > 0;
    outputs = new TeeOutput[noutputs];
    for (int i = 0; i < outputs.length; i++) {
      outputs[i] = new TeeOutput(this, i);
    }

    // Make sure that Tee time gets charged to the Tee operators
    super.profRecord.viewName = PERF_COUNTER_NAME;
  }

  /**
   * Advance all outputs to the next tuple. Can only be called when all outputs have finished with
   * the current tuple.
   */
  protected void advanceAll(MemoizationTable mt) throws Exception {

    mt.profileEnter(profRecord);

    TupleList curtups = child.getNext(mt);

    for (int i = 0; i < outputs.length; i++) {
      TeeOutput out = outputs[i];
      if (mt.bufferWasCleared(out.bufferIx)) {
        // Someone has already requested that this buffer be cleared;
        // don't bother loading data into it, since the data will never
        // be used.
      } else {
        // System.err.printf(
        // "advanceAll(): Setting tuple for output %s to %s\n",
        // outputs[i], curtup);
        out.setCurtups(curtups, mt);
      }
    }

    mt.profileLeave(profRecord);
  }

  /**
   * Hook for subclasses to set their output schema.
   * 
   * @return output schema for the indicated output of the Tee.
   */
  // protected TupleSchema getOutputSchema(int ix) {
  // return child.getOutputSchema();
  // }
  /**
   * Hook for subclasses to set their output schema. Guaranteed only to be called once.
   * 
   * @return output schema for the indicated output of the Tee.
   */
  protected AbstractTupleSchema createOutputSchema(int ix) {
    // Default implementation passes through the child's schema.
    return child.getOutputSchema();
  }

  /**
   * Method for short-circuiting the buffer reset logic; returns all TeeOutputs below the indicated
   * operator.
   * 
   * @param root operators at the root of a subgraph
   * @return the Tee outputs reachable from the root without encountering another output, or null if
   *         none exist.
   */
  public static TeeOutput[] getBufClearTargets(Operator root) {
    LinkedList<TeeOutput> out = new LinkedList<TeeOutput>();

    LinkedList<Operator> todo = new LinkedList<Operator>();
    todo.add(root);

    while (todo.size() > 0) {
      // Pull off the first element of the list and check if it is a
      // TeeOutput.
      Operator cur = todo.poll();

      if (cur instanceof TeeOutput) {
        out.add((TeeOutput) cur);
      } else {
        // Not a TeeOutput; explore the children of this node.
        for (Operator child : cur.inputs) {
          todo.add(child);
        }
      }
    }

    // Convert to an array, or return null if no answers.
    if (0 == out.size()) {
      return null;
    } else {
      TeeOutput[] ret = new TeeOutput[out.size()];
      return out.toArray(ret);
    }
  }

  /** All schema creation resides with the TeeOutput class. */
  @Override
  protected final AbstractTupleSchema createOutputSchema() {
    throw new RuntimeException("This method should never be called");
  }

  /** All getNext() action should happen in the TeeOutput class. */
  @Override
  protected final void getNextInternal(MemoizationTable mt) throws Exception {
    throw new RuntimeException("This method should never be called");
  }

  /** Use {@link #createOutputTup(int)} instead. */
  @Override
  protected final Tuple createOutputTup() {
    throw new RuntimeException("This method should never be called.");
  }

  /** Convenience method for creating new output tuples. */
  protected Tuple createOutputTup(int outputIx) {
    return outputs[outputIx].createOutputTup();
  }

}
