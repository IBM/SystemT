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

import java.util.HashSet;

import com.ibm.avatar.algebra.base.ExtractionOp;
import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.util.lang.LangCode;
import com.ibm.avatar.algebra.util.tokenize.OffsetsList;
import com.ibm.avatar.algebra.util.tokenize.StandardTokenizer;
import com.ibm.avatar.algebra.util.tokenize.Tokenizer;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.logging.Log;

public class PartOfSpeech extends ExtractionOp {

  /** Language for which this operator performs tagging. */
  private LangCode language;

  /**
   * Comma-delimited string describing the different parts of speech this operator will extract.
   */
  private String posStr;

  /**
   * Set of integer part of speech codes for which we return a span.
   */
  private HashSet<Integer> posCodes = new HashSet<Integer>();

  /**
   * Main constructor.
   * 
   * @param inCol name of input column (column containing text to tag)
   * @param langStr language code for the language that we're doing part of speech tagging for
   * @param posStr comma-delimited list of part of speech tags
   * @param outCol
   * @param child
   */
  public PartOfSpeech(String inCol, String langStr, String posStr, String outCol, Operator child) {
    super(inCol, outCol, child);

    language = LangCode.strToLangCode(langStr);

    // We decode the part of speech string later, when we have a
    // MemoizationTable available
    this.posStr = posStr;
  }

  /**
   * We override this method to initialize the parts of speech set, in addition to whatever
   * processing the superclass is already doing.
   */
  @Override
  protected void initStateInternal(MemoizationTable mt) throws TextAnalyticsException {
    final boolean debug = false;

    super.initStateInternal(mt);

    Tokenizer t = mt.getTokenizer();

    if (false == t.supportsPOSTagging()) {
      // Generate an error message that tells what tokenizer
      // implementation is in use and how to fix the problem.
      String tokenizerName = null;

      if (t instanceof StandardTokenizer) {
        tokenizerName = "Standard tokenizer";
      } else {
        tokenizerName = String.format("current %s tokenizer configuration", t.getClass().getName());
      }

      // (new Throwable()).printStackTrace();
      // System.err.print("***************************\n");

      throw new RuntimeException(String.format(
          "The %s does not support "
              + "part of speech tagging. Use the Multilingual tokenizer and part of speech tagger, "
              + "or another compatible tokenizer that supports part of speech tagging instead.",
          tokenizerName));
    }

    if (debug) {
      Log.debug("Initializing POS codes for tokenizer %s", t);
    }

    // Turn the part of speech string into a set of internal integer part of
    // speech codes.
    posCodes.addAll(t.decodePOSSpec(posStr, language));

    if (debug) {
      Log.debug("POS codes are now: %s", posCodes);
    }
  }

  @Override
  protected void extract(MemoizationTable mt, Tuple inputTup, Span inputSpan) throws Exception {

    final boolean debug = false;

    // inputSpan should not be null here

    if (false == language.equals(inputSpan.getLanguage())) {
      // Wrong language --> No parts of speech returned
      if (debug) {
        Log.debug("%s: Wrong language " + "--> No parts of speech returned", this);
      }
      return;
    }

    // Part of speech information is piggybacked on the tokens and is
    // produced by the tokenizer; charge the overhead to the tokenizer;
    // The AQL Profiler output will show Tokenization and POS overhead as a single entry
    mt.profileEnter(tokRecord);
    OffsetsList toks = mt.getTokenizer().tokenize(inputSpan);
    mt.profileLeave(tokRecord);

    // Iterate through the tokens, inspecting their POS information.
    for (int i = 0; i < toks.size(); i++) {
      int posCode = toks.index(i);

      if (debug) {
        Log.debug("%s: POS code for token %d is %d", this, i, posCode);
      }

      if (posCodes.contains(posCode)) {
        // This token is of one of the parts of speech that we are
        // outputting.
        addResultAnnot(inputTup, toks.begin(i), toks.end(i), inputSpan, mt);
      }
    }

  }

  @Override
  protected boolean requiresPartOfSpeechInternal() {
    return true;
  }

}
