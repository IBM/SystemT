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
package com.ibm.avatar.api;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.base.ProfileRecord;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.output.Sink;
import com.ibm.avatar.algebra.util.dict.CompiledDictionary;
import com.ibm.avatar.algebra.util.dict.DictFile;
import com.ibm.avatar.algebra.util.dict.DictMemoization;
import com.ibm.avatar.algebra.util.dict.DictParams;
import com.ibm.avatar.algebra.util.dict.DictionarySerializer;
import com.ibm.avatar.algebra.util.dict.TextSerializer;
import com.ibm.avatar.algebra.util.document.CsvFileReader;
import com.ibm.avatar.algebra.util.file.FileOperations;
import com.ibm.avatar.algebra.util.tokenize.Tokenizer;
import com.ibm.avatar.algebra.util.tokenize.TokenizerConfig;
import com.ibm.avatar.aog.AOGConversionException;
import com.ibm.avatar.aog.AOGMultiOpTree;
import com.ibm.avatar.aog.AOGMultiOpTree.PlaceholderOp;
import com.ibm.avatar.aog.AOGMultiOpTree.RegexesTokOp;
import com.ibm.avatar.aog.AOGOpTree;
import com.ibm.avatar.aog.AOGOpTree.DocScanOp;
import com.ibm.avatar.aog.AOGOutputExpr;
import com.ibm.avatar.aog.AOGParseTree;
import com.ibm.avatar.aog.AOGParseTreeNode;
import com.ibm.avatar.aog.AOGParser;
import com.ibm.avatar.aog.AOGParserConstants;
import com.ibm.avatar.aog.AOGPlan;
import com.ibm.avatar.aog.AnnotationReaderFactory;
import com.ibm.avatar.aog.BufferOutputFactory;
import com.ibm.avatar.aog.OutputFactory;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.aog.SymbolTable;
import com.ibm.avatar.api.exceptions.DependencyResolutionException;
import com.ibm.avatar.api.exceptions.IncompatibleCompiledDictException;
import com.ibm.avatar.api.exceptions.IncompatibleTokenizerConfigException;
import com.ibm.avatar.api.exceptions.InvalidDictionaryFileFormatException;
import com.ibm.avatar.api.exceptions.InvalidTableEntryException;
import com.ibm.avatar.api.exceptions.ModuleLoadException;
import com.ibm.avatar.api.exceptions.NoSuchModuleLoadedException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException.ExceptionType;
import com.ibm.avatar.api.tam.DictionaryMetadata;
import com.ibm.avatar.api.tam.ModuleMetadata;
import com.ibm.avatar.api.tam.ModuleMetadataFactory;
import com.ibm.avatar.api.tam.MultiModuleMetadata;
import com.ibm.avatar.api.tam.TableMetadata;
import com.ibm.avatar.api.tam.ViewMetadata;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.aql.tam.DictionaryMetadataImpl;
import com.ibm.avatar.aql.tam.ModuleUtils;
import com.ibm.avatar.aql.tam.MultiModuleMetadataImpl;
import com.ibm.avatar.aql.tam.TAM;
import com.ibm.avatar.aql.tam.TAMSerializer;
import com.ibm.avatar.logging.Log;

/**
 * This class primarily loads the operator graph and provide API to perform extraction using the
 * loaded Operator graph. There are additional API's to provide information on the schemas of the
 * extractor's document schema, output views and external views.
 * 
 */
public class OperatorGraphImpl extends OperatorGraph {
  /** Prefix for SDM node */
  private static final String SDM_PREFIX = "SDM_";

  /** Prefix for SRM node */
  private static final String SRM_PREFIX = "SRM_";

  /** Constant used to extract target column name from SRM/SDM node nick name */
  private static final String TARGETCOL_SEPARATOR = "_OVER";

  public static final String LOAD_SLEEP_TIME_PROPERTY = "com.ibm.avatar.load.sleep";

  private static final long DEFAULT_THREAD_SLEEP_TIME = -1;

  private static final String ENCODING_UTF8 = "UTF-8";

  /**
   * How many passes we make through the subtrees before giving up on conversion.
   */
  private static final int MAX_NUM_CONVERSION_PASS = 100000;

  /**
   * Metadata for all the TAMs in this operator graph.<br/>
   */
  protected MultiModuleMetadata moduleSetMetadata;

  /**
   * graphInfo contains the OperatorGraph runner. Always access it through getGraph() method to
   * ensure non null reference.
   */
  protected graphInfo graphInfoRef = null;

  /**
   * Factory object for tokenizers with part of speech detection; default is built-in whitespace
   * tokenizer configuration.
   */
  private TokenizerConfig tokenizerCfg = new TokenizerConfig.Standard();

  /**
   * Chunker object to divide long documents into shorter ones.
   */
  private final Chunker chunker = null;

  /**
   * Combined (stitched) plan of all input modules and their dependents
   */
  private AOGPlan plan;

  /**
   * Loaded TAM contents. Key: module name Value: Loaded TAM object
   */
  private Map<String, TAM> moduleNameToTam;

  /**
   * Loaded dictionaries.
   */
  private Map<String, CompiledDictionary> origLoadedDicts;

  /**
   * Original DictFiles before compilation.
   */
  private Map<String, DictFile> origDictFiles;

  /**
   * Loaded tables.
   */
  private Map<String, ArrayList<ArrayList<String>>> origLoadedTables;

  /**
   * Tokenizer instance created using the specified {@link #tokenizerCfg}. This tokenizer will be
   * used to compile the specified external dictionaries.
   */
  private Tokenizer tokenizer = null;

  /**
   * Private constructor to instantiate an {@link OperatorGraphImpl} object. Use factory method
   * {@link OperatorGraphImpl#createOGImpl(String[], String, ExternalTypeInfo, TokenizerConfig)} to
   * instantiate this class.
   * 
   * @param moduleNames the module(s) to be loaded. (Additional modules may be loaded if they are
   *        required by modules in moduleNames)
   * @param modulePath a semicolon-separated list of directories and/or .jar/.zip archives
   *        containing all the module(s) to be loaded
   * @param externalTypeInfo information encapsulating the content of all external dictionaries and
   *        tables used by the module(s)
   * @param tokenizerCfg tokenizer configuration to use
   * @throws TextAnalyticsException
   */
  private OperatorGraphImpl(String[] moduleNames, String modulePath,
      ExternalTypeInfo externalTypeInfo, TokenizerConfig tokenizerCfg)
      throws TextAnalyticsException {
    TreeMap<String, TAM> tamRegister = new TreeMap<>();

    try {
      if (null != tokenizerCfg)
        this.tokenizerCfg = tokenizerCfg;

      modulePath = FileOperations.resolveModulePath(modulePath);

      // load all modules and store them into tamRegister
      loadModules(moduleNames, modulePath, tamRegister);

      // loads all the metadata corresponding to these modules
      // also validates the loaded modules for conflicting output alias names, tokenizer, and doc
      // schema compatibility
      moduleSetMetadata =
          ModuleMetadataFactory.readAllMetaData(moduleNames, modulePath, tamRegister);

      // load external dictionaries and table from ExternalTypeInfo object into the following out
      // dictionaries and
      // tables map
      Map<String, CompiledDictionary> outLoadedDict = new HashMap<>();
      Map<String, DictFile> outDictFiles = new HashMap<>();
      Map<String, ArrayList<ArrayList<String>>> outLoadedTable = new HashMap<>();
      loadETI((ExternalTypeInfoImpl) externalTypeInfo, outLoadedDict, outDictFiles, outLoadedTable);

      // store the original loaded dictionaries and tables to restore the externals before compiled
      // dicts from
      // external tables are added to outLoadedDict.
      this.origLoadedDicts = new HashMap<>(outLoadedDict);
      this.origDictFiles = new HashMap<>(outDictFiles);
      this.origLoadedTables = new HashMap<>(outLoadedTable);

      // Now that external dictionaries and tables are loaded, compile internal dictionaries
      // dependent of external
      // tables
      Map<String, CompiledDictionary> compileDictFromExtTable =
          compileDictFromExtTable(outLoadedTable);

      // Unionize the loaded dictionaries with dictionaries coming from external tables
      if (!compileDictFromExtTable.isEmpty())
        outLoadedDict.putAll(compileDictFromExtTable);

      plan = stitchModules(outLoadedDict, outLoadedTable, tamRegister, readerCallback,
          this.tokenizerCfg);

      this.moduleNameToTam = tamRegister;

      graphInfoRef = createGraphInfo();

    } catch (Throwable t) {
      t.printStackTrace();
      throw TextAnalyticsException.convertToTextAnalyticsException(t, ExceptionType.RUNTIME_ERROR);
    } finally {
      // Release the tokenizer object created for dictionary compilation
      this.tokenizer = null;
    }
  }

  /*
   * PUBLIC STATIC METHOD
   */

  /**
   * Factory method to instantiate {@link OperatorGraphImpl} object.
   * 
   * @param moduleNames the module(s) to be loaded. (Additional modules may be loaded if they are
   *        required by modules in moduleNames)
   * @param modulePath a semicolon-separated list of directories and/or .jar/.zip archives
   *        containing all the module(s) to be loaded
   * @param info information encapsulating the content of all external dictionaries and tables used
   *        by the module(s)
   * @param tokenizerCfg tokenizer configuration to use
   * @return an {@link OperatorGraphImpl} instance
   */
  public static OperatorGraph createOGImpl(String[] moduleNames, String modulePath,
      ExternalTypeInfo externalTypeInfo, TokenizerConfig tokenizerCfg)
      throws TextAnalyticsException {
    return new OperatorGraphImpl(moduleNames, modulePath, externalTypeInfo, tokenizerCfg);
  }

  /**
   * Loads the given set of modules from the modulePath and stores the loaded TAM objects into
   * nameToTAM output parameter
   */
  public static void loadModules(String[] moduleNames, String modulePath,
      Map<String, TAM> nameToTAM) throws ModuleLoadException {
    // Pass 1: load modules from .tam files into the internal tam cache.
    for (String moduleName : moduleNames) {
      try {
        loadModule(moduleName, modulePath, nameToTAM);
      } catch (Exception e) {
        throw new ModuleLoadException(moduleName, e);
      }
    }
  }

