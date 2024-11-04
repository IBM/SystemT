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
package com.ibm.avatar.algebra.datamodel;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;

/**
 * Default, "safe" implementation of a tuple comparator; evaluates a function call tree on every
 * input tuple.
 */
public class SlowComparator extends TupleComparator {

  /**
   * Function that returns the target scalar value for comparison, given a tuple.
   */
  private final ScalarFunc targetFunc;

  /** Object that knows how to compare the scalar values our function returns. */
  private final ScalarComparator comp;

  /**
   * Reference to current thread's memoization table.
   */
  protected MemoizationTable memoizationTable;

  protected SlowComparator(ScalarFunc target) throws FunctionCallValidationException {
    this.targetFunc = target;

    comp = ScalarComparator.createComparator(targetFunc.returnType());
  }

  @Override
  public int compare(Tuple t1, Tuple t2) {
    Object o1, o2;
    try {
      o1 = targetFunc.oldEvaluate(t1, memoizationTable);
      o2 = targetFunc.oldEvaluate(t2, memoizationTable);
    } catch (TextAnalyticsException e) {
      throw new FatalInternalError(e);
    }

    return comp.compare(o1, o2);
  }

  /**
   * Set a value to memoization table reference
   * 
   * @param memoizationTable the memoizationTable to set
   */
  public synchronized void setMemoizationTable(MemoizationTable memoizationTable) {
    this.memoizationTable = memoizationTable;
  }

}
