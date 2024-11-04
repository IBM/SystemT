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
 * Exception class that indicates a configuration error with the chunker
 * 
 */
public class ChunkerException extends TextAnalyticsException {

  private static final long serialVersionUID = -5156968551019535088L;

  /**
   * The name of the field that could not be chunked.
   */
  protected String fieldName;


  /**
   * Constructs an instance of this class with the given message
   * 
   * @param fieldName name of the field that could not be chunked
   * @param message error message
   */
  public ChunkerException(String fieldName, String message) {
    super("Error when attempting to chunk field: %s.  " + "%s\n", fieldName, message);
  }

}
