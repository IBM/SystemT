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
package com.ibm.avatar.aog;

/**
 * Exception for reporting errors when converting a parse tree to a graph of Avatar operators.
 * 
 */
public class AOGConversionException extends ParseException {

  /**
   * Dummy serialization stuff.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Template for the error message to be thrown when the AOG is invalid.
   */
  public static final String ERROR_INVALID_AOG =
      "The compiled extractor for module %s is invalid. Cause: %s";

  /**
   * Generate a user-friendly error message when conversion fails.
   * 
   * @param origTok token somewhere close to the part of the AOG file that caused the error
   * @param message describes the error
   */
  public AOGConversionException(Token origTok, String message) {
    super(String.format("At line %d, column %d: %s", (null == origTok) ? -1 : origTok.beginLine,
        (null == origTok) ? -1 : origTok.beginColumn, message));
  }

}
