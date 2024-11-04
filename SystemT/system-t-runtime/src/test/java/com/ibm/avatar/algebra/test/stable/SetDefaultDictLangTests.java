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
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.aql.AQLParseTreeNode;
import com.ibm.avatar.aql.AQLParser;
import com.ibm.avatar.aql.ParseException;
import com.ibm.avatar.aql.SetDefaultDictLangNode;

/**
 * Test harness containing regression tests related to 'set default dictionary language ...'
 * statement.
 * 
 */
public class SetDefaultDictLangTests extends RuntimeTestHarness {
  /** Location of modular and non modular AQL files used for this test */
  private static final String AQL_FILES_MODULE__DIR =
      TestConstants.AQL_DIR + "/SetDefaultDictLangTests";

  /** Document Collection file path */
  public static final File DOCS_FILE =
      new File(TestConstants.TEST_DOCS_DIR, "/SetDefaultDictLangTests/input.zip");

  /** Directory containing compiled version of the modules */
  public static final String TAM_DIR = TestConstants.TESTDATA_DIR + "/tam/SetDefaultDictLangTests/";

  /**
   * Main method for invoking one test at a time.
   * 
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    SetDefaultDictLangTests t = new SetDefaultDictLangTests();
    t.setUp();

    long startMS = System.currentTimeMillis();

    t.defaultDictLangApplied1();

    long endMS = System.currentTimeMillis();

    t.tearDown();

    double elapsedSec = ((double) (endMS - startMS)) / 1000.0;
    System.err.printf("Test took %1.3f sec.\n", elapsedSec);
  }

  @Before
  public void setUp() {}

  @After
  public void tearDown() {}

  /**
   * Test to assert that the parser returns parse tree node for 'set default dictionary language
   * ...' statement.
   * 
   * @throws Exception
   */
  @Test
  public void setDefaultDictLangParseTest() throws Exception {
    String aqlFileName =
        String.format("%s/%s.aql", AQL_FILES_MODULE__DIR, "setDefaultDictLangParseTest");
    AQLParser parser = new AQLParser(new File(aqlFileName));
    List<AQLParseTreeNode> parseTreeNodes = parser.parse().getParseTreeNodes();
    // We expect two parse tree node one for 'set default dictionary language ...' statement, and
    // the other one for
    // 'create
    // dictionary ...' statement.
    Assert.assertEquals(2, parseTreeNodes.size());

    // Assert the content of 'set default dictionary langauge ...' parse tree node
    for (AQLParseTreeNode node : parseTreeNodes) {
      if (node instanceof SetDefaultDictLangNode) {
        SetDefaultDictLangNode nodeToTest = (SetDefaultDictLangNode) node;

        // Assert line number
        Assert.assertEquals(nodeToTest.getOrigTok().beginLine, 2);
        // Assert containing file name
        Assert
            .assertTrue(nodeToTest.getContainingFileName().contains("setDefaultDictLangParseTest"));
        // Asset languge code string
        Assert.assertEquals(nodeToTest.getDefaultLangString(), "en,fr");
      }
    }
  }

  /**
   * Test case to verify that the parser throws an error for 'set default dictionary ...' statement;
   * when in backward compatibility mode.
   * 
   * @throws Exception
   */
  @Test
  public void backwardCompatibilityModeTest() throws Exception {
    String aqlFileName =
        String.format("%s/%s.aql", AQL_FILES_MODULE__DIR, "backwardCompatibilityModeTest");
    AQLParser parser = new AQLParser(new File(aqlFileName));
    // change parser mode to backward compatibility
    parser.setBackwardCompatibilityMode(true);

    List<ParseException> parseErrors = parser.parse().getParseErrors();

    // there should be a parse error for 'set default dictionary ...' statement
    ParseException parseException = parseErrors.get(0);
    Assert.assertNotNull(parseException);

    // Assert error message
    Assert.assertEquals(parseException.getErrorDescription(),
        "set default dictionary language only supported in v2.0+, ignoring...");

    // Assert error location
    Assert.assertEquals(parseException.getLine(), 3);
    Assert.assertTrue(parseException.getFileName().contains("backwardCompatibilityModeTest"));
  }

  /**
   * Test to assert that the compiler return error for empty language code string.
   * 
   * @throws Exception
   */
  @Test
  public void emptyLangCodeStringTest() throws Exception {
    startTest();

    int[] lineNo = new int[] {3};
    int[] colNo = new int[] {1};
    compileAQLTest(lineNo, colNo);

    endTest();
  }

  /**
   * Test to assert that the compiler returns error for language code string containing
   * invalid/unsupported languages.
   * 
   * @throws Exception
   */
  @Test
  public void invalidLangCodeStringTest() throws Exception {
    startTest();

    int[] lineNo = new int[] {2};
    int[] colNo = new int[] {1};
    compileAQLTest(lineNo, colNo);

    endTest();
  }

