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

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;

import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.util.data.StringPairList;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.aql.FuncLanguage;
import com.ibm.avatar.aql.NickNode;
import com.ibm.avatar.aql.AbstractAQLParseTreeNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.aql.tam.ModuleUtils;
import com.ibm.avatar.logging.Log;

/**
 * Object that holds the full set of parameters needed to instantiate and use a user-defined
 * function. Reflection code for firing up the UDF implementation class lives in this class.
 * 
 */
public class UDFParams implements Comparable<UDFParams> {

  // Key values for the key-value mapping used to encode udf params

  // name of the function
  public static final String FUNCTIONNAME_PARAM = "functionName";

  // specific name of the function
  public static final String SPECIFICNAME_PARAM = "specificName";

  // external name of the function
  public static final String EXTERNALNAME_PARAM = "externalName";

  // language in which function is implemented
  public static final String LANGUAGE_PARAM = "language";

  // the return type
  public static final String RETURNTYPE_PARAM = "returnType";

  // the inp param from which the return span is derived
  public static final String RETURNSPANLIKE_PARAM = "returnSpanLike";

  // is the function deterministic
  public static final String DETERMINISTIC_PARAM = "deterministic";

  // does function return null on null inp
  public static final String RETURNSNULLONNULLINP_PARAM = "returnsNullOnNullInp";

  /*
   * FIELDS
   */

  private String functionName;
  private String externalName;
  private String jarName;
  private String className;
  private String methodName;
  private FuncLanguage language;
  private FieldType returnType;
  private String retSpanLike;
  private ArrayList<Pair<String, String>> colNamesAndTypes = new ArrayList<Pair<String, String>>();
  private boolean deterministic = true;
  private boolean returnsNullOnNullInp = true;

  /** Name of the module where function is defined */
  private String moduleName = null;

  /**
   * @return the functionName
   */
  public String getFunctionName() {
    return ModuleUtils.prepareQualifiedName(moduleName, functionName);
  }

  /**
   * @param functionName the functionName to set
   */
  public void setFunctionName(String functionName) {
    this.functionName = functionName;
  }

  /**
   * @return the externalName
   */
  public String getExternalName() {
    return externalName;
  }

  /**
   * @param externalName the externalName to set
   */
  public void setExternalName(String externalName) {
    this.externalName = externalName;

    jarName = null;
    className = null;
    methodName = null;
    // parse the external name
    if (null != externalName) {

      int idx = externalName.indexOf(':');
      if (idx != -1) {
        jarName = externalName.substring(0, idx);

        externalName = externalName.substring(idx + 1);

        idx = externalName.indexOf('!');
        if (idx != -1) {
          className = externalName.substring(0, idx);

          methodName = externalName.substring(idx + 1);
        }
      }
    }
  }

  /**
   * @return string representation of the implementation language for the UDF
   */
  public FuncLanguage getLanguage() {
    return language;
  }

  /**
   * @param language the language to set
   */
  public void setLanguage(FuncLanguage language) {
    this.language = language;
  }

  /**
   * @return return type of the UDF
   */
  public FieldType getReturnType() {
    return returnType;
  }

  /**
   * @return the retSpanLike
   */
  public String getRetSpanLike() {
    return retSpanLike;
  }

  /**
   * @return names and types of function arguments, as strings
   */
  public ArrayList<Pair<String, String>> getColNamesAndTypes() {
    return colNamesAndTypes;
  }

  /**
   * @return arguments to the function, as a TupleSchema object
   * @throws ParseException if the raw strings can't be turned into types
   */
  public TupleSchema getArgsAsSchema() throws ParseException {
    ArrayList<Pair<String, String>> rawSchema = getColNamesAndTypes();

    String[] colNames = new String[rawSchema.size()];
    FieldType[] colTypes = new FieldType[rawSchema.size()];

    for (int i = 0; i < colNames.length; i++) {
      Pair<String, String> p = rawSchema.get(i);
      colNames[i] = p.first;
      colTypes[i] = FieldType.stringToFieldType(p.second);
    }

    return new TupleSchema(colNames, colTypes);
  }

