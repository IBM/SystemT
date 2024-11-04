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
package com.ibm.avatar.algebra.function.scalar;

import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.SlowComparator;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleComparator;
import com.ibm.avatar.algebra.function.base.ScalarReturningFunc;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;

/**
 * "Fast-path" implementation of tuple comparison. Supports limited functionality but runs fast.
 * Resides in the same package as {@link GetCol} because it peeks inside instances of that class.
 */
public class FastComparator extends TupleComparator {

  /**
   * Function call that returns the column we want to compare over; this function may not be
   * initialized when the comparator is created, so we keep a pointer around until the first time
   * the comparator is invoked, then pull the column getter object out of it.
   */
  private final GetCol getColFunc;

  /**
   * Accessor that we pull out of {@link #getColFunc} to enable direct access to tuple attributes.
   */
  private FieldGetter<Object> colAcc = null;

  /** Attributes of a span on which this class supports sorting. */
  enum SpanAttrs {
    BEGIN, END, BOTH
  }

  private SpanAttrs attr;

  /**
   * @param getColFunc original function (that would have been passed to {@link SlowComparator}) for
   *        fetching a span column from a tuple
   * @param getAttrFunc function that would have retrieved the appropriate attribute from the
   *        returned span, or null if the span is itself the sorting key
   * @throws FunctionCallValidationException
   */
  public FastComparator(GetCol getColFunc, ScalarReturningFunc getAttrFunc)
      throws FunctionCallValidationException {

    // Note by Huaiyu Zhu, 2013-09
    // Fast comparator is supposed to compare span only.
    // The TupleComparator makeComparator method decides when to use fast or slow comparators.
    //
    // However, there are cases where it receives GetBegin(GetCol(Text)).
    // There are three possible treatment here:
    // 1. Disallow it. The following commented out block of code does the check.
    // The corresponding code in makeComparator need to be modified.
    // 2. Do automatic conversion of Text to Span. This is adopted here.
    // We rely on the caching of the converted Span object minimize performance impact.
    // 3. Treat Span and Text with separate code path within this class.
    // This may well be the final choice.

    // if (! getColFunc.returnType ().getIsSpan ()) { throw new FatalInternalError (
    // "FastComparator expects the getColFunc to return Span, not just a type convertable to Span.
    // The actual return type is "
    // + getColFunc.returnType ()); }

    this.getColFunc = getColFunc;
    if (null == getAttrFunc) {
      // Sorting on a span
      this.attr = SpanAttrs.BOTH;
    } else if (getAttrFunc instanceof GetBegin) {
      this.attr = SpanAttrs.BEGIN;
    } else if (getAttrFunc instanceof GetEnd) {
      this.attr = SpanAttrs.END;
    } else {
      throw new RuntimeException("Can't convert " + getAttrFunc);
    }

  }

  /**
   * Comment by Huaiyu Zhu 2013-08.
   * <p>
   * We assume that in the field to be compared contains either Text or Span. Text objects will be
   * converted to Spans for comparison purpose.
   */
  @Override
  public int compare(Tuple tup1, Tuple tup2) {
    if (null == colAcc) {
      // This method can be called from multiple threads!
      synchronized (this) {
        colAcc = getColFunc.getColAcc();
      }
    }

    Span s1 = Span.convert(colAcc.getVal(tup1));
    Span s2 = Span.convert(colAcc.getVal(tup2));

    // null loses all comparisons except to other nulls
    if ((s1 == null) && (s2 == null)) {
      return 0;
    }

    // first argument is null, so first argument is less than the second
    if (s1 == null) {
      return -1;
    }

    // second argument is null, so first argument is greater than the second
    if (s2 == null) {
      return 1;
    }

    // Make sure that the spans aren't on different target text
    Text text1 = s1.getDocTextObj();
    Text text2 = s2.getDocTextObj();

    // SPECIAL CASE: Spans against different Text objects.
    if (text1 != text2) {
      // Return comparison outcome only when the corresponding String contents are not equal
      int textComparisonOutcome = Text.compareTexts(text1, text2);
      if (textComparisonOutcome != 0)
        return textComparisonOutcome;
    }

    switch (attr) {
      case BEGIN:
        return s1.getBegin() - s2.getBegin();

      case END:
        return s1.getEnd() - s2.getEnd();

      case BOTH: {

        int val = s1.getBegin() - s2.getBegin();
        if (0 != val)
          return val;
        return s1.getEnd() - s2.getEnd();
      }

      default:
        throw new RuntimeException("Don't know how to get attr " + attr);
    }
  }
}
