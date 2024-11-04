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
package com.ibm.avatar.algebra.join;

import java.util.HashMap;
import java.util.Iterator;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.ConstantTupleList;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.algebra.joinpred.EqualsMP;
import com.ibm.avatar.algebra.joinpred.MergeJoinPred;
import com.ibm.avatar.algebra.relational.CartesianProduct;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.logging.Log;

/**
 * Hash join for equality predicates. Exposes the same API as SortMergeJoin.
 * 
 */
public class HashJoin extends CartesianProduct {
  /** Flag for enabling debug messages */
  public static final boolean debug = false;

  /**
   * Which columns of the outer and inner tuples we evaluate the predicate over.
   */
  private final ScalarFunc outerArg, innerArg;

  /**
   * If one of the inputs to this join is static, we cache the hash table for it here. The table
   * maps a value to the set of tuples with that value. This variable is initialized on the first
   * call while holding a lock.
   */
  private volatile HashMap<Object, TupleList> cachedTable = null;

  /**
   * Main constructor.
   * 
   * @param pred the join predicate that you would pass to SortMergeJoin; must be EqualsMP (merge
   *        join version of Equals, which has information about which input is the outer and which
   *        is the inner)
   * @param outer outer (left) operand of the join
   * @param inner inner (right) operand of the join
   * @throws ParseException
   */
  public HashJoin(MergeJoinPred pred, Operator outer, Operator inner) throws ParseException {
    super(outer, inner);

    setConditionalEval(SortMergeJoin.CONDITIONAL_EVAL);

    if (false == (pred instanceof EqualsMP)) {
      throw new ParseException(String.format("Hash join with non-equality predicate %s", pred));
    }

    // Get handles on the arguments.
    outerArg = pred.getOuterArg();
    innerArg = pred.getInnerArg();
  }

  @Override
  protected void initStateInternal(MemoizationTable mt) throws TextAnalyticsException {
    // Make sure any initialization in the superclass still happens.
    super.initStateInternal(mt);

    innerArg.initState(mt);
    outerArg.initState(mt);
  }

  @Override
  protected boolean requiresLemmaInternal() {
    if (outerArg.requiresLemma())
      return true;
    if (innerArg.requiresLemma())
      return true;
    return false;
  }

  @Override
  protected void reallyEvaluate(MemoizationTable mt, TupleList[] childResults) throws Exception {

    TupleList outerList = childResults[OUTER_IX];
    TupleList innerList = childResults[INNER_IX];

    if (0 == outerList.size() || 0 == innerList.size()) {
      // SPECIAL CASE: At least one input has no tuples.
      // Note that this special-case code also prevents us from caching an
      // empty inner if conditional evaluation has avoided computing the
      // inner.
      if (debug) {
        Log.debug("%s: No results (Inner %d tups, Outer %d tups)", this, innerList.size(),
            outerList.size());
      }
      return;
      // END SPECIAL CASE
    }

    // Determine which input should be used to build the hash table and
    // which should be probed into the table.
    // We do this by setting this variable to true if the outer tuples are
    // used to probe a hash table with the inner tuples in it, or false if
    // the opposite is true.
    boolean outerProbesInner;
    HashMap<Object, TupleList> table;

    if (ConstInput.INNER == getConstInputState()) {
      // Inner never changes; use the precomputed table for it.
      table = getCachedTable(innerList, outerList, mt);
      outerProbesInner = true;
    } else if (ConstInput.OUTER == getConstInputState()) {
      // Outer never changes; use the precomputed table for it.
      table = getCachedTable(innerList, outerList, mt);
      outerProbesInner = false;
    } else if (innerList.size() < outerList.size()) {
      // Both inputs dynamic, but inner is smaller --> build inner, probe
      // outer
      table = buildTable(innerList, innerArg, mt);
      outerProbesInner = true;
    } else {
      // Both inputs dynamic, but outer is smaller --> build outer, probe
      // inner
      table = buildTable(outerList, outerArg, mt);
      outerProbesInner = false;
    }

    if (debug) {
      String tablePolicyStr;
      if (cachedTable == table) {
        tablePolicyStr = "Cached";
      } else {
        tablePolicyStr = outerProbesInner ? "Build on Inner" : "Build on Outer";
      }
      Log.debug("%s:\n" + "Outer: %s\n" + "Inner: %s\n" + "Table: %s\n" + "Probe with: %s", this,
          outerList, innerList, tablePolicyStr, outerProbesInner ? "Outer" : "Inner");

    }

    // Now we go through whichever list is the "probing" list, probing into
    // the hashtable and generating result tuples for anything that comes
    // back.
    ScalarFunc probeFunc = outerProbesInner ? outerArg : innerArg;
    TLIter probeItr = outerProbesInner ? outerList.iterator() : innerList.iterator();
    while (probeItr.hasNext()) {
      Tuple probeTup = probeItr.next();
      Object key = probeFunc.oldEvaluate(probeTup, mt);

      if (key != null) {
        TupleList matches = table.get(key);
        if (null != matches) {
          // Found matches; generate a result tuple for each one.
          TLIter matchItr = matches.iterator();
          while (matchItr.hasNext()) {
            Tuple matchTup = matchItr.next();
            Tuple outerTup = outerProbesInner ? probeTup : matchTup;
            Tuple innerTup = outerProbesInner ? matchTup : probeTup;

            Tuple tup = createOutputTup();
            outerCopier.copyVals(outerTup, tup);
            innerCopier.copyVals(innerTup, tup);

            if (debug) {
              Log.debug("   --> Adding result tuple: %s", tup);
            }

            addResultTup(tup, mt);
          }
        }
      } else {
        if (debug) {
          Log.debug("%s: Null returned as output from probe function %s, skipping result tuple %s.",
              this, probeFunc, probeTup);
        }
      }
    }
  }

