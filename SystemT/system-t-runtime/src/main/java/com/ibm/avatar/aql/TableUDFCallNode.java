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
import java.util.TreeSet;

import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.aog.AOGOpTree;
import com.ibm.avatar.aog.AOGParser;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.aql.catalog.TableFuncCatalogEntry;
import com.ibm.avatar.aql.catalog.TableUDFCatalogEntry;
import com.ibm.avatar.aql.tam.ModuleUtils;

/** Parse tree node for a call to a user-defined table function. */
public class TableUDFCallNode extends TableFnCallNode {

  NickNode funcNameNode;

  /**
   * Main constructor
   * 
   * @param funcNameNode parse tree node for the name of the function
   * @param args parse tree nodes for the arguments to the function call
   */
  public TableUDFCallNode(NickNode funcNameNode, ArrayList<RValueNode> args) {
    super(funcNameNode, args.size());

    // System.err.printf ("Table func call on %s\n", funcNameNode.getNickname ());

    this.funcNameNode = funcNameNode;

    for (int i = 0; i < args.size(); i++) {
      setArg(i, args.get(i));
    }
  }

  @Override
  public void generateAOG(PrintWriter stream, int indent, Catalog catalog) throws ParseException {
    printIndent(stream, indent);
    stream.print(AOGOpTree.getConst(AOGParser.TABFUNC_OPNAME));
    stream.print("(");

    // Pull information about the target function from the AQL catalog.
    String funcName = getFuncName();
    TableFuncCatalogEntry entry = catalog.lookupTableFunc(funcName);
    if (null == entry) {
      throw new FatalInternalError("No information in catalog about table function %s", funcName);
    }
    if (false == (entry instanceof TableUDFCatalogEntry)) {
      throw new FatalInternalError("Table function %s is not user-defined", funcName);
    }

    TableUDFCatalogEntry udfEntry = (TableUDFCatalogEntry) entry;
    if (udfEntry.isImported()) {
      // SPECIAL CASE: Table function imported from another module. Use the
      // source module's name instead of the local alias.
      AQLParseTreeNode ptNode = udfEntry.getParseTreeNode();
      if (ptNode instanceof ImportFuncNode) {
        // Imported via "import function" stmt
        ImportFuncNode ifn = (ImportFuncNode) ptNode;
        funcName = ModuleUtils.prepareQualifiedName(ifn.getFromModule().getNickname(),
            ifn.getNodeName().getNickname());
      } else if (ptNode instanceof ImportModuleNode) {
        // Imported via "import module" stmt.
        // In this case, the function name is usually already qualified.
        // One exception: Function imported via "import module", then the same function imported via
        // "import function"
        // with alias. In this case, the function is called by the alias, but the catalog entry
        // points to the
        // "import module" import.
        // To handle this case, we get the function name from the catalog entry.
        funcName = udfEntry.getName();
      } else {
        throw new FatalInternalError("Unexpected parse tree node type %s",
            ptNode.getClass().getName());
      }
      // END SPECIAL CASE
    }

    // System.err.printf ("**** %s --> %s\n", getFuncName (), funcName);

    // Function name goes in as a quoted string, since it may overlap with the AOG parser's tokens
    // Use the superclass's function name, since the parse tree node isn't touched during
    // qualifyReferences()
    String quotedFuncName = StringUtils.quoteStr('"', funcName);

    TreeSet<String> inputNames = new TreeSet<String>();
    getDeps(inputNames, catalog);

    if (0 == args.size() && 0 == inputNames.size()) {
      // No arguments --> No newlines in AOG
      stream.print(quotedFuncName);
    } else {
      stream.print("\n");
      printIndent(stream, indent + 1);
      stream.print(quotedFuncName);
      stream.print(" (\n");
      for (int i = 0; i < args.size(); i++) {
        try {
          RValueNode arg = args.get(i);
          if (arg instanceof NickNode) {
            // Record locator arguments are returned as NickNodes
            NickNode nickNode = (NickNode) arg;
            printIndent(stream, indent + 2);
            stream.print(StringUtils.toAOGNick(nickNode.getNickname()));
          } else {
            // All other arguments are (scalar) function calls
            arg.asFunction().toAOG(stream, indent + 1, catalog);
          }
        } catch (ParseException e) {
          throw new FatalInternalError(e, "Error converting argument %d of %s to scalar func call",
              i, args);
        }

        if (i < args.size() - 1) {
          stream.print(",");
        }
        stream.print("\n");
      }
      printIndent(stream, indent + 1);
      stream.print(" )");

      // Remaining arguments are the inputs.
      for (String inputName : inputNames) {
        stream.print(",\n");
        printIndent(stream, indent + 1);
        stream.print(StringUtils.toAOGNick(inputName));
      }

      stream.print("\n");
    }

    printIndent(stream, indent);
    stream.print(")");
  }
  // @Override
  // public ColNameNode targetNode ()
  // {
  // // Named variables and functions over views are currently not implemented, so this function
  // must be over constant
  // // scalar values only.
  // // The optimizer expects this method to return something, so use Document.text.
  // return new ColNameNode (new NickNode ("Document"), new NickNode (Constants.DOCTEXT_COL));
  // }

}
