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
 * Exception class to signal invalid table entry. This exception is thrown by loader, if while
 * loading loader encounters a table entry which does not adheres to declared table schema.
 * 
 */
public class InvalidTableEntryException extends TextAnalyticsException {

  private static final long serialVersionUID = 1L;

  public InvalidTableEntryException(String errorMsg, Object... errorLocation) {
    super(errorMsg, errorLocation);
  }
}
