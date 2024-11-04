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

/**
 * Change dictionary used in extraction. Doesn't handle the negation case: where not (in
 * Dictionary). Algorithm: Get all tokens in the result tuples that match the dictionary, for
 * positive result set(P) and negative set (S). Remove all tokens in S-P from dictionary. Consider
 * removing tokens in S^P case by case. Note: removing a phrase from the dictionary can have overall
 * impact: this dictionary phrase may be used by another extractDict rule. Two options to deal with
 * this: 1. Keep local dictionary for each view. When conflicts arise, each view gets a separate
 * copy. This way, local change won't affect other views. 2. Continue to use one dict for all views.
 * Deleting a phrase affects all views. This requires knowing all views that depends on a
 * dictionary. I think the second method should be used. We first get all the views that directly
 * derives from the dictionary (merge all ChangeDict HLCs if the dictionary is the same). Now this
 * may mean multiple views. We gain the positives/negatives from all views to decide what to remove
 * and what to keep. Because they all derive from the dictionary directly, it's impossible for the
 * views to depend on each other. The code below only implements local changes, without worrying
 * about other views that can use the same dictionary. Additional Restrictions: 1. Assumes only one
 * dictionary is involved.
 */
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;

import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.aql.DictExNode;
import com.ibm.avatar.aql.ExtractNode;
import com.ibm.avatar.aql.ViewBodyNode;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.logging.Log;

public class ChangeExtractDict extends ChangeGenerator {

  private final ExtractNode node;
  private DictExNode dictNode;
  private String dictName; // what dictionary to change
  private final String viewName; // what view we are operating on
  private String colName; // column name of the extracted value

  public ChangeExtractDict(ArrayList<Integer> localNeg, ArrayList<Integer> missing,
      ArrayList<Integer> localPos, HashMap<Integer, Pair<Tuple, String>> tupleCacheMap,
      HashMap<String, TupleSchema> schemas,
      HashMap<Integer, Pair<String, ArrayList<Integer>>> oneLevelProvMap, ViewBodyNode node,
      String viewName, Properties props, Catalog catalog) {
    super(localNeg, missing, localPos, tupleCacheMap, schemas, oneLevelProvMap, props, catalog);
    this.node = (ExtractNode) node;
    this.viewName = viewName;

    if (this.node.getExtractList().getExtractSpec() instanceof DictExNode) {
      dictNode = (DictExNode) this.node.getExtractList().getExtractSpec();
      if (dictNode.getOutputCols().size() > 1) {
        Log.debug(
            "Error in constructor of ChangeDict: not knowing what to do when there are multiple output columns");
      } else {
        colName = dictNode.getOutputCols().get(0).getNickname();
        dictName = dictNode.getDictName(0).getStr(); // FIXME: assumes for now that we only have one
                                                     // dictionary.
      }

      if (localNeg.size() > 0)
        viewName = oneLevelProvMap.get(localNeg.get(0)).first;
      else
        Log.debug("Error in constructor of ChangeDict: no negatives to remove");
    } else
      Log.debug("Error in constructor of ChangeDict");
  }

