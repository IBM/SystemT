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
 * Logical plan node for a projection operator. Takes a list of column names that will appear in the
 * output of the projection, in the order in which they should appear.
 * 
 */
public class ProjectionNode extends PlanNode {

  /**
   * Columns left after project, in the order in which they appear; a column can appear multiple
   * times here.
   */
  // private ArrayList<String> colnames;
  /**
   * Main constructor; takes only the root of the input tree as an argument. The actual projection
   * columns should be passed in via {@link #addRenaming(String, String)}.
   */
  public ProjectionNode(PlanNode input) {
    super(input);
    // this.colnames = colnames;
  }

  @Override
  public PlanNode deepCopyImpl() throws ParseException {
    // Column renamings will be added by the superclass
    return new ProjectionNode(child().deepCopy());
  }

  @Override
  public void toAOGNoRename(PrintWriter stream, int indent, Catalog catalog) throws Exception {
    // All the logic for generating projections is in the parent.
    child().toAOG(stream, indent, catalog);
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    printIndent(stream, indent);
    stream.printf("ProjectionNode\n");
    printIndent(stream, indent + 1);
    // stream.printf("Cols: %s\n", colnames);

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
