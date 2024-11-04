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
package com.ibm.avatar.api.tam;

/**
 * This class encapsulates a parameter of a User Defined Function (UDF) as declared via the AQL
 * <code>create function</code> statement.
 *
 */
public class Param {
  String name;
  String type;

  /**
   * <p>
   * Constructor for Param.
   * </p>
   *
   * @param name
   * @param type
   */
  public Param(String name, String type) {
    super();
    this.name = name;
    this.type = type;
  }

  /**
   * Returns the name of the parameter as declared by the <code>create function</code> statement.
   *
   * @return the name of the parameter as declared by the <code>create function</code> statement
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the data type of the parameter as declared by the <code>create function</code>
   * statement.
   *
   * @return the data type of the parameter as declared by the <code>create function</code>
   *         statement
   */
  public String getType() {
    return type;
  }

}
