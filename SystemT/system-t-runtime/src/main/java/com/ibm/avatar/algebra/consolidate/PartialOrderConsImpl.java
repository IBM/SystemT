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
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.logging.Log;

/**
 * Implementation of consolidation that applies a partial order to every pair of input spans.
 * 
 */
public class PartialOrderConsImpl extends ConsolidateImpl {

  private PartialOrder<Span> order;

  protected PartialOrderConsImpl(String name) {
    order = PartialOrder.strToOrderObj(name);
  }

  @Override
  public TupleList consolidate(TupleList input, MemoizationTable mt) throws TextAnalyticsException {
    TupleList ret = new TupleList(input.getSchema());

    boolean debug = false;

    // SPECIAL CASE: Zero- or one-element input
    if (0 == input.size()) {
      return ret;
    } else if (1 == input.size()) {
      // One tuple input -- pass the single tuple through, but pin it.
      Tuple inTup = input.getElemAtIndex(0);
      ret.add(inTup);

      return ret;
    }
    // END SPECIAL CASE

    // We currently use a brute-force approach:
    // We build up the output set one element at a time. Before an element
    // is added to the undominated set, it needs to be compared against
    // every other element in the input set.
    //
    // Naturally, many of these comparisons could be avoided, given some
    // information about the partial order. However, the number of input
    // elements is usually quite small, so such an optimization is currently
    // not necessary.

    // We need an index into the input to break ties.
    int candidateIndex = 0;
    for (TLIter itr = input.iterator(); itr.hasNext();) {
      candidateIndex++;
      Tuple candidateTuple = itr.next();

      Span candidate = Span.convert(spanGetter.oldEvaluate(candidateTuple, mt));

      // Compare the candidate against every other item (except itself) to
      // determine whether the candidate belongs in the set
      boolean couldBeInSet = true;

      int otherIndex = 0;
      TLIter jtr = input.newIterator();

      while (jtr.hasNext() && couldBeInSet) {
        otherIndex++;
        Span other = Span.convert(spanGetter.oldEvaluate(jtr.next(), mt));

        if (otherIndex != candidateIndex) {
          // Try to disqualify the candidate.
          if (debug) {
            Log.debug("Comparing %s with %s", candidate, other);
          }

          // If two elements are equal, we choose the one with the
          // smaller index.
          if (order.eq(candidate, other) && candidateIndex > otherIndex) {
            if (debug) {
              Log.debug("-->Disqualified by eq and index");
            }

            couldBeInSet = false;
          } else if (order.gt(other, candidate)) {
            if (debug) {
              Log.debug("-->Disqualified by gt");
            }
            couldBeInSet = false;
          }
        }
      }

      if (couldBeInSet) {
        ret.add(candidateTuple);
      }
    }

    return ret;
  }
}
