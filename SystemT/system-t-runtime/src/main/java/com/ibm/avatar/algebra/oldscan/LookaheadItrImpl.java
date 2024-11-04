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

import java.util.Iterator;

/**
 * An iterator with one-element lookahead
 */
class LookaheadItrImpl<T> implements LookaheadItr<T> {

  /**
   * The original iterator; this object is always one entry past the position of the LookaheadItr.
   */
  private final Iterator<T> itr;

  /**
   * The last element we got from this.itr, or null if there are no more elements to read.
   */
  private T lookahead;

  public LookaheadItrImpl(Iterator<T> origItr) {
    itr = origItr;

    advance();
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.avatar.algebra.scan.LookaheadItr#hasNext()
   */
  @Override
  public boolean hasNext() {
    return (null != lookahead);
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.avatar.algebra.scan.LookaheadItr#next()
   */
  @Override
  public T next() {

    T ret = lookahead;

    advance();

    return ret;
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.avatar.algebra.scan.LookaheadItr#peek()
   */
  @Override
  public T peek() {
    return lookahead;
  }

  /**
   * Move to the next element.
   */
  private void advance() {
    if (itr.hasNext()) {
      lookahead = itr.next();
    } else {
      lookahead = null;
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.avatar.algebra.scan.LookaheadItr#remove()
   */
  @Override
  public void remove() {
    itr.remove();
    lookahead = null;
  }

}
