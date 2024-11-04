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
package com.ibm.biginsights.textanalytics.util;

import java.util.List;

/**
 * Utility class to simplify comparison of two objects.
 * 
 */
public class ObjectComparator {
  /**
   * Verifies that the two objects passed as parameters are equal or not. Applies the following
   * rules:
   * <ul>
   * <li>If both are null, they are equal</li>
   * <li>If only one of them is null, then they are not equal</li>
   * <li>If they are instances of two different classes, they are not equal</li>
   * <li>If they are lists, then each entry in the list should be equal</li>
   * <li>Verify if object1.equals(object2) returns true</li> *
   * </ul>
   * 
   * @param obj1 the object to compare with the other object
   * @param obj2 the object to compare with the other object
   * @return true, if <code>obj1</code> and <code>obj2</code> are equal.
   */
  @SuppressWarnings("unchecked")
  public static boolean equals(Object obj1, Object obj2) {
    // return true, if both references are pointing to same object (or) both are null
    if (obj1 == obj2)
      return true;

    // not equal if one of them is null and the other is not
    if (obj1 == null && obj2 != null)
      return false;

    // not equal if one of them is null and the other is not
    if (obj1 != null && obj2 == null)
      return false;

    // not equal if they are instances of two different classes
    if (false == obj1.getClass().equals(obj2.getClass()))
      return false;

    // If the objects are instances of List, then verify that each entry is equal
    if (obj1 instanceof List) {
      List<Object> list1 = (List<Object>) obj1;
      List<Object> list2 = (List<Object>) obj2;

      // ensure that sizes are same
      if (list1.size() != list2.size())
        return false;

      for (int i = 0; i < list1.size(); ++i) {
        Object item1 = list1.get(i);
        Object item2 = list2.get(i);
        if (false == ObjectComparator.equals(item1, item2)) {
          return false;
        }

      }

      // return true, if all items in the list are equal
      return true;
    } else {// for all other types, call equals() method on the object
      return obj1.equals(obj2);
    }
  }
}
