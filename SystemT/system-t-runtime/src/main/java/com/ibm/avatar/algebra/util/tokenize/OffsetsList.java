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

import com.ibm.avatar.algebra.util.string.StringUtils;

/**
 * Reusable container for storing a list of spans within a string. Keeps track of a variable number
 * of (begin, end) pairs. Also stores an optional numerical "index" for each span (for use with the
 * DictImpl class). Currently, there are two implementations: BaseOffsetsList holds the full set of
 * offsets for the text, and DerivedOffsetsList is used as a standin for representing offsets within
 * a substring of the text.
 */
public abstract class OffsetsList {

  /**
   * Auxiliary payload, used for state that you want to store along with the table.
   */
  private Object auxData = null;

  /**
   * Size of the list, in number of tokens; this number must be provided by subclasses. We keep this
   * field in the superclass because Sun Java performs poorly if size() is a virtual method.
   */
  private int size = -1;

  /** Subclasses MUST call this method at the end of initialization. */
  protected void setSize(int size) {
    this.size = size;
  }

  public Object getAuxData() {
    return auxData;
  }

  public void setAuxData(Object auxData) {
    this.auxData = auxData;
  }

  /**
   * @return number of entries currently in the offsets list (as opposed to the capacity of any
   *         internal arrays)
   */
  public final int size() {
    return size;
  }

  /**
   * @param i a token/match index
   * @return begin offset of the indicated token or match
   */
  public abstract int begin(int i);

  /**
   * @param i a token/match index
   * @return end offset of the indicated token or match
   */
  public abstract int end(int i);

  /**
   * @param i a match index
   * @return "index" value for the indicated match; this field is used to store the dictionary index
   *         for dictionary matches and the part of speech tag index for part of speech tags
   */
  public abstract int index(int i);

  /**
   * @param offset a character offset into the original string
   * @return if the offset is within a token, the index of that token; otherwise, the index of the
   *         first token after the indicated offset. If the offset is after the end of the last
   *         token, returns 1 + index of last token. If there are no tokens, returns -1.
   */
  public abstract int nextBeginIx(int offset);

  /**
   * @param off a character offset into the original string
   * @return index of the last token that starts before the indicated location. If the indicated
   *         location is before the start of the first token, returns -1.
   */
  public abstract int prevBeginIx(int off);

  /**
   * @param target target string
   * @return a pretty-printed string for the indicated tokens.
   */
  public String toString(CharSequence target) {
    StringBuilder sb = new StringBuilder();

    sb.append('[');

    for (int i = 0; i < size(); i++) {

      int begin = begin(i);
      int end = end(i);

      sb.append(String.format("%d-%d: '%s'", begin, end,
          end <= target.length() ? StringUtils.escapeForPrinting(target.subSequence(begin, end))
              : "Invalid token!"));

      if (i < size() - 1) {
        sb.append(", ");
      }
    }

    sb.append(']');

    return sb.toString();
  }

  /**
   * @param tokenIndex
   * @return the lemma of the token at the given index
   */
  public abstract String getLemma(int tokenIndex);

}
