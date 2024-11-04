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
import java.util.HashMap;
import java.util.Properties;

import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.aql.FromListNode;
import com.ibm.avatar.aql.PredicateNode;
import com.ibm.avatar.aql.RValueNode;
import com.ibm.avatar.aql.SelectNode;
import com.ibm.avatar.aql.ViewBodyNode;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.logging.Log;

/**
 * Changes the Follow predicate by varying the two distance parameters. Algorithm Summary: Penalty
 * Assignment: According to the number of predicates introduced (k).
 */
public class ChangeFollowsMP extends ChangeGenerator {

  // first and second parameter of Follow()

  // private PredicateNode follow;
  // private Pair<Integer, Integer> posRange;
  // private Pair<Integer, Integer> negRange;
  private ViewBodyNode node;
  private String thisView;

  public ViewBodyNode getNode() {
    return node;
  }

  public void setNode(ViewBodyNode node) {
    this.node = node;
  }

  private String first, second; // first and second view type in "Follow" predicate

  public String getFirst() {
    return first;
  }

  public void setFirst(String first) {
    this.first = first;
  }

  public String getSecond() {
    return second;
  }

  public void setSecond(String second) {
    this.second = second;
  }

  // private int leftInit, rightInit; //initial values of follows parameters

  public ChangeFollowsMP(ArrayList<Integer> localNeg, ArrayList<Integer> missing,
      ArrayList<Integer> localPos, HashMap<Integer, Pair<Tuple, String>> tupleCacheMap,
      HashMap<String, TupleSchema> schemas, PredicateNode follow,
      HashMap<Integer, Pair<String, ArrayList<Integer>>> oneLevelProvMap, ViewBodyNode node,
      String thisView, Properties props, Catalog catalog) {
    super(localNeg, missing, localPos, tupleCacheMap, schemas, oneLevelProvMap, props, catalog);
    // this.follow = follow;
    this.node = node;
    this.thisView = thisView;
    // extract parameter first and second
    ArrayList<RValueNode> args = follow.getFunc().getArgs();

    first = args.get(0).toString(); // name.name
    second = args.get(1).toString(); // phone.num

    // leftInit = Integer.parseInt(args.get(2).toString());
    // rightInit = Integer.parseInt(args.get(3).toString());

  }

  @Override
  public ArrayList<LowLevelChange> genChanges(ArrayList<Integer> positiveIds,
      ArrayList<Integer> negativeIds) {
    ArrayList<LowLevelChange> result = new ArrayList<LowLevelChange>();

    // a map from all ids of tuples in current view to distance values
    HashMap<Integer, Integer> distMap = getActualDist();

    // reverse map; ids are for tuples in this view (not the output view)
    HashMap<Integer, ArrayList<Integer>> distToId = new HashMap<Integer, ArrayList<Integer>>();

    // utility sequence of each distance value
    HashMap<Integer, Double> utilSeq = genUtilitySeq(distMap, positiveIds, negativeIds, distToId);

    // sort the util seq by distance
    Object[] distArray = distToId.keySet().toArray();
    Arrays.sort(distArray);
    ArrayList<Double> seq = new ArrayList<Double>();
    for (int i = 0; i < distArray.length; i++) {
      seq.add(i, utilSeq.get(distArray[i]));
    } // seq now contains utility values of each distance, matching distance value in distArray

    /*
     * Get the top k subsequences; the best change in terms of F-score will be all the ranges with
     * positive total score. Here is how the LLCs are generated: 1. top 1 2. top 2 ranges 3. top 3
     * ranges, ... So we list changes in increasing number of ranges, thus decreasing readability.
     * These are the only reasonable choices, since any other possible combination will be
     * dominated.
     */

    ArrayList<Pair<Integer, Integer>> ranges = kMaxPositiveSubsequence(maxNumRanges, seq);

    /**
     * Report the suggested changes with performance values; Should also give us a list of tuples
     * that will be gone.
     */
    double fScoreDelta;
    ArrayList<Integer> removed = new ArrayList<Integer>();

    ArrayList<Integer> removedNegatives = new ArrayList<Integer>();
    ArrayList<Integer> casualities = new ArrayList<Integer>();
    String rangeStr = "";
    fScoreDelta = 0;
    double penalty;
    int count = 1;
    for (Pair<Integer, Integer> pair : ranges) {

      removed.clear();

      rangeStr += distArray[pair.first] + ", " + distArray[pair.second] + "; ";

      for (int i = pair.first; i <= pair.second; i++) {
        if (i < 0)
          Log.debug("Error in genChanges: i is < 0");
        fScoreDelta += seq.get(i);

        removed.addAll(distToId.get(distArray[i]));
      }

      // find out removed negatives and casualties
      for (int i : removed) {
        if (this.getLocalPos().contains(i)) {
          casualities.add(i);
        } else if (this.getLocalNeg().contains(i))
          removedNegatives.add(i);
        else
          Log.debug("Error in genChanges in ChangeFollows");
      }

      // decide the penalty value: if the range removed is on the boundary, no penalty; otherwise, 1
      // unit
      if (pair.first == 0 || pair.second == distArray.length - 1)
        penalty = 0.0;
      else {
        penalty = count * rangePenalty;
        count++;
      }

      LowLevelChange llc = null;

      if (AQLRefine.DEBUG) {
        String s = "";
        ArrayList<Integer> removedPosResult = new ArrayList<Integer>();
        ArrayList<Integer> removedNegResult = new ArrayList<Integer>();
        double gain = getFMeasureChange(removed, getOneLevelProvMap(), getTupleCacheMap(),
            positiveIds, negativeIds, removedPosResult, removedNegResult, beta);
        if (Math.abs(gain - fScoreDelta) > 0.00001)
          System.out
              .println("Error in genChanges() in ChangeFollowsMP: gain doesn't match fScoreDelta");

        s += "\n Removed positive output: ";
        if (removedPosResult != null)
          for (int j : removedPosResult) {
            s += j + " ";
          }
        s += "\n Removed negative output: ";
        if (removedNegResult != null)
          for (int j : removedNegResult) {
            s += j + " ";
          }
        llc = new LowLevelChange(new ArrayList<Integer>(removedNegatives),
            new ArrayList<Integer>(casualities), true,
            "Remove ranges: " + rangeStr + " from view " + thisView + s, fScoreDelta, penalty,
            fWeight);
      } else
        llc = new LowLevelChange(new ArrayList<Integer>(removedNegatives),
            new ArrayList<Integer>(casualities), true,
            "Remove ranges: " + rangeStr + " from view " + thisView, fScoreDelta, penalty, fWeight);
      result.add(llc);
    }

    return result;
  }

