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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.AbstractTupleSchema;
import com.ibm.avatar.algebra.datamodel.BypassTypeChecks;
import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.FieldSetter;
import com.ibm.avatar.algebra.datamodel.ObjectID;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.TextSetter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.oldscan.DBDumpFileScan;
import com.ibm.avatar.algebra.oldscan.PushDocScan;
import com.ibm.avatar.algebra.scan.DocScanInternal;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.api.OperatorGraphRunner;
import com.ibm.avatar.api.exceptions.ExceptionWithView;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.logging.Log;

/**
 * Test case for bugs that crop up when outputs are repeatedly toggled.
 * 
 */
public class ToggleTests extends RuntimeTestHarness {

  /** AQL file used in the tests in this class. */
  public static final File AQL_FILE =
      new File(TestConstants.AQL_DIR, "lotus/namedentity-textcol.aql");

  private Map<String, FieldGetter<Span>> fieldGetters;

  // private TimeKeeper timeKeeper;

  private void printAnnotations(OutputStreamWriter out, Map<String, TupleList> annotations)
      throws Exception {
    for (Entry<String, TupleList> entry : annotations.entrySet()) {
      String outputName = entry.getKey();
      TupleList tups = entry.getValue();
      FieldGetter<Span> getSpan = fieldGetters.get(outputName);
      for (TLIter itr = tups.iterator(); itr.hasNext();) {
        Tuple tuple = itr.next();
        Span s = getSpan.getVal(tuple);
        String line =
            String.format("%s,%d,%d,\"%s\"\n", outputName, s.getBegin(), s.getEnd(), s.getText());
        out.append(line);
      }
    }
  }

  private static String readDoc(String fileName) throws Exception {
    StringBuilder sb = new StringBuilder();
    BufferedReader fileReader =
        new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
    String line = fileReader.readLine();
    while (line != null) {
      sb.append(line);
      line = fileReader.readLine();
    }
    fileReader.close();
    return sb.toString();
  }

  public static void main(String[] args) throws Exception {

    ToggleTests t = new ToggleTests();

    t.setUp();

    t.remnantTest();

    t.tearDown();
  }

  @Before
  public void setUp() {}

  @After
  public void tearDown() {
    // Some of the tests in this file can set the interrupted bit on the
    // main thread. Make sure this bit is cleared.
    Thread.interrupted();

    // Restore original value to avoid screwing with other tests.
    // Planner.restoreDefaultSentence();

    // Restore logging.
    Log.restoreDefaultMsgTypes();

    Log.info("Done.\n");
  }

  @Test
  public void remnantTest() throws Exception {
    startTest();

    final File AQLFILE = new File(TestConstants.AQL_DIR, "lotus/namedentity.aql");

    final String FILE1_NAME = "testdata/docs/remnantTest/doc1.txt";
    final String FILE2_NAME = "testdata/docs/remnantTest/doc2.txt";

    final String OUTFILE_NAME = "remnantTest.txt";
    final File OUTFILE = new File(getCurOutputDir(), OUTFILE_NAME);

    // Generate an output file for comparison.
    FileWriter out = new FileWriter(OUTFILE);

    compileAQL(AQLFILE, TestConstants.TESTDATA_DIR);
    OperatorGraph og = instantiateOperatorGraph(Constants.GENERIC_MODULE_NAME);

    TupleSchema docSchema = DocScanInternal.createOneColumnSchema();
    TextSetter setDocText = docSchema.textSetter(Constants.DOCTEXT_COL);
    setupFieldGetters(og);

    String doc1 = readDoc(FILE1_NAME);
    String doc2 = readDoc(FILE2_NAME);

    Tuple docTup = docSchema.createTup();

    System.err.print("\n********* First doc\n");
    out.write("******************* Pushing doc2 " + "with only person and place enabled \n");
    String[] enabledOutputs =
        new String[] {"Person", "PersonSingleToken", "Place", "AllStates", "Zipcodes"};
    // runner.setOutputEnabled("AllUSCities", true);
    setDocText.setVal(docTup, doc2);
    Map<String, TupleList> annotations = og.execute(docTup, enabledOutputs, null);
    printAnnotations(out, annotations);

    System.err.print("\n********* Second doc\n");
    out.write("******************* Pushing doc1" + " with all outputs enabled \n");
    setDocText.setVal(docTup, doc1);
    annotations = og.execute(docTup, null, null);
    printAnnotations(out, annotations);

    System.err.print("\n********* First doc again\n");
    out.write("*******************  Pushing doc2" + " with all outputs enabled \n");
    setDocText.setVal(docTup, doc2);
    annotations = og.execute(docTup, null, null);
    printAnnotations(out, annotations);

    out.close();

    compareAgainstExpected(OUTFILE_NAME, false);
  }

