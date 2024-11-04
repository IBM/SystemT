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

/**
 * Quantification logic for an atom; separated out into an external node to make it easy to strip
 * quantification off an atom.
 */
public class NodeQuant extends Node {

  NodeAtom target;

  /**
   * Constructor for the simple quantification types (?, *, +)
   * 
   * @param target atom over which quantification is being performed
   * @param qtype the quantification type
   */
  public NodeQuant(NodeAtom target, QuantType qtype) {
    this.target = target;
    this.qtype = qtype;
  }

  /**
   * Constructor for a count range quantifier (e.g. {3,6})
   * 
   * @param minCount minimum number of times the atom can occur
   * @param maxCount maximum number of times the atom can occur
   */
  public NodeQuant(NodeAtom target, int minCount, int maxCount) {
    this.target = target;
    this.qtype = QuantType.COUNT_RANGE;
    this.minCount = minCount;
    this.maxCount = maxCount;
  }

  /**
   * Constructor for an exact count quantifier (e.g. {3})
   * 
   * @param count
   */
  public NodeQuant(NodeAtom target, int count) {
    this.target = target;
    this.qtype = QuantType.COUNT;
    this.minCount = count;
    this.maxCount = count;
  }

  /** Different kinds of quantifiers that can be applied to an atom. */
  enum QuantType {
    NONE,
    // {count}
    COUNT,
    // {min,max}
    COUNT_RANGE,
    // ?
    OPTIONAL,
    // *
    STAR,
    // +
    PLUS
  }

  /** What type of quantification is applied to this atom in the regex? */
  private QuantType qtype = QuantType.NONE;

  /** If using COUNT quantification, minimum and maximum number of repeats. */
  private int minCount, maxCount;

  /** Add a quantifier in the form {min,max} to this atom. */
  public void addCountQuant(int min, int max) {
    if (qtype != QuantType.NONE) {
      throw new RuntimeException("Tried to add two quantifiers to an atom");
    }
    qtype = QuantType.COUNT_RANGE;
    minCount = min;
    maxCount = max;
  }

  /** Add a "?" quantifier to this atom. */
  public void addOptionalQuant() {
    if (qtype != QuantType.NONE) {
      throw new RuntimeException("Tried to add two quantifiers to an atom");
    }
    qtype = QuantType.OPTIONAL;
  }

  /** Shared implementation of dump(); takes care of printing quantifiers. */
  @Override
  public void dump(PrintStream stream, int indent) {
    target.dump(stream, indent);

    switch (qtype) {
      case COUNT:
        stream.print("\n");
        printIndent(stream, indent);
        stream.printf("Quantifier: {%d}", minCount);
        break;

      case COUNT_RANGE:
        stream.print("\n");
        printIndent(stream, indent);
        stream.printf("Quantifier: {%d,%d}", minCount, maxCount);
        break;

      case OPTIONAL:
        stream.print("\n");
        printIndent(stream, indent);
        stream.printf("Quantifier: ?");
        break;

      case PLUS:
        stream.print("\n");
        printIndent(stream, indent);
        stream.printf("Quantifier: +");
        break;

      case STAR:
        stream.print("\n");
        printIndent(stream, indent);
        stream.printf("Quantifier: *");
        break;

      case NONE:
        // No quantifier; don't print anything.
        break;
    }

  }

  @Override
  public String toStringInternal() {
    switch (qtype) {
      case COUNT:
        return String.format("%s{%d}", target.toString(), minCount);

      case COUNT_RANGE:
        return String.format("%s{%d,%d}", target.toString(), minCount, maxCount);

      case OPTIONAL:
        return String.format("%s?", target.toString());

      case PLUS:
        return String.format("%s+", target.toString());

      case STAR:
        return String.format("%s*", target.toString());

      case NONE:
        return target.toString();

      default:
        throw new RuntimeException("Unreachable");
    }
  }

  /**
   * Convert this atom's subtree to an NFA, applying any applicable quantification.
   */
  @Override
  public final SimpleNFAState toNFA(ArrayList<SimpleNFAState> nfaStates,
      HashMap<CharSet, ArrayList<Integer>> charSetToID, ArrayList<Integer> nextStates) {
    switch (qtype) {
      case COUNT:
      case COUNT_RANGE:

        return genCountNFA(nfaStates, charSetToID, nextStates);

      case OPTIONAL:
        // Start out with the base states.
        SimpleNFAState baseStates = target.toNFA(nfaStates, charSetToID, nextStates);

        // Add epsilon transitions to all the "next" states.
        return new SimpleNFAState(baseStates, nextStates);

      case STAR:
      case PLUS:

        return getStarNFA(nfaStates, charSetToID, nextStates);

      case NONE:
        return target.toNFA(nfaStates, charSetToID, nextStates);

      default:
        throw new RuntimeException("Unreachable");
    }
  }

