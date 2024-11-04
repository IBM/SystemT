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
import java.util.Arrays;
import java.util.List;

import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.function.scalar.NullConst;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.api.exceptions.TableFunctionNotFoundException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.api.exceptions.UnionCompatibilityException;
import com.ibm.avatar.aql.AQLParser;
import com.ibm.avatar.aql.AQLParserBase;
import com.ibm.avatar.aql.AbstractImportNode;
import com.ibm.avatar.aql.ColNameNode;
import com.ibm.avatar.aql.ConsolidateClauseNode;
import com.ibm.avatar.aql.CreateExternalViewNode;
import com.ibm.avatar.aql.CreateTableNode;
import com.ibm.avatar.aql.CreateViewNode;
import com.ibm.avatar.aql.ExtendedParseException;
import com.ibm.avatar.aql.ExtractNode;
import com.ibm.avatar.aql.ExtractPatternNode;
import com.ibm.avatar.aql.ExtractionNode;
import com.ibm.avatar.aql.FromListItemNode;
import com.ibm.avatar.aql.FromListItemSubqueryNode;
import com.ibm.avatar.aql.FromListItemTableFuncNode;
import com.ibm.avatar.aql.FromListItemViewRefNode;
import com.ibm.avatar.aql.GroupByClauseNode;
import com.ibm.avatar.aql.HavingClauseNode;
import com.ibm.avatar.aql.ImportTableNode;
import com.ibm.avatar.aql.ImportViewNode;
import com.ibm.avatar.aql.MinusNode;
import com.ibm.avatar.aql.NickNode;
import com.ibm.avatar.aql.OrderByClauseNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.PredicateNode;
import com.ibm.avatar.aql.RValueNode;
import com.ibm.avatar.aql.RequireColumnsNode;
import com.ibm.avatar.aql.ScalarFnCallNode;
import com.ibm.avatar.aql.SelectListItemNode;
import com.ibm.avatar.aql.SelectListNode;
import com.ibm.avatar.aql.SelectNode;
import com.ibm.avatar.aql.TableFnCallNode;
import com.ibm.avatar.aql.Token;
import com.ibm.avatar.aql.TopLevelParseTreeNode;
import com.ibm.avatar.aql.UnionAllNode;
import com.ibm.avatar.aql.ViewBodyNode;
import com.ibm.avatar.aql.WhereClauseNode;
import com.ibm.avatar.aql.catalog.AbstractRelationCatalogEntry;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.aql.catalog.CatalogEntry;
import com.ibm.avatar.aql.catalog.DetagCatalogEntry;
import com.ibm.avatar.aql.catalog.ExternalViewCatalogEntry;
import com.ibm.avatar.aql.catalog.TableCatalogEntry;
import com.ibm.avatar.aql.catalog.TableFuncCatalogEntry;
import com.ibm.avatar.aql.catalog.ViewCatalogEntry;
import com.ibm.avatar.aql.compiler.ParseToCatalog;
import com.ibm.avatar.logging.Log;

/**
 * Performs compile time type inference for views and tables defined in the AQL. Stores the computed
 * schema in catalog entry objects.
 * 
 */
public class SchemaInferrer {
  private static final boolean debug = false;

  /**
   * Special schema indicating that type inference has been attempted and it failed. Used in type
   * inference to avoid recomputing schemas that we tried to compute earlier but failed, but still
   * present to the user a meaningful error message along the lines of "Schema of view X cannot be
   * determined." as opposed to various peculiar errors that the compiler will produce when the
   * schema is either NULL or an empty schema. Placed here as opposed to in {@link TupleSchema}
   * because {@link TupleSchema} is part of the public API.
   */
  public static final AbstractTupleSchema TYPE_INFERENCE_ERROR_SCHEMA =
      new TupleSchema(new String[0], new FieldType[0]);

  /** Catalog for module we are currently compiling. */
  protected Catalog catalog;

  /**
   * Constructs a schema inferrer object
   * 
   * @param catalog
   */
  public SchemaInferrer(Catalog catalog) {
    super();
    this.catalog = catalog;
  }

