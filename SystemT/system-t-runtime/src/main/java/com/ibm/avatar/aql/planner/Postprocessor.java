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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.aql.ColNameNode;
import com.ibm.avatar.aql.DictExNode;
import com.ibm.avatar.aql.ExtractListNode;
import com.ibm.avatar.aql.FromListItemNode;
import com.ibm.avatar.aql.FromListItemViewRefNode;
import com.ibm.avatar.aql.NickNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.RegexExNode;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.logging.Log;
import com.ibm.systemt.util.regex.RegexesTokParams;

public class Postprocessor extends PlanRewriter {

  /**
   * Flag to indicate whether to apply the Shared Dictionary Matching optimization during the
   * postprocessing phase.
   */
  private boolean performSDM = true;

  /**
   * Flag to indicate whether to apply the Shared Regex Matching optimization during the
   * postprocessing phase.
   */
  private boolean performSRM = true;

  /**
   * @param performSDM TRUE to apply the Shared Dictionary Matching optimization during the
   *        postprocessing phase
   */
  public void setPerformSDM(boolean performSDM) {
    this.performSDM = performSDM;
  }

  /**
   * @param performSRM TRUE to apply the Shared Regex Matching optimization during the
   *        postprocessing phase
   */
  public void setPerformSRM(boolean performSRM) {
    this.performSRM = performSRM;
  }

  /**
   * Main entry point for post-processing of compiled execution plans. Modifies its argument!
   * 
   * @param plans top-level plans for the various views in the original AQL
   */
  public void postProcess(ArrayList<PlanNode> plans, final Catalog catalog) {
    boolean debug = false;

    if (debug) {
      System.err.printf("Plan before postprocessing:\n");
      for (PlanNode plan : plans) {
        plan.dump(System.err, 1);
      }
    }

    // Shared dictionary matching: Find all groups of compatible dictionary
    // scans in the original plan, and redirect them towards a shared
    // Dicts() operator.
    if (performSDM) {
      applySDM(plans, catalog);
    }

    // Shared regex matching: Find all groups of compatible RegexTok
    // evaluations, and direct them towards a single RegexesTok operator.
    if (performSRM) {
      applySRM(plans, catalog);
    }

    if (debug) {
      System.err.printf("Plan after postprocessing:\n");
      for (PlanNode plan : plans) {
        plan.dump(System.err, 1);
      }
    }

  }

