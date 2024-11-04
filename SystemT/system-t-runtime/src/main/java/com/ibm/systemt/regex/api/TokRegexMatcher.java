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

import com.ibm.avatar.algebra.util.tokenize.OffsetsList;
import com.ibm.systemt.regex.api.SimpleRegex;

/**
 * Special regular expression matcher that performs matching of multiple regular expressions on
 * token boundaries, using the SimpleRegex engine internally.
 */
public class TokRegexMatcher {

  private TokMatchBuf matchBuf = new TokMatchBuf();

  /**
   * Maximum number of tokens that need to be examined for matches.
   */
  private int globalMaxTok = -1;

  // Reusable buffers for runFSM; hold the states the FSM is currently in.
  // private int[] curStates = new int[256];
  // private int[] nextStates = new int[256];

  /*
   * PUBLIC METHODS
   */

  /**
   * SimpleRegex-specific; not part of the {@link RegexMatcher} API. Set the ranges of match
   * lengths, in tokens, that this matcher will find when running in "token mode".
   * 
   * @param minToks for each sub-expresion, the minimum number of tokens allowed in a match
   * @param maxToks for each sub-expresion, the minimum number of tokens allowed in a match
   */
  public void setTokenRanges(int[] minToks, int[] maxToks) {
    matchBuf.setTokenRanges(minToks, maxToks);

    // Find the global maximum number of tokens, so that we can know how
    // long to run the state machine for.
    globalMaxTok = 0;
    for (int ntok : maxToks) {
      globalMaxTok = Math.max(globalMaxTok, ntok);
    }
  }

  /**
   * Match one or more regular expressions in "token mode", finding all qualifying matches on token
   * boundaries, according to the offsets previously passed to {@link #setTokenRanges(int[], int[])}
   * 
   * @param regex the regular expression; may be shared across threads
   * @param text the target text, as a Java string
   * @param curToks tokens of the current target text
   * @param startTok token offset at the beginning of the region we're performing matching over
   * @return lengths of all matches (for multiple input regexes) that start at the beginning of the
   *         current token. Note that this array will be overwritten on the next call to this
   *         method!
   */
  public int[] findTokMatches(SimpleRegex regex, String text, OffsetsList curToks, int startTok) {

    // Put the match recording machinery into "token mode"
    matchBuf.setTokenMode(regex.getNumPatterns());
    matchBuf.tokenModeReset(curToks, startTok);

    // Determine the character range we'll be examining.
    int maxTokIx = Math.min(curToks.size() - 1, startTok + globalMaxTok);

    int startChar = curToks.begin(startTok);
    int endChar = curToks.end(maxTokIx);

    // Log.debug("Examining characters %d to %d of %d-length string",
    // startChar, endChar, text.length());

    // Run the state machine.
    regex.runFSM(text, startChar, endChar, matchBuf);

    // Get results out of the result recorder.
    // return matchBuf.getMatches();
    return matchBuf.getMatchLens();
  }

}
