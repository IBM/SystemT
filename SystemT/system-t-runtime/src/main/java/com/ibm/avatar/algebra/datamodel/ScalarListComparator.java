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

import java.util.ArrayList;

import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.algebra.function.base.ScalarReturningFunc;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;

/**
 * Implementation of a tuple comparator for the case when the sort key is a list of scalar
 * functions; Relies on ScalarComparator to compare on each key individually.
 */
public class ScalarListComparator extends TupleComparator {

  /**
   * List of functions that return the target scalar values for comparison, given a tuple.
   */
  private final ArrayList<ScalarFunc> targetFuncs;

  /** We have one ScalarComparator for each target function. */
  private final ArrayList<ScalarComparator> comps;

  protected ScalarListComparator(ArrayList<ScalarFunc> target)
      throws FunctionCallValidationException {
    this.targetFuncs = target;
    this.comps = new ArrayList<ScalarComparator>();

    for (ScalarReturningFunc targetFunc : targetFuncs) {
      ScalarComparator comp = ScalarComparator.createComparator(targetFunc.returnType());
      comps.add(comp);

    }
  }

  @Override
  public int compare(Tuple t1, Tuple t2) {

    ScalarFunc targetFunc;
    ScalarComparator comp;

    for (int i = 0; i < targetFuncs.size(); i++) {

      targetFunc = targetFuncs.get(i);

      Object o1, o2;
      try {
        o1 = targetFunc.oldEvaluate(t1, null);
        o2 = targetFunc.oldEvaluate(t2, null);
      } catch (TextAnalyticsException e) {
        // Comparable<T>.compare() has no exceptions, so convert to unchecked exception
        throw new FatalInternalError(e);
      }

      // Get the corresponding comparator
      comp = comps.get(i);

      // Do the comparison for this target function
      int result = comp.compare(o1, o2);

      // Tuples differ in the value for this target function
      if (result != 0)
        return result;

    }

    // Equal tuples (no difference in any of the target functions)
    return 0;

  }
}
