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

public abstract class PatternAtomNode extends PatternExpressionNode {

  public PatternAtomNode(String containingFileName, Token origTok) {
    super(containingFileName, origTok);
    // TODO Auto-generated constructor stub
  }

  @Override
  public boolean producesSameResultAsChild() {
    return false;
  }

  @Override
  protected ArrayList<Integer> getAllGroupIDs(boolean returnGroupsOnly) {

    ArrayList<Integer> groups = new ArrayList<Integer>();
    if (returnGroupsOnly)
      groups.addAll(getReturnGroupIDs());
    else
      groups.addAll(getAllGroupIDs());

    return groups;
  }

  @Override
  protected ArrayList<Integer> getAllRepeatReturnGroupIDs() {
    return new ArrayList<Integer>();
  }

  @Override
  public boolean isOptional() {
    return false;
  }
}
