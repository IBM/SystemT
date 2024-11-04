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

/**
 * Class that encapsulates the various buffers used for storing matches of a SimpleRegex. Also
 * contains logic for dealing with the two built-in match modes of SimpleRegex (single longest
 * match, multiple longest match). User code can create subclasses to implement new match modes.
 * 
 * This class is <b>not</b> reentrant; callers must ensure that a given instance of this class is
 * only called from a single thread at a time.
 * 
 * 
 */
public class SimpleMatchBuf {

  public static final int NOT_AN_OFFSET = -1;

  /** Different ways that we can record matches of the expression. */
  private enum MatchMode {

    /** Record the longest match of any sub-expression */
    LONGEST_SINGLE,

    /** Record the longest match for <b>each</b> sub-expression */
    LONGEST_MULTI,

  }

  private MatchMode matchmode = MatchMode.LONGEST_SINGLE;

  /**
   * Flag that is set to TRUE if a match has been inserted into this buffer since the last call to
   * reset.
   */
  protected boolean dirty = true;

  /**
   * In the LONGEST_SINGLE match mode, the longest single match seen so far, or -1 for no matches.
   */
  private int matchLen;

  /**
   * In the LONGEST_MULTI or TOKEN_MULTI match mode, the longest matches seen for each
   * sub-expression, or -1 for no matches.
   */
  protected int[] matchLens = null;

  /**
   * In TOKEN_MULTI match mode, the current starting character offset.
   */
  protected int startChar = NOT_AN_OFFSET;

  /**
   * Reusable buffer for storing the current set of NFA states.
   */
  protected int[] curStates = new int[16];

  /**
   * Reusable buffer for storing the next set of NFA states.
   */
  protected int[] nextStates = new int[16];

  /*
   * METHODS
   */

  /**
   * @return Flag that is set to TRUE if a match has been inserted into this buffer since the last
   *         call to reset.
   */
  public boolean getDirty() {
    return dirty;
  }

  /**
   * @return In the LONGEST_SINGLE match mode, the longest single match seen so far, or -1 for no
   *         matches.
   */
  public int getMatchLen() {
    if (MatchMode.LONGEST_SINGLE != matchmode) {
      throw new RuntimeException("Not in single-longest-match mode");
    }
    return matchLen;
  }

  /**
   * @return In the LONGEST_MULTI or TOKEN_MULTI match mode, the longest matches seen for each
   *         sub-expression, or -1 for no matches.
   */
  public int[] getMatchLens() {
    if (MatchMode.LONGEST_MULTI != matchmode) {
      throw new RuntimeException("Not in multi-match mode");
    }
    return matchLens;
  }

  /**
   * Empty this buffer of any matches from a previous run.
   */
  public void reset() {
    switch (matchmode) {

      case LONGEST_SINGLE:
        matchLen = NOT_AN_OFFSET;
        dirty = false;
        break;

      case LONGEST_MULTI:
        Arrays.fill(matchLens, NOT_AN_OFFSET);
        dirty = false;
        break;

      default:
        throw new RuntimeException("Unsupported match mode");
    }
  }

  /**
   * Put this buffer into a mode where it tracks the longest overall match across all
   * sub-expressions. Also resets the buffer.
   */
  public void setLongestSingleMode() {
    matchmode = MatchMode.LONGEST_SINGLE;
    reset();
  }

  /**
   * Put this buffer into a mode where it tracks the longest match for each of the sub-expressions
   * that went into the current master state machine.
   * 
   * @param numSubExprs number of sub-expressions to collect separate match lengths for
   */
  public void setLongestMultiMode(int numSubExprs) {
    matchmode = MatchMode.LONGEST_MULTI;
    if (null == matchLens || matchLens.length < numSubExprs) {
      matchLens = new int[numSubExprs];
    }
  }

  /**
   * Add information about the state machine reaching an accepting state. This method is assumed to
   * be called on strictly increasing offsets.
   * 
   * @param len length of the substring that the state machine has consumed, starting at the current
   *        start position.
   * @param exprIx index of the (sub)expression that reached an accepting state at the indicated
   *        position
   */
  public void addMatch(int len, int exprIx) {

    dirty = true;

    // We handle the match differently depending on what kind of matches the
    // caller has requested.
    switch (matchmode) {

      case LONGEST_SINGLE:
        matchLen = len;
        break;

      case LONGEST_MULTI: {

        matchLens[exprIx] = len;
        break;
      }

      default:
        throw new RuntimeException("Unsupported match mode");
    }

  }

  /**
   * Makes sure that the buffers for storing the current and next set of NFA states are big enough.
   * 
   * @param minBufSize minimum size of the buffers
   */
  public void ensureNFASpace(int minBufSize) {

    if (curStates.length <= minBufSize) {
      curStates = resizeArray(curStates, minBufSize);
    }
    if (nextStates.length <= minBufSize) {
      nextStates = resizeArray(nextStates, minBufSize);
    }

  }

  /**
   * Internal method to resize an array. Allocates a new array and copies all elements of the old
   * array into the new array.
   * 
   * @param array original array
   * 
   * @param targetSize new target size for the array; the actual size of the returned array will be
   *        at least as large as the target.
   * @return new, resized array
   */
  private int[] resizeArray(int[] array, int targetSize) {

    // Each call to this method should *at least* double the size of the
    // array. Sometimes the target size is more than double the array, so
    // check for that case.
    int newLen = Math.max(array.length * 2, targetSize);

    int[] newNextStates = new int[newLen];
    System.arraycopy(array, 0, newNextStates, 0, array.length);
    array = newNextStates;
    return array;
  }

  /**
   * @return reusable buffer for holding the set of states the NFA is currently in.
   * 
   *         NOTE: This buffer will be swapped with the "next states" buffer every time that
   *         {@link #swapNFAStateBufs()} is called!
   * 
   *         NOTE: This buffer may be resized on calls to {@link #ensureNFASpace(int)}!
   */
  public int[] getCurNFAStatesBuf() {
    return curStates;
  }

  /**
   * @return reusable buffer for holding the set of states the NFA will be in on the next iteration
   *         of automaton evaluation.
   * 
   *         NOTE: This buffer will be swapped with the "current states" buffer every time that
   *         {@link #swapNFAStateBufs()} is called!
   * 
   *         NOTE: This buffer may be resized on calls to {@link #ensureNFASpace(int)}!
   */
  public int[] getNextNFAStatesBuf() {
    return nextStates;
  }

  /**
   * Swaps the two NFA state buffers in place. Note that this call invalidates the values returned
   * by {@link #getCurNFAStatesBuf()} and {@link #getNextNFAStatesBuf()}!
   */
  public void swapNFAStateBufs() {
    int[] tmp = curStates;
    curStates = nextStates;
    nextStates = tmp;
  }

}
