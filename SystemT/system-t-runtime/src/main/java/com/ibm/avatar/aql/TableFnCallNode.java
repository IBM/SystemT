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
package com.ibm.avatar.aql;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.api.exceptions.TableFunctionNotFoundException;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.aql.catalog.TableFuncCatalogEntry;
import com.ibm.avatar.aql.tam.ModuleUtils;

/**
 * A table function call:
 * 
 * <pre>
 * [function]([args]);
 * </pre>
 * 
 * Can be a built-in table function or a user-defined table function.
 * 
 */
public abstract class TableFnCallNode extends AbstractAQLParseTreeNode {

  private String functionName;

  protected ArrayList<RValueNode> args;

  /**
   * Main constructor
   * 
   * @param funcNameNode AQL parse tree node for the function name
   * @param nargs number of arguments to the function call (arguments set later with
   *        {@link #setArg(int, RValueNode)}
   */
  public TableFnCallNode(NickNode funcNameNode, int nargs) {
    // set error location info
    super(funcNameNode.getContainingFileName(), funcNameNode.getOrigTok());

    this.functionName = funcNameNode.getNickname();
    args = new ArrayList<RValueNode>();
    for (int i = 0; i < nargs; i++) {
      args.add(null);
    }
  }

  @Override
  public List<ParseException> validate(Catalog catalog) {
    ArrayList<ParseException> errors = new ArrayList<ParseException>();

    // Validate function inputs.
    for (RValueNode arg : args) {
      if (arg instanceof ScalarFnCallNode) {
        ScalarFnCallNode funcArg = (ScalarFnCallNode) arg;
        errors.addAll(funcArg.validate(catalog));
      }
    }

    // Start by looking up the function in question in the catalog.
    TableFuncCatalogEntry entry = catalog.lookupTableFunc(functionName);
    if (null == entry) {
      errors.add(new TableFunctionNotFoundException(getOrigTok(), functionName));
    }

    // If we find information about the function, perform additional validations
    if (null != entry) {

      // Verify that the number of arguments of the function call matches with what the catalog
      // entry says to expect.
      TupleSchema argSchema = null;
      try {
        argSchema = entry.getArgumentSchema();
      } catch (ParseException e1) {
        errors.add(e1);
      }

      if (null != argSchema && argSchema.size() != args.size()) {
        ParseException e = new ParseException(String.format(
            "Number of arguments for %s() call "
                + "does not match function declaration (%d != %d).",
            functionName, args.size(), argSchema.size()));
        errors.add(e);
      }

      // Additional argument validation needs to wait until the type inference phase of compilation.
    }

    return errors;
  }

  /**
   * @return parse tree nodes for the arguments of the function call
   */
  public ArrayList<RValueNode> getArgs() {
    return args;
  }

  /**
   * Generate an AOG spec that will evaluate this table function call.
   * 
   * @param stream output stream to write the AOG to
   * @param indent how far to indent
   * @param catalog AQL catalog, for looking up necessary function metadata
   */
  public abstract void generateAOG(PrintWriter stream, int indent, Catalog catalog)
      throws ParseException;

  /**
   * @param c pointer to the AQL parser's catalog, for fetching any necessary information about
   *        views, tables, etc.
   * @return list of the column names returned by this table functions.
   * @throws ParseException if the column names cannot be computed due to a problem with the AQL
   */
  public String[] getColNames(Catalog c) throws ParseException {
    // Look up the catalog entry for the appropriate CREATE FUNCTION statement.
    TableFuncCatalogEntry entry = c.lookupTableFunc(getFuncName());
    if (null == entry) {
      throw new TableFunctionNotFoundException(getOrigTok(), functionName);
    }

    ArrayList<String> colNames = entry.getColNames();
    String[] ret = new String[colNames.size()];
    ret = colNames.toArray(ret);
    return ret;
  }

  protected void setArg(int ix, RValueNode arg) {
    args.set(ix, arg);
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    printIndent(stream, indent);
    stream.printf("%s(", functionName);

    if (0 == args.size()) {
      // No arguments.
      stream.print(")");
    } else {
      // Arguments
      stream.print("\n");
      for (int i = 0; i < args.size(); i++) {
        AQLParseTreeNode n = args.get(i);
        n.dump(stream, indent + 1);
        if (i < args.size() - 1) {
          stream.print(",\n");
        }
      }

      stream.print("\n");
      printIndent(stream, indent);
      stream.print(")");
    }
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(functionName);
    sb.append("(");
    for (int i = 0; i < args.size(); i++) {
      sb.append(args.get(i).toString());
      if (i < args.size() - 1) {
        sb.append(", ");
      }
    }
    sb.append(")");
    return sb.toString();
  }

  public String getFuncName() {
    return functionName;
  }

  @Override
  public int reallyCompareTo(AQLParseTreeNode o) {

    TableFnCallNode other = (TableFnCallNode) o;
    // Compare on function name, then number of args, then the args
    // themselves.
    int val = functionName.compareTo(other.functionName);
    if (val != 0) {
      return val;
    }
    val = args.size() - other.args.size();
    if (val != 0) {
      return val;
    }
    for (int i = 0; i < args.size(); i++) {
      AQLParseTreeNode myArg = args.get(i);
      if (myArg == null) {
        break;
      }
      AQLParseTreeNode otherArg = other.args.get(i);
      val = myArg.compareTo(otherArg);
      if (val != 0) {
        return val;
      }
    }
    return val;

  }

  /**
   * Compute the names of the relations that this table function call references.
   * 
   * @param accum object where the set is built up. We use a TreeSet to ensure consistent ordering
   *        in the AOG.
   * @throws ParseException
   */
  public void getDeps(TreeSet<String> accum, Catalog catalog) throws ParseException {
    // Build up a set, since a given input may be used more than once.

    // Go through the arguments list looking for locators
    for (RValueNode arg : args) {
      if (arg instanceof NickNode) {
        // Locators are encoded as NickNodes
        NickNode narg = (NickNode) arg;
        accum.add(narg.getNickname());
      } else {
        arg.getReferencedViews(accum, catalog);
      }
    }
  }

  /**
   * Subclasses should override this function to return the appropriate parse tree node.
   * 
   * @return parse tree node for the span on which this table function will run
   */
  // public abstract ColNameNode targetNode ();

  @Override
  public void qualifyReferences(Catalog catalog) throws ParseException {

    // Qualify any column references in the arguments
    for (AQLParseTreeNode node : args) {
      if (node != null) {
        node.qualifyReferences(catalog);
      }
    }

    // Qualify the function name itself.
    TableFuncCatalogEntry entry = catalog.lookupTableFunc(functionName);
    if (entry.isImported()) {
      // SPECIAL CASE: Function is imported from another module.
      // There should be no need to qualify; either the name is already qualified, or a nickname was
      // used and the
      // qualified name of the function is not valid in the context of the current module.
      // END SPECIAL CASE
    } else if (false == functionName.contains(".")) {
      // Function not imported from another module, and name not already qualified
      functionName = ModuleUtils.prepareQualifiedName(catalog.getModuleName(), functionName);
    } else {
      // Function not imported from another module, but name is already qualified
    }

  }

}
