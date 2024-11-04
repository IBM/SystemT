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
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.function.scalar.AutoID;
import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.api.CompileAQL;
import com.ibm.avatar.api.CompileAQLParams;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.DocReader;
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.tam.TAM;
import com.ibm.avatar.aql.tam.TAMSerializer;
import com.ibm.avatar.logging.Log;

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
 * output to be sorted and de-duplicated.
 */
public class RefineEvaluator {

  // Type of input documents: XML, or plain text
  private enum FILE_TYPE {
    XML, TEXT
  }

  // Type of input docs, for ACE and CoNLL gold standards
  // private final static FILE_TYPE INPUT_DOC_TYPE = FILE_TYPE.XML;
  // Type of input docs, for Enron gold standard
  @SuppressWarnings("unused")
  private final static FILE_TYPE INPUT_DOC_TYPE = FILE_TYPE.TEXT;

  // This is used to counter the shift in positions in input text and label due to conversion.
  private static int POSITION_SHIFT = 0;

  private final boolean PRINT_DETAILS = true;

  public static void setPOSITION_SHIFT(int pOSITIONSHIFT) {
    POSITION_SHIFT = pOSITIONSHIFT;
  }

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
  // private final static String TYPE_PREFIX = "";

  // Types of annotations we care about in the test
  // private final static String[] ANNOTATION_TYPES = {"Person", "Organization", "Location"};
  // private final static String[] ANNOTATION_TYPES = {"Person"};

  // private static final String DICTIONARY_DIR = "ediscovery/configs/aql/dictionaries";
  // private static final String INCLUDE_PATH = "ediscovery/configs/aql";
  // private static final String QUERY_DIR = "ediscovery/configs/aql";
  // private static final String QUERY_FILE = QUERY_DIR +
  // "/ne-ediscovery-personorgphoneaddress-new.aql";
  // private static final String DATA_DIR = "resources/data/src";

  // private static final String DICTIONARY_DIR = "Annotators/neEvaluation/GenericNE/dictionaries";
  // private static final String INCLUDE_PATH = "Annotators/neEvaluation";
  // private static final String QUERY_DIR = "testdata/aql/refineWebTests";
  // private static final String QUERY_FILE = QUERY_DIR + "/personBaseTest.aql";
  // private static final String QUERY_FILE = QUERY_DIR + "/ne-library-annotators.aql";
  // private static final String DATA_DIR = "testdata/docs/refineTest";
  public static final String UDF_DIR = "Annotators/neEvaluation/GenericNE/udfjars";

  /** Directories where output files from tests in this class go. */
  // private static final String OUTPUT_DIR = "temp/aqltestout";

  // private static final String IN_DIR = DATA_DIR + "/ace2005testingDoc";
  // private static final String IN_DIR = DATA_DIR + "/ace2005trainingDoc";
  // private static final String EXPECTED_DIR = DATA_DIR + "/ace2005trainingAnnotation";
  // private static final String EXPECTED_DIR = DATA_DIR + "/ace2005testingAnnotation";
  // private static final String IN_DIR = DATA_DIR + "/CoNLL2003trainingDoc";
  // private static final String EXPECTED_DIR = DATA_DIR + "/CoNLL2003trainingAnnotation";

  // private static final String IN_DIR = DATA_DIR + "/Enron-244/enron-244-data";
  // private static final String EXPECTED_DIR = DATA_DIR + "/Enron-244/enron-244-gold-standard";

  // private HashMap<String, ArrayList<Integer>> postiveResultMap;
  // private HashMap<String, ArrayList<Integer>> negativeResultMap;

  private final HashMap<String, ArrayList<Node>> standard;
  private final ArrayList<Integer> actualPos;
  private final ArrayList<Integer> actualNeg;
  private final ArrayList<Integer> overlapNotEqual;
  private final String rewrittenAOG; // the AQL being refined
  private final String viewName;
  private final DocReader docs;
  private ArrayList<Integer> positiveLabels;
  private ArrayList<Integer> negativeLabels;
  private double initialFscore;
  private int totalNumberOfResults;
  // private ArrayList<SimpleSpan> missingAnnotations = new ArrayList<SimpleSpan>();

