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
import java.util.ArrayList;

import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.aql.catalog.Catalog;

/**
 * Parse tree node for an entry in a query's detagging specification list. Such an entry could be in
 * any of the following forms:
 * <ul>
 * <li>element("name1") with attribute 'name2' as name3 as name4
 * </ul>
 */
public class DetagDocSpecNode extends AbstractAQLParseTreeNode {

  /** Tag to extract */
  private final StringNode tag;

  /** Output type for annotations that mark the tag positions. */
  private final NickNode tagType;

  /** Additional attributes of the tag to pass through. */
  private final ArrayList<Pair<StringNode, NickNode>> attrsAndLabels;

  /**
   * Constructor for a list item in the form "table.column as alias"
   */
  public DetagDocSpecNode(StringNode tag, NickNode tagType,
      ArrayList<Pair<StringNode, NickNode>> attrsAndLabels, String containingFileName,
      Token origTok) {
    // set error location info
    super(containingFileName, origTok);

    this.tag = tag;
    this.tagType = tagType;
    this.attrsAndLabels = attrsAndLabels;
  }

  public StringNode getTag() {
    return this.tag;
  }

  /**
   * @return qualified nick node.
   */
  public NickNode getTagType() {
    return new NickNode(prepareQualifiedName(tagType.getNickname()),
        tagType.getContainingFileName(), tagType.getOrigTok());
  }

  /**
   * @return original name of the auxiliary detagged view declared in detag statement.
   */
  public String getUnqualifiedName() {
    return this.tagType.getNickname();
  }

  public int getNumAttrs() {
    return attrsAndLabels.size();
  }

  public StringNode getAttr(int ix) {
    return attrsAndLabels.get(ix).first;
  }

  public NickNode getAttrLabel(int ix) {
    return attrsAndLabels.get(ix).second;
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    printIndent(stream, indent);
    stream.printf(" element '%s' as ", tag.getStr());

    tagType.dump(stream, 0);

    for (int i = 0; i < attrsAndLabels.size(); i++) {
      stream.print("\n");
      printIndent(stream, indent + 1);
      stream.printf(" %s attribute '%s' as ", 0 == i ? "with" : "and",
          attrsAndLabels.get(i).first.getStr());
      attrsAndLabels.get(i).second.dump(stream, 0);
    }

  }

  @Override
  protected int reallyCompareTo(AQLParseTreeNode o) {
    DetagDocSpecNode other = (DetagDocSpecNode) o;

    int val = tag.compareTo(other.tag);
    if (val != 0) {
      return val;
    }

    val = tagType.compareTo(other.tagType);
    if (val != 0) {
      return val;
    }

    val = attrsAndLabels.size() - other.attrsAndLabels.size();
    if (val != 0) {
      return val;
    }

    for (int i = 0; i < attrsAndLabels.size(); i++) {
      val = attrsAndLabels.get(i).compareTo(other.attrsAndLabels.get(i));
      if (val != 0) {
        return val;
      }
    }

    return 0;
  }

  @Override
  public void qualifyReferences(Catalog catalog) {
    // TODO Auto-generated method stub

  }
}
