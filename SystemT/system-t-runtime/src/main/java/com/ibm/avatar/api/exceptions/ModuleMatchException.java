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
 * Exception class to notify about failure to find a module in the given modulepath
 * 
 */
public abstract class ModuleMatchException extends TextAnalyticsException {

  private static final long serialVersionUID = 2175787066324190524L;

  /**
   * Name of the module
   */
  protected String moduleName;

  /**
   * Module path passed in by the user
   */
  protected String modulePath;

  public ModuleMatchException(String moduleName, String modulePath) {
    super();
    this.moduleName = moduleName;
    this.modulePath = modulePath;
  }

  @Override
  public String getMessage() {
    return String.format("Module %s not found in %s.", moduleName, modulePath);
  }

}
