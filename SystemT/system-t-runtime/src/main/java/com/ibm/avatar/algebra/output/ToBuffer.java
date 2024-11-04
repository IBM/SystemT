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
/**
 * 
 */
package com.ibm.avatar.algebra.output;

import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.TupleList;

/**
 * Operator that dumps its input to an internal buffer for further processing by the driver program.
 * As with other output operators, the input of this operator needs to consist of boxed sets of
 * tuples a la {@link Operator}, where each interior tuple consists of a single Annotation element.
 * 
 */
public class ToBuffer extends Operator {

  private static final int NOT_AN_INDEX = -1;

  private int bufferIx = NOT_AN_INDEX;

  public ToBuffer(Operator child) {
    super(child);
  }

  @Override
  protected void initStateInternal(MemoizationTable mt) {
    if (NOT_AN_INDEX == bufferIx) {
      bufferIx = mt.createResultsCache(this);
    }
  }

  /**
   * @return the most recent set of result tuples the operator has received.
   */
  public TupleList getBuf(MemoizationTable mt) {
    return mt.getCachedResults(bufferIx);
  }

  @Override
  protected AbstractTupleSchema createOutputSchema() {
    // Just pass through the input schema.
    return getInputOp(0).getOutputSchema();
  }

  /**
   * For INTERNAL use only! Manually put into place a new list of output tuples.
   */
  public void overrideOutput(TupleList tupleList, MemoizationTable mt) {
    // Make a copy, since the source may be a reusable buffer.
    TupleList copy = new TupleList(tupleList);
    mt.cacheResults(bufferIx, copy, true);
  }

  /**
   * We override this method to avoid unnecessary copies.
   */
  @Override
  public TupleList getNext(MemoizationTable mt) throws Exception {
    TupleList results = getInputOp(0).getNext(mt);

    // The memoization table acts as our buffer. That way, the operator
    // itself remains stateless.
    // Make a copy, since the source may be a reusable buffer.
    TupleList copy = new TupleList(results);
    mt.cacheResults(bufferIx, copy);

    return copy;
  }

  @Override
  protected void getNextInternal(MemoizationTable mt) throws Exception {
    throw new RuntimeException("Should never be called");
  }

  /**
   * We override this method to return false, since output buffers always create a new copy of their
   * tuples for each call.
   */
  @Override
  protected boolean outputIsAlwaysTheSame() {
    return false;
  }
}