  /**
   * Parses the AOG files from multiple modules and stitches the modules together to create a
   * combined AOGPlan.
   * 
   * @param loadedDict a map of dictionary names (both internal and external) and their compiled
   *        form
   * @param loadedTable a map of external table names and their contents
   * @param nameToTAM a collection of TAM objects that are to be stitched together
   * @param catalog pointer to the AOG parser's copy of the AQL catalog
   * @return combined AOG of all TAMs put together
   * @throws ModuleLoadException if there is any problem in loading one or more modules
   */
  public static AOGPlan stitchModules(Map<String, CompiledDictionary> loadedDict,
      Map<String, ArrayList<ArrayList<String>>> loadedTable, Map<String, TAM> nameToTAM,
      AnnotationReaderFactory readerCallback, TokenizerConfig tokenizerCfg)
      throws ModuleLoadException {
    Set<String> tamNames = new HashSet<String>();

    try {
      String loadTimeTokenizerType = tokenizerCfg.getName();

      boolean loadExtArtifacts = (null != loadedDict || null != loadedTable);

      tamNames = nameToTAM.keySet();

      // Pass 1: Create parse tree nodes out of each TAM
      ArrayList<AOGParseTree> aogTrees = new ArrayList<AOGParseTree>();
      for (TAM tam : nameToTAM.values()) {
        String moduleName = tam.getModuleName();

        // check load-time tokenizer compatibility with compile-time tokenizer
        // this check still exists because the previous validator only checks
        // that all modules have the same compile-time tokenizer
        String compileTimeTokenizer = tam.getMetadata().getTokenizerType();
        if (false == loadTimeTokenizerType.equals(compileTimeTokenizer)) {
          throw new IncompatibleTokenizerConfigException(moduleName, compileTimeTokenizer,
              loadTimeTokenizerType);
        }

        ByteArrayInputStream in =
            new ByteArrayInputStream(tam.getAog().getBytes(Constants.ENCODING_UTF8));

        // 1.1 : Get all internal compiled dicts
        Map<String, CompiledDictionary> internalCompiledDicts = getInternalCompiledDicts(tam);

        // 1.2: Load external dictionaries
        if (null != loadedDict) {
          Map<String, CompiledDictionary> dictsDependentOnExtArtifacts =
              getExtDictForModule(loadedDict, moduleName);
          for (Entry<String, CompiledDictionary> entry : dictsDependentOnExtArtifacts.entrySet()) {
            CompiledDictionary dict = entry.getValue();
            String dictTokenizer = dict.getTokenizerType();
            if (false == compileTimeTokenizer.equals(dictTokenizer)) {

              String message = String.format(
                  "External dictionary %s has tokenizer type %s, which is incompatible with tokenizer type %s specified for module %s.",
                  dict.getCompiledDictName(), dictTokenizer, compileTimeTokenizer, moduleName);

              throw new IncompatibleTokenizerConfigException(message);

            }

          }
          // merge external and internal dictionaries
          if (!dictsDependentOnExtArtifacts.isEmpty())
            internalCompiledDicts.putAll(dictsDependentOnExtArtifacts);
        }

        // 1.3: Load external tables
        Map<String, ArrayList<ArrayList<String>>> extTable = null;
        if (null != loadedTable) {
          extTable = getExtTabForModule(loadedTable, moduleName);
        }

        // 1.4: Initialize the AOG parser.
        AOGParser parser;
        if (true == loadExtArtifacts) {
          // pass in the loaded dictionaries and tables to the parser
          parser = new AOGParser(moduleName, in, ENCODING_UTF8, internalCompiledDicts, extTable);
        } else {
          parser = new AOGParser(moduleName, in, ENCODING_UTF8, internalCompiledDicts);
        }

        // Add jar information for jar files from the TAM to the parser's catalog
        for (Entry<String, byte[]> entry : tam.getAllJars().entrySet()) {
          // System.err.printf ("Adding jar file for name '%s' to parser catalog\n", entry.getKey
          // ());
          parser.getCatalog().addJarFileAOG(entry.getKey(), entry.getValue());
        }

        // 1.5: Parse the AOG
        AOGParseTree tree = parser.Input();

        // 1.6 Add parse tree to aogTrees list
        aogTrees.add(tree);
      }

      // free the tam register here because it's not used after pass 1.
      nameToTAM = null;

      // 1.6: Merge Output nodes
      AOGOutputExpr unionizedOutputs = new AOGOutputExpr();
      for (AOGParseTree tree : aogTrees) {
        ArrayList<String> treeLevelOutputs = tree.getOutputs().getOutputs();
        for (String output : treeLevelOutputs) {
          unionizedOutputs.addOutput(output);
        }
      }
      // Pass 2: Stitch the trees together
      // In this pass we do the following:
      // a) Convert any instances of AOGMultiOpTree into single AOGOpTrees.
      // b) Build symbol table at each AOGParseTree level
      // c) Build a unionized symbol table
      // d) Merge duplicate SDM and SRM nodes
      // e) Create unionized DocScanTreeStub
      // g) create AOGPlan

      // 2.1: Convert sub trees and create individual symbol tables.
      for (AOGParseTree tree : aogTrees) {
        tree.setReaderCallback(readerCallback);
        tree.convertSubtrees();
        tree.buildSymTab();
      }

      // 2.2: Unionize all subtrees except for Document, SDM and SRM nodes
      DocScanOp documentSubtree = null;
      HashMap<String, AOGOpTree> uniqueSDMNodes = new HashMap<String, AOGOpTree>();
      ArrayList<AOGOpTree> unionizedSubtrees = new ArrayList<AOGOpTree>();
      Map<String, TupleSchema> subtreeSchemas = new HashMap<String, TupleSchema>();

      for (AOGParseTree tree : aogTrees) {
        ArrayList<AOGOpTree> subtrees = tree.getConvertedSubTrees();
        for (AOGOpTree subtree : subtrees) {

          // for each document subtree, merge with existing document subtree(s)
          if (subtree instanceof DocScanOp) {

            if (documentSubtree == null) {
              documentSubtree = (DocScanOp) subtree;
            }

            subtreeSchemas.put(subtree.getModuleName(), ((DocScanOp) subtree).getDocSchema());

            // else {
            // documentSubtree.setDocSchema (ModuleUtils.mergeSchema (documentSubtree.getDocSchema
            // (),
            // ((DocScanOp) subtree).getDocSchema (), subtree.getModuleName ()));
            // }
          } else if (subtree.getNickname().startsWith("SDM_OUTPUT_")) {
            String nickName = subtree.getNickname();
            // Pick only unique SDM_OUTPUT_ operators across the modules
            if (false == uniqueSDMNodes.containsKey(nickName)) {
              uniqueSDMNodes.put(nickName, subtree);
            }
          } else {
            // add it to unionizedSubtrees
            unionizedSubtrees.add(subtree);
          }
        } // end: for-each subtree
      } // end: for-each AOGTree

      // 2.3 Add unionized document subtree
      documentSubtree.setDocSchema(ModuleUtils.computeMergedSchema(subtreeSchemas));
      unionizedSubtrees.add(documentSubtree);

      // 2.4: Add unique SDM_OUTPUT_ nodes
      unionizedSubtrees.addAll(uniqueSDMNodes.values());

      // 2.5: Build unionized Symbol table
      SymbolTable unionizedSymTab = new SymbolTable();
      for (AOGParseTree tree : aogTrees) {
        unionizedSymTab.add(tree.getSymTab());
      }

      // 2.6: Build unionized catalog
      ArrayList<Catalog> individualCatalogs = new ArrayList<Catalog>();
      for (AOGParseTree tree : aogTrees) {
        individualCatalogs.add(tree.getCatalog());
      }
      Catalog unionizedCatalog = new Catalog(individualCatalogs);

      // 2.7 Merge SDM_TEMP_ and SRM_TEMP_ nodes across the modules
      mergeSharedNodes(unionizedSubtrees);

      // Update unionized symbol table with merged SDM and SRM nodes
      for (AOGOpTree aogOpTree : unionizedSubtrees) {
        String nickname = aogOpTree.getNickname();
        if (nickname.startsWith(SDM_PREFIX) || nickname.startsWith(SRM_PREFIX)) {
          unionizedSymTab.addNick(nickname, aogOpTree);
        }
      }

      // 2.8 Remove unused views, views not reachable thru any of the output views, from the
      // unionized list of optrees
      // and symboltable
      removeUnusedViews(unionizedSymTab, unionizedSubtrees, unionizedOutputs);

      // 2.9 Remove stale output nicks(those associated with optrees/views removed in the previous
      // pass), from multi
      // optrees
      removeUnusedNicksFromMultiOpTrees(unionizedSymTab, unionizedSubtrees);

      // 2.10: Create AOG plan
      return toPlan(new BufferOutputFactory(), readerCallback, tokenizerCfg, unionizedSymTab,
          unionizedCatalog, unionizedSubtrees, unionizedOutputs);
    } catch (Exception e) {
      throw new ModuleLoadException(new ArrayList<String>(tamNames), e);
    }

  }

  /**
   * This method removes the OpTrees not reachable through any of the output views. Remvoes the
   * optrees *not* reachable from the specified unionizedSymTab and unionizedSubtrees.
   * 
   * @param unionizedSymTab unionized symbol table across the modules
   * @param unionizedSubtrees unionized list of optrees across the modules
   * @param unionizedOutputs unionized output names acros the modules
   * @throws DependencyResolutionException
   */
  private static void removeUnusedViews(SymbolTable unionizedSymTab,
      ArrayList<AOGOpTree> unionizedSubtrees, AOGOutputExpr unionizedOutputs)
      throws DependencyResolutionException {
    // List to hold views reachable thru any of the output views
    SortedMap<String, AOGOpTree> reachableViews = new TreeMap<String, AOGOpTree>();

    // Output views across the modules
    ArrayList<String> allOuts = unionizedOutputs.getOutputs();

    // Do a dfs traversal of views reachable thru any of the output views
    for (String outView : allOuts) {
      identifyReachableViews(unionizedSymTab.lookupNick(outView), unionizedSymTab, reachableViews);
    }

    // //////////////////////
    // SPECIAL CASE Start
    // (1) Operator schema determination code later expects the *main detag view* to be part of
    // required views,
    // irrespective of it being reachable thru any of the output views. If only auxiliary views are
    // reachable thru the
    // output views, identify the associated main detag view and add it to the list of reachable
    // views
    SortedMap<String, AOGMultiOpTree.PlaceholderOp> reachableMainDetagView =
        new TreeMap<String, AOGMultiOpTree.PlaceholderOp>();
    for (AOGOpTree aogOptree : reachableViews.values()) {
      if (aogOptree instanceof AOGMultiOpTree.PlaceholderOp
          && ((AOGMultiOpTree.PlaceholderOp) aogOptree)
              .getRealNode() instanceof AOGMultiOpTree.DeTagOp) {
        AOGMultiOpTree.PlaceholderOp detagPlaceholder = (AOGMultiOpTree.PlaceholderOp) aogOptree;
        AOGMultiOpTree.DeTagOp actualDetacOp =
            (AOGMultiOpTree.DeTagOp) detagPlaceholder.getRealNode();
        String mainDetagViewNickname = actualDetacOp.getMainDetagViewNick();
        if (false == mainDetagViewNickname.equals(detagPlaceholder.getNickname())) {
          reachableMainDetagView.put(mainDetagViewNickname,
              (AOGMultiOpTree.PlaceholderOp) unionizedSymTab.lookupNick(mainDetagViewNickname));
        }
      }
    }
    reachableViews.putAll(reachableMainDetagView);

    // (2) Retain the 'Document' optree too, irrespective of it being reachable thru any of the
    // output views
    AOGOpTree docOpTree = unionizedSymTab.lookupNick(Constants.DEFAULT_DOC_TYPE_NAME);
    reachableViews.put(Constants.DEFAULT_DOC_TYPE_NAME, docOpTree);
    // SPECIAL CASE End
    // //////////////////////

    // Retain only the reachable views
    unionizedSubtrees.retainAll(reachableViews.values());

    // Update the symbol table accordingly
    Collection<AOGOpTree> optreesInSymtab = new ArrayList<AOGOpTree>(unionizedSymTab.optrees());
    for (AOGOpTree aogOpTree : optreesInSymtab) {
      if (false == reachableViews.containsKey(aogOpTree.getNickname())) {
        unionizedSymTab.remove(aogOpTree.getNickname());
      }
    }
  }

