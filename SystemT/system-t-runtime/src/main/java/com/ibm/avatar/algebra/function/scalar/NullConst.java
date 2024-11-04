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
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.Token;

/** Just return null. */
public class NullConst extends ScalarFunc {
  public static final String[] ARG_NAMES = {};
  public static final FieldType[] ARG_TYPES = {};
  public static final String[] ARG_DESCRIPTIONS = {};

  /**
   * Standard-format constructor. The arguments list is, of course, always empty.
   */
  public NullConst(Token origTok, AQLFunc[] args) {
    super(origTok, new ScalarFunc[0]);
  }

  @Override
  public boolean isConst() {
    return true;
  }

  @Override
  public Object reallyEvaluate(Tuple t, TupleList[] locatorArgs, MemoizationTable mt,
      Object[] evaluatedArgs) throws TextAnalyticsException {
    return null;
  }

  @Override
  public FieldType returnType() {
    return FieldType.NULL_TYPE;
  }
}
