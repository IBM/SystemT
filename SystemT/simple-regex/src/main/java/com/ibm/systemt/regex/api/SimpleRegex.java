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

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

import com.ibm.systemt.regex.charclass.CharList;
import com.ibm.systemt.regex.charclass.CharMap;
import com.ibm.systemt.regex.charclass.CharSet;
import com.ibm.systemt.regex.parse.Node;
import com.ibm.systemt.regex.parse.ParseException;
import com.ibm.systemt.regex.parse.SimpleRegexParser;
import com.ibm.systemt.regex.util.Pair;

/**
 * A simple, fast regular expression implementation. Features supported:
 * <ul>
 * <li>Alternation
 * <li>Parentheses
 * <li>Basic quantifiers: +, *, ?, {m,n}
 * <li>Unicode character classes
 * <li>Standard builtins: \d, \s
 * </ul>
 * 
 * The implementation is a Thompson NFA with optimizations:
 * <ul>
 * <li>A dedicated table for dividing characters into character classes
 * <li>A dynamically-created DFA for commonly-used sets of states
 * <li>The ability to match multiple regular expressions in a single pass
 * </ul>
 * 
 * 
 */
public class SimpleRegex implements Regex {

  /** Master debugging switch. */
  static final boolean debug = false;

  static final boolean debugFSM = debug;

  static final boolean debugExec = debug;

  /** A single entry in the charClasses table. */
  private class arrayHolder {
    int pinCount = 1;
    char[] charToID;
    int overflowCharID;
  }

  /**
   * Global table of precompiled character class arrays. Each element is a char[]. Key is original
   * regex string and flags; if two different regexes generate the same table, we don't bother
   * combining them.
   */
  private static HashMap<Pair<String, Integer>, arrayHolder> charClassTabs =
      new HashMap<Pair<String, Integer>, arrayHolder>();

  // private static final int ACCEPT_STATE_IX = 0;

  /**
   * Maximum number of DFA states we will create when running in single-regex mode. Should be a
   * power of 2.
   */
  private static final int MAX_DFA_STATE_SINGLE = 1024;

  /**
   * Maximum number of DFA states that we will create when running in multi-regex mode. Should be a
   * power of 2.
   */
  private static final int MAX_DFA_STATE_MULTI = 8192;

  /** Index of the start (DFA) state for evaluating the FSM */
  private static final int DFA_START_STATE = 0;

  /**
   * Minimum ID for a character set; used when generating the characters table.
   */
  private static final char MIN_SET_IX = 'a';

  /**
   * The states of the NFA state machine for this regex. The accept states are stored at the
   * beginning. The array is exactly the right size, with no padding at the end.
   */
  private SimpleNFAState[] nfaStates;

  /**
   * Number of regular expressions we represent with our NFA/DFA.
   */
  private int numRegexes;

  /**
   * The states of the DFA state machine that we use to accelerate NFA execution. Each DFA state
   * corresponds to a set of NFA states. This array grows as needed by doubling in size. Make sure
   * it starts out at a size that is a power of 2!
   */
  private SimpleDFAState[] dfaStates = new SimpleDFAState[4];

  /** A mapping from sets of NFA states to the corresponding DFA states. */
  private HashMap<HashSet<Integer>, Integer> nfaStatesToDFAIx =
      new HashMap<HashSet<Integer>, Integer>();

  private int numDFAStates = 0;

  // private int[] startStates;

  /** Parse trees for the regexes; kept around for debugging. */
  private Node[] parseTrees;

  /** Original regex string(s) */
  private String[] patterns;

  /** Matching mode flags associated with the original regex string(s) */
  private int[] flags;

  /**
   * Mapping from characters to internal character class IDs. We ensure that there are less than
   * 65536 of these IDs.
   */
  private char[] charToID;

  public char[] getCharToID() {
    return charToID;
  }

  /** ID to use for characters beyond the end of {@link #charToID}. */
  private int overflowCharID;

  /**
   * The number of distinct charcter class IDs. The IDs themselves are numbered from zero.
   */
  int numCharClassIDs;

  /** Mapping from character sets to sets of character class IDs. */
  private HashMap<CharSet, ArrayList<Integer>> charSetToID;

  /**
   * Main constructor
   * 
   * @param pattern the regex pattern to compile
   * @param flags flags a la the Java regex matcher
   * @throws ParseException
   */
  public SimpleRegex(String pattern, int flags) throws ParseException {
    this(pattern, flags, false);
  }

