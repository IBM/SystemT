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

import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.algebra.util.table.TableParams;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.aql.compiler.ParseToCatalog;
import com.ibm.avatar.aql.doc.AQLDocComment;

/**
 * Top-level parse tree node for a
 * 
 * <pre>
 * create table
 * </pre>
 * 
 * statement.
 */
public class CreateTableNode extends TopLevelParseTreeNode {

  /** The name of the table. */
  NickNode tabName;

  /** The names of the columns in the table's schema. */
  ArrayList<NickNode> colNames;

  /** Types of the columns in the table's schema. */
  ArrayList<NickNode> colTypes;

  /** The tuples themselves, if the table is defined inline. */
  ArrayList<Object[]> tupVals = null;

  /** File containing table tuples, if the table is defined in a file. */
  String tableFileName = null;

  /** Table matching settings for this table. */
  protected TableParams params;

  /** The AQL Doc comment (if any) attached to this parse tree node. */
  private AQLDocComment comment;

  /**
   * Sets the table tuples
   * 
   * @param tupVals table tuple values
   */
  public void setTupVals(ArrayList<ArrayList<String>> tupVals) {

    ArrayList<Object[]> tupleArray = new ArrayList<Object[]>();
    for (ArrayList<String> tupVal : tupVals) {
      String[] stringArray = tupVal.toArray(new String[0]);
      tupleArray.add(stringArray);
    }

    this.tupVals = tupleArray;
  }

  /**
   * Sets the table params
   * 
   * @param params table params
   */
  public void setParams(TableParams params) {
    this.params = params;
  }

  /**
   * Returns the table params
   * 
   * @return
   */
  public TableParams getParams() {
    return this.params;
  }

  /**
   * Sets the unqualified name of the table
   * 
   * @param tablfileName unqualified name of the table
   */
  public void setTableFileName(String tableFileName) {
    this.tableFileName = tableFileName;
  }

  /**
   * Returns the table file name
   * 
   * @return
   */
  public String getTableFileName() {
    return this.tableFileName;
  }

  private CreateTableNode(String containingFileName, Token origTok) {
    // set error location info
    // this used to be the table name -- changed in v2.1.1 to be the CREATE statement
    super(containingFileName, origTok);

  }

  @Override
  public List<ParseException> validate(Catalog catalog) {
    List<ParseException> errors = new ArrayList<ParseException>();

    // Check for too few columns in tuples then defined.
    if (tupVals != null) {
      for (Object[] tup : tupVals) {
        // Observation: Parser uses the table schema length to prepare the tup Object[], hence
        // following if condition is
        // redundant. TODO: Ideally, we should remove this condition
        if (tup.length < colNames.size()) {
          errors.add(AQLParserBase.makeException(getErrorTok(),
              "Read %d columns, but " + "schema requires %d columns.", tup.length,
              colNames.size()));
        }
        // Check for tuple's field data type against defined data
        // types
        for (int i = 0; i < tup.length; i++) {
          String colTypeName = colTypes.get(i).getNickname();
          String columnName = colNames.get(i).getNickname();
          if (null == tup[i]) {
            errors.add(AQLParserBase.makeException(getErrorTok(),
                "Provide non-null value for column '%s'. All the columns in the table are required.",
                columnName));
            // No need to validate data type for null field
            continue;
          } ;

          if ("Text".equals(colTypeName) && !(tup[i] instanceof String)) {
            errors.add(AQLParserBase.makeException(
                // strnode.getOrigTok(),
                getErrorTok(),
                "Value '%s' for column '%s' is not of type Text. Provide a non-null Text value.",
                tup[i], columnName));
          } else if ("Integer".equals(colTypeName) && !(tup[i] instanceof Integer)) {
            errors.add(AQLParserBase.makeException(
                // strnode.getOrigTok(),
                getErrorTok(),
                "Value '%s' for column '%s' is not of type Integer. Provide a non-null Integer value.",
                tup[i], columnName));
          }

        }
      }
    }

    return ParseToCatalog.makeWrapperException(errors, containingFileName);
  }

