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

/**
 * Parse tree node for the return clause of an EXTRACT PATTERN or EXTRACT REGEX statement.
 * 
 */
public class ReturnClauseNode extends AbstractAQLParseTreeNode {
  public ReturnClauseNode(String containingFileName, Token origTok) {
    // set error location info
    super(containingFileName, origTok);

  }

  /** Integer indices of the groups specified. */
  private final ArrayList<Integer> groupIDs = new ArrayList<Integer>();

  /** Names given to the different groups. */
  private final ArrayList<NickNode> names = new ArrayList<NickNode>();

  @Override
  public List<ParseException> validate(Catalog catalog) {
    return super.validate(catalog);
  }

  public void addEntry(int groupID, NickNode name) {
    groupIDs.add(groupID);
    names.add(name);
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    printIndent(stream, indent);
    stream.print("return ");

    for (int i = 0; i < groupIDs.size(); i++) {

      printIndent(stream, (i == 0 ? 0 : indent + 1));
      if (i > 0) {
        stream.print(" and ");
      }
      stream.printf("group %d as %s", groupIDs.get(i),
          AQLParser.quoteReservedWord(names.get(i).getNickname()));

      stream.print("\n");

    }
  }

  @Override
  protected int reallyCompareTo(AQLParseTreeNode o) {
    ReturnClauseNode other = (ReturnClauseNode) o;

    int val = size() - other.size();
    if (val != 0) {
      return val;
    }

    // If we get here, the group lists of the same size. Break the tie by
    // comparing list elements.

    // Compare the group IDs
    for (int i = 0; i < size(); i++) {
      Comparable<Integer> thisCmp = groupIDs.get(i);
      // Comparable otherCmp = (Comparable)other.groupIDs.get(i);
      val = thisCmp.compareTo(other.groupIDs.get(i));
      if (val != 0) {
        return val;
      }
    }

    // Compare the group names
    for (int i = 0; i < size(); i++) {
      Comparable<AQLParseTreeNode> thisCmp = names.get(i);
      // Comparable otherCmp = (Comparable)other.names.get(i);
      val = thisCmp.compareTo(other.names.get(i));
      if (val != 0) {
        return val;
      }
    }
    return val;
  }

  public String getName(int ix) {
    return names.get(ix).getNickname();
  }

  public int getGroupID(int ix) {
    return groupIDs.get(ix);
  }

  public ArrayList<Integer> getGroupIDs() {
    return groupIDs;
  }

  public int size() {
    return names.size();
  }

  public ArrayList<NickNode> getAllNames() {
    return names;
  }

  @Override
  public void qualifyReferences(Catalog catalog) {
    // No action

  }
}
