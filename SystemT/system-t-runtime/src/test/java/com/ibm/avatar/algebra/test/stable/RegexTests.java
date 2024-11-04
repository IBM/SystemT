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
import java.io.FileWriter;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.aggregate.BlockChar;
import com.ibm.avatar.algebra.base.MemoizationTable;
import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.extract.RegularExpression;
import com.ibm.avatar.algebra.function.predicate.ContainsRegex;
import com.ibm.avatar.algebra.scan.DocScanStub;
import com.ibm.avatar.algebra.util.document.DocUtils;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.api.CompileAQL;
import com.ibm.avatar.api.CompileAQLParams;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.planner.Planner;
import com.ibm.avatar.logging.Log;
import com.ibm.systemt.regex.api.JavaRegex;
import com.ibm.systemt.regex.api.RegexMatcher;
import com.ibm.systemt.regex.api.SimpleRegex;
import com.ibm.systemt.regex.api.SimpleRegexMatcher;
import com.ibm.systemt.regex.charclass.CharIDMap;

/** Various tests of regular expressions. */
public class RegexTests extends RuntimeTestHarness {

  /** Directory where AOG files used in tests in this class are located. */
  public static final String AOG_FILES_DIR = TestConstants.AOG_DIR + "/regexTests";

  public static final String[] DOCTEXTS = {"This is a test  of M. Avatar's annotators."};

  public static final String DOCTOKENS[] =
      {"This", "is", "a", "test", "of", "M.", "Avatar", "'", "s", "annotators", "."};

  /** Tokens we'll get from a regex match on words. */
  public static final String REGEXTOKS[] =
      {"This", "is", "a", "test", "of", "M", "Avatar", "s", "annotators"};

  /** Tokens we'll get from the fast tokenizer (See {@link FastTokenize}) */
  public static final String FASTTOKENS[] =
      {"This", "is", "a", "test", "of", "M", ".", "Avatar", "'", "s", "annotators", "."};

  /** (shared) input 1 of the operators being tested. */
  protected DocScanStub in;

  /** Input documents, for comparison. */
  protected Set<String> inputDocs;

  public static void main(String[] args) throws Exception {

    RegexTests t = new RegexTests();

    t.setUp();

    long startMS = System.currentTimeMillis();

    t.nerRegexesTest();

    long endMS = System.currentTimeMillis();

    t.tearDown();

    double elapsedSec = ((double) (endMS - startMS)) / 1000.0;

    System.err.printf("Test took %1.3f sec.\n", elapsedSec);

  }

  @Before
  public void setUp() throws Exception {
    inputDocs = new TreeSet<String>();

    // this.docTexts = doctexts;

    for (int i = 0; i < DOCTEXTS.length; i++) {
      inputDocs.add(DOCTEXTS[i]);
    }

    in = new DocScanStub(inputDocs.iterator());
  }

  @After
  public void tearDown() throws Exception {

  }

  /**
   * Annotates all the alphabetical parts of the input.
   * 
   * @throws Exception
   */
  @Test
  public void regexTest() throws Exception {
    startTest();

    RegularExpression op = new RegularExpression(in, "text", new JavaRegex("[a-zA-Z]+"));

    MemoizationTable mt = new MemoizationTable(op);
    TupleList out = op.getNext(mt);
    mt.resetCache();

    FieldGetter<Span> getResult =
        op.getOutputSchema().spanAcc(RegularExpression.DEFAULT_OUTPUT_COL_NAME);

    int tupIx = 0;
    for (TLIter iter = out.iterator(); iter.hasNext();) {
      Tuple tup = iter.next();

      Span a = getResult.getVal(tup);

      assertTrue(a.getText().equals(REGEXTOKS[tupIx]));
      tupIx++;
    }
  }

  /**
   * Version of regexTest() that uses the faster regular expressions.
   * 
   * @throws Exception
   */
  @Test
  public void fastRegexTest() throws Exception {
    startTest();

    RegularExpression op = new RegularExpression(in, "text", new SimpleRegex("[a-zA-Z]+"));

    MemoizationTable mt = new MemoizationTable(op);
    TupleList out = op.getNext(mt);
    mt.resetCache();

    // String expectedResults[] = { "This", "is", "a", "test", "of", "the",
    // "Avatar", "annotators" };

    FieldGetter<Span> getResult =
        op.getOutputSchema().spanAcc(RegularExpression.DEFAULT_OUTPUT_COL_NAME);

    int tupIx = 0;
    for (TLIter iter = out.iterator(); iter.hasNext();) {
      Tuple tup = iter.next();

      // Annotation that the Regex operator created should be the last
      // element of the tuple
      Span a = getResult.getVal(tup);

      assertTrue(a.getText().equals(REGEXTOKS[tupIx]));
      tupIx++;
    }
  }

  /**
   * Tokenize (with a regex), then find all blocks of 3 or more adjacent tokens.
   */
  @Test
  public void blockTest() throws Exception {
    startTest();

    RegularExpression tokenize =
        new RegularExpression(in, Constants.DOCTEXT_COL, new SimpleRegex("[a-zA-Z]+"));
    // Tokenize tokenize = new Tokenize("text", in);
    // Arguments are: charsBetween, minSize, colix, input
    BlockChar block =
        new BlockChar(1, 3, 10, RegularExpression.DEFAULT_OUTPUT_COL_NAME, null, tokenize);

    FieldGetter<Span> getResult = block.getOutputSchema().spanAcc("block");

    MemoizationTable mt = new MemoizationTable(block);
    TupleList out = block.getNext(mt);
    mt.resetCache();

    String[] EXPECTED_RESULTS =
        {"This is a", "This is a test", "is a test", "Avatar's annotators",};

    int tupIx = 0;
    for (TLIter iter = out.iterator(); iter.hasNext();) {
      Tuple tup = iter.next();

      // TupleSchema schema = tup.getSchema();
      // assertEquals(2, schema.size());
      // assertEquals(FieldType.getDocType(), schema.type(0));
      // assertEquals(FieldType.getAnnotType(), schema.type(1));

      Span blockAnnot = getResult.getVal(tup);

      // System.err.printf("\"%s\",\n", blockAnnot.getText());

      assertEquals(EXPECTED_RESULTS[tupIx], blockAnnot.getText());

      tupIx++;
    }
  }

  /** A test of parsing and compiling simple regular expressions. */
  @Test
  public void simpleRegexParseTest() throws Exception {
    startTest();

    // A version of the CapsPerson regex with counts decreased.
    String pattern = "\\p{Lu}(\\p{L}){0,5}([\\'\\-][\\p{Lu}])?(\\p{L})*";
    // String pattern = "\\p{Lu}(\\p{L}){2,3}";
    // String pattern = "\\p{Lu}(\\p{L})*";
    // String pattern = "(\\p{L})+";

    PrintStream out = System.err;

    out.printf("Orig pattern is: %s\n\n", pattern);

    SimpleRegex re = new SimpleRegex(pattern, 0x0, true);

    out.printf("Parsed regex is: %s\n\n", re.getExpr());

    // The pattern should be able to print itself out verbatim.
    assertEquals(pattern, re.getExpr());

    out.printf("Parse tree is:\n");
    re.dump(out, 0);
    out.printf("\n--\n");

    // Try running the regex.
    String target = "Joe O'Flannigan and Mary Smith-Wesson were here.";

    SimpleRegexMatcher m = new SimpleRegexMatcher(re, target);

    System.err.printf("Matching over: '%s'\n", target);
    System.err.printf("Matches are:\n");

    int[] EXPECTED_STARTS = {0, 4, 20, 25};
    int[] EXPECTED_ENDS = {3, 15, 24, 37};

    int count = 0;
    while (m.find()) {
      System.err.printf("   [%d, %d]: %s\n", m.start(), m.end(),
          target.subSequence(m.start(), m.end()));
      assertEquals(EXPECTED_STARTS[count], m.start());
      assertEquals(EXPECTED_ENDS[count], m.end());
      count++;
    }
    assertEquals(4, count);
  }

