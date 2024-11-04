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
package com.ibm.wcs.annotationservice.test;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import com.ibm.avatar.algebra.datamodel.TupleList;
import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.algebra.util.tokenize.TokenizerConfig;
import com.ibm.avatar.logging.Log;
import com.ibm.wcs.annotationservice.AnnotationService;
import com.ibm.wcs.annotationservice.AnnotationServiceConstants;
import com.ibm.wcs.annotationservice.AnnotatorBundle;
import com.ibm.wcs.annotationservice.ExecuteParams;
import com.ibm.wcs.annotationservice.exceptions.AnnotationServiceException;
import com.ibm.wcs.annotationservice.models.json.AnnotatorBundleConfig;
import com.ibm.wcs.annotationservice.test.util.AnnotationServiceTestHarness;
import com.ibm.wcs.annotationservice.test.util.TestConstants;

/**
 * Various test cases for the AnnotationService class using SystemT annotators.
 * 
 */
public class AnnotationServiceSystemTTest extends AnnotationServiceTestHarness {

  /** Basic document used by multiple tests in this class */
  private final File DOCS_FILE = new File(TestConstants.DUMPS_DIR, "simpleDoc.json");

  static ObjectMapper mapper = new ObjectMapper();

  /**
   * Main method for invoking one test at a time.
   * 
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    AnnotationServiceSystemTTest t = new AnnotationServiceSystemTTest();
    t.setUp();

    long startMS = System.currentTimeMillis();

    t.serializeAnnotatorInfoTest();

    long endMS = System.currentTimeMillis();

    t.tearDown();

    double elapsedSec = (double) (endMS - startMS) / 1000.0;
    System.err.printf("Test took %1.3f sec.\n", elapsedSec);
  }

  @Before
  public void setUp() {

    // By default use the standard tokenizer to compile any AQL code
    setTokenizerConfig(new TokenizerConfig.Standard());

    try {
      ExecuteParams execParams = new ExecuteParams(AnnotationServiceConstants.NO_TIMEOUT, null,
          null, AnnotatorBundleConfig.ContentType.PLAIN.getValue(), null);
      setExecuteParams(execParams);
    } catch (AnnotationServiceException e) {
      throw new RuntimeException(e);
    }
  }

  @After
  public void tearDown() {

  }

  /**
   * Basic test of running the Annotation Service with a single document and getting back results.
   * 
   * @throws Exception
   */
  @Test
  public void basicTest() throws Exception {
    startTest();

    final File CFG_FILE = new File(TestConstants.TEST_CONFIGS_DIR, "common/systemTPerson_cfg.json");

    genericAnnotationServiceTest(DOCS_FILE, CFG_FILE);

    compareAgainstExpected(true);
  }

  /**
   * Ensure a SystemT extractor ignores all document fields except those required by the operator
   * graph.
   * 
   * @throws Exception
   */
  @Test
  public void systemTDocPassThroughTest() throws Exception {
    startTest();

    final File CFG_FILE =
        new File(TestConstants.TEST_CONFIGS_DIR, "AnnotatorBundleTest/simpleSystemTTest01.json");
    setExecuteParams(new ExecuteParams(AnnotationServiceConstants.NO_TIMEOUT, null, null,
        AnnotatorBundleConfig.ContentType.HTML.getValue(), null));

    genericAnnotationServiceTest(null, CFG_FILE);

    compareAgainstExpected(true);
  }

  /**
   * Ensure a SystemT extractor throws an exception when the input document record is missing one of
   * the fields of the document schema of the extractor.
   * 
   * @throws Exception
   */
  @Test
  public void systemTMissingDocFieldTest() throws Exception {
    startTest();

    final File CFG_FILE = new File(TestConstants.TEST_CONFIGS_DIR, "common/systemTPerson_cfg.json");

    genericAnnotationServiceTest(null, CFG_FILE);

    compareAgainstExpected(true);
  }

  /**
   * Ensure a SystemT extractor whose Document schema has text field accepts input records that have
   * a content field (but no text field) .
   * 
   * @throws Exception
   */
  @Test
  public void systemTContentDocFieldTest() throws Exception {
    startTest();

    final File CFG_FILE =
        new File(TestConstants.TEST_CONFIGS_DIR, "AnnotatorBundleTest/simpleSystemTTest01.json");

    setExecuteParams(new ExecuteParams(AnnotationServiceConstants.NO_TIMEOUT, null, null,
        AnnotatorBundleConfig.ContentType.HTML.getValue(), null));

    genericAnnotationServiceTest(null, CFG_FILE);

    compareAgainstExpected(true);
  }

