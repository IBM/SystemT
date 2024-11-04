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

import com.ibm.avatar.algebra.function.base.TableLocator;
import com.ibm.avatar.aql.AQLParserBase;
import com.ibm.avatar.aql.ParseException;

/**
 * This class encapsulates the data type of a field in a
 * {@link com.ibm.avatar.algebra.datamodel.Tuple} object. This class is mostly a container for
 * constants. In particular, it contains {@link com.ibm.avatar.algebra.datamodel.FieldType}
 * constants for all the supported scalar data types.
 * 
 */
public class FieldType implements Comparable<FieldType> {

  /**
   * A Java String object, used only for temporary objects;
   * <p>
   * 
   * @deprecated As of v3.0.1. Use {@link com.ibm.avatar.algebra.datamodel.Text} field instead.
   */
  @Deprecated
  public static final FieldType STRING_TYPE = new FieldType("String", String.class);
  /**
   * A regular expression literal (currently a Java string internally)
   */
  public static final FieldType REGEX_TYPE = new FieldType("Regex", String.class);

  /**
   * An Integer field.
   */
  public static final FieldType INT_TYPE = new FieldType("Integer", Integer.class);

  /**
   * A Float field.
   */
  public static final FieldType FLOAT_TYPE = new FieldType("Float", Float.class);

  /**
   * A Boolean field. Nulls represent UNKNOWN.
   */
  public static final FieldType BOOL_TYPE = new FieldType("Boolean", Boolean.class);

  /**
   * A Span field.
   */
  public static final FieldType SPAN_TYPE = new FieldType("Span", Span.class);

  /**
   * A Text field.
   */
  public static final FieldType TEXT_TYPE = new FieldType("Text", Text.class);

  /**
   * A Span or Text field.
   */
  public static final FieldType SPANTEXT_TYPE = new FieldType("Span or Text", SpanText.class);

  /**
   * A Null object field. Used to represent the type of a clearly unknown object.
   */
  public static final FieldType NULL_TYPE = new FieldType("Null", null);

  /**
   * Placeholder for an unknown type, used during the early stages of compilation.
   * <p>
   * 
   * @deprecated As of v3.0.1. Use {@link com.ibm.avatar.algebra.datamodel.FieldType#NULL_TYPE}
   *             instead.
   */
  @Deprecated
  public static final FieldType UNKNOWN_TYPE = new FieldType("Unknown", null);

  /** A type representing scalar list of NULL_TYPE */
  public static final FieldType SCALAR_LIST_TYPE = makeDummyScalarListType();

  /** Prefix at the beginning of a parameterized scalar list type. */
  private static final String SCALAR_LIST_TYPE_NAME_PREFIX = "ScalarList of ";

  /** Prefix at the beginning of a record locator type name (before the record type) */
  private static final String RECORD_LOCATOR_TYPE_NAME_PREFIX = "table ";

  /** Suffix at the end of a record locator type name. */
  private static final String RECORD_LOCATOR_TYPE_NAME_SUFFIX = " as locator";

  /** List of all the known singleton FieldType objects. */
  public static final FieldType[] SINGLETONS = {SPAN_TYPE, TEXT_TYPE, SPANTEXT_TYPE, INT_TYPE,
      FLOAT_TYPE, BOOL_TYPE, SCALAR_LIST_TYPE, NULL_TYPE};

  /*
   * ATTRIBUTES THAT ARE COMMON ACROSS ALL TYPES
   */

  /**
   * Type name string; also acts as a unique identifier. For base types, this is simply the actual
   * type; for references, this string is built up from the type being referred to.
   */
  private String typename = null;

  /**
   * The Java class that implements this type at runtime.
   */
  private Class<?> runtimeClass = null;

  /*
   * RECORD LOCATOR TYPE ATTRIBUTES
   */

  /**
   * If this field is a record locator or nested table type, the schema of the tuple pointed to.
   */
  private AbstractTupleSchema reftype = null;

  /**
   * TRUE if this field is a locator (pointer) type
   */
  private boolean isLocator = false;

  /*
   * LIST TYPE ATTRIBUTES
   */

  /**
   * If this field is a tuple list, the schema of the tuples in the list.
   */
  // private AbstractTupleSchema listtype = null;

