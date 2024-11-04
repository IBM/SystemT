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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.algebra.util.lang.LangCode;
import com.ibm.avatar.algebra.util.lang.LanguageSet;
import com.ibm.avatar.algebra.util.string.Escaper;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.algebra.util.tokenize.BaseOffsetsList;
import com.ibm.avatar.algebra.util.tokenize.DerivedOffsetsList;
import com.ibm.avatar.algebra.util.tokenize.Tokenizer;
import com.ibm.avatar.api.exceptions.InvalidDictionaryFileFormatException;
import com.ibm.avatar.logging.Log;

/**
 * The in-memory representation of a dictionary file. Can be built "inline" (e.g. programatically)
 * or read from a file. There is also an additional method to compiled
 * {@link #compile(Tokenizer, DictMemoization)} the dictionary.
 * 
 */
public class DictFile {

  /** Encoded value to indicate *case insensitive* match */
  public static final int CASE_INSENSITIVE = 0;

  /** Encoded value to indicate *Exact* match */
  public static final int CASE_SENSITIVE = 1;

  /**
   * Input encoding that we assume when reading dictionary files. At some point in the future, this
   * may become a configurable parameter, e.g. in the "create dictionary" statement.
   */
  private static final String DICT_ENCODING = "UTF-8";

  /**
   * List containing single empty string object. This list will be used to remove all dictionary
   * entries containing only whitespace characters
   */
  private static final List<String> EMPTY_STRING_LIST;
  static {
    EMPTY_STRING_LIST = new ArrayList<String>();
    EMPTY_STRING_LIST.add("");
  }

  /**
   * Matching parameters associated with the dictionary in hand. These parameters describes the type
   * of matches to be performed against this dictionary; for example, whether the match should be
   * case sensitive/in-sensitive. For implicitly declared dictionaries system uses default matching
   * parameters.
   */
  private DictParams params;

  /**
   * All the entries defined in the dictionary.
   */
  private List<String> entries;

  /**
   * dictionary matching parameter per entry. For dictionaries coming from table; there is an option
   * to provide per entry matching parameter.
   */
  @SuppressWarnings("unused")
  private final List<PerEntryParam> perEntryParams = null;

  /**
   * Escaper instance to strip comments from dictionary.
   */
  private static Escaper escaper = new Escaper(new char[] {'#'}, new char[] {'#'});

  /**
   * Constructor to create DictFile instance from given: dictionary entries,dictionary matching
   * parameter, and optional per dictionary entry matching parameter. <br>
   * This constructor will be consumed by: loader to compile external dictionaries and also by
   * compiler during serialization phase to compile internal dictionaries. <br>
   * If per entry matching parameter list is provided, then entries.size() == perEntryParams.size().
   * 
   * @param entries list of dictionary entries.
   * @param dictParams dictionary matching parameter
   * @param perEntryParams list of matching parameter, one per entry
   */
  public DictFile(List<String> entries, DictParams dictParams, List<PerEntryParam> perEntryParams)
      throws Exception {
    this.entries = entries;
    this.params = dictParams;
    if (null != perEntryParams) {
      throw new UnsupportedOperationException(
          "Providing per entry matching parameter is currently not implemented");
    }

    // Sanitize the entries
    cleanEntries();
  }

  /**
   * Constructor to create an in-memory dictionary instance from entries in the given stream and
   * with given dictionary matching parameters.
   * 
   * @param dictStream stream to the new-line delimited dictionary entries.
   * @param dictParam dictionary matching parameters to be used to produce matches.
   * @throws InvalidDictionaryFileFormatException
   * @throws Exception
   * @throws {@link IllegalArgumentException}
   */
  public DictFile(InputStream dictStream, DictParams dictParam)
      throws IOException, InvalidDictionaryFileFormatException {
    initDict(dictStream, dictParam);
  }

