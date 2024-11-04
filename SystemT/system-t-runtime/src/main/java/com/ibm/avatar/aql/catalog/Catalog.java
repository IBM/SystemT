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
package com.ibm.avatar.aql.catalog;

import java.io.ByteArrayOutputStream;
import java.io.CharArrayWriter;
import java.io.File;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.xml.bind.JAXBException;

import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.algebra.exceptions.CatalogEntryNotFoundException;
import com.ibm.avatar.algebra.function.agg.Avg;
import com.ibm.avatar.algebra.function.agg.Count;
import com.ibm.avatar.algebra.function.agg.List;
import com.ibm.avatar.algebra.function.agg.Max;
import com.ibm.avatar.algebra.function.agg.Min;
import com.ibm.avatar.algebra.function.agg.Sum;
import com.ibm.avatar.algebra.function.base.AggFunc;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.algebra.function.base.ScalarUDF;
import com.ibm.avatar.algebra.function.base.UDFPredicateWrapper;
import com.ibm.avatar.algebra.function.predicate.And;
import com.ibm.avatar.algebra.function.predicate.ContainedWithin;
import com.ibm.avatar.algebra.function.predicate.Contains;
import com.ibm.avatar.algebra.function.predicate.ContainsDict;
import com.ibm.avatar.algebra.function.predicate.ContainsDicts;
import com.ibm.avatar.algebra.function.predicate.ContainsRegex;
import com.ibm.avatar.algebra.function.predicate.ContainsText;
import com.ibm.avatar.algebra.function.predicate.Equals;
import com.ibm.avatar.algebra.function.predicate.FollowedBy;
import com.ibm.avatar.algebra.function.predicate.FollowedByTok;
import com.ibm.avatar.algebra.function.predicate.Follows;
import com.ibm.avatar.algebra.function.predicate.FollowsTok;
import com.ibm.avatar.algebra.function.predicate.GreaterThan;
import com.ibm.avatar.algebra.function.predicate.IsNull;
import com.ibm.avatar.algebra.function.predicate.ListContains;
import com.ibm.avatar.algebra.function.predicate.MatchesDict;
import com.ibm.avatar.algebra.function.predicate.MatchesRegex;
import com.ibm.avatar.algebra.function.predicate.Not;
import com.ibm.avatar.algebra.function.predicate.NotNull;
import com.ibm.avatar.algebra.function.predicate.OnWordBoundaries;
import com.ibm.avatar.algebra.function.predicate.Or;
import com.ibm.avatar.algebra.function.predicate.Overlaps;
import com.ibm.avatar.algebra.function.predicate.True;
import com.ibm.avatar.algebra.function.scalar.AutoID;
import com.ibm.avatar.algebra.function.scalar.BoolConst;
import com.ibm.avatar.algebra.function.scalar.Case;
import com.ibm.avatar.algebra.function.scalar.Cast;
import com.ibm.avatar.algebra.function.scalar.Chomp;
import com.ibm.avatar.algebra.function.scalar.CombineSpans;
import com.ibm.avatar.algebra.function.scalar.Deduplicate;
import com.ibm.avatar.algebra.function.scalar.FloatConst;
import com.ibm.avatar.algebra.function.scalar.FunctionInvocationTracker;
import com.ibm.avatar.algebra.function.scalar.GetBegin;
import com.ibm.avatar.algebra.function.scalar.GetCol;
import com.ibm.avatar.algebra.function.scalar.GetDocText;
import com.ibm.avatar.algebra.function.scalar.GetEnd;
import com.ibm.avatar.algebra.function.scalar.GetLanguage;
import com.ibm.avatar.algebra.function.scalar.GetLemma;
import com.ibm.avatar.algebra.function.scalar.GetLength;
import com.ibm.avatar.algebra.function.scalar.GetLengthTok;
import com.ibm.avatar.algebra.function.scalar.GetString;
import com.ibm.avatar.algebra.function.scalar.GetText;
import com.ibm.avatar.algebra.function.scalar.HashCode;
import com.ibm.avatar.algebra.function.scalar.IntConst;
import com.ibm.avatar.algebra.function.scalar.LeftContext;
import com.ibm.avatar.algebra.function.scalar.LeftContextTok;
import com.ibm.avatar.algebra.function.scalar.ListToString;
import com.ibm.avatar.algebra.function.scalar.NullConst;
import com.ibm.avatar.algebra.function.scalar.RegexConst;
import com.ibm.avatar.algebra.function.scalar.Remap;
import com.ibm.avatar.algebra.function.scalar.RemoveNulls;
import com.ibm.avatar.algebra.function.scalar.RightContext;
import com.ibm.avatar.algebra.function.scalar.RightContextTok;
import com.ibm.avatar.algebra.function.scalar.SpanBetween;
import com.ibm.avatar.algebra.function.scalar.SpanIntersection;
import com.ibm.avatar.algebra.function.scalar.StringConst;
import com.ibm.avatar.algebra.function.scalar.SubSpanTok;
import com.ibm.avatar.algebra.function.scalar.ToLowerCase;
import com.ibm.avatar.algebra.function.scalar.ToString;
import com.ibm.avatar.algebra.function.scalar.Xor;
import com.ibm.avatar.algebra.util.dict.DictParams;
import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.algebra.util.file.SearchPath;
import com.ibm.avatar.algebra.util.lang.LangCode;
import com.ibm.avatar.algebra.util.pmml.PMMLUtil;
import com.ibm.avatar.algebra.util.table.TableParams;
import com.ibm.avatar.algebra.util.tokenize.Tokenizer;
import com.ibm.avatar.algebra.util.udf.UDFParams;
import com.ibm.avatar.api.CompilationSummary;
import com.ibm.avatar.api.CompilationSummaryImpl;
import com.ibm.avatar.api.CompileAQLParams;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.exceptions.CatalogMergeError;
import com.ibm.avatar.api.exceptions.CompilerException;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.api.exceptions.ModuleNotFoundException;
import com.ibm.avatar.api.exceptions.VerboseNullPointerException;
import com.ibm.avatar.api.tam.DictionaryMetadata;
import com.ibm.avatar.api.tam.FunctionMetadata;
import com.ibm.avatar.api.tam.ModuleMetadata;
import com.ibm.avatar.api.tam.TableMetadata;
import com.ibm.avatar.api.tam.ViewMetadata;
import com.ibm.avatar.aql.AQLParseTreeNode;
import com.ibm.avatar.aql.AQLParser;
import com.ibm.avatar.aql.AQLParserBase;
import com.ibm.avatar.aql.AbstractExportNode;
import com.ibm.avatar.aql.AbstractImportNode;
import com.ibm.avatar.aql.CreateDictNode;
import com.ibm.avatar.aql.CreateExternalViewNode;
import com.ibm.avatar.aql.CreateFunctionNode;
import com.ibm.avatar.aql.CreateTableNode;
import com.ibm.avatar.aql.CreateViewNode;
import com.ibm.avatar.aql.DetagDocNode;
import com.ibm.avatar.aql.DetagDocSpecNode;
import com.ibm.avatar.aql.ExportDictNode;
import com.ibm.avatar.aql.ExportFuncNode;
import com.ibm.avatar.aql.ExportTableNode;
import com.ibm.avatar.aql.ExportViewNode;
import com.ibm.avatar.aql.ExtendedParseException;
import com.ibm.avatar.aql.FromListItemNode;
import com.ibm.avatar.aql.FromListItemSubqueryNode;
import com.ibm.avatar.aql.FromListItemTableFuncNode;
import com.ibm.avatar.aql.FromListItemViewRefNode;
import com.ibm.avatar.aql.FuncLanguage;
import com.ibm.avatar.aql.ImportDictNode;
import com.ibm.avatar.aql.ImportFuncNode;
import com.ibm.avatar.aql.ImportModuleNode;
import com.ibm.avatar.aql.ImportNode;
import com.ibm.avatar.aql.ImportTableNode;
import com.ibm.avatar.aql.ImportViewNode;
import com.ibm.avatar.aql.NickNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.RequireColumnsNode;
import com.ibm.avatar.aql.SetDefaultDictLangNode;
import com.ibm.avatar.aql.TableFnCallNode;
import com.ibm.avatar.aql.Token;
import com.ibm.avatar.aql.ViewBodyNode;
import com.ibm.avatar.aql.compiler.CompilerWarning;
import com.ibm.avatar.aql.compiler.ParseToCatalog;
import com.ibm.avatar.aql.doc.AQLDocComment;
import com.ibm.avatar.aql.planner.CostRecord;
import com.ibm.avatar.aql.planner.PlanNode;
import com.ibm.avatar.aql.planner.RequireDocumentPlanNode;
import com.ibm.avatar.aql.planner.SchemaInferrer;
import com.ibm.avatar.aql.planner.ViewNode;
import com.ibm.avatar.aql.tam.CompileTimeModuleResolver;
import com.ibm.avatar.aql.tam.ModuleUtils;
import com.ibm.avatar.logging.Log;

/**
 * Base class for AQL catalogs.
 * 
 */
@SuppressWarnings("deprecation")
public class Catalog {

  public static boolean debugDict = false;

  public static boolean debugReq = false;

  public static boolean debugExtView = false;

  public static boolean debugFunc = false;

  /**
   * Name of the former "built-in" document view -- this is now restricted from use.
   */
  public static final String BUILT_IN_DOC_TYPE_NAME = "DocScan";

  /** AQL view names that map to the document. */
  // public static final String[] DOCUMENT_TYPE_NAMES = { "Document", BUILT_IN_DOC_TYPE_NAME };
  public static final String[] DOCUMENT_TYPE_NAMES = {Constants.DEFAULT_DOC_TYPE_NAME};

  /**
   * Types of catalog entries that can be stored in the nameToentry map, along with an associated
   * text string for printing.
   */
  protected enum CatalogEntryTypeName {
    TABLE("Table"), VIEW("View"), EXTERNAL_VIEW("External view"), DETAG_VIEW(
        "Detag statement output"), FUNCTION(
            "Function"), DICTIONARY("Dictionary"), UNKNOWN("Unknown element"), JAR_FILE("Jar file");

    private String text;

    private CatalogEntryTypeName(String text) {
      this.text = text;
    }

    public String getText() {
      return text;
    }
  }

  /** Planner node for document that contains the unified document schema */
  protected RequireDocumentPlanNode documentPlan;

  /**
   * Module comment read (unaltered) from the {@link AQLDocComment#MODULE_AQL_DOC_COMMENT_FILE_NAME}
   * file in the module directory
   */
  protected AQLDocComment comment;

  /**
   * Name of the module being compiled.
   */
  protected String moduleName;

  /**
   * Table of namespace for views, functions Internally uses TreeMaps to ensure consistent order for
   * regression tests.
   */
  protected NamespaceTable<CatalogEntry> viewTableNamespace = new NamespaceTable<CatalogEntry>();

  /**
   * Catalog entries for all tables, indexed by local and fully-qualified names.
   */
  protected NamespaceTable<TableCatalogEntry> tableNamespace =
      new NamespaceTable<TableCatalogEntry>();

  /**
   * Catalog entries for all functions, indexed by local and fully-qualified names. By convention,
   * all names, including the fully qualified name (if appropriate) of the function are included in
   * this namespace as aliases. No one should ever be calling getEntryFromCanonicalName() on this
   * object!
   */
  protected NamespaceTable<AbstractFuncCatalogEntry> functionNamespace =
      new NamespaceTable<AbstractFuncCatalogEntry>();

  /**
   * Catalog entries for all dictionaries, indexed by local and fully-qualified names.
   */
  protected NamespaceTable<DictCatalogEntry> dictNamespace = new NamespaceTable<DictCatalogEntry>();

  /**
   * Catalog entries for jar files, indexed by local and (eventually) fully-qualified names.
   */
  protected NamespaceTable<AbstractJarCatalogEntry> jarNamespace =
      new NamespaceTable<AbstractJarCatalogEntry>();

  /** Table of require statements by filename. */
  protected TreeMap<String, CatalogEntry> requireStmts = new TreeMap<String, CatalogEntry>();

  /**
   * Map of modules to their corresponding metadata, used for retrieving an imported view's cost
   * record, and whether a module contains an imported artifact.
   */
  protected HashMap<String, ModuleMetadata> nameToMetadata = new HashMap<String, ModuleMetadata>();

  /**
   * Maintains a list of modules that the current module depends on. Any module name reference
   * encountered through the following statements are added to this list: <br>
   * <ol>
   * <li>import module &lt;moduleName&gt;</li>
   * <li>import view|table|dictionary|function &lt;elementName&gt; from module
   * &lt;moduleName&gt;</li>
   * </ol>
   */
  protected ArrayList<String> dependsOnList = new ArrayList<String>();

  /**
   * Maintains a list of modules that are imported through the 'import module' statement. This list
   * is used to determine duplicate 'import module' statements.
   */
  protected ArrayList<String> importedModules = new ArrayList<String>();

  /**
   * Sorted order of output views. Originally was in order of specification, but with addition of
   * modular AQL, that is no longer meaningful. Now the order is lexicographically sorted via
   * TreeSet for cross-platform consistency. First element of each pair is the view name, second
   * element is the output name.
   */
  private TreeSet<Pair<String, String>> outputNameOrder = new TreeSet<Pair<String, String>>();

  /**
   * List of exported Views and Tables
   */
  private ArrayList<String> exportedViewsAndTables = new ArrayList<String>();

  /**
   * List of exported functions
   */
  private ArrayList<String> exportedFunctions = new ArrayList<String>();

  /**
   * List of exported dictionaries.
   */
  private ArrayList<String> exportedDicts = new ArrayList<String>();

  /**
   * Table of query plans, arranged by view name. Filled in by the top-level loop of the planner as
   * it compiles the AQL statement; read by the optimizer implementation while costing later SELECT
   * statements.
   */
  private HashMap<String, PlanNode> nameToPlan = new HashMap<String, PlanNode>();

  /**
   * Directories for finding auxiliary dictionary files.
   */
  protected SearchPath dictsPath = new SearchPath(".");

  /**
   * Directories for finding udf jar paths
   */
  protected SearchPath udfJarPath = new SearchPath(".");

  /**
   * A semicolon-separated list of URIs that refer to absolute paths to locations where dependent
   * modules can be located.
   */
  // do not default modulePath to any value. Let the user set it.
  protected String modulePath = null;

  /**
   * Absolute URI of the temporary directory where .tam files are written out to during compilation,
   * before they are copied to outputURI. Consider two source input modules A and B, where B depends
   * on A. Assume the module path contains an old version of A.tam. When compiling B, the compiler
   * should read the module metadata of module A from the newly compiled A.tam instead of from the
   * older version of A.tam found in modulePath.
   */
  protected String compilationTempDir = null;

  /**
   * Mapping between the name of the AQL file (relative to the module directory) and ParseTreeNodes
   * in that file, for each AQL file of the module. Needed for the provenance rewrite.
   */
  private Map<String, java.util.List<AQLParseTreeNode>> file2Nodes;

