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

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.api.CompileAQL;
import com.ibm.avatar.api.CompileAQLParams;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.api.tam.ModuleMetadata;
import com.ibm.avatar.api.tam.ModuleMetadataFactory;
import com.ibm.avatar.aql.tam.ModuleUtils;

/**
 * Various regression tests to test optimized consolidator.
 * 
 */
public class RequireDocColsTests extends RuntimeTestHarness {

  /**
   * A collection of 1000 Enron emails shared by tests in this class.
   */
  public static final File DOCS_FILE = new File(TestConstants.DUMPS_DIR, "ensmall.zip");

  /**
   * A simple made-up document for testing simple cases.
   */
  public static final File SIMPLE_CANNED_DOC_FILE =
      new File(TestConstants.TEST_DOCS_DIR + "/ConsolidateNewTests", "notContainedWithinTest.del");

  /**
   * 1 simple Enron email for a real document.
   */
  public static final File SIMPLE_DOC_FILE = new File(TestConstants.DUMPS_DIR, "enron1.del");

  /**
   * Main method for invoking one test at a time.
   * 
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    RequireDocColsTests t = new RequireDocColsTests();
    t.setUp();

    long startMS = System.currentTimeMillis();

    t.requireBadKeyWordsTest();

    long endMS = System.currentTimeMillis();

    t.tearDown();

    double elapsedSec = ((double) (endMS - startMS)) / 1000.0;
    System.err.printf("Test took %1.3f sec.\n", elapsedSec);
  }

  @Before
  public void setUp() {

  }

  @After
  public void tearDown() {

  }

  /**
   * Test to see if basic usage compiles.
   * 
   * @throws Exception
   */
  @Test
  public void basicTest() throws Exception {
    startTest();

    // Expect exceptions at these locations
    int[] lineNo = new int[] {};
    int[] colNo = new int[] {};
    compileAQLTest(lineNo, colNo);

    endTest();
  }

  /**
   * Test case to throw error if multiple require statements appear in same file
   * 
   * @throws Exception
   */
  @Test
  public void multipleRequireTest() throws Exception {
    startTest();

    // Expect exceptions at these locations
    int[] lineNo = new int[] {12};
    int[] colNo = new int[] {1};
    compileAQLTest(lineNo, colNo);

    endTest();
  }

  /**
   * Test all the require keywords and type literals
   * 
   * @throws Exception
   */
  @Test
  public void requireBadKeyWordsTest() throws Exception {
    startTest();

    // Expect exceptions at these locations
    int[] lineNo = new int[] {8, 11, 14, 17, 24, 32};
    int[] colNo = new int[] {1, 9, 18, 23, 18, 14};
    compileAQLTest(lineNo, colNo);

    endTest();
  }

  /**
   * Test case to verify two columns are not defined with same name
   * 
   * @throws Exception
   */
  @Test
  public void requireValidateTest() throws Exception {
    startTest();

    // Expect exceptions at these locations
    int[] lineNo = new int[] {7, 15};
    int[] colNo = new int[] {1, 13};
    compileAQLTest(lineNo, colNo);

    endTest();
  }

  /**
   * Test case to perform a run based on a consolidate function
   * 
   * @throws Exception
   */
  @Test
  public void consolidateTest() throws Exception {
    startTest();

    setDumpPlan(true);
    setDisableOutput(false);
    setPrintTups(true);

    runNonModularAQLTest(SIMPLE_CANNED_DOC_FILE);
    compareAgainstExpected("NumberUnit.htm", true);
    compareAgainstExpected("TwoToThreeShortWords.htm", true);
    compareAgainstExpected("ConsolidatedNotWords.htm", true);
    endTest();
  }

  /**
   * Test case to do a very basic run with the previous default docschema (text Text, label Text)
   * 
   * @throws Exception
   */
  @Test
  public void simpleRunTest() throws Exception {
    startTest();

    setDumpPlan(true);
    setDisableOutput(false);
    setPrintTups(true);

    runNonModularAQLTest(SIMPLE_CANNED_DOC_FILE);
    truncateOutputFiles(true);
    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Test to verify that the compiler return error for duplicate columns in 'require document ...'
   * statement.
   * 
   * @throws Exception
   */
  @Test
  public void duplicateColTest() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNo = new int[] {7};
    int[] colNo = new int[] {-1};

    compileModuleAndCheckErrors("duplicateColTest", lineNo, colNo);

    endTest();
  }

  /**
   * Test to verify that the compiler should **not** return error for duplicate columns in 'require
   * document ...' statement in different file of the same module.
   * 
   * @throws Exception
   */
  @Test
  public void duplicateCol2Test() throws Exception {
    startTest();

    // Expect no compilation error
    compileModule("duplicateCol2Test");

    endTest();
  }

