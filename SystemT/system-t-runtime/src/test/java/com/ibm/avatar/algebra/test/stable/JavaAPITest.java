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

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.FieldSetter;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.TextSetter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.util.lang.LangCode;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.api.CompileAQL;
import com.ibm.avatar.api.CompileAQLParams;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.DocReader;
import com.ibm.avatar.api.OperatorGraph;

/**
 * One of the JUnit test cases for the SystemT Java API; also serves as example code for the API.
 * 
 */
public class JavaAPITest extends RuntimeTestHarness {

  /** File containing our test document set. */
  public static final File INPUT_DOCS_FILE = new File(TestConstants.ENRON_1K_DUMP);

  /** Main function for running individual tests without a JUnit harness. */
  public static void main(String[] args) {
    try {

      JavaAPITest t = new JavaAPITest();

      t.setUp();

      long startMS = System.currentTimeMillis();
      t.javaAPITest();

      long endMS = System.currentTimeMillis();

      t.tearDown();

      double elapsedSec = (endMS - startMS) / 1000.0;

      System.err.printf("Test took %1.3f sec.\n", elapsedSec);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Before
  public void setUp() throws Exception {

  }

  @After
  public void tearDown() {
    // Restore original value to avoid screwing with other tests.
    // Planner.restoreDefaultSentence();
  }

  /**
   * Primary test/example of the SystemT Java API.
   */
  @Test
  public void javaAPITest() throws Exception {

    /*
     * CONSTANTS
     */

    // Should we print the output annotations to STDERR?
    final boolean DUMP_TUPLES = false;

    // Input AQL file to compile and run
    final File AQL_FILE = new File(TestConstants.AQL_DIR, "/w3/localAnalysis.aql");

    // File where we put the compiled operator graph for the annotator
    // final File OPERATOR_GRAPH_FILE = new File (getCurOutputDir (), "/localAnalysis.aog");

    // Archive file containing some documents to annotate.
    // SystemT has libraries for reading from DB2 dump files, zip and tar
    // archives, and directories of text files; or you can write your own
    // method to read in files and feed those files.
    final String DOCS_FILE_NAME = TestConstants.TWITTER_MOVIE_1000;

    startTest();

    /*
     * BEGIN TEST
     */

    // First, compile the AQL annotator into an operator graph and write the
    // graph to a file on disk.
    // Note that we could have skipped the "writing to disk" step by using
    // CompileAQL.compileToStr() instead of compile(). Likewise, if the AQL
    // was in a buffer instead of on disk, we could use compileStrToStr()
    compileAQL(AQL_FILE, null);

    // Read the operator graph file into a buffer.
    // String operatorGraphStr = FileUtils.fileToStr(OPERATOR_GRAPH_FILE,
    // FILE_ENCODING);

    // Instantiate the operator graph
    OperatorGraph syst = instantiateOperatorGraph(Constants.GENERIC_MODULE_NAME);

    // Open a scan over some documents, and get some information about their
    // schema.
    DocReader docs = new DocReader(new File(DOCS_FILE_NAME));

    // Process the documents one at a time.
    @SuppressWarnings("all")
    int ndoc = 0;
    while (docs.hasNext()) {
      Tuple doc = docs.next();

      // Annotate the current document, generating every single output
      // type that the annotator produces.
      // Final argument is an optional list of what output types to
      // generate.
      Map<String, TupleList> annots = syst.execute(doc, null, null);
      ndoc++;

      // Iterate through the outputs, one type at a time.
      if (DUMP_TUPLES) {
        System.err.printf("\n***** Document %d:\n", ndoc);

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
            FieldGetter<Span> accessor = schema.spanAcc(fieldName);

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
   * Example of using SystemT Java API and create custom documents.
   * 
   * @throws Exception
   */
  @Test
  public void javaAPICustomDocTest() throws Exception {

    // Contents of documents
    String[] docs = {"Your input document text here.", "Another input document text here."};

    /** Do this once **/
    startTest();

    // Compile a simple AQL to AOG
    CompileAQLParams params = new CompileAQLParams();
    params.setOutputURI(getCurOutputDir().toURI().toString());
    params.setInputStr(
        "create view Doc as select Document.text, Document.label from Document; output view Doc;");
    params.setTokenizerConfig(getTokenizerConfig());

    setOutputDir(getCurOutputDir().getCanonicalPath());

    CompileAQL.compile(params);

    // Create a document schema

    TupleSchema docSchema =
        new TupleSchema(new String[] {Constants.DOCTEXT_COL, Constants.LABEL_COL_NAME},
            new FieldType[] {FieldType.TEXT_TYPE, FieldType.TEXT_TYPE});
    docSchema.setName(Constants.DEFAULT_DOC_TYPE_NAME);

    // Getter and setter for the document text
    TextSetter setDocText = docSchema.textSetter(Constants.DOCTEXT_COL);
    FieldGetter<Text> getDocText = docSchema.textAcc(Constants.DOCTEXT_COL);

    // Getter and setter for the document label
    TextSetter setDocLabel = docSchema.textSetter(Constants.LABEL_COL_NAME);
    FieldGetter<Text> getDocLabel = docSchema.textAcc(Constants.LABEL_COL_NAME);

    // Instantiate SystemT
    OperatorGraph syst = OperatorGraph.createOG(new String[] {Constants.GENERIC_MODULE_NAME},
        params.getOutputURI(), null, null);
    System.err.println("Output types: " + syst.getOutputTypeNames());

    // Create a document tuple
    Tuple doc = docSchema.createTup();

    long startMs = System.currentTimeMillis();
    int ndoc = 0;
    long nchar = 0;
    long ntups = 0;

    /** Do the following multiple times **/

    for (int i = 0; i < docs.length; i++) {

      // Fill in the document text in our new tuple
      setDocText.setVal(doc, docs[i]);

      // Assign a label for our document (optional), and assign it a
      // language
      // (also optional)
      setDocLabel.setVal(doc, "myDoc" + i + ".txt", LangCode.en);

      // Execute the AOG on our document
      Map<String, TupleList> results = syst.execute(doc, null, null);

      ndoc++;
      nchar = getDocText.getVal(doc).getText().length();

      System.err.printf("\n***** Document '%d' with label '%s' and text '%s'\n", ndoc,
          getDocLabel.getVal(doc).getText(), getDocText.getVal(doc).getText());

      // Count the number of result tuples
      for (TupleList tlist : results.values()) {
        ntups += tlist.size();
      }
    }

    // Print some statistics
    printStatus(ndoc, nchar, ntups, startMs);

  }

  /**
   * Example of using SystemT Java API to populate external views. This test reproduces bug #163163:
   * SystemT.SIngle.getExternalViewSchema() fails in certain cases. Currently disabled, since it
   * fails. Whoever fixes the bug should re-enable this test.
   */
  @SuppressWarnings("unused")
  @Test
  public void javaAPIExternalViewTest() throws Exception {

    startTest();

    /*
     * CONSTANTS
     */

    // Offsets into each document where we will place an external view
    // annotation
    final int BEGIN_OFFSET = 0;
    final int END_OFFSET = 10;

    // Name of the external view
    final String EXTERNAL_VIEW_NAME = "Word";

    // Should we print the output annotations to STDERR?
    final boolean DUMP_TUPLES = true;

    // Encoding for reading/writing files
    final String FILE_ENCODING = "UTF-8";

    // Input AQL file to compile and run
    final File AQL_FILE = new File("testdata/aql/JavaAPITests/externalView.aql");

    // Archive file containing some documents to annotate.
    // SystemT has libraries for reading from DB2 dump files, zip and tar
    // archives, and directories of text files; or you can write your own
    // method to read in files and feed those files.
    final String DOCS_FILE_NAME = TestConstants.TWITTER_MOVIE_1000;

    startTest();

    /*
     * BEGIN TEST
     */

    // First, compile the AQL annotator into an operator graph and write the
    // graph to a file on disk.
    // Note that we could have skipped the "writing to disk" step by using
    // CompileAQL.compileToStr() instead of compile(). Likewise, if the AQL
    // was in a buffer instead of on disk, we could use compileStrToStr()
    compileAQL(AQL_FILE, null);

    // Instantiate the operator graph and create an instance of the SystemT
    // Runtime.
    OperatorGraph syst = instantiateOperatorGraph(Constants.GENERIC_MODULE_NAME);

    // Read the operator graph file into a buffer.
    // String operatorGraphStr = FileUtils.fileToStr(OPERATOR_GRAPH_FILE,
    // FILE_ENCODING);

    // Open a scan over some documents, and get some information about their
    // schema.
    DocReader docs = new DocReader(new File(DOCS_FILE_NAME));
    TupleSchema docSchema = docs.getDocSchema();

    // Obtain the schema of the external view
    TupleSchema externalViewSchema = syst.getExternalViewSchema(EXTERNAL_VIEW_NAME);
    FieldSetter<Span> spanSetter =
        externalViewSchema.spanSetter(externalViewSchema.getFieldNameByIx(0));

    TupleList externalViewTups;
    Tuple externalViewTup;

    // Setup a getter to get at the document text
    FieldGetter<Text> getDocText = docSchema.textAcc(Constants.DOCTEXT_COL);

    // Process the documents one at a time.
    int ndoc = 0;
    while (docs.hasNext()) {
      Tuple doc = docs.next();

      // Create one external view tuple and
      // populate it with the span covering the first 10 characters of the
      // document.
      externalViewTup = externalViewSchema.createTup();
      spanSetter.setVal(externalViewTup,
          Span.makeBaseSpan(getDocText.getVal(doc), BEGIN_OFFSET, END_OFFSET));

      // Make a list of tuples
      externalViewTups = new TupleList(externalViewSchema);
      externalViewTups.add(externalViewTup);

      // Push the list of tuples to SystemT
      Map<String, TupleList> extViewTupsMap = new HashMap<String, TupleList>();
      extViewTupsMap.put(EXTERNAL_VIEW_NAME, externalViewTups);

      // Annotate the current document, generating every single output
      // type that the annotator produces.
      // Final argument is an optional list of what output types to
      // generate.
      Map<String, TupleList> annots = syst.execute(doc, null, extViewTupsMap);
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
            FieldGetter<Span> accessor = schema.spanAcc(fieldName);

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
   * Test to prove that tuples populated into external view thru pushExternalViewTups api are
   * retained across annotateDoc call for different documents. Ideally these should not be retained
   * and user has to populate tuples for each document.
   * 
   * @throws Exception
   */
  @Test
  public void javaExternalViewTest2() throws Exception {
    /** Name of the external view */
    final String EXTERNAL_VIEW_NAME = "ExternalView";

    // Input AQL file to compile and run
    final File AQL_FILE = new File("testdata/aql/JavaAPITests/externalView2.aql");

    startTest();

    // First, compile the AQL extractor containing external view into an
    // operator graph string
    System.err.println("Compiling AQL ...");
    compileAQL(AQL_FILE, null);

    // Load TAM
    // TAM tam = TAMSerializer.load (Constants.GENERIC_MODULE_NAME, getCurOutputDir().toURI
    // ().toString ());
    // String operatorGraphString = tam.getAog ();

    // Instantiate the operator graph and create an instance of the Text
    // Analytics Runtime
    System.err.println("Instantiating Text Analytics Runtime ...");
    OperatorGraph syst = instantiateOperatorGraph(Constants.GENERIC_MODULE_NAME);
    System.err.println("Output types: " + syst.getOutputTypeNames());

    // Open a scan over some documents, and get information about their
    // schema.
    DocReader docs = new DocReader(INPUT_DOCS_FILE);
    // TupleSchema docSchema = docs.getDocSchema ();

    // Obtain the schema of the external view
    TupleSchema externalViewSchema = syst.getExternalViewSchema(EXTERNAL_VIEW_NAME);
    System.err.println("External view schema definition: " + externalViewSchema.toString());

    // Prepare setters for different schema fields

    // Setter for the Text field
    TextSetter textSetter = externalViewSchema.textSetter(externalViewSchema.getFieldNameByIx(0));
    // Setter for the Integer field
    FieldSetter<Integer> intSetter =
        externalViewSchema.intSetter(externalViewSchema.getFieldNameByIx(1));
    // Similarly user can create setter for fields with other data types
    // like Float,Span ..etc

    // Preparing Tuplelist with two tuples { {"text1",1}, {"test2",2} }
    TupleList externalViewTups = new TupleList(externalViewSchema);
    Tuple externalViewTup;

    // create tuples to be pushed to AQL runtime
    externalViewTup = externalViewSchema.createTup();
    textSetter.setVal(externalViewTup, "text1");
    intSetter.setVal(externalViewTup, 1);
    externalViewTups.add(externalViewTup);

    externalViewTup = externalViewSchema.createTup();
    textSetter.setVal(externalViewTup, "text2");
    intSetter.setVal(externalViewTup, 2);
    externalViewTups.add(externalViewTup);

    // Pushing the tuples prepared adhering to external view schema to
    // Runtime
    System.err.println("Pushing the tuples prepared to Runtime");
    Map<String, TupleList> extViewTupsMap = new HashMap<String, TupleList>();
    extViewTupsMap.put(EXTERNAL_VIEW_NAME, externalViewTups);

    // Process the documents one at a time.
    System.err.println("Executing Text Analytics ...");
    int ndoc = 0;
    while (docs.hasNext()) {
      Tuple doc = docs.next();

      // Annotate the current document, generating every single output
      // type that the extractor produces.
      // Final argument is an optional list of what output types to
      // generate.

      Map<String, TupleList> results;

      // pass the prepared external view tuple just for the first document
      // all subsequent documents do not have an associated ext view tuple
      if (ndoc == 0) {
        results = syst.execute(doc, null, extViewTupsMap);
      } else {
        results = syst.execute(doc, null, null);
      }

      // For the first document, system should return 2 tuples
      // For remaining documents, system should return 0 tuples as we have
      // populated external view just once
      if (ndoc == 0)
        assertEquals("Number of tuples returned should be 2", 2,
            getCountOfTuplesFromResult(results, "ExternalView_Copy"));
      else
        assertEquals("Number of tuples returned should be 0", 0,
            getCountOfTuplesFromResult(results, "ExternalView_Copy"));

      ndoc++;
      System.err.printf("\n***** Document %d:\n", ndoc);

    }

    // Close the document reader
    docs.remove();
  }

  /**
   * Verifies that bad calls to makeSubSpan are handled correctly. Verifies fix of defect .
   * 
   * @throws Exception
   */
  @Test
  public void makeSubspanTest() throws Exception {

    // Open a scan over some documents, and get some information about their
    // schema.
    DocReader docs = new DocReader(INPUT_DOCS_FILE);
    TupleSchema docSchema = docs.getDocSchema();

    boolean error1 = false;
    boolean error2 = false;

    FieldGetter<Text> getDocText = docSchema.textAcc(Constants.DOCTEXT_COL);

    // Process the documents one at a time.
    if (docs.hasNext()) {
      Tuple doc = docs.next();

      // Create a span covering the first 10 characters of the
      // document.
      Span testSpan = Span.makeBaseSpan(getDocText.getVal(doc), 5, 10);

      // Make a subspan to trigger a non-negative begin offset error message
      try {
        Span.makeSubSpan(testSpan, -1, 6);
      } catch (IllegalArgumentException e1) {
        if (e1.getMessage().endsWith("must be non-negative.")) {
          error1 = true;
        } else {
          Assert.fail("unexpected error message: " + e1.getMessage());
        }
      }

      // Make a subspan to trigger an error message stating the end offset is outside the original
      // span.
      try {
        Span.makeSubSpan(testSpan, 0, 7);

      } catch (IllegalArgumentException e2) {
        if (e2.getMessage().endsWith("outside the original span.")) {
          error2 = true;
        } else {
          Assert.fail("unexpected error message: " + e2.getMessage());
        }
      }

      // Make a subspan that should succeed.
      Span.makeSubSpan(testSpan, 1, 4);

      if (!(error1 && error2)) {
        Assert.fail("Test failed; did not throw expected exceptions.");
      }
    }

    // Close the document reader
    docs.remove();
  }

  /**
   * Test case to verify the specialized getters/setters introduced for Boolean fields. Veriifes the
   * change made for defect#54270.
   */
  @Test
  public void boolAccessorsTest() throws Exception {
    startTest();

    TupleSchema schema = new TupleSchema(new String[] {"id", "isPresent"},
        new FieldType[] {FieldType.INT_TYPE, FieldType.BOOL_TYPE});

    // Create setters
    FieldSetter<Integer> intSetter = schema.intSetter("id");
    FieldSetter<Boolean> boolSetter = schema.boolSetter("isPresent");

    // Create empty tuples
    Tuple tuple1 = schema.createTup(), tuple2 = schema.createTup();

    // Populate tuple1
    intSetter.setVal(tuple1, new Integer(1));
    boolSetter.setVal(tuple1, true);

    // Populate tuple2
    intSetter.setVal(tuple2, new Integer(2));
    boolSetter.setVal(tuple2, false);

    // Create getter for boolean field
    FieldGetter<Boolean> boolAcc = schema.boolAcc("isPresent");

    // Fetch and assert value for boolean field 'isPresent'
    Assert.assertTrue(boolAcc.getVal(tuple1));
    Assert.assertFalse(boolAcc.getVal(tuple2));

    endTest();
  }

  /*
   * PRIVATE METHODS GO HERE
   */

  private int getCountOfTuplesFromResult(Map<String, TupleList> results, String outputViewName) {
    int count = 0;

    // All tuples for given output view
    TupleList tups = results.get(outputViewName);

    // Iterate through the tuples of the output view
    TLIter itr = tups.iterator();
    while (itr.hasNext()) {
      Tuple tup = itr.next();
      System.err.printf("    %s\n", tup);
      count++;
    }

    return count;
  }

  private static void printStatus(int ndoc, long nchar, long ntups, long startMs) {
    long elapsedMs = System.currentTimeMillis() - startMs;
    double elapsedSec = elapsedMs / 1000.0;
    double docPerSec = ndoc / elapsedSec;
    double kcharPerSec = nchar / elapsedSec / 1024.0;

    double numTupsPerDoc = (double) ntups / ndoc;

    System.err.printf(
        "\n\n***** %5d docs in %5.1f sec -->"
            + " %5.1f doc/sec, %5.1f kchar/sec %5.1f tuples/doc\n",
        ndoc, elapsedSec, docPerSec, kcharPerSec, numTupsPerDoc);
  }

}
