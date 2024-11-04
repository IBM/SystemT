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
 * Hash-based dictionary entry with exactly one token.
 */
public class SingleTokHashDictEntry extends HashDictEntry {

  /**
   * Token associated with this dictionary entry, lowercase if the entry is case-insensitive
   */
  private CharSequence token;

  /**
   * Factory method for creating instances of this class.
   * 
   * @param token the token that makes up this entry
   * @param matchInfo information about what languages to match in a case sensitive/insensitive
   *        manner.
   */
  protected static final SingleTokHashDictEntry makeEntry(CharSequence token,
      CompiledDictEntry matchInfo, DictMemoization dm) {

    // If this entry is for only case-insensitive matching, then we
    // don't need the first token and can memoize the dict entry object.
    if (false == matchInfo.getIsOnlyCaseInsensitive()) {
      // Case-sensitive matching means we need to parametrize the entry by
      // the full token in its original case. So it's unlikely that
      // memoization is going to work for us. Just create a new entry from
      // scratch.
      return new SingleTokHashDictEntry(token, matchInfo);
    }

    // Check whether we already have a cached entry with the appropriate
    // characteristics.
    SingleTokHashDictEntry ret = dm.getSingleTokSingletons().get(matchInfo);

    if (null == ret) {
      // Don't have a singleton yet.
      ret = new SingleTokHashDictEntry(null, matchInfo);
      dm.getSingleTokSingletons().put(matchInfo, ret);
    }

    return ret;

  }

  /**
   * Main constructor, for use by the factory method ONLY.
   * 
   * @param token the token that makes up this entry
   * @param matchInfo information about what languages to match in a case sensitive/insensitive
   *        manner.
   */
  private SingleTokHashDictEntry(CharSequence token, CompiledDictEntry matchInfo) {
    super(matchInfo);

    this.token = token;

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
      Log.debug("markMatches_case(): token is: %s", token);
    }

    if (performLemmaMatch) {
      String lemma = tokOffsets.getLemma(tokId);
      rstr.setToSubstr(lemma, 0, lemma.length());
    } else
      rstr.setToSubstr(text, tokOffsets.begin(tokId), tokOffsets.end(tokId));

    if (debug) {
      Log.debug("markMatches_case(): Comparing '%s' against '%s'",
          StringUtils.escapeForPrinting(token), StringUtils.escapeForPrinting(rstr));
    }

    if (false == rstr.contentEquals(token)) {
      return 0;
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

    // We can skip the first token, since the hash lookup that got us here
    // has already checked it for us.
    // Since this is a single-token dictionary entry, we don't need to look
    // any further.
    int nindex = addMatchInfo(tokOffsets, tokId, output, useTokenOffsets, indices);
    return nindex;
  }

  @Override
  protected int getNumTokens() {
    return 1;
  }

}
