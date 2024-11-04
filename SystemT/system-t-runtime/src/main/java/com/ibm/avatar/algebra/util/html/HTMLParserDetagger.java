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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Vector;

import org.htmlparser.Attribute;
import org.htmlparser.Node;
import org.htmlparser.Remark;
import org.htmlparser.Tag;
import org.htmlparser.lexer.Lexer;
import org.htmlparser.lexer.Page;
import org.htmlparser.nodes.TextNode;
import org.htmlparser.util.Translate;

import com.ibm.avatar.algebra.extract.Detag;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.algebra.util.tokenize.BaseOffsetsList;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.logging.Log;

/**
 * HTML Detagger that uses the Lexer component of HTMLParser to process the raw HTML.
 * 
 */
public class HTMLParserDetagger extends HTMLDetagger {

  private static final char NULL_CHAR = '\0';

  public static final boolean debug = false;

  /** Maximum possible length of an encoded HTML entity. */
  private static final int MAX_ENTITY_LEN = 16;

  /**
   * Should this class throw an exception if it finds an entity it doesn't know how to decode?
   */
  private static final boolean EXCEPTION_ON_UNKNOWN_ENTITY = false;

  /** Class that does most of the heavy lifting. */
  private final Lexer lexer = new Lexer();

  /** Current offset into the detagged text */
  private int detagOffset;

  /** The last character added to the detagged text. */
  private char lastDetagChar = NULL_CHAR;

  /**
   * Flag that is set to true if we are supposed to make sure there is whitespace before appending
   * anything to the detagged text.
   */
  private boolean mustHaveWhitespace = false;

  /**
   * If {@link #mustHaveWhitespace} is true, the location in the original HTML to use for any
   * whitespace added.
   */
  private int whitespaceHTMLOffset = -1;

  /**
   * Flag that is set to true if we're in the process of skipping the contents of a SCRIPT tag.
   */
  private boolean amSkippingText = false;

  /**
   * Flag that is set to true whenever processing embedded CDATA text.
   */
  private boolean cdataMode = false;

  private final int numTags;

  /**
   * Flag that is set to true whenever processing embedded CDATA text.
   */
  private final String XML_HEADER_MARKUP = "<?xml";

  public HTMLParserDetagger(String[] tags, String[][] attrs) {
    super(tags, attrs);

    numTags = tags.length;
  }

  /**
   * Detag the contents of an input stream into a byte array output stream. Useful for displaying
   * the detagged document in tooling. The method will consume the input stream using UTF-8
   * encoding, and close it.
   * 
   * @param docFile the HTML/XML document to detag.
   * @param detectContentType flag indicating whether we should attempt to validate the document as
   *        valid HTML/XML. <br>
   *        Equivalent to setting the AQL flag detect content_type. <br>
   *        If true and the document does not appear to be HTML/XML, pass the document through with
   *        a warning. <br>
   * @return a byte array output stream containing the detagged document
   * @throws IOException if a problem is encountered when opening or closing streams
   * @deprecated Unclear whether we want this functionality as an API going forward. Marking as
   *             deprecated until we can formalize requirements.
   */
  @Deprecated
  public static ByteArrayOutputStream detagFile(InputStream docFile, boolean detectContentType)
      throws IOException {
    HTMLParserDetagger detagger = new HTMLParserDetagger(new String[0], new String[0][0]);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    BufferedReader reader =
        new BufferedReader(new InputStreamReader(docFile, Constants.ENCODING_UTF8));

    byte[] buf = new byte[8192];
    int readBytes = -1;

    // convert the file input stream into one big string for the detagger
    // TODO: rewrite so that the detagger can run on smaller substrings.
    // Currently not implemented that way because the detagger is optimized to
    // run on the entire document at once.
    StringBuilder fullDocument = new StringBuilder();
    while ((readBytes = docFile.read(buf)) >= 0) {
      fullDocument.append(new String(buf, 0, readBytes, Constants.ENCODING_UTF8));
    }

    // run the detagger on the full document and write its output to a stream
    try {
      // The HTML/XML parser sometimes chokes on certain documents. We run
      // the parser inside a try block so as to catch the resulting
      // exceptions.

      String text = fullDocument.toString();
      fullDocument = null;
      final Charset UTF8_CHARSET = Charset.forName("UTF-8");

      // don't run the detagger if we can't identify the text as HTML/XML and detect content_type is
      // true
      if (detectContentType && Detag.isNonHTML(text)) {
        if (debug) {
          Log.debug("Document is not HTML or XML, passing document through.");
        }
        baos.write(text.getBytes(UTF8_CHARSET));
      } else {
        detagger.detagStr(text);
        String detaggedText = detagger.getDetaggedText();
        baos.write(detaggedText.getBytes(UTF8_CHARSET));
      }
    } catch (Throwable t) {
      String errMsg = String.format("While parsing HTML/XML, caught exception: %s", t);

      detagger.clear();
      throw new IOException(errMsg, t);

    } finally {
      // clean up open streams
      reader.close();
      docFile.close();
    }

    return baos;
  }

