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
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.Token;

/**
 * Function that takes a span as argument and returns its TEXT attribute as a string.
 * 
 */
public class GetText extends ScalarFunc {
  public static final String[] ARG_NAMES = {"span"};
  public static final FieldType[] ARG_TYPES = {FieldType.SPANTEXT_TYPE};
  public static final String[] ARG_DESCRIPTIONS = {"span to compute the covered text of"};

  /**
   * Constructor called from
   * {@link com.ibm.avatar.algebra.function.base.ScalarFunc#buildFunc(String, ArrayList) .}
   * 
   * @throws ParseException
   */
  public GetText(Token origTok, AQLFunc[] args) throws ParseException {
    super(origTok, args);
  }

  /** Convenience constructor for creating a function call directly. */
  public GetText(ScalarFunc arg) {
    super(null, new ScalarFunc[] {arg});
  }

  @Override
  public FieldType returnType() {
    return FieldType.TEXT_TYPE;
  }

  @Override
  public Text reallyEvaluate(Tuple t, TupleList[] locatorArgs, MemoizationTable mt,
      Object[] evaluatedArgs) throws TextAnalyticsException {
    Object obj = evaluatedArgs[0];

    return Text.convert(obj);
  }

}
