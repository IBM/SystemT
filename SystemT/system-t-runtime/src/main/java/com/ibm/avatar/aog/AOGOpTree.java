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
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeSet;

import com.ibm.avatar.algebra.aggregate.BlockChar;
import com.ibm.avatar.algebra.aggregate.BlockTok;
import com.ibm.avatar.algebra.aggregate.Group;
import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.base.Tee;
import com.ibm.avatar.algebra.base.Tee.TeeOutput;
import com.ibm.avatar.algebra.consolidate.Consolidate;
import com.ibm.avatar.algebra.datamodel.ExternalView;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.algebra.datamodel.Table;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.extract.ApplyScalarFunc;
import com.ibm.avatar.algebra.extract.ApplyTableFunc;
import com.ibm.avatar.algebra.extract.Dictionary;
import com.ibm.avatar.algebra.extract.PartOfSpeech;
import com.ibm.avatar.algebra.extract.RegexTok;
import com.ibm.avatar.algebra.extract.RegularExpression;
import com.ibm.avatar.algebra.extract.Split;
import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.algebra.function.base.AggFunc;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.algebra.function.base.SelectionPredicate;
import com.ibm.avatar.algebra.function.base.TableLocator;
import com.ibm.avatar.algebra.function.base.TableReturningFunc;
import com.ibm.avatar.algebra.function.base.TableUDF;
import com.ibm.avatar.algebra.function.predicate.RSEJoinPred;
import com.ibm.avatar.algebra.join.AdjacentJoin;
import com.ibm.avatar.algebra.join.HashJoin;
import com.ibm.avatar.algebra.join.NLJoin;
import com.ibm.avatar.algebra.join.RSEJoin;
import com.ibm.avatar.algebra.join.SortMergeJoin;
import com.ibm.avatar.algebra.relational.CartesianProduct;
import com.ibm.avatar.algebra.relational.Difference;
import com.ibm.avatar.algebra.relational.Limit;
import com.ibm.avatar.algebra.relational.Project;
import com.ibm.avatar.algebra.relational.Select;
import com.ibm.avatar.algebra.relational.Sort;
import com.ibm.avatar.algebra.relational.Union;
import com.ibm.avatar.algebra.scan.DocScan;
import com.ibm.avatar.algebra.scan.ExternalViewScan;
import com.ibm.avatar.algebra.scan.TableScan;
import com.ibm.avatar.algebra.util.data.StringPairList;
import com.ibm.avatar.algebra.util.dict.CompiledDictionary;
import com.ibm.avatar.algebra.util.dict.DictParams.CaseSensitivityType;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.aql.catalog.TableFuncCatalogEntry;
import com.ibm.avatar.aql.catalog.TableUDFCatalogEntry;
import com.ibm.avatar.aql.tam.ModuleUtils;
import com.ibm.systemt.regex.api.JavaRegex;
import com.ibm.systemt.regex.api.Regex;
import com.ibm.systemt.regex.api.SimpleRegex;
import com.ibm.systemt.util.regex.FlagsString;

/**
 * Parse tree node for an operator tree expression in AOG.
 * 
 */
@SuppressWarnings("deprecation")
public abstract class AOGOpTree extends AOGParseTreeNode {

  /** The name of the operator at the top of this tree. */
  private final String opname;

  /** Nickname for the entire tree; only used when this node is the root. */
  protected String entireTreeNick = null;

  /**
   * The original parser token that led to the creation of this tree
   */
  protected Token origTok = null;

  /**
   * Arguments of the top-level operator in the tree. Each element of this array is either a String,
   * an Integer, or another AOGOpTree
   */
  Object[] args;

  /**
   * Constructor used by the AOG parser.
   * 
   * @param token the parser token for the operator name.
   */
  public AOGOpTree(String moduleName, Token token) {
    super(moduleName);
    this.opname = token.image;
    this.origTok = token.copy();
  }

  /**
   * Constructor for special internal "operators" like DocScan.
   */
  protected AOGOpTree(String moduleName, String opname) {
    super(moduleName);
    this.opname = opname;
    this.origTok = null;
  }

  public void setNickname(String nickname) {
    entireTreeNick = nickname;
  }

  public String getOpname() {
    return opname;
  }

  public String getNickname() {
    return entireTreeNick;
  }

  /**
   * Child classes should override this method as necessary.
   * 
   * @return the number of physical operators that this parse tree node represents.
   */
  protected int numOperatorsRepresented() {
    return 1;
  }

  @Override
  public int dump(PrintWriter stream, int indent) throws ParseException {

    // First the nickname, if applicable.
    if (null != entireTreeNick) {
      printIndent(stream, indent);

      stream.printf("%s =\n", StringUtils.toAOGNick(entireTreeNick));
      indent++;
    }

    // Then the name of the top-level operator
    printIndent(stream, indent);
    if (null == args || 0 == args.length) {
      // SPECIAL CASE: Zero arguments; no carriage return necessary
      // after opening paren.
      stream.printf("%s()\n", opname);
      return numOperatorsRepresented();
      // END SPECIAL CASE
    } else {
      stream.printf("%s(\n", opname);
    }

    // Then the arguments.
    int numobjs = dumpObjs(stream, indent, args, origTok);

    printIndent(stream, indent);
    if (null == entireTreeNick) {
      stream.print(")");
    } else {
      stream.print(");\n");
    }

    return numobjs + numOperatorsRepresented();
  }

  // Internal implementation of dump for basic types.
  @SuppressWarnings("unchecked")
  private static int dumpObj(PrintWriter stream, int indent, Object next, Token origTok)
      throws ParseException {
    int numobjs = 0;

    if (next instanceof String) {
      // Non-recursive argument
      printIndent(stream, indent + 1);
      stream.print(String.valueOf(next));
    } else if (next instanceof Integer || next instanceof Float || next instanceof ColumnRef) {
      // Non-recursive argument
      printIndent(stream, indent + 1);
      stream.print(String.valueOf(next));
    } else if (next instanceof ArrayList) {
      // Argument is actually several arguments packaged in a list.
      ArrayList<Object> elems = (ArrayList<Object>) next;
      Object[] elemsAsArray = elems.toArray();
      printIndent(stream, indent + 1);
      stream.print("(\n");
      numobjs += dumpObjs(stream, indent, elemsAsArray, origTok);
      stream.print("\n");
      printIndent(stream, indent + 1);
      stream.print(")");
    } else if (next instanceof AOGOpTree) {
      // Recursive argument
      AOGOpTree subtree = (AOGOpTree) next;
      numobjs += subtree.dump(stream, indent + 1);

    } else if (next instanceof ScalarFuncNode) {
      // Recursive argument
      ScalarFuncNode subtree = (ScalarFuncNode) next;
      numobjs += subtree.dump(stream, indent + 1);
    } else if (next instanceof AggFuncNode) {
      // Recursive argument
      AggFuncNode subtree = (AggFuncNode) next;
      numobjs += subtree.dump(stream, indent + 1);
    } else if (next instanceof Map) {
      // Map gets printed out in perl hash syntax.
      Map<Object, Object> m = (Map<Object, Object>) next;
      printIndent(stream, indent + 1);
      stream.printf("(\n");
      Object[] keys = m.keySet().toArray();
      for (int j = 0; j < keys.length; j++) {
        Object key = keys[j];
        Object val = m.get(key);

        dumpObj(stream, indent + 2, key, origTok);
        printIndent(stream, indent + 2);
        stream.print("=>");

        // SPECIAL CASE: Add quotes to strings.
        if (val instanceof String)
          val = "\"" + val.toString() + "\"";
        // END SPECIAL CASE

        dumpObj(stream, indent + 3, val, origTok);

        // No comma after last entry.
        if (j < keys.length - 1) {
          stream.print(",\n");
        } else {
          // No newline either
        }
      }
      printIndent(stream, indent + 1);
      stream.printf(")");
    } else if (next instanceof Pair) {

      Pair<Object, Object> p = (Pair<Object, Object>) next;

      Object first = p.first;
      Object second = p.second;

      // SPECIAL CASE: Add quotes to strings in the context of a pair.
      if (first instanceof String)
        first = "\"" + first.toString() + "\"";
      if (second instanceof String)
        second = "\"" + second.toString() + "\"";
      // END SPECIAL CASE

      // Assume that a Pair is a single mapping.
      printIndent(stream, indent + 1);
      // stream.printf("(");
      dumpObj(stream, 0, first, origTok);
      // printIndent(stream, indent + 2);
      stream.print(" => ");
      dumpObj(stream, 0, second, origTok);
      // printIndent(stream, indent + 1);
      // stream.printf(")");
    } else {
      throw new AOGConversionException(origTok, "Invalid type " + next.getClass().toString());
    }

    return numobjs;
  }

