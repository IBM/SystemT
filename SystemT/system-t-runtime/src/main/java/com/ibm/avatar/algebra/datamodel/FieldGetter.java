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
 * Reusable accessor object for retrieving a field from a tuple. Also manages reference-counting on
 * the tuple.
 *
 */
public class FieldGetter<ft> {

  /** The tuple that this getter is currently pointing to. */
  // protected Tuple curtup = null;

  /** Tuple schema index of the column to fetch. */
  protected int colix = -1;

  /**
   * If this accessor is fetching a Text column from a "virtual projection" schema for a view, the
   * view and column name to attach to the Text object. null otherwise.
   */
  protected Pair<String, String> textViewAndColName = null;

  /**
   * Constructor to be called from TupleSchema
   *
   * @param schema the schema of tuples that this object will be used to access; may be a derived
   *        schema
   * @param colix column that this object will fetch from tuples; this is an index into the schema,
   *        and not necessarily a physical index of the actual run-time tuple
   */
  protected FieldGetter(AbstractTupleSchema schema, int colix) {
    if (null == schema) {
      // Assume the caller knows what he's doing if he passes you a null
      // pointer for the schema
      this.colix = colix;
    } else {
      // System.err.printf("Schema %s maps column %d to %d\n",
      // schema, colix, schema.getPhysicalIndex(colix));
      this.colix = schema.getPhysicalIndex(colix);
    }

    if (schema instanceof DerivedTupleSchema && schema.getHasName()
        && schema.getFieldTypeByIx(colix).getIsText()) {
      // Text field in a view whose top-level schema is a virtual projection.
      // We'll set the view/column name information of each Text object we retrieve from the
      // schema's tuples.
      textViewAndColName =
          new Pair<String, String>(schema.getName(), schema.getFieldNameByIx(colix));
    }
  }

  /**
   * Accesses the appropriate field and clears out any current tuple.
   *
   * @param tup tuple to get the value from
   * @return the value of the indicated field of the tuple
   */
  @SuppressWarnings("unchecked")
  public ft getVal(Tuple tup) {
    // reset();
    // if (pin) {
    // tup.pin();
    // curtup = tup;
    // }

    Object ret = tup.fields[colix];

    // Put column name information in place if necessary
    if (null != textViewAndColName && null != ret) {
      Text retText = (Text) ret;
      retText.setViewAndColumnName(textViewAndColName);
    }

    return (ft) ret;
  }

  /**
   * Returns the index of the field that this object fetches.
   *
   * @return index of the field that this object fetches
   */
  public int getColIx() {
    return colix;
  }

}