  @Override
  protected void reallyDetag() throws Exception {

    // Current offset into *detagged* text
    detagOffset = 0;

    lastDetagChar = NULL_CHAR;

    // Wrap the HTML in a Page object, which handles line numbering.
    Page htmlPage = new Page(html);

    lexer.reset();
    lexer.setPage(htmlPage);

    Node node;
    // START - <128652-fix>
    amSkippingText = false;
    // END - <128652-fix>

    // Iterate through the elements of the page.
    while (null != (node = lexer.nextNode(false))) {

      if (debug) {
        Log.debug("Got node: %s", node.toString());
      }

      if (node instanceof org.htmlparser.Text) {

        // Processing some text.
        org.htmlparser.Text text = (org.htmlparser.Text) node;

        handleText(text);

      } else if (node instanceof Tag) {

        // Tag begin
        Tag tag = (Tag) node;

        if (tag.isEmptyXmlTag() || tag.getTagName().equalsIgnoreCase("BR")) {
          // Tag in form <tag/>; functions as both beginning and end.
          // We also call this branch for HTML tags like <BR> that are
          // effectively zero length.
          handleTagBegin(tag);
          handleTagEnd(tag);
        } else if (tag.isEndTag()) {
          // End of a tag.
          handleTagEnd(tag);

        } else {
          // Beginning of a tag.
          handleTagBegin(tag);
        }

      } else if (node instanceof Remark) {

        // HTML Comment
        if (debug) {
          Log.debug("Got HTML comment: %s", node);
        }

      } else {
        throw new IOException("Don't know how to handle node: " + node.toString());
      }

    }

    // Now handle any unterminated tags that are still on the tag stack.
    drainTagStack(detagOffset);

    if (debug) {
      Log.debug("Detagged text: %s", StringUtils.shorten(sb.toString(), 200, true));
    }

  }

