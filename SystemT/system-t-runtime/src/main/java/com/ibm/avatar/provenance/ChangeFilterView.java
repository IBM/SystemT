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
import java.util.HashSet;
import java.util.Properties;
import java.util.TreeSet;

import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.aql.CreateViewNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.ViewBodyNode;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.logging.Log;

public class ChangeFilterView extends ChangeGenerator {

  // Types of substraction
  public enum ChangeSubtractType {
    // Subtract from this view matches that overlap with matches in the filter view
    OVERLAP,
    // Subtract from this view matches that are contained but not equal to matches in the filter
    // view
    CONTAINED_NOT_EQUAL,
    // Subtract from this view matches that contains but not equal to matches in the filter view
    NOT_CONTAINED_WITHIN
  }

  private ViewBodyNode node;
  private final String thisView;
  private final HashMap<String, HashMap<String, ArrayList<Span>>> filterTuples;
  private final HashMap<String, TupleSchema> schemasT;
  private final HashMap<Integer, Pair<Tuple, String>> tupleCacheMapT;

  public ViewBodyNode getNode() {
    return node;
  }

  public void setNode(ViewBodyNode node) {
    this.node = node;
  }

  public ChangeFilterView(ArrayList<Integer> localNeg, ArrayList<Integer> missing,
      ArrayList<Integer> localPos, HashMap<Integer, Pair<Tuple, String>> tupleCacheMap,
      HashMap<String, TupleSchema> schemas,
      HashMap<Integer, Pair<String, ArrayList<Integer>>> oneLevelProvMap, ViewBodyNode node,
      String thisView, HashMap<String, HashMap<String, ArrayList<Span>>> filterTuples,
      Properties props, Catalog catalog) {
    super(localNeg, missing, localPos, tupleCacheMap, schemas, oneLevelProvMap, props, catalog);
    this.node = node;
    this.thisView = thisView;
    this.filterTuples = filterTuples;
    schemasT = this.getSchemas();
    tupleCacheMapT = this.getTupleCacheMap();
  }

