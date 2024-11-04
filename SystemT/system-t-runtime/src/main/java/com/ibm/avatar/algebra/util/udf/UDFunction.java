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
/**
 * 
 */
package com.ibm.avatar.algebra.util.udf;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.function.base.AQLFunc;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.api.exceptions.FunctionCallValidationException;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.Token;

/**
 * Wrapper class for invoking a user-defined function by reflection.
 * 
 * @Huaiyu Zhu 2013-09. Rewrite to allow auto conversion between String and Text types.
 */
public class UDFunction {
  /*
   * Keep these in sync with the literals used in the AQL parser UDFParamTypeLiteral() function
   */
  // public static final String TEXT_TYPE_LITERAL = "Text";
  // public static final String SPAN_TYPE_LITERAL = "Span";
  // public static final String INTEGER_TYPE_LITERAL = "Integer";
  // public static final Object BOOLEAN_TYPE_LITERAL = "Boolean";
  // public static final String SCALARLIST_TYPE_LITERAL = "ScalarList";

  private static final boolean debug = false;

  /**
   * The UDF parameter names and types as seen in AQL type system/
   */
  private final UDFParams udfparams;

  /**
   * The name of the jar file containing the implemented Java class
   */
  private final String jarName;

  /**
   * The name of the Java class that implements the UDF function
   */
  private final String className;

  /**
   * The name of the Java method that implements the UDF function
   */
  private final String methodName;

  /**
   * Boolean to record if class is loaded
   */
  private boolean classIsLoaded = false;

  /**
   * The implementation class that the UDF uses.
   */
  private Class<?> javaClass;

  /**
   * The specific method within the implementation class that the UDF uses.
   */
  private Method javaMethod;

  /**
   * The argument types of the Java method
   */
  private Class<?>[] javaArgTypes;

  /**
   * The return type of the Java method
   */
  private Class<?> javaReturnType;

  /**
   * The argument types of the UDF represented as Java classes
   */
  private final Class<?>[] udfArgTypes;

  /**
   * The return type of the UDF represented as Java class
   */
  private final Class<?> udfReturnType;

  /**
   * If the implementing method is not static, the following field holds an instance of the
   * implementing class, created once when the operator graph is initialized. Currently, this
   * instance is shared by ALL threads that execute the operator graph. Methods of the instance MUST
   * be reentrant.
   */
  protected Object instance;

  /**
   * Main constructor, invoked from both AOG and AQL parsing.
   * 
   * @param t AQL parser token to use for error messages
   * @param params detailed information about this user-defined function
   */
  public UDFunction(Token t, UDFParams params) throws ParseException {
    udfparams = params;

    // Find the UDF argument types
    udfArgTypes = new Class[udfparams.getColNamesAndTypes().size()];
    int i = 0;
    for (Pair<String, String> p : udfparams.getColNamesAndTypes()) {
      String fieldTypeName = p.second;

      // Convert to AQL type. If the type is String it is turned into Text
      FieldType ft = FieldType.stringToFieldType(fieldTypeName);
      udfArgTypes[i] = ft.getRuntimeClass();
      i++;
    }

    // Find the UDF return type;
    udfReturnType = udfparams.getReturnType().getRuntimeClass();

    // Find the external name of UDF
    String externalName = udfparams.getExternalName();
    if (externalName == null) {
      throw new ParseException(String.format("At line %d of operator graph definition,"
          + " external path of UDF '%s' not " + " specified", t.beginLine, getName()));
    }

    // Get the UDF jar name
    jarName = udfparams.getJarName();
    if (jarName == null) {
      throw new ParseException("Jar name missing in " + externalName);
    }

    // Get the class name in the UDF jar
    className = udfparams.getClassName();
    if (className == null) {
      throw new ParseException("Class name missing in " + externalName);
    }

    // Get the method name
    methodName = udfparams.getMethodName();
    if (methodName == null) {
      throw new ParseException("Method name missing in " + externalName);
    }

    // jar will be loaded by setJarFile later

  }

  /** @return the name of the AQL function that this UDFunction object is wrapping. */
  public String getName() {
    return udfparams.getFunctionName();
  }

