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
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;

import org.junit.Test;

import com.ibm.avatar.algebra.util.test.RuntimeTestHarness;
import com.ibm.avatar.api.OperatorGraph;

/**
 * Verifies loading of TAM file from System class path.
 * 
 */
public class TAMLoaderTests extends RuntimeTestHarness {
  /**
   * Test to verify that OperatorGraph is capable of loading modules from java classpath, where the
   * classpath points to a directory containing a .tam file
   * 
   * @throws Exception
   */
  @Test
  public void loadModuleFromDirInClassPathTest() throws Exception {
    startTest();

    compileModule("phone");

    // Add module directory to the system class path
    ClassLoader currentThreadClassLoader = Thread.currentThread().getContextClassLoader();
    URLClassLoader urlClassLoader =
        new URLClassLoader(new URL[] {getCurOutputDir().toURI().toURL()}, currentThreadClassLoader);
    Thread.currentThread().setContextClassLoader(urlClassLoader);

    // Invoke createOG without passing any modulePath, thus asking it to load from system classpath
    OperatorGraph.createOG(new String[] {"phone"}, new HashMap<String, String>(), null, null);

    endTest();
  }

  /**
   * Test to verify that OperatorGraph is capable of loading modules from java classpath, where the
   * classpath points to a jar containing a .tam file
   * 
   * @throws Exception
   */
  @Test
  public void loadModuleFromJarInClassPathTest() throws Exception {
    startTest();

    compileModules(new String[] {"phone", "person"}, "tamCollection.jar");

    // Add module jar file to the system class path
    ClassLoader currentThreadClassLoader = Thread.currentThread().getContextClassLoader();
    URLClassLoader urlClassLoader = new URLClassLoader(
        new URL[] {new File(getCurOutputDir(), "tamCollection.jar").toURI().toURL()},
        currentThreadClassLoader);
    Thread.currentThread().setContextClassLoader(urlClassLoader);

    // Invoke createOG without passing any modulePath, thus asking it to load from system classpath
    OperatorGraph.createOG(new String[] {"person", "phone"}, new HashMap<String, String>(), null,
        null);

    endTest();
  }

}