  @Test
  public void watchdogTest() throws Exception {
    startTest();

    final File AQLFILE = new File(TestConstants.AQL_DIR, "lotus/namedentity.aql");

    final String FILE1_NAME = TestConstants.TEST_DOCS_DIR + "/watchdogTest/wdt1.txt";
    final String FILE2_NAME = TestConstants.TEST_DOCS_DIR + "/watchdogTest/wdt2.txt";

    final String OUTFILE_NAME = "watchdogTest.txt";
    final File OUTFILE = new File(getCurOutputDir(), OUTFILE_NAME);

    // Generate an output file for comparison.
    FileWriter out = new FileWriter(OUTFILE);

    compileAQL(AQLFILE, TestConstants.TESTDATA_DIR);
    OperatorGraph og = instantiateOperatorGraph(Constants.GENERIC_MODULE_NAME);

    TupleSchema docSchema = DocScanInternal.createOneColumnSchema();
    TextSetter setDocText = docSchema.textSetter(Constants.DOCTEXT_COL);
    setupFieldGetters(og);

    String[] docs = new String[] {readDoc(FILE1_NAME), readDoc(FILE2_NAME)};

    // timeKeeper = new TimeKeeper (Logger.getAnonymousLogger ());

    for (String doctext : docs) {
      out.write("******************* Pushing doc\n");
      Tuple docTup = docSchema.createTup();
      setDocText.setVal(docTup, doctext);
      Map<String, TupleList> annotations = og.execute(docTup, null, null);
      printAnnotations(out, annotations);
    }

    out.close();

    // Verify output -- this test may fail if moved to a different machine.
    // util.compareAgainstExpected(OUTFILE_NAME);
  }

  /*
   * Constants for tortureTest() and mtTortureTest()
   */

  /**
   * When executing, we will ask for an interrupt at a random time interval up to this value.
   */
  private static final int MAX_INTERRUPT_MS = 50;

  // Each input is enabled with this probability
  private static final double ENABLE_OUTPUT_PROB = 0.7;

  /**
   * Number of iterations per thread in {@link #tortureTest()} and {@link #mtTortureTest()}
   */
  static final int TORTURE_NUMITER = 100;

  /**
   * A randomized torture test of both the watchdog timer and the facilities for turning outputs on
   * and off. This test repeatedly runs through a random set of documents in random order, enabling
   * random outputs and interrupting after a random amount of time. After each document, it verifies
   * that the results returned are correct.
   * 
   * @throws Exception
   */
  @Test
  public void tortureTest() throws Exception {

    startTest();

    final String LOG_FILE_NAME = getCurOutputDir() + "/results.log";

    // Open up a log file.
    FileWriter log = new FileWriter(LOG_FILE_NAME);
    System.err.printf("Results are logged to %s\n", LOG_FILE_NAME);

    // Initialize the random number generator. Use a different seed each
    // time, but log the random seed for reproducibility.
    long randomSeed = System.currentTimeMillis();
    log.append(String.format("Results of ToggleTests.tortureTest() with random seed %d:\n\n\n",
        randomSeed));
    Random rand = new Random(randomSeed);

    System.err.printf("Compiling annotator...\n");

    OperatorGraph runner = createNamedEntityRunner(1);

    // Generate a random document set and the expected annotations for those
    // docs.
    ArrayList<Tuple> bigDocs = new ArrayList<Tuple>();
    ArrayList<TreeMap<String, TreeSet<Span>>> goldStandard =
        new ArrayList<TreeMap<String, TreeSet<Span>>>();
    setupCompDocs(log, rand, runner, bigDocs, goldStandard);

    // Use our random number generator to seed the RNG inside the main loop.
    tortureTestMainLoop(0, rand.nextLong(), runner, bigDocs, goldStandard);

    log.close();
  }

