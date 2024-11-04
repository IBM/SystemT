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
 * Parent class for all nodes that contain end-of-statement token information (usually a semi-colon)
 * All such nodes also contain error information, so this class is a child of
 * {@link NodeWithErrorInfo}
 * 
 */
public abstract class TopLevelParseTreeNode extends AbstractAQLParseTreeNode {
  public TopLevelParseTreeNode(String containingFileName, Token origTok) {
    // set error location info
    super(containingFileName, origTok);
  }

  /** Copy constructor for use by copy constructors in subclass. */
  protected TopLevelParseTreeNode(TopLevelParseTreeNode orig) {
    super((AbstractAQLParseTreeNode) orig);

    // Assume that Token objects are immutable
    endOfStmtToken = orig.endOfStmtToken;
  }

  /** Token corresponding to the end of the statement associated with this node */
  protected Token endOfStmtToken;

  /**
   * @return a token corresponding to the end of the statement associated with this node
   */
  public Token getEndOfStmtToken() {
    return endOfStmtToken;
  }

  /**
   * @param endOfStmtToken the end-of-statement token to set
   */
  public void setEndOfStmtToken(Token endOfStmtToken) {
    this.endOfStmtToken = endOfStmtToken;
  }

}
