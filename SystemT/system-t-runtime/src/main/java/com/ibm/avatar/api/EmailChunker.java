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
package com.ibm.avatar.api;

import com.ibm.avatar.algebra.util.string.ReusableString;
import com.ibm.systemt.regex.api.RegexMatcher;
import com.ibm.systemt.regex.api.SimpleRegex;
import com.ibm.systemt.regex.parse.ParseException;

/**
 * Document chunker for email messages. The chunker attempts to split where two newline characters
 * are found (double newlines); if it can't find any, then it splits where a single newline
 * character is found, that is not part of a header.
 */
public class EmailChunker implements Chunker {

  /**
   * Pointer to the last document text that we saw, so that we don't have to check whether the text
   * has no double newlines after a certain point.
   */
  private CharSequence lastDoctext = null;

  /**
   * Position in the current document text after which there are guaranteed to be no double
   * newlines.
   */
  private int noDoubleNewlinesAfterPos = Integer.MAX_VALUE;

  /**
   * Regular expression for double newlines.
   */
  private static SimpleRegex DOUBLE_NEWLINE_REGEX;
  static {
    try {
      DOUBLE_NEWLINE_REGEX = new SimpleRegex("\\n{2,2}|(\\r\\n){2,2}");
    } catch (ParseException e) {
      e.printStackTrace();
    }
  }

  /**
   * Regular expression for "signatures" that mark a line as possibly being part of a header.
   */
  private static SimpleRegex HEADER_SIG_REGEX;
  static {
    try {
      HEADER_SIG_REGEX = new SimpleRegex("[@/:]");
    } catch (ParseException e) {
      e.printStackTrace();
    }
  }

  private final RegexMatcher doubleNewlineMatcher = DOUBLE_NEWLINE_REGEX.matcher("");

  private final RegexMatcher headerSigMatcher = HEADER_SIG_REGEX.matcher("");

  private final ReusableString doctextStr = new ReusableString();

  /**
   * {@inheritDoc}
   *
   * Returns the start position of the next chunk in the email.
   */
  @Override
  public int getNextBoundary(CharSequence doctext, int startPos) {
    if (doctext != lastDoctext) {
      lastDoctext = doctext;
      noDoubleNewlinesAfterPos = doctext.length();
      doubleNewlineMatcher.reset(doctext);
    }

    // First, check for double newlines.
    if (startPos >= noDoubleNewlinesAfterPos) {
      // SPECIAL CASE: Skip the check, because a previous iteration has
      // determined that we won't find any.

    } else {
      doubleNewlineMatcher.region(startPos, noDoubleNewlinesAfterPos);

      if (doubleNewlineMatcher.find()) {
        // Found a double newline.
        return doubleNewlineMatcher.start() + 1;
      } else {
        // No double newlines in the rest of the document. Remember this
        // fact so that we don't rerun the regex.
        noDoubleNewlinesAfterPos = startPos;
      }
    }

    // If we get here, we didn't find any double newlines. As a fallback,
    // split on a line break that is not in an email header.
    doctextStr.setToSubstr(doctext, startPos, doctext.length());
    int nextLineBreak = startPos + doctextStr.indexOf('\n');
    int lineStart = startPos;

    headerSigMatcher.reset(doctext);

    while (-1 != nextLineBreak) {
      // Make sure the line is not part of a header. We do this by
      // checking for certain "header indicators".
      headerSigMatcher.region(lineStart, nextLineBreak);
      if (false == headerSigMatcher.find()) {
        // Line does not appear to be part of a header.
        return nextLineBreak + 1;
      }

      lineStart = nextLineBreak + 1;
      doctextStr.setToSubstr(doctext, lineStart, doctext.length());
      nextLineBreak = doctextStr.indexOf('\n') + lineStart;
    }

    // If we get here, we didn't even find a line break. Don't split at all.
    return doctext.length();
  }

  /**
   * {@inheritDoc}
   *
   * Returns a new {@link #EmailChunker()} instance.
   */
  @Override
  public Chunker makeClone() {
    // Every EmailChunker is the same.
    return new EmailChunker();
  }

}
