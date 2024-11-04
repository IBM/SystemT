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
package com.ibm.avatar.algebra.join;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.InternalUtils;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.algebra.joinpred.FollowedByTokMP;
import com.ibm.avatar.algebra.joinpred.FollowsTokMP;
import com.ibm.avatar.algebra.joinpred.MergeJoinPred;
import com.ibm.avatar.algebra.relational.CartesianProduct;
import com.ibm.avatar.algebra.util.tokenize.BaseOffsetsList;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.logging.Log;

/**
 * Specialized join operator for handling the most common type of join predicate, FollowsTok().
 * Optimized for cases where the inputs to the join start and end on token boundaries. Exposes the
 * same API as SortMergeJoin.
 * 
 */
public class AdjacentJoin extends CartesianProduct {

  public static final boolean debug = false;

  // Enable (verbose) debugging of span token index caching
  public static final boolean debugTokIxCaching = false;

  /**
   * Flag that is set to TRUE if the outer operand should come first in the document. Set to FALSE
   * if the inner should be first.
   */
  private boolean outerFirst;

  /**
   * Which columns of the outer and inner tuples we evaluate the predicate over.
   */
  private final ScalarFunc outerArg, innerArg;

  /**
   * How many tokens are allowed between spans from the "first" and "second" arguments.
   */
  private int minTok, maxTok;

  /**
   * Main constructor.
   * 
   * @param pred the join predicate that you would pass to SortMergeJoin; must be FollowsTokMP or
   *        FollowedByTokMP with an offset of zero
   * @param outer outer (left) operand of the join
   * @param inner inner (right) operand of the join
   * @throws ParseException
   */
  public AdjacentJoin(MergeJoinPred pred, Operator outer, Operator inner) throws ParseException {
    super(outer, inner);

    setConditionalEval(SortMergeJoin.CONDITIONAL_EVAL);

    // Make sure that this is an adjacency predicate, and determine which
    // input must come first.
    if (pred instanceof FollowsTokMP) {
      outerFirst = true;

      FollowsTokMP ft = (FollowsTokMP) pred;
      minTok = ft.getMintok();
      maxTok = ft.getMaxtok();

    } else if (pred instanceof FollowedByTokMP) {
      outerFirst = false;

      FollowedByTokMP fbt = (FollowedByTokMP) pred;
      minTok = fbt.getMintok();
      maxTok = fbt.getMaxtok();
    } else {
      throw new ParseException(
          "AdjacentJoin can only evaluate" + " FollowsTokMP and FollowedByTokMP predicates");
    }

    // Get handles on the arguments.
    MergeJoinPred mp = pred;
    outerArg = mp.getOuterArg();
    innerArg = mp.getInnerArg();
  }

