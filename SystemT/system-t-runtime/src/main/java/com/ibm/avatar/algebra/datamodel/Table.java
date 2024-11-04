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
package com.ibm.avatar.algebra.datamodel;

import java.io.PrintWriter;
import java.util.ArrayList;

import com.ibm.avatar.algebra.util.data.StringPairList;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.aog.AOGOpTree;
import com.ibm.avatar.aog.ParseException;

/**
 * Class that represents a static AQL table. Used as input for both the TableScan operator and for
 * creation of dictionary data structures internally.
 */
public class Table {

  /** Name of the table. */
  private String name;

  /**
   * The schema of the table.
   */
  private TupleSchema schema;

  /**
   * String representation of the tuples in the table. Kept around until the actual tuples are
   * created. To save memory, the table is stored in column-major order; the first index into the
   * array is the *column*.
   */
  private String[][] tupleStrings;

  /** Tuples that make up the table. */
  private TupleList tups;

  /**
   * Flag to mark the table external/internal; for tables declared with 'create external table ...'
   * statement this flag will be set to <code>true</code>
   */
  private boolean isExternal;

  /**
   * This flag, when set <code>true</code> indicates that empty external tables are allowed; <br>
   * when set <code>false</code> loader will throw error for empty external tables <br>
   * when null, indicates that it was not set
   */
  private Boolean allowEmpty;

  /**
   * This flag, when set <code>true</code> loader will throw error if external table is not
   * specified with a filename<br>
   * when set <code>false</code> indicates that no filename specification is necessary for an
   * external table<br>
   * when null, indicates that it was not set
   */
  private Boolean required;

  /**
   * Constructor for use by the AOG parser for creating a lookup table.
   * 
   * @param tableName the AOG name of the table
   * @param attributeString string representation of the table attribute, as (name, value) pairs.
   * @param schemaStrs string representation of the schema, as (name, type) pairs.
   * @param tupleStrs string representation of the tuples
   * @param isExternal flag when true, indicates that the table in hand is external
   * @param allowEmpty flag when true, indicates that the external table is optional
   * @param required flag when true, indicates that the external table needs to specify a file
   * @throws ParseException if the table can't be decoded from the strings
   */
  public Table(String tableName, StringPairList schemaStrs, ArrayList<ArrayList<String>> tupleStrs,
      boolean isExternal, Boolean allowEmpty, Boolean required) throws ParseException {

    this.name = tableName;
    this.isExternal = isExternal;
    this.allowEmpty = allowEmpty;
    this.required = required;

    // Convert schema info to the format that the TupleSchema constructor expects.
    String[] colNames = new String[schemaStrs.size()];
    FieldType[] colTypes = new FieldType[schemaStrs.size()];

    for (int i = 0; i < schemaStrs.size(); i++) {
      Pair<String, String> elem = schemaStrs.get(i);
      colNames[i] = elem.first;
      try {
        colTypes[i] = FieldType.stringToFieldType(elem.second);
      } catch (com.ibm.avatar.aql.ParseException e) {
        throw new ParseException(
            String.format("Error decoding field type for column %d of table %s", i, name), e);
      }
    }

    schema = new TupleSchema(colNames, colTypes);

    // Remember the original strings for the tuple values, interning them
    // and arranging them in column-major order to save memory.
    tupleStrings = new String[schemaStrs.size()][];

    for (int col = 0; col < tupleStrings.length; col++) {
      tupleStrings[col] = new String[tupleStrs.size()];
      for (int row = 0; row < tupleStrings[col].length; row++) {

        // Laura 08/11/09: avoid interning here to speed-up AOG load time for LiveText.
        // Currently, in our core annotators Tables are used only for
        // dictionary loading from AOG, and the dictionary entries
        // are made unique using a StringTable by HashDictImpl.init()
        // tupleStrings[col][row] = tupleStrs.get(row).get(col).intern();
        tupleStrings[col][row] = tupleStrs.get(row).get(col);
      }
    }

    // Conversion from strings to tuple values happens on-demand.
  }

  public AbstractTupleSchema getSchema() {
    return schema;
  }

