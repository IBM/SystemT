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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.spss.SPSSAnnotation;
import com.ibm.avatar.spss.SPSSDocument;
import com.ibm.avatar.spss.SPSSDriver;

/**
 * Tests for the functionality provided by {@link com.ibm.avatar.spss.SPSSDriver}.
 * 
 */
public class SPSSTests extends RuntimeTestHarness {
  /** AQL include directory */
  public static final String AQL_INCLUDE_DIR =
      TestConstants.TEST_WORKING_DIR + "/testdata/aql/spssSpecificTests";

  /** Top-level AQL file. */
  public static String AQL_FILE_NAME;

  public static void main(String[] args) {
    try {

      SPSSTests t = new SPSSTests();

      t.setUp();

      long startMS = System.currentTimeMillis();

      t.tlaMockupInitialTest();

      long endMS = System.currentTimeMillis();
      double elapsedSec = ((double) (endMS - startMS)) / 1000.0;
      System.err.printf("Test took %1.3f sec.\n", elapsedSec);

      t.tearDown();

    } catch (Exception e) {
      e.printStackTrace();
    }

  }

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() {}

  /**
   * Test that verifies that custom tokenization works as expected. Uses the NLJoin operator to
   * implement FollowsTok.
   * 
   * @throws Exception
   */
  @Test
  public void externalTokensTest() throws Exception {
    // Setup the test
    myStartTest("externalTokens");

    // Create 2 sample documents that are appropriate for the annotator used
    // by this test.
    ArrayList<SPSSDocument> docs = new ArrayList<SPSSDocument>();
    for (int i = 0; i < 2; i++) {

      String docLabel = "Doc_" + (i + 1) + ".txt";
      SPSSDocument doc = generateExternalToksDoc(docLabel);
      System.err.printf("\n\n***** Generated SPSS document '%s' with contents:\n%s\n", docLabel,
          doc);
      docs.add(doc);
    }

    // Execute the generic test
    genericTest(docs);

    // Compare against expected output
    compareAgainstExpected(false);
  }

  /**
   * Same as {@link #externalTokensTest()} but using AdjacentJoin to implement the FollowsTok
   * predicates.
   * 
   * @throws Exception
   */
  @Test
  public void externalTokensAdjacentJoinTest() throws Exception {
    // Setup the test
    myStartTest("externalTokensAdjacentJoin");

    // Create 2 sample documents that are appropriate for the annotator used
    // by this test.
    ArrayList<SPSSDocument> docs = new ArrayList<SPSSDocument>();
    for (int i = 0; i < 2; i++) {

      String docLabel = "Doc_" + (i + 1) + ".txt";
      SPSSDocument doc = generateExternalToksDoc(docLabel);
      System.err.printf("\n\n***** Generated SPSS document '%s' with contents:\n%s\n", docLabel,
          doc);
      docs.add(doc);
    }

    // Execute the generic test
    genericTest(docs);

    // Compare against expected output
    compareAgainstExpected(false);
  }

  @Test
  public void tlaMockupInitialTest() throws Exception {
    // Setup the test
    myStartTest("tlaMockupInitial");

    // Create 2 sample documents that are appropriate for the annotator used
    // by this test.
    ArrayList<SPSSDocument> docs = new ArrayList<SPSSDocument>();
    for (int i = 0; i < 2; i++) {

      String docLabel = "Doc_" + (i + 1) + ".txt";
      SPSSDocument doc = generateTLAMockupInitialDoc(docLabel);
      System.err.printf("\n\n***** Generated SPSS document '%s' with contents:\n%s\n", docLabel,
          doc);
      docs.add(doc);
    }

    // Execute the generic test
    genericTest(docs);

    // Compare against expected output
    compareAgainstExpected(false);
  }

  /**
   * INTERNAL METHODS
   */

  private void myStartTest(String testName) {
    super.startTest(testName);
    AQL_FILE_NAME = AQL_INCLUDE_DIR + "/" + testName + ".aql";
  }

  private void genericTest(ArrayList<SPSSDocument> docs) throws Exception {

    // Create an instance of the Driver for running the SPSS customized extraction.
    SPSSDriver t = new SPSSDriver();

    // Set the input and output files for the driver
    t.setOutputDir(getCurOutputDir().toString());
    t.setAqlFileName(AQL_FILE_NAME);
    t.setAqlIncludeDir(AQL_INCLUDE_DIR);
    t.setDictionaryDir(AQL_INCLUDE_DIR);
    t.setDumpTuples(true);
    t.setOutputHtml(true);

    // Initialize the SystemT runtime
    t.initialize();

    // Annotate the documents
    for (SPSSDocument doc : docs) {
      t.annotate(doc);
    }

    // Close the output HTML files.
    t.closeHTMLOutput();
  }

  /**
   * Generate a sample instance of SPSSDocument with some tokens and annotations.
   */
  private SPSSDocument generateExternalToksDoc(String label) {
    SPSSDocument doc = new SPSSDocument(label);

    // Set a sample content for the document
    doc.setText("This is a test for caching external tokens into System T");
    // doc.setText ("This is a t");

    // Set sample tokens for the document
    doc.setTokens(new int[] {0, 6, 10, 13, 19, 23, 32, 42},
        new int[] {4, 8, 11, 17, 21, 30, 40, 48});
    // doc.setTokens (new int[] { 0, 6, 10 }, new int[] { 4, 8, 11 });

    // Add annotations for view Noun
    ArrayList<SPSSAnnotation> nounAnnots = new ArrayList<SPSSAnnotation>();
    nounAnnots.add(new SPSSAnnotation(13, 17, "info"));
    nounAnnots.add(new SPSSAnnotation(42, 48, "info"));
    doc.addViewAnnotations("Noun", nounAnnots);

    // Add annotations for view Word
    ArrayList<SPSSAnnotation> wordAnnots = new ArrayList<SPSSAnnotation>();
    wordAnnots.add(new SPSSAnnotation(13, 17, "info"));
    wordAnnots.add(new SPSSAnnotation(42, 48, "info"));
    doc.addViewAnnotations("Word", wordAnnots);

    return doc;
  }

  /**
   * Generate a sample instance of SPSSDocument with some tokens and annotations.
   */
  private SPSSDocument generateTLAMockupInitialDoc(String label) {

    SPSSDocument doc = new SPSSDocument(label);

    // Set a sample content for the document
    doc.setText("A quick brown fox jumps over the lazy dog.");

    // Custom document tokens: "A", "quick brown fox", "jumps", "over", "the", "lazy dog", "."
    int[] beginTokOffsets = new int[] {0, 2, 18, 24, 29, 33, 41};
    int[] endTokOffsets = new int[] {1, 17, 23, 28, 32, 41, 42};
    doc.setTokens(beginTokOffsets, endTokOffsets);

    // Add annotations for view Noun: "quick brown fox" (with lead term
    // "fox") and "lazy dog" (with lead term "dog")
    ArrayList<SPSSAnnotation> nounAnnots = new ArrayList<SPSSAnnotation>();
    nounAnnots.add(new SPSSAnnotation(2, 17, "fox"));
    nounAnnots.add(new SPSSAnnotation(33, 41, "dog"));
    doc.addViewAnnotations("Noun", nounAnnots);

    // Add annotations for view Verb: "jumps" (with no lead term)
    ArrayList<SPSSAnnotation> wordAnnots = new ArrayList<SPSSAnnotation>();
    wordAnnots.add(new SPSSAnnotation(18, 23, ""));
    doc.addViewAnnotations("Verb", wordAnnots);

    return doc;
  }

}
