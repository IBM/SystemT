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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.api.CompileAQLParams;
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.api.exceptions.CompilerException;
import com.ibm.avatar.api.tam.ModuleMetadata;
import com.ibm.avatar.aql.AQLParser;
import com.ibm.avatar.aql.CreateDictNode;
import com.ibm.avatar.aql.CreateExternalViewNode;
import com.ibm.avatar.aql.CreateFunctionNode;
import com.ibm.avatar.aql.CreateTableNode;
import com.ibm.avatar.aql.CreateViewNode;
import com.ibm.avatar.aql.DetagDocNode;
import com.ibm.avatar.aql.DetagDocSpecNode;
import com.ibm.avatar.aql.AQLParseTreeNode;
import com.ibm.avatar.aql.StatementList;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.aql.compiler.CompilerWarning;
import com.ibm.avatar.aql.compiler.ParseToCatalog;
import com.ibm.avatar.aql.compiler.CompilerWarning.WarningType;
import com.ibm.avatar.aql.doc.AQLDocComment;

/**
 * Various regression tests for AQL doc feature.
 * 
 */
public class AQLDocTests extends RuntimeTestHarness {

  /**
   * A collection of 1000 Enron emails shared by tests in this class.
   */
  public static final File DOCS_FILE = new File(TestConstants.DUMPS_DIR, "ensmall.zip");

  /**
   * Main method for invoking one test at a time.
   * 
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    AQLDocTests t = new AQLDocTests();
    t.setUp();

    long startMS = System.currentTimeMillis();

    t.createFunctionTest();

    long endMS = System.currentTimeMillis();

    t.tearDown();

    double elapsedSec = ((double) (endMS - startMS)) / 1000.0;
    System.err.printf("Test took %1.3f sec.\n", elapsedSec);
  }

  @Before
  public void setUp() {

  }

  @After
  public void tearDown() {

  }

  /**
   * Test case for associating AQL Doc comments with the right create view statement when there are
   * no Parse Exceptions.
   * 
   * @throws Exception
   */
  @Test
  public void createViewTest() throws Exception {
    startTest();

    // Expect AQL Doc comments to be associated with the following names
    Map<String, String> name2Comment = new HashMap<String, String>();
    name2Comment.put(String.format("%s.Test1", getCurPrefix()), "Test1");
    name2Comment.put(String.format("%s.Test2", getCurPrefix()), "Test2");
    name2Comment.put(String.format("%s.Test3", getCurPrefix()), "Test3");
    name2Comment.put(String.format("%s.Test4", getCurPrefix()), "Test4");
    name2Comment.put(String.format("%s.Test5", getCurPrefix()), "Test5");
    name2Comment.put(String.format("%s.Test6", getCurPrefix()), "Test6");
    name2Comment.put(String.format("%s.Test7", getCurPrefix()), "Test7");

    genericAQLDocCommentTest(name2Comment, true, null, null);
    compareMetadataAgainstExpected("metadata.xml");

    endTest();
  }

  /**
   * Test case for parsing empty AQL Doc comments.
   * 
   * @throws Exception
   */
  @Test
  public void emptyCommentTest() throws Exception {
    startTest();

    genericAQLDocCommentTest(null, true, null, null);
    compareMetadataAgainstExpected("metadata.xml");

    endTest();
  }

  /**
   * Test case for associating AQL Doc comments with the right create external view statement when
   * there are no Parse Exceptions.
   * 
   * @throws Exception
   */
  @Test
  public void createExternalViewTest() throws Exception {
    startTest();

    // Expect AQL Doc comments to be associated with the following names
    Map<String, String> name2Comment = new HashMap<String, String>();
    name2Comment.put(String.format("%s.Test1", getCurPrefix()), "Test1");
    name2Comment.put(String.format("%s.Test2", getCurPrefix()), "Test2");
    name2Comment.put(String.format("%s.Test3", getCurPrefix()), "Test3");
    name2Comment.put(String.format("%s.Test4", getCurPrefix()), "Test4");
    name2Comment.put(String.format("%s.Test5", getCurPrefix()), "Test5");
    name2Comment.put(String.format("%s.Test6", getCurPrefix()), "Test6");
    name2Comment.put(String.format("%s.Test7", getCurPrefix()), "Test7");

    genericAQLDocCommentTest(name2Comment, true, null, null);
    compareMetadataAgainstExpected("metadata.xml");

    endTest();
  }

