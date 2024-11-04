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
package com.ibm.avatar.algebra.function.agg;

import java.util.ArrayList;
import java.util.TreeSet;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.AggFunc;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.Token;

public class Sum extends AggFunc {
  public static final String[] ARG_NAMES = {"value"};
  public static final String[] ARG_DESCRIPTIONS = {"integer or float"};

  FieldType returnType;

  public Sum(Token origTok, ScalarFunc arg) throws ParseException {
    super(origTok, arg);
  }

  @Override
  protected void validateArgTypes(ArrayList<FieldType> argTypes)
      throws FunctionCallValidationException {
    if (argTypes.size() != 1) {
      throw new FunctionCallValidationException(this, "Wrong number of arguments (%d instead of 1)",
          argTypes.size());
    }

    // Argument to this call should return int or float
    FieldType argType = argTypes.get(0);
    if (argType.getIsIntegerType() || argType.getIsFloatType()) {
      return;
    } else {
      throw new FunctionCallValidationException(this,
          "Argument returns non-numeric value of type %s.", argType);
    }
  }

  @Override
  public void bindImpl(AbstractTupleSchema ts) throws FunctionCallValidationException {
    // Cache our return type; we use it during evaluate()
    returnType = arg.returnType();
  }

  @Override
  public Object evaluate(TupleList tupleList, TupleList[] locatorArgs, MemoizationTable mt)
      throws TextAnalyticsException {

    // return null if the input is empty
    if (tupleList.size() == 0)
      return null;

    Tuple t;
    float sum = 0;
    Number res;
    boolean allNulls = true;

    for (int i = 0; i < tupleList.size(); i++) {
      t = tupleList.getElemAtIndex(i);
      res = (Number) arg.evaluate(t, locatorArgs, mt);
      if (res != null) {
        allNulls = false;
        sum += res.floatValue();
      }
    }

    if (allNulls) {
      return null;
    }

    // Make sure the return type is as expected
    if (returnType.getIsIntegerType())
      return (int) sum;
    else
      return sum;
  }

  @Override
  protected void reallyGetRefRels(TreeSet<String> set) {
    set.addAll(arg.getReferencedRels());

  }

  @Override
  public FieldType returnType() throws FunctionCallValidationException {
    return returnType;
  }

}
