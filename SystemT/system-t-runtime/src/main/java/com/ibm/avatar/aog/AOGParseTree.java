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

import java.io.CharArrayWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeMap;

import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.datamodel.ExternalView;
import com.ibm.avatar.algebra.datamodel.Table;
import com.ibm.avatar.algebra.output.Sink;
import com.ibm.avatar.algebra.util.dict.CompiledDictionary;
import com.ibm.avatar.algebra.util.tokenize.TokenizerConfig;
import com.ibm.avatar.aql.catalog.Catalog;

/**
 * Class that encapsulates the parse tree for an AOG file.
 * 
 */
public class AOGParseTree {

  /**
   * Name of the built in view that represents the scan over the documents; only used inside the
   * parser.
   */
  @Deprecated
  public static final String DOC_SCAN_BUILTIN = "DocScan";

  /**
   * Name of the external type that represents the current document. Internally, scans over this
   * type are mapped to {@link #DOC_SCAN_BUILTIN}.
   */
  public static final String DOC_TYPE_NAME = "Document";

  /**
   * How many passes we make through the subtrees before giving up on conversion.
   */
  // private static final int MAX_NUM_CONVERSION_PASS = 100000;

  // public static boolean NICK_WARNING_ENABLED = true;

  /** The subtrees of this file, as returned to the parser. */
  private ArrayList<AOGParseTreeNode> origSubtrees = new ArrayList<AOGParseTreeNode>();

  /**
   * origSubtrees, with all instances of AOGMultiOpTree converted to AOGOpTrees.
   */
  private ArrayList<AOGOpTree> subtrees = null;

  /**
   * Symbol table for holding mappings between names and parse tree objects.
   */
  private SymbolTable symtab = new SymbolTable();

  /**
   * Output view nicknames in the AOG file.
   */
  private AOGOutputExpr outputs = new AOGOutputExpr();

  /**
   * Dictionaries defined inline. Stored in an ordered map to avoid screwing up regression test
   * results.
   */
  // @Deprecated
  // private TreeMap<String, DictFile> inlineDicts = new TreeMap<String,
  // DictFile>();

  /**
   * Callback for producing scans over precomputed annotations, or null if unrecognized nicknames
   * should just generate an error.
   */
  private AnnotationReaderFactory readerCallback = null;

  /**
   * AQL catalog populated by the parser in the process of parsing the AOG file.
   */
  private Catalog catalog;

  /**
   * Should we print any status messages while operating over this parse tree?
   */
  // private boolean writeStatus = true;
  @Deprecated
  public void setWriteStatus(boolean writeStatus) {
    // this.writeStatus = writeStatus;
  }

  /**
   * @param readerCallback Callback for producing scans over precomputed annotations; this callback
   *        is only necessary if the AOG file references such annotations.
   */
  public void setReaderCallback(AnnotationReaderFactory readerCallback) {
    this.readerCallback = readerCallback;
  }

  public void addSubtree(AOGParseTreeNode subtree) {
    origSubtrees.add(subtree);
  }

  /**
   * Add outputs to the parse tree; if we've already received an output clause, hangs the outputs
   * off the existing clause's parse tree node.
   * 
   * @param outputs parse tree node
   */
  public void addOutputs(AOGOutputExpr outputs) {
    if (null == this.outputs) {
      this.outputs = outputs;
    } else {
      // Already have an Output clause; add additional terms to it.
      for (String outputNick : outputs.getOutputs()) {
        this.outputs.addOutput(outputNick);
      }
    }
  }

  public ArrayList<AOGParseTreeNode> getSubtrees() {
    return origSubtrees;
  }

  public AOGOutputExpr getOutputs() {
    return outputs;
  }

  /**
   * @return the symbol table that maps names to parser objects
   */
  public SymbolTable getSymTab() {
    return symtab;
  }

  public int dump(PrintStream stream) throws ParseException {
    // Dump to a string first.
    CharArrayWriter cw = new CharArrayWriter();
    PrintWriter pw = new PrintWriter(cw);
    int ret = dump(pw);
    pw.flush();
    stream.append(new String(cw.toCharArray()));
    return ret;
  }