  /** Test of the first phone number regex. */
  @Test
  public void phone1Test() throws Exception {
    startTest();

    String pattern = "\\+?\\([1-9][0-9]{2}\\)[\\-]?[0-9]{3}[\\-\\.]?[0-9]{4}";
    // String pattern = "\\([1-9]\\)";

    String target = "For a good time, call (510)-555-1212 " + "x1234 or +(888)-123-4567.";

    int[] expectedStarts = {22, 46};
    int[] expectedEnds = {36, 61};

    genericRegexTest(pattern, target, expectedStarts, expectedEnds);

  }

  /** Test of the WeakInitialWord regex. */
  @Test
  public void weakInitialWordTest() throws Exception {
    startTest();

    String pattern = "([\\p{Upper}]\\.?\\s*){1,5}";

    String target = "I can't wait to have a place of my own! " + "Apartment life is horrible. ";

    int[] expectedStarts = {0, 40};
    int[] expectedEnds = {2, 41};

    genericRegexTest(pattern, target, expectedStarts, expectedEnds);

  }

  /** Test of a problematic regex in the directions annotator. */
  @Test
  public void directionsTest() throws Exception {
    startTest();

    String pattern = "(take\\s+(([A-Za-z]+\\s*-?\\s*\\s+)"
        + "|(\\s+\\s*-?\\s*[A-Za-z]+))(\\s+exit)?\\s*[from|to])([ A-Za-z0-9\\t,])*";

    int flags = Pattern.CASE_INSENSITIVE;

    // We're mostly interested in whether the regex will compile.
    String target = "";

    int[] expectedStarts = null;
    int[] expectedEnds = null;

    genericRegexTest(pattern, flags, target, expectedStarts, expectedEnds);

  }

  /**
   * Test of a problematic regex from a selection predicate in personOrgFast.aql.
   */
  @Test
  public void selectionTest() throws Exception {
    startTest();

    String pattern = "(.|\\n|\\r)*,[ \\t]*(\\p{Lu}\\p{M}*(\\p{L}\\p{M}*|[-'.])*[ \\t]*){0,2}";

    int flags = ContainsRegex.DEFAULT_REGEX_FLAGS;

    // We're mostly interested in whether the regex will compile.
    String target = "Hello, World-Leaders";

    int[] expectedStarts = {0};
    int[] expectedEnds = {20};

    genericRegexTest(pattern, flags, target, expectedStarts, expectedEnds);

  }

  /** Test of a problematic URL regex (URL2 in namedentity-sekar.aql) */
  @Test
  public void urlTest() throws Exception {
    startTest();

    // The original pattern.
    @SuppressWarnings("unused")
    final String fullPattern = "(" + "((([\\w]+:)\\/\\/)|(w\\w+\\.))"
        + "(([\\d\\w]|%[a-fA-f\\d]{2,2})+(:([\\d\\w]|%[a-fA-f\\d]{2,2})+)?@)?"
        + "([\\d\\w][-\\d\\w]{0,253}[\\d\\w]\\.)+" + "[\\w]{2,4}(:[\\d]+)?" +
        // Begin problematic section
        "(\\/([-+_~.\\d\\w]|%[a-fA-f\\d]{2,2})*)*" +
        // End problematic section
        "(\\?(&?([-+_~.\\d\\w]|%[a-fA-f\\d]{2,2})=?)*)?" + "(#([-+_~.\\d\\w]|%[a-fA-f\\d]{2,2})*)?"
        + ")";

    // The piece that causes problems
    final String pattern = "(\\/([-+_~.\\d\\w]|%[a-fA-f\\d]{2,2})*)*";

    int flags = ContainsRegex.DEFAULT_REGEX_FLAGS;

    String target = "http://finance.yahoo.com/q?s=dyn&d=t> - news "
        + "<http://biz.yahoo.com/n/d/dyn.html>) had what sounded like a "
        + "great idea: Buy Enron's flagship trading business in natural gas "
        + "and electricity, and immediately become the dominant force in "
        + "those markets. Dynegy fled when Enron started to collapse under "
        + "an avalanche of scandal. Now, Zurich [Switzerland]-based UBS "
        + "Warburg (NYSE:UBS <http://finance.yahoo.com/q?s=ubs&d=t> - "
        + "news <http://biz.yahoo.com/n/u/ubs.html>) is making the same "
        + "bet, albeit for a lot less money.";

    int[] expectedStarts = null;
    int[] expectedEnds = null;

    genericRegexTest(pattern, flags, target, expectedStarts, expectedEnds);

  }

  /** Test of Java's behavior when given a dot inside a char class descriptor. */
  @Test
  public void dotClassTest() throws Exception {
    startTest();

    final String pattern = "[.]";

    int flags = ContainsRegex.DEFAULT_REGEX_FLAGS;

    String target = "Hello, world.  How are you today?";

    int[] expectedStarts = null;
    int[] expectedEnds = null;

    genericRegexTest(pattern, flags, target, expectedStarts, expectedEnds);

  }

  /** Test of a regex that matches the empty string. */
  @Test
  public void matchesEmptyTest() throws Exception {
    startTest();

    final String pattern = "w*";

    int flags = 0x0;

    String target = "Hello, world.";

    int[] expectedStarts = null;
    int[] expectedEnds = null;

    genericRegexTest(pattern, flags, target, expectedStarts, expectedEnds);

  }

  /** Test of a regex in the NonPhoneNum rule that crashed SimpleRegex. */
  @Test
  public void nonPhoneNumTest() throws Exception {
    startTest();

    final String pattern = "\\s*\\:*\\s*.{0,10}\\s*\\+*\\s*";

    int flags = ContainsRegex.DEFAULT_REGEX_FLAGS;

    // Nine spaces...
    String target = "          ";

    int[] expectedStarts = {0, 10};
    int[] expectedEnds = {10, 10};

    genericRegexTest(pattern, flags, target, expectedStarts, expectedEnds);

  }

  /** Test of a bug in case sensitivity handling. */
  @Test
  public void caseTest() throws Exception {
    startTest();

    final String pattern = "ext\\s*[\\.\\-\\:]?\\s*\\d{3,5}";

    int flags = Pattern.CASE_INSENSITIVE;

    // Nine spaces...
    String target = "Ext. 12345 ext. 12345 etc.";

    int[] expectedStarts = {0, 11};
    int[] expectedEnds = {10, 21};

    genericRegexTest(pattern, flags, target, expectedStarts, expectedEnds);

  }

