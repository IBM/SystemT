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
package com.ibm.avatar.aql.catalog;

import java.util.ArrayList;
import java.util.Arrays;

import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.algebra.function.base.ScalarUDF;
import com.ibm.avatar.algebra.function.base.UDFPredicateWrapper;
import com.ibm.avatar.algebra.util.udf.UDFParams;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.api.tam.FunctionMetadata;
import com.ibm.avatar.api.tam.ModuleMetadata;
import com.ibm.avatar.api.tam.Param;
import com.ibm.avatar.aql.CreateFunctionNode;
import com.ibm.avatar.aql.FuncLanguage;
import com.ibm.avatar.aql.ImportFuncNode;
import com.ibm.avatar.aql.ImportModuleNode;
import com.ibm.avatar.aql.AQLParseTreeNode;
import com.ibm.avatar.aql.ImportNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.tam.ModuleUtils;

/**
 */
public class ScalarUDFCatalogEntry extends ScalarFuncCatalogEntry {

  /**
   * Original parse tree node -- can be create function, or import function, or import module
   * statement.
   */
  private AQLParseTreeNode parseTreeNode = null;

  /**
   * Parameters sufficient to reconstitute and invoke the function.
   */
  private UDFParams udfparams = null;

  /**
   * Constructor for use during AQL compilation; populates the catalog entry from values stored
   * inside the original parse tree node.
   * 
   * @param node parse tree node for the AQL statement (create function, import function, etc.) that
   *        caused this node to be added to the catalog, or null if this constructor is being called
   *        at load time
   * @param fqName fully-qualified name of the function
   */
  public ScalarUDFCatalogEntry(CreateFunctionNode node, String fqName) {
    super(computeImplClass(node), node.getFunctionName(), fqName);

    this.parseTreeNode = node;
  }

  /**
   * Constructor for use during operator graph loading. Populates the catalog entry from values
   * stored inside the AOG file.
   * 
   * @param funcname fully-qualified AQL name of the function
   * @param implClass information about the class that implements this user-defined scalar function
   * @param params additional information necessary to instantiate the function
   */
  public ScalarUDFCatalogEntry(String funcname, Class<? extends ScalarFunc> implClass,
      UDFParams params) {
    super(implClass, funcname, funcname);

    this.udfparams = params;
  }

  /**
   * Constructor for catalog entries created when importing from another module
   * 
   * @param fmd information about the function, as loaded from the TAM file
   * @param node the 'import function' node
   * @throws ParseException if the metadata can't be parsed
   */
  public ScalarUDFCatalogEntry(FunctionMetadata fmd, ImportNode node) throws ParseException {
    super(computeImplClass(fmd), fmd.getFunctionName(), computeQualifiedName(fmd, node));

    this.parseTreeNode = node;

    setImportingNode(node);
  }

  /**
   * @return the original AQL parse tree node for the AQL statement (create or import) that pulled
   *         this function into the current module's namespace
   */
  public AQLParseTreeNode getParseTreeNode() {
    return parseTreeNode;
  }

  /**
   * @param catalog AQL catalog, for function metadata lookup
   * @return object that can be used to instantiate and evaluate the indicated function
   * @throws ParseException if there is an error parsing type strings inside the parse tree node
   */
  public UDFParams toUDFParams(Catalog catalog) throws ParseException {
    if (null != udfparams) {
      // UDFParams object was provided in constructor
      return udfparams;
    } else {
      return nodeToParams(catalog, parseTreeNode, getName());
    }
  }

