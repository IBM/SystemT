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
package com.ibm.avatar.algebra.util.tokenize;

import java.util.TreeSet;

import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.SpanText;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.util.lang.LangCode;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.logging.Log;

/**
 * Abstract superclass for internal tokenizer implementations. Provides common functionality for
 * handling token caching.
 */
public abstract class Tokenizer {

  /**
   * Tokenize a text or span object, using cached tokens if possible. Results are cached as an
   * attribute of the span.
   * 
   * @param inputObj the input obj whose text we want to tokenize.
   * @return the tokens within the indicated span
   */
  public final OffsetsList tokenize(SpanText inputObj) {

    if (inputObj instanceof Span) {
      return tokenize((Span) inputObj);
    } else if (inputObj instanceof Text) {
      return tokenize((Text) inputObj);
    } else {
      throw new RuntimeException("This code should not be reached");
    }
  }

  /**
   * Tokenize the text of a span, using cached tokens if possible. Results are cached as an
   * attribute of the span.
   * 
   * @param span the span whose text we want to tokenize.
   * @return the tokens within the indicated span
   */
  public final OffsetsList tokenize(Text text) {

    BaseOffsetsList tokens = (BaseOffsetsList) text.getCachedTokens();

    if (null != tokens) {
      return tokens;
    }

    String textStr = text.getText();
    tokens = new BaseOffsetsList();
    tokenizeStr(textStr, text.getLanguage(), tokens);
    text.setCachedTokens(tokens);

    return tokens;

  }

  /**
   * Tokenize the text of a span, using cached tokens if possible. Results are cached as an
   * attribute of the span.
   * 
   * @param span the span whose text we want to tokenize.
   * @return the tokens within the indicated span
   */
  public final OffsetsList tokenize(Span span) {

    if (null != span.getCachedTokens()) {
      // Use cached tokens if possible.
      return span.getCachedTokens();
    } else {
      // No cached tokens; create some.

      // Span over base text; make sure the text has been tokenized,
      // then generate a "virtual" token list from the base text's
      // tokens.
      Text docText = span.getDocTextObj();
      tokenize(docText);

      BaseOffsetsList docTokens = (BaseOffsetsList) docText.getCachedTokens();
      DerivedOffsetsList tokens =
          new DerivedOffsetsList(docTokens, span.getBegin(), span.getEnd(), span.getBegin());

      // Set begin and end token indices if the span begins and ends
      // on a token boundary in the base text.
      // if (tokens.isOnTokenBoundaries ()) {
      // span.setBeginTok (tokens.getFirstIx ());
      // span.setEndTok (tokens.getLastIx ());
      // }
      if (tokens.beginsOnTokenBoundary()) {
        span.setBeginTok(tokens.getFirstIx());
      } else {
        span.setBeginTok(Span.NOT_A_TOKEN_OFFSET);
      }

      if (tokens.endsOnTokenBoundary()) {
        span.setEndTok(tokens.getLastIx());
      } else {
        span.setEndTok(Span.NOT_A_TOKEN_OFFSET);
      }

      span.setCachedTokens(tokens);
      return tokens;
    }
  }

  /**
   * Return a subrange of the tokens for the text starting at the indicated location
   * 
   * @param text the text we want to tokenize.
   * @param startOff offset into {@code str} at which to start tokenization
   * @param maxTok maximum number of tokens to extract before we stop tokenization
   * @param buf where the offsets will go
   */
  public final void tokenize(Text text, int startOff, int maxTok, DerivedOffsetsList buf) {

    // Start out by tokenizing the text under the span, if it hasn't been
    // tokenized already.
    tokenize(text);

    // BaseOffsetsList textToks = (BaseOffsetsList) text.getCachedTokens();
    BaseOffsetsList textToks = (BaseOffsetsList) tokenize(text);

    // Convert the offset into the span into an offset into the original
    // text.
    int textStartOff = startOff;

    int textStartTok = textToks.nextBeginIx(textStartOff);

    int textEndOff;

    if (0 == textToks.size()) {
      // SPECIAL CASE: No tokens in the text.
      // Log.debug("%s has zero tokens.", text.toString());

      textEndOff = textStartOff;
      // END SPECIAL CASE
    } else {
      // Normal case -- the document contains at least one token.

      // Determine the rightmost token that is in the text.
      int textEndTok = Math.min(textStartTok + maxTok, textToks.size() - 1);

      // Use whichever is smaller: The end of the target span, or the end
      // of the rightmost token in the original text.
      textEndOff = Math.min(textToks.end(textEndTok), text.getLength());
    }

    // return new DerivedOffsetsList(textToks, textStartOff, textEndOff,
    // span
    // .getBegin());
    buf.init(textToks, textStartOff, textEndOff, 0);
  }

