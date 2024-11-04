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

public class NodeSeq extends Node {

  private ArrayList<Node> nodes = new ArrayList<Node>();

  @Override
  public void dump(PrintStream stream, int indent) {
    printIndent(stream, indent);
    stream.printf("Sequence:\n");
    for (Node node : nodes) {
      node.dump(stream, indent + 1);
      stream.print("\n");
    }
  }

  @Override
  public String toStringInternal() {
    if (0 == nodes.size()) {
      return "";
    }

    StringBuilder sb = new StringBuilder();
    for (Node node : nodes) {
      sb.append(node.toString());
    }
    return sb.toString();
  }

  public void add(Node cur) {
    nodes.add(cur);
  }

  public int size() {
    return nodes.size();
  }

  public Node get(int i) {
    return nodes.get(i);
  }

  @Override
  public void getCharClasses(HashMap<CharSet, Integer> classCounts) {
    for (Node n : nodes) {
      n.getCharClasses(classCounts);
    }
  }

  @Override
  public SimpleNFAState toNFA(ArrayList<SimpleNFAState> nfaStates,
      HashMap<CharSet, ArrayList<Integer>> charSetToID, ArrayList<Integer> nextStates) {

    if (0 == nodes.size()) {
      throw new RuntimeException("Empty sequence");
    }

    ArrayList<Integer> nextNext = nextStates;

    SimpleNFAState nextStart;

    // Compile the sequence from back to front.
    for (int i = nodes.size() - 1; i > 0; i--) {
      Node node = nodes.get(i);

      nextStart = node.toNFA(nfaStates, charSetToID, nextNext);

      // Add

      // Add this starting state to the official NFA state set. Strip out
      // epsilon transitions while we're at it.
      TreeSet<Integer> epsilonTrans = addStateToNFA(nfaStates, nextStart);

      // Make the next iteration generate NFA states that will point to
      // this one.
      nextNext = new ArrayList<Integer>();
      nextNext.add(nextStart.getIndex());

      // Don't forget those epsilon transitions!
      if (null != epsilonTrans) {
        nextNext.addAll(epsilonTrans);
      }
    }

    // Just return the node for the first element in the sequence, instead
    // of stripping out epsilon transitions and adding it to the node set.
    return nodes.get(0).toNFA(nfaStates, charSetToID, nextNext);
  }

}