  /**
   * Performs the Shared Dictionary Matching plan transformation. That is, finds all cases where
   * more than one dictionary is evaluated against a given source and merges each of those sets of
   * dictionary evaluations into a single SDM dictionary node.
   * 
   * @param plans compiled plans for different views
   * @param catalog main catalog, for looking up view information
   */
  @SuppressWarnings("unchecked")
  private void applySDM(ArrayList<PlanNode> plans, final Catalog catalog) {
    // Build up a map from target column to dictionary name.
    // We do this by building up a callback and passing the callback to
    // a method that iterates over the plans.
    callback buildCB = new callback() {

      // Note that we're using an ArrayList for each entry, rather
      // than a set; this is intentional -- if there are multiple
      // calls to the same dictionary, we still want to apply
      // dictionary sharing.
      // Key of this map is the (table, column) pair describing where
      // to perform the dictionary lookup.
      // Replaced HashMap with LinkedHashMap to maintain insertion
      // order while traversal. This will help in generating consistent
      // operator graphs across platform/jvm combinations.
      Map<ColNameNode, ArrayList<DictInvocation>> ttd =
          new LinkedHashMap<ColNameNode, ArrayList<DictInvocation>>();

      @Override
      public PlanNode exec(PlanNode p, ArrayList<PlanNode> parents) {
        if (p instanceof ExtractPlanNode) {

          ExtractPlanNode e = (ExtractPlanNode) p;

          ExtractListNode el = e.getExtractList();

          if (el.getExtractSpec() instanceof DictExNode) {
            // Figure out what field the extraction is done
            // over.
            ColNameNode target = e.getGlobalTargetName();

            // Fetch the parse tree node for the dictionary
            // extraction.
            DictExNode dict = (DictExNode) el.getExtractSpec();

            // A given ExtractPlanNode only handles one part of
            // a multi-dictionary extraction. Pull out the
            // appropriate one.
            int dictIx = e.getExtractSpecIx();

            if (dict.getUseSDM(dictIx)) {
              // We will be applying SDM to this node.
              if (!ttd.containsKey(target)) {
                // First time -- create a new entry
                ttd.put(target, new ArrayList<DictInvocation>());
              }

              DictInvocation di = dict.toDictInvocation(dictIx);
              String qualifiedName = catalog.getQualifiedDictName(di.dictName);
              if (qualifiedName != null) {
                di.dictName = qualifiedName;
              }
              ttd.get(target).add(di);
            }
          }
        }
        return p;
      }

      @Override
      public Object getResult() {
        return ttd;
      }
    };
    iterate(plans, buildCB);
    HashMap<ColNameNode, ArrayList<DictInvocation>> targetToDicts =

        (LinkedHashMap<ColNameNode, ArrayList<DictInvocation>>) buildCB.getResult();

    // Since we will be iterating through the plans list, we don't want
    // to add new things to it until the very end. Instead, we build up
    // a list of stuff to add.
    ArrayList<PlanNode> toAdd = new ArrayList<PlanNode>();

    // For every column that has more than one dictionary applied,
    // create a new SDMNode plan.
    // Index the SDMNode plans by column name.
    final HashMap<ColNameNode, SDMNode> sdms = new HashMap<ColNameNode, SDMNode>();
    for (ColNameNode target : targetToDicts.keySet()) {
      ArrayList<DictInvocation> dicts = targetToDicts.get(target);

      // System.err.printf ("Target %s is used for %d dicts\n", target, dicts.size ());

      if (dicts.size() > 1) {
        // Remove duplicates and maintain insertion order, to generate
        // consistent AOG.
        LinkedHashSet<DictInvocation> unique = new LinkedHashSet<DictInvocation>(dicts);

        SDMNode node = new SDMNode(unique, target);
        sdms.put(target, node);

        // Defer adding the new node to the top-level plan until
        // we're finished rewriting it.
        toAdd.add(node);
      }
    }

    // Replace the original dictionary evaluations with scans over the
    // results of the SDMNode plans.
    // Again, we create a callback and pass the callback to an iteration
    // function.
    final class replaceCB implements callback {

      @Override
      public PlanNode exec(PlanNode p, ArrayList<PlanNode> parents) {
        if (p instanceof ExtractPlanNode) {
          // Unpack the dictionary reference as before.
          ExtractPlanNode e = (ExtractPlanNode) p;
          ExtractListNode el = e.getExtractList();
          if (el.getExtractSpec() instanceof DictExNode) {
            ColNameNode target = e.getGlobalTargetName();

            // Fetch the parse tree node for the dictionary
            // extraction.
            DictExNode dict = (DictExNode) el.getExtractSpec();

            // A given ExtractPlanNode only handles one part of
            // a multi-dictionary extraction. Pull out the
            // appropriate one.
            int dictIx = e.getExtractSpecIx();

            if (dict.getUseSDM(dictIx) && sdms.containsKey(target)) {
              // We will be applying SDM to this node.
              DictInvocation di = dict.toDictInvocation(dictIx);
              String qualifedName = catalog.getQualifiedDictName(di.dictName);
              if (qualifedName != null) {
                di.dictName = qualifedName;
              }
              SDMNode sdm = sdms.get(target);
              sdm.addPassThroughCols(el.getSelectList());
              // Compute the name of the artificial view
              // that SDM will create for this dictionary
              // extraction.
              FromListItemNode newSource;
              try {
                newSource =
                    new FromListItemViewRefNode(new NickNode(sdm.getAOGOutputNick(di)), false);
              } catch (ParseException exception) {
                throw new RuntimeException("Should never happen.");
              }

              // Immediately below the extraction should
              // be a scan node. Point that scan node at
              // the artificial SDM view.
              ScanNode scan = (ScanNode) (e.getChildren()[0]);
              scan.setWhatToScan(newSource);

              // The SDM operator currently calls every output
              // column "__sdm_match"; rename the column
              // appropriately.
              scan.addRenaming(SDMNode.OUTPUT_COL_NAME, dict.getOutputCols().get(0).getNickname());

              // The scan replaces the original extraction
              // node.
              return scan;
            }
          }
        }

        return p;
      }

      @Override
      public Object getResult() {
        return null;
      }
    }

    // Iterate over the existing plan trees, performing the replacement.
    replaceCB cb = new replaceCB();
    iterate(plans, cb);

    // Now add the shared dictionary plan nodes to our top-level plan.
    plans.addAll(toAdd);
  }