  // Internal method to dump an array of arguments.

  private static int dumpObjs(PrintWriter stream, int indent, Object[] objs, Token origTok)
      throws ParseException {

    int numobjs = 0;
    for (int i = 0; i < objs.length; i++) {
      Object next = objs[i];
      // in the case of the consolidate operator two entries should be ignored if
      // the first one is null
      if (next != null) {
        numobjs += dumpObj(stream, indent, next, origTok);

        if ((objs.length - 1) != i) {
          stream.print(',');
        }
        stream.print('\n');
      }

    }

    return numobjs;
  }

  public static void printIndent(PrintWriter stream, int indent) {
    for (int i = 0; i < indent; i++) {
      stream.print("  ");
    }
  }

  /**
   * Determine what other AOGOpTrees this tree reads inputs from. The AOGOpTree class's version of
   * this function does the tree traversal; the actual dependencies are added by the implementation
   * in the Nickname class.
   * 
   * @param symtab map from string nicknames to parse trees
   * @param deps will become a list of dependencies, where a given input appears once for each time
   *        it is used as an input.
   * @throws ParseException if something goes awry with the dependency generation
   */
  public void getDeps(SymbolTable symtab, ArrayList<AOGOpTree> deps) throws ParseException {

    // SPECIAL CASE: No arguments.
    if (null == args) {
      return;
    }
    // END SPECIAL CASE

    for (Object arg : args) {

      if (null == arg) {
        // Null arguments can have no dependencies
      } else if (arg instanceof Integer || arg instanceof String || arg instanceof ScalarFuncNode
          || arg instanceof ColumnRef || arg instanceof Map<?, ?> || arg instanceof ArrayList<?>) {
        // No nicknames here!

      } else if (arg instanceof AOGOpTree) {
        // Recursive case: Argument is an inline subtree; traverse the
        // subtree. (base case is in Nickname class)
        AOGOpTree subtree = (AOGOpTree) arg;
        subtree.getDeps(symtab, deps);
      } else {
        String cause = "Don't understand type " + arg.getClass();
        throw new AOGConversionException(origTok,
            String.format(AOGConversionException.ERROR_INVALID_AOG, moduleName, cause));
      }
    }
  }

  /**
   * Find all the annotations that this tree of operators scans.
   * 
   * @param annots will become a list of precomputed annotations that this tree needs to access.
   */
  /*
   * public void getAnnotDeps(ArrayList<String> annots) throws AOGConversionException { // SPECIAL
   * CASE: No arguments. if (null == args) { return; } // END SPECIAL CASE for (Object arg : args) {
   * if (arg instanceof ScanOp) { // Base case 1: Argument represents a scan over the annotations //
   * of a particular type. Add a note to <annots>. ScanOp op = (ScanOp) arg;
   * annots.add(op.whatToScan); } else if (arg instanceof Integer || arg instanceof String || arg
   * instanceof ScalarFuncNode || arg instanceof Map || arg instanceof ColumnRef || arg instanceof
   * ArrayList) { // Base case 2: No recursion necessary. } else if (arg instanceof AOGOpTree) { //
   * Recursive case: Argument is an inline subtree; traverse the // subtree. AOGOpTree subtree =
   * (AOGOpTree) arg; subtree.getAnnotDeps(annots); } else { throw new
   * AOGConversionException(origTok, "Don't understand type " + arg.getClass()); } } }
   */

  /**
   * Instantiate a tree of runtime operators that implement this subtree, and store the tree inside
   * this object. Future calls to getOutput() will use the stored tree. Also labels the created
   * operators with the nickname of the tree.
   * 
   * @param noutput how many outputs this runtime operator tree will produce
   * @param symtab map telling what operator tree each nickname refers to.
   * @param catalog pointer to the AQL catalog, for reading metadata about imported objects and
   *        functions
   * @throws ParseException if something goes wrong with conversion
   */
  public void computeOpTree(int noutput, SymbolTable symtab, Catalog catalog)
      throws ParseException {
    if (-1 != numOutput) {
      // Tree already converted
      throw new AOGConversionException(origTok, "Tried to convert tree twice");
    }

    Operator root = toOpTree(symtab, catalog);

    if (null == root) {
      throw new RuntimeException("toOpTree() returned null");
    }

    // Recursively label the subtree we just created with the appropriate
    // nickname (which corresponds to the AQL view name)
    applyLabel(entireTreeNick, root);

    // Check for schema problems.
    try {
      root.getOutputSchema();
    } catch (IllegalArgumentException e) {
      throw new ParseException(String.format("Error converting tree for nickname '%s': %s",
          entireTreeNick, e.toString()));
    }

    setupOutput(noutput, root);
  }

  /**
   * Recursive subroutine to apply a string label to an operator tree; stops recursing when it
   * reaches a Tee output or something that's already labeled.
   */
  private void applyLabel(String label, Operator root) {
    // BEGIN: Fix for 13079
    // Cause: ExternalViewScan node labeled incorrectly as a normal view
    // Solution: Do not apply label if an ExternalViewScan is already labeled
    if (root instanceof ExternalViewScan && !root.getViewName().equals(Operator.NOT_A_VIEW_NAME)) {
      return;
    }
    // END: Fix for 13079

    root.setViewName(label);

    if (root instanceof TeeOutput) {
      // Apply view label to the tee output too (at least for now),
      // but stop there.
      return;
    }

    for (int i = 0; i < root.getNumInputs(); i++) {
      Operator child = root.getInputOp(i);
      if (Operator.NOT_A_VIEW_NAME == child.getViewName()) {
        applyLabel(label, child);
      }
    }

  }

  /**
   * Put in place the operator tree and its output stage.
   * 
   * @param noutput
   * @param root
   */
  protected void setupOutput(int noutput, Operator root) {
    if (1 == noutput) {
      singleOutput = root;
    } else {
      // We tell the Tee to start out with zero outputs; later on we'll
      // use Tee.getNextOutput() to create new ones on demand.
      multiOutput = new Tee(root, 0);
    }

    numOutput = noutput;
    numOutputUsed = 0;
  }

  /*
   * OPERATOR-SPECIFIC NODES
   */

  /** Parse tree node for a nickname. */
  public static class Nickname extends AOGOpTree implements Comparable<Nickname> {

    private final String nickname;

    // protected Nickname(Token origTok) {
    // super(origTok);
    // this.nickname = origTok.image;
    // }
    public Nickname(String moduleName, String nick) {
      super(moduleName, nick);
      this.nickname = nick;
    }

