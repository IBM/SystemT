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

/**
 * Parse tree for a HAVING clause (currently only supported on EXTRACT statements). Most of the
 * heavy lifting is done by the {@link WhereClauseNode} class.
 */
public class HavingClauseNode extends WhereClauseNode {

  public HavingClauseNode(String containingFileName, Token origTok) {
    // set error location info
    super(containingFileName, origTok);

  }

  @Override
  protected int reallyCompareTo(AQLParseTreeNode o) {
    HavingClauseNode other = (HavingClauseNode) o;
    return compareNodeLists(preds, other.preds);
  }
}
