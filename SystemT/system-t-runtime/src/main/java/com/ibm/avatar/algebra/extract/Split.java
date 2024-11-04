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
package com.ibm.avatar.algebra.extract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.TreeMap;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.base.SingleArgAnnotator;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.SpanGetter;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.logging.Log;
import com.ibm.avatar.logging.MsgType;

/**
 * Class that splits the input string on a set of input spans, a la Perl's split function. Requires
 * *two* fields of its input tuples as input. The "source" field contains the overall text to split,
 * and the "split" field (which is the default field, accessible with SingleArgAnnotator's
 * inputCol/Acc), contains the split points. Output consists of the original input tuple plus a
 * column for the split chunks.
 * 
 */
public class Split extends SingleArgAnnotator {

  /**
   * Flag that indicates that the Split operator should retain the splitting span to the left of
   * each returned span. For example, when splitting the string "a,b" on the comma, RETAIN_LEFT
   * would result in a spit into "a" and ",b".
   */
  public static final int RETAIN_LEFT = 0x1;

  /** Mirror image of RETAIN_LEFT. */
  public static final int RETAIN_RIGHT = 0x2;

  public static final int RETAIN_BOTH = RETAIN_LEFT | RETAIN_RIGHT;

  /**
   * Flags that control various aspects of splitting; drawn from the constants above.
   */
  private int flags;

  /** Name of the column containing the spans of the original text. */
  private String sourceCol;

  /** Accessor for getting at the spans of the original text. */
  private SpanGetter sourceAcc;

  /**
   * Create a new Split operator that annotates a user-configured set of capturing groups.
   * 
   * @param child
   * @param sourceCol column of input tuples containing the context in which to split
   * @param splitCol index into the input tuples where the split points are located
   * @param outCol name of the single column in the output schema
   * @param flags various flags controlling how to perform the split
   */
  public Split(Operator child, String sourceCol, String splitCol, String outCol, int flags) {
    super(splitCol, outCol, child);

    this.sourceCol = sourceCol;
    this.flags = flags;
  }

  /**
   * Originally, we overrode the version of this method in the superclass, because, instead of
   * adding a column to the input schema, we returned a single-column output schema. Now, we create
   * the output schema by copying the input schema and adding one column, so that this subclass is
   * consistent with the behavior of parent SingleArgAnnotator. We need to instantiate an input
   * field copier. Since we are copying the contents of the input tuple, we no longer explicitly set
   * a spanSetter to point to a column that may be Text or Span, resolving defect
   * (AbstractTupleSchema.spanSetter() allows creation of span setters for Text columns)
   */
  @Override
  protected AbstractTupleSchema createOutputSchema() {
    inputSchema = getInputOp(0).getOutputSchema();

    // Bind our accessors to the schema.
    inputAcc = inputSchema.asSpanAcc(col);
    sourceAcc = inputSchema.asSpanAcc(sourceCol);

    // Add one Span column to the input schema.
    AbstractTupleSchema ret = new TupleSchema(inputSchema, outCol, FieldType.SPAN_TYPE);

    // String[] fieldNames = { sourceCol, outCol };
    // FieldType[] fieldTypes = { inputSpanType, inputSpanType };
    // TupleSchema ret = new TupleSchema(fieldNames, fieldTypes);

    outputAcc = ret.spanSetter(outCol);

    // We will copy the contents of the original tuple to the result tuple
    copier = ret.fieldCopier(inputSchema);

    return ret;
  }

