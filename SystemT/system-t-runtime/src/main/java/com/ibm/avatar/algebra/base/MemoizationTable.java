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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.InternalUtils;
import com.ibm.avatar.algebra.output.Sink;
import com.ibm.avatar.algebra.util.dict.DictMemoization;
import com.ibm.avatar.algebra.util.string.ReusableString;
import com.ibm.avatar.algebra.util.tokenize.BaseOffsetsList;
import com.ibm.avatar.algebra.util.tokenize.DerivedOffsetsList;
import com.ibm.avatar.algebra.util.tokenize.StandardTokenizer;
import com.ibm.avatar.algebra.util.tokenize.Tokenizer;
import com.ibm.avatar.api.OperatorGraphImpl;
import com.ibm.avatar.logging.Log;
import com.ibm.systemt.regex.api.Regex;
import com.ibm.systemt.regex.api.RegexMatcher;
import com.ibm.systemt.regex.api.TokRegexMatcher;

/**
 * Hashtable for memoizing the results of local analysis operators. Keeping state in this table
 * helps make the operators stateless.
 * 
 */
public class MemoizationTable {

  private static final boolean debugTupleBuffers = false;

  /** A lightweight replacement for Thread.interrupted() */
  public boolean interrupted = false;

  /** Tokenizer implementation shared by all users of this MemoizationTable. */
  private Tokenizer tokenizer = new StandardTokenizer();

  /** Thread-local data structures used during dictionary initialization. */
  private DictMemoization dictMemoization;

  /**
   * Which operators have initialized their state inside the MemoizationTable?
   */
  private HashSet<Operator> initialized = new HashSet<Operator>();

  /** Cached intermediate results for operators that need to memoize them. */
  private TupleList[] cachedResults = null;

  /**
   * Special "tuple" value for an entry in cachedResults. Indicates that the entry has been used,
   * then cleared, for the current document.
   */
  public static final TupleList BUF_CLEARED = TupleList.createDummyTupleList();

  /**
   * A table of persistent results TupleLists, one per operator.
   */
  private ArrayList<TupleList> resultsLists = new ArrayList<TupleList>();

  /**
   * For debugging (only used if {@link #debugTupleBuffers} is true); maps from result cache index
   * to operator.
   */
  private ArrayList<Operator> resultCacheOwners = //
      debugTupleBuffers ? new ArrayList<Operator>() : null;

  private int numResultCaches = 0;

  /** An array of persistent counters. */
  private int[] counters = null;

  private int numCounters = 0;

  /**
   * Create a new persistent counter.
   * 
   * @return index of the counter created.
   */
  public int createCounter() {
    // We will resize the counters array when it is actually used.
    return numCounters++;
  }

  /**
   * Resize the counters array so that it has at least the requested number of entries.
   * 
   * @param ix index that the caller wants to access
   */
  private void resizeCounters(int ix) {
    numCounters = Math.max(numCounters, ix + 1);

    if (null == counters) {
      counters = new int[numCounters];
    }

    if (counters.length != numCounters) {
      // Need to resize.
      int[] tmp = new int[numCounters];
      System.arraycopy(counters, 0, tmp, 0, counters.length);
      counters = tmp;
    }
  }

  public void incrCounter(int ix) {
    resizeCounters(ix);
    counters[ix]++;
  }

  public int getCounterVal(int ix) {
    resizeCounters(ix);
    return counters[ix];
  }

  /** Lowest index in cachedResults that contains tuples. */
  // private int minCacheIx = 0;
  // private HashMap<PullOperator, Tuple> cachedResults = new
  // HashMap<PullOperator, Tuple>();
  /**
   * Output buffers for output operators in the plan. The runtime type of each buffer depends on the
   * type of output operator. Not every type of output operator creates an output buffer.
   */
  private HashMap<Operator, OutputBuffer> outputBufs = new HashMap<Operator, OutputBuffer>();