    @Override
    protected Operator toOpTree(SymbolTable symtab, Catalog catalog) throws ParseException {
      // System.err.printf("Instantiating nickname '%s'\n",
      // getNickname());
      AOGOpTree target = symtab.lookupNick(this.nickname);
      if (null == target) {
        throw new FatalInternalError("Nickname '%s' not found in AOG symbol table", this.nickname);
      }
      return target.getNextOutput();
    }

    /**
     * @return the external nickname that this parse tree node refers to (as opposed to the nickname
     *         of this entire tree)
     */
    public String getRefNick() {
      return this.nickname;
    }

    /** Special dump with no parens. */
    @Override
    public int dump(PrintWriter stream, int indent) throws ParseException {
      // First the entire-tree nickname, if applicable.
      if (null != entireTreeNick) {
        printIndent(stream, indent);
        stream.printf("%s =\n", StringUtils.toAOGNick(entireTreeNick));
        indent++;
      }
      printIndent(stream, indent);
      stream.print(StringUtils.toAOGNick(nickname));
      // stream.print("$" + nickname);

      if (null != entireTreeNick) {
        // Expression in the form $Name = Alias;
        // Print the closing semicolon.
        stream.print(";");
      }

      // Don't count a nickname as an operator.
      return 0;
    }

    /**
     * This method is where the dependency-adding part of getDeps() occurs. As one would expect, a
     * Nickname parse tree node creates a dependency on the corresponding nickname.
     */
    @Override
    public void getDeps(SymbolTable symtab, ArrayList<AOGOpTree> deps) throws ParseException {
      if (symtab.containsNick(nickname)) {
        // This argument is a nickname for another subtree
        AOGOpTree dep = symtab.lookupNick(nickname);
        deps.add(dep);
      } else {
        throw new AOGConversionException(origTok,
            "Don't know how to evaluate nickname " + nickname);
      }
    }

    /**
     * compareTo method so that these objects can be added to a TreeSet. Considers different
     * instances of the name to be equal.
     */
    @Override
    public int compareTo(Nickname o) {
      if (null == o) {
        return -1;
      }

      // Two instances of the same name are considered to be equal.
      return this.nickname.compareTo(o.nickname);
    }
  }

  /** Parse tree node for a Dictionary operator. */
  public static class DictionaryOp extends AOGOpTree {

    /** String to indicate "exact match" semantics */
    public static final String EXACT_MATCH_STR = "Exact";

    /**
     * String to indicate that the instantiated Dictionary operator should use case-insensitive
     * matching.
     */
    public static final String IGNORE_CASE_STR = "IgnoreCase";

    /**
     * String to indicate that the default options of the dictionary tiself should be used to
     * control the match mode.
     */
    public static final String DEFAULT_MATCH_STR = "Default";

    private final String dictFileName;

    private final String matchType;

    private final ColumnRef col;

    private final String outputCol;

    private final AOGOpTree inputTree;

    protected DictionaryOp(String moduleName, Token origTok, String dictFileName, String matchType,
        ColumnRef col, String outputCol, AOGOpTree inputTree) {
      super(moduleName, getConst(AOGParserConstants.DICTIONARY_OPNAME));
      super.origTok = origTok;
      Object[] tmp = {dictFileName, col, outputCol, inputTree};
      super.args = tmp;
      this.dictFileName = dictFileName;
      this.col = col;
      this.outputCol = outputCol;
      this.inputTree = inputTree;
      this.matchType = matchType;
    }

    @Override
    protected Operator toOpTree(SymbolTable symtab, Catalog catalog) throws ParseException {
      Operator input = inputTree.toOpTree(symtab, catalog);

      // Decode the "match type" argument. There are currently only two
      // options.
      CaseSensitivityType caseType;
      try {
        caseType = CaseSensitivityType.decodeStr(null, matchType);
      } catch (FunctionCallValidationException e) {
        throw new ParseException(
            String.format("At line %d of AOG, error decoding match type string '%s'",
                origTok.beginLine, matchType),
            e);
      }

      // Fetch compiled dictionary instance from symbol table
      CompiledDictionary dictFile = symtab.lookupDict(dictFileName);

      return new Dictionary(col.getColName(), outputCol, input, dictFile, caseType, symtab);
    }

  }

  /** Pull a token constant out of the AOGParserConstants data structure. */
  public static String getConst(int index) {
    // Operator names are quoted in the AOGParserConstants data structure.
    String quotedOpName = AOGParserConstants.tokenImage[index];

    // Remove quotes.
    return quotedOpName.substring(1, quotedOpName.length() - 1);
  }

  /** Parse tree node for a Union operator. */
  public static class UnionOp extends AOGOpTree {

    private final ArrayList<AOGOpTree> inputTrees = new ArrayList<AOGOpTree>();

    public UnionOp(String moduleName) {
      super(moduleName, getConst(AOGParserConstants.UNION_OPNAME));
    }

    public void addInput(AOGOpTree input) {
      inputTrees.add(input);

      // WARNING: O(n^2)
      super.args = inputTrees.toArray();
    }

    @Override
    protected Operator toOpTree(SymbolTable symtab, Catalog catalog) throws ParseException {
      Operator[] inputs = new Operator[inputTrees.size()];
      for (int i = 0; i < inputs.length; i++) {
        inputs[i] = inputTrees.get(i).toOpTree(symtab, catalog);
      }

      return new Union(inputs);
    }
  }

  /** Parse tree node for a Regex or FastRegex operator. */
  public static class RegexOp extends AOGOpTree {
    private final boolean fast;

    // NOTE: Yunyao: added 10/03/2007
    // Begin
    // Strings to indicate regular expression match specification
    private final String flag;

    // actual value in java.util.regEx.Pattern corresponding to the given
    // flag
    private int flagValue = 0;

    private final String regexStr;

    private final ColumnRef col;

    private final AOGOpTree inputTree;

    /**
     * Capturing groups for the regular expression (or empty to just use group 0), along with the
     * corresponding output names. A group can appear more than once.
     */
    private final ArrayList<Pair<Integer, String>> groups;

    protected RegexOp(String moduleName, boolean fast, String flag, String regexStr,
        ArrayList<Pair<Integer, String>> groups, ColumnRef col, AOGOpTree inputTree) {
      super(moduleName, fast ? getConst(AOGParserConstants.FAST_REGEX_OPNAME)
          : getConst(AOGParserConstants.REGEX_OPNAME));
      Object[] tmp = {"/" + regexStr + "/", groups, col, inputTree};
      super.args = tmp;
      this.fast = fast;
      this.flag = flag;
      this.regexStr = regexStr;
      this.groups = groups;
      this.col = col;
      this.inputTree = inputTree;
    }

    @Override
    protected Operator toOpTree(SymbolTable symtab, Catalog catalog) throws ParseException {
      Regex expr;

      try {
        this.flagValue = FlagsString.decode(null, this.flag);
      } catch (FunctionCallValidationException e1) {
        throw new ParseException(String.format("At line %d, error decoding regex flags string '%s'",
            origTok.beginLine, this.flag));
      }

      if (fast) {

        // Use the new SimpleRegex implementation
        try {
          expr = new SimpleRegex(regexStr, this.flagValue);
        } catch (com.ibm.systemt.regex.parse.ParseException e) {
          throw new ParseException(
              String.format("While parsing /%s/: %s", regexStr, e.getMessage()));
        }

      } else {
        expr = new JavaRegex(regexStr, this.flagValue);
      }

      // Decode groups to extract.
      if (0 == groups.size()) {
        throw new ParseException("No capturing groups specified.");
      } else {
        return new RegularExpression(inputTree.toOpTree(symtab, catalog), col.getColName(), expr,
            groups);
      }
    }
  }