  /**
   * Test to assert that compiler return errors for multiple instances of 'set default dictionary
   * language ...' statement in a module. Scenario: 1. Having more than one "Set default language"
   * declaration in a single aql file in a module
   * 
   * @throws Exception
   */
  @Test
  public void multipleSetDefaultDictLangTest() throws Exception {
    startTest();

    int[] lineNo = new int[] {15};
    int[] colNo = new int[] {1};
    compileModuleAndCheckErrors("multipleSetDefaultDictLangTest", lineNo, colNo);

    endTest();
  }

  /**
   * Test to assert that compiler return errors for multiple instances of 'set default dictionary
   * language ...' statement in a module but in 2 aql files. Scenario: 1. Having more than one "Set
   * default language" declaration in a single module
   * 
   * @throws Exception
   */
  @Test
  public void multipleSetDefaultDictLangTest2() throws Exception {
    startTest();

    int[] lineNo = new int[] {4};
    int[] colNo = new int[] {1};
    compileModuleAndCheckErrors("multipleSetDefaultDictLangTest2", lineNo, colNo);

    endTest();
  }

  /**
   * Test to assert, if the default dictionary language set by 'set default dictionary language ...'
   * statement is used for implicitly declared dictionaries and dictionaries declares without
   * explicit language clause. Scenario: 1. Test compilation for inline dictionary without explicit
   * lang 2. With 'set default dictionary language ...' statement in the module 3. Test compilation
   * for inline dictionary created from extract dict statement
   * 
   * @throws Exception
   */
  @Test
  public void defaultDictLangApplied1() throws Exception {
    startTest();

    // compile and dump the tam
    compileModule("defaultDictLangApplied1");
    // all the serialized dictionary nodes should have language attribute set to: English and French
    compareAgainstExpected(false);

    endTest();
  }

  /**
   * Test to assert, if the system default dictionary language set is used for implicitly declared
   * dictionaries and dictionaries declares without explicit language clause. Scenario: 1. Test
   * compilation for inline dictionary created from Table 2. No "set default language" declaration
   * in the module
   * 
   * @throws Exception
   */
  @Test
  public void defaultDictLangApplied2() throws Exception {
    startTest();

    // compile and dump the tam
    compileModule("defaultDictLangApplied2");
    // all the serialized dictionary nodes should have language attribute set to: system default
    compareAgainstExpected(false);

    endTest();
  }

  /**
   * Test to assert, if the default dictionary language set by 'set default dictionary language ...'
   * statement is NOT used for dictionaries declares with explicit language clause. Scenario: 1.
   * Test compilation for inline dictionary with explicit lang 2. No "set default language"
   * declaration in the module
   * 
   * @throws Exception
   */
  @Test
  public void defaultDictLangNotApplied1() throws Exception {
    startTest();

    // compile and dump the tam
    compileModule("defaultDictLangNotApplied1");
    // for dictionaries(testDictInline and testDictFromTable) declared with language clause;
    // language set should be set
    // to English, and not to default set English and French
    compareAgainstExpected(false);

    endTest();
  }

  /**
   * Test to verify that default dictionary matching languages set provided by 'set default
   * dictionary ...' statement is applied to dictionary declared through 'create external dictionary
   * ...' statement. Scenario: 1. Test compilation for external dict 2. With 'set default dictionary
   * ...' statement in module
   * 
   * @throws Exception
   */
  @Test
  public void extDictDefaultDictTest() throws Exception {
    startTest();

    // compile and dump the tam
    compileModule("extDictDefaultDictTest");

    compareAgainstExpected(false);

    endTest();
  }

  /**
   * Test to assert, if the system default dictionary language set is NOT used for dictionaries
   * declares with explicit language clause.
   * 
   * @throws Exception
   */
  @Test
  public void defaultDictLangNotApplied2() throws Exception {
    startTest();

    // compile and dump the tam
    compileModule("defaultDictLangNotApplied2");
    // for dictionaries(testDictInline and testDictFromTable) declared with language clause;
    // language set should be set
    // to English, and not to system default.
    compareAgainstExpected(false);

    endTest();
  }

  /**
   * Test to run a Windows pre-compiled module with Set Default Dictionary statement and a compiled
   * dictionary on RHEL platform Scenario: 1. Test to export a inline dictionary with default lang
   * and consume it in Windows/Linux BI project using the TAM 2. With "set default language"
   * declaration in the module
   * 
   * @throws Exception
   */
  @Test
  public void testRunWindowsPreCompiledTam() throws Exception {
    startTest();

    setTokenizerConfig(TestConstants.STANDARD_TOKENIZER);

    String moduleURIPath = new File(TAM_DIR).toURI().toString();

    runModule(DOCS_FILE, "testRunWindowsPreCompiledTam", moduleURIPath);

    compareAgainstExpected(false);

    endTest();

  }

}
