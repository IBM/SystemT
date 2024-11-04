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

import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.TopLevelParseTreeNode;

/**
 * Common superclass for catalog entries that represent objects with a single relational schema.
 * Examples of such objects include views and table functions.
 * 
 */
public abstract class AbstractRelationCatalogEntry extends CatalogEntry {

  /** Main constructor. Just passes information to the superclass. */
  protected AbstractRelationCatalogEntry(String name) {
    super(name);
  }

  /**
   * @return the schema of tuples output by the object that this catalog entry represents.
   * @throws ParseException if the schema cannot be determined
   */
  public abstract TupleSchema getSchema() throws ParseException;

  /**
   * @return parse tree node (in the context of the current module) for the object that this catalog
   *         represents
   */
  public abstract TopLevelParseTreeNode getParseTreeNode();
}
