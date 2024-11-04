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
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.base.ProfileRecord;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.SpanText;
import com.ibm.avatar.algebra.util.dict.DictParams.CaseSensitivityType;
import com.ibm.avatar.algebra.util.lang.LanguageSet;
import com.ibm.avatar.algebra.util.tokenize.BaseOffsetsList;
import com.ibm.avatar.algebra.util.tokenize.OffsetsList;
import com.ibm.avatar.algebra.util.tokenize.Tokenizer;
import com.ibm.avatar.aog.StringTable;
import com.ibm.avatar.logging.Log;

/**
 * Base classes for implementations of a dictionary matcher. Shared by the Dictionary operator and
 * the MatchesDict selection predicate.
 */
public abstract class DictImpl {

  /**
   * Default dictionary languages to use if no language is specified. Currently, we such
   * dictionaries are applied on ALL languages.
   */
  // public static final LanguageSet DEFAULT_LANGS = LanguageSet
  // .create(LangCode.DICT_DEFAULT_LANG_CODES);
  // LanguageSet.create(LangCode.values());
  // LanguageSet.create(LangCode.en);
  /**
   * If this flag is set to TRUE, then all dictionaries will automatically remove any duplicate
   * entries.
   */
  private static final boolean REMOVE_DUPLICATES = true;

  /**
   * this field holds entries for which lemma match is required
   */
  protected DictHashTable<HashDictEntry> lemmaDict;

  /**
   * Object used to mark tokenization overhead that this operator incurs.
   */
  protected final ProfileRecord tokRecord;

  /** Maximum number of tokens in any dictionary entry. */
  private int maxEntryToks;

  /**
   * @return the maximum number of tokens in any entry of this dictionary
   */
  public int getMaxEntryToks() {
    return maxEntryToks;
  }

  /**
   * Collates the compiled dictionary entries and passes the subclass a data structure with all the
   * information it needs to create a dictionary matching engine. This constructor will be consumed
   * while initializing, dictionary operator and dictionary specific predicate.
   * 
   * @param compiledDictionaries list of compiled dictionaries
   * @param cases corresponding match modes (case insensitive vs. not)
   * @param dm thread-local tables for memoization across dictionaries
   * @param globalStringTable global string table to avoid storing duplicate dictionary entry tokens
   * @param tokRecord profiler record to charge tokenization overhead to
   */
  public DictImpl(CompiledDictionary[] compiledDictionaries, CaseSensitivityType[] cases,
      DictMemoization dm, StringTable globalStringTable, ProfileRecord tokRecord) {
    boolean debug = false;

    this.tokRecord = tokRecord;

    if (compiledDictionaries.length != cases.length) {
      throw new IllegalArgumentException("dicts and cases arrays must be of the same length");
    }

    // Loop through all dicts with lemma_match first
    // Then loop through all dicts without lemma_match
    for (boolean lemmaMatch : new boolean[] {true, false}) {
      // We'll build up a set of all our dictionary entries, broken down by
      // the sequence of tokens that triggers each one.
      HashMap<String, DictEntry> entryMap = new HashMap<String, DictEntry>();

      // Start collation
      for (int i = 0; i < compiledDictionaries.length; i++) {
        if (compiledDictionaries[i].getLemmaMatch() != lemmaMatch)
          continue;

        if (debug) {
          Log.debug("DictImpl: Dictionary %d (%s) has %d entries", i,
              compiledDictionaries[i].getCompiledDictName(),
              compiledDictionaries[i].getNumberOfEntries());
        }

        // AQL statement can override, the matching condition declared in the
        // dictionary
        CaseSensitivityType aqlStmtLevelCaseMatching = null;
        if (null != cases[i]) {
          aqlStmtLevelCaseMatching = cases[i];
        }

        // Map of tokenized entry string vs. matching condition.
        Map<String, PerEntryParam> entryToMatchingCond;
        try {
          entryToMatchingCond = compiledDictionaries[i].getTokenizedEntries();
        } catch (Exception e) {
          // TODO: need to log this error to log file
          System.err.println(e.getMessage());
          throw new RuntimeException(String.format("Error decompiling dictionary %s",
              compiledDictionaries[i].getCompiledDictName()));
        }

        // Now that we have all the entries broken down by languages, create
        // the appropriate dictionary entry objects.
        for (Entry<String, PerEntryParam> entry : entryToMatchingCond.entrySet()) {

          // Fetch tokenized entry
          String canonEntryStr = entry.getKey();

          // Fetch per entry matching condition
          PerEntryParam entryMatchCond = entry.getValue();

          // Language set for tokenized entry in hand
          LanguageSet canonEntryLangs = entryMatchCond.getLangSet();

          CaseSensitivityType entryCaseMatchCond;
          // Entry case matching condition will be use only, if AQL statement
          // has not forced to use a different one
          if (null == aqlStmtLevelCaseMatching) {
            entryCaseMatchCond = entryMatchCond.getCaseCond();
          } else {
            entryCaseMatchCond = aqlStmtLevelCaseMatching;
          }

          // Case matching for tokenized entry in hand
          boolean ignoreCase;
          if (CaseSensitivityType.exact.equals(entryCaseMatchCond)) {
            ignoreCase = false;
          } else if (CaseSensitivityType.insensitive.equals(entryCaseMatchCond)) {
            ignoreCase = true;
          } else {
            throw new RuntimeException(
                String.format("Can't yet handle case '%s'", entryCaseMatchCond));
          }

          if (ignoreCase) {
            // Use lowercase if we're doing case-insensitive
            // matching
            /**
             * While converting to Lower Case passing the Locale information to fix defect. Defect
             * [#160180]: Issue with dictionary matching when locale is turkish Description: Problem
             * reported in the context of IOPES. On turkish locale, dictionary matching uses
             * toLowerCase() which causes capital "I" to be lowercase into "?".
             */
            canonEntryStr = canonEntryStr.toLowerCase(Locale.ENGLISH);
          }

          // Prepare DictEntry object
          DictEntry entryInfo = entryMap.get(canonEntryStr);
          DictEntry newEntryInfo =
              DictEntry.makeEntry(entryInfo, canonEntryLangs, i, ignoreCase, dm);

          entryMap.put(canonEntryStr, newEntryInfo);

          // Update maximum token in an entry; if current entry has more tokens
          // than any of the already encountered
          // entry.
          maxEntryToks = updateMaxToks(canonEntryStr, maxEntryToks);
        }
      }

      if (entryMap.size() == 0)
        continue;

      // Convert from hashtable to a more-compact array format.
      ArrayList<String> keys = new ArrayList<String>();
      keys.addAll(entryMap.keySet());
      int numEntries = keys.size();
      String[] canonEntryStrs = new String[numEntries];
      DictEntry[] entryData = new DictEntry[numEntries];

      for (int i = 0; i < numEntries; i++) {
        String key = keys.get(i);
        canonEntryStrs[i] = key;
        entryData[i] = entryMap.get(key);
      }
      keys = null;
      entryMap = null;

      if (REMOVE_DUPLICATES) {
        if (debug) {
          Log.debug("Removing duplicates.");
        }

        // Get rid of any duplicate entries
        for (int i = 0; i < numEntries; i++) {
          entryData[i] = entryData[i].getDuplicateFreeVersion();
        }
      }

      if (debug) {
        Log.debug("Performing low-level initialization.");
      }

      // Now we can pass the set of entries to the subclass to turn into the
      // dictionary data structure.
      init(canonEntryStrs, entryData, dm, globalStringTable, lemmaMatch);
    }
  }

