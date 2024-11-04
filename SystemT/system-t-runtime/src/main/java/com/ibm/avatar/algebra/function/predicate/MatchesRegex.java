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
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.aql.Token;
import com.ibm.avatar.logging.Log;
import com.ibm.systemt.regex.api.RegexMatcher;

/**
 * Selection predicate that takes string as input and returns true if the string *exactly* matches a
 * regular expression.
 */
public class MatchesRegex extends ContainsRegex {

  public static final String FNAME = "MatchesRegex";

  public static final String USAGE = "Usage: " + FNAME + "(/regex/, ['flags'], span )";

  private static final boolean debug = false;

  public MatchesRegex(Token origTok, AQLFunc[] args) throws ParseException {
    super(origTok, args);
  }

  @Override
  protected boolean spanMatches(Span span, MemoizationTable mt) {
    String text = span.getText();
    RegexMatcher m = mt.getMatcher(regex);
    m.reset(text);

    if (debug) {
      Log.debug("MatchesRegex(/%s/, '%s')", regex.getExpr(), text);
    }

    // Check whether the first match covers the entire string.
    return m.matches();
  }
}