  /** Parse tree node for a RegexTok operator. */
  public static class RegexTokOp extends AOGOpTree {

    // Strings to indicate regular expression match specification
    private final String flag;

    // actual value in java.util.regEx.Pattern corresponding to the given
    // flag
    private int flagValue = 0;

    private final String regexStr;

    // Minimum number of tokens allowed in a match (inclusive)
    private final int minTok;

    // Maximum number of tokens allowed in a match (inclusive)
    private final int maxTok;

    private final ColumnRef col;

    private final AOGOpTree inputTree;

    /**
     * Capturing groups for the regular expression (or empty to just use group 0), along with the
     * corresponding output names. A group can appear more than once.
     */
    private final ArrayList<Pair<Integer, String>> groups;

    private final boolean fast;

    /**
     * @param fast TRUE to use a faster, lightweight regex
     * @param flag regular expression matching flags
     * @param regexStr the actual regular expression, as a string
     * @param groups which capturing groups to retain in the output
     * @param minTok minimum length (in tokens) of a match
     * @param maxTok maximum length (in tokens) of a match
     * @param col target column for regex input
     * @param inputTree root of the tree that produces the input for the operator
     */
    protected RegexTokOp(String moduleName, boolean fast, String flag, String regexStr,
        ArrayList<Pair<Integer, String>> groups, int minTok, int maxTok, ColumnRef col,
        AOGOpTree inputTree) {
      super(moduleName, getConst(
          fast ? AOGParserConstants.FASTREGEXTOK_OPNAME : AOGParserConstants.REGEXTOK_OPNAME));
      Object[] tmp = {"/" + regexStr + "/", groups, minTok, maxTok, col, inputTree};
      super.args = tmp;
      this.fast = fast;
      this.flag = flag;
      this.regexStr = regexStr;
      this.groups = groups;
      this.minTok = minTok;
      this.maxTok = maxTok;
      this.col = col;
      this.inputTree = inputTree;
    }

    @Override
    protected Operator toOpTree(SymbolTable symtab, Catalog catalog) throws ParseException {
      Regex expr;

      try {
        this.flagValue = FlagsString.decode(null, this.flag);
      } catch (FunctionCallValidationException e1) {
        throw new ParseException(String.format("At line %d, error decoding regex flags string '%s'",
            origTok.beginLine, this.flag));
      }

      if (fast) {
        try {
          expr = new SimpleRegex(regexStr, this.flagValue);
        } catch (com.ibm.systemt.regex.parse.ParseException e) {
          throw new ParseException(String.format("Couldn't parse regex: %s", e.getMessage()));
        }
      } else {
        expr = new JavaRegex(regexStr, this.flagValue);
      }

      // Decode groups to extract.
      if (0 == groups.size()) {
        throw new ParseException("No capturing groups specified.");
      } else {
        return new RegexTok(inputTree.toOpTree(symtab, catalog), col.getColName(), expr, groups,
            minTok, maxTok);
      }
    }
  }

  /** Parse tree node for the single-input PartOfSpeech operator. */
  public static class PartOfSpeechOp extends AOGOpTree {

    // Input and output column names
    private final ColumnRef inputCol, outputCol;

    // Language code
    private final String langStr;

    // Comma-delimited list of what part of speech tags to extract
    private final String posStr;

    private final AOGOpTree inputTree;

    protected PartOfSpeechOp(String moduleName, ColumnRef inputCol, String langStr, String posStr,
        ColumnRef outputCol, AOGOpTree inputTree) {
      super(moduleName, getConst(AOGParserConstants.PARTOFSPEECH_OPNAME));

      // Wrap the string arguments so that they get properly quoted.
      super.args = new Object[] {inputCol, new ColumnRef(langStr), new ColumnRef(posStr), outputCol,
          inputTree};
      this.inputCol = inputCol;
      this.langStr = langStr;
      this.posStr = posStr;
      this.outputCol = outputCol;
      this.inputTree = inputTree;
    }

    @Override
    protected Operator toOpTree(SymbolTable symtab, Catalog catalog) throws ParseException {
      return new PartOfSpeech(inputCol.getColName(), langStr, posStr, outputCol.getColName(),
          inputTree.toOpTree(symtab, catalog));
    }
  }

  /** Parse tree node for a Project operator. */
  public static class ProjectOp extends AOGOpTree {

    /**
     * If this constant is TRUE, then this parse tree node will create a "virtual" projection by
     * attaching new derived schemas to the output of its input operator.
     */
    private static final boolean VIRTUAL_PROJECT = true;

    private final StringPairList nameMapping;

    private String outputTypeName;

    private final AOGOpTree inputTree;

    protected ProjectOp(String moduleName, String outputTypeName, StringPairList nameMapping,
        AOGOpTree inputTree) {
      super(moduleName, getConst(AOGParserConstants.PROJECT_OPNAME));
      this.args = new Object[] {addQuotes(outputTypeName), nameMapping, inputTree};

      if ("NULL".equals(outputTypeName)) {
        this.outputTypeName = null;
      } else {
        this.outputTypeName = outputTypeName;
      }

      this.nameMapping = nameMapping;
      this.inputTree = inputTree;
    }

    @Override
    protected int numOperatorsRepresented() {
      if (VIRTUAL_PROJECT) {
        // No operator will be instantiated by this parse tree node.
        return 0;
      } else {
        return 1;
      }
    }

    @Override
    protected Operator toOpTree(SymbolTable symtab, Catalog catalog) throws ParseException {
      if (null == catalog) {
        throw new NullPointerException("Null catalog ptr passed to AOGOpTree.ProjectOp.toOpTree()");
      }

      if (VIRTUAL_PROJECT) {
        // "Virtual" projection requested. Instead of creating a Project
        // operator, filter the output schema of the child operator.
        Operator input = inputTree.toOpTree(symtab, catalog);
        input.addProjection(nameMapping);
        return input;
      } else {
        String[] inputColNames = new String[nameMapping.size()];
        String[] outputColNames = new String[nameMapping.size()];

        for (int i = 0; i < nameMapping.size(); i++) {
          Pair<String, String> pair = nameMapping.get(i);
          inputColNames[i] = pair.first;
          outputColNames[i] = pair.second;
        }
        return new Project(outputTypeName, inputColNames, outputColNames,
            inputTree.toOpTree(symtab, null));
      }
    }
  }

  /** Parse tree node for a CrossProduct operator. */
  public static class CrossProdOp extends AOGOpTree {

    private final AOGOpTree inputTree1, inputTree2;

    protected CrossProdOp(String moduleName, AOGOpTree inputTree1, AOGOpTree inputTree2) {
      super(moduleName, getConst(AOGParserConstants.CROSSPROD_OPNAME));
      Object[] tmp = {inputTree1, inputTree2};
      super.args = tmp;
      this.inputTree1 = inputTree1;
      this.inputTree2 = inputTree2;
    }

    @Override
    protected Operator toOpTree(SymbolTable symtab, Catalog catalog) throws ParseException {
      return new CartesianProduct(inputTree1.toOpTree(symtab, null),
          inputTree2.toOpTree(symtab, null));
    }
  }

  /** Parse tree node for a SortMergeJoin operator. */
  public static class SortMergeJoinOp extends AOGOpTree {

    private final ScalarFuncNode pred;

    private final AOGOpTree outer, inner;

    protected SortMergeJoinOp(String moduleName, ScalarFuncNode pred, AOGOpTree outer,
        AOGOpTree inner) {
      super(moduleName, getConst(AOGParserConstants.SORTMERGEJOIN_OPNAME));
      super.args = new Object[] {pred, outer, inner};
      this.pred = pred;
      this.outer = outer;
      this.inner = inner;
    }