  /** @return name of the implementing method for this user-defined function. */
  public String getMethodName() {
    return methodName;
  }

  /** @return identifier of the implementing method for this user-defined function. */
  public Method getMethod() {
    return javaMethod;
  }

  public UDFParams getParams() {
    return udfparams;
  }

  /**
   * @return instance of the implementing class that will be called to invoke this user-defined
   *         function, or null if the function is static
   */
  public Object getInstance() {
    return instance;
  }

  /**
   * Evaluate the UDF by calling the implemented Java method. The input and output types are
   * converted as appropriate.
   * 
   * @param argVals
   * @return
   */
  public Object evaluate(Object[] argVals) {

    // Extra defensive error-handling code, just in case.
    if (!classIsLoaded) {
      throw new RuntimeException(String.format(
          "Class %s not loaded; this usually means that evaluate() was called before loadClass()",
          className));
    }

    if (javaClass == null) {
      throw new RuntimeException(String.format("Class %s not loaded", className));
    }

    if (javaMethod == null) {
      throw new RuntimeException(String.format("Method %s not loaded", methodName));
    }

    if (argVals.length != javaArgTypes.length) {
      throw new RuntimeException(
          String.format("Fatal internal error: UDF expects %s parameters but received %s",
              javaArgTypes.length, argVals.length));
    }

    // Convert the types of the objects we receive to the types the implemented UDF is expecting.
    Object[] convertedVal = new Object[javaArgTypes.length];
    for (int i = 0; i < javaArgTypes.length; i++) {
      Class<?> type = javaArgTypes[i];
      Object val = argVals[i];
      Object newVal = convertType(type, val);
      convertedVal[i] = newVal;
    }

    // Invoke the implementing method.
    Object result;
    try {
      result = javaMethod.invoke(instance, convertedVal);
    } catch (IllegalArgumentException e) {
      String message = String.format(
          "Exception while invoking UDF method '%s.%s':\n expected parameters:\n %s. actual objects: %s",
          methodName, className, Arrays.toString(javaArgTypes), Arrays.toString(convertedVal));
      // System.err.println (message);
      throw new RuntimeException(message, e);
    } catch (Exception e) {
      throw new RuntimeException(String.format("Exception while invoking method '%s' on class '%s'",
          methodName, className), e);
    }

    // Convert the result from the implementation to this declared UDF.
    return convertType(udfReturnType, result);

  }

  /**
   * Convert the given object to the desired type. Currently the only implemented conversions are
   * between String and Text types. All other objects are passed through.
   * 
   * @param type
   * @param obj
   * @return
   */
  private Object convertType(Class<?> type, Object obj) {
    if (String.class.equals(type) && obj instanceof Text) {
      return Text.convertToString(obj);
    }

    if (Text.class.equals(type) && obj instanceof String) {
      return Text.convert(obj);
    }

    return obj;
  }

