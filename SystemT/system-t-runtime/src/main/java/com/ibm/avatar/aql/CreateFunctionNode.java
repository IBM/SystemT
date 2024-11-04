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
package com.ibm.avatar.aql;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.algebra.util.file.SearchPath;
import com.ibm.avatar.algebra.util.pmml.PMMLUtil;
import com.ibm.avatar.algebra.util.udf.UDFParams;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.aql.compiler.ParseToCatalog;
import com.ibm.avatar.aql.doc.AQLDocComment;
import com.ibm.avatar.aql.tam.ModuleUtils;

/**
 * Top-level parse tree node for a
 * 
 * <pre>
 * create function
 * </pre>
 * 
 * statement.
 * 
 */
public class CreateFunctionNode extends TopLevelParseTreeNode

{

  private final UDFParams udfparams;
  private final NickNode functionName;

  /**
   * Parse tree node for the argument of the RETURN clause
   */
  private final TypeNode returnType;

  /**
   * Raw parse tree nodes for the column names and types.
   */
  private final ArrayList<Pair<NickNode, TypeNode>> colNamesAndTypes;

  /** The AQL Doc comment (if any) attached to this parse tree node. */
  private AQLDocComment comment;

  /**
   * Main constructor for this parse tree node
   * 
   * @param functionName parse tree node for the argument of the NAME clause
   * @param externalName argument of the EXTERNAL_NAME clause (String, not parse tree node)
   * @param language argument of the LANGUAGE clause (currently hard-coded to "java")
   * @param returnType parse tree node for the argument of the RETURN clause
   * @param retSpanLike (optional) parse tree node for the LIKE clause inside the RETURN clause
   * @param deterministic true if the function is marked DETERMINISTIC, false otherwise
   * @param returnsNullOnNullInp true if the function declaration contains the RETURN NULL ON NULL
   *        INPUT clause
   * @param colNamesAndTypes parse tree nodes for the function's arguments list
   * @param containingFileName file containing this statement
   * @param origTok location of the statement within the file
   * @throws ParseException if the type information cannot be parsed
   */
  public CreateFunctionNode(NickNode functionName, String externalName, FuncLanguage language,
      TypeNode returnType, String retSpanLike, boolean deterministic, boolean returnsNullOnNullInp,
      ArrayList<Pair<NickNode, TypeNode>> colNamesAndTypes, String containingFileName,
      Token origTok) throws ParseException {
    // set error location info
    // this used to be the function name -- changed in v2.1.1 to the CREATE statement
    super(containingFileName, origTok);

    // Parser code has passed in null ptrs in the past; verify that the problem doesn't happen
    // again.
    if (null == functionName) {
      throw new NullPointerException("Null functionName ptr passed to CreateFunctionNode");
    }
    if (null == externalName) {
      throw new NullPointerException("Null externalName ptr passed to CreateFunctionNode");
    }
    if (null == language) {
      throw new NullPointerException("Null language ptr passed to CreateFunctionNode");
    }
    if (null == returnType) {
      throw new NullPointerException("Null returnType ptr passed to CreateFunctionNode");
    }
    if (null == colNamesAndTypes) {
      throw new NullPointerException("Null colNamesAndTypes ptr passed to CreateFunctionNode");
    }

    this.functionName = functionName;
    this.returnType = returnType;
    this.colNamesAndTypes = colNamesAndTypes;

    // UDFParams expects the column types as strings, so convert them.
    ArrayList<Pair<String, String>> convertedColNamesAndTypes = convertSchema(colNamesAndTypes);

    udfparams = new UDFParams(functionName.getNickname(), externalName, language,
        returnType.getTypeString(), retSpanLike, deterministic, returnsNullOnNullInp,
        convertedColNamesAndTypes);
  }

  /**
   * Copy constructor. Performs a deep copy where applicable.
   * 
   * @param orig node to duplicate
   */
  public CreateFunctionNode(CreateFunctionNode orig) {
    super((TopLevelParseTreeNode) orig);

    // Deep copy of function parameters
    colNamesAndTypes = new ArrayList<Pair<NickNode, TypeNode>>();
    for (Pair<NickNode, TypeNode> origPair : orig.colNamesAndTypes) {

      // Important to deep-copy names in case downstream rewrites change them.
      NickNode nick = new NickNode(origPair.first);

      // Assume that TypeNodes are immutable.
      TypeNode type = origPair.second;

      Pair<NickNode, TypeNode> pair = new Pair<NickNode, TypeNode>(nick, type);

      colNamesAndTypes.add(pair);
    }

    // Comment is optional
    comment = null == orig.comment ? null : new AQLDocComment(orig.comment);

    functionName = new NickNode(orig.functionName);

    // Assume TypeNodes are immmutable
    returnType = orig.returnType;

    udfparams = new UDFParams(orig.udfparams);

  }

