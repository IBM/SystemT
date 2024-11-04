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

/**
 * A single state in the deterministic finite automaton that SimpleRegex builds up incrementally.
 */
public class SimpleDFAState {

  /**
   * DFA state ID that indicates a transition into a section of the DFA that hasn't yet been
   * created.
   */
  public static final int NOT_A_STATE_ID = -1;

  /** DFA state ID that indicates a transition to an empty set of NFA states. */
  public static final int FAIL_STATE_ID = -2;

  /** Transition table of DFA states; index is character class ID. */
  int[] nextDFAStates;

  /** Indices of the NFA states that correspond to this DFA state. */
  int[] nfaStates;

  /**
   * Indexes of regexes that are in the ACCEPT state in the underlying NFAs.
   */
  int[] acceptStates;

  public SimpleDFAState(int[] nextDFAStates, int[] nfaStates, int[] acceptStates) {
    this.nextDFAStates = nextDFAStates;
    this.nfaStates = nfaStates;
    this.acceptStates = acceptStates;
  }
}
