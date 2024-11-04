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
package com.ibm.avatar.provenance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Properties;

import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.function.base.ScalarFunc;
import com.ibm.avatar.algebra.function.predicate.Follows;
import com.ibm.avatar.aql.ColNameNode;
import com.ibm.avatar.aql.ScalarFnCallNode;
import com.ibm.avatar.aql.PredicateNode;
import com.ibm.avatar.aql.SelectNode;
import com.ibm.avatar.aql.StringNode;
import com.ibm.avatar.aql.ViewBodyNode;
import com.ibm.avatar.aql.WhereClauseNode;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.logging.Log;

/**
 * Generate low level changes from a high level change. A new LLCM correspond to one high level
 * change.
 */
public class LowLevelChangeModule {

  /**
   * @param hlc High Level Change
   * @param negatives negative tuples in the view of given HLC
   * @param positives positive tuples in the view of given HLC
   * @param missing tuples that should be there but are not; currently ignored.
   * @param tupleCacheMap, HashMap<Integer, Pair<Tuple, String>>
   * @param oneLevelProvMap
   * @param positiveIds *Current* positive IDs in the output view; this excludes all tuples removed
   *        by upstream changes applied.
   * @param negativeIds *Current* negative IDs in the output view. LLCM should have access to the
   *        viewname to ViewBodyNode (original) maps to figure out what part of the view to change.
   *        Therefore parser is not needed. Obviously the performance of this function depends on
   *        the number of labeled results obtained from the user. We should probably evaluate using
   *        this as a metric.
   */

  public static ArrayList<LowLevelChange> genLowLevelChanges(HighLevelChange hlc,
      HashMap<String, ViewBodyNode> rewritten2OriginalCopyMap, ArrayList<Integer> localNeg,
      ArrayList<Integer> missing, ArrayList<Integer> localPos,
      HashMap<Integer, Pair<Tuple, String>> tupleCacheMap, HashMap<String, TupleSchema> schemas,
      HashMap<Integer, Pair<String, ArrayList<Integer>>> oneLevelProvMap,
      ArrayList<Integer> positiveIds, ArrayList<Integer> negativeIds,
      HashMap<String, HashMap<String, ArrayList<Span>>> filterTuples, Properties props,
      Catalog catalog) {

    ViewBodyNode node = rewritten2OriginalCopyMap.get(hlc.getViewName());
    ArrayList<LowLevelChange> llcList = new ArrayList<LowLevelChange>();

    if (AQLRefine.DEBUG)
      Log.debug("HLC: " + hlc.toString());

    if (hlc.getChangeType().equals(AQLRefine.CHANGE_WHERE)) {
      WhereClauseNode wc = ((SelectNode) node).getWhereClause();
      PredicateNode pred = null;
      for (int i = 0; i < wc.size(); i++) {
        pred = wc.getPred(i);
        // see if the predicate is a supported type
        if (pred.isFuncCall()) {
          ScalarFnCallNode fNode = pred.getFunc();
          if (AQLRefine.DEBUG)
            Log.debug("Function in predicate is: " + fNode.getFuncName());

          if (fNode.getFuncName().equals(ScalarFunc.computeFuncName(Follows.class))) {

            // removed parser from the function.
            ChangeFollowsMP cf = new ChangeFollowsMP(localNeg, missing, localPos, tupleCacheMap,
                schemas, pred, oneLevelProvMap, node, hlc.getViewName(), props, catalog);

            llcList.addAll(cf.genChanges(positiveIds, negativeIds));
            /**
             * In order to rank the changes, LLC must return the list of tuples that will disappear
             * after the change.
             */
          } else if (fNode.getFuncName().equals("Not")) {
            // now see if there is a "NOT" in front
            if (fNode.getArgs().size() > 1) {
              Log.debug("Not knowing what to do. Args size > 1 in genLowLevelChanges");
            } else {
              ScalarFnCallNode fNode2 = (ScalarFnCallNode) fNode.getArgs().get(0);

              if (fNode2.getFuncName().contains("ContainsDict")) {
                // FIXME: currently do not support functions like "leftconext"
                if (fNode2.getArgs().get(1) instanceof ScalarFnCallNode
                    || fNode2.getArgs().get(1) instanceof StringNode
                    || fNode2.getArgs().get(1) instanceof ColNameNode) {
                  // ){
                  ChangeContainsDict ccd = new ChangeContainsDict(localNeg, missing, localPos,
                      tupleCacheMap, oneLevelProvMap, schemas, fNode2, node, true,
                      hlc.getViewName(), props, catalog);
                  llcList.addAll(ccd.genChanges(positiveIds, negativeIds));
                }
              } else {
                if (AQLRefine.DEBUG)
                  Log.debug(fNode2.getFuncName() + " is unsupported");
              }
            }
          }
          // no negation in front of ContainsDict
          else if (fNode.getFuncName().contains("ContainsDict")) {
            if (fNode.getArgs().get(1) instanceof ScalarFnCallNode
                || fNode.getArgs().get(1) instanceof ColNameNode) {
              ChangeContainsDict ccd = new ChangeContainsDict(localNeg, missing, localPos,
                  tupleCacheMap, oneLevelProvMap, schemas, fNode, node, false, hlc.getViewName(),
                  props, catalog);
              ccd.genChanges(positiveIds, negativeIds);
            }
          }
        }
      }
    } else if (hlc.getChangeType().equals(AQLRefine.CHANGE_DICT)) {
      ChangeExtractDict cd = new ChangeExtractDict(localNeg, missing, localPos, tupleCacheMap,
          schemas, oneLevelProvMap, node, hlc.getViewName(), props, catalog);
      llcList.addAll(cd.genChanges(positiveIds, negativeIds));
    } else if (hlc.getChangeType().equals(AQLRefine.CHANGE_SUBTRACT)) {
      ChangeFilterView cv = new ChangeFilterView(localNeg, missing, localPos, tupleCacheMap,
          schemas, oneLevelProvMap, node, hlc.getViewName(), filterTuples, props, catalog);
      llcList.addAll(cv.genChanges(positiveIds, negativeIds));
    }
    return llcList;
  }

