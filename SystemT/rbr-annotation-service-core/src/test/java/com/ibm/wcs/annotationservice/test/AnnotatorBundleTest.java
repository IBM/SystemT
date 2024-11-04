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

import com.ibm.wcs.annotationservice.AnnotationServiceConstants;
import com.ibm.wcs.annotationservice.ExecuteParams;
import com.ibm.wcs.annotationservice.exceptions.AnnotationServiceException;
import com.ibm.wcs.annotationservice.models.json.AnnotatorBundleConfig;
import com.ibm.wcs.annotationservice.test.util.AnnotationServiceTestHarness;
import com.ibm.wcs.annotationservice.test.util.TestConstants;

/**
 * Various test cases for the AnnotatorBundle class.
 * 
 */
public class AnnotatorBundleTest extends AnnotationServiceTestHarness {

  /**
   * Main method for invoking one test at a time.
   * 
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    AnnotatorBundleTest t = new AnnotatorBundleTest();
    t.setUp();

    long startMS = System.currentTimeMillis();

    t.simpleSystemTTest01();

    long endMS = System.currentTimeMillis();

    t.tearDown();

    double elapsedSec = (double) (endMS - startMS) / 1000.0;
    System.err.printf("Test took %1.3f sec.\n", elapsedSec);
  }

  @Before
  public void setUp() {

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

  }

  /**
   * Basic test of a SystemT extractor - output all data types.
   * 
   * @throws Exception
   */
  @Test
  public void simpleSystemTTest01() throws Exception {
    startTest();

    setExecuteParams(new ExecuteParams(AnnotationServiceConstants.NO_TIMEOUT, null, null,
        AnnotatorBundleConfig.ContentType.HTML.getValue(), null));

    genericAnnotatorBundleTest(null, null);

    compareAgainstExpected(true);
  }

  /**
   * Test of a SystemT extractor - invalid tokenizer value.
   * 
   * @throws Exception
   */
  @Test
  public void simpleSystemTTest02() throws Exception {
    startTest();
    final File DOCS_FILE = new File(TestConstants.DUMPS_DIR, "simpleDoc.json");

    try {
      genericAnnotatorBundleTest(DOCS_FILE, null);
    } catch (AnnotationServiceException ase) {
      if (ase.getMessage()
          .contains("Tokenizer class 'unknown' specified in manifest.json not found!"))
        return;
    }
    Assert.fail();

  }

  /**
   * Test of a SystemT extractor - invalid module name.
   * 
   * @throws Exception
   */
  @Test
  public void simpleSystemTTest03() throws Exception {
    startTest();
    final File DOCS_FILE = new File(TestConstants.DUMPS_DIR, "simpleDoc.json");

    final String expectedMsg =
        "Module file 'BadModName.tam' is not found in the specified module path";

    try {
      genericAnnotatorBundleTest(DOCS_FILE, null);
    } catch (AnnotationServiceException ase) {
      if (ase.getMessage().contains(expectedMsg))
        return;
    }
    Assert.fail();
  }

  /**
   * Test of a SystemT extractor - invalid output view.
   * 
   * @throws Exception
   */
  @Test
  public void simpleSystemTTest04() throws Exception {
    startTest();
    final File DOCS_FILE = new File(TestConstants.DUMPS_DIR, "simpleDoc.json");

    final String expectedMsg =
        "The SystemT annotator does not output the following required output types: [testSystemTMod01.Unknown]; annotator output views are: [testSystemTMod01.AllTypes, testSystemTMod01.AtomicSpans, testSystemTMod01.AtomicValuesOfDifferentDataTypes, testSystemTMod01.ExtNames, testSystemTMod01.Phone, testSystemTMod01.ScalarListsOfDifferentTypes]; required output types (from annotator bundle config) are: [testSystemTMod01.Unknown]";

    try {
      genericAnnotatorBundleTest(DOCS_FILE, null);
    } catch (AnnotationServiceException ase) {
      if (ase.getMessage().contains(expectedMsg))
        return;
    }
    Assert.fail();
  }

  /**
   * Test of a SystemT extractor - wrong module path.
   * 
   * @throws Exception
   */
  @Test
  public void simpleSystemTTest05() throws Exception {
    startTest();
    final File DOCS_FILE = new File(TestConstants.DUMPS_DIR, "simpleDoc.json");

    final String expectedMsg =
        "Exception encountered when checking whether the following module path exists in the filesystem:";

    try {
      genericAnnotatorBundleTest(DOCS_FILE, null);
    } catch (AnnotationServiceException ase) {
      if (ase.getMessage().contains(expectedMsg))
        return;
    }
    Assert.fail();
  }

  /**
   * Test of a SystemT extractor - wrong configure for ext. dictionary.
   * 
   * @throws Exception
   */
  @Test
  public void simpleSystemTTest06() throws Exception {
    startTest();
    final File DOCS_FILE = new File(TestConstants.DUMPS_DIR, "simpleDoc.json");

    final String expectedMsg =
        "External dictionaries [unknown.dict] specified in external type info object are not part of any of the loaded modules.";
    try {
      genericAnnotatorBundleTest(DOCS_FILE, null);
    } catch (AnnotationServiceException ase) {
      if (ase.getMessage().contains(expectedMsg))
        return;
    }
    Assert.fail();
  }

  /*
   * PRIVATE METHODS GO HERE
   */

}
