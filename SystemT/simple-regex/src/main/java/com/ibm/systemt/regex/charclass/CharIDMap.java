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
package com.ibm.systemt.regex.charclass;

import java.util.Arrays;

/**
 * Specialized char to char map for use as a compact representation of the tail of the char ID table
 * in a SimpleRegex. Stores a sorted array of intervals.
 */
public class CharIDMap {

  /**
   * The maximum ID value (inclusive) that this class can store for a given character. We only have
   * 15 bits to store the ID, and the value 0 is reserved to make search faster.
   */
  public static final char MAX_ID_VAL = 32766;

  /** Minimum index of the original table for which we store an interval. */
  private int minIx;

  /**
   * Sorted list of intervals. Each interval is encoded with its starting val, followed by the ID
   * for the interval, both of which are packed into the lower 31 bits of an int.
   */
  private int[] intervals;

  private int numIntervals;

  /**
   * Main constructor.
   * 
   * @param capacity capacity of the class for intervals
   */
  public CharIDMap(int capacity) {
    minIx = Character.MAX_VALUE;
    intervals = new int[capacity];
    numIntervals = 0;
  }

  /**
   * Add the next interval; <b>must be called in order by (start)!</b>
   * 
   * @param start beginning of the interval (intervals are assumed to completely cover the range of
   *        character values.)
   * @param id character class id for all the characters in the interval
   */
  public void addInterval(char start, char id) {
    // We can only encode IDs up to 32766, since we are unable to use the
    // sign bit of our ints and the value 0 is reserved.
    if (id > MAX_ID_VAL) {
      throw new RuntimeException(String.format("ID %d (for char '%s') is too large", (int) id,
          CharSet.makePrintable(start)));
    }

    if (numIntervals >= intervals.length) {
      throw new RuntimeException("Tried to add too many intervals to CharIDMap");
    }

    int packedInterval = encodeInterval(start, id);

    // Intervals must be inserted in order.
    if (numIntervals > 0 && packedInterval < intervals[numIntervals - 1]) {
      throw new RuntimeException("Interval inserted out of order");
    }

    if (0 == numIntervals) {
      minIx = start;
    }

    intervals[numIntervals++] = packedInterval;
  }

  /**
   * @param c a character within this class's range of intervals
   * @return id for the interval that contains the indicated character
   */
  public char lookup(char c) {

    if (c < minIx) {
      throw new RuntimeException(String.format("Char '%s' (%d) is below minimum index %d",
          CharSet.makePrintable(c), (int) c, minIx));
    }

    // Intervals are encoded as ints, with a sign bit of 0, the next 16 bits
    // encoding the start of the interval, and the remaining bits encoding
    // the id for the interval. Create a search key consisting of the
    // indicated key, padded out with 1's.
    int key = encodeInterval(c, (char) 32767);

    int ix = Arrays.binarySearch(intervals, key);

    // Since we never use 32767 in the ID field, the key will never appear
    // in
    // the intervals table.
    // binarySearch() returns (-(insertion point) - 1) when it doesn't find
    // the key, where (insertion point) is the index where the indicated
    // value would be inserted.
    // Our key is guaranteed to be the next int after the insertion point.
    int insertionPoint = -(ix + 1);

    return getId(intervals[insertionPoint - 1]);
  }

  /** Pack an interval and its ID into an int. */
  private int encodeInterval(char start, char id) {
    int startInt = start;
    int idInt = id;

    return ((startInt << 15) | (idInt));
  }

  private char getId(int encodedInterval) {
    // Mask out the lower 15 bits.
    int idField = encodedInterval & 32767;

    return (char) (idField);
  }
}
