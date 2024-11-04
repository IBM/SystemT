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
package com.ibm.avatar.algebra.extract;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.base.MultiInputOperator;
import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.TableReturningFunc;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;

/**
 * Operator wrapper for a call to a user-defined or built-in table function. The operator has one
 * input for every locator column that the table function takes on its input.
 * 
 */
public class ApplyTableFunc extends MultiInputOperator {

  /**
   * The function being wrapped. Initialized during {@link #initStateInternal(MemoizationTable)},
   * then called from {@link #reallyEvaluate(MemoizationTable, TupleList[])}.
   */
  private TableReturningFunc func;

  /**
   * Names associated with the inputs to this operator, which represent the targets of any locator
   * arguments to the table function.
   */
  private String[] targetNames;

  /**
   * Main constructor
   * 
   * @param func the table function that this operator calls
   * @param children subtrees that produce any table-valued inputs to the table function call
   * @param targetNames names of the target view/tables that the child operators represent
   */
  public ApplyTableFunc(TableReturningFunc func, Operator[] children, String[] targetNames) {
    super(children);
    this.func = func;
    this.targetNames = targetNames;
  }

  @Override
  protected void initStateInternal(MemoizationTable mt) throws TextAnalyticsException {
    func.initState(mt);
  }

  @Override
  protected boolean requiresLemmaInternal() {
    return func.requiresLemma();
  }

  @Override
  protected void reallyEvaluate(MemoizationTable mt, TupleList[] childResults) throws Exception {
    TupleList tups = func.evaluate(childResults, mt);
    if (null == tups) {
      throw new FatalInternalError("Implementation of table function %s() returned null",
          func.computeFuncName());
    }
    addResultTups(tups, mt);
  }

  @Override
  protected AbstractTupleSchema createOutputSchema() {
    // Gather up the schemas of any locator inputs to the function
    AbstractTupleSchema[] inputSchemas = new AbstractTupleSchema[inputs.length];
    for (int i = 0; i < inputs.length; i++) {
      inputSchemas[i] = inputs[i].getOutputSchema();
    }

    try {
      // Bind the table function.
      func.bind(inputSchemas, targetNames);
    } catch (FunctionCallValidationException e) {
      // Errors that occur at this point should have been caught during compilation
      throw new FatalInternalError(e, "Error instantiating function %s", this);
    }
    return func.getOutputSchema();
  }

}
