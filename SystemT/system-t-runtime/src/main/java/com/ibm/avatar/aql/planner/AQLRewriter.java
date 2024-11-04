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
import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;

import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.algebra.function.predicate.Equals;
import com.ibm.avatar.algebra.function.predicate.FollowsTok;
import com.ibm.avatar.algebra.function.scalar.Cast;
import com.ibm.avatar.algebra.function.scalar.CombineSpans;
import com.ibm.avatar.algebra.function.scalar.GetLengthTok;
import com.ibm.avatar.algebra.function.scalar.LeftContextTok;
import com.ibm.avatar.algebra.function.scalar.RightContextTok;
import com.ibm.avatar.algebra.util.dict.DictParams;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.aql.AQLParser;
import com.ibm.avatar.aql.BlockExNode;
import com.ibm.avatar.aql.ColNameNode;
import com.ibm.avatar.aql.ConsolidateClauseNode;
import com.ibm.avatar.aql.CreateDictNode;
import com.ibm.avatar.aql.CreateViewNode;
import com.ibm.avatar.aql.DictExNode;
import com.ibm.avatar.aql.ExtractListNode;
import com.ibm.avatar.aql.ExtractNode;
import com.ibm.avatar.aql.ExtractPatternNode;
import com.ibm.avatar.aql.FromListItemNode;
import com.ibm.avatar.aql.FromListItemSubqueryNode;
import com.ibm.avatar.aql.FromListItemTableFuncNode;
import com.ibm.avatar.aql.FromListItemViewRefNode;
import com.ibm.avatar.aql.FromListNode;
import com.ibm.avatar.aql.GroupByClauseNode;
import com.ibm.avatar.aql.HavingClauseNode;
import com.ibm.avatar.aql.IntNode;
import com.ibm.avatar.aql.MinusNode;
import com.ibm.avatar.aql.NickNode;
import com.ibm.avatar.aql.OrderByClauseNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.PatternAlternationNode;
import com.ibm.avatar.aql.PatternAtomColumnNode;
import com.ibm.avatar.aql.PatternAtomDictNode;
import com.ibm.avatar.aql.PatternAtomRegexNode;
import com.ibm.avatar.aql.PatternAtomTokenGapNode;
import com.ibm.avatar.aql.PatternExpressionNode;
import com.ibm.avatar.aql.PatternExpressionNode.GroupType;
import com.ibm.avatar.aql.PatternGroupNode;
import com.ibm.avatar.aql.PatternMultipleChildrenNode;
import com.ibm.avatar.aql.PatternOptionalNode;
import com.ibm.avatar.aql.PatternPassThroughColumn;
import com.ibm.avatar.aql.PatternRepeatNode;
import com.ibm.avatar.aql.PatternSequenceNode;
import com.ibm.avatar.aql.PatternSequenceNode.PositionInSequence;
import com.ibm.avatar.aql.PatternSequenceNode.SequenceType;
import com.ibm.avatar.aql.PatternSingleChildNode;
import com.ibm.avatar.aql.PredicateNode;
import com.ibm.avatar.aql.RValueNode;
import com.ibm.avatar.aql.RegexExNode;
import com.ibm.avatar.aql.RegexNode;
import com.ibm.avatar.aql.ReturnClauseNode;
import com.ibm.avatar.aql.ScalarFnCallNode;
import com.ibm.avatar.aql.SelectListItemNode;
import com.ibm.avatar.aql.SelectListNode;
import com.ibm.avatar.aql.SelectNode;
import com.ibm.avatar.aql.StringNode;
import com.ibm.avatar.aql.TableFnCallNode;
import com.ibm.avatar.aql.UnionAllNode;
import com.ibm.avatar.aql.ViewBodyNode;
import com.ibm.avatar.aql.WhereClauseNode;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.aql.catalog.ViewCatalogEntry;
import com.ibm.avatar.aql.compiler.ParseToCatalog;
import com.ibm.avatar.logging.Log;

/**
 * Base class for optimizer modules that perform AQL query rewrites. Provides useful instances of
 * the Visitor pattern for implementing the rewrites.
 * 
 */
public class AQLRewriter {

  /** Callback for rewriting various parts of the original query. */
  protected static abstract class rewriter<T> {
    abstract T rewrite(T orig) throws ParseException;

    /** Name of the view currently being rewritten (for debugging) */
    protected String curViewName;

    /** module name of the curViewName */
    protected String curModuleName = "";

    /**
     * Catalog, we require it for adding new views during rewrite, as well as record compiler
     * warnings
     */
    protected Catalog catalog;
  }

  protected static abstract class fromItemRewriter extends rewriter<FromListItemNode> {
  }

  protected static abstract class selectRewriter extends rewriter<SelectNode> {
  }

  protected static abstract class extractRewriter extends rewriter<ExtractNode> {
  }

  /**
   * Callback for rewriting "create dictionary" statements. Currently, there isn't an associated
   * "rewriteCreateDict()" method, since "create dictionary" statements don't have any kind of
   * recursion.
   */
  protected static abstract class createDictRewriter extends rewriter<CreateDictNode> {

  }

  /**
   * For rewriting select and extract statements at the same time. Currently used for inlining
   * non-correlated subqueries in the from clause of a select or extract statement.
   * 
   */
  protected static abstract class selectExtractRewriter extends rewriter<ViewBodyNode> {
  }

  /**
   * Callback for rewriting scalar function calls.
   * 
   */
  protected static abstract class funcRewriter extends rewriter<ScalarFnCallNode> {

  }

  /**
   * Callback for rewriting Extract Pattern statements.
   */
  protected static abstract class extractPatternRewriter extends rewriter<ViewBodyNode> {
  }

  /**
   * For rewriting pattern expression nodes.
   * 
   */
  protected static abstract class patternExpressionRewriter
      extends rewriter<PatternExpressionNode> {

  }

  /**
   * A generic in-place query rewrite; replaces every extract statement with a rewritten version.
   */
  protected static ViewBodyNode rewriteExtracts(extractRewriter callback, ViewBodyNode root,
      String moduleName, String viewName) throws ParseException {
    callback.curModuleName = moduleName;
    callback.curViewName = viewName;

    if (root instanceof SelectNode) {
      // Any extract statements below this select statement will have been
      // broken out into separate anonymous views.
      return root;

    } else if (root instanceof UnionAllNode) {

      // UNION ALL of multiple select statements
      UnionAllNode union = (UnionAllNode) root;

      for (int s = 0; s < union.getNumStmts(); s++) {
        ViewBodyNode stmt = union.getStmt(s);
        union.setStmt(s, rewriteExtracts(callback, stmt, moduleName, viewName));
      }

      return union;

    } else if (root instanceof MinusNode) {

      // Set difference over select statements
      MinusNode minus = (MinusNode) root;

      minus.setFirstStmt(rewriteExtracts(callback, minus.getFirstStmt(), moduleName, viewName));
      minus.setSecondStmt(rewriteExtracts(callback, minus.getSecondStmt(), moduleName, viewName));

      return minus;

    } else if (root instanceof ExtractNode) {

      return callback.rewrite((ExtractNode) root);

    } else if (root instanceof ExtractPatternNode) {
      // Re-write of sequence pattern (extract pattern ... stmt) is handled later; so return as it
      // is.
      return root;

    } else {
      throw new RuntimeException("Don't know how to rewrite " + root);
    }

  }

  /**
   * A generic query rewrite; replaces every select statement with a rewritten version.
   * 
   * @param callback callback that does the actual rewriting
   * @param root root node of the part of the parse tree to rewrite
   * @param moduleName name of the module the view belongs to
   * @param viewName name of the enclosing view
   * @return a rewritten version of the tree rooted at root
   */
  protected static ViewBodyNode rewriteSelects(selectRewriter callback, ViewBodyNode root,
      String moduleName, String viewName) throws ParseException {

    callback.curModuleName = moduleName;
    callback.curViewName = viewName;

    if (root instanceof SelectNode) {
      SelectNode stmt = (SelectNode) root;
      return callback.rewrite(stmt);
    } else if (root instanceof UnionAllNode) {

      // UNION ALL of multiple select statements
      UnionAllNode union = (UnionAllNode) root;

      for (int s = 0; s < union.getNumStmts(); s++) {
        ViewBodyNode stmt = union.getStmt(s);
        union.setStmt(s, rewriteSelects(callback, stmt, moduleName, viewName));
      }

      return union;

    } else if (root instanceof MinusNode) {

      // Set difference over select statements
      MinusNode minus = (MinusNode) root;

      minus.setFirstStmt(rewriteSelects(callback, minus.getFirstStmt(), moduleName, viewName));
      minus.setSecondStmt(rewriteSelects(callback, minus.getSecondStmt(), moduleName, viewName));

      return minus;

    } else if (root instanceof ExtractNode) {

      // No rewrites to do on an Extract node right now.
      return root;

    } else if (root instanceof ExtractPatternNode) {

      // No rewrites to do on an Extract node right now.
      return root;

    } else {
      throw new RuntimeException("Don't know how to rewrite " + root);
    }
  }

  /**
   * A generic query rewrite; replaces every select and extract statement with a rewritten version.
   * 
   * @param callback callback that does the actual rewriting
   * @param root root node of the part of the parse tree to rewrite
   * @param moduleName name of the module the view belongs to
   * @param viewName name of the enclosing view
   * @return a rewritten version of the tree rooted at root
   */
  protected static ViewBodyNode rewriteSelectsAndExtracts(selectExtractRewriter callback,
      ViewBodyNode root, String moduleName, String viewName) throws ParseException {

    int suffix;
    String suffixViewName;
    callback.curModuleName = moduleName;
    callback.curViewName = viewName;

    if (root instanceof SelectNode) {
      SelectNode stmt = (SelectNode) root;
      return callback.rewrite(stmt);
    } else if (root instanceof UnionAllNode) {

      // UNION ALL of multiple select statements
      UnionAllNode union = (UnionAllNode) root;

      for (int s = 0; s < union.getNumStmts(); s++) {
        ViewBodyNode stmt = union.getStmt(s);

        // Assign different suffixes to avoid collision of names on
        // the new created internal views we create
        suffix = s + 1;
        suffixViewName = String.format("%s_%s", viewName, suffix);

        union.setStmt(s, rewriteSelectsAndExtracts(callback, stmt, moduleName, suffixViewName));
      }

      return union;

    } else if (root instanceof MinusNode) {

      // Set difference over select statements
      MinusNode minus = (MinusNode) root;

      // Assign different suffixes to avoid collision of names on
      // the new created internal views we create
      suffixViewName = String.format("%s_%s", viewName, 1);
      minus.setFirstStmt(
          rewriteSelectsAndExtracts(callback, minus.getFirstStmt(), moduleName, suffixViewName));

      suffixViewName = String.format("%s_%s", viewName, 1);
      minus.setSecondStmt(
          rewriteSelectsAndExtracts(callback, minus.getSecondStmt(), moduleName, suffixViewName));

      return minus;

    } else if (root instanceof ExtractNode) {

      return callback.rewrite((ExtractNode) root);

    } else if (root instanceof ExtractPatternNode) {

      return callback.rewrite((ExtractPatternNode) root);

    } else {
      throw new RuntimeException("Don't know how to rewrite " + root);
    }
  }

  /**
   * A generic query rewrite; replaces every extract pattern statement with a rewritten version.
   * Assumes all subqueries have been inlined.
   * 
   * @param callback callback that does the actual rewriting
   * @param root root node of the part of the parse tree to rewrite
   * @param module name of the module the view belongs to
   * @param viewName name of the enclosing view
   * @return a rewritten version of the tree rooted at root
   */
  protected static ViewBodyNode rewriteExtractPatterns(extractPatternRewriter callback,
      ViewBodyNode root, String moduleName, String viewName) throws ParseException {

    callback.curModuleName = moduleName;
    callback.curViewName = viewName;

    if (root instanceof ExtractPatternNode) {
      ExtractPatternNode stmt = (ExtractPatternNode) root;
      return callback.rewrite(stmt);
    } else if (root instanceof UnionAllNode) {

      // UNION ALL of multiple select statements
      UnionAllNode union = (UnionAllNode) root;

      for (int s = 0; s < union.getNumStmts(); s++) {
        ViewBodyNode stmt = union.getStmt(s);
        union.setStmt(s, rewriteExtractPatterns(callback, stmt, moduleName, viewName));
      }

      return union;

    } else if (root instanceof MinusNode) {

      // Set difference over select statements
      MinusNode minus = (MinusNode) root;

      minus.setFirstStmt(
          rewriteExtractPatterns(callback, minus.getFirstStmt(), moduleName, viewName));
      minus.setSecondStmt(
          rewriteExtractPatterns(callback, minus.getSecondStmt(), moduleName, viewName));

      return minus;

    } else if (root instanceof ExtractNode) {

      // No rewrites to do on an Extract node. The assumption is that all subqueries have been
      // already inlined, and so
      // there isn't any subquery in the FROM clause.
      return root;

    } else if (root instanceof SelectNode) {

      // No rewrites to do on an Select node. The assumption is that all subqueries have been
      // already inlined, and so
      // there isn't any subquery in the FROM clause.
      return root;

    } else {
      throw new RuntimeException("Don't know how to rewrite " + root);
    }
  }

