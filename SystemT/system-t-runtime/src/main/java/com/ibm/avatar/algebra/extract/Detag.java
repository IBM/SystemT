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
package com.ibm.avatar.algebra.extract;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.util.ArrayList;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.base.MultiOutputOperator;
import com.ibm.avatar.algebra.base.Operator;
import com.ibm.avatar.algebra.base.OutputBuffer;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldSetter;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.SpanGetter;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.TextSetter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.util.html.HTMLDetagger;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.algebra.util.tokenize.OffsetsList;
import com.ibm.avatar.logging.Log;
import com.ibm.avatar.logging.MsgType;

/**
 * Our internal implementation of an HTML Detagger. In addition to output a detagged document, this
 * detagger also generates annotations for HTML element given detagging specifications, if any.
 * 
 */
public class Detag extends MultiOutputOperator {

  /** Should we provide a warning and dump output when we can't parse a page? */
  public static final boolean DUMP_ON_PARSE_FAILURE = true;

  /** Performance counter name used for the "detagging time" counter. */
  public static final String MATCH_PERF_COUNTER_NAME = "HTML Detagging";

  /**
   * Performance counter name used for the "HTML Detagging result creation" counter.
   */
  // public static final String RESULT_PERF_COUNTER_NAME =
  // "HTML Detagging Result Creation";

  // private String[] labels = null;

  /** Currently, we use the same name for all outputs. */
  public static final String OUTPUT_COL_NAME = "match";

  public static final String DOC_COL_NAME = "text";

  /**
   * Should we use the HTMLParser implementation of detagging instead of the CyberNeko one?
   */
  public static boolean USE_HTMLPARSER = true;

  /** Index of the field from which our annotation sources come. */
  private final String col;

  /** Accessor for getting at the input field of the interior tuples. */
  private SpanGetter inputAcc;

  /**
   * Accessors for setting the "tagged text" span in the tuples that mark tag locations; one
   * accessor for each tag type.
   */
  private final ArrayList<FieldSetter<Span>> tagOutputAcc = new ArrayList<FieldSetter<Span>>();

  /**
   * Accessors for setting the "attribute value" fields of the tuples that mark tag locations. First
   * index is tag type; second is the index of the attribute. See {@link #attrs} and
   * {@link #attrLabels}.
   */
  private final ArrayList<ArrayList<TextSetter>> attrOutputAcc

      = new ArrayList<ArrayList<TextSetter>>();

  private TextSetter outputTextAcc;

  /**
   * Class that we use to wrap the thread-specific HTMLSAXParser object that is stored inside the
   * MemoizationTable.
   */
  private class parserWrapper extends OutputBuffer {
    /**
     * Parser/detagger instance for the current thread.
     */
    // HTMLSAXParser parser;
    HTMLDetagger detagger;

    @Override
    public void close() {
      detagger = null;
    }
  }

  /**
   * @param mt the table that holds thread-specific data, including the HTML parser instances
   * @return an HTML parser instance that is private to the current thread
   */
  private HTMLDetagger detagger(MemoizationTable mt) {
    try {

      // The table is wrapped inside a parserWrapper object.
      OutputBuffer buf = mt.getOutputBuf(this);

      if (null == buf) {
        // Parser hasn't yet been created; create it.
        parserWrapper wrapper = new parserWrapper();
        if (USE_HTMLPARSER) {
          // Use HTMLParser-based implementation
          // We don't reference the HTMLParserDetagger class directly,
          // because we don't want a library dependency in code that
          // does not use the detag statement.
          try {
            // Do NOT put template arguments on this Class variable; the idea of this reflection
            // code is to AVOID having
            // class loader dependencies on the real implementation class
            Class<?> htmlParserDetClass =
                Class.forName("com.ibm.avatar.algebra.util.html.HTMLParserDetagger");
            Constructor<?> c = htmlParserDetClass.getConstructors()[0];
            wrapper.detagger = (HTMLDetagger) c.newInstance(tags, attrs);
          } catch (Exception e) {
            throw new RuntimeException("Error instantiating HTML detagger", e);
          }
          // Old code:
          // wrapper.detagger = new HTMLParserDetagger(tags, attrs);
        } else {
          // Use CyberNeko-based implementation
          // No longer maintained, since HTMLParser works much better.
          // wrapper.detagger = new HTMLSAXParser(tags, attrs);
        }
        mt.cacheOutputBuf(this, wrapper);

        return wrapper.detagger;
      } else {
        // Parser has been created and is hidden inside the
        // parserWrapper
        // object we were just returned.
        parserWrapper wrapper = (parserWrapper) buf;
        return wrapper.detagger;
      }

    } catch (java.lang.NoClassDefFoundError e) {
      // We get this error when there is no HTML parser available, which
      // should only happen when we've explicitly stripped out detagging
      // functionality. Throw a more appropriate error for this case.
      System.err.printf("Caught exception:\n");
      e.printStackTrace();
      throw new RuntimeException(
          "Sorry, detagging support not installed; " + "please install the appropriate plugin.");
    }
  }

