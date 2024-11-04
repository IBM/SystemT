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
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

import com.ibm.systemt.regex.api.SimpleNFAState;
import com.ibm.systemt.regex.charclass.CharSet;
import com.ibm.systemt.regex.util.StringUtils;

/**
 * Parse tree nodes for the different types of atoms that match a single character.
 */
public abstract class NodeChar extends NodeAtom {

  /** Regex matching mode flags for this node. */
  protected int flags;

  protected NodeChar(int flags) {
    this.flags = flags;
  }

  @Override
  public void getCharClasses(HashMap<CharSet, Integer> classCounts) {
    CharSet cs = getCharSetInternal();

    if (classCounts.containsKey(cs)) {
      // Already have an entry for this character class (or something
      // equivalent); just increment the counter.
      int origVal = classCounts.get(cs);
      classCounts.put(cs, origVal + 1);
    } else {
      // Haven't used this char class yet.
      classCounts.put(cs, 1);
    }
  }

  @Override
  public SimpleNFAState toNFA(ArrayList<SimpleNFAState> nfaStates,
      HashMap<CharSet, ArrayList<Integer>> charSetToID, ArrayList<Integer> nextStates) {

    CharSet cs = getCharSetInternal();

    TreeMap<Integer, TreeSet<Integer>> transitions = new TreeMap<Integer, TreeSet<Integer>>();

    // Generate a transition to the next states for each character ID in
    // the character set.
    ArrayList<Integer> charIDs = charSetToID.get(cs);
    for (Integer id : charIDs) {
      TreeSet<Integer> targets = transitions.get(id);
      if (null == targets) {
        targets = new TreeSet<Integer>();
        transitions.put(id, targets);
      }
      targets.addAll(nextStates);
    }

    return new SimpleNFAState(transitions);
  }

  /**
   * Default implementation of this method just uses the Java regex engine to determine the
   * character class. Override if there's a quicker way to make the determination.
   */
  protected CharSet getCharSetInternal() {
    String singleCharRegexStr = this.toString();
    return CharSet.fromRegex(singleCharRegexStr, flags);
  }

  /** A single-character atom. */
  static class Char extends NodeChar {

    protected Token origTok;

    protected char c;

    /**
     * The characters that this char matches (may be more than one, due to the CASE_INSENSITIVE
     * flag)
     */
    protected CharSet cs;

    Char(Token origTok, int flags) {
      super(flags);
      if (origTok.image.length() != 1) {
        throw new RuntimeException("Token should only contain one character");
      }

      this.origTok = origTok;
      setChar(origTok.image.charAt(0));
    }

    /**
     * Constructor for use by subclasses that need to decode the (encoded) character passed to them.
     */
    protected Char(Token origTok, char actualChar, int flags) {
      super(flags);
      this.origTok = origTok;
      setChar(actualChar);
    }

    /**
     * Used by this class to set the (single) character that this parse tree node represents. Even
     * though there's only one character, it may translate to several Unicode character codes,
     * depending on regex flags.
     */
    private void setChar(char theChar) {
      this.c = theChar;

      // System.err.printf("Flags are %x\n", flags);

      if (0 != (flags & Pattern.CASE_INSENSITIVE)) {
        char[] toAdd =
            {c, Character.toLowerCase(c), Character.toUpperCase(c), Character.toTitleCase(c)};

        // System.err.printf("Case-insensitive matching: %s\n",
        // Arrays.toString(toAdd));

        // Case-insensitive matching; add all the cases of this
        // character that we can think of.
        cs = new CharSet();
        for (char curChar : toAdd) {
          cs.add(curChar);
        }
      } else {
        // Case-sensitive matching; just add the requested character.
        cs = new CharSet(c);
      }
    }

    @Override
    public void dump(PrintStream stream, int indent) {
      printIndent(stream, indent);
      stream.append(c);
    }

    @Override
    public String toStringInternal() {
      return origTok.image;
    }

    @Override
    protected CharSet getCharSetInternal() {
      return cs;
    }

  }

  /** An escaped single character (e.g. "\(") */
  public static final class EscChar extends Char {

    public EscChar(Token origTok, int flags) {
      super(origTok, flags);
    }

