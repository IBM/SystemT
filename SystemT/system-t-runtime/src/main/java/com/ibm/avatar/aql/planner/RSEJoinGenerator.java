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

import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.aql.ExtractionNode;
import com.ibm.avatar.aql.FromListItemNode;
import com.ibm.avatar.aql.FromListItemSubqueryNode;
import com.ibm.avatar.aql.FromListItemTableFuncNode;
import com.ibm.avatar.aql.FromListItemViewRefNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.PredicateNode;
import com.ibm.avatar.aql.RegexExNode;
import com.ibm.avatar.logging.Log;

public class RSEJoinGenerator extends MergeJoinGenerator {

  private static boolean DEBUG = false;

  @Override
  public ArrayList<PlanNode> createJoinPlans(PlanNode outer, PlanNode inner,
      TreeSet<PredicateNode> availPreds) throws ParseException {
    if (DEBUG) {
      Log.debug("RSEJoinGenerator.createJoinPlans() invoked on preds %s", availPreds);
    }
    // Similar to the superclass's implementation, with additional hooks to
    // check for applicability of RSE join.
    ArrayList<PlanNode> ret = new ArrayList<PlanNode>();

    TreeSet<FromListItemNode> outerRels = new TreeSet<FromListItemNode>();
    outer.getRels(outerRels);

    TreeSet<FromListItemNode> innerRels = new TreeSet<FromListItemNode>();
    inner.getRels(innerRels);

    ArrayList<FromListItemNode> joinRels = new ArrayList<FromListItemNode>();
    joinRels.addAll(outerRels);
    joinRels.addAll(innerRels);

    if (DEBUG) {
      Log.debug("RSEJoinGenerator.createJoinPlans():");
      Log.debug("    Outer rels: %s\n" + "    Inner rels: %s\n" + "    Avail preds: %s", outerRels,
          innerRels, availPreds);
    }

    // Attempt to generate a plan that uses RSE join.
    generateRSEJoin(outer, inner, availPreds, ret, outerRels, innerRels, joinRels);

    // Attempt to generate a merge join plan. Note that we do this step
    // regardless of whether we found an RSE join.
    generateMergeJoin(outer, inner, availPreds, ret, outerRels, innerRels, joinRels);

    // Other option is to generate a nested-loops join.
    generateNLJoin(outer, inner, availPreds, ret, joinRels);

    return ret;
  }

  /**
   * RSE join plans for the root of the indicated join tree, if they exist.
   * 
   * @return true if this method was able to generate an RSE plan, false otherwise
   */
  protected boolean generateRSEJoin(PlanNode outer, PlanNode inner,
      TreeSet<PredicateNode> availPreds, ArrayList<PlanNode> ret,
      TreeSet<FromListItemNode> outerRels, TreeSet<FromListItemNode> innerRels,
      ArrayList<FromListItemNode> joinRels) throws ParseException {
    if (DEBUG) {
      Log.debug(
          "RSEJoinGenerator.generateRSEJoin() called with:\n" + "    outer:\n%s\n"
              + "    inner:\n%s\n" + "    outer relations: %s\n" + "    inner relations: %s\n"
              + "    join relations: %s\n" + "    predicates: %s",
          outer.dumpToString(2), inner.dumpToString(2), outerRels, innerRels, joinRels, availPreds);
    }

    // Before we do anything, check to see whether the current
    // implementation of the RSE join operator would accept the inner as its
    // second argument.
    PlanNode rseInner = convertToRSEInner(inner);
    if (null == rseInner) {
      if (DEBUG) {
        Log.debug("    generateRSEJoin(): inner not RSE-compatible");
      }
      return false;
    }

    boolean foundRSEJoin = false;

    // Try reversing the order of arguments to potential join
    // predicates; RSE join is asymmetric.
    ArrayList<PredicateNode> rsePreds = getRSEPreds(outerRels, innerRels, availPreds);
    ArrayList<PredicateNode> reversedPreds = getReversedRSEPreds(outerRels, innerRels, availPreds);

    for (PredicateNode pred : rsePreds) {
      if (DEBUG) {
        Log.debug("    generateRSEJoin(): generating plan for pred %s", pred);
      }

      boolean reversed = false;
      foundRSEJoin = true;
      createRSEJoin(outer, rseInner, inner, availPreds, joinRels, pred, reversed, ret);
    }

    for (PredicateNode pred : reversedPreds) {
      if (DEBUG) {
        Log.debug("    generateRSEJoin(): generating plan for reversed pred %s", pred);
      }

      boolean reversed = true;
      foundRSEJoin = true;
      createRSEJoin(outer, rseInner, inner, availPreds, joinRels, pred, reversed, ret);
    }

    return foundRSEJoin;
  }

