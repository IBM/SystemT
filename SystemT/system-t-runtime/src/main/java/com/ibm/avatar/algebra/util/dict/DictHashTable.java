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
package com.ibm.avatar.algebra.util.dict;

import java.util.Iterator;

/**
 * Superclass for implementations of the dictionary hash tables used in SystemT. We keep multiple
 * "competing" implementations around for experimentation and testing, and this class provides a
 * common interface and a place to put shared functionality. Keys of this class are always
 * CharSequences. Most implementations assume that the inputs implement the hashCode() method,
 * though some may hash directly on the characters. Values are parametrized by the template
 * parameter.
 * 
 * @param <V> type of value stored in the hashtable
 */
public abstract class DictHashTable<V> {

  /**
   * We implement this in the superclasses so that subclasses don't need to bother.
   * 
   * @param key string to check for
   * @return true if the string is in the table with a non-NULL value
   */
  public boolean containsKey(CharSequence key) {
    V value = this.get(key);

    return (null != value);
  }

  /**
   * Subclasses must provide an implementation of this method.
   * 
   * @param key string to check for in the hashtable
   * @return the value at the indicated position, or NULL of no value is stored
   */
  public abstract V get(CharSequence key);

  /**
   * Subclasses must provide an implementation of this method, which stores the indicated value
   * under the indicated key, replacing anything that was there previously.
   * 
   * @param key hash key
   * @param value new value to go at the indicated key position
   */
  protected abstract void putImpl(CharSequence key, V value);

  /**
   * Front-end for hash insertion. Prevents internal implementations from seeing null values.
   */
  public void put(CharSequence key, V value) {
    if (null == value) {
      throw new RuntimeException("DictHashtable cannot hold null values");
    }

    putImpl(key, value);
  }

  /**
   * Subclasses must provide an implementation of this method.
   * 
   * @return an iterator over the keys stored in the table
   */
  public abstract Iterator<CharSequence> keyItr();

  /**
   * Subclasses may override this method to shrink the size of the hash table.
   */
  public void compact() {
    // Default implementation does nothing.
  }

}
