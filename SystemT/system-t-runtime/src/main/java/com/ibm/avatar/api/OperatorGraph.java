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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.util.dict.CompiledDictionary;
import com.ibm.avatar.algebra.util.dict.DictFile;
import com.ibm.avatar.algebra.util.file.FileOperations;
import com.ibm.avatar.algebra.util.tokenize.TokenizerConfig;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException.ExceptionType;
import com.ibm.avatar.api.exceptions.VerboseNullPointerException;
import com.ibm.avatar.api.tam.DictionaryMetadata;
import com.ibm.avatar.api.tam.ModuleMetadata;
import com.ibm.avatar.api.tam.ModuleMetadataFactory;
import com.ibm.avatar.api.tam.TableMetadata;
import com.ibm.avatar.api.tam.ViewMetadata;
import com.ibm.avatar.aql.tam.TAM;

/**
 * This class provides APIs to load an operator graph from one or more compiled AQL modules; it also
 * provides an API for extracting information using the loaded operator graph.
 * <p>
 * This class can load compiled AQL modules stored on both local and Hadoop distributed filesystem.
 * It also provides an API to validate the compatibility of the compiled AQL modules to be loaded.
 * It is recommended to validate the compatibility of the modules to be loaded before attempting to
 * load them.
 * <p>
 * There are additional APIs to query the loaded operator graph about: the schema of expected
 * document, the schema of the output views, and the schema of the external views.
 * 
 * @see ModuleMetadata
 * @see ExternalTypeInfo
 */
public abstract class OperatorGraph {
  /**
   * Creates an operator graph containing all modules specified in <code>moduleNames</code>
   * parameter. Each module is loaded by a class loader as follows: first, load using the current
   * Thread's context class loader; if the module is not found, load using the current class loader.
   * Throw {@link com.ibm.avatar.api.exceptions.ModuleNotFoundException} if the module is not found.
   * The method loads the first module found with the given name, and does not throw an exception if
   * multiple modules with the same name are found in the current thread context class loader or
   * current class loader. If a required module, that an input module directly or indirectly depends
   * on, is not explicitly included in <code>moduleNames</code>, the method will search for the
   * module and load it using the current thread context loader, and then the current class loader
   * as explained above. <br/>
   * 
   * @param moduleNames Array of names of module(s) to load
   * @param info Information about external artifacts such as external dictionaries and external
   *        tables
   * @param tokenizerCfg Tokenizer configuration that the operator graph should use to tokenize the
   *        input document. The loader validates that this tokenizer matches with the one used to
   *        compile module(s).
   * @return an OperatorGraph instance.
   * @throws TextAnalyticsException
   *         <ul>
   *         A TextAnalyticsException is thrown under the following circumstances, when the input
   *         modules and the ones they depend on directly or indirectly cannot be combined together
   *         into a single valid operator graph:
   *         <li>Incompatible Document schema</li>
   *         <li>Mismatch between the Tokenizer Configuration used to compile the modules</li>
   *         <li>Incomplete set of modules, because one or more of them could not be loaded</li>
   *         </ul>
   *         It is recommended to invoke {@link #validateOG(String[], TokenizerConfig)} before
   *         loading the operator graph.
   */
  public static OperatorGraph createOG(String[] moduleNames, ExternalTypeInfo info,
      TokenizerConfig tokenizerCfg) throws TextAnalyticsException {
    // Pass null value to modulePath to force TAMSerializer to load from system classpath
    OperatorGraph instance = OperatorGraphImpl.createOGImpl(moduleNames, null, info, tokenizerCfg);

    return instance;
  }