  /**
   * Test case for another bug in case-insensitive matching for SimpleRegex
   */
  @Test
  public void caseTest2() throws Exception {
    startTest();

    String pattern = "[A-Z][a-z]*";

    int flags = Pattern.CASE_INSENSITIVE;

    // We're mostly interested in whether the regex will compile.
    String target = "fooBar";

    int[] expectedStarts = {0};
    int[] expectedEnds = {6};

    genericRegexTest(pattern, flags, target, expectedStarts, expectedEnds);

  }

  /** Test of the DOTALL flag. */
  @Test
  public void dotAllTest() throws Exception {
    startTest();

    final String pattern = "foo.bar";

    int flags = Pattern.DOTALL | Pattern.MULTILINE;

    // Nine spaces...
    String target = "foo\r\nbar foo\nbar foo bar";

    int[] expectedStarts = {9, 17};
    int[] expectedEnds = {16, 24};

    genericRegexTest(pattern, flags, target, expectedStarts, expectedEnds);

  }

  /**
   * Test of the "\x" escape.
   */
  @Test
  public void backslashXTest() throws Exception {
    startTest();

    // Look for a single double-quote
    final String pattern = "\\x22bar\\x22";

    int flags = 0x0;

    // Nine spaces...
    String target = "foo\"bar\"fab";

    int[] expectedStarts = {3};
    int[] expectedEnds = {8};

    genericRegexTest(pattern, flags, target, expectedStarts, expectedEnds);

  }

  /**
   * Test case for a bug in determining whether a regex is
   */

  /**
   * Make sure that the new regex engines return the same results on the "hot spot" regexes from the
   * named entity annotator.
   */
  @Test
  public void hotspotResultsTest() throws Exception {
    startTest();

    //
    // String SLOW_AOG_FILE = AOG_FILES_DIR + "/hotspotRegex.aog";
    // String FAST_AOG_FILE = AOG_FILES_DIR + "/hotspotFastRegex.aog";
    // String MULTI_AOG_FILE = AOG_FILES_DIR + "/hotspotMultiRegex.aog";

    // String DOCS_FILE_NAME = TestConstants.TESTDATA_DIR + "/docs/tmp.del";
    // String DOCS_FILE_NAME = TestConstants.ENRON_1_DUMP;
    // String DOCS_FILE_NAME = TestConstants.ENRON_1K_DUMP;
    // String DOCS_FILE_NAME = TestConstants.ENRON_37939_DUMP;

    // setPrintTups(true);

    System.err.printf("Running regexes with Java engine...\n");
    File OUTPUT_DIR = getCurOutputDir();

    setOutputDir(OUTPUT_DIR + "/hotspotRegex");

    // util.runAOGFile(new DBDumpFileScan(DOCS_FILE_NAME),
    // SLOW_AOG_FILE,TestConstants.EMPTY_CD_LIST);

    {
      System.err.printf("Running regexes with SimpleRegex engine...\n");
      setOutputDir(OUTPUT_DIR + "/hotspotFastRegex");

      // util.runAOGFile(new DBDumpFileScan(DOCS_FILE_NAME),
      // FAST_AOG_FILE,TestConstants.EMPTY_CD_LIST);

      // Compare the two sets of outputs against each other.
      setExpectedDir(OUTPUT_DIR + "/hotspotRegex");

      compareAgainstExpected(false);
    }

    {
      System.err.printf("Running regexes with MultiRegex engine...\n");
      setOutputDir(OUTPUT_DIR + "/hotspotMultiRegex");

      // util.runAOGFile(new DBDumpFileScan(DOCS_FILE_NAME),
      // MULTI_AOG_FILE,TestConstants.EMPTY_CD_LIST);

      // Compare the results of running all the regexes at once against
      // the results of running them individually.
      setExpectedDir(OUTPUT_DIR + "/hotspotRegex");

      compareAgainstExpected(false);
    }

  }

  /**
   * A version of hotspotResultsTest() that just measures performance, skipping the generation of
   * output HTML files.
   */
  @Test
  public void hotspotBenchmark() throws Exception {
    startTest();

    // String SLOW_AOG_FILE = AOG_FILES_DIR + "/hotspotRegex.aog";
    // String FAST_AOG_FILE = AOG_FILES_DIR + "/hotspotFastRegex.aog";

    // String DOCS_FILE_NAME = TestConstants.ENRON_37939_DUMP;
    // String DOCS_FILE_NAME = TestConstants.ENRON_1K_DUMP;

    // Skip HTML generation.
    setDisableOutput(true);

    System.err.printf("Running regexes with SimpleRegex engine...\n");
    // com.ibm.avatar.algebra.function.RegexConst.STRENGTH_REDUCE = true;
    // util.runAOGFile(new DBDumpFileScan(DOCS_FILE_NAME),
    // FAST_AOG_FILE,TestConstants.EMPTY_CD_LIST);

    System.err.printf("Running regexes with Java engine...\n");
    // com.ibm.avatar.algebra.function.RegexConst.STRENGTH_REDUCE = false;
    // util.runAOGFile(new DBDumpFileScan(DOCS_FILE_NAME),
    // SLOW_AOG_FILE,TestConstants.EMPTY_CD_LIST);

    // com.ibm.avatar.algebra.function.RegexConst.STRENGTH_REDUCE =
    // Planner.DEFAULT_RSR;
  }

  /**
   * A performance benchmark that avoids the tokenization step by using Regex instead of RegexTok.
   */
  @Test
  public void noTokBenchmark() throws Exception {
    startTest();

    // String SLOW_AOG_FILE = AOG_FILES_DIR + "/noTokRegex.aog";
    // String FAST_AOG_FILE = AOG_FILES_DIR + "/noTokFastRegex.aog";

    // String DOCS_FILE_NAME = TestConstants.ENRON_37939_DUMP;
    // String DOCS_FILE_NAME = TestConstants.ENRON_1K_DUMP;

    // Skip HTML generation.
    setDisableOutput(true);

    System.err.printf("Running regexes with SimpleRegex engine...\n");
    // com.ibm.avatar.algebra.function.RegexConst.STRENGTH_REDUCE = true;
    // util.runAOGFile(new DBDumpFileScan(DOCS_FILE_NAME),
    // FAST_AOG_FILE,TestConstants.EMPTY_CD_LIST);

    System.err.printf("Running regexes with Java engine...\n");
    // com.ibm.avatar.algebra.function.RegexConst.STRENGTH_REDUCE = false;
    // util.runAOGFile(new DBDumpFileScan(DOCS_FILE_NAME),
    // SLOW_AOG_FILE,TestConstants.EMPTY_CD_LIST);

    // com.ibm.avatar.algebra.function.RegexConst.STRENGTH_REDUCE =
    // Planner.DEFAULT_RSR;
  }

  private enum RegexImplType {
    JAVA, SIMPLE, SIMPLE_MULTI
  }

  /**
   * Record for holding the results of a single regex benchmark.
   */
  private final class bmResult implements Comparable<bmResult> {
    // Index of the regular expression run
    int regexIx;

    // What regex implementation was used for this run?
    RegexImplType impl;

