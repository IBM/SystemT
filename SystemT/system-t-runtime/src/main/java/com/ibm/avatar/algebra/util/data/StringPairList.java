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
package com.ibm.avatar.algebra.util.data;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.TreeMap;

import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.aql.AbstractAQLParseTreeNode;

/**
 * Utility class to represent a list of pairs of String objects. Also includes some utility methods.
 */
public class StringPairList extends ArrayList<Pair<String, String>> {

  /**
   * Need this serialization info to avoid a compiler warning.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Convert this list of pairs into a (key, value) map. Only works if all keys (first elements) are
   * unique.
   */
  public TreeMap<String, String> toMap() {

    // We use TreeMap to ensure consistent ordering.
    TreeMap<String, String> ret = new TreeMap<String, String>();

    for (Pair<String, String> p : this) {

      // Make sure that we're not overwriting anything already in the map.
      if (ret.containsKey(p.first)) {
        throw new RuntimeException(
            String.format("StringPairList contains two keys with value '%s'", p.first));
      }

      ret.put(p.first, p.second);
    }

    return ret;
  }

  /**
   * Dump this list using Perl hash syntax.
   */
  public void toPerlHash(PrintWriter stream, int indent, boolean printParens) {

    if (0 == size()) {
      // SPECIAL CASE: Empty list.
      if (printParens) {
        AbstractAQLParseTreeNode.printIndent(stream, indent);
        stream.print("()");
      }
      return;
    }

    // If we get here, the list has at least one element.
    if (printParens) {
      AbstractAQLParseTreeNode.printIndent(stream, indent);
      stream.print("(");
    }

    for (int i = 0; i < size(); i++) {
      Pair<String, String> elem = get(i);

      // System.err.printf("First is %s\n", elem.first);
      // System.err.printf("Second is %s\n", elem.second);

      AbstractAQLParseTreeNode.printIndent(stream, indent + 1);
      stream.printf("%s => %s", StringUtils.quoteStr('"', elem.first),
          StringUtils.quoteStr('"', elem.second));

      // No comma after last entry.
      if (i < size() - 1) {
        stream.print(",\n");
      } else {
        stream.print("\n");
      }
    }

    if (printParens) {
      AbstractAQLParseTreeNode.printIndent(stream, indent);
      stream.print(")");
    }
  }

  /**
   * Convenience method for adding a new pair to the list
   *
   * @param first first element of the pair
   * @param second second element of the pair
   */
  public void add(String first, String second) {
    add(new Pair<String, String>(first, second));
  }
}
