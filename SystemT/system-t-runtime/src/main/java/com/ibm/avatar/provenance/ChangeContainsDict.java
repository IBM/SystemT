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
import com.ibm.avatar.algebra.util.tokenize.OffsetsList;
import com.ibm.avatar.algebra.util.tokenize.StandardTokenizer;
import com.ibm.avatar.aql.ColNameNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.ScalarFnCallNode;
import com.ibm.avatar.aql.SelectListItemNode;
import com.ibm.avatar.aql.SelectListNode;
import com.ibm.avatar.aql.SelectNode;
import com.ibm.avatar.aql.StringNode;
import com.ibm.avatar.aql.ViewBodyNode;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.logging.Log;

/**
 * Class for handling changing dictionaries used in the where clause. The main goal is to handle the
 * following cases: where Not(ContainsDict('timeZone.dict', R.match)). Should learn to add new words
 * into the dictionary, should handle negation. where Not(ContainsDict('GreetingsDict',
 * LeftContext(P.person, 15)))
 */
public class ChangeContainsDict extends ChangeGenerator {

  private ScalarFnCallNode argNode = null;
  private ColNameNode colNode = null;
  private final boolean negation; // whether we have a "Not" in front of "ContainsDict"
  private String dictName = "";
  private final String viewName;
  private final ViewBodyNode viewNode;

  public static double TFIDF_THRESHOLD = 0;
  // For a phrase only appears in negative output, the minimum percentage it appears among all
  // outputs for it to be
  // considered a change
  public static double NEGATIVE_PERCENTAGE_THRESHOLD = 0.05;
  // absolute threshold for a phrase that appears in only negative dictionary to pass as a change
  public static int NEGATIVE_APPEARANCE_THRESHOLD = 5;

  /**
   * A threshold for dictionary words in positive context, on the frequency that a word appears in
   * the context; If frequency is higher than this value, the word shouldn't be removed.
   */

  // private double PositiveFrequencyThreshold = 0.1;

  public ChangeContainsDict(ArrayList<Integer> localNeg, ArrayList<Integer> missing,
      ArrayList<Integer> localPos, HashMap<Integer, Pair<Tuple, String>> tupleCacheMap,
      HashMap<Integer, Pair<String, ArrayList<Integer>>> oneLevelProvMap,
      HashMap<String, TupleSchema> schemas, ScalarFnCallNode node, ViewBodyNode viewNode,
      boolean negation, String viewName, Properties props, Catalog catalog) {
    super(localNeg, missing, localPos, tupleCacheMap, schemas, oneLevelProvMap, props, catalog);
    this.negation = negation;
    this.viewName = viewName;
    this.viewNode = viewNode;
    int size = node.getArgs().size();
    // the last argument is the colName or function like LeftContext

    // if more than one dictionary
    StringNode sNode;
    if (size > 2) {
      for (int i = 0; i < size - 1; i++) {
        sNode = (StringNode) (node.getArgs().get(i));
        dictName += sNode.getStr() + ", ";
      }
    } else
      dictName = ((StringNode) (node.getArgs().get(0))).getStr();

    // figure out what's the next argument
    if (node.getArgs().get(size - 1) instanceof ScalarFnCallNode) {
      // case of (ContainsDict('GreetingsDict', LeftContext(P.person, 15));
      argNode = (ScalarFnCallNode) (node.getArgs().get(size - 1));
    } else if (node.getArgs().get(size - 1) instanceof ColNameNode) {
      // Not(ContainsDict('GreetingsDict', fn.name)); this the same as ChangeExtractDict();
      colNode = (ColNameNode) (node.getArgs().get(size - 1));
    }
  }

