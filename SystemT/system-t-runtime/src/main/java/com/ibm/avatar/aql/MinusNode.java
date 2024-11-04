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
 *   (select ...)
 *   minus
 *   (select...);
 * </pre>
 * 
 */
public class MinusNode extends ViewBodyNode {

  /** First operand of the set difference */
  private ViewBodyNode firstStmt;

  /** Second operand */
  private ViewBodyNode secondStmt;

  /**
   * @param stmts The select statements that are unioned together to form the view.
   */
  public MinusNode(ViewBodyNode firstSelect, ViewBodyNode secondSelect, String containingFileName,
      Token origTok) throws ParseException {
    // set the error location info
    super(containingFileName, origTok);

    this.firstStmt = firstSelect;
    this.secondStmt = secondSelect;

    // Run some sanity checks.
  }

  @Override
  public List<ParseException> validate(Catalog catalog) {
    List<ParseException> errors = new ArrayList<ParseException>();

    if (firstStmt != null) {
      List<ParseException> firstStmtErrors = firstStmt.validate(catalog);
      if (null != firstStmtErrors && firstStmtErrors.size() > 0)
        errors.addAll(firstStmtErrors);
    }

    if (secondStmt != null) {
      List<ParseException> secondStmtErrors = secondStmt.validate(catalog);
      if (null != secondStmtErrors && secondStmtErrors.size() > 0)
        errors.addAll(secondStmtErrors);
    }

    return errors;
  }

  @Override
  public void dump(PrintWriter stream, int indent) {

    // Most of the work is delegated to the SelectNodes underneath us.
    stream.print("(");
    firstStmt.dump(stream, indent + 1);
    stream.print(")\n");
    printIndent(stream, indent);
    stream.print("minus\n");
    printIndent(stream, indent);
    stream.print("(");
    secondStmt.dump(stream, indent + 1);
    stream.print(")");
  }

  public ViewBodyNode getFirstStmt() {
    return firstStmt;
  }

  public ViewBodyNode getSecondStmt() {
    return secondStmt;
  }

  public void setFirstStmt(ViewBodyNode firstStmt) {
    this.firstStmt = firstStmt;
  }

  public void setSecondStmt(ViewBodyNode secondStmt) {
    this.secondStmt = secondStmt;
  }

  @Override
  public int reallyCompareTo(AQLParseTreeNode o) {
    MinusNode other = (MinusNode) o;
    int val = firstStmt.compareTo(other.firstStmt);
    if (val != 0) {
      return val;
    }
    return secondStmt.compareTo(other.secondStmt);
  }

  @Override
  public void getDeps(TreeSet<String> accum, Catalog catalog) throws ParseException {
    firstStmt.getDeps(accum, catalog);
    secondStmt.getDeps(accum, catalog);
  }

  @Override
  public void qualifyReferences(Catalog catalog) throws ParseException {
    firstStmt.qualifyReferences(catalog);
    secondStmt.qualifyReferences(catalog);
  }

}