  /**
   * @return the cached hash table for the (single) constant input to his join; creates the table if
   *         necessary
   */
  private HashMap<Object, TupleList> getCachedTable(TupleList innerTups, TupleList outerTups,
      MemoizationTable mt) throws Exception {

    if (null == cachedTable) {
      synchronized (this) {
        // Check again; someone may have modified the variable while we
        // were getting a lock.
        if (null == cachedTable) {
          switch (getConstInputState()) {

            // Note that we could actually just cache the results here.
            // Instead, we only cache the hash table for the inner.
            case INNER: {
              cachedTable = buildTable(innerTups, innerArg, mt);
            }
              break;

            case OUTER: {
              cachedTable = buildTable(outerTups, outerArg, mt);
            }
              break;

            default:
              // This method should only be called if exactly one
              // input is constant.
              throw new RuntimeException("Unexpected state");
          }
        }
      }
    }

    return cachedTable;
  }

  /**
   * Utility method to create the hash table for whichever input to the join is the "build" input
   * 
   * @param tups tuples to insert into the table
   * @param func function that returns the join key
   * @return a fully-populated hash table
   * @throws TextAnalyticsException
   */
  private HashMap<Object, TupleList> buildTable(TupleList tups, ScalarFunc func,
      MemoizationTable mt) throws TextAnalyticsException {
    HashMap<Object, TupleList> ret = new HashMap<Object, TupleList>();

    TLIter itr = tups.iterator();
    while (itr.hasNext()) {
      Tuple tup = itr.next();

      Object key = func.oldEvaluate(tup, mt);

      if (key != null) {
        TupleList list = ret.get(key);
        if (null == list) {
          // No list for this key yet --> create one
          list = new TupleList(tups.getSchema());
          ret.put(key, list);
        }

        list.add(tup);
      }
    }

    // Ensure that the table built out of ConstantTupleLists contains entries of type
    // ConstantTupleLists only
    if (tups instanceof ConstantTupleList) {
      for (Iterator<Object> iterator = ret.keySet().iterator(); iterator.hasNext();) {
        Object key = iterator.next();
        ret.put(key, ConstantTupleList.shallowCopy(ret.get(key)));
      }
    }

    return ret;

  }

  @Override
  protected AbstractTupleSchema createOutputSchema() {

    // Bind our accessor functions to the input schemas.
    try {
      AbstractTupleSchema outerSchema = outer().getOutputSchema();
      AbstractTupleSchema innerSchema = inner().getOutputSchema();

      outerArg.oldBind(outerSchema);
      innerArg.oldBind(innerSchema);

    } catch (FunctionCallValidationException e) {
      throw new RuntimeException("Error initializing HashJoin schema", e);
    }

    // Defer to CartesianProduct for the actual schema creation.
    AbstractTupleSchema ret = super.createOutputSchema();

    return ret;
  }

}
