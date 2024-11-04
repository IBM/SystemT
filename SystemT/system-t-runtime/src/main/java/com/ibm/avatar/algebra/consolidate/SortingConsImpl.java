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
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.TupleComparator;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;

/**
 * A consolidation input that processes input spans in order by begin.
 * 
 */
public abstract class SortingConsImpl extends ConsolidateImpl {

  /**
   * Comparator for sorting tuples by span begin. Initialized during
   * {@link #bind(AbstractTupleSchema)}
   */
  TupleComparator sortOrder;

  /** We override this method so that it also sets up the sort order. */
  @Override
  protected void setSpanGetter(ScalarFunc spanGetter) {
    super.setSpanGetter(spanGetter);

  }

  @Override
  protected void setPriorityFieldGetter(ScalarFunc priorityFieldGetter) {
    super.setPriorityFieldGetter(priorityFieldGetter);
  }

  @Override
  public void bind(AbstractTupleSchema inputSchema) throws ParseException {
    super.bind(inputSchema);

    // Set up the sort order.
    // GetBegin gb = new GetBegin(spanGetter);
    // gb.bind(inputSchema);

    // sortOrder = TupleComparator.makeComparator(gb);

    // Sort by span (e.g. begin, then end)
    try {
      sortOrder = TupleComparator.makeComparator(spanGetter);
    } catch (FunctionCallValidationException e) {
      // Top-level API still expects ParseException
      throw new ParseException("Error during bind", e);
    }
  }

  @Override
  public TupleList consolidate(TupleList input, MemoizationTable mt) throws TextAnalyticsException {
    // Sort the input.
    input.sort(sortOrder);

    TupleList results = new TupleList(input.getSchema());

    reallyConsolidate(input, results, mt);

    return results;
  }

  /**
   * Override this method to perform the actual work of consolidation.
   * 
   * @param input input tuples, sorted by begin of the consolidating span
   * @param results list of tuples that pass
   * @param mt pointer to object that holds thread-local data
   * @throws TextAnalyticsException
   */
  protected abstract void reallyConsolidate(TupleList input, TupleList results, MemoizationTable mt)
      throws FunctionCallValidationException, TextAnalyticsException;

  // protected abstract void reallyConsolidateWithPriority(TupleList input, TupleList results,
  // MemoizationTable mt);

}
