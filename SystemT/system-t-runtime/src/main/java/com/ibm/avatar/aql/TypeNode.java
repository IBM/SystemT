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
/**
 * 
 */
package com.ibm.avatar.aql;

import java.io.PrintWriter;
import java.util.ArrayList;

import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.aql.catalog.Catalog;

/**
 * Parse tree node for a type that appears in the arguments or RETURN clause of a CREATE FUNCTION
 * statement. Covers both scalar and table types.
 * 
 */
public class TypeNode extends AbstractAQLParseTreeNode {

  /** Name of the type, if it is a scalar type */
  private String typeName = null;
  private boolean isSpan = false;
  private boolean isScalarList = false;

  /** Full schema of the type (in column order) if the type is a table type */
  private ArrayList<Pair<NickNode, TypeNode>> tableType = null;
  private boolean isTable = false;
  private boolean isLocator = false;

  /**
   * Constructor for a scalar type
   * 
   * @param t token location to use for error reporting
   * @param typeName name of the scalar type returned
   * @param isSpan true if the type is a span type
   * @param isScalarList true if the type is a list type
   */
  public TypeNode(String typeName, boolean isSpan, boolean isScalarList, String containingFileName,
      Token origTok) {
    // set the error location info
    super(containingFileName, origTok);

    this.typeName = typeName;
    this.isSpan = isSpan;
    this.isScalarList = isScalarList;
  }

  /**
   * Constructor for a table type
   * 
   * @param t token location for error reporting
   * @param colNamesAndTypes list of the fields in the table schema
   * @throws ParseException if a validation fails
   */
  public TypeNode(ArrayList<Pair<NickNode, TypeNode>> colNamesAndTypes, String containingFileName,
      Token origTok) throws ParseException {
    // set the error location info
    super(containingFileName, origTok);

    // Validate arguments.
    for (Pair<NickNode, TypeNode> pair : colNamesAndTypes) {
      if (pair.second.isTable) {
        throw AQLParserBase.makeException(pair.second.getOrigTok(),
            "'%s' column in table type contains a nested table.", pair.first.getNickname());
      }
    }

    tableType = colNamesAndTypes;
    this.isTable = true;
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.avatar.aql.Node#dump(java.io.PrintWriter, int)
   */
  @Override
  public void dump(PrintWriter stream, int indent) {

    if (isTable) {
      for (int i = 0; i < tableType.size(); i++) {
        Pair<NickNode, TypeNode> p = tableType.get(i);
        p.first.dump(stream, indent);
        stream.print(" ");
        p.second.dump(stream, 0);
        if (i < tableType.size() - 1) {
          stream.print(",\n");
        }
      }
    } else {
      printIndent(stream, indent);
      stream.printf("%s", typeName);
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.avatar.aql.Node#reallyCompareTo(com.ibm.avatar.aql.Node)
   */
  @Override
  protected int reallyCompareTo(AQLParseTreeNode o) {
    TypeNode other = (TypeNode) o;
    return typeName.compareTo(other.typeName);
  }

  /**
   * @return the name of the type, if this type is a scalar type
   */
  public String getTypeName() {
    if (isTable) {
      throw new FatalInternalError(
          "Called getTypeName() for a table type. " + "Only scalar types have a type name. "
              + "Use getTypeString() or toTupleSchema() instead.");
    }
    return typeName;
  }

  /**
   * @return true if this type is Span or Text
   */
  public boolean isSpan() {
    return isSpan;
  }

  /**
   * @return true if this type is a scalar list type
   */
  public boolean isScalarList() {
    return isScalarList;
  }

  /**
   * @return true if this parse tree node is for a table type
   */
  public boolean isTable() {
    return isTable;
  }

  /**
   * @return true if this parse tree node is for a locator (pointer) type
   */
  public boolean isLocator() {
    return isLocator;
  }

  /**
   * @param isLocator true if this parse tree node is for a locator (pointer) type
   */
  public void setLocator(boolean isLocator) {
    this.isLocator = isLocator;
  }

  @Override
  public void qualifyReferences(Catalog catalog) {
    // No action

  }

  /**
   * @return the string format used to serialize this type for embedding in an AOG file
   * @throws ParseException if there is a problem converting the parse tree nodes to type info
   */
  public String getTypeString() throws ParseException {
    if (isTable) {
      // We keep the string serialization code in FieldType
      FieldType ft = new FieldType(toTupleSchema(), isLocator);
      return ft.toString();
    } else {
      // Scalar type
      return typeName;
    }
  }

  /**
   * @return runtime schema object for the tuples that this table function returns
   */
  public TupleSchema toTupleSchema() throws ParseException {
    if (false == isTable()) {
      throw new FatalInternalError("getTableSchema() called on a scalar type");
    }

    // Convert parse tree info on types to the format that the TupleSchema constructor expects
    String[] fieldNames = new String[tableType.size()];
    FieldType[] fieldTypes = new FieldType[tableType.size()];

    for (int i = 0; i < tableType.size(); i++) {
      Pair<NickNode, TypeNode> p = tableType.get(i);
      fieldNames[i] = p.first.getNickname();
      fieldTypes[i] = FieldType.stringToFieldType(p.second.getTypeName());
    }

    return new TupleSchema(fieldNames, fieldTypes);
  }

}