  /**
   * If this field is a list of scalars, the type of the scalar.
   */
  private FieldType nestedscalartype = null;

  /*
   * SPAN TYPE ATTRIBUTES
   */

  /**
   * If this field is either a derived text type (e.g. created from another text field by some
   * transformation) or a span over such a text field, a pointer back to the original text type.
   */
  private FieldType source;

  /**
   * Constructor for base types; should only be called from the static initializers above.
   * 
   * @param typename internal name of the type
   * @param runtimeClass Java class that implements this type in the SystemT runtime
   */
  // This constructor was made public by mistake, by someone explicitly ignoring the comment of this
  // constructor. Making
  // it private again.
  private FieldType(String typename, Class<?> runtimeClass) {
    this.typename = typename.intern();
    this.runtimeClass = runtimeClass;
  }

  /**
   * Constructor for table types (i.e. function parameters defined by "table (...) [as locator]")
   * 
   * @param reftype type of the tuples represented by this field type
   * @param isLocator true if the tuples are represented as a locator (pointer); false if they are
   *        directly embedded
   */
  public FieldType(AbstractTupleSchema reftype, boolean isLocator) {
    this.reftype = reftype;
    this.isLocator = isLocator;

    // Construct the name "table (...) as locator"
    String nameStr = RECORD_LOCATOR_TYPE_NAME_PREFIX + reftype.toString();
    if (isLocator) {
      nameStr = nameStr + RECORD_LOCATOR_TYPE_NAME_SUFFIX;
    }
    this.typename = nameStr.intern();

    // Record locator == ptr to TupleList
    this.runtimeClass = TupleList.class;
    this.nestedscalartype = null;
    this.source = null;
  }

  /**
   * Constructor for list of scalars type. Needs the second arg due to copying constructor having a
   * single FieldType argument.
   */
  private FieldType(FieldType nestedScalarType, boolean isScalarList) {
    this.nestedscalartype = nestedScalarType;
    this.runtimeClass = ScalarList.class;
  }

  /**
   * Constructs a {@link ScalarList} type.
   * 
   * @param scalarType the type of the scalar values in this list
   * @return FieldType a ScalarList type with scalar values of the given type
   */
  public static FieldType makeScalarListType(FieldType scalarType) {

    // Sanity check to forbid lists of non-scalar types
    if (!scalarType.getIsScalarType() && !scalarType.getIsNullType()) {
      throw new RuntimeException(
          String.format("Cannot make ScalarList of non-scalar type '%s'", scalarType));
    }

    FieldType ret = new FieldType(scalarType, true);

    if (scalarType.getIsNullType()) {
      ret.typename = "ScalarList";
    } else {
      ret.typename = String.format("ScalarList of %s", scalarType.typename).intern();
    }

    return ret;
  }

  /**
   * Creates a dummy ScalarList Type that is used to initialize static final field SCALAR_LIST_TYPE
   */
  private static FieldType makeDummyScalarListType() {
    return makeScalarListType(NULL_TYPE);
  }

  /**
   * Copy constructor. Copies all fields.
   * 
   * @param t type to duplicate
   */
  public FieldType(FieldType t) {
    this.typename = t.typename;
    this.runtimeClass = t.runtimeClass;
    this.reftype = t.reftype;
    this.nestedscalartype = t.nestedscalartype;
    this.source = t.source;
  }

  @Override
  public String toString() {
    return typename;
  }

