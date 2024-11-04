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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.datamodel.FieldGetter;
import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.algebra.datamodel.Text;
import com.ibm.avatar.algebra.datamodel.TextSetter;
import com.ibm.avatar.algebra.datamodel.Tuple;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.api.CompileAQLParams;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.ExternalTypeInfo;
import com.ibm.avatar.api.ExternalTypeInfoFactory;
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.api.exceptions.CompilerException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.catalog.Catalog;
import com.ibm.avatar.aql.catalog.DetagCatalogEntry;
import com.ibm.avatar.aql.catalog.ExternalViewCatalogEntry;
import com.ibm.avatar.aql.catalog.TableCatalogEntry;
import com.ibm.avatar.aql.catalog.ViewCatalogEntry;
import com.ibm.avatar.aql.compiler.Compiler;

/**
 * Verifies the correctness of types inferred during compilation phase
 * 
 */
public class TypeInferenceTests extends RuntimeTestHarness {
  private static final int DETAG = 0;
  private static final int TABLE = 1;
  private static final int VIEW = 2;
  private static final int EXTVIEW = 3;

  protected File aqlDir;

  @Before
  public void setUp() {
    String className = getClass().getSimpleName();
    aqlDir = new File(TestConstants.AQL_DIR, className);
  }

  @After
  public void tearDown() {

  }

  /**
   * Main method for invoking one test at a time.
   * 
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    TypeInferenceTests t = new TypeInferenceTests();
    t.setUp();

    long startMS = System.currentTimeMillis();

    t.tableExtractionTest();

    long endMS = System.currentTimeMillis();

    t.tearDown();

    double elapsedSec = ((double) (endMS - startMS)) / 1000.0;
    System.err.printf("Test took %1.3f sec.\n", elapsedSec);
  }

  /**
   * Verifies that schema is inferred correctly for a detag view
   * 
   * @throws Exception
   */
  @Test
  public void detaggerSimpleSchemaTest() throws Exception {
    startTest();

    Map<String, TupleSchema> schemaMap = new HashMap<String, TupleSchema>();

    TupleSchema s1 = new TupleSchema(Constants.DOCTEXT_COL, FieldType.TEXT_TYPE);
    s1.setName("detaggedDoc");
    schemaMap.put("detaggedDoc", s1);

    TupleSchema s2 = new TupleSchema("match", FieldType.SPAN_TYPE);
    s2.setName("titles");
    schemaMap.put("titles", s2);

    TupleSchema s3 = new TupleSchema(new String[] {"href", "match",},
        new FieldType[] {FieldType.TEXT_TYPE, FieldType.SPAN_TYPE});
    s3.setName("Anchor");
    schemaMap.put("Anchor", s3);

    genericTest(schemaMap, DETAG);

    endTest();
  }

  /**
   * Verifies that schema is inferred correctly when a view is created from a lookup table
   * 
   * @throws Exception
   */
  @Test
  public void tableAndViewSchemaTest() throws Exception {
    startTest();

    Map<String, TupleSchema> tableSchemaMap = new HashMap<String, TupleSchema>();

    TupleSchema s1 = new TupleSchema(new String[] {"name", "location",},
        new FieldType[] {FieldType.TEXT_TYPE, FieldType.TEXT_TYPE});
    s1.setName("NameToLocation");
    tableSchemaMap.put("NameToLocation", s1);

    TupleSchema s2 = new TupleSchema(new String[] {"location", "lat", "long"},
        new FieldType[] {FieldType.TEXT_TYPE, FieldType.INT_TYPE, FieldType.INT_TYPE});
    s2.setName("LocationToCoords");
    tableSchemaMap.put("LocationToCoords", s2);

    genericTest(tableSchemaMap, TABLE);

    // ///////////////////////////////////////////
    Map<String, TupleSchema> viewSchemaMap = new HashMap<String, TupleSchema>();
    TupleSchema s3 =
        new TupleSchema(new String[] {"company"}, new FieldType[] {FieldType.SPAN_TYPE});
    s3.setName("Company");
    viewSchemaMap.put("Company", s3);

    TupleSchema s4 = new TupleSchema(new String[] {"loc", "company"},
        new FieldType[] {FieldType.TEXT_TYPE, FieldType.SPAN_TYPE});
    s4.setName("CompanyLoc");
    viewSchemaMap.put("CompanyLoc", s4);

    genericTest(viewSchemaMap, VIEW);
    endTest();
  }