  /**
   * Handle a region of text in the HTML document.
   * 
   * @param text parser node that encapsulates the text and its offset information.
   */
  private void handleText(org.htmlparser.Text text) {

    if (text.getText().startsWith(XML_HEADER_MARKUP)) {
      // SPECIAL CASE: Skipping the text of an XML header tag
      // Fix for bug [#161892] Detagger passes through XML headers
      // when used with IBM version of HTMLParser.
      return;
      // END SPECIAL CASE
    }

    if (amSkippingText) {
      // SPECIAL CASE: Skipping the text of a SCRIPT tag.
      return;
      // END SPECIAL CASE
    }

    if (cdataMode) {
      // SPECIAL CASE: Handling a CDATA section; check for the end of the
      // section
      boolean foundEnd = handleCdataText(text);

      if (foundEnd) {
        // We found and handled the end of the CDATA segment; no more
        // processing is necessary
        return;
      }
      // END SPECIAL CASE
    }

    // Extract offsets into the original HTML from the lexer node.
    int htmlBegin = text.getStartPosition();
    int htmlEnd = text.getEndPosition();

    if (debug) {
      Log.debug("Got text from %d to %d: '%s'/'%s'", htmlBegin, htmlEnd, prettyStr(text.getText()),
          prettyStr(html.subSequence(htmlBegin, htmlEnd)));
    }

    if (htmlBegin == htmlEnd) {
      // SPECIAL CASE: Zero-length string.
      return;
      // END SPECIAL CASE
    }

    // Check whether we need to insert some extra whitespace due to a tag.
    if (mustHaveWhitespace) {
      mustHaveWhitespace = false;

      if (debug) {
        Log.debug(" --> Ensuring whitespace; last char was '%s'",
            StringUtils.escapeForPrinting(new String(new char[] {lastDetagChar})));
      }

      // Only need to insert whitespace if the text we're inserting
      // doesn't start with one and the last text inserted doesn't end
      // with one.
      if (false == Character.isWhitespace(lastDetagChar) && lastDetagChar != NULL_CHAR
          && false == Character.isWhitespace(html.charAt(htmlBegin))) {
        if (debug) {
          Log.debug("   +--> Adding whitespace");
        }

        sb.append(' ');
        offsetsBuf[detagOffset] = whitespaceHTMLOffset;
        detagOffset++;
        lastDetagChar = ' ';

        // Update any entries in the tag stack to ensure that the
        // artificial whitespace we just added doesn't end up marked as
        // being inside a tag.
        fixTagStack();
      } else {
        if (debug) {
          Log.debug("   +--> NOT adding whitespace");
        }
      }

    }

    // Go through the text, escaping characters.
    int len = htmlEnd - htmlBegin;
    for (int i = 0; i < len; i++) {
      char c = html.charAt(htmlBegin + i);

      boolean isEscape = false;

      if ('&' == c && (htmlBegin + i < html.length())) {
        // Found an ampersand; look for an HTML entity escape code.
        int entityEnd = i;
        char endChar = NULL_CHAR;

        char nextEndChar = (htmlBegin + entityEnd + 1 < html.length() - 1) ? //
            html.charAt(htmlBegin + entityEnd + 1) : NULL_CHAR;

        while (entityEnd < i + MAX_ENTITY_LEN //
            && endChar != ';' //
            && nextEndChar != '&' //
            && false == Character.isSpaceChar(endChar) //
            && htmlBegin + entityEnd < html.length() - 1) {
          entityEnd++;
          endChar = html.charAt(htmlBegin + entityEnd);

          // Need lookahead to catch ampersands in (bad) entity
          // strings.
          if (htmlBegin + entityEnd + 1 < html.length() - 1) {
            nextEndChar = html.charAt(htmlBegin + entityEnd + 1);
          } else {
            nextEndChar = NULL_CHAR;
          }
        }

        if (';' == endChar) {

          // Found an entity code; decode it, then skip over it.
          String entity = html.substring(htmlBegin + i, htmlBegin + entityEnd + 1);

          String decoded;
          if ("&nbsp;".equals(entity) || "&#160;".equals(entity) || "&#0160;".equals(entity)) {
            // SPECIAL CASE:
            // HTMLParser likes to turn &nbsp; and &#160; into
            // special
            // non-printing characters that confuse web browsers.
            // Use a normal space instead.
            decoded = " ";
            // END SPECIAL CASE
          } else if ("&apos;".equals(entity)) {
            // SPECIAL CASE:
            // HTMLParser doesn't know about the &apos; (') escape
            // found in XHTML.
            decoded = "'";
            // END SPECIAL CASE
          } else {
            decoded = Translate.decode(entity);
          }

          if (decoded.length() == entity.length()) {
            // Translater just passed the entity through.
            // See if it's a numeric code that we can translate.
            if (entity.length() > 3 && entity.length() < 7 && '#' == entity.charAt(1)) {
              char val = 0;
              if ('0' == entity.charAt(2)) {
                // Octal code, like &#0160;
                for (int pos = 3; pos < entity.length() - 1; pos++) {
                  val *= 8;
                  val += (entity.charAt(pos) - '0');
                }
              } else {
                // Decimal code, like &#39; or &#126;
                for (int pos = 3; pos < entity.length() - 1; pos++) {
                  val *= 10;
                  val += (entity.charAt(pos) - '0');
                }
              }
              decoded = new String(new char[] {val});
            }
          }

          if (debug) {
            Log.debug("Decoded entity '%s' to '%s'", entity, decoded);
          }

          if (1 == decoded.length()) {
            // The translater turned the entity into a single
            // character.
            sb.append(decoded.charAt(0));
            offsetsBuf[detagOffset] = htmlBegin + i;
            detagOffset++;

            // Don't put the rest of the escape into our buffer...
            i = entityEnd;

            isEscape = true;
          } else if (decoded.length() != entity.length() && EXCEPTION_ON_UNKNOWN_ENTITY) {
            throw new RuntimeException(String.format(
                "Expected '%s' to" + " decode to one character," + " but got back '%s'", entity,
                decoded));
          }
        }
      }

      if (false == isEscape) {
        // If current character is '\u00A0' (Unicode codepoint for a non-breaking space), normalize
        // it to a single whitespace
        // to retain consistency in how this detagger treats valid HTML entities such as '&nbsp;' or
        // '&#160;' or '&#0160;'
        // that represent a non-breaking space
        if (c == '\u00A0')
          sb.append(' ');
        else
          sb.append(c);
        offsetsBuf[detagOffset] = htmlBegin + i;
        detagOffset++;
      }
    }

    lastDetagChar = html.charAt(htmlEnd - 1);
  }

