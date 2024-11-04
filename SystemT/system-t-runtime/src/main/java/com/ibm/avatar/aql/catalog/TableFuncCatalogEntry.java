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
import java.util.List;

import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.aql.AQLParseTreeNode;
import com.ibm.avatar.aql.ParseException;

/**
 * Entry in the AQL catalog for a table function (i.e. a function that returns tuples)
 * 
 */
public abstract class TableFuncCatalogEntry extends AbstractFuncCatalogEntry {

  /**
   * Original parse tree node -- can be create function, or import function, or import module
   * statement.
   */
  protected AQLParseTreeNode parseTreeNode = null;

  /**
   * Constructor for use by subclasses
   * 
   * @param name name to associate with this catalog entry; by convention, the function's
   *        unqualified name
   */
  protected TableFuncCatalogEntry(String name) {
    super(name);
  }

  /**
   * @return parse tree node that led to creation of this catalog entry -- can be create function,
   *         or import function, or import module statement.
   */
  public AQLParseTreeNode getParseTreeNode() {
    return parseTreeNode;
  }

  @Override
  public boolean getIsView() {
    return false;
  }

  @Override
  public boolean getIsExternal() {
    return false;
  }

  @Override
  public boolean getIsDetag() {
    return false;
  }

  /**
   * @return the names and types of the arguments that this table function accepts, encoded as a
   *         TupleSchema
   * @throws ParseException if the arguments cannot be determined due to invalid AQL
   */
  public abstract TupleSchema getArgumentSchema() throws ParseException;

  /**
   * @return the schema that this table function is declared (or hard-coded, in the case of a
   *         built-in table function) to return.
   */
  public abstract TupleSchema getReturnedSchema();

  /**
   * Instantiates a copy of the table function's implementing class and validates the declared
   * schema against any validation logic present in the class. Note that this validation is a
   * compile-time check; additional checks may fire at runtime.
   * 
   * @param catalog ptr to AQL catalog for looking up additional metadata required for validation
   * @return a list of any errors encountered during validation
   */
  public abstract List<ParseException> validateReturnedSchema(Catalog catalog);

  @Override
  public ArrayList<String> getColNames() throws ParseException {
    TupleSchema retSchema = getReturnedSchema();
    ArrayList<String> ret = new ArrayList<String>();
    for (int i = 0; i < retSchema.size(); i++) {
      ret.add(retSchema.getFieldNameByIx(i));
    }
    return ret;
  }

}
