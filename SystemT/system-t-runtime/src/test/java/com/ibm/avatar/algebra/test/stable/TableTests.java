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
import java.util.List;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.api.ExternalTypeInfo;
import com.ibm.avatar.api.exceptions.CompilerException;

/**
 * Various regression tests involving tables with all basic types.
 * 
 */
public class TableTests extends RuntimeTestHarness {

  /**
   * A dummy text file used by tests in this class
   */
  public static final File DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR,
      String.format("%s/%s", TableTests.class.getSimpleName(), "sampleData.txt"));

  /**
   * A dummy table file used by tests in this class
   */
  public static final File TABLE_DOC_FILE = new File(TestConstants.TEST_DOCS_DIR,
      String.format("%s/%s", TableTests.class.getSimpleName(), "doc.txt"));

  /**
   * Main method for invoking one test at a time.
   * 
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    TableTests t = new TableTests();
    t.setUp();

    long startMS = System.currentTimeMillis();

    t.basicTypesTest();

    long endMS = System.currentTimeMillis();

    t.tearDown();

    double elapsedSec = (double) ((endMS - startMS)) / 1000.0;
    System.err.printf("Test took %1.3f sec.\n", elapsedSec);
  }

  @Before
  public void setUp() {

  }

  @After
  public void tearDown() {

  }

  /**
   * Test support for tables with a column of type Float.
   */
  @Test
  public void basicTypesTest() throws Exception {
    startTest();
    setPrintTups(true);
    genericTest(DOCS_FILE, null);

    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Test support for well formed table file.
   */
  @Test
  public void tableFromFileTest() throws Exception {
    startTest();
    setPrintTups(true);
    genericTest(TABLE_DOC_FILE, null);

    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Test support for dictionary creation from well formed table file.
   */
  @Test
  public void dictFromTableFromFileTest() throws Exception {
    startTest();
    setPrintTups(true);
    genericTest(TABLE_DOC_FILE, null);

    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Test support for table file which doesn't exist in indicated path releative to module
   */
  @Test
  public void tableFromNoFileTest() throws Exception {

    startTest();
    String[] modules = new String[] {"tableFromNoFileTest"};
    try {
      compileModules(modules, null);
    } catch (CompilerException ce) {
      List<Exception> errors = ce.getAllCompileErrors();
      // first exception should be a parse exception
      Exception expectedParseException = errors.get(0);
      String expectedMessage =
          "Table file 'sampleTable.csv' \\(for table 'tableFromFileTest.MyTable'\\) not found in table path .*";
      Assert.assertTrue(Pattern.matches(expectedMessage, expectedParseException.getMessage()));
      return;
    }

    endTest();
  }

  /**
   * Test support for table file with well formed header but no tuples
   */
  @Test
  public void tableFromEmptyFileTest() throws Exception {

    startTest();
    String[] modules = new String[] {"tableFromEmptyFileTest"};
    try {
      compileModules(modules, null);
    } catch (CompilerException ce) {
      List<Exception> errors = ce.getAllCompileErrors();
      // first exception should be a parse exception
      Exception expectedParseException = errors.get(0);
      String expectedMessage = "Table MyTable has no tuples.  Compile time tables cannot be empty!";
      Assert.assertEquals(expectedMessage, expectedParseException.getMessage());
      return;
    }

    endTest();
  }

  /**
   * Test support for entry with incorrect type in columns
   */
  @Test
  public void malformedTableFromFileTest() throws Exception {
    startTest();
    String[] modules = new String[] {"malformedTableFromFileTest"};
    try {
      compileModules(modules, null);
    } catch (CompilerException ce) {
      List<Exception> errors = ce.getAllCompileErrors();
      // first exception should be a parse exception
      Exception expectedParseException = errors.get(0);
      String expectedMessage =
          "In  line 3, column 1: In the csv file '.*malformedSampleTable.csv', "
              + "on line 2, field value '<booleanVal>' in column 2 is not a valid Boolean.";
      Assert.assertTrue(Pattern.matches(expectedMessage, expectedParseException.getMessage()));
      return;
    }
    endTest();
  }

  /**
   * Test support for entry with non-matching headers
   */
  @Test
  public void malformedHeaderFromFileTest() throws Exception {
    startTest();
    String[] modules = new String[] {"malformedHeaderFromFileTest"};
    try {
      compileModules(modules, null);
    } catch (CompilerException ce) {
      List<Exception> errors = ce.getAllCompileErrors();
      // first exception should be a parse exception
      Exception expectedParseException = errors.get(0);
      String expectedMessage =
          "In  line 3, column 1: The header of CSV file '.*malformedHeaderTable.csv' differs from the schema of the external table.\n"
              + "Schema of CSV header: \\[<floatVal>, <wrongHeader>\\].\nSchema of external table: \\[<floatVal>, <booleanVal>\\].";
      Assert.assertTrue(Pattern.matches(expectedMessage, expectedParseException.getMessage()));
      return;
    }
    endTest();
  }
  /*
   * ADD NEW TEST CASES HERE
   */

  /*
   * UTILITY METHODS
   */

  /**
   * Generic test method that assumes that the source code of the module is located in:
   * 
   * <pre>
   * testdata/aql/[test class name]/[test case name]
   * </pre>
   * 
   * @param eti external artifacts object to be loaded into operator graph
   * @throws Exception
   */
  private void genericTest(File docsFile, ExternalTypeInfo eti) throws Exception {
    if (null == getCurOutputDir()) {
      throw new Exception("genericTest() called without calling startTest() first");
    }

    String className = getClass().getSimpleName();
    String testName = getCurPrefix();

    // Compute the location of the current test case's AQL code
    File moduleDir =
        new File(String.format("%s/%s/%s", TestConstants.AQL_DIR, className, testName));

    if (false == moduleDir.exists()) {
      throw new Exception(
          String.format("Directory containing modules %s not found.", moduleDir.getAbsolutePath()));
    }

    String[] modules = new String[] {testName};

    compileModules(modules, null);

    runModules(docsFile, modules, getCurOutputDir().toURI().toString(), eti);
  }

}