  /**
   * Test case for associating AQL Doc comments with the right create table statement when there are
   * no Parse Exceptions.
   * 
   * @throws Exception
   */
  @Test
  public void createTableTest() throws Exception {
    startTest();

    // Expect AQL Doc comments to be associated with the following names
    Map<String, String> name2Comment = new HashMap<String, String>();
    name2Comment.put(String.format("%s.Test1", getCurPrefix()), "Test1");
    name2Comment.put(String.format("%s.Test2", getCurPrefix()), "Test2");
    name2Comment.put(String.format("%s.Test3", getCurPrefix()), "Test3");
    name2Comment.put(String.format("%s.Test4", getCurPrefix()), "Test4");
    name2Comment.put(String.format("%s.Test5", getCurPrefix()), "Test5");
    name2Comment.put(String.format("%s.Test6", getCurPrefix()), "Test6");
    name2Comment.put(String.format("%s.Test7", getCurPrefix()), "Test7");

    genericAQLDocCommentTest(name2Comment, true, null, null);
    compareMetadataAgainstExpected("metadata.xml");

    endTest();
  }

  /**
   * Test case for associating AQL Doc comments with the right create table statement when there are
   * no Parse Exceptions.
   * 
   * @throws Exception
   */
  @Test
  public void createExternalTableTest() throws Exception {
    startTest();

    // Expect AQL Doc comments to be associated with the following names
    Map<String, String> name2Comment = new HashMap<String, String>();
    name2Comment.put(String.format("%s.Test1", getCurPrefix()), "Test1");
    name2Comment.put(String.format("%s.Test2", getCurPrefix()), "Test2");
    name2Comment.put(String.format("%s.Test3", getCurPrefix()), "Test3");
    name2Comment.put(String.format("%s.Test4", getCurPrefix()), "Test4");
    name2Comment.put(String.format("%s.Test5", getCurPrefix()), "Test5");
    name2Comment.put(String.format("%s.Test6", getCurPrefix()), "Test6");
    name2Comment.put(String.format("%s.Test7", getCurPrefix()), "Test7");

    genericAQLDocCommentTest(name2Comment, true, null, null);
    compareMetadataAgainstExpected("metadata.xml");

    endTest();
  }

  /**
   * Test case for associating AQL Doc comments with the right create dictionary statement when
   * there are no Parse Exceptions.
   * 
   * @throws Exception
   */
  @Test
  public void createDictionaryTest() throws Exception {
    startTest();

    // Expect AQL Doc comments to be associated with the following names
    Map<String, String> name2Comment = new HashMap<String, String>();
    name2Comment.put(String.format("%s.Test1", getCurPrefix()), "Test1");
    name2Comment.put(String.format("%s.Test2", getCurPrefix()), "Test2");
    name2Comment.put(String.format("%s.Test3", getCurPrefix()), "Test3");
    name2Comment.put(String.format("%s.Test4", getCurPrefix()), "Test4");
    name2Comment.put(String.format("%s.Test5", getCurPrefix()), "Test5");
    name2Comment.put(String.format("%s.Test6", getCurPrefix()), "Test6");
    name2Comment.put(String.format("%s.Test7", getCurPrefix()), "Test7");

    genericAQLDocCommentTest(name2Comment, true, null, null);
    compareMetadataAgainstExpected("metadata.xml");

    endTest();
  }

  /**
   * Test case for associating AQL Doc comments with the right create external dictionary statement
   * when there are no Parse Exceptions.
   * 
   * @throws Exception
   */
  @Test
  public void createExternalDictionaryTest() throws Exception {
    startTest();

    // Expect AQL Doc comments to be associated with the following names
    Map<String, String> name2Comment = new HashMap<String, String>();
    name2Comment.put(String.format("%s.Test1", getCurPrefix()), "Test1");
    name2Comment.put(String.format("%s.Test2", getCurPrefix()), "Test2");
    name2Comment.put(String.format("%s.Test3", getCurPrefix()), "Test3");
    name2Comment.put(String.format("%s.Test4", getCurPrefix()), "Test4");
    name2Comment.put(String.format("%s.Test5", getCurPrefix()), "Test5");
    name2Comment.put(String.format("%s.Test6", getCurPrefix()), "Test6");
    name2Comment.put(String.format("%s.Test7", getCurPrefix()), "Test7");

    genericAQLDocCommentTest(name2Comment, true, null, null);
    compareMetadataAgainstExpected("metadata.xml");

    endTest();
  }

