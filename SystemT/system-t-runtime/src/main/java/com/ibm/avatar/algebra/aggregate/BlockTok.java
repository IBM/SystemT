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
import com.ibm.avatar.algebra.util.tokenize.DerivedOffsetsList;
import com.ibm.avatar.algebra.util.tokenize.OffsetsList;

/**
 * Version of the Block operator that takes distances in tokens instead of characters.
 * 
 */
public class BlockTok extends Block {

  /**
   * How many tokens are allowed between consecutive members of a block
   */
  private final int toksBetween;

  /**
   * Create a new BlockTok operator.
   * 
   * @param toksBetween how many characters are allowed between consecutive members of a block
   * @param minSize minimum number of annotations that the operator will consider in a block
   * @param maxSize maximum number of annotations that the operator will consider in a block
   * @param col index of the annotations to merge into blocks *
   * @param outputCol name of output column, or NULL to use the default "block"
   * @param input root of the operator tree that produces our inputs
   */
  public BlockTok(int toksBetween, int minSize, int maxSize, String col, String outputCol,
      Operator input) {
    super(minSize, maxSize, col, outputCol, input);

    // Verify inputs.
    if (toksBetween < 0) {
      throw new IllegalArgumentException("toksBetween must be >= 0");
    }

    this.toksBetween = toksBetween;
  }

  @Override
  protected void findBlocks(MemoizationTable mt, TupleList intups) throws Exception {

    boolean debug = false;

    // Counter to make sure that the inputs are sorted by start position.
    int lastBegin = 0;

    // We iterate through the input tuples, outputting *all* the blocks that
    // start at each tuple.
    // Note that we're relying on the input being sorted by position.
    for (int startix = 0; startix < intups.size(); startix++) {

      // Note that we go back to the base (e.g. document-derived) span.
      Span startSpan = startAcc.getVal(intups.getElemAtIndex(startix));

      // skip if the span is null -- no blocks start at a null tuple
      if (startSpan == null)
        continue;

      // String doctext = startSpan.getDocText();
      int blockBeginOffset = startSpan.getBegin();

      if (debug) {
        System.err.printf("BlockTok: Start is '%s' (item %d)" + " at position [%d, %d]\n",
            startSpan.getText(), startix, startSpan.getBegin(), startSpan.getEnd());
      }

      // Verify that input is ordered properly
      if (blockBeginOffset < lastBegin) {
        throw new RuntimeException("Input to Block operator must be sorted by position");
      }
      lastBegin = blockBeginOffset;

      // Build up blocks of adjacent spans, using charsBetween as the
      // definition of how close "adjacent" is.
      int blockEndOffset = startSpan.getEnd();

      // Find the range of chars that we will consider for the next guy in
      // the block.
      DerivedOffsetsList tokens = mt.getTempOffsetsList();
      mt.getTokenizer().tokenize(startSpan.getDocTextObj(), startSpan.getEnd(), toksBetween + 1,
          tokens);

      // Watch out for corner cases...
      int maxNextItemOffset = computeMaxNextItemOff(startSpan, tokens);

      if (debug) {
        System.err.printf("BlockTok:    --> " + "Next item can start at up to position %d\n",
            maxNextItemOffset);
      }

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
      while (endix < intups.size() && blockSize < maxSize && keepgoing) {
        Span endSpan = endAcc.getVal(intups.getElemAtIndex(endix));

        // All the spans in the block must have same document text
        if (!startSpan.hasSameDocText(endSpan)) {
          lastBegin = 0;
          break;
        }

        if (debug) {
          System.err.printf("BlockTok:    --> " + "Trying '%s' (item %d) at position [%d, %d]\n",
              endSpan.getText(), endix, endSpan.getBegin(), endSpan.getEnd());
        }

        // If the candidate "end" annotation is within the specified
        // distance of the end of the current block, extend the block.
        if (endSpan.getBegin() <= maxNextItemOffset) {

          // The next span may actually be entirely contained within
          // the block, so make sure we don't shrink the block.
          blockEndOffset = Math.max(endSpan.getEnd(), blockEndOffset);

          // Update the range of text we consider for the next item in
          // the block
          if (debug) {
            System.err.printf("BlockTok:" + "       --> Adding to block\n" + "BlockTok:"
                + "       --> Tokenizing from %d\n", blockEndOffset);
          }

          mt.getTokenizer().tokenize(startSpan.getDocTextObj(), blockEndOffset, toksBetween + 1,
              tokens);
          maxNextItemOffset =
              // tokens.begin(toksBetween);
              computeMaxNextItemOff(startSpan, tokens);

          if (debug) {
            System.err.printf(
                "BlockTok:" + "       --> Next item" + " now can start at up to position %d\n",
                maxNextItemOffset);
          }

          blockSize++;

          if (blockSize >= minSize) {
            if (debug) {
              System.err.printf(
                  "BlockTok:       " + "--> Generating output " + "(blockSize is %d/%d)\n",
                  blockSize, maxSize);
            }

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

  private int computeMaxNextItemOff(Span startSpan, OffsetsList tokens) {
    int maxNextItemOffset;
    if (0 == tokens.size()) {
      // SPECIAL CASE: No tokens found.
      // We allow distances of zero tokens between elements of a
      // block, so allow anything from this point to the end of the
      // document.
      maxNextItemOffset = startSpan.getDocText().length();
    } else if (tokens.size() <= toksBetween) {
      // SPECIAL CASE: Got fewer tokens than requested.
      // Allow anything from this point to the end of the document.
      maxNextItemOffset = startSpan.getDocText().length();
    } else {
      // Normal case: There are at least toksBetween + 1 tokens after
      // the current start point.
      maxNextItemOffset = tokens.begin(toksBetween);
    }
    return maxNextItemOffset;
  }
}
