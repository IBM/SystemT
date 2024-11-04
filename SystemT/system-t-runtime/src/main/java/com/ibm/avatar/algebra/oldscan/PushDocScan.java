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
package com.ibm.avatar.algebra.oldscan;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.base.OutputBuffer;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.scan.DocScanInternal;

/** Document scanner for pushing documents into an operator graph one at a time. */
public class PushDocScan extends DocScanInternal {

  /**
   * Output buffer object; used for storing the document tuples that are pushed into this operator.
   * We use the MemoizationTable's buffering facilities to ensure that this operator has no
   * persistent state.
   */
  private static class docBuf extends OutputBuffer {

    Tuple docTup;

    private docBuf(Tuple docTup) {
      this.docTup = docTup;
    }

    @Override
    public void close() {
      // Nothing to do here.
    }

  }

  // protected PushItr<Tuple> itr = new PushItr<Tuple>();

  public PushDocScan() {
    super();
  }

  /**
   * @param docSchema schema for the tuples that will be pushed into this operator
   */
  public PushDocScan(AbstractTupleSchema docSchema) {
    super(docSchema);
  }

  /**
   * Put in place the next document for processing.
   * 
   * @param docTup the document tuple
   * @param mt the memoization table
   */
  public void setDoc(Tuple docTup, MemoizationTable mt) {
    // System.err.printf("%s setting doc %s\n", this, docTup.getOid());
    mt.cacheOutputBuf(this, new docBuf(docTup), true);
  }

  /**
   * Call this method when no more documents will be sent. Causes output buffers to be flushed and
   * closed. The next call to getNext() will return an empty tuple.
   */
  public void setDone(MemoizationTable mt) {
    // itr.setDone();
    mt.setEndOfInput();
  }

  @Override
  protected Tuple getNextDoc(MemoizationTable mt) throws Exception {
    docBuf buf = (docBuf) mt.getOutputBuf(this);

    Tuple docTup = buf.docTup;
    // System.err.printf("%s returning doc %s\n", this, docTup.getOid());

    return docTup;
  }

  @Override
  protected void startScan(MemoizationTable mt) throws Exception {
    // No-op
  }

  @Override
  protected void reallyCheckEndOfInput(MemoizationTable mt) throws Exception {
    // The end of input logic is located in setDone().
  }

}
