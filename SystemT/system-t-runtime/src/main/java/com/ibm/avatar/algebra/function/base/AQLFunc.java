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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.base.ProfileRecord;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.api.exceptions.FunctionCallInitializationException;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.aql.Token;

/**
 * Base class for scalar and table functions in SystemT. Common functionality that is shared between
 * both scalar and table functions.
 * 
 */
public abstract class AQLFunc {

  /**
   * Name of special static field in a function implementation class that holds human-readable names
   * of the function's arguments.
   */
  public static final String ARGNAMES_FIELD_NAME = "ARG_NAMES";

  /**
   * Name of special static field in a function implementation class that holds static types of the
   * function's arguments, for non-polymorphic functions.
   */
  public static final String ARGTYPES_FIELD_NAME = "ARG_TYPES";

  /**
   * Name of special static field in a function implementation class that holds human-readable
   * descriptions of the function's arguments.
   */
  public static final String ARGDESCR_FIELD_NAME = "ARG_DESCRIPTIONS";

  /**
   * Name of special static field in a function implementation class that holds a custom USAGE
   * string.
   */
  public static final String USAGE_FIELD_NAME = "USAGE";

  /**
   * Name of special static field in a function implementation class that holds an alternate AQL
   * function name to be used instead of the class name. If this field is present, it is used in
   * place of the
   */
  public static final String FNAME_FIELD_NAME = "FNAME";

  /** The AQL parser token containing the name of the function -- for error handling */
  protected Token origTok;

  /** States that a subclass of AQLFunc can be in. */
  protected enum State {
    UNINITIALIZED, // No initialization code has been called
    BOUND, // bind() has been called, but initState() has not been called
    INITIALIZED // initState() has been called
  };

  protected State state = State.UNINITIALIZED;

  /**
   * Arguments of the function.
   */
  protected AQLFunc[] args;

  /** Flag to indicate that argument order should be ignored. */
  protected boolean ignoreArgOrder = false;

  /**
   * Object used to mark tokenization overhead that this function incurs.
   */
  protected ProfileRecord tokRecord = new ProfileRecord(Operator.TOKENIZATION_OVERHEAD_NAME, //
      /* TODO: How can we get a pointer to Operator here? */null);

  /**
   * Main constructor, mainly present to standardize the inputs to the constructors of child
   * classes.
   * 
   * @param origTok parser token for the function name.
   * @param args the list of arguments to the function in the original statement.
   */
  protected AQLFunc(Token origTok, AQLFunc[] args) {
    // Validate arguments
    if (null != args) {
      for (int i = 0; i < args.length; i++) {
        if (null == args[i]) {
          throw new FatalInternalError("AQLFunc object received null pointer for argument %d", i);
        }
      }
    }

    // Make sure that the current class has enough information to construct a "usage" message
    // but not too much information
    {
      boolean haveUsage = (null != getDeclFieldByName(USAGE_FIELD_NAME));
      boolean haveArgNames = (null != getDeclFieldByName(ARGNAMES_FIELD_NAME));

      if (!haveUsage && !haveArgNames) {
        throw new FatalInternalError("%s class does not define either of %s or %s",
            this.getClass().getName(), USAGE_FIELD_NAME, ARGNAMES_FIELD_NAME);
      }
      if (haveUsage && haveArgNames) {
        throw new FatalInternalError(
            "%s class defines both %s and %s; "
                + "unclear which should be used when generating 'usage' message",
            this.getClass().getName(), USAGE_FIELD_NAME, ARGNAMES_FIELD_NAME);
      }
    }

    this.origTok = origTok;
    this.args = args;
  }

