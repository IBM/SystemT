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
package com.ibm.systemt.regex.util;

/**
 * An ordered pair, a la the STL pair class.
 * 
 * 
 * @param <firstT> type of the first element in the pair
 * @param <secondT> type of the second element in the pair
 */
public class Pair<firstT, secondT> implements Comparable<Pair<firstT, secondT>> {
  public firstT first;
  public secondT second;

  public Pair(firstT first, secondT second) {
    this.first = first;
    this.second = second;
  }

  @Override
  public int hashCode() {
    return first.hashCode() + second.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof Pair<?, ?>) {
      Pair<?, ?> p = (Pair<?, ?>) obj;

      // Check for all the ways the two pairs could be different.
      if (null == first) {
        if (null != p.first) {
          return false;
        }
      } else {
        if (false == first.equals(p.first)) {
          return false;
        }
      }

      if (null == second) {
        if (null != p.second) {
          return false;
        }
      } else {
        if (false == second.equals(p.second)) {
          return false;
        }
      }
    }

    return true;
  }

  @Override
  public String toString() {
    return String.format("<%s,%s>", first.toString(), second.toString());
  }

  @SuppressWarnings("all")
  public int compareTo(Pair<firstT, secondT> o) {
    Comparable firstC = (Comparable) first;
    int val = firstC.compareTo(o.first);
    if (0 != val) {
      return val;
    }

    Comparable secondC = (Comparable) second;
    return secondC.compareTo(o.second);
  }
}
