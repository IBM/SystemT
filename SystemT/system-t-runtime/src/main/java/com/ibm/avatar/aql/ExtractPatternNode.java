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
import java.util.TreeSet;

import com.ibm.avatar.api.Constants;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.aql.compiler.ParseToCatalog;

/**
 * Parse tree node for an EXTRACT PATTERN statement.
 */
public class ExtractPatternNode extends ViewBodyNode {

  /* CONSTANTS */
  /**
   * Default view name used for the with inline_match clause, whenever the user did not specify the
   * clause
   */
  public static final String DEFAULT_WITH_INLINE_MATCH_VIEW_NAME = Constants.DEFAULT_DOC_TYPE_NAME;

  /**
   * Default column name used for the with inline_match clause, whenever the user did not specify
   * the clause
   */
  public static final String DEFAULT_WITH_INLINE_MATCH_COL_NAME = Constants.DOCTEXT_COL;

  /** List of select items that come before the pattern extraction */
  private SelectListNode selectList;

  private PatternExpressionNode pattern;

  private final ReturnClauseNode returnClause;

  private final ColNameNode target;

  private final FromListNode fromList;

  private final HavingClauseNode havingClause;

  private final ConsolidateClauseNode consolidateClause;

  private final IntNode maxTup;

  public ExtractPatternNode(ArrayList<SelectListItemNode> items, PatternExpressionNode pattern,
      ReturnClauseNode returnClause, ColNameNode target, FromListNode fromList,
      HavingClauseNode havingClause, ConsolidateClauseNode consolidateClause, IntNode maxTup,
      String containingFileName, Token origTok) throws ParseException {
    // set error location info
    super(containingFileName, origTok);

    this.selectList = new SelectListNode(items, this.getContainingFileName(), origTok);
    this.pattern = pattern;
    this.returnClause = returnClause;
    this.target = target;
    this.fromList = fromList;
    this.havingClause = havingClause;
    this.consolidateClause = consolidateClause;
    this.maxTup = maxTup;
  }

