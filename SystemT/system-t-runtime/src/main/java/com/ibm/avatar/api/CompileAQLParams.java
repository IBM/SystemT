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

import java.io.File;
import java.util.Arrays;

import com.ibm.avatar.algebra.util.file.FileOperations;
import com.ibm.avatar.algebra.util.tokenize.Tokenizer;
import com.ibm.avatar.algebra.util.tokenize.TokenizerConfig;
import com.ibm.avatar.api.exceptions.InvalidCompileParamException;
import com.ibm.avatar.api.exceptions.URIToFileException;

/**
 * This class encapsulates all parameters needed for compiling modular and non-modular AQL.
 * Construct an instance of this class and pass it as parameter to
 * {@link com.ibm.avatar.api.CompileAQL#compile(CompileAQLParams)}.<br>
 * This class provides a constructor for the common scenario when the input non-modular AQL is in a
 * file and the output compiled module should be written to a location specified by the output URI.
 */
public class CompileAQLParams implements Cloneable {

  /**
   * <code>true</code> to enable the Shared Regex Matching optimization. <code>true</code> by
   * default.
   */
  private boolean performSRM = true;

  /**
   * <code>true</code> to enable the Shared Dictionary Matching optimization. <code>true</code> by
   * default.
   */
  private boolean performSDM = true;

  /**
   * <code>true</code> to enable Regex Strength Reduction. <code>true</code> by default.
   */
  private boolean performRSR = true;

  /** Top-level AQL file of an extractor. Used when compiling from a file. */
  private File inputFile;

  /**
   * Java string containing the top-level AQL file of an extractor. Used when compiling from a
   * string.
   */
  private String inputStr;

  /** Default encoding is UTF-8 */
  private final String DEFAULT_ENCODING = "UTF-8";

  /**
   * Encoding of the input AQL file (if compiling from a file), and any auxiliary AQL or dictionary
   * files to be loaded from disk. Default value is UTF-8.
   */
  private String inEncoding = DEFAULT_ENCODING;

  /**
   * URIs of directories containing source files for modules to be compiled
   */
  private String[] inputModules;

  /**
   * Location where compiled TAMs are to be written out to.
   */
  private String outputURI;

  /**
   * A semicolon-separated list of URIs that refer to absolute paths of locations where dependent
   * modules can be located.
   */
  // do not default it to any value. Also, do not perform any validation on this parameter because
  // this parameter is
  // optional. If compiler can resolve all dependents from the list of input module source passed to
  // it, then there is
  // no need of modulePath at all.
  private String modulePath;

  /**
   * Path for finding auxiliary dictionary, AQL, and UDF files, or NULL to use the parent directory
   * of the top-level input AQL file. The path can contain multiple directories, separated by the
   * character ';'. The path separator character is ';' regardless of platform.
   */
  private String dataPath;

  /**
   * Tokenization configuration to be used to compile dictionaries.
   */
  // Do not default it to anything; let the user either set the tokenizer config, or use an
  // instantiated Tokenizer
  // object
  private TokenizerConfig dictTokenizerConfig;

  /**
   * Tokenizer to be used to compile dictionaries, for cases when you wish to use a single instance
   * of the Tokenizer across multiple compile calls.
   */
  private Tokenizer dictTokenizer = null;

  /**
   * Create a parameter container with default values.
   */
  public CompileAQLParams() {
    performSRM = true;
    performSDM = true;
    performRSR = true;
    inEncoding = DEFAULT_ENCODING;
  }

  /**
   * Create a parameter container for the common scenario when the input non-modular AQL is in a
   * file and the output compiled module is to be written to a location specified by the output URI.
   *
   * @param inputFile Top-level non-modular AQL file
   * @param outputURI Output URI where the compiled module will be placed. Should point to a valid
   *        directory or JAR/ZIP file on the local filesystem. See
   *        <a href="../../../../overview-summary.html#uri">Supported URI Formats</a> for details.
   * @param dataPath Path for finding auxiliary dictionary, AQL, and UDF files, or NULL to use the
   *        parent directory of the top-level input AQL file. The path can contain multiple
   *        directories, separated by the character ';'. The path separator character is ';'
   *        regardless of platform.
   */
  public CompileAQLParams(File inputFile, String outputURI, String dataPath) {
    this.inputFile = inputFile;
    this.outputURI = outputURI;
    this.dataPath = dataPath;
  }

