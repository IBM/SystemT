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

import com.ibm.avatar.algebra.datamodel.Span;

/**
 * Various user-defined function definitions for testing support of user-defined
 * predicates.
 * 
 * 
 */
public class BoolUDFs {

	/**
	 * Example user-defined unary predicate.
	 * @param str a string to test
	 * @return true if the string does not contain the letter 'E'
	 */
	public Boolean containsNoE(String str) {
		
		for (int i = 0; i < str.length(); i++) {
			char c = str.charAt(i);
			if ('e' == c || 'E' == c) {
				return false;
			}
		}
		
		return true;
	}
	
	
	/**
	 * Example user-defined binary predicate.
	 * @param s1 lefthand span
	 * @param s2 righthand span
	 * @return true if s2 starts exactly 10 characters after s1
	 */
	public Boolean tenCharsApart(Span s1, Span s2) {
		
		// Make sure spans are over the same text.
		if (false == (s1.getDocTextObj().equals(s2.getDocTextObj()))) {
			return false;
		}
		
		int s1end = s1.getEnd();
		int s2begin = s2.getBegin();
		
		if (10 == s2begin - s1end) {
			return true;
		} else {
			return false;
		}
	}
	
}
