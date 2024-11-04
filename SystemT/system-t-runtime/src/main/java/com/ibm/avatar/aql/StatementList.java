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

import java.util.LinkedList;

/**
 * StatementList will contain ordered list of parse node tree and parse error. There are methods to
 * add parse tree node and parse error. This class will be the output of AQLParser.parse() method.
 */
public class StatementList {

  /** Ordered list of parse tree nodes for all the Top level statement */
  private LinkedList<AQLParseTreeNode> topLevelStatementList = new LinkedList<AQLParseTreeNode>();

  /** Ordered list of parse error */
  private LinkedList<ParseException> parseErrorList = new LinkedList<ParseException>();

  /** Method to add parse tree node to StatementList */
  public void addParseTreeNode(AQLParseTreeNode node) {
    topLevelStatementList.add(node);
  }

  /** Method to add parse error to StatementList */
  public void addParseError(ParseException pe) {
    parseErrorList.add(pe);
  }

  /** Utility method to a StatementList object to current StatementList */
  public void addStatementList(StatementList statementList) {
    topLevelStatementList.addAll(statementList.getParseTreeNodes());
    parseErrorList.addAll(statementList.getParseErrors());
  }

  /** @return List of all the contained parse tree nodes */
  public LinkedList<AQLParseTreeNode> getParseTreeNodes() {
    return topLevelStatementList;
  }

  /** @return List of all the contained parse error */
  public LinkedList<ParseException> getParseErrors() {
    return parseErrorList;
  }

}
