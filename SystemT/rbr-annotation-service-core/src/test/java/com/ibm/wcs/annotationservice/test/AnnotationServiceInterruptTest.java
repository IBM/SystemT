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
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.After;
import org.junit.Before;
// import org.junit.Test;

import com.ibm.avatar.logging.Log;
import com.ibm.wcs.annotationservice.AnnotationServiceConstants;
import com.ibm.wcs.annotationservice.ExecuteParams;
import com.ibm.wcs.annotationservice.exceptions.AnnotationServiceException;
import com.ibm.wcs.annotationservice.models.json.AnnotatorBundleConfig;
import com.ibm.wcs.annotationservice.test.util.AnnotationServiceTestHarness;
import com.ibm.wcs.annotationservice.test.util.TestConstants;

/**
 * Test for the Interrupt functionality of the Annotation Service. <br>
 * This class should contain exactly one single test for the interrupt functionality. If the test
 * exists in a class with multiple tests, the Junit framework and the Maven surefire plugin do not
 * wait for the executor service to return causing the test to fail (in that case, the test passes
 * only when run in standalone mode). <br>
 * FIXME: Need to figure out how to run such test properly in Junit and Maven. <br>
 * 
 */
public class AnnotationServiceInterruptTest extends AnnotationServiceTestHarness {

  private ExecutorService executorService;

  /**
   * Main method for invoking one test at a time.
   * 
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    AnnotationServiceInterruptTest t = new AnnotationServiceInterruptTest();
    t.setUp();

    long startMS = System.currentTimeMillis();

    t.systemTInterruptTest();

    long endMS = System.currentTimeMillis();

    t.tearDown();

    double elapsedSec = (double) (endMS - startMS) / 1000.0;
    System.err.printf("Test took %1.3f sec.\n", elapsedSec);
  }

  @Before
  public void setUp() {

    executorService = Executors.newSingleThreadExecutor();

    try {
      ExecuteParams execParams = new ExecuteParams(AnnotationServiceConstants.NO_TIMEOUT, null,
          null, AnnotatorBundleConfig.ContentType.PLAIN.getValue(), null);
      setExecuteParams(execParams);
    } catch (AnnotationServiceException e) {
      throw new RuntimeException(e);
    }
  }

  @After
  public void tearDown() {

    executorService.shutdown();

  }

  /**
   * Test for the interrupt function of the SystemT implementation
   * 
   * @throws Exception
   */
  // Comment out because it fails on Travis (exception is not generated and serialized. Re-enable
  // when issue #786 is resolved
  // @Test
  public void systemTInterruptTest() throws Exception {
    startTest();

    // Request timeout of 1 ms
    long timeoutMS = 1;
    setExecuteParams(new ExecuteParams(timeoutMS, null, null, null, null));

    final File DOCS_FILE = new File(TestConstants.DUMPS_DIR, "simpleDoc.json");
    final File CFG_FILE = new File(TestConstants.TEST_CONFIGS_DIR, "common/systemTPerson_cfg.json");

    genericAnnotationServiceTest(DOCS_FILE, CFG_FILE);

    compareAgainstExpected(true);
  }

  /*
   * PRIVATE METHODS GO HERE
   */

}
