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
package com.ibm.avatar.algebra.consolidate;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;

/**
 * Consolidation that allows only the last span in the document to pass.
 * 
 */
public class RetainLastConsImpl extends SortingConsImpl {

  @Override
  protected void reallyConsolidate(TupleList sortedInput, TupleList results, MemoizationTable mt) {

    if (0 == sortedInput.size()) {
      // SPECIAL CASE: Empty input
      return;
      // END SPECIAL CASE
    } else {
      // Have at least one tuple; return only the first one in the sort
      // order.
      Tuple lastTup = sortedInput.getElemAtIndex(sortedInput.size() - 1);
      results.add(lastTup);
    }
  }
}
