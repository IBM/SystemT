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
/**
 * 
 */
package com.ibm.avatar.aql.planner;

import java.util.ArrayList;
import java.util.TreeSet;

import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.algebra.function.predicate.FollowedByTok;
import com.ibm.avatar.algebra.function.predicate.FollowsTok;
import com.ibm.avatar.aql.FromListItemNode;
import com.ibm.avatar.aql.IntNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.PredicateNode;
import com.ibm.avatar.aql.RValueNode;
import com.ibm.avatar.logging.Log;

public class MergeJoinGenerator extends JoinGenerator {

  /**
   * Set this flag to TRUE to generate a specialized join operator for FollowsTok() predicates with
   * small token distances.
   */
  public static final boolean GENERATE_ADJACENT_JOIN = true;

  /**
   * Maximum token distance for which we will generate an AdjacentJoin operator instead of
   * SortMergeJoin
   */
  public static final int ADJACENT_JOIN_MAX_TOKS = 5;

  @Override
  public ArrayList<PlanNode> createJoinPlans(PlanNode outer, PlanNode inner,
      TreeSet<PredicateNode> availPreds) throws ParseException {

    ArrayList<PlanNode> ret = new ArrayList<PlanNode>();

    // Similar to the code in NaiveMergePlanner.

    TreeSet<FromListItemNode> outerRels = new TreeSet<FromListItemNode>();
    outer.getRels(outerRels);

    TreeSet<FromListItemNode> innerRels = new TreeSet<FromListItemNode>();
    inner.getRels(innerRels);

    ArrayList<FromListItemNode> joinRels = new ArrayList<FromListItemNode>();
    joinRels.addAll(outerRels);
    joinRels.addAll(innerRels);

    // System.err.printf("Outer rels: %s\n" + "Inner rels: %s\n"
    // + "Avail preds: %s\n", outerRels, innerRels, availPreds);

    // Figure out whether we can use merge join for this join.
    boolean foundMergeJoin =
        generateMergeJoin(outer, inner, availPreds, ret, outerRels, innerRels, joinRels);

    if (false == foundMergeJoin) {
      // Didn't find a suitable predicate for merge join; fall back on
      // nested loops join.
      generateNLJoin(outer, inner, availPreds, ret, joinRels);
    }

    return ret;
  }

  protected boolean generateMergeJoin(PlanNode outer, PlanNode inner,
      TreeSet<PredicateNode> availPreds, ArrayList<PlanNode> ret,
      TreeSet<FromListItemNode> outerRels, TreeSet<FromListItemNode> innerRels,
      ArrayList<FromListItemNode> joinRels) throws ParseException {

    final boolean debug = false;

    boolean foundMergeJoin = false;
    ArrayList<PredicateNode> forwardPreds = getMergeJoinPreds(outerRels, innerRels, availPreds);

    for (PredicateNode pred : forwardPreds) {
      boolean isReversedPred = false;
      foundMergeJoin = true;
      createMergeJoin(outer, inner, availPreds, ret, joinRels, pred, isReversedPred);
    }

    // Try reversing the order of arguments to potential join
    // predicates; merge join is asymmetric.
    ArrayList<PredicateNode> reversedPreds =
        getReversedMergePreds(outerRels, innerRels, availPreds);
    for (PredicateNode pred : reversedPreds) {
      boolean isReversedPred = true;
      foundMergeJoin = true;
      createMergeJoin(outer, inner, availPreds, ret, joinRels, pred, isReversedPred);
    }

    if (debug) {
      Log.debug("    generateMergeJoin():");
      Log.debug("        Available predicates are: %s", availPreds);
      Log.debug("        Merge join forward preds are: %s", forwardPreds);
      Log.debug("        Merge join reverse preds are: %s", reversedPreds);
    }

    return foundMergeJoin;
  }

  protected void generateNLJoin(PlanNode outer, PlanNode inner, TreeSet<PredicateNode> availPreds,
      ArrayList<PlanNode> ret, ArrayList<FromListItemNode> joinRels) throws ParseException {
    // Nested-loops join can apply all filtering predicates as join
    // predicates, so there's no need for a post-filtering pass.
    ArrayList<PredicateNode> joinpreds = MergePlanner.getFilterPreds(catalog, joinRels, availPreds);
    // availPreds.removeAll(joinpreds);
    ret.add(new NLJoinNode(outer, inner, joinpreds));
  }

