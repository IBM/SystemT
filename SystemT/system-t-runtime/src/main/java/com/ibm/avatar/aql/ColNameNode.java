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
import java.util.Arrays;
import java.util.TreeSet;

import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.algebra.function.scalar.GetCol;
import com.ibm.avatar.aog.ConstFuncNode;
import com.ibm.avatar.aql.catalog.Catalog;

/**
 * Parse tree node for an expression in the form
 * 
 * <pre>
 * table.column
 * </pre>
 * 
 */
public class ColNameNode extends RValueNode {

  public static final String TYPE_NAME = "ColumnName";

  /**
   * Parse tree node for the table/view name part of the column reference. Gets rewritten in place
   * during qualifyReferences().
   */
  private NickNode tabname;

  private final NickNode colname;

  /**
   * Pointer to the parse tree node for the element of the FROM clause that this column reference is
   * from. Filled in during type inference.
   */
  // private FromListItemNode targetFromListItem = null;

  /**
   * Cached value of the qualified column name for this node
   */
  private String qualifiedName = null;

  /**
   * Cached set of referenced columns.
   */
  // private TreeSet<String> referencedCols = null;

  /**
   * @param item Pointer to the parse tree node for the element of the FROM clause that this column
   *        reference is from.
   */
  // public void setTargetFromListItem (FromListItemNode item)
  // {
  // this.targetFromListItem = item;
  // }

  public Token getTabnameTok() {
    return tabname.getOrigTok();
  }

  public Token getColnameTok() {
    return colname.getOrigTok();
  }

  // @SuppressWarnings("unused")
  // private Token colnameTok;

  public ColNameNode(NickNode tabname, NickNode colname) {
    // set error location info
    super(TYPE_NAME, colname.getContainingFileName(), colname.getOrigTok());

    this.tabname = tabname;
    this.colname = colname;
  }

  /**
   * Convenience constructor for creating temporary ColNameNodes during rewrites.
   * 
   * @param tableName string name of the table
   * @param colnameInTable name of the column within the table
   */
  public ColNameNode(String tableName, String colnameInTable) {
    super(TYPE_NAME, null, null);

    this.tabname = new NickNode(tableName);
    this.colname = new NickNode(colnameInTable);
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    printIndent(stream, indent);

    // Fix for defect : From list example from AQL Reference gives provenance rewrite error when I
    // output view it.
    // The tabname and colname of a column reference are NickNodes. In the AQLParser, Nicknodes can
    // be either composed
    // of a-z, A-Z, 0-9, _ characters, or they can be double quoted string literals. When parsing
    // the latter form of
    // NickNode, the AQLParser dequotes the string of the NickNode: that is, removes the leading and
    // trailing
    // double-quotes, and de-escapes any double quotes inside the string. So if we want to dump back
    // valid AQL, we need
    // to (re)quote, i.e., use NickNode.dump(), instead of dumping the tab name and column name
    // directly.
    // stream.printf (getColName ());

    if (null != tabname) {
      tabname.dump(stream, 0);
      stream.print(".");
    }
    colname.dump(stream, 0);
  }

  @Override
  public String toString() {
    return getColName();
  }

  /**
   * @return column name within the context of the enclosing table (e.g. unqualified reference)
   */
  public String getColnameInTable() {
    return colname.getNickname();
  }

  public String getTabname() {
    // TODO: Should there really be a call to prepareQualifiedName() here?
    return (tabname == null) ? null : prepareQualifiedName(tabname.getNickname());
  }

  public boolean getHaveTabname() {
    return (null != tabname);
  }

  @Override
  public String getColName() {
    // We cache the value for performance
    if (null == qualifiedName) {

      // Use a StringBuilder for performance.
      if (null != tabname) {
        StringBuilder sb = new StringBuilder(getTabname());
        sb.append('.');
        sb.append(getColnameInTable());
        this.qualifiedName = sb.toString();
      } else {
        // No table name
        this.qualifiedName = getColnameInTable();
      }
    }

    return qualifiedName;
  }

  @Override
  public void getReferencedCols(TreeSet<String> accum, Catalog catalog) {
    accum.add(getColName());
  }

  @Override
  public int hashCode() {
    int accum = 0;
    if (tabname != null) {
      accum += tabname.hashCode();
    }
    accum <<= 3;
    accum += colname.hashCode();

    return accum;
  }