  /**
   * Test case for associating AQL Doc comments with the right create function statement when there
   * are no Parse Exceptions. Parses the AQL and verifies that the parse tree nodes have the
   * appropriate comments. FIXME: Does not verify that the comments are serialized in module
   * metadata. Enable the verification once the following defect is fixed: 25170: Function info is
   * not serialized in module metadata.
   * 
   * @throws Exception
   */
  @Test
  public void createFunctionTest() throws Exception {
    startTest();

    // Expect AQL Doc comments to be associated with the following names
    Map<String, String> name2Comment = new HashMap<String, String>();
    name2Comment.put(String.format("%s.Test1", getCurPrefix()), "Test1");
    name2Comment.put(String.format("%s.Test2", getCurPrefix()), "Test2");
    name2Comment.put(String.format("%s.Test3", getCurPrefix()), "Test3");
    name2Comment.put(String.format("%s.Test4", getCurPrefix()), "Test4");
    name2Comment.put(String.format("%s.Test5", getCurPrefix()), "Test5");
    name2Comment.put(String.format("%s.Test6", getCurPrefix()), "Test6");
    name2Comment.put(String.format("%s.Test7", getCurPrefix()), "Test7");

    genericAQLDocCommentTest(name2Comment, true, null, null);
    compareMetadataAgainstExpected("metadata.xml");
    endTest();
  }

  /**
   * Test case for associating AQL Doc comments with the right detag statement when there are no
   * Parse Exceptions. Parses the AQL and verifies that the parse tree nodes have the appropriate
   * comments. Currently fails because of defect : Compiler should maintain qualified names for
   * exported tables/functions/views/dictionaries.
   * 
   * @throws Exception
   */
  @Test
  public void detagTest() throws Exception {
    startTest();

    // Expect AQL Doc comments to be associated with the following names
    Map<String, String> name2Comment = new HashMap<String, String>();
    name2Comment.put(String.format("%s.Test1", getCurPrefix()), "Test1");
    name2Comment.put(String.format("%s.Test2", getCurPrefix()), "Test2");
    name2Comment.put(String.format("%s.Test3", getCurPrefix()), "Test3");
    name2Comment.put(String.format("%s.Test4", getCurPrefix()), "Test4");
    name2Comment.put(String.format("%s.Test5", getCurPrefix()), "Test5");
    name2Comment.put(String.format("%s.Test6", getCurPrefix()), "Test6");
    name2Comment.put(String.format("%s.Test7", getCurPrefix()), "Test7");

    genericAQLDocCommentTest(name2Comment, true, null, null);
    compareMetadataAgainstExpected("metadata.xml");

    endTest();
  }

  /**
   * Test case for associating AQL Doc comments with the detag views and detag auxiliary views, when
   * the detag statement contains multiple auxiliary views. Currently fails because of defect :
   * Compiler should maintain qualified names for exported tables/functions/views/dictionaries.
   * 
   * @throws Exception
   */
  @Test
  public void detagSingleAuxViewTest() throws Exception {
    startTest();

    // Expect AQL Doc comments to be associated with the following names
    Map<String, String> name2Comment = new HashMap<String, String>();
    name2Comment.put(String.format("%s.Test1", getCurPrefix()), "Test1");
    name2Comment.put(String.format("%s.Test2", getCurPrefix()), "Test2");

    genericAQLDocCommentTest(name2Comment, true, null, null);
    compareMetadataAgainstExpected("metadata.xml");

    endTest();
  }

  /**
   * Test case for associating AQL Doc comments with the detag views and detag auxiliary views, when
   * the detag statement contains multiple auxiliary views. Currently fails because of defect :
   * Compiler should maintain qualified names for exported tables/functions/views/dictionaries.
   * 
   * @throws Exception
   */
  @Test
  public void detagMultiAuxViewsTest() throws Exception {
    startTest();

    // Expect AQL Doc comments to be associated with the following names
    Map<String, String> name2Comment = new HashMap<String, String>();
    name2Comment.put(String.format("%s.Test1", getCurPrefix()), "Test1");
    name2Comment.put(String.format("%s.Test2", getCurPrefix()), "Test2");
    name2Comment.put(String.format("%s.Test3", getCurPrefix()), "Test3");

    genericAQLDocCommentTest(name2Comment, true, null, null);
    compareMetadataAgainstExpected("metadata.xml");

    endTest();
  }

  /**
   * Test case for associating AQL Doc comments with the right select into statement when there are
   * no Parse Exceptions.
   * 
   * @throws Exception
   */
  @Test
  public void selectIntoTest() throws Exception {
    startTest();

    // Expect AQL Doc comments to be associated with the following names
    Map<String, String> name2Comment = new HashMap<String, String>();
    name2Comment.put(String.format("%s.Test1", getCurPrefix()), "Test1");
    name2Comment.put(String.format("%s.Test2", getCurPrefix()), "Test2");
    name2Comment.put(String.format("%s.Test3", getCurPrefix()), "Test3");
    name2Comment.put(String.format("%s.Test4", getCurPrefix()), "Test4");
    name2Comment.put(String.format("%s.Test5", getCurPrefix()), "Test5");
    name2Comment.put(String.format("%s.Test6", getCurPrefix()), "Test6");
    name2Comment.put(String.format("%s.Test7", getCurPrefix()), "Test7");

    genericAQLDocCommentTest(name2Comment, true, null, null);
    compareMetadataAgainstExpected("metadata.xml");

    endTest();
  }

