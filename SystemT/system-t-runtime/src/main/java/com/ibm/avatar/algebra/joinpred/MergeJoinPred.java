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
import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.base.ProfileRecord;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleComparator;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.algebra.function.scalar.GetBegin;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.logging.Log;

/**
 * A join predicate for use in a merge join. Provides methods for finding the range of potential
 * matches to an outer tuple, as well as determining whether a given tuple matches.
 */
public abstract class MergeJoinPred {

  /**
   * Object used to mark tokenization overhead that this predicate incurs.
   */
  protected ProfileRecord tokRecord = new ProfileRecord(Operator.TOKENIZATION_OVERHEAD_NAME, null);

  /**
   * Which columns of the outer and inner tuples we evaluate the predicate over.
   */
  protected ScalarFunc outerArg, innerArg;

  private final boolean debug = false;

  public MergeJoinPred(ScalarFunc outerArg, ScalarFunc innerArg) {
    this.outerArg = outerArg;
    this.innerArg = innerArg;
  }

  public void bind(AbstractTupleSchema outerSchema, AbstractTupleSchema innerSchema)
      throws FunctionCallValidationException {
    outerArg.oldBind(outerSchema);
    innerArg.oldBind(innerSchema);
    internalBind(outerSchema, innerSchema);
  }

  public ScalarFunc getInnerArg() {
    return innerArg;
  }

  public ScalarFunc getOuterArg() {
    return outerArg;
  }

  /**
   * Subclasses should override this method to perform any additional binding of predicates that
   * needs to be done for {@link #bind(AbstractTupleSchema, AbstractTupleSchema)} to succeed.
   */
  protected void internalBind(AbstractTupleSchema outerSchema, AbstractTupleSchema innerSchema)
      throws FunctionCallValidationException {
    // By default, do nothing.
  }

  /**
   * @return true if the inner tuple is before the range of inner tuples that could match the outer
   *         tuple.
   * @throws TextAnalyticsException
   */
  public boolean beforeMatchRange(MemoizationTable mt, TupleList outerTups, int outerIx,
      TupleList innerTups, int innerIx) throws TextAnalyticsException {
    Span outer = Span.convert(outerArg.oldEvaluate(outerTups.getElemAtIndex(outerIx), mt));
    Span inner = Span.convert(innerArg.oldEvaluate(innerTups.getElemAtIndex(innerIx), mt));

    // null tuples are sorted first, so they must be before the match range
    if ((outer == null) || (inner == null))
      return true;

    if (debug)
      Log.debug("MergeJoinPred.beforeMatchRange(%s): %s, %s, sameDoc=%s", this.getClass().getName(),
          outer, inner, outer.hasSameDocText(inner));

    // spans with unequal docText objects do not match in any position.
    if (!outer.hasSameDocText(inner)) {
      return false;
    }

    return beforeMatchRange(mt, outer, inner);
  }

  /**
   * Internal version of beforeMatchRange that applies to Annotations. Override in subclasses to
   * perform the actual predicate eval.
   */
  protected abstract boolean beforeMatchRange(MemoizationTable mt, Span outer, Span inner);

  /**
   * @return true if the inner tuple is beyond the range of inner tuples that could match the outer
   *         tuple.
   * @throws TextAnalyticsException TODO
   */
  public boolean afterMatchRange(MemoizationTable mt, TupleList outerTups, int outerIx,
      TupleList innerTups, int innerIx) throws TextAnalyticsException {
    Span outer = Span.convert(outerArg.oldEvaluate(outerTups.getElemAtIndex(outerIx), mt));
    Span inner = Span.convert(innerArg.oldEvaluate(innerTups.getElemAtIndex(innerIx), mt));

    // null tuples are sorted first, so they cannot be after the match range -- they must always be
    // before the
    // range
    if ((outer == null) || (inner == null))
      return false;

    if (debug)
      Log.debug("MergeJoinPred.afterMatchRange(%s): %s, %s, sameDoc=%s", this.getClass().getName(),
          outer, inner, outer.hasSameDocText(inner));

    // spans with unequal docText objects do not match in any position.
    if (!outer.hasSameDocText(inner)) {
      return false;
    }

    return afterMatchRange(mt, outer, inner);
  }

  protected abstract boolean afterMatchRange(MemoizationTable mt, Span outer, Span inner);