  /**
   * A version of {@link #tortureTest()} that uses the new Java API.
   */
  @Test
  public void newTortureTest() throws Exception {

    startTest();

    final String LOG_FILE_NAME = getCurOutputDir() + "/results.log";
    final File tamDir = getCurOutputDir();

    // Open up a log file.
    FileWriter log = new FileWriter(LOG_FILE_NAME);
    System.err.printf("Results are logged to %s\n", LOG_FILE_NAME);

    // Initialize the random number generator. Use a different seed each
    // time, but log the random seed for reproducibility.
    long randomSeed = System.currentTimeMillis();
    log.append(String.format(
        "Results of ToggleTests.newTortureTest() " + "with random seed %d:\n\n\n", randomSeed));
    Random rand = new Random(randomSeed);

    OperatorGraph runner = createNamedEntityRunner(1);

    // Generate a random document set and the expected annotations for those
    // docs.
    ArrayList<Tuple> bigDocs = new ArrayList<Tuple>();
    ArrayList<TreeMap<String, TreeSet<Span>>> goldStandard =
        new ArrayList<TreeMap<String, TreeSet<Span>>>();
    setupCompDocs(log, rand, runner, bigDocs, goldStandard);

    Log.info("Compiling annotator...\n");

    // Compile to an AOG file...
    compileAQL(AQL_FILE, TestConstants.TESTDATA_DIR);
    OperatorGraph systemt = instantiateOperatorGraph(Constants.GENERIC_MODULE_NAME);

    Log.info("Beginning main loop...\n");

    // Use our random number generator to seed the RNG inside the main loop.
    newTortureMainLoop(0, rand.nextLong(), systemt, tamDir.getPath(), bigDocs, goldStandard);

    log.close();
  }

  /**
   * Version of {@link #mtTortureTest()} that uses the new Java API.
   */
  @Test
  public void newMtTortureTest() throws Exception {

    startTest();

    final String LOG_FILE_NAME = getCurOutputDir() + "/results.log";
    final File tamDir = new File(getCurOutputDir(), "plan.aog");

    final int NUM_THREADS = 8;

    // Open up a log file.
    FileWriter log = new FileWriter(LOG_FILE_NAME);
    System.err.printf("Results are logged to %s\n", LOG_FILE_NAME);

    // Initialize the random number generator. Use a different seed each
    // time, but log the random seed for reproducibility.
    long randomSeed = System.currentTimeMillis();
    log.append(String.format("Results of ToggleTests.tortureTest() " + "with random seed %d:\n\n\n",
        randomSeed));
    Random rand = new Random(randomSeed);

    // The non-LanguageWare sentence operator is not currently thread-safe.
    OperatorGraph runner = createNamedEntityRunner(NUM_THREADS);

    // Generate a random document set and the expected annotations for those
    // docs.
    final ArrayList<Tuple> bigDocs = new ArrayList<Tuple>();
    final ArrayList<TreeMap<String, TreeSet<Span>>> goldStandard =
        new ArrayList<TreeMap<String, TreeSet<Span>>>();
    setupCompDocs(log, rand, runner, bigDocs, goldStandard);

    Log.info("Compiling annotator...\n");

    // Compile to a TAM
    compileAQL(AQL_FILE, TestConstants.TESTDATA_DIR);
    final OperatorGraph systemt = instantiateOperatorGraph(Constants.GENERIC_MODULE_NAME);

    // Spawn off the worker threads.
    class worker implements Runnable {

      int threadIx;
      long randomSeed;

      /** If the thread receives an exception, we put it in this field. */
      Throwable exception = null;

      private worker(int threadIx, long randomSeed) {
        this.threadIx = threadIx;
        this.randomSeed = randomSeed;

      }

      @Override
      public void run() {
        try {
          newTortureMainLoop(threadIx, randomSeed, systemt, tamDir.getPath(), bigDocs,
              goldStandard);
        } catch (Throwable e) {
          exception = e;
        }
      }

    }

    Thread[] threads = new Thread[NUM_THREADS];
    worker[] workers = new worker[NUM_THREADS];
    for (int i = 0; i < NUM_THREADS; i++) {
      // Use our random number generator to seed the RNG inside the main
      // loop.
      workers[i] = new worker(i, rand.nextLong());
      threads[i] = new Thread(workers[i]);
      threads[i].start();
    }

    // Wait for the worker threads to complete.
    for (int i = 0; i < NUM_THREADS; i++) {
      threads[i].join();

      // Pass through the first exception we receive from a worker thread.
      if (null != workers[i].exception) {
        throw new Exception(String.format("Worker thread %d threw an exception", i),
            workers[i].exception);
      }
    }

    log.close();

  }

