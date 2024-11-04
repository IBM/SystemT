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

import com.ibm.avatar.aql.FromListItemNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.PredicateNode;

public class HashJoinGenerator extends RSEJoinGenerator {

  private static boolean DEBUG = false;

  /**
   * Hook to disable RSE join in emergencies
   */
  public static boolean ENABLE_RSE_JOIN = true;

  @Override
  public ArrayList<PlanNode> createJoinPlans(PlanNode outer, PlanNode inner,
      TreeSet<PredicateNode> availPreds) throws ParseException {
    // Uses the superclass's implementation, with additional code to
    // generate hash joins.
    ArrayList<PlanNode> ret = new ArrayList<PlanNode>();

    TreeSet<FromListItemNode> outerRels = new TreeSet<FromListItemNode>();
    outer.getRels(outerRels);

    TreeSet<FromListItemNode> innerRels = new TreeSet<FromListItemNode>();
    inner.getRels(innerRels);

    ArrayList<FromListItemNode> joinRels = new ArrayList<FromListItemNode>();
    joinRels.addAll(outerRels);
    joinRels.addAll(innerRels);

    if (DEBUG) {
      System.err.printf("HashJoinGenerator.createJoinPlans():\n");
      System.err.printf("    Outer rels: %s\n" + "    Inner rels: %s\n" + "    Avail preds: %s\n",
          outerRels, innerRels, availPreds);
    }

    // Attempt to generate a hash join.
    generateHashJoin(outer, inner, availPreds, ret, outerRels, innerRels, joinRels);

    // Attempt to generate a plan that uses RSE join.
    if (ENABLE_RSE_JOIN) {
      generateRSEJoin(outer, inner, availPreds, ret, outerRels, innerRels, joinRels);
    }

    // Attempt to generate a merge join plan. Note that we do this step
    // regardless of whether we found an RSE join.
    generateMergeJoin(outer, inner, availPreds, ret, outerRels, innerRels, joinRels);

    // Other option is to generate a nested-loops join.
    generateNLJoin(outer, inner, availPreds, ret, joinRels);

    return ret;
  }

  /**
   * Generate all possible hash join plans for the indicated inputs.
   * 
   * @param outer root of plan that produces outer join input
   * @param inner root of plan that produces inner join input
   * @param availPreds predicates to be applied to outer and inner tuples; join predicate would come
   *        from among these guys
   * @param ret the set of join plans that we're building up
   * @param outerRels set of "base" relations (from list items) inside each tuple of the outer
   * @param innerRels set of "base" relations (from list items) inside each tuple of the inner
   * @param joinRels precomputed union of outerRels and innerRels
   * @return true if a join plan is found
   * @throws ParseException
   */
  protected boolean generateHashJoin(PlanNode outer, PlanNode inner,
      TreeSet<PredicateNode> availPreds, ArrayList<PlanNode> ret,
      TreeSet<FromListItemNode> outerRels, TreeSet<FromListItemNode> innerRels,
      ArrayList<FromListItemNode> joinRels) throws ParseException {

    // Generate all joins that we can do with the predicate arguments in the
    // current order
    ArrayList<PredicateNode> preds = getHashJoinPreds(outerRels, innerRels, availPreds);

    addHashJoins(outer, inner, availPreds, ret, joinRels, preds, false);

    // Generate hash joins that we can do with the order of the predicate
    // arguments reversed.
    ArrayList<PredicateNode> reversePreds =
        getReversedHashJoinPreds(outerRels, innerRels, availPreds);

    addHashJoins(outer, inner, availPreds, ret, joinRels, reversePreds, true);

    if (0 == preds.size()) {
      // Didn't find a join
      return false;
    } else {
      return true;
    }
  }

  private void addHashJoins(PlanNode outer, PlanNode inner, TreeSet<PredicateNode> availPreds,
      ArrayList<PlanNode> ret, ArrayList<FromListItemNode> joinRels, ArrayList<PredicateNode> preds,
      boolean reverse) throws ParseException {
    for (PredicateNode joinpred : preds) {

      PredicateNode actualJoinPred = reverse ? joinpred.reverse(catalog) : joinpred;

      // Found a hash join predicate. Generate the join and any
      // post-filtering
      // predicates.
      JoinNode hashjoin = new HashJoinNode(outer, inner, actualJoinPred);

      // Don't want to apply the join predicate as a filter!
      TreeSet<PredicateNode> newAvailPreds = new TreeSet<PredicateNode>(availPreds);
      newAvailPreds.remove(joinpred);

      // Use a selection operator for postfiltering.
      ArrayList<PredicateNode> postFilters =
          MergePlanner.getFilterPreds(catalog, joinRels, newAvailPreds);

      if (postFilters.size() > 0) {
        ret.add(new SelectionNode(hashjoin, postFilters));
      } else {
        // No post-filtering necessary.
        ret.add(hashjoin);
      }
    }
  }

  protected ArrayList<PredicateNode> getHashJoinPreds(TreeSet<FromListItemNode> outerRels,
      TreeSet<FromListItemNode> innerRels, TreeSet<PredicateNode> availPreds)
      throws ParseException {

    ArrayList<PredicateNode> ret = new ArrayList<PredicateNode>();

    // Go through the list of available predicates and add all the ones that
    // work.
    for (PredicateNode node : availPreds) {
      if (node.coversHashJoin(outerRels, innerRels, catalog)) {
        ret.add(node);
      }
    }

    return ret;
  }

  /**
   * @param outerRels base relations covered in the outer of the join
   * @param innerRels base relations covered in the inner of the join
   * @param availPreds set of predicates available for use as a join predicate
   * @return a list of all predicates in availPreds that, if the order of their arguments was
   *         reversed, could be used as a hash join predicate
   * @throws ParseException
   */
  protected ArrayList<PredicateNode> getReversedHashJoinPreds(TreeSet<FromListItemNode> outerRels,
      TreeSet<FromListItemNode> innerRels, TreeSet<PredicateNode> availPreds)
      throws ParseException {

    ArrayList<PredicateNode> ret = new ArrayList<PredicateNode>();

    // Go through the list of available predicates and add all the ones that
    // work
    for (PredicateNode node : availPreds) {

      // Leverage all the code that was written for reversed merge join
      // predicates.
      PredicateNode reversed = node.reverse(catalog);

      if (null != reversed && reversed.coversHashJoin(outerRels, innerRels, catalog)) {
        // Note that we return the original node, not the reversed
        // version; the caller uses this node as a hash key.
        ret.add(node);
      }

    }

    return ret;
  }
}
