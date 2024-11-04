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

package com.ibm.avatar.aql.compiler;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

import javax.xml.bind.JAXBException;

import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.util.dict.CompiledDictionary;
import com.ibm.avatar.algebra.util.dict.DictFile;
import com.ibm.avatar.algebra.util.dict.DictMemoization;
import com.ibm.avatar.algebra.util.dict.DictParams;
import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.algebra.util.tokenize.Tokenizer;
import com.ibm.avatar.api.CompilationSummary;
import com.ibm.avatar.api.CompilationSummaryImpl;
import com.ibm.avatar.api.CompileAQLParams;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.exceptions.CompilerException;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.aql.AQLParserBase;
import com.ibm.avatar.aql.CreateDictNode;
import com.ibm.avatar.aql.CreateExternalViewNode;
import com.ibm.avatar.aql.CreateFunctionNode;
import com.ibm.avatar.aql.CreateTableNode;
import com.ibm.avatar.aql.CreateViewNode;
import com.ibm.avatar.aql.ExtendedParseException;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.catalog.AbstractJarCatalogEntry;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.aql.catalog.CatalogEntry;
import com.ibm.avatar.aql.catalog.DetagCatalogEntry;
import com.ibm.avatar.aql.catalog.DictCatalogEntry;
import com.ibm.avatar.aql.catalog.ExternalViewCatalogEntry;
import com.ibm.avatar.aql.catalog.TableCatalogEntry;
import com.ibm.avatar.aql.catalog.ViewCatalogEntry;
import com.ibm.avatar.aql.doc.AQLDocComment;
import com.ibm.avatar.aql.planner.Planner;
import com.ibm.avatar.aql.tam.DictionaryMetadataImpl;
import com.ibm.avatar.aql.tam.FunctionMetadataImpl;
import com.ibm.avatar.aql.tam.InputType;
import com.ibm.avatar.aql.tam.ModuleMetadataImpl;
import com.ibm.avatar.aql.tam.ModuleMetadataImpl.Dictionaries;
import com.ibm.avatar.aql.tam.ModuleMetadataImpl.Functions;
import com.ibm.avatar.aql.tam.ModuleMetadataImpl.Tables;
import com.ibm.avatar.aql.tam.ModuleMetadataImpl.Views;
import com.ibm.avatar.aql.tam.ModuleUtils;
import com.ibm.avatar.aql.tam.TAM;
import com.ibm.avatar.aql.tam.TAMSerializer;
import com.ibm.avatar.aql.tam.TableMetadataImpl;
import com.ibm.avatar.aql.tam.ViewMetadataImpl;

/**
 * Top level class to compile AQL to AOG that allows using non-default implementations for the
 * {@link com.ibm.avatar.aql.Catalog} and {@link com.ibm.avatar.aql.planner.Planner}. Uses
 * {@link com.ibm.avatar.aql.ParseToCatalog} for parsing AQL and generating the catalog, and
 * {@link com.ibm.avatar.aql.planner.Planner} to generate an optimized operator graph (AOG).
 * 
 */
public class Compiler {

  // BEGIN:V1.3_BACKWARD_COMPATIBILITY_SUPPORT

  /**
   * Flag denoting whether the compiler runs in backward compatibility mode
   */
  private boolean backwardCompatibilityMode = false;

  /**
   * A map of original AQL file Vs AQL file copied to genericModule. This map is helpful in
   * reporting parse/compile errors on original AQL file instead of reporting errors on copied AQL
   * file.
   */
  private final HashMap<String, String> aqlFilesMap = new HashMap<String, String>();
  // END:V1.3_BACKWARD_COMPATIBILITY_SUPPORT

  /**
   * Catalog of available views, external types, and functions.
   */
  private Catalog catalog = new Catalog();

  /**
   * Instantiates a compiler with a temporary directory of its own. Useful for single module
   * compilation only.
   */
  public Compiler() {
    this(ModuleUtils.createCompilationTempDir().toURI().toString());
  }

  /**
   * Constructor that initializes Compiler with a specific compilationTempDir. Use this version of
   * the constructor when compiling multiple modules, because we would like all related modules
   * (that are compiled together) to share the same temporary directory.
   * 
   * @param compilationTempDir URI of the intermediate directory where .tam files are placed during
   *        compilation. Compiler would always first attempt to locate dependent modules from this
   *        temp directory because this directory is expected to contain a more recent version of
   *        the tam than what can be located from modulePath.
   */
  public Compiler(String compilationTempDir) {
    catalog.setCompilationTempDir(compilationTempDir);
  }

