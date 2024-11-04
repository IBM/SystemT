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
package com.ibm.avatar.aql.planner;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.TreeSet;

import com.ibm.avatar.aql.AQLParserBase;
import com.ibm.avatar.aql.ColNameNode;
import com.ibm.avatar.aql.ConsolidateClauseNode;
import com.ibm.avatar.aql.ExtractNode;
import com.ibm.avatar.aql.ExtractionNode;
import com.ibm.avatar.aql.FromListItemNode;
import com.ibm.avatar.aql.GroupByClauseNode;
import com.ibm.avatar.aql.HavingClauseNode;
import com.ibm.avatar.aql.IntNode;
import com.ibm.avatar.aql.NickNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.PredicateNode;
import com.ibm.avatar.aql.RValueNode;
import com.ibm.avatar.aql.ScalarFnCallNode;
import com.ibm.avatar.aql.SelectListItemNode;
import com.ibm.avatar.aql.SelectListNode;
import com.ibm.avatar.aql.SelectNode;
import com.ibm.avatar.aql.SplitExNode;
import com.ibm.avatar.aql.catalog.Catalog;

/** Base class for the internal implementations of the AQL planner. */
public abstract class PlannerImpl {

  /** Pointer to the parser's symbol table; used for internal name resolution. */
  protected Catalog catalog;

  public void setCatalog(Catalog catalog) {
    this.catalog = catalog;
  }

  /**
   * Helper function to compute and add a cost record to a plan node. Implemented using the simple
   * cost model as default, but specific implementations may override this with a more appropriate
   * cost model.
   * 
   * @param node The plan node to modify
   * @throws ParseException
   */
  public void addCostRecordToNode(PlanNode node) throws ParseException {
    SimpleCostModel costModel = new SimpleCostModel();
    CostRecord costRec = costModel.computeCostRecord(node);
    node.setCostRecord(costRec);
  }

  /**
   * Generate a query plan for executing a SELECT statement in AQL. Delegates most of the hard work
   * to {@link #computeJoinPlan(SelectNode)}.
   * 
   * @param stmt parse tree for the select statement
   * @return logical query plan
   * @throws ParseException
   */
  public PlanNode planSelect(SelectNode stmt) throws ParseException {
    final boolean debug = false;

    // Perform some checks first.
    validateStmt(stmt);

    if (debug) {
      System.err.printf("Planning select statement:\n");
      stmt.dump(System.err, 1);
      System.err.printf("\n\n");
    }

    // Start with the output of the implementation class's join planner.
    PlanNode root = computeJoinPlan(stmt);

    if (debug) {
      System.err.printf("Base join plan is:\n");
      root.dump(System.err, 1);
      System.err.printf("\n\n");
    }

    // The plan returned by computeJoinPlan doesn't perform the projection
    // and scalar functions that the select statement calls for. Add those
    // postprocessing steps to the generated plan.

    // Examine the select list for scalar functions and renamings.
    SelectListNode selectList = stmt.getSelectList();

    ArrayList<String> internalNames = new ArrayList<String>();
    ArrayList<String> externalNames = new ArrayList<String>();
    ArrayList<ScalarFnCallNode> scalarFuncs = new ArrayList<ScalarFnCallNode>();
    ArrayList<ScalarFnCallNode> aggFuncs = new ArrayList<ScalarFnCallNode>();
    ArrayList<String> aggFuncsInternalNames = new ArrayList<String>();

    compileSelectList(selectList, internalNames, externalNames, scalarFuncs, aggFuncs,
        aggFuncsInternalNames);

    if (debug) {
      System.err.printf("Internal names: %s\n", internalNames);
      System.err.printf("External names: %s\n", externalNames);
      System.err.printf("Scalar functions: %s\n", scalarFuncs);
      System.err.printf("Aggregate functions: %s\n", aggFuncs);
      System.err.printf("Aggregate functions internal names: %s\n", aggFuncsInternalNames);
    }

    // Apply any scalar functions that appear in the select list.
    for (ScalarFnCallNode func : scalarFuncs) {
      root = new ScalarFuncNode(func, root);
    }

    // If the select statement has a GROUP BY clause,
    // or any aggregate function in the select list,
    // apply the grouping (if any) and compute the aggregate values
    GroupByClauseNode groupByClause = stmt.getGroupByClause();
    if (null != stmt.getGroupByClause() || !aggFuncs.isEmpty()) {

      // Create an artificial GROUP BY clause when the select list
      // contains at least
      // one aggregate. In this case, a single tuple consisting of the
      // results of the
      // aggregate functions in the SELECT list is output.
      ArrayList<RValueNode> groupByVals = null;
      if (null != stmt.getGroupByClause())
        groupByVals = groupByClause.getValues();

      root = new GroupNode(groupByVals, aggFuncs, aggFuncsInternalNames, root);

      // TODO: handle HAVING clause, if any
    }

    // If the select statement has an ORDER BY clause, apply the appropriate
    // sort order.
    if (null != stmt.getOrderByClause()) {
      root = new SortNode(stmt.getOrderByClause().getValues(), root);
    }

    // If the select statement had a CONSOLIDATE ON clause, add a
    // Consolidate operator at the top of the plan.
    ConsolidateClauseNode consolidateClause = stmt.getConsolidateClause();
    if (null != consolidateClause) {
      root = new ConsolidatePlanNode(consolidateClause, root);
    }

    // Add a projection at the top so we only get the columns in the select
    // list.
    ProjectionNode proj = new ProjectionNode(root);

    // Rename the internal column names to external column names.
    for (int i = 0; i < internalNames.size(); i++) {
      proj.addRenaming(internalNames.get(i), externalNames.get(i));
    }

    root = proj;

    // If there is a LIMIT clause, add a Limit operator.
    IntNode maxTups = stmt.getMaxTups();
    if (null != maxTups) {
      root = new LimitNode(root, maxTups.getValue());
    }

    return root;
  }