  /**
   * look through all positive and negative examples, and find out the list of distance values in
   * all positive and negative examples.
   * 
   * @return map from tuple id to distance value
   */
  public HashMap<Integer, Integer> getActualDist() {

    // result to return; map each tuple ID to its actual distance in Follows
    HashMap<Integer, Integer> distMap = new HashMap<Integer, Integer>();

    ArrayList<Integer> positives = this.getLocalPos();
    ArrayList<Integer> negatives = this.getLocalNeg();
    HashMap<Integer, Pair<String, ArrayList<Integer>>> oneLevelProvMap = this.getOneLevelProvMap();

    // figure out the viewname of arguments
    FromListNode fromList = ((SelectNode) node).getFromList();

    // view name corresponding to the first and second argument
    String view1 = "";
    String view2 = "";

    // note: use .getAlias().toString won't work
    if (first.contains(fromList.get(0).getAlias().getNickname())) {
      view1 = fromList.get(0).getExternalName();
      view2 = fromList.get(1).getExternalName();
    } else {
      view2 = fromList.get(0).getExternalName();
      view1 = fromList.get(1).getExternalName();
    }
    String[] views = {view1, view2};

    HashMap<String, TupleSchema> schemas = this.getSchemas();
    HashMap<String, ArrayList<Integer>> idProv = new HashMap<String, ArrayList<Integer>>();
    ArrayList<Integer> prov1, prov2;

    TupleSchema[] schema = {schemas.get(view1), schemas.get(view2)};
    Tuple[] tups = new Tuple[2];
    ArrayList<Integer> allIDs = new ArrayList<Integer>();
    allIDs.addAll(negatives);
    allIDs.addAll(positives);

    for (int i : allIDs) {
      idProv.clear();
      AQLRefine.getIdProvenance(i, oneLevelProvMap, idProv);

      prov1 = idProv.get(view1);
      prov2 = idProv.get(view2);

      tups[0] = this.getTupleCacheMap().get(prov1.get(0)).first;
      tups[1] = this.getTupleCacheMap().get(prov2.get(0)).first;

      distMap.put(i, getTupDist(tups, schema, views));

      prov1 = null;
      prov2 = null;
    }

    tups = null;
    return distMap;
  }