  /**
   * In-place rewrite of table functions; will go away as soon as the table functions go away.
   */
  protected void rewriteTabFuncs(fromItemRewriter callback, CreateViewNode cvn)
      throws ParseException {
    final fromItemRewriter tabFuncCB = callback;

    // Create a visitor that will modify each node in turn.
    class cb extends selectRewriter {

      @Override
      SelectNode rewrite(SelectNode orig) throws ParseException {
        FromListNode fromList = orig.getFromList();
        for (int i = 0; i < fromList.size(); i++) {
          FromListItemNode origItem = fromList.get(i);

          if (origItem instanceof FromListItemTableFuncNode) {
            // Found a table function; rewrite it.
            tabFuncCB.curViewName = this.curViewName;
            tabFuncCB.curModuleName = this.curModuleName;
            tabFuncCB.catalog = this.catalog;
            FromListItemNode newItem = tabFuncCB.rewrite(origItem);
            fromList.set(i, newItem);
          }
        }
        return orig;
      }

    }

    // Pass the visitor into the function that will iterate over all the
    // select statements in the indicated view.
    cvn.setBody(
        rewriteSelects(new cb(), cvn.getBody(), cvn.getModuleName(), cvn.getUnqualifiedName()));

  }

  /**
   * String that is used in constructing internal names for subqueries. Intended to be a string
   * that's extremely unlikely to appear in an actual view name.
   */
  protected static final String SUBQUERY_NAME_PUNCT = "\u2761subquery";

  /**
   * In-place rewrite of non-correlated subqueries inlined in the FROM clause of a select or extract
   * statement. Pulls out the subquery as an internal view and replaces the subquery with a
   * reference to this internal view in the FROM clause. Processes recursively any subqueries nested
   * within subqueries in the select or extracts clauses.
   */
  protected void rewriteSubqueries(fromItemRewriter callback, CreateViewNode cvn)
      throws ParseException {
    final fromItemRewriter subqueryCB = callback;

    // Create a visitor that will modify each node in turn.
    class cb extends selectExtractRewriter {

      @Override
      ViewBodyNode rewrite(ViewBodyNode orig) throws ParseException {

        if (orig instanceof SelectNode || orig instanceof ExtractPatternNode) {
          // Inline all subqueries in the from clause

          FromListNode fromList = orig instanceof SelectNode ? ((SelectNode) orig).getFromList()
              : ((ExtractPatternNode) orig).getFromList();

          String parentSubqueryName = this.curViewName;

          int childSubqueryNum = 0;

          for (int i = 0; i < fromList.size(); i++) {
            FromListItemNode origItem = fromList.get(i);

            if (origItem instanceof FromListItemSubqueryNode) {
              // Found a child subquery; rewrite it.
              FromListItemSubqueryNode subqueryItem = (FromListItemSubqueryNode) origItem;

              // First create a name for the current child
              // subquery
              // This name will be passed as current view name
              // when rewriting any subqueries in the from clause
              // of
              // the current child subquery
              childSubqueryNum++;
              String childSubqueryName =
                  String.format("%s%s%d", this.curViewName, SUBQUERY_NAME_PUNCT, childSubqueryNum);

              // Process the child subqueries underneath this one,
              // if any
              subqueryItem.setBody(rewriteSelectsAndExtracts(this, subqueryItem.getBody(),
                  this.curModuleName, childSubqueryName));

              // Rewrite this child subquery (first pass its name
              // to the callback)
              subqueryCB.curViewName = childSubqueryName;
              FromListItemNode newItem = subqueryCB.rewrite(subqueryItem);
              fromList.set(i, newItem);

              // Finally, restore the name of the parent subquery,
              // so that it can be passed correctly to its
              // remaining child subqueries
              this.curViewName = parentSubqueryName;
            }
          }
        } else if (orig instanceof ExtractNode) {
          // Inline the subquery in the from clause

          FromListItemNode origItem = ((ExtractNode) orig).getTarget();

          if (origItem instanceof FromListItemSubqueryNode) {
            // The target is a subquery; rewrite it.
            FromListItemSubqueryNode subqueryItem = (FromListItemSubqueryNode) origItem;

            // First create a name for the target subquery
            // This name will be passed as current view name when
            // rewriting any subqueries in the from clause of the
            // targetsubquery
            String childSubqueryName = String.format("%s_%d", this.curViewName, 1);

            // Process the child subqueries underneath this one, if
            // any
            subqueryItem.setBody(rewriteSelectsAndExtracts(this, subqueryItem.getBody(),
                this.curModuleName, childSubqueryName));

            // Rewrite this target subquery (first pass its name to
            // the callback)
            subqueryCB.curViewName = childSubqueryName;
            FromListItemNode newItem = subqueryCB.rewrite(subqueryItem);
            ((ExtractNode) orig).setTarget(newItem);
          }
        }
        return orig;
      }

    }

    // Pass the visitor into the function that will iterate over all the
    // select statements in the indicated view.
    cvn.setBody(rewriteSelectsAndExtracts(new cb(), cvn.getBody(), cvn.getModuleName(),
        cvn.getUnqualifiedName()));

  }

  /**
   * In-place rewrite of scalar function calls. Assumes that all subqueries have been unnested.
   * 
   * @param callback callback function that does the work of replacing function calls
   */
  protected static void rewriteScalarFuncs(final funcRewriter callback, CreateViewNode cvn)
      throws ParseException {

    // Create a visitor that will visit each select or extract node in turn.
    class cb extends selectExtractRewriter {

      @Override
      ViewBodyNode rewrite(ViewBodyNode orig) throws ParseException {

        // SelectList and Consolidate clause are common in both Select
        // and extract stmt
        SelectListNode selectList = null;
        ConsolidateClauseNode consolidateClause = null;

        if (orig instanceof SelectNode) {
          // SELECT statement; look in the select,where and consolidate clauses
          // for function calls.
          SelectNode select = (SelectNode) orig;

          // Since select list and consolidate clause are common to
          // both select and extract stmt, rewrite for them is
          // after the if-else
          selectList = select.getSelectList();
          consolidateClause = select.getConsolidateClause();

          // Where clause is currently only found on select stmts
          WhereClauseNode whereClause = select.getWhereClause();
          if (null != whereClause) {
            for (int i = 0; i < whereClause.size(); i++) {
              PredicateNode pred = whereClause.getPred(i);
              ScalarFnCallNode newFunc = rewriteSingleFunc(pred.getFunc(), callback);
              pred.replaceFunc(newFunc);
            }
          }

          // Group by clause is currently only found on select stmts
          GroupByClauseNode groupByClause = select.getGroupByClause();
          if (null != groupByClause) {
            for (int i = 0; i < groupByClause.size(); i++) {
              RValueNode value = groupByClause.getValue(i);
              if (value instanceof ScalarFnCallNode) {
                ScalarFnCallNode newVal = rewriteSingleFunc((ScalarFnCallNode) value, callback);
                groupByClause.replaceValue(i, newVal);
              }
            }
          }

          // Order by clause is currently only found on select stmts
          OrderByClauseNode orderByClause = select.getOrderByClause();
          if (null != orderByClause) {
            for (int i = 0; i < orderByClause.size(); i++) {
              RValueNode value = orderByClause.getValue(i);
              if (value instanceof ScalarFnCallNode) {
                ScalarFnCallNode newVal = rewriteSingleFunc((ScalarFnCallNode) value, callback);
                orderByClause.replaceValue(i, newVal);
              }
            }
          }

        } else if (orig instanceof ExtractNode || orig instanceof ExtractPatternNode) {

          HavingClauseNode havingClause = null;
          if (orig instanceof ExtractNode) {
            // EXTRACT statement; check the having clause,consolidate clause and select list for
            // function
            // calls
            ExtractNode extract = (ExtractNode) orig;
            selectList = extract.getExtractList().getSelectList();
            consolidateClause = extract.getConsolidateClause();
            havingClause = extract.getHavingClause();
          } else {
            ExtractPatternNode pattern = (ExtractPatternNode) orig;
            selectList = pattern.getSelectList();
            consolidateClause = pattern.getConsolidateClause();
            havingClause = pattern.getHavingClause();
          }

          if (null != havingClause) {
            for (int i = 0; i < havingClause.size(); i++) {
              PredicateNode pred = havingClause.getPred(i);
              ScalarFnCallNode newFunc = rewriteSingleFunc(pred.getFunc(), callback);
              pred.replaceFunc(newFunc);
            }
          }

        }

        // Apply callback to function calls in select list
        if (null != selectList) {
          for (int i = 0; i < selectList.size(); i++) {
            SelectListItemNode item = selectList.get(i);
            RValueNode value = item.getValue();
            if (value instanceof ScalarFnCallNode) {
              ScalarFnCallNode newVal = rewriteSingleFunc((ScalarFnCallNode) value, callback);
              item.replaceValue(newVal);
            }
          }
        }

        // Apply callback to function call in consolidate clause
        if (null != consolidateClause) {
          RValueNode target = consolidateClause.getTarget();
          if (target instanceof ScalarFnCallNode) {
            ScalarFnCallNode newTarget = rewriteSingleFunc((ScalarFnCallNode) target, callback);
            consolidateClause.replaceTarget(newTarget);
          }

          // If the consolidate clause has a "with priority from" clause,
          // rewrite the target of the "with priority from" clause.
          RValueNode priorityTarget = consolidateClause.getPriorityTarget();
          if (priorityTarget instanceof ScalarFnCallNode) {
            ScalarFnCallNode newPriorityTarget =
                rewriteSingleFunc((ScalarFnCallNode) priorityTarget, callback);
            consolidateClause.replacePriorityTarget(newPriorityTarget);
          }
        }

        // All of our changes happened in place, so just return the
        // original top-level node.
        return orig;
      }

    }

    // Pass the visitor into the function that will iterate over all the
    // select statements in the indicated view.
    cvn.setBody(rewriteSelectsAndExtracts(new cb(), cvn.getBody(), cvn.getModuleName(),
        cvn.getUnqualifiedName()));
  }

  /**
   * Subroutine of {@link #rewriteScalarFuncs(funcRewriter, CreateViewNode)}. Recursively rewrites
   * the children of a function call, then does the call itself.
   * 
   * @throws ParseException
   */
  private static ScalarFnCallNode rewriteSingleFunc(ScalarFnCallNode input, funcRewriter callback)
      throws ParseException {

    // First, rewrite the children of the function call.
    ArrayList<RValueNode> args = input.getArgs();
    for (int i = 0; i < args.size(); i++) {
      RValueNode orig = args.get(i);

      if (orig instanceof ScalarFnCallNode) {
        ScalarFnCallNode origFunc = (ScalarFnCallNode) orig;
        ScalarFnCallNode newFunc = rewriteSingleFunc(origFunc, callback);

        // Assume we can replace in place.
        args.set(i, newFunc);
      }
    }

    // Now we can do the function call itself.
    return callback.rewrite(input);
  }

  protected PatternExpressionNode rewritePatternExpression(patternExpressionRewriter callback,
      PatternExpressionNode root, String moduleName, String viewName) throws ParseException {

    callback.curModuleName = moduleName;
    callback.curViewName = viewName;

    // There is no rewrite for token gap nodes
    if (root instanceof PatternAtomTokenGapNode)
      return root;

    // Handle terminal nodes
    if (root instanceof PatternAtomRegexNode || root instanceof PatternAtomDictNode
        || root instanceof PatternAtomColumnNode) {
      return callback.rewrite(root);
    }

    // Handle nodes with multiple children
    else if (root instanceof PatternAlternationNode || root instanceof PatternSequenceNode) {

      // Alternation of multiple Sequence nodes
      PatternMultipleChildrenNode node = (PatternMultipleChildrenNode) root;

      for (int i = 0; i < node.getNumChildren(); i++)
        node.setChildAtIdx(i, callback.rewrite(node.getChild(i)));

      return node;
    }

    // Handle nodes with a single child
    else if (root instanceof PatternOptionalNode || root instanceof PatternRepeatNode
        || root instanceof PatternGroupNode) {

      PatternSingleChildNode node = (PatternSingleChildNode) root;

      node.setChild(callback.rewrite(node.getChild()));

      return node;
    } else {
      throw new RuntimeException("Don't know how to rewrite " + root);
    }

  }