  /**
   * Convenience constructor for when you want to use the default set of flags.
   * 
   * @param pattern the regex pattern to compile
   * @throws ParseException
   */
  public SimpleRegex(String pattern) throws ParseException {
    this(pattern, 0x0);
  }

  /**
   * Constructor for when you want to keep the parse tree around for debugging.
   * 
   * @param pattern the regex pattern to compile
   * @param flags flags a la the Java regex matcher
   * @param keepParseTree set to true to keep the parse tree around after generating an NFA
   */
  public SimpleRegex(String pattern, int flags, boolean keepParseTree) throws ParseException {
    numRegexes = 1;

    if (debug) {
      System.err.printf("Compiling /%s/\n", pattern);
    }

    // Start by parsing the regex.
    SimpleRegexParser parser;
    try {
      parser = new SimpleRegexParser(pattern, flags);
    } catch (IOException e) {
      // This should never happen.
      throw new RuntimeException(e);
    }
    parseTrees = new Node[1];
    parseTrees[0] = parser.Input();

    patterns = new String[1];
    this.flags = new int[1];
    patterns[0] = pattern;
    this.flags[0] = flags;

    // Then generate the characters table and auxiliary data structures.
    genCharTable();

    // Compile an NFA for the regex.
    genNFA();

    // Clean up various data structures that were used in building the NFA.
    charSetToID = null;

    if (false == keepParseTree) {
      parseTrees = null;
    }
  }

  /**
   * Constructor for running multiple regexes at once.
   * 
   * @param patterns regex patterns to compile
   * @param flags flags a la the Java regex matcher, one per regex
   */
  public SimpleRegex(String[] patterns, int[] flags) throws ParseException {
    numRegexes = patterns.length;
    this.flags = flags;
    this.patterns = patterns;

    if (debug) {
      System.err.printf("Compiling multiple regexes...\n");
    }

    // Start by parsing the regexes
    parseTrees = new Node[numRegexes];
    for (int i = 0; i < numRegexes; i++) {
      SimpleRegexParser parser;
      try {
        parser = new SimpleRegexParser(patterns[i], flags[i]);
      } catch (IOException e) {
        // This should never happen.
        throw new RuntimeException(e);
      }
      parseTrees[i] = parser.Input();
    }

    // Then generate the characters table and auxiliary data structures.
    genCharTable();

    // Compile an NFA for the regexes
    genNFA();

    cleanUpCompileObjects();
  }

  /** Free objects that are used during compilation time, then discarded. */
  private void cleanUpCompileObjects() {
    // Clean up various data structures that were used in building the NFA.
    charSetToID = null;
    uniqueSetStrings = null;

    // Get rid of the parse trees.
    parseTrees = null;
  }

  @Override
  protected void finalize() throws Throwable {
    // Clean up the global table of character class arrays.
    if (1 == patterns.length) {
      synchronized (charClassTabs) {
        arrayHolder ah = charClassTabs.get(patterns[0]);
        ah.pinCount--;
        if (0 == ah.pinCount) {
          charClassTabs.remove(patterns[0]);
        }
      }
    }
  }

  /**
   * @param pattern a regular expression
   * @param flags integer flags to control regex matching
   * @param checkBoundaryConds true to verify that any boundary conditions (single-char
   *        lookahead/lookbehind at the very beginning or end of the expression) specified in the
   *        expression are supported by the engine
   * @return true if the indicated regular expression string is supported by this class with the
   *         indicated match mode flags
   */
  public static final boolean isSupported(String pattern, int flags, boolean checkBoundaryConds) {

    // First, check for unsupported flags.
    final int UNSUPPORTED_MASK = Pattern.CANON_EQ | Pattern.COMMENTS | Pattern.LITERAL
        | Pattern.UNICODE_CASE | Pattern.UNIX_LINES;
    if (0 != (flags & UNSUPPORTED_MASK)) {
      // We don't support one or more of the flags
      return false;
    }

    // Try parsing the regular expression.
    SimpleRegexParser parser;
    try {
      parser = new SimpleRegexParser(pattern, flags);
    } catch (IOException e) {
      // This should never happen.
      throw new RuntimeException(e);
    }

    Node parseTree;
    try {
      parseTree = parser.Input();
    } catch (ParseException e) {
      // Regex doesn't parse, so we clearly can't execute it.
      return false;
    }

    if (checkBoundaryConds) {
      // Check boundary conditions to see if they are currently
      // implemented.
      // Currently we don't implement any of the boundary conditions!
      if (null != parseTree.getBeforeBoundCond() || null != parseTree.getAfterBoundCond()) {
        return false;
      }
    }

    // TODO: Catch regexes that do parse but won't execute properly.

    return true;
  }

