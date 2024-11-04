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
 * The inverse of the {@link ContainsMP} join predicate. Returns true if the <b>inner's</b> span
 * completely contains the <b>outer's</b>. NOTE: This predicate will only work if the outer and
 * inner are both sorted by begin!
 *
 */
public class ContainedWithinMP extends MergeJoinPred {

  public static final String FNAME = "ContainedWithin";

  /**
   * See {@link OverlapsMP} for the details of implementation.
   */
  private int endsListIx = -1;

  public ContainedWithinMP(ScalarFunc innerArg, ScalarFunc outerArg) {
    super(innerArg, outerArg);
  }

  @Override
  public void initState(MemoizationTable mt) {
    super.initState(mt);

    endsListIx = mt.createOffsetsList();
  }

  @Override
  protected boolean afterMatchRange(MemoizationTable mt, Span outer, Span inner) {
    // We assume the inputs are sorted by begin.
    int outerBegin = outer.getBegin();
    int innerBegin = inner.getBegin();

    if (innerBegin > outerBegin) {
      return true;
    } else {
      return false;
    }
  }

  @Override
  public boolean beforeMatchRange(MemoizationTable mt, TupleList outerTups, int outerIx,
      TupleList innerTups, int innerIx) throws TextAnalyticsException {
    Span outer = Span.convert(outerArg.oldEvaluate(outerTups.getElemAtIndex(outerIx), mt));
    Span inner = Span.convert(innerArg.oldEvaluate(innerTups.getElemAtIndex(innerIx), mt));

    BaseOffsetsList maxEnds = mt.getOffsetsList(endsListIx);

    // null tuples never match -- prevent exception
    if ((outer == null) || (inner == null) || (maxEnds == null))
      return false;

    // spans with unequal docText objects do not match in any position.
    if (!outer.hasSameDocText(inner)) {
      return false;
    }

    int outerBegin = outer.getBegin();
    int maxInnerEnd = maxEnds.end(innerIx);

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
    throw new RuntimeException("This method should never be called.");
  }

  @Override
  protected boolean matchesPred(MemoizationTable mt, Span outer, Span inner) {

    if (inner.getBegin() <= outer.getBegin() && inner.getEnd() >= outer.getEnd()) {
      return true;
    } else {
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

    int curMax = 0;
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
    }
  }

}
