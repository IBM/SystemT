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
package com.ibm.avatar.spss;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.SpanGetter;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.TextSetter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.scan.DocScanInternal;
import com.ibm.avatar.algebra.util.document.HtmlViz;
import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.algebra.util.lang.LangCode;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.algebra.util.tokenize.BaseOffsetsList;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.OperatorGraph;

/**
 * This class shows how to use SystemT Java API to create and annotate "custom" documents with token
 * offsets and initial annotations populated from an external source. It also illustrates how to
 * iterate through the output of SystemT. The class uses the following simple data structures:
 * <ul>
 * <li>{@link SPSSDocument} stores external token and annotation information for a single document.
 * It makes use of {@link SPSSAnnotation} to store information associated with a single annotation,
 * namely its begin and end offsets, and lead term.</li>
 * <li>{@link SPSSMetaData} stores field setters for external view tuples that are reused across
 * executions of SystemT annotators on different documents.</li>
 * </ul>
 * 
 */
public class SPSSDriver extends RuntimeTestHarness {

  /**
   * Path to the AQL include directory that all AQL files referenced by our annotator are relative
   * to.
   */
  private String AQL_INCLUDE_DIR =
      TestConstants.TEST_WORKING_DIR + "/testdata/aql/spssSpecificTests";

  /** Path to the top-level AQL file of our annotator. */
  private String AQL_FILE_NAME = AQL_INCLUDE_DIR + "/tlaMockupInitial.aql";

  /**
   * Path to the dictionary include directory that all dictionaries referenced in our annotator are
   * relative to.
   */
  private String DICTIONARY_DIR = AQL_INCLUDE_DIR;

  /** Directory where the output of SystemT goes. */
  private String OUTPUT_DIR = TestConstants.TEST_WORKING_DIR + "/grr";

  /** Should we write out SystemT output in TXT format? */
  private boolean DUMP_TUPLES = true;

  /** Should we write out SystemT output in HTML format? */
  private boolean OUTPUT_HTML = true;

  /** Instance of the System T Runtime. */
  private OperatorGraph syst;

  /** SystemT document schema. */
  private TupleSchema docSchema;

  /** Getters and setters for the document text and label. */
  private FieldGetter<Text> getDocText;
  private TextSetter setDocText;
  private TextSetter setDocLabel;
  private FieldGetter<Text> getDocLabel;

  /**
   * Object for storing accessors for the external view tuples that are populated at runtime. The
   * accessors are reused across invocations of {@link #annotate()}.
   */
  private SPSSMetadata spssMetadata;

  /**
   * HTML Visualizer instances for when we want to output annotations in HTML format.
   */
  private HashMap<String, HtmlViz> outputToViz;

