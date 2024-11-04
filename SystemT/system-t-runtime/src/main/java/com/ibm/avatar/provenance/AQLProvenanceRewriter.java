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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;

import com.ibm.avatar.algebra.consolidate.ConsolidateImpl;
import com.ibm.avatar.algebra.consolidate.PartialOrder;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.function.agg.List;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.algebra.function.predicate.ContainedWithin;
import com.ibm.avatar.algebra.function.predicate.Contains;
import com.ibm.avatar.algebra.function.predicate.Equals;
import com.ibm.avatar.algebra.function.predicate.GreaterThan;
import com.ibm.avatar.algebra.function.predicate.Or;
import com.ibm.avatar.algebra.function.predicate.Overlaps;
import com.ibm.avatar.algebra.function.scalar.AutoID;
import com.ibm.avatar.algebra.function.scalar.GetBegin;
import com.ibm.avatar.algebra.util.file.FileOperations;
import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.api.CompilationSummary;
import com.ibm.avatar.api.CompilationSummaryImpl;
import com.ibm.avatar.api.CompileAQLParams;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.exceptions.CompilerException;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.api.exceptions.InvalidCompileParamException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.api.tam.ModuleMetadata;
import com.ibm.avatar.api.tam.ModuleMetadataFactory;
import com.ibm.avatar.aql.AQLParseTreeNode;
import com.ibm.avatar.aql.ColNameNode;
import com.ibm.avatar.aql.ConsolidateClauseNode;
import com.ibm.avatar.aql.CreateTableNode;
import com.ibm.avatar.aql.CreateViewNode;
import com.ibm.avatar.aql.DictExNode;
import com.ibm.avatar.aql.ExtractListNode;
import com.ibm.avatar.aql.ExtractNode;
import com.ibm.avatar.aql.FromListItemNode;
import com.ibm.avatar.aql.FromListItemSubqueryNode;
import com.ibm.avatar.aql.FromListItemTableFuncNode;
import com.ibm.avatar.aql.FromListItemViewRefNode;
import com.ibm.avatar.aql.FromListNode;
import com.ibm.avatar.aql.GroupByClauseNode;
import com.ibm.avatar.aql.ImportModuleNode;
import com.ibm.avatar.aql.ImportViewNode;
import com.ibm.avatar.aql.IntNode;
import com.ibm.avatar.aql.MinusNode;
import com.ibm.avatar.aql.NickNode;
import com.ibm.avatar.aql.OutputViewNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.PredicateNode;
import com.ibm.avatar.aql.RValueNode;
import com.ibm.avatar.aql.RegexExNode;
import com.ibm.avatar.aql.ScalarFnCallNode;
import com.ibm.avatar.aql.SelectListItemNode;
import com.ibm.avatar.aql.SelectListNode;
import com.ibm.avatar.aql.SelectNode;
import com.ibm.avatar.aql.StringNode;
import com.ibm.avatar.aql.TableFnCallNode;
import com.ibm.avatar.aql.TableLocatorNode;
import com.ibm.avatar.aql.TableUDFCallNode;
import com.ibm.avatar.aql.UnionAllNode;
import com.ibm.avatar.aql.ViewBodyNode;
import com.ibm.avatar.aql.WhereClauseNode;
import com.ibm.avatar.aql.catalog.AbstractRelationCatalogEntry;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.aql.catalog.CatalogEntry;
import com.ibm.avatar.aql.catalog.DetagCatalogEntry;
import com.ibm.avatar.aql.compiler.ParseToCatalog;
import com.ibm.avatar.aql.planner.SchemaInferrer;
import com.ibm.avatar.aql.tam.ModuleUtils;
import com.ibm.avatar.logging.Log;

public class AQLProvenanceRewriter extends AQLRewriter {

  public static boolean debug = false;

  /** Constants */
  public static final String ID_SUFFIX = "__id";
  public static final String AUTO_ID_ALIAS = "__auto__id";
  public static final String CONSOLIDATE_TARGET_ID = "__consolidate__target";

  public static final String STMT_TYPE_ALIAS = "__stmt__type";
  public static final String DISPLAY_NAME_ALIAS = "__display__name";
  public static final String STMT_TYPE_UNION = "UNION";
  public static final String STMT_TYPE_MINUS = "MINUS";
  public static final String STMT_TYPE_SELECT = "SELECT";
  public static final String STMT_TYPE_CONSOLIDATE = "CONSOLIDATE";
  public static final String STMT_TYPE_EXTRACT_REGEX = "EXTRACT REGEX";
  public static final String STMT_TYPE_EXTRACT_DICT = "EXTRACT DICTIONARY";
  public static final String STMT_TYPE_TABLE_FUNC = "TABLE FUNCTION";

  public enum StmtType {
    STMT_TYPE_UNION, STMT_TYPE_MINUS, STMT_TYPE_SELECT, STMT_TYPE_EXTRACT_REGEX, STMT_TYPE_EXTRACT_DICT, STMT_TYPE_CONSOLIDATE, STMT_TYPE_TABLE_FUNC
  }

  // The auto id attribute name has the format:
  // <viewName><separator><viewAlias>__id
  public static final String AUTO_ID_ATTRIBUTE_VIEW_SEPARATOR = "_____sep_____";
  public static final String AUTO_ID_ATTRIBUTE_FORMAT =
      "%s" + AUTO_ID_ATTRIBUTE_VIEW_SEPARATOR + "%s__%s";

  public static final String UNION_OPERAND_TEMP_VIEW_NAME_FORMAT = "__Union__%s__TempOp__%s";
  public static final String BASE_TEMP_VIEW_NAME_FORMAT = "__Base__%s__Temp";
  public static final String MINUS_OPERAND_TEMP_VIEW_NAME_FORMAT = "__Minus__%s__TempOp__%s";
  public static final String SUBQUERY_TEMP_VIEW_NAME_FORMAT = "__Subquery__%s__Temp";
  public static final String CONSOLIDATE_TEMP_VIEW_NAME_FORMAT = "__Consolidate__%s__Temp__%s";
  public static final String TABLE_FUNC_TEMP_VIEW_NAME_FORMAT = "__TableFunction__%s__Temp";
  public static final String TABLE_LOCATOR_TEMP_VIEW_NAME_FORMAT = "__TableLocator__%s__Temp";
  public static final String MINUS_TEMP_VIEW_NAME_FORMAT = "__Minus__%s__Temp";
  public static final String TEMP_TUPLE_VAR = "__t";
  public static final String WRAPPED_OP_ALIAS = "Op";
  public static final String UNION_DEFAULT_ID = "__union_op" + ID_SUFFIX;
  public static final int DEFAULT_DOC_ID = -1;

  public final TempViewIDGenerator tempViewIDGenerator = new TempViewIDGenerator();

  private class TempViewIDGenerator {
    private int id = 0;

    public int getNextID() {
      return ++id;
    }

    public int getCurrentID() {
      return id;
    }
  }

  /** Mapping from original view body statement and its exact copy */
  private HashMap<ViewBodyNode, ViewBodyNode> original2CopyViewBodyMap;

  /** Mapping from rewritten view name to copy of corresponding original view body statement */
  private final HashMap<String, ViewBodyNode> rewritten2OriginalCopyMap =
      new HashMap<String, ViewBodyNode>();

  public HashMap<ViewBodyNode, ViewBodyNode> getOriginal2CopyViewBodyMap() {
    return original2CopyViewBodyMap;
  }

  public HashMap<String, ViewBodyNode> getRewritten2OriginalCopyMap() {
    return rewritten2OriginalCopyMap;
  }

  /**
   * Base views for which we cannot or should not compute provenance. Contains both qualified and
   * unqualified names, for modular AQL, and unqualified names for AQL v1.4.
   */
  public ArrayList<String> basicViews = new ArrayList<String>();

  /**
   * Base views that are either a detag statement view name, or any auxiliary views generated by a
   * detag statement for which we cannot or should not compute provenance. Contains both qualified
   * and unqualified names, for modular AQL, and unqualified names for AQL v1.4.
   */
  private final ArrayList<String> basicDetagViews = new ArrayList<String>();

  /**
   * List of imported views in the context of a single file that are imported from a module whose
   * source is not to be rewritten. Contains either the local alias, if the view is imported using
   * local alias, or the fully qualified view name if the view is imported without a local alias.
   * This is always empty for non-modular AQL, since non-modular AQL does not allow compiled module
   * dependencies. Used to determine which imported views are to be treated as a base views. Used to
   * be in the context of a single file, but is now at the level of the entire module, since we
   * didn't respect the original design document and made the scope of imports the entire module, as
   * opposed to a single AQL file.
   */
  private final ArrayList<String> localImportedBaseViews = new ArrayList<String>();

  /**
   * Global list of exported views, from all modules, that could not be rewritten. Contains fully
   * qualified view names for modular AQL, and it is empty for non-modular AQL. Kept around so that
   * once we rewrite a module, we remember the exported views that could not be rewritten, so that
   * we can determine later on whether views (in other modules) that depend on this one can copy the
   * AutoID attribute from the exported view or need to create a new attribute.
   */
  private final ArrayList<String> globalExportedBaseViews = new ArrayList<String>();

  /**
   * Map from table function call string representation of table locator name to name of auxiliary
   * view created for that function call or table locator to support provenance rewrite. Exists such
   * that for identical table function calls or table locators we do not create redundant copies of
   * such auxiliary views. For example, when rewriting the function call
   * 
   * <pre>
   * MyTableFunc(3, View1)
   * </pre>
   * 
   * <br/>
   * the provenance rewrite will create the auxiliary view: <br/>
   * 
   * <pre>
   * create view AuxView as
   * select T.*, AutoID() as id, 'TABLE_FUNC' as stmtType
   * from MyTableFunc(3, View1) T;
   * </pre>
   * 
   * The mapping will have one entry MyTableFunc(3, View1) -> AuxView. The mapping will contain this
   * single entry, regardless of how many times the table function call appears in the current
   * module. Once the auxiliary view is created, it will be reused when rewriting statements that
   * contain the same table function call in the same module.
   */
  private final TreeMap<String, String> tableFnOrLoc2AuxViewName = new TreeMap<String, String>();

  private void initBaseViews(ArrayList<String> views) {
    if (views != null)
      basicViews = new ArrayList<String>(views);

  }

  private void addBaseView(String viewName) {
    basicViews.add(viewName);
  }

  public ArrayList<String> getBaseViews() {
    return basicViews;
  }

  public boolean isBaseView(String viewName) {
    return basicViews.contains(viewName);
  }

  private void addBaseDetagView(String viewName) {
    basicDetagViews.add(viewName);
  }

  private boolean isBaseDetagView(String viewName) {
    return basicDetagViews.contains(viewName);
  }

  private void initAuxViews() {
    tableFnOrLoc2AuxViewName.clear();
  }

  public static void main(String[] args) throws Exception {
    final String USAGE = "Usage: java %s inputAqlFile outputAqlFile\n";
    if (args.length != 2) {
      System.err.printf(USAGE, AQLProvenanceRewriter.class.getName());
      return;
    }

    // Parse the arguments.
    File inputAqlFile = new File(args[0]);
    File outputAqlFile = new File(args[1]);

    // Default data path
    String parentDir = inputAqlFile.getParent();

    // Prepare compile parameters
    // FIXME: Now that outputFile is removed from CompileAQLParams, AQLProvenanceRewriter should use
    // alternate way of
    // passing rewrittenAQLFile parameter to rewriteAQL() method. Temporarily passing some arbitrary
    // value to allow
    // compiler to succeed.
    CompileAQLParams params = new CompileAQLParams(inputAqlFile,
        outputAqlFile.getParentFile().toURI().toString(), parentDir);

    // Rewrite
    AQLProvenanceRewriter rewriter = new AQLProvenanceRewriter();
    rewriter.rewriteAQL(params, null);
  }

  /**
   * Deprecated method to rewrite to a string. No longer supported and always throws a runtime
   * exception. Keep here for now because removing it breaks a lot of code in the AQL Refiner code.
   * Use instead {@link #rewriteAQL(CompileAQLParams, ArrayList)}.
   * 
   * @param params
   * @param baseViews
   * @return
   */
  @Deprecated
  public String rewriteAQLToStr(CompileAQLParams params, ArrayList<String> baseViews) {
    throw new RuntimeException("Rewriting AQL to string no longer supported.");
  }

