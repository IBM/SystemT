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

import com.ibm.avatar.api.udf.TableUDFBase;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.Token;

/**
 * Exception thrown when a table function is declared with an implementing class that is not a
 * subclass of {@link TableUDFBase}.
 */
public class TableFunctionWrongTypeException extends ParseException {

  /**
   * Version ID for serialization.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Main constructor
   * 
   * @param t location of the error (usually the function declaration)
   * @param functionName name of the table function
   * @param className class that the "create function" statement declared was the implementing class
   *        for the table function
   */
  public TableFunctionWrongTypeException(Token t, String functionName, String className) {
    super(String.format(
        "Table function '%s' declared with implementing class %s, "
            + "but that class is not a subclass of %s.  "
            + "Classes that implement table functions must be subclasses of %s.",
        functionName, className, TableUDFBase.class.getName(), TableUDFBase.class.getSimpleName()));
    this.currentToken = t;
  }

}
