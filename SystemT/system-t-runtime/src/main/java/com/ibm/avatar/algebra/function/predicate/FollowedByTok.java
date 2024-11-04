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
 * Version of the FollowsTok predicate with arguments reversed. We implement the second version
 * because RSE join predicates are asymmetric.
 * 
 */
public class FollowedByTok extends RSEJoinPred {
  public static final String[] ARG_NAMES = {"span1", "span2", "mintok", "maxtok"};
  public static final FieldType[] ARG_TYPES =
      {FieldType.SPAN_TYPE, FieldType.SPAN_TYPE, FieldType.INT_TYPE, FieldType.INT_TYPE};
  public static final String[] ARG_DESCRIPTIONS =
      {"later span", "earlier span", "minimum token distance", "maximum token distance"};

  /**
   * Debug ?
   */
  private static final boolean debug = false;

  /**
   * Arguments that produce the input annotations
   */
  private ScalarFunc firstArg;

  /**
   * The minimum and maximum number of tokens (inclusive) that we allow between the first and second
   * arguments.
   */
  private int mintok, maxtok;

  public FollowedByTok(Token origTok, AQLFunc[] args) throws ParseException {
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

    if (debug) {
      System.err.printf("FollowedByTok: Checking tuple %s\n", tuple);
    }

    // Marshal arguments.
    // NOTE: Order is reversed!!!
    Span firstSpan = Span.convert(evaluatedArgs[1]);
    Span secondSpan = Span.convert(evaluatedArgs[0]);

    if (debug)
      Log.debug("FollowsByTok(%d,%d); first = %s; second = %s", mintok, maxtok, firstSpan,
          secondSpan);

    // The remainder of this method is the same as FollowsTok.matches().
    // NOTE: Keep the two methods in sync!

    if (!firstSpan.hasSameDocText(secondSpan)) {
      return false;
    }

    if (debug)
      Log.debug("Getting text of %s\n", firstSpan);

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
        return false;
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

    boolean debug = false;

    // The outer tuple schema is always a prefix of the join tuple schema,
    // so we can reuse the accessors for the join tuples in this context.
    Span outerSpan = (Span) firstArg.oldEvaluate(outerTup, mt);

    if (debug) {
      Log.debug("Outer span: %s", outerSpan);
    }

    if (outerSpan == null)
      return null;

    // The inner span can *end* anywhere from mintok to maxtok tokens
    // *before* the outer span.
    // Tokenize *backwards* from the beginning of the outer span.
    DerivedOffsetsList tokens = mt.getTempOffsetsList();
    mt.getTokenizer().tokenizeBackwards(outerSpan.getDocTextObj(), outerSpan.getBegin(), maxtok + 1,
        tokens);

    if (tokens.size() <= mintok || 0 == tokens.size()) {
      // SPECIAL CASE: Not enough tokens before the outer.
      return null;
      // END SPECIAL CASE
    }

    // tokenizeBackwards() returns tokens in *forwards* order, so the
    // acceptable end range goes from the begin of token 0 to the end of
    // token (maxtok - mintok)
    if (debug) {
      Log.debug("   --> Asked for %d tokens and got %d", maxtok + 1, tokens.size());
    }
    RSEBindings ret = new RSEBindings();
    ret.type = RSEBindings.ENDS_AT;
    ret.begin = tokens.begin(0);
    ret.end = tokens.end(Math.min(maxtok - mintok, tokens.size() - 1));

    if (debug) {
      Log.debug("   --> Token 0 from %d to %d", tokens.begin(0), tokens.end(0));
      Log.debug("   --> Inner range %d to %d ('%s')", ret.begin, ret.end,
          outerSpan.getDocText().subSequence(ret.begin, ret.end));
    }

    return ret;
  }

}
