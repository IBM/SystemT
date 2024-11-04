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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.util.udf.UDFParams;
import com.ibm.avatar.algebra.util.udf.UDFunction;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.api.exceptions.FunctionCallInitializationException;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TableFunctionNotFoundException;
import com.ibm.avatar.api.exceptions.TableUDFException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.api.udf.TableUDFBase;
import com.ibm.avatar.aql.AQLParserBase;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.Token;
import com.ibm.avatar.aql.catalog.AbstractJarCatalogEntry;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.aql.catalog.TableFuncCatalogEntry;
import com.ibm.avatar.aql.catalog.TableUDFCatalogEntry;

/**
 * Adapter class that allows embedding a reference to a user-defined table function inside an AOG
 * file.
 * 
 */
public class TableUDF extends TableReturningFunc {
  /**
   * Dummy arguments so that the validation code in the AQLFunc constructor doesn't complain.
   */
  public static final String[] ARG_NAMES = {"This string should never be used."};

  /** The actual UDF; reflection code resides in this class */
  private UDFunction udf;

  /**
   * Runtime implementation of the table function. Note that one copy is shared among all threads.
   */
  private TableUDFBase funcObj = null;

  /**
   * Type of tuples returned by the table function, as declared in the "create function" statement.
   */
  private TupleSchema returnSchema = null;

  /**
   * Names and types of the inputs of the table function, as declared in the "create function"
   * statment.
   */
  private TupleSchema declaredInputSchema = null;

  /** Actual names and types of the inputs to the table function at runtime */
  private TupleSchema runtimeInputSchema = null;

  /** (Shared) catalog entry for the jar file from which the function is loaded. */
  private AbstractJarCatalogEntry jarEntry;

  /**
   * Main constructor, invoked by the AQL and AOG parsers
   * 
   * @param origTok AQL parser token for the function name, or NULL if loading from AOG
   * @param args list of arguments (function subtrees, can be empty)
   * @param funcname AQL name of the function
   * @param catalog the AQL catalog, for finding function metadata
   * @throws ParseException
   */
  public TableUDF(Token origTok, AQLFunc[] args, String funcname, Catalog catalog)
      throws ParseException {
    super(origTok, args);

    // Find function definition from the catalog

    TableFuncCatalogEntry catEntry = catalog.lookupTableFunc(funcname);
    if (null == catEntry) {
      throw new TableFunctionNotFoundException(origTok, funcname);
    }

    if (false == (catEntry instanceof TableUDFCatalogEntry)) {
      throw new FatalInternalError("Catalog entry for table-returning UDF %s is of the wrong type",
          funcname);
    }
    TableUDFCatalogEntry udfEntry = (TableUDFCatalogEntry) catEntry;
    UDFParams params = udfEntry.toUDFParams(catalog);

    udf = new UDFunction(origTok, params);

    // Get enough information to be able to fire up a ClassLoader later on.
    jarEntry = catalog.lookupJar(params.getJarName());
    udf.loadClass(jarEntry.makeClassLoader(this.getClass().getClassLoader()));

    // Make sure that the user-defined function is indeed a table function.
    Object funcObj = udf.getInstance();
    if (null == funcObj) {
      // Instance ptr is set to null if the implementing method is static.
      throw AQLParserBase.makeException(origTok,
          "Implementation of user-defined table function %s " + "is the static method %s.%s, "
              + "but user-defined table functions cannot be implemented with static methods.  "
              + "Please convert the method to a non-static method.",
          funcname, udf.getClass(), udf.getMethodName());
    }
    if (false == funcObj instanceof TableUDFBase) {
      throw AQLParserBase.makeException(origTok,
          "Implementation of user-defined table function %s " + "is not a subclass of %s. "
              + "User-defined table functions must be subclasses of %s.",
          funcname, TableUDFBase.class.getName(), TableUDFBase.class.getName());
    }

    if (true == udf.getParams().isReturnsNullOnNullInp()) {
      throw new FatalInternalError("Table function marked as returning null on null input");
    }

    // argument type checking can happen only after binding
  }

  /**
   * We need to override this method from the superclass, since the arguments change depending on
   * the UDF implementation.
   */
  @Override
  protected String[] getArgNames() {
    ArrayList<Pair<String, String>> schemaInfo = udf.getParams().getColNamesAndTypes();
    String[] ret = new String[schemaInfo.size()];
    for (int i = 0; i < ret.length; i++) {
      ret[i] = schemaInfo.get(i).first;
    }
    return ret;
  }

  @Override
  protected void validateArgTypes(ArrayList<FieldType> argTypes)
      throws FunctionCallValidationException {
    udf.validateParams(this, argTypes);
  }

