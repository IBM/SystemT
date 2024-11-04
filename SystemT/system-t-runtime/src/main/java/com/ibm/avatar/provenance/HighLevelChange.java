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
package com.ibm.avatar.provenance;

public class HighLevelChange implements Comparable<HighLevelChange> {
  private final String viewName;
  private int id;
  private static int counter = 0;

  public String getViewName() {
    return viewName;
  }

  public String getChangeType() {
    return changeType;
  }

  public String getChangePurpose() {
    return changePurpose;
  }

  private final String changeType;
  private final String changePurpose;

  HighLevelChange(String viewName, String changeType, String changePurpose) {
    this.viewName = viewName;
    this.changeType = changeType;
    this.changePurpose = changePurpose;
    id = counter;
    counter++;
  }

  HighLevelChange(HighLevelChange c) {
    this.viewName = c.viewName;
    this.changeType = c.changeType;
    this.changePurpose = c.changePurpose;
  }

  @Override
  public String toString() {
    return "Change view: " + viewName + "; Change type: " + changeType + "; ChangePurpose: "
        + changePurpose;
  }

  @Override
  public boolean equals(Object c) {
    boolean status = false;

    if (c == null)
      return false;
    if (viewName.equals(((HighLevelChange) c).viewName)
        && changeType.equals(((HighLevelChange) c).changeType)
        && changePurpose.equals(((HighLevelChange) c).changePurpose))
      status = true;
    return status;
  }

  public int getId() {
    return id;
  }

  /**
   * Compare changes lexicographically (ignores id value)
   */
  @Override
  public int compareTo(HighLevelChange c) {
    if (viewName.compareTo(c.viewName) != 0)
      return viewName.compareTo(c.viewName);
    else if (changeType.compareTo(c.changeType) != 0)
      return changeType.compareTo(c.changeType);
    else if (changePurpose.compareTo(c.changePurpose) != 0)
      return changePurpose.compareTo(c.changePurpose);
    else
      return 0;
  }

  @Override
  public int hashCode() {
    return (viewName + changeType + changePurpose).hashCode();
  }
}
