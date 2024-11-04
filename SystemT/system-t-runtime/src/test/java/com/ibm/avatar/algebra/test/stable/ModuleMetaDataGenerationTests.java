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
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.ibm.avatar.algebra.datamodel.FieldType;
import com.ibm.avatar.algebra.datamodel.Pair;
import com.ibm.avatar.algebra.datamodel.TupleSchema;
import com.ibm.avatar.algebra.util.test.MemoryProfiler;
import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.api.tam.ModuleMetadata;
import com.ibm.avatar.api.tam.ModuleMetadataFactory;
import com.ibm.avatar.api.tam.ViewMetadata;

/**
 * Various tests to verify compiler's module meta-data generation.
 * 
 */
public class ModuleMetaDataGenerationTests extends RuntimeTestHarness {

  public static void main(String[] args) {
    try {

      ModuleMetaDataGenerationTests t = new ModuleMetaDataGenerationTests();

      // t.setUp ();

      long startMS = System.currentTimeMillis();

      t.exportUDFTest();

      long endMS = System.currentTimeMillis();

      // t.tearDown ();

      double elapsedSec = (double) (endMS - startMS) / 1000.0;

      System.err.printf("Test took %1.3f sec.\n", elapsedSec);

      MemoryProfiler.dumpHeapSize("After test");

    } catch (Exception e) {
      e.printStackTrace();
    }
  }


  /**
   * Test to verify, that only exported detagged views are written to module meta-data.
   * 
   * @throws Exception
   */
  @Test
  public void exportDetaggedViewTest() throws Exception {
    startTest();
    compileModuleAndSerializeMM("exportDetaggedViewTest");
    compareMetadataAgainstExpected("metadata.xml");
    endTest();
  }

  /**
   * Test to verify, that only exported function are written to module meta-data.
   * 
   * @throws Exception
   */
  @Test
  public void exportUDFTest() throws Exception {
    startTest();
    compileModuleAndSerializeMM("exportUDFTest");
    compareMetadataAgainstExpected("metadata.xml");
    endTest();
  }

  /**
   * Test to verify tables are marked exported in module meta-data.
   * 
   * @throws Exception
   */
  @Test
  public void exportTableTest() throws Exception {
    startTest();
    compileModuleAndSerializeMM("exportTableTest");
    compareMetadataAgainstExpected("metadata.xml");
    endTest();
  }

  /**
   * Test for defect : External views that are output are serialized in module metadata with
   * output=false. Exporting external views results in a compiler error.. Commented out because it
   * fails.
   * 
   * @throws Exception
   */
  @Test
  public void outputExternalViewTest() throws Exception {
    startTest();
    compileModuleAndSerializeMM("outputExternalViewTest");
    compareMetadataAgainstExpected("metadata.xml");
    endTest();
  }

  /**
   * Test to verify that the document schema accessed from metadata is not null. Scenario for defect
   * .
   * 
   * @throws Exception
   */
  @Test
  public void documentMetadataTest() throws Exception {
    startTest();
    compileModule("documentMetadataTest");
    ModuleMetadata metadata = ModuleMetadataFactory.readMetaData("documentMetadataTest",
        getCurOutputDir().toURI().toString());
    TupleSchema actualSchema = metadata.getDocSchema();
    TupleSchema expectedSchema = new TupleSchema(new String[] {"field1", "field2", "field3"},
        new FieldType[] {FieldType.TEXT_TYPE, FieldType.INT_TYPE, FieldType.TEXT_TYPE});
    expectedSchema.setName(Constants.DEFAULT_DOC_TYPE_NAME);

    assertNotNull("Document schema should not be null", actualSchema);
    assertEquals("Actual schema does not match with expected schema ", expectedSchema,
        actualSchema);

    endTest();
  }

