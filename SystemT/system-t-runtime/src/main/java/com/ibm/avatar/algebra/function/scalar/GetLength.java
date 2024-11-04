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
import com.ibm.avatar.algebra.datamodel.ScalarList;
import com.ibm.avatar.algebra.datamodel.SpanText;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.algebra.function.base.ScalarReturningFunc;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.Token;

/**
 * Function that takes a span or scalar list as argument and returns the length of the span (in
 * characters), or, respectively, the number of elements in the list.
 * 
 */
public class GetLength extends ScalarFunc {
  public static final String USAGE = "Usage: GetLength(span) or GetLength(scalarList)";

  private FieldType argType;

  /**
   * Constructor called from
   * {@link com.ibm.avatar.algebra.function.base.ScalarFunc#buildFunc(String, ArrayList) .}
   * 
   * @throws ParseException
   */
  public GetLength(Token origTok, AQLFunc[] args) throws ParseException {
    super(origTok, args);
  }

  /** Convenience constructor for creating a function call directly. */
  public GetLength(ScalarFunc arg) {
    super(null, new ScalarFunc[] {arg});
  }

  /** This function is polymorphic in its input, so we need to override this method. */
  @Override
  protected void validateArgTypes(ArrayList<FieldType> argTypes)
      throws FunctionCallValidationException {
    if (1 != argTypes.size()) {
      throw new FunctionCallValidationException(this, 1, argTypes.size());
    }

    FieldType argType = argTypes.get(0);
    if (false == argType.getIsScalarListType() && false == argType.getIsSpanOrText()
        && false == argType.getIsNullType()) {
      throw new FunctionCallValidationException(this,
          "Argument is of type %s; only Span, Text, String, and List allowed", argType);
    }
  }

  @Override
  public void bindImpl(AbstractTupleSchema ts) throws FunctionCallValidationException {
    argType = getSFArg(0).returnType();
  }

  @Override
  public FieldType returnType() {
    return FieldType.INT_TYPE;
  }

  @Override
  public Object reallyEvaluate(Tuple t, TupleList[] locatorArgs, MemoizationTable mt,
      Object[] evaluatedArgs) throws TextAnalyticsException {

    if (argType.getIsScalarListType()) {
      @SuppressWarnings({"all"})
      ScalarList list = (ScalarList) evaluatedArgs[0];

      return list.size();
    }

    Object ret = evaluatedArgs[0];

    if (ret instanceof String)
      return ((String) ret).length();

    if (ret instanceof SpanText)
      return ((SpanText) ret).getLength();

    throw new RuntimeException(
        "object is not a string, text, span or scalar list: " + ret.toString());
  }

  protected ScalarReturningFunc getTarget() {
    return getSFArg(0);
  }

}
