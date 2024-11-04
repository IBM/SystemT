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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.api.ExternalTypeInfo;
import com.ibm.avatar.api.ExternalTypeInfoFactory;
import com.ibm.avatar.api.exceptions.InvalidTableEntryException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.AQLParseTreeNode;
import com.ibm.avatar.aql.AQLParser;
import com.ibm.avatar.aql.CreateTableNode;
import com.ibm.avatar.aql.ParseException;

import org.junit.Assert;

/**
 * This test harness contains tests to exercises, the support for 'create external table ...'
 * statement; tests span across parser, compiler and loader.
 * 
 */
public class ExternalTableTests extends RuntimeTestHarness {
  /** Location of modular and non modular AQL files used for this test */
  private static final String AQL_FILES_MODULE__DIR = TestConstants.AQL_DIR + "/ExternalTableTests";

  /**
   * Main method for invoking one test at a time.
   * 
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    ExternalTableTests test = new ExternalTableTests();
    test.setUp();

    long startMS = System.currentTimeMillis();

    test.loadExtTableFloatBoolTest();

    long endMS = System.currentTimeMillis();

    test.tearDown();

    double elapsedSec = (endMS - startMS) / 1000.0;
    System.err.printf("Test took %1.3f sec.\n", elapsedSec);
  }

  @Before
  public void setUp() {}

  @After
  public void tearDown() {}

  // Parser tests start
  /**
   * Test to verify the parser for 'create external table ...' statement; this test asserts the
   * content of the parse tree nodes: to verify that table is being marked external/internal and
   * required/allow_empty, according to declaration of the table in AQL.
   * 
   * @throws Exception
   */
  @SuppressWarnings("deprecation")
  @Test
  public void externalTableParseTest() throws Exception {
    String aqlFileName =
        String.format("%s/%s.aql", AQL_FILES_MODULE__DIR, "externalTableParseTest");
    AQLParser parser = new AQLParser(new File(aqlFileName));
    List<AQLParseTreeNode> parseTreeNodes = parser.parse().getParseTreeNodes();
    // we expect five parse tree nodes: two for 'create external table ...' statement with
    // 'allow_empty' flag
    // two with 'required' flag, and one for 'create table ...'
    Assert.assertEquals(5, parseTreeNodes.size());

    // Assert parse tree node for the extTab1
    CreateTableNode extTab1 = (CreateTableNode) parseTreeNodes.get(0);
    Assert.assertEquals("extTab1", extTab1.getTableName());
    // assert, that the table is external
    Assert.assertTrue(extTab1.getIsExternal());
    // table can be empty - 'allow_empty true'
    Assert.assertTrue(extTab1.isAllowEmpty());
    Assert.assertNull(extTab1.isRequired());

    // Assert parse tree node for the extTab2
    CreateTableNode extTab2 = (CreateTableNode) parseTreeNodes.get(1);
    Assert.assertEquals("extTab2", extTab2.getTableName());
    // assert, that the table is external
    Assert.assertTrue(extTab2.getIsExternal());
    // table must not be empty - 'allow_empty false'
    Assert.assertFalse(extTab2.isAllowEmpty());
    Assert.assertNull(extTab2.isRequired());

    // Assert parse tree node for the requiredTrue table
    CreateTableNode requiredTrue = (CreateTableNode) parseTreeNodes.get(2);
    Assert.assertEquals("requiredTrue", requiredTrue.getTableName());
    // assert, that the table is external
    Assert.assertTrue(requiredTrue.getIsExternal());
    // table filename required - 'required true'
    Assert.assertTrue(requiredTrue.isRequired());
    Assert.assertNull(requiredTrue.isAllowEmpty());

    // Assert parse tree node for the requiredTrue table
    CreateTableNode requiredFalse = (CreateTableNode) parseTreeNodes.get(3);
    Assert.assertEquals("requiredFalse", requiredFalse.getTableName());
    // assert, that the table is external
    Assert.assertTrue(requiredFalse.getIsExternal());
    // table filename not required - 'required false'
    Assert.assertFalse(requiredFalse.isRequired());
    Assert.assertNull(requiredFalse.isAllowEmpty());

    // Assert parse tree node for the intTab1
    CreateTableNode intTab1 = (CreateTableNode) parseTreeNodes.get(4);
    Assert.assertEquals("intTab1", intTab1.getTableName());
    // assert, that the table is internal
    Assert.assertFalse(intTab1.getIsExternal());
    try {
      // 'allow_empty' flag is allowed only for external tables
      Assert.assertNull(intTab1.isAllowEmpty());
    } catch (UnsupportedOperationException e) {
      Assert.assertTrue(true);
    }

    try {
      // 'required' flag is allowed only for external tables
      Assert.assertNull(intTab1.isRequired());

    } catch (UnsupportedOperationException e) {
      Assert.assertTrue(true);
      return;
    }

    // control should not come here
    Assert.fail();
  }

