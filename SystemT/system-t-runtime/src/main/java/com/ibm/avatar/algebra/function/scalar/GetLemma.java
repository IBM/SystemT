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
 * Function that takes a span as argument and returns its lemmas as a string.
 * 
 */
public class GetLemma extends ScalarFunc {
  public static final String[] ARG_NAMES = {"span"};
  public static final FieldType[] ARG_TYPES = {FieldType.SPANTEXT_TYPE};
  public static final String[] ARG_DESCRIPTIONS = {"span to get the lemma of"};

  /**
   * Constructor called from
   * {@link com.ibm.avatar.algebra.function.base.ScalarFunc#buildFunc(String, ArrayList) .}
   * 
   * @throws ParseException
   */
  public GetLemma(Token origTok, AQLFunc[] args) throws ParseException {
    super(origTok, args);
  }

  /** Convenience constructor for creating a function call directly. */
  public GetLemma(ScalarFunc arg) {
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

    if (false == (obj instanceof SpanText)) {
      throw new RuntimeException("object is not a span: " + obj.toString());
    }

    SpanText span = (SpanText) obj;

    // Lemma is computed during part of speech, we don't have a way to split the overhead between
    // tokenization and part
    // of speech, and we don't have a profiler record for the PartOfSpeech operator, so change on
    // tokenization here
    // The AQL Profiler output will show a single entry combining Tokenization and POS overhead when
    // the Multilingual
    // tokenizer is used
    mt.profileEnter(tokRecord);
    String lemma = span.getLemma(mt);
    mt.profileLeave(tokRecord);

    return Text.fromString(lemma);
  }

  @Override
  protected boolean requiresLemmaInternal() {
    return true;
  }

}