  /**
   * Main loop for tortureTest() and mtTortureTest()
   * 
   * @param threadIx index of the thread in which the loop is executing (0 for single-threaded)
   * @param randomSeed
   * @param runner
   * @param bigDocs
   * @param goldStandard
   * @throws Exception
   */
  private void tortureTestMainLoop(int threadIx, long randomSeed, OperatorGraph runner,
      ArrayList<Tuple> bigDocs, ArrayList<TreeMap<String, TreeSet<Span>>> goldStandard)
      throws Exception {

    Random rand = new Random(randomSeed);

    System.err.printf("Running annotator (thread %d)...\n", threadIx);
    long startMs = System.currentTimeMillis();
    int numInterrupts = 0;

    // Create a reusable document tuple; we'll fill in its fields and OID
    // each time we process a document.
    PushDocScan fakescan = new PushDocScan();
    AbstractTupleSchema docSchema = fakescan.getOutputSchema();

    FieldSetter<String> setText = docSchema.textSetter(Constants.DOCTEXT_COL);
    FieldGetter<Text> getText = docSchema.textAcc(Constants.DOCTEXT_COL);
    Tuple docCopy = docSchema.createTup();

    for (int i = 0; i < TORTURE_NUMITER; i++) {

      // Enable a random subset of the inputs.
      ArrayList<String> enabledOutputs = new ArrayList<String>();
      for (String outputName : runner.getOutputTypeNames()) {
        boolean enabled = (rand.nextDouble() < ENABLE_OUTPUT_PROB);
        if (enabled)
          enabledOutputs.add(outputName);
      }

      // Ask for the timer interrupt.
      // TODO: This part is no longer applicable in the new OperatorGraph. Modify or remove.
      // final OperatorGraph rnr = runner;
      // final int tdix = threadIx;
      Timer timer = new Timer();
      TimerTask interruptMe = new TimerTask() {
        Thread curThread = Thread.currentThread();

        @Override
        public void run() {
          curThread.interrupt();
          // rnr.interrupt (tdix);
        }
      };
      int delay = rand.nextInt(MAX_INTERRUPT_MS);
      timer.schedule(interruptMe, delay);

      // Run the current document.
      int docix = rand.nextInt(bigDocs.size());
      Tuple doc = bigDocs.get(docix);

      // Make a (shallow) copy of the document tuple so that token caching
      // doesn't get screwed up.
      docCopy.setOid(doc.getOid());
      setText.setVal(docCopy, getText.getVal(doc).getText());
      doc = null;

      // Check to see if the document is valid with the required doc schema
      // OperatorGraphRunner.validateTuple (docCopy, runner.getDocumentSchema ());
      if (docCopy.size() != runner.getDocumentSchema().size()) {
        throw new RuntimeException(String
            .format("Input document does not meet required doc schema %s.", docSchema.toString()));
      }

      // Log.debug("Running over %s", docCopy.getOid());

      boolean wasInterrupted = false;
      Map<String, TupleList> annotations = new HashMap<String, TupleList>();
      try {
        annotations = runner.execute(docCopy, enabledOutputs.toArray(new String[0]), null);
      }
      // The top-level exception is now ExceptionWithView. It's cause should be an
      // interruptedException
      // catch (InterruptedException e) {
      catch (ExceptionWithView e) {
        assertTrue(null != e.getCause());
        assertTrue(e.getCause() instanceof InterruptedException);
        // Log.info ("--> Iteration %d was interrupted\n", i);
        wasInterrupted = true;
        numInterrupts++;
      }

      // Forget about the timer interrupt.
      timer.cancel();

      for (Entry<String, TupleList> entry : annotations.entrySet()) {
        String outputName = entry.getKey();
        TupleList tups = entry.getValue();

        TreeSet<Span> curSpans = new TreeSet<Span>();
        if (null == tups) {
          // If we were interrupted, we may have no results for
          // this particular output.
          assertTrue(wasInterrupted);
        } else {
          // Got a result set for this output; package it up.
          for (TLIter itr = tups.iterator(); itr.hasNext();) {
            Tuple tup = itr.next();

            FieldGetter<Span> getSpan = fieldGetters.get(outputName);
            Span s = getSpan.getVal(tup);
            curSpans.add(s);
          }
        }

        // Make sure that this span is part of the set of spans we
        // should be getting back for this document.
        TreeSet<Span> expectedSpans = goldStandard.get(docix).get(outputName);

        if (wasInterrupted) {
          // An interrupt can cause us to compute a subset of the
          // expected spans, so just verify that we have a subset.

          // Use a string comparison, as the built-in comparison
          // functions for Spans aren't intended to compare across
          // different runs of the annotator.
          HashSet<String> expectedStrings = new HashSet<String>();
          for (Span span : expectedSpans) {
            expectedStrings.add(span.toString());
          }

          for (Span span : curSpans) {
            if (false == expectedStrings.contains(span.toString())) {
              throw new Exception(String.format(
                  "On document " + "'%s' (iteration %d), " + "got unexpected span"
                      + " %s for output '%s'; " + "see logfile.",
                  docCopy.getOid(), i, span, outputName));
            }
          }
        } else {
          // If we weren't interrupted, we should see exactly the
          // expected set of spans.
          // Compare a string representation of the spans, since
          // the low-level span comparison functions are not meant
          // to function across different runs of the annotator.
          String expectedStr = expectedSpans.toString();
          String actualStr = curSpans.toString();
          if (false == expectedStr.equals(actualStr)) {
            throw new Exception(String.format(
                "On document " + "'%s' (iteration %d), " + "output '%s', expected "
                    + "spans %s but got %s",
                docCopy.getOid(), i, outputName, expectedStr, actualStr));
          }
        }

      }

      // Generate status message
      int iterComplete = i + 1;
      if (0 == iterComplete % 100) {
        long curMs = System.currentTimeMillis();
        double elapsedSec = ((double) (curMs - startMs)) / 1000.0;
        System.err.printf("%5d/%d iterations in %4.1f sec " + "-- %d (%1.1f %%) interrupted\n",
            iterComplete, TORTURE_NUMITER, elapsedSec, numInterrupts,
            100.0 * (double) numInterrupts / (double) iterComplete);
      }
    }
  }