  /**
   * Labels the groups defined by a sequence pattern specification. At the same time, it records
   * which of these are to be returned.
   * 
   * @param cvn
   * @throws ParseException
   */
  protected void labelGroups(CreateViewNode cvn) throws ParseException {

    // Visitor for rewriting the pattern expression nodes
    class labelGroupsCB extends patternExpressionRewriter {

      private final boolean debug = false;

      private int nextGroupId = 1;
      private final ArrayList<Integer> groupIDsSoFar = new ArrayList<Integer>();
      protected ArrayList<Integer> groupIDsToReturn;

      @Override
      PatternExpressionNode rewrite(PatternExpressionNode orig) throws ParseException {

        // Step 1: Process this node
        if (orig instanceof PatternGroupNode) {

          // Found a new group
          groupIDsSoFar.add(new Integer(nextGroupId));
          nextGroupId++;

        } else if (!orig.producesSameResultAsChild() && !(orig instanceof PatternOptionalNode)) {

          // Assign the groups found so far to this node
          orig.setGroupType(GroupType.CAPTURING);
          orig.setGroupIDs(groupIDsSoFar);
          if (debug && !groupIDsSoFar.isEmpty())
            Log.debug("Pattern %s: --- Added group ids: %s\n", orig, groupIDsSoFar);

          // Determine which of these group IDs are to be returned
          ArrayList<Integer> toReturn = new ArrayList<Integer>();
          toReturn.addAll(groupIDsSoFar);
          toReturn.retainAll(groupIDsToReturn);
          orig.setGroupIDsToReturn(toReturn);
          if (debug && !toReturn.isEmpty())
            Log.debug("Pattern %s: --- Added return group ids: %s\n", orig, toReturn);

          groupIDsSoFar.clear();
        }

        // Step 2: Process any child nodes
        if (orig instanceof PatternMultipleChildrenNode || orig instanceof PatternSingleChildNode)
          rewritePatternExpression(this, orig, curModuleName, curViewName);

        // Step 3: For capturing groups of optionals of the form (X?),
        // swap the capturing group with the optional (X?) -> (X)?
        // in an attempt to minimize the situations in which the
        // pattern cleanup phase cannot group an optional with any required element

        if (orig instanceof PatternGroupNode) {
          PatternGroupNode group = (PatternGroupNode) orig;

          if (group.getChild() instanceof PatternOptionalNode) {
            PatternOptionalNode optional = (PatternOptionalNode) group.getChild();

            group.setChild(optional.getChild());
            optional.setChild(group);
            return optional;
          }
        }

        // Step 4: Otherwise, the rewrite happened in place, so just return the original node
        return orig;
      }

      public void setGroupIDsToReturn(ArrayList<Integer> groupIDs) {
        groupIDsToReturn = groupIDs;
      }

    }

    // Create a visitor that will modify each node in turn.
    class cb extends extractPatternRewriter {
      @Override
      ViewBodyNode rewrite(ViewBodyNode orig) throws ParseException {
        if (orig instanceof ExtractPatternNode) {

          ExtractPatternNode patternNode = (ExtractPatternNode) orig;

          // Label the groups, remembering which ones are to be retained
          labelGroupsCB patternCB = new labelGroupsCB();
          patternCB.curViewName = this.curViewName;
          patternCB.curModuleName = this.curModuleName;

          if (patternCB.debug) {
            Log.debug("LABELING GROUPS for [%s]", this.curViewName);
          }

          patternCB.setGroupIDsToReturn(patternNode.getReturnClause().getGroupIDs());

          patternNode.setPattern(patternCB.rewrite(patternNode.getPattern()));

          // Validate the return clause of the extract sequence pattern statement
          patternNode.validateGroupIDs();
        }

        // Rewrite happened in place, so just return the original
        return orig;
      }
    }

    // Pass the visitor into the function that will iterate over all the
    // select statements in the indicated view.
    cvn.setBody(rewriteExtractPatterns(new cb(), cvn.getBody(), cvn.getModuleName(),
        cvn.getUnqualifiedName()));
  }

  /**
   * Labels pattern expression subcomponents with the pass through columns that must be output in
   * the select list of the extract pattern statement.
   * 
   * @param cvn original parse tree node for the view being rewritten
   * @param catalog pointer to the AQL catalog, for looking up field types
   * @throws ParseException
   */
  protected void labelPassThroughCols(CreateViewNode cvn, Catalog catalog) throws ParseException {

    // Visitor for rewriting the pattern expression nodes
    class labelPassThroughColsCB extends patternExpressionRewriter {

      private final boolean debug = false;

      /**
       * Set of pass through col refs from the select list of a EXTRACT PATTERN statement
       */
      protected Set<PatternPassThroughColumn> passThroughCols;

      /**
       * Set of pass through cols that are mentioned under a repeat elements. Only used for
       * validation of the EXTRACT PATTERN (currently we do not allow pass through cols from under
       * repeatable elements, so validation will fail if this set is non-empty.
       */
      protected Set<PatternPassThroughColumn> passThroughColsUnderRepeat =
          new TreeSet<PatternPassThroughColumn>();

      @Override
      PatternExpressionNode rewrite(PatternExpressionNode orig) throws ParseException {

        // Step 1: process the node

        // PatternAtomColumnNode: verify whether there is any pass through column to pull from this
        // node
        if (orig instanceof PatternAtomColumnNode) {

          // Obtain the column name of this node
          ColNameNode col = ((PatternAtomColumnNode) orig).getCol();

          // Obtain the view alias
          String tabName = col.getTabname();

          // Examine the list of all pass through cols specified in the select list
          // And pull out from this node those with the same view alias
          ArrayList<PatternPassThroughColumn> cols = new ArrayList<PatternPassThroughColumn>();
          for (PatternPassThroughColumn passThroughCol : passThroughCols) {
            if (passThroughCol.getViewAlias().equals(tabName)) {
              // Found a pass through col that must be pulled from this node
              cols.add(passThroughCol);
            }
          }
          ((PatternAtomColumnNode) orig).setPassThroughCols(cols);

        }
        // PatternMultipleChildrenNode: collect the pass through cols from all children
        else if (orig instanceof PatternMultipleChildrenNode
            || orig instanceof PatternSingleChildNode) {
          rewritePatternExpression(this, orig, curModuleName, curViewName);
        }

        // Step 2: Collect any pass through cols from repeatable elements, used later for validation
        // of the EXTRACT
        // PATTERN statement
        if (orig instanceof PatternRepeatNode) {
          passThroughColsUnderRepeat.addAll(orig.getPassThroughCols());
        }

        if (debug && orig.getPassThroughCols().size() > 0)
          Log.debug("Pattern %s: --- Added pass through cols: %s\n", orig,
              orig.getPassThroughCols());

        // Step 3: The rewrite happened in place, so just return the original node
        return orig;
      }

      public void setPassThroughCols(Set<PatternPassThroughColumn> cols) {
        passThroughCols = cols;
      }

    }

    // Create a visitor that will modify each node in turn.
    class cb extends extractPatternRewriter {
      public cb(Catalog catalog) {
        this.catalog = catalog;
      }

      @Override
      ViewBodyNode rewrite(ViewBodyNode orig) throws ParseException {
        if (orig instanceof ExtractPatternNode) {

          ExtractPatternNode patternNode = (ExtractPatternNode) orig;

          // Label the groups, remembering which ones are to be retained
          labelPassThroughColsCB patternCB = new labelPassThroughColsCB();
          patternCB.curViewName = this.curViewName;
          patternCB.curModuleName = this.curModuleName;

          if (patternCB.debug) {
            Log.debug("LABELING PASS THROUGH COLS for [%s]", this.curViewName);
          }

          // Set the list of pass through cols in the callback, appending type information
          TreeSet<String> qualifiedColNames = new TreeSet<String>();
          patternNode.getSelectList().getReferencedCols(qualifiedColNames, catalog);
          TreeSet<PatternPassThroughColumn> passthroughCols =
              new TreeSet<PatternPassThroughColumn>();

          for (String qualifiedColName : qualifiedColNames) {
            PatternPassThroughColumn passthru =
                new PatternPassThroughColumn(qualifiedColName, null, true);

            // Turn the alias into a view name and fetch the type information from the catalog
            String viewAlias = passthru.getViewAlias();
            FromListItemNode fromNode = patternNode.getFromList().getByName(viewAlias);
            TupleSchema targetSchema = SchemaInferrer.inferTargetType(catalog, fromNode);
            FieldType type = targetSchema.getFieldTypeByName(passthru.getColName());
            passthru.setType(type);

            passthroughCols.add(passthru);
          }
          patternCB.setPassThroughCols(passthroughCols);

          // The callback traverses the PatternNode and labels each subnode with a list of pass
          // through cols
          patternNode.setPattern(patternCB.rewrite(patternNode.getPattern()));

          // Validate that no pass through col refs are coming from views involved in repeatable
          // elements
          if (patternCB.passThroughColsUnderRepeat != null
              && patternCB.passThroughColsUnderRepeat.size() > 0)
            throw ParseToCatalog.makeWrapperException(AQLParser.makeException(String.format(
                "Invalid attributes %s: attributes of view names that appear within repeatable elements of "
                    + "the pattern expression are not allowed in the select list of the EXTRACT statement.",
                patternCB.passThroughColsUnderRepeat), patternNode.getOrigTok()),
                patternNode.getContainingFileName());
        }

        // Rewrite happened in place, so just return the original
        return orig;
      }
    }

    // Pass the visitor into the function that will iterate over all the
    // select statements in the indicated view.
    cvn.setBody(rewriteExtractPatterns(new cb(catalog), cvn.getBody(), cvn.getModuleName(),
        cvn.getUnqualifiedName()));
  }

  /**
   * Detect common sub-expressions in a sequence pattern specification.
   * 
   * @param cb callback that does the actual work
   * @param cvn
   * @throws ParseException
   */
  protected void detectCSE(patternExpressionRewriter callback, CreateViewNode cvn)
      throws ParseException {

    final patternExpressionRewriter patternCB = callback;
    final boolean debug = false;

    // Create a visitor that will modify each node in turn.
    class cb extends extractPatternRewriter {
      @Override
      ViewBodyNode rewrite(ViewBodyNode orig) throws ParseException {
        patternCB.curViewName = this.curViewName;
        patternCB.curModuleName = this.curModuleName;

        if (orig instanceof ExtractPatternNode) {

          if (debug) {
            Log.debug("DETECT CSE for [%s]", patternCB.curViewName);
          }
          ExtractPatternNode patternNode = (ExtractPatternNode) orig;
          patternNode.setPattern(patternCB.rewrite(patternNode.getPattern()));
        }

        // Rewrite happened in place, so just return the original
        return orig;
      }
    }

    // Pass the visitor into the function that will iterate over all the
    // select statements in the indicated view.
    cvn.setBody(rewriteExtractPatterns(new cb(), cvn.getBody(), cvn.getModuleName(),
        cvn.getUnqualifiedName()));
  }

