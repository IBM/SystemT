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
 * Tests that extract information from the Spock data set. (Currently empty)
 */
public class AQLSpockTests extends RuntimeTestHarness {

  public static void main(String[] args) {
    try {

      AQLSpockTests t = new AQLSpockTests();

      t.setUp();

      long startMS = System.currentTimeMillis();

      // t.tarScanTest();

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
  @SuppressWarnings("unused")
  // private DocScan scan = null;
  private final File defaultDocsFile = new File(TestConstants.SPOCK_TARFILE);

  @Before
  public void setUp() throws Exception {

    // Renice the current thread to avoid locking up the entire system.
    Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

    // scan = new DerbyDocScan(EnronTests.dbname, EnronTests.quickQuery);

    // scan = DocScan.makeFileScan(new File(TestConstants.SPOCK_TARFILE));

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

  /*
   * Dummy test required to avoid failure of ant junit task testSystemTRuntime due to
   * "no runnable methods". Remove the test when the class contains at least one "real" test.
   */
  @Test
  public void dummyTest() throws Exception {
    startTest();
  }

}