  /**
   * This method performs a depth first traversal to identify views reachable thru any of the output
   * views.
   * 
   * @param opTreeToTraverse current optree node to traverse
   * @param unionizedSymTab unionized symbol table across the modules, to perform lookup
   * @param reachableViews list to maintain reachable view across recursive calls
   * @throws DependencyResolutionException
   */
  private static void identifyReachableViews(AOGOpTree opTreeToTraverse,
      SymbolTable unionizedSymTab, SortedMap<String, AOGOpTree> reachableViews)
      throws DependencyResolutionException {
    // Traverse the view, only if it is not traveresed before
    if (false == reachableViews.containsKey(opTreeToTraverse.getNickname())) {
      reachableViews.put(opTreeToTraverse.getNickname(), opTreeToTraverse);

      ArrayList<AOGOpTree> deps = new ArrayList<AOGOpTree>();
      try {
        opTreeToTraverse.getDeps(unionizedSymTab, deps);
      } catch (ParseException pe) {
        throw new DependencyResolutionException(opTreeToTraverse.getModuleName(),
            opTreeToTraverse.getNickname(), pe.getMessage());
      }
      for (AOGOpTree aogOpTree : deps) {
        identifyReachableViews(aogOpTree, unionizedSymTab, reachableViews);
      }
    }
  }

  /**
   * This method removes the unused output nicks from the {@link AOGMultiOpTree}.
   * 
   * @param unionizedSymTab unionized symbol table across the modules
   * @param unionizedSubtrees unionized list of optrees across the modules
   */
  private static void removeUnusedNicksFromMultiOpTrees(SymbolTable unionizedSymTab,
      ArrayList<AOGOpTree> unionizedSubtrees) {
    for (AOGOpTree aogOpTree : unionizedSubtrees) {

      if (aogOpTree instanceof AOGMultiOpTree.PlaceholderOp) {
        ((AOGMultiOpTree.PlaceholderOp) aogOpTree).getRealNode()
            .removeUnusedOutputNicks(unionizedSymTab);
      }
    }
  }

  /**
   * Returns a pre-defined value used for periodically making the AOG load thread go to sleep in an
   * effort to force it into the background.
   */
  public static Long getLoadSleepTime() {

    String loadSleepTimeProperty = System.getProperty(LOAD_SLEEP_TIME_PROPERTY);
    Long sleepTime = DEFAULT_THREAD_SLEEP_TIME;
    if (loadSleepTimeProperty != null)
      sleepTime = Long.parseLong(loadSleepTimeProperty);

    return sleepTime;
  }

  /**
   * Convert this parse tree into an operator tree and wrap the operator tree in an AOGPlan object.
   * 
   * @param outputFactory factory object for creating output operators
   * @param readerFactory OPTIONAL factory object for creating annotation reader operators
   * @param tokenizerCfg factory object that knows how to create tokenizer instances, or NULL to use
   *        the default internal tokenizer.
   * @param unionizedSymTab symbol table constructed by combining the symbol tables from parsing
   *        multiple different AOG files
   * @param catalog AQL catalog, for reading additional metadata
   * @return execution plan for the parse tree
   * @throws DependencyResolutionException
   */
  public static AOGPlan toPlan(OutputFactory outputFactory, AnnotationReaderFactory readerFactory,
      TokenizerConfig tokenizerCfg, SymbolTable unionizedSymTab, Catalog catalog,
      ArrayList<AOGOpTree> subtrees, AOGOutputExpr outputs)
      throws ParseException, DependencyResolutionException {
    // setReaderCallback (readerFactory);

    TreeMap<String, Operator> outputMap = new TreeMap<String, Operator>();
    TreeMap<String, Integer> indexMap = new TreeMap<String, Integer>();

    // find the docscan op tree, pull the doc schema, and then use it
    TupleSchema docSchema = null;
    for (AOGParseTreeNode tree : subtrees) {
      if (tree instanceof DocScanOp) {
        docSchema = ((DocScanOp) tree).getDocSchema();
      }
    }

    // Doc schema should never be null!
    if (docSchema == null) {
      throw new ParseException("No doc schema specified by AOG");
    }

    // toOpTree() does most of the heavy lifting.
    Sink root =
        toOpTree(unionizedSymTab, catalog, outputFactory, outputMap, indexMap, subtrees, outputs);

    // Build up a list of all the nicknames in the plan
    ArrayList<String> allNicks = new ArrayList<String>();
    for (int i = 0; i < subtrees.size(); i++) {
      String nick = subtrees.get(i).getNickname();
      allNicks.add(nick);
    }

    return AOGParseTree.toPlan(root, outputMap, indexMap, allNicks, tokenizerCfg);
  }

  /*
   * PUBLIC APIs
   */
  @Override
  public Map<String, TupleList> execute(Tuple input, String[] outputTypes,
      Map<String, TupleList> extViewTups) throws TextAnalyticsException {
    try {
      // Acquire a lock (as opposed to a latch) on a thread slot.
      int slotIx = getGraph().lockThreadSlot();

      // Toggle outputs.
      if (null == outputTypes) {
        // null --> all outputs enabled
        getGraph().runner.setAllOutputsEnabled(true, slotIx);
      } else {
        // Caller has requested a specific set of outputs.
        getGraph().runner.setAllOutputsEnabled(false, slotIx);
        for (String outputName : outputTypes) {
          getGraph().runner.setOutputEnabled(outputName, true, slotIx);
        }
      }

      // Annotate the document.
      try {

        // need to clear all the old external view tuples first
        // this fixes defect
        getGraph().runner.clearExternalViewTups(slotIx);

        // push new external view tups if non-null values passed
        if (extViewTups != null && extViewTups.size() > 0) {
          Iterator<String> keys = extViewTups.keySet().iterator();
          while (keys.hasNext()) {
            String extViewName = keys.next();
            TupleList tupList = extViewTups.get(extViewName);
            getGraph().runner.pushExternalViewTups(slotIx, extViewName, tupList);
          }
        }

        getGraph().runner.pushDoc(input, slotIx);
      } catch (Throwable t) {
        t.printStackTrace();

        // Some of the operators might not have completely finished. So when the next document comes
        // in, those iterators
        // might look like they are in use. Make sure we mark them as not in use, so that we
        // completely clear state of
        // the
        // operator graph before the next document comes in. Note that we don't need to clear the
        // childResults buffer,
        // because that is already accomplished by the call to results.clear() inside {@link
        // Operator#getNext()}.
        getGraph().markResultsBufDone(slotIx);

        // Don't forget to release our lock!
        getGraph().unlockThreadSlot(slotIx);
        // Attach short information about the document that caused the exception. We only attach a
        // short version of the
        // document tuple, without the external views, to ensure the resulting message is small
        throw new TextAnalyticsException(t, "Exception on input Document tuple %s",
            input.toString());
      }

      // Copy data into the map that we'll return.
      // Use a TreeMap to ensure consistent results.
      TreeMap<String, TupleList> ret = new TreeMap<String, TupleList>();

      for (String outputName : getGraph().runner.getOutputNames()) {
        if (getGraph().runner.getOutputEnabled(outputName, slotIx)) {
          ret.put(outputName, getGraph().runner.getResults(outputName, slotIx));
        }
      }

      // Release the lock on the thread slot.
      getGraph().unlockThreadSlot(slotIx);

      return ret;
    } catch (Throwable t) {
      throw TextAnalyticsException.convertToTextAnalyticsException(t, ExceptionType.RUNTIME_ERROR);
    }
  }

  @Override
  public ModuleMetadata getModuleMetadata(String moduleName) throws TextAnalyticsException {
    ModuleMetadata tamMetadata = moduleSetMetadata.getModuleMetadata(moduleName);
    if (tamMetadata == null) {
      throw new NoSuchModuleLoadedException(moduleName);
    }
    return tamMetadata;
  }

  @Override
  public String[] getModuleNames() {
    return moduleSetMetadata.getModuleNames();
  }

  @Override
  public void setChunker(Chunker chunker) throws TextAnalyticsException {
    try {
      getGraph().setChunker(chunker);
    } catch (Throwable t) {
      throw TextAnalyticsException.convertToTextAnalyticsException(t, ExceptionType.RUNTIME_ERROR);
    }

  }

  @Override
  public TupleSchema getExternalViewSchema(String viewName) throws TextAnalyticsException {
    try {
      return getGraph().runner.getExternalViewSchema(viewName);
    } catch (Throwable t) {
      throw TextAnalyticsException.convertToTextAnalyticsException(t, ExceptionType.RUNTIME_ERROR);
    }
  }

  @Override
  public String[] getExternalViewNames() throws TextAnalyticsException {
    try {
      return getGraph().runner.getExternalViewNames();
    } catch (Throwable t) {
      throw TextAnalyticsException.convertToTextAnalyticsException(t, ExceptionType.RUNTIME_ERROR);
    }

  }

  @Override
  public String getExternalViewExternalName(String viewName) throws TextAnalyticsException {
    try {
      return getGraph().runner.getExternalViewExternalName(viewName);
    } catch (Throwable t) {
      throw TextAnalyticsException.convertToTextAnalyticsException(t, ExceptionType.RUNTIME_ERROR);
    }
  }

  @Override
  public ArrayList<String> getOutputTypeNames() throws TextAnalyticsException {
    try {
      return getGraph().runner.getOutputNames();
    } catch (Throwable t) {
      throw TextAnalyticsException.convertToTextAnalyticsException(t, ExceptionType.RUNTIME_ERROR);
    }
  }

