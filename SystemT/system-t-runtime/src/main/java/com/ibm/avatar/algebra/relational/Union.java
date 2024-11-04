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
package com.ibm.avatar.algebra.relational;

import java.util.ArrayList;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.base.MultiInputOperator;
import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.DerivedTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldCopier;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;

/**
 * Merge the results of two or more operators into a single stream of tuples.
 * 
 */
public class Union extends MultiInputOperator {

  /**
   * Set to true during schema initialization if fields of input tuples need to be copied, as
   * opposed to just passing through entire tuples.
   */
  boolean copyFields;

  FieldCopier[] copiers = null;

  /**
   * Default constructor
   * 
   * @param children inputs of the operator
   */
  public Union(Operator children[]) {
    super(children);

  }

  @Override
  protected void reallyEvaluate(MemoizationTable mt, TupleList[] childResults) throws Exception {

    if (copyFields) {
      // Need to copy the fields of the input tuples
      for (int i = 0; i < inputs.length; i++) {
        TLIter itr = childResults[i].iterator();
        while (itr.hasNext()) {
          Tuple childTup = itr.next();

          Tuple resultTup = createOutputTup();
          copiers[i].copyVals(childTup, resultTup);

          addResultTup(resultTup, mt);
        }
      }
    } else {
      // No field copy is necessary; just pass through the tuples.
      for (int i = 0; i < inputs.length; i++) {
        addResultTups(childResults[i], mt);
      }
    }
  }

  @Override
  protected AbstractTupleSchema createOutputSchema() {

    copyFields = false;

    // Verify that the children all have union-compatible schemas.
    AbstractTupleSchema firstInputSchema = getInputOp(0).getOutputSchema();
    for (int i = 1; i < inputs.length; i++) {
      AbstractTupleSchema otherSchema = getInputOp(i).getOutputSchema();

      // Note:Text, Span and SpanText are unionCompatible
      if (!firstInputSchema.unionCompatible(otherSchema)) {
        throw new IllegalArgumentException(
            String.format("Incompatible schemas (%s and %s) passed to Union operator",
                firstInputSchema, otherSchema));
      }

      // Note: Text and Span not sameTupleFormat
      if (false == firstInputSchema.sameTupleFormat(otherSchema)) {
        // Tuples have different low-level formats; need to copy
        copyFields = true;
      }
    }

    ArrayList<AbstractTupleSchema> schemas = new ArrayList<AbstractTupleSchema>();
    for (int i = 0; i < getNumInputs(); i++) {
      schemas.add(getInputOp(i).getOutputSchema());
    }

    if (copyFields) {

      // We'll be making copies of input tuples, so create a base output schema.
      TupleSchema ret = new TupleSchema(AbstractTupleSchema.generalize(schemas), true);
      for (int i = 0; i < getNumInputs(); i++) {
        schemas.add(getInputOp(i).getOutputSchema());
      }

      for (int i = 0; i < firstInputSchema.getFieldTypes().length; i++) {

        FieldType firstInputFieldType = firstInputSchema.getFieldTypeByIx(i);
        FieldType outputFieldType = firstInputFieldType;

        // Make sure the best Text/Span type is used
        // Use precedence: Span, SpanText, Text, Null.
        if (firstInputFieldType.getIsSpanOrText() || firstInputFieldType.getIsNullType()) {

          for (int j = 0; j < this.getNumInputs(); j++) {
            AbstractTupleSchema otherSchema = getInputOp(j).getOutputSchema();
            FieldType otherInputFieldType = otherSchema.getFieldTypeByIx(i);

            // If the other type is null, it will never be used as null is lowest priority -- skip
            // it
            if (otherInputFieldType.getIsNullType())
              continue;

            // If the other type is span, use that and we are done
            if (otherInputFieldType.getIsSpan() && !outputFieldType.getIsSpan()) {
              outputFieldType = otherInputFieldType;
              break;
            }
            // If the other type is SpanText, upcast to that, but continue looking
            else if (otherInputFieldType.getIsSpanText() && outputFieldType.getIsText()) {
              outputFieldType = otherInputFieldType;
            }
            // If current type is null, upcast to the non-null output type, but continue looking
            else if (outputFieldType.getIsNullType()) {
              outputFieldType = otherInputFieldType;
            }

          } // j over inputs
          // System.out.printf ("\tUnion: final field type for field %d: %s\n", i, outputFieldType);
          ret.setFieldTypeByIx(i, outputFieldType);
        }

      } // i over fields

      // System.err.printf("Union in %s maps:\n" +
      // " %s\n" +
      // "to\n" +
      // " %s\n", getViewName (),
      // StringUtils.join (schemas, "\n"), ret);

      // Set up the objects that will copy fields from inputs to outputs.
      copiers = new FieldCopier[inputs.length];

      for (int i = 0; i < inputs.length; i++) {
        copiers[i] = ret.fieldCopier(getInputOp(i).getOutputSchema());
      } // i over inputs

      return ret;

    } // copy fields
    else {
      // Not copying; Make our output schema a derived one from the first input.
      return new DerivedTupleSchema(firstInputSchema);
    }
  }
}
