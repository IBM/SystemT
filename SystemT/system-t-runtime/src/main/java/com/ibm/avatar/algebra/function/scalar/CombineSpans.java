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
package com.ibm.avatar.algebra.function.scalar;

import java.util.ArrayList;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.Token;
import com.ibm.avatar.logging.Log;

/**
 * Builds a new annotation whose span covers the span of two input annotations. The source of the
 * new annotation will be the base document. The order of the inputs doesn't matter if the first
 * argument is the optional flag 'IgnoreOrder', else they must be in left-to-right order. Arguments
 * are:
 * <ul>
 * <li>Function call that returns first input
 * <li>Function call that returns second input
 * </ul>
 * 
 */
public class CombineSpans extends ScalarFunc {

  public static final String FNAME = "CombineSpans";
  public static final String IGNORE_ORDER_FLAG = "IgnoreOrder";
  public static final String USAGE =
      "Usage: " + FNAME + "(['" + IGNORE_ORDER_FLAG + "',] span1, span2)";

  public static final boolean debug = false;

  /**
   * Constructor called from
   * {@link com.ibm.avatar.algebra.function.base.ScalarFunc#buildFunc(String, ArrayList) .}
   */
  public CombineSpans(Token origTok, AQLFunc[] args) throws ParseException {
    super(origTok, args);
  }

  @Override
  public FieldType returnType() {
    return FieldType.SPAN_TYPE;
  }

  @Override
  protected void validateArgTypes(ArrayList<FieldType> argTypes)
      throws FunctionCallValidationException {
    if (2 == argTypes.size()) {
      // Optional argument left out
      if ((!FieldType.SPANTEXT_TYPE.accepts(argTypes.get(0)))) {
        throw new FunctionCallValidationException(this,
            "First argument returns %s instead of a span or text", argTypes.get(0));
      }
      if ((!FieldType.SPANTEXT_TYPE.accepts(argTypes.get(1)))) {
        throw new FunctionCallValidationException(this,
            "Second argument returns %s instead of a span or text", argTypes.get(1));
      }
    } else if (3 == argTypes.size()) {

      if (!argTypes.get(0).getIsText()) {
        throw new FunctionCallValidationException(this,
            "First argument returns %s instead of a string", argTypes.get(0));
      }
      if (!FieldType.SPANTEXT_TYPE.accepts(argTypes.get(1))) {
        throw new FunctionCallValidationException(this,
            "Second argument returns %s instead of a span or text", argTypes.get(1));
      }
      if (!FieldType.SPANTEXT_TYPE.accepts(argTypes.get(2))) {
        throw new FunctionCallValidationException(this,
            "Third argument returns %s instead of a span or text", argTypes.get(2));
      }

    } else {
      throw new FunctionCallValidationException(this,
          "Wrong number of arguments (%d); should be 2 or 3", argTypes.size());
    }
  }

  @Override
  public void bindImpl(AbstractTupleSchema ts) throws FunctionCallValidationException {
    if (3 == args.length) {
      // Flags string provided
      String flagStr = Text.convertToString(getSFArg(0).evaluateConst());

      if (flagStr.matches(IGNORE_ORDER_FLAG)) {
        ignoreArgOrder = true;
      } else if ((null == flagStr) || (flagStr.length() == 0)) {
        ignoreArgOrder = false;
      } else {
        throw new FunctionCallValidationException(this, String.format(
            "Unrecognizable flag '%s', acceptable flags are: '%s'", flagStr, IGNORE_ORDER_FLAG));
      }
    }

  }

  @Override
  public Object reallyEvaluate(Tuple t, TupleList[] locatorArgs, MemoizationTable mt,
      Object[] evaluatedArgs) throws TextAnalyticsException {
    // Get spans with offsets on the base document.
    Span firstSpan = Span.convert(evaluatedArgs[args.length - 2]);
    Span secondSpan = Span.convert(evaluatedArgs[args.length - 1]);

    if (debug) {
      Log.debug(FNAME + ": arg0: " + args[0]);
      Log.debug(FNAME + ": arg1: " + args[1]);
      Log.debug(FNAME + ": First span: " + firstSpan + firstSpan.getDocText());
      Log.debug(FNAME + ": Second span: " + secondSpan + secondSpan.getDocText());
    }

    // Ensure that spans are from the same document
    if (!firstSpan.hasSameDocText(secondSpan)) {
      return null;
    }

    int firstBegin = firstSpan.getBegin();
    int firstEnd = firstSpan.getEnd();
    int secondBegin = secondSpan.getBegin();
    int secondEnd = secondSpan.getEnd();

    Span ret = null;

    // As per KC: The span with a smaller begin offset is sorted lower. Between two spans that start
    // on the same offset,
    // the one with the smaller end offset sorts lower.
    if (!((firstBegin < secondBegin) || ((firstBegin == secondBegin) && (firstEnd <= secondEnd)))) {
      // The spans are NOT in left-to-right order

      // Support agnostic ordering for tooling if flag is set
      if (ignoreArgOrder) {

        int begin = Math.min(firstBegin, secondBegin);
        int end = Math.max(firstEnd, secondEnd);

        // SPECIAL CASE 1: Bypass and return span 1 if span 2 is contained within span 1
        if (begin == firstBegin && end == firstEnd) {
          ret = firstSpan;
          // Propagate cached token offsets, if available.
          ret.setBeginTok(firstSpan.getBeginTok());
          ret.setEndTok(firstSpan.getEndTok());
        }
        // SPECIAL CASE 2: Bypass and return span 2 if span 1 is contained within span 2
        else if (begin == secondBegin && end == secondEnd) {
          ret = secondSpan;
          // Propagate cached token offsets, if available.
          ret.setBeginTok(secondSpan.getBeginTok());
          ret.setEndTok(secondSpan.getEndTok());
        } else {
          // Otherwise create a span using the computed begin, end values
          ret = Span.makeBaseSpan(firstSpan, begin, end);
          if (begin == firstBegin)
            ret.setBeginTok(firstSpan.getBeginTok());
          else
            ret.setBeginTok(secondSpan.getBeginTok());
          if (end == firstEnd)
            ret.setBeginTok(firstSpan.getEndTok());
          else
            ret.setBeginTok(secondSpan.getEndTok());
        }
      } else {
        // To make way for RSE, we no longer allow spans to occur in either
        // order unless specified explicitly by a flag.
        throw new RuntimeException(
            String.format("Arguments to CombineSpans must be in left-to-right order"
                + " (Input spans were %s and %s, respectively)", firstSpan, secondSpan));
      }
    }
    // SPECIAL CASE 3: Spans are in L-R order, such that span2 is contained within span 1, hence
    // return span 1
    else if (firstEnd >= secondEnd) {
      ret = firstSpan;
      // Propagate cached token offsets, if available.
      ret.setBeginTok(firstSpan.getBeginTok());
      ret.setEndTok(firstSpan.getEndTok());
    } else {
      ret = Span.makeBaseSpan(firstSpan, firstBegin, secondEnd);
      // Propagate cached token offsets, if available.
      ret.setBeginTok(firstSpan.getBeginTok());
      ret.setEndTok(secondSpan.getEndTok());
    }

    return ret;
  }
}