  /**
   * Infer the schema of all view definitions (including external views) and tables during compile
   * time. Stores the computed schema objects into catalog entries.
   * 
   * @param catalog
   * @param views a list of CreateViewNodes as returned by catalog.getRequiredViews(). Note that
   *        this list is always in topologically sorted order. This does not include
   *        ExternalViewNodes
   * @throws ParseException on the FIRST schema validation error encountered, since a single
   *         validation error early on can lead to hundreds of spurious errors
   */
  public static void inferTypes(Catalog catalog, ArrayList<CreateViewNode> views)
      throws ParseException {
    SchemaInferrer instance = new SchemaInferrer(catalog);

    // Pass 1: Infer schema for Document View before any other type inference
    RequireDocumentPlanNode unionizedDocPlanNode = catalog.getDocumentPlan();
    ViewCatalogEntry docViewCatalogEntry =
        (ViewCatalogEntry) catalog.lookupView(Constants.DEFAULT_DOC_TYPE_NAME, null);
    docViewCatalogEntry.setViewSchema(computeDocSchema(unionizedDocPlanNode));

    // Pass 2: Infer schema for tables before views, because some views might refer to tables
    ArrayList<CreateTableNode> ctNodes = catalog.getCreateTableNodes();
    for (CreateTableNode ctNode : ctNodes) {
      TupleSchema tableSchema = instance.computeSchema(ctNode);
      TableCatalogEntry tce = (TableCatalogEntry) catalog.lookupTable(ctNode.getTableName());
      tce.setTableSchema(tableSchema);
    }

    // Pass 3: Infer schema for external views
    ArrayList<CreateExternalViewNode> cevNodes = catalog.getCreateExternalViewNodes();
    for (CreateExternalViewNode cevn : cevNodes) {
      TupleSchema viewSchema = instance.computeSchema(cevn);
      ExternalViewCatalogEntry evce = (ExternalViewCatalogEntry) catalog
          .lookupView(cevn.getExternalViewName(), cevn.getEndOfStmtToken());
      evce.setViewSchema(viewSchema);
    }

    AQLStatementValidator validator = new AQLStatementValidator(catalog);

    if (debug) {
      System.err.printf("Inferring schema for views:\n");
      for (CreateViewNode createViewNode : views) {
        System.err.printf("   %s\n", createViewNode.getViewName());
      }
      System.err.printf("-----------\n");
    }

    // Pass 4: Infer schema for normal views (i.e non-external views)
    for (CreateViewNode cvn : views) {
      if (debug)
        Log.debug("Compiling view %s", cvn.getViewName());

      try {
        TupleSchema viewSchema = instance.computeSchema(cvn);
        ViewCatalogEntry vce =
            (ViewCatalogEntry) catalog.lookupView(cvn.getViewName(), cvn.getEndOfStmtToken());
        vce.setViewSchema(viewSchema);
        if (debug)
          Log.debug("Schema for view %s: %s", cvn.getViewName(), viewSchema);

        validator.validateView(cvn);
      } catch (Throwable e) {

        // Mark the catalog entry with an error schema. An error schema in the catalog means we have
        // attempted to
        // compute the
        // schema and failed. On the other hand, a null schema in the catalog means that we have
        // never encountered this
        // view before, and so we have not attempted to compute its schema. Therefore, any time type
        // inference finds a
        // null schema in the catalog, the cause can be only an error in the AQL Compiler's
        // topological sort.
        ViewCatalogEntry vce =
            (ViewCatalogEntry) catalog.lookupView(cvn.getViewName(), cvn.getEndOfStmtToken());
        vce.setViewSchema((TupleSchema) TYPE_INFERENCE_ERROR_SCHEMA);

        if (debug)
          Log.debug("Exception compiling view %s: %s", cvn.getViewName(), e);

        if (e instanceof ExtendedParseException) {
          catalog.addCompilerException((ExtendedParseException) e);
        } else if (e instanceof ParseException) {
          ParseException wrappedException = ParseToCatalog.makeWrapperException((ParseException) e,
              instance.getContainingFileName(cvn));
          catalog.addCompilerException(wrappedException);
        } else {
          Log.debug("Throwable caught when compiling view %s: %s", cvn.getViewName(), e);
          ParseException pe = AQLParserBase.makeException(e, cvn.getOrigTok(),
              "Caught unchecked exception when compiling view %s: %s", cvn.getViewName(),
              TextAnalyticsException.findNonNullMsg(e));
          ParseException wrappedException =
              ParseToCatalog.makeWrapperException(pe, instance.getContainingFileName(cvn));
          catalog.addCompilerException(wrappedException);

        }
        continue;
      }

    }

    // Pass 5: Infer schema for detag views
    ArrayList<DetagCatalogEntry> detagCatalogEntries = catalog.getDetagCatalogEntries();
    for (DetagCatalogEntry dce : detagCatalogEntries) {
      try {
        validator.validateDetag(dce.getParseTreeNode());
        if (dce.getDetagSchema() == null) {
          // schema is not inferred during view schema inference.
          TupleSchema schema = instance.computeSchema(dce);
          dce.setDetagSchema(schema);
        }
      } catch (ParseException pe) {
        ParseException wrappedException =
            ParseToCatalog.makeWrapperException(pe, dce.getParseTreeNode().getContainingFileName());
        catalog.addCompilerException(wrappedException);
      }
    }
  }

  /**
   * Perform additional type inference for new views created (usually through rewrites, i.e
   * provenance) since the last call to {@link #inferTypes(Catalog, ArrayList)}.
   * <p>
   * Will not work unless {@link #inferTypes(Catalog, ArrayList)} has already been run.
   * 
   * @param catalog AQL catalog, as populated with the previous call to
   *        {@link #inferTypes(Catalog, ArrayList)}
   * @param view parse tree node for newly minted view
   * @return inferred schema (schema is also inserted into catalog)
   * @throws ParseException
   */
  public static TupleSchema inferAdditionalType(Catalog catalog, CreateViewNode view)
      throws ParseException {
    // Pull up the catalog entry for the new view.
    ViewCatalogEntry vce =
        (ViewCatalogEntry) catalog.lookupView(view.getViewName(), view.getOrigTok());

    SchemaInferrer si = new SchemaInferrer(catalog);
    TupleSchema schema = si.computeSchema(view.getBody());
    vce.setViewSchema(schema);
    return schema;
  }

  /**
   * Perform additional type inference for references that may be new views created (usually through
   * rewrites, i.e provenance) since the last call to {@link #inferTypes(Catalog, ArrayList)}.
   * <p>
   * Will not work unless {@link #inferTypes(Catalog, ArrayList)} has already been run.
   * 
   * @param catalog AQL catalog, as populated with the previous call to
   *        {@link #inferTypes(Catalog, ArrayList)}
   * @param target parse tree node for
   * @return inferred schema (schema is also inserted into catalog)
   * @throws ParseException
   */
  public static TupleSchema inferTargetType(Catalog catalog, FromListItemNode target)
      throws ParseException {
    if (null == catalog) {
      throw new FatalInternalError("Null catalog pointer passed to TupleSchema.inferTargetType()");
    }
    SchemaInferrer si = new SchemaInferrer(catalog);
    return si.computeSchema(target);
  }

  private String getContainingFileName(CreateViewNode cvn) {
    String ret = cvn.getContainingFileName();
    if (ret == null) {
      ret = cvn.getBody().getContainingFileName();
    }
    return ret;
  }

  /*
   * TYPE INFERENCE METHODS One method per parse tree node type.
   */

