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

public class InvalidOutputNameException extends TextAnalyticsException {

  private static final long serialVersionUID = 1L;

  /**
   * Constructor for situations where you only know the name of the invalid output
   * 
   * @param name name that the caller tried to use
   */
  public InvalidOutputNameException(String name) {
    super("Invalid output name '%s'", name);
  }

  /**
   * Constructor for situations where you know what are the options for output names
   * 
   * @param name name that the caller tried to use
   * @param validNames list of the names that correspond to valid outputs
   */
  public InvalidOutputNameException(String name, Collection<String> validNames) {
    super("Invalid output name '%s' (valid names are %s)", name, validNames);
  }

}
