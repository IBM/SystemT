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

public abstract class PatternSingleChildNode extends PatternExpressionNode {

  /**
   * Sub-expression of this parse node.
   */
  protected PatternExpressionNode child;

  public PatternSingleChildNode(PatternExpressionNode atom, String containingFileName,
      Token origTok) {
    // set error location info
    super(containingFileName, origTok);

    this.child = atom;
  }

  @Override
  public List<ParseException> validate(Catalog catalog) {
    return child.validate(catalog);
  }

  public PatternExpressionNode getChild() {
    return child;
  }

  public void setChild(PatternExpressionNode expr) {
    child = expr;
  }

  @Override
  public ArrayList<ColNameNode> getCols() {

    return child.getCols();
  }

  @Override
  protected ArrayList<Integer> getAllGroupIDs(boolean returnGroupsOnly) {

    ArrayList<Integer> ids = new ArrayList<Integer>();

    if (returnGroupsOnly)
      ids.addAll(getReturnGroupIDs());
    else
      ids.addAll(getGroupIDs());

    ids.addAll(child.getAllGroupIDs(returnGroupsOnly));

    return ids;
  }

  @Override
  protected ArrayList<Integer> getAllRepeatReturnGroupIDs() {

    ArrayList<Integer> ids = new ArrayList<Integer>();

    if (this instanceof PatternRepeatNode)
      ids.addAll(child.getAllReturnGroupIDs());
    else
      ids.addAll(child.getAllRepeatReturnGroupIDs());

    return ids;
  }

  /**
   * @return Names of pass through columns that must be projected out by this node, in the form
   *         <viewAlias>.<attributeName>
   */
  @Override
  public ArrayList<PatternPassThroughColumn> getPassThroughCols() {
    ArrayList<PatternPassThroughColumn> passThroughCols = new ArrayList<PatternPassThroughColumn>();

    passThroughCols.addAll(child.getPassThroughCols());

    return passThroughCols;
  }

  @Override
  public void qualifyReferences(Catalog catalog) throws ParseException {
    child.qualifyReferences(catalog);
  }

  @Override
  public boolean isOptional() {
    return child.isOptional();
  }
}
