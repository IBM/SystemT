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
package com.ibm.avatar.algebra.aggregate;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;

/**
 * Operator that merges blocks of adjacent annotations. Here "adjacent" is defined as being within a
 * certain (configurable) number of characters of the previous and next annotation in the block. A
 * second parameter controls the minimum number of component annotations needed for a range of
 * tuples to qualify as a block. The operator outputs *all* such blocks, including blocks that are
 * contained within other blocks. Output schema is always (Doc, Annotation), where each annotation
 * covers a block of adjacent annotations in the original input.
 * 
 */
public class BlockChar extends Block {

  /**
   * How many characters are allowed between consecutive members of a block
   */
  private final int charsBetween;

  /**
   * Create a new Block operator.
   * 
   * @param charsBetween how many characters are allowed between consecutive members of a block
   * @param minSize minimum number of annotations that the operator will consider in a block
   * @param maxSize maximum number of annotations that the operator will consider in a block
   * @param col column containing the spans to merge into blocks *
   * @param outputCol name of output column, or NULL to use the default "block"
   * @param input root of the operator tree that produces our inputs
   */
  public BlockChar(int charsBetween, int minSize, int maxSize, String col, String outputCol,
      Operator input) {
    super(minSize, maxSize, col, outputCol, input);

    // Verify inputs.
    if (charsBetween < 0) {
      throw new IllegalArgumentException("charsBetween must be >= 0");
    }

    this.charsBetween = charsBetween;
  }

  @Override
  protected void findBlocks(MemoizationTable mt, TupleList input) throws Exception {

    // Counter to make sure that the inputs are sorted by start position.
    int lastBegin = 0;

    // We iterate through the input tuples, outputting *all* the blocks that
    // start at each tuple.
    // Note that we're relying on the input being sorted by position.
    for (int startix = 0; startix < input.size(); startix++) {

      // Note that we go back to the base (e.g. document-derived) span.
      Span startSpan = startAcc.getVal(input.getElemAtIndex(startix));

      // skip if the span is null -- no blocks start at a null tuple
      if (startSpan == null)
        continue;

      int blockBeginOffset = startSpan.getBegin();

      // Verify that input is ordered properly
      if (blockBeginOffset < lastBegin) {
        throw new RuntimeException("Input to Block operator must be sorted by position");
      }
      lastBegin = blockBeginOffset;

      // Build up blocks of adjacent spans, using charsBetween as the
      // definition of how close "adjacent" is.
      int blockEndOffset = startSpan.getEnd();
      int blockSize = 1;

      if (1 == minSize) {
        // SPECIAL CASE: Blocks of size 1 are allowed.
        Span blockSpan = Span.makeBaseSpan(startSpan, blockBeginOffset, blockEndOffset);

        Tuple outTup = createOutputTup();
        outAcc.setVal(outTup, blockSpan);

        addResultTup(outTup, mt);
        // END SPECIAL CASE
      }

      int endix = startix + 1;
      boolean keepgoing = true;
      while (endix < input.size() && blockSize < maxSize && keepgoing) {

        Span endSpan = endAcc.getVal(input.getElemAtIndex(endix));

        // All the spans in the block must have same document text
        if (!startSpan.hasSameDocText(endSpan)) {
          lastBegin = 0;
          break;
        }

        // If the candidate "end" annotation is within the specified
        // distance of the end of the current block, extend the block.
        if ((endSpan.getBegin() - blockEndOffset) <= charsBetween) {

          // The next span may actually be entirely contained within
          // the block, so make sure we don't shrink the block.
          blockEndOffset = Math.max(endSpan.getEnd(), blockEndOffset);
          blockSize++;

          if (blockSize >= minSize) {
            // Block is large enough to send to the output, so
            // generate an output tuple.
            Span blockSpan = Span.makeBaseSpan(startSpan, blockBeginOffset, blockEndOffset);

            Tuple outTup = createOutputTup();
            outAcc.setVal(outTup, blockSpan);

            addResultTup(outTup, mt);

            // Note that, after generating an output tuple, we keep
            // trying to extend the block, generating new output
            // each time.
          }
        } else {
          // Candidate "end" annotation is too far away to add to this
          // block.
          keepgoing = false;
        }

        endix++;
      }

    }
  }
}
