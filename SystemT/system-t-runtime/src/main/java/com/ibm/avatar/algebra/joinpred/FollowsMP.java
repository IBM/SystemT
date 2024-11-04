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
 * Merge join predicate that returns true if the inner follows the outer by a certain range of
 * numbers of characters. In particular, the predicate takes four parameters:
 * <ul>
 * <li>Two column indexes; the indicated columns must contain Annotation objects
 * <li>A minimum and maximum number of chars, INCLUSIVE, that can be between the outer and inner
 * annotations in order for the predicate to return true.
 * </ul>
 * 
 */
public class FollowsMP extends MergeJoinPred {

  private int minchars, maxchars;

  public FollowsMP(ScalarFunc outerArg, ScalarFunc innerArg, int minchars, int maxchars) {
    super(outerArg, innerArg);
    this.minchars = minchars;
    this.maxchars = maxchars;
  }

  @Override
  protected boolean afterMatchRange(MemoizationTable mt, Span outer, Span inner) {

    final boolean debug = false;

    // The inner is past the match range if it starts after the last
    // qualifying character.
    if (inner.getBegin() > outer.getEnd() + maxchars) {
      if (debug) {
        System.err.printf("      --> inner's begin (%d) is " + "after last matching char (%d)\n",
            inner.getBegin(), outer.getEnd() + maxchars);
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

    // The inner is before the match range if it starts before the first
    // qualifying character.
    if (inner.getBegin() < outer.getEnd() + minchars) {
      if (debug) {
        System.err.printf(
            "      --> inner's begin (%d) is " + "before first matching token begin (%d)\n",
            inner.getBegin(), outer.getEnd() + minchars);
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

    int innerBegin = inner.getBegin();

    int outerEnd = outer.getEnd();
    int minBegin = outerEnd + minchars;
    int maxBegin = outerEnd + maxchars;

    if (innerBegin >= minBegin && innerBegin <= maxBegin) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * Outer spans need to be sorted by *end*
   * 
   * @throws ParseException
   */
  @Override
  protected Comparator<Tuple> reallyGetOuterSortComp() throws FunctionCallValidationException {
    return TupleComparator.makeComparator(new GetEnd(outerArg));
  }
}