  /**
   * Test to verify parser error handling for 'create external table ...' statement; when invoked in
   * backward compatibility mode.<br>
   * Note: Remove this test once we remove support for non modular AQL.
   * 
   * @throws Exception
   */
  @Test
  public void extTabBackwardCompatibilityTest() throws Exception {
    String aqlFileName =
        String.format("%s/%s.aql", AQL_FILES_MODULE__DIR, "externalTableParseTest");
    AQLParser parser = new AQLParser(new File(aqlFileName));
    // move parser to backward compatibility mode
    parser.setBackwardCompatibilityMode(true);

    List<ParseException> parseErrors = parser.parse().getParseErrors();
    // In backward compatibility mode, we expect parse error for all instance of 'create external
    // table ...' statement

    ParseException pe = parseErrors.get(0);
    // Assert error location
    Assert.assertTrue(pe.getFileName().contains("externalTableParseTest"));
    Assert.assertEquals(7, pe.getLine());
    // Assert error message
    Assert.assertEquals("create external table statement only supported in v2.0+, ignoring...",
        pe.getErrorDescription());

    pe = parseErrors.get(1);
    // Assert error location
    Assert.assertTrue(pe.getFileName().contains("externalTableParseTest"));
    Assert.assertEquals(11, pe.getLine());
    // Assert error message
    Assert.assertEquals("create external table statement only supported in v2.0+, ignoring...",
        pe.getErrorDescription());

  }

  /**
   * Test to verify common errors in 'create external table' statement are caught. <br>
   * In particular, both flags being set, neither flag being set, and the flags being set out of
   * order.
   * 
   * @throws Exception
   */
  @Test
  public void invalidExtTableFlags() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNo = new int[] {8, 12, 16, 21, 26, 30, 34, 38, 43};
    int[] colNo = new int[] {1, 1, 1, 1, 1, 1, 1, 1, 31};

    compileModuleAndCheckErrors("invalidFlags", lineNo, colNo);

