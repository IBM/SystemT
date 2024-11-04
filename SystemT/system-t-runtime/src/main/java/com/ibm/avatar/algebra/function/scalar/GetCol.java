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
import java.util.TreeSet;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;

/**
 * Function that extracts and returns a column value from the input tuple. Column should be
 * specified as an integer or a string name.
 * 
 */
public class GetCol extends ScalarFunc {

  // Empty USAGE field; we don't want the hidden function GetCol() showing up in AQL error messages.
  public static final String USAGE = "";
  // public static final String USAGE = "GetCol(column_name)";

  /** Name of the column to fetch. */
  private String col;

  /** Accessor for getting at the requested column. */
  private FieldGetter<Object> colAcc;

  /** This function for the use of FastComparator only. */
  protected FieldGetter<Object> getColAcc() {
    return colAcc;
  }

  /** The actual type that we will return. */
  private FieldType returnType = null;

  /** Primary constructor; takes column indices directly as strings. */
  public GetCol(String col) throws ParseException {
    // Note how the superclass doesn't get passed any arguments.
    // GetCol() is a generated function that always sits at the leaf of a function call tree and has
    // no children, but
    // the AOG representation is GetCol("name of col").
    super(null, null);
    this.col = col;
  }

  @Override
  protected void validateArgTypes(ArrayList<FieldType> argTypes)
      throws FunctionCallValidationException {
    // Bypass the superclass's checks, since this node is at the leaf of the function call tree
  }

  @Override
  public void bindImpl(AbstractTupleSchema ts) throws FunctionCallValidationException {
    // Generate an accessor for the appropriate type of field.
    try {
      returnType = ts.getFieldTypeByName(col);
    } catch (RuntimeException e) {
      // Wrap the schema's RuntimeException in something more useful
      throw new FunctionCallValidationException(this, "Error retrieving column %s from schema %s",
          col, ts);
    }
    colAcc = ts.genericGetter(col, returnType);
  }

  @Override
  public FieldType returnType() throws FunctionCallValidationException {
    if (null == returnType) {
      throw new FatalInternalError("returnType() called before bind()");
    }
    return returnType;
  }

  @Override
  public Object reallyEvaluate(Tuple t, TupleList[] locatorArgs, MemoizationTable mt,
      Object[] evaluatedArgs) throws TextAnalyticsException {
    if (null == colAcc) {
      throw new IllegalStateException("reallyEvaluate() called before bind()");
    }

    // colAcc.setTup(t);
    // Leave the tuple pinned until we're done with its field.
    // return colAcc.getVal();

    // On second thought, don't pin -- rely on the caller to keep the tuple
    // pinned while working on it.
    return colAcc.getVal(t);
  }

  @Override
  protected void computeReferencedRels(TreeSet<String> set) {
    // Pick apart our function name.

    String qualifiedName = col;

    // qualifiedName is in the form "Tab.col"
    // Pick apart the column name into table and column.
    // String[] pieces = qualifiedName.split("\\.");
    // if (2 == pieces.length) {
    // String tabname = pieces[0];
    // set.add(tabname);
    // }
    final int pos = qualifiedName.indexOf('.');
    if (pos > 0) {
      set.add(qualifiedName.substring(0, pos));
    }
  }

  @Override
  public String toString() {

    if (null != colAcc) {
      return String.format("%s(%s (%d))", this.getClass().getSimpleName(), col, colAcc.getColIx());
    } else {
      return String.format("%s(%s)", this.getClass().getSimpleName(), col);
    }

  }

}
