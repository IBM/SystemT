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

import java.util.ArrayList;
import java.util.Random;
import java.util.TreeSet;

import com.ibm.avatar.aql.ConsolidateClauseNode;
import com.ibm.avatar.aql.FromListItemNode;
import com.ibm.avatar.aql.FromListNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.PredicateNode;
import com.ibm.avatar.aql.RValueNode;
import com.ibm.avatar.aql.SelectListNode;

/**
 * Planner that chooses a random join order and applies merge join whenever possible. Used for
 * generating a histogram of query running times across the set of all possible join orders.
 * 
 */
public class RandomMergePlanner extends NaiveMergePlanner {

  /** Random number generator used for choosing random plans. */
  private static final Random rng = new Random();

  public static void setRandomSeed(long seed) {
    rng.setSeed(seed);
  }

  @Override
  protected PlanNode reallyComputeJoin(FromListNode fromList, SelectListNode selectList, int offset,
      TreeSet<PredicateNode> availPreds, ArrayList<RValueNode> groupByValues,
      ConsolidateClauseNode consolidateClause) throws ParseException {

    // Only intercept the first recursive call; other calls go through to
    // the superclass implementation.
    if (fromList.size() - 1 != offset) {
      return super.reallyComputeJoin(fromList, selectList, offset, availPreds, groupByValues,
          consolidateClause);
    } else {

      // The superclass uses the order of the from list as a join order,
      // so we just reorder the items in the from list to get a random
      // join
      // order.
      Object[] order = fromList.toArray();
      permute(order);
      ArrayList<FromListItemNode> tmp = new ArrayList<FromListItemNode>();
      for (int i = 0; i < order.length; i++) {
        tmp.add((FromListItemNode) order[i]);
      }

      // System.err.printf("--> New from list: %s\n", tmp);

      FromListNode artificialFromList =
          new FromListNode(tmp, fromList.getContainingFileName(), fromList.getOrigTok());
      return super.reallyComputeJoin(artificialFromList, selectList, offset, availPreds,
          groupByValues, consolidateClause);
    }
  }

  /** Permute the elements of an array in place. */
  private void permute(Object[] arr) {

    boolean debug = false;

    if (debug) {
      ArrayList<Object> tmp = new ArrayList<Object>();
      for (Object object : arr) {
        tmp.add(object);
      }
      System.err.printf("Before permute: %s\n", tmp);
    }

    for (int i = 0; i < arr.length; i++) {
      // We walk through the array from left to right. At each point, the
      // indices up to i are the permuted values, while the indices
      // greater than i hold the ones we haven't permuted yet.
      int numleft = arr.length - i;

      // Choose an element from the "todo" set to add to our output.
      int nextix;
      synchronized (rng) {
        nextix = i + rng.nextInt(numleft);
      }

      // Swap the chosen element to the output.
      Object tmp = arr[i];
      arr[i] = arr[nextix];
      arr[nextix] = tmp;
    }

    if (debug) {
      ArrayList<Object> tmp = new ArrayList<Object>();
      for (Object object : arr) {
        tmp.add(object);
      }
      System.err.printf("After permute: %s\n", tmp);
    }
  }
}
