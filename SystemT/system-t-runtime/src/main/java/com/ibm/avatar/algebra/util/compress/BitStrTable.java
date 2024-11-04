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
package com.ibm.avatar.algebra.util.compress;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import com.ibm.avatar.algebra.datamodel.Span;

/**
 * A table of bit strings. Supports retrieving all entries that match a particular variable-length
 * prefix. Optimized for typical compressed dictionary entry lengths.
 */
public class BitStrTable {

  /** The first 64 bits of each entry; ordered lexicographically */
  private long[] prefixes = new long[1024];

  private int numEntries = 0;

  /**
   * Buffer holding the remainder of each entry. Each remainder is encoded as a range of chars, with
   * the first char holding the length of the range.
   */
  private char[] remainders = new char[1024];

  private int remUsed = 0;

  /**
   * Table of offsets into the remainders array; one for each entry in prefixes.
   */
  private int[] remOffsets = new int[1024];

  enum State {
    LOADING, SORTED
  }

  private State state = State.LOADING;

  /**
   * Create a new table.
   * 
   * @param strings the compressed bit strings that will go into the table.
   */
  public BitStrTable(List<BitStr> strings) {

    prefixes = new long[strings.size()];

    remainders = new char[1024];

    int remUsed = 0;

    // Start by putting all the values in place.

    // Shrink the remainders table to its smallest possible size.
    char[] newRem = new char[remUsed];
    System.arraycopy(remainders, 0, newRem, 0, remUsed);
    remainders = newRem;

    // Sort the bit strings. We create a table of offsets into
    // prefixes/remOffsets; these offsets function as proxies for the actual
    // bit strings. Finally, we rearrange prefixes and remOffsets.

  }

  public void addEntry(BitStr entry) {
    if (false == (State.LOADING.equals(state))) {
      throw new RuntimeException("Adding an entry after packing.");
    }

    // Make sure there's room in our arrays.
    if (numEntries >= prefixes.length) {
      long[] newPre = new long[prefixes.length * 2];
      System.arraycopy(prefixes, 0, newPre, 0, numEntries);
      prefixes = newPre;

      int[] newOff = new int[remOffsets.length * 2];
      System.arraycopy(remOffsets, 0, newOff, 0, numEntries);
      remOffsets = newOff;
    }

    // Pack the first 4 chars of the entry into long.
    long prefix = 0x0L;

    for (int i = 0; i < 4 && i < entry.length; i++) {
      prefix |= ((long) entry.chars[i]) << (16 * (3 - i));
    }

    prefixes[numEntries] = prefix;

    // Build up a remainder, if necessary.
    final int PREFIX_LEN_CHARS = 4;
    if (entry.length < PREFIX_LEN_CHARS) {
      remOffsets[numEntries] = 0;
    } else {
      int remLen = entry.length - PREFIX_LEN_CHARS;

      // Make sure there's room in the remainder table.
      int newRemUsed = remUsed + remLen;
      if (newRemUsed >= remainders.length) {
        char[] newRem = new char[remainders.length * 2];
        System.arraycopy(remainders, 0, newRem, 0, remUsed);
        remainders = newRem;
      }

      // Put the remainder in place.
      int remOff = remUsed;

      // First char of the remainder holds the length.
      remainders[remOff] = (char) remLen;

      // Remaining chars hold the remainder itself.
      for (int i = 0; i < remLen; i++) {
        remainders[remOff + i] = entry.chars[PREFIX_LEN_CHARS + i];
      }

      remUsed += remLen + 1;

      remOffsets[numEntries] = remOff;
    }

    numEntries++;
  }

  /**
   * Pack and sort the entries added thus far. After this method is called, no more entries can be
   * added.
   */
  public void packEntries() {
    if (State.SORTED.equals(state)) {
      throw new RuntimeException("Tried to pack entries twice.");
    }

    if (0 == numEntries) {
      state = State.SORTED;
      return;
    }

    // Sort the bit strings; this method also shrinks the prefixes and
    // remOffsets arrays.
    sort();

    // Shrink the overflow table down as small as possible.
    char[] newRem = new char[remUsed];
    System.arraycopy(remainders, 0, newRem, 0, remUsed);
    remainders = newRem;

    state = State.SORTED;
  }

