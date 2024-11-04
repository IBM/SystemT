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

import org.junit.Assert;
import org.junit.Test;

import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.algebra.util.tokenize.TokenizerConfig;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.api.exceptions.ModuleLoadException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;

/**
 * Test suite that verifies if OperatorGraph.validateOG() method is working fine. This class
 * validates loading of operator graph using modulePath. For tests related to loading of
 * OperatorGraph from classpath, please refer to TAMLoaderTests.
 * 
 */
public class ValidateOGTests extends RuntimeTestHarness {
  /**
   * WhiteSpace tokenizer configuration
   */
  private static final TokenizerConfig whitespaceTokenizerCfg = new TokenizerConfig.Standard();

  /**
   * Main method for invoking one test at a time.
   * 
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    ValidateOGTests t = new ValidateOGTests();
    // t.setUp ();

    long startMS = System.currentTimeMillis();

    t.compatibleDocSchemaTest();

    long endMS = System.currentTimeMillis();

    // t.tearDown ();

    double elapsedSec = (double) ((endMS - startMS)) / 1000.0;
    System.err.printf("Test took %1.3f sec.\n", elapsedSec);
  }

  /**
   * Scenario: Load a set of modules, where a dependent module is missing in module path <br/>
   * Expected result: ModuleLoadException should be thrown. Cause: ModuleNotFoundException
   * 
   * @throws Exception
   */
  @Test
  public void validateMissingModuleTest() throws Exception {
    startTest();

    // TEST 1: Test with specific modulePath
    // include the path of phone.tam, but skip the path of person.tam
    File modulePath = new File(TestConstants.TESTDATA_DIR + "/tam");
    genericTestCase(new String[] {"personPhone"}, modulePath.toURI().toString(),
        whitespaceTokenizerCfg, "com.ibm.avatar.api.exceptions.ModuleNotFoundException");

    endTest();

  }

  /**
   * Verifies that modules can be loaded successfully from modulePath and classpath
   * 
   * @throws Exception
   */
  @Test
  public void successfulModuleLoadTest() throws Exception {
    startTest();

    // TEST 1: Test with specific modulePath
    File pathOfTAMs = new File(TestConstants.TESTDATA_DIR + "/tam");
    File pathOfPersonTAM = new File(TestConstants.TESTDATA_DIR + "/tam/subdir");
    String modulePathURI = pathOfTAMs.toURI().toString() + Constants.MODULEPATH_SEP_CHAR
        + pathOfPersonTAM.toURI().toString();
    genericTestCase(new String[] {"personPhone"}, modulePathURI, whitespaceTokenizerCfg, null);

    endTest();
  }

  /**
   * Scenario: Attempt to load a module from a modulePath where more than one TAM with same name
   * exists. <br/>
   * Expected result: ModuleLoadException should be thrown. Cause: AmbiguousModuleMatchException
   * 
   * @throws Exception
   */
  @Test
  public void validateDuplicateModuleTest() throws Exception {
    startTest();

    // TEST 1: Test with specific modulePath
    // include the path of phone.tam, but skip the path of person.tam
    File pathOfTAMs = new File(TestConstants.TESTDATA_DIR + "/tam");
    File pathOfPersonTAM = new File(TestConstants.TESTDATA_DIR + "/tam/subdir");
    File duplicateModulePath = new File(TestConstants.TESTDATA_DIR + "/tam/duplicate");
    String modulePathURI = pathOfTAMs.toURI().toString() + Constants.MODULEPATH_SEP_CHAR
        + pathOfPersonTAM.toURI().toString() + Constants.MODULEPATH_SEP_CHAR
        + duplicateModulePath.toURI().toString();
    genericTestCase(new String[] {"personPhone"}, modulePathURI, whitespaceTokenizerCfg,
        "com.ibm.avatar.api.exceptions.AmbiguousModuleMatchException");

    endTest();
  }

  /**
   * Scenario: Attempt to load two modules whose document schemas are compatible. i.e they have
   * duplicate field names, but *not* conflicting types.<br/>
   * schema1 : Document(text Text in Document.text, URL Integer) <br/>
   * schema2 : Document(text Text in Document.text, label Text in Document.label) <br/>
   * Expected result: Not expecting any exception, because the docschemas are compatible.
   * 
   * @throws Exception
   */
  @Test
  public void compatibleDocSchemaTest() throws Exception {
    startTest();

    // TEST 1: Test with specific modulePath
    File pathOfTAMs = new File(TestConstants.TESTDATA_DIR + "/tam");
    // The following path contains an invalid person.tam, to simulate the problem of incomplete
    // operator graph
    File pathOfCompatiblePersonTAM =
        new File(TestConstants.TESTDATA_DIR + "/tam/compatibleDocSchema");
    String modulePathURI = pathOfTAMs.toURI().toString() + Constants.MODULEPATH_SEP_CHAR
        + pathOfCompatiblePersonTAM.toURI().toString();
    genericTestCase(new String[] {"personPhone"}, modulePathURI, whitespaceTokenizerCfg, null);

    endTest();
  }

