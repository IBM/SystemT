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

import com.ibm.avatar.algebra.util.dict.DictParams.CaseSensitivityType;
import com.ibm.avatar.algebra.util.lang.LanguageSet;

/**
 * This class encapsulates matching parameters associated with a dictionary entry<br>
 * Note: For dictionaries coming from table, there is an option to specify per entry based
 * dictionary matching parameter.
 */
public class PerEntryParam {
  LanguageSet langSet;
  CaseSensitivityType caseCond;

  /**
   * Constructor to instantiate matching parameter for a dictionary entry.
   * 
   * @param langSet language matching parameter
   * @param caseCond case matching parameter
   */
  public PerEntryParam(LanguageSet langSet, CaseSensitivityType caseCond) {
    this.langSet = langSet;
    this.caseCond = caseCond;
  }

  /**
   * Returns the language set, to be used to generate match for this entry.
   * 
   * @return the language set, to be used to generate match for this entry.
   */
  public LanguageSet getLangSet() {
    return langSet;
  }

  /**
   * Returns the case matching parameter, to be used to generate match for this entry.
   * 
   * @return the case matching parameter, to be used to generate match for this entry.
   */
  public CaseSensitivityType getCaseCond() {
    return caseCond;
  }

  /**
   * Returns textual representation of the entry matching parameters; Matching parameters are
   * encoded as follows: [param1 => 'value1', param2 => 'value2' ...]
   * 
   * @return textual representation of the entry matching parameters.
   */
  @Override
  public String toString() {
    String langSetToString = langSet.toString();

    // remove square brackets from toString representation
    String langSetCSVString = langSetToString.substring(1, langSetToString.length() - 1);

    return String.format("[case => '%s', language => '%s']", caseCond.toString(), langSetCSVString);
  }

}
