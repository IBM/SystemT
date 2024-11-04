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

import java.util.ArrayList;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;

/**
 * Function that takes a string literal as argument and returns it.
 * 
 */
public class StringConst extends ScalarFunc {

  public static final String[] ARG_NAMES = {"value"};
  public static final FieldType[] ARG_TYPES = {FieldType.TEXT_TYPE};
  public static final String[] ARG_DESCRIPTIONS = {"string literal"};

  public static final boolean ISCONST = true;

  private Text text;

  // private TupleSchema ts;

  /**
   * Main constructor.
   * 
   * @throws ParseException
   */
  /*
   * public StringConst(ArrayList<ScalarFunc> args) throws ParseException { if (1 == args.size() &&
   * (FieldType.STRING_TYPE == args.get(0).returnType())) { str =
   * (String)args.get(0).evaluateConst(); } else { throw new ParseException(USAGE); } }
   */

  /** Constructor for the AOG parser. */
  public StringConst(String arg) {
    super(null, null);
    text = Text.fromString(arg);
  }

  @Override
  protected void validateArgTypes(ArrayList<FieldType> argTypes)
      throws FunctionCallValidationException {
    // Disable checks from the superclass.
  }

  @Override
  public FieldType returnType() {
    return FieldType.TEXT_TYPE;
  }

  @Override
  public boolean isConst() {
    return true;
  }

  @Override
  public Object reallyEvaluate(Tuple t, TupleList[] locatorArgs, MemoizationTable mt,
      Object[] evaluatedArgs) throws TextAnalyticsException {
    return text;
  }

  /**
   * Convenience method for use by other scalar functions.
   */
  public String getString() {
    return text.getText();
  }

}
