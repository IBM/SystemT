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

import java.io.BufferedWriter;
import java.io.CharArrayWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.TextSetter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.scan.DocScanInternal;
import com.ibm.avatar.algebra.util.dict.CompiledDictionary;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.aog.AOGParseTree;
import com.ibm.avatar.aog.AOGParser;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.aql.tam.TAM;
import com.ibm.avatar.aql.tam.TAMSerializer;
import com.ibm.avatar.logging.Log;

public class ConsolidateTests extends RuntimeTestHarness {
  /**
   * Directory containing AOG files referenced by the test cases in this class.
   */
  public static final String AOG_FILES_DIR = TestConstants.AOG_DIR + "/consolidateTest";

  // private File defaultDocsFile = new File (TestConstants.TEST_DOCS_DIR + "/overlap.zip");

  final boolean DUMP_TUPLES = true;

  // Encoding for reading/writing files
  final String FILE_ENCODING = "UTF-8";

  /** Document schema **/
  TupleSchema docSchema;

  /** Getter and setter for the document text **/
  TextSetter setDocText;
  FieldGetter<Text> getDocText;

  /** Getter and setter for the document label **/
  TextSetter setDocLabel;
  FieldGetter<Text> getDocLabel;

  /** Instance of SystemT **/
  OperatorGraph syst;

  /**
   * Main method for invoking one test at a time.
   * 
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    ConsolidateTests t = new ConsolidateTests();
    t.setUp();

    long startMS = System.currentTimeMillis();

    t.graphDumpTest();

    long endMS = System.currentTimeMillis();

    t.tearDown();

    double elapsedSec = ((double) (endMS - startMS)) / 1000.0;
    System.err.printf("Test took %1.3f sec.\n", elapsedSec);
  }

  @Before
  public void setUp() throws Exception {

    Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

    // scan = new ZipFileScan ("testdata/docs/overlap.zip");

    Log.enableAllMsgTypes();
  }

  @After
  public void tearDown() {

    // Deregister the Derby driver so that other tests can connect to other
    // databases.
    try {
      DriverManager.getConnection("jdbc:derby:;shutdown=true");
    } catch (SQLException e) {
      // The shutdown command always raises a SQLException
      // See http://db.apache.org/derby/docs/10.2/devguide/
    }
    System.gc();
  }

  /*
   * check if after resulting AOG can be dumped and read again (case of consolidate with priority,
   * not checked by other tests)
   */
  @Test
  public void graphDumpTest() throws Exception {
    startTest();

    // We use a big test file in the hopes of catching more problems.

    // Compile AQL
    compileAQL(new File(TestConstants.AQL_DIR, "consolidateTests/prioritydirections.aql"),
        TestConstants.TESTDATA_DIR);
    File tamDir = getCurOutputDir();
    TAM tam = TAMSerializer.load(Constants.GENERIC_MODULE_NAME, tamDir.toURI().toString());
    String origaog = tam.getAog();
    Map<String, CompiledDictionary> allCompiledDicts = tam.getAllDicts();

    // Dump the original AOG to a file for checking.
    File origdump = new File(getCurOutputDir(), "graphDumpOrig.aog");
    FileWriter origWriter = new FileWriter(origdump);
    origWriter.write(origaog);
    origWriter.close();
    System.err.printf("Original written to:\n%s\n", origdump);

    // Now parse the AOG and write it out again.
    AOGParser parser =
        new AOGParser(Constants.GENERIC_MODULE_NAME, origaog, allCompiledDicts, null);
    // parser.setDictsPath(TestConstants.TESTDATA_DIR);
    AOGParseTree parsetree = parser.Input();

    // Dump the parse tree to a buffer.
    CharArrayWriter buf = new CharArrayWriter();
    parsetree.dump(new PrintWriter(buf));

    // Dump the resulting AOG back to a file.
    File aogdump = new File(getCurOutputDir(), "graphDump.aog");
    System.out.println(getCurOutputDir());
    OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(aogdump), "UTF-8");

    String secondaog = buf.toString();
    w.write(secondaog);
    // w.write(buf.toString());
    w.close();
    System.err.printf("AOG dump written to:\n%s\n", aogdump);