  /**
   * @return true if the pair of tuples satisfies the join predicate.
   * @throws TextAnalyticsException TODO
   */
  public boolean matchesPred(MemoizationTable mt, Tuple outerTup, Tuple innerTup)
      throws TextAnalyticsException {
    Span outer = Span.convert(outerArg.oldEvaluate(outerTup, mt));
    Span inner = Span.convert(innerArg.oldEvaluate(innerTup, mt));

    // null tuples never match -- prevent exception in predicates below
    if ((outer == null) || (inner == null))
      return false;

    if (debug)
      Log.debug("MergeJoinPred.matchesPred(%s): %s, %s, sameDoc=%s", this.getClass().getName(),
          outer, inner, outer.hasSameDocText(inner));

    // spans with unequal docText objects do not match in any position.
    if (!outer.hasSameDocText(inner)) {
      return false;
    }

    // the real matching function
    return matchesPred(mt, outer, inner);
  }

  protected abstract boolean matchesPred(MemoizationTable mt, Span outer, Span inner);

  /** Cached comparator for sorting outer tuples. */
  private Comparator<Tuple> outerComp = null;

  /**
   * @return comparator for sorting the tuples of the outer relation.
   * @throws ParseException
   */
  public Comparator<Tuple> outerSortComp() throws FunctionCallValidationException {
    if (null == outerComp) {
      outerComp = reallyGetOuterSortComp();
    }
    return outerComp;
  }

  /** Cached comparator for sorting outer tuples. */
  private Comparator<Tuple> innerComp = null;

  /**
   * @return comparator for sorting the tuples of the outer relation.
   * @throws ParseException
   */
  public Comparator<Tuple> innerSortComp() throws FunctionCallValidationException {
    if (null == innerComp) {
      innerComp = reallyGetInnerSortComp();
    }
    return innerComp;
  }

  /**
   * @return comparator for sorting the tuples of the outer relation. By default, returns a
   *         comparator that sorts by begin; override to get different behavior.
   * @throws ParseException
   */
  protected Comparator<Tuple> reallyGetOuterSortComp() throws FunctionCallValidationException {
    // Create a function call that will return the outer's begin.
    ScalarFunc getBeginFunc = new GetBegin(outerArg);
    return TupleComparator.makeComparator(getBeginFunc);
  }

  /**
   * @return comparator for sorting the tuples of the inner relation. By default, returns a
   *         comparator that sorts by begin; override to get different behavior.
   * @throws ParseException
   */
  protected Comparator<Tuple> reallyGetInnerSortComp() throws FunctionCallValidationException {
    // Create a function call that will return the inner's begin.
    return TupleComparator.makeComparator(new GetBegin(innerArg));
  }

  /**
   * Hook for subclasses to populate any additional internal data structures they need, using the
   * inner tuples as input. This method will be called once for each batch of inner tuples, prior to
   * any join processing. Override as needed.
   * 
   * @param innerList the current set of inner tuples, sorted according to this class's sort
   *        comparator
   * @param mt Memoization table; holds all thread-local state
   * @throws TextAnalyticsException
   */
  public void innerHook(TupleList innerList, MemoizationTable mt) throws TextAnalyticsException {
    // By default, do nothing.
  }

  /**
   * Hook for subclasses to populate any additional internal data structures they need, using the
   * outer tuples as input. This method will be called once for each batch of outer tuples, prior to
   * any join processing. Override as needed.
   * 
   * @param sortedInnerTups the current set of outer tuples, sorted according to this class's sort
   *        comparator
   * @param mt Memoization table; holds all thread-local state
   */
  public void outerHook(TupleList outerList, MemoizationTable mt) {
    // By default, do nothing.
  }

  /**
   * Subclasses should override this method as needed to initialize state that will be stored in the
   * memoization table.
   */
  public void initState(MemoizationTable mt) {
    // By default, do nothing.
  }

  /**
   * @return true if this predicate can be evaluated efficiently by using linear search through the
   *         input annotations.
   */
  public boolean useLinearSearch() {
    return false;
  }

  /**
   * 
   * @return true if this predicate has any reference to lemma
   */
  public boolean requiresLemma() {
    if (innerArg.requiresLemma())
      return true;
    if (outerArg.requiresLemma())
      return true;
    return false;
  }
}
