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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/** A class for reading DB2 dump files. */
public class DumpFileReader {

  public static final int LONG_TYPE = 1;
  public static final int STRING_TYPE = 2;

  private static final char QUOTE_CHAR = '"';

  private static final char FIELD_SEP_CHAR = ',';

  private static final char NEWLINE = '\n';

  private static final char RECORD_SEP_CHAR = NEWLINE;

  private static final char RETURN = '\r';

  private static final char EOF_CHAR = '\0';

  public static final char COMMENT_CHAR = '#';

  private static final int BUFSZ = 8192;

  private InputStreamReader in;

  /** Expected schema of input tuples. */
  private final int[] schema;

  /** Buffer for input characters. */
  private final char[] buf = new char[BUFSZ];

  private int bufUsed = 0;

  private int bufPos = 0;

  /** How many lines have we read? */
  private int lineCount = 0;

  /** Buffer for building up the string representation of the current field. */
  private final StringBuilder sb = new StringBuilder();

  /** flag that determines whether carriage returns before new lines are stripped */
  private boolean stripCR;

  public DumpFileReader(File dumpFile, String encoding, int[] schema, boolean stripCR)
      throws IOException {
    in = new InputStreamReader(new FileInputStream(dumpFile), encoding);

    fillBuf();

    // Skip any comments at the beginning of the file.
    skipComments();

    this.schema = schema;

    this.stripCR = stripCR;
  }

  /**
   * @return next tuple in the input file, or null on EOF.
   */
  public Object[] getNextTup() throws IOException {
    Object[] ret = new Object[schema.length];

    // Read each column of the schema.
    for (int i = 0; i < schema.length; i++) {

      // System.err.printf("Reading field %d...\n", i);

      boolean last = (i == schema.length - 1);

      switch (schema[i]) {

        case LONG_TYPE:
          ret[i] = readLong(last);
          break;
        case STRING_TYPE:
          ret[i] = readString(last);
          break;

        default:
          throw new RuntimeException("Unknown schema element");
      }
    }

    // Skip any comments after the current record.
    skipComments();

    return ret;
  }

  /**
   * Skip a line of the input without trying to parse it. Useful for skipping headers of known
   * length.
   */
  public void skipLine() throws IOException {
    while (peek() != NEWLINE) {
      shift();
      if (endOfInput()) {
        // Reached EOF while reading comment.
        return;
      }
    }

    // Skip the newline at the end of the line.
    shift();
  }

  /**
   * Skip ahead over any comment lines (e.g. lines that start with {@link #COMMENT_CHAR}. Assumes
   * that our cursor is positioned at the beginning of a line.
   */
  private void skipComments() throws IOException {
    while (peek() == COMMENT_CHAR) {
      // Found a comment; skip ahead past the next newline
      skipLine();
      // while (peek() != NEWLINE) {
      // shift();
      // if (endOfInput()) {
      // // Reached EOF while reading comment.
      // return;
      // }
      // }
      //
      // // Skip the newline at the end of the line.
      // shift();
    }
  }

  /**
   * @return true if we have reached the end of the input file and consumed all buffered data.
   */
  public boolean endOfInput() {
    if (null != in) {
      return false;
    }

    if (bufPos < bufUsed) {
      return false;
    }

    return true;
  }

  /**
   * @param last true when reading the last field on a line; false for all other fields
   * @return the next string field in the current line of input
   */
  private String readString(boolean last) throws IOException {

    if (EOF_CHAR == peek()) {
      throw new IOException("Read past end of file");
    }

    // What character do we expect to find immediately after the field?
    char charAfterField = last ? RECORD_SEP_CHAR : FIELD_SEP_CHAR;

    // System.err.printf("Reading field; char after field should be '%c'\n",
    // charAfterField);

    // Check whether the current field is enclosed in the quote character
    boolean quotedField = false;
    if (QUOTE_CHAR == peek()) {
      // System.err.printf(" Field enclosed in quotes\n");
      quotedField = true;
    }

    if (quotedField) {
      // Ignore the quote.
      shift();
    }

    // Clear out the string buffer, then create the new string.
    sb.delete(0, sb.length());

    while (true) {

      if (endOfInput()) {
        throw new IOException(String.format("Unexpected EOF on line %d", lineCount + 1));
      }

      char c = shift();

      // System.err.printf("Got character '%c'\n", c);

      if (quotedField && QUOTE_CHAR == c) {
        // Found a quote; check whether it's the beginning of an escape
        // sequence or the end of the string.
        char next = peek();
        if (QUOTE_CHAR == next) {
          // Escaped quote
          shift();
          sb.append(c);
        } else if (charAfterField == next) {
          // End of field. Advance to next char, if possible.
          shift();
          return sb.toString();
        } else if (NEWLINE == charAfterField && RETURN == next) {
          // SPECIAL CASE: Record/field terminated with \r\n
          shift();
          next = peek();
          if (charAfterField == next) {
            // End of field. Advance to next char, if possible.
            shift();
            return sb.toString();
          } else {
            throw new IOException(
                String.format("Improperly terminated " + "field on line %d\n", lineCount + 1));
          }
          // END SPECIAL CASE
        } else if (EOF_CHAR == next) {
          // Reached end of file
          if (last) {
            // EOF after last field, which is OK
            return sb.toString();
          } else {
            // EOF in the middle of a record!
            throw new IOException("Incomplete last record");
          }
        } else {
          System.err.printf("next = '%c'\n", next);
          throw new IOException(String
              .format("Improperly terminated field on line %d" + " of dump file", lineCount + 1));
        }
      } else if (false == quotedField && charAfterField == c) {
        // Field is not enclosed in quotes, and we found the character
        // that comes after this field.
        return sb.toString();
      } else if (stripCR && RETURN == c && NEWLINE == peek()) {
        // do nothing if we have a CR followed by an LF
      } else {
        // Otherwise, pass the character through.
        sb.append(c);
      }
    }
  }

  /**
   * @param last true when reading the last field on a line; false for all other fields
   * @return next Long field in the current line of input
   */
  private Long readLong(boolean last) throws IOException {
    return Long.valueOf(readString(last));
  }

  /**
   * Returns, but does not remove, the next character in the input stream
   * 
   * @return next character in the input stream
   */
  private final char peek() throws IOException {
    if (endOfInput()) {
      return EOF_CHAR;
    }
    return buf[bufPos];
  }

  /**
   * Removes and returns the next character in the input stream.
   */
  private final char shift() throws IOException {
    char ret = peek();

    bufPos++;
    if (bufPos >= bufUsed) {
      // End of buffer; fill it up again.
      fillBuf();
    }

    if ('\n' == ret) {
      lineCount++;
    }

    return ret;
  }

  private final void fillBuf() throws IOException {
    if (null == in) {
      throw new IOException("Read past end of file (1)");
    }

    bufUsed = in.read(buf);
    bufPos = 0;

    if (bufUsed <= 0) {
      // Read past end of input.
      in.close();
      in = null;

    } else if (bufUsed < buf.length) {
      // Getting back fewer bytes than requested also means end of input,
      // but we wait for the next read before closing the file.
    }
  }

  public void setStripCR(boolean stripCR) {
    this.stripCR = stripCR;
  }

}
