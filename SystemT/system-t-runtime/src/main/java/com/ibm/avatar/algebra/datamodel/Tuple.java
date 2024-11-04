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

import com.ibm.avatar.api.exceptions.FatalInternalError;

/**
 * Reference-counted object that holds the fields of a tuple. Access to the the tuple's fields is
 * only via the {@link com.ibm.avatar.algebra.datamodel.FieldGetter} and
 * {@link com.ibm.avatar.algebra.datamodel.FieldSetter} classes. Likewise, tuple creation only
 * happens through {@link com.ibm.avatar.algebra.datamodel.TupleSchema}.
 *
 */
public class Tuple implements Comparable<Tuple> {

  /**
   * The fields themselves; all fields are stored boxed. Number of fields used is always the length
   * of the array.
   */
  protected Object[] fields;

  /**
   * Optional unique identifier for associating a tuple with an external storage layer.
   */
  private ObjectID oid = null;

  /**
   * Setter for associating optional {@link com.ibm.avatar.algebra.datamodel.ObjectID} with the
   * tuple.
   *
   * @param oid object id instance to be associated with the tuple
   */
  public void setOid(ObjectID oid) {
    this.oid = oid;
  }

  /**
   * Returns the associated unique object id instance of this tuple.
   *
   * @return the associated unique object id instance
   */
  public ObjectID getOid() {
    return oid;
  }

  /**
   * Constructor to create a Tuple with the given number of fields.
   *
   * @param nfields number of fields to be created in tuple
   */
  protected Tuple(int nfields) {
    fields = new Object[nfields];
  }

  /**
   * Factory method for creating dummy tuple objects. The output of this method should not be used
   * to store any actual data.
   *
   * @return a tuple with 0 fields
   */
  public static Tuple createDummyTuple() {
    return new Tuple(0);
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();

    sb.append("[");

    for (int i = 0; i < fields.length; i++) {
      if (null == fields[i]) {
        sb.append("NULL");
      } else {
        sb.append(fields[i].toString());
      }
      if (i < fields.length - 1) {
        sb.append(", ");
      }
    }

    sb.append(String.format("(%d fields)", fields.length));
    sb.append("]");

    return sb.toString();
  }

  /**
   * Returns string in Excel comma-separated value format.
   *
   * @return string in Excel comma-separated value format
   */
  public String toCSVString() {
    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < fields.length; i++) {
      // Enclose all fields in quotes for now.
      sb.append('"');
      if (null == fields[i]) {
        sb.append("NULL");
      } else {
        sb.append(fields[i].toString());
      }
      sb.append('"');
      if (i < fields.length - 1) {
        sb.append(",");
      }
    }
    return sb.toString();
  }

  /** {@inheritDoc} */
  @Override
  @SuppressWarnings("all")
  public int compareTo(Tuple o) {

    boolean debug = false;

    // Start by ordering by number of fields.
    int val = getWidth() - o.getWidth();

    if (val != 0) {
      return val;
    }

    // Compare field by field until we find a pair of fields that are
    // different.
    for (int i = 0; 0 == val && i < fields.length; i++) {
      Comparable myField = (Comparable) fields[i];

      if (debug) {
        System.err.printf("Comparing %s and %s\n", myField, o.fields[i]);
      }

      val = compareWithNull(myField, o.fields[i]);
      // myField.compareTo(o.fields[i]);

      if (debug && 0 != val) {
        System.err.printf("--> Values are NOT the same.\n");
      }
    }
    return val;

    // For now, every tuple is distinct, at least for Java comparison.
    // return hashCode() - o.hashCode();
  }

  // Generic comparison of comaparable objects with nulls sorting lower.
  private <T> int compareWithNull(Comparable<T> o1, T o2) {
    if (null == o1) {
      if (null == o2) {
        return 0;
      } else {
        // Null sorts lower.
        return -1;
      }
    } else {
      // o1 not null
      if (null == o2) {
        return 1;
      } else {
        return o1.compareTo(o2);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object o) {
    if (o instanceof Tuple) {
      // Defer to the more general compareTo() function.
      return (0 == compareTo((Tuple) o));
    } else {
      return false;
    }
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    throw new FatalInternalError("Hashcode not implemented for class %s.",
        this.getClass().getSimpleName());
  }

  /**
   * Returns number of fields in the tuple.
   *
   * @return number of fields in the tuple.
   */
  public int getWidth() {
    return fields.length;
  }

  /**
   * Returns number of fields in the tuple.
   *
   * @return number of fields in the tuple
   */
  public int size() {
    return getWidth();
  }

  /**
   * Returns field object at the given index. Compatibility API for user code that still uses the
   * old interface.
   *
   * @deprecated Use accessors {@link com.ibm.avatar.algebra.datamodel.FieldGetter}, or
   *             {@link com.ibm.avatar.algebra.datamodel.AbstractTupleSchema#getCol(Tuple, int)}.
   * @param ix an index into the tuple
   * @return the basic object at the indicated index
   */
  @Deprecated
  public Object getElement(int ix) {
    return fields[ix];
  }

  /**
   * Get a new tuple, creating one if necessary.
   *
   * @param width number of fields in the tuple
   */
  protected static Tuple getTup(int width) {
    return new Tuple(width);
  }

}