  /**
   * Special-case processing for text inside a CDATA segment. Checks to see whether the text
   * contains the string that marks the end of the CDATA. When this method returns true, it
   * processes the text it has received. If this method returns false, it does no processing.
   * 
   * @param text lexer node for the text in question
   * @return true if this method found and handled the end of the CDATA segment; false otherwise.
   */
  private boolean handleCdataText(org.htmlparser.Text text) {
    final String END_OF_CDATA_STR = "]]>";
    String textStr = text.getText();
    int cdataEndIx = textStr.indexOf(END_OF_CDATA_STR);
    boolean foundEnd = (-1 != cdataEndIx);
    if (foundEnd) {
      // Found the end of the CDATA segment. Generate the parse nodes
      // that the lexer ought to have generated up to this point, and try
      // again.
      if (cdataEndIx > 0) {
        String beforeEnd = textStr.substring(0, cdataEndIx);
        TextNode fakeTextNode = new TextNode(beforeEnd);
        fakeTextNode.setStartPosition(text.getStartPosition());
        fakeTextNode.setEndPosition(fakeTextNode.getStartPosition() + beforeEnd.length());
        handleText(fakeTextNode);
      }

      // We've now reached the end of the segment, so turn off CDATA
      // mode
      cdataMode = false;

      // Generate the node that the lexer should have generated for
      // the text after the CDATA segment, and process it normally.
      int afterEndBeginIx = cdataEndIx + END_OF_CDATA_STR.length();
      if (afterEndBeginIx < textStr.length() - 1) {
        String afterEnd = textStr.substring(afterEndBeginIx);
        TextNode fakeTextNode = new TextNode(afterEnd);
        fakeTextNode.setStartPosition(text.getStartPosition() + afterEndBeginIx);
        fakeTextNode.setEndPosition(fakeTextNode.getStartPosition() + afterEnd.length());
        handleText(fakeTextNode);
      }
    }
    return foundEnd;
  }

  /**
   * Call this method every time you add an artifical whitespace character to the detagged text.
   * Update any entries in the tag stack to ensure that the artificial whitespace we just added
   * doesn't end up marked as being inside a tag.
   */
  private void fixTagStack() {
    for (int i = 0; i < numTags; i++) {
      // Update begin offsets of open tags such that the tag begins at the offset at which the
      // whitespace was just inserted
      tagBegins[i].correctOffset(detagOffset - 1, detagOffset);

      // Update begin *and* end offsets of *empty* tags that were recently popped
      // such that in the detagged text, the empty tag previously began at the offset at which a
      // whitespace
      // was just inserted (fixes SystemT-23)
      if (((BaseOffsetsList) getTagOffsets(i)).updatePoppedEmptyTagEntries(detagOffset - 1,
          detagOffset))
        // Ensure that such an update has no impact upon the Remap operation
        offsetsBuf[detagOffset] = offsetsBuf[detagOffset - 1];
    }
  }

