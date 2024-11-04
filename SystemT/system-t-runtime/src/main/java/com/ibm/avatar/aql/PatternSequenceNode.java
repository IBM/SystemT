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

public class PatternSequenceNode extends PatternMultipleChildrenNode {

  public enum PositionInSequence {
    BEGIN, MIDDLE, END, UNDEFINED
  }

  public enum SequenceType {
    ALL_REQUIRED, REQUIRED_FOLLOWED_BY_OPTIONAL, OPTIONAL_FOLLOWED_BY_REQUIRED, OPTIONAL_FOLLEWED_BY_OPTIONAL, UNDEFINED
  }

  public PatternSequenceNode(ArrayList<PatternExpressionNode> children) {
    // set error location info
    // this constructor is guaranteed to have at least one child
    super(children, children.get(0).getContainingFileName(), children.get(0).getOrigTok());
  }

  @Override
  public List<ParseException> validate(Catalog catalog) {
    return super.validate(catalog);
  }

  @Override
  protected int reallyCompareTo(AQLParseTreeNode o) {
    PatternSequenceNode other = (PatternSequenceNode) o;
    return compareNodeLists(children, other.children);
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    for (int i = 0; i < getNumChildren(); i++) {
      if (i > 0)
        stream.print(" ");

      getChild(i).dump(stream, indent + 1);
    }
  }

  @Override
  public String toString() {

    StringBuilder out = new StringBuilder();

    for (int i = 0; i < getNumChildren(); i++) {

      if (i > 0)
        out.append(" ");

      out.append(getChild(i));
    }

    return out.toString();
  }

  @Override
  public boolean matchesEmpty() {

    for (int i = 0; i < getNumChildren(); i++) {
      if (!getChild(i).matchesEmpty())
        return false;
    }

    return true;
  }

  /**
   * Returns the type of this sequence. Possible options: ABC... a linear sequence with no optional
   * elements (token gaps may occurr between required elements) AB? a required element followed by
   * an optional element (a token gap may occur between A and B) A?B an optional element followed by
   * a required element (a token gap may occur between A and B) If none of the above apply, the type
   * returned is UNDEFINED.
   * 
   * @return
   */
  public SequenceType getType() {

    boolean twoNodes = false;
    PatternExpressionNode node1 = getChild(0), node2 = null;

    // A followed by B
    if (getNumChildren() == 2) {
      twoNodes = true;
      node2 = getChild(1);
    }
    // A <Token_Gap> B
    else if (getNumChildren() == 3 && getChild(1) instanceof PatternAtomTokenGapNode) {
      twoNodes = true;
      node2 = getChild(2);
    }

    // Handle cases: AB?, A?B, and A?B?
    if (twoNodes) {
      boolean node1IsOptional = node1.isOptional();
      boolean node2IsOptional = node2.isOptional();
      // Optional node followed by a required node
      if (node1IsOptional && !node2IsOptional)
        return SequenceType.OPTIONAL_FOLLOWED_BY_REQUIRED;

      // required node followed by optional node
      if (!node1IsOptional && node2IsOptional)
        return SequenceType.REQUIRED_FOLLOWED_BY_OPTIONAL;

      // two optional nodes
      if (node1IsOptional && node2IsOptional)
        return SequenceType.OPTIONAL_FOLLEWED_BY_OPTIONAL;
    }

    // Handle the case: ABC... (i.e., check if all children are required)
    for (int i = 0; i < getNumChildren(); i++)
      if (getChild(i) instanceof PatternOptionalNode)
        // Found an optional, return undefined
        return SequenceType.UNDEFINED;

    // Found only required nodes
    return SequenceType.ALL_REQUIRED;
  }

}