  /**
   * Interrogate the class loader for the information necessary to load the UDF, but do not
   * instantiate the class.
   * <p>
   * Starting from 4Q 2014, SystemT type system removes the String type and replaces it with Text
   * type. For backward compatibility, a UDF can be implemented to use either String or Text type.
   * String is considered as a synonym for Text. This has two consequences:
   * <ul>
   * <li>Consequence 1: The evaluation method must do appropriate conversions among compatible
   * types. This requires keeping track of both UDF types and Java types of the function parameters
   * and return value.
   * <li>Consequence 2: UDFs that have multiple methods with compatible signatures are considered as
   * ambiguous and cause a ParserException to be thrown. throw exception.
   * </ul>
   * <p>
   * Upon success, the following fields are set with valid values: javaMethod , javaArgTypes,
   * javaReturnType. Upon failure, a ParserException is thrown with indication of the cause of the
   * failre.
   * <p>
   * This is done in 4 steps:
   * <ul>
   * <li>1. Try to load the class.
   * <li>2. Iterate over all declared methods of the class.
   * <li>3. Filter through all methods for the given method name, parameter types and return type.
   * <li>4. If exactly 1 match is found, set variables avaMethod , javaArgTypes, javaReturnType..
   * <li>If less or more than 1 match is found, throw parser exception
   * </ul>
   * <p>
   * <ul>
   * <li>Starting from 4Q 2013, AQL has only Text type in place of previous String and Text types.
   * <li>For backward compatibility, a UDF can be implemented to use either String or Text type.
   * String is considered as a synonym for Text.
   * <li>Consequence 1: In our evaluation method, appropriate conversion will be done. For this
   * purpose we must keep track of both UDF types and Java types of the function arguments and
   * returns.
   * <li>Consequence 2: We will consider UDFs that have multiple methods with compatible signatures
   * as ambiguous, and throw exception.
   * </ul>
   * <p>
   * 
   * @param loader class loader that knows how to create an instance of the UDF class.
   * @throws ParseException Exception thrown when failing to find exactly one compatible
   *         implementation.
   */
  public void loadReflectionInfo(ClassLoader loader) throws ParseException {
    if (classIsLoaded) {
      throw new FatalInternalError("UDFunction.loadReflectionInfo() called twice for %s.%s()",
          className, methodName);

    }

    //
    // Step 1. Try to load the class
    //
    try {
      javaClass = Class.forName(className, true, loader);
    } catch (ClassNotFoundException e) {
      // System.err.printf ("Class %s not found in jar '%s' \n", className, jarName);
      throw new ParseException(
          String.format("Class %s not found in jar '%s' ", className, jarName));
    } catch (ExceptionInInitializerError e2) {
      // Some existing UDF code has static initializers that will crash the above call to
      // Class.forName() if called at
      // compile time. For now, just skip the rest of this test if that happens.
      return;
    } catch (UnsupportedClassVersionError ucve) {
      throw new ParseException(ucve, String.format(
          "Java class %s was compiled with a version of Java more recent than the one used for execution. Execute using the version of Java used to compile the UDF, or recompile the UDF class using the version of java you wish to compile/execute the extractor with.",
          className));
    }

    if (null == javaClass) {
      throw new FatalInternalError("classToLoad still set to null after load operation");
    }


    //
    // Step 2. Obtain all declared methods of this class
    //
    Method[] javaMethods = javaClass.getDeclaredMethods();


    //
    // Step 3. Filter through all methods for the given method name, parameter types and return
    // type.
    //
    // Step 3.0 Initialize containers for the candidate methods
    int numArgs = udfArgTypes.length;
    List<Method> incompatibleMethods = new ArrayList<Method>();
    List<Method> compatibleMethods = new ArrayList<Method>();

    // Iterate over all the declared methods of this class
    for (Method javaMethod : javaMethods) {
      if (debug) {
        System.out.printf("Verifying  method %s\n", javaMethod);
      }

      // Step 3.1 Check method name
      if (!methodName.equals(javaMethod.getName()))
        continue;

      // Step 3.2 Check number of parameters
      Class<?>[] javaArgTypes = javaMethod.getParameterTypes();
      if (numArgs != javaArgTypes.length) {
        incompatibleMethods.add(javaMethod);
        continue; // with the next method
      }

      // Step 3.3 Check parameter types
      boolean paramsCompatible = true;
      for (int i = 0; i < numArgs; i++) {
        Class<?> javaParamType = javaArgTypes[i];
        Class<?> udfParamType = udfArgTypes[i];
        if (!typeCompatibleWith(udfParamType, javaParamType)) {
          paramsCompatible = false;
          break;
        }
      }
      if (!paramsCompatible) {
        incompatibleMethods.add(javaMethod);
        continue; // with the next method
      }

      // Step 3.4 Check return type
      javaReturnType = javaMethod.getReturnType();
      if (!typeCompatibleWith(javaReturnType, udfReturnType)) {
        incompatibleMethods.add(javaMethod);
        continue; // with the next method
      }

      // Step 3.5 Passed all checks. This method is compatible
      compatibleMethods.add(javaMethod);
    }

    //
    // Step 4. Verify that exactly one compatible method is defined.
    //
    int numOfCompatibleMethods = compatibleMethods.size();
    if (debug) {
      System.out.printf("Found %s compatible methods with name %s\n", numOfCompatibleMethods,
          methodName);
    }


    // 4.1 If no valid method is found, throw exception containing signature of all methods
    if (numOfCompatibleMethods == 0) {
      StringBuilder sb = new StringBuilder();
      sb.append(String.format("No compatible Java method found for class %s method %s %s -> %s.\n",
          className, methodName, Arrays.toString(udfArgTypes), udfReturnType));

      int numIncompabile = incompatibleMethods.size();
      if (numIncompabile > 0) {
        sb.append(String.format("There are %s methods with same name but incompatible types:\n",
            numIncompabile));
        for (Method javaMethod : incompatibleMethods) {
          sb.append(String.format("\t%s -> %s\n", Arrays.toString(javaMethod.getParameterTypes()),
              javaMethod.getReturnType()));
        }
      } else {
        sb.append("No methods with this name is declared, either");
      }

      throw new ParseException(sb.toString());

    }

    // 4.2 If More than one method is found, throw exception.
    if (numOfCompatibleMethods > 1) {
      StringBuilder sb = new StringBuilder();
      sb.append(
          String.format("Multiple compatible Java method found for class %s method %s %s -> %s.\n",
              className, methodName, Arrays.toString(udfArgTypes), udfReturnType));
      sb.append(String.format("There are %s methods with same name and compatible types:\n",
          numOfCompatibleMethods));
      for (Method javaMethod : compatibleMethods) {
        sb.append(String.format("\t%s.%s %s -> %s\n", javaClass.getName(), javaMethod.getName(),
            Arrays.toString(javaMethod.getParameterTypes()), javaMethod.getReturnType()));
      }

      throw new ParseException(sb.toString());
    }

    // 4.3 Exactly one compatible method is found. Record the methods and its input/output types.
    javaMethod = compatibleMethods.get(0);
    javaArgTypes = javaMethod.getParameterTypes();
    javaReturnType = javaMethod.getReturnType();

    if (debug) {
      System.out.printf("\tFound method %s(%s)=>%s\n", methodName, Arrays.toString(javaArgTypes),
          javaReturnType);
    }

  }