  @Override
  public TupleSchema getSchema(String outputTypeName) throws TextAnalyticsException {
    try {
      return getGraph().runner.getOutputSchema(outputTypeName);
    } catch (Throwable t) {
      throw TextAnalyticsException.convertToTextAnalyticsException(t, ExceptionType.RUNTIME_ERROR);
    }

  }

  @Override
  public Map<String, TupleSchema> getOutputTypeNamesAndSchema() throws TextAnalyticsException {
    try {
      Map<String, TupleSchema> outViews = new HashMap<String, TupleSchema>();

      for (String outView : this.getOutputTypeNames()) {
        outViews.put(outView, this.getSchema(outView));
      }
      return outViews;
    } catch (Throwable t) {
      throw TextAnalyticsException.convertToTextAnalyticsException(t, ExceptionType.RUNTIME_ERROR);
    }

  }

  @Override
  public String[] getOutputViews() {
    return this.moduleSetMetadata.getOutputViews();
  }

  @Override
  public ViewMetadata getViewMetadata(final String outputViewName) {
    return this.moduleSetMetadata.getViewMetadata(outputViewName);
  }

  @Override
  public Map<String, CompiledDictionary> getOrigLoadedDicts() {
    return this.origLoadedDicts;
  }

  @Override
  public Map<String, DictFile> getOrigDictFiles() {
    return this.origDictFiles;
  }

  @Override
  public Map<String, ArrayList<ArrayList<String>>> getOrigLoadedTables() {
    return this.origLoadedTables;
  }

  @Override
  public Map<String, TAM> getModuleNameToTam() {
    return this.moduleNameToTam;
  }

  @Override
  public TupleSchema getDocumentSchema() throws TextAnalyticsException {
    try {
      return getGraph().runner.getDocSchema();
    } catch (Throwable t) {
      throw TextAnalyticsException.convertToTextAnalyticsException(t, ExceptionType.RUNTIME_ERROR);
    }
  }

  @Override
  public TokenizerConfig getTokenizerConfig() {
    return this.tokenizerCfg;
  }

  /**
   * Method to fetch meta-data for all external dictionaries from the loaded modules.
   *
   * @return map of qualified dictionary name to meta-data.
   */
  @Override
  public Map<String, DictionaryMetadata> getAllExternalDictionaryMetadata() {
    Map<String, DictionaryMetadata> dictionaryMetaData = new HashMap<>();

    String[] externalDictionaries = moduleSetMetadata.getExternalDictionaries();
    for (String dictionaryName : externalDictionaries) {
      dictionaryMetaData.put(dictionaryName,
          moduleSetMetadata.getDictionaryMetadata(dictionaryName));
    }

    return dictionaryMetaData;
  }

  /**
   * Method to fetch meta-data for all external tables from the loaded modules.
   *
   * @return map of qualified table name to meta-data.
   */
  @Override
  public Map<String, TableMetadata> getAllExternalTableMetadata() {
    Map<String, TableMetadata> tableMetaData = new HashMap<>();

    String[] externalTables = moduleSetMetadata.getExternalTables();
    for (String tableName : externalTables) {
      tableMetaData.put(tableName, moduleSetMetadata.getTableMetadata(tableName));
    }

    return tableMetaData;
  }

  /*
   * INTERNAL METHODS
   */

  /**
   * Return the latest profile record for the given thread.
   * 
   * @param threadIx
   * @return
   */
  public ProfileRecord getProfileRecAtLoc(int threadIx) {
    return plan.getCurCodeLoc(threadIx);
  }

  /**
   * THIS METHOD IS FOR INTERNAL USE ONLY!!! This method is only public so that the UIMA API can
   * access it directly.
   */
  public OperatorGraphRunner getInternalImpl_INTERNAL_USE_ONLY(AbstractTupleSchema docSchema)
      throws Exception {
    return getGraph().runner;
  }

  /**
   * Stop any annotation tasks currently underway in other threads. Any in-flight calls to
   * {@link #execute(Tuple, String[], Map))} will throw {@link InterruptedException}.
   * 
   * @throws Exception
   */
  public synchronized void interrupt(AbstractTupleSchema docSchema) throws Exception {
    // There may be more than one thread active within the indicated
    // operator graph. Interrupt all of them.
    for (int i = 0; i < getGraph().availSlots.length; i++) {
      if (false == getGraph().availSlots[i]) {
        // This thread slot is active.
        getGraph().runner.interrupt(i);
      }
    }
  }

  /**
   * Returns a compiled operator graph for the indicated AOG config file, instantiating the graph if
   * necessary.
   * 
   * @param docSchema schema of the document tuple
   * @param compiledDictionaries map of dictionary name vs compiled dictionary object.
   */
  protected synchronized graphInfo createGraphInfo() throws Exception {

    final boolean debug = false;

    graphInfo gi = new graphInfo(new OperatorGraphRunner(plan));

    if (debug) {
      Log.debug("Using tokenizer config: %s", tokenizerCfg);
      Log.debug("Using chunker: %s", chunker);
    }

    return gi;
  }

  /*
   * DATA STRUCTURES
   */

  /**
   * Class for holding information about a compiled operator graph instance.
   */
  private static final class graphInfo {

    /** Bitmap that indicates which thread slots are available. */
    boolean[] availSlots = new boolean[Constants.NUM_THREAD_SLOTS];

    /** The actual operator graph. */
    OperatorGraphRunner runner;

    /**
     * Main constructor.
     * 
     * @param runner basic compiled operator graph
     */
    public graphInfo(OperatorGraphRunner runner) throws Exception {
      this.runner = runner;

      runner.setBufferOutput();

      Arrays.fill(availSlots, true);
    }

    /**
     * Sets a chunker for this operator graph to use on document column 'text' of type Text. If the
     * column does not exist or is not of type Text, an exception will be thrown.
     */
    public void setChunker(Chunker chunker) throws Exception {
      if (chunker != null) {
        this.runner.setChunker(chunker);
      }
    }

    /**
     * Acquires a lock on an available thread slot for this operator graph.
     * 
     * @return index of the slot that is locked.
     */
    public int lockThreadSlot() {
      while (true) {
        synchronized (this) {
          for (int i = 0; i < Constants.NUM_THREAD_SLOTS; i++) {
            if (availSlots[i]) {
              availSlots[i] = false;
              return i;
            }
          }
        }

        // No lock available; back off.
        try {
          Thread.sleep(0, 100);
        } catch (InterruptedException e) {
          // Do nothing.
        }
      }
    }

    /**
     * Mark all iterators of result buffers used by this thread as not in use. This method is only
     * called on exceptions in {@link OperatorGraphImpl#execute(Tuple, String[], Map)} to ensure
     * that the OperatorGraph state is completely reset and there are no tuple lists left in use
     * when the next document comes in this thread.
     * 
     * @param slotIx
     */
    public void markResultsBufDone(int slotIx) {
      synchronized (this) {
        // Free up any memory that the objects in this thread slot are
        // using.
        runner.markResultsBufDone(slotIx);
      }
    }

    /**
     * Release a lock on a thread slot acquired through {@link #lockThreadSlot(graphInfo)}
     */
    public void unlockThreadSlot(int slotIx) {
      synchronized (this) {
        // Free up any memory that the objects in this thread slot are
        // using.
        runner.clearBuffers(slotIx);
        availSlots[slotIx] = true;
      }
    }

  }

  // /////////////// PRIVATE METHODS /////////////////////

  /**
   * Attempts to load a given module from the specified modulePath. Pass null value to modulePath
   * parameter to load the module from system classpath. If the module is successfully loaded, it
   * caches it in the cache 'nameToTAM'
   * 
   * @param moduleName name of the module to load
   * @param modulePath a semicolon-separated list of directories and/or .jar/.zip archives
   *        containing all the module(s) to be loaded. If null value is specified, this method will
   *        attempt to load the module from system classpath.
   * @throws Exception
   */
  private static void loadModule(String moduleName, String modulePath, Map<String, TAM> nameToTAM)
      throws Exception {
    if (false == nameToTAM.containsKey(moduleName)) {
      TAM tam = null;

      if (modulePath == null) {
        // Load from system classpath
        tam = TAMSerializer.load(moduleName);
      } else {
        tam = TAMSerializer.load(moduleName, modulePath);
      }

      // meta-data can be null for non modular AQL's
      if (null != tam.getMetadata()) {
        // load dependencies first before placing the current tam object in tam register
        loadDependentModules(tam.getMetadata().getDependentModules(), modulePath, nameToTAM);
      }

      // Once all dependent modules are loaded, place the current tam in tam register
      nameToTAM.put(moduleName, tam);
    }
  }

  /**
   * Loads the dependent modules based on the dependsOn list
   * 
   * @param dependentModules The list of dependent modules to load
   * @param modulePath location where the dependent modules can be loaded from.
   * @param nameToTAM Map of module name Vs TAM object
   * @throws Exception
   */
  private static void loadDependentModules(List<String> dependentModules, String modulePath,
      Map<String, TAM> nameToTAM) throws Exception {
    for (String module : dependentModules) {
      loadModule(module, modulePath, nameToTAM);
    }
  }

  /**
   * Returns an instance of operator graph. Creates an instance if required.
   * 
   * @return A non-null graphInfo instance
   * @throws Exception
   */
  private graphInfo getGraph() throws Exception {
    if (graphInfoRef == null) {
      graphInfoRef = createGraphInfo();
    }
    return graphInfoRef;
  }

  /**
   * Callback for producing scans over precomputed annotations, or null if unrecognized nicknames
   * should just generate an error.
   */
  private final AnnotationReaderFactory readerCallback = null;

  /**
   * @param readerCallback Callback for producing scans over precomputed annotations; this callback
   *        is only necessary if the AOG file references such annotations.
   */
  // private void setReaderCallback (AnnotationReaderFactory readerCallback)
  // {
  // this.readerCallback = readerCallback;
  // }

