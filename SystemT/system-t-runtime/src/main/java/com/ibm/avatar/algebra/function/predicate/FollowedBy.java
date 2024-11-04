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
package com.ibm.avatar.algebra.function.predicate;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.RSEBindings;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.Token;

/**
 * A version of {@link Follows} with the first and second arguments reversed. Used in conjunction
 * with RSE join to evaluate the *first* arg of a Follows predicate using restricted span
 * evaluation.
 * 
 */
public class FollowedBy extends RSEJoinPred {

  public static final String[] ARG_NAMES = {"span1", "span2", "minchar", "maxchar"};
  public static final FieldType[] ARG_TYPES =
      {FieldType.SPAN_TYPE, FieldType.SPAN_TYPE, FieldType.INT_TYPE, FieldType.INT_TYPE};
  public static final String[] ARG_DESCRIPTIONS =
      {"later span", "earlier span", "minimum character distance", "maximum character distance"};

  /**
   * Arguments that produce the input annotations
   */
  private ScalarFunc firstArg;

  /**
   * The minimum and maximum number of chars (inclusive) that we allow between the first and second
   * arguments.
   */
  private int minDist, maxDist;

  public FollowedBy(Token origTok, AQLFunc[] args) throws ParseException {
    super(origTok, args);
  }

  @Override
  public void bindImpl(AbstractTupleSchema ts) throws FunctionCallValidationException {
    firstArg = getSFArg(0);
    minDist = (Integer) getSFArg(2).evaluateConst();
    maxDist = (Integer) getSFArg(3).evaluateConst();
  }

  @Override
  protected Boolean matches(Tuple tuple, TupleList[] locatorArgs, MemoizationTable mt,
      Object[] evaluatedArgs) throws TextAnalyticsException {
    // Marshal arguments.
    Span firstSpan = Span.convert(evaluatedArgs[0]);
    Span secondSpan = Span.convert(evaluatedArgs[1]);

    if (!firstSpan.hasSameDocText(secondSpan)) {
      return false;
    }

    if (firstSpan.getBegin() < secondSpan.getEnd()) {
      // Spans are in the wrong order.
      return Boolean.FALSE;
    }

    int dist = firstSpan.getBegin() - secondSpan.getEnd();

    if (dist >= minDist && dist <= maxDist) {
      return Boolean.TRUE;
    } else {
      return Boolean.FALSE;
    }

  }

  @Override
  public RSEBindings getBindings(Tuple outerTup, MemoizationTable mt)
      throws TextAnalyticsException {

    // The outer tuple schema is always a prefix of the join tuple schema,
    // so we can reuse the accessors for the join tuples in this context.
    Span outerSpan = (Span) firstArg.oldEvaluate(outerTup, mt);

    if (outerSpan == null)
      return null;

    RSEBindings ret = new RSEBindings();

    // The inner span can *end* anywhere from minDist to maxDist characters
    // *before* the outer span.
    ret.type = RSEBindings.ENDS_AT;
    ret.begin = Math.max(0, outerSpan.getBegin() - maxDist);
    ret.end = Math.max(0, outerSpan.getBegin() - minDist);

    return ret;
  }

}
