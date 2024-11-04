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

public class PatternAtomTokenGapNode extends PatternExpressionNode {

  /**
   * Min, max occurrences
   */
  private IntNode minOccurrences;
  private IntNode maxOccurrences;

  public PatternAtomTokenGapNode(String containingFileName, Token origTok) throws ParseException {
    // set the error location info
    super(containingFileName, origTok);
  }

  @Override
  public List<ParseException> validate(Catalog catalog) {
    List<ParseException> errors = new ArrayList<ParseException>();

    if (minOccurrences.getValue() > maxOccurrences.getValue())
      errors.add(AQLParserBase.makeException(getOrigTok(),
          "Invalid min and max values for repeat element '%s'", this));

    return errors;
  }

  public void setMinOccurrences(IntNode minOcc) {
    this.minOccurrences = minOcc;
  }

  public IntNode getMinOccurrences() {
    return minOccurrences;
  }

  public void setMaxOccurrences(IntNode maxOcc) {
    this.maxOccurrences = maxOcc;
  }

  public IntNode getMaxOccurrences() {
    return maxOccurrences;
  }

  @Override
  protected int reallyCompareTo(AQLParseTreeNode o) {
    PatternAtomTokenGapNode other = (PatternAtomTokenGapNode) o;
    int val = minOccurrences.compareTo(other.minOccurrences);
    if (0 != val) {
      return val;
    }
    return maxOccurrences.compareTo(other.maxOccurrences);
  }

  @Override
  public ArrayList<ColNameNode> getCols() {
    return new ArrayList<ColNameNode>();
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    stream.printf("<Token>{%d,%d}", minOccurrences.getValue(), maxOccurrences.getValue());
  }

  @Override
  public String toString() {
    return String.format("<Token>{%d,%d}", minOccurrences.getValue(), maxOccurrences.getValue());
  }

  /**
   * This node always matches a single token.
   */
  @Override
  public boolean matchesEmpty() {
    if (minOccurrences.getValue() == 0)
      return true;

    return false;
  }

  @Override
  protected ArrayList<Integer> getAllGroupIDs(boolean returnGroupsOnly) {
    return new ArrayList<Integer>();
  }

  @Override
  protected ArrayList<Integer> getAllRepeatReturnGroupIDs() {
    return new ArrayList<Integer>();
  }

  @Override
  public boolean producesSameResultAsChild() {
    return false;
  }

  /**
   * @return Names of pass through columns that must be projected out by this node, in the form
   *         <viewAlias>.<attributeName>
   */
  @Override
  public ArrayList<PatternPassThroughColumn> getPassThroughCols() {
    // No columns to pass through from a token gap
    return new ArrayList<PatternPassThroughColumn>();
  }

  @Override
  public void qualifyReferences(Catalog catalog) {
    // No action
  }

  @Override
  public boolean isOptional() {
    return false;
  }
}
