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
package com.ibm.avatar.algebra.joinpred;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.algebra.util.tokenize.BaseOffsetsList;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;

/**
 * The merge join version of the {@link com.ibm.avatar.algebra.function.predicate.Overlaps}
 * selection predicate. Returns true if the outer and inner spans overlap. This predicate is
 * symmetrical with respect to the order of its input, so no reversed version is necessary. Sorts
 * both inputs by begin.
 * 
 */
public class OverlapsMP extends MergeJoinPred {

  /**
   * We use an OffsetsList to store some information for finding the range of inner tuples for a
   * given outer. In particular, the "end" attribute of index i of the list contains the maximum end
   * offset of any inner span with an index less than or equal to i. This variable tells the index
   * of this OffsetsList within the MemoizationTable.
   */
  private int endsListIx = -1;

  /**
   * Array of extra data for finding the range of inner tuples for a given outer. Index i contains
   * the maximum end offset of any inner span with an index less than or equal to i.
   */
  // private int[] maxEnds = new int[1];
  public OverlapsMP(ScalarFunc innerArg, ScalarFunc outerArg) {
    super(innerArg, outerArg);

  }

  @Override
  public void initState(MemoizationTable mt) {
    super.initState(mt);

    // Create the offsets list that we use to store information that speeds
    // up locating the matches for an outer.
    endsListIx = mt.createOffsetsList();
  }

  @Override
  protected boolean afterMatchRange(MemoizationTable mt, Span outer, Span inner) {
    // We assume the inputs are sorted by begin, which is the default.
    int outerEnd = outer.getEnd();
    int innerBegin = inner.getBegin();

    if (innerBegin > outerEnd) {
      return true;
    } else {
      return false;
    }
  }

  @Override
  public boolean beforeMatchRange(MemoizationTable mt, TupleList outerTups, int outerIx,
      TupleList innerTups, int innerIx) throws TextAnalyticsException {
    Span outer = Span.convert(outerArg.oldEvaluate(outerTups.getElemAtIndex(outerIx), mt));

    BaseOffsetsList maxEnds = mt.getOffsetsList(endsListIx);

    // null tuples never match -- prevent exception
    if ((outer == null) || (maxEnds == null))
      return false;

    int outerBegin = outer.getBegin();
    int maxInnerEnd = maxEnds.end(innerIx);
    // maxEnds[innerIx];

    // We're before the match range if the maximum inner end comes before
    // the begin of the outer.
    if (maxInnerEnd < outerBegin) {
      return true;
    } else {
      return false;
    }
  }

  @Override
  protected boolean beforeMatchRange(MemoizationTable mt, Span outer, Span inner) {
    // We override the public version of beforeMatchRange to use our
    // internal table.
    throw new RuntimeException("This method should never be called.");
  }

  @Override
  protected boolean matchesPred(MemoizationTable mt, Span outer, Span inner) {

    if (inner.getBegin() >= outer.getBegin() && inner.getBegin() < outer.getEnd()) {
      // CASE 1: Inner starts inside the outer; overlap!
      return true;
    } else if (outer.getBegin() >= inner.getBegin() && outer.getBegin() < inner.getEnd()) {
      // CASE 2: Outer starts inside the inner.
      return true;
    } else {
      // CASE 3: Neither span starts inside the other, so there can be no
      // overlap.
      return false;
    }
  }

  @Override
  public void innerHook(TupleList sortedInnerTups, MemoizationTable mt)
      throws TextAnalyticsException {
    // Build up our table of upper bounds on inner span ends.
    // The table is now stored in the MemoizationTable for thread-safety.
    BaseOffsetsList maxEnds = mt.getOffsetsList(endsListIx);
    maxEnds.reset();
    // Old code:
    // if (maxEnds.length < sortedInnerTups.size()) {
    // maxEnds = new int[sortedInnerTups.size()];
    // }

    int curMax = 0;
    // int i = 0;
    for (TLIter innerItr = sortedInnerTups.iterator(); innerItr.hasNext();) {
      Tuple innerTup = innerItr.next();
      Span inner = Span.convert(innerArg.oldEvaluate(innerTup, mt));

      int curEnd = 0;
      if (inner == null)
        curEnd = 0;
      else
        curEnd = inner.getEnd();

      curMax = Math.max(curMax, curEnd);
      maxEnds.addEntry(0, curMax);
      // Old code:
      // maxEnds[i++] = curMax;
    }
  }
}
