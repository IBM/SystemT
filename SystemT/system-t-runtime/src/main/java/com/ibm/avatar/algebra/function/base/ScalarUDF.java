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
import java.util.Iterator;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.util.udf.UDFParams;
import com.ibm.avatar.algebra.util.udf.UDFunction;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.api.exceptions.FunctionCallInitializationException;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.Token;
import com.ibm.avatar.aql.catalog.AbstractJarCatalogEntry;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.aql.catalog.ScalarFuncCatalogEntry;
import com.ibm.avatar.aql.catalog.ScalarUDFCatalogEntry;

/**
 * Generic wrapper that calls out to a user-defined scalar function.
 * 
 */
public class ScalarUDF extends ScalarFunc {

  /**
   * Dummy arguments so that the validation code in the AQLFunc constructor doesn't complain.
   */
  public static final String[] ARG_NAMES = {"This string should never be used."};

  /**
   * Name of the function used in the old implementation of scalar UDFs (prior to the existence of
   * table functions). This name is used for backwards-compatibility with compiled .tam files from
   * previous versions of SystemT.
   */
  public static final String LEGACY_FNAME = "UDFWrapper";

  /** The actual UDF; reflection code resides in this class */
  private final UDFunction udf;

  /**
   * (Shared) catalog entry for the jar file from which the function is loaded. NULL if this class
   * is being invoked at AQL compile time and the jar is not available in the current module.
   */
  private final AbstractJarCatalogEntry jarEntry;

  /** AQL type of the returned scalar value; computed at bind time. */
  private FieldType retType = null;

  /** Flag that is set to TRUE once the implementation class is fully-initialized. */
  private boolean udfClassInitialized = false;

  /** Flag for debug messages */
  // private final boolean debug = false;

  /**
   * Main constructor, invoked by the AOG parser.
   * 
   * @param origTok parser token for the function name
   * @param args list of arguments (function subtrees, can be empty)
   * @param funcname AQL name of the function
   * @param catalog the AQL catalog
   */
  public ScalarUDF(Token origTok, AQLFunc[] args, String funcname, Catalog catalog)
      throws ParseException {
    super(origTok, args);

    // Find function definition from the catalog.
    ScalarFuncCatalogEntry catentry = catalog.lookupScalarFunc(funcname);

    if (false == (catentry instanceof ScalarUDFCatalogEntry)) {
      throw new FatalInternalError("Catalog entry for scalar UDF %s is of the wrong type",
          funcname);
    }

    ScalarUDFCatalogEntry udfentry = (ScalarUDFCatalogEntry) catentry;
    UDFParams params = udfentry.toUDFParams(catalog);

    udf = new UDFunction(null, params);

    // Get enough information to be able to fire up a ClassLoader later on.
    jarEntry = catalog.lookupJar(params.getJarName());

    // Type checking of arguments happens during later steps of initialization
  }

