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
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.Span;
import com.ibm.avatar.algebra.datamodel.TLIter;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.TextGetter;
import com.ibm.avatar.algebra.datamodel.TextSetter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.join.SortMergeJoin;
import com.ibm.avatar.algebra.scan.DocScanInternal;
import com.ibm.avatar.algebra.util.dict.DictParams;
import com.ibm.avatar.algebra.util.document.ToCSVOutput;
import com.ibm.avatar.algebra.util.lang.LangCode;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.algebra.util.tokenize.TokenizerConfig;
import com.ibm.avatar.api.CompileAQL;
import com.ibm.avatar.api.CompileAQLParams;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.DocReader;
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.api.exceptions.CircularIncludeDependencyException;
import com.ibm.avatar.api.exceptions.CompilerException;
import com.ibm.avatar.api.exceptions.ExceptionWithView;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.AQLParser;
import com.ibm.avatar.aql.ColNameNode;
import com.ibm.avatar.aql.CreateDictNode;
import com.ibm.avatar.aql.DictExNode;
import com.ibm.avatar.aql.ExtendedParseException;
import com.ibm.avatar.aql.NickNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.StringNode;
import com.ibm.avatar.aql.compiler.Compiler;
import com.ibm.avatar.aql.planner.Planner;
import com.ibm.avatar.aql.planner.Planner.ImplType;
import com.ibm.avatar.aql.tam.TAM;
import com.ibm.avatar.aql.tam.TAMSerializer;
import com.ibm.avatar.logging.Log;
import com.ibm.avatar.logging.MsgType;

/**
 * Tests that arose from bugs in AQL queries.
 */
public class AQLBugTests extends RuntimeTestHarness {

  private static final String AQL_FILES_DIR = TestConstants.AQL_DIR + "/AQLBugTests";

  private static final String DOCS_DIR = TestConstants.TEST_DOCS_DIR + "/AQLBugTests";