  /**
   * Ensure a SystemT extractor whose Document schema has text field (but no content field) throws
   * an exception on input records that have both content and text field.
   * 
   * @throws Exception
   */
  @Test
  public void systemTContentTextDocFieldTest() throws Exception {
    startTest();

    final File CFG_FILE = new File(TestConstants.TEST_CONFIGS_DIR, "common/systemTPerson_cfg.json");

    genericAnnotationServiceTest(null, CFG_FILE);

    compareAgainstExpected(true);
  }

  /**
   * Ensure a SystemT extractor whose Document schema has text and content fields results in an
   * exception.
   * 
   * @throws Exception
   */
  @Test
  public void systemTBothContentAndTextFieldsTest() throws Exception {
    startTest();

    // Compile AQL source code for the test module
    compileAQL(null, null, null);

    // Run the test with the compiled extractor
    genericAnnotationServiceTest(DOCS_FILE, null);

    compareAgainstExpected(true);
  }

  /**
   * Test for span serialization (including the optional docref field) for SystemT extractors
   * 
   * @throws Exception
   */
  @Test
  public void systemTSerializeSpanTest() throws Exception {
    startTest();

    // Compile AQL source code for the test module
    compileAQL(null, null, null);

    // Run the test with the compiled extractor
    genericAnnotationServiceTest(null, null);

    compareAgainstExpected(true);
  }

  /**
   * Test for span serialization when specify "serializeSpan": "locationAndText" (including the
   * optional docref field) for SystemT extractors
   *
   * @throws Exception
   */
  @Test
  public void systemTSerializeSpanLocationAndTextTest() throws Exception {
    startTest();

    // Compile AQL source code for the test module
    compileAQL(null, null, null);

    // Run the test with the compiled extractor
    genericAnnotationServiceTest(null, null);

    compareAgainstExpected(true);
  }


  /**
   * Test for span serialization when specify "serializeSpan": "locationAndText" with detagging for
   * SystemT extractors
   *
   * @throws Exception
   */
  @Test
  public void systemTSerializeSpanLocationAndTextDetagTest() throws Exception {
    startTest();

    // Compile AQL source code for the test module
    compileAQL(null, null, null);

    // Run the test with the compiled extractor
    genericAnnotationServiceTest(null, null);

    compareAgainstExpected(true);
  }

  /**
   * Test for span serialization when specify "serializeSpan": "locationAndText" for AQL Web Tooling
   * artifact
   *
   * @throws Exception
   */
  @Test
  public void systemTSerializeSpanLocationAndTextAwtTest() throws Exception {
    startTest();

    // Compile AQL source code for the test module
    // compileAQL (null, null, null);

    // Run the test with the compiled extract
    genericAnnotationServiceTest(null, null);

    compareAgainstExpected(true);
  }

  /**
   * Test for span serialization when specify "serializeSpan": "locationAndText" for AQL Web Tooling
   * artifact with enable detagging
   *
   * @throws Exception
   */
  @Test
  public void systemTSerializeSpanLocationAndTextAwtDetagTest() throws Exception {
    startTest();

    // Compile AQL source code for the test module
    // compileAQL (null, null, null);

    // Run the test with the compiled extract
    genericAnnotationServiceTest(null, null);

    compareAgainstExpected(true);
  }

  /**
   * Test for span serialization when specify "serializeSpan": "locationAndText" (including the
   * optional docref field) for SystemT extractors
   *
   * @throws Exception
   */
  @Test(expected = ValueInstantiationException.class)
  public void systemTSerializeSpanWithUnknownTypeTest() throws Exception {
    startTest();

    // Compile AQL source code for the test module
    // compileAQL (null, null, null);

    // Run the test with the compiled extract
    genericAnnotationServiceTest(null, null);

    // compareAgainstExpected (true);
  }


