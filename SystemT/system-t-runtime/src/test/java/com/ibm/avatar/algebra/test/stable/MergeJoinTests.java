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
import java.sql.DriverManager;
import java.sql.SQLException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;

/**
 * Various tests of general sort-merge join.
 * 
 */
public class MergeJoinTests extends RuntimeTestHarness {

  public static final String AOG_FILES_DIR = TestConstants.AOG_DIR + "/mergeJoinTests";

  public static void main(String[] args) {
    try {

      MergeJoinTests t = new MergeJoinTests();

      t.setUp();

      long startMS = System.currentTimeMillis();

      t.containsTest();

      long endMS = System.currentTimeMillis();

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
  private File defaultDocsFile = null;

  @Before
  public void setUp() throws Exception {

    // Renice the current thread to avoid locking up the entire system.
    Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

    // scan = new DBDumpFileScan(TestConstants.ENRON_10K_DUMP);
    // scan = new DBDumpFileScan(TestConstants.ENRON_1K_DUMP);
    defaultDocsFile = new File(TestConstants.ENRON_1K_DUMP);
    // scan = new DBDumpFileScan("testdata/tmp.del");

    setPrintTups(false);
    // util.setGenerateHTML(true);
  }

  @After
  public void tearDown() {
    // scan = null;

    // Deregister the Derby driver so that other tests can connect to other
    // databases.
    try {
      DriverManager.getConnection("jdbc:derby:;shutdown=true");
    } catch (SQLException e) {
      // The shutdown command always raises a SQLException
      // See http://db.apache.org/derby/docs/10.2/devguide/
    }
    System.gc();
  }

  /**
   * Test of the Contains and ContainedWithin join predicates with merge join. Looks for two words
   * separated by a period that are in the same sentence.
   */
  @Test
  public void containsTest() throws Exception {
    startTest();

    String filename = AOG_FILES_DIR + "/contains.aog";

    // setPrintTups(true);
    setWriteCharsetInfo(true);
    runAOGFile(defaultDocsFile, new File(filename), null);

    System.err.printf("Comparing output files.\n");

    // util.truncateExpectedFiles();
    truncateOutputFiles(true);
    compareAgainstExpected(true);
  }

  /**
   * Test of the FollowsTok and FollowedByTok join predicates with merge join. Looks for pairs of
   * potential first names and last names within 5-10 tokens of each other.
   */
  @Test
  public void followsTokTest() throws Exception {
    startTest();

    String filename = AOG_FILES_DIR + "/followstok.aog";

    String[][] dictInfo = {{"dictionaries/first.dict", "testdata/dictionaries/first.dict"},
        {"dictionaries/last.dict", "testdata/dictionaries/last.dict"},};

    setWriteCharsetInfo(true);
    runAOGFile(defaultDocsFile, new File(filename), dictInfo);

    System.err.printf("Comparing output files.\n");
    // util.truncateExpectedFiles();
    truncateOutputFiles(true);
    compareAgainstExpected(true);
  }

  /**
   * Benchmark of the FollowsTok predicate, for profiling purposes.
   */
  @Test
  public void followsTokBench() throws Exception {
    startTest();

    String filename = AOG_FILES_DIR + "/followstokBench.aog";

    // scan = DocScan.makeFileScan(new File(TestConstants.ENRON_SMALL_ZIP));

    setDisableOutput(true);

    String[][] dictInfo = {{"dictionaries/first.dict", "testdata/dictionaries/first.dict"},
        {"dictionaries/last.dict", "testdata/dictionaries/last.dict"},};

    runAOGFile(new File(TestConstants.ENRON_SMALL_ZIP), new File(filename), dictInfo);
  }

  /**
   * Test of the Follows and FollowedBy join predicates with merge join. Looks for pairs of
   * potential first and last names within 10-50 chars of each other.
   */
  @Test
  public void followsTest() throws Exception {
    startTest();

    String filename = AOG_FILES_DIR + "/follows.aog";

    String[][] dictInfo = {{"dictionaries/first.dict", "testdata/dictionaries/first.dict"},
        {"dictionaries/last.dict", "testdata/dictionaries/last.dict"},};

    // setPrintTups(true);

    setWriteCharsetInfo(true);
    runAOGFile(defaultDocsFile, new File(filename), dictInfo);

    System.err.printf("Comparing output files.\n");
    // util.truncateExpectedFiles();
    truncateOutputFiles(false);
    compareAgainstExpected(true);
  }

  /**
   * Test of the Overlaps join predicates with merge join. Looks for city names that overlap with
   * first names.
   */
  @Test
  public void overlapsTest() throws Exception {
    startTest();

    String filename = AOG_FILES_DIR + "/overlaps.aog";

    String[][] dictInfo = {{"dictionaries/first.dict", "testdata/dictionaries/first.dict"},
        {"dictionaries/lotus/CITY.dict", "testdata/dictionaries/lotus/CITY.dict"},};

    // util.runTAMFile ("overlapsTest", moduleURI.toString ());
    setWriteCharsetInfo(true);
    runAOGFile(defaultDocsFile, new File(filename), dictInfo);

    // util.runAOGFile(scan, filename);

    System.err.printf("Comparing output files.\n");
    // util.truncateExpectedFiles();
    truncateOutputFiles(true);
    compareAgainstExpected(true);
  }

  /**
   * Test of the Equals merge join predicate.
   */
  @Test
  public void equalsTest() throws Exception {
    startTest();

    String filename = AOG_FILES_DIR + "/equals.aog";

    setPrintTups(true);

    String[][] dictInfo = {{"dictionaries/first.dict", "testdata/dictionaries/first.dict"},
        {"dictionaries/lotus/CITY.dict", "testdata/dictionaries/lotus/CITY.dict"},};

    setWriteCharsetInfo(true);
    runAOGFile(defaultDocsFile, new File(filename), dictInfo);

    System.err.printf("Comparing output files.\n");

    // util.truncateExpectedFiles();
    truncateOutputFiles(true);
    compareAgainstExpected(true);
  }

  /**
   * Test of the special-case join for FollowsTok with a distance of zero.
   */
  @Test
  public void adjacentTest() throws Exception {
    startTest();

    String filename = AOG_FILES_DIR + "/adjacent.aog";

    setPrintTups(true);

    String[][] dictInfo = {{"dictionaries/first.dict", "testdata/dictionaries/first.dict"},
        {"dictionaries/last.dict", "testdata/dictionaries/last.dict"},};

    setWriteCharsetInfo(true);
    runAOGFile(defaultDocsFile, new File(filename), dictInfo);

    System.err.printf("Comparing output files.\n");

    // util.truncateExpectedFiles();
    truncateOutputFiles(true);
    compareAgainstExpected(true);
  }

}
