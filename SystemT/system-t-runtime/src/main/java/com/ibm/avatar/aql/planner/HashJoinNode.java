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

import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.PredicateNode;
import com.ibm.avatar.aql.catalog.Catalog;

/**
 * Node that represents a hash join. Unlike merge join, hash join's predicates don't care which
 * input is the inner and which is the outer.
 * 
 */
public class HashJoinNode extends JoinNode {

  /**
   * @param outer plan for generating the outer operand of the join
   * @param inner inner operand
   * @param joinpred the (single) join predicate used in the join
   */
  public HashJoinNode(PlanNode outer, PlanNode inner, PredicateNode joinpred) {
    super(outer, inner, JoinNode.JoinType.HASH, new ArrayList<PredicateNode>());
    preds.add(joinpred);
  }

  @Override
  public PlanNode deepCopyImpl() throws ParseException {
    return new HashJoinNode(outer().deepCopy(), inner().deepCopy(), preds.get(0));
  }

  @Override
  public void toAOGNoRename(PrintWriter stream, int indent, Catalog catalog) throws Exception {

    printIndent(stream, indent);
    stream.printf("%s(\n", getAOGOpName());

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
   * @return name (in AOG) of the operator that implements this join
   */
  protected String getAOGOpName() {
    return "HashJoin";
  }

}
