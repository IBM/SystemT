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
package com.ibm.avatar.algebra.function.predicate;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.InternalUtils;
import com.ibm.avatar.algebra.datamodel.RSEBindings;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.algebra.util.tokenize.DerivedOffsetsList;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.Token;
import com.ibm.avatar.logging.Log;

/**
 * Selection predicate that returns true if one argument follows the other argument by a certain
 * number of tokens. In particular, the predicate takes four parameters:
 * <ul>
 * <li>Two column indexes; the indicated columns must contain Annotation objects
 * <li>A minimum and maximum number of tokens, INCLUSIVE
 * </ul>
 * The tokenization is currently done on the fly. Tokenization starts after the end of the first
 * argument, even if the first argument ends in the middle of a word, and ends right before the
 * begin of the second argument, even if the second argument begins in the middle of a word.
 * 
 */
public class FollowsTok extends RSEJoinPred {
  public static final String[] ARG_NAMES = {"span1", "span2", "mintok", "maxtok"};
  public static final FieldType[] ARG_TYPES =
      {FieldType.SPAN_TYPE, FieldType.SPAN_TYPE, FieldType.INT_TYPE, FieldType.INT_TYPE};
  public static final String[] ARG_DESCRIPTIONS =
      {"earlier span", "later span", "minimum token distance", "maximum token distance"};

  /**
   * Arguments that produce the input annotations
   */
  private ScalarFunc firstArg;

  /**
   * Debug ?
   */
  private static final boolean debug = false;

  /**
   * The minimum and maximum number of tokens (inclusive) that we allow between the first and second
   * arguments.
   */
  private int mintok, maxtok;

  public FollowsTok(Token origTok, AQLFunc[] args) throws ParseException {
    super(origTok, args);
  }

  @Override
  public void bindImpl(AbstractTupleSchema ts) {
    firstArg = getSFArg(0);
    mintok = (Integer) getSFArg(2).evaluateConst();
    maxtok = (Integer) getSFArg(3).evaluateConst();
  }

  @Override
  protected Boolean matches(Tuple tuple, TupleList[] locatorArgs, MemoizationTable mt,
      Object[] evaluatedArgs) throws TextAnalyticsException {
    // Marshal arguments.
    Span firstSpan = Span.convert(evaluatedArgs[0]);
    Span secondSpan = Span.convert(evaluatedArgs[1]);

    if (debug)
      Log.debug("FollowsTok(%d,%d); first = %s; second = %s", mintok, maxtok, firstSpan,
          secondSpan);

    if (!firstSpan.hasSameDocText(secondSpan)) {
      return false;
    }

    if (secondSpan.getBegin() < firstSpan.getEnd()) {
      // Spans are in the wrong order.
      return Boolean.FALSE;
    }

    if (secondSpan.getBegin() == firstSpan.getEnd()) {
      // Spans are within 0 characters of each other, i.e., 0 tokens
      if (0 == mintok)
        return Boolean.TRUE;
      else
        return Boolean.FALSE;
    }

    // Tokenize the string, starting just past the end of the first span,
    // and extending one token past the range of tokens that can be between
    // our two input spans
    DerivedOffsetsList tokens = mt.getTempOffsetsList();
    mt.getTokenizer().tokenize(firstSpan.getDocTextObj(), firstSpan.getEnd(), maxtok + 1, tokens);

    int minBegin, maxBegin;
    // Dummy class just to call these two methods
    InternalUtils utils = new InternalUtils();
    int firstSpanEndTokIx = utils.getEndTokIx(firstSpan, mt);
    int secondSpanBeginTokIx = utils.getBeginTokIx(secondSpan, mt);

    if (debug) {
      Log.debug("First span: %s; endTokIx = %d", firstSpan, firstSpanEndTokIx);
      Log.debug("Second span: %s; beginTokIx = %d", secondSpan, secondSpanBeginTokIx);
    }

    if (tokens.size() < mintok) {
      // SPECIAL CASE: Reached the end of the document before finding the requisite number of
      // tokens.
      return Boolean.FALSE;
    }

    // CASE 1: firstSpan ends either (1) at the end of a token, or (2) before the begin of the first
    // token
    if (Span.NOT_A_TOKEN_OFFSET != firstSpanEndTokIx && -1 != firstSpanEndTokIx) {

      if (mintok == 0)
        // Qualifying range starts at the end of first span
        minBegin = firstSpan.getEnd();
      else
        // Qualifying range starts one character after the begin of (mintok-1)th token
        minBegin = tokens.begin(mintok - 1) + 1;

      // Qualifying range ends at the begin of maxtok token, or the end of the document, if the
      // tokenizer reached the
      // end of the document.
      if (maxtok >= tokens.size())
        maxBegin = firstSpan.getDocTextObj().getLength();
      else
        maxBegin = tokens.begin(maxtok);
    }
    // CASE 2: firstSpan ends in the middle of a token
    else {

      if (tokens.size() < mintok) {
        // SPECIAL CASE: Reached the end of the document before finding the requisite number of
        // tokens.
        return Boolean.FALSE;
      }

      if (mintok == 0)
        // Qualifying range starts at the end of first span
        minBegin = firstSpan.getEnd();
      else if (mintok == 1) {
        // Qualifying range starts one character after the end of first span
        minBegin = firstSpan.getEnd() + 1;
      } else
        // Qualifying range starts one character after the begin of (mintok-1)th token
        minBegin = tokens.begin(mintok - 1) + 1;

      if (maxtok == 0) {
        // Qualifying range ends at the end of the firstSpan
        maxBegin = firstSpan.getEnd();
      } else {
        // Qualifying range ends at the begin of maxtok token; or the end of the document, if the
        // tokenizer reached
        // the end of the document
        if (maxtok >= tokens.size())
          maxBegin = firstSpan.getDocTextObj().getLength();
        else
          maxBegin = tokens.begin(maxtok);
      }
    }

    if (debug)
      Log.debug("For LHS '%s', RHS (%s) starts at %d; range is %d to %d\n", firstSpan.getText(),
          secondSpan.getText(), secondSpan.getBegin(), minBegin, maxBegin);

    if (secondSpan.getBegin() >= minBegin && secondSpan.getBegin() <= maxBegin) {
      return Boolean.TRUE;
    } else {
      return Boolean.FALSE;
    }

  }

  @Override
  public RSEBindings getBindings(Tuple outerTup, MemoizationTable mt)
      throws TextAnalyticsException {

    // The outer tuple schema is always a prefix of the join tuple schema,
    // so we can reuse the accessors for the join tuples in this context.
    Span outerSpan = (Span) firstArg.oldEvaluate(outerTup, mt);

    if (outerSpan == null)
      return null;

    // The inner span can start anywhere from minDist to maxDist
    // tokens from the outer span
    DerivedOffsetsList tokens = mt.getTempOffsetsList();
    mt.getTokenizer().tokenize(outerSpan.getDocTextObj(), outerSpan.getEnd(), maxtok + 1, tokens);

    if (tokens.size() <= mintok) {
      // SPECIAL CASE: Outer is at the end of the document.
      return null;
      // END SPECIAL CASE
    }

    // Default logic is the same as in matches() method above.
    RSEBindings ret = new RSEBindings();
    ret.type = RSEBindings.STARTS_AT;
    ret.begin = tokens.begin(mintok);
    ret.end = tokens.begin(Math.min(maxtok, tokens.size() - 1));

    return ret;
  }

}