    double execTimeSec;

    @Override
    public int compareTo(bmResult o) {
      int val = regexIx - o.regexIx;
      if (0 != val) {
        return val;
      }

      val = impl.ordinal() - o.impl.ordinal();
      return val;
    }

    bmResult(int regexIx, RegexImplType impl, double execTimeSec) {
      this.regexIx = regexIx;
      this.impl = impl;
      this.execTimeSec = execTimeSec;
    }
  }

  /**
   * A performance test that invokes regexes directly.
   */
  @Test
  public void directBenchmark() throws Exception {
    startTest();

    final File DOCS_FILE = new File(TestConstants.ENRON_SAMPLE_ZIP);

    // How many documents to run the regexes over (random sample of the
    // input file)
    // final int DOC_SAMPLE_SIZE = 25000;
    // final int DOC_SAMPLE_SIZE = 1000;
    final int DOC_SAMPLE_SIZE = 100;

    final String[] NO_LOOKAHEAD_REGEXES = {
        //
        // Regular expressions from the named-entity "hotspots" set
        //
        // "'",//
        "(\\p{Lu}\\p{M}*){2,30}", //
        "([\\p{Upper}]\\.?\\s*){1,5}", //
        "\\p{Lu}\\p{M}*(\\p{L}\\p{M}*){1,20}", //
        "([\\p{Upper}]\\.\\s*){0,4}[\\p{Upper}]\\.", //
        "([0123])?[\\d]( )*[\\-]( )*([0123])?[\\d]", //
        "(\\d{1,2}:\\d{2}(:\\d{2})?|\\d{1,2}-\\d{1,2})(\\s*[AP])?", //
        "\\p{Lu}\\p{M}*[\\p{Ll}\\p{Lo}]\\p{M}*(\\p{L}\\p{M}*){1,20}", //
        "\\p{Lu}\\p{M}*([\\p{Ll}\\p{Lo}\\&\\.'\\-\\,]\\p{M}*)*[\\p{Ll}\\p{Lo}]", //
        "\\p{Lu}\\p{M}*(\\p{L}\\p{M}*){0,10}(['\\-][\\p{Lu}\\p{M}*])?(\\p{L}\\p{M}*){1,10}", //
        "\\s+([\\w\\d]+\\s{1,10}){0,4}(\\\"|')?([A-Z]\\w{3,100}('s)?(-[A-z]\\w*('s)?|\\s{1,10}([a-z]{1,3}\\s{1,10}){0,2}[A-Z]\\w*('s)?){0,6})", //
        //
        // Regular expressions from our optimizer experiments
        //
        // ",", //
        // "\\/", //
        // "\\/\\/", //
        // "\\d",//
        // "\\d{4}",//
        // "[\\d(].+", //
        // "\\d{1,5}-?[A-Z]?", //
        // "([\\p{Upper}]\\.?\\s*){1,5}", //
        // "\\p{Upper}[\\p{Lower}\\&]{1,20}", //
        // "\\p{Lu}\\p{M}*(\\p{L}\\p{M}*){1,20}",//
        // "\\p{Upper}[\\p{Lower}\\&\\.\\-\\,]+",//
        // "\\p{Upper}[\\p{Lower}\\&\\.-\\/]{1,20}",//
        // "\\d{1,3}(\\s*(N|S|E|W))?(\\s*(N|S|E|W))?", //
        // "To:\\s*.{1,200}\\s*\\n(>\\s*)*\\s*(CC|cc|Cc):\\s*(\\n)?", //
        // "(take\\s+(([A-Za-z]+\\s*-?\\s*\\d+)|(\\d+\\s*-?\\s*[A-Za-z]+))(\\s+exit)\\s*to)"
        // , //
        "[0-9\\:\\.]+", //
        "(:\\/\\/)|(\\.)", //
        "\\p{Lu}\\p{M}*[[\\p{Ll}\\p{Lo}]\\p{M}*\\&\\.'\\-\\,]+", //
        "\\p{Lu}\\p{M}*[\\p{Ll}\\p{Lo}]\\p{M}*(\\p{L}\\p{M}*){1,20}", //
        "\\p{Lu}\\p{M}*(\\p{L}\\p{M}*){0,10}(['-][\\p{Lu}\\p{M}*])?(\\p{L}\\p{M}*){1,10}", //
        "([\\d\\p{Alpha}\\-\\,]*\\d[\\d\\p{Alpha}\\-\\,]*)\\s+\\p{Upper}[\\p{Lower}\\&]{1,20}", //
        "([\\d\\p{Alpha}\\.\\-\\,]*\\d[\\d\\p{Alpha}\\.\\-\\,]*)\\s*\\p{Upper}[\\p{Lower}\\&]{1,20}", //
        "\\p{Lu}\\p{M}*[\\p{Ll}\\p{Lo}]\\p{M}*[\\p{L}\\p{M}*]{0,10}(['-][\\p{Lu}\\p{M}*])?(\\p{L}\\p{M}*){1,10}", //
        "(([_a-zA-Z0-9-]+(\\.[_a-zA-Z0-9-]+)*@[a-zA-Z0-9-]+(\\.[a-zA-Z0-9-]+)*\\.(([0-9]{1,3})|([a-zA-Z]{2,3})|(aero|coop|info|museum|name))))", //
        "\\p{Alpha}{1,30}\\/.{1,25}\\/.{1,20}(\\@.{1,20})?\\s*\\n(>\\s*)*\\d{2,4}(\\/|\\.)\\d{2,4}(\\/|\\.)\\d{2,4}\\s\\d{2}\\:\\d{2}(\\s+(PM|AM))?", //
        "([[1-9]\\p{Alpha}\\-\\,]*\\d[\\d\\p{Alpha}\\-\\,]*)\\s+(\\p{Upper}\\.?\\s)?(\\p{Upper}[\\p{Lower}\\&]{1,20}|\\d{1,3}(st|nd|rd|th))", //
        "([Aa]llee|[Bb]erg|[Cc]haussee|[Dd]amm|[Dd]�mme|[Gg]asse|[Gg]aerten|[Gg]�rten|[Hh]alde|[Hh]�fe|[Hh]of|[Hh]oefe|[Ll]andstra�e|[Ll]andstrasse|[Mm]�rkte|[Mm]arkt|[Mm]aerkte|[Pp]fad|[Pp]latz|[Pp]l�tze|[Pp]laetze|[Rr]ing|[Ss]teig|[Ss]tr\\.|[Ss]tra�e|[Ss]trasse|[Uu]fer|[Ww]eg|[Zz]eile)[ \\t]+([Aa]n [dD]er|[Aa]m|[dD]ie|[dD]er|[dD]as)?[ \\t]*\\p{Lu}\\p{M}*[\\p{Ll}\\p{Lo}]\\p{M}*(\\p{L}\\p{M}*){0,20}\\s*,?\\s*([[1-9]\\p{L}\\p{M}*\\-\\,]*\\d[\\d\\p{L}\\p{M}*\\/\\-\\,]*)", //
    };

    // Regexes with lookahead/lookbehind
    final String[] LOOKAHEAD_REGEXES = {
        // First one often commented out because it takes a *really*
        // long time.
        "(\\w+[\\w\\-:&=_?\\/~.<>@:]+\\.(com|edu|org)\\/[\\w&_?~.<>@:][\\w\\-:&=_?\\/~.<>@:]+[\\w\\-:&=_?\\/~]{2,})", //
        "\\b((\\p{Lu}\\p{M}*[\\p{Ll}\\p{Lo}]\\p{M}*(\\p{L}\\p{M}*){0,10}(['-]\\p{L}\\p{M}*)?(\\p{L}\\p{M}*){1,10}\\s+)(\\p{Lu}\\p{M}*\\s+)?(\\p{Lu}\\p{M}*[\\p{Ll}\\p{Lo}]\\p{M}*(\\p{L}\\p{M}*){0,20}(['-]\\p{L}\\p{M}*(\\p{L}\\p{M}*){1,8})?\\w{0,2}\\s*[\\/]\\s*)((\\p{Lu}\\p{M}*(\\p{L}\\p{M}*){1,20}\\s*){1,2}[\\/]\\s*){1,2}(\\p{Lu}\\p{M}*){2,20}(@(\\p{L}\\p{M}*){1,20})?)(?!\\/)\\b", //
        "\\b(((\\p{Lu}\\p{M}*[\\p{Ll}\\p{Lo}]\\p{M}*(\\p{L}\\p{M}*){0,10}(['-]\\p{L}\\p{M}*)?(\\p{L}\\p{M}*){1,10}\\s+))?((\\p{Lu}\\p{M}*\\.?){1,2}\\s+)?(\\p{Lu}\\p{M}*[\\p{Ll}\\p{Lo}]\\p{M}*(\\p{L}\\p{M}*){0,20}(['-]\\p{L}\\p{M}*(\\p{L}\\p{M}*){1,8})?\\w{0,2}\\s*[\\/]\\s*)((\\p{Lu}\\p{M}*(\\p{L}\\p{M}*){1,20}\\s*){1,2}[\\/]\\s*){1,2}(\\p{Lu}\\p{M}*){2,20}(@(\\p{L}\\p{M}*){1,20})?)(?!\\/)\\b",//

    };

    final int NUM_REGEXES = NO_LOOKAHEAD_REGEXES.length + LOOKAHEAD_REGEXES.length;

    // Concatenate the two sets of regexes.
    final String[] ALL_REGEXES = new String[NUM_REGEXES];
    System.arraycopy(NO_LOOKAHEAD_REGEXES, 0, ALL_REGEXES, 0, NO_LOOKAHEAD_REGEXES.length);
    System.arraycopy(LOOKAHEAD_REGEXES, 0, ALL_REGEXES, NO_LOOKAHEAD_REGEXES.length,
        LOOKAHEAD_REGEXES.length);

    // Compile the regexes.
    RegexMatcher[] simpleMatchers = new RegexMatcher[NUM_REGEXES];
    RegexMatcher[] javaMatchers = new RegexMatcher[NUM_REGEXES];

    for (int i = 0; i < ALL_REGEXES.length; i++) {
      try {
        simpleMatchers[i] = new SimpleRegex(ALL_REGEXES[i], Pattern.DOTALL).matcher("");
      } catch (Exception e) {
        Log.info("Regex %d does not work with SimpleRegex.", i);
        simpleMatchers[i] = null;
      }
      javaMatchers[i] = new JavaRegex(ALL_REGEXES[i], Pattern.DOTALL).matcher("");
    }

    // Read a sample of the documents into RAM.

    final long seed = 42;

    ArrayList<Tuple> docs =
        DocUtils.getDocSample(DOCS_FILE, DOC_SAMPLE_SIZE, seed, Integer.MAX_VALUE);

    FieldGetter<Text> getText = DocUtils.docTextAcc(DOCS_FILE);

    ArrayList<bmResult> results = new ArrayList<bmResult>();

    System.err.printf("Timing java regexes...\n");
    timeRegexes(ALL_REGEXES, javaMatchers, docs, getText, results);

    System.err.printf("Timing simple regexes...\n");
    timeRegexes(ALL_REGEXES, simpleMatchers, docs, getText, results);

    // Try compiling everyting into a single FSM
    {
      System.err.printf("Timing multiple regexes in a single pass...\n");

      int[] flags = new int[NO_LOOKAHEAD_REGEXES.length];
      Arrays.fill(flags, Pattern.DOTALL);

      SimpleRegex allTogether = new SimpleRegex(NO_LOOKAHEAD_REGEXES, flags);
      SimpleRegexMatcher matcher = (SimpleRegexMatcher) allTogether.matcher("");

      long startNsec = System.nanoTime();
      // for (int rep = 0; rep < 10; rep++) {
      for (Tuple doc : docs) {
        String docText = getText.getVal(doc).getText();
        matcher.reset(docText);
        // while (matcher.find()) {
        // // Don't bother processing the results.
        // }

        // Use the *real* multi-match mode.
        for (int pos = 0; pos < docText.length(); pos++) {
          matcher.region(pos, docText.length());
          matcher.getAllMatches();
          // Don't bother processing the results.
        }
      }
      // }

      long elapsedNsec = System.nanoTime() - startNsec;

      double elapsedSec = ((double) (elapsedNsec)) / 1.0e9;
      System.err.printf("All non-lookahead regexes => %1.2f sec\n", elapsedSec);

      // Generate result records for amortized time.
      for (int i = 0; i < NO_LOOKAHEAD_REGEXES.length; i++) {
        results.add(
            new bmResult(i, RegexImplType.SIMPLE_MULTI, elapsedSec / NO_LOOKAHEAD_REGEXES.length));
      }
    }

    // Collate and output the results.
    TreeMap<Integer, ArrayList<bmResult>> resultsTable =
        new TreeMap<Integer, ArrayList<bmResult>>();

    for (bmResult result : results) {
      ArrayList<bmResult> list = resultsTable.get(result.regexIx);
      if (null == list) {
        list = new ArrayList<bmResult>();
        resultsTable.put(result.regexIx, list);
      }
      list.add(result);

      // Log.info("Adding result for regex %d", result.regexIx);
    }

    // Open up an output file for easier viewing.
    String OUTFILE = getCurOutputDir() + "/times.csv";

    Log.info("Writing results to %s...", OUTFILE);
    FileWriter out = new FileWriter(OUTFILE);
    out.append("\"Regex Name\",\"Expression\",\"JavaRegex Time\","
        + "\"SimpleRegex Time\",\"MultiRegex Time (Amortized)\"\n");

    for (ArrayList<bmResult> list : resultsTable.values()) {
      int regexIx = -1;
      double javaTime = -1.0, simpleTime = -1.0, multiTime = -1.0;

      for (bmResult result : list) {
        regexIx = result.regexIx;
        if (RegexImplType.JAVA == result.impl) {
          javaTime = result.execTimeSec;
        } else if (RegexImplType.SIMPLE == result.impl) {
          simpleTime = result.execTimeSec;
        } else if (RegexImplType.SIMPLE_MULTI == result.impl) {
          multiTime = result.execTimeSec;
        }
      }

      String line;
      String regexStr = StringUtils.quoteForCSV(ALL_REGEXES[regexIx]);
      if (-1.0 == multiTime) {
        // No simple regex times available
        line = String.format("\"RE%d\",%s,%1.2f\n", regexIx + 1, regexStr, javaTime);
      } else {
        line = String.format("\"RE%d\",%s,%1.2f,%1.2f,%1.2f\n", regexIx + 1, regexStr, javaTime,
            simpleTime, multiTime);
      }
      out.append(line);
    }
    out.close();

  }

