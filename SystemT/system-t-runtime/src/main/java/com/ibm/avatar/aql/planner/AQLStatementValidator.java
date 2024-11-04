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

import java.io.File;
import java.io.IOException;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;

import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.util.dict.DictParams;
import com.ibm.avatar.algebra.util.document.CsvFileReader;
import com.ibm.avatar.algebra.util.table.TableParams;
import com.ibm.avatar.algebra.util.udf.UDFParams;
import com.ibm.avatar.algebra.util.udf.UDFunction;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.aql.AQLParseTreeNode;
import com.ibm.avatar.aql.AQLParser;
import com.ibm.avatar.aql.AQLParserBase;
import com.ibm.avatar.aql.ColNameNode;
import com.ibm.avatar.aql.ConsolidateClauseNode;
import com.ibm.avatar.aql.CreateDictNode;
import com.ibm.avatar.aql.CreateFunctionNode;
import com.ibm.avatar.aql.CreateTableNode;
import com.ibm.avatar.aql.CreateViewNode;
import com.ibm.avatar.aql.DetagDocNode;
import com.ibm.avatar.aql.ExtendedParseException;
import com.ibm.avatar.aql.ExtractNode;
import com.ibm.avatar.aql.ExtractPatternNode;
import com.ibm.avatar.aql.FromListItemNode;
import com.ibm.avatar.aql.FromListItemSubqueryNode;
import com.ibm.avatar.aql.FromListItemTableFuncNode;
import com.ibm.avatar.aql.FromListNode;
import com.ibm.avatar.aql.GroupByClauseNode;
import com.ibm.avatar.aql.HavingClauseNode;
import com.ibm.avatar.aql.MinusNode;
import com.ibm.avatar.aql.NickNode;
import com.ibm.avatar.aql.OrderByClauseNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.SelectListItemNode;
import com.ibm.avatar.aql.SelectListNode;
import com.ibm.avatar.aql.SelectNode;
import com.ibm.avatar.aql.Token;
import com.ibm.avatar.aql.UnionAllNode;
import com.ibm.avatar.aql.ViewBodyNode;
import com.ibm.avatar.aql.WhereClauseNode;
import com.ibm.avatar.aql.catalog.AbstractFuncCatalogEntry;
import com.ibm.avatar.aql.catalog.AbstractJarCatalogEntry;
import com.ibm.avatar.aql.catalog.AbstractRelationCatalogEntry;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.aql.catalog.CatalogEntry;
import com.ibm.avatar.aql.catalog.DictCatalogEntry;
import com.ibm.avatar.aql.catalog.ScalarUDFCatalogEntry;
import com.ibm.avatar.aql.catalog.TableCatalogEntry;
import com.ibm.avatar.aql.catalog.TableUDFCatalogEntry;
import com.ibm.avatar.aql.catalog.ViewCatalogEntry;
import com.ibm.avatar.aql.compiler.ParseToCatalog;
import com.ibm.avatar.logging.Log;
import com.ibm.avatar.logging.MsgType;

/**
 * Performs validation of various AQL statements. Primarily invoked in preprocessor stage to
 * validate AQL statements, once the type of the view nodes, table nodes and detag nodes are
 * inferred.
 * 
 */
public class AQLStatementValidator {

  // Names of statement regions for describing error locations.
  private static final String EXTRACTION_SPECIFICATION = "extraction specification";
  private static final String PATTERN_EXPRESSION = "pattern expression";
  private static final String GROUP_BY_CLAUSE = "group by clause";
  private static final String ORDER_BY_CLAUSE = "order by clause";
  private static final String CONSOLIDATE_CLAUSE = "consolidate clause";
  private static final String WHERE_CLAUSE = "where clause";
  private static final String SELECT_LIST = "select list";

  protected Catalog catalog;

  /**
   * @param catalog
   */
  public AQLStatementValidator(Catalog catalog) {
    super();
    this.catalog = catalog;
  }

  public void validateViews(ArrayList<CreateViewNode> requiredViews) throws ParseException {
    for (CreateViewNode cvn : requiredViews) {
      validateView(cvn);
    }

    for (DetagDocNode ddn : catalog.getDetagDocNodes()) {
      validateDetag(ddn);
    }
  }