  private void compileSelectList(SelectListNode selectList, ArrayList<String> internalNames,
      ArrayList<String> externalNames, ArrayList<ScalarFnCallNode> scalarFuncs,
      ArrayList<ScalarFnCallNode> aggFuncs, ArrayList<String> aggFuncsInternalNames)
      throws ParseException {
    for (int i = 0; i < selectList.size(); i++) {
      SelectListItemNode selectItem = selectList.get(i);
      RValueNode val = selectItem.getValue();

      String internalName = val.getColName();

      String externalName = selectItem.getAlias();

      internalNames.add(internalName);
      externalNames.add(externalName);

      // Remember any select list items that require function calls.
      ScalarFnCallNode func;

      if (val instanceof ScalarFnCallNode) {
        func = (ScalarFnCallNode) val;

        // Determine whether the function is an aggregate or a scalar function
        if (null != catalog.lookupScalarFunc(func.getFuncName())) {
          scalarFuncs.add(func);
        } else if (null != catalog.lookupAggFunc(func.getFuncName())) {
          aggFuncs.add(func);
          aggFuncsInternalNames.add(internalName);
        } else {
          // Lookup failed. This usually means that upstream code has done something wrong.
          throw AQLParserBase.makeException(func.getFuncNameNode().getOrigTok(),
              "No information for function '%s' found in catalog.  "
                  + "Ensure that the function has been defined or imported under the specified name.",
              func.getFuncName());
        }
      }
    }
  }

