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
 * Parse tree node for an EXTRACT statement.
 */
public class ExtractNode extends ViewBodyNode {

  private final ExtractListNode extractList;

  private FromListItemNode target;

  private final HavingClauseNode havingClause;

  private final ConsolidateClauseNode consolidateClause;

  /**
   * Limit on the number of tuples returned, or null if the statement had no LIMIT clause.
   */
  private IntNode maxTup = null;

  public ExtractNode(ExtractListNode extractList, FromListItemNode target,
      HavingClauseNode havingClause, ConsolidateClauseNode consolidateClause, IntNode maxTup,
      String containingFileName, Token origTok) throws ParseException {
    // set error location info
    super(containingFileName, origTok);

    this.extractList = extractList;
    this.target = target;
    this.havingClause = havingClause;
    this.consolidateClause = consolidateClause;
    this.maxTup = maxTup;
  }

  @Override
  public List<ParseException> validate(Catalog catalog) {
    List<ParseException> errors = new ArrayList<ParseException>();

    if (null != this.target) {
      List<ParseException> targetErrors = this.target.validate(catalog);
      if (null != targetErrors && targetErrors.size() > 0)
        errors.addAll(targetErrors);
    }

    if (null != this.havingClause) {
      List<ParseException> havingClauseErrors = this.havingClause.validate(catalog);
      if (null != havingClauseErrors && havingClauseErrors.size() > 0)
        errors.addAll(havingClauseErrors);
    }

    if (null != this.extractList) {
      List<ParseException> extractListErrors = this.extractList.validate(catalog);
      if (null != extractListErrors && extractListErrors.size() > 0)
        errors.addAll(extractListErrors);
    }

    if (null != this.consolidateClause) {
      List<ParseException> consolidateClauseErrors = this.consolidateClause.validate(catalog);
      if (null != consolidateClauseErrors && consolidateClauseErrors.size() > 0)
        errors.addAll(consolidateClauseErrors);
    }
    return errors;
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    stream.print("extract ");
    extractList.dump(stream, indent + 1);

    // FROM clause
    printIndent(stream, indent);
    stream.print("from ");
    target.dump(stream, 0);

    // Optional HAVING clause
    if (null != havingClause) {
      stream.print("\n");
      printIndent(stream, indent);
      stream.print("having ");
      havingClause.dump(stream, indent + 1);
    }

    // Optional CONSOLIDATE clause
    if (null != consolidateClause) {
      stream.print("\n");
      printIndent(stream, indent);
      stream.print("consolidate on ");
      consolidateClause.dump(stream, 0);
    }

    // Optional LIMIT clause
    if (null != maxTup) {
      stream.print("\n");
      printIndent(stream, indent);
      stream.print("limit ");
      maxTup.dump(stream, 0);
    }
  }

  /**
   * @return line in the AQL input at which this select statement started
   */
  public int getLine() {
    return origTok.beginLine;
  }

  @Override
  protected int reallyCompareTo(AQLParseTreeNode o) {
    ExtractNode other = (ExtractNode) o;
    int val = extractList.compareTo(other.extractList);
    if (val != 0) {
      return val;
    }
    val = target.compareTo(other.target);
    return val;
  }

  @Override
  public void getDeps(TreeSet<String> accum, Catalog catalog) throws ParseException {
    target.getDeps(accum, catalog);

    // The having clause can add additional dependencies
    if (null != havingClause) {
      for (int i = 0; i < havingClause.size(); i++) {
        PredicateNode pred = havingClause.getPred(i);
        pred.getReferencedViews(accum, catalog);
      }
    }
  }

  public ExtractListNode getExtractList() {
    return this.extractList;
  }

  public FromListItemNode getTarget() {
    return target;
  }

  public void setTarget(FromListItemNode target) {
    this.target = target;
  }

  public HavingClauseNode getHavingClause() {
    return havingClause;
  }

  public ConsolidateClauseNode getConsolidateClause() {
    return consolidateClause;
  }

  public IntNode getMaxTups() {
    return maxTup;
  }

  /**
   * Expand wildcards and infer missing aliases in the select list of this node. Does not recurse to
   * subqueries, and assumes wild card expansion and alias inference for subqueries has been already
   * done. This method is called from {@link ViewBodyNode#expandWildcardAndInferAlias(Catalog)}
   * which recurses to subqueries prior to calling this method.
   * 
   * @param catalog
   * @throws ParseException
   */
  protected void expandWildcardAndInferAliasLocal(Catalog catalog) throws ParseException {

    // Construct a dummy FromListNode to pass to the actual method that does the work
    ArrayList<FromListItemNode> items = new ArrayList<FromListItemNode>();
    items.add(target);
    FromListNode fromList = new FromListNode(items, target.containingFileName, target.getOrigTok());

    // All the work is done in the ExtractListNode
    extractList.expandWildcardAndInferAliasLocal(fromList, catalog);
  }

  @Override
  public void qualifyReferences(Catalog catalog) throws ParseException {
    extractList.qualifyReferences(catalog);
    target.qualifyReferences(catalog);

    if (havingClause != null) {
      havingClause.qualifyReferences(catalog);
    }

    if (consolidateClause != null) {
      consolidateClause.qualifyReferences(catalog);
    }
  }

}
