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
package com.ibm.avatar.algebra.util.dict;

import java.util.Arrays;
import java.util.Comparator;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.SpanText;
import com.ibm.avatar.algebra.util.tokenize.BaseOffsetsList;
import com.ibm.avatar.algebra.util.tokenize.OffsetsList;

/**
 * An uber-dictionary entry with multiple subentries.
 */
class MultiEntry extends HashDictEntry {

  /** Array of different entries. */
  private HashDictEntry[] subentries = new HashDictEntry[2];

  private int numEntries;

  /** Main constructor, for the base case when you have two entries. */
  MultiEntry(HashDictEntry first, HashDictEntry second) {
    this.subentries[0] = first;
    this.subentries[1] = second;
    this.numEntries = 2;
  }

  void addEntry(HashDictEntry e) {
    // Make room first.
    if (numEntries >= subentries.length) {
      HashDictEntry[] tmp = new HashDictEntry[subentries.length * 2];
      System.arraycopy(subentries, 0, tmp, 0, numEntries);
      subentries = tmp;
    }
    subentries[numEntries++] = e;
  }

  /**
   * This version just walks through the children and tells them to mark matches themselves.
   */
  @Override
  public int markMatches(MemoizationTable mt, SpanText target, OffsetsList tokOffsets, int tokId,
      BaseOffsetsList output, boolean useTokenOffsets, boolean performLemmaMatch) {
    int nmatch = 0;
    for (int i = 0; i < numEntries; i++) {
      nmatch += subentries[i].markMatches(mt, target, tokOffsets, tokId, output, useTokenOffsets,
          performLemmaMatch);
    }

    return nmatch;
  }

  /**
   * Sort the sub-entries under this entry so that shorter matches will be produced first.
   */
  public void sortSubEntries() {
    final class cmp implements Comparator<HashDictEntry> {
      @Override
      public int compare(HashDictEntry o1, HashDictEntry o2) {
        return o1.getNumTokens() - o2.getNumTokens();
      }
    }

    // Shrink the array while we're at it.
    HashDictEntry[] newEntries = new HashDictEntry[numEntries];
    System.arraycopy(subentries, 0, newEntries, 0, numEntries);
    Arrays.sort(newEntries, new cmp());
    subentries = newEntries;
  }

  @Override
  protected int markMatches_case(MemoizationTable mt, String text, OffsetsList tokOffsets,
      int tokId, BaseOffsetsList output, boolean useTokenOffsets, int[] indices,
      boolean performLemmaMatch) {
    throw new RuntimeException("This method should never be called");
  }

  @Override
  protected int markMatches_nocase(MemoizationTable mt, String text, OffsetsList tokOffsets,
      int tokId, BaseOffsetsList output, boolean useTokenOffsets, int[] indices,
      boolean performLemmaMatch) {
    throw new RuntimeException("This method should never be called");
  }

  @Override
  protected int getNumTokens() {
    throw new RuntimeException("This method should never be called");
  }

}
