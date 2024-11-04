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

package com.ibm.avatar.provenance;

// AQL Evaluator for PersonPhone DataSet

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.TextSetter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.scan.DocScanInternal;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.DocReader;
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.aql.ParseException;

/**
 * This class is used to run basic regression test for annotator development Input: 1. Input
 * document in XML with the following format: <content> <text id="1"> .......... </text>
 * <text id="2"> .......... </text> ........ <text id="k"> .......... </text> </content> 2. Expected
 * annotations for the input document <annotations> <text id="1"> <annotationType>
 * <begin>...</begin> <end>...</end> <annotation>...</annotation> </annotationType> ...
 * <annotationType>...</annotationType> </text> <text id="2"> <annotationType>...</annotationType>
 * ... <annotationType>...</annotationType> </text> .... <text id="k">
 * <annotationType>...</annotationType> ... <annotationType>...</annotationType> </text>
 * </annotations> NOTE: for now, we assume every text element in the input document has
 * corresponding text element in the expected annotations, which can be empty if no annotation is
 * expected to be found. Output: 1. Actual annotations output for the input document 2.
 * Precision/Recall numbers 3. Missing/Mismatch annotations between the expected and actual
 * annotations TODO: 1. allow assignment of annotator within the tester 2. allow more precision
 * expected annotations, including the position of the span (this requires tool for creating golden
 * standard)
 * 
 */
public class AQLEvaluator2 extends RuntimeTestHarness {

  // Type of input documents: XML, or plain text
  private enum FILE_TYPE {
    XML, TEXT
  }

  // Type of input docs, for ACE and CoNLL gold standards
  // private final static FILE_TYPE INPUT_DOC_TYPE = FILE_TYPE.XML;
  // Type of input docs, for Enron gold standard

  private final static FILE_TYPE INPUT_DOC_TYPE = FILE_TYPE.TEXT;

  // This is used to counter the shift in positions in input text and label due to conversion.
  private static int POSITION_SHIFT = 0;

  private boolean PRINT_DETAILS = false;

  private boolean IS_CLEANING_DATA = false;
  private static String CORRECTED_LABEL_DIR = "";

  private final static String TEXT_ENTRY = "text";
  private final static String BEGIN = "start";
  private final static String END = "end";
  private final static String ANNOTATION = "annotation";

  // constants corresponding to different relationship between the positions of expected and actual
  // annotation spans
  private final static int BEFORE = 3;
  private final static int AFTER = 2;
  private final static int OVERLAP_BUT_NOT_EQUAL = 1;
  private final static int EQUAL = 0;

  // private final static String TYPE_PREFIX = "com.ibm.SystemT.";
  private final static String TYPE_PREFIX = "";

  // Types of annotations we care about in the test
  // private final static String[] ANNOTATION_TYPES = {"Person", "Organization", "Location"};
  private final static String[] ANNOTATION_TYPES = {"Person"};
  // private final static String[] ANNOTATION_TYPES = {"PersonPhone"};

  private static String DICTIONARY_DIR = "testdata/aql/refineWebTests/dictionaries";
  private static String INCLUDE_PATH = null;
  private static String QUERY_DIR = "testdata/aql/refineWebTests";
  // private static final String INIT_QUERY_FILE = QUERY_DIR + "/personBaseTest.aql";
  // private static final String INIT_QUERY_FILE = QUERY_DIR + "/simpleDictTest.aql";
  // private static final String INIT_QUERY_FILE = QUERY_DIR + "/personBaseExtraction-simple.aql";
  private static String INIT_QUERY_FILE = QUERY_DIR + "/PersonPhone-complex.aql";
  // private static final String REFINED_QUERY_FILE = QUERY_DIR + "/test1_refine123.aql";
  private static String REFINED_QUERY_FILE = QUERY_DIR + "/simpleDictTest_refine1.aql";

  private boolean runRefined = false; // set whether to run original or refined query.

  // private static final String QUERY_FILE = QUERY_DIR + "/ne-library-annotators.aql";
  private static String DATA_DIR = "testdata/docs/aqlRefineTest";
  public static String UDF_DIR = "testdata";

  /** Directories where output files from tests in this class go. */
  // private static final String OUTPUT_DIR = "temp/aqltestout";

  // private static final String IN_DIR = DATA_DIR + "/ace2005testingDoc";
  // private static final String IN_DIR = DATA_DIR + "/ace2005trainingDoc";
  // private static final String EXPECTED_DIR = DATA_DIR + "/ace2005trainingAnnotation";
  // private static final String EXPECTED_DIR = DATA_DIR + "/ace2005testingAnnotation";
  // private static final String IN_DIR = DATA_DIR + "/CoNLL2003trainingDoc";
  // private static final String EXPECTED_DIR = DATA_DIR + "/CoNLL2003trainingAnnotation";

  private static String IN_DIR = DATA_DIR + "/Enron-personphone/Enron-personphone-data";
  private static String EXPECTED_DIR = DATA_DIR + "/Enron-personphone-gs";

  // private String TRAINING_DATA = DATA_DIR + "/personphone-test/training/data";
  // private String TRAINING_STANDARD = DATA_DIR + "/personphone-test/training/gs";

  private String TESTING_DATA = DATA_DIR + "/PersonPhoneEnronGS/test/data";
  private String TESTNING_STANDARD = DATA_DIR + "/PersonPhoneEnronGS/test/label";

  @SuppressWarnings("unused")
  private String CROSS_VALIDATION_OUT = "CrossValidationResult.txt";
  // private ArrayList<SimpleSpan> missingAnnotations = new ArrayList<SimpleSpan>();

  private DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

  // counters used to store statistics of comparison results for each type
  private int[][] counters;

  // stores results before and after refinement

  public static void main(String[] args) throws Exception {
    AQLEvaluator2 tester = new AQLEvaluator2();
    // tester.testPersonPhoneEnronBefore();
    // tester.testPersonPhoneEnronAfter();
    // tester.personCoNLLCrossValidation();
    // tester.personACECrossValidation();
    tester.personACETest();
    // tester.personphoneEnronCrossValidation();
    // tester.speedTest();
    // tester.personRefinement();
    // tester.cleaningPersonPhoneEnron();
    // tester.cleaningPersonEnron();
    // tester.personSelfLabelCrossValidation();
    // tester.verifyPersonPhoneCleaning();
    // tester.testPersonACEBefore();
    // tester.testPersonACEAfter();
    // tester.testPersonCoNLLBefore();
    // tester.testPersonCoNLLAfter();
    // tester.testPersonSimple();
    // tester.personSelfLabelBeforeRefine();
    // tester.personSelfLabelAfterRefine();
    // tester.testPerson14GSBefore();
    // tester.testPerson14GSAfter();
    // tester.userStudy();
    // tester.testEnronMeetingBefore();
    // tester.testEnronMeetingAfter();
    // tester.runRefined();
    // tester.sanityTest();
    // tester.testPersonMoreFilters();
    // tester.testPersonOnFullData();
    // tester.testlabeledPerson();
    // tester.testPersonPhoneImproved();
    // tester.testPersonPhoneStandard();
    // tester.testPersonLabeledDataInit();
    // tester.testPersonLabeledDataTest();
  }

  // public void testPersonUSBefore() throws Exception{
  // PRINT_DETAILS = false;
  // System.out.println("Person user study before refine...");
  // runRefined = false;
  // DICTIONARY_DIR = "testdata/aql/refineWebTests/userStudy/GenericNE/dictionaries";
  // UDF_DIR = "testdata/aql/refineWebTests/userStudy/GenericNE/udfjars";
  // ANNOTATION_TYPES[0] = "Person";
  // INIT_QUERY_FILE = QUERY_DIR + "/userStudy/person14_expert_study_basedline.aql";
  // IN_DIR = DATA_DIR + ;
  // EXPECTED_DIR = TESTNING_STANDARD;
  // runAQL(null);
  // }