    endTest();

  }

  // Parser tests end

  // Loader validation test start
  /**
   * Test to verify, that the loader throws an exception, when a null ETI is passed and there are
   * required tables in the module.
   * 
   * @throws Exception
   */
  @Test
  public void extTableNullETITest() throws Exception {
    startTest();
    try {
      // Null ETI object; extTab1 is a required table
      compileAndLoadModule("extTableNullETITest", null);
    } catch (Exception e) {
      Assert.assertEquals(
          "External artifacts passed to the extractor are empty. Provide entries for all required external dictionaries and tables. Required external dictionaries are: []. Required external tables are: [extTableNullETITest.extTab2].",
          e.getMessage());
      return;
    }

    // control should have not reached here
    Assert.fail();
    endTest();
  }

  /**
   * Test to verify, that the loader throws an exception, when the passed ETI does not contain entry
   * for required table in the module.
   * 
   * @throws Exception
   */
  @Test
  public void etiMissingReqExtTableTest() throws Exception {
    startTest();

    ExternalTypeInfo externalTypeInfo = ExternalTypeInfoFactory.createInstance();
    String tableFileURI = new File(AQL_FILES_MODULE__DIR + "/ExtTab1.csv").toURI().toString();
    externalTypeInfo.addTable("etiMissingReqExtTableTest.extTab1", tableFileURI);

    try {
      compileAndLoadModule("etiMissingReqExtTableTest", externalTypeInfo);
    } catch (Exception e) {
      // missing required extTab2 table
      Assert.assertEquals(
          "[Required external tables [etiMissingReqExtTableTest.extTab2] are missing in external type info object]",
          e.getMessage());
      return;
    }
    // control should have not reached here
    Assert.fail();

    endTest();
  }

  /**
   * Test to verify, that the loader throws an exception, when the passed ETI contain an empty
   * table(empty TupleList/empty csv file) for required table in the module.
   * 
   * @throws Exception
   */
  @Test
  public void etiWithEmptyReqExtTableTest() throws Exception {
    startTest();

    ExternalTypeInfo externalTypeInfo = ExternalTypeInfoFactory.createInstance();
    String tableFileURI = new File(AQL_FILES_MODULE__DIR + "/ExtTab1.csv").toURI().toString();
    externalTypeInfo.addTable("etiWithEmptyReqExtTableTest.extTab1", tableFileURI);
    tableFileURI = new File(AQL_FILES_MODULE__DIR + "/emptyExtTab2.csv").toURI().toString();
    externalTypeInfo.addTable("etiWithEmptyReqExtTableTest.extTab2", tableFileURI);

    try {
      compileAndLoadModule("etiWithEmptyReqExtTableTest", externalTypeInfo);
    } catch (Exception e) {
      // Empty required extTab2 table
      Assert.assertEquals(
          "The following external tables, which are mandatory, are empty: [etiWithEmptyReqExtTableTest.extTab2].",
          e.getMessage());
      return;
    }
    // control should have not reached here
    Assert.fail();

    endTest();
  }

  /**
   * Test to verify, that the loader throws an exception, when the passed ETI contains entries not
   * declared in the module.
   * 
   * @throws Exception
   */
  @Test
  public void etiWithNotDeclaredExtTabTest() throws Exception {
    startTest();

    ExternalTypeInfo externalTypeInfo = ExternalTypeInfoFactory.createInstance();
    String tableFileURI = new File(AQL_FILES_MODULE__DIR + "/ExtTab1.csv").toURI().toString();
    externalTypeInfo.addTable("etiWithNotDeclaredExtTabTest.extTabNotDeclared", tableFileURI);
    tableFileURI = new File(AQL_FILES_MODULE__DIR + "/ExtTab2.csv").toURI().toString();
    externalTypeInfo.addTable("etiWithNotDeclaredExtTabTest.extTab2", tableFileURI);

    try {
      compileAndLoadModule("etiWithNotDeclaredExtTabTest", externalTypeInfo);
    } catch (Exception e) {
      // External table extTabNotDeclared not declared in the loaded module
      Assert.assertEquals(
          "[External tables [etiWithNotDeclaredExtTabTest.extTabNotDeclared] specified in external type info object are not part of any of the loaded modules.]",
          e.getMessage());
      return;
    }
    // control should have not reached here
    Assert.fail();

    endTest();
  }

  /**
   * Test to verify, that the loader throws an exception, when the passed ETI contains table
   * entries(from csv file) which does not adhere to table schema declared in 'create external table
   * ...' statement.
   * 
   * @throws Exception
   */
  @Test
  public void etiWithInvalidEntriesForExtTabTest() throws Exception {
    startTest();

    ExternalTypeInfo externalTypeInfo = ExternalTypeInfoFactory.createInstance();
    String tableFileURI =
        new File(AQL_FILES_MODULE__DIR + "/invalidExtTab2.csv").toURI().toString();
    externalTypeInfo.addTable("etiWithInvalidEntriesForExtTabTest.extTab2", tableFileURI);

    try {
      compileAndLoadModule("etiWithInvalidEntriesForExtTabTest", externalTypeInfo);
      Assert.fail("Control should have not reached here; we expect an exception.");
    } catch (InvalidTableEntryException e) {
      // entries in invalidExtTab2.csv file, contains tuple which does not adhere to schema of table
      // extTab2
      System.err.printf("Got error message '%s'\n", e.getMessage());
      // e.printStackTrace ();
      Assert.assertTrue(Pattern.matches(
          "In the csv file '.*/invalidExtTab2.csv', on line 1, field value 'id2' in column 1 is not a valid Integer.",
          e.getMessage()));
    }

    endTest();
  }

  /**
   * Test to verify, that the loader throws an exception, when the passed ETI contains table
   * entries(from TupleList) which does not adhere to table schema declared in 'create external
   * table ...' statement.
   * 
   * @throws Exception
   */
  @Test
  public void etiWithInvalidEntriesForExtTab2Test() throws Exception {
    startTest();

    ExternalTypeInfo externalTypeInfo = ExternalTypeInfoFactory.createInstance();
    ArrayList<ArrayList<String>> tableEntries = new ArrayList<ArrayList<String>>();
    // Tuple#1
    ArrayList<String> tup1 = new ArrayList<String>();
    tup1.add("1");
    tup1.add("Text for id#1");

    // Tuple#2- Containing invalid value for field ID
    ArrayList<String> tup2 = new ArrayList<String>();
    tup2.add("An integer expected");
    tup2.add("Text for id#2");

    tableEntries.add(tup1);
    tableEntries.add(tup2);
    externalTypeInfo.addTable("etiWithInvalidEntriesForExtTab2Test.extTab2", tableEntries);

    try {
      compileAndLoadModule("etiWithInvalidEntriesForExtTab2Test", externalTypeInfo);
      Assert.fail("Control should have not reached here; we expect an exception.");
    } catch (InvalidTableEntryException e) {
      System.err.printf("Got error message: %s\n", e.getMessage());
      Assert.assertEquals(
          "For table 'etiWithInvalidEntriesForExtTab2Test.extTab2', entry number 2, field value 'id2' in column 1 is not a valid Integer.",
          e.getMessage());
    }

    endTest();
  }

  /**
   * Test to verify, that the loader throws an exception, when the passed ETI contains an invalid
   * CSV file as a table.
   *
   * @throws Exception
   */
  @Test
  public void etiWithInvalidCsvForExtTabTest() throws Exception {
    startTest();

    ExternalTypeInfo externalTypeInfo = ExternalTypeInfoFactory.createInstance();
    String tableFileURI =
        new File(AQL_FILES_MODULE__DIR + "/invalidCsvExtTab.csv").toURI().toString();
    externalTypeInfo.addTable("etiWithInvalidCsvForExtTabTest.extTab1", tableFileURI);

	try {
		compileAndLoadModule("etiWithInvalidCsvForExtTabTest", externalTypeInfo);
		Assert.fail("Control should have not reached here; we expect an exception.");
	} catch (InvalidTableEntryException e) {
		System.err.printf("Got error message: %s\n", e.getMessage());
		StringBuilder sb = new StringBuilder();
		String expected = sb.append("In the csv file '").append(tableFileURI).append(
				"', on line 2, Unterminated quoted field at end of CSV line. Beginning of lost text: [text2\"bad line to parse.\n3,text3\n].")
				.toString();
		Assert.assertEquals(expected, e.getMessage());
	}

	endTest();
}

  /**
   * Test to verify, that the loader **does not** throws exception, when passed ETI, that does not
   * contain entries for optional tables in the module.
   * 
   * @throws Exception
   */
  @Test
  public void etiMissingOptionalExtTabTest() throws Exception {
    startTest();

    ExternalTypeInfo externalTypeInfo = ExternalTypeInfoFactory.createInstance();
    String tableFileURI = new File(AQL_FILES_MODULE__DIR + "/ExtTab2.csv").toURI().toString();
    externalTypeInfo.addTable("etiMissingOptionalExtTabTest.extTab2", tableFileURI);

    try {
      compileAndLoadModule("etiMissingOptionalExtTabTest", externalTypeInfo);
    } catch (Exception e) {
      // there should be no error for external type info object does not contain entries for
      // optional tables
      Assert.fail();
    }
    // If here, extTab2 loaded
    endTest();
  }

  /**
   * Test to verify that the loader throws error, while loading external table from csv file without
   * header.
   * 
   * @throws Exception
   */
  @Test
  public void csvMissingHeaderTest() throws Exception {
    startTest();

    ExternalTypeInfo externalTypeInfo = ExternalTypeInfoFactory.createInstance();
    String tableFileURI =
        new File(AQL_FILES_MODULE__DIR + "/csvWithoutHeader.csv").toURI().toString();
    externalTypeInfo.addTable("csvMissingHeaderTest.extTab2", tableFileURI);

    try {
      compileAndLoadModule("csvMissingHeaderTest", externalTypeInfo);
      Assert.fail("Control should have not reached here; we expect an exception.");
    } catch (Exception e) {
      Assert.assertTrue(Pattern.matches(
          "The CSV file '.*csvWithoutHeader.csv' is missing the required header.", e.getMessage()));
    }

    endTest();
  }

  /**
   * Test to verify that the loader throws error, while loading external table from csv file with
   * incompatible header. <br>
   * This test captures the scenario mentioned in defect#40493.
   * 
   * @throws Exception
   */
  @Test
  public void csvIncompatibleHeaderTest() throws Exception {
    startTest();

    ExternalTypeInfo externalTypeInfo = ExternalTypeInfoFactory.createInstance();
    String tableFileURI =
        new File(AQL_FILES_MODULE__DIR + "/module1.NameToLocation.csv").toURI().toString();
    externalTypeInfo.addTable("module1.NameToLocation", tableFileURI);

    try {
      compileAndLoadModule("module1", externalTypeInfo);
      Assert.fail("Control should have not reached here; we expect an exception.");
    } catch (Exception e) {
      String expectedPattern =
          "The header of CSV file '.*module1.NameToLocation.csv' differs from the schema of the external table.\n"
              + "Schema of CSV header: \\[Name\\].\n"
              + "Schema of external table: \\[Name, Location\\].";

      if (false == Pattern.matches(expectedPattern, e.getMessage())) {
        e.printStackTrace();
        throw new TextAnalyticsException(
            "Expected exception to match the pattern:\n" + "%s\n"
                + "but instead caught exception with error message:\n    %s",
            expectedPattern, e.getMessage());
      }
    }

    endTest();
  }

  /**
   * Test to verify that the loader does not throw error, while loading external table from csv file
   * with compatible header containing a BOM character. <br>
   * This test captures the scenario mentioned in defect .
   * 
   * @throws Exception
   */
  @Test
  public void csvBOMHeaderTest() throws Exception {
    startTest();

    ExternalTypeInfo externalTypeInfo = ExternalTypeInfoFactory.createInstance();

    // this CSV file starts with a BOM character that should be ignored by the reader
    String tableFileURI = new File(AQL_FILES_MODULE__DIR + "/bomHeader.csv").toURI().toString();
    externalTypeInfo.addTable("GeneralCondition.Condition_TABLE_UMLSDSALLW", tableFileURI);

    try {
      compileAndLoadModule("GeneralCondition", externalTypeInfo);
    } catch (Exception e) {
      // There should be no exception
      Assert.fail();
    }

    endTest();
  }

  // Loader validation test end

  // Loader test start

  /**
   * Test to verify, that the loader populated the operator graph with tuples from external tables.
   * 
   * @throws Exception
   */
  @Test
  public void loadExtTableTest() throws Exception {
    startTest();

    ExternalTypeInfo externalTypeInfo = ExternalTypeInfoFactory.createInstance();
    String tableFileURI = new File(AQL_FILES_MODULE__DIR + "/ExtTab1.csv").toURI().toString();
    externalTypeInfo.addTable("loadExtTableTest.extTab1", tableFileURI);
    tableFileURI = new File(AQL_FILES_MODULE__DIR + "/ExtTab2.csv").toURI().toString();
    externalTypeInfo.addTable("loadExtTableTest.extTab2", tableFileURI);

    setPrintTups(true);
    compileAndRunModule("loadExtTableTest", new File(TestConstants.ENRON_1_DUMP), externalTypeInfo);

    // truncateExpectedFiles ();
    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Test to verify that external tables with fields of type Float and Boolean work fine.
   * 
   * @throws Exception
   */
  @Test
  public void loadExtTableFloatBoolTest() throws Exception {
    startTest();

    ExternalTypeInfo externalTypeInfo = ExternalTypeInfoFactory.createInstance();
    String tableFileURI =
        new File(AQL_FILES_MODULE__DIR + "/ExtTabFloatBool.csv").toURI().toString();
    externalTypeInfo.addTable("loadExtTableFloatBoolTest.MyTable", tableFileURI);

    setPrintTups(true);
    compileAndRunModule("loadExtTableFloatBoolTest", new File(TestConstants.ENRON_1_DUMP),
        externalTypeInfo);

    // truncateExpectedFiles ();
    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Tests loading an external table with values defined in memory, not from a file.
   * 
   * @throws Exception
   */
  @Test
  public void loadExtTableFromMemoryTest() throws Exception {
    startTest();

    setPrintTups(true);

    // Prepare ETI
    ExternalTypeInfo eti = ExternalTypeInfoFactory.createInstance();
    ArrayList<ArrayList<String>> tableEntries = new ArrayList<ArrayList<String>>();

    // row 1
    ArrayList<String> row1 = new ArrayList<String>();
    row1.add("IBM");
    row1.add("USA");
    tableEntries.add(row1);

    // row 2
    ArrayList<String> row2 = new ArrayList<String>();
    row2.add("Infosys");
    row2.add("India");
    tableEntries.add(row2);

    eti.addTable("sample.Company2Location", tableEntries);

    compileAndRunModule("sample", new File(TestConstants.ENRON_1_DUMP), eti);
    compareAgainstExpected(true);

    endTest();
  }

  // Loader test end

}