  /**
   * Callback for use by bind(). Validates the types of the arguments of the function. Only called
   * when the Default implementation only works for functions that take a fixed list of arguments.
   * Default implementation assumes the types of arguments are fixed. Override as necessary, but
   * please use {@link #makeUsageException(String, Object...)} when constructing any checked
   * exceptions.
   * 
   * @param argTypes return types of the function's arguments, in the order they appear
   */
  protected void validateArgTypes(ArrayList<FieldType> argTypes)
      throws FunctionCallValidationException {
    // Default implementation assumes the types of arguments are fixed.
    FieldType[] expectedArgTypes;
    try {
      Field f = this.getClass().getField(ARGTYPES_FIELD_NAME);
      expectedArgTypes = (FieldType[]) f.get(this);
    } catch (Throwable e) {
      throw new FatalInternalError(
          "Could not find a %s field in function implementation class %s,"
              + "and class does not override validateArgTypes()",
          ARGTYPES_FIELD_NAME, this.getClass().getName());
    }

    if (argTypes.size() != expectedArgTypes.length) {
      throw new FunctionCallValidationException(this,
          "Number of arguments for %s function call (%d) does not match expected value (%d).  Arguments are %s.",
          this, argTypes.size(), expectedArgTypes.length, Arrays.toString(args));
    }

    // If we get here, sizes match. Check types.
    for (int i = 0; i < expectedArgTypes.length; i++) {
      FieldType expectedType = expectedArgTypes[i];
      FieldType actualType = argTypes.get(i);

      // allow all null types to pass - only validate if argument is non-null
      if (false == actualType.getIsNullType()) {

        if (false == expectedType.accepts(actualType)) {
          throw new FunctionCallValidationException(this,
              "In function call %s, argument %d of %s function\n returns "
                  + "type %s\n expected type %s",
              this, i, computeFuncName(), actualType, expectedType);
        }
      }
    }

  }

  /**
   * Initialize any function state that may need to be kept across calls. Note that this method will
   * be called once per thread; any information needed to evaluate this method MUST be kept around
   * until the first evaluate() call.
   * 
   * @param mt global per-thread table of information that is kept across calls
   */
  public final void initState(MemoizationTable mt) throws FunctionCallInitializationException {
    // Enforce API state machine restrictions
    if (false == (State.BOUND.equals(state)) && false == (State.INITIALIZED.equals(state))) {
      throw new FatalInternalError("initState() called when function %s is in %s state.", this,
          state);
    }

    // Initialize children first.
    if (null != args) {
      for (int i = 0; i < args.length; i++) {
        AQLFunc arg = args[i];
        if (null == arg) {
          throw new FatalInternalError("AQLFunc object has no argument pointer at position %d", i);
        }
        arg.initState(mt);
      }
    }

    // Now we can initialize the parent's state.
    initStateInternal(mt);

    state = State.INITIALIZED;
  }

  /**
   * Subclasses should override this method as needed with code to set up persistent state, as well
   * as any state that should not be initialized until after bind time.
   * 
   * @param mt global per-thread table of information that is kept across calls
   */
  protected void initStateInternal(MemoizationTable mt) throws FunctionCallInitializationException {
    // By default do nothing.
  }

  /**
   * Subclasses should override this method as needed, but only if the subclass can implement
   * multiple different AQL functions at runtime. Otherwise, use the special field FNAME or the
   * class name to encode the AQL function name.
   * 
   * @return the name of the function as it appears in AQL
   */
  protected String getFuncName() {
    throw new FatalInternalError(
        "The superclass's implementation of getFuncName() should never be called (current class is %s)",
        this.getClass().getName());
  }

  @Override
  public String toString() {
    // Print the function call tree with no indents or carriage returns.
    StringBuilder sb = new StringBuilder();
    sb.append(computeFuncName());
    sb.append('(');
    if (null != args) {
      for (int i = 0; i < args.length; i++) {
        sb.append(args[i].toString());
        if (i < args.length - 1) {
          sb.append(", ");
        }
      }
    }
    sb.append(')');

    return sb.toString();
  }