  /**
   * Perform various sanity checks on this parse tree node and its children. Depending on the
   * catalog implementation, this method may also have the side-effect of "importing" external types
   * as artificial AQL views.
   */
  public void validateView(CreateViewNode node) throws ParseException {
    try {
      ViewBodyNode body = node.getBody();
      if (null != body) {

        // Make sure that this view's dependencies are satisfied.
        TreeSet<String> deps = new TreeSet<String>();
        body.getDeps(deps, catalog);
        for (String depname : deps) {
          try {
            catalog.lookupView(depname, node.getEndOfStmtToken());
          } catch (ParseException e) {
            // Handled by FromListViewRefNode.validate(). So, ignore
          }
        }

        // Do a deeper validation on each statement contained within
        // the body.
        validateStmt(body);
      }
    } catch (ParseException pe) {
      // throw ParseToCatalog.makeWrapperException (pe, node.getContainingFilename ());
      throw pe;
    }

  }

  public void validateDetag(DetagDocNode node) throws ParseException {
    // For now, we just verify that the input to the detag statement is a
    // valid name.
    ColNameNode input = node.getTarget();

    // The input is a column ref, in the form <Tabname>.<Colname>; the user
    // may have typed <Tabname> and forgotten the column, in which case, it
    // will get parsed as just a column name. Throw the appropriate
    // exception if this error happens.
    if (false == input.getHaveTabname()) {
      throw AQLParserBase.makeException(
          String.format("No target column in input view %s specified", input.getColName()),
          input.getColnameTok());
    }

    CatalogEntry entry = null;
    try {
      entry = catalog.lookupView(input.getTabname(), node.getEndOfStmtToken());
    } catch (ParseException pe) {
      // If here, input view referred in detag node does not exist
      // No need to handle; this is already handled in Catalog.getRequiredViews() method
    }

    if (null != entry) {
      if (entry instanceof AbstractRelationCatalogEntry) {
        AbstractRelationCatalogEntry arce = (AbstractRelationCatalogEntry) entry;

        // Type inference may have failed to find the type for the target view.
        if (null == arce.getSchema()) {
          throw AQLParserBase.makeException(String.format(
              "Detag statement depends on view %s, whose schema cannot be determined.  "
                  + "Check for parse/compile errors on view %s",
              input.getTabname(), input.getTabname()), input.getOrigTok());
        } else if (false == entry.hasColName(input.getColnameInTable())) {
          throw AQLParserBase.makeException(String.format(
              "Detag statement depends on %s.%s, which is not defined.  "
                  + "Ensure that the specified view and column are defined or imported in the current module.",
              input.getTabname(), input.getColnameInTable()), input.getOrigTok());
        }
      }
    }

  }

  private void validateStmt(ViewBodyNode stmt) throws ParseException {
    if (stmt instanceof SelectNode) {
      validateSelect((SelectNode) stmt);
    } else if (stmt instanceof ExtractPatternNode) {
      // LAURA: Moved the col ref validation from {@link PatternNode#validate() to here for two
      // reasons:
      // 1) reuse the functionality in validateColRef()
      // 2) Eventually we want to refactor PatternNode as an ExtractNode with a different type of
      // extraction, and at
      // that point, the FROM list will be a property of the Extract node, not the Pattern node, and
      // so validation will
      // need to be done at the level of EXTRACT, not PATTERN
      validatePattern((ExtractPatternNode) stmt);
    }
    if (stmt instanceof ExtractNode) {
      validateExtract((ExtractNode) stmt);
    } else if (stmt instanceof MinusNode) {
      MinusNode mn = (MinusNode) stmt;
      validateStmt(mn.getFirstStmt());
      validateStmt(mn.getSecondStmt());

      checkMinusCompatibility(mn);
    } else if (stmt instanceof UnionAllNode) {
      UnionAllNode u = (UnionAllNode) stmt;
      for (int i = 0; i < u.getNumStmts(); i++) {
        validateStmt(u.getStmt(i));
      }

      checkUnionCompatibility(u);
    }
  }