  /**
   * Version of {@link #tortureTestMainLoop(int, long, OperatorGraphRunner, ArrayList, ArrayList)}
   * for the new Java API to SystemT.
   */
  private void newTortureMainLoop(int threadIx, long randomSeed, OperatorGraph systemt,
      String annotatorId, ArrayList<Tuple> bigDocs,
      ArrayList<TreeMap<String, TreeSet<Span>>> goldStandard) throws Exception {

    Random rand = new Random(randomSeed);

    System.err.printf("Running annotator (thread %d)...\n", threadIx);
    long startMs = System.currentTimeMillis();

    // Create a reusable document tuple; we'll fill in its fields and OID
    // each time we process a document.
    PushDocScan fakescan = new PushDocScan();
    TupleSchema docSchema = (TupleSchema) fakescan.getOutputSchema();

    FieldSetter<String> setText = docSchema.textSetter(Constants.DOCTEXT_COL);
    FieldGetter<Text> getText = docSchema.textAcc(Constants.DOCTEXT_COL);
    Tuple docCopy = docSchema.createTup();

    for (int i = 0; i < TORTURE_NUMITER; i++) {

      // Enable a random subset of the inputs.
      ArrayList<String> enabledTypes = new ArrayList<String>();
      for (String outputName : systemt.getOutputTypeNames()) {
        enabledTypes.add(outputName);
      }
      String[] enabledTypesArr = new String[enabledTypes.size()];
      enabledTypesArr = enabledTypes.toArray(enabledTypesArr);

      // Choose a document at random.
      int docix = rand.nextInt(bigDocs.size());
      Tuple doc = bigDocs.get(docix);

      // Make a (shallow) copy of the document tuple so that token caching
      // doesn't get screwed up.
      docCopy.setOid(doc.getOid());
      setText.setVal(docCopy, getText.getVal(doc).getText());
      doc = null;

      // Get annotations.
      Map<String, TupleList> results = systemt.execute(docCopy, enabledTypesArr, null);

      for (String outputName : systemt.getOutputTypeNames()) {
        if (results.containsKey(outputName)) {
          TupleList tups = results.get(outputName);

          TreeSet<Span> curSpans = new TreeSet<Span>();

          // Got a result set for this output; package it up.
          for (TLIter itr = tups.iterator(); itr.hasNext();) {
            Tuple tup = itr.next();

            FieldGetter<Span> getSpan = fieldGetters.get(outputName);
            Span s = getSpan.getVal(tup);
            curSpans.add(s);
          }

          // Make sure that this span is part of the set of spans we
          // should be getting back for this document.
          TreeSet<Span> expectedSpans = goldStandard.get(docix).get(outputName);

          // If we weren't interrupted, we should see exactly the
          // expected set of spans.
          // Compare the string representation of the two lists of
          // tuples, since the system's built-in comparison functions
          // aren't meant for doing comparisons across different runs
          // of an annotator.
          String expectedStr = expectedSpans.toString();
          String actualStr = curSpans.toString();
          if (false == expectedStr.equals(actualStr)) {
            throw new Exception(String.format(
                "On document " + "'%s' (iteration %d), " + "output '%s', expected "
                    + "spans %s but got %s",
                docCopy.getOid(), i, outputName, expectedStr, actualStr));
          }
        }

      }

      // Generate status message
      int iterComplete = i + 1;
      if (0 == iterComplete % 100) {
        long curMs = System.currentTimeMillis();
        double elapsedSec = ((double) (curMs - startMs)) / 1000.0;
        Log.info("%5d/%d iterations in %4.1f sec.", iterComplete, TORTURE_NUMITER, elapsedSec);
      }
    }
  }