    // TODO: Ideally we would want to instantiate an OperatorGraph here.
    // This is currently commented out since TAM produce NPE for moduleMetadata.
    // (There is a cumbersome work around in RuntimeTestHarness, generating a fake modelMetadata.)
    // This should be activated once there is a clean way of doing it.
    // @SuppressWarnings("unused")
    // OperatorGraph og = TestUtils.instantiateAOGStrToOG (origaog);

    // Test that we can parse the newly
    parser = new AOGParser(Constants.GENERIC_MODULE_NAME, origaog, allCompiledDicts, null);
    parsetree = parser.Input();
    buf = new CharArrayWriter();
    parsetree.dump(new PrintWriter(buf));
    // System.err.printf("AOG parsed from the dump:\n%s\n", buf.toString());

    compareAgainstExpected(false);

    endTest();
  }

  @Test
  public void priorityWithAnnotsTest() throws Exception {
    startTest();

    // First, compile the AQL annotator into an operator graph and write the
    // graph to a file on disk.
    compileAQL(new File(TestConstants.AQL_DIR, "consolidateTests/prioritydirections.aql"), null);
    // Instantiate SystemT
    syst = instantiateOperatorGraph(Constants.GENERIC_MODULE_NAME);
    System.err.println("Output types: " + syst.getOutputTypeNames());

    // Create a document schema
    docSchema = DocScanInternal.createLabeledSchema();

    // Getter and setter for the document text
    setDocText = docSchema.textSetter(Constants.DOCTEXT_COL);
    setDocLabel = docSchema.textSetter(Constants.LABEL_COL_NAME);
    // Document label
    String label = "overlapDoc_1.txt";
    String text = "abcde joee  John too bcdef  fghi";

    Tuple doc = docSchema.createTup();
    // Fill in the document text in our new tuple
    setDocText.setVal(doc, text);
    setDocLabel.setVal(doc, label);

    Map<String, TupleList> annots = syst.execute(doc, null, null);

    System.err.printf("\n***** Document %s:\n", label);

    // Iterate through the outputs, one type at a time, and print out all annotations.
    if (DUMP_TUPLES) {
      dumpTuples(label, annots, false);
    }

    compareAgainstExpected(true);

    endTest();
  }

  /**
   * Print annotator ourput to console and output files.
   * 
   * @param annots
   */
  private void dumpTuples(String docLabel, Map<String, TupleList> annots, boolean dumpToConsole) {

    try {
      // Iterate through the outputs, one type at a time.
      for (String viewName : annots.keySet()) {

        TupleList tups = annots.get(viewName);
        AbstractTupleSchema schema = tups.getSchema();

        TLIter itr = tups.iterator();

        // Write out all tuples
        BufferedWriter bw =
            new BufferedWriter(new FileWriter(getCurOutputDir() + "/" + viewName + ".txt", true));

        if (dumpToConsole)
          System.err.printf("\n%s:\n", viewName);

        if (itr.hasNext()) {
          bw.write(String.format("\n***** Document %s:\n", docLabel));
        }

        while (itr.hasNext()) {
          Tuple tup = itr.next();

          bw.write("    ");
          if (dumpToConsole)
            System.err.printf("    ");

          //
          for (int fieldIx = 0; fieldIx < schema.size(); fieldIx++) {

            if (fieldIx != 0) {
              bw.write(", ");
              if (dumpToConsole)
                System.err.printf(", ");
            }

            String fieldName = schema.getFieldNameByIx(fieldIx);
            FieldType fieldType = schema.getFieldTypeByIx(fieldIx);
            String fieldVal = null;

            if (fieldType.getIsFloatType())
              fieldVal = schema.floatAcc(fieldName).getVal(tup).toString();
            else if (fieldType.getIsIntegerType())
              fieldVal = schema.intAcc(fieldName).getVal(tup).toString();
            else if (fieldType.getIsSpan())
              fieldVal = schema.spanAcc(fieldName).getVal(tup).getText();
            else if (fieldType.getIsText())
              fieldVal = schema.textAcc(fieldName).getVal(tup).getText();
            else if (fieldType.getIsScalarListType())
              fieldVal = schema.scalarListAcc(fieldName).getVal(tup).toString();

            bw.write(String.format("%s: '%s'", fieldName, fieldVal));
            if (dumpToConsole)
              System.err.printf("%s: '%s'", fieldName, fieldVal);
          }

          bw.newLine();
          if (dumpToConsole)
            System.err.println();
          bw.flush();
        }
        bw.close();

      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

}
