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
 * An item in the FROM clause of a select or select into statement. Currently, such an item can be a
 * table function call, a reference to a view, or a subquery.
 */
public abstract class FromListItemNode extends AbstractAQLParseTreeNode {

  public FromListItemNode(String containingFileName, Token origTok) {
    // set error location info
    super(containingFileName, origTok);
  }

  /**
   * Local name for this item of the FROM list, or null if no nickname was given.
   */
  NickNode alias = null;

  /**
   * Identifies whether the element referred in 'from' clause has an explicit alias set in AQL code.
   * This flag is used in validation code. See {@link SelectNode#validate(Catalog)}
   */
  protected boolean hasExplicitAlias = false;

  /** Add an alias to this from list */
  public void setAlias(NickNode alias) {
    this.alias = alias;

    if (null != alias) {
      hasExplicitAlias = true;
    }
  }

  public NickNode getAlias() {
    return alias;
  }

  @Override
  public List<ParseException> validate(Catalog catalog) {
    return super.validate(catalog);
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    // Subclasses override this method.
  }

  protected void dumpAlias(PrintWriter stream) {
    if (null != alias) {

      // Fix for defect : From list example from AQL Reference gives provenance rewrite error when I
      // output view
      // it. Need to escape the alias if it contains white space and other special characters...
      // stream.printf (" %s", alias.getNickname ());
      stream.print(" ");
      alias.dump(stream, 0);
    }
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
  public abstract String getScopedName();

  /**
   * @return the global name of the item represented by this entry
   */
  public abstract String getExternalName();

  /**
   * Determine which views this from list item depends on.
   * 
   * @param accum object for accumulating the views that this from list item depends on.
   * @param catalog AQL catalog pointer for looking up information that wasn't available at parse
   *        time.
   * @throws ParseException if an error in the AQL is detected during dependency tracking
   */
  public abstract void getDeps(TreeSet<String> accum, Catalog catalog) throws ParseException;
}
