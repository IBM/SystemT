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

/**
 * Static functions for escaping and de-escaping strings for embedding in a Hadoop-format CSV file
 * or a sequence file. Escaped strings will have the following substitutions in place:
 * <ul>
 * <li>'\' => '\\'
 * <li>newline => '\n'
 * <li>return => '\r'
 * <li>',' => '\c'
 * <li>'"' (double quote) => '\d'
 * <li>' (single quote) => '\s'
 * </ul>
 */
public class StringEscaper {

  /**
   * Escape the 'special' characters in a string.
   */
  public static String escapeStr(String in) throws IOException {
    if (null == in) {
      // SPECIAL CASE: Empty input
      return "";
    }

    StringBuilder sb = new StringBuilder();

    // Convert to an array for faster traversal.
    char[] chars = in.toCharArray();

    for (int i = 0; i < chars.length; i++) {
      char cur = chars[i];

      switch (cur) {
        case '\n':
          sb.append("\\n");
          break;
        case '\r':
          sb.append("\\r");
          break;

        case '\t':
          sb.append("\\t");
          break;

        case ',':
          sb.append("\\c");
          break;

        case '\\':
          sb.append("\\\\");
          break;

        case '"':
          sb.append("\\d");
          break;

        case '\'':
          sb.append("\\s");
          break;

        default:
          if (Character.isISOControl(cur)) {
            sb.append(String.format("\\u%04o", (int) cur));
          } else {
            sb.append(cur);
          }
      }
    }

    return sb.toString();
  }

  /**
   * Replace escaped characters in a string with the original unescaped versions. Also strips off
   * double quotes if necessary.
   */
  public static String deEscapeStr(String in) throws IOException {
    if (0 == in.length()) {
      // SPECIAL CASE: Empty string.
      return in;
    }

    if ('"' == in.charAt(0)) {
      // Detected double quotes. Strip them off and continue.
      char lastChar = in.charAt(in.length() - 1);
      if ('"' != lastChar) {
        throw new IOException(
            "String starts with double quote " + "but does not end with double quote.");
      }
      return deEscapeInternal(in.substring(1, in.length() - 1));
    } else {
      return deEscapeInternal(in);
    }
  }

  /**
   * Implementation of deEscapeStr
   * 
   * @param in input string, with any double quotes removed
   * @return string with escapes removed
   * @throws IOException
   */
  private static String deEscapeInternal(String in) throws IOException {
    StringBuilder sb = new StringBuilder();

    // Convert to an array for faster traversal.
    char[] chars = in.toCharArray();

    for (int i = 0; i < chars.length; i++) {
      char cur = chars[i];

      if ('\\' != cur) {
        // Character is not the start of an escape sequence; pass it
        // through.
        sb.append(cur);

      } else {
        // Found an escape sequence.
        if (i == chars.length - 1) {
          throw new IOException("Reached end of string" + " while de-escaping");
        }

        char next = chars[i + 1];

        switch (next) {
          case 'n':
            sb.append('\n');
            break;

          case 'r':
            sb.append('\r');
            break;

          case 't':
            sb.append('\t');
            break;

          // Commas are escaped as \c
          case 'c':
            sb.append(',');
            break;

          // Need to escape the character we use to escape other chars
          case '\\':
            sb.append('\\');
            break;

          // Single quote
          case 's':
            sb.append('\'');
            break;

          // Double quote
          case 'd':
            sb.append('"');
            break;

          default:
            throw new IllegalArgumentException("Don't understand escape sequence '\\" + next + "'");
        }

        // Skip the second part of the escape sequence.
        i++;

      }
    }

    return sb.toString();
  }
}
