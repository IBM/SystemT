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
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.Token;

/**
 * Function that takes two integers as arguments and returns the bitwise xor between them.
 * 
 * @deprecated should be a UDF
 */
@Deprecated
public class Xor extends ScalarFunc {
  public static final String[] ARG_NAMES = {"int1", "int2"};
  public static final FieldType[] ARG_TYPES = {FieldType.INT_TYPE, FieldType.INT_TYPE};
  public static final String[] ARG_DESCRIPTIONS = {"first integer", "second integer"};

  /**
   * Constructor called from {@link com.ibm.avatar.algebra.function.ScalarFunc#buildFunc(String,
   * ArrayList).}
   * 
   * @throws ParseException
   */
  public Xor(Token origTok, AQLFunc[] args) throws ParseException {
    super(origTok, args);
  }

  @Override
  public FieldType returnType() {
    return FieldType.INT_TYPE;
  }

  @Override
  public Object reallyEvaluate(Tuple t, TupleList[] locatorArgs, MemoizationTable mt,
      Object[] evaluatedArgs) throws TextAnalyticsException {
    int firstArg = (Integer) (evaluatedArgs[0]);
    int secondArg = (Integer) (evaluatedArgs[1]);
    return firstArg ^ secondArg;
  }

}
