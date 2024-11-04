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
package com.ibm.wcs.annotationservice.models.json;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ibm.wcs.annotationservice.AnnotationServiceConstants;
import com.ibm.wcs.annotationservice.exceptions.AnnotationServiceException;
import com.ibm.wcs.annotationservice.exceptions.VerboseNullPointerException;

public class SystemTAnnotatorBundleConfig extends AnnotatorBundleConfig {
  private static final String CONTEXT_NAME = "SystemT Annotator Module Configuration";

  /**
   * SystemT-SPECIFIC CONFIGURATION PARAMETERS - Remember to update {@link #equals()},
   * {@link #validate()} and {@link #toJson()} when making changes
   */
  @JsonCreator
  public SystemTAnnotatorBundleConfig(
      // General
      @JsonProperty(value = "version", required = true) String version,
      @JsonProperty(value = "annotator", required = true) HashMap<String, String> annotator,
      @JsonProperty(value = "annotatorRuntime", required = true) String annotatorRuntime,
      @JsonProperty(value = "acceptedContentTypes",
          required = true) List<String> acceptedContentTypes,
      @JsonProperty(value = "inputTypes") List<String> inputTypes,
      @JsonProperty(value = "outputTypes", required = true) List<String> outputTypes,
      @JsonProperty(value = "location", required = true) String location,
      @JsonProperty(value = "serializeAnnotatorInfo") boolean serializeAnnotatorInfo,
      @JsonProperty(value = "serializeSpan") String serializeSpan,

      // SystemT
      @JsonProperty(value = "moduleNames", required = true) List<String> moduleNames,
      @JsonProperty(value = "modulePath") List<String> modulePath,
      @JsonProperty(value = "sourceModules") List<String> sourceModules,
      @JsonProperty(value = "externalDictionaries") HashMap<String, String> externalDictionaries,
      @JsonProperty(value = "externalTables") HashMap<String, String> externalTables,
      @JsonProperty(value = "tokenizer", required = true) String tokenizer,
      @JsonProperty(value = "tokenizerPearFile") String tokenizerPearFile)
      throws AnnotationServiceException {
    super(version, annotator, annotatorRuntime, acceptedContentTypes, inputTypes, outputTypes,
        location, serializeAnnotatorInfo, serializeSpan);
    this.moduleNames = moduleNames;
    this.modulePath = modulePath;
    this.sourceModules = sourceModules;
    this.externalDictionaries = externalDictionaries;
    this.externalTables = externalTables;
    this.tokenizer = tokenizer;
    this.tokenizerPearFile = tokenizerPearFile;

    // SystemT
    // FIXME: JsonProperty(required=true) is not supported until 2.6, so manual check. Remove once
    // upgraded
    /** TEMP CHECK **/
    if (null == moduleNames)
      throw new VerboseNullPointerException(CONTEXT_NAME,
          AnnotationServiceConstants.MODULE_NAMES_FIELD_NAME);

    if (null == tokenizer)
      throw new VerboseNullPointerException(CONTEXT_NAME,
          AnnotationServiceConstants.TOKENIZER_FIELD_NAME);
    /** TEMP CHECK **/

    if (0 == moduleNames.size())
      throw new AnnotationServiceException(
          "Value of field '%s' in %s is empty; provide at least one module name",
          AnnotationServiceConstants.MODULE_NAMES_FIELD_NAME, CONTEXT_NAME);

    // Either modulePath or sourceModules is required.
    if ((null == modulePath || 0 == modulePath.size())
        && (null == sourceModules || 0 == sourceModules.size()))
      throw new AnnotationServiceException(
          "Values of field '%s' and '%s' in %s are empty; provide at least one entry in the module path or the source modules",
          AnnotationServiceConstants.MODULE_PATH_FIELD_NAME,
          AnnotationServiceConstants.SOURCE_MODULES_FIELD_NAME, CONTEXT_NAME);
    
    // convert old enum strings to classnames for backward compatibility. to be remove on next major
    // version
    if (tokenizer.toUpperCase().equals("MULTILINGUAL")) {
      this.tokenizer = "com.ibm.avatar.algebra.util.tokenize.TokenizerConfig$Multilingual";
    }

    else if (tokenizer.toUpperCase().equals("MULTILINGUAL_UNIVERSAL")) {
      this.tokenizer = "com.ibm.avatar.algebra.util.tokenize.TokenizerConfig$MultilingualUniversal";
    }

    else if (tokenizer.toUpperCase().equals("MULTILINGUALUNIVERSAL")) {
      this.tokenizer = "com.ibm.avatar.algebra.util.tokenize.TokenizerConfig$MultilingualUniversal";
    }
  }


