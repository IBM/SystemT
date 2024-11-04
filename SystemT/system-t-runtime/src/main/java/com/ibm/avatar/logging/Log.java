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

import java.util.HashSet;

/** Single global location for controlling logging through AQL/AOG/operators. */
public class Log {

  // Message types that enabled by default
  private static final MsgType[] ENABLED_BY_DEFAULT =
      {MsgType.AQLCompileWarning, MsgType.AOGCompileWarning, MsgType.Info, MsgType.Debug,};

  private static HashSet<MsgType> enabledMsgTypes = new HashSet<MsgType>();
  static {
    restoreDefaultMsgTypes();
  }

  private static LogImpl logger = new StdErrLog();

  /**
   * @param type a type of log message
   * @param enabled true if you want subsequent log messages of this type to be sent to the Logger
   */
  public static void setMsgTypeEnabled(MsgType type, boolean enabled) {
    if (enabled) {
      enabledMsgTypes.add(type);
    } else {
      enabledMsgTypes.remove(type);
    }
  }

  /**
   * Turns on all types of logging, <b>even those that are disabled by default</b>.
   * {@see #restoreDefaultMsgTypes()}
   */
  public static void enableAllMsgTypes() {
    for (MsgType type : MsgType.ALL_MSG_TYPES) {
      setMsgTypeEnabled(type, true);
    }
  }

  /**
   * Disable all logging for the current process.
   */
  public static void disableAllMsgTypes() {
    for (MsgType type : MsgType.ALL_MSG_TYPES) {
      setMsgTypeEnabled(type, false);
    }
  }

  /**
   * Enable the global default set of message types.
   */
  public static void restoreDefaultMsgTypes() {
    disableAllMsgTypes();
    for (MsgType type : ENABLED_BY_DEFAULT) {
      setMsgTypeEnabled(type, true);
    }
  }

  /**
   * @param type a type of log message
   * @return true if messages of this type are being printed to the Logger
   */
  public static boolean getMsgTypeEnabled(MsgType type) {
    if (enabledMsgTypes.contains(type)) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * @param l logging implementation; class that sends log messages to the appropriate location
   */
  public static void setLogger(LogImpl l) {
    logger = l;
  }

  /**
   * @return
   */
  public static LogImpl getLogger() {
    return logger;
  }

  /**
   * Print a log message.
   * 
   * @param type type of message being logged
   * @param template template that
   * @param args
   */
  public static void log(MsgType type, String template, Object... args) {
    if (enabledMsgTypes.contains(type)) {
      String msg = String.format(template, args);

      // Strip off newlines.
      while (msg.length() > 1 && '\n' == msg.charAt(msg.length() - 1)) {
        msg = msg.substring(0, msg.length() - 1);
      }

      // If the message is multiple lines long, format it so that the
      // start of each line is lined up with the "Warning:" or "Info:"
      // message at the beginning of the first line.
      String spaces = "";
      for (int i = 0; i < type.getHumanReadableName().length() + 2; i++) {
        spaces += " ";
      }
      msg = msg.replaceAll("\n", "\n" + spaces);

      // String
      type.getHumanReadableName();

      logger.log(type, msg);
    } else {
      // System.err.printf("Msg type %s disabled \n", type.getJavaName());
    }
  }

  /**
   * Shorthand for log(MsgType.Debug, template, ...)
   */
  public static void debug(String template, Object... args) {
    log(MsgType.Debug, template, args);
  }

  /**
   * Shorthand for log(MsgType.AQLInfo, template, ...)
   */
  public static void info(String template, Object... args) {
    log(MsgType.Info, template, args);
  }

}
