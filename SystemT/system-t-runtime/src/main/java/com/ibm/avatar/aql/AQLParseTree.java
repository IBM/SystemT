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
import java.util.HashMap;
import java.util.TreeSet;

import com.ibm.avatar.aql.catalog.Catalog;

/** Top-level parse tree for an Avatar SQL file. */
public class AQLParseTree {

  /**
   * Top-level nodes of the parse tree, representing the SELECT and CREATE VIEW statements.
   */
  private ArrayList<AQLParseTreeNode> topNodes = new ArrayList<AQLParseTreeNode>();

  /** Symbol table used in constructing the nodes of the parse tree. */
  private Catalog st;

  public AQLParseTree(Catalog st) {
    this.st = st;
  }

  public void addTopLevel(AQLParseTreeNode node) {
    topNodes.add(node);
  }

  public Catalog getSymbolTable() {
    return st;
  }

  public ArrayList<AQLParseTreeNode> getTopLevelNodes() {
    return topNodes;
  }

  /**
   * Pretty-print the parse tree.
   * 
   * @param stream where to print to
   * @throws ParseException
   */
  public void dump(PrintWriter stream) throws ParseException {
    for (int i = 0; i < topNodes.size(); i++) {
      AQLParseTreeNode n = topNodes.get(i);
      n.dump(stream, 0);

      stream.print("\n\n");

    }
  }

  /**
   * Retrieve just the CREATE VIEW nodes in the parse tree.
   * 
   * @param topoSort true to topologically sort the nodes according to dependencies in the symbol
   *        table
   * @param catalog pointer to the AQL catalog; only needed for topological sorting
   * @return the nodes
   * @throws ParseException if a problem with the AQL is detected during the topological sort
   */
  public ArrayList<CreateViewNode> getCreateViewNodes(boolean topoSort, Catalog catalog)
      throws ParseException {
    ArrayList<CreateViewNode> createViewNodes = new ArrayList<CreateViewNode>();
    for (int i = 0; i < topNodes.size(); i++) {
      AQLParseTreeNode n = topNodes.get(i);

      if (n instanceof CreateViewNode) {
        CreateViewNode cvn = (CreateViewNode) n;
        createViewNodes.add(cvn);
      }
    }

    // Pass 2: Topologically sort the CREATE VIEW nodes by view
    // dependencies, then compile them in sorted order.
    if (topoSort) {
      return topologicalSort(createViewNodes, catalog);
    } else {
      return createViewNodes;
    }
  }

  public ArrayList<CreateDictNode> getCreateDictNodes() {
    ArrayList<CreateDictNode> ret = new ArrayList<CreateDictNode>();
    for (int i = 0; i < topNodes.size(); i++) {
      AQLParseTreeNode n = topNodes.get(i);

      if (n instanceof CreateDictNode) {
        // Inline dictionary

        CreateDictNode dn = (CreateDictNode) n;

        ret.add(dn);
      }
    }
    return ret;
  }

  /**
   * Yunyao Li: added 02/26/2008 to support HTML detagger
   * 
   * @return
   */
  public ArrayList<DetagDocNode> getDetagDocNodes() {
    ArrayList<DetagDocNode> ret = new ArrayList<DetagDocNode>();
    for (int i = 0; i < topNodes.size(); i++) {
      AQLParseTreeNode n = topNodes.get(i);

      if (n instanceof DetagDocNode) {
        // Inline dictionary

        DetagDocNode dn = (DetagDocNode) n;

        ret.add(dn);
      }
    }
    return ret;
  }

  /**
   * Sort a set of CREATE VIEW nodes according to their dependency graph. Among nodes that have no
   * dependencies, tries to keep the same order as the input ArrayList.
   * 
   * @param inputs the views to sort, in arbitrary order
   * @param catalog pointer to the AQL catalog, for looking up any information necessary to infer
   *        view dependencies
   * @throws ParseException if a problem with the AQL is detected while tracing dependencies
   */
  public static ArrayList<CreateViewNode> topologicalSort(ArrayList<CreateViewNode> inputs,
      Catalog catalog) throws ParseException {

    // Map from view name --> view node for every view that we haven't added
    // to the output yet.
    HashMap<String, CreateViewNode> todo = new HashMap<String, CreateViewNode>();

    for (CreateViewNode cvn : inputs) {
      todo.put(cvn.getViewName(), cvn);
    }

    // Output goes here.
    ArrayList<CreateViewNode> ret = new ArrayList<CreateViewNode>();

    // Repeatedly run through the input list, generating the sorted output
    // one layer at a time.
    // Note that this is potentially an O(n^2) algorithm, but probably O(n)
    // in practice (number of passes == number of levels of dependencies)
    while (todo.size() > 0) {
      for (int i = 0; i < inputs.size(); i++) {
        CreateViewNode curNode = inputs.get(i);
        String curName = curNode.getViewName();

        if (todo.containsKey(curName)) {
          // Still haven't added this node to the output; see if it's
          // now eligible.
          boolean dependsOnTodo = false;
          TreeSet<String> deps = new TreeSet<String>();
          curNode.getBody().getDeps(deps, catalog);
          for (String depName : deps) {
            if (todo.containsKey(depName)) {
              dependsOnTodo = true;
            }
          }

          if (false == dependsOnTodo) {
            // This node doesn't depend on any item in the current
            // todo list, so we can add it to the output.
            ret.add(curNode);
            todo.remove(curName);
          }
        }

      }
    }

    return ret;
  }

  /**
   * Appends all the statements in the indicated tree to this parse tree's statements list.
   * 
   * @param subtree parse tree generated by parsing an external file.
   */
  public void addAll(AQLParseTree subtree) {

    for (AQLParseTreeNode subnode : subtree.topNodes) {
      this.topNodes.add(subnode);
    }

  }

}
