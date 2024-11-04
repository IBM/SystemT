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
import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.algebra.function.scalar.BoolConst;
import com.ibm.avatar.aog.ConstFuncNode;
import com.ibm.avatar.aql.catalog.Catalog;

/** A string literal. */
public class BoolNode extends ConstValueNode {

  private static final String BOOLEAN_LITERAL = "BooleanLiteral";

  boolean value;

  public BoolNode(boolean value, String containingFileName, Token origTok) {
    // set error location info
    super(BOOLEAN_LITERAL, containingFileName, origTok);

    this.value = value;
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    printIndent(stream, indent);
    stream.print(this.toString());
  }

  public boolean getValue() {
    return value;
  }

  @Override
  public int reallyCompareTo(AQLParseTreeNode o) {
    BoolNode other = (BoolNode) o;
    return Boolean.valueOf(value).compareTo(Boolean.valueOf(other.value));
  }

  /*
   * @Override public void toAOG(PrintStream stream, int indent) { printIndent(stream, indent);
   * stream.print(StringUtils.quoteStr('"', str)); }
   */

  @Override
  public Object toAOGNode(Catalog catalog) {
    ConstFuncNode.Bool ret = new ConstFuncNode.Bool(value);

    return ret;
  }

  @Override
  public ScalarFnCallNode asFunction() throws ParseException {
    return new ConstAOGFunctNode(AQLFunc.computeFuncName(BoolConst.class), Boolean.valueOf(value));
  }

  @Override
  public void qualifyReferences(Catalog catalog) {
    // No action

  }

  @Override
  public String toString() {
    return value ? getConst(AQLParserConstants.TRUE) : getConst(AQLParserConstants.FALSE);
  }

  @Override
  public FieldType getType(Catalog c, AbstractTupleSchema schema) {
    return FieldType.BOOL_TYPE;
  }

}