  /**
   * Performs a series of transformations on a sequence pattern expression to simplify the rewrite
   * process. Mainly: 1) Repeatable elements are first split into normalized variable-length part,
   * and one or two fixed length parts. The latter fixed-length parts are split into sequences. 2)
   * Sequence elements are transformed into one of three forms, by inserting additional
   * non-capturing groups: - A linear sequence of required elements - Optional element followed by
   * required element - Required element followed by optional element
   * 
   * @param cvn
   * @param catalog
   * @throws ParseException
   */
  protected void patternCleanup(CreateViewNode cvn, Catalog c) throws ParseException {

    // Visitor for rewriting all repeat nodes
    class processRepeatCB extends patternExpressionRewriter {

      private final boolean debug = false;
      PositionInSequence pos = PositionInSequence.UNDEFINED;

      @Override
      PatternExpressionNode rewrite(PatternExpressionNode orig) throws ParseException {

        if (orig instanceof PatternSequenceNode) {

          // Sequence node: check to see if it has any repeat nodes as direct children
          // and if so, remember their position in this sequence which is useful
          // when rewriting each repeat child node.

          PatternSequenceNode sequence = (PatternSequenceNode) orig;
          ArrayList<PatternExpressionNode> newChildren = new ArrayList<PatternExpressionNode>();

          if (debug)
            Log.debug("Rewriting sequence: %s", sequence);

          for (int i = 0; i < sequence.getNumChildren(); i++) {

            PatternExpressionNode child = sequence.getChild(i);

            if (child instanceof PatternRepeatNode) {

              // Remember the position of the repeat in the parent sequence
              if (i == 0)
                pos = PositionInSequence.BEGIN;
              else if (i == sequence.getNumChildren() - 1)
                pos = PositionInSequence.END;
              else
                pos = PositionInSequence.MIDDLE;

              // Rewrite this repeat node
              PatternExpressionNode rewrittenChild = this.rewrite(child);

              // If the repeat node has been rewritten into a sequence node,
              // add all its children to this current sequence
              if (rewrittenChild instanceof PatternSequenceNode)
                newChildren.addAll(((PatternSequenceNode) rewrittenChild).getChildren());

              // Otherwise, the repeat node was unchanged, or rewritten into an optional node
              // so we just replace the original repeat with its rewrite in this sequence
              else
                newChildren.add(rewrittenChild);

              // Finished processing this child, so we reset
              pos = PositionInSequence.UNDEFINED;
            } else
              // Process the child and add the resulting node to the new list of children
              newChildren.add(rewritePatternExpression(this, child, curModuleName, curViewName));
          }

          sequence.setChildren(newChildren);

          if (debug)
            Log.debug("Rewritten sequence to: %s", sequence);
        } else if (orig instanceof PatternRepeatNode) {

          // Repeat node: rewrite it!
          PatternRepeatNode repeat = (PatternRepeatNode) orig;

          if (debug)
            Log.debug("Rewriting repeat: %s", repeat);

          // Remember the position locally, and reset the global
          // variable
          // to avoid problems when processing the child of this node
          PositionInSequence inheritedPos = pos;
          pos = PositionInSequence.UNDEFINED;

          // Process the child of this repeat node
          rewritePatternExpression(this, orig, curModuleName, curViewName);

          // Process this repeat node itself
          int min = repeat.getMinOccurrences().getValue();
          int max = repeat.getMaxOccurrences().getValue();

          if (min == max) {
            // Node has the form R{n,n} - remains unchanged
            return repeat;
          }
          if (min == 1) {
            // Node has the form R{1,n} - remains unchanged
            if (debug)
              Log.debug("Rewrittten repeat as: %s", repeat);

            return repeat;
          } else if (min == 0) {
            // Node of type R{0,n} - transform to X{1,n}?
            repeat.setMinOccurrences(new IntNode(1, null, null));

            PatternOptionalNode optional = new PatternOptionalNode(repeat);
            optional.setCseID(repeat.getCseID());
            repeat.setCseID(PatternExpressionNode.UNASSIGNED_CSE_ID);

            if (debug)
              Log.debug("Rewrittten repeat as: %s", optional);

            return optional;
          } else {
            // Node of type R{3,5} - transformation into a sequence
            // depending on the position in parent sequence

            ArrayList<PatternExpressionNode> newNodes = new ArrayList<PatternExpressionNode>();

            if (inheritedPos.equals(PositionInSequence.UNDEFINED))
              inheritedPos = PositionInSequence.END;

            int newMin = 1;
            int newMax = max - min;
            int cseID = repeat.getCseID();
            ArrayList<Integer> returnGroupIDs = repeat.getReturnGroupIDs();
            ArrayList<Integer> groupIDs = repeat.getGroupIDs();

            // Create common sub-expression "R{1,2}" needed for all
            // 3 types of rewrites
            PatternExpressionNode commonSubExpr;

            if (newMin == newMax) {
              commonSubExpr = new PatternOptionalNode(repeat.getChild());
            } else {
              repeat.setCseID(PatternExpressionNode.UNASSIGNED_CSE_ID);
              repeat.setGroupIDs(new ArrayList<Integer>());
              repeat.setGroupIDsToReturn(new ArrayList<Integer>());
              repeat.setMinOccurrences(new IntNode(newMin, null, null));
              repeat.setMaxOccurrences(new IntNode(newMax, null, null));
              commonSubExpr = new PatternOptionalNode(repeat);
            }

            // If at beginning of the sequence,
            // rewrite R{3,5} to -> R{0,2}R{3} -> R{1,2}?RRR
            if (inheritedPos == PositionInSequence.BEGIN) {

              // Add "R{1,2}?"
              newNodes.add(commonSubExpr);

              // Add the rest of the rewritten expression "RRR"
              for (int i = 0; i < min; i++)
                newNodes.add(repeat.getChild());
            }

            // If at the end of the sequence,
            // rewrite R{3,5} to -> R{3}R{0,2} -> RRRR{1,2}?
            else if (inheritedPos == PositionInSequence.END) {

              // Add the expression "RRR"
              for (int i = 0; i < min; i++)
                newNodes.add(repeat.getChild());

              // Add "R{1,2}?"
              newNodes.add(commonSubExpr);
            }

            // If in the middle of the sequence,
            // rewrite R{3,5} -> R{2}R{0,2}R{1} -> RRR{1,2}?R
            else if (inheritedPos == PositionInSequence.MIDDLE) {

              // Add the prefix "RR"
              for (int i = 0; i < min - 1; i++)
                newNodes.add(repeat.getChild());

              // Add "R{1,2}?"
              newNodes.add(commonSubExpr);

              // Add the suffix "R"
              newNodes.add(repeat.getChild());
            }

            PatternSequenceNode sequence = null;

            // Finally, create a new sequence node
            if (newNodes.size() >= 0) {
              sequence = new PatternSequenceNode(newNodes);

              // Make sure to maintain the CSE ID and the group IDs
              sequence.setCseID(cseID);
              sequence.setGroupIDs(groupIDs);
              sequence.setGroupIDsToReturn(returnGroupIDs);

              if (debug)
                Log.debug("Rewrittten repeat as: %s", sequence);
            } else {
              throw new FatalInternalError(
                  "Attempted to create a pattern sequence node without any nodes in it.");
            }

            return sequence;
          }

        }

        // Process all other types of non-terminal nodes
        else if (orig instanceof PatternMultipleChildrenNode
            || orig instanceof PatternSingleChildNode)
          rewritePatternExpression(this, orig, curModuleName, curViewName);

        // If we reached this point, the rewrite happened in place,
        // or no rewrite is necessary, so just return the original node
        return orig;
      }

    }

    // Visitor for rewriting all sequence nodes
    class processSequenceCB extends patternExpressionRewriter {

      private final boolean debug = false;

      @Override
      PatternExpressionNode rewrite(PatternExpressionNode orig) throws ParseException {

        if (orig instanceof PatternSequenceNode) {

          PatternSequenceNode sequence = (PatternSequenceNode) orig;
          ArrayList<PatternExpressionNode> newChildren = new ArrayList<PatternExpressionNode>();
          ArrayList<PatternExpressionNode> optionals = new ArrayList<PatternExpressionNode>();
          ArrayList<PatternExpressionNode> nodesToGroup;
          PatternExpressionNode newChild;

          if (debug)
            Log.debug("Clean-up sequence: %s", sequence);

          // First, we rewrite the children of this sequence
          rewritePatternExpression(this, orig, curModuleName, curViewName);

          // Keep track of token gaps
          PatternExpressionNode tokenGap = null;

          // Second, make one pass over the children and group together all non-optional ones
          for (int i = 0; i < sequence.getNumChildren(); i++) {

            // Process the child and add the resulting node to the new list of children
            PatternExpressionNode child = sequence.getChild(i);

            // Code to transform (X?) -> (X)? , so that node gets picked up for pattern cleanup
            if (child instanceof PatternGroupNode) {
              PatternGroupNode group = (PatternGroupNode) child;
              if (group.getChild() instanceof PatternOptionalNode) {
                PatternOptionalNode optional = (PatternOptionalNode) group.getChild();
                group.setChild(optional.getChild());
                optional.setChild(group);

                child = optional;
              }
            }

            // Token gap
            if (child instanceof PatternAtomTokenGapNode) {
              // A token gap node is handled at the same time with
              // the (non-token gap) node it precedes in the sequence
              tokenGap = child;
              continue;
            }
            // Optional node
            else if (child.isOptional()) {

              // Try to group the optional element with the element before
              if (!newChildren.isEmpty()) {

                nodesToGroup = new ArrayList<PatternExpressionNode>();
                nodesToGroup.add(newChildren.get(newChildren.size() - 1));

                // Handle the preceding token gap, if any
                if (tokenGap != null) {
                  nodesToGroup.add(tokenGap);
                  tokenGap = null;
                }

                nodesToGroup.add(child);
                newChild = new PatternGroupNode(new PatternSequenceNode(nodesToGroup));
                newChild.setGroupType(GroupType.NON_CAPTURING);

                newChildren.set(newChildren.size() - 1, newChild);
              }
              // If it cannot be grouped, remember it so as to handle it later on
              else {
                // Handle the preceding token gap, if any
                if (tokenGap != null) {
                  optionals.add(tokenGap);
                  tokenGap = null;
                }
                optionals.add(child);
              }
            }
            // Non-optional node:
            else {
              if (optionals.isEmpty()) {
                // If no optionals to group with, simply add it to the new list of children
                // Also add the preceding token gap, if any
                if (tokenGap != null) {
                  newChildren.add(tokenGap);
                  tokenGap = null;
                }
                newChildren.add(child);
              } else {
                // Use the non-optional node to group together all optional nodes
                // encountered so far that we could not group at that time
                newChild = child;

                // Group the resulting node with all the optionals, if any
                for (int j = optionals.size() - 1; j >= 0; j--) {

                  // A token gap is handled at the same time with the optional it follows
                  if (optionals.get(j) instanceof PatternAtomTokenGapNode) {
                    tokenGap = optionals.get(j);
                    continue;
                  }

                  nodesToGroup = new ArrayList<PatternExpressionNode>();

                  // Add the optional
                  nodesToGroup.add(optionals.get(j));

                  // Add the token gap, if any
                  if (tokenGap != null) {
                    nodesToGroup.add(tokenGap);
                    tokenGap = null;
                  }

                  // Add the non-optional
                  nodesToGroup.add(newChild);

                  newChild = new PatternGroupNode(new PatternSequenceNode(nodesToGroup));
                  newChild.setGroupType(GroupType.NON_CAPTURING);
                }

                optionals.clear();
                newChildren.add(newChild);
              }
            }
          }

          if (newChildren.size() == 0) {
            if (optionals.size() == 0)
              throw new ParseException(String.format("In view %s:\n"
                  + "Don't know how to generate AQL for pattern sub-expression: %s. "
                  + "Each sequence must contain at least one element.", curViewName, sequence));
            newChild = optionals.get(optionals.size() - 1);
            // Group the resulting node with all the optionals, if any
            for (int j = optionals.size() - 2; j >= 0; j--) {

              // A token gap is handled at the same time with the optional it follows
              if (optionals.get(j) instanceof PatternAtomTokenGapNode) {
                tokenGap = optionals.get(j);
                continue;
              }

              nodesToGroup = new ArrayList<PatternExpressionNode>();

              // Add the optional
              nodesToGroup.add(optionals.get(j));

              // Add the token gap, if any
              if (tokenGap != null) {
                nodesToGroup.add(tokenGap);
                tokenGap = null;
              }

              // Add the optional
              nodesToGroup.add(newChild);

              newChild = new PatternGroupNode(new PatternSequenceNode(nodesToGroup));
              newChild.setGroupType(GroupType.NON_CAPTURING);
            }

            optionals.clear();
            newChildren.add(newChild);
          }

          sequence.setChildren(newChildren);

          if (debug)
            Log.debug("Cleaned up sequence to: %s", sequence);
        }

        // Recursively visit all other types of non-terminal nodes
        else if (orig instanceof PatternMultipleChildrenNode
            || orig instanceof PatternSingleChildNode)
          rewritePatternExpression(this, orig, curModuleName, curViewName);

        // If we reached this point, the rewrite happened in place,
        // or no rewrite is necessary, so just return the original node
        return orig;
      }
    }

    // Create a visitor that will modify each node in turn.
    class cb extends extractPatternRewriter {

      final static boolean debug = false;

      @Override
      ViewBodyNode rewrite(ViewBodyNode orig) throws ParseException {
        if (orig instanceof ExtractPatternNode) {

          ExtractPatternNode patternNode = (ExtractPatternNode) orig;
          PatternExpressionNode pattern = patternNode.getPattern();

          // Rewrite the repeat nodes
          processRepeatCB prcb = new processRepeatCB();
          prcb.curViewName = this.curViewName;
          prcb.curModuleName = this.curModuleName;

          if (debug) {
            Log.debug("PATTERN CLEANUP for [%s]", prcb.curViewName);
          }

          PatternExpressionNode rewrite = prcb.rewrite(pattern);

          // Rewrite the sequence nodes
          processSequenceCB pscb = new processSequenceCB();
          pscb.curViewName = this.curViewName;
          rewrite = pscb.rewrite(rewrite);

          patternNode.setPattern(rewrite);
        }

        // Rewrite happened in place, so just return the original
        return orig;
      }
    }

    // Pass the visitor into the function that will iterate over all the
    // select statements in the indicated view.
    cvn.setBody(rewriteExtractPatterns(new cb(), cvn.getBody(), cvn.getModuleName(),
        cvn.getUnqualifiedName()));
  }

  /**
   * Class for generating AQL implementing a sequence pattern expression
   * 
   */
  class generateAqlCB {

    boolean debug = false;

    // Current view being rewritten, for debugging purposes
    public String curViewName;

    // Module name of the view being rewritten.
    public String curModuleName;

    // From clause of the statement we're rewriting
    private FromListNode fromList = null;

    public void setFromListMap(FromListNode fromList) {
      this.fromList = fromList;
    }

    // Target column of the statement we're rewriting
    private ColNameNode target;

    public void setTarget(ColNameNode target) {
      this.target = target;
    }

    // Remember the new views we create
    ArrayList<CreateViewNode> newInternalViews = new ArrayList<CreateViewNode>();

    // The Common Sub-expression (CSE) Table
    HashMap<Integer, String> cseTable;

    public void setCseTable(HashMap<Integer, String> cseTable) {
      this.cseTable = cseTable;
    }

    Catalog catalog;

    public void setCatalog(Catalog catalog) {
      this.catalog = catalog;
    }

    private int dictNameID = 0;
    private int viewNameID = 0;

    private int getNextDictNameID() {
      return ++dictNameID;
    }

    private int getNextViewNameID() {
      return ++viewNameID;
    }