  /**
   * Verifies if components of a UnionAllNode have compatible schemas.
   * 
   * @param unionNode The UnionAllNode that should be validated for compatible schemas
   * @throws ParseException if the components of the UnionAllNode do not have compatible schemas
   */
  private void checkUnionCompatibility(UnionAllNode unionNode) throws ParseException {
    /*
     * Fix for defect# 15933: Compile error,SelectListItemNode constructor should have detected this
     * error. Check whether the inputs produce union-compatible schemas. is conditional now, perform
     * only when there are no validation errors
     */
    TupleSchema firstSchema = null;
    SchemaInferrer schemaInferrer = new SchemaInferrer(catalog);
    try {

      firstSchema = schemaInferrer.computeSchema(unionNode.getStmt(0));

      for (int i = 1; i < unionNode.getNumStmts(); i++) {
        TupleSchema ithSchema = schemaInferrer.computeSchema(unionNode.getStmt(i));
        if (null != firstSchema && false == firstSchema.unionCompatible(ithSchema)) {
          ParseException error = AQLParserBase.makeException(unionNode.getOrigTok(),
              "Input schemas to union must have the same "
                  + "column names and types (first schema is %s and schema %d is %s)",
              firstSchema, (i + 1), ithSchema);
          throw error;
        }
      }
    } catch (RuntimeException re) {
      throw new ParseException(re.getMessage());
    }

  }

  /**
   * Verifies if components of a MinusNode have compatible schemas
   * 
   * @param minusNode The MinusNode that should be validated for compatible schemas
   * @throws ParseException if the components of the MinusNode do not have compatible schemas
   */
  private void checkMinusCompatibility(MinusNode minusNode) throws ParseException {
    TupleSchema firstSchema = null;
    TupleSchema secondSchema;
    SchemaInferrer schemaInferrer = new SchemaInferrer(catalog);
    try {

      firstSchema = schemaInferrer.computeSchema(minusNode.getFirstStmt());

      secondSchema = schemaInferrer.computeSchema(minusNode.getSecondStmt());
      if (null != firstSchema && false == firstSchema.unionCompatible(secondSchema)) {
        throw AQLParserBase.makeException(minusNode.getOrigTok(),
            "Input schemas to minus must have the same "
                + "column names and compatible column types (schemas were %s and %s)",
            firstSchema, secondSchema);
      }

    } catch (RuntimeException re) {
      throw new ParseException(re.getMessage());
    }
  }

  /**
   * Performs various sanity checks on the parse tree node representing a EXTRACT PATTERN statement.
   */
  private void validatePattern(ExtractPatternNode node) throws ParseException {

    // Verify that the FROM list contains unique scoped names
    validateFromList(node.getOrigTok(), node.getFromList());

    // Validate the SELECT list in the EXTRACT clause. Every col reference in the select list must
    // be found in the FROM
    // list
    SelectListNode sl = node.getSelectList();
    for (int i = 0; i < sl.size(); i++) {
      SelectListItemNode item = sl.get(i);

      TreeSet<String> colNames = new TreeSet<String>();
      item.getReferencedCols(colNames, catalog);

      for (String ref : colNames) {
        validateColRef(node.getOrigTok(), node.getFromList(), ref, SELECT_LIST);
      }
    }

    // Validate the PATTERN expression. Every col reference in a PatternAtom must be found in the
    // FROM list
    for (ColNameNode col : node.getPattern().getCols()) {
      validateColRef(node.getOrigTok(), node.getFromList(), col.getColName(), PATTERN_EXPRESSION);
    }

    // Make a list of output column names from the select list and the pattern return clause
    // Only these names can appear in the HAVING and the CONSOLIDATE clauses.
    TupleSchema itemSchema = sl.toSchema();
    ArrayList<NickNode> returnNames = node.getReturnClause().getAllNames();

    // Validate the HAVING clause: Verify that every reference in the HAVING clause is valid
    HavingClauseNode havingClause = node.getHavingClause();
    if (havingClause != null) {
      TreeSet<String> refCols = new TreeSet<String>();
      havingClause.getReferencedCols(refCols, catalog);
      for (String colName : refCols)
        if (!returnNames.contains(new NickNode(colName)) && !itemSchema.containsField(colName))
          throw AQLParser.makeException(String.format(
              "Invalid column name '%s' in having clause. The extract clause does not contain an output column with this name.",
              colName), node.getOrigTok());
    }

    // Validate the CONSOLIDATE clause: Verify that the consolidate target and the priority target
    // are valid
    ConsolidateClauseNode consolidateClause = node.getConsolidateClause();
    if (consolidateClause != null) {
      // Get all the referenced cols in the consolidate target
      TreeSet<String> refCols = new TreeSet<String>();
      consolidateClause.getReferencedCols(refCols, catalog);

      for (String colName : refCols) {
        if (!returnNames.contains(new NickNode(colName)) && !itemSchema.containsField(colName))
          throw AQLParser.makeException(String.format(
              "Invalid column name '%s' in consolidate clause. The extract clause does not contain an output column with this name.",
              colName), node.getOrigTok());
      }
    }
  }

