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
package com.ibm.systemt.regex.test;

import java.util.Arrays;

import com.ibm.systemt.regex.api.SimpleRegex;
import com.ibm.systemt.regex.api.SimpleRegexMatcher;

public class CheckForMatches {

  /**
   * Example program that uses the SimpleRegex API to check for matches in multiple regexes in a
   * single pass over a piece of text. This program does not attempt to return the locations of the
   * matches, simply whether a match exists.
   * 
   * @param args no arguments, though systemT_regex.jar needs to be in your classpath.
   */
  public static void main(String[] args) throws Exception {

    // The set of regular expressions to evaluate.
    final String[] expressions = {
        // Some test expressions that match the target text below
        "VISA (INT`L|INTERNATIONAL)", "JP[ ]?MORGAN( CHASE BANK)?[^\\{]{0,10}NEW YORK",

        // Expressions from Hamid's SameTime message; none of these
        // actually matches the target text.
        // Note that I had to escape a '{' in the first expression to
        // make it compile with SimpleRegex.
        "U[ .]?S[ .]? ?Bank[^\\{]{0,10}MINNEAPOLIS MN",
        "\\{3400\\}.{9}.{0,11}U[ .]?S[ .]? ?BANK.{0,11}\\*",
        "(\\{3400\\}.{9}.{0,11}U[ .]?S[ .]? ?BANK.{0,11}\\*)|(\\{4200\\}.{7}.{0,34}U[ .]?S[ .]? ?BANK.{0,34}\\*)"//
    };

    // The text to match against.
    final String targetText =
        "20081105D1B74P5C000287XFT811 {1500}0205001862T {1510}1032{1520}20081105D1B74P5C000287{2000}000003763745{3100}041001039KEY GR LAKES CLEVE*{3320}2008110500001862*{3400}021000021JPMCHASE NYC*{3600}DRW{4200}D00000009102439081*VISA INT`L BASE II SETTLEMENT*900 METRO CENTER BLVD M1-6C*FOSTER CITY CA 94404-*{4320}0016988351*{5000}D9102439081*VISA INT`L BASE II SETTLEMENT*900 METRO CENTER BLVD M1-6C*FOSTER CITY CA 94404-*{5100}F021000021*JP MORGAN CHASE BANK*NEW YORK*NEW YORK*{5200}D359681147203*SE GTWY SETTLEMENT ACCOUNT*495 N KELLER RD STE 500*MAITLAND FL 32751-8657*{6000}1000091927 VSS SETTLEMENT OF*{6500}SRE 1000091927 VSS - FTD 11-05*-2008* ";

    // The code that follows assumes that none of the input regular
    // expressions match an empty string. For safety, verify that this
    // condition holds.
    for (String expr : expressions) {
      if ("".matches(expr)) {
        throw new RuntimeException(
            String.format("Regular expression:\n" + "%s\n" + "matches the empty string", expr));
      }
    }

    // SimpleRegex's API tells the length of the longest match (if any)
    // starting at a particular location in the target text.
    // We're only interested in whether a match exists, so modify the
    // regular expressions to match any string that ends with a regex match.
    // That way we can check for matches with one call to the SimpleRegex
    // API.
    String[] modifiedExpressions = new String[expressions.length];
    for (int i = 0; i < expressions.length; i++) {
      modifiedExpressions[i] = String.format(".*(%s)", expressions[i]);
    }

    // Need compile flags for the expressions; just use the defaults.
    int[] flags = new int[modifiedExpressions.length];
    Arrays.fill(flags, 0);

    // Now compile the modified regular expressions into a single finite
    // state machine.
    SimpleRegex regex = new SimpleRegex(modifiedExpressions, flags);

    // Create a single reusable matcher object.
    SimpleRegexMatcher matcher = (SimpleRegexMatcher) regex.matcher("");

    // Now we can do the matching. Determine the longest match of each
    // modified regular expression, starting at the beginning of the string.
    // Remember, the modified regexes match:
    // [any sequence of chars][string that the original regex matches]
    matcher.reset(targetText);
    matcher.region(0, targetText.length());
    int[] matchLengths = matcher.getAllMatches();

    // Determine which regular expressions matched.
    for (int i = 0; i < matchLengths.length; i++) {
      System.out.printf("Regex: %s\n", expressions[i]);
      int matchLen = matchLengths[i];
      if (-1 == matchLen) {
        System.out.printf("  --> No match\n");
      } else {
        System.out.printf("  --> match ending at position %d\n", matchLen);
      }
    }
  }
}
