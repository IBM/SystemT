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

import java.util.Set;
import java.util.TreeSet;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.extract.ApplyScalarFunc;
import com.ibm.avatar.algebra.function.predicate.And;
import com.ibm.avatar.algebra.function.predicate.Or;
import com.ibm.avatar.algebra.function.scalar.Case;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.ColNameNode;
import com.ibm.avatar.aql.ScalarFnCallNode;
import com.ibm.avatar.aql.Token;

/**
 * Base class for a scalar function for use in a selection predicate or in the
 * {@link ApplyScalarFunc} operator.
 */
public abstract class ScalarFunc extends ScalarReturningFunc {

  private final TupleList[] EMPTY_ARRAY = {};

  /**
   * Main constructor, mainly present to standardize the inputs to the constructors of child
   * classes.
   * 
   * @param origTok parser token for the function name.
   * @param args the list of arguments to the function in the original statement.
   */
  protected ScalarFunc(Token origTok, AQLFunc[] args) {
    super(origTok, args);
  }

  /**
   * Convenience version of {@link #reallyEvaluate(Tuple, TupleList[], MemoizationTable, Object[])}
   * for callers that don't yet support record locator arguments. Should be removed when these
   * callers no longer exist.
   */
  public Object oldEvaluate(Tuple t, MemoizationTable mt) throws TextAnalyticsException {
    return evaluate(t, EMPTY_ARRAY, mt);
  }

  /**
   * Preliminary argument evaluation before full function evaluation <br>
   * <br>
   * Evaluates every argument to the function. Then, if this function returns null on null input,
   * shortcircuit evaluation to return null if one argument is null. Otherwise, evaluate the full
   * function in {@link #reallyEvaluate(Tuple, TupleList[], MemoizationTable, Object[])}.
   * 
   * @param t the primary input tuple
   * @param locatorArgs relations that serve as inputs to locators; index into this array is the
   *        same as the index into the schemas array provided at bind time. The array may have an
   *        extra entry at the end.
   * @param mt table of state that may be needed to evaluate various functions
   * @return the value that the function returns; type of this value must match the return value of
   *         {@link #returnType()}.
   */
  public Object evaluate(Tuple t, TupleList[] locatorArgs, MemoizationTable mt)
      throws TextAnalyticsException {
    int numArgs = (args == null) ? 0 : args.length;

    // evaluate all inputs and store them for later use so we don't double-evaluate
    Object[] evaluatedArgs = new Object[numArgs];

    // GHE #28
    // reset hasUnknownArg flag before evaluate not to memorize previous tuple evaluation
    if (this instanceof LogicalExpression) {
      ((LogicalExpression) this).hasUnknownArg = false;
    }

    for (int i = 0; i < numArgs; i++) {
      if (args[i] instanceof ScalarFunc) {
        ScalarFunc sf = (ScalarFunc) args[i];
        evaluatedArgs[i] = sf.evaluate(t, locatorArgs, mt);

        // short-circuit for null inputs when function returns null on null input
        if (evaluatedArgs[i] == null) {
          if (returnsNullOnNullInput()) {
            return null;
          }

          if (this instanceof LogicalExpression) {
            ((LogicalExpression) this).hasUnknownArg = true;
          }
        }
        // short-circuit for And()
        else if ((this instanceof And) && (evaluatedArgs[i] == Boolean.FALSE)) {
          return Boolean.FALSE;
        }
        // short-circuit for Or()
        else if ((this instanceof Or) && (evaluatedArgs[i] == Boolean.TRUE)) {
          return Boolean.TRUE;
        }
        // short-circuit for Case statement
        else if (this instanceof Case) {
          // Condition has been met
          if (evaluatedArgs[i] == Boolean.TRUE) {
            i++;
            if (args[i] instanceof ScalarFunc) {
              // Evaluate the expression and return since we have met the condition
              ScalarFunc sf1 = (ScalarFunc) args[i];
              evaluatedArgs[i] = sf1.evaluate(t, locatorArgs, mt);
              return evaluatedArgs[i];
            }
          }
          // Condition has not been met, skip expression to process next condition
          else if (evaluatedArgs[i] == Boolean.FALSE) {
            i++;
          }
        }
      } else if (args[i] instanceof TableReturningFunc) {
        TableReturningFunc tl = (TableReturningFunc) args[i];
        TupleList origList = tl.evaluate(locatorArgs, mt);

        // If a virtual projection is occurring, the TupleList returned may have a schema that
        // contains extra columns
        // that the function shouldn't see. Replace that schema with the "proper" schema.
        evaluatedArgs[i] = origList.overrideSchema(tl.getOutputSchema());

        if ((evaluatedArgs[i] == null) && returnsNullOnNullInput()) {
          return null;
        }
      } else {
        // aggregate functions are not allowed as arguments to scalar functions
        throw new FatalInternalError(
            "Unexpected argument type %s (object is %s); an aggregate function is not allowed as an argument to a scalar function.",
            args[i].getClass().getName(), args[i]);
      }

    }

    // evaluate the function now that the arguments have been evaluated
    return reallyEvaluate(t, locatorArgs, mt, evaluatedArgs);
  }

