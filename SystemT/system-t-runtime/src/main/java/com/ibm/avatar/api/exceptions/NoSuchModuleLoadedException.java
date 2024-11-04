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

import com.ibm.avatar.api.OperatorGraph;

/**
 * Exception to signal that the given module name is not found in the loaded {@link OperatorGraph}.
 * 
 */
public class NoSuchModuleLoadedException extends TextAnalyticsException {
  private static final long serialVersionUID = 7672582292452645L;

  /**
   * Name of the module not found in the loaded {@link OperatorGraph}.
   */
  protected String moduleName;

  /**
   * Constructor an {@link NoSuchModuleLoadedException} with the specified module name not found in
   * the loaded operator graph.
   * 
   * @param moduleName name of the module not found in the loaded operator graph
   */
  public NoSuchModuleLoadedException(String moduleName) {
    super();
    this.moduleName = moduleName;
  }

  @Override
  public String getMessage() {
    return String.format("Specified module '%s' is not found in the loaded operator graph.",
        moduleName);
  }

}