  /**
   * Utility method to convert the AQL name of a type to the appropriate FieldType object.
   * 
   * @param typeName AQL name of a type
   * @return FieldType object that represents the indicated type
   */
  public static FieldType stringToFieldType(String typeName) throws ParseException {
    // Special case for backward compatibility
    if ("String".equals(typeName))
      typeName = TEXT_TYPE.getTypeName();

    // For backward compatibility: Unknown can be represented as Null
    if ("Unknown".equals(typeName))
      typeName = NULL_TYPE.getTypeName();

    // Scalar types
    for (FieldType singleton : SINGLETONS) {
      if (typeName.equals(singleton.getTypeName())) {
        return singleton;
      }
    }

    // List types
    if (typeName.startsWith(SCALAR_LIST_TYPE_NAME_PREFIX)) {
      // Parameterized list type. Determine the list element type recursively.
      String innerTypeName = typeName.substring(SCALAR_LIST_TYPE_NAME_PREFIX.length());
      FieldType innerType = stringToFieldType(innerTypeName);
      return makeScalarListType(innerType);
    }

    // Table types
    if (typeName.startsWith(RECORD_LOCATOR_TYPE_NAME_PREFIX)) {

      String recordTypeStr;
      boolean isLocator = typeName.endsWith(RECORD_LOCATOR_TYPE_NAME_SUFFIX);
      if (isLocator) {
        recordTypeStr = typeName.substring(RECORD_LOCATOR_TYPE_NAME_PREFIX.length(),
            typeName.length() - RECORD_LOCATOR_TYPE_NAME_SUFFIX.length());
      } else {
        recordTypeStr = typeName.substring(RECORD_LOCATOR_TYPE_NAME_PREFIX.length());
      }

      TupleSchema schema;
      try {
        schema = new TupleSchema(recordTypeStr);
      } catch (com.ibm.avatar.aog.ParseException e) {
        throw AQLParserBase.makeException(e, null,
            "Error parsing record locator field type string '%s': %s", typeName, e.getMessage());
      }
      return new FieldType(schema, isLocator);
    }

    // If we get here, no match was found.
    throw new ParseException(String.format("Attempted to decode string '%s' as a field type, "
        + "but the string does not appear to be a valid field type", typeName));
  }

  /**
   * @return the type of the tables pointed to by this field, if this field is a table field; null
   *         otherwise
   */
  public AbstractTupleSchema getRecordSchema() {
    return reftype;
  }

  /**
   * @return true if this field is a locator, i.e. a reference to the tuples of a view or table
   */
  public boolean getIsLocator() {
    return (isLocator);
  }

  /**
   * @return true if this field represents a nested table or table locator
   */
  public boolean getIsTableType() {
    return (null != reftype);
  }

  /**
   * @return the type of the scalars in the list, if this field is a scalar list type; null
   *         otherwise
   */
  public FieldType getScalarListType() {
    return nestedscalartype;
  }

  /**
   * @return true if this field contains a list of scalar values
   */
  public boolean getIsScalarListType() {
    return (null != nestedscalartype);
  }

  /**
   * @return true if this is exactly the universal null type
   */
  public boolean getIsNullType() {
    return (NULL_TYPE.equals(this));
  }

  /**
   * @return true if this is exactly the SpanText type
   */
  public boolean getIsSpanText() {
    return SPANTEXT_TYPE.equals(this);
  }

  /**
   * @return true if this is exactly the Span type
   */
  public boolean getIsSpan() {
    return SPAN_TYPE.equals(this);
  }

  /**
   * @return true if this is exactly the Text type
   */
  public boolean getIsText() {
    return TEXT_TYPE.equals(this);
  }

  /**
   * @return true if this is any of the Span or Text types
   */
  public boolean getIsSpanOrText() {
    return SPAN_TYPE.equals(this) || TEXT_TYPE.equals(this) || SPANTEXT_TYPE.equals(this);
  }

  /**
   * @return true if this is exactly the Integer type
   */
  public boolean getIsIntegerType() {
    return this.equals(INT_TYPE);
  }

  /**
   * @return true if this is exactly the Float type
   */
  public boolean getIsFloatType() {
    return this.equals(FLOAT_TYPE);
  }

  /**
   * @return true if this is either Integer or Float type
   */
  public boolean getIsNumericType() {
    return (this.getIsIntegerType() || this.getIsFloatType());
  }

  /**
   * @deprecated As of v3.0.1, this method is deprecated. Use getIsText() instead.
   * @return true if this is exactly the String type (or the universal null type)
   */
  @Deprecated
  public boolean getIsStringType() {
    return this.equals(STRING_TYPE);
  }

  /**
   * @return true if this is exactly the Boolean type (or the universal null type)
   */
  public boolean getIsBooleanType() {
    return this.equals(BOOL_TYPE);
  }

  /**
   * @return true if this field is of a scalar (atomic) type, as opposed to a list or table locator
   */
  public boolean getIsScalarType() {
    return getIsNumericType() || getIsStringType() || getIsSpan() || getIsText()
        || getIsBooleanType();
  }

