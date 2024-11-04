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
package com.ibm.avatar.algebra.util.dict;

import java.util.Arrays;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.util.string.ReusableString;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.algebra.util.tokenize.BaseOffsetsList;
import com.ibm.avatar.algebra.util.tokenize.OffsetsList;
import com.ibm.avatar.logging.Log;

/**
 * Hash-based dictionary entry with multiple tokens.
 */
public class MultiTokHashDictEntry extends HashDictEntry {

  /**
   * The components of this dictionary entry, broken down into tokens.
   */
  private CharSequence[] tokens;

  /**
   * Main constructor.
   * 
   * @param tokens the tokens that make up this entry
   * @param matchInfo information about what languages to match in a case sensitive/insensitive
   *        manner.
   */
  protected MultiTokHashDictEntry(CharSequence[] tokens, CompiledDictEntry matchInfo) {
    super(matchInfo);

    this.tokens = tokens;

    // If this entry is for only case-insensitive matching, then we
    // don't need the first token.
    if (matchInfo.getIsOnlyCaseInsensitive()) {
      tokens[0] = null;
    }
  }

  /** Internal implementation of markMatches for case-sensitive matching. */
  @Override
  protected int markMatches_case(MemoizationTable mt, String text, OffsetsList tokOffsets,
      int tokId, BaseOffsetsList output, boolean useTokenOffsets, int[] indices,
      boolean performLemmaMatch) {

    final boolean debug = false;

    // Reuse a quasi-string instead of creating strings all over the
    // place.
    ReusableString rstr = mt.getReusableStr();

    if (debug) {
      Log.debug("markMatches_case(): tokens are: %s", Arrays.toString(tokens));
    }

    // Check each token in turn.
    for (int i = 0; i < tokens.length; i++) {
      // Compute our token offset into the base string
      int curTokIx = tokId + i;

      if (curTokIx >= tokOffsets.size()) {
        // Ran off the end of the document; no match.
        return 0;
      }

      if (performLemmaMatch) {
        String lemma = tokOffsets.getLemma(curTokIx);
        rstr.setToSubstr(lemma, 0, lemma.length());
      } else
        rstr.setToSubstr(text, tokOffsets.begin(curTokIx), tokOffsets.end(curTokIx));

      if (debug) {
        Log.debug("markMatches_case(): Comparing '%s' against '%s'",
            StringUtils.escapeForPrinting(tokens[i]), StringUtils.escapeForPrinting(rstr));
      }

      if (false == rstr.contentEquals(tokens[i])) {
        return 0;
      }
    }

    // If we get here, we found a match; add all qualifying indices to the
    // output.
    int nindex = addMatchInfo(tokOffsets, tokId, output, useTokenOffsets, indices);
    return nindex;
  }

  /** Internal implementation of markMatches for case-insensitive matching. */
  @Override
  public int markMatches_nocase(MemoizationTable mt, String text, OffsetsList tokOffsets, int tokId,
      BaseOffsetsList output, boolean useTokenOffsets, int[] indices, boolean performLemmaMatch) {

    final boolean debug = false;

    // Reuse a quasi-string instead of creating strings all over the
    // place.
    ReusableString rstr = mt.getReusableStr();

    if (debug) {
      Log.debug("markMatches_nocase(): Have %d tokens: %s", tokens.length, Arrays.toString(tokens));
    }

    // We can skip the first token, since the hash lookup that got us here
    // has already checked it for us.
    for (int i = 1; i < tokens.length; i++) {
      // Compute our token offset into the base string
      int curTokIx = tokId + i;

      if (curTokIx >= tokOffsets.size()) {
        // Ran off the end of the document; no match.
        return 0;
      }

      if (performLemmaMatch) {
        String lemma = tokOffsets.getLemma(curTokIx);
        rstr.setToSubstr(lemma, 0, lemma.length());
      } else
        rstr.setToSubstr(text, tokOffsets.begin(curTokIx), tokOffsets.end(curTokIx));

      if (debug) {
        Log.debug("markMatches_nocase(): Index %d: comparing '%s' against '%s'", i,
            StringUtils.escapeForPrinting(tokens[i]), StringUtils.escapeForPrinting(rstr));
      }

      if (false == rstr.equalsIgnoreCase(tokens[i])) {
        return 0;
      }
    }

    int nindex = addMatchInfo(tokOffsets, tokId, output, useTokenOffsets, indices);
    return nindex;
  }

  @Override
  protected int getNumTokens() {
    return tokens.length;
  }

}
