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
import java.util.Iterator;
import java.util.Locale;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.base.ProfileRecord;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.SpanText;
import com.ibm.avatar.algebra.util.dict.DictParams.CaseSensitivityType;
import com.ibm.avatar.algebra.util.string.LowercaseSubstr;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.algebra.util.tokenize.BaseOffsetsList;
import com.ibm.avatar.algebra.util.tokenize.OffsetsList;
import com.ibm.avatar.aog.StringTable;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.logging.Log;

/**
 * Our main internal implementation of a dictionary matcher. Shared by the Dictionary operator and
 * the MatchesDict selection predicate.
 */
public class HashDictImpl extends DictImpl {
  public static final String[] EMPTY_STRING_ARRAY = new String[0];

  /**
   * Should we let Java manage the token strings using a static cache that's part of the String
   * class?
   */
  static boolean INTERN_TOKEN_STRINGS = true;

  /**
   * All of our dictionaries, merged together. Key is the FIRST word for a multi-word phrase.
   */
  protected DictHashTable<HashDictEntry> dict;

  /**
   * Convenience constructor for a single dictionary file.
   */
  public HashDictImpl(CompiledDictionary dict, CaseSensitivityType caseType, DictMemoization dm,
      StringTable globalStringTable, ProfileRecord tokRecord) {
    this(new CompiledDictionary[] {dict}, new CaseSensitivityType[] {caseType}, dm,
        globalStringTable, tokRecord);
  }

  /**
   * Constructor to create a dictionary matching engine, for the given list of compiled
   * dictionaries.
   * 
   * @param dicts list of compiled dictionaries
   * @param cases corresponding match modes (case insensitive vs. not)
   * @param dm thread-local tables for memoization across dictionaries
   * @param globalStringTable global string table to avoid storing duplicate dictionary entry tokens
   * @param tokRecord
   */
  public HashDictImpl(CompiledDictionary[] dicts, CaseSensitivityType[] cases, DictMemoization dm,
      StringTable globalStringTable, ProfileRecord tokRecord) {
    super(dicts, cases, dm, globalStringTable, tokRecord);
  }

  @Override
  protected void init(String[] canonEntryStrs, DictEntry[] entryData, DictMemoization dm,
      StringTable globalStringTable, boolean requireLemmaMatch) {
    if (requireLemmaMatch) {
      lemmaDict = initDict(canonEntryStrs, entryData, dm, globalStringTable);
      // Log.debug("lemmaDict size = %d", lemmaDict.size());
    } else {
      dict = initDict(canonEntryStrs, entryData, dm, globalStringTable);
      // Log.debug("dict size = %d", dict.size());
    }
  }

