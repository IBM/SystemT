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
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ibm.systemt.regex.util.Pair;

/**
 * Class that encapsulates a set of characters that can appear at a given point in a regex.
 * Interface is a subset of that of a TreeSet.
 */
public class CharSet {

  /** A magic string containing all 65536 characters. */
  public static final String ALL_CHARS_STR = genAllCharsStr();

  private static String genAllCharsStr() {
    StringBuilder sb = new StringBuilder();
    for (char c = 0; c < Character.MAX_VALUE; c++) {
      sb.append(c);
    }
    return sb.toString();
  }

  /** A bitmap containing one bit for every character in the set. */
  // private boolean bitmap[] = new boolean[Character.MAX_VALUE];
  private static final int STARTING_ARRAY_SIZE = 4;

  /**
   * A list of the char intervals this charset contains. Each range is a pair of chars, encoded as a
   * single long. We would use an int, but the sign bit screws things up.
   */
  long[] ranges = new long[STARTING_ARRAY_SIZE];

  private int numRanges = 0;

  /** Number of bits in the bitmap that are set to 1. */
  // private int size = 0;
  /** A string containing all the characters in the set. Created on demand. */
  private String setStr = null;

  /**
   * Serialization ID, to make the compiler stop complaining.
   */
  // private static final long serialVersionUID = 1L;

  /** Create an empty set. */
  public CharSet() {}

  /** Create a character set with a single character. */
  public CharSet(char c) {
    add(c);
  }

  /** Create a character set with an inclusive range of chars. */
  public CharSet(char first, char last) {
    addRange(first, last);
    compactRanges();
  }

  public void add(char c) {
    // Create a new range for the character.
    addRange(c, c);

    // Merge any ranges that are now adjacent.
    compactRanges();
  }

  /** Sort the ranges and merge any that overlap or touch. */
  private void compactRanges() {
    boolean debug = false;

    if (numRanges <= 1) {
      // SPECIAL CASE: No ranges, or only one; don't need to compact
      trimRangesArray();
      return;
      // END SPECIAL CASE
    }

    // Start by sorting the ranges by <begin, end>
    Arrays.sort(ranges, 0, numRanges);

    // Compact the array in place.
    int newNumRanges = 0;

    int curStart = rangeStart(ranges[0]);
    int curEnd = rangeEnd(ranges[0]);

    for (int i = 1; i < numRanges; i++) {
      // Try to add the next range to the one we're building up.
      int nextStart = rangeStart(ranges[i]);
      int nextEnd = rangeEnd(ranges[i]);

      // if (debug) {
      // System.err.printf("Next start is %s; curEnd is %s\n",
      // makePrintable((char) nextStart),
      // makePrintable((char) curEnd));
      // }

      if (nextStart <= curEnd + 1) {
        // if (debug) {
        // System.err.printf("**** Ranges overlap!\n");
        // }

        // Next range overlaps or is adjacent with current one.
        curEnd = Math.max(curEnd, nextEnd);
      } else {
        if (debug) {
          System.err.printf("*** No overlap; adding range [%s-%s]\n",
              makePrintable((char) curStart), makePrintable((char) curEnd));
        }
        // Next range cannot be merged with this one; output the current
        // range and start a new range.
        long encodedRange = encodeRange((char) curStart, (char) curEnd);

        // if (debug) {
        // System.err.printf("*** Encoded [%x,%x] as %x\n", curStart,
        // curEnd, encodedRange);
        // }

        ranges[newNumRanges++] = encodedRange;
        curStart = nextStart;
        curEnd = nextEnd;

        // if (debug) {
        // System.err.printf("*** tmp is now %s\n", toDetailedString(
        // tmp, newNumRanges));
        // }
      }
    }

    // Add the final remaining range to the set.
    ranges[newNumRanges++] = encodeRange((char) curStart, (char) curEnd);

    if (debug) {
      System.err.printf("compactRanges(): Went from %d to %d ranges\n", numRanges, newNumRanges);
      System.err.printf("Before: %s\n", toString());
    }

    // Swap in the new set of ranges.
    // ranges = tmp;
    numRanges = newNumRanges;

    // Trim the array if possible.
    trimRangesArray();

    if (debug) {
      System.err.printf("After: %s\n", toString());
    }

    // Invalidate our string representation.
    setStr = null;
  }

  private void trimRangesArray() {
    if (numRanges < ranges.length) {
      long[] tmp = new long[numRanges];
      System.arraycopy(ranges, 0, tmp, 0, numRanges);
      ranges = tmp;
    }
  }

  private static char rangeStart(long encodedRange) {
    // Mask out the upper 16 bits.
    return (char) ((encodedRange >> 32) & 65535);
  }

