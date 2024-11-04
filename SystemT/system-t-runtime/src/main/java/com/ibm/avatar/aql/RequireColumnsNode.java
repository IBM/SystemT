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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.aql.compiler.ParseToCatalog;

/**
 * Top-level parse tree node for a
 * 
 * <pre>
 * require document with columns
 * </pre>
 * 
 * statement.
 * 
 */
public class RequireColumnsNode extends TopLevelParseTreeNode {

  /** The names of the columns in the schema of the special view Document. */
  ArrayList<NickNode> colNames;

  /** Types of the columns in the schema of the special view Document. */
  ArrayList<NickNode> colTypes;

  public RequireColumnsNode(ArrayList<NickNode> colNames, ArrayList<NickNode> colTypes,
      String containingFileName, Token origTok) {
    // set error location info
    super(containingFileName, origTok);

    this.colNames = colNames;
    this.colTypes = colTypes;
  }

  /**
   * @return token at the location where problems with this function definition should be reported.
   */
  @Deprecated
  public Token getErrorTok() {
    return getOrigTok();
  }

  @Override
  /**
   * Check to see if two columns have the same name
   * 
   * @param catalog catalog containing the columns to validate
   */
  public List<ParseException> validate(Catalog catalog) {
    List<ParseException> errors = new ArrayList<ParseException>();

    for (int i = 0; i < colNames.size(); i++) {
      for (int j = i; j < colNames.size(); j++) {
        if ((i != j) && (colNames.get(i).getNickname().equals(colNames.get(j).getNickname()))) {
          errors.add(AQLParserBase.makeException(getOrigTok(),
              "Document has multiple columns with the name %s.", colNames.get(i).getNickname()));
        }
      }
    }

    return ParseToCatalog.makeWrapperException(errors, getContainingFileName());
  }

  /**
   * <pre>
   * Compress multiple declarations of a column into one entry. 
   * This happens when it's declared in different files with
   * the same type.
   * </pre>
   */
  public void compressCols() throws ParseException {
    ArrayList<NickNode> newColNames = new ArrayList<NickNode>();
    ArrayList<NickNode> newColTypes = new ArrayList<NickNode>();

    Map<String, Pair<NickNode, NickNode>> newCols =
        new LinkedHashMap<String, Pair<NickNode, NickNode>>();

    // iterate through all the columns and keep only the nodes without duplicates
    for (int i = 0; i < colNames.size(); i++) {
      String colName = colNames.get(i).getNickname();

      if (newCols.containsKey(colName)) {

        // this is a duplicate, so don't add it, but do type-checking
        if (colTypes.get(i).equals(newCols.get(colName).second)) {
          // types match, do nothing
        } else {
          // types don't match, throw error
          AQLParserBase.makeException(String.format(String.format(
              "Document has multiple columns with name %s " + "and different types (%s, %s).",
              colName, colTypes.get(i), newCols.get(colName).second)), this.getOrigTok());
        }
      } else {
        // this is not a duplicate, so add it
        newCols.put(colName, new Pair<NickNode, NickNode>(colNames.get(i), colTypes.get(i)));
      }
    }

    // set the columns to non-duplicates only
    for (Pair<NickNode, NickNode> colPair : newCols.values()) {
      newColNames.add(colPair.first);
      newColTypes.add(colPair.second);
    }

    colNames = newColNames;
    colTypes = newColTypes;

    return;
  }

  /**
   * Pretty-print the contents of the node; should output valid AQL with identical semantics to the
   * original AQL that the parser consumed.
   */
  @Override
  public void dump(PrintWriter stream, int indent) {
    printIndent(stream, indent);
    stream.printf("require document with columns\n");

    for (int i = 0; i < colNames.size(); i++) {
      printIndent(stream, indent + 1);
      colNames.get(i).dump(stream, 0);
      stream.printf(" %s", colTypes.get(i).getNickname());
      if (i != colNames.size() - 1) {
        stream.print(" and\n");
      }
    }

    printIndent(stream, indent);
    stream.print(";");
  }

  // This function does not need to be overridden, because we construct
  // a new kind of virtual require document schema at plan generation
  // time.

  // /** Generate AOG inline spec. */
  // public void toAOG(PrintWriter stream, int indent) throws ParseException {

  // }

  @Override
  public int reallyCompareTo(AQLParseTreeNode o) {
    RequireColumnsNode other = (RequireColumnsNode) o;

    int val = colNames.size() - other.colNames.size();
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
    return getContainingFileName();
  }

  public ArrayList<NickNode> getColNames() {
    return colNames;
  }

  public ArrayList<NickNode> getColTypes() {
    return colTypes;
  }

  public void setColNames(ArrayList<NickNode> colNames) {
    this.colNames = colNames;
  }

  public void setColTypes(ArrayList<NickNode> colTypes) {
    this.colTypes = colTypes;
  }

  /**
   * Adds a column name and associated type to the node if not already present. If the column is
   * already present, do nothing.
   * 
   * @param colName the name of the column to be added
   * @param colType the associated type of the column to be added
   */
  public void addCol(String colName, FieldType colType) {
    if (!this.containsCol(colName)) {
      colNames.add(new NickNode(colName));
      colTypes.add(new NickNode(colType.getTypeName()));
    }
  }

  public boolean containsCol(String colName) {
    for (NickNode colNode : colNames) {
      String nick = colNode.getNickname();
      if (nick.equals(colName)) {
        return true;
      }
    }

    return false;
  }

  public boolean containsSchema(String colName, String colType) {
    for (int i = 0; i < colNames.size(); i++) {
      if (colNames.get(i).getNickname().equals(colName)) {
        if (colTypes.get(i).getNickname().equals(colType)) {
          return true;
        } else {
          return false;
        }
      }
    }

    return false;
  }

  /*
   * checks if the input parameter name/type pair clashes with a name/type pair in this node
   * 
   * @param colName the column name to check
   * 
   * @param colType the column type associated with colName
   */

  public boolean clashesWith(String colName, String colType) {
    for (int i = 0; i < colNames.size(); i++) {
      if (colNames.get(i).getNickname().equals(colName)) {
        if (!colTypes.get(i).getNickname().equals(colType)) {
          return true;
        } else {
          return false;
        }
      }
    }

    return false;
  }

  @Override
  public void qualifyReferences(Catalog catalog) {
    // TODO Auto-generated method stub -- Jay to fix

  }

}