  @Override
  /**
   * Input: positiveIds and negativeIds in the final output view. Output: a LLC instead of a list of
   * candidate changes. Our implementation produces only one LLC that makes sense.
   * 
   * Difference from ChangeExtractDict: it's possible there is negation involved. We also consider
   * adding words to dictionaries.
   */
  public ArrayList<LowLevelChange> genChanges(ArrayList<Integer> positiveIds,
      ArrayList<Integer> negativeIds) {
    ArrayList<LowLevelChange> result = new ArrayList<LowLevelChange>();

    HashMap<String, ArrayList<Integer>> posDicts = new HashMap<String, ArrayList<Integer>>();
    HashMap<String, ArrayList<Integer>> negDicts = new HashMap<String, ArrayList<Integer>>();

    // regular case
    if (argNode == null) {
      getDictWords(this.getLocalNeg(), negDicts);
      getDictWords(this.getLocalPos(), posDicts);
    } else { // LeftContext or RightContext
      getContextWords(this.getLocalNeg(), negDicts);
      getContextWords(this.getLocalPos(), posDicts);
    }

    // getDictStats(posDicts, negDicts);

    PhraseWithWeight[] negPhrases =
        rankNegPhrase(negDicts, posDicts, this.getLocalNeg().size() + this.getLocalPos().size());

    // remove all words in negDicts that are not in posDicts; if in both dicts, measure the effect
    // on F-score and decide
    ArrayList<String> phraseToRemove = new ArrayList<String>();

    String initialString = "";

    if (argNode == null) {
      initialString +=
          "In view " + viewName + ": Remove these phrases from dictionary " + dictName + ": ";

      if (negation) {
        initialString = "Add these phrases to dictionary " + dictName + ": ";
      }
    } else {
      initialString += "In view " + viewName + ": Remove these phrases from context dictionary "
          + dictName + ": ";

      if (negation) {
        initialString =
            "In view " + viewName + ": Add these phrases to context dictionary " + dictName + ": ";
      }
    }

    // now creating one LLC for each desirable phrase
    int numOfPositive = 0;
    double gain;
    String changeString = "";
    ArrayList<Integer> tmpList = new ArrayList<Integer>();

    String phrase = "";
    for (int i = 0; i < negPhrases.length && negPhrases[i].getWeight() > TFIDF_THRESHOLD; i++) {

      // for (String phrase: negDicts.keySet()) {
      tmpList.clear();
      numOfPositive = 0;
      changeString = "";
      phrase = negPhrases[i].getPhrase();

      if (negDicts.get(phrase).size() < 5)
        continue;

      tmpList.addAll(negDicts.get(phrase));
      if (posDicts.containsKey(phrase)) {
        numOfPositive = posDicts.get(phrase).size();
        ChangeGenerator.addListWithoutDup(tmpList, posDicts.get(phrase));
      } else {
        numOfPositive = 0;
      }

      changeString = initialString + " " + phrase + "; frequency in negatives: "
          + negDicts.get(phrase).size() + "; frequency in positives: " + numOfPositive;

      // print out removal of results in the final output view.
      if (AQLRefine.DEBUG) {
        ArrayList<Integer> removedPosResult = new ArrayList<Integer>();
        ArrayList<Integer> removedNegResult = new ArrayList<Integer>();
        gain = getFMeasureChange(tmpList, getOneLevelProvMap(), getTupleCacheMap(), positiveIds,
            negativeIds, removedPosResult, removedNegResult, beta);
        changeString += "\n Removed positive output: ";
        if (removedPosResult != null)
          for (int j : removedPosResult) {
            // changeString += j + " ";
            changeString += j + ": " + this.getTupleCacheMap().get(j).first.toString() + "\n";
          }
        changeString += "\n Removed negative output: ";
        if (removedNegResult != null)
          for (int j : removedNegResult) {
            // changeString += j + " ";
            changeString += j + ": " + this.getTupleCacheMap().get(j).first.toString() + "\n";
          }
      } else
        gain = getFMeasureChange(tmpList, getOneLevelProvMap(), getTupleCacheMap(), positiveIds,
            negativeIds, beta);

      ArrayList<Integer> poss = new ArrayList<Integer>();
      if (posDicts.containsKey(phrase))
        poss.addAll(posDicts.get(phrase));

      DictionaryChange llc = new DictionaryChange(new ArrayList<Integer>(negDicts.get(phrase)),
          poss, true, changeString, gain, phraseToRemove.size() * dictPenalty, fWeight); // assigned
                                                                                         // penalty
      llc.setAddWord(negation);
      llc.setDictionary(dictName);
      llc.setWord("'" + phrase + "'");

      result.add(llc);
    }
    return result;
  }

  public void getDictStats(HashMap<String, ArrayList<Integer>> posDicts,
      HashMap<String, ArrayList<Integer>> negDicts) {

    ArrayList<String> posOnly = new ArrayList<String>();
    ArrayList<String> inBoth = new ArrayList<String>();

    for (String s : posDicts.keySet()) {
      if (!negDicts.containsKey(s))
        posOnly.add(s);
      else
        inBoth.add(s);
    }
    String s1 = "Positive only: ", s2 = "Negative only: ", s3 = "In Both dictionary: ";

    for (String s : posOnly) {
      s1 += s + " (" + posDicts.get(s).size() + ") ";
    }

    for (String s : inBoth) {
      s3 += s + " (" + posDicts.get(s).size() + ") ";
    }

    for (String s : negDicts.keySet()) {
      if (!inBoth.contains(s)) {
        s2 += s + " (" + negDicts.get(s).size() + ") ";
      }
    }

    System.out.print(s1 + "\n" + s2 + "\n" + s3);
  }

