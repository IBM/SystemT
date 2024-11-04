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

/** Returns a new auto-generated integer. */
public class AutoID extends ScalarFunc {
  public static final String[] ARG_NAMES = {};
  public static final FieldType[] ARG_TYPES = {};
  public static final String[] ARG_DESCRIPTIONS = {};

  public static final IDGenerator idGen = new IDGenerator();

  /**
   * Standard-format constructor. The arguments list is empty
   */
  public AutoID(Token origTok, AQLFunc[] args) {
    super(origTok, new ScalarFunc[0]);
  }

  @Override
  public Object reallyEvaluate(Tuple t, TupleList[] locatorArgs, MemoizationTable mt,
      Object[] evaluatedArgs) throws TextAnalyticsException {
    return idGen.getNextID();
  }

  @Override
  public boolean isDeterministic() {
    return false;
  }

  @Override
  public FieldType returnType() {
    return FieldType.INT_TYPE;
  }

  public static void resetIDCounter() {
    idGen.resetIDCounter();
  }

  private static class IDGenerator {
    private static int id = 0;

    public int getNextID() {
      return id++;
    }

    public void resetIDCounter() {
      id = 0;
    }
  }
}