  /**
   * Collates the dictionary entries and passes the subclass a data structure with all the
   * information it needs to create a dictionary.
   * 
   * @param dicts laundry lists of words/phrases to find in text
   * @param cases corresponding match modes (case insensitive vs. not)
   * @param tokenizer implementation of tokenization to be used in dividing the dictionary entries
   *        into tokens; must be synchronized with whatever tokenization is used during dictionary
   *        matching, or annotators will produce the wrong answer
   * @param dm thread-local tables for memoization across dictionaries
   * @param tokRecord profiler record to charge tokenization overhead to
   */
  public DictImpl(DictFile[] dicts, CaseSensitivityType[] cases, Tokenizer tokenizer,
      DictMemoization dm, ProfileRecord tokRecord) {
    // TODO: Leaving the empty constructor for CompressesDictImpl class to compile
    this.tokRecord = tokRecord;
  }

  /** Update our counter of the maximum number of tokens in any entry. */
  private int updateMaxToks(String canonEntryStr, int maxEntryToks) {

    int toks = 1;
    for (int i = 0; i < canonEntryStr.length(); i++) {
      if (DictFile.TOKEN_DELIM == canonEntryStr.charAt(i)) {
        toks++;
      }
    }
    return Math.max(toks, maxEntryToks);
  }

