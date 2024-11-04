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
package com.ibm.avatar.algebra.relational;

import java.util.ArrayList;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.base.SingleInputOperator;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.DerivedTupleSchema;
import com.ibm.avatar.algebra.datamodel.TupleComparator;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.algebra.function.base.ScalarReturningFunc;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;

/** Operator that sorts all the tuples from the current document. */
public class Sort extends SingleInputOperator {

  /** List of functions that return the keys to sort on. */
  ArrayList<ScalarFunc> sortKeyFuncs;

  /** Object that knows how to compare two tuples for sorting purposes. */
  TupleComparator comparator;

  public Sort(ArrayList<ScalarFunc> sortKeyFuncs, Operator child) {
    super(child);

    this.sortKeyFuncs = sortKeyFuncs;
  }

  @Override
  protected AbstractTupleSchema createOutputSchema() {

    AbstractTupleSchema inputSchema = inputs[0].getOutputSchema();

    // Now that we have full schema information, initialize the tuple
    // comparator object.
    try {
      for (ScalarReturningFunc sortKeyFunc : sortKeyFuncs) {
        sortKeyFunc.oldBind(inputSchema);
      }
      comparator = TupleComparator.makeComparator(sortKeyFuncs);
    } catch (FunctionCallValidationException e) {
      throw new RuntimeException("Error initializing Sort operator", e);
    }

    // Sort doesn't change the schema, so we pass tuples through. Create a
    // derived schema so that any projections remain in place.
    return new DerivedTupleSchema(inputSchema);
  }

  @Override
  protected void reallyEvaluate(MemoizationTable mt, TupleList childResults) throws Exception {

    // Copy the results to the output; the input may be shared with someone
    // else, and we don't want to change their tuple order.
    addResultTups(childResults, mt);

    // Sort our output buffer in place.
    TupleList results = mt.getResultsBuf(resultsBufIx);
    results.sort(comparator, mt);

  }

  @Override
  protected boolean requiresLemmaInternal() {
    ScalarFunc sortKeyFunc;

    for (int idx = 0; idx < sortKeyFuncs.size(); idx++) {
      sortKeyFunc = sortKeyFuncs.get(idx);
      if (sortKeyFunc.requiresLemma())
        return true;
    }

    // No lemma references
    return false;
  }

}
