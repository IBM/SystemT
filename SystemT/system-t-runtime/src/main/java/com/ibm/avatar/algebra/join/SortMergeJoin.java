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
/**
 * 
 */
package com.ibm.avatar.algebra.join;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.ObjectID;
import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.joinpred.MergeJoinPred;
import com.ibm.avatar.algebra.relational.CartesianProduct;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.logging.Log;

/**
 * General sort-merge join. Handles both equality and band join predicates.
 * 
 */
public class SortMergeJoin extends CartesianProduct {
  /** Flag for enabling debug messages */
  private static final boolean debug = false;

  /**
   * Set to TRUE to use conditional evaluation (e.g. don't evaluate the inner if there are no outer
   * tuples)
   */
  public static boolean CONDITIONAL_EVAL = true;

  /**
   * Fetcher for getting at the first span in each tuple; only used for debug messages.
   */
  private FieldGetter<Span> getLastSpan;

  /** The join predicate. */
  private final MergeJoinPred pred;

  /**
   * Should we use linear search to find the inner tuples that match an outer tuple, or should we
   * use binary search? Initialized in {@link #initStateInternal(MemoizationTable)}.
   */
  private boolean useLinearSearch = false;

  public SortMergeJoin(MergeJoinPred pred, Operator outer, Operator inner) {
    super(outer, inner);
    this.pred = pred;

    setConditionalEval(CONDITIONAL_EVAL);
  }

  // Schema code comes from base class.

  @Override
  protected void reallyEvaluate(MemoizationTable mt, TupleList[] childResults) throws Exception {

    if (debug) {
      // Print docid if possible
      TupleList tups = childResults[OUTER_IX];
      String docid;

      if (0 == tups.size()) {
        docid = "<no input>";
      } else if (null == getLastSpan) {
        docid = "";
      } else {
        Tuple firstTup = tups.getElemAtIndex(0);
        ObjectID oid = firstTup.getOid();
        docid = (oid == null) ? "" : oid.getStringValue();

      }

      Log.debug("%s: reallyEvaluate(%s):\n", this, docid);
    }

    // Start by making a copy of the inner and outer and sorting them by the
    // appropriate attributes.
    TupleList outerTups = childResults[OUTER_IX];
    TupleList innerTups = childResults[INNER_IX];

    if (0 == outerTups.size() || 0 == innerTups.size()) {
      // SPECIAL CASE: Empty input --> No results
      if (debug) {
        Log.debug("    --> No results\n");
      }
      return;
      // END SPECIAL CASE
    }

    if (outerTups == innerTups) {
      // SPECIAL CASE: Self-join; make a copy in case the two inputs are
      // sorted differently.
      innerTups = new TupleList(innerTups);
      // END SPECIAL CASE
    }

    try {
      outerTups.sort(pred.outerSortComp(), mt);
      innerTups.sort(pred.innerSortComp(), mt);
    } catch (RuntimeException e) {
      // Intercept runtime exceptions and attach some location information
      // to them.

      if (debug) {
        Log.debug("SortMergeJoin: Children are");
        for (int i = 0; i < this.getNumInputs(); i++) {
          Log.debug(this.inputs[i].toString());
        }

        Log.debug("\tSortMergeJoin: %s\n", this.getViewName());
        Log.debug("\t\tSortMergeJoin: outer schema = %s\n", outerTups.getSchema());
        Log.debug("\t\tSortMergeJoin: inner schema = %s\n", innerTups.getSchema());

        Log.debug("\tSortMergeJoin: pred = %s(%s, %s)\n", pred.getClass().getSimpleName(),
            pred.getOuterArg(), pred.getInnerArg());

        TLIter iter = innerTups.iterator();
        while (iter.hasNext()) {
          Tuple tup = iter.next();
          Log.debug("\t\t innerTup = %s\n", tup);
        }
      }

      throw new RuntimeException("Runtime error in sort-merge join in view " + getViewName(), e);
    }

    if (debug) {
      // Dump the inputs in sorted order.
      Log.debug("    Outer: %s\n", outerTups);
      Log.debug("    Inner: %s\n", innerTups);
    }

    pred.outerHook(outerTups, mt);
    pred.innerHook(innerTups, mt);

    int innerIx = 0;

    // Now we go through the outer, finding matching inner tuples.
    for (int outerIx = 0; outerIx < outerTups.size(); outerIx++) {

      // Identify the range of inner tuples that *could* match this outer
      // tuple.
      Pair<Integer, Integer> innerIxRange;
      if (useLinearSearch) {
        innerIxRange = linearSearch(mt, debug, outerTups, innerTups, outerIx, innerIx);
      } else {
        innerIxRange = binarySearch(mt, debug, outerTups, innerTups, outerIx);
      }

      // Now go through every tuple in the range of inners and generate an
      // output for each pair of outer, inner that match the join
      // predicate.
      Tuple outerTup = outerTups.getElemAtIndex(outerIx);

      if (debug) {
        Log.debug("    Outer: %s\n", outerTup);
        int firstInnerIx = innerIxRange.first;
        Tuple firstInnerTup =
            firstInnerIx < innerTups.size() ? innerTups.getElemAtIndex(firstInnerIx) : null;
        int lastInnerIx = innerIxRange.second - 1;
        Tuple lastInnerTup = lastInnerIx >= 0 ? innerTups.getElemAtIndex(lastInnerIx) : null;
        Log.debug("         -> Inners range from" + " %d (%s) to %d (%s)\n", innerIxRange.first,
            firstInnerTup, innerIxRange.second - 1, lastInnerTup);
      }

      for (innerIx = innerIxRange.first; innerIx < innerIxRange.second; innerIx++) {
        Tuple innerTup = innerTups.getElemAtIndex(innerIx);

        if (pred.matchesPred(mt, outerTup, innerTup)) {

          if (debug) {
            Log.debug("    MATCHING Inner: %s\n", innerTup);
          }

          Tuple tup = createOutputTup();
          outerCopier.copyVals(outerTup, tup);
          innerCopier.copyVals(innerTup, tup);

          addResultTup(tup, mt);
        } else {
          if (debug) {
            Log.debug("    NON-MATCHING Inner: %s\n", innerTup);
          }
        }
      }

    }
  }

