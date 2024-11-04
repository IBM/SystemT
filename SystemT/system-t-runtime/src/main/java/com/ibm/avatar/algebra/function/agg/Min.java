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
package com.ibm.avatar.algebra.function.agg;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.Token;

/** Min() aggregate; gets most of its type-checking and initialization functionality from Max. */
public class Min extends Max {
  // Need to define these fields even though they're in Max, because the superclass constructor
  // checks for them.
  public static final String[] ARG_NAMES = {"scalar"};
  public static final String[] ARG_DESCRIPTIONS = {"an integer, float, string or span"};

  public Min(Token origTok, ScalarFunc arg) throws ParseException {
    super(origTok, arg);
  }

  @Override
  public Object evaluate(TupleList tupleList, TupleList[] locatorArgs, MemoizationTable mt)
      throws TextAnalyticsException {
    // return null if the input is empty
    if (tupleList.size() == 0)
      return null;

    Tuple currentMinTuple = null;
    Object currentMinResult = null;

    TLIter iter = tupleList.newIterator();

    // find the first non-null element
    while (iter.hasNext() && currentMinResult == null) {
      currentMinTuple = iter.next();
      currentMinResult = arg.evaluate(currentMinTuple, locatorArgs, mt);
    }

    // Scan to the right of the first non-null element for other minimum candidates
    while (iter.hasNext()) {
      Tuple candidate = iter.next();
      Object candidateResult = arg.evaluate(candidate, locatorArgs, mt);

      // ignore subsequent nulls so that we return null only if all inputs are null
      if (candidateResult != null) {
        if (comparator.compare(currentMinTuple, candidate) > 0) {
          currentMinResult = candidateResult;
          currentMinTuple = candidate;
        }
      }
    }

    return currentMinResult;
  }

}
