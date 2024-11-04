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
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.Token;

/**
 * Casts the first argument into the type specified by the second argument Arguments are:
 * <ul>
 * <li>Function call that returns first input
 * <li>Function call that returns second input
 * </ul>
 * 
 */
public class Cast extends ScalarFunc {

  public static final String FNAME = "Cast";

  public static final String USAGE = "Usage: " + FNAME + "(expression, type) or " + FNAME
      + "(expression, field_to_copy_type_from)";

  private FieldType returnType = null;

  /*
   * Keep these in sync with the literals used in the AQL parser UDFParamTypeLiteral() function
   */
  public static final String TEXT_TYPE_LITERAL = "Text";
  public static final String SPAN_TYPE_LITERAL = "Span";
  public static final String SCALAR_LIST_TYPE_LITERAL = "ScalarList";

  /**
   * Constructor called from
   * {@link com.ibm.avatar.algebra.function.base.ScalarFunc#buildFunc(String, ArrayList) .}
   */
  public Cast(Token origTok, AQLFunc[] args) throws ParseException {
    super(origTok, args);
  }

  @Override
  protected void validateArgTypes(ArrayList<FieldType> argTypes)
      throws FunctionCallValidationException {
    if (2 != argTypes.size()) {
      throw new FunctionCallValidationException(this, "Found %d arguments instead of 2",
          argTypes.size());
    }

    // Defer the remainder of type checking to bind time.
  }

  @Override
  public void bindImpl(AbstractTupleSchema ts) throws FunctionCallValidationException {
    // Compute return type
    if (args[1] instanceof GetCol) {
      // Get the return type from the specified column
      returnType = getSFArg(1).returnType();
      if (FieldType.UNKNOWN_TYPE.equals(returnType)) {
        // The UNKNOWN type shouldn't be occurring inside a function call tree
        throw new FunctionCallValidationException(
            "Attempted to cast to type of field returned by %s, but that field has unknown type",
            args[1]);
      }
    } else {
      // Construct the specified return type
      String typeName = ((StringConst) args[1]).getString();

      try {
        returnType = FieldType.stringToFieldType(typeName);
      } catch (com.ibm.avatar.aql.ParseException e) {
        throw new FunctionCallValidationException(e, this,
            "Type name '%s' does not correspond to a known field type", typeName);
      }
    }
  }

  @Override
  public FieldType returnType() {
    return returnType;
  }

  @Override
  public Object reallyEvaluate(Tuple t, TupleList[] locatorArgs, MemoizationTable mt,
      Object[] evaluatedArgs) throws TextAnalyticsException {
    Object firstObj = evaluatedArgs[0];

    // Currently, Cast() supports only nulls
    if (firstObj == null)
      return null;

    // TODO: otherwise, attempt to do the conversion
    Object ret = null;
    return ret;
  }

}