  /**
   * Interrogate the class loader for the information necessary to load the UDF, but do not
   * instantiate the class.
   * <p>
   * This is done in 6 steps.
   * <ul>
   * <li>1. Try to load the class.
   * <li>2. Set the initial java parameters as specified in UDF, with Text in place of String.
   * <li>3. Iterate over all alternatives to find compatible methods.
   * <li>4. Verify that exactly one valid method is defined.
   * <li>5. Find the parameters of the java method.
   * <li>6. Verify that the return type from the method is compatible with the UDF return type.
   * </ul>
   * <p>
   * Steps 2-4 try to get the implemented method from the loaded class, with the following
   * considerations:
   * <ul>
   * <li>Starting from 4Q 2013, AQL has only Text type in place of previous String and Text types.
   * <li>For backward compatibility, a UDF can be implemented to use either String or Text type.
   * String is considered as a synonym for Text.
   * <li>Consequence 1: In our evaluation method, appropriate conversion will be done. For this
   * purpose we must keep track of both UDF types and Java types of the function arguments and
   * returns.
   * <li>Consequence 2: We will consider UDFs that have multiple methods with compatible signatures
   * as ambiguous, and throw exception.
   * </ul>
   * <p>
   * 
   * @param loader class loader that knows how to create an instance of the UDF class.
   */
  public void loadReflectionInfoOld(ClassLoader loader) throws ParseException {
    if (classIsLoaded) {
      throw new FatalInternalError("UDFunction.loadReflectionInfo() called twice for %s.%s()",
          className, methodName);

    }

    // Step 1. Try to load the class
    try {
      javaClass = Class.forName(className, true, loader);
    } catch (ClassNotFoundException e) {
      // System.err.printf ("Class %s not found in jar '%s' \n", className, jarName);
      throw new ParseException(
          String.format("Class %s not found in jar '%s' ", className, jarName));
    } catch (ExceptionInInitializerError e2) {
      // Some existing UDF code has static initializers that will crash the above call to
      // Class.forName() if called at
      // compile time. For now, just skip the rest of this test if that happens.
      return;
    } catch (UnsupportedClassVersionError ucve) {
      throw new ParseException(ucve, String.format(
          "Java class %s was compiled with a version of Java more recent than the one used for execution. Execute using the version of Java used to compile the UDF, or recompile the UDF class using the version of java you wish to compile/execute the extractor with.",
          className));
    }

    if (null == javaClass) {
      throw new FatalInternalError("classToLoad still set to null after load operation");
    }

    // Step 2. Set the initial java parameters as specified in UDF, with Text in place of String.
    int num = udfArgTypes.length;

    javaArgTypes = new Class[num];
    for (int i = 0; i < num; i++) {
      Class<?> paramType = udfArgTypes[i];
      if (typeCompatibleWith(paramType, Text.class))
        paramType = Text.class;
      javaArgTypes[i] = paramType;
    }

    // Step 3. Iterate over all alternatives to find compatible methods. Keep track of number of
    // potential matches.
    int found = 0;
    int foundNotAccessible = 0;

    for (boolean hasNext = true; hasNext;) {
      // Step 3.1
      // Examine if a method with this signature exists. Keep count of numbers found so far.
      if (debug) {
        System.out.printf("Looking for method %s(%s)\n", methodName, Arrays.toString(javaArgTypes));
      }
      try {
        javaMethod = javaClass.getDeclaredMethod(methodName, javaArgTypes);
        found++;
      } catch (NoSuchMethodException e) {
        // Nothing to do here. Go to the next candidate.
      } catch (SecurityException e) {
        foundNotAccessible++;
        // Nothing else to do here. Go to the next candidate.
      } catch (NoClassDefFoundError e) {
        // We've alredy checked above that the class exists; if we're getting NoClassDefFound error
        // at this point in
        // compilation it must be due to something missing elsewhere.
        // NoClassDefFoundError.getMessage() returns the full class name.
        String classNotFoundName = e.getMessage();
        throw new ParseException(String.format(
            "Required library class '%s' not found while loading user-defined function"
                + " implementation method %s.%s."
                + "  Ensure that library classes are available on the classpath or included in the UDF's jar file.",
            classNotFoundName, className, methodName));
      }

      // Step 3.2
      // Generate the next candidate signature, in the order of bit iteration
      hasNext = false;
      // Find the right most parameter that is Text and set it to String.
      for (int i = num - 1; i >= 0; i--) {
        Class<?> paramType = javaArgTypes[i];
        if (Text.class.equals(paramType)) {

          // set it to String.
          paramType = String.class;
          javaArgTypes[i] = paramType;

          // Set all those to its right that are String to Text
          for (int j = i + 1; j < num; j++) {
            paramType = javaArgTypes[j];
            if (String.class.equals(paramType))
              paramType = Text.class;
            javaArgTypes[j] = paramType;
          }

          // This is a new alternative.
          hasNext = true;
          break;
        }
      }
      // At this point hasNext indicates whether javaParamTypes holds a new signature
    }

    // Step 4. Verify that exactly one valid method is defined.

    // 4.1 If no valid method is found, throw exception.
    if (found == 0) {

      if (foundNotAccessible > 0) {
        throw new ParseException(String.format(
            "There are %d method '%s' with parameters compatible to %s in class '%s', but they are all inaccessible.",
            methodName, Arrays.toString(udfArgTypes), className));

      } else {
        throw new ParseException(String.format(
            "Method '%s' with parameters compatible to %s does not exist in class '%s'.",
            methodName, Arrays.toString(udfArgTypes), className));
      }
    }

    // 4.2 If More than one method is found, throw expcetion.
    if (found > 1) {
      throw new ParseException(String.format(
          "Found more than one method '%s' with parameters compatible to %s in class '%s'",
          methodName, Arrays.toString(udfArgTypes), className));
    }

    // Step 5. At this point we have found exactly one method with compatible signature.
    // Now find the parameters of the java method
    javaArgTypes = javaMethod.getParameterTypes();

    // Step 6. Verify that the return type from the method is compatible with the UDF return type.
    javaReturnType = javaMethod.getReturnType();

    if (debug) {
      System.out.printf("\tFound method %s(%s)=>%s\n", methodName, Arrays.toString(javaArgTypes),
          javaReturnType);
    }

    if (!typeCompatibleWith(javaReturnType, udfReturnType)) {
      throw new ParseException(String.format(
          "Return type '%s' of method '%s' from jar %s cannot be used as return '%s' of the UDF",
          javaReturnType.getSimpleName(), methodName, jarName, udfReturnType.getSimpleName()));
    }
  }

