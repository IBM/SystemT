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

import java.io.File;

import org.junit.Test;

import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;

/**
 * Verifies ExplainModule APIs
 * 
 */
public class ExplainModuleTests extends RuntimeTestHarness {
  @Test
  public void dumpToFileTest() throws Exception {
    startTest();
    compileModule("phone");
    File outputDir = getCurOutputDir();
    File tamFile = new File(outputDir, "phone.tam");
    explainModule(tamFile, new File(outputDir, "metadata.xml"), new File(outputDir, "explain.txt"));

    System.err.printf("Comparing the output metadata against the expected metadata...\n");

    // Compare the metadata files
    compareMetadataAgainstExpected("metadata.xml");

    // Compare the operator graphs
    compareAgainstExpected("explain.txt", 0, -1);
  }
}
