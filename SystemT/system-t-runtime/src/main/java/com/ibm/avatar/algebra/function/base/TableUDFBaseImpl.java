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

import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.api.exceptions.TableUDFException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;

/**
 * Internal implementation of the non-public portions of
 * {@link com.TableUDFBase.api.udf.TableFunctionBase}. Provides hooks for the internal use when
 * initializing and calling a user-defined table function.
 */
public abstract class TableUDFBaseImpl {
  /**
   * Names and types of the table function's arguments, as declared in the "create function"
   * statement.
   */
  protected TupleSchema declaredInputSchema;

  /**
   * Actual names and types of inputs at bind time. May be different from (but compatible with) the
   * declared input schema.
   */
  protected TupleSchema runtimeInputSchema;

  /**
   * Schema of this table function's returned tuples, as declared in the "create function"
   * statement.
   */
  protected TupleSchema declaredReturnSchema;

  /** Identifier of the method within the class that implements the user-defined table function */
  Method methodInfo;


  /**
   * Initializes the fields of this object. Does NOT perform schema validation.
   * 
   * @param declaredInputSchema Names and types of this table function's arguments, as declared in
   *        the "create function" statement
   * @param runtimeInputSchema actual schema that this instance of the function will receive
   * @param returnSchema Schema of this table function's returned tuples, as declared in the "create
   *        function" statement
   * @param methodInfo identifier of the method within the class that implements the user-defined
   *        table function
   * @throws TextAnalyticsException if something goes wrong
   */
  protected final void bindImpl(TupleSchema declaredInputSchema, TupleSchema runtimeInputSchema,
      TupleSchema returnSchema, Method methodInfo) throws TableUDFException {
    if (null == returnSchema) {
      throw new NullPointerException(
          "Null returnSchema pointer passed to TableUDFBaseImpl.initialize()");
    }
    if (null == declaredInputSchema) {
      throw new NullPointerException(
          "Null declaredInputSchema pointer passed to TableUDFBaseImpl.initialize()");
    }
    if (null == runtimeInputSchema) {
      throw new NullPointerException(
          "Null runtimeInputSchema pointer passed to TableUDFBaseImpl.initialize()");
    }

    // Set up schema ptrs
    this.declaredInputSchema = declaredInputSchema;
    this.runtimeInputSchema = runtimeInputSchema;
    this.declaredReturnSchema = returnSchema;

    // Any additional bind-time initialization of the table function should go here.
  }



  /** Function implemented (and documented) in subclass. */
  protected abstract void validateSchema(TupleSchema declaredInputSchema,
      TupleSchema runtimeInputSchema, TupleSchema returnSchema, Method methodInfo,
      boolean compileTime) throws TableUDFException;

  /** Callback implemented (and documented) in subclass. */
  protected abstract void initState() throws TableUDFException;

}