  /**
   * Performs various sanity checks on the parse tree node representing a EXTRACT statement other
   * than EXTRACT PATTERN.
   */
  private void validateExtract(ExtractNode node) throws ParseException {

    // Validate the SELECT list in the EXTRACT clause. Every col reference in the select list must
    // be found in the FROM
    // list
    SelectListNode sl = node.getExtractList().getSelectList();

    // Create a dummy from list node so that we can reuse the existing {@link validateColRef()}
    ArrayList<FromListItemNode> fromListItems = new ArrayList<FromListItemNode>();
    fromListItems.add(node.getTarget());
    FromListNode fromList = new FromListNode(fromListItems,
        node.getTarget().getContainingFileName(), node.getTarget().getOrigTok());

    for (int i = 0; i < sl.size(); i++) {
      SelectListItemNode item = sl.get(i);

      TreeSet<String> colNames = new TreeSet<String>();
      item.getReferencedCols(colNames, catalog);

      for (String ref : colNames) {
        validateColRef(node.getOrigTok(), fromList, ref, SELECT_LIST);
      }
    }

    // Validate the EXTRACTION specification. Every col reference in a PatternAtom must be found in
    // the FROM list
    node.getExtractList().getExtractSpec().getTargetName();
    for (ColNameNode col : node.getExtractList().getExtractSpec().getRequiredCols()) {
      validateColRef(node.getOrigTok(), fromList, col.getColName(), EXTRACTION_SPECIFICATION);
    }

    // Make a list of output column names from the select list and the pattern return clause
    // Only these names can appear in the HAVING and the CONSOLIDATE clauses.
    TupleSchema itemSchema = sl.toSchema();
    ArrayList<NickNode> returnNames = node.getExtractList().getExtractSpec().getOutputCols();

    // Validate the HAVING clause: Verify that every reference in the HAVING clause is valid
    HavingClauseNode havingClause = node.getHavingClause();
    if (havingClause != null) {

      TreeSet<String> refCols = new TreeSet<String>();
      havingClause.getReferencedCols(refCols, catalog);
      for (String colName : refCols)
        if (!returnNames.contains(new NickNode(colName)) && !itemSchema.containsField(colName))
          throw AQLParser.makeException(String.format(
              "Invalid column name '%s' in having clause. The extract clause does not contain an output column with this name.",
              colName), node.getOrigTok());
    }

    // Validate the CONSOLIDATE clause: Verify that the consolidate target and the priority target
    // are valid
    ConsolidateClauseNode consolidateClause = node.getConsolidateClause();
    if (consolidateClause != null) {
      // Get all the referenced cols in the consolidate target
      TreeSet<String> refCols = new TreeSet<String>();
      consolidateClause.getReferencedCols(refCols, catalog);

      for (String colName : refCols) {
        if (!returnNames.contains(new NickNode(colName)) && !itemSchema.containsField(colName))
          throw AQLParser.makeException(String.format(
              "Invalid column name '%s' in consolidate clause. The extract clause does not contain an output column with this name.",
              colName), node.getOrigTok());
      }
    }
  }

