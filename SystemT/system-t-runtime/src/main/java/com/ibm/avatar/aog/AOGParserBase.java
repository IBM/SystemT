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
package com.ibm.avatar.aog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.ibm.avatar.algebra.util.dict.CompiledDictionary;

/**
 * Base class for the AOG parser. We put functionality into this class so that we don't have to edit
 * the java in the JavaCC file.
 */
public abstract class AOGParserBase {

  /**
   * Make the parse tree a member as we need access to its SymbolTable from the parser
   */
  protected AOGParseTree parseTree;

  /** Name of the module parsed by the parser */
  protected String moduleName;

  /** Map of compiled dictionary objects(internal and external both) from all the modules */
  protected Map<String, CompiledDictionary> compiledDictionaries =
      new HashMap<String, CompiledDictionary>();

  /**
   * Flag indicating whether to validate external artifacts when loading the operator graph. When
   * OperatorGraph is loaded & stitched during an invocation of OperatorGraph.validateOG() method,
   * validation should not happen for External Artifacts such as External Dictionaries and External
   * Tables, because ExternalTypeInfo is unavailable at that point of time.
   */
  protected boolean validateExternalArtifacts = true;

  /**
   * Method to pass all the loaded compiled dictionary objects to parser.
   * 
   * @param compiledDictionaries map of compiled dictionary objects(internal and external both).
   */
  protected void setCompiledDictionaries(Map<String, CompiledDictionary> compiledDictionaries) {
    this.compiledDictionaries = compiledDictionaries;
  }

  /** Map of all the external tables referred in the loaded modules */
  Map<String, ArrayList<ArrayList<String>>> externalTables =
      new HashMap<String, ArrayList<ArrayList<String>>>();

  /**
   * Method to pass all the loaded external tables to parser.
   * 
   * @param externalTables map of all the external tables.
   */
  protected void setExternalTables(Map<String, ArrayList<ArrayList<String>>> externalTables) {
    this.externalTables = externalTables;
  }

  /** Placeholder for the main parser entry point in generated code. */
  public abstract AOGParseTree Input() throws ParseException;
}
