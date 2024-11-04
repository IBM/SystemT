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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.TreeSet;

import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.algebra.function.base.ScalarUDF;
import com.ibm.avatar.algebra.function.base.UDFPredicateWrapper;
import com.ibm.avatar.algebra.function.predicate.ContainedWithin;
import com.ibm.avatar.algebra.function.predicate.Contains;
import com.ibm.avatar.algebra.function.predicate.ContainsDict;
import com.ibm.avatar.algebra.function.predicate.ContainsDicts;
import com.ibm.avatar.algebra.function.predicate.Equals;
import com.ibm.avatar.algebra.function.predicate.FollowedBy;
import com.ibm.avatar.algebra.function.predicate.FollowedByTok;
import com.ibm.avatar.algebra.function.predicate.Follows;
import com.ibm.avatar.algebra.function.predicate.FollowsTok;
import com.ibm.avatar.algebra.function.predicate.MatchesDict;
import com.ibm.avatar.algebra.function.predicate.Overlaps;
import com.ibm.avatar.algebra.joinpred.ContainedWithinMP;
import com.ibm.avatar.algebra.joinpred.ContainsMP;
import com.ibm.avatar.algebra.joinpred.EqualsMP;
import com.ibm.avatar.algebra.joinpred.FollowedByMP;
import com.ibm.avatar.algebra.joinpred.FollowedByTokMP;
import com.ibm.avatar.algebra.joinpred.FollowsMP;
import com.ibm.avatar.algebra.joinpred.FollowsTokMP;
import com.ibm.avatar.algebra.joinpred.MergeJoinPred;
import com.ibm.avatar.algebra.joinpred.OverlapsMP;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.aql.RegexNode;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.aql.catalog.ScalarFuncCatalogEntry;

/**
 * AOG parse tree node that represents a scalar function call
 */
public class ScalarFuncNode extends AOGFuncNode {
  /** Function name */
  protected String funcname;

  /** Arguments to function */
  protected ArrayList<Object> args;

  /** Predicate arguments, for dump() */
  // protected Object[] args;
  /** Token containing the function name. */
  protected Token origTok;

  /** Optional canonical name of the function, for AOG generation purposes. */
  private String canonicalName = null;

  /**
   * Table of predicates that have a merge join implementation, along with the classes that provide
   * said implementations.
   */
  private static final Class<?>[][] MERGE_COMPAT_PREDS =
      {{FollowsTok.class, FollowsTokMP.class}, {Follows.class, FollowsMP.class},
          {Contains.class, ContainsMP.class}, {ContainedWithin.class, ContainedWithinMP.class},
          {FollowedByTok.class, FollowedByTokMP.class}, {FollowedBy.class, FollowedByMP.class},
          {Overlaps.class, OverlapsMP.class}, {Equals.class, EqualsMP.class}
      // Don't use merge join for ContainedWithin -- the implementation
      // doesn't scale well on large documents.
      // { ContainedWithin.class, ContainedWithinMP.class }
      };

  /** Optimized version of {@link #MERGE_COMPAT_PREDS} */
  private static final HashMap<String, Class<?>> MERGE_COMPAT_PRED_MAP;
  static {
    MERGE_COMPAT_PRED_MAP = new HashMap<String, Class<?>>();
    for (int i = 0; i < MERGE_COMPAT_PREDS.length; i++) {
      @SuppressWarnings("unchecked")
      String fname = AQLFunc.computeFuncName((Class<? extends AQLFunc>) MERGE_COMPAT_PREDS[i][0]);
      MERGE_COMPAT_PRED_MAP.put(fname, MERGE_COMPAT_PREDS[i][1]);
    }
  }

  /**
   * @param funcName name of a scalar function
   * @return true if the indicated function can be used as the join predicate in a merge join.
   */
  public static boolean hasMergeJoinImpl(String funcName) {
    return MERGE_COMPAT_PRED_MAP.containsKey(funcName);
  }

  /**
   * Table of predicates that have an RSE join implementation. In all cases, the RSE join
   * implementation is built into the predicate itself.
   */
  private static final Class<?>[] RSE_COMPAT_PREDS =
      {FollowsTok.class, Follows.class, FollowedByTok.class, FollowedBy.class};

