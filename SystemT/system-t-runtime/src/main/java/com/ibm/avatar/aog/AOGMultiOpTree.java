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
import java.util.List;

import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.algebra.extract.Detag;
import com.ibm.avatar.algebra.extract.Dictionaries;
import com.ibm.avatar.algebra.extract.RegexesTok;
import com.ibm.avatar.algebra.util.data.StringPairList;
import com.ibm.avatar.algebra.util.dict.CompiledDictionary;
import com.ibm.avatar.algebra.util.dict.DictParams.CaseSensitivityType;
import com.ibm.avatar.api.OperatorGraphImpl;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.systemt.util.regex.RegexesTokParams;

/**
 * Parse tree node for an expression that returns one or more nicknames, using Perl array
 * initializer syntax.
 * 
 */
public class AOGMultiOpTree extends AOGParseTreeNode {

  /** Human-readable name for the operator. Set by child classes. */
  protected String opname;

  /** The list of nicknames this tree's outputs are assigned to. */
  protected ArrayList<String> outputNicks = null;

  /** Constructor used for factory object. */
  // private AOGMultiOpTree() {
  // opname = "MultiOperator Factory";
  // }
  public AOGMultiOpTree(String moduleName, String opname) {
    super(moduleName);
    this.opname = opname;
  }

  /**
   * Instance for producing the various inner classes that define the individual tree nodes.
   */
  // private static AOGMultiOpTree factory = new AOGMultiOpTree();
  // public static AOGMultiOpTree getFactory() {
  // return factory;
  // }
  /** Parse tree node for the multi-file dictionary operator. */
  public static class DictsOp extends AOGMultiOpTree {

    private Token origTok;

    private ArrayList<Pair<String, String>> dictsAndModes;

    private ColumnRef colName;

    private ColumnRef outputColName;

    private AOGOpTree inputOpTree;

    /**
     * The actual Dictionary operator, which has multiple outputs. Index of outputs is the same as
     * indexes of {@code this.dictfiles}.
     */
    private Dictionaries output = null;

    public DictsOp(String moduleName, Token t, ArrayList<Pair<String, String>> dictsAndModes,
        ColumnRef colName, ColumnRef outputColName, AOGOpTree input) {
      super(moduleName, AOGOpTree.getConst(AOGParserConstants.DICTS_OPNAME));
      this.origTok = t;
      this.dictsAndModes = dictsAndModes;
      this.colName = colName;
      this.outputColName = outputColName;
      this.inputOpTree = input;
    }

    /**
     * Copy constructor - this constructor is used while merging SDM nodes from different modules.
     * 
     * @param givenDictOp DictsOp to be copied over to the newly constructed object
     * @param outputNickName nick name of the merged DictsOp
     */
    public DictsOp(String moduleName, DictsOp givenDictOp, String outputNickName) {
      this(moduleName, givenDictOp.origTok, givenDictOp.dictsAndModes, givenDictOp.colName,
          givenDictOp.outputColName, givenDictOp.inputOpTree);

      if (null == this.outputNicks) {
        this.outputNicks = new ArrayList<String>();
      }
      this.outputNicks.add(outputNickName);
    }

    /**
     * Derives a DictsOp instance based on the original DictsOp node returned by AOG Parser. The
     * derived node corresponds to a single SDM output nick. Hence it would contain only one
     * outputNick and its corresponding dictsModes parameter. <br/>
     * Note: This constructor is for internal use only.
     * 
     * @param moduleName Name of the module that the parse tree node belongs to
     * @param origDictsop The original DictsOp node returned by AOG Parser
     * @param dictsAndModes dictionary name and case match modes
     * @param outputNickName output nick name of the SDM output node represented by the current
     *        instance of DictsOp
     */
    private DictsOp(String moduleName, DictsOp origDictsop,
        ArrayList<Pair<String, String>> dictsAndModes, String outputNickName) {
      this(moduleName, origDictsop.origTok, dictsAndModes, origDictsop.colName,
          origDictsop.outputColName, origDictsop.inputOpTree);

      // instantiate outputNicks if null
      if (null == this.outputNicks) {
        this.outputNicks = new ArrayList<String>();
      }

      // copy over outputNick if not already added
      if (false == this.outputNicks.contains(outputNickName)) {
        this.outputNicks.add(outputNickName);
      }
    }