  /**
   * Scenario: Attempt to load module whose document schemas have duplicate names with conflicting
   * field types <br/>
   * schema1 : Document(text Text in Document.text, label Integer) <br/>
   * schema2 : Document(text Text in Document.text, label Text in Document.label) <br/>
   * Expected result: ModuleLoadException should be thrown. Cause: DocSchemaMismatchException
   * 
   * @throws Exception
   */
  @Test
  public void validateIncompatibleDocSchemaTest() throws Exception {
    startTest();

    // TEST 1: Test with specific modulePath
    File pathOfTAMs = new File(TestConstants.TESTDATA_DIR + "/tam");
    // The following path contains an invalid person.tam, to simulate the problem of incomplete
    // operator graph
    File pathOfPersonTAMWithIncompatibleDocSchema =
        new File(TestConstants.TESTDATA_DIR + "/tam/incompatibleDocSchema");
    String modulePathURI = pathOfTAMs.toURI().toString() + Constants.MODULEPATH_SEP_CHAR
        + pathOfPersonTAMWithIncompatibleDocSchema.toURI().toString();
    genericTestCase(new String[] {"personPhone"}, modulePathURI, whitespaceTokenizerCfg,
        "com.ibm.avatar.api.exceptions.DocSchemaMismatchException");

    endTest();
  }

  /**
   * Scenario: Prepare module A.tam, that depends on a view in module B. After successful
   * compilation, generate B.tam without the dependent view. Attempt to load module A. <br/>
   * Expected result: ModuleLoadException should be thrown. Cause: DependencyResolutionException
   * 
   * @throws Exception
   */
  @Test
  public void validateDependencyResolutionTest() throws Exception {
    startTest();

    // TEST 1: Test with specific modulePath
    File pathOfTAMs = new File(TestConstants.TESTDATA_DIR + "/tam");
    // The following path contains an invalid person.tam, to simulate the problem of incomplete
    // operator graph
    File pathOfPersonTAMWithMissingViews =
        new File(TestConstants.TESTDATA_DIR + "/tam/missingDependencies");
    String modulePathURI = pathOfTAMs.toURI().toString() + Constants.MODULEPATH_SEP_CHAR
        + pathOfPersonTAMWithMissingViews.toURI().toString();
    genericTestCase(new String[] {"personPhone"}, modulePathURI, whitespaceTokenizerCfg,
        "com.ibm.avatar.api.exceptions.DependencyResolutionException");

    endTest();
  }

  /**
   * Test to verify that module containing required (allow_empty == false) external artifacts
   * (tables and dictionaries) are loadable through ValidateOG method, without passing in content
   * for external tables/dictionaries. * In addition, refer defect also.
   * 
   * @throws Exception
   */
  @Test
  public void validateModuleContainingReqExtArtifactsTest() throws Exception {
    startTest();

    // Compile the module containing required external dictionaries.
    compileModule("validateModuleContainingReqExtArtifactsTest");

    // Validate if the compiled module is loadable without passing the value for required external
    // dictionaries
    try {
      boolean validationResult = OperatorGraph.validateOG(new String[] {getCurPrefix()},
          getCurOutputDir().toURI().toString(), whitespaceTokenizerCfg);
      Assert.assertTrue(validationResult);
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(String.format("Unexpected exception: %s", e.getMessage()));
    }

  }

  /**
   * Generic test case that tests validateOG() method.
   * 
   * @param moduleNames An array of moduleNames to run
   * @param modulePath Semicolon separated list of directories or JAR/ZIP files where module can be
   *        found. Pass null to load from system classpath
   * @param expectedException Name of the exception that is expected to be thrown. Pass null if no
   *        exceptions are expected.
   */
  private void genericTestCase(String[] moduleNames, String modulePath,
      TokenizerConfig tokenizerCfg, String expectedCauseException) throws Exception {
    try {
      OperatorGraph.validateOG(moduleNames, modulePath, tokenizerCfg);

      // if an Exception is expected, but *not* thrown, then mark the test case as FAILED
      if (null != expectedCauseException) {
        Assert.fail(
            String.format("Expected ModuleLoadException with cause %s, but no exception thrown",
                expectedCauseException));
      }
    } catch (ModuleLoadException e) {

      // if an Exception is *not* expected, but thrown, then mark the test case as FAILED
      if (null == expectedCauseException) {
        // Also output the exception, since the developer will have no way to debug the test case
        // otherwise...
        throw new TextAnalyticsException(e,
            "No exceptions are expected. But received exception %s, with message: %s",
            e.getClass().getName(), e.getMessage());
      }

      // If the exception is due to the expected cause, then mark the test case as PASSED
      if (true == e.getCause().getClass().getName().equals(expectedCauseException)) {
        return; // success
      } else {
        // If the exception is *not* due to the expected cause, then mark the test case as FAILED
        Assert.fail(String.format(
            "Expected ModuleLoadException with cause %s, but instead received %s with cause %s",
            expectedCauseException, e.getClass().getName(), e.getCause().getClass().getName()));
      }
    } catch (Exception ex) {
      // if an Exception is *not* expected, but thrown, then mark the test case as FAILED
      if (null == expectedCauseException) {
        // Also output the exception, since the developer will have no way to debug the test case
        // otherwise...
        throw new TextAnalyticsException(ex,
            "No exceptions are expected. But received exception %s, with message: %s",
            ex.getClass().getName(), ex.getMessage());
      }

      // Before implementation of multiModuleMetadata (Story 49123), all test cases in this suite
      // were expected to throw
      // a ModuleLoadException
      // Now, modules are not necessarily loaded by the validator, just metadata, so a
      // ModuleLoadException is
      // not always appropriate.

      // If the exception is due to the expected cause, then mark the test case as PASSED
      if (true == ex.getClass().getName().equals(expectedCauseException)) {
        return; // success
      }

      // If some other exception
      // is thrown, then mark the test case as FAILED.
      if (ex.getCause() != null) {
        // Also output the exception, since the developer will have no way to debug the test case
        // otherwise...
        throw new TextAnalyticsException(ex,
            "Expected ModuleLoadException with cause %s, but instead received %s with cause %s",
            expectedCauseException, ex.getClass().getName(), ex.getCause().getClass().getName());
      } else {
        ex.printStackTrace();
      }
    }

  }

}
