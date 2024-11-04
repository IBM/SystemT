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
package com.ibm.avatar.algebra.relational;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.base.SingleInputOperator;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.DerivedTupleSchema;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.TupleList;

/**
 * Operator that implements the LIMIT clause (the one that returns the first k rows of the result)
 * 
 */
public class Limit extends SingleInputOperator {

  int maxtup;

  public Limit(int maxtup, Operator child) {
    super(child);

    this.maxtup = maxtup;
  }

  @Override
  protected void reallyEvaluate(MemoizationTable mt, TupleList childResults) throws Exception {

    int ntup = 0;
    TLIter itr = childResults.iterator();
    while (itr.hasNext() && ntup < maxtup) {
      addResultTup(itr.next(), mt);
      ntup++;
    }
    itr.done();

  }

  @Override
  protected AbstractTupleSchema createOutputSchema() {
    AbstractTupleSchema inputSchema = getInputOp(0).getOutputSchema();

    // We'll be just directly copying tuples through, so create a derived
    // schema as the output.
    DerivedTupleSchema ret = new DerivedTupleSchema(inputSchema);

    return ret;
  }

}