  /**
   * Creates an operator graph containing all modules specified in <code>moduleNames</code>
   * parameter. Each module is loaded by a class loader as follows: first, load using the current
   * Thread's context class loader; if the module is not found, load using the current class loader.
   * Throw {@link com.ibm.avatar.api.exceptions.ModuleNotFoundException} if the module is not found.
   * The method loads the first module found with the given name, and does not throw an exception if
   * multiple modules with the same name are found in the current thread context class loader or
   * current class loader. If a required module, that an input module directly or indirectly depends
   * on, is not explicitly included in <code>moduleNames</code>, the method will search for the
   * module and load it using the current thread context loader, and then the current class loader
   * as explained above. <br/>
   * <p>
   * The parameters <code>dictionaries</code> and <code>tables</code> are maps, with the key being
   * fully qualified name of the external artifact and the value being URI of the file containing
   * data for the external artifact. The data file can be either on the local filesystem or on
   * HDFS/GPFS. See <a href="../../../../overview-summary.html#uri">Supported URI Formats</a> for
   * details. If the URI information is missing (or) invalid for a <i>required</i> external artifact
   * (i.e for an external dictionary or external table with <code>allow_empty</code> equal to
   * <code>false</code>), then the loader throws an Exception.
   * </p>
   * 
   * @param moduleNames Array of names of the module(s) to load
   * @param dictionaries a mapping of fully qualified external dictionary names to
   *        <code>file://</code> or <code>hdfs://</code> or <code>gpfs://</code> URI of external
   *        dictionary files
   * @param tables a mapping of fully qualified external table names to <code>file://</code> or
   *        <code>hdfs://</code> or <code>gpfs://</code> URI of external table files.
   * @param tokenizerCfg Tokenizer configuration that the operator graph should use to tokenize the
   *        input document. The loader validates that this tokenizer matches with the one used to
   *        compile module(s).
   * @return A unionized OperatorGraph instance, if all modules are successfully loaded, validated
   *         and their operator graphs are successfully stitched together.
   * @throws TextAnalyticsException
   *         <ul>
   *         A TextAnalyticsException is thrown under the following circumstances, when the input
   *         modules and the ones they depend on directly or indirectly cannot be combined together
   *         into a single valid operator graph:
   *         <li>Incompatible Document schema</li>
   *         <li>Mismatch between the Tokenizer Configuration used to compile the modules</li>
   *         <li>Incomplete set of modules, because one or more of them could not be loaded</li>
   *         </ul>
   *         It is recommended to invoke {@link #validateOG(String[], TokenizerConfig)} before
   *         loading the operator graph.
   */
  public static OperatorGraph createOG(String[] moduleNames, Map<String, String> dictionaries,
      Map<String, String> tables, TokenizerConfig tokenizerCfg) throws TextAnalyticsException {
    ExternalTypeInfoImpl externalTypeInfo = new ExternalTypeInfoImpl(dictionaries, tables);
    // Pass null value to modulePath to force TAMSerializer to load from system classpath
    OperatorGraph instance =
        OperatorGraphImpl.createOGImpl(moduleNames, null, externalTypeInfo, tokenizerCfg);

    return instance;
  }

  /**
   * Creates an operator graph containing all of the named modules by loading them from the
   * specified <code>modulePath</code> parameter. The loader will attempt to locate modules under
   * directories and/or .jar/.zip files specified in the semicolon-separated <code>modulePath</code>
   * parameter. If a required module is not explicitly included in <code>moduleNames</code>, the
   * loader will attempt to resolve any dependency by searching for the dependent module(s) in
   * <code>modulePath</code>, throwing an error if the module(s) are not found on the path. This API
   * does <b>not</b> search the classpath. If the loader finds more than one compiled module in the
   * given <code>modulePath</code>, then this API throws an
   * {@link com.ibm.avatar.api.exceptions.AmbiguousModuleMatchException}.
   * 
   * @param moduleNames An array of names of module(s) to load.
   * @param modulePath a semicolon-separated list of URIs on local or distributed filesystem,
   *        pointing to the directories and/or .jar/.zip archives containing all the module(s) to be
   *        loaded. See <a href="../../../../overview-summary.html#uri">Supported URI Formats</a>
   *        for details.
   * @param info Information about external artifacts such as external dictionaries and external
   *        tables
   * @param tokenizerCfg Tokenizer configuration that the operator graph should use to tokenize the
   *        input document. The loader validates that this tokenizer matches with the one used to
   *        compile module(s).
   * @return A unionized OperatorGraph instance, if all modules are successfully loaded, validated
   *         and their operator graphs are successfully stitched together.
   * @throws TextAnalyticsException
   *         <ul>
   *         A TextAnalyticsException is thrown under the following circumstances, when the input
   *         modules and the ones they depend on directly or indirectly cannot be combined together
   *         into a single valid operator graph:
   *         <li>Incompatible Document schema</li>
   *         <li>Mismatch between the Tokenizer Configuration used to compile the modules</li>
   *         <li>Incomplete set of modules, because one or more of them could not be loaded</li>
   *         </ul>
   *         It is recommended to invoke {@link #validateOG(String[], String, TokenizerConfig)}
   *         before loading the operator graph.
   */
  public static OperatorGraph createOG(String[] moduleNames, String modulePath,
      ExternalTypeInfo info, TokenizerConfig tokenizerCfg) throws TextAnalyticsException {
    // Check for null inputs
    if (null == moduleNames) {
      throw new VerboseNullPointerException(OperatorGraph.class, "createOG",
          "list of module names");
    }
    for (int i = 0; i < moduleNames.length; i++) {
      if (null == moduleNames[i]) {
        throw new VerboseNullPointerException(OperatorGraph.class, "createOG",
            String.format("element %d in list of module names", i));
      }
    }
    if (null == modulePath) {
      throw new VerboseNullPointerException(OperatorGraph.class, "createOG", "module path");
    }

    OperatorGraph instance =
        OperatorGraphImpl.createOGImpl(moduleNames, modulePath, info, tokenizerCfg);

    return instance;
  }