  /**
   * Set the catalog. Use this method when you want to use a custom catalog implementation instead
   * of the default catalog {@link com.ibm.avatar.catalog.DefaultCatalog}.
   * 
   * @param catalog
   */
  public void setCatalog(Catalog catalog) {
    if (null != catalog)
      this.catalog = catalog;
    else
      throw new IllegalArgumentException(
          "Passed a null value for catalog. The catalog cannot be null.");
  }

  /**
   * @return the catalog
   */
  public Catalog getCatalog() {
    return this.catalog;
  }

  /**
   * Planner to be used by the compiler.
   */
  private Planner planner = new Planner();

  /**
   * Set the planner. Use this method when you want to use a custom planner for compilation instead
   * of the default planner {@link com.ibm.avatar.aql.planner.HashPlanner}.
   * 
   * @param planner
   */
  public void setPlanner(Planner planner) {
    if (null != planner)
      this.planner = planner;
    else
      throw new IllegalArgumentException(
          "Passed a null value for planner. The planner cannot be null.");
  }

  /**
   * @return the planner
   */
  public Planner getPlanner() {
    return planner;
  }

  //
  // /**
  // * @return list of warnings generated during compilation.
  // */
  // public List<CompilerWarning> getWarnings ()
  // {
  // return catalog.getWarnings ();
  // }
  //
  // /**
  // * @return list of exceptions encountered during AQL parsing and compilation
  // */
  // public ArrayList<Exception> getExceptions ()
  // {
  // return catalog.getExceptions ();
  // }

  /**
   * Name of the module compiled by the compiler.
   */
  private String moduleName = null;

  /**
   * @return name of the module compiled by this compiler instance
   */
  public String getCompiledModuleName() {
    return this.moduleName;
  }

  /**
   * Compile AQL and generate the compiled operator graph (AOG) to the output stream. The top-level
   * AQL can be specified as a file, or a Java string.
   * 
   * @param params parameters specifying the location of the top-level AQL, the location of the
   *        output file, and flags to use during compilation.
   * @throws CompilerException
   * @throws IOException
   * @throws JAXBException
   */
  public void compile(CompileAQLParams params)
      throws CompilerException, IOException, JAXBException {
    catalog.createCompileSummary(params);
    boolean backwardsCompatibleMode = false;

    // Remember backwards compatibility mode setting before it gets changed
    if (true == params.isBackwardCompatibilityMode()) {
      backwardsCompatibleMode = true;
    }

    TAM tam = compileToTAM(params);

    // Sanity check: Did compileToTAM produce a plan?
    if (null == tam.getAog()) {
      throw new FatalInternalError("compileToTAM() did not produce an AOG plan.");
    }

    try {

      if (backwardsCompatibleMode) {
        // in backwards compatible mode, URI is always the target directory
        TAMSerializer.serialize(tam, new URI(params.getOutputURI()));
      } else {
        // make a copy into the compilation temp directory
        // we will place the TAMs into the output URI after all TAMs are compiled.
        TAMSerializer.serialize(tam, new URI(catalog.getCompilationTempDir()));
      }

    } catch (URISyntaxException e) {

      CompilerException ce = new CompilerException(updateSummary());
      ce.addError(e);
      throw ce;
    }

  }

  /**
   * Update the summary object with data from the module just compiled
   * 
   * @return the summary object
   */
  public CompilationSummary updateSummary() {
    return catalog.updateSummary(getCompiledModuleName());
  }

  /**
   * Method to purge the temporary directory created during compilation. Ideal to invoke this method
   * post compilation.
   * 
   * @throws Exception
   */
  public void deleteTempDirectory() throws Exception {
    String strTempDir = getCatalog().getCompilationTempDir();
    URI tempDirURI = new URI(strTempDir);

    // TODO: change this when we start supporting compilation on hdfs and gpfs
    FileUtils.deleteDirectory(new File(tempDirURI));
  }

