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
package com.ibm.avatar.aql.planner;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.TreeSet;

import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.aql.FromListItemNode;
import com.ibm.avatar.aql.AbstractAQLParseTreeNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.PredicateNode;
import com.ibm.avatar.aql.catalog.Catalog;

/**
 * Superclass for nodes of a logical query plan in AQL.
 * 
 */
public abstract class PlanNode {

  PlanNode(PlanNode[] children) {
    this.children = children;
  }

  PlanNode(PlanNode child) {
    this.children = new PlanNode[] {child};
  }

  PlanNode(ArrayList<PlanNode> childrenList) {
    this.children = new PlanNode[childrenList.size()];
    this.children = childrenList.toArray(this.children);
  }

  /**
   * Create a deep copy of the plan subtree rooted at this node. This functionality is needed for
   * RSE join, which needs its own copy of the inner operand of the join. Uses shallow copy for AQL
   * parse tree nodes and other immutable objects.
   * 
   * @return a deep copy of the subtree under this plan node
   */
  public PlanNode deepCopy() throws ParseException {
    // Let the subclass copy its fields first
    PlanNode ret = deepCopyImpl();

    // Now copy the fields that are kept in this class
    // No need to copy children; the subclass takes care of that
    // Cost records are immutable.
    ret.costRecord = costRecord;

    // Add all column renamings (the individual elements of the list are immutable)
    ret.renamings.addAll(renamings);

    // System.err.printf("deepCopy(): Added %d elements to renamings list for %s\n",
    // ret.renamings.size (), ret);

    return ret;
  }

  /**
   * Placeholder for subclasses' internal implementations of {@link #deepCopy()}
   * 
   * @return a deep copy of the subtree under this plan node
   * @throws ParseException
   */
  protected abstract PlanNode deepCopyImpl() throws ParseException;

  /**
   * Cached cost record for costing this node.
   */
  private CostRecord costRecord = null;

  public CostRecord getCostRecord() {
    return costRecord;
  }

  public void setCostRecord(CostRecord costRecord) {
    this.costRecord = costRecord;
  }

  private PlanNode[] children;

  /**
   * @return the children of this node in the parse tree
   */
  public final PlanNode[] getChildren() {
    return children;
  }

  protected final void setChildren(PlanNode[] children) {
    this.children = children;
  }

  /**
   * For use by nodes that have only one child
   * 
   * @return first child node
   */
  protected final PlanNode child() {
    if (0 == children.length) {
      return null;
    } else if (1 == children.length) {
      return children[0];
    } else {
      throw new RuntimeException("Called child(), but number of children is > 1");
    }
  }

  /**
   * Mapping for renaming columns at the output of this node. Key is internal name, value is
   * external name.
   */
  protected ArrayList<Pair<String, String>> renamings = new ArrayList<Pair<String, String>>();

  protected void addRenaming(String internalName, String externalName) {
    renamings.add(new Pair<String, String>(internalName, externalName));
  }

  /**
   * Compose any column renamings already present in this PlanNode with the specified set.
   * Equivalent to adding a ProjectionNode with the specified renamings above this one. For example,
   * if this node currently maps the name "a" to "b", and the argument to this method says to map
   * "b" to "c", then after this method is complete the node will map "a" to "c" directly.
   * 
   * @param addlRenamings renamings to apply on top of the ones already present.
   */
  protected void composeRenamings(ArrayList<Pair<String, String>> addlRenamings) {
    // Replace the existing set of renamings with a fresh, new one.
    ArrayList<Pair<String, String>> oldRenamings = renamings;
    renamings = new ArrayList<Pair<String, String>>();

    // Iterate through the new set of top-level renamings, composing them with the previous
    // renamings.
    for (Pair<String, String> pair : addlRenamings) {

      String internalName = pair.first;
      String externalName = pair.second;

      // Use linear search, since schemas are generally quite small
      String oldInternalName = null;
      for (Pair<String, String> oldPair : oldRenamings) {
        String oldExternalName = oldPair.second;

        if (oldExternalName.equals(internalName)) {
          if (null != oldInternalName) {
            // Validate the data structure while we're at it
            throw new FatalInternalError(
                "Renaming table for plan node %s maps the name '%s' " + "to both '%s' and '%s'",
                this, internalName, oldInternalName, oldPair.first);
          }
          oldInternalName = oldPair.first;
        }
      }

      if (null == oldInternalName) {
        throw new FatalInternalError(
            "Plan node %s does not have any renaming for column '%s' (existing renamings are %s, new renaming is %s)",
            this, internalName, oldRenamings, pair);
      }

      Pair<String, String> newRenaming = new Pair<String, String>(oldInternalName, externalName);
      renamings.add(newRenaming);
    }
  }

