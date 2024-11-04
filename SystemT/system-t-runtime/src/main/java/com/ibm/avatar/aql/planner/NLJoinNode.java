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
 * Node that represents a nested-loops join.
 * 
 */
public class NLJoinNode extends JoinNode {

  public NLJoinNode(PlanNode outer, PlanNode inner, ArrayList<PredicateNode> joinpreds) {
    super(outer, inner, JoinNode.JoinType.NESTED_LOOPS, joinpreds);
  }

  @Override
  public PlanNode deepCopyImpl() throws ParseException {
    return new NLJoinNode(outer().deepCopy(), inner().deepCopy(), preds);
  }

  @Override
  public void toAOGNoRename(PrintWriter stream, int indent, Catalog catalog) throws Exception {

    printIndent(stream, indent);
    stream.print("NLJoin(\n");

    // First argument of nested-loops join is the join predicate.
    SelectionNode.genAOGForPreds(stream, indent, preds, catalog);
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

}
