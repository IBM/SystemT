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
package com.ibm.avatar.algebra.util.data;

import java.util.ArrayList;

import com.ibm.avatar.algebra.datamodel.Triple;

/**
 * We create a separate class for this kind of list, so that AOGOpTree can tell it apart from a
 * normal ArrayList.
 */
public class StringTripleList extends ArrayList<Triple<String, String, String>> {

  /**
   * 
   */
  private static final long serialVersionUID = 1L;

}