  public void testPersonPhoneEnronAfter() throws Exception {
    PRINT_DETAILS = false;
    System.out.println("PersonPhone after refine...");
    runRefined = false;
    DICTIONARY_DIR = "testdata/aql/refineWebTests/personPhoneEnron/GenericNE/dictionaries";
    UDF_DIR = "testdata/aql/refineWebTests/personPhoneEnron/GenericNE/udfjars";
    ANNOTATION_TYPES[0] = "PersonPhone";
    INIT_QUERY_FILE = QUERY_DIR + "/personPhoneEnron/personphone_Refined.aql";
    IN_DIR = TESTING_DATA;
    EXPECTED_DIR = TESTNING_STANDARD;
    runAQL(null);
  }

  public void testPersonPhoneEnronBefore() throws Exception {
    System.out.println("PersonPhone before refine...");
    PRINT_DETAILS = true;
    runRefined = false;
    ANNOTATION_TYPES[0] = "PersonPhone";
    DICTIONARY_DIR = "testdata/aql/refineWebTests/personPhoneEnron/GenericNE/dictionaries";
    UDF_DIR = "testdata/aql/refineWebTests/personPhoneEnron/GenericNE/udfjars";
    INIT_QUERY_FILE = QUERY_DIR + "/personPhoneEnron/personphonecandidates.aql";
    IN_DIR = DATA_DIR + "/PersonPhoneEnronClean/data";
    EXPECTED_DIR = DATA_DIR + "/PersonPhoneEnronClean/label";
    runAQL(null);
  }

  public void cleaningPersonPhoneEnron() throws Exception {
    // System.out.println("PersonPhone before refine...");
    PRINT_DETAILS = false;
    runRefined = false;
    IS_CLEANING_DATA = true;
    CORRECTED_LABEL_DIR = DATA_DIR + "/EnronCorrected";

    ANNOTATION_TYPES[0] = "PersonPhone";
    DICTIONARY_DIR = "testdata/aql/refineWebTests/personPhoneEnron/GenericNE/dictionaries";
    UDF_DIR = "testdata/aql/refineWebTests/personPhoneEnron/GenericNE/udfjars";
    INIT_QUERY_FILE = QUERY_DIR + "/personPhoneEnron/personphonecandidates.aql";
    IN_DIR = DATA_DIR + "/PersonPhoneEnron/data";
    EXPECTED_DIR = DATA_DIR + "/PersonPhoneEnron/label";;
    runAQL(null);
  }

  public void cleaningPersonEnron() throws Exception {
    // System.out.println("PersonPhone before refine...");
    PRINT_DETAILS = false;
    runRefined = false;
    IS_CLEANING_DATA = true;
    CORRECTED_LABEL_DIR = DATA_DIR + "/EnronPersonCorrected";

    ANNOTATION_TYPES[0] = "Person";
    DICTIONARY_DIR = "testdata/aql/refineWebTests/personPhoneEnron/GenericNE/dictionaries";
    UDF_DIR = "testdata/aql/refineWebTests/personPhoneEnron/GenericNE/udfjars";
    INIT_QUERY_FILE = QUERY_DIR + "/personPhoneEnron/personphonecandidates.aql";
    IN_DIR = DATA_DIR + "/PersonSelfLabel/data";
    EXPECTED_DIR = DATA_DIR + "/PersonSelfLabel/label";;
    runAQL(null);
  }

  public void verifyPersonPhoneCleaning() throws Exception {
    System.out.println("PersonPhone before refine...");
    PRINT_DETAILS = true;
    runRefined = false;
    ANNOTATION_TYPES[0] = "PersonPhone";
    DICTIONARY_DIR = "testdata/aql/refineWebTests/personPhoneEnron/GenericNE/dictionaries";
    UDF_DIR = "testdata/aql/refineWebTests/personPhoneEnron/GenericNE/udfjars";
    INIT_QUERY_FILE = QUERY_DIR + "/personPhoneEnron/personphonecandidates.aql";
    IN_DIR = DATA_DIR + "/verifyCleaning/data";
    EXPECTED_DIR = DATA_DIR + "/verifyCleaning/label";
    runAQL(null);
  }

  /**
   * ACE data contains labeled extraction for person, organization, location
   * 
   * @throws Exception
   */
  public void testPersonACEBefore() throws Exception {
    POSITION_SHIFT = 1; // after shift by 1, F-score = 0.43; before: 0.35
    System.out.println("PersonPhone before refine...");
    PRINT_DETAILS = true;
    runRefined = false;
    ANNOTATION_TYPES[0] = "Person";
    DICTIONARY_DIR = "testdata/aql/refineWebTests/personPhoneEnron/GenericNE/dictionaries";
    UDF_DIR = "testdata/aql/refineWebTests/personPhoneEnron/GenericNE/udfjars";
    INIT_QUERY_FILE = QUERY_DIR + "/person14_3Filters.aql";
    IN_DIR = DATA_DIR + "/ace2005-clean/test/data";
    EXPECTED_DIR = DATA_DIR + "/ace2005-clean/test/label";
    runAQL(null);
  }

  public void testPersonACEAfter() throws Exception {
    POSITION_SHIFT = 1; // after shift by 1, F-score = 0.43; before: 0.35
    System.out.println("PersonPhone after refine...");
    PRINT_DETAILS = true;
    runRefined = false;
    ANNOTATION_TYPES[0] = "Person";
    DICTIONARY_DIR = "testdata/aql/refineWebTests/personPhoneEnron/GenericNE/dictionaries";
    UDF_DIR = "testdata/aql/refineWebTests/personPhoneEnron/GenericNE/udfjars";
    INIT_QUERY_FILE = QUERY_DIR + "/person14_3filters_refined_ace.aql";
    IN_DIR = DATA_DIR + "/ace2005-clean/test/data";
    EXPECTED_DIR = DATA_DIR + "/ace2005-clean/test/label";
    runAQL(null);
  }

  public void testPersonCoNLLBefore() throws Exception {
    POSITION_SHIFT = 1; // after shift by 1, F-score = 0.43; before: 0.35
    System.out.println("Person before refine...");
    PRINT_DETAILS = true;
    runRefined = false;
    ANNOTATION_TYPES[0] = "Person";
    DICTIONARY_DIR = "testdata/aql/refineWebTests/personPhoneEnron/GenericNE/dictionaries";
    UDF_DIR = "testdata/aql/refineWebTests/personPhoneEnron/GenericNE/udfjars";
    INIT_QUERY_FILE = QUERY_DIR + "/person14_3Filters.aql";
    IN_DIR = DATA_DIR + "/CoNLL2003-clean/testA/data";
    EXPECTED_DIR = DATA_DIR + "/CoNLL2003-clean/testA/label";
    runAQL(null);
  }

  public void testPersonCoNLLAfter() throws Exception {
    POSITION_SHIFT = 1; // after shift by 1, F-score = 0.43; before: 0.35
    System.out.println("Person after refine...");
    PRINT_DETAILS = true;
    runRefined = false;
    ANNOTATION_TYPES[0] = "Person";
    DICTIONARY_DIR = "testdata/aql/refineWebTests/personPhoneEnron/GenericNE/dictionaries";
    UDF_DIR = "testdata/aql/refineWebTests/personPhoneEnron/GenericNE/udfjars";
    INIT_QUERY_FILE = QUERY_DIR + "/person14_3Filters_Refined.aql";
    IN_DIR = DATA_DIR + "/CoNLL2003-clean/testA/data";
    EXPECTED_DIR = DATA_DIR + "/CoNLL2003-clean/testA/label";
    runAQL(null);
  }