  /**
   * Deprecated method to rewrite a String. No longer supported and always throws a runtime
   * exception. Keep here for now because removing it breaks Eclipse Tooling code. Use instead
   * {@link #rewriteAQL(CompileAQLParams, ArrayList)}.
   * 
   * @param aql
   * @param searchPath
   * @param baseViews
   * @return
   */
  @Deprecated
  public String rewriteAQLString(String aql, String searchPath, ArrayList<String> baseViews) {
    throw new RuntimeException("Rewriting AQL string no longer supported.");
  }

  /**
   * Main-entry point to rewrite AQL code, modular, or non-modular (backward compatibility). To
   * rewrite non-modular AQL code, use a CompileAQLParams object with the following settings:
   * <ul>
   * <li>inputFile – Indicates the file on local file system that contains the top-level AQL file of
   * the extractor</li>
   * <li>dataPath – Indicates the list, separated by semi-colon (;), of paths to directories with
   * respect to which included AQL files, dictionaries and UDF jars are resolved</li>
   * <li>outputURI – URI containing path to a directory. When rewriting completes, the directory
   * contains one directory called genericModule, containing the complete rewritten AQL code. Other
   * forms of outputURI other than a directory are not supported.</li>
   * </ul>
   * To rewrite modular AQL code, use a CompileAQLParams object with the following settings:
   * <ul>
   * <li>inputModules – array of URIs, where each URI points to the top-level source directory of a
   * module (if you wish to rewrite a module called moduleX, then the corresponding URI would point
   * to a folder /path/to/moduleX). We currently plan to support file: and hdfs: URIs.
   * <li>modulePath - List, separated by semi-colon (;), of paths to directories or .jar/.zip
   * archives where the compiled representation (.tam) of modules referenced in the input modules
   * are to be found. Compiled modules are searched inside both directories and archives specified
   * in this path.</li>
   * <li>outputURI – URI containing path to a directory. When rewriting completes, the directory
   * contains one directory for each input module, containing the complete rewritten AQL code of
   * that module. Other forms of outputURI other than a directory are not supported.</li>
   * </ul>
   * 
   * @param params parameters indicating the location of the AQL code that must be rewritten, where
   *        to dump the output, and the encoding for reading input AQL code, and writing output
   *        rewritten AQL code. Settings for the following parameters are ignored: performSDM,
   *        performSRM, perform RSR.
   * @param baseViews list of base views that should not be rewritten
   * @throws Exception
   */
  public void rewriteAQL(CompileAQLParams params, ArrayList<String> baseViews) throws Exception {

    // Validate the parameters
    validateParams(params);
    CompilationSummary summary = new CompilationSummaryImpl(params);

    ArrayList<Exception> errors = new ArrayList<Exception>();
    HashMap<String, String> aqlFilesMap = new HashMap<String, String>();

    // Rewrite non-modular AQL (backward compatibility)
    if (params.isBackwardCompatibilityMode()) {

      // Make a copy of the original parameters
      CompileAQLParams bcParams = (CompileAQLParams) params.clone();

      // If data path is null, set it to AQL's parent directory
      String datapath = params.getDataPath();
      if (datapath == null || datapath.trim().length() == 0) {
        if (params.getInputFile() != null) {
          bcParams.setDataPath(params.getInputFile().getParentFile().getPath());
        } else {
          bcParams.setDataPath(".");
        }
      }

      File genModuleDir = null;
      try {
        // Create a generic module
        genModuleDir = ModuleUtils.createGenericModule(params, aqlFilesMap, errors);

        // Throw any compilation errors encountered
        if (errors.size() > 0) {
          CompilerException ce = new CompilerException(summary);
          for (Exception exception : errors) {
            ce.addError(exception);
          }
          throw ce;
        }

        // If we get here, the generic module was created successfully. Set the input to the
        // location of the new module,
        // and reset the original inputFIle and inputStr parameters
        String genericModuleURI = genModuleDir.toURI().toString();
        bcParams.setInputFile(null);
        bcParams.setInputStr(null);
        bcParams.setInputModules(new String[] {genericModuleURI});

        // Rewrite the newly created module
        rewriteModule(bcParams, Arrays.asList(new String[] {Constants.GENERIC_MODULE_NAME}),
            baseViews);
      } finally {
        // delete the temporary directory containing the generic module
        if (null != genModuleDir) {
          FileUtils.deleteDirectory(genModuleDir.getParentFile());
        }
      }

    } // end: backward compatibility code
    else {

      // Put the modules in the right order for compilation; the order is important because we need
      // to determine if an
      // exported view cannot be rewritten (because for example it is a detag view) and mark it as
      // base view so that
      // we can subsequently rewrite views in other modules that depend on the exported view.
      if (debug) {
        Log.debug("Preparing compilation order ...");
      }
      CompilerException compilerErrors = new CompilerException(summary);
      String[] inputModules = null;

      inputModules = ModuleUtils.prepareCompileOrder(params.getInputModules(),
          params.getModulePath(), compilerErrors);

      if (compilerErrors.getAllCompileErrors().size() > 0)
        throw compilerErrors;

      // Get the list of all module names to rewrite
      ArrayList<String> srcModuleNames = new ArrayList<String>();
      for (int i = 0; i < inputModules.length; i++) {
        String moduleURI = inputModules[i];
        File moduleDir = new File(new URI(moduleURI));
        String moduleName = moduleDir.getName();
        srcModuleNames.add(moduleName);
      }

      // Rewriting modular AQL: rewrite each module in turn
      for (int i = 0; i < inputModules.length; i++) {

        // Rewrite each module in turn, therefore we need to override the input module field so that
        // it contains just
        // the current module. Otherwise we end up with a catalog populated with all elements from
        // all modules.
        String moduleName = inputModules[i];
        CompileAQLParams clonedParams = (CompileAQLParams) params.clone();
        clonedParams.setInputModules(new String[] {moduleName});
        rewriteModule(clonedParams, srcModuleNames, baseViews);
      }
    }
  }

  /*
   * PRIVATE METHODS
   */

  /**
   * Performs parameter validation as follows:
   * <ul>
   * <li>Validation specific to CompileAQLParams: at least one of inputFile, inputStr and
   * inputModules is non-null; inputFile and inputModules are mutually exclusive; outputURI is
   * non-null;</li>
   * <li>outputURI is a directory.</li>
   * </ul>
   * 
   * @param params
   * @throws InvalidCompileParamException
   */
  private void validateParams(CompileAQLParams params) throws InvalidCompileParamException {
    // Validation specific to CompileAQLParams: at least one of inputFile, inputStr and inputModules
    // is non-null;
    // inputFile and inputModules are mutually exclusive; outputURI is non-null
    params.validate();
    CompilationSummary summary = new CompilationSummaryImpl(params);

    // In addition, the outputURI must point to a directory
    InvalidCompileParamException errors = new InvalidCompileParamException(summary);
    try {
      String outputURI = params.getOutputURI();

      // Verify that outputURI is a valid directory.
      if (false == FileOperations.isDirectory(outputURI)) {
        errors.addError(new Exception(String
            .format("Parameter 'outputURI' should point to an existing directory: %s", outputURI)));
      }
    } catch (Exception e) {
      // Something went wrong during outputURI validation
      errors.addError(new Exception(String.format(
          "Exception encountered when instantiating outputURI: %s", params.getOutputURI()), e));
    }

    if (0 != errors.getAllCompileErrors().size())
      throw errors;
  }

  /**
   * Rewrite a single module.
   * 
   * @param params parameters indicating the location of all the AQL code that must be rewritten,
   *        and where to dump the output
   * @param srcModuleNames list of all source module names that are to be rewritten; used to
   *        determine which imported views in the current module belong to non-src modules and
   *        therefore must be treated as base views
   * @param baseViews list of base views that are not to be rewritten
   * @throws URISyntaxException
   * @throws UnsupportedEncodingException
   * @throws FileNotFoundException
   * @throws Exception
   */
  private void rewriteModule(CompileAQLParams params, java.util.List<String> srcModuleNames,
      ArrayList<String> baseViews)
      throws URISyntaxException, UnsupportedEncodingException, FileNotFoundException, Exception {
    // The location of the module (we only have a single module at this point)
    String moduleURI = params.getInputModules()[0];
    File moduleDir = new File(new URI(moduleURI));

    if (debug) {
      Log.debug("Rewriting module '%s'", moduleURI);
    }

    // The name of the module
    String moduleName = moduleDir.getName();

    // Top-level folder of the rewritten module is a directory located directly under the outputURI,
    // having the same
    // name as the module name
    File rewrittenModuleDir =
        new File(new URI(String.format("%s/%s", params.getOutputURI(), moduleName)));
    rewrittenModuleDir.mkdirs();

    // Parse the AQL and generate the catalog
    ParseToCatalog catalogGenerator = new ParseToCatalog();
    Catalog catalog = catalogGenerator.parse(params);

    // Initialize the base views
    initBaseViews(baseViews);

    // Initialize the auxiliary views
    initAuxViews();

    // Set detag views as base views
    setDetagAsBaseViews(catalog);

    // Set lookup tables as base views
    setLookupTableAsBaseViews(catalog);

    // We only rewrite the required views (i.e., views that are either output or exported views)
    ArrayList<CreateViewNode> requiredViews = catalog.getRequiredViews();
    HashSet<CreateViewNode> requiredViewsSet = new HashSet<CreateViewNode>();
    requiredViewsSet.addAll(requiredViews);

    // Infer the schema of the required views.
    SchemaInferrer.inferTypes(catalog, requiredViews);

    // Make a copy of the required views, making views with unsupported constructs base views in the
    // process
    if (debug)
      Log.debug("Copying views ... ");
    AQLViewCopier copier = new AQLViewCopier(catalog);
    copier.addUnsupportedViews(basicViews);
    original2CopyViewBodyMap = copier.makeCopy(requiredViews);
    initBaseViews(copier.getUnsupportedViews());

    // Rewrite the required views
    if (debug)
      Log.debug("Rewriting views ... ");

    // The rewrite happens in the context of each AQL file
    Map<String, java.util.List<AQLParseTreeNode>> file2Node = catalog.getFile2NodesMapping();

    // Sort the input AQL files in a case insensitive fashion, to ensure consistent view name
    // generation across Linux
    // and Windows.
    ArrayList<String> fileNames = new ArrayList<String>();
    fileNames.addAll(file2Node.keySet());

    Collections.sort(fileNames, new Comparator<String>() {
      @Override
      public int compare(String a, String b) {
        return a.toUpperCase().compareTo(b.toUpperCase());
      }
    });

    // First pass: Iterate through each AQL file of the module to get the list of imported and
    // exported views
    localImportedBaseViews.clear();
    for (String fileName : fileNames) {

      // The set of parse tree nodes of this AQL file
      java.util.List<AQLParseTreeNode> origParseTreeNodes = file2Node.get(fileName);

      // Add to the list of local imported views that are coming from a compiled module
      addLocalImportedBaseViews(params, origParseTreeNodes, srcModuleNames);
    }

    // Second pass: Iterate through each AQL file of the module and rewrite it
    for (String fileName : fileNames) {

      // The set of parse tree nodes of this AQL file
      java.util.List<AQLParseTreeNode> origParseTreeNodes = file2Node.get(fileName);

      // Rewrite all required views from this AQL file
      ArrayList<CreateViewNode> localRequiredViews = new ArrayList<CreateViewNode>();
      for (AQLParseTreeNode node : origParseTreeNodes) {
        if (node instanceof CreateViewNode && requiredViewsSet.contains(node))
          localRequiredViews.add((CreateViewNode) node);
      }
      ArrayList<CreateViewNode> rewrittenViews = rewriteViews(localRequiredViews, catalog);

      // Callback to rewrite all scalar functions with table locators as arguments
      scalarFnCallTableLocatorCB fcb = new scalarFnCallTableLocatorCB();
      fcb.catalog = catalog;
      fcb.rewrittenViews = rewrittenViews;

      // Make a copy of the original rewrittenViews, since the rewrite will add to the original list
      ArrayList<CreateViewNode> rewrittenViewsCopy = new ArrayList<CreateViewNode>(rewrittenViews);

      for (CreateViewNode cvn : rewrittenViewsCopy) {
        rewriteScalarFuncs(fcb, cvn);
      }

      tableFnCallTableLocatorCB tcb = new tableFnCallTableLocatorCB();
      tcb.catalog = catalog;
      tcb.rewrittenViews = rewrittenViews;

      for (CreateViewNode cvn : rewrittenViewsCopy) {
        rewriteTabFuncs(tcb, cvn);
      }

      // Dump the required views into the rewritten version of this AQL file
      File rewrittenAqlFile = new File(rewrittenModuleDir, fileName);
      if (debug)
        Log.debug("Writing rewritten views to " + rewrittenAqlFile.getAbsolutePath());
      Writer outWriter =
          new OutputStreamWriter(new FileOutputStream(rewrittenAqlFile), params.getInEncoding());
      outputViews(moduleName, rewrittenViews, catalog, outWriter, origParseTreeNodes);
    }

    // Copy the non-aql files from the module directory to the rewritten module directory
    if (params.getInputModules() != null) {
      copyReferencedResources(moduleURI, rewrittenModuleDir);
    }

    // Finally, remember the schema of the detag exported views because we will use it later to
    // decide when to create a
    // new AutoID node, and when to pull an existing one; we keep only the detag (main and
    // auxiliary) views, because
    // these are the only views that we don't rewrite, whereas all other views are wrapped as base
    // statements
    for (DetagCatalogEntry entry : catalog.getDetagCatalogEntries()) {
      globalExportedBaseViews.add(entry.getName());
    }
  }

