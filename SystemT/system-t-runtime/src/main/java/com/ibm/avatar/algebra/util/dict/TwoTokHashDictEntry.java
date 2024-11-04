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

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.util.string.ReusableString;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.algebra.util.tokenize.BaseOffsetsList;
import com.ibm.avatar.algebra.util.tokenize.OffsetsList;
import com.ibm.avatar.logging.Log;

/**
 * Special-case version of MultiTokHashDictEntry for the case when there are exactly two tokens.
 * Cuts down on memory usage by not keeping an array of size two. Create instances of this class
 * through the factory method {@link #makeTwoTokEntry(CharSequence[], CompiledDictEntry)}.
 */
public abstract class TwoTokHashDictEntry extends HashDictEntry {

  /**
   * We don't want to keep a pointer to the first token unless we need to because this entry is
   * doing case-sensitive matching, so create subclasses for case sensitive and insensitive
   * matching.
   */
  private static class CaseSensitive extends TwoTokHashDictEntry {

    private CharSequence firstToken;

    protected CaseSensitive(CharSequence firstToken, CharSequence secondToken,
        CompiledDictEntry matchInfo) {
      super(secondToken, matchInfo);
      this.firstToken = firstToken;
    }

    /**
     * Internal implementation of markMatches for case-sensitive matching. Note that we retain the
     * case-insensitive code from the superclass, since an entry may do both case-insensitive and
     * case-sensitive matching.
     */
    @Override
    protected int markMatches_case(MemoizationTable mt, String text, OffsetsList tokOffsets,
        int tokId, BaseOffsetsList output, boolean useTokenOffsets, int[] indices,
        boolean performLemmaMatch) {

      final boolean debug = false;

      // Reuse a quasi-string instead of creating strings all over the
      // place.
      ReusableString rstr = mt.getReusableStr();

      if (debug) {
        Log.debug("markMatches_case(): tokens are %s and %s", firstToken, secondToken);
      }

      // Check first token.
      {
        int curTokIx = tokId;
        CharSequence expectedTok = firstToken;

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
          Log.debug("markMatches_case(): Comparing " + "token 1 '%s' against '%s'",
              StringUtils.escapeForPrinting(firstToken), StringUtils.escapeForPrinting(rstr));
        }

        if (false == rstr.contentEquals(expectedTok)) {
          return 0;
        }
      }

      // Check second token.
      {
        int curTokIx = tokId + 1;
        CharSequence expectedTok = secondToken;

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
          Log.debug("markMatches_case(): Comparing " + "token 1 '%s' against '%s'",
              StringUtils.escapeForPrinting(firstToken), StringUtils.escapeForPrinting(rstr));
        }

        if (false == rstr.contentEquals(expectedTok)) {
          return 0;
        }
      }

      // If we get here, we found a match; add all qualifying indices to
      // the output.
      int nindex = addMatchInfo(tokOffsets, tokId, output, useTokenOffsets, indices);
      return nindex;
    }

  }

  private static class CaseInsensitive extends TwoTokHashDictEntry {
    protected CaseInsensitive(CharSequence secondToken, CompiledDictEntry matchInfo) {
      super(secondToken, matchInfo);
    }

    @Override
    protected int markMatches_case(MemoizationTable mt, String text, OffsetsList tokOffsets,
        int tokId, BaseOffsetsList output, boolean useTokenOffsets, int[] indices,
        boolean performLemmaMatch) {

      if (getMatchInfo().getIsOnlyCaseInsensitive()) {
        Log.debug("matchInfo thinks we only do" + " case-insensitive matching");
      } else {
        Log.debug("matchInfo does NOT think we only do" + " case-insensitive matching");
      }

      throw new RuntimeException(
          "This method should never be called, " + "because this entry is case-insensitive");
    }

  }

  /**
   * The second token in the entry (the first is only needed when doing case-sensitive matching)
   */
  protected CharSequence secondToken;

  /**
   * Factory method for creating instances of this class. Chooses the most compact internal
   * representation possible.
   * 
   * @param firstToken the first token in this dictionary entry
   * @param secondToken second token in the dictionary entry
   * @param matchInfo information about what languages to match in a case sensitive/insensitive
   *        manner.
   * @return instance of this class, with the appropriate internal implementation
   */
  protected static final TwoTokHashDictEntry makeEntry(CharSequence firstToken,
      CharSequence secondToken, CompiledDictEntry matchInfo) {

    // If this entry is for only case-insensitive matching, then we
    // don't need to keep the first token around.
    if (matchInfo.getIsOnlyCaseInsensitive()) {
      return new CaseInsensitive(secondToken, matchInfo);
    } else {
      return new CaseSensitive(firstToken, secondToken, matchInfo);
    }
  }

  /**
   * Main constructor, for use only by subclasses.
   * 
   * @param secondToken the second token in the entry
   * @param matchInfo information about what languages to match in a case sensitive/insensitive
   *        manner.
   */
  protected TwoTokHashDictEntry(CharSequence secondToken, CompiledDictEntry matchInfo) {
    super(matchInfo);

    this.secondToken = secondToken;
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
      Log.debug("markMatches_nocase(): Second token is %s", secondToken);
    }

    // We can skip the first token, since the hash lookup that got us here
    // has already checked it for us.
    // Compute our token offset into the base string
    int curTokIx = tokId + 1;

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
      Log.debug("markMatches_nocase(): Comparing '%s' against '%s'",
          StringUtils.escapeForPrinting(secondToken), StringUtils.escapeForPrinting(rstr));
    }

    if (false == rstr.equalsIgnoreCase(secondToken)) {
      return 0;
    }

    // If we get here, we found a match.
    int nindex = addMatchInfo(tokOffsets, tokId, output, useTokenOffsets, indices);
    return nindex;
  }

  @Override
  protected int getNumTokens() {
    return 2;
  }

}
