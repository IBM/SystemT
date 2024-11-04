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
package com.ibm.avatar.provenance;

import java.util.ArrayList;
import java.util.HashMap;

import com.ibm.avatar.aql.BlockExNode;
import com.ibm.avatar.aql.BoolNode;
import com.ibm.avatar.aql.ColNameNode;
import com.ibm.avatar.aql.ConsolidateClauseNode;
import com.ibm.avatar.aql.CreateViewNode;
import com.ibm.avatar.aql.DictExNode;
import com.ibm.avatar.aql.ExtractListNode;
import com.ibm.avatar.aql.ExtractNode;
import com.ibm.avatar.aql.ExtractionNode;
import com.ibm.avatar.aql.FloatNode;
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
import com.ibm.avatar.aql.POSExNode;
import com.ibm.avatar.aql.PredicateNode;
import com.ibm.avatar.aql.RValueNode;
import com.ibm.avatar.aql.RegexExNode;
import com.ibm.avatar.aql.RegexNode;
import com.ibm.avatar.aql.ReturnClauseNode;
import com.ibm.avatar.aql.ScalarFnCallNode;
import com.ibm.avatar.aql.SelectListItemNode;
import com.ibm.avatar.aql.SelectListNode;
import com.ibm.avatar.aql.SelectNode;
import com.ibm.avatar.aql.SplitExNode;
import com.ibm.avatar.aql.StringNode;
import com.ibm.avatar.aql.TableFnCallNode;
import com.ibm.avatar.aql.TableUDFCallNode;
import com.ibm.avatar.aql.UnionAllNode;
import com.ibm.avatar.aql.ViewBodyNode;
import com.ibm.avatar.aql.WhereClauseNode;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.logging.Log;

/**
 * Copy constructors for all parse tree nodes. These were placed into a separate file because at the
 * time putting them into the parse tree nodes themselves would have created a lot of RTC conflicts.
 * <br/>
 * TODO: At some point, this should be moved to the parse tree nodes themselves. However, this code
 * is only called from the provenance rewrite so moving it is not urgent.
 */
public class AQLViewCopier {

  public HashMap<ViewBodyNode, ViewBodyNode> original2CopyViewBodyMap =
      new HashMap<ViewBodyNode, ViewBodyNode>();
  public ArrayList<CreateViewNode> copyViews = new ArrayList<CreateViewNode>();

  private final Catalog catalog;
  private final ArrayList<String> unsupportedViews = new ArrayList<String>();

  public AQLViewCopier(Catalog catalog) {
    this.catalog = catalog;
  }

  public void addUnsupportedViews(ArrayList<String> views) {
    unsupportedViews.addAll(views);
  }

  public ArrayList<String> getUnsupportedViews() {
    return unsupportedViews;
  }

  /**
   * Makes a copy of each view in the input.
   * 
   * @return
   */
  public HashMap<ViewBodyNode, ViewBodyNode> makeCopy(ArrayList<CreateViewNode> views)
      throws Exception {

    ViewBodyNode copyViewBody;
    CreateViewNode copyViewNode;

    for (CreateViewNode view : views) {

      if (!unsupportedViews.contains(view.getViewName())) {

        // view.dump(System.err, 2);

        try {
          copyViewBody = copyStmt(view.getBody());
          copyViewNode = new CreateViewNode(makeCopyNickname(view.getViewNameNode()), copyViewBody,
              view.getContainingFileName(), view.getOrigTok());
          copyViews.add(copyViewNode);
        } catch (Exception e) {
          // Temporary fix to allow refining complex annotators using AQL features not yet supported
          // in the provenance
          // rewrite.
          // TODO: remove once full AQL is supported
          if (AQLProvenanceRewriter.debug)
            Log.debug("Don't know how to copy view <%s> - marked as base view\n",
                view.getViewName());
          // view.dump(System.err, 2);
          unsupportedViews.add(view.getViewName());
        }
      }
    }

    return original2CopyViewBodyMap;
  }

