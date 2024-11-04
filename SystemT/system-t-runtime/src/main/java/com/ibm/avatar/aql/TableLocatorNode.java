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

import java.util.TreeSet;

import com.ibm.avatar.aog.AOGOpTree.Nickname;
import com.ibm.avatar.aql.catalog.Catalog;

/** AQL parse tree node for a table locator. Most of the implementation is in the superclass. */
public class TableLocatorNode extends NickNode {

  /**
   * Main constructor, called from AQL parser.
   * 
   * @param parserOutput output of lower-level rules that parse the nickname literal that appears in
   *        the function call
   */
  public TableLocatorNode(NickNode parserOutput) {
    // set error location info
    super(parserOutput.getNickname(), parserOutput.getContainingFileName(),
        parserOutput.getOrigTok());
  }

  @Override
  public void qualifyReferences(Catalog catalog) {
    String before = this.nick;
    String after = catalog.getQualifiedViewOrTableName(before);

    // System.err.printf("TableLocatorNode.qualifyReferences(): %s ==> %s\n",
    // before, after);

    this.nick = after;
  }


  @Override
  public void getReferencedViews(TreeSet<String> accum, Catalog catalog) {
    // System.err.printf("Adding %s to referenced views.\n", this.nick);

    accum.add(this.nick);
  }

  @Override
  public Object toAOGNode(Catalog catalog) {
    // Convert to the equivalent AOG parse tree node.
    Nickname convertedNick = new Nickname(getModuleName(), this.nick);
    return new com.ibm.avatar.aog.TableLocatorNode(convertedNick);
  }

}