  /**
   * Retrieve the range of entries that match the indicated prefix.
   * 
   * @return begin and end of the range, with the same semantics as the begin and end of a
   *         {@link Span}.
   */
  public int[] findPrefixRange(BitStr prefix) {

    // Build up the first 64 bits of the indicated prefix.
    long prefix64 = prefix.get64MSB();

    // Match on the first up to 64 bits.
    int start = Arrays.binarySearch(prefixes, prefix64);

    // TODO: Scan forward until we find a match of the total prefix, which
    // can be longer than 64 bits.

    int end = start;

    // Scan forward linearly, on the assumption that there will be few
    // duplicates in the prefixes table.
    while (prefix64 == maskMSB(prefixes[end], prefix.length)) {
      end++;
    }

    int[] ret = {start, end};

    return ret;
  }

  /**
   * @param l a 64-bit integer
   * @param length a number of bits (may be greater than 64)
   * @return the indicated number of MSB's of the integer, with everything to the right turned to a
   *         zero.
   */
  private long maskMSB(long l, int length) {

    if (length >= 64) {
      return l;
    }

    long mask = (0xFFFFFFFFFFFFFFFFL << (64 - length));
    return l & mask;

  }

  private void sort() {
    // We create a table of offsets into prefixes/remOffsets; these offsets
    // function as proxies for the actual bit strings. Finally, we rearrange
    // prefixes and remOffsets.
    Integer[] indices = new Integer[numEntries];
    for (int i = 0; i < numEntries; i++) {
      indices[i] = i;
    }

    Arrays.sort(indices, new sortComp());

    // Now rearrange the arrays, shrinking them while we're at it.
    long[] newPre = new long[numEntries];
    int[] newOff = new int[numEntries];

    for (int i = 0; i < indices.length; i++) {
      int oldIndex = indices[i];

      newPre[i] = prefixes[oldIndex];
      newOff[i] = remOffsets[oldIndex];
    }

    prefixes = newPre;
    remOffsets = newOff;

  }

  /** Specialized comparator for use in the {@link #sort()} method. */
  private class sortComp implements Comparator<Integer> {

    /**
     * Compares the bit strings with the indicated indices in prefixes/remOffsets.
     */
    @Override
    public int compare(Integer o1, Integer o2) {
      // Start by comparing the prefixes.
      int firstIx = o1;
      int secondIx = o2;

      long firstPrefix = prefixes[firstIx];
      long secondPrefix = prefixes[secondIx];

      long val = firstPrefix - secondPrefix;

      if (val < 0) {
        return -1;
      } else if (val > 0) {
        return 1;
      } else {
        // Prefixes are the same; compare on overflow bits.
        int firstOff = remOffsets[firstIx];
        int secondOff = remOffsets[secondIx];

        int firstRemLen = remainders[firstOff];
        int secondRemLen = remainders[secondOff];

        // Leave room for the char that holds the length!
        int firstEnd = firstOff + firstRemLen + 1;
        int secondEnd = secondOff + secondRemLen + 1;

        firstOff++;
        secondOff++;

        // Compare each one character by character.
        while (firstOff <= firstEnd || secondOff <= secondEnd) {

          // Pad the shorter string with zeros.
          char firstChar = (firstOff <= firstEnd) ? remainders[firstOff] : 0;
          char secondChar = (secondOff <= secondEnd) ? remainders[secondOff] : 0;

          if (firstChar < secondChar) {
            return -1;
          } else if (firstChar > secondChar) {
            return 1;
          }

          firstOff++;
          secondOff++;
        }

        // If we get here, we've gone through both strings and found no
        // differences. Do a last-ditch comparison on string length.
        return firstRemLen - secondRemLen;
      }

    }

  }
}
