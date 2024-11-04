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
import java.util.List;

import com.ibm.avatar.aql.catalog.Catalog;

/** Parse tree node for a block extraction specification. */
public class BlockExNode extends ExtractionNode {

  /** Range of block sizes (in number of constituent spans) */
  IntNode minSize, maxSize;

  /** Range of separations between the elements of the block. */
  IntNode minSep, maxSep;

  /**
   * True if {@link #minSep} and {@link #maxSep} are token distances; false if they are character
   * distances.
   */
  boolean sepIsTokens;

  /** Name of column in output tuples where results go. */
  private final NickNode outputCol;

  public BlockExNode(IntNode minSize, IntNode maxSize, IntNode minSep, IntNode maxSep,
      boolean sepIsTokens, ColNameNode targetName, NickNode outputCol, String containingFileName,
      Token origTok) throws ParseException {
    super(targetName, outputCol, containingFileName, origTok);

    this.minSize = minSize;
    this.maxSize = maxSize;
    this.minSep = minSep;
    this.maxSep = maxSep;
    this.sepIsTokens = sepIsTokens;
    this.outputCol = outputCol;

  }

  @Override
  public List<ParseException> validate(Catalog catalog) {

    List<ParseException> errors = new ArrayList<ParseException>();

    // Validate inputs.
    if (minSep.getValue() != 0) {
      errors.add(
          AQLParserBase.makeException("Minimum block distance must be zero", minSep.getOrigTok()));
    }

    return errors;
  }

  /**
   * Print this parse tree node back out as valid AQL. extract blocks with length between 2 and 5
   * and separation between 0 and 100 characters on P.fullNumber as numbers from Phone P;
   */
  @Override
  public void dump(PrintWriter stream, int indent) {
    stream.printf("blocks\n");

    printIndent(stream, indent);
    stream.printf("with count between %d and %d\n", minSize.getValue(), maxSize.getValue());

    printIndent(stream, indent);
    stream.printf("and separation between %d and %d %s\n", minSep.getValue(), maxSep.getValue(),
        sepIsTokens ? "tokens" : "characters");

    printIndent(stream, indent);
    stream.printf("on %s as ", super.getTargetName().getColName());
    outputCol.dump(stream, 0);
    stream.printf("\n");
  }

  @Override
  protected int reallyCompareTo(AQLParseTreeNode o) {
    BlockExNode other = (BlockExNode) o;

    int val = super.compareTarget(other);
    if (0 != val) {
      return val;
    }

    val = outputCol.compareTo(other.outputCol);
    if (0 != val) {
      return val;
    }

    val = minSize.compareTo(other.minSize);
    if (0 != val) {
      return val;
    }

    val = maxSize.compareTo(other.maxSize);
    if (0 != val) {
      return val;
    }

    val = minSep.compareTo(other.minSep);
    if (0 != val) {
      return val;
    }

    val = maxSep.compareTo(other.maxSep);
    if (0 != val) {
      return val;
    }

    if (sepIsTokens && (false == other.sepIsTokens)) {
      return 1;
    }
    if ((false == sepIsTokens) && other.sepIsTokens) {
      return -1;
    }

    return 0;
  }

  @Override
  public int getNumInputCopies() {
    return 1;
  }

  public boolean getUseTokenDist() {
    return sepIsTokens;
  }

  public IntNode getMaxSep() {
    return maxSep;
  }

  public IntNode getMinSep() {
    return minSep;
  }

  public IntNode getMinSize() {
    return minSize;
  }

  public IntNode getMaxSize() {
    return maxSize;
  }

  @Override
  public void qualifyReferences(Catalog catalog) {
    // No action
  }
}
