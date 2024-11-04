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
package com.ibm.systemt.regex.api;

import java.util.Arrays;

import com.ibm.avatar.algebra.util.tokenize.OffsetsList;
import com.ibm.avatar.logging.Log;
import com.ibm.systemt.regex.api.SimpleMatchBuf;

/**
 * Match buffer that extends the SimpleRegex engine's matcher with the additional machinery needed
 * to match regular expressions on token boundaries.
 * 
 */
public class TokMatchBuf extends SimpleMatchBuf {

  /**
   * Flag that is set to true if this buffer is currently being used for token-based matching.
   */
  private boolean tokenMode = false;

  /**
   * If token-based matching is being used, the full set of tokens for the current text.
   */
  private OffsetsList curToks = null;

  /**
   * If token-based matching is being used, the minimum number of tokens in a match for each of the
   * sub-expressions
   */
  protected int[] minToks = null;

  /**
   * If token-based matching is being used, the maximum number of tokens in a match for each of the
   * sub-expressions
   */
  private int[] maxToks = null;

  /**
   * The current starting token offset.
   */
  private int startTok = NOT_AN_OFFSET;

  /**
   * The index of the token containing the current end offset. The end offset may or may not be on a
   * token boundary, but it is guaranteed to be strictly increasing between calls to
   * tokenModeReset()
   */
  private int endTok = NOT_AN_OFFSET;

  /*
   * PUBLIC METHODS
   */

  /**
   * Put this buffer into a mode where it tracks all qualifying matches on token boundaries for each
   * of the sub-expressions in the master state machine. Matching in this mode will only work if you
   * also call {@link #setTokenRanges(int[], int[])}
   * 
   * @param numSubExprs number of sub-expressions to collect separate matches for
   */
  public void setTokenMode(int numSubExprs) {
    tokenMode = true;

    // Token mode uses the matchLens array to eliminate duplicates
    if (null == matchLens || matchLens.length < numSubExprs) {
      matchLens = new int[numSubExprs];
    }
  }

  /**
   * Special reset method for TOKEN_MULTI mode; updates internal token information that is used to
   * check whether a given match qualifies.
   * 
   * @param curToks tokens of the current target text
   * @param startTok token offset at the beginning of the region we're performing matching over
   */
  public void tokenModeReset(OffsetsList curToks, int startTok) {
    this.curToks = curToks;
    this.startTok = startTok;
    this.endTok = startTok;
    startChar = curToks.begin(startTok);
    // matches.reset();
    Arrays.fill(matchLens, NOT_AN_OFFSET);
    dirty = false;

    final boolean debug = false;

    if (debug) {
      Log.debug("Resetting to token %d: [%d, %d]", startTok, curToks.begin(startTok),
          curToks.end(startTok));
    }
  }

  /**
   * Set up the token ranges that this matcher will use in TOKEN_MULTI mode.
   * 
   * @param minToks for each sub-expresion, the minimum number of tokens allowed in a match
   * @param maxToks for each sub-expresion, the minimum number of tokens allowed in a match
   */
  public void setTokenRanges(int[] minToks, int[] maxToks) {
    this.minToks = minToks;
    this.maxToks = maxToks;
  }

  /*
   * OVERRIDES Add some additional error-handling to the superclass's methods.
   */

  @Override
  public int[] getMatchLens() {
    if (tokenMode) {
      return matchLens;
    } else {
      // Not doing token-based matching; defer to superclass.
      return super.getMatchLens();
    }
  }

  @Override
  public void reset() {
    if (tokenMode) {
      throw new RuntimeException("Use tokenModeReset() instead");
    } else {
      // Not doing token-based matching; defer to superclass.
      super.reset();
    }
  }

  @Override
  public void addMatch(int len, int exprIx) {

    final boolean debug = false;

    if (tokenMode) {
      dirty = true;
      if (debug) {
        Log.debug("Regex %d has potential match of length %d", exprIx, len);
      }

      if (0 == len) {
        // SPECIAL CASE: Zero-length match.
        if (0 == minToks[exprIx]) {
          addTokMatch(len, exprIx);
          // matches.addEntry(startChar, startChar, exprIx);
        }
        return;
        // END SPECIAL CASE
      }

      // Normal case: len > 0

      // Compute the current *inclusive* offset into the string.
      int curOff = startChar + len;

      if (debug) {
        Log.debug("   --> Character offset of match end is %d", curOff);
      }

      // We assume that the offsets fed to this method in between
      // subsequent calls totokenModeReset() will have monotonically
      // increasing character offsets. Take advantage of this property to
      // find the first token boundary that comes after the current
      // position.
      updateEndTok(curOff);

      // Check whether this offset is the last character of a token.
      int tokEndCharIx = curToks.end(endTok);
      if (curOff != tokEndCharIx) {
        // The current match is not at the end of a token.
        if (debug) {
          Log.debug("  --> Offset %d not end of nearest token (%d)", curOff, tokEndCharIx);
        }
        return;
      }

      // If we get here, the current match is at the end of a token. Make
      // sure that it's a token we're allowed to return a match for.
      int ntoks = endTok - startTok + 1;
      if (ntoks >= minToks[exprIx] && ntoks <= maxToks[exprIx]) {
        if (debug) {
          Log.debug("  --> Match from %d to %d", startChar, curOff);
        }
        addTokMatch(len, exprIx);
        // matches.addEntry(startChar, curOff, exprIx);
      } else {
        if (debug) {
          Log.debug("  --> No match; ntoks (%d) out or range [%d,%d]", ntoks, minToks[exprIx],
              maxToks[exprIx]);
        }
      }

    } else {
      // Defer to the superclass unless we're in token mode.
      super.addMatch(len, exprIx);
    }
  }

  /**
   * Update the current "end of match" token offset, given the character position of the match end.
   * This method assumes that character offsets fed to this class are in monotonically increasing
   * order.
   * 
   * @param curCharOff current character offset of the end of the latest regex match
   */
  private void updateEndTok(int curCharOff) {

    // The correctness of this method relies on an invariant: The offsets
    // passed to addMatch() are monotonically increasing. Actually, we're
    // happy if the offsets never go back to an earlier token; verify that
    // that simpler invariant holds.
    if (endTok > 0 && curCharOff < curToks.end(endTok - 1)) {
      throw new RuntimeException(String.format(
          "Internal error: SimpleRegex passed offsets to "
              + "TokMatchBuf.addMatch() in reverse order "
              + "(received character offset %d when processing token " + "from %d to %d)",
          curCharOff, curToks.begin(endTok), curToks.end(endTok)));
    }

    // Advance to the next token until we go past the current offset or run
    // off the end of the token list.
    while (endTok < curToks.size() && curToks.end(endTok) < curCharOff) {
      endTok++;
    }
  }

  /*
   * UTILITY METHODS
   */

  /**
   * Make an entry in our offsets list for a sub-regex match, provided that the indicated match
   * hasn't already been added to the list. Assumes that the match has passed token boundary checks
   * already.
   * 
   * @param len length of the current match
   * @param exprIx index of the sub-expression that matched
   */
  private void addTokMatch(int len, int exprIx) {

    matchLens[exprIx] = len;
  }
}
