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

import com.ibm.avatar.aql.catalog.Catalog;

/**
 * Top-level parse tree node to represent
 * 
 * <pre>
 * module 'module-name'
 * </pre>
 * 
 * statement.
 */
public class ModuleNode extends TopLevelParseTreeNode {

  /** Name of the module */
  private final NickNode name;

  public final NickNode getName() {
    return name;
  }

  public ModuleNode(NickNode moduleName, String containingFileName, Token origTok) {
    // set error location info
    super(containingFileName, origTok);

    this.name = moduleName;
    this.origTok = origTok;
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    printIndent(stream, indent);
    stream.printf("module ");
    name.dump(stream, 0);
    stream.print(";\n");
  }

  @Override
  protected int reallyCompareTo(AQLParseTreeNode o) {
    ModuleNode moduleNode = (ModuleNode) o;
    return this.name.compareTo(moduleNode.name);
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.avatar.aql.Node#qualifyReferences(com.ibm.avatar.aql.catalog.Catalog)
   */
  @Override
  public void qualifyReferences(Catalog catalog) {
    // No action

  }
}
