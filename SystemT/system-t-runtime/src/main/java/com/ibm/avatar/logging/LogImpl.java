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

/** Callback for printing log messages to the appropriate place. */
public abstract class LogImpl {

  /**
   * Override this method to send the indicated log message to the appropriate place
   * 
   * @param type internal type of log message; may be translated by the subclass into, for example,
   *        a UIMA-specific log message type.
   * @param msg log message; should NOT contain a terminating newline
   */
  public abstract void log(MsgType type, String msg);

}
