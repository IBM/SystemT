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
 * Tests to ensure that the Lotus build has been correctly instantiated.
 */
public class LotusBuildTests extends RuntimeTestHarness {

  public static void main(String[] args) {
    try {
      LotusBuildTests t = new LotusBuildTests();

      t.setUp();

      long startMS = System.currentTimeMillis();

      t.namedEntityTest();

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
  private File defaultDocsFile = null;

  @Before
  public void setUp() throws Exception {

    // Renice the current thread to avoid locking up the entire system.
    Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

    // scan = new DerbyDocScan(EnronTests.dbname, EnronTests.quickQuery);

    // System.err.printf("Loading file '%s'\n",
    // TestConstants.ENRON_10K_DUMP);
    // scan = new DBDumpFileScan(TestConstants.ENRON_1K_DUMP);
    defaultDocsFile = new File(TestConstants.ENRON_1K_DUMP);

    // scan = new DBDumpFileScan(TestConstants.ENRON_10K_DUMP);
    // scan = new DBDumpFileScan(TestConstants.TEST_DOCS_DIR + "/tmp.del");
    // scan = new DBDumpFileScan(TestConstants.SPOCK_DUMP);

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
   * Run the packed named-entity annotators prepared by the build script.
   * 
   * @throws Exception
   */
  @Test
  public void namedEntityTest() throws Exception {

    startTest();
    File aogfile = new File(TestConstants.TEST_WORKING_DIR + "/lotus/tmp/aog/namedentity.aog");

    System.err.println(aogfile.getAbsolutePath());

    if (false == aogfile.exists()) {
      // SPECIAL CASE: Don't have the input file around.
      System.err.printf("%s doesn't exist; exiting test.\n", aogfile);
      return;
      // END SPECIAL CASE
    }

    // Disable strength reduction for now.
    // com.ibm.avatar.algebra.function.RegexConst.STRENGTH_REDUCE = false;

    // TODO: Commenting the following line; this should be replaced by call to run tam.
    // runAOGFile(defaultDocsFile, aogfile.getPath(), "UTF-16");
    runAOGFile(defaultDocsFile, aogfile, null);

    // util.setSkipFileComparison(true);
    compareAgainstExpected(true);
  }

}
