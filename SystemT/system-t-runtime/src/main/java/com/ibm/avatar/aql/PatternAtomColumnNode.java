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

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import com.ibm.avatar.aql.catalog.Catalog;

public class PatternAtomColumnNode extends PatternAtomNode {

  private final ColNameNode column;

  /**
   * Names of pass through columns that must be projected out by this node, in the form
   * <viewAlias>.<attributeName>
   */
  private ArrayList<PatternPassThroughColumn> passThroughCols;

  public PatternAtomColumnNode(ColNameNode column) {
    // set the error location info
    super(column.getContainingFileName(), column.getOrigTok());

    this.column = column;
  }

  @Override
  public List<ParseException> validate(Catalog catalog) {
    return super.validate(catalog);
  }

  public ColNameNode getCol() {
    return column;
  }

  @Override
  protected int reallyCompareTo(AQLParseTreeNode o) {
    PatternAtomColumnNode other = (PatternAtomColumnNode) o;
    return column.compareTo(other.column);
  }

  @Override
  public ArrayList<ColNameNode> getCols() {

    ArrayList<ColNameNode> cols = new ArrayList<ColNameNode>();
    cols.add(column);
    return cols;
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    AbstractAQLParseTreeNode.printIndent(stream, 0);
    stream.printf("<");
    column.dump(stream, 0);
    stream.printf(">");
  }

  @Override
  public String toString() {
    return String.format("<%s>", column);
  }

  @Override
  public boolean matchesEmpty() {
    return false;
  }

  /**
   * @return Names of pass through columns that must be projected out by this node, in the form
   *         <viewAlias>.<attributeName>
   */
  @Override
  public ArrayList<PatternPassThroughColumn> getPassThroughCols() {
    return passThroughCols;
  }

  /**
   * Set the collection of pass through cols that must be projected out by this node, in the form
   * 'viewName.colName'. This method is only available for the PatternAtomColumnNode node, and not
   * for any other type of PatternExpressionNode, because this is the only kind of node for which we
   * must explicitly set the list of pass through cols, based on the select list of an EXTRACT
   * PATTERN statement. For all other types of PatternExpressionNode, we infer the list of pass
   * though cols whenever necessary by recursively traversing its children.
   * 
   * @param cols
   */
  public void setPassThroughCols(ArrayList<PatternPassThroughColumn> cols) {
    passThroughCols = cols;
  }

  @Override
  public void qualifyReferences(Catalog catalog) {
    column.qualifyReferences(catalog);
  }
}