  /**
   * Performs various sanity checks on the parse tree node representing a SELECT statement.
   */
  private void validateSelect(SelectNode node) throws ParseException {

    // Verify that the FROM list contains unique scoped names
    validateFromList(node.getOrigTok(), node.getFromList());

    // Verify that every reference in the select list is valid
    SelectListNode sl = node.getSelectList();
    for (int i = 0; i < sl.size(); i++) {
      SelectListItemNode item = sl.get(i);
      TreeSet<String> refCols = new TreeSet<String>();
      item.getReferencedCols(refCols, catalog);

      for (String ref : refCols) {
        validateColRef(node.getOrigTok(), node.getFromList(), ref, SELECT_LIST);
      }
    }

    // Verify that every reference in the where clause is valid.
    WhereClauseNode wc = node.getWhereClause();
    if (null != wc) {
      TreeSet<String> refCols = new TreeSet<String>();
      wc.getReferencedCols(refCols, catalog);
      for (String ref : refCols) {
        validateColRef(node.getOrigTok(), node.getFromList(), ref, WHERE_CLAUSE);
      }
    }

    // Validate that every reference in the consolidate clause is valid
    // Do it after wildcard expansion and alias inference in the select list
    ConsolidateClauseNode consolidate = node.getConsolidateClause();
    if (null != consolidate) {
      TreeSet<String> refCols = new TreeSet<String>();
      consolidate.getReferencedCols(refCols, catalog);
      for (String ref : refCols) {
        validateColRef(node.getOrigTok(), node.getFromList(), ref, CONSOLIDATE_CLAUSE);
      }
    }

    // Validate that every reference in the order by clause is valid
    // Do it after wildcard expansion and alias inference in the select list
    OrderByClauseNode orderBy = node.getOrderByClause();
    if (null != orderBy) {
      TreeSet<String> refCols = new TreeSet<String>();
      orderBy.getReferencedCols(refCols, catalog);

      for (String ref : refCols) {
        validateColRef(node.getOrigTok(), node.getFromList(), ref, ORDER_BY_CLAUSE);
      }
    }

    // Validate that every reference in the group by clause is valid
    // Do it after wildcard expansion and alias inference in the select list
    GroupByClauseNode groupBy = node.getGroupByClause();
    if (null != groupBy) {
      TreeSet<String> refCols = new TreeSet<String>();
      groupBy.getReferencedCols(refCols, catalog);

      for (String ref : refCols) {
        validateColRef(node.getOrigTok(), node.getFromList(), ref, GROUP_BY_CLAUSE);
      }
    }

    // Validate the SELECT list w.r.t GROUP BY clause. In case of an artificial GROUP BY clause,
    // this will ensure
    // that
    // the SELECT list contains only aggregates. NOTE: Do this after wildcard expansion and alias
    // inference in the
    // select list
    java.util.List<ParseException> errors = node.validateSelectList(catalog);
    // The method outputs a list of ParseExceptions. Throw the first one and ignore the rest. At
    // some point we should
    // refactor the catalog validation to collect a list of parse exception, as opposed to throwing
    // the first one.
    if (errors.size() > 0)
      throw errors.get(0);
  }

  /**
   * Validate the FROM list of a SELECT or EXTRACT PATTERN statement. Currently just ensures that
   * the scoped names (aliases) are unique.
   * 
   * @param origTok
   * @param fromList
   * @throws ParseException
   */
  private void validateFromList(Token origTok, FromListNode fromList) throws ParseException {
    // Maintain a set of unique aliases
    HashSet<String> uniqueAliases = new HashSet<String>();

    for (FromListItemNode fromItem : fromList.getItems()) {
      String alias = fromItem.getScopedName();
      if (uniqueAliases.contains(alias)) {
        // We found this alias before. Throw an exception.
        throw AQLParser.makeException(
            String.format("Alias '%s' referenced multiple times in FROM clause.", alias), origTok);
      } else {
        // We didn't find this alias before. Add it to the list of unique aliases.
        uniqueAliases.add(alias);
      }
    }

  }

