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
package com.ibm.avatar.algebra.joinpred;

import java.util.Comparator;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleComparator;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.algebra.function.scalar.GetEnd;
import com.ibm.avatar.algebra.util.tokenize.DerivedOffsetsList;
import com.ibm.avatar.algebra.util.tokenize.OffsetsList;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.logging.Log;

/**
 * A version of the FollowsTok merge join operator with the outer and inner operands reversed.
 * 
 */
public class FollowedByTokMP extends MergeJoinPred {

  private final static boolean debug = false;

  private final int mintok, maxtok;

  public FollowedByTokMP(ScalarFunc outerArg, ScalarFunc innerArg, int mintok, int maxtok) {
    super(outerArg, innerArg);
    this.mintok = mintok;
    this.maxtok = maxtok;
  }

  public int getMaxtok() {
    return maxtok;
  }

  public int getMintok() {
    return mintok;
  }

  @Override
  protected boolean afterMatchRange(MemoizationTable mt, Span outer, Span inner) {

    if (debug) {
      Log.debug("      afterMatchRange: outer = %s; inner = %s", outer, inner);
    }

    // Check whether the inner is past the end of the match range.
    int matchRangeEnd = getMatchRangeEnd(outer, mt);

    if (-1 == matchRangeEnd) {
      // SPECIAL CASE: Not enough tokens before outer
      return false;
      // END SPECIAL CASE
    }

    if (inner.getEnd() > matchRangeEnd) {
      if (debug) {
        Log.debug("      --> inner's end (%d) is " + "past end of match range (%d)", inner.getEnd(),
            matchRangeEnd);
      }
      return true;
    } else {
      if (debug) {
        Log.debug("      --> inner is before end of match range.");
      }
      return false;
    }
  }

  /**
   * Compute the relevant tokens in the vicinity of the inner, using cached tokens if possible.
   * 
   * @return list of tokens; the object returned will be REUSED on the next call to this method!
   */
  private OffsetsList computeInnerToks(Span inner, MemoizationTable mt) {
    DerivedOffsetsList tokens = mt.getTempOffsetsList();
    mt.getTokenizer().tokenize(inner.getDocTextObj(), inner.getEnd(), maxtok + 1, tokens);
    return tokens;
  }

  @Override
  protected boolean beforeMatchRange(MemoizationTable mt, Span outer, Span inner) {

    // Unlike FollowsTokMP, we don't need to keep state across calls.
    OffsetsList tokens = computeInnerToks(inner, mt);

    if (debug) {
      Log.debug("      beforeMatchRange: outer = %s; inner = %s", outer, inner);
    }

    // The number of tokens in the document after the outer could be less
    // than mintok.
    if (tokens.size() < mintok) {
      if (debug) {
        Log.debug("      --> %d toks after inner, but minimum is %d", tokens.size(), mintok);
      }
      return false;
    }

    int lastTokIx = Math.min(maxtok, tokens.size() - 1);

    if (-1 == lastTokIx) {
      // SPECIAL CASE: No tokens after the inner.
      return false;
      // END SPECIAL CASE
    }

    // The inner is before the match range if the outer starts after the
    // first qualifying token.
    if (outer.getBegin() > tokens.begin(lastTokIx)) {
      if (debug) {
        Log.debug("      --> outer's begin (%d) is " + "after last matching token begin (%d)",
            outer.getBegin(), tokens.begin(lastTokIx));
      }
      return true;
    } else {
      if (debug) {
        Log.debug("      --> starts after begin of match range.");
      }
      return false;
    }

  }

  @Override
  protected boolean matchesPred(MemoizationTable mt, Span outer, Span inner) {

    // Always tokenize, unlike FollowsTok.
    OffsetsList tokens = computeInnerToks(inner, mt);

    int outerBegin = outer.getBegin();

    if (tokens.size() <= mintok) {
      // SPECIAL CASE: Reached the end of the document before finding the
      // requisite number of tokens.
      return false;
    }

    // Return true if the outer starts anywhere in the qualifying range.
    int minBegin = tokens.begin(mintok);
    // Note the call to Math.min() for handling the case where the tokenizer
    // reached the end of the document.
    int maxBegin = tokens.begin(Math.min(maxtok, tokens.size() - 1));

    if (outerBegin >= minBegin && outerBegin <= maxBegin) {
      return true;
    } else {
      return false;
    }

  }

  /**
   * Inner spans need to be sorted by end
   * 
   * @throws ParseException
   */
  @Override
  protected Comparator<Tuple> reallyGetInnerSortComp() throws FunctionCallValidationException {
    return TupleComparator.makeComparator(new GetEnd(innerArg));
  }

  @Override
  public boolean useLinearSearch() {
    // Linear search is efficient for narrow ranges.
    if (maxtok - mintok <= 1) {
      return true;
    } else {
      return false;
    }
  }

  /** Compute the RHS of the match range in the text. */
  private int getMatchRangeEnd(Span outer, MemoizationTable mt) {
    OffsetsList tokens = getOuterTokens(outer, mt);
    int numToks = tokens.size();

    // The match range goes from the end of the first token to the beginning
    // of the last token, unless there are no tokens.
    if (numToks < 0) {
      // SPECIAL CASE: Not enough tokens before outer
      return -1;
      // END SPECIAL CASE
    } else if (numToks == 0) {
      return -1;
    } else {
      return tokens.begin(numToks - 1);
    }
  }

  /**
   * @param outer the current outer span being fed into the predicate.
   * @return information on the range of tokens up to (maxtok + 1) tokens to the left of the outer
   *         span; this object will be REUSED on the next call to this method.
   */
  private OffsetsList getOuterTokens(Span outer, MemoizationTable mt) {
    // Ensure that the text under the outer is tokenized, and charge that
    // overhead to the tokenizer.
    mt.profileEnter(tokRecord);
    mt.getTokenizer().tokenize(outer.getDocTextObj());
    mt.profileLeave(tokRecord);

    // We're looking for inner spans that come *before* the outer, so
    // tokenize backwards from the outer. We want to match inners that come
    // up to the beginning of the outer, so we start tokenizing from the
    // *end* of the outer.
    DerivedOffsetsList tokens = mt.getTempOffsetsList();
    mt.getTokenizer().tokenizeBackwards(outer.getDocTextObj(), outer.getEnd(), maxtok + 1, tokens);
    return tokens;
  }

}