  /**
   * Create a parameter container for the common scenario when the input AQL is a set of modules and
   * the compiled representation of those modules (.tam files) are sent to a directory or a
   * .jar/.zip archive file. Use either {@link #setTokenizerConfig(TokenizerConfig)} or
   * {@link #setTokenizer(Tokenizer)} to indicate the type of tokenizer to use when compiling
   * dictionaries.
   *
   * @param inputModules array of URIs pointing to the root directories of module source code on the
   *        local filesystem; one entry per module. See
   *        <a href="../../../../overview-summary.html#uri">Supported URI Formats</a> for details.
   * @param outputURI path to directory or .jar/.zip file on the local file system where the
   *        compiled module representation (.tam file) of each input module is sent to. See
   *        <a href="../../../../overview-summary.html#uri">Supported URI Formats</a> for details.
   * @param modulePath list, separated by semi-colon (;), of paths to directories or .jar/.zip files
   *        where the compiled representation (.tam) of modules referenced in the input modules are
   *        to be found. Compiled modules are searched for only inside directories specified in this
   *        path.
   * @throws Exception
   */
  public CompileAQLParams(String[] inputModules, String outputURI, String modulePath)
      throws Exception {
    setInputModules(inputModules);
    setOutputURI(outputURI);
    setModulePath(modulePath);
  }

  /**
   * Create a parameter container for the common scenario when the input AQL is a set of modules,
   * the compiled representation of those modules (.tam files) are sent to a directory or a
   * .jar/.zip archive file, and you wish to use a specific tokenizer configuration for compiling
   * dictionaries.
   *
   * @param inputModules array of URIs pointing to the root directories of module source code on the
   *        local filesystem; one entry per module. See
   *        <a href="../../../../overview-summary.html#uri">Supported URI Formats</a> for details.
   * @param outputURI path to directory or .jar/.zip file on the local file system where the
   *        compiled module representation (.tam file) of each input module is sent to. See
   *        <a href="../../../../overview-summary.html#uri">Supported URI Formats</a> for details.
   * @param modulePath list, separated by semi-colon (;), of paths to directories or .jar/.zip files
   *        where the compiled representation (.tam) of modules referenced in the input modules are
   *        to be found. Compiled modules are searched for only inside directories specified in this
   *        path.
   * @param tokenizerCfg tokenization configuration to be used while compiling internal
   *        dictionaries; if <code>null</code> is passed, this method will create a Standard
   *        tokenizer by default
   * @throws Exception
   */
  public CompileAQLParams(String[] inputModules, String outputURI, String modulePath,
      TokenizerConfig tokenizerCfg) throws Exception {
    setInputModules(inputModules);
    setOutputURI(outputURI);
    setModulePath(modulePath);
    // For backward compatibility, do not change the behavior of the constructor when passed a null
    // tokenizer config
    setTokenizerConfig(null == tokenizerCfg ? new TokenizerConfig.Standard() : tokenizerCfg);
  }

  /**
   * Set the top-level AQL file. Useful for compiling AQL from a file.
   *
   * @param inputFile input AQL file
   * @throws IllegalArgumentException if input string is already set using
   *         {@link #setInputStr(String)}.
   */
  public void setInputFile(File inputFile) throws IllegalArgumentException {
    if (null != this.inputStr && null != inputFile) {
      throw new IllegalArgumentException(
          "The value of the input string is non-null. The values of input file and input string cannot be non-null at the same time. ");
    }
    this.inputFile = inputFile;
  }

  /**
   * Returns the top-level AQL file.
   *
   * @return the top-level AQL file.
   */
  public File getInputFile() {
    return inputFile;
  }