  public PlanNode planExtract(ExtractNode e) throws ParseException {

    // PHASE 1:
    // Compute various parameters that will be needed to generate the plan.

    // Figure out which of the input columns we should project out. The
    // extraction will be copying tuples, so it's important to keep the
    // width of the input tuples to a minimum.
    FromListItemNode target = e.getTarget();
    ArrayList<String> colsToKeep =
        getNeededCols(catalog, target, null, e.getExtractList().getSelectList(), null, null);

    // Don't forget about the target column!
    ColNameNode targetCol = e.getExtractList().getExtractSpec().getTargetName();
    String targetColName = targetCol.getColnameInTable();
    if (false == colsToKeep.contains(targetColName)) {
      colsToKeep.add(targetColName);
    }

    ExtractionNode extractSpec = e.getExtractList().getExtractSpec();
    if (extractSpec instanceof SplitExNode) {
      // SPECIAL CASE: Split extractions also need the splitting column
      // passed through.
      SplitExNode split = (SplitExNode) extractSpec;
      String splitColName = split.getSplitCol().getColnameInTable();
      if (false == colsToKeep.contains(splitColName)) {
        colsToKeep.add(splitColName);
      }
      // END SPECIAL CASE
    }

    // // If possible, we use an unqualified target name to avoid generating
    // an
    // // extra projection
    // boolean qualifiedTargetName;
    // if (0 == colsToKeep.size()) {
    // // SPECIAL CASE: No input columns are needed except for the
    // // extraction column(s). In this case, we don't bother projecting
    // // the
    // // input to the extraction operator.
    // // Without a projection on the input, the target column will have an
    // // unqualified name (e.g. "col" instead of "relation.col")
    // qualifiedTargetName = false;
    // // END SPECIAL CASE
    // } else {
    // // If we get here, there are other output columns, so our scan will
    // // need to do column renaming.
    // // We also need this operator to keep the target column for the
    // // extraction, if no one else is asking for it.
    // for (ColNameNode col : e.getExtractList().getExtractSpec()
    // .getRequiredCols()) {
    // String targetColUnqual = col.getColnameInTable();
    // boolean needToAddTargetCol = true;
    // for (String name : colsToKeep) {
    // if (name.equals(targetColUnqual)) {
    // needToAddTargetCol = false;
    // }
    // }
    //
    // if (needToAddTargetCol) {
    // colsToKeep.add(targetColUnqual);
    // }
    //
    // }
    //
    // // The output columns could overlap with input columns, so the
    // // extraction uses qualified column names.
    // qualifiedTargetName = true;
    // }

    // Now we need to add on the necessary scalar function calls and
    // projections to get the select list items.
    // Most of this code is the same as in planSelect() above; both should
    // be changed when we have a Project operator that can apply functions.
    SelectListNode selectList = e.getExtractList().getSelectList();

    ArrayList<String> internalNames = new ArrayList<String>();
    ArrayList<String> externalNames = new ArrayList<String>();
    ArrayList<ScalarFnCallNode> scalarFuncs = new ArrayList<ScalarFnCallNode>();
    ArrayList<ScalarFnCallNode> aggFuncs = new ArrayList<ScalarFnCallNode>();

    compileSelectList(selectList, internalNames, externalNames, scalarFuncs, aggFuncs,
        new ArrayList<String>());

    // Aggregates are (currently) forbidden in the EXTRACT clause, so throw
    // an exception if we find any
    if (!aggFuncs.isEmpty()) {

      throw AQLParserBase.makeException(aggFuncs.get(0).getOrigTok(),
          "Aggregate function %s not allowed in EXTRACT clause.", aggFuncs.get(0).getFuncName());
    }

    // PHASE 2:
    // Generate the actual plan nodes.

    // The extraction may need multiple copies of the input; create the
    // appropriate number of plan subtrees.
    ArrayList<PlanNode> subtrees = new ArrayList<PlanNode>();
    int numCopies = e.getExtractList().getExtractSpec().getNumInputCopies();

    // Log.debug("Cols to keep are %s", colsToKeep);

    for (int inputIx = 0; inputIx < numCopies; inputIx++) {
      PlanNode root = new ScanNode(target, colsToKeep, catalog);

      // Add the extraction first, so that it can be replaced easily when
      // applied shared dictionary/regex matching.
      root = new ExtractPlanNode(e, root, inputIx);

      // Apply any scalar functions that appear in the select list.
      for (ScalarFnCallNode func : scalarFuncs) {
        root = new ScalarFuncNode(func, root);
      }

      subtrees.add(root);
    }

    PlanNode root;

    if (0 == subtrees.size()) {
      throw new RuntimeException("Didn't generate any extractions");
    }

    if (1 == subtrees.size()) {
      // Don't bother generating a union if there is only one copy of the
      // input.
      root = subtrees.get(0);
    } else {
      root = new UnionAllPlanNode(subtrees);
    }

    // Add a projection at the top so we only get the columns in the select
    // list.
    ProjectionNode proj = new ProjectionNode(root);

    // Rename the internal column names to external column names.
    for (int i = 0; i < internalNames.size(); i++) {
      proj.addRenaming(internalNames.get(i), externalNames.get(i));
    }

    // The projection should also pass through the output(s) of the
    // extraction.
    for (NickNode outputCol : e.getExtractList().getExtractSpec().getOutputCols()) {
      String outputColName = outputCol.getNickname();
      proj.addRenaming(outputColName, outputColName);
    }

    root = proj;

    // If there is a HAVING clause, implement it with a selection operator.
    HavingClauseNode having = e.getHavingClause();
    if (null != having) {
      // Construct a single selection predicate by ANDing all the terms in
      // the HAVING clause together.
      root = new SelectionNode(root, having.getPreds());
    }

    // If there is a CONSOLIDATE clause, add the appropriate type of
    // consolidation.
    ConsolidateClauseNode consolidate = e.getConsolidateClause();
    if (null != consolidate) {
      root = new ConsolidatePlanNode(consolidate, root);
    }

    // If there is a LIMIT clause, add a Limit operator.
    IntNode maxTups = e.getMaxTups();
    if (null != maxTups) {
      root = new LimitNode(root, maxTups.getValue());
    }

    return root;
  }

