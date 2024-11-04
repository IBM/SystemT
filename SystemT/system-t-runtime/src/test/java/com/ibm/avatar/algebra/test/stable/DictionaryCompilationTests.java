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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.ibm.avatar.algebra.util.dict.CompiledDictionary;
import com.ibm.avatar.algebra.util.dict.DictFile;
import com.ibm.avatar.algebra.util.dict.DictMemoization;
import com.ibm.avatar.algebra.util.dict.DictParams;
import com.ibm.avatar.algebra.util.dict.TextSerializer;
import com.ibm.avatar.algebra.util.lang.LangCode;
import com.ibm.avatar.algebra.util.string.Escaper;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.algebra.util.tokenize.StandardTokenizer;
import com.ibm.avatar.algebra.util.tokenize.Tokenizer;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.exceptions.ModuleLoadException;
import com.ibm.avatar.aql.tam.TAM;
import com.ibm.avatar.aql.tam.TAMSerializer;


/**
 * Test harness containing various dictionary compilation scenarios.
 * 
 */
public class DictionaryCompilationTests extends RuntimeTestHarness {
  private static final String DICT_DIR =
      TestConstants.TESTDATA_DIR + "/dictionaries" + "/DictionaryCompilationTests/";

  /** Document Collection file path */
  public File DOCS_FILE =
      new File(TestConstants.TEST_DOCS_DIR, "/DictionaryCompilationTests/input.zip");

  /** Directory containing compiled version of the modules */
  public static final String TAM_DIR =
      TestConstants.TESTDATA_DIR + "/tam/DictionaryCompilationTests/";

  @Before
  public void setUp() {

  }

  @After
  public void tearDown() {

  }

  /**
   * Compile dictionary with built-in whitespace tokenizer. (Simply converted testDictCompilation1()
   * test to modular aql test) Scenario: 1. Test compilation for inline dictionary with explicit
   * language codes 2. No "set default language" declaration in the module
   * 
   * @throws Exception
   */
  @Test
  public void testDictCompilationWithStandTok1() throws Exception {
    // code contained in testDictCompilation1() for reference
    // compileDictionary ("testDict", "testDictCompilation1", "en,fr", new StandardTokenizer ());
    // util.compareAgainstExpected ("testDictCompilation1_Paddy.greetings.cd", false);
    startTest();

    compileModules(new String[] {"testDictCompilationWithStandTok1"}, null);

    compareAgainstExpected(false);

    endTest();
  }

  /**
   * Compile empty dictionary file;we allow empty dictionaries: dictionary coming from file, and
   * external dictionary with allo_empty set to true (Simply converted testDictCompilation3() test
   * to modular aql test) Scenario: 1. Test compilation for inline dictionary without explicit lang
   * 2. No "set default language" declaration in the module 3. Test compilation for inline
   * dictionary created from extract dict statement
   * 
   * @throws Exception
   */
  @Test
  public void testDictCompilationEmptyDict1() throws Exception {
    // code contained in testDictCompilation3() for reference
    // compileDictionary ("testEmptyDict", "testDictCompilation3", "en", new StandardTokenizer ());
    // util.compareAgainstExpected ("testDictCompilation3.cd", false);

    startTest();

    compileModules(new String[] {"testDictCompilationEmptyDict1"}, null);

    compareAgainstExpected(false);

    endTest();

  }

  /**
   * Compile dictionary containing entries with whitespace and entry containing only whitespace
   * characters. (Simply converted testDictCompilation4() test to modular aql test) Scenario: 1.
   * Test compilation for inline dictionary without explicit lang 2. No "set default language"
   * declaration in the module 3. Test compilation for inline dictionary created from extract dict
   * statement
   * 
   * @throws Exception
   */
  @Test
  public void testDictCompilationWithSpaces() throws Exception {
    // code contained in testDictCompilation4() for reference
    // compileDictionary ("testDictContaningWhitepaces", "testDictCompilation4", "en", new
    // StandardTokenizer ());
    // util.compareAgainstExpected ("testDictCompilation4.cd", false);

    startTest();

    compileModules(new String[] {"testDictCompilationWithSpaces"}, null);

    compareAgainstExpected(false);

    endTest();

  }

