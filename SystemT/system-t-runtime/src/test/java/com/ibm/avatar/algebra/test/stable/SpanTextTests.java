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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.util.lang.LangCode;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.DocReader;
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.aql.tam.TAM;
import com.ibm.avatar.aql.tam.TAMSerializer;
import com.ibm.avatar.logging.Log;

/**
 * Various regression tests for interaction of Span and Text types
 * 
 */
public class SpanTextTests extends RuntimeTestHarness {

  /**
   * Directory where input documents for this test reside.
   */
  private static final String DOCS_DIR = TestConstants.TEST_DOCS_DIR + "/SpanTextTests";

  /**
   * Directory where the aql files for this test reside.
   */
  private static final File AQL_DIR = new File(TestConstants.AQL_DIR, "SpanTextTests");

  /**
   * Scan over the Enron database; set up by setUp() and cleaned out by tearDown()
   */
  private File defaultDocsFile = new File(TestConstants.ENRON_1K_DUMP);

  private final boolean debug = false;

  /**
   * Main method for invoking one test at a time.
   * 
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    SpanTextTests t = new SpanTextTests();
    t.setUp();

    long startMS = System.currentTimeMillis();

    t.typeHierarchyTest();

    long endMS = System.currentTimeMillis();

    t.tearDown();

    double elapsedSec = (endMS - startMS) / 1000.0;
    System.err.printf("Test took %1.3f sec.\n", elapsedSec);
  }

  @Before
  public void setUp() {
    defaultDocsFile = new File(TestConstants.ENRON_1K_DUMP);

  }

  @After
  public void tearDown() {

  }

  /**
   * Test case for select text column
   * 
   * @throws Exception
   */
  @Test
  public void typeHierarchyTest() throws Exception {
    startTest();

    FieldType[] types =
        new FieldType[] {FieldType.SPAN_TYPE, FieldType.TEXT_TYPE, FieldType.SPANTEXT_TYPE,};

    for (FieldType type1 : types) {
      for (FieldType type2 : types) {
        assertTrue(String.format("%s type must accept %s type", type1, type2),
            type1.accepts(type2));
      }
    }

    // assertTrue ("SpanText type must accept span type", spantext.accepts (span));
    // assertTrue ("Span type must accept text type", span.accepts (text));
    // assertTrue ("Span type must accept text type", span.accepts (spantext));
    endTest();

  }

  /**
   * Test case for text object comparison
   * 
   * @throws Exception
   */
  @Test
  public void textCompareTest() throws Exception {
    startTest();

    Text x = new Text("xxx", LangCode.en);
    Text y = new Text("yyy", LangCode.en);
    Text a = new Text("aaa", LangCode.en);
    Text z = new Text("xxx", LangCode.en);

    // Check that texts are compared lexically
    assertEquals("Expect: x == z", x, z);
    assertTrue("Expect: x < y", x.compareTo(y) < 0);
    assertTrue("Expect: x > a", x.compareTo(a) > 0);
    assertTrue("Expect: x  > null", x.compareTo(null) > 0);

    // Check that text sorting is correct
    Text[] texts = new Text[] {z, y, a, x};

    List<Text> textList = Arrays.asList(texts);
    System.out.println(textList);
    Collections.sort(textList);
    System.out.println(textList);

    Text[] expected = new Text[] {a, x, x, y,};
    List<Text> expectedList = Arrays.asList(expected);

    assertEquals("Text should be sorted", expectedList, textList);

    endTest();
  }