  /**
   * Callback for use in generating error messages.
   * 
   * @return a human-readable "usage" message for the function, in a standardized format
   */
  public String getUsage() {
    // Pull necessary information from fields of the current implementation class by reflection.
    // First check for a hard-coded USAGE field
    String hardCodedStr = (String) getFieldByName(USAGE_FIELD_NAME);
    if (null != hardCodedStr) {
      return hardCodedStr;
    }

    // If we get here, there is no hard-coded USAGE string (the common case). Construct a standard
    // string from the
    // ARG_NAMES and ARG_DESCRS fields.
    String[] argNames = getArgNames();

    // Descriptions are optional
    String[] argDescr = (String[]) getFieldByName(ARGDESCR_FIELD_NAME);

    // Fetch argument type information, if possible; if type info is not available, this variable
    // will be set to null
    // here.
    FieldType[] argTypes = getArgTypes();

    // Now we can construct the string.
    // Format is:
    // "Usage: FuncName(arg1 type1, arg2 type2)
    // where:
    // arg1 is descr1
    // arg2 is descr2"
    StringBuilder sb = new StringBuilder();
    sb.append("Usage: ");
    sb.append(computeFuncName());
    sb.append("(");

    if (null == argTypes) {
      // No type info; print out just the argument names
      sb.append(StringUtils.join(argNames, ", "));
    } else {

      // Sanity-check the type info the subclass provided.
      if (argTypes.length != argNames.length) {
        throw new FatalInternalError(
            "Argument names and types arrays for %s function call "
                + "have different lengths (arrays are %s and %s)",
            this.getFuncName(), Arrays.toString(argNames), Arrays.toString(argTypes));
      }

      // Have type info. Print it out.
      for (int i = 0; i < argTypes.length; i++) {
        sb.append(argNames[i]);
        sb.append(" ");
        sb.append(argTypes[i].toString());
        if (i < argTypes.length - 1) {
          sb.append(", ");
        }
      }
    }

    sb.append(")");
    if (null != argDescr && argNames.length > 0) {
      if (argDescr.length != argNames.length) {
        throw new FatalInternalError(
            "%s and %s fields of %s are of different lengths (%d and %d, respectively)",
            ARGNAMES_FIELD_NAME, ARGDESCR_FIELD_NAME, this.getClass().getName(), argNames.length,
            argDescr.length);
      }
      sb.append("\nwhere:");
      for (int i = 0; i < argNames.length; i++) {
        sb.append("\n  ");
        sb.append(argNames[i]);
        sb.append(" is ");
        sb.append(argDescr[i]);
      }
    }

    return sb.toString();
  }

  /**
   * Attempt to compute the AQL name of a function directly from the class metadata; only works for
   * functions whose names do not change at runtime.
   * 
   * @param c class information about the function implementation class
   * @return AQL name of the function
   */
  public static final String computeFuncName(Class<? extends AQLFunc> c) {
    return computeFuncName(c, null);
  }

  /**
   * @return the AQL name of the function implemented by this object
   */
  public final String computeFuncName() {
    return computeFuncName(this.getClass(), this);
  }

  /**
   * Internal implementation of the two public functions by the same name.
   * 
   * @param c class information about the function implementation class in question
   * @param func runtime instance of the function class, if available; otherwise null
   * @return the AQL function name of the function
   */
  private static final String computeFuncName(Class<? extends AQLFunc> c, AQLFunc func) {
    // If there is an "FNAME" field, use that field to compute the function name
    for (Field field : c.getDeclaredFields()) {
      if (FNAME_FIELD_NAME.equals(field.getName())) {
        try {
          return (String) field.get(func);
        } catch (Exception e) {
          // We throw FatalInternalError here because this method is used inside error handling
          // code.
          throw new FatalInternalError(e, "Error retrieving value of %s field for class %s",
              FNAME_FIELD_NAME, c.getName(), e);
        }
      }
    }

    // If there is an implementation of getFuncName(), use that function on the class instance to
    // generate the
    // (dynamically computed) name
    final String FUNC_NAME_METHOD = "getFuncName";
    for (Method m : c.getDeclaredMethods()) {
      if (FUNC_NAME_METHOD.equals(m.getName()) && 0 == m.getParameterTypes().length) {
        if (null == func) {
          // We throw FatalInternalError here because this method is used inside error handling
          // code.
          throw new FatalInternalError(
              "Attempted to compute AQL function name from only class name of %s, "
                  + "but this function name can only be computed whan an instance of the class is available",
              c.getName());
        }
        try {
          return (String) m.invoke(func);
        } catch (Exception e) {
          throw new FatalInternalError(e, "Error calling %s method on instance %s of class %s",
              FNAME_FIELD_NAME, func, c.getName());
        }
      }
    }

    // Otherwise use the class name.
    return c.getSimpleName();
  }

