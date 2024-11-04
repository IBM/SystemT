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
package com.ibm.systemt.util.regex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Pattern;

import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.aog.Token;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;

/**
 * Methods for decoding and encoding regex matching flags to and from the string format that AQL
 * uses.
 * 
 */
public class FlagsString {

  /*
   * CONSTANTS
   */

  // Value of flags allowed based on documentation at
  // http://java.sun.com/docs/books/tutorial/essential/regex/pattern.html
  public final static String CANON_EQ = "CANON_EQ";
  public final static String CASE_INSENSITIVE = "CASE_INSENSITIVE";
  public final static String UNICODE_CASE = "UNICODE";
  public final static String DOTALL = "DOTALL";
  public final static String LITERAL = "LITERAL";
  public final static String MULTILINE = "MULTILINE";
  public final static String UNIX_LINES = "UNIX_LINES";
  // Changed by Fred on Oct. 2, 2008: Java's COMMENTS flag is meaningless
  // in AQL.
  // public final static String COMMENTS = "COMMENTS";
  // END CHANGE
  // End
  /** Flags that are used if no specific flags string is specified. */
  public final static String DEFAULT_FLAGS = DOTALL;

  public static final String[] ALL_FLAGS =
      {CANON_EQ, CASE_INSENSITIVE, UNICODE_CASE, DOTALL, LITERAL, MULTILINE, UNIX_LINES};

  /*
   * METHODS
   */

  // Yunyao: added 10/05/07
  // begin
  /**
   * Validate the given flag value and transform it into Java regular expression specifications <br>
   * A valid flag value must be one or disjunctive form of the flag names defined above
   */
  public static final int decode(AQLFunc func, String flagStr)
      throws FunctionCallValidationException {
    String[] flags = flagStr.split("\\|");

    int flagValue = 0;

    for (int i = 0; i < flags.length; i++) {
      flags[i] = flags[i].trim();

      if (flags[i].equals(FlagsString.CANON_EQ)) {
        if (flagValue == 0) {
          flagValue = Pattern.CANON_EQ;
        } else {
          flagValue |= Pattern.CANON_EQ;
        }
      } else if (flags[i].equals(FlagsString.CASE_INSENSITIVE)) {
        if (flagValue == 0) {
          flagValue = Pattern.CASE_INSENSITIVE;
        } else {
          flagValue |= Pattern.CASE_INSENSITIVE;
        }
        // REMOVED by Fred on Oct. 2, 2008: Java's COMMENTS flag is
        // meaningless in AQL.
        // else if (flags[i].equals(COMMENTS_FLAGNAME)) {
        // if (flagValue == 0) {
        // flagValue = Pattern.COMMENTS;
        // } else {
        // flagValue |= Pattern.COMMENTS;
        // }
        // END CHANGE
      } else if (flags[i].equals(FlagsString.DOTALL)) {
        if (flagValue == 0) {
          flagValue = Pattern.DOTALL;
        } else {
          flagValue |= Pattern.DOTALL;
        }
      } else if (flags[i].equals(FlagsString.LITERAL)) {
        if (flagValue == 0) {
          flagValue = Pattern.LITERAL;
        } else {
          flagValue |= Pattern.LITERAL;
        }
      } else if (flags[i].equals(FlagsString.MULTILINE)) {
        if (flagValue == 0) {
          flagValue = Pattern.MULTILINE;
        } else {
          flagValue |= Pattern.MULTILINE;
        }
      } else if (flags[i].equals(FlagsString.UNICODE_CASE)) {
        if (flagValue == 0) {
          flagValue = Pattern.UNICODE_CASE;
        } else {
          flagValue |= Pattern.UNICODE_CASE;
        }
      } else if (flags[i].equals(FlagsString.UNIX_LINES)) {
        if (flagValue == 0) {
          flagValue = Pattern.UNIX_LINES;
        } else {
          flagValue |= Pattern.UNIX_LINES;
        }
      } else if (flags[i].equals("") == false) // flag string can be empty
      {
        String errorMsg = String.format("Invalid match mode '%s', valid modes are '%s'", flagStr,
            Arrays.toString(ALL_FLAGS));

        if (func != null) {
          throw new FunctionCallValidationException(func, errorMsg);
        } else {
          throw new FunctionCallValidationException(errorMsg);
        }
      }
    }

    // Check added by Fred on Oct. 2, 2008:
    // UNICODE_CASE is meaningless without CASE_INSENSITIVE. Sun Java
    // just ignores the flag if it appears by itself, but IBM Java
    // ignores the entire regular expression. Throw an error to keep
    // the user from getting confused by this behavior.
    if (0 != (flagValue & Pattern.UNICODE_CASE)) {
      if (0 == (flagValue & Pattern.CASE_INSENSITIVE)) {
        String errorMsg =
            "UNICODE_CASE flag has undefined semantics unless used with CASE_INSENSITIVE flag";

        if (func != null) {
          throw new FunctionCallValidationException(func, errorMsg);
        } else {
          throw new FunctionCallValidationException(errorMsg);
        }
      }
    }
    // END CHANGE

    return flagValue;
  }

  /**
   * The inverse of {@link #decode(String, Token)}
   * 
   * @param flags integer flags, as returned by {@link #decode(String, Token)} and as used by
   *        {@link java.util.regex.Pattern}
   * @return corresponding AQL flags string
   */
  public static final String encode(int flags) {
    ArrayList<String> flagStrs = new ArrayList<String>();

    // Separate the mask into individual flags.
    if (flagSet(flags, Pattern.CANON_EQ)) {
      flagStrs.add(CANON_EQ);
    }

    if (flagSet(flags, Pattern.CASE_INSENSITIVE)) {
      flagStrs.add(CASE_INSENSITIVE);
    }

    if (flagSet(flags, Pattern.DOTALL)) {
      flagStrs.add(DOTALL);
    }

    if (flagSet(flags, Pattern.LITERAL)) {
      flagStrs.add(LITERAL);
    }

    if (flagSet(flags, Pattern.MULTILINE)) {
      flagStrs.add(MULTILINE);
    }

    if (flagSet(flags, Pattern.UNICODE_CASE)) {
      flagStrs.add(UNICODE_CASE);
    }

    if (flagSet(flags, Pattern.UNIX_LINES)) {
      flagStrs.add(UNIX_LINES);
    }

    return StringUtils.join(flagStrs, "|");
  }

  /** Test whether a particular bit is set in a mask. */
  private static final boolean flagSet(int mask, int flag) {
    return (0 != (mask & flag));
  }

}
