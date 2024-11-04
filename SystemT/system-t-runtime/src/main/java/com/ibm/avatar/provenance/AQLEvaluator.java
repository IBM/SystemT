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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

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
public class AQLEvaluator extends RuntimeTestHarness {

  // Type of input documents: XML, or plain text
  private enum FILE_TYPE {
    XML, TEXT
  }

  // Type of input docs, for ACE and CoNLL gold standards
  // private final static FILE_TYPE INPUT_DOC_TYPE = FILE_TYPE.XML;
  // Type of input docs, for Enron gold standard
  private final static FILE_TYPE INPUT_DOC_TYPE = FILE_TYPE.TEXT;

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
  private final static String[] ANNOTATION_TYPES = {"PersonPhone"};

  // private static final String DICTIONARY_DIR = "ediscovery/configs/aql/dictionaries";
  // private static final String INCLUDE_PATH = "ediscovery/configs/aql";
  // private static final String QUERY_DIR = "ediscovery/configs/aql";
  // private static final String QUERY_FILE = QUERY_DIR +
  // "/ne-ediscovery-personorgphoneaddress-new.aql";
  // private static final String DATA_DIR = "resources/data/src";

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
  private static String DATA_DIR = "testdata/docs/refineTest";
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

  private String TESTING_DATA = DATA_DIR + "/personphone-test/testing/data";
  private String TESTNING_STANDARD = DATA_DIR + "/personphone-test/testing/gs";

  // private ArrayList<SimpleSpan> missingAnnotations = new ArrayList<SimpleSpan>();

  private DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

  // counters used to store statistics of comparison results for each type
  private int[][] counters;

  public static void main(String[] args) throws Exception {
    AQLEvaluator tester = new AQLEvaluator();
    // tester.testPersonPhone();
    tester.testPersonSimple();
  }

  public void testPersonPhone() throws Exception {
    runRefined = false;
    IN_DIR = TESTING_DATA;
    EXPECTED_DIR = TESTNING_STANDARD;
    runAQL();
  }

  public AQLEvaluator() throws IOException, ParseException, Exception,
      com.ibm.avatar.aog.ParseException, ParserConfigurationException, SAXException {}

  public void testPersonSimple() throws Exception {
    runRefined = false;
    ANNOTATION_TYPES[0] = "Person";
    INIT_QUERY_FILE = QUERY_DIR + "/person14.aql";
    IN_DIR = DATA_DIR + "/test1/test-data";
    EXPECTED_DIR = DATA_DIR + "/test1/test-standard";
    runAQL();
  }