  public void testPerson14GSBefore() throws Exception {
    POSITION_SHIFT = 0; // after shift by 1, F-score = 0.43; before: 0.35
    System.out.println("Person before refine...");
    PRINT_DETAILS = false;
    runRefined = false;
    ANNOTATION_TYPES[0] = "Person";
    DICTIONARY_DIR = "testdata/aql/refineWebTests/personPhoneEnron/GenericNE/dictionaries";
    UDF_DIR = "testdata/aql/refineWebTests/personPhoneEnron/GenericNE/udfjars";
    INIT_QUERY_FILE = QUERY_DIR + "/person14.aql";
    IN_DIR = DATA_DIR + "/test1/test-data";
    EXPECTED_DIR = DATA_DIR + "/test1/test-standard";
    runAQL(null);
  }

  public void testPerson14GSAfter() throws Exception {
    POSITION_SHIFT = 0; // after shift by 1, F-score = 0.43; before: 0.35
    System.out.println("Person on Enron after refine...");
    PRINT_DETAILS = false;
    runRefined = false;
    ANNOTATION_TYPES[0] = "Person";
    DICTIONARY_DIR = "testdata/aql/refineWebTests/personPhoneEnron/GenericNE/dictionaries";
    UDF_DIR = "testdata/aql/refineWebTests/personPhoneEnron/GenericNE/udfjars";
    INIT_QUERY_FILE = QUERY_DIR + "/person14_Refined.aql";
    IN_DIR = DATA_DIR + "/test1/test-data";
    EXPECTED_DIR = DATA_DIR + "/test1/test-standard";
    runAQL(null);
  }

  public void testEnronMeetingBefore() throws Exception {
    POSITION_SHIFT = 0; // after shift by 1, F-score = 0.43; before: 0.35
    System.out.println("Person before refine...");
    PRINT_DETAILS = false;
    runRefined = false;
    ANNOTATION_TYPES[0] = "Person";
    DICTIONARY_DIR = "testdata/aql/refineWebTests/personPhoneEnron/GenericNE/dictionaries";
    UDF_DIR = "testdata/aql/refineWebTests/personPhoneEnron/GenericNE/udfjars";
    INIT_QUERY_FILE = QUERY_DIR + "/person14_3Filters.aql";
    IN_DIR = DATA_DIR + "/EnronMeetings/test/data";
    EXPECTED_DIR = DATA_DIR + "/EnronMeetings/test/label";
    runAQL(null);
  }

  public void testEnronMeetingAfter() throws Exception {
    POSITION_SHIFT = 0; // after shift by 1, F-score = 0.43; before: 0.35
    System.out.println("Person before refine...");
    PRINT_DETAILS = false;
    runRefined = false;
    ANNOTATION_TYPES[0] = "Person";
    DICTIONARY_DIR = "testdata/aql/refineWebTests/personPhoneEnron/GenericNE/dictionaries";
    UDF_DIR = "testdata/aql/refineWebTests/personPhoneEnron/GenericNE/udfjars";
    INIT_QUERY_FILE = QUERY_DIR + "/person14_3Filters_Refined.aql";
    IN_DIR = DATA_DIR + "/EnronMeetings/test/data";
    EXPECTED_DIR = DATA_DIR + "/EnronMeetings/test/label";
    runAQL(null);
  }

  public void personphoneEnronCrossValidation() throws Exception {
    PRINT_DETAILS = true;

    ANNOTATION_TYPES[0] = "PersonPhone";
    DICTIONARY_DIR = "testdata/aql/refineWebTests/personPhoneEnron/GenericNE/dictionaries";
    UDF_DIR = "testdata/aql/refineWebTests/personPhoneEnron/GenericNE/udfjars";

    // File outFile = new File(CROSS_VALIDATION_OUT);
    // outFile.delete();

    ArrayList<Double[]> resultsBefore = new ArrayList<Double[]>();
    ArrayList<Double[]> resultsAfter = new ArrayList<Double[]>();

    for (int i = 0; i < 1; i++) {
      System.out.println("Cross validation set No. " + i + ": \n");
      System.out.println("Result before refinement: ");
      // before refine

      IN_DIR = DATA_DIR + "/PersonPhoneEnronCrossValidation/test/data_" + i;
      EXPECTED_DIR = DATA_DIR + "/PersonPhoneEnronCrossValidation/test/label_" + i;
      INIT_QUERY_FILE = QUERY_DIR + "/personPhoneEnron/personphonecandidates.aql";
      runAQL(resultsBefore);

      System.out.println("Result after refinement: ");
      INIT_QUERY_FILE = QUERY_DIR + "/personPhoneEnron/personphone_Refined_" + i + ".aql";
      runAQL(resultsAfter);

      // FileWriter cvOut = new FileWriter(CROSS_VALIDATION_OUT, true);
      // cvOut.append("\n\n");
      // cvOut.close();
    }

    compareNPrintResults(resultsBefore, resultsAfter);
  }

  public void personSelfLabelCrossValidation() throws Exception {
    PRINT_DETAILS = false;

    ANNOTATION_TYPES[0] = "Person";
    DICTIONARY_DIR = "testdata/aql/refineWebTests/personPhoneEnron/GenericNE/dictionaries";
    UDF_DIR = "testdata/aql/refineWebTests/personPhoneEnron/GenericNE/udfjars";

    // File outFile = new File(CROSS_VALIDATION_OUT);
    // outFile.delete();

    ArrayList<Double[]> resultsBefore = new ArrayList<Double[]>();
    ArrayList<Double[]> resultsAfter = new ArrayList<Double[]>();

    for (int i = 1; i < 2; i++) {
      System.out.println("Cross validation set No. " + i + ": \n");
      System.out.println("Result before refinement: ");
      // before refine

      IN_DIR = DATA_DIR + "/PersonSelfLabelCrossValidation/test/data_" + i;
      EXPECTED_DIR = DATA_DIR + "/PersonSelfLabelCrossValidation/test/label_" + i;
      INIT_QUERY_FILE = QUERY_DIR + "/person14_3Filters.aql";
      runAQL(resultsBefore);

      System.out.println("Result after refinement: ");
      INIT_QUERY_FILE = QUERY_DIR + "/personSelfLabel/person14_3Filters_Refined_" + i + ".aql";
      runAQL(resultsAfter);

      // FileWriter cvOut = new FileWriter(CROSS_VALIDATION_OUT, true);
      // cvOut.append("\n\n");
      // cvOut.close();
    }

    compareNPrintResults(resultsBefore, resultsAfter);
  }

  public void userStudy() throws Exception {
    PRINT_DETAILS = false;

    ANNOTATION_TYPES[0] = "Person";
    DICTIONARY_DIR = "testdata/aql/refineWebTests/userStudy/GenericNE/dictionaries";
    UDF_DIR = "testdata/aql/refineWebTests/userStudy/GenericNE/udfjars";
    INCLUDE_PATH = "testdata/aql/refineWebTests/userStudy/";
    // File outFile = new File(CROSS_VALIDATION_OUT);
    // outFile.delete();

    ArrayList<Double[]> resultsBefore = new ArrayList<Double[]>();
    ArrayList<Double[]> resultsAfter = new ArrayList<Double[]>();

    for (int i = 0; i < 1; i++) {
      System.out.println("Cross validation set No. " + i + ": \n");
      // before refine
      // IN_DIR = DATA_DIR + "/PersonSLCleanCV/test/data_" + i;
      // EXPECTED_DIR = DATA_DIR + "/PersonSLCleanCV/test/label_" + i;
      IN_DIR = DATA_DIR + "";
      EXPECTED_DIR = DATA_DIR;

      System.out.println("Result after refinement: ");
      INIT_QUERY_FILE = QUERY_DIR + "/userStudy/Refined/person14_Refined_" + i + ".aql";
      runAQL(resultsAfter);

      System.out.println("Result before refinement: ");
      INIT_QUERY_FILE = QUERY_DIR + "/userStudy/person14_expert_study_baseline.aql";
      runAQL(resultsBefore);

      // FileWriter cvOut = new FileWriter(CROSS_VALIDATION_OUT, true);
      // cvOut.append("\n\n");
      // cvOut.close();
    }

    compareNPrintResults(resultsBefore, resultsAfter);
  }

