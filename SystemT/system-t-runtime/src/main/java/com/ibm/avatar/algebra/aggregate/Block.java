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
package com.ibm.avatar.algebra.aggregate;

import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.base.SingleInputOperator;
import com.ibm.avatar.algebra.datamodel.TupleComparator;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.FieldSetter;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.function.scalar.GetBegin;
import com.ibm.avatar.algebra.function.scalar.GetCol;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;

/**
 * Base class for the two variants of the Block operator. Contains code that is common between the
 * character-based and token-based implementations.
 * 
 */
public abstract class Block extends SingleInputOperator {

  /**
   * Minimum number of annotations that the operator will consider in a block
   */
  protected int minSize;

  protected int maxSize;

  /**
   * Index of the annotations that we merge into blocks.
   */
  protected String col;

  /**
   * Name of the column in the output schema that contains the blocks. Currently, this column is the
   * only column in the output schema.
   */
  protected String outputCol;

  /**
   * Accessors to get at the input spans. We have two accessors, because we look at two tuples at
   * once.
   */
  protected FieldGetter<Span> startAcc, endAcc;

  /**
   * Setter for putting our result into place.
   */
  protected FieldSetter<Span> outAcc;

  /**
   * Name of the single column in our output schema, if none is given
   */
  public static final String DEFAULT_OUTPUT_NAME = "block";

  /**
   * Comparator for sorting tuples by span begin. Initialized during {@link #createInteriorSchema()}
   */
  TupleComparator sortOrder;

  /**
   * Create a new Block operator.
   * 
   * @param minSize minimum number of annotations that the operator will consider in a block
   * @param maxSize maximum number of annotations that the operator will consider in a block
   * @param col column containing the spans to merge into blocks
   * @param outputCol name of output column, or NULL to use the default "block"
   * @param input root of the operator tree that produces our inputs
   */
  protected Block(int minSize, int maxSize, String col, String outputCol, Operator input) {
    super(input);

    if (minSize < 1) {
      throw new IllegalArgumentException(
          "Block operator can only produce " + "blocks of size 1 or greater");
    }

    this.minSize = minSize;
    this.maxSize = maxSize;
    this.col = col;
    this.outputCol = null == outputCol ? DEFAULT_OUTPUT_NAME : outputCol;
  }

  @Override
  protected AbstractTupleSchema createOutputSchema() {
    // Bind to the input schema.
    AbstractTupleSchema inputSchema = inputs[0].getOutputSchema();
    startAcc = inputSchema.spanAcc(col);
    endAcc = inputSchema.spanAcc(col);

    // Set up the sort comparator.
    try {
      GetBegin gb = new GetBegin(new GetCol(col));
      gb.oldBind(inputSchema);
      sortOrder = TupleComparator.makeComparator(gb);
    } catch (ParseException e) {
      // This kind of error should be caught in the AQL parser!!!
      throw new FatalInternalError(e);
    } catch (FunctionCallValidationException e) {
      // This kind of error should be caught in the AQL compiler!!!
      throw new FatalInternalError(e);
    }

    // Output schema is always the same:
    // <span that covers block>
    String[] colnames = new String[] {outputCol};
    FieldType[] coltypes = new FieldType[] {FieldType.SPAN_TYPE};
    AbstractTupleSchema interiorOutputSchema = new TupleSchema(colnames, coltypes);

    outAcc = interiorOutputSchema.spanSetter(outputCol);
    return interiorOutputSchema;
  }

  @Override
  protected void reallyEvaluate(MemoizationTable mt, TupleList childResults) throws Exception {

    // Copy the input, then sort it.
    TupleList input = new TupleList(childResults);
    input.sort(sortOrder);

    // Subclasses do the rest.
    findBlocks(mt, input);
  }

  /**
   * Subclasses should override this method to perform the actual work of finding blocks in the
   * sorted input and producing output tuples.
   * 
   * @param input input tuples, sorted by the begin attribute of the target spans.
   */
  protected abstract void findBlocks(MemoizationTable mt, TupleList input) throws Exception;
}
