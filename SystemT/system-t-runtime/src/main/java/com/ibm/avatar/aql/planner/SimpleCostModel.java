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

import com.ibm.avatar.api.tam.ModuleMetadata;
import com.ibm.avatar.aql.ExtractionNode;
import com.ibm.avatar.aql.FromListItemNode;
import com.ibm.avatar.aql.FromListItemTableFuncNode;
import com.ibm.avatar.aql.FromListItemViewRefNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.RegexExNode;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.aql.tam.ModuleUtils;

/**
 * Simplistic cost model that does not use any statistics. Mostly focuses on making regular
 * expression evaluations appear expensive.
 */
public class SimpleCostModel extends CostModel {

  /**
   * Should the simple cost model traverse to plans below table function calls when costing?
   */
  public static boolean COST_ACROSS_TABFUNCS = true;

  public static class SimpleCostRecord extends CostRecord {

    /** The number of times the Java regex engine is invoked in the subtree */
    public double numJavaRegexes = 0;

    /**
     * The number of times the SimpleRegex engine is invoked in a way that is *NOT* SRM-compatible
     */
    public double numSimpleRegexes = 0;

    /**
     * The number of times the SimpleRegex engine is invoked with Shared Regex Matching.
     */
    public double numMultiRegexes = 0;

    public double numNLJoins = 0;

    public double numMergeJoins = 0;

    public double numHashJoins = 0;

    // public double numSentenceEvals = 0;

    /**
     * Add the costs in the indicated record to our own.
     * 
     * @param other
     */
    public void add(CostRecord other) {
      SimpleCostRecord o = (SimpleCostRecord) other;
      numJavaRegexes += o.numJavaRegexes;
      numSimpleRegexes += o.numSimpleRegexes;
      numMultiRegexes += o.numMultiRegexes;
      numNLJoins += o.numNLJoins;
      numMergeJoins += o.numMergeJoins;
      numHashJoins += o.numHashJoins;
      // numSentenceEvals += o.numSentenceEvals;
    }

    /**
     * @param other cost of evaluating a node
     * @param prob probability that this evaluation will occur
     */
    public void probAdd(CostRecord other, double prob) {
      SimpleCostRecord o = (SimpleCostRecord) other;
      numJavaRegexes += prob * o.numJavaRegexes;
      numSimpleRegexes += prob * o.numSimpleRegexes;
      numMultiRegexes += prob * o.numMultiRegexes;
      numNLJoins += prob * o.numNLJoins;
      numMergeJoins += prob * o.numMergeJoins;
      numHashJoins += prob * o.numHashJoins;
      // numSentenceEvals += prob * o.numSentenceEvals;
    }

    @Override
    public double cost() {
      return (NLJOIN_COST * numNLJoins) + (MERGEJOIN_COST * numMergeJoins)
          + (HASHJOIN_COST * numHashJoins) + (JAVA_REGEX_COST * numJavaRegexes)
          + (SIMPLE_REGEX_COST * numSimpleRegexes) + (MULTI_REGEX_COST * numMultiRegexes)
      // + (SENTENCE_COST * numSentenceEvals)
      ;
    }
  }

  /** (Constant) cost of executing a nested-loops join. */
  public static final double NLJOIN_COST = 100000.0;

  /** (Constant) cost of executing a sort-merge join. */
  public static final double MERGEJOIN_COST = 10000.0;

  /** (Constant) cost of executing a hash join. */
  public static final double HASHJOIN_COST = 1000.0;

  /** Cost of evaluating a regular expression with the Java engine. */
  public static final double JAVA_REGEX_COST = 10.0;

  /** Cost of evaluating a regular expression with the SimpleRegex engine. */
  public static final double SIMPLE_REGEX_COST = 5.0;

  /**
   * Incremental cost of adding a new regex to a Shared Regex Matching operator.
   */
  public static final double MULTI_REGEX_COST = 2.0;

  /** Cost of extracting sentences. */
  // public static final double SENTENCE_COST = 50.0;

  /** Chance that the inner of a join will be evaluated */
  public static final double EVAL_INNER_PROB = 0.5;

  /**
   * Portion of the each document that an RSE extraction operator will need to examine.
   */
  public static final double RSE_EVAL_FRAC = 0.1;

