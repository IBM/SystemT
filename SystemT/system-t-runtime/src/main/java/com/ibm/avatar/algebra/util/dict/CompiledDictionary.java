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

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.ibm.avatar.algebra.util.dict.DictParams.CaseSensitivityType;
import com.ibm.avatar.algebra.util.lang.LanguageSet;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.aog.AOGOpTree;

/**
 * This class represents the compiled form of the dictionary. This class provides
 * getTokenizedEntries method, to decode dictionary entries and return them in a form consumable by
 * DictImpl API.<br>
 * For debugging compiled form of the dictionares, there is a method
 * getHumanReadableTokenizedEntries: which returns compiled dictionaries, entries in human readable
 * format.
 */
public class CompiledDictionary {
  /**
   * Field to store name of the compiled dictionary. For explicitly declared dictionaries (declared
   * through 'create dictionary ...' AQL statement), we use the name defined in AQL. For implicit
   * dictionaries( those referred in AQL by dictionary file name), we use file name as the
   * dictionary name.
   */
  private String declaredDictName;

  /**
   * Table containing the unique tokens that appear in the entries of this dictionary. This table is
   * local to each compiled dictionary instance.
   */
  private Map<Integer, String> entryTokenTable;

  /**
   * List of language codes against which dictionary matches should be performed.
   */
  private LanguageSet languageCodes;

  /**
   * List of encoded dictionary entries. Entries as encoded as integer array, where array elements
   * are indexes into token table.For more details on dictionary encoding, please refer comments
   * from DictFile#encodeDictionary method.
   */
  private ArrayList<int[]> encodedEntries;

  /**
   * Type of tokenizer used to compile this dictionary.
   */
  private String tokenizedBy;

  /**
   * Whether this dictionary requires lemma_match as specified in AQL
   */
  private Boolean lemmaMatch;

  /**
   * This constructor will create compiled dictionary instance from the given encoded
   * dictionary.This constructor will be consumed by the
   * {@link DictFile#compile(com.ibm.avatar.algebra.util.tokenize.Tokenizer)} API, and dictionary
   * de-serializer to create compiled dictionary instance from compiled dictionary stream.
   * 
   * @param declaredDictName name to be given to compiled dictionary.
   * @param uniqueEntryTokens list of unique tokens that appear in the dictionary entries.
   * @param dictLangs list of language codes against which dictionary matches should be performed.
   * @param encodedEntries list of encoded dictionary entries.
   * @param tokenizedBy type of tokenizer used to compile this dictionary.
   */
  public CompiledDictionary(String declaredDictName, Map<Integer, String> uniqueEntryTokens,
      LanguageSet dictLangs, ArrayList<int[]> encodedEntries, String tokenizedBy) {

    // Dictionary must at least have a name.
    if (null == declaredDictName) {
      throw new NullPointerException("No value provided for declaredDictName parameter");
    }

    // TODO: Are the other parameters allowed to be null? If not, add additional checks.

    this.declaredDictName = declaredDictName;
    this.entryTokenTable = uniqueEntryTokens;
    this.languageCodes = dictLangs;
    this.encodedEntries = encodedEntries;
    this.tokenizedBy = tokenizedBy;
    this.lemmaMatch = false;
  }

  /**
   * Constructor with lemmaMatch argument
   * 
   */
  public CompiledDictionary(String declaredDictName, Map<Integer, String> uniqueEntryTokens,
      LanguageSet dictLangs, ArrayList<int[]> encodedEntries, String tokenizedBy,
      Boolean lemmaMatch) {
    this(declaredDictName, uniqueEntryTokens, dictLangs, encodedEntries, tokenizedBy);
    this.lemmaMatch = lemmaMatch;
  }

  /**
   * Returns the name of the compiled dictionary. For explicitly declared dictionaries (declared
   * through 'create dictionary ...' AQL statement), we use the name defined in AQL. For implicit
   * dictionaries( those referred in AQL by dictionary file name), we use file name as the
   * dictionary name.
   * 
   * @return the name of the compiled dictionary.
   */
  public String getCompiledDictName() {
    return this.declaredDictName;
  }

  /**
   * This method returns the type of the tokenizer used to compile this dictionary.
   * 
   * @return the type of the tokenizer used to compile this dictionary.
   */
  public String getTokenizerType() {
    return this.tokenizedBy;
  }

  /**
   * @return lemma_match for this dictionary or not
   */
  public Boolean getLemmaMatch() {
    return this.lemmaMatch;
  }

  /**
   * Method to fetch token at the given index of token table.
   * 
   * @param index index into the token table.
   * @return token string at the given index.
   */
  public String getTokenAtIndex(int index) {
    return entryTokenTable.get(index);
  }

  /**
   * Returns language set for which this dictionary should produce match.
   * 
   * @return set of language codes.
   */
  public LanguageSet getLanguages() {
    return this.languageCodes;
  }

  /**
   * Returns iterator to encoded dictionary entries; where each element returned by iterator is an
   * integer array, representing encode entry.
   * 
   * @return iterator to encoded dictionary entries
   */
  public Iterator<int[]> getEncodedEntries() {
    return this.encodedEntries.iterator();
  }

  /**
   * This method returns the number of entries in the compiled dictionary.
   * 
   * @return the number of entries in the compiled dictionary; returns 0 if there are no entries in
   *         the dictionary.
   */
  public int getNumberOfEntries() {
    return encodedEntries.size();
  }

