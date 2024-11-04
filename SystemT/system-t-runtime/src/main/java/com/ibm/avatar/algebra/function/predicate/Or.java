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

/**
 * Takes the 'or' of its children selection predicates.
 */
public class Or extends LogicalExpression {

  public static final String FNAME = "Or";
  public static final String USAGE = "Usage: " + FNAME + "(pred_1, ..., pred_n)";

  public Or(Token origTok, AQLFunc[] args) throws ParseException {
    super(origTok, args);
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
   * Checks whether a tuple is matched using any of the child selection predicates.
   * 
   * @return true if any of the child selection predicates' match function returns true, else null
   *         if any remaining predicates' match function returns null, else false
   * @throws TextAnalyticsException
   */
  @Override
  protected Boolean matches(Tuple tuple, TupleList[] locatorArgs, MemoizationTable mt,
      Object[] evaluatedArgs) throws TextAnalyticsException {
    // this method is called only if no arguments are true, as evaluation of any argument as true
    // short-circuits
    // evaluation to TRUE in ScalarFunc.evaluate()
    // so return unknown if at least one argument is unknown, else return false (when all arguments
    // are false)
    return hasUnknownArg ? null : Boolean.FALSE;
  }

}