  @SuppressWarnings("deprecation")
  @Override
  protected CostRecord computeCostRecord(PlanNode node) throws ParseException {

    SimpleCostModel.SimpleCostRecord ret = new SimpleCostRecord();

    if (node instanceof NLJoinNode) {
      ret.numNLJoins++;
      NLJoinNode j = (NLJoinNode) node;

      ret.add(getCostRecord(j.outer()));

      // Assume that the inner of the join only gets evaluated 50% of
      // the time.
      ret.probAdd(getCostRecord(j.inner()), EVAL_INNER_PROB);

    } else if (node instanceof MergeJoinNode) {
      MergeJoinNode j = (MergeJoinNode) node;

      ret.numMergeJoins++;
      ret.add(getCostRecord(j.outer()));
      ret.probAdd(getCostRecord(j.inner()), EVAL_INNER_PROB);

    } else if (node instanceof HashJoinNode) {
      // Hash join is treated pretty much the same way as merge join.
      HashJoinNode j = (HashJoinNode) node;

      ret.numHashJoins++;
      ret.add(getCostRecord(j.outer()));
      ret.probAdd(getCostRecord(j.inner()), EVAL_INNER_PROB);

    } else if (node instanceof RSEJoinNode) {

      RSEJoinNode j = (RSEJoinNode) node;

      ret.add(getCostRecord(j.outer()));

      // Model: If the outer has any tuples (EVAL_INNER_PROB), use RSE to
      // evaluate any extractions over a fraction (RSE_EVAL_FRAC) of the
      // document.
      ret.probAdd(getCostRecord(j.inner()), EVAL_INNER_PROB * RSE_EVAL_FRAC);

    } else if (node instanceof ScanNode) {

      // Add the the cost of computing the input we're scanning.
      ScanNode s = (ScanNode) node;
      FromListItemNode whatToScan = s.getWhatToScan();
      if (whatToScan instanceof FromListItemTableFuncNode) {

        // Table function evaluation.
        // TODO: When table functions have tabular inputs again, add in the cost of evaluating
        // inputs.

        // TableFnCallNode tabfunc = ((FromListItemTableFuncNode) whatToScan).getTabfunc ();

      } else if (whatToScan instanceof FromListItemViewRefNode) {
        // Scan the output of another view. For now, assume that we
        // are the only consumer of this view's output, so it will
        // be charged to us.

        // The Planner processes views in topologically-sorted
        // order. Retrieve the plan that has been computed for the
        // view referenced here.

        String refViewName = ((FromListItemViewRefNode) whatToScan).getViewName().getNickname();
        if (catalog.isImportedView(refViewName)) {
          ret.add(getCostRecordForImportedView(catalog, refViewName));
        } else if (catalog.isImportedTable(refViewName)) {
          // no cost for imported table
        } else {
          PlanNode viewPlan = catalog.getCachedPlan(refViewName);
          ret.add(getCostRecord(viewPlan));
        }

      } else {
        throw new RuntimeException("Don't understand this from list item.");
      }
    } else if (node instanceof ExtractPlanNode) {

      // EXTRACT statement. First, get the cost of computing the input.
      ret.add(getCostRecord(node.child()));

      // Then add in the cost of the extraction itself.
      ExtractPlanNode e = (ExtractPlanNode) node;
      ExtractionNode extraction = e.getExtractList().getExtractSpec();

      if (extraction instanceof RegexExNode) {
        // Regular expression
        // Determine what engine will be used to evaluate this regex.
        RegexExNode re = (RegexExNode) extraction;

        // There could be multiple regexes in this extract spec; see
        // which one we're referring to here.
        int regexIx = e.getExtractSpecIx();
        if (false == re.getUseSimpleEngine(regexIx)) {
          // Java regex engine will be used.
          ret.numJavaRegexes++;
        } else if (re.getUseRegexTok()) {
          // SimpleRegex-compatible regex match on token boundaries;
          // the system will use Shared Regex Matching, assuming it is
          // enabled.
          // We'll assume for now that the incremental cost of adding
          // a regex to the SRM operator is zero.
        } else {
          // SimpleRegex-compatible regex match, but without a token
          // boundary constraint.
          ret.numSimpleRegexes++;
        }

      } else {
        // TODO: Add code to cost other types of extraction

        // throw new RuntimeException(String.format(
        // "Don't know how to cost extraction node %s", node));
      }

    } else if (node instanceof ExternalScanNode) {
      // Scan from the UIMA CAS
      // Assume zero cost for now.
    } else if (node instanceof LookupTablePlanNode) {
      // Scan of a lookup table; assume zero cost for now.

    } else if (node instanceof RequireDocumentPlanNode) {
      // The document view scan; assume zero cost for now.
    } else if (node instanceof ExternalViewPlanNode) {
      // Scan of an external view; assume zero cost for now.

    } else if (node instanceof ProjectionNode || node instanceof MinusPlanNode
        || node instanceof SelectionNode || node instanceof UnionAllPlanNode
        || node instanceof ScalarFuncNode || node instanceof ViewNode
        || node instanceof ConsolidatePlanNode || node instanceof SortNode
        || node instanceof GroupNode || node instanceof LimitNode
        || node instanceof DetagDocPlanNode) {

      // Sum up the cost records of the child nodes.
      for (PlanNode child : node.getChildren()) {
        ret.add(getCostRecord(child));
      }
    } else {
      throw new RuntimeException(String.format("Don't understand type of %s", node));
    }

    return ret;

  }

  private CostRecord getCostRecordForImportedView(Catalog catalog, String refViewName) {
    String qualifiedMappedViewName = catalog.getQualifiedViewOrTableName(refViewName);
    String moduleName = ModuleUtils.getModuleName(qualifiedMappedViewName);

    ModuleMetadata metadata = catalog.lookupMetadata(moduleName);
    return metadata.getViewMetadata(qualifiedMappedViewName).getCostRecord();
  }
}
