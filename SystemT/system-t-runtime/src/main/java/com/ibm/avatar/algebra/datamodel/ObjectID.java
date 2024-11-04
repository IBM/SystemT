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

/**
 * A unique object identifier for a tuple; used to encapsulate the identifiers of various external
 * storage layers. The basic OID type is a (type name, 64-bit integer) pair. Create subclasses as
 * needed to encapsulate other object ID types. There is also an optional flag to indicate whether
 * this ID was assigned by the storage layer or is a temporary placeholder created by the Text
 * Analytics system.
 */
public class ObjectID {

  private String typeName = null;
  private long index = -1;

  /**
   * Optional flag to indicate whether this ID was assigned by the storage layer or is a temporary
   * placeholder created by the Text Analytics system.
   */
  private boolean isPlaceholder;

  /**
   * <p>
   * Constructor for ObjectID.
   * </p>
   *
   * @param typeName name of the external type that corresponds to the tuple being identified
   * @param index an integer that is unique among objects of the type
   * @param isPlaceholder true if this ID is a temporary ID assigned
   */
  public ObjectID(String typeName, long index, boolean isPlaceholder) {
    this.typeName = typeName;
    this.index = index;
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return String.format("%s#%d", typeName, index);
  }

  /**
   * Returns the identifier for this object that is unique within its type.
   *
   * @return identifier for this object that is unique within its type
   */
  public long getIDInType() {
    return index;
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return (int) (typeName.hashCode() + index + (isPlaceholder ? 1L : 0L));
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object o) {
    if (o instanceof ObjectID) {
      ObjectID other = (ObjectID) o;

      if (typeName == null && other.typeName == null && index == other.index
          && isPlaceholder == other.isPlaceholder) {
        return true;
      } else if (typeName != null && typeName.equals(other.typeName) && index == other.index
          && isPlaceholder == other.isPlaceholder) {
        return true;
      }
    }

    return false;
  }

  /**
   * <p>
   * getStringValue.
   * </p>
   */
  public String getStringValue() {
    return this.toString();
  }

  /**
   * <p>
   * Getter for the field <code>typeName</code>.
   * </p>
   */
  public String getTypeName() {
    return typeName;
  }

  /**
   * Returns true if this OID is a temporary "placeholder" ID created by the Text Analytics system
   * for use until the storage layer can create a permanent ID. Temporary IDs are guaranteed to be
   * unique inside main memory.
   *
   * @return true if this OID is a temporary "placeholder" ID
   */
  public boolean isTemp() {
    return isPlaceholder;
  }
}