  private final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

  // counters used to store statistics of comparison results for each type
  // private int[][] counters;

  public RefineEvaluator(String aql, String viewName, DocReader docs, String includePath,
      String dictPath, String udfJarPath) throws IOException, ParseException, Exception,
      com.ibm.avatar.aog.ParseException, ParserConfigurationException, SAXException {

    this.viewName = viewName;
    this.docs = docs;

    standard = new HashMap<String, ArrayList<Node>>();
    actualPos = new ArrayList<Integer>();
    actualNeg = new ArrayList<Integer>();
    overlapNotEqual = new ArrayList<Integer>();
    totalNumberOfResults = 0;

    TAM tam = compileAQL(aql, includePath, dictPath, udfJarPath);
    this.rewrittenAOG = tam.getAog();
  }

  public RefineEvaluator(String aog, String viewName, DocReader docs)
      throws IOException, ParseException, Exception, com.ibm.avatar.aog.ParseException,
      ParserConfigurationException, SAXException {

    this.viewName = viewName;
    this.docs = docs;
    this.rewrittenAOG = aog;

    standard = new HashMap<String, ArrayList<Node>>();
    actualPos = new ArrayList<Integer>();
    actualNeg = new ArrayList<Integer>();
    overlapNotEqual = new ArrayList<Integer>();
    totalNumberOfResults = 0;
  }

  public TAM compileAQL(String aql, String includePath, String dictPath, String udfJarPath)
      throws Exception {

    AutoID.resetIDCounter();

    // String rewrittenAQL = "";

    // AutoID.resetIDCounter();
    // rewrittenAQL = AQLProvenanceRewriter.rewriteAQLString(aql,
    // includePath, dictPath, udfJarPath);

    CompileAQLParams compileParam = new CompileAQLParams();
    compileParam.setInputFile(new File(aql));

    // place TAM in same directory as AQL file
    compileParam.setOutputURI(new File(aql).getParentFile().toURI().toString());

    compileParam.setDataPath(dictPath + ";" + includePath + ";" + udfJarPath);
    compileParam.setPerformSDM(false);
    compileParam.setPerformSRM(false);

    // Compile aql to tam
    CompileAQL.compile(compileParam);

    return TAMSerializer.load(Constants.GENERIC_MODULE_NAME, compileParam.getOutputURI());
  }

  @SuppressWarnings("unused")
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

  @SuppressWarnings("unused")
  private String[] getInDocFromXML(DocumentBuilder builder, File file)
      throws SAXException, IOException {
    Document document;

    File canonicalFile = file.getCanonicalFile();
    document = builder.parse(canonicalFile);

    NodeList texts = document.getElementsByTagName(TEXT_ENTRY);

    String[] strTexts = new String[texts.getLength()];

    for (int i = 0; i < texts.getLength(); i++)
      strTexts[i] = texts.item(i).getTextContent();

    return strTexts;
  }

  private void compareAnnots(ArrayList<Node> expectedAnnots, ArrayList<Span> actual,
      ArrayList<Integer> autoIDs) {
    // Span span = new Span();

    int begin = 0;
    int end = 0;
    String text = "";
    String type = "";

    int lastIdx = 0;

    if (expectedAnnots == null) {
      System.out.println("expected is null");
      return;
    }

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
        // if (PRINT_DETAILS){
        // System.out.println("Missing " + type + ": " + text);
        // System.out.println(String.format("Missing %s: %s[%d-%d]", type, text, begin, end));
        // missingAnnotations.add(new SimpleSpan(annotation));

        // counters[which][AFTER]++;
        i++;
      }

      boolean moveOn = true;

