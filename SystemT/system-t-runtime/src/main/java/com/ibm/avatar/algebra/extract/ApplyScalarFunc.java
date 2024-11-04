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
package com.ibm.avatar.algebra.extract;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.base.MultiInputOperator;
import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldCopier;
import com.ibm.avatar.algebra.datamodel.FieldSetter;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.TextSetter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.RValueNode;

/**
 * Operator that applies a generic scalar function to its input tuples. The output schema of the
 * operator consists of the input, plus an additional column to hold the function's result. If the
 * output name is set to the empty string, then this operator will <b>not</b> add the name to the
 * output.
 * 
 */
public class ApplyScalarFunc extends MultiInputOperator {

  /**
   * Special delimiter string used by {@link RValueNode#setAutoColumnName(String, String, String)}
   * to encode the view and column name as a string.
   */
  private static final String ENCODED_COLNAME_DELIM = "@@";

  private ScalarFunc func;

  /** Name given to the output column in the schema */
  private String outputName;

  /** Output name, parsed and converted to an AQL view/column pair, if applicable. */
  private Pair<String, String> outputViewAndCol = null;

  /** Accessor for copying the unchanged fields from the input to the output. */
  private FieldCopier copier;

  /** Accessor for putting the function result into place. */
  private FieldSetter<Object> outputAcc;

  /** Special text setter for when {@link #convertStrs} is set to TRUE. */
  private TextSetter textOutputAcc;

  /**
   * Flag that indicates that this operator should convert Java String objects returned by its
   * function to Text fields.
   */
  private boolean convertStrs = false;
  private String funcname = null;

  /**
   * For each of the first (n-1) children, i.e. the locator inputs, the fully qualified names of the
   * target views/tables
   */
  private String[] locatorTargetNames;

  /**
   * Main constructor.
   * 
   * @param func root of function call tree
   * @param outputName name of the output column that the function's result goes into
   * @param children input operators; by convention, the *last* child is the "main" input
   * @param locatorTargetNames for the first (n-1) children, i.e. the locator inputs, the fully
   *        qualified names of the target views/tables
   * @param funcname name of the function
   */
  public ApplyScalarFunc(ScalarFunc func, String outputName, Operator[] children,
      String[] locatorTargetNames, String funcname) { // irs
    super(children);

    this.locatorTargetNames = locatorTargetNames;

    this.outputName = outputName;
    this.func = func;
    this.funcname = funcname; // irs
  }

  @Override
  protected void reallyEvaluate(MemoizationTable mt, TupleList[] childResults) throws Exception {

    // By convention, the primary input is the last.
    TupleList primaryChildResults = childResults[childResults.length - 1];

    TLIter tupleIterator = primaryChildResults.iterator();
    while (tupleIterator.hasNext()) {
      Tuple childTuple = tupleIterator.next();

      Object ret;
      try {
        // childResults has an extra TupleList at the end of the array, but that's ok
        ret = func.evaluate(childTuple, childResults, mt);
      } catch (RuntimeException e) {
        // Augment error messages from
        String newErrMsg = String.format("In view '%s': %s", super.getViewName(), e.getMessage());
        throw new RuntimeException(newErrMsg, e);
      }

      Tuple resultTuple = createOutputTup();
      copier.copyVals(childTuple, resultTuple);

      if (convertStrs) {
        // SPECIAL CASE: Converting strings to Text fields.
        // System.err.printf("Converting %s from string to Text.\n",
        // outputName);
        textOutputAcc.setVal(resultTuple, (String) ret);
        // END SPECIAL CASE
      } else {
        if (ret instanceof Text && null != outputViewAndCol) {
          // Add field information to Text objects returned by functions.
          ((Text) ret).setViewAndColumnName(outputViewAndCol);
        }

        // Normal case.
        outputAcc.setVal(resultTuple, ret);
      }

      addResultTup(resultTuple, mt);
    }
  }

