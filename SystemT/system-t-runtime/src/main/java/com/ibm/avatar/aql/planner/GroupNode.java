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
import java.util.TreeSet;

import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.aql.FromListItemNode;
import com.ibm.avatar.aql.ScalarFnCallNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.PredicateNode;
import com.ibm.avatar.aql.RValueNode;
import com.ibm.avatar.aql.catalog.Catalog;

/**
 * Plan node for grouping and aggregates.
 * 
 */
public class GroupNode extends PlanNode {

  /** The values we group by */
  ArrayList<RValueNode> groupByValues;

  /** The aggregate functions we compute */
  ArrayList<ScalarFnCallNode> aggFuncs;

  /** The internal aliases for the aggregate functions we compute */
  ArrayList<String> aggAliases;

  public GroupNode(ArrayList<RValueNode> groupByValues, ArrayList<ScalarFnCallNode> aggFuncs,
      ArrayList<String> aggAliases, PlanNode input) throws ParseException {
    super(input);

    if (groupByValues != null)
      this.groupByValues = groupByValues;
    else
      this.groupByValues = new ArrayList<RValueNode>();

    this.aggFuncs = aggFuncs;
    this.aggAliases = aggAliases;

  }

  @Override
  public PlanNode deepCopyImpl() throws ParseException {
    return new GroupNode(groupByValues, aggFuncs, aggAliases, child().deepCopy());
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    printIndent(stream, indent);
    stream.printf("GroupByNode\n");
    printIndent(stream, indent + 1);
    stream.printf("Group By Values: %s\n", groupByValues);

    // printIndent(stream, indent + 1);
    // stream.printf("Aggregates: %s\n", groupByClause.getTypeStr());

    printIndent(stream, indent + 1);
    stream.print("Input:\n");
    child().dump(stream, indent + 2);
  }

  @Override
  public void getPreds(TreeSet<PredicateNode> preds) {
    child().getPreds(preds);
  }

  @Override
  public void getRels(TreeSet<FromListItemNode> rels) {
    child().getRels(rels);
  }

  @Override
  public void toAOGNoRename(PrintWriter stream, int indent, Catalog catalog) throws Exception {
    printIndent(stream, indent);

    // Generate the AOG Select operator spec.
    stream.print("GroupBy(\n");

    // First argument is the list of group by values
    printIndent(stream, indent);
    stream.print("(\n");

    for (int i = 0; i < groupByValues.size(); i++) {

      groupByValues.get(i).asFunction().toAOG(stream, indent + 1, catalog);

      if (i < groupByValues.size() - 1)
        stream.print(",\n");
    }

    stream.print("\n");
    printIndent(stream, indent);
    stream.print("),\n");

    // Second argument is the list of aggregate functions that are computed,
    // Each function has attached an internal name
    printIndent(stream, indent);
    stream.print("(\n");

    for (int i = 0; i < aggFuncs.size(); i++) {

      // print the aggregate function
      aggFuncs.get(i).asFunction().toAOG(stream, indent + 1, catalog);

      // print the internal name
      stream.printf(" => %s", StringUtils.quoteStr('"', aggAliases.get(i), true, true));

      if (i < aggFuncs.size() - 1)
        stream.print(",\n");
    }

    stream.print("\n");
    printIndent(stream, indent);
    stream.print("),\n");

    /*
     * // Third argument is the list of aliases for the aggregate functions that are computed
     * printIndent(stream, indent); stream.print("(\n"); for(int i = 0; i < aggAliases.size(); i++){
     * printIndent(stream, indent + 1); stream.print(StringUtils.quoteStr('"', aggAliases.get(i),
     * true, true)); if( i < aggAliases.size() - 1) stream.print(",\n"); } stream.print("\n");
     * printIndent(stream, indent); stream.print("),\n");
     */

    // Fourth argument is the input.
    child().toAOG(stream, indent + 1, catalog);
    stream.print("\n");

    // Close the parens
    printIndent(stream, indent);
    stream.print(")");
  }

}