  /**
   * Compiles the first input module passed through the {@link CompileAQLParams#getInputModules()}
   * into TAM object. It is the responsibility of the caller to serialize the TAM object into .tam
   * file.
   * 
   * @param params parameters for compilation.
   * @return compiled TAM object
   * @throws CompilerException
   * @throws IOException
   */
  public TAM compileToTAM(CompileAQLParams params) throws CompilerException, IOException {
    File genericModuleDir = null;
    TAM ret = null;
    try {
      params.validate();
      if (params.isBackwardCompatibilityMode()) {
        // Set this flag now because 'params' is mutated at a later point of time and hence querying
        // CompileAQLParams to
        // check for backward compatibility won't work after this point.
        backwardCompatibilityMode = true;

        // if data path is null, set it to AQL's parent directory
        String datapath = params.getDataPath();
        if (datapath == null || datapath.trim().length() == 0) {
          if (params.getInputFile() != null) {
            params.setDataPath(params.getInputFile().getParentFile().getPath());
          } else {
            // No input file --> Compiling from a string
            params.setDataPath(".");
          }
        }

        // Create Generic Module and add any errors to catalog
        ArrayList<Exception> errors = new ArrayList<Exception>();
        genericModuleDir = ModuleUtils.createGenericModule(params, aqlFilesMap, errors);
        if (errors.size() > 0) {
          for (Exception e : errors) {
            catalog.addCompilerException(e);
          }
        }

        // set v2.0 params
        params.setInputModules(new String[] {genericModuleDir.toURI().toString()});

        // reset v1.3 params
        params.setInputFile(null);
        params.setInputStr(null);
      } // end: if backwardCompatibilityMode

      // Use the tokenizer (if available) or instantiate a new tokenizer based on the given
      // configuration
      // Either one of the two is non-null, as guaranteed by {@link CompileAQLParams#validate()}
      Tokenizer tokenizer = params.getTokenizer();
      if (null == tokenizer)
        tokenizer = params.getTokenizerConfig().makeTokenizer();

      // Update catalog with tokenizer
      this.catalog.setTokenizer(tokenizer);

      // Parse and prepare the catalog
      ParseToCatalog parseToCatalog = new ParseToCatalog();
      parseToCatalog.setCatalog(catalog);
      parseToCatalog.parse(params);
      // Generate the compiled operator graph (AOG)
      planner.setPerformSDM(params.getPerformSDM());
      planner.setPerformRSR(params.getPerformRSR());
      planner.setPerformSRM(params.getPerformSRM());
      String[] inputModules = params.getInputModules();

      try {
        // This method will compile first entry from inputModules[]; Compiler is capable of
        // compiling one module at a
        // time
        String inputModule = inputModules[0];

        // Extract module name from input module URI
        moduleName = getModuleName(inputModule);

        ret = new TAM(moduleName);
        planner.compileToTAM(catalog, ret);
        compileDictionaries(catalog, ret);
        addJars(catalog, ret);
        writeModuleMetadata(catalog, ret);
      } catch (CompilerException ce) {
        // We use CompilerException in Preprocessor to indicate stopping of program flow.
        // So, do nothing here
        // ce.printStackTrace ();
      } catch (Exception e) {
        // Add all other exceptions to catalog
        catalog.addCompilerException(e);
      }

    } catch (CompilerException ce) {
      // throw the exception 'as-is' if it is already a CompilerException
      throw ce;
    } catch (Exception e) {
      // Add exception other than CompilerException to,the catalog's list of encountered exception
      catalog.addCompilerException(e);
    } finally {

      // BEGIN:V1.3_BACKWARD_COMPATIBILITY_SUPPORT
      if (backwardCompatibilityMode) {
        // Clean up temp directory of the generic module
        if (genericModuleDir != null && genericModuleDir.exists()) {
          // Also delete the temporary parent directory.
          FileUtils.deleteDirectory(genericModuleDir.getParentFile());
        }
      }
      // END:V1.3_BACKWARD_COMPATIBILITY_SUPPORT

      ArrayList<Exception> exceptions = catalog.getExceptions();
      if (exceptions != null && exceptions.size() > 0) {
        CompilerException compileError = new CompilerException(catalog.getSummary());
        for (Exception e : exceptions) {
          // if the compiler is running in backward compatibility mode, then rename references to
          // $temp/genericModule path in error messages with original path
          if (backwardCompatibilityMode && e instanceof ExtendedParseException) {
            ExtendedParseException epe = (ExtendedParseException) e;
            String tempAQLFile = epe.getFileName();
            if (aqlFilesMap.containsKey(tempAQLFile)) {
              // error message comes from genericModule
              String origFile = aqlFilesMap.get(tempAQLFile);
              // if (epe.currentToken != null) {
              // subtract 2 lines because AQL files in genericModule folder have a module statement
              // and a new line
              // added at the top
              epe.adjustLineNumber(-2);
              // }
              epe.setFileName(origFile);
              compileError.addError(epe);
            } // end: aql map file found
            else {
              // no aql mapping file found. So, error does not refer to $temp/genericModule. Add the
              // original error.
              compileError.addError(e);
            }
          } // end: if (backwardCompatibilityMode && e instanceof ExtendedParseException)
          else {
            // add any other error 'as-is' to compileError list
            compileError.addError(e);
          }
        }
        // Remove Duplicate exceptions, if any
        compileError = removeDuplicateExceptions(compileError);

        throw compileError;
      } // end: if errors found

    }

    // Sanity check: Did the planner produce a plan?
    if (null == ret.getAog()) {
      throw new FatalInternalError("Planner did not produce a plan for module %s", moduleName);
    }

    return ret;
  }

