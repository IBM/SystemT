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
 * Top-level parse tree node for a
 * 
 * <pre>
 * import module
 * </pre>
 * 
 * statement.
 */
public class ImportModuleNode extends TopLevelParseTreeNode implements ImportNode {
  /** Name of the element imported by this node */
  protected NickNode importedModuleName;

  /**
   * @param origTok
   * @param nodeName
   */
  public ImportModuleNode(NickNode importedModuleName, String containingFileName, Token origTok) {
    // set error location info
    super(containingFileName, origTok);

    this.origTok = origTok;
    this.importedModuleName = importedModuleName;
  }

  /**
   * @return the importedModuleName
   */
  public NickNode getImportedModuleName() {
    return importedModuleName;
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    printIndent(stream, indent);
    stream.printf("import module ");
    importedModuleName.dump(stream, 0);
    stream.print(";\n");
  }

  @Override
  protected int reallyCompareTo(AQLParseTreeNode o) {
    int val = 0;
    ImportModuleNode other = (ImportModuleNode) o;

    // compare nodeName
    if (importedModuleName == null && other.importedModuleName != null) {
      return -1;
    }

    if (importedModuleName != null && other.importedModuleName == null) {
      return 1;
    }

    if (importedModuleName != null && other.importedModuleName != null) {
      val = importedModuleName.compareTo(other.importedModuleName);
      if (val != 0) {
        return val;
      }
    }

    return val;
  }

  @Override
  public void qualifyReferences(Catalog catalog) {
    // No action

  }

}