  /**
   * Initialize an instance of the class that implements the UDF.
   * 
   * @param loader class loader that knows how to create an instance of the UDF class.
   */
  public void loadClass(ClassLoader loader) throws ParseException {
    if (classIsLoaded) {
      throw new FatalInternalError("UDFunction.loadClass() called twice for %s.%s()", className,
          methodName);
    }

    loadReflectionInfo(loader);

    // Step 7. Instantiate an instance of the class if this method is not static.
    if (Modifier.isStatic(javaMethod.getModifiers())) {
      instance = null;
    } else {
      // Non-static method; we'll need an instance of the class to invoke
      // the method.
      // We'll assume that the author of the method has had the good sense
      // to make the method reentrant, so we can instantiate the class
      // once for all threads.
      try {
        instance = javaClass.newInstance();
      } catch (IllegalAccessException e) {
        throw new RuntimeException(
            String.format("IllegalAccessException while" + " instantiating class '%s'", className),
            e);
      } catch (InstantiationException e) {
        throw new RuntimeException(String.format("Not able to instantiate class '%s'", className),
            e);
      }
    }

    // We are done.
    classIsLoaded = true;

  }

  /**
   * Determine if the giveType can be converted to the wantedType. This relation is potentially
   * asymmetric, but for now it is symmetric.
   * 
   * @param givenType the type of the object that is received
   * @param wantedType the type this object is to be converted to
   * @return
   */
  private boolean typeCompatibleWith(Class<?> givenType, Class<?> wantedType) {
    if (givenType.equals(wantedType))
      return true;

    // Currently we only allow conversion between String and Text in UDF.
    if ((String.class.equals(givenType) || Text.class.equals(givenType))
        && (String.class.equals(wantedType) || Text.class.equals(wantedType))) {
      return true;
    }

    return false;
  }

