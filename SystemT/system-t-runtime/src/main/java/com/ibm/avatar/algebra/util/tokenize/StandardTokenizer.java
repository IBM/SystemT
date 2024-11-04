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

import java.util.StringTokenizer;
import java.util.TreeSet;

import com.ibm.avatar.algebra.util.lang.LangCode;

/**
 * Simple internal tokenizer, used for dictionary matching and token distance predicates. Performs a
 * basic whitespace tokenization that removes whitespace but keeps punctuation characters as
 * separate tokens; similar to {@link StringTokenizer} .
 */
public class StandardTokenizer extends Tokenizer {

  /** This class is intended to be instantiated only by its parent class. */
  public StandardTokenizer() {

  }

  /** Singleton instance of this class. */
  // public static Tokenizer singleton = new FastTokenizer();
  // Masks for different kinds of characters.
  private static final byte LETTER_OR_DIGIT_MASK = 0x1;

  private static final byte WHITESPACE_MASK = 0x2;

  /**
   * Table of character class information, used to speed up various calls to static methods in
   * java.lang.Character. Each entry is a mask made up of elements like
   * {@link #LETTER_OR_DIGIT_MASK}.
   */
  private static final byte[] CHAR_CLASS_TAB = buildCharClassTab();

  private static byte[] buildCharClassTab() {
    byte[] ret = new byte[Character.MAX_VALUE];
    for (char c = 0; c < ret.length; c++) {

      // Do all the tests in turn and OR together the results.
      byte mask = 0x0;

      if (Character.isLetterOrDigit(c)) {
        mask |= LETTER_OR_DIGIT_MASK;
      }

      if (Character.isWhitespace(c)) {
        mask |= WHITESPACE_MASK;
      }

      ret[c] = mask;
    }

    return ret;
  }

  private boolean isWhitespace(byte mask) {
    return 0x0 == (mask & WHITESPACE_MASK);
  }

  private boolean isLetterOrDigit(byte mask) {
    return 0x0 == (mask & LETTER_OR_DIGIT_MASK);
  }

  // private boolean isLetterOrDigit(char c) {
  // return 0x0 == (CHAR_CLASS_TAB[c] & LETTER_OR_DIGIT_MASK);
  // }

  /**
   * Older, slower, simpler version of {@link #tokenizeStr(CharSequence, int, int, OffsetsList)}.
   */
  @Deprecated
  protected void oldTokenizeStr(CharSequence str, int startOff, int maxTok,
      BaseOffsetsList output) {

    output.reset();

    CharSequence inStr = str;

    int pos = startOff;

    // Memoize the call to length(). Believe it or not, this is a measurable
    // overhead!
    int len = inStr.length();

    // March through the string.
    while (pos < len && output.numUsed < maxTok) {

      // At this point, <pos> is always at the beginning of the next
      // token.
      if (Character.isLetterOrDigit(inStr.charAt(pos))) {
        // Character is alphanumeric. Find all the contiguous characters
        // that make up the token.
        int begin = pos;
        while (pos < len && Character.isLetterOrDigit(inStr.charAt(pos))) {
          pos++;
        }

        output.addEntry(begin, pos);

      } else if (Character.isWhitespace(inStr.charAt(pos))) {
        // Ignore whitespace.
        pos++;
      } else {
        // System.err.printf("Got punct: '%s'\n", str.charAt(pos));
        // Assume that anything other than letters/digits and whitespace
        // is punctuation and turn it into a token.
        output.addEntry(pos, pos + 1);

        pos++;
      }
    }

  }

  /**
   * Hand-tuned version of tokenizeStr(). Significantly faster on Sun Java; slightly faster on IBM
   * Java. Currently ignores the language code.
   */
  @Override
  public void tokenizeStr(CharSequence str, LangCode language, BaseOffsetsList output) {

    final boolean debug = false;

    if (debug) {
      // Add a delimiter between calls.
      System.err.printf("-----\n");
    }

    output.reset();

    CharSequence inStr = str;

    // Memoize the call to length(). Believe it or not, this is a measurable
    // overhead!
    final int len = inStr.length();

    int pos = 0;
    byte mask = getMaskAtPos(inStr, len, pos);

    // Skip any whitespace and single-character tokens at the beginning.
    while (pos < len && (isLetterOrDigit(mask))) {
      if (isWhitespace(mask)) {
        // Single-character token
        if (debug) {
          System.err.printf("Adding single-char token (1) " + "at [%d,%d]: '%s'\n", pos, pos + 1,
              inStr.subSequence(pos, pos + 1));
        }
        output.addEntry(pos, pos + 1);
      }

      pos++;
      mask = getMaskAtPos(inStr, len, pos);
    }

    // System.err.printf("Skipped whitespace '%s'\n",
    // inStr.subSequence(startOff, pos));

    int begin = pos;
    while (pos < len) {
      // mask = CHAR_CLASS_TAB[inStr.charAt(pos)];

      // At this point, we're at the beginning of a token. Search for the
      // end.
      if (isLetterOrDigit(mask)) {
        // Found the end of the token; create an entry.
        if (debug) {
          System.err.printf("Adding token at [%d,%d]: '%s'\n", begin, pos,
              inStr.subSequence(begin, pos));
        }
        output.addEntry(begin, pos);

        // Skip whitespace and single-character tokens to get to the
        // beginning of the next token.
        while (pos < len && (isLetterOrDigit(mask))) {
          if (isWhitespace(mask)) {
            // Single-character token
            if (debug) {
              System.err.printf("Adding single-char token (2) " + "at [%d,%d]: '%s'\n", pos,
                  pos + 1, inStr.subSequence(pos, pos + 1));
            }
            output.addEntry(pos, pos + 1);
          }

          pos++;
          mask = getMaskAtPos(inStr, len, pos);
        }

        begin = pos;
      }
      pos++;
      mask = getMaskAtPos(inStr, len, pos);
    }

    // If we reached the end of the string, add the last token.
    if (pos == len && pos != begin) {
      if (debug) {
        System.err.printf("Adding last token at [%d,%d]: '%s'\n", begin, pos,
            inStr.subSequence(begin, pos));
      }
      output.addEntry(begin, pos);
    }
  }

  private byte getMaskAtPos(CharSequence inStr, final int len, int pos) {
    return (pos < len) ? CHAR_CLASS_TAB[inStr.charAt(pos)] : 0x0;
  }

  @Override
  public boolean supportsPOSTagging() {
    return false;
  }

  @Override
  public TreeSet<Integer> decodePOSSpec(String posSpec, LangCode language) {
    throw new RuntimeException("Standard tokenizer does not tag parts of speech");
  }

  @Override
  public CharSequence posCodeToString(int code, LangCode language) {
    throw new RuntimeException("Standard tokenizer does not tag parts of speech");
  }

  /**
   * StandardTokenizer does not support lemmatization
   * 
   */
  @Override
  public boolean supportLemmatization() {
    return false;
  }

  @Override
  public String getName() {
    return "STANDARD";
  }

}
