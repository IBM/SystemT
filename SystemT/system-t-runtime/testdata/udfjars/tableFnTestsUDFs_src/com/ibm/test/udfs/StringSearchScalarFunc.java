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
public class StringSearchScalarFunc
{

  /**
   * Main entry point to the scalar function. This takes two arguments: A span and a table of Text or Span objects.
   * Looks for instances of the strings in the table inside the target span.
   * 
   * @param target target on which to do string searches
   * @param stringTab table of strings (or spans) to match in the target span's text
   * @return true if there is at least one match
   */
  public Boolean eval (Span target, TupleList stringTab)
  {
    String targetText = target.getText ();

    AbstractTupleSchema schema = stringTab.getSchema ();

    // Just use the first column found.
    FieldGetter<Span> spanAcc = schema.spanAcc (schema.getFirstTextOrSpanCol ());

    TLIter itr = stringTab.newIterator ();
    while (itr.hasNext ()) {
      Tuple tup = itr.next ();

      // Note that we're doing pure string matches, not dictionary match, here.
      String searchStr = spanAcc.getVal (tup).getText ();
      if (targetText.contains (searchStr)) { return true; }
    }
    itr.done ();

    // If we get here, we did not find a match.
    return false;
  }

}