  private static char rangeEnd(long encodedRange) {
    // Mask out the lower 16 bits.
    return (char) (encodedRange & 65535);
  }

  private static long encodeRange(char first, char last) {
    // High-order bits hold begin; low order bits end.
    long firstLong = (long) first;
    long lastLong = (long) last;

    long firstMask = (firstLong << 32);

    // if (first != last) {
    // System.err.printf("encodeRange(%x,%x): First mask is %x\n",
    // firstInt, lastInt, firstMask);
    // }
    long ret = (firstMask | (lastLong));

    return ret;
  }

  /**
   * Create a new range and add it to our list of ranges
   * 
   * @param first first character in the range
   * @param last last char in the range, inclusive
   */
  private void addRange(char first, char last) {
    if (numRanges >= ranges.length) {
      // Resize the array.
      long[] tmp = new long[ranges.length * 2];
      System.arraycopy(ranges, 0, tmp, 0, ranges.length);
      ranges = tmp;
    }

    // Encode the range; beginning in high-order bits, end in low-order
    // bits.
    ranges[numRanges++] = encodeRange(first, last);
  }

  public void addAll(CharSet cs) {
    // Append all the ranges in the other character set, then clean up.
    for (int i = 0; i < cs.numRanges; i++) {
      long encodedRange = cs.ranges[i];
      addRange(rangeStart(encodedRange), rangeEnd(encodedRange));
    }

    compactRanges();
  }

  public boolean contains(char c) {
    boolean debug = false;

    // Binary-search for the character among our ranges.
    // We do this search by first encoding a singleton range, then
    // finding the range that contains it.
    long searchKey = encodeRange(c, c);
    int ix = Arrays.binarySearch(ranges, searchKey);
    // ranges,0, numRanges - 1, searchKey);

    // Arrays.binarySearch() returns (-(insertion point) - 1) if it doesn't
    // find the key, where (insertion point) is the location where the value
    // would go.
    // There are several possible cases:
    // a. ranges[ix] is the range [c, c]
    // b. ranges[(insertion point)] contains the search key
    // c. ranges[(insertion point) + 1] contains the search key
    // d. ranges[(insertion point) - 1] contains the search key
    // e. No range contains the search key
    // Test each in turn, since we'll be getting rid of this method soon
    // anyway.

    // System.err
    // .printf("Searching %s for %c; index is %d\n", this, c, ix);

    // Case (a)
    if (ix >= 0) {
      long encodedRange = ranges[ix];
      char start = rangeStart(encodedRange);
      char end = rangeEnd(encodedRange);

      if (c >= start && c <= end) {
        if (debug)
          System.err.printf("(a) %s contains '%c'\n", this, c);

        return true;
      }
    } else {
      int insertionPoint = -(ix + 1);

      // Case (b)
      if (insertionPoint < numRanges) {
        long encodedRange = ranges[insertionPoint];
        char start = rangeStart(encodedRange);
        char end = rangeEnd(encodedRange);

        if (c >= start && c <= end) {
          if (debug)
            System.err.printf("(b) %s contains '%s'\n", this, makePrintable(c));
          return true;
        }
      }

      // Case (c)
      if (insertionPoint + 1 < numRanges) {
        long encodedRange = ranges[insertionPoint + 1];
        char start = rangeStart(encodedRange);
        char end = rangeEnd(encodedRange);

        if (c >= start && c <= end) {
          if (debug)
            System.err.printf("(c) %s contains '%s'\n", this, makePrintable(c));
          return true;
        }
      }

      // Case (d)
      if (insertionPoint - 1 >= 0) {
        long encodedRange = ranges[insertionPoint - 1];
        char start = rangeStart(encodedRange);
        char end = rangeEnd(encodedRange);

        if (c >= start && c <= end) {
          if (debug)
            System.err.printf("(d) %s contains '%s'\n", this, makePrintable(c));
          return true;
        }
      }
    }

    // Case (e)
    return false;
  }

  /**
   * Compute the number of characters in this set; only works properly when the set is in compact
   * form.
   */
  public int size() {
    int ret = 0;

    // Assuming that the ranges have been compacted, there should be no
    // overlap; so we just need to sum their sizes.
    for (int i = 0; i < numRanges; i++) {
      long encodedRange = ranges[i];
      ret += rangeEnd(encodedRange) - rangeStart(encodedRange) + 1;
    }
    return ret;
  }

  /** Make sure that this set's string representation is up to date. */
  private String stringRep() {
    if (null == setStr) {
      // Create the string in a char buffer.
      char[] tmp = new char[3 * numRanges];
      for (int i = 0; i < numRanges; i++) {
        long encodedRange = ranges[i];

        // Add a string of the form "a-z"
        tmp[3 * i] = rangeStart(encodedRange);
        tmp[3 * i + 1] = '-';
        tmp[3 * i + 2] = rangeEnd(encodedRange);
      }

      setStr = new String(tmp);
    }
    return setStr;
  }