  private void createMergeJoin(PlanNode outer, PlanNode inner, TreeSet<PredicateNode> availPreds,
      ArrayList<PlanNode> ret, ArrayList<FromListItemNode> joinRels, PredicateNode mergePred,
      boolean reversePred) throws ParseException {
    // Found a merge join predicate. Generate the join and any
    // post-filtering predicates.
    // Reverse the order of arguments to the join predicate if
    // necessary.
    PredicateNode realMergePred = reversePred ? mergePred.reverse(catalog) : mergePred;

    JoinNode mergejoin;
    if (GENERATE_ADJACENT_JOIN && isAdjJoinCompat(realMergePred)) {
      mergejoin = new AdjacentJoinNode(outer, inner, realMergePred);
    } else {
      mergejoin = new MergeJoinNode(outer, inner, realMergePred);
    }
    // availPreds.remove(mergePred);

    // Don't want to apply the join predicate as a filter!
    TreeSet<PredicateNode> newAvailPreds = new TreeSet<PredicateNode>(availPreds);
    newAvailPreds.remove(mergePred);

    // Use a selection operator for postfiltering.
    ArrayList<PredicateNode> postFilters =
        MergePlanner.getFilterPreds(catalog, joinRels, newAvailPreds);

    if (postFilters.size() > 0) {
      // availPreds.removeAll(postFilters);
      ret.add(new SelectionNode(mergejoin, postFilters));
    } else {
      // No post-filtering necessary.
      ret.add(mergejoin);
    }
  }

  /**
   * @param mergePred a merge join predicate
   * @return true if this join predicate can be evaluated with AdjacentJoin, a specialized merge
   *         join for adjacency (e.g. FollowsTok() with a token distance of zero) predicates
   */
  private boolean isAdjJoinCompat(PredicateNode mergePred) {
    final boolean debug = false;

    String fname = mergePred.getFunc().getFuncName();
    if (false == ScalarFunc.computeFuncName(FollowsTok.class).equals(fname)
        && false == ScalarFunc.computeFuncName(FollowedByTok.class).equals(fname)) {
      // AdjacentJoin only works for FollowsTok and FollowedByTok
      return false;
    }

    // If we get here, mergePred is either a FollowsTok or a FollowedByTok
    // predicate. Make sure that the token distances specified in the
    // predicate are both zero.
    ArrayList<RValueNode> args = mergePred.getFunc().getArgs();

    RValueNode minTokDistRVal = args.get(2);
    RValueNode maxTokDistRVal = args.get(3);

    if (minTokDistRVal instanceof IntNode && maxTokDistRVal instanceof IntNode) {
      // IntNode minTokDist = (IntNode) minTokDistRVal;
      IntNode maxTokDist = (IntNode) maxTokDistRVal;

      if (maxTokDist.getValue() <= ADJACENT_JOIN_MAX_TOKS) {
        if (debug) {
          Log.debug("Predicate is AdjacentJoin-compatible");
        }

        return true;
      }
    }

    return false;
  }

  protected ArrayList<PredicateNode> getMergeJoinPreds(TreeSet<FromListItemNode> outerRels,
      TreeSet<FromListItemNode> innerRels, TreeSet<PredicateNode> availPreds)
      throws ParseException {

    ArrayList<PredicateNode> ret = new ArrayList<PredicateNode>();

    // Go through the list of available predicates and add all the ones that
    // work.
    for (PredicateNode node : availPreds) {
      if (node.coversMergeJoin(outerRels, innerRels, catalog)) {
        ret.add(node);
      }
    }

    return ret;
  }

  protected ArrayList<PredicateNode> getReversedMergePreds(TreeSet<FromListItemNode> outerRels,
      TreeSet<FromListItemNode> innerRels, TreeSet<PredicateNode> availPreds)
      throws ParseException {

    ArrayList<PredicateNode> ret = new ArrayList<PredicateNode>();

    // Go through the list of available predicates and add all the ones that
    // work
    for (PredicateNode node : availPreds) {

      PredicateNode reversed = node.reverse(catalog);

      if (null != reversed && reversed.coversMergeJoin(outerRels, innerRels, catalog)) {
        // Note that we return the original node, not the reversed
        // version; the caller uses this node as a hash key.
        ret.add(node);
      }

    }

    return ret;
  }

}
