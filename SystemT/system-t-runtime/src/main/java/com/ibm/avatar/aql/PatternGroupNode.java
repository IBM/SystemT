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

public class PatternGroupNode extends PatternSingleChildNode {

  public PatternGroupNode(PatternExpressionNode pattern) {
    // set error location info
    super(pattern, pattern.getContainingFileName(), pattern.getOrigTok());
  }

  @Override
  protected int reallyCompareTo(AQLParseTreeNode o) {
    PatternGroupNode other = (PatternGroupNode) o;
    return child.compareTo(other.child);
  }

  @Override
  public void dump(PrintWriter stream, int indent) {

    stream.print("(");
    child.dump(stream, indent + 1);
    stream.print(")");

  }

  @Override
  public boolean producesSameResultAsChild() {
    return true;
  }

  @Override
  public String toString() {
    if (getGroupType().equals(GroupType.NON_CAPTURING))
      return String.format("(:?%s)", child);
    else
      return String.format("(%s)", child);
  }

  @Override
  public boolean matchesEmpty() {
    return child.matchesEmpty();
  }

}
