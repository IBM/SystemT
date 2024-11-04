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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.ibm.avatar.aog.ColumnRef;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.systemt.regex.api.SimpleRegex;
import com.ibm.systemt.util.regex.FlagsString;
import com.ibm.systemt.util.regex.RegexesTokParams;

/** Parse tree node for a regular expression extraction specification. */
public class RegexExNode extends ExtractionNode {

  private final ArrayList<RegexNode> regexes;

  /** String that describes the flags to pass to the regex engine. */
  private StringNode flagsStr;

  /**
   * Number of tokens to match, if using RegexTok; null for character-based regex.
   */
  private final IntNode minTok;
  private final IntNode maxTok;

  /**
   * Set of flags that indicate whether to use the {@link SimpleRegex} engine for each regex in
   * {@link #regexes}.
   */
  private final boolean[] useSimpleEngine;

  /**
   * The return clause of the extract regex statement; specifies which capturing groups to return
   * and what to call them.
   */
  private final ReturnClauseNode returnClause;

  public RegexExNode(ArrayList<RegexNode> regexes, StringNode flagsStr, IntNode minTok,
      IntNode maxTok, ColNameNode targetName, ReturnClauseNode returnClause,
      String containingFileName, Token origTok) throws ParseException {
    super(targetName, returnClause.getAllNames(), containingFileName, origTok);

    this.regexes = regexes;

    if (flagsStr != null)
      this.flagsStr = flagsStr;
    else
      // null flag string gets defaulted to DOTALL
      this.flagsStr = new StringNode(FlagsString.DEFAULT_FLAGS);

    this.minTok = minTok;
    this.maxTok = maxTok;
    this.returnClause = returnClause;

    // The Preprocessor will figure out whether to use the SimpleRegex
    // engine...
    this.useSimpleEngine = new boolean[regexes.size()];
    Arrays.fill(useSimpleEngine, false);

  }

  @Override
  public List<ParseException> validate(Catalog catalog) {
    List<ParseException> errors = new ArrayList<ParseException>();

    // Fix for part (b) of defect
    // Each regex must define every group identified in the return clause. Else, add a
    // ParseException for each such
    // discrepancy
    for (int rcGrpIx = 0; rcGrpIx < getNumGroups(); ++rcGrpIx) {
      for (int regexIx = 0; regexIx < getNumRegexes(); ++regexIx) {
        if (getGroupID(rcGrpIx) > getRegex(regexIx).getNumberOfGroups()) {
          ParseException pe = new ParseException("Regular expression #" + (regexIx + 1)
              + " [left to right] doesn't contain a group with ID #" + getGroupID(rcGrpIx));
          pe.currentToken = getOrigTok();
          errors.add(pe);
        }
      }
    }

    if (null != this.returnClause) {
      errors.addAll(this.returnClause.validate(catalog));
    }

    // Do some sanity checks on the original AQL
    if (null != minTok && maxTok.getValue() < minTok.getValue()) {
      errors.add(
          new ParseException(String.format("Minimum number of tokens (%d) greater than max (%d)",
              minTok.getValue(), maxTok.getValue())));
    }

    if (null != minTok && minTok.getValue() < 1) {
      errors.add(new ParseException(String.format(
          "Invalid minimum number of tokens %d; " + "must be at least 1", minTok.getValue())));
    }

    return errors;
  }

  public int getNumRegexes() {
    return regexes.size();
  }

  public RegexNode getRegex(int ix) {
    return regexes.get(ix);
  }

