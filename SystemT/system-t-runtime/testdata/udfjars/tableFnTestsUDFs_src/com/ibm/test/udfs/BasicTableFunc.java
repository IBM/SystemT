/*******************************************************************************
* Copyright IBM
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*******************************************************************************/
package com.ibm.test.udfs;

import java.lang.reflect.Method;

import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.api.exceptions.TableUDFException;
import com.ibm.avatar.api.udf.TableUDFBase;

/**
 * A simple AQL table function for verifying that table functions are working. Takes a single integer as an argument.
 * Returns tuples with the schema (Text, Real). The number of returned tuples is given by the integer argument.
 */
public class BasicTableFunc extends TableUDFBase
{

  /** The universe of values that can be returned. */
  private static final String[][] RETURNED_TUPS = {
    // TupleSchema.objsToTups() expects things in column-major order.
    { "One", "Two", "Three", "Four" }, { "1.0", "2.0", "3.0", "4.0" } };

  /** Flag that is set to TRUE if the initState() callback has been invoked. */
  private boolean initStateCalled = false;

  public TupleList eval (Integer intArg)
  {
    TupleSchema schema = getReturnTupleSchema ();
    if (null == schema) { throw new NullPointerException ("Null schema ptr returned by getReturnTupleSchema()"); }

    // Pull off the appropriate number of argument values
    // Note that the array of strings is in column-major order.
    String[][] tupStrs = new String[RETURNED_TUPS.length][];

    for (int i = 0; i < tupStrs.length; i++) {
      String[] truncated = new String[intArg];
      System.arraycopy (RETURNED_TUPS[i], 0, truncated, 0, truncated.length);
      tupStrs[i] = truncated;
    }

    return schema.objsToTups (tupStrs, (TupleSchema) schema);

  }

  @Override
  public void validateSchema (TupleSchema declaredInputSchema, TupleSchema runtimeInputSchema, TupleSchema returnSchema, Method methodInfo, boolean compileTime) throws TableUDFException
  {
    if (compileTime) {
      System.err.printf ("BasicTableFunc class performing compile-time schema check.\n");

      // At compile time, just check the number of columns.
      if (2 != returnSchema.size ()) { throw new TableUDFException ("Schema has the wrong number of columns."); }
    }
    else {
      System.err.printf ("BasicTableFunc class performing runtime schema check.\n");

      // Runtime check --> Check the column types too. Well, one of them.
      if (false == returnSchema.getFieldTypeByIx (0).equals (FieldType.TEXT_TYPE)) { throw new TableUDFException ( "First column in schema is not Text"); }
    }
  }

  // Implement the initState() callback for testing purposes.
  @Override
  public void initState () throws TableUDFException
  {
    if (initStateCalled) { throw new TableUDFException ("initState() called twice."); }
    initStateCalled = true;
  }

}
