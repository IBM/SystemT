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
package com.ibm.avatar.algebra.util.compress;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

/** Generic entry in a Huffman code tree. */
public class HuffmanEntry<T> implements Comparable<HuffmanEntry<T>> {

  HuffmanEntry<T> leftChild = null;
  HuffmanEntry<T> rightChild = null;

  /**
   * The character (or char sequence) represented (if this is a leaf node)
   */
  T c;

  /** Cumulative count for this node and its child subtree */
  int count;

  /** Constructor for non-leaf nodes. */
  public HuffmanEntry(HuffmanEntry<T> leftChild, HuffmanEntry<T> rightChild) {
    this.leftChild = leftChild;
    this.rightChild = rightChild;
    this.c = null;
    this.count = leftChild.count + rightChild.count;
  }

  /** Constructor for leaf nodes. */
  public HuffmanEntry(T c, int count) {
    this.c = c;
    this.count = count;
  }

  /** Comparison function for sorting by count. */
  // @Override
  @Override
  public int compareTo(HuffmanEntry<T> o) {
    return this.count - o.count;
  }

  protected ArrayList<T> getChars() {
    ArrayList<T> ret = new ArrayList<T>();
    if (null != leftChild) {
      ret.addAll(leftChild.getChars());
    }
    if (null != rightChild) {
      ret.addAll(rightChild.getChars());
    }
    if (isLeaf()) {
      ret.add(c);
    }
    return ret;
  }

  @Override
  public String toString() {
    if (isLeaf()) {
      return String.format("'%c' (count %d)", c, count);
    } else {
      return String.format("%s (count %d)", getChars(), count);
    }
  }

  public boolean isLeaf() {
    return (null != c);
  }

  /** Recursively extract all the codes for the nodes of this tree. */
  public TreeMap<T, HuffmanCode> computeCodes() {
    TreeMap<T, HuffmanCode> ret = new TreeMap<T, HuffmanCode>();

    reallyComputeCodes(ret, new boolean[256], 1);

    return ret;
  }

  /**
   * Method that does the heavy lifting for {@link #computeCodes()}
   * 
   * @param codeMap output code table
   * @param curBits current set of leading bits at this point in the tree traversal
   */
  protected void reallyComputeCodes(Map<T, HuffmanCode> codeMap, boolean[] bits, int curDepth) {

    boolean debug = false;

    if (debug) {
      for (int i = 0; i < curDepth; i++) {
        System.err.print("  ");
      }
      System.err.printf("%s\n", this);
    }

    if (isLeaf()) {
      // BASE CASE: Leaf node; generate table entry.
      codeMap.put(c, new HuffmanCode(bits, curDepth));
      // END BASE CASE
    } else {
      // RECURSIVE CASE: Interior node
      bits[curDepth] = false;
      leftChild.reallyComputeCodes(codeMap, bits, curDepth + 1);

      bits[curDepth] = true;
      rightChild.reallyComputeCodes(codeMap, bits, curDepth + 1);
      // END RECURSIVE CASE
    }
  }

}
