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
 * When OperatorGraph is loaded for a given set of modules, if there are any element references that
 * could not be resolved, then this exception is thrown. For eg, if module A defined a view called
 * Person, which was imported in module B, but module A is not in module load path of module B, then
 * this exception is thrown at the time of operator graph loading.
 * 
 */
public class DependencyResolutionException extends TextAnalyticsException {
  private static final long serialVersionUID = -8876205759555337343L;

  /**
   * Name of the element missing in the operator graph
   */
  protected String missingElement;

  /**
   * Name of the module whose OperatorGraph is incomplete. <br/>
   */
  protected String moduleName;

  public DependencyResolutionException(String moduleName, String missingElement, String message) {
    super(formatMessage(moduleName, missingElement, message));
    this.moduleName = moduleName;
  }

  private static String formatMessage(String moduleName, String missingElement, String message) {
    return String.format(
        "The compiled module '%s' is incomplete because at least one of the dependencies of element '%s' could not be resolved\n."
            + "Ensure that the dependent module has a definition of the specified element\n."
            + "Detailed message: %s",
        moduleName, missingElement, message);
  }
}