  /**
   * Validate a reference for a given node
   * 
   * @param origTok The starting token of the node
   * @param fromListNode The node containing all the valid view references
   * @param ref The reference expression to be validated. It must be of the form view.attr
   * @param clause A string describing the clause in which the reference occurs
   * @throws ParseException
   */
  private void validateColRef(Token origTok, FromListNode fromListNode, String ref, String clause)
      throws ParseException {
    // Split the reference into table and column.
    final int pos = ref.lastIndexOf('.');
    if (pos < 0) {

      throw AQLParser.makeException(
          String.format("In %s, the column reference '%s' is not of the form 'viewname.colname'.",
              clause, ref),
          origTok);
    }
    String localTabName = ref.substring(0, pos);
    String colName = ref.substring(pos + 1);

    // Map the local table name to the appropriate external type.
    FromListItemNode fli = fromListNode.getByName(localTabName);

    if (null == fli) {
      // this local table name references an input that doesn't exist.
      throw AQLParserBase.makeException(String.format(
          "Name '%s' of column reference '%s' not found in from list", localTabName, ref), origTok);
    }

    if (fli instanceof FromListItemTableFuncNode) {
      if (((FromListItemTableFuncNode) fli).getTabfunc().getFuncName().equals("Consolidate")) {
        // SPECIAL CASE: No schema checks for consolidate table
        // functions
        Log.log(
            MsgType.AQLCompileWarning, "At line %d: Consolidate() table "
                + "function is deprecated.  " + "Use a 'consolidate on' clause instead.",
            fli.getOrigTok().beginLine);
        return;
        // END SPECIAL CASE
      }
    }

    // For subqueries, validate the subquery itself
    // Then infer the schema of the subquery and check whether it contains the indicated column.
    // We must do this validation now, otherwise the user will get an exception that refers to a
    // rewritten subquery
    if (fli instanceof FromListItemSubqueryNode) {

      // Validate the subquery
      ViewBodyNode sq = ((FromListItemSubqueryNode) fli).getBody();
      validateStmt(sq);

      // Infer the schema of the subquery on the fly and make sure it contains the column
      // *** PERF ALERT ***: we infer the schema on the fly for each col ref validation !!!
      // If this turns out to be a bottleneck, consider caching the subquery schema outside
      // validateColRef
      TupleSchema sqSchema = new SchemaInferrer(catalog).computeSchema(sq);
      // ***END PERF ALERT ***
      if (!sqSchema.containsField(colName)) {
        throw AQLParser
            .makeException(
                String.format("Subquery %s has no output column called '%s'" + " (columns are %s)",
                    localTabName, colName, Arrays.asList(sqSchema.getFieldNames())),
                fli.getOrigTok());
      }

      return;
    }

    // For the rest of table functions (except consolidate)
    // and the view references, Pull up the appropriate catalog entry
    // and check whether it contains the indicated column.
    CatalogEntry entry = catalog.lookup(fli);

    if (null == entry) {
      throw AQLParser.makeException(String.format("No catalog entry for '%s'", fli),
          fli.getOrigTok());
    }
    if (entry instanceof ViewCatalogEntry && ((ViewCatalogEntry) entry).getSchema() == null) {
      // just return
      return;
    }
    if (false == entry.hasColName(colName)) {
      if (fli.getOrigTok() == null) {
        // it is a rewritten subquery. So, pin point the error to origTok instead of
        // fli.getOrigTok()
        throw AQLParser.makeException(
            String.format("Subquery %s has no output column called '%s'" + " (columns are %s)",
                localTabName, colName, entry.getColNames()),
            origTok);
      } else {
        throw AQLParser.makeException(
            String.format("%s (%s) has no output column called '%s'" + " (columns are %s)",
                localTabName, fli.getExternalName(), colName, entry.getColNames()),
            fli.getOrigTok());
      }
    }
    // if (null == entry) {
    // throw new CatalogEntryNotFoundException(String.format ("No catalog entry for '%s'", fli));
    // }
    // // fli.getOrigTok ()); }
    // if (entry != null) {
    //
    // if (false == entry.hasColName (colName)) { throw AQLParser.makeException (
    // String.format ("%s (%s) has no output column called '%s'" + " (columns are %s)",
    // localTabName,
    // fli.getExternalName (), colName, entry.getColNames ()), fli.getOrigTok ()); }
    // }
  }