  /**
   * Generate an AOG Project() operator spec for the output renaming of this node. Note that we do
   * *NOT* close the paren on the Project operator; it is up to the caller to do so!
   * 
   * @param stream where to send the AOG text
   * @param indent how far (in tabs) to indent the generated AOG
   */
  protected void genProject(PrintWriter stream, int indent) {

    printIndent(stream, indent);
    stream.print("Project(\n");
    printIndent(stream, indent + 1);
    stream.print("(\n");

    for (int i = 0; i < renamings.size(); i++) {
      String internalName = renamings.get(i).first;
      String externalName = renamings.get(i).second;

      printIndent(stream, indent + 2);

      stream.printf("\"%s\" => \"%s\"", internalName, externalName);

      // Don't put a comma after the last entry in the hash
      if (i < renamings.size() - 1) {
        stream.print(",");
      }

      stream.print("\n");
    }

    // Close the paren on the rename list
    printIndent(stream, indent + 1);
    stream.print("),\n");

    // Note that we do *NOT* close the paren on the Project operator; it
    // is up to the caller to do so!

  }

  /**
   * Generate the physical plan (expressed as an AOG spec) for the logical plan rooted at this node.
   * 
   * @param stream output stream where the AOG goes
   * @param indent how far (in tabs) to indent the lines we add to the output stream
   * @param catalog ptr to catalog, for retrieving necessary metadata
   * @throws Exception
   */
  public void toAOG(PrintWriter stream, int indent, Catalog catalog) throws Exception {

    // Don't generate empty renamings.
    if (renamings.size() > 0) {
      genProject(stream, indent);
      toAOGNoRename(stream, indent + 1, catalog);

      // Close the paren on the Project operator
      stream.print("\n");
      printIndent(stream, indent);
      stream.print(")");
    } else {
      // No renaming needed
      toAOGNoRename(stream, indent, catalog);
    }
  }

  /**
   * Internal implementation of toAOG, without output renaming.
   * 
   * @param catalog TODO
   */
  public abstract void toAOGNoRename(PrintWriter stream, int indent, Catalog catalog)
      throws Exception;

  /**
   * Convenience function for subclasses' implementations of dump(); Prints out the spaces that
   * correspond to the indicated indent level.
   */
  protected static void printIndent(PrintWriter stream, int indent) {
    // Delegate to the SQL parse tree's printIndent() to keep the tabs lined
    // up.
    AbstractAQLParseTreeNode.printIndent(stream, indent);
  }

  /** Dump the node (and children) for debugging. */
  public abstract void dump(PrintWriter stream, int indent);

  /**
   * @param indent: How far (in the units used by
   *        {@link AQLParseTreeNode#printIndent(PrintWriter, int)}) to indent the returned value
   * @return a pretty-printed string version of the node and its children
   */
  public String dumpToString(int indent) {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    dump(new PrintStream(buf), indent);
    try {
      return buf.toString("UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new FatalInternalError(
          "Internal representation of plan node %s is not UTF-8; " + "should never happen", this);
    }
  }

  /** Retrieve the set of relations joined by this plan tree. */
  public abstract void getRels(TreeSet<FromListItemNode> rels);

  /** Retrieve the set of predicates applied in this plan tree. */
  public abstract void getPreds(TreeSet<PredicateNode> preds);

  /** Adapter for {@link #dump(PrintWriter,int)} */
  public void dump(PrintStream stream, int indent) {
    PrintWriter pw = new PrintWriter(stream);
    dump(pw, indent);
    pw.close();
  }

  /**
   * This method currently only works properly if there are renamings present.
   * 
   * @return a list of the output columns that will be present on the output of this logical
   *         operator after any output projections have been applied.
   */
  public ArrayList<String> getOutputColNames() {
    ArrayList<String> ret = new ArrayList<String>();

    if (0 == renamings.size()) {
      throw new RuntimeException(
          "This method currently only works " + "properly when there are renamings present.");
    }

    for (Pair<String, String> renaming : renamings) {
      ret.add(renaming.second);
    }

    return ret;
  }

  /**
   * Utility that replaces '.' in target name with '_' so that it can be used as a part of AOG
   * 
   * @param targetName the input target name to escape
   * @return a string that can be used as part of an AQL/AOG variable name for shared dictionary
   *         matching
   */
  protected String escapeTargetName(String targetName) {
    String escapedTargetName = targetName.replace(Constants.MODULE_ELEMENT_SEPARATOR, '_');
    return escapedTargetName;
  }

  /**
   * If the tabname contains a '.' due to modular AQL (or) if the tabname contained a '.' for any
   * other reason, then prefix with '_' and surround with double quotes.
   * 
   * @param tabname name of the table to decorate
   * @return
   */
  protected String decorateTargetNameWithQuotes(String tabname) {
    if (tabname.indexOf(Constants.MODULE_ELEMENT_SEPARATOR) != -1) {
      return String.format("_{\"%s\"}", tabname);
    } else {
      return tabname;
    }
  }

}