  @Override
  protected void reallyEvaluate(MemoizationTable mt, TupleList[] childResults) throws Exception {

    TupleList outerList = childResults[OUTER_IX];
    TupleList innerList = childResults[INNER_IX];

    // Determine which tuples should go first and which are second in the
    // document text.
    TupleList firstTups, secondTups;
    ScalarFunc firstArg, secondArg;
    if (outerFirst) {
      firstTups = outerList;
      firstArg = outerArg;
      secondTups = innerList;
      secondArg = innerArg;
    } else {
      firstTups = innerList;
      firstArg = innerArg;
      secondTups = outerList;
      secondArg = outerArg;
    }

    int numFirst = firstTups.size();
    int numSecond = secondTups.size();

    if (0 == numFirst || 0 == numSecond) {
      // SPECIAL CASE: At least one input has no tuples.
      return;
      // END SPECIAL CASE
    }

    if (debug) {
      Log.debug("AdjacentJoin: Starting join " + "in view '%s'", getViewName());
    }

    // We will divide the spans into two classes: Those that start and end
    // within
    // tokens, and everyone else. The "everyone else" set is the
    // "fast path", and the mid-token set is the "slow path".
    // We start by creating a sort key for each input span. The high-order
    // bits of this tuple will consist of the token index of the span (or -1
    // if the span starts in the middle of a token), and the lower bits hold
    // an index into our input tuple list.
    long[] firstKeys = new long[numFirst];
    long[] secondKeys = new long[numSecond];

    // Dummy class just to call these two methods
    InternalUtils utils = new InternalUtils();

    for (int i = 0; i < numFirst; i++) {
      Tuple tup = firstTups.getElemAtIndex(i);

      Span first = (Span) firstArg.oldEvaluate(tup, mt);
      int endTokIx = utils.getEndTokIx(first, mt);

      if (debug) {
        Log.debug("    First span %d: %s, with end tok %d", i, first, endTokIx);
      }

      // Construct the key that we will use to sort the input spans.
      // For performance, we pack two integers into a 64-bit long; the
      // first (most significant) is the token ID at the end of the span;
      // the second (least significant) is the index into the list of
      // spans/tuples in the original input.
      // We sort an array of these 64-bit packed values because it is much
      // cheaper than sorting an array of objects, e.g. the tuples
      // themselves.
      firstKeys[i] = makeKey(endTokIx, i);

      // if (debug) {
      // Log.debug (" --> Key is 0x%016x (%d/%d)", firstKeys[i], keyToMSB (firstKeys[i]), keyToLSB
      // (firstKeys[i]));
      // }
    }

    for (int i = 0; i < numSecond; i++) {
      Tuple tup = secondTups.getElemAtIndex(i);

      Span second = Span.convert(secondArg.oldEvaluate(tup, mt));
      int beginTokIx = utils.getBeginTokIx(second, mt);

      if (debug) {
        Log.debug("    Second span %d: %s, with begin tok %d", i, second, beginTokIx);
      }

      // Construct the key that we will use to sort the input spans.
      secondKeys[i] = makeKey(beginTokIx, i);

      // if (debug) {
      // Log.debug (" --> Key is 0x%016x (%d/%d)", secondKeys[i], keyToMSB (secondKeys[i]), keyToLSB
      // (secondKeys[i]));
      // }
    }

    // Now we can sort the arrays of longs we've created.
    Arrays.sort(firstKeys);
    Arrays.sort(secondKeys);

    // Determine how many tuples will be processed in the "slow path"
    // because they start or end inside a token.
    // The call to getEndTokIx() above returns Span.NOT_AN_OFFSET (-2) if
    // the span passed to it ends in the middle of a token; returns {@link
    // Span#NOT_AN_OFFSET} if the span ends before the start of the first
    // token; and returns the index of the last token before the end of the
    // span in all other cases.
    // Note that the sort operation above has placed all the "-1" values at
    // the beginning of the array, so we just need to compute how many of
    // them there are.
    int numFirstSlow = 0, numSecondSlow = 0;
    while (numFirstSlow < numFirst
        && Span.NOT_A_TOKEN_OFFSET == keyToMSB(firstKeys[numFirstSlow])) {
      numFirstSlow++;
    }
    while (numSecondSlow < numSecond
        && Span.NOT_A_TOKEN_OFFSET == keyToMSB(secondKeys[numSecondSlow])) {
      numSecondSlow++;
    }

    /*
     * SLOW PATH: Only trigger the slow path if it's necessary. We trigger the slow path if we have
     * either (1) just slow-path "first" spans, OR (2) just slow-path "second" spans, OR (3) both
     * slow-path "first" spans and slow-path "second" spans. The slow path will take care of joining
     * the following: a) slow-path "first" spans with slow-path "second" spans; (b) slow-path
     * "first" spans with fast-path "second" spans; (c) fast-path "first" spans with slow-path
     * "second" spans.
     */
    if (numFirstSlow > 0 || numSecondSlow > 0) {
      if (debug) {
        Log.debug("AdjacentJoin: %d 'first' and %d 'second' inputs go to slow path", numFirstSlow,
            numSecondSlow);
      }

      evalSlowPath(mt, firstTups, secondTups, firstArg, secondArg, firstKeys, secondKeys,
          numFirstSlow, numSecondSlow);
    } else {
      if (debug) {
        Log.debug("AdjacentJoin: slow path not used for this document");
      }
    }

    if (numFirstSlow == numFirst || numSecondSlow == numSecond) {
      // SPECIAL CASE: Everything follows the slow path; no fast-path
      // answers.
      return;
      // END SPECIAL CASE
    }

    /*
     * FAST PATH: Handle the "fast path" tuples with a single pass through the arrays.
     */
    int secondIx = numSecondSlow;
    int secondTok = keyToMSB(secondKeys[secondIx]);

    for (int firstIx = numFirstSlow; firstIx < numFirst; firstIx++) {
      // Find all the "second" tuples that match; that is, all the spans
      // that start exactly one token after the first span.
      int firstTok = keyToMSB(firstKeys[firstIx]);

      if (debug) {
        Log.debug("AdjacentJoin: First ix %d --> token %d", firstIx, firstTok);
      }

      // First, "rewind" our cursor on the "second" tuples, in case it's
      // positioned at the end of a block of matching tuples.
      while (secondTok >= firstTok + minTok + 1 && secondIx > 0 && secondIx >= numSecondSlow) {
        secondIx--;
        secondTok = computeSecondTok(secondKeys, secondIx);
      }

      // Now move forward until we find a matching region or go past where
      // it would be.
      while (secondTok < firstTok + minTok + 1 && secondIx < numSecond) {
        secondIx++;
        secondTok = computeSecondTok(secondKeys, secondIx);
      }

      if (debug) {
        Log.debug("AdjacentJoin: Scanning second from index %d", secondIx);
      }

      // Then go forward through the region of "second" tuples that match.
      while (secondTok <= firstTok + maxTok + 1 && secondIx < numSecond) {

        // Found a match; retrieve the original tuples and generate an
        // output tuple.
        int firstTupIx = keyToLSB(firstKeys[firstIx]);
        int secondTupIx = keyToLSB(secondKeys[secondIx]);

        Tuple firstTup = firstTups.getElemAtIndex(firstTupIx);
        Tuple secondTup = secondTups.getElemAtIndex(secondTupIx);

        if (debug) {
          Log.debug(
              "AdjacentJoin: Got a match - 1F/2F:\n"
                  + "    first is tuple %d->%d with token %d: %s\n"
                  + "    second is tuple %d->%d with token %d: %s",
              firstIx, firstTupIx, firstTok, firstTup, //
              secondIx, secondTupIx, secondTok, secondTup);
        }

        boolean skipThisOutput = false;
        if (0 == minTok && secondTok == firstTok + 1) {
          // SPECIAL CASE: If the token distance is zero and there are
          // a pair of spans such that the "first" span ends in
          // between two tokens and the "second" span begins between
          // the same two tokens, then the above algorithm erroneously
          // returns a match. Filter out these false positives here
          // instead of slowing down the algorithm.
          Span firstSpan = (Span) firstArg.oldEvaluate(firstTup, mt);
          Span secondSpan = (Span) secondArg.oldEvaluate(secondTup, mt);

          // bug #17
          // when the first tuple is null (-1) and second token idx is 0
          // and minToken == 0 firstSpan will be null in this section
          if (firstSpan == null) {
            skipThisOutput = true;
          } else if (!firstSpan.hasSameDocText(secondSpan)) {
            skipThisOutput = true;
          } else if (firstSpan.getEnd() > secondSpan.getBegin()) {
            skipThisOutput = true;
          }
        }

        if (false == skipThisOutput) {
          if (debug)
            Log.debug("Generating output tuple for first tuple %s, second tuple %s\n", firstTup,
                secondTup);
          genOutputTuple(firstTup, secondTup, mt);
        } else {
          if (debug)
            Log.debug("Skipping output tuple for first tuple %s, second tuple %s\n", firstTup,
                secondTup);
        }

        secondIx++;
        secondTok = computeSecondTok(secondKeys, secondIx);
      }
    }
  }

