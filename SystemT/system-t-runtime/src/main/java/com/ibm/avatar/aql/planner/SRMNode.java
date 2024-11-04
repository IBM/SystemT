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

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;

import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.aql.ColNameNode;
import com.ibm.avatar.aql.FromListItemNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.PredicateNode;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.systemt.util.regex.RegexesTokParams;

/**
 * Plan node for the operator that implements shared regex matching.
 * 
 */
public class SRMNode extends PlanNode {
  /**
   * Name of the module that the SRM Node belongs to
   */
  private String moduleName;

  /**
   * Parameters for the various regular expressions that will be evaluated by the operator
   * underlying this node.
   */
  private ArrayList<RegexesTokParams> regexParams;

  /**
   * Names of the temporary output views that are created for the outputs of this operator.
   */
  private ArrayList<String> tempViewNames;

  /**
   * The external name of the view the expressions will be evaluated against.
   */
  private String globalTargetName;

  /**
   * Which view (local scoped name) and column the expressions will be evaluated against. Used to
   * construct the output names.
   */
  private ColNameNode target;

  /**
   * Which input columns do we need to pass through when extracting
   */
  private ArrayList<String> passThruColumnList;

  /**
   * Root of the tree of operators that produce the input to this extraction.
   */
  private PlanNode child;

  /**
   * Main constructor
   * 
   * @param regexParams parameters (expression, flags, number of tokens, etc.) for the regular
   *        expressions that this portion of the plan evaluates
   * @param target which view and column the expressions will be evaluated against
   * @param child root of the tree of operators that produces the input to this extraction.
   * @param passThruColumnList
   * @param moduleName Name of the module that this SRM node belongs to
   */
  public SRMNode(Set<RegexesTokParams> regexParams, String globalTargetName, ColNameNode target,
      PlanNode child, ArrayList<String> passThruColumnList, String moduleName) {
    super(new PlanNode[0]);

    // Convert the set to a list, fixing an (arbitrary) order in the process
    this.regexParams = new ArrayList<RegexesTokParams>(regexParams);
    this.target = target;
    this.globalTargetName = globalTargetName;
    this.child = child;
    this.passThruColumnList = passThruColumnList;
    this.moduleName = moduleName;

    // Generate view names.
    tempViewNames = new ArrayList<String>();
    for (int i = 0; i < this.regexParams.size(); i++) {
      tempViewNames.add(makeTempOutputName(i));
    }
  }

  @Override
  public PlanNode deepCopyImpl() throws ParseException {
    TreeSet<RegexesTokParams> tmpSet = new TreeSet<RegexesTokParams>();
    tmpSet.addAll(regexParams);
    return new SRMNode(tmpSet, globalTargetName, target, child, passThruColumnList, moduleName);
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    printIndent(stream, indent);
    stream.printf("SRMNode\n");
    printIndent(stream, indent + 1);
    stream.printf("Regex parameters: %s\n", regexParams);
    printIndent(stream, indent + 1);
    stream.printf("Target: %s\n", target.toString());
    printIndent(stream, indent + 1);
    stream.print("Child:\n");
    child.dump(stream, indent + 2);
  }

  /**
   * Deterministic method of creating a unique temporary nickname for one of the outputs of the SRM
   * regex operator.
   * 
   * @param exprIx index of the regular expression in our list
   */
  private String makeTempOutputName(int exprIx) {
    return String.format("SRM_OUTPUT_%s_%d_OVER_%s_%s_%s%s", moduleName, exprIx,
        escapeTargetName(globalTargetName), escapeTargetName(target.getTabname()),
        target.getColnameInTable(), prepareStringForPassThruColumns());
  }