  /**
   * Test case for span object comparison
   * 
   * @throws Exception
   */
  @Test
  public void spanCompareTest() throws Exception {
    startTest();

    // Define some base texts
    Text x = new Text("xxxxxxxxxxxxxxxx", LangCode.en);
    Text y = new Text("yyyyyyyyyyyyyyyy", LangCode.en);
    Text z = new Text("xxxxxxxxxxxxxxxx", LangCode.en);

    // Define some Spans
    Span x24 = Span.makeBaseSpan(x, 2, 4);
    Span x68 = Span.makeBaseSpan(x, 6, 8);
    Span x26 = Span.makeBaseSpan(x, 2, 6);
    Span x48 = Span.makeBaseSpan(x, 4, 8);
    Span x06 = Span.makeBaseSpan(x, 0, 6);

    Span z24 = Span.makeBaseSpan(z, 2, 4);
    Span z68 = Span.makeBaseSpan(z, 6, 8);
    Span z26 = Span.makeBaseSpan(z, 2, 6);
    Span z48 = Span.makeBaseSpan(z, 4, 8);
    Span z06 = Span.makeBaseSpan(z, 0, 6);

    // Test all cases of Allen's interval algebra for spans on the same text
    // X=Y
    assertEquals("Expect: x24 == z24", x24, z24);
    assertEquals("Expect: z24 == x24", z24, x24);

    // XsY
    assertTrue("Expect: x24 < z26", x24.compareTo(z26) < 0);
    assertTrue("Expect: x26 > z24", x26.compareTo(z24) > 0);

    // XoY
    assertTrue("Expect: x26 < z48", x26.compareTo(z48) < 0);
    assertTrue("Expect: x48 > z26", x48.compareTo(z26) > 0);

    // XmY
    assertTrue("Expect: x26 < z68", x26.compareTo(z68) < 0);
    assertTrue("Expect: x68 > z26", x68.compareTo(z26) > 0);

    // XfY
    assertTrue("Expect: x06 < z26", x06.compareTo(z26) < 0);
    assertTrue("Expect: x26 > z06", x26.compareTo(z06) > 0);

    // XdY
    assertTrue("Expect: x06 < z24", x06.compareTo(z24) < 0);
    assertTrue("Expect: x24 > z06", x24.compareTo(z06) > 0);

    // X<Y
    assertTrue("Expect: x24 < x68", x24.compareTo(z68) < 0);
    assertTrue("Expect: x68 > x24", x68.compareTo(z24) > 0);

    // For Spans on different text:

    // Define some Spans
    Span y24 = Span.makeBaseSpan(y, 2, 4);
    Span y68 = Span.makeBaseSpan(y, 6, 8);
    Span y26 = Span.makeBaseSpan(y, 2, 6);
    Span y48 = Span.makeBaseSpan(y, 4, 8);
    Span y06 = Span.makeBaseSpan(y, 0, 6);

    // Test all cases of Allen's interval algebra for spans on different text
    // X=Y
    assertTrue("Expect: x24 < y24", x24.compareTo(y24) < 0);
    assertTrue("Expect: y24 > x24", y24.compareTo(x24) > 0);

    // XsY
    assertTrue("Expect: x24 < y26", x24.compareTo(y26) < 0);
    assertTrue("Expect: y26 > x24", y26.compareTo(x24) > 0);
    assertTrue("Expect: x26 < y24", x26.compareTo(y24) < 0);
    assertTrue("Expect: y24 > x26", y24.compareTo(x26) > 0);

    // XoY
    assertTrue("Expect: x26 < y48", x26.compareTo(y48) < 0);
    assertTrue("Expect: y48 > x26", y48.compareTo(x26) > 0);
    assertTrue("Expect: x48 < y26", x48.compareTo(y26) < 0);
    assertTrue("Expect: y26 > x48", y26.compareTo(x48) > 0);

    // XmY
    assertTrue("Expect: x26 < y68", x26.compareTo(y68) < 0);
    assertTrue("Expect: y68 > x26", y68.compareTo(x26) > 0);
    assertTrue("Expect: x68 < y26", x68.compareTo(y26) < 0);
    assertTrue("Expect: y26 > x68", y26.compareTo(x68) > 0);

    // XfY
    assertTrue("Expect: x06 < y26", x06.compareTo(y26) < 0);
    assertTrue("Expect: y26 > x06", y26.compareTo(x06) > 0);
    assertTrue("Expect: x26 < y06", x26.compareTo(y06) < 0);
    assertTrue("Expect: y06 > x26", y06.compareTo(x26) > 0);

    // XdY
    assertTrue("Expect: x06 < y24", x06.compareTo(y24) < 0);
    assertTrue("Expect: y24 > x06", y24.compareTo(x06) > 0);
    assertTrue("Expect: x24 < y06", x24.compareTo(y06) < 0);
    assertTrue("Expect: y06 > x24", y06.compareTo(x24) > 0);

    // X<Y
    assertTrue("Expect: x24 < y68", x24.compareTo(y68) < 0);
    assertTrue("Expect: y68 > x24", y68.compareTo(x24) > 0);
    assertTrue("Expect: x68 < y24", x68.compareTo(y24) < 0);
    assertTrue("Expect: y24 > x68", y24.compareTo(x68) > 0);

    // Check that span sorting is correct
    Span[] texts = new Span[] {y24, x26, z26, x06, z68, y48, z24, y06, z48, x68, y48};

    List<Span> textList = Arrays.asList(texts);
    System.out.println(textList);
    Collections.sort(textList);
    System.out.println(textList);

    Span[] expected = new Span[] {x06, x24, x26, x26, x48, x68, x68, y06, y24, y48, y48};
    List<Span> expectedList = Arrays.asList(expected);
    System.out.println(expectedList);

    assertEquals("Span should be sorted", expectedList, textList);

    endTest();
  }

