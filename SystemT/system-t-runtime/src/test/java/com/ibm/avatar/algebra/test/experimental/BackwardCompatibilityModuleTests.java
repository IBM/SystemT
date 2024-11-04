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
package com.ibm.avatar.algebra.test.experimental;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;

import com.ibm.avatar.algebra.util.file.SearchPath;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.tam.ModuleMetadata;
import com.ibm.avatar.api.tam.ModuleMetadataFactory;
import com.ibm.avatar.api.tam.ViewMetadata;

/**
 * Test cases that check for various functionality of v2.0 compiler & runtime with v1.3 AQLs as
 * input.
 * 
 */
public class BackwardCompatibilityModuleTests extends RuntimeTestHarness {

  public static final File DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR, "/TAMTests/input.zip");

  /** Location of the SDA 1.4 AQL code that is used by multiple tests in this harness */
  public static final String SDA_AQL_DIR_NAME =
      TestConstants.AQL_DIR + "/BackwardCompatibilityModuleTests/sda_29745";

  /** Data path to invoke the SDA extractors using various different main AQL files */
  public static final String SDA_DATA_PATH =
      String.format("%s%c%s/udfjars", SDA_AQL_DIR_NAME, SearchPath.PATH_SEP_CHAR, SDA_AQL_DIR_NAME);

  /**
   * Main method for running a single test case at a time.
   * 
   * @param args no arguments required
   */
  public static void main(String[] args) {
    try {

      BackwardCompatibilityModuleTests t = new BackwardCompatibilityModuleTests();

      // t.setUp ();

      long startMS = System.currentTimeMillis();

      t.simplePersonPhoneTest();

      long endMS = System.currentTimeMillis();

      // t.tearDown ();

      double elapsedSec = ((double) (endMS - startMS)) / 1000.0;

      System.err.printf("Test took %1.3f sec.\n", elapsedSec);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Test
  public void simplePersonPhoneTest() throws Exception {
    startTest();
    runNonModularAQLTest(DOCS_FILE);
  }

  /**
   * Verifies that output views are written out to metadata in backward compatibility mode
   * 
   * @throws Exception
   */
  @Test
  public void outputViewMetadataTest() throws Exception {
    startTest();
    compileAQLTest(null, null);// in this case, we are not expecting any AQL compilation errors. So,
                               // passing null to
    // lineNo, colNo

    ModuleMetadata metadata = ModuleMetadataFactory.readMetaData(Constants.GENERIC_MODULE_NAME,
        getCurOutputDir().toURI().toString());
    String[] outputViews = metadata.getOutputViews();

    assertNotNull("There are no output views in module", outputViews);
    assertEquals("Number of output views for module 'outputViewMetadataTest' must be 1", 1,
        outputViews.length);
    assertEquals("PhoneNumber is not a part of module metadata", "PhoneNumber", outputViews[0]);

    ViewMetadata vmd = metadata.getViewMetadata("PhoneNumber");
    assertNotNull("View metadata should not be null", vmd);
    assertTrue("'output' parameter of PhoneNumber view metadata should be true",
        vmd.isOutputView());
    assertFalse("'external' parameter of PhoneNumber view metadata should be false",
        vmd.isExternal());
    assertFalse("'exported' parameter of PhoneNumber view metadata should be false",
        vmd.isExported());
    assertNotNull("schema of PhoneNumber view should not be null", vmd.getViewSchema());
  }

  /**
   * Verifies that dictionary references inside nested function calls like
   * Not(ContainsDict('dictname')) are properly identified when creating a generic module
   * 
   * @throws Exception
   */
  @Test
  public void dictInNotContainsDictNotCopied() throws Exception {
    startTest();
    final String BASE_DIR =
        TestConstants.AQL_DIR + "/BackwardCompatibilityModuleTests/dictInNotContainsDictNotCopied";

    final String AQL_FILE_NAME = BASE_DIR + "/test.aql";
    final String DATA_PATH = BASE_DIR;

    compileAQL(new File(AQL_FILE_NAME), DATA_PATH);
    endTest();
  }

  /**
   * Test case to verify that the compiler when ran in BC mode does not strip, table rows containing
   * 'include' as value for one of the field;generic module preparation process strips of include
   * statement. This test case recreates the scenario mentioned in defect#33820.
   * 
   * @throws Exception
   */
  @Test
  public void tableEntryContainingIncludeTest() throws Exception {
    startTest();
    final String BASE_DIR =
        TestConstants.AQL_DIR + "/BackwardCompatibilityModuleTests/tableEntryContainingIncludeTest";

    final String AQL_FILE_NAME = BASE_DIR + "/tableEntryContainingIncludeTest.aql";
    final String DATA_PATH = BASE_DIR;

    compileAQL(new File(AQL_FILE_NAME), DATA_PATH);
    endTest();
  }
}