  /**
   * @return a string containing all the pass thru column name
   */
  private String prepareStringForPassThruColumns() {
    StringBuilder sb = new StringBuilder();

    if (this.passThruColumnList != null) {
      // Prepare a list of columns except the target column
      ArrayList<String> passThruColumnExceptTarget = new ArrayList<String>(this.passThruColumnList);
      passThruColumnExceptTarget.remove(target.getColName());

      for (String colname : passThruColumnExceptTarget) {
        // we just need the columnname and not the fully qualified
        // columnname
        if (colname.indexOf('.') != 0)
          sb.append("_" + colname.substring(colname.indexOf('.') + 1));
        else
          sb.append("_" + colname);

      }
    }
    return sb.toString();
  }

  /**
   * We override the toAOG method, since output renaming is meaningless for a multi-output operator.
   */
  @Override
  public void toAOG(PrintWriter stream, int indent, Catalog catalog) throws Exception {

    printIndent(stream, indent);
    stream.print("# RegexesTok() operator created through" + " Shared Regex Matching\n");

    // Now we can generate the RegexesTok() operator that outputs to the
    // temp nicknames.

    // Start with the targets list.
    printIndent(stream, indent);
    stream.print("(\n");

    for (int i = 0; i < regexParams.size(); i++) {
      String aogTypeName = StringUtils.toAOGNick(tempViewNames.get(i));

      printIndent(stream, indent + 1);
      if (i == regexParams.size() - 1) {
        // Leave the comma off the last one.
        stream.printf("%s\n", aogTypeName);
      } else {
        stream.printf("%s,\n", aogTypeName);
      }
    }

    printIndent(stream, indent);
    stream.print(") = \n");

    // Then generate the RegexesTok() operator.
    printIndent(stream, indent);
    stream.print("RegexesTok(\n");

    // Regular expressions and parameters
    printIndent(stream, indent + 1);
    stream.print("(\n");
    for (int i = 0; i < regexParams.size(); i++) {
      RegexesTokParams params = regexParams.get(i);

      // Format is (/regex/, "flags", mintok, maxtok) => "output col name"
      // We put the regex on its own line.
      printIndent(stream, indent + 2);
      stream.printf("(%s,\n", params.getPerlRegexStr());

      // Now for the remaining parameters for this expression
      printIndent(stream, indent + 3);
      stream.printf("%s, %d, %d) => %s", //
          StringUtils.quoteStr('"', params.getFlagsStr(), true, true), //
          params.getMinTok(), params.getMaxTok(), //
          StringUtils.quoteStr('"', params.getOutputColName(), true, true));

      // No comma after last entry.
      if (i < regexParams.size() - 1) {
        stream.print(",\n");
      } else {
        stream.print("\n");
      }
    }
    printIndent(stream, indent + 1);
    stream.print("),\n");

    // Generate the target column.
    String targetCol = target.getColName();
    printIndent(stream, indent + 1);
    stream.printf("%s,\n", //
        StringUtils.quoteStr('"', targetCol, true, true));

    // Generate the tree of input operators.
    child.toAOG(stream, indent + 1, catalog);
    stream.print("\n");

    // Close the parens of RegexesTok()
    printIndent(stream, indent);
    stream.print(");\n");
  }

  @Override
  public void toAOGNoRename(PrintWriter stream, int indent, Catalog catalog) throws Exception {
    throw new RuntimeException(
        "Not implemented, because we override the top-level toAOG() method.");
  }

  @Override
  public void getPreds(TreeSet<PredicateNode> preds) {
    throw new RuntimeException("Should never be called");
  }

  @Override
  public void getRels(TreeSet<FromListItemNode> rels) {
    throw new RuntimeException("Should never be called");
  }

  /**
   * @param params parameters for a particular regular expression evaluation
   * @return name of the output that uses the indicated regex
   */
  public String getAOGOutputNick(RegexesTokParams params) {

    // Use linear search to find the indicated set of regex params in our
    // set.
    for (int i = 0; i < regexParams.size(); i++) {
      RegexesTokParams ourParams = regexParams.get(i);

      if (ourParams.equals(params)) {
        // Found the one we're looking for; return the corresponding
        // name (the two lists are in the same order)
        return tempViewNames.get(i);
      }
    }

    throw new RuntimeException(String.format("Don't know about regex invocation %s", params));
  }
}
