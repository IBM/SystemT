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
 * Parse tree node for the single-character lookahead/lookbehind boundary conditions like \A and \Z
 * 
 * 
 */
public class NodeBoundaryCond extends Node {

  public static final NodeBoundaryCond WORD_BOUNDARY = new NodeBoundaryCond(Type.WORD_BOUNDARY);

  public static final NodeBoundaryCond NON_WORD_BOUNDARY =
      new NodeBoundaryCond(Type.NON_WORD_BOUNDARY);

  public static final NodeBoundaryCond BEGINNING = new NodeBoundaryCond(Type.BEGINNING);

  public static final NodeBoundaryCond PREV_MATCH_END = new NodeBoundaryCond(Type.PREV_MATCH_END);

  public static final NodeBoundaryCond END_NO_TERMINATOR =
      new NodeBoundaryCond(Type.END_NO_TERMINATOR);

  public static final NodeBoundaryCond END = new NodeBoundaryCond(Type.END);

  public static final NodeBoundaryCond CARET = new NodeBoundaryCond(Type.CARET);

  public static final NodeBoundaryCond DOLLAR = new NodeBoundaryCond(Type.DOLLAR);

  /** Types of boundary condition that we recognize */
  private enum Type {
    WORD_BOUNDARY("\\b"), //
    NON_WORD_BOUNDARY("\\B"), //
    BEGINNING("\\A"), //
    PREV_MATCH_END("\\G"), //
    END_NO_TERMINATOR("\\Z"), //
    END("\\z"), CARET("^"), DOLLAR("$"),

    ;

    private String code;

    private Type(String code) {
      this.code = code;
    }
  }

  private Type type;

  private NodeBoundaryCond(Type type) {
    this.type = type;
  }

  @Override
  public void dump(PrintStream stream, int indent) {
    printIndent(stream, indent);
    stream.print(type.code);
  }

  @Override
  public String toStringInternal() {
    return type.code;
  }

  @Override
  public void getCharClasses(HashMap<CharSet, Integer> classCounts) {
    throw new RuntimeException("Should never be called");
  }

  @Override
  public SimpleNFAState toNFA(ArrayList<SimpleNFAState> nfaStates,
      HashMap<CharSet, ArrayList<Integer>> charSetToID, ArrayList<Integer> nextStates) {
    throw new RuntimeException("A boundary condition check cannot be converted to an NFA!");
  }
}
