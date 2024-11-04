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
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Test;

import com.ibm.avatar.algebra.util.dict.DictParams;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.algebra.util.tokenize.TokenizerConfig;
import com.ibm.avatar.api.ExternalTypeInfo;
import com.ibm.avatar.api.ExternalTypeInfoFactory;
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.aql.AQLParseTreeNode;
import com.ibm.avatar.aql.AQLParser;
import com.ibm.avatar.aql.CreateDictNode;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.StatementList;

public class ExternalDictionaryTests extends RuntimeTestHarness {
  /** Location of modular and non modular AQL files used for this test */
  private static final String AQL_FILES_MODULE__DIR =
      TestConstants.AQL_DIR + "/ExternalDictionaryTests";

  /**
   * Main method for invoking one test at a time.
   * 
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    ExternalDictionaryTests test = new ExternalDictionaryTests();
    // test.setUp ();

    long startMS = System.currentTimeMillis();

    test.invalidDictFileFormatTest();

    long endMS = System.currentTimeMillis();

    // test.tearDown ();

    double elapsedSec = (endMS - startMS) / 1000.0;
    System.err.printf("Test took %1.3f sec.\n", elapsedSec);
  }

  /*
   * Parser and compiler test start
   */

  @SuppressWarnings("deprecation")
  /**
   * Test to verify parsing of correct 'create external dictionary ...' statements. <br>
   * <br>
   * Checks internal parameters of allow_empty and required are set correctly <br>
   * 1) when allow_empty is set without required being set <br>
   * 2) when required is set without allow_empty being set <br>
   * 3) when both flags are set (where at least one is true)
   * 
   * @throws Exception
   */
  @Test
  public void extDictFlagParseTest() throws Exception {
    // this file contains several external dictionaries plus an internal dictionary at the end
    String aqlFileName = String.format("%s/%s.aql", AQL_FILES_MODULE__DIR, "extDictFlagParseTest");
    AQLParser parser = new AQLParser(new File(aqlFileName));
    StatementList statementList = parser.parse();
    List<AQLParseTreeNode> parseTreeNodes = statementList.getParseTreeNodes();

    // expected flag settings -- see the AQL for details
    boolean[] allowempty = {true, false, true, true};
    boolean[] required = {false, true, true, false};

    // test if we have the expected number of nodes (plus 1 for the internal dictionary)
    int NUM_NODES = required.length + 1;
    Assert.assertEquals(NUM_NODES, parseTreeNodes.size());

    int i = 0;
    for (AQLParseTreeNode parseTreeNode : parseTreeNodes) {
      if (parseTreeNode instanceof CreateDictNode.Inline) {
        DictParams param = ((CreateDictNode.Inline) parseTreeNode).getParams();
        Assert.assertTrue(param.getIsExternal());

        if (param.isAllowEmpty() != null) {
          Assert.assertTrue(param.isAllowEmpty() == allowempty[i++]);
        }
        if (param.isRequired() != null) {
          Assert.assertTrue(param.isRequired() == required[i++]);
        }
      } else {

        // internal dict
        Assert.assertTrue(parseTreeNode instanceof CreateDictNode.FromFile);
        DictParams param = ((CreateDictNode.FromFile) parseTreeNode).getParams();

        Assert.assertFalse(param.getIsExternal());

        // assert that allow_empty/required flags are irrelevant for internal dictionaries
        try {
          Assert.assertFalse(param.isAllowEmpty());
        } catch (RuntimeException re) {
          Assert.assertEquals("Allow empty flag is applicable only for external dictionaries",
              re.getMessage());
        }
        try {
          Assert.assertFalse(param.isRequired());
        } catch (RuntimeException re) {
          Assert.assertEquals("Required flag is applicable only for external dictionaries",
              re.getMessage());
        }
      }
    }

  }