  /**
   * Test that internal dictionary that depend on External tables are not pre-complied Scenario: 1.
   * Test compilation for inline dictionary created from external table 2. No "set default language"
   * declaration in the module
   * 
   * @throws Excetion
   */
  @Test
  public void testDictCompilationWithExtTable() throws Exception {
    startTest();

    TAM tam = null;

    compileModules(new String[] {"testDictCompilationWithExtTable"}, null);

    File modulePath = new File(getCurOutputDir().toURI());

    String moduleURIPath = modulePath.toURI().toString();

    tam = TAMSerializer.load("testDictCompilationWithExtTable", moduleURIPath);

    Map<String, CompiledDictionary> dicts = tam.getAllDicts();

    Assert.assertTrue(dicts.isEmpty());

    endTest();
  }

  /**
   * Test to assert that the default dictionary language set by 'set default dictionary language
   * ...' statement is used are applied to internal dictionary that depend on External tables in
   * metadata.xml of the compiled module Scenario: 1. Test compilation for inline dictionary created
   * from external table 2. With "set default language" declaration in the module
   * 
   * @throws Excetion
   */
  @Test
  public void testDictCompilationWithExtTable2() throws Exception {
    startTest();

    TAM tam = null;

    File modulePath = new File(getCurOutputDir().toURI());

    String moduleURIPath = modulePath.toURI().toString();

    compileModules(new String[] {"testDictCompilationWithExtTable2"}, null);

    tam = TAMSerializer.load("testDictCompilationWithExtTable2", moduleURIPath);

    Map<String, CompiledDictionary> dicts = tam.getAllDicts();

    // Assert that there are not precompiled dictionary
    Assert.assertTrue(dicts.isEmpty());

    // Check that the compiled module against expected
    compareAgainstExpected(false);

    endTest();
  }

  /**
   * Test to verify that a precompiled TAM containing a compiled dictionary with a version number
   * from the future will throw an incompatible dictionary exception
   * 
   * @throws Exception
   */
  @Test
  public void testIncompatibleCD() throws Exception {
    startTest();

    setTokenizerConfig(TestConstants.STANDARD_TOKENIZER);

    String moduleURIPath = new File(TAM_DIR).toURI().toString();

    try {
      runModule(DOCS_FILE, "testIncompatibleCD", moduleURIPath);

      Assert.fail("Expected a ModuleLoadException.");
    } catch (ModuleLoadException e) {
      System.out.println("Test passed.");
    }

    endTest();

  }

  /**
   * Test to check that no compiled dictionaries are created for external dictionaries
   * 
   * module
   */
  @Test
  public void testDictCompilationForExtDict() throws Exception {
    startTest();

    setTokenizerConfig(TestConstants.STANDARD_TOKENIZER);

    compileModules(new String[] {"testDictCompilationForExtDict"}, null);

    File modulePath = new File(getCurOutputDir().toURI());

    String moduleURIPath = modulePath.toURI().toString();

    TAM tam = TAMSerializer.load("testDictCompilationForExtDict", moduleURIPath);

    Map<String, CompiledDictionary> dicts = tam.getAllDicts();

    Assert.assertTrue(dicts.isEmpty());
  }

  /**
   * Test to assert that compiling dictionary with one locale and running the extraction on input
   * document with different locale yields no results
   * 
   * @throws Exception
   */
  @Test
  public void testExtractionWithDifferentLanguage() throws Exception {
    startTest();

    compileModules(new String[] {"testExtractionWithDifferentLanguage"}, null);

    setLanguage(LangCode.zh);

    runModule(DOCS_FILE, "testExtractionWithDifferentLanguage",
        getCurOutputDir().toURI().toString());

    compareAgainstExpected(false);

    endTest();
  }

  /**
   * Compile dictionary with whitespace with built-in whitespace tokenizer and run Scenario: 1. Dict
   * entries with whitespace 2. Whitespace Tokenizer
   * 
   * @throws Exception
   */
  @Test
  public void testExtractionWithWhitespaceTok() throws Exception {

    startTest();

    compileModules(new String[] {"testExtractionWithWhitespaceTok"}, null);

    runModule(DOCS_FILE, "testExtractionWithWhitespaceTok", getCurOutputDir().toURI().toString());

    compareAgainstExpected(false);

    endTest();
  }

