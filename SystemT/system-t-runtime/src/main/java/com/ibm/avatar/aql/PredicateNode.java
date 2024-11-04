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
import com.ibm.avatar.logging.Log;

/**
 * Parse tree node for a predicate in the where clause of a select statement in AvatarSQL.
 * Currently, the where clause is a conjunction of function evaluation predicates.
 */
public class PredicateNode extends AbstractAQLParseTreeNode
    implements Comparable<AQLParseTreeNode>, NodeWithRefInfo {

  private ScalarFnCallNode func = null;

  /**
   * Constructor for a predicate that evaluates a function. Function must return a boolean value.
   */
  public PredicateNode(ScalarFnCallNode func, String containingFileName, Token origTok) {
    // Set error location info
    super(containingFileName, origTok);

    this.func = func;
  }

  @Override
  public List<ParseException> validate(Catalog catalog) {
    return func.validate(catalog);
  }

  @Override
  public void dump(PrintWriter stream, int indent) {

    // Only function evaluations are supported currently.
    func.dump(stream, indent);
  }

  @Override
  public String toString() {

    if (null != func) {
      // If this predicate is a function call, have the function
      // pretty-print itself.
      return func.toString();
    } else {
      return super.toString();
    }
  }

  /**
   * @return true if this predicate is "syntactic sugar" that doesn't actually generate any plan
   *         nodes. The primary example of such a predicate is the SameDoc() predicate.
   */
  public boolean isIgnored() {
    final String SAMEDOC_NAME = "SameDoc";
    if (SAMEDOC_NAME.equals(func.getFuncName())) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * @return true if this predicate consists of a function call at its top level
   */
  public boolean isFuncCall() {
    // We don't have any other type of predicate at the moment.
    return true;
  }

  public ScalarFnCallNode getFunc() {
    return this.func;
  }

  /**
   * Replace the function call in this method with a new value; used during query rewrite.
   */
  public void replaceFunc(ScalarFnCallNode newFunc) {
    func = newFunc;
  }


  @Override
  public void getReferencedCols(TreeSet<String> accum, Catalog catalog) throws ParseException {
    func.getReferencedCols(accum, catalog);
  }

  @Override
  public void getReferencedViews(TreeSet<String> accum, Catalog catalog) throws ParseException {
    func.getReferencedViews(accum, catalog);
  }

  /**
   * @param outerRels relations represented in the outer of a hypothetical merge join
   * @param innerRels inner relations
   * @param catalog catalog ptr for looking up metadata
   * @return true if this function can act directly as the join predicate between the indicated two
   *         sets of relations.
   * @throws ParseException
   */
  public boolean coversMergeJoin(TreeSet<FromListItemNode> outerRels,
      TreeSet<FromListItemNode> innerRels, Catalog catalog) throws ParseException {
    if (null == func) {
      return false;
    }
    return func.coversMergeJoin(outerRels, innerRels, catalog);
  }

  /**
   * @param outerRels relations represented in the outer of a hypothetical hash join
   * @param innerRels inner relations
   * @param catalog catalog ptr for looking up metadata
   * @return true if this function can act directly as the join predicate between the indicated two
   *         sets of relations.
   * @throws ParseException
   */
  public boolean coversHashJoin(TreeSet<FromListItemNode> outerRels,
      TreeSet<FromListItemNode> innerRels, Catalog catalog) throws ParseException {
    if (null == func) {
      return false;
    }
    return func.coversHashJoin(outerRels, innerRels, catalog);
  }

  /**
   * @param outerRels the relations represented on the outer of a join
   * @param innerRels relations represented on the inner of the join
   * @param catalog pointer to the AQL catalog, for looking up metadata
   * @return true if this function call can be implemented as a basic RSE join
   */
  public boolean coversRSEJoin(TreeSet<FromListItemNode> outerRels,
      TreeSet<FromListItemNode> innerRels, Catalog catalog) throws ParseException {
    if (null == func) {
      return false;
    }
    return func.coversRSEJoin(outerRels, innerRels, catalog);
  }

  public boolean hasMergeImpl() {
    if (null == func) {
      return false;
    } else {
      return func.hasMergeImpl();
    }
  }

  /**
   * Convert a (merge-join-compatible) predicate into a version with inner and outer arguments
   * reversed in order.
   */
  public PredicateNode reverse(Catalog c) {

    final boolean debug = false;

    PredicateNode ret = null;
    ScalarFnCallNode reversed = func.getReversePred(c);

    // some functions are not reversible, so this node can be null
    if (reversed != null) {
      ret = new PredicateNode(reversed, reversed.getContainingFileName(), reversed.getOrigTok());
    }

    if (debug) {
      Log.debug("Reversed %s to %s", this, ret);
    }

    return ret;
  }

  @Override
  public int reallyCompareTo(AQLParseTreeNode n) {
    PredicateNode pred = (PredicateNode) n;
    return func.compareTo(pred.func);
  }

  @Override
  public void qualifyReferences(Catalog catalog) throws ParseException {
    func.qualifyReferences(catalog);
  }

}
