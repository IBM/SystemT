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
import java.util.Arrays;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.base.MultiOutputOperator;
import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldCopier;
import com.ibm.avatar.algebra.datamodel.FieldSetter;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.SpanGetter;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.algebra.util.tokenize.OffsetsList;
import com.ibm.avatar.logging.Log;
import com.ibm.systemt.regex.api.SimpleRegex;
import com.ibm.systemt.regex.api.TokRegexMatcher;
import com.ibm.systemt.regex.parse.ParseException;
import com.ibm.systemt.util.regex.RegexesTokParams;

/**
 * SRM regex extractor; runs a number of regular expressions simultaneously.
 */
public class RegexesTok extends MultiOutputOperator {

  /**
   * String used to identify the operation of this operator in profiles.
   */
  public static final String REGEXESTOK_PERF_COUNTER_NAME = "Shared Regex Matching";

  /** The class that does the heavy lifting. */
  private final SimpleRegex regexes;

  /** The original expressions, for debugging purposes. */
  private final String[] regexStrs;

  // private SimpleRegexMatcher matcher;

  /** How long (in tokens) the matches should be for each of the inputs. */
  private final int[] maxToks;

  /**
   * Minimum length (in tokens) that matches should be for each of the inputs.
   */
  private final int[] minToks;

  /**
   * Maximum number of tokens for *any* regex that we do; used to determine the matching region.
   */
  private int globalMaxTok;

  /** Name of the field from which our annotation sources come. */
  private final String col;

  /** Names of the fields where we write matches, one per regex. */
  private final String[] outputCols;

  /** Accessor for getting at the input field of the interior tuples. */
  private SpanGetter inputAcc;

  /** Accessors for setting the output fields of our interior output tuples. */
  private final FieldSetter<Span>[] outputAcc;

  /**
   * Accessor for copying the unchanged attributes from the input tuples to the output tuples.
   */
  private FieldCopier copier;

  @SuppressWarnings("unchecked")
  public RegexesTok(Operator input, String col, ArrayList<RegexesTokParams> regexParams)
      throws ParseException {
    super(input, regexParams.size());

    // Convert the parameters into our internal arrays.
    regexStrs = new String[regexParams.size()];
    int[] flags = new int[regexParams.size()];
    outputCols = new String[regexParams.size()];
    minToks = new int[regexParams.size()];
    maxToks = new int[regexParams.size()];

    for (int i = 0; i < regexParams.size(); i++) {
      RegexesTokParams p = regexParams.get(i);
      regexStrs[i] = p.getRegexStr();
      flags[i] = p.getFlags();
      outputCols[i] = p.getOutputColName();
      minToks[i] = p.getMinTok();
      maxToks[i] = p.getMaxTok();
    }

    regexes = new SimpleRegex(regexStrs, flags);
    // matcher = (SimpleRegexMatcher) regexes.matcher("");

    this.col = col;

    // Find the global maximum number of tokens.
    globalMaxTok = 0;
    for (int i = 0; i < maxToks.length; i++) {
      globalMaxTok = Math.max(globalMaxTok, maxToks[i]);
    }

    outputAcc = new FieldSetter[regexParams.size()];

    // Make sure that this operator's time gets charged to
    // "Shared Regex Matching".
    super.profRecord.viewName = REGEXESTOK_PERF_COUNTER_NAME;
  }

  @Override
  protected AbstractTupleSchema createOutputSchema(int ix) {
    AbstractTupleSchema inputSchema = child.getOutputSchema();

    // We pass through all the columns of the input schema, as they may
    // be needed by the EXTRACT list of the enclosing extract statement.
    // We also append the output column.
    AbstractTupleSchema outputSchema =
        new TupleSchema(inputSchema, outputCols[ix], FieldType.SPAN_TYPE);

    // Set up accessors for getting at the elements of the schemas.
    inputAcc = inputSchema.asSpanAcc(col);
    copier = outputSchema.fieldCopier(inputSchema);

    outputAcc[ix] = outputSchema.spanSetter(outputCols[ix]);

    return outputSchema;
  }

  /**
   * The advanceAll method finds all the regex matches for the next set of tuples.
   */
  @Override
  protected void advanceAllInternal(MemoizationTable mt, TupleList childTups,
      TupleList[] outputLists) throws Exception {
    boolean debug = false;

    // Set up the matching machinery; the matcher itself should be shared by
    // all instances of this operator.
    TokRegexMatcher matcher = mt.getTokenMatcher();
    matcher.setTokenRanges(minToks, maxToks);

    if (debug) {
      Log.debug("Got tuples: " + childTups);
    }

    TLIter itr = childTups.iterator();

    while (itr.hasNext()) {
      // Grab the next interior input tuple from the top-level tuple's
      // set-valued attribute.
      Tuple interiorInTup = itr.next();

      Span src = inputAcc.getVal(interiorInTup);
      if (src == null)
        continue;
      String targetText = src.getText();

      // Reuse cached tokens if possible.
      mt.profileEnter(tokRecord);
      OffsetsList tokens = mt.getTokenizer().tokenize(src);
      mt.profileLeave(tokRecord);

      for (int tokix = 0; tokix < tokens.size(); tokix++) {

        // The SimpleRegex engine does most of the heavy lifting for us;
        // we just need to collate its results.
        int[] matchLens = matcher.findTokMatches(regexes, targetText, tokens, tokix);

        for (int i = 0; i < matchLens.length; i++) {
          if (-1 != matchLens[i]) {
            int begin = tokens.begin(tokix);
            int end = begin + matchLens[i];

            if (debug) {
              Log.debug("Match of regex %d ( /%s/ ) from %d to %d", i,
                  StringUtils.shorten(regexStrs[i], 30, true), begin, end);
            }

            Span result = Span.makeSubSpan(src, begin, end);

            // // Add token offsets, but only if the source is base
            // // text.
            // if (src instanceof Text) {
            // result.setBeginTok (tokix);
            // int lastTokIx = (tokens).nextBeginIx (end) - 1;
            // result.setEndTok (lastTokIx);
            // }

            // Build up an interior tuple to send to the appropriate
            // output of the operator.
            Tuple outTup = createOutputTup(i);
            copier.copyVals(interiorInTup, outTup);

            outputAcc[i].setVal(outTup, result);
            outputLists[i].add(outTup);
          }
        }
      }

    }

  }

  @Override
  public String toString() {
    return Arrays.toString(regexStrs);
  }
}