  /**
   * Contains the list of parse tree nodes of all views that are required for compilation, in the
   * order in which they should be compiled (i.e., in topological sorted order according to view
   * dependencies, starting with views with no dependencies); It is first initialized with the views
   * output by the parser, and updated during preprocessing stages (specifically, inlining
   * subqueries and sequence pattern rewrite for now).
   */
  private ArrayList<CreateViewNode> sortedRequiredViews;

  /**
   * Flag to turn on debug messages
   */
  private static final boolean debug = false;

  public void setDictsPath(String pathStr) {
    this.dictsPath = new SearchPath(pathStr);
  }

  public void setUDFJarPath(String pathStr) {
    this.udfJarPath = new SearchPath(pathStr);
  }

  public SearchPath getDictsPath() {
    return dictsPath;
  }

  public SearchPath getUDFJarPath() {
    return udfJarPath;
  }

  /**
   * Main constructor. Sets up a catalog with built-in functions pre-populated.
   */
  public Catalog() {

    // Populate the scalar functions list with all the built-in scalar
    // functions.
    // NOTE: KEEP THESE IN ALPHABETICAL ORDER
    addBuiltinFunc(AutoID.class);
    addBuiltinFunc(BoolConst.class);
    addBuiltinFunc(Case.class);
    addBuiltinFunc(Cast.class);
    addBuiltinFunc(Chomp.class);
    addBuiltinFunc(CombineSpans.class);
    addBuiltinFunc(Deduplicate.class);
    addBuiltinFunc(FloatConst.class);
    addBuiltinFunc(FunctionInvocationTracker.class);
    addBuiltinFunc(GetBegin.class);
    addBuiltinFunc(GetCol.class);
    addBuiltinFunc(GetDocText.class);
    addBuiltinFunc(GetEnd.class);
    addBuiltinFunc(GetLanguage.class);
    addBuiltinFunc(GetLength.class);
    addBuiltinFunc(GetLengthTok.class);
    addBuiltinFunc(GetString.class);
    addBuiltinFunc(GetLemma.class);
    addBuiltinFunc(GetText.class);
    addBuiltinFunc(HashCode.class);
    addBuiltinFunc(IntConst.class);
    addBuiltinFunc(LeftContext.class);
    addBuiltinFunc(LeftContextTok.class);
    addBuiltinFunc(ListToString.class);
    addBuiltinFunc(NullConst.class);
    // addBuiltinFunc (NullSpan.class);
    addBuiltinFunc(RegexConst.class);
    addBuiltinFunc(Remap.class);
    addBuiltinFunc(RemoveNulls.class);
    addBuiltinFunc(RightContext.class);
    addBuiltinFunc(RightContextTok.class);
    addBuiltinFunc(SpanBetween.class);
    addBuiltinFunc(SpanIntersection.class);
    addBuiltinFunc(StringConst.class);
    addBuiltinFunc(SubSpanTok.class);
    addBuiltinFunc(ToLowerCase.class);
    addBuiltinFunc(ToString.class);
    addBuiltinFunc(Xor.class);

    // Add information about built-in predicates
    // NOTE: KEEP IN ALPHABETICAL ORDER
    addBuiltinFunc(And.class);
    addBuiltinFunc(ContainedWithin.class);
    addBuiltinFunc(Contains.class);
    addBuiltinFunc(ContainsDict.class);
    addBuiltinFunc(ContainsDicts.class);
    addBuiltinFunc(ContainsRegex.class);
    // addBuiltinFunc(ContainsSentenceBoundary.class);
    addBuiltinFunc(ContainsText.class);
    addBuiltinFunc(Equals.class);
    addBuiltinFunc(FollowedBy.class);
    addBuiltinFunc(FollowedByTok.class);
    addBuiltinFunc(Follows.class);
    addBuiltinFunc(FollowsTok.class);
    addBuiltinFunc(GreaterThan.class);
    addBuiltinFunc(IsNull.class);
    addBuiltinFunc(ListContains.class);
    addBuiltinFunc(MatchesDict.class);
    addBuiltinFunc(MatchesRegex.class);
    addBuiltinFunc(Not.class);
    addBuiltinFunc(NotNull.class);
    addBuiltinFunc(OnWordBoundaries.class);
    addBuiltinFunc(Or.class);
    addBuiltinFunc(Overlaps.class);
    addBuiltinFunc(True.class);

    // Populate the aggregate functions list with all the built-in aggregate
    // functions.
    // NOTE: KEEP THESE IN ALPHABETICAL ORDER
    addBuiltinAggFunc(Avg.class);
    addBuiltinAggFunc(Count.class);
    addBuiltinAggFunc(List.class);
    addBuiltinAggFunc(Max.class);
    addBuiltinAggFunc(Min.class);
    addBuiltinAggFunc(Sum.class);

    // create a dummy entry for the document view, which is always unqualified
    try {
      viewTableNamespace.add(Constants.DEFAULT_DOC_TYPE_NAME, Constants.DEFAULT_DOC_TYPE_NAME,
          new DocScanCatalogEntry(), CatalogEntryTypeName.VIEW);
    } catch (ParseException pe) {
      // we should never see an error for initializing Document view
      throw new FatalInternalError(pe);
    }

  }

  /**
   * Constructor for merging together multiple different catalogs produced from loading/parsing
   * individual modules. This constructor currently skips parts of the catalog that either don't
   * make sense to merge (such as module-level AQLDoc), as well as some parts of the catalog that
   * aren't needed during operator graph instantiation.
   * 
   * @param individualCatalogs list of catalogs to combine together
   */
  public Catalog(ArrayList<Catalog> individualCatalogs) throws CatalogMergeError {
    // Default constructor takes care of setting up builtins
    this();

    if (debugFunc) {
      Log.debug("Constructing %s from %s", this, individualCatalogs);
    }

    // Zero out every part of the catalog that this constructor does NOT set. We want downstream
    // code to fail early if
    // someone uses the output of this constructor in a way it was not intended to be used.
    // Please keep these assignments in alphabetical order!
    comment = null;
    compilationTempDir = null;
    compilerExceptions = null;
    compilerWarnings = null;
    dependsOnList = null;
    dictsPath = null;
    documentPlan = null;
    exportedDicts = null;
    exportedFunctions = null;
    exportedViewsAndTables = null;
    file2Nodes = null;
    firstSetDefaultDictStmtNode = null;
    moduleName = null;
    modulePath = null;
    nameToPlan = null;
    outputNameOrder = null;
    requireStmts = null;
    tokenizer = null;
    udfJarPath = null;

    // Merge the remaining parts of the catalogs.
    for (int i = 0; i < individualCatalogs.size(); i++) {
      Catalog c = individualCatalogs.get(i);

      try {
        // Please try to keep these fields in alphabetical order. The catalog has a lot of fields.
        functionNamespace.mergeNames(c.functionNamespace);
        jarNamespace.mergeNames(c.jarNamespace);
        dictNamespace.mergeNames(c.dictNamespace);
        mergeMaps(nameToMetadata, c.nameToMetadata);
        viewTableNamespace.mergeNames(c.viewTableNamespace);
      } catch (CatalogMergeError e) {
        // Build up a user-friendly error message.
        ArrayList<String> otherModuleNames = new ArrayList<String>();
        for (int j = 0; j < i; j++) {
          otherModuleNames.add(individualCatalogs.get(j).getModuleName());
        }
        throw new CatalogMergeError(e,
            "Error merging namespaces of catalogs from modules %s with catalog from module '%s'",
            otherModuleNames, c.getModuleName());
      }
    }

    if (debugFunc) {
      Log.debug("After merge funcs are:");
      for (AbstractFuncCatalogEntry entry : functionNamespace.getAllEntries()) {
        Log.debug("   %s --> %s", entry.getName(), entry.getClass().getName());
      }
      Log.debug("---------------------");
    }

  }

  /**
   * Utility method that merges the contents of one map with another.
   * 
   * @param target target map; modified by this method
   * @param src map with additional entries to be merged into the target's existing entry set.
   * @throws CatalogMergeError if it detects a conflict between the values associated to a key
   */
  private static <T> void mergeMaps(Map<String, T> target, Map<String, T> src)
      throws CatalogMergeError {
    for (Entry<String, T> entry : src.entrySet()) {

      Object existingValue = target.get(entry.getKey());

      if (null == existingValue) {
        // Key not in target, so no conflict
        target.put(entry.getKey(), entry.getValue());
      } else {
        // Checks for incompatible entries, if we had them, would go here.
      }
    }
  }

  /**
   * Make a catalog entry for a built-in scalar function, reading relevant data out of the
   * implementing class.
   * 
   * @param implClass class that implements the scalar function.
   */
  private void addBuiltinFunc(Class<? extends ScalarFunc> implClass) {
    ScalarFuncCatalogEntry entry = new ScalarFuncCatalogEntry(implClass);

    // add function to function namespace
    try {
      functionNamespace.add(entry.getName(), entry.getName(), entry, CatalogEntryTypeName.FUNCTION);
    } catch (ParseException e) {
      throw new RuntimeException(
          "This should never happen, because there should not be a duplicate built-in function");
    }

  }

  /**
   * Make a catalog entry for a builtin aggregate function, reading relevant data out of the
   * implementing class.
   * 
   * @param implClass class that implements the aggregate function.
   */
  private void addBuiltinAggFunc(Class<? extends AggFunc> implClass) {
    AggFuncCatalogEntry entry = new AggFuncCatalogEntry(implClass);

    // add function to function namespace
    try {
      functionNamespace.add(entry.getName(), entry.getName(), entry, CatalogEntryTypeName.FUNCTION);
    } catch (ParseException e) {
      throw new RuntimeException(
          "This should never happen, because there should not be a duplicate built-in function");
    }

  }

  /**
   * Special dummy catalog entry for the document, initialized at beginning but set when plan is
   * generated
   */
  private static class DocScanCatalogEntry extends ViewCatalogEntry {

    public DocScanCatalogEntry() {
      super(DOCUMENT_TYPE_NAMES[0]);
    }

    @Override
    public CreateViewNode getParseTreeNode() {
      throw new UnsupportedOperationException(
          "DocScanCatalogEntry.getParseTreeNode() should never be called.");
    }

    @Override
    public boolean getIsExternal() {
      return true;
    }

    @Override
    public ArrayList<String> getColNames() {
      return docColNames;
    }

    // public ArrayList<String> getColTypes ()
    // {
    // return docColTypes;
    // }

    /** Columns of the document type. */
    private final ArrayList<String> docColNames = new ArrayList<String>();
    private final ArrayList<String> docColTypes = new ArrayList<String>();

    private void addColName(String colName) {
      docColNames.add(colName);
    }

    private void addColType(String colType) {
      docColTypes.add(colType);
    }

  }

  /**
   * Make a new symbol table entry for a require document with columns statement; Make a node for
   * each schema.
   * 
   * @param node parse tree node for the view to be added; all necessary symbol table information
   *        will be extracted from
   * @throws ParseException
   */
  public void addRequiredColumns(RequireColumnsNode node) throws ParseException {
    try {

      // for now, we label each require Catalog entry by the built-in doctype + filename
      // assuming at most one require statement per file -- eyhung
      String fileName = node.getContainingFileName();
      String docViewName = Constants.DEFAULT_DOC_TYPE_NAME + fileName;

      if (debugReq) {
        Log.debug("    Adding view '%s' to catalog from file %s.", docViewName, fileName);
      }

      if (documentPlan != null) {
        // if the unionized document schema was already computed, we shouldn't be adding more
        // require columns parse tree nodes, throw an error
        throw AQLParserBase.makeException(String.format(
            "Adding doc schema from file %s after document schema has already been computed ",
            fileName), node.getOrigTok());
      } else {
        // the name of the require catalog entry is simply "Document" + filename
        if (requireStmts.containsKey(docViewName)) {
          Token firstRequireTok = ((RequireColsCatalogEntry) requireStmts.get(docViewName))
              .getParseTreeNode().getOrigTok();
          throw AQLParserBase.makeException(String.format(
              "Multiple require document statements in same file %s (first require statement on line %d)",
              fileName, firstRequireTok.beginLine), node.getOrigTok());
        } else {
          validateRequire(node);

          RequireColsCatalogEntry requireCatalogEntry = new RequireColsCatalogEntry(node);
          requireStmts.put(docViewName, requireCatalogEntry);
        }
      }

    } catch (ParseException pe) {
      throw ParseToCatalog.makeWrapperException(pe, node.getContainingFileName());
    }
  }

  /**
   * Validate require document columns statement by checking if it has been previously defined with
   * different type
   * 
   * @param node parse tree node for the required document column
   * @throws ParseException
   */
  private void validateRequire(RequireColumnsNode node) throws ParseException {

    // check to see if a newly defined column has been previously defined
    // with a different type
    ArrayList<RequireColumnsNode> nodes = getRequireColsNodes();

    ArrayList<NickNode> newNames = node.getColNames();
    ArrayList<NickNode> newTypes = node.getColTypes();

    for (RequireColumnsNode oldNode : nodes) {
      for (int i = 0; i < newNames.size(); i++) {
        if (oldNode.clashesWith(newNames.get(i).getNickname(), newTypes.get(i).getNickname())) {
          throw AQLParserBase.makeException(String.format(
              "Required column %s [type %s] does not match previously defined column "
                  + "(on line %d " + "of %s)",
              newNames.get(i).getNickname(), newTypes.get(i).getNickname(),
              oldNode.getOrigTok().beginLine, oldNode.getContainingFileName()), node.getOrigTok());
        }
      }
    }
  }

  /**
   * Gets the document schema from the parse tree nodes stored in the catalog. If the unionized
   * schema has not been computed yet, compute it.
   * 
   * @return the plan for the unionized document schema
   */
  public RequireDocumentPlanNode getDocumentPlan() throws ParseException {

    RequireColumnsNode unionizedNode = null;

    if (documentPlan == null) {

      ArrayList<RequireColumnsNode> nodes = getRequireColsNodes();

      // with no require statements, use the default schema of (text Text, label Text)
      if (nodes.size() == 0) {
        unionizedNode = new RequireColumnsNode(new ArrayList<NickNode>(), new ArrayList<NickNode>(),
            null, null);
        unionizedNode.addCol(Constants.DOCTEXT_COL, FieldType.TEXT_TYPE);
        unionizedNode.addCol(Constants.LABEL_COL_NAME, FieldType.TEXT_TYPE);
      } else {

        unionizedNode = new RequireColumnsNode(new ArrayList<NickNode>(), new ArrayList<NickNode>(),
            null, null);
        for (int i = 0; i < nodes.size(); i++) {
          RequireColumnsNode addNode = nodes.get(i);
          for (int j = 0; j < addNode.getColNames().size(); j++) {
            unionizedNode.addCol(addNode.getColNames().get(j).getNickname(),
                FieldType.stringToFieldType(addNode.getColTypes().get(j).getNickname()));
          }
        }

      }

      unionizedNode.compressCols();

      documentPlan = new RequireDocumentPlanNode(unionizedNode);

      // Set the dummy entry in the views

      try {
        DocScanCatalogEntry docScanEntry =
            (DocScanCatalogEntry) lookupView(Constants.DEFAULT_DOC_TYPE_NAME, null);

        ArrayList<NickNode> docColNames = unionizedNode.getColNames();

        for (NickNode docColNode : docColNames) {
          docScanEntry.addColName(docColNode.getNickname());
        }

        ArrayList<NickNode> docColTypes = unionizedNode.getColTypes();
        for (NickNode docColTypeNode : docColTypes) {
          docScanEntry.addColType(docColTypeNode.getNickname());
        }
      } catch (ParseException pe) {
        throw ParseToCatalog.makeWrapperException(pe, null);
      }

    }

    if (debugReq) {
      try {
        System.out.printf("\nDoc plan AOG:\n");
        documentPlan.toAOGNoRename(new PrintWriter(System.out, true), 0, this);
      } catch (Exception e) {
      }
    }

    return documentPlan;

  }

