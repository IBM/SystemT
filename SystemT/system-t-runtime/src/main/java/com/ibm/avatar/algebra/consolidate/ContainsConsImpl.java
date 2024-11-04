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

/**
 * Containment consolidation, implemented by scanning a sorted list of spans. Corresponds to the
 * "ContainedWithin" consolidation type in AQL, which is the default if no type is specified. A span
 * is removed from the input if it is completely contained within another span. Null spans are
 * treated as contained within other null spans.
 * 
 */
public class ContainsConsImpl extends SortingConsImpl {

  @Override
  protected void reallyConsolidate(TupleList sortedInput, TupleList results, MemoizationTable mt)
      throws TextAnalyticsException {
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

      // Find the longest span that begins at the start tuple's beginning.
      // and advance the curIx to the first tuple that does not begin at the
      // start tuple's beginning
      Tuple longestTup = curTup;
      Span longestSpan = startSpan;
      while (curIx < sortedInput.size()) {
        curTup = sortedInput.getElemAtIndex(curIx);
        Span curSpan = Span.convert(spanGetter.oldEvaluate(curTup, mt));
        int curSpanBegin = (curSpan == null) ? -1 : curSpan.getBegin();

        // the current span does not begin at the current begin; this is a sorted list so
        // this and all subsequent spans no longer need to be checked
        if (curSpanBegin != startBegin)
          break;

        if ((curSpan != null) && (longestSpan != null)) {
          if (curSpan.getEnd() > longestSpan.getEnd()) {
            // Current span is longer than our previous best
            longestTup = curTup;
            longestSpan = curSpan;
          }
        }

        curIx++;
      }

      // System.err.printf("Longest span is: %s\n", longestSpan);

      // Remove every other span that is contained within the current
      // longest span.
      for (int i = startIx; i < sortedInput.size(); i++) {
        Span thisSpan = Span.convert(spanGetter.oldEvaluate(sortedInput.getElemAtIndex(i), mt));

        // null span elimination
        if ((thisSpan == null)) {
          if (i == startIx) {
            // leave the start null span alone, as one null span needs to survive
          } else {
            // this span is null and it's not the start null span, so eliminate it
            eliminated[i] = true;
          }
        }
        // null span elimination, but thisSpan not null
        else if (longestSpan == null) {
          // non-null spans start after all null spans,
          // so stop checking for spans to eliminate
          break;
        }
        // non-null span elimination
        else {
          if (thisSpan.getBegin() > longestSpan.getEnd()) {
            // this span starts after the end of the current longest span, so
            // stop checking for spans to eliminate
            break;
          }
          if (thisSpan.getEnd() <= longestSpan.getEnd()
              && sortedInput.getElemAtIndex(i) != longestTup) {
            eliminated[i] = true;
          }
        }
      }
    }

    // Now add all the spans that weren't eliminated.
    for (int i = 0; i < sortedInput.size(); i++) {
      if (false == eliminated[i]) {
        results.add(sortedInput.getElemAtIndex(i));
      }
    }
  }
}
