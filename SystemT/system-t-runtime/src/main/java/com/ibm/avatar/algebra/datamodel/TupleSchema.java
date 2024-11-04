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

import java.util.ArrayList;
import java.util.Arrays;

import com.ibm.avatar.algebra.util.lang.LangCode;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.api.exceptions.SchemaParseException;
import com.ibm.avatar.logging.Log;

/**
 * This class encapsulates the "real" schema of an operator. That is, the schema that exactly
 * describes the names and types of every field of the tuple, in the order that they actually appear
 * in the tuple.
 *
 */
public class TupleSchema extends AbstractTupleSchema {

  /*
   * FIELDS
   */

  /**
   * Pre-initialized schema with zero columns.
   */
  public static final AbstractTupleSchema EMPTY_SCHEMA =
      new TupleSchema(new String[0], new FieldType[0]);

  /**
   * Names of the fields in the schema, in the order that they appear in tuples.
   */
  protected String[] colnames;

  /** Types of the fields in the schema, in the order they appear. */
  protected FieldType[] coltypes;

  /*
   * CONSTRUCTORS
   */

  /**
   * Constructor to create an anonymous schema object from the given field names and field types.
   *
   * @param colnames name for the schema fields
   * @param coltypes data type for schema fields
   */
  public TupleSchema(String[] colnames, FieldType[] coltypes) {
    this.colnames = colnames;
    this.coltypes = coltypes;

    validate();
  }

  /**
   * Convenience constructor for single field anonymous schemas.
   *
   * @param colname name for schema field
   * @param coltype data type for schema field
   */
  public TupleSchema(String colname, FieldType coltype) {
    this(new String[] {colname}, new FieldType[] {coltype});
  }

  /**
   * Create an anonymous schema by adding additional fields to an existing schema.
   *
   * @param baseSchema the schema this new schema will be based on
   * @param names name of additional fields to add (must not be present in baseSchema)
   * @param types data type of additional fields to add
   */
  public TupleSchema(AbstractTupleSchema baseSchema, String[] names, FieldType[] types) {

    final boolean debug = false;

    if (names.length != types.length) {
      throw new IllegalArgumentException("Arrays are of different lengths");
    }

    int baseWidth = baseSchema.size();

    for (int i = 0; i < names.length; i++) {
      for (int j = 0; j < baseWidth; j++) {
        String fieldName = baseSchema.getFieldNameByIx(j);
        if (fieldName.equals(names[i])) {
          throw new IllegalArgumentException(String
              .format("Base schema %s already has a column called '%s'", baseSchema, names[i]));
        }
      }
    }

    colnames = new String[baseWidth + names.length];
    coltypes = new FieldType[baseWidth + types.length];
    for (int i = 0; i < baseWidth; i++) {
      colnames[i] = baseSchema.getFieldNameByIx(i);
      coltypes[i] = baseSchema.getFieldTypeByIx(i);
    }

    for (int i = baseWidth; i < colnames.length; i++) {

      colnames[i] = names[i - baseWidth];
      coltypes[i] = types[i - baseWidth];
    }

    // System.arraycopy(names, 0, colnames, baseWidth, names.length);
    // System.arraycopy(types, 0, coltypes, baseWidth, types.length);

    if (debug) {
      Log.debug("Adding cols %s of types %s\n    To schema: %s\n    Produces: %s",
          Arrays.toString(names), Arrays.toString(types), baseSchema, this);
    }

    // fixTextFields();

    validate();
  }

  /**
   * Create an anonymous schema by adding a single field to an existing schema.
   *
   * @param baseSchema the schema this new schema will be based on
   * @param name name of the additional field to add (must not be present in baseSchema)
   * @param type data type of the additional field to add (must not be Text)
   */
  public TupleSchema(AbstractTupleSchema baseSchema, String name, FieldType type) {
    this(baseSchema, new String[] {name}, new FieldType[] {type});
  }

  /**
   * Create an anonymous schema by adding a single Text field to an existing schema.
   *
   * @param baseSchema the schema this new schema will be based on
   * @param colName name of additional Text field to add (must not be present in baseSchema)
   */
  public TupleSchema(AbstractTupleSchema baseSchema, String colName) {
    this(baseSchema, colName, FieldType.TEXT_TYPE);
  }

  /**
   * Constructor that creates a duplicate of a schema.
   *
   * @param schema schema to copy
   * @param anon <code>true</code> to create an anonymous schema; <code>false</code> to create an
   *        exact copy of the input (with the same name)
   */
  public TupleSchema(AbstractTupleSchema schema, boolean anon) {

    // Deep copy, just in case.
    int width = schema.size();
    colnames = new String[width];
    coltypes = new FieldType[width];

    for (int i = 0; i < width; i++) {
      colnames[i] = schema.getFieldNameByIx(i);
      coltypes[i] = schema.getFieldTypeByIx(i);
    }

    if (anon) {
      setName(null);
    } else {
      setName(schema.getName());
    }
  }