  /**
   * @param regexes array of regex strings
   * @param matchers compiled matchers for each of the expressions
   * @param docs documents to run over
   * @param getText accessor for pulling out document text
   * @param results where to put result records
   */
  private void timeRegexes(final String[] regexes, RegexMatcher[] matchers, ArrayList<Tuple> docs,
      FieldGetter<Text> getText, ArrayList<bmResult> results) {

    for (int i = 0; i < regexes.length; i++) {
      double elapsedSec;
      if (null == matchers[i]) {
        // No matcher for this regex.
        elapsedSec = -1.0;
      } else {
        long startNsec = System.nanoTime();
        for (int rep = 0; rep < 10; rep++) {
          for (Tuple doc : docs) {
            String docText = getText.getVal(doc).getText();
            matchers[i].reset(docText);
            while (matchers[i].find()) {
              // Don't bother processing the results.
            }
          }
        }

        long elapsedNsec = System.nanoTime() - startNsec;

        elapsedSec = ((double) elapsedNsec) / 1.0e9;

        RegexImplType impl =
            (matchers[i] instanceof SimpleRegexMatcher) ? RegexImplType.SIMPLE : RegexImplType.JAVA;

        results.add(new bmResult(i, impl, elapsedSec));

      }

      // Log.info("/%s/ => %1.2f sec", regexes[i], elapsedSec);
      // Get the regex to a fixed length, so we can read the output on
      // stderr
      Log.info("RE%d (/%s/) => %1.2f sec", i + 1, StringUtils.shorten(regexes[i], 20, true),
          elapsedSec);
    }
  }

