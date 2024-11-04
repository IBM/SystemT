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
package com.ibm.avatar.aog;

public class ColumnRef implements Comparable<ColumnRef> {

  private String colName;

  public ColumnRef(String colName) {
    this.colName = colName;
  }

  public String getColName() {
    return colName;
  }

  @Override
  public String toString() {
    // Add double quotes back on.
    return String.format("\"%s\"", colName);
  }

  @Override
  public int hashCode() {
    return colName.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (false == o instanceof ColumnRef) {
      return false;
    }
    ColumnRef other = (ColumnRef) o;
    return (colName.equals(other.colName));
  }

  @Override
  public int compareTo(ColumnRef o) {
    return colName.compareTo(o.colName);
  }

}