  /**
   * Method to compile the given dictionary file; based on the given tokenizer and language string.
   * 
   * @param dictName name of the dictionary file to compile
   * @param compiledDictFilename name of the file where compiled dictionary should be serialized
   * @param langString language against which match is to be performed
   * @param tokenizer tokenizer to be used for compilation
   * @throws Exception
   */
  private CompiledDictionary compileDictionary(String dictFileName, String compiledDictFilename,
      String langString, Tokenizer tokenizer) throws Exception {
    File dictionaryFile = new File(DICT_DIR + dictFileName + ".dict");

    // Modify dictionary matching parameter
    DictParams params = new DictParams();
    params.setDictName(dictionaryFile.getName());
    params.setLangStr(langString);

    DictFile testDict = new DictFile(dictionaryFile, params);

    // Compile dictionary
    CompiledDictionary compiledDictionary = testDict.compile(tokenizer, new DictMemoization());

    FileOutputStream compiledDictionaryStream = new FileOutputStream(
        getCurOutputDir() + "/" + compiledDictFilename + Constants.COMPILED_DICT_FILE_EXT);

    new TextSerializer().serialize(compiledDictionary, compiledDictionaryStream);

    compiledDictionaryStream.close();

    return compiledDictionary;
  }

  /**
   * Testcase to assert decoding of compiled dictionary.
   * 
   * @throws Exception
   */
  @Test
  public void testDecompilation1() throws Exception {
    startTest();

    CompiledDictionary compileDictionary =
        compileDictionary("testDict", "testDecompilation1", "en,fr", new StandardTokenizer());
    serialize(compileDictionary.getHumanReadableTokenizedEntries(), "testDecompilation1");
    compareAgainstExpected(false);

    endTest();
  }

  /**
   * Testcase to assert de-serialization and decoding of dictionary.
   * 
   * @throws Exception
   */
  @Test
  public void testDecompilation2() throws Exception {
    startTest();

    CompiledDictionary compileDictionary = new TextSerializer().deSerialize(
        new FileInputStream(DICT_DIR + "testDecompilation2" + Constants.COMPILED_DICT_FILE_EXT));
    serialize(compileDictionary.getHumanReadableTokenizedEntries(), "testDecompilation2");
    compareAgainstExpected("testDecompilation2", false);

    endTest();
  }

  /**
   * Testcase to de-serialize and decode empty dictionary; we allow empty dictionaries: dictionary
   * coming from file, and external dictionary with allo_empty set to true
   * 
   * @throws Exception
   */
  @Test
  public void testDecompilation3() throws Exception {
    startTest();

    // null language String
    CompiledDictionary compileDictionary = new TextSerializer().deSerialize(
        new FileInputStream(DICT_DIR + "testDecompilation3" + Constants.COMPILED_DICT_FILE_EXT));
    serialize(compileDictionary.getHumanReadableTokenizedEntries(), "testDecompilation3");
    compareAgainstExpected("testDecompilation3", false);

    endTest();
  }

  /**
   * Test to verify that plan generated for dictionaries coming from file contains the qualified
   * dictionary name in both CreateDict and extract Dictionary AOG production.
   * 
   * @throws Exception
   */
  @Test
  public void verifyDictPlanTest() throws Exception {
    startTest();

    setPrintTups(true);

    compileAndRunModule("verifyDictPlanTest", null, null);

    // Compare actual plan.aog(from tam) with expected plan.aog
    compareAgainstExpected(false);

    endTest();
  }

  /**
   * Test case for defect : Making the list of languages too long, shows a completely uninformative
   * error message when compiling any dictionary statement specifying language codes. <br/>
   * CASE: SET DEFAULT DICTIONARY STATEMENT <br/>
   * Verifies that an informative parse exception providing resolution details is thrown.
   * 
   * @throws Exception
   */
  @Test
  public void tooManyLangCodesInSetDefaultDictLangTest() throws Exception {
    startTest();
    int expectedLineNos[] = {3};
    int expectedColNos[] = {1};

    compileModuleAndCheckErrors("tooManyLangCodesInSetDefaultDictLang", expectedLineNos,
        expectedColNos);

    endTest();
  }

  /**
   * Test case for defect : Making the list of languages too long, shows a completely uninformative
   * error message when compiling any dictionary statement specifying language codes. <br/>
   * CASE: CREATE DICTIONARY STATEMENT <br/>
   * Verifies that an informative parse exception providing resolution details is thrown.
   * 
   * @throws Exception
   */
  @Test
  public void tooManyLangCodesInCreateDictTest() throws Exception {
    startTest();
    int expectedLineNos[] = {3};
    int expectedColNos[] = {1};

    compileModuleAndCheckErrors("tooManyLangCodesInCreateDict", expectedLineNos, expectedColNos);

    endTest();
  }

  /**
   * Test case for defect <br/>
   * Test case to verify that if an empty string provided as language code string in the "set
   * default dictionary language.." statement, then an informative parse exception providing
   * resolution details is thrown.
   * 
   * @throws Exception
   */
  @Test
  public void emptyLangCodesInSetDefaultDictLangTest() throws Exception {
    startTest();
    int expectedLineNos[] = {3};
    int expectedColNos[] = {1};

    compileModuleAndCheckErrors("emptyLangCodesInSetDefaultDictLang", expectedLineNos,
        expectedColNos);

    endTest();
  }