  /**
   * Verifies that the specified modules can be successfully loaded from the classpath and the
   * unionized operator graph is in a valid state. Detects incompatibilities in
   * TokenizerConfiguration and/or Document schema between the specified modules.Additionally, this
   * method also verifies the conflicting output names across the specified modules.
   * 
   * @param moduleNames an array of module names to load from classpath
   * @param tokenizerCfg Load time tokenizer configuration. This information will be used to
   *        validate if compile time and load time tokenizer types match with each other. This
   *        method does not perform detailed comparison of the configuration entries.
   * @return <code>true</code> , if the specified modules can be successfully loaded and the
   *         unionized operator graph is in a valid state. Returns <code>false </code> , otherwise.
   * @throws TextAnalyticsException if there are any problems in loading the specified modules.
   */
  public static boolean validateOG(String[] moduleNames, TokenizerConfig tokenizerCfg)
      throws TextAnalyticsException {
    // Pass null for modulePath parameter to instruct the loader to load modules from system
    // classpath
    return validateOG(moduleNames, null, tokenizerCfg);
  }

  /**
   * Verifies that the specified modules can be successfully loaded from the <code>modulePath</code>
   * and the unionized operator graph is in a valid state. Detects incompatibilities in
   * TokenizerConfiguration and/or Document schema between the specified modules.Additionally, this
   * method also verifies the conflicting output names across the specified modules.
   * 
   * @param moduleNames an array of module names to load from the specified modulePath
   * @param modulePath a semicolon-separated list of URIs on local or distributed filesystem,
   *        pointing to the directories and/or .jar/.zip archives containing all the module(s) to be
   *        loaded. See <a href="../../../../overview-summary.html#uri">Supported URI Formats</a>
   *        for details.
   * @param tokenizerCfg Load time tokenizer configuration. This information will be used to
   *        validate if compile time and load time tokenizer types match with each other. This
   *        method does not perform detailed comparison of the configuration entries.
   * @return <code>true</code> , if the specified modules can be successfully loaded and the
   *         unionized operator graph is in a valid state. Returns <code>false </code> , otherwise.
   * @throws TextAnalyticsException if there are any problems in loading the specified modules.
   */
  public static boolean validateOG(String[] moduleNames, String modulePath,
      TokenizerConfig tokenizerCfg) throws TextAnalyticsException {
    try {

      // This call validates the modules for compatibility in doc schema, tokenizer, and output
      // views
      ModuleMetadataFactory.readAllMetaData(moduleNames, modulePath);

      // This validates that a basic stitching (without artifacts) will not throw an exception
      HashMap<String, TAM> nameToTAM = new HashMap<String, TAM>();
      modulePath = FileOperations.resolveModulePath(modulePath);
      OperatorGraphImpl.loadModules(moduleNames, modulePath, nameToTAM);
      OperatorGraphImpl.stitchModules(null, null, nameToTAM, null, tokenizerCfg);
      return true;
    } catch (Throwable t) {
      throw TextAnalyticsException.convertToTextAnalyticsException(t, ExceptionType.RUNTIME_ERROR);
    }
  }

  /**
   * Annotates the document specified as input.
   * 
   * @param input Tuple that represents the document to be annotated
   * @param outputTypes list of output views for which the extractor should generate outputs; Pass
   *        null to return all types
   * @param extViewTups Content of the external views, given as a map with the key being the
   *        external view name qualified by the module name and the value being a list of tuples
   *        that the external view must be populated with. Can be <code>null</code> if no external
   *        views are defined.
   * @return a map from output type name to a list of output tuples for the document
   * @throws TextAnalyticsException if there are any problems that occur while annotating the
   *         document
   */
  public abstract Map<String, TupleList> execute(Tuple input, String[] outputTypes,
      Map<String, TupleList> extViewTups) throws TextAnalyticsException;

  /**
   * Retrieves the metadata of the specified compiled module.
   * 
   * @param moduleName name of the module whose metadata is requested for.
   * @return ModuleMetadata metadata of the given module
   * @throws TextAnalyticsException If the module is not found in the loaded Operator Graph
   */
  public abstract ModuleMetadata getModuleMetadata(String moduleName) throws TextAnalyticsException;

  /**
   * Returns the list of all modules contained within this operator graph.
   * 
   * @return an array of module names loaded into the operator graph
   */
  public abstract String[] getModuleNames();

