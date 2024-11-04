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
package com.ibm.avatar.algebra.function.predicate;

import java.util.ArrayList;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.algebra.function.base.LogicalExpression;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.Token;

public class And extends LogicalExpression {
  public static final String USAGE = "Usage: And(pred_1, ..., pred_n)";

  public And(Token origTok, AQLFunc[] preds) throws ParseException {
    super(origTok, preds);
  }

  @Override
  protected void validateArgTypes(ArrayList<FieldType> argTypes)
      throws FunctionCallValidationException {
    if (0 == argTypes.size()) {
      throw new FunctionCallValidationException(this, "Must have at least one argument");
    }

    for (int i = 0; i < argTypes.size(); i++) {
      if (false == argTypes.get(i).getIsBooleanType()) {
        throw new FunctionCallValidationException(this, "Argument %d returns non-Boolean type %s",
            i, argTypes.get(i));
      }
    }
  }

  /**
   * Checks whether a tuple is matched using all of the child selection predicates.
   * 
   * @return false if any of the child selection predicates' match function returns false, else null
   *         if any remaining predicates' match function returns null, else true
   * @throws TextAnalyticsException
   */
  @Override
  protected Boolean matches(Tuple tuple, TupleList[] locatorArgs, MemoizationTable mt,
      Object[] evaluatedArgs) throws TextAnalyticsException {
    // this method is only called if no arguments are false, as evaluation of any argument as false
    // short-circuits
    // evaluation to FALSE in ScalarFunc.evaluate()
    // so return unknown if any argument is unknown, or true (when all arguments are true)
    return hasUnknownArg ? null : Boolean.TRUE;
  }

}