    /**
     * Recursively traverse the input sequence pattern expression and generate the necessary
     * temporary views.
     * 
     * @param node the input sequence pattern expression
     * @return the name of the view that implements the input sequence pattern expression
     * @throws ParseException
     */
    public String process(PatternExpressionNode node) throws ParseException {

      Integer cseID = node.getCseID();

      // Have we processed this node before? If so, just return the corresponding view name
      if (PatternExpressionNode.UNASSIGNED_CSE_ID != cseID && null != cseTable.get(cseID))
        return cseTable.get(cseID);

      // Otherwise, this is the first time we process this node
      if (PatternExpressionNode.UNASSIGNED_CSE_ID == cseID) {
        // The node does not yet have a valid CSE ID; reserve one so we can later memoize the new
        // generated view name
        cseID = cseTable.size();
        node.setCseID(cseID);
        cseTable.put(cseID, null);
      }

      if (node.producesSameResultAsChild()) {
        // Don't create unnecessary views

        PatternExpressionNode child = null;
        ArrayList<Integer> childGroupIDs = new ArrayList<Integer>();
        boolean needWrapper = false;

        if (node instanceof PatternSingleChildNode)
          child = ((PatternSingleChildNode) node).getChild();
        else if (node instanceof PatternMultipleChildrenNode)
          // Since it produces the same result,
          // guaranteed to have a single child
          child = ((PatternMultipleChildrenNode) node).getChild(0);
        else
          // We should not have reached this point
          throw new ParseException(String.format(
              "In view %s: Cannot generate AQL for pattern sub-expression: %s", curViewName, node));

        // If this child represents additional (e.g., non-zero) return group IDs
        // we need a wrapper statement to ensure that all such groups are preserved
        childGroupIDs = child.getReturnGroupIDs();
        if (childGroupIDs.size() > 1
            || (childGroupIDs.size() == 1 && !childGroupIDs.contains(new Integer(0))))
          needWrapper = true;

        if (!needWrapper)
          // Process the child directly - no need to create a wrapper view
          return process(child);
        else {
          // Process the child
          String viewName = process(child);

          // Wrap it up!
          // Handle additional groups represented by the child node
          // by adding a SELECT statement on top
          ViewBodyNode stmt = createSPWrapper(viewName, node, childGroupIDs);

          // Create a new internal view from the wrapper statement
          String wrapViewName = createSPView(node, stmt);

          // Update the CSE table
          cseTable.put(cseID, wrapViewName);

          return wrapViewName;
        }
      } else if (cseTable.get(cseID) == null || cseID == PatternExpressionNode.UNASSIGNED_CSE_ID) {

        /*
         * if(node instanceof PatternAtomColumnNode){ //cseTable.put(cseID,
         * ((PatternAtomColumnNode)node).getCol().getTabname()); // For now, create a new view to
         * get all required attributes //TODO: optimization to avoid creating this view } else{
         */

        if (debug) {
          Log.debug("Rewriting EXTRACT PATTERN view %s: Processing pattern sub-expression %s",
              curViewName, node);
        }

        // Generate an AQL statement that implements this node
        ViewBodyNode stmt = rewriteSP(node);

        if (debug) {
          Log.debug(
              "Rewritten EXTRACT PATTERN view %s: "
                  + "Processed pattern subexpression %s as the following view:\n%s",
              curViewName, node, stmt.dumpToStr(0));
        }

        // Create a new internal view from the resulting statement
        String newViewName = createSPView(node, stmt);

        // Update the CSE Table
        cseTable.put(cseID, newViewName);
      }

      // Return the new view name we just created
      String viewName = cseTable.get(cseID);

      // Extra check to ensure the rewrite succeeded first
      if (null == viewName) {
        throw new FatalInternalError("Unable to rewrite pattern expression '%s' in view name %s",
            node, curViewName);
      }

      return viewName;
    }

    /**
     * Creates a new view to store the rewritten statement for the input pattern node.
     * 
     * @param node
     * @param stmt
     * @return
     * @throws ParseException
     */
    private String createSPView(PatternExpressionNode node, ViewBodyNode stmt)
        throws ParseException {

      String viewName =
          String.format(viewNameFormat, this.curViewName, node, this.getNextViewNameID());
      viewName = StringUtils.escapeForAOG(viewName);
      NickNode newViewNick = new NickNode(viewName);

      CreateViewNode cvn =
          new CreateViewNode(newViewNick, stmt, node.getContainingFileName(), node.getOrigTok());
      cvn.setModuleName(this.curModuleName);

      // Add the new view to the catalog, remember it in the list of new views
      catalog.addView(cvn);
      newInternalViews.add(cvn);

      /*
       * Compute the schema for the newly added view node. This is necessary because SchemaInference
       * happens before sequence pattern rewriting
       */
      ViewCatalogEntry vce =
          (ViewCatalogEntry) catalog.lookupView(cvn.getViewName(), cvn.getEndOfStmtToken());
      SchemaInferrer schemaInferrer = new SchemaInferrer(catalog);
      vce.setViewSchema(schemaInferrer.computeSchema(cvn));

      AQLStatementValidator validator = new AQLStatementValidator(catalog);
      validator.validateView(cvn);

      if (debug) {
        Log.debug(
            "Rewriting view %s: Processed pattern sub-expression %s as the following view:\n%s",
            curViewName, node, cvn.dumpToStr(0));
      }
      return viewName;
    }

    /**
     * Wraps the view created for a pattern node with a select statement to ensure that all return
     * group IDs are preserved.
     * 
     * @param viewName name of the target view of the select
     * @param node parse tree node for the original extract pattern statement
     * @param childGroupIDs
     * @return parse tree nodes for rewritten statement
     * @throws ParseException
     */
    private ViewBodyNode createSPWrapper(String viewName, PatternExpressionNode node,
        ArrayList<Integer> groupIDs) throws ParseException {
      ViewBodyNode stmt;
      // Create the FROM clause
      FromListItemViewRefNode rwFromItem = new FromListItemViewRefNode(new NickNode(viewName));
      NickNode tabAlias = new NickNode("D");
      rwFromItem.setAlias(tabAlias);
      FromListNode rwFromList = new FromListNode(new ArrayList<FromListItemNode>(),
          node.getContainingFileName(), node.getOrigTok());
      rwFromList.add(rwFromItem);

      // Create the SELECT clause

      // First, add all attributes of the view
      SelectListItemNode rwSelectItem = new SelectListItemNode(tabAlias, null);
      rwSelectItem.setIsDotStar(true);

      SelectListNode rwSelectList = new SelectListNode(new ArrayList<SelectListItemNode>(),
          rwSelectItem.getContainingFileName(), rwSelectItem.getOrigTok());
      rwSelectList.add(rwSelectItem);

      // Add an attribute "D.group_0 AS group_i" for each group
      NickNode group0 = new NickNode(getGroupColName(0));
      for (int i = 0; i < groupIDs.size(); i++) {

        int groupID = groupIDs.get(i);
        if (groupID != 0) {
          rwSelectItem = new SelectListItemNode(new ColNameNode(tabAlias, group0),
              new NickNode(getGroupColName(groupID)));
          rwSelectList.add(rwSelectItem);
        }
      }

      stmt = new SelectNode(rwSelectList, rwFromList, null, null, null, null, null, null, null);
      return stmt;
    }

    /**
     * Method that generates the actual AQL statement that implements the specification of the input
     * sequence pattern expression. Calls specialized subroutines to do the actual work.
     * 
     * @param node
     * @return AQL statement that implements the given pattern specification
     * @throws ParseException
     */
    private ViewBodyNode rewriteSP(PatternExpressionNode node) throws ParseException {

      if (node instanceof PatternAtomColumnNode) {
        return rewriteAtomCol((PatternAtomColumnNode) node);
      } else if (node instanceof PatternAtomRegexNode || node instanceof PatternAtomDictNode) {
        return rewriteAtomRegexAndDict(node);
      } else if (node instanceof PatternRepeatNode) {
        return rewriteRepeat((PatternRepeatNode) node);
      } else if (node instanceof PatternSequenceNode) {
        return rewriteSequence((PatternSequenceNode) node);
      } else if (node instanceof PatternAlternationNode) {
        return rewriteAlternation((PatternAlternationNode) node);
      } else {
        throw new ParseException(String.format(
            "In view %s:" + "Don't know how to generate AQL for pattern sub-expression: %s",
            curViewName, node));
      }
    }

    private ViewBodyNode rewriteAtomCol(PatternAtomColumnNode col) throws ParseException {

      SelectListItemNode rwSelectItem;
      SelectListNode rwSelectList;
      FromListItemNode rwFromItem;
      FromListNode rwFromList;

      // Create the FROM clause
      FromListItemNode fromItem = this.fromList.getByName(col.getCol().getTabname());
      String fromItemAlias = fromItem.getAlias().getNickname();

      if (fromItem instanceof FromListItemViewRefNode) {
        String viewName = ((FromListItemViewRefNode) fromItem).getViewName().getNickname();
        rwFromItem = new FromListItemViewRefNode(new NickNode(viewName));
      } else if (fromItem instanceof FromListItemTableFuncNode) {
        TableFnCallNode tabFunc = ((FromListItemTableFuncNode) fromItem).getTabfunc();
        rwFromItem = new FromListItemTableFuncNode(tabFunc);
      } else {
        // We shouldn't have reached this point
        throw new ParseException(String.format(
            "In view  %s: Cannot generate AQL for pattern sub-expression %s because %s is of an unsupported type %s.",
            this.curViewName, col, fromItemAlias, fromItem.getClass().getSimpleName()));
      }

      rwFromItem.setAlias(new NickNode(fromItemAlias));
      rwFromList = new FromListNode(new ArrayList<FromListItemNode>(), col.getContainingFileName(),
          col.getOrigTok());
      rwFromList.add(rwFromItem);

      // Create the SELECT clause
      // Add the entire match as group 0;
      rwSelectItem = new SelectListItemNode(col.getCol(), new NickNode(getGroupColName(0)));
      rwSelectList = new SelectListNode(new ArrayList<SelectListItemNode>(),
          col.getContainingFileName(), col.getOrigTok());
      rwSelectList.add(rwSelectItem);

      // For each pass through column from this node, add an additional attribute
      ArrayList<PatternPassThroughColumn> passThroughCols = col.getPassThroughCols();
      for (PatternPassThroughColumn passThroughCol : passThroughCols) {

        // Add a new select list item: tabName.colName as tabName_colName
        rwSelectItem = new SelectListItemNode(
            new ColNameNode(passThroughCol.getViewAlias(), passThroughCol.getColName()),
            new NickNode(passThroughCol.getGeneratedName()));
        rwSelectList.add(rwSelectItem);
      }

      return new SelectNode(rwSelectList, rwFromList, null, null, null, null, null, null, null);
    }

    private ViewBodyNode rewriteAtomRegexAndDict(PatternExpressionNode node) throws ParseException {
      FromListItemNode rwFromItem;
      // Regex or dictionary node - set up the target of the extraction

      ColNameNode target;
      String targetAlias, targetViewName;

      if (this.target == null) {
        rwFromItem = new FromListItemViewRefNode(
            new NickNode(ExtractPatternNode.DEFAULT_WITH_INLINE_MATCH_VIEW_NAME));
        targetAlias = "D";
        rwFromItem.setAlias(new NickNode(targetAlias));

        target =
            new ColNameNode(targetAlias, ExtractPatternNode.DEFAULT_WITH_INLINE_MATCH_COL_NAME);
      } else {
        target = this.target;
        targetViewName = target.getTabname();

        rwFromItem = new FromListItemViewRefNode(new NickNode(targetViewName));
        rwFromItem.setAlias(new NickNode(targetViewName));
      }

      ExtractListNode rwExtractList = new ExtractListNode(new ArrayList<SelectListItemNode>(), null,
          node.getContainingFileName(), node.getOrigTok());
      NickNode outputCol = new NickNode(getGroupColName(0));

      if (node instanceof PatternAtomRegexNode) {

        // Regex node
        PatternAtomRegexNode regexAtom = (PatternAtomRegexNode) node;

        ArrayList<RegexNode> regexes = new ArrayList<RegexNode>();
        regexes.add(regexAtom.getRegex());

        StringNode flagsStr = new StringNode(com.ibm.systemt.util.regex.FlagsString.DEFAULT_FLAGS);

        // Create the RETURN clause
        ReturnClauseNode rwReturn =
            new ReturnClauseNode(node.getContainingFileName(), node.getOrigTok());
        rwReturn.addEntry(0, outputCol);

        // Create the REGEX specification
        IntNode oneTok = new IntNode(1, null, null);
        RegexExNode regexExNode = new RegexExNode(regexes, flagsStr, oneTok, oneTok, target,
            rwReturn, node.getContainingFileName(), node.getOrigTok());
        rwExtractList.setExtractSpec(regexExNode);

        // Create the EXTRACT REGEX STATEMENT
        ExtractNode exStmt = new ExtractNode(rwExtractList, rwFromItem, null, null, null,
            node.getContainingFileName(), node.getOrigTok());
        return exStmt;
      } else {
        // Dictionary node
        PatternAtomDictNode dictAtom = (PatternAtomDictNode) node;

        // Add a new CREATE DICTIONARY statement
        CreateDictNode.Inline dict =
            new CreateDictNode.Inline(dictAtom.getContainingFileName(), dictAtom.getOrigTok());
        ArrayList<String> entries = new ArrayList<String>();
        entries.add(dictAtom.getEntry().getStr());
        dict.setEntries(entries);

        DictParams params = new DictParams(dictAtom.getParams());
        params.setIsInline(true);
        // String newDictName = String.format(dictNameFormat,
        // StringUtils.escapeForAOG(dictAtom.getEntry().getStr()),
        // this.getNextDictNameID());
        String newDictName =
            String.format(dictNameFormat, this.curViewName, this.getNextDictNameID());
        params.setDictName(newDictName);

        dict.setParams(params);
        dict.setModuleName(dictAtom.getModuleName());
        catalog.addDict(dict);

        if (debug) {
          Log.debug("Rewriting view %s: Created inline dictionary for sub-expression [%s]:\n%s",
              curViewName, node, dict.dumpToStr(0));
        }

        ArrayList<StringNode> dicts = new ArrayList<StringNode>();
        dicts.add(new StringNode(newDictName));

        // Create the DICT specification
        DictExNode dictExNode = new DictExNode(dicts, null, target, outputCol,
            node.getContainingFileName(), node.getOrigTok());
        rwExtractList.setExtractSpec(dictExNode);

        // Create the EXTRACT DICT STATEMENT
        ExtractNode exStmt = new ExtractNode(rwExtractList, rwFromItem, null, null, null,
            node.getContainingFileName(), node.getOrigTok());
        return exStmt;
      }

    }

