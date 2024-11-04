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
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.Token;

/**
 * Built-in function that trims whitespace off the beginning and end of a span. Returns a sub-span
 * of the original span.
 * 
 */
public class Chomp extends ScalarFunc {

  public static final String[] ARG_NAMES = {"span"};
  public static final FieldType[] ARG_TYPES = {FieldType.SPAN_TYPE};
  public static final String[] ARG_DESCRIPTIONS = {"span to remove white space from"};

  /**
   * Main constructor.
   * 
   * @throws ParseException
   */
  public Chomp(Token origTok, AQLFunc[] args) throws ParseException {
    super(origTok, args);
  }

  @Override
  public FieldType returnType() {
    return FieldType.SPAN_TYPE;
  }

  @Override
  public Object reallyEvaluate(Tuple t, TupleList[] locatorArgs, MemoizationTable mt,
      Object[] evaluatedArgs) throws TextAnalyticsException {

    // Get span with offsets on the base document.
    Span s = Span.convert(evaluatedArgs[0]);

    return s.chomp();

  }

}
