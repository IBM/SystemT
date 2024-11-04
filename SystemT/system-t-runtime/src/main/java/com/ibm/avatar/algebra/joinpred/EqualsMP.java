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
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.ScalarComparator;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleComparator;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;

/**
 * Simple equality merge join.
 * 
 */
public class EqualsMP extends MergeJoinPred {

  private static final boolean debug = false;

  /**
   * Comparator for sorting the outer arguments; initialized in
   * {@link #internalBind(AbstractTupleSchema, AbstractTupleSchema)}
   */
  private TupleComparator outerSortComp = null;

  private TupleComparator innerSortComp = null;

  /** Comparator for looking at individual scalar values from different tuples. */
  private ScalarComparator valueComp = null;

  public EqualsMP(ScalarFunc innerArg, ScalarFunc outerArg) {
    super(innerArg, outerArg);
  }

  @Override
  protected void internalBind(AbstractTupleSchema outerSchema, AbstractTupleSchema innerSchema)
      throws FunctionCallValidationException {

    FieldType outerType = outerArg.returnType();
    FieldType innerType = innerArg.returnType();

    // Verify that the inputs have the same type
    if (false == outerType.equals(innerType)) {
      throw new FunctionCallValidationException(
          "Arguments to Equals() return different types (%s and %s)", outerType, innerType);
    }

    // Generate a comparator for sorting the input tuples.
    outerSortComp = TupleComparator.makeComparator(outerArg);
    innerSortComp = TupleComparator.makeComparator(innerArg);

    valueComp = ScalarComparator.createComparator(outerType);
  }

  @Override
  public boolean beforeMatchRange(MemoizationTable mt, TupleList outerTups, int outerIx,
      TupleList innerTups, int innerIx) throws TextAnalyticsException {
    Object outer = outerArg.oldEvaluate(outerTups.getElemAtIndex(outerIx), mt);
    Object inner = innerArg.oldEvaluate(innerTups.getElemAtIndex(innerIx), mt);

    int val = valueComp.compare(outer, inner);

    // System.err.printf(" beforeMatchRange(): "
    // + "'%s'.compareTo('%s') returns %d\n", outer, inner, val);

    if (val > 0) {
      return true;
    } else {
      return false;
    }
  }

  @Override
  public boolean afterMatchRange(MemoizationTable mt, TupleList outerTups, int outerIx,
      TupleList innerTups, int innerIx) throws TextAnalyticsException {
    Object outer = outerArg.oldEvaluate(outerTups.getElemAtIndex(outerIx), mt);
    Object inner = innerArg.oldEvaluate(innerTups.getElemAtIndex(innerIx), mt);

    int val = valueComp.compare(outer, inner);

    // System.err.printf(" afterMatchRange(): "
    // + "'%s'.compareTo('%s') returns %d\n", outer, inner, val);

    if (val < 0) {
      return true;
    } else {
      return false;
    }
  }

  @Override
  public boolean matchesPred(MemoizationTable mt, Tuple outerTup, Tuple innerTup)
      throws TextAnalyticsException {

    Object outer = outerArg.oldEvaluate(outerTup, mt);
    Object inner = innerArg.oldEvaluate(innerTup, mt);

    if (debug) {
      System.err.printf("EqualsMP:\n" + "    outer = %s\n" + "    inner = %s\n", outer, inner);
    }

    int val = valueComp.compare(outer, inner);

    if (debug) {
      System.err.printf("    ==> Comparison function returns %d\n", val);
    }

    return (0 == val);
  }

  @Override
  protected Comparator<Tuple> reallyGetInnerSortComp() {
    return innerSortComp;
  }

  @Override
  protected Comparator<Tuple> reallyGetOuterSortComp() {
    return outerSortComp;
  }

  @Override
  protected boolean afterMatchRange(MemoizationTable mt, Span outer, Span inner) {
    // We override the public version of afterMatchRange to handle types
    // other than Span
    throw new RuntimeException("This method should never be called.");
  }

  @Override
  protected boolean beforeMatchRange(MemoizationTable mt, Span outer, Span inner) {
    // We override the public version of beforeMatchRange to handle types
    // other than Span
    throw new RuntimeException("This method should never be called.");
  }

  @Override
  protected boolean matchesPred(MemoizationTable mt, Span outer, Span inner) {
    // We override the public version of matchesPred to handle types
    // other than Span
    throw new RuntimeException("This method should never be called.");
  }

  @Override
  public boolean useLinearSearch() {
    return true;
  }

}
