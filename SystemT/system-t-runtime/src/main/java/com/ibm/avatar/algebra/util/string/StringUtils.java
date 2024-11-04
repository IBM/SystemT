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

import java.io.CharArrayWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import com.ibm.avatar.aql.planner.AnnotPlan;

/**
 * Various static methods for dealing with strings in way that the Java library designers didn't
 * feel like supporting.
 */
public class StringUtils {

  /**
   * Trim whitespace off a string; trims some whitespace characters that
   * {@link java.lang.String#trim()} misses.
   */
  public static String trim(String in) {
    // Start with the default.
    int begin = 0;
    while (begin < in.length() && isReallyWhitespace(in.charAt(begin))) {
      begin++;
    }

    if (in.length() == begin) {
      return "";
    }

    int end = in.length();
    while (end > 0 && isReallyWhitespace(in.charAt(end - 1))) {
      end--;
    }

    if (0 == begin && in.length() == end) {
      return in;
    } else {
      return in.substring(begin, end);
    }
  }

  private static boolean isReallyWhitespace(char c) {
    if (Character.isWhitespace(c)) {
      return true;
    } else if (0xfeff == (int) c) {
      // Unicode char 0xfeff: ZERO WIDTH NO-BREAK SPACE
      return true;
    } else if (0x00a0 == (int) c) {
      // Unicode char 0x00a0: NO-BREAK SPACE
      return true;
    } else {
      return false;
    }
  }