  /**
   * @param row index (0-based) of a row in the table
   * @param col index of a logical column in the table
   * @return the original string value of the row or column
   */
  public String getStringVal(int row, int col) {
    return tupleStrings[col][row];
  }

  /**
   * This method does not force creation of the tuples themselves.
   * 
   * @return the number of tuples (rows) in this table
   */
  public int getNumRows() {
    return tupleStrings[0].length;
  }

  /**
   * NOTE: This method is NOT reentrant!!!
   * 
   * @return the tuples in the table; creates these tuples if necessary.
   */
  public TupleList getTups() {
    // The set of tuples is initialized lazily.
    if (null == tups) {
      tups = schema.objsToTups(tupleStrings, schema);

      // Log.debug("Table: Got converted %d tuples from strings.", tups.size());
    }

    return tups;
  }

  public String getName() {
    return name;
  }

  /**
   * Convert this table to its AOG representation
   * 
   * @param writer stream to send output to
   */
  public void dump(PrintWriter writer) {
    // First argument is name.
    writer.printf("CreateTable(%s,\n", StringUtils.quoteStr('"', name));

    // Second argument is table attributes: these attribute indicates whether the table is
    // external/internal, if
    // external whether empty table is allowed or not
    AOGOpTree.printIndent(writer, 1);
    writer.print("(");
    AOGOpTree.printIndent(writer, 2);
    writer.printf("%s => %s", StringUtils.quoteStr('"', "isExternal"),
        StringUtils.quoteStr('"', String.valueOf(isExternal)));
    if (isExternal) {
      writer.print(",\n");
      AOGOpTree.printIndent(writer, 2);
      if (allowEmpty != null) {
        writer.printf("%s => %s", StringUtils.quoteStr('"', "allowEmpty"),
            StringUtils.quoteStr('"', String.valueOf(allowEmpty)));
      } else {
        writer.printf("%s => %s", StringUtils.quoteStr('"', "required"),
            StringUtils.quoteStr('"', String.valueOf(required)));
      }
    } else {
      writer.print("\n");
    }
    writer.print("),\n");

    // Third argument is schema.
    AOGOpTree.printIndent(writer, 1);
    writer.print("(");
    for (int i = 0; i < schema.size(); i++) {
      String fname = schema.getFieldNameByIx(i);
      FieldType ftype = schema.getFieldTypeByIx(i);

      String ftypeStr;
      if (ftype.getIsText()) {
        ftypeStr = "Text";
      } else if (FieldType.INT_TYPE.equals(ftype)) {
        ftypeStr = "Integer";
      } else {
        throw new RuntimeException(
            String.format("Don't know how to print out field type '%s'", ftype));
      }

      writer.printf("%s => %s", StringUtils.quoteStr('"', fname),
          StringUtils.quoteStr('"', ftypeStr));

      if (i < schema.size() - 1) {
        writer.print(", ");
      }
    }
    writer.print("),\n");

    // Make sure that this.tups is initialized.
    getTups();

    // Dump the string representation of the tuples.
    for (int i = 0; i < tups.size(); i++) {
      Tuple tup = tups.getElemAtIndex(i);

      AOGOpTree.printIndent(writer, 1);
      writer.print("(");
      for (int j = 0; j < schema.size(); j++) {
        FieldType ftype = schema.getFieldTypeByIx(j);

        if (ftype.getIsText()) {
          Text t = (Text) schema.getCol(tup, j);
          writer.print(StringUtils.quoteStr('"', t.getText()));
        } else if (FieldType.INT_TYPE.equals(ftype)) {
          Integer intVal = (Integer) schema.getCol(tup, j);
          writer.print(StringUtils.quoteStr('"', intVal.toString()));
        } else {
          throw new RuntimeException(
              String.format("Don't know how to print out field type '%s'", ftype));
        }

        if (j < schema.size() - 1) {
          writer.print(", ");
        }
      }

      // No comma after last tuple.
      if (i < tups.size() - 1) {
        writer.print("),\n");
      } else {
        writer.print(")\n");
      }
    }

    // Close the top-level paren.
    writer.print(");\n");
  }
}
