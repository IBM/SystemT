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
package com.ibm.avatar.algebra.test.stable;

import java.io.File;
import java.util.ArrayList;

import org.junit.Assert;
import org.junit.Test;

import com.ibm.avatar.algebra.util.file.FileOperations;
import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.algebra.util.tokenize.TokenizerConfig;
import com.ibm.avatar.api.CompileAQL;
import com.ibm.avatar.api.CompileAQLParams;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.ExternalTypeInfo;
import com.ibm.avatar.api.ExternalTypeInfoFactory;
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.api.exceptions.CompilerException;
import com.ibm.avatar.api.tam.ModuleMetadataFactory;
import com.ibm.avatar.logging.Log;

/**
 * Tests to verify local file operations <br/>
 * HDFS and GPFS tests need to be run manually
 * 
 */
public class FileOperationsTests extends RuntimeTestHarness {
  public static void main(String[] args) {
    try {

      FileOperationsTests t = new FileOperationsTests();

      // t.setUp ();

      long startMS = System.currentTimeMillis();

      t.localAbsoluteURITest();

      long endMS = System.currentTimeMillis();

      // t.tearDown ();

      double elapsedSec = ((double) (endMS - startMS)) / 1000.0;

      System.err.printf("Test took %1.3f sec.\n", elapsedSec);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /* The simple AQL loaded from filesystem */
  public final String className = getClass().getSimpleName();
  public final File AQL_MODULE_DIR =
      new File(String.format("%s/%s", TestConstants.AQL_DIR, className));

  /* A compiled dictionary to load from filesystem */
  public final String DICT_NAME = "strictFirst";
  public final File TEST_DICT = new File(AQL_MODULE_DIR, DICT_NAME + ".dict.cd");

  /* An external table to load from filesystem */
  public final String TABLE_NAME = "externalTable";
  public final File TEST_TABLE = new File(AQL_MODULE_DIR, TABLE_NAME + ".csv");

  // environment variable that indicates if a DFS is installed
  public final String HADOOP_CONF_DIR = System.getenv("HADOOP_CONF_DIR");

  /* Default tokenizer for all tests in the class */
  private final TokenizerConfig tokenizerCfg = new TokenizerConfig.Standard();

  /**
   * Tests a fully-valid URI to local filesystem (e.g., file://path/to/tam)
   * 
   * @throws Exception
   */
  @Test
  public void localFullURITest() throws Exception {
    startTest();

    String[] moduleNames = new String[] {"personPhone"};

    // URI to test -- full URI
    String fullURIString = makeFullURI(AQL_MODULE_DIR);
    String fullOutputURI = makeFullURI(getCurOutputDir());
    String fullDictURI = makeFullURI(TEST_DICT);
    String fullTableURI = makeFullURI(TEST_TABLE);

    genericURIFormatTest(moduleNames, fullURIString, fullOutputURI, fullDictURI, fullTableURI);

    endTest();
  }

  /**
   * Tests an absolute URI to local filesystem without scheme (e.g., /path/to/tam) <br/>
   * Don't run this test on a platform with a DFS installed since this tests local only
   * 
   * @throws Exception
   */
  @Test
  public void localAbsoluteURITest() throws Exception {
    startTest();

    String[] moduleNames = new String[] {"personPhone"};

    // URI to test -- absolute without scheme (substring (5) chops off the file:// scheme)
    String absoluteURIString = makeAbsoluteURI(AQL_MODULE_DIR);
    String absoluteOutputURI = makeAbsoluteURI(getCurOutputDir());
    String absoluteDictURI = makeAbsoluteURI(TEST_DICT);
    String absoluteTableURI = makeAbsoluteURI(TEST_TABLE);

    if (HADOOP_CONF_DIR == null) {
      genericURIFormatTest(moduleNames, absoluteURIString, absoluteOutputURI, absoluteDictURI,
          absoluteTableURI);
    } else {
      Log.info("Distributed file system detected; bypassing test " + curPrefix);
    }
    endTest();
  }

  /**
   * Tests a relative URI to local filesystem without scheme (e.g., ./path/to/tam) <br/>
   * Don't run this test on a platform with a DFS installed since this tests local only
   * 
   * @throws Exception
   */
  @Test
  public void localRelativeURITest() throws Exception {
    startTest();

    String[] moduleNames = new String[] {"personPhone"};

    // URI to test -- relative
    String relativeURIString = makeRelativeURI(AQL_MODULE_DIR);
    String relativeOutputURI = makeRelativeURI(getCurOutputDir());
    String relativeDictURI = makeRelativeURI(TEST_DICT);
    String relativeTableURI = makeRelativeURI(TEST_TABLE);

    if (HADOOP_CONF_DIR == null) {
      genericURIFormatTest(moduleNames, relativeURIString, relativeOutputURI, relativeDictURI,
          relativeTableURI);
    } else {
      Log.info("Distributed file system detected; bypassing test " + curPrefix);
    }

    endTest();
  }

  /**
   * Test case for defect : CompileAQL APIs cannot handle some paths with spaces
   * 
   * @throws Exception
   */
  @Test
  public void pathWithSpaceTest() throws Exception {

    try {
      // Test for case (b) FileOperations.createURI not able to handle directory names with spaces.
      String filename = "/dir with space/filename with space";
      Assert.assertEquals("Pathname with space did not resolve to proper URI.",
          FileUtils.createValidatedFile(filename).toURI().toString(),
          FileOperations.resolvePathToLocal(filename));
    } catch (Exception e) {
      Assert.fail(String.format("No exceptions expected, but received :%s", e.getMessage()));
    }

  }

  /*
   * ADD NEW TEST CASES ABOVE
   */

  /*
   * UTILITY METHODS BELOW
   */

  /**
   * Generic method that tests API calls that use URI strings:<br/>
   * {@link CompileAQLParams} (<b>inputModules</b>, <b>outputURI</b>, <b>modulePath</b>,
   * tokenizerCfg)} <br/>
   * {@link OperatorGraph#createOG} (moduleNames, <b>modulePath</b>, info, tokenizerCfg)}<br/>
   * {@link OperatorGraph#validateOG} (moduleNames, <b>modulePath</b>, tokenizerCfg)<br/>
   * {@link ExternalTypeInfo#addDictionary} (dictionary, <b>dictionaryFileURI</b>)<br/>
   * {@link ExternalTypeInfo#addTable} (table, <b>tableFileURI</b>)<br/>
   * {@link ModuleMetadataFactory#readMetaData} (moduleName, <b>modulePath</b>)<br/>
   * 
   * @param moduleNames the names of the modules to compile
   * @param testFormatURI URI string to the module path of the imported modules (which also contains
   *        the modules to be compiled)
   * @param testOutputURI URI string to the directory containing the TAM output from compilation
   * @param testDictURI URI string to an external dictionary declared by the compiled module
   * @param testTableURI URI string to an external table declared by the compiled module
   * @throws Exception
   */
  private void genericURIFormatTest(String[] moduleNames, String testFormatURI,
      String testOutputURI, String testDictURI, String testTableURI) {
    try {

      Log.info("Testing URI string: %s\nOutput URI: %s\nDictionary URI: %s", testFormatURI,
          testOutputURI, testDictURI);

      // set up the directories containing the input modules we want to compile
      ArrayList<String> moduleDirectories = new ArrayList<String>();

      for (String moduleName : moduleNames) {
        String moduleDirectoryName = String.format("%s/%s", testFormatURI, moduleName);

        moduleDirectories.add(moduleDirectoryName);
      }

      // compile module using a specific format
      // format is used in inputModules, outputURI, AND modulePath
      // defect : add an extra semicolon at end of module path and make sure it's handled properly
      CompileAQLParams params =
          new CompileAQLParams((String[]) moduleDirectories.toArray(new String[1]), testOutputURI,
              testFormatURI + Constants.MODULEPATH_SEP_CHAR, tokenizerCfg);

      CompileAQL.compile(params);

      // add the directory of the just-compiled TAM to the module path for subsequent API tests
      // also, add a trailing semicolon to end of module path to make sure it's handled properly
      String modulePath = testFormatURI + Constants.MODULEPATH_SEP_CHAR + testOutputURI
          + Constants.MODULEPATH_SEP_CHAR;

      // check if ETI can handle dict and table URIs in test format
      ExternalTypeInfo eti = ExternalTypeInfoFactory.createInstance();
      eti.addDictionary(moduleNames[0] + Constants.MODULE_ELEMENT_SEPARATOR + DICT_NAME,
          testDictURI);
      eti.addTable(moduleNames[0] + Constants.MODULE_ELEMENT_SEPARATOR + TABLE_NAME, testTableURI);

      // load module with module path and external dict in test format
      OperatorGraph.createOG(moduleNames, modulePath, eti, tokenizerCfg);

      // validate module with module path in test format
      OperatorGraph.validateOG(moduleNames, modulePath, tokenizerCfg);

      // check if module metadata can be read using a module path in test format
      ModuleMetadataFactory.readMetaData(moduleNames[0], modulePath);

    } catch (Exception e) {
      e.printStackTrace();

      if (e instanceof CompilerException) {
        // CompilerException --> Break out the individual errors and dump to STDERR
        CompilerException ce = (CompilerException) e;
        System.err.printf("Caught CompilerException; dumping stack traces.\n");
        for (Exception error : ce.getSortedCompileErrors()) {
          error.printStackTrace();
        }
      }

      Assert.fail(String.format("No exceptions expected, but received :%s", e.getMessage()));
    }
  }

  /**
   * Helper method to construct a full URI from a File object
   * 
   * @throws Exception
   */
  private String makeFullURI(File file) throws Exception {
    return file.getCanonicalFile().toURI().toString();
  }

  /**
   * Helper method to construct an absolute URI from a File object
   */
  private String makeAbsoluteURI(File file) throws Exception {

    // strip the scheme from a canonical URI
    String absoluteURI = file.getCanonicalFile().getPath();

    return absoluteURI;
  }

  /**
   * Helper method to construct a relative URI from a File object
   * 
   * @return
   */
  private String makeRelativeURI(File file) {

    // for Windows tests -- toString() preserves \ in directory structure, but URI strings cannot
    // contain \
    return file.toString().replace('\\', '/');
  }
}