  /**
   * Constructor to create an in-memory dictionary instance from entries in the given file and with
   * given dictionary matching parameters. If dictionary matching parameters are not provided, this
   * method will create dictionary instance with default parameters.
   * 
   * @param file newline-delimited dictionary file
   * @param dictParam dictionary matching parameters to be used to produce matches.
   * @throws IOException
   * @throws InvalidDictionaryFileFormatException
   */
  public DictFile(File file, DictParams dictParam)
      throws IOException, InvalidDictionaryFileFormatException {
    FileInputStream inputStream = new FileInputStream(file);
    DictParams dictMatchingParam;
    // if given dictionary parameter is null; create default
    if (null == dictParam) {
      // No dictionary parameters provided; put in what we can, which is not sufficient
      // to compile the file
      dictMatchingParam = new DictParams();
      dictMatchingParam.setDictName(file.getName());
    } else {
      dictMatchingParam = dictParam;
    }

    try {
      initDict(inputStream, dictMatchingParam);
    } catch (IllegalArgumentException iae) {
      throw new InvalidDictionaryFileFormatException(iae,
          "An error occurred while parsing dictionary entries from the file: '%s'. Specify the dictionary file in the format as described in the Information Center.",
          file.getCanonicalPath());
    }

    inputStream.close();
  }

  /**
   * Constructor to create a dictionary instance from entries in the given file.
   * 
   * @param file newline-delimited dictionary file
   * @throws IOException
   * @throws InvalidDictionaryFileFormatException
   */
  public DictFile(File file) throws IOException, InvalidDictionaryFileFormatException {
    this(file, null);
  }

  /**
   * Convenience constructor that takes the filename as a string.
   * 
   * @throws IOException
   * @throws InvalidDictionaryFileFormatException
   */
  public DictFile(String filename) throws IOException, InvalidDictionaryFileFormatException {
    this(FileUtils.createValidatedFile(filename));
  }

  /**
   * Method to initial dictionary instance.
   * 
   * @param dictStream stream to dictionary file.
   * @param dictParam dictionary matching parameter.
   * @throws InvalidDictionaryFileFormatException
   */
  private void initDict(InputStream dictStream, DictParams dictParam)
      throws IOException, InvalidDictionaryFileFormatException {
    BufferedReader in = new BufferedReader(
        new InputStreamReader(new BufferedInputStream(dictStream), DICT_ENCODING));

    this.params = dictParam;

    this.entries = new ArrayList<String>();
    escaper.setRemoveComments(true);
    int lineNo = 0;

    while (in.ready()) {
      lineNo++;
      String line = in.readLine();

      // Remove escape sequences and comments.
      if (line != null) {

        try {
          line = escaper.deEscapeStr(line);
        } catch (Exception e) {
          throw new InvalidDictionaryFileFormatException(e,
              "An error occurred while parsing and de-escaping dictionary entry '%s' in line %d of dictionary: '%s'. "
                  + "Ensure special characters # and \\ are escaped correctly as described in the documentation for dictionary formats.",
              line, lineNo, params.getDictName());
        }

        if (0 != line.length()) {
          // System.err.printf("Got dictionary entry: '%s'\n", line);

          // Skip empty lines
          entries.add(line);
        }
      }
    }

    // Sanitize the entries we've added to our internal array.
    cleanEntries();
  }

  /**
   * Various steps to sanitize the dictionary entries.
   */
  private void cleanEntries() {
    for (int i = 0; i < entries.size(); i++) {

      String entry = entries.get(i);

      // Remove leading and trailing whitespace.
      // Laura 08/11/09: avoid interning here to speed-up AOG load time
      // for LiveText
      // Currently, in our core annotators Tables are used only for
      // dictionary loading from AOG, and the dictionary entries
      // are made unique using a StringTable by HashDictImpl.init()
      // entry = entry.trim().intern();
      // entry = entry.trim();

      // Don't use String.trim(); it misses some kinds of whitespace.
      String trimmed = StringUtils.trim(entry);
      // System.err.printf("'%s' trims to '%s'\n", entry, trimmed);
      entry = trimmed;

      entries.set(i, entry);
    }

    // discard entries constituting only whitespace characters
    entries.removeAll(EMPTY_STRING_LIST);
  }

