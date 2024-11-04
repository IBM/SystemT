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

/**
 * This class provides APIs to retrieve table metadata for the exported and the external tables of a
 * given module. An instance of {@link com.ibm.avatar.api.tam.TableMetadata} can be obtained by the
 * API: {@link com.ibm.avatar.api.tam.ModuleMetadata#getTableMetadata(String)}.
 * 
 * @see ModuleMetadata
 */
public interface TableMetadata extends Serializable {
  /**
   * Returns the unqualified name of the table as declared by the <code>create table</code> or
   * <code>create external table</code> statement.
   * 
   * @return the unqualified name of the table as declared by the <code>create table</code> or
   *         <code>create external table</code> statement
   */
  public String getTableName();

  /**
   * Returns the text of the AQL doc comment associated with this table. The text of the original
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
   * Returns the schema of the table as declared by the <code>create table</code> or
   * <code>create external table</code> statement.
   * 
   * @return the schema of the table as declared by the <code>create table</code> or
   *         <code>create external table</code> statement
   */
  public TupleSchema getTableSchema();

  /**
   * Specifies whether empty external tables are allowed. This method will throw an exception if
   * called for a non-external table.
   * 
   * @return <code>true</code>, if table contents are optional <br>
   *         <code>false</code>, if not <br>
   *         <code>null</code>, if undefined (the required flag was set)
   * @exception UnsupportedOperationException if invoked on the internal table metadata instance
   * @deprecated As of v3.0.1, use {@link #isRequired()} instead
   */
  @Deprecated
  public Boolean isAllowEmpty();

  /**
   * Specifies whether table filename needs to be specified. This method will throw an exception if
   * called for a non-external table.
   * 
   * @return <code>true</code>, if a table filename needs to be specified <br>
   *         <code>false</code>, if not <br>
   *         <code>null</code>, if undefined (the deprecated allow_empty flag was set)
   * @exception UnsupportedOperationException if invoked on the internal table metadata instance
   */
  public Boolean isRequired();

  /**
   * Returns <code>true</code>, if the table is marked as exported through the
   * <code>export table</code> statement; <code>false</code> otherwise.
   * 
   * @return <code>true</code>, if the table is marked as exported through the
   *         <code>export table</code> statement, <code>false</code> otherwise
   */
  public boolean isExported();

  /**
   * Returns <code>true</code>, if the table is declared through the
   * <code>create external table</code> statement, <code>false</code> otherwise.
   * 
   * @return <code>true</code>, if the table is declared through the
   *         <code>create external table</code> statement, <code>false</code> otherwise
   */
  public boolean isExternal();
}