  /**
   * Rewrite a function call tree into one where the target column of the extraction is replaced
   * with {@link ExtractPlanNode#EXTRACT_COL_TEMP_NAME}
   * 
   * @param func original function call
   * @param targetColName name of the target column for our extraction
   * @return
   */
  // private static FunctionNode fixColNames(FunctionNode func, String
  // targetColName) {
  //
  // // Recursively convert the arguments.
  // ArrayList<RValueNode>
  //
  // for (RValueNode arg : func.getArgs()) {
  //
  // }
  //
  // return new FunctionNode()
  // }
  /**
   * Performs various checks to verify that the indicated statement will compile properly.
   * 
   * @param stmt statement that we will soon try to compile
   * @throws ParseException if the statement isn't valid
   */
  public void validateStmt(SelectNode stmt) throws ParseException {

    // Are all the columns referenced in the WHERE clause present in the
    // FROM list's relations?
    // Use getFilterPreds() to do the heavy lifting.
    TreeSet<PredicateNode> wherePredSet = new TreeSet<PredicateNode>();
    if (null != stmt.getWhereClause()) {
      wherePredSet.addAll(stmt.getWhereClause().getPreds());
    }
    ArrayList<PredicateNode> workingPreds =
        getFilterPreds(catalog, stmt.getFromList().getItems(), wherePredSet);

    // The returned list of predicates should contain everything in the
    // WHERE clause.
    TreeSet<PredicateNode> workingPredSet = new TreeSet<PredicateNode>();
    workingPredSet.addAll(workingPreds);

    if (!(wherePredSet.equals(workingPredSet))) {
      // Uh-oh, one or more predicates won't work. Figure out which
      // ones...
      wherePredSet.removeAll(workingPredSet);

      // Now the elements of wherePredSet are the "bad" ones.
      // Compute the set of columns referenced so that we can generate a
      // useful error message.
      TreeSet<String> referencedColNames = new TreeSet<String>();
      for (PredicateNode pred : wherePredSet) {
        pred.getReferencedCols(referencedColNames, catalog);
      }

      throw new ParseException(String.format(
          "In SELECT statement at line %d, " + "Predicate(s) (%s) reference columns"
              + " that are not in the FROM clause.  " + "Columns in FROM clause are: %s.  "
              + "Columns referenced in predicate(s) are: %s\n" + "Statement is:\n%s",
          stmt.getLine(), wherePredSet, //
          getAvailCols(catalog, stmt.getFromList().getItems()), referencedColNames,
          stmt.dumpToStr(0)));
    }

  }

  /**
   * Compute the optimal join order for a select statement and generate the corresponding
   * Implementation of this method is located in subclasses.
   * 
   * @param stmt parse tree node for a single select statement
   * @return a join plan that joins all the elements of the select statement's from list and applies
   *         all the predicates in the where clause
   * @throws ParseException
   */
  protected abstract PlanNode computeJoinPlan(SelectNode stmt) throws ParseException;

  /**
   * Convenience version of getFilterPreds() for cases with only one input.
   * 
   * @throws ParseException
   */
  protected ArrayList<PredicateNode> getFilterPreds(FromListItemNode relation,
      TreeSet<PredicateNode> availPreds) throws ParseException {
    ArrayList<FromListItemNode> oneItemList = new ArrayList<FromListItemNode>();
    oneItemList.add(relation);
    return getFilterPreds(catalog, oneItemList, availPreds);
  }