    private ViewBodyNode rewriteRepeat(PatternRepeatNode node) throws ParseException {

      FromListItemNode rwFromItem;

      // The target of the extraction is the view created by processing
      // the child of this repeat node
      String targetAlias = "S";
      String targetViewName = process(node.getChild());
      ColNameNode target = new ColNameNode(targetAlias, getGroupColName(0));

      rwFromItem = new FromListItemViewRefNode(new NickNode(targetViewName));
      rwFromItem.setAlias(new NickNode(targetAlias));

      // Create the extract list by copying all groups
      ExtractListNode rwExtractList = new ExtractListNode(new ArrayList<SelectListItemNode>(), null,
          node.getContainingFileName(), node.getOrigTok());
      NickNode outputCol = new NickNode(getGroupColName(0));

      // Create the blocks extraction node
      IntNode sep = new IntNode(0, null, null);
      BlockExNode blockExNode = new BlockExNode(node.getMinOccurrences(), node.getMaxOccurrences(),
          sep, sep, true, target, outputCol, node.getContainingFileName(), node.getOrigTok());
      rwExtractList.setExtractSpec(blockExNode);

      // Create the EXTRACT BLOCKS STATEMENT
      return new ExtractNode(rwExtractList, rwFromItem, null, null, null,
          node.getContainingFileName(), node.getOrigTok());
    }

    private ViewBodyNode rewriteSequence(PatternSequenceNode node) throws ParseException {

      String aliasFormat = "S%d";

      String group0 = getGroupColName(0);

      SequenceType nodeType = node.getType();
      if (nodeType.equals(SequenceType.ALL_REQUIRED)) {

        // SEQUENCE node of the form (N1 N2... Nk)
        return generateSelectStmtForSequenceNode(node, aliasFormat, group0);
      }

      else if (nodeType.equals(SequenceType.REQUIRED_FOLLOWED_BY_OPTIONAL)
          || nodeType.equals(SequenceType.OPTIONAL_FOLLOWED_BY_REQUIRED)) {

        // SEQUENCE node of the form (N_req N_opt ?) or of the form (N_opt ? N_req)

        // STEP 1: Create the SELECT statement corresponding to (N_req N_opt) or (N_opt N_req)
        SelectNode stmt_Nreq_Nopt = generateSelectStmtForSequenceNode(node, aliasFormat, group0);

        // Fix for defect# 13087: Sequence pattern issue when a token gap is followed or preceded by
        // a optional element
        // STEP 2: Create the SELECT statement corresponding to (N_req)
        ArrayList<SelectNode> stmt_Nreq = new ArrayList<SelectNode>();
        if (node.getChild(1) != null && (node.getChild(1) instanceof PatternAtomTokenGapNode)) {
          PatternAtomTokenGapNode tokenGap = (PatternAtomTokenGapNode) node.getChild(1);
          int min = tokenGap.getMinOccurrences().getValue();
          int max = tokenGap.getMaxOccurrences().getValue();

          for (int j = min; j <= max; j++) {

            stmt_Nreq.add(generateSelectStmtForOptionalSequencePath(node, j, nodeType));
          }
        } else {
          stmt_Nreq.add(generateSelectStmtForOptionalSequencePath(node, 0, nodeType));
        }

        // Step 3: Create the UNION ALL statement
        ArrayList<ViewBodyNode> stmtsToUnion = new ArrayList<ViewBodyNode>();
        stmtsToUnion.add(stmt_Nreq_Nopt);
        stmtsToUnion.addAll(stmt_Nreq);

        if (debug) {
          Log.debug("STMT 1\n %s\n", stmt_Nreq_Nopt.dumpToStr(0));
          int counter = 1;
          for (SelectNode selectNode : stmt_Nreq) {
            Log.debug("STMT %d\n %s\n", ++counter, selectNode.dumpToStr(0));
          }

        }

        UnionAllNode union = new UnionAllNode(stmtsToUnion,
            stmtsToUnion.get(0).getContainingFileName(), stmtsToUnion.get(0).getOrigTok());
        return union;
      } else if (nodeType.equals(SequenceType.OPTIONAL_FOLLEWED_BY_OPTIONAL)) {
        // SEQUENCE node of the form (N_opt1 ? N_opt2 ?)

        // STEP 1: Create the SELECT statement corresponding to (N_opt1 N_opt2)
        SelectNode stmt_Nopt1_Nopt2 = generateSelectStmtForSequenceNode(node, aliasFormat, group0);

        // Fix for defect# 13087: Sequence pattern issue when a token gap is followed or preceded by
        // a optional element
        // STEP 2: Create the SELECT statement corresponding to (N_opt1)
        ArrayList<SelectNode> stmt_Nreq = new ArrayList<SelectNode>();
        if (node.getChild(1) != null && (node.getChild(1) instanceof PatternAtomTokenGapNode)) {
          PatternAtomTokenGapNode tokenGap = (PatternAtomTokenGapNode) node.getChild(1);
          int min = tokenGap.getMinOccurrences().getValue();
          int max = tokenGap.getMaxOccurrences().getValue();

          for (int j = min; j <= max; j++) {

            // Computing N_opt1 N_opt2 ?
            stmt_Nreq.add(generateSelectStmtForOptionalSequencePath(node, j,
                SequenceType.REQUIRED_FOLLOWED_BY_OPTIONAL));
            // Computing N_opt1 ? N_opt2
            stmt_Nreq.add(generateSelectStmtForOptionalSequencePath(node, j,
                SequenceType.OPTIONAL_FOLLOWED_BY_REQUIRED));
          }
        } else {
          // Computing N_opt1 N_opt2 ?
          stmt_Nreq.add(generateSelectStmtForOptionalSequencePath(node, 0,
              SequenceType.REQUIRED_FOLLOWED_BY_OPTIONAL));
          // Computing N_opt1 ? N_opt2
          stmt_Nreq.add(generateSelectStmtForOptionalSequencePath(node, 0,
              SequenceType.OPTIONAL_FOLLOWED_BY_REQUIRED));
        }

        // Step 3: Create the UNION ALL statement
        ArrayList<ViewBodyNode> stmtsToUnion = new ArrayList<ViewBodyNode>();
        stmtsToUnion.add(stmt_Nopt1_Nopt2);
        stmtsToUnion.addAll(stmt_Nreq);

        if (debug) {
          Log.debug("STMT 1\n %s\n", stmt_Nopt1_Nopt2.dumpToStr(0));
          int counter = 1;
          for (SelectNode selectNode : stmt_Nreq) {
            Log.debug("STMT %d\n %s\n", ++counter, selectNode.dumpToStr(0));
          }

        }

        UnionAllNode union = new UnionAllNode(stmtsToUnion,
            stmtsToUnion.get(0).getContainingFileName(), stmtsToUnion.get(0).getOrigTok());
        return union;
      } else {
        throw new ParseException(String.format(
            "In view %s:" + "Don't know how to generate AQL for pattern sub-expression: %s",
            curViewName, node));
      }

    }

    private SelectNode generateSelectStmtForOptionalSequencePath(PatternSequenceNode node,
        int tokenGapFrequency, SequenceType sequenceType) throws ParseException {

      ColNameNode col, col1;
      ScalarFnCallNode func, func1, func2;
      ArrayList<RValueNode> args, args1, args2;
      String tabAlias, viewName;
      String aliasFormat = "S%d";
      ArrayList<FromListItemNode> rwFromList;
      FromListItemNode rwFromItem;
      SelectListNode rwSelectList;
      WhereClauseNode rwWhere = null;

      String group0 = getGroupColName(0);
      PatternExpressionNode child1 = node.getChild(0);
      PatternExpressionNode child2 =
          node.getChild(1) instanceof PatternAtomTokenGapNode ? node.getChild(2) : node.getChild(1);
      boolean allOptionalCase = node.getType().equals(SequenceType.OPTIONAL_FOLLEWED_BY_OPTIONAL)
          && child1.isOptional() && child2.isOptional();

      PatternExpressionNode requiredChild =
          sequenceType.equals(SequenceType.OPTIONAL_FOLLOWED_BY_REQUIRED) ? child2 : child1;

      // Step 1: Create the SELECT list
      if (tokenGapFrequency > 0) {

        col1 = new ColNameNode(String.format(aliasFormat, 0), group0);
        args = new ArrayList<RValueNode>();
        args.add(col1);
        args.add(new IntNode(tokenGapFrequency, null, null));

        if (sequenceType.equals(SequenceType.REQUIRED_FOLLOWED_BY_OPTIONAL)) {
          func1 = new ScalarFnCallNode(
              new NickNode(ScalarFunc.computeFuncName(RightContextTok.class)), args);
          args1 = new ArrayList<RValueNode>();
          args1.add(col1);
          args1.add(func1);

        } else {
          func1 = new ScalarFnCallNode(
              new NickNode(ScalarFunc.computeFuncName(LeftContextTok.class)), args);
          args1 = new ArrayList<RValueNode>();
          args1.add(func1);
          args1.add(col1);
        }

        func = new ScalarFnCallNode(new NickNode(ScalarFunc.computeFuncName(CombineSpans.class)),
            args1);

        SelectListItemNode rwSelectItem = new SelectListItemNode(func, new NickNode(group0));
        rwSelectList = new SelectListNode(new ArrayList<SelectListItemNode>(),
            func.getContainingFileName(), func.getOrigTok());
        rwSelectList.add(rwSelectItem);

        // Step 2: Prepare WHERE list
        args2 = new ArrayList<RValueNode>();
        args2.add(func1);
        func2 = new ScalarFnCallNode(new NickNode(ScalarFunc.computeFuncName(GetLengthTok.class)),
            args2);

        ArrayList<RValueNode> whereArgs = new ArrayList<RValueNode>();
        whereArgs.add(func2);
        whereArgs.add(new IntNode(tokenGapFrequency, null, null));
        ScalarFnCallNode whereFunc =
            new ScalarFnCallNode(new NickNode(ScalarFunc.computeFuncName(Equals.class)), whereArgs);

        rwWhere = new WhereClauseNode(node.getContainingFileName(), node.getOrigTok());
        rwWhere
            .addPred(new PredicateNode(whereFunc, node.getContainingFileName(), node.getOrigTok()));

      } else {
        ColNameNode col0 = new ColNameNode(String.format(aliasFormat, 0), group0);

        SelectListItemNode rwSelectItem = new SelectListItemNode(col0, new NickNode(group0));
        rwSelectList = new SelectListNode(new ArrayList<SelectListItemNode>(),
            node.getContainingFileName(), node.getOrigTok());
        rwSelectList.add(rwSelectItem);
      }

      // Step 3: Add to the SELECT list all necessary groups ids from the required child.
      // Fill in the group ids from the optional child as "Cast(NULL as Span)"
      ArrayList<Integer> requiredGroupIDs = requiredChild.getAllReturnGroupIDs();
      ArrayList<Integer> expectedGroupIDs = child1.getAllReturnGroupIDs();
      expectedGroupIDs.addAll(child2.getAllReturnGroupIDs());

      SelectListItemNode rwSelectItem = null;
      Integer groupID;
      String colAlias;
      tabAlias = String.format(aliasFormat, 0);
      for (int j = 0; j < expectedGroupIDs.size(); j++) {

        groupID = expectedGroupIDs.get(j);
        colAlias = getGroupColName(groupID);

        if (requiredGroupIDs.contains(groupID)) {
          col = new ColNameNode(tabAlias, colAlias);
          rwSelectItem = new SelectListItemNode(col, new NickNode(colAlias));
        } else {
          // Here's where we add in the "cast null as span" part. We currently use the mode of
          // Cast() wherein Cast()
          // pulls the type from another column. This expression should probably be changed to just
          // take the string
          // literal "Span" instead at some point.
          args = new ArrayList<RValueNode>();
          args.add(new ScalarFnCallNode(new NickNode("NullConst"), new ArrayList<RValueNode>()));
          RValueNode col0;
          col0 = new ColNameNode(String.format(aliasFormat, 0), group0);
          args.add(col0);
          func = new ScalarFnCallNode(new NickNode(Cast.FNAME), args);
          rwSelectItem = new SelectListItemNode(func, new NickNode(colAlias));
        }

        rwSelectList.add(rwSelectItem);
      }

      // Step 4: Add to the SELECT list all necessary pass though cols from the child.
      // Fill in the rest of the pass through cols from the other children with NULLs
      // Pass through colls from the required child
      ArrayList<PatternPassThroughColumn> requiredPassThroughCols =
          requiredChild.getPassThroughCols();
      // Pass through cols from both the required child and the optional child
      ArrayList<PatternPassThroughColumn> allPassThroughCols =
          new ArrayList<PatternPassThroughColumn>();
      allPassThroughCols.addAll(child1.getPassThroughCols());
      allPassThroughCols.addAll(child2.getPassThroughCols());

      for (int j = 0; j < allPassThroughCols.size(); j++) {

        PatternPassThroughColumn passThroughCol = allPassThroughCols.get(j);
        colAlias = passThroughCol.getGeneratedName();

        // Pass through col from the required child. Add the child output col ref to the select list
        if (requiredPassThroughCols.contains(passThroughCol)) {

          col = new ColNameNode(tabAlias, colAlias);
          rwSelectItem = new SelectListItemNode(col, new NickNode(colAlias));
        }
        // Pass through col from the optional child. Add NULL to the select list.
        else {
          args = new ArrayList<RValueNode>();
          args.add(new ScalarFnCallNode(new NickNode("NullConst"), new ArrayList<RValueNode>()));

          // Remember to cast this null value to the correct type.
          args.add(new StringNode(passThroughCol.getType().toString()));

          func = new ScalarFnCallNode(new NickNode(Cast.FNAME), args);
          rwSelectItem = new SelectListItemNode(func, new NickNode(colAlias));
        }

        rwSelectList.add(rwSelectItem);
      }

      // Step 5: Create the FROM list
      rwFromList = new ArrayList<FromListItemNode>();
      tabAlias = String.format(aliasFormat, 0);
      if (allOptionalCase && requiredChild instanceof PatternOptionalNode) {
        viewName = process(((PatternOptionalNode) requiredChild).getChild());
      } else {
        viewName = process(requiredChild);
      }

      rwFromItem = new FromListItemViewRefNode(new NickNode(viewName));
      rwFromItem.setAlias(new NickNode(tabAlias));
      rwFromList.add(rwFromItem);

      SelectNode stmt_Nreq = new SelectNode(rwSelectList,
          new FromListNode(rwFromList, node.getContainingFileName(), node.getOrigTok()), rwWhere,
          null, null, null, null, null, null);

      return stmt_Nreq;
    }

