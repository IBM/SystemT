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

import java.util.ArrayList;
import java.util.TreeSet;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleComparator;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.AggFunc;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.Token;

public class Max extends AggFunc {
  public static final String[] ARG_NAMES = {"scalar"};
  public static final String[] ARG_DESCRIPTIONS = {"an integer, float, string or span"};

  TupleComparator comparator = null;

  public Max(Token origTok, ScalarFunc arg) {
    super(origTok, arg);
  }

  @Override
  protected void validateArgTypes(ArrayList<FieldType> argTypes)
      throws FunctionCallValidationException {
    if (argTypes.size() != 1) {
      throw new FunctionCallValidationException(this, "Wrong number of arguments (%d instead of 1)",
          argTypes.size());
    }

    // Argument to this call should return null, int, float, string, or span
    FieldType argType = argTypes.get(0);
    if (argType.getIsNullType() || argType.getIsNumericType() || argType.getIsSpanOrText()) {
      return;
    } else {
      throw new FunctionCallValidationException(this,
          "Argument returns value of unsupported type %s.", argType);
    }
  }

  @Override
  public void bindImpl(AbstractTupleSchema ts) throws FunctionCallValidationException {
    // make a comparator
    comparator = TupleComparator.makeComparator(arg);
  }

  @Override
  public Object evaluate(TupleList tupleList, TupleList[] locatorArgs, MemoizationTable mt)
      throws TextAnalyticsException {

    // return null if the input is empty
    if (tupleList.size() == 0)
      return null;

    Tuple currentMaxTuple = null;
    Object currentMaxResult = null;

    TLIter iter = tupleList.newIterator();

    // get the first non-null element
    while (iter.hasNext() && currentMaxResult == null) {
      currentMaxTuple = iter.next();
      currentMaxResult = arg.evaluate(currentMaxTuple, locatorArgs, mt);
    }

    // Scan to the right of the first non-null element for other maximum candidates
    while (iter.hasNext()) {
      Tuple candidate = iter.next();
      Object candidateResult = arg.evaluate(candidate, locatorArgs, mt);

      // ignore subsequent nulls so that we return null only if all inputs are null
      if (candidateResult != null) {
        if (comparator.compare(currentMaxTuple, candidate) < 0) {
          currentMaxResult = candidateResult;
          currentMaxTuple = candidate;
        }
      }
    }

    return currentMaxResult;
  }

  @Override
  protected void reallyGetRefRels(TreeSet<String> set) {
    set.addAll(arg.getReferencedRels());

  }

  @Override
  public FieldType returnType() throws FunctionCallValidationException {
    return arg.returnType();
  }

}
