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
package com.ibm.avatar.algebra.util.dict;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

/**
 * Class for encapsulating the kinds of match that can occur within the context of a single
 * language.
 */
public class LangMatchInfo {

  /** Which indexes have a case-sensitive match */
  protected ArrayList<Integer> caseSensitiveIndices = new ArrayList<Integer>();

  /** Which indexes have a case-insensitive match */
  protected ArrayList<Integer> caseInsensitiveIndices = new ArrayList<Integer>();

  protected void addIndex(int ix, boolean ignoreCase) {
    if (ignoreCase) {
      caseInsensitiveIndices.add(ix);
    } else {
      caseSensitiveIndices.add(ix);
    }
  }

  protected CompiledLangMatchInfo compile(DictMemoization dm) {

    // Build up a string representation of this instance.
    String key = stringRep();

    HashMap<String, CompiledLangMatchInfo> singletons = dm.getLangMatchInfoSingletons();

    CompiledLangMatchInfo singleton = singletons.get(key);
    if (null == singleton) {
      // No suitable singleton exists; create one.
      singleton = new CompiledLangMatchInfo(this);
      singletons.put(key, singleton);
    }
    return singleton;
  }

  /**
   * @return a canonical string representation of this instance
   */
  private String stringRep() {

    // Get the indices in order, so that different objects with the same set
    // will get the same answer
    Collections.sort(caseInsensitiveIndices);
    Collections.sort(caseSensitiveIndices);

    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < caseInsensitiveIndices.size(); i++) {
      Integer ix = caseInsensitiveIndices.get(i);
      sb.append(ix.toString());
      if (i != caseInsensitiveIndices.size() - 1) {
        sb.append(',');
      }
    }
    sb.append('|');
    for (int i = 0; i < caseSensitiveIndices.size(); i++) {
      Integer ix = caseSensitiveIndices.get(i);
      sb.append(ix.toString());
      if (i != caseSensitiveIndices.size() - 1) {
        sb.append(',');
      }
    }
    return sb.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (false == (o instanceof LangMatchInfo)) {
      return false;
    }

    LangMatchInfo other = (LangMatchInfo) o;

    if (stringRep().equals(other.stringRep())) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * This method MUST be kept in sync with {@link #equals(Object)}
   */
  @Override
  public int hashCode() {
    return stringRep().hashCode();
  }

}
