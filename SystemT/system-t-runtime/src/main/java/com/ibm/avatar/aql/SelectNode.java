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

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import com.ibm.avatar.aql.catalog.AggFuncCatalogEntry;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.aql.catalog.ScalarFuncCatalogEntry;

/**
 * Parse tree node for a SELECT statement, consisting of a select list, a from list, and an optional
 * where clause.
 */
public class SelectNode extends ViewBodyNode {

  private SelectListNode selectList;

  private FromListNode fromList;

  /**
   * The group by clause, if any
   */
  private GroupByClauseNode groupByClause;

  /**
   * The order by clause, if any
   */
  private OrderByClauseNode orderByClause;

  /**
   * Parse tree node for the consolidate clause, if any.
   */
  private ConsolidateClauseNode consolidateClause;

  /** Pointer to the parse tree's global symbol table. */
  // private SymbolTable symtab;
  /** Where clause is optional; if not present, this field is null. */
  private WhereClauseNode whereClause = null;

  /**
   * Limit on the number of tuples returned, or null if the statement had no LIMIT clause.
   */
  private IntNode maxTup = null;

  /**
   * Main constructor
   * 
   * @param origTok AQL parser token to mark the first token in the SELECT statement for error
   *        reporting purposes
   * @param selectList list of expressions in the SELECT list
   * @param fromList FROM list of SELECT statement
   * @param whereClause predicates in the WHERE clause (conjunction with implicit AND)
   * @param groupByClause optional GROUP BY clause
   * @param orderByClause optional ORDER BY clause
   * @param consolidateClause optional CONSOLIDATE clause
   * @param maxTup argument of optional RETURN X ROWS ONLY clause
   */
  public SelectNode(SelectListNode selectList, FromListNode fromList, WhereClauseNode whereClause,
      GroupByClauseNode groupByClause, OrderByClauseNode orderByClause,
      ConsolidateClauseNode consolidateClause, IntNode maxTup, String containingFileName,
      Token origTok) throws ParseException {
    // set error location info
    super(containingFileName, origTok);

    this.selectList = selectList;
    this.fromList = fromList;
    this.whereClause = whereClause;
    this.groupByClause = groupByClause;
    this.orderByClause = orderByClause;
    this.consolidateClause = consolidateClause;
    this.maxTup = maxTup;
  }

