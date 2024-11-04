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
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.AggFunc;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.Token;

public class Count extends AggFunc {

  public static final String FNAME = "Count";

  public static final String USAGE = "Usage: " + FNAME + "(scalar), " + FNAME + "(*)";

  /** Flag that is set to true if this item is the aggregate function COUNT(*) */
  protected boolean isCountStar = false;

  public Count(Token origTok, ScalarFunc arg) {
    super(origTok, arg);
  }

  /**
   * Special constructor for COUNT(*).
   * 
   * @param origTok
   * @throws ParseException
   */
  public Count(Token origTok) {
    super(origTok, null);
    isCountStar = true;
  }

  @Override
  protected void validateArgTypes(ArrayList<FieldType> argTypes)
      throws FunctionCallValidationException {
    if (isCountStar) {
      // SPECIAL CASE: No real arguments to count(*)
      return;
      // END SPECIAL CASE
    }

    // If we get here, make sure there is exactly one argument.
    if (argTypes.size() != 1) {
      throw new FunctionCallValidationException(this, "Wrong number of arguments (%d instead of 1)",
          argTypes.size());
    }
  }

  @Override
  public Object evaluate(TupleList tupleList, TupleList[] locatorArgs, MemoizationTable mt)
      throws TextAnalyticsException {
    // SPECIAL CASE: if COUNT(*), return the number of input tuples
    if (isCountStar)
      return tupleList.size();
    // END SPECIAL CASE

    // Otherwise, evaluate the argument and return the number of non-null results
    Tuple t;
    int count = 0;

    for (int i = 0; i < tupleList.size(); i++) {
      t = tupleList.getElemAtIndex(i);

      if (arg.evaluate(t, locatorArgs, mt) != null)
        count++;
    }

    return count;
  }

  @Override
  protected void reallyGetRefRels(TreeSet<String> set) {
    if (!isCountStar)
      set.addAll(arg.getReferencedRels());
  }

  @Override
  public FieldType returnType() throws FunctionCallValidationException {
    return FieldType.INT_TYPE;
  }

}
