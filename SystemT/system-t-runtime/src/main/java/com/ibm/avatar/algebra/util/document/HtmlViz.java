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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.TreeSet;

import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.ObjectID;
import com.ibm.avatar.algebra.datamodel.ScalarComparator;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.SpanGetter;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.logging.Log;

/**
 * Quick and dirty way of visualizing the output of an operator graph. Produces an HTML file that
 * marks the places that are annotated in the documents.
 */
public class HtmlViz {

  /**
   * Should we put information on the character set at the beginning of the file? Defaults to false
   * so as not to screw up existing test cases.
   */
  public static boolean WRITE_CHARSET_INFO = false;

  private boolean writeCharsetInfo = WRITE_CHARSET_INFO;

  public void setWriteCharsetInfo(boolean writeCharsetInfo) {
    this.writeCharsetInfo = writeCharsetInfo;
  }

  /**
   * Flag to determine whether this class will generate snippets of the original text or the full
   * HTML.
   */
  private boolean noSnippets = false;

  public void setNoSnippets(boolean noSnippets) {
    this.noSnippets = noSnippets;
  }

  /** HTML that marks the beginning of an annotation. */
  public static final String ANNOT_BEGIN_MARKER = "<font color = gray>[</font>";

  public static final String COLOR_BEGIN1 = "<font color = #8833bb><b>";

  public static final String COLOR_BEGIN2 = "<font color = #aa22aa><b>";

  public static final String COLOR_BEGIN3 = "<font color = #bb1111><b>";

  public static final String COLOR_BEGIN4 = "<font color = #ee0000><b>";

  public static final String COLOR_BEGIN5 = "<font color = #ff0000><b>";

  /** HTML that marks the end of an annotation. */
  public static final String ANNOT_END_MARKER = "<font color = gray>]</font>";

  public static final String COLOR_END = "</b></font>";

  /** HTML that marks the beginning of a table of tuples. */
  public static final String TABLE_BEGIN = "<table class=\"aqltups\">";

  /** Maximum interval (in ms) between output flushes */
  public static final long MAX_FLUSH_INTERVAL_MS = 1000;

  /** Type name used for anonymous documents. */
  public static final String ANON_DOC_TYPE_NAME = "Nonpersistent Document";

  /** Encoding used in the output HTML files. */
  public static final String ENCODING = "UTF-8";

  private int anonDocCount = 0;

  /** Accessor for extracting the span to be marked in the text */
  private SpanGetter spanGetter;

  public void setSpanGetter(SpanGetter spanGetter) {
    this.spanGetter = spanGetter;
  }

  // private Path<Tuple, Annotation> path;

  /** Output file, or null if we're writing directly into a buffer. */
  private File outFile = null;

  /** Name given to our output. */
  private String outputName;

  /** System time (in ms) of last flush operation on the output file. */
  private long lastFlushMs = 0;

  /** The actual filehandle */
  Writer out = null;

  /** Set this flag to TRUE to generate a table of output tuples. */
  private boolean generateTupleTable = false;

  public void setGenerateTupleTable(boolean generateTupleTable) {
    this.generateTupleTable = generateTupleTable;
  }

  /** Schema of the tuples we will receive. */
  private AbstractTupleSchema tupSchema = null;

  public void setTupSchema(AbstractTupleSchema tupSchema) {
    this.tupSchema = tupSchema;
  }

  /**
   * Column of the document tuples that we should use a label for the document, or null to use the
   * document's OID as a labe.
   */
  // private String docLabelCol = null;

  // public void setDocLabelCol (String docLabelCol)
  // {
  // this.docLabelCol = docLabelCol;
  // }

  /**
   * Accessor for getting at the label fields (if requested) of incoming documents. Initialized on
   * the first call to {@link #addDoc(TupleList)}, if {@link #docLabelCol} is non-null.
   */
  private FieldGetter<Text> labelGetter = null;

  // Yunyao: added on 10/26/2007 to output unique annotations only for testing
  // purpose
  // Changed to a TreeSet by Fred to ensure consistent output for tests.
  // < begin
  Writer outList;
  TreeSet<String> annotations = new TreeSet<String>();
  // end >