  /**
   * Convert the inner operand of a join into a tree of operators that is compatible with RSEJoin,
   * if possible.
   * 
   * @param inner potential inner operand of a join
   * @return a version of the indicated operator tree that is suitable to use as the inner argument
   *         to RSEJoin. In particular, any operators that could be shared with another subplan are
   *         deep-copied before being returned. Returns null if the argument cannot be the inner
   *         operand of an RSE join
   */
  private PlanNode convertToRSEInner(PlanNode arg) throws ParseException {
    if (DEBUG) {
      Log.debug("convertToRSEInner() called on %s", arg);
    }

    // Start by drilling down past any reference to another plan subtree. Note that we don't
    // currently bother trying to
    // drill through multiple renamings of the same view.
    if (arg instanceof ScanNode) {
      // The argument node may actually be a scan that's just a pointer to a particular view's
      // output
      ScanNode scanNode = (ScanNode) arg;
      FromListItemNode target = scanNode.getWhatToScan();

      if (DEBUG) {
        Log.debug("convertToRSEInner() is digging into a scan; target of scan is '%s' (%s)",
            target.dumpToStr(0), target.getClass().getSimpleName());
      }

      if (target instanceof FromListItemSubqueryNode) {
        // There should be no subqueries by the time this point in compilation happens.
        throw new FatalInternalError(
            "Target of scan %s is a subquery that has not been converted to a generated view; plan is %s",
            arg, arg.dumpToString(0));
      } else if (target instanceof FromListItemTableFuncNode) {
        // No RSE implementations of table functions
        return null;
      } else if (target instanceof FromListItemViewRefNode) {
        // Reference to a view
        FromListItemViewRefNode viewRef = (FromListItemViewRefNode) target;
        String targetName = viewRef.getViewName().getNickname();

        // First check if it's a view imported from another module; those aren't eligible for RSE,
        // since we can't inline
        // the view definition
        if (catalog.isImportedView(targetName) || catalog.isImportedTable(targetName)) {
          return null;
        }

        PlanNode targetPlan = catalog.getCachedPlan(targetName);

        if (null == targetPlan) {
          throw new FatalInternalError("No plan for %s, referenced from:\n%s", targetPlan,
              arg.dumpToString(0));
        }

        if (false == targetPlan instanceof ViewNode) {
          // If the plan object is not a ViewNode, it's one of the other types of stubs -- table,
          // external view, etc. --
          // none of which is RSE-compatible
          return null;
        }

        // The view should have a plan of type ViewNode. Dig down into the subplan root.
        ViewNode vn = (ViewNode) targetPlan;
        PlanNode bodyPlan = vn.getBodyPlan();

        if (DEBUG) {
          Log.debug("After view removal, potential inner argument is '%s' (%s)",
              bodyPlan.dumpToString(0), bodyPlan.getClass().getSimpleName());
        }

        if (canBeRSEInner(bodyPlan)) {
          // Create a copy, since the view body may be invoked in a non-RSE manner from elsewhere in
          // the AQL
          PlanNode ret = bodyPlan.deepCopy();
          ret.composeRenamings(arg.renamings);

          return ret;
        } else {
          return null;
        }

      } else {
        throw new FatalInternalError("Unrecognized from list item node type %s",
            target.getClass().getName());
      }

    } else {
      // arg is not a ScanNode; just check whether it's a valid RSE inner.
      if (canBeRSEInner(arg)) {
        // Make sure that we return a copy, since RSE join invokes the inner over and over and can't
        // share its inner
        // operand with other operators
        return arg.deepCopy();
      } else {
        return null;
      }
    }
  }