  /**
   * Escape a string for printing to the screen.
   */
  public static String escapeForPrinting(CharSequence in) {
    if (null == in) {
      // SPECIAL CASE: Empty input
      return "";
    }

    StringBuilder sb = new StringBuilder();

    // Convert to an array for faster traversal.
    // char[] chars = in.toCharArray();

    // for (int i = 0; i < chars.length; i++) {
    for (int i = 0; i < in.length(); i++) {
      // char cur = chars[i];
      char cur = in.charAt(i);

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

        case '\\':
          sb.append("\\\\");
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
   * Escape a string for using it as a AOG view name.
   */
  public static String escapeForAOG(CharSequence in) {
    if (null == in) {
      // SPECIAL CASE: Empty input
      return "";
    }

    StringBuilder sb = new StringBuilder();

    // Convert to an array for faster traversal.
    // char[] chars = in.toCharArray();

    // for (int i = 0; i < chars.length; i++) {
    for (int i = 0; i < in.length(); i++) {
      // char cur = chars[i];
      char cur = in.charAt(i);

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

        case '\\':
          sb.append("\\\\");
          break;

        case '\'':
          sb.append("\\'");
          break;

        case '\"':
          sb.append("\\\"");
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
   * Shorten a string *and* escape it for printing to the screen.
   */
  public static CharSequence shortenForPrinting(CharSequence in, int maxlen) {
    return shorten(escapeForPrinting(in), maxlen, true);
  }

  /**
   * @param src a string, possibly containing Unicode characters.
   * @return the original string, with any non-ASCII characters escaped
   */
  public static String escapeUnicode(String in) {
    if (null == in) {
      // SPECIAL CASE: Empty input
      return "";
    }

    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < in.length(); i++) {
      // char cur = chars[i];
      char cur = in.charAt(i);

      if (cur > 127) {
        sb.append(String.format("\\u%04x", (int) cur));
      } else {
        sb.append(cur);
      }
    }

    return sb.toString();
  }

  /**
   * Removes the quotes from a quoted string as returned by a parser.
   * 
   * @param quotechar
   * @param str input string, surrounded by quotes
   * @return the string, with quotes removed and any escaped quotes inside the string de-escaped
   */
  public static final String dequoteStr(char quotechar, String str) {
    if (str.charAt(0) != quotechar || str.charAt(str.length() - 1) != quotechar) {
      throw new IllegalArgumentException("Can't dequote string '" + str + "'");
    }

    StringBuilder sb = new StringBuilder();

    final char ESCAPE = '\\';

    // Laura: START BLOCK REPLACE
    /*
     * for (int pos = 1; pos < str.length() - 1; pos++) { if (str.charAt(pos) == ESCAPE &&
     * str.charAt(pos + 1) == quotechar && pos + 1 != str.length() - 1) { // When we find ESCAPE
     * followed by the quote char, skip the // escape; the quote character will be passed through.
     * if (str.length() - 2 == pos) { throw new IllegalArgumentException(
     * "Escape character at end of string"); } } else { // All other characters just get passed
     * through. sb.append(str.charAt(pos)); } }
     */
    // Laura: END BLOCK REPLACE

    // remove the quotes from begin and end of the input string
    str = str.substring(1, str.length() - 1);

    int len = str.length();

    // input string of length 0
    if (len == 0)
      return str;

    // input string of length > 0
    int pos = 0;

    // Walk through the string from start to end, removing escapes.
    while (pos < len) {
      if (str.charAt(pos) == ESCAPE) {
        if (pos < len - 1) {
          // Before the last character
          if (str.charAt(pos + 1) == quotechar) {
            // ESCAPE char followed by the quote char;
            // Skip the escape, but keep the quote
            sb.append(quotechar);
            pos += 2;
          } else {
            // The escape char is not followed by quote, just pass it through
            // Also pass through the next character
            sb.append(str.charAt(pos));
            sb.append(str.charAt(pos + 1));
            pos += 2;
          }
        } else {
          throw new IllegalArgumentException("Escape character at end of string");
        }
      } else {
        // Character just passes through
        sb.append(str.charAt(pos));
        pos++;
      }
    }

    return sb.toString();
  }

  /**
   * De-escapes escaped characters from a string as returned by a parser.
   * 
   * @param str input string
   * @return the string, with any escaped characters inside the string de-escaped
   */
  public static final String deescapeStr(String str) {

    StringBuilder sb = new StringBuilder();

    final char ESCAPE = '\\';
    int len = str.length();

    // input string of length 0
    if (len == 0)
      return str;

    // input string of length > 0
    int pos = 0;

    while (pos < len) {
      if (str.charAt(pos) == ESCAPE) {

        if (pos < len - 1) {

          switch (str.charAt(pos + 1)) {
            // Handle built-in escape codes
            case 'n':
              sb.append('\n');
              pos += 2;
              break;

            case 'r':
              sb.append('\r');
              pos += 2;
              break;

            case 't':
              sb.append('\t');
              pos += 2;
              break;

            case 'u':
              // Unicode escape; count the number of characters
              int numChars = 0;
              boolean foundNonHex = false;
              while (numChars < 4 && (false == foundNonHex)) {
                char c = str.charAt(pos + 2 + numChars);
                if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) {
                  numChars++;
                } else {
                  foundNonHex = true;
                }

              }
              if (0 == numChars) {
                throw new IllegalArgumentException("No hex characters in unicode escape");
              }
              sb.append(decodeHex(str, pos + 2, numChars));
              pos += 2 + numChars;
              break;

            default:
              // Other characters just get passed through.
              sb.append(str.charAt(pos + 1));
              pos += 2;
              break;
          }

        } else {
          throw new IllegalArgumentException("Escape character at end of string");
        }
      } else {
        // append the char at the current position
        sb.append(str.charAt(pos));

        // go to next char
        pos++;
      }
    }

    return sb.toString();
  }

  /**
   * Removes the quotes from a quoted string as returned by a parser and de-escapes escaped
   * characters from the string.
   * 
   * @param str input string
   * @return the string, with with quotes removed and any escaped characters inside the string
   *         de-escaped.
   */
  public static final String dequoteAndDeescapeStr(char quotechar, String str) {

    if (str.charAt(0) != quotechar || str.charAt(str.length() - 1) != quotechar) {
      throw new IllegalArgumentException("Can't dequote string '" + str + "'");
    }

    // remove the quotes from begin and end of the input string
    str = str.substring(1, str.length() - 1);

    StringBuilder sb = new StringBuilder();

    final char ESCAPE = '\\';
    int len = str.length();

    // input string of length 0
    if (len == 0)
      return str;

    // input string of length > 0
    int pos = 0;

    while (pos < len) {
      if (str.charAt(pos) == ESCAPE) {

        if (pos < len - 1) {

          switch (str.charAt(pos + 1)) {
            // Handle built-in escape codes
            case 'n':
              sb.append('\n');
              pos += 2;
              break;

            case 'r':
              sb.append('\r');
              pos += 2;
              break;

            case 't':
              sb.append('\t');
              pos += 2;
              break;

            case 'u':
              // Unicode escape; count the number of characters
              int numChars = 0;
              boolean foundNonHex = false;
              while (numChars < 4 && (false == foundNonHex)) {
                char c = str.charAt(pos + 2 + numChars);
                if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) {
                  numChars++;
                } else {
                  foundNonHex = true;
                }

              }
              if (0 == numChars) {
                throw new IllegalArgumentException("No hex characters in unicode escape");
              }
              sb.append(decodeHex(str, pos + 2, numChars));
              pos += 2 + numChars;
              break;

            default:
              // Other characters (including the quotechar and the ESCAPE char) just get passed
              // through.
              sb.append(str.charAt(pos + 1));
              pos += 2;
              break;
          }

        } else {
          throw new IllegalArgumentException(
              String.format("Escape character at end of string: '%s'", str));
        }
      } else {
        // append the char at the current position
        sb.append(str.charAt(pos));

        // go to next char
        pos++;
      }
    }

    return sb.toString();
  }

  /**
   * Removes the quotes from a quoted string as returned by a parser and de-escapes escaped
   * characters from the string.
   * 
   * @param str input string
   * @return the string, with with quotes removed and any escaped characters inside the string
   *         de-escaped.
   */
  public static final String dequoteAndDeescapeUnicodeStr(char quotechar, String str) {

    if (str.charAt(0) != quotechar || str.charAt(str.length() - 1) != quotechar) {
      throw new IllegalArgumentException("Can't dequote string '" + str + "'");
    }

    // remove the quotes from begin and end of the input string
    str = str.substring(1, str.length() - 1);

    StringBuilder sb = new StringBuilder();

    final char ESCAPE = '\\';
    int len = str.length();
    char nextChar;

    // input string of length 0
    if (len == 0)
      return str;

    // input string of length > 0
    int pos = 0;

    while (pos < len) {
      if (str.charAt(pos) == ESCAPE) {

        if (pos < len - 1) {
          nextChar = str.charAt(pos + 1);

          if (nextChar == quotechar || nextChar == ESCAPE) {
            // The next char is a quote, or an ESCAPE, skip the current char (the ESCAPE)
            // and pass through the next char (the quote or the ESCAPE)
            sb.append(nextChar);
            pos += 2;
          } else if (nextChar == 'u') {
            // Unicode escape; count the number of characters
            int numChars = 0;
            boolean foundNonHex = false;
            while (numChars < 4 && (false == foundNonHex)) {
              char c = str.charAt(pos + 2 + numChars);
              if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) {
                numChars++;
              } else {
                foundNonHex = true;
              }

            }
            if (0 == numChars) {
              throw new IllegalArgumentException("No hex characters in unicode escape");
            }
            sb.append(decodeHex(str, pos + 2, numChars));
            pos += 2 + numChars;
          } else {
            // All other characters just get passed through.
            sb.append(str.charAt(pos));
            pos += 1;
          }

        } else {
          throw new IllegalArgumentException(
              String.format("Escape character at end of string: '%s'", str));
        }
      } else {
        // append the char at the current position
        sb.append(str.charAt(pos));

        // go to next char
        pos++;
      }
    }

    return sb.toString();
  }