  @Override
  public ArrayList<LowLevelChange> genChanges(ArrayList<Integer> positiveIds,
      ArrayList<Integer> negativeIds) {

    ArrayList<LowLevelChange> result = new ArrayList<LowLevelChange>();

    ArrayList<CreateViewNode> allViews = null;
    try {
      allViews = this.catalog.getRequiredViews();
    } catch (ParseException e) {
      e.printStackTrace();
    }

    // construct a cache for view name to its index
    if (AQLRefine.VIEW_INDEX.isEmpty()) {
      for (int j = 0; j < allViews.size(); j++) {
        AQLRefine.VIEW_INDEX.put(allViews.get(j).getViewName(), j);
      }
    }

    for (String filterView : filterViews) {

      //
      // if (thisView.equals("PersonLastFirstCand") && filterView.equals("PersonFirstLastCand"))
      // System.out.println("debug");

      // Check whether filterView depends on this view
      // It is not possible to filter based on a dependent view, so we do not generate an LLC in
      // this case
      // changed by Bin: checking bi-directional dependency
      if (isDependency(thisView, filterView, allViews)
          || isDependency(filterView, thisView, allViews)) {
        // System.out.println(filterView + " depends on " + thisView);
        // System.out.println("There is dependency between " + filterView + " and " + thisView);
        continue;
      }

      // System.out.println(filterView + " does not depend on " + thisView);
      // System.out.println("No dependency between " + filterView + " and " + thisView);

      if (AQLRefine.DEBUG)
        System.err.println("\nTrying filtering " + thisView + " with " + filterView);

      // for each change subtract type, identify those tuple ids that are affected
      HashMap<ChangeSubtractType, ArrayList<Integer>> possibleRemove =
          toRemoveOverlap(filterView, this.getLocalPos(), this.getLocalNeg());

      for (ChangeSubtractType type : ChangeSubtractType.values()) {

        if (type.equals(ChangeSubtractType.CONTAINED_NOT_EQUAL)
            || type.equals(ChangeSubtractType.NOT_CONTAINED_WITHIN)) {
          if (!thisView.equals(filterView))
            continue;
        }
        ArrayList<Integer> removedNegatives = new ArrayList<Integer>();
        ArrayList<Integer> casualities = new ArrayList<Integer>();
        ArrayList<Integer> removed;

        // Obtain the ids of tuples to be removed for the current type of Subtract change
        removed = possibleRemove.get(type);

        // if (type.equals(ChangeSubtractType.NOT_CONTAINED_WITHIN))
        // System.out.print("debug");
        //
        if (removed.isEmpty())
          continue;
        // if (type.equals(ChangeSubtractType.OVERLAP)){
        //
        // if (thisView.equals("PersonLastFirstCand") && filterView.equals("PersonFirstLastAll"))
        // System.out.println("debug");
        //
        // if (thisView.equals("PersonLastFirstCand") && filterView.equals("PersonFirstLastCand"))
        // System.out.println("debug");
        // }

        double fScoreChange;
        // returns score for applying filter view
        fScoreChange = getFMeasureChange(removed, this.getOneLevelProvMap(),
            this.getTupleCacheMap(), positiveIds, negativeIds, beta);

        // find out removed negatives and casualties
        for (int i : removed) {
          if (this.getLocalPos().contains(i)) {
            casualities.add(i);
          } else if (this.getLocalNeg().contains(i))
            removedNegatives.add(i);
          else
            Log.debug("Error in genChanges in ChangeFilterView");
        }

        // debug code
        // if (thisView.equals("PersonsPhoneCandidates") &&
        // filterView.equals("PersonsPhoneCandidates")){
        // System.out.println("debug");
        // for (int i: casualities){
        // System.out.println(tupleCacheMapT.get(i).second + ": " + tupleCacheMapT.get(i).first);
        // }
        // }
        // end debug

        LowLevelChange llc = null;

        if (AQLRefine.DEBUG) {
          ArrayList<Integer> removedPosResult = new ArrayList<Integer>();
          ArrayList<Integer> removedNegResult = new ArrayList<Integer>();
          /*
           * double gain = getFMeasureChange(removed, getOneLevelProvMap(), getTupleCacheMap(),
           * positiveIds, negativeIds, removedPosResult, removedNegResult); if (Math.abs(gain -
           * fScoreChange) > 0.00001) System.out.
           * println("Error in genChanges() in ChangeFilterView: gain doesn't match fScoreChange");
           */

          String s = "\n Removed positive output: ";
          if (removedPosResult != null)
            for (int j : removedPosResult) {
              s += j + " ";
            }
          s += "\n Removed negative output: ";
          if (removedNegResult != null)
            for (int j : removedNegResult) {
              s += j + " ";
            }

          Log.debug(s);
        }

        String llcName =
            String.format("Filter view: %s by view %s using %s", thisView, filterView, type);
        if (thisView.equals(filterView)) {
          String policy = "";
          switch (type) {
            case CONTAINED_NOT_EQUAL:
              policy = "ContainedWithin";
              break;
            case NOT_CONTAINED_WITHIN:
              policy = "NotContainedWithin";
              break;
          }
          llcName = String.format("Consolidate view: %s using '%s'", thisView, policy);
        }

        llc = new LowLevelChange(new ArrayList<Integer>(removedNegatives),
            new ArrayList<Integer>(casualities), true, llcName, fScoreChange,
            removed.size() * filterPenalty, fWeight);

        result.add(llc);
      }
    }
    return result;
  }

