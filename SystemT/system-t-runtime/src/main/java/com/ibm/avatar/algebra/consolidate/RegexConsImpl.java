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

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;

/**
 * Consolidation that emulates the semantics of most regular expression engines. If priority is not
 * used,when spans overlap, take the leftmost longest non-overlapping span. If priority is used,
 * then when spans overlap take the leftmost "highest priority" span. If there are multiple spans
 * under this condition, then chose the longest non-overlapping span. Which tuple has "highest
 * priority" depends on the priority direction. If priorityDirection = "ascending", then "1" > "2".
 * If it is descending then "1"< 2". The default is ascending.
 */

public class RegexConsImpl extends SortingConsImpl {

  @Override
  protected void reallyConsolidate(TupleList sortedInput, TupleList results, MemoizationTable mt)
      throws TextAnalyticsException {

    if (0 == sortedInput.size()) {
      // SPECIAL CASE: Empty input
      return;
      // END SPECIAL CASE
    }

    // Walk through the input one span at a time.
    int curIx = 0;
    Boolean hasPriority = false;
    // FieldType longestFType = null;
    FieldType ftype = null;
    if (fieldGetter != null) {
      hasPriority = true;
      ftype = fieldGetter.returnType();
    }

    while (curIx < sortedInput.size()) {

      Tuple curTup = sortedInput.getElemAtIndex(curIx);

      Span startSpan = Span.convert(spanGetter.oldEvaluate(curTup, mt));
      int startBegin = (startSpan == null) ? -1 : startSpan.getBegin();

      // best tuple so far, used to compare tuples that start at the same location
      Tuple bestTup = curTup;

      while (curIx < sortedInput.size()) {
        Span curSpan =
            Span.convert(spanGetter.oldEvaluate(curTup = sortedInput.getElemAtIndex(curIx), mt));
        int curBegin = (curSpan == null) ? -1 : curSpan.getBegin();

        if (curBegin != startBegin)
          break;

        if (curSpan == null) {
          curIx++;
          continue;
        }

        // Will treat the case where priority is not considered the same as the case when all tuples
        // have the same
        // priority.
        // priorityComp = 0 when the two tuples have the same priority value or when priority is not
        // considered

        int priorityComp = 0;
        if (hasPriority) {
          Object current = fieldGetter.oldEvaluate(curTup, mt);
          Object best = fieldGetter.oldEvaluate(bestTup, mt);

          if (ftype.getIsText()) {
            priorityComp = Text.convert(current).compareTo(Text.convert(best));
          } else if (ftype.getIsFloatType()) {
            priorityComp = ((Float) current).compareTo((Float) best);
          } else if (ftype.getIsIntegerType()) {
            priorityComp = ((Integer) current).compareTo((Integer) best);
          }
          // now adjust the meaning of "priorityComp". It is first calculated as "1" < "2". If
          // priorityDirection is
          // ascending, then it should switch to "1" > "2". In this case we just switch the sign of
          // priorityComp
          if (priorityDirection.compareTo("descending") != 0) { // if ascending
            if (priorityComp > 0)
              priorityComp = -1;
            else if (priorityComp < 0)
              priorityComp = 1;
          }

        }

        if (priorityComp > 0) {
          bestTup = curTup;

        } else if (priorityComp == 0) {
          curSpan = Span.convert(spanGetter.oldEvaluate(curTup, mt));
          Span bestSpan = Span.convert(spanGetter.oldEvaluate(bestTup, mt));
          if (curSpan.getEnd() > bestSpan.getEnd()) {
            // Current span is longer than our previous best
            bestTup = curTup;
          }
        }

        curIx++;
      }

      // The longest span gets kept; others are discarded.
      results.add(bestTup);

      // Find the next span that starts beyond the end of the current
      // span, and repeat the process.
      // Not needed for null spans as curIx has already been correctly set to the first
      // non-null span in the previous loop
      if (startSpan != null) {
        int curEnd = (Span.convert(spanGetter.oldEvaluate(bestTup, mt))).getEnd();
        while (curIx < sortedInput.size()
            && (Span.convert(spanGetter.oldEvaluate(sortedInput.getElemAtIndex(curIx), mt)))
                .getBegin() < curEnd) {
          curIx++;
        }
      }
    }

  }
}
