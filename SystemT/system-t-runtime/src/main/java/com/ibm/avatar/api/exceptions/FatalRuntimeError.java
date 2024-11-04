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
 * Class to encapsulate unchecked exceptions thrown during runtime, or to an API that is not clearly
 * executing during compilation of AQL code.
 * 
 */
public class FatalRuntimeError extends TextAnalyticsException {
  private static final long serialVersionUID = 1L;

  public FatalRuntimeError(Throwable cause) {
    super(cause, "cause: " + cause.toString() + "::An internal error occurred.\n"
        + "Gather the full stack trace that is associated with this error, and contact IBM Software Support.");
  }
}
