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
package com.ibm.avatar.api.udf;

import java.lang.reflect.Method;

import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.function.base.TableUDFBaseImpl;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.api.exceptions.TableUDFException;

/**
 * Base class for AQL table function implementation classes. All Java classes that implement AQL
 * table functions must be subclasses of this class. A given subclass of this class can implement
 * multiple different table functions.
 *
 */
public abstract class TableUDFBase extends TableUDFBaseImpl {

  /**
   * This Java method allows the implementation method for an AQL table function to obtain the table
   * function’s output schema at run time. If a subclass of TableUDFBase contains implementations of
   * more than one table function, then there will be a separate Java object created for each table
   * function instance. This method will return the appropriate schema when called from within each
   * of these objects.
   * <p>
   * It is <b>not</b> necessary to cache the output of this method for performance; multiple calls
   * to this method will return the same Java object.
   *
   * @return the schema of tuples that the current Java object instance should return when invoked
   *         as an AQL table function
   */
  protected final TupleSchema getReturnTupleSchema() {
    // Catch null ptrs here instead of passing them to user code.
    if (null == declaredReturnSchema) {
      throw new FatalInternalError("Detected null return schema pointer for table UDF.");
    }

    return declaredReturnSchema;
  }

  /**
   * This Java method allows the implementation method for an AQL table function to obtain the table
   * function’s <b>declared</b> argument schema (the names and types of arguments, as declared in
   * the "create function" statement).
   * <p>
   * <b>NOTE:</b> This schema should <b>NOT</b> be used in constructing accessors to record locator
   * arguments.
   * <p>
   * If a subclass of TableUDFBase contains implementations of more than one table function, then
   * there will be a separate Java object created for each table function instance. This method will
   * return the appropriate schema when called from within each of these objects.
   * <p>
   * It is <b>not</b> necessary to cache the output of this method for performance; multiple calls
   * to this method will return the same Java object.
   *
   * @return the names and types of the arguments that can be passed to this object when invoked as
   *         an AQL table function, as specified in the <b>create function</b> statement.
   */
  protected final TupleSchema getDeclaredArgSchema() {
    // Catch null ptrs here instead of passing them to user code.
    if (null == declaredInputSchema) {
      throw new FatalInternalError("Detected null argument schema pointer for table UDF of type %s",
          this.getClass().getName());
    }

    return declaredInputSchema;
  }

  /**
   * This Java method allows the implementation method for an AQL table function to obtain the table
   * function’s <b>runtime</b> argument schema (the names and types of arguments, where those types
   * are the precise types returned by each of the inputs to the function call).
   * <p>
   * The schema returned by this method can be used for constructing accessors for reading fields of
   * input tuples that arrive via record locator arguments.
   * <p>
   * If a subclass of TableUDFBase contains implementations of more than one table function, then
   * there will be a separate Java object created for each table function instance. This method will
   * return the appropriate schema when called from within each of these objects.
   * <p>
   * It is <b>not</b> necessary to cache the output of this method for performance; multiple calls
   * to this method will return the same Java object.
   *
   * @return the names and actual runtime types of the arguments that will be passed to this object
   *         when invoked as an AQL table function
   */
  protected final TupleSchema getRuntimeArgSchema() {
    return runtimeInputSchema;
  }

  /**
   * {@inheritDoc}
   *
   * Subclasses can override this method to check the validity of tuple schemas given in an AQL
   * "create function" statement. The default implementation allows all schemas.
   * <p>
   * This method can be invoked in two different contexts: During AQL compilation or during operator
   * graph instantiation. If it is not possible to determine whether a schema is valid at compile
   * time (for example, because the UDF needs to load resources that are not present at compile
   * time), then this function should avoid generating an error at compile time.
   */
  @Override
  public void validateSchema(TupleSchema declaredInputSchema, TupleSchema runtimeInputSchema,
      TupleSchema returnSchema, Method methodInfo, boolean compileTime) throws TableUDFException {
    // Default implementation allows all schemas.
  }

  /**
   * {@inheritDoc}
   *
   * Callback to perform any initialization of internal state for the table function. Called once
   * per function instance when the operator graph is loaded. NOT called at compile time. The
   * default implementation does nothing.
   */
  @Override
  public void initState() throws TableUDFException {
    // Default implementation does nothing
  }

}
