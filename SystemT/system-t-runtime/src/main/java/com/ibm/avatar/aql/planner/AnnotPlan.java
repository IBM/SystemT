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

import java.io.CharArrayWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.api.exceptions.CompilerException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.catalog.Catalog;

/**
 * Top-level class for storing an annotator "query plan" for AQL.
 * 
 */
public class AnnotPlan {

  public static final String NICKNAME_REGEX = "[a-zA-Z_][a-zA-Z0-9_]*";

  /** The different subtrees that make up the overall plan. */
  private ArrayList<PlanNode> subplans;

  /**
   * List of which view names go to the output. First element is the view name; second is the
   * external name.
   */
  private ArrayList<Pair<String, String>> outputNames;

  public AnnotPlan(ArrayList<PlanNode> subplans, List<Pair<String, String>> outputNames) {
    this.subplans = subplans;

    // Make a copy, just in case.
    this.outputNames = new ArrayList<Pair<String, String>>();

    this.outputNames.addAll(outputNames);
  }

  public ArrayList<PlanNode> getSubplans() {
    return subplans;
  }

  public ArrayList<Pair<String, String>> getOutputNames() {
    return outputNames;
  }

  /** Pretty-print the plan in human-readable format. */
  public void dump(PrintWriter stream, int indent) {

    for (PlanNode plan : subplans) {
      plan.dump(stream, 0);
      stream.print("\n\n");
    }

    stream.printf("Outputs: %s\n", outputNames.toString());
  }

  /**
   * Generate an AOG spec for executing the plan.
   * 
   * @param stream output stream where the AOG should be written.
   * @param catalog catalog for looking metadata about plan components.
   * @return a list of any errors that occurred during AOG generation
   */
  public List<Exception> toAOG(PrintWriter stream, Catalog catalog) {
    List<Exception> errors = new ArrayList<Exception>();

    // Generate the AOG for the different views.
    for (PlanNode node : subplans) {
      try {
        node.toAOG(stream, 0, catalog);
      } catch (Exception e) {
        errors.add(new TextAnalyticsException(e, "Error serializing plan for %s", node));
      }
    }

    // Generate any necessary aliases for output views.
    for (Pair<String, String> p : outputNames) {
      String viewName = p.first;
      String outputName = p.second;
      if (null != outputName && false == outputName.equals(viewName)) {
        stream.print(StringUtils.toAOGNick(outputName));
        stream.print(" = ");
        stream.print(StringUtils.toAOGNick(viewName));
        stream.print(";\n");
      }
    }

    // Generate an output clause.
    if (outputNames.size() > 0) {
      stream.print("Output: ");

      for (int i = 0; i < outputNames.size(); i++) {
        Pair<String, String> p = outputNames.get(i);
        String viewName = p.first;
        String outputName = p.second;

        if (null == outputName) {
          outputName = viewName;
        }

        stream.print(StringUtils.toAOGNick(outputName));

        if (i < outputNames.size() - 1) {
          stream.print(", ");
        }
      }
      stream.print(";\n");
    }

    return errors;
  }

  /**
   * Convenience function for compiling directly to an AOG string.
   */
  public String toAOGString(Catalog catalog) throws CompilerException {
    CharArrayWriter buf = new CharArrayWriter();
    List<Exception> errors = toAOG(new PrintWriter(buf), catalog);
    String aog = buf.toString();

    if (0 != errors.size()) {
      for (Exception error : errors) {
        catalog.addCompilerException(error);
      }

      throw new CompilerException(errors, null);
    }

    return aog;
  }
}