  /**
   * Set the string containing the top-level AQL of an extractor. Useful when compiling from a
   * string.
   *
   * @param aqlStr string containing the top-level AQL of an extractor.
   * @throws IllegalArgumentException if input file is already set using the
   *         {@link #setInputFile(File)}.
   */
  public void setInputStr(String aqlStr) throws IllegalArgumentException {
    if (null != this.inputFile) {
      throw new IllegalArgumentException(
          "The value of the input file is non-null. The values of input file and input string cannot be non-null at the same time. ");
    }
    this.inputStr = aqlStr;
  }

  /**
   * Returns the string containing the top-level AQL of an extractor.
   *
   * @return the string containing the top-level AQL of an extractor. <code>null</code> if the input
   *         string has not been set using {@link #setInputStr(String)}.
   */
  public String getInputStr() {
    return inputStr;
  }

  /**
   * Returns the encoding of the input AQL file, and any auxiliary AQL or dictionary files to be
   * loaded from disk.
   *
   * @return encoding of the input AQL file, and any auxiliary AQL or dictionary files to be loaded
   *         from disk.
   */
  public String getInEncoding() {
    return inEncoding;
  }

  /**
   * Set the encoding of the input AQL file (if compiling from a file), and any auxiliary AQL or
   * dictionary files to be loaded from disk.
   *
   * @param encoding encoding of the input AQL file (if compiling from a file), and any auxiliary
   *        AQL or dictionary files to be loaded from disk. Default value is UTF-8.
   */
  public void setInEncoding(String encoding) {
    inEncoding = encoding;
  }

  /**
   * Enable or disable the Shared Dictionary Matching (SDM) optimization. When enabled, the Text
   * Analytics optimizer determines all cases where more than one dictionary is evaluated against a
   * given source text and evaluates each of those sets of dictionaries together in one pass over
   * the source text. Improves the throughput of the extractor.
   *
   * @param flag <code>true</code> to enable the Shared Dictionary Matching (SDM) optimization.
   *        <code>true</code> by default.
   */
  public void setPerformSDM(Boolean flag) {
    performSDM = flag;
  }

  /**
   * Return whether the Shared Dictionary Matching (SDM) optimization is enabled.
   *
   * @return <code>true</code> if Shared Dictionary Matching (SDM) is enabled.
   */
  public Boolean getPerformSDM() {
    return performSDM;
  }

  /**
   * Enable or disable the Regex Strength Reduction (RSR) optimization. When enabled, the Text
   * Analytics Optimizer uses the Text Analytics Runtime built-in fast regex (SimpleRegex) engine
   * when possible, instead of the Java regex engine. Improves the throughput of the extractor, but
   * may also increase memory footprint.
   *
   * @param flag <code>true</code> to enable Regex Strength Reduction (RSR). <code>true</code> by
   *        default.
   */
  public void setPerformRSR(Boolean flag) {
    performRSR = flag;
  }

  /**
   * Return whether the Regex Strength Reduction (RSR) optimization is enabled.
   *
   * @return <code>true</code> if Regex Strength Reduction (RSR) is enabled.
   */
  public Boolean getPerformRSR() {
    return performRSR;
  }

  /**
   * Return whether Shared Regex Matching (SRM) optimization is enabled.
   *
   * @return <code>true</code> if Shared Regex Matching (SRM) is enabled.
   */
  public final boolean getPerformSRM() {
    return performSRM;
  }

  /**
   * Enable or disable the Shared Regex Matching (SRM) optimization. When enabled, the Text
   * Analytics Optimizer determines all compatible regular expressions over a given input view and
   * evaluates all of them at the same time. Improves the throughput of the extractor.
   *
   * @param flag <code>true</code> to enable Regex Strength Reduction (RSR). <code>true</code> by
   *        default.
   */
  public final void setPerformSRM(boolean flag) {
    this.performSRM = flag;
  }

  /**
   * Returns dataPath string for auxiliary dictionary, AQL, and UDF files.
   *
   * @return dataPath string for auxiliary dictionary, AQL, and UDF files.
   */
  public final String getDataPath() {
    return dataPath;
  }

