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
import java.util.TreeSet;

import com.ibm.avatar.aql.FromListItemNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.PredicateNode;
import com.ibm.avatar.aql.RequireColumnsNode;
import com.ibm.avatar.aql.catalog.Catalog;

/**
 * Plan node for the required document input schema. Schema is the union of all require statements
 * 
 */
public class RequireDocumentPlanNode extends PlanNode {

  // this node is assumed to have a unionized schema
  RequireColumnsNode node;

  public RequireDocumentPlanNode(RequireColumnsNode node) {
    super(new PlanNode[] {});

    this.node = node;
  }

  @Override
  public PlanNode deepCopyImpl() throws ParseException {
    return new RequireDocumentPlanNode(node);
  }

  public RequireColumnsNode getSchemaNode() {
    return node;
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    printIndent(stream, indent);
    stream.printf("RequireDocumentPlanNode\n");
    stream.printf("Unionized doc schema:\n");
    for (int i = 0; i < node.getColNames().size(); i++) {
      printIndent(stream, indent + 2);
      stream.printf("\"%s\" => \"%s\"", node.getColNames().get(i).getNickname(),
          node.getColTypes().get(i).getNickname());
      if (i == node.getColNames().size() - 1) {
        stream.print("\n");
      } else {
        stream.print(",\n");
      }
    }
    stream.flush();
  }

  // @Override
  // public void toAOG(PrintWriter stream, int indent) throws Exception {
  // node.toAOG(stream, indent);
  // }

  @Override
  public void toAOGNoRename(PrintWriter stream, int indent, Catalog catalog) throws Exception {
    // generate the DocScan AOG statement.

    printIndent(stream, indent);
    stream.printf("$Document = DocScan(\n");

    // then schema
    printIndent(stream, indent + 1);
    stream.print("(\n");

    for (int i = 0; i < node.getColNames().size(); i++) {
      printIndent(stream, indent + 2);
      stream.printf("\"%s\" => \"%s\"", node.getColNames().get(i).getNickname(),
          node.getColTypes().get(i).getNickname());
      if (i == node.getColNames().size() - 1) {
        stream.print("\n");
      } else {
        stream.print(",\n");
      }
    }

    // Close parenthesis on schema
    printIndent(stream, indent + 1);
    stream.print(")\n");

    // Close parenthesis on DocScan
    printIndent(stream, indent);
    stream.printf(");\n");

  }

  @Override
  public void getPreds(TreeSet<PredicateNode> preds) {
    throw new RuntimeException("Should never be called");
  }

  @Override
  public void getRels(TreeSet<FromListItemNode> rels) {
    throw new RuntimeException("Should never be called");
  }

}