  /**
   * Test case for the effect of span text conversion on comparison
   * 
   * @throws Exception
   */
  @Test
  public void spanTextCompareTest() throws Exception {
    startTest();

    // Define some base texts
    Text x = new Text("aaa bbb", LangCode.en);
    Text y = new Text("bbb aaa", LangCode.en);

    Span xab = x.toSpan();
    Span yba = y.toSpan();

    // Define some Spans
    Span x03 = Span.makeBaseSpan(x, 0, 3);
    Span x47 = Span.makeBaseSpan(x, 4, 7);
    Span y03 = Span.makeBaseSpan(y, 0, 3);
    Span y47 = Span.makeBaseSpan(y, 4, 7);

    Text xa = x03.toText();
    Text xb = x47.toText();
    Text ya = y47.toText();
    Text yb = y03.toText();

    // Check that text sorting is correct
    Text[] texts = new Text[] {yb, xa, xb, ya, x, y};

    List<Text> textList = Arrays.asList(texts);
    System.out.println(textList);
    Collections.sort(textList);
    System.out.println(textList);

    Text[] expectedTexts = new Text[] {ya, xa, x, yb, xb, y};
    List<Text> expectedTextList = Arrays.asList(expectedTexts);
    System.out.println(expectedTextList);

    assertEquals("Text should be sorted", expectedTextList, textList);

    // Check that span sorting is correct
    Span[] spans = new Span[] {yba, xab, x03, y47, y03, x47};

    List<Span> spanList = Arrays.asList(spans);
    System.out.println(spanList);
    Collections.sort(spanList);
    System.out.println(spanList);

    Span[] expectedSpans = new Span[] {x03, xab, x47, y03, yba, y47};
    List<Span> expectedSpanList = Arrays.asList(expectedSpans);
    System.out.println(expectedSpanList);

    assertEquals("Span should be sorted", expectedSpanList, spanList);

    endTest();
  }

  /**
   * Test case for selecting Text column
   * 
   * @throws Exception
   */
  @Test
  public void selectTextColTest() throws Exception {
    startTest();
    final File aqlFile = new File(AQL_DIR, "selectTextCol.aql");
    // final File docsFile = new File (DOCS_DIR, "simpleDocs.del");
    runTest(defaultDocsFile, aqlFile);
    // compareAgainstExpected (false);
    endTest();
  }

  /**
   * Test case for selecting Span column
   * 
   * @throws Exception
   */
  @Test
  public void selectSpanColTest() throws Exception {
    startTest();
    final File aqlFile = new File(AQL_DIR, "selectSpanCol.aql");
    // final File docsFile = new File (DOCS_DIR, "simpleDocs.del");

    setDataPath(TestConstants.TESTDATA_DIR);
    runNonModularAQLTest(defaultDocsFile, aqlFile);

    // compareAgainstExpected (false);
    endTest();
  }

  /**
   * Test case for selecting Span on Text column
   * 
   * @throws Exception
   */
  @Test
  public void selectSpanOnTextColTest() throws Exception {
    startTest();
    final File aqlFile = new File(AQL_DIR, "selectSpanOnTextCol.aql");
    // final File docsFile = new File (DOCS_DIR, "simpleDocs.del");

    setDataPath(TestConstants.TESTDATA_DIR);
    runNonModularAQLTest(defaultDocsFile, aqlFile);

    // compareAgainstExpected (false);
    endTest();
  }

  /**
   * Test case for using GetText on Text column
   * 
   * @throws Exception
   */
  @Test
  public void getTextOnTextColTest() throws Exception {
    startTest();
    final File aqlFile = new File(AQL_DIR, "getTextOnTextCol.aql");
    // final File docsFile = new File (DOCS_DIR, "simpleDocs.del");

    runNonModularAQLTest(defaultDocsFile, aqlFile);

    // compareAgainstExpected (false);
    endTest();
  }