  /**
   * Tags that we capture; kept for debugging purposes.
   */
  private final String[] tags;

  /**
   * Names of the AQL types that are assigned to the tags that we capture.
   */
  private final String[] tagTypes;

  /**
   * For each type of tag that we capture, information about any attributes that we've been
   * requested to pass through to the output.
   */
  private final String[][] attrs;

  /**
   * Labels for the columns of output tuples containing values requested via {@link #atts}.
   */
  private final String[][] attrLabels;

  private final String detaggedDocType;

  /**
   * true if the detagger should check whether a document is HTML before attempting to detag
   */
  private final boolean checkForHTML;

  /**
   * Main constructor
   * 
   * @param tags HTML tags about which to pass through information
   * @param tagTypes names of the output types corresponding to the tags
   * @param attrs for each entry of tags, a list of tag attributes to output along with the tag
   *        offset information
   * @param detaggedDocType type name for the detagged document
   * @attrLabels for each entry in attrs, name of the corresponding output column in this operator's
   *             output tuples
   * @param col name of input column containing raw HTML
   * @param checkForHTML true if the detagger should check whether a document is HTML before
   *        attempting to detag
   * @param child root of input operator tree
   */
  public Detag(String[] tags, String[] tagTypes, String[][] attrs, String[][] attrLabels,
      String detaggedDocType, String col, boolean checkForHTML, Operator child) {
    super(child, tags.length + 1);

    this.tags = tags;
    this.tagTypes = tagTypes;
    this.attrs = attrs;
    this.detaggedDocType = detaggedDocType;
    this.attrLabels = attrLabels;
    this.col = col;
    this.checkForHTML = checkForHTML;

    // Create space for our output accessors.
    for (int i = 0; i < attrLabels.length; i++) {
      attrOutputAcc.add(null);
      tagOutputAcc.add(null);
    }
  }

  /**
   * Handle cleanup and forensics when the HTML parser throws an exception.
   * 
   * @param t the exception (usually a RuntimeException of some sort)
   * @param inTup tuple that caused the exception
   */
  private void handleParseException(Throwable t, Tuple inTup) {

    // Print a warning
    Log.log(MsgType.AQLRuntimeWarning,
        "While parsing HTML for tuple with OID %s," + " caught exception: %s", inTup.getOid(), t);

    // System.err.printf("Stack trace:");
    // t.printStackTrace();

    if (DUMP_ON_PARSE_FAILURE) {
      // Generate a dump of the problem document.
      try {
        File tmpFile = File.createTempFile("detag", ".txt");

        // Use the default encoding.
        PrintWriter dump = new PrintWriter(new FileWriter(tmpFile));

        dump.printf(
            "This file contains a record of a tuple " + "that crashed the HTML detagger.\n\n");
        dump.printf("The input tuple was: %s\n", inTup);
        dump.printf("The OID of the input tuple was: %s\n", inTup.getOid());

        dump.printf("Parsing generated the following exception:\n");
        t.printStackTrace(dump);

        dump.printf("--------\n");

        String text = inputAcc.getVal(inTup).getText();
        dump.printf("Document text was as follows:\n\n%s\n", text);

        dump.close();

        Log.log(MsgType.AQLRuntimeWarning, "Dumped document information to file %s", tmpFile);

      } catch (IOException e) {
        // Uh-oh
        throw new RuntimeException("IOException in error handling code");
      }
    }

    // Turn serious problems into RuntimeExceptions
    if (t instanceof NoClassDefFoundError) {
      throw new RuntimeException(t);
    }
  }