  /**
   * find the actual distance in Follows for a particular output tuple from its upstream tuples
   * 
   * @param tups
   * @param schemas
   * @param views
   * @return
   */
  public int getTupDist(Tuple[] tups, TupleSchema[] schemas, String[] views) {
    int result;

    int dotPos = first.indexOf(".");
    FieldGetter<Span> getStr1 = schemas[0].spanAcc(first.substring(dotPos + 1));
    dotPos = second.indexOf(".");
    FieldGetter<Span> getStr2 = schemas[1].spanAcc(second.substring(dotPos + 1));

    Span s1 = getStr1.getVal(tups[0]), s2 = getStr2.getVal(tups[1]);

    result = s2.getBegin() - s1.getEnd();
    if (result < 0)
      Log.debug("Error in computeDist", result);
    return result;
  }

  /**
   * For each possible distance value, find its utility, which is defined by change of F measure.
   * This function also fills in values in distToId, which maps each distance value to a list of ids
   * that has that distance.
   * 
   * @return HashMap, key is distance value, object is the utility value (change in F-measure).
   */
  private HashMap<Integer, Double> genUtilitySeq(HashMap<Integer, Integer> distMap,
      ArrayList<Integer> positiveIds, ArrayList<Integer> negativeIds,
      HashMap<Integer, ArrayList<Integer>> distToId) {

    HashMap<Integer, Double> result = new HashMap<Integer, Double>();
    // build a reserve hashmap, from dist value to tuple IDs that has the corresponding distance
    // HashMap<Integer, ArrayList<Integer>> distToId = new HashMap<Integer, ArrayList<Integer>>();

    ArrayList<Integer> list = null;
    int dist;
    for (int i : distMap.keySet()) {
      dist = distMap.get(i);
      if (distToId.containsKey(dist)) {
        list = distToId.get(dist);
        list.add(i);
        distToId.put(dist, list);
      } else {
        ArrayList<Integer> newList = new ArrayList<Integer>();
        newList.add(i);
        distToId.put(dist, newList);
      }
    }

    // consider removing each distance value
    ArrayList<Integer> idList;
    double fScoreChange;
    for (int i : distToId.keySet()) {

      // get the list of affected tuples
      idList = distToId.get(i);
      // see the effect of those tuples on FMeasure
      fScoreChange = getFMeasureChange(idList, this.getOneLevelProvMap(), this.getTupleCacheMap(),
          positiveIds, negativeIds, beta);
      result.put(i, fScoreChange);
    }
    return result;
  }

  /**
   * Use modified version of Kadane's Algorithm to find k max subsequence. The orignal alg. works
   * for k=1. This extension is based on paper "Algorithms for the Problem of K Maximum Sums and a
   * VLSI Algorithm for the K Maximum Subarrays Problem", 2004, IEEE. This function returns *at most
   * k* subsequences that have a positive total sum.
   * 
   * @param k
   * @param seq
   * @return
   */
  private ArrayList<Pair<Integer, Integer>> kMaxPositiveSubsequence(int k, ArrayList<Double> _seq) {
    ArrayList<Double> seq = new ArrayList<Double>();
    seq.addAll(_seq);
    ArrayList<Pair<Integer, Integer>> result = new ArrayList<Pair<Integer, Integer>>();

    int start = 0, end = 0;
    double s = -Double.MAX_VALUE, t = 0;
    int subStart = 0;
    boolean cont = true; // whether to continue
    for (int i = 0; i < k && cont; i++) {
      start = 0;
      end = 0;
      s = -Double.MAX_VALUE;
      t = 0;
      subStart = 0;
      for (int j = 0; j < seq.size(); j++) {
        t = t + seq.get(j);
        if (t > s) {
          start = subStart;
          end = j;
          s = t;
        }
        if (t < 0) {
          t = 0;
          subStart = j + 1;
        }
      }

      // if not improvement in f-score, return. Thus we considers only changes that can bring
      // improvement in f-score.
      if (s < 0)
        break;

      // set marked sequence in the current iteration as all negative infinity
      for (int j = start; j <= end; j++) {
        seq.set(j, -Double.MAX_VALUE);
      }

      // put start and end to result
      Pair<Integer, Integer> pair = new Pair<Integer, Integer>(start, end);
      result.add(i, pair);

      // sometimes it's impossible to have k sub-sequences, and in this case, all seq values will be
      // set as -Double.Ma
      cont = false;
      for (Double val : seq) {
        if (val > -Double.MAX_VALUE) {
          cont = true;
          break;
        }
      }
    }
    return result;
  }

  /**
   * locally evaluate this change
   * 
   * @param left
   * @param right
   * @return
   */
  public boolean evaluate() {
    return true;
  }
}
