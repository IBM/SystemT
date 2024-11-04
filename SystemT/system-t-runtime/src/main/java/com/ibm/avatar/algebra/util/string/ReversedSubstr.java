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

public class ReversedSubstr implements CharSequence {

  /** The original, "forwards" string. */
  private final CharSequence orig;

  /** Offset into {@link #orig} where our substring starts. */
  private final int beginOff;

  private final int endOff;

  /**
   * Main constructor. Takes the specified range of characters in the original string and reverses
   * them (without making a copy).
   * 
   * @param str original (forwards) string
   * @param begin offset into the <b>original</b> string
   * @param end offset into the <b>original</b> string
   */
  public ReversedSubstr(CharSequence str, int begin, int end) {
    this.orig = str;
    this.beginOff = begin;
    this.endOff = end;
  }

  @Override
  public char charAt(int index) {
    boundsCheck(index, false);
    return orig.charAt(endOff - index - 1);
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
    return new ReversedSubstr(orig, beginOff + end, beginOff + start);
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
    sb.append(this);
    return sb.toString();
  }

}