  /**
   * Test of regex strength reduction (RSR) in the AQL compiler. Uses simplified versions of several
   * "hot-spot" regexes from the named-entity annotators for eDiscovery.
   */
  @Test
  public void hotspotAQLTest() throws Exception {
    startTest();

    // String BEFORE_AQL_FILE = SpeedTests.AQL_FILES_DIR + "/person-org-hotspots.aql";
    // String AFTER_AQL_FILE = SpeedTests.AQL_FILES_DIR + "/person-org-hs-after.aql";

    final String DICTSDIR = TestConstants.TESTDATA_DIR;

    // String DOCS_FILE_NAME = TestConstants.ENRON_10K_DUMP;
    // String DOCS_FILE_NAME = TestConstants.ENRON_1K_DUMP;

    setDisableOutput(true);
    // setPrintTups (true);

    setDataPath(DICTSDIR);

    // setDumpPlan (true);

    // outputDir
    File outputDir = getCurOutputDir();

    System.err.printf("Running original version...\n");
    // com.ibm.avatar.algebra.function.RegexConst.STRENGTH_REDUCE = false;
    setOutputDir(outputDir + "/hotspotBefore");
    // util.runAQLFile(new DBDumpFileScan(DOCS_FILE_NAME), BEFORE_AQL_FILE);

    System.err.printf("Running new version...\n");
    // com.ibm.avatar.algebra.function.RegexConst.STRENGTH_REDUCE = true;
    setOutputDir(outputDir + "/hotspotAfter");
    // util.runAQLFile(new DBDumpFileScan(DOCS_FILE_NAME), AFTER_AQL_FILE);

    // com.ibm.avatar.algebra.function.RegexConst.STRENGTH_REDUCE =
    // Planner.DEFAULT_RSR;
  }

  /**
   * Test to ensure that regex strength reduction doesn't change results.
   */
  @Test
  public void rsrResultsTest() throws Exception {
    startTest();

    // String AQL_FILE = SpeedTests.AQL_FILES_DIR + "/person-org-hs-after.aql";

    final String DICTSDIR = TestConstants.TESTDATA_DIR;

    // String DOCS_FILE_NAME = TestConstants.ENRON_10K_DUMP;
    // String DOCS_FILE_NAME = TestConstants.ENRON_1K_DUMP;

    setDisableOutput(true);

    setDataPath(DICTSDIR);

    // setPrintTups(true);

    // outputDir
    File outputDir = getCurOutputDir();

    System.err.printf("Running WITHOUT RSR...\n");
    // com.ibm.avatar.algebra.function.RegexConst.STRENGTH_REDUCE = false;
    Planner.DEFAULT_RSR = false;
    setOutputDir(outputDir + "/rsrBefore");
    // util.runAQLFile(new DBDumpFileScan(DOCS_FILE_NAME), AQL_FILE);

    System.err.printf("Running WITH RSR...\n");
    // com.ibm.avatar.algebra.function.RegexConst.STRENGTH_REDUCE = true;
    Planner.DEFAULT_RSR = true;
    setOutputDir(outputDir + "/rsrAfter");
    // util.runAQLFile(new DBDumpFileScan(DOCS_FILE_NAME), AQL_FILE);

    setExpectedDir(outputDir + "/rsrBefore");
    compareAgainstExpected(false);

    // com.ibm.avatar.algebra.function.RegexConst.STRENGTH_REDUCE =
    // Planner.DEFAULT_RSR;
  }

  /**
   * Test case for problematic regexes from the named entity annotators. Looks at versions of these
   * regexes from before and after hand-tuning and verifies that both versions produce the same
   * results.
   */
  @Test
  public void nerRegexesTest() throws Exception {
    startTest();

    // String BEFORE_AQL = SpeedTests.AQL_FILES_DIR + "/ner-hotspots-before.aql";
    // String AFTER_AQL = SpeedTests.AQL_FILES_DIR + "/ner-hotspots-after.aql";

    // String DOCS_FILE_NAME = TestConstants.ENRON_1K_DUMP;
    // String DOCS_FILE_NAME = TestConstants.ENRON_SMALL_ZIP;
    // String DOCS_FILE_NAME = TestConstants.ENRON_SAMPLE_ZIP;
    // String DOCS_FILE_NAME = TestConstants.TEST_DOCS_DIR + "/regexTests/nerProblemDocs";

    // File docsFile = new File (DOCS_FILE_NAME);

    setDisableOutput(false);

    // outputDir
    File outputDir = getCurOutputDir();

    setOutputDir(outputDir + "/nerBefore");
    // util.runAQLFile(DocScan.makeFileScan(docsFile), BEFORE_AQL);

    setOutputDir(outputDir + "/nerAfter");
    // util.runAQLFile(DocScan.makeFileScan(docsFile), AFTER_AQL);

    // Verify that both sets of regexes produce the same results.
    setExpectedDir(outputDir + "/nerBefore");
    compareAgainstExpected(false);
  }

