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
import com.ibm.avatar.algebra.datamodel.SpanText;
import com.ibm.avatar.algebra.util.tokenize.BaseOffsetsList;
import com.ibm.avatar.algebra.util.tokenize.OffsetsList;
import com.ibm.avatar.logging.Log;

/**
 * Superclass/factory for dictionary entries in a hash dictionary.
 */
public abstract class HashDictEntry {

  /**
   * Object (may be shared with many other HashDictEntries) that tells what kind of matches to
   * generate -- case sensitivity and which dictionary indices -- for our sequence of tokens.
   */
  private CompiledDictEntry matchInfo;

  protected CompiledDictEntry getMatchInfo() {
    return matchInfo;
  }

  /**
   * Main factory method. Creates the appropriate type of low-level implementation.
   */
  public static final HashDictEntry makeEntry(CharSequence[] tokens, CompiledDictEntry matchInfo,
      DictMemoization dm) {

    if (1 == tokens.length) {
      return SingleTokHashDictEntry.makeEntry(tokens[0], matchInfo, dm);
    } else if (2 == tokens.length) {
      return TwoTokHashDictEntry.makeEntry(tokens[0], tokens[1], matchInfo);
    } else {
      return new MultiTokHashDictEntry(tokens, matchInfo);
    }
  }

  /**
   * Dummy constructor for the use of subclass MultiEntry
   */
  protected HashDictEntry() {}

  /**
   * Constructor for use by subclasses other than MultiEntry.
   */
  protected HashDictEntry(CompiledDictEntry matchInfo) {
    this.matchInfo = matchInfo;
  }

  /**
   * Find all the matches of this entry, starting at the indicated token, which has the indicated
   * offset into text. Assumes that the indicated token is a case-*insensitive* match of the first
   * token in this entry!
   * 
   * @param mt memoization table for getting reusable buffers
   * @param target the original text on which we are performing matching
   * @param tokOffsets tokenization of the entire text in the appropriate language
   * @param tokId index of the token at which we are performing matching
   * @param output buffer into which to put matches
   * @param performLemmaMatch whether the match is performed against surface or lemmatized form
   * @return number of matches marked
   */
  public int markMatches(MemoizationTable mt, SpanText target, OffsetsList tokOffsets, int tokId,
      BaseOffsetsList output, boolean useTokenOffsets, boolean performLemmaMatch) {

    boolean debug = false;

    CompiledLangMatchInfo info = matchInfo.getLangInfo(target.getLanguage());

    if (null == info) {
      // No matches for this language.
      if (debug) {
        Log.debug("%8x --> No matches", this.hashCode());
      }
      return 0;
    }

    String text = target.getText();

    int[] caseIndices = info.caseSensitiveIndices;
    int[] noCaseIndices = info.caseInsensitiveIndices;

    int nmatch = 0;
    if (null != caseIndices) {
      if (debug) {
        Log.debug("%8x --> Looking for case-sensitive matches", this.hashCode());
      }
      nmatch += markMatches_case(mt, text, tokOffsets, tokId, output, useTokenOffsets, caseIndices,
          performLemmaMatch);
    }

    if (null != noCaseIndices) {
      if (debug) {
        Log.debug("%8x --> Looking for case-insensitive matches", this.hashCode());
      }
      nmatch += markMatches_nocase(mt, text, tokOffsets, tokId, output, useTokenOffsets,
          noCaseIndices, performLemmaMatch);
    }

    return nmatch;
  }

  /**
   * Utility method for subclasses; adds an entry for a single dictionary match.
   */
  protected int addMatchInfo(OffsetsList tokOffsets, int tokId, BaseOffsetsList output,
      boolean useTokenOffsets, int[] indices) {
    int nindex = indices.length;
    for (int i = 0; i < nindex; i++) {
      if (useTokenOffsets) {
        output.addEntry(tokId, tokId + getNumTokens() - 1, indices[i]);
      } else {
        output.addEntry(tokOffsets.begin(tokId), tokOffsets.end(tokId + getNumTokens() - 1),
            indices[i]);
      }
    }
    return nindex;
  }

  /*
   * ABSTRACT METHODS
   */

  /** Internal implementation of markMatches for case-sensitive matching. */
  protected abstract int markMatches_case(MemoizationTable mt, String text, OffsetsList tokOffsets,
      int tokId, BaseOffsetsList output, boolean useTokenOffsets, int[] caseIndices,
      boolean performLemmaMatch);

  /** Internal implementation of markMatches for case-insensitive matching. */
  protected abstract int markMatches_nocase(MemoizationTable mt, String text,
      OffsetsList tokOffsets, int tokId, BaseOffsetsList output, boolean useTokenOffsets,
      int[] noCaseIndices, boolean performLemmaMatch);

  protected abstract int getNumTokens();
}