  /**
   * Returns all the entries from dictionary in hand.
   * 
   * @return all the entries from dictionary in hand.
   */
  public List<String> getEntries() {
    return entries;
  }

  /**
   * Returns the iterator, to iterate through the dictionary's entries.
   * 
   * @return the iterator, to iterate through this dictionary's entries.
   */
  public Iterator<String> getEntriesItr() {
    return entries.iterator();
  }

  /**
   * Returns the dictionary's name.
   * 
   * @return this dictionary's name.
   */
  public String getName() {
    return params.getDictName();
  }

  /**
   * Returns the dictionary's matching parameter.
   * 
   * @return the dictionary's matching parameter.
   */
  public DictParams getParams() {
    return params;
  }

  /** Print this dictionary as a text dictionary file. */
  public void dumpToText(File f) throws IOException {
    FileWriter out = new FileWriter(f);

    out.append("# Dictionary file generated by DictFile.dumpToText()\n");

    for (int i = 0; i < entries.size(); i++) {
      String entry = entries.get(i);

      // Re-escape any comment chars in the entries
      out.append(String.format("%s\n", escaper.escapeStr(entry)));
    }

    out.close();
  }

  /**
   * Count the number of times each Java character occurs in the canonical version of every
   * dictionary entry. May produce an estimated count in the future.
   * 
   * @param tokenizer implementation of tokenization to be used in dividing the string into tokens;
   *        must be synchronized with whatever tokenization is used during dictionary matching, or
   *        annotators will produce the wrong answer
   * @param language target language for dictionary evaluation
   */
  public TreeMap<Character, Integer> computeCharCounts(Tokenizer tokenizer, LangCode language) {
    Iterator<String> itr = getEntriesItr();
    BaseOffsetsList offsets = new BaseOffsetsList();
    TreeMap<Character, Integer> charCounts = new TreeMap<Character, Integer>();

    while (itr.hasNext()) {
      String entry = itr.next();

      // "Canonicalize" the entry into a string; that is:
      // Construct a new string that puts a space between tokens and a
      // newline at the end.
      String canonEntry = canonicalizeEntry(tokenizer, offsets, entry, language);

      // Increment the appropriate character counts.
      for (int i = 0; i < canonEntry.length(); i++) {
        char c = canonEntry.charAt(i);

        if (charCounts.containsKey(c)) {
          int prevCount = charCounts.get(c);
          charCounts.put(c, prevCount + 1);
        } else {
          charCounts.put(c, 1);
        }
      }
    }
    return charCounts;
  }

  /**
   * Merge two character count tables, adding the counts in one table to the second.
   * 
   * @param source table that maps characters to counts
   * @param dest second table; the counts in source will be merged into this one
   */
  public static void mergeCharCounts(TreeMap<Character, Integer> source,
      TreeMap<Character, Integer> dest) {

    for (Entry<Character, Integer> entry : source.entrySet()) {

      Integer origCountObj = source.get(entry.getKey());
      int origCount = (null == origCountObj) ? 0 : origCountObj;

      int newCount = origCount + entry.getValue();

      dest.put(entry.getKey(), newCount);
    }
  }

  /**
   * Special character that delimits tokens within a dictionary entry.
   */
  public static final char TOKEN_DELIM = '\u2504';

  // U+2504 BOX DRAWINGS LIGHT TRIPLE DASH HORIZONTAL (┄)
  // We'll assume that dictionary entries won't contain Unicode line drawing
  // characters...

