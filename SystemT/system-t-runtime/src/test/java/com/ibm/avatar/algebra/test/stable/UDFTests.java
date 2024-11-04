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

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;

import org.junit.Test;

import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.algebra.util.test.TestConstants;
import com.ibm.avatar.api.CompileAQL;
import com.ibm.avatar.api.CompileAQLParams;
import com.ibm.avatar.api.Constants;
import com.ibm.avatar.api.OperatorGraph;
import com.ibm.avatar.api.exceptions.CompilerException;
import com.ibm.avatar.api.exceptions.ModuleLoadException;
import com.ibm.avatar.aql.ParseException;

/**
 * Tests for UDF feature of AQL
 * 
 */
public class UDFTests extends RuntimeTestHarness {

  public static final String AQL_DIR = TestConstants.AQL_DIR + "/UDFTests";

  /**
   * Verifies that a UDF jar compiled using higher JDK level than what is supported by systemT
   * throws a suitable exception to the user. This test uses a UDF jar compiled using JDK 7.0 when
   * SystemT v2.1.1 supports JDK 6.x only. The source of the UDF is also checked in under test data.
   * Use it to compile with higher levels of JDK when systemT moves up to higher levels of JDK.
   * 
   * @throws Exception
   */
  @Test
  public void higherJDKLevelCompilationTest() throws Exception {
    startTest();
    try {
      compileModule("udfModule");
    } catch (CompilerException ce) {

      // verification #1: Check for number of exceptions, line # and column #
      int lineNo[] = {3};
      int colNo[] = {1};
      checkException(ce, lineNo, colNo);

      // verification #2: Check for expected error message
      Exception e = ce.getAllCompileErrors().get(0);
      String msgPattern =
          ".*Java class com.ibm.biginsights.textanalytics.udftest.StringUtils was compiled with a version of Java more recent than the one used for execution.*";
      assertTrue("Compilation error is not as expected", e.getMessage().matches(msgPattern));

    }
    endTest();
  }

  /**
   * This test verifies that a suitable error message is thrown when a TAM file containing UDF jar
   * compiled with higher JDK version is executed using systemT on a lower JDK version. For the
   * current release, it verifies if SystemT runtime at JDK 6.0 level can flag an appropriate error
   * when a UDF jar compiled with JDK 70 is loaded.<br/>
   * To *simulate* this scenario, the following test data is prepared:
   * <ol>
   * <li>Prepare a udf.jar by compiling
   * $WORKSPACE_ROOT/Runtime/testdata/aql/UDFTests/higherJDKLevelCompilationTest/udfSource using JDK
   * 60 (the current JDK version supported by SystemT)</li>
   * <li>Compile $WORKSPACE_ROOT/Runtime/testdata/aql/UDFTests/higherJDKLevelRuntimeTest/udfModule
   * using the udf.jar created in the step above.</li>
   * <li>Replace 00000.jar found under udfModule.tam generated in the previous step with a udf.jar
   * compiled using a higher level JDK (for this release, use JDK 70)</li>
   * <li>Place the modified tam under
   * $WORKSPACE_ROOT/Runtime/testdata/tam/UDFTests/higherJDKLevelRuntimeTest</li>
   * </ol>
   * Now, attempting to load the modified TAM should throw UnsupportedClassVersionError (wrapped
   * into ParseException with suitable error message)
   * 
   * @throws Exception
   */
  @Test
  public void higherJDKLevelRuntimeTest() throws Exception {
    startTest();

    String[] moduleNames = {"udfModule"};
    String modulePath = getPreCompiledModuleDir().toURI().toString();

    // attempt to load extractor. Expect an exception
    try {
      OperatorGraph.createOG(moduleNames, modulePath, null, null);
    } catch (ModuleLoadException mle) {
      String msg =
          "Java class com.ibm.biginsights.textanalytics.udftest.StringUtils was compiled with a version of Java more recent than the one used for execution. Execute using the version of Java used to compile the UDF, or recompile the UDF class using the version of java you wish to compile/execute the extractor with.";
      assertException(mle, ParseException.class.getName(), msg);
    }

    endTest();
  }

  /**
   * Test case that reproduces defect "Using scalar UDF in group by clause causes exception"
   * 
   * @throws Exception
   */
  @Test
  public void scalarUDFExceptionTest() throws Exception {
    startTest();

    String[] moduleNames = {"test", "UDFs"};

    final File DOCS_FILE =
        new File(TestConstants.TEST_DOCS_DIR, "/UDFTests/scalarUDFException.txt");

    // This should run without throwing an exception. Comparison of expected results not necessary.
    compileAndRunModules(moduleNames, DOCS_FILE, null);
    endTest();
  }

  /**
   * Tests for BigInsights RTC defect : NegativeArraySizeException in ByteArrayClassLoader.readEntry
   */
  @Test
  public void loadLargeModelTest() throws Exception {
    startTest();

    // Use 1 documents as input, so that the function gets called 1 time.
    File docsFile = new File(TestConstants.ENRON_1_DUMP);

    super.setPrintTups(true);

    super.compileAndRunModule("loadLargeModelTest", docsFile, null);

    super.compareAgainstExpected(false);

    endTest();
  }

  /**
   * This test verifies backwards compatibility with TAM files created before 2.1.2 that serialized
   * JAR data directly inside the AOG. In 2.1.2, serialized JAR data was moved outside the AOG, but
   * the old format must be supported until BigInsights v5.0. This test compiles a loader module
   * which uses a UDF inside a legacy UDFs.tam to capitalize all of the "the" words in a document.
   * It then creates and runs an extractor that uses the legacy UDFs.tam.
   * 
   * @throws Exception
   */
  @Test
  public void loadJARFromTamTest() throws Exception {
    startTest();

    File DOCS_FILE = new File(TestConstants.TEST_DOCS_DIR, "/TAMTests/input.zip");

    String[] compileModuleNames = {"loader"};

    // this test uses a just-compiled module (loader, in regression/actual), and a legacy module
    // (UDFS, in testdata/tam)
    String modulePath = getCurOutputDir().toURI().toString() // loader module
        + Constants.MODULEPATH_SEP_CHAR + getPreCompiledModuleDir().toURI().toString(); // legacy
                                                                                        // UDFs
                                                                                        // module

    String compiledModuleURI;

    // URI where compiled modules should be dumped -- set to regression/actual
    compiledModuleURI = new File(String.format("%s", getCurOutputDir())).toURI().toString();

    // set up the directories containing the input modules we want to compile
    ArrayList<String> moduleDirectories = new ArrayList<String>();

    for (String moduleName : compileModuleNames) {
      String moduleDirectoryName = new File(String.format("%s/%s", getCurTestDir(), moduleName))
          .getCanonicalFile().toURI().toString();

      moduleDirectories.add(moduleDirectoryName);
    }

    // Compile new loader module using the pre-compiled UDFs module
    CompileAQLParams params = new CompileAQLParams(moduleDirectories.toArray(new String[1]),
        compiledModuleURI, modulePath, null);

    CompileAQL.compile(params);

    // attempt to load and use extractor
    // Expect no exception until JAR serialized within TAM support is removed.
    OperatorGraph og =
        OperatorGraph.createOG(new String[] {"loader", "UDFs"}, modulePath, null, null);

    annotateAndPrint(DOCS_FILE, og);

    // ensure that the extractor pulled out all "the" words and capitalized them
    compareAgainstExpected(true);

    endTest();
  }
}
