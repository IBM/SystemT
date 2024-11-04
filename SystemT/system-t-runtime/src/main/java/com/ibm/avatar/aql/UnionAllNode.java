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
import java.util.TreeSet;

import com.ibm.avatar.aql.catalog.Catalog;

/**
 * Parse tree node for a create view statement in the form
 * 
 * <pre>
 * create view [view] as
 *   (...)
 *   union all
 *   (...);
 * </pre>
 * 
 */
public class UnionAllNode extends ViewBodyNode {

  /** The statements that are unioned together to form the view. */
  private final ArrayList<ViewBodyNode> stmts;

  /**
   * @param origTok the first "union" token, for error reporting purposes
   * @param stmts The select statements that are unioned together to form the view.
   */
  public UnionAllNode(ArrayList<ViewBodyNode> stmts, String containingFileName, Token origTok)
      throws ParseException {
    // set error location info
    super(containingFileName, origTok);

    this.stmts = stmts;
  }

  @Override
  public List<ParseException> validate(Catalog catalog) {
    List<ParseException> errors = new ArrayList<ParseException>();

    if (null != stmts) {
      if (stmts.size() < 2) { // We need at least two stmt's to Union
        errors.add(
            AQLParserBase.makeException("union all statement needs at least 2 inputs!", origTok));
      } else {// Validate all the member of Union
        for (ViewBodyNode stmtBody : stmts) {
          List<ParseException> nodeError = stmtBody.validate(catalog);
          if (null != nodeError && nodeError.size() > 0)
            errors.addAll(nodeError);
        }
      }
    } else {
      errors.add(
          AQLParserBase.makeException("union all statements needs at least 2 inputs!", origTok));
    }

    return errors;
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    stream.print("(");
    // Most of the work is delegated to the SelectNodes underneath us.
    stmts.get(0).dump(stream, indent + 1);
    stream.print(")\n");

    for (int i = 1; i < stmts.size(); i++) {
      printIndent(stream, indent);
      stream.print("union all\n");
      printIndent(stream, indent);
      stream.print("(");
      stmts.get(i).dump(stream, indent + 1);
      stream.print(")");
      if (i < stmts.size() - 1)
        stream.print("\n");
    }

  }

  public ViewBodyNode getStmt(int index) {
    return stmts.get(index);
  }

  public void setStmt(int index, ViewBodyNode stmt) {
    stmts.set(index, stmt);
  }

  public int getNumStmts() {
    return stmts.size();
  }

  @Override
  protected int reallyCompareTo(AQLParseTreeNode o) {
    UnionAllNode other = (UnionAllNode) o;
    return compareNodeLists(stmts, other.stmts);
  }

  @Override
  public void getDeps(TreeSet<String> accum, Catalog catalog) throws ParseException {
    for (ViewBodyNode stmt : stmts) {
      stmt.getDeps(accum, catalog);
    }
  }

  @Override
  public void qualifyReferences(Catalog catalog) throws ParseException {
    for (ViewBodyNode vbn : stmts) {
      vbn.qualifyReferences(catalog);
    }
  }

}
