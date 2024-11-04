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
package com.ibm.avatar.provenance.experiments;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.datamodel.Triple;
import com.ibm.avatar.algebra.function.scalar.AutoID;
import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.api.CompileAQL;
import com.ibm.avatar.api.CompileAQLParams;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.DocReader;
import com.ibm.avatar.aql.planner.Planner;
import com.ibm.avatar.aql.planner.Planner.ImplType;
import com.ibm.avatar.aql.tam.TAM;
import com.ibm.avatar.aql.tam.TAMSerializer;
import com.ibm.avatar.logging.Log;
import com.ibm.avatar.provenance.AQLProvenanceRewriter;
import com.ibm.avatar.provenance.AQLRefine;
import com.ibm.avatar.provenance.RefineEvaluator;
import com.ibm.avatar.provenance.RefinerConstants;

public class AutoRefineTest extends RuntimeTestHarness {

  private static final String CHANGE_SUMMARY_FILE_NAME = "ChangeSummary.txt";

  // For person14.aql

  // public static final String AQL_FILES_DIR = TestConstants.AQL_DIR + "/refineWebTests";
  // public static final String DICTS_DIR = AQL_FILES_DIR + "/dictionaries";
  // public static final String UDF_DIR = AQL_FILES_DIR + "/dictionaries";
  //
  // For user study query

  // public static String AQL_FILES_DIR = TestConstants.AQL_DIR +
  // "/refineWebTests/personPhoneEnron";
  public static String AQL_FILES_DIR = TestConstants.AQL_DIR + "/refineTests";
  public static String DICTS_DIR = AQL_FILES_DIR + "/GenericNE/dictionaries";
  public static String UDF_DIR = AQL_FILES_DIR + "/GenericNE/udfjars";

  // for ACE and CoNLL

  // public static String AQL_FILES_DIR = TestConstants.AQL_DIR + "/refineWebTests/ACE";
  // public static String DICTS_DIR = AQL_FILES_DIR + "/GenericNE/dictionaries";
  // public static String UDF_DIR = AQL_FILES_DIR + "/GenericNE/udfjars";

  // For personPhoneCandidates.aql

  // public static final String AQL_FILES_DIR = TestConstants.AQL_DIR +
  // "/refineWebTests/personPhoneEnron";
  // public static final String DICTS_DIR = AQL_FILES_DIR + "/GenericNE/dictionaries";
  // public static final String UDF_DIR = AQL_FILES_DIR + "/GenericNE/udfjars";
  // //
  //
  // /** Directory where regression test results for this class go. */
  // public static final String OUTPUT_DIR = TestUtils.DEFAULT_OUTPUT_DIR + "/refineWebTests";
  //
  // /** Corresponding directory for holding expected test results. */
  // public static final String EXPECTED_DIR = TestUtils.DEFAULT_EXPECTED_DIR + "/refineWebTests";

