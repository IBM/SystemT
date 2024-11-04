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
import java.util.HashMap;
import java.util.List;

import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.aql.planner.AQLStatementValidator;

/** Parse tree node for the FROM list in a select or select into clause */
public class FromListNode extends AbstractAQLParseTreeNode {

  private final ArrayList<FromListItemNode> items;

  public FromListNode(ArrayList<FromListItemNode> items, String containingFileName, Token origTok) {
    // set error location info
    super(containingFileName, origTok);

    this.items = items;
  }

  /**
   * Shallow validation of the From list. Currently validates each item individually. Validation
   * that each alias is unique in the context of the FROM list node is done in
   * {@link AQLStatementValidator#validateSelect()} and
   * {@link AQLStatementValidator#validatePattern()}.
   */
  @Override
  public List<ParseException> validate(Catalog catalog) {
    List<ParseException> errors = new ArrayList<ParseException>();

    // Check for competing aliases.
    HashMap<String, Integer> aliasToPosition = new HashMap<String, Integer>();
    for (int i = 0; i < items.size(); i++) {
      FromListItemNode item = items.get(i);
      String aliasStr = item.alias.getNickname();
      if (aliasToPosition.containsKey(aliasStr)) {
        int otherPosition = aliasToPosition.get(aliasStr);
        errors.add(AQLParserBase.makeException(item.getOrigTok(),
            "Items %d and %d (first index is 0) of from list have the same alias.  "
                + "Please change the alias of one of the items.",
            otherPosition, i));
      } else {
        aliasToPosition.put(aliasStr, i);
      }
    }

    // Collect a list of errors
    for (FromListItemNode fromListItem : items) {

      // Validate the FromListItem node itself
      List<ParseException> fromListItemNodeErrors = fromListItem.validate(catalog);
      if (null != fromListItemNodeErrors && fromListItemNodeErrors.size() > 0)
        errors.addAll(fromListItemNodeErrors);
    }

    return errors;
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    for (int i = 0; i < items.size(); i++) {
      FromListItemNode node = items.get(i);

      // subqueries should maintain the indentation
      if (node instanceof FromListItemSubqueryNode) {
        node.dump(stream, (i == 0 ? indent : indent + 2));
      } else {
        node.dump(stream, (i == 0 ? 0 : indent + 2));
      }
      if (i < items.size() - 1) {
        stream.print(",\n");
      }
    }
  }

  public FromListItemNode get(int i) {
    return items.get(i);
  }

  public void set(int index, FromListItemNode item) {
    items.set(index, item);
  }

  public void add(FromListItemNode item) {
    items.add(item);
  }

  public ArrayList<FromListItemNode> getItems() {
    return items;
  }

  public int size() {
    return items.size();
  }

  @Override
  protected int reallyCompareTo(AQLParseTreeNode o) {
    FromListNode other = (FromListNode) o;
    return compareNodeLists(items, other.items);
  }

  public FromListItemNode[] toArray() {
    FromListItemNode[] ret = new FromListItemNode[items.size()];
    return items.toArray(ret);
  }

  /**
   * @param localTabName an internal nickname (in the statement's scope)
   * @return the from list entry for the indicated internal name, or null if the name is not found.
   */
  public FromListItemNode getByName(String localTabName) {
    if (null == localTabName) {
      throw new FatalInternalError("FromListNode.getByName() called with null argument");
    }
    for (FromListItemNode node : items) {
      if (localTabName.equals(node.getScopedName())) {
        return node;
      }
    }
    return null;
  }

  @Override
  public void qualifyReferences(Catalog catalog) throws ParseException {
    for (FromListItemNode item : items) {
      item.qualifyReferences(catalog);
    }
  }

}