  private int computeSecondTok(long[] secondKeys, int secondIx) {
    if (secondIx >= 0 && secondIx < secondKeys.length) {
      return keyToMSB(secondKeys[secondIx]);
    } else {
      return Integer.MAX_VALUE;
    }
  }

  private int computeFirstTok(long[] firstKeys, int firstIx) {
    if (firstIx >= 0 && firstIx < firstKeys.length) {
      return keyToMSB(firstKeys[firstIx]);
    } else if (firstIx <= 0) {
      return Integer.MIN_VALUE;
    } else
      // firstIx >= firstKeys.length
      return Integer.MAX_VALUE;
  }

  /**
   * Slow-path handling for the join; handles all those input spans that start or end in the middle
   * of a token.
   * 
   * @throws TextAnalyticsException
   */
  private void evalSlowPath(MemoizationTable mt, TupleList firstTups, TupleList secondTups,
      ScalarFunc firstArg, ScalarFunc secondArg, long[] firstKeys, long[] secondKeys,
      int numFirstSlow, int numSecondSlow) throws TextAnalyticsException {

    // ------------------------------------------------------------------------
    // Slow path Part 1: Join slow-path "first" spans with slow-path "second" spans
    // ------------------------------------------------------------------------

    // Slow-path handling should be slow but scalable, so we use an
    // algorithm that scales with the number of matches and the size of the
    // token distance range, and is O(n) in the number of input tuples.

    // We insert the "second" spans into a hashtable and probe the "first"
    // ones to see if they match.
    // Index of the table is the index of the token in which the "second"
    // span starts.

    // Insertion phase:
    HashMap<Integer, ArrayList<Tuple>> tokIxToTups = new HashMap<Integer, ArrayList<Tuple>>();
    for (int i = 0; i < numSecondSlow; i++) {
      // Tuple index is stored in the lower-order bits of the key.
      int tupIx = keyToLSB(secondKeys[i]);

      // Determine where the indicated span begins.
      Tuple tup = secondTups.getElemAtIndex(tupIx);
      Span span = (Span) secondArg.oldEvaluate(tup, mt);

      Text text = span.getDocTextObj();
      BaseOffsetsList textToks = (BaseOffsetsList) mt.getTokenizer().tokenize(text);

      // We know that this span starts in the middle of a token, since it
      // is a "slow-path" span.
      Integer startTokIx = textToks.prevBeginIx(span.getBegin());

      ArrayList<Tuple> tups = tokIxToTups.get(startTokIx);
      if (null == tups) {
        tups = new ArrayList<Tuple>();
        tokIxToTups.put(startTokIx, tups);
      }
      tups.add(tup);
    }

    // Probe phase: check that slow-path "first" spans match with slow-path "second" spans.
    for (int i = 0; i < numFirstSlow; i++) {
      // Tuple index is stored in the lower-order bits of the key.
      int tupIx = keyToLSB(firstKeys[i]);

      // Determine where the indicated span ends.
      Tuple tup = firstTups.getElemAtIndex(tupIx);
      Span span = (Span) firstArg.oldEvaluate(tup, mt);

      Text text = span.getDocTextObj();
      BaseOffsetsList textToks = (BaseOffsetsList) mt.getTokenizer().tokenize(text);

      // We know that this span ends in the middle of a token, since it is
      // a "slow-path" span
      int endTokIx = textToks.prevBeginIx(span.getEnd());

      for (int offset = minTok; offset <= maxTok; offset++) {

        if (0 == offset) {
          // CASE 1: Zero tokens allowed between
          // inputs; since both are "slow-path" tuples, they
          // must be adjacent in character terms
          ArrayList<Tuple> tups = tokIxToTups.get(endTokIx);
          if (null != tups) {
            for (Tuple secondTup : tups) {
              Span secondSpan = (Span) secondArg.oldEvaluate(secondTup, mt);
              if (secondSpan.getBegin() == span.getEnd()) {
                // Found a match

                if (debug) {
                  Log.debug(
                      "AdjacentJoin: Got a match - 1S/2S:\n"
                          + "    first is tuple %d->%d with token %d: %s\nsecond is tuple: %s",
                      i, tupIx, endTokIx, tup, secondTup);
                }

                genOutputTuple(tup, secondTup, mt);
              }
            }
          }
          // END CASE 1
        } else if (1 == offset) {
          // CASE 2: One token allowed between inputs.
          // Since the first span ends in the middle of a token and
          // the second starts in the middle of a token, this case can
          // only occur if both spans are in the *same* token and the
          // second comes after the first.
          ArrayList<Tuple> tups = tokIxToTups.get(endTokIx);
          if (null != tups) {
            for (Tuple secondTup : tups) {
              Span secondSpan = (Span) secondArg.oldEvaluate(secondTup, mt);
              if (secondSpan.getBegin() > span.getEnd()) {
                // Found a match
                if (debug) {
                  Log.debug(
                      "AdjacentJoin: Got a match - 1S/2S:\n"
                          + "    first is tuple %d->%d with token %d: %s\nsecond is tuple: %s",
                      i, tupIx, endTokIx, tup, secondTup);
                }
                genOutputTuple(tup, secondTup, mt);
              }
            }
          }
          // END CASE 2
        } else {
          // CASE 3: >= 2 tokens between inputs
          // Since the first span ends in the middle of a token and
          // the second starts in the middle of a token, the number of
          // tokens between them is equal to 1 + the difference in
          // token indices.
          ArrayList<Tuple> tups = tokIxToTups.get(endTokIx + offset - 1);
          if (null != tups) {
            for (Tuple secondTup : tups) {
              // All spans that are in the proper token must match.
              if (debug) {
                Log.debug(
                    "AdjacentJoin: Got a match - 1S/2S:\n"
                        + "    first is tuple %d->%d with token %d: %s\nsecond is tuple: %s",
                    i, tupIx, endTokIx, tup, secondTup);
              }
              genOutputTuple(tup, secondTup, mt);
            }
          }
          // END CASE 3
        }
      }
    }

    // Now we need to check whether (1) any of the slow-path "first" spans match with fast-path
    // "second" spans, and (2)
    // any of the fast-path "first" spans match with slow-path "second" spans.

    // Of course, this can only occur if the number of tokens allowed
    // between spans is at least 1.
    if (0 == maxTok) {
      return;
    }

    // ------------------------------------------------------------------------
    // Slow path Part 2: Join slow-path "first" spans match with fast-path "second" spans
    // ------------------------------------------------------------------------

    // Laura: fix for bug [#166957]-ArrayIndexOutOfBoundsException in AdjacentJoin.evalSlowPath()
    // This can also only occur if there is at least one fast-path "second" span.
    if (numSecondSlow < secondTups.size()) {
      // Laura: end fix for bug [#166957]

      // Start by sorting the "first" slow-path tuples by end token.
      long[] sortedFirstSlow = new long[numFirstSlow];
      for (int i = 0; i < numFirstSlow; i++) {
        // Tuple index is stored in the lower-order bits of the key.
        int tupIx = keyToLSB(firstKeys[i]);

        // Determine where the indicated span ends.
        Tuple tup = firstTups.getElemAtIndex(tupIx);
        Span span = (Span) firstArg.oldEvaluate(tup, mt);

        Text text = span.getDocTextObj();
        BaseOffsetsList textToks = (BaseOffsetsList) mt.getTokenizer().tokenize(text);

        // We know that this span ends in the middle of a token, since it is
        // a "slow-path" span
        int endTokIx = textToks.prevBeginIx(span.getEnd());

        sortedFirstSlow[i] = makeKey(endTokIx, tupIx);

        if (debug) {
          Log.debug("  --> SortedFirstSlow Key is 0x%016x (%d/%d)", sortedFirstSlow[i],
              keyToMSB(sortedFirstSlow[i]), keyToLSB(sortedFirstSlow[i]));
        }
      }

      Arrays.sort(sortedFirstSlow);

      // We make a single pass through both arrays, similar to the fast path processing.
      int secondIx = numSecondSlow; // this is the first fast 'second' span
      int secondTok = keyToMSB(secondKeys[secondIx]);
      int numSecond = secondTups.size(); // this is the last fast 'second' span

      // We don't check for zero-token distances, as they can never occur
      // anyway, and allowing them below would screw up our logic.
      int minTokToCheck = Math.max(1, minTok);

      // Note that we are scanning over sortedFirstSlow[], not firstKeys[]
      for (int firstIx = 0; firstIx < numFirstSlow; firstIx++) {

        // Find all the "second" tuples that match; that is, all the spans that start exactly one
        // token after the first
        // span.
        int firstTok = keyToMSB(sortedFirstSlow[firstIx]);

        if (debug) {
          Log.debug("AdjacentJoin: First ix %d --> token %d", firstIx, firstTok);
        }

        if (debug)
          Log.debug("0. firstTok=%d, secondTok=%d, secondIx=%d\n", firstTok, secondTok, secondIx);

        // First, "rewind" our cursor on the "first" tuples, in case it's positioned at the end of a
        // block of matching
        // tuples.
        // The "slow path" first spans all end in the middle of a token, so all the token offsets
        // are reduced by 1
        // relative to the "fast path" code.
        while (secondTok >= firstTok + minTokToCheck && secondIx > 0 && secondIx >= numSecondSlow) {
          secondIx--;
          secondTok = computeSecondTok(secondKeys, secondIx);
          if (debug)
            Log.debug("1. firstTok=%d, secondTok=%d, secondIx=%d\n", firstTok, secondTok, secondIx);
        }

        // Now move forward until we find a matching region or go past where it would be.
        while (secondTok < firstTok + minTokToCheck && secondIx < numSecond) {
          secondIx++;
          secondTok = computeSecondTok(secondKeys, secondIx);
          if (debug)
            Log.debug("2. firstTok=%d, secondTok=%d, secondIx=%d\n", firstTok, secondTok, secondIx);
        }

        // Then go forward through the region of "second" tuples that match.
        while (secondTok <= firstTok + maxTok && secondIx < numSecond) {

          // Found a match; retrieve the original tuples and generate an
          // output tuple.
          int firstTupIx = keyToLSB(sortedFirstSlow[firstIx]);
          int secondTupIx = keyToLSB(secondKeys[secondIx]);

          Tuple firstTup = firstTups.getElemAtIndex(firstTupIx);
          Tuple secondTup = secondTups.getElemAtIndex(secondTupIx);

          if (debug) {
            Log.debug("AdjacentJoin: Got a match - 1S/2F:\n"
                + "    first is tuple %d->%d with token %d: %s\n    second is tuple %d->%d with token %d: %s",
                firstIx, firstTupIx, firstTok, firstTup, //
                secondIx, secondTupIx, secondTok, secondTup);
          }

          genOutputTuple(firstTup, secondTup, mt);

          secondIx++;
          secondTok = computeSecondTok(secondKeys, secondIx);

          if (debug)
            Log.debug("3. firstTok=%d, secondTok=%d, secondIx=%d\n", firstTok, secondTok, secondIx);
        }
      }
    }

    // ------------------------------------------------------------------------
    // Slow path Part 3: Join fast-path "first" spans match with slow-path "second" spans
    // ------------------------------------------------------------------------

    // This can also only occur if there is at least one fast-path "first" span.
    if (numFirstSlow == firstTups.size()) {
      return;
    }

    // Start by sorting the "second" slow-path tuples by begin token.
    long[] sortedSecondSlow = new long[numSecondSlow];
    for (int i = 0; i < numSecondSlow; i++) {
      // Tuple index is stored in the lower-order bits of the key.
      int tupIx = keyToLSB(secondKeys[i]);

      // Determine where the indicated span ends.
      Tuple tup = secondTups.getElemAtIndex(tupIx);
      Span span = (Span) secondArg.oldEvaluate(tup, mt);

      Text text = span.getDocTextObj();
      BaseOffsetsList textToks = (BaseOffsetsList) mt.getTokenizer().tokenize(text);

      // We know that this span ends in the middle of a token, since it is
      // a "slow-path" span
      int beginTokIx = textToks.nextBeginIx(span.getBegin());

      sortedSecondSlow[i] = makeKey(beginTokIx, tupIx);

      if (debug) {
        Log.debug("  --> SortedSecondSlow Key is 0x%016x (%d/%d)", sortedSecondSlow[i],
            keyToMSB(sortedSecondSlow[i]), keyToLSB(sortedSecondSlow[i]));
      }
    }

    Arrays.sort(sortedSecondSlow);

    // We make a single pass through both arrays, similar to the fast path processing.
    int firstIx = numFirstSlow; // this is the first fast 'first' span
    int firstTok = keyToMSB(firstKeys[firstIx]);
    int numFirst = firstTups.size(); // this is the last fast 'first' span

    // We don't check for zero-token distances, as they can never occur
    // anyway, and allowing them below would screw up our logic.
    int minTokToCheck = Math.max(1, minTok);

    // Note that we are scanning over sortedSecondSlow[], not secondKeys[]
    for (int secondIx = numSecondSlow - 1; secondIx >= 0; secondIx--) {

      // Find all the "first" tuples that match; that is, all the spans that start exactly one token
      // before the second
      // span.
      int secondTok = keyToMSB(sortedSecondSlow[secondIx]);

      if (debug) {
        Log.debug("AdjacentJoin: Second ix %d --> token %d", secondIx, secondTok);
      }

      if (debug)
        Log.debug("0. secondTok=%d, firstTok=%d, firstIx=%d\n", secondTok, firstTok, firstIx);

      // First, "rewind" our cursor on the "first" tuples, in case it's positioned at the end of a
      // block of matching
      // tuples.
      // The "slow path" second spans all begin in the middle of a token, so all the token offsets
      // are reduced by 1
      // relative to the "fast path" code.
      // START HERE -----------------
      while (firstTok <= secondTok - minTokToCheck && firstIx < numFirst) {
        firstIx++;
        firstTok = computeFirstTok(firstKeys, firstIx);
        if (debug)
          Log.debug("1. secondTok=%d, firstTok=%d, firstIx=%d\n", secondTok, firstTok, firstIx);
      }

      // Now move backward until we find a matching region or go past where it would be.
      while (firstTok > secondTok - minTokToCheck && firstIx <= numFirst
          && firstIx >= numFirstSlow) {
        firstIx--;
        firstTok = computeFirstTok(firstKeys, firstIx);
        if (debug)
          Log.debug("2. secondTok=%d, firstTok=%d, firstIx=%d\n", secondTok, firstTok, firstIx);
      }

      // Then go backward through the region of "second" tuples that match.
      while (firstTok >= secondTok - maxTok && firstIx <= numFirst && firstIx >= numFirstSlow) {

        // Found a match; retrieve the original tuples and generate an output tuple.
        int firstTupIx = keyToLSB(firstKeys[firstIx]);
        int secondTupIx = keyToLSB(sortedSecondSlow[secondIx]);

        Tuple firstTup = firstTups.getElemAtIndex(firstTupIx);
        Tuple secondTup = secondTups.getElemAtIndex(secondTupIx);

        if (debug) {
          Log.debug("AdjacentJoin: Got a match - 1F/2S:\n"
              + "    first is tuple %d->%d with token %d: %s\n    second is tuple %d->%d with token %d: %s",
              firstIx, firstTupIx, firstTok, firstTup, //
              secondIx, secondTupIx, secondTok, secondTup);
        }

        genOutputTuple(firstTup, secondTup, mt);

        firstIx--;
        firstTok = computeFirstTok(firstKeys, firstIx);

        if (debug)
          Log.debug("3. secondTok=%d, firstTok=%d, firstIx=%d\n", secondTok, firstTok, firstIx);
      }
    }

  }

