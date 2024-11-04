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
package com.ibm.avatar.aql.planner;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.TreeSet;

import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.aog.AOGOpTree;
import com.ibm.avatar.aog.AOGParserConstants;
import com.ibm.avatar.aql.BlockExNode;
import com.ibm.avatar.aql.ColNameNode;
import com.ibm.avatar.aql.DictExNode;
import com.ibm.avatar.aql.ExtractListNode;
import com.ibm.avatar.aql.ExtractNode;
import com.ibm.avatar.aql.ExtractionNode;
import com.ibm.avatar.aql.FromListItemNode;
import com.ibm.avatar.aql.FromListItemViewRefNode;
import com.ibm.avatar.aql.NickNode;
import com.ibm.avatar.aql.POSExNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.PredicateNode;
import com.ibm.avatar.aql.RegexExNode;
import com.ibm.avatar.aql.SplitExNode;
import com.ibm.avatar.aql.catalog.Catalog;

/**
 * Generic plan node for an extraction, implemented bottom-up (e.g. without RSE).
 */
public class ExtractPlanNode extends PlanNode {

  /**
   * Original AQL parse tree node for the extraction
   */
  private final ExtractNode extractNode;

  /**
   * Parse tree node for the extract list
   */
  private final ExtractListNode extractList;

  /**
   * The original view that this extraction is over (used during plan rewriting for SDM)
   */
  private final FromListItemViewRefNode targetView;

  // private boolean useQualifiedTargetName;

  /**
   * If the extract clause specifies multiple regexes or dictionaries, the internal implementation
   * splits the input into multiple copies -- one per regex or dictionary -- and generates an
   * ExtractPlanNode for each copy. This variable holds the index of the regex/dict that this
   * particular ExtractPlanNode implements.
   */
  private final int extractSpecIx;

  public int getExtractSpecIx() {
    return extractSpecIx;
  }

  /**
   * @param e description of the extraction to perform
   * @param child root of input plan tree
   * @param inputIx which copy of the input (if there are multiple copies used) this extraction runs
   *        over
   * @param useQualifiedTargetName true if the target column has a qualified name ("table.column");
   *        false to use an unqualified name ("column")
   */
  public ExtractPlanNode(ExtractNode e, PlanNode child, int inputIx) {
    super(child);
    this.extractNode = e;
    this.extractList = e.getExtractList();
    // this.targetView = e.getTarget();
    this.extractSpecIx = inputIx;
    // this.useQualifiedTargetName = useQualifiedTargetName;

    // if (false == targetView.isViewRef()) {
    if (!(e.getTarget() instanceof FromListItemViewRefNode)) {
      throw new RuntimeException("Target of an extraction" + " should always be a view reference");
    }

    this.targetView = (FromListItemViewRefNode) e.getTarget();
  }

  @Override
  public PlanNode deepCopyImpl() throws ParseException {
    return new ExtractPlanNode(extractNode, child().deepCopy(), extractSpecIx);
  }

  public ExtractListNode getExtractList() {
    return extractList;
  }

  public FromListItemNode getTargetView() {
    return targetView;
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    printIndent(stream, indent);
    stream.printf("ExtractPlanNode\n");

    printIndent(stream, indent + 1);
    stream.printf("Extraction: Element %d of:\n", extractSpecIx);

    extractList.dump(stream, indent + 2);

    stream.print("\n");

    printIndent(stream, indent + 1);
    stream.printf("Child:\n");
    child().dump(stream, indent + 2);
  }

  @Override
  public void getPreds(TreeSet<PredicateNode> preds) {
    // Currently, extract nodes don't apply any predicates themselves.
    child().getPreds(preds);
  }

  @Override
  public void getRels(TreeSet<FromListItemNode> rels) {
    child().getRels(rels);
  }

  @Override
  public void toAOGNoRename(PrintWriter stream, int indent, Catalog catalog) throws Exception {

    ExtractionNode extract = extractList.getExtractSpec();

    if (extract instanceof RegexExNode) {
      // Regular expression extraction
      genRegexAOG(stream, indent, extract, catalog);
    } else if (extract instanceof DictExNode) {
      // Dictionary extraction
      genDictAOG(stream, indent, extract, catalog);
    } else if (extract instanceof SplitExNode) {
      // Splitting a span into subspans
      genSplitAOG(stream, indent, extract, catalog);
    } else if (extract instanceof BlockExNode) {
      // Finding blocks of spans
      genBlockAOG(stream, indent, extract, catalog);
    } else if (extract instanceof POSExNode) {
      // Part of speech extraction
      genPartOfSpeechAOG(stream, indent, extract, catalog);
    } else {
      throw new ParseException("Don't know operator for " + extract);
    }

  }