  /**
   * Constructor that parses the string representation returned by
   * {@link com.ibm.avatar.algebra.datamodel.AbstractTupleSchema#toString()}
   */
  public TupleSchema(String schemaStr) throws ParseException {
    // Format of an encoded schema is:
    // [view name, if any](name1 type1, name2 type2, ... , name_k type_k)

    // Check for the optional "view name" part of the encoded schema
    String parensPart = schemaStr;
    if (false == schemaStr.startsWith("(")) {
      // Encoded name doesn't start with parens; everything up to the parens must be the view name.
      int nameLen = schemaStr.indexOf('(');

      if (-1 == nameLen) {
        throw new SchemaParseException(schemaStr,
            "String does not contain the expected left parenthesis character");
      }
      setName(schemaStr.substring(0, nameLen));
      parensPart = schemaStr.substring(nameLen);
    }

    if (false == schemaStr.endsWith(")")) {
      throw new SchemaParseException(schemaStr,
          "String does not end with a right parenthesis character");
    }

    // Strip off the parens so we can split the list of arguments into its parts.
    String listPart = parensPart.substring(1, parensPart.length() - 1);
    String[] pairs = listPart.split(", ");

    this.colnames = new String[pairs.length];
    this.coltypes = new FieldType[pairs.length];

    for (int i = 0; i < pairs.length; i++) {
      String pairStr = pairs[i];

      // Everything up to the first space is the name of the column.
      int nameLen = pairStr.indexOf(' ');
      if (-1 == nameLen) {
        throw new SchemaParseException(schemaStr, "Element %d of schema does not contain a space",
            i);
      }

      colnames[i] = pairStr.substring(0, nameLen);

      String typeName = pairStr.substring(nameLen + 1);

      try {
        coltypes[i] = FieldType.stringToFieldType(typeName);
      } catch (com.ibm.avatar.aql.ParseException e) {
        throw new SchemaParseException(schemaStr,
            "Cannot parse field type string '%s' for field %d (%s)", typeName, i, colnames[i]);
      }
    }
  }

  /*
   * FACTORY METHODS
   */

  // Currently no factory methods exist.

  /*
   * PUBLIC METHODS Same signature as abstract methods in superclass.
   */

  /**
   * {@inheritDoc} Returns a new Tuple with this schema.
   */
  @Override
  public Tuple createTup() {
    return Tuple.getTup(size());
  }

  /**
   * {@inheritDoc} Returns the number of fields in the schema.
   */
  @Override
  public int size() {
    return colnames.length;
  }

  /**
   * {@inheritDoc} Method to get the data type of the field at the given index.
   */
  @Override
  public FieldType getFieldTypeByIx(int ix) {
    return coltypes[ix];
  }

  /**
   * Set the data type of the field at the given index with the given type.
   *
   * @param ix index of the field whose data type needs to be set
   * @param type data type to be set
   */
  public void setFieldTypeByIx(int ix, FieldType type) {
    coltypes[ix] = type;
  }

  /**
   * {@inheritDoc} Return the field name of the field at the given index in this schema.
   */
  @Override
  public String getFieldNameByIx(int i) {
    return colnames[i];
  }

  /**
   * {@inheritDoc} Returns the actual index in the tuple for the given index in this schema.
   */
  @Override
  public int getPhysicalIndex(int i) {
    // With this kind of a tuple schema, what you see is what you get.
    return i;
  }

