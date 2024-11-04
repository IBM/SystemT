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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;

import com.ibm.avatar.algebra.util.lang.LangCode;
import com.ibm.avatar.algebra.util.lang.LanguageSet;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.algebra.util.string.VersionString;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.exceptions.IncompatibleCompiledDictException;
import com.ibm.avatar.api.exceptions.InvalidDictionaryFileFormatException;

/**
 * This class serializes/de-serializes, the compiled dictionary in text format.
 * 
 */
public class TextSerializer implements DictionarySerializer {
  // Header properties
  private static final String TOKENIZER_TYPE = "TOKENIZER_TYPE";
  private static final String NAME = "NAME";
  private static final String VERSION = "VERSION";
  private static final String LEMMA_MATCH = "LEMMA_MATCH";

  // Character set used for serializing dictionaries
  private static final String CHARSET = "UTF-8";

  // Host machine line separator
  // CHANGED BY FRED: Compiled dictionary format should be the same on all operating systems. The
  // machine doing the
  // compiling is often not the same machine as the one that loads the dictionary! Use \n as the
  // line
  // separator everywhere.
  // Original code:
  // private static String lineSeparator = System.getProperty ("line.separator");
  private static String lineSeparator = "\n";
  // END CHANGE

  // delimiter for encoded entry
  private static final char ENCODED_ENTRY_DELIMITER = ' ';

  @Override
  public void serialize(CompiledDictionary compiledDictionary, OutputStream streamToSerialize)
      throws IOException {
    Writer out = new BufferedWriter(new OutputStreamWriter(streamToSerialize, CHARSET));

    // Start by serializing header
    serializeHeader(compiledDictionary, out);

    // Header and body are separated by two carriage return
    out.write(lineSeparator);
    out.write(lineSeparator);

    // Body serialization starts; there can be dictionaries without body
    if (compiledDictionary.getNumberOfEntries() > 0) {
      serializeBody(compiledDictionary, out);
    }

    out.flush();
  }

  private void serializeHeader(CompiledDictionary compiledDictionary, Writer out)
      throws IOException {
    out.write(prepareHeaderProperty(VERSION, Constants.PRODUCT_VERSION));
    out.write(lineSeparator);
    out.write(prepareHeaderProperty(NAME, compiledDictionary.getCompiledDictName()));
    out.write(lineSeparator);
    out.write(
        prepareHeaderProperty(TOKENIZER_TYPE, compiledDictionary.getTokenizerType().toString()));
    out.write(lineSeparator);

    // added at version 3.0.1
    out.write(prepareHeaderProperty(LEMMA_MATCH, compiledDictionary.getLemmaMatch().toString()));
    out.write(lineSeparator);
  }

