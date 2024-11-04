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
 * Merge join predicate that returns true if the inner follows the outer by a certain range of
 * numbers of tokens. In particular, the predicate takes four parameters:
 * <ul>
 * <li>Two column indexes; the indicated columns must contain Annotation objects
 * <li>A minimum and maximum number of tokens, INCLUSIVE, that can be between the outer and inner
 * annotations in order for the predicate to return true.
 * </ul>
 * 
 */
public class FollowsTokMP extends MergeJoinPred {

  final boolean debug = false;
  private int mintok, maxtok;

  public FollowsTokMP(ScalarFunc outerArg, ScalarFunc innerArg, int mintok, int maxtok) {
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

    // Compute tokens to the right of the outer span.
    OffsetsList outerToks = getOuterTokens(outer, mt);

    if (debug) {
      // Log.debug("Document tokens are: %s", Tokenizer.impl.tokenize(
      // outer.getDocTextObj()).toString(outer.getDocText()));
      // Log.debug(" afterMatchRange: outerToks is %s", outerToks
      // .toString(outer.getDocText()));
    }

    // The number of tokens in the document after the outer could be less
    // than mintok.
    int lastTokOff = outerToks.size() - 1;

    if (lastTokOff < mintok) {
      if (debug) {
        Log.debug("      --> document ends " + "before qualifying range begins");
      }
      // SPECIAL CASE: Document ends before qualifying range begins.
      // Return false because no inner tuple can be after the qualifying
      // tuple range -- the range is past the end of the document.
      return false;
      // END SPECIAL CASE
    }

    // The inner is past the match range if it starts after the last
    // qualifying token.
    int maxTokIx = Math.min(maxtok, outerToks.size() - 1);
    if (inner.getBegin() > outerToks.begin(maxTokIx)) {
      if (debug) {
        Log.debug("      --> inner's begin (%d) is " + "after last matching token begin (%d)",
            inner.getBegin(), outerToks.begin(maxTokIx));
      }
      return true;
    } else {
      if (debug) {
        Log.debug("      --> inner is before end" + " of match range.");
      }
      return false;
    }
  }

  @Override
  protected boolean beforeMatchRange(MemoizationTable mt, Span outer, Span inner) {

    if (debug) {
      Log.debug("      beforeMatchRange: outer = %s; inner = %s", outer, inner);
    }

    // Compute tokens to the right of the outer span.
    OffsetsList outerToks = getOuterTokens(outer, mt);

    // The number of tokens in the document after the outer could be less
    // than mintok.
    int lastTokOff = outerToks.size() - 1;

    if (lastTokOff < mintok) {
      if (debug) {
        Log.debug("      --> %d toks after outer, " + "but minimum is %d", lastTokOff, mintok);
      }
      return true;
    }

    // The inner is before the match range if it starts before the first
    // qualifying token.
    if (inner.getBegin() < outerToks.begin(mintok)) {
      if (debug) {
        Log.debug("      --> inner's begin (%d) is " + "before first matching token begin (%d)",
            inner.getBegin(), outerToks.begin(mintok));
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

    // Compute tokens to the right of the outer span.
    OffsetsList outerToks = getOuterTokens(outer, mt);

    int innerBegin = inner.getBegin();

    int lastTokOff = outerToks.size() - 1;

    if (lastTokOff < mintok) {
      // SPECIAL CASE: Reached the end of the document before finding the
      // requisite number of tokens.
      return false;
    }

    // Return true if the inner starts anywhere in the qualifying range.
    int minBegin = outerToks.begin(mintok);
    // Note the call to Math.min() for handling the case where the tokenizer
    // reached the end of the document.
    int maxTokIx = Math.min(maxtok, outerToks.size() - 1);
    int maxBegin = outerToks.begin(maxTokIx);

    if (debug) {
      Log.debug("For LHS '%s', RHS (%s) starts at %d; range is %d to %d", outer.getText(),
          inner.getText(), innerBegin, minBegin, maxBegin);
    }

    if (innerBegin >= minBegin && innerBegin <= maxBegin) {
      return true;
    } else {
      return false;
    }

  }

  /**
   * @param outer the current outer span being fed into the predicate.
   * @return information on the range of tokens up to (maxtok + 1) tokens to the right of the outer
   *         span; this object will be RECYCLED on the next call to this method!
   */
  private OffsetsList getOuterTokens(Span outer, MemoizationTable mt) {
    // Ensure that the text under the outer is tokenized, and charge that
    // overhead to the tokenizer.
    mt.profileEnter(tokRecord);
    mt.getTokenizer().tokenize(outer.getDocTextObj());
    mt.profileLeave(tokRecord);

    DerivedOffsetsList tokens = mt.getTempOffsetsList();
    mt.getTokenizer().tokenize(outer.getDocTextObj(), outer.getEnd(), maxtok + 1, tokens);
    return tokens;
  }

  /***************************************************************************
   * Outer spans need to be sorted by END
   * 
   * @throws ParseException
   */
  @Override
  protected Comparator<Tuple> reallyGetOuterSortComp() throws FunctionCallValidationException {
    return TupleComparator.makeComparator(new GetEnd(outerArg));
  }

  @Override
  public boolean useLinearSearch() {
    // Linear search is efficient for narrow ranges.
    if (maxtok - mintok <= 2) {
      return true;
    } else {
      return false;
    }
  }
}
