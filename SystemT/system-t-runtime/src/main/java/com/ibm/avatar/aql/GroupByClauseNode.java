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
 * Parse tree node for the group by clause of a select statement. Currently, this is a list of
 * RValues.
 */
public class GroupByClauseNode extends AbstractAQLParseTreeNode implements NodeWithRefInfo {
  public GroupByClauseNode(String containingFileName, Token origTok) {
    // set error location info
    super(containingFileName, origTok);

  }

  /**
   * The expressions that make up the group by clause.
   */
  protected ArrayList<RValueNode> values = new ArrayList<RValueNode>();

  @Override
  public List<ParseException> validate(Catalog catalog) {
    return validateGroupByList(catalog);
  }

  /**
   * Checks that the GROUP BY list contains only column references and scalar functions.
   * 
   * @throws ParseException
   */
  private List<ParseException> validateGroupByList(Catalog catalog) {

    List<ParseException> errorList = new ArrayList<ParseException>();

    ScalarFnCallNode func;

    for (RValueNode groupByVal : getValues()) {

      // It's a column, nothing to do here
      if (groupByVal instanceof ColNameNode) {
      }
      // It's a function, check if it's scalar
      else if (groupByVal instanceof ScalarFnCallNode) {
        func = (ScalarFnCallNode) groupByVal;

        if (catalog.lookupScalarFunc(func.getFuncName()) == null)
          errorList.add(AQLParserBase.makeException(func.getOrigTok(),
              "Non scalar Function %s is not allowed in GROUP BY clause.", func.getFuncName()));
      }
      // Anything else causes an exception.
      else
        errorList.add(AQLParserBase.makeException(groupByVal.getOrigTok(),
            "Value %s is not allowed in GROUP BY clause.", groupByVal));
    }

    return errorList;
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    if (0 == values.size()) {
      return;
    }

    stream.print("");

    for (int i = 0; i < values.size(); i++) {
      values.get(i).dump(stream, 0);
      if (i < values.size() - 1) {
        stream.print(", ");
      }
    }
  }

  /** Add a new top-level predicate to the clause */
  public void addValue(RValueNode value) {
    values.add(value);
  }

  /**
   * Method for use by query rewrite for replacing one of the inputs to a group by clause with a
   * rewritten version.
   * 
   * @param i index of the value to replace
   * @param newVal replacement value to go at the indicated index.
   */
  public void replaceValue(int i, ScalarFnCallNode newVal) {
    if (i < 0 || i >= values.size()) {
      throw new RuntimeException(String.format(
          "Invalid index %d for group by clause (should be 0 to %d)", i, values.size() - 1));
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
    GroupByClauseNode other = (GroupByClauseNode) o;
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