    /**
     * Replacement for the getDeps() method of AOGOpTree. Defers to the input subtree.
     */
    @Override
    public void getDeps(SymbolTable symtab, ArrayList<AOGOpTree> deps) throws ParseException {
      inputOpTree.getDeps(symtab, deps);
    }

    @Override
    public Operator toOpTree(String curNick, SymbolTable symtab, Catalog catalog)
        throws ParseException {
      if (null == output) {
        // Create the singleton operator instance.

        Operator input = inputOpTree.toOpTree(symtab, null);

        CompiledDictionary[] files = new CompiledDictionary[dictsAndModes.size()];
        CaseSensitivityType[] cases = new CaseSensitivityType[dictsAndModes.size()];

        for (int i = 0; i < dictsAndModes.size(); i++) {
          // Unpack the dict file, mode pair
          Pair<String, String> pair = dictsAndModes.get(i);
          String dictName = pair.first;
          String matchType = pair.second;

          files[i] = symtab.lookupDict(dictName);
          try {
            cases[i] = CaseSensitivityType.decodeStr(null, matchType);
          } catch (FunctionCallValidationException e) {
            throw new ParseException(
                String.format("At line %d of AOG, error decoding dict match type str '%s'",
                    origTok.beginLine, matchType),
                e);
          }

          // Decode the "match type" argument. There are currently
          // only two options, plus the default.

        }

        output = new Dictionaries(input, colName.getColName(), outputColName.getColName(), files,
            cases, symtab);
      }

      // If we get here, the output is ready. Figure out what index the
      // requested nickname corresponds to.
      int ix = nickToIndex(curNick);

      // Dictionary operator has one output per dictionary
      return output.getOutput(ix);
    }

    @Override
    public ArrayList<AOGOpTree> toParseTrees() {
      ArrayList<AOGOpTree> ret = new ArrayList<AOGOpTree>();

      // Create a PlaceholderOp for every outputNick found in DictsOp
      // Split the DictsOp such that each PlaceholderOp returned by this method corresponds to the
      // outputNick and
      // its related dictsAndModes object
      for (int i = 0; i < outputNicks.size(); i++) {
        String outputNickName = outputNicks.get(i);

        // The regexesTok object created below using the special internal constructor contains
        // dictsAndModes *ONLY* for
        // the specified outputNickName
        ArrayList<Pair<String, String>> dictAndMode = new ArrayList<Pair<String, String>>();
        dictAndMode.add(this.dictsAndModes.get(i));

        DictsOp dictsOp = new DictsOp(moduleName, this, dictAndMode, outputNickName);
        ret.add(new PlaceholderOp(moduleName, opname, dictsOp, outputNickName));
      }
      return ret;
    }

    @Override
    public int dump(PrintWriter stream, int indent) throws ParseException {
      // First the nicknames list.
      AOGOpTree.printIndent(stream, indent);
      stream.print("(");
      for (int i = 0; i < outputNicks.size(); i++) {
        stream.print("$" + outputNicks.get(i));
        if ((i + 1) < outputNicks.size()) {
          stream.print(", ");
        }
      }
      stream.print(") =\n");

      // Then the name of the top-level operator
      AOGOpTree.printIndent(stream, indent + 1);
      stream.printf("%s(\n", AOGOpTree.getConst(AOGParserConstants.DICTS_OPNAME));

      // Then the arguments.
      AOGOpTree.printIndent(stream, indent + 2);
      stream.print("(\n");

      for (int i = 0; i < dictsAndModes.size(); i++) {
        Pair<String, String> pair = dictsAndModes.get(i);
        AOGOpTree.printIndent(stream, indent + 3);
        stream.printf("\"%s\" => \"%s\"", pair.first, pair.second);

        if (i < dictsAndModes.size() - 1) {
          stream.print(",\n");
        } else {
          stream.print("\n");
        }
      }

      AOGOpTree.printIndent(stream, indent + 2);
      stream.printf("),\n");

      AOGOpTree.printIndent(stream, indent + 2);
      stream.printf("%s, %s,\n", colName, outputColName);

      inputOpTree.dump(stream, indent + 2);

      // Then close the parens.
      // We're a top-level tree, so always add a semicolon.
      AOGOpTree.printIndent(stream, indent + 1);
      stream.print(");\n");

      // No operator inside Dicts()
      return 1;
    }