  /**
   * Validates the pattern specification to avoid errors in the rewritten statement.
   */
  @Override
  public List<ParseException> validate(Catalog catalog) {
    List<ParseException> errors = new ArrayList<ParseException>();

    if (null != fromList) {
      errors.addAll(fromList.validate(catalog));
    }

    if (null != this.returnClause) {
      errors.addAll(returnClause.validate(catalog));
    }

    // Step 1: check that each view referenced by this statement is well defined
    // Step 1.a: The target of the WITH INLINE_MATCH clause must contain a view name
    if (target != null)
      if (!target.getHaveTabname())
        errors.add(AQLParserBase.makeException(target.getOrigTok(),
            "Target '%s' in WITH inline_match clause must be fully qualified (of the form view_name.attribute_name).",
            target));

    // Step 1.b: Each view in the FROM clause and the WITH INLINE_MATCH clause must be defined
    TreeSet<String> views = new TreeSet<String>();
    try {
      getDeps(views, catalog);
    } catch (ParseException e1) {
      errors.add(e1);
    }
    for (String viewName : views) {
      try {
        if (catalog.lookupView(viewName, getOrigTok()) == null)
          errors.add(AQLParserBase.makeException(getOrigTok(),
              "Invalid view name '%s' referenced in FROM clause or WITH inline_match clause.",
              viewName));
      } catch (ParseException e) {
        errors.add(e);
      }
    }

    // Moved the col ref validation to {@link Catalog#validatePattern()} because eventually we want
    // to
    // refactor PatternNode as an ExtractNode with a different type of extraction, and at that
    // point,
    // the FROM list will be a property of the Extract node, not the Pattern node
    // Step 2: check that each column referenced in the pattern is well defined, i.e., the view name
    // appears in the FROM
    // clause, and the attribute name is a valid attribute of that view
    /*
     * ArrayList<ColNameNode> cols = pattern.getCols (); ColNameNode col; String tabName; for (int i
     * = 0; i < cols.size (); i++) { col = cols.get (i); // For simplicity, enforce that the view
     * name of this column is explicit if (!col.getHaveTabname ()) { errors.add
     * (AQLParserBase.makeException (getOrigTok (),
     * "The view that attribute '%s' belongs to is not specified in the PATTERN clause.",
     * col.getColName ())); } else { tabName = col.getTabname (); // View name not present in the
     * FROM list if (fromList.getByName (tabName) == null) errors.add (AQLParserBase.makeException
     * (getOrigTok (), "The view name of '%s' does not appear in the FROM clause.", col.getColName
     * ())); } }
     */

    ArrayList<ColNameNode> patternCols = pattern.getCols();
    ColNameNode col;
    String tabName;

    // Step 3: check that each view alias in the FROM clause is referenced exactly once in the
    // sequence pattern;
    HashMap<String, ColNameNode> tab2Col = new HashMap<String, ColNameNode>();
    for (int j = 0; j < patternCols.size(); j++) {
      col = patternCols.get(j);
      // perform this validation on column which has a view name
      if (col.getHaveTabname()) {
        tabName = col.getTabname();
        if (tab2Col.containsKey(tabName))
          errors.add(AQLParserBase.makeException(getOrigTok(),
              "View alias '%s' referenced twice in PATTERN expression as '%s' and '%s'", tabName,
              tab2Col.get(tabName), col));

        tab2Col.put(tabName, col);
      }
    }

    // validation specific to subclasses of PatternExpressionNode
    errors.addAll(pattern.validate(catalog));

    // Step 4: Check that the consolidate clause is valid
    if (null != consolidateClause) {
      errors.addAll(consolidateClause.validate(catalog));
    }

    // Step 5: Check that the pattern does not match the empty string
    if (pattern.matchesEmpty())
      errors.add(AQLParserBase.makeException(getOrigTok(),
          "The PATTERN expression %s matches the empty string.", pattern));

    // Step 6: Validate the HAVING clause
    if (havingClause != null) {
      errors.addAll(this.havingClause.validate(catalog));
    }

    // Step 7: Validate the SELECT list
    if (null != selectList) {

      // Disallow select items of the form <viewName>.*
      /*
       * for (int i = 0; i < selectList.size (); i++) { if (selectList.get (i).getIsDotStar ()) {
       * errors.add (AQLParserBase.makeException ( selectList.get (i).getOrigValue ().getOrigTok (),
       * "Invalid select expression '%s.*'. Expressions of the form view_name.* are not allowed in the select list of an EXTRACT statement."
       * , selectList.get (i).getOrigValue ())); return errors; } }
       */

      // Normal validation of the select list. Will validate col refs and function names against the
      // catalog
      errors.addAll(selectList.validate(catalog));

      // Make sure the select list does not contain any aggregates
      errors.addAll(selectList.ensureNoAggFunc(catalog));

      // If the pattern expression does not contain any column atoms, the the rewrite doesn't know
      // how to pass through
      // additional attributes. The rewrite currently draws pass through attributes from the rewrite
      // for Column Atoms of
      // the pattern. No column atoms ==> nowhere to get those extra pass through attributes from.
      // So disallow this
      // scenario for now.
      if (0 == patternCols.size()) {

        // Get the column references from the select list

        if (selectList.getIsSelectStar()) {
          errors.add(AQLParserBase.makeException(getOrigTok(),
              "Invalid element '*' in EXTRACT clause: the PATTERN expression does not have column references, and cannot propagate additional fields to the output"));
        } else {
          TreeSet<String> selectCols = new TreeSet<String>();
          for (int i = 0; i < selectList.size(); i++) {
            SelectListItemNode item = selectList.get(i);
            if (item.getIsDotStar()) {
              selectCols.add(item.value + ".*");
            } else {
              try {
                item.getReferencedCols(selectCols, catalog);
              } catch (ParseException e) {
                errors.add(AQLParserBase.makeException(getOrigTok(), "", selectCols));
              }
            }
          }

          if (0 != selectCols.size()) {
            errors.add(AQLParserBase.makeException(getOrigTok(),
                "Invalid elements %s in SELECT list: the PATTERN expression does not have column references, and cannot propagate additional attributes to the output",
                selectCols));
          }
        }
      }
    }

    return errors;
  }

  public PatternExpressionNode getPattern() {
    return pattern;
  }

  public void setPattern(PatternExpressionNode pattern) {
    this.pattern = pattern;
  }

  public SelectListNode getSelectList() {
    return selectList;
  }

  public FromListNode getFromList() {
    return fromList;
  }

  public ReturnClauseNode getReturnClause() {
    return returnClause;
  }

  public ColNameNode getTarget() {
    return target;
  }

