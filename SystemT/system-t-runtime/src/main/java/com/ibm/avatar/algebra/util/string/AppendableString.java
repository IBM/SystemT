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
 * A string that can have additional characters added to its right-hand side. Useful for creating
 * many ReusableStrings with a single buffer or for reading a string directly from a stream. Can
 * also be reset to length 0.
 */
public class AppendableString implements CharSequence {

  private char[] buf;

  /** Number of characters in the buffer that are used. */
  private int bufUsed;

  public void append(char c) {
    // Make room.
    if (bufUsed >= buf.length) {
      char[] newBuf = new char[buf.length * 2];
      System.arraycopy(buf, 0, newBuf, 0, bufUsed);
      buf = newBuf;
    }

    buf[bufUsed] = c;
    bufUsed++;
  }

  public void append(CharSequence cs) {
    for (int i = 0; i < cs.length(); i++) {
      append(cs.charAt(i));
    }
  }

  @Override
  public char charAt(int i) {
    if (i < 0 || i >= bufUsed) {
      throw new RuntimeException(
          String.format("Tried to fetch char %d of a %d-char string", i, bufUsed));
    }
    return buf[i];
  }

  @Override
  public int length() {
    return bufUsed;
  }

  @Override
  public CharSequence subSequence(int begin, int end) {
    ReusableString ret = new ReusableString();
    ret.setToSubstr(this, begin, end);
    return ret;
  }

  /**
   * Forget the current contents of the string and set the length back to zero. Does *not* reset the
   * internal buffer.
   */
  public void reset() {
    bufUsed = 0;
  }

  @Override
  public int hashCode() {
    return StringUtils.strHash(this);
  }

  /**
   * Character comparison.
   */
  @Override
  public boolean equals(Object o) {
    if (false == (o instanceof CharSequence)) {
      return false;
    }

    CharSequence other = (CharSequence) o;
    return StringUtils.strEq(this, other);

  }

  public AppendableString() {
    buf = new char[1];
    bufUsed = 0;
  }

  /**
   * Create a new AppendableString with the same contents, and with its internal buffer set to
   * exactly the right length.
   * 
   * @param src string to copy
   */
  public AppendableString(CharSequence src) {
    buf = new char[src.length()];
    bufUsed = src.length();

    for (int i = 0; i < buf.length; i++) {
      buf[i] = src.charAt(i);
    }
  }

  @Override
  public String toString() {
    return new String(buf, 0, bufUsed);
  }

}
