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

import com.ibm.systemt.regex.charclass.CharSet;

/**
 * Single-character special escapes, such as \n, \s, \w. Each class of escape character has its own
 * dedicated static final instance.
 */
final class NodeSpecialEsc extends NodeCharClass {

  /** \n */
  public static final NodeSpecialEsc NEWLINE = new NodeSpecialEsc('n', new char[] {'\n'});

  /** \r */
  public static final NodeSpecialEsc RETURN = new NodeSpecialEsc('r', new char[] {'\r'});


  /** \t */
  public static final NodeSpecialEsc TAB = new NodeSpecialEsc('t', new char[] {'\t'});


  /** \s == [ \t\n\x0B\f\r] */
  public static final NodeSpecialEsc WHITESPACE =
      new NodeSpecialEsc('s', new char[] {' ', '\t', '\n', 0x0B, '\f', '\r'});

  /** \d == [0-9] */
  public static final NodeSpecialEsc DIGIT =
      new NodeSpecialEsc('d', new char[] {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'});


  /*
   * BEGIN CLASS FIELDS
   */

  /** Canonical representation of this special escape character. */
  private String canonRep;

  private CharSet chars;

  /*
   * BEGIN CLASS METHODS
   */
  private NodeSpecialEsc(char specialChar, char[] actualChars) {
    super(0x0);
    // this.specialChar = specialChar;
    this.canonRep = String.format("\\%c", specialChar);

    chars = new CharSet();
    for (char c : actualChars) {
      chars.add(c);
    }
  }

  /** Generate by falling back on the Java regex implementation. */
  public NodeSpecialEsc(String charRegex, int flags) {
    super(flags);
    this.canonRep = charRegex;
    this.chars = CharSet.fromRegex(this.canonRep, flags);
  }


  @Override
  public void dump(PrintStream stream, int indent) {
    NodeChar.printIndent(stream, indent);
    stream.printf("Special Char: %s", canonRep);
  }

  @Override
  public String toStringInternal() {
    return canonRep;
  }


  @Override
  protected CharSet getCharSetInternal() {
    return chars;
  }
}
