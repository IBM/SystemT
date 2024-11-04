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

import java.util.ArrayList;

import com.ibm.avatar.aql.ParseException;

/**
 * Notifies the user about circular dependencies that arose via include statements.
 * <p>
 * This class should be removed when the include statement is no longer supported.
 * 
 */
public class CircularIncludeDependencyException extends ParseException {
  private static final long serialVersionUID = 1L;

  /**
   * Constructor to create an instance of {@link CircularIncludeDependencyException} exception.
   * 
   * @param includeChain chain of names of included files that led to the circular dependency being
   *        detected
   */
  public CircularIncludeDependencyException(ArrayList<String> includeChain) {
    super(formatErrorMessage(includeChain));
  }

  /**
   * Helper method to generate an appropriate error message describing the particular circular
   * dependency that triggered this exception.
   * 
   * @param includeChain chain of included files that led to circular dependency
   * @return a formatted error message string
   */
  private static String formatErrorMessage(ArrayList<String> includeChain) {
    StringBuilder sb = new StringBuilder();
    sb.append("Circular dependency:\n");
    sb.append(String.format("%s includes %s", includeChain.get(0), includeChain.get(1)));
    for (int j = 2; j < includeChain.size(); j++) {
      sb.append(String.format("\n    which includes %s", includeChain.get(j)));
    }
    return sb.toString();
  }
}
