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
 * Exception class to indicate that the tokenizer configuration specified during compilation time
 * does not match
 * 
 */
public class IncompatibleTokenizerConfigException extends TextAnalyticsException {

  private static final long serialVersionUID = -4942264566212033529L;

  /**
   * Use this constructor to throw an exception when the compile-time and load-time tokenizer do not
   * match
   * 
   * @param moduleName
   * @param compiletimeTokenizer
   * @param loadtimeTokenizer
   */
  public IncompatibleTokenizerConfigException(String moduleName, String compiletimeTokenizer,
      String loadtimeTokenizer) {
    super(String.format(
        "The compile-time tokenizer for module %s is set to %s, but the load-time tokenizer is set to %s. The compile-time tokenizer and the load-time tokenizer must match.",
        moduleName, compiletimeTokenizer, loadtimeTokenizer));
  }

  /**
   * Use this constructor to throw an exception when two modules in a module set have incompatible
   * tokenizer types
   * 
   * @param moduleName
   * @param compiletimeTokenizer
   * @param loadtimeTokenizer
   */
  public IncompatibleTokenizerConfigException(String module1, String module2, String tokenizer1,
      String tokenizer2) {
    super(String.format(
        "Modules %s and %s cannot be combined because their tokenizer types (%s and %s) are different.",
        module1, module2, tokenizer1, tokenizer2));

  }

  /**
   * Constructs an exception object with the given message. This is used to report exceptions caused
   * from loading an external dictionary with an incompatible tokenizer type.
   * 
   * @param message the message to output
   */
  public IncompatibleTokenizerConfigException(String message) {
    super(message);

  }

}