  /**
   * Tokenize a text in reverse, starting from the indicated position. The tokens extracted are
   * returned in the <b>same</b> order they would be returned from a forwards tokenization.
   * 
   * @param text the text to tokenize
   * @param endOff where to start tokenizing (going backwards through the string); offset within the
   *        span
   * @param maxtok maximimum number of tokens to extract before we stop tokenizing
   * @param buf where the offsets will go
   */
  public void tokenizeBackwards(Text text, int endOff, int maxtok, DerivedOffsetsList buf) {

    boolean debug = false;

    if (endOff > text.getLength()) {
      throw new ArrayIndexOutOfBoundsException(
          String.format("Span index %d out of range (max %d)", endOff, text.getLength()));
    }

    // Start out by tokenizing the text, if it hasn't been
    // tokenized already.
    tokenize(text);

    BaseOffsetsList textToks = (BaseOffsetsList) text.getCachedTokens();

    // Convert the offset into the span into an offset into the original
    // text.
    int textEndOff = endOff;

    int textEndTok = textToks.prevBeginIx(textEndOff);

    // Determine the leftmost token in our requested range that is still
    // inside the text.
    int textStartTok = Math.max(0, textEndTok - maxtok + 1);

    // Use whichever is larger: The start of the target span, or the start
    // of the first token in the original text that falls within the
    // requested range.
    int textStartOff = Math.max(textToks.begin(textStartTok), 0);

    if (debug) {
      Log.debug(
          "tokenizeBackwards(%d): Creating DerivedOffsetsList"
              + " from %d (token %d) to %d (token %d)",
          maxtok, textStartOff, textStartTok, textEndOff, textEndTok);
      Log.debug("tokenizeBackwards(): Target text is: '%s'",
          StringUtils.escapeForPrinting(text.getText().subSequence(textStartOff, textEndOff)));
    }

    // return new DerivedOffsetsList(textToks, textStartOff, textEndOff,
    // span.getBegin());
    buf.init(textToks, textStartOff, textEndOff, 0);
  }

  /*
   * ABSTRACT METHODS Tokenizer implementations need to provide implementations of these methods.
   */

  /**
   * Tokenize a string, according to whatever tokenization method the implementation class is
   * currently configured to use. Results are put into {@link BaseOffsetsList}
   * 
   * @param str the string to be tokenized
   * @param language language of the text in the string; determines the tokenization used in some
   *        tokenizers
   * @param output buffer to hold the results of tokenization
   */
  public abstract void tokenizeStr(CharSequence str, LangCode language, BaseOffsetsList output);

  /**
   * @return true if this tokenizer implementation can tag tokens with a particular part of speech,
   *         false otherwise.
   */
  public abstract boolean supportsPOSTagging();

  /**
   * Decode a part of speech specification string into a list of integer part of speech tags that
   * this tagger uses to represent the different parts of speech in an OffsetsList data structure.
   * Also ensures that the indicated tags will be properly decoded, or throws an exception if they
   * are impossible to decode.
   * 
   * @param posSpec a part of speech specification string, as in the AQL extract statement
   * @return a set of internal part of speech codes
   */
  public abstract TreeSet<Integer> decodePOSSpec(String posSpec, LangCode language);

  /**
   * @param code internal integer code that this tagger uses to represent a part of speech inside an
   *        OffsetsList
   * @return human-readable string equivalent of this code
   */
  public abstract CharSequence posCodeToString(int code, LangCode language);

  /**
   * @return true if this tokenizer implementation can produce lemma, false otherwise.
   */
  public abstract boolean supportLemmatization();

  /**
   * Returns the name of this tokenizer; used to persist the tokenizer inside compiled AQL module
   * artifacts (metadata and compiled dictionaries).
   * 
   * @return the name of the tokenizer
   */
  public abstract String getName();

}