  private ClosedHashTable<HashDictEntry> initDict(String[] canonEntryStrs, DictEntry[] entryData,
      DictMemoization dm, StringTable globalStringTable) {
    final boolean debug = false;
    final boolean genNumToksHist = false;

    int[] numToksHist = null;
    if (genNumToksHist) {
      // We'll capture a histogram of the number of tokens in entries, for
      // debugging purposes.
      numToksHist = new int[20];
    }

    // dict = new DictHashMap<HashDictEntry>();
    ClosedHashTable<HashDictEntry> dict = new ClosedHashTable<HashDictEntry>();

    // Go through the canonicalized dictionary entries, adding them to our
    // hash table according to their first token.
    for (int i = 0; i < canonEntryStrs.length; i++) {
      String canonEntry = canonEntryStrs[i];
      DictEntry matchConds = entryData[i];

      // Free up space for the data structures we'll create below.
      canonEntryStrs[i] = null;
      entryData[i] = null;

      // Each canonical entry consists of the tokens, separated by
      // DictFile.TOKEN_DELIM.

      // We're going to build a hash table that maps from the lowercase
      // first token to a set of HashDictEntry objects holding metadata
      // about matches that start with the indicated token.

      // Break the canonical entry into a lowercase first token and a full
      // array of tokens.
      String[] allToks = StringUtils.split(canonEntry, DictFile.TOKEN_DELIM);

      if (genNumToksHist) {
        numToksHist[allToks.length]++;
      }

      // Ensure that Java stores each unique token once, if they are
      // strings.
      if (INTERN_TOKEN_STRINGS) {

        // Laura 8/7/09: avoid String.intern() by using a StringTable
        // instead
        for (int tokix = 0; tokix < allToks.length; tokix++) {
          // allToks[tokix] = allToks[tokix].intern();
          allToks[tokix] = globalStringTable.getUniqueStr(allToks[tokix]);
        }
      }

      if (debug) {
        Log.debug("HashDictImpl: Split '%s' into %s", canonEntry, Arrays.toString(allToks));
      }

      /**
       * While converting to Lower Case passing the Locale information to fix defect. Defect
       * [#160180]: Issue with dictionary matching when locale is turkish Description: Problem
       * reported in the context of IOPES. On turkish locale, dictionary matching uses toLowerCase()
       * which causes capital "I" to be lowercase into "?".
       */
      String key = allToks[0].toLowerCase(Locale.ENGLISH);
      // LowercaseSubstr key = new LowercaseSubstr(allToks[0]);

      // Get data on what kind of matches have the indicated token
      // sequence signature.
      CompiledDictEntry compiledConds = matchConds.compile();

      // Create a new entry out of the data we've collected.
      // HashDictEntry entry = new HashDictEntry(allToks, compiledConds);
      HashDictEntry entry = HashDictEntry.makeEntry(allToks, compiledConds, dm);

      // Check to see if we have another entry in the same
      // place.
      if (dict.containsKey(key)) {
        HashDictEntry prevEntry = dict.get(key);
        if (prevEntry instanceof MultiEntry) {
          ((MultiEntry) prevEntry).addEntry(entry);
        } else {
          // Upgrade the single entry to a multiple one.
          MultiEntry me = new MultiEntry(prevEntry, entry);
          dict.put(key, me);
        }
      } else {
        dict.put(key, entry);
      }

    }

    // Now make a pass through the entries, reordering them by number of
    // tokens so that matches will be emitted in order by end offset.
    for (Iterator<CharSequence> itr = dict.keyItr(); itr.hasNext();) {
      CharSequence key = itr.next();
      HashDictEntry entry = dict.get(key);
      if (entry instanceof MultiEntry) {
        // Multiple entries start with the indicated token.
        MultiEntry mentry = (MultiEntry) entry;
        mentry.sortSubEntries();
      }
    }

    // Shrink the hash table as much as possible
    dict.compact();

    if (genNumToksHist) {
      // Print a summary of the "number of tokens" information.
      Log.debug("%15s %10s", "Num tokens", "Count");
      for (int i = 0; i < numToksHist.length; i++) {
        Log.debug("%15d %10d", i, numToksHist[i]);
      }
    }
    return dict;
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.avatar.algebra.util.dict.DictImpl#findMatches(java.lang.String,
   * com.ibm.avatar.algebra.base.MemoizationTable, com.ibm.avatar.algebra.util.OffsetsList)
   */
  // public void findMatches(Span span, MemoizationTable mt,
  // BaseOffsetsList output) {
  //
  // String text = span.getText();
  //
  // // Make sure the text is tokenized.
  // OffsetsList tokens = mt.getTokenizer().tokenize(span);
  //
  // output.reset();
  //
  // for (int i = 0; i < tokens.size(); i++) {
  // // Always perform case-insensitive matching for the initial match.
  // String token = text.substring(tokens.begin(i), tokens.end(i))
  // .toLowerCase();
  //
  // HashDictEntry e = dict.get(token);
  // if (null != e) {
  //
  // // Mark off all the matches for the entry that matched this
  // // token.
  // e.markMatches(mt, span, tokens, i, output, false);
  // }
  // }
  //
  // }
  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.avatar.algebra.util.dict.DictImpl#findMatchesTok(com.ibm.avatar
   * .algebra.datamodel.Span, com.ibm.avatar.algebra.base.MemoizationTable,
   * com.ibm.avatar.algebra.util.OffsetsList)
   */
  @Override
  public void findMatchesTok(SpanText target, MemoizationTable mt, BaseOffsetsList output) {

    mt.profileEnter(tokRecord);
    OffsetsList tokens = mt.getTokenizer().tokenize(target);
    mt.profileLeave(tokRecord);

    output.reset();

    LowercaseSubstr token = new LowercaseSubstr();
    for (int i = 0; i < tokens.size(); i++) {
      findMatchesAtTok(mt, output, target, tokens, token, i);
    }
  }

  /**
   * Internal method to find all the matches at a single token location in the text.
   */
  private void findMatchesAtTok(MemoizationTable mt, BaseOffsetsList output, SpanText target,
      OffsetsList tokens, LowercaseSubstr tokenBuf, int tokenIx) {
    // match of surface form
    if (dict != null) {
      // Always perform case-insensitive matching for the initial match.
      tokenBuf.reinit(target.getText(), tokens.begin(tokenIx), tokens.end(tokenIx));

      HashDictEntry e = dict.get(tokenBuf);
      if (null != e) {
        // Mark off all the matches for the entry that matched this
        // token.
        e.markMatches(mt, target, tokens, tokenIx, output, true, false);
      }
    }

    // match of lemmatized form
    if (lemmaDict != null) {
      // get lemma first
      String lemma = tokens.getLemma(tokenIx);
      if (null == lemma)
        throw new FatalInternalError(
            // This should never happen unless there is an error in AOGPlan.hasLemmaReference or the
            // user is utilizing a 3rd
            // party tokenizer with an incorrect implementation for setting the lemma
            "Lemma of token [%d-%d] is null. This indicates that either the tokenizer does not support lemmatization, or there is an internal error in detecting lemma references in the operator graph.",
            tokens.begin(tokenIx), tokens.end(tokenIx));
      tokenBuf.reinit(lemma, 0, lemma.length());

      HashDictEntry l = lemmaDict.get(tokenBuf);
      if (null != l) {
        // Mark off all the matches for the entry that matched this
        // token.
        l.markMatches(mt, target, tokens, tokenIx, output, true, true);
      }
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.avatar.algebra.util.dict.DictImpl#containsMatch(java.lang.String,
   * com.ibm.avatar.algebra.base.MemoizationTable)
   */
  @Override
  public boolean containsMatch(Span span, MemoizationTable mt) {

    final boolean debug = false;

    // We're only trying to find one match, so create a new outputs list
    // each time.
    BaseOffsetsList output = new BaseOffsetsList();

    // Make sure the span is tokenized.
    mt.profileEnter(tokRecord);
    OffsetsList tokens = mt.getTokenizer().tokenize(span);
    mt.profileLeave(tokRecord);

    // Create a reusable buffer for the "current" token.
    LowercaseSubstr token = new LowercaseSubstr();

    String text = span.getText();

    for (int i = 0; i < tokens.size(); i++) {

      // match of surface form
      if (dict != null) {
        if (debug) {
          Log.debug("containsMatch(): Matching on " + "token %d/%d (offsets %d-%d of %d)", i,
              tokens.size() - 1, tokens.begin(i), tokens.end(i), text.length() - 1);
        }

        // Always perform case-insensitive matching for the initial match.
        // String token = text.substring(tokens.begin(i), tokens.end(i))
        // .toLowerCase();
        token.reinit(text, tokens.begin(i), tokens.end(i));

        if (debug) {
          Log.debug("containsMatch():      Current token is '%s'",
              StringUtils.escapeForPrinting(token));
        }

        HashDictEntry e = dict.get(token);
        if (null != e) {

          if (debug) {
            Log.debug("containsMatch():      Got a match"
                + " on first token; checking additional tokens");
          }

          // Mark off all the matches for the entry that matched this
          // token.
          int numNewMatch = e.markMatches(mt, span, tokens, i, output, false, false);

          if (debug) {
            Log.debug("containsMatch():      Found %d matches", numNewMatch);
          }

          if (0 != numNewMatch) {
            return true;
          }
        }
      }

      // match of lemmatized form
      if (lemmaDict != null) {
        // get lemma first
        String lemma = tokens.getLemma(i);
        token.reinit(lemma, 0, lemma.length());

        if (debug) {
          Log.debug("containsMatch():      Current lemma is '%s'",
              StringUtils.escapeForPrinting(lemma));
        }

        HashDictEntry l = lemmaDict.get(lemma);
        if (null != l) {

          if (debug) {
            Log.debug("containsMatch():      Got a match"
                + " on first lemma; checking additional lemmas");
          }

          // Mark off all the matches for the entry that matched this
          // token.
          int numNewMatch = l.markMatches(mt, span, tokens, i, output, false, true);

          if (debug) {
            Log.debug("containsMatch():      Found %d lemma matches", numNewMatch);
          }

          if (0 != numNewMatch) {
            return true;
          }
        }
      }
    }

    return false;
  }

}