  /**
   * Test case for using GetText on Span column
   * 
   * @throws Exception
   */
  @Test
  public void getTextOnSpanColTest() throws Exception {
    startTest();
    final File aqlFile = new File(AQL_DIR, "getTextOnSpanCol.aql");
    // final File docsFile = new File (DOCS_DIR, "simpleDocs.del");

    runNonModularAQLTest(defaultDocsFile, aqlFile);

    // compareAgainstExpected (false);
    endTest();
  }

  /**
   * Test case for a UDF that accepts a span input. Check it also works if given a Text input.
   * 
   * @throws Exception
   */
  @Test
  public void spanUDFTest() throws Exception {
    startTest();
    final File aqlFile = new File(AQL_DIR, "spanUDFTest.aql");
    // final File docsFile = new File (DOCS_DIR, "simpleDocs.del");

    setDataPath(TestConstants.TESTDATA_DIR);
    runNonModularAQLTest(defaultDocsFile, aqlFile);

    // compareAgainstExpected (false);
    endTest();
  }

  /**
   * Test case for comparing span and text
   * 
   * @throws Exception
   */
  @Test
  public void comparisonTest() throws Exception {
    startTest();
    final File aqlFile = new File(AQL_DIR, "comparisonTest.aql");
    final File docsFile = new File(DOCS_DIR, "labeledDocs.del");

    setDataPath(TestConstants.TESTDATA_DIR);
    runNonModularAQLTest(docsFile, aqlFile);

    // compareAgainstExpected (false);
    endTest();
  }

  /**
   * Test case comparing two Span objects based on their underlying document Text objects as done in
   * {@link com.ibm.avatar.algebra.function.scalar.FastComparator#compare(Tuple, Tuple)}. <b>RTC
   * 167415</b> documents a defect in this comparison behavior and was subsequently used to fix the
   * defect.
   *
   * @throws Exception
   */
  @Test
  public void fastComparatorTest() throws Exception {
    startTest();
    final File aqlFile = new File(AQL_DIR, "fastComparatorTest.aql");
    final File docsFile = new File(DOCS_DIR, "fastComparatorTest.txt");

    setDataPath(TestConstants.TESTDATA_DIR);
    runNonModularAQL(docsFile, aqlFile);

    compareAgainstExpected(false);
    endTest();
  }

  /**
   * Test of cast expressions support in AQL.
   * <p>
   * TODO Warning 2013-09. Disabled for now as the scalar and Text cast does not seem to be correct.
   * No adverse effect observed on all other tests. More investigation needed.
   * 
   * @throws Exception
   */
  // @Test
  public void castTest() throws Exception {
    startTest();
    final File aqlFile = new File(AQL_DIR, "castTest.aql");
    // final File docsFile = new File (DOCS_DIR, "simpleDocs.del");

    setDataPath(TestConstants.TESTDATA_DIR);
    runOutputTypeTest(defaultDocsFile, aqlFile, System.out);
    runNonModularAQLTest(defaultDocsFile, aqlFile);

    // compareAgainstExpected (false);
    endTest();
  }

  /**
   * Test of cast expressions support in AQL.
   * 
   * @throws Exception
   */
  @Test
  public void dictionaryTest() throws Exception {
    startTest();
    final File aqlFile = new File(AQL_DIR, "dictionaryTest.aql");
    final File docsFile = new File(DOCS_DIR, "dictionaryTest.del");

    setDataPath(TestConstants.TESTDATA_DIR);
    runOutputTypeTest(docsFile, aqlFile, System.out);
    // runNonModularAQLTest (defaultDocsFile, aqlFile);

    // compareAgainstExpected (false);
    endTest();
  }

  /**
   * Test on how remap function handles empty html document or html document which is detagged to
   * empty string
   * <p>
   * Temporarily copied from DetaggerTests/remapEmptyDocumentTest.aql.
   * 
   * @throws Exception
   */
  @Test
  public void remapEmptyDocumentTest() throws Exception {
    startTest();
    final File aqlFile =
        new File(TestConstants.AQL_DIR, "DetaggerTests/remapEmptyDocumentTest.aql");
    final File docsFile =
        new File(TestConstants.TEST_DOCS_DIR, "detaggerTests/remapEmptyDocumentTest.del");

    runNonModularAQLTest(docsFile, aqlFile);

    // compareAgainstExpected (true);
  }

