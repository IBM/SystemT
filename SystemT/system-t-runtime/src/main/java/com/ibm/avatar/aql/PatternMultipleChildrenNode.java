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
package com.ibm.avatar.aql;

import java.util.ArrayList;
import java.util.List;

import com.ibm.avatar.aql.catalog.Catalog;

public abstract class PatternMultipleChildrenNode extends PatternExpressionNode {

  /**
   * Sub-expressions of this parse node.
   */
  protected ArrayList<PatternExpressionNode> children;

  public PatternMultipleChildrenNode(ArrayList<PatternExpressionNode> children,
      String containingFileName, Token origTok) {
    // Set the error location info
    super(containingFileName, origTok);

    this.children = children;
  }

  @Override
  public List<ParseException> validate(Catalog catalog) {
    List<ParseException> errors = new ArrayList<ParseException>();

    if (null != children && children.size() > 0) {
      for (PatternExpressionNode patternExpressionNode : children) {
        errors.addAll(patternExpressionNode.validate(catalog));
      }
    }
    return errors;
  }

  public int getNumChildren() {
    return children.size();
  }

  public PatternExpressionNode getChild(int i) {
    return children.get(i);
  }

  public void setChildren(ArrayList<PatternExpressionNode> nodeList) {
    children = nodeList;
  }

  public ArrayList<PatternExpressionNode> getChildren() {
    return children;
  }

  @Override
  public ArrayList<ColNameNode> getCols() {

    ArrayList<ColNameNode> colNames = new ArrayList<ColNameNode>();

    for (int i = 0; i < children.size(); i++)
      colNames.addAll(children.get(i).getCols());

    return colNames;
  }

  @Override
  protected ArrayList<Integer> getAllGroupIDs(boolean returnGroupsOnly) {

    ArrayList<Integer> ids = new ArrayList<Integer>();
    if (returnGroupsOnly)
      ids.addAll(getReturnGroupIDs());
    else
      ids.addAll(getGroupIDs());

    for (int i = 0; i < children.size(); i++)
      ids.addAll(children.get(i).getAllGroupIDs(returnGroupsOnly));

    return ids;
  }

  @Override
  protected ArrayList<Integer> getAllRepeatReturnGroupIDs() {

    ArrayList<Integer> ids = new ArrayList<Integer>();

    for (int i = 0; i < children.size(); i++)
      ids.addAll(children.get(i).getAllRepeatReturnGroupIDs());

    return ids;
  }

  @Override
  public boolean producesSameResultAsChild() {
    return getNumChildren() == 1;
  }

  public void setChildAtIdx(int i, PatternExpressionNode expr) {
    children.set(i, expr);
  }

  /**
   * @return Names of pass through columns that must be projected out by this node, in the form
   *         <viewAlias>.<attributeName>
   */
  @Override
  public ArrayList<PatternPassThroughColumn> getPassThroughCols() {
    ArrayList<PatternPassThroughColumn> passThroughCols = new ArrayList<PatternPassThroughColumn>();

    for (int i = 0; i < children.size(); i++)
      passThroughCols.addAll(children.get(i).getPassThroughCols());

    return passThroughCols;
  }

  @Override
  public void qualifyReferences(Catalog catalog) throws ParseException {
    for (PatternExpressionNode peNode : children) {
      peNode.qualifyReferences(catalog);
    }
  }

  @Override
  public boolean isOptional() {
    for (PatternExpressionNode node : children) {
      if (!node.isOptional()) {
        return false;
      }
    }
    return true;
  }
}