  /**
   * When we run the compiler in backward compatibility mode, errors pertaining to missing
   * dictionaries/UDF jars and ambiguous dictionary/UDF jar references are reported twice: Once by
   * the code that attempts to copy referenced dicts and UDF jars to 'genericModule' folder and once
   * again by the compiler. So, we remove duplicate error messages. NOTE: For now, we assume that
   * messages with same line number and column number are duplicates. There could be some corner
   * cases where there is more than one error at the same line num and col num. Add code to handle
   * such corner cases as and when they arise. This method should go away once we stop supporting
   * v1.3 style compilation.
   * 
   * @param compileError
   * @return Revised CompilerException after removing duplicates.
   */
  private CompilerException removeDuplicateExceptions(CompilerException compileError) {
    // Remember lineNum-colNum in this list and use this collection to detect duplicates
    // The value in this map is the "best" exception at that location we have found so
    // far.
    // Use a TreeMap so this method always puts its outputs in the same order.
    TreeMap<String, ExtendedParseException> lineNumColNumMap =
        new TreeMap<String, ExtendedParseException>();

    CompilerException ret = new CompilerException(catalog.getSummary());
    List<Exception> errors = compileError.getSortedCompileErrors();
    for (Exception exception : errors) {
      if (exception instanceof ExtendedParseException) {
        ExtendedParseException epe = (ExtendedParseException) exception;
        String lineCol =
            String.format("%s-%s-%s", epe.getFileName(), epe.getLine(), epe.getColumn());

        // Check whether we have already found an error at this location.
        if (lineNumColNumMap.containsKey(lineCol)) {
          // Already have an error at this location; see if the current error is
          // more suitable. Right now the only distinguishing factor we consider
          // is that errors against the original files are more useful than errors
          // against the temporary module we created by copying the files
          String oldErrMsg = lineNumColNumMap.get(lineCol).getMessage();
          String newErrMsg = epe.getMessage();

          if (oldErrMsg.contains(Constants.GENERIC_MODULE_NAME)
              && false == newErrMsg.contains(Constants.GENERIC_MODULE_NAME)) {
            // Replace an error against the temporary module with an error against the
            // original set of files.
            lineNumColNumMap.put(lineCol, epe);
          }
        } else {
          // Don't have an exception for this location yet.
          lineNumColNumMap.put(lineCol, epe);
        }
      } else {
        // Other types of exception just get passed through
        ret.addError(exception);
      }
    }

    // Gather up the "best" exception for each unique file position
    for (ExtendedParseException epe : lineNumColNumMap.values()) {
      ret.addError(epe);
    }

    // put back the de-duplicated list to compileError object so that the origin of
    // CompilerException in exception stack
    // trace is not modified.
    compileError.getAllCompileErrors().clear();
    for (Exception exception : ret.getAllCompileErrors()) {
      compileError.addError(exception);
    }
    return compileError;
  }

