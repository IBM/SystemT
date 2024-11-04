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
import java.util.TreeSet;

import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.algebra.function.base.TableLocator;
import com.ibm.avatar.aog.AOGOpTree.Nickname;
import com.ibm.avatar.aql.catalog.Catalog;

/**
 * Parse tree node for a table locator argument to a function call. Based on ScalarFuncNode so that
 * scalar functions can take a locator as an argument.
 */
public class TableLocatorNode extends ScalarFuncNode {

  public static final String FUNC_NAME = "TableLocatorNode";

  /** Parse tree node for the actual reference. */
  private Nickname nick;

  /** Name of the view/table to which this locator reference points. */
  private String targetName;

  public TableLocatorNode(AOGOpTree.Nickname nick) {
    super(FUNC_NAME, new Object[0]);
    this.nick = nick;
    this.targetName = nick.getRefNick();

    if (null == this.targetName) {
      throw new NullPointerException("Null target name passed to TableLocatorNode constructor.");
    }
  }

  /**
   * @return name of the target table/view of the locator argument
   */
  public String getTargetName() {
    return targetName;
  }

  @Override
  public int dump(PrintWriter stream, int indent) throws ParseException {
    return nick.dump(stream, indent);
  }

  /**
   * We override the superclass's functionality here, since we don't want to go looking for a scalar
   * function in the catalog.
   */
  @Override
  public AQLFunc toFunc(SymbolTable symtab, Catalog catalog) throws ParseException {
    return new TableLocator(getTargetName());
  }

  @Override
  public void getLocators(TreeSet<AOGOpTree.Nickname> locatorInputs) {
    locatorInputs.add(nick);
  }
}
