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

/**
 * Base class for sequence pattern expression parse tree nodes.
 * 
 */
public abstract class PatternExpressionNode extends AbstractAQLParseTreeNode
    implements Comparable<AQLParseTreeNode> {

  public enum GroupType {
    NONE, CAPTURING, NON_CAPTURING
  }

  public static final int UNASSIGNED_CSE_ID = -1;

  /**
   * The group type of this node
   */
  private GroupType groupType;

  /**
   * The IDs of groups that this node represents Note that a node may represent multiple group IDs
   * E.g., in ((<R.a>)), the node <R.a> corresponds to groups 0, 1 and 2
   */
  private ArrayList<Integer> groupIDs = new ArrayList<Integer>();

  /**
   * The IDs of groups represented by this node that are retained in the return clause of the
   * sequence pattern statement.
   */
  private ArrayList<Integer> groupIDsToReturn = new ArrayList<Integer>();

  /**
   * The ID of this node in the Common Subexpression Index table
   */
  private int cseID;

  public PatternExpressionNode(String containingFileName, Token origTok) {
    super(containingFileName, origTok);

    setGroupType(GroupType.NONE);
    groupIDs = new ArrayList<Integer>();
    cseID = UNASSIGNED_CSE_ID;
  }

  public void setGroupType(GroupType type) {
    this.groupType = type;
  }

  public GroupType getGroupType() {
    return groupType;
  }

  public void setGroupIDs(ArrayList<Integer> groupIDs) throws ParseException {
    this.groupIDs.addAll(groupIDs);
  }

  public ArrayList<Integer> getGroupIDs() {
    return groupIDs;
  }

  public void setGroupIDsToReturn(ArrayList<Integer> groupIDs) {
    groupIDsToReturn = groupIDs;
  }

  public ArrayList<Integer> getReturnGroupIDs() {
    return groupIDsToReturn;
  }

  public void setCseID(int id) throws ParseException {
    cseID = id;
  }

  public int getCseID() {
    return cseID;
  }

  /**
   * @return a list of ColNameNodes referenced in the pattern; these come from the
   *         PatternAtomColRefNodes.
   */
  public abstract ArrayList<ColNameNode> getCols();

  /**
   * @return the ids of the groups created by this node and all its descendants
   */
  public ArrayList<Integer> getAllGroupIDs() {
    return getAllGroupIDs(false);
  }

  /**
   * @return the ids of the groups created by this node and all its descendants that are also
   *         retained in the output
   */
  public ArrayList<Integer> getAllReturnGroupIDs() {
    return getAllGroupIDs(true);
  }

  /**
   * @return Information about pass-through columns that must be retained by this node
   */
  public abstract ArrayList<PatternPassThroughColumn> getPassThroughCols();

  /**
   * @return the ids of the groups created by descendants of repeat nodes that are also retained in
   *         the output
   */
  protected abstract ArrayList<Integer> getAllRepeatReturnGroupIDs();

  protected abstract ArrayList<Integer> getAllGroupIDs(boolean returnGroupsOnly);

  /**
   * Returns true if the pattern represented by this node matches the empty string. E.g., an
   * optional element, an alternation containing an optional element, or a sequence of all optional
   * elements
   * 
   * @return
   */
  protected abstract boolean matchesEmpty();

  public abstract boolean producesSameResultAsChild();

  public abstract boolean isOptional();
}
