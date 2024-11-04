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
import java.util.List;

/**
 * An instance of this type acts as a container to hold information about external dictionaries and
 * external tables. The Text Analytics loader uses this information to load the external artifacts
 * into the operator graph. The information contained in an <code>ExternalTypeInfo</code> object
 * could be either the URI of files containing data for external dictionaries and external tables
 * (or) the actual content of external dictionaries and external tables.
 * <p>
 * Use the class {@link com.ibm.avatar.api.ExternalTypeInfoFactory} to create an instance of this
 * type.
 * </p>
 * Supported file formats for these external artifacts are:
 * <ul>
 * <li>For external tables: Comma-separated values (CSV) with headers.
 * <li>For external dictionaries: Carriage-return-delimited text files with one dictionary entry per
 * line.
 * </ul>
 * <p>
 * See <a href= "../../../../overview-summary.html#ext-file-format" >External Artifact File
 * Formats</a> for more details.
 * </p>
 *
 * @see OperatorGraph
 * @see ExternalTypeInfoFactory
 */
public interface ExternalTypeInfo {

  /**
   * Populates the <code>ExternalTypeInfo</code> object with entries for the given external
   * dictionary.
   *
   * @param dictionaryName name of the external dictionary, as declared in the
   *        <code>create external dictionary</code> statement, fully qualified with the module name
   * @param dictionaryEntries entries of the dictionary, as a list of String objects
   */
  public void addDictionary(String dictionaryName, List<String> dictionaryEntries);

  /**
   * Adds the specified external dictionary URI to the <code>ExternalTypeInfo</code> object. Use
   * this API if external dictionary entries are available in a file.
   *
   * @param dictionaryName name of the external dictionary, as declared in the
   *        <code>create external dictionary</code> statement, fully qualified with the module name
   * @param dictionaryFileURI URI of the file containing the dictionary entries in supported format.
   *        <code>file://</code>, <code>hdfs://</code>, and <code>gpfs://</code> URI formats are
   *        supported. See <a href="../../../../overview-summary.html#uri">Supported URI Formats</a>
   *        for details.
   */
  public void addDictionary(String dictionaryName, String dictionaryFileURI);

  /**
   * Populates the <code>ExternalTypeInfo</code> object with entries for the given external table.
   * The order and type of fields in the <code>tableEntries</code> parameter should match with the
   * schema of the external table as defined in the AQL. The loader throws an exception if there is
   * a mismatch of schemas.
   *
   * @param tableName name of the external table, as declared in the
   *        <code>create external table</code> statement, fully qualified with the module name
   * @param tableEntries entries of the table, represented as list of list of String objects. The
   *        outer list is a collection of table rows and the inner list is a collection of fields in
   *        a given row.
   */
  public void addTable(String tableName, ArrayList<ArrayList<String>> tableEntries);

  /**
   * Adds the specified external table URI to the <code>ExternalTypeInfo</code> object. Use this API
   * if external table entries are available in a file. This API accepts only MS-Excel style CSV
   * files with headers. If either the header or the entries in the CSV file does not adhere to the
   * declared schema of the external table, the loader will throw an exception. <br>
   *
   * @param tableName name of the external table, as declared in the
   *        <code>create external table</code> statement , fully qualified by the containing
   *        module's name
   * @param tableFileURI URI to the file containing table entries in the supported format.
   *        <code>file://</code>, <code>hdfs://</code>, and <code>gpfs://</code> URI formats are
   *        supported. See <a href="../../../../overview-summary.html#uri">Supported URI Formats</a>
   *        for details.
   */
  public void addTable(String tableName, String tableFileURI);

  /**
   * Returns the names of all external dictionaries added to the <code>ExternalTypeInfo</code>
   * object.
   *
   * @return names of all external dictionaries added to the <code>ExternalTypeInfo</code> object.
   */
  public List<String> getDictionaryNames();

  /**
   * Returns the names of all external tables added to the <code>ExternalTypeInfo</code> object.
   *
   * @return names of all external tables added to the <code>ExternalTypeInfo</code> object.
   */
  public List<String> getTableNames();
}
