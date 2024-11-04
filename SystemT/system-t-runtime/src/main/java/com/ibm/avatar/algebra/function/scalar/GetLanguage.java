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
import com.ibm.avatar.algebra.datamodel.SpanText;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.Token;

/**
 * Function that takes a span or text object as argument and returns its language code as a string.
 */
public class GetLanguage extends ScalarFunc {
  public static final String[] ARG_NAMES = {"span"};
  public static final FieldType[] ARG_TYPES = {FieldType.SPAN_TYPE};
  public static final String[] ARG_DESCRIPTIONS =
      {"span to find the language of the underlying text of"};

  /**
   * Constructor called from
   * {@link com.ibm.avatar.algebra.function.base.ScalarFunc#buildFunc(String, ArrayList) .}
   * 
   * @throws ParseException
   */
  public GetLanguage(Token origTok, AQLFunc[] args) throws ParseException {
    super(origTok, args);
  }

  /** Convenience constructor for creating a function call directly. */
  public GetLanguage(ScalarFunc arg) {
    super(null, new ScalarFunc[] {arg});
  }

  @Override
  public FieldType returnType() {
    return FieldType.TEXT_TYPE;
  }

  @Override
  public Object reallyEvaluate(Tuple t, TupleList[] locatorArgs, MemoizationTable mt,
      Object[] evaluatedArgs) throws TextAnalyticsException {
    Object obj = evaluatedArgs[0];

    if (obj instanceof SpanText) {
      return Text.convert(((SpanText) obj).getLanguage().toString());
    }

    throw new RuntimeException("object is not a span or text: " + obj.toString());
  }

}