  public static LowLevelChange getBestChange(ArrayList<LowLevelChange> llcs) {
    if (llcs.size() == 0) {
      Log.debug("Error in getBestChange: llcs is empty");
      return null;
    }
    LowLevelChange result = llcs.get(0);

    for (int i = 1; i < llcs.size(); i++) {
      if (result.compareTo(llcs.get(i)) > 0)
        continue;
      else
        result = llcs.get(i);
    }
    return result;
  }

  public static ArrayList<LowLevelChange> sortLLC(ArrayList<LowLevelChange> llcs) {
    Object[] llcArray = llcs.toArray();
    Arrays.sort(llcArray, new LLCComparator());
    ArrayList<LowLevelChange> result = new ArrayList<LowLevelChange>();
    for (int i = 0; i < llcArray.length; i++) {
      result.add((LowLevelChange) llcArray[i]);
    }
    return result;
  }

  /**
   * Applies a LLC and removes all affected tuples by propagation in the provenance tree
   * 
   * @param positives list of positive ids in the output view
   * @param negatives negatives in the output view
   * @param llc LLC to be applied
   */
  public static void applyLLC(ArrayList<Integer> positives, ArrayList<Integer> negatives,
      LowLevelChange llc, ArrayList<Integer> removedNonResults,
      HashMap<Integer, Pair<String, ArrayList<Integer>>> oneLevelProvMap) {

    if (AQLRefine.DEBUG)
      System.out.println("Applying LLC " + llc.toString());

    ArrayList<Integer> removed = new ArrayList<Integer>();

    ArrayList<Integer> removedPosResult = new ArrayList<Integer>();
    ArrayList<Integer> removedNegResult = new ArrayList<Integer>();

    if (llc.getRemovedLocalPos() != null)
      removed.addAll(llc.getRemovedLocalPos());

    if (llc.getRemovedLocalNeg() != null)
      removed.addAll(llc.getRemovedLocalNeg());

    removedNonResults.addAll(removed);

    if (!removed.isEmpty()) {
      propagateRemoval(removed, oneLevelProvMap, removedNonResults, positives, negatives,
          removedPosResult, removedNegResult);
    }

    positives.removeAll(removedPosResult);
    negatives.removeAll(removedNegResult);
  }