  /**
   * Decode a hex escape within a string
   * 
   * @param str the original string
   * @param startOff offset of the hex escape
   * @param len number of hex characters to decode
   * @return the decoded character
   */
  public static char decodeHex(String str, int startOff, int len) {
    // First two chars are the "\x" or u
    char accum = 0;

    for (int i = startOff; i < startOff + len; i++) {
      char curChar = str.charAt(i);

      char baseChar;
      if (curChar >= '0' && curChar <= '9') {
        baseChar = '0';
      } else if (curChar >= 'a' && curChar <= 'f') {
        baseChar = 'a' - 10;
      } else if (curChar >= 'A' && curChar <= 'F') {
        baseChar = 'A' - 10;
      } else {
        throw new RuntimeException("Unexpected char in hex escape;" + " should never happen");
      }
      accum += (str.charAt(i) - baseChar);
      if (i < str.length() - 1) {
        accum <<= 4;
      }
    }

    return accum;
  }

  public static final String quoteStrOld(char quotechar, CharSequence str) {
    return quoteStr(quotechar, str, false, false);
  }

  /**
   * Convenience method for the common case where control characters and backslashes should be
   * escaped.
   */
  public static final String quoteStr(char quotechar, CharSequence str) {
    return quoteStr(quotechar, str, true, true);
  }