  /**
   * Internal lists of offsets into strings; used mostly for storing dictionary matches.
   */
  private ArrayList<BaseOffsetsList> offsetsLists = new ArrayList<BaseOffsetsList>();

  /**
   * Flags that indicate which inputs to various Sink operators in the plan are enabled.
   */
  private HashMap<Sink, boolean[]> enabledFlags = new HashMap<Sink, boolean[]>();

  /** Flag that gets set to true when we've processed the last input document. */
  private boolean endOfInput = false;

  /**
   * Convenience constructor for when you want to use the default tokenizer.
   */
  public MemoizationTable(Operator root) throws Exception {
    this(root, new StandardTokenizer());
  }

  /**
   * @param root operator at the root of the operator graph that this memoization table will be used
   *        for.
   * @param tokenizer object that knows how to tokenize text.
   */
  public MemoizationTable(Operator root, Tokenizer tokenizer) throws Exception {
    this.tokenizer = tokenizer;
    reinit(root);
  }

  /**
   * Tell the MemoizationTable that the indicated operator has initialized any state it will need
   * inside the table.
   */
  public void setOpInitialized(Operator op) {
    initialized.add(op);
  }

  /**
   * @return true if this operator has initialized its state inside the MemoizationTable (used to
   *         ensure idempotency)
   */
  public boolean getOpInitialized(Operator op) {
    return initialized.contains(op);
  }

  /**
   * Allocate a reuable list of string offsets.
   * 
   * @return index of the list; use this index with getOffsetsList() to retrieve the object.
   */
  public int createOffsetsList() {
    // Thread.dumpStack();

    offsetsLists.add(new BaseOffsetsList());
    return offsetsLists.size() - 1;
  }

  /**
   * Allocate a persistent results buffer for a given operator.
   * 
   * @return index of the list; use with getResultsList() to retrieve the list.
   */
  public int createResultsBuf(AbstractTupleSchema schema) {
    resultsLists.add(new TupleList(schema));
    return resultsLists.size() - 1;
  }

  /**
   * @param index index of a list created with {@link #createOffsetsList()}
   * @return tokenizer object in question
   */
  public BaseOffsetsList getOffsetsList(int index) {
    // The caller may have gotten the tokenizer index from another instance
    // of MemoizationTable (e.g. one that belongs to a different thread). If
    // there isn't a tokenizer for the indicated index, create one.
    while (index >= offsetsLists.size()) {
      createOffsetsList();
    }

    return offsetsLists.get(index);
  }

  private DerivedOffsetsList tempOffsetsList = new DerivedOffsetsList();

  /**
   * @return temporary derived offsets list for use when you need to tokenize a chunk of the
   *         document.
   */
  public DerivedOffsetsList getTempOffsetsList() {
    return tempOffsetsList;
  }

  private ReusableString reusablestr = new ReusableString();

  /**
   * @return a String-like object that can be used over and over again without polluting the heap.
   */
  public ReusableString getReusableStr() {
    return reusablestr;
  }

  /**
   * Create a new location for caching operator results.
   * 
   * @param op the operator requesting a cache location (used for debugging)
   * @return index of the result cache location
   */
  public int createResultsCache(Operator op) {
    int ret = numResultCaches;

    numResultCaches++;

    if (debugTupleBuffers) {
      resultCacheOwners.add(op);
      System.err.printf("Created results cache index %d for %s\n", ret, op);
    }
    return ret;
  }

  /**
   * Internal function to resize the results cache so that it contains the indicated index. This
   * method is necessary because an operator graph may be initialized with one MemoizationTable,
   * then used with another one.
   * 
   * @param ix index that must exist in the results cache.
   */
  private void resizeResultsCache(int ix) {
    if (ix < numResultCaches) {
      // Result cache is big enough.
      return;
    } else {
      if (debugTupleBuffers) {
        System.err.printf("Resizing results cache from %d to %d\n", cachedResults.length, ix + 1);
      }

      // Result cache needs to be expanded.
      numResultCaches = ix + 1;
      TupleList[] tmp = new TupleList[numResultCaches];
      System.arraycopy(cachedResults, 0, tmp, 0, cachedResults.length);
      cachedResults = tmp;
    }
  }

