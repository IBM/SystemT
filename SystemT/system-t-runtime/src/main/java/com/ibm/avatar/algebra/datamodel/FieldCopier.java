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
 * Reusable accessor class for copying fields from one tuple to another.
 * 
 */
public class FieldCopier {

  /** Indexes for the source tuples. */
  protected int[] physicalSrcIx;

  /**
   * Corresponding indexes for the destination tuples.
   */
  protected int[] physicalDstIx;

  /**
   * If any of the target columns are of type Text, and if the target schema is a named (i.e. view)
   * schema implemented as a virtual projection, the qualified names of the source columns. Indexes
   * to this array are the same as {@link #physicalSrcIx}
   */
  protected Pair<?, ?>[] srcTextColNames;

  /**
   * If any of the target columns are of type Text, and if the target schema is a named (i.e. view)
   * schema, the qualified names of the target columns.Indexes to this array are the same as
   * {@link #physicalDstIx}
   */
  protected Pair<?, ?>[] destTextColNames;

  /**
   * Schema for the source tuple.
   */
  protected AbstractTupleSchema srcSchema;

  /**
   * Schema for the destination tuple.
   */
  protected AbstractTupleSchema destSchema;

  /**
   * Constructor for the use of the {@link com.ibm.avatar.algebra.datamodel.AbstractTupleSchema}
   * class only.
   * 
   * @param srcIx indices of tuple columns, in terms of the source schema
   * @param dstIx indices of destination tuple columns, in terms of the destination schema
   * @param srcSchema schema for the source tuple.
   * @param destSchema schema for the destination tuple.
   */
  protected FieldCopier(int[] srcIx, int[] dstIx, AbstractTupleSchema srcSchema,
      AbstractTupleSchema destSchema) {

    if (srcIx.length != dstIx.length) {
      throw new RuntimeException("Lengths don't match");
    }

    this.srcSchema = srcSchema;
    this.destSchema = destSchema;

    // Translate the column indices into physical indices.
    physicalSrcIx = new int[srcIx.length];
    physicalDstIx = new int[dstIx.length];
    for (int i = 0; i < srcIx.length; i++) {
      physicalSrcIx[i] = srcSchema.getPhysicalIndex(srcIx[i]);
      physicalDstIx[i] = destSchema.getPhysicalIndex(dstIx[i]);
    }

    // If the target is a named (view) schema, set up column name information to be embedded in any
    // Text objects we add
    // to tuples of the schema.
    destTextColNames = new Pair[dstIx.length];
    for (int i = 0; i < dstIx.length; i++) {
      FieldType targetFieldType = destSchema.getFieldTypeByIx(dstIx[i]);
      if (destSchema.getHasName() && targetFieldType.getIsText()
          && (targetFieldType.getIsNullType() == false)) {
        destTextColNames[i] =
            new Pair<String, String>(destSchema.getName(), destSchema.getFieldNameByIx(dstIx[i]));
      } else {
        destTextColNames[i] = null;
      }
    }

    // We also do the same operation on source objects if the source schema is a view schema that is
    // a virtual
    // projection over an anonymous schema.
    srcTextColNames = new Pair[srcIx.length];
    for (int i = 0; i < srcIx.length; i++) {
      if (srcSchema instanceof DerivedTupleSchema && srcSchema.getHasName()
          && srcSchema.getFieldTypeByIx(srcIx[i]).getIsText()) {
        srcTextColNames[i] =
            new Pair<String, String>(srcSchema.getName(), srcSchema.getFieldNameByIx(srcIx[i]));
      } else {
        srcTextColNames[i] = null;
      }
    }
  }