      for (int k = lastIdx; k < actual.size() && moveOn;) {
        Span annot = actual.get(k);

        int relationship = comparePosition(annot, begin, end);

        switch (relationship) {
          case EQUAL:
            // System.out.println("Finding " + type + ": " + text);
            if (PRINT_DETAILS)
              System.out.println(String.format("Finding %s: %s[%d-%d]", type, text, begin, end));
            actualPos.add(autoIDs.get(k));
            System.out.println("added autoid " + autoIDs.get(k));
            lastIdx = ++k;
            i++;
            moveOn = false;
            // counters[which][EQUAL]++;

            int cur = k;

            // Yunyao: added on 09/22/09 to consider the case where multiple actual annotations of
            // the same time
            // overlapping with
            // with one expected annotation
            if (cur < actual.size()) {
              boolean stillOverlap = false;

              do {
                annot = actual.get(cur);

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

                  // counters[which][OVERLAP_BUT_NOT_EQUAL]++;
                  overlapNotEqual.add(autoIDs.get(cur));
                  System.out.println("added autoid " + autoIDs.get(cur));

                }
                /*
                 * Added by Ben to handle case where multiple actual annotations start at the same
                 * position: count the matching tuple, and only move on when no more tuples start at
                 * that position.
                 */
                else if (comparePosition(annot, begin, end) == EQUAL) {
                  // counters[which][EQUAL]++;
                  // actualPos.add(autoIDs.get(cur));
                  // stillOverlap = true;
                  // lastIdx = ++k;
                  // if (PRINT_DETAILS)
                  // System.out.println(String.format("Finding %s: %s[%d-%d]", type, text, begin,
                  // end));
                  System.out.println(
                      String.format("Finding duplicates %s: %s[%d-%d] \n System exiting...", type,
                          text, begin, end));
                  System.exit(1);
                }

                else {
                  stillOverlap = false;
                }
                cur++;

              } while (stillOverlap & cur < actual.size());
            }

            break;
          case BEFORE:
            // System.out.println("Wrong " + type + ": " + annot.getText());
            if (PRINT_DETAILS)
              System.out.println(String.format("Wrong %s: %s[%d-%d]", type, annot.getText(),
                  annot.getBegin(), annot.getEnd()));
            actualNeg.add(autoIDs.get(k));
            System.out.println("added autoid " + autoIDs.get(k));
            lastIdx = ++k;
            moveOn = true;
            // counters[which][BEFORE]++;

            break;
          case AFTER:
            // System.out.println("Missing " + type + ": " + text);
            if (PRINT_DETAILS)
              System.out.println(String.format("Missing %s: %s[%d-%d]", type, text, begin, end));
            // missingAnnotations.add(new SimpleSpan(annotation));
            lastIdx = k;
            i++;
            moveOn = false;
            // counters[which][AFTER]++;
            // actualNeg.add(autoIDs.get(k));

            break;
          case OVERLAP_BUT_NOT_EQUAL:

            // TODO: find out a way to deal the special symbols.
            String annotText = annot.getText();

            // if(INPUT_DOC_TYPE == FILE_TYPE.XML){
            annotText = annotText.replaceAll("amp;", "&");
            // }

            if (annotText.equals(text)) {
              // System.out.println("Finding " + type + ": " + text);
              if (PRINT_DETAILS)
                System.out.println(String.format("Finding %s: %s[%d-%d]", type, text, begin, end));
              // counters[which][EQUAL]++;
              actualPos.add(autoIDs.get(k));
              System.out.println("added autoid " + autoIDs.get(k));

            } else {
              // System.out.println("Partially wrong annotation " + type + ": " + annot.getText() +
              // " instead of " +
              // text);
              if (PRINT_DETAILS)
                System.out.println(
                    String.format("Partially wrong annotation %s: %s[%d-%d] instead of %s[%d-%d]",
                        type, annotText, annot.getBegin(), annot.getEnd(), text, begin, end));
              // counters[which][OVERLAP_BUT_NOT_EQUAL]++;
              overlapNotEqual.add(autoIDs.get(k));
              System.out.println("added autoid " + autoIDs.get(k));
            }

            lastIdx = ++k;
            cur = k;

            // Yunyao: added on 09/22/09 to consider the case where multiple actual annotations of
            // the same time
            // overlapping with
            // with one expected annotation
            if (cur < actual.size()) {
              boolean stillOverlap = false;

              do {
                annot = actual.get(cur);

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

                  // counters[which][OVERLAP_BUT_NOT_EQUAL]++;
                  overlapNotEqual.add(autoIDs.get(cur));

                }
                /*
                 * Added by Ben to handle case where multiple actual annotations start at the same
                 * position: count the matching tuple, and only move on when no more tuples start at
                 * that position.
                 */
                else if (comparePosition(annot, begin, end) == EQUAL) {
                  // counters[which][EQUAL]++;
                  actualPos.add(autoIDs.get(cur));
                  stillOverlap = true;
                  lastIdx = ++k;
                  if (PRINT_DETAILS)
                    System.out
                        .println(String.format("Finding %s: %s[%d-%d]", type, text, begin, end));
                  System.out.println("added autoid " + autoIDs.get(cur));
                }

                else {
                  stillOverlap = false;
                }
                cur++;

              } while (stillOverlap & cur < actual.size());
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
        Span annot = actual.get(k);
        // System.out.println("Wrong annotation" + annot.getType().toPureSpanType() + ": " +
        // annot.getText());
        if (PRINT_DETAILS)
          System.out.println(String.format("Wrong annotation: %s[%d-%d]", annot.getText(),
              annot.getBegin(), annot.getEnd()));
        // counters[which][BEFORE]++;
        actualNeg.add(autoIDs.get(k));
        System.out.println("added autoid " + autoIDs.get(k));
      }
    }
  }

  /*
   * private int comparePosition(Span span, int begin2, int end2) { int begin1 = span.getBegin();
   * int end1 = span.getEnd(); int result = 0; //System.out.println("begin1 = " + begin1 +
   * " ; begin2" + begin2 + " ; end1 = " + end1 + " ; end2" + end2); if (begin1 == begin2 && end1 ==
   * end2) { result = EQUAL; } else if (end1 < begin2) { result = BEFORE; } else if (begin1 > end2)
   * { result = AFTER; } else result = OVERLAP_BUT_NOT_EQUAL; return result; }
   */

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

  /**
   * Read the golden standard from the a folder containing labels.
   */
  public void readGoldenStandard(String goldenFolder) throws Exception {

    // HashMap<String, ArrayList<Node>> standard = new HashMap<String, ArrayList<Node>>();
    // HashMap<String, String> typesEnabled = new HashMap<String, String>();

    File expectedDir = FileUtils.createValidatedFile(goldenFolder);
    String[] expectedFiles = expectedDir.list();
    // sort(expectedFiles);

    try {
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document expected;

      for (int i = 0; i < expectedFiles.length; i++) {

        if (expectedFiles[i].equals("CVS") || expectedFiles[i].equals(".svn"))
          continue;

        // Read the gold standard for the input document
        // System.out.println(expectedFiles[i]);
        File xmlFile = FileUtils.createValidatedFile(goldenFolder, expectedFiles[i]);
        expected = builder.parse(xmlFile);

        NodeList root4Annotators = expected.getElementsByTagName(TEXT_ENTRY);
        // ArrayList<Span>[] actualAnnots = new
        // ArrayList[typesEnabled.size()];
        // ArrayList<Node>[] expectedAnnots = new
        // ArrayList[typesEnabled.size()];
        ArrayList<Node> expectedAnnots = new ArrayList<Node>();

        NodeList children = root4Annotators.item(0).getChildNodes();

        // read expected annotations
        for (int k = 0; k < children.getLength(); k++) {
          Node child = children.item(k);

          if (child.getNodeType() != Node.ELEMENT_NODE)
            continue;
          // expectedAnnots[(Integer)(typesEnabled.get(child.getNodeName()))].add(child);

          // ignore all other types
          if (!child.getNodeName().equalsIgnoreCase(viewName))
            continue;
          expectedAnnots.add(child);
          totalNumberOfResults++;

        }
        standard.put(removeXMLExtension(expectedFiles[i]), expectedAnnots);
      }

    } catch (SAXParseException spe) {
      System.err.println("Error happens when parsing the inputs.");
    }

    Log.debug("Finished reading golden standard of totally " + totalNumberOfResults + " results");
  }

  public int getTotalNumberOfResults() {
    return totalNumberOfResults;
  }

  /**
   * Simulate human labeling: randomly choose "percent" results and use them as labeled results for
   * refinement.
   * 
   * @param removed list of removed ids that shouldn't be considered; this is for iterative
   *        evaluation.
   */
  public void genHumanLabelsRandomByID(float percent, ArrayList<Integer> positiveLabels,
      ArrayList<Integer> negativeLabels, ArrayList<Integer> removed) {

    if (removed == null)
      removed = new ArrayList<Integer>();

    //
    ArrayList<Integer> posLeft = new ArrayList<Integer>(actualPos);
    posLeft.removeAll(removed);
    ArrayList<Integer> negLeft = new ArrayList<Integer>(actualNeg);
    negLeft.removeAll(removed);

    int posSize = posLeft.size();
    int negSize = negLeft.size();

    int total = posSize + negSize;
    int numLabels = Math.round(total * percent);
    Random rand = new Random(1L);

    int count = 0;
    int idx;
    while (count < numLabels) {
      idx = rand.nextInt() % total;
      if (negativeLabels.contains(idx) || positiveLabels.contains(idx))
        continue;
      if (idx >= posSize)
        negativeLabels.add(negLeft.get(idx - posSize));
      else
        positiveLabels.add(posLeft.get(idx));
      count++;
    }
  }

  /**
   * @param percent
   * @param positiveLabels
   * @param negativeLabels
   * @param removed
   */
  public void genHumanLabelsRandomByFile(float percent, ArrayList<Integer> positiveLabels,
      ArrayList<Integer> negativeLabels, ArrayList<Integer> removed) {

    if (removed == null)
      removed = new ArrayList<Integer>();

    //
    ArrayList<Integer> posLeft = new ArrayList<Integer>(actualPos);
    posLeft.removeAll(removed);
    ArrayList<Integer> negLeft = new ArrayList<Integer>(actualNeg);
    negLeft.removeAll(removed);

    int posSize = posLeft.size();
    int negSize = negLeft.size();

    int total = posSize + negSize;
    int numLabels = Math.round(total * percent);
    Random rand = new Random(1L);

    int count = 0;
    int idx;
    while (count < numLabels) {
      idx = rand.nextInt() % total;
      if (negativeLabels.contains(idx) || positiveLabels.contains(idx))
        continue;
      if (idx >= posSize)
        negativeLabels.add(negLeft.get(idx - posSize));
      else
        positiveLabels.add(posLeft.get(idx));
      count++;
    }
  }

  // /**
  // * This function finds the result quality of the input AQL.
  // * It mimics that humans randomly browse a set of files to label. For each file, all results are
  // labeled.
  // * Must also retain the list of file names that users inspected.
  // * @param labelPercentage the percentage of files that the user labels.
  // * @param labeldFiles the files that users labeled (which is the set of files we maintain
  // provenance).
  // * @param labeledTups autoids of results that are labeled. Later use this to get the list of
  // positive and negative
  // results.
  // * @throws Exception
  // */
  // public void getResultLabels(Double labelPercentage, ArrayList<String> labeledFiles,
  // ArrayList<Integer> labeledTups)
  // throws Exception{
  // AutoID.resetIDCounter();
  //
  // // First, generate the index of files to be labeled
  // Random rand = new Random(1L);
  // int count = 0;
  // int idx;
  // ArrayList<Integer> fileIdx = new ArrayList<Integer>();
  // while (count < labelPercentage * INFILE_NUMBER){
  // idx = Math.abs(rand.nextInt()) % INFILE_NUMBER;
  // if (!fileIdx.contains(idx)){
  // fileIdx.add(idx);
  // count++;
  // }
  // }
  //
  // Tuple docTup = null;
  // TupleSchema schema = null;
  // int auto_id;
  // Planner p = new Planner();
  // Tuple tuple;
  //
  // Catalog catalog = new DefaultCatalog();
  //
  // String rewrittenAQL = "";
  //
  // AutoID.resetIDCounter();
  // rewrittenAQL = AQLProvenanceRewriter.rewriteAQLString(aql,
  // includePath, dictPath, udfJarPath);
  //
  // AQLParser parser = new AQLParser(rewrittenAQL);
  // parser.setIncludePath(includePath);
  // catalog.setDictsPath(dictPath);
  // catalog.setUDFJarPath(udfJarPath);
  //
  // parser.setCatalog(catalog);
  // parser.parse();
  // //p.setInlineDicts(true);
  //
  // p.setPerformSDM(false);
  // p.setPerformSRM(false);
  //
  // String aog = p.compileToString(parser.getCatalog());
  //
  // // System.err.print(aog + "\n");
  //
  // AOGRunner runner = AOGRunner.compileStr(aog, dictPath);
  //
  // runner.setBufferOutput();
  // runner.setPushInput(1, null);
  //
  // ArrayList<Span> actualAnnots = new ArrayList<Span>();
  // ArrayList<Integer> autoIDs = new ArrayList<Integer>();
  // String docLabel = null;
  // int fileCount = 0;
  // boolean labeled = false;
  //
  // while (docs.hasNext()) {
  // actualAnnots.clear();
  // autoIDs.clear();
  //
  // docTup = docs.next();
  // runner.setAllOutputsEnabled(false);
  // runner.setAllOutputsEnabled(true);
  // runner.pushDoc(docTup);
  //
  // docLabel = docs.getLabelAcc().getVal(docTup).getText();
  // if (docLabel.startsWith("\\"))
  // docLabel = docLabel.substring(1);
  //
  // Log.debug("File: " + docLabel);
  //
  // if (fileIdx.contains(fileCount)){
  // labeled = true;
  // labeledFiles.add(docLabel);
  // }
  //
  // for (String outputName : runner.getValidOutputNames()) {
  // if (!outputName.equals(viewName))
  // continue;
  //
  // TupleList tups = runner.getResults(outputName);
  //
  // schema = runner.getOutputSchema(outputName);
  //
  // // find the correct autoID name
  // String idString = "";
  // for (int i = 0; i < schema.size(); i++) {
  // idString = schema.getFieldNameByIx(i);
  // if (idString.startsWith(AQLRefine.AUTO_ID))
  // break;
  // }
  //
  // if (!idString.startsWith(AQLRefine.AUTO_ID))
  // continue;
  //
  // FieldGetter<Integer> getInt = schema.intAcc(idString);
  //
  // for (TLIter itr = tups.iterator(); itr.hasNext();) {
  // tuple = itr.next();
  //
  // // if (outputName.equals("PersonPhoneFiltered"))
  // // Log.debug("output");
  //
  // auto_id = getInt.getVal(tuple);
  // // now compare with the gold standard
  //
  // //Tuple tuple = tuples.next();
  // Span annot = (Span) tups.getSchema().getCol(tuple, 0);
  // int begin = annot.getBegin();
  // int end = annot.getEnd();
  // String text = annot.getText();
  // String outputLine = outputName + "," + begin + "," + end + ",\"" + text + "\"\n";
  // System.out.println(outputLine);
  //
  // actualAnnots.add(annot);
  // autoIDs.add(auto_id);
  //
  // }
  // if (labeled){
  // labeledTups.addAll(autoIDs);
  // }
  // }
  // compareAnnots(standard.get(docLabel), actualAnnots, autoIDs, actualPos, actualNeg,
  // overlapNotEqual);
  //
  // labeled = false;
  // fileCount++;
  // }
  // }

  /**
   * This function considers all documents that are being reviewed - find out positive and negative
   * results by comparing with golden standard. After this function being executed, actualPos and
   * actualNeg contains the labels of results, which can be used to refine the query.
   * 
   * @throws Exception
   */
  public void getResultLabels() throws Exception {
    System.out.println("Starting to get result labels...");

    Tuple docTup = null;
    TupleSchema schema = null;
    int auto_id;
    Tuple tuple;

    // OperatorGraph og = TestUtils.instantiateAOGStrToOG (rewrittenAOG);

    final String moduleName = Constants.GENERIC_MODULE_NAME;
    TAM tam = new TAM(moduleName);
    tam.setAog(rewrittenAOG);

    // Prepare a temporary direction and serializing the AOG to file
    File moduleDir = File.createTempFile(System.currentTimeMillis() + "", "");
    moduleDir.delete();
    moduleDir.mkdirs();
    TAMSerializer.serialize(tam, moduleDir.toURI());

    // Instantiate OperatorGraph by reading AOG from disk
    String modulePath = moduleDir.toURI().toString();
    System.err.printf("Using module path '%s'\n", modulePath);

    OperatorGraph og = OperatorGraph.createOG(new String[] {moduleName}, modulePath, null, null);

    moduleDir.delete();

    Map<String, TupleSchema> outputSchema = og.getOutputTypeNamesAndSchema();

    ArrayList<Span> actualAnnots = new ArrayList<Span>();
    ArrayList<Integer> autoIDs = new ArrayList<Integer>();
    String docLabel = null;
    // boolean labeled = false;

    while (docs.hasNext()) {
      actualAnnots.clear();
      autoIDs.clear();

      docTup = docs.next();

      docLabel = (docs.getDocSchema().spanAcc(Constants.LABEL_COL_NAME)).getVal(docTup).getText();
      if (docLabel.startsWith(System.getProperty("file.separator")))
        docLabel = docLabel.substring(1);
      Log.debug("getResultLabels: Extracting " + docLabel);

      Map<String, TupleList> annotations = og.execute(docTup, null, null);

      for (Entry<String, TupleSchema> entry : outputSchema.entrySet()) {

        String outputName = entry.getKey();
        TupleList tups = annotations.get(outputName);

        schema = outputSchema.get(outputName);

        // find the correct autoID name
        String idString = "";
        for (int i = 0; i < schema.size(); i++) {
          idString = schema.getFieldNameByIx(i);
          if (idString.startsWith(AQLRefine.AUTO_ID))
            break;
        }

        if (!idString.startsWith(AQLRefine.AUTO_ID))
          continue;

        FieldGetter<Integer> getInt = schema.intAcc(idString);

        for (TLIter itr = tups.iterator(); itr.hasNext();) {
          tuple = itr.next();

          // temporarily move here to check
          if (!outputName.equals(viewName))
            continue;

          // if (outputName.equals("PersonPhoneFiltered"))
          // Log.debug("output");

          auto_id = getInt.getVal(tuple);
          // now compare with the gold standard

          // Tuple tuple = tuples.next();

          Span annot = (Span) tups.getSchema().getCol(tuple, 0);

          System.out.println(auto_id + ": " + tuple.toString());
          // Span annot = (Span) tups.getSchema().getCol(tuple, 0);
          int begin = annot.getBegin();
          int end = annot.getEnd();
          String text = annot.getText();
          String outputLine = outputName + "," + begin + "," + end + ",\"" + text + "\"\n";
          System.out.println(outputLine);

          actualAnnots.add(annot);
          autoIDs.add(auto_id);
          if (auto_id == 3661 || auto_id == 3691)
            System.out.println("debug");

        }
      }
      compareAnnots(standard.get(docLabel), actualAnnots, autoIDs);

      // labeled = false;
    }
    System.out.println("Finished getting result labels.");
  }

  /**
   * This function is used to label some data - produce a golden standard. Run a high recall query,
   * then manually review the results and correct the mistakes. Output format example: -
   * <annotations> - <text> - <Person> <start>32</start> <end>44</end> <annotation>Brad
   * Richter</annotation> <typeinfo>PER:Individual</typeinfo> </Person> - <Person> <start>60</start>
   * <end>67</end> <annotation>Loraine</annotation> <typeinfo>PER:Individual</typeinfo> </Person>
   * </text> </annotations>
   * 
   * @param view: the view to be labeled.
   * @param colName: the column to be output.
   */
  /*
   * public void labelData(String aql, DocReader docs, String view, String colName, String outDir)
   * throws Exception{ //String tmpFolder = outDir + "/tmp"; //String dataFolder =
   * "testData/docs/refineTest/ensample"; ArrayList<String> fileToKeep = new ArrayList<String>();
   * AutoID.resetIDCounter(); Tuple docTup = null; TupleSchema schema = null; Planner p = new
   * Planner(); Tuple tuple; int count = 0; PrintWriter out; Catalog catalog = new DefaultCatalog();
   * AQLParser parser = new AQLParser(aql); parser.setIncludePath(includePath);
   * catalog.setDictsPath(dictPath); catalog.setUDFJarPath(udfJarPath); parser.setCatalog(catalog);
   * parser.parse(); p.setInlineDicts(true); p.setPerformSDM(false); p.setPerformSRM(false); String
   * aog = p.compileToString(parser.getCatalog()); // System.err.print(aog + "\n"); AOGRunner runner
   * = AOGRunner.compileStr(aog, dictPath); runner.setBufferOutput(); runner.setPushInput(1, null);
   * String docLabel = null; FieldGetter<Span> getSpan = null; Span span; while (docs.hasNext()) {
   * docTup = docs.next(); runner.setAllOutputsEnabled(false); runner.setAllOutputsEnabled(true);
   * docLabel = docs.getLabelAcc().getVal(docTup).getText(); if (docLabel.startsWith("\\")) docLabel
   * = docLabel.substring(1); runner.pushDoc(docTup); for (String outputName :
   * runner.getValidOutputNames()) { if (!outputName.equals(view)) continue; out = new
   * PrintWriter(new File(outDir + "/" + docLabel + ".xml" )); out.write("<annotations>\n<text>\n");
   * TupleList tups = runner.getResults(outputName); if (tups.size() < 1) continue; // move all
   * files that has output to another folder else{ Log.debug(docLabel); fileToKeep.add(docLabel); }
   * schema = runner.getOutputSchema(outputName); getSpan = schema.spanAcc(colName); for (TLIter itr
   * = tups.iterator(); itr.hasNext();) { tuple = itr.next(); span = getSpan.getVal(tuple); count++;
   * out.write("<" + colName + ">\n"); out.write("<start>" + span.getBegin() + "</start>");
   * out.write("<end>" + span.getEnd() + "</end>"); out.write("<annotation>" + span.getText() +
   * "</annotation>"); out.write("</" + colName + ">\n"); } out.write("</text>\n</annotations>\n");
   * out.close(); } } // // now remove all files witout output // File inData = new
   * File(dataFolder); // String[] inFiles = inData.list(); // File file; // for (int i = 0; i <
   * inFiles.length; i++){ // if (!fileToKeep.contains(inFiles[i])){ // file = new File(inData,
   * inFiles[i]); // if (!file.delete()) // Log.debug("Error deleting file " + inFiles[i]); // } //
   * } // Log.info("Totally labeled " + count + "tuples"); }
   */

  // remove .xml from xml file extension.
  public String removeXMLExtension(String s) {
    if (s.endsWith(".xml"))
      return s.replaceAll(".xml", "");
    else if (s.endsWith(".XML"))
      return s.replaceAll(".xml", "");
    else
      return s;
  }

  public String readFile(String inFileName) throws Exception {
    BufferedReader in =
        new BufferedReader(new InputStreamReader(new FileInputStream(inFileName), "UTF-8"));
    String line, result = "";
    while ((line = in.readLine()) != null) {
      // Make sure that 16-bit characters get escaped for clarity.
      result += StringUtils.escapeUnicode(line);
    }
    in.close();
    return result;
  }

  public HashMap<String, ArrayList<Node>> getStandard() {
    return standard;
  }

  public ArrayList<Integer> getActualPos() {
    return actualPos;
  }

  public ArrayList<Integer> getActualNeg() {
    return actualNeg;
  }

  public ArrayList<Integer> getOverlapNotEqual() {
    return overlapNotEqual;
  }

  public ArrayList<Integer> getPositiveLabels() {
    return positiveLabels;
  }

  public void setPositiveLabels(ArrayList<Integer> positiveLabels) {
    this.positiveLabels = positiveLabels;
  }

  public ArrayList<Integer> getNegativeLabels() {
    return negativeLabels;
  }

  public void setNegativeLabels(ArrayList<Integer> negativeLabels) {
    this.negativeLabels = negativeLabels;
  }

  public double getInitialFscore() {
    return initialFscore;
  }

  public void setInitialFscore(double initialFscore) {
    this.initialFscore = initialFscore;
  }
}
