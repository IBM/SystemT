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
import com.ibm.avatar.algebra.function.scalar.IntConst;
import com.ibm.avatar.aog.ConstFuncNode;
import com.ibm.avatar.aql.catalog.Catalog;

/** Parse tree node for an integer literal. */
public class IntNode extends ConstValueNode {

  private static final String INT_LITERAL = "IntLiteral";

  private final int value;

  public IntNode(int value, String containingFileName, Token origTok) {
    // set error location info
    super(INT_LITERAL, containingFileName, origTok);
    this.value = value;
  }

  /**
   * Internal method for creating "artificial" IntNodes to fill in blanks in the parse tree.
   */
  protected static IntNode makeConst(int value) {
    return new IntNode(value, null, null);
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    printIndent(stream, indent);
    stream.printf("%d", value);
  }

  public int getValue() {
    return value;
  }

  @Override
  public int reallyCompareTo(AQLParseTreeNode o) {
    IntNode other = (IntNode) o;
    return value - other.value;
  }

  @Override
  public String toString() {
    return String.valueOf(value);
  }

  /*
   * @Override public void toAOG(PrintStream stream, int indent) { printIndent(stream, indent);
   * stream.printf("%d", value); }
   */

  @Override
  public Object toAOGNode(Catalog catalog) {
    // AOG uses Java types for the parse tree nodes of integers.
    // return Integer.valueOf(value);

    return new ConstFuncNode.Int(value);
  }

  @Override
  public ScalarFnCallNode asFunction() throws ParseException {
    // return new FunctionNode(IntConst.FNAME, value, catalog);

    // We generate a special kind of parse tree node to avoid infinite
    // recursion when generating AOG.
    return new ConstAOGFunctNode(ScalarFunc.computeFuncName(IntConst.class), value);
  }

  @Override
  public void qualifyReferences(Catalog catalog) {
    // No action

  }

  @Override
  public FieldType getType(Catalog c, AbstractTupleSchema schema) {
    return FieldType.INT_TYPE;
  }

}
