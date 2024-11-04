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
package com.ibm.avatar.algebra.exceptions;

import com.ibm.avatar.aql.ParseException;

/**
 * As the name indicates, this exception class identifies the problem when an entity looked up in
 * the catalog is not found.
 * 
 */
public class CatalogEntryNotFoundException extends ParseException {

  private static final long serialVersionUID = 7168274726191421626L;

  /**
   * @param message
   */
  public CatalogEntryNotFoundException(String message) {
    super(message);
  }

}