  /**
   * Test case for associating AQL Doc comments with the right statement in the presence of Parse
   * Exceptions. FIXME: update the test once this defect is fixed: 25172: The compiler returns a
   * false missing module statement
   * 
   * @throws Exception
   */
  @Test
  public void parseWithExceptions1Test() throws Exception {
    startTest();

    // Expect AQL Doc comments to be associated with the following names
    Map<String, String> name2Comment = new HashMap<String, String>();
    name2Comment.put(String.format("%s.Test2", getCurPrefix()), null);
    name2Comment.put(String.format("%s.Test4", getCurPrefix()), null);

    // Expect parse exceptions at these locations
    int[] lineNo = new int[] {21, 37};
    int[] colNo = new int[] {33, 1};

    genericAQLDocCommentTest(name2Comment, true, lineNo, colNo);

    endTest();
  }

  /**
   * Test case for associating AQL Doc comments with the right statement in the presence of Parse
   * Exceptions. FIXME: update the test once this defect is fixed: 25172: The compiler returns a
   * false missing module statement
   * 
   * @throws Exception
   */
  @Test
  public void parseWithExceptions2Test() throws Exception {
    startTest();

    // Expect AQL Doc comments to be associated with the following names
    Map<String, String> name2Comment = new HashMap<String, String>();
    name2Comment.put(String.format("%s.Test2", getCurPrefix()), null);

    // Expect parse exceptions at these locations
    int[] lineNo = new int[] {15};
    int[] colNo = new int[] {1};

    genericAQLDocCommentTest(name2Comment, true, lineNo, colNo);

    endTest();
  }

  /**
   * Test case for associating AQL Doc comments with the right statement in the presence of Parse
   * Exceptions and AQL doc comments in the middle of a statement. FIXME: update the test once this
   * defect is fixed: 25172: The compiler returns a false missing module statement
   * 
   * @throws Exception
   */
  @Test
  public void parseWithExceptions3Test() throws Exception {
    startTest();

    // Expect AQL Doc comments to be associated with the following names
    Map<String, String> name2Comment = new HashMap<String, String>();
    name2Comment.put(String.format("%s.Test0", getCurPrefix()), null);
    name2Comment.put(String.format("%s.Test2", getCurPrefix()), null);
    name2Comment.put(String.format("%s.Test3", getCurPrefix()), "Test3");
    name2Comment.put(String.format("%s.Test4", getCurPrefix()), null);

    // Expect parse exceptions at these locations
    int[] lineNo = new int[] {19};
    int[] colNo = new int[] {1};

    genericAQLDocCommentTest(name2Comment, true, lineNo, colNo);

    endTest();
  }

  /**
   * Test case for verifying that we handle single line comments immediately before EOF
   * 
   * @throws Exception
   */
  @Test
  public void singleLineCommentEOFTest() throws Exception {
    startTest();

    // Expect AQL Doc comments to be associated with the following names
    Map<String, String> name2Comment = new HashMap<String, String>();
    name2Comment.put(String.format("%s.Test1", getCurPrefix()), "Test1");

    genericAQLDocCommentTest(name2Comment, true, null, null);
    compareMetadataAgainstExpected("metadata.xml");

    endTest();
  }

  /**
   * Test case for verifying that we handle multi-line line comments immediately before EOF
   * 
   * @throws Exception
   */
  @Test
  public void multiLineCommentEOFTest() throws Exception {
    startTest();

    // Expect AQL Doc comments to be associated with the following names
    Map<String, String> name2Comment = new HashMap<String, String>();
    name2Comment.put(String.format("%s.Test1", getCurPrefix()), "Test1");

    // Expect no parse exceptions
    genericAQLDocCommentTest(name2Comment, true, null, null);
    compareMetadataAgainstExpected("metadata.xml");

    endTest();
  }

  /**
   * Test case for verifying that we handle unterminated multi-line line comments immediately before
   * EOF.
   * 
   * @throws Exception
   */
  @Test
  public void multiLineCommentUnterminatedTest() throws Exception {
    startTest();

    // Expect AQL Doc comments to be associated with the following names
    Map<String, String> name2Comment = new HashMap<String, String>();
    name2Comment.put(String.format("%s.Test1", getCurPrefix()), "Test1");

    // Expect parse exception at the following locations
    int[] lineNo = new int[] {15};
    int[] colNo = new int[] {74};
    genericAQLDocCommentTest(name2Comment, true, lineNo, colNo);

    endTest();
  }