  /**
   * Store the results of evaluating an operator.
   * 
   * @param ix index of the results cache entry, as returned by {@link #createResultsCache()}
   * @param results top-level tuple containing the latest results for said operator.
   */
  public void cacheResults(int ix, TupleList results) {
    cacheResults(ix, results, false);
  }

  /**
   * Special version of {@link #cacheResults(int, TupleList)} for INTERNAL use only.
   * 
   * @param force true to skip sanity checks
   */
  public void cacheResults(int ix, TupleList results, boolean force) {
    if (debugTupleBuffers) {
      System.err.printf("     cacheResults(%d)\n", ix);
    }

    // if (ix < minCacheIx) {
    // throw new RuntimeException(
    // String.format("Tried to set results buffer %d " +
    // "after clearing 0 through %d", ix, minCacheIx));
    // }

    resizeResultsCache(ix);
    if (false == force && null != cachedResults[ix]) {
      throw new RuntimeException(
          String.format("Tried to store results twice for results index %d", ix));
    }
    cachedResults[ix] = results;
  }

  /**
   * Clear any cached results for the indicated index, and mark the index as cleared.
   * 
   * @param ix index of the results cache entry, as returned by {@link #createResultsCache()}
   */
  public void clearCachedResults(int ix) {
    if (debugTupleBuffers) {
      System.err.printf("     clearCachedResults(%d)\n", ix);
    }

    resizeResultsCache(ix);

    // We use a special value to distinguish "used, then cleared" entries
    // from "never used" entries.
    cachedResults[ix] = BUF_CLEARED;
  }

  /**
   * @param ix index of the results cache entry, as returned by {@link #createResultsCache()}
   */
  public TupleList getCachedResults(int ix) {
    if (debugTupleBuffers) {
      System.err.printf("   getCachedResults(%d)\n", ix);
    }

    resizeResultsCache(ix);

    TupleList ret = cachedResults[ix];

    return ret;
  }

  /**
   * @return true if the indicated tuple buffer has not been used since the last full cache reset.
   */
  public boolean bufferIsUnused(int ix) {
    resizeResultsCache(ix);
    return (null == cachedResults[ix]);
  }

  /**
   * @return true if {@link #clearCachedResults(int)} has been called on the indicated index since
   *         the last full cache reset.
   */
  public boolean bufferWasCleared(int ix) {
    resizeResultsCache(ix);
    return (BUF_CLEARED == cachedResults[ix]);
  }

  public void cacheOutputBuf(Operator op, OutputBuffer buf) {
    cacheOutputBuf(op, buf, false);
  }

  public void cacheOutputBuf(Operator op, OutputBuffer buf, boolean replace) {
    if ((false == replace) && outputBufs.containsKey(op)) {
      throw new RuntimeException(String.format("Tried to store two buffers for %s", op));
    }
    outputBufs.put(op, buf);
  }

  /**
   * Regular expression matchers for repeated use across documents.
   */
  private HashMap<Regex, RegexMatcher> cachedMatchers = new HashMap<Regex, RegexMatcher>();

  public RegexMatcher getMatcher(Regex r) {
    RegexMatcher ret = cachedMatchers.get(r);
    if (null == ret) {
      ret = r.matcher("");
      cachedMatchers.put(r, ret);
    }
    return ret;
  }

  /**
   * Token-based regular expression matcher for repeated use across documents and different
   * collections of regular expressions.
   */
  private TokRegexMatcher tokenMatcher = new TokRegexMatcher();

  /**
   * @return Token-based regular expression matcher for repeated use across documents and different
   *         collections of regular expressions.
   */
  public TokRegexMatcher getTokenMatcher() {
    return tokenMatcher;
  }