  /**
   * Determine which of the imported views from the input list of ParseTreeNodes refers to an
   * artifact from a module that is not among the list of source modules. Call this method once for
   * each AQL file of a module to be rewritten.
   * 
   * @param origParseTreeNodes
   * @param srcModuleNames
   * @throws ProvenanceRewriteException
   */
  private void addLocalImportedBaseViews(CompileAQLParams params,
      java.util.List<AQLParseTreeNode> origParseTreeNodes, java.util.List<String> srcModuleNames)
      throws ProvenanceRewriteException {

    // Iterate through the import view statements and add views imported from a compiled module
    for (AQLParseTreeNode node : origParseTreeNodes) {
      if (node instanceof ImportViewNode) {
        ImportViewNode ivn = (ImportViewNode) node;
        String moduleName = ivn.getFromModule().getNickname();
        if (debug)
          Log.debug("Found imported view from module %s", moduleName);

        if (!srcModuleNames.contains(moduleName)) {
          // Found a view imported from a compiled module (not to be rewritten). Add it to the list!
          String viewName = (null != ivn.getAlias() ? ivn.getAlias().getNickname()
              : moduleName + "." + ivn.getNodeName().getNickname());
          if (debug)
            Log.debug("Adding imported view %s", viewName);
          localImportedBaseViews.add(viewName);
        }
      } else if (node instanceof ImportModuleNode) {
        ImportModuleNode imn = (ImportModuleNode) node;
        String moduleName = imn.getImportedModuleName().getNickname();

        // Get the list of exported views from the module and add them to the set of local base
        // views, but only if the
        // module is not among the source modules. Essentially, if the module is a compiled module,
        // then we cannot
        // rewrite it, and so we treat it as a base view.
        if (!srcModuleNames.contains(moduleName)) {
          try {
            ModuleMetadata metadata =
                ModuleMetadataFactory.readMetaData(moduleName, params.getModulePath());

            String[] exportedViews = metadata.getExportedViews();
            for (int i = 0; i < exportedViews.length; i++) {
              localImportedBaseViews.add(exportedViews[i]);
            }
          } catch (TextAnalyticsException e) {
            throw new ProvenanceRewriteException(e,
                "Cannot read the metadata of imported module '%s'", moduleName);
          }
        }
      }
    }

  }

