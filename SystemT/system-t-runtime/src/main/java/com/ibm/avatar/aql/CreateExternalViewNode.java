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

import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.aql.doc.AQLDocComment;

/**
 * Top-level parse tree node for a
 * 
 * <pre>
 * create external view
 * </pre>
 * 
 * statement.
 */
public class CreateExternalViewNode extends TopLevelParseTreeNode {

  /** The name of the external view. */
  NickNode viewName;

  /** The external name of the external view. */
  StringNode externalName;

  /** The names of the columns in the external view's schema. */
  ArrayList<NickNode> colNames;

  /** Types of the columns in the external view's schema. */
  ArrayList<NickNode> colTypes;

  /** The AQL Doc comment (if any) attached to this parse tree node. */
  private AQLDocComment comment;

  /** Does this view go to the output? */
  private boolean isOutput = false;

  /**
   * If this view is an output view the "output view" statement has an "as" clause, output name
   * specified in the "as" clause.
   */
  private String outputName = null;

  /** Location of the statement that made this view an output view. */
  private ErrorLocation whereOutputSet = null;

  public CreateExternalViewNode(NickNode viewName, StringNode externalName,
      ArrayList<NickNode> colNames, ArrayList<NickNode> colTypes, String containingFileName,
      Token origTok) {
    // set error location info
    super(containingFileName, origTok);

    this.viewName = viewName;
    this.externalName = externalName;
    this.colNames = colNames;
    this.colTypes = colTypes;
  }

  @Override
  public List<ParseException> validate(Catalog catalog) {
    return super.validate(catalog);
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

  /**
   * Pretty-print the contents of the node; should output valid AQL with identical semantics to the
   * original AQL that the parser consumed.
   */
  @Override
  public void dump(PrintWriter stream, int indent) {
    printIndent(stream, indent);
    stream.printf("create external view ");
    // Use an indent of zero to get the view name printed inline.
    viewName.dump(stream, 0);

    printIndent(stream, indent);
    stream.print("(\n");
    for (int i = 0; i < colNames.size(); i++) {
      printIndent(stream, indent + 1);

      // colNames nickNode might require escaping -- use dump() method
      colNames.get(i).dump(stream, 0);

      stream.printf(" %s", colTypes.get(i).getNickname());
      if (i == colNames.size() - 1) {
        stream.print("\n");
      } else {
        stream.print(",\n");
      }
    }
    stream.print(")\n");

    printIndent(stream, indent);
    stream.printf("external_name ");
    // Use an indent of zero to get the view name printed inline.
    externalName.dump(stream, 0);

    stream.print(";");
  }

  /** Generate AOG inline external view spec. */
  public void toAOG(PrintWriter stream, int indent) throws ParseException {

    // First, generate the CreateExternalView() AOG statement.

    // First argument is external view name
    // Second argument is the external name of the external view
    printIndent(stream, indent);
    stream.printf("CreateExternalView(%s,\n%s,\n", StringUtils.quoteStr('"', getExternalViewName()),
        StringUtils.quoteStr('"', getExternalName()));

    printIndent(stream, indent);

    // Third argument is schema
    printIndent(stream, indent + 1);
    stream.print("(\n");

    for (int i = 0; i < colNames.size(); i++) {
      printIndent(stream, indent + 2);
      stream.printf("\"%s\" => \"%s\"", colNames.get(i).getNickname(),
          colTypes.get(i).getNickname());
      if (i == colNames.size() - 1) {
        stream.print("\n");
      } else {
        stream.print(",\n");
      }
    }

    // Close parens on schema
    printIndent(stream, indent + 1);
    stream.print(")\n");

    // Close paren on ExternalView() operator spec
    printIndent(stream, indent);
    stream.printf(");\n");

    // Finally, generate an AOG nickname that maps to a scan over this
    // table.
    stream.printf("%s = ExternalViewScan(%s);\n", StringUtils.toAOGNick(getExternalViewName()),
        StringUtils.quoteStr('"', getExternalViewName()));

  }

  @Override
  public int reallyCompareTo(AQLParseTreeNode o) {
    CreateExternalViewNode other = (CreateExternalViewNode) o;

    int val = viewName.compareTo(other.viewName);
    if (0 != val) {
      return val;
    }

    val = externalName.compareTo(other.externalName);
    if (0 != val) {
      return val;
    }

    val = colNames.size() - other.colNames.size();
    if (0 != val) {
      return val;
    }
    for (int i = 0; i < colNames.size(); i++) {
      val = colNames.get(i).compareTo(other.colNames.get(i));
      if (0 != val) {
        return val;
      }
    }

    val = colTypes.size() - other.colTypes.size();
    if (0 != val) {
      return val;
    }
    for (int i = 0; i < colTypes.size(); i++) {
      val = colTypes.get(i).compareTo(other.colTypes.get(i));
      if (0 != val) {
        return val;
      }
    }

    return val;
  }

  @Deprecated
  public String getOrigFileName() {
    return containingFileName;
  }

  public String getExternalViewName() {
    return prepareQualifiedName(viewName.getNickname());
  }

  /**
   * @return The Node object of external view's view name
   */
  public NickNode getViewNameNode() {
    return viewName;
  }

  public String getExternalName() {
    return externalName.getStr();
  }

  public String getUnqualifiedName() {
    return viewName.getNickname();
  }

  public ArrayList<NickNode> getColNames() {
    return colNames;
  }

  public ArrayList<NickNode> getColTypes() {
    return colTypes;
  }

  /**
   * @return token at the location where problems with this external view definition should be
   *         reported.
   */
  @Deprecated
  public Token getErrorTok() {
    return viewName.getOrigTok();
  }

  @Override
  /**
   * @return token at the location where problems with this external view definition should be
   *         reported.
   */
  public Token getOrigTok() {
    return viewName.getOrigTok();
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
  public void qualifyReferences(Catalog catalog) {
    // No action

  }
}
