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

import java.io.File;

/**
 * Exception thrown when a reference to a file under a search path (for include, dictionary, etc.
 * resolution) is ambiguous; that is, when it's not clear which files it refers to.
 */
public class AmbiguousPathRefException extends TextAnalyticsException {

  /**
   * Version ID for serialization
   */
  private static final long serialVersionUID = 1L;

  /**
   * Constructor for the case when there are two files, reachable by different elements of the
   * search path.
   */
  public AmbiguousPathRefException(String pathStr, String pattern, File firstFile,
      File secondFile) {
    super(String.format(
        "Reference to '%s' is ambiguous;" + " it matches %s via one part of the search path"
            + " and %s via another. The search path is %s",
        pattern, firstFile, secondFile, pathStr));
  }

  /**
   * Constructor for less-common error conditions.
   */
  public AmbiguousPathRefException(String msg) {
    super(msg);
  }
}
