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
import java.util.List;

/**
 * Exception class when a required module could not be loaded from the module path, when validating
 * or instantiating an instance of <code>OperatorGraph</code>.
 * 
 */
public class ModuleLoadException extends TextAnalyticsException {
  private static final long serialVersionUID = 5659269712421748072L;

  /**
   * List of module names that could not be loaded.
   */
  protected List<String> moduleNames;

  /**
   * Constructs an instance of this class with the specified cause.
   * 
   * @param moduleName name of the module that could not be loaded
   * @param cause Root cause of this exception
   */
  public ModuleLoadException(String moduleName, Throwable cause) {
    super(cause, "Error in loading module(s): %s.\nDetailed message: %s", moduleName,
        TextAnalyticsException.findNonNullMsg(cause));
    this.moduleNames = new ArrayList<String>();
    this.moduleNames.add(moduleName);
  }

  /**
   * Constructs an instance of this class with the specified detailed error message.
   * 
   * @param modules list of the module names that could not be loaded
   * @param message detailed error message
   */
  public ModuleLoadException(List<String> modules, String message) {
    super("Error in loading module(s): %s.\nDetailed message: %s", modules, message);
    this.moduleNames = modules;
  }

  /**
   * Constructs an instance of this class with the specified detailed error message.
   * 
   * @param modules list of the module names that could not be loaded
   * @param cause original cause of this exception
   */
  public ModuleLoadException(List<String> modules, Throwable cause) {
    super(cause, "Error in loading module(s): %s.\nDetailed message: %s", modules,
        TextAnalyticsException.findNonNullMsg(cause));
    this.moduleNames = modules;
  }

  /**
   * Returns a list of module names that could not be loaded.
   * 
   * @return a list of module names that could not be loaded.
   */
  public List<String> getModuleNames() {
    return moduleNames;
  }
}