  /**
   * Rank phrases in negDicts using a similar metric as TF/IDF
   * 
   * @param negDicts
   * @param posDicts
   * @param totalOutput total number of output tuples
   * @param threshold if the score is below this value, don't consider it as a change
   * @return
   */
  private PhraseWithWeight[] rankNegPhrase(HashMap<String, ArrayList<Integer>> negDicts,
      HashMap<String, ArrayList<Integer>> posDicts, Integer totalOutput) {

    // FIXME: we need a way to filter out negative-only phrases that appear only a few times

    PhraseWithWeight[] result = new PhraseWithWeight[negDicts.size()];
    int count = 0;
    for (String phrase : negDicts.keySet()) {

      if (posDicts.containsKey(phrase)) {
        result[count++] = new PhraseWithWeight(phrase,
            (double) negDicts.get(phrase).size() / (double) (posDicts.get(phrase).size() + 1));
      }
      /*
       * else { if (negDicts.get(phrase).size() >= totalOutput * NEGATIVE_PERCENTAGE_THRESHOLD ||
       * negDicts.get(phrase).size() >= NEGATIVE_APPEARANCE_THRESHOLD) // result[count++] = new
       * PhraseWithWeight(phrase, (double)negDicts.get(phrase).size()
       * /(double)negDicts.get(phrase).size()); else result[count++] = new PhraseWithWeight(phrase,
       * 0); }
       */
      // +1 just in case posDicts doesn't contain this phrase
      else {
        if (negDicts.get(phrase).size() >= totalOutput * NEGATIVE_PERCENTAGE_THRESHOLD
            || negDicts.get(phrase).size() >= NEGATIVE_APPEARANCE_THRESHOLD)
          result[count++] = new PhraseWithWeight(phrase, negDicts.get(phrase).size());
        else
          // phrases that appear only a few times in negative dictionary
          result[count++] = new PhraseWithWeight(phrase,
              (double) negDicts.get(phrase).size() / NEGATIVE_APPEARANCE_THRESHOLD);
      }

    }

    Arrays.sort(result);
    if (AQLRefine.DEBUG)
      System.err.println(Arrays.toString(result));
    return result;
  }

  /**
   * Get the list of dictionary words matched by tuples. Result format: Matched token: list of ids
   * that contains the match Essentially just get the corresponding column out.
   * 
   * @param ids
   * @return
   */
  private void getDictWords(ArrayList<Integer> ids, HashMap<String, ArrayList<Integer>> result) {
    // HashMap<String, ArrayList<Integer>> result = new HashMap<String, ArrayList<Integer>>();
    HashMap<Integer, Pair<Tuple, String>> tupleCacheMap = super.getTupleCacheMap();
    TupleSchema schema = super.getSchemas().get(viewName);
    if (schema == null)
      Log.debug("Error in getDictWords: no schema");

    FieldGetter<Span> getStr = null;
    if (colNode != null) {
      // need to figure out possible nickname situations like: select R.match as name where
      // containsDict(dict, R.match)
      String colName = "";
      String dictMatchName = colNode.getColName(); // R.match

      SelectNode selNode = (SelectNode) viewNode;
      SelectListNode selListNode = selNode.getSelectList();
      for (int i = 0; i < selListNode.size(); i++) {
        SelectListItemNode selItem = selListNode.get(i);
        try {
          if (dictMatchName.equals(selItem.getValue().getColName())) {
            colName = selItem.getAlias(); // "name"
          }
        } catch (ParseException e) {
          e.printStackTrace();
        }
        // if (selItem.g)
      }

      getStr = schema.spanAcc(colName);
    } else if (argNode != null) {
      // find out what function is used; if unsupported, print msg, return null as result
      // FIXME: to add.
      System.out.println("Error in getDictWords()");
    }

    Pair<Tuple, String> pair;
    String dictToken;
    ArrayList<Integer> idList;
    for (int i : ids) {
      pair = tupleCacheMap.get(i);
      dictToken = getStr.getVal(pair.first).getText();

      if (result.containsKey(dictToken)) {
        idList = result.get(dictToken);
        idList.add(i);
        result.put(dictToken, idList);
      } else {
        ArrayList<Integer> list = new ArrayList<Integer>();
        list.add(i);
        result.put(dictToken, list);
      }
    }
  }