  /**
   * Test case for defect <br/>
   * Test case to verify that if an empty string provided as language code string in the "create
   * dictionary.." statement, then an an informative parse exception providing resolution details is
   * thrown.
   * 
   * @throws Exception
   */
  @Test
  public void emptyLangCodesInCreateDictTest() throws Exception {
    startTest();
    int expectedLineNos[] = {3};
    int expectedColNos[] = {1};

    compileModuleAndCheckErrors("emptyLangCodesInCreateDict", expectedLineNos, expectedColNos);

    endTest();
  }

  /**
   * Stress-test the de-escaper in Escaper.java <br/>
   * Validates defect : External dictionary entries with escape chars are not loading chars after
   * the escape char
   */
  @Test
  public void verifyDictDeescapeTest() throws Exception {
    startTest();

    // set up the escaper utility class exactly as it's set in the code
    Escaper escaper = new Escaper(new char[] {'#'}, new char[] {'#'});
    escaper.setRemoveComments(true);

    // test strings and their expected outputs after being run through the De-escaper
    String[] inputs =
        {"This is a normal string.", "#This is a comment.", "We're #1!", "We're \\#1!",
            "This tests the escape character '\\\\'.", "How about \\## multiple comments?"};
    String[] expectedOutput = {"This is a normal string.", "", "We're ", "We're #1!",
        "This tests the escape character '\\'.", "How about #"};

    for (int i = 0; i < inputs.length; i++) {
      String output = escaper.deEscapeStr(inputs[i]);
      if (expectedOutput[i].equals(output)) {
        // output is expected, continue
      } else {
        // output is not expected, throw an exception
        Assert.fail(String.format("De-escaper with input '%s' produced '%s' (expected output '%s')",
            inputs[i], output, expectedOutput[i]));
      }
    }
  }

  /**
   * Test case for defect . Test verifies that when a dictionary is created from an optional
   * external table, the runtime does not throw any exceptions.
   * 
   * @throws Exception
   */
  @Test
  public void dictFromOptionalEmptyExtTableTest() throws Exception {
    startTest();

    // Expect no exceptions
    compileAndRunModule("module1", DOCS_FILE, null);

    endTest();

  }

  /**
   * defect - Test the behavior of case matching for dictionary when in stage 1, the create
   * dictionary statement does not specify any explicit flag. The stage 2 operator is extract
   * dictionary. The test covers all 3 cases for extract dictionary: no flag, IgnoreCase, Exact.
   * 
   * @throws Exception
   */
  @Test
  public void createDictNoFlagExtractDictTest() throws Exception {

    startTest();

    DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR, "/DictionaryCompilationTests/dictTest.txt");

    compileAndRunModule("createDictNoFlagExtractDictTest", DOCS_FILE, null);
    compareAgainstExpected(false);