  /**
   * Make a new symbol table entry for a create view statement; also stores information about the
   * schema of the view.
   * 
   * @param node parse tree node for the view to be added; all necessary symbol table information
   *        will be extracted from this node
   * @throws ParseException
   */
  public void addView(CreateViewNode node) throws ParseException {
    try {
      boolean debug = false;

      String fqName = node.getViewName();

      if (debug) {
        Log.debug("    Adding view '%s' to catalog.", fqName);
      }

      // disallow views named "docscan"
      if (fqName.equals(BUILT_IN_DOC_TYPE_NAME)) {
        throw AQLParserBase.makeException(
            String.format("View '%s' is reserved in v2.0+; rename '%s' to something else.\n",
                fqName, fqName),
            node.getViewNameNode().getOrigTok());
      }

      // disallow view name Document
      if (node.getUnqualifiedName().equals(Constants.DEFAULT_DOC_TYPE_NAME)) {
        throw AQLParserBase.makeException(
            "The view name conflicts with the built-in view 'Document'. Choose a different name for the view.",
            node.getViewNameNode().getOrigTok());
      }

      // Add the view even if there are validation errors, this will help
      // us to proceed further with semantics check and avoid reporting
      // incorrect compiler errors.
      // Example: say there are semantic errors in view X and we don't add
      // view X to catalog. All later references to view X later will
      // result in compile error 'View X not defined '.
      ViewCatalogEntry viewCatalogEntry = new ViewCatalogEntry(node);
      viewTableNamespace.add(node.getUnqualifiedName(), fqName, viewCatalogEntry,
          CatalogEntryTypeName.VIEW, node.getViewNameNode().getOrigTok(), false);

      // It is legal to reference the view using both qualified and unqualified names. So, add both
      // of the following
      // mappings
      // unqualified Name => fully qualified name (added in call to add () above)
      // fully qualified Name => fully qualified name

      if (false == ModuleUtils.isGenericModule(node.getModuleName())) {
        viewTableNamespace.linkAliasToCanonicalName(fqName, fqName,
            node.getViewNameNode().getOrigTok(), false);
      }

      if (node.getIsOutput()) {
        addOutputView(node.getViewName(), node.getOutputName());
      }
    } catch (ParseException pe) {
      if (pe.currentToken == null) {
        pe.currentToken = node.getViewNameNode().getOrigTok();
      }
      throw ParseToCatalog.makeWrapperException(pe, node.getContainingFileName());
    }
  }

  /**
   * Make a new catalog entry for a detag statement.
   * 
   * @param node parse tree node for the statement
   * @throws ParseException
   */
  public void addDetag(DetagDocNode node) throws ParseException {
    try {
      // We create a catalog entry for each output of the detag statement.

      // First output is the "detagged documents" output.
      String detagName = node.getDetaggedDocName();
      viewTableNamespace.add(detagName, detagName, new DetagCatalogEntry(detagName, node),
          CatalogEntryTypeName.DETAG_VIEW, node.getTarget().getTabnameTok(), false);

      // Remaining outputs are for the various HTML elements that are
      // extracted during detagging.
      ArrayList<DetagDocSpecNode> entries = node.getEntries();
      for (DetagDocSpecNode spec : entries) {
        detagName = spec.getTagType().getNickname();
        viewTableNamespace.add(detagName, detagName, new DetagCatalogEntry(detagName, node),
            CatalogEntryTypeName.DETAG_VIEW, node.getTarget().getTabnameTok(), false);
      }

      // all valid references to detag view nodes to namespace
      addValidDetagViewReferences(node);

      if (node.getOutputDetaggedDocs()) {
        // This node is a "detag into" statement; make sure that its output
        // will be externalized.
        addOutputView(node.getDetaggedDocName(), null);
      }
    } catch (ParseException pe) {
      throw ParseToCatalog.makeWrapperException(pe, node.getContainingFileName());
    }

  }

  /**
   * Adds all valid references to detag view nodes to validElemRefs map
   * 
   * @param node The detag node whose valid references are to be added to catalog
   * @throws ParseException
   */
  private void addValidDetagViewReferences(DetagDocNode node) throws ParseException {
    // Pass 1:add qualified names for DetaggedDocName
    String fqnDetaggedDocName = node.getDetaggedDocName();
    // It is legal to reference the view using both qualified and unqualified names. So, add the
    // mapping:
    // unqualified Name => fully qualified name
    if (false == ModuleUtils.isGenericModule(node.getModuleName())) {
      viewTableNamespace.linkAliasToCanonicalName(node.getUnqualifiedDetaggedDocName(),
          fqnDetaggedDocName, node.getOrigTok(), false);
    }

    // Pass 2: add qualified names for DetagDocSpecNodes
    for (DetagDocSpecNode entry : node.getEntries()) {
      String unqualifiedName = entry.getUnqualifiedName();
      String qualifiedName = entry.getTagType().getNickname();
      if (false == ModuleUtils.isGenericModule(node.getModuleName())) {
        viewTableNamespace.linkAliasToCanonicalName(unqualifiedName, qualifiedName,
            node.getOrigTok(), false);
      }
    }
  }

  /**
   * Create a new catalog entry for a dictionary defined with a "create dictionary" statement.
   * 
   * @param node parse tree node for the dictionary definition
   * @throws ParseException if there is already a dictionary by that name
   */
  public void addDict(CreateDictNode node) throws ParseException {
    try {
      String fqName = node.getDictname();

      if (debugDict) {
        Log.debug("Adding information about dict '%s' to catalog.", fqName);
        Log.debug("--> Dictionary node of type '%s'", node.getClass().getName());
        // Log.debug("Stack trace is:");
        // (new Throwable()).printStackTrace();
      }

      // Do some sanity checks first.
      if (dictNamespace.containsName(fqName)) {
        DictCatalogEntry entry = dictNamespace.getCatalogEntry(fqName);

        if (entry instanceof DictCatalogEntry.OnDisk) {
          DictCatalogEntry.OnDisk od = (DictCatalogEntry.OnDisk) entry;

          throw AQLParser.makeException(String.format(
              "Dictionary '%s' conflicts with " + "on-disk dictionary "
                  + "referenced or declared on line %d " + "of %s",
              fqName, od.getOrigTok().beginLine, od.getOrigFileName()), node.getOrigTok());
        } else {
          throw AQLParser.makeException(String.format(
              "Dictionary '%s' declared twice " + "(original declaration on line %d " + "of %s)",
              fqName, node.getOrigTok().beginLine, node.getContainingFileName()),
              node.getOrigTok());
        }

      }

      // If we get here, we passed the sanity checks.

      // Create the appropriate type of parse tree node.
      if (node instanceof CreateDictNode.Inline) {
        CreateDictNode.Inline il = (CreateDictNode.Inline) node;
        dictNamespace.add(node.getUnqualifiedName(), fqName, new DictCatalogEntry.Inline(il),
            CatalogEntryTypeName.DICTIONARY, node.getOrigTok(), false);
      } else if (node instanceof CreateDictNode.FromTable) {
        CreateDictNode.FromTable ft = (CreateDictNode.FromTable) node;
        dictNamespace.add(node.getUnqualifiedName(), fqName,
            new DictCatalogEntry.FromTable(ft, this), CatalogEntryTypeName.DICTIONARY,
            node.getOrigTok(), false);
      } else if (node instanceof CreateDictNode.FromFile) {
        CreateDictNode.FromFile ff = (CreateDictNode.FromFile) node;
        dictNamespace.add(node.getUnqualifiedName(), fqName,
            new DictCatalogEntry.OnDisk(ff, dictsPath), CatalogEntryTypeName.DICTIONARY,
            node.getOrigTok(), false);
      } else {
        throw new RuntimeException("Don't know about dict type of " + node);
      }

      // add the dictionary name to qualifiedDicts map
      // It is legal to reference the dictionary using both qualified and unqualified names.
      // So, add both of the following mappings:
      // unqualified Name => fully qualified name (already added in add())
      // fully qualified Name => fully qualified name

      if (false == ModuleUtils.isGenericModule(node.getModuleName())) {
        dictNamespace.linkAliasToCanonicalName(fqName, fqName, node.getOrigTok(), false);
      }

    } catch (ParseException pe) {
      throw ParseToCatalog.makeWrapperException(pe, node.getContainingFileName());
    }
  }

  /**
   * Create catalog entry for dictionary files referred in 'extract dictionary ' clause or
   * MatchesDict/ContainsDict/ContainsDicts built-in function.
   * 
   * @param moduleName name of the current module
   * @param name external dictionary files
   * @param errorLoc indicates where in the original AQL file to report an error if the lookup fails
   * @param errorFile indicates the name of the AQL file in which we should report an error if the
   *        lookup fails
   * @throws ParseException
   */
  public void addImplicitDict(String moduleName, String name, Token errorLoc, String errorFile)
      throws ParseException {
    DictCatalogEntry dictCatalogEntry = lookupDict(name, errorLoc, errorFile);

    // Entry is created, only for the first reference to the dictionary file
    if (null == dictCatalogEntry) {
      CreateDictNode.FromFile explicitFromFileNode =
          new CreateDictNode.FromFile(errorFile, errorLoc);
      explicitFromFileNode.setModuleName(moduleName);

      // Prepare dictionary matching parameter
      DictParams params = new DictParams();
      params.setDictName(name);
      params.setIsInline(false);
      params.setFileName(name);

      explicitFromFileNode.setParams(params);

      addDict(explicitFromFileNode);
    }
  }

  /**
   * Replace the existing catalog entry for a dictionary with a generated inline dictionary. Used
   * during query rewrite when inlining references to external dictionary files.
   * 
   * @param newNode parse tree node for the replacement dictionary definition
   */
  public void replaceDict(CreateDictNode.Inline newNode) throws ParseException {
    String name = newNode.getDictname();

    // Do some sanity checks first.
    if (false == isValidDictionaryReference(name)) {
      throw AQLParser.makeException(String.format(
          "Attempted to replace dictionary %s, " + "which does not exist in the catalog.", name),
          newNode.getOrigTok());
    }

    dictNamespace.overwrite(name, new DictCatalogEntry.Inline(newNode),
        CatalogEntryTypeName.DICTIONARY, newNode.getOrigTok());
  }

  /**
   * Create a new catalog entry for a lookup table
   * 
   * @param node parse tree node for the table definition
   * @throws ParseException if there is already table or view by that name
   */
  public void addTable(CreateTableNode node) throws ParseException {
    try {
      // disallow view name Document
      if (node.getUnqualifiedName().equals(Constants.DEFAULT_DOC_TYPE_NAME)) {
        throw AQLParserBase.makeException(
            "The table name conflicts with the built-in view 'Document'. Choose a different name for the table.",
            node.getTableNameNode().getOrigTok());
      }

      String fqName = node.getTableName();

      // Create the appropriate type of parse tree node.
      if (node instanceof CreateTableNode.Inline) {
        CreateTableNode.Inline il = (CreateTableNode.Inline) node;
        viewTableNamespace.add(node.getUnqualifiedName(), fqName, new TableCatalogEntry(il),
            CatalogEntryTypeName.TABLE, node.getTableNameNode().getOrigTok(), false);
      } else if (node instanceof CreateTableNode.FromFile) {
        CreateTableNode.FromFile ff = (CreateTableNode.FromFile) node;
        viewTableNamespace.add(node.getUnqualifiedName(), fqName,
            new TableCatalogEntry.OnDisk(ff, dictsPath), CatalogEntryTypeName.TABLE,
            node.getTableNameNode().getOrigTok(), false);

        ArrayList<NickNode> colNames = node.getColNames();
        ArrayList<NickNode> colTypes = node.getColTypes();

        // Prepare table parameters
        TableParams params = new TableParams();
        params.setTableName(node.getTableName());
        params.setFileName(node.getTableFileName());
        params.setColNames(colNames);
        params.setColTypes(colTypes);
        params.setSchema();
        node.setParams(params);

      } else {
        throw new RuntimeException("Don't know about table type of " + node);
      }

      // add the table name to qualifiedElems map
      // It is legal to reference the table using both qualified and unqualified names.
      // So, add both of the following mappings:
      // unqualified Name => fully qualified name (added earlier in add())
      // fully qualified Name => fully qualified name
      if (false == ModuleUtils.isGenericModule(node.getModuleName())) {
        viewTableNamespace.linkAliasToCanonicalName(fqName, fqName, node.getOrigTok(), false);
      }
    } catch (ParseException pe) {
      throw ParseToCatalog.makeWrapperException(pe, node.getContainingFileName());
    }
  }

  /**
   * Create a new catalog entry for an external view
   * 
   * @param node parse tree node for the external view definition
   * @throws ParseException if there is already table or view by that name
   */
  public void addExternalView(CreateExternalViewNode node) throws ParseException {
    try {
      String fqName = node.getExternalViewName();

      if (debugExtView) {
        System.out.printf("adding external view %s\n", fqName);
      }

      viewTableNamespace.add(node.getUnqualifiedName(), fqName, new ExternalViewCatalogEntry(node),
          CatalogEntryTypeName.EXTERNAL_VIEW, node.getOrigTok(), false);

      if (node.getIsOutput()) {
        addOutputView(fqName, node.getOutputName());
      }

      // It is legal to reference the external view using both qualified and unqualified names.
      // So, add both of the following mappings:
      // unqualified Name => fully qualified name (added earlier in add())
      // fully qualified Name => fully qualified name
      if (false == ModuleUtils.isGenericModule(node.getModuleName())) {
        viewTableNamespace.linkAliasToCanonicalName(fqName, fqName, node.getOrigTok(), false);
      }
    } catch (ParseException pe) {
      throw ParseToCatalog.makeWrapperException(pe, node.getContainingFileName());
    }
  }

