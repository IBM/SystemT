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
package com.ibm.avatar.algebra.base;

/** Record of where the SystemT runtime is executing at a given instant of time. */
public class ProfileRecord {

  public String viewName;

  public Operator operator;

  public ProfileRecord(String viewName, Operator operator) {
    this.viewName = viewName;
    this.operator = operator;
  }

  public void setViewName(String viewName) {
    this.viewName = viewName;
  }

  public String getViewName() {
    return viewName;
  }
}
