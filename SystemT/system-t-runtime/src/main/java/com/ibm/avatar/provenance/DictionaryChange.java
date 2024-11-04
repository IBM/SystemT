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
package com.ibm.avatar.provenance;

import java.util.ArrayList;

// Dictionary Low Level Change
public class DictionaryChange extends LowLevelChange {

  private String dictionary; // the dictionary to change
  private String word; // the word to add or remove
  private boolean isAddWord; // whether we are adding a word to the dictionary

  public DictionaryChange(ArrayList<Integer> removedNegatives, ArrayList<Integer> casualities,
      boolean isRemove, String changeString, double gain, double penalty, double fWeight) {
    super(removedNegatives, casualities, isRemove, changeString, gain, penalty, fWeight);
  }

  public String getDictionary() {
    return dictionary;
  }

  public void setDictionary(String dictionary) {
    this.dictionary = dictionary;
  }

  public String getWord() {
    return word;
  }

  public void setWord(String word) {
    this.word = word;
  }

  public boolean isAddWord() {
    return isAddWord;
  }

  public void setAddWord(boolean isAddWord) {
    this.isAddWord = isAddWord;
  }

}