  /**
   * Create a new catalog entry for a scalar or table function. Called from the AQL parser.
   * 
   * @param node parse tree node for the function definition
   * @throws ParseException if there is already function by that name
   */
  public void addFunction(CreateFunctionNode node) throws ParseException {
    try { // Begin try block for entire method; exceptions are wrapped at end of method

      // The catalog still uses a separate SearchPath for locating jar files. Other auxiliary
      // resources are also
      // loaded via this legacy mechanism, which no longer makes much sense, given that all
      // locations during
      // compilation are relative to the current module's root.
      // For modular AQL, these SearchPaths ought to be replaced with a single reference to the
      // location of the root
      // of the current module.
      // At the moment, the location of the current module is not recorded anywhere in the catalog.
      // As a temporary
      // measure, compute the location of the module root from the SearchPath.
      File moduleRoot = new File(this.udfJarPath.toString());

      if (FuncLanguage.PMML.equals(node.getLanguage())) {
        // SPECIAL CASE: Scoring function represented by a PMML file. Wrap the PMML file in a jar
        // file along with some
        // Java glue code, so that downstream components in the compilation/loading chain don't need
        // to be PMML-enabled.
        CreateFunctionNode copy = new CreateFunctionNode(node);

        // By convention, the relative path from the module root to the PMML file is stored in the
        // function's external
        // name
        String pmmlFileStr = copy.getExternalName();

        File pmmlFile = FileUtils.createValidatedFile(moduleRoot, pmmlFileStr);

        // Generate a package and class name for the generated java code
        String packageName = moduleName;
        String className = copy.getUnqualifiedName();

        // We write the jar file into an internal buffer so that we don't need to worry about
        // cleaning up temp files
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try {
          PMMLUtil.makePMMLJar(pmmlFile, packageName, className, buf);
        } catch (Exception e) {
          throw AQLParserBase.makeException(e, copy.getOrigTok(),
              "Could not compile PMML file %s%s%s into a UDF."
                  + "  Ensure that file exists and is readable",
              moduleRoot, File.separator, pmmlFileStr);
        }

        // Generate a fake jar file name so that downstream code will know how to find the jar in
        // the catalog
        String fakeJarFileName =
            String.format("%s____%s.jar", copy.getUnqualifiedName(), pmmlFile.getName());

        // Now rewrite the copy of the node into a Java-based UDF based on the temporary jar file.
        String externalNameStr =
            String.format("%s:%s.%s!eval", fakeJarFileName, packageName, className);

        copy.setExternalName(externalNameStr);
        copy.setLanguage(FuncLanguage.Java);

        // Wrap the generated jar file in a catalog entry so that downstream code will know where to
        // find it. Note that
        // this operation short-circuits the code at the end of this function that would normall add
        // the catalog entry.
        String fqJarFileName = ModuleUtils.prepareQualifiedName(getModuleName(), fakeJarFileName);

        jarNamespace.add(fakeJarFileName, fqJarFileName,
            new SerializedJarCatalogEntry(fakeJarFileName, buf.toByteArray()),
            CatalogEntryTypeName.JAR_FILE, node.getOrigTok(), false);

        // All downstream phases of compilation (including the rest of this method) will see the
        // rewritten copy.
        node = copy;
        // END SPECIAL CASE
      }

      // Generate the appropriate type of catalog entry for the function.
      boolean isTableFunc = node.getReturnType().isTable();

      // Compute the fully-qualified name of the function.
      String fqName = node.getFunctionName();

      AbstractFuncCatalogEntry entry;
      if (isTableFunc) {
        // Table function --> TableUDFCatalogEntry
        entry = new TableUDFCatalogEntry(node, this);
      } else {
        // Scalar function --> ScalarUDFCatalogEntry
        entry = new ScalarUDFCatalogEntry(node, fqName);
      }

      if (debugFunc) {
        Log.debug("Adding function %s to catalog from AQL with entry %s\n", fqName, entry);
      }

      // Add the function with its unqualified name as a reference
      functionNamespace.add(node.getUnqualifiedName(), fqName, entry, CatalogEntryTypeName.FUNCTION,
          node.getOrigTok(), false);

      // If this is not part of the generic module, add a reference to this function with its fully
      // qualified name
      if (false == ModuleUtils.isGenericModule(node.getModuleName())) {
        if (debugFunc) {
          Log.debug("  --> Also adding alias %s -> %s to catalog", fqName, fqName);
        }
        functionNamespace.linkAliasToCanonicalName(fqName, fqName, node.getOrigTok(), false);
      }

      // Add the function's supporting jar file to the catalog if it is not already there.
      String jarFileName = node.getJarName();

      if (false == jarNamespace.containsName(jarFileName)) {
        // Qualify the name with the current module's name, in case two modules have different jar
        // files at the same
        // location.
        String fqJarFileName = ModuleUtils.prepareQualifiedName(getModuleName(), jarFileName);

        jarNamespace.add(jarFileName, fqJarFileName,
            new OnDiskJarCatalogEntry(jarFileName, moduleRoot), CatalogEntryTypeName.JAR_FILE,
            node.getOrigTok(), false);
      }

    } catch (ParseException pe) {
      if (pe.currentToken == null) {
        pe.currentToken = node.getOrigTok();
      }
      throw ParseToCatalog.makeWrapperException(pe, node.getContainingFileName());
    }
  }

  /**
   * Create a new catalog entry for a user-defined scalar or table function. Called directly from
   * the AOG parser.
   * 
   * @param name fully-qualified name of the function
   * @param params information necessary to instantiate the function
   * @throws com.ibm.avatar.aog.ParseException so as to trigger the AOG parser's error handling
   */
  public void addFunctionAOG(String name, UDFParams params)
      throws com.ibm.avatar.aog.ParseException {

    // Determine what kind of function we're dealing with and generate the appropriate catalog entry
    AbstractFuncCatalogEntry entry;
    if (params.isTableFunc()) {
      entry = new TableUDFCatalogEntry(name, params);
    } else if (params.returnsBoolean()) {
      entry = new ScalarUDFCatalogEntry(name, UDFPredicateWrapper.class, params);

      if (debugFunc) {
        Log.debug("%s function returns Boolean value; using UDFPredicateWrapper class", name);
      }
    } else {
      entry = new ScalarUDFCatalogEntry(name, ScalarUDF.class, params);
    }

    if (debugFunc) {
      Log.debug("%s: addFunctionAOG called for %s --> %s\n", this, name, entry);
    }

    // Try block to convert exceptions to AOG parser format
    try {
      functionNamespace.add(name, name, entry, CatalogEntryTypeName.FUNCTION);
    } catch (ParseException pe) {
      throw new com.ibm.avatar.aog.ParseException(
          String.format("Error adding function %s to catalog", name), pe);
    }
  }

  /**
   * Create a new catalog entry for a jar file (for use by UDFs). Called directly from the AOG
   * parser, as well as during TAM file loading.
   * <p>
   * This method will go away when we stop supporting the serialization of jar files inside AOG.
   * 
   * @param name fully-qualified AQL name of the jar file
   * @param jarBytes the contents of the jar file, encoded as a byte array
   * @throws com.ibm.avatar.aog.ParseException so as to trigger the AOG parser's error handling
   */
  public void addJarFileAOG(String name, byte[] jarBytes) throws com.ibm.avatar.aog.ParseException {
    SerializedJarCatalogEntry entry = new SerializedJarCatalogEntry(name, jarBytes);

    // Try block to convert exceptions to AOG parser format
    try {
      jarNamespace.add(name, name, entry, CatalogEntryTypeName.JAR_FILE);
    } catch (ParseException pe) {
      throw new com.ibm.avatar.aog.ParseException(
          String.format("Error adding jar file with name '%s' to catalog", name), pe);
    }
  }

  /**
   * Adds all imported elements (Views, Dictionaries, Tables and Functions) to the catalog. <br/>
   * Handles import view, import dictionary, import table and import function statements, with and
   * without aliases.
   * 
   * @param importElemNode parse tree node for the "import" statement
   * @throws ParseException
   */
  public void addImportElementNode(AbstractImportNode importElemNode) throws ParseException {

    String importedModuleName = importElemNode.getFromModule().getNickname();
    String currModuleName = importElemNode.getModuleName();

    // Pass 1: Load module metadata
    ModuleMetadata metadata = null;
    if (nameToMetadata.containsKey(importedModuleName)) {
      metadata = nameToMetadata.get(importedModuleName);
    } else {
      try {

        // BEGIN SPECIAL CASE
        // check for self imports: defect
        if (null != importedModuleName && null != currModuleName) {
          if (importedModuleName.equals(currModuleName)) {
            throw AQLParserBase.makeException(importElemNode.getFromModule().getOrigTok(),
                "Self import of modules is not allowed.");
          }
        }
        // END SPECIAL CASE

        CompileTimeModuleResolver moduleResolver =
            new CompileTimeModuleResolver(compilationTempDir);
        metadata = moduleResolver.readMetaData(importedModuleName, modulePath);
        nameToMetadata.put(importedModuleName, metadata);
        dependsOnList.add(importedModuleName);
      } catch (ModuleNotFoundException mnfe) {
        throw AQLParserBase.makeException(importElemNode.getFromModule().getOrigTok(),
            "Module '%s' not found in module path '%s'.", importedModuleName, modulePath);
      } catch (ParseException pe) {
        throw pe;
      } catch (Exception e) {
        if (e instanceof JAXBException) {
          // Pass through XML exceptions as fatal runtime errors.
          // Someone higher up in the system is catching these RuntimeExceptions and
          // discarding their stack information, so print a stack trace to STDERR
          e.printStackTrace();
          throw new RuntimeException(e);
        } else {
          throw AQLParserBase.makeException(importElemNode.getOrigTok(), e.getMessage());
        }

      }
    }

    // Pass 2: Add the imported element to the catalog.
    String importedElemName = importElemNode.getNodeName().getNickname();
    String fqnImportedElem = ModuleUtils.prepareQualifiedName(importedModuleName, importedElemName);

    // If alias is specified, then map the alias name with imported node
    NickNode alias = importElemNode.getAlias();
    if (alias != null) {
      String aliasName = alias.getNickname();
      String fqAliasName = ModuleUtils.prepareQualifiedName(currModuleName, aliasName);

      // imported node is a dictionary
      if (importElemNode instanceof ImportDictNode) {
        // add mapping: aliasName => fqnImportedElem
        addImportedElem(importElemNode, fqAliasName, fqnImportedElem, metadata);
        dictNamespace.linkAliasToCanonicalName(aliasName, fqnImportedElem,
            importElemNode.getOrigTok(), true);
      }

      // imported node is a view or table
      else if (importElemNode instanceof ImportViewNode
          || importElemNode instanceof ImportTableNode) {
        // add mapping: aliasName => fqnImportedElem
        addImportedElem(importElemNode, fqAliasName, fqnImportedElem, metadata);
        viewTableNamespace.linkAliasToCanonicalName(aliasName, fqnImportedElem,
            importElemNode.getOrigTok(), true);
      }

      // imported node is a function
      else if (importElemNode instanceof ImportFuncNode) {
        // add mapping: aliasName => fqnImportedElem
        addImportedElem(importElemNode, fqAliasName, fqnImportedElem, metadata);
        functionNamespace.linkAliasToCanonicalName(aliasName, fqnImportedElem,
            importElemNode.getOrigTok(), true);
      }
    } else { // if no alias is specified
      // add the imported element with the mapping fqnImportedElem => fqnImportedElem
      addImportedElem(importElemNode, fqnImportedElem, fqnImportedElem, metadata);
    }

  }

  /**
   * 1) Adds the imported element to importedDicts or importedElems list. <br/>
   * 2) Adds the mapping: fqNickImportedElem => fqnImportedElement to qualifiedDicts or
   * qualifiedElems map.<br/>
   * 3) Creates a ViewCatalogEntry for imported views and adds them to catalog. <br/>
   * 4) Create a dummy catalog entry for imported UDF and add them to catalg. <br>
   * 
   * @param importElemNode parse tree node for the "import" statement
   * @param fqNickImportedElem fully qualified alias name (or) imported element name, the way it is
   *        specified in import statement
   * @param fqnImportedElement fully qualified name of the imported element
   * @param metadata module metadata of the imported element's module
   * @throws ParseException
   */
  private void addImportedElem(AbstractImportNode importElemNode, String fqNickImportedElem,
      String fqnImportedElement, ModuleMetadata metadata) throws ParseException {

    if (debug) {
      Log.debug("\timport %s as %s\n", fqnImportedElement, fqNickImportedElem);
    }

    if (importElemNode instanceof ImportDictNode) {

      String uqDictName = importElemNode.getNodeName().getNickname();

      // Validate if the dictionary to import is marked exported
      DictionaryMetadata dmd = metadata.getDictionaryMetadata(fqnImportedElement);
      if (null == dmd || false == dmd.isExported()) {
        throw AQLParserBase.makeException(importElemNode.getOrigTok(),
            "Imported dictionary name %s not found in metadata for module %s (exported dictionaries are %s)",
            uqDictName, metadata.getModuleName(),
            Arrays.toString(metadata.getExportedDictionaries()));
      }

      // add dummy catalog entry for imported dictionary
      dictNamespace.add(fqNickImportedElem, fqnImportedElement,
          new DictCatalogEntry(fqnImportedElement, importElemNode), CatalogEntryTypeName.DICTIONARY,
          importElemNode.getOrigTok(), true);
    } else if (importElemNode instanceof ImportViewNode) {

      ImportViewNode impViewNode = (ImportViewNode) importElemNode;
      String unqualifiedViewName = impViewNode.getNodeName().getNickname();

      // Validate if the view to import is marked exported
      ViewMetadata vmd = metadata.getViewMetadata(fqnImportedElement);
      if (null == vmd || false == vmd.isExported()) {
        throw AQLParserBase.makeException(importElemNode.getOrigTok(),
            "Imported view name %s not found in metadata for module %s (exported views are %s)",
            unqualifiedViewName, metadata.getModuleName(),
            Arrays.toString(metadata.getExportedViews()));
      }

      // create a ViewCatalogEntry for imported view and add to catalog
      CatalogEntry vce = null;
      if (true == vmd.isExternal()) {
        vce = new ExternalViewCatalogEntry(fqnImportedElement, vmd, importElemNode);
        viewTableNamespace.add(fqNickImportedElem, fqnImportedElement, vce,
            CatalogEntryTypeName.EXTERNAL_VIEW, importElemNode.getOrigTok(), true);
      } else {
        vce = new ViewCatalogEntry(fqnImportedElement, vmd, importElemNode);
        viewTableNamespace.add(fqNickImportedElem, fqnImportedElement, vce,
            CatalogEntryTypeName.VIEW, importElemNode.getOrigTok(), true);
      }
    } else if (importElemNode instanceof ImportFuncNode) {
      String uqFuncName = importElemNode.getNodeName().getNickname();

      // Validate if the function to import is marked exported in the source module
      FunctionMetadata fmd = metadata.getFunctionMetadata(fqnImportedElement);
      if (null == fmd) {
        throw AQLParserBase.makeException(importElemNode.getOrigTok(),
            "Imported function name %s not found in metadata for module %s (exported functions are %s)",
            uqFuncName, metadata.getModuleName(), Arrays.toString(metadata.getExportedFunctions()));
      }

      // add dummy function catalog entry for imported UDF to the catalog
      addDummyFuncCatalogEntry(fqnImportedElement, fqNickImportedElem, fmd, importElemNode);
    } else if (importElemNode instanceof ImportTableNode) {
      String uqTabName = importElemNode.getNodeName().getNickname();

      // Validate if the table to import is marked exported in the source module
      TableMetadata tableMetadata = metadata.getTableMetadata(fqnImportedElement);
      if (null == tableMetadata || false == tableMetadata.isExported()) {
        throw AQLParserBase.makeException(importElemNode.getOrigTok(),
            "Imported table name %s not found in metadata for module %s (exported tables are %s)",
            uqTabName, metadata.getModuleName(), Arrays.toString(metadata.getExportedTables()));
      }

      // add dummy catalog entry for imported table node
      viewTableNamespace.add(fqNickImportedElem, fqnImportedElement,
          new TableCatalogEntry(fqnImportedElement, tableMetadata, importElemNode),
          CatalogEntryTypeName.TABLE, importElemNode.getOrigTok(), true);
    }

  }

