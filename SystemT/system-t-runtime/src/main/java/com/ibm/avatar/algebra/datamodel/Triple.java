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
 * An ordered triple
 * 
 * @param <firstT> type of the first element
 * @param <secondT> type of the second element
 * @param <thirdT> type of the third element
 */
public class Triple<firstT, secondT, thirdT>
    implements Comparable<Triple<firstT, secondT, thirdT>> {
  public firstT first;
  public secondT second;
  public thirdT third;

  public Triple(firstT first, secondT second, thirdT third) {
    this.first = first;
    this.second = second;
    this.third = third;
  }

  @Override
  public int hashCode() {
    return ((first.hashCode() * 31) + second.hashCode()) * 31 + third.hashCode();
  }

  @SuppressWarnings({"all"})
  @Override
  public boolean equals(Object obj) {
    if (obj instanceof Triple) {
      Triple p = (Triple) obj;

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

      if (null == third) {
        if (null != p.third) {
          return false;
        }
      } else {
        if (false == third.equals(p.third)) {
          return false;
        }
      }
    }

    return true;
  }

  @Override
  public String toString() {
    return String.format("<%s,%s,%s>", first.toString(), second.toString(), third.toString());
  }

  /**
   * Note that this method will only work if the three input types are themselves comparable.
   */
  @Override
  @SuppressWarnings("unchecked")
  public int compareTo(Triple<firstT, secondT, thirdT> o) {
    int val = ((Comparable<firstT>) first).compareTo(o.first);
    if (0 != val) {
      return val;
    }

    val = ((Comparable<secondT>) second).compareTo(o.second);
    if (0 != val) {
      return val;
    }

    val = ((Comparable<thirdT>) third).compareTo(o.third);
    return val;
  }
}