  /**
   * Computes the schema of the unionized document plan node.
   * 
   * @param documentPlan Unionized document plan node
   * @return Computed schema of the document plan node
   * @throws ParseException if there is a problem computing the schema
   */
  private static TupleSchema computeDocSchema(RequireDocumentPlanNode documentPlan)
      throws ParseException {
    RequireColumnsNode docSchemaNode = documentPlan.getSchemaNode();

    ArrayList<NickNode> colNames = docSchemaNode.getColNames();
    ArrayList<NickNode> colTypes = docSchemaNode.getColTypes();

    String[] columnNames = new String[colNames.size()];
    FieldType[] columnTypes = new FieldType[colTypes.size()];

    for (int i = 0; i < colNames.size(); i++) {
      columnNames[i] = colNames.get(i).getNickname();
    }

    for (int i = 0; i < colTypes.size(); i++) {
      columnTypes[i] = FieldType.stringToFieldType(colTypes.get(i).getNickname());
    }

    // Do not use TupleSchema.makeDocSchema() because we do not want parameterized types in schema
    // for now (as of v2.0)
    TupleSchema schema = new TupleSchema(columnNames, columnTypes);
    schema.setName(Constants.DEFAULT_DOC_TYPE_NAME);
    return schema;
  }

  protected TupleSchema computeSchema(DetagCatalogEntry dce) throws ParseException {
    TupleSchema schema = null;
    if (dce.getName().equals(dce.getParseTreeNode().getDetaggedDocName())) {
      // this is the detagged document view, so return a hardcoded schema
      String[] colNames = new String[] {Constants.DOCTEXT_COL};
      FieldType[] colTypes = new FieldType[] {FieldType.TEXT_TYPE};
      schema = new TupleSchema(colNames, colTypes);
    } else {
      // this is the catalog entry for auxiliary views
      int size = dce.getColNames().size();
      String[] colNames = new String[size];
      System.arraycopy(dce.getColNames().toArray(new String[0]), 0, colNames, 0, size);

      FieldType[] colTypes = new FieldType[size];
      for (int i = 0; i < size - 1; ++i) {
        colTypes[i] = FieldType.TEXT_TYPE;
      }
      colTypes[size - 1] = FieldType.SPAN_TYPE;

      schema = new TupleSchema(colNames, colTypes);
    }
    schema.setName(dce.getName());
    return schema;
  }

  /**
   * Computes schema of external view
   * 
   * @param cevn CreateExternalViewNode
   * @return
   * @throws ParseException
   */
  protected TupleSchema computeSchema(CreateExternalViewNode cevn) throws ParseException {
    TupleSchema schema = createTupleSchema(cevn.getColNames(), cevn.getColTypes());
    schema.setName(cevn.getExternalViewName());
    return schema;
  }

  protected TupleSchema computeSchema(CreateTableNode ctNode) throws ParseException {
    TupleSchema schema = createTupleSchema(ctNode.getColNames(), ctNode.getColTypes());
    schema.setName(ctNode.getTableName());
    return schema;
  }

  protected TupleSchema computeSchema(CreateViewNode cvn) throws ParseException {
    TupleSchema schema = null;

    try {
      schema = computeSchema(cvn.getBody());
      schema.setName(cvn.getViewName());

      try {
        validateSchema(cvn.getOrigTok(), cvn.getViewName(), schema);
      } catch (ParseException e) {
        // Turn the validation error into a more complete error message
        String viewBodyStr = cvn.dumpToStr(0);
        String betterMsg = String.format("Schema validation failed: %s.\n"
            + "Expression on which validation failed was:\n" + "%s", e.getMessage(), viewBodyStr);
        ParseException betterException = new ParseException(e, betterMsg);
        betterException.currentToken = cvn.getOrigTok();
        throw betterException;
      }
    } catch (Exception e) {
      if (e instanceof ParseException) {
        // need to preserve the token of Parse exception.
        ParseException pe = (ParseException) e;

        // If internal code did not add token offset info, use the view's last token as a location.
        if (null == pe.currentToken) {
          pe.currentToken = cvn.getEndOfStmtToken();
        }

        throw ParseToCatalog.makeWrapperException(pe, cvn.getBody().getContainingFileName());
      } else {
        // create a new ParseException object, wrap it into ExtendedParseException and throw the
        // wrapped exception
        if (isRewrittenNode(cvn)) {
          // Use ViewBody's origTok and fileName attributes
          throw ParseToCatalog.makeWrapperException(
              AQLParserBase.makeException(e, cvn.getBody().getOrigTok(), "%s", e.getMessage()),
              cvn.getBody().getContainingFileName());
        } else {

          // Unexpected error that is not a ParseException.
          // Wrap the error in a ParseException.
          ParseException pe = AQLParserBase.makeException(e, cvn.getViewNameNode().getOrigTok(),
              "%s", e.getMessage());

          // Add file information
          ParseException wrappedException =
              ParseToCatalog.makeWrapperException(pe, cvn.getContainingFileName());

          throw wrappedException;
        }

      }
    }

    return schema;
  }

  private boolean isRewrittenNode(CreateViewNode cvn) {
    // cvn.containingFilename used to only be set by parser.
    // now, containingFilename is always set, so check to see if the view name has a token.
    // If not, then it's rewritten -- eyhung

    // return cvn.getContainingFileName () == null;
    return cvn.getViewNameNode().getOrigTok() == null;
  }

  /**
   * Compute the schema of an AQL expression without updating the catalog
   * 
   * @param vbn top-level parse tree node for the expression
   * @return output schema of the expression
   * @throws ParseException
   */
  public TupleSchema computeSchema(ViewBodyNode vbn) throws ParseException {
    vbn.expandWildcardAndInferAlias(catalog);

    // Sanity-check the statement before attempting type inference.
    List<ParseException> errors = vbn.validate(catalog);
    if (0 != errors.size()) {
      // Just throw the first exception; remaining errors are caught during the validation phase of
      // compilation.
      throw errors.get(0);
    }

    if (vbn instanceof SelectNode) {
      return computeSchema((SelectNode) vbn);
    }

    if (vbn instanceof ExtractNode) {
      return computeSchema((ExtractNode) vbn);
    }

    if (vbn instanceof UnionAllNode) {
      return computeSchema((UnionAllNode) vbn);
    }

    if (vbn instanceof MinusNode) {
      return computeSchema((MinusNode) vbn);
    }

    if (vbn instanceof ExtractPatternNode) {
      return computeSchema((ExtractPatternNode) vbn);
    }

    // if ViewBodyNode is of any other type, then there is some internal error. Though this error
    // would never occur for
    // now, because we have handled all the subclasses of ViewBodyNode in the if-then-else
    // statements above, but might
    // surface in the future when a new type of input is added and the systemT runtime developer
    // forgets to modify all
    // places where the new type requires special handling. We display a generic message, for lack
    // of a better one!
    throw AQLParserBase.makeException("Unable to determine the schema of view body node",
        vbn.getOrigTok());
  }

