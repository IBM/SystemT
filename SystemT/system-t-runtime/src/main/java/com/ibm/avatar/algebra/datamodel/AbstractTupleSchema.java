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

import com.ibm.avatar.algebra.exceptions.FieldNotFoundException;
import com.ibm.avatar.api.exceptions.FatalInternalError;

/**
 * Object that encapsulates the shared schema of a set of tuples. Also serves as a factory for
 * producing tuple field accessors.
 * 
 */
public abstract class AbstractTupleSchema implements Comparable<AbstractTupleSchema> {

  /**
   * Name of the schema, or null for an anonymous schema.
   */
  private String name = null;

  /**
   * <p>
   * Constructor for AbstractTupleSchema.
   * </p>
   */
  protected AbstractTupleSchema() {

  }

  /*
   * ABSTRACT METHODS Subclasses must implement these methods.
   */

  /**
   * Creates a new {@link com.ibm.avatar.algebra.datamodel.Tuple} object that adheres to this
   * schema.
   * 
   * @return a new tuple appropriate to this schema.
   */
  public abstract Tuple createTup();

  /**
   * Returns the number of fields in the schema.
   * 
   * @return the number of fields in the schema.
   */
  public abstract int size();

  /**
   * Method to get the data type of the field at the given index. Used mostly by classes in the
   * Datamodel package.
   * 
   * @param i index of the field in the schema
   * @return data type of the field at the given index
   */
  public abstract FieldType getFieldTypeByIx(int i);

  /**
   * Method to get name of the field at given index. Used mostly by classes in the Datamodel
   * package.
   * 
   * @param i index of the field in the schema
   * @return name of the field at given index
   */
  public abstract String getFieldNameByIx(int i);

  /**
   * Returns the index of the physical field in the tuple.
   * 
   * @return the index of the physical field in the tuple that is mapped to a given field of the
   *         schema
   */
  public abstract int getPhysicalIndex(int i);

  /**
   * Returns the index of the first logical field in the schema, given a physical index as returned
   * by {@link #getPhysicalIndex(int)}. Note that this method is not intended to be fast.
   * 
   * @param physicalIndex index of an actual (internal) field of the tuple, as returned by
   *        {@link #getPhysicalIndex(int)}
   * @return the index of the logical field in the tuple that is mapped to the indicated physical
   *         index. If there are multiple logical fields mapped to the same physical column, returns
   *         the lowest-numbered one.
   */
  public int getLogicalIndex(int physicalIndex) {
    for (int colix = 0; colix < size(); colix++) {
      if (getPhysicalIndex(colix) == physicalIndex) {
        // Note that we return the first column that matches, as described in the JavaDoc comment
        // above.
        return colix;
      }
    }

    // If we get here, we didn't find a match.
    throw new FatalInternalError("Schema %s does not use physical index %d", this, physicalIndex);
  }

  /**
   * {@inheritDoc} Subclass implementation of this method MUST be kept in sync with the
   * implementation of {@link #hashCode()}.
   */
  @Override
  public abstract boolean equals(Object obj);

  /**
   * {@inheritDoc} Comparison function for tuple schemas. Must sort base schemas lower than derived
   * ones.
   */
  @Override
  public abstract int compareTo(AbstractTupleSchema o);

  /**
   * A version of {@link #compareTo(AbstractTupleSchema)} that will not recurse on a particular
   * type; used to avoid infinite recursion when comparing schemas that involve Text types.
   * 
   * @param o schema to compare against
   */
  public abstract int compareWithoutRecursion(AbstractTupleSchema o);

  /*
   * PUBLIC METHODS The portion of the public interface that is implemented by the base class.
   */

  /**
   * Return name of the schema.
   * 
   * @return name of the schema. <code>[anonymous]</code> is returned when the schema does not have
   *         a name.
   */
  public String getName() {
    if (null == name) {
      return "[anonymous]";
    }
    return name;
  }

  /**
   * <p>
   * getHasName.
   * </p>
   * 
   * @return true if this schema has a [view] name
   */
  public boolean getHasName() {
    return (null != name);
  }

  /**
   * Set the schema name.
   * 
   * @param name name for the schema
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Get the data type of a field, by field name.
   * 
   * @param fieldName name of a field in this schema
   * @return the type of the indicated field; will throw a RuntimeException if the field does not
   *         exist
   */
  public FieldType getFieldTypeByName(String fieldName) {
    return getFieldTypeByIx(nameToIx(fieldName));
  }