  public void speedTest() throws Exception {
    PRINT_DETAILS = false;

    ANNOTATION_TYPES[0] = "Person";
    DICTIONARY_DIR = "testdata/aql/refineWebTests/userStudy/GenericNE/dictionaries";
    UDF_DIR = "testdata/aql/refineWebTests/userStudy/GenericNE/udfjars";
    INCLUDE_PATH = "testdata/aql/refineWebTests/userStudy/";
    // File outFile = new File(CROSS_VALIDATION_OUT);
    // outFile.delete();

    ArrayList<Double[]> resultsBefore = new ArrayList<Double[]>();
    ArrayList<Double[]> resultsAfter = new ArrayList<Double[]>();

    for (int i = 5; i < 9; i++) {
      resultsBefore.clear();
      System.out.println("Cross validation set No. " + i + ": \n");
      // before refine
      IN_DIR = DATA_DIR + "/PersonSLCleanCV/test/data_" + i;
      EXPECTED_DIR = DATA_DIR + "/PersonSLCleanCV/test/label_" + i;

      System.out.println("Result before refinement: ");
      INIT_QUERY_FILE = QUERY_DIR + "/userStudy/person14_expert_study_baseline.aql";
      runAQL(resultsBefore);

      System.out.println("Result after 1st refinement: ");
      INIT_QUERY_FILE = QUERY_DIR + "/userStudy/Refined/person14_speed_Refined_5_0.aql";
      runAQL(resultsAfter);

      System.out.println("F-score gain is:  " + (resultsAfter.get(0)[2] - resultsBefore.get(0)[2]));
      resultsAfter.clear();

      System.out.println("Result after 2nd refinement: ");
      INIT_QUERY_FILE = QUERY_DIR + "/userStudy/Refined/person14_speed_Refined_5_1.aql";
      runAQL(resultsAfter);
      System.out.println("F-score gain is:  " + (resultsAfter.get(0)[2] - resultsBefore.get(0)[2]));
      resultsAfter.clear();

      System.out.println("Result after 3rd refinement: ");
      INIT_QUERY_FILE = QUERY_DIR + "/userStudy/Refined/person14_speed_Refined_5_2.aql";
      runAQL(resultsAfter);
      System.out.println("F-score gain is:  " + (resultsAfter.get(0)[2] - resultsBefore.get(0)[2]));
      resultsAfter.clear();

      // FileWriter cvOut = new FileWriter(CROSS_VALIDATION_OUT, true);
      // cvOut.append("\n\n");
      // cvOut.close();
    }

    // compareNPrintResults(resultsBefore, resultsAfter);
  }

  public void personRefinement() throws Exception {
    PRINT_DETAILS = false;

    ANNOTATION_TYPES[0] = "Person";
    DICTIONARY_DIR = "testdata/aql/refineWebTests/userStudy/GenericNE/dictionaries";
    UDF_DIR = "testdata/aql/refineWebTests/userStudy/GenericNE/udfjars";
    INCLUDE_PATH = "testdata/aql/refineWebTests/userStudy/";
    // File outFile = new File(CROSS_VALIDATION_OUT);
    // outFile.delete();

    ArrayList<Double[]> resultsBefore = new ArrayList<Double[]>();
    ArrayList<Double[]> resultsAfter = new ArrayList<Double[]>();

    for (int i = 0; i < 1; i++) {
      resultsBefore.clear();
      System.out.println("Cross validation set No. " + i + ": \n");
      // before refineDATA_DIR + "/ace2005-clean/train/data";
      IN_DIR = DATA_DIR + "/CoNLL2003-clean/testA/data";
      EXPECTED_DIR = DATA_DIR + "/CoNLL2003-clean/testA/label";

      System.out.println("Result before refinement: ");
      INIT_QUERY_FILE = QUERY_DIR + "/userStudy/person14_expert_study_baseline.aql";
      runAQL(resultsBefore);

      System.out.println("Result after 1st refinement: ");
      INIT_QUERY_FILE = QUERY_DIR + "/userStudy/Refined/person14_speed_Refined_0.aql";
      runAQL(resultsAfter);

      System.out.println("F-score gain is:  " + (resultsAfter.get(0)[2] - resultsBefore.get(0)[2]));
      resultsAfter.clear();

      System.out.println("Result after 2nd refinement: ");
      INIT_QUERY_FILE = QUERY_DIR + "/userStudy/Refined/person14_speed_Refined_1.aql";
      runAQL(resultsAfter);
      System.out.println("F-score gain is:  " + (resultsAfter.get(0)[2] - resultsBefore.get(0)[2]));
      resultsAfter.clear();

      System.out.println("Result after 3rd refinement: ");
      INIT_QUERY_FILE = QUERY_DIR + "/userStudy/Refined/person14_speed_Refined_2.aql";
      runAQL(resultsAfter);
      System.out.println("F-score gain is:  " + (resultsAfter.get(0)[2] - resultsBefore.get(0)[2]));
      resultsAfter.clear();

      System.out.println("Result after 4rd refinement: ");
      INIT_QUERY_FILE = QUERY_DIR + "/userStudy/Refined/person14_speed_Refined_3.aql";
      runAQL(resultsAfter);
      System.out.println("F-score gain is:  " + (resultsAfter.get(0)[2] - resultsBefore.get(0)[2]));
      resultsAfter.clear();

      System.out.println("Result after 5rd refinement: ");
      INIT_QUERY_FILE = QUERY_DIR + "/userStudy/Refined/person14_speed_Refined_4.aql";
      runAQL(resultsAfter);
      System.out.println("F-score gain is:  " + (resultsAfter.get(0)[2] - resultsBefore.get(0)[2]));
      resultsAfter.clear();
      // FileWriter cvOut = new FileWriter(CROSS_VALIDATION_OUT, true);
      // cvOut.append("\n\n");
      // cvOut.close();
    }

    // compareNPrintResults(resultsBefore, resultsAfter);
  }

  public void personCoNLLCrossValidation() throws Exception {
    POSITION_SHIFT = 1;
    PRINT_DETAILS = false;

    ANNOTATION_TYPES[0] = "Person";
    DICTIONARY_DIR = "testdata/aql/refineWebTests/userStudy/GenericNE/dictionaries";
    UDF_DIR = "testdata/aql/refineWebTests/userStudy/GenericNE/udfjars";
    INCLUDE_PATH = "testdata/aql/refineWebTests/userStudy/";
    // File outFile = new File(CROSS_VALIDATION_OUT);
    // outFile.delete();

    ArrayList<Double[]> resultsBefore = new ArrayList<Double[]>();
    ArrayList<Double[]> resultsAfter = new ArrayList<Double[]>();

    // now measure each refinement
    for (int j = 0; j < 3; j++) {

      System.out.println("Result of the " + j + "-th refinement: ");
      resultsAfter.clear();

      // resultsBefore.clear();

      for (int i = 0; i < 10; i++) {
        System.out.println("Cross validation set No. " + i + ": \n");
        // before refine
        // IN_DIR = DATA_DIR + "/PersonSLCleanCV/test/data_" + i;
        // EXPECTED_DIR = DATA_DIR + "/PersonSLCleanCV/test/label_" + i;
        IN_DIR = DATA_DIR + "/CoNLLCrossValidation/test/data_" + i;
        EXPECTED_DIR = DATA_DIR + "/CoNLLCrossValidation/test/label_" + i;

        if (j == 0) {
          System.out.println("Result before refinement: ");
          INIT_QUERY_FILE = QUERY_DIR + "/userStudy/person14_expert_study_baseline.aql";
          runAQL(resultsBefore);
        }

        System.out.println("Result after refinement: ");
        INIT_QUERY_FILE = QUERY_DIR + "/userStudy/CoNLL/person14_speed_Refined_" + j + ".aql";
        runAQL(resultsAfter);

        // FileWriter cvOut = new FileWriter(CROSS_VALIDATION_OUT, true);
        // cvOut.append("\n\n");
        // cvOut.close();
      }

      compareNPrintResults(resultsBefore, resultsAfter);

    }
  }

