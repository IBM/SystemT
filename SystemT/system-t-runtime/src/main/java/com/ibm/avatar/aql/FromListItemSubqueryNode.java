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
import java.util.TreeSet;

import com.ibm.avatar.aql.catalog.Catalog;

/**
 * An item in the FROM clause of a select or select into statement that is a subquery.
 */
public class FromListItemSubqueryNode extends FromListItemNode {

  /** The definition of the subquery */
  protected ViewBodyNode subquery;

  public FromListItemSubqueryNode(ViewBodyNode subquery, String containingFileName, Token origTok) {
    // Set error location info
    super(containingFileName, origTok);

    this.subquery = subquery;
  }

  @Override
  public List<ParseException> validate(Catalog catalog) {
    super.validate(catalog);
    return subquery.validate(catalog);
  }

  public void setBody(ViewBodyNode subquery) {
    this.subquery = subquery;
  }

  public ViewBodyNode getBody() {
    return subquery;
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    stream.print("\n");
    printIndent(stream, indent);
    stream.print("(");
    subquery.dump(stream, indent);
    stream.print("\n");
    printIndent(stream, indent);
    stream.print(")");

    dumpAlias(stream);
  }

  /**
   * @return the alias for this item in the context of the containing select statement. The parser
   *         disallows subqueries without aliases in the FROM clause, so the alias is never null.
   */
  @Override
  public String getScopedName() {
    return alias.getNickname();
  }

  /**
   * @return the global name of the item represented by this entry. Since this is a subquery, it
   *         does not have a global name, so we just return null for now.
   */
  @Override
  public String getExternalName() {
    return null;
  }

  @Override
  public String toString() {

    if (null == alias) {
      return String.format("( %s )", subquery.toString());
    } else {
      return String.format("( %s ) %s", subquery.toString(), alias.getNickname());
    }
  }

  @Override
  protected int reallyCompareTo(AQLParseTreeNode o) {
    FromListItemSubqueryNode other = (FromListItemSubqueryNode) o;

    int val = alias.compareTo(other.alias);
    if (val != 0) {
      return val;
    }

    return subquery.compareTo(other.subquery);

  }

  @Override
  public void getDeps(TreeSet<String> accum, Catalog catalog) throws ParseException {
    subquery.getDeps(accum, catalog);
  }

  @Override
  public void qualifyReferences(Catalog catalog) throws ParseException {
    subquery.qualifyReferences(catalog);

  }
}
