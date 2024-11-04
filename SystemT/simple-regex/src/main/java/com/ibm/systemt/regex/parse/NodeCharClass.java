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

import com.ibm.systemt.regex.charclass.CharSet;

/** A character class expression, such as [a-zA-Z123]. */
class NodeCharClass extends NodeChar {

  protected NodeCharClass(int flags) {
    super(flags);
  }

  /**
   * Flag that is set to true if this character class expression is negated.
   */
  boolean negated = false;

  /**
   * List of elements in the expression, so that we can print it back verbatim.
   */
  ArrayList<elem> elems = new ArrayList<elem>();

  private class elem {
    // Single character
    NodeChar c;

    // Range of characters
    Char first, last;

    elem(NodeChar c) {
      this.c = c;
    }

    elem(Char first, Char last) {
      this.first = first;
      this.last = last;
    }
  }

  /** Append a single character to the character class. */
  public void addChar(NodeChar c) {
    elems.add(new elem(c));
  }

  /** Append a range of characters to the character class. */
  public void addRange(Char first, Char last) {
    elems.add(new elem(first, last));
  }

  /**
   * @param negated if TRUE, indicates that this character class expression is negated (e.g. it
   *        starts with ^)
   */
  public void setNegated(boolean negated) {
    this.negated = negated;
  }

  @Override
  public void dump(PrintStream stream, int indent) {
    NodeChar.printIndent(stream, indent);
    stream.printf("Character Class: %s", toString());
  }

  @Override
  public String toStringInternal() {
    StringBuilder sb = new StringBuilder();
    sb.append('[');

    if (negated) {
      sb.append('^');
    }

    for (elem e : elems) {
      if (null != e.c) {
        sb.append(e.c.toString());
      } else {
        // Range
        sb.append(e.first.toString());
        sb.append('-');
        sb.append(e.last.toString());
      }
    }
    sb.append(']');
    return sb.toString();
  }

  /**
   * Fast-path method of computing the elements of a character class.
   * 
   * In unusual cases, we fall back on the slower default version of this method.
   */
  @Override
  protected CharSet getCharSetInternal() {
    if (0x0 != flags) {
      // SPECIAL CASE: One or more flags are set.
      // For now, we just fall back on the superclass's default
      // implementation for this case, since it correctly accounts for the
      // effects of flags on character classes.
      return super.getCharSetInternal();
      // END SPECIAL CASE
    }
    if (negated) {
      // SPECIAL CASE: This character class is negated.
      // For now, fall back on the slow-path version, since negating
      // character sets is problematic -- what to do about special
      // characters?
      return super.getCharSetInternal();
      // END SPECIAL CASE
    }

    CharSet ret = new CharSet();
    for (elem e : elems) {
      if (null != e.c) {
        // Single character.
        CharSet cs = e.c.getCharSetInternal();
        // System.err.printf("%s: Adding set %s for char %s\n", this,
        // cs, e.c);
        ret.addAll(cs);
      } else {
        // Character range.
        CharSet cs = new CharSet(e.first.c, e.last.c);
        ret.addAll(cs);
      }
    }
    return ret;

  }
}
