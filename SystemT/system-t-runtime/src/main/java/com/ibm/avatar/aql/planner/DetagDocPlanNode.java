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

import com.ibm.avatar.aql.DetagDocNode;
import com.ibm.avatar.aql.FromListItemNode;
import com.ibm.avatar.aql.PredicateNode;
import com.ibm.avatar.aql.catalog.Catalog;

public class DetagDocPlanNode extends PlanNode {

  /** Parse tree node for the detag statement; handles most of the work. */
  DetagDocNode dn;

  public DetagDocPlanNode(DetagDocNode dn) {
    super(new PlanNode[] {});
    this.dn = dn;
  }

  @Override
  public PlanNode deepCopyImpl() {
    return new DetagDocPlanNode(dn);
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    printIndent(stream, indent);
    stream.printf("DetagDocNode\n");
  }

  @Override
  public void toAOG(PrintWriter stream, int indent, Catalog catalog) throws Exception {
    dn.toAOG(stream, indent);
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