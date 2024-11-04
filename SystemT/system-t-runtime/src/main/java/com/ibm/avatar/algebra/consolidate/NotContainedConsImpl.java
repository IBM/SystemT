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
package com.ibm.avatar.algebra.consolidate;

import java.util.Arrays;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.logging.Log;

/**
 * Containment consolidation, implemented by scanning a sorted list of spans. Corresponds to the
 * "NotContainedWithin" consolidation type in AQL. A span is removed from the input if it contains a
 * span completely contained within it. Null spans are treated as equal (and thus contained within)
 * other null spans.
 * 
 */
public class NotContainedConsImpl extends SortingConsImpl {

  @Override
  protected void reallyConsolidate(TupleList sortedInput, TupleList results, MemoizationTable mt)
      throws TextAnalyticsException {

    boolean debug = false;

    // This method is very similar to the reallyConsolidate() method of
    // RegexConsImpl. In the future, the common code may be merged.

    if (0 == sortedInput.size()) {
      // SPECIAL CASE: Empty input
      return;
      // END SPECIAL CASE
    }

    // First, we build up a bitmask of which annotations are eliminated.
    boolean[] eliminated = new boolean[sortedInput.size()];
    Arrays.fill(eliminated, false);

    // Walk through the input one span at a time.
    int curIx = 0;
    while (curIx < sortedInput.size()) {

      Tuple curTup = sortedInput.getElemAtIndex(curIx);

      Span startSpan = Span.convert(spanGetter.oldEvaluate(curTup, mt));
      int startBegin = (startSpan == null) ? -1 : startSpan.getBegin();

      int startIx = curIx;

      // Find the shortest span that begins at the current begin,
      // Remove all longer ones, as the shortest span is contained within the longer ones
      // so all longer spans are "not" containedwithin.
      Tuple shortestTup = curTup;
      Span shortestSpan = startSpan;
      int shortestIx = curIx;

      while (curIx < sortedInput.size()) {
        Span curSpan =
            Span.convert(spanGetter.oldEvaluate(curTup = sortedInput.getElemAtIndex(curIx), mt));
        int curBegin = (curSpan == null) ? -1 : curSpan.getBegin();

        if (debug) {
          Log.debug(String.format("shortestSpan = %s, shortestTup = %s, curTup = %s, curBegin = %d",
              shortestSpan, shortestTup, curTup, curBegin));
        }

        // this commented-out portion handles lists that are sorted only by the first index,
        // and not by span length. For now, we assume the list is sorted by index AND length.
        // -- eyhung
        // Span curSpan = oldEvaluate (spanGetter.evaluate (curTup, mt));
        // if (curSpan.getEnd () < shortestSpan.getEnd ()) {
        // // Current span is shorter than our previous best
        // // Eliminate previous best (it contains a span, this one)
        // // and set the current span to be the shortest span
        // Log.info ("(Old) Eliminating entry #" + shortestIx);
        // eliminated[shortestIx] = true;
        // shortestTup = curTup;
        // shortestSpan = curSpan;
        // shortestIx = curIx;
        // }
        // else

        // current span does not begin at the same offset as start span, so stop looking
        if (curBegin != startBegin)
          break;

        if (curIx != shortestIx) {
          // Current span is equal to or greater than our previous best
          // Eliminate it because it's not contained within.
          if (debug) {
            Log.info("Eliminating entry #" + curIx);
          }
          eliminated[curIx] = true;
        }

        curIx++;
      }

      if (debug) {
        Log.debug("Shortest span is: %s\n", shortestSpan);
      }

      // Identify if this shortest span contains another, uneliminated span.
      // This is possible for spans that begin after the start span begins,
      // and end before the start span ends.
      // If so, remove the shortest span and immediately terminate the search
      // This is not possible for null spans, so skip this step if we're
      // consolidating null spans
      if (shortestSpan != null) {
        for (int i = startIx; i < sortedInput.size(); i++) {
          Span testSpan = Span.convert(spanGetter.oldEvaluate(sortedInput.getElemAtIndex(i), mt));

          // null check not necessary but added for safety
          if (testSpan != null) {
            // if tested span does not start within the shortest span, skip
            if (testSpan.getBegin() > shortestSpan.getEnd()
                || testSpan.getBegin() < shortestSpan.getBegin()) {
              continue;
            }

            // if tested span has the exact same range as the shortest span, skip
            if (testSpan.getBegin() == shortestSpan.getBegin()
                && testSpan.getEnd() == shortestSpan.getEnd()) {
              continue;
            }

            // tested span starts and ends within shortest span -- throw out the shortest span (but
            // not itself!)
            if (testSpan.getEnd() <= shortestSpan.getEnd()) {
              if (debug) {
                Log.info("Eliminating entry #" + shortestIx);
              }
              eliminated[shortestIx] = true;
              break;
            }
          }
        }
      }
    }

    // Now add all the spans that weren't eliminated.
    for (int i = 0; i < sortedInput.size(); i++) {
      if (false == eliminated[i]) {
        if (debug) {
          Log.info(String.format("Adding %s to results", sortedInput.getElemAtIndex(i)));
        }
        results.add(sortedInput.getElemAtIndex(i));
      }
    }
  }

}