  /**
   * Test on AdjacentJoin
   * 
   * @throws Exception
   */
  @Test
  public void adjacentJoinTest() throws Exception {
    startTest();
    final File aqlFile = new File(AQL_DIR, "adjacentJoinTest.aql");
    // final File docsFile = new File (DOCS_DIR, "simpleDocs.del");

    runNonModularAQLTest(defaultDocsFile, aqlFile);

    // compareAgainstExpected (true);
  }

  /**
   * Test on AdjacentJoin
   * 
   * @throws Exception
   */
  @Test
  public void joinTest() throws Exception {
    startTest();
    final File aqlFile = new File(AQL_DIR, "joinTest.aql");
    final File docsFile = new File(DOCS_DIR, "simpleDocs.del");

    final File OUTFILE = new File(getCurOutputDir(), "results.txt");
    PrintStream out = new PrintStream(OUTFILE);

    runOutputTypeTest(docsFile, aqlFile, System.out);

    out.close();
    // compareAgainstExpected (true);
  }

  /**
   * Test case for a union of different combinations of Text and Span. Ensure that the type of the
   * output object is as claimed by the compiler.
   * <p>
   * Note: A failure would make ConsolidateNewTests.sda_28491 fail (for the ExcludeHighAmbigMatches
   * view)
   * <p>
   * There are two potential failures:
   * <ul>
   * <li>The output type is not determined correctly.
   * <li>The output object is not converted to the stated output type.
   * </ul>
   * When a failure occurs, with a message such as "AssertionError: Type mismatch: expect Span, got
   * Text". First determine if the expected type is correct. Then determine why the object does not
   * confirm.
   * 
   * @throws Exception
   */
  @Test
  public void unionTest() throws Exception {
    startTest();
    final File aqlFile = new File(AQL_DIR, "unionTest.aql");
    final File docsFile = new File(DOCS_DIR, "simpleDocs.del");

    final File OUTFILE = new File(getCurOutputDir(), "results.txt");
    PrintStream out = new PrintStream(OUTFILE);

    runOutputTypeTest(docsFile, aqlFile, out);

    out.close();
    compareAgainstExpected(false);
  }

  /**
   * Test case for a union bug that manifest in the NEEValTest.runAnnotTest, where union of (County,
   * State) and union of (State, County) gives different results.
   * 
   * @throws Exception
   */
  // @Test
  public void unionBugTest() throws Exception {
    startTest();
    final File aqlFile = new File(AQL_DIR, "unionBugTest.aql");
    final File docsFile = new File(DOCS_DIR, "simpleDocs.del");

    final File OUTFILE = new File(getCurOutputDir(), "results.txt");
    PrintStream out = new PrintStream(OUTFILE);

    runOutputTypeTest(docsFile, aqlFile, out);

    out.close();
    compareAgainstExpected(false);
  }

  /**
   * Test case for correct conversions between string and text
   * 
   * @throws Exception
   */
  @Test
  public void stringTextTest() throws Exception {
    startTest();
    final File aqlFile = new File(AQL_DIR, "stringTextTest.aql");
    final File docsFile = new File(DOCS_DIR, "simpleDocs.del");

    final File OUTFILE = new File(getCurOutputDir(), "results.txt");
    PrintStream out = new PrintStream(OUTFILE);

    runOutputTypeTest(docsFile, aqlFile, out);

    out.close();
    compareAgainstExpected(false);
  }

  /**
   * Test case to verify that join predicates check for equal docText object
   * 
   * @throws Exception
   */
  @Test
  public void sameDocTest() throws Exception {
    startTest();
    System.out.println("----------------");
    final File aqlFile = new File(AQL_DIR, "sameDocTest.aql");
    final File docsFile = new File(DOCS_DIR, "sameDocTest.del");

    final File OUTFILE = new File(getCurOutputDir(), "results.txt");
    PrintStream out = new PrintStream(OUTFILE);

    runOutputTypeTest(docsFile, aqlFile, out);

    out.close();
    compareAgainstExpected(false);
  }

  /**
   * Test case for correct conversions between string and text
   * 
   * @throws Exception
   */
  @Test
  public void bug20573Test() throws Exception {
    startTest();
    final File aqlFile = new File(AQL_DIR, "bug20573.aql");
    final File docsFile = new File(DOCS_DIR, "dictionaryTest.del");

    final File OUTFILE = new File(getCurOutputDir(), "results.txt");
    PrintStream out = new PrintStream(OUTFILE);

    runOutputTypeTest(docsFile, aqlFile, out);

    out.close();
    compareAgainstExpected(false);
  }