  protected TupleSchema computeSchema(SelectNode node) throws ParseException {
    return computeSchema(node.getSelectList(), node.getFromList().toArray(), node.getWhereClause(),
        node.getOrderByClause(), node.getGroupByClause(), node.getConsolidateClause());
  }

  protected TupleSchema computeSchema(ExtractNode node) throws ParseException {
    // Pull out detailed information from the parse tree node.
    ExtractionNode extractSpec = node.getExtractList().getExtractSpec();
    ColNameNode targetCol = extractSpec.getTargetName();
    FromListItemNode targetView = node.getTarget();
    TupleSchema targetViewSchema = computeSchema(targetView);

    // Verify that the input to the extract is a span-producing type.
    {
      String targetColName = targetCol.getColnameInTable();
      if (false == targetViewSchema.containsField(targetColName)) {
        throw AQLParserBase.makeException(targetCol.getOrigTok(),
            "Column '%s' does not exist in schema of '%s', which is (%s).  "
                + "Check the spelling of the column name.",
            targetColName, targetView.toString(), targetViewSchema);
      }

      FieldType targetFieldType = targetViewSchema.getFieldTypeByName(targetColName);

      if (false == targetFieldType.getIsSpanOrText()) {

        throw AQLParserBase.makeException(targetCol.getOrigTok(),
            "Target of extract statement, column '%s' in '%s', which returns schema (%s), "
                + "is not of a valid type for extraction.  Please use a column of type Span or Text.",
            targetColName, targetView.toString(), targetViewSchema);

      }
    }
    // TODO: Finish verifying

    // Start out with the output schema of the items in the extract list
    // that come before the actual extraction.
    // Send null for the ConsolidateClause because computeSchema can't validate it yet.
    TupleSchema itemSchema = computeSchema(node.getExtractList().getSelectList(),
        new FromListItemNode[] {targetView}, null, null, null, null);

    // If we reach here then TupleSchema.toSchema() did not generate an exception which means that
    // the selectList has
    // a valid output schema. Attempt to generate a more explicit error message if the return clause
    // contains output
    // names that are already output by the select list
    // Figure out what are the output columns that the extraction produces.
    ArrayList<NickNode> extractOutputs = extractSpec.getOutputCols();

    // Augment the "starter" schema with the items from the extract spec
    String[] addlColNames = new String[extractOutputs.size()];
    FieldType[] addlColTypes = new FieldType[extractOutputs.size()];
    String[] duplicateColNames = new String[extractOutputs.size()];
    String returnCol;
    boolean foundDuplicate = false;

    for (int i = 0; i < addlColNames.length; i++) {
      returnCol = extractOutputs.get(i).getNickname();

      // Generate a more explicit error message if the return clause contains output names that are
      // already output by
      // the select list
      if (itemSchema.containsField(returnCol)) {
        duplicateColNames[i] = returnCol;
        foundDuplicate = true;
      }

      addlColNames[i] = returnCol;
    }

    // Generate a more explicit error message if the return clause contains output names that are
    // already output by
    // the select list
    if (foundDuplicate)
      throw new RuntimeException(String.format(
          "Duplicate column names %s in select list and extraction specification. "
              + "Select list columns are: %s. Extraction specification columns are: %s",
          Arrays.toString(duplicateColNames), Arrays.toString(itemSchema.getFieldNames()),
          Arrays.toString(addlColNames)));

    for (int i = 0; i < addlColTypes.length; i++) {
      addlColTypes[i] = FieldType.SPAN_TYPE;
    }

    // At this point, we have already validated that the select list and extract clause do not have
    // duplicate names
    // Therefore we will never see a cryptic error message of the form "Base schema already has
    // column '<colName>'."
    TupleSchema ret = new TupleSchema(itemSchema, addlColNames, addlColTypes);

    // Verify that the predicates in the having clause are valid and return Boolean values.
    validateHavingClause(ret, node.getHavingClause());

    // Verify that the consolidate clause is valid.
    validateConsolidateClause(ret, node.getConsolidateClause());

    return ret;
  }

  /**
   * Shared code for validating the HAVING clauses of extract and extract pattern statements.
   * 
   * @param havingInputSchema schema over which the HAVING clause is evaluated; generally, the
   *        HAVING clause operates over the output schema of the statement.
   * @param havingClause parse tree node for the entire HAVING clause, or null if there is no HAVING
   *        clause
   * @throws ParseException if an error is found while verifying that the contents of the HAVING
   *         clause are valid function calls that return Boolean values
   */
  protected void validateHavingClause(TupleSchema havingInputSchema, HavingClauseNode havingClause)
      throws ParseException {
    if (null != havingClause) {
      for (PredicateNode pred : havingClause.getPreds()) {
        FieldType predRetType = pred.getFunc().getType(catalog, havingInputSchema);

        if (false == FieldType.BOOL_TYPE.equals(predRetType)) {
          throw AQLParserBase.makeException(pred.getOrigTok(),
              "Expression '%s' in HAVING clause does not return a Boolean value.  Please use expressions that return true or false in the HAVING clause.",
              pred);
        }
      }
    }
  }

