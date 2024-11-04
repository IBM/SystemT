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
package com.ibm.avatar.aql;

import java.io.PrintWriter;

import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.algebra.function.scalar.StringConst;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.aog.ConstFuncNode;
import com.ibm.avatar.aql.catalog.Catalog;

/** A string literal. */
public class StringNode extends ConstValueNode {

  private static final String STRING_LITERAL = "StringLiteral";

  String str;

  /** Constructor for an anonymous string node. */
  public StringNode(String str) {
    // set error location info
    super(STRING_LITERAL, null, null);

    this.str = str;
  }

  protected StringNode(String str, String containingFileName, Token origTok) {
    // set error location info
    super(STRING_LITERAL, containingFileName, origTok);

    this.str = str;
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    printIndent(stream, indent);
    stream.print(StringUtils.quoteStr('\'', str, true, true));
  }

  public String getStr() {
    return str;
  }

  @Deprecated
  public String getOrigFileName() {
    return getContainingFileName();
  }

  @Override
  public int reallyCompareTo(AQLParseTreeNode o) {
    StringNode other = (StringNode) o;
    return str.compareTo(other.str);
  }

  /*
   * @Override public void toAOG(PrintStream stream, int indent) { printIndent(stream, indent);
   * stream.print(StringUtils.quoteStr('"', str)); }
   */

  @Override
  public Object toAOGNode(Catalog catalog) {
    // AOG uses Java strings as parse tree nodes for strings.
    // return getStr();

    ConstFuncNode.Str ret = new ConstFuncNode.Str(str);

    // Log.debug("Returning %s", ret);

    return ret;
  }

  @Override
  public ScalarFnCallNode asFunction() throws ParseException {
    // return new FunctionNode(StringConst.FNAME, this, catalog);
    return new ConstAOGFunctNode(ScalarFunc.computeFuncName(StringConst.class), str);
  }

  @Override
  public void qualifyReferences(Catalog catalog) {
    // No action

  }

  @Override
  public String toString() {
    return "'" + str + "'";
  }

  @Override
  public FieldType getType(Catalog c, AbstractTupleSchema schema) {
    return FieldType.TEXT_TYPE;
  }

}
