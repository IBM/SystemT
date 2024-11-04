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
package com.ibm.avatar.algebra.scan;

import java.util.Iterator;

/**
 * Iterator for turning push into pull. To use, you must call {@link #setNext(Object)} before each
 * call to {@link #next()}.
 * 
 * @param <T>
 */
class PushItr<T> implements Iterator<T> {

  /** Set this to true after pushing the last element in. */
  private boolean done = false;

  public void setDone() {
    this.done = true;
  }

  /** The element that was just pushed to this iterator. */
  private T next = null;

  public void setNext(T next) {
    if (done) {
      throw new RuntimeException("Called setNext() after iterator was closed");
    }
    this.next = next;
  }

  /*
   * ITERATOR INTERFACE METHODS
   */
  @Override
  public boolean hasNext() {
    return !done;
  }

  @Override
  public T next() {
    return next;
  }

  @Override
  public void remove() {
    setDone();
  }

}
