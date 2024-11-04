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

import com.ibm.avatar.algebra.function.base.AggFunc;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.aql.AQLParseTreeNode;
import com.ibm.avatar.aql.ParseException;

/**
 * Catalog entry for an aggregate function.
 * 
 */
public class AggFuncCatalogEntry extends AbstractFuncCatalogEntry {

  /**
   * Class that implements this scalar function.
   */
  private Class<? extends AggFunc> implClass;

  /**
   * Main constructor for built-in scalar functions. Reads information about the function from the
   * fields of its implementing class. These methods must have the following static final fields:
   * <ul>
   * <li>FNAME: the AQL name of the function
   * </ul>
   * The following static fields are optional:
   * <ul>
   * <li>ISCONST: true if this function is one of the special built-in constant functions, such as
   * IntConst().
   * </ul>
   * 
   * @param implClass the class that implements the function
   */
  protected AggFuncCatalogEntry(Class<? extends AggFunc> implClass) {
    super(ScalarFunc.computeFuncName(implClass));

    this.implClass = implClass;
  }

  @Override
  public boolean getIsExternal() {
    // TODO: Change this method when UDFs are implemented.
    return false;
  }

  @Override
  public boolean getIsView() {
    return false;
  }

  @Override
  public boolean getIsDetag() {
    return false;
  }

  @Override
  public ArrayList<String> getColNames() throws ParseException {
    // Column names don't make sense for an aggregate function.
    throw new UnsupportedOperationException("This method not implemented");
  }

  public Class<? extends AggFunc> getImplClass() {
    return implClass;
  }

  @Override
  protected AQLParseTreeNode getNode() {
    // return null as there is no parse tree node associated with built-in functions
    return null;
  }
}
