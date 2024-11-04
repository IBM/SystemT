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

import org.junit.Test;

import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.ScalarList;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.Text;

/**
 * Junit tests for ScalarList.java
 * 
 */
public class ScalarListTests {

  /**
   * Verifies if ScalarList's fieldType is not null and is of a specified type. Test case for defect
   */
  @Test
  @SuppressWarnings("unchecked")
  public void fieldTypeTest() {
    // TEST1 : Integer scalar list
    ScalarList<Integer> intScalarList = ScalarList.makeScalarListFromType(FieldType.INT_TYPE);
    genericTest(intScalarList, FieldType.INT_TYPE, "Field type is not of integer type");
    assertTrue(intScalarList.getScalarType().getIsIntegerType());

    // TEST2 : Float scalar list
    ScalarList<Float> floatScalarList = ScalarList.makeScalarListFromType(FieldType.FLOAT_TYPE);
    genericTest(floatScalarList, FieldType.FLOAT_TYPE, "Field type is not of float type");
    assertTrue(floatScalarList.getScalarType().getIsFloatType());

    // TEST3 : Text scalar list
    FieldType textType = FieldType.TEXT_TYPE;
    ScalarList<Text> txtScalarList = ScalarList.makeScalarListFromType(textType);
    genericTest(txtScalarList, textType, "Field type is not of text type");
    assertTrue(txtScalarList.getScalarType().getIsText());

    // TEST4 : Span scalar list
    FieldType spanType = FieldType.SPAN_TYPE;
    ScalarList<Span> spanScalarList = ScalarList.makeScalarListFromType(spanType);
    genericTest(spanScalarList, spanType, "Field type is not of span type");
    assertTrue(spanScalarList.getScalarType().getIsSpan());

    // // TEST5 : String scalar list
    // ScalarList<String> stringScalarList = ScalarList.makeScalarListFromType
    // (FieldType.STRING_TYPE);
    // genericTest (stringScalarList, FieldType.STRING_TYPE, "Field type is not of string type");
    // assertTrue (stringScalarList.getScalarType ().getIsStringType ());
  }

  private void genericTest(ScalarList<?> list, FieldType expectedFieldType, String errorMessage) {
    assertTrue("Filed type is null", list.getScalarType() != null);
    assertEquals(errorMessage, expectedFieldType, list.getScalarType());
  }
}
