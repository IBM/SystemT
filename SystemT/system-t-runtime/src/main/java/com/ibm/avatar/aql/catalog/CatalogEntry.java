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

import java.util.ArrayList;

import com.ibm.avatar.aql.AQLParseTreeNode;
import com.ibm.avatar.aql.ErrorLocation;
import com.ibm.avatar.aql.ImportNode;
import com.ibm.avatar.aql.ParseException;

/**
 * Class that encapsulates the information about a particular catalog item. Specialized catalog
 * implementations may add additional methods to this class to support, for example, reading UIMA
 * types out of the CAS.
 */
public abstract class CatalogEntry {
  /**
   * This attribute references the import statement that imported the view/table/dict/func
   * represented by the current instance of catalog entry. Remember that catalog entries are created
   * for both 'defined' elements as well as 'imported' elements. Since the catalog entry of an
   * imported element may not have an associated parse tree node, we track the importing element so
   * that any compilation error message related to the imported element can be linked to it.
   */
  protected ImportNode importingNode;

  protected CatalogEntry(String name) {
    this.name = name;
  }

  /**
   * Name associated with this entry; could be a view name or an external type name.
   */
  private String name;

  public String getName() {
    return name;
  }

  /**
   * Special method so that ScalarFuncCatalogEntry can use reflection to determine the function name
   * after the call to super().
   */
  protected void setName(String name) {
    this.name = name;
  }

  /**
   * @return true if this entry is for a non-detag view, as opposed to an external type or table
   *         function or detag view.
   */
  public abstract boolean getIsView();

  /**
   * @return true if this entry is for an external type, as opposed to a view or a table function.
   */
  public abstract boolean getIsExternal();

  /**
   * @return true if this entry is for a detag statement, as opposed to a non-detag view or an
   *         external type, or a table function
   */
  public abstract boolean getIsDetag();

  /**
   * Override this method to return column names for external types.
   * 
   * @return names of output columns in this view
   * @throws ParseException if the column names aren't found
   */
  public abstract ArrayList<String> getColNames() throws ParseException;

  /**
   * @param colName name of an output column
   * @return true if the object represented by this catalog entry has an output column by the
   *         indicated name.
   */
  public boolean hasColName(String colName) throws ParseException {

    for (String name : getColNames()) {
      if (name.equals(colName)) {
        return true;
      }
    }

    return false;
  }

  /**
   * @param importNode the import parse tree node corresponding to the catalog entry
   */
  public void setImportingNode(ImportNode importNode) {
    this.importingNode = importNode;
  }

  /**
   * @return the isImported
   */
  public boolean isImported() {
    return importingNode != null;
  }

  /**
   * @return the import parse tree node corresponding to the catalog entry
   */
  public ImportNode getImportingNode() {
    return importingNode;
  }

  /**
   * @return error location information
   */
  public final ErrorLocation getErrorLoc() {
    AQLParseTreeNode node = getNode();
    if (node != null) {
      return node.getErrorLoc();
    } else if (importingNode != null) {
      return importingNode.getErrorLoc();
    }
    return null;
  }

  /**
   * @return the AQL parse tree node that 'defined' the element. <code>null</code> for imported
   *         elements.
   */
  protected abstract AQLParseTreeNode getNode();

}