  private void getContextWords(ArrayList<Integer> ids, HashMap<String, ArrayList<Integer>> result) {

    HashMap<Integer, Pair<Tuple, String>> tupleCacheMap = super.getTupleCacheMap();

    // if it's leftcontexttok, i get a number (charsPerTok * number of toks) of chars, try to
    // tokenize
    int charsPerTok = 20;

    TupleSchema schema = super.getSchemas().get(viewName);
    if (schema == null) {
      Log.debug("Error in getDictWords: no schema");
      return;
    }

    // figure out it's Left or Right context, and the range
    String funcName = argNode.getFuncName(); // lefTcontext
    String colName = ((ColNameNode) argNode.getArgs().get(0)).getColnameInTable(); // fn.name,
                                                                                   // ColNameNode
    int distance = Integer.parseInt(argNode.getArgs().get(1).toString()); // 15, distance

    FieldGetter<Span> getSpan = schema.spanAcc(colName);

    Pair<Tuple, String> pair;
    String dictTok;
    ArrayList<Integer> idList;
    Span span = null, contextSpan = null;

    int begin = 0, end = 0;
    StandardTokenizer ft = new StandardTokenizer();

    int subBegin, subEnd;
    ArrayList<String> dictWords = new ArrayList<String>();
    boolean isTokContext = false;
    for (int i : ids) {
      dictWords.clear();
      pair = tupleCacheMap.get(i);
      // dictToken = getStr.getVal(pair.first).getText();
      span = getSpan.getVal(pair.first);

      if (funcName.equals("LeftContext")) {
        end = span.getBegin();
        begin = Math.max(0, end - distance);
      } else if (funcName.equals("RightContext")) {
        begin = span.getEnd();
        end = begin + distance;
      } else if (funcName.equals("LeftContextTok")) {
        end = span.getBegin();
        begin = Math.max(0, end - distance * charsPerTok);
        isTokContext = true;
      } else if (funcName.equals("RightContextTok")) {
        begin = span.getEnd();
        end = begin + distance * charsPerTok;
        isTokContext = true;
      } else
        Log.debug("Error in getContextWords");

      // make sure 'end' not exceed doc length.
      if (end > span.getDocText().length())
        end = span.getDocText().length();

      contextSpan = Span.makeBaseSpan(span, begin, end);

      OffsetsList list;
      list = ft.tokenize(contextSpan);

      if (isTokContext) {
        if (funcName.contains("Right")) {
          for (int j = 0; j < list.size() && j < distance; j++) {
            subBegin = list.begin(j);
            subEnd = list.end(j);
            dictTok = Span.makeSubSpan(contextSpan, subBegin, subEnd).getText();
            // Log.debug("FuncName = " + funcName + "; id = " + i + " " + dictTok);
            if (!dictWords.contains(dictTok))
              dictWords.add(dictTok);
          }
        } else { // start from the left most token
          for (int j = list.size() - 1; (j > list.size() - distance - 1) && (j > -1); j--) {
            subBegin = list.begin(j);
            subEnd = list.end(j);
            dictTok = Span.makeSubSpan(contextSpan, subBegin, subEnd).getText();
            // Log.debug("FuncName = " + funcName + "; id = " + i + " " + dictTok);
            if (!dictWords.contains(dictTok))
              dictWords.add(dictTok);
          }
        }
      } else {
        for (int j = 0; j < list.size(); j++) {
          subBegin = list.begin(j);
          subEnd = list.end(j);
          dictTok = Span.makeSubSpan(contextSpan, subBegin, subEnd).getText();
          // Log.debug("FuncName = " + funcName + "; id = " + i + " " + dictTok);
          if (!dictWords.contains(dictTok))
            dictWords.add(dictTok);
        }
      }

      /**
       * // since the tokenizer removes line breaks, we need to add that context =
       * contextSpan.toString(); if (context.contains("\\n\\n") && !dictWords.contains("\\n\\n")){
       * dictWords.add("\\n\\n"); } if (context.contains("\\n") && !dictWords.contains("\\n")){
       * dictWords.add("\\n"); }
       */

      for (String word : dictWords)
        if (result.containsKey(word)) {
          idList = result.get(word);
          idList.add(i);
          result.put(word, idList);
        } else {
          ArrayList<Integer> ilist = new ArrayList<Integer>();
          ilist.add(i);
          result.put(word, ilist);
        }
    }
  }
}


class PhraseWithWeight implements Comparable<Object> {
  private String phrase;

  public String getPhrase() {
    return phrase;
  }

  public void setPhrase(String phrase) {
    this.phrase = phrase;
  }

  public double getWeight() {
    return weight;
  }

  public void setWeight(double weight) {
    this.weight = weight;
  }

  private double weight;

  public PhraseWithWeight(String phrase, double weight) {
    this.phrase = phrase;
    this.weight = weight;
  }

  @Override
  public int compareTo(Object arg0) {
    if (weight > ((PhraseWithWeight) arg0).getWeight())
      return -1;
    else if (weight < ((PhraseWithWeight) arg0).getWeight())
      return 1;
    else
      return 0;
  }

  @Override
  public String toString() {
    return String.format("%s: %.6f", phrase, weight);
  }
}
