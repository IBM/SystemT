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
import java.util.regex.Pattern;

import com.ibm.avatar.aql.catalog.Catalog;

public class PatternAtomRegexNode extends PatternAtomNode {

  private final RegexNode regex;

  public PatternAtomRegexNode(RegexNode regex) throws ParseException {
    // set the error location info
    super(regex.getContainingFileName(), regex.getOrigTok());

    this.regex = regex;
  }

  @Override
  public List<ParseException> validate(Catalog catalog) {
    List<ParseException> errors = new ArrayList<ParseException>();

    // Validate the regular expression
    try {
      Pattern.compile(regex.getRegexStr());
    } catch (Exception e) {
      errors.add(AQLParserBase.makeException(regex.getOrigTok(), "Invalid regular expression '%s'",
          regex.getRegexStr()));
    }

    return errors;
  }

  public RegexNode getRegex() {
    return regex;
  }

  @Override
  protected int reallyCompareTo(AQLParseTreeNode o) {
    PatternAtomRegexNode other = (PatternAtomRegexNode) o;
    return regex.compareTo(other.regex);
  }

  @Override
  public ArrayList<ColNameNode> getCols() {
    return new ArrayList<ColNameNode>();
  }

  @Override
  public void dump(PrintWriter stream, int indent) {
    regex.dump(stream, indent + 1);
  }

  @Override
  public String toString() {
    return regex.toString();
  }

  @Override
  public boolean matchesEmpty() {
    return Pattern.matches(regex.getRegexStr(), "");
  }

  /**
   * @return Names of pass through columns that must be projected out by this node, in the form
   *         <viewAlias>.<attributeName>
   */
  @Override
  public ArrayList<PatternPassThroughColumn> getPassThroughCols() {
    // No columns to pass through from a regex node
    return new ArrayList<PatternPassThroughColumn>();
  }

  @Override
  public void qualifyReferences(Catalog catalog) {
    // No action
  }
}
