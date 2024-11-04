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
 * export dictionary
 * </pre>
 * 
 * statement.
 */
public class ExportDictNode extends AbstractExportNode {

  public ExportDictNode(NickNode nodeName, String containingFileName, Token origTok) {
    // set error location info
    super(nodeName, containingFileName, origTok);
  }

  @Override
  protected String getElementType() {
    return "dictionary";
  }

  /**
   * Validates whether the dictionary name used in 'export dictionary' statement is valid
   */
  @Override
  public List<ParseException> validate(Catalog catalog) {
    List<ParseException> errors = super.validate(catalog);

    String dictName = nodeName.getNickname();
    if (false == catalog.isValidDictionaryReference(dictName)) {

      errors.add(AQLParserBase.makeException(
          String.format("Dictionary '%s' not defined.", dictName), this.getOrigTok()));
    } else {

      // As of v2.0, we don't allow exporting of imported dictionaries
      try {
        if (true == catalog.isImportedDict(dictName)) {
          throw AQLParserBase.makeException(String.format(
              "'%s' is a reference to an imported dictionary. Re-exporting an imported dictionary is not allowed.",
              dictName), this.getOrigTok());
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
    nodeName = new NickNode(catalog.getQualifiedDictName(nodeName.getNickname()),
        nodeName.getContainingFileName(), nodeName.getOrigTok());
  }

}
