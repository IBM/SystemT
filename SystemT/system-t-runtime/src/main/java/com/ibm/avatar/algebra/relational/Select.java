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

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.base.MultiInputOperator;
import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.DerivedTupleSchema;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.SelectionPredicate;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;

public class Select extends MultiInputOperator {

  // the selection predicate
  SelectionPredicate predicate;

  /** View names for any locators referenced in the selection predicate. */
  String[] locatorTargetNames;

  /**
   * @param children input operators; by convention, the last one is the "main" input; remaining
   *        inputs are locator inputs
   * @param locatorTargetNames for the first (n-1) children, i.e. the locator inputs, the fully
   *        qualified names of the target views/tables
   * @param predicate
   */
  public Select(Operator[] children, String[] locatorTargetNames, SelectionPredicate predicate) {
    super(children);

    this.predicate = predicate;
    this.locatorTargetNames = locatorTargetNames;
  }

  @Override
  public void reallyEvaluate(MemoizationTable mt, TupleList[] childResults)
      throws TextAnalyticsException {

    // System.err.printf("Select() in view '%s'\n", getViewName());

    // By convention, the last child is the "main" input
    TupleList mainResults = childResults[childResults.length - 1];

    // Use the child results directly as the locator args, ignoring the extra element at the end
    TupleList[] locatorResults = childResults;

    TLIter tupleIterator = mainResults.iterator();
    while (tupleIterator.hasNext()) {
      Tuple childTuple = tupleIterator.next();
      Boolean matchResult = (Boolean) predicate.evaluate(childTuple, locatorResults, mt);

      // unknown is treated as FALSE
      if (matchResult == Boolean.TRUE) {
        addResultTup(childTuple, mt);
      }
    }
  }

  @Override
  protected AbstractTupleSchema createOutputSchema() {

    AbstractTupleSchema inputSchema = getInputOp(getNumInputs() - 1).getOutputSchema();
    AbstractTupleSchema[] locatorSchemas = new AbstractTupleSchema[getNumInputs() - 1];
    for (int i = 0; i < locatorSchemas.length; i++) {
      locatorSchemas[i] = getInputOp(i).getOutputSchema();
    }

    // Don't forget to tell our selection predicate about the schema!
    try {
      predicate.bind(inputSchema, locatorSchemas, locatorTargetNames);
    } catch (FunctionCallValidationException e) {
      throw new RuntimeException(e);
    }

    // We'll be just directly copying tuples through, so create a derived
    // schema as the output.
    DerivedTupleSchema ret = new DerivedTupleSchema(inputSchema);

    // AbstractTupleSchema ret = new TupleSchema(inputSchema, true);

    // System.err.printf("Output schema of Select operator is: %s\n",
    // ret);

    return ret;
  }

  @Override
  protected void initStateInternal(MemoizationTable mt) throws TextAnalyticsException {
    predicate.initState(mt);
  }

  @Override
  protected boolean requiresLemmaInternal() {
    return predicate.requiresLemma();
  }

  /**
   * @return return the predicate of this Select
   */
  public SelectionPredicate getPredicate() {
    return predicate;
  }
}