  public void personACECrossValidation() throws Exception {
    POSITION_SHIFT = 1;
    PRINT_DETAILS = true;

    ANNOTATION_TYPES[0] = "Person";
    DICTIONARY_DIR = "testdata/aql/refineWebTests/userStudy/GenericNE/dictionaries";
    UDF_DIR = "testdata/aql/refineWebTests/userStudy/GenericNE/udfjars";
    INCLUDE_PATH = "testdata/aql/refineWebTests/userStudy/";
    // File outFile = new File(CROSS_VALIDATION_OUT);
    // outFile.delete();

    ArrayList<Double[]> resultsBefore = new ArrayList<Double[]>();
    ArrayList<Double[]> resultsAfter = new ArrayList<Double[]>();

    // now measure each refinement
    for (int j = 1; j < 2; j++) {

      System.out.println("Result of the " + j + "-th refinement: ");
      resultsAfter.clear();

      // resultsBefore.clear();

      for (int i = 0; i < 10; i++) {
        System.out.println("Cross validation set No. " + i + ": \n");
        // before refine
        // IN_DIR = DATA_DIR + "/PersonSLCleanCV/test/data_" + i;
        // EXPECTED_DIR = DATA_DIR + "/PersonSLCleanCV/test/label_" + i;
        IN_DIR = DATA_DIR + "/ACECrossValidation/test/data_" + i;
        EXPECTED_DIR = DATA_DIR + "/ACECrossValidation/test/label_" + i;

        // if (j == 0){
        System.out.println("Result before refinement: ");
        INIT_QUERY_FILE = QUERY_DIR + "/userStudy/person14_expert_study_baseline.aql";
        runAQL(resultsBefore);
        // }

        System.out.println("Result after refinement: ");
        INIT_QUERY_FILE = QUERY_DIR + "/userStudy/ACE/person14_speed_Refined_" + j + ".aql";
        runAQL(resultsAfter);

        // FileWriter cvOut = new FileWriter(CROSS_VALIDATION_OUT, true);
        // cvOut.append("\n\n");
        // cvOut.close();
      }

      compareNPrintResults(resultsBefore, resultsAfter);

    }
  }

  public void personACETest() throws Exception {
    POSITION_SHIFT = 1;
    PRINT_DETAILS = true;

    ANNOTATION_TYPES[0] = "Person";
    DICTIONARY_DIR = "testdata/aql/refineWebTests/userStudy/GenericNE/dictionaries";
    UDF_DIR = "testdata/aql/refineWebTests/userStudy/GenericNE/udfjars";
    INCLUDE_PATH = "testdata/aql/refineWebTests/userStudy/";
    // File outFile = new File(CROSS_VALIDATION_OUT);
    // outFile.delete();

    ArrayList<Double[]> resultsBefore = new ArrayList<Double[]>();
    ArrayList<Double[]> resultsAfter = new ArrayList<Double[]>();

    // before refine
    // IN_DIR = DATA_DIR + "/PersonSLCleanCV/test/data_" + i;
    // EXPECTED_DIR = DATA_DIR + "/PersonSLCleanCV/test/label_" + i;
    IN_DIR = DATA_DIR + "/ACECrossValidation/test/data_0";
    EXPECTED_DIR = DATA_DIR + "/ACECrossValidation/test/label_0";

    // if (j == 0){
    System.out.println("Result before refinement: ");
    INIT_QUERY_FILE = QUERY_DIR + "/userStudy/person14_expert_study_baseline.aql";
    runAQL(resultsBefore);
    // }

    System.out.println("Result after refinement: ");
    INIT_QUERY_FILE = QUERY_DIR + "/userStudy/ACE/person14_speed_Refined_7" + ".aql";
    runAQL(resultsAfter);

    // FileWriter cvOut = new FileWriter(CROSS_VALIDATION_OUT, true);
    // cvOut.append("\n\n");
    // cvOut.close();
  }

  public void testPersonSimple() throws Exception {
    runRefined = false;
    ANNOTATION_TYPES[0] = "Person";
    INIT_QUERY_FILE = QUERY_DIR + "/person14.aql";
    IN_DIR = DATA_DIR + "/test1/test-data";
    EXPECTED_DIR = DATA_DIR + "/test1/test-standard";
    runAQL(null);
  }

  public void runRefined() throws Exception {
    runRefined = false;
    ANNOTATION_TYPES[0] = "Person";
    INIT_QUERY_FILE = QUERY_DIR + "/person14_Refined.aql";
    IN_DIR = DATA_DIR + "/test1/test-data";
    EXPECTED_DIR = DATA_DIR + "/test1/test-standard";
    runAQL(null);
  }

  public void sanityTest() throws Exception {
    runRefined = false;
    ANNOTATION_TYPES[0] = "Person";
    INIT_QUERY_FILE = QUERY_DIR + "/person14_3Filters.aql";
    IN_DIR = DATA_DIR + "/refineDebug/data";
    EXPECTED_DIR = DATA_DIR + "/refineDebug/label";
    runAQL(null);
  }

  public void personSelfLabelBeforeRefine() throws Exception {
    runRefined = false;
    ANNOTATION_TYPES[0] = "Person";
    INIT_QUERY_FILE = QUERY_DIR + "/person14.aql";
    IN_DIR = DATA_DIR + "/personSelfLabel-test/data";
    EXPECTED_DIR = DATA_DIR + "/personSelfLabel-test/label";
    runAQL(null);
  }

  public void personSelfLabelAfterRefine() throws Exception {
    runRefined = false;
    ANNOTATION_TYPES[0] = "Person";
    INIT_QUERY_FILE = QUERY_DIR + "/person14_Refined.aql";
    IN_DIR = DATA_DIR + "/personSelfLabel-test/data";
    EXPECTED_DIR = DATA_DIR + "/personSelfLabel-test/label";
    runAQL(null);
  }

  public void testPersonLabeledDataInit() throws Exception {
    runRefined = false;
    ANNOTATION_TYPES[0] = "Person";
    INIT_QUERY_FILE = QUERY_DIR + "/person14.aql";
    IN_DIR = DATA_DIR + "/personSelfLabel-small/data";
    EXPECTED_DIR = DATA_DIR + "/personSelfLabel-small/label";
    runAQL(null);
  }

  public void testPersonMoreFilters() throws Exception {
    runRefined = false;
    ANNOTATION_TYPES[0] = "Person";
    INIT_QUERY_FILE = QUERY_DIR + "/personBase_MoreFilters.aql";
    IN_DIR = DATA_DIR + "/test1/test-data";
    EXPECTED_DIR = DATA_DIR + "/test1/test-standard";
    runAQL(null);
  }

  /**
   * Test on all enron-244 data (without split for testing)
   * 
   * @throws Exception
   */
  public void testPersonOnFullData() throws Exception {
    runRefined = false;
    ANNOTATION_TYPES[0] = "Person";
    // INIT_QUERY_FILE = QUERY_DIR + "/personBase_MoreFilters.aql";
    INIT_QUERY_FILE = QUERY_DIR + "/personBase_ManualDict.aql";
    IN_DIR = DATA_DIR + "/Enron-244/enron-244-data";
    EXPECTED_DIR = DATA_DIR + "/Enron-244/enron-244-gold-standard";
    runAQL(null);
  }

  public void testPersonPhoneImproved() throws Exception {
    runRefined = false;
    INIT_QUERY_FILE = QUERY_DIR + "/PersonPhone-improved.aql";
    IN_DIR = TESTING_DATA;
    EXPECTED_DIR = TESTNING_STANDARD;
    runAQL(null);
  }

  public void testPersonPhoneStandard() throws Exception {
    runRefined = false;
    IN_DIR = DATA_DIR + "/Enron-personphone/Enron-personphone-data";
    EXPECTED_DIR = DATA_DIR + "/Enron-personphone/Enron-personphone-gs";
    runAQL(null);
  }

