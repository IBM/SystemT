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
package com.ibm.avatar.aql;

import java.io.File;

/**
 * Object for encapsulating the location of an AQL parse/compile error. Holds information about file
 * (if present) and location within the file.
 */
public class ErrorLocation {

  private File file;

  // private Token locInFile;

  protected int lineNum = 0;
  protected int colNum = 0;

  public ErrorLocation(File file, Token locInFile) {
    this.file = file;
    if (locInFile != null) {
      this.lineNum = locInFile.beginLine;
      this.colNum = locInFile.beginColumn;
    }
  }

  protected ErrorLocation(File file, int line, int col) {
    this.file = file;
    this.lineNum = line;
    this.colNum = col;
  }

  /**
   * @return the most specific description of this error location; this string could range from an
   *         empty string (no information) to a file and line/column number
   */
  public String getLocStr() {

    if (0 == lineNum && null == file) {
      // SPECIAL CASE: No location information at all.
      return "";
      // END SPECIAL CASE
    }

    // Convert the file and location information into strings separately.
    String locStr = null;
    String fileStr = null;

    if (0 == lineNum) {
      // No token
      locStr = "";
    } else {
      // Have info about location within file.
      locStr = String.format(" line %d, column %d", lineNum, colNum);
    }

    if (null == file) {
      // No file information
      fileStr = "";
    } else {
      // File information present.
      fileStr = String.format("'%s'", file.getPath());
    }

    return String.format("%s%s", fileStr, locStr);

  }
}
