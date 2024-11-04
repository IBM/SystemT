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
package com.ibm.avatar.aql;

/**
 * Enumeration of supported programming languages for user-defined function implementations.
 * <p>
 * By convention, the compiler uses the default string serialization of each element in this
 * enumeration when serializing function information. <b>DO NOT CHANGE THE NAMES OF ELEMENTS OF THIS
 * ENUM!</b> Doing so will break backwards compatibility.
 * 
 */
public enum FuncLanguage {
  Java("java"), PMML("pmml");

  /**
   * Main constructor
   * 
   * @param aqlTok token in the AQL language that represents this language type
   */
  private FuncLanguage(String aqlTok) {
    this.aqlTok = aqlTok;
  }

  private String aqlTok;

  /**
   * @return token in the AQL language that represents this language type; may be different from the
   *         enum's default string representation
   */
  public String getAqlTok() {
    return aqlTok;
  }
}
