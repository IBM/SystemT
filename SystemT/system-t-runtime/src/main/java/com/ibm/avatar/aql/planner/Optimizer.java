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
import java.util.Map;
import java.util.TreeMap;
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
import com.ibm.avatar.logging.Log;

/**
 * Base class for a Selinger-style query optimizer. Considers all left-deep query plans, according
 * to pluggable cost models and join plan generators. Pushes selections and projections down as far
 * as they will go.
 * 
 */
public abstract class Optimizer extends PlannerImpl {

  public static final boolean DEBUG_DYNAMIC_PROG = false;

  /** The model used for estimating the relative cost of subplans. */
  protected CostModel costModel;

  /** Factory for generating join plans. */
  protected JoinGenerator joinGenerator;

  protected Optimizer(CostModel costModel, JoinGenerator joinGenerator) {
    this.costModel = costModel;
    this.joinGenerator = joinGenerator;
  }

  /**
   * Main entry point. Uses dynamic programming to build up a set of candidate plans of increasing
   * size.
   */
  @Override
  protected PlanNode computeJoinPlan(SelectNode stmt) throws ParseException {

    // Make sure that the join generator and cost model have the latest
    // symbol tables.
    joinGenerator.setCatalog(catalog);
    costModel.setCatalog(catalog);

    // Start by breaking down the select statement into its component parts.
    TreeSet<PredicateNode> availPreds = new TreeSet<PredicateNode>();
    WhereClauseNode whereClause = stmt.getWhereClause();
    ConsolidateClauseNode consolidateClause = stmt.getConsolidateClause();
    if (null != whereClause) {
      availPreds.addAll(whereClause.getPreds());
    }
    FromListNode fromList = stmt.getFromList();

    ArrayList<RValueNode> groupByVals = null;
    if (null != stmt.getGroupByClause())
      groupByVals = stmt.getGroupByClause().getValues();

    // Convert the from list to a set for convenience.
    // Note that we use TreeSets and TreeMaps throughout to ensure that we
    // always get the same plan, regardless of JVM.
    TreeSet<FromListItemNode> rels = new TreeSet<FromListItemNode>(fromList.getItems());

    SelectListNode selectList = stmt.getSelectList();

    // Now we have a list of tables to join (fromList) and a set of
    // predicates to apply to them. Create the data structures we'll need to
    // hold two generations of intermediate plans.

    PlanNode optimalPlan =
        doDynamicProg(availPreds, fromList, selectList, rels, groupByVals, consolidateClause);

    // Verify that the optimal plan applies all the predicates in the
    // where clause.
    TreeSet<PredicateNode> usedPreds = new TreeSet<PredicateNode>();
    optimalPlan.getPreds(usedPreds);

    for (PredicateNode pred : availPreds) {
      if (false == usedPreds.contains(pred)) {
        if (pred.hasMergeImpl()) {
          // Due to the asymmetric nature of our merge join, merge
          // join predicates may be reversed
          // (e.g. Follows(x,y) --> FollowedBy(y,x))
          // Try the reversed version before concluding that the plan
          // doesn't cover this predicate.
          pred = pred.reverse(catalog);
        }
      }

      // Check a second time, since the first check may have failed for a
      // reversed merge join predicate.
      if (false == usedPreds.contains(pred)) {
        System.err.printf("Error computing join plan for statement:\n");
        stmt.dump(System.err, 1);
        System.err.printf("\n\nGenerated plan:\n");
        optimalPlan.dump(System.err, 1);
        throw new RuntimeException(
            String.format("Optimizer generated plan " + "that doesn't apply predicate %s", pred));
      }
    }

    return optimalPlan;
  }