  public IntNode getMaxTup() {
    return maxTup;
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    stream.print("extract ");
    if (null != selectList && selectList.size() > 0) {
      // printIndent (stream, indent);
      selectList.dump(stream, indent + 4);
      stream.print(",\n");
      printIndent(stream, indent + 4);
    }

    // Pattern
    stream.print("pattern ");
    pattern.dump(stream, 0);

    // RETURN clause
    stream.print("\n");
    returnClause.dump(stream, indent);

    if (null != target) {
      printIndent(stream, indent);
      stream.printf("with inline_match on %s", target.getColName());
      stream.print("\n");
    }

    // FROM clause
    printIndent(stream, indent);
    stream.print("from  ");
    fromList.dump(stream, indent + 1);

    // Optional HAVING clause
    if (null != havingClause) {
      stream.print("\n");
      printIndent(stream, indent);
      stream.print("having  ");
      havingClause.dump(stream, indent + 1);
    }

    // Optional CONSOLIDATE clause
    if (null != consolidateClause) {
      stream.print("\n");
      printIndent(stream, indent);
      stream.print("consolidate on ");
      consolidateClause.dump(stream, indent + 1);
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
    ExtractPatternNode other = (ExtractPatternNode) o;
    int val = fromList.compareTo(other.fromList);
    if (val != 0) {
      return val;
    }
    val = pattern.compareTo(other.pattern);
    if (val != 0) {
      return val;
    }
    val = selectList.compareTo(other.selectList);
    if (val != 0) {
      return val;
    }

    // The remaining fields of this class can be null, so we need to take
    // that possibility into account.
    val = compareAndCheckNull(returnClause, other.returnClause);
    if (val != 0) {
      return val;
    }
    val = compareAndCheckNull(havingClause, other.havingClause);
    if (val != 0) {
      return val;
    }
    val = compareAndCheckNull(target, other.target);
    if (val != 0) {
      return val;
    }
    val = compareAndCheckNull(consolidateClause, other.consolidateClause);
    return val;
  }

  @Override
  public void getDeps(TreeSet<String> accum, Catalog catalog) throws ParseException {
    for (FromListItemNode fromListItem : fromList.getItems()) {
      fromListItem.getDeps(accum, catalog);
    }

    if (target != null)
      if (target.getHaveTabname())
        accum.add(target.getTabname());

    // The having clause can add additional dependencies
    if (null != havingClause) {
      for (int i = 0; i < havingClause.size(); i++) {
        PredicateNode pred = havingClause.getPred(i);
        pred.getReferencedViews(accum, catalog);
      }
    }
  }

  public HavingClauseNode getHavingClause() {
    return havingClause;
  }

  public ConsolidateClauseNode getConsolidateClause() {
    return consolidateClause;
  }

  /**
   * Verifies that all return groups are defined by the sequence pattern expression. Also, verifies
   * that no repeatable element has return groups underneath. Should be called only after the groups
   * have been labeled by the preprocessor.
   * 
   * @throws ParseException
   */
  public void validateGroupIDs() throws ParseException {

    ArrayList<Integer> actualReturnGroups = new ArrayList<Integer>();
    actualReturnGroups.add(new Integer(0));
    actualReturnGroups.addAll(pattern.getAllReturnGroupIDs());
    ArrayList<Integer> expectedReturnGroups = returnClause.getGroupIDs();

    // Check that each return group is defined by the sequence pattern expression.
    ArrayList<Integer> undefinedGroups = new ArrayList<Integer>();
    undefinedGroups.addAll(expectedReturnGroups);
    undefinedGroups.removeAll(actualReturnGroups);

    if (!undefinedGroups.isEmpty())
      // Since this code is called from the PreProcessor, make sure we attach the file information
      // to the error
      throw ParseToCatalog.makeWrapperException(AQLParser
          .makeException(String.format("Return groups %s are not defined in pattern expression %s.",
              undefinedGroups, pattern), getOrigTok()),
          getContainingFileName());

    // Check that no repeatable element has return groups underneath
    ArrayList<Integer> repeatGroups = pattern.getAllRepeatReturnGroupIDs();
    if (!repeatGroups.isEmpty())
      // Since this code is called from the PreProcessor, make sure we attach the file information
      // to the error
      throw ParseToCatalog.makeWrapperException(AQLParser.makeException(String.format(
          "Return groups %s occurring within repeatable elements are not allowed in pattern expression %s.",
          repeatGroups, pattern), getOrigTok()), getContainingFileName());
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
    // Nothing to expand or infer
    if (null == this.selectList)
      return;

    // Replace the select list with another list created by expanding wildcards
    SelectListNode newSelectListNode =
        new SelectListNode(selectList.expandWildcards(fromList, catalog),
            selectList.getContainingFileName(), selectList.getOrigTok());

    this.selectList = newSelectListNode;

    // Infer any missing aliases in the select list
    this.selectList.inferAliases();
  }

  @Override
  public void qualifyReferences(Catalog catalog) {
    // No action
  }

}
