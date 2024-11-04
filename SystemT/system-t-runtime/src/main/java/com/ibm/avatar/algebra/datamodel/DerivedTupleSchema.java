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

import com.ibm.avatar.algebra.util.data.StringPairList;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.logging.Log;

/**
 * A tuple schema that performs a "virtual projection" over the actual tuple. Some mixture of
 * removing, renaming, and/or duplicating the columns of the original tuple, without making a copy.
 *
 */
public class DerivedTupleSchema extends AbstractTupleSchema {

  /**
   * Schema over which to perform a "virtual projection"; may be either a "real" schema or a derived
   * schema
   */
  private AbstractTupleSchema baseSchema;

  /**
   * If any column in the derived schema has been given a more general type than in the base schema,
   * the more general type goes into this array; otherwise each entry is null.
   */
  private FieldType[] typeOverrides = null;

  /**
   * Mapping from column names in base schema to names of the new schema
   */
  private StringPairList nameMapping;

  /**
   * Constructor to create a derived tuple schema on given base schema based on the given names
   * mapping.
   *
   * @param base the schema over which to perform a "virtual projection"; may be either a "real"
   *        schema or a derived schema
   * @param nameMapping mapping from column names in base schema to names of the new schema
   */
  public DerivedTupleSchema(AbstractTupleSchema base, StringPairList nameMapping) {
    baseSchema = base;
    this.nameMapping = nameMapping;
    typeOverrides = new FieldType[size()];
    Arrays.fill(typeOverrides, null);
  }

  /**
   * Constructor for creating a derived schema that just passes through the visible columns of an
   * input schema.
   *
   * @param base schema to pass through
   */
  public DerivedTupleSchema(AbstractTupleSchema base) {
    baseSchema = base;
    nameMapping = new StringPairList();
    for (int i = 0; i < base.size(); i++) {
      String colName = base.getFieldNameByIx(i);
      nameMapping.add(colName, colName);
    }
    typeOverrides = new FieldType[size()];
    Arrays.fill(typeOverrides, null);
  }

  /**
   * Constructor for creating a derived schema that just passes through the visible columns of a set
   * of identical input schemas and uses an appropriately general type for each column.
   *
   * @param schemas schemas (must have identical column names and compatible column types) that
   *        serve as inputs for passing through.
   */
  public DerivedTupleSchema(ArrayList<AbstractTupleSchema> schemas) {
    // Start out with a schema derived from the first schema in the list.
    this(schemas.get(0));

    // Sanity-check the input field names (and schema sizes)
    String[] fieldNames = schemas.get(0).getFieldNames();
    for (AbstractTupleSchema schema : schemas) {
      if (false == Arrays.equals(fieldNames, schema.getFieldNames())) {
        throw new FatalInternalError(
            "Schemas with mismatched field names (%s and %s) passed to DerivedTupleSchema constructor",
            schemas.get(0), schema);
      }
    }

    // Put type overrides in place as needed.
    // Generate a temporary schema so that we can keep all the special-case logic inside the
    // generalize() method.
    TupleSchema generalizedSchema = AbstractTupleSchema.generalize(schemas);
    for (int i = 0; i < size(); i++) {
      FieldType curType = getFieldTypeByIx(i);
      FieldType generalizedType = generalizedSchema.getFieldTypeByIx(i);
      if (false == (generalizedType.equals(curType))) {
        typeOverrides[i] = generalizedType;
      }
    }

  }

  /**
   * {@inheritDoc}
   *
   * Returns the name of the field at given index. Used mostly by classes in the Datamodel package.
   */
  @Override
  public String getFieldNameByIx(int i) {
    return nameMapping.get(i).second;
  }

  /**
   * {@inheritDoc}
   *
   * Returns the data type of the field at the given index. Used mostly by classes in the Datamodel
   * package.
   */
  @Override
  public FieldType getFieldTypeByIx(int i) {
    // First check for an override.
    if (null != typeOverrides[i]) {
      return typeOverrides[i];
    }

    String baseFieldName = nameMapping.get(i).first;
    return baseSchema.getFieldTypeByName(baseFieldName);
  }

  /**
   * {@inheritDoc}
   *
   * Returns the index of the physical field in the tuple.
   */
  @Override
  public int getPhysicalIndex(int i) {
    final boolean debug = false;

    String baseFieldName = nameMapping.get(i).first;
    int baseIx = baseSchema.nameToIx(baseFieldName);
    int ret = baseSchema.getPhysicalIndex(baseIx);

    if (debug) {
      Log.debug("getPhysicalIndex(%d): Base field '%s' is column %d", i, baseFieldName, baseIx);
    }
    return ret;
  }

  /**
   * {@inheritDoc}
   *
   * Returns the number of fields in the schema.
   */
  @Override
  public int size() {
    return nameMapping.size();
  }

  /**
   * {@inheritDoc}
   *
   * Must be kept in sync with the implementation of {@link #hashCode()}.
   */
  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }

    if (!(obj instanceof DerivedTupleSchema)) {
      return false;
    }

    if (null == obj) {
      throw new RuntimeException("Comparison against null schema");
    }

    AbstractTupleSchema o = (AbstractTupleSchema) obj;

    return (0 == compareTo(o));
  }

  /**
   * {@inheritDoc}
   *
   * Comparison function for tuple schemas. Must sort base schemas lower than derived ones.
   */
  @Override
  public int compareTo(AbstractTupleSchema o) {
    if (this == o) {
      return 0;
    }

    // Check types
    if (o instanceof TupleSchema) {
      // Base schema sorts lower
      return 1;
    }

    // TODO: Should we compare how the schema was derived?

    // Next compare the fields of the schemas themselves.
    return super.compareNamesAndTypes(o);
  }

  /**
   * {@inheritDoc}
   *
   * Creates a new tuple that adheres to this schema.
   */
  @Override
  public Tuple createTup() {
    return baseSchema.createTup();
  }

  /**
   * {@inheritDoc}
   *
   * A version of {@link #compareTo(AbstractTupleSchema)} that will not recurse on a particular
   * type; used to avoid infinite recursion when comparing schemas that involve {@link Text} types.
   * Since derived tuple schemas cannot contain fields of type {@link Text}, this method will always
   * throw a RuntimeException.
   */
  @Override
  public int compareWithoutRecursion(AbstractTupleSchema o) {
    // This method should only be used in comparing text tuple types, which
    // should never be derived
    throw new RuntimeException("This method should never be called on a DerivedTupleSchema");
  }

}