  /**
   * Set the path for finding auxiliary dictionary, AQL, and UDF files, or NULL to use the parent
   * directory of the top-level input AQL file. The path can contain multiple directories, separated
   * by the character ';'. The path separator character is ';' regardless of platform. The
   * <code>dataPath</code> parameter is used only when compiling non-modular AQL.
   *
   * @param dataPath string containing one or more directory paths separated by ';' regardless of
   *        platform, or <code>null</code> to use the parent directory of the top-level input file
   *        when input file is not null, or when there are no auxiliary files.
   */
  public final void setDataPath(String dataPath) {
    this.dataPath = dataPath;
  }

  /**
   * Sets the tokenizer configuration to compile dictionaries.
   * 
   * @param dictTokenizerConfig tokenizer configuration to be used for dictionary compilation.
   */
  public void setTokenizerConfig(TokenizerConfig dictTokenizerConfig) {
    if (null != dictTokenizerConfig && null != this.dictTokenizer) {
      throw new IllegalArgumentException(
          "The value of the input tokenizer configuration is non-null. The tokenizer configuration and the tokenizer cannot be non-null at the same time. ");
    }

    this.dictTokenizerConfig = dictTokenizerConfig;
  }

  /**
   * Returns the tokenizer configuration used to compile the dictionaries.
   *
   * @return the tokenizer configuration used to compile the dictionaries set using
   *         {@link #setTokenizerConfig(Tokenizer)} if that method was used, or <code>null</code>
   *         otherwise
   */
  public TokenizerConfig getTokenizerConfig() {
    return this.dictTokenizerConfig;
  }

  /**
   * Set the tokenizer for compiling dictionaries. Use this method in lieu of
   * {@link #setTokenizerConfig(TokenizerConfig)} if you are in a highly interactive scenario where
   * you must compile AQL code repeatedly and instantiating the tokenizer takes a non-trivial amount
   * of time, therefore, you wish to save time by instantiating the tokenizer once and reusing it
   * many times.
   *
   * @param dictTokenizer tokenizer to be used for dictionary compilation.
   */
  public void setTokenizer(Tokenizer dictTokenizer) {
    if (null != this.dictTokenizerConfig && null != dictTokenizer) {
      throw new IllegalArgumentException(
          "The value of the input tokenizer is non-null. The tokenizer configuration and the tokenizer cannot be non-null at the same time. ");
    }

    this.dictTokenizer = dictTokenizer;
  }

  /**
   * Returns the tokenizer used to compile the dictionaries.
   *
   * @return the tokenizer used to compile the dictionaries set using
   *         {@link #setTokenizer(Tokenizer)} if that method was used, or <code>null</code>
   *         otherwise.
   */
  public Tokenizer getTokenizer() {
    return this.dictTokenizer;
  }

  /**
   * Returns the list of modules to be compiled.
   *
   * @return the list of modules to be compiled
   */
  public String[] getInputModules() {
    return inputModules;
  }

  /**
   * Sets the list of URIs pointing to the directory of the AQL source module to be compiled; source
   * modules should reside on local filesystem. See
   * <a href="../../../../overview-summary.html#uri">Supported URI Formats</a> for details.
   *
   * @param inputModules array of URIs pointing to the root directories of module source code on the
   *        local filesystem; one entry per module
   * @throws Exception
   */
  public void setInputModules(String[] inputModules) throws Exception {
    if (inputModules == null) {
      this.inputModules = null;
      return;
    }

    // Start by making a copy, because we may correct URIs below
    String[] copy = new String[inputModules.length];
    System.arraycopy(inputModules, 0, copy, 0, inputModules.length);

    for (int i = 0; i < copy.length; i++) {

      // Detect local filesystem URIs without a scheme and reformat them, then append a / if
      // necessary
      copy[i] = FileOperations.normalizeURI(FileOperations.resolvePathToLocal(copy[i]));
    }

    this.inputModules = copy;
  }

  /**
   * Returns the URI where compiler will store the compiled modules.
   *
   * @return the URI where compiler will store the compiled modules
   */
  public String getOutputURI() {
    return outputURI;
  }

