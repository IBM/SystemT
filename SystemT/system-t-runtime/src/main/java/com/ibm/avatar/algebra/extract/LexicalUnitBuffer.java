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
package com.ibm.avatar.algebra.extract;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Store lexical units which are segmented by UniLexAnalyzer
 */
class LexicalUnitBuffer {

  /**
   * Lexical unit
   */
  class LexicalUnit {
    /** Lexical unit type */
    final int type;
    /** Surface form string */
    final String surface;
    /** Frost gloss */
    final Object gloss;
    /** Classification code */
    final int wclass;

    /**
     * Constructor
     * 
     * @param type
     * @param surface
     * @param gloss
     * @param wclass
     */
    LexicalUnit(final int type, final String surface, final Object gloss, final int wclass) {
      this.type = type;
      this.surface = surface;
      this.gloss = gloss;
      this.wclass = wclass;
    }
  }

  /** List to store the unit */
  private ArrayList<LexicalUnit> list;

  /**
   * Constructor
   */
  LexicalUnitBuffer() {
    list = new ArrayList<LexicalUnit>();
  }

  /**
   * Clear
   */
  void clear() {
    list.clear();
  }

  /**
   * Get an iterator
   * 
   * @return Iterator
   */
  Iterator<LexicalUnit> iterator() {
    return list.iterator();
  }

  /**
   * Add a lexical unit.
   * 
   * @param unit
   */
  void add(final LexicalUnit unit) {
    list.add(unit);
  }

  /**
   * Add a lexical unit.
   * 
   * @param type
   * @param surface
   * @param gloss
   * @param wclass
   */
  void add(final int type, final String surface, final Object gloss, final int wclass) {
    add(new LexicalUnit(type, surface, gloss, wclass));
  }

  /**
   * Returns true if the buffer is empty.
   * 
   * @return true if the buffer is empty.
   */
  boolean isEmpty() {
    return list.isEmpty();
  }
}
