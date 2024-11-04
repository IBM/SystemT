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
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.ScalarComparator;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.algebra.function.base.SelectionPredicate;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.Token;

/**
 * A "greater than" predicate over any type, using the comparison classes in
 * {@link ScalarComparator}. <br>
 * 
 */
public class GreaterThan extends SelectionPredicate {

  /** Object that knows how to compare our inputs. */
  private ScalarComparator comp = null;

  public static final String[] ARG_NAMES = {"arg1", "arg2"};
  public static final String[] ARG_DESCRIPTIONS = {"larger value", "smaller value"};

  public GreaterThan(Token origTok, AQLFunc[] args) throws ParseException {
    super(origTok, args);
  }

  @Override
  protected void validateArgTypes(ArrayList<FieldType> argTypes)
      throws FunctionCallValidationException {
    if (2 != argTypes.size()) {
      throw new FunctionCallValidationException(this, "Received %d arguments instead of 2",
          argTypes.size());
    }

    FieldType firstType = argTypes.get(0);
    FieldType secondType = argTypes.get(1);

    if (!firstType.comparableTo(secondType)) {
      throw new FunctionCallValidationException(this, "Can't compare types '%s' and '%s'",
          firstType, secondType);
    }

  }

  @Override
  public void bindImpl(AbstractTupleSchema ts) throws FunctionCallValidationException {

    // Now that we've bound the arguments, we can create a comparator.
    FieldType compareType = (getSFArg(0).returnType());
    if (false == compareType.getIsNullType()) {
      comp = ScalarComparator.createComparator(compareType);
    }
  }

  @Override
  protected Boolean matches(Tuple tuple, TupleList[] locatorArgs, MemoizationTable mt,
      Object[] evaluatedArgs) throws TextAnalyticsException {
    Object firstVal = evaluatedArgs[0];
    Object secondVal = evaluatedArgs[1];

    // primitive boolean is boxed into Boolean by compiler
    return (0 < comp.compare(firstVal, secondVal));
  }

}