  /**
   * Verifies that schema is inferred correctly for a view with subqueries
   * 
   * @throws Exception
   */
  @Test
  public void viewWithSubQueriesSchemaTest() throws Exception {
    startTest();

    Map<String, TupleSchema> viewSchemaMap = new HashMap<String, TupleSchema>();

    TupleSchema s1 = new TupleSchema(new String[] {"name"}, new FieldType[] {FieldType.SPAN_TYPE});
    s1.setName("FirstName");
    viewSchemaMap.put("FirstName", s1);

    TupleSchema s2 = new TupleSchema(new String[] {"num"}, new FieldType[] {FieldType.SPAN_TYPE});
    s2.setName("PhoneNumber");
    viewSchemaMap.put("PhoneNumber", s2);

    TupleSchema s3 = new TupleSchema(new String[] {"name", "num", "personphone"},
        new FieldType[] {FieldType.SPAN_TYPE, FieldType.SPAN_TYPE, FieldType.SPAN_TYPE});
    s3.setName("PersonPhone");
    viewSchemaMap.put("PersonPhone", s3);

    genericTest(viewSchemaMap, VIEW);
    endTest();
  }

  /**
   * Verifies that SchemaInferrer infers text types correctly
   * 
   * @throws Exception
   */
  @Test
  public void textTypeTest() throws Exception {
    startTest();

    Map<String, TupleSchema> viewSchemaMap = new HashMap<String, TupleSchema>();

    TupleSchema s1 = new TupleSchema(new String[] {"match"}, new FieldType[] {FieldType.TEXT_TYPE});
    s1.setName("ViewWithText");
    viewSchemaMap.put("ViewWithText", s1);

    TupleSchema s2 = new TupleSchema(new String[] {"match"}, new FieldType[] {FieldType.TEXT_TYPE});
    s2.setName("ViewWithTextCopy");
    viewSchemaMap.put("ViewWithTextCopy", s2);

    genericTest(viewSchemaMap, VIEW);

    endTest();
  }

  /**
   * Verifies that schema is inferred correctly for a union statement
   * 
   * @throws Exception
   */
  @Test
  public void unionSchemaTest() throws Exception {
    startTest();

    Map<String, TupleSchema> viewSchemaMap = new HashMap<String, TupleSchema>();

    TupleSchema s1 = new TupleSchema(new String[] {"name"}, new FieldType[] {FieldType.SPAN_TYPE});
    s1.setName("FirstName");
    viewSchemaMap.put("FirstName", s1);

    TupleSchema s2 = new TupleSchema(new String[] {"num"}, new FieldType[] {FieldType.SPAN_TYPE});
    s2.setName("PhoneNumber");
    viewSchemaMap.put("PhoneNumber", s2);

    TupleSchema s3 = new TupleSchema(new String[] {"person", "phone", "personphone"},
        new FieldType[] {FieldType.SPAN_TYPE, FieldType.SPAN_TYPE, FieldType.SPAN_TYPE});
    s3.setName("PersonPhoneAll");
    viewSchemaMap.put("PersonPhoneAll", s3);

    // schemas of Unions
    TupleSchema s4 = new TupleSchema(new String[] {"match"}, new FieldType[] {FieldType.SPAN_TYPE});
    s4.setName("Union1");
    viewSchemaMap.put("Union1", s4);

    TupleSchema s5 = new TupleSchema(new String[] {"match"}, new FieldType[] {FieldType.SPAN_TYPE});
    s5.setName("Union2");
    viewSchemaMap.put("Union2", s5);

    TupleSchema s6 = new TupleSchema(new String[] {"match"}, new FieldType[] {FieldType.SPAN_TYPE});
    s6.setName("Union3");
    viewSchemaMap.put("Union3", s6);

    genericTest(viewSchemaMap, VIEW);

    endTest();
  }

