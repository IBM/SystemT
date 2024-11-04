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
 * Query plan with a Difference operator at the top.
 * 
 */
public class MinusPlanNode extends PlanNode {

  private static final int FIRST_ARG_IX = 0;
  private static final int SECOND_ARG_IX = 1;

  private PlanNode firstArg() {
    return getChildren()[FIRST_ARG_IX];
  }

  private PlanNode secondArg() {
    return getChildren()[SECOND_ARG_IX];
  }

  public MinusPlanNode(PlanNode firstArg, PlanNode secondArg) {
    super(new PlanNode[] {firstArg, secondArg});
  }

  @Override
  public PlanNode deepCopyImpl() throws ParseException {
    return new MinusPlanNode(firstArg().deepCopy(), secondArg().deepCopy());
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    printIndent(stream, indent);
    stream.printf("UnionAllPlanNode\n");

    printIndent(stream, indent + 1);
    stream.printf("First Plan:\n");
    firstArg().dump(stream, indent + 2);

    printIndent(stream, indent + 1);
    stream.printf("Second Plan:\n");
    secondArg().dump(stream, indent + 2);
  }

  @Override
  public void toAOGNoRename(PrintWriter stream, int indent, Catalog catalog) throws Exception {
    stream.print("Difference(\n");

    firstArg().toAOG(stream, indent + 1, catalog);
    stream.print(",\n");

    secondArg().toAOG(stream, indent + 1, catalog);

    stream.print(")");
  }

  @Override
  public void getPreds(TreeSet<PredicateNode> preds) {
    firstArg().getPreds(preds);
    secondArg().getPreds(preds);
  }

  @Override
  public void getRels(TreeSet<FromListItemNode> rels) {
    firstArg().getRels(rels);
    secondArg().getRels(rels);
  }

}