  /**
   * Copy all non-aql files from source to dest
   * 
   * @param string
   * @param parentFile
   */
  private void copyReferencedResources(String sourceURI, File destDir) {
    try {
      File sourceDir = new File(new URI(sourceURI));
      File[] files = sourceDir.listFiles();
      for (File entry : files) {
        if (entry.isFile()) {
          if (false == entry.getName().endsWith(".aql")) {
            FileUtils.copyFile(entry, new File(destDir, entry.getName()));
          }
        } else if (entry.isDirectory()) {
          File destEntry = new File(destDir, entry.getName());
          destEntry.mkdir();
          FileUtils.copyDir(entry, destEntry);
        }
      }
    } catch (URISyntaxException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

  }

  /**
   * Registers both the qualified and unqualified name of detag views and its auxiliary views in the
   * current module that is being rewritten as base views.
   * 
   * @param catalog the catalog of the original module that is currently being rewritten
   */
  private void setDetagAsBaseViews(Catalog catalog) {
    // Both the detag view and all its auxiliary views are registered in the catalog as separate
    // DetagCatalogEntries, so
    // just iterate though those entries; no need to query the detag parse tree nodes individually
    // for the detag view
    // name and the auxiliary view names.
    for (DetagCatalogEntry entry : catalog.getDetagCatalogEntries()) {

      addBaseView(entry.getName());
      addBaseView(entry.getUnqualifiedName());
      addBaseDetagView(entry.getName());
      addBaseDetagView(entry.getUnqualifiedName());
      if (debug)
        Log.debug("Setting DETAG view <%s> as base view\n", entry.getName());
    }
  }

  private void setLookupTableAsBaseViews(Catalog catalog) {
    for (CreateTableNode node : catalog.getCreateTableNodes()) {

      addBaseView(node.getTableName());
      addBaseView(node.getUnqualifiedName());
      if (debug)
        Log.debug("Setting LOOKUP TABLE <%s> as base view\n", node.getTableName());
    }

  }

  /**
   * Rewrites each of the views specified in the input.
   * 
   * @param views
   * @param catalog
   * @return
   * @throws ParseException
   * @throws Exception
   */
  private ArrayList<CreateViewNode> rewriteViews(ArrayList<CreateViewNode> views, Catalog catalog)
      throws ParseException {
    // SchemaInferrer schemaInferrer = new SchemaInferrer (catalog);

    ArrayList<CreateViewNode> rewrittenViews = new ArrayList<CreateViewNode>();

    for (CreateViewNode view : views) {

      ViewBodyNode originalStmt = view.getBody();

      try {

        if (!isBaseView(view.getViewName())) {
          // Rewrite it!

          if (debug)
            Log.debug("Rewriting view '%s'", view.getViewName());

          ViewBodyNode rewrittenStmt = rewriteStmt(originalStmt, rewrittenViews, catalog);
          view.setBody(rewrittenStmt);

          if (debug) {
            Log.debug("Rewrote view '%s' to\n%s", view.getViewName(), rewrittenStmt.dumpToStr(0));
          }

          if (debug)
            Log.debug("Inferring new schema of view '%s'", view.getViewName());

          // BEGIN SPECIAL CASE:
          // We do NOT need to update the schema of the rewritten view! The rewritten view will next
          // write itself out to
          // the rewritten module folder, and we don't need to know its schema for that purpose.
          // More importantly,
          // updating the schema at this point will result in two types of problems:
          // (1) The rewrite of views that depend on this one will be incorrect, because the schema
          // of this view is no
          // longer the original schema, but a rewritten one
          // (2) The type inference will fail for select items that come from an imported view,
          // because the schema of
          // the imported view is still the original schema and not a rewritten version (that is, if
          // the rewritten
          // statement is "select V.match, V.__auto__id from ImportedView V;", the type of
          // V.__auto__id cannot be
          // inferred because the schema of V is the original schema from the imported module
          // V[match], and it does not
          // contain rewritten fields such as V.__auto__id
          // ViewCatalogEntry catalogEntry = (ViewCatalogEntry) catalog.lookupView (view.getViewName
          // (),
          // view.getEndOfStmtToken ());
          // catalogEntry.setViewSchema (schemaInferrer.computeSchema (rewrittenStmt));
          // END SPECIAL CASE

          view.setIsOutput(null, true, null);
          rewrittenViews.add(view);

          // Update the mapping (rewritten view name -> copy of original body)
          updateRewritten2OriginalMap(view.getViewName(), originalStmt);
        }

      } catch (ProvenanceRewriteException e) {
        // Temporary fix to allow refining complex annotators using AQL
        // features not yet supported in the provenance rewrite.
        // TODO: remove once the full AQL is supported
        if (debug)
          Log.debug("Don't know how to rewrite view <%s> - will mark as base view\n",
              view.getViewName());

        addBaseView(view.getViewName());
        addBaseView(view.getUnqualifiedName());
      }

      if (isBaseView(view.getViewName())) {

        // if (view.getViewName ().contains ("logDatetime")) {
        // Log.debug("Stopping here to debug");
        // }

        TupleSchema originalStmtOutputSchema = determineSchema(catalog, view.getViewName());

        // Get the schema from the catalog, don't infer it
        // TupleSchema originalStmtOutputSchema = schemaInferrer.computeSchema (originalStmt);
        // ViewCatalogEntry ce = (ViewCatalogEntry) catalog.lookupView (view.getViewName (),
        // originalStmt.getOrigTok
        // ());
        // TupleSchema originalStmtOutputSchema = ((ViewCatalogEntry) ce).getSchema ();
        //
        // if (null == originalStmtOutputSchema) {
        // // Catalog has no schema for this view. Try to compute one.
        // originalStmtOutputSchema = SchemaInferrer.inferAdditionalType (catalog, view);
        // }

        // Wrap the operand in an outer select statement
        // to ensure all operands have compatible schemas
        String wrapperName = String.format(BASE_TEMP_VIEW_NAME_FORMAT,
            ModuleUtils.encodeElemName(view.getViewName()));
        ViewBodyNode wrappedStmt = wrapBaseView(originalStmt, wrapperName, originalStmtOutputSchema,
            rewrittenViews, catalog);

        view.setBody(wrappedStmt);
        // view.dump(System.err, 0);

        // Update the schema for existing catalog entry as there are
        // new columns added
        // ViewCatalogEntry catalogEntry = (ViewCatalogEntry) catalog.lookupView (view.getViewName
        // (),
        // view.getEndOfStmtToken ());

        // FIXME: We don't need to infer the schema and we should not. This can create problems. Add
        // a test case.
        // catalogEntry.setViewSchema (schemaInferrer.computeSchema (wrappedStmt));

        view.setIsOutput(null, true, null);
        rewrittenViews.add(view);
      }

    }

    return rewrittenViews;
  }

  /**
   * Update the mapping (rewritten view name -> copy of original body). The method should be called
   * whenever a new temporary view is created and we need to keep track of the corresponding part in
   * the original query. Currently it is used when rewriting union operands and subqueries. !!! Not
   * called with the two temporary views constructed for the consolidate rewrite!!!
   * 
   * @param rewrittenViewName
   * @param originalViewBody
   * @throws ParseException
   */
  private void updateRewritten2OriginalMap(String rewrittenViewName, ViewBodyNode originalViewBody)
      throws ParseException {

    rewritten2OriginalCopyMap.put(rewrittenViewName,
        original2CopyViewBodyMap.get(originalViewBody));

    if (debug) {

      ViewBodyNode copy = original2CopyViewBodyMap.get(originalViewBody);

      if (null == copy) {
        Log.debug("Could not update mapping for view name: %s\n", rewrittenViewName);
      } else {
        Log.debug("Updated mapping from view name: %s -> view body:\n%s\n", rewrittenViewName,
            original2CopyViewBodyMap.get(originalViewBody).dumpToStr(0));
      }
    }
  }

  /**
   * Utility method that determines the schema of a view or table by looking it up in the catalog.
   * The method throws an exception if the view or table is not present in the catalog, or their
   * schema is not present in the catalog.
   * 
   * @param catalog catalog of the original module that we currently rewrite; the catalog contains
   *        just the original elements, not the rewritten elements
   * @param name name of the view or table
   * @return the schema of the view or table as stored in the catalog; {@link FatalInternalError} is
   *         thrown if the view or table does not exist in the catalog, or it exist but its schema
   *         is null (this situation should never arise since we assume the original module has no
   *         compilation errors and so the catalog should be complete)
   * @throws ParseException
   */
  private static TupleSchema determineSchema(Catalog catalog, String name) throws ParseException {
    CatalogEntry ce = catalog.lookupView(name, null);
    if (null == ce) {
      throw new FatalInternalError("No catalog entry for view or table '%s'", name);
    }

    TupleSchema schema = null;

    if (ce instanceof AbstractRelationCatalogEntry)
      schema = ((AbstractRelationCatalogEntry) ce).getSchema();
    else if (ce instanceof DetagCatalogEntry)
      schema = ((DetagCatalogEntry) ce).getDetagSchema();
    else
      throw new FatalInternalError(
          "Computing schema for '%s' encountered an error: unexpected type of catalog entry of type: '%s'",
          name, ce.getClass());

    if (null != schema) {
      return schema;
    } else {
      // Do not infer the schema ! We assume the schema for original views was inferred before
      // starting the rewrite.
      // Inferring the schema in the middle of the rewrite will result in schemas polluted with
      // auxiliary attributes
      // generated by the rewrite.
      throw new FatalInternalError("No schema inferred for view '%s'", name);
    }
  }

  /**
   * Attempt to rewrite a statement. If the rewrite succeeds, return the rewritten statement.
   * Otherwise, make the statement a base view, and return a wrapper around this base view. Uses
   * {@link #rewriteStmt(ViewBodyNode, ArrayList<CreateViewNode>, Catalog} to perform the rewrite.
   * 
   * @param originalStmt
   * @param rewrittenViews
   * @param catalog
   * @return the body of the rewritten statement, if the rewrite succeeds; otherwise, a wrapper
   *         around the view with an unique ID value and the base provenance ID for each tuple
   * @throws ProvenanceRewriteException
   * @throws ParseException
   */
  private ViewBodyNode rewriteOrWrapStmt(ViewBodyNode originalStmt,
      ArrayList<CreateViewNode> rewrittenViews, Catalog catalog) throws ProvenanceRewriteException {
    SchemaInferrer schemaInferrer = new SchemaInferrer(catalog);
    ViewBodyNode rewrittenStmt = null;

    // Try to rewrite the statement. If any exception occurs, we wrap it in an outer base view
    // instead.
    try {
      rewrittenStmt = rewriteStmt(originalStmt, rewrittenViews, catalog);
    } catch (ProvenanceRewriteException e) {
      // If any exception occurs, then the statement cannot be rewritten. Wrap it instead.
      if (debug) {
        System.err.println("Cannot rewrite statement:\n");
        try {
          originalStmt.dump(System.err, 0);
        } catch (ParseException e1) {
          System.err.println("Exception encountered when dumping statement:\n");
          e1.printStackTrace(System.err);
        } ;
      }

      // Wrap the operand in an outer select statement
      // to ensure all operands have compatible schemas
      String wrapperName =
          String.format(BASE_TEMP_VIEW_NAME_FORMAT, tempViewIDGenerator.getNextID());
      try {
        TupleSchema originalStmtOutputSchema = schemaInferrer.computeSchema(originalStmt);
        rewrittenStmt = wrapBaseView(originalStmt, wrapperName, originalStmtOutputSchema,
            rewrittenViews, catalog);
      } catch (ParseException e1) {
        throw new ProvenanceRewriteException(e1,
            "Exception encountered when wrapping statement: \n%s\n", originalStmt);
      }

    }

    return rewrittenStmt;
  }

  /**
   * Rewrites an AQL statement by recursively traversing the parse tree and rewriting each nested
   * statement in turn. This method throws an Exception if the rewrite fails.
   * 
   * @param stmt
   * @param rewrittenViews
   * @param catalog
   * @throws ProvenanceRewriteException
   * @throws Exception
   */
  private ViewBodyNode rewriteStmt(ViewBodyNode stmt, ArrayList<CreateViewNode> rewrittenViews,
      Catalog catalog) throws ProvenanceRewriteException {

    if (stmt instanceof SelectNode) {
      // SELECT statement
      return rewriteSelect((SelectNode) stmt, rewrittenViews, catalog);

    } else if (stmt instanceof UnionAllNode) {

      // UNION ALL of multiple statements. Rewrite each in turn.
      UnionAllNode union = (UnionAllNode) stmt;

      return rewriteUnion(union, rewrittenViews, catalog);

    } else if (stmt instanceof MinusNode) {

      // Set difference
      MinusNode minus = (MinusNode) stmt;

      return rewriteMinus(minus, rewrittenViews, catalog);

    } else if (stmt instanceof ExtractNode) {

      return rewriteExtract((ExtractNode) stmt, rewrittenViews, catalog);

    } else {
      throw new ProvenanceRewriteException("Don't know how to rewrite statement: \n%s\n", stmt);
    }

  }

  private ViewBodyNode rewriteMinus(MinusNode stmt, ArrayList<CreateViewNode> rewrittenViews,
      Catalog catalog) throws ProvenanceRewriteException {
    SchemaInferrer schemaInferrer = new SchemaInferrer(catalog);
    int minusID = tempViewIDGenerator.getNextID();

    // Step 1: Rewrite the two statements that produce the operands.
    ViewBodyNode firstOp = stmt.getFirstStmt();
    ViewBodyNode secondOp = stmt.getSecondStmt();

    TupleSchema firstOutputSchema = null;

    try {
      firstOutputSchema = schemaInferrer.computeSchema(firstOp);
    } catch (ParseException e) {
      throw new ProvenanceRewriteException(e,
          "Exception encountered when rewriting MINUS statement.\n");
    }

    String firstWrapperName = String.format(MINUS_OPERAND_TEMP_VIEW_NAME_FORMAT, minusID, 1);
    ViewBodyNode firstWrappedOp =
        rewriteAndWrapOperandStmt(firstOp, false, firstWrapperName, rewrittenViews, catalog);

    String secondWrapperName = String.format(MINUS_OPERAND_TEMP_VIEW_NAME_FORMAT, minusID, 2);
    ViewBodyNode secondWrappedOp =
        rewriteAndWrapOperandStmt(secondOp, false, secondWrapperName, rewrittenViews, catalog);

    // If we got here then it is safe to perform the rewrite.
    // STEP 2: compute the result of MINUS under set semantics
    // (to avoid changing the multiplicity of the tuples in the result of
    // the final rewritten statement)

    try {

      MinusNode minus = new MinusNode(firstWrappedOp, secondWrappedOp, stmt.getContainingFileName(),
          stmt.getOrigTok());

      // Create the SELECT with GROUP BY stmt that actually computes the set
      // difference
      String subqueryAlias = TEMP_TUPLE_VAR;
      SelectListNode selectList =
          makeWrapperSelectList(firstOutputSchema, false, subqueryAlias, catalog);
      GroupByClauseNode groupByClause =
          new GroupByClauseNode(stmt.getContainingFileName(), stmt.getOrigTok());
      for (int i = 0; i < selectList.size(); i++) {
        SelectListItemNode item = selectList.get(i);
        groupByClause.addValue(item.getValue());
      }
      FromListItemSubqueryNode subqueryItem =
          new FromListItemSubqueryNode(minus, stmt.getContainingFileName(), stmt.getOrigTok());
      subqueryItem.setAlias(new NickNode(subqueryAlias));
      FromListNode fromList = new FromListNode(new ArrayList<FromListItemNode>(),
          stmt.getContainingFileName(), stmt.getOrigTok());
      fromList.add(subqueryItem);

      SelectNode setMinus =
          new SelectNode(selectList, fromList, null, groupByClause, null, null, null, null, null);
      String setMinusViewName = String.format(MINUS_TEMP_VIEW_NAME_FORMAT, minusID);
      CreateViewNode tempView = new CreateViewNode(new NickNode(setMinusViewName), setMinus,
          stmt.getContainingFileName(), stmt.getOrigTok());
      tempView.setIsOutput(null, true, null);
      catalog.addView(tempView);
      catalog.addOutputView(setMinusViewName, null);
      rewrittenViews.add(tempView);

      // STEP 3: Final rewrite joins first rewritten operand with the result
      // of set minus

      NickNode opAlias = new NickNode(WRAPPED_OP_ALIAS);
      FromListItemViewRefNode opItem = new FromListItemViewRefNode(new NickNode(firstWrapperName));
      opItem.setAlias(opAlias);
      FromListNode fromList2 = new FromListNode(new ArrayList<FromListItemNode>(),
          stmt.getContainingFileName(), stmt.getOrigTok());
      fromList2.add(opItem);

      NickNode setMinusAlias = new NickNode(TEMP_TUPLE_VAR);
      FromListItemViewRefNode setMinusItem =
          new FromListItemViewRefNode(new NickNode(setMinusViewName));
      setMinusItem.setAlias(setMinusAlias);
      fromList2.add(setMinusItem);

      SelectListNode selectList2 =
          makeWrapperSelectList(firstOutputSchema, false, opAlias.getNickname(), catalog);

      // Add an auto-generated tuple id attribute
      selectList2.add(makeAutoIDNode());

      // Add the provenance id
      selectList2.add(makeProvenanceIDNode(opItem, rewrittenViews, false, catalog));

      // Add a StatementType attribute
      selectList2.add(makeStmtTypeNode(StmtType.STMT_TYPE_MINUS));

      WhereClauseNode whereClause =
          new WhereClauseNode(stmt.getContainingFileName(), stmt.getOrigTok());

      for (String att : firstOutputSchema.getFieldNames()) {
        NickNode col = new NickNode(att);
        ColNameNode opAtt = new ColNameNode(opAlias, col);
        ColNameNode setMinusAtt = new ColNameNode(setMinusAlias, col);
        ArrayList<RValueNode> args = new ArrayList<RValueNode>();
        args.add(opAtt);
        args.add(setMinusAtt);
        ScalarFnCallNode func =
            new ScalarFnCallNode(new NickNode(ScalarFunc.computeFuncName(Equals.class)), args);
        whereClause
            .addPred(new PredicateNode(func, stmt.getContainingFileName(), stmt.getOrigTok()));
      }

      SelectNode rewrittenMinus =
          new SelectNode(selectList2, fromList2, whereClause, null, null, null, null, null, null);

      return rewrittenMinus;
    } catch (ParseException e) {
      throw new ProvenanceRewriteException(e,
          "Exception encountered when rewriting MINUS statement.\n");
    }
  }

  private ViewBodyNode rewriteUnion(UnionAllNode union, ArrayList<CreateViewNode> rewrittenViews,
      Catalog catalog) throws ProvenanceRewriteException {

    int unionID = tempViewIDGenerator.getNextID();

    for (int i = 0; i < union.getNumStmts(); i++) {

      ViewBodyNode originalUnionOp = union.getStmt(i);

      // Rewrite, then wrap the operand in an outer select statement
      // to ensure all operands have compatible schemas
      String wrapperName = String.format(UNION_OPERAND_TEMP_VIEW_NAME_FORMAT, unionID, i);
      ViewBodyNode wrappedUnionOperand =
          rewriteAndWrapOperandStmt(originalUnionOp, true, wrapperName, rewrittenViews, catalog);

      // Replaced the union operand with the wrapped version
      union.setStmt(i, wrappedUnionOperand);
    }

    return union;
  }

  /**
   * Wraps a statement with an outer select statement. Mainly to ensure union-compatibility for the
   * operands of a UNION statement after they have been rewritten. Also used as an intermediate step
   * in the rewrite for MINUS.
   * 
   * @param stmt
   * @param rewrittenViews
   * @param catalog
   * @return
   * @throws ProvenanceRewriteException
   */

  private ViewBodyNode rewriteAndWrapOperandStmt(ViewBodyNode stmt, boolean isUnionOp,
      String tempViewName, ArrayList<CreateViewNode> rewrittenViews, Catalog catalog)
      throws ProvenanceRewriteException {
    SchemaInferrer schemaInferrer = new SchemaInferrer(catalog);
    // Remember the original output schema
    TupleSchema originalOutputSchema = null;

    try {
      originalOutputSchema = schemaInferrer.computeSchema(stmt);
    } catch (ParseException e) {
      throw new ProvenanceRewriteException(e,
          "Exception encountered when wrapping a statement operand");
    }

    // First rewrite the statement
    ViewBodyNode rewrittenStmt = rewriteOrWrapStmt(stmt, rewrittenViews, catalog);

    ViewBodyNode wrapperStmt;

    if (rewrittenStmt instanceof SelectNode || rewrittenStmt instanceof ExtractNode
        || rewrittenStmt instanceof UnionAllNode) {

      try {
        // Create the FROM statement of the wrapper
        // The input statement becomes a new temporary view
        // and is referenced in the FROM clause of the wrapper
        FromListNode wrapperFromList = new FromListNode(new ArrayList<FromListItemNode>(),
            stmt.getContainingFileName(), stmt.getOrigTok());

        // Create the temporary view
        CreateViewNode tempView = new CreateViewNode(new NickNode(tempViewName), rewrittenStmt,
            stmt.getContainingFileName(), stmt.getOrigTok());
        tempView.setIsOutput(null, true, null);
        rewrittenViews.add(tempView);
        catalog.addView(tempView);
        catalog.addOutputView(tempViewName, null);
        // Update the mapping (rewritten view name -> copy of original body)
        updateRewritten2OriginalMap(tempViewName, stmt);

        // Reference the temporary view from the FROM clause of the WRAPPER
        String tempViewAlias = WRAPPED_OP_ALIAS;
        FromListItemViewRefNode tempFromItem =
            new FromListItemViewRefNode(new NickNode(tempViewName));
        tempFromItem.setAlias(new NickNode(tempViewAlias));
        wrapperFromList.add(tempFromItem);

        // Create the SELECT statement of the wrapper
        SelectListNode wrapperSelectList =
            makeWrapperSelectList(originalOutputSchema, isUnionOp, tempViewAlias, catalog);

        // Create the WRAPPER statement
        wrapperStmt = new SelectNode(wrapperSelectList, wrapperFromList, null, null, null, null,
            null, null, null);
      } catch (ParseException e) {
        throw new ProvenanceRewriteException(
            "Exception encountered when wrapping a statement operand:\n%s\n", stmt);
      }

    } else if (rewrittenStmt instanceof UnionAllNode) {

      // Nothing to do here. This union node has been already made
      // compatible
      // by the recursive invocation of the rewrite on the node itself.
      wrapperStmt = rewrittenStmt;

    } else {
      throw new ProvenanceRewriteException("Exception encountered when wrapping statement: \n%s\n",
          stmt);
    }

    return wrapperStmt;

  }

  private ViewBodyNode wrapBaseView(ViewBodyNode stmt, String tempViewName,
      TupleSchema originalOutputSchema, ArrayList<CreateViewNode> rewrittenViews, Catalog catalog)
      throws ParseException {
    if (null == originalOutputSchema) {
      throw new NullPointerException("Null originalOutputSchema ptr passed to wrapBaseView()");
    }

    // Create the temporary view
    CreateViewNode tempView = new CreateViewNode(new NickNode(tempViewName), stmt,
        stmt.getContainingFileName(), stmt.getOrigTok());
    tempView.setIsOutput(null, true, null);
    rewrittenViews.add(tempView);
    catalog.addView(tempView);
    catalog.addOutputView(tempViewName, null);
    // Update the mapping (rewritten view name -> copy of original body)
    // updateRewritten2OriginalMap(tempViewName, stmt);

    // Reference the temporary view from the FROM clause of the WRAPPER
    String tempViewAlias = WRAPPED_OP_ALIAS;
    FromListItemViewRefNode tempFromItem = new FromListItemViewRefNode(new NickNode(tempViewName));
    tempFromItem.setAlias(new NickNode(tempViewAlias));

    // Create the FROM statement of the wrapper
    // The input statement becomes a new temporary view
    // and is referenced in the FROM clause of the wrapper
    FromListNode wrapperFromList = new FromListNode(new ArrayList<FromListItemNode>(),
        stmt.getContainingFileName(), stmt.getOrigTok());

    wrapperFromList.add(tempFromItem);

    // Create the SELECT statement of the wrapper
    SelectListNode wrapperSelectList =
        makeWrapperSelectList(originalOutputSchema, false, tempViewAlias, catalog);

    // Add a fresh AutoID attribute

    // First we remove the existing AutoID attribute, if any
    // boolean hasAutoID = false;
    for (int i = 0; i < wrapperSelectList.size(); i++)
      if (wrapperSelectList.get(i).getAlias().equals(AUTO_ID_ALIAS)) {
        if (debug)
          Log.debug("Got existing __auto_id attribute in %s\n", tempViewName);
        wrapperSelectList.remove(i);
        break;
      }

    // add a fresh AutoID attribute
    wrapperSelectList.add(makeAutoIDNode());

    // Create the WRAPPER statement
    SelectNode wrapperStmt = new SelectNode(wrapperSelectList, wrapperFromList, null, null, null,
        null, null, null, null);

    return wrapperStmt;

  }

  /**
   * A helper function for rewriting a table locator. For example, in the following table function
   * call, <code>MyView</code> is such as table locator:<br/>
   * 
   * <pre>
   * MyTableFunc(2, MyView)
   * </pre>
   * 
   * This method verifies whether an auxiliary view has been already generated for a table locator
   * with the same name and if so, it returns a new table locator referring to that auxiliary view.
   * For example, if the auxiliary view is called <code>MyView_Aux</code>, then the rewritten
   * version of the original table locator <code>MyView</code> is <code>MyView_Aux</code>.
   * Otherwise, produces the following new auxiliary view, adds it to the set of rewritten views and
   * the catalog, and returns a new table locator referring to the new auxiliary view, in this case,
   * <code>MyView_Aux</code>. <br/>
   * 
   * <pre>
   * create view <MyView_Aux> as
   * select Op.<originalOutputSchema>
   * from <MyView> Op;
   * </pre>
   * 
   * Notice that the new auxiliary view copies all columns in the original view, leaving out any
   * additional columns generated by the provenance rewrite for the view <code>MyView</code>. The
   * purpose of the table locator rewrite is to ensure the surrounding scalar function or table
   * function receives a view or table with the expected schema; if the function would receive a
   * view with an altered schema (the original schema plus extra provenance attributes, the
   * rewritten AQL would not compile.
   * 
   * @param tableLoc the table locator to wrap into a new view
   * @param rewrittenViews the set of rewritten views
   * @param catalog for the module currently being rewritten
   * @return a rewritten version of the input table locator, that refers to the auxiliary view
   *         created for the original view/table in the table locator
   * @throws ParseException
   */
  private TableLocatorNode wrapTableLocator(TableLocatorNode tableLoc,
      ArrayList<CreateViewNode> rewrittenViews, Catalog catalog) throws ParseException {

    String locAuxViewName;
    if (tableFnOrLoc2AuxViewName.containsKey(tableLoc.getNickname())) {
      // The same table locator has been rewritten before. Reuse the previously generated auxiliary
      // view (Don't
      // generate a new copy!)
      locAuxViewName = tableFnOrLoc2AuxViewName.get(tableLoc.getNickname());
    } else {
      // This is the first time we see this table locator; generate an auxiliary view
      int tempID = tempViewIDGenerator.getNextID();
      locAuxViewName = String.format(TABLE_LOCATOR_TEMP_VIEW_NAME_FORMAT, tempID);

      // Remember we have created this auxiliary view for this table locator
      tableFnOrLoc2AuxViewName.put(tableLoc.getNickname(), locAuxViewName);

      // Reference the temporary view from the FROM clause of the WRAPPER
      String tempViewAlias = WRAPPED_OP_ALIAS;
      FromListItemViewRefNode tempFromItem = new FromListItemViewRefNode(tableLoc);
      tempFromItem.setAlias(new NickNode(tempViewAlias));

      // Create the FROM statement of the wrapper
      // The input statement becomes a new temporary view
      // and is referenced in the FROM clause of the wrapper
      FromListNode wrapperFromList =
          new FromListNode(new ArrayList<FromListItemNode>(), null, null);
      wrapperFromList.add(tempFromItem);

      // Create the SELECT statement of the wrapper
      TupleSchema originalOutputSchema = determineSchema(catalog, tableLoc.getNickname());
      SelectListNode wrapperSelectList =
          makeWrapperSelectList(originalOutputSchema, false, tempViewAlias, catalog);

      // First we remove the existing AutoID attribute, if any
      // boolean hasAutoID = false;
      // for (int i = 0; i < wrapperSelectList.size (); i++)
      // if (wrapperSelectList.get (i).getAlias ().equals (AUTO_ID_ALIAS)) {
      // if (debug) Log.debug ("Got existing __auto_id attribute in %s\n", tempViewName);
      // wrapperSelectList.remove (i);
      // break;
      // }

      // Create the WRAPPER statement
      SelectNode wrapperStmt = new SelectNode(wrapperSelectList, wrapperFromList, null, null, null,
          null, null, null, null);

      // Create the temporary view
      CreateViewNode tempView =
          new CreateViewNode(new NickNode(locAuxViewName), wrapperStmt, null, null);

      // Add the new view to the catalog, but we don't need to output it
      tempView.setIsOutput(null, false, null);
      catalog.addView(tempView);
      // catalog.addOutputView (tempViewName, null);

      // Add the new view to the set of rewritten views
      rewrittenViews.add(tempView);
    }

    return new TableLocatorNode(new NickNode(locAuxViewName));

  }

  /**
   * Creates the select list for the wrapper statement of a UNION ALL or MINUS operand. Pulls out
   * all original attributes (i.e., it excludes the provenance attributes). In the case of UNION
   * ALL, it also pulls out the AUTO-generated ID of the union operand.
   * 
   * @param originalOutputSchema the original schema of the union operand that is being wrapped
   * @param isUnion <code>true</code> is the operand comes from a UNION ALL statement;
   *        <code>false</code> otherwise
   * @param alias the alias of the UNION ALL or MINUS operand being wrapped as a subquery
   * @param catalog
   * @return
   * @throws Exception
   */
  private SelectListNode makeWrapperSelectList(TupleSchema originalOutputSchema, boolean isUnion,
      String alias, Catalog catalog) throws ParseException {
    if (null == originalOutputSchema) {
      throw new NullPointerException(
          "Null originalOutputSchema argument passed to makeWrapperSelectList()");
    }

    SelectListNode wrapperSelectList = null;
    String attName;
    ColNameNode newColumn;
    SelectListItemNode newSelectListItem;

    // 1. Copy all attributes of the original schema
    for (int i = 0; i < originalOutputSchema.size(); i++) {

      // get the attribute name <attname> from the original output schema
      attName = originalOutputSchema.getFieldNameByIx(i);

      // create a new column "<subqueryAlias>.<attName>"
      NickNode aliasNick = null;
      if (alias != null)
        aliasNick = new NickNode(alias);
      newColumn = new ColNameNode(aliasNick, new NickNode(attName));

      // create a new select list item
      // "<subqueryAlias>.<attName> AS <attName>"
      newSelectListItem = new SelectListItemNode(newColumn, new NickNode(attName));
      if (wrapperSelectList == null) {
        wrapperSelectList = new SelectListNode(new ArrayList<SelectListItemNode>(), null, null);
      }

      wrapperSelectList.add(newSelectListItem);
    }

    if (isUnion) {

      // 2. Add an AutoID attribute
      wrapperSelectList.add(makeAutoIDNode());

      // 3. Add a StatementType attribute
      wrapperSelectList.add(makeStmtTypeNode(StmtType.STMT_TYPE_UNION));

      // 4. Pull out the __id attribute from this union operand
      // Create a new select list item
      // "<subqueryAlias>.AUTO_ID_ALIAS AS AUTO_ID_ALIAS"
      newColumn = new ColNameNode(new NickNode(alias), new NickNode(AUTO_ID_ALIAS));
      newSelectListItem = new SelectListItemNode(newColumn, new NickNode(UNION_DEFAULT_ID));
      wrapperSelectList.add(newSelectListItem);
    }

    return wrapperSelectList;
  }

  /**
   * Rewrites a SELECT statement, and recursively each of its subqueries.
   * 
   * @param stmt
   * @param rewrittenViews
   * @param catalog
   * @throws Exception
   */
  private ViewBodyNode rewriteSelect(SelectNode stmt, ArrayList<CreateViewNode> rewrittenViews,
      Catalog catalog) throws ProvenanceRewriteException {

    if (stmt.getGroupByClause() != null) {

      String message =
          String.format("Dont't know how to rewrite GROUP BY in statement\n %s\n", stmt);

      if (debug)
        Log.debug(message);

      throw new ProvenanceRewriteException(message);
    }

    // REWRITE !
    try {

      SelectListNode selectList = stmt.getSelectList();
      ArrayList<String> originalAttrAliases = collectAttributeAliases(selectList);

      // Add an AutoID attribute
      selectList.add(makeAutoIDNode());

      // Add an StatementType attribute
      selectList.add(makeStmtTypeNode(StmtType.STMT_TYPE_SELECT));

      // Process each item in the FROM clause
      // For each item (view reference or subquery) V in the FROM list,
      // add a new attribute "V.id as alias" to the select LIST
      for (int i = 0; i < stmt.getFromList().size(); i++) {
        FromListItemNode fromItem = stmt.getFromList().get(i);

        // If subquery, rewrite it.
        if (fromItem instanceof FromListItemSubqueryNode) {

          // The subquery rewrite procedure pulls out the subquery as a temporary view
          FromListItemViewRefNode newFromItem =
              rewriteSubquery((FromListItemSubqueryNode) fromItem, rewrittenViews, catalog);

          // Replace the target with the reference to the temporary view
          stmt.getFromList().set(i, newFromItem);
        }
        // If table function, rewrite it
        else if (fromItem instanceof FromListItemTableFuncNode) {
          // The table function rewrite procedure pulls out the table function as a temporary view
          FromListItemViewRefNode newFromItem =
              rewriteTableFuncCall((FromListItemTableFuncNode) fromItem, rewrittenViews, catalog);

          // Replace the target with the reference to the temporary view
          stmt.getFromList().set(i, newFromItem);
        }

        SelectListItemNode idNode = makeProvenanceIDNode(
            (FromListItemViewRefNode) stmt.getFromList().get(i), rewrittenViews, false, catalog);
        selectList.add(idNode);
      }

      // Handle the CONSOLIDATE clause
      if (stmt.getConsolidateClause() != null)
        handleConsolidation(stmt, originalAttrAliases, rewrittenViews, catalog);

      return stmt;
    } catch (ParseException e) {
      throw new ProvenanceRewriteException(e,
          "Exception encountered when rewriting SELECT statement\n");
    }
  }

  private ArrayList<String> collectAttributeAliases(SelectListNode selectList) {

    ArrayList<String> attributes = new ArrayList<String>();
    for (int i = 0; i < selectList.size(); i++)
      attributes.add(selectList.get(i).getAlias());

    return attributes;
  }

  /**
   * Performs additional rewriting needed for the CONSOLIDATE statement.
   * 
   * @param stmt
   * @param originalAttrAliases
   * @param rewrittenViews
   * @param catalog
   * @throws ParseException
   * @throws ProvenanceRewriteException
   * @throws Exception
   */
  private void handleConsolidation(SelectNode stmt, ArrayList<String> originalAttrAliases,
      ArrayList<CreateViewNode> rewrittenViews, Catalog catalog)
      throws ParseException, ProvenanceRewriteException {

    // Step 1: Create the first intermediate view TempView1
    // TempView1 is identical to the input SELECT statement, except that:
    // 1. it does not have a CONSOLIDATE clause;
    // 2. The target of the CONSOLIDATE statement is added to the select
    // list,
    // to make things easier when consolidation is performed by TempView2.
    String tempView1Name =
        String.format(CONSOLIDATE_TEMP_VIEW_NAME_FORMAT, tempViewIDGenerator.getNextID(), 1);

    // Copy all fields of the original statement
    // These include the AutoID and the provenance columns, which have been
    // previously added by the select rewrite
    SelectListNode selectList1 = new SelectListNode(new ArrayList<SelectListItemNode>(),
        stmt.getSelectList().getContainingFileName(), stmt.getSelectList().getOrigTok());
    for (int i = 0; i < stmt.getSelectList().size(); i++)
      selectList1.add(stmt.getSelectList().get(i));

    // In addition, add the consolidate target so we can use it in TempView2
    ConsolidateClauseNode originalConsolidate = stmt.getConsolidateClause();
    NickNode consolidateTargetAlias = new NickNode(CONSOLIDATE_TARGET_ID);
    SelectListItemNode consolidateTargetSelectItem =
        new SelectListItemNode(originalConsolidate.getTarget(), consolidateTargetAlias);
    selectList1.add(consolidateTargetSelectItem);

    SelectNode selectNode1 = new SelectNode(selectList1, stmt.getFromList(), stmt.getWhereClause(),
        stmt.getGroupByClause(), stmt.getOrderByClause(), null, stmt.getMaxTups(), null, null);

    CreateViewNode tempView1 = new CreateViewNode(new NickNode(tempView1Name), selectNode1,
        stmt.getContainingFileName(), stmt.getOrigTok());
    tempView1.setIsOutput(null, true, null);
    catalog.addView(tempView1);
    catalog.addOutputView(tempView1Name, null);
    rewrittenViews.add(tempView1);
    // Update the mapping (rewritten view name -> copy of original body)
    updateRewritten2OriginalMap(tempView1Name, stmt);

    // Step 2: Create the second intermediate view TempView2
    // TempView2 selects the contents of the TempView1, and performs the
    // consolidation

    // 2(a): create the FROM clause
    FromListItemViewRefNode fromListItem1 =
        new FromListItemViewRefNode(new NickNode(tempView1Name));
    NickNode tempView1Alias = new NickNode(String.format("%s%d", TEMP_TUPLE_VAR, 1));
    fromListItem1.setAlias(tempView1Alias);
    FromListNode fromList2 = new FromListNode(new ArrayList<FromListItemNode>(),
        stmt.getFromList().getContainingFileName(), stmt.getFromList().getOrigTok());
    fromList2.add(fromListItem1);

    // 2(b): create the SELECT clause
    SelectListNode selectList2 = new SelectListNode(new ArrayList<SelectListItemNode>(),
        stmt.getSelectList().getContainingFileName(), stmt.getSelectList().getOrigTok());
    ColNameNode value1;
    for (String alias : originalAttrAliases) {
      value1 = new ColNameNode(tempView1Alias, new NickNode(alias));
      selectList2.add(new SelectListItemNode(value1, new NickNode(alias)));
    }

    // make sure we add the consolidate target to the select list
    ColNameNode consolidateVal = new ColNameNode(tempView1Alias, consolidateTargetAlias);
    selectList2.add(new SelectListItemNode(consolidateVal, consolidateTargetAlias));

    // 2(c): create the CONSOLIDATE clause
    String consType = originalConsolidate.getTypeStr();
    ConsolidateClauseNode newConsolidate2 =
        new ConsolidateClauseNode(consolidateVal, new StringNode(consType), null, null,
            originalConsolidate.getContainingFileName(), originalConsolidate.getOrigTok());

    // 2(d): finally, put together TempView2
    SelectNode selectNode2 =
        new SelectNode(selectList2, fromList2, null, null, null, newConsolidate2, null, null, null);
    String tempView2Name =
        String.format(CONSOLIDATE_TEMP_VIEW_NAME_FORMAT, tempViewIDGenerator.getCurrentID(), 2);
    CreateViewNode tempView2 = new CreateViewNode(new NickNode(tempView2Name), selectNode2,
        stmt.getContainingFileName(), stmt.getOrigTok());
    tempView2.setIsOutput(null, true, null);
    catalog.addView(tempView2);
    catalog.addOutputView(tempView2Name, null);
    rewrittenViews.add(tempView2);

    // STEP 3: rewrite the original statement in terms of TempView1 and
    // TempView2
    // We essentially join TempView1 and TempView2 on the original columns
    // and aggregate the ids of TempView1 tuples that have been consolidated
    // into a single tuple

    // 3(a): create the FROM clause
    FromListItemViewRefNode fromListItem2 =
        new FromListItemViewRefNode(new NickNode(tempView2Name));
    NickNode tempView2Alias = new NickNode(String.format("%s%d", TEMP_TUPLE_VAR, 2));
    fromListItem2.setAlias(tempView2Alias);
    FromListNode fromList = new FromListNode(new ArrayList<FromListItemNode>(),
        stmt.getFromList().getContainingFileName(), stmt.getFromList().getOrigTok());
    fromList.add(fromListItem1);
    fromList.add(fromListItem2);

    // 3(b): create the SELECT, WHERE and GROUP BY clauses
    SelectListNode selectList = new SelectListNode(new ArrayList<SelectListItemNode>(),
        stmt.getSelectList().getContainingFileName(), stmt.getSelectList().getOrigTok());
    WhereClauseNode whereClause;
    if (stmt.getWhereClause() != null) {
      whereClause = new WhereClauseNode(stmt.getWhereClause().getContainingFileName(),
          stmt.getWhereClause().getOrigTok());
    } else {
      whereClause = new WhereClauseNode(null, null);
    }
    GroupByClauseNode groupByClause;
    if (stmt.getGroupByClause() != null) {
      groupByClause = new GroupByClauseNode(stmt.getGroupByClause().getContainingFileName(),
          stmt.getGroupByClause().getOrigTok());
    } else {
      groupByClause = new GroupByClauseNode(null, null);
    }
    ColNameNode value2;

    for (String alias : originalAttrAliases) {

      // //SELECT clause pulls out all original values from TempView2
      value2 = new ColNameNode(tempView2Alias, new NickNode(alias));
      selectList.add(new SelectListItemNode(value2, new NickNode(alias)));

      // GROUP BY clause groups by all original values from TempView2
      groupByClause.addValue(value2);
    }

    // Add an auto-generated tuple id attribute
    selectList.add(makeAutoIDNode());

    // Add the list() aggregate function that computes the set of provenance
    // tuple ids
    selectList.add(makeProvenanceIDNode(fromListItem1, rewrittenViews, true, catalog));

    // Add a StatementType attribute
    selectList.add(makeStmtTypeNode(StmtType.STMT_TYPE_CONSOLIDATE));

    // 3(c): The WHERE clause joins TempView1 and TempView2 on the
    // consolidate target value using the appropriate join predicate
    ColNameNode origSpan = new ColNameNode(tempView1Alias, consolidateTargetAlias);
    ColNameNode consSpan = new ColNameNode(tempView2Alias, consolidateTargetAlias);
    ArrayList<RValueNode> args = new ArrayList<RValueNode>();
    args.add(origSpan);
    args.add(consSpan);
    ScalarFnCallNode func;

    if (consType.equals(PartialOrder.CONTAINEDWITHIN_ORDERNAME)
        || consType.equals(PartialOrder.CONTAINSBUTNOTEQUAL_ORDERNAME)) {

      func = new ScalarFnCallNode(new NickNode(ScalarFunc.computeFuncName(ContainedWithin.class)),
          args);
    } else if (consType.equals(PartialOrder.NOTCONTAINEDWITHIN_ORDERNAME)) {
      func = new ScalarFnCallNode(new NickNode(ScalarFunc.computeFuncName(Contains.class)), args);
    } else if (consType.equals(PartialOrder.EXACTMATCH_ORDERNAME)) {
      func = new ScalarFnCallNode(new NickNode(ScalarFunc.computeFuncName(Equals.class)), args);
    } else if (consType.equals(ConsolidateImpl.LEFTTORIGHT)) {
      // where Overlaps(consSpan, origSpan) and
      // Or( GreaterThan(GetBegin(origSpan), GetBegin(consSpan)),
      // Equals(GetBegin(origSpan), GetBegin(consSpan))
      func = new ScalarFnCallNode(new NickNode(ScalarFunc.computeFuncName(Overlaps.class)), args);

      ArrayList<RValueNode> argsGetBeginCons = new ArrayList<RValueNode>();
      argsGetBeginCons.add(consSpan);
      ScalarFnCallNode funcGetBeginCons = new ScalarFnCallNode(
          new NickNode(ScalarFunc.computeFuncName(GetBegin.class)), argsGetBeginCons);
      ArrayList<RValueNode> argsGetBeginOrig = new ArrayList<RValueNode>();
      argsGetBeginOrig.add(origSpan);
      ScalarFnCallNode funcGetBeginOrig = new ScalarFnCallNode(
          new NickNode(ScalarFunc.computeFuncName(GetBegin.class)), argsGetBeginOrig);

      ArrayList<RValueNode> argsGreaterThan = new ArrayList<RValueNode>();
      argsGreaterThan.add(funcGetBeginOrig);
      argsGreaterThan.add(funcGetBeginCons);
      ScalarFnCallNode funcGreaterThan = new ScalarFnCallNode(
          new NickNode(ScalarFunc.computeFuncName(GreaterThan.class)), argsGreaterThan);
      ScalarFnCallNode funcEquals = new ScalarFnCallNode(
          new NickNode(ScalarFunc.computeFuncName(Equals.class)), argsGreaterThan);

      ArrayList<RValueNode> argsOr = new ArrayList<RValueNode>();
      argsOr.add(funcGreaterThan);
      argsOr.add(funcEquals);
      ScalarFnCallNode funcOr = new ScalarFnCallNode(new NickNode(Or.FNAME), argsOr);

      whereClause
          .addPred(new PredicateNode(funcOr, stmt.getContainingFileName(), stmt.getOrigTok()));
    } else {
      throw new ProvenanceRewriteException(
          "Exception encountered when rewriting CONSOLIDATE policy: %s", consType);
    }

    whereClause.addPred(new PredicateNode(func, stmt.getContainingFileName(), stmt.getOrigTok()));

    // TODO: Anything to be done wrt the ORDER BY clause ?

    // 3(d): Finally, update the original statement in place, in terms of
    // TempView1 and TempView2
    stmt.setSelectList(selectList);
    stmt.setFromList(fromList);
    stmt.setWhereClause(whereClause);
    stmt.setGroupByClause(groupByClause);
    stmt.setOrderByClause(null);
    stmt.setConsolidateClause(null);
  }

  /**
   * Rewrites the EXTRACT statement.
   * 
   * @param stmt
   * @param rewrittenViews
   * @param catalog
   * @throws ProvenanceRewriteException
   * @throws Exception
   */
  private ViewBodyNode rewriteExtract(ExtractNode stmt, ArrayList<CreateViewNode> rewrittenViews,
      Catalog catalog) throws ProvenanceRewriteException {

    ExtractListNode extractList = stmt.getExtractList();

    // Add a StatementType attribute
    StmtType stmtType = null;
    if (extractList.getExtractSpec() instanceof DictExNode)
      stmtType = StmtType.STMT_TYPE_EXTRACT_DICT;
    else if (extractList.getExtractSpec() instanceof RegexExNode)
      stmtType = StmtType.STMT_TYPE_EXTRACT_REGEX;
    else
      throw new ProvenanceRewriteException("Don't know how to rewrite EXTRACT statement.\n");

    // If we got here then it is safe to rewrite

    try {
      FromListItemNode fromItem = stmt.getTarget();

      // Add an AutoID attribute
      SelectListItemNode autoIDNode = makeAutoIDNode();
      extractList.getSelectList().add(autoIDNode);

      SelectListItemNode stmtTypeNode = makeStmtTypeNode(stmtType);
      extractList.getSelectList().add(stmtTypeNode);

      // If the target is a subquery, rewrite it.
      if (fromItem instanceof FromListItemSubqueryNode) {

        // The subquery rewrite procedure pulls out the subquery as a
        // temporary view
        FromListItemViewRefNode newFromItem =
            rewriteSubquery((FromListItemSubqueryNode) fromItem, rewrittenViews, catalog);

        // Replace the target with the reference to the temporary view
        stmt.setTarget(newFromItem);
      }

      // Add the provenance tuple id, i.e., the tuple id from the target
      // relation
      SelectListItemNode selectIDNode = makeProvenanceIDNode(
          (FromListItemViewRefNode) stmt.getTarget(), rewrittenViews, false, catalog);
      extractList.getSelectList().add(selectIDNode);

      return stmt;
    } catch (ParseException e) {
      throw new ProvenanceRewriteException(e,
          "Exception encountered when rewriting EXTRACT statement.\n");
    }
  }

  /**
   * Processing of a subquery by rewriting its body and pulling it out as a new temporary view.
   * 
   * @param subquery
   * @param rewrittenViews
   * @param catalog
   * @return
   * @throws Exception
   */
  private FromListItemViewRefNode rewriteSubquery(FromListItemSubqueryNode subquery,
      ArrayList<CreateViewNode> rewrittenViews, Catalog catalog) throws ProvenanceRewriteException {

    ViewBodyNode originalSubqueryStmt = subquery.getBody();

    try {
      // Rewrite the subquery body
      ViewBodyNode rewrittenSubqueryStmt =
          rewriteOrWrapStmt(subquery.getBody(), rewrittenViews, catalog);

      // Pull out the subquery as a temporary view
      int tempViewID = tempViewIDGenerator.getNextID();
      String tempViewName = String.format(SUBQUERY_TEMP_VIEW_NAME_FORMAT, tempViewID);
      CreateViewNode tempView =
          new CreateViewNode(new NickNode(tempViewName), rewrittenSubqueryStmt,
              originalSubqueryStmt.getContainingFileName(), originalSubqueryStmt.getOrigTok());
      tempView.setIsOutput(null, true, null);
      rewrittenViews.add(tempView);
      catalog.addView(tempView);
      // Update the mapping (rewritten view name -> copy of original body)
      updateRewritten2OriginalMap(tempViewName, originalSubqueryStmt);

      // Return a reference to the temporary view
      FromListItemViewRefNode newFromItem = new FromListItemViewRefNode(new NickNode(tempViewName));
      newFromItem.setAlias(subquery.getAlias());

      return newFromItem;
    } catch (Exception e) {
      throw new ProvenanceRewriteException(e, "Exception in subquery rewriting statement: \n%s\n",
          originalSubqueryStmt);
    }

  }

  /**
   * Rewrite a table function. Given a table function call as in the following example: <br/>
   * 
   * <pre>
   * create view MyView as 
   * select T.*
   * from MyTableFunc('scalar arg', View1) T;
   * </pre>
   * 
   * <br/>
   * the rewrite proceeds as follows: <br/>
   * In the first step, performed in this method, add a wrapper <code>AuxView</code> around the
   * table function call. The wrapper assigns unique IDs to tuples generated by the table function.
   * Replace the table function call with a the reference to <code>AuxView</code>: <br/>
   * 
   * <pre>
   * create view MyView as
   *  select T.a, T.b,
   *         AutoID() as autoId,
   *         'STMT_TYPE' as stmtType
   *         T.autoId as provenance
   * from AuxView T;
   *   
   * create view AuxView as
   * select T.a, T.b,
   *        AutoID() as autoId,
   *       'STMT_TYPE_TABLE_FUNC' as stmtType,
   *       'MyTableFunc('scalar arg', <MyView>)' as displayName,
   *       -1 as provenance
   *  from MyTableFunc('scalar arg', View1) T;
   * </pre>
   * 
   * The second step of the rewrite is not performed in this method. Instead, it is a global pass
   * over all rewritten views in the final stage of
   * {@link #rewriteModule(CompileAQLParams, java.util.List, ArrayList)}. That global pass over all
   * rewritten views in the current module ensures that all table locators (in a table function call
   * or a scalar function call) in the rewritten views respect the schema of the input parameter of
   * the table or function call. Because all of these table locators are actually views which have
   * been rewritten for provenance, their schemas have been altered. So, we replace each occurrence
   * of a table locator with an auxiliary view that projects out all extra columns added during
   * provenance rewrite. This second step of the rewrite is performed in a global pass over all
   * rewritten views in the final stage of
   * {@link #rewriteModule(CompileAQLParams, java.util.List, ArrayList)}. In that step,
   * <code>AuxView</code> from above is rewritten as follows. Notice the replacement of the table
   * locator <code>View1</code> with the auxiliary view with the expected original schema
   * <code>AuxView1</code>:<br/>
   * 
   * <pre>
   * create view AuxView as
   * select T.a, T.b,
   *        AutoID() as autoId,
   *       'STMT_TYPE_TABLE_FUNC' as stmtType,
   *       'MyTableFunc('scalar arg', <View1>)' as displayName,
   *       -1 as provenance
   *  from MyTableFunc('scalar arg', AuxView1) T;
   *  
   *  create view AuxView1 as
   *  select <original schema of View1>
   *  from View1;
   * </pre>
   * 
   * @param subquery
   * @param rewrittenViews
   * @param catalog
   * @return a new from list item node pointing to the view produced in step 1 of the process
   *         described above
   * @throws Exception
   */
  private FromListItemViewRefNode rewriteTableFuncCall(FromListItemTableFuncNode tabFuncItem,
      ArrayList<CreateViewNode> rewrittenViews, Catalog catalog) throws ProvenanceRewriteException {

    TableFnCallNode origTabFunc = tabFuncItem.getTabfunc();

    try {

      String tableFnCallAuxViewName;

      if (tableFnOrLoc2AuxViewName.containsKey(origTabFunc.toString())) {
        // This table function call has been encountered before (with identical arguments). Don't
        // generate the auxiliary
        // view again, instead reuse the auxiliary view previously generated.
        tableFnCallAuxViewName = tableFnOrLoc2AuxViewName.get(origTabFunc.toString());
      } else {
        // This is the first time we encounter this table function call with these arguments. Create
        // a new auxiliary
        // view.

        // select T.*
        NickNode tabFuncAlias = new NickNode("T");
        SelectListItemNode selectItem = new SelectListItemNode(tabFuncAlias, null);
        selectItem.setIsDotStar(true);
        ArrayList<SelectListItemNode> selectListItems = new ArrayList<SelectListItemNode>();
        selectListItems.add(selectItem);

        // Add a fresh AutoID attribute
        selectListItems.add(makeAutoIDNode());

        // Add the statement type
        selectListItems.add(makeStmtTypeNode(StmtType.STMT_TYPE_TABLE_FUNC));

        // Add the display name for this node
        selectListItems.add(makeDisplayNameNode(origTabFunc.toString()));

        // Add the provenance tuple id, i.e., the tuple id from the target relation
        SelectListItemNode selectIDNode =
            makeProvenanceIDNode(tabFuncItem, rewrittenViews, false, catalog);
        selectListItems.add(selectIDNode);

        SelectListNode selectList = new SelectListNode(selectListItems, null, null);

        TableFnCallNode newTabFunc =
            new TableUDFCallNode(new NickNode(origTabFunc.getFuncName()), origTabFunc.getArgs());
        FromListItemNode fromItem = new FromListItemTableFuncNode(newTabFunc);
        fromItem.setAlias(tabFuncAlias);
        ArrayList<FromListItemNode> fromListItems = new ArrayList<FromListItemNode>();
        fromListItems.add(fromItem);
        FromListNode fromList = new FromListNode(fromListItems, null, null);
        SelectNode selectStmt =
            new SelectNode(selectList, fromList, null, null, null, null, null, null, null);

        // Pull out the table function call as a temporary view
        int tempID = tempViewIDGenerator.getNextID();
        tableFnCallAuxViewName = String.format(TABLE_FUNC_TEMP_VIEW_NAME_FORMAT, tempID);
        CreateViewNode tempView = new CreateViewNode(new NickNode(tableFnCallAuxViewName),
            selectStmt, selectStmt.getContainingFileName(), selectStmt.getOrigTok());
        tempView.setIsOutput(null, true, null);
        rewrittenViews.add(tempView);
        catalog.addView(tempView);

        // Remember we have created the auxiliary view for this table function call with these
        // arguments
        tableFnOrLoc2AuxViewName.put(origTabFunc.toString(), tableFnCallAuxViewName);

        // Update the mapping (rewritten view name -> copy of original body)
        // updateRewritten2OriginalMap (tempViewName, originalSubqueryStmt);
      }

      // Return a reference to the auxiliary view
      FromListItemViewRefNode newFromItem =
          new FromListItemViewRefNode(new NickNode(tableFnCallAuxViewName));
      newFromItem.setAlias(tabFuncItem.getAlias());

      return newFromItem;
    } catch (Exception e) {
      throw new ProvenanceRewriteException(e, "Exception in rewriting table function call: \n%s\n",
          tabFuncItem);
    }

  }

  /**
   * Creates the select item that pulls out the provenance ID attribute corresponding to the view
   * referenced in the input FROM item node, or assigns the base provenance ID value (i.e.,
   * {@link #DEFAULT_DOC_ID} if the FROM item node corresponds to a view or table that is not
   * supported by the provenance rewrite.
   * 
   * @param fromNode
   * @param rewrittenViews
   * @param catalog
   * @return a select item of the provenance ID attribute corresponding to the view referenced in
   *         the input FROM item node, if the fromNode corresponds to a view or table that is
   *         supported by the provenance rewrite; otherwise, if the FROM item node corresponds to a
   *         view or table that is not supported by the provenance rewrite, a select item that
   *         assigns the base provenance ID value (i.e., {@link #DEFAULT_DOC_ID}
   * @throws ParseException
   * @throws ProvenanceRewriteException
   * @throws Exception
   */
  private SelectListItemNode makeProvenanceIDNode(FromListItemNode fromNode,
      ArrayList<CreateViewNode> rewrittenViews, boolean isConsolidate, Catalog catalog)
      throws ParseException, ProvenanceRewriteException {

    String fromItemAlias = fromNode.getAlias().getNickname();
    RValueNode value;
    String idAttName = null;

    // create the select list item node "V.id as alias"
    if (fromNode instanceof FromListItemViewRefNode) {
      FromListItemViewRefNode viewRefNode = (FromListItemViewRefNode) fromNode;

      // The alias has the form <viewName><separator><viewAlias>__id
      idAttName = String.format(AUTO_ID_ATTRIBUTE_FORMAT, viewRefNode.getViewName().getNickname(),
          fromItemAlias, ID_SUFFIX);

      if (isConsolidate) {
        // For consolidation, the ID value is constructed via List
        // aggregation

        // Create the argument to the List aggregate function
        ArrayList<RValueNode> args = new ArrayList<RValueNode>();
        ColNameNode arg = new ColNameNode(new NickNode(fromItemAlias), new NickNode(AUTO_ID_ALIAS));
        args.add(arg);

        value = new ScalarFnCallNode(new NickNode(ScalarFunc.computeFuncName(List.class)), args);
      } else {
        String name = viewRefNode.getViewName().getNickname();

        // Laura: fix for bug 14117: Provenance viewer for Japanese collection just displays 'All
        // Results'
        // We can get the basic tuple IDs from the wrapped statement if the view is a
        // CreateViewNode, still need to
        // properly handle the case when the view is a DetagDocNode
        // TODO: find a better way to handle the detag and external view nodes.
        // Currently the provenance graph will not show the detag nodes and external view nodes as
        // leaves
        // need to wrap the DetagDocNodes and their auxiliary views with an outer SELECT statement,
        // as we do with
        // regular basic CreateViewNodes
        // || isBaseView(viewRefNode.getViewName().getNickname())

        // BEGIN SPECIAL CASES: Some views/tables cannot be rewritten for various reasons, so their
        // schema does not have
        // an AutoID attribute; detect that case so we can make a brand new attribute
        boolean hasAutoID = true;
        if (null != catalog.lookupTable(name)) {
          // Tables are currently not rewritten
          hasAutoID = false;
        } else if (catalog.isImportedTable(name)) {
          // Imported tables are currently not rewritten
          hasAutoID = false;
        } else if (isBaseDetagView(name)) {
          // Detag views in a generic module are currently not rewritten
          hasAutoID = false;
        } else if (localImportedBaseViews.contains(name)) {
          // Views imported from a compiled module are not rewritten
          hasAutoID = false;
        } else if (globalExportedBaseViews.contains(name)) {
          // Views exported from a source module that cannot be rewritten
          // (i.e., their schema does not contain a AutoID attribute); this situation may arise when
          // either the
          // view comes from a compiled module, or if the view comes from a source module but the
          // view could not be
          // rewritten for various reasons (e.g., it is a detag view).
          hasAutoID = false;
        } else {
          CatalogEntry catalogEntry = catalog.lookupView(name, viewRefNode.getOrigTok());

          if (null != catalogEntry) {
            if (catalogEntry.getIsExternal()) {
              // External view
              hasAutoID = false;
            } else {
              // Get the qualified name, and try the globalExportedBaseViews list once more
              String qName = catalogEntry.getName();
              if (globalExportedBaseViews.contains(qName)) {
                hasAutoID = false;
              }
            }
          }
        }
        // END SPECIAL CASES

        // If the source doesn't have an AutoID attribute, add a constant, otherwise, pull out the
        // AutoID attribute
        if (false == hasAutoID) {
          // fromNode refers to the document view, a lookup table, an imported element or some other
          // base view
          value = new IntNode(DEFAULT_DOC_ID, null, null);
        } else {
          // fromNode refers to an internal view
          value = new ColNameNode(new NickNode(fromItemAlias), new NickNode(AUTO_ID_ALIAS));
        }
      }
    } else if (fromNode instanceof FromListItemTableFuncNode) {

      TableFnCallNode viewRefNode = ((FromListItemTableFuncNode) fromNode).getTabfunc();

      // The provenance stops at table functions, so just assign the basic starting ID.
      value = new IntNode(DEFAULT_DOC_ID, null, null);

      // The alias has the form <tableFuncName><separator><viewAlias>__id
      idAttName = String.format(AUTO_ID_ATTRIBUTE_FORMAT, viewRefNode.getFuncName(), fromItemAlias,
          ID_SUFFIX);
    } else {
      throw new ProvenanceRewriteException("Unexpected type %s in from clause.",
          fromNode.getClass().getSimpleName());
    }

    SelectListItemNode selectIDNode = new SelectListItemNode(value, new NickNode(idAttName));

    return selectIDNode;
  }

  /**
   * Creates an auto-generated ID select item.
   * 
   * @param catalog
   * @return an auto-generated ID select item.
   * @throws Exception
   */
  private SelectListItemNode makeAutoIDNode() throws ParseException {

    RValueNode value = new ScalarFnCallNode(new NickNode(ScalarFunc.computeFuncName(AutoID.class)),
        new ArrayList<RValueNode>());

    // create a select node "AutoID() as <AUTO_ID_ALIAS>"
    SelectListItemNode autoIDNode = new SelectListItemNode(value, new NickNode(AUTO_ID_ALIAS));

    return autoIDNode;
  }

  /**
   * Creates a select item that indicates the type of statement being rewritten (e.g., union,
   * select, extract).
   * 
   * @param stmtType
   * @return
   * @throws Exception
   */
  private SelectListItemNode makeStmtTypeNode(StmtType stmtType) throws ParseException {

    // create a select node "<STMT_TYPE> as <STMT_TYPE_ALIAS>"

    RValueNode value = new StringNode(stmtType.toString());

    SelectListItemNode stmtTypeNode = new SelectListItemNode(value, new NickNode(STMT_TYPE_ALIAS));

    return stmtTypeNode;
  }

  /**
   * Creates a select item indicating the display name for a tuple in the result of a rewritten
   * statement.
   * 
   * @param stmtType
   * @return select item
   *         <code>'display string value' as {@link #DISPLAY_NAME_ALIAS}</code>indicating the
   *         display name for a tuple in the result of a rewritten statement
   * @throws Exception
   */
  private SelectListItemNode makeDisplayNameNode(String displayName) throws ParseException {

    // create a select node "'<DISPLAY_NAME>' as <DISPLAY_NAME_ALIAS>"

    RValueNode value = new StringNode(displayName);
    SelectListItemNode stmtTypeNode =
        new SelectListItemNode(value, new NickNode(DISPLAY_NAME_ALIAS));

    return stmtTypeNode;
  }

  /**
   * Dumps a collection of views to an output file.
   * 
   * @param views
   * @param list
   * @param outputAqlFile
   * @throws Exception
   */
  private void outputViews(String moduleName, ArrayList<CreateViewNode> views, Catalog catalog,
      Writer out, java.util.List<AQLParseTreeNode> parseTreeNodes) throws Exception {
    PrintWriter outWriter = new PrintWriter(out);

    // Make a map of rewritten view name -> CreateViewNode for easy retrieval
    // Made it a tree map to generate rewritten views in a consistent order
    TreeMap<String, CreateViewNode> viewName2CVN = new TreeMap<String, CreateViewNode>();
    for (CreateViewNode view : views) {
      viewName2CVN.put(view.getViewName(), view);
    }

    // Traverse the parse tree nodes in the original AQL file dumping all original nodes that were
    // not rewritten, and
    // replacing those that have been rewritten with the rewritten version
    if (parseTreeNodes != null) {
      for (AQLParseTreeNode origNode : parseTreeNodes) {

        if (origNode instanceof CreateViewNode) {
          // The node is a CreateViewNode. Dump the rewritten version, if it exists
          CreateViewNode cvn = (CreateViewNode) origNode;
          if (viewName2CVN.containsKey(cvn.getViewName())) {
            viewName2CVN.get(cvn.getViewName()).dump(outWriter, 0);

            // We also remove the rewrite from the list of views to dump, so later on we are left
            // only with the
            // auxiliary views
            viewName2CVN.remove(cvn.getViewName());
          } else {
            // This view must not have been a required view so it was not rewritten. Do nothing!
            // Note that writing out
            // the original version could result in a schema incompatibility issue. For example,
            // assume view C is
            // defined as (select * from A) union all (select * from B). If B is rewritten and A is
            // not rewritten (it is
            // conceivable that B is a required view, while A and C are not) then the rewritten B
            // and the original A
            // will have union incompatible schemas resulting in a compiler error when compiling the
            // rewritten module.
          }
        } else if (origNode instanceof OutputViewNode) {
          // Do nothing. We have already marked all rewritten views as output views, and the
          // corresponding
          // output view statement is dumped by CreateViewNode.dump().
        } else {
          // This is not a CreateViewNode, so it was not rewritten. Dump the original node
          origNode.dump(outWriter, 0);
        }

        outWriter.write("\n\n");
      }
    }

    // The rewrite creates a number of auxiliary views. Write them out. Note that we do not longer
    // need to ensure that
    // rewritten views are in topologically sorted order, because order no longer matters in modular
    // AQL
    for (String viewName : viewName2CVN.keySet()) {
      viewName2CVN.get(viewName).dump(outWriter, 0);
      outWriter.write("\n\n");
    }

    // Close the output
    outWriter.flush();
    outWriter.close();
  }

  /**
   * Callback for rewriting all table locators in a scalar function call (that can appear in various
   * parts of the select or extract statement). Table locators refer to views that may have been
   * rewritten and so their schema altered. However, a scalar function or table function call
   * expects the table locator to conform to the original schema specified in the original AQL;
   * furthermore, the UDF implementation of the scalar or table function might itself validate the
   * schema of the input table. Therefore, we make a copy of the original view with its original
   * schema and use it in the table function call. Heavy lifting is done by
   * {@link AQLProvenanceRewriter#wrapTableLocator(TableLocatorNode, ArrayList, Catalog)}.
   * 
   */
  class scalarFnCallTableLocatorCB extends funcRewriter {

    @SuppressWarnings("unused")
    private static final boolean debug = false;

    @Override
    ScalarFnCallNode rewrite(ScalarFnCallNode orig) throws ParseException {

      ArrayList<RValueNode> args = orig.getArgs();

      // No arguments - nothing to do
      if (args == null)
        return orig;

      // Traverse the arguments looking for table locators
      for (int i = 0; i < args.size(); i++) {
        RValueNode arg = args.get(i);

        if (arg instanceof TableLocatorNode) {
          // Found a table locator node. Rewrite it.
          TableLocatorNode oldTableLoc = (TableLocatorNode) arg;
          TableLocatorNode newTableLoc = wrapTableLocator(oldTableLoc, rewrittenViews, catalog);
          args.set(i, newTableLoc);
        }
      }

      // We modified the node in place.
      return orig;
    }

  }

  /**
   * Callback for rewriting all table locators in a table function call (that can appear in the from
   * list of the select or extract statement). Performs the same type of rewrite for table locators
   * in table function calls as {@link scalarFnCallTableLocatorCB} does for table locators in scalar
   * function calls.
   * 
   */
  class tableFnCallTableLocatorCB extends fromItemRewriter {

    @SuppressWarnings("unused")
    private static final boolean debug = false;

    @Override
    FromListItemNode rewrite(FromListItemNode orig) throws ParseException {

      if (orig instanceof FromListItemTableFuncNode) {

        FromListItemTableFuncNode tableFuncItem = (FromListItemTableFuncNode) orig;

        ArrayList<RValueNode> args = tableFuncItem.getTabfunc().getArgs();

        // No arguments - nothing to do
        if (args != null) {

          // Traverse the arguments looking for table locators
          for (int i = 0; i < args.size(); i++) {
            RValueNode arg = args.get(i);

            if (arg instanceof TableLocatorNode) {
              // Found a table locator node. Rewrite it.
              TableLocatorNode oldTableLoc = (TableLocatorNode) arg;
              TableLocatorNode newTableLoc = wrapTableLocator(oldTableLoc, rewrittenViews, catalog);
              args.set(i, newTableLoc);
            }
          }
        }
      }

      // We modified the node in place.
      return orig;
    }

  }

}
