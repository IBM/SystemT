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

import com.ibm.avatar.aql.catalog.Catalog;

/**
 * Parse tree node for the order by clause of a select statement. Currently, this is a list of
 * RValues.
 */
public class OrderByClauseNode extends AbstractAQLParseTreeNode implements NodeWithRefInfo {
  public OrderByClauseNode(String containingFileName, Token origTok) {
    // set error location info
    super(containingFileName, origTok);

  }

  /**
   * The top-level predicates that make up the where clause; these predicates must be connected by
   * logical AND in the query.
   */
  protected ArrayList<RValueNode> values = new ArrayList<RValueNode>();

  @Override
  public List<ParseException> validate(Catalog catalog) {
    return super.validate(catalog);
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    if (0 == values.size()) {
      return;
    }

    // No COMMA before the first predicate.
    values.get(0).dump(stream, 0);

    // Print remaining predicates with an COMMA before them.
    for (int i = 1; i < values.size(); i++) {
      stream.print(", ");

      // Use an indent of zero, since we're printing inline
      values.get(i).dump(stream, 0);
    }
  }

  /** Add a new column to the group by clause */
  public void addValue(RValueNode value) {
    values.add(value);
  }

  /**
   * Method for use by query rewrite for replacing one of the inputs to an order by clause with a
   * rewritten version.
   * 
   * @param i index of the value to replace
   * @param newVal replacement value to go at the indicated index.
   */
  public void replaceValue(int i, ScalarFnCallNode newVal) {
    if (i < 0 || i >= values.size()) {
      throw new RuntimeException(String.format(
          "Invalid index %d for order by clause (should be 0 to %d)", i, values.size() - 1));
    }
    values.set(i, newVal);
  }

  public int size() {
    return values.size();
  }

  public RValueNode getValue(int ix) {
    return values.get(ix);
  }

  public ArrayList<RValueNode> getValues() {
    return values;
  }

  @Override
  protected int reallyCompareTo(AQLParseTreeNode o) {
    OrderByClauseNode other = (OrderByClauseNode) o;
    return compareNodeLists(values, other.values);
  }

  @Override
  public void getReferencedCols(TreeSet<String> accum, Catalog catalog) throws ParseException {
    for (RValueNode rval : values) {
      rval.getReferencedCols(accum, catalog);
    }
  }

  @Override
  public void getReferencedViews(TreeSet<String> accum, Catalog catalog) throws ParseException {
    for (RValueNode rval : values) {
      rval.getReferencedViews(accum, catalog);
    }
  }

  @Override
  public void qualifyReferences(Catalog catalog) throws ParseException {
    for (RValueNode rval : values) {
      rval.qualifyReferences(catalog);
    }
  }

}
