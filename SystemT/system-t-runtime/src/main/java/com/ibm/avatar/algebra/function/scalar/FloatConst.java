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
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.Token;

/** Return a float constant. */
public class FloatConst extends ScalarFunc {

  public static final String[] ARG_NAMES = {"value"};
  public static final FieldType[] ARG_TYPES = {FieldType.FLOAT_TYPE};
  public static final String[] ARG_DESCRIPTIONS = {"floating point value"};

  public static final boolean ISCONST = true;

  private Float ourConst = null;

  /** Convenience constructor for creating dummy function calls. */
  public FloatConst(Token origTok, Object theConst) {
    super(origTok, null);
    ourConst = (Float) theConst;
  }

  @Override
  public boolean isConst() {
    return true;
  }

  @Override
  public Object reallyEvaluate(Tuple t, TupleList[] locatorArgs, MemoizationTable mt,
      Object[] evaluatedArgs) throws TextAnalyticsException {
    return ourConst;
  }

  @Override
  public FieldType returnType() {
    return FieldType.FLOAT_TYPE;
  }

  /**
   * Convenience method for memoizing an IntConst() call.
   * 
   * @param intConstAsObj
   * @return
   */
  public static float eval(FloatConst func) {
    return (Float) func.evaluateConst();
  }

  public float getFloat() {
    return ourConst;
  }

  @Override
  protected void validateArgTypes(ArrayList<FieldType> argTypes)
      throws FunctionCallValidationException {
    // Disable checks from the superclass.
  }
}