  /**
   * 
   */
  private static final long serialVersionUID = 1L;

  /**
   * SYSTEMT-SPECIFIC CONFIGURATION PARAMETERS - Remember to update {@link #equals()} and
   * {@link #hashCode()} when making changes
   */

  /**
   * For SystemT annotators, the set of modules to instantiate for this annotator as a JSON Array of
   * Strings, where each string is a module name
   */
  @JsonProperty(required = true)
  private List<String> moduleNames = null;

  /**
   * For SystemT annotators, the module path for this annotator as an array of String values, where
   * each String is a HDFS URI pointing to a directory, a .jar or .zip file containing compiled AQL
   * modules (.tam files) Either this or *sourceModules* field is required.
   */
  private List<String> modulePath = null;

  /**
   * For SystemT annotators, the AQL module path for this annotator as an array of String values,
   * where each String points to a source AQL module directory, the path being relative to the value
   * of location. Either this or *modulePath* field is required.
   */
  private List<String> sourceModules = null;

  /**
   * For SystemT annotators, the external dictionaries used by this annotator, as a set of key value
   * pairs, where the key is the AQL external dictionary name and the value is the location on HDFS
   * of the dictionary file
   */
  private HashMap<String, String> externalDictionaries = null;

  /**
   * For SystemT annotators, the external tables used by this annotator, as a set of key value
   * pairs, where the key is the AQL external stable name and the value is the location on HDFS of
   * the corresponding CSV file with the table entries
   */
  private HashMap<String, String> externalTables = null;

  /**
   * For SystemT annotators, the type of tokenizer used by the SystemT annotator, e.g., standard
   */
  @JsonProperty(required = true)
  private String tokenizer = null;

  /**
   * For SystemT annotators, the pearfile specifies the tokenizer
   */
  private String tokenizerPearFile = null;

  /**
   * @return the set of SystemT modules to instantiate for the SystemT annotator
   */
  public List<String> getModuleNames() {
    return moduleNames;
  }


  /**
   * @param moduleNames the moduleNames to set
   */
  public void setModuleNames(List<String> moduleNames) {
    this.moduleNames = moduleNames;
  }


  /**
   * @return the modulePath
   */
  public List<String> getModulePath() {

    return modulePath;
  }

  /**
   * Return the module path for the SystemT annotator. This method does not validate whether each
   * module path element exists on the file system.
   * 
   * @param resolvePaths true to return absolute paths that are resolved with respect to the
   *        {@link #location}, false otherwise (i.e., return the original relative paths set in the
   *        annotator bundle config)
   * @return the module path for this SystemT annotator, where each entry is a path pointing to a
   *         directory, a .jar or .zip file containing compiled AQL modules (.tam files), or null if
   *         not configured; if the resolvePaths argument is set to false, the path is relative to
   *         {@link #location}, otherwise, the path is the absolute path resolved with respect to
   *         {@link #location}; the absolute path is obtained by concatenating the location and the
   *         path using separator {@link File#separatorChar}
   * @throws AnnotationServiceException
   */
  public List<String> getModulePath(boolean resolvePaths) throws AnnotationServiceException {
    // If we user doesn't request paths to be resolved, just return the original ones
    if (false == resolvePaths)
      return getModulePath();

    // If null, just return null
    if (null == modulePath)
      return null;

    // User has requested resolved paths
    ArrayList<String> modulePathResolved = new ArrayList<String>(modulePath.size());

    for (String path : modulePath) {
      modulePathResolved.add(resolveRelativePath(getLocation(), path));
    }

    return modulePathResolved;
  }

  /**
   * @param modulePath the modulePath to set
   */
  public void setModulePath(List<String> modulePath) {
    this.modulePath = modulePath;
  }

  /**
   * @return the sourceModules
   */
  public List<String> getSourceModules() {
    return sourceModules;
  }