  /**
   * {@inheritDoc} Implementation must be synchronized with {@link #hashCode()}
   */
  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }

    if (!(obj instanceof TupleSchema)) {
      return false;
    }

    if (null == obj) {
      throw new RuntimeException("Comparison against null schema");
    }

    AbstractTupleSchema o = (AbstractTupleSchema) obj;

    return (0 == compareTo(o));

  }

  /**
   * {@inheritDoc} Comparison function for tuple schemas. Must sort base schemas lower than derived
   * ones.
   */
  @Override
  public int compareTo(AbstractTupleSchema o) {

    if (this == o) {
      return 0;
    }

    // Check types
    if (o instanceof DerivedTupleSchema) {
      // Base schema sorts lower
      return -1;
    }

    // Next compare the fields of the schemas themselves.
    return super.compareNamesAndTypes(o);
  }

  /**
   * {@inheritDoc} A version of {@link #compareTo(AbstractTupleSchema)} that will not recurse on a
   * particular type; used to avoid infinite recursion when comparing schemas that involve Text
   * types.
   * 
   * @deprecated Text type is no longer parameterized
   * @param o schema to compare against
   */
  @Deprecated
  @Override
  public int compareWithoutRecursion(AbstractTupleSchema o) {

    if (this == o) {
      return 0;
    }

    // Check types
    if (o instanceof DerivedTupleSchema) {
      // Base schema sorts lower
      return -1;
    }

    int val = 0;

    // Next compare schema names
    if (null == getName()) {
      if (null != o.getName())
        return -1;
    } else {
      val = getName().compareTo(o.getName());
      if (val != 0) {
        return val;
      }
    }

    // Next try the pieces of the schema.
    val = colnames.length - o.size();
    if (val != 0) {
      return val;
    }

    for (int i = 0; i < coltypes.length; i++) {
      val = colnames[i].compareTo(o.getFieldNameByIx(i));
      if (val != 0) {
        return val;
      }

      // System.err.printf("Comparing %s to %s\n", coltypes[i], o
      // .getFieldTypeByIx(i));
      boolean safeToRecurse = true;
      // if (coltypes[i].getIsText ()) {
      // // Text type; don't recurse if this schema is for the source
      // // tuples of the Text object
      // if (coltypes[i].getDocType ().equals (this)) {
      // safeToRecurse = false;
      // }
      // else {
      // safeToRecurse = true;
      // }
      // }
      // else {
      // // Non-text type; safe to recurse
      // safeToRecurse = true;
      // }
      if (safeToRecurse) {
        // System.err.printf(" --> Recursing\n");
        val = coltypes[i].compareTo(o.getFieldTypeByIx(i));
        if (val != 0) {
          return val;
        }
      } else {
        // System.err.printf(" --> Skipping recursion\n");
      }
    }

    // If we get here, the schemas are the same.
    return 0;
  }

  /*
   * SPECIAL PUBLIC METHODS Specific to this class, and used mostly within this package.
   */

  /**
   * Internal method to allow classes in the Datamodel package to get at fields directly by name.
   * Classes outside this package should use accessors.
   */
  protected Object getFieldByName(Tuple tup, String fieldName) {
    int ix = nameToIx(fieldName);
    return tup.fields[ix];
  }

  /**
   * {@inheritDoc} Method to get field data type in schema by given field name.
   */
  @Override
  public FieldType getFieldTypeByName(String fieldName) {
    int ix = nameToIx(fieldName);
    return coltypes[ix];
  }

  /**
   * Convert the string representation of a collection of tuples to tuple values.
   *
   * @param strs the string representation of a set of tuples, in COLUMN-MAJOR order (the first
   *        index is the column and the second is the row)
   * @param externalSchema "external" schema of the table, with columns in the same order as the
   *        elements of the strings list
   * @return the tuples, converted to this schema
   */
  public TupleList objsToTups(String[][] strs, TupleSchema externalSchema) {

    // We assume English for now
    // TODO: Provide some way of passing language codes to this method.
    final LangCode LANGUAGE = LangCode.en;

    TupleList ret = new TupleList(this);

    int numRows = strs[0].length;
    int numCols = strs.length;

    // The strings are in column-major order, but the tuples will be in
    // row-major order.
    for (int row = 0; row < numRows; row++) {
      // Create a tuple.
      Tuple tup = createTup();

      // Populate the columns of the tuple.
      // We iterate through the columns in logical order, filling in the
      // appropriate physical columns of the actual tuple.
      for (int logicalCol = 0; logicalCol < numCols; logicalCol++) {
        String valStr = strs[logicalCol][row];
        // System.err.printf ("strs[%d][%d] is '%s'\n", logicalCol, row, valStr);

        // Figure out the mapping from logical to physical columns.
        int physicalCol = externalSchema.getPhysicalIndex(logicalCol);
        FieldType colType = coltypes[physicalCol];

        try {
          if (colType.getIsText()) {
            // Strings are converted to Text objects, in order to make
            // tables look like views.
            Text text = new Text(valStr, LANGUAGE);
            tup.fields[physicalCol] = text;

          } else if (FieldType.INT_TYPE.equals(colType)) {

            // Integer values are stored internally as Java Integers
            tup.fields[physicalCol] = Integer.valueOf(valStr);

          } else if (FieldType.BOOL_TYPE.equals(colType)) {

            // Boolean values are stored internally as Java Booleans
            tup.fields[physicalCol] = Boolean.valueOf(valStr);

          } else if (FieldType.FLOAT_TYPE.equals(colType)) {
            tup.fields[physicalCol] = Float.valueOf(valStr);
          } else {
            throw new IllegalArgumentException(
                "Don't know how to decode field type " + colType + " from a string");
          }
        } catch (IllegalArgumentException e) {
          // Wrap any JVM data conversion exceptions in something with location information.
          // TODO: Should this method throw a checked exception instead?
          throw new FatalInternalError(
              "Error converting value '%s' at row %d, column %d (physical column %d) of "
                  + "array of strings to type '%s' (target schema is %s; physical schema is %s).  "
                  + "Note that the input to this method should be an array in COLUMN-MAJOR order.",
              valStr, row + 1, logicalCol + 1, physicalCol + 1, colType, externalSchema, this);
        }
      }

      ret.add(tup);
    }

    return ret;
  }

  /**
   * Convert the string representation of a collection of tuples to tuple values.
   *
   * @param strs the string representation of a set of tuples
   * @param colNames names of columns in the tuple schema, in the same order as the elements of the
   *        strings list
   * @return the tuples, converted to this schema
   */
  public TupleList objsToTups(ArrayList<ArrayList<String>> strs, ArrayList<String> colNames) {

    // We assume English for now
    // TODO: Provide some way of passing language codes to this method.
    final LangCode LANGUAGE = LangCode.en;

    TupleList ret = new TupleList(this);

    for (ArrayList<String> tupStrs : strs) {
      // Create a tuple.
      Tuple tup = createTup();

      // Populate the columns of the tuple.
      for (int i = 0; i < tupStrs.size(); i++) {
        String valStr = tupStrs.get(i);
        String colNameStr = colNames.get(i);

        int colix = nameToIx(colNameStr);
        FieldType colType = getFieldTypeByIx(colix);

        if (colType.getIsText()) {

          // Strings just get passed right through.
          // tup.setColumnValueInternalUseOnly(colix, valStr);

          // Strings are converted to Text objects, in order to make
          // tables look like views.
          Text text = new Text(valStr, LANGUAGE);
          tup.fields[colix] = text;

        } else if (FieldType.INT_TYPE.equals(colType)) {

          tup.fields[colix] = Integer.valueOf(valStr);

        } else if (FieldType.BOOL_TYPE.equals(colType)) {

          tup.fields[colix] = Boolean.valueOf(valStr);

        } else {
          throw new RuntimeException(
              "Don't know how to decode field type " + colType + " from a string");
        }
      }

      ret.add(tup);
    }

    return ret;
  }

  /*
   * PRIVATE METHODS
   */

  /** Sanity checks for schema names and types. */
  private void validate() {

    // Same number of types as names...
    if (colnames.length != coltypes.length) {
      throw new RuntimeException(String.format(
          "Tried to create a schema " + "with %d column names " + "and %d column types.",
          colnames.length, coltypes.length));
    }

    // No non-null names...
    for (int i = 0; i < size(); i++) {
      if (null == colnames[i]) {
        throw new RuntimeException(String
            .format("Column %d (cols range from 0 to %d) " + "has NULL for a name", i, size() - 1));
      }
    }

    // No duplicate names...
    for (int i = 0; i < size(); i++) {
      for (int j = i + 1; j < size(); j++) {
        if (colnames[i].equals(colnames[j])) {
          throw new RuntimeException(
              String.format("Columns %d and %d " + "of schema %s have the same name", i, j, this));
        }
      }
    }

    // Make sure that every Text type points to this schema as the text
    // tuple.
    // for (int i = 0; i < getWidth(); i++) {
    // if (coltypes[i].getIsText()
    // && coltypes[i].getDocType() != this
    // && coltypes[i].getDocType() != null) {
    // throw new RuntimeException(String.format(
    // "Column %d of schema %s is of Text type "
    // + "but points to another schema (%s)", i, this,
    // coltypes[i].getDocType()));
    // }
    // }
  }

  /**
   * Return field accessors for all span fields, in reverse order. This is to be used by the ToHTML
   * and other visualizer which want to find span fields to display but are not told which fields
   * are spans.
   * 
   * @return list of field accessors for all span fields (in reverse order)
   */
  public ArrayList<FieldGetter<Span>> getSpanGetters() {
    ArrayList<FieldGetter<Span>> list = new ArrayList<FieldGetter<Span>>();
    for (int i = size() - 1; i >= 0; i--) {
      if (getFieldTypeByIx(i).getIsSpan()
          // && !getFieldTypeByIx(i).getIsAttr()
          && !getFieldTypeByIx(i).getIsText()) {
        String colName = getFieldNameByIx(i);
        FieldGetter<Span> spanGetter = spanAcc(colName);
        list.add(spanGetter);
      }
    }
    return list;
  }

}
