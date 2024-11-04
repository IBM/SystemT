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
package com.ibm.avatar.provenance.experiments;

import java.io.File;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;

/**
 * Tests for the new build process after the reorganization of annotators. Laura, April 21, 2009
 */
public class AQLTestAnnotators extends RuntimeTestHarness {

  /** Directory containing both the input documents and expected output HTML */
  // public static final String DATA_DIR = "./../Runtime";
  // public static final String DATA_FILE = DATA_DIR + TestConstants.ENRON_10K_DUMP;
  // public static final String DATA_FILE = DATA_DIR + TestConstants.ENRON_1K_DUMP;
  // public static final String DATA_FILE = DATA_DIR + TestConstants.SPOCK_DUMP;

  // public static final String DATA_DIR = "resources/data/src/ReutersRCV1/converted-txt-format";
  // public static final String DATA_FILE = DATA_DIR + "/RCV1MergerAcquisition.zip";

  // public static final String DATA_FILE = DATA_DIR + "/SIMergerAcquisition.zip";
  // public static final String DATA_FILE = DATA_DIR + "/SIJointVenture.zip";
  // public static final String DATA_FILE = DATA_DIR + "/SIAlliance.zip";

  // private static final String DATA_DIR =
  // "resources/data/src/EnronGoldStandard/Enron-244/enron-244-data";

  // private static final String DATA_DIR =
  // "resources/data/src/EnronGoldStandard/EnronMeetings/EnronMeetingsDocTrainBunch1";
  // private static final String DATA_DIR =
  // "resources/data/src/EnronGoldStandard/EnronMeetings/EnronMeetingsDocTrainBunch2";
  // private static final String DATA_DIR =
  // "resources/data/src/EnronGoldStandard/EnronMeetings/EnronMeetingsDocTrainBunch3";

  // private static final String DATA_DIR=
  // "testdata/docs/aqlRefineTest/personSelfLabel-training/data";
  // private static final String DATA_DIR= "testdata/docs/aqlRefineTest/personSelfLabel-test/data";
  // private static final String DATA_DIR=
  // "testdata/docs/aqlRefineTest/PersonPhoneEnronGS/train/data";
  private static final String DATA_DIR = "testdata/docs/aqlRefineTest/PersonPhoneEnronGS/test/data";

  /** Directories where output files from tests in this class go. */
  public static final String OUTPUT_DIR = "temp/aqltestout";

  // For person14.aql
  /*
   * public static final String QUERY_DIR = "testdata/aql/refineWebTests"; public static final
   * String INCLUDE_DIR = "testdata/aql/refineWebTests"; public static final String DICTIONARY_DIR =
   * "testdata/aql/refineWebTests/dictionaries"; public static final String UDF_DIR =
   * "testdata/aql/refineWebTests/dictionaries";
   */

  // For personPhoneCandidates.aql
  public static final String QUERY_DIR = "testdata/aql/refineWebTests/personPhone";
  public static final String INCLUDE_DIR = "testdata/aql/refineWebTests/personPhone";
  public static final String DICTIONARY_DIR =
      "testdata/aql/refineWebTests/personPhone/GenericNE/dictionaries";
  public static final String UDF_DIR = "testdata/aql/refineWebTests/personPhone/GenericNE/udfjars";

  /*
   * public static final String QUERY_DIR = "Annotators"; public static final String INCLUDE_DIR =
   * "Annotators/neEvaluation"; public static final String DICTIONARY_DIR =
   * "Annotators/neEvaluation/GenericNE/dictionaries"; public static final String UDF_DIR =
   * "Annotators/neEvaluation/GenericNE/udfjars";
   */

  public static void main(String[] args) {
    try {

      AQLTestAnnotators t = new AQLTestAnnotators();

      t.setUp();

      long startMS = System.currentTimeMillis();

      // t.person14Test();
      t.personPhoneCandTest();

      long endMS = System.currentTimeMillis();

      t.tearDown();

      double elapsedSec = ((double) (endMS - startMS)) / 1000.0;

      System.err.printf("Test took %1.3f sec.\n", elapsedSec);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Scan over the Enron database; set up by setUp() and cleaned out by tearDown()
   */
  // private DocScan scan = null;
  private File defaultDocsFile = new File(DATA_DIR);

  @Before
  public void setUp() throws Exception {

    // Log.enableAllMsgTypes();

    // Renice the current thread to avoid locking up the entire system.
    Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

    /***** CHANGE TYPE OF INPUT HERE ************/
    // scan = new DBDumpFileScan(DATA_FILE);
    // scan = new ZipFileScan(DATA_FILE);
    // scan = new DirDocScan(new File(DATA_DIR));
    defaultDocsFile = new File(DATA_DIR);
    // scan = new TarFileScan(DATA_FILE);

    // Put default output directories into place.
    // Individual tests may override these choices.

    this.setPrintTups(true);
    this.setDisableOutput(false);
    this.setDataPath(DICTIONARY_DIR, INCLUDE_DIR, UDF_DIR);

  }

  @After
  public void tearDown() {
    // scan = null;

    // Deregister the Derby driver so that other tests can connect to other
    // databases. This is probably not necessary (we don't currently use
    // derby), but we do it just in case.
    try {
      DriverManager.getConnection("jdbc:derby:;shutdown=true");
    } catch (SQLException e) {
      // The shutdown command always raises a SQLException
      // See http://db.apache.org/derby/docs/10.2/devguide/
    }
    System.gc();
  }

  /**
   * Test for Person14 annotator
   */
  @Test
  public void person14Test() throws Exception {
    startTest();

    final String AQL_FILE_NAME = QUERY_DIR + "/person14.aql";

    runNonModularAQLTest(defaultDocsFile, new File(AQL_FILE_NAME));
  }

  /**
   * Test for Person14 annotator
   */
  @Test
  public void personPhoneCandTest() throws Exception {

    startTest();

    final String AQL_FILE_NAME = QUERY_DIR + "/personphonecandidates.aql";

    runNonModularAQLTest(defaultDocsFile, new File(AQL_FILE_NAME));
  }

}
