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
 * Exception class to indicate that the specified module is not found in the given module path
 * 
 */
public class ModuleNotFoundException extends ModuleMatchException {

  private static final long serialVersionUID = 4028867118850873251L;

  /**
   * Constructs the exception with the given moduleName and modulePath
   * 
   * @param moduleName
   * @param modulePath
   */
  public ModuleNotFoundException(String moduleName, String modulePath) {
    super(moduleName, modulePath);
  }

  /**
   * Constructs the exception with the given moduleName and system class path
   * 
   * @param moduleName
   */
  public ModuleNotFoundException(String moduleName) {
    this(moduleName, "Text Analytics application's class loader and System class loader");
  }

  @Override
  public String getMessage() {
    return String.format("Module file '%s.tam' is not found in the specified module path: %s",
        moduleName, modulePath);
  }

  /**
   * @return the module name that could not be found
   */
  public String getModuleName() {
    return this.moduleName;
  }

  /**
   * @return the modulePath where the specified module name could not be found
   */
  public String getModulePath() {
    return this.modulePath;
  }
}
