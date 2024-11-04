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

import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.aql.AQLParseTreeNode;
import com.ibm.avatar.aql.DetagDocNode;
import com.ibm.avatar.aql.DetagDocSpecNode;
import com.ibm.avatar.aql.ParseException;

/**
 * Catalog entry for an output of a 'detag' statement. We create an entry for each output, but they
 * all point to the same parse tree node.
 */
public class DetagCatalogEntry extends CatalogEntry {

  /**
   * The parse tree node (shared with other catalog entries) for the 'detag' statement.
   */
  private final DetagDocNode node;

  private TupleSchema detagSchema;

  /**
   * @param outputName name of the particular output that this catalog entry represents.
   * @param node the parse tree node (shared with other catalog entries) for the 'detag' statement.
   */
  protected DetagCatalogEntry(String outputName, DetagDocNode node) {
    super(outputName);
    this.node = node;
  }

  @Override
  public ArrayList<String> getColNames() throws ParseException {
    return node.getOutputCols(getName());
  }

  @Override
  public boolean getIsExternal() {
    return false;
  }

  @Override
  public boolean getIsView() {
    return false;
  }

  public DetagDocNode getParseTreeNode() {
    return node;
  }

  @Override
  public boolean getIsDetag() {
    return true;
  }

  /**
   * @return the detagSchema
   */
  public TupleSchema getDetagSchema() {
    return detagSchema;
  }

  /**
   * @param detagSchema the detagSchema to set
   */
  public void setDetagSchema(TupleSchema detagSchema) {
    this.detagSchema = detagSchema;
  }

  /**
   * @return the original unqualified name from the parse tree node.
   */
  public String getUnqualifiedName() {
    // Catalog entry in hand is for detagged view
    if (getName().contains(node.getUnqualifiedDetaggedDocName())) {
      return node.getUnqualifiedDetaggedDocName();
    }

    // If here, we are dealing with an auxiliary view catalog entry
    ArrayList<DetagDocSpecNode> entries = node.getEntries();
    for (DetagDocSpecNode detagDocSpecNode : entries) {
      String unQualAuxViewName = detagDocSpecNode.getUnqualifiedName();
      if (getName().endsWith(unQualAuxViewName)) {
        return unQualAuxViewName;
      }
    }

    // if here, we are dealing with an invalid entry
    throw new RuntimeException(String.format("Invalid catalog entry %s", getName()));
  }

  @Override
  protected AQLParseTreeNode getNode() {
    return node;
  }

}