  /**
   * @param inner root of the potential inner input to a join
   * @return true if the current implementation of RSE join will work with the indicated plan tree
   *         as its inner argument.
   */
  private boolean canBeRSEInner(PlanNode inner) throws ParseException {
    if (DEBUG) {
      Log.debug("Checking whether the following can be the inner of an RSE join:\n%s",
          inner.dumpToString(2));
    }

    // inner could have one or more projections at the root. Drill past them
    PlanNode topNonProjectNode = inner;
    while (topNonProjectNode instanceof ProjectionNode) {
      topNonProjectNode = topNonProjectNode.child();
    }

    // Currently, the only operator that can be the inner of an RSE join is
    // RegexTok.

    // RegexTok extraction is represented as an ExtractPlanNode with the appropriate extraction
    // parameters.

    // First rule out inners that are not extractions
    if (false == (topNonProjectNode instanceof ExtractPlanNode)) {
      return false;
    }

    ExtractPlanNode epn = (ExtractPlanNode) topNonProjectNode;
    ExtractionNode extract = epn.getExtractList().getExtractSpec();

    // Then rule out inners that are not regexes
    if (false == (extract instanceof RegexExNode)) {
      return false;
    }

    RegexExNode regexExtract = (RegexExNode) extract;

    // Then check whether the extraction is on token boundaries
    if (regexExtract.getUseRegexTok()) {
      return true;
    } else {
      // Not on token boundaries
      return false;
    }
  }

  private ArrayList<PredicateNode> getRSEPreds(TreeSet<FromListItemNode> outerRels,
      TreeSet<FromListItemNode> innerRels, TreeSet<PredicateNode> availPreds)
      throws ParseException {
    ArrayList<PredicateNode> ret = new ArrayList<PredicateNode>();

    if (DEBUG) {
      Log.debug("RSEJoinGenerator.getRSEPreds() called on preds %s", availPreds);
    }

    // Go through the list of available predicates and add all the ones that
    // work.
    for (PredicateNode node : availPreds) {
      if (node.coversRSEJoin(outerRels, innerRels, catalog)) {
        ret.add(node);
      }
    }

    return ret;
  }

  private ArrayList<PredicateNode> getReversedRSEPreds(TreeSet<FromListItemNode> outerRels,
      TreeSet<FromListItemNode> innerRels, TreeSet<PredicateNode> availPreds)
      throws ParseException {
    ArrayList<PredicateNode> ret = new ArrayList<PredicateNode>();

    // Go through the list of available predicates and add all the ones that
    // work
    for (PredicateNode node : availPreds) {

      PredicateNode reversed = node.reverse(catalog);

      if (null != reversed && reversed.coversRSEJoin(outerRels, innerRels, catalog)) {
        // Note that we return the original node, not the reversed
        // version; the caller uses this node as a hash key.
        ret.add(node);
      }

    }

    return ret;
  }

  /**
   * Subroutine that builds the plan node for RSE join
   * 
   * @param outer outer (left) operand of the join
   * @param inlinedInner inner (right) operand of the join, after making a private copy of the
   *        subplan for any views contained within (RSE join cannot share child operators with other
   *        operators)
   * @param origInner original inner operand prior to inlining
   * @param availPreds all predicates that haven't yet been applied to the outer
   * @param joinRels list of all relations that participate in either side of the join
   * @param mergePred the join predicate
   * @param reversePred true if the predicate needs to be replaced by its "reversed" equivalent (for
   *        example FollowsTok --> FollowedByTok)
   * @param ret output -- new join plan goes in here
   * @throws ParseException
   */
  protected void createRSEJoin(PlanNode outer, PlanNode inlinedInner, PlanNode origInner,
      TreeSet<PredicateNode> availPreds, ArrayList<FromListItemNode> joinRels,
      PredicateNode mergePred, boolean reversePred, ArrayList<PlanNode> ret) throws ParseException {
    // Found a merge join predicate. Generate the join and any post-filtering predicates.
    // Reverse the order of arguments to the join predicate if necessary.
    PredicateNode realMergePred = reversePred ? mergePred.reverse(catalog) : mergePred;

    JoinNode mergejoin = new RSEJoinNode(outer, inlinedInner, origInner, realMergePred);
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
}
