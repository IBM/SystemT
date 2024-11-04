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

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.ScalarList;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.Token;

/**
 * Function that takes a list of scalars as argument and returns an element of the list.
 * 
 */
public class GetElementAt extends ScalarFunc {
  public static final String[] ARG_NAMES = {"scalarList", "index"};
  public static final FieldType[] ARG_TYPES = {FieldType.SCALAR_LIST_TYPE, FieldType.INT_TYPE};
  public static final String[] ARG_DESCRIPTIONS =
      {"target list of scalars", "index of element to retrieve"};

  private FieldType returnType;

  /**
   * Constructor called from {@link com.ibm.avatar.algebra.function.ScalarFunc#buildFunc(String,
   * ArrayList).}
   * 
   * @throws ParseException
   */
  public GetElementAt(Token origTok, AQLFunc[] args) throws ParseException {
    super(origTok, args);
  }

  /** Convenience constructor for creating a function call directly. */
  public GetElementAt(ScalarFunc arg) {
    super(null, new ScalarFunc[] {arg});
  }

  @Override
  public void bindImpl(AbstractTupleSchema ts) throws FunctionCallValidationException {
    returnType = getSFArg(0).returnType().getScalarListType();
  }

  @Override
  public FieldType returnType() {
    return returnType;
  }

  @Override
  public Object reallyEvaluate(Tuple t, TupleList[] locatorArgs, MemoizationTable mt,
      Object[] evaluatedArgs) throws TextAnalyticsException {

    @SuppressWarnings({"all"})
    ScalarList list = (ScalarList) evaluatedArgs[0];
    int idx = (Integer) evaluatedArgs[1];

    if (idx < 0 || idx >= list.size())
      return null;

    return list.get(idx);
  }

}
