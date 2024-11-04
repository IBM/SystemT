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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.util.file.SearchPath;
import com.ibm.avatar.algebra.util.test.MemoryProfiler;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.algebra.util.test.TestUtils;
import com.ibm.avatar.aog.ParseException;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.DocReader;
import com.ibm.avatar.api.EmailChunker;
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.api.exceptions.InvalidOutputNameException;

/**
 * Tests of running operator graphs in "push mode", where documents are pushed in one at a time.
 * 
 */
public class PushModeTests extends RuntimeTestHarness {

  @SuppressWarnings("unused")
  private static final String AOG_FILES_DIR = TestConstants.AOG_DIR + "/pushModeTests";

  /** File containing our test document set. */
  public static final File INPUT_DOCS_FILE = new File(TestConstants.ENRON_1K_DUMP);

  public static void main(String[] args) {
    try {

      PushModeTests t = new PushModeTests();

      t.setUp();

      long startMS = System.currentTimeMillis();

      t.lotusMockup();

      long endMS = System.currentTimeMillis();

      t.tearDown();

      double elapsedSec = ((double) (endMS - startMS)) / 1000.0;

      System.err.printf("Test took %1.3f sec.\n", elapsedSec);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /** We scan documents so that we can push them into the operator graph. */
  // private DocScan docscan;
  // private Iterator<String> itr;

  @Before
  public void setUp() throws Exception {
    // docscan = new DBDumpFileScan(INPUT_DOCS_FILE);
    // Iterator<String> itr = DocReader.makeDocTextItr(INPUT_DOCS_FILE);

    // Renice the current thread to avoid locking up the entire system.
    Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
  }

  @After
  public void tearDown() {
    // Restore original value to avoid screwing with other tests.
    // Planner.restoreDefaultSentence();
  }

  /**
   * Example code for the Lotus code transfer. Instantiates the named entity annotators as an
   * operator graph and feeds documents through one at a time, randomly enabling and disabling
   * various output annotation types.
   */
  @Test
  public void lotusMockup() throws Exception {

    startTest();

    // File containing the AQL spec for the named-entity annotators.
    final String AQLFILE = TestConstants.AQL_DIR + "/lotus/namedentity-sekar.aql";

    // File containing 1000 documents to use as input.
    final String DOCSFILE = TestConstants.ENRON_1K_DUMP;

    // We'll dump the annotations we receive to the following CSV file:
    final File OUTFILE = new File(getCurOutputDir(), "lotusMockup.csv");

    // "Gold standard" output file for comparison.
    final File EXPECTED_FILE = new File(getCurExpectedDir(), "lotusMockup.csv");

    // ////////////////////////////////////////////////////////////////////
    // SETUP

    FileWriter out = new FileWriter(OUTFILE);
    out.write("docid,type,begin,end,text\n");

    // The file containing documents is in DB2 comma-delimited dump format.
    // Manually instantiate a scan operator to get at the text fields of the
    // document.
    // DocScan docscan = new DBDumpFileScan(DOCSFILE);
    DocReader reader = new DocReader(new File(DOCSFILE));

    // Parse and compile the AQL file, then run the file, passing in one
    // document at a time.
    compileAQL(new File(AQLFILE), TestConstants.TESTDATA_DIR);
    OperatorGraph og = instantiateOperatorGraph(Constants.GENERIC_MODULE_NAME);

    // Set up random number generator for consistent results.
    Random rng = new Random(42);

    // MemoizationTable mt = new MemoizationTable(docscan);

    // Create accessors for getting at the first field of each output.
    HashMap<String, FieldGetter<Span>> accessors = new HashMap<String, FieldGetter<Span>>();
    for (Map.Entry<String, TupleSchema> entry : og.getOutputTypeNamesAndSchema().entrySet()) {
      String outputName = entry.getKey();
      AbstractTupleSchema schema = entry.getValue();
      // Assume that the annotation is in field 0 of the tuple.
      FieldGetter<Span> acc = schema.spanAcc(schema.getFieldNameByIx(0));
      accessors.put(outputName, acc);
    }

    // ////////////////////////////////////////////////////////////////////
    // EXECUTION
    int ndoc = 0;
    // while (mt.haveMoreInput()) {
    while (reader.hasNext()) {
      // Unpack the document text from the scan's output.
      // mt.resetCache();
      // Tuple doc = docscan.getNextDocTup(mt);
      Tuple doc = reader.next();

      // Enable/disable various outputs at *random*.
      ArrayList<String> enabledOutputs = new ArrayList<String>();
      for (String outputName : og.getOutputTypeNames()) {
        if (rng.nextDouble() <= 0.5)
          enabledOutputs.add(outputName);
      }

      // Feed the document text into the operator graph.
      Map<String, TupleList> annotations =
          og.execute(doc, enabledOutputs.toArray(new String[0]), null);

      // Retrieve all results for the document text we just pushed
      // through.
      for (String outputName : enabledOutputs) {

        FieldGetter<Span> acc = accessors.get(outputName);

        TupleList resultTups = annotations.get(outputName);
        for (TLIter itr = resultTups.iterator(); itr.hasNext();) {
          Tuple tuple = itr.next();

          Span span = acc.getVal(tuple);

          // Unpack the properties of the annotation.
          int begin = span.getBegin();
          int end = span.getEnd();
          String text = span.getText();

          // Generate a line of output.
          String outputLine =
              String.format("%d,%s,%d,%d,\"%s\"\n", ndoc, outputName, begin, end, text);
          out.write(outputLine);
        }
      }

      ndoc++;
    }

    // Tell the graph runner to clean up.
    out.close();

    // Close the document reader
    reader.remove();

    // Make sure that we generated the output we were expecting to generate.
    System.err.printf("Comparing output files.\n");
    TestUtils.compareFiles(EXPECTED_FILE, OUTFILE, 0, 1000);
  }

  /*
   * Constants for running the production eDiscovery annotator.
   */
  public static final File ANNOTATORS_PROJECT = SpeedTests.ANNOTATORS_PROJECT;
  public static final File EDISCOVERY_AQLFILE = SpeedTests.EDISCOVERY_AQLFILE;
  public static final String EDISCOVERY_DICT_PATH = SpeedTests.EDISCOVERY_DICT_PATH;
  public static final String EDISCOVERY_INCLUDE_PATH = SpeedTests.EDISCOVERY_INCLUDE_PATH;

  /** Test of processing a very large document in smaller chunks. */
  @Test
  public void chunkBigDoc() throws Exception {
    startTest();

    // final String DOCS_FILE_NAME = SpeedTests.TEN_MEG_DOC_TARFILE;
    final String DOCS_FILE_NAME = SpeedTests.ONE_MEG_DOC_TARFILE;

    final File PERSON_OUTPUT_FILE = new File(getCurOutputDir(), "person.txt");
    final File ORG_OUTPUT_FILE = new File(getCurOutputDir(), "organization.txt");

    runEdiscovery(DOCS_FILE_NAME, PERSON_OUTPUT_FILE, ORG_OUTPUT_FILE, 1);

  }

  /**
   * Run the eDiscovery annotators in push mode, and check results for Person and Org. Assumes that
   * this.util has been set up with the appropriate directories.
   */
  private void runEdiscovery(final String docsFileName, final File personOutputFile,
      final File orgOutputFile, int numDocsToRun) throws Exception, ParseException,
      UnsupportedEncodingException, FileNotFoundException, IOException {
    {
      if (false == ANNOTATORS_PROJECT.exists()) {
        // SPECIAL CASE: User hasn't checked out a copy of
        // AnnotatorTester.
        System.err.printf("No Annotators project.  Exiting.\n");
        return;
        // END SPECIAL CASE
      }

      DocReader reader = new DocReader(new File(docsFileName));

      // MemoizationTable mt = new MemoizationTable(ds);

      compileAQL(EDISCOVERY_AQLFILE, EDISCOVERY_INCLUDE_PATH + ";" + EDISCOVERY_DICT_PATH);
      OperatorGraph runner = instantiateOperatorGraph(Constants.GENERIC_MODULE_NAME);

      Map<String, TupleSchema> outputSchema = runner.getOutputTypeNamesAndSchema();

      // ***************************************************************
      // Comment out the following line when generating "gold standard"
      // results.
      runner.setChunker(new EmailChunker());
      // ***************************************************************

      long startMs = System.currentTimeMillis();
      int ndoc = 0;
      Map<String, TupleList> annotations = new HashMap<String, TupleList>();
      // while (mt.haveMoreInput() && ndoc < numDocsToRun) {
      while (reader.hasNext() && ndoc < numDocsToRun) {
        // Tuple doctup = ds.getNextDocTup(mt);
        Tuple docTup = reader.next();
        annotations = runner.execute(docTup, null, null);
        System.err.printf("Processed %d docs\n", ++ndoc);
      }
      long endMs = System.currentTimeMillis();

      long elapsedMs = endMs - startMs;

      System.err.printf("Pushed documents through in %1.1f sec\n", (double) elapsedMs / 1000);

      // System.exit(0);

      // Dump the results in text format.
      writeResultsText(personOutputFile, "com.ibm.systemT.Person", annotations, outputSchema);
      writeResultsText(orgOutputFile, "com.ibm.systemT.Organization", annotations, outputSchema);

      // Make sure we generated the proper result.
      // util.compareAgainstExpected(false);

      MemoryProfiler.dumpHeapSize("After test");

      // Close the document reader
      reader.remove();
    }
  }

  /**
   * Test of processing a very large document in smaller chunks. Uses documents generated from the
   * Enron dataset.
   */
  @Test
  public void chunkBigDoc2() throws Exception {
    startTest();

    final String SMALL_DOCS_FILE_NAME = TestConstants.ENRON_SAMPLE_ZIP;
    final int DOC_SIZE_TARGET_KB = 1000;

    final String BIG_DOCS_FILE_NAME = getCurOutputDir() + "/bigdocs.del";

    // Generate artifical large documents.
    SpeedTests.createBigDocs(SMALL_DOCS_FILE_NAME, BIG_DOCS_FILE_NAME, DOC_SIZE_TARGET_KB);

    final File PERSON_OUTPUT_FILE = new File(getCurOutputDir(), "person.txt");
    final File ORG_OUTPUT_FILE = new File(getCurOutputDir(), "organization.txt");

    runEdiscovery(BIG_DOCS_FILE_NAME, PERSON_OUTPUT_FILE, ORG_OUTPUT_FILE, 1);

  }

  /**
   * Test of the singleton API for pushing documents through an operator graph.
   */
  @Test
  @SuppressWarnings("unused")
  public void singletonAPITest() throws Exception {
    startTest();

    // Should we print the output annotations to STDERR?
    final boolean DUMP_TUPLES = true;

    // Compile the local analysis rules into an AOG file. Create an instance of the Java API.
    File aqlFile = new File(TestConstants.AQL_DIR, "/w3/localAnalysis.aql");
    compileAQL(aqlFile, null);
    OperatorGraph syst = instantiateOperatorGraph(Constants.GENERIC_MODULE_NAME);

    // Open a scan over some documents.
    final String docsFileName = TestConstants.TWITTER_MOVIE_1000;
    DocReader docs = new DocReader(new File(docsFileName));
    TupleSchema docSchema = docs.getDocSchema();

    // Process the documents one at a time.
    int ndoc = 0;
    while (docs.hasNext()) {
      Tuple doc = docs.next();

      // Annotate the current document, generating every single output
      // type that the annotator produces.
      // Final argument is an optional list of what output types to
      // generate.
      Map<String, TupleList> annots = syst.execute(doc, null, null);
      ndoc++;

      System.err.printf("\n***** Document %d:\n", ndoc);

      // Iterate through the outputs, one type at a time.
      if (DUMP_TUPLES) {
        for (String viewName : annots.keySet()) {
          TupleList tups = annots.get(viewName);
          AbstractTupleSchema schema = tups.getSchema();

          System.err.printf("Output View %s:\n", viewName);

          TLIter itr = tups.iterator();
          while (itr.hasNext()) {
            Tuple tup = itr.next();
            System.err.printf("    %s\n", tup);

            // Iterate through the fields of the tuple, to show how
            // to do it.
            // Method #1: Direct access to fields by index
            for (int fieldIx = 0; fieldIx < schema.size(); fieldIx++) {
              String fieldName = schema.getFieldNameByIx(fieldIx);
              Object fieldVal = schema.getCol(tup, fieldIx);
            }

            // Method #2: Create and use accessor objects
            // Creating the accessors -- do this ONCE, ahead of
            // time.
            String fieldName = schema.getLastTextOrSpanCol();
            FieldGetter<Span> accessor = schema.asSpanAcc(fieldName);

            // Using the accessors
            Span span = accessor.getVal(tup);
          }
        }
      }
    }

    // Close the document reader
    docs.remove();
  }

  /**
   * A test case that runs the artificial big document that the Lotus folks provided through the new
   * Java API.
   */
  @Test
  public void bigDocTest() throws Exception {
    startTest();

    final String DOCS_FILE_NAME = SpeedTests.ONE_MEG_DOC_TARFILE;

    if (false == SpeedTests.ANNOTATORS_PROJECT.exists()) {
      // SPECIAL CASE: User hasn't checked out a copy of AnnotatorTester.
      System.err.printf("No AnnotatorTester project.  Exiting.\n");
      return;
      // END SPECIAL CASE
    }

    String dataPathStr = String.format("%s%c%s", EDISCOVERY_DICT_PATH, SearchPath.PATH_SEP_CHAR,
        EDISCOVERY_INCLUDE_PATH);

    // Compile an AQL file.
    compileAQL(EDISCOVERY_AQLFILE, dataPathStr);
    OperatorGraph syst = instantiateOperatorGraph(Constants.GENERIC_MODULE_NAME);

    // Get the document text, and run it through the plan.
    // String docText = DocReader.makeDocTextItr (new File (DOCS_FILE_NAME)).next ();

    // Use chunking to keep processing time under control.
    syst.setChunker(new EmailChunker());

    DocReader docs = new DocReader(new File(DOCS_FILE_NAME));
    while (docs.hasNext()) {
      Tuple doc = docs.next();

      // Annotate the current document, generating every single output
      // type that the extractor produces.
      syst.execute(doc, null, null);

    }

    // Close the document reader
    docs.remove();
  }

  private void writeResultsText(File outputFile, String typeName,
      Map<String, TupleList> annotations, Map<String, TupleSchema> outputSchema)
      throws UnsupportedEncodingException, FileNotFoundException, IOException,
      InvalidOutputNameException {
    TupleList personResults = annotations.get(typeName);
    TupleSchema schema = outputSchema.get(typeName);
    OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(outputFile), "UTF-8");
    out.append(
        String.format("Output of %s annotator in " + "PushModeTests.chunkBigDoc():\n", typeName));

    // Assume we want to output the first column.
    FieldGetter<Span> getFirstCol = schema.spanAcc(schema.getFieldNameByIx(0));

    for (TLIter itr = personResults.iterator(); itr.hasNext();) {
      Tuple personTup = itr.next();
      Span personSpan = getFirstCol.getVal(personTup);
      out.append(personSpan.toString());
      out.append("\n");
    }

    out.close();
  }

}