    @Override
    public void dump(PrintStream stream, int indent) {
      printIndent(stream, indent);
      stream.printf("\\%s", origTok.image);
    }

    @Override
    public String toStringInternal() {
      return String.format("\\%s", origTok.image);
    }
  }

  /** A hex escape like "\x22" or "\x0022" */
  public static final class HexEscChar extends Char {

    boolean isFourChars;

    public HexEscChar(Token origTok, int flags) {
      super(origTok, decodeHexEsc(origTok.image), flags);

      int len = origTok.image.length();
      if (4 == len) {
        // 2 chars of hex + "\x"
        isFourChars = false;
      } else if (6 == len) {
        // 4 chars of hex + "\x"
        isFourChars = true;
      } else {
        // This should never happen
        throw new RuntimeException(
            String.format("Hex escape '%s' does not" + " have 2 or 4 chars of hex", origTok.image));
      }
    }

    @Override
    public void dump(PrintStream stream, int indent) {
      printIndent(stream, indent);
      if (isFourChars) {
        stream.printf("\\x%4x", c);
      } else {
        stream.printf("\\x%2x", c);
      }
    }

    @Override
    public String toStringInternal() {
      return origTok.image;
    }

    private static char decodeHexEsc(String str) {
      // First two characters are the "\x"
      return StringUtils.decodeHex(str, 2, str.length() - 2);
    }

    // private static char decodeHex(String str) {
    // // First two chars are the "\x".
    // char accum = 0;
    //
    // for (int i = 2; i < str.length(); i++) {
    // char curChar = str.charAt(i);
    //
    // char baseChar;
    // if (curChar >= '0' && curChar <= '9') {
    // baseChar = '0';
    // } else if (curChar >= 'a' && curChar <= 'f') {
    // baseChar = 'a' - 8;
    // } else if (curChar >= 'A' && curChar <= 'F') {
    // baseChar = 'A' - 8;
    // } else {
    // throw new RuntimeException("Unexpected char in hex escape;"
    // + " should never happen");
    // }
    // accum += (str.charAt(i) - baseChar);
    // if (i < str.length() - 1) {
    // accum <<= 4;
    // }
    // }
    //
    // return accum;
    // }
  }

  /** An octal escape like "\04" or "\022" or "\0022" */
  public static final class OctalEscChar extends Char {

    /** Number of octal characters after the escape; can be 1, 2, or 3 */
    int numChars;

    public OctalEscChar(Token origTok, int flags) {
      super(origTok, decodeOctal(origTok.image), flags);

      int len = origTok.image.length();

      // Don't count the "\0" at the beginning of the escape.
      numChars = len - 2;

      if (numChars > 3) {
        throw new RuntimeException("Wrong num chars in octal escape");
      }
    }

    @Override
    public void dump(PrintStream stream, int indent) {
      printIndent(stream, indent);
      if (1 == numChars) {
        stream.printf("\\0%1o", c);
      } else if (2 == numChars) {
        stream.printf("\\0%2o", c);
      } else if (3 == numChars) {
        stream.printf("\\0%3o", c);
      } else {
        throw new RuntimeException("Wrong num chars in octal escape");
      }
    }

    @Override
    public String toStringInternal() {
      return origTok.image;
    }

    private static char decodeOctal(String str) {
      // First two chars are the "\0".
      char accum = 0;

      for (int i = 2; i < str.length(); i++) {
        char curChar = str.charAt(i);

        if (curChar >= '0' && curChar <= '7') {
          accum += curChar - '0';
        } else {
          throw new RuntimeException("Unexpected char in octal escape;" + " should never happen");
        }

        if (i < str.length() - 1) {
          accum <<= 3;
        }
      }

      return accum;
    }
  }

  /** A Unicode character class */
  static final class Unicode extends NodeChar {

    String classCode;

    public Unicode(String classCode, int flags) {
      super(flags);
      this.classCode = classCode;
    }

    @Override
    public void dump(PrintStream stream, int indent) {
      printIndent(stream, indent);
      stream.printf("Unicode: %s", classCode);
    }

    @Override
    public String toStringInternal() {
      return String.format("\\p{%s}", classCode);
    }

  }

}