  /**
   * Handler for a "beginning of HTML tag" event.
   * 
   * @param tag parser information about the HTML tag
   */
  private void handleTagBegin(Tag tag) {

    // String that marks the beginning of a CDATA segment
    final String CDATA_BEGIN_STR = "![CDATA[";

    boolean foundBeginIgnoreTag = false;

    String tagName = tag.getTagName();

    if (debug) {
      Log.debug("Got tag BEGIN: %s", tagName);
    }

    if (SCRIPT_TAG.equalsIgnoreCase(tagName) || STYLE_TAG.equalsIgnoreCase(tagName)) {

      // We will ignore any tag contained within this tag
      // including another (nested) <script> or <style> tag
      if (!amSkippingText)
        foundBeginIgnoreTag = true;

      // We will ignore any text contained within this tag.
      amSkippingText = true;

    }

    if (false == amSkippingText && tagName.startsWith(CDATA_BEGIN_STR)) {
      // SPECIAL CASE: CDATA segment.
      if (debug) {
        Log.debug(" --> Start of CDATA segment");
      }

      // The lexer attaches the text to the end of the line, the end of
      // the CDATA segment, or the next tag begin, whichever is shorter,
      // to the tag.
      // Pull out this text and send it to our output.
      String tagText = tag.getText();
      String innerText = tagText.substring(CDATA_BEGIN_STR.length());

      // Here's the tricky part: The CDATA segment is supposed to go until
      // it reaches the string "]]>". However, if the CDATA all fits on
      // one line, the lexer will strip off the final ">" and return us a
      // string that ends with "]]".
      if (innerText.endsWith("]]")) {
        // Ok, the inner text ends with "]]". Is this happening because
        // the lexer stripped off the ">", or was there a "]]" in the
        // text, followed by a carriage return or tag?
        // Go back to the original text to fine out.
        int offAfterTag = tag.getEndPosition();
        if (offAfterTag < html.length() && '>' == html.charAt(offAfterTag - 1)) {
          // The Tag object *does* cover the entire CDATA segment. Add
          // the ending ">" so that downstream processing will occur
          // correctly.
          innerText = innerText + ">";
        }
      }

      if (debug) {
        Log.debug(" --> Inner text: %s", innerText);
      }

      // We will attempt to parse any additional HTML tags within the
      // CDATA segment, on the assumption that it is wrapping HTML.
      // Note that the script/style tag detection code above should
      // prevent us from attempting to parse JavaScript or CSS as html.
      cdataMode = true;

      // Generate the node that the lexer *should* have generated at this
      // point. Fill in enough information that the downstream processing
      // can proceed normally.
      TextNode fakeTextNode = new TextNode(innerText);
      fakeTextNode.setStartPosition(tag.getStartPosition() + CDATA_BEGIN_STR.length() + 1);
      fakeTextNode.setEndPosition(fakeTextNode.getStartPosition() + innerText.length());
      handleText(fakeTextNode);

      return;
      // END SPECIAL CASE
    }

    // ignore any tags within a SCRIPT or STYLE tag, but make sure we don't ignore the tag itself
    if (foundBeginIgnoreTag || !amSkippingText) {
      if (getAnnotateTags()) {

        if (debug) {
          Log.debug("Processed tag   END: %s", tagName);
        }

        // Add the current text offset to all the relevant stacks.
        for (int i = 0; i < this.tags.length; i++) {
          if (tags[i].equalsIgnoreCase(tagName)) {

            // Decode attributes, if requested.
            String[] attrVals = null;
            String attrName = null;
            String attrVal = null;
            if (attrs[i].length > 0) {
              attrVals = new String[attrs[i].length];
              for (int attrIx = 0; attrIx < attrs[i].length; attrIx++) {
                attrName = attrs[i][attrIx];
                attrVal = null;
                // SPECIAL CASE: When the attribute name is the same as the tag name, the lexer
                // confuses the tag name
                // with the attribute because it stores botht he tag name and the attributes as
                // elements of a single
                // array; skip the tag name to get at the real attribute
                if (attrName.equalsIgnoreCase(tagName)) {
                  @SuppressWarnings("rawtypes")
                  // Get all the attributes
                  Vector tagAttributes = tag.getAttributesEx();

                  // The tag name is the first attribute; skip it
                  if (1 == tagAttributes.size())
                    break;

                  // Go through each attribute, ignoring the first one (the tag name)
                  for (int ix = 1; ix < tagAttributes.size(); ix++) {
                    if (tagAttributes.get(ix) instanceof Attribute) {
                      Attribute attr = (Attribute) tagAttributes.get(ix);

                      // Found the attribute; get its value and break out of the loop
                      if (attrName.equalsIgnoreCase(attr.getName())) {
                        attrVal = attr.getValue();
                        break;
                      }
                    }
                  }
                }
                // END: SPECIAL CASE
                else
                  attrVal = tag.getAttribute(attrName);

                attrVals[attrIx] = attrVal;
              }
            }

            pushTag(i, detagOffset, tag.getStartPosition(), attrVals);
          }
        }
      }
    }

    // Add some whitespace to the end of the detagged text, if there isn't
    // some already.
    // ensureWhiteSpace(tag.getEndPosition() - 1);
    ensureWhiteSpace(tag.getStartPosition());

  }

