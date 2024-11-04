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
package com.ibm.avatar.algebra.util.html;

import java.util.ArrayList;

import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.algebra.util.tokenize.BaseOffsetsList;
import com.ibm.avatar.algebra.util.tokenize.OffsetsList;

/**
 * Abstract base class for the classes that do the work of detagging HTML documents, using various
 * HTML parser implementations. This class holds common code for tracking the extracted tags, as
 * well as the mapping from offsets in the detagged text back to the original text.
 * 
 */
public abstract class HTMLDetagger {

  /*
   * CONSTANTS
   */

  /**
   * By default we will ignore everything inside script
   */
  protected static final String SCRIPT_TAG = "SCRIPT";

  /**
   * By default we will ignore everything inside style description
   */
  protected static final String STYLE_TAG = "STYLE";

  /** The maximum number of entries in the persistent offsets table array. */
  private static final int MAX_OFFSETS_BUF_SIZE = 32768;

  /**
   * Starting size for the buffer; it will grow when we encounter a document larger than this size.
   */
  private static final int OFFSETS_BUF_SIZE = 16384;

  /*
   * INNER CLASSES
   */

  /**
   * This class is used to store a stack of tags of the same type in the order they are encountered
   * while parsing the HTML document. {@link java.util.Stack} is synchronized, so we create our own
   * implementation. While we're at it, we specialize the class for the types we'll be storing in
   * the stack, so as to avoid creating lots of temporary objects.
   */
  static final class tagStack {

    /** Store begin offsets of tags in the detagged text. **/
    private int[] offsets = new int[1];

    /** Store begin offsets of tags in the original html text. **/
    private int[] htmlOffsets = new int[1];

    /** Store the attributes of each tag. **/
    private String[][] attrs = new String[1][];

    /** The depth of the stack. **/
    private int depth = 0;

    public void push(int detagOffset, int htmlOffset, String[] a) {
      if (depth >= offsets.length) {
        // Resize the internal arrays.
        int[] otmp = new int[depth * 2];
        int[] hotmp = new int[depth * 2];
        String[][] atmp = new String[depth * 2][];
        System.arraycopy(offsets, 0, otmp, 0, depth);
        System.arraycopy(htmlOffsets, 0, hotmp, 0, depth);
        System.arraycopy(attrs, 0, atmp, 0, depth);
        offsets = otmp;
        htmlOffsets = hotmp;
        attrs = atmp;
      }
      offsets[depth] = detagOffset;
      htmlOffsets[depth] = htmlOffset;
      attrs[depth] = a;
      depth++;
    }

    public void pop() {
      if (0 == depth) {
        throw new RuntimeException("Ran out of stack");
      }
      depth--;
    }

    public int peekOffset() {
      return offsets[depth - 1];
    }

    public int peekHtmlOffset() {
      return htmlOffsets[depth - 1];
    }

    public String[] peekAttrs() {
      return attrs[depth - 1];
    }

    public void clear() {
      depth = 0;
    }

    public boolean empty() {
      return (0 == depth);
    }

    /**
     * Replace all instances of a given offset with a corrected value. Used to correct for
     * artificial whitespace added during detagging.
     */
    public void correctOffset(int oldVal, int newVal) {
      for (int i = 0; i < depth; i++) {
        if (offsets[i] == oldVal) {
          offsets[i] = newVal;
        }
      }
    }
  }

  /*
   * READ-ONLY FIELDS Set in constructor or at the beginning of parsing a document.
   */

  /**
   * Names of tags whose offsets we will remember when detagging. Read-only.
   */
  protected String[] tags;

  /**
   * For each tag captured, a (possibly empty) list of attributes to capture for the indicated tag.
   * Read-only.
   */
  protected String[][] attrs;

  /**
   * The HTML string being parsed.
   */
  protected String html = null;

  /*
   * INTERNAL READ/WRITE FIELDS Used only by this class; not shared with subclasses.
   */

  /**
   * Cached copy of the detagged document text, generated from the internal StringBuffer on demand.
   */
  private String detaggedText;

  /**
   * This flag is set to TRUE if the class is set to generate output annotations aside from the
   * detagged document.
   */
  private boolean annotTags = false;

  protected boolean getAnnotateTags() {
    return annotTags;
  }

  /**
   * Offsets of tags in the original document, translated into offsets into the detagged document.
   * Each entry in this
   */
  private BaseOffsetsList[] tagOffsets;

