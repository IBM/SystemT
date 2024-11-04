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
package com.ibm.avatar.algebra.base;

import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.logging.Log;

/**
 * Base class for operators that do local analysis and have a single input stream. We handle these
 * operators separately for performance reasons.
 * 
 */
public abstract class SingleInputOperator extends Operator {

  /** Constructor for an operator with a single child. */
  protected SingleInputOperator(Operator child) {
    super(child);
  }

  //
  // METHODS
  //

  @Override
  protected void getNextInternal(MemoizationTable mt) throws Exception {

    // Check for watchdog interrupts.
    // if (Thread.interrupted()) {
    if (mt.interrupted) {
      mt.interrupted = false;
      throw new InterruptedException();
    }

    // Make sure that our schema is set up.
    getOutputSchema();

    // Read our input tuples
    TupleList childResults = getInputOp(0).getNext(mt);

    if (TRACE_OPERATORS) {
      Log.info("%s: Calling reallyEvaluate()", this);
    }

    reallyEvaluate(mt, childResults);

  }

  /**
   * Method that does the hard work of evaluating the operator once the results, inputs, and schema
   * are set up.
   * 
   * @param mt thread-local table for holding persistent data across function calls
   * @param childResults
   * @throws Exception
   */
  protected abstract void reallyEvaluate(MemoizationTable mt, TupleList childResults)
      throws Exception;

}