  /**
   * Pretty-print the contents of the node; should output valid AQL with identical semantics to the
   * original AQL that the parser consumed.
   */
  @Override
  public void dump(PrintWriter stream, int indent) {
    printIndent(stream, indent);
    if (isExternal)
      stream.printf("create external table ");
    else
      stream.printf("create table ");

    // Print the table name inline so that we can resolve double-quotes
    tabName.dump(stream, 0);
    stream.printf("\n");

    printIndent(stream, indent + 1);
    stream.print("(");
    for (int i = 0; i < colNames.size(); i++) {
      stream.printf("%s %s", AQLParser.quoteReservedWord(colNames.get(i).getNickname()),
          colTypes.get(i).getNickname());
      if (i != colNames.size() - 1) {
        stream.print(", ");
      }
    }
    stream.print(")");

    if (isExternal) { // allow_empty flag only applicable to external table

      // AQL parser enforces that either allow_empty or required is not null.
      if (isAllowEmpty() != null) {
        stream.printf("\n");
        printIndent(stream, indent + 1);
        stream.printf("allow_empty %s", String.valueOf(isAllowEmpty()));
      } else {
        stream.printf("\n");
        printIndent(stream, indent + 1);
        stream.printf("required %s", String.valueOf(isRequired()));
      }
    } else {
      stream.print(" as\n");
      printIndent(stream, indent + 1);
      stream.print("values\n");
      for (int i = 0; i < tupVals.size(); i++) {
        Object[] tup = tupVals.get(i);
        printIndent(stream, indent + 2);
        stream.print("(");
        for (int j = 0; j < tup.length; j++) {
          if (tup[j] instanceof String) {
            // Make sure that strings are quoted, SQL-style.
            // stream.printf("'%s'", tup[j]);
            stream.printf("%s", StringUtils.quoteStr('\'', (String) tup[j]));
          } else {
            // For everything else, assume that toString() will give the
            // right value.
            stream.print(tup[j]);
          }

          if (j != tup.length - 1) {
            stream.print(", ");
          }
        }
        if (i == tupVals.size() - 1) {
          stream.print(")");
        } else {
          stream.print("),\n");
        }
      }
    }
    stream.print(";");
  }

  /**
   * Generate AOG inline table spec.
   * 
   * @param stream where to dump the AOG
   * @param indent how far to indent the lines we print out
   */
  public void toAOG(PrintWriter stream, int indent) throws ParseException {

    // First, generate the CreateTable() AOG statement.

    // First argument is table name
    printIndent(stream, indent);
    stream.printf("CreateTable(%s,\n", StringUtils.quoteStr('"', getTableName()));

    // second argument is table attributes: these attribute indicates whether the table is
    // external/internal, if
    // external whether empty table is allowed or not
    printIndent(stream, indent + 1);
    stream.print("(\n");
    printIndent(stream, indent + 2);
    stream.printf("\"%s\" => \"%s\"", "isExternal", getIsExternal());

    if (getIsExternal()) {
      stream.print(",\n");
      printIndent(stream, indent + 2);

      // AQL parser enforces that either allow_empty or required is not null.
      if (isAllowEmpty() != null) {
        stream.printf("\"%s\" => \"%s\"", "allowEmpty", isAllowEmpty());
      } else {
        stream.printf("\"%s\" => \"%s\"", "required", isRequired());
      }
      stream.print("\n");
    } else {
      stream.print("\n");
    }

    // Close parens on attribute
    printIndent(stream, indent + 1);
    stream.print("),\n");

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

    if (!getIsExternal()) {

      // Close parens on schema
      printIndent(stream, indent + 1);
      stream.print("),\n");

      // Remaining arguments are the list of tuples

      for (int i = 0; i < tupVals.size(); i++) {
        printIndent(stream, indent + 1);
        stream.print("(");

        Object[] tup = tupVals.get(i);
        for (int j = 0; j < tup.length; j++) {

          if (tup[j] instanceof String) {
            // Make sure that strings are quoted
            // stream.printf ("\"%s\"", tup[j]);
            stream.print(StringUtils.quoteStr('"', (String) tup[j]));
          } else {
            // For everything else, assume that toString() will give the
            // right value (at least for now)
            stream.printf("\"%s\"", tup[j]);
          }

          if (j < tup.length - 1) {
            stream.print(", ");
          }
        }

        // Close parens on current tuple
        if (i == tupVals.size() - 1) {
          stream.print(")\n");
        } else {
          stream.print("),\n");
        }
      }
    } else {
      // Close parens on schema
      printIndent(stream, indent + 1);
      stream.print(")\n");
    }

    // Close paren on Table() operator spec
    printIndent(stream, indent);
    stream.printf(");\n");

    // Finally, generate an AOG nickname that maps to a scan over this
    // table.
    stream.printf("%s = TableScan(%s);\n", StringUtils.toAOGNick(getTableName()),
        StringUtils.quoteStr('"', getTableName()));

  }

