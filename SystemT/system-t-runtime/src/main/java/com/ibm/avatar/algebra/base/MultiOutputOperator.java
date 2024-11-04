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

/**
 * Abstract base class for operators that have multiple outputs, each of which returns different
 * results.
 */
public abstract class MultiOutputOperator extends Tee {

  /**
   * Performance counter name used for the "multi-output result creation" counter.
   */
  // public static final String RESULT_PERF_COUNTER_NAME = "Multi-Output Result Creation";

  public MultiOutputOperator(Operator child, int noutputs) {
    super(child, noutputs);
  }

  @Override
  protected void advanceAll(MemoizationTable mt) throws Exception {

    mt.profileEnter(profRecord);

    // Get the next batch of input tuples from the child subtree.
    TupleList childTups = child.getNext(mt);

    if (null == childTups) {
      throw new RuntimeException("Received null input tuple");
    }

    // Create the tuple lists that will go to our outputs.
    TupleList[] outputLists = new TupleList[outputs.length];
    for (int i = 0; i < outputLists.length; i++) {
      outputLists[i] = new TupleList(outputs[i].getOutputSchema());
    }

    // Let the subclass fill up the output buffers.
    advanceAllInternal(mt, childTups, outputLists);

    // Propagate results to the output buffers.
    for (int i = 0; i < outputs.length; i++) {
      outputs[i].setCurtups(outputLists[i], mt);
    }

    mt.profileLeave(profRecord);

  }

  protected abstract void advanceAllInternal(MemoizationTable mt, TupleList childTups,
      TupleList[] outputLists) throws Exception;

}
