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

import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.aql.ConsolidateClauseNode;
import com.ibm.avatar.aql.FromListItemNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.PredicateNode;
import com.ibm.avatar.aql.RValueNode;
import com.ibm.avatar.aql.catalog.Catalog;

/**
 * New plan node for consolidation. Will soon replace the older table function representation.
 */
public class ConsolidatePlanNode extends PlanNode {

  /** Parse tree node for the original consolidation. */
  private ConsolidateClauseNode consolidateClause;

  public ConsolidatePlanNode(ConsolidateClauseNode consolidateClause, PlanNode input) {
    super(input);
    this.consolidateClause = consolidateClause;
  }

  @Override
  public PlanNode deepCopyImpl() throws ParseException {
    return new ConsolidatePlanNode(consolidateClause, this.child().deepCopy());
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    printIndent(stream, indent);
    stream.printf("ConsolidatePlanNode\n");
    printIndent(stream, indent + 1);
    stream.printf("Target: %s\n", consolidateClause.getTarget());
    printIndent(stream, indent + 1);
    stream.printf("Type: %s\n", consolidateClause.getTypeStr());

    if (null != consolidateClause.getPriorityTarget()) {
      printIndent(stream, indent + 1);
      stream.printf("Priority Target: %s\n", consolidateClause.getPriorityTarget());
      printIndent(stream, indent + 1);
      stream.printf("Priority Direction: %s\n", consolidateClause.getPriorityDirectionStr());
    }

    printIndent(stream, indent + 1);
    stream.print("Input:\n");
    child().dump(stream, indent + 2);
  }

  @Override
  public void getPreds(TreeSet<PredicateNode> preds) {
    child().getPreds(preds);
  }

  @Override
  public void getRels(TreeSet<FromListItemNode> rels) {
    child().getRels(rels);
  }

  @Override
  public void toAOGNoRename(PrintWriter stream, int indent, Catalog catalog) throws Exception {
    printIndent(stream, indent);

    // Generate the AOG Select operator spec.
    stream.print("Consolidate(\n");

    // First argument is partial order type, in quotes.
    printIndent(stream, indent + 1);
    stream.printf("\"%s\",\n", consolidateClause.getTypeStr());

    // Second argument is the target of consolidation.
    // System.err.printf("Consolidation target is %s\n", consolidateTarget);
    // System.err.printf("As function: %s\n", consolidateTarget.asFunction());
    // System.err.printf("As AOG:\n");
    // consolidateTarget.asFunction().toAOG(System.err, 1);

    RValueNode consolidateTarget = consolidateClause.getTarget();
    if (null == consolidateTarget) {
      throw new FatalInternalError("Null target ptr for consolidate target");
    }

    consolidateTarget.asFunction().toAOG(stream, indent + 1, catalog);
    stream.print(",\n");
    // print the priorityTarget argument
    if (consolidateClause.getPriorityTarget() != null) {
      consolidateClause.getPriorityTarget().asFunction().toAOG(stream, indent + 1, catalog);
      stream.print(",\n");
      printIndent(stream, indent + 1);
      // stream.printf("\"%s\",\n", consolidateClause.getPriorityDirectionStr());
      stream.print('"');
      stream.print(consolidateClause.getPriorityDirectionStr());
      stream.print("\",\n");
    }

    // Third argument is the input.
    child().toAOG(stream, indent + 1, catalog);
    stream.print("\n");

    // Close the parens
    printIndent(stream, indent);
    stream.print(")");
  }

}