  /**
   * Adds the exported elements of imported module to the catalog
   * 
   * @param importModuleNode parse tree node for the "import" statement
   * @throws ParseException
   */
  public void addImportModuleNode(ImportModuleNode importModuleNode) throws ParseException {
    String importedModuleName = importModuleNode.getImportedModuleName().getNickname();
    String currModuleName = importModuleNode.getModuleName();

    // BEGIN SPECIAL CASE
    // check for self imports: defect
    if (null != importedModuleName && null != currModuleName) {
      if (importedModuleName.equals(currModuleName)) {
        throw AQLParserBase.makeException(importModuleNode.getImportedModuleName().getOrigTok(),
            "Self import of modules is not allowed");
      }
    }
    // END SPECIAL CASE

    // BEGIN: check for duplicate imports
    if (true == importedModules.contains(importedModuleName)) {
      // Do not flag any warning or error, since duplicate import module statements are harmless.
      // Simply return so that we do not import the elements twice!
      return;
    }
    // END: check for duplicate imports
    else {
      // add to tracking list
      importedModules.add(importedModuleName);
    }

    // Load module metadata
    ModuleMetadata metadata = null;
    try {

      // attempt to fetch from metadata namespace
      if (nameToMetadata.containsKey(importedModuleName)) {
        metadata = nameToMetadata.get(importedModuleName);
      } else {// load, if not loaded already
        CompileTimeModuleResolver moduleResolver =
            new CompileTimeModuleResolver(compilationTempDir);
        metadata = moduleResolver.readMetaData(importedModuleName, modulePath);
        nameToMetadata.put(importedModuleName, metadata);
        dependsOnList.add(importedModuleName);
      }

    } catch (Exception e) {
      ParseException pe = null;
      if (e instanceof VerboseNullPointerException) {
        pe = AQLParserBase.makeException(importModuleNode.getImportedModuleName().getOrigTok(),
            "Imported module '%s' not found in module path '%s'", importedModuleName, modulePath);
      } else {
        pe = AQLParserBase.makeException(e.getMessage(),
            importModuleNode.getImportedModuleName().getOrigTok());
      }

      addCompilerException(
          ParseToCatalog.makeWrapperException(pe, importModuleNode.getContainingFileName()));
      return;
    }

    // add qualified names of imported dicts to importedElements map
    String[] dicts = metadata.getExportedDictionaries();
    if (dicts != null) {
      for (String dict : dicts) {
        String qName = ModuleUtils.prepareQualifiedName(importedModuleName, dict);

        // add dummy catalog entry for imported dictionary
        dictNamespace.add(qName, qName, new DictCatalogEntry(qName, importModuleNode),
            CatalogEntryTypeName.DICTIONARY, importModuleNode.getOrigTok(), true);

      }
    }

    // add qualified names of imported tables to importedElements map
    String[] tables = metadata.getExportedTables();
    if (tables != null) {
      for (String table : tables) {
        String qName = ModuleUtils.prepareQualifiedName(importedModuleName, table);

        TableMetadata tableMetadata = metadata.getTableMetadata(qName);
        // add dummy catalog entry for imported table
        viewTableNamespace.add(qName, qName,
            new TableCatalogEntry(qName, tableMetadata, importModuleNode),
            CatalogEntryTypeName.TABLE, importModuleNode.getOrigTok(), true);
      }
    }

    // add qualified names of imported views to importedElements map
    String[] views = metadata.getExportedViews();
    if (views != null) {
      for (String view : views) {
        String qName = ModuleUtils.prepareQualifiedName(importedModuleName, view);

        // create a ViewCatalogEntry for imported view
        ViewMetadata vmd = metadata.getViewMetadata(qName);
        CatalogEntry vce = null;
        if (true == vmd.isExternal()) {
          vce = new ExternalViewCatalogEntry(qName, vmd, importModuleNode);
          viewTableNamespace.add(qName, qName, vce, CatalogEntryTypeName.EXTERNAL_VIEW,
              importModuleNode.getOrigTok(), true);
        } else {
          vce = new ViewCatalogEntry(qName, vmd, importModuleNode);
          viewTableNamespace.add(qName, qName, vce, CatalogEntryTypeName.VIEW,
              importModuleNode.getOrigTok(), true);
        }

      }
    }

    // add qualified names of imported functions to importedElements map
    String[] exportedFuncs = metadata.getExportedFunctions();
    if (exportedFuncs != null) {
      for (String func : exportedFuncs) {
        String qName = ModuleUtils.prepareQualifiedName(importedModuleName, func);

        // add dummy function catalog entry for imported UDF to the catalog
        FunctionMetadata fmd = metadata.getFunctionMetadata(qName);
        addDummyFuncCatalogEntry(qName, qName, fmd, importModuleNode);
      }
    }
  }

  /**
   * Adds the exported element (View, Dictionary, Table or Function) to an internal register of
   * exported elements.
   * 
   * @param node the exported node.
   * @throws ParseException
   */
  public void addExportedNode(AbstractExportNode node) throws ParseException {

    String exportedElementName =
        ModuleUtils.prepareQualifiedName(node.getModuleName(), node.getNodeName().getNickname());

    if (node instanceof ExportDictNode) {
      exportedDicts.add(exportedElementName);
    } else if (node instanceof ExportViewNode) {
      // BEGIN: SPECIAL CASE
      if (Constants.DEFAULT_DOC_TYPE_NAME.equals(node.getNodeName().getNickname())) {
        throw AQLParserBase.makeException(
            "The view Document cannot be exported because it is a built-in view. Specify a different view to export.",
            node.getNodeName().getOrigTok());
      }
      // END: SPECIAL CASE
      else {
        exportedViewsAndTables.add(exportedElementName);
      }
    } else if (node instanceof ExportTableNode) {

      // BEGIN: SPECIAL CASE
      if (Constants.DEFAULT_DOC_TYPE_NAME.equals(node.getNodeName().getNickname())) {
        throw AQLParserBase.makeException(
            "The table Document cannot be exported because it is a built-in view. Specify a different table to export.",
            node.getNodeName().getOrigTok());
      }
      // END: SPECIAL CASE

      exportedViewsAndTables.add(exportedElementName);
    } else if (node instanceof ExportFuncNode) {
      exportedFunctions.add(exportedElementName);
    }
  }

  /**
   * Returns <code>true</code>, if the given fully qualified view name is marked exported through
   * 'export view ...' statement.
   * 
   * @param fullyQualifiedViewName fully qualified view name
   * @return <code>true</code>, if the given fully qualified view name is marked exported through
   *         'export view ...' statement
   */
  public boolean isExportedView(String fullyQualifiedViewName) {
    return exportedViewsAndTables.contains(fullyQualifiedViewName);
  }

  /**
   * Returns <code>true</code>, if the given fully qualified table name is marked exported through
   * 'export table ...' statement.
   * 
   * @param fullyQualifiedTableName fully qualified table name
   * @return <code>true</code>, if the given fully qualified table name is marked exported through
   *         'export table ...' statement
   */
  public boolean isExportedTable(String fullyQualifiedTableName) {
    return exportedViewsAndTables.contains(fullyQualifiedTableName);
  }

  /**
   * Returns <code>true</code>, if the given fully qualified UDF name is marked exported through
   * 'export function ...' statement.
   * 
   * @param fullyQualifiedFunctionName fully qualified function name
   * @return <code>true</code>, if the given fully qualified UDF name is marked exported through
   *         'export function ...' statement
   */
  public boolean isExportedFunction(String fullyQualifiedFunctionName) {
    return exportedFunctions.contains(fullyQualifiedFunctionName);
  }

  /**
   * Returns <code>true</code>, if the given fully qualified dictionary name is marked exported
   * through 'export dictionary ...' statement.
   * 
   * @param fullyQualifiedDictName fully qualified dictionary name
   * @return <code>true</code>, if the given fully qualified dictionary name is marked exported
   *         through 'export dictionary ...' statement
   */
  public boolean isExportedDict(String fullyQualifiedDictName) {
    return exportedDicts.contains(fullyQualifiedDictName);
  }

  /**
   * Verifies whether the given dictionary name is a valid dictionary reference
   * 
   * @param dictName Name of the dictionary whose validity is to be checked for
   * @return <code>true</code>, if the given dictName is a valid dictionary reference;
   *         <code>false</code> otherwise
   */
  public boolean isValidDictionaryReference(String dictName) {
    return dictNamespace.containsName(dictName);
  }

  /**
   * Verifies whether the given view name is a valid view reference
   * 
   * @param viewName Name of the view whose validity is to be checked for
   * @return <code>true</code>, if the given viewName is a valid view reference; <code>false</code>
   *         otherwise
   */
  public boolean isValidViewReference(String viewName) {
    return viewTableNamespace.containsName(viewName);
  }

  /**
   * Verifies whether the given table name is a valid table reference
   * 
   * @param tableName Name of the table whose validity is to be checked for
   * @return <code>true</code>, if the given tableName is a valid table reference;
   *         <code>false</code> otherwise
   */
  public boolean isValidTableReference(String tableName) {
    return viewTableNamespace.containsName(tableName);
  }

  /**
   * Returns the aliases linked to a namespace entry. Currently unused.
   * 
   * @param viewTableName Name of the view or table catalog entry to return aliases for
   * @return list of aliases linked to the input viewTableName, or null if none
   */
  public ArrayList<String> getAliases(String viewTableName) {
    if (viewTableNamespace.getEntryFromCanonicalName(viewTableName) == null) {
      return null;
    } else {
      return viewTableNamespace.getAliasesFromName(viewTableName);
    }
  }

  /**
   * Add information about an "output view" statement
   * 
   * @param viewName name of the view being output
   * @param outputName name of the operator graph output to which the tuples of the view will be
   *        directed, or null to use the (fully qualified) name of the view
   * @throws ParseException
   */
  public void addOutputView(String viewName, String outputName) throws ParseException {
    // add qualified names, since even alias names can be used in output view stmt.
    String qualifiedElemName = viewTableNamespace.getCanonicalNameFromAlias(viewName);
    if (qualifiedElemName == null) {
      // TODO: Ideally, we should throw exception if given view name is not a valid reference;this
      // validation
      // should be deferred until we have completely populated catalog.
      // throw new ParseException (String.format ("View %s not defined", viewName));
      qualifiedElemName = viewName;
    }

    outputNameOrder.add(new Pair<String, String>(qualifiedElemName, outputName));
  }

  /**
   * Retrieve information about a view or external type.
   * 
   * @param name name of a view in the current module's scope
   * @param errLoc location in the AQL code at which any errors on lookup should be reported
   * @return information about the indicated view
   * @throws ParseException if the view name is not a valid name
   */
  public CatalogEntry lookupView(String name, Token errLoc) throws ParseException {
    // First, perform the lookup assuming that the given name is qualified view name
    CatalogEntry catalogEntry = viewTableNamespace.getEntryFromCanonicalName(name);

    // If null, then the given name can be a unqualified reference - let's qualify and lookup again
    if (null == catalogEntry) {
      if (false == isValidViewReference(name)) {
        throw AQLParserBase.makeException(errLoc, "View %s not defined", name);
      }
      String qualifiedName = viewTableNamespace.getCanonicalNameFromAlias(name);

      // Internal hash table is on qualified names.
      catalogEntry = viewTableNamespace.getEntryFromCanonicalName(qualifiedName);
      if (catalogEntry == null) {
        throw AQLParserBase.makeException(errLoc,
            "No information in catalog for view %s (expanded from %s)", qualifiedName, name);
      }
    }

    return catalogEntry;
  }

  /**
   * Retrieve information about a view that is a lookup table.
   * 
   * @param name qualified/unqualified lookup table reference
   * @return a table catalog entry; <code>null</code> if the referred name is pointing to view
   * @throws ParseException
   */
  public CatalogEntry lookupTable(String name) throws ParseException {
    // First, perform the lookup assuming that the given name is qualified table name
    CatalogEntry entry = viewTableNamespace.getEntryFromCanonicalName(name);

    // If null, then the given reference might be unqualified - let's qualify and lookup again
    if (null == entry) {
      if (false == isValidTableReference(name)) {
        throw new ParseException(String.format("Table %s not defined", name));
      }

      String qualifiedName = viewTableNamespace.getCanonicalNameFromAlias(name);
      entry = viewTableNamespace.getEntryFromCanonicalName(qualifiedName);

      if (entry == null) {
        throw new ParseException(String.format(
            "No information in catalog for table %s (expanded from %s)", qualifiedName, name));
      }
    }

    return entry instanceof TableCatalogEntry ? entry : null;
  }

  /**
   * Look up a dictionary by name. If there is no inline dictionary with the indicated name, looks
   * for a dictionary file on disk and creates an entry for a disk-based dictionary before returning
   * the relevant node. Unlike other lookup element methods, does not throw ParseException because
   * we expect a null to be returned when looking for a disk-based dictionary.
   * 
   * @param name qualified/un-qualified name of the dictionary
   * @param errorLoc indicates where in the original AQL file to report an error if the lookup fails
   * @param errorFile indicates the name of the AQL file in which we should report an error if the
   *        lookup fails
   * @throws ParseException if no dictionary with the indicated name can be found
   */
  public DictCatalogEntry lookupDict(String name, Token errorLoc, String errorFile)
      throws ParseException {
    // First, perform the lookup assuming that the given name is qualified dict name
    DictCatalogEntry entry = dictNamespace.getEntryFromCanonicalName(name);

    // If null, then the given reference might be unqualified - let's qualify and lookup again
    if (null == entry) {
      String qualifiedName = dictNamespace.getCanonicalNameFromAlias(name);
      entry = dictNamespace.getEntryFromCanonicalName(qualifiedName);
    }

    return entry;
  }