  /**
   * Test to verify the inferred schema of views dependent on custom document schema( define through
   * 'require document ...' statement). <br>
   * 
   * @throws Exception
   */
  @Test
  public void customDocSchemaTest() throws Exception {
    Compiler compiler = null;
    try {
      startTest();

      Map<String, TupleSchema> viewSchemaMap = new HashMap<String, TupleSchema>();

      TupleSchema s1 = new TupleSchema(new String[] {"file1Field1", "file1Field2", "file2Field1"},
          new FieldType[] {FieldType.TEXT_TYPE, FieldType.INT_TYPE, FieldType.TEXT_TYPE});
      s1.setName("customDocSchemaTest.testView");
      viewSchemaMap.put("testView", s1);

      TupleSchema s2 = new TupleSchema(new String[] {"file2Field1", "alias2"},
          new FieldType[] {FieldType.TEXT_TYPE, FieldType.INT_TYPE});
      s2.setName("customDocSchemaTest.testView1");
      viewSchemaMap.put("testView1", s2);

      // Compile
      CompileAQLParams params = new CompileAQLParams();
      params.setInputModules(new String[] {getCurTestDir().toURI().toString()});
      params.setOutputURI(getCurOutputDir().toURI().toString());
      params.setTokenizerConfig(getTokenizerConfig());

      compiler = new Compiler();
      compiler.compile(params);

      // Fetch populated catalog from compiler
      Catalog catalog = compiler.getCatalog();

      // Verify actual inferred schema with expected
      verifySchemas(catalog, viewSchemaMap, VIEW);

      endTest();
    } finally {
      if (null != compiler)
        compiler.deleteTempDirectory();
    }
  }

  /**
   * Verifies that the schemas of statements input to UnionAll statement is inferred correctly
   * 
   * @throws Exception
   */
  @Test
  public void unionAllUnknownTypeTest() throws Exception {
    Compiler compiler = null;
    try {
      startTest();
      // Test 1 : compilation should be successful without any errors
      compileModules(new String[] {"module1", "module2"}, null);

      // clean up for next test
      getCurOutputDir().delete();
      getCurOutputDir().mkdirs();

      // Test 2: Check if schemas of module2.IndicatorInvalid1 and module2.IndicatorInvalid2 are
      // same
      Map<String, TupleSchema> viewSchemaMap = new HashMap<String, TupleSchema>();

      TupleSchema schemaIndicatorInvalid1 =
          new TupleSchema(new String[] {"metric", "amount", "match"},
              new FieldType[] {FieldType.SPAN_TYPE, FieldType.SPAN_TYPE, FieldType.SPAN_TYPE});
      schemaIndicatorInvalid1.setName("module2.IndicatorInvalid1");
      viewSchemaMap.put("module2.IndicatorInvalid1", schemaIndicatorInvalid1);

      TupleSchema schemaIndicatorInvalid2 =
          new TupleSchema(new String[] {"metric", "amount", "match"},
              new FieldType[] {FieldType.SPAN_TYPE, FieldType.SPAN_TYPE, FieldType.SPAN_TYPE});
      schemaIndicatorInvalid2.setName("module2.IndicatorInvalid2");
      viewSchemaMap.put("module2.IndicatorInvalid2", schemaIndicatorInvalid2);

      // URI where compiled modules should be dumped
      String compiledModuleURI =
          new File(String.format("%s", getCurOutputDir())).toURI().toString();

      // Compile module1
      CompileAQLParams params = new CompileAQLParams();
      params
          .setInputModules(new String[] {new File(getCurTestDir(), "module1").toURI().toString()});
      params.setOutputURI(compiledModuleURI);
      params.setModulePath(getCurOutputDir().toURI().toString());
      params.setTokenizerConfig(getTokenizerConfig());
      compiler = new Compiler();
      compiler.compile(params);
      compiler.deleteTempDirectory();

      // compile module2
      params
          .setInputModules(new String[] {new File(getCurTestDir(), "module2").toURI().toString()});
      compiler = new Compiler();
      compiler.compile(params);

      // Fetch populated catalog from compiler
      Catalog catalog = compiler.getCatalog();

      // Verify actual inferred schema with expected
      verifySchemas(catalog, viewSchemaMap, VIEW);
      endTest();
    } finally {
      if (null != compiler)
        compiler.deleteTempDirectory();

    }
  }

