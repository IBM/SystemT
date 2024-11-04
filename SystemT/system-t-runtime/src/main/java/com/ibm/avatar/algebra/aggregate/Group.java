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
package com.ibm.avatar.algebra.aggregate;

import java.util.ArrayList;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.base.SingleInputOperator;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.FieldSetter;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleComparator;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.function.base.AggFunc;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.algebra.function.base.ScalarReturningFunc;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;

/**
 * The Group By operator. Sorts the input on group by values and computes any aggregates in the
 * select list.
 * 
 */
public class Group extends SingleInputOperator {

  /** List of functions to group by */
  ArrayList<ScalarFunc> groupByFuncs;

  /** Convenience flag indicating whether we group by any columns or not */
  boolean isGrouping;

  /** List of aggregate functions to compute */
  ArrayList<AggFunc> aggFuncs;

  /**
   * List of aliases used in the SELECT clause for the aggregate functions we compute
   */
  ArrayList<String> aggAliases;

  /** Input schema */
  AbstractTupleSchema inputSchema;

  /**
   * Getters for accessing values from the input.
   */
  protected ArrayList<FieldGetter<Object>> inAccessors;

  /**
   * Setter for putting our aggregate results into place.
   */
  protected ArrayList<FieldSetter<Object>> outAccessors;

  /**
   * Comparator for sorting tuples by span begin. Initialized during {@link #createInteriorSchema()}
   */
  TupleComparator sortOrder = null;

  /**
   * Create a new Group operator.
   * 
   * @param input root of the operator tree that produces our inputs
   */
  public Group(ArrayList<ScalarFunc> groupByFuncs, ArrayList<AggFunc> aggFuncs,
      ArrayList<String> aggAliases, Operator input) {
    super(input);

    this.groupByFuncs = groupByFuncs;
    this.aggFuncs = aggFuncs;
    this.aggAliases = aggAliases;

    // Make sure we set the flag
    this.isGrouping = groupByFuncs.isEmpty() ? false : true;
  }

  @Override
  protected AbstractTupleSchema createOutputSchema() {

    String[] colnames = new String[aggFuncs.size()];
    FieldType[] coltypes = new FieldType[aggFuncs.size()];
    AggFunc aggFunc;
    AbstractTupleSchema interiorOutputSchema;

    // Get the input schema.
    inputSchema = inputs[0].getOutputSchema();

    try {

      // If grouping by anything, set up the sort comparator.
      if (isGrouping) {
        for (ScalarReturningFunc groupByFunc : groupByFuncs) {
          groupByFunc.oldBind(inputSchema);
        }

        sortOrder = TupleComparator.makeComparator(groupByFuncs);
      }
    } catch (FunctionCallValidationException e) {
      // This kind of error should be caught in the AQL compiler!!!
      throw new FatalInternalError(e);
    }

    // Create the output schema. Start with the input schema,
    // and for each aggregate function add one output field.
    try {
      for (int idx = 0; idx < aggFuncs.size(); idx++) {

        aggFunc = aggFuncs.get(idx);
        aggFunc.oldBind(inputSchema);

        colnames[idx] = aggAliases.get(idx);
        coltypes[idx] = aggFunc.returnType();
      }
    } catch (FunctionCallValidationException e) {
      // This kind of error should be caught in the AQL compiler!!!
      throw new FatalInternalError(e);
    }

    // The output schema is a superset of the input schema
    // because the select list may involve other fields besides aggregates.
    // The job of throwing away the unnecessary fields is left to the
    // Project operator.
    // Note that the validation phase for the GROUP BY clause already
    // ensured that
    // the SELECT list of the statement does not contain any non-grouping
    // fields,
    // unless they appear in aggregate functions.
    interiorOutputSchema = new TupleSchema(inputSchema, colnames, coltypes);

    // Create accessors for corresponding fields in the input and output
    // schemas
    inAccessors = new ArrayList<FieldGetter<Object>>();
    outAccessors = new ArrayList<FieldSetter<Object>>();
    String fieldName;
    FieldType type;

    for (int idx = 0; idx < inputSchema.size(); idx++) {
      fieldName = inputSchema.getFieldNameByIx(idx);
      type = inputSchema.getFieldTypeByIx(idx);
      inAccessors.add(inputSchema.genericGetter(fieldName, type));
      outAccessors.add(interiorOutputSchema.genericSetter(fieldName, type));
    }

    // Create out accessors for each of the aggregate output fields
    for (int idx = 0; idx < colnames.length; idx++) {
      outAccessors.add(interiorOutputSchema.genericSetter(colnames[idx], coltypes[idx]));
    }

    return interiorOutputSchema;
  }

