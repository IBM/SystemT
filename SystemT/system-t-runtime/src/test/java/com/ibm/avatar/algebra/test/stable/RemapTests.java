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
import com.ibm.avatar.algebra.datamodel.TextSetter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.util.document.ToCSVOutput;
import com.ibm.avatar.algebra.util.file.SearchPath;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.api.CompileAQL;
import com.ibm.avatar.api.CompileAQLParams;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.DocReader;
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.logging.Log;

/**
 *
 */
public class RemapTests extends RuntimeTestHarness {


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
   * Test on how remap function handles detagged document
   * 
   * @throws Exception
   */
  @Test
  public void basicTest() throws Exception {

    final File aqlFile = new File(TestConstants.AQL_DIR + "/RemapTests/basicTest.aql");

    final String dataPath = null;

    final File docsFile = new File(TestConstants.TEST_DOCS_DIR + "/RemapTests/basicTest.del");

    startTest();

    genericTest(aqlFile, dataPath, docsFile);

    compareAgainstExpected(false);

    endTest();
  }

  /**
   * Test on how remap function handles detagged document
   */
  @Test
  public void edaTest() throws Exception {

    final File aqlFile = new File(TestConstants.AQL_DIR,
        "ExtractorLibrary/aql/eDA/ne-ediscovery-personorgphoneaddress.aql");

    String includePath = TestConstants.AQL_DIR + "/ExtractorLibrary/aql";
    String dictPath = TestConstants.AQL_DIR + "/ExtractorLibrary/aql/core/GenericNE/dictionaries";
    String dataPath = String.format("%s%c%s", includePath, SearchPath.PATH_SEP_CHAR, dictPath);

    final File docsFile = new File(TestConstants.TEST_DOCS_DIR, "/RemapTests/basicTest.del");

    startTest();

    genericTest(aqlFile, dataPath, docsFile);

    compareAgainstExpected(false);

    endTest();
  }

  /**
   * Test on how remap function handles detagged document with text reset by a TextSetter
   * 
   * @throws Exception
   */
  @Test
  public void basicResetTextTest() throws Exception {

    final File aqlFile = new File(TestConstants.AQL_DIR + "/RemapTests/basicTest.aql");

    final String dataPath = null;

    final File docsFile = new File(TestConstants.TEST_DOCS_DIR + "/RemapTests/basicTest.del");

    String str = "IBM acquired Cognos";

    startTest();

    genericTestWithReset(aqlFile, dataPath, docsFile, str);

    compareAgainstExpected(false);

    endTest();
  }

  /**
   * Test on how remap function handles detagged document with text reset by a TextSetter
   */
  @Test
  public void edaResetTextTest() throws Exception {

    final File aqlFile = new File(TestConstants.AQL_DIR,
        "ExtractorLibrary/aql/eDA/ne-ediscovery-personorgphoneaddress.aql");

    String includePath = TestConstants.AQL_DIR + "/ExtractorLibrary/aql";
    String dictPath = TestConstants.AQL_DIR + "/ExtractorLibrary/aql/core/GenericNE/dictionaries";
    String dataPath = String.format("%s%c%s", includePath, SearchPath.PATH_SEP_CHAR, dictPath);

    final File docsFile = new File(TestConstants.TEST_DOCS_DIR, "/RemapTests/basicTest.del");

    String str = "IBM acquired Cognos";

    startTest();

    genericTestWithReset(aqlFile, dataPath, docsFile, str);

    compareAgainstExpected(false);

    endTest();
  }

  /**
   * Test on how remap function handles not detagged document
   * 
   * @throws Exception
   */
  @Test
  public void basicNoDetagTest() throws Exception {

    final File aqlFile = new File(TestConstants.AQL_DIR + "/RemapTests/basicNoDetagTest.aql");

    final String dataPath = null;

    final File docsFile = new File(TestConstants.TEST_DOCS_DIR + "/RemapTests/basicTest.del");

    startTest();

    genericTest(aqlFile, dataPath, docsFile);

    compareAgainstExpected(false);

    endTest();
  }

  /**
   * Test on how remap function handles document with text reset by a TextSetter
   */
  @Test
  public void remapBugTextTestOrig() throws Exception {

    File aqlFile = new File(TestConstants.AQL_DIR,
        "ExtractorLibrary/aql/eDA/ne-ediscovery-personorgphoneaddress.aql");

    String includePath = TestConstants.AQL_DIR + "/ExtractorLibrary/aql";
    String dictPath = TestConstants.AQL_DIR + "/ExtractorLibrary/aql/core/GenericNE/dictionaries";
    String dataPath = String.format("%s%c%s", includePath, SearchPath.PATH_SEP_CHAR, dictPath);

    final File docsFile = new File(TestConstants.TEST_DOCS_DIR, "/aogBugTests/samplehtml.zip");

    startTest();

    setDumpPlan(false);
    setDisableOutput(false);
    setPrintTups(false);
    setDataPath(dataPath);
    runNonModularAQLTest(docsFile, aqlFile);

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

  /**
   * @param aqlFile
   * @param dataPath
   * @param docsFile
   * @param str
   * @throws Exception
   */
  private void genericTestWithReset(File aqlFile, String dataPath, File docsFile, String str)
      throws Exception {

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
    TextSetter textSetter = docSchema.textSetter(Constants.DOCTEXT_COL);
    TextGetter textGetter = docSchema.textAcc(Constants.DOCTEXT_COL);
    TextGetter labelGetter = docSchema.textAcc("label");
    Map<String, TupleSchema> outputViews = og.getOutputTypeNamesAndSchema();
    ToCSVOutput outCSV = new ToCSVOutput(outputViews, getCurOutputDir());

    DocReader docs = new DocReader(docsFile, docSchema, null);

    while (docs.hasNext()) {
      Tuple docTup = docs.next();
      Text label = labelGetter.getVal(docTup);

      Text docText = textGetter.getVal(docTup);
      System.out.println(docText);

      // set the input document value to the given string
      textSetter.setVal(docTup, str);

      docText = textGetter.getVal(docTup);
      System.out.println(docText);

      Map<String, TupleList> annotations = og.execute(docTup, null, null);
      outCSV.write(label.getText(), annotations);
    }

    outCSV.close();

  }

}
