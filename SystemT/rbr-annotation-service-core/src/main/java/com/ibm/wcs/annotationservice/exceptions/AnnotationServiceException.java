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
package com.ibm.wcs.annotationservice.exceptions;

/**
 * A generic exception class to signal errors while invoking the Annotation Service.
 * 
 */
public class AnnotationServiceException extends Exception {

  /**
   * Version ID for serialization
   */
  private static final long serialVersionUID = 1L;

  /**
   * Constructs an instance of this class with the given message format and arguments.
   * 
   * @param fmt format string, as in {@link java.lang.String#format(String, Object...)}
   * @param args arguments for format string
   */
  public AnnotationServiceException(String fmt, Object... args) {
    super(String.format(fmt, args));
  }

  /**
   * Constructs an instance of this class with the given message format, arguments, and root cause
   * behind this exception.
   * 
   * @param cause Root cause of this exception
   * @param fmt format string, as in {@link java.lang.String#format(String, Object...)}
   * @param args arguments for format string
   */
  public AnnotationServiceException(Throwable cause, String fmt, Object... args) {
    super(String.format(fmt, args), cause);
  }

  /**
   * Construct an instance of this class with the given root cause behind this exception.
   * 
   * @param cause Root cause of this exception
   */
  public AnnotationServiceException(Throwable cause) {
    this(cause, cause.toString());
  }
}
