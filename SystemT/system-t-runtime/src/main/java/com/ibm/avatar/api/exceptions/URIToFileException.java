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

public class URIToFileException extends TextAnalyticsException {

  private static final long serialVersionUID = 1L;

  /**
   * Main constructor; builds up a user friendly error message in a standard format
   * 
   * @param cause the actual exception encountered when attempting to create a file from a uri
   * @param uri the content of the uri causing the problem
   */
  public URIToFileException(Throwable cause, String uri) {
    super(cause, "Exception creating a file from URI: %s", uri);
  }

}
