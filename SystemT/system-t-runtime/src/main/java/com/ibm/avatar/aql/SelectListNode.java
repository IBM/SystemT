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
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.exceptions.CatalogEntryNotFoundException;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.aql.catalog.Catalog;

/** Parse tree node for the SELECT list in a select or select into statement. */
public class SelectListNode extends AbstractAQLParseTreeNode implements NodeWithRefInfo {

  boolean isSelectStar = false;

  private ArrayList<SelectListItemNode> items;

  /**
   * Constructor for the list "*", as in "select * from..."
   */
  public SelectListNode(String containingFileName, Token origTok) {
    // set error location info
    super(containingFileName, origTok);

    this.isSelectStar = true;
  }

  /**
   * Constructor for the normal case list.
   */
  public SelectListNode(ArrayList<SelectListItemNode> items, String containingFileName,
      Token origTok) {
    // set error location info
    super(containingFileName, origTok);

    this.items = items;
  }

  @Override
  public List<ParseException> validate(Catalog catalog) {
    List<ParseException> errors = new ArrayList<ParseException>();

    if (null != this.items) {
      for (SelectListItemNode selectListNode : this.items) {
        List<ParseException> selectNodeErrors = selectListNode.validate(catalog);
        if (null != selectNodeErrors && selectNodeErrors.size() > 0)
          errors.addAll(selectNodeErrors);
      }
    }

    return errors;
  }

  /**
   * Verify that the select list does not contain any aggregate function calls. This method is used
   * when validating a PatternNode, and should also be used when validating an ExtractNode.
   * 
   * @param catalog
   * @return
   */
  protected List<ParseException> ensureNoAggFunc(Catalog catalog) {
    List<ParseException> errors = new ArrayList<ParseException>();

    if (null != this.items) {
      for (SelectListItemNode selectListNode : this.items) {
        RValueNode value = selectListNode.value;

        // If the value is a function node, make sure it is not an aggregate function call, not has
        // any aggregate
        // function calls underneath.
        if (value instanceof ScalarFnCallNode) {
          List<ParseException> selectNodeErrors =
              ((ScalarFnCallNode) value).ensureNoAggFunc(catalog);
          if (null != selectNodeErrors && selectNodeErrors.size() > 0)
            errors.addAll(selectNodeErrors);
        }
      }
    }

    return errors;
  }

  /**
   * Expand any wildcards in the select list. This method is used for wildcard expansion in
   * {@link SelectNode}, {@link ExtractPatternNode} and {@link ExtractNode}. Among these, only the
   * former allows for the construct "select *" but nevertheless, we expand any single * anyways for
   * the other two, since it cannot hurt.
   * 
   * @param fromList
   * @param catalog
   * @return the new list of select items, constructed via expansion
   * @throws ParseException
   */
  protected ArrayList<SelectListItemNode> expandWildcards(FromListNode fromList, Catalog catalog)
      throws ParseException {
    if (this.getIsSelectStar()) {
      // select * - only encountered for Select Node. The parser does not allow extract * (i.e., an
      // ExtrcatNode or
      // PatternNode will never have a select list consisting of a single *, but we perform the *
      // expansion anyways in
      // order to reuse the same method across invocations from {@link SelectNode}, {@link
      // PatternNode} and {@link
      // ExtractNode}.
      ArrayList<SelectListItemNode> newItems = new ArrayList<SelectListItemNode>();

      // Pull column names out of the input views.
      for (int i = 0; i < fromList.size(); i++) {
        FromListItemNode fromItem = fromList.get(i);

        // Check to see if we are selecting * from Document, which is illegal.
        if (fromItem instanceof FromListItemViewRefNode) {
          if (((FromListItemViewRefNode) fromItem).getOrigViewName().getNickname()
              .equals(Constants.DEFAULT_DOC_TYPE_NAME)) {
            throw AQLParserBase.makeException(fromItem.getOrigTok(),
                "The statement 'select * from Document' is not supported. "
                    + "Rewrite the select clause by specifying required fields from Document view.");
          }
        }

        ArrayList<String> colNames = catalog.getColNames(fromItem);
        for (String name : colNames) {
          newItems.add(new SelectListItemNode(
              new ColNameNode(new NickNode(fromItem.getScopedName()), new NickNode(name)), null));
        }
      }

      // Return the new list of select items.
      return newItems;
    } else if (this.containsWildcard()) {
      // select A.*
      // or select Foo.bar, A.*, Fab.baz

      ArrayList<SelectListItemNode> newItems = new ArrayList<SelectListItemNode>();

      for (int i = 0; i < items.size(); i++) {
        SelectListItemNode origItem = items.get(i);
        if (origItem.getIsDotStar()) {
          // Wildcards get expanded.
          String tabName = ((NickNode) origItem.getValue()).getNickname();
          FromListItemNode fromItem = fromList.getByName(tabName);

          if (null == fromItem) {
            Token tok = origItem.getValue().getOrigTok();
            // Log.debug("Line %d",tok.beginLine);
            throw AQLParserBase.makeException(tok,
                "Name '%s' of select list reference '%s.*' not found in from list.", tabName,
                tabName);
          }

          // Check to see if we are selecting * from Document, which is illegal.
          if (fromItem instanceof FromListItemViewRefNode) {
            if (((FromListItemViewRefNode) fromItem).getOrigViewName().getNickname()
                .equals(Constants.DEFAULT_DOC_TYPE_NAME)) {
              throw AQLParserBase.makeException(fromItem.getOrigTok(),
                  "The statement 'select %s.* from Document %s' is not supported. "
                      + "Rewrite the select clause by specifying required fields from Document view.",
                  tabName, tabName);
            }
          }

          try {
            ArrayList<String> colNames = catalog.getColNames(fromItem);

            for (String name : colNames) {
              newItems.add(new SelectListItemNode(
                  new ColNameNode(new NickNode(fromItem.getScopedName()), new NickNode(name)),
                  null));
            }
          } catch (CatalogEntryNotFoundException e) {
            // Ignore the exception as it is an internal exception and we don't want this to bubble
            // up to the user
          }

        } else {
          // Normal entries get passed through.
          newItems.add(origItem);
        }
      }

      // Return the new list of items, after expansion.
      return newItems;
    } else {

      // We did not find any wildcards. Just return the current list of select items.
      return items;
    }
  }

