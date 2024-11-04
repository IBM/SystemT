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
package com.ibm.avatar.algebra.util.document;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.SpanGetter;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;

/**
 * Utility class to write the output of an extractor on a collection of documents to HTML format.
 * Heavy lifting is done by {@link HtmlViz}. The class takes as input a map of output view names and
 * their schemas and produces a HTML file that marks the places that are annotated in the documents.
 * 
 */
public class ToHTMLOutput {

  /**
   * Set of HtmlViz objects, indexed by the name of the output view.
   */
  HashMap<String, HtmlViz> outputNameToViz = new HashMap<String, HtmlViz>();

  /**
   * The output directory where output HTML files go.
   */
  private File outputDir;

  /**
   * Main constructor.
   * 
   * @param outputViews map of output view name to schema
   * @param outputDir location of directory where the output HTML files are written to
   * @param generateTables <code>true</code> to generate the tables, <code>false</code> otherwise
   * @param writeCharsetInfo <code>true</code> to write character set information,
   *        <code>false</code> otherwise
   * @param generateFullHTML <code>true</code> to generate the full text, <code>false</code> to
   *        generate snippets of the original text
   * @paran docSchema schema of the document tuples that will be passed to the
   *        {@link #write(Tuple, Map)} method of this object
   * @throws IOException
   * @throws TextAnalyticsException
   */
  public ToHTMLOutput(Map<String, TupleSchema> outputViews, File outputDir, boolean generateTables,
      boolean writeCharsetInfo, boolean generateFullHTML, AbstractTupleSchema docSchema)
      throws IOException, TextAnalyticsException {

    // Sanity checks to make sure the output directory is in place.
    if (!outputDir.exists())
      throw new TextAnalyticsException("The output directory '%s' does not exist.",
          outputDir.getCanonicalPath());

    if (!outputDir.isDirectory())
      throw new TextAnalyticsException("The output directory '%s' does not point to a directory.",
          outputDir.getCanonicalPath());

    this.outputDir = outputDir;

    // Now set up one HtmlViz object for each output view
    Iterator<Entry<String, TupleSchema>> itr = outputViews.entrySet().iterator();

    while (itr.hasNext()) {

      Entry<String, TupleSchema> outputView = itr.next();

      String viewName = outputView.getKey();
      TupleSchema viewSchema = outputView.getValue();

      // Create the output file to write the HTML to.
      File outFile = new File(outputDir, String.format("%s.htm", viewName));

      // Fetch information about the schema of this output, then create an accessor for getting at
      // the rightmost span in
      // the output schema.
      String lastSpanCol = viewSchema.getLastSpanCol();
      SpanGetter spanGetter = (null == lastSpanCol) ? null : viewSchema.asSpanAcc(lastSpanCol);

      // Emulate behavior of ToHTML to make test cases conform
      if (null == spanGetter) {
        // If no Span col in schema, then grab the last Text col instead.
        String lastTextCol = (null == lastSpanCol) ? viewSchema.getLastTextCol() : null;
        spanGetter = (null == lastTextCol) ? null : viewSchema.asSpanAcc(lastTextCol);
      }

      // Create an accessor for retrieving the "label" column from document tuples, if said column
      // exists.
      FieldGetter<Text> docTextAcc = null;
      if (docSchema.containsField(Constants.DOCTEXT_COL)) {
        docTextAcc = docSchema.textAcc(Constants.DOCTEXT_COL);
      }
      FieldGetter<Text> docLabelAcc = null;
      if (docSchema.containsField(Constants.LABEL_COL_NAME)) {
        docLabelAcc = docSchema.textAcc(Constants.LABEL_COL_NAME);
      }

      // Use the accessor to create an object that will write the
      // contents of the indicated view into the file.
      HtmlViz viz = new HtmlViz(outFile, spanGetter, docLabelAcc, docTextAcc);

      viz.setTupSchema(viewSchema);
      viz.setGenerateTupleTable(true);
      // viz.setDocLabelCol (Constants.LABEL_COL_NAME);

      outputNameToViz.put(viewName, viz);
    }

    setWriteCharsetInfo(writeCharsetInfo);
    setGenerateTupleTable(generateTables);
    setGenerateFullHTML(generateFullHTML);
  }

  /**
   * Convenience constructor for when you want to use default values for generateTables (default
   * value is false), writeCharsetInfo (default value is true) and generateFullHTML (default value
   * is false) flags. By default, tuple tables are not generated, character set info is written, and
   * we generate snippets of the original text instead of the full text.
   * 
   * @param outputViews map of output view name to schema
   * @param outputDir location of directory where the output HTML files are written to
   * @paran docSchema schema of the document tuples that will be passed to the
   *        {@link #write(Tuple, Map)} method of this object
   * @throws IOException
   * @throws TextAnalyticsException
   */
  public ToHTMLOutput(Map<String, TupleSchema> outputViews, File outputDir,
      AbstractTupleSchema docSchema) throws IOException, TextAnalyticsException {
    this(outputViews, outputDir, false, true, false, docSchema);
  }

  /**
   * Close the HTML Visualizer objects.
   */
  public void close() throws IOException {

    if (null != outputNameToViz) {
      for (HtmlViz viz : outputNameToViz.values()) {
        viz.close();
      }
    }
  }

  /**
   * Write annotations to HTML format.
   * 
   * @param docTup contents of the Document view for the current document
   * @param annots Set of output tuples, indexed by view name.
   * @throws IOException
   * @throws Exception if the view name is unknown, that is, is was not provided as input to the
   *         constructor
   */
  public void write(Tuple docTup, Map<String, TupleList> annots) throws IOException, Exception {

    for (String outputTypeName : annots.keySet()) {
      HtmlViz viz = outputNameToViz.get(outputTypeName);

      if (null == viz)
        throw new Exception(String
            .format("Attempting to write results for unknown output view '%s'.", outputTypeName));
      viz.addDoc(docTup, annots.get(outputTypeName));
    }
  }

  /**
   * Set a flag to determine whether to generate a table of output tuples in each output HTML file.
   * 
   * @param generateTupleTable <code>true</code> to generate the tables, <code>false</code>
   *        otherwise
   */
  public void setGenerateTupleTable(boolean generateTupleTable) {
    for (HtmlViz viz : outputNameToViz.values()) {
      viz.setGenerateTupleTable(generateTupleTable);
    }
  }

  /**
   * Set a flag to determine whether to generate snippets of the original text or the full text in
   * each output HTML file.
   * 
   * @param generateFullHTML <code>true</code> to generate the full text, <code>false</code> to
   *        generate snippets of the original text
   */
  public void setGenerateFullHTML(boolean generateFullHTML) {
    for (HtmlViz viz : outputNameToViz.values()) {
      viz.setNoSnippets(generateFullHTML);
    }
  }

  /**
   * Set a flag to determine whether to write information on the character set at the beginning of
   * each output HTML file.
   * 
   * @param writeCharsetInfo <code>true</code> to write character set information,
   *        <code>false</code> otherwise
   */
  public void setWriteCharsetInfo(boolean writeCharsetInfo) {
    for (HtmlViz viz : outputNameToViz.values()) {
      viz.setWriteCharsetInfo(writeCharsetInfo);
    }
  }

  /**
   * @return the outputDir
   */
  public File getOutputDir() {
    return outputDir;
  }

}
