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
import com.ibm.avatar.algebra.datamodel.ScalarList;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.AggFunc;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.Token;

public class List extends AggFunc {

  public static final String[] ARG_NAMES = {"value"};
  public static final String[] ARG_DESCRIPTIONS = {"any scalar type"};

  private FieldType argType = null;
  private FieldType returnType = null;

  public List(Token origTok, ScalarFunc arg) {
    super(origTok, arg);
  }

  @Override
  protected void validateArgTypes(ArrayList<FieldType> argTypes)
      throws FunctionCallValidationException {
    // If we get here, make sure there is exactly one argument.
    if (argTypes.size() != 1) {
      throw new FunctionCallValidationException(this, "Wrong number of arguments (%d instead of 1)",
          argTypes.size());
    }
  }

  @Override
  public void bindImpl(AbstractTupleSchema ts) throws FunctionCallValidationException {
    // The return type is a list of scalar objects of argument's type
    argType = arg.returnType();
    returnType = FieldType.makeScalarListType(argType);
  }

  @Override
  @SuppressWarnings("all")
  public Object evaluate(TupleList tupleList, TupleList[] locatorArgs, MemoizationTable mt)
      throws TextAnalyticsException {

    Tuple t;

    ScalarList outputList = ScalarList.makeScalarListFromType(argType);
    outputList.setScalarType(argType);

    for (int i = 0; i < tupleList.size(); i++) {
      t = tupleList.getElemAtIndex(i);

      // Evaluate the argument on the tuple
      // and add the result to the output list
      Object res = arg.evaluate(t, locatorArgs, mt);

      // skip null arguments
      if (res != null) {
        outputList.add(res);
      }
    }

    return outputList;
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
