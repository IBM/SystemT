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
package com.ibm.avatar.aog;

import java.io.PrintWriter;

public abstract class AOGParseTreeNode {

  /**
   * Name of the module that the parse tree node belongs to
   */
  protected String moduleName = null;

  /**
   * Constructor that associates the parse tree node with a module.
   * 
   * @param moduleName Name of the module the parse tree node belongs to
   */
  protected AOGParseTreeNode(String moduleName) {
    setModuleName(moduleName);
  }

  /**
   * Recursively pretty-print the tree's contents to a stream.
   * 
   * @return number of operators printed
   * @throws ParseException
   */
  public abstract int dump(PrintWriter stream, int indent) throws ParseException;

  /**
   * Returns the name of the module that the parse tree node belongs to
   * 
   * @return moduleName of the parse tree node
   */
  public String getModuleName() {
    return moduleName;
  }

  /**
   * Sets the module name that the parse tree node belongs to
   * 
   * @param moduleName name of the module that the parse tree node belongs to
   */
  public void setModuleName(String moduleName) {
    this.moduleName = moduleName;
  }

}
