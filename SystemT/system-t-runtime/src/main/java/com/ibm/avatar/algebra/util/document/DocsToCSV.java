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

/** Driver program for converting documents into CSV format. */
public class DocsToCSV {

  private static final String USAGE =
      String.format("Usage: java %s [infile] [outfile.zip]", DocsToCSV.class);

  public static void main(String[] args) throws Exception {

    if (args.length != 2) {
      System.err.print(USAGE + "\n");
      return;
    }

    String infileName = args[0];
    String zipFileName = args[1];

    DocUtils.toCSV(new File(infileName), new File(zipFileName));
  }
}
