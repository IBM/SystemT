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

import java.io.IOException;
import java.net.URI;

import javax.tools.SimpleJavaFileObject;

/**
 * Wrapper around a string for the purpose of passing said string to javac.
 * 
 */
public class CompilerInputStub extends SimpleJavaFileObject {

  /** Contents of the "file". */
  private String contents;

  /**
   * Main constructor.
   * 
   * @param packageName name of the package containing the class
   * @param className name of the class (no .class or .java extension)
   * @param contents contents of the "file"
   */
  public CompilerInputStub(String packageName, String className, String contents) {
    // Generate a URI with the "string" type. Note how we replace the dots in the package name with
    // slashes.
    super(
        URI.create(
            "string:///" + packageName.replace('.', '/') + "/" + className + Kind.SOURCE.extension),
        Kind.SOURCE);

    this.contents = contents;
  }

  @Override
  public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
    return contents;
  }

}
