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
package com.ibm.avatar.algebra.function.base;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.Token;

/**
 * Base class for selection predicates that take a Span as input. All of these predicates must pass
 * the input Span as the last argument.
 */
public abstract class SpanSelectionPredicate extends SelectionPredicate {
  public static final String[] ARG_NAMES = {"s"};
  public static final FieldType[] ARG_TYPES = {FieldType.SPAN_TYPE};
  public static final String[] ARG_DESCRIPTIONS = {"a span"};

  protected SpanSelectionPredicate(Token origTok, AQLFunc[] args) {
    super(origTok, args);
  }

  /**
   * Function for computing our argument, given a tuple. The return type of this can be either
   * String or Span.
   */
  protected ScalarFunc arg;

  @Override
  protected Boolean matches(Tuple tuple, TupleList[] locatorArgs, MemoizationTable mt,
      Object[] evaluatedArgs) throws TextAnalyticsException {
    // the input span is assumed, by design, to always be the last argument
    Object o = evaluatedArgs[evaluatedArgs.length - 1];
    if (null == o) {
      return null;
    }

    return spanMatches(Span.convert(o), mt);
  }

  protected abstract boolean spanMatches(Span span, MemoizationTable mt);

}