  /**
   * For select node in addition to validation we do wildcard expansion also
   */
  @Override
  public List<ParseException> validate(Catalog catalog) {
    List<ParseException> errors = new ArrayList<ParseException>();

    // Pass 1: validate that ColNameNodes in select list have corresponding elements in the from
    // list
    if (null != this.selectList) {
      // for each SelectListItemNode
      for (int i = 0; i < this.selectList.size(); ++i) {
        SelectListItemNode selListItemNode = this.selectList.get(i);

        // For now we only validate ColNameNodes here. Invalid column references buried in a
        // function call will be
        // caught during type inference.
        if (selListItemNode.value instanceof ColNameNode) {
          ColNameNode colNameNode = (ColNameNode) selListItemNode.value;

          String tabName = colNameNode.getTabname();
          if (null == tabName) {
            // Table name not provided; will be inferred (and checked) later in compilation.

          } else {
            // Normal case -- table name present. Make sure that the target of the name is in the
            // FROM list
            FromListItemNode targetTab = fromList.getByName(tabName);

            if (null == targetTab) {
              errors.add(AQLParserBase.makeException(colNameNode.getTabnameTok(),
                  "Name '%s' does not appear in the FROM clause of the SELECT statement.  "
                      + "Ensure that the target view, table, or function appears in the FROM clause under the correct name.",
                  tabName));
            }
          }

          // Checks on the name of the column within the table are currently done during type
          // inference.

        } // end: value instanceof ColNameNode
      } // end: for each SelectListItemNode
    }

    // Pass 2: Validate selectList
    if (null != this.selectList) {
      List<ParseException> selectListError = selectList.validate(catalog);
      if (null != selectListError && selectListError.size() > 0)
        errors.addAll(selectListError);

    }

    // Pass 3: Validate fromList
    if (null != fromList) {
      List<ParseException> fromListError = fromList.validate(catalog);
      if (null != fromListError && fromListError.size() > 0)
        errors.addAll(fromListError);
    }

    // Pass 4: Validate consolidateClause
    if (null != consolidateClause) {
      List<ParseException> consolidateClauseErrors = this.consolidateClause.validate(catalog);
      if (null != consolidateClauseErrors && consolidateClauseErrors.size() > 0)
        errors.addAll(consolidateClauseErrors);
    }

    // Pass 5: Validate groupByClause
    if (null != this.groupByClause) {
      List<ParseException> groupByErrors = this.groupByClause.validate(catalog);
      if (null != groupByErrors && groupByErrors.size() > 0)
        errors.addAll(groupByErrors);

      // Validate the SELECT list w.r.t GROUP BY clause. In case of an artificial GROUP BY clause,
      // this will ensure that
      // the SELECT list contains only aggregates.
      // IMPORTANT!!! BEGIN MOVE
      // Group by validation must be done after wildcard expansion and alias inference. Moved it to
      // {@link
      // Catalog#validateSelect()}.
      // try {
      // errors.addAll (validateSelectList (catalog));
      // }
      // catch (ParseException e) {
      // errors.add (e);
      // }
      // IMPORTANT!!! END MOVE
    }

    // Pass 6: Validate orderByClause
    if (null != this.orderByClause) {
      List<ParseException> orderByErrors = this.orderByClause.validate(catalog);
      if (null != orderByErrors && orderByErrors.size() > 0)
        errors.addAll(orderByErrors);
    }

    // Pass 7: Validate whereClause
    if (null != this.whereClause) {
      List<ParseException> whereClauseErrors = this.whereClause.validate(catalog);
      if (null != whereClauseErrors && whereClauseErrors.size() > 0)
        errors.addAll(whereClauseErrors);
    }

    return errors;
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    // SELECT clause
    stream.print("select  ");
    selectList.dump(stream, indent + 4);
    stream.print("\n");

    // FROM clause
    printIndent(stream, indent);
    stream.print("from  ");
    fromList.dump(stream, indent + 1);

    if (null != whereClause) {
      stream.print("\n");
      printIndent(stream, indent);
      stream.print("where ");
      whereClause.dump(stream, indent + 1);
    }

    if (null != groupByClause) {
      stream.print("\n");
      printIndent(stream, indent);
      stream.print("group by ");
      groupByClause.dump(stream, indent);
    }

    if (null != consolidateClause) {
      stream.print("\n");
      printIndent(stream, indent);
      stream.printf("consolidate on ");

      consolidateClause.dump(stream, indent + 1);
    }

    if (null != orderByClause) {
      stream.print("\n");
      printIndent(stream, indent);
      stream.print("order by ");
      orderByClause.dump(stream, indent);
    }

    if (null != maxTup) {
      stream.print("\n");
      printIndent(stream, indent);
      stream.print("limit ");
      maxTup.dump(stream, 0);
    }
  }

  /**
   * @return line in the AQL input at which this select statement started, or -1 if there isn't
   *         enough information to compute a line number
   */
  public int getLine() {
    if (null == origTok) {
      return -1;
    }
    return origTok.beginLine;
  }

  public FromListNode getFromList() {
    return fromList;
  }

  public SelectListNode getSelectList() {
    return selectList;
  }

  public WhereClauseNode getWhereClause() {
    return whereClause;
  }

  public GroupByClauseNode getGroupByClause() {
    return groupByClause;
  }

  public OrderByClauseNode getOrderByClause() {
    return orderByClause;
  }

  public ConsolidateClauseNode getConsolidateClause() {
    return consolidateClause;
  }

  public IntNode getMaxTups() {
    return maxTup;
  }

  public void setFromList(FromListNode fromList) {
    this.fromList = fromList;
  }

  public void setSelectList(SelectListNode selectList) {
    this.selectList = selectList;
  }

  public void setWhereClause(WhereClauseNode whereClause) {
    this.whereClause = whereClause;
  }

