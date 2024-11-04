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
import java.util.Comparator;

import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.algebra.function.base.ScalarReturningFunc;
import com.ibm.avatar.algebra.function.scalar.FastComparator;
import com.ibm.avatar.algebra.function.scalar.GetBegin;
import com.ibm.avatar.algebra.function.scalar.GetCol;
import com.ibm.avatar.algebra.function.scalar.GetEnd;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;

/**
 * Interface for comparators that are used when sorting sets of tuples. Provides a hook to bind to a
 * tuple schema.
 */
public abstract class TupleComparator implements Comparator<Tuple> {

  /**
   * Factory method for creating a comparator. Will attempt to use {@link FastComparator} when
   * possible.
   * 
   * @throws FunctionCallValidationException if the target of comparison doesn't compile
   */
  public static TupleComparator makeComparator(ScalarFunc target)
      throws FunctionCallValidationException {

    // Check whether the sort key is the begin or end of a span. If
    // we're sorting directly on a span column of the tuple, then we can
    // use FastComparator.
    if (target instanceof GetBegin) {
      ScalarReturningFunc targetTarget = ((GetBegin) target).getTarget();
      if (targetTarget instanceof GetCol) {
        return new FastComparator((GetCol) targetTarget, target);
      }
    } else if (target instanceof GetEnd) {
      ScalarReturningFunc targetTarget = ((GetEnd) target).getTarget();
      if (targetTarget instanceof GetCol) {
        return new FastComparator((GetCol) targetTarget, target);
      }
    } else if (target instanceof GetCol) {
      // Target function returns a span --> sort on begin, then end
      FieldType colType = ((GetCol) target).returnType();
      if (colType.getIsSpan()) {
        return new FastComparator((GetCol) target, null);
      }
    }

    // If we get here, the input can't be converted to a FastComparator.
    // Fall back on the slower version.
    return new SlowComparator(target);
  }

  /**
   * Factory method for creating a comparator from a list of keys. Will attempt to use
   * {@link FastComparator} when possible.
   * 
   * @throws ParseException if the target of comparison doesn't compile
   */
  public static TupleComparator makeComparator(ArrayList<ScalarFunc> targets)
      throws FunctionCallValidationException {

    // A single sort key
    if (targets.size() == 1) {

      // Try using the FastComparator if possible
      ScalarFunc target = targets.get(0);
      return makeComparator(target);
    }
    // Multiple sort keys
    else {
      return new ScalarListComparator(targets);
    }
  }

}