  /**
   * @return user-friendly "usage" string to tell the user how this function should be invoked.
   */
  public String getUsageStr() {
    String usage = "Usage: " + udfparams.getFunctionName() + "(";
    boolean first = true;
    for (Pair<String, String> p : udfparams.getColNamesAndTypes()) {
      if (first)
        first = false;
      else
        usage += ", ";
      usage += p.second;
    }
    usage += ")";
    return usage;
  }

  /**
   * Verify that a set of AQL scalar function trees will produce the appropriate return types to
   * feed the inputs of this user-defined function.
   * 
   * @param func function object for error handling
   * @param argTypes return types of zero or more trees of scalar function calls
   */
  public void validateParams(AQLFunc func, ArrayList<FieldType> argTypes)
      throws FunctionCallValidationException {
    ArrayList<Pair<String, String>> colNamesAndTypes = getParams().getColNamesAndTypes();

    // Make sure the number of arguments is correct.

    if (colNamesAndTypes.size() != argTypes.size()) {
      throw new FunctionCallValidationException(func, colNamesAndTypes.size(), argTypes.size());
    }

    Iterator<Pair<String, String>> it = colNamesAndTypes.iterator();
    for (int i = 0; i < argTypes.size(); i++) {
      FieldType fieldType = argTypes.get(i);

      Pair<String, String> p = it.next();
      String expectedTypeName = p.second;

      FieldType expectedType;
      try {
        expectedType = FieldType.stringToFieldType(expectedTypeName);
      } catch (com.ibm.avatar.aql.ParseException e) {
        // Upstream code should prevent bad type names from getting into the parameters list
        throw new FatalInternalError(
            "Cannot parse type name '%s' in arugments description for function %s",
            expectedTypeName, getName());
      }

      // Use a loose comparison that allows for automatic type conversions
      if (false == expectedType.accepts(fieldType)) {
        throw new FunctionCallValidationException(func,
            "Argument %d is of type %s instead of expected type %s.", i, fieldType, expectedType);
      }
    }

  }

}
