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

import java.util.HashMap;

import com.ibm.avatar.algebra.datamodel.FieldSetter;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.TextSetter;

/**
 * This class stores field setters used to populate SystemT external view tuples from an instance of
 * {@link SPSSDocument}. These setters are initialized once after the SystemT initialization
 * procedure {@link SPSSDriver#initialize()}, and repeatedly reused in
 * {@link SPSSDriver#annotate(SPSSDocument)}.
 * 
 */
public class SPSSMetadata {

  // Map from external view name to its span setter, used to get a handle on
  // the span column of the external view
  private HashMap<String, FieldSetter<Span>> spanSetters;

  // Map from external view name to its text setter, used to get a handle on
  // the text column of the external view
  HashMap<String, TextSetter> textSetters;

  SPSSMetadata() {

    spanSetters = new HashMap<String, FieldSetter<Span>>();
    textSetters = new HashMap<String, TextSetter>();
  }

  public void addSpanSetter(String viewName, FieldSetter<Span> spanSetter) {
    if (viewName != null && spanSetter != null)
      this.spanSetters.put(viewName, spanSetter);
  }

  public FieldSetter<Span> getSpanSetter(String viewName) {
    return this.spanSetters.get(viewName);
  }

  public void addTextSetter(String viewName, TextSetter textSetter) {
    if (viewName != null && textSetter != null)
      this.textSetters.put(viewName, textSetter);
  }

  public TextSetter getTextSetter(String viewName) {
    return this.textSetters.get(viewName);
  }

}