  /**
   * Test case for verifying that we handle AQL Doc comments immediately before EOF
   * 
   * @throws Exception
   */
  @Test
  public void aqlDocCommentEOFTest() throws Exception {
    startTest();

    // Expect AQL Doc comments to be associated with the following names
    Map<String, String> name2Comment = new HashMap<String, String>();
    name2Comment.put(String.format("%s.Test1", getCurPrefix()), "Test1");

    genericAQLDocCommentTest(name2Comment, true, null, null);
    compareMetadataAgainstExpected("metadata.xml");

    endTest();
  }

  /**
   * Test case for verifying that we handle AQL Doc comments correctly in the presence of top-level
   * statements that do not consume AQL doc comments themselves.
   * 
   * @throws Exception
   */
  @Test
  public void otherStatementsTest() throws Exception {
    startTest();

    // Expect AQL Doc comments to be associated with the following names
    Map<String, String> name2Comment = new HashMap<String, String>();
    name2Comment.put(String.format("%s.Test1", getCurPrefix()), "Test1");
    name2Comment.put(String.format("%s.Test2", getCurPrefix()), null);

    genericAQLDocCommentTest(name2Comment, true, null, null);
    compareMetadataAgainstExpected("metadata.xml");

    endTest();
  }

  /**
   * Test case for verifying that we handle unterminated AQL Doc comments immediately before EOF
   * 
   * @throws Exception
   */
  @Test
  public void aqlDocCommentUnterminatedTest() throws Exception {
    startTest();

    // Expect AQL Doc comments to be associated with the following names
    Map<String, String> name2Comment = new HashMap<String, String>();
    name2Comment.put(String.format("%s.Test1", getCurPrefix()), "Test1");

    // Expect parse exception at the following locations
    int[] lineNo = new int[] {15};
    int[] colNo = new int[] {72};
    genericAQLDocCommentTest(name2Comment, true, lineNo, colNo);

    endTest();
  }

  /**
   * Test for associating the module comment when the module comment file is present in the module's
   * source directory.
   * 
   * @throws Exception
   */
  @Test
  public void moduleWithCommentTest() throws Exception {
    startTest();

    genericModuleCommentTest(null);
    compareMetadataAgainstExpected("metadata.xml");

    endTest();
  }

  /**
   * Test case for associating AQL Doc comments for the view Document from the file module.info with
   * the Document view metadata, when Document is an output view. Currently fails because SystemT
   * does not support Document as an output view.
   */
  @Test
  public void documentCommentOutputTest() throws Exception {
    startTest();

    // Expect AQL Doc comments to be associated with the following names
    Map<String, String> name2Comment = new HashMap<String, String>();

    // Expect parse exception at the following locations
    int[] lineNo = new int[] {10};
    int[] colNo = new int[] {1};

    genericAQLDocCommentTest(name2Comment, true, lineNo, colNo);
    // genericModuleCommentTest (false);
    // compareMetadataAgainstExpected ("metadata.xml");

    endTest();
  }

  /**
   * Test case for associating AQL Doc comments for the view Document from the file module.info with
   * the Document view metadata, when Document is not an output view. Essentially tests that
   * Document view metadata is always serialized in module metadata. Currently fails because that is
   * not the case.
   */
  @Test
  public void documentCommentNonOutputTest() throws Exception {
    startTest();

    genericModuleCommentTest(null);
    compareMetadataAgainstExpected("metadata.xml");

    endTest();
  }

  /**
   * Test for associating the module comment when the module comment file is present in the module's
   * source directory but it is empty.
   * 
   * @throws Exception
   */
  @Test
  public void moduleWithEmptyCommentTest() throws Exception {
    startTest();

    genericModuleCommentTest(null);
    compareMetadataAgainstExpected("metadata.xml");

    endTest();
  }

  /**
   * Test for associating the module comment when the module comment file is present in the module's
   * source directory but it is a directory. This test verifies that we get the appropriate compiler
   * warning when this happens.
   * 
   * @throws Exception
   */
  @Test
  public void moduleWithCommentFolderTest() throws Exception {
    startTest();

    WarningType[] warningTypes =
        new WarningType[] {WarningType.MODULE_COMMENT_LOCATION_IS_DIRECTORY};
    genericModuleCommentTest(warningTypes);
    compareMetadataAgainstExpected("metadata.xml");

    endTest();
  }