  public OutputBuffer getOutputBuf(Operator op) {
    return outputBufs.get(op);
  }

  public void cacheEnabledFlags(Sink sink, boolean[] flags) {
    if (enabledFlags.containsKey(sink)) {
      throw new RuntimeException(
          String.format("Tried to store two sets of 'enabled' flags for %s", sink));
    }
    enabledFlags.put(sink, flags);
  }

  public boolean[] getEnabledFlags(Sink sink) {
    return enabledFlags.get(sink);
  }

  public void setEndOfInput() {
    this.endOfInput = true;
  }

  public boolean endOfInput() {
    return this.endOfInput;
  }

  public boolean haveMoreInput() {
    return !endOfInput();
  }

  /**
   * Remove cached data from previous runs. Does note clear the "end of input" bit. Call this method
   * in between invocations of {@link PullOperator#getNext(MemoizationTable_new)}.
   */
  public void resetCache() {

    if (debugTupleBuffers) {
      System.err.printf("Resetting cache...\n");
    }

    // Recycle any cached results.
    resetCachedResults();

    // Clear out any regex matchers.
    resetCachedMatchers();

    // Clear cached offsets lists, keeping the data structures in place for
    // future use.
    int nlist = offsetsLists.size();
    // System.err.printf("Resetting %d offsets lists.\n", nlist);
    for (int i = 0; i < nlist; i++) {
      offsetsLists.get(i).reset();
    }

    // Reset the profiling stack, in case execution was interrupted in the
    // middle.
    profileStackHeight = 0;
  }

  /** Reset the results caches. */
  private void resetCachedResults() {
    // Recreate the tuple buffers array if necessary.
    if (null == cachedResults || cachedResults.length != numResultCaches) {
      cachedResults = new TupleList[numResultCaches];
    }

    // Recreate the counters array if necessary
    if (null == counters || counters.length != numCounters) {
      counters = new int[numCounters];
    }

    if (debugTupleBuffers) {
      for (int i = 1; i < cachedResults.length; i++) {
        if (BUF_CLEARED != cachedResults[i]) {
          if (null == cachedResults[i]) {
            System.err.printf("Buffer %d (Owner: %s) was never used.\n", i,
                resultCacheOwners.get(i));
          } else {
            System.err.printf("Buffer %d (Owner: %s) was not cleared.\n", i,
                resultCacheOwners.get(i));
          }
        }
      }
    }

    Arrays.fill(cachedResults, 0, cachedResults.length, null);
    // Arrays.fill(cachedResults, minCacheIx, cachedResults.length, null);
    // minCacheIx = 0;
    Arrays.fill(counters, 0);
  }

  /**
   * Reset all cached regex matchers. This reset is important because Java regexes in particular can
   * keep pointers to very long strings around.
   */
  private void resetCachedMatchers() {
    for (RegexMatcher matcher : cachedMatchers.values()) {
      matcher.reset("");
    }
  }

  /**
   * Completely reset the memoization table to prepare it for a different operator graph.
   */
  public void reinit(Operator root) throws Exception {
    outputBufs.clear();

    cachedMatchers.clear();

    endOfInput = false;

    dictMemoization = new DictMemoization();

    if (null != root) {
      root.initState(this);
      // The graph may have no input at all.
      root.checkEndOfInput(this);
    }

    // Recycle any cached results, and initialize the buffer to its new
    // size.
    resetCachedResults();

    // Remove any tables used during dictionary initialization; they should
    // no longer be needed.
    clearDictMemoization();
  }

  public void closeOutputs() {
    for (OutputBuffer buf : outputBufs.values()) {
      buf.close();
    }
  }

  /**
   * @return implementation of tokenization to be used by all operators that use this
   *         MemoizationTable
   */
  public Tokenizer getTokenizer() {
    return tokenizer;
  }

  public DictMemoization getDictMemoization() {
    if (null == dictMemoization) {
      throw new RuntimeException(
          "getDictMemoization() called after dictionary " + "memoization tables have been cleared");
    }
    return dictMemoization;
  }

