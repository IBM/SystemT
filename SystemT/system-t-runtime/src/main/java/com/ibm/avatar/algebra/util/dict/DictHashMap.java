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

import java.util.HashMap;
import java.util.Iterator;

/**
 * Simple HashMap-based implementation of the {@link DictHashTable} interface.
 * 
 * @param <V> type of value stored in the hashtable
 */
public final class DictHashMap<V> extends DictHashTable<V> {

  private HashMap<CharSequence, V> table = new HashMap<CharSequence, V>();

  @Override
  public V get(CharSequence key) {
    return table.get(key);
  }

  @Override
  public Iterator<CharSequence> keyItr() {
    return table.keySet().iterator();
  }

  @Override
  protected void putImpl(CharSequence key, V value) {
    table.put(key, value);
  }

}