  /**
   * Needs to be called by each view inside filterViews returns the ids of neg and pos tuples that
   * overlaps with the filter view and the ids of neg and pos tuples that contain but not equal the
   * filter view Will return HashMap with key ChangeSubtractType type (e.g. OVERLAP, or
   * CONTAINED_NOT_EQUAL) and value an array of ids affected by that type of change.
   * 
   * @param filterView
   * @return
   */
  private HashMap<ChangeSubtractType, ArrayList<Integer>> toRemoveOverlap(String filterView,
      ArrayList<Integer> positiveIds, ArrayList<Integer> negativeIds) {

    // Initialize a map to store for each type of subtract change, the ids it would remove
    HashMap<ChangeSubtractType, ArrayList<Integer>> idRemove =
        new HashMap<ChangeSubtractType, ArrayList<Integer>>();
    for (ChangeSubtractType type : ChangeSubtractType.values()) {
      idRemove.put(type, new ArrayList<Integer>());
    }

    /*
     * int overlap = 0; //number of overlaps with the given filter view int contains = 0; //number
     * of contains with the given filter view ArrayList<Integer> idRemoveOverlap = new
     * ArrayList<Integer>(); ArrayList<Integer> idRemoveContains = new ArrayList<Integer>();
     */

    if (AQLRefine.DEBUG)
      System.err.println("toRemoveOverlap: " + filterView);

    HashMap<String, ArrayList<Span>> filtTup = filterTuples.get(filterView);

    if (null == filtTup) {
      if (AQLRefine.DEBUG)
        System.err.println("Skipping: " + filterView);
      return idRemove;
    }

    TupleSchema schema = schemasT.get(thisView);
    String match = schema.getFieldNameByIx(0);

    FieldGetter<Span> viewGetSpan = schema.spanAcc(match);
    // ArrayList<Integer> ids = this.getLocalPos();
    // ids.addAll(this.getLocalNeg());

    ArrayList<Integer> ids = new ArrayList<Integer>();
    ids.addAll(positiveIds);
    ids.addAll(negativeIds);

    HashSet<ChangeSubtractType> typesFound = new HashSet<ChangeSubtractType>();
    // Set<Span> filtSpans = filtTup.keySet();

    String docLabel = null;
    ArrayList<Span> filtSpans = null;
    for (int id : ids) {
      if (!tupleCacheMapT.containsKey(id)) {
        System.out.println("Error in toRemoveOverlap: no tuple for id: " + id);
        System.exit(1);
      }
      docLabel = tupleCacheMapT.get(id).second;

      // sameDocIds = AQLRefine.labelToTupleMap.get(docLabel);
      if (filtTup.containsKey(docLabel))
        filtSpans = filtTup.get(docLabel);
      else
        continue;

      // int overLapct = 0; // # of overlaps per ID
      // int containCt = 0;
      Pair<Tuple, String> pair = tupleCacheMapT.get(id);
      Tuple tup = pair.first;
      // System.out.println(tup.toString() + " in doc: " + pair.second + ", match is " + match);
      Span viewSpan = viewGetSpan.getVal(tup); // span of specific tuple id
      // System.out.printf("Tuple ID: %d View: %s\nvalue: %s\n", id, thisView, viewSpan.getText());

      // HERE I already have docLabel

      typesFound.clear();

      // Compare the current span with each span in the filter view
      for (Span filterSpan : filtSpans) {

        // We use typesFound to remember if (viewSpan,filterSpan) satisfy the relationship indicated
        // by each type
        // to avoid redundant work as well as copying the same id multiple times in idRemove.
        // The first time we have found a filter span that satisfies the relationship for type T,
        // we mark T as found and do not compare with any other spans in the filter view

        for (ChangeSubtractType type : ChangeSubtractType.values())
          if (!typesFound.contains(type)) {

            if (type.equals(ChangeSubtractType.OVERLAP)) {

              // do not consider overlap with thisView itself
              if (thisView.equals(filterView))
                typesFound.add(type);
              else if (Span.overlaps(viewSpan, filterSpan)) {
                idRemove.get(type).add(id);
                typesFound.add(type);
              }
            } else if (type.equals(ChangeSubtractType.CONTAINED_NOT_EQUAL)) {
              if (spanContainedNotEqual(viewSpan, filterSpan)) {
                idRemove.get(type).add(id);
                typesFound.add(type);
              }
            } else if (type.equals(ChangeSubtractType.NOT_CONTAINED_WITHIN)) {
              if (spanContainsNotEqual(viewSpan, filterSpan)) {
                idRemove.get(type).add(id);
                typesFound.add(type);
              }
            } else {
              // TODO: throw a proper exception here
              Log.debug("System EXIT: Don't know how to handle ChangeSubtractType: %s\n", type);
              System.exit(1);
            }
          }

      }
      /*
       * if (overLapct > 0) idRemoveOverlap.add(id); //add id that needs to be removed if (containCt
       * > 0) idRemoveContains.add(id); overlap = overlap + overLapct; contains = contains +
       * containCt;
       */

    }

    // Log.debug("Number of times view " + filtView + "overlaps is: " + Integer.toString(overlap));
    // if (thisView.equals("PersonLastFirstCand") && filterView.equals("PersonFirstLastCand"))
    // System.out.println("person found");

    // idRemove.put(AQLRefine.CHANGE_SUBTRACT_OVERLAP, idRemoveOverlap);
    // idRemove.put(AQLRefine.CHANGE_SUBTRACT_CONTAINED_NOT_EQUAL, idRemoveContains);

    return idRemove;

  }

  /**
   * Returns true if span1 is contained within, but not equal to span2
   * 
   * @param span1
   * @param span2
   * @return
   */
  private boolean spanContainedNotEqual(Span span1, Span span2) {

    if (span1 == null || span2 == null)
      return false;

    if (false == (span1.getDocTextObj().equals(span2.getDocTextObj()))) {
      // Spans on different document tuples cannot overlap
      return false;
    }

    if (span1.getBegin() == span2.getBegin() && span1.getEnd() == span2.getEnd())
      // Equal spans
      return false;

    if (span1.getBegin() >= span2.getBegin() && span1.getEnd() <= span2.getEnd())
      // span1 is inside span2
      return true;

    return false;

  }

