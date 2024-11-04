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

package com.ibm.avatar.aql.catalog;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.util.udf.UDFParams;
import com.ibm.avatar.algebra.util.udf.UDFunction;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.api.exceptions.TableFunctionWrongTypeException;
import com.ibm.avatar.api.exceptions.TableUDFException;
import com.ibm.avatar.api.tam.FunctionMetadata;
import com.ibm.avatar.api.udf.TableUDFBase;
import com.ibm.avatar.aql.AQLParseTreeNode;
import com.ibm.avatar.aql.AQLParserBase;
import com.ibm.avatar.aql.CreateFunctionNode;
import com.ibm.avatar.aql.ExtendedParseException;
import com.ibm.avatar.aql.ImportNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.Token;

/** Catalog entry for a user-defined table funcion. */
public class TableUDFCatalogEntry extends TableFuncCatalogEntry {

  /** Schema of tuples that this table function returns when called. */
  private TupleSchema returnSchema;

  /** Detailed information about the UDF */
  private final UDFParams params;

  /**
   * Constructor for entries created via a "create function" statement. Called from the AQL parser.
   * 
   * @param name the unqualified name of the table function
   * @param node parse tree node for the create function statement for this table function
   * @param catalog AQL catalog, for looking up information about functions declared in other
   *        modules
   * @throws ParseException if there is a failure decoding the information the parser put into the
   *         parse tree node
   */
  protected TableUDFCatalogEntry(CreateFunctionNode node, Catalog catalog) throws ParseException {
    super(null != node ? node.getFunctionName() : "");

    if (null == node) {
      throw new FatalInternalError("Null node pointer passed to %s constructor",
          this.getClass().getName());
    }

    parseTreeNode = node;

    // Convert the parse tree node to a real schema.
    returnSchema = node.getReturnType().toTupleSchema();

    // Scalar and table functions share the same core UDF code, so we can use the conversion code
    // from the scalar side
    // here.
    params = ScalarUDFCatalogEntry.nodeToParams(catalog, parseTreeNode, getName());

    // Table functions are incompatible with the "returns null on null input" clause, since they
    // must always return
    // something.
    if (params.isReturnsNullOnNullInp()) {
      throw AQLParserBase.makeException(node.getOrigTok(), "Table functions cannot return null.  "
          + "Please remove the RETURN NULL ON NULL INPUT clause from the function declaration for function '%s'.",
          node.getFunctionName());
    }
  }

  /**
   * Constructor for catalog entries created when importing from another module
   * 
   * @param fmd information about the function, as loaded from the TAM file
   * @param node parse tree node for the import statement
   * @throws com.ibm.avatar.aog.ParseException
   * @throws ParseException if the metadata can't be parsed
   */
  public TableUDFCatalogEntry(FunctionMetadata fmd, ImportNode node, Catalog catalog)
      throws ParseException {
    super(ScalarUDFCatalogEntry.computeQualifiedName(fmd, node));

    this.parseTreeNode = node;

    setImportingNode(node);

    // Parse return type, which is encoded as a string in the metadata
    try {
      returnSchema = new TupleSchema(fmd.getReturnType());
    } catch (com.ibm.avatar.aog.ParseException e) {
      throw AQLParserBase.makeException(e, node.getOrigTok(),
          "Error reading table function return type " + "for function '%s' from module metadata.",
          fmd.getFunctionName());
    }

    params = ScalarUDFCatalogEntry.nodeToParams(catalog, node, super.getName());
  }

  /**
   * Constructor for use during operator graph loading. Populates the catalog entry from values
   * stored inside the AOG file.
   * 
   * @param funcname AQL name of the function
   * @param params additional information necessary to instantiate the function
   */
  public TableUDFCatalogEntry(String funcname, UDFParams params) {
    super(funcname);

    this.params = params;
  }

  /**
   * @param catalog AQL catalog, for function metadata lookup
   * @return object that can be used to instantiate and evaluate the indicated function
   */
  public UDFParams toUDFParams(Catalog catalog) {
    return params;
  }

  @Override
  public TupleSchema getArgumentSchema() throws ParseException {
    return params.getArgsAsSchema();
  }

  @Override
  public TupleSchema getReturnedSchema() {
    return returnSchema;
  }

  @Override
  public List<ParseException> validateReturnedSchema(Catalog catalog) {
    List<ParseException> ret = new ArrayList<ParseException>();
    if (false == (parseTreeNode instanceof CreateFunctionNode)) {
      // SPECIAL CASE: Table function being called from a module other than the module in which it
      // was defined.
      // In this case, we skip validation, since validation was done when the source module was
      // compiled, and we don't
      // want to read any parts of the compiled module besides the metadata.
      return ret;
      // END SPECIAL CASE
    } else {
      // Table func being called within the module it was defined in. Perform compile-time
      // validation of the returned
      // schema.
      CreateFunctionNode cfn = (CreateFunctionNode) parseTreeNode;
      Token origTok = cfn.getOrigTok();
      try {
        // Create an instance of the function's implementing class, so that we can interrogate the
        // instance about the
        // correctness of the schema.
        UDFunction udf = new UDFunction(origTok, params);

        // Get enough information to be able to fire up a ClassLoader.
        AbstractJarCatalogEntry jarEntry = catalog.lookupJar(params.getJarName());
        udf.loadClass(jarEntry.makeClassLoader(this.getClass().getClassLoader()));

        Object instance = udf.getInstance();
        if (false == (instance instanceof TableUDFBase)) {
          ret.add(new TableFunctionWrongTypeException(origTok, params.getFunctionName(),
              params.getClassName()));
        } else {
          TableUDFBase tudf = (TableUDFBase) instance;
          TupleSchema returnSchema = getReturnedSchema();
          TupleSchema inputSchema = getArgumentSchema();
          Method methodInfo = udf.getMethod();
          boolean compileTime = true;
          try {
            // Note that compile-time validation uses the declared input schema as the "runtime"
            // input schema.
            // At some point in the future, we may want to actually bind the function at compile
            // time, so as to get a
            // more complete validation.
            tudf.validateSchema(inputSchema, inputSchema, returnSchema, methodInfo, compileTime);
          } catch (TableUDFException e) {

            // Wrap the exception so that we get location info.
            ParseException wrappedException = AQLParserBase.makeException(e, origTok,
                "Error validating output schema of function %s: %s", getName(), e.getMessage());
            ExtendedParseException epe =
                new ExtendedParseException(wrappedException, new File(cfn.getContainingFileName()));
            // System.err.printf("Exception: %s\n", epe);

            ret.add(epe);
          }
        }
      } catch (Exception e) {
        // Add file information
        ParseException pe = AQLParserBase.makeException(e, origTok, e.toString());
        ExtendedParseException epe =
            new ExtendedParseException(pe, new File(cfn.getContainingFileName()));
        ret.add(epe);
        // ret.add (AQLParserBase.makeException (e, origTok, e.toString ()));
      }
      return ret;
    }

  }

  @Override
  protected AQLParseTreeNode getNode() {
    return parseTreeNode;
  }

}