  public String getExpr() {
    if (null == parseTrees) {
      // No parse trees; use the original regex string(s)
      if (1 == patterns.length) {
        return patterns[0];
      } else {
        return Arrays.toString(patterns);
      }
    }

    // If we have a parse tree, reverse-engineer the regex for debugging
    // purposes.

    if (1 == parseTrees.length) {
      return parseTrees[0].toString();
    } else {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < parseTrees.length; i++) {
        sb.append(String.format("Regex %d: %s\n", i, parseTrees[i].toString()));
      }
      return sb.toString();
    }
  }

  /**
   * Pretty-print the parse tree rooted at this node.
   * 
   * @param stream where to print to
   * @param indent starting indent level (in tabs)
   * 
   */
  public void dump(PrintStream stream, int indent) {
    if (1 == parseTrees.length) {
      parseTrees[0].dump(stream, indent);
    } else {
      for (int i = 0; i < parseTrees.length; i++) {
        stream.printf("Regex %d:\n", i);
        parseTrees[i].dump(stream, indent + 1);
      }
    }
  }

  public RegexMatcher matcher(String text) {
    return new SimpleRegexMatcher(this, text);
  }

  /*
   * INTERNAL METHODS
   */

  /**
   * Convenience class to avoid typing lots of template params. Encodes a mapping from a char set
   * (encoded as a string) to an array of the character codes in the set.
   */
  static class set2vals extends HashMap<String, CharList> {
    private static final long serialVersionUID = 1L;
  }

  /**
   * Generate the table that maps from characters to internal character classes.
   */
  private void genCharTable() {

    // Start by getting a list of all the character classes that appear in
    // the regex.
    HashMap<CharSet, Integer> classCounts = new HashMap<CharSet, Integer>();
    for (int i = 0; i < parseTrees.length; i++) {
      parseTrees[i].getCharClasses(classCounts);
    }

    if (classCounts.size() > 65000) {
      // Prevent character overflow.
      throw new RuntimeException("Too many different classes");
    }

    // Give each character class an index (as a char to aid debugging)
    // to identify it by.
    char nextIx = MIN_SET_IX;

    if (debug) {
      System.err.printf("Class mapping is as follows:\n");
    }

    CharMap<CharSet> ixToSet = new CharMap<CharSet>();
    CharMap<Integer> ixToCount = new CharMap<Integer>();
    for (CharSet cs : classCounts.keySet()) {
      ixToSet.put(nextIx, cs);
      ixToCount.put(nextIx, classCounts.get(cs));

      if (debug) {
        System.err.printf("%c = %s\n", nextIx, cs);
      }

      nextIx++;
    }

    char maxIx = (char) (nextIx - 1);

    // Figure out the points along the number line where the set of char
    // sets could change.
    CharList changePoints = computeChangePoints(classCounts);

    // System.err.printf("Dedup'd change points are: %s\n",
    // Arrays.toString(changePoints.toIntArray()));

    ArrayList<String> metaSets = computeClassIDs(ixToSet, ixToCount, maxIx, changePoints);

    // Now we know the number of character class IDs.
    numCharClassIDs = metaSets.size();

    if (numCharClassIDs >= Character.MAX_VALUE) {
      // Char IDs need to fit into 16 bits!
      throw new RuntimeException("Too many character class IDs");
    }

    if (debug) {
      System.err.printf("==> %d char class IDs total.\n", numCharClassIDs);
    }

    // Invert the mapping in the sorted metaSets array.
    HashMap<String, Integer> setToID;
    {
      setToID = new HashMap<String, Integer>();

      if (debug) {
        System.err.printf("%6s %s\n", "Index", "Set");
      }

      for (int i = 0; i < metaSets.size(); i++) {
        if (debug) {
          System.err.printf("%6d %s\n", i, metaSets.get(i));
        }

        setToID.put(metaSets.get(i), i);
      }
    }

    // Create the auxiliary table that will be used during compilation.
    // This table maps CharSet objects to sets of internal character IDs.
    computeCharSetToID(ixToSet, metaSets, setToID);

    if (debug) {
      System.err.printf("CharSet to ID mapping is:\n");
      for (CharSet cs : charSetToID.keySet()) {
        ArrayList<Integer> ids = charSetToID.get(cs);

        System.err.printf("%40s ==> %s\n", cs, ids);
      }
    }

    // Now we can finally generate the table.
    genCharIDMap(changePoints, ixToSet, maxIx, setToID);

    // Clear out data structures that were used by genSetString().
    genSetStringBuf = null;
    uniqueSetStrings = null;
  }

  private void computeCharSetToID(CharMap<CharSet> ixToSet, ArrayList<String> metaSets,
      HashMap<String, Integer> setToID) {
    {
      charSetToID = new HashMap<CharSet, ArrayList<Integer>>();

      for (String setStr : metaSets) {

        // Fetch the ID associated with this set of CharSets
        int id = setToID.get(setStr);

        // Add this ID to the list for each component CharSet.
        // We get the CharSets by decoding the set string (each
        // character is
        // an index into ixToSet)
        for (int i = 0; i < setStr.length(); i++) {
          char c = setStr.charAt(i);
          CharSet cs = ixToSet.get(c);
          ArrayList<Integer> ids = charSetToID.get(cs);
          if (null == ids) {
            // No IDs yet for this CharSet
            ids = new ArrayList<Integer>();
            charSetToID.put(cs, ids);
          }

          ids.add(id);
        }
      }
    }
  }

  private ArrayList<String> computeClassIDs(CharMap<CharSet> ixToSet, CharMap<Integer> ixToCount,
      char maxIx, CharList changePoints) {
    // Now we can figure out what sets of charsets actually appear.
    HashSet<String> metaSetsSet;
    {
      metaSetsSet = new HashSet<String>();

      for (int i = 0; i < changePoints.size(); i++) {
        char c = changePoints.get(i);

        // Find all the character sets that contain this char,
        // and use those to build up a key string.
        String key = genSetString(ixToSet, maxIx, c);

        // System.err.printf("'%c' --> %s\n", c, key);
        metaSetsSet.add(key);
      }
    }

    // Order the different meta-sets by how common they are, so that high
    // indices will not be used too often.
    ArrayList<String> metaSets;
    {
      metaSets = new ArrayList<String>(metaSetsSet);

      class comp implements Comparator<String> {

        CharMap<Integer> i2c;

        comp(CharMap<Integer> i2c) {
          this.i2c = i2c;
        }

        /**
         * @return 1 if s1 represents a set with lower popularity, 0 if they have the same
         *         popularity, and 1 s1 represents a set with greater popularity.
         */
        public int compare(String s1, String s2) {
          int pop1 = 0;
          int pop2 = 0;

          // Compute popularity scores.
          for (int i = 0; i < s1.length(); i++) {
            char ix = s1.charAt(i);
            pop1 += i2c.get(ix);
          }
          for (int i = 0; i < s2.length(); i++) {
            char ix = s2.charAt(i);
            pop2 += i2c.get(ix);
          }

          // Note the inverted sort order.
          return pop2 - pop1;
        }

      }

      Collections.sort(metaSets, new comp(ixToCount));
    }
    return metaSets;
  }

  private CharList computeChangePoints(HashMap<CharSet, Integer> classCounts) {
    CharList changePoints;
    {
      changePoints = new CharList();
      changePoints.add((char) 0);
      for (CharSet cs : classCounts.keySet()) {
        cs.getChangePoints(changePoints);
      }
      changePoints.sort();

      // System.err.printf("Sorted change points are: %s\n",
      // Arrays.toString(changePoints.toIntArray()));

      // Remove duplicates.
      CharList tmp = new CharList();
      int lastVal = -1;
      for (int i = 0; i < changePoints.size(); i++) {
        int curVal = changePoints.get(i);
        if (curVal != lastVal) {
          tmp.add((char) curVal);
          lastVal = curVal;
        }
      }
      changePoints = tmp;
    }
    return changePoints;
  }

  /**
   * Generate the table that maps characters to character class set IDs.
   * 
   * @param changePoints points along the number line where the set of character classes could
   *        change.
   * @param ixToSet mapping from set indices to character set strings
   * @param maxIx maximum index in ixToSet
   * @param setToID mapping from character set strings to character set IDs.
   */
  private void genCharIDMap(CharList changePoints, CharMap<CharSet> ixToSet, char maxIx,
      HashMap<String, Integer> setToID) {

    // First, check whether there's a cached table.
    Pair<String, Integer> cacheKey = new Pair<String, Integer>(patterns[0], flags[0]);
    if (1 == patterns.length) {
      synchronized (charClassTabs) {
        if (charClassTabs.containsKey(cacheKey)) {
          if (debug) {
            System.err.printf("==> Using cached chars table.\n");
          }
          arrayHolder ah = charClassTabs.get(cacheKey);
          ah.pinCount++;
          charToID = ah.charToID;
          overflowCharID = ah.overflowCharID;
          return;
        }
      }
    }

    charToID = new char[Character.MAX_VALUE];

    // Walk through the list of change points. Each point starts an interval
    // in which all characters match the same CharSets.
    for (int i = 0; i < changePoints.size(); i++) {

      // Get the end points (inclusive) of the current interval
      char start = changePoints.get(i);
      char end;
      if (i < changePoints.size() - 1) {
        // Last char in the interval is the one before the next change
        // point, if any.
        end = (char) (changePoints.get(i + 1) - 1);
      } else {
        end = Character.MAX_VALUE - 1;
      }

      // Figure out what index to give every character in the interval
      String setStr = genSetString(ixToSet, maxIx, start);
      int id = setToID.get(setStr);

      // System.err.printf("Labeling %s through %s with index %d\n",
      // CharSet.makePrintable(start), CharSet.makePrintable(end),
      // id);

      for (int c = start; c <= end; c++) {
        charToID[c] = (char) id;
      }
    }

    // Shorten the array by removing the run of unused characters at the
    // end.
    overflowCharID = charToID[charToID.length - 1];
    int pos = charToID.length - 1;
    int runLen = 0;
    while (pos > 0 && charToID[pos] == overflowCharID) {
      pos--;
      runLen++;
    }

    char[] tmp = new char[charToID.length - runLen];
    System.arraycopy(charToID, 0, tmp, 0, charToID.length - runLen);
    charToID = tmp;

    if (debug) {
      System.err.printf("Stripped a run of %d copies of %d" + " out of char table (max is %d).\n",
          runLen, overflowCharID, numCharClassIDs);
      System.err.printf("   Resulting table is of size %d\n", charToID.length);
    }

    // Save a copy of the array for other copies of this regex.
    if (1 == patterns.length) {
      synchronized (charClassTabs) {
        arrayHolder ah = new arrayHolder();
        ah.pinCount = 1;
        ah.charToID = charToID;
        ah.overflowCharID = overflowCharID;
        charClassTabs.put(cacheKey, ah);
      }
    }
  }

  /** Buffer used by genSetString() */
  private char[] genSetStringBuf = new char[64];

  private ArrayList<String> uniqueSetStrings = new ArrayList<String>();

  /**
   * Generate the unique string that represents which character sets a character belongs to.
   * 
   * @param ixToSet mapping from set indices to character sets
   * @param maxIx highest set index represented in ixToSet
   * @param c character to test
   * @return unique character set string.
   */
  private String genSetString(CharMap<CharSet> ixToSet, char maxIx, int c) {

    int len = 0;

    for (char ix = MIN_SET_IX; ix <= maxIx; ix++) {
      if (ixToSet.get(ix).contains((char) c)) {
        genSetStringBuf[len++] = ix;
      }
    }

    // Check whether we already have created the indicated string.

    for (int i = 0; i < uniqueSetStrings.size(); i++) {
      String s = uniqueSetStrings.get(i);

      if (s.length() == len) {
        boolean equal = true;
        int j = 0;
        while (equal && j < len) {
          if (s.charAt(j) != genSetStringBuf[j]) {
            equal = false;
          }
          j++;
        }
        if (equal) {
          // Already have a copy of this string; return the copy.
          return s;
        }
      }
    }

    // If we get here, we don't have a copy of the indicated string. Create
    // one.
    String key = String.copyValueOf(genSetStringBuf, 0, len).intern();
    uniqueSetStrings.add(key);

    return key;
  }

  /** Compile an NFA to execute the regular expression. */
  private void genNFA() {

    ArrayList<SimpleNFAState> stateList = new ArrayList<SimpleNFAState>();

    // Add the accepting states, one per regex.
    for (int i = 0; i < numRegexes; i++) {
      stateList.add(SimpleNFAState.ACCEPT);
    }

    // Create an array to hold the starting states of the NFAs for our
    // component regexes.
    ArrayList<SimpleNFAState> regexStarts = new ArrayList<SimpleNFAState>();

    // Compile each of the regexes into NFA states.
    for (int i = 0; i < numRegexes; i++) {

      // The toNFA() method below takes a set of "where to go next"
      // states;
      // bootstrap this set with the accepting state for this regex.
      ArrayList<Integer> bootstrapNext = new ArrayList<Integer>();
      bootstrapNext.add(i);

      regexStarts.add(parseTrees[i].toNFA(stateList, charSetToID, bootstrapNext));
    }

    // Now create a global start state.
    SimpleNFAState startState = new SimpleNFAState(regexStarts);

    // Separate out any epsilon transitions out of the start state, and
    // create a start states set.
    TreeSet<Integer> epsilonTrans = Node.addStateToNFA(stateList, startState);

    this.nfaStates = stateList.toArray(new SimpleNFAState[stateList.size()]);

    // We start at the start state and every state that it has an epsilon
    // transition to.
    int[] startStates;
    if (null != epsilonTrans) {
      startStates = new int[epsilonTrans.size() + 1];
      startStates[0] = startState.getIndex();

      int i = 1;
      for (Integer target : epsilonTrans) {
        startStates[i++] = target;
      }
    } else {
      startStates = new int[] {startState.getIndex()};
    }

    // Create the starting DFA state.
    addDFAState(startStates);

    if (debugFSM) {
      System.err.printf("\n%d states in NFA:\n", nfaStates.length);
      for (int i = 0; i < nfaStates.length; i++) {
        System.err.printf("Entry %d is ", i);
        System.err.print(nfaStates[i].toPrettyString() + "\n");
      }

      System.err.printf("Start states are: %s\n", Arrays.toString(startStates));
    }
  }

  /**
   * Add a new state to the DFA transition table. Also updates auxiliary data structures.
   * 
   * <b>NOTE:</b> This method does not take care of thread-safety; locking must be handled by the
   * calling method!
   * 
   * @param nfaStates the set of NFA states corresponding to the DFA state to be added. This set
   *        must not contain any duplicates.
   * @return the index of the new state
   * 
   */
  private int addDFAState(int[] nfaStates) {

    if (debugFSM) {
      System.err.printf("Adding DFA state for states: %s\n", Arrays.toString(nfaStates));
    }

    // Build up the accepting states list.
    ArrayList<Integer> acceptStatesList = new ArrayList<Integer>();
    for (int i = 0; i < nfaStates.length; i++) {
      // The first <numRegexes> states are accepting states for the
      // corresponding regexes.
      if (nfaStates[i] < numRegexes) {
        if (debugFSM) {
          System.err.printf("   State %d is accepting\n", nfaStates[i]);
        }
        acceptStatesList.add(nfaStates[i]);
      }
    }

    int[] acceptStates = null;
    if (acceptStatesList.size() > 0) {
      acceptStates = new int[acceptStatesList.size()];
      for (int i = 0; i < acceptStatesList.size(); i++) {
        acceptStates[i] = acceptStatesList.get(i);
      }
    }

    int index = numDFAStates;
    numDFAStates++;

    // Create an empty transitions table.
    int[] nextDFAStates = new int[numCharClassIDs + 1];
    Arrays.fill(nextDFAStates, SimpleDFAState.NOT_A_STATE_ID);

    if (index >= dfaStates.length) {
      // Ran out of space in the states array.
      SimpleDFAState[] newArr = new SimpleDFAState[dfaStates.length * 2];
      System.arraycopy(dfaStates, 0, newArr, 0, dfaStates.length);
      dfaStates = newArr;
    }

    dfaStates[index] = new SimpleDFAState(nextDFAStates, nfaStates, acceptStates);
    numDFAStates++;

    // Update the mapping from NFA set to DFA state.
    HashSet<Integer> stateSet = new HashSet<Integer>();
    for (int i = 0; i < nfaStates.length; i++) {
      stateSet.add(nfaStates[i]);
    }
    nfaStatesToDFAIx.put(stateSet, index);

    return index;

  }

  /**
   * Method that does most of the work of running the state machine. Should only be called from a
   * SimpleRegexMatcher. Reentrant.
   * 
   * @param text the text on which to run the FSM
   * @param startPos where in the text to start execution
   * @param maxPos the last position in the text at which to match; must be less than the length of
   *        the text
   * @param matchBuf object that knows how to handle the bookkeeping for regular expression matches
   */
  protected void runFSM(CharSequence text, int startPos, int maxPos, SimpleMatchBuf matchBuf) {

    if (matchBuf.getDirty()) {
      throw new RuntimeException("Match buffer was not cleared before starting FSM!");
    }

    // We start out in the DFA, then fall back to the NFA when we run out of
    // states.
    SimpleDFAState curState = dfaStates[DFA_START_STATE];

    // Check whether any of our regexes accepts a zero-length string
    if (null != curState.acceptStates) {
      for (int i = 0; i < curState.acceptStates.length; i++) {
        int regexIx = curState.acceptStates[i];
        matchBuf.addMatch(0, regexIx);
        // matchLengths[regexIx] = 0;
      }
    }

    // Remember the number of DFA states allocated at the start of this
    // function, in case we need to do a reentrant insertion operation
    // later.
    int origNumDFAStates = numDFAStates;

    for (int pos = startPos; pos < maxPos; pos++) {

      // Translate the next character into a character class ID.
      char c = text.charAt(pos);
      int charClassID = (c >= charToID.length) ? overflowCharID : charToID[c];

      if (debugExec) {
        System.err.printf("'%s' ==> Char class %d of %d," + " and cur state has %d next states\n",
            CharSet.makePrintable(c), charClassID, numCharClassIDs, curState.nextDFAStates.length);
      }

      int nextStateID = curState.nextDFAStates[charClassID];
      if (SimpleDFAState.NOT_A_STATE_ID == nextStateID) {
        // No transition to a DFA state for this character class. If
        // there's room, create a new DFA state to handle this
        // transition.
        if (origNumDFAStates < MAX_DFA_STATE_SINGLE
            || (1 != getNumPatterns() && origNumDFAStates < MAX_DFA_STATE_MULTI)) {
          createDFAState(curState, charClassID);
        }

        // Then fall back on NFA mode.

        // The DFA state contains a list of the NFA states that the DFA
        // state represents. Use that list to initialize the NFA
        // evaluator.
        // First ensure that the NFA state buffers are large enough.
        matchBuf.ensureNFASpace(curState.nfaStates.length + 1);
        final int[] curStates = matchBuf.getCurNFAStatesBuf();
        System.arraycopy(curState.nfaStates, 0, curStates, 0, curState.nfaStates.length);
        curStates[curState.nfaStates.length] = -1;

        runNFA(text, startPos, pos, maxPos, matchBuf);
        return;
      } else if (SimpleDFAState.FAIL_STATE_ID == nextStateID) {
        // Ran out of active states, so we're done matching.
        break;
      } else {
        // We have a DFA state transition; advance to the next state and
        // check for a match at the current position.
        curState = dfaStates[nextStateID];

        if (null != curState.acceptStates) {
          for (int i = 0; i < curState.acceptStates.length; i++) {
            int regexID = curState.acceptStates[i];
            matchBuf.addMatch(pos - startPos + 1, regexID);
            // matchLengths[regexID] = pos - startPos + 1;
          }
        }
      }
    }
  }

  /**
   * Add a new DFA state transition to the indicated state, updating the transition table if
   * necessary. Reentrant; also supports current readers on the DFA states table.
   * 
   * @param curDFAState state to modify
   * @param charClassID ID of the character class on which to add a transition.
   */
  private synchronized void createDFAState(SimpleDFAState curDFAState, int charClassID) {
    // Compute the NFA states that we will be on after the transition.
    HashSet<Integer> nextNFAStates = new HashSet<Integer>();

    for (int i = 0; i < curDFAState.nfaStates.length; i++) {
      int nfaStateIx = curDFAState.nfaStates[i];
      SimpleNFAState nfaState = nfaStates[nfaStateIx];

      if (null != nfaState.transTab && charClassID < nfaState.transTab.length) {
        int[] nextNFAIDs = nfaState.transTab[charClassID];
        if (null != nextNFAIDs) {
          for (int j = 0; j < nextNFAIDs.length; j++) {
            nextNFAStates.add(nextNFAIDs[j]);
          }
        }
      }
    }

    // Check whether there is already a DFA state for the indicated
    // set of NFA states. This step also makes this method idempotent.
    int nextDFAStateIx = -1;
    if (0 == nextNFAStates.size()) {
      // SPECIAL CASE: No active NFA states
      nextDFAStateIx = SimpleDFAState.FAIL_STATE_ID;
      // END SPECIAL CASE
    } else {
      Integer val = nfaStatesToDFAIx.get(nextNFAStates);
      if (null != val) {
        nextDFAStateIx = val;
      } else {

        int[] nextStateIDs = new int[nextNFAStates.size()];
        int i = 0;
        for (int stateID : nextNFAStates) {
          nextStateIDs[i++] = stateID;
        }
        nextDFAStateIx = addDFAState(nextStateIDs);
      }
    }

    // Now that we've added the new state, we can update the state
    // transition table of the original DFA state; any readers that follow
    // the new state transition will find a valid destionation at the other
    // end.
    curDFAState.nextDFAStates[charClassID] = nextDFAStateIx;
  }

  /**
   * Run the nondeterministic finite automaton for this regex, starting from the indicated position
   * in the NFA.
   * 
   * @param text text over which to perform matching
   * @param startPos initial position in the text for matching
   * @param curPos where we currently are in the matching process (>= to startPos)
   * 
   * @param maxPos the last position in the text at which to match; must be less than the length of
   *        the text
   * @param matchBuf object that knows how to handle the bookkeeping for regular expression matches
   */
  private void runNFA(CharSequence text, int startPos, int curPos, int maxPos,
      SimpleMatchBuf matchBuf) {

    int numNextStates = 0;

    // Grab the buffer that contains the current set of active NFA states.

    // Compute the number of starting states.
    int numCurStates = 0;
    {
      final int[] curStates = matchBuf.getCurNFAStatesBuf();
      while (curStates[numCurStates] != -1) {
        numCurStates++;
      }
    }

    // Start out in the start states
    // int numCurStates = startStates.length;
    // System.arraycopy(startStates, 0, curStates, 0, startStates.length);

    for (int pos = curPos; pos < maxPos; pos++) {

      // Translate the next character into a character class ID.
      char c = text.charAt(pos);
      int charClassID = (c >= charToID.length) ? overflowCharID : charToID[c];

      // Advance every state in the current state set.
      {
        final int[] curStates = matchBuf.getCurNFAStatesBuf();
        for (int i = 0; i < numCurStates; i++) {
          int stateIx = curStates[i];
          SimpleNFAState state = nfaStates[stateIx];

          if (null != state.transTab && charClassID < state.transTab.length
              && null != state.transTab[charClassID]) {
            // Have a state transition on this character class; mark
            // all
            // the next states for the next time around.
            int[] nextIDs = state.transTab[charClassID];

            // Blindly copy the target states into the "next states"
            // buffer; we'll take care of duplicates later.
            int newNumNextStates = numNextStates + nextIDs.length;

            // First make sure we have enough space for the copy to
            // succeed.
            matchBuf.ensureNFASpace(newNumNextStates);

            System.arraycopy(nextIDs, 0, matchBuf.getNextNFAStatesBuf(), numNextStates,
                nextIDs.length);
            numNextStates = newNumNextStates;
          }
        }
      }

      // Check whether we've reached an accepting state.
      {
        final int[] nextStates = matchBuf.getNextNFAStatesBuf();
        for (int i = 0; i < numNextStates; i++) {
          // The first <numRegexes> states in the NFA are the
          // accepting
          // states for the corresponding regexes.
          if (nextStates[i] < numRegexes) {
            // Reached the "accept" state; mark the location of
            // the new longest match.
            matchBuf.addMatch(pos - startPos + 1, nextStates[i]);
            // matchBuf[nextStates[i]] = pos - startPos + 1;
            // foundAccept = true;
          }
        }
      }

      if (0 == numNextStates) {
        // State machine is no longer executing.
        return;
      } else {
        if (numNextStates > 64) {
          // Too many states, probably due to duplicates. Strip out
          // duplicate states, filling up the "current" buffer with
          // the results.
          final int[] curStates = matchBuf.getCurNFAStatesBuf();
          final int[] nextStates = matchBuf.getNextNFAStatesBuf();
          Arrays.sort(nextStates, 0, numNextStates);

          // First one always gets copied over.
          curStates[0] = nextStates[0];
          numCurStates = 1;
          int prevVal = nextStates[0];

          // For the remainder, do a merge duplicate elimination
          for (int nextIx = 1; nextIx < numNextStates; nextIx++) {
            if (nextStates[nextIx] != prevVal) {
              curStates[numCurStates++] = nextStates[nextIx];
              prevVal = nextStates[nextIx];
            }
          }

          // Now that we've copied the unique states into curStates,
          // clear out the nextStates buffer.
          numNextStates = 0;
        } else {
          // Swap current and next buffers, then clear the next
          // buffer.
          matchBuf.swapNFAStateBufs();

          numCurStates = numNextStates;
          numNextStates = 0;
        }

        // System.err.printf("NFA is in %d states.\n", numCurStates);
      }

    }

    return;
  }

  /**
   * @return the number of individual regular expressions that this SimpleRegex object evaluates at
   *         oncd.
   */
  public int getNumPatterns() {
    return numRegexes;
  }

  /**
   * @return the number of states in the nondeterministic finite state automaton that implements
   *         this regular expression
   */
  public int getNumNFAStates() {
    return nfaStates.length;
  }

}
