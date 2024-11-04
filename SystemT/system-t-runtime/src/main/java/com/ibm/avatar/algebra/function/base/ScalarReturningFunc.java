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

package com.ibm.avatar.algebra.function.base;

import java.util.ArrayList;

import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.aql.Token;

/**
 * Base class for functions that return a scalar value (as opposed to table functions, which return
 * table values). Holds methods that are specific to returning scalars.
 */
public abstract class ScalarReturningFunc extends AQLFunc {
  /** Empty array for use in variants of oldEvaluate() */
  protected static final TupleList[] EMPTY_TL_ARRAY = {};

  /**
   * Types of any locator arguments of this function; index is the same as the indexes to the second
   * argument of {@link #bind(AbstractTupleSchema, AbstractTupleSchema[], String[])}
   */
  protected AbstractTupleSchema[] locatorSchemas;

  /** Pass-through constructor. */
  protected ScalarReturningFunc(Token origTok, AQLFunc[] args) {
    super(origTok, args);
  }

  /**
   * Convenience version of bind() for cases where record locators are not supported. Should be
   * removed once every part of AQL that supports scalar functions supports locators.
   * 
   * @param primarySchema schema of the main input tuples of the function
   */
  public final void oldBind(AbstractTupleSchema primarySchema)
      throws FunctionCallValidationException {
    bind(primarySchema, new AbstractTupleSchema[0], new String[0]);
  }

  /**
   * Prepare a function to be called over tuples from a particular schema.
   * 
   * @param primarySchema the schema of the join tuples that serve as the primary input
   * @param targetSchemas the schemas of any locator inputs to the function
   * @param targetNames names of the target views/tables referenced in the first argument
   * @throws FunctionCallValidationException if there is a failure to validate the input schema(s)
   */
  public final void bind(AbstractTupleSchema primarySchema, AbstractTupleSchema[] targetSchemas,
      String[] targetNames) throws FunctionCallValidationException {
    // Enforce API state machine restrictions
    if (false == (State.UNINITIALIZED.equals(state))) {
      throw new FatalInternalError("bind() called when function is in %s state.", state);
    }

    if (null == targetNames) {
      throw new FatalInternalError("Null targetNames pointer passed to ScalarReturningFunc.bind()");
    }

    // Sanity check.
    for (int i = 0; i < targetNames.length; i++) {
      if (null == targetNames[i]) {
        throw new FatalInternalError(
            "Null pointer at position %d of targetNames array passed to ScalarReturningFunc.bind()",
            i);
      }
    }

    this.locatorSchemas = targetSchemas;

    // Bind arguments first.
    // Build up a list of argument types while we're at it.
    ArrayList<FieldType> argTypes = new ArrayList<FieldType>();
    if (null != args) {
      for (int i = 0; i < args.length; i++) {
        // Arguments of scalar functions must be scalar functions or record locators
        if (args[i] instanceof TableLocator) {
          // SPECIAL CASE: Table locator argument.
          TableLocator tl = (TableLocator) args[i];

          // System.err.printf("Calling bind() on %s (type %s)\n", tl, tl.getClass ().getName ());
          tl.bind(targetSchemas, targetNames);

          // System.err.printf("Calling getOutputSchema() on %s\n", tl);
          FieldType retType = new FieldType(tl.getOutputSchema(), true);
          argTypes.add(retType);
          // END SPECIAL CASE
        } else {
          // Normal case: Scalar function argument
          ScalarReturningFunc arg = (ScalarReturningFunc) args[i];
          if (null == arg) {
            throw new FatalInternalError("AQLFunc object has no argument pointer at position %d",
                i);
          }
          arg.bind(primarySchema, targetSchemas, targetNames);

          FieldType retType = arg.returnType();

          // Sanity check
          // Functions can return null, but they are not expected to return null
          if (null == retType) {
            throw new FatalInternalError(
                "Inferred null return type for argument %d of function call %s", i + 1, this);
          }

          argTypes.add(retType);
        }
      }
    }

    // Verify that the input types are ok.
    validateArgTypes(argTypes);

    // Subclasses do most of the work.
    bindImpl(primarySchema);

    // Now that we're done with bind(), we should be done with the original
    // token that is used for reporting errors at bind time.
    // Remove it to save (a surprisingly large amount of) memory.
    origTok = null;

    state = State.BOUND;
  }

  /**
   * Subclasses should override this method with the actual internal implementation of binding, but
   * only in the context of the current class.
   * 
   * @param ts the schema of tuples that will be passed to evaluate()
   * @throws ParseException
   */
  public void bindImpl(AbstractTupleSchema ts) throws FunctionCallValidationException {
    // By default do nothing.
  }

  /**
   * This function should only be called when the function has been bound to a tuple schema
   * 
   * @return return type of the function in the context of the expression
   * @throws ParseException
   */
  public abstract FieldType returnType() throws FunctionCallValidationException;

}