  /**
   * Utility function to generate UDFParams objects from catalog and parse tree information. Shared
   * with {@link TableUDFCatalogEntry} class.
   * 
   * @param catalog AQL catalog, for looking up function metadata from other modules
   * @param node parse tree node for the create or import function stmt, or null if this method is
   *        being called during operator graph instantiation.
   * @param functionName name of the function in question (not always present in parse tree node...)
   * @return UDFParams object that contains all parameters necessary to instantiate and use the
   *         function
   * @throws ParseException if there is an error parsing type strings inside the parse tree node
   */
  protected static UDFParams nodeToParams(Catalog catalog, AQLParseTreeNode node,
      String functionName) throws ParseException {
    // Information is stored differently depending on whether the parse tree node came from
    if (node instanceof CreateFunctionNode) {
      CreateFunctionNode cfn = (CreateFunctionNode) node;
      return cfn.getUDFParams();
    } else if (node instanceof ImportFuncNode || node instanceof ImportModuleNode) {
      // Imported function; need to generate a UDFParams object from module metadata.
      String moduleName;

      // Determine the name of the module where the original "create function" stmt resides.
      // Note that this module is NOT the one returned by parseTreeNode.getModuleName()!!!
      if (node instanceof ImportFuncNode) {
        ImportFuncNode ifn = (ImportFuncNode) node;
        moduleName = ifn.getFromModule().getNickname();
      } else if (node instanceof ImportModuleNode) {
        ImportModuleNode imn = (ImportModuleNode) node;
        moduleName = imn.getImportedModuleName().getNickname();
      } else {
        throw new FatalInternalError("Should never happen");
      }

      // Function names in module metadata are qualified with the module name.
      String fqFunctionName = ModuleUtils.prepareQualifiedName(moduleName, functionName);

      // Fetch metadata information for the specified module, then get the metadata for the function
      // itself.
      ModuleMetadata moduleMeta = catalog.lookupMetadata(moduleName);
      FunctionMetadata fmd = moduleMeta.getFunctionMetadata(fqFunctionName);

      if (null == fmd) {
        throw new FatalInternalError("Module metadata for module '%s' "
            + "does not contain function metadata for function %s'.  Available functions are: %s",
            moduleName, functionName, Arrays.toString(moduleMeta.getExportedFunctions()));
      }

      // Define variables for all the information we need to collect
      String externalName = fmd.getExternalName();
      FuncLanguage language = fmd.getLanguage();
      String returnType = fmd.getReturnType();
      String retSpanLike = fmd.getReturnLikeParam();

      boolean deterministic = fmd.isDeterministic();
      boolean returnsNullOnNullInp = fmd.returnsNullOnNullInput();

      ArrayList<Pair<String, String>> colNamesAndTypes = new ArrayList<Pair<String, String>>();
      for (Param param : fmd.getParameters()) {
        Pair<String, String> nameAndType =
            new Pair<String, String>(param.getName(), param.getType());
        colNamesAndTypes.add(nameAndType);
      }

      return new UDFParams(functionName, externalName, language, returnType, retSpanLike,
          deterministic, returnsNullOnNullInp, colNamesAndTypes);
    } else {
      throw new FatalInternalError("Don't know how to generate UDFParams from parse tree node %s",
          node);
    }
  }

  /**
   * @param node parse tree node for a "create function" statement for a user-defined scalar
   *        function
   * @return identity of the wrapper class that will implement the function at runtime
   */
  protected static Class<? extends ScalarFunc> computeImplClass(CreateFunctionNode node) {
    if (null == node) {
      throw new FatalInternalError("Null pointer passed to computeImplClass()");
    }

    if (node.returnsBoolean()) {
      return UDFPredicateWrapper.class;
    } else {
      return ScalarUDF.class;
    }
  }

  /**
   * @param fmd metadata about a function, as stored inside a TAM file's metadata section
   * @return identity of the wrapper class that will implement the function at runtime
   * @throws ParseException if the function metadata is corrupted in some way
   */
  protected static Class<? extends ScalarFunc> computeImplClass(FunctionMetadata fmd)
      throws ParseException {
    if (null == fmd) {
      throw new FatalInternalError("Null pointer passed to computeImplClass()");
    }

    FieldType returnType = FieldType.stringToFieldType(fmd.getReturnType());
    if (returnType.getIsBooleanType()) {
      return UDFPredicateWrapper.class;
    } else {
      return ScalarUDF.class;
    }
  }

  /**
   * Compute the fully-qualified name of a function imported via an import statement.
   * 
   * @param fmd information about the function
   * @param node parse tree node for the import stmt
   * @return canonical, fully-qualified name of the function in question. Note that the returned
   *         value may not be a valid function name within the context of the current module.
   */
  protected static String computeQualifiedName(FunctionMetadata fmd, AQLParseTreeNode node) {
    String moduleName;
    String realFuncName;
    if (node instanceof ImportFuncNode) {
      // "import function" stmt. May have an alias, which we ignore and go to the real name.
      ImportFuncNode ifn = (ImportFuncNode) node;
      moduleName = ifn.getFromModule().getNickname();
      realFuncName = ifn.getNodeName().getNickname();

    } else if (node instanceof ImportModuleNode) {
      // "import module" stmt. The statement doesn't contain the function name, so we pull the name
      // from the function
      // metadata.
      ImportModuleNode imn = (ImportModuleNode) node;
      moduleName = imn.getImportedModuleName().getNickname();
      realFuncName = fmd.getFunctionName();
    } else {
      throw new FatalInternalError("Unexpected type of parse tree node %s",
          node.getClass().getName());
    }

    return ModuleUtils.prepareQualifiedName(moduleName, realFuncName);

  }

  @Override
  protected AQLParseTreeNode getNode() {
    return this.parseTreeNode;
  }

}
