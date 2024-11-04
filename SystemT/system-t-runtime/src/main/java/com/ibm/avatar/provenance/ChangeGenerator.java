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
import java.util.HashMap;
import java.util.Properties;

import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.aql.catalog.Catalog;

/**
 * Abstract base class for low level change generators. Naming convention: localPos/localNeg refers
 * to positives/negatives in the view under consideration. positive(ids)/negative(ids) referes to
 * ids in the *output* view (visible to user).
 */
public abstract class ChangeGenerator {

  private ArrayList<Integer> localNeg;
  // list of missing tuples that CGM will attempt to add, for EACH document; key is docLabel.
  private ArrayList<Integer> missing;
  // list of positive tuples.
  private ArrayList<Integer> localPos;
  private HashMap<Integer, Pair<String, ArrayList<Integer>>> oneLevelProvMap;

  private HashMap<String, TupleSchema> schemas;

  protected Catalog catalog;

  // full set of input tuples to the view, broken down first by viewName, and then document
  private HashMap<Integer, Pair<Tuple, String>> tupleCacheMap;
  protected double beta;
  protected double rangePenalty;
  protected int maxNumRanges;
  protected double fWeight;
  protected double filterPenalty;
  protected double dictPenalty;
  protected String[] filterViews;

  public HashMap<Integer, Pair<Tuple, String>> getTupleCacheMap() {
    return tupleCacheMap;
  }

  public void setTupleCacheMap(HashMap<Integer, Pair<Tuple, String>> tupleCacheMap) {
    this.tupleCacheMap = tupleCacheMap;
  }

  public void setCatalog(Catalog catalog) {
    this.catalog = catalog;
  }

  public ChangeGenerator(ArrayList<Integer> localNeg, ArrayList<Integer> missing,
      ArrayList<Integer> localPos, HashMap<Integer, Pair<Tuple, String>> tupleCacheMap,
      HashMap<String, TupleSchema> schemas,
      HashMap<Integer, Pair<String, ArrayList<Integer>>> oneLevelProvMap, Properties props,
      Catalog catalog) {
    this.localNeg = localNeg;
    this.missing = missing;
    this.localPos = localPos;
    this.tupleCacheMap = tupleCacheMap;
    this.schemas = schemas;
    this.oneLevelProvMap = oneLevelProvMap;
    this.catalog = catalog;

    this.beta = Double.parseDouble((String) props.get(RefinerConstants.REFINER_BETA_PROP));
    this.rangePenalty =
        Double.parseDouble((String) props.get(RefinerConstants.REFINER_RANGE_PENALTY_PROP));
    this.filterPenalty =
        Double.parseDouble((String) props.get(RefinerConstants.REFINER_FILTER_PENALTY_PROP));
    this.dictPenalty =
        Double.parseDouble((String) props.get(RefinerConstants.REFINER_DICT_PENALTY_PROP));
    this.maxNumRanges =
        Integer.parseInt((String) props.get(RefinerConstants.REFINER_MAX_NUM_RANGES_PROP));
    this.fWeight = Double.parseDouble((String) props.get(RefinerConstants.REFINER_F_WEIGHT_PROP));
    this.filterViews =
        ((String) props.get(RefinerConstants.REFINER_OVERLAP_FILTER_VIEWS_PROP)).split("\\s*,\\s*");
  }

  /**
   * Find out the change of F-measure if the list of tuples are removed. Note that the tuples in
   * <code>ids</code> are not necessarily in the output view. Need: positiveids, and negativeids
   * from refine Update on 01/10/2010: fix bug for consolidation
   * 
   * @param localIds tuples to be removed at the view being refined (not necessarily the output
   *        view)
   * @return
   */
  public double getFMeasureChange(ArrayList<Integer> localIds,
      HashMap<Integer, Pair<String, ArrayList<Integer>>> oneLevelProvMap,
      HashMap<Integer, Pair<Tuple, String>> tupleCacheMap, ArrayList<Integer> positiveIds,
      ArrayList<Integer> negativeIds, double beta) {
    double result;

    if (localIds.isEmpty())
      return 0;

    double initialFscore = pseudoFMeasure(positiveIds, negativeIds, null, null, beta);

    ArrayList<Integer> removed = new ArrayList<Integer>();
    // String outputView = AQLRefine.getRequestedView();
    // ArrayList<Pair<String, ArrayList<Integer>>> entry;

    // first, remove all tuples in ids
    if (oneLevelProvMap.get(localIds.get(0)).first.equals(AQLRefine.getRequestedView()))
      removed.addAll(localIds);

    ArrayList<Integer> removedNonResult = new ArrayList<Integer>();
    ArrayList<Integer> removedPosResult = new ArrayList<Integer>();
    ArrayList<Integer> removedNegResult = new ArrayList<Integer>();

    LowLevelChangeModule.propagateRemoval(localIds, oneLevelProvMap, removedNonResult, positiveIds,
        negativeIds, removedPosResult, removedNegResult);

    // now see what's changed in positive and negative
    double fscore =
        pseudoFMeasure(positiveIds, negativeIds, removedPosResult, removedNegResult, beta);
    result = fscore - initialFscore;
    return result;
  }