  @Override
  protected AbstractTupleSchema createOutputSchema(int ix) {

    final boolean debug = false;

    AbstractTupleSchema inputSchema = child.getOutputSchema();

    AbstractTupleSchema outputSchema;

    if (0 == ix) {
      // Output zero is the detagged document.

      // Pass through span source of input.
      outputSchema = new TupleSchema(DOC_COL_NAME, FieldType.TEXT_TYPE);
      outputSchema.setName(detaggedDocType);

      // Mark the detagged document type as being derived from the
      // original HTML.
      FieldType origHTMLFT = inputSchema.getFieldTypeByName(col);
      FieldType detaggedTextFT = outputSchema.getFieldTypeByName(DOC_COL_NAME);
      detaggedTextFT.setSourceDocType(origHTMLFT);

      // Set up accessors for getting at the elements of the schemas.
      inputAcc = inputSchema.asSpanAcc(col);
      outputTextAcc = outputSchema.textSetter(DOC_COL_NAME);
    } else {

      // Outputs 1 and higher are for tags extracted from the detagged
      // document.

      // Generate an index into the attrLabels array for this output.
      int attrLabelsIx = ix - 1;

      int ncol = 1 + attrLabels[attrLabelsIx].length;
      String[] colNames = new String[ncol];
      FieldType[] colTypes = new FieldType[ncol];

      // First create some columns to hold attributes of the tag that are passed through
      for (int attrIx = 0; attrIx < attrLabels[attrLabelsIx].length; attrIx++) {
        colNames[attrIx] = attrLabels[attrLabelsIx][attrIx];
        colTypes[attrIx] = FieldType.TEXT_TYPE;
      }

      // Add a column for the detagged text inside the tag.
      colNames[ncol - 1] = OUTPUT_COL_NAME;
      colTypes[ncol - 1] = FieldType.SPAN_TYPE;

      outputSchema = new TupleSchema(colNames, colTypes);
      outputSchema.setName(tagTypes[attrLabelsIx]);

      // Set up the accessor for writing the tag contents into output
      // tuples
      tagOutputAcc.set(attrLabelsIx, outputSchema.spanSetter(OUTPUT_COL_NAME));

      // Set up accessor for writing the attribute values into output
      // tuples
      ArrayList<TextSetter> accessors = new ArrayList<TextSetter>();
      for (int attrIx = 0; attrIx < attrLabels[attrLabelsIx].length; attrIx++) {
        accessors.add(outputSchema.textSetter(attrLabels[attrLabelsIx][attrIx]));
      }
      attrOutputAcc.set(attrLabelsIx, accessors);
    }

    if (debug) {
      Log.debug("Created schema for %s (output %d):", this, ix);
      Log.debug("    Input schema was: %s", inputSchema);
      Log.debug("    Output schema is: %s", outputSchema);
      Log.debug("    View.column name: %s", outputTextAcc.getViewAndColumnName());
    }

    return outputSchema;
  }

  /**
   * Determines if the input string appears to be an HTML or XML document. Only looks at the first
   * 100 characters.
   * 
   * @param str string containing document text that has been fed to the Detag operator
   * @return true if the string doesn't appear to contain any HTML tags and therefore shouldn't be
   *         run through the HTML parser
   */
  public static boolean isNonHTML(String str) {

    boolean debug = false;

    // A collection of useful markers for finding HTML documents.
    final String HTML_START_MARKER = "<html";
    final String XML_START_MARKER_1 = "xml";
    final String XML_START_MARKER_2 = "XML";
    final String DOCTYPE_START_MARKER = "<!doctype";
    final String CDATA_START_MARKER = "<![cdata[";

    // Pull out the first 100 characters.
    String first100 = str.substring(0, Math.min(100, str.length()));

    if (debug) {
      Log.debug("isNonHtml(): first100 is '%s'", StringUtils.escapeForPrinting(first100));
    }

    // Convert to lowercase, so we can do cheap case-insensitive
    // comparisons.
    first100 = first100.toLowerCase();

    // Start by looking for obvious markers of HTML
    if (first100.contains(HTML_START_MARKER)) {
      return false;
    }

    if (first100.contains(XML_START_MARKER_1) || first100.contains(XML_START_MARKER_2)) {
      if (debug) {
        Log.debug("first100 contains '%s' or '%s'; " + "returning false", XML_START_MARKER_1,
            XML_START_MARKER_2);
      }
      return false;
    } else {
      if (debug) {
        Log.debug("first100 does not contain '%s' or '%s'", XML_START_MARKER_1, XML_START_MARKER_2);
      }
    }

    if (first100.contains(DOCTYPE_START_MARKER)) {
      return false;
    }

    if (first100.contains(CDATA_START_MARKER)) {
      return false;
    }

    // Look for at least two '<' symbols and two '>' symbols in the
    // first 100 bytes.
    int numLT = 0;
    int numGT = 0;

    for (int i = 0; i < first100.length(); i++) {
      char c = first100.charAt(i);
      if ('<' == c) {
        numLT++;
      } else if ('>' == c) {
        numGT++;
      }

      if (numLT >= 2 && numGT >= 2) {
        return false;
      }
    }

    if (debug) {
      Log.debug("Document is not HTML");
    }

    // If we get here, we've failed all the tests so far; assume non-HTML
    return true;

  }