  /**
   * Generate any missing aliases for elements of the select list where the user typed "View.col"
   * instead of "View.col as name". Protected because it is called from ViewCatalogEntry. Assumes
   * that all wildcards have been expanded.
   */
  protected void inferAliases() throws ParseException {
    for (int i = 0; i < items.size(); i++) {
      SelectListItemNode item = items.get(i);
      if (null == item.getAlias()) {
        RValueNode value = item.getOrigValue();

        // We can only generate names if the item is in the form
        // "view.col" or is a function call
        if (value instanceof ColNameNode) {
          item.setAlias(((ColNameNode) value).getColnameInTable());
        } else if (value instanceof ScalarFnCallNode) {
          item.setAlias(((ScalarFnCallNode) value).getFuncName());
        } else {
          // this rvalue needs an alias
          // Due to defect , node validation no longer throws the exception, so handle it here
          throw AQLParserBase.makeException(value.getOrigTok(),
              "'as (name)' is required after select list item: %s", value.toString());
        }
      }
    }
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    if (isSelectStar) {
      printIndent(stream, 0);
      stream.print("*");
    } else {
      for (int i = 0; i < items.size(); i++) {
        items.get(i).dump(stream, (i == 0) ? 0 : indent);
        if (i < items.size() - 1) {
          stream.print(",\n");
        }
      }
    }
  }

  public int size() {
    if (null == items) {
      return 0;
    }
    return items.size();
  }

  public SelectListItemNode get(int i) {
    return items.get(i);
  }

  public void add(SelectListItemNode node) {
    items.add(node);
  }

  public boolean getIsSelectStar() {
    return isSelectStar;
  }

  @Override
  protected int reallyCompareTo(AQLParseTreeNode o) {
    SelectListNode other = (SelectListNode) o;

    // Select * ranks higher.
    int val = Boolean.valueOf(isSelectStar).compareTo(Boolean.valueOf(other.isSelectStar));
    if (0 != val) {
      return val;
    }

    return compareNodeLists(items, other.items);
  }

  /**
   * @return schema of tuples that this select list would produce
   */
  public TupleSchema toSchema() {

    int numCols = size();

    String[] colNames = new String[numCols];
    FieldType[] colTypes = new FieldType[numCols];

    // Pull output column names from the select list.
    for (int i = 0; i < numCols; i++) {
      SelectListItemNode item = get(i);

      colNames[i] = item.getAlias();
    }

    // For now, we generate a schema with unknown output types.
    // TODO: Add proper type information.
    for (int i = 0; i < numCols; i++) {
      colTypes[i] = FieldType.NULL_TYPE;
    }

    TupleSchema ret = new TupleSchema(colNames, colTypes);
    return ret;
  }

  /**
   * @return true if any of the items in this list is a wildcard expression like A.*
   */
  public boolean containsWildcard() {

    for (SelectListItemNode item : items) {
      if (item.getIsDotStar()) {
        return true;
      }
    }

    return false;
  }

  public void remove(int i) {
    items.remove(i);

  }

  @Override
  public void getReferencedCols(TreeSet<String> accum, Catalog catalog) throws ParseException {
    for (SelectListItemNode item : items) {
      item.getReferencedCols(accum, catalog);
    }
  }

  @Override
  public void getReferencedViews(TreeSet<String> accum, Catalog catalog) throws ParseException {
    for (SelectListItemNode item : items) {
      item.getReferencedViews(accum, catalog);
    }
  }

  @Override
  public void qualifyReferences(Catalog catalog) throws ParseException {
    if (items != null) {
      for (SelectListItemNode item : items) {
        if (item != null) {
          item.qualifyReferences(catalog);
        }
      }
    }
  }

}
