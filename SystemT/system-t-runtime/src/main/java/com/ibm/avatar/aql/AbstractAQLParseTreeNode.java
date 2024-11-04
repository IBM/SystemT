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

import java.io.CharArrayWriter;
import java.io.File;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.aql.tam.ModuleUtils;

/**
 * Abstract base class for an AvatarSQL parse tree node.
 * 
 */
public abstract class AbstractAQLParseTreeNode implements AQLParseTreeNode {

  /**
   * Name of the module that this parse tree node belongs to.
   */
  protected String moduleName;

  /** Token for the statement associated with this node */
  protected Token origTok;

  /** AQL file name containing the statement associated with the node */
  protected String containingFileName;

  /** Constructor that stores error location information */
  public AbstractAQLParseTreeNode(String containingFileName, Token origTok) {
    this.containingFileName = containingFileName;
    this.origTok = origTok;
  }

  /** Copy constructor for use by copy constructors in subclasses. */
  protected AbstractAQLParseTreeNode(AbstractAQLParseTreeNode orig) {
    // Assume tokens are immutable
    origTok = orig.origTok;
    containingFileName = orig.containingFileName;
    moduleName = orig.moduleName;
  }

  /**
   * @return error location information
   */
  @Override
  public ErrorLocation getErrorLoc() {
    return new ErrorLocation(new File(containingFileName), origTok);
  }

  /** Adapter for {@link #dump(PrintWriter,int)} */
  @Override
  public void dump(PrintStream stream, int indent) throws ParseException {
    stream.append(dumpToStr(indent));
  }

  /** Dump a parse tree to a string. */
  @Override
  public String dumpToStr(int indent) throws ParseException {
    // Dump to a buffer, then send the buffer to a stream.
    CharArrayWriter cw = new CharArrayWriter();
    PrintWriter pw = new PrintWriter(cw);
    dump(pw, indent);
    pw.close();
    return cw.toString();
  }

  /**
   * Convenience function for subclasses' implementations of dump(); Prints out the spaces that
   * correspond to the indicated indent level.
   */
  public static void printIndent(PrintWriter stream, int indent) {
    for (int i = 0; i < indent; i++) {
      stream.print("  ");
    }
  }

  /**
   * Pull a string constant out of the AvatarSQLParserConstants data structure.
   */
  public static String getConst(int index) {
    // Operator names are quoted in the AOGParserConstants data structure.
    String quotedOpName = AQLParserConstants.tokenImage[index];

    // Remove quotes.
    return quotedOpName.substring(1, quotedOpName.length() - 1);
  }

  @Override
  public int compareTo(AQLParseTreeNode o) {
    if (null == o)
      return -1;

    // If different classes, order by class name.
    int val = getClass().getName().compareTo(o.getClass().getName());
    if (val != 0) {
      return val;
    } else {
      // Otherwise, defer to class-specific comparison function.
      return reallyCompareTo(o);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof AQLParseTreeNode) {
      return (0 == compareTo((AQLParseTreeNode) o));
    } else {
      return false;
    }
  }

  /** Utility function for comparing lists of nodes. */
  @SuppressWarnings({"all"})
  protected static int compareNodeLists(List l1, List l2) {
    if (l1 == null && l2 == null) {
      return 0;
    }

    // null always loses in comparison
    if (l1 == null) {
      return -1;
    }
    if (l2 == null) {
      return 1;
    }

    int val = l1.size() - l2.size();
    if (val != 0) {
      return val;
    }

    // If we get here, lists are of the same size. Break the tie by
    // comparing list elements.
    for (int i = 0; i < l1.size(); i++) {
      Comparable l1cmp = (Comparable) l1.get(i);
      Comparable l2cmp = (Comparable) l2.get(i);
      val = l1cmp.compareTo(l2cmp);
      if (val != 0) {
        return val;
      }
    }
    return val;
  }

  /**
   * Class-specific comparison function.
   * 
   * @param o instance of the SAME class.
   * @return same as {@link Comparable#compareTo(Object)}
   */
  protected abstract int reallyCompareTo(AQLParseTreeNode o);

  /**
   * This method is the hook to write validation logic on the parse tree node. Subclasses have to
   * override the method to provide their own logic. If there are no errors this method will return
   * empty list.
   * 
   * @param catalog
   * @return TODO
   */
  @Override
  public List<ParseException> validate(Catalog catalog) {
    return new ArrayList<ParseException>();
  }

  /**
   * @return name of the module where the original AQL statement for this parse tree node resides
   */
  @Override
  public String getModuleName() {
    return moduleName;
  }

  /**
   * @param moduleName the moduleName to set
   */
  @Override
  public void setModuleName(String moduleName) {
    this.moduleName = moduleName;
  }

  /**
   * Returns the fully qualified name of the given element
   * 
   * @param elemName Name of the element whose fully qualified name is requested
   * @return fully qualified name of the given element
   */
  protected String prepareQualifiedName(String elemName) {
    return ModuleUtils.prepareQualifiedName(this.moduleName, elemName);
  }

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
  @Override
  public void setState(Catalog catalog) throws ParseException {
    // empty implementation
  }

  /**
   * @return the original token for this statement, set by the constructor
   */
  @Override
  public Token getOrigTok() {
    return origTok;
  }

  /**
   * This method should never be called. Instead, use the constructor to set the original token.
   * 
   * @param the original token for this statement
   */
  @Deprecated
  protected void setOrigTok(Token origTok) {
    this.origTok = origTok;
  }

  /**
   * @return the containingFileName
   */
  @Override
  public String getContainingFileName() {
    return containingFileName;
  }

  /**
   * This method should never be called. Instead, use the constructor to set the containing file
   * name.
   * 
   * @param containingFilename the containingFileName to set
   */
  @Deprecated
  public void setContainingFileName(String containingFileName) {
    this.containingFileName = containingFileName;
  }
}