  /**
   * Performs the following validation on dictionaries: <br/>
   * 1) If a dictionary is created from a table, then validate that the referenced table is
   * defined<br/>
   * 2) <<Add more validations, if required>> <br>
   * This method adds the encountered validation error, to the list of compilation exception in the
   * catalog.
   * 
   * @param catalog
   */
  public void validateDictionaries() {
    ArrayList<CreateDictNode> cdnList;
    try {
      cdnList = catalog.getCreateDictNodes();
    } catch (ParseException e) {
      // Add the encountered exception to list of compilation errors in the catalog
      catalog.addCompilerException(e);
      return;
    }

    for (CreateDictNode node : cdnList) {
      try {
        if (node instanceof CreateDictNode.FromTable) {
          String tableName = node.getParams().getTabName();

          // Try to find the backing table.
          CatalogEntry entry = catalog.lookupTable(tableName);

          if (null == entry) {
            throw DictCatalogEntry.makeException(node.getOrigTok(), node.getContainingFileName(),
                "Table '%s', required for dictionary '%s', not found", tableName,
                node.getDictname());
          }

          // We currently can't create a dictionary from a dynamic view.
          if (false == entry instanceof TableCatalogEntry) {
            throw DictCatalogEntry.makeException(node.getOrigTok(), node.getContainingFileName(),
                "View '%s', referenced by dictionary '%s', " + "is not a static table", tableName,
                node.getDictname());
          }

          TableCatalogEntry tabEntry = (TableCatalogEntry) entry;

          // As of version 2.0, we do not support dictionary coming from imported table
          if (true == tabEntry.isImported()) {
            throw DictCatalogEntry.makeException(node.getOrigTok(), node.getContainingFileName(),
                "Dictionary '%s' is coming from an imported table '%s'. This functionality is not supported.",
                node.getDictname(), tabEntry.getName());
          }

          // Verify that the requested dict entries column is present,
          // or set it ourselves if the table only has one column.
          DictParams params = node.getParams();
          String colName = params.getTabColName();
          ArrayList<String> colNames = tabEntry.getColNames();

          if (null != colName) {
            // Parameters specify a column name; verify that it exists.
            if (false == colNames.contains(colName)) {
              throw DictCatalogEntry.makeException(node.getOrigTok(), node.getContainingFileName(),
                  "Table '%s', referenced by dictionary '%s', "
                      + "does not have a column called '%s'",
                  tableName, node.getDictname(), colName);
            }
          } else {
            // No column specified; try to infer one.
            if (1 != colNames.size()) {
              throw DictCatalogEntry.makeException(node.getOrigTok(), node.getContainingFileName(),
                  "Table '%s', referenced by dictionary '%s', " + "has more than one column, "
                      + "and no column name specified",
                  tableName, node.getDictname(), colName);
            }
            params.setTabColName(colNames.get(0));
          }
        }
      } catch (ExtendedParseException epe) {
        catalog.addCompilerException(epe);
      } catch (ParseException pe) {
        // Add the encountered exception to list of compilation errors in the catalog
        catalog.addCompilerException(
            ParseToCatalog.makeWrapperException(pe, node.getContainingFileName()));
      }
    }
  }