  private SimpleNFAState getStarNFA(ArrayList<SimpleNFAState> nfaStates,
      HashMap<CharSet, ArrayList<Integer>> charSetToID, ArrayList<Integer> nextStates) {

    // Allow the new NFA chunk to repeat. To do this, we create a space
    // for the "start" state of the sub-NFA and tell the subprocess to
    // point there for "next".
    nfaStates.add(null);
    int startStateID = nfaStates.size() - 1;

    ArrayList<Integer> newNext = new ArrayList<Integer>();
    newNext.addAll(nextStates);
    newNext.add(startStateID);

    SimpleNFAState startState = target.toNFA(nfaStates, charSetToID, newNext);

    // In the case of Kleene star, add epsilon transitions to all the
    // "next" states.
    if (QuantType.STAR == qtype) {
      startState = new SimpleNFAState(startState, nextStates);
    }

    // Put the "start" state into the place we reserved in the states
    // array, *without* stripping out epsilon transitions (the next guy
    // in line will take care of that)
    // System.err.printf("Start state at %d: %s\n", startStateID, startState);
    nfaStates.set(startStateID, startState);
    startState.setIndex(startStateID);

    return startState;
  }

  /** Compile a {m,n} quantifier into NFA states. */
  private SimpleNFAState genCountNFA(ArrayList<SimpleNFAState> nfaStates,
      HashMap<CharSet, ArrayList<Integer>> charSetToID, ArrayList<Integer> nextStates) {
    // We're going to generate a chain of "fixed" copies of the target,
    // followed by a chain of "optional" copies.
    // Of course, we're generating the state machine from back to front,
    // so things happen in reverse.

    // Start by generating the chain of "optional" copies.
    // The logic is basically an extension of that used in
    // SimpleNodeSeq.
    ArrayList<Integer> nextNext = nextStates;

    // Cut off the last iteration of the loop if there is no "required"
    // run to generate.
    boolean noRequiredRun = (0 == minCount);

    int numIter = noRequiredRun ? (maxCount - minCount - 1) : (maxCount - minCount);

    for (int i = 0; i < numIter; i++) {
      SimpleNFAState nextStart = target.toNFA(nfaStates, charSetToID, nextNext);

      // See SimpleNodeSeq.toNFA() for a version of this logic with
      // detailed comments.
      TreeSet<Integer> epsilonTrans = addStateToNFA(nfaStates, nextStart);
      nextNext = new ArrayList<Integer>();
      nextNext.add(nextStart.getIndex());

      // Add epsilon transitions to the end of the chain.
      if (null == epsilonTrans) {
        epsilonTrans = new TreeSet<Integer>();
      }
      epsilonTrans.addAll(nextStates);

      nextNext.addAll(epsilonTrans);
    }

    // We handle the first element of the "optional" chain differently
    // depending on whether there will be a "required" chain.
    if (noRequiredRun) {
      // No "required" chain to generate; finish up with the first
      // element of the "optional" chain.
      // Since the whole chain is optional, we'll need an epsilon
      // transition to the stuff that comes after this entry in the regex.
      SimpleNFAState startState = target.toNFA(nfaStates, charSetToID, nextNext);
      return new SimpleNFAState(startState, nextStates);
    } else {

      // Generate the "required" chain.
      // Leverage the logic that's already in SimpleNodeSeq.
      NodeSeq dummySeq = new NodeSeq();
      for (int i = 0; i < minCount; i++) {
        dummySeq.add(target);
      }
      return dummySeq.toNFA(nfaStates, charSetToID, nextNext);
    }
  }

  @Override
  public void getCharClasses(HashMap<CharSet, Integer> classCounts) {

    switch (qtype) {

      case COUNT:
      case COUNT_RANGE:
        // The NFA states for this node will be repeated up to maxCount
        // times. Make sure that the char class counts reflect this reality.
        HashMap<CharSet, Integer> tmp = new HashMap<CharSet, Integer>();
        target.getCharClasses(tmp);
        for (CharSet cs : tmp.keySet()) {

          Integer origCountObj = classCounts.get(cs);
          int origCount = (null == origCountObj ? 0 : origCountObj.intValue());
          int newCount = origCount + maxCount * tmp.get(cs);
          classCounts.put(cs, newCount);
        }

      default:
        target.getCharClasses(classCounts);
    }
  }
}
