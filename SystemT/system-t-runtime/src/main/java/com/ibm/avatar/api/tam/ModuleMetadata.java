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

import java.util.List;

import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.util.tokenize.Tokenizer;
import com.ibm.avatar.api.CompileAQL;
import com.ibm.avatar.api.exceptions.InvalidModuleElementException;

/**
 * This class provides APIs to retrieve metadata of various AQL elements from the loaded
 * {@link com.ibm.avatar.api.tam.ModuleMetadata} object. An instance of this type can be obtained by
 * invoking one of the APIs in {@link com.ibm.avatar.api.tam.ModuleMetadataFactory} class. There are
 * APIs to fetch the list of AQL elements, which are either marked as <i>exported</i> through a
 * variant of <code>export</code> statement, and/or as <i>external</i> through a variant of
 * <code>create external </code> statement; all the AQL element names returned by these APIs are
 * qualified by containing module name except for the one which belongs to 'genericModule'. <br>
 * Note: Non-modular AQLs are compiled into a special module called 'genericModule'. There are
 * additional APIs per AQL element type to fetch detailed metadata based on the element name.
 * <p>
 * There is an additional API to fetch module names on which this module depends on.
 * 
 * @see CompileAQL
 */
public interface ModuleMetadata extends ITAMComponent {

  // IMPORTANT: for each new entry into metadata.xml, update the equals() method implementation in
  // ModuleMetadataImpl.java

  /**
   * Types of AQL elements that the module metadata can be queried about.
   */
  public enum ElementType {
    TABLE("table"), VIEW("view"), FUNCTION("function"), DICTIONARY("dictionary"), MODULE("module");

    private final String text;

    private ElementType(String text) {
      this.text = text;
    }

    public String getText() {
      return text;
    }
  }

  /**
   * Provides the schema of the input document as defined by the module.
   * 
   * @return input document schema
   */
  public TupleSchema getDocSchema();

  /**
   * Returns the name of the module.
   * 
   * @return the name of the module
   */
  public String getModuleName();

  /**
   * Returns list of external view name pairs, where the first element of the pair is the view name
   * as declared in an AQL <code>create external view</code> statement. For modular AQL, the view
   * name returned will be qualified with containing module name; the second argument is the
   * external name as declared in an AQL <code>create external view</code> statement's
   * <code>external_name</code> clause.
   * 
   * @return list of external view name pairs
   */
  public List<Pair<String, String>> getExternalViews();

  /**
   * Returns a list of qualified names of views marked exported via <code>export view</code>
   * statement.
   * 
   * @return a list of qualified names of views marked exported
   */
  public String[] getExportedViews();

  /**
   * Returns the list of qualified function names marked exported via <code>export function</code>
   * statement.
   * 
   * @return a list of qualified names of functions marked exported
   */
  public String[] getExportedFunctions();

  /**
   * Returns a list of names of the views marked as output view through the <code>output view</code>
   * statement. Names returned are either output view aliases created through
   * <code>output view</code> statement's 'as' clause, a view name qualified by containing module
   * name if alias is not declared, or an unqualified view name for genericModule (module compiled
   * from non-modular AQLs).
   * 
   * @return an array of output view names
   */
  public String[] getOutputViews();

  /**
   * Returns view metadata of a given view name.
   * 
   * @param viewName this name can be one of the following: an output alias, an external name (in
   *        case of external view), the fully qualified name for modular AQLs, an unqualified view
   *        name inside a genericModule(module generate for non-modular AQL code), or the view
   *        Document
   * @return view metadata object; <code>null</code>, if metadata not found for the given view name
   * @throws RuntimeException if multiple matches are found in the metadata for the given view name
   */
  public ViewMetadata getViewMetadata(String viewName);

  /**
   * Returns the list of qualified names for the external tables declared through the
   * <code>create external table</code> statement.
   * 
   * @return an array of qualified names for the declared external table
   */
  public String[] getExternalTables();