    endTest();
  }

  /**
   * defect - Test the behavior of case matching for dictionary when in stage 1, the create
   * dictionary statement does not specify any explicit flag. The stage 2 operator is MatchesDict.
   * The test covers all 3 cases for MatchesDict: no flag, IgnoreCase, Exact.
   * 
   * @throws Exception
   */
  @Test
  public void createDictNoFlagMatchesDictTest() throws Exception {

    startTest();

    DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR, "/DictionaryCompilationTests/dictTest.txt");

    compileAndRunModule("createDictNoFlagMatchesDictTest", DOCS_FILE, null);
    compareAgainstExpected(false);

    endTest();
  }

  /**
   * defect - Test the behavior of case matching for dictionary when in stage 1, the create
   * dictionary statement does not specify any explicit flag. The stage 2 operator is ContainsDict.
   * The test covers all 3 cases for ContainsDict: no flag, IgnoreCase, Exact.
   * 
   * @throws Exception
   */
  @Test
  public void createDictNoFlagContainsDictTest() throws Exception {

    startTest();

    DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR, "/DictionaryCompilationTests/dictTest.txt");

    compileAndRunModule("createDictNoFlagContainsDictTest", DOCS_FILE, null);
    compareAgainstExpected(false);

    endTest();
  }

  /**
   * defect - Test the behavior of case matching for dictionary when in stage 1, the create
   * dictionary statement specifies "exact" case flag. The stage 2 operator is extract dictionary.
   * The test covers all 3 cases for extract dictionary: no flag, IgnoreCase, Exact.
   * 
   * @throws Exception
   */
  @Test
  public void createDictExactFlagExtractDictTest() throws Exception {

    startTest();

    DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR, "/DictionaryCompilationTests/dictTest.txt");

    compileAndRunModule("createDictExactFlagExtractDictTest", DOCS_FILE, null);
    compareAgainstExpected(false);

    endTest();
  }

  /**
   * defect - Test the behavior of case matching for dictionary when in stage 1, the create
   * dictionary statement specifies "exact" case flag. The stage 2 operator is MatchesDict. The test
   * covers all 3 cases for MatchesDict: no flag, IgnoreCase, Exact.
   * 
   * @throws Exception
   */
  @Test
  public void createDictExactFlagMatchesDictTest() throws Exception {

    startTest();

    DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR, "/DictionaryCompilationTests/dictTest.txt");

    compileAndRunModule("createDictExactFlagMatchesDictTest", DOCS_FILE, null);
    compareAgainstExpected(false);

    endTest();
  }

  /**
   * defect - Test the behavior of case matching for dictionary when in stage 1, the create
   * dictionary statement specifies "exact" case flag. The stage 2 operator is ContainsDict. The
   * test covers all 3 cases for ContainsDict: no flag, IgnoreCase, Exact.
   * 
   * @throws Exception
   */
  @Test
  public void createDictExactFlagContainsDictTest() throws Exception {

    startTest();

    DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR, "/DictionaryCompilationTests/dictTest.txt");

    compileAndRunModule("createDictExactFlagContainsDictTest", DOCS_FILE, null);
    compareAgainstExpected(false);

    endTest();
  }

  /**
   * defect - Test the behavior of case matching for dictionary when in stage 1, the create
   * dictionary statement specifies "insensitive" case flag. The stage 2 operator is extract
   * dictionary. The test covers all 3 cases for extract dictionary: no flag, IgnoreCase, Exact.
   * 
   * @throws Exception
   */
  @Test
  public void createDictIgnoreFlagExtractDictTest() throws Exception {

    startTest();

    DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR, "/DictionaryCompilationTests/dictTest.txt");

    compileAndRunModule("createDictIgnoreFlagExtractDictTest", DOCS_FILE, null);
    compareAgainstExpected(false);

    endTest();
  }

  /**
   * defect - Test the behavior of case matching for dictionary when in stage 1, the create
   * dictionary statement specifies "insensitive" case flag. The stage 2 operator is MatchesDict.
   * The test covers all 3 cases for MatchesDict: no flag, IgnoreCase, Exact.
   * 
   * @throws Exception
   */
  @Test
  public void createDictIgnoreFlagMatchesDictTest() throws Exception {

    startTest();

    DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR, "/DictionaryCompilationTests/dictTest.txt");

    compileAndRunModule("createDictIgnoreFlagMatchesDictTest", DOCS_FILE, null);
    compareAgainstExpected(false);

    endTest();
  }

  /**
   * defect - Test the behavior of case matching for dictionary when in stage 1, the create
   * dictionary statement specifies "insensitive" case flag. The stage 2 operator is ContainsDict.
   * The test covers all 3 cases for ContainsDict: no flag, IgnoreCase, Exact.
   * 
   * @throws Exception
   */
  @Test
  public void createDictIgnoreFlagContainsDictTest() throws Exception {

    startTest();

    DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR, "/DictionaryCompilationTests/dictTest.txt");

    compileAndRunModule("createDictIgnoreFlagContainsDictTest", DOCS_FILE, null);
    compareAgainstExpected(false);

    endTest();
  }


  /**
   * Method to serialize the human readable form of tokenized dictionary in a file.
   * 
   * @param tokenizedEntryList human readable form of tokenized dictionary
   * @param outputFileName
   * @throws Exception
   */
  private void serialize(Map<String, String> tokenizedEntryList, String outputFileName)
      throws Exception {
    BufferedWriter decompiledDictionaryWriter =
        new BufferedWriter(new FileWriter(new File(getCurOutputDir(), outputFileName)));

    Set<String> keySet = tokenizedEntryList.keySet();
    for (String tokenizedEntryString : keySet) {
      decompiledDictionaryWriter.write(tokenizedEntryString);
      // Separator for entry tokens and languages in which they get tokenized
      decompiledDictionaryWriter.write(" <---> ");
      decompiledDictionaryWriter.write(tokenizedEntryList.get(tokenizedEntryString).toString());
      decompiledDictionaryWriter.newLine();
    }
    decompiledDictionaryWriter.close();
  }

}