  private ArrayList<Pair<String, String>> convertSchema(
      ArrayList<Pair<NickNode, TypeNode>> colNamesAndTypes) throws ParseException {
    ArrayList<Pair<String, String>> convertedColNamesAndTypes =
        new ArrayList<Pair<String, String>>();
    for (Pair<NickNode, TypeNode> pair : colNamesAndTypes) {
      convertedColNamesAndTypes
          .add(new Pair<String, String>(pair.first.getNickname(), pair.second.getTypeString()));
    }
    return convertedColNamesAndTypes;
  }

  @Override
  public void setModuleName(String moduleName) {
    super.setModuleName(moduleName);

    // cascade the module name to function parameters
    if (this.udfparams != null && (false == ModuleUtils.isGenericModule(moduleName))) {
      this.udfparams.setModuleName(moduleName);
    }

  }

  @Override
  public List<com.ibm.avatar.aql.ParseException> validate(Catalog catalog) {
    List<ParseException> errors = new ArrayList<ParseException>();

    // The following checks used to be done in the AQL parser. Checks were moved here to simplify
    // the grammar.
    // These checks should be REMOVED when the Span and List types are de-parametrized.
    {
      // Pull out the argument of the "span like" or "list like" clause.
      String retSpanLike = udfparams.getRetSpanLike();

      // Dereference the type of the "like" argument.
      TypeNode retSpanLikeType = null;
      if (null != retSpanLike) {
        boolean keepGoing = true;
        for (int i = 0; keepGoing && i < colNamesAndTypes.size(); i++) {
          Pair<NickNode, TypeNode> p = colNamesAndTypes.get(i);
          if (retSpanLike.equals(p.first.getNickname())) {
            retSpanLikeType = p.second;
            keepGoing = false;
          }
        }
      }

      // if (returnType.isSpan ()) {
      // if (retSpanLike == null) {
      // errors.add (AQLParserBase.makeException (
      // "For Span and Text return types, the parameter on which the span is built must be specified
      // using the LIKE clause.",
      // returnType.getOrigTok ()));
      // }
      // else if (retSpanLikeType == null) {
      // errors.add (AQLParserBase.makeException (String.format ("Input parameter '%s' does not
      // exist.", retSpanLike),
      // returnType.getOrigTok ()));
      // }
      // else if (!retSpanLikeType.isSpan ()) {
      // errors.add (AQLParserBase.makeException (String.format (
      // "Return Span or Text type is specified based on the input parameter '%s', which is not of
      // type Span.",
      // retSpanLike), returnType.getOrigTok ()));
      // }
      // }
      // else
      if (returnType.isScalarList()) {

        if (retSpanLike == null) {
          errors.add(AQLParserBase.makeException(
              "For ScalarList return types, the parameter based on which the scalar list type is built must be specified using the LIKE clause.",
              returnType.getOrigTok()));
        } else if (retSpanLikeType == null) {
          errors.add(AQLParserBase.makeException(
              String.format("Input parameter '%s' does not exist.", retSpanLike),
              returnType.getOrigTok()));
        } else if (!retSpanLikeType.isScalarList()) {
          errors.add(AQLParserBase.makeException(String.format(
              "Return scalar list type is specified based on the input parameter '%s', which is not of scalar list type.",
              retSpanLike), returnType.getOrigTok()));
        }
      } else {
        // No checks here if the return type is not a span or list
      }

    } // END checks moved here from AQLParser.jj

    if (FuncLanguage.Java.equals(udfparams.getLanguage())) {
      // Basic checks for Java UDFs

      // Null check for the jar name containing the UDF
      String jarName = udfparams.getJarName();
      if (jarName == null) {
        errors.add(AQLParserBase.makeException(getOrigTok(), "Jarname not specified for UDF %s",
            udfparams.getFunctionName()));
        // Skip the tests that follow; they will fail with nonsensical errors when there is no jar
        // name parameter.
        return ParseToCatalog.makeWrapperException(errors, getContainingFileName());
      }

      // Check if the jar file exists
      SearchPath searchPath = catalog.getUDFJarPath();
      File file = searchPath.resolve(jarName);

      if (null == file) {
        errors.add(AQLParserBase.makeException(getOrigTok(), "jar file '%s' not found in path '%s'",
            jarName, searchPath));
      }
    }

    if (FuncLanguage.PMML.equals(udfparams.getLanguage())) {
      // UDF represented as a PMML file

      boolean inputsInvalid = false;
      if (1 != colNamesAndTypes.size()) {
        inputsInvalid = true;
      } else {
        // Right number of arguments; check name and type.
        // There should be one argument, called params, of type record locator
        Pair<NickNode, TypeNode> arg = colNamesAndTypes.get(0);
        if (false == PMMLUtil.MODEL_PARAMS_ARG_NAME.equals(arg.first.getNickname())
            || false == arg.second.isLocator()) {
          inputsInvalid = true;
        }
      }
      if (inputsInvalid) {
        errors.add(AQLParserBase.makeException(getOrigTok(),
            "Unsupported input schema for PMML-based function.  "
                + "The function must take one argument, of type table(...) as locator, with the name '%s'",
            PMMLUtil.MODEL_PARAMS_ARG_NAME));
      }

      if (false == returnType.isTable()) {
        errors.add(AQLParserBase.makeException(getOrigTok(),
            "Unsupported return type for PMML-based function.  "
                + "The function must return a value of type table(...)"));
      }

      // Location of PMML file goes into "external name" field
      String pmmlFileName = udfparams.getExternalName();

      SearchPath searchPath = catalog.getUDFJarPath();
      File pmmlFile = searchPath.resolve(pmmlFileName);

      if (null == pmmlFile) {
        errors
            .add(
                AQLParserBase.makeException(getOrigTok(),
                    "PMML file '%s' not found in path '%s'.  Ensure that the target file exists "
                        + "and that the file name is spelled correctly.",
                    pmmlFileName, searchPath));
      }
    }

    return ParseToCatalog.makeWrapperException(errors, getContainingFileName());
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.avatar.aql.Node#dump(java.io.PrintWriter, int)
   */
  @Override
  public void dump(PrintWriter stream, int indent) {
    if (udfparams != null)
      udfparams.dumpAQL(stream, indent);
    stream.print(";\n");
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.avatar.aql.Node#reallyCompareTo(com.ibm.avatar.aql.Node)
   */
  @Override
  protected int reallyCompareTo(AQLParseTreeNode o) {
    CreateFunctionNode other = (CreateFunctionNode) o;
    return udfparams.compareTo(other.udfparams);
  }

  public String getFunctionName() {
    if (functionName != null)
      return prepareQualifiedName(functionName.getNickname());
    else
      return null;
  }

  /**
   * Returns NickNode for function name
   * 
   * @return functionName nick node
   */
  public NickNode getFunctionNameNode() {
    return functionName;
  }

  /**
   * Returns the unqualified name of the function
   * 
   * @return name of the function without module prefix
   */
  public String getUnqualifiedName() {
    return functionName.getNickname();
  }

  /**
   * @return token at the location where problems with this function definition should be reported.
   */
  @Deprecated
  public Token getErrorTok() {
    return functionName.getOrigTok();
  }

  public void toAOG(PrintWriter stream, int indent) {

    // Now generate a function definition statement
    printIndent(stream, indent);
    stream.print("CreateFunction(\n");

    // Output the parameters
    udfparams.toKeyValuePairs().toPerlHash(stream, indent + 1, false);

    printIndent(stream, indent);
    stream.print(");\n\n");

  }

  public String getJarName() {
    return udfparams.getJarName();
  }

  // public String getReturnType ()
  // {
  // return udfparams.getReturnType ();
  // }

  public TypeNode getReturnType() {
    return returnType;
  }

  // public void jarToAOG (PrintWriter stream, int indent, SearchPath searchPath) throws
  // ParseException
  // {
  // // Now output the jar contents
  // printIndent (stream, indent);
  // stream.print ("Jar(\n");
  //
  // // Validation for non null jar name and existence of jar file is moved to parser.
  // String jarName = udfparams.getJarName ();
  //
  // printIndent (stream, indent + 1);
  // stream.print ("\"" + jarName + "\",\n");
  //
  // // open the jar file
  // File file = searchPath.resolve (jarName);
  //
  // String encoded = "";
  //
  // try {
  // // encoded = Base64.encodeBytes(getBytesFromFile(file), Base64.GZIP | Base64.DO_BREAK_LINES);
  // encoded = new String (Base64.encodeBase64Chunked (getBytesFromFile (file)), "UTF-8");
  // }
  // catch (IOException e) {
  // throw new ParseException ("Not able to pack jar file into aog " + jarName + "Error : " +
  // e.toString ());
  // }
  //
  // stream.format ("\"%s\"", encoded);
  //
  // printIndent (stream, indent);
  // stream.print (");\n\n");
  //
  // }

  // Returns the contents of the file in a byte array.
  public static byte[] getBytesFromFile(File file) throws IOException {
    InputStream is = new FileInputStream(file);

    try {
      // Get the size of the file
      long length = file.length();

      // You cannot create an array using a long type.
      // It needs to be an int type.
      // Before converting to an int type, check
      // to ensure that file is not larger than Integer.MAX_VALUE.
      if (length > Integer.MAX_VALUE) {
        throw new IOException("File is too large " + file.getName());// File is too
        // large
      }

      // Create the byte array to hold the data
      byte[] bytes = new byte[(int) length];

      // Read in the bytes
      int offset = 0;
      int numRead = 0;
      while (offset < bytes.length
          && (numRead = is.read(bytes, offset, bytes.length - offset)) >= 0) {
        offset += numRead;
      }

      // Ensure all the bytes have been read in
      if (offset < bytes.length) {
        throw new IOException("Could not completely read file " + file.getName());
      }

      return bytes;
    } finally {
      // Close the input stream and return bytes
      is.close();
    }
  }

  public boolean returnsBoolean() {

    return udfparams.returnsBoolean();

  }

  /**
   * Set the AQL Doc comment attached to this statement.
   * 
   * @param comment the AQL doc comment attached to this statement
   */
  public void setComment(AQLDocComment comment) {
    this.comment = comment;
  }

  /**
   * Get the AQL Doc comment attached to this node.
   * 
   * @return the AQL Doc comment attached to this node; null if this node does not have an AQL Doc
   *         comment
   */
  public AQLDocComment getComment() {
    return comment;
  }

  @Override
  public void qualifyReferences(Catalog catalog) {
    // No action

  }

  /**
   * Return <code>true</code>, if the udf returns same value for the same set of input.
   * 
   * @return <code>true</code>, if the function is deterministic.
   */
  public boolean isDeterministic() {
    return udfparams.isDeterministic();
  }

  /**
   * Return the external name for the user defined function.
   * 
   * @return string containing the external name.
   */
  public String getExternalName() {
    return udfparams.getExternalName();
  }

  /**
   * Override the external name string from the original function declaration
   * 
   * @param externalNameStr new external name string to replace the original contents of the
   *        external_name clause
   */
  public void setExternalName(String externalName) {
    udfparams.setExternalName(externalName);
  }

  /**
   * Return the language in which udf is written
   * 
   * @return language in which udf is written.
   */
  public FuncLanguage getLanguage() {
    return udfparams.getLanguage();
  }

  /**
   * Override the function's implementation language as declared with a different value.
   * 
   * @param lang new programming language for the function
   */
  public void setLanguage(FuncLanguage language) {
    udfparams.setLanguage(language);
  }

  /**
   * Return pairs of parameter name and parameter type to the udf.
   * 
   * @return list of parameter name and their type to the udf.
   */
  public ArrayList<Pair<String, String>> getParamNamesAndType() {
    return udfparams.getColNamesAndTypes();
  }

  /**
   * Return <code>true</code>, if udf is not invoked on null input parameters.
   * 
   * @return <code>true</code>, if udf is not invoked on null input parameters.
   */
  public boolean isReturnsNullOnNullInp() {
    return udfparams.isReturnsNullOnNullInp();
  }

  /**
   * @return the internal, low-level representation of the function
   */
  public UDFParams getUDFParams() {
    return udfparams;
  }

  /**
   * Qualify any references to jar files with the current module's name.
   * 
   * @param catalog AQL catalog, which should have the name of the current module
   */
  public void qualifyJarRefs(Catalog catalog) {
    // Jar location is stored inside the UDFParams object.
    udfparams.qualifyJarRefs(catalog);
  }

}