  @SuppressWarnings("deprecation")
  protected void writeModuleMetadata(Catalog catalog, TAM tam) throws ParseException {
    ModuleMetadataImpl mmd = new ModuleMetadataImpl();
    mmd.setModuleName(tam.getModuleName());
    mmd.setCompilationTime(new Date().toString());
    mmd.setUserName(System.getProperty("user.name"));
    try {
      mmd.setHostName(InetAddress.getLocalHost().getHostName());
    } catch (UnknownHostException e) {
      mmd.setHostName(Constants.UNKNOWN_HOSTNAME);
    }
    mmd.setProductVersion(Constants.PRODUCT_VERSION);
    mmd.setDependentModules(catalog.getRequiredModules());

    // Set the module comment
    mmd.setComment(catalog.getComment());

    // set the tokenizer
    String tokenizerType = catalog.getTokenizer().getName();
    mmd.setTokenizerType(tokenizerType);

    Views views = new Views();
    mmd.setViews(views);

    // pass 1: normal views
    ArrayList<CreateViewNode> requiredViews = catalog.getRequiredViews();
    for (CreateViewNode cvn : requiredViews) {
      // SPECIAL CASE: Skip the view Document because we always serialize it (see below)
      if (Constants.DEFAULT_DOC_TYPE_NAME.equals(cvn.getUnqualifiedName()))
        continue;
      // END SPECIAL CASE

      String viewName = cvn.getViewName();

      if (catalog.isExportedView(viewName)
          || catalog.isOutputView(viewName, cvn.getEndOfStmtToken())) {

        ViewCatalogEntry vce =
            (ViewCatalogEntry) catalog.lookupView(viewName, cvn.getEndOfStmtToken());
        TupleSchema viewSchema = vce.getSchema();

        ViewMetadataImpl vmd = new ViewMetadataImpl();
        vmd.setViewName(cvn.getUnqualifiedName());
        vmd.setSchema(viewSchema);
        vmd.setOutput(catalog.isOutputView(viewName, cvn.getEndOfStmtToken()));
        vmd.setOutputAlias(cvn.getOutputName());
        vmd.setExported(catalog.isExportedView(viewName));
        vmd.setExternal(false);
        vmd.setCostRecord(catalog.getCostRecord(viewName), cvn.getEndOfStmtToken(), viewName);
        vmd.setComment(cvn.getComment());

        mmd.getViews().getView().add(vmd);
      }
    }

    // pass 1.5: Serialize metadata for imported output views
    ArrayList<Pair<String, String>> outputNamePairs = catalog.getOutputNamePairs();
    for (Pair<String, String> pair : outputNamePairs) {
      String outputtedViewName = pair.first;
      String outputName = pair.second;

      CatalogEntry vce = catalog.lookupView(outputtedViewName, null);
      if (vce.isImported()) {
        boolean isExternal = false;
        TupleSchema viewSchema = null;
        String externalName = null;

        if (true == vce instanceof ExternalViewCatalogEntry) {
          viewSchema = ((ExternalViewCatalogEntry) vce).getSchema();
          isExternal = true;
          externalName = ((ExternalViewCatalogEntry) vce).getExternalName();
        } else {
          viewSchema = ((ViewCatalogEntry) vce).getSchema();
          isExternal = false;
        }

        ViewMetadataImpl vmd = new ViewMetadataImpl();
        vmd.setViewName(ModuleUtils.getUnqualifiedElementName(outputtedViewName));
        vmd.setModuleName(ModuleUtils.getModuleName(outputtedViewName));

        vmd.setSchema(viewSchema);
        // Always true, as we are iterating over the list of output views
        vmd.setOutput(true);
        vmd.setOutputAlias(outputName);
        // Exporting of imported views is not allowed in v2.0
        vmd.setExported(false);
        vmd.setExternal(isExternal);
        vmd.setExternalName(externalName);

        // Only write out cost records of non-external output views, because
        // the planner is currently not setting the cost record of external output views
        if (false == isExternal) {
          CreateViewNode importedViewNode = ((ViewCatalogEntry) vce).getParseTreeNode();

          // many imported view nodes do not have token information for the error message
          // so use a null token, which is handled properly by ParseException
          // TODO: When Task #34722 is fixed, get rid of this null check and clause
          if (importedViewNode == null) {
            vmd.setCostRecord(catalog.getCostRecord(pair.first), null, pair.first);
          } else {
            vmd.setCostRecord(catalog.getCostRecord(pair.first),
                ((ViewCatalogEntry) vce).getParseTreeNode().getEndOfStmtToken(), pair.first);
          }
        }

        mmd.getViews().getView().add(vmd);
      }
    }

    // SPECIAL CASE: Always serialize the view Document and make sure to add its comment
    {

      ViewMetadataImpl vmd = new ViewMetadataImpl();
      vmd.setViewName(Constants.DEFAULT_DOC_TYPE_NAME);

      TupleSchema tempDocSchema =
          ((ViewCatalogEntry) catalog.lookupView(Constants.DEFAULT_DOC_TYPE_NAME, null))
              .getSchema();

      // Sort the doc schema
      TupleSchema sortedDocSchema = ModuleUtils.createSortedDocSchema(tempDocSchema);
      vmd.setSchema(sortedDocSchema);

      vmd.setOutput(catalog.isOutputView(Constants.DEFAULT_DOC_TYPE_NAME, null));
      vmd.setExported(false);
      vmd.setExternal(false);

      // This token is unlikely to be meaningful, since the document plan is internally generated
      vmd.setCostRecord(catalog.getCostRecord(Constants.DEFAULT_DOC_TYPE_NAME),
          catalog.getDocumentPlan().getSchemaNode().getEndOfStmtToken(),
          Constants.DEFAULT_DOC_TYPE_NAME);

      // Comment for the view Document comes from the module comment.
      AQLDocComment moduleComment = catalog.getComment();
      if (null != moduleComment)
        vmd.setComment(catalog.getComment().getDocumentComment());

      mmd.getViews().getView().add(vmd);

    }
    // END SPECIAL CASE

    // pass 2: external views
    ArrayList<CreateExternalViewNode> externalViews = catalog.getCreateExternalViewNodes();
    for (CreateExternalViewNode cevn : externalViews) {
      String viewName = cevn.getExternalViewName();
      ExternalViewCatalogEntry evce =
          (ExternalViewCatalogEntry) catalog.lookupView(viewName, cevn.getEndOfStmtToken());
      TupleSchema viewSchema = evce.getSchema();

      ViewMetadataImpl vmd = new ViewMetadataImpl();
      vmd.setViewName(cevn.getUnqualifiedName());
      vmd.setSchema(viewSchema);
      vmd.setOutput(catalog.isOutputView(viewName, cevn.getEndOfStmtToken()));
      vmd.setExported(catalog.isExportedView(cevn.getExternalViewName()));
      vmd.setExternal(true);
      vmd.setExternalName(cevn.getExternalName());

      vmd.setCostRecord(catalog.getCostRecord(viewName), cevn.getEndOfStmtToken(), viewName);
      vmd.setComment(cevn.getComment());

      mmd.getViews().getView().add(vmd);
    }

    // pass 3: add exported/output detag views
    ArrayList<DetagCatalogEntry> detagCatalogEntries = catalog.getDetagCatalogEntries();
    for (DetagCatalogEntry dce : detagCatalogEntries) {
      String viewName = dce.getName();
      TupleSchema viewSchema = dce.getDetagSchema();

      // add only the detagged/auxilary views marked as exported/output
      if (catalog.isExportedView(viewName)
          || catalog.isOutputView(viewName, dce.getParseTreeNode().getEndOfStmtToken())) {
        ViewMetadataImpl vmd = new ViewMetadataImpl();
        vmd.setViewName(dce.getUnqualifiedName());
        vmd.setSchema(viewSchema);
        vmd.setOutput(catalog.isOutputView(viewName, dce.getParseTreeNode().getEndOfStmtToken()));
        vmd.setExported(catalog.isExportedView(viewName));
        vmd.setExternal(false);
        vmd.setCostRecord(catalog.getCostRecord(viewName),
            dce.getParseTreeNode().getEndOfStmtToken(), viewName);

        // Serialize the comment
        if (null != dce.getParseTreeNode().getComment()) {
          if (dce.getParseTreeNode().getDetaggedDocName().equals(viewName)) {
            // The detag view itself
            vmd.setComment(dce.getParseTreeNode().getComment().getDetagViewComment());
          } else {
            // An auxiliary view
            vmd.setComment(dce.getParseTreeNode().getComment()
                .getDetagAuxiliaryViewComment(dce.getUnqualifiedName()));
          }
        }

        mmd.getViews().getView().add(vmd);
      }
    }

    // pass 4: add tables
    Tables tables = new Tables();
    mmd.setTables(tables);

    ArrayList<CreateTableNode> ctNodes = catalog.getCreateTableNodes();
    for (CreateTableNode ctn : ctNodes) {
      String tableName = ctn.getTableName();
      if (catalog.isExportedTable(tableName) || ctn.getIsExternal()) {
        TableCatalogEntry tce = (TableCatalogEntry) catalog.lookupTable(tableName);
        TupleSchema tableSchema = tce.getSchema();
        CreateTableNode tableNode = tce.getParseTreeNode();

        TableMetadataImpl tmd = new TableMetadataImpl();
        tmd.setTableName(ctn.getUnqualifiedName());
        tmd.setSchema(tableSchema);
        tmd.setExported(catalog.isExportedTable(ctn.getTableName()));
        tmd.setExternal(tableNode.getIsExternal());

        // AQL parser enforces that either allow_empty or required is not null.
        if (tableNode.getIsExternal()) {
          if (tableNode.isAllowEmpty() != null) {
            tmd.setAllowEmpty(tableNode.isAllowEmpty());
          } else {
            tmd.setRequired(tableNode.isRequired());
          }
        }
        tmd.setComment(ctn.getComment());

        mmd.getTables().getTable().add(tmd);
      }
    }
    // pass 5: add dictionaries
    Dictionaries dicts = new Dictionaries();
    mmd.setDictionaries(dicts);
    ArrayList<CreateDictNode> cdNodes = catalog.getCreateDictNodes();
    for (CreateDictNode cdn : cdNodes) {

      DictCatalogEntry dictCatalogEntry = catalog.lookupDict(cdn.getDictname(), null, null);

      // In addition to exported and external dictionaries, we also serialize metadata for internal
      // dictionaries
      // dependent on external tables; This metadata will be used by loader to compile these
      // dictionaries using the
      // table entries from ExternalTypeInfo object.
      if (catalog.isExportedDict(cdn.getDictname()) || dictCatalogEntry.isExternallyDependent()) {
        DictionaryMetadataImpl dmd = new DictionaryMetadataImpl();
        DictParams dictParams = cdn.getParams();
        dmd.setCase(String.valueOf(dictParams.getDefaultCase()));
        dmd.setDictName(cdn.getUnqualifiedName());
        dmd.setExported(catalog.isExportedDict(cdn.getDictname()));
        dmd.setExternal(dictParams.getIsExternal());
        dmd.setLanguages(dictParams.getLangStr());

        // AQL parser enforces that either allow_empty or required is not null.
        if (dictParams.getIsExternal()) {
          if (dictParams.isAllowEmpty() != null) {
            dmd.setAllowEmpty(dictParams.isAllowEmpty());
          } else {
            dmd.setRequired(dictParams.isRequired());
          }
        }
        dmd.setLemmaMatch(dictParams.supportLemmaMatch());
        dmd.setComment(cdn.getComment());

        // SPECIAL CASE: Serialize external table name and the column name from which dictionary
        // entries come; we also
        // mark this dictionary as coming from external table by setting 'srcExtTable' attribute
        String sourceTabName = dictParams.getTabName();
        if (null != sourceTabName) {
          TableCatalogEntry dependentExtTab =
              (TableCatalogEntry) catalog.lookupTable(sourceTabName);
          if (null != dependentExtTab && dependentExtTab.getParseTreeNode().getIsExternal()) {
            dmd.setSrcExtTable(true);
            dmd.setExtTableName(dependentExtTab.getName());
            dmd.setExtTableColName(dictParams.getTabColName());
          } else {
            dmd.setSrcExtTable(false);
          }
        }
        // SPECIAL CASE END

        mmd.getDictionaries().getDict().add(dmd);
      }
    }

    // pass 7: add exported user defined functions
    Functions funcs = new Functions();
    mmd.setFunctions(funcs);
    ArrayList<CreateFunctionNode> createFuncNodes = catalog.getCreateFunctionNodes();
    for (CreateFunctionNode cfn : createFuncNodes) {
      // Only exported functions are added
      if (catalog.isExportedFunction(cfn.getFunctionName())) {
        FunctionMetadataImpl fmd = new FunctionMetadataImpl();
        fmd.setFunctionName(cfn.getUnqualifiedName());

        // prepare and populate UDF parameters in function metadata
        ArrayList<Pair<String, String>> paramNamesAndType = cfn.getParamNamesAndType();
        List<InputType.Param> params = new ArrayList<InputType.Param>();
        for (Pair<String, String> paramNameType : paramNamesAndType) {
          String paramName = paramNameType.first;
          String paramType = paramNameType.second;

          InputType.Param param = new InputType.Param();
          param.setName(paramName);
          param.setType(paramType);

          params.add(param);
        }
        InputType inputType = new InputType();
        inputType.getParam().addAll(params);
        fmd.setInput(inputType);
        // end: population of udf parameters

        // Table schemas are serialized differently from scalar types
        String retTypeStr = cfn.getReturnType().getTypeString();
        String retLikeParam = cfn.getUDFParams().getRetSpanLike();

        // Generate an appropriate error message if we encounter missing metadata regarding the
        // specific type of scalar
        // list
        if (FieldType.SCALAR_LIST_TYPE.getTypeName().equals(retTypeStr) && null == retLikeParam) {
          throw AQLParserBase.makeException(cfn.getEndOfStmtToken(),
              "The metadata of the compiled module that contains function '%s' is missing information about the return type for the function.  "
                  + "Please recompile this function with the latest version of BigInsights Text Analytics.",
              cfn.getFunctionName());
        }

        fmd.setReturnType(retTypeStr);
        fmd.setReturnLikeParam(retLikeParam);
        fmd.setLanguage(cfn.getLanguage());
        fmd.setDeterministic(cfn.isDeterministic());
        fmd.setExported(true);
        fmd.setExternalName(cfn.getExternalName());
        fmd.setReturnsNullOnNullInput(cfn.isReturnsNullOnNullInp());
        fmd.setComment(cfn.getComment());

        mmd.getFunctions().getFunction().add(fmd);
      }
    }

    tam.setMetadata(mmd);
  }