    private SelectNode generateSelectStmtForSequenceNode(PatternSequenceNode node,
        String aliasFormat, String group0) throws ParseException {
      PatternExpressionNode child;
      String tabAlias, viewName;
      ColNameNode col1, col2, col;
      ScalarFnCallNode func;
      ArrayList<RValueNode> args;
      SelectListItemNode rwSelectItem;
      ArrayList<FromListItemNode> rwFromList;
      FromListItemNode rwFromItem = null;
      WhereClauseNode rwWhere;
      Integer groupID;
      ArrayList<Integer> groupIDs;

      // Step 1: Create the SELECT list

      // First, add the group0 attribute as CombineSpans(S1.group0, Sk.group0)
      col1 = new ColNameNode(String.format(aliasFormat, 0), group0);
      col2 = new ColNameNode(String.format(aliasFormat, node.getNumChildren() - 1), group0);
      args = new ArrayList<RValueNode>();
      args.add(col1);
      args.add(col2);
      func =
          new ScalarFnCallNode(new NickNode(ScalarFunc.computeFuncName(CombineSpans.class)), args);

      rwSelectItem = new SelectListItemNode(func, new NickNode(group0));
      SelectListNode rwSelectList = new SelectListNode(new ArrayList<SelectListItemNode>(),
          node.getContainingFileName(), node.getOrigTok());
      rwSelectList.add(rwSelectItem);

      // STEP 2: Create the FROM and WHERE clauses
      rwFromList = new ArrayList<FromListItemNode>();
      rwWhere = new WhereClauseNode(node.getContainingFileName(), node.getOrigTok());

      PatternAtomTokenGapNode tokenGap = null;
      IntNode min, max;
      int precedingAliasID = 0;

      // STEP 3: Iterate through the children nodes and populate the FROM and WHERE clauses. At the
      // same time, also
      // populate the SELECT list with group IDs. We populate the SELECT list with all group IDs
      // from all
      // children first. We then add the pass through cols in a second iteration though the children
      // nodes, so
      // that all groups ID cols come before all the pass through cols. This ensures that the output
      // SELECT list is
      // union compatible with the select list resulting from processing the (N_req N_opt) and
      // (N_opt N_req) in
      // {@link #generateSelectStmtForOptionalSequencePath(PatternSequenceNode, int)}.
      for (int i = 0; i < node.getNumChildren(); i++) {

        child = node.getChild(i);

        // Consider the token gap with the next child
        if (child instanceof PatternAtomTokenGapNode) {
          tokenGap = (PatternAtomTokenGapNode) child;
          continue;
        }

        // Add a new FROM item
        tabAlias = String.format(aliasFormat, i);

        if (child instanceof PatternOptionalNode)
          viewName = process(((PatternOptionalNode) child).getChild());
        else
          viewName = process(child);

        rwFromItem = new FromListItemViewRefNode(new NickNode(viewName));
        rwFromItem.setAlias(new NickNode(tabAlias));
        rwFromList.add(rwFromItem);

        // Add join predicate FollowsTok( S(precedingAliasID).group0, S(aliasID).group0, 0, 0)
        if (i > 0) {
          col1 = new ColNameNode(String.format(aliasFormat, precedingAliasID), group0);
          col2 = new ColNameNode(String.format(aliasFormat, i), group0);
          args = new ArrayList<RValueNode>();
          args.add(col1);
          args.add(col2);

          // Determine the min and max arguments for the FollowsTok() predicate
          if (tokenGap != null) {
            min = new IntNode(tokenGap.getMinOccurrences().getValue(), null, null);
            max = new IntNode(tokenGap.getMaxOccurrences().getValue(), null, null);
            tokenGap = null;
          } else {
            min = new IntNode(0, null, null);
            max = new IntNode(0, null, null);
          }

          args.add(min);
          args.add(max);
          func = new ScalarFnCallNode(new NickNode(ScalarFunc.computeFuncName(FollowsTok.class)),
              args);

          rwWhere.addPred(new PredicateNode(func, node.getContainingFileName(), node.getOrigTok()));
        }

        // STEP 3: Add to the SELECT list all groups ids returned from the processed child in the
        // form
        // "S_i.group_x AS group_x"
        if (child instanceof PatternOptionalNode)
          groupIDs = ((PatternOptionalNode) child).getChild().getAllReturnGroupIDs();
        else
          groupIDs = child.getAllReturnGroupIDs();

        for (int j = 0; j < groupIDs.size(); j++) {

          groupID = groupIDs.get(j);
          col = new ColNameNode(String.format(aliasFormat, i), getGroupColName(groupID));
          rwSelectItem = new SelectListItemNode(col, new NickNode(getGroupColName(groupID)));
          rwSelectList.add(rwSelectItem);
        }

        // STEP 4: Add to the SELECT list all groups ids returned locally from the optional child
        // (if any)
        // in the form "S_aliasID.group_0 AS group_x"
        if (child instanceof PatternOptionalNode) {
          groupIDs = child.getReturnGroupIDs();

          for (int j = 0; j < groupIDs.size(); j++) {

            groupID = groupIDs.get(j);
            col = new ColNameNode(String.format(aliasFormat, i), getGroupColName(0));
            rwSelectItem = new SelectListItemNode(col, new NickNode(getGroupColName(groupID)));
            rwSelectList.add(rwSelectItem);
          }
        }

        precedingAliasID = i;
      }

      // STEP 5: Add to the SELECT list all pass through cols from the processed child in the form
      // "S_i.<viewname>_<att_name> AS <viewname>_<att_name>". Note

      for (int i = 0; i < node.getNumChildren(); i++) {

        child = node.getChild(i);
        tabAlias = String.format(aliasFormat, i);

        // Consider the token gap with the next child
        if (child instanceof PatternAtomTokenGapNode) {
          continue;
        }

        ArrayList<PatternPassThroughColumn> childPassThroughCols = child.getPassThroughCols();
        for (PatternPassThroughColumn passThroughCol : childPassThroughCols) {
          String colAlias = passThroughCol.getGeneratedName();

          // Pass through col from the child. Add the child output col ref to the select list
          col = new ColNameNode(tabAlias, colAlias);
          rwSelectItem = new SelectListItemNode(col, new NickNode(colAlias));
          rwSelectList.add(rwSelectItem);
        }
      }

      return new SelectNode(rwSelectList,
          new FromListNode(rwFromList, node.getContainingFileName(), node.getOrigTok()), rwWhere,
          null, null, null, null, null, null);
    }

    private ViewBodyNode rewriteAlternation(PatternAlternationNode node) throws ParseException {

      String aliasFormat = "S%d";
      String tabAlias, viewName, colAlias;
      ColNameNode col0, col;
      ScalarFnCallNode func;
      ArrayList<RValueNode> args;
      SelectListItemNode rwSelectItem;
      ArrayList<FromListItemNode> rwFromList;
      FromListItemNode rwFromItem;
      ArrayList<Integer> expectedGroupIDs, childGroupIDs;
      ArrayList<PatternPassThroughColumn> expectedPassThroughCols, childPassThroughCols;

      PatternExpressionNode child;

      String group0 = getGroupColName(0);
      Integer groupID;
      PatternPassThroughColumn passThroughCol;

      ArrayList<ViewBodyNode> stmtsToUnion = new ArrayList<ViewBodyNode>();

      // STEP 1: collect all group ids and pass through cols returned from the child nodes
      expectedGroupIDs = new ArrayList<Integer>();
      for (int i = 0; i < node.getNumChildren(); i++) {
        expectedGroupIDs.addAll(node.getChild(i).getAllReturnGroupIDs());
      }

      expectedPassThroughCols = new ArrayList<PatternPassThroughColumn>();
      for (int i = 0; i < node.getNumChildren(); i++) {
        expectedPassThroughCols.addAll(node.getChild(i).getPassThroughCols());
      }

      // STEP 2: Create one SELECT statement for each operand in the alternation
      for (int i = 0; i < node.getNumChildren(); i++) {

        child = node.getChild(i);

        // STEP 2(a): Create the SELECT list
        // First, add the group0 attribute

        col0 = new ColNameNode(String.format(aliasFormat, 0), group0);

        rwSelectItem = new SelectListItemNode(col0, new NickNode(group0));
        SelectListNode rwSelectList = new SelectListNode(new ArrayList<SelectListItemNode>(),
            node.getContainingFileName(), node.getOrigTok());
        rwSelectList.add(rwSelectItem);

        // STEP 2(b): Create the FROM clause
        rwFromList = new ArrayList<FromListItemNode>();
        tabAlias = String.format(aliasFormat, 0);
        viewName = process(child);

        rwFromItem = new FromListItemViewRefNode(new NickNode(viewName));
        rwFromItem.setAlias(new NickNode(tabAlias));
        rwFromList.add(rwFromItem);

        // STEP 2(c): Add to the SELECT list all necessary groups ids from the child.
        // Fill in the group ids from the other children as "Cast(NULL as Span)"
        for (int j = 0; j < expectedGroupIDs.size(); j++) {

          groupID = expectedGroupIDs.get(j);
          colAlias = getGroupColName(groupID);
          childGroupIDs = child.getAllReturnGroupIDs();

          // This group is computed in the child. Add the child output col ref to the select list
          if (childGroupIDs.contains(groupID)) {
            col = new ColNameNode(tabAlias, colAlias);
            rwSelectItem = new SelectListItemNode(col, new NickNode(colAlias));
          }
          // This group is not computed by this child. Add "Cast(NULL as Span)" to the select list
          else {
            args = new ArrayList<RValueNode>();
            args.add(new ScalarFnCallNode(new NickNode("NullConst"), new ArrayList<RValueNode>()));

            // Note that we're using the mode of Cast where the type to be cast to is copied from
            // another column's type.
            // This argument should be replace with the string literal 'Span' at some point.
            args.add(col0);

            func = new ScalarFnCallNode(new NickNode(Cast.FNAME), args);
            rwSelectItem = new SelectListItemNode(func, new NickNode(colAlias));
          }

          rwSelectList.add(rwSelectItem);
        }

        // STEP 2(d): Add to the SELECT list all necessary pass though cols from the child.
        // Fill in the rest of the pass through cols from the other children with NULLs
        for (int j = 0; j < expectedPassThroughCols.size(); j++) {

          passThroughCol = expectedPassThroughCols.get(j);
          colAlias = passThroughCol.getGeneratedName();
          childPassThroughCols = child.getPassThroughCols();

          if (childPassThroughCols.contains(passThroughCol)) {
            // Pass through col from the child. Add the child output col ref to the select list
            col = new ColNameNode(tabAlias, colAlias);
            rwSelectItem = new SelectListItemNode(col, new NickNode(colAlias));
          }
          // Pass through col not computed by this child. Add NULL to the select list
          else {
            args = new ArrayList<RValueNode>();
            args.add(new ScalarFnCallNode(new NickNode("NullConst"), new ArrayList<RValueNode>()));

            // Don't forget to cast to the appropriate type!
            args.add(new StringNode(passThroughCol.getType().toString()));

            func = new ScalarFnCallNode(new NickNode(Cast.FNAME), args);
            rwSelectItem = new SelectListItemNode(func, new NickNode(colAlias));
          }

          rwSelectList.add(rwSelectItem);
        }

        // Step 2(e): Add the SELECT to the UNION ALL statement
        SelectNode stmt = new SelectNode(rwSelectList,
            new FromListNode(rwFromList, node.getContainingFileName(), node.getOrigTok()), null,
            null, null, null, null, null, null);
        stmtsToUnion.add(stmt);
      }

      UnionAllNode union = new UnionAllNode(stmtsToUnion,
          stmtsToUnion.get(0).getContainingFileName(), stmtsToUnion.get(0).getOrigTok());
      return union;
    }
  }

