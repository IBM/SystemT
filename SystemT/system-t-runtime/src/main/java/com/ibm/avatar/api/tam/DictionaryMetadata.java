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
package com.ibm.avatar.api.tam;

import java.io.Serializable;

import com.ibm.avatar.algebra.util.dict.DictParams.CaseSensitivityType;

/**
 * This class provides APIs to retrieve dictionary metadata for the exported and the external
 * dictionaries of a given module. An instance of {@link com.ibm.avatar.api.tam.DictionaryMetadata}
 * can be obtained by the API:
 * {@link com.ibm.avatar.api.tam.ModuleMetadata#getDictionaryMetadata(String)}.
 * 
 * @see ModuleMetadata
 */
public interface DictionaryMetadata extends Serializable {
  /**
   * Returns the unqualified dictionary name as declared in <code>create dictionary</code> or
   * <code>create external dictionary</code> statement.
   * 
   * @return the unqualified dictionary name as declared in <code>create dictionary</code> or
   *         <code>create external dictionary</code> statement
   */
  public String getDictName();

  /**
   * Returns the list of language codes used for tokenizing this dictionary.
   * 
   * @return comma-separated list of language code
   */
  public String getLanguages();

  /**
   * Returns whether dictionary matches should be case-sensitive or not. <br/>
   * For example, if the case type is <code>exact</code>, "Miller" would not match "miller". <br/>
   * 
   * @return One of the following case types: exact, insensitive
   */
  public CaseSensitivityType getCaseType();

  /**
   * Specifies whether the dictionary is internal (defined by <code>create dictionary</code>) <br/>
   * or external (defined by <code>create external dictionary</code>)
   * 
   * @return <code>true</code>, if the dictionary is external
   */
  public boolean isExternal();

  /**
   * Specifies whether the dictionary is exported.
   * 
   * @return <code>true</code>, if the dictionary is exported.
   */
  public boolean isExported();

  /**
   * Specifies whether empty external dictionaries are allowed. This method will throw an exception
   * if called for a non-external dictionary.
   * 
   * @return <code>true</code>, if dictionary contents are optional <br>
   *         <code>false</code>, if not <br>
   *         <code>null</code>, if undefined (the required flag was set)
   * @exception UnsupportedOperationException if invoked on the internal dictionary metadata
   *            instance
   * @deprecated As of v3.0.1, use {@link #isRequired()} instead
   */
  @Deprecated
  public Boolean isAllowEmpty();

  /**
   * Specifies whether dictionary filename needs to be specified. This method will throw an
   * exception if called for a non-external dictionary.
   * 
   * @return <code>true</code>, if a dictionary filename needs to be specified <br>
   *         <code>false</code>, if not <br>
   *         <code>null</code>, if undefined (the deprecated allow_empty flag was set)
   * @exception UnsupportedOperationException if invoked on the internal dictionary metadata
   *            instance
   */
  public Boolean isRequired();

  /**
   * Specifies whether the dictionary is matched on lemma (i.e., it was defined using the
   * lemma_match setting).
   * 
   * @return <code>true</code>, if the dictionary is matched on lemma <br>
   *         <code>false</code>, otherwise
   */
  public boolean isLemmaMatch();

  /**
   * Returns the text of the AQL doc comment associated with this dictionary. The text of the
   * original comment is processed as follows:
   * <ul>
   * <li>Leading comment separator (/**) and trailing comment separator (*&#47;) are removed</li>
   * <li>Leading asterisk (*) characters on each line are discarded; blanks and tabs preceding the
   * initial asterisk (*) characters are also discarded. If you omit the leading asterisk on a line,
   * the leading white space is not removed.</li>
   * <li>Carriage return characters (\r) are removed.</li>
   * </ul>
   * 
   * @return the text of the AQL doc comment with leading (begin AQL doc comment) and trailing (end
   *         AQL doc comment) separators discarded, leading consecutive whitespace followed by an
   *         asterisk on each line of the comment also discarded, carriage return characters (\r)
   *         also discarded
   */
  public String getComment();

}
