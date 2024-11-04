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
import java.util.List;

import com.ibm.avatar.algebra.extract.Split;
import com.ibm.avatar.api.exceptions.FatalInternalError;
import com.ibm.avatar.aql.catalog.Catalog;

/** Parse tree node for a span-splitting extraction specification. */
public class SplitExNode extends ExtractionNode {

  /** Column that is used for determining split points in the target column. */
  private final ColNameNode splitCol;

  /** Name of column in output tuples where results go. */
  private final NickNode outputCol;

  /**
   * Flags that control whether to retain the split points in the results; using constants defined
   * in {@link Split}.
   */
  private final int splitFlags;

  public SplitExNode(ColNameNode splitCol, int splitFlags, ColNameNode targetName,
      NickNode outputCol, String containingFileName, Token origTok) throws ParseException {
    super(targetName, outputCol, containingFileName, origTok);

    this.splitCol = splitCol;
    this.splitFlags = splitFlags;
    this.outputCol = outputCol;
  }

  @Override
  public List<ParseException> validate(Catalog catalog) {
    return super.validate(catalog);
  }

  public ColNameNode getSplitCol() {
    return splitCol;
  }

  @Override
  public ColNameNode[] getRequiredCols() {
    // A Split operator needs both the target and the split points.
    return new ColNameNode[] {getTargetName(), getSplitCol()};
  }

  public int getSplitFlags() {
    return splitFlags;
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    stream.printf("split using %s\n", splitCol.getColName());

    if (0x0 != splitFlags) {
      printIndent(stream, indent + 1);
      if ((Split.RETAIN_LEFT | Split.RETAIN_RIGHT) == splitFlags) {
        stream.printf("retain both split points\n");
      } else if (Split.RETAIN_LEFT == splitFlags) {
        stream.printf("retain left split point\n");
      } else if (Split.RETAIN_RIGHT == splitFlags) {
        stream.printf("retain right split point\n");
      } else {
        throw new FatalInternalError("Don't understand split flags 0x%x", splitFlags);
      }
    }

    printIndent(stream, indent);
    stream.printf("on %s as ", super.getTargetName().getColName());
    outputCol.dump(stream, 0);
    stream.printf("\n");
  }

  @Override
  protected int reallyCompareTo(AQLParseTreeNode o) {
    SplitExNode other = (SplitExNode) o;

    int val = super.compareTarget(other);
    if (0 != val) {
      return val;
    }

    val = outputCol.compareTo(other.outputCol);
    if (0 != val) {
      return val;
    }

    val = splitCol.compareTo(other.splitCol);
    if (0 != val) {
      return val;
    }

    val = splitFlags - other.splitFlags;

    return val;
  }

  @Override
  public int getNumInputCopies() {
    return 1;
  }

  @Override
  public void qualifyReferences(Catalog catalog) {
    splitCol.qualifyReferences(catalog);
  }
}