  /**
   * For each entry in {@link #tags}, we keep a stack of information about instances of the tag that
   * we've seen without encountering the corresponding closing tags. We could use a single master
   * stack, but HTML often contains improperly nested tags. Tracking each tag separately helps to
   * improve recall with improperly-formatted HTML.
   */
  protected tagStack[] tagBegins;

  /**
   * Attribute values that correspond to the entries in {@link #tagOffsets}
   */
  private ArrayList<ArrayList<String[]>> tagAttrs;

  /** Number of HTML documents parsed (for debugging) */
  int docCount = 0;

  /*
   * PROTECTED READ/WRITE FIELDS Accessed directly by child classes during detagging/parsing
   */

  /**
   * Object used for building up the detagged document string.
   */
  protected StringBuilder sb = new StringBuilder();

  /**
   * Mapping from offsets into the detagged document to offsets in the original doc. Index is offset
   * into detagged document; value is offset into original HTML.
   */
  protected int[] offsetsBuf = new int[OFFSETS_BUF_SIZE];

  /*
   * PUBLIC API
   */

  /**
   * Main constructor.
   * 
   * @param tags HTML tags the contents of which will be output as annotations
   * @param attrs for each tag captured, a (possibly empty) list of attributes to capture for the
   *        indicated tag
   */
  public HTMLDetagger(String[] tags, String[][] attrs) {

    // if there is any specification for annotations
    // initialize internal data structures
    if (tags.length > 0) {
      this.annotTags = true;
      this.tags = tags;
      this.attrs = attrs;

      this.tagBegins = new tagStack[tags.length];
      this.tagOffsets = new BaseOffsetsList[tags.length];
      this.tagAttrs = new ArrayList<ArrayList<String[]>>();

      for (int i = 0; i < tags.length; i++) {
        this.tagBegins[i] = new tagStack();
        this.tagOffsets[i] = new BaseOffsetsList();
        this.tagAttrs.add(new ArrayList<String[]>());
      }
    }
  }

  /**
   * Clear the information stored in the parser, to prepare the parser for the next document.
   */
  public void clear() {
    html = null;
    detaggedText = null;
    sb.delete(0, sb.capacity());

    if (this.annotTags) {
      for (int i = 0; i < tags.length; i++) {
        tagOffsets[i].reset();
        tagBegins[i].clear();
        tagAttrs.get(i).clear();
      }
    }

    // Shrink the offsets buffer, if necessary
    if (offsetsBuf.length > MAX_OFFSETS_BUF_SIZE) {
      offsetsBuf = new int[MAX_OFFSETS_BUF_SIZE];
    }
  }

  /**
   * Main entry point. Parses the indicated string and fills up internal data structures with
   * results.
   */
  public void detagStr(String str) throws Exception {

    html = str;

    // Set up metadata for translating offsets.
    if (offsetsBuf.length < str.length()) {
      // In the worst case every character in the original string will be
      // copied to the detagged string.
      offsetsBuf = new int[str.length()];
    }

    // Subclass does most of the work here.
    reallyDetag();

    // We won't need this (rather large) string any more after this point.
    html = null;

    // Update document counter for debugging
    docCount++;
  }

  /**
   * Subclasses should override this method to handle the actual mechanics of parsing/detagging once
   * internal data structures are set up.
   */
  protected abstract void reallyDetag() throws Exception;

  /**
   * Call this method after detagging is complete
   * 
   * @return a table that translates offsets into the detagged text into offsets into the original
   *         HTML.
   */
  public int[] getOffsetsTable() {
    // Create a table of exactly the right length. Copying also helps us
    // prevent problems if the caller makes a shallow copy of the returned
    // array.
    int[] ret = new int[getDetaggedText().length()];
    System.arraycopy(offsetsBuf, 0, ret, 0, ret.length);
    return ret;
  }

  /**
   * Access method; call after detagging is complete.
   * 
   * @return the final detagged text
   */
  public String getDetaggedText() {
    if (null == detaggedText) {
      detaggedText = sb.toString();
    }
    return detaggedText;
  }

  /**
   * @param ix index of an HTML tag that this class was told to capture
   * @return offsets of the indicated type of tag into the most recent detagged document
   */
  public OffsetsList getTagOffsets(int ix) {
    return tagOffsets[ix];
  }

