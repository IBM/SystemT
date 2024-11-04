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
import com.ibm.avatar.aql.PredicateNode;

/**
 * Node that represents a join operator in a logical plan. Includes information about the join type
 * and the operands of the join, as well as the join predicate
 * 
 */
public abstract class JoinNode extends PlanNode {

  public enum JoinType {
    NESTED_LOOPS("Nested Loops Join"), MERGE("Merge Join"), RSE(
        "Restricted Span Evaluation Join"), HASH("Hash Join");

    String name;

    JoinType(String name) {
      this.name = name;
    }
  }

  // Indexes of child operators.
  private static final int OUTER_IX = 0;
  private static final int INNER_IX = 1;

  /** Outer (left) operand of the join. */
  protected PlanNode outer() {
    return getChildren()[OUTER_IX];
  }

  /** Inner (right) operand of the join. */
  protected PlanNode inner() {
    return getChildren()[INNER_IX];
  }

  /** Join algorithm used to peform the join. */
  private JoinType type;

  /** Predicate(s) applied inside the join operator. */
  protected ArrayList<PredicateNode> preds;

  public JoinNode(PlanNode outer, PlanNode inner, JoinType jointype,
      ArrayList<PredicateNode> joinpreds) {
    super(new PlanNode[] {outer, inner});
    this.type = jointype;
    this.preds = joinpreds;
  }

  @Override
  public void dump(PrintWriter stream, int indent) {

    printIndent(stream, indent);
    stream.printf("JoinNode for %s\n", type.name);
    printIndent(stream, indent + 1);
    stream.printf("Preds: %s\n", preds);

    printIndent(stream, indent + 1);
    stream.print("Outer:\n");
    outer().dump(stream, indent + 2);

    printIndent(stream, indent + 1);
    stream.print("Inner:\n");
    inner().dump(stream, indent + 2);

  }

  @Override
  public void getPreds(TreeSet<PredicateNode> preds) {
    preds.addAll(this.preds);
    outer().getPreds(preds);
    inner().getPreds(preds);
  }

  @Override
  public void getRels(TreeSet<FromListItemNode> rels) {
    outer().getRels(rels);
    inner().getRels(rels);
  }
}
