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
package com.ibm.avatar.algebra.util.pmml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.ToolProvider;

import com.ibm.avatar.algebra.util.file.FileUtils;
import com.ibm.avatar.api.exceptions.TextAnalyticsException;

/**
 * Various utility methods for dealing with scoring models that are represented in the PMML
 * standard.
 * <p>
 * <b>NOTE:</b> This class is deliberately kept free of references to the JPMML library. We do not
 * want extractors that do not need JPMML to have a dependency on that library.
 * 
 */
public class PMMLUtil {

  /**
   * Format string for constructing the location of the PMML model in the classpath of a generated
   * UDF. First argument is the package name, with dots changed to slashes, and second argument is
   * class name.
   */
  public static final String PMML_RESOURCE_FORMAT = "%s/%s_model.xml";

  /**
   * Location where the Java template for a scoring function resides.
   */
  public static final String SCORING_FN_RESOURCE_LOC =
      "com/ibm/systemt/pmml/PMMLScorer.java.template";

  /**
   * Name of the single required argument (of type table) of a PMML UDF
   */
  public static final String MODEL_PARAMS_ARG_NAME = "params";

  /**
   * Package up a PMML file inside a jar file to be embedded inside a TAM file.
   * 
   * @param pmmlFile the XML file in PMML format
   * @param packageName name of the package in which the temporary class should reside
   * @param className name of the temporary class to generate
   * @param jarOut stream where the contents of the jar file shou
   * @throws IOException if there is a problem reading or writing data
   */
  public static void makePMMLJar(File pmmlFile, String packageName, String className,
      OutputStream jarOut) throws IOException, TextAnalyticsException {
    if (false == pmmlFile.exists()) {
      throw new FileNotFoundException(String.format(
          "PMML file %s not found.  Ensure that the file exists and the parent directory is readable.",
          pmmlFile));
    }

    // Read the scoring function template.
    StringBuilder sb = new StringBuilder();
    char[] buf = new char[1024];

    InputStream inStream =
        PMMLUtil.class.getClassLoader().getResourceAsStream(SCORING_FN_RESOURCE_LOC);

    if (null == inStream) {
      throw new IOException(String.format(
          "Error opening required resource '%s' "
              + "while compiling PMML-based function.  Ensure that systemT.jar is not damaged.",
          SCORING_FN_RESOURCE_LOC));
    }

    InputStreamReader in = new InputStreamReader(inStream, "UTF-8");

    int nread;
    while (0 < (nread = in.read(buf))) {
      sb.append(buf, 0, nread);
    }
    in.close();
    String javaCode = sb.toString();

    // Fill in the components of the template.
    javaCode = javaCode.replaceAll("CLASS_NAME_GOES_HERE", className);
    javaCode = javaCode.replaceAll("PACKAGE_NAME_GOES_HERE", packageName);

    // System.err.printf ("Compiling java code:\n%s\n-------------------------------\n", javaCode);

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (null == compiler) {
      throw new TextAnalyticsException(
          "A Java compiler, which is required to generate PMML-based scoring functions, is not present.  "
              + "Ensure that the AQL compiler is running from within a JDK environment.");
    }

    JarOutputStream out = new JarOutputStream(jarOut);

    ArrayList<CompilerInputStub> compilationUnits = new ArrayList<CompilerInputStub>();
    compilationUnits.add(new CompilerInputStub(packageName, className, javaCode));

    CompilerOutputStub outStub =
        new CompilerOutputStub(compiler.getStandardFileManager(null, null, null));

    CompilationTask task = compiler.getTask(null, outStub, null, null, null, compilationUnits);
    task.call();

    // Put the bytecode for the table function into the jar file.
    {
      byte[] classBytes = outStub.getClassBytes(packageName, className);
      String pathStr = String.format("%s/%s.class", packageName.replace('.', '/'), className);
      ZipEntry entry = new ZipEntry(pathStr);
      out.putNextEntry(entry);
      out.write(classBytes);
      out.closeEntry();
    }

    // Put the PMML file into the jar file.
    {
      String pmmlResourceLoc =
          String.format(PMML_RESOURCE_FORMAT, packageName.replace('.', '/'), className);
      ZipEntry entry = new ZipEntry(pmmlResourceLoc);
      out.putNextEntry(entry);
      FileUtils.copyStreamToStream(new FileInputStream(pmmlFile), out);
      out.closeEntry();
    }

    out.close();
  }

  /**
   * Convenience version of {@link #makePMMLJar(File, String, String, OutputStream)} for writing
   * directly to a temp file.
   * 
   * @param pmmlFile the XML file in PMML format
   * @param packageName name of the package in which the temporary class should reside
   * @param className name of the temporary class to generate
   * @param jarTemp temporary jar file that will contain the PMML file
   * @throws IOException if there is a problem reading or writing data
   */
  public static void makePMMLJar(File pmmlFile, String packageName, String className, File jarTemp)
      throws IOException, TextAnalyticsException {
    makePMMLJar(pmmlFile, packageName, className, new FileOutputStream(jarTemp));
  }

}