  /**
   * Convert this parse tree into an operator tree. The top of this operator tree is always a Sink
   * operator, since in general an AOG file may specify multiple outputs.
   * 
   * @param symtab sumbol table of AOG parser
   * @param catalog AQL catalog, for reading additional metadata not in symbol table
   * @param outputMap map where the conversion process will put handles on the output operators it
   *        produces, indexed by output nickname
   * @param indexMap will map nicknames to argument indexes of the Sink operator at the top of the
   *        plan
   * @throws ParseException
   * @throws DependencyResolutionException
   */
  private static Sink toOpTree(SymbolTable symtab, Catalog catalog, OutputFactory outputFactory,
      Map<String, Operator> outputMap, Map<String, Integer> indexMap, ArrayList<AOGOpTree> subtrees,
      AOGOutputExpr outputs) throws ParseException, DependencyResolutionException {

    // Before we start, tell everyone about the current working directory.
    // AOGOpTree.setDictsPath(dictsPath);

    // Pass 0: Convert any instances of AOGMultiOpTree into single
    // AOGOpTrees.
    // convertSubtrees(); - handled in load modules

    // Pass 1: Build up the nicknames table, which maps nicknames to
    // AOGOpTrees.
    // buildSymTab(input); - handled in load modules

    // Pass 2: Build up a dependency graph and figure out how many outputs
    // each subtree will need.
    HashMap<AOGOpTree, ArrayList<AOGOpTree>> rawdeps =
        new HashMap<AOGOpTree, ArrayList<AOGOpTree>>();
    HashMap<AOGOpTree, Integer> numOutput = new HashMap<AOGOpTree, Integer>();

    computeDeps(symtab, rawdeps, numOutput, outputs);

    // Pass 3: Build up operator trees, starting with the guys that don't
    // depend on anyone.
    convertToOperators(symtab, catalog, rawdeps, numOutput);

    // Pass 4: Attach output operators at the appropriate places.
    createOutputs(outputFactory, symtab, outputMap, outputs);

    // Pass 5: Attach a sink to all the outputs and the trees that go
    // nowhere.
    ArrayList<AOGOpTree> zeroOutputOps = new ArrayList<AOGOpTree>();

    for (AOGOpTree tree : subtrees) {
      Integer noutputObj = numOutput.get(tree);

      if (null == noutputObj) {
        // This pointer should never be null!
        String cause = String.format("No entry in numOutput table for tree with nick $%s (%s)",
            tree.getEntireTreeNick(), tree);
        throw new RuntimeException(
            String.format(AOGConversionException.ERROR_INVALID_AOG, tree.getModuleName(), cause));
      } else {

        if (0 == noutputObj) {
          // if (writeStatus) {
          // Log.log(MsgType.AOGCompileWarning,
          // "Nickname $%s does not go to any output.", tree
          // .getNickname());
          // }
          zeroOutputOps.add(tree);
        }
      }
    }

    // ArrayList<PullOperator> outputOps = new ArrayList<PullOperator>();
    // outputOps.addAll(outputMap.values());

    // The root of the final plan will be a single Sink operator. Build up
    // the set of inputs to that operator.
    // NOTE: DocScan is added to zeroOutputOps and hence retaining this code
    int numSinkOps = zeroOutputOps.size() + outputMap.size();
    Operator[] sinkOps = new Operator[numSinkOps];

    // Start with the operators whose results aren't directed anywhere.
    for (int i = 0; i < zeroOutputOps.size(); i++) {
      AOGOpTree origTree = zeroOutputOps.get(i);
      sinkOps[i] = origTree.getNextOutput();
      indexMap.put(origTree.getEntireTreeNick(), i);
    }

    // Then add the operators whose outputs are materialized.
    ArrayList<String> outputNames = new ArrayList<String>();
    outputNames.addAll(outputMap.keySet());
    for (int i = 0; i < outputNames.size(); i++) {
      String name = outputNames.get(i);
      int destix = i + zeroOutputOps.size();
      sinkOps[destix] = outputMap.get(name);
      indexMap.put(name, destix);
    }

    Sink ret = new Sink(sinkOps);

    // Before returning the operator tree, force schema checks.
    ret.getOutputSchema();

    return ret;
  }

  /**
   * Convert the forest of parse trees into operators, choosing an order of conversion that
   * satisfies all dependencies.
   * 
   * @param symtab symbol table that maps "nicknames" of trees to the trees themselves.
   * @param catalog AQL catalog, for additional metadata
   * @param rawdeps table of dependencies between operator trees
   * @param numOutput map indicating how many outputs each tree will need to be teed to
   * @throws ParseException
   */
  private static void convertToOperators(SymbolTable symtab, Catalog catalog,
      HashMap<AOGOpTree, ArrayList<AOGOpTree>> rawdeps, HashMap<AOGOpTree, Integer> numOutput)
      throws ParseException {
    // Use a TreeMap to ensure that operators are processed in a consistent order
    TreeMap<String, AOGOpTree> todo = new TreeMap<String, AOGOpTree>();
    for (AOGOpTree tree : symtab.optrees()) {
      String name = tree.getEntireTreeNick();
      todo.put(name, tree);
    }

    boolean debug = false;

    int numpass = 0;
    while (todo.size() > 0) {

      // We can't modify <todo> while iterating through it, so build up a
      // list of things to remove.
      ArrayList<AOGOpTree> done = new ArrayList<AOGOpTree>();

      for (AOGOpTree todoTree : todo.values()) {

        ArrayList<AOGOpTree> deps = rawdeps.get(todoTree);

        // A tree can be converted once everything it depends on has
        // been converted.
        boolean canConvert = true;

        for (AOGOpTree dep : deps) {
          if (todo.containsKey(dep.getEntireTreeNick())) {
            canConvert = false;
          }
        }

        if (canConvert) {
          // If the tree has no outputs, we'll give it one later in
          // the conversion process.
          int realNumOutput = numOutput.get(todoTree);

          if (debug) {
            System.err.printf("Nickname '%s' (%s) has %d outputs\n", todoTree.getNickname(),
                todoTree, realNumOutput);
          }

          todoTree.computeOpTree((0 == realNumOutput) ? 1 : realNumOutput, symtab, catalog);

          if (debug) {
            System.err.printf("Done with optree for nickname '%s'\n", todoTree.getNickname());
          }

          done.add(todoTree);
        }
      }

      // Now we can do the deferred removals from the todo set.
      for (AOGOpTree tree : done) {
        todo.remove(tree.getEntireTreeNick());
      }

      numpass++;
      if (numpass > MAX_NUM_CONVERSION_PASS) {
        // Dump the remaining todo list for debugging purposes.
        System.err.print("Remaining subtrees to convert:\n");
        for (AOGOpTree tree : todo.values()) {
          System.err.printf("    %s\n", tree.getNickname());
        }

        throw new ParseException("Failed to convert all subtrees to operators");
      }
    }
  }

  /**
   * Determine the dependencies between the operator trees in the AOG file.
   * 
   * @param symtab symbol table as returned by buildNicknames()
   * @param rawdeps empty map; on return, this map contains a dependency graph in terms of AOGOpTree
   *        nodes.
   * @param numOutput empty map; on return this map indicates how many outputs each tree root will
   *        need to send tuples to.
   * @throws ParseException
   * @throws DependencyResolutionException
   */
  private static void computeDeps(SymbolTable symtab,
      HashMap<AOGOpTree, ArrayList<AOGOpTree>> rawdeps, HashMap<AOGOpTree, Integer> numOutput,
      AOGOutputExpr outputs) throws ParseException, DependencyResolutionException {

    boolean debug = false;

    // Create an entry in the numOutput table for every operator tree that
    // we expect to generate dependencies for.
    for (AOGOpTree tree : symtab.optrees()) {
      numOutput.put(tree, 0);
    }

    // We're also tracking dependencies the special DocScan tree.
    // numOutput.put(symtab.getDocScanTree(), 0);

    // The loop below will modify <nicknames>, so we need to make a copy of
    // the map's values and iterate over that.
    ArrayList<AOGOpTree> allTrees = new ArrayList<AOGOpTree>();
    allTrees.addAll(symtab.optrees());

    for (AOGOpTree tree : allTrees) {
      if (debug) {
        System.out.printf("Finding dependencies for $%s\n", tree.getNickname());

        System.out.printf("Tree is:\n");
        tree.dump(new PrintWriter(System.err, true), 1);
      }

      ArrayList<AOGOpTree> deps = new ArrayList<AOGOpTree>();
      try {
        tree.getDeps(symtab, deps);
      } catch (AOGConversionException aogex) {
        throw new DependencyResolutionException(tree.getModuleName(), tree.getNickname(),
            aogex.getMessage());
      }

      // if (debug) {
      // System.err.printf(" Dependencies for $%s are %s\n",
      // tree.getNickname(), deps);
      // }

      // Dependency generation for the current subtree is complete;
      // remember the result.
      rawdeps.put(tree, deps);

      // Increment the output count of everything the current tree depends
      // on.
      for (AOGOpTree dep : deps) {
        if (debug) {
          System.err.printf("    $%s depends on $%s\n", tree.getNickname(), dep.getNickname());
        }

        Integer oldCountObj = numOutput.get(dep);
        if (null == oldCountObj) {
          throw new ParseException("Corrupted dependency info for " + dep);
        }
        int oldCount = oldCountObj.intValue();

        if (debug) {
          System.err.printf("     --> Setting number of outputs " + "for $%s (%s) to %d\n",
              dep.getNickname(), dep, oldCount + 1);
        }

        numOutput.put(dep, oldCount + 1);

      }
    }

    // Don't forget about the global outputs!
    for (String outputNick : outputs.getOutputs()) {
      if (!symtab.containsNick(outputNick)) {
        throw new ParseException("Don't know about output nickname '" + outputNick + "'");
      }

      AOGOpTree outputTree = symtab.lookupNick(outputNick);
      int oldCount = numOutput.get(outputTree);
      numOutput.put(outputTree, oldCount + 1);
    }

  }

  /**
   * Instantiates output operators, using information in this.outputs, and attach the appropriate
   * Rename and Project operators. Assumes that convertToOperators() has already been run.
   * 
   * @param outputFactory callback for producing output operators for the caller's choice of I/O
   *        system
   * @param symtab symbol table for mapping the names of subtrees to the corresponding root nodes
   * @param outputMap where we will put the instantiation of the output operators, indexed by output
   *        nickname
   * @throws ParseException
   */
  private static void createOutputs(OutputFactory outputFactory, SymbolTable symtab,
      Map<String, Operator> outputMap, AOGOutputExpr outputs) throws ParseException {
    for (String outputNick : outputs.getOutputs()) {

      AOGOpTree outputTree = symtab.lookupNick(outputNick);
      Operator outputOp;

      // Now we can add the appropriate type of I/O operator.
      try {
        outputOp = outputFactory.makeOp(outputTree.getNextOutput(), outputNick);
      } catch (Exception e) {
        throw new ParseException(String.format("Couldn't instantiate output operator for %s: %s",
            outputNick, e.getLocalizedMessage()));
      }

      outputMap.put(outputNick, outputOp);
    }
  }