  /**
   * Sets the URI where compiler should store the compiled modules. This URI should point to an
   * existing directory, or a JAR/ZIP file on local filesystem. See
   * <a href="../../../../overview-summary.html#uri">Supported URI Formats</a> for details. The
   * modules specified through {@link #setInputModules(String[])} are compiled into
   * <code>&lt;moduleName&gt;.tam</code> and placed into the location represented by the
   * <code>outputURI</code> parameter.
   *
   * @param outputURI URI to a directory or JAR/ZIP file on the local filesystem where the compiler
   *        should store the compiled module
   */
  public void setOutputURI(String outputURI) throws Exception {
    if (outputURI == null) {
      this.outputURI = null;
      return;
    }

    // Detect local filesystem URIs without a scheme and reformat them, then append a / if necessary
    this.outputURI = FileOperations.normalizeURI(FileOperations.resolvePathToLocal(outputURI));
  }

  /**
   * Returns the path used by the compiler to locate pre-compiled module files that are required for
   * compiling <code>inputModules</code>.
   *
   * @return the path used by the compiler to locate pre-compiled module files that are required for
   *         compiling <code>inputModules</code>
   */
  public String getModulePath() {
    return modulePath;
  }

  /**
   * Sets the path where the compiler can locate pre-compiled <code>.tam</code> files of the
   * dependent modules, while compiling the input modules. This path is a semicolon-separated list
   * of <code>file://</code> or <code>hdfs://</code> URIs that refer to absolute paths of locations
   * where dependent modules can be located. Each entry in the <code>modulePath</code> can be either
   * a directory (or) a JAR/ZIP file containing compiled modules (i.e., <code>.tam</code> files).
   * See <a href="../../../../overview-summary.html#uri">Supported URI Formats</a> for details.
   *
   * @param modulePath semicolon-separated list of URIs on local or distributed filesystem that
   *        refer to absolute paths of locations where dependent modules can be located;
   *        <code>null</code>, if the modules to compile are not dependent on any other modules.
   */
  public void setModulePath(String modulePath) throws Exception {
    this.modulePath = FileOperations.resolveModulePath(modulePath);
  }

  /**
   * Validates the parameters passed to the compiler. The following validation rules are enforced:
   * <br/>
   * <ol>
   * <li>Provide only one of the following parameters: <code>inputFile</code>,
   * <code>inputStr</code>, <code>inputModules</code>.</li>
   * <li>Only one of the input parameters listed above must be provided. They are mutually
   * exclusive.</li>
   * <li>When compiling modular AQLs, the <code>inputModules</code> array should be non-null and
   * have at least one entry.</li>
   * <li>The <code>outputURI</code> parameter must point to an existing directory (or) a JAR/ZIP
   * file.</li>
   * </ol>
   *
   * @throws InvalidCompileParamException if there is any validation error
   */
  public void validate() throws InvalidCompileParamException {
    InvalidCompileParamException errors =
        new InvalidCompileParamException(new CompilationSummaryImpl(this));

    // rule 1: at most one of inputFile and inputModules must be provided
    if (inputFile == null && inputModules == null && inputStr == null) {
      errors.addError(new Exception(
          "Input param for compilation not provided. Provide a non-null value for one of the following: inputFile, inputModules, inputStr"));
    }

    // rule 2: inputFile, inputStr, and inputModules are mutually exclusive
    // inputModules turns on non-BC mode, so with this check, we guarantee that
    // inputFile or inputStr will not be used as input parameter for non-BC mode
    if (inputFile != null && inputModules != null) {
      errors.addError(new Exception(
          "Only one of the following values must be provided: inputFile, inputModules"));
    } else if (inputFile != null && inputStr != null) {
      errors.addError(
          new Exception("Only one of the following values must be provided: inputFile, inputStr"));
    } else if (inputModules != null && inputStr != null) {
      errors.addError(new Exception(
          "Only one of the following values must be provided: inputModules, inputStr"));
    }

    // rule 3: ensure that inputModules[] is not empty
    if (null != inputModules && 0 == inputModules.length) {
      errors.addError(new Exception("inputModules[] can not be empty"));
    }

    // rule 4: outputURI should point to a valid directory (or) jar/zip file
    if (outputURI == null) {
      errors.addError(new Exception("Provide a non-null value for outputURI"));
    } else {
      try {
        // verify that outputURI is either a JAR/ZIP or a valid directory.
        if (false == outputURI.endsWith(".jar") && false == outputURI.endsWith(".zip")
            && false == FileOperations.isDirectory(outputURI)) {
          errors.addError(new Exception(String.format(
              "outputURI parameter should be either an existing directory or a JAR or ZIP file : %s",
              outputURI)));
        }
      } catch (Exception e) {
        // Build up a user friendly exception so someone can actually debug the problem...
        errors.addError(new URIToFileException(e, outputURI));
      }
    }

    // Rule 5: at most one of dictTokenizerConfig and dictTokenizer must be non-null
    if (dictTokenizerConfig == null && dictTokenizer == null) {
      errors.addError(new Exception(
          "Provide a non-null value for one of the following: dictTokenizerConfig or dictTokenizer"));
    }

    // TODO: once warnings are implemented, throw a deprecation warning if inputStr is non-null (and
    // in BC mode).

    if (errors.getAllCompileErrors().size() > 0) {
      throw errors;
    }
  }

