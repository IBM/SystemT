/*******************************************************************************
* Copyright IBM
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*******************************************************************************/
package com.ibm.biginsights.textanalytics.udftest;

/**
 * Utilities to manipulate Java String
 * 
 */
public class StringUtils
{
  /**
   * Simple utility that converts given string to lower case
   * 
   * @param str input string
   * @return lower case representation of the input string.
   */
  public static String toLowerCase (String str)
  {
    if (str != null) { return str.toLowerCase (); }

    return "";
  }
}
