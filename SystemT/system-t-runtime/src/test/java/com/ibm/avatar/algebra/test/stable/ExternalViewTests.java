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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.fail;

import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.FieldSetter;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.SpanGetter;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.TextSetter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.util.document.HtmlViz;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.api.CompileAQL;
import com.ibm.avatar.api.CompileAQLParams;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.DocReader;
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.api.exceptions.CompilerException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;

/**
 * Tests for the external views functionality.
 * 
 */
public class ExternalViewTests extends RuntimeTestHarness {

  /** File containing our test document set. */
  public static final File INPUT_DOCS_FILE = new File(TestConstants.ENRON_1K_DUMP);

  // Directory containing input AQL files
  public static final String AQL_DIR = TestConstants.AQL_DIR + "/ExternalViewTests";

  /** Main function for running individual tests without a JUnit harness. */
  public static void main(String[] args) {
    try {

      ExternalViewTests t = new ExternalViewTests();

      t.setUp();

      long startMS = System.currentTimeMillis();

      t.extViewJoinWithDocument();

      long endMS = System.currentTimeMillis();

      t.tearDown();

      double elapsedSec = ((double) (endMS - startMS)) / 1000.0;

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
  }

  /**
   * Test case for bug #168205: ExternalViewScanOp dump() method not in synch with the AOG syntax
   */
  @Test
  public void dumpPlanBug() throws Exception {

    startTest();

    nonModularTestCase();
    compareAgainstExpected("WordAll.htm", true);

    endTest();
  }

  /**
   * Test case for defect - SystemT.Single.getExternalViewSchema() fails in certain cases
   */
  @Test
  public void outputExternalViewBug() throws Exception {

    startTest();

    nonModularTestCase();
    compareAgainstExpected("Word.htm", true);

    endTest();
  }

  /**
   * Test case for defect - SystemT.SIngle.getExternalViewSchema() Verifies that external views can
   * be directly output.
   */
  @Test
  public void noOutputExternalViewBug() throws Exception {

    startTest();

    nonModularTestCase();

    endTest();
  }

  /**
   * Test case for defect - SystemT.Single.getExternalViewSchema() fails in certain cases
   */
  @Test
  public void extViewMergedWithViewBug() throws Exception {
    startTest();
    nonModularTestCase();
    compareAgainstExpected("WordAll.htm", true);

    endTest();
  }

  /**
   * Positive test case for defect - Verifies that externalViewNodes are not merged with normal view
   * nodes when Joined with Document Node
   */
  @Test
  public void extViewJoinWithDocument() throws Exception {
    startTest();
    nonModularTestCase();
    compareAgainstExpected("WordAll.htm", true);

    endTest();
  }

  /**
   * Test case for defect : External views are broken. Runs the test file through the compatibility
   * API.
   */
  @Test
  public void nonModularPassThruTest() throws Exception {

    startTest();

    nonModularTestCase();
    compareAgainstExpected("MyExternalViewCopy.htm", true);
    compareAgainstExpected("SecondExternalViewCopy.htm", true);

    endTest();
  }

  /**
   * Test case for defect : External views are broken. Attempts to compile and run the original,
   * modular test case that was broken in the Jaql/SystemT test harness.
   */
  @Test
  public void passThruTest() throws Exception {

    startTest();

    genericTestCase();

    compareAgainstExpected(true);

    endTest();
  }

  @Test
  public void invalidExtViewTest() throws Exception {
    startTest();

    final File aqlFile = new File(AQL_DIR, getCurPrefix() + ".aql");

    compileAQL(aqlFile, null);

    OperatorGraph og = instantiateOperatorGraph(Constants.GENERIC_MODULE_NAME);

    DocReader docs = new DocReader(INPUT_DOCS_FILE);
    String viewName = "undefinedExternalView";
    try {
      while (docs.hasNext()) {
        Tuple doc = docs.next();

        Map<String, TupleList> extViewTupsMap = new HashMap<String, TupleList>();
        extViewTupsMap.put(viewName, null);

        og.execute(doc, null, extViewTupsMap);
      }
    } catch (TextAnalyticsException e) {
      String[] externalViewNames = {"Word"};
      assertException(e.getCause(), TextAnalyticsException.class.getName(), String.format(
          "The external view '%s' does not exist; the extractor has external views with the following external names: [%s]",
          viewName, String.join(", ", externalViewNames)));
    }

    endTest();
  }

  /*
   * UTILITY METHODS Everything below this line should be a utility method, not a test case.
   */

  /**
   * Generic test case for modular AQL. Compiles all modules in the input directory for the current
   * test case, then pushes tuples through all external views that those modules define.
   */
  private void genericTestCase() throws Exception {
    // Look for AQL modules in the current test case's AQL directory.
    final File aqlDir = new File(AQL_DIR, getCurPrefix());

    File[] moduleDirs = aqlDir.listFiles();
    ArrayList<String> inputModulesList = new ArrayList<String>();
    ArrayList<String> moduleNamesList = new ArrayList<String>();
    for (File dir : moduleDirs) {
      // Ignore files, such as README.txt, in the input AQL dir
      if (dir.isDirectory()) {
        inputModulesList.add(dir.toURI().toString());
        moduleNamesList.add(dir.getName());
      }
    }

    // Create a temporary directory to hold compiled modules.
    File outDir = new File(getCurOutputDir(), "compiledModules");
    outDir.delete();
    outDir.mkdirs();

    String outputURI = outDir.toURI().toString();

    String[] inputModules = new String[inputModulesList.size()];
    inputModules = inputModulesList.toArray(inputModules);

    String[] moduleNames = new String[moduleNamesList.size()];
    moduleNames = moduleNamesList.toArray(moduleNames);

    // Invoke the main SystemT compilation API
    CompileAQLParams params = new CompileAQLParams();
    params.setInputModules(inputModules);
    params.setOutputURI(outputURI);
    params.setTokenizerConfig(getTokenizerConfig());

    try {
      CompileAQL.compile(params);
    } catch (CompilerException e) {
      System.err.printf("Caught CompilerException.  Dumping individual stack traces...\n");
      for (Exception error : e.getSortedCompileErrors()) {
        error.printStackTrace();
      }
      throw e;
    }

    // Instantiate an operator graph with all the modules we just compiled.
    OperatorGraph og = OperatorGraph.createOG(moduleNames, outputURI, null, null);

    pushExtViewTups(og);
  }

  /**
   * Generic test case for those tests that aren't yet converted to modular AQL. Compiles an AQL
   * associated with the test case into the generic module, then pushes tuples through all external
   * views defined by the AQL. This function should go away as soon as the tests are all modular.
   * 
   * @throws Exception
   */
  private void nonModularTestCase() throws Exception {

    // Input AQL file to compile and run
    final File aqlFile = new File(AQL_DIR, getCurPrefix() + ".aql");

    // Compile the AQL using the compatibility API.
    compileAQL(aqlFile, null);

    OperatorGraph og = instantiateOperatorGraph(Constants.GENERIC_MODULE_NAME);

    pushExtViewTups(og);

  }

  /**
   * Subroutine of the generic test case methods.
   * 
   * <pre>
   * For each input document, this populates the external view with a tuple of
   * arbitrary values, based on the schema of the external view: 
   * 	- If the view specifies an int, the value is the size of the Document text. 
   *  - If the view specifies a float, the value is the size of the Document text
   *    divided by the number of fields in the schema.
   *  - If the view specifies a text or span, the value is the first 10 characters 
   *    of the Document text.
   * </pre>
   * 
   * @param og operator graph initialized with the test case's AQL
   */
  @SuppressWarnings("unchecked")
  private void pushExtViewTups(OperatorGraph og) throws Exception, IOException {

    // Open a scan over some documents, and get some information about their
    // schema.
    DocReader docs = new DocReader(INPUT_DOCS_FILE);
    TupleSchema docSchema = docs.getDocSchema();

    // Offsets into each document where we will place an external view
    // annotation
    final int BEGIN_OFFSET = 0;
    final int END_OFFSET = 10;

    // Determine the names of all external views.
    String[] externalViewNames = og.getExternalViewNames();
    System.err.printf("External view names: %s\n", Arrays.toString(externalViewNames));

    // Store the schemas and types of the external views
    ArrayList<TupleSchema> externalViewSchemas = new ArrayList<TupleSchema>();
    Map<String, FieldType> types = new HashMap<String, FieldType>();

    // Store the accessors to get at the fields of the external view
    Map<String, FieldSetter<?>> setters = new HashMap<String, FieldSetter<?>>();

    // Iterate over every external view field and store the appropriate type
    // and accessor for that field
    for (String externalViewName : externalViewNames) {

      TupleSchema externalViewSchema = og.getExternalViewSchema(externalViewName);
      externalViewSchemas.add(externalViewSchema);

      for (String fieldName : externalViewSchema.getFieldNames()) {
        FieldType fieldType = externalViewSchema.getFieldTypeByName(fieldName);

        String fieldKey = generateFieldKey(externalViewName, fieldName);

        types.put(fieldKey, fieldType);
        if (fieldType.getIsIntegerType()) {
          setters.put(fieldKey, externalViewSchema.intSetter(fieldName));
        } else if (fieldType.getIsFloatType()) {
          setters.put(fieldKey, externalViewSchema.floatSetter(fieldName));
        } else if (fieldType.getIsText()) {
          setters.put(fieldKey, externalViewSchema.textSetter(fieldName));
        } else if (fieldType.getIsSpan()) {
          setters.put(fieldKey, externalViewSchema.spanSetter(fieldName));
        } else {
          throw new RuntimeException(
              String.format("Invalid type '%s' in field '%s' of external view '%s'",
                  fieldType.toString(), fieldName, externalViewName));
        }
      }

    }

    TupleList externalViewTups;
    Tuple externalViewTup;

    // Initialize the HTMLViz objects that generate pretty HTML output
    System.err.printf("Output types: %s\n", og.getOutputTypeNames());
    FieldGetter<Text> labelGetter = null;
    if (docSchema.containsField(Constants.LABEL_COL_NAME)) {
      labelGetter = docSchema.textAcc(Constants.LABEL_COL_NAME);
    }
    FieldGetter<Text> docTextGetter = null;
    if (docSchema.containsField(Constants.DOCTEXT_COL)) {
      docTextGetter = docSchema.textAcc(Constants.DOCTEXT_COL);
    }
    HashMap<String, HtmlViz> outputToViz = initializeHtmlOutput(og, labelGetter, docTextGetter);

    int ndoc = 0;
    long nchar = 0;
    long ntups = 0;

    // Process the documents one at a time.
    long startMs = System.currentTimeMillis();
    while (docs.hasNext()) {
      Tuple doc = docs.next();
      int docSize =
          docs.getDocSchema().textAcc(Constants.DOCTEXT_COL).getVal(doc).getText().length();
      int endOffset = docSize < END_OFFSET ? docSize : END_OFFSET;

      // Populate the external views
      Map<String, TupleList> extViewTupsMap = new HashMap<String, TupleList>();
      for (int ix = 0; ix < externalViewSchemas.size(); ix++) {

        // Create one external view tuple and populate it
        externalViewTup = externalViewSchemas.get(ix).createTup();

        for (String fieldName : externalViewSchemas.get(ix).getFieldNames()) {
          String fieldKey = generateFieldKey(externalViewNames[ix], fieldName);

          FieldType popFieldType = types.get(fieldKey);
          FieldSetter<?> setter = setters.get(fieldKey);

          // populate an external view field with appropriate data
          // if the field is of type int, use the document size
          // if the field is of type float, use the doc size divided
          // by the # of schema fields
          // if the field is of type text or span, use the first 10
          // characters of the document text,
          // assumed to be in document column "text" of type Text
          if (popFieldType.getIsIntegerType()) {
            ((FieldSetter<Integer>) setter).setVal(externalViewTup, docSize);
          } else if (popFieldType.getIsFloatType()) {
            ((FieldSetter<Float>) setter).setVal(externalViewTup,
                (float) docSize / (float) externalViewSchemas.size());
          } else if (popFieldType.getIsText()) {
            if (docSchema.containsField(Constants.DOCTEXT_COL)) {
              FieldGetter<Text> getDocText = docSchema.textAcc(Constants.DOCTEXT_COL);
              ((TextSetter) setter).setVal(externalViewTup,
                  getDocText.getVal(doc).getText().substring(BEGIN_OFFSET, endOffset));
            } else {
              throw new RuntimeException(
                  "Attempted to populate a Text external view field with the document text when "
                      + "no document text exists.");
            }
          } else if (popFieldType.getIsSpan()) {

            if (docSchema.containsField(Constants.DOCTEXT_COL)) {
              FieldGetter<Text> getDocText = docSchema.textAcc(Constants.DOCTEXT_COL);
              ((FieldSetter<Span>) setter).setVal(externalViewTup,
                  Span.makeBaseSpan(getDocText.getVal(doc), BEGIN_OFFSET, endOffset));
            } else {
              throw new RuntimeException(
                  "Attempted to populate a Span external view with document text when "
                      + "no document text exists.");
            }
          } else {

            throw new RuntimeException(
                String.format("Invalid type '%s' in field '%s' of external view '%s'",
                    popFieldType.toString(), fieldName, externalViewNames[ix]));
          }
        }

        // Make a list of tuples
        externalViewTups = new TupleList(externalViewSchemas.get(ix));
        externalViewTups.add(externalViewTup);

        // Push the list of tuples to SystemT
        extViewTupsMap.put(externalViewNames[ix], externalViewTups);
      }

      // Annotate the current document, generating every single output
      // type that the annotator produces.
      // Final argument is an optional list of what output types to
      // generate.
      // Map<String, TupleList> annots = syst.annotateDoc(doc, null,
      // extViewTupsMap);
      Map<String, TupleList> annots = og.execute(doc, null, extViewTupsMap);
      ndoc++;
      nchar += docSize;

      for (TupleList tlist : annots.values()) {
        ntups += tlist.size();
      }

      if (0 == ndoc % 1000) {
        printStatus(ndoc, nchar, ntups, startMs);
      }

      writeToHtml(doc, annots, outputToViz);

    }

    // Close the output files
    closeHTMLOutput(outputToViz);

    // Close the document reader
    docs.remove();

    // truncateExpectedFiles();
    truncateOutputFiles(true);
  }

  /**
   * Generates a consistent format for a field key for later retrieval of field setter
   */
  private static String generateFieldKey(String extViewName, String fieldName) {
    return extViewName + Constants.MODULE_ELEMENT_SEPARATOR + fieldName;
  }

  private static void printStatus(int ndoc, long nchar, long ntups, long startMs) {
    long elapsedMs = System.currentTimeMillis() - startMs;
    double elapsedSec = (double) elapsedMs / 1000.0;
    double docPerSec = (double) ndoc / elapsedSec;
    double kcharPerSec = (double) nchar / elapsedSec / 1024.0;

    double numTupsPerDoc = (double) ntups / ndoc;

    System.err.printf(
        "\n\n***** %5d docs in %5.1f sec -->"
            + " %5.1f doc/sec, %5.1f kchar/sec %5.1f tuples/doc\n",
        ndoc, elapsedSec, docPerSec, kcharPerSec, numTupsPerDoc);
  }

  /**
   * Initialize the HTML Visualizers used to write the output of SystemT in HTML format.
   * 
   * @param og preinitialized operator graph whose output we want to dump to HTML
   * @param docLabelGetter accessor for retrieving the "label" column from document tuples, or null
   *        if there is no label column
   * @param docTextGetter
   * @return visualizer objects, indexed by view name
   * @throws IOException
   * @throws Exception
   */
  private HashMap<String, HtmlViz> initializeHtmlOutput(OperatorGraph og,
      FieldGetter<Text> docLabelGetter, FieldGetter<Text> docTextGetter)
      throws IOException, Exception {

    // Assume we're using the default document schema.
    // TupleSchema docSchema = DocScanInternal.createLabeledSchema ();

    HashMap<String, HtmlViz> outputToViz = new HashMap<String, HtmlViz>();
    for (String outputTypeName : og.getOutputTypeNames()) {

      // Create output files to write the HTML to.
      File outFile = new File(getCurOutputDir(), String.format("%s.htm", outputTypeName));

      // Fetch information about the schema of this output, then
      // create an accessor for getting at the rightmost span in the
      // output schema.
      TupleSchema schema = og.getSchema(outputTypeName);

      String lastSpanCol = schema.getLastSpanCol();
      SpanGetter spanGetter = (null == lastSpanCol) ? null : schema.asSpanAcc(lastSpanCol);

      // Use the accessor to create an object that will write the
      // contents of the indicated view into the file.
      HtmlViz viz = new HtmlViz(outFile, spanGetter, docLabelGetter, docTextGetter);

      viz.setTupSchema(schema);
      viz.setGenerateTupleTable(true);
      // viz.setDocLabelCol (Constants.LABEL_COL_NAME);

      outputToViz.put(outputTypeName, viz);
    }

    return outputToViz;
  }

  /**
   * Close the HTML Visualizer objects.
   */
  public void closeHTMLOutput(HashMap<String, HtmlViz> outputToViz) throws IOException {

    if (null != outputToViz)
      for (HtmlViz viz : outputToViz.values()) {
        viz.close();
      }
  }

  /**
   * Write SystemT annotations to HTML format. Heavy-lifting is done by the {@link HTMLViz} class.
   * 
   * @param docTup contents of Document view for current document
   * @param annots Set of SystemT annotations, indexed by view name.
   * @param outputs set of output handles on open files
   * @throws IOException
   * @throws Exception
   */
  private void writeToHtml(Tuple docTup, Map<String, TupleList> annots,
      HashMap<String, HtmlViz> outputToViz) throws IOException, Exception {

    for (String outputTypeName : annots.keySet()) {
      HtmlViz viz = outputToViz.get(outputTypeName);
      viz.addDoc(docTup, annots.get(outputTypeName));
    }
  }

}
