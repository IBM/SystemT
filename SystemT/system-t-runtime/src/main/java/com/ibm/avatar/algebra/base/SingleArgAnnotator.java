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
package com.ibm.avatar.algebra.base;

import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldCopier;
import com.ibm.avatar.algebra.datamodel.FieldSetter;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.SpanGetter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.logging.Log;

/**
 * Base class for annotators that read a single input and add a single output column to the schema
 * of the input.
 */
public abstract class SingleArgAnnotator extends SingleInputOperator {

  /** Cached copy of the input schema to this operator, for subclasses to use. */
  protected AbstractTupleSchema inputSchema;

  // protected TupleSchema interiorInputSchema;

  /** Name of the input column from which we read input spans. */
  protected String col;

  /** Name of the output column we create. */
  protected String outCol;

  /** Accessor for setting the "output" column in our output tuples. */
  protected FieldSetter<Span> outputAcc;

  /** Object for copying preserved fields from source to destination. */
  protected FieldCopier copier;

  /** Accessor for getting at the input column. */
  protected SpanGetter inputAcc;

  protected AbstractTupleSchema getInputSchema() {
    if (null == inputSchema) {
      throw new RuntimeException("Tried to fetch input schema before creating it");
    }
    return inputSchema;
  }

  /**
   * The type of the annotations that this operator by default adds to the interior schema of its
   * output tuples.
   */
  // protected AnnotationType annotType;
  public SingleArgAnnotator(String col, String outCol, Operator child) {
    super(child);
    this.col = col;
    this.outCol = outCol;
  }

  /**
   * Default behavior is to add a single "Annotation" column. Note that, if you override this
   * method, you must implement the setting of inputAcc
   */
  @Override
  protected AbstractTupleSchema createOutputSchema() {
    final boolean debug = false;

    inputSchema = getInputOp(0).getOutputSchema();

    // Output span should be over the same target text as the input span.
    TupleSchema ret = new TupleSchema(inputSchema, outCol, FieldType.SPAN_TYPE);

    // Bind our accessors to the schema.
    inputAcc = inputSchema.asSpanAcc(col);

    copier = ret.fieldCopier(inputSchema);
    outputAcc = ret.spanSetter(outCol);

    if (debug) {
      Log.debug("Created schema for %s:", this);
      Log.debug("    Input schema was: %s", inputSchema);
      Log.debug("    Output schema is: %s", ret);
    }

    // System.out.println ("SingleArg.createOutputSchema: " + ret);
    return ret;
  }

  /**
   * Convenience function for adding a new annotation to the current document's worth of results.
   * 
   * @param inTup input tuple that resulted in the annotation being generated (annotation will be
   *        appended to this tuple)
   * @param begin begin of span (index into the source span)
   * @param end end of span (index into source span)
   * @param inputSpan source for new span
   * @param mt
   */
  protected void addResultAnnot(Tuple inTup, int begin, int end, Span inputSpan,
      MemoizationTable mt) {
    Span span = Span.makeSubSpan(inputSpan, begin, end);

    Tuple resultTuple = createOutputTup();
    copier.copyVals(inTup, resultTuple);
    outputAcc.setVal(resultTuple, span);

    addResultTup(resultTuple, mt);
  }

}