  public static final boolean LIST_UNIQUE_ANNOTATIONS_TO_FILE = false;

  /**
   * Main constructor.
   * 
   * @param outFile where the HTML output should be written
   * @param spanGetter accessor to retrieve the target spans from incoming tuples
   * @param labelGetter accessor to find the document label in document tuples, or NULL to use the
   *        document tuple OID instead
   * @param labelGetter
   */
  public HtmlViz(File outFile, SpanGetter spanGetter, FieldGetter<Text> labelGetter)
      throws IOException {
    this.outFile = outFile;
    outputName = outFile.getName();

    this.spanGetter = spanGetter;
    this.labelGetter = labelGetter;
  }

  /**
   * Constructor to use when only certain functions are needed (e.g., {@link #escapeHTMLSpecials()}
   * or {@link #genTupleTable()}).
   * 
   * @param outFile2
   * 
   * @param spanGetter
   */
  public HtmlViz(SpanGetter spanGetter) {
    this.spanGetter = spanGetter;
  }

  /**
   * Constructor to pass in a known doc text getter. This is a better approach but the change
   * involves too much changed results in tests. Currently the docTextGetter is ignored.
   * 
   * @param outFile
   * @param spanGetter
   * @param labelGetter
   * @param docTextGetter
   * @throws IOException
   */
  public HtmlViz(File outFile, SpanGetter spanGetter, FieldGetter<Text> labelGetter,
      FieldGetter<Text> docTextGetter) throws IOException {
    this(outFile, spanGetter, labelGetter);
  }

  /**
   * Print the document header. Assumes that out has been opened.
   * 
   * @throws IOException
   */
  private void printHeader() throws IOException {
    out.write("<html>\n");

    if (writeCharsetInfo) {
      // Add a META tag with language information, so that our output will
      // render properly in a web browser.
      out.write("<META http-equiv=\"Content-Type\"" + " content=\"text/html; charset=UTF-8\">\n");
    }

    out.write("<head>\n");
    // Leaving this header line commented out for now so as not to
    // break all the regression tests.
    // + String.format("<meta http-equiv=\"Content-Type\""
    // + " content=\"text/html; charset=%s\">\n", ENCODING)

    // Only generate CSS for tables if we're actually generating tables.
    if (generateTupleTable) {
      out.write("<style type=\"text/css\">\n" + "    table.aqltups {\n"
          + "        background-color: lightgray;\n" + "        width: 100%;\n" + "    }\n"
          + "    th {\n" + "        background-color: darkslategray;\n" + "        color: white;\n"
          + "    }\n" + "</style>\n");
    }

    out.write("<title>" + outputName + "</title>\n" + "</head>\n" + "<body>\n");
  }

  /**
   * Shorten a string and escape newlines.
   */
  public static CharSequence dbgShorten(String input) {

    // First escape, then shorten.
    CharSequence escaped = StringUtils.escapeForPrinting(input);
    return StringUtils.shorten(escaped, 100, true);

    // // Escape invisible characters.
    // input = input.replace("\n", "\\n");
    // input = input.replace("\r", "\\r");
    // input = input.replace("\t", "\\t");
    //
    // if (input.length() <= 100) {
    // return input;
    // }
    //
    // String begin = input.substring(0, 50);
    // String end = input.substring(input.length() - 50, input.length());
    //
    // // "snip" is the sound of part of the text being cut out.
    // return begin + "...\n[ snip! ]\n..." + end;
  }

  /** Flag to identify the first write to the output file */
  private boolean firstWrite = true;

  /** Global counter to keep track of the number of open output files. */
  private static int noOfOpenOutputFiles = 0;

  /** Flag to identify Windows or Linux machine; <code>true</code> for Windows machine. */
  private static boolean isWindowsMachine =
      System.getProperty("os.name").toUpperCase().indexOf("WIN") >= 0 ? true : false;

  /**
   * Maximum number of output files opened at any give time. As of now we restrict this to 75% of
   * the underlying machine's ulimit, and remaining 25% is reserved for java to open various
   * dependent .jar and .so files.
   */
  private static int MAX_ALLOWED_OPEN_OUTPUT_FILES = (int) (fetchUlimit() * 0.75);

