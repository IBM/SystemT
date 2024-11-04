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

import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.aql.catalog.Catalog;

/** Parse tree node for a direct reference to a table or column nickname. */
public class NickNode extends RValueNode {

  public static final String NODE_NAME = "Nickname";

  protected String nick;

  /** Constructor for internally-generated nodes. */
  public NickNode(String nick) {
    // set error location info
    super(NODE_NAME, null, null);

    this.nick = nick;
  }

  public NickNode(String nick, String containingFileName, Token origTok) {
    // set error location info
    super(NODE_NAME, containingFileName, origTok);

    this.nick = nick;

    // Log.debug("%s starts on line %d", this,
    // null == getOrigTok() ? -1 : getOrigTok().beginLine);
  }

  /**
   * Copy constructor.
   * 
   * @param orig parse tree node to duplicate
   */
  public NickNode(NickNode orig) {
    super(NODE_NAME, orig.containingFileName, orig.origTok);
    this.nick = orig.nick;
  }

  public String getNickname() {
    return nick;
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    printIndent(stream, indent);
    dumpStrNick(stream, nick);
  }

  /**
   * Dump the input String as a valid NickLiteral to a stream. Public because we need to use it from
   * other places, for example to dump an auto-generated alias in a SelectListItemNode, or to dump
   * the name of a function.
   * 
   * @param stream the stream to dump to
   * @param str the string object to dump as a valid NickLiteral
   */
  public static void dumpStrNick(PrintWriter stream, String str) {
    // enclose str in double quotes, if need be
    stream.print(AQLParser.quoteReservedWord(str));
  }

  @Override
  public String toString() {
    // Add some punctuation to indicate that this is a nickname
    return String.format("<%s>", nick);
  }

  @Override
  public String getColName() {
    return "A lone nickname should never have a column name";
  }

  @Override
  protected int reallyCompareTo(AQLParseTreeNode o) {
    NickNode other = (NickNode) o;
    return nick.compareTo(other.nick);
  }

  @Override
  public int hashCode() {
    return nick.hashCode();
  }

  @Override
  public Object toAOGNode(Catalog catalog) {
    throw new UnsupportedOperationException(
        "Nicknames should never occur " + "inside a scalar function call.");
  }

  @Override
  public ScalarFnCallNode asFunction() {
    throw new UnsupportedOperationException(
        "Nicknames should never occur " + "inside a select list.");
  }

  @Override
  public void qualifyReferences(Catalog catalog) {
    // No action

  }

  @Override
  public FieldType getType(Catalog c, AbstractTupleSchema schema) {
    throw new RuntimeException("Method not implemented");
  }

}