  /**
   * Shared code for validating the Consolidation target and the Priority target for the CONSOLIDATE
   * clauses of select, extract and extract pattern statements.
   * 
   * @param consolidateSchema schema over which the CONSOLIDATE clause is evaluated; generally, the
   *        clause operates over the output schema of the statement.
   * @param consolidateClause parse tree node for the entire CONSOLIDATE clause, or null if there is
   *        no Consolidate clause
   * @throws ParseException if an error is found while verifying that the contents of the
   *         CONSOLIDATE clause is a valid type
   */
  protected void validateConsolidateClause(TupleSchema consolidateSchema,
      ConsolidateClauseNode consolidateClause) throws ParseException {
    FieldType priorityType = null;
    RValueNode priority = null;
    FieldType targetType = null;
    RValueNode target = null;
    if (null != consolidateClause) {
      target = consolidateClause.getTarget();
      if (null != target) {
        targetType = target.getType(catalog, consolidateSchema);
        // RTC 112329 - Consolidation applies ONLY for Span/Text types
        // Imp: Allowing columns of Null type due to RTC 98966, 99489
        if (!targetType.getIsSpanOrText() && !targetType.getIsNullType()) {
          throw AQLParser.makeException(target.getOrigTok(),
              "Invalid type %s for target expression '%s"
                  + "'. Allowed types for the consolidate target are Span and Text only.",
              targetType, target);
        }
      }
      priority = consolidateClause.getPriorityTarget();
      if (null != priority) {
        priorityType = priority.getType(catalog, consolidateSchema);
        if (!priorityType.getIsText() && !priorityType.getIsNumericType()
            && !priorityType.getIsNullType()) {
          throw AQLParser.makeException(priority.getOrigTok(),
              "Invalid type %s for priority expression '%s" + "'", priorityType, priority);
        }
      }
    }
  }


  protected TupleSchema computeSchema(UnionAllNode node) throws ParseException {
    // Build up a list of schemas for the inputs.
    ArrayList<TupleSchema> schemas = new ArrayList<TupleSchema>();
    for (int i = 0; i < node.getNumStmts(); i++) {
      schemas.add(computeSchema(node.getStmt(i)));
    }

    TupleSchema firstSchema = schemas.get(0);

    // Check for union-compatibility
    for (int i = 1; i < node.getNumStmts(); i++) {
      TupleSchema curSchema = computeSchema(node.getStmt(i));

      if (false == firstSchema.unionCompatible(curSchema)) {
        throw new UnionCompatibilityException(node.getOrigTok(), firstSchema, curSchema, i);
      }
    }

    // Then merge together the input schemas, casting certain types (i.e. Text) to more general
    // types (i.e. Span) as
    // needed.
    return AbstractTupleSchema.generalize(schemas);
  }

  protected TupleSchema computeSchema(MinusNode node) throws ParseException {
    return computeSchema(node.getFirstStmt());
  }

  protected TupleSchema computeSchema(ExtractPatternNode node) throws ParseException {

    // Validate the WITH INLINE_MATCH clause
    String targetView = null, targetColName = null;
    Token errTok = null;
    ColNameNode targetCol = node.getTarget();
    boolean hasExplicitWithInlineMatchClause = (null == targetCol ? false : true);
    if (hasExplicitWithInlineMatchClause) {
      // If we get here, then we know the WITH INLINE_MATCH clause is present and it is of the form
      // <view_name>.<col_name> and the <view_name> exists (validation happens earlier, in {@link
      // ExtractPatternNode#validate()}
      targetView = targetCol.getTabname();
      targetColName = targetCol.getColnameInTable();
      errTok = targetCol.getColnameTok();
    } else {
      targetView = ExtractPatternNode.DEFAULT_WITH_INLINE_MATCH_VIEW_NAME;
      targetColName = ExtractPatternNode.DEFAULT_WITH_INLINE_MATCH_COL_NAME;
      // The with inline_match clause is not present; get the token of the pattern node instead
      errTok = node.getOrigTok();
    }

    // Compute the schema of the view in the with inline_match clause
    TupleSchema targetViewSchema = getSchemaFromViewName(targetView, errTok);

    // Verify the col name appears in the schema
    // Report different error, depending on whether the clause is present of not
    if (false == targetViewSchema.containsField(targetColName)) {

      if (hasExplicitWithInlineMatchClause) {
        throw AQLParserBase.makeException(errTok,
            "Column '%s' does not exist in schema of '%s', which is (%s).  "
                + "Check the spelling of the column name.",
            targetColName, targetView.toString(), targetViewSchema);
      } else {
        throw AQLParserBase.makeException(errTok,
            "In default 'with inline_match' specification, column '%s' does not exist in schema of '%s', which is (%s).  "
                + "Add an explicit 'with inline_match' specification.",
            targetColName, targetView.toString(), targetViewSchema);
      }
    }

    // Now verify the field is a span-producing type
    FieldType targetFieldType = targetViewSchema.getFieldTypeByName(targetColName);

    if (false == targetFieldType.getIsSpanOrText()) {

      if (hasExplicitWithInlineMatchClause) {
        throw AQLParserBase.makeException(errTok,
            "In 'with inline_match' specification, column '%s' in '%s', which returns schema (%s), "
                + "is not of a valid type.  Please use a column of type Span or Text.",
            targetColName, targetView.toString(), targetViewSchema);
      } else {
        throw AQLParserBase.makeException(errTok,
            "In default 'with inline_match' specification, column '%s' in '%s', which returns schema (%s), "
                + "is not of a valid type.  Please use a column of type Span or Text.",
            targetColName, targetView.toString(), targetViewSchema);
      }
    }

    // Start out with the output schema of the items in the extract list
    // that come before the actual extraction.
    TupleSchema itemSchema =
        computeSchema(node.getSelectList(), node.getFromList().toArray(), null, null, null, null);

    // If we reach here then TupleSchema.toSchema() did not generate an exception which means that
    // the selectList has
    // a valid output schema. Attempt to generate a more explicit error message if the return clause
    // contains output
    // names that are already output by the select list

    // Augment the "starter" schema with the items from the extract pattern spec
    int addlNumCols = node.getReturnClause().size();

    String[] addlColNames = new String[addlNumCols];
    String[] duplicateColNames = new String[addlNumCols];
    FieldType[] addlColTypes = new FieldType[addlNumCols];
    String returnCol;
    boolean foundDuplicate = false;

    for (int i = 0; i < addlNumCols; i++) {
      returnCol = node.getReturnClause().getName(i);

      // Remember the duplicate col names
      if (itemSchema.containsField(returnCol)) {
        duplicateColNames[i] = returnCol;
        foundDuplicate = true;
      }

      addlColNames[i] = returnCol;
    }

    // Generate a more explicit error message if the return clause contains output names that are
    // already output by
    // the select list
    if (foundDuplicate)
      throw new RuntimeException(String.format(
          "Duplicate column names %s in select list and extraction specification. "
              + "Select list columns are: %s. Extraction specification columns are: %s",
          Arrays.toString(duplicateColNames), Arrays.toString(itemSchema.getFieldNames()),
          Arrays.toString(addlColNames)));

    for (int i = 0; i < addlNumCols; i++) {
      addlColTypes[i] = FieldType.SPAN_TYPE;
    }

    // At this point, we have already validated that the select list and extract clause do not have
    // duplicate names
    // Therefore we will never see a cryptic error message of the form "Base schema already has
    // column '<colName>'."
    TupleSchema ret = new TupleSchema(itemSchema, addlColNames, addlColTypes);

    // Make sure that the contents of the HAVING clause are correct before returning the schema
    validateHavingClause(ret, node.getHavingClause());

    // Verify that the consolidate clause is valid.
    validateConsolidateClause(ret, node.getConsolidateClause());

    return ret;
  }

