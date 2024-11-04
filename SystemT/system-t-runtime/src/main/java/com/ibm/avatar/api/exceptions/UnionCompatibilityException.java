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

import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.Token;

/**
 * Exception thrown when two inputs to a UNION ALL stmt are not union-compatible. Extends
 * ParseException for consistency with other errors that occur during type inference.
 */
public class UnionCompatibilityException extends ParseException {
  private static final long serialVersionUID = 1L;

  /**
   * Main constructor.
   * 
   * @param origTok AQL parser token of the "union all" keywords
   * @param firstSchema schema of the first input of the union
   * @param badSchema schema of the incompatible input of the union
   * @param badInputIx index of the incompatible input (zero-based)
   */
  public UnionCompatibilityException(Token origTok, AbstractTupleSchema firstSchema,
      AbstractTupleSchema badSchema, int badInputIx) {
    super(String.format(
        "Schema of input %d of 'union all' statement, %s, is not compatible with schema of first input, %s.  "
            + "Ensure that all inputs to the union all statement produce schemas with the same column types and names.",
        badInputIx + 1, badSchema, firstSchema));
    this.currentToken = origTok;
  }

}
