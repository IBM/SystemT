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
package com.ibm.avatar.algebra.datamodel;

/**
 * An iterator over a {@link com.ibm.avatar.algebra.datamodel.TupleList}. All objects of this type
 * are owned by a TupleList.
 */
public class TLIter {
  int pos = 0;

  boolean inUse = false;

  private TupleList list;

  /**
   * Constructor for the exclusive use of the TupleList class.
   */
  protected TLIter(TupleList list) {
    this.list = list;
  }

  /**
   * Returns true if this iterator can return any additional values.
   *
   * @return <code>true</code> if this iterator can return any additional values
   */
  public boolean hasNext() {
    return (pos < list.ntups);
  }

  /**
   * Returns the next tuple in the tuple list.
   *
   * @return next tuple in the {@link com.ibm.avatar.algebra.datamodel.TupleList}
   */
  public Tuple next() {
    if (pos == list.ntups - 1) {
      // Automatically unpin the iterator when we reach the end of the
      // list.
      inUse = false;
    }

    if (pos >= list.ntups) {
      throw new RuntimeException("Ran off end of TupleList");
    }

    return list.tuples[pos++];
  }

  /**
   * The caller must call this method to release the iterator.
   */
  public void done() {
    inUse = false;
  }

  /**
   * Prepare this iterator to be used by another caller.
   *
   * @throws RuntimeException if there are two simultaneous users for iterator
   */
  protected void reset() {
    if (inUse) {
      throw new RuntimeException("Two simultaneous users for iterator");
    }

    pos = 0;

    if (pos >= list.ntups) {
      inUse = false;
    } else {
      inUse = true;
    }
  }
}
