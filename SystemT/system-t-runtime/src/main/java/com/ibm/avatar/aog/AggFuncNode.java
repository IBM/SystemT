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
import java.lang.reflect.Constructor;

import com.ibm.avatar.algebra.function.base.AggFunc;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.algebra.function.base.ScalarReturningFunc;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.aql.catalog.AggFuncCatalogEntry;
import com.ibm.avatar.aql.catalog.Catalog;

/**
 * Parse tree node that represents an aggregate function call
 */
public class AggFuncNode extends AOGFuncNode {
  /** Function name */
  protected String funcname;

  /** Argument to function */
  protected Object arg;

  /** Token containing the function name. */
  protected Token origTok;

  /** Flag that is set to true if this item is the aggregate function COUNT(*) */
  protected boolean isCountStar = false;

  /**
   * Generate the appropriate function call tree for this node.
   * 
   * @param symtab AOG parser symbol table
   * @param catalog AQL catalog
   * @throws com.ibm.avatar.aog.ParseException
   */
  public AggFunc toFunc(SymbolTable symtab, Catalog catalog) throws ParseException {

    // Start by recursively converting the arguments.
    // Currently all aggregates take only scalar values as arguments.
    ScalarReturningFunc convertedArg = null;

    // SPECIAL CASE: for COUNT(*)
    if (getIsCountStar()) {
      // no argument to convert
    } else if (arg instanceof ScalarFuncNode) {
      ScalarFuncNode child = (ScalarFuncNode) arg;
      convertedArg = (ScalarReturningFunc) (child.toFunc(symtab, catalog));
    } else {
      throw new ParseException(String.format("%s is not a ScalarFuncNode", arg));
    }

    // Look up this function in the catalog.
    AggFuncCatalogEntry entry = catalog.lookupAggFunc(funcname);

    if (null == entry) {
      throw new ParseException(String.format("Don't understand function name '%s'", funcname));
    }

    // Get reflection information from the catalog entry.
    Class<? extends AggFunc> implClass = entry.getImplClass();

    // Figure out how to instantiate the class that implements the function.
    Constructor<? extends AggFunc> constructor = null;
    try {
      // SPECIAL CASE: for COUNT(*)
      if (getIsCountStar()) {
        constructor = implClass.getConstructor(com.ibm.avatar.aql.Token.class);
        return constructor.newInstance((com.ibm.avatar.aql.Token) null);
      } else {
        // The standard constructor takes an AQL parser Token and an Object as argument.
        constructor = implClass.getConstructor(com.ibm.avatar.aql.Token.class, ScalarFunc.class);
        return constructor.newInstance(null, convertedArg);
      }
    } catch (Exception e) {
      // Quite a few things can go wrong during a reflection call.
      // Tell the user about the bad news.
      // e.printStackTrace ();
      throw new ParseException(
          String.format("Error instantiating aggregate function '%s'", funcname), e);
    }

  }

  public AggFuncNode(Token origTok, Object arg) {
    this.funcname = origTok.image;
    this.origTok = origTok;
    this.arg = arg;
  }

  /**
   * Constructor for creating function nodes from whole cloth, as opposed to parsing them out of AQL
   * or AOG.
   */
  public AggFuncNode(String funcname, Object arg) {
    this.funcname = funcname;

    // No original token, since the node was created "by hand"
    this.origTok = null;
    this.arg = arg;
  }

  public void setIsCountStar(boolean isCountStar) {
    this.isCountStar = isCountStar;
  }

  public boolean getIsCountStar() {
    return isCountStar;
  }

  /**
   * Recursively pretty-print the node.
   * 
   * @throws ParseException
   */
  @Override
  public int dump(PrintWriter stream, int indent) throws ParseException {

    // System.err.printf("AggFuncNode: Dumping %s\n", this);

    // Then the name of the top-level operator
    AOGOpTree.printIndent(stream, indent);
    stream.printf("%s(\n", funcname);

    // Then the argument.
    // System.err.printf("==> Arg %d is %s\n", i, next);

    if (getIsCountStar()) {
      AOGOpTree.printIndent(stream, indent + 1);
      stream.print("*");
    } else if (arg instanceof String) {
      // Non-recursive argument
      AOGOpTree.printIndent(stream, indent + 1);
      stream.print(StringUtils.quoteStr('"', (String) arg, true, true));
      // stream.print("\"" + String.valueOf(next) + "\"");

    } else if (arg instanceof Integer) {
      AOGOpTree.printIndent(stream, indent + 1);
      stream.print(String.valueOf(arg));
    } else if (arg instanceof ColumnRef) {
      ColumnRef col = (ColumnRef) arg;
      AOGOpTree.printIndent(stream, indent + 1);
      stream.print(col.toString() + "\n");
    } else if (arg instanceof ScalarFuncNode) {
      // Recursive argument
      ScalarFuncNode subtree = (ScalarFuncNode) arg;
      subtree.dump(stream, indent + 1);

    } else {
      throw new AOGConversionException(origTok, "Invalid type " + arg.getClass().toString());
    }
    stream.print('\n');

    // Then close the parens.
    AOGOpTree.printIndent(stream, indent);
    stream.print(")");

    // Selection predicates are not operators.
    return 0;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(funcname);
    sb.append("(");
    sb.append(getIsCountStar() ? "*" : arg.toString());
    sb.append(")");
    return sb.toString();
  }
}