  /**
   * Formats for attribute, view and dictionary names created during the sequence pattern rewrite
   */
  private static final String groupColNameFormat = "group_%d";
  // Moved to the PatternPassThroughColumn class by Fred
  // private static final String passThroughColNameFormat = "%s_%s";
  // END code moved by Fred
  private static final String viewNameFormat = "_%s_TmpView_%s__%d";
  private static final String dictNameFormat = "_%s_TmpDict__%d";

  /**
   * Construct the name of the attribute that carries a return group value for a given group ID.
   * 
   * @param ix
   * @return
   */
  protected static String getGroupColName(int ix) {
    return String.format(groupColNameFormat, ix);
  }

  /**
   * Construct an alias for a pass through column in the select list of an EXTRACT PATTERN
   * statement. Used during the pattern query rewrite for passing through the appropriate values
   * from the bottom to the top level of the pattern expression.
   * 
   * @param tabName
   * @param colName
   * @return
   * @throws ParseException if the input is not in the format <view_name>.<col_name>
   */
  // Moved to the PatternPassThroughColumn class by Fred
  // protected static String getPassThroughColName (String passThroughCol, Token origTok) throws
  // ParseException
  // {
  // // Split the reference into table and column.
  // final int pos = passThroughCol.indexOf ('.');
  // if (pos < 0) {
  // // Should never happen because the pattern validation code ensures all col refs in the select
  // list are
  // // of the form <viewname>.<attrname>
  // throw AQLParser.makeException (String.format (
  // "Don't understand column reference '%s'. The column reference must be of the form
  // '<viewname>.<colname>'.",
  // passThroughCol), origTok);
  // }
  // String tabName = passThroughCol.substring (0, pos);
  // String colName = passThroughCol.substring (pos + 1);
  //
  // return String.format (passThroughColNameFormat, tabName, colName);
  // }
  // END code moved by Fred

  /**
   * Translates an EXTRACT PATTERN statement into a rewritten SELECT statement.
   * 
   * @param pcb class that does the actual rewriting of the sequence pattern
   * @param fcb class for rewriting functions in the WHERE clause of the rewritten statement
   * @param root root node of the part of the parse tree to rewrite
   * @viewName name of the enclosing view
   * @return a rewritten version of the tree rooted at root
   */
  protected void generateAQL(generateAqlCB callback, CreateViewNode cvn) throws ParseException {

    final generateAqlCB patternExprCB = callback;

    class funcCB extends funcRewriter {

      @SuppressWarnings("unused")
      private static final boolean debug = false;

      private HashMap<String, RValueNode> alias2Col;

      public void setAlias2ColMap(HashMap<String, RValueNode> alias2Col) {
        this.alias2Col = alias2Col;
      }

      @Override
      ScalarFnCallNode rewrite(ScalarFnCallNode orig) throws ParseException {

        ArrayList<RValueNode> args = orig.getArgs();
        String arg;

        for (int i = 0; i < args.size(); i++) {

          arg = args.get(i).toString();
          if (alias2Col.containsKey(arg))
            args.set(i, alias2Col.get(arg));
        }

        // We modified the node in place.
        return orig;
      }
    }

    // Create a visitor that will modify each node in turn.
    class cb extends extractPatternRewriter {
      boolean debug = false;
      final funcCB fcb = new funcCB();

      final String patternViewAlias = "V";

      @Override
      ViewBodyNode rewrite(ViewBodyNode orig) throws ParseException {
        if (orig instanceof ExtractPatternNode) {

          ExtractPatternNode patternNode = (ExtractPatternNode) orig;

          PatternExpressionNode pattern = patternNode.getPattern();

          FromListNode fromList = patternNode.getFromList();

          patternExprCB.curViewName = this.curViewName;
          patternExprCB.curModuleName = this.curModuleName;

          if (debug)
            Log.debug("GENERATE AQL for %s", patternExprCB.curViewName);

          patternExprCB.setFromListMap(fromList);
          patternExprCB.setTarget(patternNode.getTarget());

          // GHE #53 check the body of pattern is not optional sequence
          if (pattern.isOptional()) {
            throw new ParseException(String.format(
                "In view %s:\n" + "Don't know how to generate AQL for pattern expression: %s. "
                    + "A root sequence must contain at least one non-optional element.",
                curViewName, pattern));
          }

          // Rewrite the sequence pattern into AQL
          String patternView = patternExprCB.process(pattern);

          /*
           * Create a final view on top of the AQL generated from the pattern expression SELECT
           * V.group_0 as name0, V.group_1 as name 1, ... FROM patternView V WHERE conjunction of
           * predicates in the original HAVING clause CONSOLIDATE ON ... LIMIT ...
           */

          // Step 1: create the SELECT clause
          SelectListNode rwSelectList = null;

          ReturnClauseNode returnClause = patternNode.getReturnClause();
          String attName;
          int groupID;
          SelectListItemNode rwSelectItem;
          ColNameNode col;

          // Mapping from passThroughCol ref from select list to new col ref generated from the
          // pattern expression
          // rewrite. Used for rewriting the function calls in the select list.
          HashMap<String, RValueNode> passThrough2RWCol = new HashMap<String, RValueNode>();

          // Mapping from output col in SELECT list or PATTERN RETURN clause to actual col ref from
          // the pattern
          // expression rewrite. Used for rewriting the function calls in the HAVING clause.
          HashMap<String, RValueNode> alias2Col = new HashMap<String, RValueNode>();

          // First, add the columns from the select list of the statement

          SelectListNode selectList = patternNode.getSelectList();
          if (null != selectList) {

            // Remember the new name of the column as coming from the pattern expression rewrite so
            // that we can use them
            // in the function rewrite
            ArrayList<PatternPassThroughColumn> passThroughCols =
                patternNode.getPattern().getPassThroughCols();
            for (PatternPassThroughColumn passThroughCol : passThroughCols) {

              col = new ColNameNode(patternViewAlias, passThroughCol.getGeneratedName());
              passThrough2RWCol.put(passThroughCol.getQualifiedName(), col);

            }
            SelectListItemNode item;
            RValueNode rwVal;
            for (int i = 0; i < selectList.size(); i++) {
              item = selectList.get(i);
              rwSelectItem = null;

              // Rewrite the original value into an equivalent value using the output columns of the
              // rewritten statement
              RValueNode val = item.getValue();
              rwVal = transformSelectItem(passThrough2RWCol, val);

              rwSelectItem = new SelectListItemNode(rwVal, new NickNode(item.getAlias()));
              if (rwSelectList == null) {
                rwSelectList = new SelectListNode(new ArrayList<SelectListItemNode>(),
                    orig.getContainingFileName(), orig.getOrigTok());
              }
              rwSelectList.add(rwSelectItem);

              // Remember the mapping between the output alias and the new rewritten value in the
              // rewritten select list
              String qualifiedColumnName = item.getAlias();
              alias2Col.put(qualifiedColumnName, rwSelectItem.getValue());

            }
          }

          // Second, add the output columns from the RETURN clause of the pattern
          for (int i = 0; i < returnClause.size(); i++) {
            groupID = returnClause.getGroupID(i);
            attName = returnClause.getName(i);

            col = new ColNameNode(patternViewAlias, getGroupColName(groupID));

            alias2Col.put(attName, col);

            rwSelectItem = new SelectListItemNode(col, new NickNode(attName));
            if (rwSelectList == null) {
              rwSelectList = new SelectListNode(new ArrayList<SelectListItemNode>(),
                  orig.getContainingFileName(), orig.getOrigTok());
            }
            rwSelectList.add(rwSelectItem);
          }

          // Step 2: create the FROM clause
          FromListItemNode rwFromItem = new FromListItemViewRefNode(new NickNode(patternView));
          rwFromItem.setAlias(new NickNode(patternViewAlias));
          FromListNode rwFromList = new FromListNode(new ArrayList<FromListItemNode>(),
              orig.getContainingFileName(), orig.getOrigTok());

          rwFromList.add(rwFromItem);

          // Step 3: Transform the HAVING clause into a WHERE clause
          HavingClauseNode havingClause = patternNode.getHavingClause();
          WhereClauseNode rwWhere = null;

          if (havingClause != null) {
            // Copy all predicates from the HAVING clause into new WHERE clause
            rwWhere = new WhereClauseNode(havingClause.getContainingFileName(),
                havingClause.getOrigTok());
            rwWhere.setPreds(havingClause.getPreds());

            // Rewrite each function to replace output attribute names
            // with the appropriate column in the SELECT clause
            ScalarFnCallNode func;
            fcb.setAlias2ColMap(alias2Col);

            for (int i = 0; i < rwWhere.getPreds().size(); i++) {
              func = rwWhere.getPred(i).getFunc();
              rewriteSingleFunc(func, fcb);
            }
          }

          // Step 4: Add the CONSOLIDATE clause, if any
          ConsolidateClauseNode consolidateClause = patternNode.getConsolidateClause();
          ConsolidateClauseNode rwConsolidateClause = null;

          if (consolidateClause != null) {

            // Rewrite the consolidate target to the appropriate column in the SELECT clause
            RValueNode target = consolidateClause.getTarget();
            RValueNode rwTarget = transformOutputColRef(alias2Col, target);

            // Rewrite the priority target as well
            RValueNode priorityTarget = consolidateClause.getPriorityTarget();
            RValueNode rwPriorityTarget = transformOutputColRef(alias2Col, priorityTarget);

            rwConsolidateClause =
                new ConsolidateClauseNode(rwTarget, consolidateClause.getTypeNode(),
                    rwPriorityTarget, consolidateClause.getPriorityDirection(),
                    consolidateClause.getContainingFileName(), consolidateClause.getOrigTok());
          }

          // Step 5: create the final rewritten statement
          ViewBodyNode stmt = new SelectNode(rwSelectList, rwFromList, rwWhere, null, null,
              rwConsolidateClause, patternNode.getMaxTup(), null, null);

          if (debug) {
            Log.debug("************************************");
            Log.debug("Rewritten EXTRACT PATTERN view %s as the following view:\n%s",
                this.curViewName, stmt.dumpToStr(0));
            Log.debug("************************************");
          }

          return stmt;
        }

        // Otherwise, no rewrite happened so just return the original
        return orig;
      }

      private RValueNode transformSelectItem(HashMap<String, RValueNode> passThrough2RWCol,
          RValueNode val) throws ParseException {
        ColNameNode col;
        RValueNode rwVal;
        if (val instanceof ScalarFnCallNode) {
          // Rewrite each function to replace output attribute names
          // with the appropriate column in the SELECT clause
          fcb.setAlias2ColMap(passThrough2RWCol);
          rwVal = rewriteSingleFunc((ScalarFnCallNode) val, fcb);
        } else if (val instanceof ColNameNode) {
          // Replace the old value with the new value
          col = (ColNameNode) val;
          PatternPassThroughColumn wrappedName =
              new PatternPassThroughColumn(col.getColName(), null, true);
          rwVal = new ColNameNode(patternViewAlias, wrappedName.getGeneratedName());
        } else {
          rwVal = val;
        }
        return rwVal;
      }

      private RValueNode transformOutputColRef(HashMap<String, RValueNode> passThrough2RWCol,
          RValueNode val) throws ParseException {
        if (null == val) {
          // SPECIAL CASE: Rewriting a field that is currently set to null. Keep it that way.
          return null;
          // END SPECIAL CASE
        }

        RValueNode rwVal;
        if (val instanceof ScalarFnCallNode) {
          // Rewrite each function to replace output attribute names
          // with the appropriate column in the SELECT clause
          fcb.setAlias2ColMap(passThrough2RWCol);
          rwVal = rewriteSingleFunc((ScalarFnCallNode) val, fcb);
        } else if (val instanceof ColNameNode) {
          // Pass through the old value.
          String key = ((ColNameNode) val).getColName();
          if (false == passThrough2RWCol.containsKey(key)) {
            throw new FatalInternalError(
                "Name '%s' not found in passthrough column map; valid keys are: %s", key,
                passThrough2RWCol.keySet());
          }
          rwVal = passThrough2RWCol.get(key);
        } else {
          rwVal = val;
        }

        if (null == rwVal) {
          throw new FatalInternalError(
              "Computed null column reference for output column based on value '%s' (type %s)",
              val.toString(), val.getClass().getName());
        }

        return rwVal;
      }
    }

    // Pass the visitor into the function that will iterate over all the
    // extract pattern statements in the indicated view.
    cvn.setBody(rewriteExtractPatterns(new cb(), cvn.getBody(), cvn.getModuleName(),
        cvn.getUnqualifiedName()));

  }

}
