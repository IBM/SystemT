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
package com.ibm.avatar.algebra.test.experimental;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.ibm.avatar.algebra.function.scalar.AutoID;
import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.algebra.util.string.StringUtils;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.algebra.util.tokenize.TokenizerConfig;
import com.ibm.avatar.api.CompileAQL;
import com.ibm.avatar.api.CompileAQLParams;
import com.ibm.avatar.api.ExternalTypeInfo;
import com.ibm.avatar.api.ExternalTypeInfoFactory;
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.api.exceptions.CompilerException;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.logging.Log;
import com.ibm.avatar.provenance.AQLProvenanceRewriter;

/**
 * Various tests for the provenance rewrite when running on modular AQL code.
 * 
 */
public class ProvenanceRewriteModularTests extends RuntimeTestHarness {

  /**
   * A collection of 1000 Enron emails shared by tests in this class.
   */
  public static File DOCS_FILE = new File(TestConstants.DUMPS_DIR, "ensmall.zip");

  /**
   * Tokenizer config shared by tests in this class.
   */

  /**
   * Name of the directory in the test output directory where the compiled version of the rewritten
   * AQL goes
   */
  private static final String COMPILED_MODULE_DIR = "compiledModules";

  /** Name of the directory in the test output directory where the rewritten source AQL goes */
  private static final String REWRITTEN_SRC_MODULE_DIR = "rewrittenSrcModules";

  /** Name of the directory in the test output directory where the rewritten compiled modules go. */
  private static final String REWRITTEN_COMPILED_MODULE_DIR = "rewrittenCompiledModules";

  /**
   * Main method for invoking one test at a time.
   * 
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    ProvenanceRewriteModularTests t = new ProvenanceRewriteModularTests();
    t.setUp();

    long startMS = System.currentTimeMillis();

    t.modularNerTest();

    long endMS = System.currentTimeMillis();

    t.tearDown();

    double elapsedSec = (endMS - startMS) / 1000.0;
    System.err.printf("Test took %1.3f sec.\n", elapsedSec);
  }

  @Before
  public void setUp() {

  }

  @After
  public void tearDown() {

  }

  @Override
  protected void startHook() {
    super.startHook();
    setPrintTups(true);
    // Reset the AutoID generator for each test to ensure consistent results when running tests
    // inside the regression
    // suite.
    AutoID.resetIDCounter();

    // Set up the document collection used by the test. The test itself can override it.
    DOCS_FILE = new File(TestConstants.ENRON_SMALL_ZIP);
  }

  /**
   * Test case for rewriting a single module, with no dependencies and no new constructs (require
   * document columns, external tables/dictionaries).
   */
  @Test
  public void singleModuleTest() throws Exception {
    startTest();

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"module1"};
    String[] dependencies = new String[] {};

    genericProvenanceTest(toRewrite, dependencies);

    // Compare the annotated output
    specialCompareAgainstExpected(toRewrite);