  /**
   * Test to verify, that parser throws relevant errors for 'create external dictionary ...'
   * statement, when run in backward compatibility mode. Note: Remove this test once we remove
   * support for non modular AQL.
   * 
   * @throws Exception
   */
  @Test
  public void extDictBackwardCompatibilityTest() throws Exception {
    String aqlFileName = String.format("%s/%s.aql", AQL_FILES_MODULE__DIR, "extDictFlagParseTest");
    AQLParser parser = new AQLParser(new File(aqlFileName));
    parser.setBackwardCompatibilityMode(true);

    LinkedList<ParseException> parseErrors = parser.parse().getParseErrors();
    // when in backward compatibility mode - parse will return error for 'create external dictionary
    // ...' statement
    Assert.assertEquals(4, parseErrors.size());

    // Assert error message and error location
    ParseException pe = parseErrors.get(0);
    // Assert error location
    Assert.assertTrue(pe.getFileName().contains("extDictFlagParseTest"));
    Assert.assertEquals(6, pe.getLine());
    // Assert error message
    Assert.assertEquals("create external dictionary statement only supported in v2.0+, ignoring...",
        pe.getErrorDescription());

    pe = parseErrors.get(1);
    // Assert error location
    Assert.assertTrue(pe.getFileName().contains("extDictFlagParseTest"));
    Assert.assertEquals(12, pe.getLine());
    // Assert error message
    Assert.assertEquals("create external dictionary statement only supported in v2.0+, ignoring...",
        pe.getErrorDescription());

  }

  /**
   * Test to verify common errors in 'create external dictionary' statement are caught. <br>
   * In particular, both flags being set, neither flag being set, and the flags being set out of
   * order.
   * 
   * @throws Exception
   */
  @Test
  public void invalidExtDictFlags() throws Exception {
    startTest();

    // Expect exception at these locations
    int[] lineNo = new int[] {8, 14, 21, 29, 36, 44, 50, 56, 64};
    int[] colNo = new int[] {13, 10, 1, 1, 1, 1, 1, 1, 1};

    compileModuleAndCheckErrors("invalidFlags", lineNo, colNo);

    endTest();

  }

  /*
   * Parser and compiler test end
   */

  /*
   * Loader tests start
   */

  /**
   * Test to verify that the loader throws an exception when a null ETI is passed and there are
   * required dictionaries in the module.
   * 
   * @throws Exception
   */
  @Test
  public void extDictNullETITest() throws Exception {
    startTest();
    try {
      // Passing null ETI object, when there is required dictionary 'requiredExtDict' in the module
      compileAndLoadModule("extDictNullETITest", null);
    } catch (Exception e) {
      Assert.assertEquals(
          "External artifacts passed to the extractor are empty. Provide entries for all required external dictionaries and tables. Required external dictionaries are: [extDictNullETITest.requiredExtDict]. Required external tables are: [].",
          e.getMessage());
      return;
    }

    // control should have not reached here
    Assert.fail();
    endTest();
  }

  /**
   * Test to verify that when a dictionary is declared with required = true, the ETI must contain
   * external dictionary information, but when a dictionary is declared with required = false, it
   * doesn't matter
   * 
   * @throws Exception
   */
  @Test
  public void etiMissingReqExtDictsTest() throws Exception {
    startTest();

    // eti object without the required dictionary - requiredExtDict
    ExternalTypeInfo externalTypeInfo = ExternalTypeInfoFactory.createInstance();
    ArrayList<String> entries = new ArrayList<String>();
    entries.add("firstEntry");
    entries.add("secondEntry");

    externalTypeInfo.addDictionary("etiMissingReqExtDictsTest.otherExtDict", entries);
    try {
      compileAndLoadModule("etiMissingReqExtDictsTest", externalTypeInfo);
    } catch (TextAnalyticsException e) {
      // error should state we are missing the required dictionary, but not the optional one
      final String expectedPattern =
          "[Required external dictionaries [etiMissingReqExtDictsTest.requiredExtDict] are missing in external type info object";
      if (false == e.getMessage().contains(expectedPattern)) {
        throw new Exception(String.format("Expected exception to contain the pattern:\n"
            + "    %s\n" + "but message was:\n" + "    %s", expectedPattern, e.getMessage()));
      }

      endTest();
      return;
    }
    // control should have not reached here
    Assert.fail();

    endTest();
  }