  /**
   * Main entry point. Takes a TupleList containing all the tuples for a particular document, and
   * adds that document to the output.
   * 
   * @param docTup tuple representing the current document
   * @param tuplist list of tuples to print to HTML
   */
  public void addDoc(Tuple docTup, TupleList tuplist) throws IOException {
    try {
      if (0 == tuplist.size()) {
        // Ignore documents with no annotations.
        return;
      }
      open();

      // Flush the output if necessary.
      long now = System.currentTimeMillis();
      if (now - lastFlushMs > MAX_FLUSH_INTERVAL_MS) {
        out.flush();
        lastFlushMs = now;
      }

      boolean debug = false;

      // Use whatever document is referenced by our first span as the
      // document.
      Tuple firstTup = tuplist.newIterator().next();
      boolean skipMarkup = false;
      String doctext = null;
      Span firstSpan = null;

      if (spanGetter != null) {
        firstSpan = spanGetter.getVal(firstTup);
        if (null != firstSpan)
          doctext = firstSpan.getDocText();
      } else {
        // SPECIAL CASE: No output column is of type span.
        if (debug) {
          System.err.printf("Output schema %s does not contain a field of type span or text.\n",
              tupSchema);
        }
        // END SPECIAL CASE
      }

      if (null == doctext) {
        // SPECIAL CASE: The rightmost span in the first tuple is NULL,
        // OR no output column is of type span (the SPECIAL CASE above).
        // In this case, we don't know where to get the document text from.
        if (debug && spanGetter != null) {
          System.err.printf("Field %d of tuple '%s' is null\n", spanGetter.getColIx(), firstTup);
        }
        // END SPECIAL CASE
        skipMarkup = true;
      }

      // if (null != firstSpan && null != docLabelCol && null == labelGetter) {
      // // Need to initialize the accessor for retrieving document labels.
      // AbstractTupleSchema docSchema = firstSpan.getType ().getDocType ();
      //
      // if (docSchema.containsField (docLabelCol))
      // labelGetter = docSchema.spanAcc (docLabelCol);
      // // SPECIAL CASE - for the case when the first span is over an anonymous text object
      // // and so the doc schema does not contain the "label" field
      // else {
      // if (debug) {
      // System.err.printf ("Document schema %s of span '%s' does not contain field '%s'\n",
      // docSchema, firstSpan,
      // docLabelCol);
      // }
      // docLabelCol = null;
      // }
      //
      // }

      String docLabel;

      if (null == doctext) {
        docLabel = "Unknown document";
      } else if (null == labelGetter) {
        // The user has *not* requested that we use a particular column of
        // the document tuples as a label; use the OID instead.
        ObjectID textTupOid = docTup.getOid();
        if (null == textTupOid) {
          textTupOid = new ObjectID(ANON_DOC_TYPE_NAME, anonDocCount++, true);
        }
        docLabel = textTupOid.getStringValue();
      } else {
        // Tuple textTup = firstSpan.getTextTup ();
        // System.err.printf("Text tuple is: %s\n", textTup);

        // Handle null values gracefully
        Text labelSpan = labelGetter.getVal(docTup);
        if (null == labelSpan) {
          docLabel = "null";
        } else {
          docLabel = labelSpan.getText();
        }
      }

      // doctext = new String(doctext.getBytes("UTF-8"));

      // Get a list of all the ranges to mark in the text, in order.
      ArrayList<Span> spans = new ArrayList<Span>();

      if (spanGetter == null) {
        // SPECIAL CASE: No output column is of type span.
      } else {
        TLIter itr = tuplist.newIterator();

        if (debug) {
          System.err.printf("%s:%s\n", outputName, docLabel);
        }

        while (itr.hasNext()) {
          Span curSpan = null;
          if (spanGetter != null)
            curSpan = spanGetter.getVal(itr.next());

          if (debug && null != curSpan) {
            System.err.printf("    Will mark '%s' at span <%d, %d>\n",
                dbgShorten(curSpan.getText()), curSpan.getBegin(), curSpan.getEnd());
          }

          if (LIST_UNIQUE_ANNOTATIONS_TO_FILE) {
            this.annotations.add(curSpan.getText());
          }

          spans.add(curSpan);
        }
      }

      // Put the annotations in order by begin.
      Collections.sort(spans, ScalarComparator.createComparator(FieldType.SPAN_TYPE));

      if (debug) {
        Log.debug("-----");
      }

      out.write(String.format("\n\n<h1>Document '%s'</h1>\n\n", docLabel));

      if (generateTupleTable) {
        String table = genTupleTable(tuplist);
        out.write(table);
      }

      if (skipMarkup) {
        out.write("\n");
      } else {
        String colored = markAnnotationsInText(doctext, spans, !noSnippets);

        // System.err.printf("Writing HTML:\n%s\n", colored);

        out.write(colored);
      }

      if (debug) {
        Log.debug("=====");
      }
    } finally {
      // If there are more than MAX_ALLOWED_OPEN_OUTPUT_FILES output files opened, close them once
      // the annotations for
      // the current document(tuple list) are added to the output file
      if (noOfOpenOutputFiles > MAX_ALLOWED_OPEN_OUTPUT_FILES) {
        closeInternal();
      }
    }
  }

