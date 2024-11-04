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
package com.ibm.avatar.algebra.function.predicate;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.RSEBindings;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.algebra.function.base.SelectionPredicate;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.Token;

/**
 * A join predicate for use in a restricted span evaluation join.
 */
public abstract class RSEJoinPred extends SelectionPredicate {

  protected RSEJoinPred(Token origTok, AQLFunc[] args) {
    super(origTok, args);
  }

  /**
   * @param outerTup a tuple from the outer relation of the join
   * @return bindings for fetching matching inner tuples, or null if no valid bindings exist.
   * @throws TextAnalyticsException
   */
  public abstract RSEBindings getBindings(Tuple outerTup, MemoizationTable mt)
      throws TextAnalyticsException;

}
