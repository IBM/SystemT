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
/**
 * 
 */
package com.ibm.avatar.algebra.datamodel;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.util.lang.LangCode;

/**
 */
public interface SpanText extends Comparable<SpanText> {

  /**
   * This method returns the textual content of this span, which is text between the span's begin
   * and end offsets in the document text or the textual content of this text.
   * 
   * @return text value
   */
  public String getText();

  /**
   * Returns the language of the text of this span.
   * 
   * @return {@link LangCode} of the text of this span
   */
  public LangCode getLanguage();

  public String getDocText();

  public Text getDocTextObj();

  public Span chomp();

  public int getLength();

  /**
   * @param mt
   * @return return the lemma string of this span or text
   */
  public String getLemma(MemoizationTable mt);

}