  /**
   * Test case that verifies that Text objects are correctly tagged with the names and appropriate
   * columns of any views in which they appear.
   */
  @Test
  public void textColnameTest() throws Exception {
    startTest();

    // The outputs we're looking for are only accessible via the Java API, so load up an
    // OperatorGraph instance.
    OperatorGraph og = compileAndLoadModule("textColnameTest", null);

    // Construct a dummy document so we can get some output
    String docText = "Hello world.  My name is Beaufort.";
    TupleSchema docSchema = og.getDocumentSchema();
    TextSetter setText = docSchema.textSetter("text");
    FieldGetter<Text> textGetter = docSchema.textAcc("text");
    Tuple docTup = docSchema.createTup();
    setText.setVal(docTup, docText);

    // First, check that Document.text is correctly labeled.
    Text docTextObj = textGetter.getVal(docTup);

    final String DOCTEXT_COLNAME = "Document.text";

    Pair<String, String> docColName = docTextObj.getViewAndColumnName();
    String docColNameStr = docColName.first + "." + docColName.second;
    if (false == (DOCTEXT_COLNAME.equals(docColNameStr))) {
      throw new TextAnalyticsException("Document.text labeled with incorrect view/column '%s'",
          docColNameStr);
    }

    Map<String, TupleList> results = og.execute(docTup, null, null);

    // Expected labels for each of the different columns
    final String BEAUFORT_COLNAME = "textColnameTest.CapsWord.beaufort";
    final String YO_COLNAME = "textColnameTest.CapsWord.yo";
    final String NAMESTR_COLNAME = "textColnameTest.JoinResult.nameStr";
    final String TEXTCOPY_COLNAME = "Document.text";
    final String HELLO_COLNAME = "textColnameTest.Beaufort.hello";
    final String DETAGGED_DOC_1_COLNAME = "textColnameTest.DetaggedDoc1.text";
    final String DETAGGED_DOC_2_COLNAME = "textColnameTest.DetaggedDoc2.text";
    final String DETAGGED_DOC_3_COLNAME = "textColnameTest.DetaggedDoc3.text";

    {
      // The view CapsWord should have three columns of type Text
      TupleList capsWordTups = results.get("textColnameTest.CapsWord");

      System.err.printf("%d tuples in CapsWord view.\n", capsWordTups.size());
      assertEquals(3, capsWordTups.size());

      FieldGetter<Text> yoGetter = capsWordTups.getSchema().textAcc("yo");
      FieldGetter<Text> beaufortGetter = capsWordTups.getSchema().textAcc("beaufort");
      FieldGetter<Text> textCopyGetter = capsWordTups.getSchema().textAcc("docText");

      for (int i = 0; i < capsWordTups.size(); i++) {
        Tuple tup = capsWordTups.getElemAtIndex(i);

        // The columns yo and beaufort contain Text objects generated within the view
        Pair<String, String> yoPair = yoGetter.getVal(tup).getViewAndColumnName();
        assertEquals(YO_COLNAME, yoPair.first + "." + yoPair.second);

        Pair<String, String> beaufortPair = beaufortGetter.getVal(tup).getViewAndColumnName();
        assertEquals(BEAUFORT_COLNAME, beaufortPair.first + "." + beaufortPair.second);

        // CapsWord.text is a copy of Document.text and should be labeled as such
        Pair<String, String> textCopyPair = textCopyGetter.getVal(tup).getViewAndColumnName();
        assertEquals(DOCTEXT_COLNAME, textCopyPair.first + "." + textCopyPair.second);
      }
    }

    {
      // The view CapsWordCopy has one Text column, whose values come from CapsWord
      TupleList capsWordCopyTups = results.get("textColnameTest.CapsWordCopy");

      System.err.printf("%d tuples in CapsWordCopy view.\n", capsWordCopyTups.size());
      assertEquals(3, capsWordCopyTups.size());

      // In CapsWordCopy, the "beaufort" column is renamed to "col"
      FieldGetter<Text> beaufortGetter = capsWordCopyTups.getSchema().textAcc("col");

      for (int i = 0; i < capsWordCopyTups.size(); i++) {
        Tuple tup = capsWordCopyTups.getElemAtIndex(i);

        Pair<String, String> beaufortPair = beaufortGetter.getVal(tup).getViewAndColumnName();
        assertEquals(BEAUFORT_COLNAME, beaufortPair.first + "." + beaufortPair.second);
      }
    }

    {
      // The view JoinResult has a number of different Text columns.
      TupleList joinResultTups = results.get("textColnameTest.JoinResult");

      System.err.printf("%d tuples in JoinResult view.\n", joinResultTups.size());
      assertEquals(3, joinResultTups.size());

      FieldGetter<Text> helloGetter = joinResultTups.getSchema().textAcc("hello");
      FieldGetter<Text> yoGetter = joinResultTups.getSchema().textAcc("yo");
      FieldGetter<Text> beaufortGetter = joinResultTups.getSchema().textAcc("beaufort");
      FieldGetter<Text> docTextGetter = joinResultTups.getSchema().textAcc("docText");
      FieldGetter<Text> textCopyGetter = joinResultTups.getSchema().textAcc("textCopy");
      FieldGetter<Text> nameStrGetter = joinResultTups.getSchema().textAcc("nameStr");

      for (int i = 0; i < joinResultTups.size(); i++) {
        Tuple tup = joinResultTups.getElemAtIndex(i);
        System.out.println(tup);

        Pair<String, String> helloPair = helloGetter.getVal(tup).getViewAndColumnName();
        assertEquals(HELLO_COLNAME, helloPair.first + "." + helloPair.second);

        Pair<String, String> yoPair = yoGetter.getVal(tup).getViewAndColumnName();
        assertEquals(YO_COLNAME, yoPair.first + "." + yoPair.second);

        Pair<String, String> beaufortPair = beaufortGetter.getVal(tup).getViewAndColumnName();
        assertEquals(BEAUFORT_COLNAME, beaufortPair.first + "." + beaufortPair.second);

        Pair<String, String> docTextPair = docTextGetter.getVal(tup).getViewAndColumnName();
        assertEquals(DOCTEXT_COLNAME, docTextPair.first + "." + docTextPair.second);

        Pair<String, String> textCopyPair = textCopyGetter.getVal(tup).getViewAndColumnName();
        assertEquals(TEXTCOPY_COLNAME, textCopyPair.first + "." + textCopyPair.second);

        Pair<String, String> nameStrPair = nameStrGetter.getVal(tup).getViewAndColumnName();
        assertEquals(NAMESTR_COLNAME, nameStrPair.first + "." + nameStrPair.second);
      }
    }

    {
      // The view DetaggedDoc1 has one Text column.
      TupleList detaggedDoc1Tups = results.get("textColnameTest.DetaggedDoc1");

      System.err.printf("%d tuples in DetaggedDoc1 view.\n", detaggedDoc1Tups.size());
      assertEquals(1, detaggedDoc1Tups.size());

      FieldGetter<Text> detaggedTextGetter = detaggedDoc1Tups.getSchema().textAcc("text");

      Tuple tup = detaggedDoc1Tups.getElemAtIndex(0);

      Pair<String, String> textPair = detaggedTextGetter.getVal(tup).getViewAndColumnName();
      assertEquals(DETAGGED_DOC_1_COLNAME, textPair.first + "." + textPair.second);
    }

    {
      // The view IndirectDetaggedDoc1 has one Text column, which actually comes from DetaggedDoc1
      TupleList tups = results.get("textColnameTest.IndirectDetaggedDoc1");

      System.err.printf("%d tuples in IndirectDetaggedDoc1 view.\n", tups.size());
      assertEquals(1, tups.size());

      FieldGetter<Text> detaggedTextGetter = tups.getSchema().textAcc("text");

      Tuple tup = tups.getElemAtIndex(0);

      Pair<String, String> textPair = detaggedTextGetter.getVal(tup).getViewAndColumnName();
      assertEquals(DETAGGED_DOC_1_COLNAME, textPair.first + "." + textPair.second);
    }

    {
      // The view DetaggedDoc3 has one Text column
      TupleList tups = results.get("textColnameTest.DetaggedDoc3");

      System.err.printf("%d tuples in DetaggedDoc3 view.\n", tups.size());
      assertEquals(1, tups.size());

      FieldGetter<Text> detaggedTextGetter = tups.getSchema().textAcc("text");

      Tuple tup = tups.getElemAtIndex(0);

      Pair<String, String> textPair = detaggedTextGetter.getVal(tup).getViewAndColumnName();
      assertEquals(DETAGGED_DOC_3_COLNAME, textPair.first + "." + textPair.second);
    }

    {
      // The view IndirectDetaggedDoc2 has one Text column, which actually comes from DetaggedDoc2
      TupleList tups = results.get("textColnameTest.IndirectDetaggedDoc2");

      System.err.printf("%d tuples in IndirectDetaggedDoc2 view.\n", tups.size());
      assertEquals(1, tups.size());

      FieldGetter<Text> detaggedTextGetter = tups.getSchema().textAcc("text");

      Tuple tup = tups.getElemAtIndex(0);

      Pair<String, String> textPair = detaggedTextGetter.getVal(tup).getViewAndColumnName();
      assertEquals(DETAGGED_DOC_2_COLNAME, textPair.first + "." + textPair.second);
    }

    endTest();
  }