  private void open() throws UnsupportedEncodingException, FileNotFoundException, IOException {
    // Open the output file- if not opened already
    if (null == out) {
      out = new OutputStreamWriter(new FileOutputStream(this.outFile, true), ENCODING);
      noOfOpenOutputFiles++;
    }

    // Add header to the output file, before writing the annotations(tuple list) from the first
    // document
    if (true == firstWrite && null != out) {
      printHeader();
      firstWrite = false;
    }
  }

  /**
   * @param tuplist a list of output tuples
   * @return a string containing an HTML table of the tuples.
   */
  private String genTupleTable(TupleList tuplist) {

    StringBuilder sb = new StringBuilder();

    // Generate the table header with column names and types.
    sb.append(TABLE_BEGIN);
    sb.append("\n<tr>\n");
    // sb.append("<table border=\"1\">\n<tr>\n");
    for (int col = 0; col < tupSchema.size(); col++) {
      FieldType ft = tupSchema.getFieldTypeByIx(col);
      String name = tupSchema.getFieldNameByIx(col);
      sb.append(String.format("    <th>%s: %s</th>\n", name, ft));
    }
    sb.append("</tr>\n");

    // Generate the rows.
    for (TLIter itr = tuplist.iterator(); itr.hasNext();) {
      Tuple tup = itr.next();

      sb.append("<tr>\n");
      for (int col = 0; col < tupSchema.size(); col++) {
        Object val = tupSchema.getCol(tup, col);

        String fieldStr;
        if (null == val) {
          fieldStr = "null";
        } else {
          fieldStr = val.toString();
        }

        // The string representation of the field may contain HTML;
        // escape any &, < or > symbols in the string.
        String fieldStrEscaped = escapeHTMLSpecials(fieldStr);

        sb.append(String.format("   <td>%s</td>\n", fieldStrEscaped));
      }
      sb.append("</tr>\n");
    }

    // Close out the table
    sb.append("</table>\n<br><br>\n");

    return sb.toString();
  }

