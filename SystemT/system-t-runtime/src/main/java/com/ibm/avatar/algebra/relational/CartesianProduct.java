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
import com.ibm.avatar.algebra.base.MultiInputOperator;
import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldCopier;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;

/**
 * Evaluates the Cartesian product of two TupleSets, partitioned by document.
 */
public class CartesianProduct extends MultiInputOperator {
  /** Which of the operator's inputs is the inner relation of the join. */
  public static final int INNER_IX = 1;

  public static final int OUTER_IX = 0;

  /** Accessor for copying fields from the outer. */
  protected FieldCopier outerCopier;

  /** Accessor for copying fields from the inner. */
  protected FieldCopier innerCopier;

  /**
   * Enumeration of the different types of constant input an instance of this operator or its
   * children could have.
   */
  protected static enum ConstInput {
    /** Both inputs dynamic */
    NONE,
    /** Inner constant, outer dynamic */
    INNER,
    /** Outer constant, inner dynamic */
    OUTER,
    /** Both inputs const; compute the answer once. */
    BOTH
  }

  /**
   * Flag that indicates whether either of the inputs to this join is guaranteed to produce the same
   * answer every time.
   */
  private ConstInput constInputState = null;

  protected ConstInput getConstInputState() {
    return constInputState;
  }

  /**
   * Builds the operator.
   * 
   * @param first the operator to get the first TupleList from
   * @param second the operator to get the second TupleList from
   */
  public CartesianProduct(Operator first, Operator second) {
    super(first, second);
  }

  /**
   * Constructor for join operators that take locator arguments.
   * 
   * @param first the operator to get the first TupleList from
   * @param second the operator to get the second TupleList from
   * @param locatorArgs operators that produce any locator arguments
   */
  protected CartesianProduct(Operator first, Operator second, Operator[] locatorArgs) {
    super(makeInputsArray(first, second, locatorArgs));
  }

  /**
   * Helper method for constructor above; creates the arguments array in the format that the
   * superclass expects.
   */
  private static Operator[] makeInputsArray(Operator outer, Operator inner,
      Operator[] locatorArgs) {
    Operator[] ret = new Operator[locatorArgs.length + 2];
    ret[0] = outer;
    ret[1] = inner;
    System.arraycopy(locatorArgs, 0, ret, 2, locatorArgs.length);
    return ret;
  }

  /**
   * Evaluates the Cartesian product.
   */
  @Override
  protected void reallyEvaluate(MemoizationTable mt, TupleList[] childResults) throws Exception {

    // iterate over first child
    TLIter firstTuples = childResults[0].iterator();
    while (firstTuples.hasNext()) {
      // iterate over second child
      TLIter secondTuples = childResults[1].newIterator();
      Tuple firstTuple = firstTuples.next();
      while (secondTuples.hasNext()) {
        Tuple secondTuple = secondTuples.next();

        Tuple newTuple = createOutputTup();
        outerCopier.copyVals(firstTuple, newTuple);
        innerCopier.copyVals(secondTuple, newTuple);

        addResultTup(newTuple, mt);
      }
    }
  }

  @Override
  protected AbstractTupleSchema createOutputSchema() {
    boolean debug = false;

    AbstractTupleSchema outerSchema = outer().getOutputSchema();
    AbstractTupleSchema innerSchema = inner().getOutputSchema();

    // We add the inner schema to the end of the inner schema.
    String[] namesToAdd = innerSchema.getFieldNames();
    FieldType[] typesToAdd = innerSchema.getFieldTypes();

    AbstractTupleSchema ret = new TupleSchema(outerSchema, namesToAdd, typesToAdd);

    // Set up tuple field accessors.
    innerCopier = ret.fieldCopier(innerSchema);
    outerCopier = ret.fieldCopier(outerSchema);

    if (debug) {
      System.err.printf("CartesianProduct.createInteriorSchema():\n" + "  Outer schema: %s\n"
          + "  Inner schema: %s\n" + "  Output schema: %s\n", outerSchema, innerSchema, ret);
    }

    return ret;
  }

  /**
   * @return the inner operand of the cross-product
   */
  protected Operator inner() {
    return getInputOp(INNER_IX);
  }

  /**
   * @return the outer operand of the cross-product
   */
  protected Operator outer() {
    return getInputOp(OUTER_IX);
  }

  @Override
  protected void initStateInternal(MemoizationTable mt) throws TextAnalyticsException {
    // Make sure any initialization in the superclass still happens.
    super.initStateInternal(mt);

    // Check whether one or both inputs always produces the same answer.
    // HashJoin uses this information to avoid rebuilding hash tables
    // unnecessarily.
    boolean outerIsConst = outer().producesConstOutput();
    boolean innerIsConst = inner().producesConstOutput();

    if (innerIsConst && outerIsConst) {
      constInputState = ConstInput.BOTH;
    } else if (innerIsConst && !outerIsConst) {
      constInputState = ConstInput.INNER;
    } else if (!innerIsConst && outerIsConst) {
      constInputState = ConstInput.OUTER;
    } else {
      constInputState = ConstInput.NONE;
    }

  }

}