  private void handleTagEnd(Tag tag) {

    String tagName = tag.getTagName();

    if (debug) {
      Log.debug("Got tag   END: %s", tagName);
    }

    if (amSkippingText
        && (SCRIPT_TAG.equalsIgnoreCase(tagName) || STYLE_TAG.equalsIgnoreCase(tagName))) {
      // We've reached the end of a region of text that we're skipping.
      amSkippingText = false;
    }

    // ignore any tags within a SCRIPT or STYLE tag
    if (amSkippingText) {
      if (debug) {
        Log.debug("Skipped tag   END: %s", tagName);
      }
    } else {
      // Only if the tags annotated as a part of the detag statement are used towards an output
      // view, enter this block
      if (getAnnotateTags()) {

        if (debug) {
          Log.debug("Processed tag   END: %s", tagName);
        }

        for (int i = 0; i < tags.length; i++) {
          if (tags[i].equalsIgnoreCase(tagName)) {

            // Found a closing tag of a type that we're monitoring.
            // Make sure that we've tracked the position of the
            // corresponding opening tag.
            if (peekTag(i)) {

              /*
               * Fix for defect# 13100:Remap() exception with empty detagged documents
               */
              // Condition to identify empty tags (for example: </br>, <title></title> etc.)
              if (tag.isEmptyXmlTag() || tagBegins[i].peekHtmlOffset()
                  + tag.getText().length() == tag.getStartPosition() - 1) {

                /*
                 * IMP: 04/11/2018 - Added by Ramiya - We no longer use sb.append(" ") for this case
                 * as we map such empty tags now to empty spans instead of a placeholder whitespace.
                 * Due to the addition of whitespace earlier at this place in the code, the detagged
                 * output would differ by an offset of 1 for every such empty annotated tags that
                 * were used towards an output view vs. not used towards an output view leading to
                 * inconsistency.
                 **/
                // sb.append(" ");

                // Adding correct offset(location) of empty html
                // tag , this offset will later be used by
                // remap function
                offsetsBuf[detagOffset] = tag.getStartPosition();
                popTag(i, detagOffset);
              } else {
                // Non empty tag branch
                popTag(i, detagOffset);
              }

              // popTag(i, detagOffset);
            }

          }
        }
      }
    }

    // Add some whitespace to the end of the detagged text, if there isn't
    // some already.
    ensureWhiteSpace(tag.getEndPosition() - 1);
  }

  /**
   * Add some whitespace to the detagged text, if there isn't already some at the current position.
   * 
   * @param curHTMLPos our current position in parsing the original HTML; any new space added will
   *        be mapped to this location.
   */
  private void ensureWhiteSpace(int curHTMLPos) {
    // if (detagOffset > 0
    // && false == Character.isWhitespace(sb.charAt(detagOffset))) {
    // sb.append(' ');
    // offsetsBuf[detagOffset] = curHTMLPos;
    // detagOffset++;
    // }
    mustHaveWhitespace = true;
    whitespaceHTMLOffset = curHTMLPos;
  }

}
