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
import java.io.FileOutputStream;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.util.file.SearchPath;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.DocReader;
import com.ibm.avatar.aql.tam.TAMSerializer;

/**
 * Tests of the "chunking" mechanism that
 * 
 */
public class ChunkingTests extends RuntimeTestHarness {
  public static final String AQL_FILES_DIR = TestConstants.AQL_DIR + "/chunkingTests";

  /**
   * Name of the file with "big" documents that test generate in order to trigger chunking.
   */
  private static final String DOCS_FILE_NAME = "bigDocs.zip";

  /** Main method for convenience */
  public static void main(String[] args) {
    try {

      ChunkingTests t = new ChunkingTests();

      t.setUp();

      long startMS = System.currentTimeMillis();

      t.chunkInfoTest();

      long endMS = System.currentTimeMillis();

      t.tearDown();

      double elapsedSec = ((double) (endMS - startMS)) / 1000.0;

      System.err.printf("Test took %1.3f sec.\n", elapsedSec);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() {

  }

  /**
   * Test case that loads the named entity annotators with and without chunking and compares the
   * results. Requires access to the "Annotators" project.
   * 
   * @throws Exception
   */
  @Test
  public void namedEntityTest() throws Exception {
    startTest();
    if (false == SpeedTests.ANNOTATORS_PROJECT.exists()) {
      // SPECIAL CASE: Don't bother running the test if the user hasn't
      // checked out the "Annotators" project into his or her workspace.
      System.err.printf("Skipping test because Annotators project" + " is not present at %s.\n",
          SpeedTests.ANNOTATORS_PROJECT);
      return;
      // END SPECIAL CASE
    }

    String dataPath = SpeedTests.EDISCOVERY_DICT_PATH + SearchPath.PATH_SEP_CHAR
        + SpeedTests.EDISCOVERY_INCLUDE_PATH;

    // Compile the eDA version of the named entity annotators.
    compileAQL(SpeedTests.EDISCOVERY_AQLFILE, dataPath);

    // Load tam
    TAMSerializer.load(Constants.GENERIC_MODULE_NAME, getCurOutputDir().toURI().toString());

    // String aog = tam.getAog ();

    // Generate some moderately large documents
    createBigDocs(TestConstants.ENRON_SMALL_ZIP, 10);

    // TODO: Run the annotators

    endTest();
  }

  /**
   * Test of solutions for getting at chunking information. These solutions take advantage of the
   * fact that the chunks are actually spans over the original document.
   */
  @Test
  public void chunkInfoTest() throws Exception {
    startTest();

    // Generate some really big documents.
    System.err.printf("Generating artificial big documents...\n");
    createBigDocs(TestConstants.ENRON_SMALL_ZIP, 100);

    // DocScan scan = DocScan.makeFileScan(getCurBigDocsFile());

    // String aqlFileName = AQL_FILES_DIR + "/chunkInfoTest.aql";

    // System.err.printf("Running AQL file...\n");
    // util.runAQLFile(scan, aqlFileName);

    endTest();
  }

  /*
   * UTILITY METHODS
   */

  /**
   * @return the location of the current test's generated "big documents" file, as created by
   *         {@link #createBigDocs(String,int)}
   */
  private File getCurBigDocsFile() {
    return new File(getCurOutputDir(), DOCS_FILE_NAME);
  }

  /**
   * Simple program that creates big documents by merging the documents in the ensample data set.
   * Documents go into the current output directory.
   * 
   * @param smallDocsFileName name of the archive containing "small" documents to merge.
   * @param numToMerge number of Enron documents to merge into each "big" document.
   */
  private void createBigDocs(String smallDocsFileName, int numToMerge) throws Exception {

    // Open up a scan on the document set.
    Iterator<String> docTextItr = DocReader.makeDocTextItr(new File(smallDocsFileName));

    // Open up the output file.
    ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(getCurBigDocsFile()));

    // Start the first document in the zip archive
    int bigDocIx = 0;
    int docsInCurBigDoc = 0;
    zip.putNextEntry(new ZipEntry("Big Document 0"));

    while (docTextItr.hasNext()) {
      String docText = docTextItr.next();

      // Create a Zip file catalog entry for the new file.

      // Write the document into the zip file, in UTF-8 format.
      zip.write(docText.getBytes("UTF-8"));

      // Check whether we've finished creating the current big document.
      docsInCurBigDoc++;
      if (docsInCurBigDoc > numToMerge) {
        String bigDocName = String.format("Big Document %d", ++bigDocIx);
        zip.putNextEntry(new ZipEntry(bigDocName));
      }

    }
    zip.close();

    // Close the document scan
    docTextItr.remove();
  }
}