  public static void main(String[] args) {
    try {

      AQLBugTests t = new AQLBugTests();

      t.setUp();

      long startMS = System.currentTimeMillis();

      t.floatErrorBug();

      long endMS = System.currentTimeMillis();

      t.tearDown();

      double elapsedSec = (endMS - startMS) / 1000.0;

      System.err.printf("Test took %1.3f sec.\n", elapsedSec);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Scan over the Enron database; set up by setUp() and cleaned out by tearDown()
   */
  // private DocScan scan = null;
  private File defaultDocsFile;

  @Before
  public void setUp() throws Exception {

    // Renice the current thread to avoid locking up the entire system.
    Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

    // scan = new DerbyDocScan(EnronTests.dbname, EnronTests.quickQuery);

    // System.err.printf("Loading file '%s'\n",
    // TestConstants.ENRON_10K_DUMP);
    // scan = new DBDumpFileScan(TestConstants.ENRON_10K_DUMP);
    // scan = new DBDumpFileScan(TestConstants.TEST_DOCS_DIR + "/tmp.del");
    // scan = null;
    defaultDocsFile = new File(TestConstants.ENRON_10K_DUMP);

    setDataPath(TestConstants.TESTDATA_DIR);

    // For now, don't put any character set information into the header of
    // our output HTML.
    setWriteCharsetInfo(false);
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
   * Test case for a bug that popped up during phone number annotator creation.
   * 
   * @throws Exception
   */
  @Test
  public void phoneBug() throws Exception {

    startTest();

    runNonModularAQLTest(defaultDocsFile);

  }

  /**
   * Test of a second bug in phone number identification that turned up during the tokenizer
   * refactoring.
   */
  @Test
  public void phoneBug2() throws Exception {
    startTest();
    genericBugTestCase("phoneBug2");
  }

  /**
   * Test case for a bug that popped up when using the case-sensitive State dictionary.
   * 
   * @throws Exception
   */
  @Test
  public void stateBug() throws Exception {
    startTest();

    String filename = AQL_FILES_DIR + "/statebug.aql";

    // scan = new DBDumpFileScan(TestConstants.ENRON_10K_DUMP);

    runNonModularAQLTest(defaultDocsFile, new File(filename));

    // util.setSkipFileComparison(true);

    truncateOutputFiles(true);
    compareAgainstExpected("AllStates.htm", false);
  }

  /**
   * Test case for a bug in AdjacentJoin implementation when both inputs fall between the same
   * tokens.
   * 
   * @throws Exception
   */
  @Test
  public void adjacentFollowsTokBug() throws Exception {
    startTest();

    genericBugTestCase("adjacentFollowsTokBug");
  }

  /**
   * Test case for a bug in AdjacentJoin implementation with the slow path evaluation. #166957:
   * ArrayIndexOutOfBoundsException in AdjacentJoin.evalSlowPath()
   * 
   * @throws Exception
   */
  @Test
  public void adjacentFollowsTokBug2() throws Exception {
    startTest();

    genericBugTestCase("adjacentFollowsTokBug2");
  }

  /**
   * Original test case for defect : Defect in AdjacentJoin with the FollowedByTok predicate.
   * 
   * @throws Exception
   */
  @Test
  public void adjacentFollowedByTokBug() throws Exception {
    startTest();
    setPrintTups(true);

    genericBugTestCase("adjacentFollowedByTokBug");
  }

  /**
   * Exhaustive test cases for defect : Defect in AdjacentJoin with the FollowedByTok predicate.
   * Version of {@link #adjacentFollowedByTokBug3()} using various combinations of input spans not
   * on token boundaries.
   * 
   * @throws Exception
   */
  @Test
  public void adjacentFollowedByTokBug2() throws Exception {
    startTest();
    setPrintTups(true);

    genericBugTestCase("adjacentFollowedByTokBug2");
  }

  /**
   * Follow-up test for defect ; a variant of {@link #adjacentFollowedByTokBug2()} that uses merge
   * join.
   * 
   * @throws Exception
   */
  @Test
  public void adjacentFollowedByTokBug3() throws Exception {
    com.ibm.avatar.aql.compiler.Compiler compiler = null;
    try {
      startTest();
      setPrintTups(true);

      // Use the same AQL file and docs as adjacentFollowedByTokBug2().
      File aqlFile = new File(TestConstants.AQL_DIR, "AQLBugTests/adjacentFollowedByTokBug2.aql");
      File docsFile = new File(DOCS_DIR, "adjacentFollowedByTokBug2.del");

      // Compile with the low-level compiler API, so that we can set the planner.
      CompileAQLParams params = new CompileAQLParams();
      params.setInputFile(aqlFile);
      params.setOutputURI(curOutputDir.toURI().toString());
      params.setTokenizerConfig(getTokenizerConfig());

      compiler = new com.ibm.avatar.aql.compiler.Compiler();
      Planner p = new Planner(ImplType.NAIVE_MERGE);
      compiler.setPlanner(p);
      compiler.compileToTAM(params);

      runNonModularAQLTest(docsFile, aqlFile);

      compareAgainstExpected(true);
    } finally {
      if (null != compiler)
        compiler.deleteTempDirectory();
    }
  }

  /**
   * Exhaustive test cases for a bug discovered while fixing defect : Defect in AdjacentJoin with
   * the FollowedByTok predicate: the Select implementation of FollowsTok resulted in incorrect
   * outputs when one or both inputs are not on token boundaries. Uses the same rules as in
   * {@link #adjacentFollowedByTokBug2()} but forges the FollowsTok predicate to be evaluated as a
   * selection predicate.
   * 
   * @throws Exception
   */
  @Test
  public void followsTokNotOnTokenBoundariesBug() throws Exception {
    startTest();
    setPrintTups(true);

    genericBugTestCase("followsTokNotOnTokenBoundariesBug");
  }

  /**
   * Follow-up test for defect ; a variant of {@link #followsTokNotOnTokenBoundariesBug()} that uses
   * merge join.
   * 
   * @throws Exception
   */
  @Test
  public void followsTokNotOnTokenBoundariesBug2() throws Exception {
    Compiler compiler = null;
    try {
      startTest();
      setPrintTups(true);

      // Use the same AQL file and docs as followsTokNotOnTokenBoundariesBug().
      File aqlFile =
          new File(TestConstants.AQL_DIR, "AQLBugTests/followsTokNotOnTokenBoundariesBug.aql");
      File docsFile = new File(DOCS_DIR, "followsTokNotOnTokenBoundariesBug.del");

      // Compile with the low-level compiler API, so that we can set the planner.
      CompileAQLParams params = new CompileAQLParams();
      params.setInputFile(aqlFile);
      params.setOutputURI(curOutputDir.toURI().toString());
      params.setTokenizerConfig(getTokenizerConfig());

      compiler = new com.ibm.avatar.aql.compiler.Compiler();
      Planner p = new Planner(ImplType.NAIVE_MERGE);
      compiler.setPlanner(p);
      compiler.compileToTAM(params);

      runNonModularAQLTest(docsFile, aqlFile);

      compareAgainstExpected(true);
    } finally {
      if (compiler != null) {
        compiler.deleteTempDirectory();
      }
    }
  }

  /**
   * Same tests as in {@link #followsTokNotOnTokenBoundariesBug()} this time using the FollowedByTok
   * predicate.
   * 
   * @throws Exception
   */
  @Test
  public void followedByTokNotOnTokenBoundariesBug() throws Exception {
    startTest();
    setPrintTups(true);

    genericBugTestCase("followedByTokNotOnTokenBoundariesBug");
  }

  /**
   * Test case for a bug in de-escaping \ in the AQL and AOG parsers. #166958 Problem with
   * deescaping in RegexLiterals
   * 
   * @throws Exception
   */
  @Test
  public void regexEscapedSlashBug() throws Exception {
    startTest();

    genericBugTestCase("regexEscapedSlashBug");
  }

  /**
   * Test case for a bug when using regex with external dictionary with no external flags specified.
   * 
   * @throws Exception
   */
  @Test
  public void regexDefaultFlagBug() throws Exception {
    startTest();

    genericBugTestCase("regexDefaultFlagBug");
  }

  /**
   * Test case for making sure that a nondeterminism bug doesn't recur. The original bug was that,
   * when a particular email message was fed through the system multiple times via the push API,
   * some annotations got dropped, seemingly at random.
   * 
   * @throws Exception
   */
  @Test
  public void ndBugTest() throws Exception {
    startTest();

    // AQL input file
    String filename = TestConstants.AQL_DIR + "/lotus/namedentity.aql";

    // Output file
    String OUTFILE_NAME = getCurOutputDir() + "/ndbug.txt";

    // Number of times to run the file through the plan
    int NREPEAT = 10;

    // Input text that triggers the bug.
    String TEXT = "From:	Timothy Fong/San Francisco/IBM\n"
        + "To:	Sriram Raghavan/Almaden/IBM@IBMUS\n" + "Date:	11/05/2007 08:58 AM\n"
        + "Subject:	IOPES and folders\n" + "\n" + "\n" + "Sriram,\n" + "\n"
        + "Currentnly it is not indexing my To Do Folder.  Suggestions?\n" + "\n" + "Thanks!\n"
        + "\n" + "Tim\n" + "===========================================\n"
        + "Click here for my blog: http://bigblueguy.wordpress.com\n"
        + "Customers -- online support: http://www-306.ibm.com/support/operations/worldwide/\n"
        + "\n" + "Timothy Fong\n" + "IBM Software Group\n"
        + "Tivoli Sales Rep - Security, Storage, Automation, ITIL\n"
        + "425 Market Street, San Francisco, CA 94105\n" + "415-545-4227\n" + "tfong@us.ibm.com\n";

    // Compile the AQL file. Instantiate OperatorGraph by reading AOG from disk
    compileAQL(new File(filename), TestConstants.TESTDATA_DIR);
    OperatorGraph og = instantiateOperatorGraph(Constants.GENERIC_MODULE_NAME);

    // Open the output file.
    FileWriter out = new FileWriter(OUTFILE_NAME);

    // Create a document schema and text setter. Get output schema
    TupleSchema docSchema = DocScanInternal.createOneColumnSchema();
    TextSetter setDocText = docSchema.textSetter(Constants.DOCTEXT_COL);
    Map<String, TupleSchema> outputSchema = og.getOutputTypeNamesAndSchema();

    // Use the push API to repeatedly push the same document through the plan.
    for (int i = 0; i < NREPEAT; i++) {

      Tuple docTup = docSchema.createTup();
      setDocText.setVal(docTup, TEXT);
      Map<String, TupleList> annotations = og.execute(docTup, null, null);

      // Dump results to the output file.
      out.append(String.format("Results of run %d:\n", i));
      for (Entry<String, TupleList> entry : annotations.entrySet()) {
        String outputName = entry.getKey();
        TupleList tups = entry.getValue();

        TupleSchema schema = outputSchema.get(outputName);

        // Assume that element zero is an annotation column.
        FieldGetter<Span> getSpan = schema.spanAcc(schema.getFieldNameByIx(0));

        for (TLIter itr = tups.iterator(); itr.hasNext();) {
          Tuple tuple = itr.next();

          // Assume that element zero is an annotation column.
          Span s = getSpan.getVal(tuple);

          String line = String.format("    => %s from %d to %d: '%s'\n", outputName, s.getBegin(),
              s.getEnd(), s.getText());
          out.append(line);
        }
      }
      out.append("-------------\n");
    }

    out.close();

    // TODO: Verify output

    // util.setSkipFileComparison(true);

    // util.compareAgainstExpected("Block13.htm");
    // util.compareAgainstExpected("Block3.htm");
    // util.compareAgainstExpected("BlockTok13.htm");
    // util.compareAgainstExpected("BlockTok25.htm");
    // util.compareAgainstExpected("BlockTok025.htm");
  }

  /**
   * Test of a bug in the address annotator.
   * 
   * @throws Exception
   */
  @Test
  public void addressBugTest() throws Exception {
    startTest();
    genericBugTestCase("addressBugTest");

    // These checks are now done automatically in genericBugTestCase()
    // util.compareAgainstExpected("Inner.htm");
    // util.compareAgainstExpected("Outer.htm");
    // util.compareAgainstExpected("Result.htm");
  }

  /**
   * Test that makes sure a bug in the name annotator does not resurface. The minus operator had
   * been applied to tuples that came from separate views were otherwise identical, and the
   * overlapping tuples weren't being filtered out. A change in tuple equality semantics fixed this
   * problem.
   * 
   * @throws Exception
   */
  @Test
  public void nameBug() throws Exception {

    startTest();
    // util.setGenerateTables(true);

    genericBugTestCase("nameBug");

    // util.setSkipFileComparison(true);

    compareAgainstExpected("Person4Copy.htm", false);
    // util.compareAgainstExpected("Person4Copy.txt");
    // util.compareAgainstExpected("Outer.htm");
    // util.compareAgainstExpected("Result.htm");
  }

  /**
   * A simpler version of nameBugTest that started failing in the midst of the tokenization
   * refactoring.
   */
  @Test
  public void nameBug2() throws Exception {

    startTest();
    genericBugTestCase("nameBug2");

    // util.setSkipFileComparison(true);

    // util.compareAgainstExpected("Person4Copy.htm");
    // util.compareAgainstExpected("Person4Copy.txt");
    // util.compareAgainstExpected("Outer.htm");
    // util.compareAgainstExpected("Result.htm");
  }

  /**
   * Test case for a bug that we originally thought was with the Difference (minus) operator; it
   * turned out to be a problem with trailing spaces in dictionary entries.
   */
  @Test
  public void minusBug() throws Exception {
    startTest();

    genericBugTestCase("minusBug");

    // These checks are now done automatically in genericBugTestCase()
    // util.compareAgainstExpected("CityName1.htm");
    // util.compareAgainstExpected("CityName1.htm");
    // util.compareAgainstExpected("WithoutUSCity.htm");
    // util.compareAgainstExpected("WithUSCity.htm");
  }

  /**
   * A compiler bug in generating Block() operator calls from AQL.
   * 
   * @throws Exception
   */
  @Test
  public void blockBug() throws Exception {
    startTest();

    setPrintTups(true);
    // util.setGenerateTables(true);

    genericBugTestCase("blockBug");

    // util.setSkipFileComparison(true);
    compareAgainstExpected("blockBug.htm", false);

    // util.compareAgainstExpected("blockBug.htm");
  }

  /**
   * A bug in the BlockTok operator that causes it not to generate blocks of 3 elements.
   * 
   * @throws Exception
   */
  @Test
  public void blockTokBug() throws Exception {
    startTest();

    setPrintTups(true);
    // util.setGenerateTables(true);

    genericBugTestCase("blockTokBug");

    // util.setSkipFileComparison(true);

    compareAgainstExpected("Coonetothree.htm", false);
    // util.compareAgainstExpected("Coonetothree.htm");
  }

  /**
   * Test case for defect: 58349: Extract block with character distance outputs blocks of length
   * maxSize+1
   * 
   * @throws Exception
   */
  // @Test
  public void blockCharBug() throws Exception {
    startTest();

    setPrintTups(true);
    // util.setGenerateTables(true);

    genericBugTestCase("blockCharBug");

    // util.setSkipFileComparison(true);
    compareAgainstExpected("blockCharBug.htm", false);

    // util.compareAgainstExpected("blockBug.htm");
  }

  /**
   * Test of a bug in the conditional evaluation code. NOTE FROM LAURA 10/1/2012: This test is
   * commented out since revision 9 of old CS svn. END NOTE FROM LAURA 10/1/2012
   */
  // @Test
  public void condEvalBug() throws Exception {
    startTest();

    // Explicitly enable conditional evaluation.
    boolean oldval = SortMergeJoin.CONDITIONAL_EVAL;
    SortMergeJoin.CONDITIONAL_EVAL = true;

    genericBugTestCase("condEvalBug");

    truncateExpectedFiles();
    compareAgainstExpected(true);

    // util.setSkipFileComparison(true);
    //
    // util.compareAgainstExpected("FirstName.htm");
    // util.compareAgainstExpected("PhoneNumber.htm");
    //
    // util.compareAgainstExpected("PersonPhone1.htm");
    // util.compareAgainstExpected("PPOutput.htm");

    // Reinstate the default setting.
    SortMergeJoin.CONDITIONAL_EVAL = oldval;
  }

  /**
   * Test of a second bug in the conditional evaluation code.
   */
  @Test
  public void condEvalBug2() throws Exception {

    startTest();

    // Explicitly enable conditional evaluation.
    boolean oldval = SortMergeJoin.CONDITIONAL_EVAL;
    SortMergeJoin.CONDITIONAL_EVAL = true;

    genericBugTestCase("condEvalBug2");

    // Reinstate the default setting.
    SortMergeJoin.CONDITIONAL_EVAL = oldval;
  }

  /**
   * Test of a bug where the string "Sriram Raghavan" as the document text caused a
   * NullPointerException.
   */
  @Test
  public void sriramBug() throws Exception {
    startTest();
    genericBugTestCase("sriramBug");

    // There's no output to test; we just want to make sure that the test
    // doesn't generate a NullPointerException.
  }

  /**
   * Test of a bug where using DocScan as an input prevents a query from compiling.
   */
  @Test
  public void docscanBug() throws Exception {
    startTest();

    String aqlFileName = String.format("%s/%s.aql", AQL_FILES_DIR, "docscanBug");
    String docsFileName = String.format("%s/%s.del", DOCS_DIR, "docscanBug");

    File newDocsFile = new File(docsFileName);
    runNonModularAQLTest(newDocsFile, aqlFileName);

    // Don't bother checking output; we're mostly concerned about not
    // throwing exceptions.
  }

  /**
   * Test of a bug in which the compiler does not catch mismatched column names.
   */
  @Test
  public void badColNameTest() throws Exception {
    startTest();

    String AQL = "create view The as \n" + "select R.match as the \n"
        + "from Regex(/the/, DocScan.text) R;\n" + "\n" + "select T.wrongColumnName as the\n"
        + "into myoutput\n" + "from The T;\n";

    // Attempt to compile; this operation should throw an error.
    boolean threwException = false;
    try {
      CompileAQLParams params = new CompileAQLParams();
      params.setInputStr(AQL);
      params.setOutputURI(getCurOutputDir().toURI().toString());
      CompileAQL.compile(params);
    } catch (CompilerException e) {

      System.err.printf("Caught exception as expected: %s\n",
          e.getAllCompileErrors().get(0).toString());
      threwException = true;
    }

    assertTrue(threwException);

  }

  /**
   * Another test to ensure that the compiler catches invalid column names.
   */
  @Test
  public void badColNameTest2() throws Exception {
    startTest();

    String AQL = "create view The as \n" + "select R.match as the \n"
        + "from Regex(/the/, DocScan.text) R;\n" + "\n" + "select T.the as the\n"
        + "into myoutput\n" + "from The T\n" + "where MatchesRegex(/h/, T.wrongColumn);\n";

    // Attempt to compile; this operation should throw an error.
    boolean threwException = false;
    try {
      CompileAQLParams params = new CompileAQLParams();
      params.setInputStr(AQL);
      params.setOutputURI(getCurOutputDir().toURI().toString());
      CompileAQL.compile(params);
    } catch (CompilerException e) {

      System.err.printf("Caught exception as expected: %s\n",
          e.getAllCompileErrors().get(0).toString());
      threwException = true;
    }

    assertTrue(threwException);

  }

  /**
   * A regular expression that confused the JavaCC-generated lexer.
   */
  @Test
  public void lexerBug() throws Exception {
    startTest();

    String AQLFILE = AQL_FILES_DIR + "/lexerBug.aql";

    // Just try to compile.
    compileAQL(new File(AQLFILE), AQL_FILES_DIR);
  }

  /**
   * Test case for a bug that popped up in containsDict operator
   * 
   * @throws Exception
   */
  @Test
  public void singleQuoteBug() throws Exception {
    startTest();
    genericBugTestCase("singleQuoteBug");

  }

  /**
   * Test case for defect : CS defect : Problem with dictionary evaluation on entries ending with
   * escape character
   */
  @Test
  public void dictEmoticonBug() throws Exception {
    startTest();
    setDataPath(TestConstants.AQL_DIR + "/" + this.getClass().getSimpleName());
    // setDumpPlan (true);
    genericBugTestCase("dictEmoticonBug");

  }

  /**
   * Make sure that the old (multiple matches per start token) RegexTok semantics don't reappear.
   */
  @Test
  public void regexTokBug() throws Exception {
    startTest();
    setPrintTups(true);
    // util.setGenerateTables(true);
    genericBugTestCase("regexTokBug");

  }

  /**
   * Test case for a bug involving a crash in a RegexTok operator
   */
  @Test
  public void regexTokBug2() throws Exception {
    startTest();
    genericBugTestCase("regexTokBug2");
  }

  /**
   * Test case for a bug in RegexTok flag support.
   */
  @Test
  public void regexTokFlagBug() throws Exception {
    startTest();
    genericBugTestCase("regexTokFlagBug");

  }

  /**
   * Test case for a bug involving a crash in a MatchesRegex selection predicate.
   */
  @Test
  public void matchesRegexBug() throws Exception {
    startTest();
    genericBugTestCase("matchesRegexBug");
  }

  /**
   * Test case for an obscure bug in FollowedByTok
   */
  @Test
  public void followedByTokBug() throws Exception {
    startTest();
    setPrintTups(false);
    genericBugTestCase("followedByTokBug", TestConstants.ENRON_1K_DUMP);

  }

  /**
   * Test case for a bug involving a column appearing twice in a select list.
   */
  @Test
  public void repeatBugTest() throws Exception {
    startTest();

    File aqlFile = new File(AQL_FILES_DIR, String.format("%s.aql", getCurPrefix()));

    if (false == aqlFile.exists()) {
      throw new Exception(String.format("AQL file %s not found.", aqlFile));
    }

    // Parse the AQL file
    compileAQL(aqlFile, TestConstants.TESTDATA_DIR);

    // We don't care about whether the AQL runs, just about whether it
    // compiles.

  }

  /** A reusable scratchpad for trying out new tests. */
  public void scratchpad() throws Exception {
    startTest();
    genericBugTestCase("scratchpad");
  }

  /**
   * Yet another test case for a bug in tuple buffer clearing code.
   */
  @Test
  public void bufferBugTest() throws Exception {
    startTest();

    String AQLFILE_NAME = AQL_FILES_DIR + "/bufferBug.aql";
    System.err.print("compiling " + AQLFILE_NAME);

    // Parse the AQL file
    compileAQL(new File(AQLFILE_NAME), TestConstants.TESTDATA_DIR);

    // Load tam
    TAM tam =
        TAMSerializer.load(Constants.GENERIC_MODULE_NAME, getCurOutputDir().toURI().toString());

    String aog = tam.getAog();

    System.err.print("-----\nAOG plan is:\n" + aog);

    runAOGString(new File(TestConstants.ENRON_10K_DUMP), aog, null);

  }

  /**
   * Test case for a bug involving tuple buffers getting used twice in a single document.
   */
  @Test
  public void bufferBug2() throws Exception {
    startTest();
    genericBugTestCase("bufferBug2");
  }

  /**
   * Yet another bug in TeeOutput tuple buffer handling.
   */
  @Test
  public void bufferBug3() throws Exception {
    startTest();
    genericBugTestCase("bufferBug3");
  }

  /**
   * Yet another bug in TeeOutput tuple buffer handling.
   */
  @Test
  public void bufferBug4() throws Exception {
    startTest();

    final String DICTSDIR = TestConstants.TESTDATA_DIR;
    setDataPath(DICTSDIR);

    genericBugTestCase("bufferBug4");
  }

  /**
   * Yet another bug in TeeOutput tuple buffer handling.
   */
  @Test
  public void contextBug() throws Exception {
    startTest();
    genericBugTestCase("contextBug");
  }

  /**
   * Test case for a race condition in the parser's global file name stack.
   */
  @Test
  public void fileStackTest() throws Exception {

    final String AQL_FILE_NAME = TestConstants.AQL_DIR
        // + "/personphone.aql";
        + "/lotus/namedentity.aql";

    final int NTHREAD = 10;

    // Read the file into a string.
    FileReader in = new FileReader(AQL_FILE_NAME);
    char[] buf = new char[100000];
    int size = in.read(buf);
    final String fileContents = new String(buf, 0, size);
    in.close();

    // Parse the same file over and over again in multiple threads.
    class task implements Runnable {
      @Override
      public void run() {
        try {
          for (int i = 0; i < 10; i++) {
            AQLParser p = new AQLParser(fileContents);
            p.setIncludePath(TestConstants.TESTDATA_DIR);
            p.parse();
          }
        } catch (Exception e) {
          this.e = e;
        }
      }

      Exception e = null;
    }

    // Span threads that will do nothing but parse.
    task[] tasks = new task[NTHREAD];
    Thread[] threads = new Thread[tasks.length];
    for (int i = 0; i < tasks.length; i++) {
      tasks[i] = new task();
      threads[i] = new Thread(tasks[i]);
      threads[i].start();
    }

    // Wait for the threads to complete.
    for (int i = 0; i < threads.length; i++) {
      threads[i].join();
    }

    // Check for errors.
    for (int i = 0; i < tasks.length; i++) {
      Exception e = tasks[i].e;
      if (null != e) {
        System.err.printf("Thread %d caught exception.\n", i);
        throw new Exception(e);
      }
    }
  }

  /** Test of parsing regular expressions containing Unicode characters. */
  @Test
  public void unicode() throws Exception {
    startTest();

    // First try running the regex without AQL.
    System.err.printf("Trying to run regex directly in Java.\n");

    final String REGEX_STR = "[一二三四五六七八九〇][一二三四五六七八九〇]" + "[一二三四五六七八九〇][一二三四五六七八九〇]";

    final String TARGET_STR = "Some text 一三四九 some more text";

    Pattern regex = Pattern.compile(REGEX_STR);

    Matcher m = regex.matcher(TARGET_STR);

    if (false == m.find()) {
      throw new Exception("Regex produced no matches.");
    }

    System.err.printf("Found match from %d to %d: '%s'\n", m.start(), m.end(),
        TARGET_STR.subSequence(m.start(), m.end()));

    assertEquals(10, m.start());
    assertEquals(14, m.end());

    if (true == m.find()) {
      throw new Exception("Regex produced more than one match (only one expected).");
    }

    System.err.printf("Trying test with AQL.\n");
    genericBugTestCase("unicode");

    System.err.printf("Comparing output files.\n");
    // util.setSkipFileComparison(true);
    compareAgainstExpected(true);
  }

  /**
   * Test of the error-checking code that catches circular dependencies between AQL files.
   */
  @Test
  public void includeTest() throws Exception {
    startTest();

    // TODO: Need to check with Laura why we are parsing the same file
    // thrice
    // We'll try all 3 parts of the chain.
    final String[] TEST_FILES = {AQL_FILES_DIR + "/includeBug.aql",
        AQL_FILES_DIR + "/includeBug.aql", AQL_FILES_DIR + "/includeBug.aql"};

    CompileAQLParams compileParams = new CompileAQLParams();

    File tamDir = getCurOutputDir();

    for (String fileName : TEST_FILES) {
      boolean caughtException = false;
      try {
        compileParams.setInputModules(null);
        compileParams.setInputFile(new File(fileName));
        compileParams.setOutputURI(tamDir.toURI().toString());
        compileParams.setTokenizerConfig(getTokenizerConfig());
        CompileAQL.compile(compileParams);
        // AQLRunner.compileFile(fileName);
      } catch (CompilerException e) {

        System.err.printf("Caught CompilerException: %s\n", e);

        Exception firstException = e.getSortedCompileErrors().get(0);

        // The exception may be wrapped in another exception.
        if (firstException instanceof ExtendedParseException) {
          ExtendedParseException epe = (ExtendedParseException) firstException;
          firstException = epe.getWrappedException();
        }

        if (false == firstException instanceof CircularIncludeDependencyException) {
          throw new TextAnalyticsException(
              "Caught %s (type %s) instead of expected exception type "
                  + "CircularIncludeDependencyException",
              firstException, firstException.getClass().getName());
        }

        caughtException = true;
      }

      assertTrue(caughtException);
    }

  }

  /**
   * Unit test of the MatchesDict() predicate. Not a bug test per se, but we put it into this file
   * because this test has a custom set of input documents.
   */
  @Test
  public void matchesDict() throws Exception {
    startTest();
    genericBugTestCase("matchesDict");
  }

  /**
   * Test of a bug in the ExactMatch sorting consolidation type.
   */
  @Test
  public void exactMatch() throws Exception {
    startTest();
    genericBugTestCase("exactMatch");
  }

  /**
   * Test of a bug where the split operator returns zero results when there are no split points.
   */
  @Test
  public void split() throws Exception {
    startTest();
    genericBugTestCase("split");
  }

  /**
   * Test of a bug in remapping annotation offsets in HTML documents.
   */
  @Test
  public void remap() throws Exception {
    startTest();
    genericBugTestCase("remap");
  }

  /**
   * Test of a bug in the sentence detection query that showed up during tokenizer refactoring.
   */
  @Test
  public void sentenceBug() throws Exception {
    startTest();
    // util.setGenerateTables(true);
    setPrintTups(true);
    genericBugTestCase("sentenceBug");
  }

  /**
   * Test of a bug in the code for eliminating duplicate dictionary entries.
   */
  @Test
  public void dupElimBug() throws Exception {
    startTest();
    genericBugTestCase("dupElimBug");
  }

  /**
   * Test of a bug in identifying that a document is HTML
   */
  @Test
  public void htmlIdentBug() throws Exception {
    startTest();
    setWriteCharsetInfo(true);

    genericBugTestCase("htmlIdentBug");
  }

  /**
   * Test of a bug in the AdjacentJoin operator
   */
  @Test
  public void adjacentBug() throws Exception {
    startTest();
    genericBugTestCase("adjacentBug");
  }

  /**
   * Test of a bug in new case-specification syntax for dictionaries
   */
  @Test
  public void dictCaseBug() throws Exception {
    startTest();
    genericBugTestCase("dictCaseBug");
  }

  /**
   * Test of a bug in code generation for select statements that generate a cross product.
   */
  @Test
  public void crossProdBug() throws Exception {
    startTest();
    genericBugTestCase("crossProdBug");
  }

  /**
   * Test of a bug in the error-handling code for incorrect input types on UDFs.
   * <p>
   * Disabled by Huaiyu Zhu 2013-09. In the new type system a Span can be automatically converted to
   * a Text, and String is a synonym of Text.
   */
  // @Test
  public void udfErrorBug() throws Exception {
    startTest();

    // The error message we expect to see, minus the part at the beginning that
    final String EXPECTED_ERROR_MSG =
        "line 19, column 8: Error determining return type of expression 'StringUDF(IntConst(1), IntConst(2), GetCol(N.name), GetCol(N.name))': Argument 2 is of type Span instead of expected type String.  Usage: StringUDF(p1 Integer, p2 Integer, p3 String, p4 String)";

    try {
      genericBugTestCase("udfErrorBug");
    } catch (Throwable t) {
      // Verify that we caught the error message we expected to catch.
      String actualMsg = t.getMessage();

      Log.info("Error message is '%s'", actualMsg);
      Log.info("Expected message is '%s'", EXPECTED_ERROR_MSG);

      // Use contains(), since there may be some line number stuff at the
      // beginning of the error message.
      assertTrue(actualMsg.contains(EXPECTED_ERROR_MSG));

      // If we get here, the test was successful.
      return;
    }

    throw new RuntimeException(
        "We should never reach this point; " + "the test case should always throw an exception.");
  }

  /**
   * Test of a bug in code generation for string constants.
   */
  @Test
  public void stringEscapeBug() throws Exception {
    startTest();
    genericBugTestCase("stringEscapeBug");
  }

  /**
   * Test case for bugs in how the detagger handles malformed links
   */
  @Test
  public void malformedHTMLBug() throws Exception {
    startTest();
    setPrintTups(true);
    // util.setGenerateTables(true);

    genericBugTestCase("malformedHTMLBug");
  }

  /**
   * Test case for a bug involving column name collisions for the EXTRACT statement.
   */
  @Test
  public void extractColNameBug() throws Exception {
    startTest();
    genericBugTestCase("extractColNameBug");
  }

  /**
   * Test case for a bug involving column name collisions for the EXTRACT statement when using
   * shared dictionary matching.
   */
  @Test
  public void extractColNameBug2() throws Exception {
    startTest();
    genericBugTestCase("extractColNameBug2");
  }

  /**
   * Test case for a bug involving improper escaping of dictionary names in CREATE DICTIONARY
   * statements; this is bug #120960 in the bug tracker
   */
  @Test
  public void dictNameEscBug() throws Exception {
    startTest();

    // This test case only works on Windows.
    if ('/' == File.separatorChar) {
      return;
    }

    genericBugTestCase("dictNameEscBug");
  }

  /**
   * Second test case for bug #120960
   */
  @Test
  public void dictNameEscBug2() throws Exception {
    startTest();

    CreateDictNode cdn = new CreateDictNode.FromFile(null, null);

    DictParams params = new DictParams();
    params.setDictName("DictName");
    params.setFileName("path\\with\\backslashes");

    cdn.setParams(params);

    // Dump to the screen for our edification.
    cdn.dump(System.err, 0);

    // Dump to a file for comparison purposes.
    File outFile = new File(getCurOutputDir(), "output.aql");
    PrintWriter out = new PrintWriter(new FileOutputStream(outFile));
    cdn.dump(out, 0);
    out.close();

    // Compare against the expected result.
    compareAgainstExpected(false);
  }

  /**
   * Test case for defect : ensure that a DictExNode doesn't print the optional 'with flags' clause
   * when created with no flags.
   */
  @Test
  public void dictFlagDumpBug() throws Exception {
    startTest();

    ArrayList<StringNode> dicts = new ArrayList<StringNode>();
    dicts.add(new StringNode("last.dict"));

    ColNameNode target = new ColNameNode(new NickNode("D"), new NickNode("text"));

    NickNode outputCol = new NickNode("lastname");

    DictExNode dictexnode = new DictExNode(dicts, null, target, outputCol, null, null);

    dictexnode.dump(System.err, 0);

    // Dump to a file for comparison purposes.
    File outFile = new File(getCurOutputDir(), "output.aql");
    PrintWriter out = new PrintWriter(new FileOutputStream(outFile));
    dictexnode.dump(out, 0);
    out.close();

    // Compare against the expected result.
    compareAgainstExpected(false);
  }

  /**
   * Test case for bug #126764: Missing dictionary matches involving periods
   */
  @Test
  public void periodDictBug() throws Exception {
    startTest();

    setTokenizerConfig(TestConstants.STANDARD_TOKENIZER);

    setDataPath(AQL_FILES_DIR + "/periodDictBug");

    genericBugTestCase("periodDictBug");
  }

  /**
   * Test case for bug #131161: Problem with single line comment on last line
   */
  @Test
  public void commentBug() throws Exception {
    startTest();

    setTokenizerConfig(TestConstants.STANDARD_TOKENIZER);

    genericBugTestCase("commentBug");
  }

  /**
   * Test case for bug #???: References to undefined view names produce unfriendly error message
   */
  @Test
  public void undefViewBug() throws Exception {
    startTest();

    try {
      genericBugTestCase("undefViewBug");
    } catch (CompilerException e) {
      // Make sure that we got
      List<Exception> compileErrors = e.getAllCompileErrors();
      String msg = compileErrors.get(0).getMessage();

      // Beginning of the error
      String expectedSuffix =
          "line 9, column 15: View or Table 'ViewThatDoesNotExist', referenced in FROM clause, is not a valid reference. Ensure that the View or Table is defined and is visible in the current module, accessible by the given name.";

      String actualSuffix = msg.substring(msg.length() - expectedSuffix.length());
      assertEquals(expectedSuffix, actualSuffix);

      return;
    }

    throw new Exception("This test should have caught an exception.");
  }

  /**
   * Test case for problem with Turkish locale when capital "I" is lower cased to "?". Whoever fixes
   * this should uncomment the JUnit annotation. Defect [#160180]: Issue with dictionary matching
   * when locale is turkish
   */
  @Test
  public void turkishLocaleBug() throws Exception {
    startTest();

    // For some reason changing the locale programatically does not work,
    // so please change the locale when executing this method
    // by using the VM arg "-Duser.language=tr"
    System.err.println(System.getProperty("user.language"));
    System.setProperty("user.language", "tr");
    System.err.println(System.getProperty("user.language"));

    genericBugTestCase("turkishLocaleBug");

    System.setProperty("user.language", "en");
    System.err.println(System.getProperty("user.language"));
  }

  /**
   * Test case for bug #135377: "Exception when running AQL"
   */
  @Test
  public void annotExceptionBug() throws Exception {
    startTest();

    if (false == SpeedTests.ANNOTATORS_PROJECT.exists()) {
      // SPECIAL CASE: Don't bother running the test if the user hasn't
      // checked out the "Annotators" project into his or her workspace.
      System.err.printf("Skipping test because Annotators project" + " is not present at %s.\n",
          SpeedTests.ANNOTATORS_PROJECT);
      return;
      // END SPECIAL CASE
    }

    // The named entity evaluation annotators have their own funky set of search paths.
    File evalAnnotDir = new File(SpeedTests.ANNOTATORS_PROJECT, "aql/neEvaluation");
    File dictRootDir = new File(evalAnnotDir, "GenericNE/dictionaries");
    File udfRootDir = new File(evalAnnotDir, "GenericNE/udfjars");

    setDataPath(dictRootDir.getCanonicalPath().toString(),
        evalAnnotDir.getCanonicalPath().toString(), udfRootDir.getCanonicalPath().toString());

    File aqlFile = new File(evalAnnotDir, "ne-library-annotators-for-CoNLL2003.aql");
    String docsFileName = String.format("%s/%s.del", DOCS_DIR, "annotExceptionBug");
    File newdocsFile = new File(docsFileName);

    // DocScan scan = DocScan.makeFileScan(docsFile);

    runNonModularAQLTest(newdocsFile, aqlFile);
  }

  /**
   * Test case for bug #140785: Special characters in inline regex or dictionary atoms in sequence
   * patterns cause parse error
   */
  @Test
  public void inlineRegexBug() throws Exception {
    startTest();

    genericBugTestCase("inlineRegexBug");
  }

  /**
   * Test case for LeftContextTok where token offset is specified as '0'.
   */
  @Test
  public void leftContextTokBug2() throws Exception {
    startTest();

    genericBugTestCase("leftContextTokBug2");
  }

  /**
   * Test case for bug #18638 - Runtime Exception in sort-merge join Looks like
   * SlowComparator.compare() passes a null memoization table Currently commented out. Whoever fixes
   * this bug should uncomment the test.
   */
  @Test
  public void mergeJoinBug() throws Exception {
    startTest();
    genericBugTestCase("mergeJoinBug");
  }

  /**
   * Test case for RightContextTok where token offset is specified as '0'.
   */
  @Test
  public void rightContextTokBug() throws Exception {
    startTest();
    genericBugTestCase("rightContextTokBug");
  }

  /**
   * Test case for bug #144994: Runtime exception thrown when using FollowedByTok
   * 
   * @throws Exception
   */
  @Test
  public void followedByTokBug2() throws Exception {
    startTest();
    String docsFilePath = DOCS_DIR + "/followedByTokBug2.zip";
    genericBugTestCase("followedByTokBug2", docsFilePath);
  }

  /**
   * Test case for bug [#148046] Extract split returns no answers when string to be split starts
   * with a split point.
   */
  @Test
  public void splitBug() throws Exception {
    startTest();
    genericBugTestCase("splitBug");
  }

  /**
   * Test case for defect : Error in wildcard expansion of select stmt when used with sub query
   */
  @Test
  public void subqueryWildCardExpansionBug() throws Exception {
    startTest();
    genericBugTestCase("subqueryNodeWildCardExpansionBug");
  }

  /**
   * Test case for bug# 16500 : HashJoin does not accept UDF calls as arguments
   */
  @Test
  public void udfReferencedInJoinPredicateBug() throws Exception {
    startTest();
    genericBugTestCase("udfReferencedInJoinPredicateBug");
  }

  /**
   * Test case for defect : ScalarList of Text types are not union compatible unless nested Text
   * types are identical
   */
  @Test
  public void scalarListOfTextUnionBug() throws Exception {
    startTest();
    genericBugTestCase("scalarListOfTextUnionBug");
  }

  /**
   * Test case for defect : Shared Dictionary Matching does not preserve select fields
   */
  @Test
  public void sdmPassThruColumnBug() throws Exception {
    startTest();
    genericBugTestCase("sdmPassThruColumnBug");
  }

  /**
   * Test case for defect# 29267: Explain button enabled when provenance rewrite fails. Provenance
   * rewrite exception: Schema [anonymous] does not contain field name '__auto__id'. Turns out it
   * was a problem with the SDM rewrite in the postprocessor, where the select list of each extract
   * dictionary statement on the same target was overriding any other select list of other extract
   * dictionary statements.
   */
  @Test
  public void sdmOverridingSelectBug() throws Exception {
    startTest();
    setDumpPlan(true);
    this.setPrintTups(true);
    genericBugTestCase("sdmOverridingSelectBug");
  }

  /**
   * Bug in inlining the dictionary file referred in extract stmt's select list
   */
  @Test
  public void dictionaryInliningBug() throws Exception {
    startTest();
    genericBugTestCase("dictionaryInliningBug");
  }

  /**
   * Test case for defect : Shared Regex Matching can produce duplicate outputs
   */
  @Test
  public void duplicateSRMViewBug() throws Exception {
    startTest();
    genericBugTestCase("duplicateSRMViewBug");
  }

  /**
   * Test case for defect : SystemT runtime code infers an incorrect return datatype for case
   * expression when used in select stmt. Disabled until the defect is resolved.
   */
  @Test
  public void caseTypeInferenceBug() throws Exception {
    startTest();
    genericBugTestCase("caseTypeInferenceBug");
    endTest();
  }

  /**
   * Test case for defect : "The compiler generates an invalid AOG leading the OG init code to throw
   * "Expected a span-producing type, but got 'Float'" "
   */
  @Test
  public void floatErrorBug() throws Exception {
    startTest();

    boolean caughtException = false;

    try {
      genericModularBugTestCase(TestConstants.ENRON_100_DUMP);
    } catch (CompilerException e) {

      caughtException = true;

      // We expect to get one error on line 30, column 65.
      List<Exception> allErrs = e.getAllCompileErrors();

      assertEquals("Number of compile errors", 1, allErrs.size());

      Exception error = allErrs.get(0);

      if (false == (error instanceof ParseException)) {
        throw new TextAnalyticsException("Caught %s instead of ParseException", error);
      }

      ParseException pe = (ParseException) error;
      assertEquals("Line number", 30, pe.getLine());
      assertEquals("Column number", 65, pe.currentToken.beginColumn);
    }

    if (false == caughtException) {
      throw new Exception("Did not catch CompilerException as expected.");
    }

    endTest();
  }

  /**
   * Test case for RTC defect A Span with offset-range [x-y] should be deemed to overlap across Span
   * with offset-range [x-x] and Span with offset-range [y-y]
   * 
   * @throws Exception
   */
  @Test
  public void overlapsBugTest() throws Exception {
    com.ibm.avatar.aql.compiler.Compiler compiler = null;
    try {
      startTest();
      setPrintTups(true);

      // Use the same AQL file and data as "overlapsBug"
      File aqlFile = new File(TestConstants.AQL_DIR, "AQLBugTests/overlapsBug.aql");
      File docsFile = new File(DOCS_DIR, "overlapsBug.del");

      // Compile with the low-level compiler API, so that we can set the planner.
      CompileAQLParams params = new CompileAQLParams();
      params.setInputFile(aqlFile);
      params.setOutputURI(curOutputDir.toURI().toString());
      params.setTokenizerConfig(getTokenizerConfig());

      compiler = new com.ibm.avatar.aql.compiler.Compiler();
      compiler.compile(params);

      runModule(docsFile, Constants.GENERIC_MODULE_NAME, params.getOutputURI());

      // Check that the output of using NLJoin to evaluate the Overlaps predicate is as expected
      compareAgainstExpected(true);
    } finally {
      if (null != compiler)
        compiler.deleteTempDirectory();
    }
  }

  /**
   * a. Test NLJoin enforcement upon the SystemT compiler, towards evaluating the Overlaps predicate
   * b. Test integrity of output of the module compiled in (a), against a custom data file This
   * test, taken together with {@link #overlapsViaSortMergeJoinTest()} can be used to establish
   * consistency of the output from our runtime against AQL using the Overlaps predicate in the
   * where clause.
   * 
   * @throws Exception
   */
  @Test
  public void overlapsViaNLJoinTest() throws Exception {
    com.ibm.avatar.aql.compiler.Compiler compiler = null;
    try {
      startTest();
      setPrintTups(true);

      // Use the same AQL file and data as "overlapsBug"
      File aqlFile = new File(TestConstants.AQL_DIR, "AQLBugTests/overlapsInWhereClause.aql");
      File docsFile = new File(DOCS_DIR, "overlapsBug.del");

      // Compile with the low-level compiler API, so that we can set the planner.
      CompileAQLParams params = new CompileAQLParams();
      params.setInputFile(aqlFile);
      params.setOutputURI(curOutputDir.toURI().toString());
      params.setTokenizerConfig(getTokenizerConfig());

      compiler = new com.ibm.avatar.aql.compiler.Compiler();

      // Override the default with NLJoin algorithm when evaluating Overlaps predicate
      Planner p = new Planner(ImplType.NAIVE);

      compiler.setPlanner(p);
      compiler.compile(params);

      // Assert that this module's compiled plan uses NLJoin, as enforced upon the compiler
      Assert.assertTrue("This module's plan doesn't utilize NLJoin!", isStringExistInAOG("NLJoin"));

      runModule(docsFile, Constants.GENERIC_MODULE_NAME, params.getOutputURI());

      // Check that the output of using NLJoin to evaluate the Overlaps predicate is as expected
      compareAgainstExpected(true);
    } finally {
      if (null != compiler)
        compiler.deleteTempDirectory();
    }
  }

  /**
   * a. Test SortMergeJoin enforcement via default compilation of the AQL code <br/>
   * b. Test integrity of output from running (a) against a custom data set <br/>
   * c. Test output seen in (b) against output seen in (b) of {@link #overlapsViaNLJoinTest()} [(c)
   * is to establish consistency of the output from our runtime against AQL using the Overlaps
   * predicate in the where clause irrespective of whether NLJoin or SortMergeJoin was used
   * accordingly.]
   * 
   * @throws Exception
   */
  @Test
  public void overlapsViaSortMergeJoinTest() throws Exception {
    com.ibm.avatar.aql.compiler.Compiler compiler = null;
    try {
      startTest();
      setPrintTups(true);

      // Use the same AQL file and data as "overlapsBug"
      File aqlFile = new File(TestConstants.AQL_DIR, "AQLBugTests/overlapsInWhereClause.aql");
      File docsFile = new File(DOCS_DIR, "overlapsBug.del");

      // Compile with the low-level compiler API, so that we can set the planner.
      CompileAQLParams params = new CompileAQLParams();
      params.setInputFile(aqlFile);
      params.setOutputURI(curOutputDir.toURI().toString());
      params.setTokenizerConfig(getTokenizerConfig());

      // No need to enforce planner specification - due to AQL being compiled, default chosen would
      // by SortMergeJoin
      compiler = new com.ibm.avatar.aql.compiler.Compiler();
      compiler.compile(params);

      // Assert that this module's compiled plan uses SortMergeJoin, by default
      Assert.assertTrue("This module's plan doesn't utilize SortMergeJoin!",
          isStringExistInAOG("SortMergeJoin"));

      runModule(docsFile, Constants.GENERIC_MODULE_NAME, curOutputDir.toURI().toString());

      // Check that the output of using SortMergeJoin to evaluate the Overlaps predicate is as
      // expected
      compareAgainstExpected(true);

      // Point expected directory to the files from the previous test, to establish consistency of
      // Overlaps function in
      // a join operation
      setExpectedDir(new File(getCurExpectedDir().toString(), "../overlapsViaNLJoinTest"));
      compareAgainstExpected(true);
    } finally {
      if (null != compiler)
        compiler.deleteTempDirectory();
    }
  }

  /**
   * Test to ensure that the end span in any span isn't extracted as part of a extract split
   * statement. [RTC 62173]
   * 
   * @throws Exception
   */
  @Test
  public void extractSplitEndBug() throws Exception {
    startTest();
    genericBugTestCase("extractSplitEndBug");
  }

  /*
   * HELPER FUNCTIONS
   */

  /**
   * A generic bug-derived test case. Assumes that an AQL module called (current test's prefix name)
   * and a docs file prefix.del will replicate the problem, and sends output to
   * testdata/regression/output/prefix.
   */
  @SuppressWarnings("unused")
  private void genericModularBugTestCase() throws Exception {
    String prefix = getCurPrefix();
    String docsFileName = String.format("%s/%s.del", DOCS_DIR, prefix);
    genericModularBugTestCase(docsFileName);
  }

  private void genericModularBugTestCase(String docsFileName) throws Exception {
    String prefix = getCurPrefix();

    String moduleName = prefix;
    File docsFile = new File(docsFileName);

    compileAndRunModule(moduleName, docsFile, null);

    File expectedDir = getCurExpectedDir();
    if (expectedDir != null) {
      File[] expectedFiles = expectedDir.listFiles();
      if (null == expectedFiles) {
        Log.log(MsgType.Info, "Skipping file comparison because dir %s does not exist.",
            expectedDir);
      } else {
        compareAgainstExpected(true);
      }
    }

  }

  /**
   * A generic bug-derived test case. Takes a prefix string as argument; assumes that an AQL file
   * called prefix.aql and a docs file prefix.del will replicate the problem, and sends output to
   * testdata/regression/output/prefix.
   */
  private void genericBugTestCase(String prefix) throws Exception {
    String docsFileName = String.format("%s/%s.del", DOCS_DIR, prefix);
    genericBugTestCase(prefix, docsFileName);

  }

  private void genericBugTestCase(String prefix, String docsFileName) throws Exception {
    String aqlFileName = String.format("%s/%s.aql", AQL_FILES_DIR, prefix);

    File newdocsFile = new File(docsFileName);

    runNonModularAQLTest(newdocsFile, aqlFileName);

    File expectedDir = getCurExpectedDir();
    if (expectedDir != null) {
      File[] expectedFiles = expectedDir.listFiles();
      if (null == expectedFiles) {
        Log.log(MsgType.Info, "Skipping file comparison because dir %s does not exist.",
            expectedDir);
      } else {
        compareAgainstExpected(true);
      }
    }
  }

  /**
   * Verifies that argument caching accounts for short circuit for Case statement thus respecting
   * its semantics <br>
   * This example specifically tests the CombineSpans function sample code provided by Thilo - RTC
   * 94431
   * 
   * @throws Exception
   */
  @Test
  public void caseStmtTest1() throws Exception {
    startTest();
    super.setPrintTups(true);

    final File DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR,
        String.format("%s/caseStmtInputs1.del", getClass().getSimpleName()));
    OperatorGraph og = compileAndLoadModule("combineSpansTest", null);
    annotateAndPrint(DOCS_FILE, og);
    compareAgainstExpected(false);

    endTest();
  }

  /**
   * Verifies that argument caching accounts for short circuit for Case statement thus respecting
   * its semantics <br>
   * This example tests the use of SpanBetween function without the IgnoreOrder flag by checking if
   * the spans are in the correct order - RTC 94431
   * 
   * @throws Exception
   */
  @Test
  public void caseStmtTest2() throws Exception {
    startTest();
    super.setPrintTups(true);

    final File DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR,
        String.format("%s/caseStmtInputs2.del", getClass().getSimpleName()));
    OperatorGraph og = compileAndLoadModule("spanBetweenTest", null);
    annotateAndPrint(DOCS_FILE, og);
    compareAgainstExpected(false);

    endTest();
  }

  /**
   * Verifies that argument caching accounts for short circuit for Case statement thus respecting
   * its semantics <br>
   * This example verifies different scenarios for case statement - RTC 94431
   * 
   * @throws Exception
   */
  @Test
  public void caseStmtTest3() throws Exception {
    startTest();
    super.setPrintTups(true);

    final File DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR,
        String.format("%s/caseStmtInputs3.del", getClass().getSimpleName()));
    OperatorGraph og = compileAndLoadModule("miscCaseTest", null);
    annotateAndPrint(DOCS_FILE, og);
    compareAgainstExpected(false);

    endTest();
  }