  /**
   * Finds the position base + (offset tokens) in str. Uses the same tokenization as tokenizeStr().
   * 
   * @param str the string in which to perform analysis
   * @param base where to start looking at tokens
   * @param offsetToks how far to the left or right to look
   * @param right true to look to the right of base; false to look to the left.
   * @return offset into str, or -1 if we go off either end of the string
   * @deprecated calls to this method should be replaced by OffsetsList operations
   */
  @Deprecated
  public static int findOffset(String str, int base, int offsetToks, boolean right) {

    if (offsetToks < 0) {
      throw new IllegalArgumentException("offsetToks must be >= 0");
    }

    if (base < 0) {
      throw new IllegalArgumentException("base must be >= 0");
    }

    int pos = base;

    // The logic of going left vs. right in the string is bug-prone, so we
    // implement the main loop twice.
    if (right) {
      // Going right in the string

      // Start by skipping ahead to the next non-whitespace character.
      while (Character.isWhitespace(str.charAt(pos))) {
        pos++;
        if (pos >= str.length()) {
          // Reached the end of the string.
          return -1;
        }
      }

      // Now go forward by the requested number of tokens.
      for (int t = 0; t < offsetToks; t++) {
        // Skip token
        if (Character.isLetterOrDigit(str.charAt(pos))) {
          // Word token; skip the rest of the word.
          do {
            pos++;
            if (pos >= str.length()) {
              return -1;
            }
          } while (Character.isLetterOrDigit(str.charAt(pos)));

        } else {
          // Punctuation token
          pos++;
          if (pos >= str.length()) {
            return -1;
          }
        }

        // Skip whitespace
        while (Character.isWhitespace(str.charAt(pos))) {
          pos++;
          if (pos >= str.length()) {
            return -1;
          }
        }
      }

      return pos;

    } else {
      // Going left in the string

      // Start by skipping back to the next non-whitespace character.
      while (Character.isWhitespace(str.charAt(pos - 1))) {
        pos--;
        if (pos <= 0) {
          // Reached the beginning of the string.
          return -1;
        }
      }

      // Now go back by the requested number of tokens.
      for (int t = 0; t < offsetToks; t++) {
        // Skip token
        if (Character.isLetterOrDigit(str.charAt(pos - 1))) {
          // Word token; skip the rest of the word.
          do {
            pos--;
            if (pos <= 0) {
              return -1;
            }
          } while (Character.isLetterOrDigit(str.charAt(pos - 1)));

        } else {
          // Punctuation token
          pos--;
          if (pos <= 0) {
            return -1;
          }
        }

        // Skip whitespace
        while (Character.isWhitespace(str.charAt(pos - 1))) {
          pos--;
          if (pos <= 0) {
            return -1;
          }
        }
      }

      return pos;
    }

  }

  /**
   * Find all dictionary matches in the indicated span.
   * 
   * @param span the span to perform the match on
   * @param mt used for getting tokenization buffers
   * @param output preallocated buffer for holding the dictionary matches found
   */
  public void findMatches(Span target, MemoizationTable mt, BaseOffsetsList output) {

    // Start with offsets in token distances.
    BaseOffsetsList tokOffsets = new BaseOffsetsList();

    findMatchesTok(target, mt, tokOffsets);

    // Convert to character offsets.
    mt.profileEnter(tokRecord);
    OffsetsList tokens = mt.getTokenizer().tokenize(target);
    mt.profileLeave(tokRecord);

    output.reset();
    for (int i = 0; i < tokOffsets.size(); i++) {
      int beginChar = tokens.begin(tokOffsets.begin(i));
      int endChar = tokens.end(tokOffsets.end(i));
      output.addEntry(beginChar, endChar, tokOffsets.index(i));
    }
  }

  /**
   * @return Returns true if any of the dictionaries requires lemma_match
   */
  public boolean requireLemmaMatch() {
    return lemmaDict != null;
  }

  /*
   * ABSTRACT METHODS
   */

  /**
   * Initialize the subclass's internal data structures.
   * 
   * @param canonEntries canonical entry strings; each entry string consists of the original entry,
   *        with {@link DictFile#TOKEN_DELIM} in place of the space between tokens. If
   *        case-insensitive matching was requested, the entry is turned to lowercase.
   * @param entryData for each element of canonEntries, information about what kind of matching to
   *        perform.
   * @param dm thread-local tables for memoization across dictionaries
   * @param globalStringTable global string table to avoid storing duplicate dictionary entry tokens
   * @param requireLemmaMatch whether the dictionaries require lemma_match
   */
  protected abstract void init(String[] canonEntryStrs, DictEntry[] entryData, DictMemoization dm,
      StringTable globalStringTable, boolean requireLemmaMatch);

  /**
   * Find all dictionary matches in the indicated Span's text, making use of cached tokens if
   * applicable. Offsets are returned in terms of the target span's tokens.
   * 
   * @param inputSpan the span to perform the match on
   * @param mt used for getting tokenization buffers
   * @param output preallocated buffer for holding the dictionary matches found
   */
  public abstract void findMatchesTok(SpanText inputSpan, MemoizationTable mt,
      BaseOffsetsList output);

  /**
   * @param span the span to perform the match on
   * @param mt table of state; provides the tokenizer.
   * @return true if the indicated string contains at least one match of any of our dictionaries.
   */
  public abstract boolean containsMatch(Span span, MemoizationTable mt);

}
