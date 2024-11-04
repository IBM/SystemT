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

import java.util.TreeSet;

import com.ibm.avatar.algebra.util.lang.LangCode;
import com.ibm.avatar.algebra.util.lang.LanguageSet;

/**
 * This class encode/decode language set for a tokenized entry into an integer and vice versa.
 * Encoding/decoding is performed against the base language set.<br>
 * For example, if the base language set is {'en','fr'}: language set {'en'} will get encoded to
 * decimal 2(binary 10),language set {'fr'} will get encoded to decimal 1(binary 01), and language
 * set {'en','fr'} will get encoded to decimal 3(binary 11). Effectively, encoder prepares the
 * binary string for the given language set against the base language set, and later return the
 * decimal value for binary string.
 * 
 */
public class EncodeDecodeLanguageSet {
  /** base language set */
  private TreeSet<LangCode> baseLangSet;

  /** base language set array */
  private LangCode[] dictlangArray;

  /**
   * Constructor to instantiate encoder for base language set.
   * 
   * @param dictlangs
   */
  public EncodeDecodeLanguageSet(LanguageSet dictlangs) throws Exception {
    // Cannot encode a set containing more than the maximum number of language codes allowed; since
    // we encode language
    // set as integer
    if (dictlangs.size() > LangCode.MAX_NUM_LANG_CODES) {
      throw new Exception(
          String.format("Cannot encode a set containing more than %d language codes",
              LangCode.MAX_NUM_LANG_CODES));
    }

    // We intentionally sort the language codes alphabetically, to become independent of given
    // LanguageSet iteration
    // order
    baseLangSet = new TreeSet<LangCode>(dictlangs);

    // prepare an array; to be used while encoding/decoding to avoid creating language set iterator
    // multiple times
    dictlangArray = new LangCode[dictlangs.size()];
    int i = 0;
    for (LangCode langCode : baseLangSet) {
      dictlangArray[i++] = langCode;
    }
  }

  /**
   * Method to encode the given language set in context of base language set.
   * 
   * @param entryLangSet language set to encode
   * @return an integer encoded language set
   */
  public int encode(LanguageSet entryLangSet) throws Exception {
    if (!(baseLangSet.containsAll(entryLangSet))) {
      throw new Exception("Dictionary entry language set should be a subset of base language set");
    }

    StringBuilder binaryRepresentation = new StringBuilder();

    // prepare binary string for the language set to encode
    for (LangCode langCodeString : dictlangArray) {
      if (entryLangSet.contains(langCodeString)) {
        binaryRepresentation.append("1");
      } else {
        binaryRepresentation.append("0");
      }
    }

    // Binary string to integer
    return Integer.parseInt(binaryRepresentation.toString(), 2);
  }

  /**
   * Method to decode the given integer in context of base language set.
   * 
   * @param encodedlanguage
   * @return decoded language set
   */
  public LanguageSet decode(int encodedlanguage) throws Exception {

    // To binary string
    String entryBinaryString = Integer.toString(encodedlanguage, 2);

    // Length of binary string should be equal to length of the base language set.
    int diff = this.dictlangArray.length - entryBinaryString.length();

    // If length of binary string for the given integer to decode is less than size of base language
    // set, then pad '0'
    // to binary string.
    StringBuilder pad = new StringBuilder();
    if (diff > 0) {
      for (int i = 0; i < diff; i++) {
        pad.append('0');
      }
    } // If length of binary string for the given integer to decode is greater than size of base
      // language set; this is an
      // error, and it is not possible to decode this in context of base language set
    else if (diff < 0) {
      throw new Exception("Error decoding the given value in context of base language set");
    }

    entryBinaryString = pad.append(entryBinaryString).toString();

    int len = entryBinaryString.length();
    LangCode[] matchedLangs = new LangCode[len];
    int matchNum = 0;
    for (int i = diff; i < len; i++) {
      if (entryBinaryString.charAt(i) == '1') {
        matchedLangs[matchNum++] = dictlangArray[i];
      }
    }

    LangCode[] langs = new LangCode[matchNum];
    System.arraycopy(matchedLangs, 0, langs, 0, matchNum);

    return LanguageSet.create(langs, new DictMemoization());
  }
}