  public ArrayList<AOGOpTree> getConvertedSubTrees() {
    return subtrees;
  }

  /** Accessor method to create an AOGPlan with the input parameters */
  public static AOGPlan toPlan(Sink root, TreeMap<String, Operator> outputMap,
      TreeMap<String, Integer> indexMap, ArrayList<String> allNicks, TokenizerConfig tokenizerCfg) {
    return new AOGPlan(root, outputMap, indexMap, allNicks, tokenizerCfg);
  }

  /**
   * Convert any instances of AOGMultiOpTree into single AOGOpTrees.
   */
  public void convertSubtrees() {
    subtrees = new ArrayList<AOGOpTree>();

    for (AOGParseTreeNode node : origSubtrees) {
      if (node instanceof AOGMultiOpTree) {
        // Node has multiple arguments; generate a tree for each.
        AOGMultiOpTree mtnode = (AOGMultiOpTree) node;
        ArrayList<AOGOpTree> trees = mtnode.toParseTrees();
        for (AOGOpTree tree : trees) {
          subtrees.add(tree);
        }
      } else {
        // Node is a single tree
        subtrees.add((AOGOpTree) node);
      }
    }
  }

  /**
   * Augment the symbol table with the "nickname" table used in the later stages of conversion to
   * decode references to operator trees in the AOG file. Also adds the appropriate factories for
   * generating scan operators to the symbol table. Assumes that you've already generated
   * {@link #subtrees}.
   */
  // private void buildSymTab(DocScan docscan) throws AOGConversionException,
  public void buildSymTab() throws AOGConversionException, ParseException {

    for (AOGOpTree tree : subtrees) {
      String nick = tree.getNickname();
      symtab.addNick(nick, tree);
    }

    // Create a special internal view, __doc_scan, to represent a scan over
    // the documents. We currently need this internal view so that
    // dependency generation will go properly.
    // This code no longer necessary with document schema specified as an optree with
    // nickname "Document" -- eyhung
    // DocScanTreeStub docScanNode = new DocScanTreeStub(docscan);
    // symtab.addNick(DOC_SCAN_BUILTIN, docScanNode);
    // symtab.setDocScanTree(docScanNode);

    // The symbol table also holds a pointer to the factory object for
    // annotation scans; this pointer is used by the ScanOp parse tree
    // node to generate scan operators.
    symtab.setAnnotReaderFactory(readerCallback);

  }

  /**
   * Pretty-print the tree to a stream.
   * 
   * @throws ParseException
   */
  public int dump(PrintWriter writer) throws ParseException {

    int numops = 0;

    // We first dump lookup tables, dictionaries and external views. Unless we do so, parsing for
    // operator trees will
    // fail whenever encountering a dictionary.

    // First, then the lookup tables.
    for (Table table : symtab.getTables()) {
      table.dump(writer);
    }

    // Then the dictionary definitions.
    for (CompiledDictionary dict : symtab.getDicts()) {
      dict.dump(writer);
    }

    // Then the external views.
    for (ExternalView view : symtab.getExternalViews()) {
      view.dump(writer);
    }

    // Then the operator trees.
    for (Iterator<AOGParseTreeNode> iter = origSubtrees.iterator(); iter.hasNext();) {
      AOGParseTreeNode tree = iter.next();

      numops += tree.dump(writer, 0);

      // Add a blank line between entries.
      writer.print("\n");
    }

    // Then the outputs.
    outputs.dump(writer);

    return numops;
  }

  /**
   * @param catalog AQL catalog with information necessary to fully decode the elements of the parse
   *        tree
   */
  public void setCatalog(Catalog catalog) {
    this.catalog = catalog;
  }

  /**
   * @return AQL catalog containing additional information necessary to instantiate the operators in
   *         this parse tree.
   */
  public Catalog getCatalog() {
    return catalog;
  }

  /**
   * Add a new inline dictionary definition.
   * 
   * @param dict parse tree node for the inline def.
   */
  // @Deprecated
  // public void addInlineDict(DictFile dict) {
  // inlineDicts.put(dict.getName(), dict);
  // }

}
