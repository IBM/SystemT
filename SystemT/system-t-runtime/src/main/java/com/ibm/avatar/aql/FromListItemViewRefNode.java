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
 * An item in the FROM clause of a select or select into statement that references a view.
 */
public class FromListItemViewRefNode extends FromListItemNode {

  /** Name of the referenced view */
  private NickNode view = null;

  /**
   * In the case of a non-imported view, it holds the fully qualified name of the referenced view.
   * In the case of an imported view, it holds the fully qualified name of the original imported
   * view. This attribute is set by {@link #qualifyReferences(Catalog)} method.
   */
  private NickNode origView = null;

  private boolean check = false;

  public FromListItemViewRefNode(NickNode view) throws ParseException {

    this(view, true);
  }

  /**
   * This constructor is for when you want to disable checks
   * 
   * @param view name of a view
   * @param check TRUE to check whether the view exists
   * @throws ParseException
   */
  public FromListItemViewRefNode(NickNode view, boolean check) throws ParseException {
    super(view.getContainingFileName(), view.getOrigTok());

    this.view = view;
    this.check = check;
    // Set the alias to the name of the view until we hear otherwise
    this.alias = view;
    this.origView = view;

  }

  @Override
  public List<ParseException> validate(Catalog catalog) {
    List<ParseException> errors = super.validate(catalog);

    if (check) {
      // Make sure that the view / table referenced actually exists.
      String viewName = view.getNickname();
      if (false == catalog.isValidViewReference(viewName)) {
        errors.add(AQLParserBase.makeException(view.getOrigTok(),
            "View or Table '%s', referenced in FROM clause, is not a valid reference. Ensure that the View or Table is defined and is visible in the current module, accessible by the given name.",
            view.getNickname()));
      }

    }

    return errors;
  }

  public NickNode getViewName() {
    return view;
  }

  /**
   * Returns the original view name. See {@link #origView} for details.
   * 
   * @return original view name.
   */
  public NickNode getOrigViewName() {
    return origView;
  }

  @Override
  public void dump(PrintWriter stream, int indent) {

    view.dump(stream, indent);

    dumpAlias(stream);
  }

  /**
   * @return the alias for this item in the context of the containing select statement, or a
   *         generated name if no alias was given in the query
   */
  @Override
  public String getScopedName() {
    if (null != alias) {
      return alias.getNickname();
    } else
      return view.getNickname();
  }

  /**
   * @return the global name of the item represented by this entry
   */
  @Override
  public String getExternalName() {

    return view.getNickname();
  }

  @Override
  public String toString() {

    if (null == alias) {
      return view.getNickname();
    } else {
      return String.format("%s %s", view.getNickname(), alias.getNickname());
    }
  }

  @Override
  protected int reallyCompareTo(AQLParseTreeNode o) {
    FromListItemViewRefNode other = (FromListItemViewRefNode) o;

    int val = alias.compareTo(other.alias);
    if (val != 0) {
      return val;
    }

    return view.compareTo(other.view);
  }

  @Override
  public void getDeps(TreeSet<String> accum, Catalog catalog) {
    accum.add(getViewName().getNickname());
  }

  @Override
  public void qualifyReferences(Catalog catalog) {
    origView = new NickNode(catalog.getQualifiedViewOrTableName(origView.getNickname()),
        origView.getContainingFileName(), origView.getOrigTok());
  }
}