  /**
   * Test case for defect : Type inference problem: Error determining return type of UDF returning
   * scalar list
   */
  @Test
  public void tableExtractionTest() throws Exception {
    startTest();

    // Extractor has required external types, so we need an ExternalTypeInfo object to pass
    // load-time validation
    // Due to a misunderstanding regarding the "allow_empty" parameter, we need to put dummy values
    // in to these external types, instead of just using empty values.
    ExternalTypeInfo dummyETI = ExternalTypeInfoFactory.createInstance();

    final String[] REQUIRED_DICT_NAMES =
        {"annotator_library.month", "table_types.financial_table_row_column_headers",
            "table_types.financial_table_invalid_table_clues",
            "table_types.financial_table_titlekeys", "table_types.financial_table_invalid_titles",
            "table_types.financial_table_titlekeys_notes"};

    ArrayList<String> dummyList = new ArrayList<String>();
    dummyList.add("hello world");
    for (String dictName : REQUIRED_DICT_NAMES) {
      dummyETI.addDictionary(dictName, dummyList);
    }

    final String REQUIRED_TABLE_NAME = "annotator_library.Time_Period_Table";

    ArrayList<ArrayList<String>> dummyTupleList = new ArrayList<ArrayList<String>>();
    ArrayList<String> dummyTuple = new ArrayList<String>();
    for (int i = 0; i < 5; i++) {
      dummyTuple.add("dummy value");
    }
    dummyTupleList.add(dummyTuple);
    dummyETI.addTable(REQUIRED_TABLE_NAME, dummyTupleList);

    compileAndLoadModules(
        new String[] {"annotator_library", "detag_doc", "java_udf", "table_types"}, dummyETI);

  }