  @Override
  public void bindImpl(AbstractTupleSchema[] schemas, String[] targetNames)
      throws FunctionCallValidationException {

    // Figure out the declared type of the AQL table function. This type is packed into a string in
    // the AOG file and is
    // only decoded here.
    UDFParams params = udf.getParams();
    try {
      declaredInputSchema = params.getArgsAsSchema();
      returnSchema = new TupleSchema(params.getReturnType().getRecordSchema(), false);
    } catch (Exception e) {
      // This error should have been caught during compilation
      throw new FatalInternalError(e, "Error parsing return schema of table function %s",
          getFuncName());
    }

    // Walk through the arguments, collecting the elements of the runtime schema (arguments have
    // already been bound by
    // the time this method is called).
    FieldType[] rtArgTypes = new FieldType[args.length];
    String[] rtArgNames = getArgNames();

    for (int i = 0; i < args.length; i++) {
      AQLFunc arg = args[i];

      if (arg instanceof TableLocator) {
        // Table locator argument
        rtArgTypes[i] = new FieldType(((TableLocator) arg).getOutputSchema(), true);
      } else if (arg instanceof ScalarReturningFunc) {
        // (constant) scalar argument
        rtArgTypes[i] = ((ScalarReturningFunc) arg).returnType();
      } else {
        throw new FatalInternalError(
            "Unexpected argument type %s at position %d while computing argument schema of %s",
            arg.getClass().getName(), i, this);
      }
    }

    runtimeInputSchema = new TupleSchema(rtArgNames, rtArgTypes);

    // Instantiate the function and perform compile-time checks
    // The constructor will have checked that this cast will succeed.
    funcObj = (TableUDFBase) udf.getInstance();

    try {
      funcObj.bindImpl(declaredInputSchema, runtimeInputSchema, returnSchema, udf.getMethod());
    } catch (TableUDFException e) {
      throw new FunctionCallValidationException(e, this, "Error binding function to schema: %s",
          e.getMessage());
    }
  }

  @Override
  protected void initStateInternal(MemoizationTable mt) throws FunctionCallInitializationException {
    // Set up the table function implementation object.
    // Note the check to ensure we don't initialize the function more than once; this function is
    // called once per
    // thread.
    if (false == (State.INITIALIZED.equals(this.state))) {
      try {
        funcObj.initState();

        // Now that we've initialized the function, we can perform a runtime validation of the input
        // schema
        funcObj.validateSchema(declaredInputSchema, runtimeInputSchema, returnSchema,
            udf.getMethod(), false);
      } catch (TableUDFException e) {
        throw new FunctionCallInitializationException(e,
            "Error initializing call to user-defined table function %s(): %s", computeFuncName(),
            e.getMessage());
      }
    }
  }

  @Override
  public TupleList evaluate(TupleList[] childResults, MemoizationTable mt)
      throws TextAnalyticsException {
    // Construct the arguments of the function call
    Object[] argVals = new Object[args.length];

    for (int i = 0; i < args.length; i++) {
      if (args[i] instanceof TableReturningFunc) {
        argVals[i] = ((TableReturningFunc) args[i]).evaluate(childResults, mt);
      } else if (args[i] instanceof ScalarFunc) {
        // Scalar function arguments to a table function must ignore the "primary input relation"
        // argument; hence the
        // null argument here.
        argVals[i] = ((ScalarFunc) args[i]).evaluate(null, childResults, mt);
      } else {
        throw new FatalInternalError("Don't know how to call function object of type %s",
            args[i].getClass().getName());
      }
    }

    try {
      Method m = udf.getMethod();

      // Sanity checks
      if (null == m) {
        throw new FatalInternalError("Null method pointer inside UDF.  Should never happen.");
      }
      if (null == funcObj) {
        throw new FatalInternalError("Null function object pointer.  Should never happen.");
      }

      TupleList tups = (TupleList) m.invoke(funcObj, argVals);
      if (null == tups) {
        throw new FatalInternalError(
            "Implementation of table function %s() returned null instead of empty TupleList",
            computeFuncName());
      }
      return tups;
    } catch (Throwable t) {
      // Pretty print the arguments in the error message
      String[] argValsStr = new String[argVals.length];
      for (int i = 0; i < args.length; i++) {
        if (argVals[i] instanceof TupleList) {
          // SPECIAL CASE: Use a short representation of the tuple list for the error message
          argValsStr[i] = ((TupleList) argVals[i]).toShortString();
          // END: SPECIAL CASE
        } else {
          argValsStr[i] = argVals[i].toString();
        }
      }

      throw new TextAnalyticsException(t, "Error invoking %s() table function over inputs %s",
          udf.getName(), Arrays.toString(argValsStr));
    }
  }

  @Override
  public AbstractTupleSchema getOutputSchema() {
    return returnSchema;
  }

  /**
   * @return Names and types of the inputs of the table function, as declared in the "create
   *         function" statment.
   */
  public TupleSchema getDeclaredInputSchema() {
    return declaredInputSchema;
  }

}