  /**
   * Test for associating the module comment when the module comment file is not present in the
   * module's source directory.
   * 
   * @throws Exception
   */
  @Test
  public void moduleWithoutCommentTest() throws Exception {
    startTest();

    genericModuleCommentTest(null);
    compareMetadataAgainstExpected("metadata.xml");

    endTest();
  }

  /*
   * ADD NEW TEST CASES HERE
   */

  /*
   * UTILITY METHODS
   */

  /**
   * TODO: move to RuntimeTestHarness Utility method to parse and compile a single test case
   * involving a module with the same name as the current test and verify that top-level statements
   * have the expected AQLDocComments. Assumes that the AQL file is located in:
   * 
   * <pre>
   * testdata/aql/[test class name]/[test case name]/[test case name].aql
   * </pre>
   * 
   * The method parses the AQL file and makes sure that the correct AQL doc comments are associated
   * with parse tree nodes. Optionally, it also compiles the module and verifies the either compile
   * exceptions are as expected, for AQL files with compile errors, or the module compiles
   * successfully and the AQL doc are properly serialized in the module meta data.
   * 
   * @param name2Comment a map from statement name to snippet of comment that should appear as part
   *        of the AQL Doc comment attached to the statement, or null if no AQL Doc Comment should
   *        be attached to the name. If you're not interested in verifying the AQL Doc Comment of a
   *        particular name, do not include the name in the map. The statement name must be
   *        qualified with the module name. If you're not interested in verifying any names, pass a
   *        null or an empty map.
   * @param compile whether to compile the code or not.
   * @param lineNo : list of line numbers where an Exception is expected. If expecting 0 exceptions,
   *        pass a null. Does not have any effect unless the caller requested compilation by setting
   *        the compile parameter to true.
   * @param colNo : list of column numbers where a ParseException is expected. If expecting 0
   *        exceptions, pass a null. If the caller is expecting an Exception with a line number, but
   *        no column number, specify -1 as the column number. Does not have any effect unless the
   *        caller requested compilation by setting the compile parameter to true.
   * @throws Exception
   */
  private void genericAQLDocCommentTest(Map<String, String> name2Comment, boolean compile,
      int[] lineNo, int[] colNo) throws Exception {
    String className = getClass().getSimpleName();
    String testcaseName = getCurPrefix();
    String moduleName = testcaseName;

    // Compute the location of the current test case's top-level AQL file.
    File aqlDir = new File(TestConstants.AQL_DIR, className);
    File aqlFile =
        new File(aqlDir, String.format("%s/%s/%s.aql", testcaseName, moduleName, moduleName));

    if (false == aqlFile.exists()) {
      throw new Exception(String.format("AQL file %s not found.", aqlFile));
    }

    // Parse the AQL file.
    AQLParser parser = new AQLParser(aqlFile);
    StatementList stmtList = parser.parse();

    // Initialize an empty list of names
    Map<String, String> foundSoFar = new HashMap<String, String>();

    // Verify that each valid statement has the appropriate AQL doc comment.
    List<AQLParseTreeNode> stmts = stmtList.getParseTreeNodes();
    for (AQLParseTreeNode stmt : stmts) {

      // Get the name of the statement and the AQLDocComment attached to it.
      AQLDocComment c = null;
      String name = null;

      if (stmt instanceof CreateViewNode) {
        c = ((CreateViewNode) stmt).getComment();
        name = ((CreateViewNode) stmt).getViewName();
      } else if (stmt instanceof CreateExternalViewNode) {
        c = ((CreateExternalViewNode) stmt).getComment();
        name = ((CreateExternalViewNode) stmt).getExternalViewName();
      } else if (stmt instanceof DetagDocNode) {
        c = ((DetagDocNode) stmt).getComment();
        name = ((DetagDocNode) stmt).getDetaggedDocName();
      } else if (stmt instanceof CreateFunctionNode) {
        c = ((CreateFunctionNode) stmt).getComment();
        name = ((CreateFunctionNode) stmt).getFunctionName();
      } else if (stmt instanceof CreateTableNode) {
        c = ((CreateTableNode) stmt).getComment();
        name = ((CreateTableNode) stmt).getTableName();
      } else if (stmt instanceof CreateDictNode) {
        c = ((CreateDictNode) stmt).getComment();
        name = ((CreateDictNode) stmt).getDictname();
      }

      // We found one of the top-level statements that is allowed to have a comment.
      if (null != name) {

        // Check whether the caller cares about this name. If not, ignore it and continue with the
        // next statement.
        if (null == name2Comment || !name2Comment.containsKey(name))
          continue;

        // Obtain the snippet of text that should be contained in the AQL Doc Comment for this view
        String snippet = name2Comment.get(name);

        // SPECIAL CASE: Detag auxiliary views get their comment from the parent detag view
        if (stmt instanceof DetagDocNode) {

          // Check whether this name should not be associated with any AQL Doc comment
          if (null == snippet) {
            Assert.assertTrue(String.format(
                "Expected no AQL Doc comment for name '%s'. Got instead: %s", name, c), null == c);
            continue;
          }

          // Handle the detag view itself
          String subComment = c.getDetagViewComment();

          // Add to the list of names found so far
          foundSoFar.put(name, subComment);

          Assert.assertTrue(String.format(
              "Expected an AQL Doc comment for name '%s' containing the string '%s'. Got instead: %s",
              name, snippet, subComment), null != subComment);

          // Check that the AQL doc comment for this name contains a certain snippet
          Assert.assertTrue(String.format(
              "Expected AQL Doc comment for name '%s' containing the string '%s'. Got instead: %s",
              name, snippet, subComment), -1 != subComment.indexOf(snippet));

          // Now check the auxiliary views
          ArrayList<DetagDocSpecNode> auxViews = ((DetagDocNode) stmt).getEntries();
          for (DetagDocSpecNode view : auxViews) {
            String viewName = view.getUnqualifiedName();
            String qualifiedViewName = view.getModuleName() + "." + viewName;

            // Obtain the snippet of text that should be contained in the AQL Doc Comment for this
            // view
            snippet = name2Comment.get(name);

            // Subcomment from the detag statement's comment for this auxiliary view
            subComment = c.getDetagAuxiliaryViewComment(viewName);

            // Add to the list of names found so far
            foundSoFar.put(qualifiedViewName, subComment);

            Assert.assertTrue(String.format(
                "Expected an AQL Doc comment for name '%s' containing the string '%s'. Got instead: %s",
                name, snippet, subComment), null != subComment);

            // Check that the AQL doc comment for this name contains a certain snippet
            Assert.assertTrue(String.format(
                "Expected AQL Doc comment for name '%s' containing the string '%s'. Got instead: %s",
                name, snippet, subComment), -1 != subComment.indexOf(snippet));
          }

        }
        // END SPECIAL CASE
        else {

          // Add to the list of names found so far
          if (null == c)
            foundSoFar.put(name, null);
          else
            foundSoFar.put(name, c.getCleanText());

          // Check whether this name should not be associated with any AQL Doc comment
          if (null == snippet) {
            Assert.assertTrue(String.format(
                "Expected no AQL Doc comment for name '%s'. Got instead: %s", name, c), null == c);
            continue;
          } else
            Assert.assertTrue(String.format(
                "Expected an AQL Doc comment for name '%s' containing the string '%s'. Got instead: %s",
                name, snippet, c), null != c);

          // Check that the AQL doc comment for this name contains a certain snippet
          Assert.assertTrue(String.format(
              "Expected AQL Doc comment for name '%s' containing the string '%s'. Got instead: %s",
              name, snippet, c), -1 != c.getText().indexOf(snippet));
        }
      }
    }

    // If we got here, then all names we encountered have the right AQL doc comments.
    // Check that we have encountered all expected names
    Set<String> left = new HashSet<String>();
    if (null != name2Comment)
      left.addAll(name2Comment.keySet());
    left.removeAll(foundSoFar.keySet());
    Assert.assertTrue(String.format("Did not find expected AQL Doc comments for names '%s'",
        Arrays.toString(left.toArray())), left.size() == 0);

    // Compile
    if (compile) {
      if (null == lineNo || 0 == lineNo.length) {

        // We're not expecting any exceptions. Compile and load the module
        OperatorGraph og = compileAndLoadModule(moduleName, null);
        ModuleMetadata mm = og.getModuleMetadata(getCurPrefix());

        // Write out the metadata so we can easily look at it without going through hoops
        File mmOut = new File(this.getCurOutputDir(), "metadata.xml");
        FileOutputStream out = new FileOutputStream(mmOut);
        mm.serialize(out);

        // Now verify that each parsed comment was accurately serialized
        for (String name : foundSoFar.keySet()) {
          String c = foundSoFar.get(name);

          // Get the unqualified view name so we can query the module metadata
          if (!name.startsWith(moduleName))
            Assert.fail(String.format(
                "Expected view names to be qualified with module name '%s'. Got view name '%s'",
                moduleName, name));

          String serializedCommentStr = null;

          if (null != mm.getViewMetadata(name))
            serializedCommentStr = mm.getViewMetadata(name).getComment();
          else if (null != mm.getTableMetadata(name))
            serializedCommentStr = mm.getTableMetadata(name).getComment();
          else if (null != mm.getDictionaryMetadata(name))
            serializedCommentStr = mm.getDictionaryMetadata(name).getComment();
          else if (null != mm.getFunctionMetadata(name))
            serializedCommentStr = mm.getFunctionMetadata(name).getComment();

          if (null == c) {
            // No comment in the AQL file. Verify that we have serialized an empty string comment
            Assert.assertTrue(String.format(
                "View name '%s' having no AQL doc comment was serialized with the comment:\n%s",
                name, serializedCommentStr), null == serializedCommentStr);
          } else {
            // We have a comment. Verify that it was serialized properly
            Assert.assertTrue(String.format(
                "View name '%s' having AQL doc comment:\n%s\n was serialized with a different comment:\n%s\n",
                name, c, serializedCommentStr), c.equals(serializedCommentStr));
          }
        }
      } else {
        // If we're expecting compiler exceptions, use the generic method to compile and check that
        // we receive the
        // expected exceptions
        boolean foundCompilerException = false;
        try {

          compileModule(moduleName);
        } catch (CompilerException e) {
          foundCompilerException = true;
          checkException(e, lineNo, colNo);
        } finally {
          if (!foundCompilerException)
            Assert.fail("Expected a Compiler Exception, but didn't get one.");
        }

      }
    }
  }

