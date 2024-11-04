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
 * A generic exception class to signal errors while invoking Text Analytics API.
 * 
 */
public class TextAnalyticsException extends Exception {

  /**
   * Version ID for serialization
   */
  private static final long serialVersionUID = 1L;

  /**
   * Type of exception indicating where the exception occurred, along with an associated text string
   * for printing.
   */
  public enum ExceptionType {
    COMPILE_ERROR, RUNTIME_ERROR;

    private ExceptionType() {}

  }

  /**
   * <p>
   * Constructor for TextAnalyticsException.
   * </p>
   */
  public TextAnalyticsException() {
    super();
  }

  /**
   * Constructs an instance of this class with the given message format and arguments.
   * 
   * @param fmt format string, as in {@link java.lang.String#format(String, Object...)}
   * @param args arguments for format string
   */
  public TextAnalyticsException(String fmt, Object... args) {
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
  public TextAnalyticsException(Throwable cause, String fmt, Object... args) {
    super(String.format(fmt, args), cause);
  }

  /**
   * Construct an instance of this class with the given root cause behind this exception.
   * 
   * @param cause Root cause of this exception
   */
  public TextAnalyticsException(Throwable cause) {
    this(cause, cause.toString());
  }

  /**
   * This method converts the specified unchecked/error (such as NPE) or checked (which are not
   * subclass of TextAnalyticsException) exception by wrapping them into
   * {@link com.ibm.avatar.api.exceptions.TextAnalyticsException} or its subclasses. Unchecked
   * exception/Error occurred during compilation are returned as
   * {@link com.ibm.avatar.api.exceptions.FatalCompileError}. Unchecked exception/Error occurred
   * during creation of {@link com.ibm.avatar.api.OperatorGraph}, or while invoking any of the
   * {@link com.ibm.avatar.api.OperatorGraph} method is returned as FatalRuntimeError. <br>
   * 
   * @param cause Root cause of this exception
   * @param errorType When this exception occurred (compile time, runtime, etc.)
   * @return Rewrapped {@link com.ibm.avatar.api.exceptions.TextAnalyticsException} exception object
   */
  public static TextAnalyticsException convertToTextAnalyticsException(Throwable cause,
      ExceptionType errorType) {
    TextAnalyticsException convertedTAException = null;

    // Wrap Error,Unchecked and Checked (specifically, which does not extend TextAnalyticsException)
    // exception into
    // TextAnalyticsException
    if (cause instanceof RuntimeException || cause instanceof Error) {
      try {
        if (errorType == ExceptionType.COMPILE_ERROR)
          convertedTAException = new FatalCompileError(cause);
        else if (errorType == ExceptionType.RUNTIME_ERROR)
          convertedTAException = new FatalRuntimeError(cause);
      } catch (Throwable moreError) {
        if (cause instanceof RuntimeException)
          throw (RuntimeException) cause;
        else if (cause instanceof Error) {
          throw (Error) cause;
        }

      }
    } else {
      if (cause instanceof TextAnalyticsException)
        convertedTAException = (TextAnalyticsException) cause;
      else
        convertedTAException = new TextAnalyticsException(cause);
    }
    return convertedTAException;
  }

  /**
   * Utility method to run through a series of chained exceptions and find a non-null error message
   * string.
   * 
   * @param t an exception, which may have a chain of other exceptions on its "cause" pointer
   * @return the closest non-null error message to the root of the chain; if no error message is
   *         found, uses the exception classes in the chain as a message
   */
  public static String findNonNullMsg(Throwable t) {

    // Start by going down the chain to find non-null message.
    {
      Throwable current = t;
      while (null != current) {
        if (null != current.getMessage()) {
          return current.getMessage();
        }
        current = current.getCause();
      }
    }

    // If we get here, no message string exists anywhere in the chain. Generate a string that
    // describes the chain of
    // causality.
    StringBuilder sb = new StringBuilder();
    sb.append(t.getClass().getName());

    {
      Throwable current = t.getCause();
      while (null != current) {
        sb.append(" caused by ");
        sb.append(current.getClass().getName());
        current = current.getCause();
      }
    }

    return sb.toString();
  }
}
