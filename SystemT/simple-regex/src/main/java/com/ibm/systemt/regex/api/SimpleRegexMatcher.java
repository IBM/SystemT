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
package com.ibm.systemt.regex.api;

/**
 * Main API for executing a SimpleRegex regular expression. Similar to the standard Java API. This
 * class is meant to belong to a *single* thread, but multiple SimpleRegexMatchers in different
 * threads can share the same underlying regular expression NFA/DFA.
 * 
 * This class also has methods (not part of the RegexMatcher interface) for running multiple regexes
 * at once.
 * 
 */
public class SimpleRegexMatcher extends RegexMatcher {

  /** The actual regular expression; may be shared across threads! */
  private SimpleRegex regex;

  private CharSequence text;

  /** Cached length of the text. String.length() is slow on IBM java. */
  private int textLen;

  private int curPos = 0;

  /** Begin of the matching region in the text */
  private int regionBegin;

  /** End of the matching region in the text */
  private int regionEnd;

  /** Begin of the regex match found by the last call to find() */
  private int matchBegin;

  /** End of the regex match found by the last call to find() */
  private int matchEnd;

  /**
   * Class that encapsulates reusable buffers for running state machines and recording matches.
   */
  private SimpleMatchBuf matchBuf = new SimpleMatchBuf();

  public SimpleRegexMatcher(SimpleRegex regex, CharSequence text) {
    this.regex = regex;

    // Allocate buffers.
    // curStatesBuf = new int[2 * regex.getNumNFAStates()];
    // nextStatesBuf = new int[2 * regex.getNumNFAStates()];

    // this.matchLengths = new int[regex.getNumPatterns()];
    reset(text);
  }

  @Override
  public boolean find() {
    // Make sure that our match recording machinery is in
    // single-longest-match mode.
    matchBuf.setLongestSingleMode();

    while (curPos <= regionEnd) {
      runFSM(curPos, regionEnd);
      int acceptedLen = matchBuf.getMatchLen();
      // The runFSM() method returns the length of the longest accepted
      // string at the given offset.

      if (0 == acceptedLen) {
        // SPECIAL CASE: Regex matched the empty string; return a match,
        // but advance to the next character.
        this.matchBegin = curPos;
        this.matchEnd = curPos;
        curPos++;
        return true;
        // END SPECIAL CASE
      } else if (acceptedLen > 0) {
        // Found a match. Create the corresponding annotation and
        // move past the match.
        this.matchBegin = curPos;
        this.matchEnd = curPos + acceptedLen;
        curPos += acceptedLen;
        return true;

      } else {
        // No match; check next character.
        curPos++;
      }
    }

    // No match found.
    return false;
  }

  @Override
  public int end(int i) {
    return this.matchEnd;
  }

  @Override
  public int start(int i) {
    // No capturing groups in this regex package!
    if (0 != i) {
      throw new RuntimeException("Capturing groups not supported " + "in SimpleRegex engine");
    }
    return this.matchBegin;
  }

  @Override
  public int start() {
    return this.matchBegin;
  }

  @Override
  public int end() {
    return this.matchEnd;
  }

  @Override
  public boolean find(int pos) {
    curPos = pos;
    return this.find();
  }

  @Override
  public boolean matches() {
    // Make sure that our match recording machinery is in
    // single-longest-match mode.
    matchBuf.setLongestSingleMode();

    runFSM(regionBegin, regionEnd);
    int acceptedLen = matchBuf.getMatchLen();

    // Only return true if the entire region matches.
    if (regionEnd - regionBegin == acceptedLen) {
      return true;
    } else {
      return false;
    }
  }

  @Override
  public boolean lookingAt() {
    // Make sure that our match recording machinery is in
    // single-longest-match mode.
    matchBuf.setLongestSingleMode();

    runFSM(regionBegin, regionEnd);
    int acceptedLen = matchBuf.getMatchLen();
    if (acceptedLen > 0) {
      // Found a match; mark its location.
      this.matchBegin = regionBegin;
      this.matchEnd = regionBegin + acceptedLen;
      return true;
    } else {
      return false;
    }
  }

  @Override
  public void region(int start, int end) {

    // Sanity check.
    if (end > textLen) {
      throw new RuntimeException(
          String.format("Invalid region [%d,%d] selected" + " for %d-char string '%s'", start, end,
              textLen, text));
    }

    regionBegin = start;
    curPos = regionBegin;
    regionEnd = end;
  }

  private void runFSM(int startPos, int maxPos) {
    regex.runFSM(text, startPos, maxPos, matchBuf);
  }

  @Override
  public void reset(CharSequence text) {
    this.text = text;
    this.textLen = text.length();
    this.matchBegin = -1;
    this.matchEnd = -1;
    this.curPos = 0;
    this.regionBegin = 0;
    this.regionEnd = text.length();
  }

  /**
   * @return lengths of all matches (for multiple input regexes) that start at the beginning of the
   *         current region. Note that this array will be overwritten on the next call to this
   *         method!
   */
  public int[] getAllMatches() {
    matchBuf.setLongestMultiMode(regex.getNumPatterns());
    matchBuf.reset();
    runFSM(regionBegin, regionEnd);
    return matchBuf.getMatchLens();
  }

}