  /**
   * Return true if span1 contains, but not equal to span
   * 
   * @param span1
   * @param span2
   * @return
   */
  private boolean spanContainsNotEqual(Span span1, Span span2) {

    if (span1 == null || span2 == null)
      return false;

    if (false == (span1.getDocTextObj().equals(span2.getDocTextObj()))) {
      // Spans on different document tuples cannot overlap
      return false;
    }

    if (span1.getBegin() == span2.getBegin() && span1.getEnd() == span2.getEnd())
      // Equal spans
      return false;

    if (span1.getBegin() <= span2.getBegin() && span1.getEnd() >= span2.getEnd())
      // span2 is inside span1
      return true;

    return false;

  }

  /**
   * Checks whether otherView depends on view
   * 
   * @param otherView
   * @return
   */
  private boolean isDependency(String view, String otherView, ArrayList<CreateViewNode> allViews) {

    // System.out.print("checking if " + otherView + " depends on " + view + "...");
    if (view.equals(otherView))
      return false;

    if (!AQLRefine.VIEW_INDEX.containsKey(view) || !AQLRefine.VIEW_INDEX.containsKey(otherView))
      return false;

    if (AQLRefine.DEBUG)
      Log.debug("Check VIEW_INDEX for view '%s'\n", view);
    int viewIdx = AQLRefine.VIEW_INDEX.get(view);

    if (AQLRefine.DEBUG)
      Log.debug("Check VIEW_INDEX for view '%s'\n", otherView);
    int otherViewIdx = AQLRefine.VIEW_INDEX.get(otherView);

    // see if the otherView appears before view in views; if so, otherView doesn't depend on view
    if (AQLRefine.DEPENDENCIES.containsKey(otherView)) {
      if (AQLRefine.DEPENDENCIES.get(otherView).first.contains(viewIdx))
        return true;
      if (AQLRefine.DEPENDENCIES.get(otherView).second.contains(viewIdx))
        return false;
    } else {
      ArrayList<Integer> first = new ArrayList<Integer>();
      ArrayList<Integer> second = new ArrayList<Integer>();
      AQLRefine.DEPENDENCIES.put(otherView,
          new Pair<ArrayList<Integer>, ArrayList<Integer>>(first, second));
    }

    if (otherViewIdx < viewIdx) { // otherView appears first in the dependency graph
      AQLRefine.DEPENDENCIES.get(otherView).second.add(viewIdx);
      return false;
    } else {
      // see if any view that otherView depends on depends on view
      ViewBodyNode filterViewBody = allViews.get(otherViewIdx).getBody();
      TreeSet<String> vDeps = new TreeSet<String>();
      try {
        filterViewBody.getDeps(vDeps, catalog);
      } catch (ParseException e) {
        throw new FatalInternalError(e,
            "Error computing view dependencies during provenance rewrite.");
      }
      for (String dep : vDeps) {
        AQLRefine.DEPENDENCIES.get(otherView).first.add(AQLRefine.VIEW_INDEX.get(dep));

        if (dep.equals(view)) {
          AQLRefine.DEPENDENCIES.get(otherView).first.add(viewIdx);
          return true;
        }
        if (isDependency(view, dep, allViews)) {
          // System.out.print(" Yes \n");
          // if (!AQLRefine.DEPENDENCIES.get(index).first.contains(viewIdx))
          AQLRefine.DEPENDENCIES.get(otherView).first.add(viewIdx);
          return true;
        }
      }
      // System.out.print(" NO \n");
      AQLRefine.DEPENDENCIES.get(otherView).second.add(viewIdx);
      return false;
    }

    // HashSet<String> allDeps = new HashSet<String>();
    // collectViewDeps(otherView, allDeps, views);
    //
    // if(allDeps.contains(view))
    // return true;
    //
    // return false;
  }

  /**
   * Recursively collects all dependencies of a given view. TODO: Ideally these should be computed
   * one time.
   * 
   * @param view
   * @param allDdeps
   * @param allViews
   */
  @SuppressWarnings("unused")
  private void collectViewDeps(String view, HashSet<String> allDeps,
      ArrayList<CreateViewNode> allViews) {
    System.out.println("collectViewDeps on: " + view);
    for (CreateViewNode v : allViews) {

      if (v.getViewName().equals(view)) {

        ViewBodyNode filterViewBody = v.getBody();
        TreeSet<String> vDeps = new TreeSet<String>();
        try {
          filterViewBody.getDeps(vDeps, catalog);
        } catch (ParseException e) {
          throw new FatalInternalError(e,
              "Error computing view dependencies during provenance rewrite.");
        }

        for (String dep : vDeps) {
          allDeps.add(dep);
          collectViewDeps(dep, allDeps, allViews);
        }
      }
    }
  }
}
