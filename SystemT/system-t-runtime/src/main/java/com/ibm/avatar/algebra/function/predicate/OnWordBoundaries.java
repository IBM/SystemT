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
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.algebra.function.base.SelectionPredicate;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.Token;

/**
 * Selection predicate that takes a Span and returns true if the span's beginning and end are at
 * word boundaries (or the very beginning or end) in the Span's source.
 * 
 * @deprecated use matching primitives on token boundaries instead
 */
@Deprecated
public class OnWordBoundaries extends SelectionPredicate {

  public static final String[] ARG_NAMES = {"span"};
  public static final FieldType[] ARG_TYPES = {FieldType.SPAN_TYPE};
  public static final String[] ARG_DESCRIPTIONS = {"span to check"};

  public OnWordBoundaries(Token origTok, AQLFunc[] args) throws ParseException {
    super(origTok, args);

  }

  @Override
  protected Boolean matches(Tuple tuple, TupleList[] locatorArgs, MemoizationTable mt,
      Object[] evaluatedArgs) throws TextAnalyticsException {
    // Span span = path.evaluate(tuple);
    Span span = Span.convert(evaluatedArgs[0]);
    String parentText = span.getText();

    // First check the left-hand side of the span.
    if (span.getBegin() > 0 && isWordChar(parentText.charAt(span.getBegin() - 1))) {
      return false;
    }

    // Then check the right-hand side.
    if (span.getEnd() < parentText.length() && isWordChar(parentText.charAt(span.getEnd()))) {
      return false;
    }

    // If we get here, we passed both checks.
    return true;
  }

  private static final boolean isWordChar(char c) {

    // lower-case alphabet?
    if (c >= 'a' && c <= 'z') {
      return true;
    }

    // upper-case alphabet?
    if (c >= 'A' && c <= 'Z') {
      return true;
    }

    // number?
    if (c >= '0' && c <= '9') {
      return true;
    }

    // otherwise assum it's not a word character.
    return false;
  }

}
