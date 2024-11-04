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

/**
 * Various user-defined function definitions for testing support of user-defined
 * functions with float arguments or return type.
 * 
 */
public class FloatUDFs {

	/**
	 * Example user-defined function with two float arguments and a float return type.
	 * @param arg1 first argument to add
	 * @param arg2 second argument to add
	 * @return the sum of the two arguments
	 */
	public Float Add(Float arg1, Float arg2) {
		
		return arg1+arg2;
	}

	
}
