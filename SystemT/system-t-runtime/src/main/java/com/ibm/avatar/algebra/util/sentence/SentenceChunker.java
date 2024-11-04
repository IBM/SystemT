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
package com.ibm.avatar.algebra.util.sentence;

import java.io.BufferedWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.regex.Matcher;

public class SentenceChunker {
  private Matcher sentenceEndingMatcher = null;

  public static BufferedWriter sentenceBufferedWriter = null;

  private HashSet<String> abbreviations = new HashSet<String>();

  public SentenceChunker() {

  }

  /** Constructor that takes in the abbreviations directly. */
  public SentenceChunker(String[] abbreviations) {

    // Generate the abbreviations directly.
    for (String abbr : abbreviations) {
      this.abbreviations.add(abbr);
    }
  }

  /**
   * @param doc the document text to be analyzed
   * @return true if the document contains at least one sentence boundary
   */
  public boolean containsSentenceBoundary(String doc) {

    String origDoc = doc;

    /*
     * Based on getSentenceOffsetArrayList()
     */

    // String origDoc = doc;
    // int dotpos, quepos, exclpos, newlinepos;
    int boundary;
    int currentOffset = 0;

    do {

      /* Get the next tentative boundary for the sentenceString */
      setDocumentForObtainingBoundaries(doc);
      boundary = getNextCandidateBoundary();

      if (boundary != -1) {
        String candidate = doc.substring(0, boundary + 1);
        String remainder = doc.substring(boundary + 1);

        /*
         * Looks at the last character of the String. If this last character is part of an
         * abbreviation (as detected by REGEX) then the sentenceString is not a fullSentence and
         * "false" is returned
         */
        // while (!(isFullSentence(candidate) &&
        // doesNotBeginWithCaps(remainder))) {
        while (!(doesNotBeginWithPunctuation(remainder) && isFullSentence(candidate))) {

          /* Get the next tentative boundary for the sentenceString */
          int nextBoundary = getNextCandidateBoundary();
          if (nextBoundary == -1) {
            break;
          }
          boundary = nextBoundary;
          candidate = doc.substring(0, boundary + 1);
          remainder = doc.substring(boundary + 1);

        }

        if (candidate.length() > 0) {
          // sentences.addElement(candidate.trim().replaceAll("\n", "
          // "));
          // sentenceArrayList.add(new Integer(currentOffset + boundary
          // + 1));
          // currentOffset += boundary + 1;
          // Found a sentence boundary. If the boundary is the last
          // character in the string, we don't consider it to be
          // contained within the string.
          int baseOffset = currentOffset + boundary + 1;
          if (baseOffset < origDoc.length()) {
            // System.err.printf("Sentence ends at %d of %d\n",
            // baseOffset, origDoc.length());
            return true;
          } else {
            return false;
          }
        }
        // origDoc.substring(0,currentOffset));
        // doc = doc.substring(boundary + 1);
        doc = remainder;
      }
    } while (boundary != -1);

    // If we get here, didn't find any boundaries.
    return false;

  }

  public ArrayList<Integer> getSentenceOffsetArrayList(String doc) {
    ArrayList<Integer> sentenceArrayList = new ArrayList<Integer>();

    // String origDoc = doc;
    // int dotpos, quepos, exclpos, newlinepos;
    int boundary;
    int currentOffset = 0;
    sentenceArrayList.add(new Integer(0));

    do {

      /* Get the next tentative boundary for the sentenceString */
      setDocumentForObtainingBoundaries(doc);
      boundary = getNextCandidateBoundary();

      if (boundary != -1) {
        String candidate = doc.substring(0, boundary + 1);
        String remainder = doc.substring(boundary + 1);

        /*
         * Looks at the last character of the String. If this last character is part of an
         * abbreviation (as detected by REGEX) then the sentenceString is not a fullSentence and
         * "false" is returned
         */
        // while (!(isFullSentence(candidate) &&
        // doesNotBeginWithCaps(remainder))) {
        while (!(doesNotBeginWithPunctuation(remainder) && isFullSentence(candidate))) {

          /* Get the next tentative boundary for the sentenceString */
          int nextBoundary = getNextCandidateBoundary();
          if (nextBoundary == -1) {
            break;
          }
          boundary = nextBoundary;
          candidate = doc.substring(0, boundary + 1);
          remainder = doc.substring(boundary + 1);

        }

        if (candidate.length() > 0) {
          sentenceArrayList.add(new Integer(currentOffset + boundary + 1));
          currentOffset += boundary + 1;
        }
        // origDoc.substring(0,currentOffset));
        // doc = doc.substring(boundary + 1);
        doc = remainder;
      }
    } while (boundary != -1);

    if (doc.length() > 0) {
      sentenceArrayList.add(new Integer(currentOffset + doc.length()));
    }

    sentenceArrayList.trimToSize();
    return sentenceArrayList;
  }

  private void setDocumentForObtainingBoundaries(String doc) {
    sentenceEndingMatcher = SentenceConstants.sentenceEndingPattern.matcher(doc);
  }

  private int getNextCandidateBoundary() {
    if (sentenceEndingMatcher.find()) {
      return sentenceEndingMatcher.start();
    } else
      return -1;

  }

  private boolean doesNotBeginWithPunctuation(String remainder) {
    Matcher m = SentenceConstants.punctuationPattern.matcher(remainder);
    return (!m.find());
  }

  private String getLastWord(String cand) {
    Matcher lastWordMatcher = SentenceConstants.lastWordPattern.matcher(cand);
    if (lastWordMatcher.find()) {
      return lastWordMatcher.group();
    } else {
      return "";
    }
  }

  /*
   * Looks at the last character of the String. If this last character is part of an abbreviation
   * (as detected by REGEX) then the sentenceString is not a fullSentence and "false" is returned
   */
  private boolean isFullSentence(String cand) {

    // cand = cand.replaceAll("\n", " "); cand = " " + cand;

    Matcher validSentenceBoundaryMatcher =
        SentenceConstants.validSentenceBoundaryPattern.matcher(cand);
    if (validSentenceBoundaryMatcher.find())
      return true;

    Matcher abbrevMatcher = SentenceConstants.abbrevPattern.matcher(cand);

    if (abbrevMatcher.find()) {
      return false; // Means it ends with an abbreviation
    } else {
      // Check if the last word of the sentenceString has an entry in the
      // abbreviations dictionary (like Mr etc.)
      String lastword = getLastWord(cand);

      if (abbreviations.contains(lastword)) {
        return false;
      }

    }

    return true;
  }
}
