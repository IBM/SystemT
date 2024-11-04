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
package com.ibm.avatar.algebra.output;

import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.base.SingleInputOperator;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.TupleList;

/**
 * Operator that just passes through its input tuples.
 * 
 */
public class Passthru extends SingleInputOperator {

  /** Internal counter of how many interior tuples we've passed through. */
  private int totPassed = 0;

  /**
   * Default constructor;
   * 
   * @param inputs roots of the subtrees that feed into this sink.
   */
  public Passthru(Operator input) {
    super(input);

  }

  @Override
  protected AbstractTupleSchema createOutputSchema() {
    return getInputOp(0).getOutputSchema();
  }

  public int getNumTupsSeen() {
    return totPassed;
  }

  @Override
  protected void reallyEvaluate(MemoizationTable mt, TupleList childResults) throws Exception {
    totPassed += childResults.size();

    addResultTups(childResults, mt);
  }

  // @Override
  // protected void getNextInternal(MemoizationTable mt)
  // throws Exception {
  // TupleList ret = getInputOp(0).getNext(mt);
  //
  // totPassed += ret.size();
  //
  // addResultTups(ret, mt);
  // }
}
