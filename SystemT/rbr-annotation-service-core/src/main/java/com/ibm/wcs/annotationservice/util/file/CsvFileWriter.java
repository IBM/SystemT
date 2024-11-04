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
package com.ibm.wcs.annotationservice.util.file;

import com.opencsv.CSVWriter;
import com.ibm.avatar.algebra.datamodel.TupleSchema;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;

/**
 * Utility class to write external table entries to a file with header.
 *
 */
public class CsvFileWriter {
  public static void writeTable(ArrayList<ArrayList<String>> tuples, Writer writer,
      TupleSchema tableSchema) throws IOException {
    try (CSVWriter csvWriter = new CSVWriter(writer)) {
      // write header
      csvWriter.writeNext(tableSchema.getFieldNames(), false);

      // write contents
      for (ArrayList<String> line : tuples) {
        csvWriter.writeNext(line.toArray(new String[line.size()]), false);
      }
    }
  }
}