  /**
   * Return the source modules for the SystemT annotator. This method does not validate whether each
   * source modules element exists on the file system.
   *
   * @param resolvePaths true to return absolute paths that are resolved with respect to the
   *        {@link #location}, false otherwise (i.e., return the original relative paths set in the
   *        annotator bundle config)
   * @return the source modules for this SystemT annotator, where each entry is a path pointing to a
   *         directory containing AQL modules (.aql files), or null if not configured; if the
   *         resolvePaths argument is set to false, the path is relative to {@link #location},
   *         otherwise, the path is the absolute path resolved with respect to {@link #location};
   *         the absolute path is obtained by concatenating the location and the path using
   *         separator {@link File#separatorChar}
   */
  public List<String> getSourceModules(boolean resolvePaths) {
    // If we user doesn't request paths to be resolved, just return the original ones
    if (false == resolvePaths)
      return getSourceModules();

    // If null, just return null
    if (null == sourceModules)
      return null;

    // User has requested resolved paths
    ArrayList<String> sourceModulesResolved = new ArrayList<String>(sourceModules.size());

    for (String path : sourceModules) {
      sourceModulesResolved.add(resolveRelativePath(getLocation(), path));
    }

    return sourceModulesResolved;
  }

  /**
   * @param the sourceModules to set
   */
  public void setSourceModules(List<String> sourceModules) {
    this.sourceModules = sourceModules;
  }

  /**
   * @return the externalDictionary
   */
  public HashMap<String, String> getExternalDictionaries() {
    return externalDictionaries;
  }

  /**
   * Return the mapping from SystemT external dictionary names to paths for the SystemT annotator.
   * This method does not validate whether each dictionary path exists on the file system.
   * 
   * @param resolvePaths true to return absolute paths that are resolved with respect to the
   *        {@link #location}, false otherwise (i.e., return the original relative paths set in the
   *        annotator bundle config)
   * @return the external dictionaries used by this SystemT annotator, as a set of key value pairs,
   *         where the key is the AQL external dictionary name and its value is the path to the
   *         dictionary file, or null if not configured; if the resolvePaths argument is set to
   *         false, the path is relative to {@link #location}, otherwise, the path is the absolute
   *         path resolves with respect to {@link #location}; the absolute path is obtained by
   *         concatenating the location and the path using separator {@link File#separatorChar}
   * @throws AnnotationServiceException
   */
  public HashMap<String, String> getExternalDictionaries(boolean resolvePaths) {
    // If we user doesn't request paths to be resolved, just return the original ones
    if (false == resolvePaths)
      return getExternalDictionaries();

    // If null, just return null
    if (null == externalDictionaries)
      return null;

    // User has requested resolved paths
    HashMap<String, String> externalDictionariesResolved =
        new HashMap<String, String>(externalDictionaries.size());

    for (Entry<String, String> entry : externalDictionaries.entrySet()) {
      String dictName = entry.getKey();
      String dictPath = entry.getValue();

      externalDictionariesResolved.put(dictName, resolveRelativePath(getLocation(), dictPath));
    }

    return externalDictionariesResolved;
  }


  /**
   * @param externalDictionary the externalDictionary to set
   */
  public void setExternalDictionary(HashMap<String, String> externalDictionary) {
    this.externalDictionaries = externalDictionary;
  }


  /**
   * @return the externalTables
   */
  public HashMap<String, String> getExternalTables() {
    return externalTables;
  }

  /**
   * Return the mapping from SystemT external table names to paths for the SystemT annotator. This
   * method does not validate whether each table path exists on the file system.
   * 
   * @param resolvePaths true to return absolute paths that are resolved with respect to the
   *        {@link #location}, false otherwise (i.e., return the original relative paths set in the
   *        annotator bundle config)
   * @return the external tables used by this SystemT annotator, as a set of key value pairs, where
   *         the key is the AQL external table name and its value is the path to the CSV file with
   *         the table entries, or null if not configured; if the resolvePaths argument is set to
   *         false, the path is relative to {@link #location}, otherwise, the path is the absolute
   *         path resolves with respect to {@link #location}; the absolute path is obtained by
   *         concatenating the location and the path using separator {@link File#separatorChar}
   * @throws AnnotationServiceException
   */
  public HashMap<String, String> getExternalTables(boolean resolvePaths) {
    // If we user doesn't request paths to be resolved, just return the original ones
    if (false == resolvePaths)
      return externalTables;

    // If null, just return null
    if (null == externalTables)
      return null;

    // User has requested resolved paths
    HashMap<String, String> externalTablesResolved =
        new HashMap<String, String>(externalTables.size());

    for (Entry<String, String> entry : externalTables.entrySet()) {
      String tableName = entry.getKey();
      String tablePath = entry.getValue();
      externalTablesResolved.put(tableName, resolveRelativePath(getLocation(), tablePath));
    }

    return externalTablesResolved;
  }


