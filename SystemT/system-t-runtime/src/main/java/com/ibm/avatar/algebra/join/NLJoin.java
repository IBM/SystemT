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
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.SelectionPredicate;
import com.ibm.avatar.algebra.relational.CartesianProduct;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.logging.Log;

/**
 * Nested-loops join operator. Takes three arguments:
 * <ul>
 * <li>Inner and outer operands
 * <li>A predicate to apply to the cartesian product of the outer and inner.
 * </ul>
 * Column indices in the join predicate must refer to offsets into the cross-product tuple. Columns
 * of the outer come first, followed by the columns of the inner.
 * 
 */
public class NLJoin extends CartesianProduct {
  /** Flag for enabling debug messages */
  private static final boolean debug = false;

  /**
   * Set to TRUE to use conditional evaluation (e.g. don't evaluate the inner if there are no outer
   * tuples)
   */
  // We now use the variable in SortMergeJoin.
  // public static boolean CONDITIONAL_EVAL = true;

  /** The join predicate */
  private final SelectionPredicate pred;

  /**
   * sources of any locator arguments referenced in the join predicate
   */
  Operator[] locatorArgs;

  /**
   * names of views at the other end of elements of {@link #locatorArgs}
   */
  String[] locatorNames;

  /**
   * @param pred join predicate
   * @param outer outer (left) input to the join
   * @param inner inner (right) input to the join
   * @param locatorArgs sources of any locator arguments referenced in the join predicate
   * @param locatorNames names of views at the other end of elements of locatorArgs
   */
  public NLJoin(SelectionPredicate pred, Operator outer, Operator inner, Operator[] locatorArgs,
      String[] locatorNames) {
    super(outer, inner, locatorArgs);
    this.pred = pred;
    this.locatorArgs = locatorArgs;
    this.locatorNames = locatorNames;

    setConditionalEval(SortMergeJoin.CONDITIONAL_EVAL);
  }

  private static final TupleList[] EMPTY_ARRAY = {};

  @Override
  protected void reallyEvaluate(MemoizationTable mt, TupleList[] childResults) throws Exception {

    TupleList outerList = childResults[CartesianProduct.OUTER_IX];
    TupleList innerList = childResults[CartesianProduct.INNER_IX];

    TupleList[] locatorResults = EMPTY_ARRAY;
    if (locatorNames.length > 0) {
      locatorResults = new TupleList[locatorNames.length];

      // Locator results are at the *end* of the array
      System.arraycopy(childResults, 2, locatorResults, 0, locatorResults.length);
    }

    TLIter outerItr = outerList.iterator();

    while (outerItr.hasNext()) {
      Tuple outer = outerItr.next();

      TLIter innerItr;
      if (outerList == innerList) {
        // Self-join; need a spare iterator
        innerItr = innerList.newIterator();
      } else {
        innerItr = innerList.iterator();
      }

      while (innerItr.hasNext()) {
        Tuple inner = innerItr.next();

        // Construct the next tuple in the cross-product.
        Tuple tup = createOutputTup();
        outerCopier.copyVals(outer, tup);
        innerCopier.copyVals(inner, tup);

        if (debug) {
          Log.debug("NLJoin: Creating join tuple\n");
          Log.debug("NLJoin: --> Outer: %s\n", outer);
          Log.debug("NLJoin: --> Inner: %s\n", inner);
          Log.debug("NLJoin: --> Joined: %s\n", tup);
        }

        if (pred.evaluate(tup, locatorResults, mt) == Boolean.TRUE) {
          if (debug) {
            Log.debug("NLJoin: --> passed predicate check %s \n", pred.toString());
          }
          addResultTup(tup, mt);
        } else { // FALSE OR UNKNOWN
          if (debug) {
            Log.debug("NLJoin: --> FAILED predicate check %s \n", pred.toString());
          }
        }
      }
    }
  }

  @Override
  protected AbstractTupleSchema createOutputSchema() {
    // Read the join schema and bind the join predicate to it.
    // Defer to CartesianProduct for the actual schema creation.
    AbstractTupleSchema interiorSchema = super.createOutputSchema();

    AbstractTupleSchema[] locatorSchemas = new AbstractTupleSchema[locatorArgs.length];
    for (int i = 0; i < locatorSchemas.length; i++) {
      locatorSchemas[i] = locatorArgs[i].getOutputSchema();
    }

    try {
      pred.bind(interiorSchema, locatorSchemas, locatorNames);
    } catch (FunctionCallValidationException e) {
      throw new RuntimeException("Error initializing NLJoin schema", e);
    }

    return interiorSchema;
  }

  @Override
  protected void initStateInternal(MemoizationTable mt) throws TextAnalyticsException {
    pred.initState(mt);
  }

  @Override
  protected boolean requiresLemmaInternal() {
    return pred.requiresLemma();
  }
}