  /**
   * Retain the second-oldest behavior of this method.
   */
  // public static final String quoteStr(char quotechar, CharSequence str,
  // boolean escapeEscapes) {
  // return quoteStr(quotechar, str, escapeEscapes, false);
  // }
  /**
   * Adds quotes to a string, escaping any quotes inside the string.
   * 
   * @param quotechar
   * @param str input string
   * @param escapeEscapes TRUE to escape any escape characters inside the string.
   * @param escapeControl TRUE to escape any carriage return, newline, or other control characters
   *        inside the string.
   * @return the string, surrounded by quotes and with any internal quotes escaped
   */
  public static final String quoteStr(char quotechar, CharSequence str, boolean escapeEscapes,
      boolean escapeControl) {

    StringBuilder sb = new StringBuilder();

    final char ESCAPE = '\\';

    // Opening quote
    sb.append(quotechar);

    // Walk through the string from start to end, escaping quotes.
    for (int pos = 0; pos < str.length(); pos++) {
      char curChar = str.charAt(pos);

      if (curChar == quotechar) {
        sb.append(ESCAPE);
        sb.append(curChar);
      } else if (escapeEscapes && curChar == ESCAPE) {
        // Escape the escape char...
        sb.append(ESCAPE);
        sb.append(curChar);
      } else if (escapeControl && Character.isISOControl(curChar)) {
        // Escape a control character.
        switch (curChar) {
          // Use shorthand for the three most common control characters.
          case '\n':
            sb.append("\\n");
            break;

          case '\r':
            sb.append("\\r");
            break;

          case '\t':
            sb.append("\\t");
            break;

          default:
            // Uncommon control chars get printed in octal notation.
            sb.append(String.format("\\u%04o", (int) curChar));
            break;
        }
      } else {
        sb.append(curChar);
      }
    }

    // Closing quote
    sb.append(quotechar);

    return sb.toString();
  }

  /**
   * Quotes a string for an Excel CSV file. The string is surrounded by double quotes, and any
   * instance of the double-quote character is replaced by two double-quotes. Newlines are also
   * escaped.
   * 
   * @param orig string to quote
   * @return quoted version of orig, including the quotes
   */
  public static String quoteForCSV(CharSequence orig) {
    StringBuilder sb = new StringBuilder();
    sb.append('"');

    for (int i = 0; i < orig.length(); i++) {
      final char c = orig.charAt(i);

      // Character conversions happen here:
      if ('"' == c) {
        sb.append("\"\"");
      } else if ('\n' == c) {
        sb.append("\\n");
      } else {
        sb.append(c);
      }
    }

    sb.append('"');
    return sb.toString();
  }

