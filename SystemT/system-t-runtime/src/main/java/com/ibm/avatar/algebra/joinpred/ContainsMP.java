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
import com.ibm.avatar.algebra.function.base.ScalarFunc;

/**
 * The merge join version of the {@link com.ibm.avatar.algebra.function.predicate.Contains}
 * selection predicate. Returns true if the outer's span completely contains the inner. NOTE: This
 * predicate will only work if the outer and inner are both sorted by begin!
 * 
 */
public class ContainsMP extends MergeJoinPred {

  public ContainsMP(ScalarFunc innerArg, ScalarFunc outerArg) {
    super(innerArg, outerArg);

  }

  @Override
  protected boolean afterMatchRange(MemoizationTable mt, Span outer, Span inner) {
    // We assume the inputs are sorted by begin.
    int outerEnd = outer.getEnd();
    int innerBegin = inner.getBegin();

    if (innerBegin > outerEnd) {
      return true;
    } else {
      return false;
    }
  }

  @Override
  protected boolean beforeMatchRange(MemoizationTable mt, Span outer, Span inner) {
    int outerBegin = outer.getBegin();
    int innerBegin = inner.getBegin();

    if (innerBegin < outerBegin) {
      return true;
    } else {
      return false;
    }
  }

  @Override
  protected boolean matchesPred(MemoizationTable mt, Span outer, Span inner) {

    if (outer.getBegin() <= inner.getBegin() && outer.getEnd() >= inner.getEnd()) {
      return true;
    } else {
      return false;
    }
  }
}