    endTest();
  }

  /**
   * Test with subquery - make sure that extra rewritten views (for subqueries, or union operands)
   * get written out.
   */
  @Test
  public void singleModuleSubqueryTest() throws Exception {
    startTest();

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"module1"};
    String[] dependencies = new String[] {};

    genericProvenanceTest(toRewrite, dependencies);

    // Compare the annotated output
    specialCompareAgainstExpected(toRewrite);

    // Compare the TAM of the rewritten AQL
    endTest();
  }

  /**
   * Test with some non-required views - make sure we don't rewrite then, not write out the original
   * version in the rewritten module (otherwise we'd run into schema incompatibility issues).
   */
  @Test
  public void singleModuleReqViewsTest() throws Exception {
    startTest();

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"module1"};
    String[] dependencies = new String[] {};

    genericProvenanceTest(toRewrite, dependencies);

    // Compare the annotated output
    specialCompareAgainstExpected(toRewrite);

    endTest();
  }

  /**
   * Test case for rewriting a single module with an internal dictionary from a file, with no other
   * module dependencies and no new constructs (require document columns, external
   * tables/dictionaries).
   */
  @Test
  public void singleModuleWithInternalDictTest() throws Exception {
    startTest();

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"module1"};
    String[] dependencies = new String[] {};

    genericProvenanceTest(toRewrite, dependencies);

    // Compare the annotated output
    specialCompareAgainstExpected(toRewrite);

    endTest();
  }

  /**
   * Test case for rewriting a single module with an external dictionary.
   */
  @Test
  public void singleModuleWithExternalDictTest() throws Exception {
    startTest();

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"module1"};
    String[] dependencies = new String[] {};

    // Prepare eti object for declared external dictionary
    ExternalTypeInfo eti = ExternalTypeInfoFactory.createInstance();
    eti.addDictionary("module1.externalDict",
        new File(getCurTestDir(), "module1/dicts/firstNames.dict").toURI().toString());
    genericProvenanceTest(toRewrite, dependencies, eti);

    // Compare the annotated output
    specialCompareAgainstExpected(toRewrite);

    endTest();
  }

  /**
   * Test case for rewriting a single module with an external table.
   */
  @Test
  public void singleModuleWithExternalTableTest() throws Exception {
    startTest();

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"module1"};
    String[] dependencies = new String[] {};

    // Prepare eti object for declared external table
    ExternalTypeInfo eti = ExternalTypeInfoFactory.createInstance();
    eti.addTable("module1.externalTable",
        new File(getCurTestDir(), "module1/tables/externalTable.csv").toURI().toString());
    genericProvenanceTest(toRewrite, dependencies, eti);

    // Compare the annotated output
    specialCompareAgainstExpected(toRewrite);

    endTest();
  }

  /**
   * Test case for rewriting two modules, where one depends on the other and there are no other
   * dependencies.
   */
  @Test
  public void twoModulesTest() throws Exception {
    startTest();

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"module1", "module2"};
    String[] dependencies = new String[] {};

    genericProvenanceTest(toRewrite, dependencies);

    // Compare the output
    specialCompareAgainstExpected(toRewrite);

    endTest();
  }

  /**
   * Test case for rewriting two modules, where one depends on the other and there are no other
   * dependencies; just like {@link #twoModuletest()} but with a different form of import view
   * statement.
   */
  @Test
  public void twoModulesImportViewNoAliasTest() throws Exception {
    startTest();

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"module1", "module2"};
    String[] dependencies = new String[] {};

    genericProvenanceTest(toRewrite, dependencies);

    // Compare the output
    specialCompareAgainstExpected(toRewrite);

    endTest();
  }

  /**
   * Test case for rewriting two modules, where one depends on the other and there are no other
   * dependencies; just like {@link #twoModuletest()} and {@link #twoModulesImportViewNoAliasTest()}
   * but with an import module statement, instead of import view.
   */
  @Test
  public void twoModulesImportModuleTest() throws Exception {
    startTest();

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"module1", "module2"};
    String[] dependencies = new String[] {};

    genericProvenanceTest(toRewrite, dependencies);

    // Compare the output
    specialCompareAgainstExpected(toRewrite);

    endTest();
  }

  /**
   * Test case for rewriting two modules, where one depends on the other and there are no other
   * dependencies. Just like {@link #twoModulesTest()} but where one of the modules depends on a
   * compiled module (that is not rewritten).
   */
  @Test
  public void twoModulesOneCompiledTest() throws Exception {
    startTest();

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"module2"};
    String[] dependencies = new String[] {"module1"};

    genericProvenanceTest(toRewrite, dependencies);

    // Compare the output
    specialCompareAgainstExpected(toRewrite);

    endTest();
  }

  /**
   * Test case for rewriting two modules, where one depends on the other and there are no other
   * dependencies. Just like {@link #twoModulesOneCompiledTest()} but where we reverse the order of
   * module dependencies so we don't run into defect : Compile API should also accept inputModules
   * parameter in random order.
   */
  @Test
  public void twoModulesOneCompiledReverseTest() throws Exception {
    startTest();

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"module1"};
    String[] dependencies = new String[] {"module2"};

    genericProvenanceTest(toRewrite, dependencies);

    // Compare the output
    specialCompareAgainstExpected(toRewrite);

    endTest();
  }

  /**
   * Test of two modules, each with a require document columns statement.
   */
  @Test
  public void twoModulesRequireDocTest() throws Exception {
    startTest();

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"module1", "module2"};
    String[] dependencies = new String[] {};

    genericProvenanceTest(toRewrite, dependencies);

    // Compare the output
    specialCompareAgainstExpected(toRewrite);

    endTest();
  }

  /**
   * Test of two modules, with an imported table, where the imported table is used in the FROM list
   * of a SELECT statement.
   */
  @Test
  public void twoModulesSimpleImportTableTest() throws Exception {
    startTest();

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"module1", "module2"};
    String[] dependencies = new String[] {};

    genericProvenanceTest(toRewrite, dependencies);

    // Compare the output
    specialCompareAgainstExpected(toRewrite);

    endTest();
  }

  /**
   * Test of two modules, with an imported table, where the imported table is used as a source for a
   * dictionary. Currently fails due to defect : "import table" statement has no effect, with
   * exception: In 'twoModulesImportTableTest\module2\b.aql' line 5, column 19: Table 'C2L',
   * required for dictionary 'module2.CompanyDict', not found. Currently this defect is now known as
   * #27339: Compiler unable to compile dictionary coming from imported table.
   */
  // @Test
  public void twoModulesImportTableTest() throws Exception {
    startTest();

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"module1", "module2"};
    String[] dependencies = new String[] {};

    genericProvenanceTest(toRewrite, dependencies);

    // Compare the output
    specialCompareAgainstExpected(toRewrite);

    endTest();
  }

  /**
   * Test of two modules, with an imported function.
   */
  @Test
  public void twoModulesImportFunctionTest() throws Exception {
    startTest();

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"module1", "module2"};
    String[] dependencies = new String[] {};

    genericProvenanceTest(toRewrite, dependencies);

    // Compare the output
    specialCompareAgainstExpected(toRewrite);

    endTest();
  }

  /**
   * Test with two AQL files, where they import the same view under different names.
   */
  @Test
  public void twoModulesTwoViewsWithSameAliasTest() throws Exception {
    startTest();

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"module1", "module2"};
    String[] dependencies = new String[] {};

    genericProvenanceTest(toRewrite, dependencies);

    // Compare the annotated output
    specialCompareAgainstExpected(toRewrite);

    // Compare the TAM of the rewritten AQL
    endTest();
  }

  /**
   * Test with two AQL files, where they import different views under the same name. Currently fails
   * because of defect 26155: Scope of "import view" stmt out of sync with design doc, with
   * exception: com.ibm.avatar.aql.compiler.CompilerException: Compiling AQL encountered 1 errors:
   * In 'twoModulesOneViewWithDiffAliasTest\module2\c.aql' line 3, column 1: Element name 'Name' is
   * declared twice.
   */
  // @Test
  public void twoModulesOneViewWithDiffAliasTest() throws Exception {
    startTest();

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"module1", "module2"};
    String[] dependencies = new String[] {};

    genericProvenanceTest(toRewrite, dependencies);

    // Compare the annotated output
    specialCompareAgainstExpected(toRewrite);

    // Compare the TAM of the rewritten AQL
    endTest();
  }

  /**
   * Test with two modules, where one is compiled, and the other is in source. Tests whether the
   * provenance rewrite considers the scope of import statements at the level of the module, not
   * individual files. The source module imports a table and view from the first compiled module
   * using aliases, and uses it in multiple files.
   */
  @Test
  public void twoModulesImportScopeArtifactWithAliasTest() throws Exception {
    startTest();

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"module2"};
    String[] dependencies = new String[] {"module1"};

    genericProvenanceTest(toRewrite, dependencies);

    // Compare the annotated output
    specialCompareAgainstExpected(toRewrite);

    // Compare the TAM of the rewritten AQL
    endTest();
  }

  /**
   * Same test as {@link twoModulesImportScopeArtifactWithAliasTest}, but individual artifacts are
   * imported without alias.
   */
  @Test
  public void twoModulesImportScopeArtifactNoAliasTest() throws Exception {
    startTest();

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"module2"};
    String[] dependencies = new String[] {"module1"};

    genericProvenanceTest(toRewrite, dependencies);

    // Compare the annotated output
    specialCompareAgainstExpected(toRewrite);

    // Compare the TAM of the rewritten AQL
    endTest();
  }

  /**
   * Same test as {@link twoModulesImportScopeArtifactWithAliasTest}, this time using the import
   * module statement.
   */
  @Test
  public void twoModulesImportScopeModuleTest() throws Exception {
    startTest();

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"module2"};
    String[] dependencies = new String[] {"module1"};

    genericProvenanceTest(toRewrite, dependencies);

    // Compare the annotated output
    specialCompareAgainstExpected(toRewrite);

    // Compare the TAM of the rewritten AQL
    endTest();
  }

  /**
   * Test case using the original NER extractor in defect : Text Analytics : Provenance Rewrite
   * fails to rewrite modules containing detag statements in them, and also modules that depend on
   * such detag modules. We don't use the NER extractor in the BigInsightsExtractorLibrary project
   * because that extractor might change in the future and we want to be able to compare the
   * rewrite.
   */
  @Ignore
  @Test
  public void modularNerTest() throws Exception {
    startTest();

    // Override the input document collection; we need a collection with Document schema (text text)
    DOCS_FILE = new File(TestConstants.ENRON_1K_DUMP);

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite =
        new String[] {"Address", "BigInsightsExtractorsOutput", "City", "CommonFeatures",
            "Continent", "Country", "County", "Date", "DateTime", "Dictionaries", "Disambiguation",
            "InputDocumentProcessor", "EmailAddress", "Facility", "Linguistics", "Location",
            "LocationCandidates", "NotesEmailAddress", "Organization", "OrganizationCandidates",
            "Person", "PersonCandidates", "PhoneNumber", "Region", "SentenceBoundary",
            "StateOrProvince", "Time", "Town", "UDFs", "URL", "WaterBody", "ZipCode"};
    String[] dependencies = new String[] {};

    genericProvenanceTest(toRewrite, dependencies);

    // truncateExpectedFiles ();

    // Compare the annotated output
    specialCompareAgainstExpected(toRewrite);

    // Compare the TAM of the rewritten AQL
    endTest();
  }

  /**
   * Minimal test case for defect : Text Analytics : Provenance Rewrite fails to rewrite modules
   * containing detag statements in them, and also modules that depend on such detag modules.
   */
  @Test
  public void importDetagTest() throws Exception {
    startTest();

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"module1", "module2"};
    String[] dependencies = new String[] {};

    genericProvenanceTest(toRewrite, dependencies);

    // truncateExpectedFiles ();

    // Compare the annotated output
    specialCompareAgainstExpected(toRewrite);

    // Compare the TAM of the rewritten AQL
    endTest();
  }

  /**
   * Same as {@link #importDetagTest()}, this time using import with alias statements.
   */
  @Test
  public void importDetagAliasTest() throws Exception {
    startTest();

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"module1", "module2"};
    String[] dependencies = new String[] {};

    genericProvenanceTest(toRewrite, dependencies);

    // truncateExpectedFiles ();

    // Compare the annotated output
    specialCompareAgainstExpected(toRewrite);

    // Compare the TAM of the rewritten AQL
    endTest();
  }

  /**
   * Test of the MDA (Machine Data Analytics) generic extractors
   */
  @Ignore
  @Test
  public void mdaGenericTest() throws Exception {
    startTest();

    // Override the input document collection
    DOCS_FILE = new File(TestConstants.CISCO_LOGS_1000);

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"common", "extractor_generic"};
    String[] dependencies = new String[] {};

    genericProvenanceTest(toRewrite, dependencies);

    // Compare the annotated output
    specialCompareAgainstExpected(toRewrite);

    // Compare the TAM of the rewritten AQL
    endTest();
  }

  /**
   * Test of the MDA (Machine Data Analytics) DataPower extractors
   */
  @Test
  public void mdaDatapowerTest() throws Exception {
    startTest();

    // Override the input document collection
    DOCS_FILE = new File(TestConstants.CISCO_LOGS_1000);

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"common", "datapower", "extractor_datapower"};
    String[] dependencies = new String[] {};

    genericProvenanceTest(toRewrite, dependencies);

    // Compare the annotated output
    specialCompareAgainstExpected(toRewrite);

    // Compare the TAM of the rewritten AQL
    endTest();
  }

  /**
   * Test of the MDA (Machine Data Analytics) Syslog extractors
   */
  @Ignore
  @Test
  public void mdaSyslogTest() throws Exception {
    startTest();

    // Override the input document collection
    DOCS_FILE = new File(TestConstants.CISCO_LOGS_1000);

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"common", "syslog", "extractor_syslog"};
    String[] dependencies = new String[] {};

    genericProvenanceTest(toRewrite, dependencies);

    // Compare the annotated output
    specialCompareAgainstExpected(toRewrite);

    // Compare the TAM of the rewritten AQL
    endTest();
  }

  /**
   * Test of the MDA (Machine Data Analytics) WAS extractors
   */
  @Test
  public void mdaWasTest() throws Exception {
    startTest();

    // Override the input document collection
    DOCS_FILE = new File(TestConstants.CISCO_LOGS_1000);

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"common", "was", "extractor_was"};
    String[] dependencies = new String[] {};

    genericProvenanceTest(toRewrite, dependencies);

    // Compare the annotated output
    specialCompareAgainstExpected(toRewrite);

    // Compare the TAM of the rewritten AQL
    endTest();
  }

  /**
   * Test of the MDA (Machine Data Analytics) Webaccess extractors
   */
  @Test
  public void mdaWebaccessTest() throws Exception {
    startTest();

    // Override the input document collection
    DOCS_FILE = new File(TestConstants.CISCO_LOGS_1000);

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"common", "webaccess", "extractor_webaccess"};
    String[] dependencies = new String[] {};

    genericProvenanceTest(toRewrite, dependencies);

    // Compare the annotated output
    specialCompareAgainstExpected(toRewrite);

    // Compare the TAM of the rewritten AQL
    endTest();
  }

  /**
   * Test to ensure the rewrite works when SDM optimization is enabled. Added as part of fixing
   * defect : Explain button enabled when provenance rewrite fails. Provenance rewrite exception:
   * Schema [anonymous] does not contain field name '__auto__id'. This defect turned out to be a
   * problem with the SDM rewrite in the Postprocessor. Added a dedicated test for that in
   * {@link AQLBugTests#sdmOverridingSelectBug()}.
   */
  @Test
  public void sdmTest() throws Exception {
    startTest();

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"module1"};
    String[] dependencies = new String[] {};

    genericProvenanceTest(toRewrite, dependencies);

    // Compare the annotated output
    specialCompareAgainstExpected(toRewrite);

    // Compare the TAM of the rewritten AQL
    endTest();
  }

  /**
   * Verifies that nick names enclosed in double quotes are properly rewritten by the provenance
   * rewrite feature. Test case for defect .
   * 
   * @throws Exception
   */
  @Test
  public void namesInQuotesTest() throws Exception {
    startTest();

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"main", "metricsIndicator_dictionaries",
        "metricsIndicator_externalTypes", "metricsIndicator_features", "metricsIndicator_udfs"};
    String[] dependencies = new String[] {};

    genericProvenanceTest(toRewrite, dependencies);

    specialCompareAgainstExpected(toRewrite);

    endTest();
  }

  /**
   * Simple test case to verify that entity names appearing within double quotes are handled
   * properly by provenance rewriter.
   * 
   * @throws Exception
   */
  @Test
  public void namesInQuotesSimpleTest() throws Exception {
    startTest();

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"module1"};
    String[] dependencies = new String[] {};

    genericProvenanceTest(toRewrite, dependencies);

    specialCompareAgainstExpected(toRewrite);

    endTest();
  }

  /**
   * Simple test for table function rewrite. Reproduces defect : ProvenanceRewrite fails for views
   * using table functions
   * 
   * @throws Exception
   */
  @Test
  public void tableFuncTest() throws Exception {
    startTest();

    DOCS_FILE = new File(TestConstants.ENRON_100_DUMP);

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"module1"};
    String[] dependencies = new String[] {};

    genericProvenanceTest(toRewrite, dependencies);

    specialCompareAgainstExpected(toRewrite);

    endTest();
  }

  /**
   * Simple test for table function rewrite, for the AQL in
   * {@link com.ibm.avatar.algebra.test.stable.TableFnTests#basicTest()}.
   * 
   * @throws Exception
   */
  @Test
  public void tableFuncBasicTest() throws Exception {
    startTest();

    DOCS_FILE = new File(TestConstants.ENRON_100_DUMP);

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"basicTest"};
    String[] dependencies = new String[] {};

    genericProvenanceTest(toRewrite, dependencies);

    specialCompareAgainstExpected(toRewrite);

    endTest();
  }

  /**
   * Simple test for table function rewrite, for the AQL in
   * {@link com.ibm.avatar.algebra.test.stable.TableFnTests#importTest()}.
   * 
   * @throws Exception
   */
  @Test
  public void tableFuncImportTest() throws Exception {
    startTest();

    DOCS_FILE = new File(TestConstants.ENRON_100_DUMP);

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"module1", "module2"};
    String[] dependencies = new String[] {};

    genericProvenanceTest(toRewrite, dependencies);

    specialCompareAgainstExpected(toRewrite);

    endTest();
  }

  /**
   * Simple test for table function rewrite, for the AQL in
   * {@link com.ibm.avatar.algebra.test.stable.TableFnTests#importTest2()}.
   * 
   * @throws Exception
   */
  @Test
  public void tableFuncImportTest2() throws Exception {
    startTest();

    DOCS_FILE = new File(TestConstants.ENRON_100_DUMP);

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"module1", "module2"};
    String[] dependencies = new String[] {};

    genericProvenanceTest(toRewrite, dependencies);

    specialCompareAgainstExpected(toRewrite);

    endTest();
  }

  /**
   * Simple test for table function rewrite, for the AQL in
   * {@link com.ibm.avatar.algebra.test.stable.TableFnTests#locatorTest()}.
   * 
   * @throws Exception
   */
  @Ignore
  @Test
  public void tableFuncLocatorTest() throws Exception {
    startTest();

    DOCS_FILE = new File(TestConstants.ENRON_100_DUMP);

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"locatorTest"};
    String[] dependencies = new String[] {};

    genericProvenanceTest(toRewrite, dependencies);

    specialCompareAgainstExpected(toRewrite);

    endTest();
  }

  /**
   * Basic test for when the detag view and some other views that depend on it are in the same
   * module. Reproduces defect 46390: Provenance rewrite incorrect for detag statements in the
   * current module.
   * 
   * @throws Exception
   */
  @Test
  public void singleModuleDetagTest() throws Exception {
    startTest();

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"module1"};
    String[] dependencies = new String[] {};

    genericProvenanceTest(toRewrite, dependencies);

    specialCompareAgainstExpected(toRewrite);

    endTest();
  }

  /**
   * Basic test for when the detag view is in one module and other views that depend on it are in
   * another module.
   * 
   * @throws Exception
   */
  @Test
  public void twoModulesDetagTest() throws Exception {
    startTest();

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"module1", "module2"};
    String[] dependencies = new String[] {};

    genericProvenanceTest(toRewrite, dependencies);

    specialCompareAgainstExpected(toRewrite);

    endTest();
  }

  /**
   * Basic test for when the detag view is in one module, and it is imported and output in another
   * module. Verifies that the detag views are not output, since at the moment provenance stops at a
   * detag statement, there is no provenance to show.
   * 
   * @throws Exception
   */
  @Test
  public void twoModulesDetagDirectOutputTest() throws Exception {
    startTest();

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"module1", "module2", "module3", "module4"};
    String[] dependencies = new String[] {};

    genericProvenanceTest(toRewrite, dependencies);

    specialCompareAgainstExpected(toRewrite);

    endTest();
  }

  /**
   * Extra test for defect : Provenance rewrite incorrect for detag statements in the current
   * module.
   * 
   * @throws Exception
   */
  @Test
  public void watsonDetagTest() throws Exception {
    startTest();

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"watsonagain", "watson"};
    String[] dependencies = new String[] {};

    genericProvenanceTest(toRewrite, dependencies);

    specialCompareAgainstExpected(toRewrite);

    endTest();
  }

  /**
   * Extra test for defect : Provenance rewrite incorrect for detag statements in the current
   * module. Same as {@link #watsonDetagTest()} but with one of the modules as compiled module.
   * 
   * @throws Exception
   */
  @Test
  public void watsonDetag2Test() throws Exception {
    startTest();

    // Make a list of modules to rewrite, and their dependencies
    String[] toRewrite = new String[] {"watsonagain"};
    String[] dependencies = new String[] {"watson"};

    genericProvenanceTest(toRewrite, dependencies);

    specialCompareAgainstExpected(toRewrite);

    endTest();
  }

  /*
   * NEW TESTS GO HERE
   */

  /**
   * Test case for RTC 58105 Tests avoidance of escaping unnecessary characters within string atoms
   * during a provenance re-write Only a single-quote '\'' needs to be escaped - all other
   * characters needn't be escaped during a provenance re-write
   */

  @Test
  public void unescapeDoubleQuotesTest() throws Exception {
    startTest();

    // Test the re-write towards provenance
    genericProvenanceTest(new String[] {"UnescapeDoubleQuotes"}, new String[] {});

    // Compare the annotated output
    specialCompareAgainstExpected(new String[] {"UnescapeDoubleQuotes"});

    endTest();
  }

  /**
   * Tests provenance re-write of single-quote character within extract pattern's dictionary atom
   * Any single-quote character within extract pattern's dictionary atom would already be explicitly
   * escaped within that atom Our provenance-rewrite flow mustn't escape it again -- the compilation
   * from provenance .aql to provenance .aog shall escape it
   */

  @Test
  public void singleQuotesExtractPatternTest() throws Exception {
    startTest();

    // Test the re-write towards provenance
    genericProvenanceTest(new String[] {"avoidEscapeSingleQuoteExtractPattern"}, new String[] {});

    // Compare the annotated output
    specialCompareAgainstExpected(new String[] {"avoidEscapeSingleQuoteExtractPattern"});

    endTest();
  }

  /*
   * PRIVATE METHODS
   */

  /**
   * Generic test method that takes as input two sets of modules: a set of source modules to
   * rewrite, and a set of modules that the source modules depend on, but should not be rewritten.
   * Assumes that the source code for each module of each of the two sets are located in
   * 
   * <pre>
   * testdata/aql/[test class name]/[test case name]/[module name]
   * </pre>
   * 
   * @param srcModules module names of source modules that should be rewritten
   * @param compiledModules module names of modules that the source modules depend on, but should
   *        not be rewritten
   * @throws Exception
   */
  private void genericProvenanceTest(String[] srcModules, String[] compiledModules)
      throws Exception {
    genericProvenanceTest(srcModules, compiledModules, null);
  }

  /**
   * Generic test method that takes as input two sets of modules: a set of source modules to
   * rewrite, and a set of modules that the source modules depend on, but should not be rewritten.
   * Assumes that the source code for each module of each of the two sets are located in
   * 
   * <pre>
   * testdata/aql/[test class name]/[test case name]/[module name]
   * </pre>
   * 
   * @param srcModules module names of source modules that should be rewritten
   * @param compiledModules module names of modules that the source modules depend on, but should
   *        not be rewritten
   * @param eti external artifacts object to be loaded into operator graph
   * @throws Exception
   */
  private void genericProvenanceTest(String[] srcModules, String[] compiledModules,
      ExternalTypeInfo eti) throws Exception {
    if (null == getCurOutputDir()) {
      throw new Exception("genericProvenanceTest() called without calling startTest() first");
    }

    String className = getClass().getSimpleName();
    String testName = getCurPrefix();

    // Compute the location of the current test case's AQL code
    File moduleDir =
        new File(String.format("%s/%s/%s", TestConstants.AQL_DIR, className, testName));

    if (false == moduleDir.exists()) {
      throw new Exception(
          String.format("Directory containing modules %s not found.", moduleDir.getAbsolutePath()));
    }

    // Output directory where the compiled modules go
    File compiledModuleDir = new File(this.getCurOutputDir(), COMPILED_MODULE_DIR);
    compiledModuleDir.mkdirs();
    String compiledModuleDirURI = compiledModuleDir.toURI().toString();

    // Output directory where the rewritten source AQL goes
    File rewriteSrcDir = new File(this.getCurOutputDir(), REWRITTEN_SRC_MODULE_DIR);
    rewriteSrcDir.mkdirs();
    String rewriteSrcDirURI = rewriteSrcDir.toURI().toString();

    // Output directory where the compiled version of the rewritten AQL goes
    File rewriteCompiledModuleDir = new File(this.getCurOutputDir(), REWRITTEN_COMPILED_MODULE_DIR);
    rewriteCompiledModuleDir.mkdirs();
    String rewriteCompiledModuleDirURI = rewriteCompiledModuleDir.toURI().toString();

    // Some basic sanity check
    if (null == srcModules)
      throw new Exception("The set of source modules must be non-null");
    if (null == compiledModules)
      compiledModules = new String[] {};

    // Construct module URIs
    String[] srcModuleURIs = new String[srcModules.length];
    String[] rewriteSrcModuleURIs = new String[srcModules.length];
    for (int i = 0; i < srcModuleURIs.length; i++) {
      String uri = new File(moduleDir, srcModules[i]).toURI().toString();
      srcModuleURIs[i] = uri;

      uri = new File(rewriteSrcDir, srcModules[i]).toURI().toString();
      rewriteSrcModuleURIs[i] = uri;
    }

    String[] compiledModuleURIs = new String[compiledModules.length];
    for (int i = 0; i < compiledModuleURIs.length; i++) {
      String uri = new File(moduleDir, compiledModules[i]).toURI().toString();
      compiledModuleURIs[i] = uri;
    }

    Log.info("Compiling input src modules '%s' and their dependencies '%s'...",
        Arrays.asList(srcModules), Arrays.asList(compiledModules));

    // Prepare the compilation parameters
    CompileAQLParams params = new CompileAQLParams();
    String[] allModuleURIs = new String[srcModules.length + compiledModules.length];
    System.arraycopy(srcModuleURIs, 0, allModuleURIs, 0, srcModuleURIs.length);
    System.arraycopy(compiledModuleURIs, 0, allModuleURIs, srcModuleURIs.length,
        compiledModuleURIs.length);
    params.setInputModules(allModuleURIs);
    params.setOutputURI(compiledModuleDirURI);
    params.setTokenizerConfig(new TokenizerConfig.Standard());

    // The provenance rewrite assumes that the input modules compile. Compile them to make sure. We
    // will also use the
    // compiled dependent modules later on, when we instantiate an operator graph for the rewritten
    // modules
    compileAndDumpErrors(params);

    // Rewrite the source modules
    Log.info("Rewriting input src modules '%s'...", Arrays.asList(srcModules));

    params = new CompileAQLParams();
    params.setInputModules(srcModuleURIs);
    params.setModulePath(compiledModuleDirURI);
    params.setOutputURI(rewriteSrcDirURI);
    // params.setPerformSDM (false);
    params.setTokenizerConfig(new TokenizerConfig.Standard());

    AQLProvenanceRewriter rewriter = new AQLProvenanceRewriter();
    rewriter.rewriteAQL(params, null);

    // Compile the rewritten source modules and place the .tams in rewriteCompiledModuleDirURI
    Log.info("Compiling rewritten src modules to '%s':\n%s", rewriteCompiledModuleDir.getPath(),
        StringUtils.join(rewriteSrcModuleURIs, "\n\t"));

    params = new CompileAQLParams();
    params.setInputModules(rewriteSrcModuleURIs);
    // params.setPerformSDM (false);
    // Before compiling, remove the compiled version of modules that we have rewritten, to get
    // around a Compiler defect
    // that makes a compiled version of a module trump the source version. We also add the
    // rewriteCompiledModuleDirURI
    // to the module path for the same reason. (see Comment #4 in defect : Compile API should also
    // accept
    // inputModules parameter in random order)
    for (String srcModuleName : srcModules) {
      File compiledModule = new File(compiledModuleDir, String.format("%s.tam", srcModuleName));
      compiledModule.delete();
    }
    params.setModulePath(String.format("%s;%s", compiledModuleDirURI, rewriteCompiledModuleDirURI));
    params.setOutputURI(rewriteCompiledModuleDirURI);
    params.setTokenizerConfig(new TokenizerConfig.Standard());

    compileAndDumpErrors(params);

    // Copy the dependency modules from where they got compiled to in Step 1 to the same folder as
    // the rewritten
    // compiled modules, so we can instantiate an operator graph with the rewritten compiled modules
    // dir as the module
    // path
    Log.info("Copying compiled modules from '%s' to '%s'...", compiledModuleDir.getPath(),
        rewriteCompiledModuleDir.getPath());
    for (int i = 0; i < compiledModules.length; i++) {
      String compiledModuleName = String.format("%s.tam", compiledModules[i]);
      File src = new File(compiledModuleDir, compiledModuleName);
      File dest = new File(rewriteCompiledModuleDir, compiledModuleName);

      Log.info("Copying '%s' to '%s'...", src.getPath(), dest.getPath());
      FileUtils.copyFile(src, dest);
    }

    // Now we have the rewritten compiled modules, along with original compiled modules into a
    // single directory, so we
    // can load an operator graph
    String[] allModuleNames = new String[srcModules.length + compiledModules.length];
    System.arraycopy(srcModules, 0, allModuleNames, 0, srcModules.length);
    System.arraycopy(compiledModules, 0, allModuleNames, srcModules.length, compiledModules.length);
    Log.info(
        "Instantiating provenance operator graph with modules '%s' and module path URI '%s'...",
        Arrays.asList(allModuleNames), rewriteCompiledModuleDirURI);
    OperatorGraph og = OperatorGraph.createOG(allModuleNames, rewriteCompiledModuleDirURI, eti,
        new TokenizerConfig.Standard());

    // Execute the graph
    Log.info("Executing provenance operator graph on collection '%s'...", DOCS_FILE.getPath());
    annotateAndPrint(DOCS_FILE, og);
  }

  /**
   * Convenience method that wraps CompileAQL.compile() in some code that dumps the list of compile
   * errors (if any) to STDERR
   * 
   * @param params parameters that you would normally pass to
   *        {@link CompileAQL#compile(CompileAQLParams)}
   */
  private void compileAndDumpErrors(CompileAQLParams params)
      throws TextAnalyticsException, CompilerException {
    try {
      CompileAQL.compile(params);
    } catch (CompilerException e) {
      // Generate some more verbose console output for compile errors.
      List<Exception> errors = e.getSortedCompileErrors();
      System.err.printf("%d error(s) during compilation; dumping stack traces.\n", errors.size());
      for (Exception exception : errors) {
        exception.printStackTrace();
      }

      throw e;
    }
  }

  /**
   * Special method to compare the result of {@link #genericProvenanceTest(String[], String[])} with
   * the expected result. Compares the following in this order:
   * <ul>
   * <li>The rewritten AQL code of each input module;</li>
   * <li>The compiled TAM of each of the rewritten version of each input module.</li>
   * <li>The HTML output of the rewritten TAMs;</li>
   * </ul>
   * 
   * @param toRewrite set of input module that were rewritten
   * @throws Exception
   * @throws IOException
   */
  private void specialCompareAgainstExpected(String[] toRewrite) throws Exception, IOException {

    // Compare the rewritten AQL code
    for (int i = 0; i < toRewrite.length; i++) {
      File moduleDir = new File(
          String.format("%s/%s/%s", getCurOutputDir(), REWRITTEN_SRC_MODULE_DIR, toRewrite[i]));

      File[] aqlFiles = moduleDir.listFiles(new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
          return name.endsWith(".aql");
        }
      });

      for (File file : aqlFiles) {
        compareAgainstExpected(
            String.format("%s/%s/%s", REWRITTEN_SRC_MODULE_DIR, toRewrite[i], file.getName()),
            true);
      }
    }

    // Compare the compiled TAMs of the rewritten version
    for (int i = 0; i < toRewrite.length; i++) {
      compareAgainstExpected(
          String.format("%s/%s.tam", REWRITTEN_COMPILED_MODULE_DIR, toRewrite[i]), true);
    }

    // Uncomment the following lines when adding a test with new gold standard files,
    // run the test, and then re-comment once done.

    // truncateExpectedFiles ();
    // truncateOutputFiles (false);

    compareTopDirAgainstExpected(true);

  }

}
