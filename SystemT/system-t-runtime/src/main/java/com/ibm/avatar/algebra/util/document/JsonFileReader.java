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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

/** A class for reading JSON files. */
public class JsonFileReader {
  /** contents of the JSON file */
  private BufferedReader in;

  /** the name and line number of the JSON file we are reading, for error messages */
  private String fileName;
  private int lineNum;

  /** object mapper used to parse JSON from string */
  private ObjectMapper mapper =
      JsonMapper.builder().enable(JsonReadFeature.ALLOW_TRAILING_COMMA).build();

  /** the current line being parsed -- only keep one line in memory at a time */
  private String currentLine;

  /**
   * Constructor -- sets up the JSON file for parsing JSON file must be in the following format:
   * 
   * <pre>
   * JSON file must be in the following format ( all on the same line, spread out for readability) :
   * 
   * { 
   *    “doccol1”: “123”, 
   *    “doccol2”: “Lorem ipsum”, 
   *    “ExternalViews": 
   *      { 
   *        “moduleName.NameExt”: 
   *        [ 
   *          { 
   *            “field1”:”value1”, 
   *            “field2”:”value2” 
   *          }, 
   *          { 
   *            “field1”:”value3”,
   *            “field2”:”value4”
   *          } 
   *        ],
   *        "otherExternalViewsHere": 
   *        [
   *          <other external view fields and values>
   *        ]
   *      }
   * }  // this ends the first line
   * { <document and external view pair> }       // 2nd line, same format as first line
   * { <document and external view pair> }       // 3rd line, same format first line
   * </pre>
   * 
   * Having the file as a JSONArray of JSONRecords, one per line, will throw a parse error.
   * 
   * @param jsonFile input file in JSON format. Each line consists of one JSONRecord, one per line.
   *        Having a comma at the end of a line is not expected but will be accepted.
   * @throws IOException
   */
  public JsonFileReader(File jsonFile) throws IOException {
    in = new BufferedReader(new InputStreamReader(new FileInputStream(jsonFile), "UTF-8"));

    fileName = jsonFile.getName();
    lineNum = 1;

    currentLine = in.readLine();

    // exit gracefully if the file is empty
    if (currentLine == null) {
      in.close();
      in = null;
    }
  }

  /**
   * Parse the next document record from the current line in memory -- this line is set in the
   * initializer and also at the end of this function. We need to load the next line immediately
   * after getting a doc Tuple so we can identify whether the reader is done. This line is expected
   * to be a JSONRecord.
   * 
   * @return an ObjectNode containing the next document record. This includes the tuple + all
   *         external views.
   */
  public JsonNode getNextTup() throws IOException {

    try {

      // shouldn't be necessary but just in case
      if (currentLine == null) {
        in = null;
      }
      // skip current line if current line does not contain any JSON content
      else if (currentLine.trim().length() == 0) {
        // load next tuple
        readNextLine();

        // attempt to parse & return the tuple we read above
        return getNextTup();
      }

      if (endOfInput()) {
        return null;
      }

      // parse current line into ObjectNode
      JsonNode record = mapper.readTree(currentLine);

      // we need to load the next line now, so that we can check for end of input and let the reader
      // know it's done reading tuples
      readNextLine();

      // parse the old line for a JSON record
      // only one record expected per line, containing both doc tuple and external view data
      return record;
    } catch (Exception e) {
      throw new IOException(
          makeErrorHeader() + "Could not parse this line as a JSON record : \n" + currentLine);
    }

  }

  /**
   * Checks to see if there is another document and if so, loads it into memory. If not, mark this
   * reader as having reached end of input.
   * 
   * @return whether we have another document tuple to parse
   */
  private void readNextLine() throws IOException {
    if (in != null) {

      try {

        lineNum++;

        if ((currentLine = in.readLine()) != null) {
          return;
        } else {
          in.close();
          in = null;
          return;
        }
      } catch (IOException e) {
        throw new IOException(makeErrorHeader() + e.getMessage());
      }
    } else {

      // we've already finished processing this document
      return;
    }
  }

  /**
   * @return true if we have reached the end of the input file
   */
  public boolean endOfInput() {
    if (in == null) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * Utility method to print out file name and line number for clear error messages
   * 
   * @return
   */
  public String makeErrorHeader() {
    return String.format("file '%s', line %d: ", fileName, lineNum);
  }

}
