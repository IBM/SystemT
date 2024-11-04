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
 * An ordered pair, for example, the STL pair class.
 *
 * @param <firstT> type of the first element in the pair
 * @param <secondT> type of the second element in the pair
 */
public class Pair<firstT, secondT> implements Comparable<Pair<firstT, secondT>> {
  /**
   * Type of the first element in the pair.
   */
  public firstT first;

  /**
   * Type of the second element in the pair.
   */
  public secondT second;

  /**
   * Constructor to create a Pair instance.
   *
   * @param first type of the first element in the pair
   * @param second type of the second element in the pair
   */
  public Pair(firstT first, secondT second) {
    this.first = first;
    this.second = second;
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return first.hashCode() + second.hashCode();
  }

  /** {@inheritDoc} */
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

      return true;
    }
    // object is not a pair, so obviously they are not equal
    else {
      return false;
    }

  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return String.format("<%s,%s>", first.toString(), second.toString());
  }

  /** {@inheritDoc} */
  @Override
  @SuppressWarnings("unchecked")
  public int compareTo(Pair<firstT, secondT> o) {
    Comparable<firstT> firstC = (Comparable<firstT>) first;
    int val = firstC.compareTo(o.first);
    if (0 != val) {
      return val;
    }

    Comparable<secondT> secondC = (Comparable<secondT>) second;

    /*
     * added null handling because output views with null second elements weren't properly being
     * compared -- eyhung
     */
    if (secondC == null && o.second == null)
      return 0;
    else if (secondC == null && o.second != null)
      return -1;
    else if (secondC != null && o.second == null)
      return 1;
    else
      // both pointers are non-null at this point -- ignore findbugs warning.
      return secondC.compareTo(o.second);
  }
}
