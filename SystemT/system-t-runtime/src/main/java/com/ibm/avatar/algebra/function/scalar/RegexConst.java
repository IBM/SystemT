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
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.algebra.function.predicate.ContainsRegex;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.aog.ConstFuncNode;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.RegexNode;
import com.ibm.systemt.regex.api.JavaRegex;
import com.ibm.systemt.regex.api.Regex;
import com.ibm.systemt.regex.api.SimpleRegex;

/**
 * Return a regular expression constant. Takes two arguments: The regular expression string and a
 * string indicating what kind of engine to use.
 */
public class RegexConst extends ScalarFunc {

  public static final String FNAME = "RegexConst";

  public static final String USAGE = String.format("Usage: %s(regex, engine_name)", FNAME);

  // public static final boolean ISCONST = true;

  /**
   * String that, when passed as the second argument to this function, indicates that the regex
   * should be evaluated with the SimpleRegex engine.
   */
  public static final String SIMPLE_REGEX_ENGINE_NAME = "SimpleRegex";
  public static final String JAVA_REGEX_ENGINE_NAME = "JavaRegex";

  // private RegexNode regexNode = null;

  /** The regular expression string itself, as a parse tree node. */
  private RegexNode regex;

  /** Name of the engine to use when compiling and running the regex. */
  private String engineName;

  /** Flags to use when compiling the regex. */
  private int flags = ContainsRegex.DEFAULT_REGEX_FLAGS;

  private Regex compiledRegex = null;

  protected static Regex strToRegex(String pattern, int flags, String engineName) {

    Regex ret;
    if (SIMPLE_REGEX_ENGINE_NAME.equals(engineName)) {
      if (false == SimpleRegex.isSupported(pattern, flags, true)) {
        // Compiler told us to use SimpleRegex for this expression, but
        // the engine doesn't support the expression.
        throw new RuntimeException(String.format(
            "Operator graph spec specifies SimpleRegex" + " engine for expression %s, "
                + "but the SimpleRegex engine " + "can't execute the regex; "
                + "this probably means there's a bug in the "
                + "AQL compiler's regex strength reduction",
            StringUtils.quoteStr('/', pattern, true, true)));
      }
      try {
        ret = new SimpleRegex(pattern, flags);
      } catch (com.ibm.systemt.regex.parse.ParseException e) {
        // We should never catch this exception!
        throw new RuntimeException(e);
      }

    } else if (JAVA_REGEX_ENGINE_NAME.equals(engineName)) {
      ret = new JavaRegex(pattern, flags);
    } else {
      throw new RuntimeException(String.format("Don't know about '%s' regex engine", engineName));
    }

    return ret;
  }

  /**
   * The constructor for this class should only be called from
   * {@link ConstFuncNode.Regex#Regex(RegexNode, String)} or from {@link RegexNode#asFunction()}
   */
  public RegexConst(RegexNode regexNode, String engineName) {
    super(null, null);

    regex = regexNode;
    this.engineName = engineName;

    // The actual compilation of the regex happens at bind time.
  }

  @Override
  protected void validateArgTypes(ArrayList<FieldType> argTypes)
      throws FunctionCallValidationException {
    // No validation beyond what happens in teh constructor.
  }

  /** Convenience constructor for the AOG parser. */
  // public RegexConst(RegexNode arg) {
  // super(null, null);
  // regexNode = arg;
  //
  // // Compile the regex once.
  // compiledRegex = strToRegex(regexNode.getRegexStr(),
  // ContainsRegex.DEFAULT_REGEX_FLAGS);
  // }
  /**
   * Set flags to be used when compiling the regex. This method must NOT be called after bind()!
   */
  public void setFlags(int flags) {
    if (null != compiledRegex) {
      throw new RuntimeException("Attempted to set flags after binding regex");
    }

    this.flags = flags;
  }

  @Override
  public void bindImpl(AbstractTupleSchema ts) {
    compiledRegex = strToRegex(regex.getRegexStr(), flags, engineName);
  }

  @Override
  public boolean isConst() {
    return true;
  }

  @Override
  public Object reallyEvaluate(Tuple t, TupleList[] locatorArgs, MemoizationTable mt,
      Object[] evaluatedArgs) throws TextAnalyticsException {
    return compiledRegex;
  }

  @Override
  public FieldType returnType() {
    return FieldType.REGEX_TYPE;
  }

}
