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

import java.util.EnumMap;

import com.ibm.avatar.algebra.util.lang.LangCode;

/**
 * Runtime representation of the compile-time class {@link DictEntry}. This class provides data
 * structures for determining whether a dictionary match has occurred, as well as a global singleton
 * creation mechanism to avoid redundantly storing match conditions in dictionary data structures.
 * 
 */
public class CompiledDictEntry {

  /**
   * For each language code that can match this entry, information on what kind of matches can
   * occur.
   */
  private EnumMap<LangCode, CompiledLangMatchInfo> matchInfo = //
      new EnumMap<LangCode, CompiledLangMatchInfo>(LangCode.class);

  /**
   * true if this dictionary entry type can only match case-insensitive entries.
   */
  private boolean isOnlyCaseInsensitive;

  /**
   * 
   */

  /**
   * This constructor for the use of the DictEntry class only!
   */
  protected CompiledDictEntry(DictEntry entry, DictMemoization dm) {

    // Start by building up a prototypical version of matchInfo.
    EnumMap<LangCode, LangMatchInfo> protoMatchInfo = //
        new EnumMap<LangCode, LangMatchInfo>(LangCode.class);

    // Go through the different parts of the original entry and organize
    // them into nested tables.
    for (int i = 0; i < entry.size(); i++) {

      LangCode lang = entry.getLang(i);
      int dictIx = entry.getDictIndex(i);
      boolean ignoreCase = entry.getIgnoreCase(i);

      LangMatchInfo info = protoMatchInfo.get(lang);
      // int langIx = lang.ordinal();
      // LangMatchInfo info = matchInfo[langIx];
      if (null == info) {
        // No pre-existing index object. Create one.
        info = new LangMatchInfo();
        protoMatchInfo.put(lang, info);
      }

      info.addIndex(dictIx, ignoreCase);
    }

    // Intern all the LangMatchInfo objects to remove duplicates.
    for (LangCode lang : protoMatchInfo.keySet()) {
      LangMatchInfo info = protoMatchInfo.get(lang);
      matchInfo.put(lang, info.compile(dm));
    }

    // Determine whether this dictionary entry only performs
    // case-insensitive matching.
    isOnlyCaseInsensitive = true;
    for (CompiledLangMatchInfo info : matchInfo.values()) {
      if (null != info.caseSensitiveIndices) {
        isOnlyCaseInsensitive = false;
      }
    }

  }

  protected CompiledLangMatchInfo getLangInfo(LangCode lang) {
    // int langIx = lang.ordinal();
    // return matchInfo[langIx];
    return matchInfo.get(lang);
  }

  @Override
  public int hashCode() {
    return matchInfo.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (false == o instanceof CompiledDictEntry) {
      return false;
    }

    CompiledDictEntry obj = (CompiledDictEntry) o;

    // TODO: Is this check going deep enough to determine equality?
    return matchInfo.equals(obj.matchInfo);
  }

  /**
   * @return true if this dictionary entry type can only match case-insensitive entries.
   */
  public boolean getIsOnlyCaseInsensitive() {
    return this.isOnlyCaseInsensitive;
  }
}