  /**
   * Test to verify the flags: <br>
   * allow_empty true, required true <br>
   * allow_empty false, required false <br>
   * <br>
   * The passed ETI will contain an empty dictionary. It should throw an exception if allow_empty is
   * false, but pass if allow_empty is true, or if the required flag is set.
   * 
   * @throws Exception
   */
  @Test
  public void etiWithEmptyReqExtDictsTest() throws Exception {
    startTest();

    // eti object with empty required dictionaries
    ExternalTypeInfo externalTypeInfo = ExternalTypeInfoFactory.createInstance();

    // this dictionary is declared allow_empty true
    externalTypeInfo.addDictionary("etiWithEmptyReqExtDictsTest.AllowEmptyTrue",
        new ArrayList<String>());

    // this dictionary is declared allow_empty false
    externalTypeInfo.addDictionary("etiWithEmptyReqExtDictsTest.AllowEmptyFalse",
        new ArrayList<String>());

    // this dictionary is declared required true
    externalTypeInfo.addDictionary("etiWithEmptyReqExtDictsTest.RequiredTrue",
        new ArrayList<String>());

    // this dictionary is declared required false
    externalTypeInfo.addDictionary("etiWithEmptyReqExtDictsTest.RequiredFalse",
        new ArrayList<String>());

    try {
      compileAndLoadModule("etiWithEmptyReqExtDictsTest", externalTypeInfo);
    } catch (TextAnalyticsException e) {
      // Should only throw error for the allow_empty false dictionary
      final String expectedPattern =
          "The following required non-empty external dictionaries are empty: [etiWithEmptyReqExtDictsTest.AllowEmptyFalse].";
      if (false == e.getMessage().contains(expectedPattern)) {
        throw new Exception(String.format("Expected exception to contain the pattern:\n"
            + "    %s\n" + "but message was:\n" + "    %s", expectedPattern, e.getMessage()));
      }

      endTest();
      return;
    }
    // control should have not reached here
    Assert.fail();

    endTest();
  }

  /**
   * Test to verify, that the loader throw an exception, when the passed ETI contain dictionaries,
   * which are either *not declared* or *not declared external* in the loaded module
   * 
   * @throws Exception
   */
  @Test
  public void etiWithUnknownExtDictTest() throws Exception {
    startTest();

    // eti object containing unknown dictionary - unknownExtDict
    ExternalTypeInfo externalTypeInfo = ExternalTypeInfoFactory.createInstance();
    ArrayList<String> entries = new ArrayList<String>();
    entries.add("firstEntry");
    entries.add("secondEntry");

    externalTypeInfo.addDictionary("etiWithUnknownExtDictTest.unknownExtDict", entries);

    try {
      compileAndLoadModule("etiWithUnknownExtDictTest", externalTypeInfo);
    } catch (Exception e) {
      // Unknown external dictionary - unknownExtDict
      Assert.assertEquals(
          "[External dictionaries [etiWithUnknownExtDictTest.unknownExtDict] specified in external type info object are not part of any of the loaded modules.]",
          e.getMessage());
      return;
    }
    // control should have not reached here
    Assert.fail();

    endTest();
  }