  /** Create a composite document set for tortureTest() */
  private void setupCompDocs(FileWriter log, Random rand, OperatorGraph runner,
      ArrayList<Tuple> bigDocs, ArrayList<TreeMap<String, TreeSet<Span>>> goldStandard)
      throws Exception {

    // Start out by choosing some random documents and concatenating them to
    // produce bigger documents.
    System.err.printf("Building composite documents...\n");
    buildBigDocs(rand, bigDocs);

    BypassTypeChecks<Text> getColZero = new BypassTypeChecks<Text>(0);

    // Log the composite documents to the log file so that we can
    // reconstruct any problems that occurred.
    String msg = String.format("Built %d composite documents.\n\n", bigDocs.size());
    log.append(msg);
    for (int i = 0; i < bigDocs.size(); i++) {
      Tuple doc = bigDocs.get(i);
      log.append(String.format("BEGIN %s **************************\n", doc.getOid()));
      log.append(getColZero.getVal(doc).getText());
      log.append(String.format("END %s ****************************\n\n", doc.getOid()));
    }

    // Compute the full set of annotations for the test documents.
    // Each entry contains a mapping from output name to set of spans
    System.err.printf("Computing expected annotations...\n");
    getFullResults(bigDocs, runner, goldStandard);

    // Log the expected results.
    log.append("*** BEGIN expected results\n");
    for (int i = 0; i < bigDocs.size(); i++) {
      log.append(String.format("%s:\n", bigDocs.get(i).getOid()));

      TreeMap<String, TreeSet<Span>> results = goldStandard.get(i);

      for (String name : results.keySet()) {
        log.append(String.format("    %s --> %s\n", name, results.get(name)));
      }
    }
    log.append("*** END expected results\n");
  }

