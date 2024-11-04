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
package com.ibm.avatar.aql.planner;

import com.ibm.avatar.aql.PredicateNode;

/**
 * Plan node for an invocation of the AdjacentJoin operator, a specialized sort-merge join for
 * FollowsTok() predicates with a token distance of zero.
 * 
 */
public class AdjacentJoinNode extends MergeJoinNode {

  public AdjacentJoinNode(PlanNode outer, PlanNode inner, PredicateNode joinpred) {
    super(outer, inner, joinpred);
  }

  @Override
  protected String getAOGOpName() {
    return "AdjacentJoin";
  }

}
