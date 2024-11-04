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
import com.ibm.avatar.algebra.function.base.SelectionPredicate;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.Token;

/**
 * Selection predicate that returns true if its (single) input is null.
 * 
 */
public class IsNull extends SelectionPredicate {
  public static final String[] ARG_NAMES = {"scalar value"};

  public IsNull(Token origTok, AQLFunc[] args) throws ParseException {
    super(origTok, args);
  }

  @Override
  protected void validateArgTypes(ArrayList<FieldType> argTypes)
      throws FunctionCallValidationException {
    if (1 != argTypes.size()) {
      throw new FunctionCallValidationException(this, "Number of arguments is %d instead of 1",
          argTypes.size());
    }

    // No more checks; we allow any type here
  }

  @Override
  protected Boolean matches(Tuple tuple, TupleList[] locatorArgs, MemoizationTable mt,
      Object[] evaluatedArgs) throws TextAnalyticsException {
    Object o = evaluatedArgs[0];

    if (o == null)
      return Boolean.TRUE;
    else
      return Boolean.FALSE;
  }

  @Override
  public boolean returnsNullOnNullInput() throws TextAnalyticsException {
    // This function never returns null, let alone null on a null input
    return false;
  }
}