  /**
   * A multithreaded version of tortureTest(). Spawns 16 threads, each of which runs the main loop
   * of tortureTest() in parallel.
   * 
   * @throws Exception
   */
  @Test
  public void mtTortureTest() throws Exception {

    startTest();

    final String LOG_FILE_NAME = getCurOutputDir() + "/results.log";

    final int NUM_THREADS = 8;

    // Open up a log file.
    FileWriter log = new FileWriter(LOG_FILE_NAME);
    System.err.printf("Results are logged to %s\n", LOG_FILE_NAME);

    // Initialize the random number generator. Use a different seed each
    // time, but log the random seed for reproducibility.
    long randomSeed = System.currentTimeMillis();
    log.append(String.format("Results of ToggleTests.tortureTest() with random seed %d:\n\n\n",
        randomSeed));
    Random rand = new Random(randomSeed);

    System.err.printf("Compiling annotator...\n");

    // The non-LanguageWare sentence operator is not currently thread-safe.
    // Planner.forceLWSentence();
    OperatorGraph runner = createNamedEntityRunner(NUM_THREADS);

    // Generate a random document set and the expected annotations for those
    // docs.
    ArrayList<Tuple> bigDocs = new ArrayList<Tuple>();
    ArrayList<TreeMap<String, TreeSet<Span>>> goldStandard =
        new ArrayList<TreeMap<String, TreeSet<Span>>>();
    setupCompDocs(log, rand, runner, bigDocs, goldStandard);

    // Spawn off the worker threads.
    class worker implements Runnable {

      int threadIx;
      long randomSeed;
      OperatorGraph runner;
      ArrayList<Tuple> bigDocs;
      ArrayList<TreeMap<String, TreeSet<Span>>> goldStandard;

      /** If the thread detects an exception, we set this flag. */
      boolean threwException = false;

      private worker(int threadIx, long randomSeed, OperatorGraph runner2, ArrayList<Tuple> bigDocs,
          ArrayList<TreeMap<String, TreeSet<Span>>> goldStandard) {
        this.threadIx = threadIx;
        this.randomSeed = randomSeed;
        this.runner = runner2;
        this.bigDocs = bigDocs;
        this.goldStandard = goldStandard;
      }

      @Override
      public void run() {
        try {
          tortureTestMainLoop(threadIx, randomSeed, runner, bigDocs, goldStandard);
        } catch (Throwable e) {
          threwException = true;
          e.printStackTrace();
          throw new RuntimeException(e);
        }
      }

    }

    Thread[] threads = new Thread[NUM_THREADS];
    worker[] workers = new worker[NUM_THREADS];
    for (int i = 0; i < NUM_THREADS; i++) {
      // Use our random number generator to seed the RNG inside the main
      // loop.
      workers[i] = new worker(i, rand.nextLong(), runner, bigDocs, goldStandard);
      threads[i] = new Thread(workers[i]);
      threads[i].start();
    }

    // Wait for the worker threads to complete.
    for (int i = 0; i < NUM_THREADS; i++) {
      threads[i].join();

      assertFalse(workers[i].threwException);

    }

    log.close();

    // Restore original value to avoid screwing with other tests.
    // Planner.restoreDefaultSentence();
  }

  private OperatorGraph createNamedEntityRunner(int nthreads) throws Exception {

    compileAQL(AQL_FILE, TestConstants.TESTDATA_DIR);
    OperatorGraph og = instantiateOperatorGraph(Constants.GENERIC_MODULE_NAME);

    setupFieldGetters(og);

    return og;
  }

