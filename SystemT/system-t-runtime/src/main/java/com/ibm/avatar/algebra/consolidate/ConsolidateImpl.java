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
package com.ibm.avatar.algebra.consolidate;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.FunctionCallInitializationException;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;

/**
 * Internal implementation of a Consolidate operator. This abstract base class hides the mechanics
 * of consolidation from the high-level operator. Class also includes factory methods for
 * instantiating the appropriate implementation of consolidation.
 */

public abstract class ConsolidateImpl {

  /**
   * Function call that, when fed a tuple, returns the span used for consolidation
   */
  protected ScalarFunc spanGetter;
  protected ScalarFunc fieldGetter;
  protected String priorityDirection;

  protected void setSpanGetter(ScalarFunc spanGetter) {
    this.spanGetter = spanGetter;
  }

  protected void setPriorityFieldGetter(ScalarFunc fieldGetter) {
    this.fieldGetter = fieldGetter;
  }

  protected void setPriorityDirection(String priorityDirection) {
    this.priorityDirection = priorityDirection;
  }

  public static final String ASCENDING = "ascending";
  public static final String DESCENDING = "descending";
  public static final String CONTAINEDWITHIN = "ContainedWithin";
  public static final String NOTCONTAINEDWITHIN = "NotContainedWithin";
  public static final String LEFTTORIGHT = "LeftToRight";
  public static final String RETAINFIRST = "RetainFirst";
  public static final String RETAINLAST = "RetainLast";
  public static final String EXACTMATCH = "ExactMatch";
  // New name constants for left-to-right consolidations go here.

  /** Default choice of consolidation if the user doesn't specify. */
  public static final String DEFAULT_CONSOLIDATION_TYPE = CONTAINEDWITHIN;
  public static final String EFAULT_CONSOLIDATION_PRIORITY = ASCENDING;

  public static ConsolidateImpl makeImpl(String name, ScalarFunc spanGetter,
      ScalarFunc pFieldGetter, String priorityDirection) {

    // check for errors during validation: priority policy must be present only with LeftToRight
    // consolidation.
    // cannot check for the type column of pFieldGeter because it is not binded yet.

    if (priorityDirection != null) {
      if (!LEFTTORIGHT.equals(name)) {
        throw new IllegalArgumentException(String.format(
            "Invalid usage of 'with priority from' with '%s' consolidation policy. The 'with priority from' clause can only be used with the '%s' consolidation policy.",
            name, LEFTTORIGHT));
      }

    }

    ConsolidateImpl ret = null;

    // Try for a sorting consolidation type first -- they're faster.
    if (LEFTTORIGHT.equals(name)) {
      ret = new RegexConsImpl();
    } else if (CONTAINEDWITHIN.equals(name)) {
      ret = new ContainsConsImpl();
    } else if (NOTCONTAINEDWITHIN.equals(name)) {
      ret = new NotContainedConsImpl();
    } else if (RETAINFIRST.equals(name)) {
      ret = new RetainFirstConsImpl();
    } else if (RETAINLAST.equals(name)) {
      ret = new RetainLastConsImpl();
    } else if (EXACTMATCH.equals(name)) {
      ret = new ExactMatchConsImpl();
    }
    // String comparisons for new consolidation types go here
    else {
      // Didn't find a sorting consolidation type; check for partial order
      // consolidation types.
      for (String orderName : PartialOrder.ORDER_NAMES) {
        if (orderName.equals(name)) {
          ret = new PartialOrderConsImpl(name);
        }
      }
    }

    if (null == ret) {
      throw new IllegalArgumentException("Invalid consolidation type '" + name + "'");
    }

    // Put the function in place.
    ret.setSpanGetter(spanGetter);
    ret.setPriorityFieldGetter(pFieldGetter);
    ret.setPriorityDirection(priorityDirection);

    return ret;
  }

  public void bind(AbstractTupleSchema inputSchema) throws ParseException {
    try {
      // Perform both of the runtime steps of function initialization.
      spanGetter.oldBind(inputSchema);

      // bind fieldGetter and validate its type. It must be Text, String, Float, Integer or Null
      if (fieldGetter != null) {
        fieldGetter.oldBind(inputSchema);
        FieldType ftype = fieldGetter.returnType();
        if (!ftype.getIsText() && !ftype.getIsNumericType() && !ftype.getIsNullType())
          throw new IllegalArgumentException("Invalid priority type '" + ftype + "'");

      }
    } catch (FunctionCallValidationException e) {
      // Top-level API still expects ParseException. For now, just wrap the
      // FunctionCallValidationException.
      throw new ParseException("Error during bind", e);
    }
  }

  /**
   * Initialize any function state that may need to be kept across calls.
   * 
   * @param mt global per-thread table of information that is kept across calls
   */
  public final void initState(MemoizationTable mt) throws FunctionCallInitializationException {
    spanGetter.initState(mt);
    if (null != fieldGetter) {
      fieldGetter.initState(mt);
    }
  }

  protected boolean requiresLemma() {
    if (spanGetter.requiresLemma())
      return true;
    if (null != fieldGetter && fieldGetter.requiresLemma())
      return true;
    return false;
  }

  /**
   * Override this method to perform the actual consolidation operation.
   * 
   * @param input the input tuple set
   * @param mt pointer to data structure holding thread-local data
   * @return input with redundant spans removed; does not modify input
   * @throws TextAnalyticsException
   */
  public abstract TupleList consolidate(TupleList input, MemoizationTable mt)
      throws TextAnalyticsException;

}