  /**
   * Create an output tuple and send it to our output.
   * 
   * @param firstTup input tuple from the "first" argument
   * @param secondTup input tuple from the "second" argument
   * @param mt outputs are stored here
   */
  void genOutputTuple(Tuple firstTup, Tuple secondTup, MemoizationTable mt) {
    Tuple tup = createOutputTup();
    outerCopier.copyVals(outerFirst ? firstTup : secondTup, tup);
    innerCopier.copyVals(outerFirst ? secondTup : firstTup, tup);
    addResultTup(tup, mt);
  }

  /**
   * Construct a key by combining the indicated lower- and higher-order bits.
   */
  long makeKey(int msBits, int lsBits) {
    long msBitsLong = msBits;
    long lsBitsLong = lsBits;
    return (msBitsLong << 32) | (lsBitsLong & 0x00000000ffffffffL);
  }

  /**
   * Pull out the most-significant bits of a packed key value.
   */
  int keyToMSB(long key) {
    return (int) (key >> 32);
  }

  /**
   * Pull out the least-significant bits of a packed key value.
   */
  int keyToLSB(long key) {
    return (int) (key & 0x00000000ffffffffL);
  }

  @Override
  protected AbstractTupleSchema createOutputSchema() {

    // Bind our accessor functions to the input schemas.
    try {
      AbstractTupleSchema outerSchema = outer().getOutputSchema();
      AbstractTupleSchema innerSchema = inner().getOutputSchema();

      outerArg.oldBind(outerSchema);
      innerArg.oldBind(innerSchema);

    } catch (FunctionCallValidationException e) {
      throw new RuntimeException("Error initializing AdjacentJoin schema", e);
    }

    // Defer to CartesianProduct for the actual schema creation.
    AbstractTupleSchema ret = super.createOutputSchema();

    return ret;
  }

  @Override
  protected boolean requiresLemmaInternal() {
    if (outerArg.requiresLemma())
      return true;
    if (innerArg.requiresLemma())
      return true;
    return false;
  }
}
