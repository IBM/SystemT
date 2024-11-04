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
 * An item in the FROM clause of a select or select into statement that calls a table function.
 */
public class FromListItemTableFuncNode extends FromListItemNode {

  /** The table function call */
  private TableFnCallNode tabfunc = null;

  public FromListItemTableFuncNode(TableFnCallNode tabfunc) {
    // Set error location info
    super(tabfunc.getContainingFileName(), tabfunc.getOrigTok());

    this.tabfunc = tabfunc;
    this.alias = new NickNode(tabfunc.getFuncName());
  }

  public TableFnCallNode getTabfunc() {
    return tabfunc;
  }

  @Override
  public List<ParseException> validate(Catalog catalog) {
    return super.validate(catalog);
  }

  @Override
  public void dump(PrintWriter stream, int indent) {

    tabfunc.dump(stream, indent);

    dumpAlias(stream);
  }

  /**
   * Generate the appropriate AOG code for fetching this item.
   */
  // public void generateAOG(PrintStream stream, int indent)
  // throws ParseException {
  // if (null != view) {
  // // This item is a reference to a view.
  // String viewname = view.getNickname();
  //
  // printIndent(stream, indent);
  // stream.printf("%s", AnnotPlan.toAOGNick(viewname));
  //
  // } else {
  // // Item must be a table function
  // tabfunc.generateAOG(stream, indent);
  // }
  // }

  /**
   * @return the alias for this item in the context of the containing select statement, or a
   *         generated name if no alias was given in the query
   */
  @Override
  public String getScopedName() {
    if (null != alias) {
      return alias.getNickname();
    } else
      return tabfunc.getFuncName();
  }

  /**
   * @return the global name of the item represented by this entry
   */
  @Override
  public String getExternalName() {
    return tabfunc.getFuncName();
  }

  @Override
  public String toString() {

    if (null == alias) {
      return tabfunc.toString();
    } else {
      return String.format("%s %s", tabfunc.toString(), alias.getNickname());
    }
  }

  @Override
  protected int reallyCompareTo(AQLParseTreeNode o) {
    FromListItemTableFuncNode other = (FromListItemTableFuncNode) o;

    int val = alias.compareTo(other.alias);
    if (val != 0) {
      return val;
    }

    return tabfunc.compareTo(other.tabfunc);

  }

  @Override
  public void getDeps(TreeSet<String> accum, Catalog catalog) throws ParseException {
    TableFnCallNode fn = getTabfunc();
    fn.getDeps(accum, catalog);
  }

  @Override
  public void qualifyReferences(Catalog catalog) throws ParseException {
    tabfunc.qualifyReferences(catalog);
  }
}