  /**
   * The inverse of {@link #quoteForCSV(CharSequence)}.
   * 
   * @param quoted quoted string from CSV file, including the quotes
   * @return the original, de-escaped string.
   */
  public static String unquoteFromCSV(CharSequence quoted) {
    StringBuilder sb = new StringBuilder();

    if ('"' != quoted.charAt(0)) {
      throw new RuntimeException("Input string does not begin with double quote");
    }
    if ('"' != quoted.charAt(quoted.length() - 1)) {
      throw new RuntimeException("Input string does not end with double quote");
    }

    // Walk through the quoted string (not counting the quotes), undoing the escaping that
    // quoteForCSV() does.
    for (int i = 1; i < quoted.length() - 1; i++) {
      final char c = quoted.charAt(i);

      // Character conversions happen here:
      if ('"' == c) {
        // Found a double quote. The next character should be a double quote too.
        if (i >= quoted.length() - 2 || '"' != quoted.charAt(i + 1)) {
          throw new RuntimeException(
              String.format("Unterminated double quote escape at position %d", i));
        } else {
          sb.append(c);
          i++;
        }
      } else if ('\\' == c) {
        if (i < quoted.length() - 2 && 'n' == quoted.charAt(i + 1)) {
          // Escaped newline
          sb.append('\n');
          i++;
        } else {
          // Newline is the only backslash escape put in place by quoteForCSV()
          sb.append(c);
        }
      } else {
        // Normal case
        sb.append(c);
      }
    }

    return sb.toString();
  }

  /**
   * Quotes a string for inclusion in an JSON record. The string is surrounded by double quotes, and
   * any instance of the double-quote character is replaced by a backslash, followed by double
   * quotes. Newlines are also escaped.
   */
  public static String quoteForJSON(CharSequence orig) {
    return quoteStr('"', orig, true, true);
  }

  public static <T> String join(List<T> objs, String delim) {
    String[] strs = new String[objs.size()];
    for (int i = 0; i < objs.size(); i++) {
      strs[i] = "" + objs.get(i);
    }
    return join(strs, delim, 0);
  }

  public static String join(Object[] objs, String delim) {
    String[] strs = new String[objs.length];
    for (int i = 0; i < objs.length; i++) {
      strs[i] = "" + objs[i];
    }
    return join(strs, delim, 0);
  }

  public static String join(String[] array, String delim) {
    return join(array, delim, 0);
  }

  public static String join(String[] strs, char sepChar) {
    return join(strs, new String(new char[] {sepChar}));
  }

  public static String join(String[] array, String delim, int max) {
    if (array == null) {
      return null;
    }
    StringBuffer sb = join(array, delim, new StringBuffer(), max);
    return sb.toString();
  }

  public static String join(String[] array, String delim, int begin, int end) {
    StringBuffer sb = join(array, delim, new StringBuffer(), begin, end);
    return sb.toString();
  }

  public static StringBuffer join(String[] array, String delim, StringBuffer sb, int max) {
    if (max == 0) {
      max = array.length;
    }

    for (int i = 0; i < max; i++) {
      if (i != 0)
        sb.append(delim);
      sb.append(array[i]);
    }
    return sb;
  }

  public static StringBuffer join(String[] array, String delim, StringBuffer sb, int begin,
      int end) {
    for (int i = begin; i < end; i++) {
      if (i != begin)
        sb.append(delim);
      sb.append(array[i]);
    }
    return sb;
  }

  public static String[] quote(String[] vals, String quote) {
    String[] results = new String[vals.length];
    for (int i = 0; i < vals.length; i++) {
      results[i] = quote + vals[i] + quote;
    }
    return results;
  }

  // public static String quote(String val, String quote) {
  // return (quote + val + quote);
  // }

  public static String convertToLower(String s) {
    if (s == null)
      return null;
    else
      return (s.toLowerCase());
  }

  public static boolean isTrue(String value) {
    return value.equals("1") || value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("true");
  }

  /**
   * Maximum length of the strings returned by {@link #shorten(String)}; must be even.
   */
  private static final int SHORTEN_STR_LEN = 100;