  /**
   * This method merges the SDM and SRM nodes across the modules. Later, this method replaces the
   * original nodes with the merged SDM and SRM node.
   * 
   * @param unionizedSubtrees unionized list of operator trees from all the modules to be loaded
   */
  private static void mergeSharedNodes(ArrayList<AOGOpTree> unionizedSubtrees) {
    ArrayList<AOGMultiOpTree.PlaceholderOp> dictOpNodes =
        new ArrayList<AOGMultiOpTree.PlaceholderOp>();
    ArrayList<AOGMultiOpTree.PlaceholderOp> regexTokNodes =
        new ArrayList<AOGMultiOpTree.PlaceholderOp>();

    // 1) Iterate through all subtrees(from all the modules to be loaded) to prepare two list of
    // PlaceholderOps node:
    // (i) whose
    // child op is a DictsOp, and (ii) whose child ops as RegexesToksOp
    for (AOGOpTree aogOpTree : unionizedSubtrees) {
      if (aogOpTree instanceof AOGMultiOpTree.PlaceholderOp
          && (aogOpTree.getNickname().startsWith(SRM_PREFIX)
              || aogOpTree.getNickname().startsWith(SDM_PREFIX))) {
        AOGMultiOpTree actualNode = ((AOGMultiOpTree.PlaceholderOp) aogOpTree).getRealNode();
        if (actualNode instanceof AOGMultiOpTree.DictsOp) {
          dictOpNodes.add(((AOGMultiOpTree.PlaceholderOp) aogOpTree));
        } else if (actualNode instanceof AOGMultiOpTree.RegexesTokOp) {
          regexTokNodes.add(((AOGMultiOpTree.PlaceholderOp) aogOpTree));
        }
      }
    }

    // =========================================================
    // Merge SDM nodes
    // =========================================================
    if (dictOpNodes.size() > 0) {
      // 2)Group all PlaceholderOps, whose child op is a DictsOp, by target column
      HashMap<String, ArrayList<AOGMultiOpTree.PlaceholderOp>> groupsToMerge =
          new HashMap<String, ArrayList<AOGMultiOpTree.PlaceholderOp>>();
      for (AOGMultiOpTree.PlaceholderOp dictPlaceholderOp : dictOpNodes) {
        String nickname = dictPlaceholderOp.getNickname();
        // Extract target column for the current dictOp placeholder node; this extracted string will
        // act as the grouping
        // key
        String targetColMatchMode =
            nickname.substring(nickname.lastIndexOf(TARGETCOL_SEPARATOR), nickname.length());

        if (true == groupsToMerge.containsKey(targetColMatchMode)) {
          groupsToMerge.get(targetColMatchMode).add(dictPlaceholderOp);
        } else {
          ArrayList<AOGMultiOpTree.PlaceholderOp> mergableDictOps =
              new ArrayList<AOGMultiOpTree.PlaceholderOp>();
          mergableDictOps.add(dictPlaceholderOp);
          groupsToMerge.put(targetColMatchMode, mergableDictOps);
        }
      }

      // 2.a) For each group of child multi ops that can be merged from step (2),3.a make a single
      // merged DictsOp
      // operator
      LinkedHashMap<String, AOGMultiOpTree.DictsOp> groupVsMergedDict =
          new LinkedHashMap<String, AOGMultiOpTree.DictsOp>();
      for (Entry<String, ArrayList<PlaceholderOp>> mergedGroup : groupsToMerge.entrySet()) {
        ArrayList<PlaceholderOp> dictOpToMerge = mergedGroup.getValue();

        if (dictOpToMerge.size() > 1) {
          // prepare merged dictops for a group
          AOGMultiOpTree.DictsOp mergedDict = null;
          for (AOGMultiOpTree.PlaceholderOp placeholderOp : dictOpToMerge) {
            AOGMultiOpTree.DictsOp actualNode =
                (AOGMultiOpTree.DictsOp) placeholderOp.getRealNode();
            if (null == mergedDict) {
              mergedDict = new AOGMultiOpTree.DictsOp(actualNode.getModuleName(), actualNode,
                  placeholderOp.getNickname());
            } else {
              mergedDict.mergeDictOp(actualNode, placeholderOp.getNickname());
            }
          }

          groupVsMergedDict.put(mergedGroup.getKey(), mergedDict);
        }
      }

      // 2.b add as many placeholderOps as the duplicate free union of the placeholderOps of the
      // original DictsOp in the
      // group
      LinkedHashMap<String, AOGOpTree> modifiedDictOpsPlaceHolder =
          new LinkedHashMap<String, AOGOpTree>();
      for (Entry<String, ArrayList<PlaceholderOp>> mergedGroup : groupsToMerge.entrySet()) {
        ArrayList<PlaceholderOp> dictOpToMerge = mergedGroup.getValue();

        if (dictOpToMerge.size() > 1) {
          for (AOGMultiOpTree.PlaceholderOp oldplaceholderOp : dictOpToMerge) {
            String nickName = oldplaceholderOp.getNickname();
            AOGMultiOpTree.DictsOp mergedDictOps = groupVsMergedDict.get(mergedGroup.getKey());
            AOGMultiOpTree.PlaceholderOp newPlaceHolder =
                new AOGMultiOpTree.PlaceholderOp(mergedDictOps.getModuleName(),
                    AOGOpTree.getConst(AOGParserConstants.DICTS_OPNAME), mergedDictOps, nickName);

            modifiedDictOpsPlaceHolder.put(nickName, newPlaceHolder);
          }
        }
      }

      // 2.c remove all the placeHolderOps of the group from the list of subtrees
      for (Entry<String, ArrayList<PlaceholderOp>> mergedGroup : groupsToMerge.entrySet()) {
        ArrayList<PlaceholderOp> dictOpToMerge = mergedGroup.getValue();
        if (dictOpToMerge.size() > 1) {
          unionizedSubtrees.removeAll(dictOpToMerge);
        }
      }

      // 2.d add the new placeholder ops created in 4.b
      unionizedSubtrees.addAll(modifiedDictOpsPlaceHolder.values());
    }

    // =========================================================
    // Merge SRM nodes
    // =========================================================
    if (regexTokNodes.size() > 0) {

      // 3)Group all PlaceholderOps, whose child op is a RegexesOp, by target column
      HashMap<String, ArrayList<AOGMultiOpTree.PlaceholderOp>> groupByTargetCol =
          new HashMap<String, ArrayList<AOGMultiOpTree.PlaceholderOp>>();
      for (AOGMultiOpTree.PlaceholderOp regexesOpsPlaceholderOp : regexTokNodes) {
        // Extract target column from Ops nickname; we will use this extracted string as the
        // grouping key
        String nickName = regexesOpsPlaceholderOp.getNickname();
        String targetCol =
            nickName.substring(nickName.lastIndexOf(TARGETCOL_SEPARATOR), nickName.length());

        if (true == groupByTargetCol.containsKey(targetCol)) {
          groupByTargetCol.get(targetCol).add(regexesOpsPlaceholderOp);
        } else {
          ArrayList<AOGMultiOpTree.PlaceholderOp> mergableRegexesOps =
              new ArrayList<AOGMultiOpTree.PlaceholderOp>();
          mergableRegexesOps.add(regexesOpsPlaceholderOp);
          groupByTargetCol.put(targetCol, mergableRegexesOps);
        }
      }

      // 3.a) For each group of child multi ops that can be merged from step (2),3.a make a single
      // merged RegexTokOp
      // operator
      LinkedHashMap<String, AOGMultiOpTree.RegexesTokOp> groupKeyVsMergedRegexes =
          new LinkedHashMap<String, AOGMultiOpTree.RegexesTokOp>();
      for (Entry<String, ArrayList<PlaceholderOp>> mergedGroup : groupByTargetCol.entrySet()) {
        ArrayList<PlaceholderOp> regexesOpToMerge = mergedGroup.getValue();

        if (regexesOpToMerge.size() > 1) {
          // prepare merged regexes Op for a group
          AOGMultiOpTree.RegexesTokOp mergedRegexesOp = null;
          for (AOGMultiOpTree.PlaceholderOp placeholderOp : regexesOpToMerge) {
            AOGMultiOpTree.RegexesTokOp actualNode =
                (AOGMultiOpTree.RegexesTokOp) placeholderOp.getRealNode();
            if (null == mergedRegexesOp) {
              mergedRegexesOp = new AOGMultiOpTree.RegexesTokOp(actualNode.getModuleName(),
                  actualNode, placeholderOp.getNickname());
            } else {
              mergedRegexesOp.mergeRegexesTokOps(actualNode, placeholderOp.getNickname());
            }
          }

          groupKeyVsMergedRegexes.put(mergedGroup.getKey(), mergedRegexesOp);
        }
      }

      // 3.b add as many placeholderOps as the duplicate free union of the placeholderOps of the
      // original RegexesOp in
      // the
      // group
      LinkedHashMap<String, AOGOpTree> modifiedRegexesOpsPlaceHolder =
          new LinkedHashMap<String, AOGOpTree>();
      for (Entry<String, ArrayList<PlaceholderOp>> mergedGroup : groupByTargetCol.entrySet()) {
        ArrayList<PlaceholderOp> regexesOpToMerge = mergedGroup.getValue();

        if (regexesOpToMerge.size() > 1) {
          for (AOGMultiOpTree.PlaceholderOp oldplaceholderOp : regexesOpToMerge) {
            String nickName = oldplaceholderOp.getNickname();
            RegexesTokOp mergedRegexOps = groupKeyVsMergedRegexes.get(mergedGroup.getKey());
            AOGMultiOpTree.PlaceholderOp newPlaceHolder = new AOGMultiOpTree.PlaceholderOp(
                mergedRegexOps.getModuleName(),
                AOGOpTree.getConst(AOGParserConstants.REGEXTOK_OPNAME), mergedRegexOps, nickName);

            modifiedRegexesOpsPlaceHolder.put(nickName, newPlaceHolder);
          }
        }
      }

      // 3.c remove all the placeHolderOps of the group from the list of subtrees
      for (Entry<String, ArrayList<PlaceholderOp>> mergedGroup : groupByTargetCol.entrySet()) {
        ArrayList<PlaceholderOp> regexesOpToMerge = mergedGroup.getValue();
        if (regexesOpToMerge.size() > 1) {
          unionizedSubtrees.removeAll(regexesOpToMerge);
        }
      }

      // 3.d add the new placeholder ops created in 4.b
      unionizedSubtrees.addAll(modifiedRegexesOpsPlaceHolder.values());
    }
  }

  private static Map<String, CompiledDictionary> getInternalCompiledDicts(TAM tam) {
    Map<String, CompiledDictionary> internalCompiledDicts =
        new LinkedHashMap<String, CompiledDictionary>();

    Map<String, CompiledDictionary> dicts = tam.getAllDicts();
    internalCompiledDicts.putAll(dicts);

    return internalCompiledDicts;
  }

