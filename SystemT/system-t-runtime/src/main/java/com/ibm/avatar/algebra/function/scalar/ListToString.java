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

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.ScalarList;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.Token;

/**
 * Function that takes a list of scalars as argument and returns a copy of the list with duplicate
 * elements removed
 * 
 */
public class ListToString extends ScalarFunc {
  public static final String[] ARG_NAMES = {"scalarlist", "separator"};
  public static final FieldType[] ARG_TYPES = {FieldType.SCALAR_LIST_TYPE, FieldType.TEXT_TYPE};
  public static final String[] ARG_DESCRIPTIONS = {"target list", "delimiter string"};

  private String separator;

  /**
   * Constructor called from {@link com.ibm.avatar.algebra.function.ScalarFunc#buildFunc(String,
   * ArrayList).}
   * 
   * @throws ParseException
   */
  public ListToString(Token origTok, AQLFunc[] args) throws ParseException {
    super(origTok, args);
  }

  @Override
  public FieldType returnType() {
    return FieldType.TEXT_TYPE;
  }

  @Override
  public Object reallyEvaluate(Tuple t, TupleList[] locatorArgs, MemoizationTable mt,
      Object[] evaluatedArgs) throws TextAnalyticsException {

    @SuppressWarnings({"all"})
    ScalarList list = (ScalarList) evaluatedArgs[0];

    StringBuffer buff = new StringBuffer();

    int size = list.size();

    if (size > 0)
      buff.append(list.get(0).toString());

    for (int idx = 1; idx < size; idx++)
      buff.append(separator + list.get(idx).toString());

    return Text.convert(buff.toString());
  }

}
