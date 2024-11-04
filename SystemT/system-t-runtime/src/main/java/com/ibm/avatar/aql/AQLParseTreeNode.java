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
package com.ibm.avatar.aql;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.List;

import com.ibm.avatar.aql.catalog.Catalog;

/**
 * Super interface for all AQL parse tree nodes.
 * 
 */
public interface AQLParseTreeNode extends Comparable<AQLParseTreeNode> {
  /**
   * @return error location information
   */
  public ErrorLocation getErrorLoc();

  public void dump(PrintStream stream, int indent) throws ParseException;

  /** Dump a parse tree to a string. */
  public String dumpToStr(int indent) throws ParseException;

  /**
   * Pretty-print the parse tree rooted at this node. Implementations of this function should NOT
   * print a trailing carriage return.
   * 
   * @param stream where to print to
   * @param indent starting indent level (in tabs)
   */
  public void dump(PrintWriter stream, int indent);

  /**
   * This method is the hook to write validation logic on the parse tree node. Subclasses have to
   * override the method to provide their own logic. If there are no errors this method will return
   * empty list.
   * 
   * @param catalog
   * @return TODO
   */
  public List<ParseException> validate(Catalog catalog);

  /**
   * Qualifies all references to views, tables and dictionaries with their fully qualified names
   * looked up from the catalog.
   * 
   * @param catalog
   * @throws ParseException
   */
  public abstract void qualifyReferences(Catalog catalog) throws ParseException;

  /**
   * @return name of the module where the original AQL statement for this parse tree node resides
   */
  public String getModuleName();

  /**
   * @param moduleName the moduleName to set
   */
  public void setModuleName(String moduleName);

  /**
   * Provides an opportunity for each node to set its state. This method is called only for the
   * "required views" that need to be compiled - i.e only for those views which are reachable from
   * output or export statements. This method is called in ParseToCatalog.java, *after* all nodes
   * are added to catalog and individual node validation is performed, but *before* any preprocessor
   * validation is invoked on the nodes.
   * 
   * @param catalog
   * @throws ParseException
   */
  public void setState(Catalog catalog) throws ParseException;

  /**
   * @return the original token for this statement, set by the constructor
   */
  public Token getOrigTok();

  /**
   * @return the containingFileName
   */
  public String getContainingFileName();

}
