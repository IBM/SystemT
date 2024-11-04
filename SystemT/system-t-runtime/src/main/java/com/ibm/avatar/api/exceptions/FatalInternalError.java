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
package com.ibm.avatar.api.exceptions;

/**
 * Class to encapsulate fatal errors that trigger failed assertions inside SystemT internal code.
 * When one of these exceptions are thrown, the current high-level operation (for example, compiling
 * an AQL file or running an operator graph) needs to be terminated, because internal state may be
 * corrupted.
 * 
 */
public class FatalInternalError extends RuntimeException {
  /**
   * Version ID for serialization
   */
  private static final long serialVersionUID = 1L;

  public FatalInternalError(String fmt, Object... args) {
    super(makeMsg(fmt, args));
  }

  public FatalInternalError(Throwable cause, String fmt, Object... args) {
    super(makeMsg(fmt, args), cause);
  }

  public FatalInternalError(Throwable cause) {
    this(cause, makeMsg(cause.toString()));
  }

  /**
   * Wrap the provided message in a longer message that says to contact IBM support personnel.
   * 
   * @param fmt format string for "interior" message
   * @param args arguments of format string
   */
  private static String makeMsg(String fmt, Object... args) {
    String interiorMsg = String.format(fmt, args);

    // Make sure message ends with a period.
    if (false == interiorMsg.endsWith(".")) {
      interiorMsg = interiorMsg + ".";
    }

    return "An internal error occurred.  " + interiorMsg
        + "  Gather the full stack trace associated with this error, and contact IBM Software Support.";
  }

}