    @Override
    protected Operator toOpTree(SymbolTable symtab, Catalog catalog) throws ParseException {
      if (null == catalog) {
        throw new NullPointerException(
            "Null catalog ptr passed to AOGOpTree.SortMergeJoinOp.toOpTree()");
      }

      return new SortMergeJoin(pred.toMergePred(symtab, catalog), outer.toOpTree(symtab, catalog),
          inner.toOpTree(symtab, catalog));
    }
  }

  /** Parse tree node for an AdjacentJoin operator. */
  public static class AdjacentJoinOp extends AOGOpTree {

    private final ScalarFuncNode pred;

    private final AOGOpTree outer, inner;

    protected AdjacentJoinOp(String moduleName, ScalarFuncNode pred, AOGOpTree outer,
        AOGOpTree inner) {
      super(moduleName, getConst(AOGParserConstants.ADJACENTJOIN_OPNAME));
      super.args = new Object[] {pred, outer, inner};
      this.pred = pred;
      this.outer = outer;
      this.inner = inner;
    }

    @Override
    protected Operator toOpTree(SymbolTable symtab, Catalog catalog) throws ParseException {
      return new AdjacentJoin(pred.toMergePred(symtab, catalog), outer.toOpTree(symtab, catalog),
          inner.toOpTree(symtab, catalog));
    }
  }

  /** Parse tree node for a HashJoin operator. */
  public static class HashJoinOp extends AOGOpTree {

    private final ScalarFuncNode pred;

    private final AOGOpTree outer, inner;

    protected HashJoinOp(String moduleName, ScalarFuncNode pred, AOGOpTree outer, AOGOpTree inner) {
      super(moduleName, getConst(AOGParserConstants.HASHJOIN_OPNAME));
      super.args = new Object[] {pred, outer, inner};
      this.pred = pred;
      this.outer = outer;
      this.inner = inner;
    }

    @Override
    protected Operator toOpTree(SymbolTable symtab, Catalog catalog) throws ParseException {
      return new HashJoin(pred.toMergePred(symtab, catalog), outer.toOpTree(symtab, catalog),
          inner.toOpTree(symtab, catalog));
    }
  }

  /** Parse tree node for a NLJoin operator. */
  public static class NLJoinOp extends AOGOpTree {

    private final ScalarFuncNode pred;

    private final AOGOpTree outer, inner;

    /** Additional inputs to cover any locator arguments in the function call tree. */
    private final TreeSet<AOGOpTree.Nickname> locatorInputs = new TreeSet<AOGOpTree.Nickname>();

    protected NLJoinOp(String moduleName, ScalarFuncNode pred, AOGOpTree outer, AOGOpTree inner) {
      super(moduleName, getConst(AOGParserConstants.NLJOIN_OPNAME));
      super.args = new Object[] {pred, outer, inner};
      this.pred = pred;
      this.outer = outer;
      this.inner = inner;

      pred.getLocators(locatorInputs);

      // Construct an arguments list for the shared code that stitches together the operator graph.
      ArrayList<Object> argsList = new ArrayList<Object>();
      argsList.add(pred);
      argsList.addAll(locatorInputs);

      // By convention, the main input comes last.
      argsList.add(outer);
      argsList.add(inner);

      this.args = argsList.toArray();
    }

    @Override
    protected Operator toOpTree(SymbolTable symtab, Catalog catalog) throws ParseException {
      Operator[] locatorOps = new Operator[locatorInputs.size()];
      String[] locatorNames = new String[locatorInputs.size()];
      int i = 0;
      for (AOGOpTree.Nickname input : locatorInputs) {
        locatorNames[i] = input.getRefNick();
        locatorOps[i++] = input.toOpTree(symtab, catalog);
      }

      return new NLJoin((SelectionPredicate) pred.toFunc(symtab, catalog),
          outer.toOpTree(symtab, catalog), inner.toOpTree(symtab, catalog), locatorOps,
          locatorNames);
    }
  }

  /** Parse tree node for a RSEJoin operator. */
  public static class RSEJoinOp extends AOGOpTree {

    private final ScalarFuncNode pred;

    private final AOGOpTree outer, inner;

    protected RSEJoinOp(String moduleName, ScalarFuncNode pred, AOGOpTree outer, AOGOpTree inner) {
      super(moduleName, getConst(AOGParserConstants.RSEJOIN_OPNAME));
      super.args = new Object[] {pred, outer, inner};
      this.pred = pred;
      this.outer = outer;
      this.inner = inner;
    }

    @Override
    protected Operator toOpTree(SymbolTable symtab, Catalog catalog) throws ParseException {
      return new RSEJoin((RSEJoinPred) pred.toFunc(symtab, catalog),
          outer.toOpTree(symtab, catalog), inner.toOpTree(symtab, catalog));
    }
  }

  /**
   * Parse tree node for a Consolidate operator with one input and one output.
   */

  public static class OneInputConsolidateOp extends AOGOpTree {

    private final String partialOrderName;

    private final ScalarFuncNode target;

    private final ScalarFuncNode priorityTarget;

    private final String priorityDirection;

    private final AOGOpTree inputTree;

    protected OneInputConsolidateOp(String moduleName, Token origTok, String partialOrderName,
        ScalarFuncNode target, ScalarFuncNode priorityTarget, String priorityDirection,
        AOGOpTree inputTree) throws ParseException {
      super(moduleName, getConst(AOGParserConstants.CONSOLIDATE_OPNAME));
      this.origTok = origTok.copy();

      this.partialOrderName = partialOrderName;
      this.target = target;
      this.priorityTarget = priorityTarget;
      this.priorityDirection = priorityDirection;
      this.inputTree = inputTree;

      if (priorityTarget == null) {
        // No priority target; ignore direction argument (which the parser should disallow anyway)
        if (null != priorityDirection) {
          throw new ParseException(
              "Consolidate operator has priority direction but no priority target argument");
        }

        super.args = new Object[] {StringUtils.quoteStr('"', partialOrderName), target, inputTree};
      } else {
        // Have priority target. Make sure there's a direction
        if (null == priorityDirection) {
          throw new ParseException(
              "Consolidate operator has priority target but no priority direction argument");
        }

        super.args = new Object[] {StringUtils.quoteStr('"', partialOrderName), target,
            priorityTarget, StringUtils.quoteStr('"', priorityDirection), inputTree};
      }
    }

    @Override
    protected Operator toOpTree(SymbolTable symtab, Catalog catalog) throws ParseException {
      // Decode the partial order argument.
      // PartialOrder<Span> order = PartialOrder
      // .strToOrderObj(partialOrderName);
      if (priorityTarget == null)
        return new Consolidate(inputTree.toOpTree(symtab, catalog), partialOrderName,
            target.toScalarFunc(symtab, catalog), null, null);
      else
        return new Consolidate(inputTree.toOpTree(symtab, catalog), partialOrderName,
            target.toScalarFunc(symtab, catalog), priorityTarget.toScalarFunc(symtab, catalog),
            priorityDirection);
    }
  }

  /**
   * Parse tree node for a DocScan "operator". Invokes the appropriate factory modules to create the
   * right kind of scan operator.
   */
  public static class DocScanOp extends AOGOpTree {

    // private final ArrayList<String> names;
    // private final ArrayList<String> types;

    private TupleSchema schema;

