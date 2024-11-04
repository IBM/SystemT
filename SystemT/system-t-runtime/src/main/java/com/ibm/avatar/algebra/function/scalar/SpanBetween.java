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
 * Builds a new annotation whose span covers the span of text between two input spans. If the
 * IgnoreOrder flag is passed in as the first, optional, argument, order doesn't matter: the spans
 * will be sorted internally. If the flag is not passed in, then if the second argument comes before
 * the first, a zero-length span will be returned.
 * 
 */
public class SpanBetween extends ScalarFunc {
  public static final String FNAME = "SpanBetween";
  public static final String IGNORE_ORDER_FLAG = "IgnoreOrder";
  public static final String USAGE =
      "Usage: " + FNAME + "(['" + IGNORE_ORDER_FLAG + "',] span1, span2)";

  public static final boolean debug = false;

  /**
   * Constructor called from {@link com.ibm.avatar.algebra.function.ScalarFunc#buildFunc(String,
   * ArrayList).}
   * 
   * @throws ParseException
   */
  public SpanBetween(Token origTok, AQLFunc[] args) throws ParseException {
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
      if (FieldType.SPANTEXT_TYPE.accepts(argTypes.get(0)) == false) {
        throw new FunctionCallValidationException(this,
            "First argument returns %s instead of a span or text", argTypes.get(0));
      }
      if (FieldType.SPANTEXT_TYPE.accepts(argTypes.get(1)) == false) {
        throw new FunctionCallValidationException(this,
            "Second argument returns %s instead of a span or text", argTypes.get(1));
      }
    } else if (3 == argTypes.size()) {

      if ((argTypes.get(0).getIsText()) == false) {
        throw new FunctionCallValidationException(this,
            "First argument returns %s instead of a string", argTypes.get(0));
      }
      if (FieldType.SPANTEXT_TYPE.accepts(argTypes.get(1)) == false) {
        throw new FunctionCallValidationException(this,
            "Second argument returns %s instead of a span or text", argTypes.get(1));
      }
      if (FieldType.SPANTEXT_TYPE.accepts(argTypes.get(2)) == false) {
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

    int firstBegin = firstSpan.getBegin();
    int secondEnd = secondSpan.getEnd();

    // Ensure that spans are from the same document
    if (!firstSpan.hasSameDocText(secondSpan)) {
      return null;
    }

    int begin, end;

    // check if flag is set to handle spans in either order
    // and if spans are in reverse order
    if (ignoreArgOrder && (firstBegin > secondEnd)) {
      begin = secondEnd;
      end = firstBegin;
    } else {
      // we handle in strict order, with the first span being the earlier span.
      // The output span will always begin at the end of the first arg's span.
      begin = firstSpan.getEnd();

      // Make sure we return a zero-length string if the second argument
      // starts before the first ends.
      end = Math.max(begin, secondSpan.getBegin());
    }

    return Span.makeBaseSpan(firstSpan, begin, end);
  }
}