  /**
   * Test to verify that loader returns error with location information, while loading dictionary
   * file with invalid escape characters. Also covers the enhancement made in GHE#78: Null tokenizer
   * type in dictionary error to make the error message more informative by including the line
   * number and dict entry that cannot be parsed in the error message.
   * 
   * @throws Exception
   */
  @Test
  public void dictFileWithInvalidEscapeCharTest() throws Exception {
    startTest();

    ExternalTypeInfo eti = ExternalTypeInfoFactory.createInstance();
    String dictFileURI =
        new File(AQL_FILES_MODULE__DIR + "/dictFileWithInvalidEscapeChar.dict").toURI().toString();
    eti.addDictionary("dictFileWithInvalidEscapeCharTest.requiredExtDict", dictFileURI);

    try {
      compileAndLoadModule("dictFileWithInvalidEscapeCharTest", eti);
      Assert.fail("Control should have not reached here; an exception was expected.");
    } catch (Exception e) {
      final String expectedPattern =
          "An error occurred while loading external dictionary 'dictFileWithInvalidEscapeCharTest.requiredExtDict' from the file '.*/dictFileWithInvalidEscapeChar.dict':\\s+"
              + "An error occurred while parsing and de-escaping dictionary entry '.*' in line 3 of dictionary: 'dictFileWithInvalidEscapeCharTest.requiredExtDict'.*";

      if (false == Pattern.matches(expectedPattern, e.getMessage())) {
        e.printStackTrace();
        throw new Exception(String.format("Expected exception to match the pattern:\n" + "    %s\n"
            + "but message was:\n" + "    %s", expectedPattern, e.getMessage()));
      }
    }

    endTest();
  }

  /**
   * Test to verify, that the dictionaries from the passed ETI are loaded into the operator graph.
   * To assert dictionary loading this test perform extraction using the loaded dictionary.
   * 
   * @throws Exception
   */
  @Test
  public void loadExtDictTest() throws Exception {
    startTest();

    ExternalTypeInfo externalTypeInfo = ExternalTypeInfoFactory.createInstance();
    String dictFileURI = new File(AQL_FILES_MODULE__DIR + "/strictfirst.dict").toURI().toString();
    externalTypeInfo.addDictionary("loadExtDictTest.externalDict", dictFileURI);

    setPrintTups(true);
    compileAndRunModule("loadExtDictTest", new File(TestConstants.ENRON_100_DUMP),
        externalTypeInfo);

    truncateExpectedFiles();
    compareAgainstExpected(true);

    endTest();
  }

  /**
   * Test to verify, that the compiled dictionary are loaded into the operator graph. To assert
   * dictionary loading this test perform extraction using the loaded dictionary.
   * 
   * @throws Exception
   */
  @Test
  public void loadCompliedExtDictTest() throws Exception {
    startTest();

    ExternalTypeInfo externalTypeInfo = ExternalTypeInfoFactory.createInstance();
    String dictFileURI = new File(AQL_FILES_MODULE__DIR + "/loadCompliedExtDictTest/strictfirst.cd")
        .toURI().toString();
    externalTypeInfo.addDictionary("loadCompliedExtDictTest.externalDict", dictFileURI);

    setPrintTups(true);
    compileAndRunModule("loadCompliedExtDictTest", new File(TestConstants.ENRON_100_DUMP),
        externalTypeInfo);

    truncateExpectedFiles();
    compareAgainstExpected(true);

    endTest();
  }

  @Test
  public void loadExtDictWithMismatchedTokenizerTest() throws Exception {
    startTest();

    try {
      TokenizerConfig tokenizerCfg = TestConstants.STANDARD_TOKENIZER;
      String[] moduleNames = {"personPhone"};
      String modulePath = AQL_FILES_MODULE__DIR + "/loadExtDictWithMismatchedTokenizerTest/";
      ExternalTypeInfo eti = ExternalTypeInfoFactory.createInstance();
      String dictFileURI = new File(
          AQL_FILES_MODULE__DIR + "/loadExtDictWithMismatchedTokenizerTest/strictFirst.dict.cd")
              .toURI().toString();
      eti.addDictionary("personPhone.strictFirst", dictFileURI);

      // load module with module path and external dict in test format
      OperatorGraph.createOG(moduleNames, modulePath, eti, tokenizerCfg);

      Assert.fail("Exception expected, Operator Graph should not be created");
    }

    catch (Exception e) {
      Assert.assertTrue(
          e.getMessage().equals("Error in loading module(s): [person, personPhone, phone].\n"
              + "Detailed message: External dictionary dict/strictFirst.dict has tokenizer type BUILTIN, which is incompatible with tokenizer type STANDARD specified for module personPhone."));
    }
    endTest();
  }

