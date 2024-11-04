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
import com.ibm.avatar.algebra.datamodel.ScalarList;
import com.ibm.avatar.algebra.datamodel.SpanText;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.Token;

/**
 * Function that takes any type as argument and returns the attribute as a String.
 * 
 */
public class GetString extends ScalarFunc {
  public static final String USAGE = "Usage: GetString(scalar)";

  /**
   * Constructor called from
   * {@link com.ibm.avatar.algebra.function.base.ScalarFunc#buildFunc(String, ArrayList) .}
   * 
   * @throws ParseException
   */
  public GetString(Token origTok, AQLFunc[] args) throws ParseException {
    super(origTok, args);
  }

  /** Convenience constructor for creating a function call directly. */
  public GetString(ScalarFunc arg) {
    super(null, new ScalarFunc[] {arg});
  }

  @Override
  protected void validateArgTypes(ArrayList<FieldType> argTypes)
      throws FunctionCallValidationException {
    if (1 != argTypes.size()) {
      throw new FunctionCallValidationException(this, 1, argTypes.size());
    }

    // We allow anything as an argument to this function.
  }

  @Override
  public FieldType returnType() {
    return FieldType.TEXT_TYPE;
  }

  @Override
  public Object reallyEvaluate(Tuple t, TupleList[] locatorArgs, MemoizationTable mt,
      Object[] evaluatedArgs) throws TextAnalyticsException {
    Object obj = evaluatedArgs[0];

    if (obj instanceof SpanText || obj instanceof String) {
      return Text.convert(obj);
    }
    return Text.convert(objToStr(obj));
  }

  /**
   * Static method for sharing with other classes.
   * 
   * @param obj an object from one of SystemT's built-in data types
   * @return the long string representation of the object.
   */
  @SuppressWarnings({"all"})
  public static final String objToStr(Object obj) {
    if (obj == null) {
      return null;
    }

    if (obj instanceof ScalarList) {
      return ((ScalarList) obj).toLongString();
    } else if (true == (obj instanceof SpanText)) {
      return ((SpanText) obj).getText();
    } else {
      return obj.toString();
    }
  }

}
