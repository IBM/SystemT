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

import com.ibm.avatar.algebra.datamodel.TupleList;

/**
 * User-defined scalar function for testing record locator arguments.
 * 
 */
public class TableConsumingScalarFunc 
{

  /**
   * Main entry point to the scalar function. This function takes two lists of tuples and concatenates them into a big
   * string.
   * 
   * @param arg1 first set of tuples to merge
   * @param arg2 second set of tuples to merge
   * @return the two sets of tuples, concatenated
   */
  public String eval (TupleList arg1, TupleList arg2)
  {
    StringBuilder sb = new StringBuilder ();
    
    sb.append("Input1: ");
    sb.append(arg1.toPrettyString ());
    sb.append("\nInput2: ");
    sb.append(arg2.toPrettyString ());
    
    return sb.toString ();
  }


}
