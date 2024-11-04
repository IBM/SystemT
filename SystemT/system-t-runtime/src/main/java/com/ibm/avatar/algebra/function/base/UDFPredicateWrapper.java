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
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.api.exceptions.FunctionCallInitializationException;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.Token;
import com.ibm.avatar.aql.catalog.Catalog;

/**
 * The SystemT type hierarchy makes SelectionPredicate a subclass of ScalarFunc. For most functions,
 * this relationship between types is convenient. UDFs, however, can return different types
 * depending on the function implementation, so user-defined functions need to reside at both places
 * in the hierarchy. This class acts as an adapter to turn a UDF that returns a boolean value into a
 * SelectionPredicate.
 * 
 */
public class UDFPredicateWrapper extends SelectionPredicate {

  /**
   * Dummy arguments so that the validation code in the AQLFunc constructor doesn't complain.
   */
  public static final String[] ARG_NAMES = {"This string should never be used."};

  /* This class delegates all calls to ScalarUDF */
  private final ScalarUDF delegate;

  /**
   * This constructor should never be called
   */
  // private UDFPredicateWrapper (Token origTok, ScalarFunc[] args)
  // {
  // super (origTok, args);
  // throw new FatalInternalError ("This constructor should never be called");
  // }

  /**
   * Use this constructor always
   * 
   * @param origTok original AOG token for error reporting
   * @param args arguments to the UDF call
   * @param funcname name of the function to call
   * @param catalog AQL catalog, for looking up function info
   * @throws ParseException
   */
  public UDFPredicateWrapper(Token origTok, AQLFunc[] args, String funcname, Catalog catalog)
      throws ParseException {
    super(origTok, args);
    delegate = new ScalarUDF(origTok, args, funcname, catalog);
    // new UDFWrapper (origTok, args, funcname, symtab);

    if (!delegate.returnsBoolean()) {
      throw new ParseException(
          String.format("UDF used as predicate %s should return boolean type", funcname));
    }
  }

  @Override
  protected void validateArgTypes(ArrayList<FieldType> argTypes)
      throws FunctionCallValidationException {
    delegate.validateArgTypes(argTypes);
  }

  @Override
  public void bindImpl(AbstractTupleSchema ts) throws FunctionCallValidationException {
    delegate.bindImpl(ts);
  }

  @Override
  protected void initStateInternal(MemoizationTable mt) throws FunctionCallInitializationException {
    delegate.initStateInternal(mt);
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.avatar.algebra.predicate.SelectionPredicate#matches(com.ibm.avatar
   * .algebra.datamodel.Tuple, com.ibm.avatar.algebra.base.MemoizationTable)
   */
  @Override
  protected Boolean matches(Tuple t, TupleList[] locatorArgs, MemoizationTable mt,
      Object[] evaluatedArgs) throws TextAnalyticsException {
    Boolean ret = (Boolean) delegate.reallyEvaluate(t, locatorArgs, mt, evaluatedArgs);

    return ret;
  }

  @Override
  public boolean returnsNullOnNullInput() throws TextAnalyticsException {
    return delegate.returnsNullOnNullInput();
  }
}
