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
package com.ibm.avatar.algebra.util.table;

import java.util.ArrayList;

import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.aql.NickNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.tam.ModuleUtils;

/**
 * Class that encapsulates the parameters associated with a given AQL table.
 */
public class TableParams implements Comparable<TableParams> {

  /*
   * CONSTANTS
   */

  /** Name of the module where CreateTable statement is defined */
  private String moduleName = null;

  /** AQL name of the table */
  private String tableName = null;

  /** Name of table file, if any. */
  private String fileName = null;

  /** Array of column names */
  private ArrayList<NickNode> colNames = null;

  /** Array of column types */
  private ArrayList<NickNode> colTypes = null;

  /** TupleSchema generated from col names and call types */
  private TupleSchema schema = null;

  /** ArrayList<ArrayList<String>> tuples defined in file */
  private ArrayList<ArrayList<String>> tuples = null;

  /**
   * Returns the fully qualified name of the table
   * 
   * @return fully qualified name of the table
   */
  public String getTableName() {
    return ModuleUtils.prepareQualifiedName(moduleName, tableName);
  }

  /**
   * Returns the unqualified name of the table
   * 
   * @return unqualified name of the table
   */
  public String getUnqualifiedName() {
    return tableName;
  }

  /**
   * Sets the unqualified name of the table
   * 
   * @param tableName unqualified name of the table
   */
  public void setTableName(String tableName) {
    this.tableName = tableName;
  }

  /**
   * Sets the module name of the table
   * 
   * @param moduleName name of the module where CreateTable statement is defined
   */
  public void setModuleName(String moduleName) {
    this.moduleName = moduleName;
  }

  /**
   * Returns the name of the file containing the table tuples
   * 
   * @return name of the file containing the table tuples
   */
  public String getFileName() {
    return fileName;
  }

  /**
   * Sets the name of the file containing the table tuples
   * 
   * @param fileName file containing the table tuples
   */
  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  /**
   * Returns the table tuples
   * 
   * @return tuples the tuples of the table
   */
  public ArrayList<ArrayList<String>> getTuples() {
    return tuples;
  }

  /**
   * Sets the tuple values as read from a file
   * 
   * @param tuples tuple values
   */
  public void setTuples(ArrayList<ArrayList<String>> tuples) {
    this.tuples = tuples;
  }

  /**
   * Returns the table schema
   * 
   * @return schema the schema of the table
   */
  public TupleSchema getSchema() {
    return schema;
  }

  /**
   * Builds and sets the table schema from colnames and coltypes
   */
  public void setSchema() {
    String[] names = new String[this.colNames.size()];
    for (int i = 0; i < this.colNames.size(); i++) {
      names[i] = this.colNames.get(i).toString();
    }

    FieldType[] types = new FieldType[this.colTypes.size()];
    try {
      for (int i = 0; i < this.colTypes.size(); i++) {
        types[i] = FieldType.stringToFieldType(colTypes.get(i).getNickname());
      }

    } catch (ParseException pe) {
      //
    }

    this.schema = new TupleSchema(names, types);
  }

  /**
   * Sets the column names of the table
   * 
   * @param colNames column names
   */
  public void setColNames(ArrayList<NickNode> colNames) {
    this.colNames = colNames;
  }

  /**
   * Returns the table column names
   * 
   * @return colNames the table column names
   */
  public ArrayList<NickNode> getColNames() {
    return colNames;
  }

  /**
   * Sets the column types of the table
   * 
   * @param colTypes column types
   */
  public void setColTypes(ArrayList<NickNode> colTypes) {
    this.colTypes = colTypes;
  }

  /**
   * Returns the table column types
   * 
   * @return colTypes the table column types
   */
  public ArrayList<NickNode> getColTypes() {
    return colTypes;
  }

  /**
   * Main constructor
   * 
   * @param paramStrs list of name-value pairs containing config params for this table
   */
  public TableParams() {}

  /** Ensure that this set of parameters is valid. */
  public void validate() {
    // Sanity check
    if (null == tableName) {
      throw new IllegalArgumentException("No table name provided");
    }

  }

  /** Copy constructor. */
  public TableParams(TableParams o) {
    moduleName = o.moduleName;
    tableName = o.tableName;
    fileName = o.fileName;
    schema = o.schema;
    tuples = o.tuples;

    // Sanity check
    if (0 != compareTo(o)) {
      throw new RuntimeException("Copy constructor failed to create an exact copy");
    }
  }

  @Override
  public int compareTo(TableParams o) {

    // Try to keep these comparisons in alphabetical order.
    int val;

    val = compareWithNulls(moduleName, o.moduleName);
    if (val != 0) {
      return val;
    }

    val = compareWithNulls(tableName, o.tableName);
    if (val != 0) {
      return val;
    }

    val = compareWithNulls(fileName, o.fileName);
    if (val != 0) {
      return val;
    }

    val = compareWithNulls(schema, o.schema);
    if (val != 0) {
      return val;
    }

    val = compareWithNulls(tuples, o.tuples);
    if (val != 0) {
      return val;
    }

    // If we get here, the two inputs are identical.
    return 0;
  }

  /** A version of compareTo() that handles null values properly. */
  @SuppressWarnings("unchecked")
  private <T> int compareWithNulls(T first, T second) {

    if (null == first) {
      if (null == second) {
        return 0;
      } else {
        // Nulls sort lower.
        return -1;
      }
    } else {
      return ((Comparable<T>) first).compareTo(second);
    }
  }
}
