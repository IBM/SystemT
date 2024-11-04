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
 * A recyclable version of java.lang.String. Currently created by creating a substring of a given
 * string.
 */
public class ReusableString implements CharSequence {

  private static final int NOT_A_HASH_CODE = -1;

  /**
   * Enable extra sanity checks that we wouldn't normally want in the inner loop of functions.
   */
  private static final boolean paranoid = false;

  /**
   * The actual "base" string.
   */
  CharSequence src;

  int beginOff;

  int endOff;

  // Cached hash code
  int hashCode = NOT_A_HASH_CODE;

  private void validate() {
    if (beginOff > endOff) {
      throw new IllegalArgumentException(String.format("%d > %d", beginOff, endOff));
    }
  }

  /**
   * Set this string to be the indicated substring of the indicated original string.
   */
  public void setToSubstr(CharSequence src, int begin, int end) {
    if (begin > end) {
      throw new IllegalArgumentException(String.format("%d > %d", begin, end));
    }

    if (src instanceof ReusableString) {
      // Short-circuit pointers if possible.
      ReusableString rsrc = (ReusableString) src;
      this.src = rsrc.src;
      this.beginOff = rsrc.beginOff + begin;
      this.endOff = rsrc.endOff + end;
    } else {
      this.src = src;
      this.beginOff = begin;
      this.endOff = end;
    }

    if (paranoid) {
      validate();
    }
  }

  @Override
  public char charAt(int index) {
    if (paranoid) {
      validate();
    }

    if (index < 0 || index > endOff - beginOff) {
      throw new IndexOutOfBoundsException(
          String.format("Tried to get char %d from ReusableString of length %d (%d - %d)\n", index,
              endOff - beginOff, endOff, beginOff));
    }
    return src.charAt(index + beginOff);
  }

  @Override
  public int length() {
    if (paranoid) {
      validate();
    }

    return endOff - beginOff;
  }

  @Override
  public CharSequence subSequence(int start, int end) {
    throw new RuntimeException("Not implemented");
  }

  public boolean equalsIgnoreCase(CharSequence o) {
    if (paranoid) {
      validate();
    }

    if (o.length() != this.length()) {
      return false;
    }

    int len = length();

    for (int i = 0; i < len; i++) {
      char c = charAt(i);
      char otherC = o.charAt(i);

      if (Character.toLowerCase(c) != Character.toLowerCase(otherC)) {
        // System.err.printf("%s != %s\n", this, o);
        return false;
      }
    }

    return true;
  }

  /** Duplicate of String.contentEquals() */
  public boolean contentEquals(CharSequence o) {
    if (paranoid) {
      validate();
    }

    if (o.length() != this.length()) {
      return false;
    }

    int len = length();

    for (int i = 0; i < len; i++) {
      char c = charAt(i);
      char otherC = o.charAt(i);

      if (c != otherC) {
        return false;
      }
    }

    return true;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null)
      return false;
    if (obj instanceof CharSequence) {
      return contentEquals((CharSequence) obj);
    }

    throw new RuntimeException("Don't know how to evaluate equality with " + obj.toString());
  }

  /** @return the same hash code that an actual Java String would return. */
  @Override
  public int hashCode() {
    if (NOT_A_HASH_CODE == hashCode) {
      hashCode = StringUtils.strHash(this);
    }
    return hashCode;
  }

  @Override
  public String toString() {
    if (paranoid) {
      validate();
    }

    // Java's String class doesn't have a constructor that takes a
    // CharSequence, believe it or not.
    StringBuilder sb = new StringBuilder();
    sb.append(src, beginOff, endOff);
    return sb.toString();
  }

  /**
   * @see String#indexOf(int)
   * @param c character to search for
   * @return location of the first instance of the character
   */
  public int indexOf(int c) {
    return indexOf(c, 0);
  }

  /**
   * @see String#indexOf(int)
   * @param c character to search for
   * @param fromIndex where to start searching
   * @return location of the first instance of the character
   */
  private int indexOf(int c, int fromIndex) {
    if (src instanceof String) {
      // Use String.indexOf() if possible; it's much faster.
      String srcStr = (String) src;
      int srcOff = srcStr.indexOf(c, fromIndex + beginOff);
      if (srcOff >= endOff) {
        // There's a match, but it's past the end of the string.
        return -1;
      } else {
        return srcOff - beginOff;
        // return srcOff;
      }
    } else {
      int len = length();
      for (int i = fromIndex; i < len; i++) {
        if (charAt(i) == c) {
          return i;
        }
      }
    }

    // If we get here, we didn't find the character.
    return -1;
  }
}