    /**
     * Method to merge given DictsOp with current one.
     * 
     * @param givenDictsOp DictOp to be merged
     * @param outputNickName nick name of the merged DictsOp
     */
    public void mergeDictOp(DictsOp givenDictsOp, String outputNickName) {

      if (false == outputNicks.contains(outputNickName)) {
        outputNicks.add(outputNickName);

        dictsAndModes.add(givenDictsOp.dictsAndModes.get(0));
      }
    }

    @Override
    public List<String> removeUnusedOutputNicks(SymbolTable symbTab) {
      ArrayList<String> nicksToRemove = new ArrayList<String>();

      ArrayList<Pair<String, String>> dictsAndModesToRemove = new ArrayList<Pair<String, String>>();

      for (String nick : outputNicks) {
        if (symbTab.containsNick(nick) == false) {
          int index = nickToIndex(nick);

          nicksToRemove.add(nick);
          dictsAndModesToRemove.add(dictsAndModes.get(index));
        }
      }

      outputNicks.removeAll(nicksToRemove);
      dictsAndModes.removeAll(dictsAndModesToRemove);

      return nicksToRemove;
    }

  }

  /** Parse tree node for the Detag operator. */
  public static class DeTagOp extends AOGMultiOpTree {

    /**
     * A "tag" label that actually points to the name to give the output schema for the type.
     */
    public static final String TAG_SCHEMA_NAME = "__SCHEMA__";

    @SuppressWarnings("unused")
    private Token origTok;

    private ArrayList<Pair<String, StringPairList>> tagSpec;

    private ColumnRef colName;

    private String docTypeName;

    /**
     * Should the detagger check whether a document is HTML before attempting to detag?
     */
    private boolean checkForHTML;

    private AOGOpTree inputOpTree;

    /**
     * The actual Detag operator, which has multiple outputs.
     */
    private Detag output = null;

    public DeTagOp(String moduleName, Token t, ArrayList<Pair<String, StringPairList>> tagSpec,
        ColumnRef colName, String docTypeName, boolean checkForHTML, AOGOpTree input) {
      super(moduleName, AOGOpTree.getConst(AOGParserConstants.DETAG_OPNAME));
      this.origTok = t;
      this.tagSpec = tagSpec;
      this.colName = colName;
      this.docTypeName = docTypeName;
      this.checkForHTML = checkForHTML;
      this.inputOpTree = input;
    }

    /**
     * Replacement for the getDeps() method of AOGOpTree. Defers to the input subtree.
     */
    @Override
    public void getDeps(SymbolTable symtab, ArrayList<AOGOpTree> deps) throws ParseException {
      inputOpTree.getDeps(symtab, deps);
    }

    @Override
    public Operator toOpTree(String curNick, SymbolTable symtab, Catalog catalog)
        throws ParseException {
      if (null == output) {
        // Create the singleton operator instance.

        Operator input = inputOpTree.toOpTree(symtab, null);

        int numTags = tagSpec.size();

        // Translate arguments into a format that the operator's
        // constructor will understand.
        String[] tags = new String[numTags];
        String[] tagTypes = new String[numTags];
        String[][] attrs = new String[numTags][];
        String[][] attrLabels = new String[numTags][];

        for (int i = 0; i < numTags; i++) {
          Pair<String, StringPairList> p = tagSpec.get(i);

          tags[i] = p.first;

          StringPairList attrToLabel = p.second;

          // The attribute to label mapping contains one entry for the
          // name of the output schema for the tag.
          int numAttrs = attrToLabel.size() - 1;

          // This attribute must be the first element.
          if (false == TAG_SCHEMA_NAME.equals(attrToLabel.get(0).first)) {
            throw new ParseException(
                "First element of tag schema mapping" + " must contain schema name");
          }

          tagTypes[i] = attrToLabel.get(0).second;

          attrs[i] = new String[numAttrs];
          attrLabels[i] = new String[numAttrs];

          for (int j = 1; j < attrToLabel.size(); j++) {
            attrs[i][j - 1] = attrToLabel.get(j).first;
            attrLabels[i][j - 1] = attrToLabel.get(j).second;
          }

        }

        output = new Detag(tags, tagTypes, attrs, attrLabels, docTypeName, colName.getColName(),
            checkForHTML, input);
      }

      // If we get here, the output is ready. Figure out what index the
      // requested nickname corresponds to.
      int ix = nickToIndex(curNick);

      // Return the appropriate output for the indicated nickname.
      return output.getOutput(ix);
    }

