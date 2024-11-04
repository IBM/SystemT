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

import java.util.HashMap;
import java.util.Map;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.Token;

/**
 * Special built-in scalar function for use in test cases. Sets an internal flag each time the
 * function is called. Test cases use this function to verify that a given part of an AQL view is
 * invoked at run time. Note that this class is <b>NOT</b> thread-safe and should not be used
 * outside of single-threaded test cases.
 */
public class FunctionInvocationTracker extends ScalarFunc {
  public static final String[] ARG_NAMES = {"view_name"};
  public static final FieldType[] ARG_TYPES = {FieldType.TEXT_TYPE};
  public static final String[] ARG_DESCRIPTIONS = {"name of the view containing the function call "
      + "(or another string to use to distinguish between expressions)"};

  public static Map<String, Integer> invocationTracker = new HashMap<String, Integer>();

  private String viewName = "";

  /**
   * Main constructor.
   * 
   * @param origTok AQL parser token for error handling
   * @param args one argument, the name of the view in which the function is located
   * @throws ParseException
   */
  public FunctionInvocationTracker(Token origTok, AQLFunc[] args) throws ParseException {
    super(origTok, args);
  }

  @Override
  public void bindImpl(AbstractTupleSchema ts) {
    viewName = Text.convertToString(getSFArg(0).evaluateConst());

    // initialize the invocation tracker for the input view
    if (false == invocationTracker.containsKey(viewName)) {
      invocationTracker.put(viewName, 0);
    }
  }

  @Override
  public FieldType returnType() {
    return FieldType.INT_TYPE;
  }

  @Override
  public Object reallyEvaluate(Tuple t, TupleList[] locatorArgs, MemoizationTable mt,
      Object[] evaluatedArgs) throws TextAnalyticsException {
    Integer invocationCount = invocationTracker.get(viewName);

    // increment the counter
    invocationCount = invocationCount + 1;

    // put it back into the tracker
    invocationTracker.put(viewName, invocationCount);

    // return the incremented invocation count
    return invocationCount;
  }


  public static int getInvocationCount(String view) {
    Integer count = invocationTracker.get(view);
    return (count == null ? 0 : count);
  }

}