  public void setGroupByClause(GroupByClauseNode groupByClause) {
    this.groupByClause = groupByClause;
  }

  public void setOrderByClause(OrderByClauseNode orderByClause) {
    this.orderByClause = orderByClause;
  }

  public void setConsolidateClause(ConsolidateClauseNode consolidateClause) {
    this.consolidateClause = consolidateClause;
  }

  @Override
  protected int reallyCompareTo(AQLParseTreeNode o) {
    SelectNode other = (SelectNode) o;
    int val = fromList.compareTo(other.fromList);
    if (val != 0) {
      return val;
    }
    val = selectList.compareTo(other.selectList);
    if (val != 0) {
      return val;
    }

    // The remaining fields of this class can be null, so we need to take
    // that possibility into account.
    val = compareAndCheckNull(whereClause, other.whereClause);
    if (val != 0) {
      return val;
    }
    val = compareAndCheckNull(groupByClause, other.groupByClause);
    if (val != 0) {
      return val;
    }
    val = compareAndCheckNull(orderByClause, other.orderByClause);
    if (val != 0) {
      return val;
    }
    val = compareAndCheckNull(consolidateClause, other.consolidateClause);
    return val;
  }

  @Override
  public void getDeps(TreeSet<String> accum, Catalog catalog) throws ParseException {
    // Scan the FROM list for dependencies; this approach works because AQL
    // does not currently have subqueries at this point in compilation.
    for (FromListItemNode fromListItem : fromList.getItems()) {
      fromListItem.getDeps(accum, catalog);
    }

    // Also pull in dependencies that arise from locator arguments in the select list.
    for (int i = 0; i < selectList.size(); i++) {
      SelectListItemNode selectListItem = selectList.get(i);

      RValueNode value = selectListItem.getValue();
      value.getReferencedViews(accum, catalog);
    }

    // There can also be locator arguments in the where clause.
    if (null != whereClause) {
      for (int i = 0; i < whereClause.size(); i++) {
        PredicateNode pred = whereClause.getPred(i);
        pred.getReferencedViews(accum, catalog);
      }
    }

  }

  /** Adapter for {@link #dump(PrintWriter,int)} */
  @Override
  public void dump(PrintStream stream, int indent) throws ParseException {
    PrintWriter pw = new PrintWriter(stream);
    dump(pw, indent);
    pw.close();
  }

  /**
   * Checks that each item in the select list is valid w.r.t this GROUP BY clause.
   * 
   * @return a list of all errors encountered while validating the select list
   * @throws ParseException
   */
  public List<ParseException> validateSelectList(Catalog catalog) throws ParseException {
    List<ParseException> errors = new ArrayList<ParseException>();

    ColNameNode col;
    ScalarFnCallNode func;
    boolean hasAggs = false;
    boolean hasScalars = false;
    Token tok;

    for (int i = 0; i < selectList.size(); i++) {
      SelectListItemNode selectItem = selectList.get(i);
      RValueNode val = selectItem.getValue();
      // The value can be created via expand wildcard/infer alias. If so, create an error with
      // location at the "select"
      // token.
      tok = (null != val.getOrigTok() ? val.getOrigTok() : this.getOrigTok());

      if (val instanceof ColNameNode) {
        hasScalars = true;
        col = (ColNameNode) val;

        if (!validateCol(col.getColName()))
          errors.add(AQLParserBase.makeException(tok,
              "Column %s in SELECT clause is not valid with respect to the GROUP BY clause. Either remove it from the select list, or add it to the GROUP BY clause.",
              val));
      } else if (val instanceof ScalarFnCallNode) {

        // The value can be created via expand wildcard/infer alias. If so, create an error with
        // location at the
        // "select" token.
        tok = (null != val.getOrigTok() ? val.getOrigTok() : this.getOrigTok());

        func = (ScalarFnCallNode) val;
        String fname = func.getFuncName();

        // We would switch on the function type here, but at this point
        // in compilation that information is not available.
        // So instead we look up the function in the catalog and
        // replicate some logic from the Preprocessor class.
        ScalarFuncCatalogEntry scalarFuncEntry = catalog.lookupScalarFunc(fname);
        AggFuncCatalogEntry aggFuncEntry = catalog.lookupAggFunc(fname);

        if (null != scalarFuncEntry) {
          // Function is a scalar function
          hasScalars = true;
          if (!validateFunc(func, catalog)) {
            errors.add(AQLParserBase.makeException(tok,
                "Scalar function call %s(...) is not valid with respect to the GROUP BY clause. Either remove it from the select list, or add it to the GROUP BY clause.",
                func.getFuncName()));
          }
        } else if (null != aggFuncEntry) {
          // Function is an aggregate; no further validation needed here.
          hasAggs = true;
        } else {
          // No need to throw an exception here. Function names are already validated in {@link
          // ParseTreeNode#validate(Catalog)}
          // This will only result in duplicate exceptions
          // errors.add (AQLParserBase.makeException (val.getOrigTok (), "Unknown function '%s'",
          // fname));
        }

      }
    }

    // Final check to make sure that if there is no GROUP BY, we cannot have both scalars and
    // aggregates. It's either
    // scalars only, or aggregates only.
    if (null == groupByClause && hasAggs && hasScalars) {
      errors.add(AQLParserBase.makeException(getOrigTok(),
          "The SELECT list contains columns, scalar functions and aggregate functions. Either specify a GROUP BY clause, or remove the columns and scalar functions, or remove the aggregate functions."));
    }

    return errors;
  }

