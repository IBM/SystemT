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

import java.util.ArrayList;

import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.api.BatchAQLRunner;
import com.ibm.avatar.api.BatchAQLRunner.RunRecord;
import com.ibm.avatar.logging.Log;

/**
 * Various throughput and memory benchmarks that used to be spread among many different test cases.
 * These benchmarks are replicated here so that we can have a stable location to run them from.
 */
public class Benchmarks {

  // Location of the ensample dataset.
  public static final String ENSAMPLE_LOCATION = TestConstants.ENRON_SAMPLE_ZIP;

  // public static final String ENSAMPLE_LOCATION =
  // "/local/freiss/ensample.zip";

  public static void main(String[] args) throws Exception {

    // Enable some logging.
    Log.enableAllMsgTypes();

    Benchmarks b = new Benchmarks();

    b.eDiscovery_2way();
    // b.eDiscoveryPersonOrg_8way();

  }

  /** Run the entire eDiscovery named entity pipeline with 1 and 2 cores. */
  public void eDiscovery_2way() throws Exception {

    final String DOCSFILE_NAME = ENSAMPLE_LOCATION;
    final String AQLFILE_NAME = AQLEnronTests.EDISCOVERY_FULL_AQLFILE.getPath();
    final String DICT_PATH = AQLEnronTests.EDISCOVERY_DICT_PATH;

    final int[] NTHREADS = {2};

    genericScaleout(DOCSFILE_NAME, AQLFILE_NAME, DICT_PATH, NTHREADS);

  }

  /** Run the entire eDiscovery named entity pipeline with 1-8 cores. */
  public void eDiscovery_8way() throws Exception {

    final String DOCSFILE_NAME = ENSAMPLE_LOCATION;
    final String AQLFILE_NAME = AQLEnronTests.EDISCOVERY_FULL_AQLFILE.getPath();
    final String DICT_PATH = AQLEnronTests.EDISCOVERY_DICT_PATH;

    final int[] NTHREADS = {1, 2, 3, 4, 5, 6, 7, 8};
    // final int[] NTHREADS = { 8, 8, 8, 8, 8, 8, 8, 8 };

    genericScaleout(DOCSFILE_NAME, AQLFILE_NAME, DICT_PATH, NTHREADS);

  }

  /** Run the entire eDiscovery named entity pipeline with 1-16 cores. */
  public void eDiscovery_16way() throws Exception {

    final String DOCSFILE_NAME = ENSAMPLE_LOCATION;
    final String AQLFILE_NAME = AQLEnronTests.EDISCOVERY_FULL_AQLFILE.getPath();
    final String DICT_PATH = AQLEnronTests.EDISCOVERY_DICT_PATH;

    final int[] NTHREADS = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};

    genericScaleout(DOCSFILE_NAME, AQLFILE_NAME, DICT_PATH, NTHREADS);

  }

  /**
   * Generic method for running a series of scaleout tests.
   */
  private void genericScaleout(String docsFileName, String aqlFileName, String dictDirName,
      int[] nthreads) throws Exception {

    ArrayList<RunRecord> results = new ArrayList<RunRecord>();

    // Do the runs.
    for (int n : nthreads) {
      System.err.printf("Running %s with %d threads...\n", aqlFileName, n);
      RunRecord record = runScaleout(docsFileName, aqlFileName, dictDirName, n);

      results.add(record);
    }

    // Generate a report.
    System.err.printf("Results for %s over %s:\n", aqlFileName, docsFileName);
    for (RunRecord record : results) {

      double docsPerSec = (double) record.ndoc / record.sec;
      double bytesPerSec = (double) record.ndocBytes / record.sec;

      System.err.printf(
          "    %2d tds ==> %1.0f kb/sec " + "(%1.2f sec, %d bytes, %1.2f docs/sec) \n",
          record.nthreads, bytesPerSec / 1000.0, record.sec, record.ndocBytes, docsPerSec);

    }

  }

  /**
   * Generic method for running a single SMP scaleout test.
   */
  private RunRecord runScaleout(String docsFileName, String aqlFileName, String dictDirName,
      int nthreads) throws Exception {
    // For the moment, most of the work gets pushed down to BatchAQLRunner.
    BatchAQLRunner runner =
        new BatchAQLRunner(docsFileName, dictDirName, null, aqlFileName, nthreads);
    return runner.run();
  }

}
