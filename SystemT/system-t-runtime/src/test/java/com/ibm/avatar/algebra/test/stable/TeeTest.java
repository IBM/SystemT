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
package com.ibm.avatar.algebra.test.stable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.base.Tee;
import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.output.Sink;
import com.ibm.avatar.algebra.util.test.SimpleTestHarness;
import com.ibm.avatar.api.Constants;

/**
 * Unit test of the Tee operator
 * 
 */
public class TeeTest extends SimpleTestHarness {

  String[] DOCTEXTS = {"doc 1", "doc 2", "doc 3"};

  @Before
  public void setUp() throws Exception {
    super.setUp(DOCTEXTS);
  }

  @Test
  public void testTee() throws Exception {
    Tee t = new Tee(in, 2);

    Operator op1 = t.getOutput(0);
    Operator op2 = t.getOutput(1);

    // Create a sink as a temporary root so that we can initialize the
    // MemoizationTable.
    Sink tmpRoot = new Sink(new Operator[] {op1, op2});

    MemoizationTable mt = new MemoizationTable(tmpRoot);

    FieldGetter<Text> acc = op1.getOutputSchema().textAcc(Constants.DOCTEXT_COL);

    for (int i = 0; i < DOCTEXTS.length; i++) {
      mt.resetCache();

      TupleList tl1 = op1.getNext(mt);
      TupleList tl2 = op2.getNext(mt);

      assertEquals(1, tl1.size());
      assertEquals(1, tl2.size());

      Tuple innerTup1 = tl1.iterator().next();
      Tuple innerTup2 = tl2.iterator().next();

      if (innerTup1 == innerTup2) {
        System.err.printf("Tuples are exactly the same\n");
      }

      innerTup1.equals(innerTup2);

      assertEquals(innerTup1, innerTup2);

      Text doc1 = acc.getVal(innerTup1);
      assertEquals(DOCTEXTS[i], doc1.getText());
    }

    assertTrue(mt.endOfInput());
  }

}