  /**
   * @param funcName name of a scalar function
   * @return true if the indicated function can be used as the join predicate in an RSE join.
   */
  public static boolean hasRSEJoinImpl(String funcName) {
    for (int i = 0; i < RSE_COMPAT_PREDS.length; i++) {

      @SuppressWarnings("unchecked")
      String predName = AQLFunc.computeFuncName((Class<? extends AQLFunc>) RSE_COMPAT_PREDS[i]);
      if (predName.equals(funcName)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Convenience version of {@link #toFunc(SymbolTable, Catalog)} that casts the value returned to a
   * scalar function. Used in cases where the caller only accepts function calls that return a
   * scalar value.
   */
  public ScalarFunc toScalarFunc(SymbolTable symtab, Catalog catalog) throws ParseException {
    return (ScalarFunc) toFunc(symtab, catalog);
  }

  /**
   * Generate the appropriate function call tree for this node.
   * 
   * @param symtab the AOG parser's symbol table
   * @param catalog the AQL catalog
   * @return runtime function object; will be either ScalarFunc or TableLocator
   */
  public AQLFunc toFunc(SymbolTable symtab, Catalog catalog) throws ParseException {
    if (null == catalog) {
      throw new NullPointerException("Null catalog ptr passed to ScalarFuncNode.toFunc()");
    }

    // Start by recursively converting the arguments.
    AQLFunc[] convertedArgs = new AQLFunc[args.size()];
    for (int i = 0; i < args.size(); i++) {
      Object arg = args.get(i);
      if (arg instanceof ScalarFuncNode) {
        ScalarFuncNode child = (ScalarFuncNode) arg;
        convertedArgs[i] = (child.toFunc(symtab, catalog));
      } else {
        throw new ParseException(String.format(
            "Error converting args of %s: "
                + "%s (type %s) is not a ScalarFuncNode.  Current class is %s",
            this, arg, arg.getClass().getName(), this.getClass().getName()));
      }
    }

    // Look up this function in the catalog.
    ScalarFuncCatalogEntry entry = catalog.lookupScalarFunc(funcname);

    if (null == entry) {
      throw new ParseException(String.format("Don't understand function name '%s'", funcname));
    }

    // Get reflection information from the catalog entry.
    Class<? extends ScalarFunc> implClass = entry.getImplClass();

    {
      // SPECIAL CASE: Certain classes require access to the AOG symbol
      // table; special-case those functions.
      if (ContainsDict.FNAME.equals(funcname)) {
        return new ContainsDict(null, convertedArgs, symtab);
      } else if (ContainsDicts.FNAME.equals(funcname)) {
        return new ContainsDicts(null, convertedArgs, symtab);
      } else if (AQLFunc.computeFuncName(Equals.class).equals(funcname)) {
        return new Equals(null, convertedArgs, symtab, catalog);
      } else if (MatchesDict.FNAME.equals(funcname)) {
        return new MatchesDict(null, convertedArgs, symtab);
      }
      try {
        // check if implClass is a UDFWrapper or UDFPredicateWrapper
        if (implClass.equals(ScalarUDF.class)) {
          return new ScalarUDF(null, convertedArgs, funcname, catalog);
        }

        // SPECIAL CASE: Legacy .tam files from versions of SystemT prior to table function support
        // use "UDFWrapper()"
        // instead of "ScalarUDF". The following code handles these legacy files and can be removed
        // if we stop
        // supporting the older versions of the .tam file format that use them.
        // Code commented out because it didn't actually do anything --Fred
        // if (ScalarUDF.LEGACY_FNAME.equals (fname)) return new ScalarUDF (origTok, convertedArgs,
        // funcname, symtab);
        // END SPECIAL CASE

        else if (implClass.equals(UDFPredicateWrapper.class)) {
          return new UDFPredicateWrapper(null, convertedArgs, funcname, catalog);
        }

      } catch (Exception e) {
        throw new ParseException(
            String.format("Error instantiating scalar function '%s': %s", funcname, e), e);
      }
      // END SPECIAL CASE
    }

    // Figure out how to instantiate the class that implements the function.
    Constructor<? extends ScalarFunc> constructor = null;
    try {
      // The standard constructor takes an AQL Token and an ArrayList as args.
      constructor =
          implClass.getConstructor(com.ibm.avatar.aql.Token.class, convertedArgs.getClass());
      return constructor.newInstance(null, convertedArgs);
    } catch (Exception e) {
      // Quite a few things can go wrong during a reflection call.
      // Tell the user about the bad news.
      // e.printStackTrace ();
      throw new ParseException(
          String.format("Error instantiating scalar function '%s': %s", funcname, e), e);
    }

  }

  //
  // Merge join predicate names
  //

  private static int funcToInt(Object funcObj, SymbolTable symtab, Catalog catalog)
      throws ParseException {
    ScalarFuncNode funcNode = (ScalarFuncNode) funcObj;
    ScalarFunc func = funcNode.toScalarFunc(symtab, catalog);
    return (Integer) func.evaluateConst();
  }

  /**
   * Generate the appropriate <b>merge join</b> predicate for this function, if applicable.
   */
  public MergeJoinPred toMergePred(SymbolTable symtab, Catalog catalog) throws ParseException {
    // Look through the lookup table to find the appropriate implementation class.
    Class<?> implClass = MERGE_COMPAT_PRED_MAP.get(funcname);

    if (null == implClass) {
      throw new FatalInternalError("Error looking up merge implementation for %s predicate",
          funcname);
    }

    // The function should have a constructor that takes arguments in the same order they appear in
    // the args list.
    Constructor<?>[] constructors = implClass.getConstructors();

    if (1 != constructors.length) {
      throw new FatalInternalError("Class %s has more than one constructor; "
          + "merge join predicates should only have one constructor", implClass.getName());
    }

    Constructor<?> constructor = constructors[0];

    // By convention, merge join predicates take either scalar function or integer constant
    // arguments.
    Class<?>[] constructorParams = constructor.getParameterTypes();

    if (constructorParams.length != args.size()) {
      throw new FatalInternalError(
          "Received %d arguments for %s predicate (%s), "
              + "but constructor for corresponding merge join predicate %s expects %d arguments.",
          args.size(), funcname, args, implClass.getName(), constructorParams.length);
    }

    Object[] constructorArgs = new Object[constructorParams.length];
    for (int i = 0; i < constructorArgs.length; i++) {
      if (constructorParams[i].equals(ScalarFunc.class)) {
        // Argument needs to be an instance of ScalarFunc
        ScalarFuncNode argNode = (ScalarFuncNode) args.get(i);
        constructorArgs[i] = argNode.toFunc(symtab, catalog);
        // ScalarFunc.toScalarFunc (args.get (i), symtab, catalog);
      } else if (constructorParams[i].equals(int.class)
          || constructorParams[i].equals(Integer.class)) {
        // Argument is an integer
        constructorArgs[i] = funcToInt(args.get(i), symtab, catalog);
      } else {
        throw new FatalInternalError(
            "Don't know how to convert argument %d of constructor for %s (%s)" + " to type %s", i,
            implClass.getName(), args.get(i), constructorParams[i].getName());
      }
    }

    try {
      return (MergeJoinPred) constructor.newInstance(constructorArgs);
    } catch (Exception e) {
      ArrayList<String> typeNames = new ArrayList<String>();
      for (Object arg : constructorArgs) {
        typeNames.add(arg.getClass().getName());
      }
      throw new FatalInternalError(e,
          "Error instantiating merge join implementation "
              + "for %s predicate with arguments %s of types %s (expected types %s)",
          funcname, Arrays.toString(constructorArgs), typeNames,
          Arrays.toString(constructorParams));
    }
  }

  public ScalarFuncNode(Token origTok, ArrayList<Object> args) {
    this.funcname = origTok.image;
    this.origTok = origTok;
    this.args = args;
  }

  // public ScalarFuncNode(String funcname, ArrayList<Object> args) {
  // this.funcname = funcname;
  // this.origTok = null;
  // this.args = args;
  // }

  /**
   * Constructor for creating function nodes from whole cloth, as opposed to parsing them out of AQL
   * or AOG.
   */
  public ScalarFuncNode(String funcname, Object[] args) {
    this.funcname = funcname;

    // System.err.printf ("ScalarFuncNode(%s, %s)\n", funcname, Arrays.toString (args));
    // if ("IntConst".equals (funcname)) {
    // (new Throwable()).printStackTrace ();
    // }

    // No original token, since the node was created "by hand"
    this.origTok = null;
    this.args = new ArrayList<Object>();
    for (Object arg : args) {
      this.args.add(arg);
    }
  }

  /** Single-argument constructor for internal use. */
  // public ScalarFuncNode(String funcname, Object arg) {
  // this.funcname = funcname;
  // this.origTok = null;
  // this.args = new ArrayList<Object>();
  // this.args.add(arg);
  // }

  /**
   * Recursively pretty-print the node. Note that this method is used during AOG generation.
   * 
   * @throws ParseException
   */
  @Override
  public int dump(PrintWriter stream, int indent) throws ParseException {
    // Dump the canonical name, if present, so that the resulting AOG will link properly.
    String funcNameToUse = (null != canonicalName) ? canonicalName : funcname;

    // System.err.printf("ScalarFuncNode: Dumping %s\n", this);

    // Then the name of the top-level operator
    AOGOpTree.printIndent(stream, indent);
    if (null == args || 0 == args.size()) {
      // SPECIAL CASE: Zero arguments; no carriage return necessary
      // after opening paren.
      stream.printf("%s()\n", funcNameToUse);
      return 1;
      // END SPECIAL CASE
    } else {
      stream.printf("%s(\n", funcNameToUse);
    }

    // Then the arguments.
    for (int i = 0; i < args.size(); i++) {
      Object next = args.get(i);

      // System.err.printf("==> Arg %d is %s\n", i, next);

      if (next instanceof String) {
        // Non-recursive argument
        AOGOpTree.printIndent(stream, indent + 1);
        stream.print(StringUtils.quoteStr('"', (String) next, true, true));
        // stream.print("\"" + String.valueOf(next) + "\"");

      } else if (next instanceof Integer) {
        AOGOpTree.printIndent(stream, indent + 1);
        stream.print(String.valueOf(next));
      } else if (next instanceof Float) {
        AOGOpTree.printIndent(stream, indent + 1);
        stream.print(String.valueOf(next));
      } else if (next instanceof Boolean) {
        AOGOpTree.printIndent(stream, indent + 1);
        stream.print(String.valueOf(next));
      } else if (next instanceof ColumnRef) {
        ColumnRef col = (ColumnRef) next;
        AOGOpTree.printIndent(stream, indent + 1);
        stream.print(col.toString() + "\n");
      } else if (next instanceof ScalarFuncNode) {
        // Recursive argument
        ScalarFuncNode subtree = (ScalarFuncNode) next;
        subtree.dump(stream, indent + 1);

      } else if (next instanceof RegexNode) {
        RegexNode regex = (RegexNode) next;
        regex.dump(stream, indent + 1);
      } else {
        throw new AOGConversionException(origTok, "Invalid type " + next.getClass().toString());
      }
      if ((args.size() - 1) != i) {
        stream.print(',');
      }
      stream.print('\n');

    }

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
    for (int i = 0; i < args.size(); i++) {
      sb.append(args.get(i).toString());
      if (i < args.size() - 1) {
        sb.append(", ");
      }
    }
    sb.append(")");
    return sb.toString();
  }

  /**
   * @param canonicalName global name of the function for the purposes of generated AOG
   */
  public void setCanonicalName(String canonicalName) {
    this.canonicalName = canonicalName;
  }

  /**
   * Recursive method to track down all views/tables that are referenced via locator arguments under
   * this function call tree. The subclasses that implement locator lookup override this method; the
   * main implementation in this class just handles the recursion.
   * 
   * @param locatorInputs set for collecting results of the search
   */
  public void getLocators(TreeSet<AOGOpTree.Nickname> locatorInputs) {
    for (Object argObj : args) {
      if (argObj instanceof ScalarFuncNode) {
        ScalarFuncNode sfnArg = (ScalarFuncNode) argObj;
        sfnArg.getLocators(locatorInputs);
      }
    }
  }
}
