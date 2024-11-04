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

import com.ibm.avatar.algebra.base.Tee.TeeOutput;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.logging.Log;

/**
 * Base class for operators that do local analysis and have multiple inputs.
 * 
 */
public abstract class MultiInputOperator extends Operator {

  /** Accessors for getting interior tuples out of our input tuples. */
  // protected FieldGetter<TupleList> inputsAcc[];
  /**
   * Set this flag to TRUE to enable conditional evaluation for this operator; if any input produces
   * no tuples, don't bother evaluating subsequent ones.
   */
  private boolean conditionalEval = false;

  /**
   * If {@link #conditionalEval} is set to true, a list of TeeOutputs whose buffers need to be reset
   * when not used.
   */
  private TeeOutput[][] toReset = null;

  //
  // CONSTRUCTORS
  //

  /** Constructor for an operator with <bf>no</bf> children (e.g. a scan) */
  protected MultiInputOperator() {
    super(new Operator[0]);
  }

  /** Constructor for an operator with two children. */
  protected MultiInputOperator(Operator leftChild, Operator rightChild) {
    super(leftChild, rightChild);
  }

  /** Constructor for an operator with an arbitrary number of children. */
  protected MultiInputOperator(Operator children[]) {
    super(children);
  }

  //
  // METHODS
  //

  protected void setConditionalEval(boolean conditionalEval) {
    this.conditionalEval = conditionalEval;

    if (conditionalEval) {
      // Figure out which Tee outputs need to have their buffers reset
      // when we skip reading the inner operand(s) due to conditional
      // eval.
      toReset = new TeeOutput[inputs.length][];
      for (int i = 1; i < inputs.length; i++) {
        TeeOutput[] targets = Tee.getBufClearTargets(inputs[i]);
        if (null == targets) {
          toReset[i] = new TeeOutput[0];
        } else {
          toReset[i] = targets;
        }
      }
    }
  }

  @Override
  protected void getNextInternal(MemoizationTable mt) throws Exception {

    // Check for watchdog interrupts.
    // if (Thread.interrupted()) {
    if (mt.interrupted) {
      mt.interrupted = false;
      throw new InterruptedException();
    }

    TupleList[] childResults = prepareInputs(mt);

    if (null == childResults) {
      // We were shortcutted by conditional evaluation; don't call
      // reallyEvaluate()
    } else {
      if (TRACE_OPERATORS) {
        Log.info("%s: Calling reallyEvaluate()", this);
      }

      reallyEvaluate(mt, childResults);

    }
  }

  /**
   * Method that makes sure that inputs are ready to consume. Works by calling getNext() on all the
   * inputs and packing the resultant tuple sets into childResults.
   * 
   * @param mt
   * @return the results of the child operators
   * @throws Exception oops
   */
  protected TupleList[] prepareInputs(MemoizationTable mt) throws Exception {

    // Make sure that our schema is set up.
    getOutputSchema();

    // This flag will be set to true if one or more of our inputs produces
    // zero tuples. The flag is used when conditional evaluation is enabled.
    boolean haveEmptyInput = false;

    // Create the result buffer.
    TupleList[] childResults = new TupleList[inputs.length];

    if (2 == inputs.length) {
      // SPECIAL CASE:
      // The current crop of JVMs is stupid about optimizing short loops.
      // Get around this problem by special-casing the 2-input case.

      childResults[0] = inputs[0].getNext(mt);

      if (conditionalEval && 0 == childResults[0].size()) {
        // Conditional evaluation, and no outer results. Set up a dummy
        // input, and clear any buffers below the inner.
        childResults[1] = new TupleList(inputs[1].getOutputSchema());
        for (TeeOutput todo : toReset[1]) {
          todo.clearCachedState(mt);
        }
        // return null;
      } else {
        childResults[1] = inputs[1].getNext(mt);
      }
      // END SPECIAL CASE
    } else {

      for (int i = 0; i < inputs.length; i++) {
        Operator in = inputs[i];

        if (conditionalEval && haveEmptyInput) {
          // Skipping this input due to conditional evaluation.
          childResults[i] = new TupleList(in.getOutputSchema());

          // Clear buffers below this input, too.
          for (TeeOutput todo : toReset[i]) {
            todo.clearCachedState(mt);
          }
        } else {

          // System.err.printf("%s got input tuple %s\n", this,
          // inTup);
          childResults[i] = in.getNext(mt);

          // Check for an empty input, in case conditional eval is
          // enabled.
          if (0 == childResults[i].size()) {
            haveEmptyInput = true;
          }
        }
      }
    }

    return childResults;
  }

  /**
   * Method that does the hard work of evaluating the operator once the results, inputs, and schema
   * are set up.
   * 
   * @param mt table for holding data across calls
   * @param childResults results for all the child operators of this one
   * @param results place to put the output tuples of this operator
   * @throws Exception
   */
  protected abstract void reallyEvaluate(MemoizationTable mt, TupleList[] childResults)
      throws Exception;

}