  /**
   * @return the internal AOG name of the column on which to perform extraction.
   */
  private String inputColName(ExtractionNode node) {
    String inputColName = node.getTargetName().getColName();
    return inputColName;
  }

  /**
   * Generate the AOG operator spec for a regular expression extraction
   * 
   * @param stream output strem to write AOG to
   * @param indent how far to indent printed AOG
   * @param extract extraction spec
   * @param catalog AQL catalog ptr for looking up function metadata
   */
  private void genRegexAOG(PrintWriter stream, int indent, ExtractionNode extract, Catalog catalog)
      throws Exception {
    RegexExNode ren = (RegexExNode) extract;

    String opName;
    // There are currently four ways to evaluate a regular expression:
    if (ren.getUseRegexTok()) {
      if (ren.getUseSimpleEngine(extractSpecIx)) {
        // RegexTok() operator, SimpleRegex engine
        opName = AOGOpTree.getConst(AOGParserConstants.FASTREGEXTOK_OPNAME);
        // System.err.printf("Using SimpleRegex engine for %s\n", ren
        // .getRegex(inputIx));
      } else {
        // RegexTok() operator, Java regex engine
        opName = AOGOpTree.getConst(AOGParserConstants.REGEXTOK_OPNAME);
        // System.err.printf("NOT using SimpleRegex engine for %s\n",
        // ren
        // .getRegex(inputIx));
      }
    } else {
      if (ren.getUseSimpleEngine(extractSpecIx)) {
        // Regex() operator, SimpleRegex engine
        opName = AOGOpTree.getConst(AOGParserConstants.FAST_REGEX_OPNAME);
      } else {
        // Regex() operator, Java regex engine
        opName = AOGOpTree.getConst(AOGParserConstants.REGEX_OPNAME);
      }
    }

    // Pattern, quoted perl-style with forward slashes.
    String pattern = ren.getRegex(extractSpecIx).getPerlRegexStr();

    // Generate the string that specifies which groups to get.

    StringBuilder sb = new StringBuilder();
    sb.append("(");
    for (int i = 0; i < ren.getOutputCols().size(); i++) {
      int group = ren.getGroupID(i);
      String outputCol = ren.getOutputCols().get(i).getNickname();

      sb.append(String.format("%d => \"%s\"", group, outputCol));
      if (i < ren.getOutputCols().size() - 1) {
        sb.append(", ");
      }
    }
    sb.append(")");
    String groupStr = sb.toString();

    printIndent(stream, indent);

    // Generate strings for optional parts of the spec, if required.

    // Flags string
    String flagsStr = "";
    if (null != ren.getFlagsStr()) {
      flagsStr = String.format("\"%s\", ", ren.getFlagsStr().getStr());
    }

    // Number of tokens (for RegexTok and FastRegexTok)
    String toksStr = "";
    if (ren.getUseRegexTok()) {
      toksStr = String.format("%d, %d,", ren.getMinTok().getValue(), ren.getMaxTok().getValue());
    }

    String firstAOGLine = String.format("%s(%s, %s, %s%s\"%s\",\n", opName, pattern, groupStr,
        flagsStr, toksStr, inputColName(ren));

    // System.err.printf("First line of AOG is: %s", firstAOGLine);

    stream.print(firstAOGLine);

    child().toAOG(stream, indent + 1, catalog);

    stream.print("\n");
    printIndent(stream, indent);
    stream.printf(")");
  }

