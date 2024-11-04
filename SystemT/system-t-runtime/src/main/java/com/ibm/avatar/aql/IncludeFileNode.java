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
 * include 'aql-file'
 * </pre>
 * 
 * statement.
 */
public class IncludeFileNode extends TopLevelParseTreeNode {

  /** Name of the aql file Included */
  private final StringNode includedFileName;

  private final boolean allowEmptyFileSet;

  public final StringNode getIncludedFileName() {
    return includedFileName;
  }

  public final boolean isAllowEmptyFileSet() {
    return allowEmptyFileSet;
  }

  public IncludeFileNode(StringNode includedFileName, boolean allowEmptyFileSet,
      String containingFileName, Token origTok) {
    // set error location info
    super(containingFileName, origTok);

    this.allowEmptyFileSet = allowEmptyFileSet;
    this.includedFileName = includedFileName;
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    printIndent(stream, indent);
    stream.printf("include ");
    includedFileName.dump(stream, 0);
    stream.print(";\n");
  }

  @Override
  protected int reallyCompareTo(AQLParseTreeNode o) {
    IncludeFileNode includeFileNode = (IncludeFileNode) o;
    return this.includedFileName.compareTo(includeFileNode.includedFileName);
  }

  @Override
  public void qualifyReferences(Catalog catalog) {
    // No action

  }

}
