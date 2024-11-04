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

import java.lang.reflect.Field;
import java.util.ArrayList;

import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.aql.AQLParseTreeNode;
import com.ibm.avatar.aql.ParseException;

/**
 * Catalog entry for a scalar function.
 * 
 */
public class ScalarFuncCatalogEntry extends AbstractFuncCatalogEntry {

  /**
   * Flag that is set to TRUE for special built-in constant functions like IntConst()
   */
  private boolean isConstFn;

  /**
   * Class that implements this scalar function.
   */
  private Class<? extends ScalarFunc> implClass;

  /**
   * Fully-qualified name of the function. Note that this name may not be a valid name in the
   * context of the current module if the function was imported under an alias.
   */
  private String fqFuncName;

  /**
   * Main constructor for built-in scalar functions. Reads information about the function from the
   * fields of its implementing class. The following static fields are optional:
   * <ul>
   * <li>ISCONST: true if this function is one of the special built-in constant functions, such as
   * IntConst().
   * </ul>
   * 
   * @param implClass the class that implements the function
   */
  protected ScalarFuncCatalogEntry(Class<? extends ScalarFunc> implClass) {
    this(implClass, ScalarFunc.computeFuncName(implClass), ScalarFunc.computeFuncName(implClass));
  }

  /**
   * Special constructor for when the AQL function name cannot be computed from the class (i.e.
   * user-defined functions)
   * 
   * @param implClass information about the implementing function class
   * @param aqlFuncName name of the function from the perspective of AQL code
   * @param fqFuncName fully-qualified name of the function (if the function is a user-defined
   *        function)
   */
  protected ScalarFuncCatalogEntry(Class<? extends ScalarFunc> implClass, String aqlFuncName,
      String fqFuncName) {
    super(aqlFuncName);

    this.fqFuncName = fqFuncName;

    // Fill in additional, optional fields by reflection.
    try {
      try {
        Field isConst = implClass.getField("ISCONST");
        this.isConstFn = isConst.getBoolean(null);
      } catch (NoSuchFieldException e) {
        // No ISCONST field; assume the function is not a constant
        // function.
        this.isConstFn = false;
      }

    } catch (Exception e) {
      throw new RuntimeException(String.format(
          "Error creating catalog entry for built-in " + "scalar function '%s': %s", getName(), e));
    }

    this.implClass = implClass;
  }

  /**
   * @return true if this function is one of the built-in constant functions like IntConst()
   */
  public boolean isConstFn() {
    return isConstFn;
  }

  @Override
  public boolean getIsExternal() {
    return false;
  }

  /**
   * @return Fully-qualified name of the function. Note that this name may not be a valid name in
   *         the context of the current module if the function was imported under an alias.
   */
  public String getFqFuncName() {
    return fqFuncName;
  }

  @Override
  public ArrayList<String> getColNames() throws ParseException {
    // Column names don't make sense for a scalar function.
    throw new UnsupportedOperationException("This method not implemnted");
  }

  public Class<? extends ScalarFunc> getImplClass() {
    return implClass;
  }

  @Override
  protected AQLParseTreeNode getNode() {
    // no parse tree node associated with this catalog entry
    return null;
  }

}
