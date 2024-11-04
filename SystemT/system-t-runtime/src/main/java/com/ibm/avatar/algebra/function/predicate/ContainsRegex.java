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

import java.util.ArrayList;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.algebra.function.base.SpanSelectionPredicate;
import com.ibm.avatar.algebra.function.scalar.RegexConst;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.aql.Token;
import com.ibm.systemt.regex.api.Regex;
import com.ibm.systemt.regex.api.RegexMatcher;
import com.ibm.systemt.util.regex.FlagsString;

/**
 * Selection predicate that takes string as input and returns true if the string contains a match of
 * a regular expression. Arguments:
 * <ul>
 * <li>Regular expression
 * <li>Function/column ref that produces input annotations
 * </ul>
 */
public class ContainsRegex extends SpanSelectionPredicate {

  public static final String FNAME = "ContainsRegex";

  public static final String USAGE = "Usage: " + FNAME + "(/regex/, ['flags'], span )";

  /** Parse tree for the regex constant that is the main argument to this function. */
  private RegexConst regexFn;

  /**
   * The actual regular expression; initialized at bind time.
   */
  protected Regex regex;

  public static final int DEFAULT_REGEX_FLAGS = 0x0;

  public ContainsRegex(Token origTok, AQLFunc[] args) throws ParseException {
    super(origTok, args);

    // Parsing of the flags argument needs to happen before bind time, so process flags in the
    // constructor.

    // Last argument is the target of the predicate.
    this.arg = (ScalarFunc) args[args.length - 1];

    try {
      if (false == (args[0] instanceof RegexConst)) {
        throw new FunctionCallValidationException(this,
            "First argument must be a regular expression.");
      }
      regexFn = (RegexConst) args[0];

      String flagStr;
      if (3 == args.length) {
        // Flags string provided
        flagStr = Text.convertToString(((ScalarFunc) args[1]).evaluateConst());
      } else {
        // Use the default flags for "extract regex" unless instructed otherwise.
        flagStr = FlagsString.DEFAULT_FLAGS;
      }

      int flags = FlagsString.decode(this, flagStr);
      regexFn.setFlags(flags);
    } catch (FunctionCallValidationException e) {
      throw new ParseException(e.toString(), e);
    }

  }

  @Override
  protected void validateArgTypes(ArrayList<FieldType> argTypes)
      throws FunctionCallValidationException {
    if (2 == argTypes.size()) {
      // Optional argument left out
      if (!FieldType.REGEX_TYPE.equals(argTypes.get(0))) {
        throw new FunctionCallValidationException(this,
            "First argument returns %s instead of a regex", argTypes.get(0));
      }
      if (!FieldType.SPANTEXT_TYPE.accepts(argTypes.get(1))) {
        throw new FunctionCallValidationException(this,
            "Second argument returns %s instead of a span or text", argTypes.get(1));
      }
    } else if (3 == argTypes.size()) {

      if (!FieldType.REGEX_TYPE.equals(argTypes.get(0))) {
        throw new FunctionCallValidationException(this,
            "First argument returns %s instead of a regex", argTypes.get(0));
      }
      if (!argTypes.get(1).getIsText()) {
        throw new FunctionCallValidationException(this,
            "Second argument returns %s instead of a string", argTypes.get(1));
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
    regex = (Regex) regexFn.evaluateConst();
  }

  @Override
  protected boolean spanMatches(Span span, MemoizationTable mt) {
    RegexMatcher m = mt.getMatcher(regex);
    m.reset(span.getText());

    return m.find();
  }

}
