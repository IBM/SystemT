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

import java.util.TreeSet;

import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.aql.catalog.Catalog;

/**
 * Interface for parse tree nodes that encapsulate rvalues (e.g. expressions that could appear on
 * the right hand side of an assignment statement in a language like Java).
 * 
 */
public abstract class RValueNode extends AbstractAQLParseTreeNode
    implements Comparable<AQLParseTreeNode>, NodeWithRefInfo {

  /**
   * Automatically-generated column name for this particular RValue in the context of the current
   * select block.
   */
  private String autoColname;

  public RValueNode(String typename, String containingFileName, Token origTok) {
    // set error location info
    super(containingFileName, origTok);
  }

  /**
   * Override this method for subclasses that don't want a name automatically generated for them.
   * 
   * @return column name in the context of the current SELECT block that will return the rvalue, if
   *         applicable.
   */
  public String getColName() {
    return autoColname;
  }

  /**
   * Subclasses should override this method if they actually reference any columns; default
   * implementation returns an empty set!
   * 
   * @param accum set for accumulating the names of columns that this rvalue depends on
   * @param catalog pointer to the AQL catalog, for looking up function information
   * @throws ParseException if a syntax error is found
   */
  @Override
  public void getReferencedCols(TreeSet<String> accum, Catalog catalog) throws ParseException {
    // Default implementation adds nothing.
  }

  /**
   * Subclasses should override this method if they actually reference any views (or if they
   * recursively call out to another RValue that does); default implementation returns an empty set!
   * 
   * @param accum set for accumulating the names of views that this rvalue depends on via record
   *        locator arguments.
   * @param catalog pointer to the AQL catalog, for looking up function information
   * @throws ParseException if a syntax error is found while generating the set of views.
   */
  @Override
  public void getReferencedViews(TreeSet<String> accum, Catalog catalog) throws ParseException {
    // Default implementation adds nothing.
  }

  /**
   * Converts from AQL's representation of a function call tree to AOG's representation; this allows
   * us to put all the type-checking and instantiation code on the AOG side.
   * 
   * @param catalog AQL catalog for looking up any necessary metadata
   * @return the AQL parse rooted at this node, converted to AOG parse tree nodes.
   * @throws ParseException if an error in the AQL is detected during conversion
   */
  public abstract Object toAOGNode(Catalog catalog) throws ParseException;

  /**
   * Wraps this RValue as an AQL function call, for use in a select list.
   */
  public abstract ScalarFnCallNode asFunction() throws ParseException;

  /**
   * This method must be called AFTER {@link #qualifyReferences(com.ibm.avatar.aql.catalog.Catalog)}
   * 
   * @param c pointer to the AQL compiler's catalog
   * @param schema schema of tuples over which this RValue will be evaluated
   * @return the runtime type of the rvalue that this parse tree node represents
   */
  public abstract FieldType getType(Catalog c, AbstractTupleSchema schema) throws ParseException;

  /**
   * Generate a unique internal name for the result of this function call. Called from the
   * preprocessor to ensure that every element of a select list has a name that can be referenced
   * from the final projection in the view.
   * 
   * @param moduleName name of the module that contains the view that contains this RValue
   * @param viewName name of the view that contains this RValue
   * @param colName location of the RValue in the select list of the view.
   */
  public final void setAutoColumnName(String moduleName, String viewName, String colName) {
    // Start the generated name with a special character to ensure that
    // it's unlikely to conflict with an actual column name.
    this.autoColname = String.format("@@%s@@%s@@%s", moduleName, viewName, colName);
  }

  /**
   * Some internal logic that we don't fully understand makes new function nodes in
   * SelectListItemNode.asFunction(). This method allows that logic to copy over the generated
   * column name.
   */
  protected void __setAutoColumnNameDirectly(String value) {
    this.autoColname = value;
  }

}