  /**
   * Static so that plug-in plan generation classes can call this method.
   * 
   * @param catalog pointer to AQL catalog
   * @param relations the from list items that are available
   * @param availPreds predicates that need to be applied
   * @return list of all the filtering predicates that can be applied at the indicated point in the
   *         join plan.
   */
  protected static ArrayList<PredicateNode> getFilterPreds(Catalog catalog,
      ArrayList<FromListItemNode> relations, TreeSet<PredicateNode> availPreds)
      throws ParseException {
    ArrayList<PredicateNode> toApply = new ArrayList<PredicateNode>();

    boolean debug = false;

    if (debug) {
      ArrayList<String> relnames = new ArrayList<String>();
      for (FromListItemNode node : relations) {
        relnames.add(node.getScopedName());
      }
      System.err.printf("Getting filter preds for %s\n", relnames);
    }

    // Start by computing the fully-qualified names of the columns that are
    // available for predicate evaluation.
    HashSet<String> availCols = getAvailCols(catalog, relations);

    // System.err.printf("Available columns are: %s\n", availCols);

    // Now run through the available predicates and determine
    // which ones we can apply here.
    for (PredicateNode pred : availPreds) {

      // System.err.printf("Trying predicate %s\n", pred);

      if (pred.isIgnored()) {
        // Some predicates (SameDoc(), for example) are no-ops.
      } else {

        TreeSet<String> colsUsed = new TreeSet<String>();
        pred.getReferencedCols(colsUsed, catalog);

        if (debug) {
          System.err.printf("   %s uses cols %s\n", pred, colsUsed);
        }

        boolean canUse = true;
        for (String colname : colsUsed) {
          if (!availCols.contains(colname)) {
            canUse = false;
          }
        }

        if (canUse) {
          toApply.add(pred);
        }
      }
    }

    return toApply;
  }

  /**
   * @param catalog catalog for a parsed AQL annotator
   * @param fromListItems a set of items in the FROM list of a select statement
   * @return the set of columns (as qualified column names) that are available from the
   *         relations/views in the indicated FROM list items
   * @throws ParseException
   */
  private static HashSet<String> getAvailCols(Catalog catalog,
      ArrayList<FromListItemNode> fromListItems) throws ParseException {

    final boolean debug = false;

    HashSet<String> availCols = new HashSet<String>();
    for (FromListItemNode fromItem : fromListItems) {

      ArrayList<String> unqualifiedNames = catalog.getColNames(fromItem);

      // Make sure that the names are properly scoped (e.g. nick.col)
      // for use inside the select statement.
      for (String name : unqualifiedNames) {
        String qualifiedName = fromItem.getScopedName() + "." + name;

        if (debug) {
          System.err.printf(" Adding qualified name '%s'\n", qualifiedName);
        }

        availCols.add(qualifiedName);
      }
    }
    return availCols;
  }

  /**
   * Static so that plug-in plan generation modules can call it.
   * 
   * @param catalog pointer to AQL catalog, for metadata lookup
   * @param target one of the input relations on the current select/extract clause being compiled
   * @param availPreds predicates that could be evaluated at this point in the plan
   * @param selectList select list of the clause being compiled
   * @param groupByValues group by clause node if present as a part of the select rule
   * @param consolidateClause consolidate clause node of the select rule
   * @return the unqualified names of all columns of the target that will be needed to evaluate the
   *         indicated predicates, consolidate clause and select list.
   * @throws ParseException if a syntax error is found
   */
  protected static ArrayList<String> getNeededCols(Catalog catalog, FromListItemNode target,
      TreeSet<PredicateNode> availPreds, SelectListNode selectList,
      ArrayList<RValueNode> groupByValues, ConsolidateClauseNode consolidateClause)
      throws ParseException {

    TreeSet<String> colNames = new TreeSet<String>();

    // Add all the columns referenced in the predicates.
    if (null != availPreds) {
      for (PredicateNode node : availPreds) {
        node.getReferencedCols(colNames, catalog);
      }
    }

    // If group by clause is there then project all of them irrespective
    // of them being referred in select stmt
    if (null != groupByValues) {
      for (RValueNode groupByVal : groupByValues) {
        groupByVal.getReferencedCols(colNames, catalog);
      }
    }

    // Add all the columns referenced in the select list.
    selectList.getReferencedCols(colNames, catalog);

    // Ramiya: Add all the columns referenced in the consolidate clause
    // defect
    if (null != consolidateClause) {
      consolidateClause.getReferencedCols(colNames, catalog);
    }
    // Filter out the columns of the target, turn them into unqualified
    // references, and convert to ArrayList format.
    String prefix = String.format("%s.", target.getScopedName());
    ArrayList<String> ret = new ArrayList<String>();

    for (String qualifiedName : colNames) {
      if (qualifiedName.startsWith(prefix)) {
        // Column from the target; strip off the prefix to make an
        // unqualified reference.
        ret.add(qualifiedName.substring(prefix.length()));
      }
    }
    return ret;
  }
}