  /**
   * Test to verify that correct meta-data is serialized, when imported views are output-ed.
   * 
   * @throws Exception
   */
  @Test
  public void outputImportedViewTest() throws Exception {
    startTest();
    String[] testModules = new String[] {"exportView", "outputImportedViewTest"};
    // Compiled test-case's module and it's dependent
    compileModules(testModules, null);

    // Read and serialize meta-data for test-case's module
    ModuleMetadata mm = ModuleMetadataFactory.readMetaData("outputImportedViewTest",
        getCurOutputDir().toURI().toString());
    serializeMM(mm);

    // Compare meta-data XMLs
    compareMetadataAgainstExpected("metadata.xml");

    // Load operator graph - to compare names returned by meta-data API and OperatorGraph API
    OperatorGraph og = OperatorGraph.createOG(testModules, getCurOutputDir().toURI().toString(),
        null, getTokenizerConfig());

    /*
     * Compare output view names
     */
    // Output names from operator graph
    ArrayList<String> outputNameFromOG = og.getOutputTypeNames();
    // System.out.println ("outputNameFromOG: " + outputNameFromOG);

    // Output names module meta-data API
    List<String> outputNameFromMM = Arrays.asList(mm.getOutputViews());
    // System.out.println ("outputNameFromMM: " + outputNameFromMM);

    // Compare output views
    Assert.assertTrue(outputNameFromOG.containsAll(outputNameFromMM)
        && outputNameFromMM.containsAll(outputNameFromOG));

    /*
     * Compare external view names
     */
    // External view name from OG
    List<String> externalViewNameOG = Arrays.asList(og.getExternalViewNames());
    ArrayList<Pair<String, String>> extViewPairOG = new ArrayList<Pair<String, String>>();
    for (String extAQLName : externalViewNameOG) {
      Pair<String, String> extView =
          new Pair<String, String>(extAQLName, og.getExternalViewExternalName(extAQLName));
      extViewPairOG.add(extView);
    }
    // System.out.println ("externalViewNameOG:" + externalViewNameOG);
    // System.out.println ("extViewPairOG:" + extViewPairOG);

    // External view name from OG
    List<Pair<String, String>> externalViewNameMM = mm.getExternalViews();
    // System.out.println ("externalViewNameMM: " + externalViewNameMM);

    // Compare external views
    Assert.assertTrue(extViewPairOG.containsAll(externalViewNameMM)
        && externalViewNameMM.containsAll(extViewPairOG));

    endTest();
  }

  /**
   * This tests captures various scenario mentioned by Sudarshan in defect#26281.
   * 
   * @throws Exception
   */
  @Test
  public void sudarshanMetadataAPITest() throws Exception {
    startTest();
    String[] testModules = new String[] {"moduleA", "moduleB1", "moduleB2", "moduleB3"};
    // Compiled test-case's module and it's dependent
    compileModules(testModules, null);

    // Read and serialize meta-data for module - moduleB1
    ModuleMetadata mm =
        ModuleMetadataFactory.readMetaData("moduleB1", getCurOutputDir().toURI().toString());

    Assert.assertNull(mm.getViewMetadata("sample"));

    Assert.assertNull(mm.getViewMetadata("moduleB1.sample"));

    Assert.assertNull(mm.getViewMetadata("moduleA.sample"));

    // Read and serialize meta-data for module - moduleB2
    ModuleMetadata mm1 =
        ModuleMetadataFactory.readMetaData("moduleB2", getCurOutputDir().toURI().toString());
    serializeMM(mm1);

    ViewMetadata vmdAlias = mm1.getViewMetadata("sample");
    Assert.assertNull(vmdAlias);

    ViewMetadata vmdQ = mm1.getViewMetadata("moduleA.sample");
    Assert.assertNotNull(vmdQ);

    compareMetadataAgainstExpected("metadata.xml");

    // Read and serialize meta-data for module - moduleB2
    ModuleMetadata mm2 =
        ModuleMetadataFactory.readMetaData("moduleB3", getCurOutputDir().toURI().toString());
    // serializeMM (mm2);

    ViewMetadata vmdByUQ = mm2.getViewMetadata("sample");
    Assert.assertNull(vmdByUQ);

    ViewMetadata vmdQ1 = mm2.getViewMetadata("moduleA.sample");
    Assert.assertNotNull(vmdQ1);

    ViewMetadata vmdByAlias = mm2.getViewMetadata("sampleOutputAlias");
    Assert.assertNotNull(vmdByAlias);

    compareMetadataAgainstExpected("metadata.xml");

    endTest();
  }

  /**
   * Method to compile module and later serialize the meta-data.
   * 
   * @throws Exception
   */
  private void compileModuleAndSerializeMM(String moduleName) throws Exception {
    OperatorGraph og = compileAndLoadModule(moduleName, null);
    ModuleMetadata mm = og.getModuleMetadata(getCurPrefix());
    serializeMM(mm);
  }

  /**
   * This method serializes the give module meta-data object into currentTestCase/metadata.xml file.
   * 
   * @param moduleMetadata module meta-data object to be serialized
   * @throws Exception
   */
  private void serializeMM(ModuleMetadata moduleMetadata) throws Exception {
    // Write out the meta-data so we can easily look at it without going through hoops
    File mmOut = new File(this.getCurOutputDir(), "metadata.xml");
    FileOutputStream out = new FileOutputStream(mmOut);
    moduleMetadata.serialize(out);

    out.close();
  }
}