  /**
   * Return our internal set of parameters into the convenient packed format that is used during the
   * Shared Regex Matching (SRM) transformation.
   */
  public RegexesTokParams getRegexTokParams(int ix) {
    if (false == getUseRegexTok()) {
      throw new RuntimeException(
          "Tried to get RegexTok params " + "for a regex that is NOT matched on token boundaries.");
    }

    if (false == getUseSimpleEngine(ix)) {
      throw new RuntimeException("Tried to get RegexesTok params for a"
          + " regex that is not compatible with the SimpleRegex engine.");
    }

    // Since we don't do groups and SRM at the same time, there must be
    // exactly one regex output column.
    NickNode outputCol = super.getOutputCols().get(0);

    try {
      return new RegexesTokParams(regexes.get(ix), new ColumnRef(outputCol.getNickname()),
          getFlagsStr().getStr(), minTok.getValue(), maxTok.getValue());
    } catch (com.ibm.avatar.aog.ParseException e) {
      throw new RuntimeException("Error generating regex params for Shared Regex Matching");
    }

  }

  public IntNode getMaxTok() {
    return maxTok;
  }

  public IntNode getMinTok() {
    return minTok;
  }

  public int getGroupID(int ix) {
    return returnClause.getGroupID(ix);
  }

  public String getGroupName(int ix) {
    return returnClause.getName(ix);
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    stream.print(1 == regexes.size() ? "regex " : "regexes ");
    for (int i = 0; i < regexes.size(); i++) {
      if (i > 0) {
        printIndent(stream, indent);
        stream.print("and ");
      }
      regexes.get(i).dump(stream, 0);
      stream.print("\n");
    }

    // flagsStr is optional. Check for null before calling dump() method
    if (flagsStr != null) {
      printIndent(stream, indent);
      stream.print("with flags ");
      flagsStr.dump(stream, 0);
      stream.print("\n");
    }

    printIndent(stream, indent);
    if (null == minTok) {
      stream.printf("on %s\n", super.getTargetName().getColName());
    } else {
      stream.printf("on between %d and %d tokens in %s\n", minTok.getValue(), maxTok.getValue(),
          super.getTargetName().getColName());
    }

    printIndent(stream, indent);
    stream.print("return ");
    for (int i = 0; i < returnClause.size(); i++) {
      if (i > 0) {
        printIndent(stream, indent + 1);
        stream.print(" and ");
      }
      stream.printf("group %d as ", returnClause.getGroupID(i));
      getOutputCols().get(i).dump(stream, 0);
      stream.print("\n");
    }
  }

  @Override
  protected int reallyCompareTo(AQLParseTreeNode o) {
    RegexExNode other = (RegexExNode) o;

    int val = super.compareTarget(other);
    if (0 != val) {
      return val;
    }

    val = regexes.size() - other.regexes.size();
    if (0 != val) {
      return val;
    }
    for (int i = 0; i < regexes.size(); i++) {
      val = regexes.get(i).compareTo(other.regexes.get(i));
    }
    return val;
  }

  /** @return integer version of the flags this regular expression will use. */
  public int getFlags() throws ParseException {
    // Flags come in as a string.
    int flags;
    try {
      flags = FlagsString.decode(null, flagsStr.getStr());
    } catch (FunctionCallValidationException e) {
      throw AQLParserBase.makeException(e.getMessage(), flagsStr.getOrigTok());
    }
    return flags;
  }

  public StringNode getFlagsStr() {
    return flagsStr;
  }

  public void setUseSimpleEngine(int ix, boolean useSimpleEngine) {
    this.useSimpleEngine[ix] = useSimpleEngine;
  }

  /**
   * @param ix index of one of the regular expressions in this extraction
   * @return true if this regular expression should be evaluated using the SimpleRegex engine; false
   *         to use the Java regex engine.
   */
  public boolean getUseSimpleEngine(int ix) {
    return useSimpleEngine[ix];
  }

  /**
   * @return true if this regular expression should be evaluated with the RegexTok operator; false
   *         to use the character-based Regex operator.
   */
  public boolean getUseRegexTok() {
    return (null != maxTok);
  }

  @Override
  public int getNumInputCopies() {
    return getNumRegexes();
  }

  public int getNumGroups() {
    return returnClause.size();
  }

  @Override
  public void qualifyReferences(Catalog catalog) {
    // No action
  }
}
