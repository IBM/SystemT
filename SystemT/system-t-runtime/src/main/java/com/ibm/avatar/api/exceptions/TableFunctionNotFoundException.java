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

import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.Token;

/**
 * Exception thrown when a table function referenced in a FROM clause cannot be found in the
 * catalog. Extends ParseException because the code that checks for valid table function references
 * uses ParseException for error handling.
 */
public class TableFunctionNotFoundException extends ParseException {

  /**
   * Version ID for serialization.
   */
  private static final long serialVersionUID = 1L;

  public TableFunctionNotFoundException(Token t, String functionName) {
    super(String.format(
        "Table function '%s' not found in catalog.  "
            + "Please ensure that a table function by that name has been declared "
            + "with a 'create function' statement or imported with an 'import' statement.",
        functionName));
    this.currentToken = t;
  }

}