  /**
   * Retrieve information about a table function.
   * 
   * @param name name (qualified or module-local) to look up
   * @return information about the table function, or null if no table function by that name is
   *         found
   */
  public TableFuncCatalogEntry lookupTableFunc(String name) {
    // By convention, ALL names by which the function is accessible are stored as aliases, even
    // (when applicable) the
    // fully-qualified name. So we only need to make one call to functionNamespace to implement the
    // lookup.
    AbstractFuncCatalogEntry entry = functionNamespace.getCatalogEntry(name);

    if (entry instanceof TableFuncCatalogEntry) {
      return (TableFuncCatalogEntry) entry;
    } else {
      return null;
    }

  }

  /**
   * Retrieve information about a scalar function.
   * 
   * @param name qualified/unqualified scalar function reference
   * @return catalog entry for given scalar function reference; <code>null</code> for unknown
   *         reference
   */
  public ScalarFuncCatalogEntry lookupScalarFunc(String name) {
    // By convention, ALL names by which the function is accessible are stored as aliases, even
    // (when applicable) the
    // fully-qualified name. So we only need to make one call to functionNamespace to implement the
    // lookup.
    AbstractFuncCatalogEntry entry = functionNamespace.getCatalogEntry(name);

    if (entry instanceof ScalarFuncCatalogEntry) {
      return (ScalarFuncCatalogEntry) entry;
    } else {
      return null;
    }
  }

  /** Retrieve information about an aggregate function. */
  public AggFuncCatalogEntry lookupAggFunc(String name) {
    // By convention, ALL names by which the function is accessible are stored as aliases, even
    // (when applicable) the
    // fully-qualified name. So we only need to make one call to functionNamespace to implement the
    // lookup.
    AbstractFuncCatalogEntry entry = functionNamespace.getCatalogEntry(name);

    if (entry instanceof AggFuncCatalogEntry) {
      return (AggFuncCatalogEntry) entry;
    } else
      return null;
  }

  /**
   * Retrieve information about a UDF jar file.
   * 
   * @param name local AQL name of the jar file
   * @return catalog entry for the file, or null if no entry exists under that name
   */
  public AbstractJarCatalogEntry lookupJar(String name) {
    return jarNamespace.getCatalogEntry(name);
  }

  /**
   * Version of {@link #lookup(String)} for from list items; also accepts functions.
   * 
   * @throws ParseException
   */
  public CatalogEntry lookup(FromListItemNode fromItem) throws ParseException {
    if (fromItem instanceof FromListItemTableFuncNode) {
      TableFnCallNode fn = ((FromListItemTableFuncNode) fromItem).getTabfunc();

      return lookupTableFunc(fn.getFuncName());
    } else if (fromItem instanceof FromListItemViewRefNode) {
      // From list references a view.
      NickNode nick = ((FromListItemViewRefNode) fromItem).getViewName();
      String viewname = nick.getNickname();
      return lookupView(viewname, fromItem.getOrigTok());
    } else if (null == fromItem) {
      throw new RuntimeException("Null pointer passed to lookup()");
    } else {
      throw new RuntimeException(String.format("Don't understand from list item type '%s'",
          fromItem.getClass().getName()));
    }
  }

  /** Retrieve the output column names produced by a from list entry. */
  public ArrayList<String> getColNames(FromListItemNode fromItem) throws ParseException {

    // We'll build the list on the fly from our internal data
    // structures.

    if (fromItem instanceof FromListItemTableFuncNode) {
      // From list references a table function.
      // For now, we just use special-case code.
      TableFnCallNode fn = ((FromListItemTableFuncNode) fromItem).getTabfunc();

      ArrayList<String> ret = new ArrayList<String>();

      String[] names = fn.getColNames(this);
      for (String name : names) {
        ret.add(name);
      }

      return ret;

    }
    /**
     * Fix for defect# 13094: Error in wildcard select when used with nested query
     */
    else if (fromItem instanceof FromListItemSubqueryNode) {
      ViewBodyNode viewBodyNode = ((FromListItemSubqueryNode) fromItem).getBody();
      String[] names = new SchemaInferrer(this).computeSchema(viewBodyNode).getFieldNames();

      ArrayList<String> ret = new ArrayList<String>(names.length);
      for (String name : names) {
        ret.add(name);
      }

      return ret;
    } else {
      // All other entries in the from list go through the relevant symbol
      // table entry.
      CatalogEntry entry = lookup(fromItem);
      if (null == entry) {
        String itemStr = fromItem.dumpToStr(0);
        throw new CatalogEntryNotFoundException(
            String.format("Internal error looking up from list item %s", itemStr));
      }

      // document view is special case since we can't get the parse tree node
      // and we've already computed the schema in the document plan node -- eyhung
      if (entry instanceof DocScanCatalogEntry) {

        // if the document plan is null, we haven't computed the schema yet, so force a computation
        if (documentPlan == null) {
          getDocumentPlan();
        }

        ArrayList<String> ret = ((DocScanCatalogEntry) entry).getColNames();
        return ret;

      }
      /*
       * Note: When view nodes are validated before adding to catalog (in ParseToCatalog.java),
       * getColNames() is invoked to perform basic validation. At that point of time, viewSchema
       * might be null, since we have not yet computed the type of the node. Tried moving this
       * validation piece to post schema inference in preprocessor, but that led to several other
       * errors. So, keeping it this way for now.
       */
      else if (entry instanceof ViewCatalogEntry) {
        if (((ViewCatalogEntry) entry).getSchema() == null) {

          ArrayList<String> ret = new ArrayList<String>();
          String[] colNames = new SchemaInferrer(this)
              .computeSchema(((ViewCatalogEntry) entry).getParseTreeNode().getBody())
              .getFieldNames();
          for (String colName : colNames) {
            ret.add(colName);
          }
          return ret;
        }
      }

      return entry.getColNames();
    }
  }

  /**
   * @return map containing all view names whose tuples will be sent to the output, in the order
   *         that they appeared in the original AQL file; first element in each pair is the view
   *         name, second is the output name
   */
  public ArrayList<Pair<String, String>> getOutputNamePairs() {
    ArrayList<Pair<String, String>> ret = new ArrayList<Pair<String, String>>();
    for (Pair<String, String> p : outputNameOrder) {

      // BEGIN: SPECIAL CASE
      /**
       * The method {@link #addOutputView(String, String)}, might not have mapped the imported
       * view's alias to its canonical name because Catalog might not be fully populated by then, so
       * trying to establish the mapping now
       */
      String canonicalName = viewTableNamespace.getCanonicalNameFromAlias(p.first);
      // END: SPECIAL CASE

      String viewName = (canonicalName == null) ? p.first : canonicalName;
      String outputName = p.second;

      ret.add(new Pair<String, String>(viewName, outputName));
    }
    return ret;
  }

  /**
   * @return list of parse tree nodes for the views that need to be compiled to generate the current
   *         set of outputs; this list is always topologically sorted according to view dependencies
   */
  public ArrayList<CreateViewNode> getRequiredViews() throws ParseException {

    final boolean debug = false;

    // Return the cached list of view nodes instead of recomputing the list again
    if (sortedRequiredViews != null) {
      if (debug) {
        Log.debug("Cached View nodes:-");
        for (CreateViewNode c : sortedRequiredViews) {
          Log.debug("Node %s", c.getViewName());
        }
      }
      // IMP: This list gets refreshed as a part of the Planner work too and thus it has the right
      // updated set of views
      // post rewrite
      return sortedRequiredViews;
    }

    // Start by building up a set of views, following the dependency graph
    // backwards from the outputs. We use a TreeSet so that we get the same
    // results regardless of JVM.
    TreeSet<CreateViewNode> viewset = new TreeSet<CreateViewNode>();

    // Prepare a potential list of views to compile => add all exported and output views
    ArrayList<String> potentialViewsToCompile = new ArrayList<String>();

    // add output views (except the Document view) to the list
    for (Pair<String, String> p : outputNameOrder) {
      String viewName = p.first;
      if (false == viewName.equals(Constants.DEFAULT_DOC_TYPE_NAME)) {
        potentialViewsToCompile.add(viewName);
      }
    }

    // add exported views to the list
    for (CatalogEntry entry : viewTableNamespace.getAllEntries()) {
      if (entry instanceof ViewCatalogEntry) {
        String viewName = entry.getName();
        if (isExportedView(viewName)) {
          potentialViewsToCompile.add(viewName);
        }
      }
    }

    // remove imported views from potentialViewsToCompile
    if (potentialViewsToCompile != null && potentialViewsToCompile.size() > 0) {
      for (String viewName : potentialViewsToCompile) {
        CatalogEntry entry = null;
        try {
          entry = lookupView(viewName, null);
          // This output could be a view or an external type; only add it to
          // the set if it's a view.
          if (entry.getIsView() && false == entry.isImported()) {
            ViewCatalogEntry vce = (ViewCatalogEntry) entry;
            viewset.add(vce.getParseTreeNode());
          }
        } catch (ParseException e) {
          // Do nothing.
          // Any error thrown by lookupView() method invocation is already handled during node level
          // validation
          // performed in ParseToCatalog and hence reporting the current error again will result in
          // duplicate messages.
        }

      }
    }

    // Currently, all detag statements are also required for compilation, so
    // we need to add their inputs to the required set.
    for (CatalogEntry entry : viewTableNamespace.getAllEntries()) {
      if (entry.getIsDetag()) {
        DetagCatalogEntry detag = (DetagCatalogEntry) entry;

        // Get the name of the view that is the input to the detag
        // statement.
        String targetView = detag.getParseTreeNode().getTarget().getTabname();

        try {
          CatalogEntry targetEntry =
              lookupView(targetView, detag.getParseTreeNode().getTarget().getTabnameTok());

          if (targetEntry.getIsView() && false == targetEntry.getIsExternal()
              && false == targetEntry.isImported() && false == entry.isImported()) {
            ViewCatalogEntry vce = (ViewCatalogEntry) targetEntry;
            viewset.add(vce.getParseTreeNode());
          }
        } catch (ParseException pe) {
          addCompilerException(new ExtendedParseException(pe,
              new File(detag.getParseTreeNode().getContainingFileName())));
        }
      }
    }

    // Transitive closure; keep iterating as long as the set is growing.
    int lastSize;
    do {
      if (debug) {
        Log.debug("Starting iteration with %d views", viewset.size());
      }

      lastSize = viewset.size();

      // We can't modify the set while we're scanning through it, so make
      // a todo list for after.
      ArrayList<CreateViewNode> toAdd = new ArrayList<CreateViewNode>();

      for (CreateViewNode node : viewset) {
        // Add all the views that this one depends on
        TreeSet<String> deps = new TreeSet<String>();
        node.getBody().getDeps(deps, this);
        for (String depName : deps) {
          CatalogEntry entry = null;
          try {
            entry = lookupView(depName, node.getEndOfStmtToken());
          } catch (ParseException pe) {
            // do not throw any error here. Let FromListItemViewRefNode.validate() take care of this
            // dependency missing
            // error
            // throw ParseToCatalog.makeWrapperException (pe, node.getContainingFilename ());
          }

          if (entry != null) {
            if (entry.getIsView()) {
              if (debug) {
                Log.debug("    View %s depends on %s", node.getViewName(), depName);
              }

              ViewCatalogEntry vce = (ViewCatalogEntry) entry;

              // Don't try to compile external views & imported views...
              if (false == vce.getIsExternal() && false == entry.isImported()) {
                toAdd.add(vce.getParseTreeNode());
              }
            }
          }
        }
      }

      // Now that we're finished with our pass through viewset, we can add
      // the new nodes to it.
      for (CreateViewNode node : toAdd) {
        viewset.add(node);
      }

      if (debug) {
        Log.debug("Ending iteration with %d views", viewset.size());
      }

    } while (viewset.size() != lastSize);

    // Convert the set to an ArrayList, and sort.
    ArrayList<CreateViewNode> ret = topologicalSort(viewset);
    // Cache the list of views to be compiled
    // IMP: This list gets refreshed as a part of the Planner work too and thus it has the right
    // updated set of views
    // post rewrite
    sortedRequiredViews = ret;
    return ret;
  }

  /**
   * @return list of parse tree nodes for all the views we know about, even the ones that aren't
   *         used to generate the current outputs.
   */
  public ArrayList<CreateViewNode> getAllViews() {
    ArrayList<CreateViewNode> ret = new ArrayList<CreateViewNode>();
    for (CatalogEntry entry : viewTableNamespace.getAllEntries()) {
      if (entry instanceof ViewCatalogEntry && (false == "Document".equals(entry.getName()))) {
        ViewCatalogEntry vce = (ViewCatalogEntry) entry;
        ret.add(vce.getParseTreeNode());
      }
    }
    return ret;
  }

  /**
   * This method is equivalent to {@link #getCreateDictNodes(false)}.
   * 
   * @return the parse tree nodes for all the create dictionary statements that the catalog knows
   *         about
   */
  public ArrayList<CreateDictNode> getCreateDictNodes() throws ParseException {
    return getCreateDictNodes(false);
  }

  /**
   * @param excludeExternal when false, parse tree nodes for all dictionaries(internal and external)
   *        are returned.<br>
   *        when true, only parse tree nodes for internal dictionaries are returned.
   * @return the parse tree nodes for all the create dictionary statements that the catalog knows
   *         about
   */
  public ArrayList<CreateDictNode> getCreateDictNodes(boolean excludeExternal)
      throws ParseException {
    ArrayList<CreateDictNode> ret = new ArrayList<CreateDictNode>();

    for (DictCatalogEntry entry : dictNamespace.getAllEntries()) {
      // Imported dictionaries are already compiled; no need to compile again
      if (false == entry.isImported()) {
        if (excludeExternal) {
          if (!entry.isExternallyDependent()) {
            ret.add(entry.getParseTreeNode());
          }
        } else {
          ret.add(entry.getParseTreeNode());
        }
      }
    }
    return ret;
  }

  /**
   * @return parse tree nodes for all the detag statements in the catalog
   */
  public ArrayList<DetagDocNode> getDetagDocNodes() {

    // Scan through all the catalog entries and pick out the detag nodes.
    // The nodes will go in multiple times, so use a HashSet.
    HashSet<DetagDocNode> set = new HashSet<DetagDocNode>();

    for (CatalogEntry entry : viewTableNamespace.getAllEntries()) {
      if (entry instanceof DetagCatalogEntry) {
        DetagCatalogEntry dce = (DetagCatalogEntry) entry;
        set.add(dce.getParseTreeNode());
      }
    }

    ArrayList<DetagDocNode> ret = new ArrayList<DetagDocNode>();
    ret.addAll(set);
    return ret;
  }

  /**
   * @return all detag catalog entries from the catalog
   */
  public ArrayList<DetagCatalogEntry> getDetagCatalogEntries() {
    ArrayList<DetagCatalogEntry> ret = new ArrayList<DetagCatalogEntry>();
    for (CatalogEntry entry : viewTableNamespace.getAllEntries()) {
      if (entry instanceof DetagCatalogEntry) {
        ret.add((DetagCatalogEntry) entry);
      }
    }
    return ret;
  }