  /**
   * This method compiles all the internal dictionaries from the catalog,and later add the
   * {@link CompiledDictionary} object to the given {@link TAM} instance.
   * 
   * @param catalog catalog containing all the dictionaries
   * @param tam tam instance for the module
   * @throws ParseException
   */
  private void compileDictionaries(Catalog catalog, TAM tam) throws ParseException, Exception {
    // data structure, to perform memoization during dictionary compilation
    DictMemoization dm = new DictMemoization();

    // get all internal dictionaries; we compile only internal dictionaries entries
    ArrayList<CreateDictNode> allDictionaryNodes = catalog.getCreateDictNodes(true);

    // Fetch the tokenizer from catalog
    Tokenizer tokenizer = catalog.getTokenizer();

    for (CreateDictNode createDictNode : allDictionaryNodes) {
      DictParams dictParams = createDictNode.getParams();

      // Fetch entries from dictionary parse nodes
      List<String> entries = fetchDictionaryEntry(createDictNode, catalog);
      CompiledDictionary compiledDictionary = null;

      // Compile the dictionary; synchronize on the tokenizer since we added new feature in
      // CompileAQLParams to cache
      // the tokenizer
      synchronized (tokenizer) {
        compiledDictionary = new DictFile(entries, dictParams, null).compile(tokenizer, dm);
      }

      // Add to TAM
      tam.addDict(compiledDictionary.getCompiledDictName(), compiledDictionary);
    }

  }