  /**
   * Copy fields from the source tuple to the destination tuple, doing automatic type conversion if
   * necessary.
   * 
   * @param src source tuple
   * @param dest destination tuple
   */
  @SuppressWarnings("all")
  public void copyVals(Tuple src, Tuple dest) {
    for (int i = 0; i < physicalSrcIx.length; i++) {
      Object srcObj = src.fields[physicalSrcIx[i]];

      FieldType destType = this.destSchema.getFieldTypeByIx(physicalDstIx[i]);
      dest.fields[physicalDstIx[i]] = destType.convert(srcObj);

      // Tag Text objects with field name information as appropriate. Note that only the first tag
      // applied "sticks".
      if (null != srcTextColNames[i] && null != srcObj) {
        // Source is a named schema that implements a virtual projection over an anonymous schema,
        // and this column is of
        // type Text. Tag the object with the appropriate field name.
        Text textObj = (Text) srcObj;
        textObj.setViewAndColumnName((Pair<String, String>) srcTextColNames[i]);
      }

      if (null != destTextColNames[i] && null != srcObj) {
        // Target is a named schema, and this column is of type Text. Tag the object with the
        // appropriate field name.
        Text textObj;

        // First do a sanity check to make sure that type inference correctly determined that this
        // column is indeed of
        // type Text.
        if (false == (srcObj instanceof Text)) {
          throw new FatalInternalError("\n\tColumn %d of target schema \n\t\t%s "
              + "\n\tis of type Text, but source object \n\t\t%s \n\tfrom column %d of source schema \n\t\t%s "
              + "\n\tis not a Text object.", destSchema.getLogicalIndex(physicalDstIx[i]) + 1,
              destSchema, srcObj, srcSchema.getLogicalIndex(physicalSrcIx[i]) + 1, srcSchema);
        }
        textObj = (Text) srcObj;
        // TODO: can the above test and cast be replaced with a convert as below?
        // textObj = Text.convert (srcObj);
        textObj.setViewAndColumnName((Pair<String, String>) destTextColNames[i]);

        // Tag Text objects with field name information as appropriate. Note that only the first tag
        // applied "sticks".
        if (null != srcTextColNames[i] && null != srcObj) {
          // Source is a named schema that implements a virtual projection over an anonymous schema,
          // and this column is
          // of
          // type Text. Tag the object with the appropriate field name.
          textObj = (Text) srcObj;
          textObj.setViewAndColumnName((Pair<String, String>) srcTextColNames[i]);
        }

        if (null != destTextColNames[i] && null != srcObj) {
          // Target is a named schema, and this column is of type Text. Tag the object with the
          // appropriate field name.
          // First do a sanity check to make sure that type inference correctly determined that this
          // column is indeed of
          // type Text.
          if (false == (srcObj instanceof Text)) {
            throw new FatalInternalError(
                "Column %d of target schema %s "
                    + "is of type Text, but source object %s from column %d of source schema %s "
                    + "is not a Text object",
                destSchema.getLogicalIndex(physicalDstIx[i]) + 1, destSchema, srcObj,
                srcSchema.getLogicalIndex(physicalSrcIx[i]) + 1, srcSchema);
          }
          textObj = (Text) srcObj;
          textObj.setViewAndColumnName((Pair<String, String>) destTextColNames[i]);
        }
      }
    }
  }

  /**
   * Returns true if this copier will just pass all fields through.
   * 
   * @return true if this copier will just pass all fields through
   */
  public boolean isNoOp() {

    // First, do some quick size checks.
    if (srcSchema.size() != physicalSrcIx.length) {
      return false;
    }
    if (srcSchema.size() != destSchema.size()) {
      // Destination schema has one or more extra fields.
      return false;
    }

    // Next check for one-to-one mapping.
    for (int i = 0; i < physicalSrcIx.length; i++) {
      if (physicalDstIx[i] != physicalSrcIx[i]) {
        return false;
      }
    }

    // Check whether any column name information will be added.
    for (int i = 0; i < srcTextColNames.length; i++) {
      if (null != srcTextColNames[i]) {
        return false;
      }
    }
    for (int i = 0; i < destTextColNames.length; i++) {
      if (null != destTextColNames[i]) {
        return false;
      }
    }

    // Make sure that every index is covered.
    boolean[] mask = new boolean[physicalSrcIx.length];
    for (int i = 0; i < physicalSrcIx.length; i++) {
      mask[physicalSrcIx[i]] = true;
    }

    for (int i = 0; i < mask.length; i++) {
      if (false == mask[i]) {
        return false;
      }
    }

    return true;
  }

}
