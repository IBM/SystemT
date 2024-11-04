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
package com.ibm.systemt.regex.parse;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeSet;

import com.ibm.systemt.regex.api.SimpleNFAState;
import com.ibm.systemt.regex.charclass.CharSet;

public abstract class Node {

  /**
   * Single-char lookbehind (such as \b) that goes before this node in the regex
   */
  private NodeBoundaryCond beforeBoundCond = null;

  /**
   * Single-char lookahead (such as \b or \z) that goes after this node in the regex
   */
  private NodeBoundaryCond afterBoundCond = null;

  protected void setBoundConds(NodeBoundaryCond beforeBoundCond, NodeBoundaryCond afterBoundCond) {
    this.beforeBoundCond = beforeBoundCond;
    this.afterBoundCond = afterBoundCond;
  }

  public NodeBoundaryCond getAfterBoundCond() {
    return afterBoundCond;
  }

  public NodeBoundaryCond getBeforeBoundCond() {
    return beforeBoundCond;
  }

  /**
   * Pretty-print the parse tree rooted at this node. Implementations of this function should NOT
   * print a trailing carraige return.
   * 
   * @param stream where to print to
   * @param indent starting indent level (in tabs)
   * 
   */
  public abstract void dump(PrintStream stream, int indent);

  protected static void printIndent(PrintStream stream, int indent) {
    for (int i = 0; i < indent; i++) {
      stream.append("  ");
    }
  }

  /**
   * Walk this node and its children, and return all distinct character classes that appear at any
   * position therein.
   */
  public abstract void getCharClasses(HashMap<CharSet, Integer> classCounts);

  /**
   * Convert this node and its children into an NFA
   * 
   * @param nfaStates the states that make up the NFA; add new objects to this array as needed.
   * @param charSetToID mapping from character set to the character IDs in the indicated set
   * @param nextStates states the generated NFA should transition to on success
   * @return a dummy "start" state for the NFA, which is *not* inserted into the nfaStates table
   */
  public abstract SimpleNFAState toNFA(ArrayList<SimpleNFAState> nfaStates,
      HashMap<CharSet, ArrayList<Integer>> charSetToID, ArrayList<Integer> nextStates);

  /**
   * Utility function for inserting a state into the NFA states array. This method also takes care
   * of stripping out epsilon transitions.
   * 
   * @param nfaStates the array of states
   * @param state new state to insert
   * @return targets of the epsilon transitions that were stripped out of the target state
   */
  public static TreeSet<Integer> addStateToNFA(ArrayList<SimpleNFAState> nfaStates,
      SimpleNFAState state) {

    // Separate out the epsilon transitions
    TreeSet<Integer> ret = state.getEpsilonTrans();
    state.clearEpsilonTrans();

    if (SimpleNFAState.NOT_AN_INDEX == state.getIndex()) {
      // Don't add a state to the array twice!
      nfaStates.add(state);
      state.setIndex(nfaStates.size() - 1);

      // System.err.printf("Entry %d gets %s\n", nfaStates.size() - 1,
      // state);

    }

    return ret;
  }

  @Override
  public final String toString() {
    String ret = "";
    if (null != beforeBoundCond) {
      ret += beforeBoundCond.toStringInternal();
    }
    ret += toStringInternal();
    if (null != afterBoundCond) {
      ret += afterBoundCond.toStringInternal();
    }
    return ret;
  }

  /**
   * Subclasses must provide an internal toString() method that prints back the relevant part of the
   * original regex, verbatim.
   */
  public abstract String toStringInternal();

}
