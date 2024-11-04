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

package com.ibm.avatar.api;

import java.io.File;
import java.util.Map;

import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;

/**
 * This class provides APIs that we don't want to expose to the public, but are needed for
 * consumption by internal classes in other packages such as the test harness. Do not put this class
 * in the Javadoc build target.
 * 
 */

public class DocReaderInternal extends DocReader {

  public DocReaderInternal(File docFile) throws TextAnalyticsException {
    super(docFile);
  }

  public DocReaderInternal(File docFile, TupleSchema docSchema,
      Map<Pair<String, String>, TupleSchema> extNameVsSchema) throws TextAnalyticsException {
    super(docFile, docSchema, extNameVsSchema, Constants.DEFAULT_CSV_FIELD_SEPARATOR);
  }

  /**
   * Indicate that the input data collection contains text files in DOS format (i.e., a carriage
   * return character followed by a line feed character). <br>
   * Used by internal test suite to make annotations consistent across Windows and Linux platform.
   * 
   * @param isDosFormat
   */
  public void stripCR(boolean isDosFormat) {
    if (scan != null) {
      scan.setStripCR(isDosFormat);
    }
  }

}