  /**
   * @param re a regular expression extraction
   * @param regexIx index of the sub-expression to check
   * @return true if the indicated sub-expression can be evaluated with Shared Regex Matching
   */
  private static boolean isSRMCompatible(RegexExNode re, int regexIx) {
    return (re.getUseRegexTok() && re.getUseSimpleEngine(regexIx));
  }

  /**
   * @param node a node within a plan
   * @param parents the parents of the node, closest first, going up to the root of the containing
   *        view
   * @return true if the indicated node is evaluated as part of the inner operand of an RSEJoin
   *         operator
   */
  private static boolean evaluatedWithRSE(PlanNode node, ArrayList<PlanNode> parents) {
    // Go up the parents until we find the first join.
    int closestJoinIx = -1;
    for (int i = parents.size() - 1; i >= 0; i--) {
      PlanNode parent = parents.get(i);
      if (parent instanceof JoinNode) {
        closestJoinIx = i;
        break;
      }
    }

    if (-1 == closestJoinIx) {
      // No joins in parents up to view boundary --> Not RSE
      // If the RSE crossed view boundaries, the entire contents of the target view would have been
      // copied into the
      // parent view
      return false;
    }

    JoinNode closestJoin = (JoinNode) parents.get(closestJoinIx);
    PlanNode closestJoinChild =
        closestJoinIx < parents.size() - 1 ? parents.get(closestJoinIx + 1) : node;

    if (false == (closestJoin instanceof RSEJoinNode)) {
      // Closest join not RSEJoin --> Not RSE
      // RSEJoin currently cannot take a join as an inner.
      // Will need to revisit this branch if RSEJoin is expanded to accept a join as the inner
      // argument.
      return false;
    }

    // If we get here, the closest parent join node is an RSEJoin.
    // Check whether this node is the outer or inner operand of the join.
    PlanNode innerOp = closestJoin.inner();
    if (innerOp == closestJoinChild) {
      // Note pointer comparison; we're checking whether the child really is the exact same object
      return true;
    } else {
      // Current node is the outer operand of the join
      return false;
    }

  }

  /**
   * Parameters of a expression extraction that are used for determining whether a set of
   * extractions can be merged via shared regex or dictionary matching.
   */
  private static class ExtractTargetParams extends Pair<ColNameNode, HashSet<String>> {

    // Child node of the extraction; note that this field is NOT used in
    // comparisons between instances of this class!
    private final PlanNode child;

    // Scoped name of the view used in the extraction (e.g. "D" instead of
    // "Document"), as it appears in the output columns of 'child' above.
    // This field is NOT used in comparisons between instances of this
    // class.
    private final String scopedTargetName;

    public ExtractTargetParams(ColNameNode targetCol, ArrayList<String> colsToRetain,
        PlanNode child, String scopedTargetName) {
      super(targetCol, null);

      second = new HashSet<String>();
      second.addAll(colsToRetain);

      this.child = child;
      this.scopedTargetName = scopedTargetName;
    }

    public ColNameNode getTargetCol() {
      return first;
    }

    public ArrayList<String> getColsToRetain() {
      ArrayList<String> ret = new ArrayList<String>();

      ret.addAll(second);

      return ret;
    }