  @Override
  /**
   * Input: positiveIds and negativeIds in the final output view. Output: a LLC instead of a list of
   * candidate changes. Our implementation produces only one LLC that makes sense.
   */
  public ArrayList<LowLevelChange> genChanges(ArrayList<Integer> positiveIds,
      ArrayList<Integer> negativeIds) {

    // FIXME: I need to get the actual list of tuples for this view.
    HashMap<String, ArrayList<Integer>> posDicts = new HashMap<String, ArrayList<Integer>>();
    getDictWords(this.getLocalPos(), posDicts);

    HashMap<String, ArrayList<Integer>> negDicts = new HashMap<String, ArrayList<Integer>>();
    getDictWords(this.getLocalNeg(), negDicts);

    // remove all words in negDicts that are not in posDicts; if in both dicts, measure the effect
    // on F-score and decide
    ArrayList<String> phraseToRemove = new ArrayList<String>();
    ArrayList<Integer> tentaiveList;

    ArrayList<Integer> removedNegatives = new ArrayList<Integer>();
    ArrayList<Integer> casualities = new ArrayList<Integer>();

    Double fScoreChange = 0.0;
    String changeString =
        "In view " + viewName + ": Remove these phrases from dictionary " + dictName + ": ";
    String wordsToRemove = "";

    for (String phrase : negDicts.keySet()) {
      if (posDicts.containsKey(phrase)) {

        // this removes all tuples who matches the phrase
        tentaiveList = new ArrayList<Integer>(posDicts.get(phrase));
        // BUG!!!
        ChangeGenerator.addListWithoutDup(tentaiveList, negDicts.get(phrase));
        // tentaiveList.addAll(negDicts.get(phrase));
        fScoreChange = getFMeasureChange(tentaiveList, getOneLevelProvMap(), getTupleCacheMap(),
            positiveIds, negativeIds, beta);

        if (fScoreChange > 0) {
          phraseToRemove.add(phrase);
          changeString += "'" + phrase + "', ";
          if (wordsToRemove.length() > 0)
            wordsToRemove += ", '" + phrase + "'";
          else
            wordsToRemove += "'" + phrase + "'";
          ChangeGenerator.addListWithoutDup(removedNegatives, negDicts.get(phrase));
          ChangeGenerator.addListWithoutDup(casualities, posDicts.get(phrase));
          // removedNegatives.addAll(negDicts.get(phrase));
          // casualities.addAll(posDicts.get(phrase));
        }
      } else {
        phraseToRemove.add(phrase);
        changeString += "'" + phrase + "', ";
        if (wordsToRemove.length() > 0)
          wordsToRemove += ", '" + phrase + "'";
        else
          wordsToRemove += "'" + phrase + "'";
        ChangeGenerator.addListWithoutDup(removedNegatives, negDicts.get(phrase));
      }
    }

    // get final F-score gain.
    ArrayList<Integer> totalRemoval = new ArrayList<Integer>(casualities);
    ChangeGenerator.addListWithoutDup(totalRemoval, removedNegatives);
    // totalRemoval.addAll(removedNegatives);

    Double gain;
    if (AQLRefine.DEBUG) {
      ArrayList<Integer> removedPosResult = new ArrayList<Integer>();
      ArrayList<Integer> removedNegResult = new ArrayList<Integer>();
      gain = getFMeasureChange(totalRemoval, getOneLevelProvMap(), getTupleCacheMap(), positiveIds,
          negativeIds, removedPosResult, removedNegResult, beta);
      changeString += "\n Removed positive output: ";
      if (removedPosResult != null)
        for (int j : removedPosResult) {
          changeString += j + " ";
        }
      changeString += "\n Removed negative output: ";
      if (removedNegResult != null)
        for (int j : removedNegResult) {
          changeString += j + " ";
        }
    } else
      gain = getFMeasureChange(totalRemoval, getOneLevelProvMap(), getTupleCacheMap(), positiveIds,
          negativeIds, beta);

    DictionaryChange llc = new DictionaryChange(new ArrayList<Integer>(removedNegatives),
        new ArrayList<Integer>(casualities), true, changeString, gain,
        phraseToRemove.size() * dictPenalty, fWeight); // assigned penalty
    llc.setAddWord(false);
    llc.setDictionary(dictName);
    llc.setWord(wordsToRemove);

    ArrayList<LowLevelChange> result = new ArrayList<LowLevelChange>();
    result.add(llc);

    return result;
  }

  /**
   * Get the list of dictionary words matched by tuples. Result format: Matched token: list of ids
   * that contains the match Essentially just get the corresponding column out.
   * 
   * @param ids
   * @return
   */
  public void getDictWords(ArrayList<Integer> ids, HashMap<String, ArrayList<Integer>> result) {
    // HashMap<String, ArrayList<Integer>> result = new HashMap<String, ArrayList<Integer>>();
    HashMap<Integer, Pair<Tuple, String>> tupleCacheMap = super.getTupleCacheMap();
    TupleSchema schema = super.getSchemas().get(viewName);
    if (schema == null) {
      Log.debug("Error in getDictWords: no schema");

    } else {

      FieldGetter<Span> getStr = schema.spanAcc(colName);

      Pair<Tuple, String> pair;
      // Tuple tuple;
      String dictToken;
      ArrayList<Integer> idList;
      for (int i : ids) {
        pair = tupleCacheMap.get(i);
        dictToken = getStr.getVal(pair.first).getText();

        if (result.containsKey(dictToken)) {
          idList = result.get(dictToken);
          if (!idList.contains(i))
            idList.add(i);
          result.put(dictToken, idList);
        } else {
          ArrayList<Integer> list = new ArrayList<Integer>();
          list.add(i);
          result.put(dictToken, list);
        }
      }
    }

  }
}
