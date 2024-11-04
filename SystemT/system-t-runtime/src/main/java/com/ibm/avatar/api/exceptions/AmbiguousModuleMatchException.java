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

/**
 * Exception thrown when more than one .tam file with the same name is found in the module path
 * 
 */
public class AmbiguousModuleMatchException extends ModuleMatchException {
  private static final long serialVersionUID = 715937763784753498L;

  protected ArrayList<String> ambigousMatches;

  public AmbiguousModuleMatchException(String moduleName, String modulePath,
      ArrayList<String> ambigousMatches) {
    super(moduleName, modulePath);
    this.ambigousMatches = ambigousMatches;
  }

  @Override
  public String getMessage() {
    return String.format("More than one Text Analytics Module file found for module '%s'\n"
        + "Module path: %s\n" + "Matches: %s\n", moduleName, modulePath,
        ambigousMatches.toString());
  }

}
