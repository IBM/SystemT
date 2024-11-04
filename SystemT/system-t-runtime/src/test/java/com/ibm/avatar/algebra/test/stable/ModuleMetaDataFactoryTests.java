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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Assert;
import org.junit.Test;

import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.exceptions.CircularDependencyException;
import com.ibm.avatar.api.exceptions.IncompatibleTokenizerConfigException;
import com.ibm.avatar.api.exceptions.ModuleLoadException;
import com.ibm.avatar.api.exceptions.ModuleNotFoundException;
import com.ibm.avatar.api.tam.ModuleMetadata;
import com.ibm.avatar.api.tam.ModuleMetadataFactory;
import com.ibm.avatar.api.tam.MultiModuleMetadata;
import com.ibm.avatar.aql.tam.MultiModuleMetadataImpl;

/**
 * Tests to validate loading of metadata from ModuleMetadataFactory
 * 
 */
public class ModuleMetaDataFactoryTests extends RuntimeTestHarness {
  File tamDir = new File(TestConstants.TESTDATA_DIR + "/tam/" + getClass().getSimpleName());

  /**
   * Verifies that the module metadata of a valid TAM file can be loaded successfully
   * 
   * @throws Exception
   */
  @Test
  public void loadMetadataFromModulePath() throws Exception {
    startTest();
    compileModule("phone");

    try {

      String modulePath = getCurOutputDir().getCanonicalFile().toURI().toString();

      ModuleMetadata metadata = ModuleMetadataFactory.readMetaData("phone", modulePath);
      assertNotNull("Metadata of module 'phone' is not expected to be null", metadata);
    } catch (Exception e) {
      Assert.fail(e.getMessage());
    } finally {
      endTest();
    }
  }

  /**
   * Verifies that ModuleNotFoundException is thrown when we attempt to load metadata of an invalid
   * module.
   * 
   * @throws Exception
   */
  @Test
  public void loadMetadataInvalidModule() throws Exception {
    startTest("loadMetadataInvalidModule");
    try {
      ModuleMetadataFactory.readMetaData("InvalidModuleName");
      Assert.fail("Expected a ModuleNotFoundException, but none thrown");
    } catch (Exception e) {
      assertTrue(String.format("Expected ModuleNotFoundException, but received %s with message: %s",
          e.getClass().getName(), e.getMessage()), (e instanceof ModuleNotFoundException));
    }
    endTest();
  }

  /**
   * Verifies that the following API works fine: ModuleMetadataFactory.readAllMetaData (String
   * modulePath)
   * 
   * @throws Exception
   */
  @Test
  public void readAllMetadataFromModulePathTest() throws Exception {
    startTest();

    String modulePathDirURI = new File(getCurTestDir(), "tamDir").toURI().toString();
    String modulePathJarURI = new File(getCurTestDir(), "jarDir/phone.jar").toURI().toString();
    String modulePathZipURI = new File(getCurTestDir(), "zipDir/hello.zip").toURI().toString()
        + Constants.MODULEPATH_SEP_CHAR
        + new File(getCurTestDir(), "zipDir/phone.zip").toURI().toString();
    String modulePathMixedURI =
        new File(getCurTestDir(), "mixedDir").toURI().toString() + Constants.MODULEPATH_SEP_CHAR
            + new File(getCurTestDir(), "mixedDir/phone.jar").toURI().toString()
            + Constants.MODULEPATH_SEP_CHAR
            + new File(getCurTestDir(), "mixedDir/phone.zip").toURI().toString()
            + Constants.MODULEPATH_SEP_CHAR;

    genericReadAllMetadataTest(modulePathDirURI, 3);
    genericReadAllMetadataTest(modulePathJarURI, 1);
    genericReadAllMetadataTest(modulePathZipURI, 4);
    genericReadAllMetadataTest(modulePathMixedURI, 4);

    endTest();
  }

  /**
   * Helper class that tests ModuleMetadataFactory.readAllMetaData(modulePath) API
   * 
   * @param modulePathURI URI from where modules are to be read
   * @param expectedCount expected number of metadata
   * @throws Exception
   */
  private void genericReadAllMetadataTest(String modulePathURI, int expectedCount)
      throws Exception {

    ModuleMetadata[] metadata = ModuleMetadataFactory.readAllMetaData(modulePathURI);
    assertTrue(String.format("Expected %s metadata entries in modulePath %s, but found %s entries",
        expectedCount, modulePathURI, metadata.length), (expectedCount == metadata.length));
  }

  /**
   * Tests multi-module metadata generator ModuleMetadataFactory.readAllMetadata (moduleNames,
   * modulePath) API <br/>
   * Takes in one module with two dependent modules, and creates metadata relevant to the set
   * containing all three modules
   */
  @Test
  public void createMultiMetadataTest() throws Exception {
    startTest();

    String[] modulesToLoad = {"personPhone"};
    String modulePath =
        new File(tamDir, getCurPrefix()).toURI().toString() + Constants.MODULEPATH_SEP_CHAR;

    MultiModuleMetadata mmm = ModuleMetadataFactory.readAllMetaData(modulesToLoad, modulePath);

    // dump relevant data to stdout for manual debugging
    ((MultiModuleMetadataImpl) mmm).dump();

    String[] modulesInMetadata = mmm.getModuleNames();

    // some basic retrieval tests

    // check # of modules in metadata
    assertTrue(String.format("Expected %s modules in generated metadata, but found %s entries", 3,
        modulesInMetadata.length), (modulesInMetadata.length == 3));

    // retrieve a view from one of the modules
    String viewName = "person.PersonSimple";

    assertTrue(String.format("Could not find the view %s in the multi-module metadata.", viewName),
        null != (mmm.getViewMetadata(viewName)));

    // try to retrieve a view that doesn't exist
    assertTrue("Found a metadata object for a non-existent view.",
        (null == mmm.getViewMetadata("NonexistentView")));

    endTest();
  }

