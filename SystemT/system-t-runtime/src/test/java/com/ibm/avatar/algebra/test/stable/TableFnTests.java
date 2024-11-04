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

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.opencsv.CSVReader;

import com.ibm.avatar.algebra.datamodel.FieldSetter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.util.document.ToHTMLOutput;
import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.algebra.util.pmml.PMMLUtil;
import com.ibm.avatar.algebra.util.test.MemoryProfiler;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.api.exceptions.CompilerException;
import com.ibm.avatar.api.exceptions.ExceptionWithView;
import com.ibm.avatar.api.exceptions.TableUDFException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.tam.TAM;
import com.ibm.avatar.aql.tam.TAMSerializer;
import com.ibm.avatar.logging.Log;

/**
 * Various regression tests of functionality involving table functions and functions that take table
 * locators as arguments. The test cases in this class make use of the UDF jar file
 * testdata/udfjars/tableFnTestsUDFs.jar, which is built by testdata/udfjars/BuildUDF.xml.
 * 
 */
public class TableFnTests extends RuntimeTestHarness {

  public static void main(String[] args) {
    try {

      TableFnTests t = new TableFnTests();

      t.setUp();

      long startMS = System.currentTimeMillis();

      t.runtimeErrorTest2();

      long endMS = System.currentTimeMillis();

      t.tearDown();

      double elapsedSec = (double) (endMS - startMS) / 1000.0;

      System.err.printf("Test took %1.3f sec.\n", elapsedSec);

      MemoryProfiler.dumpHeapSize("After test");

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Before
  public void setUp() throws Exception {

  }

  @After
  public void tearDown() {

  }

  /**
   * Simple test of table functions. Declares a user-defined table function in some AQL, then calls
   * the function.
   */
  @Test
  public void basicTest() throws Exception {
    startTest();

    // Use 100 documents as input, so that the table function gets called 100 times.
    File docsFile = new File(TestConstants.ENRON_100_DUMP);

    super.setPrintTups(true);

    super.compileAndRunModule("basicTest", docsFile, null);

    super.compareAgainstExpected(false);

    endTest();
  }

  /**
   * Test of the schema validation API built into the table function base class.
   */
  @Test
  public void schemaValidationTest() throws Exception {
    startTest();

    int[] lineNums = {9, 20, 32, 44, 55, 69, 87};
    int[] colNums = {1, 1, 1, 1, 1, 1, 15};

    boolean caughtException = false;
    try {
      super.compileModule("schemaValidationTest");
    } catch (CompilerException e) {
      checkException(e, lineNums, colNums);
      caughtException = true;
    }

    if (false == caughtException) {
      throw new TextAnalyticsException("Expected %d compile errors but found none.",
          lineNums.length);
    }

    endTest();
  }

  /**
   * Additional test of schema validation. Looks for validation errors that are masked by the errors
   * in schemaValidationTest().
   */
  @Test
  public void schemaValidationTest2() throws Exception {
    startTest();

    int[] lineNums = {9};
    int[] colNums = {1};

    boolean caughtException = false;
    try {
      super.compileModule("schemaValidationTest2");
    } catch (CompilerException e) {
      checkException(e, lineNums, colNums);
      caughtException = true;
    }

    if (false == caughtException) {
      throw new TextAnalyticsException("Expected %d compile errors but found none.",
          lineNums.length);
    }

    endTest();
  }

  /**
   * Another test of schema validation. Looks for validation errors that are masked by the errors in
   * schemaValidationTest() and schemaValidationTest2()
   */
  @Test
  public void schemaValidationTest3() throws Exception {
    startTest();

    boolean caughtException = false;
    try {
      File docsFile = new File(TestConstants.ENRON_100_DUMP);

      // This test triggers failure at operator graph load time, so we use compileAndRunModule()
      super.compileAndRunModule("schemaValidationTest3", docsFile, null);
    } catch (Exception e) {
      System.err.printf("Caught exception as expected: %s\n", e);
      // e.printStackTrace ();
      caughtException = true;

      // Make sure we caught the *right* exception...
      // Trace down to the root cause.
      Throwable rootCause = e;
      while (null != rootCause.getCause()) {
        rootCause = rootCause.getCause();
      }

      System.err.printf("Root cause exception is: %s\n", rootCause);
      if (false == (rootCause instanceof TableUDFException)) {
        throw new TextAnalyticsException(
            "Root cause exception is of type %s instead of expected type TableUDFException.",
            rootCause.getClass().getName());
      }

      final String EXPECTED_MSG = "First column in schema is not Text";
      if (false == EXPECTED_MSG.equals(rootCause.getMessage())) {
        throw new TextAnalyticsException(
            "Root cause exception has message '%s' instead of expected message '%s'.",
            rootCause.getMessage(), EXPECTED_MSG);
      }
    }

    if (false == caughtException) {
      throw new TextAnalyticsException("Expected to catch an exception but did not.");
    }

    endTest();
  }

  /**
   * Test of exporting and importing table functions.
   */
  @Test
  public void importTest() throws Exception {
    startTest();

    // Use 100 documents as input, so that the table function gets called 100 times.
    File docsFile = new File(TestConstants.ENRON_100_DUMP);

    super.setPrintTups(true);

    // There are two modules in this test case.
    // module1 defines the function (and also calls it)
    // module2 imports the function from module1

    // Compile the modules separately to make sure that the import works in that case.
    System.err.printf("Compiling module 1...\n");
    compileModule("module1");

    System.err.printf("Compiling module 2...\n");
    compileModule("module2");

    System.err.printf("Running both modules...\n");
    runModules(docsFile, new String[] {"module1", "module2"}, getCurOutputDir().toURI().toString(),
        null);

    compareAgainstExpected(false);

    endTest();
  }

  /**
   * Test of exporting and importing table functions with locator arguments.
   */
  @Test
  public void importTest2() throws Exception {
    startTest();

    // Use 100 documents as input, so that the table function gets called 100 times.
    File docsFile = new File(TestConstants.ENRON_100_DUMP);

    super.setPrintTups(true);

    // There are two modules in this test case.
    // module1 defines the function (and also calls it)
    // module2 imports the function from module1

    // Compile the modules separately to make sure that the import works in that case.
    System.err.printf("Compiling module 1...\n");
    compileModule("module1");

    System.err.printf("Compiling module 2...\n");
    compileModule("module2");

    System.err.printf("Running both modules...\n");
    runModules(docsFile, new String[] {"module1", "module2"}, getCurOutputDir().toURI().toString(),
        null);

    compareAgainstExpected(false);

    endTest();
  }

  /**
   * Test of locator-valued arguments to table functions.
   */
  @Test
  public void locatorTest() throws Exception {
    startTest();

    // Use 100 documents as input, so that the table function gets called 100 times.
    File docsFile = new File(TestConstants.ENRON_100_DUMP);

    super.setPrintTups(true);

    super.compileAndRunModule("locatorTest", docsFile, null);

    super.compareAgainstExpected(false);

    endTest();
  }

  /**
   * Test of handling exceptions thrown by table functions at run time.
   */
  @Test
  public void runtimeErrorTest() throws Exception {
    startTest();

    // Use 100 documents as input, so that the table function gets called 100 times.
    File docsFile = new File(TestConstants.ENRON_100_DUMP);

    super.setPrintTups(true);

    try {
      super.compileAndRunModule("runtimeErrorTest", docsFile, null);
    } catch (TextAnalyticsException e) {
      // Caught the type of root exception we expected. Trace down the chain of causality.
      assertNotNull(e.getCause());
      ExceptionWithView ewv = (ExceptionWithView) e.getCause();

      // Caught the type of exception we expected. Make sure the view is correct.
      assertEquals("runtimeErrorTest.TabFuncOutput", ewv.getViewName());

      // Make sure that the next element of the cause chain has a user-friendly message.
      assertNotNull(ewv.getCause());
      TextAnalyticsException cause = (TextAnalyticsException) ewv.getCause();
      assertEquals("Error invoking runtimeErrorTest.MyTableFunc() table function over inputs [42]",
          cause.getMessage());
    }

    endTest();
  }

  /**
   * Test to ensure that the compiler disallows circular references created via function invocations
   * over locators.
   */
  @Test
  public void circularRefTest() throws Exception {
    startTest();

    int[] lineNums = {31};
    int[] colNums = {13};

    boolean caughtException = false;
    try {
      super.compileModule("circularRefTest");
    } catch (CompilerException e) {
      checkException(e, lineNums, colNums);
      caughtException = true;
    }

    if (false == caughtException) {
      throw new TextAnalyticsException("Expected %d compile errors but found none.",
          lineNums.length);
    }

    endTest();
  }

  /**
   * Test case for defect
   */
  @Test
  public void circularRefTest2() throws Exception {
    startTest();

    int[] lineNums = {34};
    int[] colNums = {13};

    boolean caughtException = false;
    try {
      super.compileModule("circularRefTest2");
    } catch (CompilerException e) {
      checkException(e, lineNums, colNums);
      caughtException = true;
    }

    if (false == caughtException) {
      throw new TextAnalyticsException("Expected %d compile errors but found none.",
          lineNums.length);
    }

    endTest();
  }

  /**
   * Tests for rewriting sequence patterns that involve table functions.
   */
  @Test
  public void seqPatternTest() throws Exception {
    startTest();

    // Use 100 documents as input, so that the table function gets called 100 times.
    File docsFile = new File(TestConstants.ENRON_100_DUMP);

    super.setPrintTups(true);

    super.compileAndRunModule("seqPatternTest", docsFile, null);

    super.compareAgainstExpected(false);

    endTest();
  }

  /**
   * Tests for ensuring table functions include only the first few input tuples in the error message
   * For RTC defect: 163012: Exceptions from UDFs with table input may be exceptionally long
   */
  @Test
  public void runtimeErrorTest2() throws Exception {
    startTest();

    // Use 1 document as input, so that the table function gets called 1 time.
    File docsFile = new File(TestConstants.ENRON_1_DUMP);

    super.setPrintTups(true);

    try {
      super.compileAndRunModule("runtimeErrorTest2", docsFile, null);
    } catch (Exception e) {
      String actualMsg = e.getCause().getCause().getMessage();
      String unexpectedMsgFragment = "four";

      Log.debug("Error message is %s", actualMsg);

      assertFalse(String.format("Expected that message contains string '%s', but message is: '%s'",
          unexpectedMsgFragment, actualMsg), actualMsg.contains(unexpectedMsgFragment));
    }

    endTest();
  }


  /**
   * Low-level test of the scoring function behind the CREATE MODEL statement
   */
  @Test
  public void scoringFnTest() throws Exception {
    startTest();

    // don't run this test in an environment without a JDK or else an exception will be thrown
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (null != compiler) {

      // The models, represented as PMML
      final File logRegPMMLFile = new File(getCurTestDir(), "single_iris_logreg.xml");
      final File kMeansPMMLFile = new File(getCurTestDir(), "single_iris_kmeans.xml");

      // Temporary jar file that we package the PMML into so that it can be embedded inside the TAM
      final File logRegJarTmp = new File(getCurOutputDir(), "iris.jar");
      final File kMeansJarTmp = new File(getCurOutputDir(), "irisKmeans.jar");

      // Here's the file where the "real" input data resides
      final File inputCSV = new File(getCurTestDir(), "Iris.csv");

      final String moduleName = "scoringFnTest";

      CSVReader readCSV = new CSVReader(new InputStreamReader(new FileInputStream(inputCSV)));
      List<String[]> inputCSVLines = readCSV.readAll();
      readCSV.close();

      // Start by packaging up the PMML model inside a jar file.
      PMMLUtil.makePMMLJar(logRegPMMLFile, "com.ibm.systemt.pmml", "TestFunc", logRegJarTmp);
      PMMLUtil.makePMMLJar(kMeansPMMLFile, "com.ibm.systemt.pmml", "KMeans", kMeansJarTmp);

      // Then compile and instantiate the module
      OperatorGraph og = compileAndLoadModule(moduleName, null);

      // Dump the plan while we're at it.
      TAM tam = TAMSerializer.load(moduleName, getCurOutputDir().toURI().toString());
      FileUtils.strToFile(tam.getAog(), new File(getCurOutputDir(), "plan.aog"), "UTF-8");

      // Can't use the superclass's utility functions to run the extractor, since external views are
      // involved.

      // Set up the external view's tuples.
      // For some reason, the "external_name" clause of "create external view" doesn't seem to do
      // anything.
      final String irisViewName = "scoringFnTest.IrisData";
      Log.debug("External view names are %s", Arrays.toString(og.getExternalViewNames()));

      TupleSchema irisSchema = og.getExternalViewSchema(irisViewName);
      FieldSetter<Float> sepalLengthSetter = irisSchema.floatSetter("sepal_length");
      FieldSetter<Float> sepalWidthSetter = irisSchema.floatSetter("sepal_width");
      FieldSetter<Float> petalLengthSetter = irisSchema.floatSetter("petal_length");
      FieldSetter<Float> petalWidthSetter = irisSchema.floatSetter("petal_width");
      FieldSetter<String> classSetter = irisSchema.textSetter("actual_class");

      TupleList irisTups = new TupleList(irisSchema);

      // Note that we skip the header and start at line 1, not 0
      for (int i = 1; i < inputCSVLines.size(); i++) {
        String[] fields = inputCSVLines.get(i);
        Tuple curTup = irisSchema.createTup();

        sepalLengthSetter.setVal(curTup, Float.valueOf(fields[0]));
        sepalWidthSetter.setVal(curTup, Float.valueOf(fields[1]));
        petalLengthSetter.setVal(curTup, Float.valueOf(fields[2]));
        petalWidthSetter.setVal(curTup, Float.valueOf(fields[3]));
        classSetter.setVal(curTup, fields[4]);

        irisTups.add(curTup);
      }
      String[][] linesArray = new String[inputCSVLines.size()][];
      inputCSVLines.toArray(linesArray);
      TreeMap<String, TupleList> extViewTups = new TreeMap<String, TupleList>();
      extViewTups.put(irisViewName, irisTups);

      // Set up a simple document schema
      TupleSchema docSchema = og.getDocumentSchema();
      FieldSetter<String> textSetter = docSchema.textSetter("text");

      // Initialize the utility object to write the output HTML files
      final boolean printTups = true;
      ToHTMLOutput toHtmlOut = new ToHTMLOutput(og.getOutputTypeNamesAndSchema(), getCurOutputDir(),
          printTups, this.getWriteCharsetInfo(), false, docSchema);

      // Push a dummy document through the extractor, along with the external view that contains the
      // primary input data
      Tuple docTup = docSchema.createTup();
      textSetter.setVal(docTup, "Dummy document text");

      Map<String, TupleList> results = og.execute(docTup, null, extViewTups);
      toHtmlOut.write(docTup, results);
      toHtmlOut.close();

      super.compareAgainstExpected(false);
    }

    endTest();
  }
}