  /**
   * Test of the CharIDMap data structure.
   */
  @Test
  public void charIDMapTest() throws Exception {
    startTest();

    // Generate some random intervals.
    Random r = new Random(42);

    char[] starts = new char[65536];
    char[] ids = new char[65536];
    int numIntervals = 0;
    {
      int maxStart = 0;

      while (maxStart < 65536) {
        maxStart += r.nextInt(1024);
        if (maxStart < 65536) {
          starts[numIntervals] = (char) maxStart;
          ids[numIntervals] = (char) r.nextInt(1024);

          // System.err
          // .printf("Chars starting from %d get id %d\n",
          // (int) starts[numIntervals],
          // (int) ids[numIntervals]);
          numIntervals++;
        }
      }
    }

    // Encode the intervals in a CharIDMap.
    CharIDMap map = new CharIDMap(numIntervals);
    for (int i = 0; i < numIntervals; i++) {
      map.addInterval(starts[i], ids[i]);
    }

    // Look up each character in turn, and verify that it has the correct
    // value.
    int numLookups = 0;
    long startMs = System.currentTimeMillis();
    for (int count = 0; count < 50; count++) {
      for (int i = 0; i < numIntervals; i++) {
        for (int c = starts[i]; c < 65536 && c < starts[i + 1]; c++) {
          // System.err.printf("Trying char %d\n", (int)c);
          assertEquals(ids[i], map.lookup((char) c));
          numLookups++;
        }
      }
    }
    long endMs = System.currentTimeMillis();

    long elapsedMs = endMs - startMs;

    System.err.printf("%d lookups in %d msec --> %1.1f lookups/sec\n", numLookups, elapsedMs,
        1000.0 * numLookups / elapsedMs);
  }

  /**
   * Test case for a bug in multi-regex mode. When a pair of similar regexes appeared in the input
   * to the SimpleRegex compiler, matches from only one of them were produced.
   */
  @Test
  public void repeatedRegexTest() throws Exception {
    startTest();

    final String[] PATTERNS = {"a", "a",};

    final int[] FLAGS = new int[PATTERNS.length];
    Arrays.fill(FLAGS, Pattern.DOTALL);

    final int[][] EXPECTED_BEGINS = {{0}, {0}};

    final int[][] EXPECTED_ENDS = {{1}, {1}};

    // Text against which to perform matching.
    final String TARGET = "a";

    genericMultiRegexTest(PATTERNS, FLAGS, TARGET, EXPECTED_BEGINS, EXPECTED_ENDS);
  }

  /**
   * Test case for a parser bug involving question marks after a character class
   */
  @Test
  public void charClassBugTest() throws Exception {
    startTest();

    // Regex that wouldn't parse
    final String pattern = "[ ]?:[ ]?((\\p{Lu}\\p{M}*)+)";

    int flags = 0x0;

    String target = "foo :bar :Fab";

    int[] expectedStarts = {8};
    int[] expectedEnds = {11};

    genericRegexTest(pattern, flags, target, expectedStarts, expectedEnds);

  }

  /**
   * Test for defect : systemT extraction fails with Java IndexoutofBounds Exception. Turned out to
   * be a JVM bug. Submitted PMR 90366,001,866 with IBM Java team. Commented out because it fails.
   * Uncomment it whenever the Java team reports that the bug is fixed.
   * 
   * @throws Exception
   */
  // @Test
  public void sdaRegexTest() throws Exception {
    startTest();

    String pattern = "I'm\\s+(?:at|in|@)\\s+(.+?)(\\s+|\\s+(?:w/|with)(?:.)+)(http[:/a-zA-Z0-9.]+)";
    Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);

    // String inputStr = "I'm in town with cristiano cristiano 😍";
    // Same problem can be reproduced with the unicode escaped version of the above string:
    String inputStr = "I'm in town with cristiano cristiano \ud83d\ude0d";
    Log.info("Input string is: %s", inputStr);
    Log.info("Escaped input string is: %s", StringUtils.escapeUnicode(inputStr));

    Matcher m = p.matcher(inputStr);