    // declare and set names and types in constructor
    protected DocScanOp(String moduleName, Token origTok, StringPairList schemaStrs)
        throws ParseException {
      super(moduleName, getConst(AOGParserConstants.DOCSCAN_OPNAME));

      // copy the original token minus the next token info so that we don't
      // end up with a chain of tokens representing the entire AOG after this
      // statement.
      // Fixes defect : Out of Memory error while running JAQL extractors in SDA
      this.origTok = origTok.copy();

      // Convert the string representation of the schema into an actual
      // schema.
      String[] colNames = new String[schemaStrs.size()];
      FieldType[] colTypes = new FieldType[schemaStrs.size()];

      for (int i = 0; i < colNames.length; i++) {
        Pair<String, String> elem = schemaStrs.get(i);
        colNames[i] = elem.first;
        try {
          colTypes[i] = FieldType.stringToFieldType(elem.second);
        } catch (com.ibm.avatar.aql.ParseException e) {
          throw new ParseException(
              String.format("Error reading type of '%s' column of doc type", colNames[i]), e);
        }
      }

      schema = ModuleUtils.createSortedDocSchema(new TupleSchema(colNames, colTypes));
    }

    @Override
    protected Operator toOpTree(SymbolTable symtab, Catalog catalog) throws ParseException {

      return new DocScan(schema);
    }

    public TupleSchema getDocSchema() {
      return schema;
    }

    public void setDocSchema(TupleSchema docSchema) {
      schema = docSchema;
    }

    @Override
    public int dump(PrintWriter stream, int indent) throws ParseException {

      // First the nickname, if applicable.
      if (null != entireTreeNick) {
        printIndent(stream, indent);

        stream.printf("%s =\n", StringUtils.toAOGNick(entireTreeNick));
        indent++;
      }

      // Then the name of the top-level operator
      printIndent(stream, indent);
      stream.printf("%s(\n( ", AOGParseTree.DOC_SCAN_BUILTIN);

      // Dump the schema
      for (int i = 0; i < schema.size(); i++) {

        String name = schema.getFieldNameByIx(i);
        FieldType type = schema.getFieldTypeByIx(i);

        printIndent(stream, indent + 2);
        stream.printf("\"%s\" => \"%s\"", name, type);
        if (i == schema.size() - 1) {
          stream.print("\n");
        } else {
          stream.print(",\n");
        }
      }

      printIndent(stream, indent);
      stream.print(")\n);\n");
      stream.flush();

      return 1;
    }

  }

  /**
   * Parse tree node for a TableScan operator.
   */
  public static class TableScanOp extends AOGOpTree {

    private final String tabName;

    protected TableScanOp(String moduleName, Token origTok, String tabName) {
      super(moduleName, getConst(AOGParserConstants.TABLESCAN_OPNAME));
      super.args = new Object[] {tabName};

      // copy the original token minus the next token info so that we don't
      // end up with a chain of tokens representing the entire AOG after this
      // statement.
      // Fixes defect : Out of Memory error while running JAQL extractors in SDA
      this.origTok = origTok.copy();
      this.tabName = tabName;
    }

    @Override
    protected Operator toOpTree(SymbolTable symtab, Catalog catalog) throws ParseException {
      Table table = symtab.getTable(tabName);
      return new TableScan(table);
    }
  }

  /**
   * Parse tree node for a ExternalViewScan operator.
   */
  public static class ExternalViewScanOp extends AOGOpTree {

    private final String viewName;

    protected ExternalViewScanOp(String moduleName, Token origTok, String viewName) {
      super(moduleName, getConst(AOGParserConstants.EXTERNALVIEWSCAN_OPNAME));
      super.args = new Object[] {viewName};

      this.origTok = origTok.copy();
      this.viewName = viewName;
    }

    @Override
    protected Operator toOpTree(SymbolTable symtab, Catalog catalog) throws ParseException {
      ExternalView view = symtab.getExternalView(viewName);
      return new ExternalViewScan(view);
    }

    /**
     * Fix for bug #168205: ExternalViewScanOp dump() method not in synch with the AOG syntax We
     * need a special dump method because of the nonstandard syntax for this operator.
     */
    @Override
    public int dump(PrintWriter stream, int indent) throws ParseException {
      stream.printf("%s = ExternalViewScan(%s);\n", StringUtils.toAOGNick(viewName),
          StringUtils.quoteStr('"', viewName));

      return numOperatorsRepresented() + 1;
    }
  }

  /**
   * Parse tree node for an ApplyFunc operator.
   */
  public static class ApplyFuncOp extends AOGOpTree {

    private final Token origTok;

    private final String outputName;

    /** The primary input (i.e. join result) over which the scalar function is evaluated. */
    private final AOGOpTree mainInput;

    /** Additional inputs to cover any locator arguments in the function call tree. */
    private final TreeSet<AOGOpTree.Nickname> locatorInputs = new TreeSet<AOGOpTree.Nickname>();

    private final ScalarFuncNode func;

    public ApplyFuncOp(String moduleName, Token origTok, ScalarFuncNode func, String outputName,
        AOGOpTree input) {
      super(moduleName, getConst(AOGParserConstants.APPLYFUNC_OPNAME));

      this.origTok = origTok.copy();
      this.func = func;
      this.outputName = outputName;
      this.mainInput = input;

      func.getLocators(locatorInputs);

      // Construct an arguments list for the shared code that stitches together the operator graph.
      ArrayList<Object> argsList = new ArrayList<Object>();
      argsList.add(func);
      argsList.add(outputName);
      argsList.addAll(locatorInputs);

      // By convention, the main input comes last.
      argsList.add(mainInput);

      args = argsList.toArray();

    }

    @Override
    protected Operator toOpTree(SymbolTable symtab, Catalog catalog) throws ParseException {
      if (null == catalog) {
        throw new NullPointerException(
            "Null catalog ptr passed to AOGOpTree.ApplyFuncOp.toOpTree()");
      }

      // By convention, the last input is the "main" input; the remaining inputs are for locators
      Operator[] children = new Operator[locatorInputs.size() + 1];
      String[] locatorTargetNames = new String[locatorInputs.size()];

      int i = 0;
      for (AOGOpTree.Nickname locator : locatorInputs) {
        locatorTargetNames[i] = locator.getRefNick();

        if (null == locatorTargetNames[i]) {
          throw new FatalInternalError("Got null ptr for locator target %d (%s)", i, locator);
        }

        children[i++] = locator.toOpTree(symtab, catalog);
      }

      children[children.length - 1] = mainInput.toOpTree(symtab, catalog);

      return new ApplyScalarFunc(func.toScalarFunc(symtab, catalog), outputName, children,
          locatorTargetNames, func.funcname);
    }

    /**
     * We need a special dump method because of the nonstandard syntax for this operator.
     */
    @Override
    public int dump(PrintWriter stream, int indent) throws ParseException {
      // First the nickname, if applicable.
      if (null != entireTreeNick) {
        printIndent(stream, indent);
        stream.printf("$%s =\n", entireTreeNick);
        indent++;
      }

      // Then the name of the top-level operator
      printIndent(stream, indent);
      stream.printf("%s(\n", getConst(AOGParserConstants.APPLYFUNC_OPNAME));

      int numobjs = 1;

      // Then the function call and destination.
      numobjs += dumpObj(stream, indent + 1, func, origTok);
      stream.printf(" => \"%s\",\n", outputName);

      // Then the main input
      mainInput.dump(stream, indent + 1);

      // Then close the parens.
      // If we're a top-level tree (e.g. one with a nickname), add the
      // semicolon.
      printIndent(stream, indent);
      if (null == entireTreeNick) {
        stream.print(")");
      } else {
        stream.print(");\n");
      }

      return numobjs + 1;
    }
  }

