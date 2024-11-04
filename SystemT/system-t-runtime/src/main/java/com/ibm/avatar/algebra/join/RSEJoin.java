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

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.base.RSEOperator;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldCopier;
import com.ibm.avatar.algebra.datamodel.RSEBindings;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.function.predicate.RSEJoinPred;
import com.ibm.avatar.algebra.relational.CartesianProduct;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.logging.Log;

/**
 * A nested-loops join that uses restricted span evaluation to pre-filter the inner operand.
 * 
 */
public class RSEJoin extends CartesianProduct {
  /** Flag for enabling debug messages */
  private final boolean debug = false;

  /** The join predicate */
  private final RSEJoinPred pred;

  /** Accessor for making (deep) copies of the outer tuples. */
  private FieldCopier outerCopyCopier;

  /**
   * We copy the used fields of the outer schema into a set of temporary tuples that are used for
   * computing bindings. This is the schema of the copy.
   */
  private TupleSchema outerCopySchema;

  public RSEJoin(RSEJoinPred pred, Operator outer, Operator inner) {
    super(outer, inner);

    // Verify that the inner operand of the join implements the operations
    // we need
    if (false == (inner instanceof RSEOperator) || false == ((RSEOperator) inner).implementsRSE()) {
      throw new RuntimeException(
          "Inner operand of RSEJoin (" + inner + ") does not implement the RSEOperator API");
    }

    this.pred = pred;
  }

  /**
   * The superclass's implementation of this method calls getNext() on *all* inputs; we don't want
   * to invoke the inner of the join that way, so we override the method. <b>NOTE:</b> This method
   * must be kept in sync with changes in the corresponding superclass method!!!
   */
  @Override
  protected TupleList[] prepareInputs(MemoizationTable mt) throws Exception {

    // To ensure compliance with the superclass's API, we return an array of
    // two TupleLists, one of which is always empty.
    TupleList[] childResults = new TupleList[2];
    childResults[INNER_IX] = new TupleList(inner().getOutputSchema());

    // Fetch the outer's result tuples
    childResults[OUTER_IX] = outer().getNext(mt);

    return childResults;
  }

  @Override
  protected void reallyEvaluate(MemoizationTable mt, TupleList[] childResults) throws Exception {

    if (debug) {
      Log.debug("RSEJoin.reallyEvaluate() with predicate %s", pred.toString());
    }

    // TupleList outerTups = childResults[OUTER_IX];

    // We make a deep copy of the outer tuples to ensure that their fields
    // are in the proper place for the predicate to compute bindings.
    TupleList outerTups = new TupleList(outerCopySchema);
    TLIter itr = childResults[OUTER_IX].iterator();
    while (itr.hasNext()) {
      Tuple copy = outerCopySchema.createTup();
      outerCopyCopier.copyVals(itr.next(), copy);
      outerTups.add(copy);
    }

    // Step 1: Build up the set of bindings for the outer tuples.
    RSEBindings[] b = new RSEBindings[outerTups.size()];

    for (int i = 0; i < outerTups.size(); i++) {
      b[i] = pred.getBindings(outerTups.getElemAtIndex(i), mt);

      if (null != b[i] && b[i].begin < 0) {
        // Sanity check
        throw new RuntimeException(String.format(
            "Bindings specify a match range that starts " + "at %d, which is less than zero.",
            b[i].begin));
      }

    }

    // Step 2: Get results for all the bindings.
    TupleList[] allInners = ((RSEOperator) inner()).getNextRSE(mt, b);

    if (allInners.length != outerTups.size()) {
      throw new RuntimeException("Wrong number of result sets");
    }

    // Step 3: Marshal the results
    for (int i = 0; i < outerTups.size(); i++) {

      // Build up each <outer, inner> combination, apply a final filter,
      // and send tuples that pass to the output.
      Tuple outerTup = outerTups.getElemAtIndex(i);

      TupleList innerTups = allInners[i];

      TLIter innerItr = innerTups.iterator();

      while (innerItr.hasNext()) {
        Tuple innerTup = innerItr.next();

        // Create the join tuple.
        Tuple tup = createOutputTup();
        outerCopier.copyVals(outerTup, tup);
        innerCopier.copyVals(innerTup, tup);

        if (debug) {
          Log.debug("RSEJoin: Creating join tuple\n");
          Log.debug("RSEJoin: --> Outer: %s\n", outerTup);
          Log.debug("RSEJoin: --> Inner: %s\n", innerTup);
          Log.debug("RSEJoin: --> Joined: %s\n", tup);
        }

        // One final level of filtering -- the RSE operation may have
        // returned a superset of the actual inner tuples that pass the
        // join predicate.

        if (pred.oldEvaluate(tup, mt) == Boolean.TRUE) {
          if (debug) {
            Log.debug("RSEJoin: --> passed predicate check\n");
          }
          addResultTup(tup, mt);
        } else { // FALSE OR UNKNOWN
          if (debug) {
            Log.debug("RSEJoin: --> FAILED predicate check \n");
          }
        }
      }
    }
  }

  @Override
  protected AbstractTupleSchema createOutputSchema() {
    // Read the join schema and bind the join predicate to it.
    // Defer to CartesianProduct for the actual schema creation.
    AbstractTupleSchema ret = super.createOutputSchema();
    try {
      pred.oldBind(ret);
    } catch (FunctionCallValidationException e) {
      throw new RuntimeException("Error initializing RSEJoin schema", e);
    }

    // We will be copying the outer tuples to ensure that the predicate can
    // compute bindings on them properly.
    AbstractTupleSchema outerSchema = outer().getOutputSchema();
    outerCopySchema = new TupleSchema(outerSchema, true);
    outerCopyCopier = outerCopySchema.fieldCopier(outerSchema);

    // When we assemble result tuples, we'll use the deep copies of the
    // outer tuples.
    outerCopier = ret.fieldCopier(outerCopySchema);

    return ret;
  }

  @Override
  protected boolean requiresLemmaInternal() {
    return pred.requiresLemma();
  }
}