  /**
   * Clean out the tables used for memoization while initializing dictionaries.
   */
  public void clearDictMemoization() {
    dictMemoization = null;
  }

  /**
   * @param ix index returned previously by {@link #createResultsBuf(AbstractTupleSchema)}
   * @return the indicated buffer
   */
  public TupleList getResultsBuf(int ix) {
    return resultsLists.get(ix);
  }

  /**
   * Mark the results tuple lists as not in use, but don't clear the tuples inside those lists.
   * Called only on exceptions in {@link OperatorGraphImpl#execute()} to ensure that the
   * OperatorGraph state is completely reset and there are no tuple lists left in use when the next
   * document comes in.
   */
  public void markResultsBufDone() {
    InternalUtils util = new InternalUtils();

    for (int ix = 0; ix < resultsLists.size(); ix++) {
      util.markDone(resultsLists.get(ix));
    }
  }

  /**
   * Create a new, empty results buffer for an operator; usually invoked so that the garbage
   * collector can get rid of a buffer that has grown too large.
   * 
   * @param ix index returned previously by {@link #createResultsBuf(AbstractTupleSchema)}
   * @param schema schema for the operator that owns this buffer
   */
  public void resetResultsBuf(int ix, AbstractTupleSchema schema) {
    resultsLists.set(ix, new TupleList(schema));
  }

  /*
   * PROFILING SUPPORT
   */

  /**
   * Stack that is maintained with the current position. We use an array so that we can get
   * concurrent access.
   */
  private int profileStackSize = 512;

  private ProfileRecord[] profileStack = new ProfileRecord[profileStackSize];

  private int profileStackHeight = 0;

  private static final boolean DEBUG_PROFILING = false;

  /**
   * Mark the time at which the runtime enters a particular part of the operator graph.
   * 
   * @param record object that indicates the current location in the operator graph
   */
  public void profileEnter(ProfileRecord record) {
    if (profileStackHeight >= profileStackSize) {
      resizeProfileStackSize();
    }
    profileStack[profileStackHeight] = record;
    profileStackHeight++;

    if (DEBUG_PROFILING) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < profileStackHeight - 1; i++) {
        sb.append(" ");
      }
      sb.append(String.format("Enter: %s", record.operator));
      Log.debug("%s", sb.toString());
    }
  }

  /**
   * Internal function to resize the underlying array of the profileStack to avoid overflow
   */
  private void resizeProfileStackSize() {
    int oldProfileStackSize = profileStackSize;
    profileStackSize *= 2; // increasing the size 2 folds
    ProfileRecord[] newProfileStack = new ProfileRecord[profileStackSize];
    System.arraycopy(profileStack, 0, newProfileStack, 0, oldProfileStackSize);
    profileStack = newProfileStack;
  }

  /**
   * Mark the time at which the runtime leaves a particular part of the operator graph.
   * 
   * @param record object that indicates the current location in the operator graph
   */
  public void profileLeave(ProfileRecord record) {
    // Verify that the caller is following a proper stack discipline.
    ProfileRecord topOfStack = profileStack[profileStackHeight - 1];
    if (topOfStack != record) {
      throw new RuntimeException("Profile records don't match.");
    }
    profileStackHeight--;

    if (DEBUG_PROFILING) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < profileStackHeight; i++) {
        sb.append(" ");
      }
      sb.append(String.format("Leave: %s", record.operator));
      Log.debug("%s", sb.toString());
    }
  }

  /**
   * @return whatever location in the operator graph is at the top of the stack currently; this
   *         should be the location that the underlying thread is currently executing at.
   */
  public ProfileRecord profilePeek() {
    // profileStackHeight may change underneath us, so make a copy.
    int height = profileStackHeight;
    if (0 == height) {
      return null;
    } else {
      return profileStack[height - 1];
    }
  }
}