  /**
   * Parse tree node for an Difference operator.
   */
  public static class DifferenceOp extends AOGOpTree {

    /** We'll compute (first - second) */
    private final AOGOpTree first, second;

    protected DifferenceOp(String moduleName, AOGOpTree first, AOGOpTree second) {
      super(moduleName, getConst(AOGParserConstants.DIFFERENCE_OPNAME));
      Object[] tmp = {first, second};
      super.args = tmp;
      this.first = first;
      this.second = second;
    }

    @Override
    protected Operator toOpTree(SymbolTable symtab, Catalog catalog) throws ParseException {
      return new Difference(first.toOpTree(symtab, catalog), second.toOpTree(symtab, catalog));
    }
  }

  /**
   * Parse tree node for a Block operator.
   */
  public static class BlockOp extends AOGOpTree {

    private final int charsBetween;

    private final int minSize;

    private final int maxSize;

    private final ColumnRef col;

    private final String outputCol;

    private final AOGOpTree inputTree;

    protected BlockOp(String moduleName, int charsBetween, int minSize, int maxSize, ColumnRef col,
        String outputCol, AOGOpTree inputTree) {
      super(moduleName, getConst(AOGParserConstants.BLOCK_OPNAME));
      Object[] tmp = {charsBetween, minSize, maxSize, col, inputTree};
      super.args = tmp;
      this.charsBetween = charsBetween;
      this.minSize = minSize;
      this.maxSize = maxSize;
      this.col = col;
      this.outputCol = outputCol;
      this.inputTree = inputTree;
    }

    @Override
    protected Operator toOpTree(SymbolTable symtab, Catalog catalog) throws ParseException {
      return new BlockChar(charsBetween, minSize, maxSize, col.getColName(), outputCol,
          inputTree.toOpTree(symtab, catalog));
    }
  }

  /**
   * Parse tree node for the token-distance version of the Block operator.
   */
  public static class BlockTokOp extends AOGOpTree {

    private final int toksBetween;

    private final int minSize;

    private final int maxSize;

    private final ColumnRef col;

    private final String outputCol;

    private final AOGOpTree inputTree;

    protected BlockTokOp(String moduleName, int toksBetween, int minSize, int maxSize,
        ColumnRef col, String outputCol, AOGOpTree inputTree) {
      super(moduleName, getConst(AOGParserConstants.BLOCKTOK_OPNAME));
      Object[] tmp = {toksBetween, minSize, maxSize, col, inputTree};
      super.args = tmp;
      this.toksBetween = toksBetween;
      this.minSize = minSize;
      this.maxSize = maxSize;
      this.col = col;
      this.outputCol = outputCol;
      this.inputTree = inputTree;
    }

    @Override
    protected Operator toOpTree(SymbolTable symtab, Catalog catalog) throws ParseException {
      return new BlockTok(toksBetween, minSize, maxSize, col.getColName(), outputCol,
          inputTree.toOpTree(symtab, catalog));
    }
  }

  /**
   * Parse tree node for a Group operator.
   */
  public static class GroupOp extends AOGOpTree {

    /**
     * List of functions to group by.
     */
    private final ArrayList<ScalarFuncNode> groupByFnTrees;

    /**
     * List of aggregate functions to evaluate.
     */
    private final ArrayList<AggFuncNode> aggFnTrees;

    /**
     * List of aliases given to the aggregate functions in the select list.
     */
    private final ArrayList<String> aggAliases;

    private final AOGOpTree inputTree;

    protected GroupOp(String moduleName, ArrayList<ScalarFuncNode> groupByFuncs,
        ArrayList<AggFuncNode> aggFuncs, ArrayList<String> aggAliases, AOGOpTree inputTree) {
      super(moduleName, getConst(AOGParserConstants.GROUP_OPNAME));
      Object[] tmp = {groupByFuncs, aggFuncs, aggAliases, inputTree};
      super.args = tmp;
      this.groupByFnTrees = groupByFuncs;
      this.aggFnTrees = aggFuncs;
      this.aggAliases = aggAliases;
      this.inputTree = inputTree;
    }

    @Override
    protected Operator toOpTree(SymbolTable symtab, Catalog catalog) throws ParseException {

      ArrayList<ScalarFunc> groupByFuncs = new ArrayList<ScalarFunc>();

      for (ScalarFuncNode groupByFnTree : groupByFnTrees) {
        ScalarFunc groupByFn = groupByFnTree.toScalarFunc(symtab, catalog);
        groupByFuncs.add(groupByFn);
      }

      ArrayList<AggFunc> aggFuncs = new ArrayList<AggFunc>();

      for (AggFuncNode aggFnTree : aggFnTrees) {
        AggFunc aggFn = aggFnTree.toFunc(symtab, catalog);
        aggFuncs.add(aggFn);
      }

      return new Group(groupByFuncs, aggFuncs, aggAliases, inputTree.toOpTree(symtab, catalog));
    }
  }

  /**
   * Parse tree node for a Sort operator. Internally, Sort has a flexible system of sort orders, but
   * we hide that functionality for the time being. Right now, the only argument to Sort in AOG a
   * function call that returns an integer.
   */
  public static class SortOp extends AOGOpTree {

    /**
     * Function that, when applied to an input tuple, returns the integer sort key.
     */
    private final ArrayList<ScalarFuncNode> sortKeyFnTrees;

    private final AOGOpTree inputTree;

    protected SortOp(String moduleName, ArrayList<ScalarFuncNode> sortKeyFn, AOGOpTree inputTree) {
      super(moduleName, getConst(AOGParserConstants.SORT_OPNAME));
      Object[] tmp = {sortKeyFn, inputTree};
      super.args = tmp;
      this.sortKeyFnTrees = sortKeyFn;
      this.inputTree = inputTree;
    }

    @Override
    protected Operator toOpTree(SymbolTable symtab, Catalog catalog) throws ParseException {

      ArrayList<ScalarFunc> sortKeyFuncs = new ArrayList<ScalarFunc>();

      for (ScalarFuncNode sortKeyFnTree : sortKeyFnTrees) {
        ScalarFunc sortKeyFn = sortKeyFnTree.toScalarFunc(symtab, catalog);
        sortKeyFuncs.add(sortKeyFn);
      }

      return new Sort(sortKeyFuncs, inputTree.toOpTree(symtab, catalog));
    }
  }

  /** Parse tree node for a Select operator. */
  public static class SelectOp extends AOGOpTree {

    private final ScalarFuncNode predTree;

    private final AOGOpTree inputTree;

    /** Additional inputs to cover any locator arguments in the function call tree. */
    private final TreeSet<AOGOpTree.Nickname> locatorInputs = new TreeSet<AOGOpTree.Nickname>();

    protected SelectOp(String moduleName, ScalarFuncNode predTree, AOGOpTree inputTree) {
      super(moduleName, getConst(AOGParserConstants.SELECT_OPNAME));

      this.inputTree = inputTree;
      this.predTree = predTree;

      predTree.getLocators(locatorInputs);

      // Construct an arguments list for the shared code that stitches together the operator graph.
      ArrayList<Object> argsList = new ArrayList<Object>();
      argsList.add(predTree);
      argsList.addAll(locatorInputs);

      // By convention, the main input comes last.
      argsList.add(inputTree);

      this.args = argsList.toArray();
    }