  /**
   * Test to verify that the compiler should return error for duplicate columns with different data
   * type for 'require document ...' statement in different file of the same module.
   * 
   * @throws Exception
   */
  @Test
  public void duplicateCol3Test() throws Exception {
    startTest();

    int[] lineNo = new int[] {6};
    int[] colNo = new int[] {1};

    compileModuleAndCheckErrors("duplicateCol3Test", lineNo, colNo);

    endTest();
  }

  /**
   * Test to verify that compiler return error, while referring document column declared in a
   * different file of the same module. FIXME: Un comment this test once defect#28095(Compiler **not
   * ** returning error, while referring document field defined other aql files of the same module)
   * is fixed.
   * 
   * @throws Exception
   */
  // @Test
  public void invalidDocColReferenceTest() throws Exception {
    startTest();

    int[] lineNo = new int[] {17};
    int[] colNo = new int[] {-1};

    compileModuleAndCheckErrors("invalidDocColReferenceTest", lineNo, colNo);

    endTest();
  }

  /**
   * Test to verify that compiler merges custom document schema( declared through 'require document
   * ...' statement).
   * 
   * @throws Exception
   */
  @Test
  public void mergeDocSchema1Test() throws Exception {
    startTest();

    // Expected merged document schema
    TupleSchema expectedDocSchema = ModuleUtils.sortSchemaWithNonParameterizedTypes(
        new TupleSchema(new String[] {"id", "URL", "text", "detailedText"}, new FieldType[] {
            FieldType.INT_TYPE, FieldType.TEXT_TYPE, FieldType.TEXT_TYPE, FieldType.TEXT_TYPE}));
    expectedDocSchema.setName(Constants.DEFAULT_DOC_TYPE_NAME);

    // First compile the module
    compileModule("mergeDocSchema1Test");

    // Fetch merged document schema from compiled form(tam) of the module
    ModuleMetadata md =
        ModuleMetadataFactory.readMetaData(getCurPrefix(), getCurOutputDir().toURI().toString());
    TupleSchema actualdocSchema =
        ModuleUtils.sortSchemaWithNonParameterizedTypes(md.getDocSchema());

    // Compare schema
    Assert.assertEquals(expectedDocSchema, actualdocSchema);

    endTest();
  }

  /**
   * Test to verify that the loader merges document schema from all the loaded modules.
   * 
   * @throws Exception
   */
  @Test
  public void mergeDocSchema2Test() throws Exception {
    startTest();

    // set up the expected doc schema
    String[] nontextNames = new String[] {"id"};
    FieldType[] nontextTypes = new FieldType[] {FieldType.INT_TYPE};
    String[] textNames = new String[] {"text", "URL", "detailedText", "label"};

    TupleSchema expectedDocSchema = DocScanTests.makeDocSchema(Constants.DEFAULT_DOC_TYPE_NAME,
        nontextNames, nontextTypes, textNames);

    String[] moduleToCompile = new String[] {"module_customDocSchema", "module_defaultDocSchema"};

    // Compile and load test modules
    String[] moduleToCompileURIs = new String[moduleToCompile.length];
    for (int i = 0; i < moduleToCompileURIs.length; i++) {
      String uri = new File(getCurTestDir(), moduleToCompile[i]).toURI().toString();
      moduleToCompileURIs[i] = uri;
    }

    // Prepare compilation parameter
    CompileAQLParams param = new CompileAQLParams();
    param.setInputModules(moduleToCompileURIs);
    param.setOutputURI(getCurOutputDir().toURI().toString());
    param.setTokenizerConfig(getTokenizerConfig());
    CompileAQL.compile(param);

    // Load
    OperatorGraph og = OperatorGraph.createOG(moduleToCompile, getCurOutputDir().toURI().toString(),
        null, getTokenizerConfig());

    // Fetch merged schema from the loaded Operator graph
    TupleSchema actualDocSchema = og.getDocumentSchema();

    // Compare schema
    Assert.assertEquals(expectedDocSchema, actualDocSchema);

    endTest();
  }

  @Test
  public void variousFieldTypesTest() throws Exception {
    startTest();

    File inputFile = new File(getCurTestDir(), "input.json");
    setPrintTups(true);
    compileAndRunModule("module1", inputFile, null);

    // small input, so nothing to truncate
    compareAgainstExpected(false);
    endTest();
  }

  /*
   * ADD NEW TEST CASES HERE
   */

  /*
   * UTILITY METHODS
   */

}