  /*
   * Mark the annotations in the text.
   */
  private String markAnnotationsInText(String text, ArrayList<Span> spans, boolean isSnip) {

    boolean debug = false;

    if (debug) {
      System.err.printf("%s: Marking the following spans:\n", outputName);
      for (int i = 0; i < spans.size(); i++) {
        Span s = spans.get(i);
        System.err.printf("  %d: <%d, %d>\n", i, s.getBegin(), s.getEnd());
      }
    }

    StringBuilder sb = new StringBuilder();

    // Walk through the document, keeping track of the spans that cover each
    // character position.
    ArrayList<Span> curSpans = new ArrayList<Span>();

    // Spans are sorted by begin.
    Iterator<Span> jtr = spans.iterator();
    Span nextSpan = null;
    if (jtr.hasNext()) {
      nextSpan = jtr.next();
    }

    // Keep track of which portion of the document we've generated output
    // for.
    int outPos = 0;

    for (int pos = 0; pos < text.length(); pos++) {

      // Check whether one or more active annotations end at this point in
      // the document.
      // Note that we go backwards through the arraylist so that we can
      // remove things in place.
      for (int i = curSpans.size() - 1; i >= 0; i--) {

        Span s = curSpans.get(i);

        if (s.getEnd() == pos) {
          curSpans.remove(s);

          // Output any additional text that falls within s.
          String toAppend = text.substring(outPos, s.getEnd());
          toAppend = escapeHTMLSpecials(toAppend);

          sb.append(toAppend);

          // Put in markers that will later be replaced with some
          // HTML.
          sb.append("END_OF_COLOR");
          sb.append("END_OF_ANNOT");

          if (curSpans.size() > 0) {
            // Still inside an annotation...
            sb.append(String.format("BEGIN_OF_COLOR%d", Math.min(curSpans.size(), 5)));
          }

          outPos = s.getEnd();
        }
      }

      // Check to see whether an annotation becomes active at this point
      // in the document.
      while (null != nextSpan && nextSpan.getBegin() == pos) {

        if (debug) {
          // System.err.printf("%s: At position %d, "
          // + "starting to mark for <%d, %d>: '%s'\n",
          // outputName, pos, nextSpan.getBegin(), nextSpan
          // .getEnd(), dbgShorten(nextSpan.getText()));
          System.err.printf("%s: At position %d, " + "starting to mark for <%d, %d>\n", outputName,
              pos, nextSpan.getBegin(), nextSpan.getEnd());
        }

        if (0 == curSpans.size()) {
          // We're not currently in an annotation, so we should output
          // the "between annotations" text in shortened form.
          String toAppend = text.substring(outPos, nextSpan.getBegin());

          if (isSnip) {
            toAppend = (String) StringUtils.shorten(toAppend);
          }

          // Escape problem characters
          toAppend = escapeHTMLSpecials(toAppend);

          sb.append(toAppend);

        } else {

          // We're currently in an annotation, so we need to add
          // what's been annotated so far, then stop and
          // restart the highlighting.
          String toAppend = text.substring(outPos, nextSpan.getBegin());

          // Escape any ampersands in the text.
          toAppend = escapeHTMLSpecials(toAppend);

          sb.append(toAppend);
          sb.append("END_OF_COLOR");
        }
        outPos = pos;

        // Add a marker that will be replaced with the symbol for
        // "annotation starts here"
        sb.append("BEGIN_OF_ANNOT");
        sb.append(String.format("BEGIN_OF_COLOR%d", Math.min(curSpans.size() + 1, 5)));

        if (nextSpan.getEnd() <= nextSpan.getBegin()) {

          // SPECIAL CASE: Zero-length span; close it out right away.
          sb.append("END_OF_COLOR");
          sb.append("END_OF_ANNOT");

          if (curSpans.size() > 0) {
            // Still inside an annotation...
            sb.append(String.format("BEGIN_OF_COLOR%d", Math.min(curSpans.size(), 5)));
          }
          // END SPECIAL CASE
        } else {
          // Add the span to our active set.
          curSpans.add(nextSpan);
        }

        if (jtr.hasNext()) {
          nextSpan = jtr.next();
        } else {
          nextSpan = null;
        }
      }
    }

    if (curSpans.size() > 0) {
      // SPECIAL CASE: One or more annotations reach the end of the
      // document.
      String toAppend = text.substring(outPos);
      toAppend = escapeHTMLSpecials(toAppend);

      sb.append(toAppend);
      sb.append("END_OF_COLOR");
      for (int i = 0; i < curSpans.size(); i++) {
        sb.append("END_OF_ANNOT");
      }
      outPos = text.length();
      // END SPECIAL CASE
    }

    final boolean debugEscapes = false;

    // Don't forget the end of the document!
    String toAppend = text.substring(outPos);
    if (isSnip) {
      toAppend = (String) StringUtils.shorten(toAppend);
    }
    toAppend = escapeHTMLSpecials(toAppend);

    sb.append(toAppend);

    // Escape all the funky HTML stuff in the original document.
    String markedUp = sb.toString();

    if (debugEscapes) {
      Log.debug("Marking up: '%s'", dbgShorten(markedUp));
    }

    // String tagEscaped = escapeHTMLSpecials(markedUp);
    String tagEscaped = markedUp.replace("<", "&lt;").replace(">", "&gt;");

    // Strip out any carriage return characters.
    tagEscaped = tagEscaped.replace("\r", "");

    // Turn newlines into HTML linebreaks.
    String brEscaped = tagEscaped.replace("\n", "<br>\n");

    if (debugEscapes) {
      Log.debug("--> After br escapes: '%s'", dbgShorten(brEscaped));
    }

    // Now that we've escaped all the HTML, we can add the FONT tags.
    String colored = brEscaped.replace("BEGIN_OF_ANNOT", ANNOT_BEGIN_MARKER)
        .replace("END_OF_ANNOT", ANNOT_END_MARKER).replace("BEGIN_OF_COLOR1", COLOR_BEGIN1)
        .replace("BEGIN_OF_COLOR2", COLOR_BEGIN2).replace("BEGIN_OF_COLOR3", COLOR_BEGIN3)
        .replace("BEGIN_OF_COLOR4", COLOR_BEGIN4).replace("BEGIN_OF_COLOR5", COLOR_BEGIN5)
        .replace("END_OF_COLOR", COLOR_END);

    // System.err.printf("HTML output is:\n%s\n", colored);

    return colored;
  }