  /** Main entry point. */
  public static void main(String[] args) {
    try {

      SPSSDriver t = new SPSSDriver();

      long startMS = System.currentTimeMillis();

      t.run();

      long endMS = System.currentTimeMillis();

      double elapsedSec = (endMS - startMS) / 1000.0;

      System.err.printf("Test took %1.3f sec.\n", elapsedSec);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Initialize an instance of the SystemT runtime, generate two identical sample document and
   * annotate them with SystemT.
   * 
   * @throws Exception
   */
  public void run() throws Exception {

    // Initialize the SystemT runtime
    initialize();

    // Annotate documents using SystemT. For now, we generate the same
    // document twice, annotating it with SystemT each time to show how it's
    // done.
    for (int i = 0; i < 2; i++) {

      // Generate a sample SPSSDocument instance
      String docLabel = "Doc_" + (i + 1) + ".txt";
      SPSSDocument doc = generateTLAMockupInitialDoc(docLabel);
      System.err.printf("\n\n***** Generating SPSS document '%s' with contents:\n%s\n", docLabel,
          doc);

      // Annotate the document we just created
      annotate(doc);
    }

    // Make sure to close the HTML output visualizers if we used them
    if (OUTPUT_HTML)
      closeHTMLOutput();
  }

  /**
   * Initialize the SystemT runtime instance.
   * 
   * @throws Exception
   */
  public void initialize() throws Exception {

    startTest();

    // Input top-level AQL file
    File aqlFile = FileUtils.createValidatedFile(AQL_FILE_NAME);

    // Tell the SystemT compiler where it should search for AQL and
    // dictionary files when compiling the annotator.
    String dataPath = String.format("%s;%s", AQL_INCLUDE_DIR, DICTIONARY_DIR);

    // Compile the AQL annotator into an operator graph
    compileAQL(aqlFile, dataPath);
    syst = instantiateOperatorGraph(Constants.GENERIC_MODULE_NAME);

    // Create the SystemT document schema
    // String[] nontextNames = new String[0];
    // FieldType[] nontextTypes = new FieldType[0];
    docSchema = DocScanInternal.createLabeledSchema();
    // TupleSchema.makeDocSchema (Constants.DEFAULT_DOC_TYPE_NAME, nontextNames, nontextTypes, new
    // String[] {
    // Constants.DOCTEXT_COL, Constants.LABEL_COL_NAME });

    // Setup getters and setters for the document fields: text and label
    setDocText = docSchema.textSetter(Constants.DOCTEXT_COL);
    getDocText = docSchema.textAcc(Constants.DOCTEXT_COL);
    setDocLabel = docSchema.textSetter(Constants.LABEL_COL_NAME);
    getDocLabel = docSchema.textAcc(Constants.LABEL_COL_NAME);

    /*
     * Store accessors for the external view tuples that are populated at runtime. Assume that each
     * external view has the following schema: (match Span, leadTerm Text). Store the accessors for
     * setting the values of the two fields in a SPSSMetaData object that can be reused for multiple
     * invocations of {@link #annotate(SPSSDocument)}.
     */
    spssMetadata = new SPSSMetadata();
    for (String viewName : syst.getExternalViewNames()) {
      TupleSchema viewSchema = syst.getExternalViewSchema(viewName);
      spssMetadata.addSpanSetter(viewName, viewSchema.spanSetter(viewSchema.getFieldNameByIx(0)));
      spssMetadata.addTextSetter(viewName, viewSchema.textSetter(viewSchema.getFieldNameByIx(1)));

    }

    // Ensure the output directory is present
    ensureOutputDir();

    // Initialize the HTMLViz objects that generate pretty HTML output
    if (true == OUTPUT_HTML)
      outputToViz = initializeHtmlOutput();
  }

  /**
   * Method that does the heavy-lifting. Creates a SystemT document from the input SPSSDocument
   * instance, and annotates it using SystemT.
   * 
   * @param spssDoc
   * @throws Exception
   */
  public void annotate(SPSSDocument spssDoc) throws Exception {

    if (spssDoc == null)
      throw new RuntimeException("Null SPSS Document instance passed to SystemT!");

    /**
     * CREATE A DOCUMENT TUPLE
     */
    Tuple doc = docSchema.createTup();

    // Fill in the document text in our new tuple
    setDocText.setVal(doc, spssDoc.getText());

    // Assign a label for our document (optional), and assign it a
    // language (also optional). The only reason we need to set the document
    // label for SPSS TLA annotators is so that it shows correctly in the
    // HTML output files.
    setDocLabel.setVal(doc, spssDoc.getLabel(), LangCode.en);

    /**
     * POPULATE TOKEN OFFSETS FOR THE DOCUMENT
     */

    // Create a token offset list and populate it with the token offsets
    // stored in the SPSS Document instance.
    BaseOffsetsList customTokOffsetList = new BaseOffsetsList();
    for (int tokIx = 0; tokIx < spssDoc.getTokens().size(); tokIx++)
      customTokOffsetList.addEntry(spssDoc.getTokens().get(tokIx).first,
          spssDoc.getTokens().get(tokIx).second);

    // Obtain the document span object
    Text docSpan = getDocText.getVal(doc);

    // Cache the custom token offsets inside the document span
    docSpan.setCachedTokens(customTokOffsetList);

    /**
     * POPULATE EXTERNAL VIEWS FOR THE DOCUMENT
     */
    // For each external view, create tuples from external annotations
    // and push them into SystemT.
    Map<String, TupleList> extViewTupsMap = new HashMap<String, TupleList>();

    for (String viewName : syst.getExternalViewNames()) {

      // Obtain the external annotations stored in the SPSS Document
      // instance
      ArrayList<SPSSAnnotation> annots = spssDoc.getAnnotations(viewName);

      if (annots != null) {

        TupleSchema externalViewSchema = syst.getExternalViewSchema(viewName);

        // Create a SystemT TupleList to hold the external annotations
        TupleList externalViewTups = new TupleList(externalViewSchema);

        // Iterate through the external annotations, and for each
        // one, do the following:
        for (int ix = 0; ix < annots.size(); ix++) {

          // Create a SystemT tuple
          Tuple externalViewTup = externalViewSchema.createTup();

          // Fill in the span field of the tuple with a new
          // span object over the document text with appropriate
          // begin and end offsets
          spssMetadata.getSpanSetter(viewName).setVal(externalViewTup,
              Span.makeBaseSpan(docSpan, annots.get(ix).getBegin(), annots.get(ix).getEnd()));

          // Fill in the text field of the tuple
          spssMetadata.getTextSetter(viewName).setVal(externalViewTup, annots.get(ix).getInfo());

          // Add the tuple to the list of external view tuples
          externalViewTups.add(externalViewTup);
        }

        // Push the list of external view tuples to SystemT
        if (externalViewTups.size() > 0) {
          extViewTupsMap.put(viewName, externalViewTups);
        }
      }
    }

    /**
     * ANNOTATE THE DOCUMENT WITH SYSTEMT
     */

    // Annotate the current document, generating every single output
    // type that the annotator produces.
    // Final argument is an optional list of what output types to
    // generate.
    // Map<String, TupleList> annots = syst.annotateDoc(doc, null, extViewTupsMap);
    Map<String, TupleList> annots = syst.execute(doc, null, extViewTupsMap);

    /**
     * WRITE OUT THE OUTPUT PRODUCED BY SYSTEMT
     */
    System.err.printf("\n***** Document %s:\n", spssDoc.getLabel());

    // Iterate through the outputs, one type at a time, and print out all
    // annotations in .txt format. Last argument indicates whether we should
    // write out the annotations to the console.
    if (DUMP_TUPLES) {
      ensureOutputDir();
      dumpTuples(spssDoc.getLabel(), annots, true);
    }

    // We can also print out the annotations in HTML format.
    if (OUTPUT_HTML) {
      ensureOutputDir();
      writeToHtml(doc, annots);
    }
  }

  /*
   * INTERNAL METHODS
   */

  private void ensureOutputDir() {
    File outDir = FileUtils.createValidatedFile(OUTPUT_DIR);
    if (!outDir.exists())
      outDir.mkdirs();

  }

  /**
   * Method that shows how to iterate through the output of SystemT and access the field values of
   * tuples in each output view. Writes the output to .txt files in the current working directory.
   * There will be one .txt file for each output view.
   * 
   * @param docLabel Document label
   * @param annots List of tuples in each output view.
   * @param dumpToConsole If true, also dump the output to console.
   */
  private void dumpTuples(String docLabel, Map<String, TupleList> annots, boolean dumpToConsole) {

    try {
      // Iterate through the outputs, one view at a time.
      for (String viewName : annots.keySet()) {

        TupleList tups = annots.get(viewName);
        AbstractTupleSchema schema = tups.getSchema();
        TLIter itr = tups.iterator();

        // Write out all tuples of this view
        BufferedWriter bw =
            new BufferedWriter(new FileWriter(this.getOutputDir() + "/" + viewName + ".txt", true));

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

          // Iterate over the fields of this tuple
          for (int fieldIx = 0; fieldIx < schema.size(); fieldIx++) {

            if (fieldIx != 0) {
              bw.write(", ");
              if (dumpToConsole)
                System.err.printf(", ");
            }

            String fieldName = schema.getFieldNameByIx(fieldIx);
            FieldType fieldType = schema.getFieldTypeByIx(fieldIx);
            String fieldVal = null;

            try {
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
            } catch (NullPointerException e) {
              // Some fields may be null. Make sure we catch the
              // error and print out the null.
              fieldVal = "NULL";
            }

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

  /**
   * Initialize the HTML Visualizers used to write the output of SystemT in HTML format.
   * 
   * @return
   * @throws IOException
   * @throws Exception
   */
  private HashMap<String, HtmlViz> initializeHtmlOutput() throws IOException, Exception {

    HashMap<String, HtmlViz> outputToViz = new HashMap<String, HtmlViz>();
    for (String outputTypeName : syst.getOutputTypeNames()) {

      // Create output files to write the HTML to.
      File outFile = FileUtils.createValidatedFile(this.getOutputDir(),
          String.format("%s.htm", outputTypeName));

      // Fetch information about the schema of this output, then
      // create an accessor for getting at the rightmost span in the
      // output schema.
      TupleSchema schema = syst.getSchema(outputTypeName);

      String lastSpanCol = schema.getLastSpanCol();
      SpanGetter spanGetter = (null == lastSpanCol) ? null : schema.asSpanAcc(lastSpanCol);

      // Use the accessor to create an object that will write the
      // contents of the indicated view into the file.
      HtmlViz viz = new HtmlViz(outFile, spanGetter, getDocLabel, getDocText);

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
  public void closeHTMLOutput() throws IOException {

    if (null != outputToViz)
      for (HtmlViz viz : outputToViz.values()) {
        viz.close();
      }
  }

  /**
   * Write SystemT annotations to HTML format. Heavy-lifting is done by the {@link HTMLViz} class.
   * 
   * @param docTup contents of the Document view for the current document
   * @param annots Set of SystemT annotations, indexed by view name.
   * @throws IOException
   * @throws Exception
   */
  private void writeToHtml(Tuple docTup, Map<String, TupleList> annots)
      throws IOException, Exception {

    for (String outputTypeName : syst.getOutputTypeNames()) {
      HtmlViz viz = outputToViz.get(outputTypeName);
      viz.addDoc(docTup, annots.get(outputTypeName));
    }
  }

  /**
   * Generate a sample instance of SPSSDocument with some tokens and annotations.
   */
  private SPSSDocument generateTLAMockupInitialDoc(String label) {

    SPSSDocument doc = new SPSSDocument(label);

    // Set a sample content for the document
    doc.setText("A quick brown fox jumps over the lazy dog.");

    // Custom document tokens: "A", "quick brown fox", "jumps", "over", "the", "lazy dog", "."
    int[] beginTokOffsets = new int[] {0, 2, 18, 24, 29, 33, 41};
    int[] endTokOffsets = new int[] {1, 17, 23, 28, 32, 41, 42};
    doc.setTokens(beginTokOffsets, endTokOffsets);

    // Add annotations for view Noun: "quick brown fox" (with lead term
    // "fox") and "lazy dog" (with lead term "dog")
    ArrayList<SPSSAnnotation> nounAnnots = new ArrayList<SPSSAnnotation>();
    nounAnnots.add(new SPSSAnnotation(2, 17, "fox"));
    nounAnnots.add(new SPSSAnnotation(33, 41, "dog"));
    doc.addViewAnnotations("Noun", nounAnnots);

    // Add annotations for view Verb: "jumps" (with no lead term)
    ArrayList<SPSSAnnotation> wordAnnots = new ArrayList<SPSSAnnotation>();
    wordAnnots.add(new SPSSAnnotation(18, 23, ""));
    doc.addViewAnnotations("Verb", wordAnnots);

    return doc;
  }

  private String getOutputDir() {
    return this.OUTPUT_DIR;
  }

  @Override
  public void setOutputDir(String str) {
    this.OUTPUT_DIR = str;
  }

  public void setAqlIncludeDir(String str) {
    this.AQL_INCLUDE_DIR = str;
  }

  public void setAqlFileName(String str) {
    this.AQL_FILE_NAME = str;
  }

  public void setDumpTuples(Boolean todump) {
    this.DUMP_TUPLES = todump;
  }

  public void setOutputHtml(Boolean toHtml) {
    this.OUTPUT_HTML = toHtml;
  }

  public String getAqlIncludeDir() {
    return AQL_INCLUDE_DIR;
  }

  public void setDictionaryDir(String str) {
    this.DICTIONARY_DIR = str;
  }

}
