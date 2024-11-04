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
package com.ibm.avatar.algebra.util.tokenize;

import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.logging.Log;

/**
 * Data structure that represents a sub-range of the offsets in a {@link BaseOffsetsList} instance,
 * without replicating the associated arrays.
 * 
 */
public class DerivedOffsetsList extends OffsetsList {

  /**
   * The original, full set of offsets into the full text.
   */
  private BaseOffsetsList base;

  /**
   * BaseOffsetsList objects have a sequence number that is incremented every time the list is
   * reset. We track the sequence number of the base list at the time this derived list was created,
   * so as to avoid reading stale data.
   */
  private int baseSeqNo;

  /**
   * Index of first token/match in the base offsets list that we store. The range covered may start
   * in the middle of this token. Set to -1 if no tokens are contained in the substring.
   */
  private int firstIx;

  /**
   * How far from the beginning of the first token the substring starts.
   */
  private int firstOff;

  /**
   * Index of the last token/match (inclusive) in the base offsets list we store. The range covered
   * may end in the middle of this token.
   */
  private int lastIx;

  /**
   * How far from the end of the last token the substring ends.
   */
  private int lastOff;

  /**
   * Offset to subtract from all values returned by {@link #begin(int)} and {@link #end(int)}
   */
  private int spanStart;

  /**
   * Special constructor for an empty offsets list; only call this if you will later call
   * {@link #init(BaseOffsetsList, int, int, int)} to initialize the list.
   */
  public DerivedOffsetsList() {

  }

  /**
   * Main constructor. Converts offsets into the original string into token indices.
   * 
   * @param base The original, full set of offsets into the full text.
   * @param begin beginning character offset of the substring we are to cover, relative to the base
   *        text
   * @param end end of the substring we will cover
   * @param spanStart beginning of the span within the base string that we are tokenizing for
   */
  public DerivedOffsetsList(BaseOffsetsList base, int begin, int end, int spanStart) {
    init(base, begin, end, spanStart);
  }

  /**
   * Initialize (or re-initialize) a DerivedOffsetsList object
   * 
   * @param base The original, full set of offsets into the full text.
   * @param begin beginning character offset of the substring we are to cover, relative to the base
   *        text
   * @param end end of the substring we will cover
   * @param spanStart beginning of the span within the base string that we are tokenizing for
   */
  public void init(BaseOffsetsList base, int begin, int end, int spanStart) {
    final boolean debug = false;

    this.base = base;
    this.baseSeqNo = base.getSeqno();

    // Compute start/end indices
    int firstTokenAtOrAfterBegin = base.nextBeginIx(begin);
    int lastTokenAtOrBeforeEnd = base.prevBeginIx(end);

    if (debug) {
      Log.debug("DerivedOffsetsList.init(): Span from %d to %d, span starting at char %d, "
          + "base offsets list has %d tokens", begin, end, spanStart, base.size());
      Log.debug("firstTokenAtOrAfterBegin = %d", firstTokenAtOrAfterBegin);
      Log.debug("lastTokenAtOrBeforeEnd = %d", lastTokenAtOrBeforeEnd);
    }

    // Check for the cases where there is no token within the specified
    // boundary
    if (base.size() == firstTokenAtOrAfterBegin) {
      // SPECIAL CASE: Begin offset is after the last token.
      setEmptyList();
      // END SPECIAL CASE
    } else if (-1 == firstTokenAtOrAfterBegin) {
      // SPECIAL CASE: Empty base tokens
      setEmptyList();
      // END SPECIAL CASE
    } else if (-1 == lastTokenAtOrBeforeEnd) {
      // SPECIAL CASE: End offset is before the first token.
      setEmptyList();
      // END SPECIAL CASE
    } else if (firstTokenAtOrAfterBegin > lastTokenAtOrBeforeEnd) {
      // SPECIAL CASE: No tokens contained within the selected range
      setEmptyList();
      // END SPECIAL CASE
    } else {

      // If we get here, there is at least one token within the specified
      // range.
      firstIx = firstTokenAtOrAfterBegin;
      lastIx = lastTokenAtOrBeforeEnd;

      // Compute character offsets relative to the token offsets.
      firstOff = begin - base.begin(firstIx);
      lastOff = end - base.end(lastIx);

      this.spanStart = spanStart;

      if (debug) {
        Log.debug("DerivedOffsetsList: Span from %d to %d", begin, end);
        Log.debug("    First token ix is %d (%d to %d), with offset %d", firstIx,
            base.begin(firstIx), base.end(firstIx), firstOff);
        Log.debug("    Last token ix is %d (%d to %d), with offset %d", lastIx, base.begin(lastIx),
            base.end(lastIx), lastOff);
        // Log.debug (" After constructor, computed bounds are %d and %d", begin (0), end (size () -
        // 1));
      }

      // Tell the superclass what is the size of this list.
      // Other branches have their size set by the setEmptyList() method
      setSize((lastIx - firstIx) + 1);
    }

  }

  /**
   * Check the base list's sequence number to verify that this object isn't accessing stale token
   * data.
   */
  private final void checkSeqNo() {
    if (base.getSeqno() != baseSeqNo) {
      throw new RuntimeException(String.format(
          "Access to stale data detected " + "(sequence numbers %d and %d do not match)", baseSeqNo,
          base.getSeqno()));
    }
  }

  /**
   * Initialize this offsets list to be an empty list.
   */
  private void setEmptyList() {
    firstIx = lastIx = Span.NO_TOKENS;
    setSize(0);
  }

  /**
   * @return true if this offsets list is empty, i.e. it contains no tokens at all
   */
  private boolean isEmptyList() {
    return (Span.NO_TOKENS == firstIx);
  }

