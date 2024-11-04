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
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.DocReader;
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.logging.Log;

/**
 * Various tests that run over our Craigslist job posting documents, mostly aimed for the SIGMOD
 * 2009 demo.
 */
public class CraigslistTests extends RuntimeTestHarness {

  /**
   * Directory where AQL files referenced in this class's regression tests are located.
   */
  public static final String AQL_FILES_DIR = TestConstants.AQL_DIR + "/CraigslistTests";

  /**
   * Directory where dictionary files referenced in this class's regression tests are located.
   */
  public static final String DICTS_DIR = AQL_FILES_DIR;

  /** Name of the file containing documents to drive the tests in this class. */
  public static final String DOCS_FILE =
      TestConstants.TEST_DOCS_DIR + "/craigslist/softwareJobs.zip";

  public static void main(String[] args) {
    try {

      CraigslistTests t = new CraigslistTests();

      t.setUp();

      long startMS = System.currentTimeMillis();

      t.noDetagTest();

      long endMS = System.currentTimeMillis();

      t.tearDown();

      double elapsedSec = ((double) (endMS - startMS)) / 1000.0;

      System.err.printf("Test took %1.3f sec.\n", elapsedSec);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Scan over the Enron database; set up by setUp() and cleaned out by tearDown()
   */
  // private DocScan scan = null;
  private File defaultDocsFile = new File(DOCS_FILE);

  @Before
  public void setUp() throws Exception {

    // Renice the current thread to avoid locking up the entire system.
    Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

    // scan = DocScan.makeFileScan(new File(DOCS_FILE));

    setDataPath(DICTS_DIR);

    // Make sure that we don't dump query plans unless a particular test
    // requests it.
    // this.dumpAOG = false;

    // Make sure our log messages get printed!
    Log.enableAllMsgTypes();
  }

  @After
  public void tearDown() {
    // scan = null;

    Log.info("Done.");
  }

  /** Test of the full annotator pipeline for the SIGMOD 09 demo. */
  @Test
  public void demoTest() throws Exception {

    startTest();

    setPrintTups(true);

    genericTestCase("demo");

    endTest();
  }

  /**
   * Test of the full annotator pipeline for the SIGMOD 09 demo, producing MashupHub-style XMl
   * output.
   */
  @Test
  public void demoTestXML() throws Exception {
    startTest();

    genericXMLTestCase("demo");

    endTest();
  }

  /**
   * Test of the version of the "skills" annotator without any detagging.
   */
  @Test
  public void noDetagTest() throws Exception {

    startTest();

    setDataPath(TestConstants.AQL_DIR + "/sigmodDemo/dictionaries");

    // Use the detagged docs.
    // scan = DocScan.makeFileScan(new File(TestConstants.TEST_DOCS_DIR
    // + "/craigslist/softwareJobs-detagged.zip"));

    this.defaultDocsFile =
        new File(TestConstants.TEST_DOCS_DIR + "/craigslist/softwareJobs-detagged.zip");

    genericTestCase("skills_nodetag");

    endTest();
  }

  /**
   * Test that compiles the SIGMOD 09 demo annotators into an AOG file, to make sure that they still
   * compile. Also useful for setting up the demo itself.
   */
  @Test
  public void compileTest() throws Exception {
    startTest();

    File AQLFILE = new File(AQL_FILES_DIR, "demo.aql");
    compileAQL(AQLFILE, null);

    endTest();
  }

  /**
   * Set this flag to TRUE to make {@link #genericTestCase(String, ImplType)} print out query plans
   * to STDERR.
   */
  // private boolean dumpAOG = false;

  /**
   * A generic AQL test case. Takes a prefix string as argument; runs the file prefix.aql and sends
   * output to testdata/regression/output/prefix. Also dumps the generated AOG plan to a file in the
   * output directory.
   */
  private void genericTestCase(String prefix) throws Exception {
    String AQLFILE_NAME = String.format("%s/%s.aql", AQL_FILES_DIR, prefix);

    // Parse the AQL.
    Log.info("Compiling AQL file '%s'", AQLFILE_NAME);

    runNonModularAQLTest(defaultDocsFile, AQLFILE_NAME);

  }

  /**
   * Generic test case that produces MashupHub style XML output. Output goes into a directory called
   * [prefix]_xml, on the assumption that there may also be a conventional test called [prefix].
   */
  private void genericXMLTestCase(String prefix) throws Exception {
    File AQL_FILE = new File(AQL_FILES_DIR, String.format("%s.aql", prefix));
    String SEARCH_PATH = AQL_FILES_DIR;

    // Create an output file; we'll put all the annotations into one file.
    File OUTPUT_FILE = new File(getCurOutputDir(), "annotations.xml");
    PrintWriter out =
        new PrintWriter(new OutputStreamWriter(new FileOutputStream(OUTPUT_FILE), "UTF-8"));

    // Parse and compile the AQL.
    compileAQL(AQL_FILE, SEARCH_PATH);
    OperatorGraph og = instantiateOperatorGraph(Constants.GENERIC_MODULE_NAME);

    Map<String, TupleSchema> outputSchema = og.getOutputTypeNamesAndSchema();

    DocReader reader = new DocReader(defaultDocsFile);

    // Connect to our document scan.
    // MemoizationTable mt = new MemoizationTable(scan);

    // while (mt.haveMoreInput()) {
    while (reader.hasNext()) {

      // Tuple doc = scan.getNextDocTup(mt);
      Tuple doc = reader.next();
      Map<String, TupleList> annotations = og.execute(doc, null, null);

      // Generate the XML
      out.print("<mhub:entry>\n");

      for (Map.Entry<String, TupleList> entry : annotations.entrySet()) {
        String outputName = entry.getKey();
        TupleList tups = entry.getValue();
        TupleSchema schema = outputSchema.get(outputName);

        TLIter itr = tups.iterator();

        while (itr.hasNext()) {
          Tuple tup = itr.next();

          out.printf("    <mhub:%s>\n", outputName);

          for (int i = 0; i < schema.size(); i++) {

            Object field = schema.getCol(tup, i);

            FieldType type = schema.getFieldTypeByIx(i);
            String fieldName = schema.getFieldNameByIx(i);

            Span s = null;
            if (type.getIsSpan()) {
              try {
                s = (Span) field;
              } catch (Exception e) {
                System.err.flush();
                Thread.sleep(20);
                System.out.printf("\t i = %s\n\t type = %s\n\t fieldName = %s\n\t field = %s(%s)\n",
                    i, type, fieldName, field.getClass().getSimpleName(), field);
                System.out.printf("schema = %s\n", schema);
                System.out.flush();
                out.close();
                throw e;
              }
              if ("reference".equals(fieldName)) {
                // SPECIAL CASE: The "reference" column
                int length = s.getEnd() - s.getBegin();
                out.printf("<mhub:reference" + " length=\"%d\" " + "position=\"%d\">%s"
                    + "</mhub:reference>\n", length, s.getBegin(), s.getText());
                // END SPECIAL CASE
              } else {
                // NORMAL CASE: Print text of span
                out.printf("<mhub:%s>%s</mhub:%s>\n", fieldName, s.getText(), fieldName);
              }
            }

          }

          out.printf("    </mhub:%s>\n", outputName);
        }

      }

      out.print("</mhub:entry>\n");
    }

    out.close();

    // Close the document reader
    reader.remove();

  }

}
