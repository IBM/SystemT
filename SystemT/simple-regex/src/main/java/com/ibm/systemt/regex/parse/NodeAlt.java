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

import com.ibm.systemt.regex.api.SimpleNFAState;
import com.ibm.systemt.regex.charclass.CharSet;

/** Regex parse tree node for alternation (e.g. a|b) */
public class NodeAlt extends Node {

  private ArrayList<Node> nodes = new ArrayList<Node>();

  @Override
  public void dump(PrintStream stream, int indent) {
    printIndent(stream, indent);
    stream.printf("Alternation:\n");
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

    // Build up the string node1|node2|...|noden
    StringBuilder sb = new StringBuilder();
    sb.append(nodes.get(0).toString());
    for (int i = 1; i < nodes.size(); i++) {
      sb.append('|');
      sb.append(nodes.get(i).toString());
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

    // Compile all the alternates, then merge their starting states into one
    // big starting state.
    ArrayList<SimpleNFAState> startingStates = new ArrayList<SimpleNFAState>();

    for (Node node : nodes) {
      startingStates.add(node.toNFA(nfaStates, charSetToID, nextStates));
    }

    return new SimpleNFAState(startingStates);
  }

}
