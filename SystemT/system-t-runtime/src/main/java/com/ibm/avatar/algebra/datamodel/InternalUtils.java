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

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.base.ProfileRecord;
import com.ibm.avatar.algebra.util.tokenize.BaseOffsetsList;
import com.ibm.avatar.algebra.util.tokenize.DerivedOffsetsList;
import com.ibm.avatar.algebra.util.tokenize.OffsetsList;
import com.ibm.avatar.algebra.util.tokenize.Tokenizer;
import com.ibm.avatar.logging.Log;

/**
 * Internal class to expose protected methods of this package to other internal APIs.
 * 
 */
public class InternalUtils {

  /**
   * Debug ?
   */
  private static final boolean debug = false;

  /**
   * Object used to mark tokenization overhead that this operator incurs.
   */
  private final ProfileRecord tokRecord = new ProfileRecord(Operator.TOKENIZATION_OVERHEAD_NAME, //
      /* TODO: How can we get a pointer to Operator here? */null);

  /**
   * Mark the singleton iterator of the input tuple list as unused, but don't clear the tuples in
   * the list. To clear the tuples, use {@link TupleList#clear()}.
   * 
   * @param tupleList
   */
  public void markDone(TupleList tupleList) {
    tupleList.done();
  }

  /**
   * @param s a span within the document
   * @return the appropriate token index to use as the "begin" of the indicated span. Returns
   *         {@link Span#NOT_A_TOKEN_OFFSET} if the span begins in the middle of a token; otherwise,
   *         returns the index of the first token after the beginning of the span.
   */
  public int getBeginTokIx(Span s, MemoizationTable mt) {
    // null spans have no tokens
    if (s == null) {
      return Span.NO_TOKENS;
    }

    Tokenizer t = mt.getTokenizer();

    // First check for a cached token offset. Note that we return the cached token only for spans
    // that truly begin on
    // token boundary. For those spans that are either 0-length spans, or begin in the middle of a
    // token, we recompute
    // the true begin token Ix because someone else ({@link Tokenizer#tokenizeStr()}) resets that
    // every time).
    int cachedVal = s.getBeginTok();
    if (Span.TOKEN_OFFSET_NOT_COMPUTED != cachedVal && Span.NOT_A_TOKEN_OFFSET != cachedVal
        && Span.NO_TOKENS != cachedVal) {
      return cachedVal;
    } else {
      if (debug) {
        Log.debug("getBeginTokIx(): Did NOT use a cached value for a span BEGIN token of span '%s'",
            s);
      }
    }

    // No cached value. Use the tokenizer to find the appropriate index.
    mt.profileEnter(tokRecord);
    OffsetsList toks = t.tokenize(s);

    int firstTokIx;
    if (toks instanceof DerivedOffsetsList) {
      // NORMAL CASE: Target is a span
      DerivedOffsetsList derivedToks = (DerivedOffsetsList) mt.getTokenizer().tokenize(s);
      firstTokIx = derivedToks.getFirstIx();
      if (debug) {
        Log.debug("getBeginTokIx(): Span %s is a span- Set firstTokIx = %d", s, firstTokIx);
      }
    } else if (toks instanceof BaseOffsetsList) {
      // Log.info("View '%s' does an AdjacentJoin"
      // + " with the doc text as an arg", getViewName());
      // SPECIAL CASE: Target is the entire doc (or field within doc) text
      firstTokIx = 0;
      // END SPECIAL CASE
      if (debug) {
        Log.debug("getBeginTokIx(): Span %s is a doc text- Set firstTokIx = %d", s, firstTokIx);
      }
    } else {
      throw new RuntimeException(
          String.format("Unknown offsets list class %s", toks.getClass().getName()));
    }

    OffsetsList textToks = t.tokenize(s.getDocTextObj());
    mt.profileLeave(tokRecord);

    if (Span.NO_TOKENS == firstTokIx) {
      // SPECIAL CASE: Zero tokens in this span. Fall back on a more
      // expensive method of finding the first token after the beginning
      // of the span
      if (debug) {
        Log.debug("getBeginTokIx(): Span %s has ZERO tokens. Returning nextBeginIx = %d", s,
            textToks.nextBeginIx(s.getBegin()));
      }
      return textToks.nextBeginIx(s.getBegin());
      // END SPECIAL CASE
    }

    int firstTokBegin = textToks.begin(firstTokIx);

    if (s.getBegin() > firstTokBegin) {
      if (debug) {
        Log.debug("getBeginTokIx(): Span %s - Span BEGIN > firstTokBegin. Returning %d", s,
            Span.NOT_A_TOKEN_OFFSET);
      }
      return Span.NOT_A_TOKEN_OFFSET;
    } else {
      if (debug) {
        Log.debug("getBeginTokIx(): Span %s - On Span BEGIN token boundary. Returning %d", s,
            firstTokIx);
      }
      return firstTokIx;
    }
  }

