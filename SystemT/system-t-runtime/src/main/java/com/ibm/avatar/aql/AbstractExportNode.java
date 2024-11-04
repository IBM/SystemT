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

import java.io.PrintWriter;

import com.ibm.avatar.aql.catalog.Catalog;

/**
 * Abstract base class for all export nodes
 * 
 */
public abstract class AbstractExportNode extends TopLevelParseTreeNode {
  /** Name of the element imported by this node */
  protected NickNode nodeName;

  /**
   * Module Prefix node, when the output view statement is referring to a view from different module
   */
  private NickNode modulePrefixNickNode;

  /** View name without qualification. Consumed by Eclipse Tooling Indexer */
  private NickNode unqualifiedElemNameNickNode;

  /**
   * @param origToken
   * @param nodeName
   */
  public AbstractExportNode(NickNode nodeName, String containingFileName, Token origTok) {
    // set error location info
    super(containingFileName, origTok);

    this.nodeName = nodeName;
  }

  /**
   * @return the nodeName
   */
  public NickNode getNodeName() {
    return nodeName;
  }

  /**
   * Subclasses override this method to return element types such as view, dictionary, table,
   * function
   * 
   * @return element type
   */
  protected abstract String getElementType();

  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.avatar.aql.Node#dump(java.io.PrintWriter, int)
   */
  @Override
  public void dump(PrintWriter stream, int indent) {
    printIndent(stream, indent);
    stream.printf("export %s ", getElementType());

    // escape node name if either module prefix or unqualified elemName is an AQL reserved word
    if ((modulePrefixNickNode != null
        && AQLParser.isEscapingRequired(modulePrefixNickNode.getNickname()))
        || (unqualifiedElemNameNickNode != null
            && AQLParser.isEscapingRequired(unqualifiedElemNameNickNode.getNickname()))) {
      nodeName.dump(stream, 0);
    } else {
      stream.printf("%s", nodeName.getNickname());
    }
    stream.print(";\n");
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.avatar.aql.Node#reallyCompareTo(com.ibm.avatar.aql.Node)
   */
  @Override
  protected int reallyCompareTo(AQLParseTreeNode o) {

    int val = 0;

    AbstractExportNode other = (AbstractExportNode) o;

    // compare elementType
    String elemType = getElementType();
    String otherElemType = other.getElementType();
    if (elemType == null && otherElemType != null) {
      return -1;
    }

    if (elemType != null && otherElemType == null) {
      return 1;
    }

    if (elemType != null && otherElemType != null) {
      val = elemType.compareTo(otherElemType);
      if (val != 0) {
        return val;
      }
    }

    // compare nodeName
    if (nodeName == null && other.nodeName != null) {
      return -1;
    }

    if (nodeName != null && other.nodeName == null) {
      return 1;
    }

    if (nodeName != null && other.nodeName != null) {
      val = nodeName.compareTo(other.nodeName);
      if (val != 0) {
        return val;
      }
    }

    return val;
  }

  /**
   * Returns the nick node of modulePrefix
   * 
   * @return
   */
  public NickNode getModulePrefixNickNode() {
    return modulePrefixNickNode;
  }

  /**
   * Sets the nick node for module prefix
   * 
   * @param modulePrefixNickNode
   */
  public void setModulePrefixNickNode(NickNode modulePrefixNickNode) {
    this.modulePrefixNickNode = modulePrefixNickNode;
  }

  /**
   * Returns the NickNode for the unqualified name of the element
   * 
   * @return unqualified element name nick node
   */
  public NickNode getUnqualifiedElemNameNickNode() {
    return unqualifiedElemNameNickNode;
  }

  /**
   * Sets the NickNode for the unqualified name of the element
   * 
   * @param unqualifiedElemNameNickNode
   */
  public void setUnqualifiedElemNameNickNode(NickNode unqualifiedElemNameNickNode) {
    this.unqualifiedElemNameNickNode = unqualifiedElemNameNickNode;
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.avatar.aql.Node#setState(com.ibm.avatar.aql.catalog.Catalog)
   */
  @Override
  public void setState(Catalog catalog) throws ParseException {
    // first lets qualify any unqualified names
    qualifyReferences(catalog);
  }

}