  /**
   * @param ix index of an HTML tag that this class was told to capture
   * @return information about the attributes of instances of the tag found in the most recent
   *         document to be detagged; indexes in the returned array correspond to the order in which
   *         attribute names were passed to the constructor of this class
   */
  public ArrayList<String[]> getTagAttrs(int ix) {
    return tagAttrs.get(ix);
  }

  /*
   * INTERNAL UTILITY METHODS
   */

  /**
   * Store information about a tag that the parser/lexer has just encountered the beginning of.
   * 
   * @param tagIx index into {@link #tags}
   * @param offset offset into the detagged text
   * @param htmlOffset offset into the original html text
   * @param attrVals values of any attributes that are being extracted for this tag type, as
   *        specified in {@link #attrs}
   */
  protected void pushTag(int tagIx, int offset, int htmlOffset, String[] attrVals) {
    tagBegins[tagIx].push(offset, htmlOffset, attrVals);
  }

  /**
   * Check to see whether the stack for the indicated tag has at least one element; the stack should
   * contain one element for each open tag that hasn't been closed at the current point in the
   * document.
   * 
   * @param tagIx index into {@link #tags}
   * @return true if the stack for tracking the indicated tag has at least one element
   */
  protected boolean peekTag(int tagIx) {
    return (false == tagBegins[tagIx].empty());
  }

  /**
   * Remove the top entry from the indicated tag's stack, and process any improperly closed tags
   * that occur within the span of this tag in the original html text. Uses {@link #reallyPopTag()}
   * and {@link #drainTagStackByOffset()} to do the actual work. This method only works if the tag
   * stack is non-empty!
   * 
   * @param tagix index into {@link #tags}
   * @param endOffset offset into the original HTML of the tag's end point
   */
  protected void popTag(int tagIx, int endOffset) {

    // Retrieve the begin offsets that we stored in the stack when the top
    // entry was pushed onto the stack
    int beginHtmlOffset = tagBegins[tagIx].peekHtmlOffset();

    // Pop this tag
    reallyPopTag(tagIx, endOffset);

    // Drain all other tag stacks
    drainTagStackByOffset(beginHtmlOffset, endOffset);
  }

  /**
   * Remove the top entry from the indicated tag's stack and store information about the tag's
   * contents. This method only works if the tag stack is non-empty!
   * 
   * @param tagix index into {@link #tags}
   * @param endOffset offset into the original HTML of the tag's end point
   */
  private void reallyPopTag(int tagIx, int endOffset) {
    // Retrieve the begin offset that we stored in the stack when the top
    // entry was pushed onto the stack
    int beginOffset = tagBegins[tagIx].peekOffset();

    // Mark the boundaries of this tag instance in the
    // detagged text.
    tagOffsets[tagIx].addEntry(beginOffset, endOffset);

    // Pass through any information about the tag's
    // attribute values.
    tagAttrs.get(tagIx).add(tagBegins[tagIx].peekAttrs());

    tagBegins[tagIx].pop();

  }

  /**
   * Close out any tags remaining in the tag stacks that start after the specified begin offset in
   * the original html text.
   * 
   * @param beginHtmlOffset offset into the original html document after which we want to close all
   *        tags; usually the begin offset of a tag we have just closed.
   * @param endOffset current offset into the document; usually the end of a properly closed tag.
   */
  private void drainTagStackByOffset(int beginHtmlOffset, int endOffset) {
    if (null == tagBegins) {
      // No stacks to drain.
      return;
    }

    for (int tagIx = 0; tagIx < tagBegins.length; tagIx++) {
      while (false == tagBegins[tagIx].empty()
          && tagBegins[tagIx].peekHtmlOffset() > beginHtmlOffset) {
        reallyPopTag(tagIx, endOffset);
      }
    }
  }

  /**
   * Close out any tags remaining in the tag stacks; usually called at the end of the document to
   * catch improperly terminated tags.
   * 
   * @param endOffset current offset into the document; usually the end of the document
   */
  protected void drainTagStack(int endOffset) {
    if (null == tagBegins) {
      // No stacks to drain.
      return;
    }

    for (int tagIx = 0; tagIx < tagBegins.length; tagIx++) {
      while (false == tagBegins[tagIx].empty()) {
        reallyPopTag(tagIx, endOffset);
      }
    }
  }

  /**
   * Utility method for use in child classes' debugging methods. Formats a string for printing to
   * stderr, making invisible chars visible and truncating if necessary.
   */
  protected static String prettyStr(CharSequence orig) {
    return StringUtils.escapeForPrinting(StringUtils.shorten(orig));
  }

}
