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
package com.ibm.avatar.aql.doc;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ibm.avatar.logging.Log;

/**
 * Represents an AQL doc comment for an AQL statement, or an AQL module.
 * 
 */
public class AQLDocComment {

  /*
   * CONSTANTS
   */

  private static final boolean debug = false;

  /**
   * Pattern that matches any number of whitespace and a single asterisk on the beginning of each
   * line of the input. Initialized statically for performance. Used by {@link #cleanUp} to clean up
   * the content of the comment. java.regex.Pattern is thread safe, so it's safe to use a static
   * pattern. On the other hand, java.regex.Matcher is not thread safe, so {@link #cleanUp} uses a
   * new Matcher object each time, to ensure correct results when the AQLParser is called from
   * multiple threads.
   */
  private static Pattern PATTERN_CLEANER_LEADING_ASTERISK;
  private static Pattern PATTERN_CLEANER_CR;
  static {
    PATTERN_CLEANER_LEADING_ASTERISK = Pattern.compile("^\\s*\\*", Pattern.MULTILINE);
    PATTERN_CLEANER_CR = Pattern.compile("\r+");
  }

  /** Leading comment separator */
  private static final String LEADING_COMMENT_SEP = "/**";
  /** Trailing comment separator */
  private static final String TRAILING_COMMENT_SEP = "*/";

  /*
   * Various comment tags that we special case for now
   */
  /**
   * Tag describing the schema of the view Document in the module comment file. Needed for
   * serializing the Document schema in the module metadata.
   */
  private static final String TAG_DOC_FIELD = "@docField";

  /**
   * Tag describing a field of a view. Currently used to serialize info about the fields of a main
   * detag view in the view metadata.
   */
  private static final String TAG_FIELD = "@field";

  /**
   * Tag describing a detag auxiliary view. Currently used to serialize the subpart of the comment
   * of a detag statement for a given auxiliary views in the view metadata.
   */
  private static final String TAG_AUX_VIEW = "@auxView";

  /**
   * Tag describing a field of a detag auxiliary view. Currently used to serialize the subpart of
   * the comment of a detag statement for a given auxiliary views in the view metadata.
   */
  private static final String TAG_AUX_VIEW_FIELD = "@auxViewField";

  /*
   * PRIVATE FIELDS
   */

  /** The original string content of the comment. */
  private String text;

  /**
   * A cleaned-up version of the comment content, with leading and trailing markers, and any lead
   * wildcards removed. This version is serialized in the module metadata.
   */
  private String cleanText;

  /**
   * The portion of the clean text of the comment before the first tag.
   */
  private String description = "";

  /**
   * We don't parse the comment into components unless when explicitly requested. Make sure we don't
   * parse it more than once.
   */
  private boolean parsed = false;

  /**
   * The tags of the comment.
   */
  private List<Tag> tags = new ArrayList<Tag>();

  /** The line where the comment begins in the original AQL file. */
  private int beginLine;

  /** The column where the comment begins in the original AQL file. */
  private int beginColumn;

  /** The line where the comment ends in the original AQL file. */
  private int endLine;

  /** The column where the comment ends in the original AQL file. */
  private int endColumn;

  /**
   * @param beginLine
   * @param beginColumn
   * @param endLine
   * @param endColumn
   * @param text
   */
  public AQLDocComment(int beginLine, int beginColumn, int endLine, int endColumn, String text) {
    this.text = text;
    this.beginLine = beginLine;
    this.beginColumn = beginColumn;
    this.endLine = endLine;
    this.endColumn = endColumn;

    // Clean up the text by removing leading and trailing comment separators, stripping '\r' and
    // removing leading
    // whitespace plus the first asterisk (*) from each line.
    cleanUp();
  }

  /**
   * Copy constructor
   * 
   * @param orig object to duplicate
   */
  public AQLDocComment(AQLDocComment orig) {
    this.text = orig.text;
    this.beginLine = orig.beginLine;
    this.beginColumn = orig.beginColumn;
    this.endLine = orig.endLine;
    this.endColumn = orig.endColumn;
  }

  /**
   * Return the original content of this comment.
   * 
   * @return the content of this comment
   */
  public String getText() {
    return text;
  }

  /**
   * Return the content of the comment processed as follows:
   * <ul>
   * <li>Leading comment separator (/**) and trailing comment separator (*&#47;) are removed</li>
   * <li>Leading asterisk (*) characters on each line are discarded; blanks and tabs preceding the
   * initial asterisk (*) characters are also discarded. If you omit the leading asterisk on a line,
   * the leading white space is not removed.</li>
   * <li>Carriage return characters (\r) are removed.</li>
   * </ul>
   * 
   * @return the text with the following removed: leading and trailing comment separators, carriage
   *         return characters (\r); leading consecutive whitespace together with the first asterisk
   *         at the beginning of each line
   */
  public String getCleanText() {
    return cleanText;
  }

