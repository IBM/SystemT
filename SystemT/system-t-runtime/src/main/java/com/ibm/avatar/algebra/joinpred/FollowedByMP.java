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

import java.util.Comparator;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleComparator;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.algebra.function.scalar.GetEnd;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;

/**
 * A version of the Follows merge join predicate with inner and outer arguments reversed.
 * 
 */
public class FollowedByMP extends MergeJoinPred {

  private int minchars, maxchars;

  public FollowedByMP(ScalarFunc outerArg, ScalarFunc innerArg, int minchars, int maxchars) {
    super(outerArg, innerArg);
    this.minchars = minchars;
    this.maxchars = maxchars;
  }

  @Override
  protected boolean afterMatchRange(MemoizationTable mt, Span outer, Span inner) {

    final boolean debug = false;

    if (debug) {
      System.err.printf("      afterMatchRange: outer = %s; inner = %s\n", outer, inner);
    }

    // The inner is past the match range if the outer's begin is before the
    // first qualifying character.
    if (outer.getBegin() < inner.getEnd() + minchars) {
      if (debug) {
        System.err.printf("      --> outer's begin (%d) is " + "before first matching char (%d)\n",
            outer.getBegin(), inner.getEnd() + minchars);
      }
      return true;
    } else {
      if (debug) {
        System.err.printf("      --> inner is before end of match range.\n");
      }
      return false;
    }
  }

  @Override
  protected boolean beforeMatchRange(MemoizationTable mt, Span outer, Span inner) {

    final boolean debug = false;

    if (debug) {
      System.err.printf("      beforeMatchRange: outer = %s; inner = %s\n", outer, inner);
    }

    // The inner is before the match range if the outer starts after the
    // first qualifying character.
    if (outer.getBegin() > inner.getEnd() + maxchars) {
      if (debug) {
        System.err.printf(
            "      --> outer's begin (%d) is " + "after last matching token begin (%d)\n",
            outer.getBegin(), inner.getEnd() + maxchars);
      }
      return true;
    } else {
      if (debug) {
        System.err.printf("      --> starts after begin of match range.\n");
      }
      return false;
    }

  }

  @Override
  protected boolean matchesPred(MemoizationTable mt, Span outer, Span inner) {
    int outerBegin = outer.getBegin();

    int innerEnd = inner.getEnd();
    int minBegin = innerEnd + minchars;
    int maxBegin = innerEnd + maxchars;

    if (outerBegin >= minBegin && outerBegin <= maxBegin) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * Inner spans need to be sorted by *end*
   * 
   * @throws ParseException
   */
  @Override
  protected Comparator<Tuple> reallyGetInnerSortComp() throws FunctionCallValidationException {
    return TupleComparator.makeComparator(new GetEnd(innerArg));
  }
}
