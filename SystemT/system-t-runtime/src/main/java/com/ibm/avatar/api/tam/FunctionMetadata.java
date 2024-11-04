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

import com.ibm.avatar.aql.FuncLanguage;

/**
 * This class provides APIs to retrieve function metadata for the exported functions of a given
 * module. An instance of {@link com.ibm.avatar.api.tam.FunctionMetadata} can be obtained by
 * invoking the API: {@link com.ibm.avatar.api.tam.ModuleMetadata#getFunctionMetadata(String)}.
 *
 * @see ModuleMetadata
 */
public interface FunctionMetadata extends Serializable {
  /**
   * <p>
   * getFunctionName.
   * </p>
   *
   * @return the unqualified function name as declared by the <code>create function</code> statement
   */
  public String getFunctionName();

  /**
   * <p>
   * getParameters.
   * </p>
   *
   * @return an array of function parameters
   */
  public Param[] getParameters();

  /**
   * <p>
   * getLanguage.
   * </p>
   *
   * @return the language in which the UDF associated with the function is implemented.
   */
  public FuncLanguage getLanguage();

  /**
   * <p>
   * getReturnType.
   * </p>
   *
   * @return the return type of the function as declared by the <code>create function</code>
   *         statement
   */
  public String getReturnType();

  /**
   * Returns the argument of the <code>return ... like ...</code> clause in the original
   * <code>create function</code> statement, if applicable.
   *
   * @return the name of the input column from which the returned value of the function inherits
   *         detailed type information; or <code>null</code> if no such column was specified
   */
  public String getReturnLikeParam();

  /**
   * <p>
   * isDeterministic.
   * </p>
   *
   * @return <code>true</code>, if the function is declared as deterministic by the
   *         <code>create function</code> statement; <code>false </code> otherwise.
   */
  public boolean isDeterministic();

  /**
   * <p>
   * returnsNullOnNullInput.
   * </p>
   *
   * @return <code>true</code>, if the function is declared with
   *         <code>return null on null input</code> clause; <code>false</code> otherwise
   */
  public boolean returnsNullOnNullInput();

  /**
   * <p>
   * getExternalName.
   * </p>
   *
   * @return the string value of the <code>external_name</code> clause as declared by the
   *         <code>create function</code> statement
   */
  public String getExternalName();

  /**
   * <p>
   * isExported.
   * </p>
   *
   * @return <code>true</code>, if the function is marked as exported through the
   *         <code>export function</code> statement, <code>false</code> otherwise
   */
  public boolean isExported();

  /**
   * Returns the text of the AQL doc comment associated with this function. The text of the original
   * comment is processed as follows:
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