  /**
   * Ensure that a SystemT extractor throws an exception when an output span is over a text object
   * that does not exist in the input document.
   * 
   * @throws Exception
   */
  @Test
  public void serializeSpanWithUnknownTextTest() throws Exception {
    startTest();

    final File DOCS_FILE = new File(TestConstants.DUMPS_DIR, "personDoc.json");
    final File CFG_FILE = new File(TestConstants.TEST_CONFIGS_DIR, "common/systemTPerson_cfg.json");

    genericAnnotatorBundleTest(DOCS_FILE, CFG_FILE);

    compareAgainstExpected(true);
  }

  /**
   * Ensure a extractor throws an exception when the input document record is missing the specified
   * language field.
   * 
   * @throws Exception
   */
  @Test
  public void missingLanguageFieldTest() throws Exception {
    startTest();

    setExecuteParams(
        new ExecuteParams(AnnotationServiceConstants.NO_TIMEOUT, null, "language", null, null));

    final File CFG_FILE = new File(TestConstants.TEST_CONFIGS_DIR, "common/systemTPerson_cfg.json");

    genericAnnotationServiceTest(null, CFG_FILE);

    compareAgainstExpected(true);
  }

  /**
   * Ensure a extractor throws an exception when the input document record is missing the specified
   * media type field.
   * 
   * @throws Exception
   */
  @Test
  public void missingMediaTypeFieldTest() throws Exception {
    startTest();

    setExecuteParams(
        new ExecuteParams(AnnotationServiceConstants.NO_TIMEOUT, null, "mediaType", null, null));

    final File CFG_FILE = new File(TestConstants.TEST_CONFIGS_DIR, "common/systemTPerson_cfg.json");

    genericAnnotationServiceTest(null, CFG_FILE);

    compareAgainstExpected(true);
  }

  /**
   * Test for serialization of null fields and values.
   * 
   * @throws Exception
   */
  @Test
  public void nullTest() throws Exception {
    startTest();

    // Compile AQL source code for the test module
    compileAQL(null, null, null);

    // Run the test with the compiled extractor
    genericAnnotationServiceTest(null, null);

    compareAgainstExpected(true);
  }

  /**
   * Ensure a extractor throws an exception when the annotation bundle config does not contain the
   * required location field.
   * 
   * @throws Exception
   */
  // @Test
  // Test commented out because the error is no longer serialized into instrumentation info because
  // the error is thrown by Jackson
  public void missingLocationTest() throws Exception {
    startTest();

    genericAnnotationServiceTest(DOCS_FILE, null);

    compareAgainstExpected(true);
  }

  /**
   * Test for serialization of annotator info. field.
   * 
   * @throws Exception
   */
  @Test
  public void serializeAnnotatorInfoTest() throws Exception {
    startTest();

    // Compile AQL source code for the test module
    compileAQL(null, null, null);
    genericAnnotationServiceTest(DOCS_FILE, null);

    compareAgainstExpected(true);
  }

  /**
   * Test for external dictionaries and tables.
   * 
   * @throws Exception
   */
  @Test
  public void externalDictsTablesTest() throws Exception {
    startTest();

    // Compile AQL source code for the test module
    compileAQL(null, null, null);
    genericAnnotationServiceTest(DOCS_FILE, null);

    compareAgainstExpected(true);
  }

  /**
   * Test for the document classification bundle FIXME: This test is temporary and needs to be
   * replaced once the actual bundle code is delivered to WCS-Enrichment-Core repo.
   * 
   * @throws Exception
   */
  @Test
  public void docClassificationTrainingTest() throws Exception {
    startTest();

    genericAnnotationServiceTest(null, null);

    compareAgainstExpected(true);
  }

