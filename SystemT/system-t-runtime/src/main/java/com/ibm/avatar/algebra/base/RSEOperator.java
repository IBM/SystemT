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
package com.ibm.avatar.algebra.base;

import com.ibm.avatar.algebra.datamodel.RSEBindings;
import com.ibm.avatar.algebra.datamodel.TupleList;

/**
 * Additional API functions for operators that support restricted span evaluation.
 */
public interface RSEOperator {

  /**
   * Some operators that inherit this interface do not implement its functionality. This method
   * tells whether an given operator is really RSE-capable.
   */
  boolean implementsRSE();

  /**
   * A version of {@link PullOperator#getNext(MemoizationTable)} for RSE. Takes in bindings that
   * pre-filter the operator's output.
   * 
   * @param mt container for state that remains across calls to this method
   * @param b the current set of bindings
   * @return lists of inner tuples, instead of a single top-level tuple; one list for each set of
   *         bindings
   * @throws Exception
   */
  TupleList[] getNextRSE(MemoizationTable mt, RSEBindings[] b) throws Exception;

}
