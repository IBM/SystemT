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
package com.ibm.avatar.aql.catalog;

import java.util.ArrayList;
import java.util.Arrays;

import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.api.tam.ViewMetadata;
import com.ibm.avatar.aql.AQLParseTreeNode;
import com.ibm.avatar.aql.CreateViewNode;
import com.ibm.avatar.aql.ErrorLocation;
import com.ibm.avatar.aql.ImportNode;
import com.ibm.avatar.aql.ParseException;

/**
 * Catalog entry for an AQL view.
 */
public class ViewCatalogEntry extends AbstractRelationCatalogEntry {

  /** Constructor for entries that wrap *external* views. */
  protected ViewCatalogEntry(String viewName) {
    super(viewName);
  }

  /** Constructor for the entry for a view. */
  public ViewCatalogEntry(CreateViewNode cvn) {
    super(cvn.getViewName());
    this.cvn = cvn;
  }

  /**
   * Constructor to create entry for imported views.
   * 
   * @param qualifiedViewName qualified name of the imported view
   * @param vmd meta-data for the imported view
   * @param importingNode the 'import view' node
   */
  public ViewCatalogEntry(String qualifiedViewName, ViewMetadata vmd, ImportNode importingNode) {
    super(qualifiedViewName);
    setImportingNode(importingNode);
    setViewSchema(vmd.getViewSchema());
  }

  /**
   * Parse tree for the view declaration.
   */
  private CreateViewNode cvn = null;

  /**
   * @return the parse tree node for this view
   */
  @Override
  public CreateViewNode getParseTreeNode() {
    return cvn;
  }

  /**
   * @return true if this entry is for a view, as opposed to an external type or table function.
   */
  @Override
  public boolean getIsView() {
    return true;
  }

  /**
   * Override this method to return column names for external types.
   * 
   * @return names of output columns in this view
   */
  @Override
  public ArrayList<String> getColNames() {
    if (false == getIsView()) {
      throw new RuntimeException("This implementation of getColNames()" + " only works for views.");
    }

    if (null == viewSchema) {
      throw new FatalInternalError("getColNames() called before view schema for view '%s' set",
          getName());
    }

    return new ArrayList<String>(Arrays.asList(viewSchema.getFieldNames()));
  }

  @Override
  public boolean getIsExternal() {
    return false;
  }

  @Override
  public boolean getIsDetag() {
    return false;
  }

  /** Schema of the view */
  private TupleSchema viewSchema;

  /** Set the catalog entry view schema */
  public void setViewSchema(TupleSchema viewSchema) {
    this.viewSchema = viewSchema;
  }

  /** Does this view go to the output? */
  private boolean isOutput = false;

  /**
   * If this view is an output view the "output view" statement has an "as" clause, output name
   * specified in the "as" clause.
   */
  private String outputName = null;

  /** Location of the statement that made this view an output view. */
  private ErrorLocation whereOutputSet = null;

  /**
   * Mark this view as an output view
   * 
   * @param whereSet location in the AQL where the "output view" statement was found
   * @param isOutput
   * @param outputName optional output name, or null to use the view's fully-qualified view name
   */
  public void setIsOutput(ErrorLocation whereSet, boolean isOutput, String outputName) {

    if (null != cvn) {
      cvn.setIsOutput(whereSet, isOutput, outputName);
    } else {
      this.isOutput = isOutput;
      this.whereOutputSet = whereSet;
      this.outputName = outputName;
    }
  }

  /**
   * @return <code>true</code> if the current view is output using the "output view" statement;
   *         otherwise, <code>false </code>.
   */
  public boolean getIsOutput() {
    if (null != cvn) {
      return cvn.getIsOutput();
    } else {
      return this.isOutput;
    }
  }

  /**
   * @return If this view is an output view the "output view" statement has an "as" clause, output
   *         name specified in the "as" clause.
   */
  public String getOutputName() {
    if (null != cvn) {
      return cvn.getOutputName();
    } else {
      return this.outputName;
    }
  }

  /**
   * @return if this view is an output view, the location in an AQL file where this view was
   *         converted to an output view; otherwise, returns null
   */
  public ErrorLocation getWhereOutputSet() {
    if (null != cvn) {
      return cvn.getWhereOutputSet();
    } else {
      return this.whereOutputSet;
    }
  }

  @Override
  protected AQLParseTreeNode getNode() {
    return cvn;
  }

  @Override
  public TupleSchema getSchema() throws ParseException {
    return viewSchema;
  }
}