  /**
   * TODO: move to RuntimeTestHarness Utility method to parse and compile a single test case
   * involving a module with the same name as the current test and verify that the module comment is
   * parsed and serialized correctly. Assumes that the module is located in:
   * 
   * <pre>
   * testdata/aql/[test class name]/[test case name]
   * </pre>
   * 
   * The method parses the module to catalog and makes sure that the correct module comment is
   * stored in the catalog. Optionally, it also verifies whether a compiler warning has been
   * generated as expected if the module comment could not be read for some reason. The method also
   * compiles the module and verifies that module comment (if any) has been correctly serialized to
   * the module metadata.
   * 
   * @param warningTypes whether to verify that module-related compiler warning has been generated.
   * @throws Exception
   */
  private void genericModuleCommentTest(WarningType[] warningTypes) throws Exception {
    // Compute the location of the current test case's top-level AQL file.
    String moduleName = getCurPrefix();
    // Module directory URI
    String inputModuleURI = new File(getCurTestDir(), moduleName).toURI().toString();
    // URI where compiled modules should be dumped
    String compiledModuleURI = new File(String.format("%s", getCurOutputDir())).toURI().toString();

    // Prepare compilation parameters
    CompileAQLParams params = new CompileAQLParams();
    params.setInputModules(new String[] {inputModuleURI});
    params.setOutputURI(compiledModuleURI);

    // Parse to catalog
    ParseToCatalog parser = new ParseToCatalog();
    Catalog catalog = parser.parse(params);

    // Ensure we get the same number and type of compiler warnings
    if (null != warningTypes) {
      ArrayList<CompilerWarning> warnings = catalog.getWarnings();
      Assert.assertEquals("Number of CompilerWarnings does not match", warningTypes.length,
          warnings.size());
      for (int i = 0; i < warningTypes.length; i++) {
        WarningType expected = warningTypes[i];
        WarningType actual = warnings.get(i).getType();

        Assert.assertEquals("CompilerWarning type does not match", expected, actual);

      }
    }

    // Compile and load the module and verify the parsed comment is equal to the serialized comment.
    // Note that although the caller expected a warning, that should not have prevented the
    // successful compilation of
    // the module.
    try {
      OperatorGraph og = compileAndLoadModule(moduleName, null);
      ModuleMetadata mm = og.getModuleMetadata(getCurPrefix());

      // Write out the metadata so we can easily look at it without going through hoops
      File mmOut = new File(this.getCurOutputDir(), "metadata.xml");
      FileOutputStream out = new FileOutputStream(mmOut);
      mm.serialize(out);

      // The parsed comment from the catalog
      String parsedModuleComment = null;
      if (null != catalog.getComment())
        parsedModuleComment = catalog.getComment().getCleanText();
      // The serialized comment from the module metadata
      String serializedModuleComment = mm.getComment();

      // Make sure we serialized the same thing that we parsed
      Assert.assertTrue(
          String.format(
              "Parsed module comment:\n%s\nis different than serialized module comment:\n%s\n",
              parsedModuleComment, serializedModuleComment),
          ((null == parsedModuleComment && null == serializedModuleComment)
              || ((null != parsedModuleComment)
                  && parsedModuleComment.equals(serializedModuleComment))));

    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(
          "Expected successful compilation and loading for the module, but it failed instead.");
    }
  }

}
