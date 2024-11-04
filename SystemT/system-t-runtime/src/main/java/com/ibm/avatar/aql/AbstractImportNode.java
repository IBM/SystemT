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
 * Abstract base class for all import node statements.
 * 
 */

public abstract class AbstractImportNode extends TopLevelParseTreeNode implements ImportNode {
  /** Name, in the context of the source module, of the element imported by this node */
  protected NickNode nodeName;

  /** Name of the module from where the element is imported */
  protected NickNode fromModule;

  /** Optional alias of the imported element */
  protected NickNode alias;

  /**
   * Main constructor. Note that this constructor does not handle the alias parameter.
   * 
   * @param origToken AQL parser token, for error reporting
   * @param nodeName Name, in the context of the source module, of the element imported by this node
   * @param moduleName Name of the module from where the element is imported
   */
  public AbstractImportNode(NickNode nodeName, NickNode fromModule, String containingFileName,
      Token origTok) {
    // set error location info
    super(containingFileName, origTok);

    this.nodeName = nodeName;
    this.fromModule = fromModule;
  }

  /**
   * @return alias specified in the import statement, or null if no alias was present
   */
  public NickNode getAlias() {
    return alias;
  }

  /**
   * @param alias the alias to set
   */
  public void setAlias(NickNode alias) {
    this.alias = alias;
  }

  /**
   * @return Name, in the context of the source module, of the element imported by this node
   */
  public NickNode getNodeName() {
    return nodeName;
  }

  /**
   * @return Name of the module from where the element is imported
   */
  public NickNode getFromModule() {
    return fromModule;
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.avatar.aql.Node#dump(java.io.PrintWriter, int)
   */
  @Override
  public void dump(PrintWriter stream, int indent) {
    printIndent(stream, indent);
    stream.printf("import %s ", getElementType());
    nodeName.dump(stream, 0);
    stream.printf(" from module ");
    fromModule.dump(stream, 0);
    if (alias != null) {
      stream.print(String.format(" as "));
      alias.dump(stream, 0);
      stream.print("\n");
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

    AbstractImportNode other = (AbstractImportNode) o;

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

    // compare fromModule
    if (fromModule == null && other.fromModule != null) {
      return -1;
    }

    if (fromModule != null && other.fromModule == null) {
      return 1;
    }

    if (fromModule != null && other.fromModule != null) {
      val = fromModule.compareTo(other.fromModule);
      if (val != 0) {
        return val;
      }
    }

    // compare alias
    if (alias == null && other.alias != null) {
      return -1;
    }

    if (alias != null && other.alias == null) {
      return 1;
    }

    if (alias != null && other.alias != null) {
      val = alias.compareTo(other.alias);
      if (val != 0) {
        return val;
      }
    }

    return val;
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
   * @see com.ibm.avatar.aql.Node#qualifyReferences(com.ibm.avatar.aql.catalog.Catalog)
   */
  @Override
  public void qualifyReferences(Catalog catalog) {
    // No action
  }

}