  /**
   * @return For {@link com.ibm.avatar.algebra.datamodel.Span} types, the type of the document that
   *         was the source for the span's target. For {@link com.ibm.avatar.algebra.datamodel.Text}
   *         types, the type of the document that was the source for the text. Returns null for
   *         other types or if the text isn't derived.
   */
  public FieldType getSourceDocType() {
    return source;
  }

  /**
   * @param type For {@link com.ibm.avatar.algebra.datamodel.Span} types, the type of the document
   *        that was the source for the span's target. For
   *        {@link com.ibm.avatar.algebra.datamodel.Text} types, the type of the document that was
   *        the source for the text.
   */
  public void setSourceDocType(FieldType type) {
    source = type;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof FieldType)) {
      return false;
    }

    FieldType o = (FieldType) obj;

    if (0 == compareTo(o)) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * Determines if the given actual type can be used as our type, possibly after a conversion.
   * <p>
   * <b>Note:</b> This method does NOT commute. Invert the arguments and you will get a slightly
   * different answer in certain cases.
   * 
   * @param actualType another FieldType object
   * @return true if a field of type otherType can be used in function calls and extraction
   *         operators that expect a field of the current object's type
   */
  public boolean accepts(FieldType actualType) {
    // the null type is always a subtype of other types
    if (actualType.getIsNullType()) {
      return true;
    } else if (getIsSpanOrText()) {
      // Span or Text types should accept each other
      return actualType.getIsSpanOrText();
    } else if (getIsScalarListType()) {
      // SPECIAL CASE: We don't currently check parameterized subtypes of lists, since module
      // metadata throws out the
      // parameter values.
      return actualType.getIsScalarListType();
      // END SPECIAL CASE
    } else if (getIsLocator()) {
      // SPECIAL CASE: For record locators, we don't require that names match, just types. Also, we
      // allow a loose match
      // on each type.
      if (false == actualType.getIsLocator()) {
        return false;
      }
      AbstractTupleSchema actualRef = actualType.getRecordSchema();
      if (reftype.size() != actualRef.size()) {
        return false;
      }

      for (int i = 0; i < reftype.size(); i++) {
        FieldType myNestedType = reftype.getFieldTypeByIx(i);
        FieldType actualNestedType = actualRef.getFieldTypeByIx(i);
        if (false == myNestedType.accepts(actualNestedType)) {
          return false;
        }
      }

      // If we get here, all columns match (loosely)
      return true;
      // END SPECIAL CASE
    } else {
      // Other types should be an exact match
      return equals(actualType);
    }
  }

  @Override
  public int hashCode() {
    // NOTE: Keep this method in sync with equals()!
    return (ptrHash(typename) + ptrHash(reftype));
  }

  @Override
  public int compareTo(FieldType o) {

    if (this == o) {
      return 0;
    }

    // Walk through the parameters one by one, watching out for nulls.
    if (false == comparePtrs(typename, o.typename)) {
      return typename.compareTo(o.typename);
    }

    // We currently don't compare the runtimeClass field

    if (false == comparePtrs(reftype, o.reftype)) {
      return reftype.compareTo(o.reftype);
    }
    if (false == comparePtrs(nestedscalartype, o.nestedscalartype)) {
      return nestedscalartype.compareTo(o.nestedscalartype);
    }
    if (false == comparePtrs(source, o.source)) {
      return source.compareTo(o.source);
    }

    // If we get here, everything we checked is the same.
    return 0;
  }

  /**
   * @return name of the type, as it would appear in an AQL statement
   */
  public String getTypeName() {
    return typename;
  }

  /**
   * @return the Java type that implements the specified field type at runtime
   */
  public Class<?> getRuntimeClass() {
    return runtimeClass;
  }

  /*
   * UTILITY METHODS GO BELOW THIS LINE
   */

  // hashcode for a possibly null pointer.
  private static int ptrHash(Object o) {
    if (null == o) {
      return 0;
    } else {
      return o.hashCode();
    }
  }

  /**
   * Utility method to compare the values of two pointers, either of which may possibly be null.
   * 
   * @param o1 first object to compare (may be null)
   * @param o2 second object to compare (may also be null)
   */
  public static boolean comparePtrs(Object o1, Object o2) {
    if (null == o1 && null == o2) {
      return true;
    } else if (null == o1 && null != o2) {
      return false;
    } else if (null != o1 && null == o2) {
      return false;
    } else {
      // Case 4: Both pointers are non-null -- ignore findbugs warning
      return o1.equals(o2);
    }
  }

  /**
   * Utility method to compare the values of two pointers to Comparables, either of which may
   * possibly be null.
   * 
   * @param o1 first object to compare; if not null, must be an instance of T, in addition to being
   *        Comparable<T>
   * @param o2 second object to compare
   */
  @SuppressWarnings("unchecked")
  public static <T> int compareComparablePtrs(Comparable<T> o1, Comparable<T> o2) {
    if (null == o1 && null == o2) {
      return 0;
    } else if (null == o1 && null != o2) {
      return -1;
    } else if (null != o1 && null == o2) {
      return 1;
    } else {

      assert (o1 != null);
      // Case 4: Both pointers are non-null.
      return o1.compareTo((T) o2);
    }
  }

  /**
   * Automatic type conversion. Currently only available conversion is between Span and Text.
   * 
   * @param val The input object to be converted
   * @return The converted object of this FieldType
   * @throws InterruptedException
   */
  public Object convert(Object val) {
    // System.out.printf ("FieldType.convert: wanted %s, actual %s\n", this, val.getClass
    // ().getSimpleName ());
    if (SPAN_TYPE.equals(this)) {
      if (val instanceof Text)
        return ((Text) val).toSpan();
      if (val instanceof String)
        return Text.convert(val).toSpan();
    }

    if (TEXT_TYPE.equals(this)) {
      if (val instanceof Span)
        return ((Span) val).toText();
      if (val instanceof String)
        return Text.convert(val);
    }
    return val;
  }

  /**
   * Method to verify the given val is of correct type. This is intended for test code that
   * validates the type system itself.
   * 
   * @param val The input object the type of which is to be checked
   * @return true or false, indicating if val is of the correct type
   */
  public boolean checkType(Object val) {
    // test val for null first -- null is untyped, so it's always the correct type
    if (val == null) {
      return true;
    }

    if (getIsNullType()) {
      return true;
    }

    if (getIsText()) {
      return val instanceof Text || val instanceof String;
    } else if (getIsSpan()) {
      return val instanceof Span;
    } else if (getIsSpanText()) {
      return val instanceof SpanText;
    } else if (getIsBooleanType()) {
      return val instanceof Boolean;
    } else if (this.getIsIntegerType()) {
      return val instanceof Integer;
    } else if (this.getIsFloatType()) {
      return val instanceof Float;
    } else if (getIsBooleanType()) {
      return val instanceof Boolean;
    } else if (getIsScalarListType()) {
      return val instanceof ScalarList<?>;
    } else if (getIsLocator()) {
      if (val instanceof TableLocator) {
        return false;
      }
      TableLocator loc = (TableLocator) val;

      AbstractTupleSchema actualRef = loc.getOutputSchema();
      if (reftype.size() != actualRef.size()) {
        return false;
      }

      for (int i = 0; i < reftype.size(); i++) {
        FieldType myNestedType = reftype.getFieldTypeByIx(i);
        FieldType actualNestedType = actualRef.getFieldTypeByIx(i);
        if (false == myNestedType.accepts(actualNestedType)) {
          return false;
        }
      }
      return true;
    } else {
      // Should not reach here.
      return false;
    }
  }

  /**
   * Check whether two types are comparable for equality.
   * <p>
   * This is a precondition to guard against internal inconsistency.
   * <p>
   * 
   * @param other The other object to be compared against.
   * @return boolean indicating whether the objects are comparable.
   */
  public boolean comparableTo(FieldType other) {
    // null type is always comparable to other types
    if (this.getIsNullType())
      return true;
    if (other.getIsNullType())
      return true;

    if (getIsSpanOrText()) {
      // Span and Text types can be compared.
      return other.getIsSpanOrText();
    } else {
      // For all other types we require strict match
      return this.equals(other);
    }
  }

}