  @Override
  public String toString() {
    return text;
  }

  /**
   * The line number of the beginning of this comment.
   * 
   * @return the line number of the beginning of this comment
   */
  public int getBeginLine() {
    return beginLine;
  }

  /**
   * The column number of the beginning of this comment.
   * 
   * @return the column number of the beginning of this comment
   */
  public int getBeginColumn() {
    return beginColumn;
  }

  /**
   * The line number of the end of this comment.
   * 
   * @return the line number of the end of this comment
   */
  public int getEndLine() {
    return endLine;
  }

  /**
   * The column number of the end of this comment.
   * 
   * @return the column number of the end of this comment
   */
  public int getEndColumn() {
    return endColumn;
  }

  /**
   * Return the description of the comment (the first part of the comment, before the first tag.
   * 
   * @return
   */
  public String getDescription() {
    parseAndStoreTags();

    return description;
  }

  /**
   * Return all tags with a given name.
   * 
   * @param name The name of the tag (including the leading '@')
   * @return
   */
  public List<Tag> getTags(String name) {
    parseAndStoreTags();

    List<Tag> tags = new ArrayList<Tag>();

    for (Tag tag : tags) {
      if (TAG_DOC_FIELD.equals(tag.getName())) {
        tags.add(tag);
      }
    }

    return tags;
  }

  /**
   * Return all tags.
   * 
   * @param name
   * @return
   */
  public List<Tag> getTags() {
    parseAndStoreTags();

    return tags;
  }

  /**
   * Extract the part of the comment that describes the schema of the Document view from the module
   * comment, if present.
   * 
   * @return
   */
  public String getDocumentComment() {
    parseAndStoreTags();

    boolean atLeastOneTag = false;

    StringBuffer sb = new StringBuffer();
    for (Tag tag : tags) {
      if (TAG_DOC_FIELD.equals(tag.getName())) {
        if (false == atLeastOneTag) {
          atLeastOneTag = true;
        } else {
          sb.append('\n');
        }
        sb.append(tag.toString());
      }
    }

    return sb.toString();
  }

  /**
   * Extract the part of the comment that describes the main detag view from the comment of a detag
   * statement, if present
   * 
   * @return the description part of the comment, together with any @field tags
   */
  public String getDetagViewComment() {
    parseAndStoreTags();

    // Add the description
    StringBuffer sb = new StringBuffer();
    if (null != description) {
      sb.append(description);
      sb.append('\n');
    }

    // Add any @field tags
    boolean atLeastOneTag = false;
    for (Tag tag : tags) {
      if (TAG_FIELD.equals(tag.getName())) {

        if (false == atLeastOneTag) {
          atLeastOneTag = true;
        } else {
          sb.append('\n');
        }

        sb.append(tag.toString());
      }
    }

    return sb.toString();
  }

  /**
   * Extract the part of the comment of that describes an auxiliary view from the comment of a detag
   * statement, if present
   * 
   * @param name name of the auxiliary view
   * @return the description part of the comment, together with any @auxView and @auxViewField tags
   */
  public String getDetagAuxiliaryViewComment(String name) {
    parseAndStoreTags();

    // Add the description
    StringBuffer sb = new StringBuffer();
    if (null != description) {
      sb.append(description);
      sb.append('\n');
    }

    // Add @auxView or @auxViewField content only if it starts with the view name
    boolean atLeastOneTag = false;
    for (Tag tag : tags) {
      if ((TAG_AUX_VIEW.equals(tag.getName()) || TAG_AUX_VIEW_FIELD.equals(tag.getName()))
          && tag.getText().startsWith(name)) {

        if (false == atLeastOneTag) {
          atLeastOneTag = true;
        } else {
          sb.append('\n');
        }

        sb.append(tag.toString());
      }
    }

    return sb.toString();
  }

  /*
   * PRIVATE METHODS
   */

