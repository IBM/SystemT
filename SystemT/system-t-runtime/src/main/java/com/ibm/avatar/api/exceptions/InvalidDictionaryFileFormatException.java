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
 * Signals error occurred while parsing dictionary files, which does not adheres to the format
 * described in the Information Center.
 * 
 */
public class InvalidDictionaryFileFormatException extends TextAnalyticsException {
  private static final long serialVersionUID = 793259344562217494L;

  /**
   * Constructs an instance of this class with the given message format and arguments.
   * 
   * @param fmt format string, as in {@link String#format(String, Object...)}
   * @param args arguments for format string
   */
  public InvalidDictionaryFileFormatException(String fmt, Object... args) {
    super(String.format(fmt, args));
  }

  /**
   * Constructs an instance of this class with the given message format, arguments, and root cause
   * behind this exception.
   * 
   * @param cause Root cause of this exception
   * @param fmt format string, as in {@link String#format(String, Object...)}
   * @param args arguments for format string
   */
  public InvalidDictionaryFileFormatException(Throwable cause, String fmt, Object... args) {
    super(cause, fmt, args);
  }

}