    @Override
    public int dump(PrintWriter stream, int indent) throws ParseException {
      // First the nicknames list.
      AOGOpTree.printIndent(stream, indent);
      stream.print("(");
      for (int i = 0; i < outputNicks.size(); i++) {
        stream.print("$" + outputNicks.get(i));
        if ((i + 1) < outputNicks.size()) {
          stream.print(", ");
        }
      }
      stream.print(") =\n");

      // Then the name of the top-level operator
      AOGOpTree.printIndent(stream, indent + 1);
      stream.printf("%s(\n", AOGOpTree.getConst(AOGParserConstants.DETAG_OPNAME));

      // Tag specification
      for (int i = 0; i < tagSpec.size(); i++) {
        AOGOpTree.printIndent(stream, indent + 2);
        stream.printf("(\"%s\", (\n", tagSpec.get(i).first);

        StringPairList pairs = tagSpec.get(i).second;
        for (int j = 0; j < pairs.size(); j++) {
          AOGOpTree.printIndent(stream, indent + 3);
          stream.printf("\"%s\" => \"%s\"", pairs.get(j).first, pairs.get(j).second);

          if (j < pairs.size() - 1) {
            stream.printf(",\n");
          } else {
            stream.printf("\n");
          }
        }

        AOGOpTree.printIndent(stream, indent + 2);
        stream.printf(")),\n");
      }

      // Column name
      AOGOpTree.printIndent(stream, indent + 2);
      stream.printf("%s,\n", colName);

      // Output type name for detagged docs
      AOGOpTree.printIndent(stream, indent + 2);
      stream.printf("\"%s\",\n", docTypeName);

      // Whether to check for HTML.
      AOGOpTree.printIndent(stream, indent + 2);
      stream.printf("\"%s\",\n", checkForHTML ? "true" : "false");

      inputOpTree.dump(stream, indent + 2);
      stream.printf("\n");

      // Then close the parens.
      // We're a top-level tree, so always add a semicolon.
      AOGOpTree.printIndent(stream, indent + 1);
      stream.print(");\n");

      // No operator inside Dicts()
      return 1;
    }

    @Override
    public List<String> removeUnusedOutputNicks(SymbolTable symbTab) {
      ArrayList<String> nicksToRemove = new ArrayList<String>();

      for (String nick : outputNicks) {
        if (symbTab.containsNick(nick) == false) {
          nicksToRemove.add(nick);
        }
      }
      outputNicks.removeAll(nicksToRemove);

      ArrayList<Pair<String, StringPairList>> tagSpecToRemove =
          new ArrayList<Pair<String, StringPairList>>();
      for (Pair<String, StringPairList> tagSpec : this.tagSpec) {
        StringPairList pairs = tagSpec.second;
        for (int j = 0; j < pairs.size(); j++) {
          if (nicksToRemove.contains(pairs.get(j).second)) {
            tagSpecToRemove.add(tagSpec);
          }
        }
      }
      this.tagSpec.removeAll(tagSpecToRemove);

      return nicksToRemove;
    }

    /**
     * This method returns the nickname of the main detag view.
     * 
     * @return the nickname of the main detag view
     */
    public String getMainDetagViewNick() {
      return this.docTypeName;
    }
  }

  /** Put in place the array of nicknames this tree's output is assigned to. */
  public void setNicknames(ArrayList<String> nicks) {
    outputNicks = nicks;
  }

  /**
   * @param curNick one of the nicknames that this operator's output is assigned to in the original
   *        AOG expression
   * @return index of the indicated nickname in the array of names
   */
  protected int nickToIndex(String curNick) {
    int ix = -1;

    for (int i = 0; i < outputNicks.size(); i++) {
      if (outputNicks.get(i).equals(curNick)) {
        ix = i;
        break;
      }
    }
    if (-1 == ix) {
      throw new IllegalArgumentException("Don't have a nickname '" + curNick + "'");
    }
    return ix;
  }

