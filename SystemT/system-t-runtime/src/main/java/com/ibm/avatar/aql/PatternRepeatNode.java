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

public class PatternRepeatNode extends PatternSingleChildNode {

  /**
   * Min, max occurrences
   */
  private IntNode minOccurrences;
  private IntNode maxOccurrences;

  public PatternRepeatNode(PatternExpressionNode atom, IntNode minOcc, IntNode maxOcc)
      throws ParseException {
    // set error location info
    super(atom, atom.getContainingFileName(), atom.getOrigTok());

    this.minOccurrences = minOcc;
    this.maxOccurrences = maxOcc;
  }

  @Override
  public List<ParseException> validate(Catalog catalog) {
    List<ParseException> errors = new ArrayList<ParseException>();

    errors.addAll(super.validate(catalog));
    if (minOccurrences.getValue() > maxOccurrences.getValue())
      errors.add(AQLParserBase.makeException(getOrigTok(),
          "Invalind min and max values for repeat element '%s'", this));

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
    PatternRepeatNode other = (PatternRepeatNode) o;
    return child.compareTo(other.child);
  }

  @Override
  public boolean producesSameResultAsChild() {
    return getMinOccurrences().getValue() == 1 && getMaxOccurrences().getValue() == 1;
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    child.dump(stream, indent + 1);
    stream.printf("{%d,%d}", minOccurrences.getValue(), maxOccurrences.getValue());
  }

  @Override
  public String toString() {
    return String.format("%s{%d,%d}", child, minOccurrences.getValue(), maxOccurrences.getValue());
  }

  @Override
  public boolean matchesEmpty() {
    return minOccurrences.getValue() == 0;
  }

  @Override
  public boolean isOptional() {
    return matchesEmpty();
  }
}