  /**
   * Check if this function tree has any reference to lemma match, i.e., a call to GetLemma(), or a
   * call to ContainsDict/ContainsDicts/MatchesDict with a dictionary with the lemma_match flag.
   * 
   * @return true if the tree of functions rooted at this function contains a reference to lemma
   *         match
   */
  public final boolean requiresLemma() {
    // Check this function first
    if (requiresLemmaInternal())
      return true;

    // Check the function inputs
    if (null != args) {
      for (int i = 0; i < args.length; i++) {
        AQLFunc arg = args[i];
        if (null == arg) {
          throw new FatalInternalError("AQLFunc object has no argument pointer at position %d", i);
        }

        if (arg.requiresLemma())
          return true;
      }
    }

    // If we got here, the tree of functions rooted at this function doesn't have a lemma reference
    return false;
  }

  /**
   * Check if this function itself has any reference to lemma match; subclasses should override the
   * default implementation
   * 
   * @return true if this function itself (not its inputs) requires lemma match
   */
  protected boolean requiresLemmaInternal() {
    // Default implementation returns false
    return false;
  }

  /*
   * INTERNAL UTILITY METHODS GO BELOW THIS LINE
   */

  /**
   * Callback for use in generating error messages. Default implementation assumes that the subclass
   * defines the constant ARG_NAMES.
   * 
   * @return list of the human-readable names of the arguments that this function returns
   */
  protected String[] getArgNames() {
    String[] ret = (String[]) getFieldByName(ARGNAMES_FIELD_NAME);
    if (null == ret) {
      throw new FatalInternalError("Could not find a %s field in function implementation class %s",
          ARGNAMES_FIELD_NAME, this.getClass().getName());
    }
    return ret;
  }

  /**
   * Callback for use in generating error messages. Default implementation assumes that the subclass
   * defines the constant ARG_TYPES. Override as needed.
   * 
   * @return list of the types of arguments that this function expects, or null if no type
   *         information is available
   */
  protected FieldType[] getArgTypes() {
    FieldType[] ret = (FieldType[]) getFieldByName(ARGTYPES_FIELD_NAME);
    return ret;
  }

  /**
   * Utility method to retrieve a field of the current object by reflection, without lots of
   * exception handling code
   * 
   * @param name name of the field to retrieve
   * @return value of the indicated field on this object, or null if not found
   */
  private Object getDeclFieldByName(String name) {
    Field[] fields = this.getClass().getDeclaredFields();
    Field targetField = null;
    for (Field field : fields) {
      if (field.getName().equals(name)) {
        targetField = field;
      }
    }

    if (null == targetField) {
      // Field not found --> return null
      return null;
    }

    try {
      return targetField.get(this);
    } catch (Throwable e) {
      throw new FatalInternalError(e,
          "Could not retrieve %s field in function implementation class %s", ARGDESCR_FIELD_NAME,
          this.getClass().getName());
    }
  }

  /**
   * Utility method to retrieve a field of the current object by reflection, without lots of
   * exception handling code
   * 
   * @param name name of the field to retrieve
   * @return value of the indicated field on this object
   */
  private Object getFieldByName(String name) {
    Field[] fields = this.getClass().getFields();
    Field targetField = null;
    for (Field field : fields) {
      if (field.getName().equals(name)) {
        targetField = field;
      }
    }

    if (null == targetField) {
      // Field not found --> return null
      return null;
    }

    try {
      return targetField.get(this);
    } catch (Throwable e) {
      throw new FatalInternalError(e,
          "Could not retrieve %s field in function implementation class %s", ARGDESCR_FIELD_NAME,
          this.getClass().getName());
    }
  }

}