  public static CharSequence shorten(CharSequence input) {
    return shorten(input, SHORTEN_STR_LEN, false);
  }

  /**
   * Shorten a string to <= {@link #SHORTEN_STR_LEN} chars.
   * 
   * @param oneLine true to avoid adding line breaks to the string when shortening it.
   */
  public static CharSequence shorten(CharSequence input, int maxLen, boolean oneLine) {

    final String SINGLE_LINE_ELLIPSIS = "...";

    // "snip" is the sound of part of the text being cut out.
    final String MULTI_LINE_ELLIPSIS = "...\n[ snip! ]\n...";

    String ellipsis = oneLine ? SINGLE_LINE_ELLIPSIS : MULTI_LINE_ELLIPSIS;

    if (input.length() <= maxLen) {
      return input;
    }

    CharSequence begin = input.subSequence(0, maxLen / 2);
    CharSequence end;
    if (false == oneLine) {
      // SPECIAL CASE: Don't change semantics for multi-line printouts
      // yet, to avoid screwing up regression test results.
      end = input.subSequence(input.length() - begin.length(), input.length());
      // END SPECIAL CASE
    } else {

      int endLen = maxLen - begin.length() - ellipsis.length();
      end = input.subSequence(input.length() - endLen, input.length());
    }

    return begin + ellipsis + end;
  }

  /**
   * Compute the hash code for a CharSequence, using java.lang.String's hash function.
   */
  public static int strHash(CharSequence seq) {

    // Should we double-check our answers?
    final boolean paranoid = false;

    // The following is equivalent to the string hash function in IBM Java
    // 6:
    int hash = 0, multiplier = 1;
    for (int i = seq.length() - 1; i >= 0; i--) {
      hash += seq.charAt(i) * multiplier;
      int shifted = multiplier << 5;
      multiplier = shifted - multiplier;
    }

    if (paranoid) {
      // Do things the expensive way and compare.
      int actualHash = seq.toString().hashCode();
      if (actualHash != hash) {
        throw new RuntimeException(
            String.format("Internal error: Hashcodes 0x%x and 0x%x" + " for '%s' don't match.",
                hash, actualHash, seq));
      }
    }

    return hash;
  }

  /**
   * Compute the hash code for a CharSequence, using a more effective hash function.
   */
  public static int betterStrHash(CharSequence seq) {

    long hash = 0;
    for (int pos = 0; pos < seq.length(); pos++) {
      char c = seq.charAt(pos);
      hash = c + (hash << 6) + (hash << 16) - hash;

    }

    // Reduce to 32 bits.
    long highBits = ((hash >> 32) & 0x00000000ffffffffL);
    long lowBits = (hash & 0x00000000ffffffffL);
    // System.err.printf("High bits: %x, low bits: %x\n", highBits,
    // lowBits);

    int ret = (int) (highBits ^ lowBits);

    // System.err.printf("%s ==> %x\n", Arrays.toString(elems), ret);

    return ret;
  }

