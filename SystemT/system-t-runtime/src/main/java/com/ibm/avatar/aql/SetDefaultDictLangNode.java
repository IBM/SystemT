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
import java.util.List;

import com.ibm.avatar.aql.catalog.Catalog;

/**
 * Top-level parse tree node to represent
 * 
 * <pre>
 *  set default dictionary language as '<list of language codes>'
 * </pre>
 * 
 * statement.
 */
public class SetDefaultDictLangNode extends TopLevelParseTreeNode {

  /** Default language code string; to be used for dictionary matching */
  private final String defaultLangString;

  /**
   * Constructor to create parse tree instance for 'set default dictionary ...' statement
   * 
   * @param defaultLangString
   * @param containinFileName
   * @param origTok
   */
  public SetDefaultDictLangNode(String defaultLangString, String containingFileName,
      Token origTok) {
    // set error location info
    super(containingFileName, origTok);

    this.defaultLangString = defaultLangString;
  }

  /** Returns the default language code string */
  public String getDefaultLangString() {
    return defaultLangString;
  }

  @Override
  public List<ParseException> validate(Catalog catalog) {
    // Validations are performed while adding the node to catalog
    return super.validate(catalog);
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    printIndent(stream, indent);
    stream.printf("set default dictionary language as '%s'", this.defaultLangString);
    stream.print(";\n");
  }

  @Override
  protected int reallyCompareTo(AQLParseTreeNode o) {
    return this.defaultLangString.compareTo(((SetDefaultDictLangNode) o).getDefaultLangString());
  }

  @Override
  public void qualifyReferences(Catalog catalog) {
    // No action

  }
}
