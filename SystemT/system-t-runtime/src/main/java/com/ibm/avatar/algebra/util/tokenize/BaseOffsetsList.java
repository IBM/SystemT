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

import com.ibm.avatar.api.exceptions.FatalInternalError;

/**
 * Data structure for holding the full set tokens for the base document text. Also used for holding
 * sets of dictionary/regex matches.
 * 
 */
public class BaseOffsetsList extends OffsetsList {

  public static final int STARTING_SZ = 1;

  /**
   * Maximum allowable length of our arrays after a call to {@link #reset()}. Should be a power of
   * 2!
   */
  public static final int MAX_PERMANENT_SZ = 16384;

  /** Begin offsets. The tail of this array may contain unused entries. */
  private int[] begins = new int[STARTING_SZ];

  private int[] ends = new int[STARTING_SZ];

  /**
   * Flag that is set to true if the offsets contained within are non-overlapping and occur in
   * sorted order.
   */
  private boolean offsetsAreTokens = true;

  /**
   * Dictionary indices (or some other integral parameter) corresponding to the entries in
   * {@link #begins} and {@link #ends}.
   */
  private int[] indices = new int[STARTING_SZ];

  /**
   * How many of the elements of {@link #begins} and {@link #ends} are in use. Accessed directly by
   * FastTokenizer to get around deficiencies of IBM Java 5's compiler.
   */
  protected int numUsed = 0;

  /**
   * Index for determining which token is closest to a given character position. Element i of this
   * index holds what lowerBoundIx(i) would return.
   */
  private int[] boundsIx = null;

  /**
   * Number of entries in the boundsIx array that contain valid values, or -1 if the array is
   * currently not in use.
   */
  private int numBoundsIx = -1;

  /**
   * Sequence number that is incremented every time {@link #reset()} is called, to prevent access to
   * stale data.
   */
  private int seqno = 0;

  /**
   * maintains lemmas
   */
  private String[] lemmas = new String[STARTING_SZ];

  protected int getSeqno() {
    return seqno;
  }

  /**
   * Auxiliary payload, used for state that you want to store along with the table.
   */
  private Object auxData = null;

  @Override
  public Object getAuxData() {
    return auxData;
  }

  @Override
  public void setAuxData(Object auxData) {
    this.auxData = auxData;
  }

  /**
   * returns value of isOffsetAreTokens
   * 
   * @return
   */
  public boolean isOffsetAreTokens() {
    return offsetsAreTokens;
  }

  public BaseOffsetsList() {
    setSize(0);
  }

  public void addEntry(int begin, int end, int index, String lemmaString) {
    addEntry(begin, end, index);
    lemmas[numUsed - 1] = lemmaString;
  }

  public void addEntry(int begin, int end, int index) {
    if (numUsed >= begins.length) {
      resizeArrays();
    }

    // Check sort orders while we're inserting.
    if (numUsed > 0 && begin < ends[numUsed - 1]) {
      offsetsAreTokens = false;
    }

    begins[numUsed] = begin;
    ends[numUsed] = end;
    indices[numUsed] = index;
    numUsed++;
    setSize(numUsed);
  }

  /**
   * Version of addEntry that doesn't touch the indices array; used by FastTokenize
   */
  public void addEntry(int begin, int end) {
    addEntry(begin, end, 0);
  }

  /**
   * Update begin and end offsets of each already-popped empty tag in case an artificial whitespace
   * was inserted at its previously recorded begin offset. Side-effect upon such a tag is that its
   * begin and end offsets shall now point to the value of input argument {@code newBegin}.
   * 
   * @param oldBegin previous begin offset of this tag in the detagged text
   * @param newBegin new begin offset of this tag in the detagged text
   * @return {@code true} when at least one popped empty tag's offsets were updated; {@code false}
   *         if no updates were made
   */
  public boolean updatePoppedEmptyTagEntries(int oldBegin, int newBegin) {
    boolean updatedOffsetOfPoppedEmptyTag = false;
    for (int x = 0; x < ends.length; ++x) {
      // Predicate 1 asserts that this tag's offsets need to be updated
      // Predicate 2 asserts that this tag is empty in value
      if (begin(x) == oldBegin && begin(x) == end(x)) {
        begins[x] = newBegin;
        ends[x] = newBegin;
        updatedOffsetOfPoppedEmptyTag = true;
      }
    }
    return updatedOffsetOfPoppedEmptyTag;
  }

  public void reset() {
    numUsed = 0;
    offsetsAreTokens = true;

    auxData = null;

    numBoundsIx = -1;

    // Don't keep huge arrays around for too long.
    if (begins.length > MAX_PERMANENT_SZ) {
      begins = new int[MAX_PERMANENT_SZ];
      ends = new int[MAX_PERMANENT_SZ];
      indices = new int[MAX_PERMANENT_SZ];
      lemmas = new String[MAX_PERMANENT_SZ];

      if (null != boundsIx) {
        boundsIx = new int[MAX_PERMANENT_SZ];
      }
    }

    // Tell the superclass what we've done.
    setSize(0);
  }

  @Override
  public int begin(int i) {
    if (i < 0 || i >= begins.length) {
      throw new FatalInternalError(
          "Attempted to retrieve begin offset of token %d (indexes numbered from 0) from an offsets list with %d entries",
          i, begins.length);
    }

    return begins[i];

  }

  @Override
  public int end(int i) {
    if (i < 0 || i >= ends.length) {
      throw new FatalInternalError(
          "Attempted to retrieve end offset of token %d (indexes numbered from 0) from an offsets list with %d entries",
          i, ends.length);
    }

    return ends[i];

  }

  @Override
  public int index(int i) {
    return indices[i];
  }

