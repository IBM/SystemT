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

import java.io.IOException;
import java.util.ArrayList;

/**
 * Class for escaping and de-escaping strings according to a set of configurable parameters. Also
 * supports removal of comments.
 */
public class Escaper {

  /** The characters that we escape, not including the escape character. */
  private char[] specialChars = new char[0];

  /** The characters that we use to replace the special characters. */
  private char[] replacements = new char[0];

  private static final char ESCAPE_CHAR = '\\';

  /** Character that indicates the start of a comment. */
  private final char COMMENT_CHAR = '#';

  /** Should we strip out comments when de-escaping strings? */
  private boolean removeComments = false;

  public void setRemoveComments(boolean removeComments) {
    this.removeComments = removeComments;
  }

  public Escaper(char[] specialChars, char[] replacements) {
    this.specialChars = specialChars;
    this.replacements = replacements;
  }

  /**
   * Escape the 'special' characters in a string.
   */
  public String escapeStr(String in) throws IOException {
    if (null == in) {
      // SPECIAL CASE: Empty input
      return "";
    }

    StringBuilder sb = new StringBuilder();

    // Convert to an array for faster traversal.
    char[] chars = in.toCharArray();

    for (int i = 0; i < chars.length; i++) {
      char cur = chars[i];

      // Check for the special characters.
      boolean foundMatch = false;
      int matchIndex = -1;

      if (ESCAPE_CHAR == cur) {
        // Escape the escape character...
        foundMatch = true;
        matchIndex = -1;
      }

      // We'll assume that the list is reasonably short.
      for (int j = 0; false == foundMatch && j < specialChars.length; j++) {
        if (specialChars[j] == cur) {
          foundMatch = true;
          matchIndex = j;
        }
      }

      if (foundMatch) {
        char replacement;

        // We may want to put a different character after the
        // escape.
        if (-1 == matchIndex) {
          replacement = ESCAPE_CHAR;
        } else {
          replacement = replacements[matchIndex];
        }

        sb.append(ESCAPE_CHAR);
        sb.append(replacement);
      } else {
        sb.append(cur);
      }

    }

    return sb.toString();
  }

  /**
   * Replace escaped characters in a string with the original unescaped versions. Also strips off
   * double quotes if necessary.
   */
  public String deEscapeStr(String in) throws IOException {
    StringBuilder sb = new StringBuilder();

    // Convert to an array for faster traversal.
    char[] chars = in.toCharArray();

    for (int i = 0; i < chars.length; i++) {
      char cur = chars[i];

      if (removeComments && COMMENT_CHAR == cur) {
        // SPECIAL CASE: Found a comment; skip the rest of the string.
        return sb.toString();
        // END SPECIAL CASE
      }

      if (ESCAPE_CHAR != cur) {
        // Character is not the start of an escape sequence; pass it
        // through.
        sb.append(cur);

      } else {
        // Found an escape sequence.
        if (i == chars.length - 1) {
          throw new IOException("Reached end of string" + " while de-escaping");
        }

        char next = chars[i + 1];

        // System.err.printf("Cur is '%c'; next is '%c'\n", cur, next);

        if (ESCAPE_CHAR == next) {
          // System.err.printf("Got escaped escape char.\n");

          // The escape character is always escaped with itself.
          sb.append(ESCAPE_CHAR);
          i++;
        } else {
          // Figure out what this character originally was.
          boolean foundMatch = false;
          int matchIx = -1;
          for (int j = 0; false == foundMatch && j < replacements.length; j++) {
            if (replacements[j] == next) {
              matchIx = j;
              foundMatch = true;
            }
          }

          if (false == foundMatch) {
            throw new IllegalArgumentException(
                String.format("Error escaping '%s': " + "Don't understand escape sequence '%c%c'",
                    in, ESCAPE_CHAR, next));
          }

          sb.append(specialChars[matchIx]);
          i++;
        }

      }
    }

    return sb.toString();
  }
}
