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

import com.ibm.avatar.algebra.function.scalar.GetString;

/**
 * The ScalarList provides a container for an ordered bag of scalar objects.
 *
 */
public class ScalarList<st> extends ArrayList<st> implements Comparable<ScalarList<st>> {

  /**
   * The type of the scalar elements of the list
   */
  private FieldType scalarType;

  /**
   * Dummy version ID to make serialization compiler warning go away.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Default constructor with an unspecified type for the scalar objects of the list. Consumers of
   * this constructor are responsible to explicitly set the type of the scalar objects of this list
   * by explicitly invoking {@link #setScalarType(FieldType)}.
   *
   * @deprecated Use {@link #makeScalarListFromType(FieldType)} instead, to instantiate ScalarList
   *             objects with an explicit type for the scalar elements of the list.
   * @since 1.3.0.1
   */
  @Deprecated
  public ScalarList() {

  }

  /**
   * Constructs an instance of ScalarList with scalar elements of the specified type. Note: Keep
   * this constructor private, as we want to regulate the instantiation of this class through the
   * factory method {@link #makeScalarListFromType(FieldType)}
   * 
   * @param type {@link FieldType} - type of the scalar list
   */
  private ScalarList(FieldType type) {
    setScalarType(type);
  }

  /**
   * Factory method to create a ScalarList object with the specified scalar field type. Valid scalar
   * types are: {@link com.ibm.avatar.algebra.datamodel.FieldType#INT_TYPE},
   * {@link com.ibm.avatar.algebra.datamodel.FieldType#FLOAT_TYPE},
   * {@link com.ibm.avatar.algebra.datamodel.FieldType#STRING_TYPE}, Text and Span. Any other type
   * will result in a RuntimeException. This is the recommended method of instantiating a ScalarList
   * object.
   *
   * @param type Type of the scalar objects of this list
   * @return a list of scalar objects with the input type
   */
  @SuppressWarnings({"all"})
  public static ScalarList makeScalarListFromType(FieldType type) {

    if (type.getIsIntegerType())
      return new ScalarList<Integer>(type);
    else if (type.getIsFloatType())
      return new ScalarList<Float>(type);
    else if (type.getIsText())
      return new ScalarList<Text>(type);
    else if (type.getIsSpan())
      return new ScalarList<Span>(type);
    else if (type.getIsNullType())
      return new ScalarList<Span>(type);
    else {
      throw new RuntimeException(String.format(
          "Cannot make list of type %s. Valid types are: Integer, Float, String, Span and Text.",
          type));
    }
  }

  /**
   * Sets the scalar type for the scalar objects of this list. Valid scalar types are:
   * {@link com.ibm.avatar.algebra.datamodel.FieldType#INT_TYPE},
   * {@link com.ibm.avatar.algebra.datamodel.FieldType#FLOAT_TYPE},
   * {@link com.ibm.avatar.algebra.datamodel.FieldType#STRING_TYPE}, Text and Span. Any other type
   * will result in a RuntimeException.
   *
   * @param type Type of the elements of this ScalarList
   */
  public void setScalarType(FieldType type) {
    if (type.getIsScalarType() || type.getIsNullType())
      scalarType = type;
    else
      throw new RuntimeException(String.format(
          "Invalid type %s. Valid types are: Integer, Float, String, Span and Text.", type));
  }

  /**
   * Returns the scalar type for the scalar objects of this list.
   *
   * @return the scalar type for the scalar objects of this list.
   */
  public FieldType getScalarType() {
    return scalarType;
  }

  /**
   * Compares this object with the specified object for order.
   * 
   * @see Comparable#compareTo(Object)
   */
  @Override
  public int compareTo(ScalarList<st> o) {

    if (null == o) {
      // Null always sorts lower
      return 1;
    }

    int result;
    int thisSize = this.size();
    int otherSize = o.size();

    int minSize = thisSize < otherSize ? thisSize : otherSize;

    ScalarComparator comp;

    try {
      comp = ScalarComparator.createComparator(scalarType);
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage());
    }

    for (int idx = 0; idx < minSize; idx++) {

      result = comp.compare(this.get(idx), o.get(idx));

      // The two lists differ at position idx
      if (result != 0)
        return result;
    }

    // One of the lists is a prefix of the other

    if (thisSize > otherSize)
      // the other is a prefix, sorts lower than this
      return 1;
    else if (thisSize < otherSize)
      // this is the prefix, sorts lower than the other
      return -1;
    else
      // equal lists
      return 0;
  }

  /**
   * Returns a non-abbreviated string representation of the list, containing every single element in
   * full.
   *
   * @return a non-abbreviated string representation of the list, containing every single element in
   *         full.
   */
  public String toLongString() {
    StringBuilder sb = new StringBuilder();
    final String SEPARATOR = ";";

    int size = size();
    for (int i = 0; i < size; i++) {

      // Leverage the special case code in the GetString built-in scalar
      // function
      sb.append(GetString.objToStr(get(i)));

      if (i < size - 1) {
        // Put in the separator after every entry but the last one.
        sb.append(SEPARATOR);
      }
    }

    return sb.toString();
  }
}
