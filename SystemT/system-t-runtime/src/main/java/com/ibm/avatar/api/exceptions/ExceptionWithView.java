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
 * Class to encapsulate errors that occurred during the execution of an operator graph that are
 * tagged with a view name.
 * 
 */
public class ExceptionWithView extends RuntimeException {
  private String viewName;

  /**
   * Version ID for serialization
   */
  private static final long serialVersionUID = 1L;

  public ExceptionWithView(String viewName) {
    super(String.format("Exception in view '%s'", viewName));
    this.viewName = viewName;
  }

  public ExceptionWithView(Throwable cause, String viewName) {
    super(String.format("Exception in view '%s'", viewName), cause);
    this.viewName = viewName;
  }

  /**
   * @return name of the view associated with this exception
   */
  public String getViewName() {
    return viewName;
  }
}