  /**
   * Test to verify, that the internal dictionaries coming from external tables are loaded into the
   * operator graph. To assert this, we perform extraction using the internal dictionaries dependent
   * on external tables.
   * 
   * @throws Exception
   */
  @Test
  public void loadInternalDictFromExtTableTest() throws Exception {
    startTest();

    ExternalTypeInfo externalTypeInfo = ExternalTypeInfoFactory.createInstance();
    String tableFileURI =
        new File(AQL_FILES_MODULE__DIR + "/firstNameTable.csv").toURI().toString();
    externalTypeInfo.addTable("loadInternalDictFromExtTableTest.extTab1", tableFileURI);

    setPrintTups(true);
    compileAndRunModule("loadInternalDictFromExtTableTest", new File(TestConstants.ENRON_100_DUMP),
        externalTypeInfo);

    truncateExpectedFiles();
    compareAgainstExpected(true);

    endTest();
  }

  /**
   * Test to verify, that there are no regressions in running the module containing **no external
   * artifacts** .
   * 
   * @throws Exception
   */
  @Test
  public void runModuleWithNoExtArtifacts() throws Exception {
    startTest();

    setPrintTups(true);
    // Empty/Null ETI and module containing no external table/dictionary declaration
    compileAndRunModule("runModuleWithNoExtArtifacts",
        new File(TestConstants.TEST_DOCS_DIR + "/TAMTests/input.zip"), null);

    // truncateExpectedFiles ();
    compareAgainstExpected(true);

    endTest();
  }

  /**
   * Test to verify that the loader throws appropriate error, while loading entries for external
   * dictionaries from a file in invalid format.<br>
   * This test captures the scenario mentioned in defect#33809.<br>
   * Also covers the enhancement made in GHE#78: Null tokenizer type in dictionary error to make the
   * error message more informative by including the line number and dict entry that cannot be
   * parsed in the error message.
   * 
   * @throws Excepiton
   */
  @Test
  public void invalidDictFileFormatTest() throws Exception {
    startTest();

    ExternalTypeInfo eti = ExternalTypeInfoFactory.createInstance();
    String dictFileURI =
        new File(AQL_FILES_MODULE__DIR + "/invalidDictFormat.zip").toURI().toString();
    eti.addDictionary("invalidDictFileFormatTest.requiredExtDict", dictFileURI);

    try {
      compileAndLoadModule("invalidDictFileFormatTest", eti);
      Assert.fail("Control should have not reached here; an exception was expected");
    } catch (TextAnalyticsException e) {
      Assert.assertTrue(Pattern.matches(
          "An error occurred while loading external dictionary 'invalidDictFileFormatTest.requiredExtDict' from the file '.*/invalidDictFormat.zip':\\s+"
              + "An error occurred while parsing and de-escaping dictionary entry '     .*' in line 2 of dictionary: 'invalidDictFileFormatTest.requiredExtDict'.*",
          e.getMessage()));
    }

    endTest();
  }

  /**
   * Test case for defect GHE#78: Null tokenizer type in dictionary error.
   * 
   * @throws Exception
   */
  @Test
  public void nullTokenizerTypeTest() throws Exception {
    startTest();

    File pathToResources = getCurTestDir();

    ExternalTypeInfo eti = ExternalTypeInfoFactory.createInstance();
    String dict1URI = new File(pathToResources, "name_only.dict").toURI().toString();
    eti.addDictionary("module1.Name_Dict", dict1URI);
    String dict2URI = new File(pathToResources, "name_version.dict").toURI().toString();
    eti.addDictionary("module1.Name_Version_Dict", dict2URI);
    String dict3URI =
        new File(pathToResources, "name_version_tokenizertype.dict").toURI().toString();
    eti.addDictionary("module1.Name_Version_TokenizerType_Dict", dict3URI);

    setPrintTups(true);
    compileAndRunModule("module1", null, eti);

    truncateExpectedFiles();
    compareAgainstExpected(true);

    endTest();
  }

  /*
   * Loader tests end
   */
}