  /**
   * This method will decode the compiled dictionary entries, and return a map of tokenized
   * dictionary entry String vs entry matching condition; key of this map is a string, containing
   * tokens for the entry delimited by '\u2504' character. <br>
   * This method will be consumed by DictImpl constructor, to prepare hashtable to be used during
   * dictionary evaluation.
   * 
   * @return a map of tokenized dictionary entry string vs entry matching condition; key of this map
   *         is a string, containing tokens for the entry delimited by '\u2504' character.
   */
  public Map<String, PerEntryParam> getTokenizedEntries() throws Exception {
    return decodeCompiledDictionary();
  }

  /**
   * This method will return human readable form of the compiled dictionary entries. This method
   * returns a map of tokenized entry string vs textual representation of entry matching parameter.
   * Tokenized entry string contains tokens for an entry, delimited by '\u2504'.<br>
   * This method will be useful, to debug/test dictionary compilation process.
   * 
   * @return returns a map of tokenized entry string vs textual representation of entry matching
   *         parameter. Tokenized entry string contains tokens for an entry, delimited by '\u2504'
   */
  public Map<String, String> getHumanReadableTokenizedEntries() throws Exception {
    Map<String, String> retVal = new LinkedHashMap<String, String>();

    Map<String, PerEntryParam> decodeCompiledDictionary = decodeCompiledDictionary();
    Set<String> keySet = decodeCompiledDictionary.keySet();
    for (String tokenizedString : keySet) {
      PerEntryParam perEntryParam = decodeCompiledDictionary.get(tokenizedString);
      retVal.put(tokenizedString, perEntryParam.toString());
    }
    return retVal;
  }

  /**
   * This method decodes the {@link #encodedEntries} using the {@link #entryTokenTable}.
   * 
   * @return a map of tokenized dictionary entry String vs entry matching condition; key of the map
   *         is a string, containing tokens for the entry delimited by '\u2504' character.
   */
  private Map<String, PerEntryParam> decodeCompiledDictionary() throws Exception {
    Map<String, PerEntryParam> decodedDictonary = new LinkedHashMap<String, PerEntryParam>();

    // there is nothing to decode; looks like dictionary in hand is empty, return empty map
    if (encodedEntries.isEmpty()) {
      return decodedDictonary;
    }

    // create language set decoder
    EncodeDecodeLanguageSet decodeLanguageSet = new EncodeDecodeLanguageSet(this.languageCodes);
    for (int[] encodedEntry : this.encodedEntries) {

      // decode encoded language codes
      LanguageSet languageSet = decodeLanguageSet.decode(encodedEntry[0]);

      // decode encoded case sensitivity to perform match
      CaseSensitivityType matchingCase = null;
      if (encodedEntry[1] == DictFile.CASE_SENSITIVE) {
        matchingCase = CaseSensitivityType.exact;
      } else if (encodedEntry[1] == DictFile.CASE_INSENSITIVE) {
        matchingCase = CaseSensitivityType.insensitive;
      }

      // decode entry tokens
      StringBuilder entry = new StringBuilder();
      for (int i = 2; i < encodedEntry.length; i++) {
        entry.append(entryTokenTable.get(encodedEntry[i]));
        entry.append(DictFile.TOKEN_DELIM);
      }
      decodedDictonary.put(entry.substring(0, entry.length() - 1),
          new PerEntryParam(languageSet, matchingCase));
    }

    return decodedDictonary;
  }

  /**
   * Print the AOG representation of dictionary to given stream.
   * 
   * @param writer stream to which AOG should be serailized.
   */
  // TODO: Dictionary attributes are hard coded as of now; this is to make
  // AOGParserTests#graphDumpTest pass
  public void dump(PrintWriter writer) {
    try {
      // Map<String, PerEntryParam> decodeCompiledDictionary = decodeCompiledDictionary ();

      // Right now all the entries in the dictionary have the same dictionary matching parameter; so
      // lets use the
      // matching parameter from the first entry
      // Collection<PerEntryParam> entryMatchingParam = decodeCompiledDictionary.values ();

      writer.printf("CreateDict(\n");

      AOGOpTree.printIndent(writer, 1);
      writer.printf("%s => %s,\n", StringUtils.quoteStr('"', "name"),
          StringUtils.quoteStr('"', this.declaredDictName));
      AOGOpTree.printIndent(writer, 1);
      writer.printf("%s => %s,\n", StringUtils.quoteStr('"', "language"),
          StringUtils.quoteStr('"', "de,es,en,fr,it,x_unspecified"));
      AOGOpTree.printIndent(writer, 1);
      writer.printf("%s => %s,\n", StringUtils.quoteStr('"', "case"),
          StringUtils.quoteStr('"', "insensitive"));
      AOGOpTree.printIndent(writer, 1);
      writer.printf("%s => %s\n", StringUtils.quoteStr('"', "isExternal"),
          StringUtils.quoteStr('"', "false"));
      writer.print(");\n");
    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  /**
   * Utility factory method to create empty compiled dictionary instance. Empty compiled dictionary
   * represents dictionaries without entries. Recall we allow empty dictionaries: (1) Internal
   * dictionary coming from file can be empty (2) External dictionaries with allow_empty set to true
   * can be empty.
   * 
   * @param dictionaryName name for the empty compiled dictionary instance.
   * @param tokenizer tokenizer to be used to compile dictionary.
   * @return an empty compiled dictionary
   */
  public static CompiledDictionary createEmptyCompiledDictionary(String dictionaryName,
      String tokenizer) {
    if (null != tokenizer) {
      return new CompiledDictionary(dictionaryName, new HashMap<Integer, String>(), null,
          new ArrayList<int[]>(), tokenizer);
    } else {
      return new CompiledDictionary(dictionaryName, new HashMap<Integer, String>(), null,
          new ArrayList<int[]>(), null);
    }
  }

}