  /**
   * Check if the schema contains the given field name.
   * 
   * @param fieldName name of a field that may or may not be in the schema
   * @return <code>true</code> if the schema contains a field by the indicated name
   */
  public boolean containsField(String fieldName) {
    for (int i = 0; i < size(); i++) {
      if (getFieldNameByIx(i).equals(fieldName)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns all the field names of this schema. The returned array can be modified without
   * affecting this schema.
   * 
   * @return names of the fields in this schema, in the order that they occur
   */
  public String[] getFieldNames() {
    String[] fieldNames = new String[size()];
    for (int ix = 0; ix < size(); ix++) {
      fieldNames[ix] = getFieldNameByIx(ix);
    }
    return fieldNames;
  }

  /**
   * Returns all the field types for this schema. The returned array can be modified without
   * affecting this schema.
   * 
   * @return types of the fields in this schema, in the order that they occur
   */
  public FieldType[] getFieldTypes() {
    FieldType[] fieldTypes = new FieldType[size()];
    for (int i = 0; i < size(); i++) {
      fieldTypes[i] = getFieldTypeByIx(i);
    }
    return fieldTypes;
  }

  /**
   * {@inheritDoc} Hashcode implementation for this schema.
   */
  @Override
  public int hashCode() {
    // NOTE: This method needs to be kept in sync with equals()!
    int ret = getName().hashCode();

    // Incorporate the fields of the schema (Similar to Arrays.hashCode())
    for (int i = 0; i < size(); i++) {
      ret = 31 * ret + getFieldNameByIx(i).hashCode();
      ret = 31 * ret + getFieldTypeByIx(i).hashCode();
    }

    return ret;
  }

  /**
   * {@inheritDoc} Prints the schema object in a readable format.
   * <p>
   * <b>NOTE: Keep this method in sync with the corresponding constructor
   * {@link com.ibm.avatar.algebra.datamodel.TupleSchema#TupleSchema(String)}!</b>
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();

    // Start with the schema name.
    if (null != name) {
      sb.append(getName());
    }

    // Then list the column names and types.
    sb.append('(');
    for (int i = 0; i < size(); i++) {
      sb.append(getFieldNameByIx(i));
      sb.append(" ");
      sb.append(getFieldTypeByIx(i).toString());
      if (i < size() - 1) {
        sb.append(", ");
      }
    }
    sb.append(')');

    return sb.toString();
  }

  /**
   * Create a getter object to fetch the value of a {@link com.ibm.avatar.algebra.datamodel.Span}
   * field for the given field name.
   * 
   * @param fieldName name of a field
   * @return a getter object to fetch the value of a {@link com.ibm.avatar.algebra.datamodel.Span}
   *         field for the given field name
   * @throws RuntimeException if the given field name is not of type
   *         {@link com.ibm.avatar.algebra.datamodel.Span}
   */
  public FieldGetter<Span> spanAcc(String fieldName) {
    int ix = nameToIx(fieldName);

    if (getFieldTypeByIx(ix).getIsSpan()) {
      return new FieldGetter<Span>(this, ix);
    } else {
      throw new RuntimeException(
          String.format("Expected field '%s' of %s to be of type Span, " + "but it is of type %s",
              fieldName, this, getFieldTypeByIx(ix)));
    }
  }

  /**
   * Create a getter object to fetch the value of a {@link Span} or {@link Text} field for the given
   * field name and return it as a Span, doing type conversion if necessary.
   * 
   * @param fieldName name of a field
   * @return a getter object to fetch the value of a {@link Span} field for the given field name
   * @throws RuntimeException if the given field name is not of type {@link Span}
   */
  public SpanGetter asSpanAcc(String fieldName) {
    int ix = nameToIx(fieldName);

    if (FieldType.SPANTEXT_TYPE.accepts(getFieldTypeByIx(ix))) {
      return new SpanGetter(this, ix);
    } else {
      throw new RuntimeException(String.format(
          "Expected field '%s' of %s to be of type Span or Text, " + "but it is of type %s",
          fieldName, this, getFieldTypeByIx(ix)));
    }
  }

  /**
   * Create a getter object to fetch the value of a {@link com.ibm.avatar.algebra.datamodel.Text}
   * field for the given field name.
   * 
   * @param fieldName name of a field
   * @return a getter object to fetch the value of a {@link com.ibm.avatar.algebra.datamodel.Text}
   *         field for the given field name
   * @throws RuntimeException if the given field name is not of type
   *         {@link com.ibm.avatar.algebra.datamodel.Text}
   */
  public TextGetter textAcc(String fieldName) {
    int ix = nameToIx(fieldName);

    if (getFieldTypeByIx(ix).getIsText()) {
      return new TextGetter(this, ix);
    } else {
      throw new RuntimeException(
          String.format("Expected field '%s' of %s to be of type Text, " + "but it is of type %s",
              fieldName, this, getFieldTypeByIx(ix)));
    }
  }

  /**
   * Create a getter object to fetch the value of a {@link java.lang.Integer} field for the given
   * field name.
   * 
   * @param fieldName name of a field
   * @return a getter object to fetch the value of a {@link java.lang.Integer} field for the given
   *         field name
   * @throws RuntimeException if the given field name is not of type {@link java.lang.Integer}
   */
  public FieldGetter<Integer> intAcc(String fieldName) {
    int ix = nameToIx(fieldName);

    if (FieldType.INT_TYPE == getFieldTypeByIx(ix)) {
      return new FieldGetter<Integer>(this, ix);
    } else {
      throw new RuntimeException(String.format(
          "Expected field '%s' of %s to be of type Integer, " + "but it is of type %s", fieldName,
          this, getFieldTypeByIx(ix)));
    }
  }

  /**
   * Create a getter object to fetch the value of a {@link java.lang.Float} field for the given
   * field name.
   * 
   * @param fieldName name of a field
   * @return a getter object to fetch the value of a {@link java.lang.Float} field for the given
   *         field name
   * @throws RuntimeException if the given field name is not of type {@link java.lang.Float}
   */
  public FieldGetter<Float> floatAcc(String fieldName) {
    int ix = nameToIx(fieldName);

    if (FieldType.FLOAT_TYPE == getFieldTypeByIx(ix)) {
      return new FieldGetter<Float>(this, ix);
    } else {
      throw new RuntimeException(
          String.format("Expected field '%s' of %s to be of type Float, " + "but it is of type %s",
              fieldName, this, getFieldTypeByIx(ix)));
    }
  }

  /**
   * Create a getter object to fetch the value of a
   * {@link com.ibm.avatar.algebra.datamodel.TupleList} field for the given field name.
   * 
   * @param fieldName name of a field
   * @return a getter object to fetch the value of a
   *         {@link com.ibm.avatar.algebra.datamodel.TupleList} field for the given field name
   * @throws RuntimeException if the given field name is not of type
   *         {@link com.ibm.avatar.algebra.datamodel.TupleList}
   */
  public FieldGetter<TupleList> locatorAcc(String fieldName) {
    int ix = nameToIx(fieldName);

    if (getFieldTypeByIx(ix).getIsLocator()) {
      return new FieldGetter<TupleList>(this, ix);
    } else {
      throw new RuntimeException(String.format(
          "Expected field '%s' of %s to be of type "
              + "table (...) as locator, but it is of type %s",
          fieldName, this, getFieldTypeByIx(ix)));
    }
  }

  /**
   * Create a getter object to fetch the value of a
   * {@link com.ibm.avatar.algebra.datamodel.ScalarList} field for the given field name.
   * 
   * @param fieldName name of a field
   * @return a getter object to fetch the value of a
   *         {@link com.ibm.avatar.algebra.datamodel.ScalarList} field for the given field name
   * @throws RuntimeException if the given field name is not of type
   *         {@link com.ibm.avatar.algebra.datamodel.ScalarList}
   */
  @SuppressWarnings("all")
  public FieldGetter<ScalarList> scalarListAcc(String fieldName) {
    int ix = nameToIx(fieldName);

    if (getFieldTypeByIx(ix).getIsScalarListType()) {
      return new FieldGetter<ScalarList>(this, ix);
    } else {
      throw new RuntimeException(String.format(
          "Expected field '%s' of %s to be of type ScalarList, " + "but it is of type %s",
          fieldName, this, getFieldTypeByIx(ix)));
    }
  }

  /**
   * Create a getter object to fetch the value of a {@link java.lang.Boolean} field for the given
   * field name.
   * 
   * @param fieldName name of a field
   * @return a getter object to fetch the value of a {@link java.lang.Boolean} field for the given
   *         field name
   * @throws RuntimeException if the given field name is not of type {@link java.lang.Boolean}
   */
  public FieldGetter<Boolean> boolAcc(String fieldName) {
    int ix = nameToIx(fieldName);

    if (FieldType.BOOL_TYPE == getFieldTypeByIx(ix)) {
      return new FieldGetter<Boolean>(this, ix);
    } else {
      throw new RuntimeException(String.format(
          "Expected field '%s' of %s to be of type Boolean, " + "but it is of type %s", fieldName,
          this, getFieldTypeByIx(ix)));
    }
  }

  /**
   * This method is only for use when accessors will not suffice; e.g. when writing code that must
   * deal with many different types of tuple from the same loop.
   * 
   * @param tup a tuple that follows this schema
   * @param fieldIx index of a field in this schema
   * @return the indicated field of the tuple
   */
  public Object getCol(Tuple tup, int fieldIx) {
    return tup.fields[getPhysicalIndex(fieldIx)];
  }

  /**
   * Create setter object to set the value of a {@link com.ibm.avatar.algebra.datamodel.Span} field
   * for the given field name of the schema.
   * 
   * @param fieldName name of a field in the schema
   * @return a setter object to set the value of the {@link com.ibm.avatar.algebra.datamodel.Span}
   *         field
   * @throws RuntimeException if the given field name is not of type
   *         {@link com.ibm.avatar.algebra.datamodel.Span}
   */
  public FieldSetter<Span> spanSetter(String fieldName) {
    int ix = nameToIx(fieldName);

    FieldType fieldType = getFieldTypeByIx(ix);

    // previously, this method allowed the association of a spanSetter with a Text column,
    // as call to getIsSpan() returns true for both type Text and type Span. This results in
    // defect : spanSetter allows creation of span setters for Text columns.
    // To fix, check to make sure we are not creating a span setter for a Text column.
    if (fieldType.getIsSpan() == true && fieldType.getIsText() == false) {
      return new FieldSetter<Span>(this, ix);
    } else {
      throw new RuntimeException(
          String.format("Expected field '%s' of %s to be of type Span, " + "but it is of type %s",
              fieldName, this, fieldType));
    }
  }

  /**
   * Create setter object to set the value of a {@link java.lang.Integer} field for the given field
   * name of the schema.
   * 
   * @param fieldName name of a field in the schema
   * @return a setter object to set the value of the {@link java.lang.Integer} field
   * @throws RuntimeException if the given field name is not of type {@link java.lang.Integer}
   */
  public FieldSetter<Integer> intSetter(String fieldName) {
    int ix = nameToIx(fieldName);

    if (FieldType.INT_TYPE == getFieldTypeByIx(ix)) {
      return new FieldSetter<Integer>(this, ix);
    } else {
      throw new RuntimeException(String.format(
          "Expected field '%s' of %s to be of type Integer, " + "but it is of type %s", fieldName,
          this, getFieldTypeByIx(ix)));
    }
  }

  /**
   * Create setter object to set the value of a {@link java.lang.Float} field for the given field
   * name of the schema.
   * 
   * @param fieldName name of a field in the schema
   * @return a setter object to set the value of the {@link java.lang.Float} field
   * @throws RuntimeException if the given field name is not of type {@link java.lang.Float}
   */
  public FieldSetter<Float> floatSetter(String fieldName) {
    int ix = nameToIx(fieldName);

    if (FieldType.FLOAT_TYPE == getFieldTypeByIx(ix)) {
      return new FieldSetter<Float>(this, ix);
    } else {
      throw new RuntimeException(
          String.format("Expected field '%s' of %s to be of type Float, " + "but it is of type %s",
              fieldName, this, getFieldTypeByIx(ix)));
    }
  }

  /**
   * Create setter object to set the value of a {@link com.ibm.avatar.algebra.datamodel.Text} field
   * for the given field name of the schema.
   * 
   * @param fieldName name of a field in the schema
   * @return a setter object to set the value of the {@link com.ibm.avatar.algebra.datamodel.Text}
   *         field
   * @throws RuntimeException if the given field name is not of type
   *         {@link com.ibm.avatar.algebra.datamodel.Text}
   */
  public TextSetter textSetter(String fieldName) {
    int ix = nameToIx(fieldName);

    if (false == (this instanceof TupleSchema)) {
      throw new RuntimeException(String.format(
          "Tried to create text setter for schema %s," + " which is not a base schema.", this));
    }

    if (getFieldTypeByIx(ix).getIsText()) {
      return new TextSetter((TupleSchema) this, ix);
    } else {
      throw new RuntimeException(
          String.format("Expected field '%s' of %s to be of type Text, " + "but it is of type %s",
              fieldName, this, getFieldTypeByIx(ix)));
    }
  }

  /**
   * Create setter object to set the value of a {@link com.ibm.avatar.algebra.datamodel.TupleList}
   * field for the given field name of the schema.
   * 
   * @param fieldName name of a field in the schema
   * @return a setter object to set the value of the
   *         {@link com.ibm.avatar.algebra.datamodel.TupleList} field
   * @throws RuntimeException if the given field name is not of type
   *         {@link com.ibm.avatar.algebra.datamodel.TupleList}
   */
  public FieldSetter<TupleList> locatorSetter(String fieldName) {
    int ix = nameToIx(fieldName);

    if (getFieldTypeByIx(ix).getIsLocator()) {
      return new FieldSetter<TupleList>(this, ix);
    } else {
      throw new RuntimeException(String.format(
          "Expected field '%s' of %s to be of type "
              + "table (...) as locator, but it is of type %s",
          fieldName, this, getFieldTypeByIx(ix)));
    }
  }

  /**
   * Create setter object to set the value of a {@link com.ibm.avatar.algebra.datamodel.ScalarList}
   * field for the given field name of the schema.
   * 
   * @param fieldName name of a field in the schema
   * @return a setter object to set the value of the
   *         {@link com.ibm.avatar.algebra.datamodel.ScalarList} field
   * @throws RuntimeException if the given field name is not of type
   *         {@link com.ibm.avatar.algebra.datamodel.ScalarList}
   */
  @SuppressWarnings("all")
  public FieldSetter<ScalarList> scalarListSetter(String fieldName) {
    int ix = nameToIx(fieldName);

    if (getFieldTypeByIx(ix).getIsScalarListType()) {
      return new FieldSetter<ScalarList>(this, ix);
    } else {
      throw new RuntimeException(String.format(
          "Expected field '%s' of %s to be of type ScalarList, " + "but it is of type %s",
          fieldName, this, getFieldTypeByIx(ix)));
    }
  }

  /**
   * Create setter object to set the value of a {@link java.lang.Boolean} field for the given field
   * name of the schema.
   * 
   * @param fieldName name of a field in the schema
   * @return a setter object to set the value of the {@link java.lang.Boolean} field
   * @throws RuntimeException if the given field name is not of type {@link java.lang.Boolean}
   */
  public FieldSetter<Boolean> boolSetter(String fieldName) {
    int ix = nameToIx(fieldName);

    if (FieldType.BOOL_TYPE == getFieldTypeByIx(ix)) {
      return new FieldSetter<Boolean>(this, ix);
    } else {
      throw new RuntimeException(String.format(
          "Expected field '%s' of %s to be of type Boolean, " + "but it is of type %s", fieldName,
          this, getFieldTypeByIx(ix)));
    }
  }

  /**
   * Create a setter that takes objects with no built-in casting to the appropriate runtime type.
   * Java generates warnings when you use the returned object.
   * 
   * @param fieldName the field to set
   * @param type the type of the objects you pass to the setter.
   * @return a setter that will accept Objects, even though it is setting a field that isn't of type
   *         Object
   */
  public FieldSetter<Object> genericSetter(String fieldName, FieldType type) {
    int ix = nameToIx(fieldName);

    // If passed a null type, assume the caller knows what he's doing.
    if (null == type || type.equals(getFieldTypeByIx(ix))) {
      return new FieldSetter<Object>(this, ix);
    } else {
      throw new RuntimeException(
          String.format("Expected field '%s' of %s to be of type %s, " + "but it is of type %s",
              fieldName, this, type, getFieldTypeByIx(ix)));
    }
  }

  /**
   * Creates a getter that fetches field values as {@link java.lang.Object} for the given field
   * name.
   * 
   * @param fieldName name of the field
   * @param expectedType expected data type of the fetched field
   * @return a getter that will fetch field as {@link java.lang.Object}
   * @throws RuntimeException if the given expected data type of the field is not the same as the
   *         actual data type
   */
  public FieldGetter<Object> genericGetter(String fieldName, FieldType expectedType) {
    int ix = nameToIx(fieldName);

    if (null == expectedType || expectedType.equals(getFieldTypeByIx(ix))) {
      return new FieldGetter<Object>(this, ix);
    } else {
      throw new RuntimeException(
          String.format("Expected field '%s' of %s to be of type %s, " + "but it is of type %s",
              fieldName, this, expectedType, getFieldTypeByIx(ix)));
    }
  }

  /**
   * Create a new object for copying fields from one tuple to another.
   * 
   * @param srcSchema schema of source tuples (destination tuples must be of this schema)
   * @param srcFields names of fields in the source tuples
   * @param destFields names of corresponding fields in the destination tuples
   * @return FieldCopier the new object
   */
  public FieldCopier fieldCopier(AbstractTupleSchema srcSchema, String[] srcFields,
      String[] destFields) {

    int nfield = srcFields.length;

    if (destFields.length != nfield) {
      throw new RuntimeException(
          String.format("Tried to copy %d fields to %d fields", nfield, destFields.length));
    }

    // Convert names to offsets
    int[] srcIx = new int[nfield], destIx = new int[nfield];

    for (int i = 0; i < nfield; i++) {
      srcIx[i] = srcSchema.nameToIx(srcFields[i]);
      destIx[i] = nameToIx(destFields[i]);
    }

    return new FieldCopier(srcIx, destIx, srcSchema, this);
  }

  /**
   * Convenience method for copying all the fields of the source schema without changing their
   * names.
   */
  public FieldCopier fieldCopier(AbstractTupleSchema srcSchema) {
    String[] fieldNames = srcSchema.getFieldNames();

    return fieldCopier(srcSchema, fieldNames, fieldNames);
  }

  /**
   * Returns the name of the last field of type {@link com.ibm.avatar.algebra.datamodel.Span} in the
   * schema.
   * 
   * @return name of the rightmost field of type span in the schema, or <code>null</code> if no such
   *         field exists
   */
  public String getLastSpanCol() {
    for (int i = size() - 1; i >= 0; i--) {
      if (getFieldTypeByIx(i).getIsSpan()
          // && !getFieldTypeByIx(i).getIsAttr()
          && !getFieldTypeByIx(i).getIsText()) {
        return getFieldNameByIx(i);
      }
    }
    return null;
  }

  /**
   * Returns the name of the last field of type {@link com.ibm.avatar.algebra.datamodel.Text} in the
   * schema.
   * 
   * @return name of the rightmost field of type Text in the schema, or null if no such field exists
   */
  public String getLastTextCol() {
    for (int i = size() - 1; i >= 0; i--) {
      if (getFieldTypeByIx(i).getIsText()
      // && !getFieldTypeByIx(i).getIsAttr()
      ) {
        return getFieldNameByIx(i);
      }
    }
    return null;
  }

  /**
   * Returns the name of the first field of type {@link com.ibm.avatar.algebra.datamodel.Text} in
   * the schema.
   * 
   * @return name of the leftmost field of type Text in the schema, or null if no such field exists
   */
  @Deprecated
  public String getFirstTextCol() {
    for (int i = 0; i <= size() - 1; i++) {
      if (getFieldTypeByIx(i).getIsText()
      // && !getFieldTypeByIx(i).getIsAttr()
      ) {
        return getFieldNameByIx(i);
      }
    }
    return null;
  }

  /**
   * Returns the name of the last {@link com.ibm.avatar.algebra.datamodel.Text} or
   * {@link com.ibm.avatar.algebra.datamodel.Span} field in the schema.
   * 
   * @return name of the rightmost field of type Text *or* Span in the schema, or null if no such
   *         field exists
   */
  public String getLastTextOrSpanCol() {
    for (int i = size() - 1; i >= 0; i--) {
      if ((getFieldTypeByIx(i).getIsSpan() || getFieldTypeByIx(i).getIsText())
      // && !getFieldTypeByIx(i).getIsAttr()
      ) {
        return getFieldNameByIx(i);
      }
    }
    return null;
  }

  /**
   * Returns the name of the first {@link com.ibm.avatar.algebra.datamodel.Text} or
   * {@link com.ibm.avatar.algebra.datamodel.Span} field in the schema.
   * 
   * @return name of the leftmost field of type Text *or* Span in the schema, or null if no such
   *         field exists
   */
  @Deprecated
  public String getFirstTextOrSpanCol() {
    for (int i = 0; i <= size() - 1; i++) {
      if ((getFieldTypeByIx(i).getIsSpan() || getFieldTypeByIx(i).getIsText())
      // && !getFieldTypeByIx(i).getIsAttr()
      ) {
        return getFieldNameByIx(i);
      }
    }
    return null;
  }

  /**
   * Returns the index of the given field name in the schema.
   * 
   * @param fieldName field name
   * @return index of the given field name in the schema
   * @throws RuntimeException if schema does not contain the given field name
   */
  public int nameToIx(String fieldName) {
    for (int i = 0; i < size(); i++) {
      if (getFieldNameByIx(i).equals(fieldName)) {
        return i;
      }
    }

    // If we get here, we didn't find the requested name.
    if (getHasName()) {
      throw new FieldNotFoundException(getName(), fieldName, getFieldNames(), getFieldTypes());
    } else {
      throw new FieldNotFoundException(fieldName, getFieldNames(), getFieldTypes());
    }
    // throw new RuntimeException (String.format ("Schema %s does not contain field name '%s'",
    // this, fieldName));
  }

  /**
   * <p>
   * unionCompatible.
   * </p>
   * 
   * @return true if tuples from this schema can be UNIONed safely with tuples from other
   */
  public boolean unionCompatible(AbstractTupleSchema other) {
    if (other == this) {
      return true;
    }

    // Check that all column names are the same
    if (size() != other.size()) {
      return false;
    }
    for (int i = 0; i < size(); i++) {
      if (false == getFieldNameByIx(i).equals(other.getFieldNameByIx(i))) {
        return false;
      }
    }

    for (int i = 0; i < size(); i++) {
      FieldType type = getFieldTypeByIx(i);
      FieldType otherType = other.getFieldTypeByIx(i);

      if (type.getIsNullType() || otherType.getIsNullType()) {
        // the null type is always union-compatible with any other type
      } else if (type.getIsSpanOrText() && otherType.getIsSpanOrText()) {
        // Text and span types are considered equal for comparison
        // purposes.
      } else if (type.getIsScalarListType() && otherType.getIsScalarListType()
          && (type.getScalarListType().equals(FieldType.NULL_TYPE)
              && !otherType.getScalarListType().equals(FieldType.NULL_TYPE)
              || !type.getScalarListType().equals(FieldType.NULL_TYPE)
                  && otherType.getScalarListType().equals(FieldType.NULL_TYPE))) {
        // List types are considered equal for comparison purposes
        // when the type of elements of one of the lists is unknown
      } else if (type.getIsScalarListType() && otherType.getIsScalarListType()
          && (type.getScalarListType().getIsText() && otherType.getScalarListType().getIsText())) {
        // List of Text types of are considered equal for comparison
        // purposes for any nested Text field type
      } else {
        if (false == type.equals(otherType)) {
          return false;
        }
      }
    }

    // ADDITIONAL CHECKS GO HERE

    return true;
  }

  /**
   * Given a list of schemas that are compatible from the perspective of
   * {@link #unionCompatible(AbstractTupleSchema)}, determine the most general schema that safely
   * encompasses all types in the list.
   * <p>
   * <b>NOTE:</b> This method must be kept in sync with
   * {@link com.ibm.avatar.algebra.datamodel.TupleSchema#unionCompatible(AbstractTupleSchema)}.
   * 
   * @param schemas schemas to be combined together (usually as inputs to a union operator).
   *        Upstream code should have already verified union-compatibility of these schemas.
   * @return most specific generalized schemas that the fields of the indicated schemas can be cast
   *         to
   */
  public static TupleSchema generalize(ArrayList<? extends AbstractTupleSchema> schemas) {
    // Use the first schema as a starting template
    AbstractTupleSchema firstSchema = schemas.get(0);

    FieldType[] coltypes = firstSchema.getFieldTypes();
    String[] colnames = firstSchema.getFieldNames();

    // Walk through the columns of the other schemas, replacing types with more general types as
    // needed. Note that it's
    // ok to modify the arrays in place, as getFieldNames() creates a copy.
    for (int i = 1; i < schemas.size(); i++) {
      AbstractTupleSchema otherSchema = schemas.get(i);

      // Sanity check
      if (false == firstSchema.unionCompatible(otherSchema)) {
        throw new FatalInternalError(
            "Incompatible schemas %s and %s passed to TupleSchema.generalize()", firstSchema,
            otherSchema);
      }

      for (int colix = 0; colix < coltypes.length; colix++) {
        FieldType curType = coltypes[colix];
        FieldType otherType = otherSchema.getFieldTypeByIx(colix);

        if (curType.getIsScalarListType() && curType.getScalarListType().equals(FieldType.NULL_TYPE)
            && false == (otherType.getScalarListType().equals(FieldType.NULL_TYPE))) {
          // Current "best" type is an untyped scalar list, and another input has a more specific
          // type of scalar list
          // in this position. Use the more specific type.
          // TODO: Revisit this logic. It may be better to return list of Null types in this case
          coltypes[colix] = otherType;
        } else if (curType.getIsNullType() && (curType != otherType)) {
          // If one type is null, override it with any non-null type.
          coltypes[colix] = otherType;
        } else if (FieldType.SPAN_TYPE.equals(curType) || FieldType.SPAN_TYPE.equals(otherType)) {
          // If one type is Span, any Text objects from the other type should be converted to Span
          coltypes[colix] = FieldType.SPAN_TYPE;
        } else {
          // Otherwise, leave the current type in place
        }
      }
    }

    return new TupleSchema(colnames, coltypes);

  }

  /**
   * Returns true if both schemas have their fields in the same positions and share the same data
   * types.
   * 
   * @param otherSchema another schema, which can be a base or derived schema
   * @return <code>true</code> if both schemas put their fields in the same positions within the
   *         tuples
   */
  public boolean sameTupleFormat(AbstractTupleSchema otherSchema) {
    if (otherSchema == this) {
      return true;
    }

    // Check that all column names are the same
    if (size() != otherSchema.size()) {
      return false;
    }
    for (int i = 0; i < size(); i++) {
      if (false == getFieldNameByIx(i).equals(otherSchema.getFieldNameByIx(i))) {
        return false;
      }
    }

    // Check that types are the same
    for (int i = 0; i < size(); i++) {
      FieldType type = getFieldTypeByIx(i);
      FieldType otherType = otherSchema.getFieldTypeByIx(i);

      if (type.getIsText() && otherType.getIsText()) {
        // Any two text types are considered equal for schema comparison
        // purposes.
      }
      // else if (type.getIsText () && otherType.getIsSpan () || type.getIsSpan () &&
      // otherType.getIsText ()) {
      // // Text and span types are considered equal for comparison
      // // purposes.
      // }
      else {
        if (false == type.equals(otherType)) {
          return false;
        }
      }
    }

    // Check that columns map to the same physical locations in the tuples.
    for (int i = 0; i < size(); i++) {
      if (getPhysicalIndex(i) != otherSchema.getPhysicalIndex(i)) {
        return false;
      }
    }
    // ADDITIONAL CHECKS GO HERE

    return true;
  }

  /*
   * INTERNAL METHODS For use by derived classes.
   */

  /**
   * Compare the names and types of the fields in a schema, without reference to the underlying
   * mapping to actual tuple fields.
   * 
   * @return a negative integer, zero, or a positive integer as this schema sorts lower, equal to,
   *         or higher than the specified schema.
   */
  protected int compareNamesAndTypes(AbstractTupleSchema o) {
    int val;

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
    val = size() - o.size();
    if (val != 0) {
      return val;
    }

    for (int i = 0; i < size(); i++) {
      val = getFieldNameByIx(i).compareTo(o.getFieldNameByIx(i));
      if (val != 0) {
        return val;
      }

      val = getFieldTypeByIx(i).compareTo(o.getFieldTypeByIx(i));
      if (val != 0) {
        return val;
      }
    }

    // If we get here, the schemas are the same.
    return 0;
  }

}
