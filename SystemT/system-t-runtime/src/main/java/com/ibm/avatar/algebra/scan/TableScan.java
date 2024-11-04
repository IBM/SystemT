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
package com.ibm.avatar.algebra.scan;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.base.MultiInputOperator;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.Table;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;

/**
 * A scan over a static lookup table. Returns the same set of tuples every time it is invoked.
 */
public class TableScan extends MultiInputOperator {

  @Override
  public boolean outputIsAlwaysTheSame() {
    // TableScan always produces the same output.
    return true;
  }

  /** The actual table to scan. */
  private Table table;

  /**
   * Main constructor.
   */
  public TableScan(Table table) {
    super();
    this.table = table;
  }

  @Override
  protected void initStateInternal(MemoizationTable mt) throws TextAnalyticsException {
    // Make sure the tuples are created before the runtime goes multithreaded.
    table.getTups();
  }

  @Override
  protected void reallyEvaluate(MemoizationTable mt, TupleList[] childResults) throws Exception {
    // Nothing to do but return the tuples in the lookup table.
    // System.err.printf("TableScan returning:\n%s\n", tups.toPrettyString());
    addResultTups(table.getTups(), mt);
  }

  @Override
  protected AbstractTupleSchema createOutputSchema() {
    return table.getSchema();
  }

}
