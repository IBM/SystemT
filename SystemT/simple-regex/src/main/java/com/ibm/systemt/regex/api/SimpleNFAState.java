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
package com.ibm.systemt.regex.api;

import java.util.ArrayList;
import java.util.TreeMap;
import java.util.TreeSet;

/** A single state in the NFA for a simple regular expression. */
public class SimpleNFAState {

  // Dedicated "accept" state.
  public static final SimpleNFAState ACCEPT = new SimpleNFAState();

  public static final int NOT_AN_INDEX = -1;

  /**
   * Table of transitions out of this state. Index into this array is character class ID. Each entry
   * holds a list of target state IDs.
   */
  int[][] transTab;

  /**
   * Index used to represent this state in other states' transition tables
   */
  int index = NOT_AN_INDEX;

  public void setIndex(int index) {
    this.index = index;
  }

  public int getIndex() {
    return index;
  }

  /**
   * Epsilon transitions (transitions on empty input). Any state that is in the global table should
   * not have any entries here.
   */
  TreeSet<Integer> epsilonTrans = null;

  public TreeSet<Integer> getEpsilonTrans() {
    return epsilonTrans;
  }

  public void clearEpsilonTrans() {
    epsilonTrans = null;
  }

  /**
   * Constructor used only for dummy states.
   */
  private SimpleNFAState() {}

  /**
   * Create the union of several states; used for building starting states for alternations.
   * 
   * @param startingStates input set of states to be overlapped
   */
  public SimpleNFAState(ArrayList<SimpleNFAState> startingStates) {

    // Start by finding the maximum char ID.
    int maxCharID = -1;
    for (SimpleNFAState state : startingStates) {
      maxCharID = Math.max(maxCharID, state.transTab.length - 1);
    }

    // Now we can create a transition table of the appropriate size.
    transTab = new int[maxCharID + 1][];

    // Create each entry in turn.
    for (int id = 0; id <= maxCharID; id++) {

      // Build up an exhaustive list of targets for this char set ID
      TreeSet<Integer> targets = new TreeSet<Integer>();

      for (SimpleNFAState state : startingStates) {
        if (id < state.transTab.length && null != state.transTab[id]) {
          for (int j = 0; j < state.transTab[id].length; j++) {
            targets.add(state.transTab[id][j]);
          }
        }
      }

      // Turn the set into an array.
      targetsToArray(id, targets);
    }

    // Union together the epsilon transitions.
    epsilonTrans = new TreeSet<Integer>();
    for (SimpleNFAState state : startingStates) {
      if (null != state.epsilonTrans) {
        epsilonTrans.addAll(state.epsilonTrans);
      }
    }

    if (0 == epsilonTrans.size()) {
      // Keep the state compact
      epsilonTrans = null;
    }

  }

  /**
   * Main constructor; takes a table of transitions OUT of this state and produces the new state
   * object.
   * 
   * @param transitions transition table; key is a character set ID, and values are the state IDs of
   *        the target states for the indicated character.
   */
  public SimpleNFAState(TreeMap<Integer, TreeSet<Integer>> transitions) {

    // Determine the maximum character ID of any transition.
    int maxCharID = transitions.lastKey();

    // Translate the map into arrays.
    transTab = new int[maxCharID + 1][];

    for (int id : transitions.keySet()) {
      TreeSet<Integer> targets = transitions.get(id);
      targetsToArray(id, targets);
    }

  }

  /** Constructor to augment a state with epsilon transitions. */
  public SimpleNFAState(SimpleNFAState baseStates, ArrayList<Integer> epsilonTrans) {

    // Transition table is immutable, so we can do a shallow copy.
    this.transTab = baseStates.transTab;

    this.epsilonTrans = new TreeSet<Integer>();

    if (null != baseStates.epsilonTrans) {
      this.epsilonTrans.addAll(baseStates.epsilonTrans);
    }

    this.epsilonTrans.addAll(epsilonTrans);
  }

  private void targetsToArray(int id, TreeSet<Integer> targets) {
    if (0 == targets.size()) {
      // SPECIAL CASE: Don't bother creating empty arrays.
      transTab[id] = null;
      return;
      // END SPECIAL CASE
    }
    transTab[id] = new int[targets.size()];

    int ix = 0;
    for (Integer target : targets) {
      transTab[id][ix] = target;
      ix++;
    }
  }

  /**
   * @return human-readable string representation of this state.
   */
  public String toPrettyString() {


    if (ACCEPT == this) {
      // SPECIAL CASE: Contents of the accept state are meaningless.
      return "ACCEPT";
      // END SPECIAL CASE
    }

    StringBuilder sb = new StringBuilder();

    sb.append(String.format("State %d:\n", index));

    if (null == transTab) {
      sb.append("   ==> No transition table\n");
      return sb.toString();
    }

    // Header for transition table
    sb.append(String.format("%15s %s\n", "Char ID", "Next States"));

    // Transition table
    for (int id = 0; id < transTab.length; id++) {
      sb.append(String.format("%15d ", id));

      int[] targets = transTab[id];

      if (null == targets) {
        sb.append("null\n");
      } else if (0 == targets.length) {
        // throw new RuntimeException("Zero targets in " + sb.toString());
        sb.append("no targets\n");
      } else {
        for (int j = 0; j < targets.length - 1; j++) {
          sb.append(String.format("%d, ", targets[j]));
        }
        sb.append(String.format("%d\n", targets[targets.length - 1]));
      }
    }

    // Epsilon transitions
    if (null != epsilonTrans) {
      sb.append(String.format("%15s %s\n", "ε", epsilonTrans));
    }

    return sb.toString();
  }
}