  /**
   * @return parse tree nodes for all the create table statements in the catalog
   */
  public ArrayList<CreateTableNode> getCreateTableNodes() {

    ArrayList<CreateTableNode> ret = new ArrayList<CreateTableNode>();

    for (CatalogEntry entry : viewTableNamespace.getAllEntries()) {
      if (entry instanceof TableCatalogEntry) {
        TableCatalogEntry tce = (TableCatalogEntry) entry;
        // Compile only non-imported tables
        if (false == tce.isImported())
          ret.add(tce.getParseTreeNode());
      }
    }

    return ret;
  }

  /**
   * @return parse tree nodes for all the create external view statements in the catalog
   */
  public ArrayList<CreateExternalViewNode> getCreateExternalViewNodes() {

    ArrayList<CreateExternalViewNode> ret = new ArrayList<CreateExternalViewNode>();

    for (CatalogEntry entry : viewTableNamespace.getAllEntries()) {
      // Compile only non-imported external views
      if (entry instanceof ExternalViewCatalogEntry && false == entry.isImported()) {
        ExternalViewCatalogEntry tce = (ExternalViewCatalogEntry) entry;
        ret.add(tce.getParseTreeNode());
      }
    }

    return ret;
  }

  /**
   * @return the parse tree nodes for all the create function statements that the catalog knows
   *         about
   */
  public ArrayList<CreateFunctionNode> getCreateFunctionNodes() {

    ArrayList<CreateFunctionNode> ret = new ArrayList<CreateFunctionNode>();
    AQLParseTreeNode node = null;

    for (CatalogEntry entry : functionNamespace.getAllEntries()) {

      if (entry instanceof ScalarUDFCatalogEntry) {
        node = ((ScalarUDFCatalogEntry) entry).getParseTreeNode();
        if ((node != null) && (node instanceof CreateFunctionNode)) {
          ret.add((CreateFunctionNode) node);
        } else {
          // do nothing if it's not a create function node -- the UDF catalog entry
          // can have ImportModule or ImportFunction nodes, but this method only returns
          // CreateFunction nodes.
        }
      } else if (entry instanceof TableUDFCatalogEntry) {
        node = ((TableUDFCatalogEntry) entry).getParseTreeNode();
        if ((node != null) && (node instanceof CreateFunctionNode)) {
          ret.add((CreateFunctionNode) node);
        }
      }
    }
    return ret;
  }

  /**
   * @return the nodes for all the require statements that the catalog knows about
   */
  public ArrayList<RequireColumnsNode> getRequireColsNodes() {

    ArrayList<RequireColumnsNode> ret = new ArrayList<RequireColumnsNode>();
    RequireColumnsNode node = null;

    for (CatalogEntry entry : requireStmts.values()) {

      if (entry instanceof RequireColsCatalogEntry) {
        node = ((RequireColsCatalogEntry) entry).getParseTreeNode();
        if (node != null)
          ret.add(node);
      }
    }
    return ret;
  }

  /**
   * @return a list of all catalog entries for jar files
   */
  public ArrayList<AbstractJarCatalogEntry> getAllJarFiles() {
    ArrayList<AbstractJarCatalogEntry> ret = new ArrayList<AbstractJarCatalogEntry>();
    for (CatalogEntry entry : jarNamespace.getAllEntries()) {
      ret.add((AbstractJarCatalogEntry) entry);
    }
    return ret;
  }

  /**
   * @return a list of all catalog entries for user-defined functions
   */
  public ArrayList<AbstractFuncCatalogEntry> getAllUDFs() {
    ArrayList<AbstractFuncCatalogEntry> ret = new ArrayList<AbstractFuncCatalogEntry>();
    for (AbstractFuncCatalogEntry entry : functionNamespace.getAllEntries()) {
      if (entry instanceof ScalarUDFCatalogEntry || entry instanceof TableUDFCatalogEntry) {
        ret.add(entry);
      }
    }
    return ret;
  }

  /**
   * Remember the plan the optimizer is using for a particular view.
   * 
   * @param viewName name of the view in question
   * @param viewplan optimizer's choice of plan for the view
   */
  public void cachePlan(String viewName, PlanNode viewplan) {
    nameToPlan.put(viewName, viewplan);
  }

  /**
   * This method returns the cost record for the given view name. For imported view cost record is
   * fetched from the, imported view's module meta-data.
   * 
   * @param viewName view name for which cost record should be returned.
   * @return CostRecord for the given view name
   * @throws ParseException
   */
  public CostRecord getCostRecord(String viewName) throws ParseException {
    if (viewName.equals(Constants.DEFAULT_DOC_TYPE_NAME)) {
      return documentPlan.getCostRecord();
    }

    if (debug) {
      Log.debug("\t\tGetting cost record for view: %s\n", viewName);
    }

    CatalogEntry vce = lookupView(viewName, null);

    // For imported views fetch the cost record from the imported view's module meta-data
    if (true == vce.isImported()) {
      if (debug) {
        Log.debug("\t\t\tView %s is imported\n", viewName);
      }

      if (false == vce instanceof ExternalViewCatalogEntry) {
        String viewModuleName = ModuleUtils.getModuleName(vce.getName());
        ModuleMetadata moduleMetadata = nameToMetadata.get(viewModuleName);

        // for imported views, always query view metadata using the name returned by CatalogEntry
        return moduleMetadata.getViewMetadata(vce.getName()).getCostRecord();
      } else { // We don't compute cost record for external views
        return null;
      }
    } else {
      PlanNode viewPlan = nameToPlan.get(viewName);
      return viewPlan.getCostRecord();
    }
  }

  /**
   * Returns the cached plan for a non-imported view. This should never be called for an imported
   * view.
   * 
   * @param viewName name of a view that has already been compiled
   * @return plan for this view that was cached with {@link #cachePlan(String, ViewNode)}
   */
  public PlanNode getCachedPlan(String viewName) {
    PlanNode ret = null;

    if (viewName.equals(Constants.DEFAULT_DOC_TYPE_NAME)) {
      ret = documentPlan;
    } else {
      String qualifiedName = viewTableNamespace.getCanonicalNameFromAlias(viewName);
      ret = nameToPlan.get(qualifiedName);
    }

    if (null == ret) {
      throw new RuntimeException(String.format("No cached plan for view '%s'; "
          + "this usually means that views " + "were compiled in the wrong order.", viewName));
    }

    return ret;
  }

  /**
   * Sort a set of CREATE VIEW nodes according to their dependency graph. Among nodes that have no
   * dependencies, tries to keep the same order as the input ArrayList. An exception will be thrown,
   * if a cycle is detected in the view dependency graph.
   * 
   * @param inputs a set of view parse tree nodes to be sorted according to their dependency graph
   * @throws ParseException expection to report back cycle among the given views to sort
   */
  public ArrayList<CreateViewNode> topologicalSort(Collection<CreateViewNode> inputs)
      throws ParseException {

    boolean debug = false;

    // Map from view name --> view node for every view that we haven't added
    // to the output yet.
    Map<String, CreateViewNode> todo = new LinkedHashMap<String, CreateViewNode>();

    for (CreateViewNode cvn : inputs) {
      todo.put(cvn.getViewName(), cvn);
    }

    // BEGIN FIX FOR defect
    // The error handling code below needs a full set of view information in order to trace cycles,
    // so save a copy of
    // the todo list before starting to remove items from it.
    TreeMap<String, CreateViewNode> nameToInputView = new TreeMap<String, CreateViewNode>();
    nameToInputView.putAll(todo);
    // END FIX FOR defect

    // Output goes here.
    ArrayList<CreateViewNode> ret = new ArrayList<CreateViewNode>();

    // Repeatedly run through the input list, generating the sorted output
    // one layer at a time.
    // Note that this is potentially an O(n^2) algorithm, but probably O(n)
    // in practice (number of passes == number of levels of dependencies)
    while (todo.size() > 0) {

      if (debug) {
        Log.debug("Topological sort: Current todo list is %s", todo.keySet());
      }

      int pendingItemCountBefore = todo.size();

      for (CreateViewNode curNode : inputs) {
        String curName = curNode.getViewName();

        if (todo.containsKey(curName)) {
          // Still haven't added this node to the output; see if it's
          // now eligible.
          boolean dependsOnTodo = false;
          TreeSet<String> deps = new TreeSet<String>();
          curNode.getBody().getDeps(deps, this);

          if (debug) {
            Log.debug("Topological sort: %s depends on %s", curName, deps);
          }

          for (String depName : deps) {
            String qualifiedName = viewTableNamespace.getCanonicalNameFromAlias(depName);
            if (todo.containsKey(qualifiedName)) {
              dependsOnTodo = true;
            }
          }

          if (false == dependsOnTodo) {
            // This node doesn't depend on any item in the current
            // todo list, so we can add it to the output.
            ret.add(curNode);
            todo.remove(curName);
          }
        }

      }
      int pendingItemCountAfter = todo.size();

      /*
       * At the end of each pass, we expect that at least one leaf node is knocked off from todo
       * list. If that does not happen, then it means there are cycles between the views in the todo
       * list, that is certain views depends on itself, directly (viewX -> viewX) or indirectly
       * (viewX -> viewY -> viewZ -> viewX). Topological sort will go on forever for graph
       * containing cycles. So let's identify these cycles and report them back, for module author
       * to break them. As of now we don't report all the cycles, we just report the first
       * cycle.Note:Compilation of this module will stop here.
       */
      if (pendingItemCountAfter == pendingItemCountBefore) {
        identifyAndReportCycle(nameToInputView);
      }

    }

    if (debug) {
      // Debug mode on; print out the output views.
      ArrayList<String> sortedNames = new ArrayList<String>();
      for (CreateViewNode node : ret) {
        sortedNames.add(node.getViewName());
      }

      Log.debug("toplogicalSort(): Sorted views are %s", sortedNames);
    }

    return ret;
  }

  /**
   * Identify and report cycle between the views that could not be sorted topologically. As of now
   * this method does not identify all the cycles, it just reports the first cycle.
   * 
   * @param viewNameVsNode map of views that could not be sorted topologically, where key of the map
   *        is name of the view, and value is parse tree node of the view
   * @exception ParseException exception to report back the first cycle detected among the views in
   *            the module
   */
  private void identifyAndReportCycle(Map<String, CreateViewNode> viewNameVsNode)
      throws ParseException {
    // Global stack used to detect cycle between view
    Stack<String> stackOfViews = new Stack<String>();

    for (String viewName : viewNameVsNode.keySet()) {
      traverseViews(viewName, viewNameVsNode, stackOfViews);
    }

    // This method should never reach here.
    throw new FatalInternalError(
        "Topological sort identified a cycle, but indentifyAndReportCycle() method could not find the cycle");
  }

  /**
   * Helper method to traverse the specified view and its dependents to detect cycle among views.
   * 
   * @param viewToTraverse name of the view to be traversed
   * @param viewNameVsViewNode collection of views that could not be sorted topologically
   * @param viewStack global stack used to detect cycle between view
   * @exception ParseException exception to report back the first cycle identified among views
   */
  private void traverseViews(String viewToTraverse, Map<String, CreateViewNode> viewNameVsViewNode,
      Stack<String> viewStack) throws ParseException {
    if (null == viewToTraverse) {
      throw new FatalInternalError("Null viewToTraverse ptr passed to traverseViews");
    }

    if (Constants.DEFAULT_DOC_TYPE_NAME.equals(viewToTraverse)) {
      // SPECIAL CASE: Ignore the Document view, since there is no create view statement to find for
      // it.
      return;
      // END SPECIAL CASE
    }

    CreateViewNode viewToTraverseNode = viewNameVsViewNode.get(viewToTraverse);

    // BEGIN FIX FOR defect
    // If the target view is not in the list that was provided, check whether the target exists but
    // is not in the set of
    // items that was passed to topologicalSort()
    if (null == viewToTraverseNode) {

      // TODO: Clean up the logic below once the refactoring of the catalog lookup functions is
      // complete

      // First check whether it's a table
      if (null != lookupTable(viewToTraverse)) {
        // SPECIAL CASE: The target is a table; tables will not be in the viewNameVsViewNode map
        // and cannot depend on any other views or tables.
        return;
        // END SPECIAL CASE
      }

      // Then check whether it's an imported view.
      {
        try {
          CatalogEntry entry = lookupView(viewToTraverse, null);

          if (entry.isImported()) {
            // SPECIAL CASE: Imported views aren't passed to toplogicalSort() and hence aren't in
            // the viewNameVsViewNode
            // map
            return;
            // END SPECIAL CASE
          }
        } catch (ParseException e) {
          // Lookup failed; flow through
        }
      }

      // There is a missing entry in the viewNameVsViewNode map; we cannot recover from this error.
      throw new FatalInternalError(
          "View %s not found in viewNameVsViewNode map.  Keys in map are %s", viewToTraverse,
          viewNameVsViewNode.keySet());
    }
    // END FIX FOR defect

    int viewAlreadyEncounteredAtIndex = viewStack.indexOf(viewToTraverse);

    // Presence of the specified view to traverse in the global stack, implies cycle among the
    // views, report it
    if (-1 != viewAlreadyEncounteredAtIndex) {
      throw new ExtendedParseException(AQLParserBase.makeException(
          viewToTraverseNode.getViewNameNode().getOrigTok(),
          "A cycle is detected among the following views %s of the module '%s'. Break the cycle, by modifying the definition of either of the views in the cycle.",
          viewStack.subList(viewAlreadyEncounteredAtIndex, viewStack.size()), getModuleName()),
          new File(viewToTraverseNode.getContainingFileName()));
    }

    // Push the view in the global stack, and traverse all its dependents
    viewStack.push(viewToTraverse);
    TreeSet<String> deps = new TreeSet<String>();
    viewToTraverseNode.getBody().getDeps(deps, this);
    for (String dependentView : deps) {
      String qualifiedName = getQualifiedViewOrTableName(dependentView);
      if (null == qualifiedName) {
        // Target view doesn't exist; generate an appropriate error message
        throw new ExtendedParseException(
            AQLParserBase.makeException(viewToTraverseNode.getOrigTok(),
                "View %s depends on view '%s', which does not exist.  "
                    + "Check whether the view name was misspelled "
                    + "or whether view '%s' failed to compile.",
                viewToTraverseNode.getViewName(), dependentView, dependentView),
            new File(viewToTraverseNode.getContainingFileName()));
      }

      traverseViews(getQualifiedViewOrTableName(dependentView), viewNameVsViewNode, viewStack);
    }

    // Pop the view, once the view and its dependents are traversed
    viewStack.pop();
  }

