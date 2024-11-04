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

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.TreeSet;

import com.ibm.avatar.aql.FromListItemNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.PredicateNode;
import com.ibm.avatar.aql.catalog.Catalog;

/**
 * Node that represents a restricted span evaluation (RSE) join. Like merge joins, RSE joins are
 * asymmetric.
 * 
 */
public class RSEJoinNode extends JoinNode {
  /**
   * The original best plan for the inner operand of the join, prior to inlining. We keep this
   * pointer so that the optimizer can compute the set of FROM list items that this join covers.
   */
  private PlanNode origInner;

  /**
   * @param outer plan for generating the outer operand of the join
   * @param inner inner operand, after being inlined for use in RSE join
   * @param origInner inner operand, before inlining
   * @param joinpred the (single) join predicate used in the join
   */
  public RSEJoinNode(PlanNode outer, PlanNode inner, PlanNode origInner, PredicateNode joinpred) {
    super(outer, inner, JoinNode.JoinType.RSE, new ArrayList<PredicateNode>());
    preds.add(joinpred);
    this.origInner = origInner;
  }

  @Override
  public PlanNode deepCopyImpl() throws ParseException {
    // Note that we don't deep-copy origInner, which is immutable
    return new RSEJoinNode(outer().deepCopy(), inner().deepCopy(), origInner, preds.get(0));
  }

  @Override
  public void toAOGNoRename(PrintWriter stream, int indent, Catalog catalog) throws Exception {

    printIndent(stream, indent);
    stream.print("RSEJoin(\n");

    // Generate the join predicate's AOG code.
    PredicateNode joinpred = preds.get(0);
    joinpred.getFunc().toAOG(stream, indent + 1, catalog);
    stream.print(",\n");

    // Second argument is the outer.
    outer().toAOG(stream, indent + 1, catalog);
    stream.print(",\n");

    // Third argument is the inner.
    inner().toAOG(stream, indent + 1, catalog);
    stream.print("\n");

    // Close parens for NLJoin operator.
    printIndent(stream, indent);
    stream.print(")");

  }

  /**
   * The inner of an RSE join node has been inlined, so the superclass's implementation of this
   * function doesn't work. This implementation pulls information on what relation (i.e. FROM list
   * item) the inner represents from a local field in the current object.
   */
  @Override
  public void getRels(TreeSet<FromListItemNode> rels) {
    outer().getRels(rels);

    // Note the use of origInner -- the inner operand of the join prior to being inlined for RSE
    origInner.getRels(rels);
  }
}
