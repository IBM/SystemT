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
package com.ibm.wcs.annotationservice.test;

import java.io.File;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ibm.wcs.annotationservice.test.util.AnnotationServiceTestHarness;
import com.ibm.wcs.annotationservice.test.util.TestConstants;

/**
 * Various test cases for the AnnotatorBundle class.
 * 
 */
public class Json4jBugTest extends AnnotationServiceTestHarness {
  static ObjectMapper mapper = new ObjectMapper();

  /**
   * Main method for invoking one test at a time.
   * 
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    Json4jBugTest t = new Json4jBugTest();
    t.setUp();

    long startMS = System.currentTimeMillis();

    t.instrumentationInfoParseSerTest();

    long endMS = System.currentTimeMillis();

    t.tearDown();

    double elapsedSec = (double) (endMS - startMS) / 1000.0;
    System.err.printf("Test took %1.3f sec.\n", elapsedSec);
  }

  @Before
  public void setUp() {

  }

  @After
  public void tearDown() {

  }

  /**
   * Basic test of a SystemT extractor
   * 
   * @throws Exception
   */
  @Test
  public void instrumentationInfoParseSerTest() throws Exception {
    startTest();

    genericTest(null);

    compareAgainstExpected(false);
  }

  /*
   * PRIVATE METHODS GO HERE
   */

  /**
   * Utility method to read a single Json document from disk and serialize it back to disk at the
   * following location:
   * 
   * <pre>
   * regression/actual/[test class name]/[test case name]/out.json
   * </pre>
   * 
   * 
   * @param docsFile .json file containing a single document, or NULL to look for a documents file
   *        customized to the needs of this particular test. Such custom files should be .json files
   *        located at:
   * 
   *        <pre>
   * src/test/resources/docs/[test class name]/[test case name].json
   *        </pre>
   */
  private void genericTest(File docsFile) throws Exception {

    String className = getClass().getSimpleName();

    // Compute the location of the documents file.
    if (null == docsFile) {
      // Caller wants us to look for a specific .json file
      docsFile = new File(TestConstants.TEST_DOCS_DIR,
          String.format("%s/%s.json", className, getCurPrefix()));
    }

    if (false == docsFile.exists()) {
      throw new Exception(String.format("Documents file %s not found", docsFile));
    }

    // Read the document as JSON Records
    @SuppressWarnings("unused")
    JsonNode jsonDoc = mapper.readTree(docsFile);

    String str =
        "Record {\"content\":\"A quick brown fox jumped over the lazy dog.  Hillary Clinton probably will not vote for Barack Obama.\"} missing expected field 'text'";

    ObjectNode jsonObj = JsonNodeFactory.instance.objectNode();
    jsonObj.put("key", str);
    // Write the result, as a single JSON file
    File outputFile = new File(getCurOutputDir(), String.format("out.json", className));
    mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, jsonObj);
  }

}