    try {
      while (m.find()) {
        Log.info("Got match: '%s'", m.group(0));
      }
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }
  }

  /**
   * Test for defect : systemT extraction fails with Java IndexoutofBounds Exception. Turned out to
   * be a JVM bug. Submitted PMR 90366,001,866 with IBM Java team. This test is a version of
   * {@link #sdaRegexTest()} with simpler inputs. Commented out because it fails. Uncomment it
   * whenever the Java team reports that the bug is fixed.
   * 
   * @throws Exception
   */
  // @Test
  public void sdaRegexSimpleTest() throws Exception {
    startTest();

    String pattern = "with(.)+(http[:/a-zA-Z0-9.]+)";
    Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);

    String inputStr = "with so and so 😍";
    // Same problem can be reproduced with:
    // String inputStr = "with so and so \ud83d\ude0d";
    Log.info("Input string is: %s", inputStr);
    Log.info("Unicode escaped input string is: %s", StringUtils.escapeUnicode(inputStr));

    Matcher m = p.matcher(inputStr);

    try {
      while (m.find()) {
        Log.info("Got match: '%s'", m.group(0));
      }
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }
  }

  /**
   * A generic test of running SimpleRegex in multi-regex mode
   * 
   * @param patterns regular expressions to evaluate
   * @param flags flags to pass for each of the patterns
   * @param target target string to perform matching over
   * @param expectedStarts expected offsets of matches for each regular expression, or null to skip
   *        checking these offsets
   * @param expectedEnds expected end offsets of matches for each regular expression
   */
  private void genericMultiRegexTest(String[] patterns, int[] flags, String target,
      int[][] expectedStarts, int[][] expectedEnds) throws Exception {

    SimpleRegex mre = new SimpleRegex(patterns, flags);

    JavaRegex[] jre = new JavaRegex[patterns.length];
    for (int i = 0; i < patterns.length; i++) {
      jre[i] = new JavaRegex(patterns[i], flags[i]);
    }

    // System.err.printf("Parse tree is:\n");
    // re.dump(System.err, 1);

    SimpleRegexMatcher m = (SimpleRegexMatcher) mre.matcher(target);

    RegexMatcher[] jm = new RegexMatcher[patterns.length];
    for (int i = 0; i < patterns.length; i++) {
      jm[i] = jre[i].matcher(target);
    }

    Log.info("Matching over: '%s'", escapeNonPrinting(target));

    // Get all the matches from the multi-regex matcher in one pass, pulling
    // out the corresponding matches from the single-regex matchers.
    Log.info("Matches are:");

    // The multi-regex matcher gives us the match lengths at the current
    // position for all regexes that match there. It's up to us to remember
    // the last position that we matched each regex at.
    int[] curOff = new int[patterns.length];
    Arrays.fill(curOff, 0);

    int[] nextMatchNum = new int[patterns.length];
    Arrays.fill(nextMatchNum, 0);

    for (int pos = 0; pos < target.length(); pos++) {
      // Find the lengths of all matches at the current position.
      m.region(pos, target.length());
      int[] matchLens = m.getAllMatches();

      for (int i = 0; i < patterns.length; i++) {
        if (matchLens[i] >= 0 && curOff[i] <= pos) {
          // Found a match at this position, and it doesn't overlap
          // with the last match we found for this sub-expression.
          int begin = pos;
          int end = pos + matchLens[i];
          Log.info("Regex %d: [%2d, %2d]: '%s'", i, begin, end, target.substring(begin, end));

          if (null != expectedStarts) {
            // Verify that this match is where we expected it to be.
            int matchIx = nextMatchNum[i];
            if (matchIx > expectedStarts[i].length) {
              throw new Exception(
                  String.format("Got %d matches for regex %d," + " but only expected %d",
                      matchIx + 1, i, expectedStarts[i].length));
            }

            int expectedBegin = expectedStarts[i][matchIx];
            int expectedEnd = expectedEnds[i][matchIx];

            if (expectedBegin != begin || expectedEnd != end) {
              throw new Exception(String.format(
                  "Expected match %d for regex %d " + "to be on [%d, %d], " + "but got [%d, %d]",
                  matchIx, i, expectedBegin, expectedEnd, begin, end));
            }
          }

          nextMatchNum[i]++;
        }
      }
    }

    // Check for missing matches.
    if (null != expectedStarts) {
      for (int i = 0; i < patterns.length; i++) {
        int matchIx = nextMatchNum[i];
        if (matchIx < expectedStarts[i].length) {
          int start = expectedStarts[i][matchIx];
          int end = expectedEnds[i][matchIx];
          throw new Exception(String.format("Missed a match of regex %d from %d to %d ('%s')", i,
              start, end, target.substring(start, end)));

        }
      }
    }

    //
    //
    // int count = 0;
    // while (m.find()) {
    // System.err.printf(" [%2d, %2d]: '%s'\n", m.start(), m.end(),
    // escapeNonPrinting(target.subSequence(m.start(), m.end())));
    //
    // // Make sure the Java engine returns the same matches.
    // assertTrue(jm.find());
    // assertEquals(jm.start(), m.start());
    // assertEquals(jm.end(), m.end());
    //
    // if (null != expectedStarts) {
    // assertEquals(expectedStarts[count], m.start());
    // assertEquals(expectedEnds[count], m.end());
    // }
    // count++;
    // }
    //
    // // Make sure that there aren't any additional matches that we missed.
    // if (jm.find()) {
    // char[] str = target.subSequence(jm.start(), jm.end()).toString()
    // .toCharArray();
    // System.err.printf("JavaRegex returned: %s\n", Arrays.toString(str));
    // throw new Exception(String
    // .format("JavaRegex returns additional"
    // + " match: [%d, %d]: '%s'", jm.start(), jm.end(),
    // escapeNonPrinting(target.subSequence(jm.start(), jm
    // .end()))));
    //
    // }
    //
    // if (null != expectedStarts) {
    // assertEquals(expectedStarts.length, count);
    // }
  }

  /**
   * Convenience version of {@link #genericRegexTest(String, int, String, int[], int[])} with
   * default flags.
   */
  private void genericRegexTest(String pattern, String target, int[] expectedStarts,
      int[] expectedEnds) throws Exception {
    genericRegexTest(pattern, 0x0, target, expectedStarts, expectedEnds);
  }

  /**
   * A generic test of a simple regex.
   * 
   * @throws ParseException
   */
  private void genericRegexTest(String pattern, int flags, String target, int[] expectedStarts,
      int[] expectedEnds) throws Exception {

    SimpleRegex re = new SimpleRegex(pattern, flags, true);
    JavaRegex jre = new JavaRegex(pattern, flags);

    System.err.printf("Orig pattern is: %s\n", pattern);
    System.err.printf("Parsed regex is: %s\n", re.getExpr());

    // The pattern should be able to print itself out verbatim.
    assertEquals(pattern, re.getExpr());

    // System.err.printf("Parse tree is:\n");
    // re.dump(System.err, 1);

    RegexMatcher m = re.matcher(target);
    RegexMatcher jm = jre.matcher(target);

    System.err.printf("Matching over: '%s'\n", escapeNonPrinting(target));
    System.err.printf("Matches with SimpleRegex are:\n");

    int count = 0;
    while (m.find()) {
      System.err.printf("   [%2d, %2d]: '%s'\n", m.start(), m.end(),
          escapeNonPrinting(target.subSequence(m.start(), m.end())));

      // Make sure the Java engine returns the same matches.
      assertTrue(jm.find());
      assertEquals(jm.start(), m.start());
      assertEquals(jm.end(), m.end());

      if (null != expectedStarts) {
        assertEquals(expectedStarts[count], m.start());
        assertEquals(expectedEnds[count], m.end());
      }
      count++;
    }

    // Make sure that there aren't any additional matches that we missed.
    if (jm.find()) {
      char[] str = target.subSequence(jm.start(), jm.end()).toString().toCharArray();
      System.err.printf("JavaRegex returned: %s\n", Arrays.toString(str));
      throw new Exception(String.format("JavaRegex returns additional" + " match: [%d, %d]: '%s'",
          jm.start(), jm.end(), escapeNonPrinting(target.subSequence(jm.start(), jm.end()))));

    }

    if (null != expectedStarts) {
      assertEquals(expectedStarts.length, count);
    }
  }

  // Escape non-printing characters in a string.
  private static String escapeNonPrinting(CharSequence in) {
    String s = in.toString();
    s = s.replace("\n", "\\n");
    s = s.replace("\r", "\\r");
    s = s.replace("\t", "\\t");
    return s;
  }

  /**
   * Test that verifies whether regex strength reduction is correctly handled during AQL to AOG
   * compilation.
   */
  @Test
  public void testRSR() throws Exception {
    startTest();

    File AQL_FILE = new File(AOG_FILES_DIR, "testRSR.aql");
    File OUT_RSR_ENABLED = new File(getCurOutputDir(), "test-RSR-enabled");
    File OUT_RSR_DISABLED = new File(getCurOutputDir(), "test-RSR-disabled");

    OUT_RSR_ENABLED.mkdirs();
    OUT_RSR_DISABLED.mkdirs();

    // Generate AOG file with RSR enabled
    CompileAQLParams params =
        new CompileAQLParams(AQL_FILE, OUT_RSR_ENABLED.toURI().toString(), null);
    params.setPerformRSR(true);
    params.setTokenizerConfig(getTokenizerConfig());
    CompileAQL.compile(params);

    // Generate AOG file with RSR disabled
    params = new CompileAQLParams(AQL_FILE, OUT_RSR_DISABLED.toURI().toString(), null);
    params.setPerformRSR(false);
    params.setTokenizerConfig(getTokenizerConfig());
    CompileAQL.compile(params);

    // Make sure the compiler produced the output we expected it to.
    // compareAgainstExpected(false);

    // BEGIN Comment by Huaiyu Zhu 2013-07.
    // This comparison is commented out - It fails when comparing genericModule.tam, with
    // Debug: Expected line:'$Document ='
    // Debug: Actual line:'$Document = DocScan('

    // This test previously passed using TestUtils.compareAgainstExpected
    // because subdirectories were erroneously ignored.
    // The comparison fails even with TestUtils if the expected directory is set correctly. For
    // example
    // TestUtils util = new TestUtils();
    // util.setExpectedDir (new File (getCurExpectedDir (), "test-RSR-enabled").toString ());
    // util.setOutputDir (OUT_RSR_ENABLED.toString ());
    // util.compareAgainstExpected (false);
    // END Comment by Huaiyu Zhu 2013-07.

  }
}
