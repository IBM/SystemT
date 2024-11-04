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
package com.ibm.avatar.aql.compiler;

/**
 * Simple container for representing a performance warning generated during AQL compilation.
 * 
 */
public class CompilerWarning {

  /**
   * Enum of predefined warning types
   */
  public enum WarningType {
    /**
     * The module comment location is a directory. A text file is expected
     */
    MODULE_COMMENT_LOCATION_IS_DIRECTORY,

    /**
     * Exception occurred while reading module comment file
     */
    MODULE_COMMENT_READ_FAILED,

    /**
     * RSR is not supported for this particular regex
     */
    RSR_FOR_THIS_REGEX_NOT_SUPPORTED,

    /**
     * Deprecated flag used
     */
    DEPRECATED_FLAG_USED

  }

  /** String that is displayed in front of the warning message */
  public static String WARNING = "Warning: ";

  /**
   * String that is displayed in front of the continuation lines in multi-line warning message, so
   * that the text on each line is lined up with the "Warning:" or "Info:" message at the beginning
   * of the first line.
   */
  static String spaces = "";

  static {
    for (int i = 0; i < WARNING.length(); i++) {
      spaces += " ";
    }
  }

  /** Message of the warning. */
  private final String msg;

  /** AQL file name containing the error -- may be null */
  protected final String fileName;

  /** The line number of the first character of the Token that prompted this warning. */
  public final int beginLine;
  /** The column number of the first character of the Token that prompted this warning. */
  public final int beginColumn;
  /** The line number of the last character of the Token that prompted this warning. */
  public final int endLine;
  /** The column number of the last character of the Token that prompted this warning. */
  public final int endColumn;

  private final WarningType type;

  public CompilerWarning(WarningType type, String message, String fileName) {
    this(type, message, fileName, 0, 0, 0, 0);
  }

  public CompilerWarning(WarningType type, String message, String fileName, int beginLine,
      int beginColumn, int endLine, int endColumn) {
    this.msg = message;
    this.type = type;
    this.fileName = fileName;
    this.beginLine = beginLine;
    this.beginColumn = beginColumn;
    this.endLine = endLine;
    this.endColumn = endColumn;
  }

  @Override
  public String toString() {
    String formattedMsg;
    // Strip off newlines.
    while (msg.length() > 1 && '\n' == msg.charAt(msg.length() - 1)) {
      formattedMsg = msg.substring(0, msg.length() - 1);
    }

    formattedMsg = WARNING + msg.replaceAll("\n", "\n" + spaces);

    return formattedMsg;
  }

  public WarningType getType() {
    return this.type;
  }

  public String getFileName() {
    return this.fileName;
  }

  /**
   * The line number of the beginning of the token where the warning is generated
   * 
   * @return the line number of the beginning of the token where the warning is generated
   */
  public int getBeginLine() {
    return beginLine;
  }

  /**
   * The column number of the beginning of the token where the warning is generated
   * 
   * @return the column number of the beginning of the token where the warning is generated
   */
  public int getBeginColumn() {
    return beginColumn;
  }

  /**
   * The line number of the end of the token where the warning is generated
   * 
   * @return the line number of the end of the token where the warning is generated
   */
  public int getEndLine() {
    return endLine;
  }

  /**
   * The column number of the end of the token where the warning is generated
   * 
   * @return the column number of the end of the token where the warning is generated
   */
  public int getEndColumn() {
    return endColumn;
  }

}
