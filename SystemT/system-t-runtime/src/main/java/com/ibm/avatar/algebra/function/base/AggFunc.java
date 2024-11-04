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
package com.ibm.avatar.algebra.function.base;

import java.util.Set;
import java.util.TreeSet;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.Token;

/**
 * Base class for an aggregate function for use in a selection predicate. (And later on in the
 * HAVING clause of a SELECT clause)
 */
public abstract class AggFunc extends ScalarReturningFunc {

  /** Aggregates can only (currently) have one argument. */
  protected ScalarFunc arg;

  /**
   * Main constructor, mainly present to standardize the inputs to the constructors of child
   * classes.
   * 
   * @param origTok parser token for the function name.
   * @param arg the argument to the function in the original statement, or null if no arguments.
   */
  protected AggFunc(Token origTok, ScalarFunc arg) {
    super(origTok, null == arg ? null : new ScalarFunc[] {arg});
    this.arg = arg;
  }

  /**
   * Convenience version of {@link #evaluate(TupleList, TupleList[], MemoizationTable)} for callers
   * that do not currently support locator arguments. Should be removed once all calling locations
   * support locators.
   * 
   * @throws TextAnalyticsException
   */
  public Object oldEvaluate(TupleList t, MemoizationTable mt) throws TextAnalyticsException {
    return evaluate(t, EMPTY_TL_ARRAY, mt);
  }

  /**
   * Apply the aggregate function over the indicated list of tuples, using bindings set up with
   * {@link #bind(AbstractTupleSchema)}. Unlike scalar functions, no null validation is necessary --
   * nulls are handled directly inside this function.
   * 
   * @param t the input list of tuples
   * @param locatorArgs contents of any views/tables referenced via locators inside the function
   *        call tree
   * @param mt table of state that may be needed to evaluate various functions
   * @return the value that the function returns; type of this value must match the return value of
   *         {@link #returnType()}.
   * @throws TextAnalyticsException
   */
  public abstract Object evaluate(TupleList t, TupleList[] locatorArgs, MemoizationTable mt)
      throws TextAnalyticsException;

  /**
   * Subclasses that always return the same value should override this method to return true and
   * ensure that {@link #evaluate(TupleList, MemoizationTable)} works with both arguments set to
   * null.
   * 
   * @return true
   */
  public boolean isConst() {
    return false;
  }

  /**
   * If the aggregate function always returns the same value, override this method to return that
   * value.
   * 
   * @throws TextAnalyticsException
   */
  public Object evaluateConst() throws TextAnalyticsException {
    if (false == isConst()) {
      throw new RuntimeException(
          String.format("This function (%s)" + " does not always return the same value",
              this.getClass().getName()));
    } else {
      return evaluate(null, null, null);
    }
  }

  /**
   * @return names of any relations referenced by this function or any functions it calls.
   */
  public Set<String> getReferencedRels() {
    TreeSet<String> ret = new TreeSet<String>();
    reallyGetRefRels(ret);
    return ret;
  }

  /**
   * Subclasses should replace this with a function that adds the appropriate strings to the input
   * set.
   * 
   * @param set
   */
  protected abstract void reallyGetRefRels(TreeSet<String> set);

}
