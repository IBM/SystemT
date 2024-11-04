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
import com.ibm.avatar.aql.CreateExternalViewNode;
import com.ibm.avatar.aql.ErrorLocation;
import com.ibm.avatar.aql.ImportNode;
import com.ibm.avatar.aql.NickNode;

/**
 * Catalog entry for an external view in AQL. Currently these external views are created with the
 * "create external view" statement.
 */
public class ExternalViewCatalogEntry extends AbstractRelationCatalogEntry {

  /**
   * Parse tree node for the table declaration.
   */
  private CreateExternalViewNode node = null;

  /** Schema of the external view */
  private TupleSchema viewSchema = null;

  /** External name as declared in external_name clause of 'create external view ...' statement. */
  private String externalName = null;

  /** Constructor for the entry for a external view. */
  public ExternalViewCatalogEntry(CreateExternalViewNode node) {
    super(node.getExternalViewName());
    this.node = node;
    externalName = node.getExternalName();
  }

  /**
   * Constructor to create entry for a imported external views.
   * 
   * @param qualifiedViewName fully qualified name of the imported external view
   * @param vmd meta-data for imported external view
   * @param importingNode the import view node
   */
  public ExternalViewCatalogEntry(String qualifiedViewName, ViewMetadata vmd,
      ImportNode importingNode) {
    super(qualifiedViewName);
    setImportingNode(importingNode);
    setViewSchema(vmd.getViewSchema());
    externalName = vmd.getExternalName();
  }

  /**
   * Override this method to return column names for external types.
   * 
   * @return names of output columns in this view
   */
  @Override
  public ArrayList<String> getColNames() {
    ArrayList<String> ret = new ArrayList<String>();

    /*
     * An external view catalog entry can be constructed in two ways. It can be constructed with a
     * parse tree node (when declared) or via an importing node (when declared in another AQL file
     * and imported). In the latter case, we need to get the column names from the view schema, not
     * from a parse tree node.
     */
    if (node != null) {
      // parse tree node exists, grab the column names from there
      for (NickNode nick : node.getColNames()) {
        ret.add(nick.getNickname());
      }
    } else if (viewSchema != null) {
      // no parse tree node, so this is an imported external view with a view schema
      ret = new ArrayList<String>(Arrays.asList(viewSchema.getFieldNames()));
    } else {
      // one of node or viewSchema should be defined
      throw new FatalInternalError(String
          .format("Unable to retrieve column information from external view %s.", externalName));
    }

    return ret;
  }

  @Override
  public boolean getIsExternal() {
    return true;
  }

  @Override
  public boolean getIsDetag() {
    return false;
  }

  @Override
  public boolean getIsView() {
    return false;
  }

  public void setViewSchema(TupleSchema viewSchema) {
    this.viewSchema = viewSchema;
  }

  @Override
  public TupleSchema getSchema() {
    return viewSchema;
  }

  @Override
  public CreateExternalViewNode getParseTreeNode() {
    return node;
  }

  /**
   * @return the external name as declared in external_name clause of 'create external view ...'
   *         statement.
   */
  public String getExternalName() {
    return externalName;
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
    if (null != node) {
      node.setIsOutput(whereSet, isOutput, outputName);
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
    if (null != node) {
      return node.getIsOutput();
    } else {
      return this.isOutput;
    }
  }

  /**
   * @return If this view is an output view the "output view" statement has an "as" clause, output
   *         name specified in the "as" clause.
   */
  public String getOutputName() {
    if (null != node) {
      return node.getOutputName();
    } else {
      return outputName;
    }
  }

  /**
   * @return if this view is an output view, the location in an AQL file where this view was
   *         converted to an output view; otherwise, returns null
   */
  public ErrorLocation getWhereOutputSet() {
    if (null != node) {
      return node.getWhereOutputSet();
    } else {
      return whereOutputSet;
    }
  }

  @Override
  protected AQLParseTreeNode getNode() {
    return node;
  }
}
