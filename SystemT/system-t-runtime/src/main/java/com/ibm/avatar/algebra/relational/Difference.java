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

import java.util.TreeSet;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.base.MultiInputOperator;
import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldCopier;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;

/**
 * Computes the set difference of two the results of two operators. If the comparator argument is
 * not used in the constructor, tuple equality is based on entity identity / primitive value.
 * Evaluate performs first - second
 * 
 */
public class Difference extends MultiInputOperator {

  /**
   * Set to TRUE to use conditional evaluation (e.g. don't evaluate the second argument if the first
   * produces no tuples).
   */
  public static final boolean CONDITIONAL_EVAL = true;

  /** Accessors for copying fields from input to output tuples. */
  private FieldCopier firstCopier, secondCopier;

  /**
   * Builds a new set difference operator from two child operators.
   * 
   * @param firstChild the first child operator
   * @param secondChild the second child operator
   */
  public Difference(Operator firstChild, Operator secondChild) {
    super(firstChild, secondChild);

    setConditionalEval(CONDITIONAL_EVAL);
  }

  /**
   * Performs the set difference (first - second)
   */
  @Override
  public void reallyEvaluate(MemoizationTable mt, TupleList[] childResults) {

    TreeSet<Tuple> secondSet = new TreeSet<Tuple>();
    for (TLIter itr = childResults[1].iterator(); itr.hasNext();) {
      Tuple inTup = itr.next();

      // We copy the fields to ensure that every tuple we insert into our
      // set has the same low-level schema.
      Tuple copy = createOutputTup();
      secondCopier.copyVals(inTup, copy);
      secondSet.add(copy);
    }

    for (TLIter itr = childResults[0].iterator(); itr.hasNext();) {
      Tuple inTup = itr.next();

      // We copy the fields to ensure that every tuple we insert into our
      // set has the same low-level schema.
      Tuple copy = createOutputTup();
      firstCopier.copyVals(inTup, copy);

      if (!(secondSet.contains(copy))) {
        addResultTup(copy, mt);
      }
    }

  }

  @Override
  protected AbstractTupleSchema createOutputSchema() {

    AbstractTupleSchema firstSchema = getInputOp(0).getOutputSchema();
    AbstractTupleSchema secondSchema = getInputOp(1).getOutputSchema();

    if (false == firstSchema.unionCompatible(secondSchema)) {
      throw new RuntimeException(String.format(
          "Input schemas to set difference (%s and %s) not compatible", firstSchema, secondSchema));
    }

    // We'll be copying input tuples, so create a base schema.
    TupleSchema ret = new TupleSchema(firstSchema, true);

    firstCopier = ret.fieldCopier(firstSchema);
    secondCopier = ret.fieldCopier(secondSchema);

    return ret;
  }
}