  /**
   * @param colNamesAndTypes the colNamesAndTypes to set
   */
  public void setColNamesAndTypes(ArrayList<Pair<String, String>> colNamesAndTypes) {
    this.colNamesAndTypes = colNamesAndTypes;
  }

  /**
   * @return the deterministic
   */
  public boolean isDeterministic() {
    return deterministic;
  }

  /**
   * @param deterministic the deterministic to set
   */
  public void setDeterministic(boolean deterministic) {
    this.deterministic = deterministic;
  }

  /**
   * @return the returnsNullOnNullInp
   */
  public boolean isReturnsNullOnNullInp() {
    return returnsNullOnNullInp;
  }

  /**
   * @param returnsNullOnNullInp the returnsNullOnNullInp to set
   */
  public void setReturnsNullOnNullInp(boolean returnsNullOnNullInp) {
    this.returnsNullOnNullInp = returnsNullOnNullInp;
  }

  /**
   * @return true if this is a user-defined table function
   */
  public boolean isTableFunc() {
    // The return type has already been encoded as a string by the time it reaches UDFParams.
    // System.err.printf ("isTableFunc(%s): return type str is '%s'\n", functionName, returnType);
    return returnType.getIsTableType();
  }

  /**
   * Constructor used at operator graph loading time.
   * 
   * @param paramStrs list of name-value pairs containing config params for this udf
   */
  public UDFParams(StringPairList paramStrs) {
    final boolean debug = false;

    for (Pair<String, String> nameVal : paramStrs) {
      if (FUNCTIONNAME_PARAM.equals(nameVal.first)) {
        functionName = nameVal.second;
      } else if (SPECIFICNAME_PARAM.equals(nameVal.first)) {
        // Old "specific name" parameter. In DB2, this parameter controls the function's unique
        // identifier in the
        // catalog. AQL has never used this parameter, but older versions kept placeholders for it
        // in the parse tree
        // nodes.
        // We need to keep this branch here because AOG from previous versions of SystemT will
        // contain it.
      } else if (EXTERNALNAME_PARAM.equals(nameVal.first)) {
        setExternalName(nameVal.second);
      } else if (LANGUAGE_PARAM.equals(nameVal.first)) {
        language = FuncLanguage.valueOf(nameVal.second);
      } else if (RETURNTYPE_PARAM.equals(nameVal.first)) {
        try {
          returnType = FieldType.stringToFieldType(nameVal.second);
        } catch (ParseException e) {
          // The string we're parsing here was generated by the AQL compiler and should always parse
          throw new FatalInternalError(e, "Error parsing type string '%s'", nameVal.second);
        }
      } else if (RETURNSPANLIKE_PARAM.equals(nameVal.first)) {
        retSpanLike = nameVal.second;
      } else if (DETERMINISTIC_PARAM.equals(nameVal.first)) {
        deterministic = Boolean.parseBoolean(nameVal.second);
      } else if (RETURNSNULLONNULLINP_PARAM.equals(nameVal.first)) {
        returnsNullOnNullInp = Boolean.parseBoolean(nameVal.second);
      } else /* this is a function parameter */ {
        colNamesAndTypes.add(nameVal);
      }

    }

    if (debug) {
      Log.debug("UDF params:\n"//
          + "    name       = %s\n"//
          + "    externalName = %s\n"//
          + "    language   = %s\n" //
          + "    returnType   = %s\n" //
          + "    retSpanLike = %s\n" //
          + "    deterministic   = %s\n" //
          + "    returnsNullOnNullInp    = %s\n" //
          , functionName, externalName, language, returnType, retSpanLike, deterministic,
          returnsNullOnNullInp);
    }
  }