  /**
   * Actually close the output file and update the global counter.
   */
  private void closeInternal() throws IOException {
    if (null != out) {
      out.close();
      out = null;
      noOfOpenOutputFiles--;
    }
  }

  /** Flush buffers and close the output file */
  public void close() throws IOException {
    try {
      open();
      // Finally add the footer after adding annotations from all the documents.
      out.write("\n</body>\n</html>\n");
      out.flush();

      // only output the list when information of the output directory is
      // given
      if (this.outFile != null && LIST_UNIQUE_ANNOTATIONS_TO_FILE)
        outputList();
    } finally {
      closeInternal();
    }
  }

  public void outputList() throws IOException {
    File outListFile = new File(this.outFile.getParent() + File.separator
        + outputName.substring(0, outputName.lastIndexOf(".")) + ".txt");

    outList = new OutputStreamWriter(new FileOutputStream(outListFile));

    Iterator<String> iter = this.annotations.iterator();

    while (iter != null && iter.hasNext()) {
      outList.write(iter.next().toString().replaceAll("[\n\t\r]", " ") + "\n");
    }

    outList.close();
  }

  public String getFileName() {
    return outFile.getPath();
  }

  public File getOutFile() {
    return outFile;
  }

  public String escapeHTMLSpecials(String orig) {
    final boolean debug = false;

    if (0 == orig.length()) {
      return orig;
    }

    String ret;

    // Need to do the ampersands first
    ret = orig.replace("&", "&amp;");
    ret = ret.replace("<", "&lt;");
    ret = ret.replace(">", "&gt;");

    if (debug) {
      Log.debug("escapeHTMLSpecials(): Turned:\n" + //
          "'%s'\n" + //
          "    into:\n" + //
          "'%s'", //
          StringUtils.escapeForPrinting(orig), StringUtils.escapeForPrinting(ret));
    }

    return ret;
  }

  /**
   * This method returns the ulimit of the test machine. If there is an exception while fetching the
   * ulimit, instead of throwing the exception and halting the whole test suite, this method returns
   * a default ulimit, 1024(most of our test machines have ulimit set to 1024). <br>
   * There is no notion of ulimit on Windows, but still to restrict the number of output files
   * opened by the test case, this method returns 1024 for Windows.
   * 
   * @return the ulimit of the underlying test machine.
   */
  private static int fetchUlimit() {
    final int defaultUlimit = 1024;

    if (true == isWindowsMachine)
      return defaultUlimit;

    try {
      Runtime runTime = Runtime.getRuntime();
      Process p = runTime.exec(new String[] {"bash", "-c", "ulimit -n"});
      InputStream in = p.getInputStream();
      InputStreamReader isr = new InputStreamReader(in);
      BufferedReader br = new BufferedReader(isr);
      p.waitFor();

      return Integer.parseInt(br.readLine());
    } catch (Throwable e) {
      // If there is an exception while fetching Ulimit, instead of throwing the exception and
      // halting the whole test
      // suite, lets return a default ulimit, 1024( most of our test machines have ulimit set to
      // 1024).
      return defaultUlimit;
    }
  }
}