  /**
   * @param s a span within the document
   * @return the appropriate token index to use as the "end" of the indicated span. Returns
   *         {@link Span#NOT_A_TOKEN_OFFSET} if the span ends in the middle of a token; returns -1
   *         if the span ends before the start of the first token; otherwise, returns the index of
   *         the last token before the end of the span.
   */
  public int getEndTokIx(Span s, MemoizationTable mt) {

    // null spans have no tokens
    if (s == null) {
      return Span.NO_TOKENS;
    }

    // First check for a cached token offset. Note that we return the cached token only for spans
    // that truly end on
    // token boundary. For those spans that are either 0-length spans, or end in the middle of a
    // token, we recompute the
    // true end token Ix because someone else ({@link Tokenizer#tokenizeStr()}) resets that every
    // time).

    int cachedVal = s.getEndTok();
    if (Span.TOKEN_OFFSET_NOT_COMPUTED != cachedVal && Span.NOT_A_TOKEN_OFFSET != cachedVal
        && Span.NO_TOKENS != cachedVal) {
      return cachedVal;
    } else {
      if (debug) {
        Log.debug("getEndTokIx(): Did NOT use a cached value for a span END token of span '%s'", s);
      }
    }

    // No cached value. Use the tokenizer to find the appropriate index.
    mt.profileEnter(tokRecord);
    OffsetsList toks = mt.getTokenizer().tokenize(s);

    int lastTokIx;
    if (toks instanceof DerivedOffsetsList) {
      // NORMAL CASE: Target is a span
      DerivedOffsetsList derivedToks = (DerivedOffsetsList) mt.getTokenizer().tokenize(s);
      lastTokIx = derivedToks.getLastIx();
      if (debug) {
        Log.debug("getEndTokIx(): Span %s is a span- Set lastTokIx = %d", s, lastTokIx);
      }
    } else if (toks instanceof BaseOffsetsList) {
      // SPECIAL CASE: Target is the entire doc (or field within doc) text
      lastTokIx = toks.size();
      if (debug) {
        Log.debug("getEndTokIx(): Span %s is entire doc text- Set lastTokIx = %d", s, lastTokIx);
      }
      // END SPECIAL CASE
    } else {
      throw new RuntimeException(
          String.format("Unknown offsets list class %s", toks.getClass().getName()));
    }

    OffsetsList textToks = mt.getTokenizer().tokenize(s.getDocTextObj());
    mt.profileLeave(tokRecord);

    if (Span.NO_TOKENS == lastTokIx) {
      // SPECIAL CASE: Zero tokens in this span. Fall back on a more
      // expensive method of finding the last token before the end of the
      // span
      if (debug) {
        Log.debug("getEndTokIx(): Span %s has ZERO tokens. Returning prevBeginIx = %d", s,
            textToks.prevBeginIx(s.getEnd()));
      }
      return textToks.prevBeginIx(s.getEnd());
      // END SPECIAL CASE
    }

    int lastTokEnd = textToks.end(lastTokIx);

    if (s.getEnd() < lastTokEnd) {
      if (debug) {
        Log.debug("getEndTokIx(): Span %s - Span END < lastTokEnd. Returning %d", s,
            Span.NOT_A_TOKEN_OFFSET);
      }
      // Span ends in the middle of a token.
      return Span.NOT_A_TOKEN_OFFSET;
    } else {
      if (debug) {
        Log.debug("getEndTokIx(): Span %s - On Span END token boundary. Returning %d", s,
            lastTokIx);
      }
      return lastTokIx;
    }
  }
}
