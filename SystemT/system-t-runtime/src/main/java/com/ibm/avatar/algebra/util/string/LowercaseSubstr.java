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
package com.ibm.avatar.algebra.util.string;

/**
 * Class for creating a zero-copy lowercase substring. Changes case of individual characters on
 * demand.
 */
public class LowercaseSubstr implements CharSequence {

  /** The original, "forwards" string. */
  private CharSequence orig;

  /** Offset into {@link #orig} where our substring starts. */
  private int beginOff;

  private int endOff;

  private static final int LOOKUP_TABLE_SZ = 128;

  /** Lookup table for {@link #myToLowerCase(char)} */
  private static final char[] LOOKUP_TABLE = genLookupTable();

  private static char[] genLookupTable() {
    char[] ret = new char[LOOKUP_TABLE_SZ];
    for (char c = 0; c < LOOKUP_TABLE_SZ; c++) {
      ret[c] = Character.toLowerCase(c);
    }
    return ret;
  }

  /**
   * A super-fast version of toLowerCase, for languages that use Latin1.
   */
  private static char myToLowerCase(char c) {
    if (c < LOOKUP_TABLE_SZ) {
      // Fast path
      return LOOKUP_TABLE[c];
    }
    return Character.toLowerCase(c);
  }

  /**
   * Main constructor. Takes the specified range of characters in the original string and lowercases
   * them (without making a copy).
   * 
   * @param str original string
   * @param begin offset into the <b>original</b> string
   * @param end offset into the <b>original</b> string
   */
  public LowercaseSubstr(CharSequence str, int begin, int end) {
    this.orig = str;
    this.beginOff = begin;
    this.endOff = end;
    initCheck();
  }

  /**
   * Create a lowercase version of an entire string
   * 
   * @param str the original string
   */
  public LowercaseSubstr(CharSequence str) {
    this(str, 0, str.length());
  }

  /**
   * Create an empty substring; use {@link #reinit(CharSequence, int, int)} to point the resulting
   * object at the appropriate location.
   */
  public LowercaseSubstr() {
    this.orig = "";
    this.beginOff = 0;
    this.endOff = 0;
  }

  /** Check string parameters on (re)initialization. */
  private void initCheck() {
    if (beginOff < 0 || beginOff > orig.length()) {
      throw new RuntimeException(
          String.format("Begin offset of %d invalid (need 0 to %d); end is %d\n", beginOff,
              orig.length(), endOff));
    }
    if (endOff < 0 || endOff > orig.length()) {
      throw new RuntimeException(
          String.format("End offset of %d invalid (need 0 to %d)\n", endOff, orig.length()));
    }
  }

  /** Reuse this object on a different substring. */
  public void reinit(CharSequence str, int begin, int end) {
    this.orig = str;
    this.beginOff = begin;
    this.endOff = end;
    initCheck();
  }

  @Override
  public char charAt(int index) {
    boundsCheck(index, false);
    // return Character.toLowerCase(orig.charAt(beginOff + index));
    return myToLowerCase(orig.charAt(beginOff + index));
  }

  @Override
  public int length() {
    return endOff - beginOff;
  }

  @Override
  public CharSequence subSequence(int start, int end) {
    boundsCheck(start, false);
    boundsCheck(end, true);

    // Don't fall back on java.lang.String
    return new LowercaseSubstr(orig, beginOff + start, beginOff + end);
  }

  /**
   * Validate an index into the string.
   * 
   * @param index the index to check
   * @param intervalEnd true if this is an interval end (e.g. one past the end of a range)
   */
  private void boundsCheck(int index, boolean intervalEnd) {
    int len = length();
    if (index < 0 || index > len || (false == intervalEnd && index == len)) {
      throw new IndexOutOfBoundsException(
          String.format("Index %d out of range (expected 0 to %d)", index, len));
    }
  }

  /** This implementation is intended for debugging purposes only. */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < length(); i++) {
      sb.append(charAt(i));
    }
    return sb.toString();
  }

  /** Duplicates semantics of String.equals() */
  @Override
  public boolean equals(Object obj) {

    if (false == (obj instanceof CharSequence)) {
      return false;
    }

    return StringUtils.strEq(this, (CharSequence) obj);
  }

  /**
   * Returns the same hash code as the built-in String class (at least according to the JavaDoc for
   * {@link java.lang.String}).
   */
  @Override
  public int hashCode() {

    return StringUtils.strHash(this);

    // From JavaDoc for java.lang.String:
    // The hash code for a String object is computed as
    // s[0]*31^(n-1) + s[1]*31^(n-2) + ... + s[n-1]
    // using int arithmetic, where s[i] is the ith character of the string,
    // n is the length of the string, and ^ indicates exponentiation.

    // int accum = 0;
    //
    // for (int i = 0; i < length(); i++) {
    // accum *= 31;
    // accum += charAt(i);
    // }
    //
    // return accum;
  }

}
