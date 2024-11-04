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
package com.ibm.avatar.algebra.extract;

import java.util.ArrayList;

import com.ibm.avatar.algebra.base.ExtractionOp;
import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.algebra.datamodel.RSEBindings;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.SpanText;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.util.tokenize.OffsetsList;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.systemt.regex.api.Regex;
import com.ibm.systemt.regex.api.RegexMatcher;

/**
 * Finds regular expression matches that start and end on a token boundary. Unlike the other regular
 * expression extractors, this one returns the longest match at EVERY token starting position.
 * Borrows schema and matcher setup code from {@link RegularExpression}. Uses either SimpleRegex or
 * Java regular expression matchers.
 */
public class RegexTok extends RegularExpression {

  /** Minimum number of tokens allowed in a match. */
  private final int minTok;

  /** Maximum number of tokens allowed in a match. */
  private final int maxTok;

  /** flag to output debugging strings */
  private final boolean debug = false;

  public RegexTok(Operator child, String col, Regex regex, ArrayList<Pair<Integer, String>> groups,
      int minTok, int maxTok) throws ParseException {
    super(child, col, regex, groups);

    // if (false == (regex instanceof JavaRegex)) {
    // throw new ParseException(
    // "RegexTok can only be used with Java regexes.");
    // }

    this.minTok = minTok;
    this.maxTok = maxTok;
  }

  /**
   * Override the extract() method of the superclass to do token-boundary extraction.
   */
  @Override
  protected void extract(MemoizationTable mt, Tuple inputTup, Span inputSpan) throws Exception {

    // System.err.printf("In extract() for view '%s'\n", getViewName());

    // Tokenize the text, using cached tokens if possible.
    mt.profileEnter(tokRecord);
    OffsetsList tokens = mt.getTokenizer().tokenize(inputSpan);
    mt.profileLeave(tokRecord);

    // System.err.printf("Input span is: %s\n", inputSpan.toString());
    // System.err.printf("Cached tokens should be: %s\n", tokens
    // .toString(text));
    // System.err.printf("Cached tokens are: %s\n", inputSpan
    // .getCachedTokens().toString(text));

    // Reuse our cached regex matcher.
    RegexMatcher matcher = mt.getMatcher(regex);
    matcher.reset(inputSpan.getText());

    // Go through the tokens one by one, looking for a match that starts at
    // each.
    for (int tokix = 0; tokix < tokens.size(); tokix++) {
      extractMatchesAtTok(mt, inputTup, inputSpan, tokens, matcher, tokix);
    }
  }

  @Override
  public boolean implementsRSE() {
    return true;
  }

  /**
   * Find all the matches that satisfy a set of bindings. At some point in the future,
   * {@link #extract(MemoizationTable, Tuple, Span, TupleList)} will be rewritten as a call to this
   * method. See {@link ExtractionOp} for full API description.
   */
  @Override
  protected void extractRSE(MemoizationTable mt, Tuple inputTup, Span inputSpan, RSEBindings b)
      throws Exception {

    // Tokenize the text, using cached tokens if possible.
    mt.profileEnter(tokRecord);
    OffsetsList tokens = mt.getTokenizer().tokenize(inputSpan);
    mt.profileLeave(tokRecord);

    int[] tokRange = computeRSEMatchRange(inputSpan, b, tokens, maxTok);

    if (-1 == tokRange[0] || -1 == tokRange[1]) {
      // SPECIAL CASE: Zero tokens to extract from ==> No results
      return;
      // END SPECIAL CASE
    }

    // Create a single matcher; we'll reuse it on every token.
    RegexMatcher matcher = mt.getMatcher(regex);
    matcher.reset(inputSpan.getText());

    // Go through the tokens one by one, looking for a match that starts at
    // each.
    for (int tokix = tokRange[0]; tokix <= tokRange[1]; tokix++) {
      extractMatchesAtTok(mt, inputTup, inputSpan, tokens, matcher, tokix);
    }
  }

  /**
   * Shared code used to identify regular expression matches at a given location in the document.
   * 
   * @param mt various thread-local data structures
   * @param inputTup tuple containing the target of extraction
   * @param inputSpan span drawn from the input tuple
   * @param tokens token offsets over inputSpan
   * @param matcher regular expression matching object
   * @param tokix token index at which to find matches of the regular expression
   * @throws InterruptedException if the regular expression watchdog timer fires
   */
  private void extractMatchesAtTok(MemoizationTable mt, Tuple inputTup, Span inputSpan,
      OffsetsList tokens, RegexMatcher matcher, int tokix) throws InterruptedException {
    if (tokix >= tokens.size()) {
      throw new FatalInternalError(
          "Attempted to access token %d (0-based) of span %s during restricted "
              + "span evaluation, but the span only contains %d tokens.",
          tokix, inputSpan, tokens.size());
    }
    if (tokix < 0) {
      throw new FatalInternalError("Attempted to access token %d during restriced span evaluation",
          tokix);
    }

    // Define a match region that runs from the beginning of the current
    // token to the end of the token that is maxTok tokens further on.
    int firstTokStart = tokens.begin(tokix);

    int maxTokIx = Math.min(tokix + maxTok - 1, tokens.size() - 1);
    int lastTokEnd = tokens.end(maxTokIx);

    matcher.region(firstTokStart, lastTokEnd);

    if (matcher.lookingAt()) {
      // Try decreasing numbers of tokens until we have no match or too
      // few tokens.
      while (maxTokIx >= tokix + minTok - 1) {

        // Get match boundaries for group 0 (the entire match)
        int end = matcher.end();
        lastTokEnd = tokens.end(maxTokIx);

        if (end > lastTokEnd) {
          // The match ends after the maximum token.
          // Try for a shorter match.
          matcher.region(firstTokStart, lastTokEnd);
          if (matcher.lookingAt()) {
            // Got a shorter match; update the match region.
            end = matcher.end();
          } else {
            // No more matches.
            return;
          }
        }

        if (end == lastTokEnd) {
          // The match ends on the current maximum token.
          // Create an output tuple.
          Tuple resultTuple = extractCurMatch(matcher, inputTup, inputSpan, tokix, maxTokIx);
          addResultTup(resultTuple, mt);

          // Added by Fred on April 23, 2008: Only return the
          // *longest* match at each token position.
          return;
        }

        maxTokIx--;
      }

    }
  }

  private Tuple extractCurMatch(RegexMatcher matcher, Tuple inputTup, SpanText inputSpan,
      int beginTokIx, int endTokIx) {
    Tuple resultTuple = createOutputTup();

    copier.copyVals(inputTup, resultTuple);

    for (int i = 0; i < groups.size(); i++) {
      int grp = groups.get(i).first;
      int begin = matcher.start(grp);
      int end = matcher.end(grp);

      if (debug) {
        System.err.printf(String.format("Match from %d to %d of %d-char string '%s'\n", begin, end,
            inputSpan.getLength(), inputSpan));
      }

      Span span;
      // special case of labeled empty capturing group
      if (begin == -1 && end == -1) {
        span = null;
      }

      else {
        span = Span.makeSubSpan(inputSpan, begin, end);

        if (0 == grp && inputSpan instanceof Text) {
          // Group zero (the entire match) is guaranteed to start and end
          // on token boundaries; remember the token indices if the
          // extraction target is base text.
          span.setBeginTok(beginTokIx);
          span.setEndTok(endTokIx);
        }
      }
      groupAcc[i].setVal(resultTuple, span);
    }
    return resultTuple;
  }
}
