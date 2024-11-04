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
import java.util.HashSet;

import com.ibm.avatar.algebra.datamodel.Triple;
import com.ibm.avatar.algebra.util.lang.LangCode;
import com.ibm.avatar.algebra.util.lang.LanguageSet;
import com.ibm.avatar.algebra.util.string.AppendableString;
import com.ibm.avatar.logging.Log;

/**
 * Dictionary entry object used during dictionary construction to hold information about what
 * matches to produce for a given sequence of tokens. At the end of dictionary construction, this
 * class is converted into a more efficient runtime representation that provides deduplication and
 * fast match determination.
 */
public class DictEntry {

  /** Class that encapsulates the elements of this entry. */
  static class Elem extends Triple<LangCode, Integer, Boolean> {
    public Elem(LangCode lang, Integer index, Boolean ignoreCase) {
      super(lang, index, ignoreCase);
    }
  }

  /**
   * Factory method for creating entries.
   * 
   * @param orig entry to extend, or null to create a single-element entry
   * @param langs additional languages in which the dictionary matches
   * @param index additional index of the dictionary file that matches
   * @param ignoreCase true to perform case-insensitive matching
   * @return DictEntry instance for the indicated parameters
   */
  public static synchronized DictEntry makeEntry(DictEntry orig, LanguageSet langs, int index,
      boolean ignoreCase, DictMemoization dm) {

    final boolean debug = false;

    // Fetch reusable objects for the current thread
    ArrayList<Elem> elemsTmp = dm.getDictEntryElemsTmp();
    AppendableString keyStr = dm.getDictEntryKeyStr();
    HashMap<AppendableString, DictEntry> singletons = dm.getDictEntrySingletons();

    // Build up the string key that will uniquely identify this entry.
    elemsTmp.clear();

    if (null != orig) {
      // We're extending an existing entry; put its keys first.
      elemsTmp.addAll(orig.elems);
    }

    for (LangCode lang : langs) {
      elemsTmp.add(new Elem(lang, index, ignoreCase));
    }

    keyStr.reset();
    computeSignature(keyStr, elemsTmp);

    // Check if a DictEntry object for the indicated combination already
    // exists.
    DictEntry entry = singletons.get(keyStr);

    if (null == entry) {
      // No existing object; create one.
      entry = new DictEntry(elemsTmp, dm);

      if (debug) {
        Log.debug("DictEntry: Creating new object" + " for key '%s'", keyStr);
      }

      // Note that we make a copy of the keyStr, since we'll reuse the
      // object on the next call
      singletons.put(new AppendableString(keyStr), entry);
    } else {

      // if (debug) {
      // Log.debug("DictEntry: Reusing existing object for key '%s'",
      // keyStr);
      // }

    }

    return entry;
  }

  /*
   * FIELDS
   */

  private ArrayList<Elem> elems = new ArrayList<Elem>();

  /** Compiled version of this entry. */
  private CompiledDictEntry compiledEntry;

  /**
   * Duplicate-free version of this entry, or null if this entry is already duplicate-free.
   */
  private DictEntry dupFreeEntry = null;

  /*
   * CONSTRUCTORS
   */

  /** Constructor for use by factory methods. */
  // private DictEntry(LangCode lang, int index, boolean ignoreCase) {
  // elems.add(new Elem(lang, index, ignoreCase));
  // compiledEntry = new CompiledDictEntry(this);
  // dupFreeEntry = computeDupFreeEntry();
  // }
  /**
   * Constructor for use by factory methods. Takes the entries from orig and adds an additional one
   * to the end.
   */
  private DictEntry(ArrayList<Elem> listOfElems, DictMemoization dm) {
    elems.addAll(listOfElems);
    compiledEntry = new CompiledDictEntry(this, dm);
    dupFreeEntry = computeDupFreeEntry(dm);
  }

  /*
   * PUBLIC METHODS
   */

  protected LangCode getLang(int index) {
    // return langs.get(index);
    return elems.get(index).first;
  }

  protected int getDictIndex(int index) {
    // return indices.get(index);
    return elems.get(index).second;
  }

  protected boolean getIgnoreCase(int index) {
    // return ignoreCases.get(index);
    return elems.get(index).third;
  }

  protected int size() {
    // return langs.size();
    return elems.size();
  }

  /**
   * @return "runtime-friendly" version of this entry
   */
  protected CompiledDictEntry compile() {
    return compiledEntry;
  }

  protected DictEntry getDuplicateFreeVersion() {
    return dupFreeEntry;
  }

  /*
   * PRIVATE METHODS
   */

  /**
   * Compute the unique "signature" of the type of match this entry corresponds to. Make sure that
   * this method is kept in sync with the signature code in the factory methods!
   * 
   * @param strbuf reusable buffer for holding the signature
   * @param elemsList the elements of the entry; will be sorted in place!
   */
  private static void computeSignature(AppendableString strbuf, ArrayList<Elem> elemsList) {
    strbuf.reset();

    // Get the elements into a canonical order.
    Collections.sort(elemsList);

    for (Elem elem : elemsList) {
      signatureElem(strbuf, elem.first, elem.second, elem.third);
      strbuf.append(';');
    }
  }

  private static void signatureElem(AppendableString strbuf, LangCode lang, int index,
      boolean ignoreCase) {

    final boolean debug = false;

    if (debug) {
      // Debug mode -- use a human-readable string.
      strbuf.append(lang.toString());
      strbuf.append(',');
      strbuf.append(Integer.toString(index));
      strbuf.append(',');
      strbuf.append(Boolean.toString(ignoreCase));
    } else {
      // Normal mode -- generate a compact, but difficult to read, string.
      strbuf.append(lang.toString());
      strbuf.append(Integer.toString(index));
      if (false == ignoreCase) {
        strbuf.append('!');
      }
    }
  }

  /**
   * Removes duplicate entries -- those that would produce exactly the same kind of match on the
   * same language -- for this dictionary entry, and adds a pointer to the resulting entry, if
   * necessary.
   */
  private DictEntry computeDupFreeEntry(DictMemoization dm) {

    boolean debug = false;

    // Write out the new version of our combinations, maintaining the
    // original order so as not to perturb the results of unit tests.
    HashSet<Elem> alreadyDone = new HashSet<Elem>();
    ArrayList<Elem> newElems = new ArrayList<Elem>();
    for (Elem elem : elems) {
      if (debug) {
        Log.debug("Considering entry %s (hashcode %d)", elem, elem.hashCode());
      }

      if (false == alreadyDone.contains(elem)) {
        if (debug) {
          Log.debug("--> First time seeing this entry");
        }
        // First time seeing this element.
        newElems.add(elem);
        alreadyDone.add(elem);
      } else {
        if (debug) {
          Log.debug("--> Eliminating duplicate entry");
        }
      }
    }

    if (newElems.size() != elems.size()) {
      // We found and eliminated some duplicates; create the
      // duplicate-free version of this entry.
      return new DictEntry(newElems, dm);
    } else {
      return this;
    }
  }

}