  /**
   * Test data manually labeled
   * 
   * @throws Exception
   */
  public void testlabeledPerson() throws Exception {
    INIT_QUERY_FILE = QUERY_DIR + "/personConsolidated-noEntityFilter.aql";
    runRefined = false;
    IN_DIR = DATA_DIR + "/labelTest/data";
    EXPECTED_DIR = DATA_DIR + "/labelTest/gs";
    runAQL(null);
  }

  public AQLEvaluator2() throws IOException, ParseException, Exception,
      com.ibm.avatar.aog.ParseException, ParserConfigurationException, SAXException {}

  @SuppressWarnings({"all"})
  private void runAQL(ArrayList<Double[]> resultArray) throws Exception {
    String dataPath = INCLUDE_PATH + ";" + DICTIONARY_DIR + ";" + UDF_DIR;
    String aqlFileName;
    if (!runRefined) {
      aqlFileName = INIT_QUERY_FILE;
    } else {
      aqlFileName = REFINED_QUERY_FILE;
    }
    compileAQL(new File(aqlFileName), dataPath);
    OperatorGraph og = instantiateOperatorGraph(Constants.GENERIC_MODULE_NAME);

    HashMap<String, String> typesEnabled = new HashMap<String, String>();

    for (int i = 0; i < ANNOTATION_TYPES.length; i++) {
      String id = "" + i;
      typesEnabled.put(ANNOTATION_TYPES[i], id);
    }

    File inDir = new File(IN_DIR);
    String[] inFiles = inDir.list();
    // removed comment by Bin on March 6
    sort(inFiles);

    // DocScan docScan = null;
    // MemoizationTable mt = null;
    DocReader reader = null;

    // if (INPUT_DOC_TYPE == FILE_TYPE.TEXT){
    // docScan = new DirDocScan(new File(IN_DIR));
    reader = new DocReader(new File(IN_DIR));

    // mt = new MemoizationTable(docScan);
    // }

    File expectedDir = new File(EXPECTED_DIR);
    String[] expectedFiles = expectedDir.list();
    // removed comment by Bin on March 6
    sort(expectedFiles);

    List<String> cleanLabels = null;
    if (IS_CLEANING_DATA) {
      File clean = new File(CORRECTED_LABEL_DIR);
      String[] files = clean.list();
      cleanLabels = Arrays.asList(files);
    }

    TupleSchema docSchema = DocScanInternal.createOneColumnSchema();
    TextSetter setDocText = docSchema.textSetter(Constants.DOCTEXT_COL);

    try {
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document expected;

      counters = new int[typesEnabled.size()][4];

      for (int i = 0; i < typesEnabled.size(); i++) {
        for (int k = 0; k < 4; k++)
          counters[i][k] = 0;
      }

      for (int i = 0; i < inFiles.length; i++) {

        if (IS_CLEANING_DATA) {
          if (cleanLabels.contains(inFiles[i])) {
            System.out.println("Skipping " + inFiles[i]);
            continue;
          }
        }

        if (PRINT_DETAILS)
          System.out.println(inFiles[i]);

        if (inFiles[i].equals("CVS") || inFiles[i].equals(".svn")) {
          // System.out.println("skipped " + inFiles[i] + "; expected is " + expectedFiles[i]);
          continue;
        }

        // Read the input document
        String texts[];
        if (INPUT_DOC_TYPE == FILE_TYPE.XML)
          texts = getInDocFromXML(builder, new File(IN_DIR + File.separator + inFiles[i]));
        else
          texts = getInDocFromText(new File(IN_DIR + File.separator + inFiles[i]));

        // Read the gold standard for the input document
        if (PRINT_DETAILS)
          System.out.println(expectedFiles[i]);
        expected = builder.parse(new File(EXPECTED_DIR + File.separator + expectedFiles[i]));

        NodeList root4Annotators = expected.getElementsByTagName(TEXT_ENTRY);
        // ArrayList<Span>[] actualAnnots = new ArrayList[typesEnabled.size()];
        // ArrayList<Node>[] expectedAnnots = new ArrayList[typesEnabled.size()];
        ArrayList[] actualAnnots = new ArrayList[typesEnabled.size()];
        ArrayList[] expectedAnnots = new ArrayList[typesEnabled.size()];

        for (int j = 0; j < typesEnabled.size(); j++) {
          // actualAnnots[i] = new ArrayList<Span>();
          // expectedAnnots[i] = new ArrayList<Node>();
          actualAnnots[j] = new ArrayList();
          expectedAnnots[j] = new ArrayList();
        }

        for (int kj = 0; kj < texts.length; kj++) {
          if (PRINT_DETAILS)
            System.out.println("i = " + kj);

          // prepare the arrayLists to read in annotations
          for (int k = 0; k < typesEnabled.size(); k++) {
            actualAnnots[k].clear();
            expectedAnnots[k].clear();
          }

          NodeList children = root4Annotators.item(kj).getChildNodes();

          // read expected annotations
          for (int k = 0; k < children.getLength(); k++) {
            Node child = children.item(k);

            if (child.getNodeType() != Node.ELEMENT_NODE)
              continue;
            // expectedAnnots[(Integer)(typesEnabled.get(child.getNodeName()))].add(child);
            // FIXME: child.getNodeName() might not appear in typesEnabled.
            // System.out.println(child.getNodeName());
            if (!typesEnabled.containsKey(child.getNodeName()))
              continue;
            expectedAnnots[Integer.parseInt((String) typesEnabled.get(child.getNodeName()))]
                .add(child);
          }

          String docText = texts[kj];
          // System.out.println(docText);

          // commented by Bin on Mar 2nd.
          // if(INPUT_DOC_TYPE == FILE_TYPE.XML){
          docText = docText.replaceAll("amp;", "&");
          docText = docText.replaceAll("gt;", ">");
          docText = docText.replaceAll("lt;", "<");
          // runner.pushDoc(docText);
          // }
          // else if (INPUT_DOC_TYPE == FILE_TYPE.TEXT){
          // pushes the document directly from IN_DIR,
          // instead of the one read using getInDocFromText() to ensure the
          // gold standard offsets are consistent with input doc
          // mt.resetCache();
          // Tuple doc = docScan.getNextDocTup(mt);
          Tuple docTup = reader.next();
          setDocText.setVal(docTup, docText);
          Map<String, TupleList> annotations = og.execute(docTup, null, null);
          // }

          for (String name : og.getOutputTypeNames()) {
            String outputName = (String) name.substring(TYPE_PREFIX.length());
            if (PRINT_DETAILS)
              System.out.println(outputName);

            // run annotator
            if (typesEnabled.keySet().contains(outputName)) {
              int which = Integer.parseInt((String) typesEnabled.get(outputName));
              if (PRINT_DETAILS)
                System.out.println(outputName + " in " + which);

              TupleList resultTups = annotations.get(name);
              TLIter tuples = resultTups.iterator();
              while (tuples.hasNext()) {
                Tuple tuple = tuples.next();
                Span annot = (Span) resultTups.getSchema().getCol(tuple, 0);
                int begin = annot.getBegin();
                int end = annot.getEnd();
                String text = annot.getText();
                String outputLine = outputName + "," + begin + "," + end + ",\"" + text + "\"\n";
                if (PRINT_DETAILS)
                  System.out.println(outputLine);

                actualAnnots[which].add(annot);
              }

              // compare annotations
              compareAnnots(expectedAnnots[which], actualAnnots[which], which, inFiles[i]);
            }
          }
        }
      }

      double precision = 0;
      double relaxedPrecision = 0;
      double recall = 0;
      double fmeasure = 0;
      double relaxedFmeasure = 0;

      for (int i = 0; i < typesEnabled.size(); i++) {
        precision = (double) counters[i][EQUAL] / (double) (counters[i][EQUAL] + counters[i][BEFORE]
            + counters[i][OVERLAP_BUT_NOT_EQUAL]);
        // Overlapping is okay
        relaxedPrecision = (double) (counters[i][EQUAL] + counters[i][OVERLAP_BUT_NOT_EQUAL])
            / (double) (counters[i][EQUAL] + counters[i][BEFORE]
                + counters[i][OVERLAP_BUT_NOT_EQUAL]);
        recall = (double) counters[i][EQUAL] / (double) (counters[i][EQUAL] + counters[i][AFTER]);
        fmeasure = 2 * precision * recall / (precision + recall);
        relaxedFmeasure = 2 * relaxedPrecision * recall / (relaxedPrecision + recall);

        // Completely equal only

        System.out.println("For " + ANNOTATION_TYPES[i] + " precision = " + precision
            + " relaxedPrecision = " + relaxedPrecision + "; recall = " + recall);
        System.out.println("F-measure = " + fmeasure);

        System.out.println("relaxed F-measure = " + relaxedFmeasure);

        System.out.println("counters[i][EQUAL] = " + counters[i][EQUAL]);
        System.out.println("counters[i][BEFORE] = " + counters[i][BEFORE]);
        System.out.println("counters[i][AFTER] = " + counters[i][AFTER]);
        System.out
            .println("counters[i][OVERLAP_BUT_NOT_EQUAL] = " + counters[i][OVERLAP_BUT_NOT_EQUAL]);

        // store results
        Double[] result = {precision, recall, fmeasure, relaxedPrecision, relaxedFmeasure};
        if (resultArray != null)
          resultArray.add(result);
        // FileWriter cvOut = new FileWriter(CROSS_VALIDATION_OUT, true);
        // cvOut.append(precision + " " + recall + " " + fmeasure + " " + relaxedPrecision + " "+
        // relaxedFmeasure +
        // "\n");
        // cvOut.close();
      }

    } catch (SAXParseException spe) {
      System.err.println("Error happens when parsing the inputs.");
    } finally {
      // Close the document reader
      if (null != reader)
        reader.remove();
    }
  }