  @Override
  protected void advanceAllInternal(MemoizationTable mt, TupleList childTups,
      TupleList[] outputLists) throws Exception {

    final boolean debug = false;

    // Get a handle on the parser instance for the current thread
    HTMLDetagger detagger = detagger(mt);

    TLIter itr = childTups.iterator();

    while (itr.hasNext()) {
      // Grab the next interior input tuple from the top-level tuple list
      Tuple interiorInTup = itr.next();

      Span src = inputAcc.getVal(interiorInTup);
      if (src == null)
        continue;

      String text = src.getText();

      // Declare the variables that will hold the data that we will
      // marshal below into the results.
      String detaggedTextStr;

      // This variable will hold the mapping from offsets in the detagged
      // text to offsets into the original HTML.
      int[] offsetsTable;

      detagger.clear();

      boolean nonHTML = checkForHTML && isNonHTML(text);

      if (nonHTML) {
        // SPECIAL CASE: Non-HTML input detected; avoid feeding it
        // through the detagger.
        // Don't forget to generate a dummy remapping table, though!
        detaggedTextStr = text;
        offsetsTable = new int[text.length()];
        for (int i = 0; i < offsetsTable.length; i++) {
          offsetsTable[i] = i;
        }
        // END SPECIAL CASE
      } else {
        // Normal case: HTML input; detag it!

        // The HTML parser sometimes chokes on certain documents. We run
        // the parser inside a try block so as to catch the resulting
        // exceptions.
        try {
          detagger.detagStr(text);
        } catch (Throwable t) {
          handleParseException(t, interiorInTup);

          detagger.clear();
        }
        detaggedTextStr = detagger.getDetaggedText();

        // Get table for translating offsets in detagged text back to
        // the
        // original document
        offsetsTable = detagger.getOffsetsTable();
      }

      if (debug) {
        Log.debug("Detag: Input HTML is: %s", StringUtils.shortenForPrinting(text, 50));
        Log.debug("Detag: Detagged text is: %s",
            StringUtils.shortenForPrinting(detaggedTextStr, 50));

      }

      // Build up an interior tuple to send to the appropriate detagged
      // document output of the operator
      Tuple outTup = createOutputTup(0);

      // GHE #18
      // use Text instance whose 'derived' property is false if the text isn't HTML text.
      Text detaggedText = (nonHTML) ? outputTextAcc.setVal(outTup, text, src.getLanguage())
          : outputTextAcc.setVal(outTup, detaggedTextStr, src.getDocTextObj(), offsetsTable);
      outputLists[0].add(outTup);

      if (debug) {
        Log.debug("Detag: Detagged text is: %s", detaggedText);
        Log.debug("View and column name is: %s", detaggedText.getViewAndColumnName());
      }

      // Span doc = Span.makeSpan(getInteriorOutputSchema(0)
      // .getFieldTypeByName(DOC_COL_NAME).toPureSpanType(),
      // interiorOutTup, 0, detaggedTextStr.length());

      // Build up an interior tuple to send to the appropriate tag
      // position output of the operator.
      for (int outputIx = 1; outputIx < this.outputs.length; outputIx++) {

        // Compute the offset into the tags/labels arrays.
        int tagsOffset = outputIx - 1;

        OffsetsList offsets = detagger.getTagOffsets(tagsOffset);
        ArrayList<String[]> tagAttrs = detagger.getTagAttrs(tagsOffset);

        for (int i = 0; i < offsets.size(); i++) {
          Span annotation = Span.makeBaseSpan(detaggedText, offsets.begin(i), offsets.end(i));
          outTup = createOutputTup(outputIx);
          tagOutputAcc.get(tagsOffset).setVal(outTup, annotation);

          // Output the desired attributes of the tag
          String[] attrVals = tagAttrs.get(i);
          for (int attrIx = 0; attrIx < attrs[tagsOffset].length; attrIx++) {

            // System.err.printf("Value of attribute %s is %s\n",
            // attrs[outputIx][attrIx], attrVals[attrIx]);

            TextSetter setter = attrOutputAcc.get(tagsOffset).get(attrIx);

            setter.setVal(outTup, Text.convert(attrVals[attrIx]));
          }

          outputLists[outputIx].add(outTup);
        }
      }

    }
  }

}
