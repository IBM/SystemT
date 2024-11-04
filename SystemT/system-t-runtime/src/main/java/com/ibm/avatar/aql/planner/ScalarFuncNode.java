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
import com.ibm.avatar.aql.ScalarFnCallNode;
import com.ibm.avatar.aql.PredicateNode;
import com.ibm.avatar.aql.catalog.Catalog;

/**
 * Logical plan node for a scalar function call. Inputs are the parse tree node for the function
 * call and the name to be given the result of the function.
 * 
 */
public class ScalarFuncNode extends PlanNode {

  private ScalarFnCallNode func;

  private String resultName;

  public ScalarFuncNode(ScalarFnCallNode func, PlanNode input) {
    super(input);
    this.func = func;
    this.resultName = func.getColName();
  }

  @Override
  public PlanNode deepCopyImpl() throws ParseException {
    return new ScalarFuncNode(func, child().deepCopy());
  }

  @Override
  public void toAOGNoRename(PrintWriter stream, int indent, Catalog catalog) throws Exception {
    printIndent(stream, indent);
    stream.print("ApplyFunc(\n");

    // System.err.printf("Dumping function: %s\n", func);

    // ApplyFunc now takes the same internal syntax as the predicate
    // version.
    func.toAOG(stream, indent + 1, catalog);
    stream.printf(" => \"%s\",\n", resultName);

    child().toAOG(stream, indent + 1, catalog);
    stream.print("\n");

    // Close the parens on the AOG ApplyFunc() spec.
    printIndent(stream, indent);
    stream.print(")");
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    printIndent(stream, indent);
    stream.printf("ScalarFuncNode\n");
    printIndent(stream, indent + 1);
    stream.printf("Func: %s\n", func);

    printIndent(stream, indent + 1);
    stream.printf("Result Name: %s\n", resultName);

    printIndent(stream, indent + 1);
    stream.print("Input:\n");
    child().dump(stream, indent + 2);
  }

  @Override
  public void getPreds(TreeSet<PredicateNode> preds) {
    throw new RuntimeException("Should never be called");
  }

  @Override
  public void getRels(TreeSet<FromListItemNode> rels) {
    child().getRels(rels);
  }

}