  private void sort(String[] array) {

    for (int i = 0; i < array.length - 1; i++)
      for (int j = i + 1; j < array.length; j++)
        if (array[i].compareTo(array[j]) > 0) {
          String tmp = array[j];
          array[j] = array[i];
          array[i] = tmp;
        }
  }

  private String[] getInDocFromText(File file) {

    StringBuilder text = new StringBuilder();

    try {
      BufferedReader input = new BufferedReader(new FileReader(file));
      try {
        String line = null;

        while ((line = input.readLine()) != null) {
          text.append(line);
          text.append(System.getProperty("line.separator"));
        }
      } finally {
        input.close();
      }
    } catch (IOException ex) {
      ex.printStackTrace();
    }

    String[] texts = new String[1];
    texts[0] = text.toString();
    return texts;
  }

  private String[] getInDocFromXML(DocumentBuilder builder, File file)
      throws SAXException, IOException {
    Document document;
    document = builder.parse(file);
    NodeList texts = document.getElementsByTagName(TEXT_ENTRY);

    String[] strTexts = new String[texts.getLength()];

    for (int i = 0; i < texts.getLength(); i++)
      strTexts[i] = texts.item(i).getTextContent();

    return strTexts;
  }

  private void compareAnnots(ArrayList<Node> expectedAnnots, ArrayList<Span> actual, int which,
      String docLabel) {
    // Span span = new Span();

    int begin = 0;
    int end = 0;
    String text = "";
    String type = "";

    int lastIdx = 0;

    for (int i = 0; i < expectedAnnots.size();) {
      Node annotation = (Node) expectedAnnots.get(i);

      type = annotation.getNodeName();

      NodeList children = annotation.getChildNodes();

      // read content of expected annotation
      for (int j = 0; j < children.getLength(); j++) {
        Node child = children.item(j);

        if (child.getNodeType() != Node.ELEMENT_NODE)
          continue;

        if (child.getNodeName().equals(BEGIN)) {
          begin = Integer.parseInt(child.getTextContent());
        } else if (child.getNodeName().equals(END)) {
          end = Integer.parseInt(child.getTextContent());
        } else if (child.getNodeName().equals(ANNOTATION)) {
          text = child.getTextContent();
        }
      }

      if (lastIdx >= actual.size()) {
        // System.out.println("Missing " + type + ": " + text);
        if (PRINT_DETAILS)
          System.out.println(String.format("Missing %s: %s[%d-%d]", type, text, begin, end));
        // missingAnnotations.add(new SimpleSpan(annotation));

        counters[which][AFTER]++;
        i++;
      }

      boolean moveOn = true;

      for (int k = lastIdx; k < actual.size() && moveOn;) {
        Span annot = (Span) actual.get(k);

        int relationship = comparePosition(annot, begin, end);

        switch (relationship) {
          case EQUAL:
            // System.out.println("Finding " + type + ": " + text);
            if (PRINT_DETAILS)
              System.out.println(String.format("Finding %s: %s[%d-%d]", type, text, begin, end));
            lastIdx = ++k;
            i++;
            moveOn = false;
            counters[which][EQUAL]++;

            // added by Bin to fix problem where we have multiple annotations with same ending but
            // different begin
            // e.g., M.Pickering[63-74] (correct) followed by Pickering[65-74], which should be
            // partially correct
            // however, the code moves on since a correct annotation has been found and thus the 2nd
            // is marked wrong.

            int cur = k;

            // Yunyao: added on 09/22/09 to consider the case where multiple actual annotations of
            // the same time
            // overlapping with
            // with one expected annotation

            if (cur < actual.size()) {
              boolean stillOverlap = false;

              do {
                annot = (Span) actual.get(cur++);

                if (comparePosition(annot, begin, end) == OVERLAP_BUT_NOT_EQUAL) {
                  lastIdx = ++k;

                  stillOverlap = true;

                  // System.out.println("Partially wrong annotation " + type + ": " +
                  // annot.getText() + " instead of " +
                  // text);
                  if (PRINT_DETAILS)
                    System.out.println(String.format(
                        "Partially wrong annotation %s: %s[%d-%d] instead of %s[%d-%d]", type,
                        annot.getText(), annot.getBegin(), annot.getEnd(), text, begin, end));

                  // if (IS_CLEANING_DATA)
                  // confirmDialog(annot, docLabel, CORRECTED_LABEL_DIR, type);

                  counters[which][OVERLAP_BUT_NOT_EQUAL]++;
                }
                /*
                 * Added by Bin to handle case where multiple actual annotations start at the same
                 * position: count the matching tuple, and only move on when no more tuples start at
                 * that position.
                 */
                else if (comparePosition(annot, begin, end) == EQUAL) {
                  // counters[which][EQUAL]++;
                  stillOverlap = true;
                  lastIdx = ++k;
                  if (PRINT_DETAILS)
                    System.out
                        .println(String.format("Duplicate %s: %s[%d-%d]", type, text, begin, end));
                } else {
                  stillOverlap = false;
                  // System.out.println("debug");
                }
              } while (stillOverlap && cur < actual.size());
            }

            break;
          case BEFORE:
            // System.out.println("Wrong " + type + ": " + annot.getText());
            if (PRINT_DETAILS || IS_CLEANING_DATA)
              System.out.println(String.format("Wrong %s: %s[%d-%d]", type, annot.getText(),
                  annot.getBegin(), annot.getEnd()));

            // for data cleaning
            if (IS_CLEANING_DATA)
              confirmDialog(annot, docLabel, CORRECTED_LABEL_DIR);

            lastIdx = ++k;
            moveOn = true;
            counters[which][BEFORE]++;
            break;
          case AFTER:
            // System.out.println("Missing " + type + ": " + text);
            if (PRINT_DETAILS)
              System.out.println(String.format("Missing %s: %s[%d-%d]", type, text, begin, end));
            // missingAnnotations.add(new SimpleSpan(annotation));
            lastIdx = k;
            i++;
            moveOn = false;
            counters[which][AFTER]++;
            break;
          case OVERLAP_BUT_NOT_EQUAL:
            lastIdx = ++k;

            // find out a way to deal the special symbols.
            String annotText = annot.getText();

            // commented by bin on Mar 2nd
            // if(INPUT_DOC_TYPE == FILE_TYPE.XML){
            annotText = annotText.replaceAll("amp;", "&");
            // }

            if (annotText.equals(text)) {
              // System.out.println("Finding " + type + ": " + text);
              if (PRINT_DETAILS)
                System.out.println(String.format("Finding %s: %s[%d-%d]", type, text, begin, end));
              counters[which][EQUAL]++;
            } else {
              // System.out.println("Partially wrong annotation " + type + ": " + annot.getText() +
              // " instead of " +
              // text);
              if (PRINT_DETAILS)
                System.out.println(
                    String.format("Partially wrong annotation %s: %s[%d-%d] instead of %s[%d-%d]",
                        type, annotText, annot.getBegin(), annot.getEnd(), text, begin, end));
              counters[which][OVERLAP_BUT_NOT_EQUAL]++;

              // if (IS_CLEANING_DATA)
              // confirmDialog(annot, docLabel, CORRECTED_LABEL_DIR, type);
            }

            cur = k;

            // Yunyao: added on 09/22/09 to consider the case where multiple actual annotations of
            // the same time
            // overlapping with
            // with one expected annotation
            if (cur < actual.size()) {
              boolean stillOverlap = false;

              do {
                annot = (Span) actual.get(cur++);

                if (comparePosition(annot, begin, end) == OVERLAP_BUT_NOT_EQUAL) {
                  lastIdx = ++k;

                  stillOverlap = true;

                  // System.out.println("Partially wrong annotation " + type + ": " +
                  // annot.getText() + " instead of " +
                  // text);
                  if (PRINT_DETAILS)
                    System.out.println(String.format(
                        "Partially wrong annotation %s: %s[%d-%d] instead of %s[%d-%d]", type,
                        annot.getText(), annot.getBegin(), annot.getEnd(), text, begin, end));

                  // if (IS_CLEANING_DATA)
                  // confirmDialog(annot, docLabel, CORRECTED_LABEL_DIR, type);

                  counters[which][OVERLAP_BUT_NOT_EQUAL]++;
                }
                /*
                 * Added by Ben to handle case where multiple actual annotations start at the same
                 * position: count the matching tuple, and only move on when no more tuples start at
                 * that position.
                 */
                else if (comparePosition(annot, begin, end) == EQUAL) {
                  counters[which][EQUAL]++;
                  stillOverlap = true;
                  lastIdx = ++k;
                  if (PRINT_DETAILS)
                    System.out
                        .println(String.format("Finding %s: %s[%d-%d]", type, text, begin, end));
                } else {
                  stillOverlap = false;
                }
              } while (stillOverlap && cur < actual.size()); // Dec 24 Ben: added the second
                                                             // condition so that it works
              // with consolidation on "ExactMatch"
            }

            i++;

            moveOn = false;

            break;
          default:
            break;
        }
      }
    }

    if (actual.size() > lastIdx) {
      for (int k = lastIdx; k < actual.size(); k++) {
        Span annot = (Span) actual.get(k);
        // System.out.println("Wrong annotation" + annot.getType().toPureSpanType() + ": " +
        // annot.getText());
        if (PRINT_DETAILS || IS_CLEANING_DATA)
          System.out.println(String.format("Wrong annotation: %s[%d-%d]", annot.getText(),
              annot.getBegin(), annot.getEnd()));
        counters[which][BEFORE]++;

        if (IS_CLEANING_DATA)
          confirmDialog(annot, docLabel, CORRECTED_LABEL_DIR);
      }
    }
  }

