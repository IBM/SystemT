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

import com.ibm.avatar.algebra.datamodel.RSEBindings;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.util.tokenize.OffsetsList;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.logging.Log;

/**
 * Base class for an operator that takes a single column as input and extracts annotations from the
 * span in the input column. Output schema is input schema plus the new annotation column. This
 * class also provides hooks for extraction operators that support RSE.
 */
public abstract class ExtractionOp extends SingleArgAnnotator implements RSEOperator {

  public ExtractionOp(String inCol, String outCol, Operator child) {
    super(inCol, outCol, child);

  }

  @Override
  protected void reallyEvaluate(MemoizationTable mt, TupleList childResults) throws Exception {
    // System.err.printf("%s.reallyEvaluate()\n", this);
    for (TLIter itr = childResults.iterator(); itr.hasNext();) {
      Tuple inputTup = itr.next();
      Span inputSpan = Span.convert(inputAcc.getVal(inputTup));
      if (inputSpan != null) {
        extract(mt, inputTup, inputSpan);
      }
    }
  }

  /**
   * Subclasses should override this method to perform the actual extraction task on a single input
   * annotation.
   * 
   * @param mt table of state information
   * @param inputTup the next input tuple
   * @param inputSpan the next input span (drawn from inputTup)
   */
  protected abstract void extract(MemoizationTable mt, Tuple inputTup, Span inputSpan)
      throws Exception;

  /**
   * Extraction for restricted span evaluation. Child classes that implement RSE should override
   * this method to perform the extraction step for the indicated set of bindings.
   * 
   * @param mt table of state informatio
   * @param inputTup the next input tuple
   * @param inputSpan the next input span (drawn from inputTup)
   * @param results container for result tuples
   * @param b bindings that indicate over what region of the input document/span to perform the
   *        extraction.
   */
  protected void extractRSE(MemoizationTable mt, Tuple inputTup, Span inputSpan, RSEBindings b)
      throws Exception {
    if (implementsRSE()) {
      throw new RuntimeException(
          "Class implements implementsRSE() method," + " but not extractRSE() method");
    }
    throw new RuntimeException("Not implemented");
  }

  @Override
  public boolean implementsRSE() {
    // Currently, most children of this class do *not* implement RSE.
    return false;
  }

  /**
   * Main entry point for RSE functionality, if the child class supports it. See comment in
   * interface for more information.
   */

  @Override
  public TupleList[] getNextRSE(MemoizationTable mt, RSEBindings[] b) throws Exception {
    mt.profileEnter(profRecord);

    // System.err.printf("%s.getNextRSE()\n", this);

    // Currently, most children of this class do *not* implement RSE.
    if (false == implementsRSE()) {
      throw new RuntimeException(String.format("Operator %s does not implement RSE", this));
    }

    // Make sure that our schema is set up.
    getOutputSchema();

    // Fetch the input tuple(s) ONCE; they'll be reused for each set of
    // bindings.
    TupleList childResults = getInputOp(0).getNext(mt);

    // Create the list of top-level tuples that we will return.
    TupleList[] ret = new TupleList[b.length];

    for (int i = 0; i < b.length; i++) {
      // Extraction is CPU intensive, so check for watchdog interrupts
      // before each binding.
      // if (Thread.interrupted()) {
      if (mt.interrupted) {
        mt.interrupted = false;
        throw new InterruptedException();
      }

      // Clear out the operator's results buffer.
      mt.resetResultsBuf(resultsBufIx, getOutputSchema());

      if (null != b[i]) {
        for (TLIter itr = childResults.iterator(); itr.hasNext();) {
          Tuple inputTup = itr.next();
          Span inputSpan = Span.convert(inputAcc.getVal(inputTup));
          extractRSE(mt, inputTup, inputSpan, b[i]);
        }
      }

      // Add the current result set to the array that we will return.
      ret[i] = mt.getResultsBuf(resultsBufIx);;
    }

    mt.profileLeave(profRecord);

    return ret;
  }

  /**
   * Part of restricted span evaluation.
   * 
   * @param inputSpan target span for extraction
   * @param b bindings specifying where within the target span extraction should be performed
   * @param tokens tokens of the target span
   * @param maxTok maximum number of tokens to look at after the beginning (or before the end) of
   *        the start/end token specified in the bindings
   * @return the range of tokens over which extraction should be performed; the range may be -1 to
   *         -1 if no tokens are available
   */
  protected int[] computeRSEMatchRange(Span inputSpan, RSEBindings b, OffsetsList tokens,
      int maxTok) {
    boolean debug = false;

    if (b.begin < 0) {
      throw new FatalInternalError(
          "Bindings specify a match range that starts " + "at %d, which is less than zero.",
          b.begin);
    }

    // Find the (inclusive) range of tokens over which matches could occur.
    int firstTokIx;
    int lastTokIx;

    // Figure out which tokens we need to look at.
    if (RSEBindings.STARTS_AT == b.type) {
      // Looking for a span that starts between b.begin and b.end.
      firstTokIx = tokens.nextBeginIx(b.begin);

      // Find the token that is at least one character past the end of the
      // region, then go back one token.
      // lastTokIx = tokens.nextBeginIx(b.end + 1) - 1;
      lastTokIx = tokens.prevBeginIx(b.end + 1);

    } else if (RSEBindings.ENDS_AT == b.type) {

      // Looking for a span that *ends* between b.begin and b.end.
      // That means that the first token of the span could be up to maxTok
      // tokens before b.begin.
      firstTokIx = tokens.nextBeginIx(b.begin) - maxTok;
      firstTokIx = Math.max(firstTokIx, 0);

      // Find the token that is at least one character past the end of the
      // region, then go back one token.
      // lastTokIx = tokens.nextBeginIx(b.end + 1) - 1;
      lastTokIx = tokens.prevBeginIx(b.end + 1);

      if (lastTokIx > tokens.size()) {
        // Sanity check
        throw new FatalInternalError("prevBeginIx(%d) on %d tokens (%s) returned index %d",
            b.end + 1, tokens.size(), tokens.getClass().getSimpleName(), lastTokIx);
      }
    } else {
      throw new RuntimeException(String.format("Don't know about bindings type %d", b.type));
    }

    if (debug) {
      int firstBeginOff =
          (firstTokIx > 0 && firstTokIx < tokens.size() ? tokens.begin(firstTokIx) : -1);
      int firstEndOff =
          (firstTokIx > 0 && firstTokIx < tokens.size() ? tokens.end(firstTokIx) : -1);
      int lastBeginOff = (lastTokIx == -1) ? -1 : tokens.begin(lastTokIx);
      int lastEndOff = (lastTokIx == -1) ? -1 : tokens.end(lastTokIx);
      CharSequence coveredText =
          (firstBeginOff > 0 && lastEndOff > 0 && lastEndOff >= firstBeginOff)
              ? inputSpan.getText().subSequence(firstBeginOff, lastEndOff)
              : "<Empty>";

      Log.debug("    Range [%d,%d] of type %d --> " + "tokens %d [%d,%d] to %d [%d,%d]: '%s'", //
          b.begin, b.end, b.type, //
          firstTokIx, firstBeginOff, firstEndOff, //
          lastTokIx, lastBeginOff, lastEndOff, //
          coveredText);
    }

    int[] tokRange = {firstTokIx, lastTokIx};
    return tokRange;
  }
}
