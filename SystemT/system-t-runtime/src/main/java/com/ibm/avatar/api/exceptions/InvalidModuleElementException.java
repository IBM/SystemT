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

import java.util.Collection;

import com.ibm.avatar.api.tam.ModuleMetadata.ElementType;

/**
 * Exception to signal that either the given element type, element name, or both does not exist in
 * the module metadata.
 *
 */
public class InvalidModuleElementException extends TextAnalyticsException {

  private static final long serialVersionUID = 1L;

  /**
   * Constructor for situations where you only know the name and type of the invalid output
   *
   * @param type type of the element that the caller tried to use
   * @param name name that the caller tried to use
   */
  public InvalidModuleElementException(ElementType type, String name) {
    super("Element %s of type %s does not exist.", name, type.name());
  }

  /**
   * Constructor for situations where the possible options for output names are known.
   *
   * @param type type of the element that the caller tried to use
   * @param name name that the caller tried to use
   * @param validNames list of the names that correspond to valid outputs
   */
  public InvalidModuleElementException(ElementType type, String name,
      Collection<String> validNames) {
    super("Element %s of type %s does not exist. Existing elements of this type are: %s", name,
        type.name(), validNames);
  }

}
