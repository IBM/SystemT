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

import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.aql.compiler.ParseToCatalog;
import com.ibm.avatar.aql.doc.AQLDocComment;

/**
 * Top-level parse tree node for a
 * 
 * <pre>
 * create view
 * </pre>
 * 
 * statement.
 */
public class CreateViewNode extends TopLevelParseTreeNode {

  /** Name for the view. */
  protected NickNode viewname;

  /** The definition of the view */
  protected ViewBodyNode body;

  /** Does this view go to the output? */
  private boolean isOutput = false;

  /**
   * If this view is an output view the "output view" statement has an "as" clause, output name
   * specified in the "as" clause.
   */
  private String outputName = null;

  /** Location of the statement that made this view an output view. */
  private ErrorLocation whereOutputSet = null;

  /** The AQL Doc comment (if any) attached to this parse tree node. */
  private AQLDocComment comment;

  public CreateViewNode(NickNode viewname, ViewBodyNode body, String containingFileName,
      Token origTok) {
    // set error location info
    super(containingFileName, origTok);

    this.viewname = viewname;
    // this.colnames = colnames;
    this.body = body;
  }

  @Override
  public List<ParseException> validate(Catalog catalog) {
    List<ParseException> errors = new ArrayList<ParseException>();

    if (null != body) {
      List<ParseException> bodyErrors = body.validate(catalog);
      if (null != bodyErrors && bodyErrors.size() > 0)
        errors.addAll(bodyErrors);
    }

    return ParseToCatalog.makeWrapperException(errors, getContainingFileName());
  }

  /**
   * Mark this view as an output view
   * 
   * @param whereSet location in the AQL where the "output view" statement was found
   * @param isOutput
   * @param outputName optional output name, or null to use the view's fully-qualified view name
   */
  public void setIsOutput(ErrorLocation whereSet, boolean isOutput, String outputName) {
    this.isOutput = isOutput;
    this.whereOutputSet = whereSet;
    this.outputName = outputName;
  }

  public boolean getIsOutput() {
    return this.isOutput;
  }

  /**
   * @return If this view is an output view the "output view" statement has an "as" clause, output
   *         name specified in the "as" clause.
   */
  public String getOutputName() {
    return outputName;
  }

  /**
   * @return if this view is an output view, the location in an AQL file where this view was
   *         converted to an output view; otherwise, returns null
   */
  public ErrorLocation getWhereOutputSet() {
    return whereOutputSet;
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    printIndent(stream, indent);
    stream.print("create view ");
    // Use an indent of zero to get the view name printed inline.
    viewname.dump(stream, 0);
    stream.print(" as\n");
    printIndent(stream, indent + 1);
    body.dump(stream, indent + 1);
    stream.print(";");

    if (isOutput) {
      stream.print("\n\n");
      printIndent(stream, indent);
      stream.print("output view ");
      viewname.dump(stream, 0);
      stream.print(";");
    }

  }

  public String getViewName() {
    return prepareQualifiedName(viewname.getNickname());
  }

  public String getUnqualifiedName() {
    return viewname.getNickname();
  }

  public ViewBodyNode getBody() {
    return body;
  }

  public void setBody(ViewBodyNode body) {
    this.body = body;
  }

  @Override
  public int reallyCompareTo(AQLParseTreeNode o) {
    CreateViewNode other = (CreateViewNode) o;
    int val = viewname.compareTo(other.viewname);
    if (val != 0) {
      return val;
    }
    return body.compareTo(other.body);
  }

  /**
   * @return the names of all output columns of this view.
   */
  public ArrayList<String> getOutputColNames() {

    if (null == body) {
      throw new RuntimeException("No view body");
    }

    return getNamesForStmt(body);

  }

  private static ArrayList<String> getNamesForStmt(ViewBodyNode body) {
    if (body instanceof SelectNode) {
      return getNamesForSelect((SelectNode) body);

    } else if (body instanceof UnionAllNode) {

      UnionAllNode union = (UnionAllNode) body;

      // Get schema info from the first statement in the union.
      ViewBodyNode firstStmt = union.getStmt(0);

      return getNamesForStmt(firstStmt);

    } else if (body instanceof MinusNode) {

      MinusNode minus = (MinusNode) body;

      // For now, just use the first statement.
      return getNamesForStmt(minus.getFirstStmt());

    } else if (body instanceof ExtractNode) {

      ExtractNode extract = (ExtractNode) body;

      return getNamesForExtract(extract);

    } else if (body instanceof ExtractPatternNode) {

      ExtractPatternNode pattern = (ExtractPatternNode) body;

      return getNamesForPattern(pattern);

    } else {
      throw new RuntimeException("Don't know how to get column names for " + "view body " + body);
    }
  }

  private static ArrayList<String> getNamesForSelect(SelectNode select) {
    // Select statement; pull out the entries in the select list and
    // ask them for their column names.
    ArrayList<String> ret = new ArrayList<String>();

    SelectListNode list = select.getSelectList();
    for (int i = 0; i < list.size(); i++) {
      SelectListItemNode item = list.get(i);
      ret.add(item.getAlias());
      // item.getValue().getColName());
    }

    return ret;
  }

  private static ArrayList<String> getNamesForExtract(ExtractNode e) {
    ArrayList<String> ret = new ArrayList<String>();

    ExtractListNode list = e.getExtractList();

    // First (n-1) items are stored internally using the SelectListNode data
    // structure.
    SelectListNode slist = list.getSelectList();
    for (int i = 0; i < slist.size(); i++) {
      SelectListItemNode item = slist.get(i);
      ret.add(item.getAlias());
      // item.getValue().getColName());
    }

    // Last item is the extraction spec
    ExtractionNode extractSpec = list.getExtractSpec();
    for (NickNode outputName : extractSpec.getOutputCols()) {
      ret.add(outputName.getNickname());
    }

    return ret;
  }

  private static ArrayList<String> getNamesForPattern(ExtractPatternNode pattern) {
    ArrayList<String> ret = new ArrayList<String>();

    for (NickNode outputName : pattern.getReturnClause().getAllNames()) {
      ret.add(outputName.getNickname());
    }

    return ret;
  }

  public NickNode getViewNameNode() {
    return viewname;
  }

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

  @Override
  public void qualifyReferences(Catalog catalog) throws ParseException {
    body.qualifyReferences(catalog);
  }

}
