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
package com.ibm.avatar.algebra.function.scalar;

import java.util.ArrayList;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.Token;

/**
 * Function that implements a CASE statement.
 * 
 */
public class Case extends ScalarFunc {

  public static final String FNAME = "Case";

  public static final String USAGE = "Usage: " + FNAME
      + "(condition_1, value_1, [condition_2, value_2, ... , condition_n, value_n] [, default_value])";

  private FieldType returnType = null;

  private FieldType lastArgType = null;

  /**
   * Main constructor.
   * 
   * @throws ParseException
   */
  public Case(Token origTok, AQLFunc[] args) throws ParseException {
    super(origTok, args);
  }

  /**
   * The Case() function is polymorphic, so we need a custom implementation of validateArgTypes().
   */
  @Override
  protected void validateArgTypes(ArrayList<FieldType> argTypes)
      throws FunctionCallValidationException {
    // Arguments to Case() come in pairs, possibly followed by a default value.
    if (0 == argTypes.size()) {
      throw new FunctionCallValidationException(this,
          "Case() function must have at least one argument (found 0 args)");
    }

    // Regardless of whether there is a default value, the last argument to case should determine
    // the type returned.
    // If this type is null, we'll set it to any other type in the loop below.
    // Note that after changes to allow null return types,
    // since every odd type is checked against the last type, we know that
    // odd-numbered arguments will always return the same scalar type if not null.
    lastArgType = argTypes.get(argTypes.size() - 1);

    // Determine how many pairs of <condition, value> there are
    // Rounding here is on purpose, to exclude the default value if present.
    int numPairs = argTypes.size() / 2;

    // Make sure that the pairs are correctly typed.
    for (int pairIx = 0; pairIx < numPairs; pairIx++) {
      int firstIx = 2 * pairIx;
      int secondIx = firstIx + 1;

      // First element of the pair is the condition to check
      if (false == argTypes.get(firstIx).getIsBooleanType()) {
        throw new FunctionCallValidationException(this,
            "argument %d (first is 0) does not return a Boolean value (returns %s)", firstIx,
            argTypes.get(0));
      }

      // Second element is what to return if the condition evaluates to true, allow null for both
      // second element and last arg
      if ((false == lastArgType.getIsNullType())
          && (false == argTypes.get(secondIx).getIsNullType())
          && (false == argTypes.get(secondIx).equals(lastArgType))) {
        throw new FunctionCallValidationException(this,
            "argument %d (first is 0) returns %s, but last argument returns incompatible type %s",
            secondIx, argTypes.get(secondIx), lastArgType);
      }

      // If the last argument was set to null above and the second element of this pair is not null,
      // set the last argument to the type of the second element.
      if ((true == lastArgType.getIsNullType())
          && (false == argTypes.get(secondIx).getIsNullType()))
        lastArgType = argTypes.get(secondIx);
    }
  }

  @Override
  public void bindImpl(AbstractTupleSchema ts) throws FunctionCallValidationException {
    returnType = lastArgType;
  }

  @Override
  public FieldType returnType() {
    return returnType;
  }

  @Override
  public Object reallyEvaluate(Tuple t, TupleList[] locatorArgs, MemoizationTable mt,
      Object[] evaluatedArgs) throws TextAnalyticsException {
    // This method is only reached in the special case where all conditions evaluate to FALSE as
    // evaluation of any
    // argument as TRUE short-circuits evaluation to return the computed value in
    // ScalarFunc.evaluate()

    // If we reached this point, then no condition was true
    // Return the default value if present, or null otherwise
    if (args.length % 2 == 1)
      return evaluatedArgs[args.length - 1];

    return null;
  }

  @Override
  public boolean returnsNullOnNullInput() throws TextAnalyticsException {
    // when a null input is passed to case, we let the sub-function handle it. Case can have null
    // inputs without a null
    // output.
    return false;
  };

}