  /**
   * Compute the output schema of a SELECT statement or of the "non-extraction" part of an EXTRACT
   * statement
   * 
   * @param selectList parse tree node for the select/extract list in the stmt
   * @param fromListItems parse tree node for the FROM list
   * @param whereClause parse tree node for the WHERE clause, or null if there is none
   * @param orderByClause parse tree node for the ORDER BY clause, or null
   * @param groupByClause parse tree node for the GROUP BY clause, or null
   * @param consolidateClauseNode consolidate clause node for the SELECT statement or null if there
   *        is none or if the consolidate clause is checked elsewhere
   * @return schema of the tuples constructed by the select list, as inferred from the names given
   *         in the from list.
   * @throws ParseException
   */
  private TupleSchema computeSchema(SelectListNode selectList, FromListItemNode[] fromListItems,
      WhereClauseNode whereClause, OrderByClauseNode orderByClause, GroupByClauseNode groupByClause,
      ConsolidateClauseNode consolidateClause) throws ParseException {
    // Compute the schema for the join of all the relations in the from list.
    // Use fully-qualified names in the context of the current view.
    // For example, if the FROM list was "from View1 V1, View1 V2, View2 W", and if View1 and View2
    // had one column
    // each called "match", then the schema would be:
    // (V1.match Span, V2.match Span, W.match Span)
    ArrayList<String> fromListNames = new ArrayList<String>();
    ArrayList<FieldType> fromListTypes = new ArrayList<FieldType>();

    for (int i = 0; i < fromListItems.length; i++) {
      FromListItemNode fromItem = fromListItems[i];

      TupleSchema itemSchema = computeSchema(fromItem);

      // Rename the columns in the returned schema to their fully qualified names within the context
      // of the current
      // SELECT statement.
      String aliasStr = fromItem.getAlias().getNickname();

      for (int j = 0; j < itemSchema.size(); j++) {
        String origColName = itemSchema.getFieldNameByIx(j);
        fromListNames.add(aliasStr + "." + origColName);
        fromListTypes.add(itemSchema.getFieldTypeByIx(j));
      }
    }

    // Constructor for TupleSchema expects arrays
    String[] fromListNamesArr = new String[fromListNames.size()];
    FieldType[] fromListTypesArr = new FieldType[fromListTypes.size()];
    fromListNamesArr = fromListNames.toArray(fromListNamesArr);
    fromListTypesArr = fromListTypes.toArray(fromListTypesArr);
    TupleSchema joinedSchema = new TupleSchema(fromListNamesArr, fromListTypesArr);

    validateSchema(fromListItems[0].getOrigTok(), "Unified schema of from list", joinedSchema);

    // Prepare the arrays we will use to construct the schema.
    String[] colNames = getColNames(selectList);
    int numCols = colNames.length;
    FieldType[] colTypes = new FieldType[numCols];

    // Iterate over the select list
    for (int i = 0; i < numCols; i++) {
      SelectListItemNode selListItemNode = selectList.get(i);

      RValueNode rval = selListItemNode.getValue();

      colTypes[i] = rval.getType(catalog, joinedSchema);

      if (FieldType.UNKNOWN_TYPE.equals(colTypes[i])) {
        // SPECIAL CASE: The RValue returned "Unknown" for its type.
        // Check for the one case where this problem is not due to a bug in type inference: The use
        // of NULL without a
        // cast.
        if ((rval instanceof ScalarFnCallNode)
            && NullConst.class.getSimpleName().equals(((ScalarFnCallNode) rval).getFuncName())) {
          // Null constant at the root of the function call tree
          throw AQLParserBase.makeException(rval.getOrigTok(),
              "Cannot determine type of null constant without cast.  "
                  + "Please use the 'cast' operator to cast the null constant to an appropriate type.");
        } else {
          // All other cases indicate a bug in type inference.
          throw new FatalInternalError("Type inference returns Unknown for type of expression '%s'",
              rval);
        }
        // END SPECIAL CASE
      }

      // if (FieldType.STRING_TYPE.equals (colTypes[i])) {
      // // SPECIAL CASE: The operators that evaluate scalar functions (and constant expressions)
      // will automatically
      // // wrap any returned string values in Text for compatibility with downstream code. Correct
      // the inferred type
      // // accordingly.
      // colTypes[i] = FieldType.TEXT_TYPE;
      // // END SPECIAL CASE
      // }
    }

    // Make sure that everything in the where clause returns a boolean value
    if (null != whereClause) {
      for (PredicateNode pred : whereClause.getPreds()) {
        FieldType predRetType = pred.getFunc().getType(catalog, joinedSchema);

        if (false == FieldType.BOOL_TYPE.equals(predRetType)) {
          throw AQLParserBase.makeException(pred.getOrigTok(),
              "Expression '%s' in WHERE clause does not return a Boolean value.  Please use expressions that return true or false in the WHERE clause.",
              pred);
        }
      }
    }

    // Make sure that everything in the order by clause is a valid rvalue.
    if (null != orderByClause) {
      for (RValueNode rval : orderByClause.getValues()) {
        rval.getType(catalog, joinedSchema);
      }
    }

    // Make sure that everything in the group by clause is a valid rvalue.
    if (null != groupByClause) {
      for (RValueNode rval : groupByClause.getValues()) {
        rval.getType(catalog, joinedSchema);
      }
    }

    TupleSchema ret = new TupleSchema(colNames, colTypes);
    // Check the types in the consolidate priority clause

    validateConsolidateClause(joinedSchema, consolidateClause);

    return ret;
  }

