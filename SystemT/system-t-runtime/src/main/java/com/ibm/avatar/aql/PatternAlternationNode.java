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

public class PatternAlternationNode extends PatternMultipleChildrenNode {

  public PatternAlternationNode(ArrayList<PatternExpressionNode> children) {
    // set error location info
    // children is guaranteed to have at least 1 member -- parser only calls this function after
    // parsing one node
    super(children, children.get(0).containingFileName, children.get(0).origTok);
  }

  @Override
  public List<ParseException> validate(Catalog catalog) {
    return super.validate(catalog);
  }

  @Override
  protected int reallyCompareTo(AQLParseTreeNode o) {
    PatternAlternationNode other = (PatternAlternationNode) o;
    return compareNodeLists(children, other.children);
  }

  @Override
  public void dump(PrintWriter stream, int indent) {

    for (int i = 0; i < getNumChildren(); i++) {

      if (i > 0)
        stream.print("|");

      getChild(i).dump(stream, indent + 1);
    }
  }

  @Override
  public String toString() {

    StringBuilder out = new StringBuilder();

    for (int i = 0; i < getNumChildren(); i++) {

      if (i > 0)
        out.append("|");

      out.append(getChild(i));
    }

    return out.toString();
  }

  @Override
  public boolean matchesEmpty() {

    for (int i = 0; i < getNumChildren(); i++) {
      if (getChild(i).matchesEmpty())
        return true;
    }

    return false;
  }

  @Override
  public void qualifyReferences(Catalog catalog) {
    // No action
  }

}