  /**
   * Apply the scalar function over the indicated tuple, using bindings set up with
   * {@link #bind(AbstractTupleSchema)}. If the function returns null on null input, the evaluated
   * arguments are guaranteed to be non-null. This function should not be called directly, use
   * {@link #evaluate(Tuple, TupleList[], MemoizationTable)} instead.
   * 
   * @param t the primary input tuple
   * @param locatorArgs relations that serve as inputs to locators; index into this array is the
   *        same as the index into the schemas array provided at bind time. The array may have an
   *        extra entry at the end.
   * @param mt table of state that may be needed to evaluate various functions
   * @param evaluatedArgs previously-evaluated arguments to the function
   * @return the value that the function returns; type of this value must match the return value of
   *         {@link #returnType()}.
   */
  protected abstract Object reallyEvaluate(Tuple t, TupleList[] locatorArgs, MemoizationTable mt,
      Object[] evaluatedArgs) throws TextAnalyticsException;

  /**
   * Subclasses that always return the same value should override this method to return true and
   * ensure that {@link #evaluate(Tuple, MemoizationTable)} works with both arguments set to null.
   * 
   * @return true if this function always returns the same value
   */
  public boolean isConst() {
    return false;
  }

  /**
   * Subclasses that always return the same value should override this method to return false
   * 
   * @return true if the function,given the same input at *any* point in time, will return the same
   *         answer (AutoID returns is not deterministic as it can return a different answer for the
   *         same input at any point in time); but it may return one value for one input tuple, and
   *         a different value for another input tuple (IMP: unlike constant functions)
   */
  public boolean isDeterministic() {
    return true;
  }

  /**
   * If the scalar function always returns the same value, override this method to return that
   * value.
   */
  public Object evaluateConst() {
    if (false == isConst()) {
      throw new RuntimeException(
          String.format("This function (%s)" + " does not always return the same value",
              this.getClass().getName()));
    } else {
      try {
        return evaluate(null, null, null);
      } catch (TextAnalyticsException e) {
        throw new FatalInternalError("Evaluation of constant %s threw exception.", this);
      }
    }
  }

  /**
   * Utility function to aid in type-checking.
   * 
   * @param arg argument you're trying to check
   * @return true if the argument is a function call or something that can be turned into a function
   *         call
   */
  public static boolean producesFunc(Object arg) throws ParseException {
    if (arg instanceof ScalarFunc) {
      return true;
    } else if (arg instanceof String || arg instanceof Integer) {
      // For now, assume every string or int can be converted to a
      // function call.
      return true;
    } else if (arg instanceof ColNameNode || arg instanceof ScalarFnCallNode) {
      // AQL parse tree nodes are OK too.
      return true;
    } else {
      return false;
    }
  }

  /**
   * @return names of any relations referenced by this function or any functions it calls.
   */
  public final Set<String> getReferencedRels() {
    TreeSet<String> ret = new TreeSet<String>();
    computeReferencedRels(ret);
    return ret;
  }

  /**
   * Default implementation of walking the function call tree to find referenced relations. Override
   * as needed.
   */
  protected void computeReferencedRels(TreeSet<String> set) {

    // Cover children first.
    for (AQLFunc arg : args) {
      ScalarFunc sf = (ScalarFunc) arg;
      sf.computeReferencedRels(set);
    }

    // Then cover the parent
    getLocalRefRels(set);
  }

  /**
   * Subclasses can replace this with a function that adds the appropriate strings to the input set.
   * 
   * @param set names of relations that are already known to be referenced by the current function
   *        call tree
   */
  protected void getLocalRefRels(TreeSet<String> set) {
    // By default don't add anything to the set.
  }

  /**
   * Convenience function for use by subclasses for the common case where all arguments are
   * ScalarFunc objects
   * 
   * @param index index of an argument to this function call
   * @return the argument, cast to ScalarFunc
   */
  protected ScalarFunc getSFArg(int index) {
    return (ScalarFunc) args[index];
  }

  /**
   * Null input handling policy. Default policy is to return null on null input. Functions such as
   * IsNull() or Cast() may override this method
   * 
   * @return boolean indicating the policy
   */
  protected boolean returnsNullOnNullInput() throws TextAnalyticsException {
    return true;
  }
}