  /**
   * Subroutine that implements the Selinger algorithm for left-deep plans.
   * 
   * @param allPreds predicates that we need to apply.
   * @param fromList parse tree node for the FROM list of the current basic block (select statement)
   * @param rels relations referenced in the FROM list
   * @param groupByValues group by clause node if present as a part of the select rule
   * @param consolidateClause consolidate clause node of the select rule
   * @return the optimal plan
   * @throws ParseException
   */
  private PlanNode doDynamicProg(TreeSet<PredicateNode> _allPreds, FromListNode fromList,
      SelectListNode selectList, TreeSet<FromListItemNode> rels,
      ArrayList<RValueNode> groupByValues, ConsolidateClauseNode consolidateClause)
      throws ParseException {

    // Arrays of logical plans
    ArrayList<PlanNode> currentPlans = new ArrayList<PlanNode>();

    // Make a copy of the predicates, so that we can modify it.
    TreeSet<PredicateNode> availPreds = new TreeSet<PredicateNode>();
    availPreds.addAll(_allPreds);

    // Start out with the single-relation plans.
    // Save them in a separate set, too, since we'll need them during plan
    // generation.
    TreeMap<FromListItemNode, PlanNode> singletonPlans = new TreeMap<FromListItemNode, PlanNode>();
    for (FromListItemNode target : rels) {
      PlanNode node =
          getSingletonPlan(availPreds, selectList, target, groupByValues, consolidateClause);
      singletonPlans.put(target, node);
      currentPlans.add(node);

      // Remove the predicates that will be evaluated in the
      // single-relation plans from consideration as join predicates.
      TreeSet<PredicateNode> preds = new TreeSet<PredicateNode>();
      node.getPreds(preds);
      availPreds.removeAll(preds);
    }

    // Iteratively add to the plans until we've covered all the relations.
    for (int planSize = 1; planSize < fromList.size(); planSize++) {

      // Build up a mapping from <set of relations> --> <best plan>
      // Note: Use TreeMap instead of HashMap so that order of plans is maintained
      Map<String, PlanNode> nextPlans = new TreeMap<String, PlanNode>();

      // For each plan we currently have, try adding each possible
      // relation to the end.
      for (int i = 0; i < currentPlans.size(); i++) {
        PlanNode curPlan = currentPlans.get(i);

        TreeSet<FromListItemNode> curRels = new TreeSet<FromListItemNode>();
        curPlan.getRels(curRels);

        for (FromListItemNode target : rels) {
          if (false == curRels.contains(target)) {
            // The target relation isn't in the current plan.
            // Create a new plan that joins the target relation into
            // the result of the current plan.
            TreeSet<FromListItemNode> newRels = new TreeSet<FromListItemNode>(curRels);
            newRels.add(target);

            // Figure out which predicates are available to apply.
            TreeSet<PredicateNode> outerPreds = new TreeSet<PredicateNode>();
            curPlan.getPreds(outerPreds);

            TreeSet<PredicateNode> availJoinPreds = new TreeSet<PredicateNode>();
            availJoinPreds.addAll(availPreds);
            availJoinPreds.removeAll(outerPreds);

            // Retrieve the singleton plan we computed earlier.
            PlanNode innerPlan = singletonPlans.get(target);
            // getSingletonPlan(availPreds, selectList, target);

            // Now we can generate plans.
            ArrayList<PlanNode> newPlans =
                joinGenerator.createJoinPlans(curPlan, innerPlan, availPreds);

            String KEY = newRels.toString();

            if (DEBUG_DYNAMIC_PROG) {
              Log.debug("Considering the following plans for joining relations %s:", KEY);
              for (PlanNode planNode : newPlans) {
                Log.debug("%s", planNode.dumpToString(2));
              }
            }

            for (PlanNode newPlan : newPlans) {
              PlanNode bestPlan = nextPlans.get(KEY);

              if (null == bestPlan) {
                nextPlans.put(KEY, newPlan);
              } else {
                double newPlanCost = costModel.cost(newPlan);
                double bestPlanCost = costModel.cost(bestPlan);

                if (newPlanCost < bestPlanCost) {
                  nextPlans.put(KEY, newPlan);
                }
              }
            }
          }
        }
      }

      // Swap our plan sets to move to the next iteration.
      currentPlans.clear();
      for (PlanNode plan : nextPlans.values()) {
        currentPlans.add(plan);
      }
    }

    if (currentPlans.size() != 1) {
      throw new RuntimeException(String.format(
          "After %d rounds of dynamic programming," + " have %d plans -- should only have one",
          fromList.size(), currentPlans.size()));
    }

    return currentPlans.get(0);
  }

  /**
   * Computes single-relation plans
   * 
   * @param availPreds predicates that could be evaluated at this point in the plan
   * @param selectList select list of the clause being compiled
   * @param target one of the input relations on the current select/extract clause being compiled
   * @param groupByValues group by clause node if present as a part of the select rule
   * @param consolidateClause consolidate clause node of the select rule
   * @return
   * @throws ParseException
   */
  private PlanNode getSingletonPlan(TreeSet<PredicateNode> availPreds, SelectListNode selectList,
      FromListItemNode target, ArrayList<RValueNode> groupByValues,
      ConsolidateClauseNode consolidateClause) throws ParseException {
    ArrayList<PredicateNode> filters = getFilterPreds(target, availPreds);

    ArrayList<String> colsToKeep =
        getNeededCols(catalog, target, availPreds, selectList, groupByValues, consolidateClause);

    if (0 == colsToKeep.size()) {
      // SPECIAL CASE: No columns are used; this typically happens in
      // statements like:
      // select 1 as one from Document D
      colsToKeep = null;
    }

    ScanNode scan = new ScanNode(target, colsToKeep, catalog);

    // Apply filtering predicates if applicable.
    PlanNode node = null;
    if (filters.size() > 0) {
      node = new SelectionNode(scan, filters);
      // availPreds.removeAll(filters);
    } else {
      // No filtering preds.
      node = scan;
    }
    return node;
  }

  /**
   * Helper function to compute and add a cost record to a plan node
   * 
   * @param node The plan node to modify
   * @throws ParseException
   */
  @Override
  public void addCostRecordToNode(PlanNode node) throws ParseException {
    CostRecord costRec = costModel.computeCostRecord(node);
    node.setCostRecord(costRec);
  }

}
