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

/**
 * Base class for optimizer modules that perform various "rewrite" transformations on compiled query
 * plans; for example, Shared Dictionary Matching.
 * 
 */
public class PlanRewriter {
  /**
   * Callback for iterating over a plan, performing rewrites.
   */
  protected static interface callback {

    /**
     * The portion of the callback that transforms an individual plan node.
     * 
     * @param p a node of the plan
     * @param parents plan nodes above this one (up to the root of the containing view), in order
     *        from closest to furthest
     * @return a rewritten version of p
     */
    PlanNode exec(PlanNode p, ArrayList<PlanNode> parents);

    /**
     * @return an aggregate computed during the pass over the plan.
     */
    Object getResult();
  }

  protected static final boolean DEBUG_ITERATE = false;

  /**
   * Iterate over a set of plans, applying the callback to each node.
   * 
   * @param plans what to iterate over (recursively)
   * @param cb callback; we'll call {@code cb.exec()} for every node we encounter
   */
  protected static void iterate(ArrayList<PlanNode> plans, callback cb) {

    if (DEBUG_ITERATE) {
      System.err.printf("Iterating over %d plans...\n", plans.size());
    }

    // Create a stack
    ArrayList<PlanNode> stack = new ArrayList<PlanNode>();

    for (int i = 0; i < plans.size(); i++) {
      // Process each root in turn.
      plans.set(i, reallyIterate(plans.get(i), cb, stack));
    }
  }

  /**
   * Recursive implementation of iterate().
   * 
   * @param node the current node in the recursive traversal
   * @param cb callback object
   * @param stack stack of nodes above the current node in the graph, going up to the root of the
   *        current view's subplan
   */
  protected static PlanNode reallyIterate(PlanNode node, callback cb, ArrayList<PlanNode> stack) {
    // Add the parent to the stack.
    stack.add(node);

    // Iterate over the children.
    PlanNode[] children = node.getChildren();
    for (int i = 0; i < children.length; i++) {
      PlanNode origChild = children[i];
      children[i] = reallyIterate(origChild, cb, stack);
    }

    // Pop the parent off the stack, then rewrite the parent.
    stack.remove(stack.size() - 1);
    PlanNode rewritten = cb.exec(node, stack);
    if (DEBUG_ITERATE && node != rewritten) {
      System.err.printf("Rewrote %s to %s\n", node, rewritten);
    }
    return rewritten;
  }
}