  /**
   * Constructor used during AQL parsing.
   * 
   * @param functionName unqualified name of the function
   * @param externalName fully qualified name of the implementing (Java) method; argument of the
   *        EXTERNAL_NAME clause in the CREATE FUNCTION statement
   * @param language argument of the LANGUAGE clause
   * @param returnType For scalar types, string containing the name of the return type. For table
   *        types, string containing a comma-delimited list of space-delimited field name (in double
   *        quotes) and field type pairs.
   * @param retSpanLike if the return type is a parametrized type like Span or List, the identifier
   *        of the input argument from which parameters should be taken
   * @param deterministic TRUE if there is a DETERMINISTIC clause in the CREATE FUNCTION statement
   * @param returnsNullOnNullInp TRUE if the CREATE FUNCTION statement contains a RETURN NULL ON
   *        NULL INPUT, false otherwise
   * @param colNamesAndTypes list of the input arguments of the function
   */
  public UDFParams(String functionName, String externalName, FuncLanguage language,
      String returnType, String retSpanLike, boolean deterministic, boolean returnsNullOnNullInp,
      ArrayList<Pair<String, String>> colNamesAndTypes) throws ParseException {
    this.functionName = functionName;
    setExternalName(externalName);
    this.language = language;
    this.returnType = FieldType.stringToFieldType(returnType);
    this.retSpanLike = retSpanLike;
    this.deterministic = deterministic;
    this.returnsNullOnNullInp = returnsNullOnNullInp;
    this.colNamesAndTypes = colNamesAndTypes;
  }

  /**
   * Copy constructor.
   * 
   * @param orig parameter set to deep-copy
   */
  public UDFParams(UDFParams orig) {
    this.className = orig.className;

    // Deep-copy the parameter names and types
    this.colNamesAndTypes = new ArrayList<Pair<String, String>>();
    for (Pair<String, String> origPair : orig.colNamesAndTypes) {
      colNamesAndTypes.add(new Pair<String, String>(origPair.first, origPair.second));
    }

    this.deterministic = orig.deterministic;
    this.externalName = orig.externalName;
    this.functionName = orig.functionName;
    this.jarName = orig.jarName;
    this.language = orig.language;
    this.methodName = orig.methodName;
    this.moduleName = orig.moduleName;
    this.retSpanLike = orig.retSpanLike;
    this.returnsNullOnNullInp = orig.returnsNullOnNullInp;

    // Assume FieldType immutable
    this.returnType = orig.returnType;

  }

  /** Convert this object's parameter settings into a set of key-value pairs. */
  public StringPairList toKeyValuePairs() {
    StringPairList ret = new StringPairList();

    addIfNotNull(ret, FUNCTIONNAME_PARAM, getFunctionName());
    addIfNotNull(ret, EXTERNALNAME_PARAM, externalName);
    addIfNotNull(ret, LANGUAGE_PARAM, language.toString());
    addIfNotNull(ret, RETURNTYPE_PARAM, returnType.toString());
    addIfNotNull(ret, RETURNSPANLIKE_PARAM, retSpanLike);
    addIfNotNull(ret, DETERMINISTIC_PARAM, Boolean.toString(deterministic));
    addIfNotNull(ret, RETURNSNULLONNULLINP_PARAM, Boolean.toString(returnsNullOnNullInp));
    ret.addAll(colNamesAndTypes);

    // Add additional parameters here.

    return ret;
  }

  private static void addIfNotNull(StringPairList list, String key, String val) {
    if (null != val) {
      list.add(key, val);
    }
  }

  @Override
  public int compareTo(UDFParams o) {

    int val = compareWithNulls(functionName, o.functionName);
    if (val != 0) {
      return val;
    }

    val = compareWithNulls(externalName, o.externalName);
    if (val != 0) {
      return val;
    }

    val = compareWithNulls(language, o.language);
    if (val != 0) {
      return val;
    }

    val = compareWithNulls(returnType, o.returnType);
    if (val != 0) {
      return val;
    }

    val = compareWithNulls(retSpanLike, o.retSpanLike);
    if (val != 0) {
      return val;
    }

    val = new Boolean(deterministic).compareTo(new Boolean(o.deterministic));
    if (val != 0) {
      return val;
    }

    val = new Boolean(returnsNullOnNullInp).compareTo(new Boolean(o.returnsNullOnNullInp));
    if (val != 0) {
      return val;
    }

    Iterator<Pair<String, String>> it1 = colNamesAndTypes.iterator();
    Iterator<Pair<String, String>> it2 = o.colNamesAndTypes.iterator();
    while (it1.hasNext() && it2.hasNext()) {
      Pair<String, String> p1, p2;
      p1 = it1.next();
      p2 = it2.next();

      val = compareWithNulls(p1.first, p2.first);
      if (val != 0) {
        return val;
      }

      val = compareWithNulls(p1.second, p2.second);
      if (val != 0) {
        return val;
      }
    }
    if (it1.hasNext())
      return 1;
    if (it2.hasNext())
      return -1;

    return 0;
  }

