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

/**
 * Parse tree nodes for atomic parts of a regular expression. Handles quantification logic for all
 * its subclasses.
 */
public abstract class NodeAtom extends Node {


  /*
   * BEGIN SPECIFIC KINDS OF ATOM
   */

  /** Parse tree node for a parenthesized regular expression. */
  static final class Paren extends NodeAtom {

    Node child;

    boolean isCapturing;

    public Paren(Node child, boolean isCapturing) {
      this.child = child;
      this.isCapturing = isCapturing;
    }



    @Override
    public void dump(PrintStream stream, int indent) {
      printIndent(stream, indent);

      if (isCapturing)
        stream.printf("(\n");
      else
        stream.printf("(?:\n");

      child.dump(stream, indent + 1);
      stream.print("\n");

      printIndent(stream, indent);
      stream.printf(")");
    }

    @Override
    public String toStringInternal() {
      return String.format("(%s%s)", (isCapturing ? "" : "?:"), child);
    }

    @Override
    public void getCharClasses(HashMap<CharSet, Integer> classCounts) {
      child.getCharClasses(classCounts);
    }

    @Override
    public SimpleNFAState toNFA(ArrayList<SimpleNFAState> nfaStates,
        HashMap<CharSet, ArrayList<Integer>> charSetToID, ArrayList<Integer> nextStates) {
      // Just pass through to the child.
      return child.toNFA(nfaStates, charSetToID, nextStates);
    }

  }

}
