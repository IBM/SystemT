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
package com.ibm.avatar.algebra.function.predicate;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.algebra.function.base.SelectionPredicate;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.aql.Token;

/**
 * Selection predicate that always returns true.
 * 
 * @deprecated use BoolConst() instead.
 */
@Deprecated
public class True extends SelectionPredicate {
  public static final String[] ARG_NAMES = {};
  public static final FieldType[] ARG_TYPES = {};
  public static final String[] ARG_DESCRIPTIONS = {};

  public True(Token origTok, AQLFunc[] args) throws ParseException {
    super(origTok, args);
  }

  @Override
  protected Boolean matches(Tuple tuple, TupleList[] locatorArgs, MemoizationTable mt,
      Object[] evaluatedArgs) {
    return Boolean.TRUE;
  }

}
