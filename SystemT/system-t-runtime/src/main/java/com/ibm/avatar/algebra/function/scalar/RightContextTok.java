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
import com.ibm.avatar.algebra.util.tokenize.DerivedOffsetsList;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.Token;

/**
 * Version of {@link RightContext} that takes distances in tokens instead of characters.
 * 
 */
public class RightContextTok extends ScalarFunc {
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
  public RightContextTok(Token origTok, AQLFunc[] args) throws ParseException {
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

    // Get span with offsets on the base document.
    Span s = Span.convert(evaluatedArgs[0]);

    // The output span will always begin at the end of the first arg's span.
    int begin = s.getEnd();

    // Tokenize the document, starting at the "begin" offset and stopping
    // after the specified number of tokens.
    // Charge this overhead to the tokenizer.
    mt.profileEnter(tokRecord);
    DerivedOffsetsList offsets = mt.getTempOffsetsList();
    mt.getTokenizer().tokenize(s.getDocTextObj(), begin, toks, offsets);
    mt.profileLeave(tokRecord);

    int end;
    if (offsets.size() < toks) {
      // Note that tokenizeStr() may have returned *fewer* tokens than we
      // requested; if this happens, extend our context to the end of the
      // document, even if there's trailing whitespace.
      end = s.getDocText().length();
    } else if (offsets.size() == 0 && toks == 0) {
      end = begin;
    } else {
      end = offsets.end(toks - 1);
    }

    return Span.makeBaseSpan(s, begin, end);
  }

}