  // ############################# External type population code - start
  // ############################################

  /**
   * Method to load given external dictionaries/tables; loaded dictionaries are returned in their
   * compiled form. This method will return loaded dictionaries and tables through *out* parameters.
   * 
   * @param externalTypeInfo external type info object to be loaded.
   * @param outLoadedDict out parameter to return loaded dictionaries; map of dictionary name vs
   *        compiled dictionary object.
   * @param outLoadedTable out parameter to return loaded tables; map of table name vs list of table
   *        entries.
   */
  private void loadETI(ExternalTypeInfoImpl externalTypeInfo,
      Map<String, CompiledDictionary> outLoadedDict, Map<String, DictFile> outDictFiles,
      Map<String, ArrayList<ArrayList<String>>> outLoadedTable) throws Exception {
    boolean debug = false;

    long startTime = System.currentTimeMillis();

    // load metadata for all external dictionaries/tables, from all the loaded modules
    Map<String, DictionaryMetadata> allExternalDictionaryMetadata =
        getAllExternalDictionaryMetadata();
    Map<String, TableMetadata> allExternalTableMetadata = getAllExternalTableMetadata();

    // validate external type info; don't proceed if given external artifacts does not clear
    // validation.
    validateExternalArtifacts(externalTypeInfo, allExternalDictionaryMetadata,
        allExternalTableMetadata);

    if (null == externalTypeInfo || externalTypeInfo.isEmpty()) {
      // Reaching here implies: (1)there is nothing to load (2) None of the external artifacts are
      // required
      return;
    }

    if (debug) {
      Log.debug("Start loading external artifacts(dictionaries/tables): %s",
          externalTypeInfo.toString());
    }

    // Start loading tables
    // list to maintain names of *required tables*, which are empty; this list is used for error
    // reporting
    List<String> emptyTableList = new ArrayList<String>();

    // iterate thru all the external table from the external type info, and load them into
    // outLoadedTable map
    for (String externalTableName : externalTypeInfo.getTableNames()) {
      TableMetadata tableMetadata = allExternalTableMetadata.get(externalTableName);

      ArrayList<ArrayList<String>> tableEntries = null;
      if (externalTypeInfo.isTableComingFromFile(externalTableName)) {
        // To load tables coming from a URI(file:// or hdfs://); we use the TableReader API
        String uri =
            FileOperations.resolvePath(externalTypeInfo.getTableFileURI(externalTableName));
        tableEntries = CsvFileReader.readTable(uri, tableMetadata.getTableSchema());
      } else {
        ArrayList<ArrayList<String>> givenTableEntries =
            externalTypeInfo.getTableEntries(externalTableName);

        // validate given table entries against the schema of external table
        validateTableEntries(externalTableName,
            allExternalTableMetadata.get(externalTableName).getTableSchema(), givenTableEntries);

        tableEntries = givenTableEntries;
      }

      // After loading, verify once more that external table is not empty if allow_empty is false
      if (tableMetadata.isAllowEmpty() == Boolean.FALSE
          && (null == tableEntries || (null != tableEntries && tableEntries.size() == 0))) {
        emptyTableList.add(externalTableName);
      }
      outLoadedTable.put(externalTableName, tableEntries);

      // let's not stop here; continue loading the remaining tables, to identify any other empty
      // *required table*
    }
    // tables loaded

    // Start loading dictionaries

    // list to maintain names of *required dictionaries*, which are empty; used for error reporting
    List<String> emptyDictList = new ArrayList<String>();

    DictionarySerializer dictSerializer = new TextSerializer();

    // iterate thru all the external dictionary from external type info, and load them into
    // outLoadDict map
    for (String externalDictName : externalTypeInfo.getDictionaryNames()) {
      DictionaryMetadata dictionaryMetadata = allExternalDictionaryMetadata.get(externalDictName);

      // prepare dictionary matching parameter from dictionary metadata
      DictParams dictParam = getDictParam(dictionaryMetadata, externalDictName);

      DictFile dictFile = null;
      InputStream dictStream = null;
      CompiledDictionary compiledDict = null;
      if (externalTypeInfo.isDictComingFromFile(externalDictName)) {
        try {
          // To load dictionaries coming from a URI(file:// or hdfs:// or gpfs://);
          // we open a stream to the URI, then use the DictFile API to load entries from this stream
          String uri =
              FileOperations.resolvePath(externalTypeInfo.getDictionaryFileURI(externalDictName));
          dictStream = FileOperations.getStream(uri);

          // First check, that the external dictionary coming from file URI is already in compiled
          // form
          try {
            compiledDict = dictSerializer.deSerialize(dictStream);
            if (null != compiledDict)
              outLoadedDict.put(externalDictName, compiledDict);
          } catch (IncompatibleCompiledDictException | InvalidDictionaryFileFormatException e) {
            // If here, we dealing with dictionary not in compiled form
            if (debug) {
              Log.debug("External dictionary '%s' from file '%s' is not in compiled form.",
                  externalDictName, uri);
            }
            // release the handle to the file
            if (null != dictStream) {
              dictStream.close();
            }
          }

          // If not in compiled form, load the dictionary
          if (null == compiledDict) {
            // Open a reader stream again
            dictStream = FileOperations.getStream(uri);
            try {
              // Loading the dictionary file
              dictFile = new DictFile(dictStream, dictParam);
            } catch (IllegalArgumentException iae) {
              throw new InvalidDictionaryFileFormatException(iae,
                  "An error occurred while parsing entries for external dictionary '%s', from the file '%s'. Specify the entries for external dictionary in the file format as described in the Information Center.",
                  externalDictName, uri);
            } catch (Exception e) {
              throw new TextAnalyticsException(
                  "An error occurred while loading external dictionary '%s' from the file '%s': \n%s",
                  externalDictName, uri, e.getMessage());
            }
          }
        } finally {
          // release the handle to the file
          if (null != dictStream) {
            dictStream.close();
          }
        }
      } else {
        List<String> dictionaryEntries = externalTypeInfo.getDictionaryEntries(externalDictName);
        dictFile = new DictFile(dictionaryEntries, dictParam, null);
      }

      // Add to the list of loaded dictionaries
      if (null != dictFile)
        outDictFiles.put(externalDictName, dictFile);

      // After loading, verify that external dict is not empty if allow_empty is false
      if (dictionaryMetadata.isAllowEmpty() == Boolean.FALSE
          && ((dictFile != null && dictFile.getEntries().size() == 0)
              || (compiledDict != null && compiledDict.getNumberOfEntries() == 0))) {
        emptyDictList.add(externalDictName);
      }
      // Lets not stop here; continue loading the remaining dictionaries, to identify any
      // other empty *required dictionary*
    }
    // dictionaries loaded

    // Builder to prepare an error report
    StringBuilder errorReport = new StringBuilder();

    if (emptyDictList.size() > 0) {
      // purge incompletely loaded tables
      outLoadedTable.clear();
      errorReport.append(String.format(
          "The following required non-empty external dictionaries are empty: %s.", emptyDictList));
    }

    if (emptyTableList.size() > 0) {
      errorReport.append(String.format(
          "The following external tables, which are mandatory, are empty: %s.", emptyTableList));
    }

    if (errorReport.length() > 0) {
      throw new TextAnalyticsException(errorReport.toString());
    }

    // Reaching here implies, that all the dictionaries/table loaded without any error. It is safe
    // to start dictionary
    // compilation now.
    long dictCompilationStartTime = System.currentTimeMillis();

    // Memoization data structure to be used while compiling external dictionaries
    DictMemoization dm = new DictMemoization();
    // create tokenizer, using the tokenizer configuration provided while invoking createOG method
    Tokenizer tokenizer = getTokenizer();

    for (String dictName : outDictFiles.keySet()) {
      DictFile loadedDict = outDictFiles.get(dictName);
      try {
        outLoadedDict.put(dictName, loadedDict.compile(tokenizer, dm));
      } catch (Exception e) {
        throw new TextAnalyticsException(
            "Encountered an error while compiling external dictionary '%s' from the file '%s': \n%s.",
            loadedDict.getName(), externalTypeInfo.getDictionaryFileURI(loadedDict.getName()),
            e.getMessage());
      }

    }
    // dictionaries compiled

    if (debug) {
      Log.debug("Time taken to compile %s dictionaries in %d millisec.", outDictFiles.keySet(),
          System.currentTimeMillis() - dictCompilationStartTime);
    }

    if (debug) {
      Log.debug("External artifacts(dictionaries/tables) loaded in %d millisec",
          System.currentTimeMillis() - startTime);
    }
  }