  public static void main(String[] args) {
    try {

      AutoRefineTest t = new AutoRefineTest();

      t.setUp();

      long startMS = System.currentTimeMillis();

      t.personSigmodDemoTest();

      long endMS = System.currentTimeMillis();

      t.tearDown();

      double elapsedSec = (endMS - startMS) / 1000.0;

      System.err.printf("Test took %1.3f sec.\n", elapsedSec);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Scan over the Enron database; set up by setUp() and cleaned out by tearDown()
   */
  // private String docLocation;
  // private int tupleID;
  private final boolean runProvQuery = false;
  private ArrayList<Triple<String, Integer, String>> labeledResult = null;
  // private String labelText = null;
  private String currentView = null;
  private final String DATA_DIR = "testdata/docs/aqlRefineTest";
  private String TRAINING_DATA = null;
  private String TRAINING_STANDARD = null;

  @Before
  public void setUp() throws Exception {

    AQLProvenanceRewriter.debug = true;

    // Renice the current thread to avoid locking up the entire system.
    Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

    // scan = new ZipFileScan(TestConstants.ENRON_SMALL_ZIP);

    // docLocation = TestConstants.ENRON_SMALL_ZIP;

    setDataPath(AQL_FILES_DIR, DICTS_DIR, UDF_DIR);

    // For now, don't put any character set information into the header of
    // our output HTML.
    setWriteCharsetInfo(false);
    setDisableOutput(false);
    setPrintTups(true);

    // Make sure that we don't dump query plans unless a particular test
    // requests it.
    setDumpPlan(false);

    // Make sure our log messages get printed!
    Log.enableAllMsgTypes();
  }

  @After
  public void tearDown() {
    AQLProvenanceRewriter.debug = false;

    // Deregister the Derby driver so that other tests can connect to other
    // databases. This is probably not necessary (we don't currently use
    // derby), but we do it just in case.
    try {
      DriverManager.getConnection("jdbc:derby:;shutdown=true");
    } catch (SQLException e) {
      // The shutdown command always raises a SQLException
      // See http://db.apache.org/derby/docs/10.2/devguide/
    }
    System.gc();

    Log.info("Done.");
  }

  /**
   * Test case for the rewrite of the SELECT statement
   */
  @Test
  public void selectTest() throws Exception {
    startTest();
    currentView = "PersonPhoneAll";
    genericTestCase("select");
    // compareAgainstExpected(true);
  }

  public void personSigmodDemoTest() throws Exception {
    startTest();

    currentView = "Person";

    TRAINING_DATA = "C:/Laura/workspace-systemt-tools/SigmodDemo/dataset/train-100/data";
    TRAINING_STANDARD = "C:/Laura/workspace-systemt-tools/SigmodDemo/dataset/train-100/label";

    refineTestCase("personPhoneBaseline", false);

    // compareAgainstExpected(true);
  }

  // Use one document to verify the correctness of the refining algorithms
  public void sanityTest() throws Exception {
    startTest();

    currentView = "Person";

    TRAINING_DATA = DATA_DIR + "/refineDebug/data";
    TRAINING_STANDARD = DATA_DIR + "/refineDebug/label";

    refineTestCase("person14", false);
  }

  public void personPhoneEnronTest() throws Exception {
    startTest();

    currentView = "PersonPhone";
    TRAINING_DATA = DATA_DIR + "/PersonPhoneEnronGS/train/data";
    TRAINING_STANDARD = DATA_DIR + "/PersonPhoneEnronGS/train/label";

    refineTestCase("personphoneFilter", false);
  }

  public void personACETest() throws Exception {
    startTest();

    File outFile = new File("LLC.txt");
    outFile.delete();

    currentView = "Person";
    TRAINING_DATA = DATA_DIR + "/ace2005-clean/train/data";
    TRAINING_STANDARD = DATA_DIR + "/ace2005-clean/train/label";

    // TRAINING_DATA = DATA_DIR + "/CoNLL2003/CoNLL2003trainingDoc";
    // TRAINING_STANDARD = DATA_DIR + "/CoNLL2003/CoNLL2003trainingAnnotation";

    System.out.printf("Running personACETest\n");
    // refineTestCase("personphonecandidates", false);
    AQLRefine.setDEBUG(false);
    // RefineEvaluator.setPOSITION_SHIFT(1);
    AQLRefine.setPOSITION_SHIFT(1);
    // AQLRefine.setNUM_ITERATIONS(10);
    refineTestCase("person14_expert_study_baseline", false);

    // genericTestCase("personBase");
    // personBaseExtraction-simple
    // compareAgainstExpected(true);
  }

  public void personCoNLLTest() throws Exception {
    startTest();

    File outFile = new File("LLC.txt");
    outFile.delete();

    System.out.printf("Running personCoNLL test\n");
    currentView = "Person";
    TRAINING_DATA = DATA_DIR + "/CoNLL2003-clean/train/data";
    TRAINING_STANDARD = DATA_DIR + "/CoNLL2003-clean/train/label";

    // TRAINING_DATA = DATA_DIR + "/CoNLL2003/CoNLL2003trainingDoc";
    // TRAINING_STANDARD = DATA_DIR + "/CoNLL2003/CoNLL2003trainingAnnotation";

    AQLRefine.setDEBUG(false);
    // refineTestCase("personphonecandidates", false);
    // RefineEvaluator.setPOSITION_SHIFT(1);
    AQLRefine.setPOSITION_SHIFT(1);
    refineTestCase("person14_expert_study_baseline", true);

    // genericTestCase("personBase");
    // personBaseExtraction-simple
    // util.compareAgainstExpected(true);
  }

  public void personCoNLLCrossValidation() throws Exception {
    currentView = "Person";
    File outFile = new File(CHANGE_SUMMARY_FILE_NAME);
    outFile.delete();

    FileWriter cvOut;
    RefineEvaluator.setPOSITION_SHIFT(1);
    // AQLRefine.setNUM_ITERATIONS(2);

    for (int i = 0; i < 1; i++) {
      System.out.printf("Running cross-validation iteration %d\n", i);
      cvOut = new FileWriter(CHANGE_SUMMARY_FILE_NAME, true);
      cvOut.append("Cross validation set No. " + i + ": \n");
      cvOut.close();
      TRAINING_DATA = DATA_DIR + "/CoNLLCrossValidation/train/data_" + i;
      TRAINING_STANDARD = DATA_DIR + "/CoNLLCrossValidation/train/label_" + i;
      refineTestCase("", true);
    }
  }

  public void personSelfLabelCrossValidation() throws Exception {
    currentView = "Person";
    File outFile = new File(CHANGE_SUMMARY_FILE_NAME);
    outFile.delete();

    FileWriter cvOut;
    RefineEvaluator.setPOSITION_SHIFT(0);
    AQLRefine.setDEBUG(false);

    for (int i = 1; i < 3; i++) {
      System.out.printf("Running cross-validation iteration %d\n", i);
      cvOut = new FileWriter(CHANGE_SUMMARY_FILE_NAME, true);
      cvOut.append("Cross validation set No. " + i + ": \n");
      cvOut.close();
      TRAINING_DATA = DATA_DIR + "/PersonSelfLabelCrossValidation/train/data_" + i;
      TRAINING_STANDARD = DATA_DIR + "/PersonSelfLabelCrossValidation/train/label_" + i;
      refineTestCase("person14", true);
    }
  }

  /**
   * Test why reading dictionaries leads to error "Null".
   */
  public void dictTest() throws Exception {
    startTest();

    currentView = "Fortune1000";
    TRAINING_DATA = DATA_DIR + "/test1/training-data";
    TRAINING_STANDARD = DATA_DIR + "/test1/training-standard";

    refineTestCase("dictTest", false);
  }

  public void simpleDictTest() throws Exception {
    startTest();

    // tupleID = 818;
    currentView = "Person";
    genericTestCase("simpleDict");
    // compareAgainstExpected(true);
  }

  public void personPhoneTest() throws Exception {
    startTest();

    currentView = "PersonPhone";
    TRAINING_DATA = DATA_DIR + "/personphone-test/training/data";
    TRAINING_STANDARD = DATA_DIR + "/personphone-test/training/gs";
    refineTestCase("PersonPhone-complex", false);
  }

  public void filterDictTest() throws Exception {
    startTest();

    // tupleID = 818;
    currentView = "PersonPhoneAll1";
    genericTestCase("filterDict");
    // compareAgainstExpected(true);
  }

  @Test
  public void unionTest() throws Exception {
    startTest();

    // tupleID = 230;
    genericTestCase("union");
    // compareAgainstExpected(true);
  }

  @Test
  public void subqueryTest() throws Exception {
    startTest();

    // tupleID = 797;
    genericTestCase("subquery");
    // compareAgainstExpected(true);
  }

  @Test
  public void consolidateTest() throws Exception {
    startTest();

    // tupleID = 828;
    genericTestCase("consolidate");
    // compareAgainstExpected(true);
  }

  @Test
  public void minusTest() throws Exception {
    startTest();

    // tupleID = 829;
    genericTestCase("minus");
    // util.compareAgainstExpected(true);
  }

  /**
   * Temp hack: URL is too long to be used in the webGUI. This the URL is genearted from web GUI.
   */
  public ArrayList<Triple<String, Integer, String>> extractLabels(String labelResult) {

    if (!labelResult.contains("Incorrect")) {
      Log.debug("Error! No negative results");
      return null;
    }

    String docIDDelimiter = "__";
    String tupleDelimiter = "____";
    String idChoiceDelimiter = "___";

    ArrayList<Triple<String, Integer, String>> result =
        new ArrayList<Triple<String, Integer, String>>();
    String[] tupleChoices = labelResult.split(tupleDelimiter);
    for (int i = 0; i < tupleChoices.length; i++) {
      String docLabelID = tupleChoices[i].split(idChoiceDelimiter)[0];
      String choice = tupleChoices[i].split(idChoiceDelimiter)[1];
      Triple<String, Integer, String> entry =
          new Triple<String, Integer, String>(docLabelID.split(docIDDelimiter)[0],
              Integer.parseInt(docLabelID.split(docIDDelimiter)[1]), choice);
      result.add(entry);
    }
    return result;
  }

  public void refineFromWebTest() throws Exception {

  }

  /**
   * Version of {@link #genericTestCase(String, ImplType)} that uses the default planner
   * implementation.
   */
  private void genericTestCase(String prefix) throws Exception {
    genericTestCase(prefix, Planner.DEFAULT_IMPL_TYPE);
  }

  /**
   * Set this flag to TRUE to make {@link #genericTestCase(String, ImplType)} print out query plans
   * to STDERR.
   */
  private final boolean dumpAOG = false;

  /**
   * A generic AQL test case. Takes a prefix string as argument; runs the file prefix.aql and sends
   * output to testdata/regression/output/prefix. Also dumps the generated AOG plan to a file in the
   * output directory.
   * 
   * @param implType what implementation of the planner to use to compile the AQL
   */
  private void genericTestCase(String prefix, ImplType implType) throws Exception {

    File INPUT_AQLFILE = new File(String.format("%s/%s.aql", AQL_FILES_DIR, prefix));
    File REWRITTEN_AQLFILE =
        new File(String.format("%s/%s_Rewrite.aql", getCurOutputDir(), prefix));

    String DATA_DIR = "testdata/docs/refineTest";

    String TRAINING_DATA = DATA_DIR + "/test1/training-data";
    String TRAINING_STANDARD = DATA_DIR + "/test1/training-standard";

    // String viewName = "PersonBase";
    boolean askForInput = true;
    // mimic user labeled result.
    File labelFile = new File(String.format("%s/%s_label.txt", AQL_FILES_DIR, prefix));
    // labelText = "labelSample.txt";

    // Rewrite the input AQL
    AutoID.resetIDCounter();

    AQLRefine refiner = new AQLRefine();

    // refiner.setDictPath(util.getDictionaryPath());
    // refiner.setUDFJarPath(util.getUDFJarPath());

    BufferedReader reader = new BufferedReader(new FileReader(INPUT_AQLFILE));
    String line = null;
    String inputAQL = "";
    while ((line = reader.readLine()) != null) {
      inputAQL += line + "\n";
    }
    reader.close();

    // File docsDir = new File(TestConstants.DUMPS_DIR);
    // String docCollectionName = "ensmall.zip";
    //
    /*
     * get labeledResult from a file. File content is copied from the content of "labeledResult"
     * variable in web GUI under debug mode. So it's only one line.
     */
    // labeledResult = new ArrayList<Triple<String, Integer, String>>();

    try {
      BufferedReader reader2 = new BufferedReader(new FileReader(labelFile));
      String line2 = reader2.readLine();
      labeledResult = extractLabels(line2);
      reader2.close();
    } catch (Exception e) {
      e.printStackTrace();
    }

    if (labeledResult == null) {
      System.out.print("No negative result to remove. End.\n");
      return;
    }

    DocReader docs = new DocReader(new File(TRAINING_DATA));
    RefineEvaluator eval =
        new RefineEvaluator(inputAQL, currentView, docs, getDataPath(), null, null);

    eval.readGoldenStandard(TRAINING_STANDARD);

    eval.getResultLabels();

    // output the initial Fscore
    double beta = refiner.getDoubleProperty(RefinerConstants.REFINER_BETA_PROP);

    double initF = trueFMeasure(eval.getActualPos().size(), eval.getActualNeg().size(),
        eval.getTotalNumberOfResults(), beta);
    eval.setInitialFscore(initF);
    // above is the initial setting before user chooses to apply any change.

    /*************************************************************************************************/
    if (runProvQuery) {

      // run the rewritten file to see the output
      AQLProvenanceRewriter rewriter = new AQLProvenanceRewriter();

      String dataPathStr = getDataPath();

      // FIXME: Now that outputFile is removed from CompileAQLParams, AQLProvenanceRewriter should
      // use alternate way of
      // passing rewrittenAQLFile parameter to rewriteAQL() method. Temporarily passing some
      // arbitrary value to allow
      // compiler to succeed.
      CompileAQLParams params = new CompileAQLParams(INPUT_AQLFILE, null, dataPathStr);
      rewriter.rewriteAQL(params, refiner.getBaseViews());

      refiner.addBaseViews(rewriter.getBaseViews());

      refiner.autoRefine(inputAQL, null, currentView, getDataPath(), null, null, TRAINING_DATA,
          eval, askForInput, System.err);

      // Parse the rewritten AQL.
      Log.info("Compiling rewritten AQL file '%s'", REWRITTEN_AQLFILE.getPath());

      // Prepare compile parameter
      CompileAQLParams compileParam = new CompileAQLParams(REWRITTEN_AQLFILE, null, dataPathStr);
      compileParam.setInputFile(REWRITTEN_AQLFILE);

      // Compile
      CompileAQL.compile(compileParam);

      // load TAM
      TAM tam = TAMSerializer.load(Constants.GENERIC_MODULE_NAME, compileParam.getOutputURI());

      String aog = tam.getAog();

      // Dump the plan to a file in the output directory.
      File planFile = new File(getCurOutputDir(), "plan.aog");
      Log.info("Dumping plan to %s", planFile);
      FileWriter aogOut = new FileWriter(planFile);
      aogOut.append(aog);
      aogOut.close();

      if (dumpAOG) {
        System.err.print("-----\nAOG plan is:\n" + aog);
      }

      // Try running the plan.
      try {
        runAOGString(new File(TRAINING_DATA), aog, null);
        // util.runAOGStr (aog);
      } catch (Exception e) {
        System.err.printf("Caught exception while parsing AOG; " + "original AOG plan was:\n%s\n",
            aog);
        throw new RuntimeException(e);
      }
    }
  }

  private void refineTestCase(String aql, boolean isCrossValidation) throws Exception {

    File INPUT_AQLFILE = new File(String.format("%s/%s.aql", AQL_FILES_DIR, aql));

    System.out.printf("Refining %s.aql", aql);
    // String viewName = "PersonBase";
    boolean askForInput = true;

    // Rewrite the input AQL
    AutoID.resetIDCounter();

    FileWriter timeFile = new FileWriter("time.txt", true);

    AQLRefine refiner = new AQLRefine();
    refiner.setProperty(RefinerConstants.REFINER_CROSS_VALIDATION_PROP,
        Boolean.toString(isCrossValidation));

    String dataPathStr = getDataPath();

    Log.info("Data path is: %s", dataPathStr);
    CompileAQLParams params = new CompileAQLParams(INPUT_AQLFILE, null, dataPathStr);

    AQLProvenanceRewriter rewriter = new AQLProvenanceRewriter();

    // TODO: Laura, can you explain this warning? -- eyhung
    @SuppressWarnings("deprecation")
    String rewrittenAQL = rewriter.rewriteAQLToStr(params, refiner.getBaseViews());

    refiner.getBaseViews().addAll(rewriter.getBaseViews());

    DocReader docs = new DocReader(FileUtils.createValidatedFile(TRAINING_DATA));
    RefineEvaluator eval =
        new RefineEvaluator(rewrittenAQL, currentView, docs, getDataPath(), null, null);

    eval.readGoldenStandard(TRAINING_STANDARD);

    long startMS = System.currentTimeMillis();
    timeFile.append("This fold starts at " + startMS);

    AQLRefine.setStartTimeMS(startMS);

    refiner.autoRefine(rewrittenAQL, rewriter, currentView, getDataPath(), null, null,
        TRAINING_DATA, eval, askForInput, System.err);

    long endMS = System.currentTimeMillis();

    double elapsedSec = (endMS - startMS) / 1000.0;

    timeFile.append("Whole fold took " + elapsedSec + "\n");
    timeFile.close();

    refiner = null;
  }

  @SuppressWarnings("unused")
  @Deprecated
  private void labelData() {
    File INPUT_AQLFILE = new File(String.format("%s/%s", AQL_FILES_DIR, "personphone_demo3.aql"));

    String DATA_DIR = "testdata/docs/refineTest/ensample";

    String viewName = "PersonPhone";
    String colName = "personphone";

    String OUT_DIR = "testdata/docs/refineTest/ensample-label";

    // Rewrite the input AQL
    AutoID.resetIDCounter();

    AQLRefine refiner = new AQLRefine();

    try {
      BufferedReader reader = new BufferedReader(new FileReader(INPUT_AQLFILE));
      String line = null;
      String inputAQL = "";
      while ((line = reader.readLine()) != null) {
        inputAQL += line + "\n";
      }
      reader.close();

      // DocReader docs = new DocReader(new File(DATA_DIR));
      // RefineEvaluator eval = new RefineEvaluator(inputAQL, viewName, docs, util.getIncludePath(),
      // util.getDictionaryPath(), util.getUDFJarPath());
      // eval.labelData(inputAQL, docs, viewName, colName, OUT_DIR);

      runAQL(inputAQL);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void runAQL(String aql) throws Exception {

    // String OUTPUT_DIR = "testdata/docs/refineTest/ensample-output";

    // docLocation = TestConstants.ENRON_SMALL_ZIP;

    // For now, don't put any character set information into the header of
    // our output HTML.
    setWriteCharsetInfo(false);
    setDisableOutput(false);
    setPrintTups(true);

    // Compile the rewritten AQL
    File inputFile = null;
    CompileAQLParams compileParam = new CompileAQLParams(inputFile, null, DICTS_DIR);
    compileParam.setInputStr(aql);
    CompileAQL.compile(compileParam);

    TAM tam = TAMSerializer.load(Constants.GENERIC_MODULE_NAME, compileParam.getOutputURI());
    String aog = tam.getAog();

    // Dump the plan to a file in the output directory.
    File planFile = new File(getCurOutputDir(), "plan.aog");
    Log.info("Dumping plan to %s", planFile);
    FileWriter aogOut = new FileWriter(planFile);
    aogOut.append(aog);
    aogOut.close();

    if (dumpAOG) {
      System.err.print("-----\nAOG plan is:\n" + aog);
    }

    // Try running the plan.
    try {
      // util.runAOGStr (aog);
      runAOGString(FileUtils.createValidatedFile(TRAINING_DATA), aog, null);
    } catch (Exception e) {
      System.err.printf("Caught exception while parsing AOG; " + "original AOG plan was:\n%s\n",
          aog);
      throw new RuntimeException(e);
    }
  }

  private double trueFMeasure(int pos, int neg, int total, double beta) {
    double result;
    double P, R;

    P = (double) pos / (double) total;
    R = (double) pos / (double) (pos + neg);

    // Formula according to P156, Stanford IR book.
    result = (beta * beta + 1) * P * R / (beta * beta * P + R);

    return result;
  }
}