  /**
   * Same function as above, but retaining info on removed output tuples (in the output view, not
   * local view).
   * 
   * @param localIds
   * @param oneLevelProvMap
   * @param tupleCacheMap
   * @param positiveIds
   * @param negativeIds
   * @param removedPosResult
   * @param removedNegResult
   * @return
   */
  public double getFMeasureChange(ArrayList<Integer> localIds,
      HashMap<Integer, Pair<String, ArrayList<Integer>>> oneLevelProvMap,
      HashMap<Integer, Pair<Tuple, String>> tupleCacheMap, ArrayList<Integer> positiveIds,
      ArrayList<Integer> negativeIds, ArrayList<Integer> removedPosResult,
      ArrayList<Integer> removedNegResult, double beta) {
    double result;

    if (localIds.isEmpty())
      return 0;

    double initialFscore = pseudoFMeasure(positiveIds, negativeIds, null, null, beta);

    ArrayList<Integer> removed = new ArrayList<Integer>();

    // first, remove all tuples in ids
    if (oneLevelProvMap.get(localIds.get(0)).first.equals(AQLRefine.getRequestedView()))
      removed.addAll(localIds);

    ArrayList<Integer> removedNonResult = new ArrayList<Integer>();

    LowLevelChangeModule.propagateRemoval(localIds, oneLevelProvMap, removedNonResult, positiveIds,
        negativeIds, removedPosResult, removedNegResult);

    // now see what's changed in positive and negative
    double fscore =
        pseudoFMeasure(positiveIds, negativeIds, removedPosResult, removedNegResult, beta);
    result = fscore - initialFscore;
    return result;
  }

  /**
   * Compute F measure, given parameter of beta, which controls weight of Precision and Recall. This
   * function considers a positiveIds are correct and they are all the correct ones. Thus recall
   * before removal is 1. This is the pseudo F-measure because we don't have the golden standard
   * here.
   * 
   * @param beta
   * @return
   */
  public double pseudoFMeasure(ArrayList<Integer> _positiveIds, ArrayList<Integer> _negativeIds,
      ArrayList<Integer> _removedPos, ArrayList<Integer> _removedNeg, double beta) {
    double result;
    double P, R;
    if (_removedPos == null && _removedNeg == null) {
      P = (double) _positiveIds.size()
          / ((double) _positiveIds.size() + (double) _negativeIds.size());
      R = 1.0;
    } else {
      // there might be duplicates in "removed"
      ArrayList<Integer> positiveIds = new ArrayList<Integer>(_positiveIds);
      ArrayList<Integer> negativeIds = new ArrayList<Integer>(_negativeIds);

      for (Integer i : _removedPos) {
        positiveIds.remove(i);
      }
      for (Integer i : _removedNeg)
        negativeIds.remove(i);

      if (positiveIds.isEmpty())
        return 0;

      P = (double) positiveIds.size() / ((double) positiveIds.size() + (double) negativeIds.size());
      R = (double) positiveIds.size() / (double) _positiveIds.size();
    }

    // Formula according to P156, Stanford IR book.
    result = (beta * beta + 1) * P * R / (beta * beta * P + R);

    return result;
  }

  public HashMap<Integer, Pair<String, ArrayList<Integer>>> getOneLevelProvMap() {
    return oneLevelProvMap;
  }

  public void setOneLevelProvMap(
      HashMap<Integer, Pair<String, ArrayList<Integer>>> oneLevelProvMap) {
    this.oneLevelProvMap = oneLevelProvMap;
  }

  public abstract ArrayList<LowLevelChange> genChanges(ArrayList<Integer> positiveIds,
      ArrayList<Integer> negativeIds);

  public ArrayList<Integer> getLocalNeg() {
    return localNeg;
  }

  public void setLocalNeg(ArrayList<Integer> negatives) {
    this.localNeg = negatives;
  }

  public ArrayList<Integer> getMissing() {
    return missing;
  }

  public ArrayList<Integer> getLocalPos() {
    return localPos;
  }

  public HashMap<String, TupleSchema> getSchemas() {
    return schemas;
  }

  public void setSchemas(HashMap<String, TupleSchema> schemas) {
    this.schemas = schemas;
  }

  // result stored in sum
  public static void addListWithoutDup(ArrayList<Integer> sum, ArrayList<Integer> source) {
    for (int i : source) {
      if (!sum.contains(i))
        sum.add(i);
    }
  }
}
