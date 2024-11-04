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
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.Token;

/**
 * Builds a new annotation whose span covers the span of text to the right of the indicated span in
 * the original text. Second argument is an integer that tells how many characters of context to
 * capture.
 * 
 */
public class RightContext extends ScalarFunc {
  public static final String[] ARG_NAMES = {"span", "chars"};
  public static final FieldType[] ARG_TYPES = {FieldType.SPAN_TYPE, FieldType.INT_TYPE};
  public static final String[] ARG_DESCRIPTIONS =
      {"target span", "number of characters of context to retrieve"};

  /**
   * Function that, when called on an input tuple, will return the input span.
   */
  private int chars;

  /**
   * Constructor called from {@link com.ibm.avatar.algebra.function.ScalarFunc#buildFunc(String,
   * ArrayList).}
   * 
   * @throws ParseException
   */
  public RightContext(Token origTok, AQLFunc[] args) throws ParseException {
    super(origTok, args);
  }

  @Override
  public void bindImpl(AbstractTupleSchema ts) throws FunctionCallValidationException {
    chars = (Integer) getSFArg(1).evaluateConst();
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

    // The output span will always begin at the end of the first arg's span.
    int begin = s.getEnd();

    // Don't go off the end of the document.
    int doclen = s.getDocText().length();
    int end = Math.min(doclen, begin + chars);

    return Span.makeBaseSpan(s, begin, end);
  }
}
