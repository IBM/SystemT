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
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.algebra.util.tokenize.TokenizerConfig;
import com.ibm.avatar.aql.tam.ModuleMetadataImpl;
import com.ibm.avatar.aql.tam.TAM;
import com.ibm.avatar.aql.tam.TAMSerializer;

/**
 * Driver program for invoking a SystemT annotator. Does not currently support modular AQLs
 * 
 */
public class SystemTDriver {

  public static final String USAGE = String.format(
      "Usage: java %s -q aql_or_aog_file -f docs_file/dir -", SystemTDriver.class.getName());

  /**
   * @param args
   */
  public static void main(String[] args) throws Exception {

    // Process arguments.
    Map<String, String> argsMap = processArgs(args);

    String annotFileStr = argsMap.get("-q");
    String docsFileStr = argsMap.get("-f");

    if (null == annotFileStr || null == docsFileStr) {
      System.err.printf("%s\n", USAGE);
      return;
    }

    File annotFile = new File(annotFileStr);
    File docsFile = new File(docsFileStr);

    // TODO: Check that input files exist.

    // Compile the annotator if necessary.
    String aog;
    TAM tam;

    // FIXME: Pass a valid outputURI once this class is upgraded to support modular AQLs
    URI outputURI = annotFile.getParentFile().toURI();
    final String moduleName = Constants.GENERIC_MODULE_NAME;

    if (annotFile.getName().matches(".*\\.aql")) {
      CompileAQLParams params =
          new CompileAQLParams(annotFile, outputURI.toString(), annotFile.getParent());
      CompileAQL.compile(params);

      // Load tam
      // FIXME: Pass a valid module name once this class is upgraded to support modular AQLs
      tam = TAMSerializer.load(moduleName, params.getOutputURI());

      aog = tam.getAog();
    } else if (annotFile.getName().matches(".*\\.aog")) {
      aog = FileUtils.fileToStr(annotFile, "UTF-8");

      // Generate a TAM file.
      tam = new TAM(moduleName);

      tam.setAog(aog);
    } else {
      System.err.printf("Don't know how to process file '%s'\n", annotFile);
      return;
    }

    // Open up a scan over the documents, and fetch information about their
    // schema.
    DocReader docs = new DocReader(docsFile);

    // create dummy empty meta data object; this is to make loader happy
    ModuleMetadataImpl dummyMD = ModuleMetadataImpl.createEmptyMDInstance();

    // we compile dictionaries from old testcases using the built-in whitespace tokenizer
    TokenizerConfig tokenizerCfg = new TokenizerConfig.Standard();
    dummyMD.setTokenizerType(tokenizerCfg.getName());
    tam.setMetadata(dummyMD);

    TAMSerializer.serialize(tam, outputURI);

    // Instantiate the resulting operator graph.
    String modulePath = outputURI.toString();
    System.err.printf("Using module path '%s'\n", modulePath);

    OperatorGraph og = OperatorGraph.createOG(new String[] {moduleName}, modulePath, null, null);

    long startMs = System.currentTimeMillis();

    // Process the documents one at a time.
    int ndoc = 0;
    while (docs.hasNext()) {
      Tuple doc = docs.next();

      // Annotate the current document, generating every single output
      // type that the annotator produces.
      og.execute(doc, null, null);
      ndoc++;

      // Don't do anything with the output, but generate periodic progress
      // indicators.
      if (0 == ndoc % 1000) {
        long elapsedMs = System.currentTimeMillis() - startMs;
        double elapsedSec = (double) elapsedMs / 1000.0;
        System.err.printf("Processed %d documents in %1.1f sec\n", ndoc, elapsedSec);
      }
    }

    // Close the document reader
    docs.remove();

    long elapsedMs = System.currentTimeMillis() - startMs;
    double elapsedSec = (double) elapsedMs / 1000.0;
    System.err.printf("Processed %d documents in %1.1f sec\n", ndoc, elapsedSec);

  }

  private static Map<String, String> processArgs(String[] args) {

    HashMap<String, String> ret = new HashMap<String, String>();
    for (int argIx = 0; argIx < args.length / 2; argIx++) {
      String flag = args[argIx * 2];
      String value = args[argIx * 2 + 1];
      ret.put(flag, value);
    }

    return ret;
  }

}