  /**
   * Performs the following validation on table: <br/>
   * 1) If a table is created from a file, then validate that table entries<br/>
   * 2) <<Add more validations, if required>> <br>
   * This method adds the encountered validation error, to the list of compilation exception in the
   * catalog.
   */
  public void validateTables() {
    ArrayList<CreateTableNode> ctList;
    try {
      ctList = catalog.getCreateTableNodes();
    } catch (Exception e) {
      // Add the encountered exception to list of compilation errors in the catalog
      catalog.addCompilerException(e);
      return;
    }

    for (CreateTableNode node : ctList) {
      try {
        if (node instanceof CreateTableNode.FromFile) {

          TableParams params = node.getParams();

          String tableName = node.getUnqualifiedName();

          // Try to find the table
          CatalogEntry entry = catalog.lookupTable(tableName);

          // read tuples from file and add them to the catalog entry
          String tableFileURI = ((TableCatalogEntry.OnDisk) entry).getTableURI();
          TupleSchema schema = params.getSchema();
          ArrayList<ArrayList<String>> tuples = new ArrayList<ArrayList<String>>();
          tuples = CsvFileReader.readTable(tableFileURI, schema);
          // throw compile error if tuples empty
          if (tuples.size() < 1) {
            ParseException pe = new ParseException(String.format(
                "Table %s has no tuples.  Compile time tables cannot be empty!", tableName));
            catalog.addCompilerException(
                ParseToCatalog.makeWrapperException(pe, node.getContainingFileName()));
          }

          node.setTupVals(tuples);

        }
      }

      catch (ExtendedParseException epe) {
        catalog.addCompilerException(epe);
      } catch (ParseException pe) {
        // Add the encountered exception to list of compilation errors in the catalog
        catalog.addCompilerException(
            ParseToCatalog.makeWrapperException(pe, node.getContainingFileName()));
      } catch (Exception e) {
        ParseException pe =
            TableCatalogEntry.makeException(node.getOrigTok(), null, e.getMessage());
        catalog.addCompilerException(pe);
      }
    }
  }

  /**
   * Validates all "create function" statements, including those associated with functions that are
   * never called. May load the implementing class to ensure that it will load correctly. Adds all
   * errors found to the catalog.
   */
  public void validateFunctions() {
    for (AbstractFuncCatalogEntry entry : catalog.getAllUDFs()) {
      if (entry instanceof ScalarUDFCatalogEntry) {
        // User-defined scalar function.
        ScalarUDFCatalogEntry udfEntry = (ScalarUDFCatalogEntry) entry;

        AQLParseTreeNode node = udfEntry.getParseTreeNode();

        // Ignore "import function" statements; we're only interested in validating the "create
        // function" statements
        // here.
        if (node instanceof CreateFunctionNode) {

          // System.err.printf("Validating function %s\n", udfEntry.getFqFuncName ());

          // create a temporary class loader that can be closed so that we can
          // remove jar files at end of run
          URLClassLoader tempLoader = null;

          try {

            // Pull function information from the parse tree node
            UDFParams params = udfEntry.toUDFParams(catalog);

            UDFunction udf = new UDFunction(node.getOrigTok(), params);

            // Get enough information to be able to fire up a ClassLoader.
            AbstractJarCatalogEntry jarEntry = catalog.lookupJar(params.getJarName());
            if (null == jarEntry) {
              throw AQLParserBase.makeException(node.getOrigTok(),
                  "Jar file %s not found in catalog", params.getJarName());
            }

            tempLoader =
                (URLClassLoader) jarEntry.makeClassLoader(this.getClass().getClassLoader());
            udf.loadReflectionInfo(tempLoader);

          } catch (ParseException e) {
            // We settle for just catching the first problem that crops up for each function
            // Add file information first.
            System.err.printf("Caught exception...\n");
            if (null == e.currentToken) {
              e.currentToken = node.getOrigTok();
            }
            ExtendedParseException epe =
                new ExtendedParseException(e, new File(node.getContainingFileName()));

            catalog.addCompilerException(epe);
          } finally {
            try {
              if (tempLoader != null) {
                tempLoader.close();
              }
            } catch (IOException e) {
              throw new FatalInternalError(e.getMessage());
            }
          }
        }

      } else if (entry instanceof TableUDFCatalogEntry) {
        // User-defined table function. Instantiate the target class and verify the declared input
        // schema and return
        // type.
        TableUDFCatalogEntry udfEntry = (TableUDFCatalogEntry) entry;
        List<ParseException> errors = udfEntry.validateReturnedSchema(catalog);
        for (ParseException parseException : errors) {
          catalog.addCompilerException(parseException);
        }
      } else {
        throw new FatalInternalError("Unexpected UDF catalog entry type %s (entry is for %s)",
            entry.getClass().getName(), entry.getName());
      }
    }
  }

}