  /**
   * Retrieve the full results for the indicated set of documents
   * 
   * @param docs set of documents to annotate
   * @param runner OperatorGraphRunner, initialized and set for push mode
   * @param goldStandard2
   * @return an array of result sets; each element contains the results for the corresponding input
   *         document. These results are organized by output type name.
   */
  private void getFullResults(ArrayList<Tuple> docs, OperatorGraph runner,
      ArrayList<TreeMap<String, TreeSet<Span>>> goldStandard) throws Exception {

    for (int i = 0; i < docs.size(); i++) {
      Tuple docTup = docs.get(i);
      Map<String, TupleList> annotations = runner.execute(docTup, null, null);

      TreeMap<String, TreeSet<Span>> curResults = new TreeMap<String, TreeSet<Span>>();
      for (Entry<String, TupleList> entry : annotations.entrySet()) {
        String outputName = entry.getKey();
        TupleList tups = entry.getValue();

        TreeSet<Span> curSpans = new TreeSet<Span>();
        for (TLIter itr = tups.iterator(); itr.hasNext();) {
          Tuple tup = itr.next();

          FieldGetter<Span> getSpan = fieldGetters.get(outputName);
          Span s = getSpan.getVal(tup);
          curSpans.add(s);
        }

        curResults.put(outputName, curSpans);
      }

      goldStandard.add(curResults);
    }
  }

  /**
   * @param bigDocs
   * @return an array of artifical large docs, produced by concatenating small documents
   * @throws Exception
   */
  private void buildBigDocs(Random rand, ArrayList<Tuple> bigDocs) throws Exception {

    boolean debug = false;

    // How many documents we concatenate together to form our "big"
    // documents
    final int DOCS_TO_CONCAT = 10;

    // What percentage of documents we use
    final double DOC_FRACT = 0.1;

    String curText = "";
    int docsInText = 0;
    int docsUsed = 0;
    int docsTot = 0;

    // We need to create actual document tuples so that upstream code can
    // compare spans over these documents across different runs.
    DBDumpFileScan scan = new DBDumpFileScan(TestConstants.ENRON_10K_DUMP);

    // We'll be building new documents that use the same schema as the ones
    // the scan returned.
    AbstractTupleSchema docSchema = scan.getOutputSchema();
    FieldSetter<String> setText = docSchema.textSetter(Constants.DOCTEXT_COL);

    FieldGetter<Text> getText = docSchema.textAcc(Constants.DOCTEXT_COL);
    MemoizationTable mt = new MemoizationTable(scan);
    while (mt.haveMoreInput()) {
      docsTot++;
      mt.resetCache();
      Tuple docTup = scan.getNextDocTup(mt);
      if (rand.nextDouble() <= DOC_FRACT) {
        String text = getText.getVal(docTup).getText();

        if (debug) {
          System.err.printf("***** Using document %s:\n", docTup.getOid());
          System.err.printf("%s\n", text);
          System.err.printf("---------------------------------------------\n\n");
        }

        curText = curText + text + "\n";
        docsInText++;
        docsUsed++;
      }

      // Create a "big" document out of the concatenated docs
      if (docsInText >= DOCS_TO_CONCAT || (false == mt.haveMoreInput())) {
        Tuple bigDoc = docSchema.createTup();
        setText.setVal(bigDoc, curText);

        // Set up a fake OID for reference purposes.
        bigDoc.setOid(new ObjectID("CompositeDoc", bigDocs.size(), true));

        bigDocs.add(bigDoc);
        curText = "";
        docsInText = 0;
      }
    }

    System.err.printf("Used %d of %d docs to build %d composite documents.\n", docsUsed, docsTot,
        bigDocs.size());
  }

  private void setupFieldGetters(OperatorGraph runner) throws TextAnalyticsException {
    fieldGetters = new HashMap<String, FieldGetter<Span>>();
    for (Entry<String, TupleSchema> entry : runner.getOutputTypeNamesAndSchema().entrySet()) {
      String outputName = entry.getKey();
      AbstractTupleSchema schema = entry.getValue();
      fieldGetters.put(outputName, schema.spanAcc(schema.getFieldNameByIx(0)));
    }
  }
}
