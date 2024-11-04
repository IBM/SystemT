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
package com.ibm.test.udfs;

import com.ibm.avatar.api.exceptions.TextAnalyticsException;

/**
 * Various function definitions for testing support of 3VL for user-defined predicates
 * 
 * 
 */
public class NullHandlingUDFs {

	/**
	 * Example predicate to show 3-value logic.
	 * @param str a string to test
	 * @return A string denoting the logical value of whether the input string contains the letter 'T'
	 */
	public String containsT(String str) {
	  
	  // if the string is null, it's unknown whether or not the string contains the letter 'T'
	  if (str == null) return "Unknown";
		
		for (int i = 0; i < str.length(); i++) {
			char c = str.charAt(i);
			if ('t' == c || 'T' == c) {
				return "True";
			}
		}
		
		return "False";
	}
	
	 /**
   * Example user-defined predicate that does not return null on null input
   * 
   * Implements Kleene logic's implication operation.
   * 
   * @param a first proposition truth value
   *        b second proposition truth value
   * @return true if A is FALSE or B is TRUE
   *         false if A is TRUE and B is FALSE
   *         null otherwise
   */
  public Boolean implies (Boolean a, Boolean b) {

    if ((a == Boolean.FALSE) || (b == Boolean.TRUE)) { return Boolean.TRUE; }
    if ((a == Boolean.TRUE) && (b == Boolean.FALSE)) { return Boolean.FALSE; }
    
    return null;
  }
	
  /**
   * User-defined predicate that throws an exception when called.
   * 
   * Useful for testing whether evaluation is short-circuited -- if the UDF gets evaluated, it will throw an exception
   */
  public Boolean throwException (Boolean a) throws TextAnalyticsException {
    throw new TextAnalyticsException("Exception thrown.");
  }
	

}