  /**
   * Informs the operator graph about the particular approach to divide documents into smaller
   * "chunks" for processing. The document text is expected to be in column 'text' of type Text. If
   * the column 'text' does not exist or is not of type Text, an exception will be thrown.
   * 
   * @param chunker object that knows how to divide up documents in an extractor-appropriate way
   */
  public abstract void setChunker(Chunker chunker) throws TextAnalyticsException;

  /**
   * Returns the input tuple schema of an external view.
   * 
   * @param viewName fully qualified name of the external view as defined in the AQL, or unqualified
   *        name for non-modular AQLs.
   * @return schema of the given external view
   * @throws TextAnalyticsException if the external view cannot be found
   */
  public abstract TupleSchema getExternalViewSchema(String viewName) throws TextAnalyticsException;

  /**
   * Returns the fully qualified names of all external views found in the loaded operator graph, or
   * unqualified names for non-modular AQLs.
   * 
   * @return the fully qualified AQL names of all external views
   * @throws TextAnalyticsException if the external views were never initialized
   */
  public abstract String[] getExternalViewNames() throws TextAnalyticsException;

  /**
   * Returns the external name of an external view.
   * 
   * @param viewName the fully qualified AQL (internal) name of the external view or unqualfiied
   *        name for non-modular AQLs
   * @return the external name of the given external view.
   * @throws TextAnalyticsException if the external view cannot be found
   */
  public abstract String getExternalViewExternalName(String viewName) throws TextAnalyticsException;

  /**
   * Returns a list of output type names from the loaded operator graph.
   * 
   * @return a list of output type names
   * @throws TextAnalyticsException if the output type names were never initialized
   */
  public abstract ArrayList<String> getOutputTypeNames() throws TextAnalyticsException;

  /**
   * Fetches the schema of the given output type name from the loaded operator graph.
   * 
   * @param outputTypeName name of the output type whose schema you want to fetch
   * @return the schema of the indicated output of the extractor
   */
  public abstract TupleSchema getSchema(String outputTypeName) throws TextAnalyticsException;

  /**
   * Return a map containing all output type names and their schemas.
   * 
   * @return a map where the key is the output type name, and the value is its schema
   * @throws TextAnalyticsException if the output type names were never initialized
   */
  public abstract Map<String, TupleSchema> getOutputTypeNamesAndSchema()
      throws TextAnalyticsException;

  /**
   * Return an array of String containing all output view names.
   *
   * @return an array of String where the content is output view name.
   */
  public abstract String[] getOutputViews();

  /**
   * Return ViewMetadata for the specified output view name.
   *
   * @param outputViewName name of the output view whose metadata you want to fetch
   * @return view metadata of the specified view name
   */
  public abstract ViewMetadata getViewMetadata(final String outputViewName);

  /**
   * Return a map containing original external dictionary names and their compiled-form
   * dictionaries.
   *
   * @return a map where the key is the external dictionary name, and the value is compiled
   *         dictionary itself.
   */
  public abstract Map<String, CompiledDictionary> getOrigLoadedDicts();

  /**
   * Return a map containing original external dictionary names and their original-form DictFiles.
   *
   * @return a map where the key is the external dictionary name, and the value is original-form
   *         DictFiles itself.
   */
  public abstract Map<String, DictFile> getOrigDictFiles();

  /**
   * Return a map containing original external table names and their contents.
   *
   * @return a map where the key is the external table name, and the value is its content.
   */
  public abstract Map<String, ArrayList<ArrayList<String>>> getOrigLoadedTables();

  /**
   * Return a map containing module names and their TAM instances.
   *
   * @return a map where the key is the module name, and the value is its TAM instance.
   */
  public abstract Map<String, TAM> getModuleNameToTam();

  /**
   * Returns the schema of the document expected by the loaded operator graph.
   * 
   * @return the document schema (all required columns and the order in which they must appear) for
   *         the extractor
   */
  public abstract TupleSchema getDocumentSchema() throws TextAnalyticsException;

  /**
   * Returns the tokenizer configuration used to create the operator graph.
   * 
   * @return
   */
  public abstract TokenizerConfig getTokenizerConfig();

  /**
   * Method to fetch meta-data for all external dictionaries from the loaded modules.
   *
   * @return map of qualified dictionary name to meta-data.
   */
  public abstract Map<String, DictionaryMetadata> getAllExternalDictionaryMetadata();

  /**
   * Method to fetch meta-data for all external tables from the loaded modules.
   *
   * @return map of qualified table name to meta-data.
   */
  public abstract Map<String, TableMetadata> getAllExternalTableMetadata();
}