  /**
   * @param externalTable the externalTable to set
   */
  public void setExternalTable(HashMap<String, String> externalTable) {
    this.externalTables = externalTable;
  }


  /**
   * @return the type of tokenizer used by the SystemT annotator, e.g., standard
   */
  public String getTokenizer() {
    return tokenizer;
  }


  /**
   * @param tokenizer the tokenizer to set
   */
  public void setTokenizer(String tokenizer) {
    this.tokenizer = tokenizer;
  }


  /**
   * @return the tokenizerPearFile
   */
  public String getTokenizerPearFile() {
    return tokenizerPearFile;
  }

  /**
   * The path of the SystemT tokenizer pear file..
   * 
   * @param resolvePaths true to return absolute paths that are resolved with respect to the
   *        {@link #location}, false otherwise (i.e., return the original relative paths set in the
   *        annotator bundle config)
   * @return location of the SystemT pear file; if the resolvePaths argument is set to false, the
   *         path is relative to {@link #location}, otherwise, the path is the absolute path
   *         resolved with respect to {@link #location}; the absolute path is obtained by
   *         concatenating the location and the path using separator {@link File#separatorChar}
   */
  public String getTokenizerPearFile(boolean resolvePaths) {

    // If we user doesn't request paths to be resolved, just return the original ones
    if (false == resolvePaths)
      return tokenizerPearFile;

    // If null, just return null
    if (null == tokenizerPearFile)
      return null;

    // User has requested resolved paths
    return resolveRelativePath(getLocation(), tokenizerPearFile);
  }


  /**
   * @param tokenizerPearFile the tokenizerPearFile to set
   */
  public void setTokenizerPearFile(String tokenizerPearFile) {
    this.tokenizerPearFile = tokenizerPearFile;
  }


  @Override
  public String getPearFilePath() {
    return getTokenizerPearFile();
  }

  @Override
  public String getPearFileResolvedPath() {
    return getTokenizerPearFile(true);
  }


  /*
   * (non-Javadoc)
   * 
   * @see java.lang.Object#hashCode()
   */
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result =
        prime * result + ((externalDictionaries == null) ? 0 : externalDictionaries.hashCode());
    result = prime * result + ((externalTables == null) ? 0 : externalTables.hashCode());
    result = prime * result + ((moduleNames == null) ? 0 : moduleNames.hashCode());
    result = prime * result + ((modulePath == null) ? 0 : modulePath.hashCode());
    result = prime * result + ((sourceModules == null) ? 0 : sourceModules.hashCode());
    result = prime * result + ((tokenizer == null) ? 0 : tokenizer.hashCode());
    result = prime * result + ((tokenizerPearFile == null) ? 0 : tokenizerPearFile.hashCode());
    return result;
  }


  /*
   * (non-Javadoc)
   * 
   * @see java.lang.Object#equals(java.lang.Object)
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (!super.equals(obj))
      return false;
    if (getClass() != obj.getClass())
      return false;
    SystemTAnnotatorBundleConfig other = (SystemTAnnotatorBundleConfig) obj;
    if (externalDictionaries == null) {
      if (other.externalDictionaries != null)
        return false;
    } else if (!externalDictionaries.equals(other.externalDictionaries))
      return false;
    if (externalTables == null) {
      if (other.externalTables != null)
        return false;
    } else if (!externalTables.equals(other.externalTables))
      return false;
    if (moduleNames == null) {
      if (other.moduleNames != null)
        return false;
    } else if (!moduleNames.equals(other.moduleNames))
      return false;
    if (modulePath == null) {
      if (other.modulePath != null)
        return false;
    } else if (!modulePath.equals(other.modulePath))
      return false;
    if (sourceModules == null) {
      if (other.sourceModules != null)
        return false;
    } else if (!sourceModules.equals(other.sourceModules))
      return false;
    if (tokenizer == null) {
      if (other.tokenizer != null)
        return false;
    } else if (!tokenizer.equals(other.tokenizer))
      return false;
    if (tokenizerPearFile == null) {
      if (other.tokenizerPearFile != null)
        return false;
    } else if (!tokenizerPearFile.equals(other.tokenizerPearFile))
      return false;
    return true;
  }
}