  /** Special character that delimits dictionary entries when encoding. */
  // public static final char ENTRY_DELIM = '\u2620';
  // U+2620 SKULL AND CROSSBONES (☠)
  /**
   * "Canonicalize" a dictionary entry into a string; that is: Construct a new string that puts a
   * space between tokens.
   * 
   * @param tokenizer implementation of tokenization to be used in dividing the string into tokens;
   *        must be synchronized with whatever tokenization is used during dictionary matching, or
   *        annotators will produce the wrong answer
   * @param offsets reusable offsets list
   * @param entry original version of the entry.
   * @param language target language for dictionary evaluation
   * @return canonicalized version of entry
   */
  public static String canonicalizeEntry(Tokenizer tokenizer, BaseOffsetsList offsets, String entry,
      LangCode language) {
    tokenizer.tokenizeStr(entry, language, offsets);
    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < offsets.size(); i++) {
      sb.append(entry.subSequence(offsets.begin(i), offsets.end(i)));

      if (i < offsets.size() - 1) {
        sb.append(TOKEN_DELIM);
      }
    }
    // sb.append(ENTRY_DELIM);
    return sb.toString();
  }

  /**
   * This method produces the same result as calling
   * {@link #canonicalizeEntry(Tokenizer, BaseOffsetsList, String, LangCode)} on every entry in the
   * dictionary, but it does all tokenization in one step for performance.
   * 
   * @param tokenizer implementation of tokenization to be used in dividing the string into tokens;
   *        must be synchronized with whatever tokenization is used during dictionary matching, or
   *        annotators will produce the wrong answer
   * @param language target language for dictionary evaluation
   * @return all of the entries in this dictionary, converted into canonical form, with
   *         {@link #TOKEN_DELIM} between the tokens of the entry.
   */
  public ArrayList<String> getCanonEntries(Tokenizer tokenizer, LangCode language) {

    // String that we stick between the entries when concatenating them.
    // This string should be something that every tokenizer will turn into a
    // token boundary.
    final String ENTRY_DELIM = "\n\n";

    // Concatenate all the dictionary entries into a single string, and
    // remember their offsets into said string.
    StringBuilder sb = new StringBuilder();

    int[] begins = new int[entries.size()];
    int[] ends = new int[entries.size()];

    int offset = 0;
    for (int i = 0; i < entries.size(); i++) {
      String entry = entries.get(i);

      sb.append(entry);
      sb.append(ENTRY_DELIM);

      begins[i] = offset;
      ends[i] = offset + entry.length();
      offset += entry.length() + ENTRY_DELIM.length();
    }

    String monsterStr = sb.toString();
    sb = null;
    BaseOffsetsList toks = new BaseOffsetsList();

    // System.err.printf("Tokenizing dictionary for language %s\n",
    // language);

    // Feed the resulting monster string through the tokenizer.
    tokenizer.tokenizeStr(monsterStr, language, toks);

    // Now retrieve the tokens and build canonical string versions of the
    // dict entries.
    ArrayList<String> ret = new ArrayList<String>();

    for (int i = 0; i < entries.size(); i++) {

      // Retrieve the tokens that fall in entry i, with offsets relative
      // to the original monster string.
      DerivedOffsetsList entryToks = new DerivedOffsetsList(toks, begins[i], ends[i], 0);

      if (1 == entryToks.size()) {
        // COMMON CASE: Entry consists of exactly one token.

        // Token boundaries should be perfectly aligned, since we've
        // trimmed whitespace off each entry.
        if (entryToks.begin(0) != begins[i] || entryToks.end(0) != ends[i]) {
          String entry = entries.get(i);
          String token = monsterStr.substring(entryToks.begin(0), entryToks.end(0));
          String expectedToken = monsterStr.substring(begins[i], ends[i]);
          Log.info("Monster string: %s", StringUtils.quoteStr('"', monsterStr, true, true));

          // Log.info("Char 0 is '%c' (0x%x)", monsterStr.charAt(0),
          // (int)monsterStr.charAt(0));
          // Log.info("Char 1 is '%c'", monsterStr.charAt(1));

          throw new RuntimeException(String.format(
              "While tokenizing dictionary '%s' " + "in language %s: "
                  + "Single-token entry runs from %d to %d," + " but tokenizer identified "
                  + "a token from %d to %d " + "(original entry: '%s'; token '%s';"
                  + " expected token '%s')\n" + "This error usually occurs because "
                  + "there is a difference between " + "the tokenizer's concept of whitespace "
                  + "and that of java.lang.String.trim().",
              this.getName(), language, begins[i], ends[i], entryToks.begin(0), entryToks.end(0),
              StringUtils.escapeUnicode(entry), token, expectedToken));
        }

        ret.add(entries.get(i));

        // END COMMON CASE
      } else {
        // SPECIAL CASE: Entry has multiple tokens.

        // Build up the canonical string from those tokens.
        sb = new StringBuilder();

        for (int tok = 0; tok < entryToks.size(); tok++) {
          sb.append(monsterStr, entryToks.begin(tok), entryToks.end(tok));

          if (tok < entryToks.size() - 1) {
            // Don't want a delimiter after the last token...
            sb.append(TOKEN_DELIM);
          }
        }

        ret.add(sb.toString());

        // END SPECIAL CASE
      }
    }

    return ret;

  }

  /**
   * This method compiles(tokenize and encode) the dictionary entries based on the matching
   * parameters. If entry matching parameters is not null,then they will override the global
   * dictionary match parameter.<br>
   * Note: For dictionaries coming from table; there is an option to specify per entry based
   * dictionary parameter.
   * 
   * @param tokenizer tokenizer to be used for dictionary compilation.
   * @param dm data structure for memoization across dictionaries compilation.
   * @return compiled form of the dictionary.
   */
  public CompiledDictionary compile(Tokenizer tokenizer, DictMemoization dm) throws Exception {
    boolean debug = false;

    if (debug) {
      Log.debug("Start dictionary compilation by tokenizing dictionary entries");
    }

    long startTokenization = System.currentTimeMillis();

    // Tokenized entry versus languages in which tokenization was performed
    Map<String, LanguageSet> entryToLangs = new LinkedHashMap<String, LanguageSet>();

    // String that we stick between the entries when concatenating them.
    // This string should be something that every tokenizer will turn into a
    // token boundary.
    final String ENTRY_DELIM = "\n\n";

    // Concatenate all the dictionary entries into a single string, and
    // remember their offsets into said string.
    StringBuilder sb = new StringBuilder();

    int[] begins = new int[entries.size()];
    int[] ends = new int[entries.size()];

    int offset = 0;
    for (int i = 0; i < entries.size(); i++) {
      String entry = entries.get(i).trim();

      sb.append(entry);
      sb.append(ENTRY_DELIM);

      begins[i] = offset;
      ends[i] = offset + entry.length();
      offset += entry.length() + ENTRY_DELIM.length();
    }

    String monsterStr = sb.toString();
    sb = null;
    BaseOffsetsList tokens;

    // Get language codes
    if (null == this.params.getLangStr()) {
      throw new Exception(String.format(
          "Parameters for dictionary '%s' do not contain a language set", params.getDictName()));
    }
    LanguageSet dictLangs = LanguageSet.create(this.params.getLangStr(), dm);

    // Tokenize the dictionary entries against each language
    for (LangCode lang : dictLangs) {
      // Buffer to hold tokenization result: that is token boundaries
      tokens = new BaseOffsetsList();

      // Tokenize
      tokenizer.tokenizeStr(monsterStr, lang, tokens);

      for (int i = 0; i < entries.size(); i++) {

        String tokenizedEntry;
        // Retrieve the tokens that fall in entry i, with offsets relative
        // to the original monster string.
        DerivedOffsetsList entryToks = new DerivedOffsetsList(tokens, begins[i], ends[i], 0);

        if (1 == entryToks.size()) {
          // COMMON CASE: Entry consists of exactly one token.

          // Token boundaries should be perfectly aligned, since we've
          // trimmed whitespace off each entry.
          if (entryToks.begin(0) != begins[i] || entryToks.end(0) != ends[i]) {
            String entry = entries.get(i);
            String token = monsterStr.substring(entryToks.begin(0), entryToks.end(0));
            String expectedToken = monsterStr.substring(begins[i], ends[i]);

            // Log.info("Char 0 is '%c' (0x%x)", monsterStr.charAt(0),
            // (int)monsterStr.charAt(0));
            // Log.info("Char 1 is '%c'", monsterStr.charAt(1));

            throw new RuntimeException(String.format(
                "While canonicalizing dictionary '%s' " + "in language %s: "
                    + "Single-token entry runs from %d to %d," + " but tokenizer identified "
                    + "a token from %d to %d " + "(original entry: '%s'; token '%s';"
                    + " expected token '%s')\n" + "This error usually occurs because "
                    + "there is a difference between " + "the tokenizer's concept of whitespace "
                    + "and that of java.lang.String.trim().",
                this.getName(), lang, begins[i], ends[i], entryToks.begin(0), entryToks.end(0),
                StringUtils.escapeUnicode(entry), token, expectedToken));
          }

          tokenizedEntry = entries.get(i);

          // END COMMON CASE
        } else {
          // SPECIAL CASE: Entry has multiple tokens.

          // Build up the canonical string from those tokens.
          sb = new StringBuilder();

          for (int tok = 0; tok < entryToks.size(); tok++) {
            sb.append(monsterStr, entryToks.begin(tok), entryToks.end(tok));

            if (tok < entryToks.size() - 1) {
              // Don't want a delimiter after the last token...
              sb.append(TOKEN_DELIM);
            }
          }
          tokenizedEntry = sb.toString();

          // END SPECIAL CASE
        }

        LanguageSet currentLangs = entryToLangs.get(tokenizedEntry);
        LanguageSet newLangs = null;
        if (null == currentLangs) {
          newLangs = LanguageSet.create(lang, dm);
        } else {
          newLangs = LanguageSet.create(currentLangs, lang, dm);
        }

        entryToLangs.put(tokenizedEntry, newLangs);

      }
    }

    if (debug) {
      Log.debug(
          "Time taken to tokenize the dictionary entries for all the languages: %d milli seconds",
          System.currentTimeMillis() - startTokenization);
    }

    // Done with tokenization, lets encode; encode only non empty dictionaries.We do allow empty
    // dictionaries:
    // 1) Internal dictionary coming from file can be empty
    // 2) External dictionaries with allow_empty set to true can be empty.
    try {
      if (entryToLangs.size() > 0)
        return encodeDictionary(entryToLangs, dictLangs, tokenizer.getName());
      else
        // return new CompiledDictionary (params.getDictName (), new HashMap<Integer, String> (),
        // dictLangs,
        // new ArrayList<int[]> (), TokenizerType.getTokenizerType (tokenizer));
        return CompiledDictionary.createEmptyCompiledDictionary(params.getDictName(),
            tokenizer.getName());
    } catch (Exception e) {
      // TODO need to log this error to log file
      // System.err.println (e.getMessage ());
      throw new Exception(String.format("Error while compiling dictionary %s", this.getName()), e);
    }

  }

  /**
   * This method encodes the tokenized dictionary entries. Each tokenized dictionary entry is
   * encoded into an int[]: the first element encodes the *set of languages*, the second element
   * encodes the type of case sensitivity to use (1 is case sensitive and 0 is case insensitive) and
   * the remaining element denote the position of each token in the table of unique tokens across
   * the dictionary(we assume the convention that the first token in the unique token table is
   * stored at position 0, the second token in the unique token table is at position 1 and so on).
   * Language set is encode using {@link EncodeDecodeLanguageSet}.
   * 
   * @param tokenizedEntries tokenized form of the dictionary.
   * @param dictLangs langauge set for which dictionary match is to be performed.
   * @param tokenizerType type of the tokenizer used to tokenize dictionary.
   * @return compiled form of the dictionary.
   * @throws Exception
   */
  private CompiledDictionary encodeDictionary(Map<String, LanguageSet> tokenizedEntries,
      LanguageSet dictLangs, String tokenizerType) throws Exception {
    boolean debug = false;

    long startEncoding = System.currentTimeMillis();

    // list of encoded entries
    ArrayList<int[]> encodedEntries = new ArrayList<int[]>(tokenizedEntries.size());;

    // Map of unique tokens across all the dictionary entries vs token's index in this map; this
    // index is used
    // while encoding entries
    Map<String, Integer> uniqueTokenVsIndexTable = new LinkedHashMap<String, Integer>();

    // Lets start dictionary entries encoding by instantiating language set encoder
    EncodeDecodeLanguageSet encodeLanguageSet = new EncodeDecodeLanguageSet(dictLangs);

    // Cache encode value for a language set; this will help to avoid redundant encoding of a
    // language set
    Map<LanguageSet, Integer> cacheEncodedLangSet = new HashMap<LanguageSet, Integer>();

    Set<Entry<String, LanguageSet>> entrySet = tokenizedEntries.entrySet();
    for (Entry<String, LanguageSet> entry : entrySet) {
      String tokenizedEntry = entry.getKey();
      LanguageSet langCodes = tokenizedEntries.get(tokenizedEntry);

      boolean caseSensitive = true;
      if (params.getDefaultCase() == DictParams.CaseSensitivityType.insensitive) {
        caseSensitive = false;
      }

      String[] tokens = StringUtils.split(tokenizedEntry, DictFile.TOKEN_DELIM);

      // Encoded entry array
      int[] encodedEntry = new int[tokens.length + 2];

      // Encoded entry array index
      int encodedEntryIndex = 0;

      // Start with language codes encoding
      Integer encodeLangSet = cacheEncodedLangSet.get(langCodes); // look into cache first
      if (null == encodeLangSet) { // if not there encode
        encodeLangSet = encodeLanguageSet.encode(langCodes);
        cacheEncodedLangSet.put(langCodes, encodeLangSet); // add to cache for further use
      }
      encodedEntry[encodedEntryIndex++] = encodeLangSet;

      // then encode case
      if (caseSensitive) {
        encodedEntry[encodedEntryIndex++] = CASE_SENSITIVE;
      } else {
        encodedEntry[encodedEntryIndex++] = CASE_INSENSITIVE;
      }

      // finally encode, the tokens from dictionary entry
      for (String token : tokens) {
        Integer index = uniqueTokenVsIndexTable.get(token);
        // new token, add to token table
        if (null == index) {
          index = uniqueTokenVsIndexTable.size();
          uniqueTokenVsIndexTable.put(token, index);
        }
        encodedEntry[encodedEntryIndex++] = index;
      }

      // Add entry to encoded entry list
      encodedEntries.add(encodedEntry);
    }

    if (debug) {
      Log.debug("Time taken to encode dictionary: %d milliseconds",
          System.currentTimeMillis() - startEncoding);
    }

    return new CompiledDictionary(params.getDictName(),
        transposeTokenTable(uniqueTokenVsIndexTable), dictLangs, encodedEntries, tokenizerType,
        params.supportLemmaMatch());
  }

  /**
   * This method returns the transpose of the given map. Transposed map will be use while decoding
   * and serializing dictionary entries.
   * 
   * @param uniqueTokenVsIndexTable map of unique tokens appear in the dictionary.
   * @return transposed map.
   */
  private Map<Integer, String> transposeTokenTable(Map<String, Integer> uniqueTokenVsIndexTable) {
    Map<Integer, String> indexVsTokenTable = new HashMap<Integer, String>();
    Set<String> keySet = uniqueTokenVsIndexTable.keySet();
    for (String token : keySet) {
      Integer index = uniqueTokenVsIndexTable.get(token);
      indexVsTokenTable.put(index, token);
    }

    return indexVsTokenTable;
  }
}
