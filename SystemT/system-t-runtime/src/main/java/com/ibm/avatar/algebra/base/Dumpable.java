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
package com.ibm.avatar.algebra.base;

import java.io.PrintStream;

import com.ibm.avatar.aog.ParseException;

/**
 * Interface for objects that can be pretty-printed to a stream.
 * 
 */
public interface Dumpable {

  /**
   * Recursively pretty-print the objects's contents to a stream.
   * 
   * @return number of sub-elements printed
   * @throws ParseException if an incompatible object is encountered.
   */
  public int dump(PrintStream stream, int indent) throws ParseException;

}