  /**
   * This method adds the contents of all jar files referenced in the catalog to the given
   * {@link TAM} instance.
   * 
   * @param catalog catalog containing all the jar files
   * @param tam tam instance for the module
   */
  private void addJars(Catalog catalog, TAM tam) throws Exception {
    ArrayList<AbstractJarCatalogEntry> entries = catalog.getAllJarFiles();
    for (AbstractJarCatalogEntry entry : entries) {
      tam.addJar(entry.getName(), entry.getJarBytes());
    }
  }

  /**
   * Method to fetch entries from different type of dictionary parse tree nodes. This method expects
   * only two type of dictionary nodes: {@link CreateDictNode.Inline} and
   * {@link CreateDictNode.FromTable}, since dictionary nodes of type
   * {@link CreateDictNode.FromFile} are transformed to Inline node in the pre processing phase.
   * 
   * @param createDictNode dictionary parse tree node from which entries should be fetched
   * @param catalog fully populated catalog; this is use to lookup table node for dictionary coming
   *        from table
   * @return list of dictionary entry string
   * @throws Exception
   */
  private List<String> fetchDictionaryEntry(CreateDictNode createDictNode, Catalog catalog)
      throws Exception {
    DictParams dictParams = createDictNode.getParams();
    List<String> entries = null;
    if (createDictNode instanceof CreateDictNode.Inline) {
      entries = ((CreateDictNode.Inline) createDictNode).getEntries();
    } else if (createDictNode instanceof CreateDictNode.FromTable) {
      String tabName = dictParams.getTabName();
      String tabColName = dictParams.getTabColName();

      // lookup for dictionary source table, parse tree node
      TableCatalogEntry sourceTableCatalogEntry = (TableCatalogEntry) catalog.lookupTable(tabName);
      CreateTableNode sourceTableParseNode = sourceTableCatalogEntry.getParseTreeNode();

      // fetch dictionary entries from source table
      List<Object[]> columnValues = sourceTableParseNode.getColumnValues(new String[] {tabColName});
      entries = new ArrayList<String>();
      for (Object[] record : columnValues) {
        entries.add((String) record[0]);
      }
    } else {
      throw new Exception(String.format("Unexpected dictionary parse tree node of type %s",
          createDictNode.getClass().getName()));
    }
    return entries;
  }

