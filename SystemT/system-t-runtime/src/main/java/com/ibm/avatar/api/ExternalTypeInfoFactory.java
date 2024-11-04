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
package com.ibm.avatar.api;

/**
 * Factory class to create an {@link com.ibm.avatar.api.ExternalTypeInfo} instance. This instance is
 * used to pass in the content of external tables and and external dictionaries when loading an
 * operator graph using the
 * {@link com.ibm.avatar.api.OperatorGraph#createOG(String[], ExternalTypeInfo, com.ibm.avatar.algebra.util.tokenize.TokenizerConfig)}
 * and
 * {@link com.ibm.avatar.api.OperatorGraph#createOG(String[], String, ExternalTypeInfo, com.ibm.avatar.algebra.util.tokenize.TokenizerConfig)}
 * APIs.
 *
 * @see OperatorGraph
 */
public class ExternalTypeInfoFactory {
  /**
   * Method to create an {@link com.ibm.avatar.api.ExternalTypeInfo} instance. This method returns
   * an empty instance to be populated by the user by invoking variants of
   * <code>addDictionary()</code> and <code>addTable()</code> methods of
   * {@link com.ibm.avatar.api.ExternalTypeInfo} class.
   *
   * @return an empty {@link com.ibm.avatar.api.ExternalTypeInfo} instance
   */
  public static ExternalTypeInfo createInstance() {
    return new ExternalTypeInfoImpl();
  }

  // Suppress object instantiation
  private ExternalTypeInfoFactory() {}
}
