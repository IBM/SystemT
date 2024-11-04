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

import java.util.ArrayList;
import java.util.TreeSet;

import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.PredicateNode;
import com.ibm.avatar.aql.catalog.Catalog;

/**
 * Abstract base class for factories that generate plans for joining subtrees together.
 * 
 */
public abstract class JoinGenerator {

  protected Catalog catalog;

  public void setCatalog(Catalog catalog) {
    this.catalog = catalog;
  }

  /**
   * Generate a plan that joins the indicated two subtrees.
   * 
   * @param outer left-hand subtree of the join
   * @param inner right-hand subtree of the join
   * @param availPreds predicates that are available to use as join or selection predicates.
   * @return all possible join plans
   * @throws ParseException
   */
  public abstract ArrayList<PlanNode> createJoinPlans(PlanNode outer, PlanNode inner,
      TreeSet<PredicateNode> availPreds) throws ParseException;

}