    public PlanNode getChild() {
      return child;
    }

    public String getScopedTargetName() {
      return scopedTargetName;
    }
  }

  private static ExtractTargetParams targetParams(ExtractPlanNode e) {
    // Figure out what field the extraction is done
    // over.
    ColNameNode targetCol = e.getGlobalTargetName();

    // Pull out the child of the extraction (e.g. the scan that produces its
    // inputs)
    PlanNode child = e.child();

    // Figure out which columns this extraction is passing through.
    ArrayList<String> colNames = child.getOutputColNames();

    // Determine what is the nickname of the target view in the context of
    // the extract statement.
    String locallyScopedTargetName = e.getTargetView().getAlias().getNickname();

    // Build up a lookup key so we can check for other
    // extractions that are compatible with this one.
    ExtractTargetParams key =
        new ExtractTargetParams(targetCol, colNames, child, locallyScopedTargetName);
    return key;
  }

  /**
   * Apply the Shared Regex Matching plan transformation. All compatible regexes over a given input
   * view are merged into a single SRM regex plan node.
   * 
   * @param plans plans for each of the views in the current AQL file being compiled
   * @param catalog main catalog, for looking up view information
   */
  @SuppressWarnings("unchecked")
  private void applySRM(ArrayList<PlanNode> plans, final Catalog catalog) {

    final boolean debug = false;

    // Build up a map from target column to regex parameters.
    // We do this by building up a callback and passing the callback to
    // a method that iterates over the plans.
    callback buildCB = new callback() {

      // As with the SDM transformation, we keep a list of regex
      // invocations for each target view/column pair.
      // Key of this map is an object that encapsulates the target of the
      // regex evaluation, as well as what columns need to be passed
      // through.
      // Replaced HashMap with LinkedHashMap to maintain insertion
      // order while traversal. This will help in generating consistent
      // operator graph across platform/jvm combinations.
      Map<ExtractTargetParams, ArrayList<RegexesTokParams>> target2regex

          = new LinkedHashMap<ExtractTargetParams, ArrayList<RegexesTokParams>>();

      @Override
      public PlanNode exec(PlanNode p, ArrayList<PlanNode> parents) {
        if (p instanceof ExtractPlanNode) {

          if (evaluatedWithRSE(p, parents)) {
            // SPECIAL CASE: Don't apply SRM to the inner of an RSEJoin
            return p;
            // END SPECIAL CASE
          }

          ExtractPlanNode e = (ExtractPlanNode) p;

          ExtractListNode el = e.getExtractList();

          if (el.getExtractSpec() instanceof RegexExNode) {
            ExtractTargetParams key = targetParams(e);

            // Fetch the parse tree node for the regex extraction.
            RegexExNode re = (RegexExNode) el.getExtractSpec();

            // This ExtractPlanNode may be part of a multi-regex
            // extraction (e.g. "extract regexes"); pull out the
            // appropriate regex from the set.
            int regexIx = e.getExtractSpecIx();

            // Currently, we only apply SRM to regex extractions on
            // token boundaries that can be executed with the
            // SimpleRegex engine.
            if (isSRMCompatible(re, regexIx)) {
              // We will be applying SRM to this node.
              if (!target2regex.containsKey(key)) {
                // First time -- create a new entry
                target2regex.put(key, new ArrayList<RegexesTokParams>());
              }

              target2regex.get(key).add(re.getRegexTokParams(regexIx));
            }
          }
        }
        return p;
      }

      @Override
      public Object getResult() {
        return target2regex;
      }
    };
    iterate(plans, buildCB);

    Map<ExtractTargetParams, ArrayList<RegexesTokParams>> targetToRegexes = //
        (LinkedHashMap<ExtractTargetParams, ArrayList<RegexesTokParams>>) buildCB.getResult();

    // Since we will be iterating through the plans list, we don't want
    // to add new things to it until the very end. Instead, we build up
    // a list of stuff to add.
    ArrayList<PlanNode> toAdd = new ArrayList<PlanNode>();

    // For every column that has more than one regex applied, create a new
    // SRMNode plan node.
    // Index the SRMNodes by target.
    final HashMap<ExtractTargetParams, SRMNode> srmNodes =
        new HashMap<ExtractTargetParams, SRMNode>();
    for (ExtractTargetParams target : targetToRegexes.keySet()) {
      ArrayList<RegexesTokParams> regexes = targetToRegexes.get(target);

      if (regexes.size() > 1) {
        // Remove duplicates and maintain insertion order, to generate
        // consistent AOG.
        LinkedHashSet<RegexesTokParams> unique = new LinkedHashSet<RegexesTokParams>(regexes);

        if (debug) {
          Log.debug("Using SRM to evaluate %d expressions over %s", unique.size(),
              target.toString());
        }

        // Convert the target from "Document.text" to "D.text"
        ColNameNode localTarget = new ColNameNode(//
            target.getScopedTargetName(), //
            target.getTargetCol().getColnameInTable());

        SRMNode node = new SRMNode(unique, target.getTargetCol().getTabname(), localTarget,
            target.getChild(), target.getColsToRetain(), catalog.getModuleName());
        srmNodes.put(target, node);

        // Defer adding the new node to the top-level plan until
        // we're finished rewriting it.
        toAdd.add(node);
      }
    }

    // Replace the original regex evaluations with scans over the results of
    // the SRMNode plans.
    // Again, we create a callback and pass the callback to an iteration
    // function.
    final class replaceCB implements callback {

      @Override
      public PlanNode exec(PlanNode p, ArrayList<PlanNode> parents) {
        if (p instanceof ExtractPlanNode) {

          if (evaluatedWithRSE(p, parents)) {
            // SPECIAL CASE: Don't apply SRM to the inner of an RSEJoin
            return p;
            // END SPECIAL CASE
          }

          // Unpack the regex reference as before.
          ExtractPlanNode e = (ExtractPlanNode) p;
          ExtractListNode el = e.getExtractList();

          if (el.getExtractSpec() instanceof RegexExNode) {
            // Figure out what field the extraction is done
            // over; we'll use that target as the key for a hash
            // lookup on the SRM nodes.
            ExtractTargetParams key = targetParams(e);

            // Fetch the parse tree node for the regex extraction.
            RegexExNode re = (RegexExNode) el.getExtractSpec();

            // This ExtractPlanNode may be part of a multi-regex
            // extraction (e.g. "extract regexes"); pull out the
            // appropriate regex from the set.
            int regexIx = e.getExtractSpecIx();

            if (isSRMCompatible(re, regexIx) && srmNodes.containsKey(key)) {
              // We will be applying SRM to this node.
              RegexesTokParams params = re.getRegexTokParams(regexIx);
              SRMNode srm = srmNodes.get(key);

              // Compute the name of the artificial "view" that
              // SRM will create for this particular regex
              // extraction.
              FromListItemNode newSource;
              try {
                newSource = new FromListItemViewRefNode(
                    new NickNode(srm.getAOGOutputNick(params), null, null), false);
              } catch (ParseException exception) {
                throw new RuntimeException("Should never happen.");
              }

              // Create a scan over the artificial view.
              ScanNode scan;
              try {
                scan = new ScanNode(newSource, null, catalog);
              } catch (ParseException pe) {
                // This should never happen
                throw new RuntimeException(pe);
              }

              // Old code modified a scan node in place.
              // ScanNode scan = (ScanNode) (e.getChildren()[0]);
              // scan.setWhatToScan(newSource);

              // The scan replaces the original extraction
              // node.
              return scan;
            }
          }
        }

        return p;
      }

      @Override
      public Object getResult() {
        return null;
      }
    }

    // Iterate over the existing plan trees, performing the replacement.
    replaceCB cb = new replaceCB();
    iterate(plans, cb);

    // Now add the shared regex plan nodes to our top-level plan.
    plans.addAll(toAdd);
  }
}
