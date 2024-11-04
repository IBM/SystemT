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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.PrintWriter;
import java.net.URI;
import java.util.ArrayList;

import org.junit.Assert;
import org.junit.Test;

import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.function.scalar.FunctionInvocationTracker;
import com.ibm.avatar.algebra.util.test.MemoryProfiler;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.algebra.util.tokenize.TokenizerConfig;
import com.ibm.avatar.api.CompileAQL;
import com.ibm.avatar.api.CompileAQLParams;
import com.ibm.avatar.api.DocReader;
import com.ibm.avatar.api.ExternalTypeInfo;
import com.ibm.avatar.api.ExternalTypeInfoFactory;
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.api.OperatorGraphImpl;
import com.ibm.avatar.api.exceptions.ModuleLoadException;
import com.ibm.avatar.api.tam.ModuleMetadata;
import com.ibm.avatar.api.tam.ModuleMetadataFactory;
import com.ibm.avatar.api.tam.ViewMetadata;
import com.ibm.avatar.aql.catalog.Catalog;

/**
 * Several tests to verify modular AQLs
 * 
 */
public class TAMTests extends RuntimeTestHarness {
  public static final File DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR, "/TAMTests/input.zip");

  // Main method for running one test at a time.
  public static void main(String[] args) {
    try {

      TAMTests t = new TAMTests();

      // t.setUp ();

      long startMS = System.currentTimeMillis();

      t.srmNodeUnionizeNERTest();

      long endMS = System.currentTimeMillis();

      // t.tearDown ();

      double elapsedSec = (endMS - startMS) / 1000.0;

      System.err.printf("Test took %1.3f sec.\n", elapsedSec);

      MemoryProfiler.dumpHeapSize("After test");

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Verifies creation of a simple module with export view and output view statements. Creates a
   * module by name 'phone' with a simple extractor to extract phone numbers.
   * 
   * @throws Exception
   */
  @Test
  public void simpleModuleTest() throws Exception {
    startTest();
    super.setPrintTups(true);
    compileAndRunModule("phone", DOCS_FILE, null);
    endTest();
  }

  /**
   * Test for defect : Module does not compile, but there is no compiler error in Problems view, or
   * Eclipse log. Indexer goes on and on.
   * 
   * @throws Exception
   */
  @Test
  public void nerCustomizedTest() throws Exception {
    startTest();
    super.setPrintTups(true);

    String[] moduleNames = new String[] {"Address", "BigInsightsExtractorsOutput", "City",
        "CommonFeatures", "Continent", "Country", "County", "CurrencyAmount", "Date", "DateTime",
        "Dictionaries", "Disambiguation", "EmailAddress", "Facility", "FinancialAnnouncements",
        "FinancialDictionaries", "FinancialEvents", "GenericExtractorsOutput",
        "InputDocumentProcessor", "Linguistics", "Location", "LocationCandidates",
        "NotesEmailAddress", "Number", "Organization", "OrganizationCandidates", "Person",
        "PersonCandidates", "PhoneNumber", "Region", "SentenceBoundary", "StateOrProvince", "Time",
        "Town", "UDFs", "URL", "WaterBody", "ZipCode"};

    compileAndRunModules(moduleNames, DOCS_FILE, null);
    endTest();
  }

  /**
   * A simple test case for defect . <br/>
   * module1 defines a view View1, which is imported by module2 using an alias name View1Alias. In
   * module2, import.aql imports the module1.View1 and assigns an alias name to it; The file
   * candidate.aql attempts to output the imported view. The names of the aql files of module2 are
   * chosen such that the file containing output view statement is parsed before the file containing
   * import statement. This results in a scenario where in, an output view statement is encountered
   * before import view statement, due to which {@link Catalog#outputOrder} registers the name
   * 'module2.View1Alias' instead of its canonical name 'module1.View1'. This causes incorrect view
   * name being added to the generated AOG.
   * 
   * @throws Exception
   */
  @Test
  public void outputViewBeforeImportViewAliasTest() throws Exception {
    startTest();
    setPrintTups(true);

    String[] moduleNames = {"module1", "module2"};
    compileAndRunModules(moduleNames, DOCS_FILE, null);

    endTest();
  }

  /**
   * Verifies AQL module creation & execution using simple AQL that uses inlined and file based
   * dictionaries to extract person names. Creates a module by name 'person'.
   * 
   * @throws Exception
   */
  @Test
  public void personSimpleTest() throws Exception {
    startTest();
    super.setPrintTups(true);
    compileAndRunModule("person", DOCS_FILE, null);
    endTest();
  }

  /**
   * Verifies creation of a simple module with export view and output view statements inside a ZIP
   * file. Creates a module by name 'phone' with a simple extractor to extract phone numbers.
   * 
   * @throws Exception
   */
  @Test
  public void phoneZipTest() throws Exception {
    startTest();
    super.setPrintTups(true);
    String outputURI = "phone.zip";

    compileModules(new String[] {"phoneZipTest"}, new URI(outputURI).toString());
    runModule(DOCS_FILE, "phoneZipTest", new File(getCurOutputDir(), outputURI).toURI().toString());

    assertEquals(2, getCurOutputDir().listFiles().length);
    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Verifies creation of multiple modules within a ZIP file
   * 
   * @throws Exception
   */
  @Test
  public void multiZipTest() throws Exception {
    startTest();

    super.setPrintTups(true);
    String outputURI = "hello.zip";

    compileModules(new String[] {"multiZipTest", "helloworld", "math"}, outputURI);

    // TODO: compiling the AQL in multiZipTest currently doesn't work ..
    // does the ZIP file need to be added to the modulePath? -- eyhung
    // runTAMTest (DOCS_FILE, "helloworld", "math");

    endTest();
  }

  /**
   * Verifies creation of a simple module with export view and output view statements inside a JAR
   * file. Creates a module by name 'phone' with a simple extractor to extract phone numbers.
   * 
   * @throws Exception
   */
  @Test
  public void phoneJarTest() throws Exception {
    startTest();
    super.setPrintTups(true);
    String outputURI = "phone.jar";

    compileModules(new String[] {"phoneJarTest"}, new URI(outputURI).toString());
    runModule(DOCS_FILE, "phoneJarTest", new File(getCurOutputDir(), outputURI).toURI().toString());
    assertEquals(2, getCurOutputDir().listFiles().length);
    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Simple test for multi module that imports views from another module and does simple extraction
   * from it.
   * 
   * @throws Exception
   */
  @Test
  public void personPhoneSimpleTest() throws Exception {
    startTest();
    compileModules(new String[] {"person", "phone", "personPhone"}, null);

    super.setPrintTups(true);
    runModule(DOCS_FILE, "personPhone", getCurOutputDir().toURI().toString());
    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Verifies that a module can contain more than one AQL file and views defined in one AQL file can
   * be used in the other without having to import (as the files are under same module).
   * 
   * @throws Exception
   */
  @Test
  public void multipleFilesTest() throws Exception {
    startTest();
    compileModules(new String[] {"person", "phone", "multipleFiles"}, null);
    setPrintTups(true);
    runModule(DOCS_FILE, "multipleFiles", getCurOutputDir().toURI().toString());
    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Tests whether inline and file based dictionaries are compiled into a proper TAM file.
   * 
   * @throws Exception
   */
  @Test
  public void dictSimpleTest() throws Exception {
    startTest();
    compileModule("dictSimple");
    endTest();
  }

  /**
   * Verifies that exported views are compiled and are available for use in other modules. Verifies
   * the fix for defect # 25158.
   * 
   * @throws Exception
   */
  @Test
  public void importViewTest() throws Exception {
    startTest();
    compileModules(new String[] {"importViewTest", "exportView"}, null);

    setPrintTups(true);
    runModule(DOCS_FILE, "importViewTest", getCurOutputDir().toURI().toString());
    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Verifies that views imported through "import module" statement work fine
   * 
   * @throws Exception
   */
  @Test
  public void importModuleTest() throws Exception {
    startTest();
    compileModules(new String[] {"importModuleTest", "exportView"}, null);
    setPrintTups(true);
    runModule(DOCS_FILE, "importModuleTest", getCurOutputDir().toURI().toString());

    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Verifies that views imported through "import view ... as <alias>" work fine
   * 
   * @throws Exception
   */
  @Test
  public void importViewAliasTest() throws Exception {
    startTest();
    compileModules(new String[] {"importViewAliasTest", "exportView"}, null);

    runModule(DOCS_FILE, "importViewAliasTest", getCurOutputDir().toURI().toString());
    endTest();
  }

  /**
   * This test consumes imported views in context of built-in functions.
   */
  @Test
  public void consumeImportedViewTest() throws Exception {
    startTest();

    compileModules(new String[] {"consumeImportedViewTest", "person", "personPhone", "phone"},
        null);
    super.setPrintTups(true);
    runModule(DOCS_FILE, "consumeImportedViewTest", getCurOutputDir().toURI().toString());

    compareAgainstExpected(true);
    endTest();

  }

  /**
   * Verifies that When there are multiple extract dictionary statements defined on targets from a
   * view imported under different names, the two dictionary extractions should result in a single
   * SDM node.
   * 
   * @throws Exception
   */
  @Test
  public void sdmSimpleTest() throws Exception {
    startTest();
    compileModules(new String[] {"person", "sdmSimpleTest"}, null);
    runModule(DOCS_FILE, "sdmSimpleTest", getCurOutputDir().toURI().toString());
    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Test to verify that SDM nodes are not merged, when two dictionaries of same name, but with
   * different contents & under two different modules are stitched together. <br/>
   * In moduleA, make my.dict have two entries X and Y In moduleB, make my.dict have two entries Y
   * and Z Make an OG from both modules and run on input "X Y Z". We want to make sure
   * moduleA.Extract1 does not contain 'Z', and moduleB.Extract1 does not contain 'X'. That is, the
   * merging of SDM nodes does not result in outputs from a dictionary in one module polluting
   * outputs of a dictionary with the same name in a different module.
   * 
   * @throws Exception
   */
  @Test
  public void sdmSimpleNegativeTest() throws Exception {
    startTest();

    String[] moduleNames = new String[] {"moduleA", "moduleB"};
    compileModules(moduleNames, null);

    File inputFile = new File(getCurTestDir(), "doc.del");
    String modulePath = getCurOutputDir().toURI().toString();

    setPrintTups(true);

    runModule(inputFile, "moduleA", modulePath);
    runModule(inputFile, "moduleB", modulePath);

    compareAgainstExpected(true);

    endTest();
  }

  /**
   * Test to verify the following: <br/>
   * 1) When AQLs in two modules perform a regex extraction on Document.text, ensure that the SRM
   * nodes on Document.text are merged during operator graph loading. <br/>
   * 2) Multiple SRM nodes over qualified non-Document view should be merged
   * 
   * @throws Exception
   */
  @Test
  public void srmNodeUnionizeTest() throws Exception {
    startTest();

    String[] moduleNames = new String[] {"module0", "module1", "module2"};
    try {
      // compile & load module
      compileModules(moduleNames, null);

      // Test 1: Load all 3 modules to ensure that there are not any duplicate SRM nodes
      OperatorGraph.createOG(moduleNames, getCurOutputDir().toURI().toString(), null, null);

      setPrintTups(true);

      // Test 2: Run module1 and modue2 and compare against expected results
      runModule(DOCS_FILE, "module1", getCurOutputDir().toURI().toString());
      runModule(DOCS_FILE, "module2", getCurOutputDir().toURI().toString());

      // compare the generated tam files and results
      compareAgainstExpected(false);
    } catch (Exception e) {
      Assert.fail(String.format("No exceptions expected, but received :%s", e.getMessage()));
    }
  }

  /**
   * Minimal test case to reproduce the following defects: 35244: Text Analytics Runtime : SRM
   * [Shared Regex Matching] optimization issue with modular NER AQL. <br/>
   * 35249: Text Analytics Runtime : Buffer not initialized exception on modular NER AQL <br/>
   * In module1, create two views that use regular expressions and generate SRM nodes in AOG.
   * Similarly create module2 with two different regexes, and with extract output columns of
   * different names than the ones used in module1. Attempt to run both the modules together.
   * Ideally, there should not be any problem. However, due to a bug in SRM optimization, the user
   * is forced to name all the output columns to same name for these AQLs to work together.
   * 
   * @throws Exception
   */
  @Test
  public void srmNodeUnionizeSimpleTest() throws Exception {
    startTest();

    String[] moduleNames = new String[] {"module1", "module2"};

    File input =
        new File(TestConstants.TEST_DOCS_DIR, "/TAMTests/srmNodeUnionizeSimpleTest/data.zip");
    try {
      setPrintTups(true);
      compileAndRunModules(moduleNames, input, null);
      compareAgainstExpected(false);
    } catch (Exception e) {
      Assert.fail(String.format("No exceptions expected, but received %s", e.getMessage()));
    }
    endTest();
  }

  /**
   * Exhaustive test case from NER to reproduce the following defects: <br/>
   * 35244: Text Analytics Runtime : SRM [Shared Regex Matching] optimization issue with modular NER
   * AQL. <br/>
   * 35249: Text Analytics Runtime : Buffer not initialized exception on modular NER AQL <br/>
   * In module1, create two views that use regular expressions and generate SRM nodes in AOG.
   * Similarly create module2 with two different regexes, and with extract output columns of
   * different names than the ones used in module1. Attempt to run both the modules together.
   * Ideally, there should not be any problem. However, due to a bug in SRM optimization, the user
   * is forced to name all the output columns to same name for these AQLs to work together. As a
   * work around, if the user renames output columns to same name,due to a bug in SRM node merge
   * logic, some SRM nodes go missing after the merge, resulting in BufferNotInitialized error.
   * 
   * @throws Exception
   */
  @Test
  public void srmNodeUnionizeNERTest() throws Exception {
    startTest();

    String[] moduleNames = new String[] {"Address", "BigInsightsExtractors_Export",
        "BigInsightsExtractors_Output", "City", "CommonFeatures", "Continent", "Country", "County",
        "Date", "DateTime", "Dictionaries", "Disambiguation", "DocumentDetagger", "EmailAddress",
        "Facility", "Linguistics", "Location", "Location_Candidates", "NotesEmailAddress",
        "Organization", "Organization_Candidates", "Person", "Person_Candidates", "PhoneNumber",
        "Region", "SentenceBoundary", "StateOrProvince", "Time", "Town", "UDFs", "URL", "WaterBody",
        "ZipCode"};

    File input = new File(TestConstants.TEST_DOCS_DIR, "/TAMTests/srmNodeUnionizeNERTest/data");
    setPrintTups(true);

    compileAndRunModules(moduleNames, input, null);
    compareAgainstExpected(false);

    endTest();
  }

  /**
   * Fix for GHE#93: ModuleLoadException: ArrayIndexOutOfBoundsException in
   * AOGMultiOpTree$RegexesTokOp.toOpTree
   * 
   * @throws Exception
   */
  @Test
  public void srmBugTest() throws Exception {
    startTest();

    setPrintTups(true);
    compileModule("module1");
    compileModule("module2");
    runModule(DOCS_FILE, "module2", getCurOutputDir().toURI().toString());

    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Minimal test case to reproduce defect : Module names that contain <br/>
   * illegal URI characters are not handled correctly. Module names should be able <br/>
   * to contain characters that are illegal in URIs, such as space. <br/>
   * 
   * @throws Exception
   */
  @Test
  public void illegalURITest() throws Exception {
    startTest();

    String[] moduleNames = new String[] {"illegal name"};
    try {
      // Compute the location of the current test case's top-level AQL file.
      File aqlModuleDir = getCurTestDir();

      String compiledModuleURI;

      // URI where compiled modules should be dumped -- set to regression/actual
      compiledModuleURI = new File(String.format("%s", getCurOutputDir())).toURI().toString();

      // set up the directories containing the input modules we want to compile
      ArrayList<String> moduleDirectories = new ArrayList<String>();

      for (String moduleName : moduleNames) {
        String moduleDirectoryName = new File(String.format("%s/%s", aqlModuleDir, moduleName))
            .getCanonicalFile().toURI().toString();

        // this undoes the good work done by the previous command so that we
        // can test calls with a space from within the Java test suite.
        // CompileAQL now handles illegal URI Strings and will internally convert them to
        // legal URI Strings. -- eyhung
        moduleDirectoryName = moduleDirectoryName.replaceAll("%20", " ");

        moduleDirectories.add(moduleDirectoryName);
      }

      // Compile
      CompileAQLParams params = new CompileAQLParams(moduleDirectories.toArray(new String[1]),
          compiledModuleURI, compiledModuleURI, new TokenizerConfig.Standard());

      CompileAQL.compile(params);

      // Load the module to ensure that we handle the illegal URI character correctly
      OperatorGraph.createOG(moduleNames, getCurOutputDir().toURI().toString(), null, null);

    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(String.format("No exceptions expected, but received: %s", e.getMessage()));
    }
  }

  /**
   * Minimal test case to re-create defect# 29286. Test scenario: 1) module1 performing extraction
   * on Document.text using dictionary MetricDict and UnitDict. <br>
   * 2) module2 performing extraction on Document.text using dictionary AmountNegativeClueDict and
   * UnitDict(imported from module1). <br>
   * 3) Compiler generates SDM node for both the modules. <br>
   * 4) Loader stitches the both the modules. <br>
   * 5) While annotating document- exception coming from OperatorGraph. <br>
   * 6) Turning off SDM optimization while compiling results in successfull annotations.
   * 
   * @throws Exception
   */
  @Test
  public void sdmNodeUnionize2Test() throws Exception {
    startTest();

    String[] moduleNames = new String[] {"module1", "module2"};
    try {
      // compile & load module
      compileModules(moduleNames, null);
      setPrintTups(true);

      runModule(new File(TestConstants.TEST_DOCS_DIR, "/TAMTests/ibmQuarterlyReports.zip"),
          "module2", getCurOutputDir().toURI().toString());

      // compare the generated tam files and results
      compareAgainstExpected(false);
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(String.format("No exceptions expected, but received :%s", e.getMessage()));
    }
  }

  /**
   * Test scenario similar to {@link #sdmNodeUnionize2Test()}. <br>
   * Only difference - AmountNegativeClueDict with match mode different than Default.
   * 
   * @throws Exception
   */
  @Test
  public void sdmNodeUnionize3Test() throws Exception {
    startTest();

    String[] moduleNames = new String[] {"module1", "module2"};
    try {
      // compile & load module
      compileModules(moduleNames, null);
      setPrintTups(true);

      runModule(new File(TestConstants.TEST_DOCS_DIR, "/TAMTests/ibmQuarterlyReports.zip"),
          "module2", getCurOutputDir().toURI().toString());

      // compare the generated tam files and results
      compareAgainstExpected(false);
    } catch (Exception e) {
      Assert.fail(String.format("No exceptions expected, but received :%s", e.getMessage()));
    }
  }

  /**
   * Test case to re-create exact scenario mentioned in defect# 29286.
   * 
   * @throws Exception
   */
  @Test
  public void sdmNodeUnionize4Test() throws Exception {
    startTest();

    String[] moduleNames = new String[] {"module1A", "module2"};
    try {
      // compile & load module
      compileModules(moduleNames, null);
      setPrintTups(true);

      runModule(new File(TestConstants.TEST_DOCS_DIR, "/TAMTests/ibmQuarterlyReports.zip"),
          "module2", getCurOutputDir().toURI().toString());

      // compare the generated tam files and results
      compareAgainstExpected(true);
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(String.format("No exceptions expected, but received :%s", e.getMessage()));
    }
  }

  /**
   * Test to verify the following: <br/>
   * 1) When AQLs in two modules perform a dictionary extraction on Document.text, ensure that the
   * SDM nodes on Document.text are merged during operator graph loading. <br/>
   * 2) Multiple SDM nodes over qualified non-Document view should be merged
   * 
   * @throws Exception
   */
  @Test
  public void sdmNodeUnionizeTest() throws Exception {
    startTest();

    String[] moduleNames = new String[] {"module0", "module1", "module2", "person"};
    try {
      // compile & load module
      compileModules(moduleNames, null);

      // Test 1: Load all 4 modules to ensure that there are not any duplicate SDM nodes
      OperatorGraph.createOG(moduleNames, getCurOutputDir().toURI().toString(), null, null);

      setPrintTups(true);

      // Test 2: Run module1 and modue2 and compare against expected results
      runModule(DOCS_FILE, "module1", getCurOutputDir().toURI().toString());
      runModule(DOCS_FILE, "module2", getCurOutputDir().toURI().toString());

      // compare the generated tam files and results
      compareAgainstExpected(false);
    } catch (Exception e) {
      Assert.fail(String.format("No exceptions expected, but received :%s", e.getMessage()));
    }
  }

  /**
   * Verify whether the validation of dictionaries from tables succeeds when the dictionary is
   * defined in a file that is parsed before the file containing the table definition. Replicates
   * defect : CompileAQL validates dictionaries too early. Commented out because it currently fails.
   * 
   * @throws Exception
   */
  @Test
  public void earlyDictValidationBugTest() throws Exception {
    startTest();
    compileModule("earlyDictValidationBug");
    endTest();
  }

  /*
   * ADD NEW TESTS HERE
   */

  /**
   * Verifies that output views that are not exported are written out to metadata file.
   * 
   * @throws Exception
   */
  @Test
  public void outputViewMetadataTest() throws Exception {
    startTest();
    compileModule("outputViewMetadataTest");

    ModuleMetadata metadata = ModuleMetadataFactory.readMetaData("outputViewMetadataTest",
        getCurOutputDir().toURI().toString());
    String[] outputViews = metadata.getOutputViews();

    assertNotNull("There are no output views in module", outputViews);
    assertEquals("Number of output views for module 'outputViewMetadataTest' must be 1", 1,
        outputViews.length);
    assertEquals("PhoneNumber is not a part of module metadata",
        "outputViewMetadataTest.PhoneNumber", outputViews[0]);

    ViewMetadata vmd = metadata.getViewMetadata("outputViewMetadataTest.PhoneNumber");
    assertNotNull("View metadata should not be null", vmd);
    assertTrue("'output' parameter of PhoneNumber view metadata should be true",
        vmd.isOutputView());
    assertFalse("'external' parameter of PhoneNumber view metadata should be false",
        vmd.isExternal());
    assertFalse("'exported' parameter of PhoneNumber view metadata should be false",
        vmd.isExported());
    assertNotNull("schema of PhoneNumber view should not be null", vmd.getViewSchema());
    endTest();
  }

  /**
   * Verify that legacy TAMs with no cost record for the Document view will still load and run
   * correctly
   * 
   * @throws Exception
   */
  @Test
  public void noCostRecDocTest() throws Exception {
    startTest();

    setPrintTups(true);

    runModule(DOCS_FILE, "oldImportViewTest", getCurTestDir().toURI().toString());

    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Verify that legacy TAMs with no cost record for an external view will still load and run
   * correctly
   * 
   * @throws Exception
   */
  @Test
  public void noCostRecExtViewTest() throws Exception {
    startTest();

    setPrintTups(true);
    final String JSON_FILE = TestConstants.TEST_DOCS_DIR + "/DocScanTests/jsonTests/EView.json";

    runModule(new File(JSON_FILE), "oldSalutationTest", getCurTestDir().toURI().toString());

    // this output is not very large, so no need for truncation
    compareAgainstExpected(false);
    endTest();
  }

  /**
   * Verifies that scalar list fieldtype is being written out correctly to metadata file.
   * 
   * @throws Exception
   */
  @Test
  public void scalarListViewMetadataTest() throws Exception {
    startTest();
    compileModule("scalarListViewMetadataTest");

    ModuleMetadata metadata = ModuleMetadataFactory.readMetaData("scalarListViewMetadataTest",
        getCurOutputDir().toURI().toString());
    String[] outputViews = metadata.getOutputViews();

    assertNotNull("There are no output views in module", outputViews);
    assertEquals("Number of output views for module 'scalarListViewMetadataTest' must be 1", 1,
        outputViews.length);

    ViewMetadata vmd = metadata.getViewMetadata("scalarListViewMetadataTest.isComicFanFromTweet");
    assertNotNull("View metadata should not be null", vmd);
    assertNotNull("schema of output view should not be null", vmd.getViewSchema());
    FieldType badFieldType = vmd.getViewSchema().getFieldTypeByName("comicMatches");
    assertFalse("schema of scalar list output view should not be Unknown",
        badFieldType.toString().equals("Unknown"));
    endTest();
  }

  /**
   * Test to verify, that the UDFs are importable thru the 'import module ..' statement.
   * 
   * @throws Exception
   */
  @Test
  public void importFuncTest() throws Exception {
    startTest();
    compileModules(new String[] {"exportFunc", "importFuncTest"}, null);

    super.setPrintTups(true);

    runModule(DOCS_FILE, "importFuncTest", getCurOutputDir().toURI().toString());

    // truncateExpectedFiles ();
    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Test to verify, that the UDFs are importable thru the 'import fuction ... as <alias>'
   * statement.
   * 
   * @throws Exception
   */
  @Test
  public void importFuncAliasTest() throws Exception {
    startTest();
    compileModules(new String[] {"importFuncAliasTest", "exportFunc"}, null);
    setPrintTups(true);

    runModule(DOCS_FILE, "importFuncAliasTest", getCurOutputDir().toURI().toString());

    // truncateExpectedFiles ();
    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Test to verify, that table are imported using the 'import module ...' statement.
   * 
   * @throws Exception
   */
  @Test
  public void importTableTest() throws Exception {
    startTest();
    super.setPrintTups(true);

    ExternalTypeInfo eti = ExternalTypeInfoFactory.createInstance();
    eti.addTable("exportTable.externalTable",
        new File(getModuleDirectory("exportTable", "importTableTest"), "ExtTab1.csv").toURI()
            .toString());

    // compile dependent module first
    compileModule("exportTable");
    compileAndRunModule("importTableTest", new File(TestConstants.ENRON_1_DUMP), eti);

    compareAgainstExpected(true);
    endTest();
  }

  /**
   * This test verifies importing of table using 'import table ...' and 'import table ... as alias
   * ..' statement.
   * 
   * @throws Exception
   */
  @Test
  public void importTableAliasTest() throws Exception {
    startTest();

    setPrintTups(true);

    ExternalTypeInfo eti = ExternalTypeInfoFactory.createInstance();
    eti.addTable("exportTable.externalTable",
        new File(getModuleDirectory("exportTable", "importTableAliasTest"), "ExtTab1.csv").toURI()
            .toString());

    // compile dependent module first
    compileModule("exportTable");
    compileAndRunModule("importTableAliasTest", new File(TestConstants.ENRON_1_DUMP), eti);

    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Test to verify, that dictionaries are imported using the 'import module ...' statement.
   * 
   * @throws Exception
   */
  @Test
  public void importDictTest() throws Exception {
    startTest();
    compileModules(new String[] {"exportDict", "importDictTest"}, null);

    super.setPrintTups(true);

    runModule(DOCS_FILE, "importDictTest", getCurOutputDir().toURI().toString());

    compareAgainstExpected(true);
    endTest();
  }

  /**
   * This test verifies importing of dictionaries using 'import dictionary ...' and 'import
   * dictionary ... as alias ..' statement.
   * 
   * @throws Exception
   */
  @Test
  public void importDictAliasTest() throws Exception {
    startTest();
    compileModules(new String[] {"exportDict", "importDictAliasTest"}, null);
    super.setPrintTups(true);

    runModule(DOCS_FILE, "importDictAliasTest", getCurOutputDir().toURI().toString());

    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Scenario: Attempt to compile Person, Phone (two independent modules) and PersonPhone (which
   * depends on Person and Phone). Provide no modulepath as we expect the compiler to resolve
   * dependencies by using input modules passed to it.
   * 
   * @throws Exception
   */
  @Test
  public void resolveDependencyWtihInputModulesTest() throws Exception {
    startTest("resolveDependencyWtihInputModulesTest");

    File baseDir =
        new File(TestConstants.AQL_DIR + "/TAMTests/resolveDependencyWtihInputModulesTest/");

    // 1. prepare inputModules
    String uriPerson = new File(baseDir, "person").toURI().toString();
    String uriPhone = new File(baseDir, "phone").toURI().toString();
    String uriPersonPhone = new File(baseDir, "personPhone").toURI().toString();
    String[] inputModules = new String[] {uriPerson, uriPhone, uriPersonPhone};

    // 2. prepare outputURI
    String outputURI = getCurOutputDir().toURI().toString();

    // 3. prepare CompileAQLParams
    CompileAQLParams params = new CompileAQLParams();
    params.setInputModules(inputModules);
    params.setOutputURI(outputURI);
    params.setTokenizerConfig(getTokenizerConfig());
    // do not set any module path

    // 5. Invoke compilation. No exception expected.
    try {
      CompileAQL.compile(params);
    } catch (Exception e) {
      Assert.fail(String.format("No exceptions are expected, but received '%s' with message: %s",
          e.getClass().getName(), e.getMessage()));
    }

    endTest();
  }

  /**
   * Scenario: An older version of phone.tam exists in modulePath. However, both phone and
   * phoneConsumer modules are compiled together. The compiler should not complain, but instead pick
   * the newly generated phone.tam when resolving dependencies.
   * 
   * @throws Exception
   */
  @Test
  public void latestTAMForCompilationTest() throws Exception {
    startTest();

    File baseDir = new File(TestConstants.AQL_DIR + "/TAMTests/latestTAMForCompilationTest/");

    // 1. prepare inputModules
    String uriPerson = new File(baseDir, "person").toURI().toString();
    String uriPhone = new File(baseDir, "phone").toURI().toString();
    String uriPersonPhone = new File(baseDir, "personPhone").toURI().toString();
    String[] inputModules = new String[] {uriPerson, uriPhone, uriPersonPhone};

    // 2. prepare outputURI
    String outputURI = getCurOutputDir().toURI().toString();

    // 3. prepare modulePath
    File tamDir = new File(TestConstants.TESTDATA_DIR + "/tam");
    File tamSubDir = new File(tamDir, "subdir");
    String modulePath = tamDir.toURI().toString() + ";" + tamSubDir.toURI().toString();

    // 4. prepare CompileAQLParams
    CompileAQLParams params = new CompileAQLParams();
    params.setInputModules(inputModules);
    params.setOutputURI(outputURI);
    params.setModulePath(modulePath);
    params.setTokenizerConfig(getTokenizerConfig());

    // 5. Invoke compilation. No exception expected.
    try {
      CompileAQL.compile(params);
    } catch (Exception e) {
      Assert.fail(String.format("No exceptions are expected, but received '%s' with message: %s",
          e.getClass().getName(), e.getMessage()));
    }

    endTest();
  }

  /**
   * Test to verify that loader merges duplicate output views coming from the different modules to
   * be loaded.
   * 
   * @throws Exception
   */
  @Test
  public void duplicateOutputViewTest() throws Exception {
    startTest();

    compileModule("phone");
    compileModule("duplicateOutputView");
    super.setPrintTups(true);

    runModule(DOCS_FILE, "duplicateOutputView", getCurOutputDir().toURI().toString());

    compareAgainstExpected(true);
    endTest();
  }

  /**
   * @throws Exception
   */
  @Test
  public void copyrightTest() throws Exception {
    startTest();

    compileModule("copyright");
    super.setPrintTups(true);

    File INPUT_FILE = new File(TestConstants.TEST_DOCS_DIR, "/TAMTests/copyright.txt");

    runModule(INPUT_FILE, "copyright", getCurOutputDir().toURI().toString());

    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Test case to capture scenario mentioned in defect - Incomplete dictionary matches are generated
   * for the extractor containing more than one module with the same dictionary file name.
   * 
   * @throws Exception
   */
  @Test
  public void sameDictFileNameAcrossModuleTest() throws Exception {
    startTest();
    setPrintTups(true);

    // Compile modules - both module contains team.dict file with different entries
    compileModule("firstModule");
    compileModule("secondModule");

    // Load both the module
    OperatorGraph og = OperatorGraph.createOG(new String[] {"firstModule", "secondModule"},
        getCurOutputDir().toURI().toString(), null, null);

    // Extract using the loaded graph
    File docFile = new File(
        String.format("%s/%s/%s.del", TestConstants.TEST_DOCS_DIR, "TAMTests", getCurPrefix()));
    annotateAndPrint(docFile, og);

    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Test to verify that the MDA extractors created in v2.0 compile and load fine. <br/>
   * Presently commented out due to defect : Conflict with output alias
   * 
   * @throws Exception
   */
  @Test
  public void mdaTest() throws Exception {
    startTest();
    String modules[] = {"common", "datapower", "extractor_datapower", "extractor_generic",
        "extractor_syslog", "extractor_was", "extractor_webaccess", "syslog", "was", "webaccess"};

    compileModules(modules, null);

    try {
      OperatorGraph.validateOG(modules, getCurOutputDir().toURI().toString(),
          new TokenizerConfig.Standard());
    } catch (ModuleLoadException e) {
      e.printStackTrace();
      // we expect a ModuleLoadException
      return;
    }

    Assert.fail("Control should have not reached here; ModuleLoadException was expected.");
    endTest();
  }

  /**
   * Test to trace a NPE that does not have a ParseException<br>
   * Based on defect
   * 
   * @throws Exception
   */
  @Test
  public void timeTest() throws Exception {
    startTest();

    String dependencies = new File(getCurTestDir(), "Dependencies").toURI().toString();

    // Compile
    CompileAQLParams params = new CompileAQLParams();
    params.setInputModules(new String[] {new File(getCurTestDir(), "Time").toURI().toString(),
        new File(getCurTestDir(), "DocumentDetagger").toURI().toString()});
    params.setModulePath(String.format("%s", dependencies));
    params.setOutputURI(getCurOutputDir().toURI().toString());
    params.setTokenizerConfig(getTokenizerConfig());

    CompileAQL.compile(params);

    endTest();
  }

  /**
   * Test to verify that the loader throw error while loading modules with conflicting output names.
   * Ouput alias name from one module can have conflicts with either output alias or view name from
   * the other module.
   * 
   * @throws Exception
   */
  @Test
  public void conflictingOutputNameTest() throws Exception {
    startTest();

    String modules[] = {"module1", "module2"};

    compileModules(modules, null);

    try {
      OperatorGraph.createOG(modules, getCurOutputDir().toURI().toString(), null,
          new TokenizerConfig.Standard());
    } catch (ModuleLoadException mle) {
      // We expect exception while loading modules
      System.err.println(mle.getMessage());
      return;
    }

    Assert.fail("Control should have not reached here; ModuleLoadException was expected");
    endTest();
  }

  /**
   * Test to verify that a module compiled with v2.0 runs fine with v2.1 runtime
   * 
   * @throws Exception
   */
  @Test
  public void v20CompiledModuleMetadataDependsOnAttributeTest() throws Exception {
    startTest();
    super.setPrintTups(true);

    // pick a few v2.0 compiled modules
    File tamDir = new File(TestConstants.TESTDATA_DIR
        + "/tam/v20CompiledModules/moduleMetadataWithDependsOnAttribute");
    File docsFile = new File(TestConstants.ENRON_1_DUMP);

    ExternalTypeInfo eti = ExternalTypeInfoFactory.createInstance();
    eti.addTable("exportTable.externalTable",
        new File(getModuleDirectory("exportTable", "importTableTest"), "ExtTab1.csv").toURI()
            .toString());

    // run the v2.0 compiled modules against the current runtime
    runModules(docsFile, new String[] {"exportTable", "importTableTest"}, tamDir.toURI().toString(),
        eti);

    compareAgainstExpected(true);
    endTest();
  }

  /**
   * Test to verify that proper error message with sufficient details (such as field name, module
   * name etc) is thrown when there is mismatch of docschema among the modules loaded.
   * 
   * @throws Exception
   */
  @Test
  public void docSchemaMismatchTest1() throws Exception {
    startTest();
    String[] moduleNames = {"module1", "module2", "module3"};
    File docsFile = new File(TestConstants.ENRON_1_DUMP);

    try {
      compileAndRunModules(moduleNames, docsFile, null);
      Assert.fail("Expected ModuleLoadException, but received none");
    } catch (ModuleLoadException e) {
      System.err.println(e.getMessage());
      String expectedErrorMessage = "Error in loading module(s): [module1, module2, module3].\n" //
          + "Detailed message: The type of field 'text' of view Document is incompatible between the modules that are loaded. The type of the specified field in module [module1] is 'Text', whereas its type in module(s) module3 is 'Integer'. Ensure that the field types match.\n"
          + "The type of field 'text' of view Document is incompatible between the modules that are loaded. The type of the specified field in module [module2] is 'Text', whereas its type in module(s) module3 is 'Integer'. Ensure that the field types match.\n";
      assertEquals(expectedErrorMessage, e.getMessage());
    } catch (Exception e) {
      Assert.fail(String.format("Expected ModuleLoadException, but received %s", e.getMessage()));
    }
    endTest();
  }

  /**
   * Detect a legacy TAM file with no cost record defined for views. Early v2.0 TAMs may end up in
   * this state. Validates defect - uncomment when this defect is fixed.
   * 
   * @throws Exception
   */
  @Test
  public void noCostRecordTest() throws Exception {
    startTest();
    super.setPrintTups(true);

    // Compute the location of the current test case's top-level AQL file.
    File aqlModuleDir = getCurTestDir();

    String compiledModuleURI;

    // URI where compiled modules should be dumped -- set to regression/actual
    compiledModuleURI = new File(String.format("%s", getCurOutputDir())).toURI().toString();

    // set up the directories containing the input modules we want to compile
    ArrayList<String> moduleDirectories = new ArrayList<String>();

    String moduleDirectoryName = new File(String.format("%s/%s", aqlModuleDir, "Time"))
        .getCanonicalFile().toURI().toString();
    moduleDirectories.add(moduleDirectoryName);

    // Compile
    CompileAQLParams params = new CompileAQLParams();
    params.setInputModules(moduleDirectories.toArray(new String[1]));
    params.setOutputURI(compiledModuleURI);
    params.setModulePath(moduleDirectoryName);
    params.setTokenizerConfig(getTokenizerConfig());

    // Compile the modules and write the compiled module(.tam) files in the location specified by
    // setOutputURI() method
    CompileAQL.compile(params);

    endTest();
  }

  /**
   * Verifies if an unused view is executed. Uses an in-built scalar function
   * FunctionInvocationTracker to trace the execution.
   */
  @Test
  public void unusedViewTest() throws Exception {
    startTest();
    compileAndRunModules(new String[] {"module1", "module2"},
        new File(TestConstants.TEST_DOCS_DIR, "TAMTests/input.zip"), null);

    int count = FunctionInvocationTracker.getInvocationCount("module1.UnusedView");
    assertTrue(String.format("Expected 0, but received %d", count), count == 0);
    endTest();
  }

  /**
   * Test verifies that the stitched plan does *not* contains any of the view not reachable thru any
   * of the output views.<br>
   * Expected result: Stitched plan will not contain 'module1.UnusedView' view.
   */
  @Test
  public void stitchedPlanUnusedViewTest() throws Exception {
    startTest();

    String[] moduleNames = {"module1", "module2"};
    genericStitchPlanTest(moduleNames);

    endTest();
  }

  /**
   * Test to verify that the merged SDM nodes in stitched plan should *not* contain dictionaries,
   * refered in the view, which are reachable thru any of the output views. <br>
   * Expected result: Dictionaries referred in the view 'module1.Metric' should not be part of the
   * merged SDM node.
   * 
   * @throws Exception
   */
  @Test
  public void unusedSDMTest() throws Exception {
    startTest();

    String[] moduleNames = {"module1", "module2"};
    genericStitchPlanTest(moduleNames);

    endTest();
  }

  /**
   * Test to verify that the merged SRM nodes in stitched plan should *not* contain regexParams from
   * the view, which are not reachable thru any of the output views. <br>
   * Expected result: Only regexes from views 'module1.ValidPhone' and 'module2.NotA4DigitExtension'
   * should not be part of the merged SRM node.
   * 
   * @throws Exception
   */
  @Test
  public void unusedSRMTest() throws Exception {
    startTest();

    String[] moduleNames = {"module1", "module2"};
    genericStitchPlanTest(moduleNames);

    endTest();
  }

  /**
   * Test to verify that stitched plan does *not* contains detag views not reachable thru any of the
   * output views.<br>
   * Expected result: Stitched plan should not contain 'module1.BoldTag',
   * 'module1.DetaggedExpensiveView', 'module2.Img', 'module2.Link' and 'module2.Script'.
   * 
   * @throws Exception
   */
  @Test
  public void unusedDetagViewTest() throws Exception {
    startTest();

    String[] moduleNames = {"module1", "module2"};
    genericStitchPlanTest(moduleNames);

    endTest();
  }

  /**
   * Test to verify that the disabled views(not passed to OperatorGraph.execute() method) are not
   * evaluated, even if they are marked output. Both 'module1.ExpensiveView' and 'module2.CheapView'
   * are output, but only 'module2.CheapView' is enabled.
   * 
   * @throws Exception
   */
  @Test
  public void disabledViewTest() throws Exception {
    startTest();

    String[] moduleNames = new String[] {"module1", "module2"};

    OperatorGraph operatorGraph = compileAndLoadModules(moduleNames, null);

    DocReader docs = new DocReader(new File(TestConstants.TEST_DOCS_DIR, "TAMTests/input.zip"));
    while (docs.hasNext()) {
      // enable only 'module2.CheapView' view
      operatorGraph.execute(docs.next(), new String[] {"module2.CheapView"}, null);
    }

    int expensiveViewCount = FunctionInvocationTracker.getInvocationCount("module1.ExpensiveView");
    assertTrue(String.format("Expected zero, but received %d", expensiveViewCount),
        expensiveViewCount == 0);

    int cheapViewCount = FunctionInvocationTracker.getInvocationCount("module1.CheapView");
    assertTrue(String.format("Expected positive number, but received %d", cheapViewCount),
        cheapViewCount != 0);

    endTest();
  }

  /**
   * Test to verify defect : Compile error with NULL message when using an imported external view
   * 
   * @throws Exception
   */
  @Test
  public void externalViewExportTest() throws Exception {
    startTest();
    setPrintTups(true);

    String[] moduleNames = {"module1", "module2"};
    compileAndRunModules(moduleNames, DOCS_FILE, null);

    endTest();
  }

  /**
   * Test to verify defect : Id and fix regression in Contains/Overlaps
   * 
   * @throws Exception
   */
  @Test
  public void overlapsTest() throws Exception {
    startTest();
    setPrintTups(true);

    final File INPUT_FILE =
        new File(TestConstants.TEST_DOCS_DIR, "/TAMTests/overlapsTest/dex10326.html");

    runModule(INPUT_FILE, "driver", getPreCompiledModuleDir().toString());
    endTest();
  }

  /*
   * ADD NEW TEST CASES ABOVE
   */

  /*
   * UTILITY METHODS BELOW
   */

  /**
   * Helper method to compile and load given modules; dump the stitched plan and compare with the
   * expected stitched plan.
   * 
   * @param moduleNames names of the module to be compiled. This mehod expects the source of the
   *        given modules is found in the testcase AQL directory.
   * @throws Exception
   */
  private void genericStitchPlanTest(String[] moduleNames) throws Exception {
    String tamDir = getCurOutputDir().getCanonicalFile().toURI().toString();

    // Compile modules
    compileModules(moduleNames, null);

    // Load both the module
    OperatorGraph og = OperatorGraph.createOG(moduleNames, tamDir, null, null);

    PrintWriter out = new PrintWriter(new File(getCurOutputDir(), "stitchedplan.dump"));
    ((OperatorGraphImpl) og).dump(out, 0);
    out.close();

    // Do *not* truncate as we want to compare the complete aog.
    compareAgainstExpected(false);

  }

  /**
   * Returns the test module source directory.
   * 
   * @param moduleName module name, for which handle to source directory is required.
   * @return
   */
  private File getModuleDirectory(String moduleName, String testName) {
    String className = getClass().getSimpleName();
    File moduleDir = new File(
        String.format("%s/%s/%s/%s", TestConstants.AQL_DIR, className, testName, moduleName))
            .getAbsoluteFile();

    return moduleDir;
  }

}