  /**
   * @param target item in the FROM list of a SELECT or EXTRACT statement
   * @return the raw schema of the item, in the context of the original relation
   * @throws ParseException
   */
  private TupleSchema computeSchema(FromListItemNode target) throws ParseException {

    // There are several kinds of from list item; handle each separately.
    TupleSchema ret;
    if (target instanceof FromListItemViewRefNode) {
      FromListItemViewRefNode viewRefNode = (FromListItemViewRefNode) target;
      ret = getSchemaForViewRef(viewRefNode);
    } else if (target instanceof FromListItemTableFuncNode) {
      // This item in the from list is a table function call.
      FromListItemTableFuncNode tabFuncNode = (FromListItemTableFuncNode) target;

      ret = computeSchema(tabFuncNode);
    } else if (target instanceof FromListItemSubqueryNode) {
      // Subquery that hasn't been rewritten; we should only hit this case if SchemaInferrer is
      // being called from within
      // another rewrite (i.e. the provenance rewrite)
      FromListItemSubqueryNode sqn = (FromListItemSubqueryNode) target;
      ret = computeSchema(sqn.getBody());
    } else {
      throw new FatalInternalError("Don't know how to determine the schema of %s (type %s)", target,
          target.getClass().getName());
    }

    // Validate the schema before returning it.
    SchemaInferrer.validateSchema(target.getOrigTok(), target.getScopedName(), ret);

    return ret;
  }

  protected TupleSchema computeSchema(FromListItemTableFuncNode tabFuncNode)
      throws TableFunctionNotFoundException, ParseException {
    // Retrieve information about the table function from the AQL catalog.
    TableFnCallNode fnCall = tabFuncNode.getTabfunc();
    String key = tabFuncNode.getTabfunc().getFuncName();
    TableFuncCatalogEntry ce = catalog.lookupTableFunc(key);
    if (null == ce) {
      throw new TableFunctionNotFoundException(fnCall.getOrigTok(), key);
    }

    // Validate arguments to the table function call.
    TupleSchema argSchema = ce.getArgumentSchema();
    ArrayList<RValueNode> args = fnCall.getArgs();
    for (int i = 0; i < args.size(); i++) {
      RValueNode arg = args.get(i);

      FieldType inferredType;
      if (arg instanceof NickNode) {
        // Record locator argument.
        String viewName = ((NickNode) arg).getNickname();
        TupleSchema locatorSchema = getSchemaFromViewName(viewName, arg.getOrigTok());
        inferredType = new FieldType(locatorSchema, true);
      } else {
        // Scalar argument
        // Note empty schema argument; table functions are called from the from clause, and their
        // arguments can't
        // reference any columns of incoming tuples
        inferredType = arg.getType(catalog, TupleSchema.EMPTY_SCHEMA);
      }
      FieldType expectedType = argSchema.getFieldTypeByIx(i);

      if (false == expectedType.accepts(inferredType)) {
        throw AQLParser.makeException(fnCall.getOrigTok(),
            "Argument %d of %s function, %s, returns %s instead of expected type %s.  Usage: %s%s",
            i + 1, key, args.get(i), inferredType, expectedType, key, argSchema);

      }
    }

    // Ask the table function's implementing class to validate the returned schema.
    // The following code has been moved earlier in compilation, so that functions that are not
    // called anywhere are
    // still validated.
    // List<ParseException> errors = ce.validateReturnedSchema (catalog);
    // if (errors.size () > 0) {
    // // Just keep the first error for SchemaInferrer's purposes. Upstream validation code should
    // catch the rest.
    // throw errors.get (0);
    // }

    return ce.getReturnedSchema();
  }

  /*
   * UTILITY METHODS Please keep internal utility methods below this line.
   */

  /**
   * A utility method that creates a TupleSchema objects from an array list of column names and
   * column types.
   * 
   * @param colNamesList list of parse tree nodes for column names
   * @param colTypesList list of parse tree nodes for column type names
   * @return TupleSchema
   * @throws ParseException if there is a problem computing the schema
   */
  private TupleSchema createTupleSchema(ArrayList<NickNode> colNamesList,
      ArrayList<NickNode> colTypesList) throws ParseException {
    int size = colNamesList.size();
    String[] colNames = new String[size];
    FieldType[] colTypes = new FieldType[size];

    for (int i = 0; i < size; ++i) {
      colNames[i] = colNamesList.get(i).getNickname();
    }

    for (int i = 0; i < size; ++i) {
      colTypes[i] = FieldType.stringToFieldType(colTypesList.get(i).getNickname());
    }

    return new TupleSchema(colNames, colTypes);
  }

  /**
   * Infer the names of the elements of the select list within the scope of a given SELECT
   * statement, dereferencing any aliases given in the select list.
   * 
   * @param selectList parse tree node for the select list
   * @return array of canonical names, scoped to the SELECT statement
   * @throws ParseException
   */
  private String[] getColNames(SelectListNode selectList) throws ParseException {
    int numCols = selectList.size();

    String[] colNames = new String[numCols];

    // Pull output column names from the select list.
    for (int i = 0; i < numCols; i++) {
      SelectListItemNode item = selectList.get(i);

      colNames[i] = item.getAlias();

      if (null == colNames[i]) {
        // No alias information; this usually happens because of select * when the target in the
        // from list can't be
        // compiled.
        ParseException pe = new ParseException(String
            .format("Cannot determine name of column %d of %d in select list", i + 1, numCols));
        RValueNode value = selectList.get(i).getValue();
        pe.currentToken = value.getOrigTok();

        throw pe;
      }

    }

    return colNames;

  }