  private ViewBodyNode copyStmt(ViewBodyNode stmt) throws Exception {

    ViewBodyNode copy = null;

    if (stmt instanceof SelectNode) {

      // SELECT statement
      copy = copySelect((SelectNode) stmt);

    } else if (stmt instanceof UnionAllNode) {

      // UNION ALL
      copy = copyUnion((UnionAllNode) stmt);

    } else if (stmt instanceof MinusNode) {

      // MINUS
      copy = copyMinus((MinusNode) stmt);

    } else if (stmt instanceof ExtractNode) {

      // EXTRACT
      copy = copyExtract((ExtractNode) stmt);

    } else {
      throw new RuntimeException("Don't know how to rewrite " + stmt);
    }

    // add the original and copy to the hashmap
    original2CopyViewBodyMap.put(stmt, copy);

    return copy;

  }

  private ViewBodyNode copyMinus(MinusNode stmt) throws Exception {

    return new MinusNode(copyStmt(stmt.getFirstStmt()), copyStmt(stmt.getSecondStmt()),
        stmt.getContainingFileName(), stmt.getOrigTok());
  }

  private UnionAllNode copyUnion(UnionAllNode union) throws Exception {

    ArrayList<ViewBodyNode> copyOperands = new ArrayList<ViewBodyNode>();

    for (int i = 0; i < union.getNumStmts(); i++) {
      // Copy each union operand in turn
      copyOperands.add(copyStmt(union.getStmt(i)));
    }

    return new UnionAllNode(copyOperands, union.getContainingFileName(), union.getOrigTok());
  }

  private SelectNode copySelect(SelectNode stmt) throws Exception {

    SelectListNode selectList = copySelectList(stmt.getSelectList());
    FromListNode fromList = copyFromList(stmt.getFromList());
    WhereClauseNode whereClause = copyWhereClause(stmt.getWhereClause());
    GroupByClauseNode groupByClause = copyGroupByClause(stmt.getGroupByClause());
    OrderByClauseNode orderByClause = copyOrderByClause(stmt.getOrderByClause());
    ConsolidateClauseNode consolidateClause = copyConsolidateClause(stmt.getConsolidateClause());
    IntNode maxTups = copyInt(stmt.getMaxTups());

    SelectNode copy = new SelectNode(selectList, fromList, whereClause, groupByClause,
        orderByClause, consolidateClause, maxTups, stmt.getContainingFileName(), null);
    return copy;
  }

  private SelectListNode copySelectList(SelectListNode selectList) throws Exception {

    // SELECT *
    if (selectList.getIsSelectStar())
      return new SelectListNode(selectList.getContainingFileName(), selectList.getOrigTok());

    // Standard SELECT list
    ArrayList<SelectListItemNode> copyItems = new ArrayList<SelectListItemNode>();

    for (int i = 0; i < selectList.size(); i++)
      copyItems.add(copySelectListItem(selectList.get(i)));

    return new SelectListNode(copyItems, selectList.getContainingFileName(),
        selectList.getOrigTok());
  }

  private SelectListItemNode copySelectListItem(SelectListItemNode selectListItem)
      throws Exception {

    SelectListItemNode copy = new SelectListItemNode(copyRValue(selectListItem.getValue()),
        new NickNode(selectListItem.getAlias()));
    copy.setIsDotStar(selectListItem.getIsDotStar());

    // If an aggregate function, throw an error because we don't know how to rewrite GROUP BY yet
    if (copy.getValue() instanceof ScalarFnCallNode) {
      String fname = ((ScalarFnCallNode) copy.getValue()).getFuncName();
      if (catalog.lookupAggFunc(fname) != null)
        throw new RuntimeException("Don't know how to rewrite " + fname);
    }

    return copy;
  }

  private FromListNode copyFromList(FromListNode fromList) throws Exception {

    ArrayList<FromListItemNode> copyItems = new ArrayList<FromListItemNode>();

    for (int i = 0; i < fromList.size(); i++)
      copyItems.add(copyFromListItem(fromList.get(i)));

    return new FromListNode(copyItems, fromList.getContainingFileName(), fromList.getOrigTok());
  }