  /**
   * Test to verify that type inference doesn't add null schemas in the catalog for views that fail
   * type inference, leading type inference for upstream views to run into NullPointerException.
   * 
   * @throws Exception
   */
  @Test
  public void nullSchemaTest() throws Exception {
    startTest();

    int[] lineNums = {26, 35};
    int[] colNums = {8, 7};

    boolean caughtException = false;
    try {
      super.compileModule("module1");
    } catch (CompilerException e) {
      checkException(e, lineNums, colNums);
      caughtException = true;

      // Verify the 2nd exception has the right message
      Exception pe = e.getAllCompileErrors().get(1);
      String EXPECTED_MSG = "Error determining schema of 'FailTypeInference'";
      if (-1 == pe.getMessage().indexOf(EXPECTED_MSG))
        throw new TextAnalyticsException(
            "Expected that the second exception has message:\n'%s'\nbut got exception with message\n%s",
            EXPECTED_MSG, pe.getMessage());
    }

    if (false == caughtException) {
      throw new TextAnalyticsException("Expected %d compile errors but found none.",
          lineNums.length);
    }

    endTest();
  }

  // //////////////////// Helper methods ////////////////////////////

  private void genericTest(Map<String, TupleSchema> schemaMap, int entityType) throws Exception {
    if (schemaMap == null || schemaMap.size() == 0) {
      throw new Exception("Invalid schema map");
    }

    // Compute the location of the current test case's top-level AQL file.
    File aqlFile = new File(aqlDir, String.format("%s.aql", getCurPrefix()));
    // File aogFile = new File (aogDir, String.format ("%s.aog", getCurPrefix ()));

    CompileAQLParams params = new CompileAQLParams();
    params.setInputFile(aqlFile);
    params.setOutputURI(getCurOutputDir().toURI().toString());
    params.setDataPath(TestConstants.TESTDATA_DIR);
    params.setTokenizerConfig(getTokenizerConfig());

    Compiler compiler = new Compiler();

    try {
      compiler.compile(params);

      Catalog catalog = compiler.getCatalog();
      verifySchemas(catalog, schemaMap, entityType);
    } finally {
      compiler.deleteTempDirectory();
    }
  }