  /**
   * Verify caching of annotator bundles works correctly
   * 
   * @param docsFile
   * @param annotatorCfgFile
   * @throws Exception
   */
  @Test
  public void cacheTest() throws Exception {
    final boolean debug = false;
    final File DOCS_FILE = new File(TestConstants.DUMPS_DIR, "personDoc.json");
    final File CFG_FILE = new File(TestConstants.TEST_CONFIGS_DIR, "common/systemTPerson_cfg.json");

    // Read the document and config file as JSON Records
    JsonNode jsonDoc = mapper.readTree(DOCS_FILE);

    AnnotatorBundleConfig jsonCfg = mapper.readValue(CFG_FILE, AnnotatorBundleConfig.class);

    // Instantiate the Annotation Service
    AnnotationService service = new AnnotationService();

    // Invoke the service once
    JsonNode res = service.invoke(jsonCfg, jsonDoc, getExecParams());
    if (debug)
      Log.debug("First invocation result:\n %s", res);
    assertTrue(String.format("Expected 1 annotator bundle, but got %d instead",
        service.getNumAnnotatorBundles()), 1 == service.getNumAnnotatorBundles());

    // Read the same config in a new object and invoke the service a second time
    AnnotatorBundleConfig jsonCfg2 = mapper.readValue(CFG_FILE, AnnotatorBundleConfig.class);
    res = service.invoke(jsonCfg2, jsonDoc, getExecParams());
    if (debug)
      Log.debug("Second invocation result:\n %s", res);
    assertTrue(String.format("Expected 1 annotator bundle, but got %d instead",
        service.getNumAnnotatorBundles()), 1 == service.getNumAnnotatorBundles());
  }

  /**
   * Test for raw annotation output record
   *
   * @throws Exception
   */
  @Test
  public void rawAnnotationRecordTest() throws Exception {
    final boolean debug = false;
    final File DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR,
        "AnnotationServiceSystemTTest/rawAnnotationRecordTest.json");
    final File CFG_FILE = new File(TestConstants.TEST_CONFIGS_DIR,
        "AnnotationServiceSystemTTest/rawAnnotationRecordTest.json");

    // Read the document and config file as JSON Records
    JsonNode jsonDoc = mapper.readTree(DOCS_FILE);

    AnnotatorBundleConfig jsonCfg = mapper.readValue(CFG_FILE, AnnotatorBundleConfig.class);

    // Instantiate the Annotation Service
    AnnotationService service = new AnnotationService();

    // Invoke the service once
    AnnotatorBundle.RawAnnotationOutputRecord res =
        service.invokeRaw(jsonCfg, jsonDoc, getExecParams());

    Map<String, TupleList> annotations = res.getAnnotations();
    assertEquals(4, annotations.get("ProgrammingLanguageName").size());
    assertEquals(4, annotations.get("ProgrammingLanguageWithVersion").size());