  private FromListItemNode copyFromListItem(FromListItemNode fromListItem) throws Exception {
    FromListItemNode copy = null;

    if (fromListItem instanceof FromListItemViewRefNode) {
      NickNode viewName = ((FromListItemViewRefNode) fromListItem).getViewName();

      copy = new FromListItemViewRefNode(makeCopyNickname(viewName));
    } else if (fromListItem instanceof FromListItemSubqueryNode) {

      ViewBodyNode subquery = ((FromListItemSubqueryNode) fromListItem).getBody();
      copy = new FromListItemSubqueryNode(copyStmt(subquery), fromListItem.getContainingFileName(),
          fromListItem.getOrigTok());

    } else if (fromListItem instanceof FromListItemTableFuncNode) {

      TableFnCallNode tabFunc = ((FromListItemTableFuncNode) fromListItem).getTabfunc();

      if (tabFunc instanceof TableUDFCallNode) {
        copy = new FromListItemTableFuncNode(copyTableUDFFunction((TableUDFCallNode) tabFunc));
      } else
        throw new Exception(
            "Don't know how to rewrite FromListItemNode of type Table Function (that is not a Table UDF Function)");

    } else {
      throw new Exception(
          "Don't know how to rewrite FromListItemNode of type " + fromListItem.getClass());
    } ;

    NickNode alias = fromListItem.getAlias();
    if (alias != null)
      copy.setAlias(new NickNode(alias.getNickname()));

    return copy;
  }

  private WhereClauseNode copyWhereClause(WhereClauseNode whereClause) throws Exception {

    if (whereClause == null)
      return null;

    WhereClauseNode copy =
        new WhereClauseNode(whereClause.getContainingFileName(), whereClause.getOrigTok());

    for (PredicateNode pred : whereClause.getPreds()) {
      copy.addPred(copyPredicateNode(pred));
    }

    return copy;
  }

  private HavingClauseNode copyHavingClause(HavingClauseNode havingClause) throws Exception {

    if (havingClause == null)
      return null;

    HavingClauseNode copy =
        new HavingClauseNode(havingClause.getContainingFileName(), havingClause.getOrigTok());

    for (PredicateNode pred : havingClause.getPreds()) {
      copy.addPred(copyPredicateNode(pred));
    }

    return copy;
  }

  private GroupByClauseNode copyGroupByClause(GroupByClauseNode groupByClause) throws Exception {

    if (groupByClause == null)
      return null;

    GroupByClauseNode copy =
        new GroupByClauseNode(groupByClause.getContainingFileName(), groupByClause.getOrigTok());
    for (RValueNode groupByItem : groupByClause.getValues())
      copy.addValue(copyRValue(groupByItem));

    return copy;
  }

  private OrderByClauseNode copyOrderByClause(OrderByClauseNode orderByClause) throws Exception {

    if (orderByClause == null)
      return null;

    OrderByClauseNode copy =
        new OrderByClauseNode(orderByClause.getContainingFileName(), orderByClause.getOrigTok());
    for (RValueNode orderByItem : orderByClause.getValues())
      copy.addValue(copyRValue(orderByItem));

    return copy;
  }

  private ConsolidateClauseNode copyConsolidateClause(ConsolidateClauseNode consolidateClause)
      throws Exception {

    if (consolidateClause == null)
      return null;

    RValueNode target = copyRValue(consolidateClause.getTarget());
    StringNode type = copyString(consolidateClause.getTypeNode());
    RValueNode priority = (null == consolidateClause.getPriorityTarget() ? null
        : copyRValue(consolidateClause.getPriorityTarget()));
    StringNode direction = (null == consolidateClause.getPriorityDirection() ? null
        : copyString(consolidateClause.getPriorityDirection()));
    return new ConsolidateClauseNode(target, type, priority, direction,
        consolidateClause.getContainingFileName(), consolidateClause.getOrigTok());
  }

