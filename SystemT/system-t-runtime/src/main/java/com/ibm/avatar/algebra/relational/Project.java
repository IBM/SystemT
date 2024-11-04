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
package com.ibm.avatar.algebra.relational;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.base.RSEOperator;
import com.ibm.avatar.algebra.base.SingleInputOperator;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldCopier;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.RSEBindings;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;

/**
 * Relational projection on a bag of tuples packaged inside a set-valued attribute.
 * 
 */
public class Project extends SingleInputOperator implements RSEOperator {

  /** The names of the columns that remain in our output tuples. */
  private String[] outputColNames = null;

  /**
   * Names of corresponding columns of input tuples; project can rename columns as it filters them.
   */
  private String[] inputColNames = null;

  /**
   * Custom name to give to our output type, or null to generate an anonymous type.
   */
  private String outputTypeName = null;

  /** Accessor for copying the fields we keep. */
  private FieldCopier copier;

  /**
   * Flag that is set to true if this Project operator is just passing tuples through without
   * changing the actual field values. In this case, we skip the copy.
   */
  private boolean isPassThru = false;

  /**
   * Master constructor.
   * 
   * @param outputTypeName name of output type for this operator, or null to generate an anonymous
   *        output schema
   * @param inputColNames names of columns to keep from input tuples (a column can appear more than
   *        once), or null to pass through all columns unchanged
   * @param outputColNames names of corresponding output columns
   * @param child root of input subtree
   */
  public Project(String outputTypeName, String[] inputColNames, String[] outputColNames,
      Operator child) {
    super(child);

    this.outputTypeName = outputTypeName;
    this.inputColNames = inputColNames;
    this.outputColNames = outputColNames;

    // Actual decoding of the column spec is deferred until schema
    // resolution.
  }

  /**
   * Creates a new Project operator that projects down to a single column and does no renaming.
   * 
   * @param child the child operator
   * @param col the column to project down to
   */
  @Deprecated
  public Project(Operator child, String col) {
    this(null, new String[] {col}, new String[] {col}, child);
  }

  /**
   * Creates a new Project operator that projects down to multiple columns. Columns may be specified
   * as either names or column indexes.
   * 
   * @param child the child operator
   * @param keptCols the columns to project down to; each element is either a String (column name)
   *        or an Integer (column index)
   */
  // public Project(Operator child, ArrayList<String> keptCols) {
  // this(null, (String[]) keptCols.toArray(),
  // (String[]) keptCols.toArray(), child);
  // }

  /**
   * Evaluate the operator, building resulting tuples.
   */
  @Override
  public void reallyEvaluate(MemoizationTable mt, TupleList childResults) {

    if (isPassThru) {
      // SPECIAL CASE: Same input and output schemas; only the names
      // change
      addResultTups(childResults, mt);
      return;
      // END SPECIAL CASE
    }

    TLIter tupleIterator = childResults.iterator();
    // iterate over child tuples, building the new tuples
    while (tupleIterator.hasNext()) {
      Tuple childTuple = tupleIterator.next();

      Tuple resultTuple = createOutputTup();
      copier.copyVals(childTuple, resultTuple);

      addResultTup(resultTuple, mt);
    }
  }

  /**
   * Currently, we only support projecting down to a single column. This method creates the schema
   * for a 1-column tuple with the column type drawn from the source column of the child operator's
   * schema.
   */
  @Override
  protected AbstractTupleSchema createOutputSchema() {

    AbstractTupleSchema childSchema = inputs[0].getOutputSchema();

    if (null == inputColNames) {
      // SPECIAL CASE: Passing through all columns unchanged.
      inputColNames = childSchema.getFieldNames();
      outputColNames = inputColNames;
      // END SPECIAL CASE
    }

    if (inputColNames.length != outputColNames.length) {
      throw new RuntimeException("Incompatible arrays");
    }

    // Build up the list of column names and types.
    // String[] names = keptCols.toArray(new String[0]);
    FieldType[] types = new FieldType[inputColNames.length];

    for (int i = 0; i < inputColNames.length; i++) {
      types[i] = childSchema.getFieldTypeByName(inputColNames[i]);
    }

    // Now we can create the output schema.
    AbstractTupleSchema ret = new TupleSchema(outputColNames, types);
    ret.setName(outputTypeName);

    // Create the accessor that will copy fields for us.
    copier = ret.fieldCopier(childSchema, inputColNames, outputColNames);

    if (copier.isNoOp()) {
      isPassThru = true;
    }

    return ret;
  }

  @Override
  public TupleList[] getNextRSE(MemoizationTable mt, RSEBindings[] b) throws Exception {

    // We'll assume that the caller was smart enough to call implementsRSE()
    // first.
    RSEOperator child = (RSEOperator) inputs[0];

    TupleList[] childResults = child.getNextRSE(mt, b);

    // Apply the appropriate projection to the returned tuples.
    TupleList[] ret = new TupleList[childResults.length];
    for (int i = 0; i < childResults.length; i++) {
      ret[i] = new TupleList(getOutputSchema());

      for (TLIter itr = childResults[i].iterator(); itr.hasNext();) {
        Tuple childTup = itr.next();

        Tuple tup = createOutputTup();
        copier.copyVals(childTup, tup);
        ret[i].add(tup);
      }
    }

    return ret;
  }

  @Override
  public boolean implementsRSE() {
    // When running in RSE mode, Project will just pass bindings down to its
    // child.
    if (inputs[0] instanceof RSEOperator && ((RSEOperator) inputs[0]).implementsRSE()) {
      return true;
    } else {
      return false;
    }
  }

}
