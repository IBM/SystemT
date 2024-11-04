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
package com.ibm.avatar.algebra.scan;

import java.util.Iterator;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.Tuple;

/**
 * Document scan operator that takes a Java Iterator as an argument. At runtime, the operator pulls
 * Strings or Tuples from the Iterator, packages them as document tuples, and returns them to the
 * parent operator graph.
 * 
 */
public class DocScanStub extends DocScanInternal {

  protected Iterator<String> itr;

  /**
   * Creates a new DocScanStub operator with a prearranged set of documents to return.
   * 
   * @param docitr iterator over the tuples that the stub will spit out
   */
  public DocScanStub(Iterator<String> docitr) {
    super();

    itr = docitr;
  }

  // @Override
  // public boolean endOfInput() {
  // return (!itr.hasNext());
  // }

  @Override
  protected Tuple getNextDoc(MemoizationTable mt) throws Exception {
    Tuple ret = createOutputTup();

    docTextAcc.setVal(ret, itr.next());

    return ret;
  }

  @Override
  protected void startScan(MemoizationTable mt) throws Exception {
    // No-op
  }

  @Override
  protected void reallyCheckEndOfInput(MemoizationTable mt) throws Exception {
    if (!itr.hasNext()) {
      mt.setEndOfInput();
    }
  }

}