  /*
   * INTERNAL METHODS
   */

  /** Double our internal storage size. */
  private void resizeArrays() {
    begins = resizeIntArray(begins);
    ends = resizeIntArray(ends);
    indices = resizeIntArray(indices);
    lemmas = resizeStringArray(lemmas);
  }

  private static int[] resizeIntArray(int[] orig) {
    int[] ret = new int[2 * orig.length];
    System.arraycopy(orig, 0, ret, 0, orig.length);
    return ret;
  }

  private static String[] resizeStringArray(String[] orig) {
    String[] ret = new String[2 * orig.length];
    System.arraycopy(orig, 0, ret, 0, orig.length);
    return ret;
  }

  /**
   * @param off a character offset into the original string
   * @return if the offset is within a token, the index of that token; otherwise, the index of the
   *         first token after the indicated offset. If the offset is after the end of the last
   *         token, returns 1 + index of last token. Returns -1 if there are no tokens in the
   *         OffsetsList.
   */
  @Override
  public final int nextBeginIx(int off) {

    if (false == offsetsAreTokens) {
      // This method doesn't have meaning if the list does not contain
      // non-overlapping tokens.
      throw new RuntimeException("Offsets are not tokens");
    }

    int size = size();
    if (0 == size) {
      // SPECIAL CASE: Zero tokens
      return -1;
      // END SPECIAL CASE
    }

    // if (null == boundsIx) {
    if (-1 == numBoundsIx) {
      // No index; create one.

      // The index will go up to the end of the last token.
      int lastEndOff = end(size - 1);

      // Resize the array or create it if necessary
      if (null == boundsIx || boundsIx.length < lastEndOff) {
        boundsIx = new int[lastEndOff];
      }

      // Remember how many elements of this array have valid data.
      numBoundsIx = lastEndOff;

      // Position i in the index holds what lowerBoundIx(i) should return;
      // that is the first token that either overlaps the given offset or
      // starts after it.
      int tokIx = 0;
      for (int i = 0; i < lastEndOff; i++) {
        // Log.debug("Index %d: tokIx is %d (size is %d)", i, tokIx,
        // size());
        int tokEnd = end(tokIx);

        if (i < tokEnd) {
          // This position is before the end of the current token.
          boundsIx[i] = tokIx;
        } else {
          // Current position is past the end of the current token.
          boundsIx[i] = tokIx + 1;
          tokIx++;
        }

      }
    }

    // If we get here, there should be an index already created.
    if (off >= numBoundsIx) {
      // Everything past the end of the last token gets 1 + the last token
      // index.
      return size();
    } else {
      return boundsIx[off];
    }

  }

  // protected int upperBoundIx(int off) {
  //
  // if (USE_BINARY_SEARCH == BOUND_IX_METHOD) {
  // return upperBoundIx_binsearch(off);
  // } else if (USE_FULL_INDEX == BOUND_IX_METHOD) {
  // return upperBoundIx_fullix(off);
  // } else {
  // throw new RuntimeException(
  // "Don't know how to compute a upper bound with method "
  // + BOUND_IX_METHOD);
  // }
  //
  // }
  /**
   * Binary search-based implementation of {@link #lowerBoundIx(int)}.
   */

  // protected int upperBoundIx_binsearch(int off) {
  //
  // boolean debug = false;
  //
  // // Find the position where the offset would go in the array of token
  // // begin positions.
  // int ret = Arrays.binarySearch(begins, 0, numUsed, off);
  //
  // if (debug) {
  // Log.debug("Looking for offset %d in first %d entries of array %s",
  // off, numUsed, Arrays.toString(ends));
  // Log.debug(" ==> Arrays.binarySearch() returned %d", ret);
  // }
  //
  // if (ret < 0) {
  // // Arrays.binarySearch() returns index - 1, where <index> is the
  // // position at which the offset, if the exact value
  // // isn't found in the original array. Convert this negative value to
  // // the position where the offset *would* have been inserted.
  // ret = -ret - 1;
  // }
  //
  // // At this point, ret is set to the index of the next token that begins
  // // at or after the indicated token. We want to return the index of the
  // // previous token, or -1 if we found the first token.
  // return ret - 1;
  // }
  /**
   * @param off a character offset into the original string
   * @return index of the last token that starts before the indicated location. If the indicated
   *         location is before the start of the first token, returns -1.
   */
  @Override
  public int prevBeginIx(int off) {

    // Start from the lower bound token.
    int lbIx = nextBeginIx(off);

    if (-1 == lbIx) {
      // SPECIAL CASE: No tokens
      return -1;
      // END SPECIAL CASE
    }

    // Assuming that tokens don't overlap, the answer will always be either
    // the lower bound token or the one before it (or -1 if the lower bound
    // is 0)
    if (size() == lbIx) {
      // SPECIAL CASE: Lower bound is 1 + last token ix.
      return lbIx - 1;
      // END SPECIAL CASE
    }

    // Normal case: Lower bound between 0 and size() - 1, inclusive.
    int lbBegin = begin(lbIx);
    if (off <= lbBegin) {
      // Offset is at or before the beginning of the lower-bound
      // token; so the previous token must be the "upper-bound" one.
      return lbIx - 1;
    } else {
      return lbIx;
    }

  }

  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.avatar.algebra.util.tokenize.OffsetsList#getLemma(int)
   */
  @Override
  public String getLemma(int tokenIndex) {
    if (tokenIndex < 0 || tokenIndex >= lemmas.length) {
      throw new FatalInternalError(
          "Attempted to retrieve lemma of token %d (indexes numbered from 0) from an offsets list with %d entries",
          tokenIndex, ends.length);
    }

    return lemmas[tokenIndex];
  }
}
