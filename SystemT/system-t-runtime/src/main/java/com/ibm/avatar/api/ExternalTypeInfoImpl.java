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
import java.util.List;
import java.util.Map;

/**
 * This class implements the {@link ExternalTypeInfo} interface. There are additional internal
 * accessor methods to retrieve content of tables and dictionaries, while loading external artifacts
 * into operator graph.
 * 
 */
public class ExternalTypeInfoImpl implements ExternalTypeInfo {
  /**
   * Map of fully qualified table name, as declared in 'create external table ...' statement vs
   * table entries , as list of list of String object;where each element of the outer list represent
   * a table entry.
   */
  private Map<String, ArrayList<ArrayList<String>>> tableNameVsTableEntries;

  /**
   * Map of fully qualified table name, as declared in 'create external table ...' statement vs URI
   * to the file containing tuples in the table
   */
  private Map<String, String> tableNameVsTableFileURI;

  /**
   * Map of fully qualified dictionary name, as declared in 'create external dictionary ...'
   * statement vs entries of the dictionary as list of string
   */
  private Map<String, List<String>> dictionaryNameVsDictionaryEntry;

  /**
   * Map of fully qualified dictionary name, as declared in 'create external dictionary ...'
   * statement vs URI to the file containing dictionary entries, either in compiled or non compiled
   * format
   */
  private Map<String, String> dictionaryNameVsDictFileURI;

  /** This list hold the names of all the dictionaries added to the external type info object */
  private List<String> dictNameList;

  /** List to hold the names of all the tables added to the external type info object */
  private List<String> tableNameList;

  @Override
  public void addDictionary(String dictionaryName, List<String> dictionaryEntries) {
    addToGlobalDictNameList(dictionaryName);

    // Defensively copying the dictionary entries, to avoid side effects in loader from invoker
    dictionaryNameVsDictionaryEntry.put(dictionaryName, new ArrayList<String>(dictionaryEntries));
  }

  @Override
  public void addDictionary(String dictionaryName, String dictionaryFileURI) {
    addToGlobalDictNameList(dictionaryName);
    dictionaryNameVsDictFileURI.put(dictionaryName, dictionaryFileURI);
  }

  @SuppressWarnings("unchecked")
  @Override
  public void addTable(String tableName, ArrayList<ArrayList<String>> tableEntries) {
    addToGlobalTableNameList(tableName);

    // Defensively copying the table entries, to avoid side effects in loader from invoker
    tableNameVsTableEntries.put(tableName, (ArrayList<ArrayList<String>>) tableEntries.clone());
  }

  @Override
  public void addTable(String tableName, String tableFileURI) {
    addToGlobalTableNameList(tableName);
    tableNameVsTableFileURI.put(tableName, tableFileURI);
  }

  @Override
  public List<String> getDictionaryNames() {
    return this.dictNameList;
  }

  @Override
  public List<String> getTableNames() {
    return this.tableNameList;
  }

  // Internal API, not exposed publicly - to be consumed by loader

  /**
   * Main constructor to create an empty instance of {@link ExternalTypeInfo}.
   */
  public ExternalTypeInfoImpl() {
    tableNameVsTableEntries = new HashMap<String, ArrayList<ArrayList<String>>>();
    tableNameVsTableFileURI = new HashMap<String, String>();
    dictionaryNameVsDictFileURI = new HashMap<String, String>();
    dictionaryNameVsDictionaryEntry = new HashMap<String, List<String>>();
    dictNameList = new ArrayList<String>();
    tableNameList = new ArrayList<String>();
  }

