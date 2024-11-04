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
import com.ibm.avatar.aql.ScalarFnCallNode;
import com.ibm.avatar.aql.PredicateNode;
import com.ibm.avatar.aql.catalog.Catalog;

/**
 * Logical plan node for a selection operator. Takes a list of predicates to be applied as a
 * conjunction.
 * 
 */
public class SelectionNode extends PlanNode {

  /** The selection will apply the conjunction of these predicates. */
  private ArrayList<PredicateNode> preds;

  public SelectionNode(PlanNode input, ArrayList<PredicateNode> preds) {
    super(input);
    this.preds = preds;
  }

  @Override
  public PlanNode deepCopyImpl() throws ParseException {
    return new SelectionNode(child().deepCopy(), preds);
  }

  @Override
  public void toAOGNoRename(PrintWriter stream, int indent, Catalog catalog) throws Exception {

    printIndent(stream, indent);

    // Generate the AOG Select operator spec.
    stream.print("Select(\n");

    // First argument of Select is the selection predicate.
    ArrayList<PredicateNode> toApply = preds;

    genAOGForPreds(stream, indent, toApply, catalog);
    stream.print(",\n");

    // Second argument of Select is the input.
    child().toAOG(stream, indent + 1, catalog);
    stream.print("\n");

    // Close the parens
    printIndent(stream, indent);
    stream.print(")");
  }

  /**
   * Generate the selection/nested-loops join predicate string for a set of predicate parse tree
   * nodes.
   * 
   * @param stream output stream for AOG spec
   * @param indent how far to indent when writing to output stream
   * @param toApply predicates to apply
   * @param catalog pointer to AQL catalog for metadata lookup
   */
  public static void genAOGForPreds(PrintWriter stream, int indent,
      ArrayList<PredicateNode> toApply, Catalog catalog) {
    if (0 == toApply.size()) {
      // SPECIAL CASE: Empty selection -- assume pass-through.
      printIndent(stream, indent + 1);
      stream.print("True()");
      // END SPECIAL CASE
    } else if (1 == toApply.size()) {
      // SPECIAL CASE: Single predicate; don't bother create an And
      // predicate.
      toApply.get(0).getFunc().toAOG(stream, indent + 1, catalog);
      // END SPECIAL CASE
    } else {
      printIndent(stream, indent + 1);
      stream.print("And(\n");

      for (int i = 0; i < toApply.size(); i++) {
        PredicateNode pred = toApply.get(i);
        ScalarFnCallNode func = pred.getFunc();

        func.toAOG(stream, indent + 2, catalog);

        if (i < toApply.size() - 1) {
          stream.print(",\n");
        }
      }
      stream.print("\n");

      // Close the And() predicate
      printIndent(stream, indent + 1);
      stream.print(")");
    }
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    printIndent(stream, indent);
    stream.printf("SelectionNode\n");
    printIndent(stream, indent + 1);
    stream.printf("Preds: %s\n", preds);

    printIndent(stream, indent + 1);
    stream.print("Input:\n");
    child().dump(stream, indent + 2);
  }

  @Override
  public void getPreds(TreeSet<PredicateNode> preds) {
    preds.addAll(this.preds);
    child().getPreds(preds);
  }

  @Override
  public void getRels(TreeSet<FromListItemNode> rels) {
    child().getRels(rels);
  }
}
