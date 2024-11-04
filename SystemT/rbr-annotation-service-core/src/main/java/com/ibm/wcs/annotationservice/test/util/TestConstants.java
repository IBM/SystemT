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
package com.ibm.wcs.annotationservice.test.util;

import java.io.File;
import java.util.ArrayList;

import com.ibm.avatar.algebra.util.string.StringUtils;

/**
 * Various constants used by tests.
 * 
 */
public class TestConstants {

  /** Name of the system property that may hold the base directory for tests. */
  public static final String TEST_DIR_PROPNAME = "avatar.test.dir";

  public static final String TEST_WORKING_DIR;

  static {
    // Use the user-specified working directory if possible.
    if (null != System.getProperty(TEST_DIR_PROPNAME)) {
      TEST_WORKING_DIR = System.getProperty(TEST_DIR_PROPNAME);
    } else {
      TEST_WORKING_DIR = ".";
    }
  }

  /**
   * Directory containing all files (documents, and AQL code) for testing purposes.
   */
  public static final String TESTDATA_DIR = TEST_WORKING_DIR + "/src/test/resources";

  /** Directory containing AQL files for testing purposes. */
  public static final String AQL_DIR = TESTDATA_DIR + "/aql";

  /** Directory containing input document files for testing purposes. */
  public static final String TEST_DOCS_DIR = TESTDATA_DIR + "/docs";

  /** Directory containing annotator bundle configuration files for testing purposes. */
  public static final String TEST_CONFIGS_DIR = TESTDATA_DIR + "/configs";

  /**
   * Directory containing common input document files for testing purposes, common across multiple
   * tests.
   */
  public static final String DUMPS_DIR = TESTDATA_DIR + "/docs/common";

  /** Directory containing other resources necessary for testing purposes. */
  public static final String RESOURCES_DIR = TESTDATA_DIR + "/resources";

  /**
   * Name of the system property that may hold the compiled modules for the tests; used when running
   * the tests from ANT.
   */
  public static final String MODULES_DIR_PROPNAME = "modules.bin.dir";

  /** Location of the compiled top-level module, and any dependent modules. */
  public static String COMPILED_MODULES_PATH;

  static {
    // Use the user-specified entries if possible.
    if (null != System.getProperty(MODULES_DIR_PROPNAME)) {

      // Log.debug("%s: %s", MODULES_DIR_PROPNAME,
      // System.getProperty(MODULES_DIR_PROPNAME));
      String modulePathSep = ";";

      String[] modulePathList = System.getProperty(MODULES_DIR_PROPNAME).split(modulePathSep);
      ArrayList<String> modulePathURIList = new ArrayList<String>();

      for (String entry : modulePathList) {
        String entryURI = new File(entry).toURI().toString();
        // Log.debug("Entry: %s", entryURI);
        modulePathURIList.add(entryURI);
      }

      COMPILED_MODULES_PATH = StringUtils.join(modulePathURIList, modulePathSep);
      // Log.debug("CompileModulePath: %s", COMPILED_MODULES_PATH);

    } else {
      // Will compile source AQL
      COMPILED_MODULES_PATH = null;
    }
  }

  /**
   * Name of the system property that may hold the external resources (dictionaries and tables) for
   * the tests; used when running the tests from ANT.
   */
  public static final String EXTERNAL_RESOURCES_DIR_PROPNAME = "external.res.bin.dir";

  /**
   * Name of the special json file where we write instrumentation info output by the Annotation
   * Service
   */
  public static final String INFO_JSON_FILE_NAME = "instrumentationInfo.json";

  /**
   * Name of the special json file where we write annotations output by the Annotation Service
   */
  public static final String ANNOTATIONS_JSON_FILE_NAME = "annotations.json";

  /**
   * Extension expected for all json files; used to distinguish when comparing actual with expected
   * files: for .json files, use comparison as objects; for all other files, compare content as
   * strings
   */
  public static final String JSON_FILE_EXTENSION = ".json";

  /** Location of the external resources. */
  public static File EXTERNAL_RESOURCES_DIR;

  static {
    // Use the user-specified directory if possible.
    if (null != System.getProperty(EXTERNAL_RESOURCES_DIR_PROPNAME)) {
      EXTERNAL_RESOURCES_DIR = new File(System.getProperty(EXTERNAL_RESOURCES_DIR_PROPNAME));
    } else {
      EXTERNAL_RESOURCES_DIR = new File(TEST_WORKING_DIR, "src/main/resources/aql");
    }
  }

}
