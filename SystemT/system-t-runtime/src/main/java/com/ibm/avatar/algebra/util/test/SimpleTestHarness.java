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
package com.ibm.avatar.algebra.util.test;

import java.util.Set;
import java.util.TreeSet;

import com.ibm.avatar.algebra.scan.DocScanStub;

/** Base class for test cases. */
public class SimpleTestHarness {
  // String[] docTexts;

  /** (shared) input 1 of the operators being tested. */
  protected DocScanStub in;

  /** Input documents, for comparison. */
  protected Set<String> inputDocs;

  /** Call this function from the JUnit setUp() function. */
  protected void setUp(String[] doctexts) {
    inputDocs = new TreeSet<String>();

    // this.docTexts = doctexts;

    for (int i = 0; i < doctexts.length; i++) {
      inputDocs.add(doctexts[i]);
    }

    in = new DocScanStub(inputDocs.iterator());
  }
}