  /**
   * Determine whether two CharSequences are equal.
   */
  public static boolean strEq(CharSequence c1, CharSequence c2) {
    if (c1.length() != c2.length()) {
      return false;
    }

    for (int i = 0; i < c1.length(); i++) {
      if (c1.charAt(i) != c2.charAt(i)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Split a string on a single character, as opposed to a regex.
   * 
   * @param str string to split
   * @param delim delimiter character
   * @return array of substrings
   */
  public static String[] split(String str, char delim) {

    // Start by counting the number of times the delimiter occurs, so that
    // we only have to allocate one array of strings.
    int numDelim = 0;
    for (int i = 0; i < str.length(); i++) {
      if (str.charAt(i) == delim) {
        numDelim++;
      }
    }

    // Now make a second pass, generating substrings.
    String[] ret = new String[numDelim + 1];

    int pos = 0;

    for (int i = 0; i < ret.length - 1; i++) {
      // Calculate offsets for the current chunk of the string.
      int begin = pos;
      int end = str.indexOf(delim, pos);
      ret[i] = str.substring(begin, end);

      pos = end + 1;
    }

    // Don't forget the last one!
    ret[ret.length - 1] = str.substring(pos);

    return ret;
  }

  /**
   * Version of {@link #split(String, char)} that deals with CharSequences.
   */
  public static CharSequence[] split(CharSequence str, char delim) {

    // Start by counting the number of times the delimiter occurs, so that
    // we only have to allocate one array of strings.
    int numDelim = 0;
    for (int i = 0; i < str.length(); i++) {
      if (str.charAt(i) == delim) {
        numDelim++;
      }
    }

    // Now make a second pass, generating substrings.
    CharSequence[] ret = new String[numDelim + 1];

    int pos = 0;

    for (int i = 0; i < ret.length - 1; i++) {
      // Calculate offsets for the current chunk of the string.
      int begin = pos;

      int end = begin;
      while (begin < str.length() && delim != str.charAt(end)) {
        end++;
      }

      ret[i] = str.subSequence(begin, end);

      pos = end + 1;
    }

    // Don't forget the last one!
    ret[ret.length - 1] = str.subSequence(pos, str.length());

    return ret;
  }

  /**
   * Strip off whitespace from the beginning and end of a string.
   * 
   * @param orig input string, possibly including leading/trailing whitespace
   * @return a version of the input string with whitespace removed
   */
  public static CharSequence chomp(CharSequence orig) {

    // Find the range of the original string that does not start or end with
    // whitespace
    int begin = 0;
    int end = orig.length();

    while (begin < end && Character.isWhitespace(orig.charAt(begin))) {
      begin++;
    }

    while (begin < end && Character.isWhitespace(orig.charAt(end - 1))) {
      end--;
    }

    return orig.subSequence(begin, end);
  }

  /** Convert a string into an AOG nickname, escaping as appropriate. */
  public static String toAOGNick(String name) {
    if (name.matches(AnnotPlan.NICKNAME_REGEX)) {
      // Simple names don't need escaping.
      return String.format("$%s", name);
    } else {
      return String.format("$_{\"%s\"}", name);
    }
  }

  /**
   * Get a stack trace as a string.
   */
  public static String stackTraceStr(Throwable t) {
    CharArrayWriter buf = new CharArrayWriter();
    PrintWriter pw = new PrintWriter(buf);
    t.printStackTrace(pw);
    return buf.toString();
  }

  /**
   * Checks whether the given string is either null or just a whitespace
   * 
   * @param str input string
   * @return true, if str is null or a whitespace. false, otherwise.
   */
  public static boolean isNullOrWhiteSpace(String str) {
    return (str == null || str.trim().length() == 0);
  }

  /**
   * Concatenates a list of items into a single string by using the given separator character.
   * 
   * @param items list of String items to be concatenated
   * @param separator the separator character to use
   * @return contacted string of items, delmited by the separator character. Returns an empty
   *         string, if the list is null or empty.
   */
  public static String concatenate(List<String> items, char separator) {
    if (items == null || items.size() == 0) {
      return "";
    }

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < items.size(); i++) {
      sb.append(items.get(i));
      if (i != items.size() - 1) {
        sb.append(separator);
      }
    }
    return sb.toString();
  }

  /**
   * Splits a String into tokens based on a separator character.
   * 
   * @param item String containing multiple tokens separated by a separator character
   * @param separator character separating tokens in the given string
   * @return list of tokens contained in the given string input. Returns an empty list, if the input
   *         string is null or white space.
   */
  public static List<String> convertToList(String item, char separator) {
    if (StringUtils.isNullOrWhiteSpace(item)) {
      return new ArrayList<String>();
    } else {
      StringTokenizer tokenizer = new StringTokenizer(item, String.valueOf(separator));
      ArrayList<String> ret = new ArrayList<String>();
      while (tokenizer.hasMoreTokens()) {
        ret.add(tokenizer.nextToken());
      }
      return ret;
    }
  }
}
