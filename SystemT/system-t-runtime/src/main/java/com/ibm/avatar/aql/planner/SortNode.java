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
import com.ibm.avatar.aql.RValueNode;
import com.ibm.avatar.aql.catalog.Catalog;

public class SortNode extends PlanNode {

  /** Function that returns the sort key. */
  private ArrayList<RValueNode> sortKeyFuncs;

  public SortNode(ArrayList<RValueNode> sortKeyFuncs, PlanNode input) {
    super(input);
    this.sortKeyFuncs = sortKeyFuncs;
  }

  @Override
  public PlanNode deepCopyImpl() throws ParseException {
    return new SortNode(sortKeyFuncs, child().deepCopy());
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    printIndent(stream, indent);
    stream.printf("SortNode\n");
    printIndent(stream, indent + 1);
    stream.printf("Sort Keys: %s\n", sortKeyFuncs);

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
    stream.print("Sort(\n");

    // First argument of Sort is the list of functions that generates the sort key
    printIndent(stream, indent);
    stream.print("(\n");

    // Print the first function in the list
    sortKeyFuncs.get(0).asFunction().toAOG(stream, indent + 1, catalog);

    // Print the rest of the functions with a COMMA between them
    for (int i = 1; i < sortKeyFuncs.size(); i++) {

      stream.print(",\n");

      sortKeyFuncs.get(i).asFunction().toAOG(stream, indent + 1, catalog);
      // sortKeyFunc.toAOG(stream, indent + 1);
    }

    stream.print("\n");
    printIndent(stream, indent);
    stream.print("),\n");

    // Second argument of Sort is the input.
    child().toAOG(stream, indent + 1, catalog);
    stream.print("\n");

    // Close the parens
    printIndent(stream, indent);
    stream.print(")");
  }

}
