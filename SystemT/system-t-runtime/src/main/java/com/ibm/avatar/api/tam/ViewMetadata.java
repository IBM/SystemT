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

import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.aql.planner.CostRecord;

/**
 * This class provides APIs to retrieve view metadata of the exported and the output views of a
 * given module. An instance of {@link com.ibm.avatar.api.tam.ViewMetadata} can be obtained by the
 * API: {@link com.ibm.avatar.api.tam.ModuleMetadata#getViewMetadata(String)}.
 *
 * @see ModuleMetadata
 */
public interface ViewMetadata extends Serializable {
  /**
   * Returns the unqualified view name as declared by the <code>create view</code> or
   * <code>create external view</code> statement.
   *
   * @return the unqualified view name as declared in <code>create view</code> or
   *         <code>create external view</code> statement
   */
  public String getViewName();

  /**
   * Returns the text of the AQL doc comment associated with this view. The text of the original
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

  /**
   * Returns the schema of the view.
   *
   * @return the schema of the view
   */
  public TupleSchema getViewSchema();

  /**
   * Returns <code>true</code>, if the view is marked as an output view through the
   * <code>output view</code> statement, <code>false</code> otherwise.
   *
   * @return <code>true</code>, if the view is marked as an output view through the
   *         <code>output view</code> statement, <code>false</code> otherwise
   */
  public boolean isOutputView();

  /**
   * Returns <code>true</code>, if the view is marked as exported using the <code>export view</code>
   * statement, <code>false</code> otherwise.
   *
   * @return <code>true</code>, if the view is marked as exported using the <code>export view</code>
   *         statement, <code>false</code> otherwise
   */
  public boolean isExported();

  /**
   * Returns <code>true</code>, only if the view is declared through the
   * <code>create external view</code> statement, <code>false </code> otherwise.
   *
   * @return <code>true</code>, only if the view is declared through the
   *         <code>create external view</code> statement, <code>false </code> otherwise
   */
  public boolean isExternal();

  /**
   * Returns the cost record associated with this view. This is consumed by the optimizer to compute
   * the cost of views which depends on this view.
   *
   * @return the cost record associated with this view
   */
  public CostRecord getCostRecord();

  /**
   * Returns the name of the module where this view is declared.
   *
   * @return name of module to which this view belongs
   */
  public String getModuleName();

  /**
   * Returns the alias name declared through the <code>output view</code> statement's
   * <code>as</code> alias clause; <code>null</code> if the alias is not declared.
   *
   * @return the alias name declared through the 'output view</code> statement <code>as</code> alias
   *         clause; <code>null</code> if the alias is not declared.
   */
  public String getOutputAlias();

  /**
   * Returns the external name of the view as declared by <code>create external view</code>
   * statement's <code>external_name</code> clause. For non-external views this method returns
   * <code>null</code>.
   *
   * @return the external name of the view as declared by <code>create external view</code>
   *         statement's <code>external_name</code> clause. For non-external views this method
   *         returns <code>null</code>.
   */
  public String getExternalName();
}
