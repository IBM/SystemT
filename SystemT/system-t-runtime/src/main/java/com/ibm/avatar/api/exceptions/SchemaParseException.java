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

import com.ibm.avatar.aog.ParseException;

/**
 * Exception to cover problems parsing a TupleSchema that has been encoded as a string inside an AOG
 * file.
 */
public class SchemaParseException extends ParseException {

  /**
   * Version ID for serialization
   */
  private static final long serialVersionUID = 1L;

  /**
   * Main constructor
   * 
   * @param cause lower-level exception that is the root cause of the failure to parse
   * @param schemaStr schema that we were attempting to parse
   * @param problemDescr format string for printing went wrong during parsing
   * @param args arguments of the format string
   */
  public SchemaParseException(Throwable cause, String schemaStr, String problemDescr,
      Object... args) {
    super(String.format(
        "Error parsing serialized schema string '%s': %s.  Please contact IBM support personnel.",
        schemaStr, String.format(problemDescr, args)), cause);

  }

  /**
   * Main constructor
   * 
   * @param schemaStr schema that we were attempting to parse
   * @param problemDescr format string for printing went wrong during parsing
   * @param args arguments of the format string
   */
  public SchemaParseException(String schemaStr, String problemDescr, Object... args) {
    this(null, schemaStr, problemDescr, args);
  }
}