  /**
   * We need to override this method from the superclass, since the function name changes depending
   * on the UDF implementation.
   */
  @Override
  protected String getFuncName() {
    return udf.getName();
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

  /**
   * We need to override this method from the superclass, since the arguments change depending on
   * the UDF implementation.
   */
  @Override
  protected FieldType[] getArgTypes() {
    ArrayList<Pair<String, String>> schemaInfo = udf.getParams().getColNamesAndTypes();
    FieldType[] ret = new FieldType[schemaInfo.size()];
    for (int i = 0; i < ret.length; i++) {
      try {
        ret[i] = FieldType.stringToFieldType(schemaInfo.get(i).second);
      } catch (com.ibm.avatar.aql.ParseException e) {
        throw new FatalInternalError("Couldn't convert field type string '%s'"
            + " for column %d to FieldType; should never happen.", schemaInfo.get(i).second, i);
      }
    }
    return ret;
  }

  @Override
  protected void validateArgTypes(ArrayList<FieldType> argTypes)
      throws FunctionCallValidationException {
    udf.validateParams(this, argTypes);
  }

  @Override
  public void bindImpl(AbstractTupleSchema ts) throws FunctionCallValidationException {
    UDFParams params = udf.getParams();

    String returnTypeName = params.getReturnType().toString();

    if (returnTypeName.equals(FieldType.SCALAR_LIST_TYPE.getTypeName())) {
      // SPECIAL CASE: Scalar list types are parameterized by one of the inputs, via the "like
      // <input>" clause
      if (params.getRetSpanLike() == null) {
        throw new FunctionCallValidationException(this,
            "Parameter name not specified for List return type");
      }

      // Scalar lists are parameterized by one of the inputs
      Iterator<Pair<String, String>> it = params.getColNamesAndTypes().iterator();
      FieldType retSpanLikeType = null;
      for (AQLFunc arg : args) {
        // Scalar functions can only take scalar arguments
        ScalarReturningFunc sf = (ScalarReturningFunc) arg;
        Pair<String, String> p = it.next();
        if (p.first.equals(params.getRetSpanLike())) {
          retSpanLikeType = sf.returnType();
        }
      }
      if (!retSpanLikeType.getIsScalarListType())
        throw new FunctionCallValidationException(this, "Parameter %s is not of scalarlist type",
            params.getRetSpanLike());
      retType = retSpanLikeType;
      // END SPECIAL CASE
    } else {
      // All other scalar types are encoded according to the names of the FieldType singletons
      try {
        retType = FieldType.stringToFieldType(returnTypeName);
      } catch (com.ibm.avatar.aql.ParseException e) {
        throw new FunctionCallValidationException(this, "Cannot parse return type name '%s'",
            returnTypeName);
      }
    }

    // // Compute return type
    // if (params.getReturnType ().equals (UDFunction.STRING_TYPE_LITERAL)) {
    // retType = FieldType.STRING_TYPE;
    // }
    // else if (params.getReturnType ().equals (UDFunction.INTEGER_TYPE_LITERAL)) {
    // retType = FieldType.INT_TYPE;
    // }
    // else if (params.getReturnType ().equals (UDFunction.BOOLEAN_TYPE_LITERAL)) {
    // retType = FieldType.BOOL_TYPE;
    // }
    // else if (params.getReturnType ().equals (UDFunction.SPAN_TYPE_LITERAL)) {
    // retType = FieldType.SPAN_TYPE;
    // }
    // else if (params.getReturnType ().equals (UDFunction.TEXT_TYPE_LITERAL)) {
    // retType = FieldType.TEXT_TYPE;
    // }
    // else if (params.getReturnType ().equals (UDFunction.SCALARLIST_TYPE_LITERAL)) {
    //
    // }
    // else {
    // throw new FunctionCallValidationException (this, "Return type " + params.getReturnType () + "
    // not known");
    // }

  }

  @Override
  protected void initStateInternal(MemoizationTable mt) throws FunctionCallInitializationException {
    // This method is called once per thread, but we only want to load the class once.
    if (false == udfClassInitialized) {
      if (null == jarEntry) {
        throw new FunctionCallInitializationException(
            "Cannot instantiate scalar UDF %s(), because information about jar file '%s' "
                + "is not present in the catalog.",
            udf.getParams().getFunctionName(), udf.getParams().getJarName());
      }

      // Only at this final point of initialization do we need to load the implementation class.
      try {
        udf.loadClass(jarEntry.makeClassLoader(this.getClass().getClassLoader()));
      } catch (ParseException e) {
        throw new FunctionCallInitializationException(e, "Error loading class for scalar UDF %s",
            udf.getName());
      }

      udfClassInitialized = true;
    }
  }

  /**
   * Calls the UDF evaluation method after converting input arguments to the expected field type
   */
  @Override
  public Object reallyEvaluate(Tuple t, TupleList[] locatorArgs, MemoizationTable mt,
      Object[] evaluatedArgs) throws TextAnalyticsException {
    if (false == udfClassInitialized) {
      throw new FatalInternalError(
          "ScalarUDF.reallyEvaluate() called without calling ScalarUDF.initStateInternal() first.");
    }

    if (evaluatedArgs.length != args.length) {
      throw new FatalInternalError(
          "ScalarUDF.reallyEvaluate() called with %d arguments (%d expected).",
          evaluatedArgs.length, args.length);
    }

    Object[] convertedArgs = new Object[evaluatedArgs.length];
    FieldType[] argTypes = getArgTypes();

    // Convert each of the evaluated input arguments to the type this UDF is expecting
    for (int i = 0; i < evaluatedArgs.length; i++) {
      FieldType argType = argTypes[i];
      convertedArgs[i] = argType.convert(evaluatedArgs[i]);
    }

    // try {
    return udf.evaluate(convertedArgs);
    // }
    // catch (RuntimeException e) {
    // if (debug) {
    // System.out.print ("UDF " + this.getFuncName ());
    // System.out.println (" has expected input types: " + Arrays.toString (argTypes));
    // System.out.println ("It has arguments that evaluate to these values: " + Arrays.toString
    // (evaluatedArgs));
    // System.out.println ("They are converted to : " + Arrays.toString (convertedArgs));
    // System.out.println ("The UDF implementation has parameters : " + udf.getParams ());
    // }
    // e.printStackTrace ();
    // throw e;
    // }
  }

  @Override
  public FieldType returnType() throws FunctionCallValidationException {
    return retType;
  }

  /**
   * Function for use by {@link UDFPredicateWrapper}. <b>Note:</b> This function must work BEFORE
   * binding!
   */
  public boolean returnsBoolean() {
    return udf.getParams().returnsBoolean();
  }

  @Override
  public boolean returnsNullOnNullInput() throws TextAnalyticsException {
    return udf.getParams().isReturnsNullOnNullInp();
  }

  @Override
  public boolean isDeterministic() {
    return udf.getParams().isDeterministic();
  }

}