  @Override
  @SuppressWarnings({"all"})
  public int reallyCompareTo(AQLParseTreeNode o) {
    CreateTableNode other = (CreateTableNode) o;

    int val = tabName.compareTo(other.tabName);
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

    val = tupVals.size() - other.tupVals.size();
    if (0 != val) {
      return val;
    }
    for (int i = 0; i < tupVals.size(); i++) {
      Object[] fields = tupVals.get(i);
      Object[] otherFields = other.tupVals.get(i);
      val = fields.length - otherFields.length;
      if (0 != val) {
        // This should never happen.
        throw new RuntimeException("Different tuple lengths but same schema");
      }

      for (int j = 0; j < fields.length; j++) {
        Comparable c = (Comparable) (fields[j]);
        Comparable oc = (Comparable) (otherFields[j]);
        val = c.compareTo(oc);
        if (0 != val) {
          return val;
        }
      }

    }

    return val;
  }

  @Deprecated
  public String getOrigFileName() {
    return containingFileName;
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

  /**
   * This flag, when set<code>true</code> marks the table external; By default table is marked
   * internal
   */
  private boolean isExternal = false;

  /**
   * Method to mark table external.
   * 
   * @param isExternal - if <code>true</code>, marks the table external.
   */
  public void setIsExternal(boolean isExternal) {
    this.isExternal = isExternal;
  }

  /**
   * Returns the table name qualified by the module name.
   * 
   * @return
   */
  public String getTableName() {
    return prepareQualifiedName(tabName.getNickname());
  }

  /**
   * Return the original table name (unqualified by the module name) as it appears in the create
   * table statement.
   * 
   * @return
   */
  public String getUnqualifiedName() {
    return tabName.getNickname();
  }

  /**
   * Returns the table file name
   * 
   * @return table file name
   */
  public String getFileName() {
    return this.tableFileName;
  }

  /**
   * @return returns true, if the table is declared external.
   */
  public boolean getIsExternal() {
    return this.isExternal;
  }

  /**
   * This flag, when set <code>true</code> indicates that empty external tables are allowed; when
   * set <code>false</code> loader will throw error for empty external tables.<br>
   * Deprecated As of v3.0.1 -- use the "required" flag instead.
   */
  @Deprecated
  private Boolean allowEmpty = null;

  /**
   * Method to mark that empty external table is allowed.Allow empty flag is applicable only for
   * external table; setting this flag for internal table will throw exception.
   * 
   * @param allowEmpty - if <code>true</code>, marks that empty external table is allowed.
   * @UnsupportedOperationException - if invoked for internal table.
   * @deprecated As of v3.0.1, use {@link #setRequired(boolean)} with the inverse logical value
   *             instead
   */
  @Deprecated
  public void setAllowEmpty(boolean allowEmpty) {
    if (!this.isExternal)
      throw new UnsupportedOperationException(
          "Allow empty flag is applicable only for external tables");

    this.allowEmpty = allowEmpty;
  }

  /**
   * This method returns <code>true</code>, if empty external table is allowed.
   * 
   * @return <code>true</code> if empty external table is allowed.
   * @UnsupportedOperationException - if invoked for internal table.
   * @deprecated As of v3.0.1, use {@link #isRequired()} instead
   */
  @Deprecated
  public Boolean isAllowEmpty() {
    if (!this.isExternal)
      throw new UnsupportedOperationException(
          "Allow empty flag is applicable only for external tables");

    return allowEmpty;
  }

  /**
   * This flag, when set to <code>true</code> indicates that a table file must be specified when the
   * operator graph is instantiated, or loader will throw error for missing external table file. The
   * file may be empty.
   */
  private Boolean required = null;

  /**
   * Method to mark that external table file name is required to be specified. Required flag is
   * applicable only for external table; setting this flag for internal table will throw exception.
   * 
   * @param required - if <code>true</code>, marks that external table file name must be specified.
   * @UnsupportedOperationException - if invoked for internal table.
   */
  public void setRequired(boolean required) {
    if (!this.isExternal)
      throw new UnsupportedOperationException(
          "Required flag is applicable only for external tables");

    this.required = required;
  }

  /**
   * This method returns <code>true</code>, if external table filename is required.
   * 
   * @return <code>true</code> if external table filename is required.
   * @UnsupportedOperationException - if invoked for internal table.
   */
  public Boolean isRequired() {
    if (!this.isExternal)
      throw new UnsupportedOperationException(
          "Required flag is applicable only for external tables");

    return this.required;
  }

  public ArrayList<NickNode> getColNames() {
    return colNames;
  }

  public ArrayList<NickNode> getColTypes() {
    return colTypes;
  }

  /**
   * @return token at the location where problems with this table definition should be reported.
   */
  @Deprecated
  public Token getErrorTok() {
    return tabName.getOrigTok();
  }

  /**
   * Returns the table name nick node
   * 
   * @return table name nick node
   */
  public NickNode getTableNameNode() {
    return tabName;
  }

  /**
   * This method will return values for the given columns from all the records in the table.
   * 
   * @param columns column names for which values should be returned.
   * @return values for the given columns for all the records in the table.
   */
  public List<Object[]> getColumnValues(String[] columns) throws Exception {
    if (!this.isExternal) {
      // Map of given column name vs their index in table schema
      Map<String, Integer> columnNameVsIndexInSchema = new LinkedHashMap<String, Integer>();

      List<String> unknownColumns = new ArrayList<String>();

      for (int i = 0; i < columns.length; i++) {
        NickNode temp = new NickNode(columns[i]);
        // validate given column names
        if (!this.colNames.contains(temp)) {
          unknownColumns.add(columns[i]);
        } else {
          columnNameVsIndexInSchema.put(columns[i], this.colNames.indexOf(temp));
        }
      }

      // There are unknown columns
      if (unknownColumns.size() > 0) {
        String errorMsg = String.format("Following columns %s not found in schema for table '%s' ",
            unknownColumns, getTableName());
        throw new Exception(errorMsg);
      } ;

      // Fetch column values
      List<Object[]> retVal = new ArrayList<Object[]>();
      for (Object[] tup : this.tupVals) {
        Object[] newTup = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
          newTup[i] = tup[columnNameVsIndexInSchema.get(columns[i])];
        }
        retVal.add(newTup);
      }
      return retVal;
    } else {
      throw new Exception("There are no values for external tables during compilation");
    }
  }

  /** /** Parse tree node to represent Inline table. */
  public static class Inline extends CreateTableNode {
    public Inline(NickNode tabName, ArrayList<NickNode> colNames, ArrayList<NickNode> colTypes,
        ArrayList<Object[]> tupVals, String containingFileName, Token origTok) {
      // set error location info
      super(containingFileName, origTok);
      this.tabName = tabName;
      this.colNames = colNames;
      this.colTypes = colTypes;
      this.tupVals = tupVals;
    }

  }

  /** Table created from a file at compile time. */
  public static class FromFile extends CreateTableNode {
    public FromFile(NickNode tabName, ArrayList<NickNode> colNames, ArrayList<NickNode> colTypes,
        String tableFileName, String containingFileName, Token origTok) {
      super(containingFileName, origTok);
      this.tabName = tabName;
      this.tableFileName = tableFileName;
      this.colNames = colNames;
      this.colTypes = colTypes;
      this.containingFileName = containingFileName;
      this.origTok = origTok;

    }
  }

  @Override
  public void qualifyReferences(Catalog catalog) {
    // No action

  }
}