  /** A version of compareTo() that handles null values properly. */
  @SuppressWarnings("unchecked")
  private <T> int compareWithNulls(T first, T second) {

    if (null == first) {
      if (null == second) {
        return 0;
      } else {
        // Nulls sort lower.
        return -1;
      }
    } else {
      return ((Comparable<T>) first).compareTo(second);
    }
  }

  /**
   * Generate the AQL boilerplate for the create function statement
   * 
   * @param stream where to print to
   * @param indent how much to indent what we print.
   */
  public void dumpAQL(PrintWriter stream, int indent) {

    AbstractAQLParseTreeNode.printIndent(stream, indent);
    // Function name is a NickLiteral. Need to make sure we write it as such
    stream.print("create function ");
    NickNode.dumpStrNick(stream, functionName);
    stream.print("(\n");

    boolean first = true;
    for (Pair<String, String> colNameAndType : colNamesAndTypes) {

      AbstractAQLParseTreeNode.printIndent(stream, indent + 1);
      if (first)
        first = false;
      else
        stream.printf(",");
      if (colNameAndType.first != null)
        stream.printf("%s %s", colNameAndType.first, colNameAndType.second);
      else
        stream.printf("%s", colNameAndType.second);
    }
    stream.printf(")\n");
    AbstractAQLParseTreeNode.printIndent(stream, indent);
    stream.printf("return %s", returnType);
    if (retSpanLike != null)
      stream.printf(" like %s", retSpanLike);
    stream.printf("\n");
    AbstractAQLParseTreeNode.printIndent(stream, indent);
    stream.printf("external_name '%s'\n", externalName);
    AbstractAQLParseTreeNode.printIndent(stream, indent);

    // Note that the AQL representation of the language is different from the internal
    // representation (i.e. "java" vs
    // "Java"). Can't fix this discrepancy without breaking backwards compatbility.
    stream.printf("language %s\n", language.getAqlTok());

    AbstractAQLParseTreeNode.printIndent(stream, indent);
    if (deterministic) {
      stream.printf("deterministic\n");
    } else
      stream.printf("not deterministic\n");
    AbstractAQLParseTreeNode.printIndent(stream, indent);
    if (returnsNullOnNullInp) {
      stream.printf("return null on null input\n");
    } else
      stream.printf("called on null input\n");
    stream.printf("\n");
  }

  public String getJarName() {

    return jarName;
  }

  /**
   * @return the className
   */
  public String getClassName() {
    return className;
  }

  /**
   * @return the methodName
   */
  public String getMethodName() {
    return methodName;
  }

  public boolean returnsBoolean() {
    return FieldType.BOOL_TYPE.equals(getReturnType());
    // return UDFunction.BOOLEAN_TYPE_LITERAL.equals (getReturnType ());
  }

  /**
   * Sets the module name of the udf.
   * 
   * @param moduleName name of the module where the function is declared
   */
  public void setModuleName(String moduleName) {
    this.moduleName = moduleName;
  }

  /**
   * Qualify any references to jar files with the current module's name
   * 
   * @param catalog poiner to the AQL catalog, which should have the current module's name
   */
  public void qualifyJarRefs(Catalog catalog) {
    this.jarName = ModuleUtils.prepareQualifiedName(catalog.getModuleName(), this.jarName);
  }

  @Override
  public String toString() {
    StringBuffer sb = new StringBuffer("UDFParams(\n");
    sb.append(String.format("\tfunctionName = %s,\n", functionName));
    sb.append(String.format("\tjar:class!method = %s:%s!%s,\n", jarName, className, methodName));
    sb.append(String.format("\treturnType = %s,\n", returnType));
    sb.append(String.format("\targs = %s,\n", colNamesAndTypes));
    sb.append(String.format(")"));
    return sb.toString();

  }
}