    @Override
    protected Operator toOpTree(SymbolTable symtab, Catalog catalog) throws ParseException {
      SelectionPredicate pred = (SelectionPredicate) predTree.toFunc(symtab, catalog);
      Operator mainInput = inputTree.toOpTree(symtab, catalog);

      // By convention, the last input is the "main" input; the remaining inputs are for locators
      Operator[] children = new Operator[locatorInputs.size() + 1];
      String[] locatorTargetNames = new String[locatorInputs.size()];

      int i = 0;
      for (AOGOpTree.Nickname locator : locatorInputs) {
        locatorTargetNames[i] = locator.getRefNick();

        if (null == locatorTargetNames[i]) {
          throw new FatalInternalError("Got null ptr for locator target %d (%s)", i, locator);
        }

        children[i++] = locator.toOpTree(symtab, catalog);
      }

      children[children.length - 1] = mainInput;

      return new Select(children, locatorTargetNames, pred);
    }
  }

  /** Parse tree node for a Limit operator. */
  public static class LimitOp extends AOGOpTree {

    private final int maxtup;

    private final AOGOpTree inputTree;

    protected LimitOp(String moduleName, Token origTok, int maxtup, AOGOpTree inputTree) {
      super(moduleName, getConst(AOGParserConstants.LIMIT_OPNAME));
      Object[] tmp = {maxtup, inputTree};
      this.args = tmp;
      this.inputTree = inputTree;
      this.maxtup = maxtup;
    }

    @Override
    protected Operator toOpTree(SymbolTable symtab, Catalog catalog) throws ParseException {
      Operator input = inputTree.toOpTree(symtab, catalog);
      return new Limit(maxtup, input);
    }
  }

  /** Parse tree node for a Split operator. */
  public static class SplitOp extends AOGOpTree {

    private final ColumnRef targetCol, splitCol;

    private final String outputCol;

    private final int flags;

    private final AOGOpTree inputTree;

    protected SplitOp(String moduleName, Token origTok, ColumnRef targetCol, ColumnRef splitCol,
        String outputCol, int flags, AOGOpTree inputTree) {
      super(moduleName, getConst(AOGParserConstants.SPLIT_OPNAME));
      this.args = new Object[] {targetCol, splitCol, flags, addQuotes(outputCol), inputTree};

      this.targetCol = targetCol;
      this.splitCol = splitCol;
      this.outputCol = outputCol;
      this.flags = flags;
      this.inputTree = inputTree;
    }

    @Override
    protected Operator toOpTree(SymbolTable symtab, Catalog catalog) throws ParseException {

      Operator input = inputTree.toOpTree(symtab, catalog);

      return new Split(input, targetCol.getColName(), splitCol.getColName(), outputCol, flags);
    }
  }

  /** Parse tree node for a table function call (UDF or built-in) */
  public static class TabFuncCallOp extends AOGOpTree {
    String funcName;
    ArrayList<AOGFuncNode> funcArgs;
    ArrayList<AOGOpTree.Nickname> inputs;

    /** Additional inputs to cover any locator arguments in the function call tree. */
    private final TreeSet<AOGOpTree.Nickname> locatorInputs = new TreeSet<AOGOpTree.Nickname>();

    public TabFuncCallOp(String moduleName, Token t, String funcName,
        ArrayList<AOGFuncNode> funcArgs, ArrayList<AOGOpTree.Nickname> inputs) {
      super(moduleName, t);

      this.funcName = funcName;
      this.funcArgs = funcArgs;

      this.inputs = inputs;

      // If any of the inputs to the table function are scalar functions with locator inputs, add
      // those locators to our
      // set of inputs for the table function evaluation.
      for (AOGFuncNode funcNode : funcArgs) {
        if (funcNode instanceof ScalarFuncNode) {
          ScalarFuncNode sfn = (ScalarFuncNode) funcNode;
          sfn.getLocators(locatorInputs);
        }
      }

      // Convert inputs to the superclass's format, so that the superclass's dependency generation
      // will work properly.
      this.args = new Object[inputs.size() + locatorInputs.size()];
      int i = 0;
      for (Nickname nickname : inputs) {
        this.args[i++] = nickname;
      }
      for (Nickname nickname : locatorInputs) {
        this.args[i++] = nickname;
      }
    }

    @Override
    protected Operator toOpTree(SymbolTable symtab, Catalog catalog) throws ParseException {
      // Convert arguments to the appropriate runtime objects
      AQLFunc[] convertedArgs = new AQLFunc[funcArgs.size()];
      for (int i = 0; i < convertedArgs.length; i++) {
        AOGFuncNode arg = funcArgs.get(i);
        if (arg instanceof ScalarFuncNode) {
          ScalarFuncNode sarg = (ScalarFuncNode) arg;
          convertedArgs[i] = sarg.toFunc(symtab, catalog);
        } else if (arg instanceof TableLocatorNode) {
          TableLocatorNode larg = (TableLocatorNode) arg;
          convertedArgs[i] = new TableLocator(larg.getTargetName());
        } else {
          throw new FatalInternalError(
              "Found unexpected arg type %s in argument %d of %s table function", arg, i, funcName);
        }
      }

      TableFuncCatalogEntry entry = catalog.lookupTableFunc(funcName);
      if (null == entry) {
        throw new ParseException(
            String.format("No information in catalog for table function '%s'", funcName));
      }

      TableReturningFunc func;
      if (entry instanceof TableUDFCatalogEntry) {
        // User-defined table function
        try {
          func = new TableUDF(null, convertedArgs, funcName, catalog);
        } catch (com.ibm.avatar.aql.ParseException e) {
          throw new ParseException("Error initializing table-returning UDF object", e);
        }
      } else {
        throw new FatalInternalError(
            "Instantiation of non-user-defined table functions not yet implemented.");
      }

      // Gather together any locator inputs
      // Reuse the superclass's args list that we constructed earlier.
      Operator[] children = new Operator[args.length];
      String[] targetNames = new String[args.length];

      for (int i = 0; i < children.length; i++) {
        Nickname n = (Nickname) args[i];
        children[i] = n.toOpTree(symtab, catalog);
        targetNames[i] = n.getRefNick();
      }

      return new ApplyTableFunc(func, children, targetNames);
    }

  }

  /**
   * Recursive internal implementation of operator tree conversion.
   * 
   * @param symtab symbol table for decoding nicknames and inline dictionaries.
   * @param catalog TODO
   * @throws ParseException if anything goes wrong with conversion
   */
  protected abstract Operator toOpTree(SymbolTable symtab, Catalog catalog) throws ParseException;

  /** Convenience method to add quotes to a string for printing. */
  protected String addQuotes(String str) {
    return "\"" + str + "\"";
  }

  private Operator singleOutput = null;

  private Tee multiOutput = null;

  /**
   * This is actually a ceiling on the number of outputs the operator will have at runtime.
   */
  private int numOutput = -1;

  private int numOutputUsed = -1;

  /**
   * @return the next available output of this operator's corresponding tree of runtime operators,
   *         assuming that this tree exists and has an output that isn't spoken for.
   * @throws ParseException if an output is not available
   */
  public Operator getNextOutput() throws ParseException {

    if (-1 == numOutput) {
      throw new AOGConversionException(origTok,
          "Tried to call getNextOutput() on " + this + " before converting tree to runtime ops.  "
              + "(This usually means that dependency" + " generation is broken)");
    }

    if (numOutputUsed >= numOutput) {
      throw new AOGConversionException(origTok,
          String.format("Not enough outputs left (%d of %d used)", numOutputUsed, numOutput));
    }

    Operator ret;
    if (1 == numOutput) {
      // Single output; skip the tee.
      ret = singleOutput;
    } else {
      ret = multiOutput.getNextOutput();
    }

    numOutputUsed++;

    return ret;
  }

  /**
   * @return the top-level nickname in the AOG file of the operator subtree that contains this
   *         operator
   */
  public String getEntireTreeNick() {
    return entireTreeNick;
  }

}
