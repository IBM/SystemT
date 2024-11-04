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
package com.ibm.avatar.algebra.consolidate;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.base.SingleInputOperator;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.DerivedTupleSchema;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;

/**
 * LocalAnalysisOperator that computes the set of input tuples that aren't dominated by any other
 * tuple in the input.
 * 
 */

public class Consolidate extends SingleInputOperator {

  private ConsolidateImpl impl;

  /**
   * Create a new Consolidate operator
   * 
   * @param child root of input subtree
   * @param consTypeName type of consolidation to perform -- determines which annotations to keep
   *        and which to throw away
   * @param target column or function call that produces input spans
   * @param priorityTarget column that holds the priority
   * @param priorityDirection
   * @throws ParseException
   */
  public Consolidate(Operator child, String consTypeName, ScalarFunc target,
      ScalarFunc priorityTarget, String priorityDirection) throws ParseException {
    // We have no output column.
    super(child);
    this.impl = ConsolidateImpl.makeImpl(consTypeName, target, priorityTarget, priorityDirection);
  }

  @Override
  protected void reallyEvaluate(MemoizationTable mt, TupleList childResults) throws Exception {

    TupleList outputTups = impl.consolidate(childResults, mt);
    addResultTups(outputTups, mt);
  }

  @Override
  protected AbstractTupleSchema createOutputSchema() {

    AbstractTupleSchema inputSchema = getInputOp(0).getOutputSchema();

    // Make an anonymous version of the input schema.
    // AbstractTupleSchema ret = new TupleSchema(inputSchema, true);

    // We'll be passing through input tuples without copying, so create a
    // derived output schema.
    AbstractTupleSchema ret = new DerivedTupleSchema(inputSchema);

    // Initialize our accessors.
    try {
      impl.bind(inputSchema);
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }

    return ret;

  }

  @Override
  protected void initStateInternal(MemoizationTable mt) throws TextAnalyticsException {
    impl.initState(mt);
  }

  @Override
  protected boolean requiresLemmaInternal() {
    return impl.requiresLemma();
  }
}