  private PredicateNode copyPredicateNode(PredicateNode pred) throws Exception {
    return new PredicateNode(copyScalarFunction(pred.getFunc()), pred.getContainingFileName(),
        pred.getOrigTok());
  }

  private ExtractNode copyExtract(ExtractNode stmt) throws Exception {

    ExtractListNode extractList = copyExtractList(stmt.getExtractList());
    FromListItemNode target = copyFromListItem(stmt.getTarget());
    HavingClauseNode havingClause = copyHavingClause(stmt.getHavingClause());
    ConsolidateClauseNode consolidateClause = copyConsolidateClause(stmt.getConsolidateClause());
    IntNode maxTups = copyInt(stmt.getMaxTups());

    ExtractNode copy = new ExtractNode(extractList, target, havingClause, consolidateClause,
        maxTups, stmt.getContainingFileName(), stmt.getOrigTok());
    return copy;
  }

  private ExtractListNode copyExtractList(ExtractListNode extractList) throws Exception {

    ArrayList<SelectListItemNode> copySelectItems = new ArrayList<SelectListItemNode>();

    for (int i = 0; i < extractList.getSelectList().size(); i++)
      copySelectItems.add(copySelectListItem(extractList.getSelectList().get(i)));

    ExtractionNode extraction = copyExtraction(extractList.getExtractSpec());

    ExtractListNode copy = new ExtractListNode(copySelectItems, extraction,
        extractList.getContainingFileName(), extractList.getOrigTok());
    return copy;
  }

  private ExtractionNode copyExtraction(ExtractionNode extract) throws Exception {

    ExtractionNode copy = null;

    ArrayList<NickNode> copyOutputCols = new ArrayList<NickNode>();
    for (NickNode nick : extract.getOutputCols())
      copyOutputCols.add(copyNick(nick));

    ColNameNode target = copyColName(extract.getTargetName());

    if (extract instanceof DictExNode) {
      DictExNode dictEx = (DictExNode) extract;

      ArrayList<StringNode> copyDicts = new ArrayList<StringNode>();
      for (int i = 0; i < dictEx.getNumDicts(); i++)
        copyDicts.add(copyString(dictEx.getDictName(i)));

      copy = new DictExNode(copyDicts, copyString(dictEx.getFlagsStr()), target,
          copyOutputCols.get(0), extract.getContainingFileName(), extract.getOrigTok());
    } else if (extract instanceof RegexExNode) {
      RegexExNode regexEx = (RegexExNode) extract;

      ArrayList<RegexNode> copyRegexes = new ArrayList<RegexNode>();
      for (int i = 0; i < regexEx.getNumRegexes(); i++)
        copyRegexes.add(copyRegex(regexEx.getRegex(i)));

      ReturnClauseNode returnClause =
          new ReturnClauseNode(extract.getContainingFileName(), extract.getOrigTok());
      for (int i = 0; i < regexEx.getNumGroups(); i++) {

        returnClause.addEntry(regexEx.getGroupID(i), new NickNode(regexEx.getGroupName(i)));
      }

      copy = new RegexExNode(copyRegexes, copyString(regexEx.getFlagsStr()),
          copyInt(regexEx.getMinTok()), copyInt(regexEx.getMaxTok()), target, returnClause,
          extract.getContainingFileName(), extract.getOrigTok());

    } else if (extract instanceof BlockExNode) {
      BlockExNode blockEx = (BlockExNode) extract;

      copy = new BlockExNode(copyInt(blockEx.getMinSize()), copyInt(blockEx.getMaxSize()),
          copyInt(blockEx.getMinSep()), copyInt(blockEx.getMaxSep()), blockEx.getUseTokenDist(),
          target, copyOutputCols.get(0), extract.getContainingFileName(), extract.getOrigTok());
    } else if (extract instanceof POSExNode) {
      @SuppressWarnings("unused")
      POSExNode posEx = (POSExNode) extract;

      // TODO: later
      throw new RuntimeException(
          "Don't know how to rewrite ExtractionNode of type" + extract.getClass());
    } else if (extract instanceof SplitExNode) {
      SplitExNode splitEx = (SplitExNode) extract;

      copy = new SplitExNode(copyColName(splitEx.getSplitCol()), splitEx.getSplitFlags(), target,
          copyOutputCols.get(0), extract.getContainingFileName(), extract.getOrigTok());
    } else {
      throw new RuntimeException(
          "Don't know how to rewrite ExtractionNode of type" + extract.getClass());
    } ;

    return copy;
  }

