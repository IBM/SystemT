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

import com.ibm.avatar.aql.CreateTableNode;
import com.ibm.avatar.aql.FromListItemNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.PredicateNode;
import com.ibm.avatar.aql.catalog.Catalog;

/**
 * Plan node for a lookup table. Translates directly into the TableScan operator.
 */
public class LookupTablePlanNode extends PlanNode {

  /** Parse tree node for the create table statement; handles most of the work. */
  CreateTableNode node;

  public LookupTablePlanNode(CreateTableNode node) {
    super(new PlanNode[] {});
    if (null == node) {
      throw new RuntimeException("node is null");
    }
    this.node = node;
  }

  @Override
  public PlanNode deepCopyImpl() throws ParseException {
    return new LookupTablePlanNode(node);
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    printIndent(stream, indent);
    stream.printf("CreateTablePlanNode\n");
    printIndent(stream, indent + 1);
    stream.printf("Table Name: %s", node.getTableName());
  }

  @Override
  public void toAOG(PrintWriter stream, int indent, Catalog catalog) throws Exception {
    node.toAOG(stream, indent);
  }

  @Override
  public void toAOGNoRename(PrintWriter stream, int indent, Catalog catalog) throws Exception {
    throw new RuntimeException(
        "This function not implemented, " + "because we override ToAOG() directly.");
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