  /**
   * Attempt to generate multi-module metadata with modules that have conflicting output views
   */
  @Test
  public void conflictingOutputViewTest() throws Exception {
    startTest();

    String[] modulesToLoad = {"module1", "module2"};
    String modulePath =
        new File(tamDir, getCurPrefix()).toURI().toString() + Constants.MODULEPATH_SEP_CHAR;

    try {
      MultiModuleMetadata mmm = ModuleMetadataFactory.readAllMetaData(modulesToLoad, modulePath);

      // should never reach this line -- print out the metadata if it did
      ((MultiModuleMetadataImpl) mmm).dump();
    } catch (ModuleLoadException mle) {
      // We expect exception while loading modules
      System.err.println(mle.getMessage());
      return;
    }

    Assert.fail("Control should have not reached here; ModuleLoadException was expected");

    endTest();
  }

  /**
   * Attempt to generate multi-module metadata with modules that have incompatible doc schema
   */
  @Test
  public void incompatibleDocSchemaTest() throws Exception {
    startTest();

    String[] modulesToLoad = {"person", "phone"};
    String modulePath =
        new File(tamDir, getCurPrefix()).toURI().toString() + Constants.MODULEPATH_SEP_CHAR;

    try {
      MultiModuleMetadata mmm = ModuleMetadataFactory.readAllMetaData(modulesToLoad, modulePath);

      // should never reach this line -- print out the metadata if it did
      ((MultiModuleMetadataImpl) mmm).dump();
    } catch (ModuleLoadException mle) {
      // We expect exception while loading modules
      System.err.println(mle.getMessage());
      return;
    }

    Assert.fail("Control should have not reached here; ModuleLoadException was expected");

    endTest();
  }

  /**
   * Attempt to generate multi-module metadata with modules that have incompatible tokenizer types
   */
  @Test
  public void incompatibleTokenizerTest() throws Exception {
    startTest();

    String[] modulesToLoad = {"person", "phone", "personPhone"};
    String modulePath =
        new File(tamDir, getCurPrefix()).toURI().toString() + Constants.MODULEPATH_SEP_CHAR;

    try {
      MultiModuleMetadata mmm = ModuleMetadataFactory.readAllMetaData(modulesToLoad, modulePath);

      // should never reach this line -- print out the metadata if it did
      ((MultiModuleMetadataImpl) mmm).dump();
    } catch (IncompatibleTokenizerConfigException itce) {
      // We expect exception while loading modules
      System.err.println(itce.getMessage());
      return;
    }

    Assert.fail(
        "Control should have not reached here; IncompatibleTokenizerConfigException was expected");

    endTest();
  }

  /**
   * Attempt to generate multi-module metadata with modules that have a circular dependency. <br/>
   */
  @Test
  public void circularDependencyTest() throws Exception {
    // moduleA was compiled dependent on a moduleB that is not dependent on anything. A second
    // moduleB was compiled
    // dependent on a different moduleA that is not dependent on anything. Now this test tries to
    // create multi-module
    // metadata on the two modules that are dependent on each other.
    startTest();

    String[] modulesToLoad = {"moduleA", "moduleB"};
    String modulePath =
        new File(tamDir, getCurPrefix()).toURI().toString() + Constants.MODULEPATH_SEP_CHAR;

    try {
      MultiModuleMetadata mmm = ModuleMetadataFactory.readAllMetaData(modulesToLoad, modulePath);

      // should never reach this line -- print out the metadata if it did
      ((MultiModuleMetadataImpl) mmm).dump();
    } catch (CircularDependencyException cde) {
      // We expect exception while loading modules
      System.err.println(cde.getMessage());
      return;
    }

    Assert.fail("Control should have not reached here; CircularDependencyException was expected");

    endTest();
  }

  /**
   * Test case for defect . <br/>
   * Attempt to generate multi-module metadata with modules that output imported non-alias views
   */
  @Test
  public void importedNonAliasOutputViewTest() throws Exception {
    startTest();

    String[] modulesToLoad = {"watson", "watsonagain"};
    compileModules(modulesToLoad, null);

    String modulePath = getCurOutputDir().getCanonicalFile().toURI().toString();

    MultiModuleMetadata mmm = ModuleMetadataFactory.readAllMetaData(modulesToLoad, modulePath);

    TupleSchema viewSchema = mmm.getViewMetadata("watson.AllWatson").getViewSchema();

    assertNotNull("Expected not null value for viewSchema", viewSchema);

    endTest();
  }

  /**
   * Test case for defect . <br/>
   * Verifies that a view's metadata can be queries by using the output view alias
   * 
   * @throws Exception
   */
  @Test
  public void outputViewAliasMetadataTest() throws Exception {
    startTest();
    String[] modulesToLoad = {"module1", "module2"};
    compileModules(modulesToLoad, null);

    String modulePath = getCurOutputDir().getCanonicalFile().toURI().toString();

    MultiModuleMetadata mmm = ModuleMetadataFactory.readAllMetaData(modulesToLoad, modulePath);

    // Query the view metadata using output view alias name
    TupleSchema viewSchema = mmm.getViewMetadata("NumbersOutputAlias").getViewSchema();

    assertNotNull("Expected not null value for viewSchema", viewSchema);

    endTest();
  }

}
