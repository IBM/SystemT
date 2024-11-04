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
package com.ibm.avatar.aql.planner;

/**
 * Class for encapsulating a dictionary invocation; stores dictionary name and match mode (case
 * sensitive or not)
 */
public class DictInvocation implements Comparable<DictInvocation> {
  public DictInvocation(String dictName, String matchMode) {
    this.dictName = dictName;
    this.matchMode = matchMode;
  }

  public String dictName;

  public String matchMode;

  @Override
  public int hashCode() {
    return dictName.hashCode() + matchMode.hashCode();
  }

  @Override
  public int compareTo(DictInvocation o) {
    if (this == o) {
      return 0;
    }

    int val = dictName.compareTo(o.dictName);

    if (0 == val) {
      // Same dictionary; check matching mode.
      val = matchMode.compareTo(o.matchMode);
    }

    return val;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof DictInvocation)) {
      return false;
    }
    DictInvocation o = (DictInvocation) obj;

    return (0 == compareTo(o));
  }

  @Override
  public String toString() {
    return String.format("%s with match mode %s", dictName, matchMode);
  }
}
