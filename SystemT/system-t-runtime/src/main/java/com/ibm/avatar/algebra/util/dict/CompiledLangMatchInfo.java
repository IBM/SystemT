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

import java.util.Arrays;

/**
 * Compiled runtime representation of LangMatchInfo
 */
public class CompiledLangMatchInfo {

  /** Which indexes have a case-sensitive match, or null if none */
  protected int[] caseSensitiveIndices = null;

  /** Which indexes have a case-insensitive match, or null if none */
  protected int[] caseInsensitiveIndices = null;

  /**
   * Build an instance of this class from the properties of a LangMatchInfo object. This constructor
   * is intended for the exclusive use of the LangMatchInfo class!
   * 
   * @param orig object whose properties we want to duplicate.
   */
  protected CompiledLangMatchInfo(LangMatchInfo orig) {

    // Copy and sort the lists of indices.
    if (orig.caseSensitiveIndices.size() > 0) {
      caseSensitiveIndices = new int[orig.caseSensitiveIndices.size()];
      for (int i = 0; i < caseSensitiveIndices.length; i++) {
        caseSensitiveIndices[i] = orig.caseSensitiveIndices.get(i);
      }
      Arrays.sort(caseSensitiveIndices);
    }
    if (orig.caseInsensitiveIndices.size() > 0) {
      caseInsensitiveIndices = new int[orig.caseInsensitiveIndices.size()];
      for (int i = 0; i < caseInsensitiveIndices.length; i++) {
        caseInsensitiveIndices[i] = orig.caseInsensitiveIndices.get(i);
      }
      Arrays.sort(caseInsensitiveIndices);
    }

  }

}