  @Override
  public int hashCode() {

    int ret = stringRep().hashCode();
    // System.err.printf("%s has hashcode %d\n", this, ret);
    return ret;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof CharSet) {
      CharSet cobj = (CharSet) obj;
      return (this.stringRep().equals(cobj.stringRep()));
    } else {
      return false;
    }
  }

  /**
   * Create a character set out of all the single characters that the indicated regex matches.
   */
  public static CharSet fromRegex(String singleCharRegexStr, int flags) {

    boolean debug = false;

    Pair<String, Integer> key = new Pair<String, Integer>(singleCharRegexStr, flags);

    // Memoize to cut down on overhead.
    synchronized (cachedSets) {
      if (cachedSets.containsKey(key)) {
        return cachedSets.get(key);
      }
    }

    CharSet ret = new CharSet();

    if (debug) {
      System.err.printf("Finding chars " + "that match '%s' with flags 0x%x\n", singleCharRegexStr,
          flags);
    }

    Pattern singleCharRegex = Pattern.compile(singleCharRegexStr, flags);
    Matcher m = singleCharRegex.matcher(ALL_CHARS_STR);

    // Check every possible character to see whether it matches.
    // We do this with a preallocated string containing every possible
    // character.
    for (int c = 0; c < Character.MAX_VALUE; c++) {

      // We just want to see if there's a match at the indicated position
      // in the magic string.
      m.region(c, c + 1);

      if (m.matches()) {
        // Found a match; add the character to the returned set as a
        // singleton range, but do *NOT* compact the set yet.

        // System.err.printf(" Char %d matches\n", (int) c);
        ret.addRange((char) c, (char) c);
      }

    }

    // Now that we've added all the characters, we can compact the newly
    // created set.
    ret.compactRanges();

    if (debug) {
      System.err.printf("--> %d chars (in %d ranges) match\n", ret.size(), ret.numRanges);
    }

    // Store the mapping so we don't have to recompute it.
    synchronized (cachedSets) {
      cachedSets.put(key, ret);
    }

    return ret;
  }

  /**
   * Cached mapping from single-char regex to character set; used for memoization in
   * {@link #fromRegex(String)}
   */
  private static HashMap<Pair<String, Integer>, CharSet> cachedSets =
      new HashMap<Pair<String, Integer>, CharSet>();

  /**
   * Clear out the cached regex substring -- CharSet mappings. Frees memory, but forces
   * recomputation later on.
   */
  public static void clearCachedSets() {
    synchronized (cachedSets) {
      cachedSets.clear();
    }
  }

  @Override
  public String toString() {

    if (numRanges > 30) {
      // Don't print out really big sets.
      return String.format("CharSet with %d ranges, %d elements", numRanges, size());
    } else {
      return toDetailedString();
    }
  }

  private String toDetailedString() {
    return toDetailedString(ranges, numRanges);
  }

  private static String toDetailedString(long[] ranges, int numRanges) {
    // Generate a string in the form "[a-zA-Z]"
    StringBuilder sb = new StringBuilder();
    sb.append('[');

    for (int i = 0; i < numRanges; i++) {
      long encodedRange = ranges[i];
      sb.append(makePrintable(rangeStart(encodedRange)));
      sb.append('-');
      sb.append(makePrintable(rangeEnd(encodedRange)));
    }

    sb.append(']');

    return sb.toString();
  }

  public static String makePrintable(char ch) {
    if ('\n' == ch) {
      return "\\n";
    } else if ('\r' == ch) {
      return "\\r";
    } else if ('\t' == ch) {
      return "\\t";
    } else if (Character.isWhitespace(ch) || Character.isISOControl(ch)) {
      // Control character; escape it with unicode escapes.
      return String.format("\\u%04x", (int) ch);
    } else {
      return String.valueOf(ch);

    }
  }

  /**
   * Finds all the points on the number line where this character set switches from containing a
   * range of characters to not containing a range.
   * 
   * @param dest list to which to append results
   */
  public void getChangePoints(CharList dest) {
    for (int i = 0; i < numRanges; i++) {
      long encodedRange = ranges[i];
      char rangeStart = rangeStart(encodedRange);
      char rangeEnd = rangeEnd(encodedRange);

      dest.add(rangeStart);

      // Add (1 past end of range), unless it would overflow.
      if (rangeEnd <= 65535) {
        dest.add((char) (rangeEnd + 1));
      }
    }
  }
}
