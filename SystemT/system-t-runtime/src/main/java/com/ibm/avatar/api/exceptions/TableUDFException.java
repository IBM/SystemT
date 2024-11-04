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
 * Exception thrown by user-defined table functions during initialization or schema validation
 * callbacks.
 */
public class TableUDFException extends TextAnalyticsException {
  /**
   * Version ID for serialization
   */
  private static final long serialVersionUID = 1L;

  /**
   * Constructor that builds an error message from a format string.
   * 
   * @param fmt format string, as passed to {@link java.lang.String#format(String, Object...)}
   * @param args arguments of format string
   */
  public TableUDFException(String fmt, Object... args) {
    super(fmt, args);
  }

  /**
   * Constructor for when another exception marks the root cause of the error.
   * 
   * @param cause root cause of the problem
   * @param fmt format string, as passed to {@link java.lang.String#format(String, Object...)}
   * @param args arguments of format string
   */
  public TableUDFException(Throwable cause, String fmt, Object... args) {
    super(cause, fmt, args);
  }
}
