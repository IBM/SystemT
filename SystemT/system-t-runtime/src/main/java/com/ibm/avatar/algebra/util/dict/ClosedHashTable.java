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

import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.logging.Log;

/**
 * Hash table implementation that uses closed hashing with linear probing. This implementation cuts
 * memory usage substantially, mostly by not having linked lists of HashMap.Entry objects.
 * 
 * @param <V>
 */
@SuppressWarnings("unchecked")
public final class ClosedHashTable<V> extends DictHashTable<V> {

  private static final boolean debug = false;

  /**
   * Starting size of the table. The table will be expanded as needed by rehashing.
   */
  private static final int STARTING_SIZE = 16;

  /**
   * At most this fraction of the table will contain entries.
   */
  private static final double MAX_FILL_FACTOR = 0.5;

  /** Array of key strings. */
  private CharSequence[] keys = new CharSequence[STARTING_SIZE];

  /** Values that correspond to the keys. */
  private Object[] values = new Object[STARTING_SIZE];

  /** Number of key, value pairs stored in the table. */
  private int size = 0;

  /** Compute num MOD base, making sure the returned value is positive */
  private static int mod(int num, int base) {
    int ret = num % base;

    // Java's % operator likes to return negative values
    if (ret < 0) {
      ret += base;
    }

    return ret;
  }

  @Override
  public final V get(CharSequence key) {

    if (null == key) {
      throw new RuntimeException("Lookup key should never be null.");
    }

    // int hashCode = key.hashCode();
    int hashCode = hash(key);

    int index = mod(hashCode, keys.length);

    while (null != keys[index]) {

      CharSequence tabKey = keys[index];

      if (debug) {
        Log.debug("ClosedHashTable.get(%s): Checking index %d (%s)", key, index, tabKey);
        // (new Throwable()).printStackTrace(System.err);
      }

      if (key.equals(tabKey)) {
        // Found the key we're looking for.
        return (V) values[index];
      } else {
        // Found a different key; do linear probing, with wraparound.
        index = mod(index + 1, keys.length);
      }
    }

    // If we get here, we probed until we found an empty space, without
    // finding the key.
    return null;
  }

  @Override
  public Iterator<CharSequence> keyItr() {

    // Create an iterator inline.
    Iterator<CharSequence> ret = new Iterator<CharSequence>() {

      int index = firstKeyIndex();

      private int firstKeyIndex() {
        int ret = 0;
        while (ret < keys.length && null == keys[ret]) {
          ret++;
        }
        return ret;
      }

      @Override
      public boolean hasNext() {
        return (index < keys.length);
      }

      @Override
      public CharSequence next() {
        CharSequence ret = keys[index];

        // Advance the iterator to the next position.
        index++;
        while (index < keys.length && null == keys[index]) {
          index++;
        }
        return ret;
      }

      @Override
      public void remove() {
        index = keys.length;
      }
    };

    return ret;
  }

  @Override
  protected void putImpl(CharSequence key, V value) {
    // Expand the table as needed.
    double fillFactor = (double) size / (double) keys.length;
    if (fillFactor > MAX_FILL_FACTOR) {
      rehash();
    }

    reallyPut(keys, values, key, value);
    size++;
  }

  /**
   * Internal implementation of hash insertion. Does NOT resize the tables.
   * 
   * @param keysArr pointer to {@link #keys}, or the future replacement of it
   * @param valuesArr pointer to {@link #values}, or the future replacement of it
   * @param key new key to insert
   * @param value what to insert at the indicated key
   */
  private static final void reallyPut(CharSequence[] keysArr, Object[] valuesArr, CharSequence key,
      Object value) {

    // First, determine where to put the key; it either replaces an existing
    // key or goes in the first empty spot.
    // int hashCode = key.hashCode();
    int hashCode = hash(key);

    int index = mod(hashCode, keysArr.length);

    while (null != keysArr[index] && false == key.equals(keysArr[index])) {
      index = mod(index + 1, keysArr.length);
    }

    // Now we can do the insertion.
    keysArr[index] = key;
    valuesArr[index] = value;
  }

  /**
   * Double the size of the hash table; called whenever the fill factor exceeds
   * {@link #MAX_FILL_FACTOR}.
   */
  private void rehash() {

    // Create a new table of double the current size.
    CharSequence[] newKeys = new CharSequence[keys.length * 2];
    Object[] newValues = new Object[values.length * 2];

    // Insert all keys and values into the new table.
    // Look up everything for now, to ensure correctness; later this should
    // be replaced with code that directly walks through the arrays.
    for (Iterator<CharSequence> keyItr = keyItr(); keyItr.hasNext();) {
      CharSequence key = keyItr.next();
      V value = get(key);

      reallyPut(newKeys, newValues, key, value);
    }

    // Replace the previous table.
    keys = newKeys;
    values = newValues;
  }

  /**
   * Hash function used by this class.
   */
  private static int hash(CharSequence arg) {
    return StringUtils.strHash(arg);
    // return StringUtils.betterStrHash(arg);
  }

  @Override
  public void compact() {
    final boolean DUMP_HIST = false;
    final int MIN_DUMP_SZ = 1000;

    // Dump some information about the histogram, if requested.
    if (DUMP_HIST && size > MIN_DUMP_SZ) {
      // Collect a histogram of the number of items that would have to be
      // searched to determine that something is *not* in the dictionary
      int[] runLenHist = new int[100];

      int runLen = 0;
      boolean inRun = false;
      for (int i = 1; i < keys.length; i++) {
        if (null != keys[i] && false == inRun) {
          // Starting a run of non-null entries
          runLen = 1;
          inRun = true;
        } else if (null != keys[i] && inRun) {
          // Continuing a run of non-null entries
          runLen++;
        } else if (null == keys[i] && inRun) {
          // End of a run
          runLenHist[Math.min(runLenHist.length - 1, runLen)]++;
          inRun = false;
          runLen = 0;
        }
      }

      Log.debug("Run length histogram for hash table %s:", this);
      Log.debug("%10s  %s", "Run Length", "Count");
      for (int i = 0; i < runLenHist.length; i++) {
        Log.debug("%10d  %d", i, runLenHist[i]);
      }

    }
  }
}
