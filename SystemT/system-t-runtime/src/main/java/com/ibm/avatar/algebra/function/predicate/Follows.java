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
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.Token;
import com.ibm.avatar.logging.Log;

/**
 * Selection predicate that returns true if one argument follows the other argument by a certain
 * number of characters. In particular, the predicate takes four parameters:
 * <ul>
 * <li>Two column indexes; the indicated columns must contain Annotation objects
 * <li>A minimum and maximum number of characters, INCLUSIVE
 * </ul>
 * 
 */
public class Follows extends RSEJoinPred {
  public static final String[] ARG_NAMES = {"span1", "span2", "minchar", "maxchar"};
  public static final FieldType[] ARG_TYPES =
      {FieldType.SPAN_TYPE, FieldType.SPAN_TYPE, FieldType.INT_TYPE, FieldType.INT_TYPE};
  public static final String[] ARG_DESCRIPTIONS =
      {"earlier span", "later span", "minimum character distance", "maximum character distance"};

  /**
   * Arguments that produce the input annotations
   */
  private ScalarFunc firstArg;

  /**
   * The minimum and maximum number of chars (inclusive) that we allow between the first and second
   * arguments.
   */
  private int minDist, maxDist;
  private final boolean debug = false;

  public Follows(Token origTok, AQLFunc[] args) throws ParseException {
    super(origTok, args);
  }

  @Override
  public void bindImpl(AbstractTupleSchema ts) {
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

    if (debug)
      Log.debug("Follows(%d,%d); first = %s; second = %s", minDist, maxDist, firstSpan, secondSpan);

    if (!firstSpan.hasSameDocText(secondSpan)) {
      return false;
    }

    if (secondSpan.getBegin() < firstSpan.getEnd()) {
      // Spans are in the wrong order.
      return Boolean.FALSE;
    }

    int dist = secondSpan.getBegin() - firstSpan.getEnd();

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

    int textLen = outerSpan.getDocText().length();

    RSEBindings ret = new RSEBindings();

    // The inner span can start anywhere from minDist to maxDist
    // characters from the outer span
    ret.type = RSEBindings.STARTS_AT;
    ret.begin = Math.min(textLen, outerSpan.getEnd() + minDist);
    ret.end = Math.min(textLen, outerSpan.getEnd() + maxDist);

    return ret;
  }

}
