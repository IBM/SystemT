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
import com.ibm.avatar.aql.catalog.Catalog;

/**
 * Logical plan node for a Limit operator. Takes an integer parameter -- the maximum number of
 * tuples to return.
 * 
 */
public class LimitNode extends PlanNode {

  private int maxTups;

  public LimitNode(PlanNode input, int maxTups) {
    super(input);
    this.maxTups = maxTups;
  }

  @Override
  public PlanNode deepCopyImpl() throws ParseException {
    return new LimitNode(child().deepCopy(), maxTups);
  }

  @Override
  public void toAOGNoRename(PrintWriter stream, int indent, Catalog catalog) throws Exception {

    // First argument is the maximum number of tuples per document
    printIndent(stream, indent);
    stream.printf("Limit(%d,\n", maxTups);

    // Second argument is the input.
    child().toAOG(stream, indent + 1, catalog);

    // Close parens.
    printIndent(stream, indent);
    stream.print(")");
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    printIndent(stream, indent);
    stream.printf("LimitNode\n");
    printIndent(stream, indent + 1);
    stream.printf("MaxTups: %d\n", maxTups);

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

}