  private RValueNode copyRValue(RValueNode value) throws Exception {

    if (value instanceof ColNameNode)
      return copyColName((ColNameNode) value);
    else if (value instanceof BoolNode)
      return copyBool((BoolNode) value);
    else if (value instanceof FloatNode)
      return copyFloat((FloatNode) value);
    else if (value instanceof IntNode)
      return copyInt((IntNode) value);
    else if (value instanceof StringNode)
      return copyString((StringNode) value);
    else if (value instanceof NickNode)
      return copyNick((NickNode) value);
    else if (value instanceof RegexNode)
      return copyRegex((RegexNode) value);
    else if (value instanceof ScalarFnCallNode)
      return copyScalarFunction((ScalarFnCallNode) value);
    else
      throw new RuntimeException("Don't know how to rewrite RValueNode of type" + value.getClass());
  }

  private ColNameNode copyColName(ColNameNode colNameNode) {

    NickNode copyTabname = null;

    if (colNameNode.getHaveTabname())
      copyTabname = new NickNode(colNameNode.getTabname());

    return new ColNameNode(copyTabname, new NickNode(colNameNode.getColnameInTable()));
  }

  private FloatNode copyFloat(FloatNode floatNode) {

    if (floatNode == null)
      return null;

    return new FloatNode(floatNode.getValue(), null, null);
  }

  private ScalarFnCallNode copyScalarFunction(ScalarFnCallNode funcNode) throws Exception {

    if (funcNode == null)
      return null;

    ArrayList<RValueNode> copyArgs = new ArrayList<RValueNode>();

    for (RValueNode arg : funcNode.getArgs())
      copyArgs.add(copyRValue(arg));

    ScalarFnCallNode copy = new ScalarFnCallNode(new NickNode(funcNode.getFuncName()), copyArgs);

    return copy;
  }

  private TableFnCallNode copyTableUDFFunction(TableUDFCallNode funcNode) throws Exception {

    if (funcNode == null)
      return null;

    ArrayList<RValueNode> copyArgs = new ArrayList<RValueNode>();

    for (RValueNode arg : funcNode.getArgs())
      copyArgs.add(copyRValue(arg));

    TableFnCallNode copy = new TableUDFCallNode(new NickNode(funcNode.getFuncName()), copyArgs);

    return copy;
  }

  private BoolNode copyBool(BoolNode boolNode) {

    if (boolNode == null)
      return null;

    return new BoolNode(boolNode.getValue(), null, null);
  }

  private IntNode copyInt(IntNode intNode) {

    if (intNode == null)
      return null;

    return new IntNode(intNode.getValue(), null, null);
  }

  private StringNode copyString(StringNode stringNode) {

    if (stringNode == null)
      return null;

    return new StringNode(stringNode.getStr());
  }

  private NickNode copyNick(NickNode nickNode) {

    if (nickNode == null)
      return null;

    return new NickNode(nickNode.getNickname());
  }

  private RegexNode copyRegex(RegexNode regexNode) {

    if (regexNode == null)
      return null;

    return new RegexNode(regexNode.getRegexStr(), regexNode.getNumberOfGroups());
  }

  private NickNode makeCopyNickname(NickNode name) {
    return new NickNode(new String(name.getNickname()));
    // return new NickNode(new String(name.getNickname() + COPY_SUFFIX), catalog);
  }

}
