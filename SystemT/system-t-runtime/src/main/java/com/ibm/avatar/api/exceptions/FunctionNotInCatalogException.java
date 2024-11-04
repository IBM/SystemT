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

/** Exception to throw when a given function is not found in the AQL catalog. */
public class FunctionNotInCatalogException extends ParseException {

  /**
   * Version ID for serialization
   */
  private static final long serialVersionUID = 4815556165244418080L;

  private static final String MSG_FORMAT_STR = "Function name '%s' is not a valid reference. "
      + "Ensure that the function is defined and is visible in the current module, "
      + "accessible by the given name.";

  /**
   * @param funcName name of the function for which lookup failed
   */
  public FunctionNotInCatalogException(String funcName) {
    super(String.format(MSG_FORMAT_STR, funcName));
  }

  /**
   * @param funcName name of the function for which lookup failed
   * @param errLoc location in the AQL file where the error should be reported
   */
  public FunctionNotInCatalogException(String funcName, Token errLoc) {
    super(String.format(MSG_FORMAT_STR, funcName));
    this.currentToken = errLoc;
  }

}