  /**
   * RTC defect - Verifies that RightContext, RightContextTok, LeftContextTok scalars work correctly
   * for arg values Document.text and 0
   * 
   * @throws Exception
   */
  @Test
  public void leftRightContextBugTest() throws Exception {
    startTest();
    super.setPrintTups(true);

    final File DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR,
        String.format("%s/testtranscript.json", getClass().getSimpleName()));
    OperatorGraph og = compileAndLoadModule("test", null);
    annotateAndPrint(DOCS_FILE, og);
    compareAgainstExpected(false);

    endTest();
  }

  /**
   * RTC defect - CombineSpans incorrectly returns [s1,e1] when s1=s2, e2>e1 and span 1 is
   * completely contained in span 2; it should return [s1, e2] AND in addition also check for all
   * different scenarios of CombineSpans and SpanBetween arguments to test the span interval
   * semantics
   * 
   * @throws Exception
   */
  @Test
  public void combineSpansBugTest() throws Exception {
    startTest();
    super.setPrintTups(true);

    final File DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR,
        String.format("%s/combineSpansBugTest.del", getClass().getSimpleName()));
    OperatorGraph og = compileAndLoadModule("test", null);
    annotateAndPrint(DOCS_FILE, og);
    compareAgainstExpected(false);

    endTest();
  }

  /**
   * RTC defect - The target of consolidate clause need not be present in the select list of the
   * rule. Make sure there is no "Error to bind" exception via this test. AQL should run fine and
   * produce the expected output.
   * 
   * @throws Exception
   */
  @Test
  public void consolidateTargetBugTest() throws Exception {
    startTest();
    super.setPrintTups(true);

    final File DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR,
        String.format("%s/consolidateBug.del", getClass().getSimpleName()));
    OperatorGraph og = compileAndLoadModule("test", null);
    annotateAndPrint(DOCS_FILE, og);
    compareAgainstExpected(false);

    endTest();
  }

  /**
   * RTC defect - Pass group by cols/consolidate clause cols for Naive planner implementation. Thus
   * this test will force the planner to be NAIVE to test join with a group by clause and a join
   * with a consolidate clause.
   * 
   * @throws Exception
   */
  @Test
  public void consolidateTargetBug1Test() throws Exception {
    startTest();
    super.setPrintTups(true);

    File aqlFile =
        new File(TestConstants.AQL_DIR, "AQLBugTests/testPlannerJoinGroupConsolidate.aql");
    File docsFile = new File(DOCS_DIR, "testPlannerJoinGroupConsolidate.del");
    Planner planner = new Planner(ImplType.NAIVE);

    genericTest(docsFile, aqlFile, planner);

    endTest();
  }

  /**
   * RTC defect - Pass group by cols/consolidate clause cols for Naive Merge planner implementation.
   * Thus this test will force the planner to be NAIVE to test join with a group by clause and a
   * join with a consolidate clause.
   * 
   * @throws Exception
   */
  @Test
  public void consolidateTargetBug2Test() throws Exception {
    startTest();
    super.setPrintTups(true);

    File aqlFile =
        new File(TestConstants.AQL_DIR, "AQLBugTests/testPlannerJoinGroupConsolidate.aql");
    File docsFile = new File(DOCS_DIR, "testPlannerJoinGroupConsolidate.del");
    Planner planner = new Planner(ImplType.NAIVE_MERGE);

    genericTest(docsFile, aqlFile, planner);

    endTest();
  }

  /**
   * RTC defect - Pass group by cols/consolidate clause cols for Random Merge planner
   * implementation. Thus this test will force the planner to be NAIVE to test join with a group by
   * clause and a join with a consolidate clause.
   * 
   * @throws Exception
   */
  @Test
  public void consolidateTargetBug3Test() throws Exception {
    startTest();
    super.setPrintTups(true);

    File aqlFile =
        new File(TestConstants.AQL_DIR, "AQLBugTests/testPlannerJoinGroupConsolidate.aql");
    File docsFile = new File(DOCS_DIR, "testPlannerJoinGroupConsolidate.del");
    Planner planner = new Planner(ImplType.RANDOM_MERGE);

    genericTest(docsFile, aqlFile, planner);

    endTest();
  }

  /**
   * RTC defect /GH issue 3609 - Ensure no NPE is seen when one of the inputs to the Overlaps
   * predicate function is null
   * 
   * @throws Exception
   */
  @Test
  public void overlapsMPNullBugTest() throws Exception {
    startTest();
    super.setPrintTups(true);

    final File DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR,
        String.format("%s/overlapsMPNullBug.del", getClass().getSimpleName()));
    OperatorGraph og = compileAndLoadModule("test", null);
    annotateAndPrint(DOCS_FILE, og);
    compareAgainstExpected(false);
  }

  /**
   * GHE issue #17 - NPE in AdjacentJoin when Null span is included
   * 
   * @throws Exception
   */
  @Test
  public void testNPEAdjacentJoinBug() throws Exception {

    startTest();

    setTokenizerConfig(TestConstants.STANDARD_TOKENIZER);

    genericBugTestCase("testNPEAdjacentJoinBug");

    endTest();
  }

  /**
   * GHE issue #28 - MatchesDict and ContainsDict bug."And" predicate returns null when any of
   * earlier input has unknown argument
   * 
   * @throws Exception
   */
  @Test
  public void testMatchContainDictBug() throws Exception {
    startTest();

    setPrintTups(true);

    setTokenizerConfig(TestConstants.STANDARD_TOKENIZER);

    genericBugTestCase("testMatchContainDictBug");

    endTest();
  }

  /**
   * GHE issue #18 - Sequence pattern group 0 outputs null, but group 1 outputs non-null
   *
   * @throws Exception
   */
  @Test
  public void nullSeqPatternOutputBug() throws Exception {

    startTest();

    File docsFile =
        new File(TestConstants.TEST_DOCS_DIR, "AQLBugTests/nullSeqPatternOutputBug.txt");
    String moduleName = "nullSeqPatternOutputBug";

    String[] inputModules = new String[] {moduleName};

    String outputModulePath = getCurOutputDir().toURI().toString();
    TokenizerConfig tokenizerCfg = TestConstants.STANDARD_TOKENIZER;

    setTokenizerConfig(tokenizerCfg);

    compileModules(inputModules, null);

    OperatorGraph og = OperatorGraph.createOG(inputModules, outputModulePath, null, tokenizerCfg);

    TupleSchema docSchema = og.getDocumentSchema();
    TextGetter labelGetter = docSchema.textAcc("label");
    Map<String, TupleSchema> outputViews = og.getOutputTypeNamesAndSchema();
    ToCSVOutput outCSV = new ToCSVOutput(outputViews, getCurOutputDir());

    DocReader docs = new DocReader(docsFile, docSchema, null);

    while (docs.hasNext()) {
      Tuple docTup = docs.next();
      Text label = labelGetter.getVal(docTup);

      Map<String, TupleList> annotations = og.execute(docTup, null, null);
      outCSV.write(label.getText(), annotations);
    }

    outCSV.close();

    compareAgainstExpected(false);

    endTest();
  }

  /**
   * Defect in regex views in which there is a labeled capturing group that is optional or can be
   * empty for any reason, the capturing group span is given offsets of -1,-1 which causes an error
   * to be thrown.
   * 
   * @throws Exception
   */
  @Test
  public void emptyCaptureGroupBug() throws Exception {
    startTest();
    super.setPrintTups(true);

    genericBugTestCase("emptyCaptureGroupBug");

    endTest();
  }

}
