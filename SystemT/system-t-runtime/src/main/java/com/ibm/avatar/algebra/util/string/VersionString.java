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
package com.ibm.avatar.algebra.util.string;

public class VersionString implements Comparable<VersionString> {

  private final String version;

  public final String get() {
    return this.version;
  }

  /**
   * Handle version number string comparison. Version number strings are a string of numbers
   * delimited by periods (.), e.g., 3.0.1
   * 
   * @param version The version number string
   */
  public VersionString(String version) {
    if (null == version)
      throw new IllegalArgumentException(
          "Invalid version format for version: null. Ensure the version is not null.");

    if (version.matches("[0-9]+(\\.[0-9]+)*") == false)
      throw new IllegalArgumentException(
          "Invalid version format. The version should be either major such as 1, 30, or major with minor such as 1.2, 30.10.");

    this.version = version;
  }

  @Override
  /**
   * Implements comparison of version strings. "1.1" < "1.1.1" "2.0" > "1.9.9" "1.0" = "1" "1" >
   * null "2.06" != "2.060"
   */
  public int compareTo(VersionString that) {
    if ((that == null) && (this.get() == null))
      return 0;
    if (that.get() == null) {
      int returnVal = this.get() == null ? 0 : 1;
      return returnVal;
    }
    if (this.get() == null)
      return -1;
    String[] thisParts = this.get().split("\\.");
    String[] thatParts = that.get().split("\\.");
    int length = Math.max(thisParts.length, thatParts.length);
    for (int i = 0; i < length; i++) {
      int thisPart = i < thisParts.length ? Integer.parseInt(thisParts[i]) : 0;
      int thatPart = i < thatParts.length ? Integer.parseInt(thatParts[i]) : 0;
      if (thisPart < thatPart)
        return -1;
      if (thisPart > thatPart)
        return 1;
    }
    return 0;
  }

  @Override
  public boolean equals(Object that) {
    if (this == that)
      return true;
    if (that == null)
      return false;
    if (this.getClass() != that.getClass())
      return false;
    return this.compareTo((VersionString) that) == 0;
  }

}
