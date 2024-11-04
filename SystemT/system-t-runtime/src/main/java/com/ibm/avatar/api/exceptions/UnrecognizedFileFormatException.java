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

import java.io.File;

/**
 * Signals that the input document does not fall under the list of recognized file formats.
 * 
 */
public class UnrecognizedFileFormatException extends TextAnalyticsException {

  private static final long serialVersionUID = 7997833900343618675L;

  /**
   * Constructs an UnrecognizedFileFormatException object
   * 
   * @param file The file whose format is not recognized by Text Analytics engine.
   */
  public UnrecognizedFileFormatException(File file) {
    super(String.format(
        "Either the file '%s' does not exist, or it is not in one of the supported input document collection formats.",
        file));
  }

}
