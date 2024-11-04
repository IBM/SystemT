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
import java.util.List;

import com.ibm.avatar.algebra.extract.Detag;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.aog.AOGMultiOpTree;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.aql.doc.AQLDocComment;

/**
 * Top-level parse tree node for a
 * 
 * <pre>
 * detag into
 * </pre>
 * 
 * statement.
 */
public class DetagDocNode extends TopLevelParseTreeNode {

  /** Original document input */
  protected ColNameNode target;

  public ColNameNode getTarget() {
    return target;
  }

  /** Nick node of the detagged document view name. */
  protected NickNode detaggedDocName;

  /** True if the detagged documents view should be set as an output. */
  private final boolean outputDetaggedDocs;

  /** The AQL Doc comment (if any) attached to this parse tree node. */
  private AQLDocComment comment;

  public final void setDetagViewToken(Token token) {
    this.origTok = token;
  }

  /**
   * True if the detagger should test whether the document is actually HTML/XML before attempting to
   * detag.
   */
  private final boolean detectContentType;

  public boolean getOutputDetaggedDocs() {
    return outputDetaggedDocs;
  }

  /** Entries of detagger specifications */
  protected ArrayList<DetagDocSpecNode> entries;

  public ArrayList<DetagDocSpecNode> getEntries() {
    return entries;
  }

  /**
   * @return the entry for the indicated output of this detag statement, or null if no entry by that
   *         name is found
   */
  public DetagDocSpecNode getEntryByName(String outputName) {
    for (DetagDocSpecNode node : entries) {
      if (node.getTagType().getNickname().equals(outputName)) {
        return node;
      }
    }

    return null;
  }

  /**
   * Names of all outputs of this detag statement that are currently global outputs.
   */
  protected ArrayList<NickNode> outputNames;

  /**
   * Set the AQL Doc comment attached to this statement.
   * 
   * @param comment the AQL doc comment attached to this statement
   */
  public void setComment(AQLDocComment comment) {
    this.comment = comment;
  }

  /**
   * Get the AQL Doc comment attached to this node.
   * 
   * @return the AQL Doc comment attached to this node; null if this node does not have an AQL Doc
   *         comment
   */
  public AQLDocComment getComment() {
    return comment;
  }

  /**
   * Main constructor.
   * 
   * @param target column containing HTML to detag
   * @param detaggedDocName name of the view into which the detagged document text will go
   * @param entries nodes that describe additional tag information to generate during detagging
   * @param outputDetaggedDocs true to make the view for the detagged documents an output
   * @param detectContentType true to detect whether the document is actually HTML before attempting
   *        to detag it.
   */
  public DetagDocNode(ColNameNode target, NickNode detaggedDocName,
      ArrayList<DetagDocSpecNode> entries, boolean outputDetaggedDocs, boolean detectContentType,
      String containingFileName, Token origTok) {
    // set error location info
    super(containingFileName, origTok);

    this.target = target;
    this.detaggedDocName = detaggedDocName;
    this.entries = entries;
    this.outputDetaggedDocs = outputDetaggedDocs;
    this.detectContentType = detectContentType;

  }