  /**
   * Checks if the input column name is valid with respect to the Group By clause and can safely
   * appear in the select list.
   * 
   * @param col
   * @param groupByValues
   * @return true if the input column appears as a column in the group by list.
   */
  private boolean validateCol(String col) {
    // Trivially valid
    if (null == groupByClause)
      return true;

    ColNameNode groupByCol;

    for (RValueNode groupByVal : groupByClause.values) {

      if (groupByVal instanceof ColNameNode) {
        // return true if the group by list contains the input column itself
        groupByCol = (ColNameNode) groupByVal;
        if (col.equals(groupByCol.getColName()))
          return true;
      }
    }

    return false;
  }

  /**
   * Checks if the input function is valid with respect to the group by list and can safely appear
   * in the select list.
   * 
   * @param func parse tree node to validate
   * @param catalog ptr to AQL catalog for metadata lookup
   * @return true if each column referenced in the input function is by itself valid with respect to
   *         the group by list, or the same function appears in the group by list.
   */
  private boolean validateFunc(ScalarFnCallNode func, Catalog catalog) throws ParseException {

    // Trivially valid
    if (null == groupByClause)
      return true;

    TreeSet<String> funcArgs = new TreeSet<String>();
    func.getReferencedCols(funcArgs, catalog);
    boolean validArgs = true;

    // First, check if each argument in itself is valid
    for (String arg : funcArgs) {
      if (!validateCol(arg)) {
        validArgs = false;
        break;
      }
    }

    // If the previous check fails, we next check if our function itself appears in the group by
    // list
    if (validArgs)
      return true;
    else {
      for (RValueNode groupByVal : groupByClause.values) {

        if (groupByVal instanceof ScalarFnCallNode)
          if (func.reallyCompareTo(groupByVal) == 0)
            return true;
      }
    }

    return false;
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
    // Replace the select list with another list created by expanding wildcards
    selectList = new SelectListNode(selectList.expandWildcards(fromList, catalog),
        selectList.getContainingFileName(), selectList.getOrigTok());
    // Infer any missing aliases in the select list
    selectList.inferAliases();
  }

  @Override
  public void qualifyReferences(Catalog catalog) throws ParseException {
    selectList.qualifyReferences(catalog);
    fromList.qualifyReferences(catalog);

    if (whereClause != null) {
      whereClause.qualifyReferences(catalog);
    }

    if (consolidateClause != null) {
      consolidateClause.qualifyReferences(catalog);
    }

    if (groupByClause != null) {
      groupByClause.qualifyReferences(catalog);
    }

    if (orderByClause != null) {
      orderByClause.qualifyReferences(catalog);
    }

  }

}