  @Override
  public int begin(int i) {
    // Make sure we're not reading stale data.
    checkSeqNo();

    if (i < 0 || i >= size()) {
      throw new FatalInternalError(
          "Bad index %d (indexes numbered from 0) on size %d DerivedOffsetsList", i, size());
    }

    // First compute an offset relative to the base string (as opposed to
    // the substring)
    int baseOff;
    if (0 == i && firstOff > 0) {
      // SPECIAL CASE: First token, and the first token starts in the
      // *middle* of a token in the original document text.
      // Correct for the substring starting in the middle of a token.
      baseOff = base.begin(firstIx) + firstOff;
      // END SPECIAL CASE
    } else {
      baseOff = base.begin(firstIx + i);
    }

    // Remap the offset to an offset within the span
    return baseOff - spanStart;
  }

  @Override
  public int end(int i) {
    // Make sure we're not reading stale data.
    checkSeqNo();

    int size = size();
    if (i >= size) {
      throw new ArrayIndexOutOfBoundsException(i);
    }

    // First compute an offset relative to the base string (as opposed to
    // the substring)
    int baseOff;

    if (i == size - 1 && lastOff < 0) {
      // SPECIAL CASE: Last token, and the last token ends in the middle
      // of a token in the original document text.
      // Correct for the last token ending in the middle of a token.
      baseOff = base.end(lastIx) + lastOff;
      // END SPECIAL CASE
    } else {
      baseOff = base.end(firstIx + i);
    }

    // Remap the offset to an offset within the span
    return baseOff - spanStart;
  }

  @Override
  public int index(int i) {
    // Make sure we're not reading stale data.
    checkSeqNo();

    return base.index(firstIx + i);
  }

  @Override
  public int nextBeginIx(int offset) {
    // Make sure we're not reading stale data.
    checkSeqNo();

    if (isEmptyList()) {
      // SPECIAL CASE: Empty list
      return -1;
      // END SPECIAL CASE
    }

    // Convert the offset into our substring into an offset into the
    // original string.
    int baseOffset = offset + substrBegin();

    // Ask the "real" token list where the next token is.
    int baseIx = base.nextBeginIx(baseOffset);

    // Convert this token index to a token index over the substring.
    return baseIx - firstIx;
  }

  @Override
  public int prevBeginIx(int offset) {
    // Make sure we're not reading stale data.
    checkSeqNo();

    if (-1 == firstIx) {
      // SPECIAL CASE: Zero tokens
      return -1;
      // END SPECIAL CASE
    }

    // Convert the offset into our substring into an offset into the
    // original string.
    int baseOffset = offset + substrBegin();

    // Ask the "real" token list where the previous token is.
    int baseIx = base.prevBeginIx(baseOffset);

    // Sanity check
    if (baseIx >= base.size()) {
      throw new FatalInternalError(
          "BaseOffsetsList.prevBeginIx(%d) on a tuple list of size %d returned %d", baseOffset,
          base.size(), baseIx);
    }

    // Convert to an offset within the tokens covered by this list
    int ret = baseIx - firstIx;

    // The base TupleList may have returned an offset that is out of the range we cover.
    if (ret < 0) {
      return -1;
    }

    if (ret > lastIx - firstIx) {
      return lastIx - firstIx;
    }

    return ret;
  }

  /*
   * METHODS FOR THE EXCLUSIVE USE OF THE Tokenizer CLASS
   */

  /**
   * Method for the exclusive use of the {@link Tokenizer} class.
   * 
   * @return true if the substring that this object represents both begins and ends on a token
   *         boundary.
   */
  protected boolean isOnTokenBoundaries() {
    if (isEmptyList()) {
      return false;
    } else if (0 == firstOff && 0 == lastOff) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * Method for the exclusive use of the {@link Tokenizer} class.
   * 
   * @return true if the substring that this object represents begins on a token boundary.
   */
  protected boolean beginsOnTokenBoundary() {
    if (0 == firstOff) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * Method for the exclusive use of the {@link Tokenizer} class.
   * 
   * @return true if the substring that this object represents ends on a token boundary.
   */
  protected boolean endsOnTokenBoundary() {
    if (0 == lastOff) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * Method for internal use.
   * 
   * @return index of first token of our substring within the original text
   */
  public int getFirstIx() {
    return firstIx;
  }

  /**
   * Method for internal use.
   * 
   * @return index of last token of our substring within the original text
   */
  public int getLastIx() {
    return lastIx;
  }

  /*
   * UTILITY METHODS
   */

  /**
   * @return where the substring that this offsets list annotates begins in the original string
   */
  private int substrBegin() {
    if (Span.NO_TOKENS == firstIx) {
      throw new FatalInternalError(
          "Attempted to compute the character range covered by a DerivedOffsetsList that contains no tokens.");
    }

    // We calculate this value rather than storing it, in order to save
    // space, DerivedOffsetsList objects being extremely common.
    return base.begin(firstIx) + firstOff;
  }

  /**
   * @param tokenIndex tokenIndex is index in this derived span and ranges from 0 to size()-1
   * @see com.ibm.avatar.algebra.util.tokenize.OffsetsList#getLemma(int)
   */
  @Override
  public String getLemma(int tokenIndex) {
    return base.getLemma(firstIx + tokenIndex);
  }

  // @Override
  // protected int lowerBoundIx(int off) {
  //
  // // Convert the offset, which is relative to our original string, to an
  // // offset relative to the base text.
  // int baseOff = off + substrBegin();
  //
  // int baseIx = base.lowerBoundIx(baseOff);
  //
  // return baseIx + firstIx;
  // }
}