  private void runAQL() throws Exception {
    String dataPath = INCLUDE_PATH + ";" + DICTIONARY_DIR + ";" + UDF_DIR;
    String aqlFileName;
    if (!runRefined) {
      aqlFileName = INIT_QUERY_FILE;
    } else {
      aqlFileName = REFINED_QUERY_FILE;
    }
    compileAQL(new File(aqlFileName), dataPath);
    OperatorGraph og = instantiateOperatorGraph(Constants.GENERIC_MODULE_NAME);

    for (String name : og.getOutputTypeNames()) {
      System.out.println(name);
    }

    HashMap<String, String> typesEnabled = new HashMap<String, String>();

    for (int i = 0; i < ANNOTATION_TYPES.length; i++) {
      String id = "" + i;
      typesEnabled.put(ANNOTATION_TYPES[i], id);
    }

    File inDir = new File(IN_DIR);
    String[] inFiles = inDir.list();
    // sort(inFiles);

    // DocScan docScan = null;
    // MemoizationTable mt = null;
    DocReader reader = null;

    if (INPUT_DOC_TYPE == FILE_TYPE.TEXT) {
      // docScan = new DirDocScan(new File(IN_DIR));
      reader = new DocReader(new File(IN_DIR));
      // mt = new MemoizationTable(docScan);
    }

    File expectedDir = new File(EXPECTED_DIR);
    String[] expectedFiles = expectedDir.list();
    // sort(expectedFiles);

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
        System.out.println(inFiles[i]);

        if (inFiles[i].equals("CVS") || inFiles[i].equals(".svn"))
          continue;

        // Read the input document
        String texts[];
        if (INPUT_DOC_TYPE == FILE_TYPE.XML)
          texts = getInDocFromXML(builder, new File(IN_DIR + File.separator + inFiles[i]));
        else
          texts = getInDocFromText(new File(IN_DIR + File.separator + inFiles[i]));

        // Read the gold standard for the input document
        System.out.println(expectedFiles[i]);
        expected = builder.parse(new File(EXPECTED_DIR + File.separator + expectedFiles[i]));

        NodeList root4Annotators = expected.getElementsByTagName(TEXT_ENTRY);
        // ArrayList<Span>[] actualAnnots = new ArrayList[typesEnabled.size()];
        // ArrayList<Node>[] expectedAnnots = new ArrayList[typesEnabled.size()];
        @SuppressWarnings("unchecked")
        ArrayList<Span>[] actualAnnots = new ArrayList[typesEnabled.size()];
        @SuppressWarnings("unchecked")
        ArrayList<Node>[] expectedAnnots = new ArrayList[typesEnabled.size()];

        for (int j = 0; j < typesEnabled.size(); j++) {
          // actualAnnots[i] = new ArrayList<Span>();
          // expectedAnnots[i] = new ArrayList<Node>();
          actualAnnots[j] = new ArrayList<Span>();
          expectedAnnots[j] = new ArrayList<Node>();
        }

        Tuple docTup = docSchema.createTup();
        for (int kj = 0; kj < texts.length; kj++) {
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
            expectedAnnots[Integer.parseInt(typesEnabled.get(child.getNodeName()))].add(child);
          }

          String docText = texts[kj];
          // System.out.println(docText);

          Map<String, TupleList> annotations = new HashMap<String, TupleList>();
          if (INPUT_DOC_TYPE == FILE_TYPE.XML) {
            docText = docText.replaceAll("amp;", "&");
            setDocText.setVal(docTup, docText);
            annotations = og.execute(docTup, null, null);
          } else if (INPUT_DOC_TYPE == FILE_TYPE.TEXT) {
            // pushes the document directly from IN_DIR,
            // instead of the one read using getInDocFromText() to ensure the
            // gold standard offsets are consistent with input doc
            // mt.resetCache();
            // Tuple doc = docScan.getNextDocTup(mt);
            Tuple doc = reader.next();
            annotations = og.execute(doc, null, null);
          }

          for (String name : og.getOutputTypeNames()) {
            String outputName = (String) name.substring(TYPE_PREFIX.length());
            System.out.println(outputName);

            // run annotator
            if (typesEnabled.keySet().contains(outputName)) {
              int which = Integer.parseInt(typesEnabled.get(outputName));
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
                System.out.println(outputLine);

                actualAnnots[which].add(annot);
              }

              // compare annotations
              compareAnnots(expectedAnnots[which], actualAnnots[which], which);
            }
          }
        }
      }

      double precision = 0;
      double relaxedPrecision = 0;
      double recall = 0;
      double fmeasure = 0;

      for (int i = 0; i < typesEnabled.size(); i++) {
        // Completely equal only
        precision = (double) counters[i][EQUAL] / (double) (counters[i][EQUAL] + counters[i][BEFORE]
            + counters[i][OVERLAP_BUT_NOT_EQUAL]);
        // Overlapping is okay
        relaxedPrecision = (double) (counters[i][EQUAL] + counters[i][OVERLAP_BUT_NOT_EQUAL])
            / (double) (counters[i][EQUAL] + counters[i][BEFORE]
                + counters[i][OVERLAP_BUT_NOT_EQUAL]);
        recall = (double) counters[i][EQUAL] / (double) (counters[i][EQUAL] + counters[i][AFTER]);

        System.out.println("For " + ANNOTATION_TYPES[i] + " precision = " + precision
            + " relaxedPrecision = " + relaxedPrecision + "; recall = " + recall);
        fmeasure = 2 * precision * recall / (precision + recall);
        System.out.println("F-measure = " + fmeasure);

        fmeasure = 2 * relaxedPrecision * recall / (relaxedPrecision + recall);
        System.out.println("relaxed F-measure = " + fmeasure);

        System.out.println("counters[i][EQUAL] = " + counters[i][EQUAL]);
        System.out.println("counters[i][BEFORE] = " + counters[i][BEFORE]);
        System.out.println("counters[i][AFTER] = " + counters[i][AFTER]);
        System.out
            .println("counters[i][OVERLAP_BUT_NOT_EQUAL] = " + counters[i][OVERLAP_BUT_NOT_EQUAL]);
      }

    } catch (SAXParseException spe) {
      System.err.println("Error happens when parsing the inputs.");
    } finally {
      // Close the document reader
      if (null != reader)
        reader.remove();
    }
  }

  @SuppressWarnings("unused")
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

  // private static void compareAnnots(ArrayList<Node> expected, ArrayList<Span> actual, int[]
  // counters) {
  private void compareAnnots(ArrayList<Node> expectedAnnots, ArrayList<Span> actual, int which) {
    // Span span = new Span();

    int begin = 0;
    int end = 0;
    String text = "";
    String type = "";

    int lastIdx = 0;

    for (int i = 0; i < expectedAnnots.size();) {
      Node annotation = expectedAnnots.get(i);

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
        System.out.println(String.format("Missing %s: %s[%d-%d]", type, text, begin, end));
        // missingAnnotations.add(new SimpleSpan(annotation));

        counters[which][AFTER]++;
        i++;
      }

      boolean moveOn = true;

      for (int k = lastIdx; k < actual.size() && moveOn;) {
        Span annot = actual.get(k);

        int relationship = comparePosition(annot, begin, end);

        switch (relationship) {
          case EQUAL:
            // System.out.println("Finding " + type + ": " + text);
            System.out.println(String.format("Finding %s: %s[%d-%d]", type, text, begin, end));
            lastIdx = ++k;
            i++;
            moveOn = false;
            counters[which][EQUAL]++;
            break;
          case BEFORE:
            // System.out.println("Wrong " + type + ": " + annot.getText());
            System.out.println(String.format("Wrong %s: %s[%d-%d]", type, annot.getText(),
                annot.getBegin(), annot.getEnd()));
            lastIdx = ++k;
            moveOn = true;
            counters[which][BEFORE]++;
            break;
          case AFTER:
            // System.out.println("Missing " + type + ": " + text);
            System.out.println(String.format("Missing %s: %s[%d-%d]", type, text, begin, end));
            // missingAnnotations.add(new SimpleSpan(annotation));
            lastIdx = k;
            i++;
            moveOn = false;
            counters[which][AFTER]++;
            break;
          case OVERLAP_BUT_NOT_EQUAL:
            lastIdx = ++k;

            // TODO: find out a way to deal the special symbols.
            String annotText = annot.getText();

            if (INPUT_DOC_TYPE == FILE_TYPE.XML) {
              annotText = annotText.replaceAll("amp;", "&");
            }

            if (annotText.equals(text)) {
              // System.out.println("Finding " + type + ": " + text);
              System.out.println(String.format("Finding %s: %s[%d-%d]", type, text, begin, end));
              counters[which][EQUAL]++;
            } else {
              // System.out.println("Partially wrong annotation " + type + ": " + annot.getText() +
              // " instead of " +
              // text);
              System.out.println(
                  String.format("Partially wrong annotation %s: %s[%d-%d] instead of %s[%d-%d]",
                      type, annotText, annot.getBegin(), annot.getEnd(), text, begin, end));
              counters[which][OVERLAP_BUT_NOT_EQUAL]++;
            }

            int cur = k;

            // Yunyao: added on 09/22/09 to consider the case where multiple actual annotations of
            // the same time
            // overlapping with
            // with one expected annotation
            if (cur < actual.size() - 1) {
              boolean stillOverlap = false;

              do {
                annot = (Span) actual.get(cur++);

                if (comparePosition(annot, begin, end) == OVERLAP_BUT_NOT_EQUAL) {
                  lastIdx = ++k;

                  stillOverlap = true;

                  // System.out.println("Partially wrong annotation " + type + ": " +
                  // annot.getText() + " instead of " +
                  // text);
                  System.out.println(String.format(
                      "Partially wrong annotation %s: %s[%d-%d] instead of %s[%d-%d]", type,
                      annot.getText(), annot.getBegin(), annot.getEnd(), text, begin, end));

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
                  System.out
                      .println(String.format("Finding %s: %s[%d-%d]", type, text, begin, end));
                } else {
                  stillOverlap = false;
                }
              } while (stillOverlap && cur < actual.size());
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
        System.out.println(String.format("Wrong annotation: %s[%d-%d]", annot.getText(),
            annot.getBegin(), annot.getEnd()));
        counters[which][BEFORE]++;
      }
    }
  }

  private int comparePosition(Span span, int begin2, int end2) {
    int begin1 = span.getBegin();
    int end1 = span.getEnd();
    int result = 0;

    // System.out.println("begin1 = " + begin1 + " ; begin2" + begin2 + " ; end1 = " + end1 + " ;
    // end2" + end2);

    if (begin1 == begin2 && end1 == end2) {
      result = EQUAL;
    } else if (end1 < begin2) {
      result = BEFORE;
    } else if (begin1 > end2) {
      result = AFTER;
    } else
      result = OVERLAP_BUT_NOT_EQUAL;

    return result;
  }
}
