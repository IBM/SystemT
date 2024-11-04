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
package com.ibm.avatar.algebra.function.base;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.Token;

/**
 * Superclass for scalar functions that can be used as selection predicates. Also allows these
 * functions to be called as scalar functions, returning Boolean values.
 * 
 */
public abstract class SelectionPredicate extends ScalarFunc {

  protected SelectionPredicate(Token origTok, AQLFunc[] args) {
    super(origTok, args);
  }

  @Override
  public Object reallyEvaluate(Tuple t, TupleList[] locatorArgs, MemoizationTable mt,
      Object[] evaluatedArgs) throws TextAnalyticsException {
    return matches(t, locatorArgs, mt, evaluatedArgs);
  }

  @Override
  public FieldType returnType() throws FunctionCallValidationException {
    return FieldType.BOOL_TYPE;
  }

  /**
   * Restricted span evaluation has the option to return a <b>superset</b> of the required results.
   * This method is used as a second filter after the low-level RSE operation has been executed.
   * Join predicates whose bindings always fetch exactly the right results can just return true
   * here.
   * 
   * @return true if the indicated join tuple matches the predicate
   * @param t a <b>join</b> tuple (e.g. an outer tuple concatenated with an inner one)
   * @param locatorArgs results referenced by any locators in the predicate
   * @param mt table for holding data that needs to be stored across calls to this method
   * @param evaluatedArgs previously-evaluated arguments to the predicate
   * @throws TextAnalyticsException
   */
  protected abstract Boolean matches(Tuple t, TupleList[] locatorArgs, MemoizationTable mt,
      Object[] evaluatedArgs) throws TextAnalyticsException;

}