    JsonNode instrumentationInfo = res.getInstrumentationInfo();
    assertTrue(instrumentationInfo.get("success").asBoolean());
  }

  /**
   * Test for sourceModules element to support CompileAQL
   *
   * @throws Exception
   */
  @Test
  public void sourceModulesTest() throws Exception {
    startTest();

    final File DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR,
        "AnnotationServiceSystemTTest/sourceModulesTest.json");
    final File CFG_FILE = new File(TestConstants.TEST_CONFIGS_DIR,
        "AnnotationServiceSystemTTest/sourceModulesTest.json");

    genericAnnotatorBundleTest(DOCS_FILE, CFG_FILE);

    compareAgainstExpected(true);
  }

  /**
   * Test for sourceModules element (an empty list) to support CompileAQL
   *
   * @throws Exception
   */
  @Test
  public void sourceModulesEmptyTest() throws Exception {
    startTest();

    final File DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR,
        "AnnotationServiceSystemTTest/sourceModulesEmptyTest.json");
    final File CFG_FILE = new File(TestConstants.TEST_CONFIGS_DIR,
        "AnnotationServiceSystemTTest/sourceModulesEmptyTest.json");

    genericAnnotatorBundleTest(DOCS_FILE, CFG_FILE);

    compareAgainstExpected(true);
  }

  /**
   * Test for sourceModules element (without modulePath element) to support CompileAQL
   *
   * @throws Exception
   */
  @Test
  public void sourceModulesOnlyTest() throws Exception {
    startTest();

    final File DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR,
        "AnnotationServiceSystemTTest/sourceModulesTest.json");
    final File CFG_FILE = new File(TestConstants.TEST_CONFIGS_DIR,
        "AnnotationServiceSystemTTest/sourceModulesOnlyTest.json");

    genericAnnotatorBundleTest(DOCS_FILE, CFG_FILE);

    compareAgainstExpected(true);
  }

  /**
   * Test for invalid modulePath
   *
   */
  @Test
  public void invalidModulePathErrorTest() {
    startTest();

    final File DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR,
        "AnnotationServiceSystemTTest/sourceModulesTest.json");
    final File CFG_FILE = new File(TestConstants.TEST_CONFIGS_DIR,
        "AnnotationServiceSystemTTest/invalidModulePathErrorTest.json");

    try {
      genericAnnotatorBundleTest(DOCS_FILE, CFG_FILE);

      fail();
    } catch (Exception e) {
      String expectedMessage = "Module path ./invalid module path doesn't exist in filesystem.";
      assertThat(e.getCause().getMessage(), equalTo(expectedMessage));
    }
  }

  /**
   * Test for empty modulePath/sourceModules
   *
   */
  @Test
  public void emptyModulesErrorTest() {
    startTest();

    final File DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR,
        "AnnotationServiceSystemTTest/sourceModulesTest.json");
    final File CFG_FILE = new File(TestConstants.TEST_CONFIGS_DIR,
        "AnnotationServiceSystemTTest/emptyModulesErrorTest.json");

    try {
      genericAnnotatorBundleTest(DOCS_FILE, CFG_FILE);

      fail();
    } catch (Exception e) {
      String expectedMessage = String.format(
          "Values of field '%s' and '%s' in SystemT Annotator Module Configuration are empty; provide at least one entry in the module path or the source modules",
          AnnotationServiceConstants.MODULE_PATH_FIELD_NAME,
          AnnotationServiceConstants.SOURCE_MODULES_FIELD_NAME);
      assertThat(e.getCause().getMessage(), equalTo(expectedMessage));
    }
  }

  /**
   * Test for support for input types.
   * 
   * @throws Exception
   */
  @Test
  public void inputTypesTest() throws Exception {
    startTest();

    // Compile AQL source code for the test module
    compileAQL(null, null, null);
    genericAnnotationServiceTest(null, null);

    compareAgainstExpected(true);
  }

  /**
   * Test for support of null input types.
   * 
   * @throws Exception
   */
  @Test
  public void inputTypesNullTest() throws Exception {
    startTest();

    // Compile AQL source code for the test module
    compileAQL(null, null, null);
    genericAnnotationServiceTest(null, null);

    compareAgainstExpected(true);
  }

  /**
   * Test for save() method which saves the model into the specified path as a zip file.
   *
   * @throws Exception
   */
  @Test
  public void saveModelTest() throws Exception {
    startTest();

    AnnotatorBundleConfig cfg =
        mapper.readValue(
            Files.readAllBytes(Paths.get(TestConstants.TEST_CONFIGS_DIR,
                "AnnotationServiceSystemTTest", "saveModelTest.json")),
            AnnotatorBundleConfig.class);
    AnnotationService as = new AnnotationService();
    as.init(cfg);
    File tempDir = null;
    try {
      tempDir = FileUtils.makeTempDir("saveModelTest");
      Path modelZipFile = Paths.get(tempDir.toString(), "model.zip");
      as.save(cfg, modelZipFile.toString());

      assert Files.exists(modelZipFile);

      Path expectedPath =
          Paths.get("regression", "expected", "AnnotationServiceSystemTTest", "saveModelTest")
              .toAbsolutePath();
      Path actualPath =
          Paths.get("regression", "actual", "AnnotationServiceSystemTTest", "saveModelTest")
              .toAbsolutePath();

      unzip(modelZipFile, actualPath);

      // assert if the contents of the zip file is as expected
      Files.walk(expectedPath).forEach(p -> {
        Path relative = expectedPath.relativize(p);
        try {
          assert Files.isDirectory(p)
              || p.toString().endsWith(".tam") && Files.exists(actualPath.resolve(relative))
              || Arrays.equals(Files.readAllBytes(expectedPath.resolve(relative)),
                  Files.readAllBytes(actualPath.resolve(relative)));
        } catch (Exception e) {
          fail();
        }
      });
    } finally {
      if (tempDir != null) {
        // Delete the temporary directory if exists.
        FileUtils.deleteDirectory(tempDir);
      }
    }

  }

  /**
   * Test for save() method with compileDictionaries flag which saves the model into the specified
   * path as a zip file.
   *
   * @throws Exception
   */
  @Test
  public void saveModelWithCompiledDictionariesTest() throws Exception {
    startTest();

    AnnotatorBundleConfig cfg =
        mapper.readValue(
            Files.readAllBytes(Paths.get(TestConstants.TEST_CONFIGS_DIR,
                "AnnotationServiceSystemTTest", "saveModelTest.json")),
            AnnotatorBundleConfig.class);
    AnnotationService as = new AnnotationService();
    as.init(cfg);
    File tempDir = null;
    try {
      tempDir = FileUtils.makeTempDir("saveModelTest");
      Path modelZipFile = Paths.get(tempDir.toString(), "model.zip");
      as.save(cfg, modelZipFile.toString(), true);

      assert Files.exists(modelZipFile);

      Path expectedPath = Paths.get("regression", "expected", "AnnotationServiceSystemTTest",
          "saveModelWithCompiledDictionariesTest").toAbsolutePath();
      Path actualPath = Paths.get("regression", "actual", "AnnotationServiceSystemTTest",
          "saveModelWithCompiledDictionariesTest").toAbsolutePath();

      unzip(modelZipFile, actualPath);

      // assert if the contents of the zip file is as expected
      Files.walk(expectedPath).forEach(p -> {
        Path relative = expectedPath.relativize(p);
        try {
          assert Files.isDirectory(p)
              || p.toString().endsWith(".tam") && Files.exists(actualPath.resolve(relative))
              || Arrays.equals(Files.readAllBytes(expectedPath.resolve(relative)),
                  Files.readAllBytes(actualPath.resolve(relative)));
        } catch (Exception e) {
          fail();
        }
      });
    } finally {
      if (tempDir != null) {
        // Delete the temporary directory if exists.
        FileUtils.deleteDirectory(tempDir);
      }
    }

  }

  /**
   * Test for save() method which saves the model into the specified path as a zip file even if the
   * location path has file: header.
   *
   * @throws Exception
   */
  @Test
  public void locationHeaderTest() throws Exception {
    startTest();

    AnnotatorBundleConfig cfg =
        mapper.readValue(
            Files.readAllBytes(Paths.get(TestConstants.TEST_CONFIGS_DIR,
                "AnnotationServiceSystemTTest", "locationHeaderTest.json")),
            AnnotatorBundleConfig.class);
    cfg.setLocation(new File(cfg.getLocation()).toURI().toString());
    AnnotationService as = new AnnotationService();
    as.init(cfg);

    File tempDir = null;
    try {
      tempDir = FileUtils.makeTempDir("locationHeaderTest");
      Path modelZipFile = Paths.get(tempDir.toString(), "model.zip");
      as.save(cfg, modelZipFile.toString());

      assert Files.exists(modelZipFile);

      Path expectedPath =
          Paths.get("regression", "expected", "AnnotationServiceSystemTTest", "locationHeaderTest")
              .toAbsolutePath();
      Path actualPath =
          Paths.get("regression", "actual", "AnnotationServiceSystemTTest", "locationHeaderTest")
              .toAbsolutePath();

      unzip(modelZipFile, actualPath);

      // assert if the contents of the zip file is as expected
      Files.walk(expectedPath).forEach(p -> {
        Path relative = expectedPath.relativize(p);
        try {
          assert Files.isDirectory(p)
              || p.toString().endsWith(".tam") && Files.exists(actualPath.resolve(relative))
              || Arrays.equals(Files.readAllBytes(expectedPath.resolve(relative)),
                  Files.readAllBytes(actualPath.resolve(relative)));
        } catch (Exception e) {
          fail();
        }
      });
    } finally {
      if (tempDir != null) {
        // Delete the temporary directory if exists.
        FileUtils.deleteDirectory(tempDir);
      }
    }

  }

  /**
   * Test for support AQL Web Tooling artifact with detag option
   *
   * @throws Exception
   */
  @Test
  public void awtSequenceDetagTest() throws Exception {
    startTest();

    genericAnnotationServiceTest(null, null);

    compareAgainstExpected(true);
  }

  /*
   * PRIVATE METHODS GO HERE
   */

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private void unzip(Path zipFilePath, Path outputDirPath) throws IOException {
    Files.createDirectories(outputDirPath);
    try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFilePath))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (entry.isDirectory())
          continue;

        Path dest = Paths.get(outputDirPath.toString(), entry.getName());
        Files.createDirectories(dest.getParent());
        Files.createFile(dest);
        byte[] data = new byte[10];
        int count = 0;
        while ((count = zis.read(data)) != -1) {
          Files.write(dest, Arrays.copyOfRange(data, 0, count), StandardOpenOption.APPEND);
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