  /**
   * @param viewRefNode parse tree node for a reference to a view in a FROM clause
   * @return the schema of the target view
   */
  protected TupleSchema getSchemaForViewRef(FromListItemViewRefNode viewRefNode)
      throws ParseException {
    String name = viewRefNode.getViewName().getNickname();
    return getSchemaFromViewName(name, viewRefNode.getOrigTok());
  }

  /**
   * @param name name of a view in the catalog
   * @param errLoc location at which to report any lookup errors
   * @return the schema of the target view
   */
  protected TupleSchema getSchemaFromViewName(String name, Token errLoc) throws ParseException {
    CatalogEntry ce = catalog.lookupView(name, errLoc);

    TupleSchema targetSchema = null;
    if (ce instanceof AbstractRelationCatalogEntry) {
      AbstractRelationCatalogEntry ace = ((AbstractRelationCatalogEntry) ce);
      targetSchema = ace.getSchema();

      if (null == targetSchema && ce instanceof ViewCatalogEntry) {
        // Catalog has no schema information for target of from list item. Assume that the caller
        // knows what they're
        // doing and compute the schema recursively.
        ViewCatalogEntry vce = (ViewCatalogEntry) ce;
        targetSchema = computeSchema(vce.getParseTreeNode());
      }

      // Check whether the schema contains fields of UNKNOWN type and issue an appropriate error
      // message
      checkForUnknownCols(name, ace);

    } else if (ce instanceof DetagCatalogEntry) {
      // if a view depends on a detag node, then the detag node's schema is not computed yet. So,
      // compute now.
      targetSchema = ((DetagCatalogEntry) ce).getDetagSchema();
      if (targetSchema == null) {
        DetagCatalogEntry dce = (DetagCatalogEntry) ce;
        try {
          targetSchema = computeSchema(dce);
          dce.setDetagSchema(targetSchema);
        } catch (Exception e) {
          throw new ParseException(e.getMessage());
        }

      }
    } else {// throw error if ce instanceof any other CatalogEntry class
      throw AQLParserBase.makeException(errLoc,
          "Valid values in from list are: [view, table, external view, detag]. Received %s instead",
          ce.getClass().getName());
    }

    if (TYPE_INFERENCE_ERROR_SCHEMA == targetSchema) {
      throw AQLParserBase.makeException(errLoc, "Error determining schema of '%s'", name);
    } else if (null == targetSchema) {
      // This cannot ever happen since we attempt to compute the schema above, and the first time
      // type inference is
      // attempted it will either populate the schema or mark it as an error schema. However, cover
      // all our bases just
      // in case
      throw new FatalInternalError(
          "Catalog has null schema pointer for view or table '%s' (entry type %s).  "
              + "This usually means that type inference is not correctly topologically sorting input views.",
          name, ce.getClass().getName());
    }

    return targetSchema;
  }

  /**
   * Subroutine of {@link #getSchemaFromViewName(String, Token)}. Checks for missing type
   * information in the returned schema.
   * 
   * @param name name of the view or table that was the target of the lookup
   * @param ace catalog entry associated with this schema
   * @throws ParseException if there is missing type information because the target view/table was
   *         imported from a module compiled with an older version of BigInsights
   */
  protected void checkForUnknownCols(String name, AbstractRelationCatalogEntry ace)
      throws ParseException {
    TupleSchema targetSchema = ace.getSchema();

    ArrayList<String> unknownColNames = new ArrayList<String>();
    for (int i = 0; i < targetSchema.size(); i++) {
      FieldType ft = targetSchema.getFieldTypeByIx(i);
      if (FieldType.UNKNOWN_TYPE.equals(ft)) {
        unknownColNames.add(targetSchema.getFieldNameByIx(i));

      }
    }

    if (unknownColNames.size() > 0) {
      // Generate the appropriate error message depending on whether the view was imported or not.
      TopLevelParseTreeNode node = ace.getParseTreeNode();
      if (node instanceof AbstractImportNode) {
        // Imported view, table, etc. -- compiled module must not contain valid type information
        String importType = null;
        if (node instanceof ImportViewNode) {
          importType = "view";
        } else if (node instanceof ImportTableNode) {
          importType = "table";
        } else {
          throw new FatalInternalError(
              "Error while generating error message: Type of imported element '%s' is not known",
              ((AbstractImportNode) node).getNodeName());
        }

        throw AQLParserBase.makeException(node.getOrigTok(),
            "Schema information for imported %s '%s' is missing type information "
                + "for the following columns: %s.  "
                + "Please recompile the %s with the latest version of BigInsights Text Analytics.",
            importType, ((AbstractImportNode) node).getNodeName(), unknownColNames.toString(),
            importType);
      } else {
        // Non-imported view or table; the fact that any columns are missing types indicates a
        // failure of type
        // inference
        throw new FatalInternalError("Type inference produced type 'Unknown' for columns %s of %s",
            unknownColNames, name);
      }
    }
  }

  /**
   * Run a selection of sanity checks against a schema produced by type inference.
   * 
   * @param origTok token location where errors should be reported
   * @param entityName human-readable name of the entity for which this schema should be the schema
   * @param schema a schema that the type inference code in this class has produced
   */
  protected static void validateSchema(Token origTok, String entityName, TupleSchema schema)
      throws ParseException {
    // Check whether any of the types of the fields are not known.
    // The deprecated UNKNOWN type can appear when compiling with modules from older versions of
    // SystemT
    // on the module path (type inference did not detect some output types prior to the Q4 2013
    // release)
    // Throw an exception if this occurs.
    // ------BEGIN-CODE--------------------------
    for (int i = 0; i < schema.size(); i++) {
      FieldType ft = schema.getFieldTypeByIx(i);
      if (FieldType.UNKNOWN_TYPE.equals(ft)) {
        throw AQLParserBase.makeException(origTok, "Field %d of inferred schema for %s is unknown",
            i, entityName);
      }
    }
    // ------END-CODE----------------------------

    // Other validations go here.

  }

}
