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
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.algebra.util.tokenize.DerivedOffsetsList;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.Token;
import com.ibm.avatar.logging.Log;

/**
 * Version of {@link LeftContext} that takes distances in tokens.
 * 
 */
public class LeftContextTok extends ScalarFunc {
  public static final String[] ARG_NAMES = {"span", "toks"};
  public static final FieldType[] ARG_TYPES = {FieldType.SPAN_TYPE, FieldType.INT_TYPE};
  public static final String[] ARG_DESCRIPTIONS =
      {"target span", "number of tokens of context to retrieve"};

  /**
   * Function that, when called on an input tuple, will return the input span.
   */
  private int toks;

  /**
   * Constructor called from
   * {@link com.ibm.avatar.algebra.function.base.ScalarFunc#buildFunc(String, ArrayList) .}
   * 
   * @throws ParseException
   */
  public LeftContextTok(Token origTok, AQLFunc[] args) throws ParseException {
    super(origTok, args);
  }

  @Override
  public void bindImpl(AbstractTupleSchema ts) throws FunctionCallValidationException {
    toks = (Integer) getSFArg(1).evaluateConst();
  }

  @Override
  public FieldType returnType() {
    return FieldType.SPAN_TYPE;
  }

  @Override
  public Object reallyEvaluate(Tuple t, TupleList[] locatorArgs, MemoizationTable mt,
      Object[] evaluatedArgs) throws TextAnalyticsException {

    final boolean debug = false;

    // Get span with offsets on the base document.
    Span s = Span.convert(evaluatedArgs[0]);

    // The output span will always end at the beginning of the input.
    int end = s.getBegin();

    // Tokenize backwards until we either reach the beginning of the
    // document or the correct number of tokens.
    // Charge this overhead to the tokenizer.
    mt.profileEnter(tokRecord);
    DerivedOffsetsList offsets = mt.getTempOffsetsList();
    mt.getTokenizer().tokenizeBackwards(s.getDocTextObj(), end, toks, offsets);
    mt.profileLeave(tokRecord);

    if (debug) {
      Log.debug("LeftContextTok(%d): Tokens to " + "the left of '%s' are:\n    %s", toks,
          StringUtils.escapeForPrinting(s.getText()), offsets.toString(s.getDocText()));
    }

    // The tokenizeBackwards() method returns tokens in the *same* order
    // they would be returned by the forwards tokenizer. So we take the
    // first token offset (if any).
    int begin;

    if (offsets.size() > 0) {
      begin = offsets.begin(0);
    } else {
      begin = end;
    }

    return Span.makeBaseSpan(s, begin, end);
  }

}