  /**
   * @return a set of fake placeholder parse tree nodes, one for each nickname in the original
   *         outputs list.
   */
  public ArrayList<AOGOpTree> toParseTrees() {
    ArrayList<AOGOpTree> ret = new ArrayList<AOGOpTree>();
    for (int i = 0; i < outputNicks.size(); i++) {
      ret.add(new PlaceholderOp(moduleName, opname, this, outputNicks.get(i)));
    }
    return ret;
  }

  /**
   * This method is only present in AOGMultiOpTree because the class needs to be non-abstract to
   * function as a factory. We implement dump in the child classes, since there are relatively few
   * of them.
   */
  @Override
  public int dump(PrintWriter stream, int indent) throws ParseException {
    throw new RuntimeException("Should never call this method on superclass");
  }

  /**
   * Replacement for the getDeps() method of AOGOpTree. Defers to the input subtree.
   */
  public void getDeps(SymbolTable symtab, ArrayList<AOGOpTree> deps) throws ParseException {
    throw new RuntimeException("Should never call this method on superclass");
  }

  /**
   * Replacement for the toOpTree() method of AOGOpTree.
   * 
   * @param curNick one of the nicknames in the return list for the original AOG expression
   * @param symtab same as in AOGOpTree.toOpTree()
   * @param catalog TODO
   * @return operator subtree that corresponds to the indicated nickname
   */
  public Operator toOpTree(String curNick, SymbolTable symtab, Catalog catalog)
      throws ParseException {
    throw new RuntimeException("Should never call this method on superclass");
  }

  /**
   * This method removes the output nicks, which are not present in the symbol table.
   * 
   * @param symbTab unionized symbol table
   * @return list of output nicks removed
   */
  public List<String> removeUnusedOutputNicks(SymbolTable symbTab) {
    throw new UnsupportedOperationException("Should never call this method on supperclass");
  }

  /**
   * Fake placeholder parse tree node for a single output of a multi-output operator.
   */
  public static class PlaceholderOp extends AOGOpTree {

    /** The actual parse tree node returned by the parser. */
    private AOGMultiOpTree realNode;

    public PlaceholderOp(String moduleName, String opname, AOGMultiOpTree realNode, String nick) {
      super(moduleName, opname);
      this.realNode = realNode;
      super.setNickname(nick);
    }

    @Override
    public int dump(PrintWriter stream, int indent) throws ParseException {
      throw new IllegalArgumentException("Called dump() on a fake parse tree node.");
    }

    @Override
    public void getDeps(SymbolTable symtab, ArrayList<AOGOpTree> deps) throws ParseException {
      realNode.getDeps(symtab, deps);
    }

    @Override
    protected Operator toOpTree(SymbolTable symtab, Catalog catalog) throws ParseException {
      return realNode.toOpTree(super.getNickname(), symtab, catalog);
    }

    public AOGMultiOpTree getRealNode() {
      return this.realNode;
    }

  }

  /** Parse tree node for the multi-regextok operator. */
  public static class RegexesTokOp extends AOGMultiOpTree {

    private Token origTok;

    // Parameters that tell how to run the various regexes.
    private ArrayList<RegexesTokParams> regexParams;

    // Input column name
    private ColumnRef colName;

    private AOGOpTree inputOpTree;

    /**
     * The actual RegexesTok operator, which has multiple outputs. Index of outputs is the same as
     * indexes of {@code this.regexes}.
     */
    private RegexesTok output = null;

    public RegexesTokOp(String moduleName, Token t, ArrayList<RegexesTokParams> regexParams,
        ColumnRef colName, AOGOpTree input) {
      super(moduleName, AOGOpTree.getConst(AOGParserConstants.REGEXESTOK_OPNAME));
      this.origTok = t;
      this.regexParams = regexParams;
      this.colName = colName;
      this.inputOpTree = input;
    }

    /**
     * Copy constructor - this constructor is used to merge SRM nodes from different modules.
     * 
     * @param moduleNAme Name of the module that the parse tree node belongs to
     * @param givenRegexesTokOp RegexesTokOp object to be copied over to the newly created instance
     * @param outputNickName nick name for the given RegexesTokOp object
     */
    public RegexesTokOp(String moduleName, RegexesTokOp givenRegexesTokOp, String outputNickName) {
      this(moduleName, givenRegexesTokOp.origTok, givenRegexesTokOp.regexParams,
          givenRegexesTokOp.colName, givenRegexesTokOp.inputOpTree);

      if (null == this.outputNicks) {
        this.outputNicks = new ArrayList<String>();
      }
      this.outputNicks.add(outputNickName);
    }

