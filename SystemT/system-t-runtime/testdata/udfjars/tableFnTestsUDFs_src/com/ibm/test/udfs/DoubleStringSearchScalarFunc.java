/*******************************************************************************
* Copyright IBM
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*******************************************************************************/
package com.ibm.test.udfs;

import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;

/**
 * User-defined scalar function for testing record locator arguments.
 * 
 */
public class DoubleStringSearchScalarFunc
{

  /**
   * Main entry point to the scalar function. This takes three arguments: two spans and a table of Text or Span objects.
   * Returns true if there is an entry in the table that matches both target spans.
   * 
   * @param target1 target on which to do string searches
   * @param target2 second target on which to do string searches
   * @param stringTab table of strings (or spans) to match in the target spans' text
   * @return true if there is at least one match that covers both targets
   */
  public Boolean eval (Span target1, Span target2, TupleList stringTab)
  {
    String targetText1 = target1.getText ();
    String targetText2 = target2.getText ();

    // System.err.printf("Matching '%s' and '%s' against string table...\n",
    // targetText1, targetText2);

    AbstractTupleSchema schema = stringTab.getSchema ();

    // Just use the first column found.
    FieldGetter<Span> spanAcc = schema.spanAcc (schema.getFirstTextOrSpanCol ());

    TLIter itr = stringTab.newIterator ();
    while (itr.hasNext ()) {
      Tuple tup = itr.next ();

      // Note that we're doing pure string matches, not dictionary match, here.
      String searchStr = spanAcc.getVal (tup).getText ();

      if (targetText1.contains (searchStr) && targetText2.contains (searchStr)) {
        // System.err.printf("     ==> Match!\n");
        return true;
      }
    }
    itr.done ();

    // If we get here, we did not find a match.
    return false;
  }

}