  /**
   * Generate the AOG operator spec for a dictionary extraction
   * 
   * @param stream output stream to write AOG to
   * @param indent how far to indent printed AOG
   * @param extract extraction spec
   * @param catalog pointer to AQL catalog for metadata lookup
   */
  private void genDictAOG(PrintWriter stream, int indent, ExtractionNode extract, Catalog catalog)
      throws Exception {

    DictExNode den = (DictExNode) extract;

    // Generate strings for optional parts of the spec, if required.

    // Flags string
    String flagsStr = "";
    if (null != den.getFlagsStr()) {
      flagsStr = String.format(" => \"%s\" ", den.getFlagsStr().getStr());
    }

    printIndent(stream, indent);
    stream.printf("Dictionary(\"%s\"%s, \"%s\", \"%s\",\n", den.getDictName(extractSpecIx).getStr(),
        flagsStr, inputColName(den), den.getOutputCols().get(0).getNickname());

    child().toAOG(stream, indent + 1, catalog);

    stream.print("\n");
    printIndent(stream, indent);
    stream.print(")");
  }

  /**
   * Generate the AOG operator spec for a Split operator.
   * 
   * @param stream output strem to write AOG to
   * @param indent how far to indent printed AOG
   * @param extract extraction spec
   * @param catalog pointer to AQL catalog for metadata lookup
   */
  private void genSplitAOG(PrintWriter stream, int indent, ExtractionNode extract, Catalog catalog)
      throws Exception {

    SplitExNode s = (SplitExNode) extract;

    // Strip table names off the column names if we're accessing the output
    // of the input view directly without renaming.
    String splitColName = s.getSplitCol().getColName();

    printIndent(stream, indent);
    stream.printf("Split(\"%s\", \"%s\", %d, \"%s\",\n", inputColName(s), splitColName,
        s.getSplitFlags(), s.getOutputCols().get(0).getNickname());

    child().toAOG(stream, indent + 1, catalog);

    stream.print("\n");
    printIndent(stream, indent);
    stream.print(")");
  }

  /**
   * Generate the AOG operator spec for a Block operator.
   * 
   * @param stream output strem to write AOG to
   * @param indent how far to indent printed AOG
   * @param extract extraction spec
   * @param catalog pointer to AQL catalog for metadata lookup
   */
  private void genBlockAOG(PrintWriter stream, int indent, ExtractionNode extract, Catalog catalog)
      throws Exception {

    BlockExNode b = (BlockExNode) extract;

    // Internally, we use different operators for blocks with token and
    // character distances.
    String operatorName = b.getUseTokenDist() ? "BlockTok" : "Block";

    printIndent(stream, indent);
    stream.printf("%s(%d, %d, %d, \"%s\", \"%s\",\n", operatorName, b.getMaxSep().getValue(),
        b.getMinSize().getValue(), b.getMaxSize().getValue(), inputColName(b),
        b.getOutputCols().get(0).getNickname());

    child().toAOG(stream, indent + 1, catalog);

    stream.print("\n");
    printIndent(stream, indent);
    stream.print(")");
  }

  /**
   * Generate the AOG operator spec for a part of speech extraction
   * 
   * @param stream output stream to write AOG to
   * @param indent how far to indent printed AOG
   * @param extract extraction spec
   * @param catalog pointer to AQL catalog for metadata lookup
   */
  private void genPartOfSpeechAOG(PrintWriter stream, int indent, ExtractionNode extract,
      Catalog catalog) throws Exception {

    POSExNode pen = (POSExNode) extract;

    // Get the list of parts of speech to extract.
    ArrayList<String> tags = new ArrayList<String>();
    for (int i = 0; i < pen.getNumTags(); i++) {
      tags.add(pen.getPosStr(i));
    }
    String tagsStr = StringUtils.join(tags, ",");

    printIndent(stream, indent);
    stream.printf("PartOfSpeech(%s, %s, %s, %s,\n", StringUtils.quoteStr('"', inputColName(pen)),
        StringUtils.quoteStr('"', pen.getLanguage().toString()), StringUtils.quoteStr('"', tagsStr),
        StringUtils.quoteStr('"', pen.getOutputCol().getNickname()));

    child().toAOG(stream, indent + 1, catalog);

    stream.print("\n");
    printIndent(stream, indent);
    stream.print(")");
  }

  /**
   * @return the name of the target table and column in global scope, as opposed to inside the view
   *         that contains this extract
   */
  public ColNameNode getGlobalTargetName() {
    String viewName = targetView.getOrigViewName().getNickname();
    String colName = extractList.getExtractSpec().getTargetName().getColnameInTable();
    return new ColNameNode(new NickNode(viewName), new NickNode(colName));
  }
}
