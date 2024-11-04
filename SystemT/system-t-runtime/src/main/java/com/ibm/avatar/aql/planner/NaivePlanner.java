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

import com.ibm.avatar.aql.ConsolidateClauseNode;
import com.ibm.avatar.aql.FromListItemNode;
import com.ibm.avatar.aql.FromListNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.PredicateNode;
import com.ibm.avatar.aql.RValueNode;
import com.ibm.avatar.aql.SelectListNode;
import com.ibm.avatar.aql.SelectNode;
import com.ibm.avatar.aql.WhereClauseNode;

/**
 * A naive query optimizer for AQL. Generates plans that join the elements of a SELECT statement in
 * the order they appear in the FROM list, using nested loops join only. Pushes down predicates as
 * far as they will go.
 * 
 */
public class NaivePlanner extends PlannerImpl {

  @Override
  protected PlanNode computeJoinPlan(SelectNode stmt) throws ParseException {
    TreeSet<PredicateNode> availPreds = new TreeSet<PredicateNode>();
    WhereClauseNode whereClause = stmt.getWhereClause();
    if (null != whereClause) {
      availPreds.addAll(whereClause.getPreds());
    }
    FromListNode fromList = stmt.getFromList();
    SelectListNode selectList = stmt.getSelectList();
    ConsolidateClauseNode consolidateClause = stmt.getConsolidateClause();
    ArrayList<RValueNode> groupByVals = null;
    if (null != stmt.getGroupByClause())
      groupByVals = stmt.getGroupByClause().getValues();

    return reallyComputeJoin(fromList, selectList, fromList.size() - 1, availPreds, groupByVals,
        consolidateClause);
  }

  /**
   * Recursive function that does the heavy lifting of "optimizing" a select statement.
   * 
   * @param fromList the entries in the select statement's from list
   * @param selectList the entries in the select statement's select list
   * @param offset an offset from the left of the from list (0 is the first entry); since we're just
   *        generating a join tree in the order that relations appear in the from list, this offset
   *        also tells us the position within the join tre
   * @param usedPreds predicates that have already been applied earlier in the tree.
   * @param availPreds predicates that could be evaluated at this point in the plan
   * @param groupByValues group by clause node if present as a part of the select rule
   * @param consolidateClause consolidate clause node of the select rule
   * @return root of the plan that joins the from list nodes up to the indicated offset
   * @throws ParseException
   */
  protected PlanNode reallyComputeJoin(FromListNode fromList, SelectListNode selectList, int offset,
      TreeSet<PredicateNode> availPreds, ArrayList<RValueNode> groupByValues,
      ConsolidateClauseNode consolidateClause) throws ParseException {

    // We join the inputs in order from left (offset 0) to right (offset
    // fromList.size() - 1). However, since we generate the join tree from
    // the top down, the top-level call to this method has offset
    // fromList.size() - 1.

    if (0 == offset) {
      // BASE CASE: Leftmost element in the join tree.
      FromListItemNode target = fromList.get(0);

      ArrayList<PredicateNode> filters = getFilterPreds(target, availPreds);

      ArrayList<String> colsToKeep =
          getNeededCols(catalog, target, availPreds, selectList, groupByValues, consolidateClause);

      ScanNode scan = new ScanNode(target, colsToKeep, catalog);

      // Apply filtering predicates if applicable.
      if (filters.size() > 0) {
        availPreds.removeAll(filters);
        return new SelectionNode(scan, filters);
      } else {
        // No filtering preds.
        return scan;
      }
    }

    // RECURSIVE CASE: We're in the middle of the join tree.

    // Generate the inputs to the join.
    PlanNode outer = reallyComputeJoin(fromList, selectList, offset - 1, availPreds, groupByValues,
        consolidateClause);

    // Inner is always a scan; try to push down predicates if possible.
    FromListItemNode innerRel = fromList.get(offset);

    ArrayList<String> colsToKeep =
        getNeededCols(catalog, innerRel, availPreds, selectList, groupByValues, consolidateClause);

    ScanNode innerScan = new ScanNode(innerRel, colsToKeep, catalog);
    ArrayList<PredicateNode> innerFilters = getFilterPreds(innerRel, availPreds);
    PlanNode inner;
    if (innerFilters.size() > 0) {
      availPreds.removeAll(innerFilters);
      inner = new SelectionNode(innerScan, innerFilters);
    } else {
      inner = innerScan;
    }

    // Generate the join

    // Nested-loops join can apply all filtering predicates as join
    // predicates, so there's no need for a post-filtering pass.
    ArrayList<FromListItemNode> relations = new ArrayList<FromListItemNode>();
    for (int i = 0; i <= offset; i++) {
      relations.add(fromList.get(i));
    }
    ArrayList<PredicateNode> joinpreds = getFilterPreds(catalog, relations, availPreds);
    availPreds.removeAll(joinpreds);
    return new NLJoinNode(outer, inner, joinpreds);

  }

}