  /**
   * Returns the qualified names of the tables marked as exported through the
   * <code>export table</code> statement.
   * 
   * @return an array of qualified table names as exported by the module
   */
  public String[] getExportedTables();

  /**
   * Returns metadata for a given table name.
   * 
   * @param tableName qualified table name, if modular AQL; unqualified otherwise
   * @return table metadata object; <code>null</code>, if metadata not found for the given table
   *         name
   */
  public TableMetadata getTableMetadata(String tableName);

  /**
   * Returns a list of qualified names of the external dictionaries declared through the
   * <code>create external dictionary</code> statement.
   * 
   * @return an array of the qualified names of the declared external dictionary
   */
  public String[] getExternalDictionaries();

  /**
   * Returns a list of qualified names of dictionaries marked as exported through the
   * <code>export dictionary</code> statement.
   * 
   * @return list of qualified names of dictionaries marked as exported in the module
   */
  public String[] getExportedDictionaries();

  /**
   * Returns metadata for a given dictionary name.
   * 
   * @param dictName qualified dictionary name, if modular AQL; unqualified otherwise
   * @return dictionary metadata object;<code>null</code>, if metadata not found for the given
   *         dictionary name
   */
  public DictionaryMetadata getDictionaryMetadata(String dictName);

  /**
   * Returns metadata for a given function name.
   * 
   * @param functionName qualified function name, if modular AQL; unqualified otherwise
   * @return function metadata object; <code>null</code>, if metadata not found for the given
   *         function name
   */
  public FunctionMetadata getFunctionMetadata(String functionName);

  /**
   * Returns the time when the module was compiled
   * 
   * @return compilation time as a String equivalent of {@link java.util.Date}
   */
  public String getCompilationTime();

  /**
   * Returns the text of the AQL doc comment associated with this module. The text of the original
   * comment read from the file "module.info" in the source module directory is processed as
   * follows:
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
   * Retrieve the text of the AQL doc comment associated with the given element of the given type.
   * 
   * @param type type of element
   * @param name qualified name of the element. For elements of type
   *        {@link com.ibm.avatar.api.tam.ModuleMetadata.ElementType#VIEW},this name be either be an
   *        output alias, fully qualified name,external name (in case of external view), or
   *        unqualified name for view inside a genericModule(module generated for non-modular AQL)
   * @return string contains the AQL doc comment associated for given element name
   * @throws InvalidModuleElementException if an element of the given type with the given name does
   *         not exist
   */
  public String getComment(ElementType type, String name) throws InvalidModuleElementException;

  /**
   * Returns the host where module was compiled.
   * 
   * @return the host where module was compiled
   */
  public String getHostName();

  /**
   * Returns the user who compiled the module.
   * 
   * @return the user who compiled the module
   */
  public String getUserName();

  /**
   * Returns the product version used to compile the module.
   * 
   * @return the product version used to compile the module
   */
  public String getProductVersion();

  /**
   * Returns the list of module names that the current module depends on. Module A is considered to
   * depend on module B if it directly imports module B, or an artifact of module B. Indirect
   * (transitive) dependencies are not returned. Example: Module A imports module B, which in turn
   * imports module C. By this definition, A depends on B, and does not depend on C.
   * 
   * @return comma-separated list of dependent module names
   * @deprecated As of v2.1, replaced by {@link #getDependentModules()}
   */
  @Deprecated
  public String getDependsOn();

  /**
   * Returns a list of module names that the current module depends on. Module A is considered to
   * depend on module B if it directly imports module B, or an artifact of module B. Indirect
   * (transitive) dependencies are not returned. Example: Module A imports module B, which in turn
   * imports module C. By this definition, A depends on B, and does not depend on C.
   * 
   * @return List of module names that the current module depends on
   */
  public List<String> getDependentModules();

  /**
   * Returns the tokenizer type used during compilation of this module.
   * 
   * @return tokenizer type used for compiling this module
   */
  public String getTokenizerType();
}