  @Override
  protected AbstractTupleSchema createOutputSchema() {
    AbstractTupleSchema primaryInputSchema = inputs[inputs.length - 1].getOutputSchema();

    // Grab schemas of any relations referenced via locators; by convention, these inputs come as
    // the first n-1 inputs
    // to the operator.
    AbstractTupleSchema[] locatorSchemas = new AbstractTupleSchema[inputs.length - 1];

    for (int i = 0; i < locatorSchemas.length; i++) {
      locatorSchemas[i] = inputs[i].getOutputSchema();
    }

    // System.err.printf("%s: Input schema %s\n", this,
    // interiorInputSchema);

    AbstractTupleSchema outputSchema;

    // Prepare the function to run over our input schema.
    try {
      func.bind(primaryInputSchema, locatorSchemas, locatorTargetNames);

      FieldType retType = func.returnType();

      // If the output is of type Text, the Text object needs to be labeled with the view and column
      // name, so that
      // downstream code knows where the Text object came from. Unfortunately, the ApplyScalarFunc
      // operator operates
      // over an unnamed temporary schema, not the schema of the select list, so the column name
      // information isn't
      // directly available. So we parse the view and column name encoded in the name of the
      // temporary target column.
      // Note that this code needs to be kept in sync with RValueNode#setAutoColumnName(String,
      // String, String), which
      // does the encoding.
      // This encoding and decoding stuff should be gotten rid of when we get around to merging the
      // functionality of the
      // ApplyScalarFunc operator with the Project operator.
      if (outputName.startsWith(ENCODED_COLNAME_DELIM)) {
        String[] vals = outputName.split(ENCODED_COLNAME_DELIM);
        // System.err.printf ("%s ==> %s\n", outputName, Arrays.toString (vals));

        // Ignore the array if it isn't the right length; too short indicates legacy AOG with no
        // name available.
        if (4 == vals.length) {
          String moduleName = vals[1];
          String viewName = vals[2];
          String colName = vals[3];

          // Note that we generate a qualified view name, provided that this view wasn't compiled
          // with the compatibility
          // API.
          if (Constants.GENERIC_MODULE_NAME.equals(moduleName)) {
            outputViewAndCol = new Pair<String, String>(viewName, colName);
          } else {
            outputViewAndCol = new Pair<String, String>(moduleName + "." + viewName, colName);
          }
        }
      }

      if ((FieldType.STRING_TYPE == retType) && (funcname.compareTo("GetString") != 0)) { // irs
        // SPECIAL CASE:
        // Function returns strings; we need to convert them to
        // Text fields.
        outputSchema = new TupleSchema(primaryInputSchema, outputName);
        convertStrs = true;
        outputSchema.getFieldTypeByName(outputName);
        textOutputAcc = outputSchema.textSetter(outputName);

        // The output schema of the operator is not the final view schema, so we need to put the
        // view/column information
        // in manually.
        textOutputAcc.setViewAndColumnName(outputViewAndCol);

        outputAcc = null;
        // END SPECIAL CASE
      } else {
        // Normal case.
        outputSchema = new TupleSchema(primaryInputSchema, outputName, func.returnType());
        outputAcc = outputSchema.genericSetter(outputName, retType);
        textOutputAcc = null;
      }

      // Create the accessor we will use to copy values from input tuples
      // to output tuples.
      copier = outputSchema.fieldCopier(primaryInputSchema);

    } catch (FunctionCallValidationException e) {
      // This problem should have been caught during compilation
      throw new FatalInternalError(e);
    }

    return outputSchema;
  }

  @Override
  protected void initStateInternal(MemoizationTable mt) throws TextAnalyticsException {
    func.initState(mt);
  }

  @Override
  protected boolean requiresLemmaInternal() {
    return func.requiresLemma();
  }

  /**
   * @return return the scalar function name
   */
  public String getFuncName() {
    return funcname;
  }

  @Override
  public boolean outputIsAlwaysTheSame() {
    // Added instead of TransformResults.
    if (func.isDeterministic() && super.outputIsAlwaysTheSame()) {
      return true;
    }
    return false;
  }

}
