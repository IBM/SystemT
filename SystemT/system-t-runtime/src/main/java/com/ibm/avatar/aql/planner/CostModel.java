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
package com.ibm.avatar.aql.planner;

import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.catalog.Catalog;

/**
 * Abstract base class for a cost model for use in a Selinger-style optimizer. Provides a framework
 * for recursive cost estimation, but leaves the details up to the derived class.
 * 
 */
public abstract class CostModel {

  public double cost(PlanNode node) throws ParseException {
    CostRecord cr = getCostRecord(node);
    return cr.cost();
  }

  /**
   * Obtain a cost record for the indicated plan node, using cached values if possible.
   * 
   * @throws ParseException
   */
  protected CostRecord getCostRecord(PlanNode node) throws ParseException {

    // First, try for a cached cost record.
    CostRecord cr = node.getCostRecord();

    if (null == cr) {
      // No cached cost record; create (and cache) a record.
      cr = computeCostRecord(node);
      node.setCostRecord(cr);
    }

    return cr;
  }

  /**
   * Directly compute a cost record for a node with no cached record.
   * 
   * @throws ParseException
   */
  protected abstract CostRecord computeCostRecord(PlanNode node) throws ParseException;

  /** Copy of the parser's symbol table, for use in costing */
  protected Catalog catalog;

  public void setCatalog(Catalog catalog) {
    this.catalog = catalog;
  }

}
