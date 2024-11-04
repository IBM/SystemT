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
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;

/**
 * Consolidation that only removes spans that are exactly the same as other spans, with no guarantee
 * on which of the duplicates is returned. Null spans are exactly the same as other null spans.
 * 
 */
public class ExactMatchConsImpl extends SortingConsImpl {

  @Override
  protected void reallyConsolidate(TupleList sortedInput, TupleList results, MemoizationTable mt)
      throws TextAnalyticsException {

    if (0 == sortedInput.size()) {
      // SPECIAL CASE: Empty input
      return;
      // END SPECIAL CASE
    } else {
      // Have at least one tuple; go through the tuples
      Tuple curTup = sortedInput.getElemAtIndex(0);
      Span curSpan = Span.convert(spanGetter.oldEvaluate(curTup, mt));

      for (int i = 1; i < sortedInput.size(); i++) {
        Tuple nextTup = sortedInput.getElemAtIndex(i);

        Span nextSpan = Span.convert(spanGetter.oldEvaluate(nextTup, mt));

        int curBegin = (curSpan == null) ? -1 : curSpan.getBegin();
        int curEnd = (curSpan == null) ? -1 : curSpan.getEnd();

        int nextBegin = (nextSpan == null) ? -1 : nextSpan.getBegin();
        int nextEnd = (nextSpan == null) ? -1 : nextSpan.getEnd();

        if ((curBegin != nextBegin) || (curEnd != nextEnd)) {
          // Found a span that is different from the current one, so
          // we can safely add the current one to the output and
          // continue.
          results.add(curTup);
          curTup = nextTup;
          curSpan = nextSpan;
        } else {
          // System.err.printf("Filtering duplicate span %s\n", nextSpan);
        }
      }

      results.add(curTup);
    }
  }
}