  /**
   * Accessing the reverseMap and oneLevelProvMap to remove all affected IDs in <code>target</code>
   * if IDs in <code>removed</code> are removed. Need to find out removed tuples in both
   * intermediate views AND output view.
   * 
   * @param removed list of IDs in the provenance tree (except the output view) to be removed
   * @param oneLevelProvMap
   * @param removedNonResults tuples that will be removed in the provenance tree
   * @param positives positives tuples in the output view
   * @param negatives negative tuples in the output view
   * @param removedPosResult positive tuples that will be removed from the output view
   * @param removedNegResult negative tuples that will be removed from the output view
   */
  public static void propagateRemoval(ArrayList<Integer> removed,
      HashMap<Integer, Pair<String, ArrayList<Integer>>> oneLevelProvMap,
      ArrayList<Integer> removedNonResults, ArrayList<Integer> positives,
      ArrayList<Integer> negatives, ArrayList<Integer> removedPosResult,
      ArrayList<Integer> removedNegResult) {

    // first, find out all removed ids, except those in output view
    for (int i : removed) {
      recursiveRemoval(i, oneLevelProvMap, removedNonResults, AQLRefine.getReverseMap());
    }

    // now figure out which tuples in the output view will be removed.
    boolean isRemoved = true;

    ArrayList<Integer> consolidateSource = new ArrayList<Integer>();
    ArrayList<Integer> possibleOutputRemoval = new ArrayList<Integer>();

    for (int i : removedNonResults) {
      if (!oneLevelProvMap.containsKey(i))
        System.out.println("Error in propagateRemoval: tuple is not in prov map. ");

      if (oneLevelProvMap.get(i).first.equals(AQLRefine.getConsolidateCandidateView())) {
        consolidateSource.add(i);
        for (int j : AQLRefine.consolidateSourceToTarget.get(i))
          if (!possibleOutputRemoval.contains(j))
            possibleOutputRemoval.add(j);
      }
    }

    Pair<String, ArrayList<Integer>> parentPair = null;
    Pair<String, ArrayList<Integer>> grantparentPair = null;
    ArrayList<Integer> removedOutput = new ArrayList<Integer>();

    for (int i : possibleOutputRemoval) {
      isRemoved = true;
      parentPair = oneLevelProvMap.get(i); // find provenance in the level up (e.g.,
                                           // __Consolidate__6__Temp__1)
      for (int j : parentPair.second) {
        grantparentPair = oneLevelProvMap.get(j);
        for (int k : grantparentPair.second) {
          if (oneLevelProvMap.get(k).first.equals(AQLRefine.getConsolidateCandidateView())) {
            if (!consolidateSource.contains(k)) {
              isRemoved = false;
              if (AQLRefine.DEBUG)
                System.out.println(
                    "Tuple " + i + " is not removed because source tuple " + k + " isn't removed");
            }
          }
        }
      }

      if (isRemoved && !removedOutput.contains(i)) {
        removedOutput.add(i);
        // if (AQLRefine.DEBUG)
        // System.out.println("Tuple " + i + " is removed");
      }
    }
    possibleOutputRemoval.clear();
    possibleOutputRemoval = null;

    for (int i : removedOutput) {
      if (positives.contains(i))
        removedPosResult.add(i);
      if (negatives.contains(i))
        removedNegResult.add(i);
    }

    removedOutput.clear();
    removedOutput = null;
  }

  // Recursively remove an id from the provenance tree, until the level of consolidateCandidateView
  public static void recursiveRemoval(int id,
      HashMap<Integer, Pair<String, ArrayList<Integer>>> oneLevelProvMap,
      ArrayList<Integer> toRemove,
      HashMap<Integer, HashMap<String, ArrayList<Integer>>> reverseMap) {
    ArrayList<Integer> ids = null;
    HashMap<String, ArrayList<Integer>> entry = null;
    ArrayList<Integer> desc = null;
    // see if the result is already cached
    if (AQLRefine.descendants.containsKey(id)) {
      if (AQLRefine.DEBUG)
        System.out.println("Found cache for tuple " + id);
      ids = AQLRefine.descendants.get(id);
      for (int i : ids) {
        if (!toRemove.contains(i)) {
          toRemove.add(i);
        }
      }
    } else { // compute the list of descendants and put into cache
      desc = new ArrayList<Integer>();
      if (reverseMap.containsKey(id)) {
        entry = reverseMap.get(id);
        for (String view : entry.keySet()) {
          ids = entry.get(view);
          for (int j : ids) {
            // if (!toRemove.contains(j)) {
            desc.add(j);
            recursiveRemoval(j, oneLevelProvMap, desc, reverseMap);
            // }
          }
        }
        // put results in cache
        AQLRefine.descendants.put(id, desc);
        for (int i : desc) {
          if (!toRemove.contains(i))
            toRemove.add(i);
        }
      } else {
        // reached the level of consolidateCandidateView
        toRemove.add(id);
      }
    }
  }
}


class LLCComparator implements Comparator<Object> {
  @Override
  public int compare(Object o1, Object o2) {
    return ((LowLevelChange) o2).compareTo(o1);
  }
}
