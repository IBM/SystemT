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
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.TextGetter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.util.document.ToCSVOutput;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.api.CompileAQL;
import com.ibm.avatar.api.CompileAQLParams;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.DocReader;
import com.ibm.avatar.api.ExternalTypeInfo;
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.logging.Log;

/**
 * Various regression tests concerning spans with same or different document texts
 * 
 */
public class SameDocTests extends RuntimeTestHarness {

  /**
   * A dummy text file used by tests in this class
   */
  public static final File DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR,
      String.format("%s/%s", SameDocTests.class.getSimpleName(), "sampleData.txt"));

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

    double elapsedSec = ((endMS - startMS)) / 1000.0;
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
  public void basicTest() throws Exception {
    startTest();
    setPrintTups(true);
    genericTest(DOCS_FILE, null);

    compareAgainstExpected(true);
    endTest();
  }


  /**
   * Test 1 for original reported issue.
   * <p>
   * defect : Span created from alternative doc text fails in Contains
   * 
   * @throws Exception
   */
  @Test
  public void defect100793Test1() throws Exception {

    final File aqlFile = new File(TestConstants.AQL_DIR + "/SameDocTests/defect100793Test1.aql");
    final String dataPath = null;
    final File docsFile =
        new File(TestConstants.TEST_DOCS_DIR + "/SameDocTests/defect100793Test1.del");

    startTest();
    genericTest(aqlFile, dataPath, docsFile);
    compareAgainstExpected(false);
    endTest();
  }

  /**
   * Test 1 for original reported issue.
   * <p>
   * defect : Span created from alternative doc text fails in Contains
   * 
   * @throws Exception
   */
  @Test
  public void defect100793Test2() throws Exception {

    final File docsFile =
        new File(TestConstants.TEST_DOCS_DIR + "/SameDocTests/defect100793Test1.del");

    startTest();
    genericTest(docsFile, null);
    compareAgainstExpected(false);
    endTest();
  }

  /**
   * Test 1 for original reported issue.
   * <p>
   * defect : SystemT Runtime: Block operator does not check for same doc text
   * 
   * @throws Exception
   */
  @Test
  public void defect101011Test1() throws Exception {

    final File aqlFile = new File(TestConstants.AQL_DIR + "/SameDocTests/defect101011Test1.aql");
    final String dataPath = null;
    final File docsFile =
        new File(TestConstants.TEST_DOCS_DIR + "/SameDocTests/defect101011Test1.csv");

    startTest();
    genericTest(aqlFile, dataPath, docsFile);
    compareAgainstExpected(false);
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
    File currentOutputDir = getCurOutputDir();
    if (null == currentOutputDir) {
      throw new Exception("genericTest() called without calling startTest() first");
    }

    String className = getClass().getSimpleName();
    String testName = getCurPrefix();
    System.out.println("testName = " + testName);
    System.out.println("currentOutputDir = " + currentOutputDir);

    // Compute the location of the current test case's AQL code
    File moduleDir =
        new File(String.format("%s/%s/%s", TestConstants.AQL_DIR, className, testName));
    System.out.println("moduleDir = " + moduleDir);

    if (false == moduleDir.exists()) {
      throw new Exception(
          String.format("Directory containing modules %s not found.", moduleDir.getAbsolutePath()));
    }

    String[] modules = new String[] {testName};
    System.out.println("module = " + testName);

    compileModules(modules, null);

    runModules(docsFile, modules, currentOutputDir.toURI().toString(), eti);
  }

  /**
   * @param aqlFile
   * @param dataPath
   * @param docsFile
   * @param str
   * @throws Exception
   */
  private void genericTest(File aqlFile, String dataPath, File docsFile) throws Exception {

    // Compile the AQL file and serialize the TAM to test case directory.
    String moduleURI = getCurOutputDir().toURI().toString();
    CompileAQLParams params = new CompileAQLParams(aqlFile, moduleURI, dataPath);
    params.setTokenizerConfig(this.getTokenizerConfig());
    Log.info("Compiler Parameters are:\n%s", params);
    CompileAQL.compile(params);

    // Instantiate the resulting operator graph.
    OperatorGraph og = OperatorGraph.createOG(new String[] {Constants.GENERIC_MODULE_NAME},
        moduleURI, null, this.getTokenizerConfig());
    TupleSchema docSchema = og.getDocumentSchema();
    TextGetter labelGetter = docSchema.textAcc("label");
    Map<String, TupleSchema> outputViews = og.getOutputTypeNamesAndSchema();
    ToCSVOutput outCSV = new ToCSVOutput(outputViews, getCurOutputDir());

    DocReader docs = new DocReader(docsFile, docSchema, null);

    while (docs.hasNext()) {
      Tuple docTup = docs.next();
      Text label = labelGetter.getVal(docTup);

      Map<String, TupleList> annotations = og.execute(docTup, null, null);
      outCSV.write(label.getText(), annotations);
    }

    outCSV.close();

  }

}
