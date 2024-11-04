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
 * export view
 * </pre>
 * 
 * statement.
 */
public class ExportViewNode extends AbstractExportNode {

  public ExportViewNode(NickNode nodeName, String containingFileName, Token origTok) {
    // set error location info
    super(nodeName, containingFileName, origTok);
  }

  @Override
  protected String getElementType() {
    return "view";
  }

  /**
   * Validates whether the view name used in 'export view' statement is valid
   */
  @Override
  public List<ParseException> validate(Catalog catalog) {
    List<ParseException> errors = super.validate(catalog);

    String viewName = nodeName.getNickname();
    if (false == catalog.isValidViewReference(viewName)) {

      errors.add(AQLParserBase.makeException(String.format("View '%s' not defined.", viewName),
          this.getOrigTok()));
    } else {

      // As of v2.0, we don't support exporting of imported view
      try {
        if (true == catalog.isImportedView(viewName)) {
          throw AQLParserBase.makeException(String.format(
              "'%s' is a reference to an imported view. Re-exporting an imported view is not allowed. "
                  + "\n Define a new view whose body is "
                  + "'select * from importedViewName' and export that view instead.",
              viewName), this.getOrigTok());

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
    nodeName = new NickNode(catalog.getQualifiedViewOrTableName(nodeName.getNickname()),
        nodeName.getContainingFileName(), nodeName.getOrigTok());
  }
}
