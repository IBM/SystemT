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

import java.util.List;

import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.aql.compiler.ParseToCatalog;

/**
 * Top-level parse tree node for a
 * 
 * <pre>
 * export function
 * </pre>
 * 
 * statement.
 */
public class ExportFuncNode extends AbstractExportNode {

  public ExportFuncNode(NickNode nodeName, String containingFileName, Token origTok) {
    // set error location info
    super(nodeName, containingFileName, origTok);
  }

  @Override
  protected String getElementType() {
    return "function";
  }

  /**
   * Validates whether the function name used in 'export function' statement is valid
   */
  @Override
  public List<ParseException> validate(Catalog catalog) {
    List<ParseException> errors = super.validate(catalog);

    String funcName = nodeName.getNickname();

    boolean isScalarFunc = (null != catalog.lookupScalarFunc(funcName));
    boolean isTableFunc = (null != catalog.lookupTableFunc(funcName));

    if (!isScalarFunc && !isTableFunc) {
      errors.add(AQLParserBase.makeException(String.format("Function '%s' not defined.", funcName),
          this.getOrigTok()));
    } else {
      // As of v2.0, we don't support exporting of imported function
      try {
        if (((isScalarFunc) && catalog.isImportedScalarFunc(funcName))
            || ((isTableFunc) && catalog.isImportedTableFunc(funcName))) {
          throw AQLParserBase.makeException(String.format(
              "'%s' is a reference to an imported function. Re-exporting an imported function is not allowed.",
              funcName), this.getOrigTok());
        }
      } catch (ParseException e) {
        errors.add(e);
      }
    }

    return ParseToCatalog.makeWrapperException(errors, containingFileName);
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.avatar.aql.Node#qualifyReferences(com.ibm.avatar.aql.catalog.Catalog)
   */
  @Override
  public void qualifyReferences(Catalog catalog) {
    nodeName = new NickNode(catalog.getQualifiedFuncName(nodeName.getNickname()),
        nodeName.getContainingFileName(), nodeName.getOrigTok());
  }

}
