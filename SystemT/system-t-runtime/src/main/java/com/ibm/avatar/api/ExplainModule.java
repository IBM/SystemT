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
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.tam.ModuleUtils;

/**
 * Describes the contents of a given compiled module in a human-readable format, which includes the
 * module metadata, and the operator graph.
 *
 */
public class ExplainModule {
  /**
   * Describes the contents of a compiled module, with output sent to a Writer. This version of
   * ExplainModule API is recommended for modules with large amounts of metadata and/or a large
   * operator graph.
   *
   * @param compiledModule the {@link java.io.File} object pointing to the compiled module
   *        file(.tam) to be explained
   * @param outputBuffer the character stream where the explanation of the module is written
   * @throws TextAnalyticsException if a problem is encountered when loading the metadata or
   *         operator graph
   */
  public static void explain(File compiledModule, Writer outputBuffer)
      throws TextAnalyticsException {
    try {
      String aog = null;
      String metadata = null;

      FileInputStream fis = new FileInputStream(compiledModule);
      JarInputStream jis = new JarInputStream(fis);
      JarEntry entry = null;
      try {
        while ((entry = jis.getNextJarEntry()) != null) {
          byte[] content = ModuleUtils.readCurrJarEntry(jis);
          if (entry.getName().equals("plan.aog")) {
            aog = new String(content);
          } else if (entry.getName().equals("metadata.xml")) {
            metadata = new String(content);
          }
        }

        // write metadata first so that it helps in file comparison during Junits (i.e skip first
        // two lines of metadata)
        outputBuffer.write(metadata);

        outputBuffer.write(System.getProperty("line.separator"));

        outputBuffer.write(aog);
      } finally {
        fis.close();
        jis.close();
      }
    } catch (IOException ioe) {
      throw new TextAnalyticsException(ioe);
    }
  }

  /**
   * Describes the contents of a compiled module, with output sent to a file. A convenient method
   * for sending the explanation of a module to a file.
   *
   * @param compiledModule the {@link java.io.File} object pointing to the compiled module
   *        file(.tam) to be explained
   * @param output the {@link java.io.File} object pointing to the file in the local filesystem
   *        where explanation of the module should be written.
   * @throws TextAnalyticsException if a problem is encountered when loading the metadata or
   *         operator graph
   */
  public static void explain(File compiledModule, File output) throws TextAnalyticsException {
    try {
      Writer writer = new FileWriter(output);
      try {
        explain(compiledModule, writer);
      } finally {
        writer.close();
      }
    } catch (IOException ioe) {
      throw new TextAnalyticsException(ioe);
    }
  }
}