  private void serializeBody(CompiledDictionary compiledDictionary, Writer out) throws IOException {
    // prepare the list of unique token from encoded entries
    LinkedHashSet<String> tokenSet = new LinkedHashSet<String>();
    Iterator<int[]> encodedEntries = compiledDictionary.getEncodedEntries();
    while (encodedEntries.hasNext()) {
      int[] encodedEntry = encodedEntries.next();
      // Tokens are encoded from third element onwards; first and second entry encodes *language
      // set* and *case*
      // matching parameter for the entry
      for (int i = 2; i < encodedEntry.length; i++) {
        tokenSet.add(compiledDictionary.getTokenAtIndex(encodedEntry[i]));
      }
    }

    // Section#1: Serialize entry token table
    Iterator<String> entryTokens = tokenSet.iterator();
    while (entryTokens.hasNext()) {
      String token = entryTokens.next();
      out.write(token);
      out.write(lineSeparator);
    }

    // Sections separated by carriage return
    out.write(lineSeparator);

    // Section#2: Serialize language code table
    Iterator<LangCode> languages = compiledDictionary.getLanguages().iterator();
    while (languages.hasNext()) {
      String languageCode = LangCode.langCodeToStr(languages.next());
      out.write(languageCode);
      out.write(lineSeparator);
    }

    // Sections separated by carriage return
    out.write(lineSeparator);

    // Section#3: Serialize encoded entries, one entry per line
    encodedEntries = compiledDictionary.getEncodedEntries();
    while (encodedEntries.hasNext()) {
      int[] encodedEntry = encodedEntries.next();
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < encodedEntry.length; i++) {
        sb.append(encodedEntry[i]);
        sb.append(ENCODED_ENTRY_DELIMITER);
      }
      out.write(sb.substring(0, sb.length() - 1));
      out.write(lineSeparator);
    }
  }

  /**
   * This method returns header key/value pair.
   * 
   * @param property header property name
   * @param value header property value
   * @return returns header key/value pair
   */

  private String prepareHeaderProperty(String property, String value) {
    return property + "=" + value;
  }

  @Override
  public CompiledDictionary deSerialize(InputStream dictionaryStream)
      throws IOException, Exception {
    BufferedReader reader = new BufferedReader(new InputStreamReader(dictionaryStream, CHARSET));

    String currentLine;

    // Prepare header string
    StringBuilder headerString = new StringBuilder();
    while ((currentLine = reader.readLine()) != null && currentLine.length() > 0) {
      headerString.append(currentLine);
      headerString.append(lineSeparator);
    }

    // Load header properties from header string
    Properties headers = new Properties();
    headers.load(new StringReader(headerString.toString()));

    String dictionaryName = headers.getProperty(NAME);
    // could not read name, return exception
    if (null == dictionaryName || dictionaryName.equals(""))
      throw new InvalidDictionaryFileFormatException(
          "Compiled dictionary is missing required property '%s' in header", NAME);

    VersionString compilerVersion = null;

    // If we cannot read the version, return exception with dictionary name (set higher in the
    // stack)
    try {
      compilerVersion = new VersionString(headers.getProperty(VERSION));
    } catch (IllegalArgumentException e) {
      throw new IncompatibleCompiledDictException(dictionaryName, null, null);
    }

    // versions in the future from the current version are incompatible --
    // if so, throw an exception with name and version (and path will be set higher in the stack)
    if (new VersionString(Constants.PRODUCT_VERSION).compareTo(compilerVersion) < 0)
      throw new IncompatibleCompiledDictException(dictionaryName, compilerVersion.get(), null);

    // The tokenizer doesn't match
    String tokenizerType = headers.getProperty(TOKENIZER_TYPE);

    if (null == tokenizerType || tokenizerType.equals(""))
      throw new InvalidDictionaryFileFormatException("Null tokenizer type in dictionary '%s'",
          dictionaryName);

    Boolean bLemmaMatch = Boolean.valueOf(headers.getProperty(LEMMA_MATCH, "FALSE"));

    // Recall header and body are separated by two carriage return; one of them is consumed by
    // header string preparation
    // loop, so lets consume the second one and then start de-serializing body
    reader.readLine();

    // Read body
    // Section#1 Read entry token
    Map<Integer, String> tokenTable = new HashMap<Integer, String>();
    int index = 0;
    while ((currentLine = reader.readLine()) != null && currentLine.length() > 0) {
      tokenTable.put(index++, currentLine);
    }

    // tokenz.size() > 0 implies that dictionary is not empty. Recall we allow empty dictionaries:
    // dictionary coming
    // from file, and external dictionary with allow_empty set to true
    if (tokenTable.size() > 0) {
      // Section#2 Read language codes
      StringBuilder languages = new StringBuilder();
      while ((currentLine = reader.readLine()) != null && currentLine.length() > 0) {
        languages.append(currentLine);
        languages.append(Constants.COMMA);
      }

      // Section#3 Encoded entry
      ArrayList<int[]> encodedEntries = new ArrayList<int[]>();
      while ((currentLine = reader.readLine()) != null && currentLine.length() > 0) {
        encodedEntries.add(parseEncodedEntries(currentLine));
      }
      return new CompiledDictionary(dictionaryName, tokenTable,
          LanguageSet.create(languages.substring(0, languages.length() - 1), new DictMemoization()),
          encodedEntries, tokenizerType, bLemmaMatch);
    } else {// Control coming here implies empty dictionary
      // return new CompiledDictionary (dictionaryName, new HashMap<Integer, String> (), null, new
      // ArrayList<int[]> (),
      // TokenizerType.getTokenizerType (tokenizerType));
      return CompiledDictionary.createEmptyCompiledDictionary(dictionaryName, tokenizerType);
    }
  }

  private int[] parseEncodedEntries(String currentEntry) {
    String[] index = StringUtils.split(currentEntry, ENCODED_ENTRY_DELIMITER);
    int entryLength = index.length;

    int[] encodedEntry = new int[entryLength];
    for (int i = 0; i < entryLength; i++) {
      encodedEntry[i] = Integer.parseInt(index[i]);
    }

    return encodedEntry;
  }
}
