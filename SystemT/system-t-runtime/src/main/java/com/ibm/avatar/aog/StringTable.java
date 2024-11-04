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
package com.ibm.avatar.aog;

import java.util.HashMap;

public class StringTable {

  /**
   * List of unique string objects stored during parsing
   */
  private HashMap<String, String> stringTable = new HashMap<String, String>();

  /**
   * Concatenates all unique strings encountered during parsing. We store strings referencing this
   * object, as opposed to the original AOG input string, to make sure the latter is disposed of
   * after parsing, and keep the memory footprint low.
   */
  private StringBuilder bigString = new StringBuilder();

  /**
   * Return a unique String object for the input string referencing our internal big string, as
   * opposed to the original .
   * 
   * @param str
   * @return
   */

  public String getUniqueStr(String str) {

    if (stringTable.containsKey(str))
      // We've seen this string before
      return stringTable.get(str);
    else {

      // This is a new string
      int beginOffset = bigString.length();

      // store the new string and return a reference into the big string
      bigString.append(str);
      str = bigString.substring(beginOffset);

      stringTable.put(str, str);

      return str;
    }
  }

  public void add(StringTable table) {
    bigString.append(table.bigString.toString());
    for (String key : table.stringTable.keySet()) {
      stringTable.put(key, table.stringTable.get(key));
    }
  }

  /*
   * public String getUniqueStr(String str){ if(stringTable.containsKey(str)) // We've seen this
   * string before return stringTable.get(str); else { stringTable.put(str, str); return str; } }
   */

}
