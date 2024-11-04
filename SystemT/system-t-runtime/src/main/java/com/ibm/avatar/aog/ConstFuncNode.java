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

import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.algebra.function.scalar.BoolConst;
import com.ibm.avatar.algebra.function.scalar.FloatConst;
import com.ibm.avatar.algebra.function.scalar.GetCol;
import com.ibm.avatar.algebra.function.scalar.IntConst;
import com.ibm.avatar.algebra.function.scalar.RegexConst;
import com.ibm.avatar.algebra.function.scalar.StringConst;
import com.ibm.avatar.aql.RegexNode;
import com.ibm.avatar.aql.catalog.Catalog;

/**
 * AOG parse tree nodes for special built-in scalar functions that serve as placeholders for
 * constant values.
 */
public abstract class ConstFuncNode extends ScalarFuncNode {

  protected ConstFuncNode(String funcname, Object arg) {
    super(funcname, new Object[] {arg});
  }

  protected ConstFuncNode(String funcname, Object[] args) {
    super(funcname, args);
  }

  /** Force inner classes to override this method. */
  @Override
  public abstract ScalarFunc toFunc(SymbolTable symtab, Catalog catalog) throws ParseException;

  /** Function-call wrapper for an integer constant. */
  public static class Int extends ConstFuncNode {
    public Int(int val) {
      super(IntConst.class.getSimpleName(), val);
    }

    @Override
    public ScalarFunc toFunc(SymbolTable symtab, Catalog catalog) throws ParseException {
      return new IntConst(null, args.get(0));
    }

  }

  /** Function-call wrapper for a float constant. */
  public static class Flt extends ConstFuncNode {
    public Flt(float val) {
      super(FloatConst.class.getSimpleName(), val);
    }

    @Override
    public ScalarFunc toFunc(SymbolTable symtab, Catalog catalog) throws ParseException {
      return new FloatConst(null, args.get(0));
    }

  }

  /** Function-call wrapper for a string constant. */
  public static class Str extends ConstFuncNode {
    public Str(String val) {
      super(StringConst.class.getSimpleName(), val);
    }

    @Override
    public ScalarFunc toFunc(SymbolTable symtab, Catalog catalog) throws ParseException {
      return new StringConst((String) args.get(0));
    }
  }

  /**
   * Function-call wrapper for a regex constant, along with an argument that tells what engine to
   * use..
   */
  public static class Regex extends ConstFuncNode {

    /**
     * This constructor is called from {@link RegexNode#toAOGNode()} and from {@link AOGParser}
     */
    public Regex(RegexNode regex, String engineName) {
      super(RegexConst.FNAME, new Object[] {regex, engineName});
      // super(RegexConst.FNAME, val);
    }

    @Override
    public ScalarFunc toFunc(SymbolTable symtab, Catalog catalog) throws ParseException {
      return new RegexConst((RegexNode) args.get(0), (String) args.get(1));
    }
  }

  /** Function-call wrapper for a column name constant. */
  public static class Col extends ConstFuncNode {
    public Col(String name) {
      super(GetCol.class.getSimpleName(), name);
    }

    @Override
    public ScalarFunc toFunc(SymbolTable symtab, Catalog catalog) throws ParseException {
      return new GetCol((String) args.get(0));
    }
  }

  /** Function-call wrapper for a column name constant. */
  public static class Bool extends ConstFuncNode {
    public Bool(boolean val) {
      super(BoolConst.class.getSimpleName(), val);
    }

    @Override
    public ScalarFunc toFunc(SymbolTable symtab, Catalog catalog) throws ParseException {
      return new BoolConst(args.get(0));
    }
  }
}
