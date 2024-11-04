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
 * Parse tree node for the where clause of a select statement. Currently, only conjunctive WHERE
 * clauses are supported.
 */
public class WhereClauseNode extends AbstractAQLParseTreeNode implements NodeWithRefInfo {
  public WhereClauseNode(String containingFileName, Token origTok) {
    // set error location info
    super(containingFileName, origTok);

  }

  /**
   * The top-level predicates that make up the where clause; these predicates must be connected by
   * logical AND in the query.
   */
  protected ArrayList<PredicateNode> preds = new ArrayList<PredicateNode>();

  @Override
  public List<ParseException> validate(Catalog catalog) {
    List<ParseException> errors = new ArrayList<ParseException>();

    for (PredicateNode pred : preds) {
      List<ParseException> predParseError = pred.validate(catalog);
      if (null != predParseError && predParseError.size() > 0)
        errors.addAll(predParseError);
    }
    return errors;
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    if (0 == preds.size()) {
      return;
    }

    // No AND before the first predicate.
    preds.get(0).dump(stream, 0);

    // Print remaining predicates with an AND before them.
    for (int i = 1; i < preds.size(); i++) {
      stream.print("\n");
      printIndent(stream, indent);
      stream.print(" and ");

      // Use an indent of zero, since we're printing inline
      preds.get(i).dump(stream, 0);
    }
  }

  /** Add a new top-level predicate to the [conjunctive] where clause */
  public void addPred(PredicateNode pred) {
    preds.add(pred);
  }

  public int size() {
    return preds.size();
  }

  public PredicateNode getPred(int ix) {
    return preds.get(ix);
  }

  public ArrayList<PredicateNode> getPreds() {
    return preds;
  }

  public void setPreds(ArrayList<PredicateNode> preds) {
    this.preds = preds;
  }

  @Override
  public void getReferencedCols(TreeSet<String> accum, Catalog catalog) throws ParseException {
    for (int i = 0; i < size(); i++) {
      getPred(i).getReferencedCols(accum, catalog);
    }
  }

  @Override
  public void getReferencedViews(TreeSet<String> accum, Catalog catalog) throws ParseException {
    for (int i = 0; i < size(); i++) {
      getPred(i).getReferencedViews(accum, catalog);
    }
  }

  @Override
  protected int reallyCompareTo(AQLParseTreeNode o) {
    WhereClauseNode other = (WhereClauseNode) o;
    return compareNodeLists(preds, other.preds);
  }

  @Override
  public void qualifyReferences(Catalog catalog) throws ParseException {
    for (PredicateNode pred : preds) {
      pred.qualifyReferences(catalog);
    }
  }

}
