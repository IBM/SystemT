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

import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldCopier;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.api.exceptions.TableUDFException;
import com.ibm.avatar.api.udf.TableUDFBase;

/**
 * User-defined table function for testing record locator arguments.
 * 
 */
public class TableConsumingTableFunc extends TableUDFBase
{

  /** Accessors for copying fields from input tuples to output tuples. */
  private FieldCopier arg1Copier, arg2Copier;

  /**
   * Main entry point to the table function. This function takes two lists of tuples and generates a new list of wide
   * tuples, where element i of the returned list is created by joining element i of the first input with element i of
   * the second input.
   * 
   * @param arg1 first set of tuples to merge
   * @param arg2 second set of tuples to merge
   * @return the two sets of tuples, interleaved
   */
  public TupleList eval (TupleList arg1, TupleList arg2)
  {

    TupleSchema retSchema = getReturnTupleSchema ();
    TupleList ret = new TupleList (retSchema);

    // We skip any records that go off the end
    int numRecs = Math.min (arg1.size (), arg2.size ());

    for (int i = 0; i < numRecs; i++) {
      Tuple retTuple = retSchema.createTup ();

      Tuple inTup1 = arg1.getElemAtIndex (i);
      Tuple inTup2 = arg2.getElemAtIndex (i);

      arg1Copier.copyVals (inTup1, retTuple);
      arg2Copier.copyVals (inTup2, retTuple);

      // System.err.printf ("%s + %s = %s\n", inTup1, inTup2, retTuple);

      ret.add (retTuple);
    }

    return ret;
  }

  /**
   * Initialize the internal state of the table function. In this case, we create accessors to copy fields from input
   * tuples to output tuples.
   * 
   * @see com.ibm.avatar.api.udf.TableUDFBase#initState() for detailed description
   */
  @Override
  public void initState () throws TableUDFException
  {
    // Create accessors to do the work of copying fields from input tuples to output tuples
    AbstractTupleSchema arg1Schema = getRuntimeArgSchema ().getFieldTypeByIx (0).getRecordSchema ();
    AbstractTupleSchema arg2Schema = getRuntimeArgSchema ().getFieldTypeByIx (1).getRecordSchema ();
    TupleSchema retSchema = getReturnTupleSchema ();

    // Create offsets tables for a straightforward copy.
    String[] srcs1 = new String[arg1Schema.size ()];
    String[] dests1 = new String[arg1Schema.size ()];
    String[] srcs2 = new String[arg2Schema.size ()];
    String[] dests2 = new String[arg2Schema.size ()];

    for (int i = 0; i < srcs1.length; i++) {
      srcs1[i] = arg1Schema.getFieldNameByIx (i);
      dests1[i] = retSchema.getFieldNameByIx (i);
    }
    for (int i = 0; i < srcs2.length; i++) {
      srcs2[i] = arg2Schema.getFieldNameByIx (i);
      dests2[i] = retSchema.getFieldNameByIx (i + srcs1.length);
    }

    arg1Copier = retSchema.fieldCopier (arg1Schema, srcs1, dests1);
    arg2Copier = retSchema.fieldCopier (arg2Schema, srcs2, dests2);
  }

  /**
   * Check the validity of tuple schemas given in the AQL 'create function'.
   * 
   * @see com.ibm.avatar.api.udf.TableUDFBase#validateSchema(TupleSchema, TupleSchema, TupleSchema, Method, boolean) for
   *      description of arguments
   */
  @Override
  public void validateSchema (TupleSchema declaredInputSchema, TupleSchema runtimeInputSchema,
    TupleSchema returnSchema, Method methodInfo, boolean compileTime) throws TableUDFException
  {
    // The output schema should contain the columns of the two input schemas in order.
    AbstractTupleSchema arg1Schema = declaredInputSchema.getFieldTypeByIx (0).getRecordSchema ();
    AbstractTupleSchema arg2Schema = declaredInputSchema.getFieldTypeByIx (1).getRecordSchema ();

    System.err.printf ("TableConsumingTableFunc: Input schemas are %s and %s\n", arg1Schema, arg2Schema);

    // First check sizes
    if (returnSchema.size () != arg1Schema.size () + arg2Schema.size ()) { throw new TableUDFException (
      "Schema sizes don't match (%d + %d != %d)", arg1Schema.size (), arg2Schema.size (), returnSchema.size ()); }

    // Then check types
    for (int i = 0; i < arg1Schema.size (); i++) {
      if (false == (arg1Schema.getFieldTypeByIx (i).equals (returnSchema.getFieldTypeByIx (i)))) { throw new TableUDFException (
        "Field type %d of output doesn't match corresponding field of first input arg (%s != %s)", i,
        returnSchema.getFieldTypeByIx (i), arg1Schema.getFieldTypeByIx (i)); }
    }

    for (int i = 0; i < arg2Schema.size (); i++) {
      if (false == (arg2Schema.getFieldTypeByIx (i).equals (returnSchema.getFieldTypeByIx (i + arg1Schema.size ())))) { throw new TableUDFException (
        "Field type %d of output doesn't match corresponding field of first input arg (%s != %s)", i
          + arg1Schema.size (), returnSchema.getFieldTypeByIx (i + arg1Schema.size ()), arg2Schema.getFieldTypeByIx (i)); }
    }
  }

}