  @Override
  public List<ParseException> validate(Catalog catalog) {
    return super.validate(catalog);
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.avatar.aql.Node#setState(com.ibm.avatar.aql.catalog.Catalog)
   */
  @Override
  public void setState(Catalog catalog) throws ParseException {
    // Set up which outputs of the statement will be externalized by
    // default; currently only the detagged docs may be.
    outputNames = new ArrayList<NickNode>();
    if (outputDetaggedDocs) {
      outputNames.add(detaggedDocName);
    }
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    printIndent(stream, indent);
    stream.printf("detag %s %s %s \n", target.toString(), outputDetaggedDocs ? "into" : "as",
        detaggedDocName.getNickname());

    // DETECT CONTENT_TYPE clause
    stream.printf("detect content_type %s\n", detectContentType ? "always" : "never");

    if (entries.size() > 0) {
      printIndent(stream, indent + 1);
      stream.print("annotate\n");

      for (int i = 0; i < entries.size(); i++) {
        entries.get(i).dump(stream, indent + 2);
        if (i != entries.size() - 1) {
          stream.print(",\n");
        }
      }
    }

    stream.print(";");
  }

  /** Generate the AOG representation of this detag statement. */
  public void toAOG(PrintWriter stream, int indent) throws ParseException {
    printIndent(stream, indent);
    stream.printf("(");
    stream.printf(StringUtils.toAOGNick(getDetaggedDocName()));

    for (int i = 0; i < entries.size(); i++) {
      printIndent(stream, indent + 1);
      stream.printf(", %s", StringUtils.toAOGNick(entries.get(i).getTagType().getNickname()));
      stream.print("\n");
    }
    stream.printf(") = \n");
    stream.printf("Detag(");

    for (DetagDocSpecNode entry : entries) {

      printIndent(stream, indent + 1);
      stream.printf("(\n");

      printIndent(stream, indent + 2);
      stream.printf("\"%s\",", entry.getTag().getStr());

      printIndent(stream, indent + 2);
      stream.printf("(\n");

      // First entry in the tag type mapping is the name of the output
      // type for the tag.
      printIndent(stream, indent + 3);
      stream.printf("\"%s\" => \"%s\",\n", AOGMultiOpTree.DeTagOp.TAG_SCHEMA_NAME,
          entry.getTagType().getNickname());

      // Remaining entries specify which fields to retain and what to call
      // them.
      for (int i = 0; i < entry.getNumAttrs(); i++) {
        printIndent(stream, indent + 3);
        stream.printf("\"%s\" => \"%s\"", entry.getAttr(i).getStr(),
            entry.getAttrLabel(i).getNickname());

        if (i == entry.getNumAttrs() - 1) {
          stream.printf("\n");
        } else {
          stream.printf(",\n");
        }
      }

      printIndent(stream, indent + 2);
      stream.printf(")\n");

      printIndent(stream, indent + 1);
      stream.printf("),\n");
    }

    printIndent(stream, indent + 1);
    stream.printf("%s, %s, \"%s\", %s\n", StringUtils.quoteStr('"', target.getColnameInTable()),
        StringUtils.quoteStr('"', getDetaggedDocName()), detectContentType ? "true" : "false",
        StringUtils.toAOGNick(target.getTabname()));

    printIndent(stream, indent);
    stream.printf(");\n\n");

    // Output statement is now handled by the Planner.
    // if (outputNames.size() > 0) {
    // printIndent(stream, indent);
    // stream.printf("Output: ");
    // for (int i = 0; i < outputNames.size(); i++) {
    //
    // Log.debug("Listing '%s' as an output.", outputNames.get(i)
    // .getNickname());
    //
    // stream.printf("$%s", outputNames.get(i).getNickname());
    //
    // if (i < outputNames.size() - 1) {
    // stream.print(",");
    // } else {
    // stream.print(";");
    // }
    // }
    // }
  }

  public String getDetaggedDocName() {
    return prepareQualifiedName(detaggedDocName.getNickname());
  }

  /**
   * @return the nick node associated with the detagged document view
   */
  public NickNode getDetaggedDocNameNode() {
    return detaggedDocName;
  }

  /**
   * Returns the DetaggedDocName without module prefix
   * 
   * @return unqualified DetaggedDocName
   */
  public String getUnqualifiedDetaggedDocName() {
    return detaggedDocName.getNickname();
  }

  @Override
  public int reallyCompareTo(AQLParseTreeNode o) {
    DetagDocNode other = (DetagDocNode) o;
    return detaggedDocName.compareTo(other.detaggedDocName);
  }

  /**
   * @return names of all other views that this detag statement depends on directly.
   */
  public ArrayList<String> getDeps() {
    // Only input to a detag statement is the view to be detagged.
    ArrayList<String> ret = new ArrayList<String>();
    ret.add(target.getTabname());
    return ret;
  }

  /**
   * Enable sending one of the tag outputs of the detag operator to the global AQL output.
   */
  public void enableOutput(NickNode type) throws ParseException {
    if (outputNames.contains(type)) {
      throw AQLParser.makeException(String.format("Enabled output '%s' twice", type.getNickname()),
          type.getOrigTok());
    }
    outputNames.add(type);
  }

  /**
   * This method is mainly for the use of the AQL Catalog.
   * 
   * @return the columns that will be present in the tuples of the indicated output of this detag
   *         statement
   * @throws ParseException if the indicated output doesn't exist
   */
  public ArrayList<String> getOutputCols(String outputName) throws ParseException {
    ArrayList<String> ret = new ArrayList<String>();

    if (outputName.equals(getDetaggedDocName())) {
      // The detagged document currently has a single column with a fixed
      // name.
      ret.add(Detag.DOC_COL_NAME);
    } else {

      DetagDocSpecNode spec = getEntryByName(outputName);

      if (null == spec) {
        throw AQLParser.makeException(
            String.format("Detag statement has no output named %s", outputName),
            target.getTabnameTok());
      }

      // First n columns are any auxiliary attributes of the tag.
      for (int i = 0; i < spec.getNumAttrs(); i++) {
        ret.add(spec.getAttrLabel(i).getNickname());
        // ret.add(spec.getAttr(i).getStr());
      }

      // Last column is the text inside of the tag. The name of this
      // output is currently hard-coded.
      ret.add(Detag.OUTPUT_COL_NAME);
    }
    return ret;

  }

  @Override
  public void qualifyReferences(Catalog catalog) {
    // Local target view(view belonging to the module under compilation) can be referred through
    // their unqualified
    // names; qualify them.
    if (null != this.target) {
      this.target.qualifyReferences(catalog);
    }
  }

}
