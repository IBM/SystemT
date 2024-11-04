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

/**
 * Implementation of the AQL query planner that is a step above {@link NaivePlanner} in
 * sophistication. Like NaivePlanner, this class generates plans that join the elements of a SELECT
 * statement in the order they appear in the FROM list and pushes down predicates as far as they
 * will go. Unlike NaivePlanner, this class uses merge join instead of nested-loops join wherever
 * possible.
 * 
 */
public class NaiveMergePlanner extends NaivePlanner {

  /**
   * Recursive function that does the heavy lifting of "optimizing" a select statement. Unlike the
   * version in the parent class, this method uses merge join whenever possible.
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
  @Override
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

      ArrayList<String> colsNeeded =
          getNeededCols(catalog, target, availPreds, selectList, groupByValues, consolidateClause);

      ScanNode scan = new ScanNode(target, colsNeeded, catalog);

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

    ArrayList<String> colsNeeded =
        getNeededCols(catalog, innerRel, availPreds, selectList, groupByValues, consolidateClause);

    ScanNode innerScan = new ScanNode(innerRel, colsNeeded, catalog);
    ArrayList<PredicateNode> innerFilters = getFilterPreds(innerRel, availPreds);
    PlanNode inner;
    if (innerFilters.size() > 0) {
      availPreds.removeAll(innerFilters);
      inner = new SelectionNode(innerScan, innerFilters);
    } else {
      inner = innerScan;
    }

    // Generate the join.

    // Start by generating sets of relations to serve as inputs to
    // predicate-choosing functions.
    TreeSet<FromListItemNode> outerRels = new TreeSet<FromListItemNode>();
    for (int i = 0; i < offset; i++) {
      outerRels.add(fromList.get(i));
    }
    ArrayList<FromListItemNode> joinRels = new ArrayList<FromListItemNode>(outerRels);
    joinRels.add(innerRel);

    // Figure out whether we can use merge join for this join. If necessary,
    // try reversing the order of arguments to potential join predicates.
    PredicateNode mergePred = getMergeJoinPred(outerRels, innerRel, availPreds);
    boolean reversePred = false;
    if (null == mergePred) {

      mergePred = getReversedMergePred(outerRels, innerRel, availPreds);
      if (null != mergePred) {
        reversePred = true;
      }
    }

    if (null != mergePred) {
      // Found a merge join predicate. Generate the join and any
      // post-filtering predicates.
      // Reverse the order of arguments to the join predicate if
      // necessary.
      PredicateNode realMergePred = reversePred ? mergePred.reverse(catalog) : mergePred;

      JoinNode mergejoin = new MergeJoinNode(outer, inner, realMergePred);
      availPreds.remove(mergePred);

      // Use a selection operator for postfiltering.
      ArrayList<PredicateNode> postFilters = getFilterPreds(catalog, joinRels, availPreds);

      if (postFilters.size() > 0) {
        availPreds.removeAll(postFilters);
        return new SelectionNode(mergejoin, postFilters);
      } else {
        // No post-filtering necessary.
        return mergejoin;
      }

    } else {
      // Didn't find a suitable predicate for merge join; fall back on
      // nested loops join.

      // Nested-loops join can apply all filtering predicates as join
      // predicates, so there's no need for a post-filtering pass.
      ArrayList<PredicateNode> joinpreds = getFilterPreds(catalog, joinRels, availPreds);
      availPreds.removeAll(joinpreds);
      return new NLJoinNode(outer, inner, joinpreds);
    }

  }

  private PredicateNode getMergeJoinPred(TreeSet<FromListItemNode> outerRels,
      FromListItemNode innerRel, TreeSet<PredicateNode> availPreds) throws ParseException {

    TreeSet<FromListItemNode> innerRels = new TreeSet<FromListItemNode>();
    innerRels.add(innerRel);

    // Go through the list of available predicates and choose the first one
    // that works.
    for (PredicateNode node : availPreds) {

      if (node.coversMergeJoin(outerRels, innerRels, catalog)) {
        return node;
      }

    }

    // If we get here, we found no suitable join predicates.
    return null;
  }

  protected PredicateNode getReversedMergePred(TreeSet<FromListItemNode> outerRels,
      FromListItemNode innerRel, TreeSet<PredicateNode> availPreds) throws ParseException {

    TreeSet<FromListItemNode> innerRels = new TreeSet<FromListItemNode>();
    innerRels.add(innerRel);

    // Go through the list of available predicates and choose the first one
    // that works.
    for (PredicateNode node : availPreds) {

      PredicateNode reversed = node.reverse(catalog);

      if (null != reversed && reversed.coversMergeJoin(outerRels, innerRels, catalog)) {
        // Note that we return the original node; the caller uses this
        // node as a hash key.
        return node;
      }

    }

    // If we get here, we found no suitable join predicates.
    return null;
  }
}