  /**
   * Print out xml segment for this span that can be pasted into label file.
   * 
   * @param span
   */
  private void confirmDialog(Span span, String docLabel, String folder) {

    String outputName = ANNOTATION_TYPES[0];

    // print some context chars around the span to make sure we don't miss out
    System.out.println("\nFile " + docLabel + ", Span: " + span.getText());
    System.out.print("Context of the span: ");
    int dist = 20;
    int leftBound = Math.max(0, span.getBegin() - dist);
    int rightBound = Math.min(span.getDocText().length(), span.getEnd() + dist);
    Span context = Span.makeBaseSpan(span, leftBound, rightBound);
    System.out.print(context.getText());

    Scanner in = new Scanner(System.in);
    do {
      System.out.print("\nCorrect: 1; incorrect 0; press 3 to read more context\n");

      int choice = in.nextInt();
      if (choice == 0) {
        System.out.print("\n");
        return;
      } else if (choice == 1) {
        try {
          FileWriter fw = new FileWriter(folder + "/" + docLabel, true);
          fw.append("<" + outputName + ">\n");
          fw.append("<start>" + span.getBegin() + "</start>\n");
          fw.append("<end>" + span.getEnd() + "</end>\n");
          fw.append("<annotation>" + span.getText() + "</annotation>\n");
          fw.append("</" + outputName + ">\n");
          fw.close();
        } catch (Exception e) {
          e.printStackTrace();
        }
        return;
      } else if (choice == 3) {
        dist = 80;
        leftBound = Math.max(0, span.getBegin() - dist);
        rightBound = Math.min(span.getDocText().length(), span.getEnd() + dist);
        context = Span.makeBaseSpan(span, leftBound, rightBound);
        System.out.print(context.getText());
      } else {
        System.out.println("Error in input.");
        System.exit(1);
      }
    } while (true);

  }

  private int comparePosition(Span span, int begin2, int end2) {
    int begin1 = span.getBegin();
    int end1 = span.getEnd();
    int result = 0;

    // System.out.println("begin1 = " + begin1 + " ; begin2" + begin2 + " ; end1 = " + end1 + " ;
    // end2" + end2);

    if ((begin1 + POSITION_SHIFT) == begin2 && (end1 + POSITION_SHIFT) == end2) {
      result = EQUAL;
    } else if (end1 + POSITION_SHIFT < begin2) {
      result = BEFORE;
    } else if (begin1 + POSITION_SHIFT > end2) {
      result = AFTER;
    } else
      result = OVERLAP_BUT_NOT_EQUAL;

    return result;
  }

  private void compareNPrintResults(ArrayList<Double[]> resultsBefore,
      ArrayList<Double[]> resultsAfter) {
    int fold = resultsBefore.size();
    Double[] sumBefore = {0d, 0d, 0d, 0d, 0d};
    Double[] sumAfter = {0d, 0d, 0d, 0d, 0d};
    Double[] avgGain = {0d, 0d, 0d, 0d, 0d};

    for (int i = 0; i < resultsBefore.size(); i++) {
      for (int j = 0; j < 5; j++) {
        sumBefore[j] += resultsBefore.get(i)[j];
        sumAfter[j] += resultsAfter.get(i)[j];
      }
    }

    for (int j = 0; j < 5; j++) {
      avgGain[j] += (sumAfter[j] - sumBefore[j]) / fold;
      sumAfter[j] /= fold;
      sumBefore[j] /= fold;
    }

    System.out.println("Cross-validation results: ");

    System.out.println("Before refinement average precision = " + sumBefore[0] + ", recall = "
        + sumBefore[1] + ", f-score = " + sumBefore[2] + ", relaxed precision = " + sumBefore[3]
        + ", relaxed f-score = " + sumBefore[4]);

    System.out.println("After refinement average precision = " + sumAfter[0] + ", recall = "
        + sumAfter[1] + ", f-score = " + sumAfter[2] + ", relaxed precision = " + sumAfter[3]
        + ", relaxed f-score = " + sumAfter[4]);

    System.out.println("Average improvement in precision = " + avgGain[0] + ", recall = "
        + avgGain[1] + ", f-score = " + avgGain[2] + ", relaxed precision = " + avgGain[3]
        + ", relaxed f-score = " + avgGain[4]);

  }
}
