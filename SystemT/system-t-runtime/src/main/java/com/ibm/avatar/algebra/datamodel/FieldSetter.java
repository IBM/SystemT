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
 * Reusable accessor object for setting a field in a tuple. Also manages reference-counting on the
 * tuple.
 *
 */
public class FieldSetter<ft> {

  /** Tuple schema index of the column to set. */
  protected int colix = -1;

  /** Detailed type information about the field accessed. */
  // protected FieldType type;
  /**
   * Constructor for use by TupleSchema only.
   *
   * @param schema the schema of tuples that this object will be used to set the fields of
   * @param colix index of the column to set; this is an index into the SCHEMA, and not necessarily
   *        a physical index of the actual run-time tuple.
   */
  protected FieldSetter(AbstractTupleSchema schema, int colix) {
    this.colix = schema.getPhysicalIndex(colix);
  }

  /**
   * Sets the value of the appropriate field of the tuple.
   */
  public void setVal(Tuple tup, ft obj) {
    Text.verifyNotString(obj, "Unknown producer");
    tup.fields[colix] = (Object) obj;
  }

  // @Deprecated
  // public FieldType getType() {
  // return this.type;
  // }
}