  private void verifySchemas(Catalog catalog, Map<String, TupleSchema> schemaMap, int entityType)
      throws Exception {

    for (Iterator<String> iterator = schemaMap.keySet().iterator(); iterator.hasNext();) {
      String entityName = iterator.next();
      TupleSchema schemaActual = getSchema(catalog, entityName, entityType);
      TupleSchema schemaExpected = schemaMap.get(entityName);
      assertEquals(schemaExpected, schemaActual);
    }
  }

  private TupleSchema getSchema(Catalog catalog, String entityName, int entityType)
      throws ParseException {
    TupleSchema retSchema = null;

    switch (entityType) {
      case DETAG:
        DetagCatalogEntry dce = (DetagCatalogEntry) catalog.lookupView(entityName, null);
        retSchema = dce.getDetagSchema();
        break;
      case TABLE:
        TableCatalogEntry tce = (TableCatalogEntry) catalog.lookupTable(entityName);
        retSchema = tce.getSchema();
        break;
      case VIEW:
        ViewCatalogEntry vce = (ViewCatalogEntry) catalog.lookupView(entityName, null);
        retSchema = vce.getSchema();
        break;
      case EXTVIEW:
        ExternalViewCatalogEntry evce =
            (ExternalViewCatalogEntry) catalog.lookupView(entityName, null);
        retSchema = evce.getSchema();
        break;
    }
    return retSchema;
  }

}