  /**
   * Returns <code>true</code> if the given compilation parameters makes the compiler run in
   * backward-compatibility mode; returns <code>false</code> otherwise. The compiler will run in
   * backward-compatibility mode, if the input modules are NOT provided by invoking
   * {@link #setInputModules(String[])}.
   *
   * @return <code>true</code>, if the given compilation parameters make the compiler run in
   *         backward-compatibility mode;<code>false</code> otherwise
   */
  public boolean isBackwardCompatibilityMode() {
    return getInputModules() == null;
  }

  /**
   * {@inheritDoc} Creates and returns a copy of this object.
   */
  @Override
  public Object clone() throws CloneNotSupportedException {
    // Leverage default clone implementation for now, even though it is just a shallow copy
    return super.clone();
  }

  /**
   * Returns a normalized URI string. The following normalizations are performed: <br>
   * 1) URIs with missing schema are assumed to be pointing to the local filesystem. <br>
   * 2) A trailing '/' character is appended to URIs pointing to the directory, which already does
   * not contains one. <br>
   * In addition to normalization, this method also validates the given URI string for syntax(format
   * of URI); it does not validates the existence of the URI's target.
   *
   * @param origPath a raw URI passed into the params object
   * @return normalized and validated URI
   * @throws Exception if the URI can't be validated
   * @deprecated This function will be removed in later releases
   */
  @Deprecated
  public static String normalizeURI(String origPath) throws Exception {
    return FileOperations.normalizeURI(origPath);
  }

  /**
   * {@inheritDoc} Returns the textual representation of compilation parameter object.
   */
  @Override
  public String toString() {

    StringBuffer ret = new StringBuffer();

    ret.append(String.format("Input AQL file: %s\n", this.inputFile));
    ret.append(String.format("Input data path: %s\n", this.dataPath));
    ret.append(String.format("Input modules: %s\n",
        (null == inputModules ? null : Arrays.asList(this.inputModules))));
    ret.append(String.format("Module path: %s\n", this.modulePath));
    ret.append(String.format("Input encoding: %s\n", this.inEncoding));
    ret.append(String.format("Output URI: %s\n", this.outputURI));
    ret.append(String.format("Tokenization configuration: %s\n", this.dictTokenizerConfig));
    ret.append(String.format("Tokenizer instance has been cached: %s\n",
        (null != this.dictTokenizer) ? true : false));
    ret.append(String.format("Perform Regex Strength Reduction: %s\n", this.performRSR));
    ret.append(String.format("Perform Shared Regex Matching: %s\n", this.performSRM));
    ret.append(String.format("Perform Shared Dictionary Matching: %s\n", this.performSDM));

    return ret.toString();

  }
}
