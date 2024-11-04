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

import java.util.ArrayList;

/**
 * Base class for nodes that represent the part of an extract statement that specifies what kind of
 * extraction to do.
 */
public abstract class ExtractionNode extends AbstractAQLParseTreeNode {

  /** Column names for the outputs of the extraction. */
  private final ArrayList<NickNode> outputCols;

  public ArrayList<NickNode> getOutputCols() {
    return outputCols;
  }

  /** Name of the target column in the input. */
  private final ColNameNode targetName;

  protected ExtractionNode(ColNameNode targetName, ArrayList<NickNode> outputCols,
      String containingFileName, Token origTok) {
    // set error location info
    super(containingFileName, origTok);

    this.outputCols = outputCols;
    this.targetName = targetName;
  }

  /** Constructor for an extraction that always has exactly one output column. */
  protected ExtractionNode(ColNameNode targetName, NickNode outputCol, String containingFileName,
      Token origTok) {
    // set error location info
    super(containingFileName, origTok);

    this.outputCols = new ArrayList<NickNode>();
    outputCols.add(outputCol);
    this.targetName = targetName;
  }

  public ColNameNode getTargetName() {
    return targetName;
  }

  /**
   * Extraction types that require multiple columns as input should override this method.
   * 
   * @return all input columns that are required to run this extraction
   */
  public ColNameNode[] getRequiredCols() {
    return new ColNameNode[] {targetName};
  }

  /**
   * @return the number of copies of the input that this extraction will require (if, for example,
   *         it involves evaluating multiple regular expressions)
   */
  public abstract int getNumInputCopies();

  /**
   * Compare the input and output column names of another extraction spec with this one's
   */
  protected int compareTarget(ExtractionNode other) {
    int val = outputCols.size() - other.outputCols.size();
    if (0 != val) {
      return val;
    }
    for (int i = 0; i < outputCols.size(); i++) {
      val = outputCols.get(i).compareTo(other.outputCols.get(i));
      if (0 != val) {
        return val;
      }
    }
    val = targetName.compareTo(other.targetName);
    return val;
  }
}