    /**
     * Derives a RegexesTokOp instance based on the original RegexesTokOp node returned by AOG
     * Parser. The derived node corresponds to a single SRM output nick. Hence it would contain only
     * one outputNick and its corresponding regex parameter. <br/>
     * Note: This constructor is for internal use only.
     * 
     * @param moduleNAme Name of the module that the parse tree node belongs to
     * @param origRegexTokOp The original RegexesTokOp node returned by AOG Parser
     * @param index Index of the outputNick in PlaceholderOp
     * @param outputNickName output nick name of the SRM output node represented by the current
     *        instance of RegexesTokOp
     */
    private RegexesTokOp(String moduleName, RegexesTokOp origRegexTokOp, int index,
        String outputNickName) {
      // initialize the new RegexesTokOp instance with the given parameters
      this(moduleName, origRegexTokOp.origTok, getRegexTokParams(origRegexTokOp, index),
          origRegexTokOp.colName, origRegexTokOp.inputOpTree);

      // instantiate outputNicks if null
      if (null == this.outputNicks) {
        this.outputNicks = new ArrayList<String>();
      }

      // copy over outputNick if not already added
      if (false == this.outputNicks.contains(outputNickName)) {
        this.outputNicks.add(outputNickName);
      }
    }

    /**
     * Fetches the regexParam found at the specified index from the given RegexesTokOp node
     * 
     * @param origRegexTokOp The original RegexesTokOp node returned by AOG Parser
     * @param index The index of regexParam that should be retrieved from RegexesTokOp node
     * @return An ArrayList containing the regexParam found at the specified index from the given
     *         RegexesTokOp node
     */
    private static ArrayList<RegexesTokParams> getRegexTokParams(RegexesTokOp origRegexTokOp,
        int index) {
      ArrayList<RegexesTokParams> ret = new ArrayList<RegexesTokParams>();
      ret.add(origRegexTokOp.regexParams.get(index));
      return ret;
    }

    /**
     * Overrides the default implementation provided by the base class. This method splits the
     * original RegexesTokOp node (i.e SRM node) into a list of AOGOpTree nodes (i.e PlaceholderOp
     * nodes), one each for every output nick name of the RegexesTok node. During load time, the
     * TextAnalytics loader would merge related SRM output nicks & their regexParam objects. Refer
     * to {@link OperatorGraphImpl#mergeSharedNodes()} for details.
     */
    @Override
    public ArrayList<AOGOpTree> toParseTrees() {
      ArrayList<AOGOpTree> ret = new ArrayList<AOGOpTree>();

      // Create a PlaceholderOp for every outputNick found in RegexesTokOp
      // Split the RegexesTokOp such that each PlaceholderOp returned by this method corresponds to
      // the outputNick and
      // its related regexParam object
      for (int i = 0; i < outputNicks.size(); i++) {
        String outputNickName = outputNicks.get(i);

        // The regexesTok object created below using the special internal constructor contains
        // regexParams *ONLY* for
        // the specified outputNickName
        RegexesTokOp regexesTok = new RegexesTokOp(moduleName, this, i, outputNickName);
        ret.add(new PlaceholderOp(moduleName, opname, regexesTok, outputNicks.get(i)));
      }
      return ret;
    }

    /**
     * Replacement for the getDeps() method of AOGOpTree. Defers to the input subtree.
     */
    @Override
    public void getDeps(SymbolTable symtab, ArrayList<AOGOpTree> deps) throws ParseException {
      inputOpTree.getDeps(symtab, deps);
    }

    @Override
    public Operator toOpTree(String curNick, SymbolTable symtab, Catalog catalog)
        throws ParseException {
      if (null == output) {
        // Create the singleton operator instance.

        Operator input = inputOpTree.toOpTree(symtab, catalog);

        try {
          output = new RegexesTok(input, colName.getColName(), regexParams);
        } catch (com.ibm.systemt.regex.parse.ParseException e) {
          throw new ParseException("Error instantiating RegexesTok: " + e.getMessage());
        }
      }

      // If we get here, the output is ready. Figure out what index the
      // requested nickname corresponds to.
      int ix = nickToIndex(curNick);

      // Dictionary operator has one output per regular expression
      return output.getOutput(ix);
    }