  /**
   * Constructor to create an instance of {@link ExternalTypeInfo} object from map of external
   * dictionaries/tables name vs URIs to file containing dictionary/table entries.
   * 
   * @param dictionaryNameVsDictFileURI map of fully qualified external dictionay name vs URI to the
   *        file containing dictionary entries.
   * @param tableNameVsTableFileURI map of fully qualified external table name vs URI to the file
   *        containing table entries.
   */
  public ExternalTypeInfoImpl(Map<String, String> dictionaryNameVsDictFileURI,
      Map<String, String> tableNameVsTableFileURI) {
    this.dictionaryNameVsDictFileURI = dictionaryNameVsDictFileURI;

    if (null != dictionaryNameVsDictFileURI) {
      dictNameList = new ArrayList<String>(dictionaryNameVsDictFileURI.keySet());
    } else {
      dictNameList = new ArrayList<String>();
    }

    this.tableNameVsTableFileURI = tableNameVsTableFileURI;
    if (null != tableNameVsTableFileURI) {
      tableNameList = new ArrayList<String>(tableNameVsTableFileURI.keySet());
    } else {
      tableNameList = new ArrayList<String>();
    }

  }

  /**
   * Method to assert, if the entries for the given dictionary name are coming from a file.
   * 
   * @param dictName name of the dictionary on which 'coming from file' assert needs to be
   *        performed.
   * @return true, if the entries for the given dictionary are coming from a file.
   */
  public boolean isDictComingFromFile(String dictName) {
    return dictionaryNameVsDictFileURI.containsKey(dictName);
  }

  /**
   * @param dictName
   * @return
   */
  public List<String> getDictionaryEntries(String dictName) {
    return dictionaryNameVsDictionaryEntry.get(dictName);
  }

  /**
   * This method returns the URI string to the file containing entries for the given dictionary
   * name. Before calling this method, assert if at all entries are coming from file using the
   * {@link #isDictComingFromFile(String)} method.
   * 
   * @param dictName name of the dictionary which is coming from file.
   * @return the URI string to the file containing entries for the given dictionary name; null if
   *         entries are not coming from file.
   */
  public String getDictionaryFileURI(String dictName) {
    return dictionaryNameVsDictFileURI.get(dictName);
  }

  /**
   * Method to assert, if the entries for the given table name are coming from a file.
   * 
   * @param tableName name of the table on which 'coming from file' assert needs to be performed.
   * @return true, if the entries for the given table are coming from a file.
   */
  public boolean isTableComingFromFile(String tableName) {
    return tableNameVsTableFileURI.containsKey(tableName);
  }

  /**
   * This method returns the table entries for the given fully qualified table name, the entries are
   * returned as list of list of String object;where each element of the outer list represent a
   * table entry.
   * 
   * @param tableName fully qualified table name, as declared thru 'create external table... '
   *        statement.
   * @return the entries, as list of list of String object;where each element of the outer list
   *         represent a table entry.
   */
  public ArrayList<ArrayList<String>> getTableEntries(String tableName) {
    return tableNameVsTableEntries.get(tableName);
  }

  /**
   * This method returns the URI string to the file containing tuples for the given table name.
   * Before calling this method, assert if at all entries are coming from file using the
   * {@link #isTableComingFromFile(String)} method.
   * 
   * @param tableName name of the table which is coming from file.
   * @return the URI string to the file containing tuples for the given table name; null if entries
   *         are not coming from file.
   */
  public String getTableFileURI(String tableName) {
    return tableNameVsTableFileURI.get(tableName);
  }

  /**
   * @return true, if external type info instance is empty;
   */
  public boolean isEmpty() {
    return dictNameList.size() == 0 && tableNameList.size() == 0;
  }

  /**
   * This method returns, a string containing list of all the external dictionaries and tables added
   * to the external type info object.
   * 
   * @return textual representation of the external type info object.
   */
  @Override
  public String toString() {
    return String.format("External dictionaries: %s , External tables: %s", this.dictNameList,
        this.tableNameList);
  }

  /**
   * Method to add dictionary name to global dictionary list.
   */
  private void addToGlobalDictNameList(String dictName) {
    dictNameList.add(dictName);
  }

  /**
   * Method to add dictionary name to global table list.
   */
  private void addToGlobalTableNameList(String tableName) {
    tableNameList.add(tableName);
  }

}
