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

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.Token;

/**
 * Base class for <b>all</b> AQL table function calls, i.e. calls to functions that return a set of
 * tuples. Not to be confused with the external API for implementation classes of user-defined table
 * functions, {@link com.ibm.avatar.api.TableUDFBase}. Built-in table functions should derive
 * directly from this class. User-defined table functions go through {@link TableUDF}.
 */
public abstract class TableReturningFunc extends AQLFunc {

  /**
   * Main constructor for use by subclasses
   * 
   * @param origTok AQL parser token for the function name
   * @param args recursive arguments of the function call
   */
  protected TableReturningFunc(Token origTok, AQLFunc[] args) {
    super(origTok, args);
  }

  /**
   * Decode a function's returned tuple schema, as serialized inside an AOG file.
   * 
   * @param commaDelimList the serialized tuple schema, as a comma delimited list of
   *        (space-delimited) pairs of type and name
   * @return runtime schema object that represents the indicated schema
   */
  public static AbstractTupleSchema decodeTupleSchema(String commaDelimList) {
    // We do minimal validation of the arguments, since the input to this method should come from
    // internal SystemT code.
    String[] elems = commaDelimList.split(",");

    if (0 == elems.length) {
      throw new FatalInternalError("'%s' does not appear to be an encoded tuple schema",
          commaDelimList);
    }

    String[] colnames = new String[elems.length];
    FieldType[] coltypes = new FieldType[elems.length];

    for (int i = 0; i < elems.length; i++) {
      String[] vals = elems[i].split(" ");
      if (2 != vals.length) {
        throw new FatalInternalError(
            "Element %d of encoded tuple schema"
                + " '%s' ('%s') does not appear to be a space-delimited pair of type and name",
            i, commaDelimList, elems[i]);
      }

      colnames[i] = vals[0];
      try {
        coltypes[i] = FieldType.stringToFieldType(vals[1]);
      } catch (ParseException e) {
        throw new FatalInternalError(e, "Couldn't parse type of field in table function schema for "
            + "table function while parsing AOG");
      }

    }

    return new TupleSchema(colnames, coltypes);
  }

  /**
   * Prepare a function to be called over tuples from a particular schema.
   * 
   * @param targetSchemas the schemas of any locator inputs to the function
   * @param targetNames names of the target views/tables of the locator arguments
   * @throws FunctionCallValidationException if there is a failure to validate the input schema(s)
   */
  public final void bind(AbstractTupleSchema[] targetSchemas, String[] targetNames)
      throws FunctionCallValidationException {
    // Enforce API state machine restrictions
    if (false == (State.UNINITIALIZED.equals(state))) {
      throw new FatalInternalError("bind() called when function is in %s state.", state);
    }

    // Sanity check.
    for (int i = 0; i < targetNames.length; i++) {
      if (null == targetNames[i]) {
        throw new FatalInternalError(
            "Null pointer at position %d of targetNames array passed to TableReturningFunc.bind()",
            i);
      }
    }

    // Bind arguments first.
    // Build up a list of argument types while we're at it.
    ArrayList<FieldType> argTypes = new ArrayList<FieldType>();
    if (null != args) {
      for (int i = 0; i < args.length; i++) {
        AQLFunc arg = args[i];
        if (null == arg) {
          throw new FatalInternalError("AQLFunc object has no argument pointer at position %d", i);
        }

        if (arg instanceof ScalarReturningFunc) {
          // Scalar argument
          // Bind the argument with a null primary input schema ptr, since table functions don't
          // have a "primary" input
          // schema to pull records from
          ScalarReturningFunc sf = (ScalarReturningFunc) arg;

          sf.bind(null, targetSchemas, targetNames);
          argTypes.add(sf.returnType());
        } else if (arg instanceof TableLocator) {
          // Locator argument. Figure out which of the locator inputs it is.
          TableLocator tl = (TableLocator) arg;

          tl.bind(targetSchemas, targetNames);
          argTypes.add(new FieldType(tl.getOutputSchema(), true));
        } else {
          throw new FatalInternalError(
              "Encountered unexpected argument type %s " + "while binding arguments of %s",
              arg.getClass().getName(), this);
        }
      }
    }

    // Verify that the input types are ok.
    validateArgTypes(argTypes);

    // Subclasses do most of the work.
    bindImpl(targetSchemas, targetNames);

    // Now that we're done with bind(), we should be done with the original
    // token that is used for reporting errors at bind time.
    // Remove it to save (a surprisingly large amount of) memory.
    origTok = null;

    state = State.BOUND;
  }

  /**
   * Internal callback for any bind-time initialization that occurs in the subclass.
   * 
   * @param targetSchemas schemas of any locator valued inputs to the function
   * @param targetNames names of the target views/tables of the locator arguments
   */
  public void bindImpl(AbstractTupleSchema[] targetSchemas, String[] targetNames)
      throws FunctionCallValidationException {
    // By default do nothing
  }

  /**
   * Main entry point for calling a given table function.
   * 
   * @param childResults the outputs of any views whose locators are arguments to the function call
   * @param mt thread-local data
   * @return return value of the table function as applied to the indicated inputs
   */
  public abstract TupleList evaluate(TupleList[] childResults, MemoizationTable mt)
      throws TextAnalyticsException;

  /**
   * Implementations of this function should only be invoked after binding.
   * 
   * @return output schema of the tuples that this table function produces
   */
  public abstract AbstractTupleSchema getOutputSchema();
}