  /**
   * Test of the Follows join predicate with RSE join. Looks for pairs of potential first and last
   * names within 10-50 chars of each other.
   */
  @Test
  public void followsTest() throws Exception {
    startTest();
    String aogFileName = "testdata/aog/SpanTextTests/followsRSE.aog";
    final File docsFile = new File(DOCS_DIR, "personNameDocs.del");

    String[][] dictInfo = {{"dictionaries/first.dict", "testdata/dictionaries/first.dict"},
        {"dictionaries/last.dict", "testdata/dictionaries/last.dict"}};

    setWriteCharsetInfo(true);
    runAOGFile(docsFile, new File(aogFileName), dictInfo);

    System.err.printf("Comparing output files.\n");

    compareAgainstExpected(false);
  }

  // Utility methods

  private void runOutputTypeTest(File docsFile, File aqlFile, PrintStream out) throws Exception {
    // Parse and compile the AQL.
    compileAQL(aqlFile, TestConstants.TESTDATA_DIR);
    OperatorGraph og = instantiateOperatorGraph(Constants.GENERIC_MODULE_NAME);

    if (debug) {
      String moduleURI = getCurOutputDir().toURI().toString();
      TAM tam = TAMSerializer.load("genericModule", moduleURI);
      String aog = tam.getAog();
      System.err.print("AQL parse tree is:\n" + aog);
    }

    Map<String, TupleSchema> outputSchema = og.getOutputTypeNamesAndSchema();

    // Iterate over all documents
    DocReader reader = new DocReader(docsFile);
    while (reader.hasNext()) {
      Tuple doc = reader.next();
      out.println("------------------- New document ------------------------");
      if (debug)
        Log.debug(
            "SpanTextTests.runOutputTypeTest: ---------------------------------------- doc = %s",
            doc);

      Map<String, TupleList> annotations = og.execute(doc, null, null);

      // Iterate over all output views
      for (Map.Entry<String, TupleList> entry : annotations.entrySet()) {
        String outputName = entry.getKey();
        TupleList tups = entry.getValue();
        TupleSchema schema = outputSchema.get(outputName);
        out.printf("output view %s\n", schema);

        // Iterate over all tuples
        TLIter itr = tups.iterator();
        while (itr.hasNext()) {
          Tuple tup = itr.next();
          // out.printf ("\t %-50s\t", tup);

          // Iterate over all fields
          for (int i = 0; i < schema.size(); i++) {
            Object field = schema.getCol(tup, i);
            FieldType type = schema.getFieldTypeByIx(i);
            String fieldName = schema.getFieldNameByIx(i);
            String fieldClass;
            if (null != field) {
              fieldClass = field.getClass().getSimpleName();
            } else {
              fieldClass = null;
            }

            out.printf("\t %3d: %s:%s:\t %s(%s)\n", i, fieldName, type, fieldClass, field);
            assertTrue(String.format("Type mismatch: expect %s, got %s", type, fieldClass),
                (null == field) || type.checkType(field));

          } // fields
        } // tuples
      } // views
    } // docs
  }

  public void runTest(File docsFile, File aqlFile) throws Exception {
    startTest();
    // Parse the AQL file
    compileAQL(aqlFile, this.getDataPath());

    // String moduleURI = getCurOutputDir ().toURI ().toString ();
    // TAM tam = TAMSerializer.load ("genericModule", moduleURI);
    // String aog = tam.getAog ();
    // System.err.print ("AQL parse tree is:\n" + aog);

    OperatorGraph og = instantiateOperatorGraph(Constants.GENERIC_MODULE_NAME);

    // Try running the operator graph.
    System.err.print("Running plan...\n");

    int ndoc = 0;
    int nannot = 0;

    DocReader reader = new DocReader(docsFile);

    while (reader.hasNext()) {

      Tuple docTup = reader.next();

      Map<String, TupleList> results = og.execute(docTup, null, null);

      for (String outputName : results.keySet()) {
        nannot += results.get(outputName).size();
      }

      ndoc++;

      if (0 == ndoc % 1000) {
        System.err.printf("Produced %d annotations on %d documents.\n", nannot, ndoc);
      }
    }

    System.err.printf("Produced a total of %d annotations over %d documents.\n", nannot, ndoc);
  }

}
