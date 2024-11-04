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

public class PatternOptionalNode extends PatternSingleChildNode {

  public PatternOptionalNode(PatternExpressionNode pattern) {
    // set error location info
    super(pattern, pattern.getContainingFileName(), pattern.getOrigTok());
  }

  @Override
  protected int reallyCompareTo(AQLParseTreeNode o) {
    PatternOptionalNode other = (PatternOptionalNode) o;

    return child.compareTo(other.child);
  }

  @Override
  public boolean producesSameResultAsChild() {
    return false;
  }

  @Override
  public void dump(PrintWriter stream, int indent) {

    child.dump(stream, indent + 1);
    stream.print("?");

  }

  @Override
  public String toString() {
    return child.toString() + "?";
  }

  @Override
  public boolean matchesEmpty() {
    return true;
  }

  @Override
  public boolean isOptional() {
    return true;
  }
}