  /**
   * Method to validate the {@link ExternalTypeInfo} object passed during operator graph creation.
   * This method performs the following validation: <br>
   * 1) Validates if the given external dictionaries/tables are declared in the loaded modules. <br>
   * 2) Validates if all the required external dictionaries/tables are provided in passed external
   * type info instance.
   * 
   * @param externalTypeInfo external type info instance passed to the loader.
   * @param allExternalDictionaryMetadata map of all the external dictionary name vs their meta-data
   *        from the loaded modules.
   * @param allExternalTableMetadata map of all the external table name vs their meta-data from the
   *        loaded modules.
   */
  private void validateExternalArtifacts(ExternalTypeInfoImpl externalTypeInfo,
      Map<String, DictionaryMetadata> allExternalDictionaryMetadata,
      Map<String, TableMetadata> allExternalTableMetadata) throws Exception {
    // prepare list of required external dictionaries
    List<String> requiredExternalDicts = new ArrayList<String>();
    for (Entry<String, DictionaryMetadata> externalDict : allExternalDictionaryMetadata
        .entrySet()) {
      String externalDictName = externalDict.getKey();
      DictionaryMetadata dictionaryMetadata = externalDict.getValue();

      if ((dictionaryMetadata.isRequired() == Boolean.TRUE)
          || (dictionaryMetadata.isAllowEmpty() == Boolean.FALSE)) {
        requiredExternalDicts.add(externalDictName);
      }
    }

    // prepare list of required external tables
    List<String> requiredExternalTables = new ArrayList<String>();
    for (Entry<String, TableMetadata> externalTable : allExternalTableMetadata.entrySet()) {
      String externalTableName = externalTable.getKey();
      TableMetadata tableMetadata = externalTable.getValue();

      if ((tableMetadata.isRequired() == Boolean.TRUE)
          || (tableMetadata.isAllowEmpty() == Boolean.FALSE)) {
        requiredExternalTables.add(externalTableName);
      }
    }

    // Validate, that ETI object contains all the required external dictionaries and tables
    /* 1) throw error if ETI is null or empty, and there are required dictionaries/tables */
    if (null == externalTypeInfo || externalTypeInfo.isEmpty()) {
      if (requiredExternalDicts.size() > 0 || requiredExternalTables.size() > 0) {
        // No need to proceed further
        throw new TextAnalyticsException(
            "External artifacts passed to the extractor are empty. Provide entries for all required external dictionaries and tables. Required external dictionaries are: %s. Required external tables are: %s.",
            requiredExternalDicts, requiredExternalTables);
      }

      // Reaching here implies: there is nothing to validate
      return;
    }

    // Validation error reporter
    List<String> errors = new ArrayList<String>();

    // list of external dictionary name given by user
    List<String> givenExternalDictName = externalTypeInfo.getDictionaryNames();
    // list of all the external dictionary name from the loaded modules.
    Set<String> allExternalDictFromLoadedModule = allExternalDictionaryMetadata.keySet();

    // list of external table name given by user
    List<String> givenExternalTableName = externalTypeInfo.getTableNames();
    // list of all the external table name from the loaded modules.
    Set<String> allExternalTableFromLoadedModule = allExternalTableMetadata.keySet();

    /*
     * 2) validate if all the required external dictionaries are provided by user. Note: external
     * dictionaries declared with required flag as 'true' or allow_empty flag as 'false' are
     * required.
     */
    if (!givenExternalDictName.containsAll(requiredExternalDicts)) {
      List<String> temp = new ArrayList<String>(requiredExternalDicts);
      temp.removeAll(givenExternalDictName);
      if (temp.size() > 0) {
        errors.add(String.format(
            "Required external dictionaries %s are missing in external type info object", temp));
      }
    }

    /*
     * 3) validate if all the required external tables are provided by user. Note: external tables
     * declared with required flag as 'true' or allow_empty flag as 'false' are required.
     */
    if (!givenExternalTableName.containsAll(requiredExternalTables)) {
      List<String> temp = new ArrayList<String>(requiredExternalTables);
      temp.removeAll(givenExternalTableName);
      if (temp.size() > 0) {
        errors.add(String
            .format("Required external tables %s are missing in external type info object", temp));
      }
    }

    // 4) validate if all the given external dictionary names, belongs to the loaded modules.
    if (!allExternalDictFromLoadedModule.containsAll(givenExternalDictName)) {
      List<String> temp = new ArrayList<String>(givenExternalDictName);
      temp.removeAll(allExternalDictFromLoadedModule);

      if (temp.size() > 0) {
        errors.add(String.format(
            "External dictionaries %s specified in external type info object are not part of any of the loaded modules.",
            temp));
      }
    }

    // 5) validate if all the given external table names, belongs to the loaded modules.
    if (!allExternalTableFromLoadedModule.containsAll(givenExternalTableName)) {
      List<String> temp = new ArrayList<String>(givenExternalTableName);
      temp.removeAll(allExternalTableFromLoadedModule);

      if (temp.size() > 0) {
        errors.add(String.format(
            "External tables %s specified in external type info object are not part of any of the loaded modules.",
            temp));
      }
    }

    // Throw exception, if there are validation issues.
    if (errors.size() > 0) {
      throw new TextAnalyticsException(errors.toString());
    }

  }

  /**
   * Method to validate the table entries passed to the loader through the external type info
   * object; the entries are validated against the schema declared in 'create external table ...'
   * statement. For any occurrence of invalid entry this method will throw an
   * InvalidTableEntryException.
   * 
   * @param tableName name of the external table, whose entries are validated; this parameter is
   *        only used for error reporting.
   * @param tableSchema schema of the external table against which validation will be performed.
   * @param tableEntries table entries to be validated;
   */
  private void validateTableEntries(String tableName, TupleSchema tableSchema,
      ArrayList<ArrayList<String>> tableEntries) throws InvalidTableEntryException {
    int tableSize = tableEntries.size();

    for (int entryCounter = 0; entryCounter < tableSize; entryCounter++) {
      try {
        CsvFileReader.validateTableEntry(tableEntries.get(entryCounter).toArray(new String[0]),
            tableSchema);
      } catch (Exception ve) {
        throw new InvalidTableEntryException("For table '%s', entry number %d, %s.", tableName,
            entryCounter + 1, ve.getMessage());
      }
    }
  }

  /**
   * This method compiles the internal dictionaries coming from loaded external table.
   * 
   * @param loadedTable map of loaded external table and their loaded tuples
   * @return map of compiled dictionary object; empty map,if none of the internal dictionaries are
   *         coming from external table
   */
  private Map<String, CompiledDictionary> compileDictFromExtTable(
      Map<String, ArrayList<ArrayList<String>>> loadedTable) throws Exception {
    Map<String, CompiledDictionary> internalCompiledDict =
        new HashMap<String, CompiledDictionary>();

    // Get list of internal dictionaries from the loaded modules, whose entries comes from external
    // table
    Map<String, DictionaryMetadata> allDictComingFromExtTabMetadata =
        getAllDictionariesComingFromExtTable();

    // Compile now
    if (false == allDictComingFromExtTabMetadata.isEmpty()) {
      Map<String, TableMetadata> allExternalTableMetadata = getAllExternalTableMetadata();

      // Memoization data structure to be used while compiling external dictionaries
      DictMemoization dm = new DictMemoization();

      // create tokenizer, using the configuration provided while invoking OperatorGrpah creation
      Tokenizer tokenizer = getTokenizer();

      for (String dictName : allDictComingFromExtTabMetadata.keySet()) {
        DictionaryMetadata dictionaryMetadata = allDictComingFromExtTabMetadata.get(dictName);
        String extTableName = ((DictionaryMetadataImpl) dictionaryMetadata).getExtTableName();
        String extTableColName = ((DictionaryMetadataImpl) dictionaryMetadata).getExtTableColName();
        TableMetadata tabMetaData = allExternalTableMetadata.get(extTableName);
        List<String> dictionaryEntries = loadDictEntriesFromExtTable(tabMetaData.getTableSchema(),
            loadedTable.get(extTableName), extTableColName);
        DictParams dictParam = getDictParam(dictionaryMetadata, dictName);

        DictFile dictFile = new DictFile(dictionaryEntries, dictParam, null);
        internalCompiledDict.put(dictName, dictFile.compile(tokenizer, dm));
      }
    }
    return internalCompiledDict;
  }

  /**
   * Method to fetch dictionary entries from the loaded external table, using the given source
   * column.
   * 
   * @param tableSchema schema of the external table
   * @param tableEntries external table entries
   * @param sourceColumn external table column from which dictionary entries should come
   * @return list of string containing entries from the source column
   */
  private List<String> loadDictEntriesFromExtTable(TupleSchema tableSchema,
      ArrayList<ArrayList<String>> tableEntries, String sourceColumn) {
    List<String> dictEntries = new ArrayList<String>();

    // probe for index of the source column in the tuple
    String[] fieldNames = tableSchema.getFieldNames();
    int sourceColumnIndex = 0;
    while (sourceColumnIndex < fieldNames.length) {
      if (sourceColumn.equals(fieldNames[sourceColumnIndex])) {
        // got the index - no need to iterate anymore
        break;
      }
      sourceColumnIndex++;
    }
    // Now use the index probed above, to fetch dictionary entries from table entries ArrayList
    // Optional external tables may be empty. So, check for null.
    if (tableEntries != null) {
      for (ArrayList<String> tableEntry : tableEntries) {
        dictEntries.add(tableEntry.get(sourceColumnIndex));
      }
    }

    return dictEntries;
  }

  /**
   * Method to fetch meta-data for all the dictionaries, whose entries comes from external table.
   * 
   * @return map of qualified dictionary name to meta-data
   */
  private Map<String, DictionaryMetadata> getAllDictionariesComingFromExtTable() {
    Map<String, DictionaryMetadata> dictionaryMetaData = new HashMap<String, DictionaryMetadata>();

    String[] dictsComingFromExtTables =
        ((MultiModuleMetadataImpl) moduleSetMetadata).getDictComingFromExtTable();
    for (String dictionaryName : dictsComingFromExtTables) {
      dictionaryMetaData.put(dictionaryName,
          moduleSetMetadata.getDictionaryMetadata(dictionaryName));
    }

    return dictionaryMetaData;
  }

  /**
   * Utility method to prepare dictionary matching parameter from dictionary metadata.
   * 
   * @param dictMetadata metadata of the dictionary for which dictionary matching parameter is
   *        requested.
   * @param fullyQualifiedDictName fully qualified dictionary name; meta-data does not contains
   *        qualified name.
   * @return instance of dictionary matching parameter
   */
  private static DictParams getDictParam(DictionaryMetadata dictMetadata,
      String fullyQualifiedDictName) {
    DictParams dictParam = new DictParams();
    dictParam.setDictName(fullyQualifiedDictName); // meta-data contains unqualified name
    dictParam.setLangStr(dictMetadata.getLanguages());
    dictParam.setCase(dictMetadata.getCaseType());
    dictParam.setSupportLemmaMatch(dictMetadata.isLemmaMatch());

    return dictParam;
  }

  /**
   * @param loadedDicts
   * @param moduleName
   * @return
   */
  private static Map<String, CompiledDictionary> getExtDictForModule(
      Map<String, CompiledDictionary> loadedDicts, String moduleName) {
    Map<String, CompiledDictionary> moduleDicts = new HashMap<String, CompiledDictionary>();
    for (String dictName : loadedDicts.keySet()) {
      if (moduleName.equals(ModuleUtils.getModuleName(dictName))) {
        moduleDicts.put(dictName, loadedDicts.get(dictName));
      }
    }
    return moduleDicts;
  }

  /**
   * @param loadedTables
   * @param moduleName
   * @return
   */
  private static Map<String, ArrayList<ArrayList<String>>> getExtTabForModule(
      Map<String, ArrayList<ArrayList<String>>> loadedTables, String moduleName) {
    Map<String, ArrayList<ArrayList<String>>> moduleTables =
        new HashMap<String, ArrayList<ArrayList<String>>>();

    for (String tableName : loadedTables.keySet()) {
      if (moduleName.equals(ModuleUtils.getModuleName(tableName))) {
        moduleTables.put(tableName, loadedTables.get(tableName));
      }
    }
    return moduleTables;
  }

  // Used by test cases to compare stitched modules
  public void dump(PrintWriter stream, int indent) throws ParseException {
    plan.dump(stream, indent);
  }

  /**
   * @return the tokenizer instance created using the specified {@link #tokenizerCfg}. This
   *         tokenizer will be used to compile the specified external dictionaries.
   * @throws TextAnalyticsException
   */
  private Tokenizer getTokenizer() throws TextAnalyticsException {
    if (null == tokenizer)
      tokenizer = this.tokenizerCfg.makeTokenizer();

    return tokenizer;
  }
}