  /**
   * Dump all the views and dictionary creation statements in the catalog to the indicated stream.
   */
  public void dump(PrintWriter stream) {
    try {

      // First, do the create dictionary statements
      for (CreateDictNode node : getCreateDictNodes()) {
        node.dump(stream, 0);
        stream.print("\n");
        stream.print("\n");
      }

      // Next, do the create table statements
      for (CreateTableNode node : getCreateTableNodes()) {
        node.dump(stream, 0);
        stream.print("\n");
        stream.print("\n");
      }

      // Next, do the create function statements
      for (CreateFunctionNode node : getCreateFunctionNodes()) {
        node.dump(stream, 0);
        stream.print("\n");
        stream.print("\n");
      }

      // Next, do the set default dictionary language statement
      if (null != firstSetDefaultDictStmtNode) {
        firstSetDefaultDictStmtNode.dump(stream, 0);
        stream.print("\n");
        stream.print("\n");
      }

      // Next, do the require document with columns statement
      // Note that we dump only the unionized require doc columns parse tree node, and not the
      // individual original parse
      // tree nodes, since we may have many of those and we cannot put them all into a single file.
      getDocumentPlan().getSchemaNode().dump(stream, 0);
      stream.print("\n");
      stream.print("\n");

      // Next, do any detag statements
      for (DetagDocNode node : getDetagDocNodes()) {
        node.dump(stream, 0);
        stream.print("\n");
        stream.print("\n");
      }

      // Next, do the external views
      for (CreateExternalViewNode node : getCreateExternalViewNodes()) {
        node.dump(stream, 0);
        stream.print("\n");
        stream.print("\n");
      }

      // Then do the views that are needed
      for (CreateViewNode node : getRequiredViews()) {
        node.dump(stream, 0);
        stream.print("\n");
        stream.print("\n");
      }

    } catch (ParseException e) {
      throw new RuntimeException(e);
    }
  }

  /** Adapter for {@link #dump(PrintWriter)} */
  public void dump(PrintStream stream) {
    // Dump to a buffer first.
    CharArrayWriter buf = new CharArrayWriter();
    dump(new PrintWriter(buf));
    stream.append(buf.toString());
  }

  /** List of exceptions encountered during AQL parsing and compilation */
  private ArrayList<Exception> compilerExceptions = new ArrayList<Exception>();

  /**
   * Add a compiler exception.
   * 
   * @param exceptions list of compiler exceptions
   */
  public void addCompilerException(Exception e) {
    compilerExceptions.add(e);
  }

  public ArrayList<Exception> getExceptions() {
    return compilerExceptions;
  }

  /** List of warnings encountered during AQL parsing and compilation */
  private ArrayList<CompilerWarning> compilerWarnings = new ArrayList<CompilerWarning>();

  /**
   * Add a compiler warning.
   * 
   * @param w the compiler warning
   */
  public void addCompilerWarning(CompilerWarning w) {
    compilerWarnings.add(w);
  }

  /**
   * @return list of warnings generated during compilation.
   */
  public ArrayList<CompilerWarning> getWarnings() {
    return compilerWarnings;
  }

  /**
   * Compilation summary object that holds Warnings and Exceptions.
   */
  private CompilationSummary summary;

  /**
   * Create the summary object for the given compilation request
   * 
   * @param params
   * @return
   * @throws CompilerException
   */
  public CompilationSummary createCompileSummary(CompileAQLParams params) {
    if (null == summary) {
      summary = new CompilationSummaryImpl(params);
    }
    return summary;
  }

  /**
   * Update the summary object with data from the module just compiled
   * 
   * @return the summary object
   */
  public CompilationSummary updateSummary(String compiledModuleName) {
    summary.updateSummary(compiledModuleName, getNoOfViewsCompiled(), getWarnings());
    return summary;
  }

  /**
   * Return the summary object
   * 
   * @return the summary object
   */
  public CompilationSummary getSummary() {
    return summary;
  }

  // Tokenizer to be used for internal dictionary compilation
  private Tokenizer tokenizer = null;

  public void setTokenizer(Tokenizer tokenizer) {
    this.tokenizer = tokenizer;
  }

  public Tokenizer getTokenizer() {
    return this.tokenizer;
  }

  // System default dictionary matching language set
  private final static String systemDefaultDictLangSet;

  static {
    StringBuilder sb = new StringBuilder();
    for (LangCode langCode : LangCode.DICT_DEFAULT_LANG_CODES) {
      sb.append(langCode);
      sb.append(Constants.COMMA);
    }
    systemDefaultDictLangSet = sb.substring(0, sb.length() - 1);
  }

  // default language set(provided by user thru the 'set default dictionary ...') against which
  // dictionary match should
  // be performed; null value indicates no instance of 'set default dictionary ...' statement in the
  // module
  private SetDefaultDictLangNode firstSetDefaultDictStmtNode = null;

  /**
   * This method will return default dictionary language string; if consumer has explicitly not set
   * the default,the API will return system default {@link LangCode#DICT_DEFAULT_LANG_CODES}.
   */
  public String getDefaultDictLangStr() {
    if (null != firstSetDefaultDictStmtNode)
      return firstSetDefaultDictStmtNode.getDefaultLangString();
    else
      return systemDefaultDictLangSet;
  }

  /**
   * Method to set default language string; this method will throw exception if language string is
   * already set.
   * 
   * @param node parse tree node for 'set default dictionary ...' statement
   */
  public void setDefaultDictLang(SetDefaultDictLangNode node) throws ParseException {
    // In a module only one instance of 'set default dictionary ...' is allowed
    if (null != firstSetDefaultDictStmtNode) {
      String errMsg = String.format(
          "Already encountered a 'set default dictionary ...' in aql file '%s', line number %d, of module '%s'; Only one such statement is allowed per module.",
          firstSetDefaultDictStmtNode.getContainingFileName(),
          firstSetDefaultDictStmtNode.getOrigTok().beginLine,
          firstSetDefaultDictStmtNode.getModuleName());
      throw new ParseException(errMsg);
    }

    try {
      // first validate
      validateDefaultLangStr(node.getDefaultLangString());
    } catch (IllegalArgumentException e) {
      // Throw parse error with proper error locatoin
      throw AQLParserBase.makeException(e.getMessage(), node.getOrigTok());
    }

    // set first occurrence of 'set default dictionary ..' in catalog
    this.firstSetDefaultDictStmtNode = node;
  }

  /**
   * Method to validate the language code string passed through the 'set default dictionary ...'
   * statement.
   * 
   * @param defaultDictLangStr
   * @exception IllegalArgumentException
   */
  private void validateDefaultLangStr(String defaultDictLangStr) {
    // Validate: Given language codes string should be non empty
    if (defaultDictLangStr.trim().length() == 0) {
      throw new IllegalArgumentException("No language code specified.");
    }

    // Validate: Given language codes string should contain only the allowed number of language
    // codes and supported
    // language codes
    LangCode.validateLangStr(defaultDictLangStr);
  }

  /**
   * @param alias one of the local names of a view or table
   * @return the fully-qualified, canonical name of the view or table, or NULL if no table with the
   *         indicated alias exists
   */
  public String getQualifiedViewOrTableName(String alias) {
    return viewTableNamespace.getCanonicalNameFromAlias(alias);
  }

  public String getQualifiedFuncName(String alias) {
    return functionNamespace.getCanonicalNameFromAlias(alias);
  }

  public String getQualifiedDictName(String name) {
    return dictNamespace.getCanonicalNameFromAlias(name);
  }

  /**
   * @return the modulePath
   */
  public String getModulePath() {
    return modulePath;
  }

  /**
   * Sets the path where modules can be located.
   * 
   * @param modulePath A semicolon separated list of URIs that refer to absolute paths to locations
   *        where modules can be located.
   */
  public void setModulePath(String modulePath) {
    this.modulePath = modulePath;
  }

  /**
   * Sets the path where .tam files are written out to during the process of compilation.
   * 
   * @param dirURI Absolute path of the temporary directory. Only file: URIs are supported at
   *        present.
   */
  public void setCompilationTempDir(String dirURI) {
    this.compilationTempDir = dirURI;
  }

  /**
   * Returns the URI string of temp directory used for compilation
   * 
   * @return compilationTempDir
   */
  public String getCompilationTempDir() {
    return compilationTempDir;
  }

  public ModuleMetadata lookupMetadata(String moduleName) {
    return nameToMetadata.get(moduleName);
  }

  /**
   * @return <code>true</code>, if the given view name is imported using any of the three forms of
   *         import statement; false otherwise
   */
  public boolean isImportedView(String viewName) throws ParseException {
    CatalogEntry view = lookupView(viewName, null);
    return (view instanceof ViewCatalogEntry || view instanceof ExternalViewCatalogEntry)
        && view.isImported();
  }

  /**
   * @return <code>true</code>, if the given table name is imported using any of the three forms of
   *         import statement; false otherwise
   */
  public boolean isImportedTable(String tabName) throws ParseException {
    CatalogEntry table = lookupTable(tabName);
    return null != table && table.isImported();
  }

  /**
   * @return <code>true</code>, if the given dictionary name is imported using any of the three
   *         forms of import statement; false otherwise
   * @throws ParseException
   */
  public boolean isImportedDict(String dictName) throws ParseException {
    DictCatalogEntry dict = lookupDict(dictName, null, null);
    return null != dict && dict.isImported();
  }

  /**
   * @return <code>true</code>, if the given function name is imported using any of the three forms
   *         of import statement; <code>false</code> otherwise
   * @throws ParseException if the given function name is not declared
   */
  public boolean isImportedScalarFunc(String funcName) throws ParseException {
    ScalarFuncCatalogEntry scalarFunc = lookupScalarFunc(funcName);
    // We don't have the handle to error location, hence this method return exception without error
    // location; it is
    // responsibility of invoker to populate error location.
    if (null == scalarFunc) {
      throw new ParseException(String.format("Scalar function '%s' is not declared", funcName));
    }
    return scalarFunc.isImported();
  }

  /**
   * @return <code>true</code>, if the given function name is imported using any of the three forms
   *         of import statement; <code>false</code> otherwise
   * @throws ParseException if the given function name is not declared
   */
  public boolean isImportedTableFunc(String funcName) throws ParseException {
    TableFuncCatalogEntry entry = lookupTableFunc(funcName);
    // We don't have the handle to error location, hence this method return exception without error
    // location; it is
    // responsibility of invoker to populate error location.
    if (null == entry) {
      throw new ParseException(String.format("Table function '%s' is not declared", funcName));
    }
    return entry.isImported();
  }

  /**
   * @param viewName fully-qualified name of a view
   * @param errLoc location in the AQL code in which any view lookup errors should be reported
   * @return true if the view exists and is an output view false otherwise
   * @throws ParseException
   */
  public boolean isOutputView(String viewName, Token errLoc) throws ParseException {
    CatalogEntry ce = lookupView(viewName, errLoc);

    // lookupView () can never return null; but it can return TableCatalogEntry, hence this
    // condition is required
    if (!(ce instanceof ViewCatalogEntry || ce instanceof DetagCatalogEntry
        || ce instanceof ExternalViewCatalogEntry)) {
      // Not a catalog entry for view.
      // TODO: Should we really be returning false (as opposed to throwing an exception) here? I
      // guess we should return
      // a false - wdyt Laura
      return false;
    }

    // If we get here, the view name is valid. Look for it in the output names list.

    // outputNameOrder is a list of pairs, where the first element of each pair is the view name and
    // the second element
    // is the output name.
    for (Pair<String, String> p : outputNameOrder) {
      if (p.first.equals(viewName)) {
        // Found it
        return true;
      }
    }

    // If we get here, didn't find the name in the list of output views
    return false;
  }

  /**
   * Set the comment for this module.
   * 
   * @param comment
   */
  public void setComment(AQLDocComment comment) {
    this.comment = comment;
  }

  /**
   * Get the module comment.
   * 
   * @return the comment for the module as read from the
   *         {@link AQLDocComment#MODULE_AQL_DOC_COMMENT_FILE_NAME}; <code>null</code> if the module
   *         comment file does not exist or could not be processed.
   */
  public AQLDocComment getComment() {
    return comment;
  }

  /**
   * Method to add dummy catalog entry for imported function node.
   * 
   * @param qualifiedFuncName qualified imported function name
   * @param alias reference (qualified or unqualified) to the qualifiedFuncName
   * @param fmd meta-data for the qualified imported name
   * @param node original parse tree node for the import statement
   * @throws ParseException
   */
  private void addDummyFuncCatalogEntry(String qualifiedFuncName, String alias,
      FunctionMetadata fmd, ImportNode node) throws ParseException {
    AbstractFuncCatalogEntry entry;

    // Figure out if this function is a table function
    String retTypeStr = fmd.getReturnType();
    FieldType retType = FieldType.stringToFieldType(retTypeStr);
    if (retType.getIsTableType()) {
      entry = new TableUDFCatalogEntry(fmd, node, this);
    } else {
      entry = new ScalarUDFCatalogEntry(fmd, node);
    }

    if (debugFunc) {
      Log.debug(
          "addDummyFuncCatalogEntry() adding catalog entry "
              + "of type %s for function %s with alias %s",
          entry.getClass().getName(), qualifiedFuncName, alias);
    }

    functionNamespace.add(alias, qualifiedFuncName, entry, CatalogEntryTypeName.FUNCTION,
        node.getOrigTok(), true);
  }

  /**
   * Return the mapping between AQL file name and the parse tree nodes defined in that file.
   * 
   * @return the file2Nodes mappping
   */
  public Map<String, java.util.List<AQLParseTreeNode>> getFile2NodesMapping() {
    return file2Nodes;
  }

  /**
   * Set the mapping between AQL file name and the parse tree nodes defined in that file.
   * 
   * @param file2Nodes the file2Nodes to set
   */
  public void setFile2NodesMapping(Map<String, java.util.List<AQLParseTreeNode>> file2Nodes) {
    this.file2Nodes = file2Nodes;
  }

  /**
   * @return true, if the given name is same as external_name for one of the visible external view,
   *         otherwise false
   */
  public boolean isExternalViewExternalName(String name) {
    ArrayList<CreateExternalViewNode> createExternalViewNodes = getCreateExternalViewNodes();
    for (CreateExternalViewNode cevn : createExternalViewNodes) {
      if (name.equals(cevn.getExternalName())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the name of the module being compiled
   * 
   * @return the name of the module being compiled
   */
  public String getModuleName() {
    return moduleName;
  }

  /**
   * Sets the name of the module being compiled
   * 
   * @param moduleName name of the module being compiled
   */
  public void setModuleName(String moduleName) {
    this.moduleName = moduleName;
  }

  /**
   * Returns a list of modules required to compile the current module.
   * 
   * @return list of module names that the current module depends on
   */
  public java.util.List<String> getRequiredModules() {
    return dependsOnList;
  }

  /**
   * @return the number of user-declared views compiled in the module<br />
   *         Returns -1 if an exception is encountered.
   */
  public int getNoOfViewsCompiled() {
    int noOfViewsCompiled = 0;
    try {
      noOfViewsCompiled = getRequiredViews().size();
    } catch (ParseException e) {
      return -1;
    }
    return noOfViewsCompiled;
  }

  public void setSummary(CompilationSummaryImpl summary) {
    this.summary = summary;
  }

}
