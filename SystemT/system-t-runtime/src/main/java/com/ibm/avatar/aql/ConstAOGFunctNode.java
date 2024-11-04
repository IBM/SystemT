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

import java.util.ArrayList;
import java.util.List;

import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.algebra.function.scalar.BoolConst;
import com.ibm.avatar.algebra.function.scalar.FloatConst;
import com.ibm.avatar.algebra.function.scalar.GetCol;
import com.ibm.avatar.algebra.function.scalar.IntConst;
import com.ibm.avatar.algebra.function.scalar.StringConst;
import com.ibm.avatar.aog.AOGFuncNode;
import com.ibm.avatar.aog.ConstFuncNode;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.aql.catalog.Catalog;

/**
 * AQL parse tree node to represent a built-in constant scalar function. This parse tree node is
 * <b>never</b> generated directly by the parser; it's only created while converting constants into
 * function calls.
 */
public class ConstAOGFunctNode extends ScalarFnCallNode {

  Object arg;

  public ConstAOGFunctNode(String fname, Object arg) throws ParseException {
    super(new NickNode(fname), new ArrayList<RValueNode>());
    this.arg = arg;
  }

  @Override
  public List<ParseException> validate(Catalog catalog) {
    List<ParseException> errors = new ArrayList<ParseException>();

    List<ParseException> parentErrors = super.validate(catalog);
    if (null != parentErrors && parentErrors.size() > 0)
      errors.addAll(parentErrors);

    return errors;
  }

  /**
   * This method is the reason why we need to create a special-case parse tree node for column
   * references.
   */
  @Override
  public AOGFuncNode toAOGNode(Catalog catalog) {
    String fname = getFuncName();

    // Call the appropriate AOG parse tree node constructor
    if (AQLFunc.computeFuncName(BoolConst.class).equals(fname)) {
      return new ConstFuncNode.Bool((Boolean) arg);
    } else if (AQLFunc.computeFuncName(FloatConst.class).equals(fname)) {
      return new ConstFuncNode.Flt((Float) arg);
    } else if (AQLFunc.computeFuncName(GetCol.class).equals(fname)) {
      return new ConstFuncNode.Col((String) arg);
    } else if (AQLFunc.computeFuncName(IntConst.class).equals(fname)) {
      return new ConstFuncNode.Int((Integer) arg);
    } else if (AQLFunc.computeFuncName(StringConst.class).equals(fname)) {
      return new ConstFuncNode.Str((String) arg);
    } else {
      throw new FatalInternalError("Don't know how to convert %s to ConstFuncNode", fname);
    }

    // Old code worked for AOG generation but not for type inference:
    // return new ScalarFuncNode (getFuncName (), new Object[] { arg });
  }

  /*
   * @Override public void dump (PrintWriter stream, int indent) throws ParseException { printIndent
   * (stream, indent); stream.print (getFuncName ()); stream.print ("(\n"); printIndent (stream,
   * indent + 1); stream.print (arg); stream.print ("\n"); printIndent (stream, indent);
   * stream.print (")"); }
   */
}
