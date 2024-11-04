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

import com.ibm.avatar.algebra.function.base.AQLFunc;

/**
 * Class for exceptions thrown while validating a function call (either during compilation or during
 * loading). Generates an error message with a "usage" message showing proper use of the function.
 * 
 */
public class FunctionCallValidationException extends TextAnalyticsException {
  /**
   * Version ID for serialization
   */
  private static final long serialVersionUID = -7692783939278388748L;

  /** Main constructor: adds a human-readable "usage" message after the error message. */
  public FunctionCallValidationException(AQLFunc func, String fmt, Object... args) {
    super("%s  %s", punctuate(String.format(fmt, args)), func.getUsage());
  }

  /** Constructor for when there is a root cause exception available.. */
  public FunctionCallValidationException(Throwable cause, AQLFunc func, String fmt,
      Object... args) {
    super(cause, "%s  %s", punctuate(String.format(fmt, args)), func.getUsage());
  }

  private static String punctuate(String s) {
    if (s.endsWith(".")) {
      return s;
    } else {
      return s + ".";
    }
  }

  /** Constructor for the common case of wrong number of args. */
  public FunctionCallValidationException(AQLFunc func, int expectedNumArgs, int actualNumArgs) {
    this(func, "Received %d arguments instead of %d.", actualNumArgs, expectedNumArgs);
  }

  /**
   * Constructor for cases (mostly merge join predicates) where a function object isn't available.
   */
  public FunctionCallValidationException(String fmt, Object... args) {
    super(fmt, args);
  }
}