  @Override
  public int reallyCompareTo(AQLParseTreeNode o) {
    ColNameNode other = (ColNameNode) o;
    int val = colname.compareTo(other.colname);
    if (val != 0) {
      return val;
    }

    if (null == tabname) {
      if (null != other.tabname) {
        return -1;
      } else {
        return 0;
      }
    } else {
      return tabname.compareTo(other.tabname);
    }
  }

  /*
   * @Override public void toAOG(PrintStream stream, int indent) { // Generate a call to the
   * GetCol() scalar function.s printIndent(stream, indent); stream.printf("%s(%s)", GetCol.FNAME,
   * StringUtils.quoteStr('"', getColName())); }
   */

  @Override
  public Object toAOGNode(Catalog catalog) {
    // return getColName();

    return new ConstFuncNode.Col(getColName());
  }

  @Override
  public ScalarFnCallNode asFunction() throws ParseException {
    // Argument of GetCol() function is a string
    // return new FunctionNode(GetCol.FNAME, new StringNode(getColName(),
    // catalog), catalog);

    // We generate a special kind of parse tree node, since the argument to
    // the AOG function GetCol() is a string constant that we don't want to
    // wrap.
    return new ConstAOGFunctNode(AQLFunc.computeFuncName(GetCol.class), getColName());
    // return new GetColNode(this);
  }

  @Override
  public void qualifyReferences(Catalog catalog) {
    if (tabname != null) {
      String qualifiedName = catalog.getQualifiedViewOrTableName(tabname.getNickname());
      if (qualifiedName != null) {
        tabname =
            new NickNode(qualifiedName, tabname.getContainingFileName(), tabname.getOrigTok());
      }
    }
  }

  @Override
  public FieldType getType(Catalog c, AbstractTupleSchema schema) throws ParseException {
    if (null == tabname) {
      // SPECIAL CASE: AQL did not specify a table name.
      // Determine which column of the target schema matches, and put that name into the node for
      // future reference.
      // This step would be much easier if qualified names were stored as pairs instead of squashed
      // into a single
      // string.
      String targetColSuffix = "." + colname.getNickname();
      for (int i = 0; i < schema.size(); i++) {
        String fieldName = schema.getFieldNameByIx(i);
        if (fieldName.endsWith(targetColSuffix) || (fieldName.equals(colname.getNickname()))) {
          // tabname = new NickNode (fieldName.substring (0, fieldName.length () -
          // targetColSuffix.length ()));
          return schema.getFieldTypeByIx(i);
        }
      }

      throw AQLParserBase.makeException(getColnameTok(),
          "Error dereferencing unqualified column name %s (available names are %s).  "
              + "Ensure that the target column appears in one of the elements of the FROM list.",
          colname.getNickname(), Arrays.toString(schema.getFieldNames()));
      // END SPECIAL CASE
    }

    // If we get here, the column name should match the target schema.
    try {
      return schema.getFieldTypeByName(getColName());
    } catch (Throwable t) {
      // If we get here, we didn't find a matching column name
      throw AQLParserBase.makeException(t, getColnameTok(),
          "Error dereferencing column name %s (available names are %s).  "
              + "Ensure that the target column appears in one of the elements of the FROM list.",
          getColName(), Arrays.toString(schema.getFieldNames()));
    }

    // if (null == targetFromListItem) {
    //
    // throw AQLParserBase.makeException (getTabnameTok (),
    // "Could not identify FROM list entry for '%s'", getTabname ());
    // }
    //
    // // Note that we assume that qualifyReferences() has already been called
    // CatalogEntry entry = c.lookup(targetFromListItem);
    //
    // if (null == entry) { throw AQLParserBase.makeException (getTabnameTok (),
    // "Couldn't find catalog entry for FROM list item '%s'", targetFromListItem); }
    //
    // ArrayList<String> colNames = entry.getColNames ();
    // for (int i = 0; i < colNames.size (); i++) {
    // if (colNames.get (i).equals (getColnameInTable ())) {
    // // Found a matching name. Pull out the type.
    //
    // }
    // }
    //
    // // If we get here, we didn't find a matching column name
    // throw AQLParserBase.makeException (getColnameTok (),
    // "View '%s' does not have a column called '%s' (column names are %s)",
    // getTabname(), getColnameInTable (), entry.getColNames ());
  }
}
