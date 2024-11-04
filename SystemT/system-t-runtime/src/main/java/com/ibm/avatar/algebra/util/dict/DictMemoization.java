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
import java.util.HashMap;

import com.ibm.avatar.algebra.util.dict.DictEntry.Elem;
import com.ibm.avatar.algebra.util.lang.LanguageSet;
import com.ibm.avatar.algebra.util.string.AppendableString;

/**
 * Thread-local data structures used for memoization during dictionary initialization. These data
 * structures are used by several different classes, but they are kept in this central class to
 * simplify the thread-safety code. These tables are not accessed directly via the usual
 * MemoizationTable data structure because dictionary initialization occurs before the
 * MemoizationTable is fully initialized.
 * 
 */
public class DictMemoization {

  /**
   * A global set of singletons for case-insensitive single-token entries; kept as part of this
   * class for now, but may be moved to a more central location later.
   */
  private HashMap<CompiledDictEntry, SingleTokHashDictEntry> singleTokSingletons = //
      new HashMap<CompiledDictEntry, SingleTokHashDictEntry>();

  public HashMap<CompiledDictEntry, SingleTokHashDictEntry> getSingleTokSingletons() {
    return singleTokSingletons;
  }

  /**
   * Global set of unique dictionary entries; used by factory methods to reduce memory usage by not
   * storing redundant entry info.
   */
  private HashMap<AppendableString, DictEntry> dictEntrySingletons = //
      new HashMap<AppendableString, DictEntry>();

  public HashMap<AppendableString, DictEntry> getDictEntrySingletons() {
    return dictEntrySingletons;
  }

  /**
   * Reusable string for creating keys for lookup in entryMap. Don't use this string when inserting
   * into the set!
   */
  private AppendableString dictEntryKeyStr = new AppendableString();

  public AppendableString getDictEntryKeyStr() {
    return dictEntryKeyStr;
  }

  /**
   * Reusable list for creating keys for lookup in entryMap.
   */
  private ArrayList<DictEntry.Elem> dictEntryElemsTmp = new ArrayList<Elem>();

  public ArrayList<DictEntry.Elem> getDictEntryElemsTmp() {
    return dictEntryElemsTmp;
  }

  /**
   * Set of unique LangMatchInfo objects, for memoization.
   */
  private HashMap<String, CompiledLangMatchInfo> langMatchInfoSingletons = //
      new HashMap<String, CompiledLangMatchInfo>();

  public HashMap<String, CompiledLangMatchInfo> getLangMatchInfoSingletons() {
    return langMatchInfoSingletons;
  }

  /**
   * Repository of LanguageSets that have been created for various subsets of SystemT's supported
   * languages. Key is internal bit mask.
   */
  private HashMap<byte[], LanguageSet> langSetSingletons = //
      new HashMap<byte[], LanguageSet>();

  public HashMap<byte[], LanguageSet> getLangSetSingletons() {
    return langSetSingletons;
  }

}
