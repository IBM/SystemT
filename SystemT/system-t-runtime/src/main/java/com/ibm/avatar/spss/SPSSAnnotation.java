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
package com.ibm.avatar.spss;

import com.ibm.avatar.algebra.datamodel.Pair;

/**
 * This class models a simple annotation with begin/end offsets, and an extra field to hold
 * additional information. Used to model an instance of a TYPE extracted during the SPSS TA
 * Extraction-Typing phase, where the extra information represents the lead term (if any was
 * obtained during grouping). In AQL, such annotations are modeled using external views with the
 * following schema (match Span, lead Text).
 * 
 */
public class SPSSAnnotation {

  // Begin and end offsets of the annotation
  private Pair<Integer, Integer> span;

  // Extra information associated with the annotation
  private String info;

  public SPSSAnnotation(Integer begin, Integer end, String info) {
    this.span = new Pair<Integer, Integer>(begin, end);
    this.info = info;
  }

  public Integer getBegin() {
    return span.first;
  }

  public Integer getEnd() {
    return span.second;
  }

  public String getInfo() {
    return info;
  }

}
