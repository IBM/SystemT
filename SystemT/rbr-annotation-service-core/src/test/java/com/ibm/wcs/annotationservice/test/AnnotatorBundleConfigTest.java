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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.wcs.annotationservice.models.json.AnnotatorBundleConfig;
import com.ibm.wcs.annotationservice.test.util.AnnotationServiceTestHarness;
import com.ibm.wcs.annotationservice.test.util.TestConstants;

/**
 * Various test cases for the AnnotatorBundle class.
 * 
 */
public class AnnotatorBundleConfigTest extends AnnotationServiceTestHarness {
  static ObjectMapper mapper = new ObjectMapper();

  /**
   * Main method for invoking one test at a time.
   * 
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    AnnotatorBundleConfigTest t = new AnnotatorBundleConfigTest();
    t.setUp();

    long startMS = System.currentTimeMillis();

    t.systemTTest();

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
   * Basic test to check {@link AnnotatorBundleConfig.equals()) works for the configuration of an
   * SystemT annotator.
   * 
   * @throws Exception
   */
  @Test
  public void systemTTest() throws Exception {
    startTest();

    genericEqualityTest(null);
  }

  /**
   * 
   * /* PRIVATE METHODS GO HERE
   */

  /**
   * Utility method to instantiates two AnnotatorBundleConfig objects from the same underlying JSON
   * file and checks for equality.
   * 
   * @param annotatorCfgFile .json file containing an annotator bundle configuration specification,
   *        or NULL to look for a config file customized to the needs of this particular test. Such
   *        custom files should be .json files located at:
   * 
   *        <pre>
   * src/test/resources/configs/[test class name]/[test case name].json
   *        </pre>
   */
  private void genericEqualityTest(File annotatorCfgFile) throws Exception {

    String className = getClass().getSimpleName();

    // Compute the location of the annotation config file.
    if (null == annotatorCfgFile) {
      // Caller wants us to look for a specific file
      annotatorCfgFile = new File(TestConstants.TEST_CONFIGS_DIR,
          String.format("%s/%s.json", className, getCurPrefix()));
    }

    if (false == annotatorCfgFile.exists()) {
      throw new Exception(
          String.format("Annotator bundle config file %s not found", annotatorCfgFile));
    }

    // Read the document and config file as JSON Records

    // Instantiate two Annotator Bundle Configs
    AnnotatorBundleConfig cfg1 = mapper.readValue(annotatorCfgFile, AnnotatorBundleConfig.class);
    AnnotatorBundleConfig cfg2 = mapper.readValue(annotatorCfgFile, AnnotatorBundleConfig.class);

    Assert.assertNotNull("Expected that first configuration is not null, but it is null", cfg1);
    Assert.assertNotNull("Expected that second configuration is not null, but it is null", cfg2);
    // Check that the two objects are equal
    Assert.assertTrue("Expected that the two configs are equal, but they are not",
        cfg1.equals(cfg2));
  }

}
