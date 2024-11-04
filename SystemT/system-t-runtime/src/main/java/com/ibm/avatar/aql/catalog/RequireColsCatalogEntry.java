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
import com.ibm.avatar.aql.NickNode;
import com.ibm.avatar.aql.RequireColumnsNode;

/**
 * Catalog entry for a require statement in AQL. Currently these are created with the "require
 * document with columns" statement.
 */
public class RequireColsCatalogEntry extends CatalogEntry {

  /**
   * Parse tree node for the table declaration.
   */
  private RequireColumnsNode node = null;

  /** Constructor for the entry for a lookup table. */
  public RequireColsCatalogEntry(RequireColumnsNode node) {
    super(node.getContainingFileName());
    this.node = node;
  }

  /**
   * Override this method to return column names for the special document input view
   * 
   * @return names of output columns in input view
   */
  @Override
  public ArrayList<String> getColNames() {
    ArrayList<String> ret = new ArrayList<String>();
    for (NickNode nick : node.getColNames()) {
      ret.add(nick.getNickname());
    }
    return ret;
  }

  /**
   * Add new method to return column types for the special document input view
   * 
   * @return types of output columns in input view
   */
  public ArrayList<String> getColTypes() {
    ArrayList<String> ret = new ArrayList<String>();
    for (NickNode nick : node.getColTypes()) {
      ret.add(nick.getNickname());
    }
    return ret;
  }

  @Override
  public boolean getIsExternal() {
    return false;
  }

  @Override
  public boolean getIsDetag() {
    return false;
  }

  @Override
  public boolean getIsView() {
    return false;
  }

  public RequireColumnsNode getParseTreeNode() {
    return node;
  }

  @Override
  protected AQLParseTreeNode getNode() {
    return node;
  }
}
