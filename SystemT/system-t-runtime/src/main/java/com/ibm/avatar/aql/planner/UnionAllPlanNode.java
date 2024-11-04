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
 * Query plan with a Union operator at the top.
 * 
 */
public class UnionAllPlanNode extends PlanNode {

  public UnionAllPlanNode(ArrayList<PlanNode> stuffToUnionList) {
    super(stuffToUnionList);
  }

  @Override
  public PlanNode deepCopyImpl() throws ParseException {
    // Deep-copy child plans first
    ArrayList<PlanNode> tmpList = new ArrayList<PlanNode>();

    for (PlanNode planNode : getChildren()) {
      tmpList.add(planNode.deepCopy());
    }

    return new UnionAllPlanNode(tmpList);
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    printIndent(stream, indent);
    stream.printf("UnionAllPlanNode\n");

    for (int i = 0; i < getChildren().length; i++) {
      printIndent(stream, indent + 1);
      stream.printf("Plan %d:\n", i);
      getChildren()[i].dump(stream, indent + 2);
      if (i < getChildren().length - 1) {
        stream.print("\n");
      }
    }
  }

  @Override
  public void toAOGNoRename(PrintWriter stream, int indent, Catalog catalog) throws Exception {
    printIndent(stream, indent);
    stream.print("Union(\n");
    for (int i = 0; i < getChildren().length; i++) {
      getChildren()[i].toAOG(stream, indent + 1, catalog);

      if (i < getChildren().length - 1) {
        stream.print(",\n");
      } else {
        stream.print("\n");
      }
    }

    // Close the parens on Union()
    printIndent(stream, indent);
    stream.print(")");
  }

  @Override
  public void getPreds(TreeSet<PredicateNode> preds) {
    for (PlanNode child : getChildren()) {
      child.getPreds(preds);
    }
  }

  @Override
  public void getRels(TreeSet<FromListItemNode> rels) {
    for (PlanNode child : getChildren()) {
      child.getRels(rels);
    }
  }

}
