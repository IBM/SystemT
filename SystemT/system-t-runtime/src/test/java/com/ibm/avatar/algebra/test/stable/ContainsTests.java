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
/**
 * 
 */
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
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.logging.Log;

/**
 * Tests for defect SystemT Runtime: Contains(Text, Span) does not work as expected
 * 
 */
public class ContainsTests extends RuntimeTestHarness {


  /**
   * @param args
   */
  public static void main(String[] args) {
    // TODO Auto-generated method stub

  }

  @Before
  public void setUp() throws Exception {
    setDataPath(TestConstants.TESTDATA_DIR);

    // Renice the current thread to avoid locking up the entire system.
    Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

  }

  @After
  public void tearDown() {
    System.gc();
  }

  /**
   * Test 1 for original reported issue.
   * 
   * @throws Exception
   */
  @Test
  public void defect98984Test1() throws Exception {

    final File aqlFile = new File(TestConstants.AQL_DIR + "/ContainsTests/defect98984Test1.aql");
    final String dataPath = null;
    final File docsFile =
        new File(TestConstants.TEST_DOCS_DIR + "/ContainsTests/defect98984Test1.del");

    startTest();
    genericTest(aqlFile, dataPath, docsFile);
    compareAgainstExpected(false);
    endTest();
  }


  /**
   * Test 2 for original reported issue.
   * 
   * @throws Exception
   */
  @Test
  public void defect98984Test2() throws Exception {

    final File aqlFile = new File(TestConstants.AQL_DIR + "/ContainsTests/defect98984Test2.aql");
    final String dataPath = null;
    final File docsFile =
        new File(TestConstants.TEST_DOCS_DIR + "/ContainsTests/defect98984Test2.del");

    startTest();
    genericTest(aqlFile, dataPath, docsFile);
    compareAgainstExpected(false);
    endTest();
  }

  /**
   * Test the ContainsText operator
   * 
   * @throws Exception
   */
  @Test
  public void containsTest() throws Exception {

    final File aqlFile = new File(TestConstants.AQL_DIR + "/ContainsTests/containsTest.aql");
    final String dataPath = null;
    final File docsFile = new File(TestConstants.TEST_DOCS_DIR + "/ContainsTests/containsTest.del");

    startTest();
    genericTest(aqlFile, dataPath, docsFile);
    compareAgainstExpected(false);
    endTest();
  }

  /**
   * Test the ContainsText operator
   * 
   * @throws Exception
   */
  @Test
  public void containsTextTest() throws Exception {

    final File aqlFile = new File(TestConstants.AQL_DIR + "/ContainsTests/containsTextTest.aql");
    final String dataPath = null;
    final File docsFile =
        new File(TestConstants.TEST_DOCS_DIR + "/ContainsTests/containsTextTest.del");

    startTest();
    genericTest(aqlFile, dataPath, docsFile);
    compareAgainstExpected(false);
    endTest();
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