  /**
   * Use binary search to find the range of inner tuples that could match the indicated outer tuple.
   * 
   * @param mt table for holding persistent state across calls
   * @param debug true to generate debug messages
   * @param outerTups the outer relation of the join
   * @param innerTups inner relation
   * @param outerIx current index into our scan of the sorted outer
   * @return the range of indices in the inner to consider
   * @throws TextAnalyticsException
   */
  private Pair<Integer, Integer> binarySearch(MemoizationTable mt, final boolean debug,
      TupleList outerTups, TupleList innerTups, int outerIx) throws TextAnalyticsException {
    // First, find the beginning of the range. For now, we use a full
    // binary search of the inner tuples.
    // TODO: Shortcut the binary search if possible.
    int minIx = 0;
    int maxIx = innerTups.size();

    while (minIx < maxIx) {
      int testIx = minIx + ((maxIx - minIx) / 2);

      if (pred.beforeMatchRange(mt, outerTups, outerIx, innerTups, testIx)) {
        // Test index is before the matching range; chop off the
        // left-hand side of our test range.
        minIx = testIx + 1;
      } else {
        // Test index is within or beyond the matching range; chop
        // off the right-hand side of the test range.
        maxIx = testIx;
      }
    }

    int innerBeginIx = minIx;

    // Next, find the end of the range of inner tuples that matches this
    // outer tuple. For now, we use a full binary search.
    minIx = 0;
    maxIx = innerTups.size();

    while (minIx < maxIx) {
      int testIx = minIx + ((maxIx - minIx) / 2);

      if (pred.afterMatchRange(mt, outerTups, outerIx, innerTups, testIx)) {
        // Test index is after the matching range; chop off the
        // right-hand side of our test range.
        maxIx = testIx;
      } else {
        // Test index is within or beyond the matching range; chop
        // off the left-hand side of the test range.
        minIx = testIx + 1;
      }
    }

    // Note that this is actually one beyond the last index, inclusive.
    int innerEndIx = maxIx;

    if (debug) {
      Log.debug("  Outer tuple %d: range of inners to check " + "is [%d, %d] (out of [0, %d])\n",
          outerIx, innerBeginIx, innerEndIx, innerTups.size() - 1);
    }

    Pair<Integer, Integer> innerIxRange = new Pair<Integer, Integer>(innerBeginIx, innerEndIx);
    return innerIxRange;
  }

  /**
   * Use a simple linear search to find the range of inner tuples that could match the indicated
   * outer tuple. Can be faster than binary search when the range of matching tuples is tight.
   * 
   * @param mt table for holding persistent state across calls
   * @param debug true to generate debug messages
   * @param outerTups the outer relation of the join
   * @param innerTups inner relation
   * @param outerIx current index into our scan of the sorted outer
   * @return the range of indices in the inner to consider
   * @throws TextAnalyticsException
   */
  private Pair<Integer, Integer> linearSearch(MemoizationTable mt, final boolean debug,
      TupleList outerTups, TupleList innerTups, int outerIx, int lastInnerIx)
      throws TextAnalyticsException {

    // Scan backwards through the inner until we find the first tuple
    // before the match range.
    int testIx = lastInnerIx - 1;
    while (testIx >= 0
        && false == pred.beforeMatchRange(mt, outerTups, outerIx, innerTups, testIx)) {
      testIx--;
    }

    int innerBeginIx = testIx + 1;

    // Now scan forwards through the inner until we find the first tuple
    // *after* the match range.
    testIx = innerBeginIx;
    while (testIx < innerTups.size()
        && false == pred.afterMatchRange(mt, outerTups, outerIx, innerTups, testIx)) {
      testIx++;
    }

    int innerEndIx = testIx;

    Pair<Integer, Integer> innerIxRange = new Pair<Integer, Integer>(innerBeginIx, innerEndIx);
    return innerIxRange;
  }

  @Override
  protected AbstractTupleSchema createOutputSchema() {
    // Set up the join predicate.
    AbstractTupleSchema outerSchema = outer().getOutputSchema();
    AbstractTupleSchema innerSchema = inner().getOutputSchema();
    try {
      pred.bind(outerSchema, innerSchema);
    } catch (FunctionCallValidationException e) {
      throw new RuntimeException(
          String.format("%s: Error binding join predicate to tuple schema.", getViewName()), e);
    }

    // Bind the object that will allow us to print out the docid when debug
    // messages are enabled.
    String lastSpanName = outerSchema.getLastSpanCol();
    if (null != lastSpanName) {
      getLastSpan = outerSchema.spanAcc(lastSpanName);
    } else {
      getLastSpan = null;
    }

    // Defer to CartesianProduct for the actual schema creation.
    return super.createOutputSchema();
  }

  @Override
  protected void initStateInternal(MemoizationTable mt) {
    pred.initState(mt);

    useLinearSearch = pred.useLinearSearch();
  }

  @Override
  protected boolean requiresLemmaInternal() {
    return pred.requiresLemma();
  }
}