  /**
   * Clean up the text of the comment. Remove the leading and trailing comment separators. Also
   * remove, from each line of the remaining text, any number of consecutive whitespace together
   * with the first asterisk.
   */
  private void cleanUp() {
    if (null != this.text) {

      cleanText = this.text;

      // Remove the leading /* and the trailing */ comment separators
      if (cleanText.startsWith(LEADING_COMMENT_SEP))
        cleanText = cleanText.substring(2);
      if (cleanText.endsWith(TRAILING_COMMENT_SEP))
        cleanText = cleanText.substring(0, cleanText.length() - 2);

      // TODO: If pattern matching turns out to be slow, we should rewrite the rest of this method
      // to process the test
      // in one pass left to right.
      // Remove any leading asterisk at the beginning of each line in the comment
      // Initialize a new matcher. We don't use a static Matcher object, because java.regex.Matcher
      // is not thread safe.
      // On the other hand, java.regex.Pattern is thread safe, therefore it is safe to use a static
      // Pattern.
      Matcher m = PATTERN_CLEANER_LEADING_ASTERISK.matcher(cleanText);
      cleanText = m.replaceAll("");

      // Remove carriage returns because they mess with our regression tests
      m = PATTERN_CLEANER_CR.matcher(cleanText);
      cleanText = m.replaceAll("");

      if (debug)
        Log.debug("Original comment was:\n %s\nClean comment is:\n%s\n", this.text, cleanText);

    }
  }

  /**
   * Parse the cleaned up text of the comment into individual components: a description, followed by
   * 0 or more tags.
   */
  private void parseIntoComponents() {
    if (null != cleanText) {
      // Examine each character starting left to right. At each character, we are in exactly one of
      // the following 3
      // states:
      // Inside the description, or the text of a tag
      final int IN_TEXT = 1;
      // Inside a tag name (e.g., the token after a '@'
      final int IN_TAG_NAME = 2;
      // Immediately after a tag name, skipping to the next non-whitespace character, where we would
      // start processing
      // text
      final int AFTER_TAG_NAME = 3;

      // We start at the beginning, which is the same as starting after a tag name, only the tag
      // name is null. In this
      // situation we will make the text parsed to the end of the comment, or to the beginning of
      // the next tag name the
      // comment description.
      int state = AFTER_TAG_NAME;
      boolean newLine = true;

      int tagStart = 0, textStart = 0;

      String tagName = null;
      int len = cleanText.length();

      for (int ix = 0; ix < len; ix++) {
        char c = cleanText.charAt(ix);
        boolean isWhitespace = Character.isWhitespace(c);

        switch (state) {
          case IN_TAG_NAME:
            if (isWhitespace) {
              // Found whitespace => complete tag name
              tagName = cleanText.substring(tagStart, ix);
              state = AFTER_TAG_NAME;
            }
            break;
          case AFTER_TAG_NAME:
            // Found a whitespace => skip it
            if (isWhitespace) {
              break;
            }
            // Otherwise, found the next character after the tag name => if it's not '@', start
            // processing text;
            // otherwise
            // need to start a new tag
            textStart = ix;
            state = IN_TEXT;
            // fall through to start processing the text of the tag, or the next tag
          case IN_TEXT:
            // Found a '@' after a new line (and multiple skipped whitespace) => save the current
            // tag and start
            // processing
            // the next tag
            if (newLine && c == '@') {
              parseTag(tagName, textStart, ix);

              tagStart = ix;
              state = IN_TAG_NAME;
            }
            break;
        }

        // Found a new line ?
        if ('\n' == c) {
          newLine = true;
        }
        // Once a new line is found, we skip any consecutive whitespace following it
        else if (!isWhitespace) {
          newLine = false;
        }
      }

      // We reached the end of the comment, so process the last tag.
      switch (state) {
        case IN_TAG_NAME:
          // The name of the tag spans to the end of the comment
          tagName = cleanText.substring(tagStart, len);
        case AFTER_TAG_NAME:
          // The text of the tag starts at the end of the comment (essentially, no text)
          textStart = len;
        case IN_TEXT:
          parseTag(tagName, textStart, len);
      }
    }
  }

  /**
   * Process a tag and add it to the list of tags.
   * 
   * @param comment clean text of the comment
   * @param tagName name of the tag, or null if no tag
   * @param begin index in the input comment where the tag's text begins
   * @param end index in the input comment where the tag's text ends
   */
  private void parseTag(String tagName, int begin, int end) {
    String text = (end <= begin) ? "" : cleanText.substring(begin, end).trim();

    if (null == tagName) {
      // No tag name. This means that we are processing the first part of the comment, i.e., the
      // description
      this.description = text;
    } else {
      // We have a tag name. Make a new tag and add it to the list
      TagImpl tag = new TagImpl(tagName, text);
      tags.add(tag);
    }
  }

  /**
   * If this comment has not been parsed into components yet, parse it and store the components
   * (description and individual tags). For now, catch any exception because we do not want any
   * problem in the code for extracting various portions of the comment to interfere with otherwise
   * successful module compilation
   */
  private void parseAndStoreTags() {
    if (!parsed)
      try {
        parseIntoComponents();
      } catch (Throwable e) {
        Log.debug("Exception encountered when parsing AQL doc\n'%s'\n%s", cleanText,
            e.getMessage());
      }

    parsed = true;
  }

}
