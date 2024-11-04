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

import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.aog.AOGParseTree;
import com.ibm.avatar.aql.FromListItemNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.PredicateNode;
import com.ibm.avatar.aql.catalog.Catalog;

/**
 * Special PlanNode for the top level of a view declaration.
 * 
 */
public class ViewNode extends PlanNode {

  private String viewName;

  // If true, the view is a document view
  private boolean isExternal;

  public ViewNode(String viewName) {
    super(new PlanNode[0]);
    this.viewName = viewName;
    this.isExternal = false;
  }

  @Override
  public PlanNode deepCopyImpl() throws ParseException {
    ViewNode ret = new ViewNode(viewName);
    ret.setExternal(isExternal);
    return ret;
  }

  public void setBodyPlan(PlanNode bodyPlan) {
    setChildren(new PlanNode[] {bodyPlan});
  }

  public PlanNode getBodyPlan() {
    return child();
  }

  public void setExternal(boolean isExternal) {
    this.isExternal = isExternal;
  }

  public boolean isExternal() {
    return isExternal;
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    printIndent(stream, indent);
    stream.printf("ViewNode\n");

    printIndent(stream, indent + 1);
    stream.printf("Name: %s\n", viewName);
    printIndent(stream, indent + 1);
    stream.print("Body:\n");

    PlanNode viewBody = child();
    if (null == viewBody) {
      printIndent(stream, indent + 2);
      stream.printf("<none>");
    } else {
      viewBody.dump(stream, indent + 2);
    }
  }

  /**
   * Special-case version of toAOG() for this top-level node.
   */
  @SuppressWarnings("deprecation")
  @Override
  public void toAOG(PrintWriter stream, int indent, Catalog catalog) throws Exception {
    PlanNode bodyPlan = child();
    if (null == bodyPlan) {
      // SPECIAL CASE: No body --> no definition
      // This special case should only occur with the $DocScan built-in view.
      if (false == AOGParseTree.DOC_SCAN_BUILTIN.equals(viewName)) {
        throw new RuntimeException(String.format("No view body for view %s", viewName));
      } else {
        return;
      }
      // END SPECIAL CASE
    }
    stream.printf("%s =\n", StringUtils.toAOGNick(viewName));
    bodyPlan.toAOG(stream, 0, catalog);
    stream.print(";\n\n");
  }

  @Override
  public void toAOGNoRename(PrintWriter stream, int indent, Catalog catalog) throws Exception {
    throw new Exception("Function not implemented.");
  }

  /*
   * @Override public PlanNode[] getChildren() { if (null == bodyPlan) { // SPECIAL CASE: No body
   * --> no definition // This special case should only occur with the $DocScan built-in view. if
   * (false == AOGParseTree.DOC_SCAN_BUILTIN.equals(viewName)) { throw new RuntimeException(
   * String.format("No view body for view %s", viewName)); } else { return new PlanNode[] {}; } //
   * END SPECIAL CASE } return new PlanNode[] { bodyPlan }; }
   */

  @Override
  public void getPreds(TreeSet<PredicateNode> preds) {
    throw new RuntimeException("Should never be called");
  }

  @Override
  public void getRels(TreeSet<FromListItemNode> rels) {
    throw new RuntimeException("Should never be called");
  }

  public String getViewName() {
    return viewName;
  }

}
