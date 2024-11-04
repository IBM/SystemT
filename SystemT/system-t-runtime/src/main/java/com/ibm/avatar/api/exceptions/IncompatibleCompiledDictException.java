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

import com.ibm.avatar.api.Constants;

/**
 * Exception to signal that a compiled dictionary is incompatible; that is, the loader could not
 * decompile the dictionary. Currently, this is only thrown when the version is incompatible
 * (policy: has a version number in the future from the current version).
 * 
 */
public class IncompatibleCompiledDictException extends TextAnalyticsException {
  /**
   * Version ID for serialization
   */
  private static final long serialVersionUID = 1L;

  /**
   * Qualified name of the dictionary that throws the exception
   */
  protected String qualifiedDictName;

  /**
   * The version of the dictionary that throws the exception.
   */
  protected String actualVersion;

  /**
   * The path to the TAM file containing the dictionary that throws the exception
   */
  protected String tamPath;

  /**
   * Constructs an exception object by providing details of the dictionary that caused the exception
   * 
   * @param qualifiedDictName fully-qualified name of dictionary
   * @param actualVersion incompatible version number that causes the exception
   * @param tamPath path to TAM file containing the dictionary
   */
  public IncompatibleCompiledDictException(String qualifiedDictName, String actualVersion,
      String tamPath) {
    this.qualifiedDictName = qualifiedDictName;
    this.actualVersion = actualVersion;
    this.tamPath = tamPath;
  }

  /**
   * Constructs an exception object with the given message. This is used to report exceptions caused
   * from loading a dictionary with an incompatible version number with the current version.
   * 
   * @param message the message to output
   */
  public IncompatibleCompiledDictException(String message) {
    super(message);
  }

  /**
   * Set the path to the TAM file containing the incompatible dictionary
   * 
   * @param tamPath the path to the TAM file
   */
  public void setTamPath(String tamPath) {
    this.tamPath = tamPath;
  }

  /**
   * Prepares a message with fully-qualified dictionary name, the version that caused the exception,
   * the path to the TAM file containing the dictionary, and the current version
   * 
   * @return
   */
  public String getOutputMessage() {

    if (this.qualifiedDictName == null) {
      return String.format("'%s' contains a corrupt dictionary file with no name in the header.",
          tamPath);
    } else
      return String.format(
          "Dictionary '%s' in '%s' has an incompatible version number '%s'. Use a dictionary with version '%s' or earlier.",
          qualifiedDictName, tamPath, actualVersion, Constants.PRODUCT_VERSION);
  }
}