  @Override
  protected void reallyEvaluate(MemoizationTable mt, TupleList childResults) throws Exception {

    // Copy the input.
    TupleList input = new TupleList(childResults);

    if (isGrouping) {
      // Sort, group, and compute aggregates for each group
      input.sort(sortOrder);
      doGroup(mt, input);
    } else {
      // Process the entire input in one shot
      process(mt, input);
    }
  }

  /**
   * Calculates the groups. For each group, computes any aggregate values and generates the
   * corresponding output tuple.
   * 
   * @param input input tuples, sorted by the group by values
   */
  private void doGroup(MemoizationTable mt, TupleList input) throws Exception {

    Tuple currentTuple, nextTuple;
    TupleList currentGroup = new TupleList(inputSchema);

    // We iterate through the input tuples, outputting one tuple for each
    // group.
    // Note that we're relying on the input being sorted by the group by
    // values
    for (int idx = 0; idx < input.size(); idx++) {

      currentTuple = input.getElemAtIndex(idx);
      currentGroup.add(currentTuple);

      if (idx == input.size() - 1) {
        // Last element in the input marks the end of the last group
        // Process it !
        process(mt, currentGroup);
        currentGroup.clear();
      } else {

        // Lookahead one tuple
        nextTuple = input.getElemAtIndex(idx + 1);

        if (sortOrder.compare(currentTuple, nextTuple) == 0)
          // If equal, proceed to the next input tuple
          continue;
        else {
          // Otherwise, we have one complete group, process it !
          process(mt, currentGroup);
          currentGroup.clear();
        }
      }
    }
  }

  /**
   * Compute an output tuple that contains the result of the aggregate functions on the set of input
   * tuples.
   * 
   * @param mt
   * @param input
   * @throws TextAnalyticsException
   */
  private void process(MemoizationTable mt, TupleList input) throws TextAnalyticsException {

    Tuple outTup = createOutputTup();

    FieldGetter<Object> inAcc;
    FieldSetter<Object> outAcc;

    // Copy all values from the first tuple in the input group to the output
    // tuple
    if (input.size() > 0) {
      Tuple inTup = input.getElemAtIndex(0);

      for (int idx = 0; idx < inputSchema.size(); idx++) {
        inAcc = inAccessors.get(idx);
        outAcc = outAccessors.get(idx);
        outAcc.setVal(outTup, inAcc.getVal(inTup));
      }
    }

    int outOffset = inputSchema.size();

    // For each aggregate function, evaluate it and add the result to the
    // output tuple
    for (int idx = 0; idx < aggFuncs.size(); idx++) {
      outAcc = outAccessors.get(outOffset + idx);
      outAcc.setVal(outTup, aggFuncs.get(idx).oldEvaluate(input, mt));
    }

    // Add the new tuple to the result
    addResultTup(outTup, mt);

  }

  @Override
  protected void initStateInternal(MemoizationTable mt) throws TextAnalyticsException {
    if (null == mt) {
      throw new FatalInternalError("Null MemoizationTable pointer passed to %s operator.",
          this.getClass().getSimpleName());
    }

    AggFunc aggFunc;
    ScalarFunc groupByFunc;

    for (int idx = 0; idx < aggFuncs.size(); idx++) {

      aggFunc = aggFuncs.get(idx);

      if (null == aggFunc) {
        throw new FatalInternalError("Group operator has no aggregate function at index %d", idx);
      }

      aggFunc.initState(mt);
    }

    for (int idx = 0; idx < groupByFuncs.size(); idx++) {

      groupByFunc = groupByFuncs.get(idx);

      if (null == groupByFunc) {
        throw new FatalInternalError("Group operator has no group-by function at index %d", idx);
      }

      groupByFunc.initState(mt);
    }
  }

  @Override
  protected boolean requiresLemmaInternal() {
    AggFunc aggFunc;
    ScalarFunc groupByFunc;

    for (int idx = 0; idx < aggFuncs.size(); idx++) {

      aggFunc = aggFuncs.get(idx);

      if (null == aggFunc) {
        throw new FatalInternalError("Group operator has no aggregate function at index %d", idx);
      }

      if (aggFunc.requiresLemma())
        return true;
    }

    for (int idx = 0; idx < groupByFuncs.size(); idx++) {

      groupByFunc = groupByFuncs.get(idx);

      if (null == groupByFunc) {
        throw new FatalInternalError("Group operator has no group-by function at index %d", idx);
      }

      if (groupByFunc.requiresLemma())
        return true;
    }

    // No lemma references
    return false;
  }
}