  /**
   * Returns the last segment of the URI as module name
   * 
   * @param inputModuleURI
   * @return module name from inputModuleURI
   * @throws CompilerException
   */
  private String getModuleName(String inputModuleURI) throws CompilerException {
    if (inputModuleURI == null || inputModuleURI.trim().length() == 0) {
      return "";
    }
    if (inputModuleURI.endsWith("/")) {
      inputModuleURI = inputModuleURI.substring(0, inputModuleURI.lastIndexOf("/"));
    }

    if (inputModuleURI.endsWith("\\")) {
      inputModuleURI = inputModuleURI.substring(0, inputModuleURI.lastIndexOf("\\"));
    }

    int idxSlash = inputModuleURI.lastIndexOf('/');
    if (idxSlash < 0) {
      idxSlash = inputModuleURI.lastIndexOf("\\");
    }

    // Decode URI so as to properly return spaces and other invalid URI characters in module name
    try {
      if (idxSlash < 0) {
        return URLDecoder.decode(inputModuleURI, Constants.ENCODING_UTF8);
      } else {
        return URLDecoder.decode(inputModuleURI.substring(idxSlash + 1), Constants.ENCODING_UTF8);
      }
    } catch (UnsupportedEncodingException e) {
      CompilerException ce = new CompilerException(catalog.getSummary());
      ce.addError(e);
      throw ce;
    }

  }

  public void setSummary(CompilationSummaryImpl summary) {
    catalog.setSummary(summary);

  }

}