  @Override
  protected void reallyEvaluate(MemoizationTable mt, TupleList childResults) throws Exception {

    // Start by dividing the interior tuples by source.
    // Index is source span; contents of ArrayList are the split points.
    TreeMap<Span, ArrayList<Span>> splitsBySrc = new TreeMap<Span, ArrayList<Span>>();

    // map to store associated input tuples with source spans
    TreeMap<Span, Tuple> tuplesBySrc = new TreeMap<Span, Tuple>();

    TLIter itr = childResults.iterator();
    while (itr.hasNext()) {
      Tuple tup = itr.next();
      Span sourceSpan = Span.convert(sourceAcc.getVal(tup));
      Span splitSpan = Span.convert(inputAcc.getVal(tup));

      // skip null spans
      if (sourceSpan == null)
        continue;

      ArrayList<Span> spans = splitsBySrc.get(sourceSpan);
      if (null == spans) {
        // No spans for this source; create a list to hold them.
        spans = new ArrayList<Span>();
        splitsBySrc.put(sourceSpan, spans);
      }

      // associate each unique source span with its input tuple
      Tuple tuples = tuplesBySrc.get(sourceSpan);
      if (null == tuples) {
        tuplesBySrc.put(sourceSpan, tup);
      }

      spans.add(splitSpan);
    }

    // Now process the different split points in order.
    for (Span sourceSpan : splitsBySrc.keySet()) {
      ArrayList<Span> splitPoints = splitsBySrc.get(sourceSpan);
      Tuple inTup = tuplesBySrc.get(sourceSpan);

      // Process the split points in order from left to right.
      // NULLs will sort to the *beginning* of the list.
      Collections.sort(splitPoints);

      // Special-case the first output span.
      {
        Span firstSplit = splitPoints.get(0);
        if (null == firstSplit) {
          // Null split point --> Return entire source span.
          addResultAnnot(inTup, sourceSpan, sourceSpan.getBegin(), sourceSpan.getEnd(), mt);
        } else if (0 != (flags & RETAIN_RIGHT)) {
          addResultAnnot(inTup, sourceSpan, sourceSpan.getBegin(), firstSplit.getEnd(), mt);
        } else if (firstSplit.getBegin() > sourceSpan.getBegin()) {
          addResultAnnot(inTup, sourceSpan, sourceSpan.getBegin(), firstSplit.getBegin(), mt);
        }
        // SPECIAL CASE for when we have a single split point at the beginning of the source span

        else if (splitPoints.size() == 1) {
          if (firstSplit.getEnd() < sourceSpan.getEnd())
            addResultAnnot(inTup, sourceSpan, firstSplit.getEnd(), sourceSpan.getEnd(), mt);
        }

        // END SPECIAL CASE
      }

      // END SPECIAL CASE

      // Handle all the spans between the first and last.
      for (int i = 1; i < splitPoints.size(); i++) {
        // At each point, there will be three spans:
        // prevSplit: the split point to the *left* of the output span
        // nextSplit: the split point to the *right* of the output span
        // outSpan: the current output span
        Span prevSplit = splitPoints.get(i - 1);
        Span nextSplit = splitPoints.get(i);

        if (null == nextSplit) {
          // SPECIAL CASE: Null split point --> Return entire source
          // span.
          addResultAnnot(inTup, sourceSpan, sourceSpan.getBegin(), sourceSpan.getEnd(), mt);
          // END SPECIAL CASE
        } else if (Span.overlaps(prevSplit, nextSplit)) {
          // Splitting spans overlap; skip the current split point.
          Log.log(MsgType.AQLRuntimeWarning, "In view %s, split" + " points %s and %s overlap",
              getViewName(), prevSplit, nextSplit);
        } else {

          // Compute the begin and end of the output span, depending
          // on the flags the caller set.
          int begin = (0 != (flags & RETAIN_LEFT)) ? prevSplit.getBegin() : prevSplit.getEnd();
          int end = (0 != (flags & RETAIN_RIGHT)) ? nextSplit.getEnd() : nextSplit.getBegin();

          // Sanity-check the spans we're about to create.
          if (begin > end) {

            System.err.printf("Span.overlaps(%s, %s)\n" + "    returns %s\n", prevSplit, nextSplit,
                Span.overlaps(prevSplit, nextSplit) ? "true" : "false");

            Text prevText = prevSplit.getDocTextObj();
            Text nextText = nextSplit.getDocTextObj();

            System.err.printf("prevSplit docText is %s\n" + "nextSplit docText is %s\n", prevText,
                nextText);

            System.err.printf("docTexts %s equal\n", prevText.equals(nextText) ? "are" : "are NOT");

            throw new RuntimeException(String.format(
                "Split: " + "Spans out of order;\n" + "    begin = %d, end = %d\n"
                    + "    prevSplit is %s\n" + "    nextSplit is %s",
                begin, end, prevSplit, nextSplit));
          }

          addResultAnnot(inTup, sourceSpan, begin, end, mt);
        }
      }

      // Special-case the last output span.
      {
        Span lastSplit = splitPoints.get(splitPoints.size() - 1);
        if (null == lastSplit) {
          // Null split point --> Return entire source span.
          if (splitPoints.size() > 1) {
            // ...but only if we haven't already covered this span
            // on the first split point checks.
            addResultAnnot(inTup, sourceSpan, sourceSpan.getBegin(), sourceSpan.getEnd(), mt);
          }
        } else if (0 != (flags & RETAIN_LEFT)) {
          addResultAnnot(inTup, sourceSpan, lastSplit.getBegin(), sourceSpan.getEnd(), mt);
        }

        // Extra check to avoid extraction of empty span at end of input span, when split point is
        // at end of input span
        else if ((lastSplit.getBegin() > sourceSpan.getBegin())
            && (lastSplit.getEnd() < sourceSpan.getEnd())) {
          addResultAnnot(inTup, sourceSpan, lastSplit.getEnd(), sourceSpan.getEnd(), mt);
        }
      }

    }

  }

  /**
   * Convenience function for adding a new annotation to the current document's worth of results.
   * 
   * @param inTup the input tuple associated with the span being split
   * @param sourceSpan the span being split
   * @param begin begin of span (index into the document text)
   * @param end end of span (index into the document text)
   * @param mt memoization table
   */
  protected void addResultAnnot(Tuple inTup, Span sourceSpan, int begin, int end,
      MemoizationTable mt) {
    Tuple resultTuple = createOutputTup();
    copier.copyVals(inTup, resultTuple);
    outputAcc.setVal(resultTuple, Span.makeBaseSpan(sourceSpan, begin, end));

    addResultTup(resultTuple, mt);
  }
}
