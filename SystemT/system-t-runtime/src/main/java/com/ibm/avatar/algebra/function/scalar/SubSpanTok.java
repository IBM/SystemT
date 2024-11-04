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
import com.ibm.avatar.algebra.util.tokenize.OffsetsList;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.Token;

/**
 * Builds a new annotation whose span covers a given range of tokens in the input span. Arguments
 * are:
 * <ul>
 * <li>Function call that returns input span
 * <li>Integer specifying the first token in the range
 * <li>Integer specifying the last token in the range
 * </ul>
 * 
 */
public class SubSpanTok extends ScalarFunc {

  public static final String[] ARG_NAMES = {"span", "first_tok", "last_tok"};
  public static final FieldType[] ARG_TYPES =
      {FieldType.SPAN_TYPE, FieldType.INT_TYPE, FieldType.INT_TYPE};
  public static final String[] ARG_DESCRIPTIONS =
      {"target span", "first token in range", "last token in range"};

  private int firstTok, lastTok;

  /**
   * Main constructor.
   */
  public SubSpanTok(Token origTok, AQLFunc[] args) throws ParseException {
    super(origTok, args);
  }

  @Override
  public void bindImpl(AbstractTupleSchema ts) throws FunctionCallValidationException {

    firstTok = (Integer) getSFArg(1).evaluateConst();
    lastTok = (Integer) getSFArg(2).evaluateConst();

  }

  @Override
  public FieldType returnType() {
    return FieldType.SPAN_TYPE;
  }

  @Override
  public Object reallyEvaluate(Tuple t, TupleList[] locatorArgs, MemoizationTable mt,
      Object[] evaluatedArgs) throws TextAnalyticsException {

    Span span = Span.convert(evaluatedArgs[0]);

    // Tokenize the document, starting at the "begin" offset and stopping
    // after the specified number of tokens.
    // DerivedOffsetsList offsets = mt.getTempOffsetsList();
    mt.profileEnter(tokRecord);
    OffsetsList offsets = mt.getTokenizer().tokenize(span);
    mt.profileLeave(tokRecord);

    int begin, end;

    if (offsets.size() == 0) {
      // if the input span is empty,
      // return the empty span the left of the input span
      begin = 0;
      end = 0;
    } else if (offsets.size() < firstTok) {
      // the first requested token is outside the input span
      // return a span of length 0 to the right of the input span
      begin = offsets.end(offsets.size() - 1);
      end = begin;
    } else {

      if (firstTok <= 0) {
        // nonsense input
        // output span starts at the beginning of first token in the input span
        begin = offsets.begin(0);
        firstTok = 1;
      } else {
        // the first requested token is inside the input span
        // output span starts at the beginning of the first requested token
        begin = offsets.begin(firstTok - 1);
      }

      if (lastTok < firstTok) {
        // nonsense input
        // return a span of length 0 to the left of the first token
        end = begin;
      } else if (offsets.size() < lastTok) {
        // the last requested token is outside the input span
        // output span ends at the end of last token in the input span
        end = offsets.end(offsets.size() - 1);
      } else {
        // the last requested token is inside the input span
        // output span ends at the end of the last requested token
        end = offsets.end(lastTok - 1);
      }
    }

    return Span.makeSubSpan(span, begin, end);
  }

}
