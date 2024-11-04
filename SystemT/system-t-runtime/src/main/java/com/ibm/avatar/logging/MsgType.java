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
package com.ibm.avatar.logging;

/**
 * Base class for a type of log message; contains actual classes as inner classes.
 */
public class MsgType {

  /**
   * List of all the log message types that UIMA knows about; we use this list instead of
   * org.apache.uima.util.Level to avoid requiring UIMA jars when they are not necessary.
   */
  protected enum UimaLogLevel {
    OFF, SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST, ALL
  }

  /**
   * Fully-qualified Java-style log output name, e.g. "com.ibm.avatar.foo.bar"
   */
  private String javaName;

  /**
   * Equivalent UIMA message type
   */
  private UimaLogLevel uimaLevel;

  /** Human-readable name for the log message type. */
  private String humanReadableName;

  public String getJavaName() {
    return javaName;
  }

  public String getHumanReadableName() {
    return humanReadableName;
  }

  public UimaLogLevel getUimaLevel() {
    return uimaLevel;
  }

  /** Constructor for use by inner classes. */
  protected MsgType(String javaName, UimaLogLevel uimaLevel, String humanReadableName) {
    this.javaName = javaName;
    this.uimaLevel = uimaLevel;
    this.humanReadableName = humanReadableName;
  }

  /*
   * LOG MESSAGE TYPES GO HERE
   */

  /** Compile warning while instantiating an operator graph. */
  public static MsgType AOGCompileWarning =
      new MsgType("com.ibm.avatar.aog.compile.warning", UimaLogLevel.WARNING, "Warning");

  /** Compile warning while compile AQL into a plan. */
  public static MsgType AQLCompileWarning =
      new MsgType("com.ibm.avatar.aql.compile.warning", UimaLogLevel.WARNING, "Warning");

  /**
   * Warning from the compiler about a potential performance issue that does not affect correctness.
   */
  public static MsgType AQLPerfWarning = new MsgType(
      "com.ibm.avatar.aql.compile.warning.performance", UimaLogLevel.WARNING, "Warning");

  /**
   * Warning from the SystemT runtime about a potential correctness problem.
   */
  public static MsgType AQLRuntimeWarning =
      new MsgType("com.ibm.avatar.aql.runtime.warning", UimaLogLevel.WARNING, "Warning");

  /**
   * Informational message
   */
  public static MsgType Info = new MsgType("com.ibm.avatar.info", UimaLogLevel.INFO, "Info");

  /**
   * Debugging message.
   */
  public static MsgType Debug = new MsgType("com.ibm.avatar.debug", UimaLogLevel.INFO, "Debug");

  /**
   * List of all available message types; Make sure that this list is kept in sync with reality!
   */
  public static MsgType[] ALL_MSG_TYPES =
      {AOGCompileWarning, AQLCompileWarning, AQLPerfWarning, AQLRuntimeWarning, Info,

          Debug,};

}