    @Override
    public List<String> removeUnusedOutputNicks(SymbolTable symtab) {
      ArrayList<String> nicksToRemove = new ArrayList<String>();
      ArrayList<Integer> regexesIxToRemove = new ArrayList<Integer>();

      for (String nick : outputNicks) {
        if (symtab.containsNick(nick) == false) {
          int index = nickToIndex(nick);

          nicksToRemove.add(nick);
          // Fix for defect GHE #93: ModuleLoadException: ArrayIndexOutOfBoundsException in
          // AOGMultiOpTree$RegexesTokOp.toOpTree
          // Remember the indices of regexes to remove, to avoid over-removing those that appear
          // multiple
          // times, and contribute to the output at least once
          // regexesToRemove.add (regexParams.get (index));
          regexesIxToRemove.add(index);
        }
      }

      // Nicks are unique, so we can remove them with no extra checks
      outputNicks.removeAll(nicksToRemove);

      // Fix for defect GHE 93: ModuleLoadException: ArrayIndexOutOfBoundsException in
      // AOGMultiOpTree$RegexesTokOp.toOpTree
      // Regex params may not be unique, therefore we must be careful when removing them
      // When the same regex appears more than once in a SRM node, and some are marked for removal
      // because they are not
      // contributing to an output, we must be careful to not over-remove it
      // regexParams.removeAll (regexesToRemove);
      for (int ix = regexParams.size() - 1; ix >= 0; ix--) {
        if (regexesIxToRemove.contains(ix))
          regexParams.remove(ix);
      }

      return nicksToRemove;

    }

    @Override
    public int dump(PrintWriter stream, int indent) throws ParseException {
      // First the nicknames list.
      AOGOpTree.printIndent(stream, indent);
      stream.print("(");
      for (int i = 0; i < outputNicks.size(); i++) {
        stream.print("$" + outputNicks.get(i));
        if ((i + 1) < outputNicks.size()) {
          stream.print(", ");
        }
      }
      stream.print(") =\n");

      // Then the name of the top-level operator
      AOGOpTree.printIndent(stream, indent + 1);
      stream.printf("%s(\n", AOGOpTree.getConst(AOGParserConstants.REGEXESTOK_OPNAME));

      // Then the arguments.
      AOGOpTree.printIndent(stream, indent + 2);
      stream.print("(\n");

      for (int i = 0; i < regexParams.size(); i++) {
        RegexesTokParams p = regexParams.get(i);

        p.dump(stream, indent);

        if (i < regexParams.size() - 1) {
          stream.print(",\n");
        } else {
          stream.print("\n");
        }
      }

      AOGOpTree.printIndent(stream, indent + 2);
      stream.printf("),\n");

      AOGOpTree.printIndent(stream, indent + 2);
      stream.printf("%s,\n", colName);

      inputOpTree.dump(stream, indent + 2);

      // Then close the parens.
      // We're a top-level tree, so always add a semicolon.
      AOGOpTree.printIndent(stream, indent + 1);
      stream.print(");\n");

      // No operator inside RegexesTok()
      return 1;
    }

    /**
     * Method to add the regexes parameter from the given RegexesTokOp operator.
     * 
     * @param givenRegexesTokOp regexesTokOp object from which regexes parameter should be merged
     * @param outputNickName nick name for the given regexesTokOp object
     */
    public void mergeRegexesTokOps(RegexesTokOp givenRegexesTokOp, String outputNickName) {
      // process only when the outputNick is not already merged into this SRM node
      if (false == outputNicks.contains(outputNickName)) {

        outputNicks.add(outputNickName);

        /**
         * The code below fetches the regexparam at index 0, because we do not expect more than one
         * entry in the regexParams list. This is ensured by the the method
         * {@link RegexesTokOp#toParseTrees()} by splitting the original SRM node having more than
         * one outputNicks into multiple SRM nodes with exactly one outputNick and its corresponding
         * regexParam.
         */
        this.regexParams.add(givenRegexesTokOp.regexParams.get(0));

      }

    }
  }

}
